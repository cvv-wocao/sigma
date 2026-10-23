package cn.itcast.agent.search.service;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryBatchQuery;
import cn.itcast.agent.common.api.dto.RepositoryFileContent;
import cn.itcast.agent.common.api.dto.RepositoryFileEntry;
import cn.itcast.agent.common.api.dto.RepositoryFileListing;
import cn.itcast.agent.common.api.dto.RepositoryReadme;
import cn.itcast.agent.common.api.dto.RepositoryRelease;
import cn.itcast.agent.common.api.dto.RepositoryReleaseAsset;
import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import org.springframework.web.client.HttpClientErrorException;
import cn.itcast.agent.search.client.RepositoryArchiveStream;
import cn.itcast.agent.search.config.RepositorySearchProperties;
import cn.itcast.agent.search.provider.KeywordQuery;
import cn.itcast.agent.search.provider.RepositorySearchProvider;
import cn.itcast.agent.search.support.RepositoryTextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 检索业务层：负责参数规整、来源选择与结果合并，数据获取交给
 * {@link RepositorySearchProvider} 的实现。
 *
 * <p>来源选择规则：按 search.provider 指定的主来源优先；主来源无结果时，
 * 若 search.fallback-enabled 为 true 则依次尝试其余来源。任意来源调用失败都会记日志，
 * 只有「所有来源都失败」才把异常抛给调用方，避免因为一个平台抖动就整体不可用。</p>
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PER_PAGE = 10;
    private static final int MAX_PER_PAGE = 50;

    /** 批量检索：单个关键词的默认取条数与上限 */
    private static final int DEFAULT_PER_KEYWORD = 15;
    private static final int MAX_PER_KEYWORD = 30;
    /** 批量检索：一次最多接受多少个关键词（推荐流水线一般给 3-6 个） */
    private static final int MAX_KEYWORDS = 8;
    /** 批量检索：召回阶段多收集一些，排序后再截断，避免只看前 N 条就错过高质量仓库 */
    private static final int RECALL_MULTIPLIER = 2;

    /** owner / repo 只允许常见仓库名字符，既满足安全校验也避免拼出异常 URL */
    private static final Pattern REPOSITORY_PART_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final int MAX_BRANCH_LENGTH = 200;

    /** Release / 目录 / 文件读取的边界：既保护上游额度，也避免把超大内容塞给模型 */
    private static final int DEFAULT_RELEASE_LIMIT = 5;
    private static final int MAX_RELEASE_LIMIT = 20;
    private static final int MAX_RELEASE_ASSETS = 50;
    private static final int RELEASE_BODY_MAX_LENGTH = 4000;
    private static final int MAX_DIRECTORY_ENTRIES = 200;
    private static final int MAX_FILE_CONTENT_LENGTH = 30000;
    private static final int MAX_REPOSITORY_PATH_LENGTH = 500;
    /** 深层内容短时缓存：GitHub 未配置 Token 时额度很低，重复追问不应反复打外部接口 */
    private static final long DEEP_CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final int MAX_DEEP_CACHE_ENTRIES = 500;

    /** 主来源排在最前的有序 Map，保证 provider 顺序稳定可预期 */
    private final LinkedHashMap<String, RepositorySearchProvider> providers;
    private final RepositorySearchProperties searchProperties;
    private final RepositoryTextCleaner textCleaner;
    private final Map<String, CacheEntry> deepCache = new ConcurrentHashMap<>();

    public SearchService(List<RepositorySearchProvider> providerList, RepositorySearchProperties searchProperties,
                         RepositoryTextCleaner textCleaner) {
        this.searchProperties = searchProperties;
        this.textCleaner = textCleaner;
        LinkedHashMap<String, RepositorySearchProvider> ordered = new LinkedHashMap<>();
        providerList.stream()
                .filter(provider -> provider.name().equals(searchProperties.provider()))
                .findFirst()
                .ifPresent(provider -> ordered.put(provider.name(), provider));
        providerList.stream()
                .sorted(Comparator.comparing(RepositorySearchProvider::name))
                .forEach(provider -> ordered.putIfAbsent(provider.name(), provider));
        if (ordered.isEmpty()) {
            throw new IllegalStateException("没有可用的检索来源，请检查 provider 实现是否被扫描到");
        }
        this.providers = ordered;
        log.info("检索来源初始化完成：primary={}, fallbackEnabled={}, 可用来源={}",
                ordered.keySet().iterator().next(), searchProperties.fallbackEnabled(), ordered.keySet());
    }

    /**
     * 关键词检索仓库。
     * 对外：GET /api/search/repositories?keyword=xxx&page=1&perPage=10
     */
    public List<GiteeRepository> searchRepositories(String keyword, Integer page, Integer perPage) {
        String normalizedKeyword = requireText(keyword, "搜索关键词");
        return searchOne(KeywordQuery.of(normalizedKeyword, normalizePage(page), normalizePerPage(perPage)));
    }

    /**
     * 多关键词批量检索：逐个关键词检索后按 fullName 去重合并，再做语言 / Star 数过滤，
     * 最后按 Star 降序返回候选池。供「AI 推荐」流水线一次召回使用。
     *
     * <p>语言 / Star 下限 / 推送时间会作为限定符一起下推给来源（GitHub 支持），
     * 本地仍保留同样的过滤作为兜底：来源不支持限定符时（Gitee 降级路径）结果同样被约束。</p>
     * 对外：POST /api/search/repositories/batch
     */
    public List<GiteeRepository> searchBatch(RepositoryBatchQuery query) {
        List<String> keywords = distinctKeywords(query);
        int perKeyword = normalizePerKeyword(query.perKeyword());
        int maxResults = normalizeMaxResults(query.maxResults());
        int recallLimit = maxResults * RECALL_MULTIPLIER;

        LinkedHashMap<String, GiteeRepository> candidates = new LinkedHashMap<>();
        RuntimeException firstFailure = null;
        for (String keyword : keywords) {
            KeywordQuery keywordQuery = new KeywordQuery(keyword, DEFAULT_PAGE, perKeyword,
                    query.language(), query.minStars(), query.pushedAfter());
            try {
                for (GiteeRepository repository : searchOne(keywordQuery)) {
                    if (!matches(repository, query)) {
                        continue;
                    }
                    candidates.putIfAbsent(dedupeKey(repository), repository);
                }
            } catch (RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
                log.warn("关键词检索失败，继续尝试其余关键词：keyword={}, error={}", keyword, e.getMessage());
            }
            if (candidates.size() >= recallLimit) {
                break;
            }
        }

        if (candidates.isEmpty() && firstFailure != null) {
            throw firstFailure;
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingInt((GiteeRepository repository) ->
                        repository.stars() == null ? 0 : repository.stars()).reversed())
                .limit(maxResults)
                .toList();
    }

    /**
     * 仓库详情。依次尝试各来源（主来源优先）：搜到的是 GitHub 仓库就查 GitHub，
     * 收藏过的 Gitee 仓库仍能查到，避免「来源不同导致详情 404」。
     */
    public GiteeRepository getRepository(String owner, String repo) {
        String normalizedOwner = requireText(owner, "owner");
        String normalizedRepo = requireText(repo, "repo");
        FailureCollector failures = new FailureCollector();
        for (RepositorySearchProvider provider : providers.values()) {
            try {
                GiteeRepository repository = provider.getRepository(normalizedOwner, normalizedRepo);
                if (repository != null) {
                    return textCleaner.clean(repository);
                }
            } catch (RuntimeException e) {
                failures.record(e);
                log.debug("来源查询仓库详情未命中：provider={}, repo={}/{}, error={}",
                        provider.name(), normalizedOwner, normalizedRepo, e.getMessage());
            }
        }
        if (failures.hasFailure()) {
            throw failures.reportable();
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "仓库不存在：" + normalizedOwner + "/" + normalizedRepo);
    }

    /** 仓库分支列表，来源选择策略同详情接口 */
    public List<GiteeBranch> getBranches(String owner, String repo) {
        String normalizedOwner = requireText(owner, "owner");
        String normalizedRepo = requireText(repo, "repo");
        FailureCollector failures = new FailureCollector();
        for (RepositorySearchProvider provider : providers.values()) {
            try {
                List<GiteeBranch> branches = provider.getBranches(normalizedOwner, normalizedRepo);
                if (branches != null && !branches.isEmpty()) {
                    return branches;
                }
            } catch (RuntimeException e) {
                failures.record(e);
                log.debug("来源查询分支未命中：provider={}, repo={}/{}, error={}",
                        provider.name(), normalizedOwner, normalizedRepo, e.getMessage());
            }
        }
        if (failures.hasFailure()) {
            throw failures.reportable();
        }
        return List.of();
    }

    /**
     * 查询 Release 与附件。有 Release 不代表一定有安装包，所以 assets 会完整列出；
     * 没有 Release 时返回空列表，由调用方继续查看仓库目录或 README。
     */
    public List<RepositoryRelease> getReleases(String owner, String repo, Integer limit, String source) {
        String normalizedOwner = requireRepositoryPart(owner, "owner");
        String normalizedRepo = requireRepositoryPart(repo, "repo");
        int size = normalizeReleaseLimit(limit);
        RepositorySearchProvider provider = resolveProviderForRepository(normalizedOwner, normalizedRepo, source);
        String cacheKey = "releases:" + provider.name() + ":" + normalizedOwner + "/" + normalizedRepo + ":" + size;
        return cached(cacheKey, () -> {
            List<RepositoryRelease> releases = provider.getReleases(normalizedOwner, normalizedRepo, size);
            if (releases == null || releases.isEmpty()) {
                return List.of();
            }
            return releases.stream().map(this::sanitizeRelease).toList();
        });
    }

    /**
     * 列出仓库目录。path 为空表示根目录；目录项过多时只保留前若干项，
     * 并通过 truncated / total 明确告诉调用方，避免模型误以为仓库只有这些文件。
     */
    public RepositoryFileListing listFiles(String owner, String repo, String path, String branch, String source) {
        String normalizedOwner = requireRepositoryPart(owner, "owner");
        String normalizedRepo = requireRepositoryPart(repo, "repo");
        String normalizedPath = normalizeRepositoryPath(path);
        String normalizedBranch = normalizeOptionalBranch(branch);
        RepositorySearchProvider provider = resolveProviderForRepository(normalizedOwner, normalizedRepo, source);
        String cacheKey = "files:" + provider.name() + ":" + normalizedOwner + "/" + normalizedRepo
                + ":" + normalizedBranch + ":" + normalizedPath;
        return cached(cacheKey, () -> {
            List<RepositoryFileEntry> entries = provider.listFiles(normalizedOwner, normalizedRepo,
                    normalizedPath, normalizedBranch);
            List<RepositoryFileEntry> all = entries == null ? List.of() : entries;
            List<RepositoryFileEntry> visible = all.stream().limit(MAX_DIRECTORY_ENTRIES).toList();
            return new RepositoryFileListing(normalizedOwner + "/" + normalizedRepo, normalizedPath,
                    normalizedBranch, visible, all.size() > MAX_DIRECTORY_ENTRIES, all.size());
        });
    }

    /**
     * 读取仓库中的单个文本文件。内容超过上限时截断，二进制 / 目录 / 超大文件
     * 由 DTO 中的标记说明原因，调用方不需要靠 null 猜。
     */
    public RepositoryFileContent readFile(String owner, String repo, String path, String branch, String source) {
        String normalizedOwner = requireRepositoryPart(owner, "owner");
        String normalizedRepo = requireRepositoryPart(repo, "repo");
        String normalizedPath = normalizeRepositoryPath(path);
        if (!StringUtils.hasText(normalizedPath)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件路径不能为空");
        }
        String normalizedBranch = normalizeOptionalBranch(branch);
        RepositorySearchProvider provider = resolveProviderForRepository(normalizedOwner, normalizedRepo, source);
        String cacheKey = "file:" + provider.name() + ":" + normalizedOwner + "/" + normalizedRepo
                + ":" + normalizedBranch + ":" + normalizedPath;
        return cached(cacheKey, () -> {
            RepositoryFileContent file = provider.readFile(normalizedOwner, normalizedRepo,
                    normalizedPath, normalizedBranch);
            if (file == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在：" + normalizedPath);
            }
            String fullName = normalizedOwner + "/" + normalizedRepo;
            if (!StringUtils.hasText(file.content())) {
                return new RepositoryFileContent(fullName, normalizedPath, normalizedBranch, null,
                        file.size(), file.truncated(), file.binary(), file.directory(), file.tooLarge());
            }
            String content = file.content();
            boolean truncated = content.length() > MAX_FILE_CONTENT_LENGTH;
            if (truncated) {
                content = content.substring(0, MAX_FILE_CONTENT_LENGTH);
            }
            return new RepositoryFileContent(fullName, normalizedPath, normalizedBranch, content,
                    file.size(), truncated, false, false, false);
        });
    }

    /**
     * 读取仓库 README 正文，供「项目深读」使用。
     * 依次尝试各来源，返回第一个真正拿到内容的实现；都没有时返回 content 为 null 的结果，
     * 由调用方退化为只用仓库元数据判断，不因为「读不到 README」而中断推荐。
     */
    public RepositoryReadme getReadme(String owner, String repo) {
        String normalizedOwner = requireText(owner, "owner");
        String normalizedRepo = requireText(repo, "repo");
        for (RepositorySearchProvider provider : providers.values()) {
            try {
                RepositoryReadme readme = provider.getReadme(normalizedOwner, normalizedRepo);
                if (readme != null && StringUtils.hasText(readme.content())) {
                    return new RepositoryReadme(readme.fullName(),
                            textCleaner.sanitizeReadme(readme.content()), readme.truncated());
                }
            } catch (RuntimeException e) {
                log.debug("来源读取 README 失败：provider={}, repo={}/{}, error={}",
                        provider.name(), normalizedOwner, normalizedRepo, e.getMessage());
            }
        }
        return new RepositoryReadme(normalizedOwner + "/" + normalizedRepo, null, false);
    }

    /**
     * 打开仓库 ZIP 归档流，供前端直接下载。
     *
     * <p>前端会把检索结果里的 source / defaultBranch 一并带来；手动收藏或历史数据缺少
     * 分支时，再回查一次仓库详情确定默认分支。归档内容不在服务端落盘，直接流式转发。</p>
     *
     * @param source 数据来源（github / gitee），为空时使用主检索来源
     */
    public RepositoryArchiveStream openArchive(String owner, String repo, String branch, String source) {
        String normalizedOwner = requireRepositoryPart(owner, "owner");
        String normalizedRepo = requireRepositoryPart(repo, "repo");
        RepositorySearchProvider provider = resolveProvider(source);
        String normalizedBranch = StringUtils.hasText(branch)
                ? normalizeBranch(branch)
                : resolveDefaultBranch(provider, normalizedOwner, normalizedRepo);
        log.info("开始下载仓库归档：repo={}/{}, branch={}, source={}",
                normalizedOwner, normalizedRepo, normalizedBranch, provider.name());
        return provider.openArchive(normalizedOwner, normalizedRepo, normalizedBranch);
    }

    /**
     * 检索结果的统一后处理：先剔除关键词堆砌的垃圾仓库，再清洗简介。
     *
     * <p>为什么放在这一层：单关键词检索与批量召回都从这里取数据，
     * 在这里处理一次，页面展示、收藏快照和推荐流水线的提示词就都不会再看到
     * 「几万字符的填充空白 + 乱码」。</p>
     */
    private List<GiteeRepository> usable(List<GiteeRepository> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return List.of();
        }
        return repositories.stream()
                .filter(repository -> !textCleaner.isSpam(repository))
                .map(textCleaner::clean)
                .toList();
    }

    /**
     * 多来源失败时的归因：优先抛出「有信息量」的那个失败。
     *
     * <p>为什么需要它：详情 / 分支会依次问所有来源。GitHub 是主来源，Gitee 通常根本没有这个仓库，
     * 于是「GitHub 限流 403 → Gitee 404」这种组合会把真正的限流原因盖掉，用户只看到
     * 「该仓库在数据源上不存在」，排查方向直接被带偏。这里优先保留非 404 的失败。</p>
     */
    private static final class FailureCollector {

        private RuntimeException first;
        private RuntimeException firstMeaningful;

        void record(RuntimeException error) {
            if (first == null) {
                first = error;
            }
            if (firstMeaningful == null && !isNotFound(error)) {
                firstMeaningful = error;
            }
        }

        boolean hasFailure() {
            return first != null;
        }

        RuntimeException reportable() {
            return firstMeaningful != null ? firstMeaningful : first;
        }

        private static boolean isNotFound(RuntimeException error) {
            return error instanceof HttpClientErrorException httpError
                    && httpError.getStatusCode().value() == 404;
        }
    }

    /** 单关键词检索：主来源优先，必要时降级到其他来源；限定符由来源决定是否下推 */
    private List<GiteeRepository> searchOne(KeywordQuery query) {
        RepositorySearchProvider primary = providers.values().iterator().next();
        RuntimeException primaryFailure = null;
        boolean fallbackEnabled = Boolean.TRUE.equals(searchProperties.fallbackEnabled());
        try {
            List<GiteeRepository> found = usable(primary.search(query));
            if (!found.isEmpty() || !fallbackEnabled) {
                return found;
            }
            log.debug("主来源无结果，尝试降级检索：provider={}, keyword={}", primary.name(), query.keyword());
        } catch (RuntimeException e) {
            if (!fallbackEnabled) {
                throw e;
            }
            primaryFailure = e;
            log.warn("主来源检索失败，尝试降级检索：provider={}, keyword={}, error={}",
                    primary.name(), query.keyword(), e.getMessage());
        }

        for (RepositorySearchProvider provider : providers.values()) {
            if (provider.name().equals(primary.name())) {
                continue;
            }
            try {
                List<GiteeRepository> found = usable(provider.search(query));
                if (!found.isEmpty()) {
                    log.warn("已降级到备用来源：primary={}, fallback={}, keyword={}, 命中={}",
                            primary.name(), provider.name(), query.keyword(), found.size());
                    return found;
                }
            } catch (RuntimeException e) {
                log.warn("备用来源检索失败：provider={}, keyword={}, error={}",
                        provider.name(), query.keyword(), e.getMessage());
            }
        }
        if (primaryFailure != null) {
            throw primaryFailure;
        }
        return List.of();
    }

    /** 语言与 Star 数硬过滤：在候选进入排序前先挡掉明显不符合的 */
    private boolean matches(GiteeRepository repository, RepositoryBatchQuery query) {
        if (StringUtils.hasText(query.language())
                && (repository.language() == null
                || !repository.language().equalsIgnoreCase(query.language().trim()))) {
            return false;
        }
        return query.minStars() == null || (repository.stars() != null && repository.stars() >= query.minStars());
    }

    private List<String> distinctKeywords(RepositoryBatchQuery query) {
        if (query == null || query.keywords() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "关键词列表不能为空");
        }
        List<String> keywords = query.keywords().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(MAX_KEYWORDS)
                .toList();
        if (keywords.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "关键词列表不能为空");
        }
        return keywords;
    }

    private String dedupeKey(GiteeRepository repository) {
        String fullName = repository.fullName();
        return fullName == null ? String.valueOf(repository.name()) : fullName.toLowerCase(Locale.ROOT);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizePerPage(Integer perPage) {
        if (perPage == null || perPage < 1) {
            return DEFAULT_PER_PAGE;
        }
        return Math.min(perPage, MAX_PER_PAGE);
    }

    private int normalizePerKeyword(Integer perKeyword) {
        if (perKeyword == null || perKeyword < 1) {
            return DEFAULT_PER_KEYWORD;
        }
        return Math.min(perKeyword, MAX_PER_KEYWORD);
    }

    private int normalizeMaxResults(Integer maxResults) {
        if (maxResults == null || maxResults < 1) {
            return searchProperties.maxResults();
        }
        return Math.min(maxResults, MAX_PER_PAGE);
    }

    private RepositoryRelease sanitizeRelease(RepositoryRelease release) {
        String rawBody = release.body();
        boolean truncated = release.bodyTruncated()
                || (rawBody != null && rawBody.length() > RELEASE_BODY_MAX_LENGTH);
        String body = textCleaner.sanitizeDocument(rawBody, RELEASE_BODY_MAX_LENGTH);
        List<RepositoryReleaseAsset> assets = release.assets() == null
                ? List.of()
                : release.assets().stream().limit(MAX_RELEASE_ASSETS).toList();
        int assetCount = Math.max(release.assetCount(), assets.size());
        return new RepositoryRelease(release.tagName(), release.name(), release.prerelease(),
                release.publishedAt(), release.htmlUrl(), body, truncated, assetCount, assets);
    }

    private int normalizeReleaseLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_RELEASE_LIMIT;
        }
        return Math.min(limit, MAX_RELEASE_LIMIT);
    }

    private String normalizeOptionalBranch(String branch) {
        return StringUtils.hasText(branch) ? normalizeBranch(branch) : null;
    }

    /**
     * 校验仓库内相对路径，禁止绝对路径、Windows 路径、控制字符与目录穿越。
     * 路径允许中文和空格，但不能以 / 开头、不能包含反斜杠或 .. 段。
     */
    private String normalizeRepositoryPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalized = path.trim();
        if (normalized.length() > MAX_REPOSITORY_PATH_LENGTH
                || normalized.startsWith("/")
                || normalized.indexOf((char) 92) >= 0
                || normalized.contains(":")
                || normalized.indexOf((char) 0) >= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仓库内路径不合法");
        }
        for (String segment : normalized.split("/", -1)) {
            if (!StringUtils.hasText(segment) || ".".equals(segment) || "..".equals(segment)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "仓库内路径不合法");
            }
        }
        return normalized;
    }

    /** 深层查询没带来源时，先确认仓库属于哪个平台，避免拿 GitHub 仓库去问 Gitee */
    private RepositorySearchProvider resolveProviderForRepository(String owner, String repo, String source) {
        if (StringUtils.hasText(source)) {
            return resolveProvider(source);
        }
        FailureCollector failures = new FailureCollector();
        for (RepositorySearchProvider provider : providers.values()) {
            try {
                if (provider.getRepository(owner, repo) != null) {
                    return provider;
                }
            } catch (RuntimeException e) {
                failures.record(e);
                log.debug("深层查询解析来源未命中：provider={}, repo={}/{}, error={}",
                        provider.name(), owner, repo, e.getMessage());
            }
        }
        if (failures.hasFailure()) {
            throw failures.reportable();
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "仓库不存在：" + owner + "/" + repo);
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CacheEntry entry = deepCache.get(key);
        if (entry != null && entry.expiresAt() > now) {
            return (T) entry.value();
        }
        T value = loader.get();
        if (value != null) {
            if (deepCache.size() >= MAX_DEEP_CACHE_ENTRIES) {
                deepCache.clear();
            }
            deepCache.put(key, new CacheEntry(value, now + DEEP_CACHE_TTL_MILLIS));
        }
        return value;
    }

    private record CacheEntry(Object value, long expiresAt) {
    }

    private String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, name + " 不能为空");
        }
        return value.trim();
    }

    private String requireRepositoryPart(String value, String name) {
        String normalized = requireText(value, name);
        if (!REPOSITORY_PART_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, name + " 格式不正确");
        }
        return normalized;
    }

    private String normalizeBranch(String branch) {
        String normalized = requireText(branch, "分支");
        if (normalized.length() > MAX_BRANCH_LENGTH
                || normalized.contains("..")
                || normalized.contains("\\")
                || normalized.startsWith("/")
                || normalized.endsWith("/")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分支名称不合法");
        }
        return normalized;
    }

    private String resolveDefaultBranch(RepositorySearchProvider provider, String owner, String repo) {
        GiteeRepository repository = provider.getRepository(owner, repo);
        if (repository == null || !StringUtils.hasText(repository.defaultBranch())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无法确定仓库默认分支，请重新打开仓库详情后再下载");
        }
        return normalizeBranch(repository.defaultBranch());
    }

    private RepositorySearchProvider resolveProvider(String source) {
        if (!StringUtils.hasText(source)) {
            return providers.values().iterator().next();
        }
        String normalizedSource = source.trim();
        for (RepositorySearchProvider provider : providers.values()) {
            if (provider.name().equalsIgnoreCase(normalizedSource)) {
                return provider;
            }
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的数据来源：" + normalizedSource);
    }
}
