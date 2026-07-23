import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { ExceptionReportRow } from '../../shared/api/businessReportingApi'
import type { ApprovalTaskRecord, DocumentTaskRecord } from '../../shared/api/documentPlatformApi'
import {
  documentTaskStatusLabel,
  documentTaskTypeLabel,
  formatPlatformDateTime,
} from '../platform/platformPageHelpers'

const decimalFormatter = new Intl.NumberFormat('zh-CN', {
  maximumFractionDigits: 3,
})

const moneyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const knownTaskStatuses = new Set([
  'QUEUED',
  'RUNNING',
  'READY_TO_COMMIT',
  'VALIDATION_FAILED',
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
  'EXPIRED',
])

const exceptionTypeLabels: Record<string, string> = {
  SALES_DELIVERY_OVERDUE: '销售交付逾期',
  PROCUREMENT_RECEIPT_OVERDUE: '采购到货逾期',
  INVENTORY_SHORTAGE: '库存不足',
  PRODUCTION_OVERDUE: '生产逾期',
  COST_MISSING: '成本未归集',
  RECEIVABLE_OVERDUE: '应收逾期',
  PAYABLE_DUE_SOON: '应付临期',
}

const exceptionSeverityLabels: Record<string, string> = {
  CRITICAL: '严重',
  WARNING: '普通',
}

function finiteNumber(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized : null
}

export function formatWorkbenchMoney(value: string | number | null | undefined): string {
  const normalized = finiteNumber(value)
  return normalized === null ? '—' : `¥${moneyFormatter.format(normalized)}`
}

export function formatWorkbenchNumber(
  value: string | number | null | undefined,
  suffix = '',
): string {
  const normalized = finiteNumber(value)
  if (normalized === null) {
    return '—'
  }
  const formatted = decimalFormatter.format(normalized)
  return suffix ? `${formatted} ${suffix}` : formatted
}

export function formatWorkbenchDateTime(value: string | null | undefined): string {
  return value ? formatPlatformDateTime(value) : '—'
}

export function clampProgress(value: number | null | undefined): number | null {
  const normalized = finiteNumber(value)
  return normalized === null ? null : Math.min(100, Math.max(0, normalized))
}

export function taskDisplayName(record: DocumentTaskRecord): string {
  return documentTaskTypeLabel(record.taskType)
}

export function taskStatusText(status: string | null | undefined): string {
  return status && knownTaskStatuses.has(status)
    ? documentTaskStatusLabel(status)
    : '未知任务状态'
}

export function exceptionTypeText(type: string | null | undefined): string {
  return type && exceptionTypeLabels[type] ? exceptionTypeLabels[type] : '未知异常类型'
}

export function exceptionSeverityText(severity: string | null | undefined): string {
  return severity && exceptionSeverityLabels[severity]
    ? exceptionSeverityLabels[severity]
    : '未知严重程度'
}

export function exceptionRowKey(
  record: Pick<ExceptionReportRow, 'traceKey' | 'exceptionType' | 'sourceId'>,
  index: number,
): string {
  const traceKey = record.traceKey?.trim()
  return traceKey || `${record.exceptionType || 'UNKNOWN'}-${record.sourceId ?? 'restricted'}-${index}`
}

function taskTimestamp(record: DocumentTaskRecord): string {
  return record.completedAt ?? record.createdAt ?? ''
}

export function mergeTaskPages(
  pages: PageResult<DocumentTaskRecord>[],
  limit: number,
): { total: number; items: DocumentTaskRecord[] } {
  const total = pages.reduce((sum, page) => sum + Number(page.total ?? 0), 0)
  const items = pages
    .flatMap((page) => page.items ?? [])
    .sort((left, right) => taskTimestamp(right).localeCompare(taskTimestamp(left)))
    .slice(0, Math.max(0, limit))
  return { total, items }
}

export function approvalTitle(record: ApprovalTaskRecord): string {
  return record.objectName?.trim()
    || record.objectNo?.trim()
    || record.taskNo?.trim()
    || '审批事项'
}

export function documentTaskRoute(id: string | number, status?: string): string {
  const query = new URLSearchParams()
  query.set('taskId', String(id))
  if (status) {
    query.set('status', status)
  }
  query.set('returnTo', '/')
  return `/platform/document-tasks?${query.toString()}`
}
