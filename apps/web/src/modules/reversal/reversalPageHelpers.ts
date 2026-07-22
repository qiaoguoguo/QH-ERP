const reversalDocumentStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
  CANCELLED: '已取消',
}

const reversalDocumentStatusTypes: Record<string, 'info' | 'success' | 'danger'> = {
  DRAFT: 'info',
  POSTED: 'success',
  CANCELLED: 'info',
}

const settlementSideLabels: Record<string, string> = {
  RECEIVABLE: '应收冲减',
  PAYABLE: '应付冲减',
}

const settlementAdjustmentTypeLabels: Record<string, string> = {
  RETURN_OFFSET: '退货冲抵',
  REFUND: '退款',
  PAYMENT_OFFSET: '付款冲抵',
}

const settlementAdjustmentSourceTypeLabels: Record<string, string> = {
  SALES_RETURN: '销售退货',
  PURCHASE_RETURN: '采购退货',
  RECEIPT: '收款',
  PAYMENT: '付款',
  SETTLEMENT_ADJUSTMENT: '结算调整',
}

const reversalSourceTypeLabels: Record<string, string> = {
  SALES_SHIPMENT: '销售出库来源',
  SALES_SHIPMENT_LINE: '销售出库行',
  SALES_RETURN: '销售退货',
  SALES_RETURN_LINE: '销售退货行',
  PURCHASE_RECEIPT: '采购入库来源',
  PURCHASE_RECEIPT_LINE: '采购入库行',
  PURCHASE_RETURN: '采购退货',
  PURCHASE_RETURN_LINE: '采购退货行',
  PRODUCTION_MATERIAL_ISSUE: '生产领料来源',
  PRODUCTION_MATERIAL_ISSUE_LINE: '生产领料行',
  PRODUCTION_WORK_ORDER: '生产工单',
  PRODUCTION_WORK_ORDER_MATERIAL: '工单用料行',
  PRODUCTION_MATERIAL_RETURN: '生产退料',
  PRODUCTION_MATERIAL_RETURN_LINE: '生产退料行',
  PRODUCTION_MATERIAL_SUPPLEMENT: '生产补料',
  PRODUCTION_MATERIAL_SUPPLEMENT_LINE: '生产补料行',
  INVENTORY_MOVEMENT: '库存流水',
  RECEIVABLE: '应收冲减',
  PAYABLE: '应付冲减',
  SETTLEMENT_ADJUSTMENT: '结算调整',
  RECEIPT: '收款',
  PAYMENT: '付款',
  COST_RECORD: '成本记录',
}

const reversalTraceDirectionLabels: Record<string, string> = {
  SOURCE_TO_REVERSE: '原单到反向单',
  REVERSE_TO_SOURCE: '反向单到原单',
}

const productionWorkOrderStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  RELEASED: '已下达',
  IN_PROGRESS: '生产中',
  COMPLETED: '已完工',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const targetSettlementStatusLabels: Record<string, string> = {
  CONFIRMED: '待收款',
  PARTIALLY_RECEIVED: '部分收款',
  RECEIVED: '已收清',
  PARTIALLY_PAID: '部分付款',
  PAID: '已付清',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const genericTraceStatusLabels: Record<string, string> = {
  ACTIVE: '已生效',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
  ...reversalDocumentStatusLabels,
}

function key(value?: string | null): string {
  return String(value ?? '').trim().toUpperCase()
}

export function reversalDocumentStatusLabel(status?: string | null): string {
  return reversalDocumentStatusLabels[key(status)] ?? '未知状态'
}

export function reversalDocumentStatusType(status?: string | null): 'info' | 'success' | 'danger' {
  return reversalDocumentStatusTypes[key(status)] ?? 'info'
}

export function settlementSideLabel(value?: string | null): string {
  return settlementSideLabels[key(value)] ?? '未知方向'
}

export function settlementAdjustmentTypeLabel(value?: string | null): string {
  return settlementAdjustmentTypeLabels[key(value)] ?? '未知调整类型'
}

export function settlementAdjustmentSourceTypeLabel(value?: string | null): string {
  return settlementAdjustmentSourceTypeLabels[key(value)] ?? '未知来源'
}

export function reversalSourceTypeLabel(sourceType?: string | null): string {
  return reversalSourceTypeLabels[key(sourceType)] ?? '未知来源'
}

export function reversalTraceDirectionLabel(direction?: string | null): string {
  return reversalTraceDirectionLabels[key(direction)] ?? '未知方向'
}

export function productionSourceStatusLabel(status?: string | null): string {
  return productionWorkOrderStatusLabels[key(status)] ?? reversalDocumentStatusLabels[key(status)] ?? genericTraceStatusLabels[key(status)] ?? '未知状态'
}

export function targetSettlementStatusLabel(status?: string | null): string {
  return targetSettlementStatusLabels[key(status)] ?? '未知状态'
}

export function reversalTraceStatusLabel(source: { sourceType?: string | null; status?: string | null }): string {
  const sourceType = key(source.sourceType)
  const status = key(source.status)
  if (!status) {
    return '-'
  }
  if (sourceType === 'PRODUCTION_WORK_ORDER' || sourceType === 'PRODUCTION_WORK_ORDER_MATERIAL') {
    return productionWorkOrderStatusLabels[status] ?? '未知状态'
  }
  if (sourceType.startsWith('PRODUCTION_MATERIAL_')) {
    return reversalDocumentStatusLabels[status] ?? genericTraceStatusLabels[status] ?? '未知状态'
  }
  if (sourceType.startsWith('SALES_') || sourceType.startsWith('PURCHASE_')) {
    return reversalDocumentStatusLabels[status] ?? genericTraceStatusLabels[status] ?? '未知状态'
  }
  if (sourceType === 'SETTLEMENT_ADJUSTMENT' || sourceType === 'RECEIPT' || sourceType === 'PAYMENT') {
    return reversalDocumentStatusLabels[status] ?? targetSettlementStatusLabels[status] ?? genericTraceStatusLabels[status] ?? '未知状态'
  }
  return genericTraceStatusLabels[status] ?? '未知状态'
}
