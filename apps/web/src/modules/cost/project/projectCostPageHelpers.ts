import type { RouteLocationRaw } from 'vue-router'
import type {
  ProjectCostAdjustmentStatus,
  ProjectCostAdjustmentType,
  ProjectCostAdjustmentDirection,
  ProjectCostCategory,
  ProjectCostCalculationStatus,
  ProjectCostCompletenessStatus,
  ProjectCostFreshnessStatus,
  ProjectCostSourceRoute,
  ProjectCostSourceStatus,
  ProjectCostStage,
  ProjectCostVarianceSeverity,
  ProjectCostVarianceStatus,
  ResourceId,
} from '../../../shared/api/projectCostApi'
import { queryWithReturnTo } from '../../../shared/navigation/navigationReturn'

export const projectCostMessages = {
  amountForbidden: '无权查看成本金额',
  sourceRestricted: '来源权限受限，仅显示脱敏摘要',
  incompleteMargin: '毛利不完整：存在暂估、未定价、在制或阻断差异',
  sourceChanged: '来源已变化，请重算后再确认。',
}

const calculationStatusLabels: Record<ProjectCostCalculationStatus, string> = {
  DRAFT: '草稿',
  CALCULATED: '已计算',
  CONFIRMED: '已确认',
  CANCELLED: '已取消',
}

const completenessLabels: Record<ProjectCostCompletenessStatus, string> = {
  COMPLETE: '完整',
  INCOMPLETE: '不完整',
}

const freshnessLabels: Record<ProjectCostFreshnessStatus, string> = {
  CURRENT: '当前有效',
  STALE: '来源已变化',
}

const categoryLabels: Record<ProjectCostCategory, string> = {
  MATERIAL: '材料',
  LABOR: '人工',
  OUTSOURCING: '外协',
  MANUFACTURING_OVERHEAD: '制造费用',
  PROJECT_EXPENSE: '项目费用',
  ADJUSTMENT: '调整',
}

const stageLabels: Record<ProjectCostStage, string> = {
  WIP: '在制',
  FINISHED: '完工',
  DELIVERED: '已交付',
  DIRECT_PROJECT: '直接项目',
}

const sourceStatusLabels: Record<ProjectCostSourceStatus, string> = {
  ACTUAL: '实际',
  PROVISIONAL: '暂估',
  UNPRICED: '未定价',
  ADJUSTED: '已调整',
  RESTRICTED: '来源受限',
  EXCLUDED: '已排除',
}

const varianceSeverityLabels: Record<ProjectCostVarianceSeverity, string> = {
  INFO: '提示',
  WARNING: '警告',
  BLOCKING: '阻断',
}

const varianceStatusLabels: Record<ProjectCostVarianceStatus, string> = {
  OPEN: '待处理',
  RESOLVED: '已解决',
  SUPERSEDED: '已替代',
}

const adjustmentStatusLabels: Record<ProjectCostAdjustmentStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  CONFIRMED: '已确认',
  REJECTED: '已拒绝',
  CANCELLED: '已取消',
}

const adjustmentTypeLabels: Record<ProjectCostAdjustmentType, string> = {
  PROJECT_ADJUSTMENT: '项目成本调整',
  PUBLIC_EXPENSE_ALLOCATION: '公共费用分配',
  VARIANCE_SETTLEMENT: '差异结算',
}

const adjustmentDirectionLabels: Record<ProjectCostAdjustmentDirection, string> = {
  INCREASE: '增加',
  DECREASE: '减少',
}

const projectStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '执行中',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const varianceTypeLabels: Record<string, string> = {
  OUTSOURCING_ESTIMATE_ACTUAL: '外协暂估差异',
  UNPRICED_LABOR: '人工未定价',
  UNVALUED_MATERIAL: '材料未估值',
  SOURCE_CHANGED: '来源变化',
  WIP_OPEN: '在制未完结',
}

const sourceTypeLabels: Record<string, string> = {
  INVENTORY_ISSUE: '库存领料',
  INVENTORY_RETURN: '库存退料',
  INVENTORY_SUPPLEMENT: '库存补料',
  PRODUCTION_REPORT: '生产报工',
  OUTSOURCING_RECEIPT: '外协收货',
  FINANCE_EXPENSE: '费用单',
  SALES_SHIPMENT: '销售出库',
  SALES_RETURN: '销售退货',
  ADJUSTMENT: '成本调整',
}

export const projectCostSourceTypeOptions = Object.entries(sourceTypeLabels).map(([value, label]) => ({ value, label }))

function labelFromMap(value: string | null | undefined, labels: Record<string, string>, unknownLabel: string): string {
  if (!value) {
    return '-'
  }
  return labels[value] ?? unknownLabel
}

export function projectCostProjectStatusLabel(status?: string | null): string {
  return labelFromMap(status, projectStatusLabels, '未知项目状态')
}

