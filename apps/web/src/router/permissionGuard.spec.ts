import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSession, UserProfile } from '../shared/api/accountPermissionApi'
import { useAuthStore } from '../stores/authStore'
import BusinessPeriodListView from '../modules/system/businessPeriods/BusinessPeriodListView.vue'
import MaterialCategoryView from '../modules/materials/categories/MaterialCategoryView.vue'
import MaterialItemListView from '../modules/materials/items/MaterialItemListView.vue'
import BomListView from '../modules/materials/boms/BomListView.vue'
import InventoryBalanceListView from '../modules/inventory/InventoryBalanceListView.vue'
import InventoryDocumentDetailView from '../modules/inventory/InventoryDocumentDetailView.vue'
import InventoryDocumentFormView from '../modules/inventory/InventoryDocumentFormView.vue'
import InventoryDocumentListView from '../modules/inventory/InventoryDocumentListView.vue'
import InventoryMovementListView from '../modules/inventory/InventoryMovementListView.vue'
import QualityInspectionListView from '../modules/quality/QualityInspectionListView.vue'
import CustomerListView from '../modules/master/customers/CustomerListView.vue'
import SupplierListView from '../modules/master/suppliers/SupplierListView.vue'
import UnitListView from '../modules/master/units/UnitListView.vue'
import UnitConversionListView from '../modules/master/unitConversions/UnitConversionListView.vue'
import CodingRuleListView from '../modules/master/codingRules/CodingRuleListView.vue'
import WarehouseListView from '../modules/master/warehouses/WarehouseListView.vue'
import ApprovalCenterView from '../modules/platform/approvals/ApprovalCenterView.vue'
import MessageCenterView from '../modules/platform/messages/MessageCenterView.vue'
import DocumentTaskCenterView from '../modules/platform/documentTasks/DocumentTaskCenterView.vue'
import ProductionCompletionReceiptView from '../modules/production/ProductionCompletionReceiptView.vue'
import ProductionMaterialIssueView from '../modules/production/ProductionMaterialIssueView.vue'
import ProductionWorkOrderDetailView from '../modules/production/ProductionWorkOrderDetailView.vue'
import ProductionWorkOrderFormView from '../modules/production/ProductionWorkOrderFormView.vue'
import ProductionWorkOrderListView from '../modules/production/ProductionWorkOrderListView.vue'
import ProductionWorkReportView from '../modules/production/ProductionWorkReportView.vue'
import ProductionMaterialReturnDetailView from '../modules/reversal/ProductionMaterialReturnDetailView.vue'
import ProductionMaterialReturnFormView from '../modules/reversal/ProductionMaterialReturnFormView.vue'
import ProductionMaterialReturnListView from '../modules/reversal/ProductionMaterialReturnListView.vue'
import ProductionMaterialSupplementDetailView from '../modules/reversal/ProductionMaterialSupplementDetailView.vue'
import ProductionMaterialSupplementFormView from '../modules/reversal/ProductionMaterialSupplementFormView.vue'
import ProductionMaterialSupplementListView from '../modules/reversal/ProductionMaterialSupplementListView.vue'
import CostRecordDetailView from '../modules/cost/CostRecordDetailView.vue'
import CostRecordFormView from '../modules/cost/CostRecordFormView.vue'
import CostRecordListView from '../modules/cost/CostRecordListView.vue'
import PurchaseOrderDetailView from '../modules/procurement/PurchaseOrderDetailView.vue'
import PurchaseOrderFormView from '../modules/procurement/PurchaseOrderFormView.vue'
import PurchaseOrderListView from '../modules/procurement/PurchaseOrderListView.vue'
import PurchaseReceiptDetailView from '../modules/procurement/PurchaseReceiptDetailView.vue'
import PurchaseReceiptFormView from '../modules/procurement/PurchaseReceiptFormView.vue'
import PurchaseReceiptListView from '../modules/procurement/PurchaseReceiptListView.vue'
import PurchaseReturnDetailView from '../modules/reversal/PurchaseReturnDetailView.vue'
import PurchaseReturnFormView from '../modules/reversal/PurchaseReturnFormView.vue'
import PurchaseReturnListView from '../modules/reversal/PurchaseReturnListView.vue'
import SalesOrderDetailView from '../modules/sales/SalesOrderDetailView.vue'
import SalesOrderFormView from '../modules/sales/SalesOrderFormView.vue'
import SalesOrderListView from '../modules/sales/SalesOrderListView.vue'
import SalesProjectDetailView from '../modules/sales/projects/SalesProjectDetailView.vue'
import SalesProjectFormView from '../modules/sales/projects/SalesProjectFormView.vue'
import SalesProjectListView from '../modules/sales/projects/SalesProjectListView.vue'
import SalesShipmentDetailView from '../modules/sales/SalesShipmentDetailView.vue'
import SalesShipmentFormView from '../modules/sales/SalesShipmentFormView.vue'
import SalesShipmentListView from '../modules/sales/SalesShipmentListView.vue'
import SalesReturnDetailView from '../modules/reversal/SalesReturnDetailView.vue'
import SalesReturnFormView from '../modules/reversal/SalesReturnFormView.vue'
import SalesReturnListView from '../modules/reversal/SalesReturnListView.vue'
import ReceivableListView from '../modules/finance/ReceivableListView.vue'
import ReceivableFormView from '../modules/finance/ReceivableFormView.vue'
import ReceivableDetailView from '../modules/finance/ReceivableDetailView.vue'
import ReceiptListView from '../modules/finance/ReceiptListView.vue'
import ReceiptFormView from '../modules/finance/ReceiptFormView.vue'
import ReceiptDetailView from '../modules/finance/ReceiptDetailView.vue'
import PayableListView from '../modules/finance/PayableListView.vue'
import PayableFormView from '../modules/finance/PayableFormView.vue'
import PayableDetailView from '../modules/finance/PayableDetailView.vue'
import PaymentListView from '../modules/finance/PaymentListView.vue'
import PaymentFormView from '../modules/finance/PaymentFormView.vue'
import PaymentDetailView from '../modules/finance/PaymentDetailView.vue'
import SettlementAdjustmentDetailView from '../modules/reversal/SettlementAdjustmentDetailView.vue'
import SettlementAdjustmentFormView from '../modules/reversal/SettlementAdjustmentFormView.vue'
import SettlementAdjustmentListView from '../modules/reversal/SettlementAdjustmentListView.vue'
import { reportRouteConfigs, reportPermissions } from '../modules/reports/reportPageHelpers'
import ReportOverviewView from '../modules/reports/ReportOverviewView.vue'
import SalesReportView from '../modules/reports/SalesReportView.vue'
import ProcurementReportView from '../modules/reports/ProcurementReportView.vue'
import InventoryReportView from '../modules/reports/InventoryReportView.vue'
import ProductionReportView from '../modules/reports/ProductionReportView.vue'
import CostReportView from '../modules/reports/CostReportView.vue'
import SettlementReportView from '../modules/reports/SettlementReportView.vue'
import ExceptionReportView from '../modules/reports/ExceptionReportView.vue'
import { createQhErpRouter } from './index'

