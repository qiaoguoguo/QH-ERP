import type { DataRepairStatus, HistoryImportStatus } from '../../shared/api/platformGovernanceApi'
import { createUnknownStatusDisplay } from '../../shared/status/statusDisplay'

export function dataRepairStatusLabel(status: DataRepairStatus | string): string {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING_APPROVAL: '待审批',
    READY_TO_EXECUTE: '待执行',
    EXECUTING: '执行中',
    EXECUTED: '待验证',
    VERIFIED: '已验证',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
    FAILED: '执行失败',
    VERIFY_FAILED: '验证失败',
  }
  return labelFromMap(status, labels, 'dataRepairStatus', '未知状态')
}

export function dataRepairStatusTagType(status: DataRepairStatus | string): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'VERIFIED') {
    return 'success'
  }
  if (status === 'REJECTED' || status === 'FAILED' || status === 'VERIFY_FAILED') {
    return 'danger'
  }
  if (status === 'READY_TO_EXECUTE' || status === 'EXECUTED' || !knownDataRepairStatuses.has(status)) {
    return 'warning'
  }
  return 'info'
}

export function dataRepairRiskLabel(risk?: string | null): string {
  const labels: Record<string, string> = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
  }
  return risk ? labels[risk] ?? '未知风险' : '-'
}

export function historyImportStatusLabel(status: HistoryImportStatus | string): string {
  const labels: Record<string, string> = {
    QUEUED: '排队中',
    RUNNING: '执行中',
    READY_TO_COMMIT: '待确认',
    VALIDATION_FAILED: '预检失败',
    SUCCEEDED: '已完成',
    FAILED: '执行失败',
    CANCELLED: '已取消',
    EXPIRED: '已过期',
  }
  return labelFromMap(status, labels, 'historyImportStatus', '未知状态')
}

export function historyImportStatusTagType(status: HistoryImportStatus | string): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'SUCCEEDED' || status === 'READY_TO_COMMIT') {
    return 'success'
  }
  if (status === 'VALIDATION_FAILED' || status === 'FAILED') {
    return 'danger'
  }
  if (status === 'EXPIRED' || !knownHistoryImportStatuses.has(status)) {
    return 'warning'
  }
  return 'info'
}

export function governanceErrorLabel(code: string): string {
  const labels: Record<string, string> = {
    DATA_REPAIR_NOT_FOUND: '数据修复记录不存在',
    DATA_REPAIR_STATUS_INVALID: '数据修复状态不可执行',
    DATA_REPAIR_ADAPTER_NOT_SUPPORTED: '数据修复适配器不支持',
    DATA_REPAIR_FIELD_NOT_ALLOWED: '字段不在修复白名单',
    DATA_REPAIR_SELF_APPROVAL_FORBIDDEN: '申请人不得自批',
    DATA_REPAIR_SELF_VERIFY_FORBIDDEN: '执行人不得自验',
    DATA_REPAIR_OBJECT_CHANGED: '目标对象已变化',
    DATA_REPAIR_EXECUTION_FAILED: '数据修复执行失败',
    DATA_REPAIR_VERIFICATION_FAILED: '数据修复验证失败',
    HISTORY_IMPORT_ADAPTER_NOT_SUPPORTED: '历史导入适配器不支持',
    HISTORY_IMPORT_TEMPLATE_VERSION_MISMATCH: '历史导入模板版本不一致',
    HISTORY_IMPORT_ALREADY_EXISTS: '历史导入记录已存在',
    BATCH_TOOL_NOT_SUPPORTED: '批量工具不支持',
    BATCH_OPERATION_STATUS_INVALID: '批量操作状态不可执行',
    BATCH_OPERATION_PRECHECK_FAILED: '批量操作预检失败',
    BATCH_OPERATION_OBJECT_CHANGED: '批量对象已变化',
    DELIVERY_ASSET_NOT_AVAILABLE: '交付资料不可用',
  }
  return labels[code] ?? '未知错误'
}

export function dataRepairCheckStageLabel(stage?: string | null): string {
  const labels: Record<string, string> = {
    PRECHECK: '预检查',
    VERIFY: '验证',
    EXECUTE: '执行',
  }
  return labelFromMap(stage, labels, 'dataRepairCheckStage', '未知阶段')
}

export function dataRepairCheckStatusLabel(status?: string | null): string {
  const labels: Record<string, string> = {
    PASSED: '通过',
    FAILED: '失败',
    WARNING: '警告',
    SKIPPED: '跳过',
  }
  return labelFromMap(status, labels, 'dataRepairCheckStatus', '未知状态')
}

