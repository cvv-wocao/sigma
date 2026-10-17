/** 会话列表项（对应后端 ConversationVO） */
export interface Conversation {
  /** 会话标识，拉取消息时回传 */
  conversationId: string
  /** 会话标题（首条用户消息摘要） */
  title: string
  /** 消息条数 */
  messageCount: number
  createTime: string | null
  updateTime: string | null
}

/** 会话消息（对应后端 MessageVO） */
export interface HistoryMessage {
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  /** 仅助手消息有值 */
  model?: string | null
  createTime: string | null
}