<template>
  <div class="page">
    <section class="card card-pad">
      <div class="card-head">
        <button class="btn btn-icon btn-sm" type="button" title="返回上一页" @click="goBack">
          <AppIcon name="arrowRight" :size="15" style="transform: rotate(180deg)" />
        </button>
        <span class="repo-avatar">{{ (detail?.name || repo || 'R').slice(0, 1).toUpperCase() }}</span>
        <div style="min-width: 0">
          <div class="detail-title">{{ detail?.fullName || routeFullName }}</div>
          <p class="subtitle" style="margin-top: 2px">
            {{ detail?.description || (loading ? '正在加载简介…' : '该作者未填写简介') }}
          </p>
        </div>
        <div class="page-head-actions">
          <a
            v-if="detail?.htmlUrl"
            class="btn btn-sm"
            :href="detail.htmlUrl"
            target="_blank"
            rel="noreferrer"
          >
            <AppIcon name="external" :size="14" />
            打开主页
          </a>
          <button
            class="btn btn-primary btn-sm"
            type="button"
            :disabled="saving || !detail"
            @click="handleFavorite"
          >
            <AppIcon name="star" :size="14" />
            {{ saving ? '收藏中…' : '收藏' }}
          </button>
        </div>
      </div>

      <div v-if="loading" class="loading-line mt-16">
        <AppIcon name="refresh" :size="15" class="spin" />
        正在加载仓库详情…
      </div>

      <div v-if="errorMessage" class="alert alert-error mt-16">
        <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
        <span>{{ errorMessage }}</span>
      </div>
      <div v-if="partialNotice" class="alert alert-info mt-16">
        <span class="icon-slot"><AppIcon name="info" :size="16" /></span>
        <span>{{ partialNotice }}</span>
      </div>
      <div v-if="successMessage" class="alert alert-success mt-16">
        <span class="icon-slot"><AppIcon name="check" :size="16" /></span>
        <span>{{ successMessage }}</span>
      </div>

      <template v-if="detail">
        <div class="detail-grid mt-16">
          <div class="detail-stat">
            <div class="label">主要语言</div>
            <div class="value">{{ detail.language || '未标注' }}</div>
          </div>
          <div class="detail-stat detail-stat-star">
            <div class="label">Star</div>
            <div class="value">{{ detail.stars ?? 0 }}</div>
          </div>
          <div class="detail-stat">
            <div class="label">Fork</div>
            <div class="value">{{ detail.forks ?? 0 }}</div>
          </div>
          <div class="detail-stat">
            <div class="label">默认分支</div>
            <div class="value">{{ detail.defaultBranch || '未知' }}</div>
          </div>
          <div class="detail-stat">
            <div class="label">开源协议</div>
            <div class="value">{{ detail.license || '未标注' }}</div>
          </div>
          <div class="detail-stat">
            <div class="label">最近推送</div>
            <div class="value">{{ detail.updatedAt ? detail.updatedAt.slice(0, 10) : '未知' }}</div>
          </div>
        </div>

        <div class="branch-wrap mt-16">
          <span class="tag">来源 {{ detail.source || '未知' }}</span>
          <span v-if="detail.updatedAt" class="tag tag-mono">{{ detail.updatedAt.slice(0, 10) }}</span>
        </div>

        <div v-if="topics.length" class="branch-wrap mt-12">
          <span class="foot-note">主题</span>
          <span v-for="topic in topics" :key="topic" class="tag tag-brand">{{ topic }}</span>
        </div>

        <div class="branch-wrap mt-12">
          <span class="foot-note">分支</span>
          <span v-if="branches.length === 0" class="tag">暂无数据</span>
          <span
            v-for="branch in visibleBranches"
            :key="branch.name"
            class="tag tag-mono"
          >
            {{ branch.name }}
          </span>
          <span v-if="hiddenBranchCount > 0" class="tag">还有 {{ hiddenBranchCount }} 个…</span>
        </div>

        <div v-if="readme" class="readme-block mt-16">
          <div class="readme-head">
            <span class="foot-note">README 摘要</span>
            <span class="tag tag-mono">{{ readme.length }} 字符</span>
            <button
              v-if="readmeCollapsible"
              class="btn btn-soft btn-sm"
              type="button"
              @click="readmeExpanded = !readmeExpanded"
            >
              {{ readmeExpanded ? '收起' : '展开全部' }}
            </button>
          </div>
          <pre class="readme-body">{{ readmePreview }}</pre>
        </div>
      </template>

      <div v-else-if="!loading" class="empty mt-16">
        <span class="empty-icon"><AppIcon name="search" :size="24" /></span>
        <span class="empty-title">没有查到该仓库</span>
        <span class="empty-desc">确认 owner/repo 是否正确，或回到代码检索页重新搜索。</span>
        <router-link class="btn btn-soft btn-sm mt-12" to="/search">回到代码检索</router-link>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createProject } from '@/api/project'
