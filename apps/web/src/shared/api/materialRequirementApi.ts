import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number

export type MaterialRequirementRunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'STALE' | 'EXPIRED' | string
export type MaterialRequirementSuggestionStatus = 'OPEN' | 'CONFIRMED' | 'CONVERTED' | 'DISMISSED' | string
export type MaterialRequirementSuggestionType =
  | 'PURCHASE_REQUISITION'
  | 'PRODUCTION_ORDER'
  | 'USE_PUBLIC_STOCK'
  | 'USE_EXISTING_SUPPLY'
  | string

export interface MaterialRequirementPageParams {
  page: number
  pageSize: number
}

export interface MaterialRequirementRunListParams extends MaterialRequirementPageParams {
  projectId?: ResourceId | null | ''
  customerId?: ResourceId | null | ''
  contractId?: ResourceId | null | ''
  orderId?: ResourceId | null | ''
  materialId?: ResourceId | null | ''
  requiredDateTo?: string | null
  status?: MaterialRequirementRunStatus | null
  expired?: boolean | null
}

export interface MaterialRequirementRunCreatePayload {
  projectId?: ResourceId | null
  customerId?: ResourceId | null
  contractId?: ResourceId | null
  orderId?: ResourceId | null
  materialId?: ResourceId | null
  requiredDateTo?: string | null
  includePublicDemand?: boolean | null
  idempotencyKey: string
}

export interface MaterialRequirementVersionPayload {
  version: number
  idempotencyKey: string
}

export interface MaterialRequirementDismissPayload extends MaterialRequirementVersionPayload {
  reason: string
}

export interface MaterialRequirementSuggestionConversionRecord {
  suggestionId: ResourceId
  status: MaterialRequirementSuggestionStatus
  targetObjectType: 'WORK_ORDER' | 'OUTSOURCING_ORDER' | 'PURCHASE_REQUISITION' | string
  targetObjectId: ResourceId
  targetObjectNo: string
  targetRoute: string
  version: number
}

export interface MaterialRequirementRequirementLineParams extends MaterialRequirementPageParams {
  materialId?: ResourceId | null | ''
  coverageStatus?: string | null
}

export interface MaterialRequirementAllocationParams extends MaterialRequirementPageParams {
  requirementLineId?: ResourceId | null
}

export interface MaterialRequirementSuggestionListParams extends MaterialRequirementPageParams {
  status?: MaterialRequirementSuggestionStatus | null
  suggestionType?: MaterialRequirementSuggestionType | null
}

export interface MaterialRequirementSubstituteHintParams extends MaterialRequirementPageParams {
  requirementLineId?: ResourceId | null
}

export interface MaterialRequirementRunRecord {
  id: ResourceId
  runNo: string
  status: MaterialRequirementRunStatus
  statusName?: string | null
  scopeSummary?: string | null
  projectCount?: number | null
  requirementLineCount: number
  shortageMaterialCount: number
  purchaseSuggestionCount: number
  productionSuggestionCount: number
  exceptionCount?: number | null
  asOfBusinessDate?: string | null
  asOfTime?: string | null
  completedAt?: string | null
  expiresAt?: string | null
  createdByName?: string | null
  stale?: boolean | null
  expired?: boolean | null
  sourceChangedReason?: string | null
  failureCode?: string | null
  failureSummary?: string | null
  allowedActions?: string[]
  version: number
}

export interface MaterialRequirementRunDetailRecord extends MaterialRequirementRunRecord {
  sourceFingerprint?: string | null
  sourceCounts?: Record<string, number>
  previousRunId?: ResourceId | null
}

