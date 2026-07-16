import { h } from 'vue'
import { createMemoryHistory, createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { firstFinanceRouteByPermission } from '../modules/finance/financePageHelpers'
import { firstReportRouteByPermission, reportRouteConfigs } from '../modules/reports/reportPageHelpers'
import { useAuthStore } from '../stores/authStore'

const history = import.meta.env.MODE === 'test' ? createMemoryHistory() : createWebHistory()

declare module 'vue-router' {
  interface RouteMeta {
    guestOnly?: boolean
    requiresAuth?: boolean
    requiredPermission?: string
    requiredPermissions?: string[]
  }
}

const placeholder = (title: string, description: string) => ({
  render: () => h('section', [h('h1', title), h('p', description)]),
})

const reportPageComponent = (routeName: string) => {
  switch (routeName) {
    case 'reports-overview':
      return () => import('../modules/reports/ReportOverviewView.vue')
    case 'reports-sales':
      return () => import('../modules/reports/SalesReportView.vue')
    case 'reports-procurement':
      return () => import('../modules/reports/ProcurementReportView.vue')
    case 'reports-inventory':
      return () => import('../modules/reports/InventoryReportView.vue')
    case 'reports-production':
      return () => import('../modules/reports/ProductionReportView.vue')
    case 'reports-cost':
      return () => import('../modules/reports/CostReportView.vue')
    case 'reports-settlement':
      return () => import('../modules/reports/SettlementReportView.vue')
    case 'reports-exceptions':
      return () => import('../modules/reports/ExceptionReportView.vue')
    default:
      return placeholder('经营报表', '经营报表页面。')
  }
}

const salesRouteOrder = [
  { name: 'sales-projects', permission: 'sales:project:view' },
  { name: 'sales-quotes', permission: 'sales:quote:view' },
  { name: 'sales-orders', permission: 'sales:order:view' },
  { name: 'sales-delivery-plans', permission: 'sales:delivery-plan:view' },
  { name: 'sales-shipments', permission: 'sales:shipment:view' },
  { name: 'sales-returns', permission: 'sales:return:view' },
  { name: 'sales-credit-profiles', permission: 'sales:credit:view' },
  { name: 'sales-effective-demands', permission: 'sales:effective-demand:view' },
] as const

const procurementRouteOrder = [
  { name: 'procurement-requisitions', permission: 'procurement:requisition:view' },
  { name: 'procurement-inquiries', permission: 'procurement:inquiry:view' },
  { name: 'procurement-price-agreements', permission: 'procurement:price-agreement:view' },
  { name: 'procurement-orders', permission: 'procurement:order:view' },
  { name: 'procurement-receipts', permission: 'procurement:receipt:view' },
  { name: 'procurement-returns', permission: 'procurement:return:view' },
  { name: 'procurement-effective-supplies', permission: 'procurement:supply:view' },
] as const

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    meta: { guestOnly: true },
    component: () => import('../modules/auth/LoginView.vue'),
  },
  {
    path: '/forbidden',
    name: 'forbidden',
    component: placeholder('无权限访问', '当前账号没有访问该功能的权限。'),
  },
  {
    path: '/',
    name: 'home',
    component: placeholder('工作台', '账号与权限基础已接入，后续承接物料、BOM、库存和生产模块。'),
  },
  {
    path: '/accounts',
    name: 'accounts',
    meta: { requiresAuth: true },
    component: placeholder('账号权限', '账号、角色、菜单和操作权限基础入口。'),
  },
  {
    path: '/accounts/users',
    name: 'system-users',
    alias: '/system/users',
    meta: { requiresAuth: true, requiredPermission: 'system:user:view' },
    component: () => import('../modules/system/users/UserListView.vue'),
  },
  {
    path: '/accounts/roles',
    name: 'system-roles',
    alias: '/system/roles',
    meta: { requiresAuth: true, requiredPermission: 'system:role:view' },
    component: () => import('../modules/system/roles/RoleListView.vue'),
  },
  {
    path: '/accounts/roles/:id/permissions',
    name: 'system-role-permissions',
    alias: '/system/roles/:id/permissions',
    meta: {
      requiresAuth: true,
      requiredPermissions: ['system:role:view', 'system:permission:view', 'system:role:assign-permission'],
    },
    component: () => import('../modules/system/roles/RolePermissionView.vue'),
  },
  {
    path: '/system/business-periods',
    name: 'system-business-periods',
    meta: { requiresAuth: true, requiredPermission: 'system:business-period:view' },
    component: () => import('../modules/system/businessPeriods/BusinessPeriodListView.vue'),
  },
  {
    path: '/master/units',
    name: 'master-units',
    meta: { requiresAuth: true, requiredPermission: 'master:unit:view' },
    component: () => import('../modules/master/units/UnitListView.vue'),
  },
  {
    path: '/master/unit-conversions',
    name: 'master-unit-conversions',
    meta: { requiresAuth: true, requiredPermission: 'master:unit-conversion:view' },
    component: () => import('../modules/master/unitConversions/UnitConversionListView.vue'),
  },
  {
    path: '/master/coding-rules',
    name: 'master-coding-rules',
    meta: { requiresAuth: true, requiredPermission: 'master:coding-rule:view' },
    component: () => import('../modules/master/codingRules/CodingRuleListView.vue'),
  },
  {
    path: '/platform/approvals',
    name: 'platform-approvals',
    meta: { requiresAuth: true, requiredPermission: 'platform:todo:view' },
    component: () => import('../modules/platform/approvals/ApprovalCenterView.vue'),
  },
  {
    path: '/platform/messages',
    name: 'platform-messages',
    meta: { requiresAuth: true, requiredPermission: 'platform:message:view' },
    component: () => import('../modules/platform/messages/MessageCenterView.vue'),
  },
  {
    path: '/platform/document-tasks',
    name: 'platform-document-tasks',
    meta: { requiresAuth: true, requiredPermission: 'platform:document-task:view' },
    component: () => import('../modules/platform/documentTasks/DocumentTaskCenterView.vue'),
  },
  {
    path: '/master/warehouses',
    name: 'master-warehouses',
    meta: { requiresAuth: true, requiredPermission: 'master:warehouse:view' },
    component: () => import('../modules/master/warehouses/WarehouseListView.vue'),
  },
  {
    path: '/master/suppliers',
    name: 'master-suppliers',
    meta: { requiresAuth: true, requiredPermission: 'master:supplier:view' },
    component: () => import('../modules/master/suppliers/SupplierListView.vue'),
  },
  {
    path: '/master/customers',
    name: 'master-customers',
    meta: { requiresAuth: true, requiredPermission: 'master:customer:view' },
    component: () => import('../modules/master/customers/CustomerListView.vue'),
  },
  {
    path: '/materials',
    name: 'materials',
    component: placeholder('物料管理', '物料、单位、分类和 BOM 前置资料入口。'),
  },
  {
    path: '/materials/categories',
    name: 'material-categories',
    meta: { requiresAuth: true, requiredPermission: 'master:material-category:view' },
    component: () => import('../modules/materials/categories/MaterialCategoryView.vue'),
  },
  {
    path: '/materials/items',
    name: 'material-items',
    meta: { requiresAuth: true, requiredPermission: 'master:material:view' },
    component: () => import('../modules/materials/items/MaterialItemListView.vue'),
  },
  {
    path: '/materials/boms',
    name: 'material-boms',
    meta: { requiresAuth: true, requiredPermission: 'material:bom:view' },
    component: () => import('../modules/materials/boms/BomListView.vue'),
  },
  {
    path: '/inventory',
    redirect: '/inventory/balances',
  },
  {
    path: '/inventory/balances',
    name: 'inventory-balances',
    meta: { requiresAuth: true, requiredPermission: 'inventory:balance:view' },
    component: () => import('../modules/inventory/InventoryBalanceListView.vue'),
  },
  {
    path: '/inventory/movements',
    name: 'inventory-movements',
    meta: { requiresAuth: true, requiredPermission: 'inventory:movement:view' },
    component: () => import('../modules/inventory/InventoryMovementListView.vue'),
  },
  {
    path: '/inventory/documents',
    name: 'inventory-documents',
    meta: { requiresAuth: true, requiredPermission: 'inventory:document:view' },
    component: () => import('../modules/inventory/InventoryDocumentListView.vue'),
  },
  {
    path: '/inventory/documents/create',
    name: 'inventory-document-create',
    meta: { requiresAuth: true, requiredPermission: 'inventory:document:create' },
    component: () => import('../modules/inventory/InventoryDocumentFormView.vue'),
  },
  {
    path: '/inventory/documents/:id',
    name: 'inventory-document-detail',
    meta: { requiresAuth: true, requiredPermission: 'inventory:document:view' },
    component: () => import('../modules/inventory/InventoryDocumentDetailView.vue'),
  },
  {
    path: '/inventory/documents/:id/edit',
    name: 'inventory-document-edit',
    meta: { requiresAuth: true, requiredPermission: 'inventory:document:update' },
    component: () => import('../modules/inventory/InventoryDocumentFormView.vue'),
  },
  {
    path: '/inventory/warehouse-transfers',
    name: 'inventory-warehouse-transfers',
    meta: { requiresAuth: true, requiredPermission: 'inventory:warehouse-transfer:view' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/warehouse-transfers/create',
    name: 'inventory-warehouse-transfer-create',
    meta: { requiresAuth: true, requiredPermission: 'inventory:warehouse-transfer:create' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/warehouse-transfers/:id',
    name: 'inventory-warehouse-transfer-detail',
    meta: { requiresAuth: true, requiredPermission: 'inventory:warehouse-transfer:view' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/warehouse-transfers/:id/edit',
    name: 'inventory-warehouse-transfer-edit',
    meta: { requiresAuth: true, requiredPermission: 'inventory:warehouse-transfer:update' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/ownership-conversions',
    name: 'inventory-ownership-conversions',
    meta: { requiresAuth: true, requiredPermission: 'inventory:ownership-conversion:view' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/ownership-conversions/create',
    name: 'inventory-ownership-conversion-create',
    meta: { requiresAuth: true, requiredPermission: 'inventory:ownership-conversion:create' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/ownership-conversions/:id',
    name: 'inventory-ownership-conversion-detail',
    meta: { requiresAuth: true, requiredPermission: 'inventory:ownership-conversion:view' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/ownership-conversions/:id/edit',
    name: 'inventory-ownership-conversion-edit',
    meta: { requiresAuth: true, requiredPermission: 'inventory:ownership-conversion:update' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/stocktakes',
    name: 'inventory-stocktakes',
    meta: { requiresAuth: true, requiredPermission: 'inventory:stocktake:view' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/stocktakes/create',
    name: 'inventory-stocktake-create',
    meta: { requiresAuth: true, requiredPermission: 'inventory:stocktake:create' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/stocktakes/:id',
    name: 'inventory-stocktake-detail',
    meta: { requiresAuth: true, requiredPermission: 'inventory:stocktake:view' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/stocktakes/:id/edit',
    name: 'inventory-stocktake-edit',
    meta: { requiresAuth: true, requiredPermission: 'inventory:stocktake:update' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/valuation-adjustments',
    name: 'inventory-valuation-adjustments',
    meta: { requiresAuth: true, requiredPermission: 'inventory:valuation-adjustment:view' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/valuation-adjustments/create',
    name: 'inventory-valuation-adjustment-create',
    meta: { requiresAuth: true, requiredPermission: 'inventory:valuation-adjustment:create' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/valuation-adjustments/:id',
    name: 'inventory-valuation-adjustment-detail',
    meta: { requiresAuth: true, requiredPermission: 'inventory:valuation-adjustment:view' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/inventory/valuation-adjustments/:id/edit',
    name: 'inventory-valuation-adjustment-edit',
    meta: { requiresAuth: true, requiredPermission: 'inventory:valuation-adjustment:update' },
    component: () => import('../modules/inventory/InventoryControlledDocumentView.vue'),
  },
  {
    path: '/procurement',
    name: 'procurement-root',
    meta: { requiresAuth: true },
    component: placeholder('采购管理', '采购请购、询价比价、价格协议、采购订单、入库、退货和有效供给入口。'),
  },
  {
    path: '/procurement/requisitions',
    name: 'procurement-requisitions',
    meta: { requiresAuth: true, requiredPermission: 'procurement:requisition:view' },
    component: () => import('../modules/procurement/PurchaseRequisitionListView.vue'),
  },
  {
    path: '/procurement/requisitions/create',
    name: 'procurement-requisition-create',
    meta: { requiresAuth: true, requiredPermission: 'procurement:requisition:create' },
    component: () => import('../modules/procurement/PurchaseRequisitionFormView.vue'),
  },
  {
    path: '/procurement/requisitions/:id/edit',
    name: 'procurement-requisition-edit',
    meta: { requiresAuth: true, requiredPermission: 'procurement:requisition:update' },
    component: () => import('../modules/procurement/PurchaseRequisitionFormView.vue'),
  },
  {
    path: '/procurement/requisitions/:id',
    name: 'procurement-requisition-detail',
    meta: { requiresAuth: true, requiredPermission: 'procurement:requisition:view' },
    component: () => import('../modules/procurement/PurchaseRequisitionDetailView.vue'),
  },
  {
    path: '/procurement/inquiries',
    name: 'procurement-inquiries',
    meta: { requiresAuth: true, requiredPermission: 'procurement:inquiry:view' },
    component: () => import('../modules/procurement/PurchaseInquiryListView.vue'),
  },
  {
    path: '/procurement/inquiries/create',
    name: 'procurement-inquiry-create',
    meta: { requiresAuth: true, requiredPermission: 'procurement:inquiry:create' },
    component: () => import('../modules/procurement/PurchaseInquiryFormView.vue'),
  },
  {
    path: '/procurement/inquiries/:id/edit',
    name: 'procurement-inquiry-edit',
    meta: { requiresAuth: true, requiredPermission: 'procurement:inquiry:update' },
    component: () => import('../modules/procurement/PurchaseInquiryFormView.vue'),
  },
  {
    path: '/procurement/inquiries/:id',
    name: 'procurement-inquiry-detail',
    meta: { requiresAuth: true, requiredPermission: 'procurement:inquiry:view' },
    component: () => import('../modules/procurement/PurchaseInquiryDetailView.vue'),
  },
  {
    path: '/procurement/price-agreements',
    name: 'procurement-price-agreements',
    meta: { requiresAuth: true, requiredPermission: 'procurement:price-agreement:view' },
    component: () => import('../modules/procurement/PriceAgreementListView.vue'),
  },
  {
    path: '/procurement/price-agreements/create',
    name: 'procurement-price-agreement-create',
    meta: { requiresAuth: true, requiredPermission: 'procurement:price-agreement:create' },
    component: () => import('../modules/procurement/PriceAgreementFormView.vue'),
  },
  {
    path: '/procurement/price-agreements/:id/edit',
    name: 'procurement-price-agreement-edit',
    meta: { requiresAuth: true, requiredPermission: 'procurement:price-agreement:update' },
    component: () => import('../modules/procurement/PriceAgreementFormView.vue'),
  },
  {
    path: '/procurement/price-agreements/:id',
    name: 'procurement-price-agreement-detail',
    meta: { requiresAuth: true, requiredPermission: 'procurement:price-agreement:view' },
    component: () => import('../modules/procurement/PriceAgreementDetailView.vue'),
  },
  {
    path: '/procurement/orders',
    name: 'procurement-orders',
    meta: { requiresAuth: true, requiredPermission: 'procurement:order:view' },
    component: () => import('../modules/procurement/PurchaseOrderListView.vue'),
  },
  {
    path: '/procurement/orders/create',
    name: 'procurement-order-create',
    meta: { requiresAuth: true, requiredPermission: 'procurement:order:create' },
    component: () => import('../modules/procurement/PurchaseOrderFormView.vue'),
  },
  {
    path: '/procurement/orders/:id',
    name: 'procurement-order-detail',
    meta: { requiresAuth: true, requiredPermission: 'procurement:order:view' },
    component: () => import('../modules/procurement/PurchaseOrderDetailView.vue'),
  },
  {
    path: '/procurement/orders/:id/edit',
    name: 'procurement-order-edit',
    meta: { requiresAuth: true, requiredPermission: 'procurement:order:update' },
    component: () => import('../modules/procurement/PurchaseOrderFormView.vue'),
  },
  {
    path: '/procurement/orders/:id/schedules',
    name: 'procurement-order-schedules',
    meta: { requiresAuth: true, requiredPermission: 'procurement:order:view' },
    component: () => import('../modules/procurement/PurchaseScheduleView.vue'),
  },
  {
    path: '/procurement/receipts',
    name: 'procurement-receipts',
    meta: { requiresAuth: true, requiredPermission: 'procurement:receipt:view' },
    component: () => import('../modules/procurement/PurchaseReceiptListView.vue'),
  },
  {
    path: '/procurement/orders/:orderId/receipts/create',
    name: 'procurement-receipt-create',
    meta: { requiresAuth: true, requiredPermission: 'procurement:receipt:create' },
    component: () => import('../modules/procurement/PurchaseReceiptFormView.vue'),
  },
  {
    path: '/procurement/receipts/:id',
    name: 'procurement-receipt-detail',
    meta: { requiresAuth: true, requiredPermission: 'procurement:receipt:view' },
    component: () => import('../modules/procurement/PurchaseReceiptDetailView.vue'),
  },
  {
    path: '/procurement/receipts/:id/edit',
    name: 'procurement-receipt-edit',
    meta: { requiresAuth: true, requiredPermission: 'procurement:receipt:update' },
    component: () => import('../modules/procurement/PurchaseReceiptFormView.vue'),
  },
  {
    path: '/procurement/returns',
    name: 'procurement-returns',
    meta: { requiresAuth: true, requiredPermission: 'procurement:return:view' },
    component: () => import('../modules/reversal/PurchaseReturnListView.vue'),
  },
  {
    path: '/procurement/returns/create',
    name: 'procurement-return-create',
    meta: { requiresAuth: true, requiredPermission: 'procurement:return:create' },
    component: () => import('../modules/reversal/PurchaseReturnFormView.vue'),
  },
  {
    path: '/procurement/returns/:id',
    name: 'procurement-return-detail',
    meta: { requiresAuth: true, requiredPermission: 'procurement:return:view' },
    component: () => import('../modules/reversal/PurchaseReturnDetailView.vue'),
  },
  {
    path: '/procurement/returns/:id/edit',
    name: 'procurement-return-edit',
    meta: { requiresAuth: true, requiredPermission: 'procurement:return:update' },
    component: () => import('../modules/reversal/PurchaseReturnFormView.vue'),
  },
  {
    path: '/procurement/effective-supplies',
    name: 'procurement-effective-supplies',
    meta: { requiresAuth: true, requiredPermission: 'procurement:supply:view' },
    component: () => import('../modules/procurement/EffectivePurchaseSupplyListView.vue'),
  },
  {
    path: '/sales',
    name: 'sales-root',
    meta: { requiresAuth: true },
    component: placeholder('销售管理', '销售项目、销售订单、出库和退货入口。'),
  },
  {
    path: '/sales/projects',
    name: 'sales-projects',
    meta: { requiresAuth: true, requiredPermission: 'sales:project:view' },
    component: () => import('../modules/sales/projects/SalesProjectListView.vue'),
  },
  {
    path: '/sales/projects/create',
    name: 'sales-project-create',
    meta: { requiresAuth: true, requiredPermission: 'sales:project:create' },
    component: () => import('../modules/sales/projects/SalesProjectFormView.vue'),
  },
  {
    path: '/sales/projects/:id',
    name: 'sales-project-detail',
    meta: { requiresAuth: true, requiredPermission: 'sales:project:view' },
    component: () => import('../modules/sales/projects/SalesProjectDetailView.vue'),
  },
  {
    path: '/sales/projects/:id/edit',
    name: 'sales-project-edit',
    meta: { requiresAuth: true, requiredPermission: 'sales:project:update' },
    component: () => import('../modules/sales/projects/SalesProjectFormView.vue'),
  },
  {
    path: '/sales/quotes',
    name: 'sales-quotes',
    meta: { requiresAuth: true, requiredPermission: 'sales:quote:view' },
    component: () => import('../modules/sales/quotes/SalesQuoteListView.vue'),
  },
  {
    path: '/sales/quotes/create',
    name: 'sales-quote-create',
    meta: { requiresAuth: true, requiredPermission: 'sales:quote:create' },
    component: () => import('../modules/sales/quotes/SalesQuoteFormView.vue'),
  },
  {
    path: '/sales/quotes/:id',
    name: 'sales-quote-detail',
    meta: { requiresAuth: true, requiredPermission: 'sales:quote:view' },
    component: () => import('../modules/sales/quotes/SalesQuoteDetailView.vue'),
  },
  {
    path: '/sales/quotes/:id/edit',
    name: 'sales-quote-edit',
    meta: { requiresAuth: true, requiredPermission: 'sales:quote:update' },
    component: () => import('../modules/sales/quotes/SalesQuoteFormView.vue'),
  },
  {
    path: '/sales/orders',
    name: 'sales-orders',
    meta: { requiresAuth: true, requiredPermission: 'sales:order:view' },
    component: () => import('../modules/sales/SalesOrderListView.vue'),
  },
  {
    path: '/sales/orders/create',
    name: 'sales-order-create',
    meta: { requiresAuth: true, requiredPermission: 'sales:order:create' },
    component: () => import('../modules/sales/SalesOrderFormView.vue'),
  },
  {
    path: '/sales/orders/:id',
    name: 'sales-order-detail',
    meta: { requiresAuth: true, requiredPermission: 'sales:order:view' },
    component: () => import('../modules/sales/SalesOrderDetailView.vue'),
  },
  {
    path: '/sales/orders/:id/edit',
    name: 'sales-order-edit',
    meta: { requiresAuth: true, requiredPermission: 'sales:order:update' },
    component: () => import('../modules/sales/SalesOrderFormView.vue'),
  },
  {
    path: '/sales/delivery-plans',
    name: 'sales-delivery-plans',
    meta: { requiresAuth: true, requiredPermission: 'sales:delivery-plan:view' },
    component: () => import('../modules/sales/delivery/SalesDeliveryPlanListView.vue'),
  },
  {
    path: '/sales/orders/:orderId/shipments/create',
    name: 'sales-shipment-create',
    meta: { requiresAuth: true, requiredPermission: 'sales:shipment:create' },
    component: () => import('../modules/sales/SalesShipmentFormView.vue'),
  },
  {
    path: '/sales/shipments',
    name: 'sales-shipments',
    meta: { requiresAuth: true, requiredPermission: 'sales:shipment:view' },
    component: () => import('../modules/sales/SalesShipmentListView.vue'),
  },
  {
    path: '/sales/shipments/:id',
    name: 'sales-shipment-detail',
    meta: { requiresAuth: true, requiredPermission: 'sales:shipment:view' },
    component: () => import('../modules/sales/SalesShipmentDetailView.vue'),
  },
  {
    path: '/sales/shipments/:id/edit',
    name: 'sales-shipment-edit',
    meta: { requiresAuth: true, requiredPermission: 'sales:shipment:update' },
    component: () => import('../modules/sales/SalesShipmentFormView.vue'),
  },
  {
    path: '/sales/returns',
    name: 'sales-returns',
    meta: { requiresAuth: true, requiredPermission: 'sales:return:view' },
    component: () => import('../modules/reversal/SalesReturnListView.vue'),
  },
  {
    path: '/sales/returns/create',
    name: 'sales-return-create',
    meta: { requiresAuth: true, requiredPermission: 'sales:return:create' },
    component: () => import('../modules/reversal/SalesReturnFormView.vue'),
  },
  {
    path: '/sales/returns/:id',
    name: 'sales-return-detail',
    meta: { requiresAuth: true, requiredPermission: 'sales:return:view' },
    component: () => import('../modules/reversal/SalesReturnDetailView.vue'),
  },
  {
    path: '/sales/returns/:id/edit',
    name: 'sales-return-edit',
    meta: { requiresAuth: true, requiredPermission: 'sales:return:update' },
    component: () => import('../modules/reversal/SalesReturnFormView.vue'),
  },
  {
    path: '/sales/credit-profiles',
    name: 'sales-credit-profiles',
    meta: { requiresAuth: true, requiredPermission: 'sales:credit:view' },
    component: () => import('../modules/sales/credit/SalesCreditProfileListView.vue'),
  },
  {
    path: '/sales/effective-demands',
    name: 'sales-effective-demands',
    meta: { requiresAuth: true, requiredPermission: 'sales:effective-demand:view' },
    component: () => import('../modules/sales/effective-demand/EffectiveSalesDemandListView.vue'),
  },
  {
    path: '/production',
    redirect: '/production/work-orders',
    meta: { requiresAuth: true, requiredPermission: 'production:work-order:view' },
  },
  {
    path: '/production/work-orders',
    name: 'production-work-orders',
    meta: { requiresAuth: true, requiredPermission: 'production:work-order:view' },
    component: () => import('../modules/production/ProductionWorkOrderListView.vue'),
  },
  {
    path: '/production/work-orders/create',
    name: 'production-work-order-create',
    meta: { requiresAuth: true, requiredPermission: 'production:work-order:create' },
    component: () => import('../modules/production/ProductionWorkOrderFormView.vue'),
  },
  {
    path: '/production/work-orders/:id',
    name: 'production-work-order-detail',
    meta: { requiresAuth: true, requiredPermission: 'production:work-order:view' },
    component: () => import('../modules/production/ProductionWorkOrderDetailView.vue'),
  },
  {
    path: '/production/work-orders/:id/edit',
    name: 'production-work-order-edit',
    meta: { requiresAuth: true, requiredPermission: 'production:work-order:update' },
    component: () => import('../modules/production/ProductionWorkOrderFormView.vue'),
  },
  {
    path: '/production/work-orders/:id/material-issues',
    name: 'production-work-order-material-issues',
    meta: { requiresAuth: true, requiredPermission: 'production:issue:view' },
    component: () => import('../modules/production/ProductionMaterialIssueView.vue'),
  },
  {
    path: '/production/work-orders/:id/reports',
    name: 'production-work-order-reports',
    meta: { requiresAuth: true, requiredPermission: 'production:report:view' },
    component: () => import('../modules/production/ProductionWorkReportView.vue'),
  },
  {
    path: '/production/work-orders/:id/completion-receipts',
    name: 'production-work-order-completion-receipts',
    meta: { requiresAuth: true, requiredPermission: 'production:receipt:view' },
    component: () => import('../modules/production/ProductionCompletionReceiptView.vue'),
  },
  {
    path: '/production/material-returns',
    name: 'production-material-returns',
    meta: { requiresAuth: true, requiredPermission: 'production:material-return:view' },
    component: () => import('../modules/reversal/ProductionMaterialReturnListView.vue'),
  },
  {
    path: '/production/material-returns/create',
    name: 'production-material-return-create',
    meta: { requiresAuth: true, requiredPermission: 'production:material-return:create' },
    component: () => import('../modules/reversal/ProductionMaterialReturnFormView.vue'),
  },
  {
    path: '/production/material-returns/:id',
    name: 'production-material-return-detail',
    meta: { requiresAuth: true, requiredPermission: 'production:material-return:view' },
    component: () => import('../modules/reversal/ProductionMaterialReturnDetailView.vue'),
  },
  {
    path: '/production/material-returns/:id/edit',
    name: 'production-material-return-edit',
    meta: { requiresAuth: true, requiredPermission: 'production:material-return:update' },
    component: () => import('../modules/reversal/ProductionMaterialReturnFormView.vue'),
  },
  {
    path: '/production/material-supplements',
    name: 'production-material-supplements',
    meta: { requiresAuth: true, requiredPermission: 'production:material-supplement:view' },
    component: () => import('../modules/reversal/ProductionMaterialSupplementListView.vue'),
  },
  {
    path: '/production/material-supplements/create',
    name: 'production-material-supplement-create',
    meta: { requiresAuth: true, requiredPermission: 'production:material-supplement:create' },
    component: () => import('../modules/reversal/ProductionMaterialSupplementFormView.vue'),
  },
  {
    path: '/production/material-supplements/:id',
    name: 'production-material-supplement-detail',
    meta: { requiresAuth: true, requiredPermission: 'production:material-supplement:view' },
    component: () => import('../modules/reversal/ProductionMaterialSupplementDetailView.vue'),
  },
  {
    path: '/production/material-supplements/:id/edit',
    name: 'production-material-supplement-edit',
    meta: { requiresAuth: true, requiredPermission: 'production:material-supplement:update' },
    component: () => import('../modules/reversal/ProductionMaterialSupplementFormView.vue'),
  },
  {
    path: '/quality',
    redirect: '/quality/inspections',
    meta: { requiresAuth: true, requiredPermission: 'quality:inspection:view' },
  },
  {
    path: '/quality/inspections',
    name: 'quality-inspections',
    meta: { requiresAuth: true, requiredPermission: 'quality:inspection:view' },
    component: () => import('../modules/quality/QualityInspectionListView.vue'),
  },
  {
    path: '/cost',
    redirect: '/cost/records',
  },
  {
    path: '/cost/records',
    name: 'cost-records',
    meta: { requiresAuth: true, requiredPermission: 'cost:record:view' },
    component: () => import('../modules/cost/CostRecordListView.vue'),
  },
  {
    path: '/cost/records/create',
    name: 'cost-record-create',
    meta: { requiresAuth: true, requiredPermission: 'cost:record:create' },
    component: () => import('../modules/cost/CostRecordFormView.vue'),
  },
  {
    path: '/cost/records/:id',
    name: 'cost-record-detail',
    meta: { requiresAuth: true, requiredPermission: 'cost:record:view' },
    component: () => import('../modules/cost/CostRecordDetailView.vue'),
  },
  {
    path: '/cost/records/:id/edit',
    name: 'cost-record-edit',
    meta: { requiresAuth: true, requiredPermission: 'cost:record:update' },
    component: () => import('../modules/cost/CostRecordFormView.vue'),
  },
  {
    path: '/finance',
    name: 'finance-root',
    meta: { requiresAuth: true },
    component: () => import('../modules/finance/FinancePlaceholderView.vue'),
  },
  {
    path: '/finance/receivables',
    name: 'finance-receivables',
    meta: { requiresAuth: true, requiredPermission: 'finance:receivable:view' },
    component: () => import('../modules/finance/ReceivableListView.vue'),
  },
  {
    path: '/finance/receivables/create',
    name: 'finance-receivable-create',
    meta: { requiresAuth: true, requiredPermission: 'finance:receivable:create' },
    component: () => import('../modules/finance/ReceivableFormView.vue'),
  },
  {
    path: '/finance/receivables/:id',
    name: 'finance-receivable-detail',
    meta: { requiresAuth: true, requiredPermission: 'finance:receivable:view' },
    component: () => import('../modules/finance/ReceivableDetailView.vue'),
  },
  {
    path: '/finance/receivables/:id/edit',
    name: 'finance-receivable-edit',
    meta: { requiresAuth: true, requiredPermission: 'finance:receivable:update' },
    component: () => import('../modules/finance/ReceivableFormView.vue'),
  },
  {
    path: '/finance/receivables/:id/receipts/create',
    name: 'finance-receipt-create',
    meta: { requiresAuth: true, requiredPermission: 'finance:receipt:create' },
    component: () => import('../modules/finance/ReceiptFormView.vue'),
  },
  {
    path: '/finance/receipts',
    name: 'finance-receipts',
    meta: { requiresAuth: true, requiredPermission: 'finance:receipt:view' },
    component: () => import('../modules/finance/ReceiptListView.vue'),
  },
  {
    path: '/finance/receipts/:id',
    name: 'finance-receipt-detail',
    meta: { requiresAuth: true, requiredPermission: 'finance:receipt:view' },
    component: () => import('../modules/finance/ReceiptDetailView.vue'),
  },
  {
    path: '/finance/receipts/:id/edit',
    name: 'finance-receipt-edit',
    meta: { requiresAuth: true, requiredPermission: 'finance:receipt:update' },
    component: () => import('../modules/finance/ReceiptFormView.vue'),
  },
  {
    path: '/finance/payables',
    name: 'finance-payables',
    meta: { requiresAuth: true, requiredPermission: 'finance:payable:view' },
    component: () => import('../modules/finance/PayableListView.vue'),
  },
  {
    path: '/finance/payables/create',
    name: 'finance-payable-create',
    meta: { requiresAuth: true, requiredPermission: 'finance:payable:create' },
    component: () => import('../modules/finance/PayableFormView.vue'),
  },
  {
    path: '/finance/payables/:id',
    name: 'finance-payable-detail',
    meta: { requiresAuth: true, requiredPermission: 'finance:payable:view' },
    component: () => import('../modules/finance/PayableDetailView.vue'),
  },
  {
    path: '/finance/payables/:id/edit',
    name: 'finance-payable-edit',
    meta: { requiresAuth: true, requiredPermission: 'finance:payable:update' },
    component: () => import('../modules/finance/PayableFormView.vue'),
  },
  {
    path: '/finance/payables/:id/payments/create',
    name: 'finance-payment-create',
    meta: { requiresAuth: true, requiredPermission: 'finance:payment:create' },
    component: () => import('../modules/finance/PaymentFormView.vue'),
  },
  {
    path: '/finance/payments',
    name: 'finance-payments',
    meta: { requiresAuth: true, requiredPermission: 'finance:payment:view' },
    component: () => import('../modules/finance/PaymentListView.vue'),
  },
  {
    path: '/finance/payments/:id',
    name: 'finance-payment-detail',
    meta: { requiresAuth: true, requiredPermission: 'finance:payment:view' },
    component: () => import('../modules/finance/PaymentDetailView.vue'),
  },
  {
    path: '/finance/payments/:id/edit',
    name: 'finance-payment-edit',
    meta: { requiresAuth: true, requiredPermission: 'finance:payment:update' },
    component: () => import('../modules/finance/PaymentFormView.vue'),
  },
  {
    path: '/finance/settlement-adjustments',
    name: 'finance-settlement-adjustments',
    meta: { requiresAuth: true, requiredPermission: 'finance:settlement-adjustment:view' },
    component: () => import('../modules/reversal/SettlementAdjustmentListView.vue'),
  },
  {
    path: '/finance/settlement-adjustments/create',
    name: 'finance-settlement-adjustment-create',
    meta: { requiresAuth: true, requiredPermission: 'finance:settlement-adjustment:create' },
    component: () => import('../modules/reversal/SettlementAdjustmentFormView.vue'),
  },
  {
    path: '/finance/settlement-adjustments/:id',
    name: 'finance-settlement-adjustment-detail',
    meta: { requiresAuth: true, requiredPermission: 'finance:settlement-adjustment:view' },
    component: () => import('../modules/reversal/SettlementAdjustmentDetailView.vue'),
  },
  {
    path: '/finance/settlement-adjustments/:id/edit',
    name: 'finance-settlement-adjustment-edit',
    meta: { requiresAuth: true, requiredPermission: 'finance:settlement-adjustment:update' },
    component: () => import('../modules/reversal/SettlementAdjustmentFormView.vue'),
  },
  {
    path: '/reports',
    name: 'reports-root',
    meta: { requiresAuth: true },
    component: placeholder('经营报表', '经营报表入口。'),
  },
  ...reportRouteConfigs.map((config): RouteRecordRaw => ({
    path: config.path,
    name: config.routeName,
    meta: { requiresAuth: true, requiredPermission: config.permission },
    component: reportPageComponent(config.routeName),
  })),
]

export function createQhErpRouter() {
  const appRouter = createRouter({
    history: import.meta.env.MODE === 'test' ? createMemoryHistory() : history,
    routes,
  })

  appRouter.beforeEach(async (to) => {
    if (!to.meta.requiresAuth && !to.meta.guestOnly) {
      return true
    }

    const authStore = useAuthStore()
    if (!authStore.currentUser) {
      try {
        await authStore.fetchCurrentUser()
      } catch {
        if (to.meta.requiresAuth) {
          return { name: 'login', query: { redirect: to.fullPath } }
        }

        return true
      }
    }

    if (to.meta.guestOnly && authStore.isAuthenticated) {
      return { name: 'home' }
    }

    if (!to.meta.requiresAuth) {
      return true
    }

    if (!authStore.isAuthenticated) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    const hasSystemAdminRole = authStore.roles.some((role) => role.code === 'SYSTEM_ADMIN')
    const hasRoutePermission = (permission: string) => (
      authStore.hasPermission(permission)
      || (permission === 'sales:credit:view' && hasSystemAdminRole)
    )

    if (to.name === 'finance-root') {
      const financeRoute = firstFinanceRouteByPermission((permission) => authStore.hasPermission(permission))
      if (!financeRoute) {
        return { name: 'forbidden', query: { from: to.fullPath } }
      }
      return financeRoute
    }

    if (to.name === 'sales-root') {
      const salesRoute = salesRouteOrder.find((item) => hasRoutePermission(item.permission))
      if (!salesRoute) {
        return { name: 'forbidden', query: { from: to.fullPath } }
      }
      return { name: salesRoute.name }
    }

    if (to.name === 'procurement-root') {
      const procurementRoute = procurementRouteOrder.find((item) => authStore.hasPermission(item.permission))
      if (!procurementRoute) {
        return { name: 'forbidden', query: { from: to.fullPath } }
      }
      return { name: procurementRoute.name }
    }

    if (to.name === 'reports-root') {
      const reportRoute = firstReportRouteByPermission((permission) => authStore.hasPermission(permission))
      if (!reportRoute) {
        return { name: 'forbidden', query: { from: to.fullPath } }
      }
      return reportRoute
    }

    const requiredPermissions = [
      ...(to.meta.requiredPermission ? [to.meta.requiredPermission] : []),
      ...(to.meta.requiredPermissions ?? []),
    ]
    if (requiredPermissions.some((permission) => !hasRoutePermission(permission))) {
      return { name: 'forbidden', query: { from: to.fullPath } }
    }

    return true
  })

  return appRouter
}

export const router = createQhErpRouter()
