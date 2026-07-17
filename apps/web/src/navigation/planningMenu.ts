import type { MenuNode } from '../shared/api/accountPermissionApi'

export const planningMaterialRequirementPath = '/planning/material-requirements'

export const planningChildren: MenuNode[] = [
  {
    id: 'planning-material-requirements',
    code: 'planning:material-requirement:view',
    name: '订单缺料分析',
    routePath: planningMaterialRequirementPath,
  },
]

export const planningMenuPaths = new Set(planningChildren.map((child) => child.routePath))
