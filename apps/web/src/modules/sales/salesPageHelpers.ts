import type {
  ResourceId,
  SalesOrderLineRecord,
  SalesOrderSummaryRecord,
  SalesPriceSourceType,
  SalesOrderStatus,
  SalesShipmentLineRecord,
  SalesShipmentStatus,
} from '../../shared/api/salesApi'
import type { InventoryQualityStatus, InventoryTrackingAllocationPayload, InventoryTrackingMethod } from '../../shared/api/inventoryApi'
import { createUnknownStatusDisplay, type StatusTone } from '../../shared/status/statusDisplay'

export interface SalesDecimalValidationResult {
  value: number | null
  payloadValue: string | null
  message: string | null
}

export interface SalesOrderLineDraft {
  lineNo: number
  materialId: ResourceId | ''
  unitId: ResourceId | ''
  unitName: string
  reservationWarehouseId: ResourceId | ''
  reservationWarehouseName: string
  quantity: string
  unitPrice: string
  priceSourceType: SalesPriceSourceType
  priceSourceNo: string
  quoteLineId?: ResourceId | null
  contractLineId?: ResourceId | null
  untaxedUnitPrice: string
  taxIncludedUnitPrice: string
  taxRate: string
  expectedShipDate: string
  remark: string
}

export interface SalesShipmentSourceLine {
  id: ResourceId
  lineNo: number
  deliveryPlanId?: ResourceId | null
  deliveryPlanNo?: string | null
  deliveryPlanDate?: string | null
  materialId: ResourceId
  materialCode: string
  materialName: string
  trackingMethod?: InventoryTrackingMethod | null
  trackingMethodName?: string | null
  unitId: ResourceId
  unitName: string
  orderedQuantity: number
  shippedQuantityBefore: number
  remainingQuantityBefore: number
  reservationWarehouseId?: ResourceId | null
  reservationWarehouseName?: string | null
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
}

export interface SalesShipmentLineDraft {
  lineNo: number
  orderLineId: ResourceId | ''
  deliveryPlanId: ResourceId | ''
  deliveryPlanNo: string
  deliveryPlanDate: string
  materialId: ResourceId | ''
  materialCode: string
  materialName: string
  trackingMethod?: InventoryTrackingMethod | null
  trackingMethodName?: string | null
  unitId: ResourceId | ''
  unitName: string
  orderedQuantity: number
  shippedQuantityBefore: number
  remainingQuantityBefore: number
  reservationWarehouseId?: ResourceId | null
  reservationWarehouseName?: string | null
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
  trackingAllocations: InventoryTrackingAllocationPayload[]
  quantity: string
  remark: string
}

const salesOrderStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  PARTIALLY_SHIPPED: '部分出库',
  SHIPPED: '全部出库',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const salesOrderStatusTypes: Record<string, StatusTone> = {
  DRAFT: 'info',
  CONFIRMED: 'success',
  PARTIALLY_SHIPPED: 'warning',
  SHIPPED: 'success',
  CLOSED: 'info',
  CANCELLED: 'info',
}

const salesShipmentStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
}

const salesShipmentStatusTypes: Record<string, StatusTone> = {
  DRAFT: 'info',
  POSTED: 'success',
}

const salesPriceSourceLabels: Record<string, string> = {
  MANUAL: '手工录入',
  QUOTE: '报价带入',
  LEGACY_MANUAL: '历史手工价',
}

function normalizedDisplayText(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value).trim()
}

function knownOrFallbackLabel(
  code: unknown,
  labels: Record<string, string>,
  unknownLabel: string,
  context: { domain: string; field: string },
): string {
  const codeText = normalizedDisplayText(code).toUpperCase()
  if (!codeText) {
    return '-'
  }
  const knownLabel = labels[codeText]
  if (knownLabel) {
    return knownLabel
  }
  if (unknownLabel === '未知状态') {
    return createUnknownStatusDisplay({
      ...context,
      code: codeText,
    }).label
  }
  return unknownLabel
}

