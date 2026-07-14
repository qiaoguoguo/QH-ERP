import type {
  InventoryAdjustmentDirection,
  InventoryDirection,
  InventoryDocumentStatus,
  InventoryDocumentType,
  InventoryMovementType,
  InventoryOwnershipType,
  InventoryValuationState,
  ResourceId,
} from '../../shared/api/inventoryApi'

export interface InventoryLineDraft {
  lineNo: number
  warehouseId: ResourceId | ''
  materialId: ResourceId | ''
  unitId: ResourceId | ''
  unitName: string
  adjustmentDirection: InventoryAdjustmentDirection | ''
  quantity: string
  remark: string
}

export interface InventoryQuantityValidationResult {
  value: number | null
  payloadValue: string | null
  message: string | null
}

const documentTypeLabels: Record<InventoryDocumentType, string> = {
  OPENING: '期初库存',
  ADJUSTMENT: '库存调整',
}

const documentStatusLabels: Record<InventoryDocumentStatus, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
}

const documentStatusTagTypes: Record<InventoryDocumentStatus, 'info' | 'success'> = {
  DRAFT: 'info',
  POSTED: 'success',
}

const movementTypeLabels: Record<InventoryMovementType, string> = {
  OPENING: '期初',
  ADJUSTMENT_INCREASE: '调增',
  ADJUSTMENT_DECREASE: '调减',
  PRODUCTION_ISSUE: '生产领料',
  PRODUCTION_RECEIPT: '完工入库',
  PURCHASE_RECEIPT: '采购入库',
  SALES_SHIPMENT: '销售出库',
  WAREHOUSE_TRANSFER_OUT: '调拨出库',
  WAREHOUSE_TRANSFER_IN: '调拨入库',
  OWNERSHIP_CONVERSION_OUT: '所有权转出',
  OWNERSHIP_CONVERSION_IN: '所有权转入',
  STOCKTAKE_GAIN: '盘盈',
  STOCKTAKE_LOSS: '盘亏',
  VALUATION_ADJUSTMENT: '估值调整',
}

const directionLabels: Record<InventoryDirection, string> = {
  IN: '入库',
  OUT: '出库',
}

const directionTagTypes: Record<InventoryDirection, 'success' | 'warning'> = {
  IN: 'success',
  OUT: 'warning',
}

const adjustmentDirectionLabels: Record<InventoryAdjustmentDirection, string> = {
  INCREASE: '调增',
  DECREASE: '调减',
}

export function documentTypeLabel(type: InventoryDocumentType): string {
  return documentTypeLabels[type]
}

export function documentStatusLabel(status: InventoryDocumentStatus): string {
  return documentStatusLabels[status]
}

export function documentStatusTagType(status: InventoryDocumentStatus): 'info' | 'success' {
  return documentStatusTagTypes[status]
}

export function movementTypeLabel(type: InventoryMovementType): string {
  return movementTypeLabels[type]
}

export function directionLabel(direction: InventoryDirection): string {
  return directionLabels[direction]
}

export function directionTagType(direction: InventoryDirection): 'success' | 'warning' {
  return directionTagTypes[direction]
}

export function adjustmentDirectionLabel(direction?: InventoryAdjustmentDirection | null): string {
  return direction ? adjustmentDirectionLabels[direction] : '-'
}

