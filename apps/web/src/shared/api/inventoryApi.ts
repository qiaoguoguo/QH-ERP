import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type InventoryDocumentStatus = 'DRAFT' | 'POSTED'
export type InventoryDocumentType = 'OPENING' | 'ADJUSTMENT'
export type InventoryMovementType =
  | 'OPENING'
  | 'ADJUSTMENT_INCREASE'
  | 'ADJUSTMENT_DECREASE'
  | 'PRODUCTION_ISSUE'
  | 'PRODUCTION_RECEIPT'
  | 'PURCHASE_RECEIPT'
  | 'SALES_SHIPMENT'
export type InventoryDirection = 'IN' | 'OUT'
export type InventoryAdjustmentDirection = 'INCREASE' | 'DECREASE'
export type InventoryQuantityPayload = string
export type InventoryQualityStatus = 'PENDING_INSPECTION' | 'QUALIFIED' | 'REJECTED' | 'FROZEN'
export type InventoryReservationType = 'RESERVATION' | 'OCCUPATION'
export type InventoryReservationStatus = 'ACTIVE' | 'RELEASED' | 'CONSUMED' | 'CANCELLED'

export interface InventoryBalanceListParams {
  keyword?: string
  warehouseId?: ResourceId
  materialId?: ResourceId
  materialType?: string
  qualityStatus?: InventoryQualityStatus
  includeZeroQualityStatuses?: boolean
  onlyPositive?: boolean
  page: number
  pageSize: number
}

export interface InventoryMovementListParams {
  keyword?: string
  warehouseId?: ResourceId
  materialId?: ResourceId
  movementType?: InventoryMovementType
  direction?: InventoryDirection
  qualityStatus?: InventoryQualityStatus
  sourceType?: string
  sourceId?: ResourceId
  sourceLineId?: ResourceId
  dateFrom?: string
  dateTo?: string
  page: number
  pageSize: number
}

export interface InventoryDocumentListParams {
  keyword?: string
  documentType?: InventoryDocumentType
  status?: InventoryDocumentStatus
  dateFrom?: string
  dateTo?: string
  page: number
  pageSize: number
}

export interface InventoryReservationListParams {
  keyword?: string
  warehouseId?: ResourceId
  materialId?: ResourceId
  reservationType?: InventoryReservationType
  status?: InventoryReservationStatus
  sourceType?: string
  sourceId?: ResourceId
  sourceLineId?: ResourceId
  businessDateFrom?: string
  businessDateTo?: string
  page: number
  pageSize: number
}

export interface InventoryBalanceRecord {
  id: ResourceId
  warehouseId: ResourceId
  warehouseCode: string
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  materialSpec?: string | null
  materialType: string
  unitId: ResourceId
  unitName: string
  qualityStatus?: InventoryQualityStatus
  qualityStatusName?: string
  bookQuantity?: number | string
  quantityOnHand: number | string
  lockedQuantity: number | string
  availableQuantity: number | string
  totalQuantityOnHand?: number | string
  pendingInspectionQuantity?: number | string
  qualifiedQuantity?: number | string
  rejectedQuantity?: number | string
  frozenQuantity?: number | string
  reservedQuantity?: number | string
  occupiedQuantity?: number | string
  inTransitQuantity?: number | string
  availableToPromiseQuantity?: number | string
  netRequirementShortageQuantity?: number | string
  unavailableReason?: string | null
  updatedAt: string
}

export interface InventoryReservationSummaryRecord {
  id: ResourceId
  reservationNo: string
  reservationType: InventoryReservationType
  reservationTypeName: string
  status: InventoryReservationStatus
  statusName: string
  warehouseId: ResourceId
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  qualityStatus: InventoryQualityStatus
  qualityStatusName: string
  quantity: number | string
  remainingQuantity: number | string
  releasedQuantity: number | string
  consumedQuantity: number | string
  sourceType: string
  sourceTypeName: string
  sourceId: ResourceId
  sourceLineId: ResourceId
  sourceDocumentNo: string
  businessDate: string
  reason?: string | null
  remark?: string | null
  createdByName: string
  createdAt: string
  releasedByName?: string | null
  releasedAt?: string | null
}

export interface InventoryReservationAuditRecord {
  action: string
  actionName: string
  operatorName: string
  operatedAt: string
  businessDate?: string | null
  reason?: string | null
  remark?: string | null
}

export interface InventoryReservationDetailRecord extends InventoryReservationSummaryRecord {
  sourceSummary?: Record<string, unknown> | null
  auditRecords?: InventoryReservationAuditRecord[]
}

export interface InventoryMovementRecord {
  id: ResourceId
  movementNo: string
  movementType: InventoryMovementType
  direction: InventoryDirection
  warehouseId: ResourceId
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  qualityStatus?: InventoryQualityStatus
  qualityStatusName?: string
  quantity: number | string
  beforeQuantity: number | string
  afterQuantity: number | string
  sourceType: string
  sourceId: ResourceId
  sourceLineId?: ResourceId | null
  sourceDocumentNo?: string | null
  relatedMovementId?: ResourceId | null
  businessDate: string
  reason?: string | null
  remark?: string | null
  operatorName: string
  occurredAt: string
}

