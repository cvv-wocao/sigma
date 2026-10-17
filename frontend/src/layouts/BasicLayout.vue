<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark"><AppIcon name="sparkles" :size="20" /></span>
        <span class="brand-text">
          <span class="brand-name">智能体平台</span>
          <span class="brand-sub">agent-platform</span>
        </span>
      </div>

      <nav>
        <div v-for="group in navGroups" :key="group.label" class="nav-group">
          <p class="nav-label">{{ group.label }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="nav-link"
            active-class="active"
          >
            <span class="icon-slot"><AppIcon :name="item.icon" :size="18" /></span>
            <span>{{ item.label }}</span>
          </router-link>
        </div>
      </nav>

      <div class="sidebar-foot">
        <p>
          <span class="status-dot"></span>
          <span class="foot-text">网关在线 · 5 个微服务</span>
        </p>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-title">
          <h1>{{ page.title }}</h1>
          <p>{{ page.desc }}</p>
        </div>

        <div class="topbar-actions">
          <router-link to="/search" class="btn btn-soft btn-sm">
            <AppIcon name="search" :size="15" />
            找开源项目
          </router-link>

          <div ref="chipRef" class="user-chip">
            <button class="user-chip-btn" type="button" @click="menuOpen = !menuOpen">
              <span class="avatar avatar-sm">{{ avatarText }}</span>
              <span class="user-chip-info">
                <span class="user-chip-name">{{ userStore.displayName || '未登录' }}</span>
                <span class="user-chip-role">{{ userStore.userInfo?.username || '游客' }}</span>
              </span>
              <AppIcon name="chevronDown" :size="15" />
            </button>

            <div v-if="menuOpen" class="dropdown">
              <button class="dropdown-item" type="button" @click="go('/profile')">
                <AppIcon name="user" :size="16" />
                个人中心
              </button>
              <button class="dropdown-item" type="button" @click="go('/history')">
                <AppIcon name="history" :size="16" />
                历史记录
              </button>
              <div class="dropdown-sep"></div>
              <button class="dropdown-item danger" type="button" @click="handleLogout">
                <AppIcon name="logout" :size="16" />
                退出登录
              </button>
            </div>
          </div>
        </div>
      </header>

      <main class="content">
        <div class="content-inner" :class="{ 'is-wide': route.name === 'recommend' }">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menuOpen = ref(false)
const chipRef = ref<HTMLElement | null>(null)

/** 侧边栏导航：按「工作台 / 账户」分组 */
const navGroups = [
  {
    label: '工作台',
    items: [
      { to: '/recommend', label: 'AI 推荐', icon: 'sparkles' as const },
      { to: '/search', label: '代码检索', icon: 'search' as const },
      { to: '/history', label: '历史记录', icon: 'history' as const },
      { to: '/project', label: '项目管理', icon: 'folder' as const }
    ]
  },
  {
    label: '账户',
    items: [{ to: '/profile', label: '个人中心', icon: 'user' as const }]
  }
]

/** 顶栏标题：与路由 meta 对应，避免页面里重复写大标题 */
const PAGE_META: Record<string, { title: string; desc: string }> = {
  recommend: { title: 'AI 推荐 · 对话', desc: '描述需求让 AI 解析、召回并逐维排序推荐；右侧可随时就推荐结果与 AI 对话' },
  search: { title: '代码检索', desc: '按关键词搜索 GitHub / Gitee 开源仓库，查看详情与分支，一键收藏' },
  history: { title: '历史记录', desc: '按会话回看全部问答：Redis 存短期记忆，MySQL 存完整历史' },
  project: { title: '项目管理', desc: '管理收藏的开源项目，补充备注与关注状态' },
  profile: { title: '个人中心', desc: '查看登录账号信息与令牌状态' }
}

const page = computed(() => {
  const name = typeof route.name === 'string' ? route.name : ''
  return PAGE_META[name] ?? { title: '智能体平台', desc: 'AI Agent Platform' }
})

const avatarText = computed(() => (userStore.displayName || 'U').slice(0, 1).toUpperCase())

function go(path: string) {
  menuOpen.value = false
  void router.push(path)
}

function handleLogout() {
  menuOpen.value = false
  userStore.logout()
  void router.replace({ name: 'login' })
}

/** 点击面板外部时收起下拉菜单 */
function onDocumentClick(event: MouseEvent) {
  if (!menuOpen.value) {
    return
  }
  const target = event.target as Node | null
  if (chipRef.value && target && !chipRef.value.contains(target)) {
    menuOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  // 刷新页面后令牌仍在 localStorage，但用户信息只在内存中，这里补拉一次
  if (userStore.isLoggedIn && !userStore.userInfo) {
    void userStore.fetchCurrentUser().catch(() => undefined)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
})
</script>