import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSession, UserProfile } from '../shared/api/accountPermissionApi'
import { useAuthStore } from '../stores/authStore'
import CustomerListView from '../modules/master/customers/CustomerListView.vue'
import MasterDataRoutePlaceholder from '../modules/master/shared/MasterDataRoutePlaceholder.vue'
import SupplierListView from '../modules/master/suppliers/SupplierListView.vue'
import UnitListView from '../modules/master/units/UnitListView.vue'
import WarehouseListView from '../modules/master/warehouses/WarehouseListView.vue'
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

  it('基础资料路由加载真实页面，物料未来路由仍加载 Task 5 通用占位组件', async () => {
    const router = createQhErpRouter()
    const realMasterRoutes = [
      ['master-units', UnitListView],
      ['master-warehouses', WarehouseListView],
      ['master-suppliers', SupplierListView],
      ['master-customers', CustomerListView],
    ] as const
    const placeholderRouteNames = ['material-categories', 'material-items']

    for (const [routeName, expectedComponent] of realMasterRoutes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }

    for (const routeName of placeholderRouteNames) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', MasterDataRoutePlaceholder)
    }
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
})
