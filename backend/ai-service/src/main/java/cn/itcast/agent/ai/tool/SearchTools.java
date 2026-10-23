package cn.itcast.agent.ai.tool;

import cn.itcast.agent.common.api.client.SearchServiceClient;
import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryFileContent;
import cn.itcast.agent.common.api.dto.RepositoryFileEntry;
import cn.itcast.agent.common.api.dto.RepositoryFileListing;
import cn.itcast.agent.common.api.dto.RepositoryRelease;
import cn.itcast.agent.common.api.dto.RepositoryReleaseAsset;
import cn.itcast.agent.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Agent 工具集：把「检索能力」以工具的形式暴露给大模型。
 *
 * <p>调用链：大模型决定调用工具 -> 本类 -> Feign(SearchServiceClient) -> Nacos 服务发现 ->
 * search-service -> GitHub / Gitee OpenAPI。ai-service 不直接访问外部平台，第三方接入统一收敛在 search-service。</p>
 *
 * <p>返回值统一为 String：工具的返回值会作为工具消息回填给模型，纯文本最利于模型阅读，
 * 也避免模型面对 JSON 结构时产生额外解析负担。</p>
 */
@Component
public class SearchTools {

    private static final Logger log = LoggerFactory.getLogger(SearchTools.class);

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 10;
    private static final int DEFAULT_LIMIT = 5;

    private final SearchServiceClient searchServiceClient;

    public SearchTools(SearchServiceClient searchServiceClient) {
        this.searchServiceClient = searchServiceClient;
    }

    @Tool(name = "search_repositories",
            description = "按关键词搜索开源仓库（检索源以 GitHub 为主、Gitee 兜底），返回仓库全名、简介、主要语言、Star 数和仓库地址。当用户想找开源项目、想找某个主题的参考实现时使用。")
    public String searchRepositories(
            @ToolParam(description = "搜索关键词，例如 java、spring boot、agent、工作流") String keyword,
            @ToolParam(description = "返回条数，取值范围 1-10，默认 5", required = false) Integer limit) {

        if (keyword == null || keyword.isBlank()) {
            return "搜索关键词为空，请提供关键词后重试。";
        }
        int size = normalizeLimit(limit);
        log.info("工具调用 search_repositories: keyword={}, limit={}", keyword, size);

        Result<List<GiteeRepository>> result;
        try {
            result = searchServiceClient.searchRepositories(keyword, 1, size);
        } catch (Exception e) {
            log.error("调用 search-service 搜索仓库失败：keyword={}", keyword, e);
            return "检索服务暂时不可用（" + e.getClass().getSimpleName() + "），请稍后重试。";
        }
        if (result == null || !result.isSuccess()) {
            return "检索失败：" + (result == null ? "检索服务无响应" : result.getMessage());
        }

        List<GiteeRepository> repositories = result.getData();
        if (repositories == null || repositories.isEmpty()) {
            return "没有搜索到与「" + keyword + "」相关的仓库。可以换一个更通用的关键词，或改用英文关键词再试。";
        }

        StringBuilder builder = new StringBuilder("共找到 ").append(repositories.size())
                .append(" 个与「").append(keyword).append("」相关的仓库：\n");
        int index = 1;
        for (GiteeRepository repository : repositories) {
            builder.append(index++).append(". ").append(text(repository.fullName())).append('\n')
                    .append("   简介：").append(text(repository.description())).append('\n')
                    .append("   语言：").append(text(repository.language()))
                    .append("；Star：").append(repository.stars() == null ? "未知" : repository.stars())
                    .append("；来源：").append(text(repository.source())).append('\n')
                    .append("   地址：").append(text(repository.htmlUrl())).append('\n');
            if (!isBlank(repository.homepage())) {
                builder.append("   官网：").append(repository.homepage().trim()).append('\n');
            }
        }
        return builder.toString();
    }

