/** 用户自带模型配置（服务端不会返回 API Key 明文） */
export interface UserModelConfig {
  configured: boolean
  enabled: boolean
  baseUrl: string
  model: string
  apiKeyMasked: string
  lastTestTime: string | null
  updateTime: string | null
}

export interface UserModelConfigPayload {
  baseUrl: string
  apiKey?: string
  model: string
  enabled: boolean
}

export interface UserModelTestResult {
  success: boolean
  message: string
  model: string
  latencyMs: number
}