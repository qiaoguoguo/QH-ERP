import { describe, expect, it } from 'vitest'
import {
  activeMenuPath,
  currentRouteReturnTo,
  queryWithReturnTo,
  routeReturnTo,
  safeReturnTo,
} from './navigationReturn'

describe('导航返回上下文', () => {
  it('只接受站内路径作为返回地址', () => {
    expect(safeReturnTo('/reports/cost')).toBe('/reports/cost')
    expect(safeReturnTo('/reports/cost?trace=1#row')).toBe('/reports/cost?trace=1#row')
    expect(safeReturnTo('https://example.com/reports/cost')).toBeNull()
    expect(safeReturnTo('//example.com/reports/cost')).toBeNull()
    expect(safeReturnTo('reports/cost')).toBeNull()
    expect(safeReturnTo([' /reports/cost '])).toBeNull()
  })

  it('菜单高亮优先使用返回上下文中的模块路径', () => {
    expect(activeMenuPath('/production/work-orders/9', '/reports/cost?trace=1')).toBe('/reports/cost')
    expect(activeMenuPath('/production/work-orders/9', null)).toBe('/production/work-orders/9')
  })

  it('详情页返回地址优先取已有 returnTo，否则使用当前完整路径', () => {
    expect(routeReturnTo({ query: { returnTo: '/reports/cost' } })).toBe('/reports/cost')
    expect(currentRouteReturnTo({ fullPath: '/cost/records/1', query: { returnTo: '/reports/cost' } })).toBe('/reports/cost')
    expect(currentRouteReturnTo({ fullPath: '/cost/records/1?tab=trace', query: {} })).toBe('/cost/records/1?tab=trace')
  })

  it('来源跳转合并原有查询参数并写入返回上下文', () => {
    expect(queryWithReturnTo({ lineId: '8' }, '/reports/cost')).toEqual({
      lineId: '8',
      returnTo: '/reports/cost',
    })
    expect(queryWithReturnTo({ lineId: '8' }, 'https://example.com')).toEqual({ lineId: '8' })
  })

  it('从带查询条件报表进入追溯详情时保留原完整返回地址', () => {
    const returnTo = currentRouteReturnTo({
      fullPath: '/reports/sales?dateFrom=2026-07-01&dateTo=2026-07-31&customerId=20',
      query: {},
    })

    expect(queryWithReturnTo({ lineId: '8' }, returnTo)).toEqual({
      lineId: '8',
      returnTo: '/reports/sales?dateFrom=2026-07-01&dateTo=2026-07-31&customerId=20',
    })
  })
})
