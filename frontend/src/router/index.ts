import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

// 路由表：按 auth / recommend / search / project / history / user 六个业务域划分
// 「智能对话」不再是独立页面，而是 AI 推荐页右侧的对话面板（见 components/ChatPanel.vue）
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', guestOnly: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: '注册', guestOnly: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/BasicLayout.vue'),
    children: [
      { path: '', redirect: '/recommend' },
      {
        path: 'recommend',
        name: 'recommend',
        component: () => import('@/views/recommend/RecommendView.vue'),
        meta: { title: 'AI 推荐', requiresAuth: true }
      },
      {
        path: 'search',
        name: 'search',
        component: () => import('@/views/search/SearchView.vue'),
        meta: { title: '代码检索', requiresAuth: true }
      },
      {
        path: 'repo/:owner/:repo',
        name: 'repo-detail',
        component: () => import('@/views/repo/RepositoryDetailView.vue'),
        meta: { title: '仓库详情', requiresAuth: true }
      },
      {
        path: 'history',
        name: 'history',
        component: () => import('@/views/history/HistoryView.vue'),
        meta: { title: '历史记录', requiresAuth: true }
      },
      {
        path: 'project',
        name: 'project',
        component: () => import('@/views/project/ProjectView.vue'),
        meta: { title: '项目管理', requiresAuth: true }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/user/ProfileView.vue'),
        meta: { title: '个人中心', requiresAuth: true }
      },
      {
        path: 'wallet',
        name: 'wallet',
        component: () => import('@/views/user/WalletView.vue'),
        meta: { title: '额度中心', requiresAuth: true }
      },
      {
        path: 'model-settings',
        name: 'model-settings',
        component: () => import('@/views/user/ModelSettingsView.vue'),
        meta: { title: '模型设置', requiresAuth: true }
      },
      {
        path: 'admin/recharge',
        name: 'admin-recharge',
        component: () => import('@/views/admin/RechargeView.vue'),
        meta: { title: '管理员充值', requiresAuth: true, requiresAdmin: true }
      },
      // 旧地址兼容：智能对话已经并入 AI 推荐页，老书签与历史链接统一重定向过去
      { path: 'chat', redirect: '/recommend' }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/recommend' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 刷新页面后内存中的用户信息为空，先补拉一次再做角色判断，避免管理员被误拦
  if (to.meta.requiresAuth && userStore.isLoggedIn && !userStore.userInfo) {
    try {
      await userStore.fetchCurrentUser()
    } catch {
      // 令牌失效由请求拦截器统一跳转登录；这里继续按未登录信息处理后续路由
    }
  }
  if (to.meta.requiresAdmin && userStore.userInfo?.role !== 'ADMIN') {
    return { name: 'recommend' }
  }
  if (to.meta.guestOnly && userStore.isLoggedIn) {
    return { name: 'recommend' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title as string} - 智能体平台` : '智能体平台'
})

export default router
