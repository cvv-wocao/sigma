/** 收藏的项目（对应后端 ProjectVO） */
export interface Project {
  id: number
  /** 仓库全名 owner/repo */
  fullName: string
  repoOwner: string
  repoName: string
  description?: string | null
  language?: string | null
  stars?: number | null
  forks?: number | null
  defaultBranch?: string | null
  htmlUrl?: string | null
  /** 用户备注 */
  note?: string | null
  /** 1 关注中，0 已归档 */
  status: number
  createTime?: string | null
  updateTime?: string | null
}

/** 收藏项目入参 */
export interface ProjectSaveParams {
  repoOwner: string
  repoName: string
  description?: string | null
  language?: string | null
  stars?: number | null
  forks?: number | null
  defaultBranch?: string | null
  htmlUrl?: string | null
  note?: string | null
}

/** 更新项目入参：只提交需要修改的字段 */
export interface ProjectUpdateParams {
  description?: string | null
  note?: string | null
  status?: number
}

/** 项目列表查询条件 */
export interface ProjectQuery {
  keyword?: string
  status?: number
}