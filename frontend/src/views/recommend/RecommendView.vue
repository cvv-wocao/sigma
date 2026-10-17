<template>
  <div class="rec-page">
    <div class="rec-main">
      <section class="card card-pad">
        <div class="page-head">
          <div>
            <h2>AI 项目推荐</h2>
            <p class="subtitle">用自己的话描述需求，AI 先拆出技术栈与功能点，再按维度召回候选、深读 README，最后逐项核对排序</p>
          </div>
          <div class="page-head-actions">
            <span class="tag tag-brand">
              <AppIcon name="sparkles" :size="13" />
              需求解析 → 限定符召回 → README 深读 → 逐维核对
            </span>
          </div>
        </div>

        <form class="mt-16" @submit.prevent="handleRecommend">
          <textarea
            v-model.trim="draft"
            class="textarea"
            rows="4"
            placeholder="例如：我想找一个能快速搭后台管理系统的 Java 项目，要有权限管理和代码生成，最好活跃维护、协议宽松"
          ></textarea>

          <div class="toolbar mt-12">
            <select v-model.number="topN" class="select" style="width: 132px">
              <option :value="3">3 条推荐</option>
              <option :value="5">5 条推荐</option>
              <option :value="8">8 条推荐</option>
            </select>
            <div class="grow"></div>
            <button class="btn btn-primary" type="submit" :disabled="loading || !draft">
              <AppIcon name="send" :size="15" />
              {{ loading ? '分析中…' : '开始推荐' }}
            </button>
          </div>
        </form>

        <div class="toolbar mt-12">
          <span class="foot-note">试试这些需求</span>
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
        <div v-if="loading" class="loading-line mt-16">
          <AppIcon name="refresh" :size="15" class="spin" />
          正在解析需求、召回候选并深读项目 README，通常需要 30-90 秒…
        </div>
      </section>

      <section v-if="result" class="card card-pad">
        <div class="card-head">
          <h3>需求解析</h3>
          <span class="tag">{{ result.candidateCount }} 个候选仓库</span>
          <div class="page-head-actions">
            <span v-if="result.model" class="tag tag-mono">{{ result.model }}</span>
          </div>
        </div>

        <p class="rec-summary">{{ result.requirement.summary || '—' }}</p>
        <div class="rec-dims mt-12">
          <div v-if="result.requirement.projectType" class="rec-dim-row">
            <span class="rec-dim-label">项目形态</span>
            <span class="tag">{{ result.requirement.projectType }}</span>
          </div>
          <div v-if="result.requirement.techStack.length" class="rec-dim-row">
            <span class="rec-dim-label">技术栈</span>
            <span v-for="tech in result.requirement.techStack" :key="tech" class="tag tag-brand">{{ tech }}</span>
          </div>
          <div v-if="result.requirement.features.length" class="rec-dim-row">
            <span class="rec-dim-label">功能点</span>
            <span v-for="feature in result.requirement.features" :key="feature" class="tag">{{ feature }}</span>
          </div>
          <div v-if="result.requirement.scale" class="rec-dim-row">
            <span class="rec-dim-label">规模/场景</span>
            <span class="tag">{{ result.requirement.scale }}</span>
          </div>
          <div v-if="result.requirement.constraints.length" class="rec-dim-row">
            <span class="rec-dim-label">其他约束</span>
            <span v-for="constraint in result.requirement.constraints" :key="constraint" class="tag">{{ constraint }}</span>
          </div>
        </div>

        <div class="detail-grid mt-12">
          <div class="detail-stat">
            <div class="label">语言</div>
            <div class="value">{{ languagesText }}</div>
          </div>
          <div class="detail-stat">
            <div class="label">Star 下限</div>
            <div class="value">{{ result.requirement.minStars ?? '不限' }}</div>
          </div>
          <div class="detail-stat">
            <div class="label">活跃度</div>
            <div class="value">{{ activityText }}</div>
          </div>
          <div class="detail-stat">
            <div class="label">协议</div>
            <div class="value">{{ result.requirement.license || '不限' }}</div>
          </div>
        </div>

        <div class="branch-wrap mt-12">
          <span class="foot-note">检索关键词</span>
          <span v-for="keyword in result.requirement.keywords" :key="keyword" class="tag tag-brand">
            {{ keyword }}
          </span>
        </div>

        <p v-if="result.requirement.notes" class="note-line mt-12">{{ result.requirement.notes }}</p>
      </section>

      <section class="card card-pad">
        <div class="card-head">
          <h3>推荐结果</h3>
          <span class="tag">{{ result ? result.recommendations.length : 0 }} 个项目</span>
          <div class="page-head-actions">
            <button class="btn btn-sm" type="button" :disabled="loading || !draft" @click="handleRecommend">
              <AppIcon name="refresh" :size="14" :class="{ spin: loading }" />
              重新推荐
            </button>
            <button
              class="btn btn-sm"
              type="button"
              :disabled="loading || (!draft && !result)"
              @click="recommendStore.reset()"
            >
              <AppIcon name="trash" :size="14" />
              清空
            </button>
          </div>
        </div>

        <div v-if="!result && !loading" class="empty">
          <span class="empty-icon"><AppIcon name="sparkles" :size="24" /></span>
          <span class="empty-title">还没有推荐结果</span>
          <span class="empty-desc">在上面描述你的需求，AI 会先解析条件，再检索候选仓库并逐个打分排序。</span>
        </div>

        <div v-else-if="result && result.recommendations.length === 0 && !loading" class="empty">
          <span class="empty-icon"><AppIcon name="search" :size="24" /></span>
          <span class="empty-title">没有匹配到合适的仓库</span>
          <span class="empty-desc">{{ emptyReason }}</span>
        </div>

        <div v-else class="rec-grid">
          <article
            v-for="item in result?.recommendations ?? []"
            :key="item.repository.fullName"
            class="rec-card"
          >
            <div class="rec-head">
              <span class="repo-avatar">{{ (item.repository.name || 'R').slice(0, 1).toUpperCase() }}</span>
              <div class="rec-title">
                <div class="repo-name">{{ item.repository.fullName }}</div>
                <div class="rec-meta">
                  <span v-if="item.repository.language" class="stat">
                    <AppIcon name="code" :size="13" />
                    {{ item.repository.language }}
                  </span>
                  <span class="stat stat-star">
                    <AppIcon name="star" :size="13" />
                    {{ item.repository.stars ?? 0 }}
                  </span>
                  <span v-if="item.repository.license" class="stat">
                    <AppIcon name="shield" :size="13" />
                    {{ item.repository.license }}
                  </span>
                  <span v-if="item.repository.updatedAt" class="stat">
                    <AppIcon name="clock" :size="13" />
                    {{ formatDate(item.repository.updatedAt) }}
                  </span>
                  <span v-if="item.repository.source" class="tag tag-mono">{{ item.repository.source }}</span>
                </div>
              </div>
              <span class="rec-score" :class="{ 'rec-score-none': item.score === null || item.score === undefined }">
                {{ item.score ?? '—' }}
              </span>
            </div>

            <p class="rec-reason">{{ item.reason }}</p>

            <ul v-if="item.matches.length" class="rec-match">
              <li v-for="point in item.matches" :key="point.dimension + point.verdict">
                <span class="rec-match-dim">{{ point.dimension }}</span>
                <span class="rec-match-verdict" :class="verdictClass(point.verdict)">{{ point.verdict }}</span>
                <span class="rec-match-detail">{{ point.detail }}</span>
              </li>
            </ul>

            <ul v-if="item.highlights.length" class="rec-points">
              <li v-for="point in item.highlights" :key="point">
                <AppIcon name="check" :size="13" />
                <span>{{ point }}</span>
              </li>
            </ul>
            <ul v-if="item.risks.length" class="rec-points rec-points-risk">
              <li v-for="point in item.risks" :key="point">
                <AppIcon name="alert" :size="13" />
                <span>{{ point }}</span>
              </li>
            </ul>

            <div class="rec-foot">
              <a v-if="item.repository.htmlUrl" class="link-line" :href="item.repository.htmlUrl" target="_blank" rel="noreferrer">
                <AppIcon name="external" :size="14" />
                {{ item.repository.htmlUrl }}
              </a>
              <button class="btn btn-sm" type="button" @click="openDetail(item.repository)">
                <AppIcon name="arrowRight" :size="13" />
                查看详情
              </button>
              <button
                class="btn btn-soft btn-sm"
                type="button"
                :disabled="savingFullName === item.repository.fullName"
                @click="handleFavorite(item.repository)"
              >
                <AppIcon name="star" :size="13" />
                {{ savingFullName === item.repository.fullName ? '收藏中…' : '收藏' }}
              </button>
            </div>
          </article>
        </div>
      </section>
    </div>

    <aside class="rec-side">
      <ChatPanel :context="chatContext" />
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { createProject } from '@/api/project'
import { recommendProjects } from '@/api/recommend'
import AppIcon from '@/components/AppIcon.vue'
import ChatPanel from '@/components/ChatPanel.vue'
import type { GiteeRepository } from '@/types/search'
import { useRecommendStore } from '@/stores/recommend'
import { rememberRepository } from '@/utils/repoCache'

