import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type InventoryDocumentStatus = 'DRAFT' | 'POSTED'
export type InventoryDocumentType = 'OPENING' | 'ADJUSTMENT'
export type InventoryMovementType =
  | 'OPENING'
  | 'ADJUSTMENT_INCREASE'
  | 'ADJUSTMENT_DECREASE'
  | 'PRODUCTION_ISSUE'
  | 'PRODUCTION_RECEIPT'
  | 'PURCHASE_RECEIPT'
  | 'SALES_SHIPMENT'
  | 'WAREHOUSE_TRANSFER_OUT'
  | 'WAREHOUSE_TRANSFER_IN'
  | 'OWNERSHIP_CONVERSION_OUT'
  | 'OWNERSHIP_CONVERSION_IN'
  | 'STOCKTAKE_GAIN'
  | 'STOCKTAKE_LOSS'
  | 'VALUATION_ADJUSTMENT'
export type InventoryDirection = 'IN' | 'OUT'
export type InventoryAdjustmentDirection = 'INCREASE' | 'DECREASE'
export type InventoryQuantityPayload = string
export type InventoryQualityStatus = 'PENDING_INSPECTION' | 'QUALIFIED' | 'REJECTED' | 'FROZEN'
export type InventoryReservationType = 'RESERVATION' | 'OCCUPATION'
export type InventoryReservationStatus = 'ACTIVE' | 'RELEASED' | 'CONSUMED' | 'CANCELLED'
export type InventoryTrackingMethod = 'NONE' | 'BATCH' | 'SERIAL'
export type InventoryStockStatus = 'IN_STOCK' | 'RESERVED' | 'OCCUPIED' | 'OUTBOUND' | 'CANCELLED'
export type InventoryOwnershipType = 'PUBLIC' | 'PROJECT'
export type InventoryValuationState =
  | 'VALUED'
  | 'PROJECT_ACTUAL_LAYER'
  | 'LEGACY_UNVALUED'
  | 'NON_VALUED'
  | 'CURRENT_AVERAGE_PROVISIONAL'
  | 'MANUAL_PROVISIONAL'
  | 'ABNORMAL'
export type InventoryValuationMethod =
  | 'MOVING_AVERAGE'
  | 'PROJECT_ACTUAL_LAYER'
  | 'LEGACY_UNVALUED'
  | 'NON_VALUED'
  | 'CURRENT_AVERAGE_PROVISIONAL'
  | 'MANUAL_PROVISIONAL'
export type InventoryControlledDocumentStatus = 'DRAFT' | 'COUNTING' | 'RECONCILED' | 'POSTED' | 'CANCELLED'
export type InventoryAllowedAction =
  | 'UPDATE'
  | 'POST'
  | 'CANCEL'
  | 'SUBMIT_APPROVAL'
  | 'WITHDRAW'
  | 'START'
  | 'UPDATE_LINES'
  | 'RECONCILE'
  | 'COMPLETE_ZERO_VARIANCE'
export type InventoryValuationAdjustmentType = 'LEGACY_OPENING' | 'PROVISIONAL_REVALUATION'

export interface InventoryBalanceListParams {
  keyword?: string
  warehouseId?: ResourceId
  materialId?: ResourceId
  materialType?: string
  ownershipType?: InventoryOwnershipType
  projectId?: ResourceId
  valuationState?: InventoryValuationState
  includeZero?: boolean
  qualityStatus?: InventoryQualityStatus
  trackingMethod?: InventoryTrackingMethod
  batchId?: ResourceId
  batchNo?: string
  serialId?: ResourceId
  serialNo?: string
  includeZeroQualityStatuses?: boolean
  onlyPositive?: boolean
  page: number
  pageSize: number
}

export interface InventoryMovementListParams {
  keyword?: string
  warehouseId?: ResourceId
  materialId?: ResourceId
  ownershipType?: InventoryOwnershipType
  projectId?: ResourceId
  valuationMethod?: InventoryValuationMethod
  costLayerId?: ResourceId
  movementType?: InventoryMovementType
  direction?: InventoryDirection
  qualityStatus?: InventoryQualityStatus
  trackingMethod?: InventoryTrackingMethod
  batchId?: ResourceId
  batchNo?: string
  serialId?: ResourceId
  serialNo?: string
  sourceType?: string
  sourceId?: ResourceId
  sourceLineId?: ResourceId
  dateFrom?: string
  dateTo?: string
  page: number
  pageSize: number
}

export interface InventoryCostLayerListParams {
  keyword?: string
  ownershipType?: InventoryOwnershipType
  projectId?: ResourceId
  warehouseId?: ResourceId
  materialId?: ResourceId
  sourceType?: string
  sourceId?: ResourceId
  batchNo?: string
  serialNo?: string
  status?: string
  costLayerId?: ResourceId
  page: number
  pageSize: number
}

