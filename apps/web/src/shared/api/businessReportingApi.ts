import { AccountPermissionApiError, type ApiEnvelope, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type ReportDecimal = string
export type ReportMoney = string
export type ReportPercent = string
export type ReportAnalysisMode = 'LIVE' | 'BUSINESS_SNAPSHOT'
export type ReportCompletenessStatus = 'COMPLETE' | 'INCOMPLETE' | 'UNAVAILABLE' | 'RESTRICTED' | string
export type ReportFreshnessStatus = 'CURRENT' | 'FROZEN' | 'STALE' | 'LEGACY_NOT_INCLUDED' | string
export type ReportReconciliationStatus = 'MATCHED' | 'DIFFERENT' | 'INCOMPLETE' | 'UNAVAILABLE' | 'RESTRICTED' | string
export type ReportFinalityStatus = 'PREVIEW' | 'FINAL' | 'UNAVAILABLE' | string

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

export interface OperatingFinanceParams {
  periodCode?: string | null
  projectId?: ResourceId | null
  contractId?: ResourceId | null
  basis?: string | null
  analysisMode?: ReportAnalysisMode | string | null
  completenessStatus?: ReportCompletenessStatus | null
  reconciliationStatus?: ReportReconciliationStatus | null
  finalityStatus?: ReportFinalityStatus | null
  keyword?: string | null
  page?: number
  pageSize?: number
}

export interface OperatingFinanceTraceListParams extends PageParams {
  traceKey: string
  periodCode?: string | null
  analysisMode?: ReportAnalysisMode | string | null
}

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

export interface OperatingFinanceOverviewRecord {
  periodCode: string
  analysisMode: ReportAnalysisMode | string
  businessPeriodStatus: string
  accountingPeriodStatus: string
  financialCloseStatus: string
  finalityStatus: ReportFinalityStatus
  freshnessStatus: ReportFreshnessStatus
  projectProfitAmount: ReportMoney | null
  contractUnreceivedAmount: ReportMoney | null
  procurementVarianceAmount: ReportMoney | null
  inventoryCapitalAmount: ReportMoney | null
  receivablePayableBalanceAmount: ReportMoney | null
  accountingDifferenceAmount: ReportMoney | null
  restrictedReason?: string | null
  sourceCount: number
}

export interface SalesReportListParams extends DateRangeParams, KeywordParams, PageParams {
  customerId?: ResourceId | null
  materialId?: ResourceId | null
  status?: string | null
}

export interface SalesReportSummary {
  shipmentQuantity: ReportDecimal
  shipmentAmount: ReportMoney
  salesOriginalAmount?: ReportMoney
  salesReturnAmount?: ReportMoney
  salesNetAmount?: ReportMoney
  salesOriginalQuantity?: ReportDecimal
  salesReturnQuantity?: ReportDecimal
  salesNetQuantity?: ReportDecimal
  receivableAmount?: ReportMoney
  receivedAmount?: ReportMoney
  unreceivedAmount?: ReportMoney
  sourceCount: number
}

export interface SalesReportRow {
  sourceType: 'SALES_SHIPMENT' | 'SALES_RETURN'
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
  salesOriginalAmount?: ReportMoney
  salesReturnAmount?: ReportMoney
  salesNetAmount?: ReportMoney
  salesOriginalQuantity?: ReportDecimal
  salesReturnQuantity?: ReportDecimal
  salesNetQuantity?: ReportDecimal
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
  purchaseOriginalAmount?: ReportMoney
  purchaseReturnAmount?: ReportMoney
  purchaseNetAmount?: ReportMoney
  purchaseOriginalQuantity?: ReportDecimal
  purchaseReturnQuantity?: ReportDecimal
  purchaseNetQuantity?: ReportDecimal
  payableAmount?: ReportMoney
  paidAmount?: ReportMoney
  unpaidAmount?: ReportMoney
  sourceCount: number
}

export interface ProcurementReportRow {
  sourceType: 'PURCHASE_RECEIPT' | 'PURCHASE_RETURN'
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
  purchaseOriginalAmount?: ReportMoney
  purchaseReturnAmount?: ReportMoney
  purchaseNetAmount?: ReportMoney
  purchaseOriginalQuantity?: ReportDecimal
  purchaseReturnQuantity?: ReportDecimal
  purchaseNetQuantity?: ReportDecimal
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
  inboundOriginalQuantity?: ReportDecimal
  inboundReverseQuantity?: ReportDecimal
  inboundNetQuantity?: ReportDecimal
  outboundOriginalQuantity?: ReportDecimal
  outboundReverseQuantity?: ReportDecimal
  outboundNetQuantity?: ReportDecimal
  inventoryNetChangeQuantity?: ReportDecimal
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
  issuedOriginalQuantity?: ReportDecimal
  materialReturnQuantity?: ReportDecimal
  materialSupplementQuantity?: ReportDecimal
  issuedNetQuantity?: ReportDecimal
  completedQuantity?: ReportDecimal
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
  issuedOriginalQuantity?: ReportDecimal
  materialReturnQuantity?: ReportDecimal
  materialSupplementQuantity?: ReportDecimal
  issuedNetQuantity?: ReportDecimal
  completedQuantity?: ReportDecimal
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
  materialOriginalCost?: ReportMoney
  materialReturnCost?: ReportMoney
  materialSupplementCost?: ReportMoney
  materialNetCost?: ReportMoney
  totalNetCost?: ReportMoney
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
  materialOriginalCost?: ReportMoney
  materialReturnCost?: ReportMoney
  materialSupplementCost?: ReportMoney
  materialNetCost?: ReportMoney
  totalNetCost?: ReportMoney
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
  receivableOriginalAmount?: ReportMoney
  receivableAdjustmentAmount?: ReportMoney
  receivableNetAmount?: ReportMoney
  payableOriginalAmount?: ReportMoney
  payableAdjustmentAmount?: ReportMoney
  payableNetAmount?: ReportMoney
  settlementRemainingAmount?: ReportMoney
  sourceCount: number
}

export interface SettlementReportRow {
  settlementType: 'RECEIVABLE' | 'PAYABLE' | 'RECEIPT' | 'PAYMENT' | 'SETTLEMENT_ADJUSTMENT'
  sourceType?: 'RECEIVABLE' | 'PAYABLE' | 'RECEIPT' | 'PAYMENT' | 'SETTLEMENT_ADJUSTMENT' | string
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
  receivableOriginalAmount?: ReportMoney
  receivableAdjustmentAmount?: ReportMoney
  receivableNetAmount?: ReportMoney
  payableOriginalAmount?: ReportMoney
  payableAdjustmentAmount?: ReportMoney
  payableNetAmount?: ReportMoney
  settlementRemainingAmount?: ReportMoney
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
  statusName?: string | null
  quantity: ReportDecimal | null
  amount: ReportMoney | null
  resourceRouteName: string | null
  resourceRouteParams: Record<string, ResourceId> | null
  resourceRouteQuery: Record<string, ResourceId> | null
  canViewResource: boolean
  restricted: boolean
  restrictedMessage: string | null
}

export interface ProjectProfitReportSummary {
  projectCount?: number
  shipmentRevenueAmount?: ReportMoney | null
  invoiceRevenueAmount?: ReportMoney | null
  targetRevenueAmount?: ReportMoney | null
  projectCostAmount?: ReportMoney | null
  operatingGrossProfitAmount?: ReportMoney | null
  accountingProfitAmount?: ReportMoney | null
  differenceAmount?: ReportMoney | null
  sourceCount: number
  amountVisible?: boolean
  restrictedReason?: string | null
}

export interface ProjectProfitReportRow {
  projectId: ResourceId
  projectNo: string
  projectName: string
  customerName?: string | null
  shipmentRevenueAmount: ReportMoney | null
  invoiceRevenueAmount: ReportMoney | null
  targetRevenueAmount: ReportMoney | null
  projectCostAmount: ReportMoney | null
  operatingGrossProfitAmount: ReportMoney | null
  operatingGrossProfitRate: ReportPercent | null
  accountingRevenueAmount?: ReportMoney | null
  accountingCostAmount?: ReportMoney | null
  accountingProfitAmount?: ReportMoney | null
  differenceAmount?: ReportMoney | null
  completenessStatus: ReportCompletenessStatus
  freshnessStatus: ReportFreshnessStatus
  reconciliationStatus: ReportReconciliationStatus
  finalityStatus: ReportFinalityStatus
  sourceCount: number
  traceKey: string | null
  amountVisible?: boolean
  restrictedReason?: string | null
}

export interface ProjectProfitDetailRecord extends ProjectProfitReportRow {
  costStageEntries: Array<{ stage: string; amount: ReportMoney | null; status: string }>
  revenueEntries: Array<{ basis: string; amount: ReportMoney | null; description: string }>
  accountingEntries: Array<{ accountCode?: string; accountName?: string; amount: ReportMoney | null; description?: string }>
  varianceReasons: Array<{ reasonCode: string; description: string; amount: ReportMoney | null }>
}

export interface ContractCollectionReportSummary {
  contractAmount?: ReportMoney | null
  invoiceAmount?: ReportMoney | null
  receivedAmount?: ReportMoney | null
  unreceivedAmount?: ReportMoney | null
  advanceReceiptAmount?: ReportMoney | null
  overdueAmount?: ReportMoney | null
  sourceCount: number
}

export interface ContractCollectionReportRow {
  projectId?: ResourceId | null
  projectNo?: string | null
  contractId: ResourceId
  contractNo: string
  customerName?: string | null
  contractAmount: ReportMoney | null
  invoiceAmount: ReportMoney | null
  receivedAmount: ReportMoney | null
  allocatedAmount: ReportMoney | null
  unreceivedAmount: ReportMoney | null
  advanceReceiptAmount: ReportMoney | null
  overdueAmount: ReportMoney | null
  collectionRate: ReportPercent | null
  status: string
  sourceCount: number
  traceKey: string | null
  restrictedReason?: string | null
}

export interface ProcurementVarianceReportSummary {
  orderAmount?: ReportMoney | null
  receiptAmount?: ReportMoney | null
  invoiceAmount?: ReportMoney | null
  paidAmount?: ReportMoney | null
  matchVarianceAmount?: ReportMoney | null
  sourceCount: number
}

export interface ProcurementVarianceReportRow {
  sourceNo: string
  supplierName?: string | null
  projectNo?: string | null
  basis: string
  orderAmount: ReportMoney | null
  receiptAmount: ReportMoney | null
  invoiceAmount: ReportMoney | null
  paidAmount: ReportMoney | null
  unreceivedOrderAmount: ReportMoney | null
  receivedUninvoicedAmount: ReportMoney | null
  invoiceReceiptDifferenceAmount: ReportMoney | null
  unpaidAmount: ReportMoney | null
  matchVarianceAmount: ReportMoney | null
  outsourcingUnsettledAmount: ReportMoney | null
  reconciliationStatus: ReportReconciliationStatus
  sourceCount: number
  traceKey: string | null
  restrictedReason?: string | null
}

export interface InventoryCapitalReportSummary {
  quantity?: ReportDecimal | null
  amount?: ReportMoney | null
  snapshotAmount?: ReportMoney | null
  differenceAmount?: ReportMoney | null
  riskQuantity?: ReportDecimal | null
  knownValuationAmount?: ReportMoney | null
  unknownValuationQuantity?: ReportDecimal | null
  completenessStatus?: ReportCompletenessStatus | null
  sourceCount: number
}

export interface InventoryCapitalReportRow {
  projectId?: ResourceId | null
  ownerType: string
  projectNo?: string | null
  warehouseName: string
  materialName: string
  qualityStatus: string
  freezeStatus: string
  valuationStatus: string
  quantity: ReportDecimal | null
  amount: ReportMoney | null
  snapshotAmount: ReportMoney | null
  differenceAmount: ReportMoney | null
  riskQuantity: ReportDecimal | null
  knownValuationAmount?: ReportMoney | null
  unknownValuationQuantity?: ReportDecimal | null
  completenessStatus?: ReportCompletenessStatus | null
  freshnessStatus: ReportFreshnessStatus
  sourceCount: number
  traceKey: string | null
  restrictedReason?: string | null
}

export interface ReceivablePayableReportSummary {
  receivableAmount?: ReportMoney | null
  payableAmount?: ReportMoney | null
  advanceReceiptAmount?: ReportMoney | null
  prepaymentAmount?: ReportMoney | null
  balanceAmount?: ReportMoney | null
  overdueAmount?: ReportMoney | null
  sourceCount: number
}

export interface ReceivablePayableReportRow {
  projectId?: ResourceId | null
  partyType: string
  partyName: string
  projectNo?: string | null
  sourceType?: string | null
  sourceNo?: string | null
  receivableAmount: ReportMoney | null
  payableAmount: ReportMoney | null
  advanceReceiptAmount: ReportMoney | null
  prepaymentAmount: ReportMoney | null
  settledAmount: ReportMoney | null
  balanceAmount: ReportMoney | null
  notDueAmount: ReportMoney | null
  aging1To30Amount: ReportMoney | null
  aging31To60Amount: ReportMoney | null
  aging61To90Amount: ReportMoney | null
  agingOver90Amount: ReportMoney | null
  overdueAmount: ReportMoney | null
  agingBucket?: string | null
  sourceCount: number
  traceKey: string | null
  restrictedReason?: string | null
}

export interface OperatingAccountingReconciliationReportSummary {
  operatingProfitAmount?: ReportMoney | null
  accountingProfitAmount?: ReportMoney | null
  publicUnallocatedAmount?: ReportMoney | null
  differenceAmount?: ReportMoney | null
  sourceCount: number
}

export interface OperatingAccountingReconciliationReportRow {
  projectId?: ResourceId | null
  projectNo: string
  projectName: string
  operatingRevenueAmount: ReportMoney | null
  operatingCostAmount: ReportMoney | null
  operatingProfitAmount: ReportMoney | null
  accountingRevenueAmount: ReportMoney | null
  accountingCostAmount: ReportMoney | null
  accountingProfitAmount: ReportMoney | null
  publicUnallocatedAmount: ReportMoney | null
  differenceAmount: ReportMoney | null
  reconciliationStatus: ReportReconciliationStatus
  finalityStatus: ReportFinalityStatus
  varianceReason?: string | null
  sourceCount: number
  traceKey: string | null
  restrictedReason?: string | null
}

export interface FinancialSummaryRecord {
  periodCode: string
  analysisMode: ReportAnalysisMode | string
  finalityStatus: ReportFinalityStatus
  businessPeriodStatus: string
  accountingPeriodStatus: string
  financialCloseStatus: string
  revenueAmount: ReportMoney | null
  mainCostAmount: ReportMoney | null
  periodExpenseAmount: ReportMoney | null
  otherProfitLossAmount: ReportMoney | null
  incomeTaxExpenseAmount: ReportMoney | null
  operatingResultAmount: ReportMoney | null
  assetBalanceAmount: ReportMoney | null
  liabilityBalanceAmount: ReportMoney | null
  equityBalanceAmount: ReportMoney | null
  trialBalanceStatus: string
  bankReconciliationStatus: string
  taxSummaryStatus: string
  sourceCount: number
  traceKey: string | null
  restrictedReason?: string | null
  legalReport: boolean
  disclaimer: string
}

interface TraceEndpoint {
  list(params: ReportTraceListParams): Promise<PageResult<ReportTraceRecord>>
}

interface OperatingFinanceTraceEndpoint {
  list(params: OperatingFinanceTraceListParams): Promise<PageResult<ReportTraceRecord>>
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
  operatingFinanceOverview: {
    get(params?: OperatingFinanceParams): Promise<OperatingFinanceOverviewRecord>
  }
  projectProfit: {
    list(params: OperatingFinanceParams & PageParams): Promise<ReportPageResult<ProjectProfitReportSummary, ProjectProfitReportRow>>
    detail: {
      get(projectId: ResourceId, params?: OperatingFinanceParams): Promise<ProjectProfitDetailRecord>
    }
    traces: {
      list(params: OperatingFinanceTraceListParams & { projectId: ResourceId }): Promise<PageResult<ReportTraceRecord>>
    }
  }
  contractCollection: {
    list(params: OperatingFinanceParams & PageParams): Promise<ReportPageResult<ContractCollectionReportSummary, ContractCollectionReportRow>>
    traces: OperatingFinanceTraceEndpoint
  }
  procurementVariance: {
    list(params: OperatingFinanceParams & PageParams): Promise<ReportPageResult<ProcurementVarianceReportSummary, ProcurementVarianceReportRow>>
    traces: OperatingFinanceTraceEndpoint
  }
  inventoryCapital: {
    list(params: OperatingFinanceParams & PageParams): Promise<ReportPageResult<InventoryCapitalReportSummary, InventoryCapitalReportRow>>
    traces: OperatingFinanceTraceEndpoint
  }
  receivablePayable: {
    list(params: OperatingFinanceParams & PageParams): Promise<ReportPageResult<ReceivablePayableReportSummary, ReceivablePayableReportRow>>
    traces: OperatingFinanceTraceEndpoint
  }
  operatingAccountingReconciliation: {
    list(params: OperatingFinanceParams & PageParams): Promise<ReportPageResult<OperatingAccountingReconciliationReportSummary, OperatingAccountingReconciliationReportRow>>
    traces: OperatingFinanceTraceEndpoint
  }
  financialSummary: {
    get(params?: OperatingFinanceParams): Promise<FinancialSummaryRecord>
    traces: OperatingFinanceTraceEndpoint
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
  const operatingFinanceQueryKeys = [
    'periodCode',
    'projectId',
    'contractId',
    'basis',
    'analysisMode',
    'completenessStatus',
    'reconciliationStatus',
    'finalityStatus',
    'keyword',
    'page',
    'pageSize',
  ] as const
  const operatingFinanceOverviewQueryKeys = [
    'periodCode',
    'analysisMode',
    'finalityStatus',
  ] as const
  const operatingFinanceTraceQueryKeys = ['traceKey', 'periodCode', 'analysisMode', 'page', 'pageSize'] as const

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
  const operatingFinanceTrace = (path: string): OperatingFinanceTraceEndpoint => ({
    list: (params) => get<PageResult<ReportTraceRecord>>(path, pickQuery(params, operatingFinanceTraceQueryKeys)),
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
    operatingFinanceOverview: {
      get: (params = {}) =>
        get<OperatingFinanceOverviewRecord>(
          '/api/admin/reports/operating-finance-overview',
          pickQuery(params, operatingFinanceOverviewQueryKeys),
        ),
    },
    projectProfit: {
      list: (params) =>
        get<ReportPageResult<ProjectProfitReportSummary, ProjectProfitReportRow>>(
          '/api/admin/reports/project-profit',
          pickQuery(params, operatingFinanceQueryKeys),
        ),
      detail: {
        get: (projectId, params = {}) =>
          get<ProjectProfitDetailRecord>(
            `/api/admin/reports/project-profit/${encodeURIComponent(String(projectId))}`,
            pickQuery(params, operatingFinanceOverviewQueryKeys),
          ),
      },
      traces: {
        list: (params) =>
          get<PageResult<ReportTraceRecord>>(
            `/api/admin/reports/project-profit/${encodeURIComponent(String(params.projectId))}/traces`,
            pickQuery(params, operatingFinanceTraceQueryKeys),
          ),
      },
    },
    contractCollection: {
      list: (params) =>
        get<ReportPageResult<ContractCollectionReportSummary, ContractCollectionReportRow>>(
          '/api/admin/reports/contract-collections',
          pickQuery(params, operatingFinanceQueryKeys),
        ),
      traces: operatingFinanceTrace('/api/admin/reports/contract-collections/traces'),
    },
    procurementVariance: {
      list: (params) =>
        get<ReportPageResult<ProcurementVarianceReportSummary, ProcurementVarianceReportRow>>(
          '/api/admin/reports/procurement-variances',
          pickQuery(params, operatingFinanceQueryKeys),
        ),
      traces: operatingFinanceTrace('/api/admin/reports/procurement-variances/traces'),
    },
    inventoryCapital: {
      list: (params) =>
        get<ReportPageResult<InventoryCapitalReportSummary, InventoryCapitalReportRow>>(
          '/api/admin/reports/inventory-capital',
          pickQuery(params, operatingFinanceQueryKeys),
        ),
      traces: operatingFinanceTrace('/api/admin/reports/inventory-capital/traces'),
    },
    receivablePayable: {
      list: (params) =>
        get<ReportPageResult<ReceivablePayableReportSummary, ReceivablePayableReportRow>>(
          '/api/admin/reports/receivable-payable',
          pickQuery(params, operatingFinanceQueryKeys),
        ),
      traces: operatingFinanceTrace('/api/admin/reports/receivable-payable/traces'),
    },
    operatingAccountingReconciliation: {
      list: (params) =>
        get<ReportPageResult<OperatingAccountingReconciliationReportSummary, OperatingAccountingReconciliationReportRow>>(
          '/api/admin/reports/operating-accounting-reconciliation',
          pickQuery(params, operatingFinanceQueryKeys),
        ),
      traces: operatingFinanceTrace('/api/admin/reports/operating-accounting-reconciliation/traces'),
    },
    financialSummary: {
      get: (params = {}) => get<FinancialSummaryRecord>('/api/admin/reports/financial-summary', pickQuery(params, operatingFinanceOverviewQueryKeys)),
      traces: operatingFinanceTrace('/api/admin/reports/financial-summary/traces'),
    },
  }
}

export const businessReportingApi = createBusinessReportingApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
