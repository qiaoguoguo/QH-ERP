import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'
import KnowledgeArticleView from './KnowledgeArticleView.vue'
import { knowledgeBaseApi } from '../../shared/api/knowledgeBaseApi'

vi.mock('../../shared/api/knowledgeBaseApi', () => ({
  knowledgeBaseApi: {
    help: {
      get: vi.fn(),
      related: vi.fn(),
    },
  },
  knowledgeTypeLabel: (type: string) => `类型-${type}`,
}))

const ElementStubs = {
  MasterDataTableView: { props: ['title', 'description'], template: '<section><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /><slot name="alerts" /><slot /></section>' },
  ElButton: { template: '<button type="button" v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' },
  ElAlert: { props: ['title'], template: '<p class="alert">{{ title }}</p>' },
  ElEmpty: { props: ['description'], template: '<p class="empty">{{ description }}</p>' },
}

const article = {
  id: 11,
  title: '采购订单如何确认',
  slug: 'purchase-order-confirm',
  summary: '采购订单确认说明',
  categoryId: 1,
  categoryCode: 'procurement',
  categoryName: '采购管理',
  knowledgeType: 'PAGE' as const,
  knowledgeTypeName: '页面操作',
  content: '# 功能用途\n说明页面用途。\n## 操作步骤\n1. 点击确认。',
  keywords: '采购订单',
  routePaths: '/procurement/orders/:id',
  pageNames: '采购订单详情',
  permissionNote: '需要采购订单查看权限',
  relatedArticleIds: [12],
  status: 'ENABLED' as const,
  updatedAt: '2026-08-14 10:00:00',
}

async function mountView(initialPath = '/help/articles/11?keyword=采购&page=2&pageSize=20') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/help', name: 'help-center', component: { template: '<div />' } },
      { path: '/help/articles/:id', name: 'help-article', component: KnowledgeArticleView },
      { path: '/procurement/orders/:id', name: 'procurement-order-detail', component: { template: '<div />' } },
    ],
  })
  await router.push(initialPath)
  await router.isReady()
  const wrapper = mount(KnowledgeArticleView, { global: { plugins: [router], stubs: ElementStubs } })
  await flushPromises()
  return { wrapper, router }
}

describe('KnowledgeArticleView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(knowledgeBaseApi.help.get).mockResolvedValue(article)
    vi.mocked(knowledgeBaseApi.help.related).mockResolvedValue([{ ...article, id: 12, title: '采购订单状态说明' }])
  })

  it('加载详情与关联知识，并保留返回帮助中心上下文', async () => {
    const { wrapper, router } = await mountView()

    expect(knowledgeBaseApi.help.get).toHaveBeenCalledWith('11')
    expect(knowledgeBaseApi.help.related).toHaveBeenCalledWith('11')
    expect(wrapper.text()).toContain('采购订单如何确认')
    expect(wrapper.text()).toContain('采购订单状态说明')

    await wrapper.find('[data-test="return-help-center"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('help-center')
    expect(router.currentRoute.value.query.keyword).toBe('采购')
    expect(router.currentRoute.value.query.page).toBe('2')
  })

  it('从页面帮助进入文章时可返回原页面', async () => {
    sessionStorage.setItem('qherp-page-help-return', '/procurement/orders/PO-REAL-001?tab=lines')
    const { wrapper, router } = await mountView('/help/articles/11?fromPage=1&routePath=/procurement/orders/:id')

    await wrapper.find('[data-test="return-original-page"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/procurement/orders/PO-REAL-001?tab=lines')
  })
})
