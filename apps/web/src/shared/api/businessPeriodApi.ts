import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type BusinessPeriodStatus = 'OPEN' | 'LOCKED'

export interface BusinessPeriodRecord {
  id: ResourceId
  periodCode: string
  periodName: string
  startDate: string
  endDate: string
  status: BusinessPeriodStatus
  statusName: string
  lockedBy?: string | null
  lockedAt?: string | null
  lockReason?: string | null
  unlockedBy?: string | null
  unlockedAt?: string | null
  unlockReason?: string | null
}

export type BusinessPeriodPageResult = PageResult<BusinessPeriodRecord>

export interface BusinessPeriodListParams {
  periodCode?: string | null
  status?: BusinessPeriodStatus | null
  startDate?: string | null
  endDate?: string | null
  page: number
  pageSize: number
}

export interface BusinessPeriodPayload {
  periodCode: string
  periodName: string
  startDate: string
  endDate: string
}

export interface GenerateMonthlyPayload {
  startMonth: string
  endMonth: string
}

export interface BusinessPeriodReasonPayload {
  reason: string
}

export interface BusinessPeriodResolveResult {
  configured: boolean
  businessDate: string
  period: BusinessPeriodRecord | null
  statusName: string
  message: string
}

export interface BusinessPeriodApi {
  list(params: BusinessPeriodListParams): Promise<BusinessPeriodPageResult>
  create(payload: BusinessPeriodPayload): Promise<BusinessPeriodRecord>
  update(id: ResourceId, payload: BusinessPeriodPayload): Promise<BusinessPeriodRecord>
  generateMonthly(payload: GenerateMonthlyPayload): Promise<BusinessPeriodRecord[]>
  lock(id: ResourceId, payload: BusinessPeriodReasonPayload): Promise<BusinessPeriodRecord>
  unlock(id: ResourceId, payload: BusinessPeriodReasonPayload): Promise<BusinessPeriodRecord>
  resolve(businessDate: string): Promise<BusinessPeriodResolveResult>
}

export interface BusinessPeriodApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createBusinessPeriodApi(options: BusinessPeriodApiOptions = {}): BusinessPeriodApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')

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
  const write = async <T>(method: 'POST' | 'PUT', path: string, body?: object): Promise<T> => {
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

  const periodPath = (id?: ResourceId) =>
    `/api/admin/system/business-periods${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`

  const normalizeList = (data: BusinessPeriodPageResult | BusinessPeriodRecord[], params: BusinessPeriodListParams) => {
    if (Array.isArray(data)) {
      return {
        items: data,
        page: params.page,
        pageSize: params.pageSize,
        total: data.length,
        totalPages: Math.ceil(data.length / params.pageSize),
      }
    }
    return data
  }

  return {
    async list(params) {
      const data = await get<BusinessPeriodPageResult | BusinessPeriodRecord[]>(periodPath(), params)
      return normalizeList(data, params)
    },
    create: (payload) => write<BusinessPeriodRecord>('POST', periodPath(), payload),
    update: (id, payload) => write<BusinessPeriodRecord>('PUT', periodPath(id), payload),
    generateMonthly: (payload) => write<BusinessPeriodRecord[]>('POST', `${periodPath()}/generate-monthly`, payload),
    lock: (id, payload) => write<BusinessPeriodRecord>('POST', `${periodPath(id)}/lock`, payload),
    unlock: (id, payload) => write<BusinessPeriodRecord>('POST', `${periodPath(id)}/unlock`, payload),
    resolve: (businessDate) => get<BusinessPeriodResolveResult>(`${periodPath()}/resolve`, { businessDate }),
  }
}

export const businessPeriodApi = createBusinessPeriodApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
