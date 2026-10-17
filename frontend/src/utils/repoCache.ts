import type { GiteeRepository } from '@/types/search'

/**
 * 检索结果快照缓存（仅当前页面会话，刷新即失效）。
 *
 * <p>为什么需要它：详情页要展示分支与 README，必须回查一次外部平台；而未配置
 * GITHUB_TOKEN 时 GitHub 核心接口只有 60 次/小时，很容易被限流。缓存检索列表里
 * 已经拿到的仓库信息后，用户点进详情页可以「先有内容、再补齐」——即使回查失败，
 * 也不会看到一个空白页或错误提示，这正是「搜出来的项目点开打不开」的直接原因。</p>
 */

const CACHE_LIMIT = 200

const cache = new Map<string, GiteeRepository>()

function keyOf(fullName: string): string {
  return fullName.trim().toLowerCase()
}

export function rememberRepository(repository: GiteeRepository): void {
  if (!repository?.fullName) {
    return
  }
  cache.set(keyOf(repository.fullName), repository)
  if (cache.size > CACHE_LIMIT) {
    const oldest = cache.keys().next().value
    if (oldest) {
      cache.delete(oldest)
    }
  }
}

export function recallRepository(fullName: string): GiteeRepository | null {
  return cache.get(keyOf(fullName)) ?? null
}