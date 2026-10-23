<template>
  <div class="page model-settings-page">
    <div class="kpi-grid">
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="cpu" :size="19" /></span>
        <div>
          <div class="kpi-label">当前模型来源</div>
          <div class="kpi-value">{{ activeSource }}</div>
        </div>
      </div>
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="key" :size="19" /></span>
        <div>
          <div class="kpi-label">API Key</div>
          <div class="kpi-value">{{ config?.apiKeyMasked || '未配置' }}</div>
        </div>
      </div>
      <div class="kpi">
        <span class="icon-slot"><AppIcon name="clock" :size="19" /></span>
        <div>
          <div class="kpi-label">最近测试</div>
          <div class="kpi-value">{{ config?.lastTestTime || '暂无' }}</div>
        </div>
      </div>
    </div>

    <section class="card card-pad">
      <div class="card-head">
        <h3><AppIcon name="cpu" :size="18" />我的大模型 API</h3>
        <span :class="['tag', config?.configured && config.enabled ? 'tag-brand' : '']">
          {{ activeSource }}
        </span>
      </div>

      <p class="subtitle model-desc">
        填写任意 OpenAI 兼容接口后，普通对话、Agent、项目推荐和长期记忆抽取都会优先使用你的 API Key；
        关闭或删除配置后自动回退平台默认 DeepSeek。
      </p>

      <div class="alert alert-info model-alert">
        <span class="icon-slot"><AppIcon name="info" :size="16" /></span>
        <span v-if="isAdmin">
          你是管理员：使用平台默认模型不扣平台额度；如果填写并启用下面的配置，则优先使用你自己的 API Key。
        </span>
        <span v-else>
          使用平台默认模型时，按实际输入/输出 Token 从
          <router-link to="/wallet">额度中心</router-link>
          扣费；填写并启用下面的配置后，不再消耗平台额度。
        </span>
      </div>

      <div v-if="errorMessage" class="alert alert-error model-alert">
        <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
        <span>{{ errorMessage }}</span>
      </div>
      <div v-if="successMessage" class="alert alert-success model-alert">
        <span class="icon-slot"><AppIcon name="check" :size="16" /></span>
        <span>{{ successMessage }}</span>
      </div>
      <div v-if="testResult" :class="['alert', testResult.success ? 'alert-success' : 'alert-error', 'model-alert']">
        <span class="icon-slot">
          <AppIcon :name="testResult.success ? 'check' : 'alert'" :size="16" />
        </span>
        <span>
          {{ testResult.message }}
          <template v-if="testResult.success">
            · 模型 {{ testResult.model }} · {{ testResult.latencyMs }} ms
          </template>
        </span>
      </div>

      <form class="model-form" @submit.prevent="handleSave">
        <label class="use-switch">
          <input v-model="form.enabled" type="checkbox" />
          <span>
            <strong>使用我的模型</strong>
            <small>关闭后仍保留配置，但所有 AI 请求回退平台默认模型。</small>
          </span>
        </label>

        <div class="field">
          <label for="model-base-url">Base URL <span class="required">*</span></label>
          <input
            id="model-base-url"
            v-model.trim="form.baseUrl"
            class="input"
            type="url"
            placeholder="例如 https://api.deepseek.com 或 https://api.openai.com/v1"
            autocomplete="url"
          />
          <span class="hint">只填写到版本路径，不要包含 /chat/completions。</span>
        </div>

        <div class="form-grid">
          <div class="field">
            <label for="model-name">模型名 <span class="required">*</span></label>
            <input
              id="model-name"
              v-model.trim="form.model"
              class="input"
              type="text"
              placeholder="例如 deepseek-chat"
              autocomplete="off"
            />
          </div>

          <div class="field">
            <label for="model-api-key">API Key</label>
            <input
              id="model-api-key"
              v-model.trim="form.apiKey"
              class="input"
              type="password"
              :placeholder="config?.configured ? `已保存 ${config.apiKeyMasked}，留空不修改` : '请输入你的 API Key'"
              autocomplete="new-password"
            />
            <span class="hint">Key 会加密保存，页面和接口都不会返回明文。</span>
          </div>
        </div>

        <div class="model-actions">
          <button class="btn btn-primary" type="submit" :disabled="saving || loading">
            <AppIcon name="check" :size="15" />
            {{ saving ? '保存中…' : '保存配置' }}
          </button>
          <button class="btn btn-soft" type="button" :disabled="testing || saving" @click="handleTest">
            <AppIcon name="activity" :size="15" />
            {{ testing ? '测试中…' : '测试连接' }}
          </button>
          <button
            v-if="config?.configured"
            class="btn btn-danger"
            type="button"
            :disabled="deleting || saving"
            @click="handleDelete"
          >
            <AppIcon name="trash" :size="15" />
            {{ deleting ? '删除中…' : '恢复平台默认' }}
          </button>
        </div>
      </form>

      <div class="note-line">
        <AppIcon name="shield" :size="16" />
        <span>
          API Key 使用 AES-GCM 加密后存入 MySQL，仅用于代替平台 Token 调用模型；
          请勿在公共设备保存个人 Key，生产部署应使用 HTTPS。
        </span>
      </div>
    </section>

    <section class="card card-pad">
      <div class="card-head">
        <h3><AppIcon name="info" :size="18" />常见配置示例</h3>
      </div>
      <div class="provider-grid">
        <div v-for="provider in providers" :key="provider.name" class="provider-item">
          <strong>{{ provider.name }}</strong>
          <code>{{ provider.baseUrl }}</code>
          <span>{{ provider.model }}</span>
        </div>
      </div>
      <p class="subtitle model-desc">
        本地 Ollama 运行在宿主机时，Docker 内的 ai-service 可填写
        <code>http://host.docker.internal:11434/v1</code>。
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { deleteModelConfig, getModelConfig, saveModelConfig, testModelConfig } from '@/api/modelConfig'
import { useUserStore } from '@/stores/user'
import type { UserModelConfig, UserModelTestResult } from '@/types/modelConfig'

