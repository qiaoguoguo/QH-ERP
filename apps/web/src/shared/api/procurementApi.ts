import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type { InventoryDirection, InventoryMovementType, InventoryTrackingAllocationPayload } from './inventoryApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type PurchaseOrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CLOSED' | 'CANCELLED'
export type PurchaseReceiptStatus = 'DRAFT' | 'POSTED'
export type PurchaseInTransitStatus = 'NORMAL' | 'DUE_SOON' | 'OVERDUE' | 'NOT_COUNTED'
export type ProcurementQuantityPayload = string
export type ProcurementUnitPricePayload = string

export interface PurchaseOrderListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  status?: PurchaseOrderStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  expectedDateFrom?: string | null
  expectedDateTo?: string | null
  page: number
  pageSize: number
}

export interface PurchaseReceiptListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  warehouseId?: ResourceId | null
  status?: PurchaseReceiptStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  orderId?: ResourceId | null
  page: number
  pageSize: number
}

export interface PurchaseOrderSummaryRecord {
  id: ResourceId
  orderNo: string
  supplierId: ResourceId
  supplierCode: string
  supplierName: string
  orderDate: string
  expectedArrivalDate?: string | null
  status: PurchaseOrderStatus
  lineCount: number
  totalQuantity: number
  receivedQuantity: number
  remainingQuantity: number
  inTransitQuantity?: string | number | null
  inTransitStatus?: PurchaseInTransitStatus | string | null
  inTransitStatusName?: string | null
  remark?: string | null
  createdByName: string
  createdAt: string
  updatedAt: string
  confirmedByName?: string | null
  confirmedAt?: string | null
  cancelledByName?: string | null
  cancelledAt?: string | null
  closedByName?: string | null
  closedAt?: string | null
}

export interface PurchaseOrderLineRecord {
  id: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  materialSpec?: string | null
  unitId: ResourceId
  unitName: string
  quantity: number
  receivedQuantity: number
  remainingQuantity: number
  inTransitQuantity?: string | number | null
  inTransitStatus?: PurchaseInTransitStatus | string | null
  inTransitStatusName?: string | null
  unitPrice: number
  expectedArrivalDate?: string | null
  remark?: string | null
}

export interface PurchaseReceiptSummaryRecord {
  id: ResourceId
  receiptNo: string
  orderId: ResourceId
  orderNo: string
  supplierId: ResourceId
  supplierName: string
  warehouseId: ResourceId
  warehouseName: string
  businessDate: string
  status: PurchaseReceiptStatus
  lineCount: number
  totalQuantity: number
  remark?: string | null
  createdByName: string
  createdAt: string
  updatedAt: string
  postedByName?: string | null
  postedAt?: string | null
}

export interface PurchaseOrderDetailRecord extends PurchaseOrderSummaryRecord {
  lines: PurchaseOrderLineRecord[]
  receipts: PurchaseReceiptSummaryRecord[]
}

export interface PurchaseReceiptLineRecord {
  id: ResourceId
  lineNo: number
  orderLineId: ResourceId
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  orderedQuantity: number
  receivedQuantityBefore: number
  remainingQuantityBefore: number
  inTransitQuantity?: string | number | null
  inTransitStatus?: PurchaseInTransitStatus | string | null
  inTransitStatusName?: string | null
  quantity: number
  beforeQuantity?: number | null
  afterQuantity?: number | null
  remark?: string | null
}

export interface PurchaseReceiptInventoryMovementRecord {
  id: ResourceId
  movementNo: string
  movementType: InventoryMovementType
  direction: InventoryDirection
  warehouseName: string
  materialCode: string
  materialName: string
  quantity: number
  beforeQuantity?: number | null
  afterQuantity?: number | null
  businessDate?: string | null
  operatorName?: string | null
  occurredAt?: string | null
}

export interface PurchaseReceiptDetailRecord extends PurchaseReceiptSummaryRecord {
  lines: PurchaseReceiptLineRecord[]
  orderSummary: PurchaseOrderSummaryRecord
  inventoryMovements?: PurchaseReceiptInventoryMovementRecord[]
}

export interface PurchaseOrderLinePayload {
  lineNo: number
  materialId: ResourceId
  unitId?: ResourceId
  quantity: ProcurementQuantityPayload
  unitPrice: ProcurementUnitPricePayload
  expectedArrivalDate?: string
  remark?: string
}

