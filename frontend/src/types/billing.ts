/** 当前用户的平台额度信息，金额单位均为元 */
export interface WalletInfo {
  userId: number
  balance: number
  totalRecharged: number
  totalConsumed: number
  inputPricePerMillionYuan: number
  outputPricePerMillionYuan: number
  updateTime: string | null
}

/** 一次模型调用的 Token 与扣费流水 */
export interface UsageRecord {
  id: number
  scene: string
  model: string | null
  promptTokens: number
  completionTokens: number
  totalTokens: number
  inputCost: number
  outputCost: number
  cost: number
  durationMs: number
  chargeMode: string
  platformToken: boolean
  createTime: string
}

/** 指定时间窗口内的 Token 用量与扣费汇总 */
export interface UsageSummary {
  requestCount: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  inputCost: number
  outputCost: number
  usageCost: number
  platformUsageCost: number
  selfKeyUsageCost: number
  totalCost: number
  chargedRequestCount: number
  platformRequestCount: number
  selfKeyRequestCount: number
  avgDurationMs: number
  maxDurationMs: number
  firstTime: string | null
  lastTime: string | null
}

/** 管理员看到的用户额度总览 */
export interface WalletOverview {
  userId: number
  username: string
  nickname: string | null
  role: 'ADMIN' | 'USER'
  balance: number
  totalConsumed: number
  admin: boolean
}

export interface RechargePayload {
  userId: number
  amountYuan: number
  remark?: string
}

export type WechatOrderStatus = 'PENDING' | 'PAID' | 'CLOSED' | 'FAILED'

/** 微信 Native 扫码充值订单 */
export interface WechatOrder {
  outTradeNo: string
  amountYuan: number
  status: WechatOrderStatus
  codeUrl: string | null
  expireTime: string | null
  paidTime: string | null
}

export type AlipayOrderStatus = 'PENDING' | 'PAID' | 'CLOSED' | 'FAILED'

/** 支付宝当面付预下单订单 */
export interface AlipayOrder {
  outTradeNo: string
  amountYuan: number
  status: AlipayOrderStatus
  qrCode: string | null
  expireTime: string | null
  paidTime: string | null
}
