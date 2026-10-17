package cn.itcast.agent.search.provider;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryReadme;

import java.util.List;

/**
 * 代码托管平台检索能力抽象。
 *
 * <p>引入它的原因：Gitee 官方关键词检索接口当前不可用，检索主力换成 GitHub 后，
 * 上层（SearchService / Controller）不应该关心数据来自哪个平台；同时仓库详情与分支
 * 接口必须「从哪个平台搜到的，就去哪个平台查」，否则搜到 GitHub 仓库后点详情会 404。</p>
 *
 * <p>实现类均为 Spring Bean，由 SearchService 按 search.provider 选择主来源，
 * 主来源无结果时依次尝试其余来源。</p>
 */
public interface RepositorySearchProvider {

    /** 来源标识，取值见 {@link ProviderNames} */
    String name();

    /** 按关键词检索仓库，page 从 1 开始；返回空列表表示该来源没有命中 */
    List<GiteeRepository> search(String keyword, int page, int perPage);

    /** 查询仓库详情，仓库不存在时返回 null，由调用方决定是否继续尝试其他来源 */
    GiteeRepository getRepository(String owner, String repo);

    /**
     * 带过滤条件的检索。默认忽略条件、退化为纯关键词检索；
     * 支持平台限定符的来源（GitHub）覆写本方法，把语言 / Star / 推送时间下推给平台。
     */
    default List<GiteeRepository> search(KeywordQuery query) {
        return search(query.keyword(), query.page(), query.perPage());
    }

    /**
     * 读取仓库 README 正文，供「项目深读」使用。
     * 默认返回 null 表示该来源不支持（例如 Gitee），调用方据此退化为只用仓库元数据。
     */
    default RepositoryReadme getReadme(String owner, String repo) {
        return null;
    }

    /** 查询仓库分支列表，仓库不存在时可能抛出 4xx 异常，由调用方兜底 */
    List<GiteeBranch> getBranches(String owner, String repo);
}