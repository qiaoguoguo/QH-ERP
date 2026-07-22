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
  operatingFinanceView: 'report:operating-finance:view',
  projectProfitView: 'report:project-profit:view',
  contractCollectionView: 'report:contract-collection:view',
  procurementVarianceView: 'report:procurement-variance:view',
  inventoryCapitalView: 'report:inventory-capital:view',
  receivablePayableView: 'report:receivable-payable:view',
  operatingAccountingView: 'report:operating-accounting:view',
  financialSummaryView: 'report:financial-summary:view',
} as const

export interface ReportRouteConfig {
  id: string
  routeName: string
  path: string
  permission: string
  menuName: string
  title: string
  description: string
  group?: 'basic' | 'operatingFinance'
  menuVisible?: boolean
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
    group: 'basic',
  },
  {
    id: 'report-sales',
    routeName: 'reports-sales',
    path: '/reports/sales',
    permission: reportPermissions.salesView,
    menuName: '销售经营',
    title: '销售经营',
    description: '销售经营汇总页面将在后续任务实现。',
    group: 'basic',
  },
  {
    id: 'report-procurement',
    routeName: 'reports-procurement',
    path: '/reports/procurement',
    permission: reportPermissions.procurementView,
    menuName: '采购经营',
    title: '采购经营',
    description: '采购经营汇总页面将在后续任务实现。',
    group: 'basic',
  },
  {
    id: 'report-inventory',
    routeName: 'reports-inventory',
    path: '/reports/inventory',
    permission: reportPermissions.inventoryView,
    menuName: '库存收发存',
    title: '库存收发存',
    description: '库存收发存汇总页面将在后续任务实现。',
    group: 'basic',
  },
  {
    id: 'report-production',
    routeName: 'reports-production',
    path: '/reports/production',
    permission: reportPermissions.productionView,
    menuName: '生产执行',
    title: '生产执行',
    description: '生产执行汇总页面将在后续任务实现。',
    group: 'basic',
  },
  {
    id: 'report-cost',
    routeName: 'reports-cost',
    path: '/reports/cost',
    permission: reportPermissions.costView,
    menuName: '成本归集',
    title: '成本归集',
    description: '成本归集汇总页面将在后续任务实现。',
    group: 'basic',
  },
  {
    id: 'report-settlement',
    routeName: 'reports-settlement',
    path: '/reports/settlement',
    permission: reportPermissions.settlementView,
    menuName: '往来收付',
    title: '往来收付',
    description: '往来收付汇总页面将在后续任务实现。',
    group: 'basic',
  },
  {
    id: 'report-exceptions',
    routeName: 'reports-exceptions',
    path: '/reports/exceptions',
    permission: reportPermissions.exceptionView,
    menuName: '异常清单',
    title: '异常清单',
    description: '经营异常清单页面将在后续任务实现。',
    group: 'basic',
  },
  {
    id: 'report-project-profit',
    routeName: 'reports-project-profit',
    path: '/reports/project-profit',
    permission: reportPermissions.projectProfitView,
    menuName: '项目利润',
    title: '项目利润',
    description: '按项目查看经营收入、项目成本、毛利、会计口径和差异。',
    group: 'operatingFinance',
  },
  {
    id: 'report-project-profit-detail',
    routeName: 'reports-project-profit-detail',
    path: '/reports/project-profit/:projectId',
    permission: reportPermissions.projectProfitView,
    menuName: '项目利润详情',
    title: '项目利润详情',
    description: '查看单个项目的收入、成本、会计对照、差异和来源。',
    group: 'operatingFinance',
    menuVisible: false,
  },
  {
    id: 'report-contract-collection',
    routeName: 'reports-contract-collection',
    path: '/reports/contract-collection',
    permission: reportPermissions.contractCollectionView,
    menuName: '合同回款',
    title: '合同回款',
    description: '按合同查看开票、收款、核销、未收和逾期。',
    group: 'operatingFinance',
  },
  {
    id: 'report-procurement-variance',
    routeName: 'reports-procurement-variance',
    path: '/reports/procurement-variance',
    permission: reportPermissions.procurementVarianceView,
    menuName: '采购差异',
    title: '采购差异',
    description: '查看订单、收货、发票、付款、三单匹配和外协结算差异。',
    group: 'operatingFinance',
  },
  {
    id: 'report-inventory-capital',
    routeName: 'reports-inventory-capital',
    path: '/reports/inventory-capital',
    permission: reportPermissions.inventoryCapitalView,
    menuName: '库存资金',
    title: '库存资金',
    description: '查看公共和项目库存资金、估值风险和月结快照差异。',
    group: 'operatingFinance',
  },
  {
    id: 'report-receivable-payable',
    routeName: 'reports-receivable-payable',
    path: '/reports/receivable-payable',
    permission: reportPermissions.receivablePayableView,
    menuName: '往来账龄',
    title: '往来账龄',
    description: '查看应收、应付、预收、预付、核销、余额和固定账龄。',
    group: 'operatingFinance',
  },
  {
    id: 'report-operating-accounting',
    routeName: 'reports-operating-accounting',
    path: '/reports/operating-accounting-reconciliation',
    permission: reportPermissions.operatingAccountingView,
    menuName: '经营会计对照',
    title: '经营会计对照',
    description: '并列展示管理口径与会计项目辅助口径，解释差异和不可对账状态。',
    group: 'operatingFinance',
  },
  {
    id: 'report-financial-summary',
    routeName: 'reports-financial-summary',
    path: '/reports/financial-summary',
    permission: reportPermissions.financialSummaryView,
    menuName: '固定经营财务摘要',
    title: '固定经营财务摘要',
    description: '管理用固定经营财务摘要，不是法定三大报表。',
    group: 'operatingFinance',
  },
]

