import { describe, expect, it } from 'vitest'
import {
  formatInventoryAmount,
  inventoryActionLabel,
  ownershipTypeLabel,
  valuationStateLabel,
  validateInventoryMoney,
} from './inventoryPageHelpers'

describe('库存计价页面辅助函数', () => {
  it('金额与单价只格式化后端十进制字符串，不把空值显示成零', () => {
    expect(formatInventoryAmount('1325.5')).toBe('1,325.50')
    expect(formatInventoryAmount('3.333333', 6)).toBe('3.333333')
    expect(formatInventoryAmount(null)).toBe('-')
    expect(formatInventoryAmount(undefined)).toBe('-')
  })

  it('所有权、估值状态和动作提供清晰中文文案', () => {
    expect(ownershipTypeLabel('PUBLIC')).toBe('公共库存')
    expect(ownershipTypeLabel('PROJECT')).toBe('项目库存')
    expect(valuationStateLabel('LEGACY_UNVALUED')).toBe('历史未估值')
    expect(valuationStateLabel('NON_VALUED')).toBe('无需计价')
    expect(valuationStateLabel('CURRENT_AVERAGE_PROVISIONAL')).toBe('当前平均暂估')
    expect(inventoryActionLabel('SUBMIT_APPROVAL')).toBe('提交审批')
    expect(inventoryActionLabel('COMPLETE_ZERO_VARIANCE')).toBe('结束零差异盘点')
  })

  it('金额输入只校验十进制字符串精度，不执行库存价值计算', () => {
    expect(validateInventoryMoney('999999999999.999999', 6)).toEqual({
      payloadValue: '999999999999.999999',
      message: null,
    })
    expect(validateInventoryMoney('1.234567', 2).message).toBe('金额最多 2 位小数')
    expect(validateInventoryMoney('-1.00', 2).message).toBe('金额不能为负数')
    expect(validateInventoryMoney('1e3', 2).message).toBe('金额仅支持普通十进制数')
  })
})
