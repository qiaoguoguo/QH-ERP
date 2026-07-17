import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type { InventoryTrackingAllocationPayload } from './inventoryApi'
import type { ProductionOwnershipType, ResourceId } from './projectProductionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type OutsourcingOrderStatus = 'DRAFT' | 'RELEASED' | 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED' | 'CANCELLED' | string
export type OutsourcingDocumentStatus = 'DRAFT' | 'POSTED' | 'CANCELLED' | string

export interface OutsourcingDocumentVersionPayload {
  version: number
  idempotencyKey: string
}

export interface OutsourcingPageParams {
  page: number
  pageSize: number
}

export interface OutsourcingOrderListParams extends OutsourcingPageParams {
  keyword?: string | null
  projectId?: ResourceId | null | ''
  supplierId?: ResourceId | null | ''
  productMaterialId?: ResourceId | null | ''
  status?: OutsourcingOrderStatus | null | ''
  plannedDateFrom?: string | null
  plannedDateTo?: string | null
}

export interface OutsourcingTraceLink {
  label: string
  routeName?: string | null
  routePath?: string | null
  targetId?: ResourceId | null
  restricted?: boolean | null
  restrictedReason?: string | null
}

export interface OutsourcingOrderSummaryRecord {
  id: ResourceId
  orderNo: string
  ownershipType: ProductionOwnershipType
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  supplierId: ResourceId
  supplierCode?: string | null
  supplierName: string
  productMaterialId: ResourceId
  productMaterialCode: string
  productMaterialName: string
  bomId?: ResourceId | null
  bomCode?: string | null
  bomVersionCode?: string | null
  unitName?: string | null
  plannedQuantity: string
  issuedQuantity: string
  receivedQuantity: string
  acceptedQuantity?: string | null
  rejectedQuantity?: string | null
  issueWarehouseId?: ResourceId | null
  issueWarehouseName?: string | null
  receiptWarehouseId?: ResourceId | null
  receiptWarehouseName?: string | null
  plannedIssueDate?: string | null
  plannedReceiptDate?: string | null
  plannedStartDate?: string | null
  plannedFinishDate?: string | null
  status: OutsourcingOrderStatus
  statusName?: string | null
  sourceMrpRunId?: ResourceId | null
  sourceMrpSuggestionId?: ResourceId | null
  sourceMrpRequirementLineId?: ResourceId | null
  sourceSuggestionNo?: string | null
  costVisible?: boolean | null
  costRestrictedReason?: string | null
  actionDisabledReason?: string | null
  allowedActions?: string[]
  remark?: string | null
  createdByName?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  version: number
}

export interface OutsourcingOrderMaterialRecord {
  id: ResourceId
  lineNo: number
  bomItemId?: ResourceId | null
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName?: string | null
  requiredQuantity: string
  issuedQuantity?: string | null
  lossRate?: string | null
}

export interface OutsourcingDocumentSummaryRecord {
  id: ResourceId
  documentNo?: string | null
  issueNo?: string | null
  receiptNo?: string | null
  outsourcingOrderId: ResourceId
  status: OutsourcingDocumentStatus
  businessDate: string
  lineCount?: number | null
  actionDisabledReason?: string | null
  allowedActions?: string[]
  version?: number | null
}

export interface OutsourcingMaterialIssueLineRecord {
  id?: ResourceId | null
  orderMaterialId: ResourceId
  lineNo: number
  materialId?: ResourceId | null
  materialCode?: string | null
  materialName?: string | null
  quantity: string
  ownershipType: ProductionOwnershipType
  projectId?: ResourceId | null
  projectNo?: string | null
  costLayerId?: ResourceId | null
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string | null
}

export interface OutsourcingReceiptLineRecord {
  id?: ResourceId | null
  lineNo: number
  acceptedQuantity: string
  rejectedQuantity: string
  provisionalUnitCost?: string | null
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string | null
}

export interface OutsourcingDocumentDetailRecord extends OutsourcingDocumentSummaryRecord {
  warehouseId?: ResourceId | null
  warehouseName?: string | null
  receiptWarehouseId?: ResourceId | null
  receiptWarehouseName?: string | null
  remark?: string | null
  lines: Array<OutsourcingMaterialIssueLineRecord | OutsourcingReceiptLineRecord>
}

export interface OutsourcingOrderDetailRecord extends OutsourcingOrderSummaryRecord {
  materials: OutsourcingOrderMaterialRecord[]
  materialIssues: OutsourcingDocumentSummaryRecord[]
  receipts: OutsourcingDocumentSummaryRecord[]
  traceLinks?: OutsourcingTraceLink[]
}

