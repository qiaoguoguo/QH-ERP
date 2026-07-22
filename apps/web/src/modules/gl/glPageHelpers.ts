import { AccountPermissionApiError, type PageResult } from '../../shared/api/accountPermissionApi'
import type { GlVoucherRecord } from '../../shared/api/glApi'

export const glPermissions = {
  periodView: 'gl:period:view',
  periodInitialize: 'gl:period:initialize',
  periodCreate: 'gl:period:create',
  accountView: 'gl:account:view',
  accountCreate: 'gl:account:create',
  accountUpdate: 'gl:account:update',
  accountDisable: 'gl:account:disable',
  auxiliaryView: 'gl:auxiliary:view',
  auxiliaryManage: 'gl:auxiliary:manage',
  ruleView: 'gl:rule:view',
  ruleManage: 'gl:rule:manage',
  voucherView: 'gl:voucher:view',
  voucherCreate: 'gl:voucher:create',
  voucherUpdate: 'gl:voucher:update',
  voucherConvert: 'gl:voucher:convert',
  voucherSubmit: 'gl:voucher:submit',
  voucherCancel: 'gl:voucher:cancel',
  voucherReverse: 'gl:voucher:reverse',
  voucherApprovePost: 'gl:voucher:approve-post',
  ledgerView: 'gl:ledger:view',
  balanceView: 'gl:balance:view',
  amountView: 'gl:amount:view',
  sourceView: 'gl:source:view',
} as const

export const glPageSizes = [10, 20, 50, 100]
export const glDefaultSourceVariant = 'DEFAULT'

export function glPageItems<T>(page: PageResult<T>): T[] {
  return page.items ?? page.records ?? page.content ?? []
}

export function glPageTotal<T>(page: PageResult<T>): number {
  const normalized = page as PageResult<T> & { totalElements?: number }
  return normalized.total ?? normalized.totalElements ?? glPageItems(page).length
}