export interface InventoryControlledDocumentListParams {
  keyword?: string
  status?: InventoryControlledDocumentStatus
  dateFrom?: string
  dateTo?: string
  page: number
  pageSize: number
}

export interface InventoryDocumentListParams {
  keyword?: string
  documentType?: InventoryDocumentType
  status?: InventoryDocumentStatus
  dateFrom?: string
  dateTo?: string
  page: number
  pageSize: number
}

export interface InventoryReservationListParams {
  keyword?: string
  warehouseId?: ResourceId
  materialId?: ResourceId
  reservationType?: InventoryReservationType
  status?: InventoryReservationStatus
  sourceType?: string
  sourceId?: ResourceId
  sourceLineId?: ResourceId
  businessDateFrom?: string
  businessDateTo?: string
  page: number
  pageSize: number
}

export interface InventoryBatchListParams {
  keyword?: string
  materialId?: ResourceId
  warehouseId?: ResourceId
  qualityStatus?: InventoryQualityStatus
  batchNo?: string
  sourceType?: string
  sourceId?: ResourceId
  onlyAvailable?: boolean
  page: number
  pageSize: number
}

export interface InventorySerialListParams {
  keyword?: string
  materialId?: ResourceId
  warehouseId?: ResourceId
  qualityStatus?: InventoryQualityStatus
  serialNo?: string
  batchId?: ResourceId
  sourceType?: string
  sourceId?: ResourceId
  onlyAvailable?: boolean
  page: number
  pageSize: number
}

export interface InventoryTrackingQualityStatusSummary {
  qualityStatus: InventoryQualityStatus
  qualityStatusName: string
  quantityOnHand: number | string
  availableQuantity?: number | string
}

export interface InventoryBalanceRecord {
  id: ResourceId
  warehouseId: ResourceId
  warehouseCode: string
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  materialSpec?: string | null
  materialType: string
  trackingMethod?: InventoryTrackingMethod
  trackingMethodName?: string | null
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  traceableQuantity?: number | string | null
  unitId: ResourceId
  unitName: string
  qualityStatus?: InventoryQualityStatus
  qualityStatusName?: string
  ownershipType?: InventoryOwnershipType
  ownershipTypeName?: string | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  costVisible?: boolean
  valuationState?: InventoryValuationState | string | null
  valuationStateName?: string | null
  inventoryAmount?: string | null
  averageUnitCost?: string | null
  costLayerCount?: number | string | null
  costLayerId?: ResourceId | null
  abnormalReason?: string | null
  bookQuantity?: number | string
  quantityOnHand: number | string
  lockedQuantity: number | string
  availableQuantity: number | string
  totalQuantityOnHand?: number | string
  pendingInspectionQuantity?: number | string
  qualifiedQuantity?: number | string
  rejectedQuantity?: number | string
  frozenQuantity?: number | string
  reservedQuantity?: number | string
  occupiedQuantity?: number | string
  inTransitQuantity?: number | string
  availableToPromiseQuantity?: number | string
  netRequirementShortageQuantity?: number | string
  unavailableReason?: string | null
  updatedAt: string
}

export interface InventoryReservationSummaryRecord {
  id: ResourceId
  reservationNo: string
  reservationType: InventoryReservationType
  reservationTypeName: string
  status: InventoryReservationStatus
  statusName: string
  warehouseId: ResourceId
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  qualityStatus: InventoryQualityStatus
  qualityStatusName: string
  quantity: number | string
  remainingQuantity: number | string
  releasedQuantity: number | string
  consumedQuantity: number | string
  sourceType: string
  sourceTypeName: string
  sourceId: ResourceId
  sourceLineId: ResourceId
  sourceDocumentNo: string
  businessDate: string
  reason?: string | null
  remark?: string | null
  createdByName: string
  createdAt: string
  releasedByName?: string | null
  releasedAt?: string | null
}

export interface InventoryReservationAuditRecord {
  action: string
  actionName: string
  operatorName: string
  operatedAt: string
  businessDate?: string | null
  reason?: string | null
  remark?: string | null
}

export interface InventoryReservationDetailRecord extends InventoryReservationSummaryRecord {
  sourceSummary?: Record<string, unknown> | null
  auditRecords?: InventoryReservationAuditRecord[]
}

export interface InventoryBatchSummaryRecord {
  id: ResourceId
  batchNo: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  sourceType?: string | null
  sourceId?: ResourceId | null
  sourceLineId?: ResourceId | null
  sourceDocumentNo?: string | null
  warehouseId?: ResourceId | null
  warehouseName?: string | null
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  stockStatus?: InventoryStockStatus | null
  stockStatusName?: string | null
  businessDate?: string | null
  quantityOnHand: number | string
  availableQuantity: number | string
  selectable?: boolean | null
  disabledReasonCode?: string | null
  disabledReason?: string | null
  qualityStatusSummary?: InventoryTrackingQualityStatusSummary[]
  updatedAt: string
}