const user: UserProfile = { id: '1', username: 'admin', displayName: '管理员', status: 'ENABLED' }
const adminSession: AuthSession = {
  user,
  roles: [{ id: 'role-1', code: 'SYSTEM_ADMIN', name: '系统管理员', status: 'ENABLED' }],
  menus: [{ id: 'menu-1', code: 'system:user:view', name: '用户管理', type: 'MENU', routePath: '/accounts/users' }],
  permissions: ['system:user:view'],
}

function apiResponse<T>(data: T) {
  return {
    ok: true,
    json: async () => ({
      success: true,
      code: 'OK',
      message: '成功',
      data,
      traceId: 'trace-id',
      timestamp: '2026-07-02T00:00:00+08:00',
    }),
  } as Response
}

describe('账号权限路由守卫', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('未登录访问受保护路由时跳转登录页并保留来源地址', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('未登录')))
    const router = createQhErpRouter()

    await router.push('/accounts/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/accounts/users')
  })

  it('根路径工作台占位组件不依赖运行时 template 编译', async () => {
    const router = createQhErpRouter()

    await router.push('/')
    await router.isReady()

    const homeComponent = router.currentRoute.value.matched[0].components?.default as { render?: unknown; template?: unknown }
    expect(homeComponent.template).toBeUndefined()
    expect(homeComponent.render).toBeTypeOf('function')
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('业务期间路由加载真实页面并配置系统期间查看权限', async () => {
    const router = createQhErpRouter()
    const route = router.getRoutes().find((item) => item.name === 'system-business-periods')
    const component = route?.components?.default as (() => Promise<unknown>) | undefined

    expect(route?.path).toBe('/system/business-periods')
    expect(route?.meta.requiresAuth).toBe(true)
    expect(route?.meta.requiredPermission).toBe('system:business-period:view')
    expect(component).toBeTypeOf('function')
    await expect(component?.()).resolves.toHaveProperty('default', BusinessPeriodListView)
  })

  it('基础资料和物料路由加载真实页面', async () => {
    const router = createQhErpRouter()
    const realMasterRoutes = [
      ['master-units', UnitListView],
      ['master-unit-conversions', UnitConversionListView],
      ['master-coding-rules', CodingRuleListView],
      ['master-warehouses', WarehouseListView],
      ['master-suppliers', SupplierListView],
      ['master-customers', CustomerListView],
      ['material-categories', MaterialCategoryView],
      ['material-items', MaterialItemListView],
      ['material-boms', BomListView],
    ] as const

    for (const [routeName, expectedComponent] of realMasterRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }

    expect(router.getRoutes().find((item) => item.name === 'master-unit-conversions')?.path)
      .toBe('/master/unit-conversions')
    expect(router.getRoutes().find((item) => item.name === 'master-unit-conversions')?.meta.requiredPermission)
      .toBe('master:unit-conversion:view')
    expect(router.getRoutes().find((item) => item.name === 'master-coding-rules')?.path)
      .toBe('/master/coding-rules')
    expect(router.getRoutes().find((item) => item.name === 'master-coding-rules')?.meta.requiredPermission)
      .toBe('master:coding-rule:view')
    expect(router.getRoutes().find((item) => item.name === 'material-categories')?.meta.requiredPermission)
      .toBe('master:material-category:view')
    expect(router.getRoutes().find((item) => item.name === 'material-items')?.meta.requiredPermission)
      .toBe('master:material:view')
    expect(router.getRoutes().find((item) => item.name === 'material-boms')?.meta.requiredPermission)
      .toBe('material:bom:view')
  })

  it('平台工作台路由加载真实页面并配置固定权限', async () => {
    const router = createQhErpRouter()
    const platformRoutes = [
      ['platform-approvals', '/platform/approvals', 'platform:todo:view', ApprovalCenterView],
      ['platform-messages', '/platform/messages', 'platform:message:view', MessageCenterView],
      ['platform-document-tasks', '/platform/document-tasks', 'platform:document-task:view', DocumentTaskCenterView],
    ] as const

    for (const [routeName, path, permission, expectedComponent] of platformRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(route?.path).toBe(path)
      expect(route?.meta.requiresAuth).toBe(true)
      expect(route?.meta.requiredPermission).toBe(permission)
      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }
  })

  it('库存路由加载真实页面并配置对应权限', async () => {
    const router = createQhErpRouter()
    const inventoryRoutes = [
      ['inventory-balances', '/inventory/balances', 'inventory:balance:view', InventoryBalanceListView],
      ['inventory-movements', '/inventory/movements', 'inventory:movement:view', InventoryMovementListView],
      ['inventory-documents', '/inventory/documents', 'inventory:document:view', InventoryDocumentListView],
      ['inventory-document-create', '/inventory/documents/create', 'inventory:document:create', InventoryDocumentFormView],
      ['inventory-document-detail', '/inventory/documents/:id', 'inventory:document:view', InventoryDocumentDetailView],
      ['inventory-document-edit', '/inventory/documents/:id/edit', 'inventory:document:update', InventoryDocumentFormView],
    ] as const

    for (const [routeName, path, permission, expectedComponent] of inventoryRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(route?.path).toBe(path)
      expect(route?.meta.requiredPermission).toBe(permission)
      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }
  })

  it('质量确认路由加载真实页面并配置质量查看权限', async () => {
    const router = createQhErpRouter()
    const route = router.getRoutes().find((item) => item.name === 'quality-inspections')
    const component = route?.components?.default as (() => Promise<unknown>) | undefined

    expect(route?.path).toBe('/quality/inspections')
    expect(route?.meta.requiresAuth).toBe(true)
    expect(route?.meta.requiredPermission).toBe('quality:inspection:view')
    expect(component).toBeTypeOf('function')
    await expect(component?.()).resolves.toHaveProperty('default', QualityInspectionListView)
  })

  it('生产路由加载真实页面并配置对应权限', async () => {
    const router = createQhErpRouter()
    const productionRoutes = [
      ['production-work-orders', '/production/work-orders', 'production:work-order:view', ProductionWorkOrderListView],
      ['production-work-order-create', '/production/work-orders/create', 'production:work-order:create', ProductionWorkOrderFormView],
      ['production-work-order-detail', '/production/work-orders/:id', 'production:work-order:view', ProductionWorkOrderDetailView],
      ['production-work-order-edit', '/production/work-orders/:id/edit', 'production:work-order:update', ProductionWorkOrderFormView],
      [
        'production-work-order-material-issues',
        '/production/work-orders/:id/material-issues',
        'production:issue:view',
        ProductionMaterialIssueView,
      ],
      ['production-work-order-reports', '/production/work-orders/:id/reports', 'production:report:view', ProductionWorkReportView],
      [
        'production-work-order-completion-receipts',
        '/production/work-orders/:id/completion-receipts',
        'production:receipt:view',
        ProductionCompletionReceiptView,
      ],
      [
        'production-material-returns',
        '/production/material-returns',
        'production:material-return:view',
        ProductionMaterialReturnListView,
      ],
      [
        'production-material-return-create',
        '/production/material-returns/create',
        'production:material-return:create',
        ProductionMaterialReturnFormView,
      ],
      [
        'production-material-return-detail',
        '/production/material-returns/:id',
        'production:material-return:view',
        ProductionMaterialReturnDetailView,
      ],
      [
        'production-material-return-edit',
        '/production/material-returns/:id/edit',
        'production:material-return:update',
        ProductionMaterialReturnFormView,
      ],
      [
        'production-material-supplements',
        '/production/material-supplements',
        'production:material-supplement:view',
        ProductionMaterialSupplementListView,
      ],
      [
        'production-material-supplement-create',
        '/production/material-supplements/create',
        'production:material-supplement:create',
        ProductionMaterialSupplementFormView,
      ],
      [
        'production-material-supplement-detail',
        '/production/material-supplements/:id',
        'production:material-supplement:view',
        ProductionMaterialSupplementDetailView,
      ],
      [
        'production-material-supplement-edit',
        '/production/material-supplements/:id/edit',
        'production:material-supplement:update',
        ProductionMaterialSupplementFormView,
      ],
    ] as const

    for (const [routeName, path, permission, expectedComponent] of productionRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(route?.path).toBe(path)
      expect(route?.meta.requiredPermission).toBe(permission)
      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }
  })

  it('成本路由加载真实页面并配置对应权限', async () => {
    const router = createQhErpRouter()
    const costRoutes = [
      ['cost-records', '/cost/records', 'cost:record:view', CostRecordListView],
      ['cost-record-create', '/cost/records/create', 'cost:record:create', CostRecordFormView],
      ['cost-record-detail', '/cost/records/:id', 'cost:record:view', CostRecordDetailView],
      ['cost-record-edit', '/cost/records/:id/edit', 'cost:record:update', CostRecordFormView],
    ] as const

    for (const [routeName, path, permission, expectedComponent] of costRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(route?.path).toBe(path)
      expect(route?.meta.requiredPermission).toBe(permission)
      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }
  })

  it('采购订单、采购入库和采购退货路由加载真实页面并配置对应权限', async () => {
    const router = createQhErpRouter()
    const procurementRoutes = [
      ['procurement-orders', '/procurement/orders', 'procurement:order:view', PurchaseOrderListView],
      ['procurement-order-create', '/procurement/orders/create', 'procurement:order:create', PurchaseOrderFormView],
      ['procurement-order-detail', '/procurement/orders/:id', 'procurement:order:view', PurchaseOrderDetailView],
      ['procurement-order-edit', '/procurement/orders/:id/edit', 'procurement:order:update', PurchaseOrderFormView],
      ['procurement-receipts', '/procurement/receipts', 'procurement:receipt:view', PurchaseReceiptListView],
      [
        'procurement-receipt-create',
        '/procurement/orders/:orderId/receipts/create',
        'procurement:receipt:create',
        PurchaseReceiptFormView,
      ],
      ['procurement-receipt-detail', '/procurement/receipts/:id', 'procurement:receipt:view', PurchaseReceiptDetailView],
      ['procurement-receipt-edit', '/procurement/receipts/:id/edit', 'procurement:receipt:update', PurchaseReceiptFormView],
      ['procurement-returns', '/procurement/returns', 'procurement:return:view', PurchaseReturnListView],
      ['procurement-return-create', '/procurement/returns/create', 'procurement:return:create', PurchaseReturnFormView],
      ['procurement-return-detail', '/procurement/returns/:id', 'procurement:return:view', PurchaseReturnDetailView],
      ['procurement-return-edit', '/procurement/returns/:id/edit', 'procurement:return:update', PurchaseReturnFormView],
    ] as const

    for (const [routeName, path, permission, expectedComponent] of procurementRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(route?.path).toBe(path)
      expect(route?.meta.requiredPermission).toBe(permission)
      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }

    const rootRoute = router.getRoutes().find((item) => item.path === '/procurement')
    expect(rootRoute?.redirect).toBe('/procurement/orders')
    expect(rootRoute?.meta.requiresAuth).toBe(true)
    expect(rootRoute?.meta.requiredPermission).toBe('procurement:order:view')
  })

  it('销售订单、销售出库和销售退货路由加载真实页面，并配置对应权限', async () => {
    const router = createQhErpRouter()
    const salesRoutes = [
      ['sales-orders', '/sales/orders', 'sales:order:view', SalesOrderListView],
      ['sales-order-create', '/sales/orders/create', 'sales:order:create', SalesOrderFormView],
      ['sales-order-detail', '/sales/orders/:id', 'sales:order:view', SalesOrderDetailView],
      ['sales-order-edit', '/sales/orders/:id/edit', 'sales:order:update', SalesOrderFormView],
      ['sales-shipment-create', '/sales/orders/:orderId/shipments/create', 'sales:shipment:create', SalesShipmentFormView],
      ['sales-shipments', '/sales/shipments', 'sales:shipment:view', SalesShipmentListView],
      ['sales-shipment-detail', '/sales/shipments/:id', 'sales:shipment:view', SalesShipmentDetailView],
      ['sales-shipment-edit', '/sales/shipments/:id/edit', 'sales:shipment:update', SalesShipmentFormView],
      ['sales-returns', '/sales/returns', 'sales:return:view', SalesReturnListView],
      ['sales-return-create', '/sales/returns/create', 'sales:return:create', SalesReturnFormView],
      ['sales-return-detail', '/sales/returns/:id', 'sales:return:view', SalesReturnDetailView],
      ['sales-return-edit', '/sales/returns/:id/edit', 'sales:return:update', SalesReturnFormView],
    ] as const

    for (const [routeName, path, permission, expectedComponent] of salesRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(route?.path).toBe(path)
      expect(route?.meta.requiresAuth).toBe(true)
      expect(route?.meta.requiredPermission).toBe(permission)
      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }

    const rootRoute = router.getRoutes().find((item) => item.path === '/sales')
    expect(rootRoute?.name).toBe('sales-root')
    expect(rootRoute?.meta.requiresAuth).toBe(true)
    expect(rootRoute?.meta.requiredPermission).toBeUndefined()
  })

  it('财务往来路由加载基础占位页面并配置对应权限', async () => {
    const router = createQhErpRouter()
    const financeRoutes = [
      ['finance-receivables', '/finance/receivables', 'finance:receivable:view', ReceivableListView],
      ['finance-receivable-create', '/finance/receivables/create', 'finance:receivable:create', ReceivableFormView],
      ['finance-receivable-detail', '/finance/receivables/:id', 'finance:receivable:view', ReceivableDetailView],
      ['finance-receivable-edit', '/finance/receivables/:id/edit', 'finance:receivable:update', ReceivableFormView],
      ['finance-receipt-create', '/finance/receivables/:id/receipts/create', 'finance:receipt:create', ReceiptFormView],
      ['finance-receipts', '/finance/receipts', 'finance:receipt:view', ReceiptListView],
      ['finance-receipt-detail', '/finance/receipts/:id', 'finance:receipt:view', ReceiptDetailView],
      ['finance-receipt-edit', '/finance/receipts/:id/edit', 'finance:receipt:update', ReceiptFormView],
      ['finance-payables', '/finance/payables', 'finance:payable:view', PayableListView],
      ['finance-payable-create', '/finance/payables/create', 'finance:payable:create', PayableFormView],
      ['finance-payable-detail', '/finance/payables/:id', 'finance:payable:view', PayableDetailView],
      ['finance-payable-edit', '/finance/payables/:id/edit', 'finance:payable:update', PayableFormView],
      ['finance-payment-create', '/finance/payables/:id/payments/create', 'finance:payment:create', PaymentFormView],
      ['finance-payments', '/finance/payments', 'finance:payment:view', PaymentListView],
      ['finance-payment-detail', '/finance/payments/:id', 'finance:payment:view', PaymentDetailView],
      ['finance-payment-edit', '/finance/payments/:id/edit', 'finance:payment:update', PaymentFormView],
      [
        'finance-settlement-adjustments',
        '/finance/settlement-adjustments',
        'finance:settlement-adjustment:view',
        SettlementAdjustmentListView,
      ],
      [
        'finance-settlement-adjustment-create',
        '/finance/settlement-adjustments/create',
        'finance:settlement-adjustment:create',
        SettlementAdjustmentFormView,
      ],
      [
        'finance-settlement-adjustment-detail',
        '/finance/settlement-adjustments/:id',
        'finance:settlement-adjustment:view',
        SettlementAdjustmentDetailView,
      ],
      [
        'finance-settlement-adjustment-edit',
        '/finance/settlement-adjustments/:id/edit',
        'finance:settlement-adjustment:update',
        SettlementAdjustmentFormView,
      ],
    ] as const

    for (const [routeName, path, permission, expectedComponent] of financeRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(route?.path).toBe(path)
      expect(route?.meta.requiresAuth).toBe(true)
      expect(route?.meta.requiredPermission).toBe(permission)
      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }

    const rootRoute = router.getRoutes().find((item) => item.path === '/finance')
    expect(rootRoute?.meta.requiresAuth).toBe(true)
  })

  it('经营报表路由加载真实页面并配置对应权限', async () => {
    const router = createQhErpRouter()
    const reportRoutes = [
      ['reports-overview', ReportOverviewView],
      ['reports-sales', SalesReportView],
      ['reports-procurement', ProcurementReportView],
      ['reports-inventory', InventoryReportView],
      ['reports-production', ProductionReportView],
      ['reports-cost', CostReportView],
      ['reports-settlement', SettlementReportView],
      ['reports-exceptions', ExceptionReportView],
    ] as const

    for (const config of reportRouteConfigs) {
      const route = router.getRoutes().find((item) => item.name === config.routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined
      const expectedComponent = reportRoutes.find(([routeName]) => routeName === config.routeName)?.[1]

      expect(route?.path).toBe(config.path)
      expect(route?.meta.requiresAuth).toBe(true)
      expect(route?.meta.requiredPermission).toBe(config.permission)
      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }

    const rootRoute = router.getRoutes().find((item) => item.path === '/reports')
    expect(rootRoute?.name).toBe('reports-root')
    expect(rootRoute?.meta.requiresAuth).toBe(true)
  })

  it('访问库存根路径时重定向到库存余额页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['inventory:balance:view'] })

    await router.push('/inventory')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('inventory-balances')
  })

  it('访问成本根路径时重定向到成本记录页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['cost:record:view'] })

    await router.push('/cost')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('cost-records')
  })

  it('访问采购根路径时重定向到采购订单页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['procurement:order:view'] })

    await router.push('/procurement')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('procurement-orders')
  })

  it('访问销售根路径时重定向到销售订单页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['sales:order:view'] })

    await router.push('/sales')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('sales-orders')
  })

  it('访问财务根路径时按首个可用财务查看权限动态重定向', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['finance:receivable:view', 'finance:payable:view'] })

    await router.push('/finance')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('finance-receivables')
  })

  it('访问经营报表根路径时按首个可用报表权限动态重定向', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [reportPermissions.salesView, reportPermissions.inventoryView] })

    await router.push('/reports')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('reports-sales')
  })

  it('仅有经营异常权限时访问经营报表根路径进入异常清单', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [reportPermissions.exceptionView] })

    await router.push('/reports')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('reports-exceptions')
  })

  it('无任一报表查看权限时访问经营报表根路径进入无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [] })

    await router.push('/reports')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/reports')
  })

  it('仅有应付查看权限时访问财务根路径进入应付台账', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['finance:payable:view'] })

    await router.push('/finance')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('finance-payables')
  })

  it('仅有往来冲减查看权限时访问财务根路径进入往来冲减', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['finance:settlement-adjustment:view'] })

    await router.push('/finance')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('finance-settlement-adjustments')
  })

  it('无任一财务查看权限时访问财务根路径进入无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [] })

    await router.push('/finance')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/finance')
  })

  it('store 为空但后端 session 有效时访问受保护路由会恢复会话并放行', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(apiResponse(adminSession)))
    const router = createQhErpRouter()

    await router.push('/accounts/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('system-users')
    expect(useAuthStore().currentUser?.username).toBe('admin')
  })

  it('store 为空但后端 session 有效时访问登录页会恢复会话并跳转首页', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(apiResponse(adminSession)))
    const router = createQhErpRouter()

    await router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('访问登录页即使带旧退出标记也会按真实后端 session 恢复', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(apiResponse(adminSession)))
    const router = createQhErpRouter()

    await router.push({ path: '/login', query: { loggedOut: '1' } })
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('home')
    expect(useAuthStore().currentUser?.username).toBe('admin')
  })

  it('已登录访问登录页时跳转首页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['system:user:view'] })

    await router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('已登录但缺少权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [] })

    await router.push('/accounts/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('多权限路由缺少任一权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({
      user,
      menus: [],
      permissions: ['system:role:view', 'system:role:assign-permission'],
    })

    await router.push('/accounts/roles/1/permissions')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('恢复会话失败时清理旧状态并跳转登录页', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('未登录')))
    const router = createQhErpRouter()
    const store = useAuthStore()
    store.permissions = ['system:user:view']

    await router.push('/accounts/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
    expect(store.currentUser).toBeNull()
    expect(store.permissions).toEqual([])
  })

  it('恢复会话后缺少权限时仍跳转无权限页', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(apiResponse({ ...adminSession, permissions: [] })))
    const router = createQhErpRouter()

    await router.push('/accounts/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('已登录且拥有权限时允许访问目标路由', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['system:user:view'] })

    await router.push('/accounts/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('system-users')
  })

  it('已登录且缺少库存路由权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['inventory:balance:view'] })

    await router.push('/inventory/documents/create')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('已登录且拥有质量确认查看权限时允许访问质量确认列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['quality:inspection:view'] })

    await router.push('/quality/inspections')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('quality-inspections')
  })

  it('已登录但缺少质量确认查看权限时直访质量确认进入无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [] })

    await router.push('/quality/inspections')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/quality/inspections')
  })

  it('已登录且拥有生产工单查看权限时允许访问工单列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['production:work-order:view'] })

    await router.push('/production/work-orders')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('production-work-orders')
  })

  it('已登录且拥有生产退料查看权限时允许访问生产退料列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['production:material-return:view'] })

    await router.push('/production/material-returns')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('production-material-returns')
  })

  it('已登录但缺少生产退料创建权限时不能访问新建生产退料', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['production:material-return:view'] })

    await router.push('/production/material-returns/create')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/production/material-returns/create')
  })

  it('已登录且拥有生产补料查看权限时允许访问生产补料列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['production:material-supplement:view'] })

    await router.push('/production/material-supplements')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('production-material-supplements')
  })

  it('已登录但缺少生产补料创建权限时不能访问新建生产补料', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['production:material-supplement:view'] })

    await router.push('/production/material-supplements/create')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/production/material-supplements/create')
  })

  it('已登录且拥有成本记录查看权限时允许访问成本记录列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['cost:record:view'] })

    await router.push('/cost/records')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('cost-records')
  })

  it('已登录且拥有采购订单查看权限时允许访问采购订单列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['procurement:order:view'] })

    await router.push('/procurement/orders')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('procurement-orders')
  })

  it('已登录但缺少采购入库查看权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['procurement:order:view'] })

    await router.push('/procurement/receipts')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/procurement/receipts')
  })

  it('未登录访问采购路由时跳转登录页并保留来源地址', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('未登录')))
    const router = createQhErpRouter()

    await router.push('/procurement/orders')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/procurement/orders')
  })

  it('已登录且拥有销售订单查看权限时允许访问销售订单列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['sales:order:view'] })

    await router.push('/sales/orders')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('sales-orders')
  })

  it('销售项目路由加载真实页面，销售根路径按项目、订单、出库、退货顺序进入首个可见页', async () => {
    const router = createQhErpRouter()
    const projectRoutes = [
      ['sales-projects', '/sales/projects', 'sales:project:view', SalesProjectListView],
      ['sales-project-create', '/sales/projects/create', 'sales:project:create', SalesProjectFormView],
      ['sales-project-detail', '/sales/projects/:id', 'sales:project:view', SalesProjectDetailView],
      ['sales-project-edit', '/sales/projects/:id/edit', 'sales:project:update', SalesProjectFormView],
    ] as const

    for (const [routeName, path, permission, expectedComponent] of projectRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined
      expect(route?.path).toBe(path)
      expect(route?.meta.requiredPermission).toBe(permission)
      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }

    useAuthStore().setSession({ user, menus: [], permissions: ['sales:project:view'] })
    await router.push('/sales')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('sales-projects')

    const orderOnlyRouter = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['sales:order:view'] })
    await orderOnlyRouter.push('/sales')
    await orderOnlyRouter.isReady()
    expect(orderOnlyRouter.currentRoute.value.name).toBe('sales-orders')
  })

  it('已登录但缺少销售出库查看权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['sales:order:view'] })

    await router.push('/sales/shipments')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/sales/shipments')
  })

  it('已登录且拥有采购退货查看权限时允许访问采购退货列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['procurement:return:view'] })

    await router.push('/procurement/returns')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('procurement-returns')
  })

  it('已登录但缺少采购退货创建权限时不能访问新建采购退货', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['procurement:return:view'] })

    await router.push('/procurement/returns/create')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/procurement/returns/create')
  })

  it('已登录且拥有销售退货查看权限时允许访问销售退货列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['sales:return:view'] })

    await router.push('/sales/returns')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('sales-returns')
  })

  it('已登录但缺少销售退货创建权限时不能访问新建销售退货', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['sales:return:view'] })

    await router.push('/sales/returns/create')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/sales/returns/create')
  })

  it('未登录访问销售路由时跳转登录页并保留来源地址', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('未登录')))
    const router = createQhErpRouter()

    await router.push('/sales/orders')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/sales/orders')
  })

  it('已登录但缺少成本记录查看权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [] })

    await router.push('/cost/records')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/cost/records')
  })

  it('已登录但缺少财务查看权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [] })

    await router.push('/finance/receivables')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/finance/receivables')
  })

  it('已登录且拥有往来冲减查看权限时允许访问往来冲减列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['finance:settlement-adjustment:view'] })

    await router.push('/finance/settlement-adjustments')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('finance-settlement-adjustments')
  })

  it('已登录但缺少往来冲减创建权限时不能访问新建往来冲减', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['finance:settlement-adjustment:view'] })

    await router.push('/finance/settlement-adjustments/create')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/finance/settlement-adjustments/create')
  })

  it('已登录且拥有对应报表权限时允许访问经营报表子路由', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [reportPermissions.inventoryView] })

    await router.push('/reports/inventory')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('reports-inventory')
  })

  it('已登录但缺少对应报表权限时访问经营报表子路由进入无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [reportPermissions.salesView] })

    await router.push('/reports/procurement')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/reports/procurement')
  })

  it('已登录但缺少成本记录创建权限时不能访问新建路由', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['cost:record:view'] })

    await router.push('/cost/records/create')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/cost/records/create')
  })

  it('已登录但缺少生产工单查看权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [] })

    await router.push('/production/work-orders')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/production/work-orders')
  })

  it('未登录访问生产工单时跳转登录页并保留来源地址', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('未登录')))
    const router = createQhErpRouter()

    await router.push('/production/work-orders')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/production/work-orders')
  })
})
