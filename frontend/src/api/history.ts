import { get } from './request'
import type { Conversation, HistoryMessage } from '@/types/history'

/** 当前登录用户的会话列表（按最后更新时间倒序） */
export function listConversations(): Promise<Conversation[]> {
  return get<Conversation[]>('/ai/conversations')
}

/** 某个会话的消息列表（按时间正序），只能查询自己的会话 */
export function listMessages(conversationId: string): Promise<HistoryMessage[]> {
  return get<HistoryMessage[]>(`/ai/conversations/${encodeURIComponent(conversationId)}/messages`)
}