export function createGlIdempotencyKey(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export function glErrorMessage(error: unknown) {
  if (error instanceof AccountPermissionApiError && error.status === 403) {
    return '无权访问会计核算数据'
  }
  if (error instanceof AccountPermissionApiError && error.status === 409) {
    return error.message || '数据已被其他用户变更，请刷新后重试'
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

export function formatGlAmount(value: string | number | null | undefined, restrictedReason?: string | null) {
  if (restrictedReason) {
    return restrictedReason
  }
  if (value === null || value === undefined || value === '') {
    return '0.00'
  }
  const raw = String(value).trim()
  const sign = raw.startsWith('-') ? '-' : ''
  const unsigned = sign ? raw.slice(1) : raw
  if (!/^\d+(\.\d+)?$/.test(unsigned)) {
    return raw
  }
  const [integerPart, decimalPart = ''] = unsigned.split('.')
  const normalizedInteger = integerPart.replace(/^0+(?=\d)/, '') || '0'
  return `${sign}${normalizedInteger.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${`${decimalPart}00`.slice(0, 2)}`
}

export function formatGlDateTime(value: string | null | undefined) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-'
}

export function glAccountCategoryText(value: string | null | undefined) {
  const text: Record<string, string> = {
    ASSET: '资产',
    LIABILITY: '负债',
    COMMON: '共同',
    EQUITY: '所有者权益',
    COST: '成本',
    PROFIT_LOSS: '损益',
  }
  return labelFromMap(value, text, '未知科目类别')
}

export function glBalanceDirectionText(value: string | null | undefined) {
  const text: Record<string, string> = {
    DEBIT: '借方',
    CREDIT: '贷方',
  }
  return labelFromMap(value, text, '未知方向')
}

export function glVoucherStatusText(value: string | null | undefined) {
  const text: Record<string, string> = {
    DRAFT: '草稿',
    SUBMITTED: '审批中',
    POSTED: '已记账',
    CANCELLED: '已取消',
  }
  return labelFromMap(value, text, '未知状态')
}

export function glVoucherTypeText(value: string | null | undefined) {
  const text: Record<string, string> = {
    GENERAL: '普通凭证',
    OPENING: '期初凭证',
  }
  return labelFromMap(value, text, '未知凭证类型')
}

export function glAuxDimensionTypeText(value: string | null | undefined) {
  const text: Record<string, string> = {
    SYSTEM: '系统维度',
    CUSTOM: '自定义维度',
  }
  return labelFromMap(value, text, '未知维度类型')
}

export function glApprovalStatusText(value: string | null | undefined) {
  const text: Record<string, string> = {
    DRAFT: '草稿',
    SUBMITTED: '审批中',
    APPROVED: '已审批',
    REJECTED: '已拒绝',
    CANCELLED: '已取消',
    POSTED: '已记账',
  }
  return labelFromMap(value, text, '未知审批状态')
}

export function glPeriodStatusText(value: string | null | undefined) {
  const text: Record<string, string> = {
    OPEN: '开放',
    CLOSED: '已关闭',
  }
  return labelFromMap(value, text, '未知期间状态')
}

export function glActionAllowed(record: { allowedActions?: string[] | null }, action: string) {
  return record.allowedActions?.includes(action) ?? false
}

export function glActionDisabledReason(record: { actionDisabledReasons?: Record<string, string> | null }, action: string) {
  return record.actionDisabledReasons?.[action] ?? ''
}

export function glVoucherDisplayNo(record: Pick<GlVoucherRecord, 'voucherNo' | 'draftNo'>) {
  return record.voucherNo || record.draftNo
}

export function glFormalSourceText(record: Pick<GlVoucherRecord, 'sourceType' | 'sourceNo' | 'sourceVisible' | 'restrictedReason'>) {
  if (record.sourceVisible === false) {
    return record.restrictedReason || '无权查看来源'
  }
  return `正式来源 ${[glSourceTypeText(record.sourceType || 'MANUAL'), record.sourceNo].filter(Boolean).join(' ')}`
}

export function glCombinedActionDisabledReason(record: { actionDisabledReasons?: Record<string, string> | null }) {
  const reasons = record.actionDisabledReasons ?? {}
  return ['CANCEL', 'SUBMIT', 'UPDATE', 'CREATE', 'REVERSE', 'WITHDRAW']
    .map((action) => reasons[action])
    .find((reason): reason is string => Boolean(reason)) ?? ''
}

export function glAllowedActionsText(actions: string[] | null | undefined) {
  const labels: Record<string, string> = {
    UPDATE: '可编辑',
    SUBMIT: '可提交',
    CANCEL: '可取消',
    WITHDRAW: '可撤回',
    REVERSE: '可冲销',
    CREATE: '可创建',
    CREATE_CHILD: '可新增下级',
    NEW_VERSION: '可复制新版本',
    VALIDATE: '可预览校验',
    ACTIVATE: '可激活',
    DISABLE: '可停用',
  }
  return actions?.length ? actions.map((action) => labelFromMap(action, labels, '未知动作')).join('、') : '-'
}

export function glFinancialCloseStatusText(value: string | null | undefined) {
  const text: Record<string, string> = {
    READY: '可结账',
    BLOCKED: '阻断',
    CHECKING: '检查中',
    STALE: '已失效',
    CONSUMED: '已关闭',
    CLOSED: '已关闭',
    OPEN: '开放',
  }
  return labelFromMap(value, text, '未知财务结账状态')
}

export function glBusinessSourceText(record: Pick<GlVoucherRecord, 'businessSourceType' | 'sourceOriginalType' | 'businessSourceNo' | 'sourceOriginalNo' | 'sourceVisible' | 'restrictedReason'>) {
  if (record.sourceVisible === false) {
    return record.restrictedReason || '无权查看来源'
  }
  const sourceType = record.sourceOriginalType || record.businessSourceType
  const sourceNo = record.sourceOriginalNo || record.businessSourceNo
  if (!sourceType && !sourceNo) {
    return '业务来源 -'
  }
  return `业务来源 ${[glSourceTypeText(sourceType), sourceNo].filter(Boolean).join(' ')}`
}

export function glBusinessSourceMetaText(record: Pick<GlVoucherRecord, 'sourceOriginalVersion' | 'businessSourceVersion' | 'sourceOriginalFingerprint' | 'businessSourceFingerprint' | 'sourceVisible' | 'restrictedReason'>) {
  if (record.sourceVisible === false) {
    return record.restrictedReason || '无权查看来源'
  }
  const version = record.sourceOriginalVersion ?? record.businessSourceVersion
  const fingerprint = record.sourceOriginalFingerprint || record.businessSourceFingerprint
  return [
    version === null || version === undefined ? '' : `业务来源版本 ${version}`,
    fingerprint ? `来源指纹 ${fingerprint}` : '',
  ].filter(Boolean).join(' / ') || '来源版本 -'
}

export function glSourceTypeText(value: string | null | undefined) {
  const text: Record<string, string> = {
    MANUAL: '手工凭证',
    FIN_VOUCHER_DRAFT: '财务凭证草稿',
    PROFIT_LOSS_TRANSFER: '期末损益结转',
    PROFIT_LOSS_CARRYFORWARD: '期末损益结转',
    TAX_SUMMARY: '税额汇总',
    SALES_INVOICE: '销售发票',
    PURCHASE_INVOICE: '采购发票',
    REVERSAL: '冲销凭证',
  }
  return labelFromMap(value, text, '未知来源')
}

export function glSourceVariantText(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return value === 'DEFAULT' ? '默认变体' : '自定义变体'
}

export function glPostingRuleStatusText(value: string | null | undefined) {
  const text: Record<string, string> = {
    DRAFT: '草稿',
    ACTIVE: '已启用',
    SUPERSEDED: '已替代',
    DISABLED: '已停用',
  }
  return labelFromMap(value, text, '未知规则状态')
}

export function glPostingRuleValidationStatusText(value: string | null | undefined) {
  const text: Record<string, string> = {
    PENDING: '待校验',
    VALID: '校验通过',
    INVALID: '校验未通过',
    WARNING: '校验警告',
  }
  return labelFromMap(value, text, '未知校验状态')
}

export function glPostingRuleDirectionText(value: string | null | undefined) {
  return glBalanceDirectionText(value)
}

function labelFromMap(value: string | null | undefined, labels: Record<string, string>, unknownLabel: string): string {
  if (!value) {
    return '-'
  }
  return labels[value] ?? unknownLabel
}
