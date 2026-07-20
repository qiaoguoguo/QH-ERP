import { describe, expect, it } from 'vitest'
import { financialCloseChildren, financialCloseMenuPaths } from './financialCloseMenu'
import { glChildren } from './glMenu'
import appMenuRegistrySource from './appMenuRegistry.ts?raw'

describe('financialCloseMenu', () => {
  it('把 032 菜单挂入现有会计核算，不新增一级菜单或 finance 菜单', () => {
    expect(financialCloseChildren.map((item) => item.routePath)).toEqual([
      '/gl/financial-close',
      '/gl/profit-loss-carryforward',
      '/gl/bank-accounts',
      '/gl/bank-statements',
      '/gl/bank-reconciliation',
      '/gl/tax-settings',
      '/gl/tax-summary',
      '/gl/tax-payments',
    ])
    expect(financialCloseChildren.map((item) => item.code)).toEqual([
      'financial-close:period:view',
      'financial-close:profit-loss:view',
      'financial-close:bank-account:view',
      'financial-close:bank-reconciliation:import',
      'financial-close:bank-reconciliation:view',
      'financial-close:tax-profile:view',
      'financial-close:tax-summary:view',
      'financial-close:tax-payment:view',
    ])
    expect(glChildren.map((item) => item.routePath)).toContain('/gl/financial-close')
    expect(financialCloseMenuPaths.has('/gl/tax-summary')).toBe(true)
    expect(appMenuRegistrySource).toContain('glChildren')
    expect(appMenuRegistrySource).toContain("name: '会计核算'")
    expect(appMenuRegistrySource).not.toContain("name: '财务结账'")
  })
})