export function dataRepairEventActionLabel(action?: string | null): string {
  const labels: Record<string, string> = {
    CREATE: '创建',
    SUBMIT: '提交',
    APPROVE: '审批通过',
    REJECT: '审批驳回',
    EXECUTE: '执行',
    VERIFY: '验证',
    CANCEL: '取消',
  }
  return labelFromMap(action, labels, 'dataRepairEventAction', '未知动作')
}

export function batchOperationStatusLabel(status?: string | null): string {
  const labels: Record<string, string> = {
    PRECHECKED: '预检通过',
    PRECHECK_FAILED: '预检失败',
    EXECUTING: '执行中',
    SUCCEEDED: '执行成功',
    FAILED: '执行失败',
    CANCELLED: '已取消',
    EXPIRED: '已过期',
  }
  return labelFromMap(status, labels, 'batchOperationStatus', '未知状态')
}

export function batchOperationItemStatusLabel(status?: string | null): string {
  const labels: Record<string, string> = {
    READY: '可执行',
    BLOCKED: '阻断',
    SUCCEEDED: '成功',
    FAILED: '失败',
  }
  return labelFromMap(status, labels, 'batchOperationItemStatus', '未知状态')
}

export function batchOperationItemStatusTagType(status?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'READY' || status === 'SUCCEEDED') {
    return 'success'
  }
  if (status === 'BLOCKED' || status === 'FAILED') {
    return 'danger'
  }
  if (!status || !knownBatchOperationItemStatuses.has(status)) {
    return 'warning'
  }
  return 'info'
}

export function deliveryAssetStatusLabel(status?: string | null): string {
  const labels: Record<string, string> = {
    ACTIVE: '启用',
    ENABLED: '启用',
    AVAILABLE: '可用',
    VERIFIED: '已验证',
    INACTIVE: '停用',
    DISABLED: '停用',
    NOT_VERIFIED: '未验证',
    DEPRECATED: '已停用',
  }
  return labelFromMap(status, labels, 'deliveryAssetStatus', '未知状态')
}

export function deliveryAssetStatusTagType(status?: string | null): 'success' | 'warning' | 'info' {
  if (status === 'ACTIVE' || status === 'ENABLED' || status === 'AVAILABLE' || status === 'VERIFIED') {
    return 'success'
  }
  if (status === 'INACTIVE' || status === 'DISABLED' || status === 'DEPRECATED') {
    return 'info'
  }
  return 'warning'
}

export function deliveryAssetActionLabel(action?: string | null): string {
  const labels: Record<string, string> = {
    STATUS_CHANGE: '状态变更',
    PRINT: '打印',
    IMPORT: '导入',
    EXPORT: '导出',
  }
  return labelFromMap(action, labels, 'deliveryAssetAction', '未知动作')
}

export function deliveryAssetObjectTypeLabel(type?: string | null): string {
  const labels: Record<string, string> = {
    CUSTOMER: '客户',
    SUPPLIER: '供应商',
    MATERIAL: '物料',
    BOM: 'BOM',
    SALES_PROJECT: '销售项目',
    SALES_ORDER: '销售订单',
    SALES_QUOTE: '销售报价',
    SALES_PROJECT_CONTRACT: '销售合同',
    PURCHASE_ORDER: '采购订单',
    DOCUMENT: '业务单据',
    GL_VOUCHER: '总账凭证',
    FINANCIAL_PERIOD_REOPEN: '反结账审批',
  }
  return labelFromMap(type, labels, 'deliveryAssetObjectType', '未知对象类型')
}

export function demoDataStatusLabel(status?: string | null): string {
  const labels: Record<string, string> = {
    VERIFIED: '已验证',
    NOT_VERIFIED: '未验证',
    VERIFY_FAILED: '验证失败',
    MISSING: '缺失',
  }
  return labelFromMap(status, labels, 'demoDataStatus', '未知状态')
}

function labelFromMap(value: string | null | undefined, labels: Record<string, string>, field: string, fallback: string): string {
  if (value && labels[value]) {
    return labels[value]
  }
  if (!value) {
    return fallback
  }
  const display = createUnknownStatusDisplay({ domain: 'platform-governance', field, code: value })
  return display.label === '未知状态' ? fallback : display.label
}

const knownDataRepairStatuses = new Set(['DRAFT', 'PENDING_APPROVAL', 'READY_TO_EXECUTE', 'EXECUTING', 'EXECUTED', 'VERIFIED', 'REJECTED', 'CANCELLED', 'FAILED', 'VERIFY_FAILED'])
const knownHistoryImportStatuses = new Set(['QUEUED', 'RUNNING', 'READY_TO_COMMIT', 'VALIDATION_FAILED', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED'])
const knownBatchOperationItemStatuses = new Set(['READY', 'BLOCKED', 'SUCCEEDED', 'FAILED'])
