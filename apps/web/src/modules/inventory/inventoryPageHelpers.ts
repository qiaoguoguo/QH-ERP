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
import { createUnknownStatusDisplay, type StatusTone } from '../../shared/status/statusDisplay'

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

const controlledDocumentStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  COUNTING: '盘点中',
  RECONCILED: '已确认差异',
  SUBMITTED: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  POSTED: '已过账',
  CANCELLED: '已取消',
}

const valuationAdjustmentTypeLabels: Record<string, string> = {
  LEGACY_OPENING: '历史期初估值',
  PROVISIONAL_REVALUATION: '暂估重估',
}

const qualityStatusLabels: Record<string, string> = {
  PENDING_INSPECTION: '待检',
  QUALIFIED: '合格',
  REJECTED: '不合格',
  FROZEN: '冻结',
}

const qualityStatusTagTypes: Record<string, StatusTone> = {
  PENDING_INSPECTION: 'warning',
  QUALIFIED: 'success',
  REJECTED: 'danger',
  FROZEN: 'info',
}

const inventorySourceTypeLabels: Record<string, string> = {
  INVENTORY_DOCUMENT: '库存单据',
  WAREHOUSE_TRANSFER: '仓库调拨',
  OWNERSHIP_CONVERSION: '所有权转换',
  STOCKTAKE: '库存盘点',
  VALUATION_ADJUSTMENT: '估值调整',
  PURCHASE_RECEIPT: '采购入库',
  PRODUCTION_RECEIPT: '完工入库',
  PRODUCTION_COMPLETION: '生产完工',
  SALES_RETURN: '销售退货',
  PRODUCTION_RETURN: '生产退料',
  PRODUCTION_MATERIAL_ISSUE: '生产领料',
  SALES_SHIPMENT: '销售出库',
  SALES_ORDER: '销售订单',
  PRODUCTION_WORK_ORDER: '生产工单',
  PURCHASE_ORDER: '采购订单',
}

const reservationTypeLabels: Record<string, string> = {
  RESERVATION: '预约',
  OCCUPATION: '占用',
}

const reservationStatusLabels: Record<string, string> = {
  ACTIVE: '有效',
  RELEASED: '已释放',
  CONSUMED: '已消耗',
  CANCELLED: '已取消',
}

const approvalStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  CANCELLED: '已取消',
}

const trackingMethodLabels: Record<string, string> = {
  NONE: '不追踪',
  BATCH: '批次管理',
  SERIAL: '序列号管理',
}

const stockStatusLabels: Record<string, string> = {
  IN_STOCK: '在库',
  RESERVED: '已预约',
  OCCUPIED: '已占用',
  OUTBOUND: '已出库',
  CANCELLED: '已取消',
}

const traceNodeTypeLabels: Record<string, string> = {
  SOURCE: '来源',
  QUALITY_EVENT: '质量事件',
  OUTBOUND: '出库',
  RETURN: '退回',
  MOVEMENT: '库存流水',
  RESERVATION: '预留/占用',
  RESTRICTED_SOURCE: '受限来源',
}

const costLayerStatusLabels: Record<string, string> = {
  OPEN: '开放',
  AVAILABLE: '可用',
  CONSUMED: '已耗用',
  CLOSED: '已关闭',
}

const valuationMethodLabels: Record<string, string> = {
  MOVING_AVERAGE: '移动加权平均',
  PROJECT_ACTUAL_LAYER: '项目实际成本层',
  LEGACY_UNVALUED: '历史未估值',
  NON_VALUED: '无需计价',
  CURRENT_AVERAGE_PROVISIONAL: '当前平均暂估',
  MANUAL_PROVISIONAL: '手工暂估',
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
  SALES_RETURN_IN: '销售退货入库',
  PURCHASE_RETURN_OUT: '采购退货出库',
  PRODUCTION_MATERIAL_RETURN_IN: '生产退料入库',
  PRODUCTION_MATERIAL_SUPPLEMENT_OUT: '生产补料出库',
  QUALITY_STATUS_TRANSFER: '质量状态转移',
  BUSINESS_REVERSAL: '业务反向冲销',
  STOCKTAKE_VARIANCE_IN: '盘点差异入库',
  STOCKTAKE_VARIANCE_OUT: '盘点差异出库',
  OUTSOURCING_ISSUE: '外协发料',
  OUTSOURCING_RECEIPT: '外协收货',
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
  const codeText = normalizedDisplayText(code)
  const serverText = normalizedDisplayText(serverName)
  if (serverText && serverText !== codeText && hasChineseText(serverText)) {
    return serverText
  }
  if (!codeText && !serverText) {
    return '-'
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

export function controlledDocumentStatusLabel(status?: string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, controlledDocumentStatusLabels, '未知状态', {
    domain: '库存',
    field: '受控单据状态',
  }, statusName)
}

export function inventoryApprovalStatusLabel(status?: string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, approvalStatusLabels, '未知状态', {
    domain: '库存',
    field: '审批状态',
  }, statusName)
}

export function valuationAdjustmentTypeLabel(type?: string | null, typeName?: string | null): string {
  return knownOrFallbackLabel(type, valuationAdjustmentTypeLabels, '未知类型', {
    domain: '库存',
    field: '估值调整类型',
  }, typeName)
}

export function documentStatusTagType(status: InventoryDocumentStatus): 'info' | 'success' {
  return documentStatusTagTypes[status]
}

