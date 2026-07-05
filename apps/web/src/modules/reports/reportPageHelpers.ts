import type { MenuNode } from '../../shared/api/accountPermissionApi'

export const reportPermissions = {
  menu: 'report',
  overviewView: 'report:overview:view',
  salesView: 'report:sales:view',
  procurementView: 'report:procurement:view',
  inventoryView: 'report:inventory:view',
  productionView: 'report:production:view',
  costView: 'report:cost:view',
  settlementView: 'report:settlement:view',
  exceptionView: 'report:exception:view',
} as const

export interface ReportRouteConfig {
  id: string
  routeName: string
  path: string
  permission: string
  menuName: string
  title: string
  description: string
}

export const reportRouteConfigs: ReportRouteConfig[] = [
  {
    id: 'report-overview',
    routeName: 'reports-overview',
    path: '/reports/overview',
    permission: reportPermissions.overviewView,
    menuName: '经营概览',
    title: '经营概览',
    description: '经营概览页面将在后续任务实现。',
  },
  {
    id: 'report-sales',
    routeName: 'reports-sales',
    path: '/reports/sales',
    permission: reportPermissions.salesView,
    menuName: '销售经营',
    title: '销售经营',
    description: '销售经营汇总页面将在后续任务实现。',
  },
  {
    id: 'report-procurement',
    routeName: 'reports-procurement',
    path: '/reports/procurement',
    permission: reportPermissions.procurementView,
    menuName: '采购经营',
    title: '采购经营',
    description: '采购经营汇总页面将在后续任务实现。',
  },
  {
    id: 'report-inventory',
    routeName: 'reports-inventory',
    path: '/reports/inventory',
    permission: reportPermissions.inventoryView,
    menuName: '库存收发存',
    title: '库存收发存',
    description: '库存收发存汇总页面将在后续任务实现。',
  },
  {
    id: 'report-production',
    routeName: 'reports-production',
    path: '/reports/production',
    permission: reportPermissions.productionView,
    menuName: '生产执行',
    title: '生产执行',
    description: '生产执行汇总页面将在后续任务实现。',
  },
  {
    id: 'report-cost',
    routeName: 'reports-cost',
    path: '/reports/cost',
    permission: reportPermissions.costView,
    menuName: '成本归集',
    title: '成本归集',
    description: '成本归集汇总页面将在后续任务实现。',
  },
  {
    id: 'report-settlement',
    routeName: 'reports-settlement',
    path: '/reports/settlement',
    permission: reportPermissions.settlementView,
    menuName: '往来收付',
    title: '往来收付',
    description: '往来收付汇总页面将在后续任务实现。',
  },
  {
    id: 'report-exceptions',
    routeName: 'reports-exceptions',
    path: '/reports/exceptions',
    permission: reportPermissions.exceptionView,
    menuName: '异常清单',
    title: '异常清单',
    description: '经营异常清单页面将在后续任务实现。',
  },
]

export const reportViewPermissions = reportRouteConfigs.map((item) => item.permission)

export const reportMenuChildren: MenuNode[] = reportRouteConfigs.map((item) => ({
  id: item.id,
  code: item.permission,
  name: item.menuName,
  routePath: item.path,
}))

export function hasAnyReportViewPermission(hasPermission: (permission: string) => boolean) {
  return reportViewPermissions.some((permission) => hasPermission(permission))
}

export function firstReportRouteByPermission(hasPermission: (permission: string) => boolean) {
  return reportRouteConfigs.find((item) => hasPermission(item.permission))?.path ?? null
}

export function reportSourceTypeText(sourceType: string | null | undefined) {
  const labels: Record<string, string> = {
    SALES_SHIPMENT: '销售出库',
    SALES_RETURN: '销售退货',
    PURCHASE_RECEIPT: '采购入库',
    PURCHASE_RETURN: '采购退货',
    INVENTORY_MOVEMENT: '库存流水',
    PRODUCTION_MATERIAL_ISSUE: '生产领料',
    PRODUCTION_MATERIAL_RETURN: '生产退料',
    PRODUCTION_MATERIAL_SUPPLEMENT: '生产补料',
    PRODUCTION_WORK_ORDER: '生产工单',
    COST_RECORD: '成本记录',
    RECEIVABLE: '应收',
    PAYABLE: '应付',
    RECEIPT: '收款',
    PAYMENT: '付款',
    SETTLEMENT_ADJUSTMENT: '往来冲减',
  }
  return sourceType ? labels[sourceType] ?? sourceType : '-'
}
