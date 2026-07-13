import { describe, expect, it } from 'vitest'
import {
  bomEffectiveState,
  bomEffectiveStateLabel,
  bomEffectiveStateTagType,
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
  })
})
