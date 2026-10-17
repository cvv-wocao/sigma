import { del, get, post, put } from './request'
import type { Project, ProjectQuery, ProjectSaveParams, ProjectUpdateParams } from '@/types/project'

/** 当前用户收藏的项目列表（按最后更新时间倒序） */
export function listProjects(query: ProjectQuery = {}): Promise<Project[]> {
  return get<Project[]>('/project', { params: query })
}

/** 项目详情 */
export function getProject(id: number): Promise<Project> {
  return get<Project>(`/project/${id}`)
}

/** 收藏项目；重复收藏会返回「该项目已在收藏列表中」 */
export function createProject(data: ProjectSaveParams): Promise<Project> {
  return post<Project>('/project', data)
}

/** 更新简介 / 备注 / 状态 */
export function updateProject(id: number, data: ProjectUpdateParams): Promise<Project> {
  return put<Project>(`/project/${id}`, data)
}

/** 取消收藏 */
export function deleteProject(id: number): Promise<void> {
  return del<void>(`/project/${id}`)
}