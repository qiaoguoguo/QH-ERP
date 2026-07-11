import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type { InventoryQualityStatus, InventoryTrackingAllocationPayload } from './inventoryApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type ProductionWorkOrderStatus = 'DRAFT' | 'RELEASED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type ProductionDocumentStatus = 'DRAFT' | 'POSTED'

export interface ProductionWorkOrderListParams {
  keyword?: string
  status?: ProductionWorkOrderStatus
  productMaterialId?: ResourceId
  dateFrom?: string
  dateTo?: string
  page: number
  pageSize: number
}

export interface ProductionDocumentListParams {
  page: number
  pageSize: number
}

export interface ProductionWorkOrderSummaryRecord {
  id: ResourceId
  workOrderNo: string
  productMaterialId: ResourceId
  productMaterialCode: string
  productMaterialName: string
  bomId: ResourceId
  bomCode: string
  bomVersionCode: string
  plannedQuantity: number
  reportedQuantity: number
  qualifiedQuantity: number
  defectiveQuantity: number
  receivedQuantity: number
  issueWarehouseId: ResourceId
  issueWarehouseName: string
  receiptWarehouseId: ResourceId
  receiptWarehouseName: string
  plannedStartDate: string
  plannedFinishDate: string
  status: ProductionWorkOrderStatus
  remark?: string | null
  createdByName: string
  createdAt: string
  updatedAt: string
  releasedByName?: string | null
  releasedAt?: string | null
  completedByName?: string | null
  completedAt?: string | null
  cancelledByName?: string | null
  cancelledAt?: string | null
}

export interface ProductionWorkOrderMaterialRecord {
  id: ResourceId
  lineNo: number
  bomItemId: ResourceId
  materialId: ResourceId
  materialCode: string
  materialName: string
  materialType: string
  unitId: ResourceId
  unitName: string
  requiredQuantity: number
  issuedQuantity: number
  remainingQuantity: number
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
  lossRate: number
  remark?: string | null
}

export interface ProductionMaterialIssueLineRecord {
  id: ResourceId
  workOrderMaterialId: ResourceId
  lineNo: number
  warehouseId: ResourceId
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  quantity: number
  beforeQuantity?: number | null
  afterQuantity?: number | null
  remark?: string | null
}

