package cn.itcast.agent.ai.tool;

import cn.itcast.agent.common.api.client.SearchServiceClient;
import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

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
                    .append('\n')
                    .append("   地址：").append(text(repository.htmlUrl())).append('\n');
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
                + "默认分支：" + text(repository.defaultBranch()) + '\n'
                + "地址：" + text(repository.htmlUrl());
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