export function movementTypeLabel(type: InventoryMovementType | string | null | undefined): string {
  return knownOrFallbackLabel(type, movementTypeLabels, '未知类型', {
    domain: '库存',
    field: '库存流水类型',
  })
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

export function qualityStatusLabel(status?: string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, qualityStatusLabels, '未知状态', {
    domain: '库存',
    field: '质量状态',
  }, statusName)
}

export function qualityStatusTagType(status?: string | null): StatusTone {
  const codeText = normalizedDisplayText(status)
  return qualityStatusTagTypes[codeText] ?? 'warning'
}

export function inventorySourceTypeLabel(type?: string | null, typeName?: string | null): string {
  return knownOrFallbackLabel(type, inventorySourceTypeLabels, '未知类型', {
    domain: '库存',
    field: '来源类型',
  }, typeName)
}

export function reservationTypeLabel(type?: string | null, typeName?: string | null): string {
  return knownOrFallbackLabel(type, reservationTypeLabels, '未知类型', {
    domain: '库存',
    field: '预留占用类型',
  }, typeName)
}

export function reservationStatusLabel(status?: string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, reservationStatusLabels, '未知状态', {
    domain: '库存',
    field: '预留占用状态',
  }, statusName)
}

export function inventoryTrackingMethodLabel(method?: string | null, methodName?: string | null): string {
  return knownOrFallbackLabel(method, trackingMethodLabels, '未知类型', {
    domain: '库存',
    field: '追踪方式',
  }, methodName)
}

export function inventoryStockStatusLabel(status?: string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, stockStatusLabels, '未知状态', {
    domain: '库存',
    field: '实物库存状态',
  }, statusName)
}

export function inventoryTraceNodeTypeLabel(type?: string | null, typeName?: string | null): string {
  return knownOrFallbackLabel(type, traceNodeTypeLabels, '未知类型', {
    domain: '库存',
    field: '追溯节点',
  }, typeName)
}

export function inventoryCostLayerStatusLabel(status?: string | null, statusName?: string | null): string {
  return knownOrFallbackLabel(status, costLayerStatusLabels, '未知状态', {
    domain: '库存',
    field: '成本层状态',
  }, statusName)
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

export function formatInventoryAmountImpact(
  amountImpactSummary: unknown,
  keyInfoSummary: unknown,
  costVisible?: boolean | null,
): string {
  const summary = amountImpactSummary ?? keyInfoSummary
  if (summary === null || summary === undefined || summary === '') {
    return '-'
  }
  if (typeof summary === 'string') {
    return costVisible === false ? '金额受限' : summary
  }
  if (typeof summary !== 'object') {
    return String(summary)
  }

  const record = summary as Record<string, unknown>
  if (Object.keys(record).length === 0) {
    return '未形成金额影响'
  }
  const amount = firstPresent(record, [
    'amount',
    'amountImpact',
    'inventoryAmount',
    'movementAmount',
    'adjustmentAmount',
    'increaseAmount',
    'decreaseAmount',
  ])
  if (amount === undefined || amount === null || amount === '') {
    return '-'
  }
  if (costVisible === false) {
    return '金额受限'
  }
  return `${amountDirectionLabel(record)} ${formatInventoryAmount(amount)}`.trim()
}

function firstPresent(record: Record<string, unknown>, keys: string[]): unknown {
  for (const key of keys) {
    if (record[key] !== undefined && record[key] !== null && record[key] !== '') {
      return record[key]
    }
  }
  return undefined
}

function amountDirectionLabel(record: Record<string, unknown>): string {
  const direction = String(record.direction ?? record.amountDirection ?? '').toUpperCase()
  if (direction === 'INCREASE' || direction === 'IN') {
    return '调增'
  }
  if (direction === 'DECREASE' || direction === 'OUT') {
    return '调减'
  }
  if (record.increaseAmount !== undefined) {
    return '调增'
  }
  if (record.decreaseAmount !== undefined) {
    return '调减'
  }
  return '金额影响'
}

export function ownershipTypeLabel(type?: InventoryOwnershipType | string | null): string {
  const labels: Record<string, string> = {
    PUBLIC: '公共库存',
    PROJECT: '项目库存',
  }
  return knownOrFallbackLabel(type, labels, '未知所有权', {
    domain: '库存',
    field: '所有权',
  })
}

export function valuationStateLabel(state?: InventoryValuationState | string | null, stateName?: string | null): string {
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
  return knownOrFallbackLabel(state, labels, '未知估值状态', {
    domain: '库存',
    field: '估值状态',
  }, stateName)
}

export function valuationStateTagType(state?: InventoryValuationState | string | null): StatusTone {
  const stateText = normalizedDisplayText(state)
  if (stateText === 'VALUED') {
    return 'success'
  }
  if (['CURRENT_AVERAGE_PROVISIONAL', 'MANUAL_PROVISIONAL', 'MANUAL_PROVISIONAL_REQUIRED', 'ABNORMAL'].includes(stateText)) {
    return 'warning'
  }
  return 'info'
}

export function valuationMethodLabel(method?: string | null, methodName?: string | null): string {
  return knownOrFallbackLabel(method, valuationMethodLabels, '未知估值方法', {
    domain: '库存',
    field: '计价方法',
  }, methodName)
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
  return knownOrFallbackLabel(action, labels, '未知操作', {
    domain: '库存',
    field: '操作动作',
  })
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
