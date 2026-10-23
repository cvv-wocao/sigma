<template>
  <div class="page recharge-page">
    <div class="kpi-grid">
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="user" :size="19" /></span>
        <div>
          <div class="kpi-label">用户数</div>
          <div class="kpi-value">{{ users.length }}</div>
        </div>
      </div>
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="database" :size="19" /></span>
        <div>
          <div class="kpi-label">平台余额合计</div>
          <div class="kpi-value">¥ {{ formatYuan(totalBalance) }}</div>
        </div>
      </div>
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="activity" :size="19" /></span>
        <div>
          <div class="kpi-label">Token 消费合计</div>
          <div class="kpi-value">¥ {{ formatYuan(totalConsumed) }}</div>
        </div>
      </div>
    </div>

    <div v-if="errorMessage" class="alert alert-error">
      <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
      <span>{{ errorMessage }}</span>
    </div>
    <div v-if="successMessage" class="alert alert-success">
      <span class="icon-slot"><AppIcon name="check" :size="16" /></span>
      <span>{{ successMessage }}</span>
    </div>

    <section class="card card-pad">
      <div class="card-head">
        <h3><AppIcon name="plus" :size="18" />给用户充值</h3>
        <span class="tag tag-brand">管理员</span>
      </div>

      <form class="recharge-form" @submit.prevent="handleRecharge">
        <div class="field">
          <label for="recharge-user">充值用户</label>
          <select id="recharge-user" v-model.number="form.userId" class="input">
            <option :value="0" disabled>请选择用户</option>
            <option v-for="user in users" :key="user.userId" :value="user.userId">
              #{{ user.userId }} {{ user.nickname || user.username }}（{{ user.username }}）
            </option>
          </select>
        </div>

        <div class="field">
          <label for="recharge-amount">充值金额（元）</label>
          <input
            id="recharge-amount"
            v-model.number="form.amountYuan"
            class="input"
            type="number"
            min="0.01"
            step="0.01"
            placeholder="例如 10.00"
          />
          <span class="hint">充值后按平台模型的实际输入/输出 Token 消耗。</span>
        </div>

        <div class="field remark-field">
          <label for="recharge-remark">备注</label>
          <input
            id="recharge-remark"
            v-model.trim="form.remark"
            class="input"
            type="text"
            maxlength="255"
            placeholder="可选，例如：首次充值"
          />
        </div>

        <button class="btn btn-primary recharge-submit" type="submit" :disabled="submitting">
          <AppIcon name="check" :size="15" />
          {{ submitting ? '充值中…' : '确认充值' }}
        </button>
      </form>
    </section>

    <section class="card card-pad">
      <div class="card-head">
        <h3><AppIcon name="shield" :size="18" />用户额度总览</h3>
        <button class="btn btn-soft btn-sm" type="button" :disabled="loading" @click="loadOverview">
          <AppIcon name="refresh" :size="15" />
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>

      <div class="billing-table-wrap">
        <table class="billing-table">
          <thead>
            <tr>
              <th>用户</th>
              <th>角色</th>
              <th class="num">余额</th>
              <th class="num">累计消耗</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.userId">
              <td>
                <strong>{{ user.nickname || user.username }}</strong>
                <span class="user-sub">#{{ user.userId }} · {{ user.username }}</span>
              </td>
              <td>
                <span :class="['tag', user.admin ? 'tag-brand' : '']">
                  {{ user.admin ? '管理员' : '普通用户' }}
                </span>
              </td>
              <td class="num balance">¥ {{ formatYuan(user.balance) }}</td>
              <td class="num">¥ {{ formatYuan(user.totalConsumed) }}</td>
              <td>
                <button class="btn btn-soft btn-sm" type="button" @click="selectUser(user)">
                  充值
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { getWalletOverview, rechargeWallet } from '@/api/wallet'
import type { WalletOverview } from '@/types/billing'

const users = ref<WalletOverview[]>([])
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive({
  userId: 0,
  amountYuan: 10,
  remark: ''
})

const totalBalance = computed(() => users.value.reduce((sum, user) => sum + Number(user.balance), 0))
const totalConsumed = computed(() => users.value.reduce((sum, user) => sum + Number(user.totalConsumed), 0))

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

function selectUser(user: WalletOverview) {
  form.userId = user.userId
  successMessage.value = ''
  errorMessage.value = ''
}

async function loadOverview() {
  loading.value = true
  try {
    users.value = await getWalletOverview()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载用户额度失败'
  } finally {
    loading.value = false
  }
}

async function handleRecharge() {
  errorMessage.value = ''
  successMessage.value = ''
  if (!form.userId) {
    errorMessage.value = '请选择要充值的用户'
    return
  }
  const amount = Number(form.amountYuan)
  if (!Number.isFinite(amount) || amount < 0.01) {
    errorMessage.value = '充值金额至少为 0.01 元'
    return
  }

  submitting.value = true
  try {
    const target = users.value.find((user) => user.userId === form.userId)
    const wallet = await rechargeWallet({
      userId: form.userId,
      amountYuan: amount,
      remark: form.remark || undefined
    })
    successMessage.value = `已为 ${target?.nickname || target?.username || '用户'} 充值 ¥${formatYuan(amount)}，当前余额 ¥${formatYuan(wallet.balance)}。`
    await loadOverview()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '充值失败'
  } finally {
    submitting.value = false
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.recharge-form {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(180px, 240px) minmax(220px, 1fr) auto;
  align-items: end;
  gap: 14px;
}

.remark-field {
  min-width: 0;
}

.recharge-submit {
  height: 40px;
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
  padding: 12px;
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

.billing-table .balance {
  font-weight: 600;
  color: var(--accent-yellow-ink);
}

.user-sub {
  display: block;
  margin-top: 1px;
  font-size: 12px;
  color: var(--text-3);
}

@media (max-width: 980px) {
  .recharge-form {
    grid-template-columns: 1fr 1fr;
  }

  .recharge-submit {
    grid-column: span 2;
  }
}

@media (max-width: 640px) {
  .recharge-form {
    grid-template-columns: 1fr;
  }

  .recharge-submit {
    grid-column: auto;
  }
}
</style>
