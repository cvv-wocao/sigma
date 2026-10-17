import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { ChatMessage } from '@/types/chat'

/**
 * 对话面板的状态（消息列表 + 会话标识）。
 *
 * <p>为什么放到 Store + sessionStorage：对话面板嵌在「AI 推荐」页右侧，
 * 用户切到别的页面再回来时组件会卸载重建，状态放在组件的 ref 里就会丢。
 * 放进 Store 后切路由不丢；再落一份 sessionStorage，刷新页面也能恢复。</p>
 *
 * <p>用 sessionStorage 而不是 localStorage：它是「当前标签页的会话」，
 * 关掉标签页即失效，不会把对话长期留在浏览器里。</p>
 */

const STORAGE_KEY = 'agent_platform_chat'

interface ChatSnapshot {
  messages: ChatMessage[]
  conversationId: string
}

function emptySnapshot(): ChatSnapshot {
  return { messages: [], conversationId: '' }
}

/** 读取缓存：缓存损坏或结构不对时退回空状态，绝不让解析失败影响页面 */
function loadSnapshot(): ChatSnapshot {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return emptySnapshot()
    }
    const parsed = JSON.parse(raw) as Partial<ChatSnapshot>
    return {
      messages: Array.isArray(parsed.messages) ? (parsed.messages as ChatMessage[]) : [],
      conversationId: typeof parsed.conversationId === 'string' ? parsed.conversationId : ''
    }
  } catch {
    return emptySnapshot()
  }
}

export const useChatStore = defineStore('chat', () => {
  const initial = loadSnapshot()

  const messages = ref<ChatMessage[]>(initial.messages)
  const conversationId = ref(initial.conversationId)

  function persist() {
    try {
      sessionStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ messages: messages.value, conversationId: conversationId.value })
      )
    } catch {
      // 隐私模式 / 存储配额满时忽略：内存里的状态依然有效
    }
  }

  watch([messages, conversationId], persist, { deep: true })

  /** 开一个新会话：清空消息与会话标识（后端会按新会话重建记忆） */
  function reset() {
    const empty = emptySnapshot()
    messages.value = empty.messages
    conversationId.value = empty.conversationId
    try {
      sessionStorage.removeItem(STORAGE_KEY)
    } catch {
      // 忽略
    }
  }

  return { messages, conversationId, reset }
})