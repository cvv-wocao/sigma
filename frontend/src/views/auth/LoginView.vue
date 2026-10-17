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
        <h1>让 AI 智能体<br />帮你检索、分析与沉淀开源项目</h1>
        <p>
          基于 Spring Cloud 微服务与 Spring AI 构建：网关统一鉴权、Agent 工具调用、
          会话记忆与完整对话历史落库。
        </p>

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
        <h2>欢迎回来</h2>
        <p class="subtitle">登录后即可使用智能对话、代码检索与项目管理</p>

        <div v-if="errorMessage" class="alert alert-error">
          <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
          <span>{{ errorMessage }}</span>
        </div>

        <form @submit.prevent="handleSubmit">
          <div class="field">
            <label for="username">用户名</label>
            <div class="input-icon">
              <span class="icon-slot"><AppIcon name="user" :size="16" /></span>
              <input
                id="username"
                v-model.trim="form.username"
                class="input"
                type="text"
                placeholder="请输入用户名"
                autocomplete="username"
              />
            </div>
          </div>

          <div class="field">
            <label for="password">密码</label>
            <div class="input-icon">
              <span class="icon-slot"><AppIcon name="lock" :size="16" /></span>
              <input
                id="password"
                v-model="form.password"
                class="input"
                type="password"
                placeholder="请输入密码"
                autocomplete="current-password"
              />
            </div>
          </div>

          <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登录' }}
            <AppIcon v-if="!loading" name="arrowRight" :size="17" />
          </button>
        </form>

        <p class="auth-footer">
          还没有账号？<router-link to="/register">立即注册</router-link>
        </p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const points = [
  { icon: 'shield' as const, text: '网关统一鉴权，令牌校验与身份透传' },
  { icon: 'sparkles' as const, text: 'Spring AI Agent：工具调用 + 会话记忆' },
  { icon: 'database' as const, text: 'Redis 短期记忆 · MySQL 完整历史' },
  { icon: 'layers' as const, text: 'Docker Compose 一键拉起全栈依赖' }
]

const techs = ['Spring Boot 4', 'Spring Cloud 2025.1', 'Spring AI 2.0', 'Nacos', 'Redis', 'MySQL']

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorMessage = ref('')

async function handleSubmit() {
  errorMessage.value = ''
  if (!form.username || !form.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/recommend'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>