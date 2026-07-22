import type {
  ResourceId,
  PriceAgreementStatus,
  PurchaseOrderLineRecord,
  ProcurementMode,
  ProcurementInquiryStatus,
  ProcurementRequisitionStatus,
  PurchaseInTransitStatus,
  PurchaseOrderStatus,
  PurchaseReceiptLineRecord,
  PurchaseReceiptStatus,
  PurchaseScheduleStatus,
  SupplierQuoteStatus,
} from '../../shared/api/procurementApi'
import type { InventoryTrackingAllocationPayload, InventoryTrackingMethod } from '../../shared/api/inventoryApi'
import { createUnknownStatusDisplay, type StatusTone } from '../../shared/status/statusDisplay'

export interface ProcurementDecimalValidationResult {
  value: string | null
  payloadValue: string | null
  message: string | null
}

export interface PurchaseOrderLineDraft {
  lineNo: number
  materialId: ResourceId | ''
  unitId: ResourceId | ''
  unitName: string
  quantity: string
  unitPrice: string
  procurementMode?: ProcurementMode | null
  projectId?: ResourceId | null
  projectCode?: string | null
  projectName?: string | null
  requisitionLineId?: ResourceId | null
  requisitionSourceLabel?: string
  quoteLineId?: ResourceId | null
  quoteSourceLabel?: string
  priceAgreementLineId?: ResourceId | null
  priceAgreementSourceLabel?: string
  taxRate: string
  taxIncludedUnitPrice: string
  taxExcludedUnitPrice: string
  currency: string
  expectedArrivalDate: string
  remark: string
}

export interface PurchaseOrderSourceOption {
  id: ResourceId
  label: string
  materialId?: ResourceId | null
  materialCode?: string | null
  materialName?: string | null
  unitId?: ResourceId | null
  unitName?: string | null
  quantity?: string | null
  supplierId?: ResourceId | null
  procurementMode?: ProcurementMode | null
  projectId?: ResourceId | null
  projectCode?: string | null
  projectName?: string | null
  taxRate?: string | null
  taxIncludedUnitPrice?: string | null
  taxExcludedUnitPrice?: string | null
  currency?: string | null
}

export interface PurchaseReceiptSourceLine {
  id: ResourceId
  lineNo: number
  materialId: ResourceId
  materialCode: string
  materialName: string
  trackingMethod?: InventoryTrackingMethod | null
  trackingMethodName?: string | null
  unitId: ResourceId
  unitName: string
  orderedQuantity: string
  receivedQuantityBefore: string
  remainingQuantityBefore: string
  inTransitQuantity?: string | number | null
  inTransitStatus?: string | null
  inTransitStatusName?: string | null
}

export interface PurchaseReceiptLineDraft {
  lineNo: number
  orderLineId: ResourceId | ''
  materialId: ResourceId | ''
  materialCode: string
  materialName: string
  trackingMethod?: InventoryTrackingMethod | null
  trackingMethodName?: string | null
  unitId: ResourceId | ''
  unitName: string
  orderedQuantity: string
  receivedQuantityBefore: string
  remainingQuantityBefore: string
  inTransitQuantity?: string | number | null
  inTransitStatus?: string | null
  inTransitStatusName?: string | null
  quantity: string
  trackingAllocations: InventoryTrackingAllocationPayload[]
  remark: string
}

const purchaseOrderStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  PARTIALLY_RECEIVED: '部分入库',
  RECEIVED: '全部入库',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const purchaseOrderStatusTypes: Record<string, StatusTone> = {
  DRAFT: 'info',
  CONFIRMED: 'success',
  PARTIALLY_RECEIVED: 'warning',
  RECEIVED: 'success',
  CLOSED: 'info',
  CANCELLED: 'info',
}

const purchaseReceiptStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
}

const purchaseReceiptStatusTypes: Record<string, StatusTone> = {
  DRAFT: 'info',
  POSTED: 'success',
}

const procurementRequisitionStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVED: '已批准',
  PARTIALLY_ORDERED: '部分转单',
  ORDERED: '已转单',
  CLOSED: '已结案',
  CANCELLED: '已取消',
}

const procurementRequisitionStatusTypes: Record<string, StatusTone> = {
  DRAFT: 'info',
  SUBMITTED: 'warning',
  APPROVED: 'success',
  PARTIALLY_ORDERED: 'warning',
  ORDERED: 'success',
  CLOSED: 'info',
  CANCELLED: 'info',
}

const procurementInquiryStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  RELEASED: '已发布',
  COMPLETED: '已完成',
  AWARDED: '已定标',
  CANCELLED: '已取消',
}

const supplierQuoteStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  VALID: '有效',
  SELECTED: '已选中',
  REJECTED: '已拒绝',
  EXPIRED: '已过期',
  CANCELLED: '已取消',
}

const priceAgreementStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  ACTIVE: '生效中',
  DISABLED: '已停用',
  EXPIRED: '已过期',
  CANCELLED: '已取消',
}

const purchaseScheduleStatusLabels: Record<string, string> = {
  PLANNED: '计划中',
  PARTIALLY_RECEIVED: '部分入库',
  RECEIVED: '已全部入库',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const purchaseInTransitStatusLabels: Record<string, string> = {
  NORMAL: '正常',
  DUE_SOON: '即将到期',
  OVERDUE: '已逾期',
  NOT_COUNTED: '不计入',
}

const procurementApprovalStatusLabels: Record<string, string> = {
  NOT_SUBMITTED: '未提交',
  NONE: '未提交',
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  CANCELLED: '已取消',
}

const procurementPriceSourceTypeLabels: Record<string, string> = {
  QUOTE: '供应商报价',
  QUOTE_SELECTION: '供应商报价',
  AGREEMENT: '价格协议',
  PUBLIC_DIRECT: '公共直采例外',
  MIXED: '混合来源',
}

const procurementModeLabels: Record<ProcurementMode, string> = {
  PUBLIC: '公共采购',
  PROJECT: '项目专采',
}

const procurementOwnershipTagTypes: Record<ProcurementMode, 'info' | 'success'> = {
  PUBLIC: 'info',
  PROJECT: 'success',
}

function normalizedDisplayText(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value).trim()
}

function hasChineseText(value: string): boolean {
  return /[\u4e00-\u9fff]/.test(value)
}

function knownOrFallbackLabel(
  code: unknown,
  labels: Record<string, string>,
  unknownLabel: string,
  context: { domain: string; field: string },
  serverName?: unknown,
): string {
  const codeText = normalizedDisplayText(code).toUpperCase()
  const serverText = normalizedDisplayText(serverName)
  if (serverText && serverText !== codeText && hasChineseText(serverText)) {
    return serverText
  }
  if (!codeText && !serverText) {
    return unknownLabel === '未提交' ? '未提交' : '-'
  }
  const knownLabel = labels[codeText] || labels[serverText]
  if (knownLabel) {
    return knownLabel
  }
  if (unknownLabel === '未知状态') {
    return createUnknownStatusDisplay({
      ...context,
      code: codeText,
      statusName: serverText,
    }).label
  }
  return unknownLabel
}

export function purchaseOrderStatusLabel(status?: PurchaseOrderStatus | string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, purchaseOrderStatusLabels, '未知状态', {
    domain: '采购',
    field: '采购订单状态',
  }, statusName)
}

export function purchaseOrderStatusTagType(status?: PurchaseOrderStatus | string | null): StatusTone {
  return purchaseOrderStatusTypes[normalizeStatusCode(status)] ?? 'warning'
}

export function purchaseReceiptStatusLabel(status?: PurchaseReceiptStatus | string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, purchaseReceiptStatusLabels, '未知状态', {
    domain: '采购',
    field: '采购入库状态',
  }, statusName)
}

export function purchaseReceiptStatusTagType(status?: PurchaseReceiptStatus | string | null): StatusTone {
  return purchaseReceiptStatusTypes[normalizeStatusCode(status)] ?? 'warning'
}

export function procurementRequisitionStatusLabel(
  status?: ProcurementRequisitionStatus | string | null,
  statusName?: string | null,
): string {
  const code = normalizeStatusCode(status)
  const displayName = String(statusName ?? '').trim()
  if (displayName && displayName !== code && hasChineseText(displayName)) {
    return displayName
  }
  if (!code) {
    return '未知状态'
  }
  return procurementRequisitionStatusLabels[code] ?? '未知状态'
}

export function procurementRequisitionStatusTagType(
  status?: ProcurementRequisitionStatus | string | null,
): StatusTone {
  return procurementRequisitionStatusTypes[normalizeStatusCode(status)] ?? 'info'
}