export interface InventoryBatchDetailRecord extends InventoryBatchSummaryRecord {
  remark?: string | null
  createdByName?: string | null
  createdAt?: string | null
}

export interface InventorySerialSummaryRecord {
  id: ResourceId
  serialNo: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  batchId?: ResourceId | null
  batchNo?: string | null
  warehouseId?: ResourceId | null
  warehouseName?: string | null
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  stockStatus?: InventoryStockStatus | null
  stockStatusName?: string | null
  availableQuantity?: number | string | null
  selectable?: boolean | null
  disabledReasonCode?: string | null
  disabledReason?: string | null
  sourceType?: string | null
  sourceId?: ResourceId | null
  sourceLineId?: ResourceId | null
  sourceDocumentNo?: string | null
  updatedAt: string
}

export interface InventorySerialDetailRecord extends InventorySerialSummaryRecord {
  remark?: string | null
  createdByName?: string | null
  createdAt?: string | null
}

export interface InventoryTrackingAllocationPayload {
  allocationId?: ResourceId
  trackingMethod?: InventoryTrackingMethod
  trackingMethodName?: string
  batchId?: ResourceId
  batchNo?: string
  serialId?: ResourceId
  serialNo?: string
  quantity: InventoryQuantityPayload
  qualityStatus?: InventoryQualityStatus
  qualityStatusName?: string
  movementId?: ResourceId
  documentType?: string
  documentId?: ResourceId
  documentLineId?: ResourceId
  sourceType?: string
  sourceId?: ResourceId
  sourceLineId?: ResourceId
  sourceDocumentNo?: string
  sourceLineNo?: number
  sourceAllocationId?: ResourceId
}

export interface InventoryTraceSubjectRecord {
  trackingMethod: InventoryTrackingMethod
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  materialId?: ResourceId | null
  materialCode?: string | null
  materialName?: string | null
  sourceDocumentNo?: string | null
}

export interface InventoryTraceBalanceRecord {
  warehouseId?: ResourceId | null
  warehouseName: string
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  quantityOnHand: number | string
  availableQuantity?: number | string | null
  reservedQuantity?: number | string | null
  occupiedQuantity?: number | string | null
}

export interface InventoryTraceNodeRecord {
  nodeType?: string | null
  nodeTypeName?: string | null
  documentType?: string | null
  documentId?: ResourceId | null
  documentNo?: string | null
  lineId?: ResourceId | null
  businessDate?: string | null
  direction?: InventoryDirection | string | null
  quantity?: number | string | null
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  warehouseName?: string | null
  operatorName?: string | null
  routeName?: string | null
  permissionRestricted?: boolean
}

export interface InventoryTraceDetailRecord {
  subject: InventoryTraceSubjectRecord
  currentBalances: InventoryTraceBalanceRecord[]
  activeReservations: InventoryTraceNodeRecord[]
  sourceRecords: InventoryTraceNodeRecord[]
  qualityEvents: InventoryTraceNodeRecord[]
  outboundRecords: InventoryTraceNodeRecord[]
  returnRecords: InventoryTraceNodeRecord[]
  movements: InventoryTraceNodeRecord[]
  restrictedSources: InventoryTraceNodeRecord[]
}

export interface InventoryMovementRecord {
  id: ResourceId
  movementNo: string
  movementType: InventoryMovementType
  direction: InventoryDirection
  warehouseId: ResourceId
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  qualityStatus?: InventoryQualityStatus
  qualityStatusName?: string
  ownershipType?: InventoryOwnershipType
  ownershipTypeName?: string | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  costVisible?: boolean
  valuationMethod?: InventoryValuationMethod | string | null
  valuationMethodName?: string | null
  valuationState?: InventoryValuationState | string | null
  valuationStateName?: string | null
  unitCost?: string | null
  movementAmount?: string | null
  valueFlowId?: ResourceId | null
  originalValueFlowId?: ResourceId | null
  costLayerId?: ResourceId | null
  trackingMethod?: InventoryTrackingMethod
  trackingMethodName?: string | null
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  quantity: number | string
  beforeQuantity: number | string
  afterQuantity: number | string
  sourceType: string
  sourceId: ResourceId
  sourceLineId?: ResourceId | null
  sourceDocumentNo?: string | null
  targetDocumentNo?: string | null
  relatedMovementId?: ResourceId | null
  businessDate: string
  reason?: string | null
  remark?: string | null
  operatorName: string
  occurredAt: string
}