/** 示例需求：覆盖「技术栈明确」「领域明确」「前后端组合」三种典型写法 */
const samples = [
  '我想找一个能快速搭后台管理系统的 Java 项目，要有权限管理和代码生成',
  '想学 Python 爬虫，找一个活跃维护、文档齐全的爬虫框架',
  '需要一个支持多租户的 SaaS 后台模板，前端用 Vue，Star 要多一点'
]

/** 随对话下发的仓库条数：只带前几条，够多轮追问用，同时控制提示词长度 */
const CONTEXT_REPO_LIMIT = 5
/** 单条理由 / 风险在上下文里的截断长度 */
const CONTEXT_TEXT_LIMIT = 80

const router = useRouter()

/**
 * 需求草稿、条数与推荐结果都放在 Store 里（并落 sessionStorage）：
 * 切到别的页面再回来、甚至刷新页面，刚才的推荐结果都还在。
 */
const recommendStore = useRecommendStore()
const { draft, topN, result } = storeToRefs(recommendStore)

const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
/** 正在收藏的仓库全名，用于按钮禁用与文案 */
const savingFullName = ref('')

const languagesText = computed(() => result.value?.requirement.languages.join(' / ') || '不限')

const activityText = computed(() => {
  const days = result.value?.requirement.updatedWithinDays
  return days ? `最近 ${days} 天活跃` : '不限'
})

