<template>
  <div class="page">
    <section class="card card-pad">
      <div class="page-head">
        <div>
          <h2>项目管理</h2>
          <p class="subtitle">
            收藏感兴趣的开源项目，补充备注与状态。仓库信息取自收藏时刻的检索结果，不会自动跟随上游仓库变化。
          </p>
        </div>
        <div class="page-head-actions">
          <span class="tag tag-brand">
            <AppIcon name="database" :size="13" />
            共 {{ projects.length }} 个项目
          </span>
          <button class="btn btn-sm" type="button" :disabled="loading" @click="loadProjects">
            <AppIcon name="refresh" :size="14" :class="{ spin: loading }" />
            刷新
          </button>
        </div>
      </div>

      <form class="toolbar mt-16" @submit.prevent="loadProjects">
        <div class="input-icon grow">
          <span class="icon-slot"><AppIcon name="search" :size="16" /></span>
          <input
            v-model.trim="keyword"
            class="input"
            type="text"
            placeholder="按仓库全名或简介筛选，例如 mall"
          />
        </div>
        <select v-model="status" class="select" style="width: 132px">
          <option :value="''">全部状态</option>
          <option :value="1">关注中</option>
          <option :value="0">已归档</option>
        </select>
        <button class="btn btn-primary" type="submit" :disabled="loading">
          <AppIcon name="search" :size="15" />
          {{ loading ? '加载中…' : '筛选' }}
        </button>
      </form>

      <div v-if="errorMessage" class="alert alert-error mt-16">
        <span class="icon-slot"><AppIcon name="alert" :size="16" /></span>
        <span>{{ errorMessage }}</span>
      </div>
      <div v-if="successMessage" class="alert alert-success mt-16">
        <span class="icon-slot"><AppIcon name="check" :size="16" /></span>
        <span>{{ successMessage }}</span>
      </div>

      <div class="divider"></div>

      <form class="toolbar" @submit.prevent="handleCreate">
        <span class="foot-note">
          <AppIcon name="plus" :size="14" />
          手动添加
        </span>
        <input
          v-model.trim="draftOwner"
          class="input"
          style="width: 200px"
          type="text"
          placeholder="owner，例如 macrozheng"
        />
        <input
          v-model.trim="draftRepo"
          class="input"
          style="width: 200px"
          type="text"
          placeholder="repo，例如 mall"
        />
        <div class="input-icon grow">
          <span class="icon-slot"><AppIcon name="edit" :size="16" /></span>
          <input v-model.trim="draftNote" class="input" type="text" placeholder="备注（可选）" />
        </div>
        <button class="btn btn-primary" type="submit" :disabled="creating">
          <AppIcon name="plus" :size="15" />
          {{ creating ? '添加中…' : '添加' }}
        </button>
      </form>
    </section>

    <section class="card card-pad">
      <div v-if="loading && projects.length === 0" class="loading-line">
        <AppIcon name="refresh" :size="15" class="spin" />
        正在加载项目…
      </div>

      <div v-else-if="projects.length === 0" class="empty">
        <span class="empty-icon"><AppIcon name="folder" :size="24" /></span>
        <span class="empty-title">还没有收藏的项目</span>
        <span class="empty-desc">
          在「代码检索」里点击收藏，或用上面的表单手动添加 owner/repo。
        </span>
      </div>

      <div v-else class="proj-grid">
        <article v-for="project in projects" :key="project.id" class="proj-card">
          <div class="proj-top">
            <span class="repo-avatar">{{ (project.repoName || 'P').slice(0, 1).toUpperCase() }}</span>
            <div style="min-width: 0">
              <div class="proj-name">{{ project.fullName }}</div>
              <div class="foot-note" style="margin-top: 2px">
                更新于 {{ project.updateTime || '-' }}
              </div>
            </div>
            <span class="badge" :class="project.status === 1 ? 'badge-active' : 'badge-archived'">
              {{ project.status === 1 ? '关注中' : '已归档' }}
            </span>
          </div>

          <p class="repo-desc">{{ project.description || '该作者未填写简介' }}</p>

          <div class="proj-meta">
            <span v-if="project.language" class="stat">
              <AppIcon name="code" :size="13" />
              {{ project.language }}
            </span>
            <span class="stat stat-star">
              <AppIcon name="star" :size="13" />
              Star {{ project.stars ?? 0 }}
            </span>
            <span class="stat">
              <AppIcon name="fork" :size="13" />
              Fork {{ project.forks ?? 0 }}
            </span>
            <span v-if="project.defaultBranch" class="stat">
              <AppIcon name="branch" :size="13" />
              {{ project.defaultBranch }}
            </span>
          </div>

          <p v-if="project.note" class="proj-note">{{ project.note }}</p>

          <a
            v-if="project.htmlUrl"
            class="link-line"
            :href="project.htmlUrl"
            target="_blank"
            rel="noreferrer"
          >
            <AppIcon name="external" :size="14" />
            {{ project.htmlUrl }}
          </a>

          <div v-if="editingId === project.id" class="proj-edit">
            <textarea
              v-model.trim="editNote"
              class="textarea"
              rows="2"
              placeholder="备注：用途、接入计划等"
            ></textarea>
            <div class="toolbar">
              <label class="checkbox">
                <input v-model="editArchived" type="checkbox" />
                归档（不参与「关注中」筛选）
              </label>
              <div class="page-head-actions">
                <button class="btn btn-primary btn-sm" type="button" :disabled="saving" @click="handleSave(project)">
                  保存
                </button>
                <button class="btn btn-sm" type="button" @click="cancelEdit">取消</button>
              </div>
            </div>
          </div>

          <div v-else class="proj-actions">
            <button class="btn btn-sm" type="button" @click="startEdit(project)">
              <AppIcon name="edit" :size="14" />
              编辑
            </button>
            <button
              class="btn btn-sm"
              type="button"
              :disabled="downloadingId === project.id"
              @click="handleDownload(project)"
            >
              <AppIcon name="download" :size="14" />
              {{ downloadingId === project.id ? '下载中…' : '下载 ZIP' }}
            </button>
            <button class="btn btn-sm" type="button" :disabled="saving" @click="handleToggleStatus(project)">
              <AppIcon :name="project.status === 1 ? 'archive' : 'refresh'" :size="14" />
              {{ project.status === 1 ? '归档' : '恢复关注' }}
            </button>
            <button class="btn btn-danger btn-sm" type="button" :disabled="saving" @click="handleDelete(project)">
              <AppIcon name="trash" :size="14" />
              取消收藏
            </button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createProject, deleteProject, listProjects, updateProject } from '@/api/project'
