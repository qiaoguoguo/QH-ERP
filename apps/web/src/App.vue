<script setup lang="ts">
import type { Component } from 'vue'
import { computed, onMounted, ref, watch } from 'vue'
import {
  Box,
  Calendar,
  Coin,
  Collection,
  Cpu,
  Expand,
  Fold,
  Grid,
  House,
  Money,
  OfficeBuilding,
  Sell,
  Setting,
  ShoppingCart,
  TrendCharts,
} from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuNode } from './shared/api/accountPermissionApi'
import { financePermissions } from './modules/finance/financePageHelpers'
import {
  planningChildren,
  planningMaterialRequirementPath,
  planningMenuPaths,
} from './navigation/planningMenu'
import {
  productionChildren,
  productionMaterialReturnPath,
  productionMaterialSupplementPath,
  productionMenuPaths,
  productionOutsourcingOrderPath,
  productionWorkOrderPath,
} from './navigation/productionMenu'
import { reportMenuChildren, reportRouteConfigs } from './modules/reports/reportPageHelpers'
import { activeMenuPath } from './shared/navigation/navigationReturn'
import { useAuthStore } from './stores/authStore'
import qhLogoUrl from './assets/logo.ico'
import { costChildren, costMenuPaths } from './navigation/costMenu'
import { applyRegisteredModuleMenus, registeredModuleMenuPaths } from './navigation/appMenuRegistry'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isLogin = computed(() => route.name === 'login')
const sideMenuActivePath = computed(() => activeMenuPath(route.path, route.query.returnTo))
const businessPeriodPath = '/system/business-periods'
const inventoryBalancePath = '/inventory/balances'
const inventoryMovementPath = '/inventory/movements'
const inventoryDocumentPath = '/inventory/documents'
const inventoryWarehouseTransferPath = '/inventory/warehouse-transfers'
const inventoryOwnershipConversionPath = '/inventory/ownership-conversions'
const inventoryStocktakePath = '/inventory/stocktakes'
const inventoryValuationAdjustmentPath = '/inventory/valuation-adjustments'
const procurementRequisitionPath = '/procurement/requisitions'
const procurementInquiryPath = '/procurement/inquiries'
const procurementPriceAgreementPath = '/procurement/price-agreements'
const procurementOrderPath = '/procurement/orders'
const procurementReceiptPath = '/procurement/receipts'
const procurementReturnPath = '/procurement/returns'
const procurementEffectiveSupplyPath = '/procurement/effective-supplies'
const salesProjectPath = '/sales/projects'
const salesQuotePath = '/sales/quotes'
const salesOrderPath = '/sales/orders'
const salesDeliveryPlanPath = '/sales/delivery-plans'
const salesShipmentPath = '/sales/shipments'
const salesReturnPath = '/sales/returns'
const salesCreditProfilePath = '/sales/credit-profiles'
const salesEffectiveDemandPath = '/sales/effective-demands'
const qualityInspectionPath = '/quality/inspections'
const financeSalesInvoicePath = '/finance/sales-invoices'
const financePurchaseInvoicePath = '/finance/purchase-invoices'
const financeExpensePath = '/finance/expenses'
const financeAdvanceReceiptPath = '/finance/advance-receipts'
const financePrepaymentPath = '/finance/prepayments'
const financeSettlementWorkbenchPath = '/finance/settlement-workbench'
const financeVoucherDraftPath = '/finance/voucher-drafts'
const financeReceivablePath = '/finance/receivables'
const financeReceiptPath = '/finance/receipts'
const financePayablePath = '/finance/payables'
const financePaymentPath = '/finance/payments'
const financeSettlementAdjustmentPath = '/finance/settlement-adjustments'
const reportMenuPaths = new Set(reportRouteConfigs.map((item) => item.path))
const platformApprovalPath = '/platform/approvals'
const platformMessagePath = '/platform/messages'
const platformDocumentTaskPath = '/platform/document-tasks'
const platformDataRepairPath = '/platform/data-repairs'
const platformHistoryImportPath = '/platform/history-imports'
const platformDeliveryAssetPath = '/platform/delivery-assets'
const masterUnitPath = '/master/units'
const masterUnitConversionPath = '/master/unit-conversions'
const masterCodingRulePath = '/master/coding-rules'
const masterWarehousePath = '/master/warehouses'
const masterSupplierPath = '/master/suppliers'
const masterCustomerPath = '/master/customers'
const supportedMenuPaths = new Set<string>(([
  '/accounts/users',
  '/system/users',
  '/accounts/roles',
  '/system/roles',
  platformApprovalPath,
  platformMessagePath,
  platformDocumentTaskPath,
  platformDataRepairPath,
  platformHistoryImportPath,
  platformDeliveryAssetPath,
  businessPeriodPath,
  masterUnitPath,
  masterUnitConversionPath,
  masterCodingRulePath,
  masterWarehousePath,
  masterSupplierPath,
  masterCustomerPath,
  '/materials/categories',
  '/materials/items',
  '/materials/boms',
  inventoryBalancePath,
  inventoryMovementPath,
  inventoryDocumentPath,
  inventoryWarehouseTransferPath,
  inventoryOwnershipConversionPath,
  inventoryStocktakePath,
  inventoryValuationAdjustmentPath,
  procurementRequisitionPath,
  procurementInquiryPath,
  procurementPriceAgreementPath,
  procurementOrderPath,
  procurementReceiptPath,
  procurementReturnPath,
  procurementEffectiveSupplyPath,
  salesProjectPath,
  salesQuotePath,
  salesOrderPath,
  salesDeliveryPlanPath,
  salesShipmentPath,
  salesReturnPath,
  salesCreditProfilePath,
  salesEffectiveDemandPath,
  planningMaterialRequirementPath,
  productionWorkOrderPath,
  productionMaterialReturnPath,
  productionMaterialSupplementPath,
  productionOutsourcingOrderPath,
  qualityInspectionPath,
  ...costMenuPaths,
  ...registeredModuleMenuPaths,
  financeSalesInvoicePath,
  financePurchaseInvoicePath,
  financeExpensePath,
  financeAdvanceReceiptPath,
  financePrepaymentPath,
  financeSettlementWorkbenchPath,
  financeVoucherDraftPath,
  financeReceivablePath,
  financeReceiptPath,
  financePayablePath,
  financePaymentPath,
  financeSettlementAdjustmentPath,
  ...reportMenuPaths,
] as Array<string | null | undefined>).filter((path): path is string => typeof path === 'string' && path.length > 0))
const masterChildren: MenuNode[] = [
  {
    id: 'master-units',
    code: 'master:unit:view',
    name: '计量单位',
    routePath: masterUnitPath,
  },
  {
    id: 'master-unit-conversions',
    code: 'master:unit-conversion:view',
    name: '物料单位换算',
    routePath: masterUnitConversionPath,
  },
  {
    id: 'master-coding-rules',
    code: 'master:coding-rule:view',
    name: '编码规则',
    routePath: masterCodingRulePath,
  },
  {
    id: 'master-warehouses',
    code: 'master:warehouse:view',
    name: '仓库',
    routePath: masterWarehousePath,
  },
  {
    id: 'master-suppliers',
    code: 'master:supplier:view',
    name: '供应商',
    routePath: masterSupplierPath,
  },
  {
    id: 'master-customers',
    code: 'master:customer:view',
    name: '客户',
    routePath: masterCustomerPath,
  },
]
const masterMenuPaths = new Set(masterChildren.map((child) => child.routePath))
const masterMenuCodes = new Set(masterChildren.map((child) => String(child.code)))
const platformChildren: MenuNode[] = [
  {
    id: 'platform-approvals',
    code: 'platform:todo:view',
    name: '审批待办',
    routePath: platformApprovalPath,
  },
  {
    id: 'platform-messages',
    code: 'platform:message:view',
    name: '消息中心',
    routePath: platformMessagePath,
  },
  {
    id: 'platform-document-tasks',
    code: 'platform:document-task:view',
    name: '任务中心',
    routePath: platformDocumentTaskPath,
  },
  {
    id: 'platform-data-repairs',
    code: 'platform:data-repair:view',
    name: '数据修复',
    routePath: platformDataRepairPath,
  },
  {
    id: 'platform-history-imports',
    code: 'platform:history-import:view',
    name: '历史导入',
    routePath: platformHistoryImportPath,
  },
  {
    id: 'platform-delivery-assets',
    code: 'platform:delivery-asset:view',
    name: '交付资料',
    routePath: platformDeliveryAssetPath,
  },
]
const platformMenuPaths = new Set(platformChildren.map((child) => child.routePath))
const systemChildren: MenuNode[] = [
  {
    id: 'system-business-periods',
    code: 'system:business-period:view',
    name: '业务期间',
    routePath: businessPeriodPath,
  },
]
const systemMenuPaths = new Set(systemChildren.map((child) => child.routePath))
const inventoryChildren: MenuNode[] = [
  {
    id: 'inventory-balances',
    code: 'inventory:balance:view',
    name: '库存余额与价值',
    routePath: inventoryBalancePath,
  },
  {
    id: 'inventory-movements',
    code: 'inventory:movement:view',
    name: '库存流水与价值',
    routePath: inventoryMovementPath,
  },
  {
    id: 'inventory-documents',
    code: 'inventory:document:view',
    name: '库存单据',
    routePath: inventoryDocumentPath,
  },
  {
    id: 'inventory-warehouse-transfers',
    code: 'inventory:warehouse-transfer:view',
    name: '仓库调拨',
    routePath: inventoryWarehouseTransferPath,
  },
  {
    id: 'inventory-ownership-conversions',
    code: 'inventory:ownership-conversion:view',
    name: '所有权转换',
    routePath: inventoryOwnershipConversionPath,
  },
  {
    id: 'inventory-stocktakes',
    code: 'inventory:stocktake:view',
    name: '库存盘点',
    routePath: inventoryStocktakePath,
  },
  {
    id: 'inventory-valuation-adjustments',
    code: 'inventory:valuation-adjustment:view',
    name: '估值调整',
    routePath: inventoryValuationAdjustmentPath,
  },
]
const inventoryMenuPaths = new Set(inventoryChildren.map((child) => child.routePath))
const procurementChildren: MenuNode[] = [
  {
    id: 'procurement-requisitions',
    code: 'procurement:requisition:view',
    name: '采购请购',
    routePath: procurementRequisitionPath,
  },
  {
    id: 'procurement-inquiries',
    code: 'procurement:inquiry:view',
    name: '询价比价',
    routePath: procurementInquiryPath,
  },
  {
    id: 'procurement-price-agreements',
    code: 'procurement:price-agreement:view',
    name: '价格协议',
    routePath: procurementPriceAgreementPath,
  },
  {
    id: 'procurement-orders',
    code: 'procurement:order:view',
    name: '采购订单',
    routePath: procurementOrderPath,
  },
  {
    id: 'procurement-receipts',
    code: 'procurement:receipt:view',
    name: '采购入库',
    routePath: procurementReceiptPath,
  },
  {
    id: 'procurement-returns',
    code: 'procurement:return:view',
    name: '采购退货',
    routePath: procurementReturnPath,
  },
  {
    id: 'procurement-effective-supplies',
    code: 'procurement:supply:view',
    name: '有效采购供给',
    routePath: procurementEffectiveSupplyPath,
  },
]
const procurementMenuPaths = new Set(procurementChildren.map((child) => child.routePath))
const salesChildren: MenuNode[] = [
  {
    id: 'sales-projects',
    code: 'sales:project:view',
    name: '销售项目',
    routePath: salesProjectPath,
  },
  {
    id: 'sales-quotes',
    code: 'sales:quote:view',
    name: '销售报价',
    routePath: salesQuotePath,
  },
  {
    id: 'sales-orders',
    code: 'sales:order:view',
    name: '销售订单',
    routePath: salesOrderPath,
  },
  {
    id: 'sales-delivery-plans',
    code: 'sales:delivery-plan:view',
    name: '交付计划',
    routePath: salesDeliveryPlanPath,
  },
  {
    id: 'sales-shipments',
    code: 'sales:shipment:view',
    name: '销售出库',
    routePath: salesShipmentPath,
  },
  {
    id: 'sales-returns',
    code: 'sales:return:view',
    name: '销售退货',
    routePath: salesReturnPath,
  },
  {
    id: 'sales-credit-profiles',
    code: 'sales:credit:view',
    name: '信用档案',
    routePath: salesCreditProfilePath,
  },
  {
    id: 'sales-effective-demands',
    code: 'sales:effective-demand:view',
    name: '有效销售需求',
    routePath: salesEffectiveDemandPath,
  },
]
const salesMenuPaths = new Set(salesChildren.map((child) => child.routePath))
const qualityChildren: MenuNode[] = [
  {
    id: 'quality-inspections',
    code: 'quality:inspection:view',
    name: '质量确认',
    routePath: qualityInspectionPath,
  },
]
const qualityMenuPaths = new Set(qualityChildren.map((child) => child.routePath))
const financeChildren: MenuNode[] = [
  {
    id: 'finance-sales-invoices',
    code: financePermissions.salesInvoiceView,
    name: '销售发票',
    routePath: financeSalesInvoicePath,
  },
  {
    id: 'finance-purchase-invoices',
    code: financePermissions.purchaseInvoiceView,
    name: '采购发票',
    routePath: financePurchaseInvoicePath,
  },
  {
    id: 'finance-expenses',
    code: financePermissions.expenseView,
    name: '费用单',
    routePath: financeExpensePath,
  },
  {
    id: 'finance-advance-receipts',
    code: financePermissions.advanceReceiptView,
    name: '预收款',
    routePath: financeAdvanceReceiptPath,
  },
  {
    id: 'finance-prepayments',
    code: financePermissions.prepaymentView,
    name: '预付款',
    routePath: financePrepaymentPath,
  },
  {
    id: 'finance-settlement-workbench',
    code: financePermissions.settlementAllocationView,
    name: '对账核销',
    routePath: financeSettlementWorkbenchPath,
  },
  {
    id: 'finance-voucher-drafts',
    code: financePermissions.voucherDraftView,
    name: '凭证草稿',
    routePath: financeVoucherDraftPath,
  },
  {
    id: 'finance-receivables',
    code: financePermissions.receivableView,
    name: '应收台账',
    routePath: financeReceivablePath,
  },
  {
    id: 'finance-receipts',
    code: financePermissions.receiptView,
    name: '收款记录',
    routePath: financeReceiptPath,
  },
  {
    id: 'finance-payables',
    code: financePermissions.payableView,
    name: '应付台账',
    routePath: financePayablePath,
  },
  {
    id: 'finance-payments',
    code: financePermissions.paymentView,
    name: '付款记录',
    routePath: financePaymentPath,
  },
  {
    id: 'finance-settlement-adjustments',
    code: financePermissions.settlementAdjustmentView,
    name: '往来冲减',
    routePath: financeSettlementAdjustmentPath,
  },
]
const financeMenuPaths = new Set(financeChildren.map((child) => child.routePath))
const fallbackMainMenuIcon = Grid
const mainMenuIconRules: Array<[RegExp, Component]> = [
  [/system|系统/, Setting],
  [/platform|平台|审批|消息|任务/, Grid],
  [/master|基础/, OfficeBuilding],
  [/material|物料|bom/i, Collection],
  [/inventory|库存/, Box],
  [/quality|质量/, Collection],
  [/procurement|purchase|采购/, ShoppingCart],
  [/sales|销售/, Sell],
  [/planning|计划|缺料/, TrendCharts],
  [/production|生产/, Cpu],
  [/cost|成本/, Coin],
  [/period-close|业务月结|月结/, Calendar],
  [/finance|财务|往来|应收|应付/, Money],
  [/report|报表|经营/, TrendCharts],
]
const menuTree = computed<MenuNode[]>(() => {
  const supportedMenus = filterSupportedMenus(authStore.menus ?? [])
  const platformMenus = ensurePlatformMenu(supportedMenus)
  const masterMenus = ensureMasterMenu(platformMenus)
  const systemMenus = ensureSystemMenu(masterMenus)
  const inventoryMenus = ensureInventoryMenu(systemMenus)
  const procurementMenus = ensureProcurementMenu(inventoryMenus)
  const salesMenus = ensureSalesMenu(procurementMenus)
  const planningMenus = ensurePlanningMenu(salesMenus)
  const productionMenus = ensureProductionMenu(planningMenus)
  const qualityMenus = ensureQualityMenu(productionMenus)
  const costMenus = ensureCostMenu(qualityMenus)
  const financeMenus = ensureFinanceMenu(costMenus)
  const reportMenus = ensureReportsMenu(financeMenus)
  return applyRegisteredModuleMenus(reportMenus, (permission) => authStore.hasPermission(permission), supportedMenuPaths)
})
const displayName = computed(() => authStore.currentUser?.displayName ?? authStore.currentUser?.username ?? '未登录')
const logoutError = ref('')
const logoutLoading = ref(false)
const sessionHydrating = ref(false)

