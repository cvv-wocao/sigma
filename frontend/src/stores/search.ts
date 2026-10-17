import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { GiteeRepository } from '@/types/search'

/**
 * 代码检索页的会话状态（关键词、每页条数、结果列表）。
 *
 * <p>与推荐页同样的原因：状态放在组件里的话，切到别的页面再回来结果就空了，
 * 用户还得重新搜一次。这里放进 Store 并落一份 sessionStorage，
 * 路由切换和页面刷新都能恢复（sessionStorage 随标签页关闭失效）。</p>
 */

const STORAGE_KEY = 'agent_platform_search'
const DEFAULT_PER_PAGE = 10

interface SearchSnapshot {
  keyword: string
  perPage: number
  repositories: GiteeRepository[]
  searched: boolean
}

function emptySnapshot(): SearchSnapshot {
  return { keyword: '', perPage: DEFAULT_PER_PAGE, repositories: [], searched: false }
}

function loadSnapshot(): SearchSnapshot {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return emptySnapshot()
    }
    const parsed = JSON.parse(raw) as Partial<SearchSnapshot>
    return {
      keyword: typeof parsed.keyword === 'string' ? parsed.keyword : '',
      perPage:
        typeof parsed.perPage === 'number' && parsed.perPage > 0 ? parsed.perPage : DEFAULT_PER_PAGE,
      repositories: Array.isArray(parsed.repositories) ? parsed.repositories : [],
      searched: parsed.searched === true
    }
  } catch {
    return emptySnapshot()
  }
}

export const useSearchStore = defineStore('search', () => {
  const initial = loadSnapshot()

  const keyword = ref(initial.keyword)
  const perPage = ref(initial.perPage)
  const repositories = ref<GiteeRepository[]>(initial.repositories)
  const searched = ref(initial.searched)

  function persist() {
    try {
      sessionStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          keyword: keyword.value,
          perPage: perPage.value,
          repositories: repositories.value,
          searched: searched.value
        })
      )
    } catch {
      // 忽略：内存状态仍然有效
    }
  }

  watch([keyword, perPage, repositories, searched], persist, { deep: true })

  /** 写入一次检索结果 */
  function applyResult(list: GiteeRepository[]) {
    repositories.value = list
    searched.value = true
  }

  function clear() {
    const empty = emptySnapshot()
    keyword.value = empty.keyword
    perPage.value = empty.perPage
    repositories.value = empty.repositories
    searched.value = empty.searched
    try {
      sessionStorage.removeItem(STORAGE_KEY)
    } catch {
      // 忽略
    }
  }

  return { keyword, perPage, repositories, searched, applyResult, clear }
})