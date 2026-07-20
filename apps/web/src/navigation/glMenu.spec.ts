import { describe, expect, it } from 'vitest'
import { glChildren, glMenuPaths } from './glMenu'
import appMenuRegistrySource from './appMenuRegistry.ts?raw'

describe('glMenu', () => {
  it('通过轻量菜单注册会计核算入口，不要求 App.vue 新增专用 ensure/remove', () => {
    expect(glChildren.map((item) => item.routePath)).toEqual([
      '/gl/accounting-periods',
      '/gl/accounts',
      '/gl/auxiliaries',
      '/gl/posting-rules',
      '/gl/vouchers',
      '/gl/ledgers/general',
      '/gl/ledgers/detail',
      '/gl/account-balances',
      '/gl/trial-balance',
    ])
    expect(glChildren.map((item) => item.code)).toEqual([
      'gl:period:view',
      'gl:account:view',
      'gl:auxiliary:view',
      'gl:rule:view',
      'gl:voucher:view',
      'gl:ledger:view',
      'gl:ledger:view',
      'gl:balance:view',
      'gl:balance:view',
    ])
    expect(glMenuPaths.has('/gl/vouchers')).toBe(true)
    expect(appMenuRegistrySource).toContain('glChildren')
    expect(appMenuRegistrySource).toContain("name: '会计核算'")
  })
})