function initialSidebarCollapsed() {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(max-width: 390px)').matches
}

const sidebarCollapsed = ref(initialSidebarCollapsed())
const sidebarToggleLabel = computed(() => sidebarCollapsed.value ? '展开菜单' : '收起菜单')
const sidebarToggleIcon = computed(() => sidebarCollapsed.value ? Expand : Fold)

async function hydrateCurrentUser() {
  if (isLogin.value || authStore.currentUser || sessionHydrating.value) {
    return
  }
  sessionHydrating.value = true
  try {
    await authStore.fetchCurrentUser()
  } catch {
    // 未登录访问公开入口时保持未登录展示；有效后端会话会在这里恢复顶部用户。
  } finally {
    sessionHydrating.value = false
  }
}

function ensureMasterMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = masterChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeMasterMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'master',
      code: 'master',
      name: '基础资料',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function ensurePlatformMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = platformChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removePlatformMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'platform',
      code: 'platform',
      name: '平台工作台',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isPlatformMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'platform'
    || code.startsWith('platform:')
    || (menu.routePath ? platformMenuPaths.has(menu.routePath) : false)
}

function removePlatformMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removePlatformMenus(menu.children ?? []),
    }))
    .filter((menu) => !isPlatformMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function isMasterMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'master'
    || masterMenuCodes.has(code)
    || (menu.routePath ? masterMenuPaths.has(menu.routePath) : false)
}

function removeMasterMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeMasterMenus(menu.children ?? []),
    }))
    .filter((menu) => !isMasterMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

onMounted(() => {
  void hydrateCurrentUser()
})

watch(isLogin, () => {
  void hydrateCurrentUser()
})

function filterSupportedMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: filterSupportedMenus(menu.children ?? []),
    }))
    .filter((menu) => (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length))
}

function ensureSystemMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = systemChildren.filter((child) => authStore.hasPermission(String(child.code)))
  if (!allowedChildren.length) {
    return menus
  }

  const systemIndex = menus.findIndex((menu) =>
    menu.code === 'system' || (menu.routePath ? systemMenuPaths.has(menu.routePath) : false))
  if (systemIndex === -1) {
    return [
      ...menus,
      {
        id: 'system',
        code: 'system',
        name: '系统管理',
        routePath: null,
        children: allowedChildren,
      },
    ]
  }

  return menus.map((menu, index) => {
    if (index !== systemIndex) {
      return menu
    }
    const children = [...(menu.children ?? [])]
    for (const child of allowedChildren) {
      if (!children.some((existing) => existing.routePath === child.routePath)) {
        children.push(child)
      }
    }
    return {
      ...menu,
      name: '系统管理',
      children,
    }
  })
}

function ensureInventoryMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = inventoryChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const inventoryIndex = menus.findIndex((menu) =>
    menu.code === 'inventory' || (menu.routePath ? inventoryMenuPaths.has(menu.routePath) : false))
  if (inventoryIndex === -1) {
    if (!allowedChildren.length) {
      return menus
    }
    return [
      ...menus,
      {
        id: 'inventory',
        code: 'inventory',
        name: '库存管理',
        routePath: null,
        children: allowedChildren,
      },
    ]
  }

  return menus.map((menu, index) => {
    if (index !== inventoryIndex) {
      return menu
    }
    const children = (menu.children ?? []).map((existing) => {
      const currentDefinition = inventoryChildren.find((child) => child.routePath === existing.routePath)
      return currentDefinition ? { ...existing, name: currentDefinition.name, code: currentDefinition.code } : existing
    })
    for (const child of allowedChildren) {
      if (!children.some((existing) => existing.routePath === child.routePath)) {
        children.push(child)
      }
    }
    return {
      ...menu,
      name: '库存管理',
      children,
    }
  })
}

function ensureProcurementMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = procurementChildren.filter((child) => authStore.hasPermission(String(child.code)))
  if (!allowedChildren.length) {
    return removeProcurementMenus(menus)
  }

  const allowedMenuPaths = new Set(allowedChildren.map((child) => child.routePath))
  const procurementIndex = menus.findIndex((menu) =>
    menu.code === 'procurement' || (menu.routePath ? procurementMenuPaths.has(menu.routePath) : false))
  if (procurementIndex === -1) {
    return [
      ...menus,
      {
        id: 'procurement',
        code: 'procurement',
        name: '采购管理',
        routePath: null,
        children: allowedChildren,
      },
    ]
  }

  return menus.map((menu, index) => {
    if (index !== procurementIndex) {
      return menu
    }
    const children = (menu.children ?? []).filter((child) =>
      child.routePath ? allowedMenuPaths.has(child.routePath) : false)
    for (const child of allowedChildren) {
      if (!children.some((existing) => existing.routePath === child.routePath)) {
        children.push(child)
      }
    }
    return {
      ...menu,
      name: '采购管理',
      children,
    }
  })
}

function isProcurementMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'procurement'
    || code.startsWith('procurement:')
    || (menu.routePath ? procurementMenuPaths.has(menu.routePath) : false)
}

function removeProcurementMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeProcurementMenus(menu.children ?? []),
    }))
    .filter((menu) => !isProcurementMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensureSalesMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = salesChildren.filter((child) => (
    authStore.hasPermission(String(child.code))
    || (child.routePath === salesCreditProfilePath && hasSystemAdminRole())
  ))
  const cleanedMenus = removeSalesMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'sales',
      code: 'sales',
      name: '销售管理',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function hasSystemAdminRole() {
  return authStore.roles.some((role) => role.code === 'SYSTEM_ADMIN')
}

function isSalesMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'sales'
    || code.startsWith('sales:')
    || (menu.routePath ? salesMenuPaths.has(menu.routePath) : false)
}

function removeSalesMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeSalesMenus(menu.children ?? []),
    }))
    .filter((menu) => !isSalesMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensurePlanningMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = planningChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removePlanningMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'planning',
      code: 'planning',
      name: '计划管理',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isPlanningMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'planning'
    || code.startsWith('planning:')
    || (menu.routePath ? planningMenuPaths.has(menu.routePath) : false)
}

function removePlanningMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removePlanningMenus(menu.children ?? []),
    }))
    .filter((menu) => !isPlanningMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensureProductionMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = productionChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeProductionMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'production',
      code: 'production',
      name: '生产管理',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isProductionMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'production'
    || code.startsWith('production:')
    || (menu.routePath ? productionMenuPaths.has(menu.routePath) : false)
}

function removeProductionMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeProductionMenus(menu.children ?? []),
    }))
    .filter((menu) => !isProductionMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensureQualityMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = qualityChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeQualityMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'quality',
      code: 'quality',
      name: '质量管理',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isQualityMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'quality'
    || code.startsWith('quality:')
    || (menu.routePath ? qualityMenuPaths.has(menu.routePath) : false)
}

function removeQualityMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeQualityMenus(menu.children ?? []),
    }))
    .filter((menu) => !isQualityMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensureCostMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = costChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeCostMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'cost',
      code: 'cost',
      name: '成本管理',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isCostMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'cost'
    || code.startsWith('cost:')
    || (menu.routePath ? costMenuPaths.has(menu.routePath) : false)
}

function removeCostMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeCostMenus(menu.children ?? []),
    }))
    .filter((menu) => !isCostMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensureFinanceMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = financeChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeFinanceMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'finance',
      code: 'finance',
      name: '财务往来',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isFinanceMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'finance'
    || code.startsWith('finance:')
    || (menu.routePath ? financeMenuPaths.has(menu.routePath) : false)
}

function removeFinanceMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeFinanceMenus(menu.children ?? []),
    }))
    .filter((menu) => !isFinanceMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function ensureReportsMenu(menus: MenuNode[]): MenuNode[] {
  const allowedChildren = reportMenuChildren.filter((child) => authStore.hasPermission(String(child.code)))
  const cleanedMenus = removeReportMenus(menus)
  if (!allowedChildren.length) {
    return cleanedMenus
  }

  return [
    ...cleanedMenus,
    {
      id: 'reports',
      code: 'report',
      name: '经营报表',
      routePath: null,
      children: allowedChildren,
    },
  ]
}

function isReportMenu(menu: MenuNode): boolean {
  const code = String(menu.code ?? '')
  return code === 'report'
    || code.startsWith('report:')
    || (menu.routePath ? reportMenuPaths.has(menu.routePath) : false)
}

function removeReportMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .map((menu) => ({
      ...menu,
      children: removeReportMenus(menu.children ?? []),
    }))
    .filter((menu) => !isReportMenu(menu) && (
      (menu.routePath ? supportedMenuPaths.has(menu.routePath) : false) || Boolean(menu.children?.length)
    ))
}

