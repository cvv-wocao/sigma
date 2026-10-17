/** 用户信息 */
export interface UserInfo {
  id: number
  username: string
  nickname?: string | null
  email?: string | null
  phone?: string | null
  avatar?: string | null
  createTime?: string | null
}

/** 注册入参 */
export interface RegisterParams {
  username: string
  password: string
  confirmPassword: string
  nickname?: string
  email?: string
}

/** 登录入参 */
export interface LoginParams {
  username: string
  password: string
}

/** 登录返回 */
export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}