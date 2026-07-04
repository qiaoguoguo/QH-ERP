import { AccountPermissionApiError, type ApiEnvelope, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type ReportDecimal = string
export type ReportMoney = string
export type ReportPercent = string

interface BusinessReportingApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

interface DateRangeParams {
  dateFrom?: string | null
  dateTo?: string | null
}

interface PageParams {
  page: number
  pageSize: number
}

interface KeywordParams {
  keyword?: string | null
}

export interface ReportPageResult<TSummary, TItem> extends PageResult<TItem> {
  summary: TSummary
  totalPages: number
}

export interface ReportOverviewParams extends DateRangeParams {}

export interface ReportPeriod {
  dateFrom: string
  dateTo: string
}

export interface ReportOverviewRecord {
  period: ReportPeriod
  salesShipmentAmount: ReportMoney
  purchaseReceiptAmount: ReportMoney
  inventoryInQuantity: ReportDecimal
  inventoryOutQuantity: ReportDecimal
  productionPlannedQuantity: ReportDecimal
  productionCompletedQuantity: ReportDecimal
  costAmount: ReportMoney
  receivableBalance: ReportMoney
  payableBalance: ReportMoney
  receivedAmount: ReportMoney
  paidAmount: ReportMoney
  exceptionCount: number
  formalAccounting: false
}

export interface SalesReportListParams extends DateRangeParams, KeywordParams, PageParams {
  customerId?: ResourceId | null
  materialId?: ResourceId | null
  status?: string | null
}

export interface SalesReportSummary {
  shipmentQuantity: ReportDecimal
  shipmentAmount: ReportMoney
  receivableAmount?: ReportMoney
  receivedAmount?: ReportMoney
  unreceivedAmount?: ReportMoney
  sourceCount: number
}

export interface SalesReportRow {
  sourceType: 'SALES_SHIPMENT'
  sourceId: ResourceId
  sourceNo: string
  salesOrderId?: ResourceId
  salesOrderNo?: string
  customerId?: ResourceId
  customerName?: string
  materialId?: ResourceId
  materialName?: string
  businessDate?: string
  quantity: ReportDecimal
  unitPrice?: ReportMoney
  amount: ReportMoney
  receivableAmount?: ReportMoney
  receivedAmount?: ReportMoney
  unreceivedAmount?: ReportMoney
  sourceCount: number
  traceKey: string
}

export interface ProcurementReportListParams extends DateRangeParams, KeywordParams, PageParams {
  supplierId?: ResourceId | null
  materialId?: ResourceId | null
  status?: string | null
}

export interface ProcurementReportSummary {
  receiptQuantity: ReportDecimal
  receiptAmount: ReportMoney
  payableAmount?: ReportMoney
  paidAmount?: ReportMoney
  unpaidAmount?: ReportMoney
  sourceCount: number
}

export interface ProcurementReportRow {
  sourceType: 'PURCHASE_RECEIPT'
  sourceId: ResourceId
  sourceNo: string
  purchaseOrderId?: ResourceId
  purchaseOrderNo?: string
  supplierId?: ResourceId
  supplierName?: string
  materialId?: ResourceId
  materialName?: string
  businessDate?: string
  quantity: ReportDecimal
  unitPrice?: ReportMoney
  amount: ReportMoney
  payableAmount?: ReportMoney
  paidAmount?: ReportMoney
  unpaidAmount?: ReportMoney
  sourceCount: number
  traceKey: string
}

export interface InventoryReportListParams extends DateRangeParams, KeywordParams, PageParams {
  warehouseId?: ResourceId | null
  materialId?: ResourceId | null
}

export interface InventoryReportSummary {
  openingQuantity: ReportDecimal
  inQuantity: ReportDecimal
  outQuantity: ReportDecimal
  adjustQuantity: ReportDecimal
  closingQuantity: ReportDecimal
  sourceCount: number
}

export interface InventoryReportRow extends InventoryReportSummary {
  warehouseId: ResourceId
  warehouseName: string
  materialId: ResourceId
  materialName: string
  traceKey: string
}

export interface ProductionReportListParams extends DateRangeParams, KeywordParams, PageParams {
  workOrderId?: ResourceId | null
  materialId?: ResourceId | null
  status?: string | null
}

export interface ProductionReportSummary {
  workOrderCount: number
  plannedQuantity: ReportDecimal
  issuedQuantity: ReportDecimal
  reportedQuantity: ReportDecimal
  qualifiedQuantity: ReportDecimal
  defectiveQuantity: ReportDecimal
  completionReceiptQuantity: ReportDecimal
  completionRate: ReportPercent
  sourceCount: number
}

export interface ProductionReportRow {
  workOrderId: ResourceId
  workOrderNo: string
  productMaterialId: ResourceId
  productMaterialName: string
  plannedQuantity: ReportDecimal
  issuedQuantity: ReportDecimal
  reportedQuantity: ReportDecimal
  qualifiedQuantity: ReportDecimal
  defectiveQuantity: ReportDecimal
  completionReceiptQuantity: ReportDecimal
  completionRate: ReportPercent
  status: string
  plannedStartDate?: string | null
  plannedFinishDate?: string | null
  sourceCount: number
  traceKey: string
}

export interface CostReportListParams extends DateRangeParams, KeywordParams, PageParams {
  workOrderId?: ResourceId | null
  materialId?: ResourceId | null
  status?: string | null
}

export interface CostReportSummary {
  materialCostAmount: ReportMoney
  laborCostAmount: ReportMoney
  manufacturingOverheadAmount: ReportMoney
  otherCostAmount: ReportMoney
  totalCostAmount: ReportMoney
  sourceCount: number
  formalAccounting: false
}

export interface CostReportRow {
  costRecordId: ResourceId
  recordNo: string
  workOrderId?: ResourceId
  workOrderNo?: string
  productMaterialId?: ResourceId
  productMaterialName?: string
  costType: string
  sourceType: string
  sourceDocumentType?: string
  sourceDocumentId?: ResourceId
  sourceDocumentNo?: string
  businessDate?: string
  quantity?: ReportDecimal
  unitPrice?: ReportMoney
  amount: ReportMoney
  basisType?: string
  formalAccounting: false
  sourceCount: number
  traceKey: string
}

export interface SettlementReportListParams extends DateRangeParams, KeywordParams, PageParams {
  customerId?: ResourceId | null
  supplierId?: ResourceId | null
  status?: string | null
}

export interface SettlementReportSummary {
  receivableAmount: ReportMoney
  receivedAmount: ReportMoney
  unreceivedAmount: ReportMoney
  payableAmount: ReportMoney
  paidAmount: ReportMoney
  unpaidAmount: ReportMoney
  sourceCount: number
}

export interface SettlementReportRow {
  settlementType: 'RECEIVABLE' | 'PAYABLE' | 'RECEIPT' | 'PAYMENT'
  sourceId: ResourceId
  sourceNo: string
  partyType: 'CUSTOMER' | 'SUPPLIER'
  partyId: ResourceId
  partyName: string
  businessDate: string
  dueDate?: string | null
  totalAmount: ReportMoney
  settledAmount: ReportMoney
  unsettledAmount: ReportMoney
  overdueDays?: number
  agingBucket?: string
  status: string
  sourceCount: number
  traceKey: string
}

export type BusinessExceptionType =
  | 'SALES_DELIVERY_OVERDUE'
  | 'PROCUREMENT_RECEIPT_OVERDUE'
  | 'INVENTORY_SHORTAGE'
  | 'PRODUCTION_OVERDUE'
  | 'COST_MISSING'
  | 'RECEIVABLE_OVERDUE'
  | 'PAYABLE_DUE_SOON'

export interface ExceptionReportListParams extends DateRangeParams, KeywordParams, PageParams {
  type?: BusinessExceptionType | null
}

export interface ExceptionReportSummary {
  exceptionCount: number
  criticalCount: number
  warningCount: number
  countsByType: Partial<Record<BusinessExceptionType, number>>
}

export interface ExceptionReportRow {
  exceptionType: BusinessExceptionType
  severity: 'CRITICAL' | 'WARNING' | string
  sourceType: string | null
  sourceId: ResourceId | null
  sourceNo: string | null
  businessDate: string | null
  objectName: string | null
  description: string
  sourceCount: number
  canViewResource: boolean
  traceKey: string | null
}

export interface ReportTraceListParams extends DateRangeParams, PageParams {
  traceKey: string
}

export interface ReportTraceRecord {
  sourceType: string
  sourceId: ResourceId | null
  sourceNo: string | null
  sourceLineId: ResourceId | null
  businessDate: string | null
  status: string | null
  quantity: ReportDecimal | null
  amount: ReportMoney | null
  resourceRouteName: string | null
  resourceRouteParams: Record<string, ResourceId> | null
  resourceRouteQuery: Record<string, ResourceId> | null
  canViewResource: boolean
  restricted: boolean
  restrictedMessage: string | null
}

interface TraceEndpoint {
  list(params: ReportTraceListParams): Promise<PageResult<ReportTraceRecord>>
}

export interface BusinessReportingApi {
  overview: {
    get(params?: ReportOverviewParams): Promise<ReportOverviewRecord>
  }
  sales: {
    list(params: SalesReportListParams): Promise<ReportPageResult<SalesReportSummary, SalesReportRow>>
    traces: TraceEndpoint
  }
  procurement: {
    list(params: ProcurementReportListParams): Promise<ReportPageResult<ProcurementReportSummary, ProcurementReportRow>>
    traces: TraceEndpoint
  }
  inventory: {
    list(params: InventoryReportListParams): Promise<ReportPageResult<InventoryReportSummary, InventoryReportRow>>
    traces: TraceEndpoint
  }
  production: {
    list(params: ProductionReportListParams): Promise<ReportPageResult<ProductionReportSummary, ProductionReportRow>>
    traces: TraceEndpoint
  }
  cost: {
    list(params: CostReportListParams): Promise<ReportPageResult<CostReportSummary, CostReportRow>>
    traces: TraceEndpoint
  }
  settlement: {
    list(params: SettlementReportListParams): Promise<ReportPageResult<SettlementReportSummary, SettlementReportRow>>
    traces: TraceEndpoint
  }
  exceptions: {
    list(params: ExceptionReportListParams): Promise<ReportPageResult<ExceptionReportSummary, ExceptionReportRow>>
    traces: TraceEndpoint
  }
}

export function createBusinessReportingApi(options: BusinessReportingApiOptions = {}): BusinessReportingApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')

  const overviewQueryKeys = ['dateFrom', 'dateTo'] as const
  const salesQueryKeys = ['dateFrom', 'dateTo', 'customerId', 'materialId', 'status', 'keyword', 'page', 'pageSize'] as const
  const procurementQueryKeys = ['dateFrom', 'dateTo', 'supplierId', 'materialId', 'status', 'keyword', 'page', 'pageSize'] as const
  const inventoryQueryKeys = ['dateFrom', 'dateTo', 'warehouseId', 'materialId', 'keyword', 'page', 'pageSize'] as const
  const productionQueryKeys = ['dateFrom', 'dateTo', 'workOrderId', 'materialId', 'status', 'keyword', 'page', 'pageSize'] as const
  const costQueryKeys = ['dateFrom', 'dateTo', 'workOrderId', 'materialId', 'status', 'keyword', 'page', 'pageSize'] as const
  const settlementQueryKeys = ['dateFrom', 'dateTo', 'customerId', 'supplierId', 'status', 'keyword', 'page', 'pageSize'] as const
  const exceptionQueryKeys = ['dateFrom', 'dateTo', 'type', 'keyword', 'page', 'pageSize'] as const
  const traceQueryKeys = ['traceKey', 'dateFrom', 'dateTo', 'page', 'pageSize'] as const

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

  const request = async <T>(path: string, query?: object): Promise<T> => {
    const response = await fetcher(buildUrl(path, query), {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
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

  const get = <T>(path: string, query?: object) => request<T>(path, query)
  const trace = (path: string): TraceEndpoint => ({
    list: (params) => get<PageResult<ReportTraceRecord>>(path, pickQuery(params, traceQueryKeys)),
  })

  return {
    overview: {
      get: (params = {}) => get<ReportOverviewRecord>('/api/admin/reports/overview', pickQuery(params, overviewQueryKeys)),
    },
    sales: {
      list: (params) =>
        get<ReportPageResult<SalesReportSummary, SalesReportRow>>('/api/admin/reports/sales-summary', pickQuery(params, salesQueryKeys)),
      traces: trace('/api/admin/reports/sales-summary/traces'),
    },
    procurement: {
      list: (params) =>
        get<ReportPageResult<ProcurementReportSummary, ProcurementReportRow>>(
          '/api/admin/reports/procurement-summary',
          pickQuery(params, procurementQueryKeys),
        ),
      traces: trace('/api/admin/reports/procurement-summary/traces'),
    },
    inventory: {
      list: (params) =>
        get<ReportPageResult<InventoryReportSummary, InventoryReportRow>>(
          '/api/admin/reports/inventory-stock-flow',
          pickQuery(params, inventoryQueryKeys),
        ),
      traces: trace('/api/admin/reports/inventory-stock-flow/traces'),
    },
    production: {
      list: (params) =>
        get<ReportPageResult<ProductionReportSummary, ProductionReportRow>>(
          '/api/admin/reports/production-execution',
          pickQuery(params, productionQueryKeys),
        ),
      traces: trace('/api/admin/reports/production-execution/traces'),
    },
    cost: {
      list: (params) =>
        get<ReportPageResult<CostReportSummary, CostReportRow>>('/api/admin/reports/cost-collection', pickQuery(params, costQueryKeys)),
      traces: trace('/api/admin/reports/cost-collection/traces'),
    },
    settlement: {
      list: (params) =>
        get<ReportPageResult<SettlementReportSummary, SettlementReportRow>>(
          '/api/admin/reports/settlement-summary',
          pickQuery(params, settlementQueryKeys),
        ),
      traces: trace('/api/admin/reports/settlement-summary/traces'),
    },
    exceptions: {
      list: (params) =>
        get<ReportPageResult<ExceptionReportSummary, ExceptionReportRow>>('/api/admin/reports/exceptions', pickQuery(params, exceptionQueryKeys)),
      traces: trace('/api/admin/reports/exceptions/traces'),
    },
  }
}

export const businessReportingApi = createBusinessReportingApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