export function salesOrderStatusLabel(status?: SalesOrderStatus | string | null): string {
  return knownOrFallbackLabel(status, salesOrderStatusLabels, '未知状态', {
    domain: '销售',
    field: '销售订单状态',
  })
}

export function salesOrderStatusTagType(status?: SalesOrderStatus | string | null): StatusTone {
  return salesOrderStatusTypes[normalizedDisplayText(status).toUpperCase()] ?? 'warning'
}

export function salesOrderTaxIncludedAmount(record: Pick<SalesOrderSummaryRecord, 'totalTaxIncludedAmount' | 'taxIncludedAmount'>): string | null | undefined {
  return record.taxIncludedAmount ?? record.totalTaxIncludedAmount
}

export function salesShipmentStatusLabel(status?: SalesShipmentStatus | string | null): string {
  return knownOrFallbackLabel(status, salesShipmentStatusLabels, '未知状态', {
    domain: '销售',
    field: '销售出库状态',
  })
}

export function salesShipmentStatusTagType(status?: SalesShipmentStatus | string | null): StatusTone {
  return salesShipmentStatusTypes[normalizedDisplayText(status).toUpperCase()] ?? 'warning'
}

export function salesMovementTypeLabel(value: string): string {
  const labels: Record<string, string> = {
    OPENING: '期初入库',
    ADJUSTMENT_INCREASE: '调整入库',
    ADJUSTMENT_DECREASE: '调整出库',
    PRODUCTION_ISSUE: '生产领料',
    PRODUCTION_RECEIPT: '完工入库',
    PURCHASE_RECEIPT: '采购入库',
    SALES_SHIPMENT: '销售出库',
    WAREHOUSE_TRANSFER_OUT: '调拨出库',
    WAREHOUSE_TRANSFER_IN: '调拨入库',
    OWNERSHIP_CONVERSION_OUT: '所有权转出',
    OWNERSHIP_CONVERSION_IN: '所有权转入',
    STOCKTAKE_GAIN: '盘盈入库',
    STOCKTAKE_LOSS: '盘亏出库',
    VALUATION_ADJUSTMENT: '估值调整',
  }
  return knownOrFallbackLabel(value, labels, '未知类型', {
    domain: '销售',
    field: '库存流水类型',
  })
}

export function salesMovementDirectionLabel(value: string): string {
  return knownOrFallbackLabel(value, {
    IN: '入库',
    OUT: '出库',
  }, '未知方向', {
    domain: '销售',
    field: '库存流水方向',
  })
}

export function salesPriceSourceLabel(source: {
  priceSourceType?: SalesPriceSourceType | string | null
  priceSourceNo?: string | null
  sourceQuoteNo?: string | null
}): string {
  const label = knownOrFallbackLabel(source.priceSourceType, salesPriceSourceLabels, '未知价格来源', {
    domain: '销售',
    field: '销售价格来源',
  })
  const sourceNo = normalizedDisplayText(source.priceSourceNo ?? source.sourceQuoteNo)
  return sourceNo ? `${label} ${sourceNo}` : label
}

export function salesErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

export function formatSalesQuantity(value: unknown): string {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) {
    return '-'
  }
  return numberValue.toFixed(6).replace(/\.?0+$/, '')
}

export function formatSalesAmount(value: unknown): string {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) {
    return '-'
  }
  return numberValue.toFixed(2)
}

