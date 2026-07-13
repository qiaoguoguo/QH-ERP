import type { BomStatus, ResourceId } from '../../../shared/api/bomApi'

export type BomEffectiveState = 'CURRENT' | 'FUTURE' | 'EXPIRED' | 'UNPUBLISHED' | 'DISABLED'

export interface BomLineDraft {
  lineNo: number
  childMaterialId: ResourceId | ''
  businessUnitId: ResourceId | ''
  businessQuantity: string
  lossRate: string
  remark: string
}

const bomStatusLabels: Record<BomStatus, string> = {
  DRAFT: '草稿',
  ENABLED: '已发布',
  DISABLED: '停用',
}

const bomStatusTagTypes: Record<BomStatus, 'info' | 'success' | 'warning'> = {
  DRAFT: 'info',
  ENABLED: 'success',
  DISABLED: 'warning',
}

const bomEffectiveStateLabels: Record<BomEffectiveState, string> = {
  CURRENT: '当前有效',
  FUTURE: '未来生效',
  EXPIRED: '历史失效',
  UNPUBLISHED: '草稿未发布',
  DISABLED: '已停用',
}

const bomEffectiveStateTagTypes: Record<BomEffectiveState, 'info' | 'success' | 'warning' | 'danger'> = {
  CURRENT: 'success',
  FUTURE: 'warning',
  EXPIRED: 'info',
  UNPUBLISHED: 'info',
  DISABLED: 'danger',
}

export function bomStatusLabel(status: BomStatus): string {
  return bomStatusLabels[status]
}

export function bomStatusTagType(status: BomStatus): 'info' | 'success' | 'warning' {
  return bomStatusTagTypes[status]
}

export function todayText(): string {
  const today = new Date()
  const year = today.getFullYear()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function bomEffectiveState(
  status: BomStatus,
  effectiveFrom?: string | null,
  effectiveTo?: string | null,
  referenceDate = todayText(),
): BomEffectiveState {
  if (status === 'DRAFT') {
    return 'UNPUBLISHED'
  }
  if (status === 'DISABLED') {
    return 'DISABLED'
  }

  const date = referenceDate.slice(0, 10)
  const from = effectiveFrom?.slice(0, 10)
  const to = effectiveTo?.slice(0, 10)

  if (from && date < from) {
    return 'FUTURE'
  }
  if (to && date > to) {
    return 'EXPIRED'
  }
  return 'CURRENT'
}

export function bomEffectiveStateLabel(state: BomEffectiveState): string {
  return bomEffectiveStateLabels[state]
}

export function bomEffectiveStateTagType(state: BomEffectiveState): 'info' | 'success' | 'warning' | 'danger' {
  return bomEffectiveStateTagTypes[state]
}

export function positiveNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const numberValue = Number(value)
  return Number.isFinite(numberValue) && numberValue > 0 ? numberValue : null
}

export function lossRateNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') {
    return 0
  }
  const numberValue = Number(value)
  return Number.isFinite(numberValue) && numberValue >= 0 && numberValue < 1 ? numberValue : null
}

export function nextLineNo(lines: Array<{ lineNo: number }>): number {
  const maxLineNo = lines.reduce((max, line) => Math.max(max, Number(line.lineNo) || 0), 0)
  return maxLineNo + 10
}

export function newBomLine(lineNo = 10): BomLineDraft {
  return {
    lineNo,
    childMaterialId: '',
    businessUnitId: '',
    businessQuantity: '',
    lossRate: '0',
    remark: '',
  }
}

export function positiveDecimalString(value: unknown): string | null {
  const text = String(value ?? '').trim()
  if (!text) {
    return null
  }
  const numberValue = Number(text)
  return Number.isFinite(numberValue) && numberValue > 0 ? text : null
}

export function lossRateDecimalString(value: unknown): string | null {
  const text = String(value ?? '').trim()
  if (!text) {
    return '0'
  }
  const numberValue = Number(text)
  return Number.isFinite(numberValue) && numberValue >= 0 && numberValue < 1 ? text : null
}
