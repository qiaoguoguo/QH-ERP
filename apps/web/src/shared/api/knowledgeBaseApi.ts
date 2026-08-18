import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type KnowledgeId = string | number
export type KnowledgeStatus = 'ENABLED' | 'DISABLED'
export type KnowledgeType = 'PAGE' | 'PROCESS' | 'FIELD' | 'STATUS' | 'ERROR' | 'PERMISSION' | 'IMPORT_EXPORT' | 'CONCEPT'

export interface KnowledgeSearchParams {
  keyword?: string
  categoryId?: KnowledgeId | ''
  knowledgeType?: KnowledgeType | ''
  page: number
  pageSize: number
}

export interface KnowledgeAdminArticleListParams extends KnowledgeSearchParams {
  status?: KnowledgeStatus | ''
}

export interface KnowledgeCategoryRecord {
  id: KnowledgeId
  code: string
  name: string
  parentId?: KnowledgeId | null
  parentCode?: string | null
  parentName?: string | null
  sortOrder?: number | null
  status: KnowledgeStatus
  statusName?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  version?: number
}

export interface KnowledgeCategoryPayload {
  code: string
  name: string
  parentId?: KnowledgeId | null
  sortOrder: number
  status: KnowledgeStatus
}

export interface KnowledgeArticleSummary {
  id: KnowledgeId
  slug: string
  title: string
  summary: string
  categoryId: KnowledgeId
  categoryCode: string
  categoryName: string
  knowledgeType: KnowledgeType
  knowledgeTypeName: string
  keywords?: string | null
  routePaths?: string | null
  pageNames: string
  sortOrder?: number | null
  status: KnowledgeStatus
  statusName?: string | null
  updatedAt: string
}

export interface KnowledgeArticleDetail extends KnowledgeArticleSummary {
  content: string
  permissionNote?: string | null
  relatedArticleIds?: KnowledgeId[]
  createdAt?: string | null
  version?: number
}

export interface KnowledgeArticlePayload {
  slug: string
  title: string
  summary: string
  categoryId: KnowledgeId | ''
  knowledgeType: KnowledgeType
  content: string
  keywords?: string
  routePaths?: string
  pageNames?: string
  permissionNote?: string
  relatedArticleIds: KnowledgeId[]
  sortOrder: number
  status: KnowledgeStatus
}

interface KnowledgeBaseApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export interface KnowledgeBaseApi {
  help: {
    categories(): Promise<KnowledgeCategoryRecord[]>
    search(params: KnowledgeSearchParams): Promise<PageResult<KnowledgeArticleSummary>>
    get(id: KnowledgeId): Promise<KnowledgeArticleDetail>
    byRoute(routePath: string, params?: Pick<KnowledgeSearchParams, 'page' | 'pageSize'>): Promise<PageResult<KnowledgeArticleSummary>>
    related(id: KnowledgeId): Promise<KnowledgeArticleSummary[]>
  }
  admin: {
    categories(): Promise<KnowledgeCategoryRecord[]>
    createCategory(payload: KnowledgeCategoryPayload): Promise<KnowledgeCategoryRecord>
    updateCategory(id: KnowledgeId, payload: KnowledgeCategoryPayload): Promise<KnowledgeCategoryRecord>
    enableCategory(id: KnowledgeId): Promise<KnowledgeCategoryRecord>
    disableCategory(id: KnowledgeId): Promise<KnowledgeCategoryRecord>
    deleteCategory(id: KnowledgeId): Promise<void>
    articles(params: KnowledgeAdminArticleListParams): Promise<PageResult<KnowledgeArticleSummary>>
    article(id: KnowledgeId): Promise<KnowledgeArticleDetail>
    createArticle(payload: KnowledgeArticlePayload): Promise<KnowledgeArticleDetail>
    updateArticle(id: KnowledgeId, payload: KnowledgeArticlePayload): Promise<KnowledgeArticleDetail>
    enableArticle(id: KnowledgeId): Promise<KnowledgeArticleDetail>
    disableArticle(id: KnowledgeId): Promise<KnowledgeArticleDetail>
    deleteArticle(id: KnowledgeId): Promise<void>
  }
}

const typeNames: Record<KnowledgeType, string> = {
  PAGE: '页面操作',
  PROCESS: '业务流程',
  FIELD: '字段解释',
  STATUS: '状态解释',
  ERROR: '错误处理',
  PERMISSION: '权限说明',
  IMPORT_EXPORT: '导入导出',
  CONCEPT: '业务概念',
}

