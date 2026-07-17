import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type { InventoryQualityStatus, InventoryTrackingAllocationPayload } from './inventoryApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type ProductionOwnershipType = 'PUBLIC' | 'PROJECT'
export type ProjectProductionWorkOrderStatus = 'DRAFT' | 'RELEASED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | string
export type ProjectProductionDocumentStatus = 'DRAFT' | 'POSTED' | 'CANCELLED' | string

export interface VersionedIdempotentPayload {
  version: number
  idempotencyKey: string
}

export interface ProjectProductionPageParams {
  page: number
  pageSize: number
}

export interface ProjectProductionWorkOrderListParams extends ProjectProductionPageParams {
  keyword?: string | null
  status?: ProjectProductionWorkOrderStatus | null
  productMaterialId?: ResourceId | null | ''
  projectId?: ResourceId | null | ''
  ownershipType?: ProductionOwnershipType | null | ''
  sourceMrpSuggestionId?: ResourceId | null | ''
  dateFrom?: string | null
  dateTo?: string | null
}

export interface ProjectProductionSourceSummary {
  sourceMrpRunId?: ResourceId | null
  sourceMrpSuggestionId?: ResourceId | null
  sourceMrpRequirementLineId?: ResourceId | null
  sourceSuggestionNo?: string | null
  sourceRunNo?: string | null
  sourceStatus?: string | null
  sourceDisabledReason?: string | null
}

export interface ProjectProductionExecutionSummary {
  issuedQuantity?: string | null
  returnedQuantity?: string | null
  supplementedQuantity?: string | null
  reportedQualifiedQuantity?: string | null
  reportedDefectiveQuantity?: string | null
  completedQuantity?: string | null
  progressPercent?: string | null
}

export interface ProjectProductionTraceLink {
  label: string
  routeName?: string | null
  routePath?: string | null
  targetId?: ResourceId | null
  restricted?: boolean | null
  restrictedReason?: string | null
}

export interface ProjectProductionWorkOrderSummaryRecord {
  id: ResourceId
  workOrderNo: string
  ownershipType: ProductionOwnershipType
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  productMaterialId: ResourceId
  productMaterialCode: string
  productMaterialName: string
  bomId?: ResourceId | null
  bomCode?: string | null
  bomVersionCode?: string | null
  plannedQuantity: string
  reportedQuantity?: string | null
  qualifiedQuantity?: string | null
  defectiveQuantity?: string | null
  receivedQuantity?: string | null
  issueWarehouseId?: ResourceId | null
  issueWarehouseName?: string | null
  receiptWarehouseId?: ResourceId | null
  receiptWarehouseName?: string | null
  plannedStartDate?: string | null
  plannedFinishDate?: string | null
  status: ProjectProductionWorkOrderStatus
  statusName?: string | null
  sourceMrpRunId?: ResourceId | null
  sourceMrpSuggestionId?: ResourceId | null
  sourceMrpRequirementLineId?: ResourceId | null
  sourceSuggestionNo?: string | null
  completionValuationState?: string | null
  requiresManualProvisionalUnitCost?: boolean | null
  currentAverageUnitCost?: string | null
  costVisible?: boolean | null
  costRestrictedReason?: string | null
  actionDisabledReason?: string | null
  allowedActions?: string[]
  remark?: string | null
  createdByName?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  releasedByName?: string | null
  releasedAt?: string | null
  completedByName?: string | null
  completedAt?: string | null
  cancelledByName?: string | null
  cancelledAt?: string | null
  version: number
}

