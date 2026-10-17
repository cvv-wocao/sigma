import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'

/** 后端统一响应结构 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

const TOKEN_KEY = 'agent_platform_token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 15000
})

// 请求拦截：自动携带令牌
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一处理业务错误与登录过期
request.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const result = response.data
    if (result && typeof result.code === 'number' && result.code !== 200) {
      return Promise.reject(new Error(result.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const status = error?.response?.status
    const message = error?.response?.data?.message
    if (status === 401) {
      clearToken()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(new Error(message || '登录已过期，请重新登录'))
    }
    return Promise.reject(new Error(message || error?.message || '网络异常，请稍后重试'))
  }
)

export async function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await request.post<ApiResult<T>>(url, data, config)
  return response.data.data
}

export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await request.get<ApiResult<T>>(url, config)
  return response.data.data
}

export async function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await request.put<ApiResult<T>>(url, data, config)
  return response.data.data
}

export async function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await request.delete<ApiResult<T>>(url, config)
  return response.data.data
}

export default request