export interface InventoryDocumentSummaryRecord {
  id: ResourceId
  documentNo: string
  documentType: InventoryDocumentType
  status: InventoryDocumentStatus
  businessDate: string
  reason: string
  remark?: string | null
  lineCount: number
  createdByName: string
  createdAt: string
  updatedAt: string
  postedByName?: string | null
  postedAt?: string | null
}

export interface InventoryDocumentLineRecord {
  id: ResourceId
  lineNo: number
  warehouseId: ResourceId
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  quantity: number
  adjustmentDirection?: InventoryAdjustmentDirection | null
  beforeQuantity?: number | null
  afterQuantity?: number | null
  remark?: string | null
}

export interface InventoryDocumentDetailRecord extends InventoryDocumentSummaryRecord {
  lines: InventoryDocumentLineRecord[]
}

export interface InventoryDocumentLinePayload {
  lineNo: number
  warehouseId: ResourceId
  materialId: ResourceId
  unitId?: ResourceId
  quantity: InventoryQuantityPayload
  adjustmentDirection?: InventoryAdjustmentDirection
  remark?: string
}

export interface InventoryDocumentPayload {
  documentType: InventoryDocumentType
  businessDate: string
  reason: string
  remark?: string
  lines: InventoryDocumentLinePayload[]
}

export interface InventoryApi {
  balances: {
    list(params: InventoryBalanceListParams): Promise<PageResult<InventoryBalanceRecord>>
  }
  reservations: {
    list(params: InventoryReservationListParams): Promise<PageResult<InventoryReservationSummaryRecord>>
    get(id: ResourceId): Promise<InventoryReservationDetailRecord>
  }
  movements: {
    list(params: InventoryMovementListParams): Promise<PageResult<InventoryMovementRecord>>
  }
  documents: {
    list(params: InventoryDocumentListParams): Promise<PageResult<InventoryDocumentSummaryRecord>>
    get(id: ResourceId): Promise<InventoryDocumentDetailRecord>
    create(payload: InventoryDocumentPayload): Promise<InventoryDocumentDetailRecord>
    update(id: ResourceId, payload: InventoryDocumentPayload): Promise<InventoryDocumentDetailRecord>
    post(id: ResourceId): Promise<InventoryDocumentDetailRecord>
  }
}

export interface InventoryApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createInventoryApi(options: InventoryApiOptions = {}): InventoryApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const balanceQueryKeys = [
    'keyword',
    'warehouseId',
    'materialId',
    'materialType',
    'qualityStatus',
    'includeZeroQualityStatuses',
    'onlyPositive',
    'page',
    'pageSize',
  ] as const
  const movementQueryKeys = [
    'keyword',
    'warehouseId',
    'materialId',
    'movementType',
    'direction',
    'qualityStatus',
    'sourceType',
    'sourceId',
    'sourceLineId',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const documentQueryKeys = ['keyword', 'documentType', 'status', 'dateFrom', 'dateTo', 'page', 'pageSize'] as const
  const reservationQueryKeys = [
    'keyword',
    'warehouseId',
    'materialId',
    'reservationType',
    'status',
    'sourceType',
    'sourceId',
    'sourceLineId',
    'businessDateFrom',
    'businessDateTo',
    'page',
    'pageSize',
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

  return {
    balances: {
      list: (params) =>
        get<PageResult<InventoryBalanceRecord>>(
          '/api/admin/inventory/balances',
          pickQuery(params, balanceQueryKeys),
        ),
    },
    reservations: {
      list: (params) =>
        get<PageResult<InventoryReservationSummaryRecord>>(
          '/api/admin/inventory/reservations',
          pickQuery(params, reservationQueryKeys),
        ),
      get: (id) =>
        get<InventoryReservationDetailRecord>(
          `/api/admin/inventory/reservations/${encodeURIComponent(String(id))}`,
        ),
    },
    movements: {
      list: (params) =>
        get<PageResult<InventoryMovementRecord>>(
          '/api/admin/inventory/movements',
          pickQuery(params, movementQueryKeys),
        ),
    },
    documents: {
      list: (params) =>
        get<PageResult<InventoryDocumentSummaryRecord>>(
          '/api/admin/inventory/documents',
          pickQuery(params, documentQueryKeys),
        ),
      get: (id) => get<InventoryDocumentDetailRecord>(`/api/admin/inventory/documents/${encodeURIComponent(String(id))}`),
      create: (payload) => write<InventoryDocumentDetailRecord>('POST', '/api/admin/inventory/documents', payload),
      update: (id, payload) =>
        write<InventoryDocumentDetailRecord>(
          'PUT',
          `/api/admin/inventory/documents/${encodeURIComponent(String(id))}`,
          payload,
        ),
      post: (id) =>
        write<InventoryDocumentDetailRecord>('PUT', `/api/admin/inventory/documents/${encodeURIComponent(String(id))}/post`),
    },
  }
}

export const inventoryApi = createInventoryApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
