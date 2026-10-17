package cn.itcast.agent.search.provider;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.search.client.GiteeClient;
import cn.itcast.agent.search.config.GiteeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Gitee 检索来源。
 *
 * <p>降级说明：Gitee 官方 /v5/search/repositories 目前对任何请求都返回空数组
 * （实测同一令牌下 search/users、search/issues 正常，仅该接口恒空，属服务端问题）。
 * 因此官方检索为空时会再尝试「授权用户自己的仓库」，并把这一步记为 WARN 日志；
 * 未配置 access_token 时直接跳过，避免无谓的 401。</p>
 *
 * <p>仓库详情与分支列表接口免鉴权即可用，所以即使不做关键词检索，Gitee 仍是
 * 收藏过的 Gitee 仓库查询详情时的来源。</p>
 */
@Component
public class GiteeSearchProvider implements RepositorySearchProvider {

    private static final Logger log = LoggerFactory.getLogger(GiteeSearchProvider.class);

    private final GiteeClient giteeClient;
    private final GiteeProperties properties;

    public GiteeSearchProvider(GiteeClient giteeClient, GiteeProperties properties) {
        this.giteeClient = giteeClient;
        this.properties = properties;
    }

    @Override
    public String name() {
        return ProviderNames.GITEE;
    }

    @Override
    public List<GiteeRepository> search(String keyword, int page, int perPage) {
        List<GiteeRepository> repositories = mark(giteeClient.searchRepositories(keyword, page, perPage));
        if (!repositories.isEmpty()) {
            return repositories;
        }
        if (!StringUtils.hasText(properties.accessToken())) {
            log.warn("Gitee 官方检索为空且未配置 access_token，跳过「自己的仓库」降级检索：keyword={}", keyword);
            return List.of();
        }
        List<GiteeRepository> ownRepositories = mark(giteeClient.searchOwnRepositories(keyword, page, perPage));
        if (!ownRepositories.isEmpty()) {
            log.warn("Gitee 官方仓库检索返回空结果，已降级为在授权用户自己的仓库中筛选：keyword={}, 命中={}",
                    keyword, ownRepositories.size());
        }
        return ownRepositories;
    }

    @Override
    public GiteeRepository getRepository(String owner, String repo) {
        GiteeRepository repository = giteeClient.getRepository(owner, repo);
        return repository == null ? null : repository.withSource(name());
    }

    @Override
    public List<GiteeBranch> getBranches(String owner, String repo) {
        return giteeClient.getBranches(owner, repo);
    }

    private List<GiteeRepository> mark(List<GiteeRepository> repositories) {
        return repositories.stream().map(repository -> repository.withSource(name())).toList();
    }
}