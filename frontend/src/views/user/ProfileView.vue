<template>
  <div class="page">
    <div class="kpi-grid">
      <div v-for="item in kpis" :key="item.label" class="kpi">
        <span class="icon-slot"><AppIcon :name="item.icon" :size="19" /></span>
        <div>
          <div class="kpi-label">{{ item.label }}</div>
          <div class="kpi-value">{{ item.value }}</div>
        </div>
      </div>
    </div>

    <section class="card card-pad">
      <div class="profile-head">
        <span class="avatar avatar-lg">{{ avatarText }}</span>
        <div class="who">
          <h2>{{ userStore.displayName || '未登录' }}</h2>
          <p>当前登录账号 · 令牌由网关校验后透传身份</p>
        </div>
        <div class="page-head-actions">
          <button class="btn btn-soft" type="button" :disabled="loading" @click="loadProfile">
            <AppIcon name="refresh" :size="15" :class="{ spin: loading }" />
            {{ loading ? '加载中…' : '刷新信息' }}
          </button>
        </div>
      </div>

      <div v-if="errorMessage" class="alert alert-error" style="margin-top: 16px">
        <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
        <span>{{ errorMessage }}</span>
      </div>

      <div class="divider"></div>

      <div class="info-grid">
        <div v-for="row in infoRows" :key="row.label" class="info-row">
          <span class="k"><AppIcon :name="row.icon" :size="15" />{{ row.label }}</span>
          <span class="v">{{ row.value }}</span>
        </div>
      </div>

      <div class="note-line">
        <AppIcon name="info" :size="16" />
        <span>
          本页数据来自 <code>GET /api/user/me</code>，请求头携带登录时签发的 JWT 令牌；
          由网关先校验令牌再写入用户标识，服务端不信任客户端传入的用户 ID。
        </span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/types/user'

const userStore = useUserStore()
const loading = ref(false)
const errorMessage = ref('')

const userInfo = computed<UserInfo | null>(() => userStore.userInfo)
const avatarText = computed(() => (userStore.displayName || 'U').slice(0, 1).toUpperCase())

const kpis = computed(() => [
  { label: '用户 ID', value: String(userInfo.value?.id ?? '-'), icon: 'key' as const },
  { label: '用户名', value: userInfo.value?.username ?? '-', icon: 'user' as const },
  { label: '邮箱', value: userInfo.value?.email || '未填写', icon: 'mail' as const },
  {
    label: '注册时间',
    value: formatTime(userInfo.value?.createTime),
    icon: 'calendar' as const
  }
])

const infoRows = computed(() => [
  { label: '用户 ID', value: String(userInfo.value?.id ?? '-'), icon: 'key' as const },
  { label: '用户名', value: userInfo.value?.username ?? '-', icon: 'user' as const },
  { label: '昵称', value: userInfo.value?.nickname || '未填写', icon: 'edit' as const },
  { label: '邮箱', value: userInfo.value?.email || '未填写', icon: 'mail' as const },
  { label: '手机号', value: userInfo.value?.phone || '未填写', icon: 'phone' as const },
  {
    label: '注册时间',
    value: formatTime(userInfo.value?.createTime),
    icon: 'calendar' as const
  }
])

async function loadProfile() {
  loading.value = true
  errorMessage.value = ''
  try {
    await userStore.fetchCurrentUser()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载用户信息失败'
  } finally {
    loading.value = false
  }
}

function formatTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  if (!userStore.userInfo) {
    void loadProfile()
  }
})
</script>