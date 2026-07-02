import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type MasterDataStatus = 'ENABLED' | 'DISABLED'
export type MaterialType = 'RAW_MATERIAL' | 'SEMI_FINISHED' | 'FINISHED_GOOD' | 'AUXILIARY'
export type MaterialSourceType = 'PURCHASED' | 'SELF_MADE' | 'OUTSOURCED'

export interface MasterDataListQuery {
  keyword?: string
  status?: MasterDataStatus
  page: number
  pageSize: number
}

export type MaterialCategoryListQuery = MasterDataListQuery

export interface MaterialListQuery extends MasterDataListQuery {
  materialType?: MaterialType
  sourceType?: MaterialSourceType
  categoryId?: ResourceId
}

interface BaseMasterRecord {
  id: ResourceId
  code: string
  name: string
  status: MasterDataStatus
  remark?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface UnitRecord extends BaseMasterRecord {
  precisionScale: number
  sortOrder: number
}

export interface WarehouseRecord extends BaseMasterRecord {
  warehouseType?: string | null
  managerName?: string | null
  address?: string | null
}

export interface PartnerRecord extends BaseMasterRecord {
  contactName?: string | null
  contactPhone?: string | null
}

export interface CategoryRecord extends BaseMasterRecord {
  parentId?: ResourceId | null
  sortOrder: number
}

export interface MaterialRecord extends BaseMasterRecord {
  specification?: string | null
  materialType: MaterialType
  sourceType: MaterialSourceType
  categoryId: ResourceId
  categoryName?: string | null
  unitId: ResourceId
  unitName?: string | null
}

export interface UnitPayload {
  code: string
  name: string
  precisionScale: number
  sortOrder: number
  status?: MasterDataStatus
  remark?: string
}

export interface WarehousePayload {
  code: string
  name: string
  warehouseType?: string
  managerName?: string
  address?: string
  status?: MasterDataStatus
  remark?: string
}

export interface PartnerPayload {
  code: string
  name: string
  contactName?: string
  contactPhone?: string
  status?: MasterDataStatus
  remark?: string
}

export interface CategoryPayload {
  code: string
  name: string
  parentId?: ResourceId | null
  status?: MasterDataStatus
  sortOrder: number
  remark?: string
}

export interface MaterialPayload {
  code: string
  name: string
  specification?: string
  materialType: MaterialType
  sourceType: MaterialSourceType
  categoryId: ResourceId
  unitId: ResourceId
  status?: MasterDataStatus
  remark?: string
}

export interface MasterDataResource<TRecord, TPayload, TQuery extends MasterDataListQuery = MasterDataListQuery> {
  list(query: TQuery): Promise<PageResult<TRecord>>
  get(id: ResourceId): Promise<TRecord>
  create(payload: TPayload): Promise<TRecord>
  update(id: ResourceId, payload: TPayload): Promise<TRecord>
  enable(id: ResourceId): Promise<TRecord>
  disable(id: ResourceId): Promise<TRecord>
}

export interface MasterDataApi {
  units: MasterDataResource<UnitRecord, UnitPayload>
  warehouses: MasterDataResource<WarehouseRecord, WarehousePayload>
  suppliers: MasterDataResource<PartnerRecord, PartnerPayload>
  customers: MasterDataResource<PartnerRecord, PartnerPayload>
  categories: MasterDataResource<CategoryRecord, CategoryPayload, MaterialCategoryListQuery>
  materials: MasterDataResource<MaterialRecord, MaterialPayload, MaterialListQuery>
}

export interface MasterDataApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createMasterDataApi(options: MasterDataApiOptions = {}): MasterDataApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const defaultQueryKeys = ['keyword', 'status', 'page', 'pageSize'] as const
  const materialQueryKeys = [
    'keyword',
    'status',
    'page',
    'pageSize',
    'materialType',
    'sourceType',
    'categoryId',
  ] as const

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

  const createResource = <TRecord, TPayload, TQuery extends MasterDataListQuery = MasterDataListQuery>(
    path: string,
    queryKeys: readonly string[] = defaultQueryKeys,
  ): MasterDataResource<TRecord, TPayload, TQuery> => ({
    list: (query) => get<PageResult<TRecord>>(path, pickQuery(query, queryKeys)),
    get: (id) => get<TRecord>(`${path}/${encodeURIComponent(String(id))}`),
    create: (payload) => write<TRecord>('POST', path, payload as object),
    update: (id, payload) => write<TRecord>('PUT', `${path}/${encodeURIComponent(String(id))}`, payload as object),
    enable: (id) => write<TRecord>('PUT', `${path}/${encodeURIComponent(String(id))}/enable`),
    disable: (id) => write<TRecord>('PUT', `${path}/${encodeURIComponent(String(id))}/disable`),
  })

  return {
    units: createResource<UnitRecord, UnitPayload>('/api/admin/master/units'),
    warehouses: createResource<WarehouseRecord, WarehousePayload>('/api/admin/master/warehouses'),
    suppliers: createResource<PartnerRecord, PartnerPayload>('/api/admin/master/suppliers'),
    customers: createResource<PartnerRecord, PartnerPayload>('/api/admin/master/customers'),
    categories: createResource<CategoryRecord, CategoryPayload, MaterialCategoryListQuery>(
      '/api/admin/master/material-categories',
    ),
    materials: createResource<MaterialRecord, MaterialPayload, MaterialListQuery>(
      '/api/admin/master/materials',
      materialQueryKeys,
    ),
  }
}

export const masterDataApi = createMasterDataApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
