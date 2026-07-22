import type {
  CodingDatePattern,
  CodingObjectType,
  CodingResetCycle,
  CostCategory,
  InventoryValuationCategory,
  InvoiceType,
  MasterDataStatus,
  MaterialSourceType,
  MaterialTrackingMethod,
  MaterialType,
  RoundingMode,
  SettlementMethod,
} from '../../../shared/api/masterDataApi'

const masterStatusLabels: Record<MasterDataStatus, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
}

const materialTypeLabels: Record<MaterialType, string> = {
  RAW_MATERIAL: '原材料',
  SEMI_FINISHED: '半成品',
  FINISHED_GOOD: '成品',
  AUXILIARY: '辅料',
}

const sourceTypeLabels: Record<MaterialSourceType, string> = {
  PURCHASED: '外购',
  SELF_MADE: '自制',
  OUTSOURCED: '外协',
}

const trackingMethodLabels: Record<MaterialTrackingMethod, string> = {
  NONE: '不追踪',
  BATCH: '批次',
  SERIAL: '序列号',
}

const costCategoryLabels: Record<CostCategory, string> = {
  DIRECT_MATERIAL: '直接材料',
  AUXILIARY_MATERIAL: '辅助材料',
  SEMI_FINISHED: '半成品',
  FINISHED_GOOD: '产成品',
  OUTSOURCING: '委外',
  SERVICE: '服务',
  UNCLASSIFIED: '未分类',
}

const inventoryValuationCategoryLabels: Record<InventoryValuationCategory, string> = {
  VALUATED_MATERIAL: '计价物料',
  NON_VALUATED_CONSUMABLE: '非计价消耗品',
  SERVICE_NON_STOCK: '服务非库存',
  UNCLASSIFIED: '未分类',
}

const roundingModeLabels: Record<RoundingMode, string> = {
  HALF_UP: '四舍五入',
  UP: '向上取整',
  DOWN: '向下取整',
}

const codingObjectTypeLabels: Record<CodingObjectType, string> = {
  MATERIAL: '物料',
  CUSTOMER: '客户',
  SUPPLIER: '供应商',
  BOM: 'BOM',
  BOM_ECO: 'BOM 工程变更',
}

const codingDatePatternLabels: Record<CodingDatePattern, string> = {
  NONE: '无日期段',
  YYYY: 'YYYY',
  YYYYMM: 'YYYYMM',
  YYYYMMDD: 'YYYYMMDD',
}

const codingResetCycleLabels: Record<CodingResetCycle, string> = {
  NEVER: '永不重置',
  YEAR: '按年',
  MONTH: '按月',
  DAY: '按日',
}

const invoiceTypeLabels: Record<InvoiceType, string> = {
  GENERAL_VAT: '增值税普通发票',
  SPECIAL_VAT: '增值税专用发票',
  NONE: '不开票',
}

const settlementMethodLabels: Record<SettlementMethod, string> = {
  MONTHLY: '月结',
  CASH_ON_DELIVERY: '货到付款',
  ADVANCE: '预付',
  CUSTOM: '自定义',
}

function labelFromMap(value: string | null | undefined, labels: Record<string, string>, fallback: string): string {
  return value ? labels[value] ?? fallback : fallback
}

export function masterStatusLabel(status?: MasterDataStatus | string | null): string {
  return labelFromMap(status, masterStatusLabels, '未知状态')
}

export function materialTypeLabel(materialType?: MaterialType | string | null): string {
  return labelFromMap(materialType, materialTypeLabels, '未知类型')
}

export function sourceTypeLabel(sourceType?: MaterialSourceType | string | null): string {
  return labelFromMap(sourceType, sourceTypeLabels, '未知来源')
}

export function trackingMethodLabel(trackingMethod?: MaterialTrackingMethod | string | null): string {
  if (!trackingMethod) {
    return '不追踪'
  }
  return labelFromMap(trackingMethod, trackingMethodLabels, '未知追踪方式')
}

export function costCategoryLabel(value?: CostCategory | string | null): string {
  return value ? labelFromMap(value, costCategoryLabels, '未知成本类别') : '未分类'
}

export function inventoryValuationCategoryLabel(value?: InventoryValuationCategory | string | null): string {
  return value ? labelFromMap(value, inventoryValuationCategoryLabels, '未知计价类别') : '未分类'
}

export function roundingModeLabel(value?: RoundingMode | string | null): string {
  return value ? labelFromMap(value, roundingModeLabels, '未知舍入方式') : '-'
}

export function codingObjectTypeLabel(value?: CodingObjectType | string | null): string {
  return value ? labelFromMap(value, codingObjectTypeLabels, '未知对象') : '-'
}

export function codingDatePatternLabel(value?: CodingDatePattern | string | null): string {
  return value ? labelFromMap(value, codingDatePatternLabels, '未知日期格式') : '-'
}

export function codingResetCycleLabel(value?: CodingResetCycle | string | null): string {
  return value ? labelFromMap(value, codingResetCycleLabels, '未知重置周期') : '-'
}

export function invoiceTypeLabel(value?: InvoiceType | string | null): string {
  return value ? labelFromMap(value, invoiceTypeLabels, '未知发票类型') : '-'
}

export function settlementMethodLabel(value?: SettlementMethod | string | null): string {
  return value ? labelFromMap(value, settlementMethodLabels, '未知结算方式') : '-'
}

export function percentLabel(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? `${(numericValue * 100).toFixed(0)}%` : String(value)
}
