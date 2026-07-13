import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type ResourceId = string | number
export type BomStatus = 'DRAFT' | 'ENABLED' | 'DISABLED'
export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type RoundingMode = 'HALF_UP' | 'UP' | 'DOWN'
export type QuantityBasis = 'BASE_UNIT' | 'CONVERTED_BUSINESS_UNIT' | 'LEGACY_BUSINESS_UNIT'
export type BomEngineeringChangeStatus = 'DRAFT' | 'APPLIED' | 'CANCELLED'
export type BomHistoryRelationType = 'SOURCE' | 'TARGET'
export type MaterialSubstituteScopeType = 'GLOBAL' | 'PARENT_MATERIAL' | 'BOM'
export type MaterialSubstituteStatus = 'ENABLED' | 'DISABLED'

export interface VersionPayload {
  version: number
}

export interface BomListQuery {
  keyword?: string
  status?: BomStatus
  parentMaterialId?: ResourceId
  effectiveDate?: string
  includeHistory?: boolean
  page: number
  pageSize: number
}

export interface CandidateListQuery {
  keyword?: string
  page: number
  pageSize: number
  selectedIds?: ResourceId[]
  status?: string
  materialType?: string
  parentMaterialId?: ResourceId
  sourceBomId?: ResourceId
}

export interface CandidateItem {
  id: ResourceId
  code: string
  name: string
  status?: string
  disabled?: boolean
  disabledReason?: string | null
  summary?: string | null
  [key: string]: unknown
}

export interface CandidatePageResult<TItem extends CandidateItem = CandidateItem> extends PageResult<TItem> {
  selectedItems: TItem[]
}

export interface BomItemPayload {
  lineNo: number
  childMaterialId: ResourceId
  businessUnitId: ResourceId
  businessQuantity: string
  lossRate?: string
  remark?: string | null
}

export interface BomPayload {
  bomCode: string
  parentMaterialId: ResourceId
  versionCode: string
  name: string
  baseQuantity: string
  baseUnitId?: ResourceId
  effectiveFrom?: string | null
  effectiveTo?: string | null
  remark?: string | null
  items: BomItemPayload[]
  version?: number
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
  baseQuantity: string
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
  version: number
}

export interface BomItemRecord {
  id: ResourceId
  lineNo: number
  childMaterialId: ResourceId
  childMaterialCode: string
  childMaterialName: string
  childMaterialType: string
  businessUnitId: ResourceId
  businessUnitName: string
  businessQuantity: string
  baseUnitId?: ResourceId | null
  baseUnitName?: string | null
  baseQuantity?: string | null
  conversionRateSnapshot?: string | null
  quantityScaleSnapshot?: number | null
  roundingModeSnapshot?: RoundingMode | null
  quantityBasis?: QuantityBasis | null
  lossRate: string
  remark?: string | null
}

export interface BomHistoryRelationRecord {
  ecoId: ResourceId
  ecoNo: string
  relationType: BomHistoryRelationType
  sourceBomId: ResourceId
  sourceBomCode: string
  sourceVersionCode: string
  targetBomId: ResourceId
  targetBomCode: string
  targetVersionCode: string
  status: BomEngineeringChangeStatus
  effectiveFrom?: string | null
  effectiveTo?: string | null
  appliedBy?: string | null
  appliedAt?: string | null
}

export interface BomDetailRecord extends Omit<BomSummaryRecord, 'itemCount'> {
  items: BomItemRecord[]
  historyRelations: BomHistoryRelationRecord[]
}

export interface BomEngineeringChangeListQuery {
  keyword?: string
  status?: BomEngineeringChangeStatus
  sourceBomId?: ResourceId
  targetBomId?: ResourceId
  parentMaterialId?: ResourceId
  page: number
  pageSize: number
}

export interface BomEngineeringChangePayload {
  ecoNo?: string
  sourceBomId: ResourceId
  targetBomId: ResourceId
  effectiveFrom: string
  effectiveTo?: string | null
  changeReason: string
  impactScope: string
  changeSummary: string
  remark?: string | null
  version?: number
}

export interface ApprovalSummary {
  id: ResourceId
  sceneCode?: string | null
  status: 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'CANCELLED'
  submittedAt?: string | null
  version?: number | null
}

export interface BomEngineeringChangeRecord extends BomEngineeringChangePayload {
  id: ResourceId
  ecoNo: string
  sourceBomCode: string
  sourceVersionCode: string
  targetBomCode: string
  targetVersionCode: string
  parentMaterialId: ResourceId
  parentMaterialCode: string
  parentMaterialName: string
  status: BomEngineeringChangeStatus
  appliedBy?: string | null
  appliedAt?: string | null
  cancelReason?: string | null
  approvalSummary?: ApprovalSummary | null
  createdAt?: string
  updatedAt?: string
  version: number
}