export interface MaterialRequirementRequirementLineRecord {
  id: ResourceId
  lineNo?: number | null
  demandSourceNo?: string | null
  orderNo?: string | null
  deliveryPlanNo?: string | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  finishedMaterialCode?: string | null
  finishedMaterialName?: string | null
  bomVersionNo?: string | null
  bomPath?: string | null
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName?: string | null
  requiredQuantity: string
  coveredQuantity: string
  shortageQuantity: string
  requiredDate?: string | null
  estimatedAvailableDate?: string | null
  coverageStatus?: string | null
  suggestionType?: MaterialRequirementSuggestionType | null
  exceptionReasonCode?: string | null
  allowedActions?: string[]
}

export interface MaterialRequirementAllocationRecord {
  id: ResourceId
  requirementLineId: ResourceId
  supplyType: string
  supplyTypeName?: string | null
  ownershipType?: string | null
  projectNo?: string | null
  warehouseName?: string | null
  sourceNo?: string | null
  availableDate?: string | null
  allocatedQuantity: string
  onTime?: boolean | null
  excludedReasonCode?: string | null
}

export interface MaterialRequirementSuggestionRecord {
  id: ResourceId
  suggestionNo: string
  runId: ResourceId
  suggestionType: MaterialRequirementSuggestionType
  status: MaterialRequirementSuggestionStatus
  statusName?: string | null
  materialSourceType?: string | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName?: string | null
  suggestedQuantity: string
  suggestedDate?: string | null
  reasonCode?: string | null
  reasonMessage?: string | null
  conversionAllowed?: boolean | null
  convertedRequisitionId?: ResourceId | null
  convertedRequisitionNo?: string | null
  actionDisabledReason?: string | null
  allowedActions?: string[]
  version: number
}

export interface MaterialRequirementSubstituteHintRecord {
  id: ResourceId
  requirementLineId?: ResourceId | null
  mainMaterialId: ResourceId
  mainMaterialCode?: string | null
  mainMaterialName?: string | null
  substituteMaterialId: ResourceId
  substituteMaterialCode?: string | null
  substituteMaterialName?: string | null
  substituteRate?: string | null
  priority?: number | null
  hintMessage?: string | null
}

export interface MaterialRequirementApi {
  runs: {
    create(payload: MaterialRequirementRunCreatePayload): Promise<MaterialRequirementRunRecord>
    list(params: MaterialRequirementRunListParams): Promise<PageResult<MaterialRequirementRunRecord>>
    get(id: ResourceId): Promise<MaterialRequirementRunDetailRecord>
    recalculate(id: ResourceId, payload: MaterialRequirementVersionPayload): Promise<MaterialRequirementRunRecord>
    requirements(
      id: ResourceId,
      params: MaterialRequirementRequirementLineParams,
    ): Promise<PageResult<MaterialRequirementRequirementLineRecord>>
    allocations(
      id: ResourceId,
      params: MaterialRequirementAllocationParams,
    ): Promise<PageResult<MaterialRequirementAllocationRecord>>
    suggestions(
      id: ResourceId,
      params: MaterialRequirementSuggestionListParams,
    ): Promise<PageResult<MaterialRequirementSuggestionRecord>>
    substituteHints(
      id: ResourceId,
      params: MaterialRequirementSubstituteHintParams,
    ): Promise<PageResult<MaterialRequirementSubstituteHintRecord>>
  }
  suggestions: {
    confirm(id: ResourceId, payload: MaterialRequirementVersionPayload): Promise<MaterialRequirementSuggestionRecord>
    dismiss(id: ResourceId, payload: MaterialRequirementDismissPayload): Promise<MaterialRequirementSuggestionRecord>
    convertRequisition(
      id: ResourceId,
      payload: MaterialRequirementVersionPayload,
    ): Promise<MaterialRequirementSuggestionRecord>
    convertWorkOrder(
      id: ResourceId,
      payload: MaterialRequirementVersionPayload,
    ): Promise<MaterialRequirementSuggestionConversionRecord>
    convertOutsourcingOrder(
      id: ResourceId,
      payload: MaterialRequirementVersionPayload,
    ): Promise<MaterialRequirementSuggestionConversionRecord>
  }
}