export function validateInventoryQuantity(value: unknown): InventoryQuantityValidationResult {
  if (value === null || value === undefined || value === '') {
    return { value: null, payloadValue: null, message: '数量不能为空' }
  }

  const normalizedValue = String(value).trim()
  if (!normalizedValue) {
    return { value: null, payloadValue: null, message: '数量不能为空' }
  }

  if (normalizedValue.startsWith('-')) {
    return { value: null, payloadValue: null, message: '数量必须大于 0' }
  }
  if (/[eE]/.test(normalizedValue)) {
    return { value: null, payloadValue: null, message: '数量仅支持普通十进制正数' }
  }
  if (!/^\d+(?:\.\d+)?$/.test(normalizedValue)) {
    return { value: null, payloadValue: null, message: '数量仅支持普通十进制正数' }
  }

  const [integerPart, decimalPart = ''] = normalizedValue.split('.')
  if (integerPart.length > 12) {
    return { value: null, payloadValue: null, message: '数量整数部分最多 12 位' }
  }
  if (decimalPart.length > 6) {
    return { value: null, payloadValue: null, message: '数量最多 6 位小数' }
  }

  const numberValue = Number(normalizedValue)
  if (!Number.isFinite(numberValue) || numberValue <= 0) {
    return { value: null, payloadValue: null, message: '数量必须大于 0' }
  }

  return { value: numberValue, payloadValue: normalizedValue, message: null }
}

export function positiveQuantity(value: unknown): number | null {
  return validateInventoryQuantity(value).value
}

export function formatQuantity(value: unknown): string {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) {
    return '-'
  }
  return numberValue.toFixed(6).replace(/\.?0+$/, '')
}

export function formatInventoryAmount(value: unknown, fractionDigits = 2): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) {
    return '-'
  }
  return numberValue.toLocaleString('zh-CN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })
}

export function ownershipTypeLabel(type?: InventoryOwnershipType | string | null): string {
  const labels: Record<string, string> = {
    PUBLIC: '公共库存',
    PROJECT: '项目库存',
  }
  return type ? labels[type] ?? type : '-'
}

export function valuationStateLabel(state?: InventoryValuationState | string | null): string {
  const labels: Record<string, string> = {
    VALUED: '已估值',
    PROJECT_ACTUAL_LAYER: '项目实际成本层',
    LEGACY_UNVALUED: '历史未估值',
    NON_VALUED: '无需计价',
    CURRENT_AVERAGE_PROVISIONAL: '当前平均暂估',
    MANUAL_PROVISIONAL: '手工暂估',
    MANUAL_PROVISIONAL_REQUIRED: '需录入暂估',
    ABNORMAL: '异常不平衡',
  }
  return state ? labels[state] ?? state : '-'
}

export function inventoryActionLabel(action: string): string {
  const labels: Record<string, string> = {
    UPDATE: '编辑',
    POST: '过账',
    CANCEL: '取消',
    SUBMIT_APPROVAL: '提交审批',
    WITHDRAW: '撤回',
    START: '开始盘点',
    UPDATE_LINES: '保存实盘',
    RECONCILE: '确认差异',
    COMPLETE_ZERO_VARIANCE: '结束零差异盘点',
  }
  return labels[action] ?? action
}

export function validateInventoryMoney(value: unknown, decimalPlaces: number): { payloadValue: string | null; message: string | null } {
  if (value === null || value === undefined || value === '') {
    return { payloadValue: null, message: '金额不能为空' }
  }
  const normalizedValue = String(value).trim()
  if (!normalizedValue) {
    return { payloadValue: null, message: '金额不能为空' }
  }
  if (normalizedValue.startsWith('-')) {
    return { payloadValue: null, message: '金额不能为负数' }
  }
  if (/[eE]/.test(normalizedValue) || !/^\d+(?:\.\d+)?$/.test(normalizedValue)) {
    return { payloadValue: null, message: '金额仅支持普通十进制数' }
  }
  const [, decimalPart = ''] = normalizedValue.split('.')
  if (decimalPart.length > decimalPlaces) {
    return { payloadValue: null, message: `金额最多 ${decimalPlaces} 位小数` }
  }
  return { payloadValue: normalizedValue, message: null }
}

export function nextLineNo(lines: Array<{ lineNo: number }>): number {
  const maxLineNo = lines.reduce((max, line) => Math.max(max, Number(line.lineNo) || 0), 0)
  return maxLineNo + 10
}

export function newInventoryLine(lineNo = 10): InventoryLineDraft {
  return {
    lineNo,
    warehouseId: '',
    materialId: '',
    unitId: '',
    unitName: '',
    adjustmentDirection: '',
    quantity: '',
    remark: '',
  }
}