const config = ref<UserModelConfig | null>(null)
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const deleting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const testResult = ref<UserModelTestResult | null>(null)
const userStore = useUserStore()
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')

const form = reactive({
  enabled: true,
  baseUrl: '',
  apiKey: '',
  model: ''
})

const providers = [
  { name: 'DeepSeek', baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat' },
  { name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4.1-mini' },
  { name: '硅基流动', baseUrl: 'https://api.siliconflow.cn/v1', model: 'deepseek-ai/DeepSeek-V3' },
  { name: '本地 Ollama', baseUrl: 'http://host.docker.internal:11434/v1', model: 'qwen2.5:7b' }
]

const activeSource = computed(() => {
  const platform = isAdmin.value ? '平台默认（管理员免扣费）' : '平台默认'
  if (!config.value?.configured) return platform
  return config.value.enabled ? '我的模型' : `${platform}（配置已保存）`
})

function fillForm(value: UserModelConfig) {
  config.value = value
  form.enabled = value.configured ? value.enabled : true
  form.baseUrl = value.baseUrl || ''
  form.model = value.model || ''
  form.apiKey = ''
}

async function loadConfig() {
  loading.value = true
  errorMessage.value = ''
  try {
    fillForm(await getModelConfig())
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载模型配置失败'
  } finally {
    loading.value = false
  }
}

function validate(): boolean {
  if (!form.baseUrl.trim()) {
    errorMessage.value = '请填写 Base URL'
    return false
  }
  if (!form.model.trim()) {
    errorMessage.value = '请填写模型名'
    return false
  }
  if (!config.value?.configured && !form.apiKey.trim()) {
    errorMessage.value = '首次配置需要填写 API Key'
    return false
  }
  return true
}

function payload() {
  return {
    baseUrl: form.baseUrl.trim(),
    apiKey: form.apiKey.trim() || undefined,
    model: form.model.trim(),
    enabled: form.enabled
  }
}

async function handleSave() {
  errorMessage.value = ''
  successMessage.value = ''
  testResult.value = null
  if (!validate()) return
  saving.value = true
  try {
    fillForm(await saveModelConfig(payload()))
    successMessage.value = '配置已保存，新的 AI 请求将使用当前设置。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  errorMessage.value = ''
  successMessage.value = ''
  testResult.value = null
  if (!validate()) return
  testing.value = true
  try {
    const result = await testModelConfig(payload())
    testResult.value = result
    if (!result.success) {
      errorMessage.value = result.message
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '连接测试失败'
  } finally {
    testing.value = false
  }
}

async function handleDelete() {
  if (!window.confirm('删除后所有 AI 请求将回退平台默认模型，确定删除吗？')) return
  errorMessage.value = ''
  successMessage.value = ''
  testResult.value = null
  deleting.value = true
  try {
    await deleteModelConfig()
    fillForm({
      configured: false,
      enabled: false,
      baseUrl: '',
      model: '',
      apiKeyMasked: '',
      lastTestTime: null,
      updateTime: null
    })
    successMessage.value = '已删除自定义配置，当前使用平台默认模型。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '删除失败'
  } finally {
    deleting.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.model-desc {
  margin-bottom: 18px;
}

.model-alert {
  margin-bottom: 14px;
}

.model-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.use-switch {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 13px 15px;
  cursor: pointer;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: var(--r);
}

.use-switch input {
  width: 17px;
  height: 17px;
  margin-top: 2px;
  accent-color: var(--brand);
}

.use-switch span {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.use-switch strong {
  font-size: 13.5px;
  color: var(--text);
}

.use-switch small {
  font-size: 12px;
  color: var(--text-3);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.model-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 2px;
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.provider-item {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 13px 14px;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: var(--r);
}

.provider-item strong {
  font-size: 13px;
}

.provider-item span {
  font-size: 12px;
  color: var(--text-3);
}

.provider-item code {
  width: fit-content;
  max-width: 100%;
  overflow-wrap: anywhere;
}

@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
