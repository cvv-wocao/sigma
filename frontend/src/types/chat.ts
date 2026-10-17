/** 聊天消息（前端展示用） */
export interface ChatMessage {
  /** 角色：用户 / 助手 */
  role: 'user' | 'assistant'
  /** 消息文本 */
  content: string
  /** 助手消息实际使用的模型名 */
  model?: string | null
  /** 是否为错误提示（渲染成红色） */
  error?: boolean
}

/** Agent 对话入参 */
export interface AgentParams {
  message: string
  /** 会话标识；不传则由服务端生成并在响应中返回 */
  conversationId?: string
  /**
   * 当前页面上的参考数据（例如 AI 推荐页刚算出的推荐结果，已序列化成纯文本）。
   * 服务端只把它拼进本轮提示词、不写入会话记忆，因此逐轮携带也不会让记忆膨胀。
   */
  context?: string
}

/** Agent 对话返回 */
export interface AgentResult {
  answer: string
  model: string | null
  /** 本次会话标识，需保存并在同一会话的后续请求中回传 */
  conversationId: string
}