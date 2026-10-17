import type { GiteeRepository } from './search'

/** AI 推荐入参 */
export interface RecommendParams {
  /** 用户用一整段话描述想要的项目 */
  message: string
  /** 期望推荐条数，1-10，不传用服务端默认值 */
  topN?: number
  /** 会话标识，不传由后端生成 */
  conversationId?: string
  userId?: string
}

/** 需求维度逐项核对结果：模型按用户自己的需求维度给出结论 */
export interface MatchPointVO {
  /** 维度名，取自用户需求（技术栈 / 功能 / 规模 等） */
  dimension: string
  /** 命中 / 部分命中 / 未命中 */
  verdict: string
  /** 判断依据（来自简介还是 README 摘要） */
  detail: string
}

/** 需求解析结果：AI 从一整段话里拆出的维度与检索条件 */
export interface RequirementVO {
  summary: string
  /** 项目形态，例如「单体后台管理系统」；判断不出为空 */
  projectType?: string | null
  techStack: string[]
  features: string[]
  /** 项目规模或使用场景 */
  scale?: string | null
  constraints: string[]
  keywords: string[]
  languages: string[]
  minStars?: number | null
  updatedWithinDays?: number | null
  license?: string | null
  notes?: string | null
}

/** 单条推荐结果 */
export interface RecommendationVO {
  repository: GiteeRepository
  /** 匹配度 0-100；模型排序不可用时的兜底结果为 null */
  score?: number | null
  reason: string
  /** 逐项对照用户需求的核对结论 */
  matches: MatchPointVO[]
  highlights: string[]
  risks: string[]
}

/** AI 推荐结果 */
export interface RecommendResult {
  requirement: RequirementVO
  recommendations: RecommendationVO[]
  /** 本次参与排序的候选仓库数 */
  candidateCount: number
  model?: string | null
  conversationId: string
}