/** 仓库摘要（对应后端 common-api 的 GiteeRepository；数据可能来自 GitHub 或 Gitee） */
export interface GiteeRepository {
  name: string
  fullName: string
  description?: string | null
  language?: string | null
  stars?: number | null
  forks?: number | null
  defaultBranch?: string | null
  htmlUrl?: string | null
  /** 开源协议名称，缺失为 null */
  license?: string | null
  /** 主题标签（GitHub 提供，Gitee 通常为空） */
  topics?: string[] | null
  /** 最近推送时间，ISO-8601 字符串 */
  updatedAt?: string | null
  /** 数据来源平台：gitee / github */
  source?: string | null
}

/** 仓库分支 */
export interface GiteeBranch {
  name: string
}

/** 仓库检索入参 */
export interface RepositorySearchParams {
  keyword: string
  page?: number
  perPage?: number
}

/** 仓库 README（后端已压缩空白并按上限截断；无 README 时 content 为 null） */
export interface RepositoryReadme {
  fullName: string
  content?: string | null
  truncated?: boolean
}