export const reportViewPermissions = reportRouteConfigs.map((item) => item.permission)

export const reportMenuChildren: MenuNode[] = reportRouteConfigs.filter((item) => item.menuVisible !== false).map((item) => ({
  id: item.id,
  code: item.permission,
  name: item.menuName,
  routePath: item.path,
}))

export function hasAnyReportViewPermission(hasPermission: (permission: string) => boolean) {
  return reportViewPermissions.some((permission) => hasPermission(permission))
}

export function firstReportRouteByPermission(hasPermission: (permission: string) => boolean) {
  return reportRouteConfigs.find((item) => item.menuVisible !== false && hasPermission(item.permission))?.path ?? null
}

export function reportDictionaryText(
  labels: Record<string, string>,
  value: string | number | null | undefined,
  unknownText: string,
  emptyText = '受限/不可用',
) {
  if (value === null || value === undefined || String(value).trim() === '') {
    return emptyText
  }
  const key = String(value)
  return Object.prototype.hasOwnProperty.call(labels, key) ? labels[key] : unknownText
}

export function reportSourceTypeText(sourceType: string | null | undefined) {
  const labels: Record<string, string> = {
    PROJECT_COST_CALCULATION: '项目成本计算',
    PROJECT_COST_SOURCE_LINE: '项目成本来源行',
    GL_LEDGER_ENTRY: '总账分录',
    FIN_CLOSE_RUN: '财务关闭',
    BANK_RECONCILIATION_RUN: '银行对账',
    TAX_PERIOD_SUMMARY: '税务期间汇总',
    SALES_SHIPMENT: '销售出库',
    SALES_RETURN: '销售退货',
    PURCHASE_RECEIPT: '采购入库',
    PURCHASE_RETURN: '采购退货',
    INVENTORY_DOCUMENT: '库存单据',
    INVENTORY_MOVEMENT: '库存流水',
    PRODUCTION_MATERIAL_ISSUE: '生产领料',
    PRODUCTION_MATERIAL_RETURN: '生产退料',
    PRODUCTION_MATERIAL_SUPPLEMENT: '生产补料',
    PRODUCTION_WORK_ORDER: '生产工单',
    PRODUCTION_WORK_REPORT: '生产报工',
    PRODUCTION_COMPLETION_RECEIPT: '完工入库',
    COST_RECORD: '成本记录',
    RECEIVABLE: '应收',
    PAYABLE: '应付',
    RECEIPT: '收款',
    PAYMENT: '付款',
    SETTLEMENT_ADJUSTMENT: '往来冲减',
    PROJECT: '项目',
    PROJECT_COST: '项目成本',
    CONTRACT: '合同',
    SALES_PROJECT_CONTRACT: '销售项目合同',
    SALES_ORDER: '销售订单',
    SALES_INVOICE: '销售发票',
    SALES_RECEIPT: '销售收款',
    PURCHASE_ORDER: '采购订单',
    PURCHASE_INVOICE: '采购发票',
    PURCHASE_PAYMENT: '采购付款',
    PROCUREMENT_ORDER: '采购订单',
    OUTSOURCING_ORDER: '外协订单',
    OUTSOURCING_RECEIPT: '外协收货',
    SETTLEMENT_ALLOCATION: '核销单',
    ADVANCE_RECEIPT: '预收款',
    PREPAYMENT: '预付款',
    GL_VOUCHER: '会计凭证',
    TAX_SUMMARY: '税务基础汇总',
    BANK_RECONCILIATION: '银行对账',
    INVENTORY_BALANCE: '库存余额',
  }
  return reportDictionaryText(labels, sourceType, '未知来源', '-')
}

