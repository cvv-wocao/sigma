import { get, post } from './request'
import type {
  AlipayOrder,
  RechargePayload,
  UsageRecord,
  UsageSummary,
  WalletInfo,
  WalletOverview,
  WechatOrder
} from '@/types/billing'

/** 当前用户的余额、累计充值、累计消耗与平台单价 */
export function getWallet(): Promise<WalletInfo> {
  return get<WalletInfo>('/ai/wallet')
}

/** 当前用户最近的 Token 使用流水 */
export function getUsage(limit = 50): Promise<UsageRecord[]> {
  return get<UsageRecord[]>('/ai/wallet/usage', { params: { limit } })
}

/** 指定天数内的 Token、费用与耗时汇总 */
export function getUsageSummary(days = 30): Promise<UsageSummary> {
  return get<UsageSummary>('/ai/wallet/usage/summary', { params: { days } })
}

/** 管理员查看所有用户的额度与消费 */
export function getWalletOverview(): Promise<WalletOverview[]> {
  return get<WalletOverview[]>('/ai/wallet/admin/overview')
}

/** 管理员给指定用户充值 */
export function rechargeWallet(data: RechargePayload): Promise<WalletInfo> {
  return post<WalletInfo>('/ai/wallet/recharge', data)
}

/** 创建微信 Native 扫码支付订单，返回二维码内容 */
export function createWechatOrder(amountYuan: number): Promise<WechatOrder> {
  return post<WechatOrder>('/ai/wallet/wechat/orders', { amountYuan })
}

/** 查询扫码订单状态，支付成功后后端会幂等入账 */
export function getWechatOrder(outTradeNo: string): Promise<WechatOrder> {
  return get<WechatOrder>(`/ai/wallet/wechat/orders/${encodeURIComponent(outTradeNo)}`)
}

/** 创建支付宝当面付订单，返回支付宝二维码内容 */
export function createAlipayOrder(amountYuan: number): Promise<AlipayOrder> {
  return post<AlipayOrder>('/ai/wallet/alipay/orders', { amountYuan })
}

/** 查询支付宝订单状态；支付成功后后端会幂等入账 */
export function getAlipayOrder(outTradeNo: string): Promise<AlipayOrder> {
  return get<AlipayOrder>(`/ai/wallet/alipay/orders/${encodeURIComponent(outTradeNo)}`)
}
