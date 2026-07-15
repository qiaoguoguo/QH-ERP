import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type { ApprovalInstanceDetail } from './documentPlatformApi'
import type {
  InventoryDirection,
  InventoryMovementType,
  InventoryTrackingAllocationPayload,
  InventoryTrackingMethod,
} from './inventoryApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type ProcurementMode = 'PUBLIC' | 'PROJECT'
export type ProcurementAllowedAction = string
export type PurchaseOrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CLOSED' | 'CANCELLED'
export type PurchaseReceiptStatus = 'DRAFT' | 'POSTED'
export type PurchaseInTransitStatus = 'NORMAL' | 'DUE_SOON' | 'OVERDUE' | 'NOT_COUNTED'
export type ProcurementQuantityPayload = string
export type ProcurementUnitPricePayload = string
export type ProcurementMoneyPayload = string
export type ProcurementTaxRatePayload = string
export type ProcurementRequisitionStatus = 'DRAFT' | 'APPROVED' | 'PARTIALLY_ORDERED' | 'ORDERED' | 'CLOSED' | 'CANCELLED'
export type ProcurementInquiryStatus = 'DRAFT' | 'RELEASED' | 'COMPLETED' | 'AWARDED' | 'CANCELLED'
export type SupplierQuoteStatus = 'DRAFT' | 'VALID' | 'SELECTED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED'
export type PriceAgreementStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED' | 'EXPIRED' | 'CANCELLED'
export type PurchaseScheduleStatus = 'PLANNED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CLOSED' | 'CANCELLED'

export interface ProcurementPageParams {
  page: number
  pageSize: number
}

export interface ProcurementOwnershipFields {
  purchaseMode?: ProcurementMode | string | null
  procurementMode?: ProcurementMode | string | null
  ownershipType?: ProcurementMode | string | null
  projectId?: ResourceId | null
  projectCode?: string | null
  projectName?: string | null
}

export interface ProcurementApprovalFields {
  approvalStatus?: string | null
  approvalInstanceId?: ResourceId | null
  approvalStatusName?: string | null
  exceptionApprovalStatus?: string | null
  exceptionApprovalInstanceId?: ResourceId | null
  exceptionReason?: string | null
}

export interface ProcurementPriceSnapshotFields {
  currency: 'CNY' | string
  taxRate?: ProcurementTaxRatePayload | null
  taxIncludedUnitPrice?: ProcurementUnitPricePayload | null
  taxExcludedUnitPrice?: ProcurementUnitPricePayload | null
  taxIncludedAmount?: ProcurementMoneyPayload | null
  taxExcludedAmount?: ProcurementMoneyPayload | null
  priceSourceType?: string | null
  priceSourceTypeName?: string | null
  priceSourceId?: ResourceId | null
  priceSourceNo?: string | null
  sourceNo?: string | null
  priceSourceReason?: string | null
  lowestEffectiveQuote?: boolean | null
}

export interface ProcurementRequisitionListParams extends ProcurementPageParams {
  keyword?: string | null
  procurementMode?: ProcurementMode | null
  projectId?: ResourceId | null
  status?: ProcurementRequisitionStatus | null
  approvalStatus?: string | null
  requiredDateFrom?: string | null
  requiredDateTo?: string | null
}

export interface ProcurementInquiryListParams extends ProcurementPageParams {
  keyword?: string | null
  procurementMode?: ProcurementMode | null
  projectId?: ResourceId | null
  status?: ProcurementInquiryStatus | null
}

export interface SupplierQuoteListParams extends ProcurementPageParams {
  supplierId?: ResourceId | null
  status?: SupplierQuoteStatus | null
}

export interface PriceAgreementListParams extends ProcurementPageParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  materialId?: ResourceId | null
  procurementMode?: ProcurementMode | null
  projectId?: ResourceId | null
  status?: PriceAgreementStatus | null
}

export interface PurchaseScheduleListParams extends ProcurementPageParams {
  status?: PurchaseScheduleStatus | null
  expectedDateFrom?: string | null
  expectedDateTo?: string | null
}

