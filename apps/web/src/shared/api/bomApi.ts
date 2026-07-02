import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type ResourceId = string | number
export type BomStatus = 'DRAFT' | 'ENABLED' | 'DISABLED'
export type Fetcher = (input: string, init: RequestInit) => Promise<Response>

export interface BomListQuery {
  keyword?: string
  status?: BomStatus
  parentMaterialId?: ResourceId
  page: number
  pageSize: number
}

export interface BomItemPayload {
  lineNo: number
  childMaterialId: ResourceId
  unitId?: ResourceId
  quantity: number
  lossRate?: number
  remark?: string
}

export interface BomPayload {
  bomCode: string
  parentMaterialId: ResourceId
  versionCode: string
  name: string
  baseQuantity: number
  baseUnitId?: ResourceId
  effectiveFrom?: string | null
  effectiveTo?: string | null
  remark?: string
  items: BomItemPayload[]
}

export interface BomCopyPayload {
  bomCode: string
  versionCode: string
  name?: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
  remark?: string
}

export interface BomSummaryRecord {
  id: ResourceId
  bomCode: string
  parentMaterialId: ResourceId
  parentMaterialCode: string
  parentMaterialName: string
  versionCode: string
  name: string
  baseQuantity: number
  baseUnitId: ResourceId
  baseUnitName: string
  status: BomStatus
  itemCount: number
  effectiveFrom?: string | null
  effectiveTo?: string | null
  remark?: string | null
  createdAt?: string
  updatedAt?: string
  enabledAt?: string | null
}

export interface BomItemRecord {
  id: ResourceId
  lineNo: number
  childMaterialId: ResourceId
  childMaterialCode: string
  childMaterialName: string
  childMaterialType: string
  unitId: ResourceId
  unitName: string
  quantity: number
  lossRate: number
  remark?: string | null
}

export interface BomDetailRecord extends Omit<BomSummaryRecord, 'itemCount'> {
  items: BomItemRecord[]
}

export interface BomApi {
  list(query: BomListQuery): Promise<PageResult<BomSummaryRecord>>
  get(id: ResourceId): Promise<BomDetailRecord>
  create(payload: BomPayload): Promise<BomDetailRecord>
  update(id: ResourceId, payload: BomPayload): Promise<BomDetailRecord>
  copy(id: ResourceId, payload: BomCopyPayload): Promise<BomDetailRecord>
  enable(id: ResourceId): Promise<BomDetailRecord>
  disable(id: ResourceId): Promise<BomDetailRecord>
}

export interface BomApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createBomApi(options: BomApiOptions = {}): BomApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const listQueryKeys = ['keyword', 'status', 'parentMaterialId', 'page', 'pageSize'] as const

  const pickQuery = (query: object | undefined, keys: readonly string[]) => {
    const result: Record<string, unknown> = {}
    keys.forEach((key) => {
      const value = (query as Record<string, unknown> | undefined)?.[key]
      if (value !== undefined) {
        result[key] = value
      }
    })
    return result
  }

  const buildUrl = (path: string, query?: object) => {
    const search = new URLSearchParams()
    Object.entries(query ?? {}).forEach(([key, value]) => {
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
    const envelope = (await response.json()) as ApiEnvelope<T>

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

  const getCsrf = () => request<CsrfToken>('/api/auth/csrf', { method: 'GET' })
  const get = <T>(path: string, query?: object) => request<T>(path, { method: 'GET' }, query)
  const write = async <T>(method: 'POST' | 'PUT', path: string, body?: object) => {
    const csrf = await getCsrf()
    const headers: Record<string, string> = {
      [csrf.headerName]: csrf.token,
    }
    const init: RequestInit = {
      headers,
      method,
    }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }

    return request<T>(path, init)
  }

  return {
    list: (query) => get<PageResult<BomSummaryRecord>>('/api/admin/boms', pickQuery(query, listQueryKeys)),
    get: (id) => get<BomDetailRecord>(`/api/admin/boms/${encodeURIComponent(String(id))}`),
    create: (payload) => write<BomDetailRecord>('POST', '/api/admin/boms', payload),
    update: (id, payload) => write<BomDetailRecord>('PUT', `/api/admin/boms/${encodeURIComponent(String(id))}`, payload),
    copy: (id, payload) => write<BomDetailRecord>('POST', `/api/admin/boms/${encodeURIComponent(String(id))}/copy`, payload),
    enable: (id) => write<BomDetailRecord>('PUT', `/api/admin/boms/${encodeURIComponent(String(id))}/enable`),
    disable: (id) => write<BomDetailRecord>('PUT', `/api/admin/boms/${encodeURIComponent(String(id))}/disable`),
  }
}

export const bomApi = createBomApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