export interface ProjectProductionWorkOrderMaterialRecord {
  id: ResourceId
  lineNo: number
  bomItemId?: ResourceId | null
  materialId: ResourceId
  materialCode: string
  materialName: string
  materialType?: string | null
  unitId?: ResourceId | null
  unitName?: string | null
  requiredQuantity: string
  issuedQuantity?: string | null
  remainingQuantity?: string | null
  ownershipType?: ProductionOwnershipType | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  costLayerId?: ResourceId | null
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  quantityOnHand?: string | number | null
  reservedQuantity?: string | number | null
  occupiedQuantity?: string | number | null
  availableQuantity?: string | number | null
  availableToPromiseQuantity?: string | number | null
  maxSelectableQuantity?: string | number | null
  selectable?: boolean | null
  disabledReasonCode?: string | null
  disabledReason?: string | null
  lossRate?: string | number | null
  remark?: string | null
}

export interface ProjectProductionDocumentSummaryRecord {
  id: ResourceId
  documentNo?: string | null
  issueNo?: string | null
  reportNo?: string | null
  receiptNo?: string | null
  workOrderId: ResourceId
  status: ProjectProductionDocumentStatus
  businessDate: string
  reason?: string | null
  remark?: string | null
  lineCount?: number | null
  qualifiedQuantity?: string | number | null
  defectiveQuantity?: string | number | null
  totalQuantity?: string | number | null
  quantity?: string | number | null
  receiptWarehouseId?: ResourceId | null
  receiptWarehouseName?: string | null
  reporterName?: string | null
  postedByName?: string | null
  postedAt?: string | null
  createdByName?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  actionDisabledReason?: string | null
  allowedActions?: string[]
  version?: number | null
}

export interface ProjectProductionMovementRecord {
  id: ResourceId
  movementNo: string
  movementType: string
  direction: string
  ownershipType?: ProductionOwnershipType | null
  projectId?: ResourceId | null
  projectNo?: string | null
  warehouseName?: string | null
  materialCode?: string | null
  materialName?: string | null
  quantity: string
  sourceType?: string | null
  sourceId?: ResourceId | null
  sourceLineId?: ResourceId | null
  businessDate?: string | null
}

export interface ProjectProductionWorkOrderDetailRecord extends ProjectProductionWorkOrderSummaryRecord {
  sourceSummary?: ProjectProductionSourceSummary | null
  executionSummary?: ProjectProductionExecutionSummary | null
  traceLinks?: ProjectProductionTraceLink[]
  materials: ProjectProductionWorkOrderMaterialRecord[]
  materialIssues: ProjectProductionDocumentSummaryRecord[]
  reports: ProjectProductionDocumentSummaryRecord[]
  completionReceipts: ProjectProductionDocumentSummaryRecord[]
  movements: ProjectProductionMovementRecord[]
}

export interface ProjectProductionWorkOrderPayload {
  ownershipType: ProductionOwnershipType
  projectId?: ResourceId | null
  productMaterialId: ResourceId
  bomId: ResourceId
  plannedQuantity: string
  issueWarehouseId: ResourceId
  receiptWarehouseId: ResourceId
  plannedStartDate: string
  plannedFinishDate: string
  remark?: string
  idempotencyKey: string
}

export interface ProjectProductionWorkOrderUpdatePayload extends Omit<ProjectProductionWorkOrderPayload, 'idempotencyKey'> {
  version: number
  idempotencyKey: string
}

export interface ProjectProductionMaterialIssueLinePayload {
  workOrderMaterialId: ResourceId
  lineNo: number
  warehouseId: ResourceId
  quantity: string
  ownershipType: ProductionOwnershipType
  projectId?: ResourceId | null
  costLayerId?: ResourceId | null
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string
}

export interface ProjectProductionMaterialIssuePayload extends VersionedIdempotentPayload {
  businessDate: string
  reason: string
  remark?: string
  lines: ProjectProductionMaterialIssueLinePayload[]
}

export interface ProjectProductionWorkReportPayload extends VersionedIdempotentPayload {
  businessDate: string
  qualifiedQuantity: string
  defectiveQuantity: string
  reporterName?: string
  remark?: string
}

export interface ProjectProductionCompletionReceiptPayload extends VersionedIdempotentPayload {
  businessDate: string
  receiptWarehouseId: ResourceId
  quantity: string
  provisionalUnitCost?: string
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string
}

