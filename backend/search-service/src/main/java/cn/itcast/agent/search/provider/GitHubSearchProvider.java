package cn.itcast.agent.search.provider;

import cn.itcast.agent.common.api.dto.GiteeBranch;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryFileContent;
import cn.itcast.agent.common.api.dto.RepositoryFileEntry;
import cn.itcast.agent.common.api.dto.RepositoryReadme;
import cn.itcast.agent.common.api.dto.RepositoryRelease;
import cn.itcast.agent.search.client.RepositoryArchiveStream;
import cn.itcast.agent.search.client.GitHubClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GitHub 检索来源。
 * 作为默认主来源：关键词检索可用，且返回 star / license / topics / updated_at，
 * 是后续「AI 推荐」排序打分的主要依据。
 */
@Component
public class GitHubSearchProvider implements RepositorySearchProvider {

    private final GitHubClient gitHubClient;

    public GitHubSearchProvider(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    @Override
    public String name() {
        return ProviderNames.GITHUB;
    }

    @Override
    public List<GiteeRepository> search(String keyword, int page, int perPage) {
        return search(KeywordQuery.of(keyword, page, perPage));
    }

    /** GitHub 支持 language: / stars: / pushed: 限定符，条件直接拼进查询串下推给平台 */
    @Override
    public List<GiteeRepository> search(KeywordQuery query) {
        return gitHubClient.searchRepositories(query.keyword(), query.page(), query.perPage(),
                        query.language(), query.minStars(), query.pushedAfter()).stream()
                .map(repository -> repository.withSource(name()))
                .toList();
    }

    @Override
    public RepositoryReadme getReadme(String owner, String repo) {
        return gitHubClient.getReadme(owner, repo);
    }

    @Override
    public List<RepositoryRelease> getReleases(String owner, String repo, int limit) {
        return gitHubClient.getReleases(owner, repo, limit);
    }

    @Override
    public List<RepositoryFileEntry> listFiles(String owner, String repo, String path, String branch) {
        return gitHubClient.listFiles(owner, repo, path, branch);
    }

    @Override
    public RepositoryFileContent readFile(String owner, String repo, String path, String branch) {
        return gitHubClient.readFile(owner, repo, path, branch);
    }

    @Override
    public GiteeRepository getRepository(String owner, String repo) {
        GiteeRepository repository = gitHubClient.getRepository(owner, repo);
        return repository == null ? null : repository.withSource(name());
    }

    @Override
    public List<GiteeBranch> getBranches(String owner, String repo) {
        return gitHubClient.getBranches(owner, repo);
    }

    @Override
    public RepositoryArchiveStream openArchive(String owner, String repo, String branch) {
        return gitHubClient.openArchive(owner, repo, branch);
    }
}