export function procurementInquiryStatusLabel(
  status?: ProcurementInquiryStatus | string | null,
  statusName?: string | null,
): string {
  return knownOrFallbackLabel(status, procurementInquiryStatusLabels, '未知状态', {
    domain: '采购',
    field: '询价状态',
  }, statusName)
}

export function supplierQuoteStatusLabel(status?: SupplierQuoteStatus | string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, supplierQuoteStatusLabels, '未知状态', {
    domain: '采购',
    field: '供应商报价状态',
  }, statusName)
}

export function priceAgreementStatusLabel(status?: PriceAgreementStatus | string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, priceAgreementStatusLabels, '未知状态', {
    domain: '采购',
    field: '价格协议状态',
  }, statusName)
}

export function purchaseScheduleStatusLabel(
  status?: PurchaseScheduleStatus | string | null,
  statusName?: string | null,
): string {
  return knownOrFallbackLabel(status, purchaseScheduleStatusLabels, '未知状态', {
    domain: '采购',
    field: '到货计划状态',
  }, statusName)
}

export function purchaseInTransitStatusLabel(
  status?: PurchaseInTransitStatus | string | null,
  statusName?: string | null,
): string {
  return knownOrFallbackLabel(status, purchaseInTransitStatusLabels, '未知状态', {
    domain: '采购',
    field: '在途状态',
  }, statusName)
}

export function procurementApprovalStatusLabel(status?: string | null, statusName?: string | null): string {
  if (!normalizedDisplayText(status) && !normalizedDisplayText(statusName)) {
    return '未提交'
  }
  return knownOrFallbackLabel(status, procurementApprovalStatusLabels, '未知状态', {
    domain: '采购',
    field: '审批状态',
  }, statusName)
}

export function procurementErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

export function formatProcurementQuantity(value: unknown): string {
  const normalizedValue = normalizeDecimalString(value)
  if (normalizedValue === null) {
    return '-'
  }
  return trimDecimalZeros(normalizedValue)
}

export function formatProcurementAmount(value: unknown): string {
  const normalizedValue = normalizeDecimalString(value)
  if (normalizedValue === null) {
    return '-'
  }
  return trimDecimalZeros(normalizedValue)
}

export function formatProcurementDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function validateProcurementDecimal(
  value: unknown,
  options: { label: string; allowZero?: boolean },
): ProcurementDecimalValidationResult {
  if (value === null || value === undefined || value === '') {
    return { value: null, payloadValue: null, message: `${options.label}不能为空` }
  }

  const normalizedValue = String(value).trim()
  if (!normalizedValue) {
    return { value: null, payloadValue: null, message: `${options.label}不能为空` }
  }
  if (normalizedValue.startsWith('-') || /[eE]/.test(normalizedValue)) {
    return { value: null, payloadValue: null, message: `${options.label}仅支持普通十进制非负数` }
  }
  if (!/^\d+(?:\.\d+)?$/.test(normalizedValue)) {
    return { value: null, payloadValue: null, message: `${options.label}仅支持普通十进制非负数` }
  }

  const [integerPart, decimalPart = ''] = normalizedValue.split('.')
  if (integerPart.length > 12) {
    return { value: null, payloadValue: null, message: `${options.label}整数部分最多 12 位` }
  }
  if (decimalPart.length > 6) {
    return { value: null, payloadValue: null, message: `${options.label}最多 6 位小数` }
  }

  const zero = isZeroDecimal(normalizedValue)
  if ((!options.allowZero && zero) || decimalCompare(normalizedValue, '0') < 0) {
    return {
      value: null,
      payloadValue: null,
      message: options.allowZero ? `${options.label}不能小于 0` : `${options.label}必须大于 0`,
    }
  }

  return { value: normalizedValue, payloadValue: normalizedValue, message: null }
}

export function procurementModeLabel(mode: ProcurementMode): string {
  return procurementModeLabels[mode]
}

export interface ProcurementOwnershipDisplaySource {
  purchaseMode?: ProcurementMode | string | null
  procurementMode?: ProcurementMode | string | null
  ownershipType?: ProcurementMode | string | null
  projectId?: ResourceId | null
  projectCode?: string | null
  projectName?: string | null
}

export interface ProcurementPriceSourceDisplaySource {
  priceSourceType?: string | null
  priceSourceTypeName?: string | null
  priceSourceNo?: string | null
  sourceNo?: string | null
  quoteNo?: string | null
  agreementNo?: string | null
}

