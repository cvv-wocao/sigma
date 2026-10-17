<template>
  <div class="history">
    <aside class="conv-panel">
      <div class="conv-head">
        <h3>历史会话</h3>
        <button
          class="btn btn-icon btn-sm"
          type="button"
          title="刷新会话列表"
          :disabled="loadingConversations"
          @click="loadConversations"
        >
          <AppIcon name="refresh" :size="15" :class="{ spin: loadingConversations }" />
        </button>
      </div>

      <div v-if="loadingConversations" class="loading-line">
        <AppIcon name="refresh" :size="15" class="spin" />
        正在加载会话…
      </div>

      <div v-else-if="conversations.length === 0" class="empty">
        <span class="empty-icon"><AppIcon name="chat" :size="24" /></span>
        <span class="empty-title">还没有历史会话</span>
        <span class="empty-desc">去「智能对话」提问后，每一轮问答都会记录在这里。</span>
      </div>

      <ul v-else class="conv-list">
        <li
          v-for="conversation in conversations"
          :key="conversation.conversationId"
          class="conv-item"
          :class="{ active: conversation.conversationId === activeConversationId }"
          @click="selectConversation(conversation.conversationId)"
        >
          <div class="conv-title">
            <AppIcon name="chat" :size="14" />
            <span>{{ conversation.title || '未命名会话' }}</span>
          </div>
          <div class="conv-meta">
            <span>{{ conversation.messageCount }} 条消息</span>
            <span>{{ conversation.updateTime }}</span>
          </div>
        </li>
      </ul>
    </aside>

    <section class="thread">
      <div class="thread-head">
        <h3>{{ activeTitle }}</h3>
        <span v-if="activeConversationId" class="tag tag-mono">
          {{ activeConversationId.slice(0, 8) }}
        </span>
      </div>

      <div class="thread-body">
        <div v-if="errorMessage" class="alert alert-error">
          <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
          <span>{{ errorMessage }}</span>
        </div>

        <div v-if="!activeConversationId" class="empty">
          <span class="empty-icon"><AppIcon name="inbox" :size="24" /></span>
          <span class="empty-title">选择左侧会话</span>
          <span class="empty-desc">点击任意一条历史会话，即可回看当时的完整问答内容。</span>
        </div>

        <div v-else-if="loadingMessages" class="loading-line">
          <AppIcon name="refresh" :size="15" class="spin" />
          正在加载消息…
        </div>

        <div v-else-if="messages.length === 0" class="empty">
          <span class="empty-icon"><AppIcon name="file" :size="24" /></span>
          <span class="empty-title">该会话没有消息</span>
        </div>

        <template v-else>
          <div
            v-for="(message, index) in messages"
            :key="index"
            class="msg"
            :class="message.role === 'USER' ? 'user' : 'assistant'"
          >
            <div class="msg-avatar" :class="message.role === 'USER' ? 'me' : 'bot'">
              <AppIcon :name="message.role === 'USER' ? 'user' : 'sparkles'" :size="16" />
            </div>
            <div class="msg-main">
              <div class="msg-meta">
                <strong>{{ message.role === 'USER' ? '我' : 'Agent' }}</strong>
                <span v-if="message.model" class="tag tag-mono">{{ message.model }}</span>
                <span v-if="message.createTime">{{ message.createTime }}</span>
              </div>
              <div class="msg-body" v-html="linkify(message.content)"></div>
            </div>
          </div>
        </template>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listConversations, listMessages } from '@/api/history'
import AppIcon from '@/components/AppIcon.vue'
import { linkify } from '@/utils/linkify'
import type { Conversation, HistoryMessage } from '@/types/history'

const conversations = ref<Conversation[]>([])
const messages = ref<HistoryMessage[]>([])
const activeConversationId = ref('')

const loadingConversations = ref(false)
const loadingMessages = ref(false)
const errorMessage = ref('')

const activeTitle = computed(
  () =>
    conversations.value.find((item) => item.conversationId === activeConversationId.value)?.title ||
    '会话详情'
)

async function loadConversations() {
  errorMessage.value = ''
  loadingConversations.value = true
  try {
    conversations.value = await listConversations()
    if (conversations.value.length === 0) {
      activeConversationId.value = ''
      messages.value = []
      return
    }
    // 保持当前选中项；若已失效则回落到第一条
    const stillExists = conversations.value.some(
      (conversation) => conversation.conversationId === activeConversationId.value
    )
    if (!stillExists) {
      await selectConversation(conversations.value[0].conversationId)
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载会话列表失败'
  } finally {
    loadingConversations.value = false
  }
}

async function selectConversation(conversationId: string) {
  activeConversationId.value = conversationId
  errorMessage.value = ''
  loadingMessages.value = true
  try {
    messages.value = await listMessages(conversationId)
  } catch (error) {
    messages.value = []
    errorMessage.value = error instanceof Error ? error.message : '加载会话消息失败'
  } finally {
    loadingMessages.value = false
  }
}

onMounted(loadConversations)
</script>