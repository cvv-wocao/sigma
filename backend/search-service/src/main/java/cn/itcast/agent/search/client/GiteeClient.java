package cn.itcast.agent.search.client;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.search.config.GiteeProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * Gitee OpenAPI v5 客户端。
 *
 * <p>本类只负责发起请求与反序列化，不做业务判断、不包装异常：
 * 参数规整与前置校验在 SearchService，异常翻译在 GlobalExceptionHandler。</p>
 *
 * <p>接口鉴权情况（均已实测）：
 * <ul>
 *   <li>仓库详情 / 分支列表：免鉴权即可返回数据</li>
 *   <li>关键词搜索 / 授权用户仓库列表：需要 access_token</li>
 * </ul>
 */
@Component
public class GiteeClient {

    /** 关键词搜索仓库（官方接口），需要 access_token */
    private static final String PATH_SEARCH_REPOSITORIES = "/api/v5/search/repositories";
    /** 授权用户自己的仓库，支持 q 关键字筛选，需要 access_token；用作官方搜索不可用时的降级数据源 */
    private static final String PATH_USER_REPOSITORIES = "/api/v5/user/repos";
    /** 仓库详情，免鉴权 */
    private static final String PATH_REPOSITORY_DETAIL = "/api/v5/repos/{owner}/{repo}";
    /** 分支列表，免鉴权 */
    private static final String PATH_REPOSITORY_BRANCHES = "/api/v5/repos/{owner}/{repo}/branches";

    private final GiteeProperties properties;
    private final RestClient restClient;

    public GiteeClient(GiteeProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "java-agent-platform/search-service")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    /** 按关键词搜索仓库，page 从 1 开始，perPage 为每页条数 */
    public List<GiteeRepository> searchRepositories(String keyword, int page, int perPage) {
        GiteeRepository[] repositories = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(PATH_SEARCH_REPOSITORIES)
                            .queryParam("q", keyword)
                            .queryParam("page", page)
                            .queryParam("per_page", perPage);
                    appendAccessToken(uriBuilder);
                    return uriBuilder.build();
                })
                .retrieve()
                .body(GiteeRepository[].class);
        return toList(repositories);
    }

    /**
     * 在授权用户自己的仓库中按关键字筛选。
     * Gitee 官方仓库搜索接口当前恒返回空数组（服务端问题），本方法作为降级数据源使用。
     */
    public List<GiteeRepository> searchOwnRepositories(String keyword, int page, int perPage) {
        GiteeRepository[] repositories = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(PATH_USER_REPOSITORIES)
                            .queryParam("q", keyword)
                            .queryParam("page", page)
                            .queryParam("per_page", perPage);
                    appendAccessToken(uriBuilder);
                    return uriBuilder.build();
                })
                .retrieve()
                .body(GiteeRepository[].class);
        return toList(repositories);
    }

    /** 查询单个仓库详情 */
    public GiteeRepository getRepository(String owner, String repo) {
        return restClient.get()
                .uri(PATH_REPOSITORY_DETAIL, owner, repo)
                .retrieve()
                .body(GiteeRepository.class);
    }

    /** 查询仓库分支列表 */
    public List<GiteeBranch> getBranches(String owner, String repo) {
        GiteeBranch[] branches = restClient.get()
                .uri(PATH_REPOSITORY_BRANCHES, owner, repo)
                .retrieve()
                .body(GiteeBranch[].class);
        return toList(branches);
    }

    private void appendAccessToken(UriBuilder uriBuilder) {
        if (StringUtils.hasText(properties.accessToken())) {
            uriBuilder.queryParam("access_token", properties.accessToken());
        }
    }

    private <T> List<T> toList(T[] array) {
        return array == null ? List.of() : Arrays.asList(array);
    }
}