import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type { InventoryQualityStatus, InventoryTrackingAllocationPayload } from './inventoryApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type ReversalDecimal = string
export type ReversalMoney = string
export type ReversalRouteValue = string | number | boolean
export type ReversalStatus = 'DRAFT' | 'POSTED' | 'CANCELLED'
export type SalesReturnAction = 'UPDATE' | 'POST' | 'CANCEL'
export type ReversalTraceDirection = 'SOURCE_TO_REVERSE' | 'REVERSE_TO_SOURCE'
export type SettlementSide = 'RECEIVABLE' | 'PAYABLE'
export type SettlementAdjustmentType = 'RETURN_OFFSET' | 'REFUND' | 'PAYMENT_OFFSET'
export type SettlementAdjustmentSourceType = 'SALES_RETURN' | 'PURCHASE_RETURN' | 'RECEIPT' | 'PAYMENT' | 'SETTLEMENT_ADJUSTMENT'
export type ReversalProcurementMode = 'PUBLIC' | 'PROJECT'

export interface PurchaseReturnCostSourceFields {
  procurementMode?: ReversalProcurementMode | string | null
  projectId?: ResourceId | null
  projectCode?: string | null
  projectName?: string | null
  originalCostLayerNo?: string | null
  originalValueMovementNo?: string | null
  costVisible?: boolean | null
}

export interface ReversalSourceView extends PurchaseReturnCostSourceFields {
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
  effectType?: string | null
  resourceType?: string | null
  inventoryMovementId?: ResourceId | null
  movementNo?: string | null
  movementType?: string | null
  warehouseName?: string | null
  materialCode?: string | null
  materialName?: string | null
  settlementAdjustmentId?: ResourceId | null
  costRecordId?: ResourceId | null
  businessDate?: string
  quantity?: ReversalDecimal
  amount?: ReversalMoney
  status?: string
  canViewResource: boolean
  restricted: boolean
  restrictedMessage?: string
  resourceRouteName?: string
  resourceRouteParams?: Record<string, ReversalRouteValue>
  resourceRouteQuery?: Record<string, ReversalRouteValue>
}

