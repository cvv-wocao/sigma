<template>
  <div class="chat">
    <header class="chat-head">
      <span class="chip">
        <AppIcon name="chat" :size="14" />
        {{ chatStore.conversationId ? chatStore.conversationId.slice(0, 8) : '新会话' }}
      </span>
      <span v-if="hasContext" class="tag tag-brand" title="本轮提问会带上右侧推荐结果作为参考数据">
        <AppIcon name="sparkles" :size="13" />
        已关联推荐结果
      </span>
      <span v-else class="tag">
        <AppIcon name="cpu" :size="13" />
        工具：仓库检索
      </span>
      <button class="btn btn-soft btn-sm" type="button" :disabled="loading" @click="startNewConversation">
        <AppIcon name="plus" :size="15" />
        新会话
      </button>
    </header>

    <div ref="messageListRef" class="chat-body">
      <div v-if="chatStore.messages.length === 0" class="suggest">
        <div class="suggest-head">
          <span class="empty-icon"><AppIcon name="sparkles" :size="24" /></span>
          <h3>{{ hasContext ? '就这批推荐结果继续问' : '想让 Agent 帮你做什么？' }}</h3>
          <p>{{ hasContext ? '已经带上当前推荐结果，可以直接追问对比、风险与取舍' : '支持多轮上下文，会自动记住本次会话里说过的仓库与需求' }}</p>
        </div>
        <div class="suggest-grid">
          <button
            v-for="(sample, index) in activeSamples"
            :key="sample"
            class="suggest-card"
            type="button"
            @click="fillSample(sample)"
          >
            <span class="icon-slot">
              <AppIcon :name="sampleIcons[index] ?? 'search'" :size="16" />
            </span>
            <span>{{ sample }}</span>
          </button>
        </div>
      </div>

      <template v-else>
        <div
          v-for="(message, index) in chatStore.messages"
          :key="index"
          class="msg"
          :class="[message.role, { error: message.error }]"
        >
          <div class="msg-avatar" :class="message.role === 'user' ? 'me' : 'bot'">
            <AppIcon :name="message.role === 'user' ? 'user' : 'sparkles'" :size="16" />
          </div>
          <div class="msg-main">
            <div class="msg-meta">
              <strong>{{ message.role === 'user' ? '我' : 'Agent' }}</strong>
              <span v-if="message.model" class="tag tag-mono">{{ message.model }}</span>
              <button
                v-if="message.role === 'assistant' && !message.error"
                class="btn btn-icon btn-sm"
                type="button"
                :title="copiedIndex === index ? '已复制' : '复制回答'"
                @click="copyMessage(message.content, index)"
              >
                <AppIcon :name="copiedIndex === index ? 'check' : 'copy'" :size="14" />
              </button>
            </div>
            <div class="msg-body" v-html="linkify(message.content)"></div>
          </div>
        </div>

        <div v-if="loading" class="msg assistant">
          <div class="msg-avatar bot"><AppIcon name="sparkles" :size="16" /></div>
          <div class="msg-main">
            <div class="msg-meta"><strong>Agent</strong><span>正在思考</span></div>
            <div class="msg-body">
              <span class="typing"><i></i><i></i><i></i></span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <form class="composer" @submit.prevent="handleSend">
      <div class="composer-box">
        <textarea
          v-model.trim="draft"
          rows="2"
          :placeholder="hasContext ? '问点什么，例如：这几个里哪个更适合小团队？' : '输入问题，Enter 发送 / Shift + Enter 换行'"
          :disabled="loading"
          @keydown.enter.exact.prevent="handleSend"
        ></textarea>
        <button class="btn btn-primary" type="submit" :disabled="loading || !draft">
          <AppIcon name="send" :size="16" />
          {{ loading ? '生成中' : '发送' }}
        </button>
      </div>
      <p class="composer-hint">回答由大模型生成，涉及仓库信息时请以仓库主页的实际内容为准</p>
    </form>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { agentChat } from '@/api/chat'
import AppIcon from '@/components/AppIcon.vue'
import { linkify } from '@/utils/linkify'
import { useChatStore } from '@/stores/chat'

/**
 * 对话面板：从原「智能对话」页搬到 AI 推荐页右侧。
 *
 * <p>props.context 是当前页面上的参考数据（推荐结果）。它只随请求下发、不落会话记忆，
 * 所以「有推荐结果」和「没有推荐结果」两种状态下用的是同一套逻辑，只是提示词不同。</p>
 */
const props = defineProps<{
  /** 页面参考数据；为空表示独立对话 */
  context?: string
}>()

/** 有推荐结果时的追问示例：都是「必须看到结果才问得出来」的问题 */
const contextSamples = [
  '这几个里面哪个最适合小团队快速上手？说说理由',
  '排名第一的那个项目，主要风险是什么？',
  '如果我要二开，哪个的可维护性更好？'
]

/** 独立对话的示例：覆盖详情 / 分支 / 检索三种工具用法 */
const plainSamples = [
  '帮我查一下 macrozheng/mall 这个仓库的详情',
  '刚才那个仓库有哪些分支？',
  '帮我搜搜和 springboot 相关的开源项目'
]

const sampleIcons = ['sparkles', 'alert', 'layers', 'search', 'branch']

const chatStore = useChatStore()

const draft = ref('')
const loading = ref(false)
const messageListRef = ref<HTMLElement | null>(null)
const copiedIndex = ref(-1)

const hasContext = computed(() => !!props.context && props.context.trim().length > 0)
const activeSamples = computed(() => (hasContext.value ? contextSamples : plainSamples))

function fillSample(text: string) {
  draft.value = text
}

function startNewConversation() {
  chatStore.reset()
  copiedIndex.value = -1
}

async function copyMessage(text: string, index: number) {
  try {
    await navigator.clipboard.writeText(text)
    copiedIndex.value = index
    window.setTimeout(() => {
      if (copiedIndex.value === index) {
        copiedIndex.value = -1
      }
    }, 1800)
  } catch {
    copiedIndex.value = -1
  }
}

async function handleSend() {
  const question = draft.value.trim()
  if (!question || loading.value) {
    return
  }

  chatStore.messages.push({ role: 'user', content: question })
  draft.value = ''
  loading.value = true
  await scrollToBottom()

  try {
    const result = await agentChat({
      message: question,
      conversationId: chatStore.conversationId || undefined,
      context: hasContext.value ? props.context : undefined
    })
    chatStore.conversationId = result.conversationId
    chatStore.messages.push({ role: 'assistant', content: result.answer, model: result.model })
  } catch (error) {
    chatStore.messages.push({
      role: 'assistant',
      content: error instanceof Error ? error.message : '请求失败，请稍后重试',
      error: true
    })
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

async function scrollToBottom() {
  await nextTick()
  const list = messageListRef.value
  if (list) {
    list.scrollTop = list.scrollHeight
  }
}

/** 首次挂载时把消息列表定位到最新一条（从别的页面切回来时用） */
watch(
  () => chatStore.messages.length,
  () => {
    void scrollToBottom()
  },
  { immediate: true }
)
</script>