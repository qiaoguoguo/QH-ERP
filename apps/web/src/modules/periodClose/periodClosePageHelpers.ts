import type { RouteLocationRaw } from 'vue-router'
import type {
  BusinessPeriodCloseCheckDomain,
  BusinessPeriodCloseCheckSeverity,
  BusinessPeriodCloseReportCode,
  BusinessPeriodCloseSourceRoute,
  BusinessPeriodCloseStatus,
  ResourceId,
} from '../../shared/api/businessPeriodCloseApi'
import { queryWithReturnTo } from '../../shared/navigation/navigationReturn'

export const periodCloseMessages = {
  amountForbidden: '无权查看成本金额',
  sourceRestricted: '来源权限受限，仅显示脱敏摘要',
  conflictAdvice: '请刷新详情或重新检查后再执行高风险动作',
}

const closeStatusLabels: Record<BusinessPeriodCloseStatus, string> = {
  PENDING_CHECK: '待检查',
  BLOCKED: '检查未通过',
  READY: '可月结',
  CLOSED: '已月结',
  REOPENED: '已重开',
  NOT_CHECKED: '未检查',
  MANUAL_LOCKED_WITHOUT_SNAPSHOT: '已手工锁定/无月结快照',
}

const periodStatusLabels: Record<string, string> = {
  OPEN: '开放',
  LOCKED: '已锁定',
}

const auditActionLabels: Record<string, string> = {
  CHECK: '月结检查',
  CLOSE: '关闭月结',
  REOPEN: '重开期间',
  PERIOD_CLOSE_CHECK: '月结检查',
  PERIOD_CLOSE_CLOSE: '关闭月结',
  PERIOD_CLOSE_REOPEN: '重开期间',
}

const domainLabels: Record<BusinessPeriodCloseCheckDomain, string> = {
  INVENTORY: '库存计价',
  WIP: '在制/生产',
  PROJECT_COST: '项目成本',
  REPORT: '报表基线',
}

const severityLabels: Record<BusinessPeriodCloseCheckSeverity, string> = {
  BLOCKING: '阻断',
  WARNING: '警告',
  INFO: '提示',
}

export const periodCloseReportLabels: Record<BusinessPeriodCloseReportCode, string> = {
  OVERVIEW: '经营概览',
  SALES_SUMMARY: '销售汇总',
  PROCUREMENT_SUMMARY: '采购汇总',
  INVENTORY_STOCK_FLOW: '库存收发存',
  PRODUCTION_EXECUTION: '生产执行',
  COST_COLLECTION: '成本归集',
  SETTLEMENT_SUMMARY: '往来结算',
  EXCEPTIONS: '异常清单',
}

export const periodCloseReportCodes = Object.keys(periodCloseReportLabels) as BusinessPeriodCloseReportCode[]

export function periodCloseStatusLabel(status?: string | null): string {
  return status ? closeStatusLabels[status as BusinessPeriodCloseStatus] ?? status : '-'
}

export function businessPeriodStatusLabel(status?: string | null): string {
  return status ? periodStatusLabels[status] ?? status : '-'
}

export function periodCloseAuditActionLabel(action?: string | null): string {
  return action ? auditActionLabels[action] ?? action : '-'
}

export function periodCloseDomainLabel(domain?: string | null): string {
  return domain ? domainLabels[domain as BusinessPeriodCloseCheckDomain] ?? domain : '-'
}

export function periodCloseSeverityLabel(severity?: string | null): string {
  return severity ? severityLabels[severity as BusinessPeriodCloseCheckSeverity] ?? severity : '-'
}

export function periodCloseStatusTagType(status?: string | null): 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'CLOSED' || status === 'READY') {
    return 'success'
  }
  if (status === 'PENDING_CHECK' || status === 'REOPENED') {
    return 'warning'
  }
  if (status === 'BLOCKED') {
    return 'danger'
  }
  return 'info'
}

export function periodCloseSeverityTagType(severity?: string | null): 'success' | 'info' | 'warning' | 'danger' {
  if (severity === 'BLOCKING') {
    return 'danger'
  }
  if (severity === 'WARNING') {
    return 'warning'
  }
  return 'info'
}

