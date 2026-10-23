<template>
  <div class="page profile-page">
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
        <span class="avatar avatar-lg">
          <img
            v-if="showAvatarImage"
            :src="userInfo?.avatar || ''"
            alt="用户头像"
            referrerpolicy="no-referrer"
            @error="avatarLoadFailed = true"
          />
          <template v-else>{{ avatarText }}</template>
        </span>
        <div class="who">
          <h2>{{ userStore.displayName || '未登录' }}</h2>
          <p>当前登录账号 · 用户名不可修改，其余资料可自行维护</p>
        </div>
        <div class="page-head-actions">
          <button v-if="!editing" class="btn btn-primary" type="button" @click="beginEdit">
            <AppIcon name="edit" :size="15" />
            编辑资料
          </button>
          <button class="btn btn-soft" type="button" :disabled="loading || editing" @click="loadProfile">
            <AppIcon name="refresh" :size="15" :class="{ spin: loading }" />
            {{ loading ? '加载中…' : '刷新信息' }}
          </button>
        </div>
      </div>

      <div v-if="errorMessage" class="alert alert-error profile-alert">
        <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
        <span>{{ errorMessage }}</span>
      </div>
      <div v-if="successMessage" class="alert alert-success profile-alert">
        <span class="icon-slot"><AppIcon name="check" :size="16" /></span>
        <span>{{ successMessage }}</span>
      </div>

      <div class="divider"></div>

      <template v-if="!editing">
        <div class="info-grid">
          <div v-for="row in infoRows" :key="row.label" class="info-row">
            <span class="k"><AppIcon :name="row.icon" :size="15" />{{ row.label }}</span>
            <span class="v">{{ row.value }}</span>
          </div>
        </div>

        <div class="note-line">
          <AppIcon name="info" :size="16" />
          <span>
            本页数据来自 <code>GET /api/user/me</code>；点击“编辑资料”可通过
            <code>PUT /api/user/me</code> 修改昵称、邮箱、手机号和头像地址。
          </span>
        </div>
      </template>

      <form v-else class="profile-form" @submit.prevent="handleSave">
        <div class="profile-form-grid">
          <div class="field">
            <label for="profile-nickname">昵称 <span class="required">*</span></label>
            <input
              id="profile-nickname"
              v-model="form.nickname"
              class="input"
              type="text"
              maxlength="20"
              autocomplete="nickname"
              placeholder="请输入昵称"
            />
            <span class="hint">昵称会显示在页面顶部，最多 20 个字符。</span>
          </div>

          <div class="field">
            <label for="profile-email">邮箱</label>
            <input
              id="profile-email"
              v-model.trim="form.email"
              class="input"
              type="email"
              maxlength="100"
              autocomplete="email"
              placeholder="name@example.com"
            />
            <span class="hint">留空表示清除已保存的邮箱。</span>
          </div>

          <div class="field">
            <label for="profile-phone">手机号</label>
            <input
              id="profile-phone"
              v-model.trim="form.phone"
              class="input"
              type="tel"
              maxlength="20"
              autocomplete="tel"
              placeholder="例如 13800138000"
            />
            <span class="hint">支持数字、空格、+ 和 -，留空表示清除。</span>
          </div>

          <div class="field">
            <label for="profile-avatar">头像地址</label>
            <input
              id="profile-avatar"
              v-model.trim="form.avatar"
              class="input"
              type="url"
              maxlength="255"
              autocomplete="url"
              placeholder="https://example.com/avatar.png"
            />
            <span class="hint">填写 http/https 图片地址，留空则显示昵称首字。</span>
          </div>
        </div>

        <div class="profile-form-actions">
          <button class="btn btn-soft" type="button" :disabled="saving" @click="cancelEdit">取消</button>
          <button class="btn btn-primary" type="submit" :disabled="saving">
            <AppIcon name="check" :size="15" />
            {{ saving ? '保存中…' : '保存修改' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/types/user'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const avatarLoadFailed = ref(false)

const form = reactive({
  nickname: '',
  email: '',
  phone: '',
  avatar: ''
})

const userInfo = computed<UserInfo | null>(() => userStore.userInfo)
const avatarText = computed(() => (userStore.displayName || 'U').slice(0, 1).toUpperCase())
const showAvatarImage = computed(() => Boolean(userInfo.value?.avatar) && !avatarLoadFailed.value)

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
  { label: '头像', value: userInfo.value?.avatar ? '已设置' : '未设置', icon: 'edit' as const },
  {
    label: '注册时间',
    value: formatTime(userInfo.value?.createTime),
    icon: 'calendar' as const
  }
])

watch(
  () => userInfo.value?.avatar,
  () => {
    avatarLoadFailed.value = false
  }
)

function fillForm() {
  form.nickname = userInfo.value?.nickname || userInfo.value?.username || ''
  form.email = userInfo.value?.email || ''
  form.phone = userInfo.value?.phone || ''
  form.avatar = userInfo.value?.avatar || ''
}

function beginEdit() {
  fillForm()
  errorMessage.value = ''
  successMessage.value = ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  errorMessage.value = ''
  fillForm()
}

function validateProfile(): string {
  const nickname = form.nickname.trim()
  if (!nickname) {
    return '昵称不能为空'
  }
  if (nickname.length > 20) {
    return '昵称最长 20 个字符'
  }
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    return '邮箱格式不正确'
  }
  if (form.phone && !/^[0-9+\-\s]{6,20}$/.test(form.phone)) {
    return '手机号格式不正确'
  }
  if (form.avatar && !/^https?:\/\/\S+$/.test(form.avatar)) {
    return '头像地址必须是 http/https URL'
  }
  return ''
}

async function handleSave() {
  errorMessage.value = ''
  successMessage.value = ''
  const invalid = validateProfile()
  if (invalid) {
    errorMessage.value = invalid
    return
  }

  saving.value = true
  try {
    await userStore.updateProfile({
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      phone: form.phone.trim(),
      avatar: form.avatar.trim()
    })
    editing.value = false
    successMessage.value = '个人资料已更新。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

async function loadProfile() {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
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

<style scoped>
.profile-alert {
  margin-top: 16px;
}

.profile-head .avatar img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.profile-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.profile-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.profile-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 720px) {
  .profile-head {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .profile-head .page-head-actions {
    width: 100%;
    margin-left: 0;
  }

  .profile-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
