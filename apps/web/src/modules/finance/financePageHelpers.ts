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
  settlementAdjustmentView: 'finance:settlement-adjustment:view',
  settlementAdjustmentCreate: 'finance:settlement-adjustment:create',
  settlementAdjustmentUpdate: 'finance:settlement-adjustment:update',
  settlementAdjustmentPost: 'finance:settlement-adjustment:post',
  settlementAdjustmentCancel: 'finance:settlement-adjustment:cancel',
  salesInvoiceView: 'finance:sales-invoice:view',
  salesInvoiceCreate: 'finance:sales-invoice:create',
  salesInvoiceUpdate: 'finance:sales-invoice:update',
  salesInvoiceConfirm: 'finance:sales-invoice:confirm',
  salesInvoiceCancel: 'finance:sales-invoice:cancel',
  purchaseInvoiceView: 'finance:purchase-invoice:view',
  purchaseInvoiceCreate: 'finance:purchase-invoice:create',
  purchaseInvoiceUpdate: 'finance:purchase-invoice:update',
  purchaseInvoiceMatch: 'finance:purchase-invoice:match',
  purchaseInvoiceConfirm: 'finance:purchase-invoice:confirm',
  purchaseInvoiceCancel: 'finance:purchase-invoice:cancel',
  expenseView: 'finance:expense:view',
  expenseCreate: 'finance:expense:create',
  expenseUpdate: 'finance:expense:update',
  expenseConfirm: 'finance:expense:confirm',
  expenseCancel: 'finance:expense:cancel',
  advanceReceiptView: 'finance:advance-receipt:view',
  advanceReceiptCreate: 'finance:advance-receipt:create',
  advanceReceiptUpdate: 'finance:advance-receipt:update',
  advanceReceiptPost: 'finance:advance-receipt:post',
  advanceReceiptCancel: 'finance:advance-receipt:cancel',
  prepaymentView: 'finance:prepayment:view',
  prepaymentCreate: 'finance:prepayment:create',
  prepaymentUpdate: 'finance:prepayment:update',
  prepaymentPost: 'finance:prepayment:post',
  prepaymentCancel: 'finance:prepayment:cancel',
  settlementAllocationView: 'finance:settlement-allocation:view',
  settlementAllocationCreate: 'finance:settlement-allocation:create',
  settlementAllocationUpdate: 'finance:settlement-allocation:update',
  settlementAllocationPost: 'finance:settlement-allocation:post',
  settlementAllocationCancel: 'finance:settlement-allocation:cancel',
  voucherDraftView: 'finance:voucher-draft:view',
  voucherDraftGenerate: 'finance:voucher-draft:generate',
  voucherDraftReady: 'finance:voucher-draft:ready',
  voucherDraftCancel: 'finance:voucher-draft:cancel',
} as const

export const financeViewPermissions = [
  financePermissions.salesInvoiceView,
  financePermissions.purchaseInvoiceView,
  financePermissions.expenseView,
  financePermissions.advanceReceiptView,
  financePermissions.prepaymentView,
  financePermissions.settlementAllocationView,
  financePermissions.voucherDraftView,
  financePermissions.receivableView,
  financePermissions.receiptView,
  financePermissions.payableView,
  financePermissions.paymentView,
  financePermissions.settlementAdjustmentView,
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
  const text: Record<string, string> = {
    SALES_SHIPMENT: '销售出库',
    PURCHASE_RECEIPT: '采购入库',
    SALES_INVOICE: '销售发票',
    PURCHASE_INVOICE: '采购发票',
    EXPENSE: '费用单',
    OUTSOURCING_ORDER: '外协订单',
    OUTSOURCING_RECEIPT: '外协收货',
    RECEIPT: '收款',
    PAYMENT: '付款',
    ADVANCE_RECEIPT: '预收款',
    PREPAYMENT: '预付款',
    SETTLEMENT_ALLOCATION: '核销',
    VOUCHER_DRAFT: '凭证草稿',
    NONE: '无来源',
  }
  return text[value] ?? value
}

export function ownershipTypeText(value: string | null | undefined) {
  return value === 'PROJECT' ? '项目' : '公共'
}

export function invoiceStatusText(value: string | null | undefined) {
  const text: Record<string, string> = {
    DRAFT: '草稿',
    CONFIRMED: '已确认',
    CANCELLED: '已取消',
  }
  return value ? text[value] ?? value : '-'
}

export function settlementStatusText(value: string | null | undefined) {
  const text: Record<string, string> = {
    UNSETTLED: '未结清',
    PARTIALLY_SETTLED: '部分结清',
    SETTLED: '已结清',
    AVAILABLE: '可用',
    PARTIALLY_APPLIED: '部分核销',
    APPLIED: '已核销',
  }
  return value ? text[value] ?? value : '-'
}

export function matchStatusText(value: string | null | undefined) {
  const text: Record<string, string> = {
    UNMATCHED: '未匹配',
    MATCHED: '匹配通过',
    EXCEPTION: '存在差异',
  }
  return value ? text[value] ?? value : '-'
}

export function voucherDraftStatusText(value: string | null | undefined) {
  const text: Record<string, string> = {
    DRAFT: '草稿',
    READY: '待正式制证',
    CANCELLED: '已取消',
  }
  return value ? text[value] ?? value : '-'
}

export function financeMethodText(value: string | null | undefined) {
  const text: Record<string, string> = {
    BANK_TRANSFER: '银行转账',
    CASH: '现金',
    CHECK: '支票',
    OTHER: '其他',
  }
  return value ? text[value] ?? value : '-'
}

export function hasAnyFinanceViewPermission(hasPermission: (permission: string) => boolean) {
  return financeViewPermissions.some((permission) => hasPermission(permission))
}

export function firstFinanceRouteByPermission(hasPermission: (permission: string) => boolean) {
  if (hasPermission(financePermissions.salesInvoiceView)) {
    return '/finance/sales-invoices'
  }
  if (hasPermission(financePermissions.purchaseInvoiceView)) {
    return '/finance/purchase-invoices'
  }
  if (hasPermission(financePermissions.expenseView)) {
    return '/finance/expenses'
  }
  if (hasPermission(financePermissions.advanceReceiptView)) {
    return '/finance/advance-receipts'
  }
  if (hasPermission(financePermissions.prepaymentView)) {
    return '/finance/prepayments'
  }
  if (hasPermission(financePermissions.settlementAllocationView)) {
    return '/finance/settlement-workbench'
  }
  if (hasPermission(financePermissions.voucherDraftView)) {
    return '/finance/voucher-drafts'
  }
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
  if (hasPermission(financePermissions.settlementAdjustmentView)) {
    return '/finance/settlement-adjustments'
  }
  return null
}
