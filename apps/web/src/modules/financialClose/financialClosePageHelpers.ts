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

export function financialCloseActionState(
  record: Partial<FinancialCloseActionState>,
  action: string,
  hasPermission = true,
  noPermissionReason = '无操作权限',
) {
  const backendReason = financialCloseActionDisabledReason(record, action)
  if (!hasPermission) {
    return { allowed: false, reason: backendReason || noPermissionReason }
  }
  if (!canFinancialCloseAction(record, action)) {
    return { allowed: false, reason: backendReason || '当前状态不允许操作' }
  }
  return { allowed: true, reason: '' }
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
    UNMATCHED: '未匹配',
    PARTIALLY_MATCHED: '部分匹配',
    PARTIAL_MATCHED: '部分匹配',
    MATCHED: '已匹配',
    IGNORED: '已忽略',
    BALANCED: '已平衡',
    RECONCILING: '对账中',
    READY_TO_CONFIRM: '待确认',
    ENABLED: '启用',
    DISABLED: '停用',
  }
  return status ? labels[status] ?? status : '-'
}

export const taxFoundationDisclaimer = '基础汇总/估算，非正式申报'

export function bankDirectionText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    CREDIT: '银行入账',
    DEBIT: '银行出账',
    INFLOW: '银行入账',
    OUTFLOW: '银行出账',
  }
  return value ? labels[value] ?? value : '-'
}

export function bankAccountTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    BASIC: '基本户',
    GENERAL: '一般户',
    SPECIAL: '专用户',
    TEMPORARY: '临时户',
    OTHER: '其他',
  }
  return value ? labels[value] ?? value : '-'
}

export function taxTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    VAT: '增值税',
    INCOME_TAX: '企业所得税',
    URBAN_MAINTENANCE: '城市维护建设税',
    EDUCATION_SURCHARGE: '教育费附加',
  }
  return value ? labels[value] ?? value : '-'
}

export function taxpayerTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    GENERAL: '一般纳税人',
    SMALL_SCALE: '小规模纳税人',
  }
  return value ? labels[value] ?? value : '-'
}

export function sourceTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    GL_VOUCHER: '正式凭证',
    PAYMENT: '付款单',
    TAX_SUMMARY: '税额汇总',
    PROFIT_LOSS_CARRYFORWARD: '期末损益结转',
    PROFIT_LOSS_TRANSFER: '期末损益结转',
    FIN_VOUCHER_DRAFT: '财务凭证草稿',
  }
  return value ? labels[value] ?? value : '-'
}
