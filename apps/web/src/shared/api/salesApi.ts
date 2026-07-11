import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type {
  InventoryDirection,
  InventoryMovementType,
  InventoryQualityStatus,
  InventoryTrackingAllocationPayload,
} from './inventoryApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type SalesOrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_SHIPPED' | 'SHIPPED' | 'CLOSED' | 'CANCELLED'
export type SalesShipmentStatus = 'DRAFT' | 'POSTED'
export type SalesQuantityPayload = string
export type SalesUnitPricePayload = string

export interface SalesOrderListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  status?: SalesOrderStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  expectedDateFrom?: string | null
  expectedDateTo?: string | null
  page: number
  pageSize: number
}

export interface SalesShipmentListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  warehouseId?: ResourceId | null
  status?: SalesShipmentStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  orderId?: ResourceId | null
  page: number
  pageSize: number
}

export interface SalesOrderSummaryRecord {
  id: ResourceId
  orderNo: string
  customerId: ResourceId
  customerCode: string
  customerName: string
  orderDate: string
  expectedShipDate?: string | null
  status: SalesOrderStatus
  lineCount: number
  totalQuantity: number
  shippedQuantity: number
  remainingQuantity: number
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

export interface SalesOrderLineRecord {
  id: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  materialSpec?: string | null
  unitId: ResourceId
  unitName: string
  quantity: string
  shippedQuantity: number
  remainingQuantity: number
  reservationWarehouseId?: ResourceId | null
  reservationWarehouseName?: string | null
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  quantityOnHand?: string | number | null
  reservedQuantity?: string | number | null
  occupiedQuantity?: string | number | null
  availableQuantity?: string | number | null
  availableToPromiseQuantity?: string | number | null
  selectable?: boolean | null
  disabledReasonCode?: string | null
  disabledReason?: string | null
  maxSelectableQuantity?: string | number | null
  unitPrice: string
  expectedShipDate?: string | null
  remark?: string | null
}

export interface SalesShipmentSummaryRecord {
  id: ResourceId
  shipmentNo: string
  orderId: ResourceId
  orderNo: string
  customerId: ResourceId
  customerName: string
  warehouseId: ResourceId
  warehouseName: string
  businessDate: string
  status: SalesShipmentStatus
  lineCount: number
  totalQuantity: number
  remark?: string | null
  createdByName: string
  createdAt: string
  updatedAt: string
  postedByName?: string | null
  postedAt?: string | null
}

export interface SalesOrderDetailRecord extends SalesOrderSummaryRecord {
  lines: SalesOrderLineRecord[]
  shipments: SalesShipmentSummaryRecord[]
  inventoryMovements?: SalesInventoryMovementRecord[]
}

export interface SalesShipmentLineRecord {
  id: ResourceId
  lineNo: number
  orderLineId: ResourceId
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  orderedQuantity: number
  shippedQuantityBefore: number
  remainingQuantityBefore: number
  reservationWarehouseId?: ResourceId | null
  reservationWarehouseName?: string | null
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  quantityOnHand?: string | number | null
  reservedQuantity?: string | number | null
  occupiedQuantity?: string | number | null
  availableQuantity?: string | number | null
  availableToPromiseQuantity?: string | number | null
  selectable?: boolean | null
  disabledReasonCode?: string | null
  disabledReason?: string | null
  maxSelectableQuantity?: string | number | null
  quantity: string
  beforeQuantity?: number | null
  afterQuantity?: number | null
  remark?: string | null
}

export interface SalesInventoryMovementRecord {
  id: ResourceId
  movementNo: string
  movementType: InventoryMovementType
  direction: InventoryDirection
  warehouseName: string
  materialCode: string
  materialName: string
  quantity: number
  beforeQuantity: number
  afterQuantity: number
  businessDate: string
  operatorName: string
  occurredAt: string
}

export interface SalesShipmentDetailRecord extends SalesShipmentSummaryRecord {
  lines: SalesShipmentLineRecord[]
  orderSummary: SalesOrderSummaryRecord
  inventoryMovements: SalesInventoryMovementRecord[]
}

export interface SalesOrderLinePayload {
  lineNo: number
  materialId: ResourceId
  unitId?: ResourceId
  reservationWarehouseId: ResourceId
  quantity: SalesQuantityPayload
  unitPrice: SalesUnitPricePayload
  expectedShipDate?: string
  remark?: string
}

export interface SalesOrderPayload {
  customerId: ResourceId
  orderDate: string
  expectedShipDate?: string
  remark?: string
  lines: SalesOrderLinePayload[]
}

export interface SalesShipmentLinePayload {
  lineNo: number
  orderLineId: ResourceId
  materialId?: ResourceId
  unitId?: ResourceId
  quantity: SalesQuantityPayload
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string
}

export interface SalesShipmentPayload {
  warehouseId: ResourceId
  businessDate: string
  remark?: string
  lines: SalesShipmentLinePayload[]
}

export interface SalesApi {
  orders: {
    list(params: SalesOrderListParams): Promise<PageResult<SalesOrderSummaryRecord>>
    get(id: ResourceId): Promise<SalesOrderDetailRecord>
    create(payload: SalesOrderPayload): Promise<SalesOrderDetailRecord>
    update(id: ResourceId, payload: SalesOrderPayload): Promise<SalesOrderDetailRecord>
    confirm(id: ResourceId): Promise<SalesOrderDetailRecord>
    cancel(id: ResourceId): Promise<SalesOrderDetailRecord>
    close(id: ResourceId): Promise<SalesOrderDetailRecord>
  }
  shipments: {
    list(params: SalesShipmentListParams): Promise<PageResult<SalesShipmentSummaryRecord>>
    get(id: ResourceId): Promise<SalesShipmentDetailRecord>
    create(orderId: ResourceId, payload: SalesShipmentPayload): Promise<SalesShipmentDetailRecord>
    update(id: ResourceId, payload: SalesShipmentPayload): Promise<SalesShipmentDetailRecord>
    post(id: ResourceId): Promise<SalesShipmentDetailRecord>
  }
}

export interface SalesApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createSalesApi(options: SalesApiOptions = {}): SalesApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const orderQueryKeys = [
    'keyword',
    'customerId',
    'status',
    'dateFrom',
    'dateTo',
    'expectedDateFrom',
    'expectedDateTo',
    'page',
    'pageSize',
  ] as const
  const shipmentQueryKeys = [
    'keyword',
    'customerId',
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
    `/api/admin/sales/orders${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const shipmentPath = (id?: ResourceId) =>
    `/api/admin/sales/shipments${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`

  return {
    orders: {
      list: (params) =>
        get<PageResult<SalesOrderSummaryRecord>>('/api/admin/sales/orders', pickQuery(params, orderQueryKeys)),
      get: (id) => get<SalesOrderDetailRecord>(orderPath(id)),
      create: (payload) => write<SalesOrderDetailRecord>('POST', orderPath(), payload),
      update: (id, payload) => write<SalesOrderDetailRecord>('PUT', orderPath(id), payload),
      confirm: (id) => write<SalesOrderDetailRecord>('PUT', `${orderPath(id)}/confirm`),
      cancel: (id) => write<SalesOrderDetailRecord>('PUT', `${orderPath(id)}/cancel`),
      close: (id) => write<SalesOrderDetailRecord>('PUT', `${orderPath(id)}/close`),
    },
    shipments: {
      list: (params) =>
        get<PageResult<SalesShipmentSummaryRecord>>(
          '/api/admin/sales/shipments',
          pickQuery(params, shipmentQueryKeys),
        ),
      get: (id) => get<SalesShipmentDetailRecord>(shipmentPath(id)),
      create: (orderId, payload) => write<SalesShipmentDetailRecord>('POST', `${orderPath(orderId)}/shipments`, payload),
      update: (id, payload) => write<SalesShipmentDetailRecord>('PUT', shipmentPath(id), payload),
      post: (id) => write<SalesShipmentDetailRecord>('PUT', `${shipmentPath(id)}/post`),
    },
  }
}

export const salesApi = createSalesApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