    @Tool(name = "get_repository_detail",
            description = "查询指定开源仓库的详情。需要同时提供 owner（仓库所属的空间地址）和 repo（仓库名），例如 owner=macrozheng、repo=mall。")
    public String getRepositoryDetail(
            @ToolParam(description = "仓库所属空间地址，例如 macrozheng") String owner,
            @ToolParam(description = "仓库名，例如 mall") String repo) {

        if (isBlank(owner) || isBlank(repo)) {
            return "参数不完整，需要同时提供 owner 与 repo，例如 owner=macrozheng、repo=mall。";
        }
        log.info("工具调用 get_repository_detail: owner={}, repo={}", owner, repo);

        Result<GiteeRepository> result;
        try {
            result = searchServiceClient.getRepository(owner, repo);
        } catch (Exception e) {
            log.error("调用 search-service 查询仓库详情失败：{}/{}", owner, repo, e);
            return "检索服务暂时不可用（" + e.getClass().getSimpleName() + "），请稍后重试。";
        }
        if (result == null || !result.isSuccess()) {
            return "查询失败：" + (result == null ? "检索服务无响应" : result.getMessage());
        }

        GiteeRepository repository = result.getData();
        if (repository == null) {
            return "没有查询到仓库 " + owner + "/" + repo + "。";
        }
        return "仓库全名：" + text(repository.fullName()) + '\n'
                + "简介：" + text(repository.description()) + '\n'
                + "主要语言：" + text(repository.language()) + '\n'
                + "Star：" + (repository.stars() == null ? "未知" : repository.stars())
                + "；Fork：" + (repository.forks() == null ? "未知" : repository.forks()) + '\n'
                + "开源协议：" + text(repository.license()) + '\n'
                + "默认分支：" + text(repository.defaultBranch()) + '\n'
                + "代码仓库：" + text(repository.htmlUrl()) + '\n'
                + "项目官网：" + text(repository.homepage()) + '\n'
                + "数据来源：" + text(repository.source());
    }

    @Tool(name = "list_repository_branches",
            description = "查询指定开源仓库的分支列表。需要同时提供 owner 和 repo，例如 owner=macrozheng、repo=mall。")
    public String listBranches(
            @ToolParam(description = "仓库所属空间地址，例如 macrozheng") String owner,
            @ToolParam(description = "仓库名，例如 mall") String repo) {

        if (isBlank(owner) || isBlank(repo)) {
            return "参数不完整，需要同时提供 owner 与 repo，例如 owner=macrozheng、repo=mall。";
        }
        log.info("工具调用 list_repository_branches: owner={}, repo={}", owner, repo);

        Result<List<GiteeBranch>> result;
        try {
            result = searchServiceClient.getBranches(owner, repo);
        } catch (Exception e) {
            log.error("调用 search-service 查询分支失败：{}/{}", owner, repo, e);
            return "检索服务暂时不可用（" + e.getClass().getSimpleName() + "），请稍后重试。";
        }
        if (result == null || !result.isSuccess()) {
            return "查询失败：" + (result == null ? "检索服务无响应" : result.getMessage());
        }

        List<GiteeBranch> branches = result.getData();
        if (branches == null || branches.isEmpty()) {
            return "仓库 " + owner + "/" + repo + " 没有查询到分支。";
        }
        StringBuilder builder = new StringBuilder("仓库 ").append(owner).append('/').append(repo)
                .append(" 共有 ").append(branches.size()).append(" 个分支：");
        for (GiteeBranch branch : branches) {
            builder.append(' ').append(text(branch.name()));
        }
        return builder.toString();
    }

