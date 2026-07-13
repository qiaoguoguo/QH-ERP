import type {
  ApprovalInstanceStatus,
  ApprovalScope,
  ApprovalTaskStatus,
  DocumentTaskStage,
  DocumentTaskStatus,
  DocumentTaskType,
  MessageStatus,
} from '../../shared/api/documentPlatformApi'

export function platformErrorMessage(caught: unknown): string {
  return caught instanceof Error ? caught.message : '请求失败，请重试'
}

export function formatPlatformDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function approvalScopeLabel(scope: ApprovalScope): string {
  const labels: Record<ApprovalScope, string> = {
    TODO: '我的待办',
    DONE: '已处理',
    STARTED: '我发起的',
  }
  return labels[scope]
}

export function approvalStatusLabel(status: ApprovalInstanceStatus | ApprovalTaskStatus | string): string {
  const labels: Record<string, string> = {
    SUBMITTED: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    WITHDRAWN: '已撤回',
    CANCELLED: '已取消',
    PENDING: '待处理',
  }
  return labels[status] ?? status
}

export function approvalStatusTagType(status: string): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'APPROVED') {
    return 'success'
  }
  if (status === 'REJECTED' || status === 'CANCELLED') {
    return 'danger'
  }
  if (status === 'WITHDRAWN') {
    return 'warning'
  }
  return 'info'
}

export function messageStatusLabel(status: MessageStatus): string {
  return status === 'UNREAD' ? '未读' : '已读'
}

export function documentTaskTypeLabel(type: DocumentTaskType | string): string {
  const labels: Record<string, string> = {
    MATERIAL_IMPORT: '物料导入',
    MATERIAL_EXPORT: '物料导出',
    BOM_DRAFT_IMPORT: 'BOM 草稿导入',
    BOM_DRAFT_EXPORT: 'BOM 草稿导出',
    APPROVAL_PRINT: '审批单打印',
  }
  return labels[type] ?? type
}

export function documentTaskStageLabel(stage: DocumentTaskStage | string): string {
  const labels: Record<string, string> = {
    VALIDATE: '校验',
    COMMIT: '提交',
    EXPORT: '导出',
    PRINT: '打印',
  }
  return labels[stage] ?? stage
}

export function documentTaskStatusLabel(status: DocumentTaskStatus | string): string {
  const labels: Record<string, string> = {
    QUEUED: '排队中',
    RUNNING: '执行中',
    READY_TO_COMMIT: '待确认',
    VALIDATION_FAILED: '校验失败',
    SUCCEEDED: '已完成',
    FAILED: '执行失败',
    CANCELLED: '已取消',
    EXPIRED: '结果过期',
  }
  return labels[status] ?? status
}

export function documentTaskStatusTagType(status: DocumentTaskStatus | string): 'info' | 'success' | 'warning' | 'danger' {
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
