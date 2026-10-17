<template>
  <div class="auth">
    <section class="auth-brand">
      <div class="brand">
        <span class="brand-mark"><AppIcon name="sparkles" :size="20" /></span>
        <span class="brand-text">
          <span class="brand-name">智能体平台</span>
          <span class="brand-sub">agent-platform</span>
        </span>
      </div>

      <div class="auth-headline">
        <h1>三步开始你的<br />AI 辅助开发工作流</h1>
        <p>注册账号 → 登录平台 → 在对话里让 Agent 帮你检索仓库、分析技术栈、沉淀项目清单。</p>

        <div class="auth-points">
          <div v-for="point in points" :key="point.text" class="auth-point">
            <span class="icon-slot"><AppIcon :name="point.icon" :size="16" /></span>
            <span>{{ point.text }}</span>
          </div>
        </div>
      </div>

      <div class="auth-tech">
        <span v-for="tech in techs" :key="tech">{{ tech }}</span>
      </div>
    </section>

    <section class="auth-form-side">
      <div class="auth-form">
        <h2>创建账号</h2>
        <p class="subtitle">注册成功后会自动登录并跳转到个人中心</p>

        <div v-if="errorMessage" class="alert alert-error">
          <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
          <span>{{ errorMessage }}</span>
        </div>
        <div v-if="successMessage" class="alert alert-success">
          <span class="icon-slot"><AppIcon name="check" :size="16" /></span>
          <span>{{ successMessage }}</span>
        </div>

        <form @submit.prevent="handleSubmit">
          <div class="field">
            <label for="username">用户名 <span class="required">*</span></label>
            <div class="input-icon">
              <span class="icon-slot"><AppIcon name="user" :size="16" /></span>
              <input
                id="username"
                v-model.trim="form.username"
                class="input"
                type="text"
                placeholder="4-20 位字母、数字或下划线"
                autocomplete="username"
              />
            </div>
          </div>

          <div class="field">
            <label for="password">密码 <span class="required">*</span></label>
            <div class="input-icon">
              <span class="icon-slot"><AppIcon name="lock" :size="16" /></span>
              <input
                id="password"
                v-model="form.password"
                class="input"
                type="password"
                placeholder="6-32 位"
                autocomplete="new-password"
              />
            </div>
          </div>

          <div class="field">
            <label for="confirmPassword">确认密码 <span class="required">*</span></label>
            <div class="input-icon">
              <span class="icon-slot"><AppIcon name="shield" :size="16" /></span>
              <input
                id="confirmPassword"
                v-model="form.confirmPassword"
                class="input"
                type="password"
                placeholder="请再次输入密码"
                autocomplete="new-password"
              />
            </div>
          </div>

          <div class="field">
            <label for="nickname">昵称</label>
            <div class="input-icon">
              <span class="icon-slot"><AppIcon name="edit" :size="16" /></span>
              <input
                id="nickname"
                v-model.trim="form.nickname"
                class="input"
                type="text"
                placeholder="选填，默认与用户名相同"
              />
            </div>
          </div>

          <div class="field">
            <label for="email">邮箱</label>
            <div class="input-icon">
              <span class="icon-slot"><AppIcon name="mail" :size="16" /></span>
              <input
                id="email"
                v-model.trim="form.email"
                class="input"
                type="email"
                placeholder="选填"
                autocomplete="email"
              />
            </div>
          </div>

          <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
            {{ loading ? '注册中…' : '注册并登录' }}
            <AppIcon v-if="!loading" name="arrowRight" :size="17" />
          </button>
        </form>

        <p class="auth-footer">
          已有账号？<router-link to="/login">返回登录</router-link>
        </p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/auth'
import AppIcon from '@/components/AppIcon.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const points = [
  { icon: 'cpu' as const, text: 'Agent 工具调用：开源仓库检索与分析' },
  { icon: 'key' as const, text: 'JWT 签发与网关校验，身份不落前端' },
  { icon: 'file' as const, text: '问答自动落库，随时回看历史会话' }
]

const techs = ['JWT', 'Nacos', 'OpenFeign', 'Gateway', 'Prometheus']

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: ''
})
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

function validate(): string {
  if (!form.username || !form.password || !form.confirmPassword) {
    return '请填写用户名、密码与确认密码'
  }
  if (!/^[a-zA-Z0-9_]{4,20}$/.test(form.username)) {
    return '用户名需为 4-20 位字母、数字或下划线'
  }
  if (form.password.length < 6 || form.password.length > 32) {
    return '密码长度需为 6-32 位'
  }
  if (form.password !== form.confirmPassword) {
    return '两次输入的密码不一致'
  }
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    return '邮箱格式不正确'
  }
  return ''
}

async function handleSubmit() {
  errorMessage.value = ''
  successMessage.value = ''

  const invalid = validate()
  if (invalid) {
    errorMessage.value = invalid
    return
  }

  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      confirmPassword: form.confirmPassword,
      nickname: form.nickname || undefined,
      email: form.email || undefined
    })
    successMessage.value = '注册成功，正在自动登录…'
    await userStore.login({ username: form.username, password: form.password })
    await router.replace('/profile')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>