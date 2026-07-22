import { describe, expect, it } from 'vitest'
import {
  formatProcurementAmount,
  formatProcurementQuantity,
  procurementApprovalStatusLabel,
  procurementModeLabel,
  procurementOwnershipTagType,
  procurementPriceSourceDisplay,
  purchaseInTransitStatusLabel,
  purchaseOrderStatusTagType,
  validateProcurementDecimal,
} from './procurementPageHelpers'

describe('采购页面公共 helper', () => {
  it('格式化数量和金额时保持接口十进制字符串精度', () => {
    expect(formatProcurementQuantity('999999999999.123456')).toBe('999999999999.123456')
    expect(formatProcurementQuantity('12.500000')).toBe('12.5')
    expect(formatProcurementAmount('123456789012.129999')).toBe('123456789012.129999')
    expect(formatProcurementAmount('10.000000')).toBe('10')
  })

  it('校验普通十进制时不返回 Number 业务值', () => {
    const result = validateProcurementDecimal('999999999999.123456', { label: '数量' })

    expect(result.message).toBeNull()
    expect(result.payloadValue).toBe('999999999999.123456')
    expect(result.value).toBe('999999999999.123456')
  })

  it('提供采购模式和所有权中文状态，不只依赖颜色', () => {
    expect(procurementModeLabel('PUBLIC')).toBe('公共采购')
    expect(procurementModeLabel('PROJECT')).toBe('项目专采')
    expect(procurementOwnershipTagType('PUBLIC')).toBe('info')
    expect(procurementOwnershipTagType('PROJECT')).toBe('success')
  })

  it('状态和价格来源未知值使用中文兜底，不裸露原始编码', () => {
    expect(purchaseInTransitStatusLabel('OVERDUE', 'OVERDUE')).toBe('已逾期')
    expect(purchaseInTransitStatusLabel('REVIEW_REQUIRED', 'REVIEW_REQUIRED')).toBe('未知状态')
    expect(procurementApprovalStatusLabel('REJECTED', 'REJECTED')).toBe('已驳回')
    expect(procurementApprovalStatusLabel('REVIEW_REQUIRED', 'REVIEW_REQUIRED')).toBe('未知状态')
    expect(procurementPriceSourceDisplay({ priceSourceType: 'LEGACY_MANUAL', priceSourceNo: 'PS-001' })).toBe('未知价格来源 PS-001')
  })

  it('采购取消状态使用中性色，不误标为失败', () => {
    expect(purchaseOrderStatusTagType('CANCELLED')).toBe('info')
  })
})
