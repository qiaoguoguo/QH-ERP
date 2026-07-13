import type { BomStatus, ResourceId } from '../../../shared/api/bomApi'

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

export function bomStatusLabel(status: BomStatus): string {
  return bomStatusLabels[status]
}

export function bomStatusTagType(status: BomStatus): 'info' | 'success' | 'warning' {
  return bomStatusTagTypes[status]
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
