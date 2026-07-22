import { describe, expect, it } from 'vitest'
import {
  salesPriceSourceLabel,
  salesMovementDirectionLabel,
  salesMovementTypeLabel,
  salesOrderStatusTagType,
} from './salesPageHelpers'

describe('销售页面公共 helper', () => {
  it('库存流水类型和方向未知值使用中文兜底，不裸露原始编码', () => {
    expect(salesMovementTypeLabel('SALES_SHIPMENT')).toBe('销售出库')
    expect(salesMovementTypeLabel('WAREHOUSE_TRANSFER_OUT')).toBe('调拨出库')
    expect(salesMovementTypeLabel('SALES_RETURN_IN')).toBe('销售退货入库')
    expect(salesMovementTypeLabel('PURCHASE_RETURN_OUT')).toBe('采购退货出库')
    expect(salesMovementTypeLabel('PRODUCTION_MATERIAL_RETURN_IN')).toBe('生产退料入库')
    expect(salesMovementTypeLabel('PRODUCTION_MATERIAL_SUPPLEMENT_OUT')).toBe('生产补料出库')
    expect(salesMovementTypeLabel('QUALITY_STATUS_TRANSFER')).toBe('质量状态转移')
    expect(salesMovementTypeLabel('BUSINESS_REVERSAL')).toBe('业务反向冲销')
    expect(salesMovementTypeLabel('STOCKTAKE_VARIANCE_IN')).toBe('盘点差异入库')
    expect(salesMovementTypeLabel('STOCKTAKE_VARIANCE_OUT')).toBe('盘点差异出库')
    expect(salesMovementTypeLabel('OUTSOURCING_ISSUE')).toBe('外协发料')
    expect(salesMovementTypeLabel('OUTSOURCING_RECEIPT')).toBe('外协收货')
    expect(salesMovementTypeLabel('STOCKTAKE_GAIN')).toBe('未知类型')
    expect(salesMovementTypeLabel('STOCKTAKE_LOSS')).toBe('未知类型')
    expect(salesMovementTypeLabel('LEGACY_UNKNOWN')).toBe('未知类型')
    expect(salesMovementDirectionLabel('IN')).toBe('入库')
    expect(salesMovementDirectionLabel('SIDEWAYS')).toBe('未知方向')
  })

  it('销售订单取消状态使用中性色，不误标为失败', () => {
    expect(salesOrderStatusTagType('CANCELLED')).toBe('info')
  })

  it('销售价格来源使用销售语境中文标签，未知值不裸露原始编码', () => {
    expect(salesPriceSourceLabel({ priceSourceType: 'MANUAL' })).toBe('手工录入')
    expect(salesPriceSourceLabel({ priceSourceType: 'QUOTE', priceSourceNo: 'SQ-001' })).toBe('报价带入 SQ-001')
    expect(salesPriceSourceLabel({ priceSourceType: 'LEGACY_MANUAL' })).toBe('历史手工价')
    expect(salesPriceSourceLabel({ priceSourceType: 'LEGACY_UNKNOWN', priceSourceNo: 'SRC-1' })).toBe('未知价格来源 SRC-1')
  })
})
