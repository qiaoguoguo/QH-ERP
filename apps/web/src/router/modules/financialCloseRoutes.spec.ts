import { describe, expect, it } from 'vitest'
import { financialCloseRoutes, firstFinancialCloseRouteByPermission } from './financialCloseRoutes'

describe('financialCloseRoutes', () => {
  it('在 /gl 路由族注册 032 九个页面，不新增一级财务往来入口', () => {
    const routes = new Map(financialCloseRoutes.map((route) => [route.name, route]))

    expect(routes.get('gl-financial-close')).toMatchObject({
      path: '/gl/financial-close',
      meta: { requiredPermission: 'financial-close:period:view' },
    })
    expect(routes.get('gl-financial-close-run-detail')).toMatchObject({
      path: '/gl/financial-close/:runId',
      meta: { requiredPermission: 'financial-close:period:view' },
    })
    expect(routes.get('gl-profit-loss-carryforward')).toMatchObject({
      path: '/gl/profit-loss-carryforward',
      meta: { requiredPermission: 'financial-close:profit-loss:view' },
    })
    expect(routes.get('gl-bank-accounts')).toMatchObject({
      path: '/gl/bank-accounts',
      meta: { requiredPermission: 'financial-close:bank-account:view' },
    })
    expect(routes.get('gl-bank-statements')).toMatchObject({
      path: '/gl/bank-statements',
      meta: { requiredPermission: 'financial-close:bank-reconciliation:import' },
    })
    expect(routes.get('gl-bank-reconciliation')).toMatchObject({
      path: '/gl/bank-reconciliation',
      meta: { requiredPermission: 'financial-close:bank-reconciliation:view' },
    })
    expect(routes.get('gl-tax-settings')).toMatchObject({
      path: '/gl/tax-settings',
      meta: { requiredPermission: 'financial-close:tax-profile:view' },
    })
    expect(routes.get('gl-tax-summary')).toMatchObject({
      path: '/gl/tax-summary',
      meta: { requiredPermission: 'financial-close:tax-summary:view' },
    })
    expect(routes.get('gl-tax-payments')).toMatchObject({
      path: '/gl/tax-payments',
      meta: { requiredPermission: 'financial-close:tax-payment:view' },
    })
  })

  it('按 032 查看权限决定 /gl 默认财务结账入口', () => {
    expect(firstFinancialCloseRouteByPermission((permission) => permission === 'financial-close:period:view')).toEqual({ name: 'gl-financial-close' })
    expect(firstFinancialCloseRouteByPermission((permission) => permission === 'financial-close:bank-reconciliation:view')).toEqual({ name: 'gl-bank-reconciliation' })
    expect(firstFinancialCloseRouteByPermission((permission) => permission === 'financial-close:tax-summary:view')).toEqual({ name: 'gl-tax-summary' })
    expect(firstFinancialCloseRouteByPermission(() => false)).toBeNull()
  })
})
