import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { RecommendResult } from '@/types/recommend'

/**
 * AI 推荐页的会话状态（需求草稿、条数、推荐结果）。
 *
 * <p>为什么放到 Store + sessionStorage：这些状态原先放在页面组件的 ref 里，
 * 路由切走时组件卸载，切回来就全没了——用户看到的是「刚推出来的项目不见了」。
 * 放进 Store 后切换路由不丢；再落一份 sessionStorage，连刷新页面也能恢复。
 * 用 sessionStorage 而不是 localStorage：它是「当前标签页的会话」，关掉标签页即失效，
 * 不会把上一次的推荐结果长期留在浏览器里。</p>
 */

const STORAGE_KEY = 'agent_platform_recommend'
const DEFAULT_TOP_N = 5

interface RecommendSnapshot {
  draft: string
  topN: number
  result: RecommendResult | null
}

function emptySnapshot(): RecommendSnapshot {
  return { draft: '', topN: DEFAULT_TOP_N, result: null }
}

/** 读取缓存：缓存损坏或结构不对时退回空状态，绝不让解析失败影响页面 */
function loadSnapshot(): RecommendSnapshot {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return emptySnapshot()
    }
    const parsed = JSON.parse(raw) as Partial<RecommendSnapshot>
    return {
      draft: typeof parsed.draft === 'string' ? parsed.draft : '',
      topN: typeof parsed.topN === 'number' && parsed.topN > 0 ? parsed.topN : DEFAULT_TOP_N,
      result: (parsed.result ?? null) as RecommendResult | null
    }
  } catch {
    return emptySnapshot()
  }
}

export const useRecommendStore = defineStore('recommend', () => {
  const initial = loadSnapshot()

  const draft = ref(initial.draft)
  const topN = ref(initial.topN)
  const result = ref<RecommendResult | null>(initial.result)

  function persist() {
    try {
      sessionStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ draft: draft.value, topN: topN.value, result: result.value })
      )
    } catch {
      // 隐私模式 / 存储配额满时忽略：内存里的状态依然有效
    }
  }

  watch([draft, topN, result], persist, { deep: true })

  /** 清空当前推荐（「重置」按钮或退出登录时用） */
  function reset() {
    const empty = emptySnapshot()
    draft.value = empty.draft
    topN.value = empty.topN
    result.value = empty.result
    try {
      sessionStorage.removeItem(STORAGE_KEY)
    } catch {
      // 忽略
    }
  }

  return { draft, topN, result, reset }
})