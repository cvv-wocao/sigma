<template>
  <div class="page">
    <section class="card card-pad">
      <div class="page-head">
        <div>
          <h2>开源仓库检索</h2>
          <p class="subtitle">按关键词检索开源仓库，点击任意结果进入详情页查看分支、协议与 README 摘要</p>
        </div>
        <div class="page-head-actions">
          <span class="tag tag-brand">
            <AppIcon name="shield" :size="13" />
            GitHub 主检索 · Gitee 兜底
          </span>
        </div>
      </div>

      <form class="toolbar mt-16" @submit.prevent="handleSearch">
        <div class="input-icon grow">
          <span class="icon-slot"><AppIcon name="search" :size="16" /></span>
          <input
            v-model.trim="keyword"
            class="input"
            type="text"
            placeholder="搜索关键词，例如 springboot、python"
          />
        </div>
        <select v-model.number="perPage" class="select" style="width: 112px">
          <option :value="5">5 条</option>
          <option :value="10">10 条</option>
          <option :value="20">20 条</option>
        </select>
        <button class="btn btn-primary" type="submit" :disabled="searching || !keyword">
          <AppIcon name="search" :size="15" />
          {{ searching ? '搜索中…' : '搜索' }}
        </button>
      </form>

      <div class="toolbar mt-12">
        <span class="foot-note">热门关键词</span>
        <button
          v-for="sample in samples"
          :key="sample"
          class="sample-chip"
          type="button"
          @click="useSample(sample)"
        >
          {{ sample }}
        </button>
      </div>

      <div v-if="errorMessage" class="alert alert-error mt-16">
        <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
        <span>{{ errorMessage }}</span>
      </div>
      <div v-if="successMessage" class="alert alert-success mt-16">
        <span class="icon-slot"><AppIcon name="check" :size="16" /></span>
        <span>{{ successMessage }}</span>
      </div>

      <div class="divider"></div>

      <form class="toolbar" @submit.prevent="handleDirectLookup">
        <div class="input-icon grow">
          <span class="icon-slot"><AppIcon name="link" :size="16" /></span>
          <input
            v-model.trim="fullName"
            class="input"
            type="text"
            placeholder="直接查仓库：owner/repo，例如 macrozheng/mall"
          />
        </div>
        <button class="btn" type="submit" :disabled="!fullName">
          <AppIcon name="external" :size="15" />
          查详情
        </button>
      </form>
    </section>

    <section class="card card-pad">
      <div class="card-head">
        <h3>搜索结果</h3>
        <span class="tag">{{ repositories.length }} 个仓库</span>
        <div class="page-head-actions">
          <button class="btn btn-sm" type="button" :disabled="searching || !keyword" @click="handleSearch">
            <AppIcon name="refresh" :size="14" :class="{ spin: searching }" />
            重新搜索
          </button>
          <button
            class="btn btn-sm"
            type="button"
            :disabled="searching || (!keyword && repositories.length === 0)"
            @click="searchStore.clear()"
          >
            <AppIcon name="trash" :size="14" />
            清空
          </button>
        </div>
      </div>

      <div v-if="searching && repositories.length === 0" class="loading-line">
        <AppIcon name="refresh" :size="15" class="spin" />
        正在检索…
      </div>

      <div v-else-if="searched && repositories.length === 0" class="empty">
        <span class="empty-icon"><AppIcon name="search" :size="24" /></span>
        <span class="empty-title">没有搜索到相关仓库</span>
        <span class="empty-desc">换个关键词再试试，或用上面的输入框直接查询指定仓库。</span>
      </div>

      <div v-else class="repo-grid">
        <article
          v-for="repository in repositories"
          :key="repository.fullName"
          class="repo-card"
          role="link"
          tabindex="0"
          @click="openDetail(repository)"
          @keydown.enter="openDetail(repository)"
        >
          <div class="repo-card-top">
            <span class="repo-avatar">{{ (repository.name || 'R').slice(0, 1).toUpperCase() }}</span>
            <div class="repo-name">{{ repository.fullName }}</div>
            <span class="tag tag-star">
              <AppIcon name="star" :size="12" />
              {{ repository.stars ?? 0 }}
            </span>
          </div>

          <p class="repo-desc">{{ repository.description || '该作者未填写简介' }}</p>

          <div class="repo-foot">
            <span v-if="repository.source" class="tag tag-mono">{{ repository.source }}</span>
            <span v-if="repository.language" class="stat">
              <AppIcon name="code" :size="13" />
              {{ repository.language }}
            </span>
            <span class="stat">
              <AppIcon name="fork" :size="13" />
              Fork {{ repository.forks ?? 0 }}
            </span>
            <span class="stat">
              <AppIcon name="branch" :size="13" />
              {{ repository.defaultBranch || '未知分支' }}
            </span>
            <button
              class="btn btn-soft btn-sm"
              type="button"
              :disabled="savingFullName === repository.fullName"
              @click.stop="handleFavorite(repository)"
            >
              <AppIcon name="star" :size="13" />
              {{ savingFullName === repository.fullName ? '收藏中…' : '收藏' }}
            </button>
            <span class="repo-open">
              查看详情
              <AppIcon name="arrowRight" :size="13" />
            </span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { createProject } from '@/api/project'
