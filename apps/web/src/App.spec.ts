import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import App from './App.vue'
import { createQhErpRouter } from './router'
import { useAuthStore } from './stores/authStore'

describe('ERP 应用骨架', () => {
  it('展示制造业 ERP 后台框架和当前用户菜单入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
      menus: [
        {
          id: 1,
          code: 'system',
          name: '系统管理',
          routePath: '/system',
          children: [
            { id: 2, code: 'system:user', name: '用户管理', routePath: '/system/users' },
            { id: 3, code: 'system:role', name: '角色管理', routePath: '/system/roles' },
          ],
        },
        {
          id: 4,
          code: 'inventory',
          name: '库存管理',
          routePath: '/inventory/balances',
          children: [
            { id: 5, code: 'inventory:balance:view', name: '库存余额', routePath: '/inventory/balances' },
            { id: 6, code: 'inventory:movement:view', name: '库存变动', routePath: '/inventory/movements' },
            { id: 7, code: 'inventory:document:view', name: '库存单据', routePath: '/inventory/documents' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('QH ERP')
    expect(wrapper.text()).toContain('制造业生产管理 ERP')
    expect(wrapper.text()).toContain('工作台')
    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).toContain('角色管理')
    expect(wrapper.text()).toContain('库存管理')
    expect(wrapper.text()).toContain('库存余额')
    expect(wrapper.text()).toContain('库存变动')
    expect(wrapper.text()).toContain('库存单据')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/inventory')
  })

  it('有库存查看权限但后端只返回库存一级菜单时补齐库存子菜单入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'inventory_admin', displayName: '库存管理员', status: 'ENABLED' },
      menus: [
        {
          id: 4,
          code: 'inventory',
          name: '库存管理',
          routePath: '/inventory/balances',
          children: [],
        },
      ],
      permissions: ['inventory:balance:view', 'inventory:movement:view', 'inventory:document:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('库存管理')
    expect(wrapper.text()).toContain('库存余额')
    expect(wrapper.text()).toContain('库存变动')
    expect(wrapper.text()).toContain('库存单据')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/inventory')
  })

  it('有成本查看权限但后端菜单缺失时补齐成本管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'cost_admin', displayName: '成本管理员', status: 'ENABLED' },
      menus: [],
      permissions: ['cost:record:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('成本管理')
    expect(wrapper.text()).toContain('成本记录')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/cost')
  })

  it('后端返回采购菜单且用户有采购查看权限时展示采购入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'procurement_user', displayName: '采购用户', status: 'ENABLED' },
      menus: [
        {
          id: 20,
          code: 'procurement',
          name: '采购管理',
          routePath: '/procurement/orders',
          children: [
            { id: 21, code: 'procurement:order:view', name: '采购订单', routePath: '/procurement/orders' },
            { id: 22, code: 'procurement:receipt:view', name: '采购入库', routePath: '/procurement/receipts' },
          ],
        },
      ],
      permissions: ['procurement:order:view', 'procurement:receipt:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('采购管理')
    expect(wrapper.text()).toContain('采购订单')
    expect(wrapper.text()).toContain('采购入库')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/procurement')
  })

  it('有采购查看权限但后端菜单缺失时补齐采购管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'procurement_admin', displayName: '采购管理员', status: 'ENABLED' },
      menus: [],
      permissions: ['procurement:order:view', 'procurement:receipt:view'],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).toContain('采购管理')
    expect(wrapper.text()).toContain('采购订单')
    expect(wrapper.text()).toContain('采购入库')
    expect(wrapper.findAllComponents({ name: 'ElSubMenu' }).map((item) => item.props('index')))
      .toContain('/menu/procurement')
  })

  it('无采购查看权限时不显示采购管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'no_procurement', displayName: '无采购权限', status: 'ENABLED' },
      menus: [
        {
          id: 20,
          code: 'procurement',
          name: '采购管理',
          routePath: null,
          children: [
            { id: 21, code: 'procurement:order:view', name: '采购订单', routePath: '/procurement/orders' },
            { id: 22, code: 'procurement:receipt:view', name: '采购入库', routePath: '/procurement/receipts' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('采购管理')
    expect(wrapper.text()).not.toContain('采购订单')
    expect(wrapper.text()).not.toContain('采购入库')
  })

  it('无采购查看权限时递归移除挂在其他父级下的采购菜单', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'business_user', displayName: '业务用户', status: 'ENABLED' },
      menus: [
        {
          id: 30,
          code: 'business',
          name: '业务管理',
          routePath: null,
          children: [
            { id: 31, code: 'procurement:order:view', name: '采购订单', routePath: '/procurement/orders' },
            { id: 32, code: 'procurement:receipt:view', name: '采购入库', routePath: '/procurement/receipts' },
          ],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('采购管理')
    expect(wrapper.text()).not.toContain('采购订单')
    expect(wrapper.text()).not.toContain('采购入库')
    expect(wrapper.text()).not.toContain('业务管理')
  })

  it('无成本查看权限时不显示成本管理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user: { id: 1, username: 'readonly', displayName: '只读用户', status: 'ENABLED' },
      menus: [
        {
          id: 10,
          code: 'cost',
          name: '成本管理',
          routePath: null,
          children: [{ id: 11, code: 'cost:record:view', name: '成本记录', routePath: '/cost/records' }],
        },
      ],
      permissions: [],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    expect(wrapper.text()).not.toContain('成本管理')
    expect(wrapper.text()).not.toContain('成本记录')
  })

  it('退出失败时保留当前会话和路由并显示错误提示', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useAuthStore()
    store.setSession({
      user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
      menus: [],
      permissions: [],
    })
    vi.spyOn(store, 'logout').mockRejectedValue(new Error('退出接口失败'))
    const router = createQhErpRouter()
    const replaceSpy = vi.spyOn(router, 'replace')
    router.push('/')
    await router.isReady()
    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    await wrapper.find('[data-test="logout-button"]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(store.currentUser?.username).toBe('admin')
    expect(router.currentRoute.value.name).toBe('home')
    expect(replaceSpy).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('退出接口失败')
    expect(wrapper.find('[data-test="logout-button"]').attributes('disabled')).toBeUndefined()
  })

  it('退出提交中禁用退出入口并显示加载态', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useAuthStore()
    store.setSession({
      user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
      menus: [],
      permissions: [],
    })
    let resolveLogout!: () => void
    vi.spyOn(store, 'logout').mockImplementation(() => new Promise<void>((resolve) => {
      resolveLogout = () => {
        store.clearSession()
        resolve()
      }
    }))
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()
    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })

    await wrapper.find('[data-test="logout-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="logout-button"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('退出中')

    resolveLogout()
    await flushPromises()
  })
})