    @Tool(name = "list_repository_releases",
            description = "查询仓库的 Release 版本和附件（安装包、二进制包、压缩包）。用户问“安装包在哪下载”“有没有 Windows/Linux 版本”“最新版本是什么”时必须先调用。若返回没有 Release，不代表项目不能用，应继续用 list_repository_files 查看 README 和构建配置，判断源码运行方式。")
    public String listRepositoryReleases(
            @ToolParam(description = "仓库所属空间地址，例如 junegunn") String owner,
            @ToolParam(description = "仓库名，例如 fzf") String repo,
            @ToolParam(description = "返回版本数，取值范围 1-10，默认 5", required = false) Integer limit,
            @ToolParam(description = "数据来源 github 或 gitee；可从仓库详情结果中原样传入，不确定时留空", required = false) String source) {

        if (isBlank(owner) || isBlank(repo)) {
            return "参数不完整，需要同时提供 owner 与 repo。";
        }
        int size = normalizeLimit(limit);
        log.info("工具调用 list_repository_releases: repo={}/{}, limit={}, source={}", owner, repo, size, source);

        Result<List<RepositoryRelease>> result;
        try {
            result = searchServiceClient.listReleases(owner, repo, size, source);
        } catch (Exception e) {
            log.error("调用 search-service 查询 Release 失败：{}/{}", owner, repo, e);
            return "查询 Release 失败（" + e.getClass().getSimpleName() + "），请稍后重试。";
        }
        if (result == null || !result.isSuccess()) {
            return "查询 Release 失败：" + (result == null ? "检索服务无响应" : result.getMessage());
        }

        List<RepositoryRelease> releases = result.getData();
        if (releases == null || releases.isEmpty()) {
            return "仓库 " + owner + "/" + repo + " 没有查询到 Release。"
                    + "这不等于项目不能使用或不能下载：请继续调用 list_repository_files 查看仓库根目录，"
                    + "再读取 README、package.json、pom.xml、build.gradle、docker-compose.yml 等文件，"
                    + "判断源码下载后的安装与启动方式；也可以直接使用页面的“下载 ZIP”获取源码。";
        }

        StringBuilder builder = new StringBuilder("仓库 ").append(owner).append('/').append(repo)
                .append(" 最近 ").append(releases.size()).append(" 个 Release：\n");
        int index = 1;
        for (RepositoryRelease release : releases) {
            builder.append(index++).append(". ").append(text(release.name()));
            if (!isBlank(release.tagName())) {
                builder.append("（标签：").append(release.tagName()).append("）");
            }
            if (release.prerelease()) {
                builder.append(" [预发布]");
            }
            builder.append('\n');
            builder.append("   发布时间：").append(dateOnly(release.publishedAt())).append('\n');
            builder.append("   Release 页面：").append(text(release.htmlUrl())).append('\n');
            if (!isBlank(release.body())) {
                builder.append("   发布说明：").append(preview(release.body(), 1200)).append('\n');
            }
            int assetCount = release.assetCount();
            if (assetCount <= 0) {
                builder.append("   附件：该版本没有二进制附件；可以继续查看源码目录或用 README 判断启动方式。\n");
                continue;
            }
            List<RepositoryReleaseAsset> assets = release.assets() == null ? List.of() : release.assets();
            builder.append("   附件（共 ").append(assetCount).append(" 个");
            if (assets.size() < assetCount) {
                builder.append("，列出前 ").append(assets.size()).append(" 个");
            }
            builder.append("）：\n");
            for (RepositoryReleaseAsset asset : assets) {
                builder.append("   - ").append(text(asset.name()));
                if (asset.size() != null) {
                    builder.append("（").append(formatSize(asset.size())).append("）");
                }
                if (asset.downloadCount() != null) {
                    builder.append("，下载 ").append(asset.downloadCount()).append(" 次");
                }
                builder.append('\n');
                builder.append("     下载地址：").append(text(asset.downloadUrl())).append('\n');
            }
        }
        return builder.toString();
    }

    @Tool(name = "list_repository_files",
            description = "查看仓库目录结构，返回指定目录下的文件、子目录、路径和大小。用户问“项目怎么启动”“入口文件在哪”“是什么技术栈”“有没有 Docker 配置”，或者仓库没有 Release 时使用。先查根目录，再按需读取关键文件。")
    public String listRepositoryFiles(
            @ToolParam(description = "仓库所属空间地址，例如 macrozheng") String owner,
            @ToolParam(description = "仓库名，例如 mall") String repo,
            @ToolParam(description = "目录路径，空表示根目录，例如 docs 或 src/main", required = false) String path,
            @ToolParam(description = "分支，空表示仓库默认分支", required = false) String branch,
            @ToolParam(description = "数据来源 github 或 gitee；不确定时留空", required = false) String source) {

        if (isBlank(owner) || isBlank(repo)) {
            return "参数不完整，需要同时提供 owner 与 repo。";
        }
        log.info("工具调用 list_repository_files: repo={}/{}, path={}, branch={}, source={}",
                owner, repo, path, branch, source);

        Result<RepositoryFileListing> result;
        try {
            result = searchServiceClient.listRepositoryFiles(owner, repo, path, branch, source);
        } catch (Exception e) {
            log.error("调用 search-service 查询仓库目录失败：{}/{}", owner, repo, e);
            return "查询仓库目录失败（" + e.getClass().getSimpleName() + "），请稍后重试。";
        }
        if (result == null || !result.isSuccess()) {
            return "查询仓库目录失败：" + (result == null ? "检索服务无响应" : result.getMessage());
        }

        RepositoryFileListing listing = result.getData();
        if (listing == null || listing.entries() == null || listing.entries().isEmpty()) {
            return "仓库 " + owner + "/" + repo + " 的目录"
                    + (isBlank(path) ? "根目录" : " " + path) + " 没有查询到条目。";
        }
        List<RepositoryFileEntry> entries = listing.entries().stream()
                .sorted(Comparator.comparing((RepositoryFileEntry entry) -> !"dir".equalsIgnoreCase(entry.type()))
                        .thenComparing(entry -> entry.name() == null ? "" : entry.name()))
                .toList();
        StringBuilder builder = new StringBuilder("仓库 ").append(owner).append('/').append(repo)
                .append(" 的目录").append(isBlank(path) ? "根目录" : " " + path)
                .append(" 共 ").append(listing.total()).append(" 项");
        if (listing.truncated()) {
            builder.append("（仅显示前 ").append(entries.size()).append(" 项）");
        }
        builder.append("：\n");
        for (RepositoryFileEntry entry : entries) {
            String typeLabel = "dir".equalsIgnoreCase(entry.type()) ? "[目录]"
                    : "file".equalsIgnoreCase(entry.type()) ? "[文件]" : "[" + text(entry.type()) + "]";
            builder.append(typeLabel).append(' ').append(text(entry.path()));
            if (entry.size() != null) {
                builder.append("（").append(formatSize(entry.size())).append("）");
            }
            builder.append('\n');
        }
        builder.append("下一步可根据项目类型读取关键文件：README、package.json、pom.xml、build.gradle、")
                .append("requirements.txt、pyproject.toml、docker-compose.yml、Dockerfile、Makefile。");
        return builder.toString();
    }

