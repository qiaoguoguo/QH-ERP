export interface ProductionQuantityValidationResult {
  value: number | null
  payloadValue: string | null
  message: string | null
}

const workOrderStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  RELEASED: '已发布',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const workOrderStatusTypes: Record<string, 'info' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'info',
  RELEASED: 'success',
  IN_PROGRESS: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'danger',
}

const productionDocumentStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
  CANCELLED: '已取消',
}

const productionDocumentStatusTypes: Record<string, 'info' | 'success' | 'danger'> = {
  DRAFT: 'info',
  POSTED: 'success',
  CANCELLED: 'danger',
}

const productionMovementTypeLabels: Record<string, string> = {
  PRODUCTION_ISSUE: '生产领料',
  PRODUCTION_REPORT: '生产报工',
  PRODUCTION_RECEIPT: '完工入库',
  PRODUCTION_MATERIAL_RETURN: '生产退料',
  PRODUCTION_MATERIAL_SUPPLEMENT: '生产补料',
  OUTSOURCING_ISSUE: '外协发料',
  OUTSOURCING_RECEIPT: '外协收货',
}

export function workOrderStatusLabel(status: string): string {
  return workOrderStatusLabels[status] ?? status
}

export function workOrderStatusType(status: string): 'info' | 'success' | 'warning' | 'danger' {
  return workOrderStatusTypes[status] ?? 'info'
}

export function productionDocumentStatusLabel(status: string): string {
  return productionDocumentStatusLabels[status] ?? status
}

export function productionDocumentStatusType(status: string): 'info' | 'success' | 'danger' {
  return productionDocumentStatusTypes[status] ?? 'info'
}

export function productionMovementTypeLabel(type?: string | null): string {
  const key = String(type ?? '')
  return productionMovementTypeLabels[key] ?? (key || '类型未返回')
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

export function validateProductionDate(value: unknown, label: string): string | null {
  const normalizedValue = String(value ?? '').trim()
  if (!normalizedValue) {
    return `${label}不能为空`
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(normalizedValue)) {
    return `${label}格式必须为 YYYY-MM-DD`
  }
  const [year, month, day] = normalizedValue.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  if (
    date.getUTCFullYear() !== year
    || date.getUTCMonth() !== month - 1
    || date.getUTCDate() !== day
  ) {
    return `${label}不是有效日期`
  }
  return null
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

export function createProductionIdempotencyKey(prefix: string): string {
  const randomPart = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`
  return `${prefix}-${randomPart}`
}
