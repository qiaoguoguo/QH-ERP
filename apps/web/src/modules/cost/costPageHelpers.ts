import type {
  CostBasisType,
  CostRecordStatus,
  CostSourceDocumentType,
  CostSourceType,
  CostType,
} from '../../shared/api/costCollectionApi'

export interface CostDecimalValidationResult {
  payloadValue: string | null
  message: string | null
}

const costTypeLabels: Record<CostType, string> = {
  MATERIAL: '材料',
  LABOR: '人工',
  MANUFACTURING_OVERHEAD: '制造费用',
  OTHER: '其他',
}

const sourceTypeLabels: Record<CostSourceType, string> = {
  AUTO_PRODUCTION: '生产自动来源',
  MANUAL_ENTRY: '手工记录',
}

const sourceDocumentTypeLabels: Record<CostSourceDocumentType, string> = {
  PRODUCTION_MATERIAL_ISSUE: '生产领料',
  PRODUCTION_WORK_REPORT: '生产报工',
  PRODUCTION_COMPLETION_RECEIPT: '完工入库',
  MANUAL_COST_RECORD: '手工成本记录',
}

const basisTypeLabels: Record<CostBasisType, string> = {
  SOURCE_QUANTITY_ONLY: '来源数量口径',
  MANUAL_AMOUNT: '手工金额',
  MANUAL_UNIT_PRICE_QUANTITY: '手工单价数量',
  OUTPUT_QUANTITY_TRACE: '产出数量追溯',
}

const statusLabels: Record<CostRecordStatus, string> = {
  ACTIVE: '有效',
  VOIDED: '已作废',
}

const workOrderStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  RELEASED: '已下达',
  IN_PROGRESS: '生产中',
  COMPLETED: '已完工',
  CANCELLED: '已取消',
  CLOSED: '已关闭',
}

const sourceStatusLabels: Record<string, string> = {
  ACTIVE: '有效',
  VOIDED: '已作废',
  DRAFT: '草稿',
  RELEASED: '已下达',
  IN_PROGRESS: '生产中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  POSTED: '已过账',
}

const auditActionLabels: Record<string, string> = {
  MFG_COST_RECORD_CREATE: '成本记录创建',
  MFG_COST_RECORD_UPDATE: '成本记录更新',
  MFG_COST_RECORD_VOID: '成本记录作废',
  COST_RECORD_CREATE: '成本记录创建',
  COST_RECORD_UPDATE: '成本记录更新',
  COST_RECORD_VOID: '成本记录作废',
}

function labelFromMap(value: string | null | undefined, labels: Record<string, string>, unknownLabel: string): string {
  if (!value) {
    return '-'
  }
  return labels[value] ?? unknownLabel
}

export function costTypeLabel(type?: CostType | string | null): string {
  return labelFromMap(type, costTypeLabels, '未知成本类型')
}

export function costTypeTagType(type?: CostType | string | null): 'success' | 'warning' | 'info' | 'primary' {
  if (type === 'MATERIAL') {
    return 'success'
  }
  if (type === 'LABOR') {
    return 'primary'
  }
  if (type === 'MANUFACTURING_OVERHEAD') {
    return 'warning'
  }
  return 'info'
}

export function costSourceTypeLabel(type?: CostSourceType | string | null): string {
  return labelFromMap(type, sourceTypeLabels, '未知来源')
}

export function costSourceTypeTagType(type?: CostSourceType | string | null): 'success' | 'info' {
  return type === 'AUTO_PRODUCTION' ? 'success' : 'info'
}

export function sourceDocumentTypeLabel(type?: CostSourceDocumentType | string | null): string {
  return labelFromMap(type, sourceDocumentTypeLabels, '未知来源单据')
}

export function basisTypeLabel(type?: CostBasisType | string | null): string {
  return labelFromMap(type, basisTypeLabels, '未知口径')
}

export function costStatusLabel(status?: CostRecordStatus | string | null): string {
  return labelFromMap(status, statusLabels, '未知状态')
}

export function costWorkOrderStatusLabel(status?: string | null): string {
  return labelFromMap(status, workOrderStatusLabels, '未知工单状态')
}

export function costSourceStatusLabel(status?: string | null): string {
  return labelFromMap(status, sourceStatusLabels, '未知来源状态')
}

export function costAuditActionLabel(action?: string | null): string {
  return labelFromMap(action, auditActionLabels, '未知动作')
}

export function formatCostQuantity(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return formatCostDecimal(value, 6)
}

export function formatCostAmount(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return formatCostDecimal(value, 6)
}

function formatCostDecimal(value: unknown, scale: number): string {
  const raw = String(value).trim()
  const match = raw.match(/^([+-]?)(\d+)(?:\.(\d+))?$/)
  if (!match) {
    return '-'
  }
  const sign = match[1] === '-' ? '-' : ''
  const integer = match[2].replace(/^0+(?=\d)/, '') || '0'
  const fraction = (match[3] ?? '').slice(0, scale).padEnd(scale, '0').replace(/0+$/, '')
  return `${sign}${integer}${fraction ? `.${fraction}` : ''}`
}

export function formatCostDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function costErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

export function todayText(): string {
  const today = new Date()
  const year = today.getFullYear()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function validateCostDecimal(
  value: unknown,
  label: string,
  options: { allowZero: boolean },
): CostDecimalValidationResult {
  if (value === null || value === undefined || value === '') {
    return { payloadValue: null, message: `${label}不能为空` }
  }

  const normalizedValue = String(value).trim()
  if (!normalizedValue) {
    return { payloadValue: null, message: `${label}不能为空` }
  }
  if (normalizedValue.startsWith('-') || /[eE]/.test(normalizedValue)) {
    return { payloadValue: null, message: `${label}仅支持普通十进制非负数` }
  }
  if (!/^\d+(?:\.\d+)?$/.test(normalizedValue)) {
    return { payloadValue: null, message: `${label}仅支持普通十进制非负数` }
  }

  const [integerPart, decimalPart = ''] = normalizedValue.split('.')
  if (integerPart.length > 12) {
    return { payloadValue: null, message: `${label}整数部分最多 12 位` }
  }
  if (decimalPart.length > 6) {
    return { payloadValue: null, message: `${label}最多 6 位小数` }
  }
  if (!options.allowZero && /^0+(?:\.0+)?$/.test(normalizedValue)) {
    return { payloadValue: null, message: `${label}必须大于 0` }
  }

  return { payloadValue: normalizedValue, message: null }
}
