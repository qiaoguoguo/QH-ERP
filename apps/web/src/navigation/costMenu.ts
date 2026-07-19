import type { MenuNode } from '../shared/api/accountPermissionApi'

export const projectCostPath = '/cost/project-costs'
export const projectCostAdjustmentPath = '/cost/project-cost-adjustments'
export const projectCostVariancePath = '/cost/project-cost-variances'
export const costRecordPath = '/cost/records'

export const costChildren: MenuNode[] = [
  {
    id: 'cost-project-costs',
    code: 'cost:project-cost:view',
    name: '项目成本核算',
    routePath: projectCostPath,
  },
  {
    id: 'cost-project-cost-adjustments',
    code: 'cost:project-cost-adjustment:view',
    name: '成本调整/分配',
    routePath: projectCostAdjustmentPath,
  },
  {
    id: 'cost-project-cost-variances',
    code: 'cost:project-cost-variance:view',
    name: '项目成本差异',
    routePath: projectCostVariancePath,
  },
  {
    id: 'cost-records',
    code: 'cost:record:view',
    name: '成本记录',
    routePath: costRecordPath,
  },
]

export const costMenuPaths = new Set(costChildren.map((child) => child.routePath))
