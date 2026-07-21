import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import App from '../App.vue'
import DataRepairListView from '../modules/platform/dataRepairs/DataRepairListView.vue'
import DataRepairCreateView from '../modules/platform/dataRepairs/DataRepairCreateView.vue'
import DataRepairDetailView from '../modules/platform/dataRepairs/DataRepairDetailView.vue'
import HistoryImportListView from '../modules/platform/historyImports/HistoryImportListView.vue'
import HistoryImportDetailView from '../modules/platform/historyImports/HistoryImportDetailView.vue'
import DeliveryAssetsView from '../modules/platform/deliveryAssets/DeliveryAssetsView.vue'
import { createQhErpRouter } from './index'
import { useAuthStore } from '../stores/authStore'

const user = { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' as const }

describe('034 平台治理路由与菜单', () => {
  it('注册数据修复、历史导入和交付资料路由，并配置冻结平台权限', async () => {
    const router = createQhErpRouter()
    const routes = [
      ['platform-data-repairs', '/platform/data-repairs', 'platform:data-repair:view', DataRepairListView],
      ['platform-data-repair-create', '/platform/data-repairs/create', 'platform:data-repair:create', DataRepairCreateView],
      ['platform-data-repair-detail', '/platform/data-repairs/:id', 'platform:data-repair:view', DataRepairDetailView],
      ['platform-history-imports', '/platform/history-imports', 'platform:history-import:view', HistoryImportListView],
      ['platform-history-import-detail', '/platform/history-imports/:id', 'platform:history-import:view', HistoryImportDetailView],
      ['platform-delivery-assets', '/platform/delivery-assets', 'platform:delivery-asset:view', DeliveryAssetsView],
    ] as const

    for (const [routeName, path, permission, expectedComponent] of routes) {
      const route = router.getRoutes().find((item) => item.name === routeName)
      const component = route?.components?.default as (() => Promise<unknown>) | undefined

      expect(route?.path).toBe(path)
      expect(route?.meta.requiresAuth).toBe(true)
      expect(route?.meta.requiredPermission).toBe(permission)
      expect(component).toBeTypeOf('function')
      await expect(component?.()).resolves.toHaveProperty('default', expectedComponent)
    }
  })

  it('已登录但缺少 034 平台治理权限时跳转无权限页', async () => {
    const router = createQhErpRouter()
    setActivePinia(createPinia())
    useAuthStore().setSession({ user, menus: [], permissions: ['platform:document-task:view'] })

    await router.push('/platform/data-repairs')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(router.currentRoute.value.query.from).toBe('/platform/data-repairs')
  })

  it('后端菜单缺失时按 034 权限补齐平台工作台治理入口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession({
      user,
      menus: [],
      permissions: [
        'platform:data-repair:view',
        'platform:history-import:view',
        'platform:delivery-asset:view',
      ],
    })
    const router = createQhErpRouter()
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router, ElementPlus],
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('平台工作台')
    expect(wrapper.text()).toContain('数据修复')
    expect(wrapper.text()).toContain('历史导入')
    expect(wrapper.text()).toContain('交付资料')
    expect(wrapper.findAllComponents({ name: 'ElMenuItem' }).map((item) => item.props('index')))
      .toEqual(expect.arrayContaining([
        '/platform/data-repairs',
        '/platform/history-imports',
        '/platform/delivery-assets',
      ]))
  })
})
