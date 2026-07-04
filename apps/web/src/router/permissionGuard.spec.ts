import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSession, UserProfile } from '../shared/api/accountPermissionApi'
import { useAuthStore } from '../stores/authStore'
import MaterialCategoryView from '../modules/materials/categories/MaterialCategoryView.vue'
import MaterialItemListView from '../modules/materials/items/MaterialItemListView.vue'
import BomListView from '../modules/materials/boms/BomListView.vue'
import InventoryBalanceListView from '../modules/inventory/InventoryBalanceListView.vue'
import InventoryDocumentDetailView from '../modules/inventory/InventoryDocumentDetailView.vue'
import InventoryDocumentFormView from '../modules/inventory/InventoryDocumentFormView.vue'
import InventoryDocumentListView from '../modules/inventory/InventoryDocumentListView.vue'
import InventoryMovementListView from '../modules/inventory/InventoryMovementListView.vue'
import CustomerListView from '../modules/master/customers/CustomerListView.vue'
import SupplierListView from '../modules/master/suppliers/SupplierListView.vue'
import UnitListView from '../modules/master/units/UnitListView.vue'
import WarehouseListView from '../modules/master/warehouses/WarehouseListView.vue'
import ProductionCompletionReceiptView from '../modules/production/ProductionCompletionReceiptView.vue'
import ProductionMaterialIssueView from '../modules/production/ProductionMaterialIssueView.vue'
import ProductionWorkOrderDetailView from '../modules/production/ProductionWorkOrderDetailView.vue'
import ProductionWorkOrderFormView from '../modules/production/ProductionWorkOrderFormView.vue'
import ProductionWorkOrderListView from '../modules/production/ProductionWorkOrderListView.vue'
import ProductionWorkReportView from '../modules/production/ProductionWorkReportView.vue'
import CostRecordDetailView from '../modules/cost/CostRecordDetailView.vue'
import CostRecordFormView from '../modules/cost/CostRecordFormView.vue'
import CostRecordListView from '../modules/cost/CostRecordListView.vue'
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

  it('基础资料和物料路由加载真实页面', async () => {
    const router = createQhErpRouter()
    const realMasterRoutes = [
      ['master-units', UnitListView],
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

    expect(router.getRoutes().find((item) => item.name === 'material-categories')?.meta.requiredPermission)
      .toBe('master:material-category:view')
    expect(router.getRoutes().find((item) => item.name === 'material-items')?.meta.requiredPermission)
      .toBe('master:material:view')
    expect(router.getRoutes().find((item) => item.name === 'material-boms')?.meta.requiredPermission)
      .toBe('material:bom:view')
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

  it('采购路由使用占位组件并配置对应权限', async () => {
    const router = createQhErpRouter()
    const procurementRoutes = [
      ['procurement-orders', '/procurement/orders', 'procurement:order:view'],
      ['procurement-order-create', '/procurement/orders/create', 'procurement:order:create'],
      ['procurement-order-detail', '/procurement/orders/:id', 'procurement:order:view'],
      ['procurement-order-edit', '/procurement/orders/:id/edit', 'procurement:order:update'],
      ['procurement-receipts', '/procurement/receipts', 'procurement:receipt:view'],
      ['procurement-receipt-create', '/procurement/orders/:orderId/receipts/create', 'procurement:receipt:create'],
      ['procurement-receipt-detail', '/procurement/receipts/:id', 'procurement:receipt:view'],
      ['procurement-receipt-edit', '/procurement/receipts/:id/edit', 'procurement:receipt:update'],
    ] as const

    for (const [routeName, path, permission] of procurementRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as { render?: unknown; template?: unknown } | undefined

      expect(route?.path).toBe(path)
      expect(route?.meta.requiredPermission).toBe(permission)
      expect(component?.template).toBeUndefined()
      expect(component?.render).toBeTypeOf('function')
    }

    const rootRoute = router.getRoutes().find((item) => item.path === '/procurement')
    expect(rootRoute?.redirect).toBe('/procurement/orders')
    expect(rootRoute?.meta.requiresAuth).toBe(true)
    expect(rootRoute?.meta.requiredPermission).toBe('procurement:order:view')
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

  it('已登录且拥有生产工单查看权限时允许访问工单列表', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: ['production:work-order:view'] })

    await router.push('/production/work-orders')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('production-work-orders')
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

  it('已登录但缺少成本记录查看权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    useAuthStore().setSession({ user, menus: [], permissions: [] })

    await router.push('/cost/records')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/cost/records')
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