export interface OutsourcingOrderPayload {
  ownershipType: ProductionOwnershipType
  projectId?: ResourceId | null
  supplierId: ResourceId
  productMaterialId: ResourceId
  bomId: ResourceId
  plannedQuantity: string
  issueWarehouseId: ResourceId
  receiptWarehouseId: ResourceId
  plannedIssueDate: string
  plannedReceiptDate: string
  plannedStartDate?: string
  plannedFinishDate?: string
  remark?: string
  idempotencyKey: string
}

export interface OutsourcingOrderUpdatePayload extends Omit<OutsourcingOrderPayload, 'idempotencyKey'> {
  version: number
  idempotencyKey: string
}

export interface OutsourcingMaterialIssueLinePayload {
  orderMaterialId: ResourceId
  lineNo: number
  quantity: string
  ownershipType: ProductionOwnershipType
  projectId?: ResourceId | null
  costLayerId?: ResourceId | null
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string
}

export interface OutsourcingMaterialIssuePayload extends OutsourcingDocumentVersionPayload {
  businessDate: string
  warehouseId: ResourceId
  remark?: string
  lines: OutsourcingMaterialIssueLinePayload[]
}

export interface OutsourcingReceiptLinePayload {
  lineNo: number
  acceptedQuantity: string
  rejectedQuantity: string
  provisionalUnitCost?: string
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string
}

export interface OutsourcingReceiptPayload extends OutsourcingDocumentVersionPayload {
  businessDate: string
  receiptWarehouseId: ResourceId
  remark?: string
  lines: OutsourcingReceiptLinePayload[]
}

export interface ProductionOutsourcingApi {
  orders: {
    list(params: OutsourcingOrderListParams): Promise<PageResult<OutsourcingOrderSummaryRecord>>
    get(id: ResourceId): Promise<OutsourcingOrderDetailRecord>
    create(payload: OutsourcingOrderPayload): Promise<OutsourcingOrderDetailRecord>
    update(id: ResourceId, payload: OutsourcingOrderUpdatePayload): Promise<OutsourcingOrderDetailRecord>
    release(id: ResourceId, payload: OutsourcingDocumentVersionPayload): Promise<OutsourcingOrderDetailRecord>
    close(id: ResourceId, payload: OutsourcingDocumentVersionPayload): Promise<OutsourcingOrderDetailRecord>
    cancel(id: ResourceId, payload: OutsourcingDocumentVersionPayload): Promise<OutsourcingOrderDetailRecord>
  }
  materialIssues: {
    list(orderId: ResourceId, params: OutsourcingPageParams): Promise<PageResult<OutsourcingDocumentSummaryRecord>>
    get(orderId: ResourceId, id: ResourceId): Promise<OutsourcingDocumentDetailRecord>
    create(orderId: ResourceId, payload: OutsourcingMaterialIssuePayload): Promise<OutsourcingDocumentDetailRecord>
    update(orderId: ResourceId, id: ResourceId, payload: OutsourcingMaterialIssuePayload): Promise<OutsourcingDocumentDetailRecord>
    post(orderId: ResourceId, id: ResourceId, payload: OutsourcingDocumentVersionPayload): Promise<OutsourcingDocumentDetailRecord>
    cancel(orderId: ResourceId, id: ResourceId, payload: OutsourcingDocumentVersionPayload): Promise<OutsourcingDocumentDetailRecord>
  }
  receipts: {
    list(orderId: ResourceId, params: OutsourcingPageParams): Promise<PageResult<OutsourcingDocumentSummaryRecord>>
    get(orderId: ResourceId, id: ResourceId): Promise<OutsourcingDocumentDetailRecord>
    create(orderId: ResourceId, payload: OutsourcingReceiptPayload): Promise<OutsourcingDocumentDetailRecord>
    update(orderId: ResourceId, id: ResourceId, payload: OutsourcingReceiptPayload): Promise<OutsourcingDocumentDetailRecord>
    post(orderId: ResourceId, id: ResourceId, payload: OutsourcingDocumentVersionPayload): Promise<OutsourcingDocumentDetailRecord>
    cancel(orderId: ResourceId, id: ResourceId, payload: OutsourcingDocumentVersionPayload): Promise<OutsourcingDocumentDetailRecord>
  }
}

