import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type FinanceAmount = string | number
export type FinanceMoneyPayload = string

export interface FinanceStage028ApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export interface VersionedActionPayload {
  version: number
  idempotencyKey: string
}

export type OwnershipType = 'PROJECT' | 'PUBLIC'
export type SettlementStatus = 'UNSETTLED' | 'PARTIALLY_SETTLED' | 'SETTLED' | 'PARTIALLY_APPLIED' | 'APPLIED' | 'AVAILABLE'

export function createFinanceStage028Transport(options: FinanceStage028ApiOptions = {}) {
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

  const pickQuery = <T extends object>(query: T | undefined, keys: readonly (keyof T & string)[]) => {
    const result: Record<string, unknown> = {}
    keys.forEach((key) => {
      const value = query?.[key]
      if (value !== undefined) {
        result[key] = value
      }
    })
    return result
  }

  const encodeId = (id: ResourceId) => encodeURIComponent(String(id))

  return { get, write, pickQuery, encodeId }
}