export interface InventoryApprovalSummary {
  id: ResourceId
  status: string
  submittedAt?: string | null
}

export interface InventoryCostLayerRecord {
  id: ResourceId
  layerNo: string
  ownershipType: InventoryOwnershipType
  ownershipTypeName?: string | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  warehouseId?: ResourceId | null
  warehouseName?: string | null
  materialId: ResourceId
  materialCode: string
  materialName: string
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  originalQuantity: string
  originalAmount?: string | null
  remainingQuantity: string
  remainingAmount?: string | null
  unitCost?: string | null
  status: string
  statusName?: string | null
  sourceType?: string | null
  sourceTypeName?: string | null
  sourceId?: ResourceId | null
  sourceDocumentNo?: string | null
  parentLayerId?: ResourceId | null
  parentLayerNo?: string | null
  createdAt?: string | null
}

export interface InventoryControlledDocumentActionPayload {
  version: number
  idempotencyKey: string
  reason?: string
}

export interface InventoryControlledDocumentSummaryRecord {
  id: ResourceId
  documentNo: string
  status: InventoryControlledDocumentStatus | string
  statusName?: string | null
  businessDate: string
  reason: string
  lineCount?: number
  version: number
  allowedActions?: InventoryAllowedAction[] | string[]
  approvalSummary?: InventoryApprovalSummary | null
  amountImpactSummary?: string | null
  keyInfoSummary?: string | null
  costVisible?: boolean | null
  createdByName?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  postedByName?: string | null
  postedAt?: string | null
}

export interface InventoryWarehouseTransferLineRecord {
  id?: ResourceId
  lineNo: number
  sourceWarehouseId?: ResourceId
  sourceWarehouseName?: string
  targetWarehouseId?: ResourceId
  targetWarehouseName?: string
  materialId?: ResourceId
  materialCode?: string
  materialName?: string
  unitId?: ResourceId
  unitName?: string | null
  ownershipType?: InventoryOwnershipType
  ownershipTypeName?: string
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  quantity: string
  sourceCostLayerId?: ResourceId | null
  costLayerNo?: string | null
}

export interface InventoryWarehouseTransferRecord extends InventoryControlledDocumentSummaryRecord {
  lines?: InventoryWarehouseTransferLineRecord[]
}

export interface InventoryOwnershipConversionLineRecord {
  id?: ResourceId
  lineNo: number
  sourceOwnershipType?: InventoryOwnershipType
  targetOwnershipType?: InventoryOwnershipType
  sourceProjectId?: ResourceId | null
  sourceProjectNo?: string | null
  sourceProjectName?: string | null
  targetProjectId?: ResourceId | null
  targetProjectNo?: string | null
  targetProjectName?: string | null
  sourceWarehouseId?: ResourceId
  sourceWarehouseName?: string
  targetWarehouseId?: ResourceId
  targetWarehouseName?: string
  materialId?: ResourceId
  materialCode?: string
  materialName?: string
  unitId?: ResourceId
  unitName?: string | null
  sourceCostLayerId?: ResourceId | null
  costLayerId?: ResourceId | null
  costLayerNo?: string | null
  sourceUnitCost?: string | null
  qualityStatus?: InventoryQualityStatus | null
  qualityStatusName?: string | null
  batchId?: ResourceId | null
  batchNo?: string | null
  serialId?: ResourceId | null
  serialNo?: string | null
  quantity: string
}

export interface InventoryOwnershipConversionRecord extends InventoryControlledDocumentSummaryRecord {
  lines?: InventoryOwnershipConversionLineRecord[]
}

export interface InventoryStocktakeLineRecord {
  id: ResourceId
  lineNo: number
  version: number
  warehouseName?: string
  materialCode?: string
  materialName?: string
  ownershipType?: InventoryOwnershipType
  ownershipTypeName?: string | null
  projectNo?: string | null
  projectName?: string | null
  bookQuantity?: string | null
  countedQuantity?: string | null
  varianceQuantity?: string | null
  differenceAmount?: string | null
}

export interface InventoryStocktakeRecord extends InventoryControlledDocumentSummaryRecord {
  scopeType?: 'WAREHOUSE' | 'MATERIAL' | string
  warehouseId?: ResourceId | null
  warehouseName?: string | null
  lines?: InventoryStocktakeLineRecord[]
}

export interface InventoryValuationAdjustmentLineRecord {
  id?: ResourceId
  lineNo: number
  materialId?: ResourceId
  materialCode?: string
  materialName?: string
  ownershipType?: InventoryOwnershipType | null
  ownershipTypeName?: string | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  quantity?: string | null
  unitCost?: string | null
  adjustmentAmount?: string | null
  costLayerId?: ResourceId | null
  costLayerNo?: string | null
}