import { downloadRepositoryArchive } from '@/api/search'
import AppIcon from '@/components/AppIcon.vue'
import type { Project } from '@/types/project'

const projects = ref<Project[]>([])
const keyword = ref('')
const status = ref<number | ''>('')

const loading = ref(false)
const saving = ref(false)
const creating = ref(false)
const downloadingId = ref<number | null>(null)
const errorMessage = ref('')
const successMessage = ref('')

const draftOwner = ref('')
const draftRepo = ref('')
const draftNote = ref('')

const editingId = ref<number | null>(null)
const editNote = ref('')
const editArchived = ref(false)

async function loadProjects() {
  errorMessage.value = ''
  loading.value = true
  try {
    projects.value = await listProjects({
      keyword: keyword.value || undefined,
      status: status.value === '' ? undefined : status.value
    })
  } catch (error) {
    projects.value = []
    errorMessage.value = error instanceof Error ? error.message : '加载项目列表失败'
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!draftOwner.value || !draftRepo.value) {
    errorMessage.value = '请填写 owner 与 repo'
    return
  }
  errorMessage.value = ''
  successMessage.value = ''
  creating.value = true
  try {
    const created = await createProject({
      repoOwner: draftOwner.value,
      repoName: draftRepo.value,
      note: draftNote.value || undefined
    })
    successMessage.value = `已添加 ${created.fullName}`
    draftOwner.value = ''
    draftRepo.value = ''
    draftNote.value = ''
    await loadProjects()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '添加失败，请稍后重试'
  } finally {
    creating.value = false
  }
}

function startEdit(project: Project) {
  editingId.value = project.id
  editNote.value = project.note ?? ''
  editArchived.value = project.status !== 1
}

function cancelEdit() {
  editingId.value = null
  editNote.value = ''
  editArchived.value = false
}

async function handleSave(project: Project) {
  errorMessage.value = ''
  successMessage.value = ''
  saving.value = true
  try {
    await updateProject(project.id, {
      note: editNote.value,
      status: editArchived.value ? 0 : 1
    })
    successMessage.value = `已更新 ${project.fullName}`
    cancelEdit()
    await loadProjects()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

async function handleToggleStatus(project: Project) {
  errorMessage.value = ''
  successMessage.value = ''
  saving.value = true
  try {
    await updateProject(project.id, { status: project.status === 1 ? 0 : 1 })
    await loadProjects()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '操作失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

/** 项目管理页也支持直接下载；来源优先根据收藏时保存的 htmlUrl 判断 */
async function handleDownload(project: Project) {
  errorMessage.value = ''
  successMessage.value = ''
  downloadingId.value = project.id
  try {
    await downloadRepositoryArchive({
      owner: project.repoOwner,
      repo: project.repoName,
      branch: project.defaultBranch,
      source: sourceFromUrl(project.htmlUrl)
    })
    successMessage.value = `已开始下载 ${project.fullName} 的 ZIP 包`
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '下载失败，请稍后重试'
  } finally {
    downloadingId.value = null
  }
}

function sourceFromUrl(url?: string | null): string | undefined {
  if (!url) {
    return undefined
  }
  try {
    const host = new URL(url).hostname.toLowerCase()
    if (host === 'github.com' || host.endsWith('.github.com')) {
      return 'github'
    }
    if (host === 'gitee.com' || host.endsWith('.gitee.com')) {
      return 'gitee'
    }
  } catch {
    return undefined
  }
  return undefined
}

async function handleDelete(project: Project) {
  if (!window.confirm(`确定取消收藏 ${project.fullName}？`)) {
    return
  }
  errorMessage.value = ''
  successMessage.value = ''
  saving.value = true
  try {
    await deleteProject(project.id)
    successMessage.value = `已取消收藏 ${project.fullName}`
    if (editingId.value === project.id) {
      cancelEdit()
    }
    await loadProjects()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '取消收藏失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

onMounted(loadProjects)
</script>
