import { describe, expect, it } from 'vitest'
import {
  formatProjectAmount,
  salesProjectContractStatusLabel,
  salesProjectContractStatusTagType,
  salesProjectStatusLabel,
  salesProjectStatusTagType,
} from './salesProjectPageHelpers'

describe('销售项目页面辅助函数', () => {
  it('金额显示按字符串精度保留 2 位，避免 Number 精度丢失', () => {
    expect(formatProjectAmount('9007199254740993.125')).toBe('9007199254740993.13')
    expect(formatProjectAmount('100000000000000000000.1')).toBe('100000000000000000000.10')
    expect(formatProjectAmount('-12.345')).toBe('-12.35')
    expect(formatProjectAmount('abc')).toBe('-')
  })

  it('项目与合同状态未知值使用中文兜底，取消状态为中性色', () => {
    expect(salesProjectStatusLabel('ACTIVE')).toBe('执行中')
    expect(salesProjectStatusLabel('REVIEW_REQUIRED')).toBe('未知状态')
    expect(salesProjectContractStatusLabel('EFFECTIVE')).toBe('已生效')
    expect(salesProjectContractStatusLabel('REVIEW_REQUIRED')).toBe('未知状态')
    expect(salesProjectStatusTagType('CANCELLED')).toBe('info')
    expect(salesProjectContractStatusTagType('CANCELLED')).toBe('info')
  })
})