export interface MaterialRequirementApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createMaterialRequirementApi(options: MaterialRequirementApiOptions = {}): MaterialRequirementApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const runQueryKeys = [
    'projectId',
    'customerId',
    'contractId',
    'orderId',
    'materialId',
    'requiredDateTo',
    'status',
    'expired',
    'page',
    'pageSize',
  ] as const
  const requirementQueryKeys = ['materialId', 'coverageStatus', 'page', 'pageSize'] as const
  const allocationQueryKeys = ['requirementLineId', 'page', 'pageSize'] as const
  const suggestionQueryKeys = ['status', 'suggestionType', 'page', 'pageSize'] as const
  const substituteHintQueryKeys = ['requirementLineId', 'page', 'pageSize'] as const

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
    const init: RequestInit = { headers, method }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }
    return request<T>(path, init)
  }
  const assertIdempotencyKey = (payload: { idempotencyKey?: string }) => {
    if (typeof payload.idempotencyKey !== 'string' || payload.idempotencyKey.trim() === '') {
      throw new Error('幂等键不能为空')
    }
  }
  const writeAction = async <T>(method: 'POST' | 'PUT', path: string, payload: { idempotencyKey?: string }) => {
    assertIdempotencyKey(payload)
    return write<T>(method, path, payload)
  }
  const runPath = (id?: ResourceId) =>
    `/api/admin/planning/material-requirement-runs${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const suggestionPath = (id: ResourceId) =>
    `/api/admin/planning/material-requirement-suggestions/${encodeURIComponent(String(id))}`

  return {
    runs: {
      create: (payload) => writeAction<MaterialRequirementRunRecord>('POST', runPath(), payload),
      list: (params) =>
        get<PageResult<MaterialRequirementRunRecord>>(runPath(), pickQuery(params, runQueryKeys)),
      get: (id) => get<MaterialRequirementRunDetailRecord>(runPath(id)),
      recalculate: (id, payload) =>
        writeAction<MaterialRequirementRunRecord>('POST', `${runPath(id)}/recalculate`, payload),
      requirements: (id, params) =>
        get<PageResult<MaterialRequirementRequirementLineRecord>>(
          `${runPath(id)}/requirements`,
          pickQuery(params, requirementQueryKeys),
        ),
      allocations: (id, params) =>
        get<PageResult<MaterialRequirementAllocationRecord>>(
          `${runPath(id)}/allocations`,
          pickQuery(params, allocationQueryKeys),
        ),
      suggestions: (id, params) =>
        get<PageResult<MaterialRequirementSuggestionRecord>>(
          `${runPath(id)}/suggestions`,
          pickQuery(params, suggestionQueryKeys),
        ),
      substituteHints: (id, params) =>
        get<PageResult<MaterialRequirementSubstituteHintRecord>>(
          `${runPath(id)}/substitute-hints`,
          pickQuery(params, substituteHintQueryKeys),
        ),
    },
    suggestions: {
      confirm: (id, payload) =>
        writeAction<MaterialRequirementSuggestionRecord>('PUT', `${suggestionPath(id)}/confirm`, payload),
      dismiss: (id, payload) =>
        writeAction<MaterialRequirementSuggestionRecord>('PUT', `${suggestionPath(id)}/dismiss`, payload),
      convertRequisition: (id, payload) =>
        writeAction<MaterialRequirementSuggestionRecord>('POST', `${suggestionPath(id)}/convert-requisition`, payload),
      convertWorkOrder: (id, payload) =>
        writeAction<MaterialRequirementSuggestionConversionRecord>(
          'POST',
          `${suggestionPath(id)}/convert-work-order`,
          payload,
        ),
      convertOutsourcingOrder: (id, payload) =>
        writeAction<MaterialRequirementSuggestionConversionRecord>(
          'POST',
          `${suggestionPath(id)}/convert-outsourcing-order`,
          payload,
        ),
    },
  }
}

export const materialRequirementApi = createMaterialRequirementApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