export interface BomStateSnapshot {
  bomCode?: string | null
  versionCode?: string | null
  status?: BomStatus | string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
}

export interface BomEngineeringChangeApplyResult extends BomEngineeringChangeRecord {
  sourceBomBefore?: BomStateSnapshot | null
  sourceBomAfter?: BomStateSnapshot | null
  targetBomBefore?: BomStateSnapshot | null
  targetBomAfter?: BomStateSnapshot | null
}

export interface BomEngineeringChangeCancelPayload extends VersionPayload {
  reason: string
}

export interface MaterialSubstituteListQuery {
  keyword?: string
  mainMaterialId?: ResourceId
  substituteMaterialId?: ResourceId
  scopeType?: MaterialSubstituteScopeType
  scopeId?: ResourceId
  status?: MaterialSubstituteStatus
  effectiveDate?: string
  page: number
  pageSize: number
}

export interface MaterialSubstitutePayload {
  mainMaterialId: ResourceId
  substituteMaterialId: ResourceId
  scopeType: MaterialSubstituteScopeType
  scopeId?: ResourceId | null
  priority: number
  substituteRate: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
  status?: MaterialSubstituteStatus
  remark?: string | null
  version?: number
}

export interface MaterialSubstituteRecord extends MaterialSubstitutePayload {
  id: ResourceId
  mainMaterialCode: string
  mainMaterialName: string
  substituteMaterialCode: string
  substituteMaterialName: string
  scopeCode?: string | null
  scopeName?: string | null
  createdAt?: string
  updatedAt?: string
  version: number
}

export interface BomEngineeringChangeResource {
  list(query: BomEngineeringChangeListQuery): Promise<PageResult<BomEngineeringChangeRecord>>
  get(id: ResourceId): Promise<BomEngineeringChangeRecord>
  create(payload: BomEngineeringChangePayload): Promise<BomEngineeringChangeRecord>
  update(id: ResourceId, payload: BomEngineeringChangePayload): Promise<BomEngineeringChangeRecord>
  apply(id: ResourceId, payload: VersionPayload): Promise<BomEngineeringChangeApplyResult>
  cancel(id: ResourceId, payload: BomEngineeringChangeCancelPayload): Promise<BomEngineeringChangeRecord>
  sourceBomCandidates(query: CandidateListQuery): Promise<CandidatePageResult>
  targetBomCandidates(query: CandidateListQuery): Promise<CandidatePageResult>
}

export interface MaterialSubstituteResource {
  list(query: MaterialSubstituteListQuery): Promise<PageResult<MaterialSubstituteRecord>>
  get(id: ResourceId): Promise<MaterialSubstituteRecord>
  create(payload: MaterialSubstitutePayload): Promise<MaterialSubstituteRecord>
  update(id: ResourceId, payload: MaterialSubstitutePayload): Promise<MaterialSubstituteRecord>
  enable(id: ResourceId, payload: VersionPayload): Promise<MaterialSubstituteRecord>
  disable(id: ResourceId, payload: VersionPayload): Promise<MaterialSubstituteRecord>
  materialCandidates(query: CandidateListQuery): Promise<CandidatePageResult>
  bomCandidates(query: CandidateListQuery): Promise<CandidatePageResult>
}

export interface BomApi {
  list(query: BomListQuery): Promise<PageResult<BomSummaryRecord>>
  get(id: ResourceId): Promise<BomDetailRecord>
  create(payload: BomPayload): Promise<BomDetailRecord>
  update(id: ResourceId, payload: BomPayload): Promise<BomDetailRecord>
  copy(id: ResourceId, payload: BomCopyPayload): Promise<BomDetailRecord>
  enable(id: ResourceId, payload: VersionPayload): Promise<BomDetailRecord>
  disable(id: ResourceId, payload: VersionPayload): Promise<BomDetailRecord>
  materialCandidates(query: CandidateListQuery): Promise<CandidatePageResult>
  unitCandidates(query: CandidateListQuery): Promise<CandidatePageResult>
  engineeringChanges: BomEngineeringChangeResource
  substitutes: MaterialSubstituteResource
}