export function formatSalesDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function validateSalesDecimal(
  value: unknown,
  options: { label: string; allowZero?: boolean },
): SalesDecimalValidationResult {
  if (value === null || value === undefined || value === '') {
    return { value: null, payloadValue: null, message: `${options.label}不能为空` }
  }

  const normalizedValue = String(value).trim()
  if (!normalizedValue) {
    return { value: null, payloadValue: null, message: `${options.label}不能为空` }
  }
  if (normalizedValue.startsWith('-') || /[eE]/.test(normalizedValue)) {
    return { value: null, payloadValue: null, message: `${options.label}仅支持普通十进制非负数` }
  }
  if (!/^\d+(?:\.\d+)?$/.test(normalizedValue)) {
    return { value: null, payloadValue: null, message: `${options.label}仅支持普通十进制非负数` }
  }

  const [integerPart, decimalPart = ''] = normalizedValue.split('.')
  if (integerPart.length > 12) {
    return { value: null, payloadValue: null, message: `${options.label}整数部分最多 12 位` }
  }
  if (decimalPart.length > 6) {
    return { value: null, payloadValue: null, message: `${options.label}最多 6 位小数` }
  }

  const numberValue = Number(normalizedValue)
  const validZero = options.allowZero && numberValue === 0
  if (!Number.isFinite(numberValue) || (!validZero && numberValue <= 0)) {
    return {
      value: null,
      payloadValue: null,
      message: options.allowZero ? `${options.label}不能小于 0` : `${options.label}必须大于 0`,
    }
  }

  return { value: numberValue, payloadValue: normalizedValue, message: null }
}

export function validateSalesQuantity(value: unknown): SalesDecimalValidationResult {
  return validateSalesDecimal(value, { label: '数量' })
}

export function validateSalesUnitPrice(value: unknown): SalesDecimalValidationResult {
  return validateSalesDecimal(value, { label: '单价', allowZero: true })
}

export function nextSalesOrderLineNo(lines: Array<{ lineNo: number }>): number {
  const maxLineNo = lines.reduce((max, line) => Math.max(max, Number(line.lineNo) || 0), 0)
  return maxLineNo + 10
}

export function newSalesOrderLine(lineNo = 10): SalesOrderLineDraft {
  return {
    lineNo,
    materialId: '',
    unitId: '',
    unitName: '',
    reservationWarehouseId: '',
    reservationWarehouseName: '',
    quantity: '',
    unitPrice: '',
    priceSourceType: 'MANUAL',
    priceSourceNo: '',
    quoteLineId: undefined,
    contractLineId: undefined,
    untaxedUnitPrice: '',
    taxIncludedUnitPrice: '',
    taxRate: '',
    expectedShipDate: '',
    remark: '',
  }
}

export function nextSalesShipmentLineNo(lines: Array<{ lineNo: number }>): number {
  const maxLineNo = lines.reduce((max, line) => Math.max(max, Number(line.lineNo) || 0), 0)
  return maxLineNo + 10
}

export function newSalesShipmentLine(lineNo = 10): SalesShipmentLineDraft {
  return {
    lineNo,
    orderLineId: '',
    deliveryPlanId: '',
    deliveryPlanNo: '',
    deliveryPlanDate: '',
    materialId: '',
    materialCode: '',
    materialName: '',
    trackingMethod: 'NONE',
    trackingMethodName: '不追踪',
    unitId: '',
    unitName: '',
    orderedQuantity: 0,
    shippedQuantityBefore: 0,
    remainingQuantityBefore: 0,
    reservationWarehouseId: null,
    reservationWarehouseName: null,
    qualityStatus: null,
    qualityStatusName: null,
    quantityOnHand: null,
    reservedQuantity: null,
    occupiedQuantity: null,
    availableQuantity: null,
    availableToPromiseQuantity: null,
    selectable: null,
    disabledReasonCode: null,
    disabledReason: null,
    maxSelectableQuantity: null,
    trackingAllocations: [],
    quantity: '',
    remark: '',
  }
}

export function salesOrderLineDraftFromRecord(line: SalesOrderLineRecord): SalesOrderLineDraft {
  return {
    lineNo: line.lineNo,
    materialId: line.materialId,
    unitId: line.unitId,
    unitName: line.unitName,
    reservationWarehouseId: line.reservationWarehouseId ?? '',
    reservationWarehouseName: line.reservationWarehouseName ?? '',
    quantity: String(line.quantity),
    unitPrice: String(line.unitPrice),
    priceSourceType: line.priceSourceType ?? 'MANUAL',
    priceSourceNo: line.priceSourceNo ?? '',
    quoteLineId: line.quoteLineId ?? (line.priceSourceType && line.priceSourceType !== 'MANUAL' ? null : undefined),
    contractLineId: line.contractLineId ?? (line.priceSourceType && line.priceSourceType !== 'MANUAL' ? null : undefined),
    untaxedUnitPrice: line.untaxedUnitPrice ?? '',
    taxIncludedUnitPrice: line.taxIncludedUnitPrice ?? '',
    taxRate: line.taxRate ?? '',
    expectedShipDate: line.expectedShipDate ?? '',
    remark: line.remark ?? '',
  }
}

