import { describe, expect, it } from 'vitest'
import {
  bomEffectiveState,
  bomEffectiveStateLabel,
  bomEffectiveStateTagType,
  bomQuantityBasisLabel,
  bomStatusLabel,
  candidateMetaText,
} from './bomPageHelpers'

describe('BOM 页面工具函数', () => {
  const referenceDate = '2026-07-13'

  it('按参考日期派生已发布 BOM 的当前、未来、历史和空起止状态', () => {
    expect(bomEffectiveState('ENABLED', '2026-07-01', '2026-07-31', referenceDate)).toBe('CURRENT')
    expect(bomEffectiveState('ENABLED', null, '2026-07-13', referenceDate)).toBe('CURRENT')
    expect(bomEffectiveState('ENABLED', '2026-07-13', null, referenceDate)).toBe('CURRENT')
    expect(bomEffectiveState('ENABLED', null, null, referenceDate)).toBe('CURRENT')
    expect(bomEffectiveState('ENABLED', '2026-07-14', null, referenceDate)).toBe('FUTURE')
    expect(bomEffectiveState('ENABLED', null, '2026-07-12', referenceDate)).toBe('EXPIRED')
  })

  it('草稿和停用 BOM 不按有效期冒充当前有效', () => {
    expect(bomEffectiveState('DRAFT', '2026-07-01', '2026-07-31', referenceDate)).toBe('UNPUBLISHED')
    expect(bomEffectiveState('DISABLED', '2026-07-01', '2026-07-31', referenceDate)).toBe('DISABLED')
  })

  it('提供页面可直接展示的时效状态文案和标签类型', () => {
    expect(bomEffectiveStateLabel('CURRENT')).toBe('当前有效')
    expect(bomEffectiveStateLabel('FUTURE')).toBe('未来生效')
    expect(bomEffectiveStateLabel('EXPIRED')).toBe('历史失效')
    expect(bomEffectiveStateLabel('UNPUBLISHED')).toBe('草稿未发布')
    expect(bomEffectiveStateLabel('DISABLED')).toBe('已停用')
    expect(bomEffectiveStateTagType('CURRENT')).toBe('success')
    expect(bomEffectiveStateTagType('FUTURE')).toBe('warning')
    expect(bomEffectiveStateTagType('EXPIRED')).toBe('info')
    expect(bomEffectiveStateTagType('DISABLED')).toBe('info')
  })

  it('BOM 发布状态和数量口径未知值不裸露英文编码', () => {
    expect(bomStatusLabel('DRAFT')).toBe('草稿')
    expect(bomStatusLabel('UNKNOWN_STATUS')).toBe('未知状态')
    expect(bomQuantityBasisLabel('BASE_UNIT')).toBe('基本单位')
    expect(bomQuantityBasisLabel('CONVERTED_BUSINESS_UNIT')).toBe('换算业务单位')
    expect(bomQuantityBasisLabel('LEGACY_BUSINESS_UNIT')).toBe('历史业务单位')
    expect(bomQuantityBasisLabel('UNKNOWN_BASIS')).toBe('未知口径')
  })

  it('候选元信息优先展示原因和摘要，未知状态使用中文兜底', () => {
    expect(candidateMetaText({ disabledReason: '已停用不可选', summary: '摘要', status: 'DISABLED' })).toBe('已停用不可选')
    expect(candidateMetaText({ summary: '摘要', status: 'ENABLED' })).toBe('摘要')
    expect(candidateMetaText({ status: 'ENABLED' })).toBe('启用')
    expect(candidateMetaText({ status: 'ARCHIVED' })).toBe('未知状态')
  })
})
