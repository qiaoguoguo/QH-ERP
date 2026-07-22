import { describe, expect, it } from 'vitest'
import { statusLabel, statusTagType } from './pageHelpers'

describe('系统页面状态文案', () => {
  it('系统状态只展示中文，未知值不得默认成启用', () => {
    expect(statusLabel('ENABLED')).toBe('启用')
    expect(statusLabel('DISABLED')).toBe('停用')
    expect(statusLabel('LOCKED')).toBe('未知状态')
    expect(statusLabel()).toBe('未知状态')
  })

  it('停用与未知状态不使用危险红色语义', () => {
    expect(statusTagType('ENABLED')).toBe('success')
    expect(statusTagType('DISABLED')).toBe('info')
    expect(statusTagType('LOCKED')).toBe('warning')
  })
})
