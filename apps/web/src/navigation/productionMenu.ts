import type { MenuNode } from '../shared/api/accountPermissionApi'

export const productionWorkOrderPath = '/production/work-orders'
export const productionMaterialReturnPath = '/production/material-returns'
export const productionMaterialSupplementPath = '/production/material-supplements'
export const productionOutsourcingOrderPath = '/production/outsourcing-orders'

export const productionChildren: MenuNode[] = [
  {
    id: 'production-work-orders',
    code: 'production:work-order:view',
    name: '生产工单',
    routePath: productionWorkOrderPath,
  },
  {
    id: 'production-material-returns',
    code: 'production:material-return:view',
    name: '生产退料',
    routePath: productionMaterialReturnPath,
  },
  {
    id: 'production-material-supplements',
    code: 'production:material-supplement:view',
    name: '生产补料',
    routePath: productionMaterialSupplementPath,
  },
  {
    id: 'production-outsourcing-orders',
    code: 'production:outsourcing:view',
    name: '外协执行',
    routePath: productionOutsourcingOrderPath,
  },
]

export const productionMenuPaths = new Set(productionChildren.map((child) => child.routePath))