export interface ReversalDocumentLine extends PurchaseReturnCostSourceFields {
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
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  reason?: string
  stockMovementId?: ResourceId | null
  costRecordId?: ResourceId | null
  ownershipType?: ReversalProcurementMode | string | null
  projectNo?: string | null
  costLayerId?: ResourceId | null
  trackingAllocations?: InventoryTrackingAllocationPayload[]
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
  allowedActions: SalesReturnAction[]
  actionDisabledReason?: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface SalesReturnDetail extends SalesReturnSummary {
  clientRequestId?: string
  remark?: string
  lines: ReversalDocumentLine[]
  traces: ReversalTraceRecord[]
}

export interface PurchaseReturnSummary extends PurchaseReturnCostSourceFields {
  id: ResourceId
  returnNo: string
  supplierId: ResourceId
  supplierName: string
  warehouseId: ResourceId
  warehouseName: string
  businessDate: string
  status: ReversalStatus
  totalQuantity: ReversalDecimal
  totalAmount: ReversalMoney
  source: ReversalSourceView
  allowedActions?: string[]
  createdAt: string
  updatedAt: string
  version: number
}

export interface PurchaseReturnDetail extends PurchaseReturnSummary {
  clientRequestId?: string
  remark?: string
  lines: ReversalDocumentLine[]
  traces: ReversalTraceRecord[]
}

export interface ProductionMaterialReturnSummary {
  id: ResourceId
  returnNo: string
  workOrderId: ResourceId
  workOrderNo: string
  warehouseId: ResourceId
  warehouseName: string
  businessDate: string
  status: ReversalStatus
  totalQuantity: ReversalDecimal
  totalAmount?: ReversalMoney
  source: ReversalSourceView
  createdAt: string
  updatedAt: string
  version: number
}

export interface ProductionMaterialReturnDetail extends ProductionMaterialReturnSummary {
  clientRequestId?: string
  remark?: string
  lines: ReversalDocumentLine[]
  traces: ReversalTraceRecord[]
}

export interface ProductionMaterialSupplementSummary {
  id: ResourceId
  supplementNo: string
  workOrderId: ResourceId
  workOrderNo: string
  warehouseId: ResourceId
  warehouseName: string
  businessDate: string
  status: ReversalStatus
  totalQuantity: ReversalDecimal
  totalAmount?: ReversalMoney
  source: ReversalSourceView
  createdAt: string
  updatedAt: string
  version: number
}

export interface ProductionMaterialSupplementDetail extends ProductionMaterialSupplementSummary {
  clientRequestId?: string
  remark?: string
  lines: ReversalDocumentLine[]
  traces: ReversalTraceRecord[]
}

export interface SettlementAdjustmentSummary {
  id: ResourceId
  adjustmentNo: string
  settlementSide: SettlementSide
  adjustmentType: SettlementAdjustmentType
  source: ReversalSourceView
  targetId: ResourceId
  targetNo: string
  businessDate: string
  targetOriginalAmount: ReversalMoney
  targetAdjustedAmountBefore: ReversalMoney
  targetAdjustableAmountBefore: ReversalMoney
  amount: ReversalMoney
  targetRemainingAmountAfterPost: ReversalMoney
  targetStatusAfterPost: string
  status: ReversalStatus
  createdAt: string
  updatedAt: string
}

export interface SettlementAdjustmentDetail extends SettlementAdjustmentSummary {
  clientRequestId?: string
  remark?: string
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
  trackingAllocations?: InventoryTrackingAllocationPayload[]
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

export interface PurchaseReturnSourceLine extends PurchaseReturnCostSourceFields {
  receiptLineId: ResourceId
  purchaseOrderLineId?: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  receivedQuantity: ReversalDecimal
  returnedQuantity: ReversalDecimal
  returnableQuantity: ReversalDecimal
  availableStockQuantity: ReversalDecimal
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  quantityOnHand?: ReversalDecimal | null
  availableQuantity?: ReversalDecimal | null
  selectable?: boolean | null
  disabledReasonCode?: string | null
  disabledReason?: string | null
  maxSelectableQuantity?: ReversalDecimal | null
  unitPrice: ReversalMoney
  returnableAmount: ReversalMoney
}

export interface PurchaseReturnSource extends PurchaseReturnCostSourceFields {
  receiptId: ResourceId
  receiptNo: string
  supplierId: ResourceId
  supplierName: string
  warehouseId: ResourceId
  warehouseName: string
  businessDate: string
  status: 'POSTED'
  lines: PurchaseReturnSourceLine[]
}

export interface ProductionMaterialReturnSourceLine {
  issueLineId: ResourceId
  workOrderMaterialId?: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  issuedQuantity: ReversalDecimal
  returnedQuantity: ReversalDecimal
  returnableQuantity: ReversalDecimal
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  unitPrice: ReversalMoney
  returnableAmount: ReversalMoney
  ownershipType?: ReversalProcurementMode | string | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  costLayerId?: ResourceId | null
}

export interface ProductionMaterialReturnSource {
  issueId: ResourceId
  issueNo: string
  workOrderId: ResourceId
  workOrderNo: string
  warehouseId: ResourceId
  warehouseName: string
  businessDate: string
  status: 'POSTED'
  lines: ProductionMaterialReturnSourceLine[]
}

export interface ProductionMaterialSupplementSourceMaterial {
  workOrderMaterialId: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  plannedQuantity: ReversalDecimal
  issuedQuantity: ReversalDecimal
  supplementedQuantity: ReversalDecimal
  availableStockQuantity: ReversalDecimal
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  quantityOnHand?: ReversalDecimal | null
  availableQuantity?: ReversalDecimal | null
  selectable?: boolean | null
  disabledReasonCode?: string | null
  disabledReason?: string | null
  maxSelectableQuantity?: ReversalDecimal | null
  unitPrice: ReversalMoney
  ownershipType?: ReversalProcurementMode | string | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  costLayerId?: ResourceId | null
}

export interface ProductionMaterialSupplementSource {
  workOrderId: ResourceId
  workOrderNo: string
  workOrderStatus: string
  warehouseId: ResourceId
  warehouseName: string
  materials: ProductionMaterialSupplementSourceMaterial[]
}

export interface SettlementAdjustmentSource {
  sourceType: SettlementAdjustmentSourceType
  sourceId: ResourceId
  sourceNo: string
  settlementSide: SettlementSide
  targetId: ResourceId
  targetNo: string
  businessDate: string
  originalAmount: ReversalMoney
  adjustedAmount: ReversalMoney
  adjustableAmount: ReversalMoney
  status: string
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

export interface PurchaseReturnListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  warehouseId?: ResourceId | null
  status?: ReversalStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  page: number
  pageSize: number
}

export interface PurchaseReturnSourceListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  warehouseId?: ResourceId | null
  dateFrom?: string | null
  dateTo?: string | null
  page: number
  pageSize: number
}

export interface ProductionMaterialReturnListParams {
  keyword?: string | null
  workOrderId?: ResourceId | null
  warehouseId?: ResourceId | null
  status?: ReversalStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  page: number
  pageSize: number
}

export interface ProductionMaterialReturnSourceListParams {
  keyword?: string | null
  workOrderId?: ResourceId | null
  warehouseId?: ResourceId | null
  dateFrom?: string | null
  dateTo?: string | null
  page: number
  pageSize: number
}

export interface ProductionMaterialSupplementListParams {
  keyword?: string | null
  workOrderId?: ResourceId | null
  warehouseId?: ResourceId | null
  status?: ReversalStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  page: number
  pageSize: number
}

export interface ProductionMaterialSupplementSourceListParams {
  keyword?: string | null
  workOrderId?: ResourceId | null
  warehouseId?: ResourceId | null
  page: number
  pageSize: number
}

export interface SettlementAdjustmentListParams {
  keyword?: string | null
  settlementSide?: SettlementSide | null
  adjustmentType?: SettlementAdjustmentType | null
  sourceType?: SettlementAdjustmentSourceType | null
  status?: ReversalStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  page: number
  pageSize: number
}

export interface SettlementAdjustmentSourceListParams {
  keyword?: string | null
  settlementSide?: SettlementSide | null
  sourceType?: SettlementAdjustmentSourceType | null
  customerId?: ResourceId | null
  supplierId?: ResourceId | null
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

export interface SalesReturnCreatePayloadLine {
  sourceShipmentLineId: ResourceId
  quantity: ReversalDecimal
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  reason?: string
}

export interface SalesReturnUpdatePayloadLine {
  id?: ResourceId
  sourceShipmentLineId?: ResourceId
  quantity: ReversalDecimal
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  reason?: string
}

export interface SalesReturnCreatePayload {
  sourceShipmentId: ResourceId
  businessDate: string
  clientRequestId: string
  remark?: string
  lines: SalesReturnCreatePayloadLine[]
}

export interface SalesReturnUpdatePayload {
  sourceShipmentId?: ResourceId
  businessDate: string
  clientRequestId: string
  remark?: string
  lines: SalesReturnUpdatePayloadLine[]
}

export type ReversalDocumentLinePayload = SalesReturnCreatePayloadLine
export type ReversalDocumentPayload = SalesReturnCreatePayload

export interface PurchaseReturnCreatePayloadLine {
  sourceReceiptLineId: ResourceId
  qualityStatus?: InventoryQualityStatus
  quantity: ReversalDecimal
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  reason?: string
}

export interface PurchaseReturnUpdatePayloadLine {
  id?: ResourceId
  sourceReceiptLineId?: ResourceId
  qualityStatus?: InventoryQualityStatus
  quantity: ReversalDecimal
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  reason?: string
}

export interface PurchaseReturnCreatePayload {
  sourceReceiptId: ResourceId
  businessDate: string
  clientRequestId: string
  remark?: string
  lines: PurchaseReturnCreatePayloadLine[]
}

export interface PurchaseReturnUpdatePayload {
  sourceReceiptId?: ResourceId
  businessDate: string
  clientRequestId: string
  remark?: string
  lines: PurchaseReturnUpdatePayloadLine[]
  version: number
}

export type PurchaseReturnPayloadLine = PurchaseReturnCreatePayloadLine
export type PurchaseReturnPayload = PurchaseReturnCreatePayload

export interface ReversalVersionPayload {
  version: number
  idempotencyKey: string
  reason?: string
}

export interface ProductionMaterialReturnCreatePayloadLine {
  sourceIssueLineId: ResourceId
  quantity: ReversalDecimal
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  reason?: string
}

export interface ProductionMaterialReturnUpdatePayloadLine {
  id?: ResourceId
  sourceIssueLineId?: ResourceId
  quantity: ReversalDecimal
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  reason?: string
}

export interface ProductionMaterialReturnCreatePayload {
  sourceIssueId: ResourceId
  businessDate: string
  clientRequestId: string
  idempotencyKey: string
  remark?: string
  lines: ProductionMaterialReturnCreatePayloadLine[]
}

export interface ProductionMaterialReturnUpdatePayload {
  sourceIssueId?: ResourceId
  businessDate: string
  clientRequestId: string
  idempotencyKey: string
  remark?: string
  lines: ProductionMaterialReturnUpdatePayloadLine[]
  version: number
}

export type ProductionMaterialReturnPayload = ProductionMaterialReturnCreatePayload

export interface ProductionMaterialSupplementCreatePayloadLine {
  workOrderMaterialId: ResourceId
  quantity: ReversalDecimal
  ownershipType?: ReversalProcurementMode | string | null
  projectId?: ResourceId | null
  costLayerId?: ResourceId | null
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  reason?: string
}

export interface ProductionMaterialSupplementUpdatePayloadLine {
  id?: ResourceId
  workOrderMaterialId?: ResourceId
  quantity: ReversalDecimal
  ownershipType?: ReversalProcurementMode | string | null
  projectId?: ResourceId | null
  costLayerId?: ResourceId | null
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  reason?: string
}

export interface ProductionMaterialSupplementCreatePayload {
  workOrderId: ResourceId
  warehouseId: ResourceId
  businessDate: string
  clientRequestId: string
  idempotencyKey: string
  remark?: string
  lines: ProductionMaterialSupplementCreatePayloadLine[]
}

export interface ProductionMaterialSupplementUpdatePayload {
  workOrderId?: ResourceId
  warehouseId?: ResourceId
  businessDate: string
  clientRequestId: string
  idempotencyKey: string
  remark?: string
  lines: ProductionMaterialSupplementUpdatePayloadLine[]
  version: number
}

export type ProductionMaterialSupplementPayload = ProductionMaterialSupplementCreatePayload

export interface SettlementAdjustmentCreatePayload {
  settlementSide: SettlementSide
  adjustmentType: SettlementAdjustmentType
  sourceType: SettlementAdjustmentSourceType
  sourceId: ResourceId
  targetId: ResourceId
  businessDate: string
  amount: ReversalMoney
  clientRequestId: string
  remark?: string
}

export interface SettlementAdjustmentUpdatePayload {
  settlementSide?: SettlementSide
  adjustmentType?: SettlementAdjustmentType
  sourceType?: SettlementAdjustmentSourceType
  sourceId?: ResourceId
  targetId?: ResourceId
  businessDate: string
  amount: ReversalMoney
  clientRequestId: string
  remark?: string
}

export type SettlementAdjustmentPayload = SettlementAdjustmentCreatePayload

export interface ReturnRefundReversalApi {
  salesReturns: {
    list(params: SalesReturnListParams): Promise<PageResult<SalesReturnSummary>>
    get(id: ResourceId): Promise<SalesReturnDetail>
    create(payload: ReversalDocumentPayload): Promise<SalesReturnDetail>
    update(id: ResourceId, payload: SalesReturnUpdatePayload): Promise<SalesReturnDetail>
    post(id: ResourceId, payload: ReversalVersionPayload): Promise<SalesReturnDetail>
    cancel(id: ResourceId, payload: ReversalVersionPayload): Promise<SalesReturnDetail>
  }
  salesReturnSources: {
    list(params: SalesReturnSourceListParams): Promise<PageResult<SalesReturnSource>>
  }
  purchaseReturns: {
    list(params: PurchaseReturnListParams): Promise<PageResult<PurchaseReturnSummary>>
    get(id: ResourceId): Promise<PurchaseReturnDetail>
    create(payload: PurchaseReturnPayload): Promise<PurchaseReturnDetail>
    update(id: ResourceId, payload: PurchaseReturnUpdatePayload): Promise<PurchaseReturnDetail>
    post(id: ResourceId, payload: ReversalVersionPayload): Promise<PurchaseReturnDetail>
    cancel(id: ResourceId, payload: ReversalVersionPayload): Promise<PurchaseReturnDetail>
  }
  purchaseReturnSources: {
    list(params: PurchaseReturnSourceListParams): Promise<PageResult<PurchaseReturnSource>>
  }
  productionMaterialReturns: {
    list(params: ProductionMaterialReturnListParams): Promise<PageResult<ProductionMaterialReturnSummary>>
    get(id: ResourceId): Promise<ProductionMaterialReturnDetail>
    create(payload: ProductionMaterialReturnPayload): Promise<ProductionMaterialReturnDetail>
    update(id: ResourceId, payload: ProductionMaterialReturnUpdatePayload): Promise<ProductionMaterialReturnDetail>
    post(id: ResourceId, payload: ReversalVersionPayload): Promise<ProductionMaterialReturnDetail>
    cancel(id: ResourceId, payload: ReversalVersionPayload): Promise<ProductionMaterialReturnDetail>
  }
  productionMaterialReturnSources: {
    list(params: ProductionMaterialReturnSourceListParams): Promise<PageResult<ProductionMaterialReturnSource>>
  }
  productionMaterialSupplements: {
    list(params: ProductionMaterialSupplementListParams): Promise<PageResult<ProductionMaterialSupplementSummary>>
    get(id: ResourceId): Promise<ProductionMaterialSupplementDetail>
    create(payload: ProductionMaterialSupplementPayload): Promise<ProductionMaterialSupplementDetail>
    update(id: ResourceId, payload: ProductionMaterialSupplementUpdatePayload): Promise<ProductionMaterialSupplementDetail>
    post(id: ResourceId, payload: ReversalVersionPayload): Promise<ProductionMaterialSupplementDetail>
    cancel(id: ResourceId, payload: ReversalVersionPayload): Promise<ProductionMaterialSupplementDetail>
  }
  productionMaterialSupplementSources: {
    list(params: ProductionMaterialSupplementSourceListParams): Promise<PageResult<ProductionMaterialSupplementSource>>
  }
  settlementAdjustments: {
    list(params: SettlementAdjustmentListParams): Promise<PageResult<SettlementAdjustmentSummary>>
    get(id: ResourceId): Promise<SettlementAdjustmentDetail>
    create(payload: SettlementAdjustmentPayload): Promise<SettlementAdjustmentDetail>
    update(id: ResourceId, payload: SettlementAdjustmentUpdatePayload): Promise<SettlementAdjustmentDetail>
    post(id: ResourceId): Promise<SettlementAdjustmentDetail>
    cancel(id: ResourceId): Promise<SettlementAdjustmentDetail>
  }
  settlementAdjustmentSources: {
    list(params: SettlementAdjustmentSourceListParams): Promise<PageResult<SettlementAdjustmentSource>>
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
  const purchaseReturnQueryKeys = [
    'keyword',
    'supplierId',
    'warehouseId',
    'status',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const purchaseReturnSourceQueryKeys = [
    'keyword',
    'supplierId',
    'warehouseId',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const productionMaterialReturnQueryKeys = [
    'keyword',
    'workOrderId',
    'warehouseId',
    'status',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const productionMaterialReturnSourceQueryKeys = [
    'keyword',
    'workOrderId',
    'warehouseId',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const productionMaterialSupplementQueryKeys = [
    'keyword',
    'workOrderId',
    'warehouseId',
    'status',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const productionMaterialSupplementSourceQueryKeys = [
    'keyword',
    'workOrderId',
    'warehouseId',
    'page',
    'pageSize',
  ] as const
  const settlementAdjustmentQueryKeys = [
    'keyword',
    'settlementSide',
    'adjustmentType',
    'sourceType',
    'status',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const settlementAdjustmentSourceQueryKeys = [
    'keyword',
    'settlementSide',
    'sourceType',
    'customerId',
    'supplierId',
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
  const assertIdempotencyKey = (payload: { idempotencyKey?: string }) => {
    if (typeof payload.idempotencyKey !== 'string' || payload.idempotencyKey.trim() === '') {
      throw new Error('幂等键不能为空')
    }
  }
  const writeAction = async <T>(method: 'POST' | 'PUT', path: string, payload: { idempotencyKey?: string }) => {
    assertIdempotencyKey(payload)
    return write<T>(method, path, payload)
  }

  const salesReturnPath = (id?: ResourceId) =>
    `/api/admin/sales/returns${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const purchaseReturnPath = (id?: ResourceId) =>
    `/api/admin/procurement/returns${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const productionMaterialReturnPath = (id?: ResourceId) =>
    `/api/admin/production/material-returns${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const productionMaterialSupplementPath = (id?: ResourceId) =>
    `/api/admin/production/material-supplements${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const settlementAdjustmentPath = (id?: ResourceId) =>
    `/api/admin/finance/settlement-adjustments${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`

  return {
    salesReturns: {
      list: (params) =>
        get<PageResult<SalesReturnSummary>>('/api/admin/sales/returns', pickQuery(params, salesReturnQueryKeys)),
      get: (id) => get<SalesReturnDetail>(salesReturnPath(id)),
      create: (payload) => write<SalesReturnDetail>('POST', salesReturnPath(), payload),
      update: (id, payload) => write<SalesReturnDetail>('PUT', salesReturnPath(id), payload),
      post: (id, payload) => writeAction<SalesReturnDetail>('POST', `${salesReturnPath(id)}/post`, payload),
      cancel: (id, payload) => writeAction<SalesReturnDetail>('POST', `${salesReturnPath(id)}/cancel`, payload),
    },
    salesReturnSources: {
      list: (params) =>
        get<PageResult<SalesReturnSource>>('/api/admin/sales/return-sources', pickQuery(params, salesReturnSourceQueryKeys)),
    },
    purchaseReturns: {
      list: (params) =>
        get<PageResult<PurchaseReturnSummary>>('/api/admin/procurement/returns', pickQuery(params, purchaseReturnQueryKeys)),
      get: (id) => get<PurchaseReturnDetail>(purchaseReturnPath(id)),
      create: (payload) => write<PurchaseReturnDetail>('POST', purchaseReturnPath(), payload),
      update: (id, payload) => write<PurchaseReturnDetail>('PUT', purchaseReturnPath(id), payload),
      post: (id, payload) => writeAction<PurchaseReturnDetail>('PUT', `${purchaseReturnPath(id)}/post`, payload),
      cancel: (id, payload) => writeAction<PurchaseReturnDetail>('PUT', `${purchaseReturnPath(id)}/cancel`, payload),
    },
    purchaseReturnSources: {
      list: (params) =>
        get<PageResult<PurchaseReturnSource>>('/api/admin/procurement/return-sources', pickQuery(params, purchaseReturnSourceQueryKeys)),
    },
    productionMaterialReturns: {
      list: (params) =>
        get<PageResult<ProductionMaterialReturnSummary>>(
          '/api/admin/production/material-returns',
          pickQuery(params, productionMaterialReturnQueryKeys),
        ),
      get: (id) => get<ProductionMaterialReturnDetail>(productionMaterialReturnPath(id)),
      create: (payload) => writeAction<ProductionMaterialReturnDetail>('POST', productionMaterialReturnPath(), payload),
      update: (id, payload) => writeAction<ProductionMaterialReturnDetail>('PUT', productionMaterialReturnPath(id), payload),
      post: (id, payload) => writeAction<ProductionMaterialReturnDetail>('PUT', `${productionMaterialReturnPath(id)}/post`, payload),
      cancel: (id, payload) => writeAction<ProductionMaterialReturnDetail>('PUT', `${productionMaterialReturnPath(id)}/cancel`, payload),
    },
    productionMaterialReturnSources: {
      list: (params) =>
        get<PageResult<ProductionMaterialReturnSource>>(
          '/api/admin/production/material-return-sources',
          pickQuery(params, productionMaterialReturnSourceQueryKeys),
        ),
    },
    productionMaterialSupplements: {
      list: (params) =>
        get<PageResult<ProductionMaterialSupplementSummary>>(
          '/api/admin/production/material-supplements',
          pickQuery(params, productionMaterialSupplementQueryKeys),
        ),
      get: (id) => get<ProductionMaterialSupplementDetail>(productionMaterialSupplementPath(id)),
      create: (payload) => writeAction<ProductionMaterialSupplementDetail>('POST', productionMaterialSupplementPath(), payload),
      update: (id, payload) => writeAction<ProductionMaterialSupplementDetail>('PUT', productionMaterialSupplementPath(id), payload),
      post: (id, payload) => writeAction<ProductionMaterialSupplementDetail>('PUT', `${productionMaterialSupplementPath(id)}/post`, payload),
      cancel: (id, payload) => writeAction<ProductionMaterialSupplementDetail>('PUT', `${productionMaterialSupplementPath(id)}/cancel`, payload),
    },
    productionMaterialSupplementSources: {
      list: (params) =>
        get<PageResult<ProductionMaterialSupplementSource>>(
          '/api/admin/production/material-supplement-sources',
          pickQuery(params, productionMaterialSupplementSourceQueryKeys),
        ),
    },
    settlementAdjustments: {
      list: (params) =>
        get<PageResult<SettlementAdjustmentSummary>>(
          '/api/admin/finance/settlement-adjustments',
          pickQuery(params, settlementAdjustmentQueryKeys),
        ),
      get: (id) => get<SettlementAdjustmentDetail>(settlementAdjustmentPath(id)),
      create: (payload) => write<SettlementAdjustmentDetail>('POST', settlementAdjustmentPath(), payload),
      update: (id, payload) => write<SettlementAdjustmentDetail>('PUT', settlementAdjustmentPath(id), payload),
      post: (id) => write<SettlementAdjustmentDetail>('PUT', `${settlementAdjustmentPath(id)}/post`),
      cancel: (id) => write<SettlementAdjustmentDetail>('PUT', `${settlementAdjustmentPath(id)}/cancel`),
    },
    settlementAdjustmentSources: {
      list: (params) =>
        get<PageResult<SettlementAdjustmentSource>>(
          '/api/admin/finance/settlement-adjustment-sources',
          pickQuery(params, settlementAdjustmentSourceQueryKeys),
        ),
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
