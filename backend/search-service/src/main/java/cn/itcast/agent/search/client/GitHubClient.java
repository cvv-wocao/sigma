package cn.itcast.agent.search.client;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryReadme;
import cn.itcast.agent.search.config.GitHubProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * GitHub REST API 客户端。
 *
 * <p>与本类同级的 GiteeClient 一样，只负责发起请求、反序列化与结构映射，
 * 不做业务判断、不包装异常：参数规整与前置校验在 SearchService，
 * 异常翻译在 GlobalExceptionHandler。</p>
 *
 * <p>为什么需要它：Gitee 官方 search/repositories 当前对任何关键词都返回空数组，
 * 无法作为「全网检索」的数据源；GitHub 检索接口稳定可用，且返回 star / license /
 * topics / updated_at 等做推荐排序所需的元数据。</p>
 *
 * <p>映射说明：GitHub 的响应结构与 Gitee 差异较大（license、owner 为嵌套对象），
 * 因此这里手工映射为共享 DTO {@link GiteeRepository}，而不是靠 JsonAlias 硬凑。</p>
 */
@Component
public class GitHubClient {

    /** 关键词检索仓库 */
    private static final String PATH_SEARCH_REPOSITORIES = "/search/repositories";
    /** 单个仓库详情 */
    private static final String PATH_REPOSITORY_DETAIL = "/repos/{owner}/{repo}";
    /** 仓库 README 正文 */
    private static final String PATH_REPOSITORY_README = "/repos/{owner}/{repo}/readme";
    /** README 截断长度：只用于提示词，超出部分对判断没有增益，反而撑大请求 */
    private static final int README_MAX_LENGTH = 6000;
    /** README 编码标记：GitHub 默认媒体类型返回 base64 */
    private static final String ENCODING_BASE64 = "base64";

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

    /** 仓库分支列表 */
    private static final String PATH_REPOSITORY_BRANCHES = "/repos/{owner}/{repo}/branches";

    private final GitHubProperties properties;
    private final RestClient restClient;