export function normalizeProcurementMode(value?: string | null): ProcurementMode | undefined {
  return value === 'PROJECT' || value === 'PUBLIC' ? value : undefined
}

export function procurementModeFrom(source?: ProcurementOwnershipDisplaySource | null): ProcurementMode | undefined {
  return normalizeProcurementMode(source?.procurementMode)
    ?? normalizeProcurementMode(source?.purchaseMode)
    ?? normalizeProcurementMode(source?.ownershipType)
}

export function procurementModeDisplay(
  mode?: ProcurementMode | string | null,
  projectCode?: string | null,
  projectName?: string | null,
  projectId?: ResourceId | null,
): string {
  const normalizedMode = normalizeProcurementMode(mode)
  if (normalizedMode === 'PROJECT') {
    const projectText = [projectCode, projectName].filter(Boolean).join('/')
    if (projectText) {
      return `项目专采 · ${projectText}`
    }
    return projectId === null || projectId === undefined ? '项目专采 · 项目未返回' : `项目专采 · 项目ID ${projectId}`
  }
  if (normalizedMode === 'PUBLIC') {
    return '公共采购'
  }
  return '采购模式未返回'
}

export function procurementOwnershipDisplay(source?: ProcurementOwnershipDisplaySource | null): string {
  return procurementModeDisplay(
    procurementModeFrom(source),
    source?.projectCode,
    source?.projectName,
    source?.projectId,
  )
}

export function procurementPriceSourceDisplay(
  source?: ProcurementPriceSourceDisplaySource | null,
  fallback = '价格来源未返回',
): string {
  const sourceName = knownOrFallbackLabel(source?.priceSourceType, procurementPriceSourceTypeLabels, '未知价格来源', {
    domain: '采购',
    field: '价格来源',
  }, source?.priceSourceTypeName)
  const sourceNo = source?.priceSourceNo || source?.sourceNo
  if (!source?.priceSourceType && !source?.priceSourceTypeName) {
    return sourceNo ? `${fallback} ${sourceNo}` : fallback
  }
  return sourceNo ? `${sourceName} ${sourceNo}` : sourceName
}

export function procurementOwnershipTagType(mode: ProcurementMode): 'info' | 'success' {
  return procurementOwnershipTagTypes[mode]
}

export function decimalCompare(left: unknown, right: unknown): -1 | 0 | 1 {
  const leftValue = normalizeDecimalString(left)
  const rightValue = normalizeDecimalString(right)
  if (leftValue === null || rightValue === null) {
    return 0
  }
  const leftParts = splitDecimal(leftValue)
  const rightParts = splitDecimal(rightValue)
  const leftInteger = leftParts.integer.replace(/^0+(?=\d)/, '')
  const rightInteger = rightParts.integer.replace(/^0+(?=\d)/, '')
  if (leftInteger.length !== rightInteger.length) {
    return leftInteger.length > rightInteger.length ? 1 : -1
  }
  if (leftInteger !== rightInteger) {
    return leftInteger > rightInteger ? 1 : -1
  }
  const decimalLength = Math.max(leftParts.decimal.length, rightParts.decimal.length)
  const leftDecimal = leftParts.decimal.padEnd(decimalLength, '0')
  const rightDecimal = rightParts.decimal.padEnd(decimalLength, '0')
  if (leftDecimal === rightDecimal) {
    return 0
  }
  return leftDecimal > rightDecimal ? 1 : -1
}

export function validatePurchaseQuantity(value: unknown): ProcurementDecimalValidationResult {
  return validateProcurementDecimal(value, { label: '数量' })
}

export function validatePurchaseUnitPrice(value: unknown): ProcurementDecimalValidationResult {
  return validateProcurementDecimal(value, { label: '单价', allowZero: true })
}

export function nextPurchaseOrderLineNo(lines: Array<{ lineNo: number }>): number {
  const maxLineNo = lines.reduce((max, line) => Math.max(max, Number(line.lineNo) || 0), 0)
  return maxLineNo + 10
}

