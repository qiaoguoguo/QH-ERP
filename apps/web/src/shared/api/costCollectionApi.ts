import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type CostType = 'MATERIAL' | 'LABOR' | 'MANUFACTURING_OVERHEAD' | 'OTHER'
export type CostSourceType = 'AUTO_PRODUCTION' | 'MANUAL_ENTRY'
export type CostSourceDocumentType =
  | 'PRODUCTION_MATERIAL_ISSUE'
  | 'PRODUCTION_WORK_REPORT'
  | 'PRODUCTION_COMPLETION_RECEIPT'
  | 'MANUAL_COST_RECORD'
export type CostBasisType =
  | 'SOURCE_QUANTITY_ONLY'
  | 'MANUAL_AMOUNT'
  | 'MANUAL_UNIT_PRICE_QUANTITY'
  | 'OUTPUT_QUANTITY_TRACE'
export type CostRecordStatus = 'ACTIVE' | 'VOIDED'

export interface CostRecordListParams {
  keyword?: string
  workOrderId?: ResourceId
  productMaterialId?: ResourceId
  costType?: CostType
  sourceType?: CostSourceType
  sourceDocumentType?: CostSourceDocumentType
  sourceDocumentNo?: string
  dateFrom?: string
  dateTo?: string
  page: number
  pageSize: number
}

export interface CostRecordSummaryRecord {
  id: ResourceId
  recordNo: string
  workOrderId: ResourceId
  workOrderNo: string
  productMaterialId: ResourceId
  productMaterialCode: string
  productMaterialName: string
  costType: CostType
  sourceType: CostSourceType
  sourceDocumentType: CostSourceDocumentType
  sourceDocumentNo?: string | null
  sourceDocumentId?: ResourceId | null
  sourceLineId?: ResourceId | null
  basisType: CostBasisType
  materialId?: ResourceId | null
  materialCode?: string | null
  materialName?: string | null
  unitId?: ResourceId | null
  unitName?: string | null
  quantity?: number | null
  unitPrice?: number | null
  amount?: number | null
  businessDate: string
  status: CostRecordStatus
  remark?: string | null
  recordedByName: string
  recordedAt: string
  createdByName: string
  createdAt: string
  updatedAt: string
}

export interface CostRecordSourceSummary {
  sourceStatus?: string | null
  sourceDocumentNo?: string | null
  quantity?: number | null
  materialId?: ResourceId | null
  materialCode?: string | null
  materialName?: string | null
  unitId?: ResourceId | null
  unitName?: string | null
}

export interface CostRecordOutputTrace {
  receiptId: ResourceId
  receiptNo: string
  workOrderId: ResourceId
  businessDate: string
  receiptWarehouseId: ResourceId
  receiptWarehouseName: string
  quantity: number
  beforeQuantity: number
  afterQuantity: number
  postedByName: string
  postedAt: string
}

export interface CostRecordAuditSummary {
  id: ResourceId
  operatorUsername: string
  action: string
  createdAt: string
}

export interface CostRecordDetailRecord extends CostRecordSummaryRecord {
  workOrderStatus: string
  sourceStatus?: string | null
  sourceSummary?: CostRecordSourceSummary | null
  outputTrace: CostRecordOutputTrace[]
  auditSummary: CostRecordAuditSummary[]
}

export interface CostRecordPayload {
  workOrderId: ResourceId
  costType: CostType
  basisType: CostBasisType
  businessDate: string
  quantity?: string
  unitId?: ResourceId
  unitPrice?: string
  amount?: string
  sourceDocumentNo?: string
  remark: string
}

export interface CostAmountSummaryRecord {
  costType: CostType
  amount: number
}

export interface CostQuantitySummaryRecord {
  costType: CostType
  quantity: number
}

export interface WorkOrderCostSummaryRecord {
  workOrderId: ResourceId
  workOrderNo: string
  productMaterialId: ResourceId
  productMaterialCode: string
  productMaterialName: string
  formalAccounting: boolean
  records: CostRecordSummaryRecord[]
  amountSummaries: CostAmountSummaryRecord[]
  quantitySummaries: CostQuantitySummaryRecord[]
  outputTraces: CostRecordOutputTrace[]
}

export interface CostCollectionApi {
  records: {
    list(params: CostRecordListParams): Promise<PageResult<CostRecordSummaryRecord>>
    get(id: ResourceId): Promise<CostRecordDetailRecord>
    create(payload: CostRecordPayload): Promise<CostRecordDetailRecord>
    update(id: ResourceId, payload: CostRecordPayload): Promise<CostRecordDetailRecord>
  }
  workOrders: {
    summary(workOrderId: ResourceId): Promise<WorkOrderCostSummaryRecord>
  }
}

export interface CostCollectionApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createCostCollectionApi(options: CostCollectionApiOptions = {}): CostCollectionApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const recordQueryKeys = [
    'keyword',
    'workOrderId',
    'productMaterialId',
    'costType',
    'sourceType',
    'sourceDocumentType',
    'sourceDocumentNo',
    'dateFrom',
    'dateTo',
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

  const recordPath = (id: ResourceId) => `/api/admin/cost/records/${encodeURIComponent(String(id))}`
  const workOrderSummaryPath = (workOrderId: ResourceId) =>
    `/api/admin/cost/work-orders/${encodeURIComponent(String(workOrderId))}/summary`

  return {
    records: {
      list: (params) =>
        get<PageResult<CostRecordSummaryRecord>>('/api/admin/cost/records', pickQuery(params, recordQueryKeys)),
      get: (id) => get<CostRecordDetailRecord>(recordPath(id)),
      create: (payload) => write<CostRecordDetailRecord>('POST', '/api/admin/cost/records', payload),
      update: (id, payload) => write<CostRecordDetailRecord>('PUT', recordPath(id), payload),
    },
    workOrders: {
      summary: (workOrderId) => get<WorkOrderCostSummaryRecord>(workOrderSummaryPath(workOrderId)),
    },
  }
}

export const costCollectionApi = createCostCollectionApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