export interface ProjectProductionApi {
  workOrders: {
    list(params: ProjectProductionWorkOrderListParams): Promise<PageResult<ProjectProductionWorkOrderSummaryRecord>>
    get(id: ResourceId): Promise<ProjectProductionWorkOrderDetailRecord>
    create(payload: ProjectProductionWorkOrderPayload): Promise<ProjectProductionWorkOrderDetailRecord>
    update(id: ResourceId, payload: ProjectProductionWorkOrderUpdatePayload): Promise<ProjectProductionWorkOrderDetailRecord>
    release(id: ResourceId, payload: VersionedIdempotentPayload): Promise<ProjectProductionWorkOrderDetailRecord>
    complete(id: ResourceId, payload: VersionedIdempotentPayload): Promise<ProjectProductionWorkOrderDetailRecord>
    cancel(id: ResourceId, payload: VersionedIdempotentPayload): Promise<ProjectProductionWorkOrderDetailRecord>
  }
  materialIssues: {
    list(workOrderId: ResourceId, params: ProjectProductionPageParams): Promise<PageResult<ProjectProductionDocumentSummaryRecord>>
    get(workOrderId: ResourceId, id: ResourceId): Promise<ProjectProductionDocumentSummaryRecord>
    create(workOrderId: ResourceId, payload: ProjectProductionMaterialIssuePayload): Promise<ProjectProductionDocumentSummaryRecord>
    update(workOrderId: ResourceId, id: ResourceId, payload: ProjectProductionMaterialIssuePayload): Promise<ProjectProductionDocumentSummaryRecord>
    post(workOrderId: ResourceId, id: ResourceId, payload: VersionedIdempotentPayload): Promise<ProjectProductionDocumentSummaryRecord>
  }
  reports: {
    list(workOrderId: ResourceId, params: ProjectProductionPageParams): Promise<PageResult<ProjectProductionDocumentSummaryRecord>>
    get(workOrderId: ResourceId, id: ResourceId): Promise<ProjectProductionDocumentSummaryRecord>
    create(workOrderId: ResourceId, payload: ProjectProductionWorkReportPayload): Promise<ProjectProductionDocumentSummaryRecord>
    update(workOrderId: ResourceId, id: ResourceId, payload: ProjectProductionWorkReportPayload): Promise<ProjectProductionDocumentSummaryRecord>
    post(workOrderId: ResourceId, id: ResourceId, payload: VersionedIdempotentPayload): Promise<ProjectProductionDocumentSummaryRecord>
  }
  completionReceipts: {
    list(workOrderId: ResourceId, params: ProjectProductionPageParams): Promise<PageResult<ProjectProductionDocumentSummaryRecord>>
    get(workOrderId: ResourceId, id: ResourceId): Promise<ProjectProductionDocumentSummaryRecord>
    create(workOrderId: ResourceId, payload: ProjectProductionCompletionReceiptPayload): Promise<ProjectProductionDocumentSummaryRecord>
    update(workOrderId: ResourceId, id: ResourceId, payload: ProjectProductionCompletionReceiptPayload): Promise<ProjectProductionDocumentSummaryRecord>
    post(workOrderId: ResourceId, id: ResourceId, payload: VersionedIdempotentPayload): Promise<ProjectProductionDocumentSummaryRecord>
  }
}