    @Tool(name = "read_repository_file",
            description = "读取仓库中的单个文本文件，用于查看 README、package.json、pom.xml、Dockerfile、启动脚本或源码。用户问安装步骤、启动命令、入口文件、配置方式时使用。二进制、目录和超大文件会明确返回原因。")
    public String readRepositoryFile(
            @ToolParam(description = "仓库所属空间地址，例如 macrozheng") String owner,
            @ToolParam(description = "仓库名，例如 mall") String repo,
            @ToolParam(description = "文件相对路径，例如 README.md 或 package.json") String path,
            @ToolParam(description = "分支，空表示仓库默认分支", required = false) String branch,
            @ToolParam(description = "数据来源 github 或 gitee；不确定时留空", required = false) String source) {

        if (isBlank(owner) || isBlank(repo) || isBlank(path)) {
            return "参数不完整，需要同时提供 owner、repo 与 path。";
        }
        log.info("工具调用 read_repository_file: repo={}/{}, path={}, branch={}, source={}",
                owner, repo, path, branch, source);

        Result<RepositoryFileContent> result;
        try {
            result = searchServiceClient.readRepositoryFile(owner, repo, path, branch, source);
        } catch (Exception e) {
            log.error("调用 search-service 读取仓库文件失败：{}/{}/{}", owner, repo, path, e);
            return "读取仓库文件失败（" + e.getClass().getSimpleName() + "），请稍后重试。";
        }
        if (result == null || !result.isSuccess()) {
            return "读取仓库文件失败：" + (result == null ? "检索服务无响应" : result.getMessage());
        }

        RepositoryFileContent file = result.getData();
        if (file == null) {
            return "没有读取到文件 " + path + "。";
        }
        if (file.directory()) {
            return path + " 是一个目录，请改用 list_repository_files 查看其中的条目。";
        }
        if (file.binary()) {
            return path + " 是二进制文件或符号链接，无法作为文本读取。";
        }
        if (file.tooLarge()) {
            return path + " 超过单文件读取上限（512 KB），请改为读取 README 或更具体的配置文件。";
        }
        if (file.content() == null) {
            return "没有读取到 " + path + " 的文本内容，请确认路径与分支是否正确。";
        }

        StringBuilder builder = new StringBuilder("文件：").append(file.path())
                .append("\n分支：").append(text(file.branch()))
                .append("\n大小：").append(file.size() == null ? "未知" : formatSize(file.size()));
        if (file.truncated()) {
            builder.append("\n注意：内容超过 30000 字符，已截断。");
        }
        builder.append("\n--- 文件内容开始 ---\n").append(file.content())
                .append("\n--- 文件内容结束 ---");
        return builder.toString();
    }

    private String dateOnly(String value) {
        if (isBlank(value)) {
            return "未知";
        }
        return value.length() >= 10 ? value.substring(0, 10) : value;
    }

    private String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "…";
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes < 0) {
            return "大小未知";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        return String.format("%.1f MB", kb / 1024.0);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String text(String value) {
        return isBlank(value) ? "无" : value.trim();
    }
}
