import type {
  ResourceId,
  SalesOrderLineRecord,
  SalesOrderStatus,
  SalesShipmentStatus,
} from '../../shared/api/salesApi'

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
  quantity: string
  unitPrice: string
  expectedShipDate: string
  remark: string
}

const salesOrderStatusLabels: Record<SalesOrderStatus, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  PARTIALLY_SHIPPED: '部分出库',
  SHIPPED: '全部出库',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const salesOrderStatusTypes: Record<SalesOrderStatus, 'info' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'info',
  CONFIRMED: 'success',
  PARTIALLY_SHIPPED: 'warning',
  SHIPPED: 'success',
  CLOSED: 'info',
  CANCELLED: 'danger',
}

const salesShipmentStatusLabels: Record<SalesShipmentStatus, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
}

const salesShipmentStatusTypes: Record<SalesShipmentStatus, 'info' | 'success'> = {
  DRAFT: 'info',
  POSTED: 'success',
}

export function salesOrderStatusLabel(status: SalesOrderStatus): string {
  return salesOrderStatusLabels[status]
}

export function salesOrderStatusTagType(status: SalesOrderStatus): 'info' | 'success' | 'warning' | 'danger' {
  return salesOrderStatusTypes[status]
}

export function salesShipmentStatusLabel(status: SalesShipmentStatus): string {
  return salesShipmentStatusLabels[status]
}

export function salesShipmentStatusTagType(status: SalesShipmentStatus): 'info' | 'success' {
  return salesShipmentStatusTypes[status]
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
    quantity: '',
    unitPrice: '',
    expectedShipDate: '',
    remark: '',
  }
}

export function salesOrderLineDraftFromRecord(line: SalesOrderLineRecord): SalesOrderLineDraft {
  return {
    lineNo: line.lineNo,
    materialId: line.materialId,
    unitId: line.unitId,
    unitName: line.unitName,
    quantity: String(line.quantity),
    unitPrice: String(line.unitPrice),
    expectedShipDate: line.expectedShipDate ?? '',
    remark: line.remark ?? '',
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
