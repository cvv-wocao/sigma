import { get, post } from './request'
import type { LoginParams, LoginResult, RegisterParams, UserInfo } from '@/types/user'

/** 注册 */
export function register(data: RegisterParams): Promise<UserInfo> {
  return post<UserInfo>('/user/register', data)
}

/** 登录 */
export function login(data: LoginParams): Promise<LoginResult> {
  return post<LoginResult>('/user/login', data)
}

/** 当前登录用户 */
export function getCurrentUser(): Promise<UserInfo> {
  return get<UserInfo>('/user/me')
}