import type { PayableStatus, PaymentStatus, ReceivableStatus, ReceiptStatus, FinanceSourceType } from '../../shared/api/financeApi'

type TagType = '' | 'success' | 'warning' | 'info' | 'danger'

export const financePermissions = {
  receivableView: 'finance:receivable:view',
  receivableCreate: 'finance:receivable:create',
  receivableUpdate: 'finance:receivable:update',
  receivableConfirm: 'finance:receivable:confirm',
  receivableCancel: 'finance:receivable:cancel',
  receivableClose: 'finance:receivable:close',
  receiptView: 'finance:receipt:view',
  receiptCreate: 'finance:receipt:create',
  receiptUpdate: 'finance:receipt:update',
  receiptPost: 'finance:receipt:post',
  receiptCancel: 'finance:receipt:cancel',
  payableView: 'finance:payable:view',
  payableCreate: 'finance:payable:create',
  payableUpdate: 'finance:payable:update',
  payableConfirm: 'finance:payable:confirm',
  payableCancel: 'finance:payable:cancel',
  payableClose: 'finance:payable:close',
  paymentView: 'finance:payment:view',
  paymentCreate: 'finance:payment:create',
  paymentUpdate: 'finance:payment:update',
  paymentPost: 'finance:payment:post',
  paymentCancel: 'finance:payment:cancel',
} as const

export const financeViewPermissions = [
  financePermissions.receivableView,
  financePermissions.receiptView,
  financePermissions.payableView,
  financePermissions.paymentView,
] as const

export const receivableStatusText: Record<ReceivableStatus, string> = {
  DRAFT: '草稿',
  CONFIRMED: '待收款',
  PARTIALLY_RECEIVED: '部分收款',
  RECEIVED: '已收清',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

export const receiptStatusText: Record<ReceiptStatus, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
  CANCELLED: '已取消',
}

export const payableStatusText: Record<PayableStatus, string> = {
  DRAFT: '草稿',
  CONFIRMED: '待付款',
  PARTIALLY_PAID: '部分付款',
  PAID: '已付清',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

export const paymentStatusText: Record<PaymentStatus, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
  CANCELLED: '已取消',
}

export const receivableStatusTagType: Record<ReceivableStatus, TagType> = {
  DRAFT: 'info',
  CONFIRMED: '',
  PARTIALLY_RECEIVED: 'warning',
  RECEIVED: 'success',
  CLOSED: 'info',
  CANCELLED: 'danger',
}

export const receiptStatusTagType: Record<ReceiptStatus, TagType> = {
  DRAFT: 'info',
  POSTED: 'success',
  CANCELLED: 'danger',
}

export const payableStatusTagType: Record<PayableStatus, TagType> = {
  DRAFT: 'info',
  CONFIRMED: '',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
  CLOSED: 'info',
  CANCELLED: 'danger',
}

export const paymentStatusTagType: Record<PaymentStatus, TagType> = {
  DRAFT: 'info',
  POSTED: 'success',
  CANCELLED: 'danger',
}

export function formatFinanceAmount(value: string | number | null | undefined) {
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
  const groupedInteger = normalizedInteger.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const normalizedDecimal = `${decimalPart}00`.slice(0, 2)
  return `${sign}${groupedInteger}.${normalizedDecimal}`
}

export function normalizeOptionalId(value: string | number | null | undefined) {
  return value === '' || value === null || value === undefined ? undefined : value
}

export function financeErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

export function compareFinanceAmount(left: string | number, right: string | number) {
  const leftCents = toFinanceCents(left)
  const rightCents = toFinanceCents(right)
  if (leftCents === null || rightCents === null) {
    return null
  }
  if (leftCents === rightCents) {
    return 0
  }
  return leftCents > rightCents ? 1 : -1
}

export function isPositiveFinanceAmount(value: string | number) {
  const cents = toFinanceCents(value)
  return cents !== null && cents > 0n
}

function toFinanceCents(value: string | number) {
  const raw = String(value).trim()
  if (!/^\d+(\.\d{1,2})?$/.test(raw)) {
    return null
  }
  const [integerPart, decimalPart = ''] = raw.split('.')
  return BigInt(integerPart || '0') * 100n + BigInt(`${decimalPart}00`.slice(0, 2))
}

export function financeSourceTypeText(value: FinanceSourceType | string) {
  if (value === 'SALES_SHIPMENT') {
    return '销售出库'
  }
  if (value === 'PURCHASE_RECEIPT') {
    return '采购入库'
  }
  return value
}

export function hasAnyFinanceViewPermission(hasPermission: (permission: string) => boolean) {
  return financeViewPermissions.some((permission) => hasPermission(permission))
}

export function firstFinanceRouteByPermission(hasPermission: (permission: string) => boolean) {
  if (hasPermission(financePermissions.receivableView)) {
    return '/finance/receivables'
  }
  if (hasPermission(financePermissions.receiptView)) {
    return '/finance/receipts'
  }
  if (hasPermission(financePermissions.payableView)) {
    return '/finance/payables'
  }
  if (hasPermission(financePermissions.paymentView)) {
    return '/finance/payments'
  }
  return null
}
