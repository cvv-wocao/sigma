package cn.itcast.agent.search.client;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryFileContent;
import cn.itcast.agent.common.api.dto.RepositoryFileEntry;
import cn.itcast.agent.common.api.dto.RepositoryRelease;
import cn.itcast.agent.common.api.dto.RepositoryReleaseAsset;
import cn.itcast.agent.search.config.GiteeProperties;
import cn.itcast.agent.search.support.RepositoryContentSupport;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
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
    private static final String PATH_REPOSITORY_RELEASES = "/api/v5/repos/{owner}/{repo}/releases";
    private static final String PATH_REPOSITORY_CONTENTS = "/api/v5/repos/{owner}/{repo}/contents";
    private static final int MAX_RELEASE_ASSETS = 50;

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

    public List<RepositoryRelease> getReleases(String owner, String repo, int limit) {
        GiteeRelease[] releases = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(PATH_REPOSITORY_RELEASES)
                            .queryParam("per_page", limit);
                    appendAccessToken(uriBuilder);
                    return uriBuilder.build(owner, repo);
                })
                .retrieve()
                .body(GiteeRelease[].class);
        if (releases == null) {
            return List.of();
        }
        return Arrays.stream(releases).map(release -> toRelease(owner, repo, release)).toList();
    }

    public List<RepositoryFileEntry> listFiles(String owner, String repo, String path, String branch) {
        JsonNode node = getContents(owner, repo, path, branch);
        if (node == null) {
            return List.of();
        }
        if (node.isArray()) {
            return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                    .map(this::toFileEntry)
                    .toList();
        }
        return node.isObject() ? List.of(toFileEntry(node)) : List.of();
    }

    public RepositoryFileContent readFile(String owner, String repo, String path, String branch) {
        String fullName = owner + "/" + repo;
        JsonNode node = getContents(owner, repo, path, branch);
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            return new RepositoryFileContent(fullName, path, branch, null, null, false, false, true, false);
        }
        String type = text(node, "type");
        long size = longValue(node, "size", 0L);
        Long publicSize = size > 0 ? size : null;
        if ("dir".equalsIgnoreCase(type)) {
            return new RepositoryFileContent(fullName, path, branch, null, publicSize, false, false, true, false);
        }
        if (!"file".equalsIgnoreCase(type)) {
            return new RepositoryFileContent(fullName, path, branch, null, publicSize, false, true, false, false);
        }
        if (size > RepositoryContentSupport.MAX_FILE_BYTES) {
            return new RepositoryFileContent(fullName, path, branch, null, publicSize, false, false, false, true);
        }
        if (RepositoryContentSupport.isBinaryPath(path)) {
            return new RepositoryFileContent(fullName, path, branch, null, publicSize, false, true, false, false);
        }
        String content = text(node, "content");
        if (!StringUtils.hasText(content)) {
            return new RepositoryFileContent(fullName, path, branch, "", publicSize, false, false, false, false);
        }
        String encoding = text(node, "encoding");
        if (!"base64".equalsIgnoreCase(encoding)) {
            return new RepositoryFileContent(fullName, path, branch, content, publicSize, false, false, false, false);
        }
        byte[] decoded = RepositoryContentSupport.decodeBase64(content);
        if (decoded == null) {
            return new RepositoryFileContent(fullName, path, branch, null, publicSize, false, true, false, false);
        }
        if (RepositoryContentSupport.looksBinary(decoded)) {
            return new RepositoryFileContent(fullName, path, branch, null, publicSize, false, true, false, false);
        }
        return new RepositoryFileContent(fullName, path, branch,
                RepositoryContentSupport.toText(decoded), publicSize, false, false, false, false);
    }

    /** 打开指定分支的仓库 ZIP 归档流，下载过程不经过 Gitee OpenAPI */
    public RepositoryArchiveStream openArchive(String owner, String repo, String branch) {
        String url = stripTrailingSlash(properties.baseUrl())
                + "/" + ArchiveHttpSupport.encodePathSegment(owner)
                + "/" + ArchiveHttpSupport.encodePathSegment(repo)
                + "/repository/archive/" + ArchiveHttpSupport.encodeRef(branch) + ".zip";
        return ArchiveHttpSupport.open(url, ArchiveHttpSupport.archiveFileName(owner, repo, branch));
    }

    private JsonNode getContents(String owner, String repo, String path, String branch) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(PATH_REPOSITORY_CONTENTS);
                        if (StringUtils.hasText(path)) {
                            for (String segment : path.split("/")) {
                                if (!segment.isEmpty()) {
                                    uriBuilder.pathSegment(segment);
                                }
                            }
                        }
                        if (StringUtils.hasText(branch)) {
                            uriBuilder.queryParam("ref", branch);
                        }
                        appendAccessToken(uriBuilder);
                        return uriBuilder.build(owner, repo);
                    })
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw e;
        }
    }

    private RepositoryFileEntry toFileEntry(JsonNode node) {
        String type = text(node, "type");
        long size = longValue(node, "size", 0L);
        return new RepositoryFileEntry(
                text(node, "name"),
                text(node, "path"),
                StringUtils.hasText(type) ? type : "file",
                "dir".equalsIgnoreCase(type) || size <= 0 ? null : size,
                text(node, "html_url"));
    }

    private RepositoryRelease toRelease(String owner, String repo, GiteeRelease release) {
        List<GiteeAsset> rawAssets = release.assets() == null ? List.of() : release.assets();
        List<RepositoryReleaseAsset> assets = rawAssets.stream()
                .limit(MAX_RELEASE_ASSETS)
                .map(asset -> new RepositoryReleaseAsset(asset.name(), asset.contentType(), asset.size(),
                        asset.downloadCount(), asset.browserDownloadUrl()))
                .toList();
        String publishedAt = StringUtils.hasText(release.publishedAt())
                ? release.publishedAt() : release.createdAt();
        String htmlUrl = StringUtils.hasText(release.htmlUrl())
                ? release.htmlUrl() : releasePageUrl(owner, repo, release.tagName());
        return new RepositoryRelease(
                release.tagName(),
                StringUtils.hasText(release.name()) ? release.name() : release.tagName(),
                Boolean.TRUE.equals(release.prerelease()),
                publishedAt,
                htmlUrl,
                release.body(),
                false,
                rawAssets.size(),
                assets);
    }

    private String releasePageUrl(String owner, String repo, String tagName) {
        if (!StringUtils.hasText(tagName)) {
            return null;
        }
        return stripTrailingSlash(properties.baseUrl())
                + "/" + ArchiveHttpSupport.encodePathSegment(owner)
                + "/" + ArchiveHttpSupport.encodePathSegment(repo)
                + "/releases/tag/" + ArchiveHttpSupport.encodeRef(tagName);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private long longValue(JsonNode node, String field, long defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asLong(defaultValue);
    }

    private void appendAccessToken(UriBuilder uriBuilder) {
        if (StringUtils.hasText(properties.accessToken())) {
            uriBuilder.queryParam("access_token", properties.accessToken());
        }
    }

    private <T> List<T> toList(T[] array) {
        return array == null ? List.of() : Arrays.asList(array);
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GiteeRelease(
            @JsonAlias("tag_name") String tagName,
            String name,
            Boolean prerelease,
            @JsonAlias("created_at") String createdAt,
            @JsonAlias("published_at") String publishedAt,
            @JsonAlias("html_url") String htmlUrl,
            String body,
            @JsonAlias("attach_files") List<GiteeAsset> assets
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GiteeAsset(
            String name,
            @JsonAlias("browser_download_url") String browserDownloadUrl,
            @JsonAlias("content_type") String contentType,
            Long size,
            @JsonAlias("download_count") Long downloadCount
    ) {
    }
}
