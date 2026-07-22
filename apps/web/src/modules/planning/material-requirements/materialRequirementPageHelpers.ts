import type {
  MaterialRequirementRunStatus,
  MaterialRequirementSuggestionStatus,
  MaterialRequirementSuggestionType,
  ResourceId,
} from '../../../shared/api/materialRequirementApi'

export const materialRequirementPermissions = {
  view: 'planning:material-requirement:view',
  calculate: 'planning:material-requirement:calculate',
  export: 'planning:material-requirement:export',
  manageSuggestion: 'planning:material-requirement:manage-suggestion',
  convertRequisition: 'planning:material-requirement:convert-requisition',
  convertProduction: 'planning:material-requirement:convert-production',
  convertOutsourcing: 'planning:material-requirement:convert-outsourcing',
} as const

export function materialRequirementErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '请求失败，请稍后重试'
}

export function formatMaterialRequirementQuantity(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const text = String(value).trim()
  if (!/^\d+(?:\.\d+)?$/.test(text)) {
    return text || '-'
  }
  return text.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '')
}

export function formatMaterialRequirementDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

function normalizedCode(value?: string | null): string {
  return String(value ?? '').trim().toUpperCase()
}

function displayTextFromServer(displayText?: string | null, code?: string | null): string | null {
  const text = String(displayText ?? '').trim()
  if (!text || text === String(code ?? '').trim() || text === normalizedCode(code) || /^[A-Z][A-Z0-9_]*$/.test(text)) {
    return null
  }
  return text
}

export function runStatusLabel(status?: MaterialRequirementRunStatus | null, statusName?: string | null): string {
  const serverText = displayTextFromServer(statusName, status)
  if (serverText) {
    return serverText
  }
  const labels: Record<string, string> = {
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
    STALE: '来源已变化',
    EXPIRED: '已过期',
  }
  return labels[normalizedCode(status)] ?? '未知状态'
}

export function runStatusTagType(status?: MaterialRequirementRunStatus | null): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'COMPLETED') {
    return 'success'
  }
  if (status === 'STALE' || status === 'EXPIRED') {
    return 'warning'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

export function suggestionStatusLabel(
  status?: MaterialRequirementSuggestionStatus | null,
  statusName?: string | null,
): string {
  const serverText = displayTextFromServer(statusName, status)
  if (serverText) {
    return serverText
  }
  const labels: Record<string, string> = {
    OPEN: '待处理',
    CONFIRMED: '已确认',
    CONVERTED: '已转单',
    DISMISSED: '已驳回',
  }
  return labels[normalizedCode(status)] ?? '未知状态'
}

export function suggestionStatusTagType(
  status?: MaterialRequirementSuggestionStatus | null,
): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'CONFIRMED' || status === 'CONVERTED') {
    return 'success'
  }
  if (status === 'DISMISSED') {
    return 'danger'
  }
  return 'info'
}

export function suggestionTypeLabel(type?: MaterialRequirementSuggestionType | null): string {
  const labels: Record<string, string> = {
    PURCHASE_REQUISITION: '采购请购建议',
    PRODUCTION_ORDER: '生产建议',
    USE_PUBLIC_STOCK: '公共库存使用建议',
    USE_EXISTING_SUPPLY: '既有供给使用建议',
  }
  return labels[normalizedCode(type)] ?? '未知建议类型'
}

export function coverageStatusLabel(status?: string | null): string {
  const labels: Record<string, string> = {
    COVERED: '已覆盖',
    PARTIAL: '部分覆盖',
    SHORTAGE: '短缺',
    EXCEPTION: '异常',
  }
  return labels[normalizedCode(status)] ?? '未知覆盖状态'
}

export function reasonCodeLabel(code?: string | null): string {
  const labels: Record<string, string> = {
    DEMAND_NOT_COUNTED: '需求未计入',
    OUTSIDE_HORIZON: '超出分析窗口',
    BOM_NOT_FOUND: '未找到 BOM',
    BOM_NOT_EFFECTIVE: 'BOM 未生效',
    BOM_CYCLE_DETECTED: 'BOM 循环',
    UNIT_CONVERSION_REQUIRED: '缺少单位换算',
    MATERIAL_DISABLED: '物料停用',
    STOCK_NOT_QUALIFIED: '库存质量不可用',
    STOCK_RESERVED_OR_OCCUPIED: '库存已预留或占用',
    SUPPLY_LATE: '供给晚于需求',
    SUPPLY_STATUS_NOT_COUNTED: '供给状态未计入',
    CROSS_PROJECT_NOT_ALLOWED: '跨项目资源不可用',
    PUBLIC_SUPPLY_POTENTIAL_ONLY: '公共供给仅提示',
    WORK_ORDER_NOT_PROJECT_BOUND: '工单未绑定项目',
    SOURCE_CHANGED_SINCE_RUN: '来源已变化',
    SNAPSHOT_EXPIRED: '快照已过期',
  }
  if (!code) {
    return '-'
  }
  return labels[normalizedCode(code)] ?? '未知原因'
}

export function ownershipTypeLabel(type?: string | null): string {
  const labels: Record<string, string> = {
    PROJECT: '项目库存',
    PUBLIC: '公共库存',
  }
  return labels[normalizedCode(type)] ?? '未知归属'
}

export function supplyTypeLabel(type?: string | null, typeName?: string | null): string {
  const serverText = displayTextFromServer(typeName, type)
  if (serverText) {
    return serverText
  }
  const labels: Record<string, string> = {
    PROJECT_STOCK: '项目库存',
    PUBLIC_STOCK: '公共库存',
    RESERVED_STOCK: '预留占用库存',
    PURCHASE_SUPPLY: '采购供给',
    WORK_ORDER_SUPPLY: '工单供给',
    PRODUCTION_SUPPLY: '生产供给',
  }
  return labels[normalizedCode(type)] ?? '未知供给类型'
}

export function materialRequirementReasonText(
  source: {
    actionDisabledReason?: string | null
    reasonMessage?: string | null
    reasonCode?: string | null
  },
  fallback = '未知原因',
): string {
  const actionDisabledReason = displayTextFromServer(source.actionDisabledReason, source.reasonCode)
  if (actionDisabledReason) {
    return actionDisabledReason
  }
  const reasonMessage = displayTextFromServer(source.reasonMessage, source.reasonCode)
  if (reasonMessage) {
    return reasonMessage
  }
  const reasonLabel = reasonCodeLabel(source.reasonCode)
  return reasonLabel === '-' || reasonLabel === '未知原因' ? fallback : reasonLabel
}

export function failedRunSummaryLabel(failureCode?: string | null, failureSummary?: string | null): string {
  const codeLabel = reasonCodeLabel(failureCode)
  const summary = String(failureSummary ?? '').trim()
  if (codeLabel !== '-' && summary) {
    return `${codeLabel}：${summary}`
  }
  if (summary) {
    return summary
  }
  if (codeLabel !== '-') {
    return codeLabel
  }
  return '失败原因未返回'
}

export function normalizeOptionalId(value: ResourceId | ''): ResourceId | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined
  }
  const trimmed = String(value).trim()
  if (!trimmed) {
    return undefined
  }
  return /^\d+$/.test(trimmed) ? +trimmed : trimmed
}

export function hasAllowedAction(source: { allowedActions?: string[] } | null | undefined, action: string): boolean {
  return Boolean(source?.allowedActions?.includes(action))
}