export function knowledgeTypeLabel(type?: string | null) {
  return typeNames[type as KnowledgeType] ?? '未知类型'
}

export function createKnowledgeBaseApi(options: KnowledgeBaseApiOptions = {}): KnowledgeBaseApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')

  const buildUrl = (path: string, query?: object) => {
    const search = new URLSearchParams()
    Object.entries(query ?? {}).forEach(([key, value]: [string, unknown]) => {
      if (value !== undefined && value !== null && value !== '') {
        search.set(key, String(value))
      }
    })
    const queryString = search.toString()
    return `${baseUrl}${path}${queryString ? `?${queryString}` : ''}`
  }

  const request = async <T>(path: string, init: RequestInit, query?: object): Promise<T> => {
    const response = await fetcher(buildUrl(path, query), {
      credentials: 'include',
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.headers ?? {}),
      },
    })
    if (response.status === 204) {
      return undefined as T
    }

    const text = await response.text()
    const envelope = text
      ? JSON.parse(text) as ApiEnvelope<T>
      : { success: response.ok, code: response.ok ? 'OK' : 'HTTP_ERROR', message: '', data: undefined as T }

    if (!response.ok || !envelope.success) {
      throw new AccountPermissionApiError(
        envelope.message || `请求失败：${response.status}`,
        envelope.code || 'HTTP_ERROR',
        response.status,
        envelope.traceId,
      )
    }

    return envelope.data
  }

  const get = <T>(path: string, query?: object) => request<T>(path, { method: 'GET' }, query)
  const getCsrf = () => request<CsrfToken>('/api/auth/csrf', { method: 'GET' })
  const write = async <T>(method: 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<T> => {
    const csrf = await getCsrf()
    return request<T>(path, {
      body: body === undefined ? undefined : JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        [csrf.headerName]: csrf.token,
      },
      method,
    })
  }

  const articlePath = (id: KnowledgeId) => `/api/admin/system/knowledge/articles/${encodeURIComponent(String(id))}`
  const categoryPath = (id: KnowledgeId) => `/api/admin/system/knowledge/categories/${encodeURIComponent(String(id))}`

  return {
    help: {
      categories: () => get<KnowledgeCategoryRecord[]>('/api/help/categories'),
      search: (params) => get<PageResult<KnowledgeArticleSummary>>('/api/help/articles', params),
      get: (id) => get<KnowledgeArticleDetail>(`/api/help/articles/${encodeURIComponent(String(id))}`),
      byRoute: (routePath, params) => get<PageResult<KnowledgeArticleSummary>>('/api/help/articles/by-route', { routePath, ...(params ?? {}) }),
      related: (id) => get<KnowledgeArticleSummary[]>(`/api/help/articles/${encodeURIComponent(String(id))}/related`),
    },
    admin: {
      categories: () => get<KnowledgeCategoryRecord[]>('/api/admin/system/knowledge/categories'),
      createCategory: (payload) => write<KnowledgeCategoryRecord>('POST', '/api/admin/system/knowledge/categories', payload),
      updateCategory: (id, payload) => write<KnowledgeCategoryRecord>('PUT', categoryPath(id), payload),
      enableCategory: (id) => write<KnowledgeCategoryRecord>('POST', `${categoryPath(id)}/enable`),
      disableCategory: (id) => write<KnowledgeCategoryRecord>('POST', `${categoryPath(id)}/disable`),
      deleteCategory: (id) => write<void>('DELETE', categoryPath(id)),
      articles: (params) => get<PageResult<KnowledgeArticleSummary>>('/api/admin/system/knowledge/articles', params),
      article: (id) => get<KnowledgeArticleDetail>(articlePath(id)),
      createArticle: (payload) => write<KnowledgeArticleDetail>('POST', '/api/admin/system/knowledge/articles', payload),
      updateArticle: (id, payload) => write<KnowledgeArticleDetail>('PUT', articlePath(id), payload),
      enableArticle: (id) => write<KnowledgeArticleDetail>('POST', `${articlePath(id)}/enable`),
      disableArticle: (id) => write<KnowledgeArticleDetail>('POST', `${articlePath(id)}/disable`),
      deleteArticle: (id) => write<void>('DELETE', articlePath(id)),
    },
  }
}

export const knowledgeBaseApi = createKnowledgeBaseApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
