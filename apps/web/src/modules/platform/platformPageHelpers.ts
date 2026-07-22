import type {
  ApprovalInstanceStatus,
  ApprovalScope,
  ApprovalTaskStatus,
  DocumentTaskStage,
  DocumentTaskStatus,
  DocumentTaskType,
  MessageStatus,
} from '../../shared/api/documentPlatformApi'
import { createUnknownStatusDisplay } from '../../shared/status/statusDisplay'

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
  return labelFromMap(status, labels, 'status', '未知状态')
}

export function approvalStatusTagType(status: string): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'APPROVED') {
    return 'success'
  }
  if (status === 'REJECTED') {
    return 'danger'
  }
  if (!knownApprovalStatuses.has(status)) {
    return 'warning'
  }
  return 'info'
}

export function approvalActionLabel(action?: string | null): string {
  const labels: Record<string, string> = {
    SUBMIT: '提交',
    APPROVE: '通过',
    REJECT: '驳回',
    WITHDRAW: '撤回',
    CANCEL: '取消',
  }
  return labelFromMap(action, labels, 'action', '未知动作')
}

export function messageStatusLabel(status: MessageStatus | string): string {
  if (status === 'UNREAD') {
    return '未读'
  }
  if (status === 'READ') {
    return '已读'
  }
  return unknownStatusLabel('messageStatus', status)
}

export function documentTaskTypeLabel(type: DocumentTaskType | string): string {
  const labels: Record<string, string> = {
    MATERIAL_IMPORT: '物料导入',
    MATERIAL_EXPORT: '物料导出',
    BOM_DRAFT_IMPORT: 'BOM 草稿导入',
    BOM_DRAFT_EXPORT: 'BOM 草稿导出',
    APPROVAL_PRINT: '审批单打印',
    PROCUREMENT_REQUISITION_EXPORT: '采购请购导出',
    PROCUREMENT_INQUIRY_EXPORT: '采购询价导出',
    PROCUREMENT_QUOTE_IMPORT: '采购报价导入',
    PROCUREMENT_QUOTE_EXPORT: '采购报价导出',
    PROCUREMENT_PRICE_AGREEMENT_EXPORT: '价格协议导出',
    PROCUREMENT_ORDER_EXPORT: '采购订单导出',
    PROCUREMENT_SCHEDULE_EXPORT: '到货计划导出',
    PROCUREMENT_ORDER_PRINT: '采购订单打印',
    PROCUREMENT_SUPPLY_EXPORT: '有效采购供给导出',
    SALES_QUOTE_PRINT: '销售报价打印',
    SALES_QUOTE_EXPORT: '销售报价导出',
    SALES_DELIVERY_PLAN_EXPORT: '交付计划导出',
    SALES_EFFECTIVE_DEMAND_EXPORT: '有效销售需求导出',
    MATERIAL_REQUIREMENT_RUN_EXPORT: '订单缺料分析导出',
    DATA_REPAIR_EXECUTE: '数据修复执行',
    HISTORY_IMPORT_CUSTOMER: '客户历史导入',
    HISTORY_IMPORT_SUPPLIER: '供应商历史导入',
    HISTORY_IMPORT_MATERIAL: '物料历史导入',
    HISTORY_IMPORT_BOM_DRAFT: 'BOM 草稿历史导入',
    HISTORY_IMPORT_SALES_PROJECT: '销售项目草稿历史导入',
    CUSTOMER_MASTER_V1_HISTORY_IMPORT: '客户历史导入',
    SUPPLIER_MASTER_V1_HISTORY_IMPORT: '供应商历史导入',
    MATERIAL_MASTER_V1_HISTORY_IMPORT: '物料历史导入',
    BOM_DRAFT_V1_HISTORY_IMPORT: 'BOM 草稿历史导入',
    SALES_PROJECT_V1_HISTORY_IMPORT: '销售项目草稿历史导入',
    BATCH_CUSTOMER_STATUS_CHANGE: '客户状态批量变更',
    BATCH_SUPPLIER_STATUS_CHANGE: '供应商状态批量变更',
    BATCH_MATERIAL_STATUS_CHANGE: '物料状态批量变更',
    FIXED_DOCUMENT_BATCH_PRINT: '固定单据批量打印',
    SALES_ORDER_PRINT: '销售订单打印',
    SALES_SHIPMENT_PRINT: '销售出库打印',
    PROCUREMENT_RECEIPT_PRINT: '采购入库打印',
    INVENTORY_TRANSFER_PRINT: '仓库调拨打印',
    PRODUCTION_WORK_ORDER_PRINT: '生产工单打印',
    PRODUCTION_MATERIAL_ISSUE_PRINT: '生产领料打印',
    PRODUCTION_COMPLETION_RECEIPT_PRINT: '完工入库打印',
    SALES_INVOICE_PRINT: '销售发票打印',
    PURCHASE_INVOICE_PRINT: '采购发票打印',
    ACCOUNTING_VOUCHER_PRINT: '会计凭证打印',
  }
  return labelFromMap(type, labels, 'taskType', '未知任务')
}

export function documentTaskStageLabel(stage: DocumentTaskStage | string): string {
  const labels: Record<string, string> = {
    VALIDATE: '校验',
    COMMIT: '提交',
    EXPORT: '导出',
    PRINT: '打印',
    BATCH: '批量',
    REPAIR: '修复',
  }
  return labelFromMap(stage, labels, 'taskStage', '未知阶段')
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
  return labelFromMap(status, labels, 'taskStatus', '未知状态')
}

export function documentTaskStatusTagType(status: DocumentTaskStatus | string): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'SUCCEEDED' || status === 'READY_TO_COMMIT') {
    return 'success'
  }
  if (status === 'VALIDATION_FAILED' || status === 'FAILED') {
    return 'danger'
  }
  if (status === 'EXPIRED' || !knownDocumentTaskStatuses.has(status)) {
    return 'warning'
  }
  return 'info'
}

function labelFromMap(value: string | null | undefined, labels: Record<string, string>, field: string, fallback: string): string {
  return value && labels[value] ? labels[value] : unknownStatusLabel(field, value, fallback)
}

function unknownStatusLabel(field: string, code: unknown, fallback = '未知状态'): string {
  if (!code && fallback !== '未知状态') {
    return fallback
  }
  if (!code) {
    return createUnknownStatusDisplay({ domain: 'platform', field, code }).label
  }
  const display = createUnknownStatusDisplay({ domain: 'platform', field, code })
  return display.label === '未知状态' ? fallback : display.label
}

const knownApprovalStatuses = new Set(['SUBMITTED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED', 'PENDING'])
const knownDocumentTaskStatuses = new Set(['QUEUED', 'RUNNING', 'READY_TO_COMMIT', 'VALIDATION_FAILED', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED'])
