import { AccountPermissionApiError, type PageResult } from '../../shared/api/accountPermissionApi'
import type { FinancialCloseActionState, FinancialCloseAmount } from '../../shared/api/financialCloseApi'

export const financialClosePageSizes = [10, 20, 50, 100]

export const financialClosePermissions = {
  periodView: 'financial-close:period:view',
  periodCheck: 'financial-close:period:check',
  periodClose: 'financial-close:period:close',
  periodReopen: 'financial-close:period:reopen',
  profitLossView: 'financial-close:profit-loss:view',
  profitLossGenerate: 'financial-close:profit-loss:generate',
  bankAccountView: 'financial-close:bank-account:view',
  bankAccountManage: 'financial-close:bank-account:manage',
  bankReconciliationView: 'financial-close:bank-reconciliation:view',
  bankReconciliationImport: 'financial-close:bank-reconciliation:import',
  bankReconciliationMatch: 'financial-close:bank-reconciliation:match',
  bankReconciliationConfirm: 'financial-close:bank-reconciliation:confirm',
  bankReconciliationReopen: 'financial-close:bank-reconciliation:reopen',
  taxProfileView: 'financial-close:tax-profile:view',
  taxProfileManage: 'financial-close:tax-profile:manage',
  taxSummaryView: 'financial-close:tax-summary:view',
  taxSummaryCalculate: 'financial-close:tax-summary:calculate',
  taxSummaryConfirm: 'financial-close:tax-summary:confirm',
  taxSummaryGenerateVoucher: 'financial-close:tax-summary:generate-voucher',
  taxPaymentView: 'financial-close:tax-payment:view',
  taxPaymentManage: 'financial-close:tax-payment:manage',
  amountView: 'financial-close:amount:view',
  sourceView: 'financial-close:source:view',
  bankSensitiveView: 'financial-close:bank-sensitive:view',
} as const

export function createFinancialCloseIdempotencyKey(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export function financialClosePageItems<T>(page: PageResult<T>): T[] {
  return page.items ?? page.records ?? page.content ?? []
}

export function financialClosePageTotal<T>(page: PageResult<T>): number {
  const normalized = page as PageResult<T> & { totalElements?: number }
  return normalized.total ?? normalized.totalElements ?? financialClosePageItems(page).length
}

export function financialCloseErrorMessage(caught: unknown): string {
  if (caught instanceof AccountPermissionApiError) {
    return `${caught.message}${caught.code ? `（${caught.code}）` : ''}`
  }
  if (caught instanceof Error) {
    return caught.message
  }
  return String(caught)
}

export function formatFinancialCloseAmount(value: FinancialCloseAmount | null | undefined, restrictedReason?: string | null) {
  if (restrictedReason) {
    return restrictedReason
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

export function financialCloseActionDisabledReason(record: Partial<FinancialCloseActionState>, action: string) {
  return record.actionDisabledReasons?.[action] ?? ''
}

export function canFinancialCloseAction(record: Partial<FinancialCloseActionState>, action: string) {
  return record.allowedActions?.includes(action) ?? false
}

export function sourceVisibleText(visible: boolean | null | undefined) {
  return visible === false ? '来源受限' : '来源可追溯'
}

export function bankSensitiveText(visible: boolean | null | undefined) {
  return visible === false ? '账号已脱敏' : '账号明文权限可见'
}

export function financialCloseStatusText(status: string | null | undefined) {
  const labels: Record<string, string> = {
    OPEN: '开放',
    CLOSED: '已关闭',
    READY: '可结账',
    BLOCKED: '阻断',
    CHECKING: '检查中',
    STALE: '已失效',
    CONSUMED: '已消费',
    FAILED: '失败',
    REOPENED: '已反结账',
    POSTED: '已记账',
    PREVIEWED: '已预览',
    DRAFT: '草稿',
    SUBMITTED: '审批中',
    CONFIRMED: '已确认',
    CALCULATED: '已计算',
    PARTIAL_MATCHED: '部分匹配',
    READY_TO_CONFIRM: '待确认',
  }
  return status ? labels[status] ?? status : '-'
}

export const taxFoundationDisclaimer = '基础汇总/估算，非正式申报'