export function salesShipmentSourceFromOrderLine(line: SalesOrderLineRecord): SalesShipmentSourceLine {
  return {
    id: line.id,
    lineNo: line.lineNo,
    deliveryPlanId: undefined,
    deliveryPlanNo: undefined,
    deliveryPlanDate: undefined,
    materialId: line.materialId,
    materialCode: line.materialCode,
    materialName: line.materialName,
    trackingMethod: undefined,
    trackingMethodName: undefined,
    unitId: line.unitId,
    unitName: line.unitName,
    orderedQuantity: Number(line.quantity) || 0,
    shippedQuantityBefore: Number(line.shippedQuantity) || 0,
    remainingQuantityBefore: Number(line.remainingQuantity) || 0,
    reservationWarehouseId: line.reservationWarehouseId ?? null,
    reservationWarehouseName: line.reservationWarehouseName ?? null,
    qualityStatus: line.qualityStatus ?? null,
    qualityStatusName: line.qualityStatusName ?? null,
    quantityOnHand: line.quantityOnHand ?? null,
    reservedQuantity: line.reservedQuantity ?? null,
    occupiedQuantity: line.occupiedQuantity ?? null,
    availableQuantity: line.availableQuantity ?? null,
    availableToPromiseQuantity: line.availableToPromiseQuantity ?? null,
    selectable: line.selectable ?? null,
    disabledReasonCode: line.disabledReasonCode ?? null,
    disabledReason: line.disabledReason ?? null,
    maxSelectableQuantity: line.maxSelectableQuantity ?? null,
  }
}

export function salesShipmentSourceFromShipmentLine(line: SalesShipmentLineRecord): SalesShipmentSourceLine {
  return {
    id: line.orderLineId,
    lineNo: line.lineNo,
    deliveryPlanId: line.deliveryPlanId ?? undefined,
    deliveryPlanNo: undefined,
    deliveryPlanDate: undefined,
    materialId: line.materialId,
    materialCode: line.materialCode,
    materialName: line.materialName,
    trackingMethod: line.trackingMethod ?? 'NONE',
    trackingMethodName: line.trackingMethodName ?? '不追踪',
    unitId: line.unitId,
    unitName: line.unitName,
    orderedQuantity: Number(line.orderedQuantity) || 0,
    shippedQuantityBefore: Number(line.shippedQuantityBefore) || 0,
    remainingQuantityBefore: Number(line.remainingQuantityBefore) || 0,
    reservationWarehouseId: line.reservationWarehouseId ?? null,
    reservationWarehouseName: line.reservationWarehouseName ?? null,
    qualityStatus: line.qualityStatus ?? null,
    qualityStatusName: line.qualityStatusName ?? null,
    quantityOnHand: line.quantityOnHand ?? null,
    reservedQuantity: line.reservedQuantity ?? null,
    occupiedQuantity: line.occupiedQuantity ?? null,
    availableQuantity: line.availableQuantity ?? null,
    availableToPromiseQuantity: line.availableToPromiseQuantity ?? null,
    selectable: line.selectable ?? null,
    disabledReasonCode: line.disabledReasonCode ?? null,
    disabledReason: line.disabledReason ?? null,
    maxSelectableQuantity: line.maxSelectableQuantity ?? null,
  }
}

export function normalizeOptionalId(value: ResourceId | ''): ResourceId | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined
  }
  const trimmedValue = String(value).trim()
  if (!trimmedValue) {
    return undefined
  }
  const numericValue = Number(trimmedValue)
  return Number.isFinite(numericValue) ? numericValue : trimmedValue
}

export function normalizeRequiredId(value: ResourceId | ''): ResourceId | null {
  return normalizeOptionalId(value) ?? null
}