export interface PurchaseOrderPayload {
  supplierId: ResourceId
  orderDate: string
  expectedArrivalDate?: string
  remark?: string
  lines: PurchaseOrderLinePayload[]
}

export interface PurchaseReceiptLinePayload {
  lineNo: number
  orderLineId: ResourceId
  materialId?: ResourceId
  unitId?: ResourceId
  quantity: ProcurementQuantityPayload
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string
}

export interface PurchaseReceiptPayload {
  warehouseId: ResourceId
  businessDate: string
  remark?: string
  lines: PurchaseReceiptLinePayload[]
}

export interface ProcurementApi {
  orders: {
    list(params: PurchaseOrderListParams): Promise<PageResult<PurchaseOrderSummaryRecord>>
    get(id: ResourceId): Promise<PurchaseOrderDetailRecord>
    create(payload: PurchaseOrderPayload): Promise<PurchaseOrderDetailRecord>
    update(id: ResourceId, payload: PurchaseOrderPayload): Promise<PurchaseOrderDetailRecord>
    confirm(id: ResourceId): Promise<PurchaseOrderDetailRecord>
    cancel(id: ResourceId): Promise<PurchaseOrderDetailRecord>
    close(id: ResourceId): Promise<PurchaseOrderDetailRecord>
  }
  receipts: {
    list(params: PurchaseReceiptListParams): Promise<PageResult<PurchaseReceiptSummaryRecord>>
    get(id: ResourceId): Promise<PurchaseReceiptDetailRecord>
    create(orderId: ResourceId, payload: PurchaseReceiptPayload): Promise<PurchaseReceiptDetailRecord>
    update(id: ResourceId, payload: PurchaseReceiptPayload): Promise<PurchaseReceiptDetailRecord>
    post(id: ResourceId): Promise<PurchaseReceiptDetailRecord>
  }
}

export interface ProcurementApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createProcurementApi(options: ProcurementApiOptions = {}): ProcurementApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const orderQueryKeys = [
    'keyword',
    'supplierId',
    'status',
    'dateFrom',
    'dateTo',
    'expectedDateFrom',
    'expectedDateTo',
    'page',
    'pageSize',
  ] as const
  const receiptQueryKeys = [
    'keyword',
    'supplierId',
    'warehouseId',
    'status',
    'dateFrom',
    'dateTo',
    'orderId',
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

  const orderPath = (id?: ResourceId) =>
    `/api/admin/procurement/orders${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const receiptPath = (id?: ResourceId) =>
    `/api/admin/procurement/receipts${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`

  return {
    orders: {
      list: (params) =>
        get<PageResult<PurchaseOrderSummaryRecord>>(
          '/api/admin/procurement/orders',
          pickQuery(params, orderQueryKeys),
        ),
      get: (id) => get<PurchaseOrderDetailRecord>(orderPath(id)),
      create: (payload) => write<PurchaseOrderDetailRecord>('POST', orderPath(), payload),
      update: (id, payload) => write<PurchaseOrderDetailRecord>('PUT', orderPath(id), payload),
      confirm: (id) => write<PurchaseOrderDetailRecord>('PUT', `${orderPath(id)}/confirm`),
      cancel: (id) => write<PurchaseOrderDetailRecord>('PUT', `${orderPath(id)}/cancel`),
      close: (id) => write<PurchaseOrderDetailRecord>('PUT', `${orderPath(id)}/close`),
    },
    receipts: {
      list: (params) =>
        get<PageResult<PurchaseReceiptSummaryRecord>>(
          '/api/admin/procurement/receipts',
          pickQuery(params, receiptQueryKeys),
        ),
      get: (id) => get<PurchaseReceiptDetailRecord>(receiptPath(id)),
      create: (orderId, payload) =>
        write<PurchaseReceiptDetailRecord>('POST', `${orderPath(orderId)}/receipts`, payload),
      update: (id, payload) => write<PurchaseReceiptDetailRecord>('PUT', receiptPath(id), payload),
      post: (id) => write<PurchaseReceiptDetailRecord>('PUT', `${receiptPath(id)}/post`),
    },
  }
}

export const procurementApi = createProcurementApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
