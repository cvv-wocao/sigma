<template>
  <div class="page wallet-page">
    <div class="kpi-grid">
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="database" :size="19" /></span>
        <div>
          <div class="kpi-label">可用余额</div>
          <div class="kpi-value">¥ {{ formatYuan(wallet?.balance) }}</div>
        </div>
      </div>
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="plus" :size="19" /></span>
        <div>
          <div class="kpi-label">累计充值</div>
          <div class="kpi-value">¥ {{ formatYuan(wallet?.totalRecharged) }}</div>
        </div>
      </div>
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="activity" :size="19" /></span>
        <div>
          <div class="kpi-label">钱包累计扣费</div>
          <div class="kpi-value">¥ {{ formatYuan(wallet?.totalConsumed) }}</div>
        </div>
      </div>
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="cpu" :size="19" /></span>
        <div>
          <div class="kpi-label">平台模型实际成本 · 近 30 天</div>
          <div class="kpi-value">¥ {{ formatYuan(summary?.platformUsageCost) }}</div>
        </div>
      </div>
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="cpu" :size="19" /></span>
        <div>
          <div class="kpi-label">平台单价 / 百万 Token</div>
          <div class="kpi-value small-value">
            输入 ¥{{ formatYuan(wallet?.inputPricePerMillionYuan) }} · 输出 ¥{{ formatYuan(wallet?.outputPricePerMillionYuan) }}
          </div>
        </div>
      </div>
    </div>

    <div v-if="errorMessage" class="alert alert-error">
      <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
      <span>{{ errorMessage }}</span>
    </div>

    <div v-if="isAdmin" class="alert alert-info">
      <span class="icon-slot"><AppIcon name="shield" :size="16" /></span>
      <span>
        管理员账号使用平台模型免扣钱包，因此“钱包累计扣费”为 0；
        “平台模型实际成本”和下方明细中的输入/输出成本才是模型真实消耗金额。
      </span>
    </div>
    <div v-else-if="wallet && Number(wallet.balance) <= 0" class="alert alert-info">
      <span class="icon-slot"><AppIcon name="info" :size="16" /></span>
      <span>
        当前平台额度为 0。继续使用平台模型前请先充值，或在
        <router-link to="/model-settings">模型设置</router-link> 中配置自己的 API Key。
      </span>
    </div>
    <div v-else class="alert alert-info">
      <span class="icon-slot"><AppIcon name="shield" :size="16" /></span>
      <span>
        使用平台模型时按实际输入/输出 Token 扣费；配置并启用自己的 API Key 后不扣平台额度。
      </span>
    </div>

    <div v-if="successMessage" class="alert alert-success">
      <span class="icon-slot"><AppIcon name="check" :size="16" /></span>
      <span>{{ successMessage }}</span>
    </div>

    <section class="card card-pad">
      <div class="card-head">
        <h3><AppIcon name="activity" :size="18" />近 30 天 Token 使用与成本概览</h3>
        <span class="tag">每次模型调用独立记账</span>
      </div>
      <div class="usage-summary-grid">
        <div class="usage-metric">
          <span class="usage-metric-label">请求次数</span>
          <strong>{{ formatTokens(summary?.requestCount ?? 0) }}</strong>
          <small>平台 {{ formatTokens(summary?.platformRequestCount ?? 0) }} · 自带 Key {{ formatTokens(summary?.selfKeyRequestCount ?? 0) }}</small>
        </div>
        <div class="usage-metric">
          <span class="usage-metric-label">输入 Token</span>
          <strong>{{ formatTokens(summary?.promptTokens ?? 0) }}</strong>
          <small>输入费用 ¥{{ formatYuan(summary?.inputCost) }}</small>
        </div>
        <div class="usage-metric">
          <span class="usage-metric-label">输出 Token</span>
          <strong>{{ formatTokens(summary?.completionTokens ?? 0) }}</strong>
          <small>输出费用 ¥{{ formatYuan(summary?.outputCost) }}</small>
        </div>
        <div class="usage-metric">
          <span class="usage-metric-label">平台模型成本</span>
          <strong class="usage-metric-cost">¥ {{ formatYuan(summary?.platformUsageCost) }}</strong>
          <small>自带 Key 参考成本 ¥{{ formatYuan(summary?.selfKeyUsageCost) }}</small>
        </div>
        <div class="usage-metric">
          <span class="usage-metric-label">钱包实际扣费</span>
          <strong>¥ {{ formatYuan(summary?.totalCost) }}</strong>
          <small>发生扣费 {{ formatTokens(summary?.chargedRequestCount ?? 0) }} 次</small>
        </div>
        <div class="usage-metric">
          <span class="usage-metric-label">平均耗时</span>
          <strong>{{ formatDuration(summary?.avgDurationMs ?? 0) }}</strong>
          <small>最长 {{ formatDuration(summary?.maxDurationMs ?? 0) }}</small>
        </div>
      </div>
    </section>

    <section class="card card-pad">
      <div class="card-head">
        <h3><AppIcon name="plus" :size="18" />支付宝扫码充值</h3>
        <span class="tag tag-brand">支付宝</span>
      </div>

      <div class="alipay-pay-grid">
        <div class="alipay-pay-form">
          <div class="field">
            <label for="alipay-amount">充值金额（元）</label>
            <div class="amount-presets">
              <button
                v-for="option in amountOptions"
                :key="option"
                type="button"
                :class="['btn', 'btn-sm', Number(amountYuan) === option ? 'btn-primary' : 'btn-soft']"
                @click="amountYuan = option"
              >
                ¥{{ option }}
              </button>
            </div>
            <input
              id="alipay-amount"
              v-model.number="amountYuan"
              class="input"
              type="number"
              min="0.01"
              max="10000"
              step="0.01"
              placeholder="输入充值金额"
            />
            <span class="hint">单次 0.01 - 10000 元。支付成功后按实际 Token 用量扣费，余额实时到账。</span>
          </div>

          <button class="btn btn-primary alipay-pay-submit" type="button" :disabled="creating" @click="handleCreateOrder">
            <AppIcon name="code" :size="16" />
            {{ creating ? '正在生成二维码…' : '生成支付宝二维码' }}
          </button>

          <div v-if="payError" class="alert alert-error compact-alert">
            <span class="icon-slot"><AppIcon name="alert" :size="15" /></span>
            <span>{{ payError }}</span>
          </div>
        </div>

        <div class="alipay-qr-panel">
          <div v-if="!currentOrder" class="qr-placeholder">
            <span class="qr-placeholder-icon"><AppIcon name="code" :size="30" /></span>
            <strong>扫码即可充值</strong>
            <span>生成二维码后，打开支付宝扫一扫完成支付。</span>
          </div>

          <template v-else>
            <div :class="['qr-frame', { 'is-paid': currentOrder.status === 'PAID' }]">
              <QrcodeVue
                v-if="currentOrder.status === 'PENDING' && currentOrder.qrCode"
                :value="currentOrder.qrCode"
                :size="220"
                level="M"
                render-as="canvas"
              />
              <div v-else-if="currentOrder.status === 'PAID'" class="qr-result is-success">
                <span class="qr-result-icon"><AppIcon name="check" :size="30" /></span>
                <strong>支付成功</strong>
                <span>额度已到账</span>
              </div>
              <div v-else class="qr-result">
                <span class="qr-result-icon"><AppIcon name="clock" :size="28" /></span>
                <strong>{{ orderStatusLabel(currentOrder.status) }}</strong>
                <span>请重新生成支付二维码</span>
              </div>
            </div>

            <div class="qr-order-meta">
              <span>订单金额 <strong>¥{{ formatYuan(currentOrder.amountYuan) }}</strong></span>
              <span class="qr-order-no">订单号 {{ currentOrder.outTradeNo }}</span>
            </div>

            <div v-if="currentOrder.status === 'PENDING'" class="qr-waiting">
              <span class="waiting-dot"></span>
              <span>等待支付宝扫码支付，页面每 2 秒自动确认</span>
            </div>
            <div v-if="currentOrder.status === 'PENDING' && currentOrder.expireTime" class="qr-expire">
              二维码有效期至 {{ currentOrder.expireTime }}
            </div>

            <button
              v-if="currentOrder.status === 'PENDING'"
              class="btn btn-soft btn-sm"
              type="button"
              @click="closeOrder"
            >
              关闭二维码
            </button>
          </template>
        </div>
      </div>
    </section>

    <section class="card card-pad">
      <div class="card-head">
        <h3><AppIcon name="history" :size="18" />Token 消费明细</h3>
        <button class="btn btn-soft btn-sm" type="button" :disabled="loading" @click="load">
          <AppIcon name="refresh" :size="15" />
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>

      <div v-if="!loading && usage.length === 0" class="empty">
        <span class="empty-icon"><AppIcon name="database" :size="24" /></span>
        <span class="empty-title">还没有 Token 使用记录</span>
        <span class="empty-desc">发起一次对话或项目推荐后，这里会显示输入、输出 Token 和扣费金额。</span>
      </div>

      <div v-else class="billing-table-wrap">
        <table class="billing-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>场景</th>
              <th>模型</th>
              <th class="num">输入</th>
              <th class="num">输出</th>
              <th class="num">总 Token</th>
              <th class="num">输入成本</th>
              <th class="num">输出成本</th>
              <th class="num">实际扣费</th>
              <th class="num">耗时</th>
              <th>计费方式</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in usage" :key="record.id">
              <td class="muted">{{ record.createTime }}</td>
              <td>{{ sceneLabel(record.scene) }}</td>
              <td class="model-cell">{{ record.model || '-' }}</td>
              <td class="num">{{ formatTokens(record.promptTokens) }}</td>
              <td class="num">{{ formatTokens(record.completionTokens) }}</td>
              <td class="num">{{ formatTokens(record.totalTokens) }}</td>
              <td class="num">¥ {{ formatYuan(record.inputCost) }}</td>
              <td class="num">¥ {{ formatYuan(record.outputCost) }}</td>
              <td class="num cost">¥ {{ formatYuan(record.cost) }}</td>
              <td class="num">{{ formatDuration(record.durationMs) }}</td>
              <td>
                <span :class="['tag', record.chargeMode === 'PLATFORM_CHARGED' ? 'tag-brand' : '']">
                  {{ chargeModeLabel(record.chargeMode) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import QrcodeVue from 'qrcode.vue'
import { createAlipayOrder, getAlipayOrder, getUsage, getUsageSummary, getWallet } from '@/api/wallet'
import { useUserStore } from '@/stores/user'
import type { AlipayOrder, UsageRecord, UsageSummary, WalletInfo } from '@/types/billing'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const wallet = ref<WalletInfo | null>(null)
const usage = ref<UsageRecord[]>([])
const summary = ref<UsageSummary | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const payError = ref('')
const amountOptions = [10, 20, 50, 100]
const amountYuan = ref(10)
const currentOrder = ref<AlipayOrder | null>(null)
const creating = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null
let polling = false

const SCENE_LABELS: Record<string, string> = {
  CHAT: '普通对话',
  AGENT: 'Agent 对话',
  RECOMMEND_ANALYSIS: '推荐·需求解析',
  RECOMMEND_RANK: '推荐·项目排序',
  MEMORY_EXTRACT: '长期记忆抽取',
  MODEL_TEST: '模型连接测试'
}

function sceneLabel(scene: string): string {
  return SCENE_LABELS[scene] || scene
}

function formatYuan(value: number | null | undefined): string {
  const number = Number(value ?? 0)
  if (!Number.isFinite(number)) {
    return '0.00'
  }
  return number.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 6
  })
}

