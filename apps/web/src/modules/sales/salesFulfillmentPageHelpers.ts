import type {
  SalesApprovalStatus,
  SalesDeliveryPlanRecord,
  SalesDeliveryPlanStatus,
  SalesOrderChangeStatus,
  SalesQuoteLineRecord,
  SalesQuoteStatus,
} from '../../shared/api/salesFulfillmentApi'

export function salesFulfillmentErrorMessage(error: unknown): string {
  return error instanceof Error && error.message ? error.message : '操作失败，请稍后重试'
}

export function formatSalesDecimal(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const text = String(value)
  return text.includes('.') ? text.replace(/0+$/, '').replace(/\.$/, '') : text
}

export function normalizeSalesId(value: unknown): string | number {
  const text = String(value)
  return /^\d+$/.test(text) ? Number(text) : text
}

export function optionalSalesId(value: unknown): string | number | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined
  }
  return normalizeSalesId(value)
}

export function quoteStatusLabel(status: SalesQuoteStatus | string): string {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    APPROVED: '已批准',
    CONVERTED: '已转换',
    EXPIRED: '已过期',
    CANCELLED: '已取消',
  }
  return labels[status] ?? status
}

export function approvalStatusLabel(status?: SalesApprovalStatus | string | null): string {
  if (!status || status === 'NONE') {
    return '未提交'
  }
  const labels: Record<string, string> = {
    SUBMITTED: '已提交',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    WITHDRAWN: '已撤回',
    CANCELLED: '已取消',
  }
  return labels[status] ?? status
}

export function deliveryPlanStatusLabel(status: SalesDeliveryPlanStatus | string): string {
  const labels: Record<string, string> = {
    PLANNED: '计划中',
    PARTIALLY_SHIPPED: '部分出库',
    SHIPPED: '已全部出库',
    CLOSED: '已关闭',
    CANCELLED: '已取消',
  }
  return labels[status] ?? status
}

export function deliveryPlanDate(plan: Pick<SalesDeliveryPlanRecord, 'plannedDate' | 'planDate'>): string {
  return plan.plannedDate ?? plan.planDate ?? '-'
}

export function orderChangeStatusLabel(status: SalesOrderChangeStatus | string): string {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    APPLIED: '已应用',
    CANCELLED: '已取消',
  }
  return labels[status] ?? status
}

export function salesSourceChainLabel(hasQuote: boolean): string {
  return hasQuote
    ? '报价 -> 合同/订单 -> 交付计划 -> 出库 -> 退货/关闭'
    : '手工订单 -> 交付计划 -> 出库 -> 退货/关闭'
}

export function projectSalesLabel(record: {
  projectId?: unknown
  projectNo?: string | null
  projectCode?: string | null
  projectName?: string | null
}): string {
  if (record.projectId) {
    return `项目销售 · ${record.projectNo ?? record.projectCode ?? '项目未返回'}/${record.projectName ?? '项目未返回'}`
  }
  return '普通销售 · 未关联项目'
}

export function quoteUntaxedAmount(record: {
  totalUntaxedAmount?: string | number | null
  taxExcludedAmount?: string | number | null
}) {
  return record.totalUntaxedAmount ?? record.taxExcludedAmount
}

export function quoteTaxAmount(record: {
  totalTaxAmount?: string | number | null
  taxAmount?: string | number | null
}) {
  return record.totalTaxAmount ?? record.taxAmount
}

export function quoteTaxIncludedAmount(record: {
  totalTaxIncludedAmount?: string | number | null
  taxIncludedAmount?: string | number | null
}) {
  return record.totalTaxIncludedAmount ?? record.taxIncludedAmount
}

export function quoteLineUntaxedUnitPrice(line: Pick<SalesQuoteLineRecord, 'untaxedUnitPrice' | 'taxExcludedUnitPrice'>) {
  return line.untaxedUnitPrice ?? line.taxExcludedUnitPrice
}

export function quoteLineUntaxedAmount(line: Pick<SalesQuoteLineRecord, 'untaxedAmount' | 'taxExcludedAmount'>) {
  return line.untaxedAmount ?? line.taxExcludedAmount
}

export function quoteLineRequiredDate(line: Pick<SalesQuoteLineRecord, 'promisedDate' | 'requiredDate'>) {
  return line.promisedDate ?? line.requiredDate
}
