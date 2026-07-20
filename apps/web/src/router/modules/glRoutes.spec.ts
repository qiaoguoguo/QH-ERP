import { describe, expect, it } from 'vitest'
import { firstGlRouteByPermission, glRoutes } from './glRoutes'

describe('glRoutes', () => {
  it('以独立 /gl 模块暴露基础设置、凭证处理和账簿报表路由权限', () => {
    const routes = new Map(glRoutes.map((route) => [route.name, route]))

    expect(routes.get('gl-root')?.path).toBe('/gl')
    expect(routes.get('gl-accounts')).toMatchObject({ path: '/gl/accounts', meta: { requiredPermission: 'gl:account:view' } })
    expect(routes.get('gl-auxiliaries')).toMatchObject({ path: '/gl/auxiliaries', meta: { requiredPermission: 'gl:auxiliary:view' } })
    expect(routes.get('gl-accounting-periods')).toMatchObject({ path: '/gl/accounting-periods', meta: { requiredPermission: 'gl:period:view' } })
    expect(routes.get('gl-posting-rules')).toMatchObject({ path: '/gl/posting-rules', meta: { requiredPermission: 'gl:rule:view' } })
    expect(routes.get('gl-vouchers')).toMatchObject({ path: '/gl/vouchers', meta: { requiredPermission: 'gl:voucher:view' } })
    expect(routes.get('gl-voucher-create')).toMatchObject({ path: '/gl/vouchers/create', meta: { requiredPermission: 'gl:voucher:create' } })
    expect(routes.get('gl-voucher-detail')).toMatchObject({ path: '/gl/vouchers/:id', meta: { requiredPermission: 'gl:voucher:view' } })
    expect(routes.get('gl-voucher-edit')).toMatchObject({ path: '/gl/vouchers/:id/edit', meta: { requiredPermission: 'gl:voucher:update' } })
    expect(routes.get('gl-ledger-general')).toMatchObject({ path: '/gl/ledgers/general', meta: { requiredPermission: 'gl:ledger:view' } })
    expect(routes.get('gl-ledger-detail')).toMatchObject({ path: '/gl/ledgers/detail', meta: { requiredPermission: 'gl:ledger:view' } })
    expect(routes.get('gl-account-balances')).toMatchObject({ path: '/gl/account-balances', meta: { requiredPermission: 'gl:balance:view' } })
    expect(routes.get('gl-trial-balance')).toMatchObject({ path: '/gl/trial-balance', meta: { requiredPermission: 'gl:balance:view' } })
  })

  it('根路径按首个可用 GL 查看权限重定向', () => {
    expect(firstGlRouteByPermission((permission) => permission === 'gl:account:view')).toEqual({ name: 'gl-accounts' })
    expect(firstGlRouteByPermission((permission) => permission === 'gl:voucher:view')).toEqual({ name: 'gl-vouchers' })
    expect(firstGlRouteByPermission((permission) => permission === 'gl:balance:view')).toEqual({ name: 'gl-account-balances' })
    expect(firstGlRouteByPermission(() => false)).toBeNull()
  })
})
