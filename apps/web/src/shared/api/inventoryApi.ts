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
export type InventoryTrackingMethod = 'NONE' | 'BATCH' | 'SERIAL'
export type InventoryStockStatus = 'IN_STOCK' | 'RESERVED' | 'OCCUPIED' | 'OUTBOUND' | 'CANCELLED'

export interface InventoryBalanceListParams {
  keyword?: string
  warehouseId?: ResourceId
  materialId?: ResourceId
  materialType?: string
  qualityStatus?: InventoryQualityStatus
  trackingMethod?: InventoryTrackingMethod
  batchId?: ResourceId
  batchNo?: string
  serialId?: ResourceId
  serialNo?: string
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
  trackingMethod?: InventoryTrackingMethod
  batchId?: ResourceId
  batchNo?: string
  serialId?: ResourceId
  serialNo?: string
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

export interface InventoryBatchListParams {
  keyword?: string
  materialId?: ResourceId
  warehouseId?: ResourceId
  qualityStatus?: InventoryQualityStatus
  batchNo?: string
  sourceType?: string
  sourceId?: ResourceId
  onlyAvailable?: boolean
  page: number
  pageSize: number
}

export interface InventorySerialListParams {
  keyword?: string
  materialId?: ResourceId
  warehouseId?: ResourceId
  qualityStatus?: InventoryQualityStatus
  serialNo?: string
  batchId?: ResourceId
  sourceType?: string
  sourceId?: ResourceId
  onlyAvailable?: boolean
  page: number
  pageSize: number
}

export interface InventoryTrackingQualityStatusSummary {
  qualityStatus: InventoryQualityStatus
  qualityStatusName: string
  quantityOnHand: number | string
  availableQuantity?: number | string
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
  trackingMethod?: InventoryTrackingMethod
  trackingMethodName?: string | null
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  traceableQuantity?: number | string | null
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

export interface InventoryBatchSummaryRecord {
  id: ResourceId
  batchNo: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  sourceType?: string | null
  sourceId?: ResourceId | null
  sourceLineId?: ResourceId | null
  sourceDocumentNo?: string | null
  businessDate?: string | null
  quantityOnHand: number | string
  availableQuantity: number | string
  qualityStatusSummary?: InventoryTrackingQualityStatusSummary[]
  updatedAt: string
}

export interface InventoryBatchDetailRecord extends InventoryBatchSummaryRecord {
  remark?: string | null
  createdByName?: string | null
  createdAt?: string | null
}

export interface InventorySerialSummaryRecord {
  id: ResourceId
  serialNo: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  batchId?: ResourceId | null
  batchNo?: string | null
  warehouseId?: ResourceId | null
  warehouseName?: string | null
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  stockStatus?: InventoryStockStatus | null
  stockStatusName?: string | null
  sourceType?: string | null
  sourceId?: ResourceId | null
  sourceLineId?: ResourceId | null
  sourceDocumentNo?: string | null
  updatedAt: string
}

export interface InventorySerialDetailRecord extends InventorySerialSummaryRecord {
  remark?: string | null
  createdByName?: string | null
  createdAt?: string | null
}

export interface InventoryTrackingAllocationPayload {
  batchId?: ResourceId
  batchNo?: string
  serialId?: ResourceId
  serialNo?: string
  quantity: InventoryQuantityPayload
  qualityStatus?: InventoryQualityStatus
  sourceAllocationId?: ResourceId
}

export interface InventoryTraceSubjectRecord {
  trackingMethod: InventoryTrackingMethod
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  materialId?: ResourceId | null
  materialCode?: string | null
  materialName?: string | null
  sourceDocumentNo?: string | null
}

export interface InventoryTraceBalanceRecord {
  warehouseId?: ResourceId | null
  warehouseName: string
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  quantityOnHand: number | string
  availableQuantity?: number | string | null
  reservedQuantity?: number | string | null
  occupiedQuantity?: number | string | null
}

export interface InventoryTraceNodeRecord {
  nodeType?: string | null
  nodeTypeName?: string | null
  documentType?: string | null
  documentId?: ResourceId | null
  documentNo?: string | null
  lineId?: ResourceId | null
  businessDate?: string | null
  direction?: InventoryDirection | string | null
  quantity?: number | string | null
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  warehouseName?: string | null
  operatorName?: string | null
  routeName?: string | null
  permissionRestricted?: boolean
}

export interface InventoryTraceDetailRecord {
  subject: InventoryTraceSubjectRecord
  currentBalances: InventoryTraceBalanceRecord[]
  activeReservations: InventoryTraceNodeRecord[]
  sourceRecords: InventoryTraceNodeRecord[]
  qualityEvents: InventoryTraceNodeRecord[]
  outboundRecords: InventoryTraceNodeRecord[]
  returnRecords: InventoryTraceNodeRecord[]
  movements: InventoryTraceNodeRecord[]
  restrictedSources: InventoryTraceNodeRecord[]
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
  trackingMethod?: InventoryTrackingMethod
  trackingMethodName?: string | null
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  quantity: number | string
  beforeQuantity: number | string
  afterQuantity: number | string
  sourceType: string
  sourceId: ResourceId
  sourceLineId?: ResourceId | null
  sourceDocumentNo?: string | null
  targetDocumentNo?: string | null
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
  trackingAllocations?: InventoryTrackingAllocationPayload[]
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
  batches: {
    list(params: InventoryBatchListParams): Promise<PageResult<InventoryBatchSummaryRecord>>
    get(id: ResourceId): Promise<InventoryBatchDetailRecord>
  }
  serials: {
    list(params: InventorySerialListParams): Promise<PageResult<InventorySerialSummaryRecord>>
    get(id: ResourceId): Promise<InventorySerialDetailRecord>
  }
  reservations: {
    list(params: InventoryReservationListParams): Promise<PageResult<InventoryReservationSummaryRecord>>
    get(id: ResourceId): Promise<InventoryReservationDetailRecord>
  }
  traces: {
    getBatchTrace(id: ResourceId): Promise<InventoryTraceDetailRecord>
    getSerialTrace(id: ResourceId): Promise<InventoryTraceDetailRecord>
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
    'trackingMethod',
    'batchId',
    'batchNo',
    'serialId',
    'serialNo',
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
    'trackingMethod',
    'batchId',
    'batchNo',
    'serialId',
    'serialNo',
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
  const batchQueryKeys = [
    'keyword',
    'materialId',
    'warehouseId',
    'qualityStatus',
    'batchNo',
    'sourceType',
    'sourceId',
    'onlyAvailable',
    'page',
    'pageSize',
  ] as const
  const serialQueryKeys = [
    'keyword',
    'materialId',
    'warehouseId',
    'qualityStatus',
    'serialNo',
    'batchId',
    'sourceType',
    'sourceId',
    'onlyAvailable',
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
    batches: {
      list: (params) =>
        get<PageResult<InventoryBatchSummaryRecord>>(
          '/api/admin/inventory/batches',
          pickQuery(params, batchQueryKeys),
        ),
      get: (id) => get<InventoryBatchDetailRecord>(`/api/admin/inventory/batches/${encodeURIComponent(String(id))}`),
    },
    serials: {
      list: (params) =>
        get<PageResult<InventorySerialSummaryRecord>>(
          '/api/admin/inventory/serials',
          pickQuery(params, serialQueryKeys),
        ),
      get: (id) => get<InventorySerialDetailRecord>(`/api/admin/inventory/serials/${encodeURIComponent(String(id))}`),
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
    traces: {
      getBatchTrace: (id) =>
        get<InventoryTraceDetailRecord>(`/api/admin/inventory/traces/batches/${encodeURIComponent(String(id))}`),
      getSerialTrace: (id) =>
        get<InventoryTraceDetailRecord>(`/api/admin/inventory/traces/serials/${encodeURIComponent(String(id))}`),
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
