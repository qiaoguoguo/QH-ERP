import type {
  MasterDataStatus,
  MaterialSourceType,
  MaterialTrackingMethod,
  MaterialType,
} from '../../../shared/api/masterDataApi'

const masterStatusLabels: Record<MasterDataStatus, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
}

const materialTypeLabels: Record<MaterialType, string> = {
  RAW_MATERIAL: '原材料',
  SEMI_FINISHED: '半成品',
  FINISHED_GOOD: '成品',
  AUXILIARY: '辅料',
}

const sourceTypeLabels: Record<MaterialSourceType, string> = {
  PURCHASED: '外购',
  SELF_MADE: '自制',
  OUTSOURCED: '委外',
}

const trackingMethodLabels: Record<MaterialTrackingMethod, string> = {
  NONE: '不追踪',
  BATCH: '批次管理',
  SERIAL: '序列号管理',
}

export function masterStatusLabel(status: MasterDataStatus): string {
  return masterStatusLabels[status]
}

export function materialTypeLabel(materialType: MaterialType): string {
  return materialTypeLabels[materialType]
}

export function sourceTypeLabel(sourceType: MaterialSourceType): string {
  return sourceTypeLabels[sourceType]
}

export function trackingMethodLabel(trackingMethod?: MaterialTrackingMethod | null): string {
  return trackingMethod ? trackingMethodLabels[trackingMethod] : '不追踪'
}
