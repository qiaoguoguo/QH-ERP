import { h } from 'vue'
import { createMemoryHistory, createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
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
    path: '/master/units',
    name: 'master-units',
    meta: { requiresAuth: true, requiredPermission: 'master:unit:view' },
    component: () => import('../modules/master/units/UnitListView.vue'),
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
    path: '/procurement',
    redirect: '/procurement/orders',
    meta: { requiresAuth: true, requiredPermission: 'procurement:order:view' },
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

    const requiredPermissions = [
      ...(to.meta.requiredPermission ? [to.meta.requiredPermission] : []),
      ...(to.meta.requiredPermissions ?? []),
    ]
    if (requiredPermissions.some((permission) => !authStore.hasPermission(permission))) {
      return { name: 'forbidden', query: { from: to.fullPath } }
    }

    return true
  })

  return appRouter
}

export const router = createQhErpRouter()
