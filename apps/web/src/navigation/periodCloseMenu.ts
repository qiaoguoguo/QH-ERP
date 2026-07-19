import type { MenuNode } from '../shared/api/accountPermissionApi'

export const periodClosePath = '/period-close/runs'

export const periodCloseChildren: MenuNode[] = [
  {
    id: 'period-close-runs',
    code: 'system:business-period-close:view',
    name: '月结工作台',
    routePath: periodClosePath,
  },
]

export const periodCloseMenuPaths = new Set(periodCloseChildren.map((child) => child.routePath))