export interface EffectivePurchaseSupplyListParams extends ProcurementPageParams {
  projectId?: ResourceId | null
  materialId?: ResourceId | null
  supplierId?: ResourceId | null
  procurementMode?: ProcurementMode | null
  status?: PurchaseOrderStatus | PurchaseScheduleStatus | string | null
  expectedDateFrom?: string | null
  expectedDateTo?: string | null
  countedOnly?: boolean | null
}

export interface PurchaseOrderListParams extends ProcurementPageParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  status?: PurchaseOrderStatus | null
  procurementMode?: ProcurementMode | null
  projectId?: ResourceId | null
  dateFrom?: string | null
  dateTo?: string | null
  expectedDateFrom?: string | null
  expectedDateTo?: string | null
}

export interface PurchaseReceiptListParams extends ProcurementPageParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  warehouseId?: ResourceId | null
  status?: PurchaseReceiptStatus | null
  procurementMode?: ProcurementMode | null
  projectId?: ResourceId | null
  dateFrom?: string | null
  dateTo?: string | null
  orderId?: ResourceId | null
}

export interface PurchaseOrderSummaryRecord extends ProcurementOwnershipFields, ProcurementApprovalFields, ProcurementPriceSnapshotFields {
  id: ResourceId
  orderNo: string
  supplierId: ResourceId
  supplierCode: string
  supplierName: string
  orderDate: string
  expectedArrivalDate?: string | null
  status: PurchaseOrderStatus
  statusName?: string | null
  lineCount: number
  totalQuantity: string
  receivedQuantity: string
  remainingQuantity: string
  inTransitQuantity?: string | number | null
  inTransitStatus?: PurchaseInTransitStatus | string | null
  inTransitStatusName?: string | null
  nextArrivalDate?: string | null
  requisitionNo?: string | null
  inquiryNo?: string | null
  quoteNo?: string | null
  agreementNo?: string | null
  closeReason?: string | null
  allowedActions?: ProcurementAllowedAction[]
  lines?: PurchaseOrderLineRecord[]
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
  version: number
}

export interface PurchaseOrderLineRecord extends ProcurementOwnershipFields, ProcurementPriceSnapshotFields {
  id: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  materialSpec?: string | null
  unitId: ResourceId
  unitName: string
  quantity: string
  receivedQuantity: string
  remainingQuantity: string
  inTransitQuantity?: string | number | null
  inTransitStatus?: PurchaseInTransitStatus | string | null
  inTransitStatusName?: string | null
  unitPrice: string
  expectedArrivalDate?: string | null
  requisitionLineId?: ResourceId | null
  requisitionNo?: string | null
  quoteLineId?: ResourceId | null
  sourceQuoteLineId?: ResourceId | null
  quoteId?: ResourceId | null
  quoteNo?: string | null
  priceAgreementLineId?: ResourceId | null
  agreementId?: ResourceId | null
  agreementNo?: string | null
  schedules?: PurchaseScheduleRecord[]
  remark?: string | null
}

export interface PurchaseReceiptSummaryRecord extends ProcurementOwnershipFields {
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
  statusName?: string | null
  lineCount: number
  totalQuantity: string
  valuationState?: string | null
  valuationStateName?: string | null
  costVisible?: boolean | null
  taxExcludedAmount?: string | null
  allowedActions?: ProcurementAllowedAction[]
  remark?: string | null
  createdByName: string
  createdAt: string
  updatedAt: string
  postedByName?: string | null
  postedAt?: string | null
  version: number
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
  trackingMethod?: InventoryTrackingMethod | null
  trackingMethodName?: string | null
  unitId: ResourceId
  unitName: string
  orderedQuantity: string
  receivedQuantityBefore: string
  remainingQuantityBefore: string
  inTransitQuantity?: string | number | null
  inTransitStatus?: PurchaseInTransitStatus | string | null
  inTransitStatusName?: string | null
  quantity: string
  beforeQuantity?: string | null
  afterQuantity?: string | null
  scheduleId?: ResourceId | null
  scheduleSeq?: number | null
  costLayerId?: ResourceId | null
  costLayerNo?: string | null
  valueMovementNo?: string | null
  costVisible?: boolean | null
  taxExcludedAmount?: string | null
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string | null
}

