import type { ResourceId, PurchaseOrderStatus, PurchaseReceiptStatus } from '../../shared/api/procurementApi'

export interface ProcurementDecimalValidationResult {
  value: number | null
  payloadValue: string | null
  message: string | null
}

export interface PurchaseOrderLineDraft {
  lineNo: number
  materialId: ResourceId | ''
  unitId: ResourceId | ''
  unitName: string
  quantity: string
  unitPrice: string
  expectedArrivalDate: string
  remark: string
}

const purchaseOrderStatusLabels: Record<PurchaseOrderStatus, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  PARTIALLY_RECEIVED: '部分入库',
  RECEIVED: '全部入库',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const purchaseOrderStatusTypes: Record<PurchaseOrderStatus, 'info' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'info',
  CONFIRMED: 'success',
  PARTIALLY_RECEIVED: 'warning',
  RECEIVED: 'success',
  CLOSED: 'info',
  CANCELLED: 'danger',
}

const purchaseReceiptStatusLabels: Record<PurchaseReceiptStatus, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
}

const purchaseReceiptStatusTypes: Record<PurchaseReceiptStatus, 'info' | 'success'> = {
  DRAFT: 'info',
  POSTED: 'success',
}

export function purchaseOrderStatusLabel(status: PurchaseOrderStatus): string {
  return purchaseOrderStatusLabels[status]
}

export function purchaseOrderStatusTagType(status: PurchaseOrderStatus): 'info' | 'success' | 'warning' | 'danger' {
  return purchaseOrderStatusTypes[status]
}

export function purchaseReceiptStatusLabel(status: PurchaseReceiptStatus): string {
  return purchaseReceiptStatusLabels[status]
}

export function purchaseReceiptStatusTagType(status: PurchaseReceiptStatus): 'info' | 'success' {
  return purchaseReceiptStatusTypes[status]
}

export function procurementErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

export function formatProcurementQuantity(value: unknown): string {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) {
    return '-'
  }
  return numberValue.toFixed(6).replace(/\.?0+$/, '')
}

export function formatProcurementAmount(value: unknown): string {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) {
    return '-'
  }
  return numberValue.toFixed(2)
}

export function formatProcurementDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function validateProcurementDecimal(
  value: unknown,
  options: { label: string; allowZero?: boolean },
): ProcurementDecimalValidationResult {
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

export function validatePurchaseQuantity(value: unknown): ProcurementDecimalValidationResult {
  return validateProcurementDecimal(value, { label: '数量' })
}

export function validatePurchaseUnitPrice(value: unknown): ProcurementDecimalValidationResult {
  return validateProcurementDecimal(value, { label: '单价', allowZero: true })
}

export function nextPurchaseOrderLineNo(lines: Array<{ lineNo: number }>): number {
  const maxLineNo = lines.reduce((max, line) => Math.max(max, Number(line.lineNo) || 0), 0)
  return maxLineNo + 10
}

export function newPurchaseOrderLine(lineNo = 10): PurchaseOrderLineDraft {
  return {
    lineNo,
    materialId: '',
    unitId: '',
    unitName: '',
    quantity: '',
    unitPrice: '',
    expectedArrivalDate: '',
    remark: '',
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
