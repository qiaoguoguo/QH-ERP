import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import App from './App.vue'
import { createQhErpRouter } from './router'
import type { AuthSession } from './shared/api/accountPermissionApi'
import { useAuthStore } from './stores/authStore'
import { createPageHelpLocation, currentPageHelpReturnPath } from './modules/help/pageHelp'

function loginSession(permissions: string[] = [], menus: AuthSession['menus'] = []): AuthSession {
  return {
    user: { id: 1, username: 'tester', displayName: '测试用户', status: 'ENABLED' },
    menus,
    permissions,
    roles: [],
  }
}

async function mountApp(initialPath = '/', session = loginSession()) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession(session)
  const router = createQhErpRouter()
  await router.push(initialPath)
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [pinia, router, ElementPlus] } })
  await flushPromises()
  return { wrapper, router }
}

describe('Knowledge App Integration', () => {
  it('所有已登录用户可见系统帮助入口，无管理权限时不显示知识库管理菜单', async () => {
    const { wrapper, router } = await mountApp('/', loginSession())

    expect(wrapper.find('[data-test="system-help-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="page-help-button"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('知识库管理')

    await router.push({ name: 'help-center' })
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('help-center')
  })

  it('页面帮助只传规范化路由，不把真实业务单号放入知识检索参数', () => {
    sessionStorage.clear()
    const route = {
      name: 'procurement-order-detail',
      path: '/procurement/orders/PO-REAL-001',
      fullPath: '/procurement/orders/PO-REAL-001?tab=lines',
      matched: [{ path: '/procurement/orders/:id' }],
    } as unknown as RouteLocationNormalizedLoaded

    const location = createPageHelpLocation(route, '采购订单')

    expect(location).toEqual({
      name: 'help-center',
      query: { routePath: '/procurement/orders/:id', keyword: '采购订单', fromPage: '1' },
    })
    expect(JSON.stringify(location)).not.toContain('PO-REAL-001')
    expect(currentPageHelpReturnPath()).toBe('/procurement/orders/PO-REAL-001?tab=lines')
  })

  it('拥有知识库管理权限时显示系统管理中的知识库管理菜单', async () => {
    const { wrapper } = await mountApp('/', loginSession(['system:knowledge:manage']))

    expect(wrapper.text()).toContain('系统管理')
    expect(wrapper.text()).toContain('知识库管理')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index'))).toContain('/system/knowledge')
  })
})
