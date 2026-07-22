import { describe, expect, it } from 'vitest'
import {
  codingObjectTypeLabel,
  invoiceTypeLabel,
  masterStatusLabel,
  materialTypeLabel,
  sourceTypeLabel,
  trackingMethodLabel,
} from './masterPageHelpers'

describe('基础资料页面状态与枚举文案', () => {
  it('主数据状态只展示中文，未知编码不回退为原始英文', () => {
    expect(masterStatusLabel('ENABLED')).toBe('启用')
    expect(masterStatusLabel('DISABLED')).toBe('停用')
    expect(masterStatusLabel('ARCHIVED')).toBe('未知状态')
  })

  it('物料关键枚举采用产品语义并为未知编码提供中文兜底', () => {
    expect(materialTypeLabel('RAW_MATERIAL')).toBe('原材料')
    expect(materialTypeLabel('UNKNOWN_TYPE')).toBe('未知类型')
    expect(sourceTypeLabel('OUTSOURCED')).toBe('外协')
    expect(sourceTypeLabel('UNKNOWN_SOURCE')).toBe('未知来源')
    expect(trackingMethodLabel('BATCH')).toBe('批次')
    expect(trackingMethodLabel('SERIAL')).toBe('序列号')
    expect(trackingMethodLabel('UNKNOWN_TRACKING')).toBe('未知追踪方式')
  })

  it('业务配置枚举未知值不裸露英文编码', () => {
    expect(codingObjectTypeLabel('UNKNOWN_OBJECT')).toBe('未知对象')
    expect(invoiceTypeLabel('UNKNOWN_INVOICE')).toBe('未知发票类型')
  })
})
