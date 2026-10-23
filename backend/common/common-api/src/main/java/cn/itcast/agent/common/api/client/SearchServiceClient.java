package cn.itcast.agent.common.api.client;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryBatchQuery;
import cn.itcast.agent.common.api.dto.RepositoryFileContent;
import cn.itcast.agent.common.api.dto.RepositoryFileListing;
import cn.itcast.agent.common.api.dto.RepositoryReadme;
import cn.itcast.agent.common.api.dto.RepositoryRelease;
import cn.itcast.agent.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 检索服务（search-service）的 Feign 客户端声明。
 * <p>放在 common-api 是为了让「接口契约」只有一份：search-service 定义实现（SearchController），
 * 调用方（当前是 ai-service）直接依赖本接口，路径与方法名不会两边各写一套而漂移。</p>
 *
 * <p>path = "/search" 对应 search-service 内部路径前缀（网关对外会再加 /api），
 * 服务名 "search-service" 由 Nacos 服务发现解析，无需写死 IP 与端口。</p>
 */
@FeignClient(name = "search-service", path = "/search")
public interface SearchServiceClient {

    /**
     * 按关键词搜索仓库。
     *
     * @param keyword 搜索关键词
     * @param page    页码，从 1 开始
     * @param perPage 每页条数，上限 50
     */
    @GetMapping("/repositories")
    Result<List<GiteeRepository>> searchRepositories(@RequestParam("keyword") String keyword,
                                                     @RequestParam("page") Integer page,
                                                     @RequestParam("perPage") Integer perPage);

    /**
     * 多关键词批量检索：逐个关键词检索后按 fullName 去重合并，并施加硬过滤。
     * 供 AI 推荐流水线一次召回候选池使用。
     */
    @PostMapping("/repositories/batch")
    Result<List<GiteeRepository>> searchRepositoriesBatch(@RequestBody RepositoryBatchQuery query);

    /** 查询仓库详情，例如 owner=macrozheng, repo=mall */
    @GetMapping("/repositories/{owner}/{repo}")
    Result<GiteeRepository> getRepository(@PathVariable("owner") String owner,
                                          @PathVariable("repo") String repo);

    /**
     * 查询仓库 README 正文，供 AI 推荐流水线做「项目深读」。
     * 仓库没有 README、或来源平台不支持时返回 data 为 null 的成功结果，不抛异常。
     */
    @GetMapping("/repositories/{owner}/{repo}/readme")
    Result<RepositoryReadme> getRepositoryReadme(@PathVariable("owner") String owner,
                                                 @PathVariable("repo") String repo);

    /** 查询仓库分支列表 */
    @GetMapping("/repositories/{owner}/{repo}/branches")
    Result<List<GiteeBranch>> getBranches(@PathVariable("owner") String owner,
                                          @PathVariable("repo") String repo);

    /**
     * 查询仓库 Release 与附件（安装包）列表，按发布时间从新到旧。
     *
     * @param limit  最多返回多少个 Release，服务端会限制上限
     * @param source 数据来源 github / gitee；为空时由服务端根据仓库存在性判断
     */
    @GetMapping("/repositories/{owner}/{repo}/releases")
    Result<List<RepositoryRelease>> listReleases(@PathVariable("owner") String owner,
                                                 @PathVariable("repo") String repo,
                                                 @RequestParam(value = "limit", required = false) Integer limit,
                                                 @RequestParam(value = "source", required = false) String source);

    /**
     * 列出仓库指定目录下的文件与子目录。
     *
     * @param path   相对仓库根目录的路径，空表示根目录
     * @param branch 分支，空表示平台默认分支
     * @param source 数据来源 github / gitee
     */
    @GetMapping("/repositories/{owner}/{repo}/contents")
    Result<RepositoryFileListing> listRepositoryFiles(
            @PathVariable("owner") String owner,
            @PathVariable("repo") String repo,
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "branch", required = false) String branch,
            @RequestParam(value = "source", required = false) String source);

    /**
     * 读取仓库中的单个文本文件。二进制、目录与超大文件会返回对应标记，不返回正文。
     */
    @GetMapping("/repositories/{owner}/{repo}/file")
    Result<RepositoryFileContent> readRepositoryFile(
            @PathVariable("owner") String owner,
            @PathVariable("repo") String repo,
            @RequestParam("path") String path,
            @RequestParam(value = "branch", required = false) String branch,
            @RequestParam(value = "source", required = false) String source);
}
