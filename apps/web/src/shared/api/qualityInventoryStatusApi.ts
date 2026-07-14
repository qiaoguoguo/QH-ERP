import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type { InventoryOwnershipType, InventoryTrackingAllocationPayload } from './inventoryApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type InventoryQualityStatus = 'PENDING_INSPECTION' | 'QUALIFIED' | 'REJECTED' | 'FROZEN'
export type QualityInspectionStatus = 'PENDING' | 'COMPLETED'
export type QualityInspectionSourceType =
  | 'PURCHASE_RECEIPT'
  | 'PRODUCTION_COMPLETION'
  | 'SALES_RETURN'
  | 'PRODUCTION_RETURN'

export interface QualityInspectionListParams {
  keyword?: string
  sourceType?: string
  status?: QualityInspectionStatus
  warehouseId?: ResourceId
  materialId?: ResourceId
  businessDateFrom?: string
  businessDateTo?: string
  qualityStatus?: InventoryQualityStatus
  page: number
  pageSize: number
}

export interface QualityInspectionRecord {
  id: ResourceId
  inspectionNo: string
  sourceType: string
  sourceTypeName: string
  sourceId: ResourceId
  sourceLineId?: ResourceId | null
  sourceDocumentNo: string
  warehouseId: ResourceId
  warehouseCode: string
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  materialSpec?: string | null
  unitId: ResourceId
  unitName: string
  qualityStatus?: InventoryQualityStatus
  qualityStatusName?: string
  inspectionQuantity: string
  remainingQuantity: string
  qualifiedQuantity: string
  rejectedQuantity: string
  frozenQuantity: string
  status: QualityInspectionStatus
  statusName: string
  businessDate: string
  createdByName: string
  createdAt: string
  completedByName?: string | null
  completedAt?: string | null
  reason?: string | null
  remark?: string | null
  version: number
  canProcess: boolean
  disabledReason?: string | null
  trackingAllocations?: InventoryTrackingAllocationPayload[]
}

export interface QualityInspectionAuditRecord {
  action: string
  actionName: string
  operatorName: string
  operatedAt: string
  businessDate?: string | null
  reason?: string | null
  remark?: string | null
}

export interface QualityInspectionDetail extends QualityInspectionRecord {
  sourceSummary: Record<string, unknown>
  currentQualityStatus: InventoryQualityStatus
  currentQualityStatusName: string
  auditRecords: QualityInspectionAuditRecord[]
}

export interface QualityInspectionProcessPayload {
  businessDate: string
  qualifiedQuantity: string
  rejectedQuantity: string
  frozenQuantity: string
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  reason: string
  remark?: string
}

export interface QualityStatusTransferPayload {
  businessDate: string
  warehouseId: ResourceId
  materialId: ResourceId
  unitId: ResourceId
  ownershipType: InventoryOwnershipType
  projectId?: ResourceId
  costLayerId?: ResourceId
  quantity: string
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  reason: string
  remark?: string
}

export interface QualityStatusTransferResult {
  transferNo?: string
  sourceMovementId?: ResourceId
  targetMovementId?: ResourceId
  sourceQualityStatus?: InventoryQualityStatus
  targetQualityStatus?: InventoryQualityStatus
  quantity?: string
}

export interface QualityInventoryStatusApi {
  inspections: {
    list(params: QualityInspectionListParams): Promise<PageResult<QualityInspectionRecord>>
    get(id: ResourceId): Promise<QualityInspectionDetail>
    process(id: ResourceId, payload: QualityInspectionProcessPayload): Promise<QualityInspectionDetail>
  }
  qualityTransfers: {
    freeze(payload: QualityStatusTransferPayload): Promise<QualityStatusTransferResult>
    unfreeze(payload: QualityStatusTransferPayload): Promise<QualityStatusTransferResult>
  }
}

export interface QualityInventoryStatusApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createQualityInventoryStatusApi(options: QualityInventoryStatusApiOptions = {}): QualityInventoryStatusApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const inspectionQueryKeys = [
    'keyword',
    'sourceType',
    'status',
    'warehouseId',
    'materialId',
    'businessDateFrom',
    'businessDateTo',
    'qualityStatus',
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
  const write = async <T>(path: string, body?: object): Promise<T> => {
    const csrf = await getCsrf()
    const headers: Record<string, string> = {
      [csrf.headerName]: csrf.token,
    }
    const init: RequestInit = {
      headers,
      method: 'POST',
    }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }
    return request<T>(path, init)
  }

  const inspectionPath = (id?: ResourceId) =>
    `/api/admin/quality/inspections${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`

  return {
    inspections: {
      list: (params) =>
        get<PageResult<QualityInspectionRecord>>(inspectionPath(), pickQuery(params, inspectionQueryKeys)),
      get: (id) => get<QualityInspectionDetail>(inspectionPath(id)),
      process: (id, payload) => write<QualityInspectionDetail>(`${inspectionPath(id)}/process`, payload),
    },
    qualityTransfers: {
      freeze: (payload) => write<QualityStatusTransferResult>('/api/admin/inventory/quality-transfers/freeze', payload),
      unfreeze: (payload) => write<QualityStatusTransferResult>('/api/admin/inventory/quality-transfers/unfreeze', payload),
    },
  }
}

export const qualityInventoryStatusApi = createQualityInventoryStatusApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