function hasChildren(menu: MenuNode) {
  return Boolean(menu.children?.length)
}

function menuIndex(menu: MenuNode) {
  if (hasChildren(menu)) {
    return `/menu/${menu.code}`
  }
  return menu.routePath || `/menu/${menu.code}`
}

function mainMenuIcon(menu: MenuNode): Component {
  const signature = `${String(menu.code ?? '')} ${menu.name} ${String(menu.routePath ?? '')}`
  return mainMenuIconRules.find(([pattern]) => pattern.test(signature))?.[1] ?? fallbackMainMenuIcon
}

function mainMenuIconKey(menu: MenuNode): string {
  const source = String(menu.code ?? menu.routePath ?? menu.name)
  return source
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, '-')
    .replace(/(^-)|(-$)/g, '') || 'default'
}

function toggleSidebarCollapsed() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

async function logout() {
  if (logoutLoading.value) {
    return
  }
  logoutError.value = ''
  logoutLoading.value = true
  try {
    await authStore.logout()
    await router.replace({ name: 'login' })
  } catch (error) {
    logoutError.value = error instanceof Error ? error.message : '退出失败，请重试'
  } finally {
    logoutLoading.value = false
  }
}
</script>

<template>
  <el-alert v-if="logoutError" class="global-alert" type="warning" :title="logoutError" show-icon :closable="false" />
  <router-view v-if="isLogin" />

  <el-container v-else class="app-shell">
    <el-aside
      :class="['app-sidebar', { 'is-collapsed': sidebarCollapsed }]"
      :width="sidebarCollapsed ? '72px' : '232px'"
    >
      <div class="brand">
        <img data-test="app-logo" class="brand-logo" :src="qhLogoUrl" alt="QH ERP 系统标识">
        <div class="brand-copy">
          <strong>QH ERP</strong>
          <span>制造业生产管理 ERP</span>
        </div>
        <el-tooltip :content="sidebarToggleLabel" placement="right" :show-after="250">
          <button
            data-test="sidebar-toggle-button"
            class="sidebar-toggle"
            type="button"
            :aria-label="sidebarToggleLabel"
            :title="sidebarToggleLabel"
            @click="toggleSidebarCollapsed"
          >
            <component
              :is="sidebarToggleIcon"
              class="sidebar-toggle-icon"
              :data-test="sidebarCollapsed ? 'sidebar-toggle-icon-expand' : 'sidebar-toggle-icon-fold'"
            />
          </button>
        </el-tooltip>
      </div>
      <el-scrollbar class="side-menu-scroll">
        <el-menu
          :default-active="sideMenuActivePath"
          :collapse="sidebarCollapsed"
          :collapse-transition="false"
          router
          class="side-menu"
          unique-opened
        >
          <el-menu-item index="/">
            <House class="side-menu-icon" data-test="main-menu-icon-home" />
            <span>工作台</span>
          </el-menu-item>
          <template v-for="menu in menuTree" :key="menu.code">
            <el-sub-menu v-if="hasChildren(menu)" :index="menuIndex(menu)">
              <template #title>
                <component
                  :is="mainMenuIcon(menu)"
                  class="side-menu-icon"
                  :data-test="`main-menu-icon-${mainMenuIconKey(menu)}`"
                />
                <span>{{ menu.name }}</span>
              </template>
              <el-menu-item
                v-for="child in menu.children"
                :key="child.code"
                :index="menuIndex(child)"
              >
                {{ child.name }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else-if="menu.routePath" :index="menu.routePath">
              <component
                :is="mainMenuIcon(menu)"
                class="side-menu-icon"
                :data-test="`main-menu-icon-${mainMenuIconKey(menu)}`"
              />
              <span>{{ menu.name }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div>
          <strong>QH ERP</strong>
          <span>账号与权限基础</span>
        </div>
        <div class="header-user">
          <span>{{ displayName }}</span>
          <el-button
            data-test="logout-button"
            link
            type="primary"
            :loading="logoutLoading"
            :disabled="logoutLoading"
            @click="logout"
          >
            {{ logoutLoading ? '退出中' : '退出' }}
          </el-button>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