export function newPurchaseOrderLine(lineNo = 10): PurchaseOrderLineDraft {
  return {
    lineNo,
    materialId: '',
    unitId: '',
    unitName: '',
    quantity: '',
    unitPrice: '',
    procurementMode: null,
    projectId: null,
    projectCode: null,
    projectName: null,
    requisitionLineId: null,
    requisitionSourceLabel: '',
    quoteLineId: null,
    quoteSourceLabel: '',
    priceAgreementLineId: null,
    priceAgreementSourceLabel: '',
    taxRate: '',
    taxIncludedUnitPrice: '',
    taxExcludedUnitPrice: '',
    currency: 'CNY',
    expectedArrivalDate: '',
    remark: '',
  }
}

export function nextPurchaseReceiptLineNo(lines: Array<{ lineNo: number }>): number {
  const maxLineNo = lines.reduce((max, line) => Math.max(max, Number(line.lineNo) || 0), 0)
  return maxLineNo + 10
}

export function newPurchaseReceiptLine(lineNo = 10): PurchaseReceiptLineDraft {
  return {
    lineNo,
    orderLineId: '',
    materialId: '',
    materialCode: '',
    materialName: '',
    trackingMethod: 'NONE',
    trackingMethodName: '不追踪',
    unitId: '',
    unitName: '',
    orderedQuantity: '0',
    receivedQuantityBefore: '0',
    remainingQuantityBefore: '0',
    inTransitQuantity: null,
    inTransitStatus: null,
    inTransitStatusName: null,
    quantity: '',
    trackingAllocations: [],
    remark: '',
  }
}

export function purchaseReceiptSourceFromOrderLine(line: PurchaseOrderLineRecord): PurchaseReceiptSourceLine {
  return {
    id: line.id,
    lineNo: line.lineNo,
    materialId: line.materialId,
    materialCode: line.materialCode,
    materialName: line.materialName,
    trackingMethod: undefined,
    trackingMethodName: undefined,
    unitId: line.unitId,
    unitName: line.unitName,
    orderedQuantity: normalizeDecimalString(line.quantity) ?? '0',
    receivedQuantityBefore: normalizeDecimalString(line.receivedQuantity) ?? '0',
    remainingQuantityBefore: normalizeDecimalString(line.remainingQuantity) ?? '0',
    inTransitQuantity: line.inTransitQuantity ?? null,
    inTransitStatus: line.inTransitStatus ?? null,
    inTransitStatusName: line.inTransitStatusName ?? null,
  }
}

export function purchaseReceiptSourceFromReceiptLine(line: PurchaseReceiptLineRecord): PurchaseReceiptSourceLine {
  return {
    id: line.orderLineId,
    lineNo: line.lineNo,
    materialId: line.materialId,
    materialCode: line.materialCode,
    materialName: line.materialName,
    trackingMethod: line.trackingMethod ?? 'NONE',
    trackingMethodName: line.trackingMethodName ?? '不追踪',
    unitId: line.unitId,
    unitName: line.unitName,
    orderedQuantity: normalizeDecimalString(line.orderedQuantity) ?? '0',
    receivedQuantityBefore: normalizeDecimalString(line.receivedQuantityBefore) ?? '0',
    remainingQuantityBefore: normalizeDecimalString(line.remainingQuantityBefore) ?? '0',
    inTransitQuantity: line.inTransitQuantity ?? null,
    inTransitStatus: line.inTransitStatus ?? null,
    inTransitStatusName: line.inTransitStatusName ?? null,
  }
}

function normalizeDecimalString(value: unknown): string | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const normalizedValue = String(value).trim()
  if (!/^\d+(?:\.\d+)?$/.test(normalizedValue)) {
    return null
  }
  return normalizedValue
}

function trimDecimalZeros(value: string): string {
  if (!value.includes('.')) {
    return value
  }
  return value.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '')
}

function splitDecimal(value: string): { integer: string; decimal: string } {
  const [integer, decimal = ''] = value.split('.')
  return { integer, decimal }
}

function isZeroDecimal(value: string): boolean {
  return /^0+(?:\.0+)?$/.test(value)
}

function normalizeStatusCode(value: unknown): string {
  return String(value ?? '').trim().toUpperCase()
}

export function normalizeOptionalId(value: ResourceId | ''): ResourceId | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined
  }
  const trimmedValue = String(value).trim()
  if (!trimmedValue) {
    return undefined
  }
  const numericValue = Number(trimmedValue)
  return Number.isFinite(numericValue) ? numericValue : trimmedValue
}

export function normalizeRequiredId(value: ResourceId | ''): ResourceId | null {
  return normalizeOptionalId(value) ?? null
}
