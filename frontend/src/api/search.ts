import request, { get } from './request'
import type {
  GiteeBranch,
  GiteeRepository,
  RepositoryFileContent,
  RepositoryFileListing,
  RepositoryReadme,
  RepositoryRelease,
  RepositorySearchParams
} from '@/types/search'

export interface RepositoryArchiveParams {
  owner: string
  repo: string
  branch?: string | null
  source?: string | null
}

/** 关键词检索开源仓库（GitHub 主来源，Gitee 兜底） */
export function searchRepositories(params: RepositorySearchParams): Promise<GiteeRepository[]> {
  return get<GiteeRepository[]>('/search/repositories', { params })
}

/** 仓库详情 */
export function getRepository(owner: string, repo: string): Promise<GiteeRepository> {
  return get<GiteeRepository>(`/search/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`)
}

/** 仓库分支列表 */
export function listBranches(owner: string, repo: string): Promise<GiteeBranch[]> {
  return get<GiteeBranch[]>(`/search/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/branches`)
}

/** 仓库 README 摘要（用于详情页展示项目说明） */
export function getReadme(owner: string, repo: string): Promise<RepositoryReadme> {
  return get<RepositoryReadme>(`/search/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/readme`)
}

/** 仓库 Release 与安装包附件 */
export function listReleases(
  owner: string,
  repo: string,
  limit = 5,
  source?: string | null
): Promise<RepositoryRelease[]> {
  return get<RepositoryRelease[]>(
    `/search/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/releases`,
    { params: { limit, source: source || undefined } }
  )
}

/** 查看仓库目录 */
export function listRepositoryFiles(
  owner: string,
  repo: string,
  options: { path?: string; branch?: string | null; source?: string | null } = {}
): Promise<RepositoryFileListing> {
  return get<RepositoryFileListing>(
    `/search/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/contents`,
    {
      params: {
        path: options.path || undefined,
        branch: options.branch || undefined,
        source: options.source || undefined
      }
    }
  )
}

/** 读取仓库内单个文本文件 */
export function readRepositoryFile(
  owner: string,
  repo: string,
  path: string,
  options: { branch?: string | null; source?: string | null } = {}
): Promise<RepositoryFileContent> {
  return get<RepositoryFileContent>(
    `/search/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/file`,
    {
      params: {
        path,
        branch: options.branch || undefined,
        source: options.source || undefined
      }
    }
  )
}

/**
 * 下载仓库默认分支的 ZIP 归档。
 *
 * <p>下载接口受网关 JWT 保护，普通 a 标签无法携带 Authorization，因此用 axios
 * 先拿到 Blob 再触发浏览器保存。归档通常较大，这里单独关闭 15 秒超时。</p>
 */
export async function downloadRepositoryArchive(params: RepositoryArchiveParams): Promise<void> {
  const { owner, repo, branch, source } = params
  try {
    const response = await request.get<Blob>(
      `/search/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/archive`,
      {
        params: {
          branch: branch || undefined,
          source: source || undefined
        },
        responseType: 'blob',
        timeout: 0
      }
    )

    const contentType = String(response.headers['content-type'] ?? '')
    if (contentType.includes('application/json')) {
      const message = await readBlobMessage(response.data)
      throw new Error(message || '下载失败，请稍后重试')
    }

    const fileName =
      fileNameFromDisposition(String(response.headers['content-disposition'] ?? '')) ||
      `${owner}-${repo}.zip`
    saveBlob(response.data, fileName)
  } catch (error) {
    const responseData = (error as { response?: { data?: unknown } }).response?.data
    if (responseData instanceof Blob) {
      const message = await readBlobMessage(responseData)
      if (message) {
        throw new Error(message)
      }
    }
    throw error
  }
}

async function readBlobMessage(blob: Blob): Promise<string | null> {
  try {
    const parsed = JSON.parse(await blob.text()) as { message?: unknown }
    return typeof parsed.message === 'string' ? parsed.message : null
  } catch {
    return null
  }
}

function fileNameFromDisposition(disposition: string): string | null {
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1]
  if (encoded) {
    try {
      return decodeURIComponent(encoded.trim().replace(/^"|"$/g, ''))
    } catch {
      return null
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1]
  return plain ? plain.trim() : null
}

function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
