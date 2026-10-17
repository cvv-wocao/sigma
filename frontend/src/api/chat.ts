import { post } from './request'
import type { AgentParams, AgentResult } from '@/types/chat'

/**
 * Agent 对话：后端会做工具调用（开源仓库检索）与会话记忆。
 * 单次请求可能包含多轮模型调用，耗时明显长于普通接口，因此单独放宽超时。
 */
export function agentChat(data: AgentParams): Promise<AgentResult> {
  return post<AgentResult>('/ai/agent', data, { timeout: 120000 })
}