export interface BomApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createBomApi(options: BomApiOptions = {}): BomApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const listQueryKeys = ['keyword', 'status', 'parentMaterialId', 'effectiveDate', 'includeHistory', 'page', 'pageSize'] as const
  const candidateQueryKeys = [
    'keyword',
    'page',
    'pageSize',
    'selectedIds',
    'status',
    'materialType',
    'parentMaterialId',
    'sourceBomId',
  ] as const
  const engineeringChangeQueryKeys = [
    'keyword',
    'status',
    'sourceBomId',
    'targetBomId',
    'parentMaterialId',
    'page',
    'pageSize',
  ] as const
  const substituteQueryKeys = [
    'keyword',
    'mainMaterialId',
    'substituteMaterialId',
    'scopeType',
    'scopeId',
    'status',
    'effectiveDate',
    'page',
    'pageSize',
  ] as const

  const pickQuery = (query: object | undefined, keys: readonly string[]) => {
    const result: Record<string, unknown> = {}
    keys.forEach((key) => {
      const value = (query as Record<string, unknown> | undefined)?.[key]
      if (value !== undefined) {
          result[key] = Array.isArray(value) ? value.join(',') : value
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

  const createCandidateGetter = (path: string) => (query: CandidateListQuery) =>
    get<CandidatePageResult>(path, pickQuery(query, candidateQueryKeys))

  return {
    list: (query) => get<PageResult<BomSummaryRecord>>('/api/admin/boms', pickQuery(query, listQueryKeys)),
    get: (id) => get<BomDetailRecord>(`/api/admin/boms/${encodeURIComponent(String(id))}`),
    create: (payload) => write<BomDetailRecord>('POST', '/api/admin/boms', payload),
    update: (id, payload) => write<BomDetailRecord>('PUT', `/api/admin/boms/${encodeURIComponent(String(id))}`, payload),
    copy: (id, payload) => write<BomDetailRecord>('POST', `/api/admin/boms/${encodeURIComponent(String(id))}/copy`, payload),
    enable: (id, payload) => write<BomDetailRecord>('PUT', `/api/admin/boms/${encodeURIComponent(String(id))}/enable`, payload),
    disable: (id, payload) => write<BomDetailRecord>('PUT', `/api/admin/boms/${encodeURIComponent(String(id))}/disable`, payload),
    materialCandidates: createCandidateGetter('/api/admin/boms/material-candidates'),
    unitCandidates: createCandidateGetter('/api/admin/boms/unit-candidates'),
    engineeringChanges: {
      list: (query) =>
        get<PageResult<BomEngineeringChangeRecord>>(
          '/api/admin/bom-engineering-changes',
          pickQuery(query, engineeringChangeQueryKeys),
        ),
      get: (id) => get<BomEngineeringChangeRecord>(`/api/admin/bom-engineering-changes/${encodeURIComponent(String(id))}`),
      create: (payload) => write<BomEngineeringChangeRecord>('POST', '/api/admin/bom-engineering-changes', payload),
      update: (id, payload) =>
        write<BomEngineeringChangeRecord>('PUT', `/api/admin/bom-engineering-changes/${encodeURIComponent(String(id))}`, payload),
      apply: (id, payload) =>
        write<BomEngineeringChangeApplyResult>(
          'PUT',
          `/api/admin/bom-engineering-changes/${encodeURIComponent(String(id))}/apply`,
          payload,
        ),
      cancel: (id, payload) =>
        write<BomEngineeringChangeRecord>(
          'PUT',
          `/api/admin/bom-engineering-changes/${encodeURIComponent(String(id))}/cancel`,
          payload,
        ),
      sourceBomCandidates: createCandidateGetter('/api/admin/bom-engineering-changes/source-bom-candidates'),
      targetBomCandidates: createCandidateGetter('/api/admin/bom-engineering-changes/target-bom-candidates'),
    },
    substitutes: {
      list: (query) =>
        get<PageResult<MaterialSubstituteRecord>>(
          '/api/admin/material-substitutes',
          pickQuery(query, substituteQueryKeys),
        ),
      get: (id) => get<MaterialSubstituteRecord>(`/api/admin/material-substitutes/${encodeURIComponent(String(id))}`),
      create: (payload) => write<MaterialSubstituteRecord>('POST', '/api/admin/material-substitutes', payload),
      update: (id, payload) =>
        write<MaterialSubstituteRecord>('PUT', `/api/admin/material-substitutes/${encodeURIComponent(String(id))}`, payload),
      enable: (id, payload) =>
        write<MaterialSubstituteRecord>('PUT', `/api/admin/material-substitutes/${encodeURIComponent(String(id))}/enable`, payload),
      disable: (id, payload) =>
        write<MaterialSubstituteRecord>('PUT', `/api/admin/material-substitutes/${encodeURIComponent(String(id))}/disable`, payload),
      materialCandidates: createCandidateGetter('/api/admin/material-substitutes/material-candidates'),
      bomCandidates: createCandidateGetter('/api/admin/material-substitutes/bom-candidates'),
    },
  }
}

export const bomApi = createBomApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