function formatTokens(value: number): string {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatDuration(value: number | null | undefined): string {
  const millis = Number(value || 0)
  if (!Number.isFinite(millis) || millis <= 0) {
    return '0 ms'
  }
  if (millis < 1000) {
    return `${Math.round(millis)} ms`
  }
  return `${(millis / 1000).toFixed(millis < 10000 ? 1 : 0)} s`
}

function chargeModeLabel(mode: string | null | undefined): string {
  const labels: Record<string, string> = {
    PLATFORM_CHARGED: '平台扣费',
    ADMIN_EXEMPT: '管理员免扣',
    SELF_KEY: '自带 Key',
    NO_CHARGE: '本次无扣费'
  }
  return labels[mode || ''] || mode || '-'
}

function orderStatusLabel(status: AlipayOrder['status']): string {
  const labels: Record<AlipayOrder['status'], string> = {
    PENDING: '等待支付',
    PAID: '支付成功',
    CLOSED: '订单已关闭',
    FAILED: '支付失败'
  }
  return labels[status]
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function closeOrder() {
  stopPolling()
  currentOrder.value = null
  payError.value = ''
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    const order = currentOrder.value
    if (!order || order.status !== 'PENDING' || polling) {
      return
    }
    polling = true
    try {
      const latest = await getAlipayOrder(order.outTradeNo)
      currentOrder.value = latest
      if (latest.status === 'PAID') {
        stopPolling()
        successMessage.value = `充值成功，¥${formatYuan(latest.amountYuan)} 已到账。`
        payError.value = ''
        await load()
      } else if (latest.status === 'CLOSED' || latest.status === 'FAILED') {
        stopPolling()
        payError.value = latest.status === 'CLOSED'
          ? '二维码已过期，请重新生成。'
          : '支付宝支付未完成，请重新发起。'
      } else {
        payError.value = ''
      }
    } catch (error) {
      payError.value = error instanceof Error ? error.message : '查询支付状态失败'
    } finally {
      polling = false
    }
  }, 2000)
}