export interface InventoryValuationAdjustmentRecord extends InventoryControlledDocumentSummaryRecord {
  adjustmentType: InventoryValuationAdjustmentType | string
  adjustmentTypeName?: string | null
  lines?: InventoryValuationAdjustmentLineRecord[]
}

export interface InventoryWarehouseTransferLinePayload {
  lineNo: number
  sourceWarehouseId: ResourceId
  targetWarehouseId: ResourceId
  materialId: ResourceId
  unitId: ResourceId
  quantity: InventoryQuantityPayload
  ownershipType: InventoryOwnershipType
  projectId?: ResourceId
  qualityStatus?: InventoryQualityStatus
  batchId?: ResourceId
  serialId?: ResourceId
  sourceCostLayerId?: ResourceId
  remark?: string
}

export interface InventoryWarehouseTransferPayload {
  idempotencyKey: string
  version?: number
  businessDate: string
  reason: string
  remark?: string
  lines: InventoryWarehouseTransferLinePayload[]
}

export interface InventoryOwnershipConversionLinePayload {
  lineNo: number
  sourceOwnershipType: InventoryOwnershipType
  targetOwnershipType: InventoryOwnershipType
  sourceProjectId?: ResourceId
  targetProjectId?: ResourceId
  sourceWarehouseId: ResourceId
  targetWarehouseId: ResourceId
  materialId: ResourceId
  unitId: ResourceId
  quantity: InventoryQuantityPayload
  sourceCostLayerId?: ResourceId
  qualityStatus?: InventoryQualityStatus
  batchId?: ResourceId
  serialId?: ResourceId
  remark?: string
}

export interface InventoryOwnershipConversionPayload {
  idempotencyKey: string
  version?: number
  businessDate: string
  reason: string
  remark?: string
  lines: InventoryOwnershipConversionLinePayload[]
}

export interface InventoryStocktakePayload {
  idempotencyKey: string
  version?: number
  businessDate: string
  scopeType: 'WAREHOUSE' | 'MATERIAL' | string
  warehouseId?: ResourceId
  materialId?: ResourceId
  reason: string
  remark?: string
}

export interface InventoryStocktakeLinePayload {
  id: ResourceId
  version: number
  countedQuantity: InventoryQuantityPayload | null
}

export interface InventoryStocktakeLineUpdatePayload {
  version: number
  lines: InventoryStocktakeLinePayload[]
}

export interface InventoryValuationAdjustmentLinePayload {
  lineNo: number
  materialId: ResourceId
  ownershipType?: InventoryOwnershipType
  projectId?: ResourceId
  quantity?: InventoryQuantityPayload
  unitCost?: string
  adjustmentAmount: string
  costLayerId?: ResourceId
  remark?: string
}

export interface InventoryValuationAdjustmentPayload {
  adjustmentType: InventoryValuationAdjustmentType
  idempotencyKey: string
  version?: number
  businessDate: string
  reason: string
  remark?: string
  lines: InventoryValuationAdjustmentLinePayload[]
}

