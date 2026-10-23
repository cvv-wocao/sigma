package cn.itcast.agent.search.controller;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryBatchQuery;
import cn.itcast.agent.common.api.dto.RepositoryFileContent;
import cn.itcast.agent.common.api.dto.RepositoryFileListing;
import cn.itcast.agent.common.api.dto.RepositoryReadme;
import cn.itcast.agent.common.api.dto.RepositoryRelease;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.search.client.RepositoryArchiveStream;
import cn.itcast.agent.search.service.SearchService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    /**
     * 直接下载仓库 ZIP 归档。
     * 对外：GET /api/search/repositories/{owner}/{repo}/archive?branch=main&amp;source=github
     *
     * <p>响应不是统一 Result 结构，而是 ZIP 二进制流。前端使用 Blob 接收后触发浏览器保存；
     * 服务端边读上游边写响应，避免大仓库占用大量堆内存。</p>
     */
    @GetMapping("/repositories/{owner}/{repo}/releases")
    public Result<List<RepositoryRelease>> listReleases(
            @PathVariable("owner") String owner,
            @PathVariable("repo") String repo,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "source", required = false) String source) {
        return Result.success(searchService.getReleases(owner, repo, limit, source));
    }

    @GetMapping("/repositories/{owner}/{repo}/contents")
    public Result<RepositoryFileListing> listRepositoryFiles(
            @PathVariable("owner") String owner,
            @PathVariable("repo") String repo,
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "branch", required = false) String branch,
            @RequestParam(value = "source", required = false) String source) {
        return Result.success(searchService.listFiles(owner, repo, path, branch, source));
    }

    @GetMapping("/repositories/{owner}/{repo}/file")
    public Result<RepositoryFileContent> readRepositoryFile(
            @PathVariable("owner") String owner,
            @PathVariable("repo") String repo,
            @RequestParam("path") String path,
            @RequestParam(value = "branch", required = false) String branch,
            @RequestParam(value = "source", required = false) String source) {
        return Result.success(searchService.readFile(owner, repo, path, branch, source));
    }

    @GetMapping("/repositories/{owner}/{repo}/archive")
    public void downloadArchive(@PathVariable("owner") String owner,
                                @PathVariable("repo") String repo,
                                @RequestParam(value = "branch", required = false) String branch,
                                @RequestParam(value = "source", required = false) String source,
                                HttpServletResponse response) throws IOException {
        RepositoryArchiveStream archive = searchService.openArchive(owner, repo, branch, source);
        try (archive; InputStream input = archive.inputStream()) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment()
                            .filename(archive.fileName(), StandardCharsets.UTF_8)
                            .build()
                            .toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            if (archive.contentLength() > 0) {
                response.setContentLengthLong(archive.contentLength());
            }
            input.transferTo(response.getOutputStream());
            response.flushBuffer();
        }
    }
}