async function handleCreateOrder() {
  payError.value = ''
  successMessage.value = ''
  const amount = Number(amountYuan.value)
  if (!Number.isFinite(amount) || amount < 0.01 || amount > 10000) {
    payError.value = '充值金额需在 0.01 到 10000 元之间'
    return
  }

  creating.value = true
  stopPolling()
  try {
    const order = await createAlipayOrder(amount)
    currentOrder.value = order
    startPolling()
  } catch (error) {
    currentOrder.value = null
    payError.value = error instanceof Error ? error.message : '生成支付宝支付二维码失败'
  } finally {
    creating.value = false
  }
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [walletData, usageData, summaryData] = await Promise.all([
      getWallet(),
      getUsage(200),
      getUsageSummary(30)
    ])
    wallet.value = walletData
    usage.value = usageData
    summary.value = summaryData
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载额度信息失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
onUnmounted(stopPolling)
</script>

<style scoped>
.small-value {
  font-size: 13px;
  line-height: 1.5;
}

.usage-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.usage-metric {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 14px;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: var(--r);
}

.usage-metric-label {
  font-size: 12px;
  color: var(--text-3);
}

.usage-metric strong {
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}

.usage-metric-cost {
  color: var(--accent-yellow-ink);
}

.usage-metric small {
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--text-3);
}

