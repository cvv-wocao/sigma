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
  /** 项目官网，作者未填写为 null */
  homepage?: string | null
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

/** Release 附件（安装包、压缩包等） */
export interface RepositoryReleaseAsset {
  name: string
  contentType?: string | null
  size?: number | null
  downloadCount?: number | null
  downloadUrl: string
}

/** 仓库 Release */
export interface RepositoryRelease {
  tagName?: string | null
  name?: string | null
  prerelease: boolean
  publishedAt?: string | null
  htmlUrl?: string | null
  body?: string | null
  bodyTruncated: boolean
  assetCount: number
  assets: RepositoryReleaseAsset[]
}

/** 仓库目录项 */
export interface RepositoryFileEntry {
  name: string
  path: string
  type: string
  size?: number | null
  htmlUrl?: string | null
}

/** 仓库目录列表（条目过多时后端会截断） */
export interface RepositoryFileListing {
  fullName: string
  path: string
  branch?: string | null
  entries: RepositoryFileEntry[]
  truncated: boolean
  total: number
}

/** 仓库单文件内容；二进制、目录、超大文件时 content 为 null */
export interface RepositoryFileContent {
  fullName: string
  path: string
  branch?: string | null
  content?: string | null
  size?: number | null
  truncated: boolean
  binary: boolean
  directory: boolean
  tooLarge: boolean
}