/**
 * 交给右侧对话的页面参考数据：把当前推荐结果压成一段纯文本。
 *
 * <p>后端会把它作为「额外的系统消息」拼进本轮提示词（见 AgentService），
 * 因此用户可以直接问「这几个哪个最适合我」这类只有看到结果才问得出来的问题。
 * 没有推荐结果时返回空串，对话退化成普通的仓库检索助手。</p>
 */
const chatContext = computed(() => {
  const value = result.value
  if (!value) {
    return ''
  }
  const requirement = value.requirement
  const lines: string[] = []

  const condition: string[] = []
  if (requirement.projectType) {
    condition.push(`项目形态：${requirement.projectType}`)
  }
  if (requirement.techStack.length) {
    condition.push(`技术栈：${requirement.techStack.join('、')}`)
  }
  if (requirement.features.length) {
    condition.push(`功能点：${requirement.features.join('、')}`)
  }
  if (requirement.scale) {
    condition.push(`规模/场景：${requirement.scale}`)
  }
  if (requirement.languages.length) {
    condition.push(`语言：${requirement.languages.join('、')}`)
  }
  if (requirement.minStars) {
    condition.push(`Star 下限：${requirement.minStars}`)
  }

  lines.push('【用户需求】')
  lines.push(requirement.summary || draft.value.trim() || '（未记录）')
  if (condition.length) {
    lines.push(condition.join('；'))
  }

  lines.push('')
  lines.push(`【推荐结果】按匹配度从高到低，共 ${value.recommendations.length} 条，来自 ${value.candidateCount} 个候选仓库`)
  value.recommendations.slice(0, CONTEXT_REPO_LIMIT).forEach((item, index) => {
    const repository = item.repository
    const meta = [
      repository.language ? `语言 ${repository.language}` : '',
      `Star ${repository.stars ?? 0}`,
      repository.license ? `协议 ${repository.license}` : '',
      item.score === null || item.score === undefined ? '' : `匹配度 ${item.score}`
    ].filter((text) => text.length > 0)
    lines.push(`${index + 1}. ${repository.fullName}（${meta.join('，')}）`)
    if (repository.htmlUrl) {
      lines.push(`   地址：${repository.htmlUrl}`)
    }
    if (item.reason) {
      lines.push(`   推荐理由：${clip(item.reason)}`)
    }
    if (item.risks.length) {
      lines.push(`   风险：${clip(item.risks.join('；'))}`)
    }
  })

  return lines.join('\n')
})

