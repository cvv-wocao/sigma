import { del, get, post, put } from './request'
import type { UserModelConfig, UserModelConfigPayload, UserModelTestResult } from '@/types/modelConfig'

/** 获取当前用户的模型配置 */
export function getModelConfig(): Promise<UserModelConfig> {
  return get<UserModelConfig>('/ai/model-config')
}

/** 保存当前用户的模型配置；apiKey 留空表示沿用旧 Key */
export function saveModelConfig(data: UserModelConfigPayload): Promise<UserModelConfig> {
  return put<UserModelConfig>('/ai/model-config', data)
}

/** 测试 Base URL、API Key 与模型名是否可用 */
export function testModelConfig(data: UserModelConfigPayload): Promise<UserModelTestResult> {
  return post<UserModelTestResult>('/ai/model-config/test', data, { timeout: 60000 })
}

/** 删除自定义配置，之后回退平台默认模型 */
export function deleteModelConfig(): Promise<number> {
  return del<number>('/ai/model-config')
}