    public GitHubClient(GitHubProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "java-agent-platform/search-service")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();
        if (!StringUtils.hasText(properties.token())) {
            log.warn("未配置 GITHUB_TOKEN：GitHub 未认证调用按 IP 限流——核心接口 60 次/小时、"
                    + "搜索接口 10 次/分钟。检索一次消耗若干次、点开一个仓库详情要消耗 3 次"
                    + "（详情 + 分支 + README），很容易触发 403 rate limit，表现为"
                    + "「搜索突然搜不出来」「详情打不开」。请在 .env / .env.local 配置 GITHUB_TOKEN。");
        }
    }

    /**
     * 按关键词检索仓库，按 Star 降序返回；page 从 1 开始，perPage 为每页条数。
     *
     * <p>language / minStars / pushedAfter 会被拼成 GitHub 检索限定符
     * （language:java、stars:&gt;=1000、pushed:&gt;=2025-01-01）下推给平台。
     * 为什么要下推：GitHub 默认按 Star 降序返回，不下推的话每个关键词只能看到最热的
     * 十几条，冷门但贴合需求的仓库进不了候选池，模型也就永远看不到它们。</p>
     */
    public List<GiteeRepository> searchRepositories(String keyword, int page, int perPage,
                                                    String language, Integer minStars, String pushedAfter) {
        String query = buildQuery(keyword, language, minStars, pushedAfter);
        GitHubSearchResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(PATH_SEARCH_REPOSITORIES)
                        .queryParam("q", query)
                        .queryParam("sort", "stars")
                        .queryParam("order", "desc")
                        .queryParam("page", page)
                        .queryParam("per_page", perPage)
                        .build())
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubSearchResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream().map(this::toRepository).toList();
    }

    /**
     * 读取仓库 README 正文，供「项目深读」使用。
     *
     * <p>默认媒体类型下 /readme 返回 base64 编码的正文，这里解码后按 {@link #README_MAX_LENGTH}
     * 截断。仓库没有 README（404）等情况下返回 content 为 null 的对象，让调用方优雅退化为
     * 只用元数据判断，而不是把整条推荐链路打断。</p>
     */
    public RepositoryReadme getReadme(String owner, String repo) {
        String fullName = owner + "/" + repo;
        GitHubReadme readme;
        try {
            readme = restClient.get()
                    .uri(PATH_REPOSITORY_README, owner, repo)
                    .headers(this::applyAuthorization)
                    .retrieve()
                    .body(GitHubReadme.class);
        } catch (RestClientResponseException e) {
            log.debug("仓库没有可读的 README：repo={}, status={}", fullName, e.getStatusCode());
            return new RepositoryReadme(fullName, null, false);
        }
        if (readme == null || !StringUtils.hasText(readme.content())) {
            return new RepositoryReadme(fullName, null, false);
        }
        String text = decodeContent(readme);
        if (!StringUtils.hasText(text)) {
            return new RepositoryReadme(fullName, null, false);
        }
        boolean truncated = text.length() > README_MAX_LENGTH;
        return new RepositoryReadme(fullName, truncated ? text.substring(0, README_MAX_LENGTH) : text, truncated);
    }

    /** 把检索条件拼成 GitHub 查询串；关键词里的多个词天然是 AND 关系 */
    private String buildQuery(String keyword, String language, Integer minStars, String pushedAfter) {
        StringBuilder query = new StringBuilder(keyword.trim());
        if (StringUtils.hasText(language)) {
            query.append(" language:").append(language.trim().toLowerCase(Locale.ROOT));
        }
        if (minStars != null && minStars > 0) {
            query.append(" stars:>=").append(minStars);
        }
        if (StringUtils.hasText(pushedAfter)) {
            query.append(" pushed:>=").append(pushedAfter.trim());
        }
        return query.toString();
    }

    private String decodeContent(GitHubReadme readme) {
        if (!ENCODING_BASE64.equalsIgnoreCase(readme.encoding())) {
            return readme.content();
        }
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(readme.content());
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("README base64 解码失败：path={}, error={}", readme.path(), e.getMessage());
            return null;
        }
    }

    /** 查询单个仓库详情，仓库不存在时由调用方处理 404 异常 */
    public GiteeRepository getRepository(String owner, String repo) {
        GitHubItem item = restClient.get()
                .uri(PATH_REPOSITORY_DETAIL, owner, repo)
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubItem.class);
        return item == null ? null : toRepository(item);
    }

    /** 查询仓库分支列表 */
    public List<GiteeBranch> getBranches(String owner, String repo) {
        GitHubBranch[] branches = restClient.get()
                .uri(PATH_REPOSITORY_BRANCHES, owner, repo)
                .headers(this::applyAuthorization)
                .retrieve()
                .body(GitHubBranch[].class);
        if (branches == null) {
            return List.of();
        }
        return Arrays.stream(branches).map(branch -> new GiteeBranch(branch.name())).toList();
    }

    private void applyAuthorization(HttpHeaders headers) {
        if (StringUtils.hasText(properties.token())) {
            headers.setBearerAuth(properties.token());
        }
    }

    /** 数据来源标记由 provider 层统一补上，客户端只做结构映射 */
    private GiteeRepository toRepository(GitHubItem item) {
        return new GiteeRepository(
                item.name(),
                item.fullName(),
                item.description(),
                item.language(),
                item.stars(),
                item.forks(),
                item.defaultBranch(),
                item.htmlUrl(),
                item.licenseName(),
                item.topics() == null ? List.of() : item.topics(),
                item.updatedAt(),
                null);
    }

    /** GitHub 检索响应外层结构 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GitHubSearchResponse(@JsonAlias("total_count") Integer totalCount, List<GitHubItem> items) {
    }

    /** GitHub 仓库条目，只声明本项目用得到的字段 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GitHubItem(
            String name,
            @JsonAlias("full_name") String fullName,
            String description,
            String language,
            @JsonAlias("stargazers_count") Integer stars,
            @JsonAlias("forks_count") Integer forks,
            @JsonAlias("default_branch") String defaultBranch,
            @JsonAlias("html_url") String htmlUrl,
            GitHubLicense license,
            List<String> topics,
            @JsonAlias("updated_at") String updatedAt
    ) {

        /** 协议名称：优先用 SPDX 标识（MIT / Apache-2.0），缺失时退回协议全称 */
        String licenseName() {
            if (license == null) {
                return null;
            }
            return StringUtils.hasText(license.spdxId()) ? license.spdxId() : license.name();
        }
    }

    /** GitHub 协议对象 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GitHubLicense(@JsonAlias("spdx_id") String spdxId, String name) {
    }

    /** GitHub README 响应：默认媒体类型返回 base64 编码的正文 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GitHubReadme(String name, String path, Integer size, String encoding, String content) {
    }

    /** GitHub 分支条目，字段名与 Gitee 一致，可直接复用 GiteeBranch */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GitHubBranch(String name) {
    }
}