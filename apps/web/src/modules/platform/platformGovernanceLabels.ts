import type { DataRepairStatus, HistoryImportStatus } from '../../shared/api/platformGovernanceApi'

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
  return labels[status] ?? status
}

export function dataRepairStatusTagType(status: DataRepairStatus | string): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'VERIFIED') {
    return 'success'
  }
  if (status === 'REJECTED' || status === 'CANCELLED' || status === 'FAILED' || status === 'VERIFY_FAILED') {
    return 'danger'
  }
  if (status === 'READY_TO_EXECUTE' || status === 'EXECUTED') {
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
  return risk ? labels[risk] ?? risk : '-'
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
  return labels[status] ?? status
}

export function historyImportStatusTagType(status: HistoryImportStatus | string): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'SUCCEEDED' || status === 'READY_TO_COMMIT') {
    return 'success'
  }
  if (status === 'VALIDATION_FAILED' || status === 'FAILED' || status === 'EXPIRED') {
    return 'danger'
  }
  if (status === 'CANCELLED') {
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
  return labels[code] ?? code
}
