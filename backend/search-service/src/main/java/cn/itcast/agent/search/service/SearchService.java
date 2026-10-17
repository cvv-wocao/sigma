package cn.itcast.agent.search.service;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryBatchQuery;
import cn.itcast.agent.common.api.dto.RepositoryReadme;
import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import org.springframework.web.client.HttpClientErrorException;
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

    /** 主来源排在最前的有序 Map，保证 provider 顺序稳定可预期 */
    private final LinkedHashMap<String, RepositorySearchProvider> providers;
    private final RepositorySearchProperties searchProperties;
    private final RepositoryTextCleaner textCleaner;

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

    private String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, name + " 不能为空");
        }
        return value.trim();
    }
}