export interface InventoryDocumentSummaryRecord {
  id: ResourceId
  documentNo: string
  documentType: InventoryDocumentType
  status: InventoryDocumentStatus
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

export interface InventoryDocumentLineRecord {
  id: ResourceId
  lineNo: number
  warehouseId: ResourceId
  warehouseName: string
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitId: ResourceId
  unitName: string
  quantity: number
  adjustmentDirection?: InventoryAdjustmentDirection | null
  beforeQuantity?: number | null
  afterQuantity?: number | null
  remark?: string | null
}

export interface InventoryDocumentDetailRecord extends InventoryDocumentSummaryRecord {
  lines: InventoryDocumentLineRecord[]
}

export interface InventoryDocumentLinePayload {
  lineNo: number
  warehouseId: ResourceId
  materialId: ResourceId
  unitId?: ResourceId
  quantity: InventoryQuantityPayload
  adjustmentDirection?: InventoryAdjustmentDirection
  trackingAllocations?: InventoryTrackingAllocationPayload[]
  remark?: string
}

export interface InventoryDocumentPayload {
  documentType: InventoryDocumentType
  businessDate: string
  reason: string
  remark?: string
  lines: InventoryDocumentLinePayload[]
}

export interface InventoryApi {
  balances: {
    list(params: InventoryBalanceListParams): Promise<PageResult<InventoryBalanceRecord>>
  }
  batches: {
    list(params: InventoryBatchListParams): Promise<PageResult<InventoryBatchSummaryRecord>>
    get(id: ResourceId): Promise<InventoryBatchDetailRecord>
  }
  serials: {
    list(params: InventorySerialListParams): Promise<PageResult<InventorySerialSummaryRecord>>
    get(id: ResourceId): Promise<InventorySerialDetailRecord>
  }
  reservations: {
    list(params: InventoryReservationListParams): Promise<PageResult<InventoryReservationSummaryRecord>>
    get(id: ResourceId): Promise<InventoryReservationDetailRecord>
  }
  traces: {
    getBatchTrace(id: ResourceId): Promise<InventoryTraceDetailRecord>
    getSerialTrace(id: ResourceId): Promise<InventoryTraceDetailRecord>
  }
  movements: {
    list(params: InventoryMovementListParams): Promise<PageResult<InventoryMovementRecord>>
  }
  costLayers: {
    list(params: InventoryCostLayerListParams): Promise<PageResult<InventoryCostLayerRecord>>
    get(id: ResourceId): Promise<InventoryCostLayerRecord>
  }
  documents: {
    list(params: InventoryDocumentListParams): Promise<PageResult<InventoryDocumentSummaryRecord>>
    get(id: ResourceId): Promise<InventoryDocumentDetailRecord>
    create(payload: InventoryDocumentPayload): Promise<InventoryDocumentDetailRecord>
    update(id: ResourceId, payload: InventoryDocumentPayload): Promise<InventoryDocumentDetailRecord>
    post(id: ResourceId): Promise<InventoryDocumentDetailRecord>
  }
  warehouseTransfers: {
    list(params: InventoryControlledDocumentListParams): Promise<PageResult<InventoryWarehouseTransferRecord>>
    get(id: ResourceId): Promise<InventoryWarehouseTransferRecord>
    create(payload: InventoryWarehouseTransferPayload): Promise<InventoryWarehouseTransferRecord>
    update(id: ResourceId, payload: InventoryWarehouseTransferPayload): Promise<InventoryWarehouseTransferRecord>
    post(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryWarehouseTransferRecord>
    cancel(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryWarehouseTransferRecord>
  }
  ownershipConversions: {
    list(params: InventoryControlledDocumentListParams): Promise<PageResult<InventoryOwnershipConversionRecord>>
    get(id: ResourceId): Promise<InventoryOwnershipConversionRecord>
    create(payload: InventoryOwnershipConversionPayload): Promise<InventoryOwnershipConversionRecord>
    update(id: ResourceId, payload: InventoryOwnershipConversionPayload): Promise<InventoryOwnershipConversionRecord>
    submitApproval(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryOwnershipConversionRecord>
    withdraw(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryOwnershipConversionRecord>
    cancel(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryOwnershipConversionRecord>
  }
  stocktakes: {
    list(params: InventoryControlledDocumentListParams): Promise<PageResult<InventoryStocktakeRecord>>
    get(id: ResourceId): Promise<InventoryStocktakeRecord>
    create(payload: InventoryStocktakePayload): Promise<InventoryStocktakeRecord>
    start(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryStocktakeRecord>
    updateLines(id: ResourceId, payload: InventoryStocktakeLineUpdatePayload): Promise<InventoryStocktakeRecord>
    reconcile(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryStocktakeRecord>
    submitApproval(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryStocktakeRecord>
    completeZeroVariance(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryStocktakeRecord>
    cancel(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryStocktakeRecord>
  }
  valuationAdjustments: {
    list(params: InventoryControlledDocumentListParams): Promise<PageResult<InventoryValuationAdjustmentRecord>>
    get(id: ResourceId): Promise<InventoryValuationAdjustmentRecord>
    create(payload: InventoryValuationAdjustmentPayload): Promise<InventoryValuationAdjustmentRecord>
    update(id: ResourceId, payload: InventoryValuationAdjustmentPayload): Promise<InventoryValuationAdjustmentRecord>
    submitApproval(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryValuationAdjustmentRecord>
    withdraw(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryValuationAdjustmentRecord>
    cancel(id: ResourceId, payload: InventoryControlledDocumentActionPayload): Promise<InventoryValuationAdjustmentRecord>
  }
}

export interface InventoryApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createInventoryApi(options: InventoryApiOptions = {}): InventoryApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const balanceQueryKeys = [
    'keyword',
    'warehouseId',
    'materialId',
    'materialType',
    'ownershipType',
    'projectId',
    'valuationState',
    'includeZero',
    'qualityStatus',
    'trackingMethod',
    'batchId',
    'batchNo',
    'serialId',
    'serialNo',
    'includeZeroQualityStatuses',
    'onlyPositive',
    'page',
    'pageSize',
  ] as const
  const movementQueryKeys = [
    'keyword',
    'warehouseId',
    'materialId',
    'ownershipType',
    'projectId',
    'valuationMethod',
    'costLayerId',
    'movementType',
    'direction',
    'qualityStatus',
    'trackingMethod',
    'batchId',
    'batchNo',
    'serialId',
    'serialNo',
    'sourceType',
    'sourceId',
    'sourceLineId',
    'dateFrom',
    'dateTo',
    'page',
    'pageSize',
  ] as const
  const documentQueryKeys = ['keyword', 'documentType', 'status', 'dateFrom', 'dateTo', 'page', 'pageSize'] as const
  const controlledDocumentQueryKeys = ['keyword', 'status', 'dateFrom', 'dateTo', 'page', 'pageSize'] as const
  const costLayerQueryKeys = [
    'keyword',
    'ownershipType',
    'projectId',
    'warehouseId',
    'materialId',
    'sourceType',
    'sourceId',
    'batchNo',
    'serialNo',
    'status',
    'costLayerId',
    'page',
    'pageSize',
  ] as const
  const reservationQueryKeys = [
    'keyword',
    'warehouseId',
    'materialId',
    'reservationType',
    'status',
    'sourceType',
    'sourceId',
    'sourceLineId',
    'businessDateFrom',
    'businessDateTo',
    'page',
    'pageSize',
  ] as const
  const batchQueryKeys = [
    'keyword',
    'materialId',
    'warehouseId',
    'qualityStatus',
    'batchNo',
    'sourceType',
    'sourceId',
    'onlyAvailable',
    'page',
    'pageSize',
  ] as const
  const serialQueryKeys = [
    'keyword',
    'materialId',
    'warehouseId',
    'qualityStatus',
    'serialNo',
    'batchId',
    'sourceType',
    'sourceId',
    'onlyAvailable',
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

  const resourcePath = (basePath: string, id?: ResourceId) =>
    `${basePath}${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const warehouseTransferPath = (id?: ResourceId) => resourcePath('/api/admin/inventory/warehouse-transfers', id)
  const ownershipConversionPath = (id?: ResourceId) => resourcePath('/api/admin/inventory/ownership-conversions', id)
  const stocktakePath = (id?: ResourceId) => resourcePath('/api/admin/inventory/stocktakes', id)
  const valuationAdjustmentPath = (id?: ResourceId) => resourcePath('/api/admin/inventory/valuation-adjustments', id)

  return {
    balances: {
      list: (params) =>
        get<PageResult<InventoryBalanceRecord>>(
          '/api/admin/inventory/balances',
          pickQuery(params, balanceQueryKeys),
        ),
    },
    batches: {
      list: (params) =>
        get<PageResult<InventoryBatchSummaryRecord>>(
          '/api/admin/inventory/batches',
          pickQuery(params, batchQueryKeys),
        ),
      get: (id) => get<InventoryBatchDetailRecord>(`/api/admin/inventory/batches/${encodeURIComponent(String(id))}`),
    },
    serials: {
      list: (params) =>
        get<PageResult<InventorySerialSummaryRecord>>(
          '/api/admin/inventory/serials',
          pickQuery(params, serialQueryKeys),
        ),
      get: (id) => get<InventorySerialDetailRecord>(`/api/admin/inventory/serials/${encodeURIComponent(String(id))}`),
    },
    reservations: {
      list: (params) =>
        get<PageResult<InventoryReservationSummaryRecord>>(
          '/api/admin/inventory/reservations',
          pickQuery(params, reservationQueryKeys),
        ),
      get: (id) =>
        get<InventoryReservationDetailRecord>(
          `/api/admin/inventory/reservations/${encodeURIComponent(String(id))}`,
        ),
    },
    traces: {
      getBatchTrace: (id) =>
        get<InventoryTraceDetailRecord>(`/api/admin/inventory/traces/batches/${encodeURIComponent(String(id))}`),
      getSerialTrace: (id) =>
        get<InventoryTraceDetailRecord>(`/api/admin/inventory/traces/serials/${encodeURIComponent(String(id))}`),
    },
    movements: {
      list: (params) =>
        get<PageResult<InventoryMovementRecord>>(
          '/api/admin/inventory/movements',
          pickQuery(params, movementQueryKeys),
        ),
    },
    costLayers: {
      list: (params) =>
        get<PageResult<InventoryCostLayerRecord>>(
          '/api/admin/inventory/cost-layers',
          pickQuery(params, costLayerQueryKeys),
        ),
      get: (id) => get<InventoryCostLayerRecord>(`/api/admin/inventory/cost-layers/${encodeURIComponent(String(id))}`),
    },
    documents: {
      list: (params) =>
        get<PageResult<InventoryDocumentSummaryRecord>>(
          '/api/admin/inventory/documents',
          pickQuery(params, documentQueryKeys),
        ),
      get: (id) => get<InventoryDocumentDetailRecord>(`/api/admin/inventory/documents/${encodeURIComponent(String(id))}`),
      create: (payload) => write<InventoryDocumentDetailRecord>('POST', '/api/admin/inventory/documents', payload),
      update: (id, payload) =>
        write<InventoryDocumentDetailRecord>(
          'PUT',
          `/api/admin/inventory/documents/${encodeURIComponent(String(id))}`,
          payload,
        ),
      post: (id) =>
        write<InventoryDocumentDetailRecord>('PUT', `/api/admin/inventory/documents/${encodeURIComponent(String(id))}/post`),
    },
    warehouseTransfers: {
      list: (params) =>
        get<PageResult<InventoryWarehouseTransferRecord>>(
          warehouseTransferPath(),
          pickQuery(params, controlledDocumentQueryKeys),
        ),
      get: (id) => get<InventoryWarehouseTransferRecord>(warehouseTransferPath(id)),
      create: (payload) => write<InventoryWarehouseTransferRecord>('POST', warehouseTransferPath(), payload),
      update: (id, payload) => write<InventoryWarehouseTransferRecord>('PUT', warehouseTransferPath(id), payload),
      post: (id, payload) => write<InventoryWarehouseTransferRecord>('PUT', `${warehouseTransferPath(id)}/post`, payload),
      cancel: (id, payload) =>
        write<InventoryWarehouseTransferRecord>('PUT', `${warehouseTransferPath(id)}/cancel`, payload),
    },
    ownershipConversions: {
      list: (params) =>
        get<PageResult<InventoryOwnershipConversionRecord>>(
          ownershipConversionPath(),
          pickQuery(params, controlledDocumentQueryKeys),
        ),
      get: (id) => get<InventoryOwnershipConversionRecord>(ownershipConversionPath(id)),
      create: (payload) => write<InventoryOwnershipConversionRecord>('POST', ownershipConversionPath(), payload),
      update: (id, payload) => write<InventoryOwnershipConversionRecord>('PUT', ownershipConversionPath(id), payload),
      submitApproval: (id, payload) =>
        write<InventoryOwnershipConversionRecord>('PUT', `${ownershipConversionPath(id)}/submit-approval`, payload),
      withdraw: (id, payload) =>
        write<InventoryOwnershipConversionRecord>('PUT', `${ownershipConversionPath(id)}/withdraw`, payload),
      cancel: (id, payload) =>
        write<InventoryOwnershipConversionRecord>('PUT', `${ownershipConversionPath(id)}/cancel`, payload),
    },
    stocktakes: {
      list: (params) =>
        get<PageResult<InventoryStocktakeRecord>>(
          stocktakePath(),
          pickQuery(params, controlledDocumentQueryKeys),
        ),
      get: (id) => get<InventoryStocktakeRecord>(stocktakePath(id)),
      create: (payload) => write<InventoryStocktakeRecord>('POST', stocktakePath(), payload),
      start: (id, payload) => write<InventoryStocktakeRecord>('PUT', `${stocktakePath(id)}/start`, payload),
      updateLines: (id, payload) => write<InventoryStocktakeRecord>('PUT', `${stocktakePath(id)}/lines`, payload),
      reconcile: (id, payload) => write<InventoryStocktakeRecord>('PUT', `${stocktakePath(id)}/reconcile`, payload),
      submitApproval: (id, payload) =>
        write<InventoryStocktakeRecord>('PUT', `${stocktakePath(id)}/submit-approval`, payload),
      completeZeroVariance: (id, payload) =>
        write<InventoryStocktakeRecord>('PUT', `${stocktakePath(id)}/complete-zero-variance`, payload),
      cancel: (id, payload) => write<InventoryStocktakeRecord>('PUT', `${stocktakePath(id)}/cancel`, payload),
    },
    valuationAdjustments: {
      list: (params) =>
        get<PageResult<InventoryValuationAdjustmentRecord>>(
          valuationAdjustmentPath(),
          pickQuery(params, controlledDocumentQueryKeys),
        ),
      get: (id) => get<InventoryValuationAdjustmentRecord>(valuationAdjustmentPath(id)),
      create: (payload) => write<InventoryValuationAdjustmentRecord>('POST', valuationAdjustmentPath(), payload),
      update: (id, payload) => write<InventoryValuationAdjustmentRecord>('PUT', valuationAdjustmentPath(id), payload),
      submitApproval: (id, payload) =>
        write<InventoryValuationAdjustmentRecord>('PUT', `${valuationAdjustmentPath(id)}/submit-approval`, payload),
      withdraw: (id, payload) =>
        write<InventoryValuationAdjustmentRecord>('PUT', `${valuationAdjustmentPath(id)}/withdraw`, payload),
      cancel: (id, payload) =>
        write<InventoryValuationAdjustmentRecord>('PUT', `${valuationAdjustmentPath(id)}/cancel`, payload),
    },
  }
}

export const inventoryApi = createInventoryApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
