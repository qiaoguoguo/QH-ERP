import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type ReversalDecimal = string
export type ReversalMoney = string
export type ReversalRouteValue = string | number | boolean
export type ReversalStatus = 'DRAFT' | 'POSTED' | 'CANCELLED'
export type ReversalTraceDirection = 'SOURCE_TO_REVERSE' | 'REVERSE_TO_SOURCE'

export interface ReversalSourceView {
  sourceType: string
  sourceId?: ResourceId
  sourceLineId?: ResourceId
  sourceNo?: string
  lineNo?: number
  businessDate?: string
  status?: string
  quantity?: ReversalDecimal
  amount?: ReversalMoney
  canViewSource: boolean
  restricted: boolean
  restrictedMessage?: string
  resourceRouteName?: string
  resourceRouteParams?: Record<string, ReversalRouteValue>
  resourceRouteQuery?: Record<string, ReversalRouteValue>
}

export interface ReversalTraceRecord {
  traceKey: string
  direction: ReversalTraceDirection
  source: ReversalSourceView
  reverse: ReversalSourceView
  inventoryMovementId?: ResourceId
  settlementAdjustmentId?: ResourceId
  costRecordId?: ResourceId
  businessDate: string
  quantity?: ReversalDecimal
  amount?: ReversalMoney
  status: string
  canViewResource: boolean
  restricted: boolean
  restrictedMessage?: string
  resourceRouteName?: string
  resourceRouteParams?: Record<string, ReversalRouteValue>
  resourceRouteQuery?: Record<string, ReversalRouteValue>
}

export interface ReversalDocumentLine {
  id: ResourceId
  lineNo: number
  sourceLineId?: ResourceId
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  returnedQuantityBefore?: ReversalDecimal
  returnableQuantityBefore?: ReversalDecimal
  quantity: ReversalDecimal
  unitPrice?: ReversalMoney
  amount?: ReversalMoney
  reason?: string
  stockMovementId?: ResourceId
  costRecordId?: ResourceId
  source: ReversalSourceView
}

export interface SalesReturnSummary {
  id: ResourceId
  returnNo: string
  customerId: ResourceId
  customerName: string
  warehouseId: ResourceId
  warehouseName: string
  businessDate: string
  status: ReversalStatus
  totalQuantity: ReversalDecimal
  totalAmount: ReversalMoney
  source: ReversalSourceView
  createdAt: string
  updatedAt: string
}

export interface SalesReturnDetail extends SalesReturnSummary {
  clientRequestId?: string
  remark?: string
  lines: ReversalDocumentLine[]
  traces: ReversalTraceRecord[]
}

export interface SalesReturnSourceLine {
  shipmentLineId: ResourceId
  salesOrderLineId?: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  shippedQuantity: ReversalDecimal
  returnedQuantity: ReversalDecimal
  returnableQuantity: ReversalDecimal
  unitPrice: ReversalMoney
  returnableAmount: ReversalMoney
}

export interface SalesReturnSource {
  shipmentId: ResourceId
  shipmentNo: string
  customerId: ResourceId
  customerName: string
  warehouseId: ResourceId
  warehouseName: string
  businessDate: string
  status: 'POSTED'
  lines: SalesReturnSourceLine[]
}

export interface SalesReturnListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  warehouseId?: ResourceId | null
  status?: ReversalStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  page: number
  pageSize: number
}

export interface SalesReturnSourceListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  warehouseId?: ResourceId | null
  dateFrom?: string | null
  dateTo?: string | null
  page: number
  pageSize: number
}

export interface ReversalTraceListParams {
  sourceType: string
  sourceId: ResourceId
  sourceLineId?: ResourceId | null
  direction?: ReversalTraceDirection | null
  includeRestricted?: boolean | null
}

export interface ReversalDocumentLinePayload {
  sourceShipmentLineId: ResourceId
  quantity: ReversalDecimal
  reason?: string
}

export interface ReversalDocumentPayload {
  sourceShipmentId: ResourceId
  businessDate: string
  clientRequestId: string
  remark?: string
  lines: ReversalDocumentLinePayload[]
}

export interface ReturnRefundReversalApi {
  salesReturns: {
    list(params: SalesReturnListParams): Promise<PageResult<SalesReturnSummary>>
    get(id: ResourceId): Promise<SalesReturnDetail>
    create(payload: ReversalDocumentPayload): Promise<SalesReturnDetail>
    update(id: ResourceId, payload: ReversalDocumentPayload): Promise<SalesReturnDetail>
    post(id: ResourceId): Promise<SalesReturnDetail>
    cancel(id: ResourceId): Promise<SalesReturnDetail>
  }
  salesReturnSources: {
    list(params: SalesReturnSourceListParams): Promise<PageResult<SalesReturnSource>>
  }
  traces: {
    list(params: ReversalTraceListParams): Promise<ReversalTraceRecord[]>
  }
}

export interface ReturnRefundReversalApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createReturnRefundReversalApi(options: ReturnRefundReversalApiOptions = {}): ReturnRefundReversalApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const salesReturnQueryKeys = [
    'keyword',
    'customerId',
    'warehouseId',
    'status',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const salesReturnSourceQueryKeys = [
    'keyword',
    'customerId',
    'warehouseId',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const traceQueryKeys = ['sourceType', 'sourceId', 'sourceLineId', 'direction', 'includeRestricted'] as const

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

  const salesReturnPath = (id?: ResourceId) =>
    `/api/admin/sales/returns${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`

  return {
    salesReturns: {
      list: (params) =>
        get<PageResult<SalesReturnSummary>>('/api/admin/sales/returns', pickQuery(params, salesReturnQueryKeys)),
      get: (id) => get<SalesReturnDetail>(salesReturnPath(id)),
      create: (payload) => write<SalesReturnDetail>('POST', salesReturnPath(), payload),
      update: (id, payload) => write<SalesReturnDetail>('PUT', salesReturnPath(id), payload),
      post: (id) => write<SalesReturnDetail>('PUT', `${salesReturnPath(id)}/post`),
      cancel: (id) => write<SalesReturnDetail>('PUT', `${salesReturnPath(id)}/cancel`),
    },
    salesReturnSources: {
      list: (params) =>
        get<PageResult<SalesReturnSource>>('/api/admin/sales/return-sources', pickQuery(params, salesReturnSourceQueryKeys)),
    },
    traces: {
      list: (params) =>
        get<ReversalTraceRecord[]>('/api/admin/reversal-traces', pickQuery(params, traceQueryKeys)),
    },
  }
}

export const returnRefundReversalApi = createReturnRefundReversalApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
