import { describe, expect, it } from 'vitest'
import { qualityInspectionSourceTypeLabel, qualityInspectionStatusLabel } from './qualityPageHelpers'

describe('质量页面辅助函数', () => {
  it('来源类型和处理状态优先使用中文显示名并拒绝原码主文案', () => {
    expect(qualityInspectionSourceTypeLabel('PURCHASE_RECEIPT')).toBe('采购入库')
    expect(qualityInspectionSourceTypeLabel('NEW_SOURCE', '委外入库')).toBe('委外入库')
    expect(qualityInspectionSourceTypeLabel('NEW_SOURCE', 'NEW_SOURCE')).toBe('未知类型')

    expect(qualityInspectionStatusLabel('PENDING')).toBe('待处理')
    expect(qualityInspectionStatusLabel('COMPLETED')).toBe('已处理')
    expect(qualityInspectionStatusLabel('RECHECK', '需复检')).toBe('需复检')
    expect(qualityInspectionStatusLabel('RECHECK', 'RECHECK')).toBe('未知状态')
  })
})
