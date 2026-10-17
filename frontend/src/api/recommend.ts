import { post } from './request'
import type { RecommendParams, RecommendResult } from '@/types/recommend'

/**
 * AI 项目推荐：后端先解析需求，再多次检索代码托管平台召回候选，
 * 最后让模型对候选中打分排序，返回带匹配度与理由的结构化结果。
 * 单次请求包含多轮模型调用与多路检索，耗时明显长于普通接口，因此单独放宽超时。
 */
export function recommendProjects(data: RecommendParams): Promise<RecommendResult> {
  return post<RecommendResult>('/ai/recommend', data, { timeout: 180000 })
}