export interface ProductionMaterialIssueSummaryRecord {
  id: ResourceId
  issueNo: string
  workOrderId: ResourceId
  status: ProductionDocumentStatus
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

export interface ProductionMaterialIssueDetailRecord extends ProductionMaterialIssueSummaryRecord {
  lines: ProductionMaterialIssueLineRecord[]
}

export interface ProductionWorkReportRecord {
  id: ResourceId
  reportNo: string
  workOrderId: ResourceId
  status: ProductionDocumentStatus
  businessDate: string
  qualifiedQuantity: number
  defectiveQuantity: number
  totalQuantity: number
  reporterName: string
  remark?: string | null
  createdByName: string
  createdAt: string
  updatedAt: string
  postedByName?: string | null
  postedAt?: string | null
}

export interface ProductionCompletionReceiptRecord {
  id: ResourceId
  receiptNo: string
  workOrderId: ResourceId
  status: ProductionDocumentStatus
  businessDate: string
  receiptWarehouseId: ResourceId
  receiptWarehouseName: string
  quantity: number
  beforeQuantity?: number | null
  afterQuantity?: number | null
  remark?: string | null
  createdByName: string
  createdAt: string
  updatedAt: string
  postedByName?: string | null
  postedAt?: string | null
}

export interface ProductionMovementRecord {
  id: ResourceId
  movementNo: string
  movementType: string
  direction: string
  warehouseId: ResourceId
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  quantity: number
  beforeQuantity: number
  afterQuantity: number
  sourceType: string
  sourceId: ResourceId
  sourceLineId: ResourceId
  businessDate: string
  reason?: string | null
  remark?: string | null
  operatorName: string
  occurredAt: string
}

export interface ProductionWorkOrderDetailRecord extends ProductionWorkOrderSummaryRecord {
  materials: ProductionWorkOrderMaterialRecord[]
  materialIssues: ProductionMaterialIssueSummaryRecord[]
  reports: ProductionWorkReportRecord[]
  completionReceipts: ProductionCompletionReceiptRecord[]
  movements: ProductionMovementRecord[]
}

export interface ProductionWorkOrderPayload {
  productMaterialId: ResourceId
  bomId: ResourceId
  plannedQuantity: string
  issueWarehouseId: ResourceId
  receiptWarehouseId: ResourceId
  plannedStartDate: string
  plannedFinishDate: string
  remark?: string
}

export interface ProductionMaterialIssueLinePayload {
  workOrderMaterialId: ResourceId
  lineNo: number
  warehouseId: ResourceId
  quantity: string
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string
}

export interface ProductionMaterialIssuePayload {
  businessDate: string
  reason: string
  remark?: string
  lines: ProductionMaterialIssueLinePayload[]
}

export interface ProductionWorkReportPayload {
  businessDate: string
  qualifiedQuantity: string
  defectiveQuantity: string
  reporterName?: string
  remark?: string
}

export interface ProductionCompletionReceiptPayload {
  businessDate: string
  receiptWarehouseId: ResourceId
  quantity: string
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string
}

export interface ProductionApi {
  workOrders: {
    list(params: ProductionWorkOrderListParams): Promise<PageResult<ProductionWorkOrderSummaryRecord>>
    get(id: ResourceId): Promise<ProductionWorkOrderDetailRecord>
    create(payload: ProductionWorkOrderPayload): Promise<ProductionWorkOrderDetailRecord>
    update(id: ResourceId, payload: ProductionWorkOrderPayload): Promise<ProductionWorkOrderDetailRecord>
    release(id: ResourceId): Promise<ProductionWorkOrderDetailRecord>
    complete(id: ResourceId): Promise<ProductionWorkOrderDetailRecord>
    cancel(id: ResourceId): Promise<ProductionWorkOrderDetailRecord>
  }
  materialIssues: {
    list(
      workOrderId: ResourceId,
      params: ProductionDocumentListParams,
    ): Promise<PageResult<ProductionMaterialIssueSummaryRecord>>
    get(workOrderId: ResourceId, id: ResourceId): Promise<ProductionMaterialIssueDetailRecord>
    create(
      workOrderId: ResourceId,
      payload: ProductionMaterialIssuePayload,
    ): Promise<ProductionMaterialIssueDetailRecord>
    update(
      workOrderId: ResourceId,
      id: ResourceId,
      payload: ProductionMaterialIssuePayload,
    ): Promise<ProductionMaterialIssueDetailRecord>
    post(workOrderId: ResourceId, id: ResourceId): Promise<ProductionMaterialIssueDetailRecord>
  }
  reports: {
    list(workOrderId: ResourceId, params: ProductionDocumentListParams): Promise<PageResult<ProductionWorkReportRecord>>
    get(workOrderId: ResourceId, id: ResourceId): Promise<ProductionWorkReportRecord>
    create(workOrderId: ResourceId, payload: ProductionWorkReportPayload): Promise<ProductionWorkReportRecord>
    update(
      workOrderId: ResourceId,
      id: ResourceId,
      payload: ProductionWorkReportPayload,
    ): Promise<ProductionWorkReportRecord>
    post(workOrderId: ResourceId, id: ResourceId): Promise<ProductionWorkReportRecord>
  }
  completionReceipts: {
    list(
      workOrderId: ResourceId,
      params: ProductionDocumentListParams,
    ): Promise<PageResult<ProductionCompletionReceiptRecord>>
    get(workOrderId: ResourceId, id: ResourceId): Promise<ProductionCompletionReceiptRecord>
    create(
      workOrderId: ResourceId,
      payload: ProductionCompletionReceiptPayload,
    ): Promise<ProductionCompletionReceiptRecord>
    update(
      workOrderId: ResourceId,
      id: ResourceId,
      payload: ProductionCompletionReceiptPayload,
    ): Promise<ProductionCompletionReceiptRecord>
    post(workOrderId: ResourceId, id: ResourceId): Promise<ProductionCompletionReceiptRecord>
  }
}

export interface ProductionApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createProductionApi(options: ProductionApiOptions = {}): ProductionApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const workOrderQueryKeys = [
    'keyword',
    'status',
    'productMaterialId',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const documentListQueryKeys = ['page', 'pageSize'] as const

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

  const workOrderPath = (id: ResourceId) => `/api/admin/production/work-orders/${encodeURIComponent(String(id))}`
  const materialIssuePath = (workOrderId: ResourceId, id?: ResourceId) =>
    `${workOrderPath(workOrderId)}/material-issues${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const reportPath = (workOrderId: ResourceId, id?: ResourceId) =>
    `${workOrderPath(workOrderId)}/reports${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const completionReceiptPath = (workOrderId: ResourceId, id?: ResourceId) =>
    `${workOrderPath(workOrderId)}/completion-receipts${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`

  return {
    workOrders: {
      list: (params) =>
        get<PageResult<ProductionWorkOrderSummaryRecord>>(
          '/api/admin/production/work-orders',
          pickQuery(params, workOrderQueryKeys),
        ),
      get: (id) => get<ProductionWorkOrderDetailRecord>(workOrderPath(id)),
      create: (payload) => write<ProductionWorkOrderDetailRecord>('POST', '/api/admin/production/work-orders', payload),
      update: (id, payload) => write<ProductionWorkOrderDetailRecord>('PUT', workOrderPath(id), payload),
      release: (id) => write<ProductionWorkOrderDetailRecord>('PUT', `${workOrderPath(id)}/release`),
      complete: (id) => write<ProductionWorkOrderDetailRecord>('PUT', `${workOrderPath(id)}/complete`),
      cancel: (id) => write<ProductionWorkOrderDetailRecord>('PUT', `${workOrderPath(id)}/cancel`),
    },
    materialIssues: {
      list: (workOrderId, params) =>
        get<PageResult<ProductionMaterialIssueSummaryRecord>>(
          materialIssuePath(workOrderId),
          pickQuery(params, documentListQueryKeys),
        ),
      get: (workOrderId, id) => get<ProductionMaterialIssueDetailRecord>(materialIssuePath(workOrderId, id)),
      create: (workOrderId, payload) =>
        write<ProductionMaterialIssueDetailRecord>('POST', materialIssuePath(workOrderId), payload),
      update: (workOrderId, id, payload) =>
        write<ProductionMaterialIssueDetailRecord>('PUT', materialIssuePath(workOrderId, id), payload),
      post: (workOrderId, id) =>
        write<ProductionMaterialIssueDetailRecord>('PUT', `${materialIssuePath(workOrderId, id)}/post`),
    },
    reports: {
      list: (workOrderId, params) =>
        get<PageResult<ProductionWorkReportRecord>>(reportPath(workOrderId), pickQuery(params, documentListQueryKeys)),
      get: (workOrderId, id) => get<ProductionWorkReportRecord>(reportPath(workOrderId, id)),
      create: (workOrderId, payload) => write<ProductionWorkReportRecord>('POST', reportPath(workOrderId), payload),
      update: (workOrderId, id, payload) =>
        write<ProductionWorkReportRecord>('PUT', reportPath(workOrderId, id), payload),
      post: (workOrderId, id) => write<ProductionWorkReportRecord>('PUT', `${reportPath(workOrderId, id)}/post`),
    },
    completionReceipts: {
      list: (workOrderId, params) =>
        get<PageResult<ProductionCompletionReceiptRecord>>(
          completionReceiptPath(workOrderId),
          pickQuery(params, documentListQueryKeys),
        ),
      get: (workOrderId, id) => get<ProductionCompletionReceiptRecord>(completionReceiptPath(workOrderId, id)),
      create: (workOrderId, payload) =>
        write<ProductionCompletionReceiptRecord>('POST', completionReceiptPath(workOrderId), payload),
      update: (workOrderId, id, payload) =>
        write<ProductionCompletionReceiptRecord>('PUT', completionReceiptPath(workOrderId, id), payload),
      post: (workOrderId, id) =>
        write<ProductionCompletionReceiptRecord>('PUT', `${completionReceiptPath(workOrderId, id)}/post`),
    },
  }
}

export const productionApi = createProductionApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