function clip(text: string): string {
  const single = text.replace(/\s+/g, ' ').trim()
  return single.length > CONTEXT_TEXT_LIMIT ? `${single.slice(0, CONTEXT_TEXT_LIMIT)}…` : single
}

/** 空结果时区分「没召回到」和「被过滤掉了」，便于用户调整描述 */
const emptyReason = computed(() => {
  if (!result.value) {
    return ''
  }
  if (result.value.candidateCount === 0) {
    return '一个候选都没召回到：换一种说法，或写明具体语言与功能点再试。'
  }
  return `召回到 ${result.value.candidateCount} 个仓库，但都被过滤条件挡掉了：可以放宽语言、Star 下限或活跃度要求。`
})

async function handleRecommend() {
  const message = draft.value.trim()
  if (!message) {
    return
  }
  errorMessage.value = ''
  successMessage.value = ''
  loading.value = true
  try {
    result.value = await recommendProjects({ message, topN: topN.value })
  } catch (error) {
    result.value = null
    errorMessage.value = error instanceof Error ? error.message : '推荐失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function useSample(sample: string) {
  draft.value = sample
  void handleRecommend()
}

/** 后端返回 ISO-8601，界面上只展示日期部分 */
function formatDate(value: string): string {
  return value.slice(0, 10)
}

/** 收藏当前推荐：把仓库信息作为快照提交给 project-service */
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

/** 判定文案映射到颜色：命中=绿 / 部分命中=橙 / 未命中=灰 */
function verdictClass(verdict: string): string {
  if (verdict.includes('未')) {
    return 'is-miss'
  }
  if (verdict.includes('部分')) {
    return 'is-partial'
  }
  return 'is-hit'
}

/** 打开仓库详情页，查看分支、协议与 README 摘要 */
function openDetail(repository: GiteeRepository) {
  const [owner, repo] = splitFullName(repository.fullName)
  if (!owner || !repo) {
    errorMessage.value = '仓库全名格式不正确，无法打开详情'
    return
  }
  rememberRepository(repository)
  void router.push({ name: 'repo-detail', params: { owner, repo } })
}

function splitFullName(value: string): [string, string] {
  const parts = value.split('/').filter((item) => item.trim().length > 0)
  return parts.length === 2 ? [parts[0]!.trim(), parts[1]!.trim()] : ['', '']
}
</script>