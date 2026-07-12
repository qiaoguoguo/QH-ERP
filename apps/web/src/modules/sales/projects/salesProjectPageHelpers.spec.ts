import { describe, expect, it } from 'vitest'
import { formatProjectAmount } from './salesProjectPageHelpers'

describe('销售项目页面辅助函数', () => {
  it('金额显示按字符串精度保留 2 位，避免 Number 精度丢失', () => {
    expect(formatProjectAmount('9007199254740993.125')).toBe('9007199254740993.13')
    expect(formatProjectAmount('100000000000000000000.1')).toBe('100000000000000000000.10')
    expect(formatProjectAmount('-12.345')).toBe('-12.35')
    expect(formatProjectAmount('abc')).toBe('-')
  })
})
