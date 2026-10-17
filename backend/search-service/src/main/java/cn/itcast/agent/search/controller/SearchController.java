package cn.itcast.agent.search.controller;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryBatchQuery;
import cn.itcast.agent.common.api.dto.RepositoryReadme;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.search.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 检索对外接口。
 * 对外路径由网关统一加 /api 前缀，网关 StripPrefix 后到达本服务的是 /search/**。
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 关键词检索仓库。
     * 对外：GET /api/search/repositories?keyword=xxx&page=1&perPage=10
     */
    @GetMapping("/repositories")
    public Result<List<GiteeRepository>> searchRepositories(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "perPage", required = false) Integer perPage) {
        return Result.success(searchService.searchRepositories(keyword, page, perPage));
    }

    /**
     * 多关键词批量检索，返回去重 + 过滤 + 按 Star 降序的候选池。
     * 对外：POST /api/search/repositories/batch，供 AI 推荐流水线一次召回使用。
     */
    @PostMapping("/repositories/batch")
    public Result<List<GiteeRepository>> searchRepositoriesBatch(@RequestBody RepositoryBatchQuery query) {
        return Result.success(searchService.searchBatch(query));
    }

    /**
     * 仓库详情。
     * 对外：GET /api/search/repositories/{owner}/{repo}
     */
    @GetMapping("/repositories/{owner}/{repo}")
    public Result<GiteeRepository> getRepository(@PathVariable("owner") String owner,
                                                 @PathVariable("repo") String repo) {
        return Result.success(searchService.getRepository(owner, repo));
    }

    /**
     * 仓库 README 正文，供 AI 推荐流水线做「项目深读」。
     * 读不到（仓库没有 README / 来源不支持）时返回 data 中 content 为 null 的成功结果。
     * 对外：GET /api/search/repositories/{owner}/{repo}/readme
     */
    @GetMapping("/repositories/{owner}/{repo}/readme")
    public Result<RepositoryReadme> getRepositoryReadme(@PathVariable("owner") String owner,
                                                        @PathVariable("repo") String repo) {
        return Result.success(searchService.getReadme(owner, repo));
    }

    /**
     * 仓库分支列表。
     * 对外：GET /api/search/repositories/{owner}/{repo}/branches
     */
    @GetMapping("/repositories/{owner}/{repo}/branches")
    public Result<List<GiteeBranch>> getBranches(@PathVariable("owner") String owner,
                                                 @PathVariable("repo") String repo) {
        return Result.success(searchService.getBranches(owner, repo));
    }
}