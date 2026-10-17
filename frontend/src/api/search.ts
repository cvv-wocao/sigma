import { get } from './request'
import type { GiteeBranch, GiteeRepository, RepositoryReadme, RepositorySearchParams } from '@/types/search'

/** 关键词检索开源仓库（GitHub 主来源，Gitee 兜底） */
export function searchRepositories(params: RepositorySearchParams): Promise<GiteeRepository[]> {
  return get<GiteeRepository[]>('/search/repositories', { params })
}

/** 仓库详情 */
export function getRepository(owner: string, repo: string): Promise<GiteeRepository> {
  return get<GiteeRepository>(`/search/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`)
}

/** 仓库分支列表 */
export function listBranches(owner: string, repo: string): Promise<GiteeBranch[]> {
  return get<GiteeBranch[]>(`/search/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/branches`)
}

/** 仓库 README 摘要（用于详情页展示项目说明） */
export function getReadme(owner: string, repo: string): Promise<RepositoryReadme> {
  return get<RepositoryReadme>(`/search/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/readme`)
}