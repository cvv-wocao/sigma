import { defineStore } from 'pinia'
import { getCurrentUser, login as loginApi } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/api/request'
import { useChatStore } from '@/stores/chat'
import { useRecommendStore } from '@/stores/recommend'
import { useSearchStore } from '@/stores/search'
import type { LoginParams, UserInfo } from '@/types/user'

/**
 * 登录态管理：令牌保存在 localStorage，用户信息缓存在内存中。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    userInfo: null as UserInfo | null
  }),

  getters: {
    isLoggedIn: (state): boolean => !!state.token,
    displayName: (state): string => state.userInfo?.nickname || state.userInfo?.username || ''
  },

  actions: {
    /** 登录：保存令牌与用户信息 */
    async login(params: LoginParams) {
      const result = await loginApi(params)
      this.token = result.token
      this.userInfo = result.user
      setToken(result.token)
      return result
    },

    /** 拉取当前用户信息（刷新页面后恢复用户信息） */
    async fetchCurrentUser() {
      if (!this.token) {
        return null
      }
      this.userInfo = await getCurrentUser()
      return this.userInfo
    },

    /** 退出登录：服务端无会话，前端清除令牌即可 */
    logout() {
      this.token = ''
      this.userInfo = null
      clearToken()
      // 检索结果、推荐结果与对话记录缓存在 sessionStorage，属于「上一个人的数据」，换账号时必须清掉
      useSearchStore().clear()
      useRecommendStore().reset()
      useChatStore().reset()
    }
  }
})