export function formatPeriodCloseAmount(value: unknown, restrictedReason?: string | null): string {
  if (restrictedReason) {
    return restrictedReason
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return formatDecimalString(value, 2)
}

export function formatPeriodCloseQuantity(value: unknown, restrictedReason?: string | null): string {
  if (restrictedReason) {
    return restrictedReason
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return formatDecimalString(value, 6, true)
}

export function formatPeriodCloseRawDecimal(value: unknown, restrictedReason?: string | null): string {
  if (restrictedReason) {
    return restrictedReason
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

export function formatPeriodCloseDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function restrictedMoneyReason(source: { amountVisible?: boolean | null; restrictedReason?: string | null } | null | undefined): string | null {
  if (!source) {
    return null
  }
  return source.amountVisible === false ? source.restrictedReason || periodCloseMessages.amountForbidden : null
}

export function restrictedSourceReason(source: { sourceVisible?: boolean | null; restrictedReason?: string | null } | null | undefined): string | null {
  if (!source) {
    return null
  }
  return source.sourceVisible === false ? source.restrictedReason || periodCloseMessages.sourceRestricted : null
}

export function periodCloseAllowed(source: { allowedActions?: string[] } | null | undefined, action: string): boolean {
  return Boolean(source?.allowedActions?.includes(action))
}

export function canTracePeriodCloseSource(source: { sourceVisible?: boolean | null; sourceRoute?: BusinessPeriodCloseSourceRoute | null } | null | undefined): boolean {
  return Boolean(source?.sourceVisible !== false && sourceRouteLocation(source?.sourceRoute, '') !== null)
}

export function periodCloseActionDisabledReason(
  source: { allowedActions?: string[]; actionDisabledReasons?: Record<string, string> } | null | undefined,
  action: string,
): string {
  if (!source?.allowedActions?.includes(action)) {
    return source?.actionDisabledReasons?.[action] ?? ''
  }
  return source.actionDisabledReasons?.[action] ?? ''
}

export function periodCloseErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '业务月结操作失败，请稍后重试'
}

export function sourceRouteLocation(sourceRoute: BusinessPeriodCloseSourceRoute | null | undefined, returnTo: string): RouteLocationRaw | null {
  if (!sourceRoute) {
    return null
  }
  if (sourceRoute.name) {
    return {
      name: sourceRoute.name,
      params: sourceRoute.params,
      query: queryWithReturnTo(sourceRoute.query ?? {}, returnTo),
    }
  }
  if (sourceRoute.path) {
    return {
      path: sourceRoute.path,
      query: queryWithReturnTo(sourceRoute.query ?? {}, returnTo),
    }
  }
  return null
}

export function idText(value: ResourceId | null | undefined): string {
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

function formatDecimalString(value: unknown, scale: number, trimTrailingZeros = false): string {
  const normalized = normalizeDecimal(value)
  if (!normalized) {
    return '-'
  }
  const [integerPart, fraction = ''] = normalized.unsigned.split('.')
  const firstDigits = fraction.slice(0, scale).padEnd(scale, '0')
  const roundDigitChar = fraction.charAt(scale)
  const roundDigit = roundDigitChar ? roundDigitChar.charCodeAt(0) - 48 : 0
  let scaled = BigInt(integerPart || '0') * 10n ** BigInt(scale) + BigInt(firstDigits || '0')
  if (roundDigit >= 5) {
    scaled += 1n
  }
  const divisor = 10n ** BigInt(scale)
  const integer = scaled / divisor
  let decimal = String(scaled % divisor).padStart(scale, '0')
  if (trimTrailingZeros) {
    decimal = decimal.replace(/0+$/, '')
  }
  return `${normalized.sign}${integer}${decimal ? `.${decimal}` : ''}`
}

function normalizeDecimal(value: unknown): { sign: string; unsigned: string } | null {
  const raw = String(value).trim()
  const match = raw.match(/^([+-]?)(\d+)(?:\.(\d+))?$/)
  if (!match) {
    return null
  }
  const sign = match[1] === '-' ? '-' : ''
  const integer = match[2].replace(/^0+(?=\d)/, '') || '0'
  const fraction = match[3] ?? ''
  return { sign, unsigned: `${integer}${fraction ? `.${fraction}` : ''}` }
}
