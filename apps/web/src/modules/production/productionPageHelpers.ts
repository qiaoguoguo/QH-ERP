import type { ProductionDocumentStatus, ProductionWorkOrderStatus } from '../../shared/api/productionApi'

export interface ProductionQuantityValidationResult {
  value: number | null
  payloadValue: string | null
  message: string | null
}

const workOrderStatusLabels: Record<ProductionWorkOrderStatus, string> = {
  DRAFT: '草稿',
  RELEASED: '已发布',
  IN_PROGRESS: '生产中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const workOrderStatusTypes: Record<ProductionWorkOrderStatus, 'info' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'info',
  RELEASED: 'success',
  IN_PROGRESS: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'danger',
}

const productionDocumentStatusLabels: Record<ProductionDocumentStatus, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
}

const productionDocumentStatusTypes: Record<ProductionDocumentStatus, 'info' | 'success'> = {
  DRAFT: 'info',
  POSTED: 'success',
}

export function workOrderStatusLabel(status: ProductionWorkOrderStatus): string {
  return workOrderStatusLabels[status]
}

export function workOrderStatusType(status: ProductionWorkOrderStatus): 'info' | 'success' | 'warning' | 'danger' {
  return workOrderStatusTypes[status]
}

export function productionDocumentStatusLabel(status: ProductionDocumentStatus): string {
  return productionDocumentStatusLabels[status]
}

export function productionDocumentStatusType(status: ProductionDocumentStatus): 'info' | 'success' {
  return productionDocumentStatusTypes[status]
}

export function formatProductionQuantity(value: unknown): string {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) {
    return '-'
  }
  return numberValue.toFixed(6).replace(/\.?0+$/, '')
}

export function productionErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

export function validateProductionQuantity(value: unknown, options: { allowZero?: boolean } = {}): ProductionQuantityValidationResult {
  if (value === null || value === undefined || value === '') {
    return { value: null, payloadValue: null, message: '数量不能为空' }
  }

  const normalizedValue = String(value).trim()
  if (!normalizedValue) {
    return { value: null, payloadValue: null, message: '数量不能为空' }
  }
  if (normalizedValue.startsWith('-') || /[eE]/.test(normalizedValue)) {
    return { value: null, payloadValue: null, message: '数量仅支持普通十进制非负数' }
  }
  if (!/^\d+(?:\.\d+)?$/.test(normalizedValue)) {
    return { value: null, payloadValue: null, message: '数量仅支持普通十进制非负数' }
  }

  const [integerPart, decimalPart = ''] = normalizedValue.split('.')
  if (integerPart.length > 12) {
    return { value: null, payloadValue: null, message: '数量整数部分最多 12 位' }
  }
  if (decimalPart.length > 6) {
    return { value: null, payloadValue: null, message: '数量最多 6 位小数' }
  }

  const numberValue = Number(normalizedValue)
  const validZero = options.allowZero && numberValue === 0
  if (!Number.isFinite(numberValue) || (!validZero && numberValue <= 0)) {
    return { value: null, payloadValue: null, message: options.allowZero ? '数量不能小于 0' : '数量必须大于 0' }
  }

  return { value: numberValue, payloadValue: normalizedValue, message: null }
}

export function formatProductionDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function todayText(): string {
  const today = new Date()
  const year = today.getFullYear()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