export interface ProductionOutsourcingApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createProductionOutsourcingApi(options: ProductionOutsourcingApiOptions = {}): ProductionOutsourcingApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const orderQueryKeys = [
    'keyword',
    'projectId',
    'supplierId',
    'productMaterialId',
    'status',
    'plannedDateFrom',
    'plannedDateTo',
    'page',
    'pageSize',
  ] as const
  const pageKeys = ['page', 'pageSize'] as const

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

  const assertIdempotencyKey = (payload: { idempotencyKey?: string }) => {
    if (typeof payload.idempotencyKey !== 'string' || payload.idempotencyKey.trim() === '') {
      throw new Error('幂等键不能为空')
    }
  }
  const getCsrf = () => request<CsrfToken>('/api/auth/csrf', { method: 'GET' })
  const get = <T>(path: string, query?: object) => request<T>(path, { method: 'GET' }, query)
  const write = async <T>(method: 'POST' | 'PUT', path: string, body?: object) => {
    const csrf = await getCsrf()
    const headers: Record<string, string> = { [csrf.headerName]: csrf.token }
    const init: RequestInit = { headers, method }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }
    return request<T>(path, init)
  }
  const writeAction = async <T>(method: 'POST' | 'PUT', path: string, payload: { idempotencyKey?: string }) => {
    assertIdempotencyKey(payload)
    return write<T>(method, path, payload)
  }

  const orderPath = (id?: ResourceId) =>
    `/api/admin/production/outsourcing-orders${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const issuePath = (orderId: ResourceId, id?: ResourceId) =>
    `${orderPath(orderId)}/material-issues${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const receiptPath = (orderId: ResourceId, id?: ResourceId) =>
    `${orderPath(orderId)}/receipts${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`

  return {
    orders: {
      list: (params) => get<PageResult<OutsourcingOrderSummaryRecord>>(orderPath(), pickQuery(params, orderQueryKeys)),
      get: (id) => get<OutsourcingOrderDetailRecord>(orderPath(id)),
      create: (payload) => writeAction<OutsourcingOrderDetailRecord>('POST', orderPath(), payload),
      update: (id, payload) => writeAction<OutsourcingOrderDetailRecord>('PUT', orderPath(id), payload),
      release: (id, payload) => writeAction<OutsourcingOrderDetailRecord>('PUT', `${orderPath(id)}/release`, payload),
      close: (id, payload) => writeAction<OutsourcingOrderDetailRecord>('PUT', `${orderPath(id)}/close`, payload),
      cancel: (id, payload) => writeAction<OutsourcingOrderDetailRecord>('PUT', `${orderPath(id)}/cancel`, payload),
    },
    materialIssues: {
      list: (orderId, params) => get<PageResult<OutsourcingDocumentSummaryRecord>>(issuePath(orderId), pickQuery(params, pageKeys)),
      get: (orderId, id) => get<OutsourcingDocumentDetailRecord>(issuePath(orderId, id)),
      create: (orderId, payload) => writeAction<OutsourcingDocumentDetailRecord>('POST', issuePath(orderId), payload),
      update: (orderId, id, payload) => writeAction<OutsourcingDocumentDetailRecord>('PUT', issuePath(orderId, id), payload),
      post: (orderId, id, payload) => writeAction<OutsourcingDocumentDetailRecord>('PUT', `${issuePath(orderId, id)}/post`, payload),
      cancel: (orderId, id, payload) => writeAction<OutsourcingDocumentDetailRecord>('PUT', `${issuePath(orderId, id)}/cancel`, payload),
    },
    receipts: {
      list: (orderId, params) => get<PageResult<OutsourcingDocumentSummaryRecord>>(receiptPath(orderId), pickQuery(params, pageKeys)),
      get: (orderId, id) => get<OutsourcingDocumentDetailRecord>(receiptPath(orderId, id)),
      create: (orderId, payload) => writeAction<OutsourcingDocumentDetailRecord>('POST', receiptPath(orderId), payload),
      update: (orderId, id, payload) => writeAction<OutsourcingDocumentDetailRecord>('PUT', receiptPath(orderId, id), payload),
      post: (orderId, id, payload) => writeAction<OutsourcingDocumentDetailRecord>('PUT', `${receiptPath(orderId, id)}/post`, payload),
      cancel: (orderId, id, payload) => writeAction<OutsourcingDocumentDetailRecord>('PUT', `${receiptPath(orderId, id)}/cancel`, payload),
    },
  }
}

export const productionOutsourcingApi = createProductionOutsourcingApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
