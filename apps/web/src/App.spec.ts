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