export function projectCostCalculationStatusLabel(status?: string | null): string {
  return labelFromMap(status, calculationStatusLabels, '未知状态')
}

export function projectCostCompletenessLabel(status?: string | null): string {
  return labelFromMap(status, completenessLabels, '未知完整性')
}

export function projectCostFreshnessLabel(status?: string | null): string {
  return labelFromMap(status, freshnessLabels, '未知当前性')
}

export function projectCostCategoryLabel(category?: string | null): string {
  return labelFromMap(category, categoryLabels, '未知分类')
}

export function projectCostStageLabel(stage?: string | null): string {
  return labelFromMap(stage, stageLabels, '未知阶段')
}

export function projectCostSourceStatusLabel(status?: string | null): string {
  return labelFromMap(status, sourceStatusLabels, '未知来源状态')
}

export function projectCostVarianceSeverityLabel(severity?: string | null): string {
  return labelFromMap(severity, varianceSeverityLabels, '未知严重级别')
}

export function projectCostVarianceStatusLabel(status?: string | null): string {
  return labelFromMap(status, varianceStatusLabels, '未知差异状态')
}

export function projectCostAdjustmentStatusLabel(status?: string | null): string {
  return labelFromMap(status, adjustmentStatusLabels, '未知调整状态')
}

export function projectCostAdjustmentTypeLabel(type?: string | null): string {
  return labelFromMap(type, adjustmentTypeLabels, '未知调整类型')
}

export function projectCostVarianceTypeLabel(type?: string | null): string {
  return labelFromMap(type, varianceTypeLabels, '未知差异类型')
}

export function projectCostSourceTypeLabel(type?: string | null): string {
  return labelFromMap(type, sourceTypeLabels, '未知来源')
}

export function projectCostAdjustmentDirectionLabel(direction?: string | null): string {
  return labelFromMap(direction, adjustmentDirectionLabels, '未知方向')
}

export function tagTypeForStatus(status?: string | null): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'CONFIRMED' || status === 'CURRENT' || status === 'COMPLETE' || status === 'ACTUAL' || status === 'RESOLVED') {
    return 'success'
  }
  if (status === 'CALCULATED' || status === 'SUBMITTED' || status === 'WARNING' || status === 'PROVISIONAL' || status === 'UNPRICED') {
    return 'warning'
  }
  if (status === 'CANCELLED' || status === 'REJECTED' || status === 'BLOCKING' || status === 'RESTRICTED' || status === 'OPEN') {
    return 'danger'
  }
  return 'info'
}

export function projectCostErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '项目成本操作失败，请稍后重试'
}

export function formatProjectCostAmount(value: unknown, restrictedReason?: string | null): string {
  if (restrictedReason) {
    return restrictedReason
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return formatDecimalString(value, 2)
}

export function formatProjectCostQuantity(value: unknown, restrictedReason?: string | null): string {
  if (restrictedReason) {
    return restrictedReason
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return formatDecimalString(value, 6, true)
}

export function formatProjectCostRate(value: unknown, restrictedReason?: string | null): string {
  if (restrictedReason) {
    return restrictedReason
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const normalized = normalizeDecimal(value)
  if (!normalized) {
    return '-'
  }
  const [integer, fraction = ''] = normalized.unsigned.split('.')
  const basisPoints = BigInt(integer) * 10000n + BigInt(`${fraction}0000`.slice(0, 4))
  const percentCents = basisPoints
  const valueText = `${percentCents / 100n}.${String(percentCents % 100n).padStart(2, '0')}`
  return `${normalized.sign}${valueText}%`
}

export function formatProjectCostDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function restrictedMoneyReason(source: { amountVisible?: boolean | null; restrictedReason?: string | null } | null | undefined): string | null {
  if (!source) {
    return null
  }
  return source.amountVisible === false ? source.restrictedReason || projectCostMessages.amountForbidden : null
}

export function restrictedSourceReason(source: { sourceVisible?: boolean | null; restrictedReason?: string | null } | null | undefined): string | null {
  if (!source) {
    return null
  }
  return source.sourceVisible === false ? source.restrictedReason || projectCostMessages.sourceRestricted : null
}

export function projectCostAllowed(source: { allowedActions?: string[] } | null | undefined, action: string): boolean {
  return Boolean(source?.allowedActions?.includes(action))
}

export function projectCostActionDisabledReason(
  source: { allowedActions?: string[]; actionDisabledReasons?: Record<string, string> } | null | undefined,
  action: string,
): string {
  if (!source?.allowedActions?.includes(action)) {
    return source?.actionDisabledReasons?.[action] ?? ''
  }
  return source.actionDisabledReasons?.[action] ?? ''
}

export function sourceRouteLocation(sourceRoute: ProjectCostSourceRoute | null | undefined, returnTo: string): RouteLocationRaw | null {
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