import { getReadme, getRepository, listBranches } from '@/api/search'
import AppIcon from '@/components/AppIcon.vue'
import type { GiteeBranch, GiteeRepository } from '@/types/search'
import { recallRepository, rememberRepository } from '@/utils/repoCache'

/** 分支与 README 最多展示的数量，避免大仓库把页面撑得找不着重点 */
const BRANCH_LIMIT = 12
const README_PREVIEW = 1200

const route = useRoute()
const router = useRouter()

const detail = ref<GiteeRepository | null>(null)
const branches = ref<GiteeBranch[]>([])
const readme = ref('')
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const readmeExpanded = ref(false)
/** 分支 / README 等附加信息取不到时的提示文案 */
const partialNotice = ref('')

const owner = computed(() => String(route.params.owner ?? ''))
const repo = computed(() => String(route.params.repo ?? ''))
const routeFullName = computed(() => `${owner.value}/${repo.value}`)
const topics = computed(() => detail.value?.topics ?? [])
const visibleBranches = computed(() => branches.value.slice(0, BRANCH_LIMIT))
const hiddenBranchCount = computed(() => Math.max(branches.value.length - BRANCH_LIMIT, 0))
const readmeCollapsible = computed(() => readme.value.length > README_PREVIEW)
const readmePreview = computed(() => {
  if (readmeExpanded.value || !readmeCollapsible.value) {
    return readme.value
  }
  return `${readme.value.slice(0, README_PREVIEW)}…`
})

/**
 * 详情是主体，分支与 README 是附加信息：
 * 用 allSettled 分别接收，任何一个附加请求失败都不会让整页变空白。
 */
async function load() {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  branches.value = []
  readme.value = ''
  readmeExpanded.value = false
  partialNotice.value = ''
  // 先用手里的检索结果快照渲染，外部接口慢或限流时也不会看到空白页
  detail.value = recallRepository(routeFullName.value)

  const [detailResult, branchResult, readmeResult] = await Promise.allSettled([
    getRepository(owner.value, repo.value),
    listBranches(owner.value, repo.value),
    getReadme(owner.value, repo.value)
  ])

  const missing: string[] = []
  if (detailResult.status === 'fulfilled') {
    detail.value = detailResult.value
    // 回查到的详情更完整，顺手更新快照缓存
    rememberRepository(detailResult.value)
  } else if (detail.value) {
    // 有检索结果快照兜底：页面照常展示，只提示「实时详情没取到」
    missing.push('实时详情')
  } else {
    errorMessage.value =
      detailResult.reason instanceof Error ? detailResult.reason.message : '查询仓库详情失败'
  }
  if (branchResult.status === 'fulfilled') {
    branches.value = branchResult.value ?? []
  } else if (detail.value) {
    missing.push('分支')
  }
  if (readmeResult.status === 'fulfilled') {
    readme.value = readmeResult.value?.content ?? ''
  } else if (detail.value) {
    missing.push('README')
  }

  // 附加信息取不到时明确说明原因（多半是外部平台限流），而不是静默留空让人以为功能坏了
  if (missing.length > 0 && detail.value) {
    partialNotice.value = `${missing.join(' / ')}暂时取不到，可能是外部平台限流（未配置 GITHUB_TOKEN 时每小时额度很低）；上面的仓库信息来自检索结果快照，稍后刷新可获得完整数据。`
  }
  loading.value = false
}

async function handleFavorite() {
  const current = detail.value
  if (!current) {
    return
  }
  errorMessage.value = ''
  successMessage.value = ''
  saving.value = true
  try {
    await createProject({
      repoOwner: owner.value,
      repoName: repo.value,
      description: current.description,
      language: current.language,
      stars: current.stars,
      forks: current.forks,
      defaultBranch: current.defaultBranch,
      htmlUrl: current.htmlUrl
    })
    successMessage.value = `已收藏 ${current.fullName}，可在「项目管理」中查看`
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '收藏失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  void router.push('/search')
}

// 同一个组件实例在 /repo/a/b -> /repo/c/d 之间会被复用，必须监听路由参数重新加载
watch(
  () => [owner.value, repo.value].join('/'),
  () => {
    void load()
  },
  { immediate: true }
)
</script>