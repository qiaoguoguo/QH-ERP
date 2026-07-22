import type { QualityInspectionStatus } from '../../shared/api/qualityInventoryStatusApi'
import { inventorySourceTypeLabel } from '../inventory/inventoryPageHelpers'

const inspectionStatusLabels: Record<QualityInspectionStatus | string, string> = {
  PENDING: '待检验',
  COMPLETED: '已完成',
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
  serverName?: unknown,
): string {
  const codeText = normalizedDisplayText(code)
  const serverText = normalizedDisplayText(serverName)
  if (serverText && serverText !== codeText && hasChineseText(serverText)) {
    return serverText
  }
  if (!codeText) {
    return '-'
  }
  const knownLabel = labels[codeText]
  return knownLabel || unknownLabel
}

export function qualityInspectionStatusLabel(status?: string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, inspectionStatusLabels, '未知状态', statusName)
}

export function qualityInspectionSourceTypeLabel(sourceType?: string | null, sourceTypeName?: string | null): string {
  return inventorySourceTypeLabel(sourceType, sourceTypeName)
}