import { searchRepositories } from '@/api/search'
import { useSearchStore } from '@/stores/search'
import AppIcon from '@/components/AppIcon.vue'
import type { GiteeRepository } from '@/types/search'
import { rememberRepository } from '@/utils/repoCache'

const samples = ['springboot', 'java', 'mall', 'python']

const router = useRouter()

/** 关键词与结果放在 Store 里（并落 sessionStorage）：切页回来不用重新搜一遍 */
const searchStore = useSearchStore()
const { keyword, perPage, repositories, searched } = storeToRefs(searchStore)

const searching = ref(false)

const fullName = ref('')

const errorMessage = ref('')
const successMessage = ref('')
/** 正在收藏的仓库全名，用于按钮禁用与文案 */
const savingFullName = ref('')

async function handleSearch() {
  if (!keyword.value) {
    return
  }
  errorMessage.value = ''
  searching.value = true
  try {
    const list = await searchRepositories({ keyword: keyword.value, page: 1, perPage: perPage.value })
    searchStore.applyResult(list)
  } catch (error) {
    searchStore.applyResult([])
    errorMessage.value = error instanceof Error ? error.message : '搜索失败，请稍后重试'
  } finally {
    searching.value = false
  }
}

/** 打开仓库详情页：卡片整体可点，避免把详情塞在当前页底部让用户以为「点了没反应」 */
function openDetail(repository: GiteeRepository) {
  const [owner, repo] = splitFullName(repository.fullName)
  if (!owner || !repo) {
    errorMessage.value = '仓库全名格式不正确，无法打开详情'
    return
  }
  // 先把列表里已有的仓库信息放进缓存，详情页可以立刻渲染，不必等外部接口回查
  rememberRepository(repository)
  void router.push({ name: 'repo-detail', params: { owner, repo } })
}

/** 收藏当前仓库：把检索结果里的客观信息作为快照提交给 project-service */
async function handleFavorite(repository: GiteeRepository) {
  const [owner, repo] = splitFullName(repository.fullName)
  if (!owner || !repo) {
    errorMessage.value = '仓库全名格式不正确，无法收藏'
    return
  }
  errorMessage.value = ''
  successMessage.value = ''
  savingFullName.value = repository.fullName
  try {
    await createProject({
      repoOwner: owner,
      repoName: repo,
      description: repository.description,
      language: repository.language,
      stars: repository.stars,
      forks: repository.forks,
      defaultBranch: repository.defaultBranch,
      htmlUrl: repository.htmlUrl
    })
    successMessage.value = `已收藏 ${repository.fullName}，可在「项目管理」中查看`
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '收藏失败，请稍后重试'
  } finally {
    savingFullName.value = ''
  }
}

function useSample(sample: string) {
  keyword.value = sample
  void handleSearch()
}

function handleDirectLookup() {
  const [owner, repo] = splitFullName(fullName.value)
  if (!owner || !repo) {
    errorMessage.value = '请输入 owner/repo 格式，例如 macrozheng/mall'
    return
  }
  void router.push({ name: 'repo-detail', params: { owner, repo } })
}

function splitFullName(value: string): [string, string] {
  const parts = value.split('/').filter((item) => item.trim().length > 0)
  return parts.length === 2 ? [parts[0]!.trim(), parts[1]!.trim()] : ['', '']
}

onMounted(() => {
  // 只有本次会话还没有任何检索结果时才做默认检索，
  // 否则每次从别的页面切回来都会重新打一次外部接口（还容易触发限流）
  if (searchStore.searched) {
    return
  }
  keyword.value = samples[0]!
  void handleSearch()
})
</script>