.billing-table-wrap {
  overflow-x: auto;
}

.billing-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  white-space: nowrap;
}

.billing-table th,
.billing-table td {
  padding: 11px 12px;
  text-align: left;
  border-bottom: 1px solid var(--line);
}

.billing-table th {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-3);
  background: var(--surface-2);
}

.billing-table tbody tr:hover {
  background: var(--surface-2);
}

.billing-table .num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.billing-table .cost {
  font-weight: 600;
  color: var(--accent-yellow-ink);
}

.billing-table .muted {
  color: var(--text-3);
}

.billing-table .model-cell {
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.alipay-pay-grid {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 300px;
  gap: 26px;
  align-items: stretch;
}

.alipay-pay-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 520px;
}

.amount-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.alipay-pay-submit {
  width: fit-content;
  min-width: 210px;
}

.compact-alert {
  margin: 0;
}

.alipay-qr-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  min-height: 294px;
  padding: 18px;
  text-align: center;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: var(--r);
}

.qr-placeholder {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 240px;
  color: var(--text-3);
}

.qr-placeholder strong {
  color: var(--text);
  font-size: 15px;
}

.qr-placeholder span:last-child {
  max-width: 220px;
  font-size: 12.5px;
}

.qr-placeholder-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 62px;
  height: 62px;
  margin-bottom: 3px;
  color: var(--brand-600);
  background: var(--brand-soft);
  border: 1px solid var(--brand-line);
  border-radius: 18px;
}

.qr-frame {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 244px;
  height: 244px;
  padding: 12px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--r);
  box-shadow: var(--sh-sm);
}

.qr-frame.is-paid {
  border-color: #b7ebcf;
  background: var(--success-soft);
}

.qr-result {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  color: var(--text-2);
}

.qr-result.is-success {
  color: var(--success);
}

.qr-result strong {
  font-size: 16px;
}

.qr-result span:last-child {
  font-size: 12.5px;
  color: var(--text-3);
}

.qr-result-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  margin-bottom: 4px;
  color: var(--brand-600);
  background: #fff;
  border-radius: 50%;
}

.qr-result.is-success .qr-result-icon {
  color: var(--success);
}

.qr-order-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12.5px;
  color: var(--text-2);
}

.qr-order-no {
  color: var(--text-3);
  font-family: var(--mono);
  font-size: 11px;
}

.qr-waiting {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--brand-700);
}

.waiting-dot {
  width: 8px;
  height: 8px;
  background: var(--brand);
  border-radius: 50%;
  animation: qr-pulse 1.4s ease-in-out infinite;
}

.qr-expire {
  font-size: 11.5px;
  color: var(--text-3);
}

@keyframes qr-pulse {
  0%, 100% { opacity: 0.35; transform: scale(0.85); }
  50% { opacity: 1; transform: scale(1); }
}

@media (max-width: 860px) {
  .alipay-pay-grid {
    grid-template-columns: 1fr;
  }

  .alipay-qr-panel {
    align-items: flex-start;
    text-align: left;
  }

  .qr-placeholder,
  .qr-result {
    align-items: flex-start;
  }
}
</style>