export interface PurchaseReceiptInventoryMovementRecord {
  id?: ResourceId | null
  movementNo?: string | null
  movementType: InventoryMovementType
  direction: InventoryDirection
  warehouseName: string
  materialCode: string
  materialName: string
  quantity: string
  beforeQuantity?: string | null
  afterQuantity?: string | null
  businessDate?: string | null
  operatorName?: string | null
  occurredAt?: string | null
}

export interface PurchaseReceiptDetailRecord extends PurchaseReceiptSummaryRecord {
  lines: PurchaseReceiptLineRecord[]
  orderSummary: PurchaseOrderSummaryRecord
  inventoryMovements?: PurchaseReceiptInventoryMovementRecord[]
}

export interface ProcurementRequisitionLineRecord extends ProcurementOwnershipFields {
  id: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  materialSpec?: string | null
  unitId: ResourceId
  unitName: string
  quantity: string
  orderedQuantity: string
  remainingQuantity: string
  requiredDate: string
  suggestedSupplierId?: ResourceId | null
  suggestedSupplierName?: string | null
  taxRate?: ProcurementTaxRatePayload | null
  purpose?: string | null
  closeReason?: string | null
  remark?: string | null
}

export interface ProcurementRequisitionSummaryRecord extends ProcurementOwnershipFields, ProcurementApprovalFields {
  id: ResourceId
  requisitionNo: string
  title?: string | null
  requiredDate: string
  status: ProcurementRequisitionStatus
  statusName?: string | null
  materialSummary?: string | null
  lineCount: number
  totalQuantity: string
  orderedQuantity: string
  remainingQuantity: string
  closeReason?: string | null
  remark?: string | null
  allowedActions?: ProcurementAllowedAction[]
  createdByName: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface ProcurementRequisitionDetailRecord extends ProcurementRequisitionSummaryRecord {
  lines: ProcurementRequisitionLineRecord[]
  sourceChain?: ProcurementSourceChainRecord[]
}

export interface ProcurementInquiryLineRecord extends ProcurementOwnershipFields {
  id: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName: string
  quantity: string
  requiredDate?: string | null
  requisitionLineId?: ResourceId | null
  requisitionNo?: string | null
}

export interface ProcurementInquirySummaryRecord extends ProcurementOwnershipFields {
  id: ResourceId
  inquiryNo: string
  title?: string | null
  status: ProcurementInquiryStatus
  statusName?: string | null
  supplierCount: number
  quoteCount: number
  materialSummary?: string | null
  releasedAt?: string | null
  completedAt?: string | null
  remark?: string | null
  allowedActions?: ProcurementAllowedAction[]
  createdByName: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface ProcurementInquiryDetailRecord extends ProcurementInquirySummaryRecord {
  lines: ProcurementInquiryLineRecord[]
  quotes: SupplierQuoteRecord[]
  sourceChain?: ProcurementSourceChainRecord[]
}

export interface SupplierQuoteRecord extends ProcurementOwnershipFields, ProcurementPriceSnapshotFields {
  id: ResourceId
  inquiryId: ResourceId
  inquiryNo: string
  quoteNo: string
  supplierId: ResourceId
  supplierCode?: string | null
  supplierName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  quantity: string
  minPurchaseQuantity?: string | null
  deliveryDate?: string | null
  validFrom?: string | null
  validTo?: string | null
  status: SupplierQuoteStatus
  statusName?: string | null
  selectedReason?: string | null
  allowedActions?: ProcurementAllowedAction[]
  version: number
}

export interface PriceAgreementSummaryRecord extends ProcurementOwnershipFields, ProcurementApprovalFields, ProcurementPriceSnapshotFields {
  id: ResourceId
  agreementNo: string
  supplierId: ResourceId
  supplierCode?: string | null
  supplierName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  minPurchaseQuantity?: string | null
  validFrom: string
  validTo: string
  status: PriceAgreementStatus
  statusName?: string | null
  usageCount?: number | null
  remark?: string | null
  allowedActions?: ProcurementAllowedAction[]
  createdByName: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface PriceAgreementDetailRecord extends PriceAgreementSummaryRecord {
  sourceChain?: ProcurementSourceChainRecord[]
}

export interface PurchaseScheduleRecord {
  id: ResourceId
  orderId: ResourceId
  orderLineId: ResourceId
  orderNo: string
  lineNo: number
  materialId?: ResourceId | null
  materialCode?: string | null
  materialName?: string | null
  scheduleSeq: number
  expectedArrivalDate: string
  plannedQuantity: string
  receivedQuantity: string
  remainingQuantity: string
  status: PurchaseScheduleStatus
  statusName?: string | null
  closeReason?: string | null
  remark?: string | null
  allowedActions?: ProcurementAllowedAction[]
  version: number
}

export interface EffectivePurchaseSupplyRecord extends ProcurementOwnershipFields {
  id: ResourceId
  sourceType: 'ORDER_LINE' | 'SCHEDULE' | string
  sourceId: ResourceId
  orderId: ResourceId
  orderNo: string
  scheduleId?: ResourceId | null
  scheduleSeq?: number | null
  supplierId: ResourceId
  supplierName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  expectedArrivalDate?: string | null
  remainingQuantity: string
  countedAsEffectiveSupply: boolean
  notCountedReason?: string | null
  status: string
  statusName?: string | null
  priceSourceType?: string | null
  priceSourceTypeName?: string | null
  priceSourceNo?: string | null
  sourceNo?: string | null
  costVisible?: boolean | null
  taxExcludedAmount?: string | null
  allowedActions?: ProcurementAllowedAction[]
}

export interface ProcurementSourceChainRecord {
  sourceType: string
  sourceNo: string
  sourceId?: ResourceId | null
  summary?: string | null
}

export interface PurchaseOrderLinePayload {
  lineNo: number
  materialId: ResourceId
  unitId?: ResourceId
  quantity: ProcurementQuantityPayload
  unitPrice: ProcurementUnitPricePayload
  taxRate?: ProcurementTaxRatePayload
  taxIncludedUnitPrice?: ProcurementUnitPricePayload
  taxExcludedUnitPrice?: ProcurementUnitPricePayload
  requisitionLineId?: ResourceId
  quoteLineId?: ResourceId
  priceAgreementLineId?: ResourceId
  expectedArrivalDate?: string
  remark?: string
}

export interface PurchaseOrderPayload {
  supplierId: ResourceId
  procurementMode?: ProcurementMode
  projectId?: ResourceId | null
  orderDate: string
  expectedArrivalDate?: string
  publicDirectReason?: string
  priceSourceReason?: string
  remark?: string
  lines: PurchaseOrderLinePayload[]
}

export interface PurchaseOrderUpdatePayload extends PurchaseOrderPayload {
  version: number
}

export interface PurchaseReceiptLinePayload {
  lineNo: number
  orderLineId: ResourceId
  scheduleId?: ResourceId
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

export interface PurchaseReceiptUpdatePayload extends PurchaseReceiptPayload {
  version: number
}

export interface ProcurementRequisitionLinePayload {
  lineNo: number
  procurementMode: ProcurementMode
  projectId?: ResourceId | null
  materialId: ResourceId
  unitId?: ResourceId
  quantity: ProcurementQuantityPayload
  requiredDate: string
  purpose: string
  suggestedSupplierId?: ResourceId | null
  taxRate?: ProcurementTaxRatePayload
  remark?: string
}

export interface ProcurementRequisitionPayload {
  procurementMode: ProcurementMode
  projectId?: ResourceId | null
  title?: string
  requiredDate: string
  remark?: string
  lines: ProcurementRequisitionLinePayload[]
}

export interface ProcurementRequisitionUpdatePayload extends ProcurementRequisitionPayload {
  version: number
}

export interface ProcurementInquiryLinePayload {
  lineNo: number
  requisitionLineId?: ResourceId | null
  materialId: ResourceId
  unitId?: ResourceId
  quantity: ProcurementQuantityPayload
  requiredDate?: string
  remark?: string
}

export interface ProcurementInquiryPayload {
  procurementMode: ProcurementMode
  projectId?: ResourceId | null
  title?: string
  supplierIds: ResourceId[]
  remark?: string
  lines: ProcurementInquiryLinePayload[]
}

export interface ProcurementInquiryUpdatePayload extends ProcurementInquiryPayload {
  version: number
}

export interface SupplierQuotePayload {
  supplierId: ResourceId
  materialId: ResourceId
  quantity: ProcurementQuantityPayload
  taxRate: ProcurementTaxRatePayload
  taxIncludedUnitPrice: ProcurementUnitPricePayload
  taxExcludedUnitPrice: ProcurementUnitPricePayload
  taxIncludedAmount: ProcurementMoneyPayload
  taxExcludedAmount: ProcurementMoneyPayload
  currency: 'CNY'
  minPurchaseQuantity?: ProcurementQuantityPayload
  deliveryDate?: string
  validFrom?: string
  validTo?: string
  remark?: string
}

export interface SupplierQuoteUpdatePayload extends SupplierQuotePayload {
  version: number
}

export interface SupplierQuoteSelectPayload {
  version: number
  reason: string
  selectedQuantity?: ProcurementQuantityPayload
  idempotencyKey: string
}

export interface PriceAgreementPayload {
  procurementMode: ProcurementMode
  projectId?: ResourceId | null
  supplierId: ResourceId
  materialId: ResourceId
  taxRate: ProcurementTaxRatePayload
  taxIncludedUnitPrice: ProcurementUnitPricePayload
  taxExcludedUnitPrice: ProcurementUnitPricePayload
  currency: 'CNY'
  minPurchaseQuantity?: ProcurementQuantityPayload
  validFrom: string
  validTo: string
  remark?: string
}

export interface PriceAgreementUpdatePayload extends PriceAgreementPayload {
  version: number
}

export interface PurchaseSchedulePayload {
  scheduleSeq: number
  expectedArrivalDate: string
  plannedQuantity: ProcurementQuantityPayload
  remark?: string
}

export interface PurchaseScheduleUpdatePayload extends PurchaseSchedulePayload {
  version: number
}

export interface PurchaseScheduleReplaceLinePayload extends PurchaseSchedulePayload {
  orderLineId: ResourceId
}

export interface PurchaseScheduleReplacePayload {
  version: number
  idempotencyKey: string
  lines: PurchaseScheduleReplaceLinePayload[]
}

export interface ProcurementVersionPayload {
  version: number
  idempotencyKey: string
}

export interface ProcurementReasonPayload {
  version: number
  reason?: string
  closeReason?: string
  deviationAmount?: ProcurementMoneyPayload
  idempotencyKey: string
}

export interface ProcurementApprovalSubmitPayload {
  version: number
  reason: string
  idempotencyKey: string
}

export interface ProcurementApi {
  requisitions: {
    list(params: ProcurementRequisitionListParams): Promise<PageResult<ProcurementRequisitionSummaryRecord>>
    get(id: ResourceId): Promise<ProcurementRequisitionDetailRecord>
    create(payload: ProcurementRequisitionPayload): Promise<ProcurementRequisitionDetailRecord>
    update(id: ResourceId, payload: ProcurementRequisitionUpdatePayload): Promise<ProcurementRequisitionDetailRecord>
    submitApproval(id: ResourceId, payload: ProcurementApprovalSubmitPayload): Promise<ApprovalInstanceDetail>
    cancel(id: ResourceId, payload: ProcurementVersionPayload): Promise<ProcurementRequisitionDetailRecord>
    close(id: ResourceId, payload: ProcurementReasonPayload): Promise<ProcurementRequisitionDetailRecord>
  }
  inquiries: {
    list(params: ProcurementInquiryListParams): Promise<PageResult<ProcurementInquirySummaryRecord>>
    get(id: ResourceId): Promise<ProcurementInquiryDetailRecord>
    create(payload: ProcurementInquiryPayload): Promise<ProcurementInquiryDetailRecord>
    update(id: ResourceId, payload: ProcurementInquiryUpdatePayload): Promise<ProcurementInquiryDetailRecord>
    release(id: ResourceId, payload: ProcurementVersionPayload): Promise<ProcurementInquiryDetailRecord>
    complete(id: ResourceId, payload: ProcurementVersionPayload): Promise<ProcurementInquiryDetailRecord>
    cancel(id: ResourceId, payload: ProcurementVersionPayload): Promise<ProcurementInquiryDetailRecord>
  }
  quotes: {
    list(inquiryId: ResourceId, params: SupplierQuoteListParams): Promise<PageResult<SupplierQuoteRecord>>
    get(inquiryId: ResourceId, id: ResourceId): Promise<SupplierQuoteRecord>
    create(inquiryId: ResourceId, payload: SupplierQuotePayload): Promise<SupplierQuoteRecord>
    update(inquiryId: ResourceId, id: ResourceId, payload: SupplierQuoteUpdatePayload): Promise<SupplierQuoteRecord>
    select(inquiryId: ResourceId, id: ResourceId, payload: SupplierQuoteSelectPayload): Promise<SupplierQuoteRecord>
    cancel(inquiryId: ResourceId, id: ResourceId, payload: ProcurementVersionPayload): Promise<SupplierQuoteRecord>
  }
  priceAgreements: {
    list(params: PriceAgreementListParams): Promise<PageResult<PriceAgreementSummaryRecord>>
    get(id: ResourceId): Promise<PriceAgreementDetailRecord>
    create(payload: PriceAgreementPayload): Promise<PriceAgreementDetailRecord>
    update(id: ResourceId, payload: PriceAgreementUpdatePayload): Promise<PriceAgreementDetailRecord>
    submitActivation(id: ResourceId, payload: ProcurementApprovalSubmitPayload): Promise<ApprovalInstanceDetail>
    disable(id: ResourceId, payload: ProcurementReasonPayload): Promise<PriceAgreementDetailRecord>
    cancel(id: ResourceId, payload: ProcurementVersionPayload): Promise<PriceAgreementDetailRecord>
  }
  orders: {
    list(params: PurchaseOrderListParams): Promise<PageResult<PurchaseOrderSummaryRecord>>
    get(id: ResourceId): Promise<PurchaseOrderDetailRecord>
    create(payload: PurchaseOrderPayload): Promise<PurchaseOrderDetailRecord>
    update(id: ResourceId, payload: PurchaseOrderUpdatePayload): Promise<PurchaseOrderDetailRecord>
    confirm(id: ResourceId, payload: ProcurementVersionPayload): Promise<PurchaseOrderDetailRecord>
    submitException(id: ResourceId, payload: ProcurementReasonPayload): Promise<ApprovalInstanceDetail>
    cancel(id: ResourceId, payload: ProcurementVersionPayload): Promise<PurchaseOrderDetailRecord>
    close(id: ResourceId, payload: ProcurementReasonPayload): Promise<PurchaseOrderDetailRecord>
  }
  schedules: {
    list(orderId: ResourceId, params: PurchaseScheduleListParams): Promise<PageResult<PurchaseScheduleRecord>>
    replace(orderId: ResourceId, payload: PurchaseScheduleReplacePayload): Promise<PageResult<PurchaseScheduleRecord>>
    update(orderId: ResourceId, scheduleId: ResourceId, payload: PurchaseScheduleUpdatePayload): Promise<PurchaseScheduleRecord>
    close(orderId: ResourceId, scheduleId: ResourceId, payload: ProcurementReasonPayload): Promise<PurchaseScheduleRecord>
  }
  receipts: {
    list(params: PurchaseReceiptListParams): Promise<PageResult<PurchaseReceiptSummaryRecord>>
    get(id: ResourceId): Promise<PurchaseReceiptDetailRecord>
    create(orderId: ResourceId, payload: PurchaseReceiptPayload): Promise<PurchaseReceiptDetailRecord>
    update(id: ResourceId, payload: PurchaseReceiptUpdatePayload): Promise<PurchaseReceiptDetailRecord>
    post(id: ResourceId, payload: ProcurementVersionPayload): Promise<PurchaseReceiptDetailRecord>
  }
  effectiveSupplies: {
    list(params: EffectivePurchaseSupplyListParams): Promise<PageResult<EffectivePurchaseSupplyRecord>>
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
    'procurementMode',
    'projectId',
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
    'procurementMode',
    'projectId',
    'dateFrom',
    'dateTo',
    'orderId',
    'page',
    'pageSize',
  ] as const
  const requisitionQueryKeys = [
    'keyword',
    'procurementMode',
    'projectId',
    'status',
    'approvalStatus',
    'requiredDateFrom',
    'requiredDateTo',
    'page',
    'pageSize',
  ] as const
  const inquiryQueryKeys = [
    'keyword',
    'procurementMode',
    'projectId',
    'status',
    'page',
    'pageSize',
  ] as const
  const quoteQueryKeys = [
    'supplierId',
    'status',
    'page',
    'pageSize',
  ] as const
  const priceAgreementQueryKeys = [
    'keyword',
    'supplierId',
    'materialId',
    'procurementMode',
    'projectId',
    'status',
    'page',
    'pageSize',
  ] as const
  const scheduleQueryKeys = [
    'status',
    'expectedDateFrom',
    'expectedDateTo',
    'page',
    'pageSize',
  ] as const
  const effectiveSupplyQueryKeys = [
    'projectId',
    'materialId',
    'supplierId',
    'procurementMode',
    'status',
    'expectedDateFrom',
    'expectedDateTo',
    'countedOnly',
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
  const assertIdempotencyKey = (payload: { idempotencyKey?: string }) => {
    if (typeof payload.idempotencyKey !== 'string' || payload.idempotencyKey.trim() === '') {
      throw new Error('幂等键不能为空')
    }
  }
  const writeAction = async <T>(method: 'POST' | 'PUT', path: string, payload: { idempotencyKey?: string }) => {
    assertIdempotencyKey(payload)
    return write<T>(method, path, payload)
  }

  const orderPath = (id?: ResourceId) =>
    `/api/admin/procurement/orders${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const receiptPath = (id?: ResourceId) =>
    `/api/admin/procurement/receipts${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const requisitionPath = (id?: ResourceId) =>
    `/api/admin/procurement/requisitions${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const inquiryPath = (id?: ResourceId) =>
    `/api/admin/procurement/inquiries${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const inquiryQuotePath = (inquiryId: ResourceId, quoteId?: ResourceId) =>
    `${inquiryPath(inquiryId)}/quotes${quoteId === undefined ? '' : `/${encodeURIComponent(String(quoteId))}`}`
  const priceAgreementPath = (id?: ResourceId) =>
    `/api/admin/procurement/price-agreements${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const schedulePath = (orderId: ResourceId, scheduleId?: ResourceId) =>
    `${orderPath(orderId)}/schedules${scheduleId === undefined ? '' : `/${encodeURIComponent(String(scheduleId))}`}`

  return {
    requisitions: {
      list: (params) =>
        get<PageResult<ProcurementRequisitionSummaryRecord>>(
          '/api/admin/procurement/requisitions',
          pickQuery(params, requisitionQueryKeys),
        ),
      get: (id) => get<ProcurementRequisitionDetailRecord>(requisitionPath(id)),
      create: (payload) => write<ProcurementRequisitionDetailRecord>('POST', requisitionPath(), payload),
      update: (id, payload) => write<ProcurementRequisitionDetailRecord>('PUT', requisitionPath(id), payload),
      submitApproval: (id, payload) =>
        writeAction<ApprovalInstanceDetail>('POST', `${requisitionPath(id)}/submit-approval`, payload),
      cancel: (id, payload) =>
        writeAction<ProcurementRequisitionDetailRecord>('PUT', `${requisitionPath(id)}/cancel`, payload),
      close: (id, payload) =>
        writeAction<ProcurementRequisitionDetailRecord>('PUT', `${requisitionPath(id)}/close`, payload),
    },
    inquiries: {
      list: (params) =>
        get<PageResult<ProcurementInquirySummaryRecord>>(
          '/api/admin/procurement/inquiries',
          pickQuery(params, inquiryQueryKeys),
        ),
      get: (id) => get<ProcurementInquiryDetailRecord>(inquiryPath(id)),
      create: (payload) => write<ProcurementInquiryDetailRecord>('POST', inquiryPath(), payload),
      update: (id, payload) => write<ProcurementInquiryDetailRecord>('PUT', inquiryPath(id), payload),
      release: (id, payload) =>
        writeAction<ProcurementInquiryDetailRecord>('PUT', `${inquiryPath(id)}/release`, payload),
      complete: (id, payload) =>
        writeAction<ProcurementInquiryDetailRecord>('PUT', `${inquiryPath(id)}/complete`, payload),
      cancel: (id, payload) =>
        writeAction<ProcurementInquiryDetailRecord>('PUT', `${inquiryPath(id)}/cancel`, payload),
    },
    quotes: {
      list: (inquiryId, params) =>
        get<PageResult<SupplierQuoteRecord>>(inquiryQuotePath(inquiryId), pickQuery(params, quoteQueryKeys)),
      get: (inquiryId, id) => get<SupplierQuoteRecord>(inquiryQuotePath(inquiryId, id)),
      create: (inquiryId, payload) => write<SupplierQuoteRecord>('POST', inquiryQuotePath(inquiryId), payload),
      update: (inquiryId, id, payload) => write<SupplierQuoteRecord>('PUT', inquiryQuotePath(inquiryId, id), payload),
      select: (inquiryId, id, payload) =>
        writeAction<SupplierQuoteRecord>('PUT', `${inquiryQuotePath(inquiryId, id)}/select`, payload),
      cancel: (inquiryId, id, payload) =>
        writeAction<SupplierQuoteRecord>('PUT', `${inquiryQuotePath(inquiryId, id)}/cancel`, payload),
    },
    priceAgreements: {
      list: (params) =>
        get<PageResult<PriceAgreementSummaryRecord>>(
          '/api/admin/procurement/price-agreements',
          pickQuery(params, priceAgreementQueryKeys),
        ),
      get: (id) => get<PriceAgreementDetailRecord>(priceAgreementPath(id)),
      create: (payload) => write<PriceAgreementDetailRecord>('POST', priceAgreementPath(), payload),
      update: (id, payload) => write<PriceAgreementDetailRecord>('PUT', priceAgreementPath(id), payload),
      submitActivation: (id, payload) =>
        writeAction<ApprovalInstanceDetail>('POST', `${priceAgreementPath(id)}/submit-activation`, payload),
      disable: (id, payload) =>
        writeAction<PriceAgreementDetailRecord>('PUT', `${priceAgreementPath(id)}/disable`, payload),
      cancel: (id, payload) =>
        writeAction<PriceAgreementDetailRecord>('PUT', `${priceAgreementPath(id)}/cancel`, payload),
    },
    orders: {
      list: (params) =>
        get<PageResult<PurchaseOrderSummaryRecord>>(
          '/api/admin/procurement/orders',
          pickQuery(params, orderQueryKeys),
        ),
      get: (id) => get<PurchaseOrderDetailRecord>(orderPath(id)),
      create: (payload) => write<PurchaseOrderDetailRecord>('POST', orderPath(), payload),
      update: (id, payload) => write<PurchaseOrderDetailRecord>('PUT', orderPath(id), payload),
      confirm: (id, payload) =>
        writeAction<PurchaseOrderDetailRecord>('PUT', `${orderPath(id)}/confirm`, payload),
      submitException: (id, payload) =>
        writeAction<ApprovalInstanceDetail>('POST', `${orderPath(id)}/submit-exception`, payload),
      cancel: (id, payload) =>
        writeAction<PurchaseOrderDetailRecord>('PUT', `${orderPath(id)}/cancel`, payload),
      close: (id, payload) =>
        writeAction<PurchaseOrderDetailRecord>('PUT', `${orderPath(id)}/close`, payload),
    },
    schedules: {
      list: (orderId, params) =>
        get<PageResult<PurchaseScheduleRecord>>(schedulePath(orderId), pickQuery(params, scheduleQueryKeys)),
      replace: (orderId, payload) =>
        writeAction<PageResult<PurchaseScheduleRecord>>('PUT', schedulePath(orderId), payload),
      update: (orderId, scheduleId, payload) =>
        write<PurchaseScheduleRecord>('PUT', schedulePath(orderId, scheduleId), payload),
      close: (orderId, scheduleId, payload) =>
        writeAction<PurchaseScheduleRecord>('PUT', `${schedulePath(orderId, scheduleId)}/close`, payload),
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
      post: (id, payload) => writeAction<PurchaseReceiptDetailRecord>('PUT', `${receiptPath(id)}/post`, payload),
    },
    effectiveSupplies: {
      list: (params) =>
        get<PageResult<EffectivePurchaseSupplyRecord>>(
          '/api/admin/procurement/effective-supplies',
          pickQuery(params, effectiveSupplyQueryKeys),
        ),
    },
  }
}

export const procurementApi = createProcurementApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