export interface ProjectProductionApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createProjectProductionApi(options: ProjectProductionApiOptions = {}): ProjectProductionApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const workOrderQueryKeys = [
    'keyword',
    'projectId',
    'ownershipType',
    'sourceMrpSuggestionId',
    'status',
    'productMaterialId',
    'dateFrom',
    'dateTo',
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

  const workOrderPath = (id?: ResourceId) =>
    `/api/admin/production/work-orders${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const materialIssuePath = (workOrderId: ResourceId, id?: ResourceId) =>
    `${workOrderPath(workOrderId)}/material-issues${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const reportPath = (workOrderId: ResourceId, id?: ResourceId) =>
    `${workOrderPath(workOrderId)}/reports${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const completionReceiptPath = (workOrderId: ResourceId, id?: ResourceId) =>
    `${workOrderPath(workOrderId)}/completion-receipts${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`

  return {
    workOrders: {
      list: (params) => get<PageResult<ProjectProductionWorkOrderSummaryRecord>>(
        workOrderPath(),
        pickQuery(params, workOrderQueryKeys),
      ),
      get: (id) => get<ProjectProductionWorkOrderDetailRecord>(workOrderPath(id)),
      create: (payload) => writeAction<ProjectProductionWorkOrderDetailRecord>('POST', workOrderPath(), payload),
      update: (id, payload) => writeAction<ProjectProductionWorkOrderDetailRecord>('PUT', workOrderPath(id), payload),
      release: (id, payload) =>
        writeAction<ProjectProductionWorkOrderDetailRecord>('PUT', `${workOrderPath(id)}/release`, payload),
      complete: (id, payload) =>
        writeAction<ProjectProductionWorkOrderDetailRecord>('PUT', `${workOrderPath(id)}/complete`, payload),
      cancel: (id, payload) =>
        writeAction<ProjectProductionWorkOrderDetailRecord>('PUT', `${workOrderPath(id)}/cancel`, payload),
    },
    materialIssues: {
      list: (workOrderId, params) =>
        get<PageResult<ProjectProductionDocumentSummaryRecord>>(materialIssuePath(workOrderId), pickQuery(params, pageKeys)),
      get: (workOrderId, id) => get<ProjectProductionDocumentSummaryRecord>(materialIssuePath(workOrderId, id)),
      create: (workOrderId, payload) =>
        writeAction<ProjectProductionDocumentSummaryRecord>('POST', materialIssuePath(workOrderId), payload),
      update: (workOrderId, id, payload) =>
        writeAction<ProjectProductionDocumentSummaryRecord>('PUT', materialIssuePath(workOrderId, id), payload),
      post: (workOrderId, id, payload) =>
        writeAction<ProjectProductionDocumentSummaryRecord>('PUT', `${materialIssuePath(workOrderId, id)}/post`, payload),
    },
    reports: {
      list: (workOrderId, params) =>
        get<PageResult<ProjectProductionDocumentSummaryRecord>>(reportPath(workOrderId), pickQuery(params, pageKeys)),
      get: (workOrderId, id) => get<ProjectProductionDocumentSummaryRecord>(reportPath(workOrderId, id)),
      create: (workOrderId, payload) =>
        writeAction<ProjectProductionDocumentSummaryRecord>('POST', reportPath(workOrderId), payload),
      update: (workOrderId, id, payload) =>
        writeAction<ProjectProductionDocumentSummaryRecord>('PUT', reportPath(workOrderId, id), payload),
      post: (workOrderId, id, payload) =>
        writeAction<ProjectProductionDocumentSummaryRecord>('PUT', `${reportPath(workOrderId, id)}/post`, payload),
    },
    completionReceipts: {
      list: (workOrderId, params) =>
        get<PageResult<ProjectProductionDocumentSummaryRecord>>(completionReceiptPath(workOrderId), pickQuery(params, pageKeys)),
      get: (workOrderId, id) => get<ProjectProductionDocumentSummaryRecord>(completionReceiptPath(workOrderId, id)),
      create: (workOrderId, payload) =>
        writeAction<ProjectProductionDocumentSummaryRecord>('POST', completionReceiptPath(workOrderId), payload),
      update: (workOrderId, id, payload) =>
        writeAction<ProjectProductionDocumentSummaryRecord>('PUT', completionReceiptPath(workOrderId, id), payload),
      post: (workOrderId, id, payload) =>
        writeAction<ProjectProductionDocumentSummaryRecord>('PUT', `${completionReceiptPath(workOrderId, id)}/post`, payload),
    },
  }
}

export const projectProductionApi = createProjectProductionApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