export function reportStatusText(status: string | null | undefined) {
  const labels: Record<string, string> = {
    LIVE: '实时经营口径',
    BUSINESS_SNAPSHOT: '业务月结快照',
    COMPLETE: '完整',
    INCOMPLETE: '不完整',
    UNAVAILABLE: '不可用',
    RESTRICTED: '受限',
    CURRENT: '当前',
    FROZEN: '冻结快照',
    STALE: '已过期',
    LEGACY_NOT_INCLUDED: '旧快照未包含',
    MATCHED: '已匹配',
    DIFFERENT: '存在差异',
    PREVIEW: '预览',
    FINAL: '定稿',
    OPEN: '开放',
    CLOSED: '已关闭',
    ACTIVE: '启用',
    DISABLED: '停用',
    DRAFT: '草稿',
    RELEASED: '已下达',
    IN_PROGRESS: '生产中',
    COMPLETED: '已完工',
    CALCULATED: '已计算',
    CONFIRMED: '已确认',
    EFFECTIVE: '已生效',
    CANCELLED: '已取消',
    VOIDED: '已作废',
    REVERSED: '已冲销',
    PROJECT: '项目专采',
    PUBLIC: '公共',
    CUSTOMER: '客户',
    SUPPLIER: '供应商',
    QUALIFIED: '合格',
    PENDING_INSPECTION: '待检',
    UNQUALIFIED: '不合格',
    AVAILABLE: '可用',
    LOCKED: '冻结',
    VALUED: '已估值',
    NON_VALUED: '估值不完整',
    NOT_VALUED: '估值不完整',
    UNVALUED: '估值不完整',
    LEGACY_UNVALUED: '估值不完整（历史未估值）',
    COLLECTED: '已收齐',
    UNRECEIVED: '未收清',
    OVERDUE: '逾期',
    POSTED: '已过账',
    RECEIVED: '已收款',
    PARTIALLY_RECEIVED: '部分收款',
    SETTLED: '已结清',
    PARTIALLY_SETTLED: '部分结清',
    APPLIED: '已核销',
    PARTIALLY_APPLIED: '部分核销',
    PAID: '已付款',
    PARTIALLY_PAID: '部分付款',
  }
  return reportDictionaryText(labels, status, '未知状态')
}

function hasChineseText(value: string): boolean {
  return /[\u4e00-\u9fff]/.test(value)
}

export function reportTraceStatusText(source: {
  sourceType?: string | null
  status?: string | null
  statusName?: string | null
}) {
  const statusName = String(source.statusName ?? '').trim()
  const status = String(source.status ?? '').trim()
  if (statusName && statusName !== status && hasChineseText(statusName)) {
    return statusName
  }
  const traceLabels: Record<string, Record<string, string>> = {
    RECEIVABLE: {
      CONFIRMED: '待收款',
    },
    PAYABLE: {
      CONFIRMED: '待付款',
    },
    PROJECT_COST: {
      CURRENT: '当前有效',
      STALE: '来源已变化',
    },
    PROJECT_COST_CALCULATION: {
      CURRENT: '当前有效',
      STALE: '来源已变化',
    },
  }
  const sourceType = String(source.sourceType ?? '').trim()
  return traceLabels[sourceType]?.[status] ?? reportStatusText(status)
}
