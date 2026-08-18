import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'
import HelpCenterView from './HelpCenterView.vue'
import { knowledgeBaseApi } from '../../shared/api/knowledgeBaseApi'

vi.mock('../../shared/api/knowledgeBaseApi', () => ({
  knowledgeBaseApi: {
    help: {
      categories: vi.fn(),
      search: vi.fn(),
      byRoute: vi.fn(),
    },
  },
  knowledgeTypeLabel: (type: string) => `类型-${type}`,
}))

const ElementStubs = {
  MasterDataTableView: { template: '<section><slot name="actions" /><slot name="filters" /><slot name="alerts" /><slot /></section>' },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { template: '<label><slot /></label>' },
  ElInput: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" @keyup.enter="$emit(\'keyup.enter\')" />',
  },
  ElSelect: {
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template: '<select v-bind="$attrs" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
  },
  ElOption: { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  ElButton: { template: '<button type="button" v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' },
  ElAlert: { props: ['title'], template: '<p class="alert">{{ title }}</p>' },
  ElTag: { template: '<span><slot /></span>' },
  ElEmpty: { props: ['description'], template: '<p class="empty">{{ description }}</p>' },
  ElScrollbar: { template: '<div><slot /></div>' },
  ElPagination: {
    props: ['currentPage', 'pageSize', 'total'],
    emits: ['current-change', 'size-change'],
    template: '<nav><button data-test="page-next" type="button" @click="$emit(\'current-change\', currentPage + 1)">next</button><button data-test="page-size" type="button" @click="$emit(\'size-change\', 20)">size</button></nav>',
  },
}

const summary = {
  id: 11,
  title: '采购订单如何确认',
  slug: 'purchase-order-confirm',
  summary: '采购订单确认说明',
  categoryId: 1,
  categoryCode: 'procurement',
  categoryName: '采购管理',
  knowledgeType: 'PAGE' as const,
  knowledgeTypeName: '页面操作',
  routePaths: '/procurement/orders/:id',
  pageNames: '采购订单详情',
  keywords: '采购订单',
  status: 'ENABLED' as const,
  updatedAt: '2026-08-14 10:00:00',
}

async function mountView(initialPath = '/help?keyword=采购&page=2&pageSize=20') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/help', name: 'help-center', component: HelpCenterView },
      { path: '/help/articles/:id', name: 'help-article', component: { template: '<div />' } },
    ],
  })
  await router.push(initialPath)
  await router.isReady()
  const wrapper = mount(HelpCenterView, { global: { plugins: [router], stubs: ElementStubs } })
  await flushPromises()
  return { wrapper, router }
}

describe('HelpCenterView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(knowledgeBaseApi.help.categories).mockResolvedValue([{ id: 1, code: 'procurement', name: '采购管理', sortOrder: 10, status: 'ENABLED' }])
    vi.mocked(knowledgeBaseApi.help.search).mockResolvedValue({ items: [summary], total: 1, page: 1, pageSize: 10, totalPages: 1 })
    vi.mocked(knowledgeBaseApi.help.byRoute).mockResolvedValue({ items: [summary], total: 1, page: 1, pageSize: 10, totalPages: 1 })
  })

  it('按items分页契约展示搜索结果，并支持搜索、重置和分页', async () => {
    const { wrapper, router } = await mountView()

    expect(knowledgeBaseApi.help.search).toHaveBeenCalledWith(expect.objectContaining({ keyword: '采购', page: 2, pageSize: 20 }))
    expect(wrapper.text()).toContain('采购订单如何确认')

    await wrapper.find('input[name="knowledge-keyword"]').setValue('库存')
    await wrapper.find('[data-test="search-knowledge"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.keyword).toBe('库存')

    await wrapper.find('[data-test="page-next"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('2')

    await wrapper.find('[data-test="page-size"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.pageSize).toBe('20')

    await wrapper.find('[data-test="reset-knowledge"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.keyword).toBeUndefined()
  })

  it('页面帮助优先显示路由关联知识；无关联时展示空态提示并回落搜索', async () => {
    vi.mocked(knowledgeBaseApi.help.byRoute).mockResolvedValueOnce({ items: [summary], total: 1, page: 1, pageSize: 10, totalPages: 1 })
    const { wrapper } = await mountView('/help?fromPage=1&routePath=/procurement/orders/:id')

    expect(knowledgeBaseApi.help.byRoute).toHaveBeenCalledWith('/procurement/orders/:id', { page: 1, pageSize: 10 })
    expect(wrapper.text()).toContain('当前页面相关帮助')

    vi.mocked(knowledgeBaseApi.help.byRoute).mockResolvedValueOnce({ items: [], total: 0, page: 1, pageSize: 10, totalPages: 1 })
    const second = await mountView('/help?fromPage=1&routePath=/inventory/documents')
    expect(second.wrapper.text()).toContain('当前页面暂无直接关联知识')
  })

  it('主动查询、重置、分类和类型筛选会退出页面关联模式', async () => {
    async function expectRouteAssociationCleared(action: (wrapper: ReturnType<typeof mount>) => Promise<void>) {
      const { wrapper, router } = await mountView('/help?fromPage=1&routePath=/procurement/orders/:id&keyword=采购&page=2&pageSize=20')

      expect(knowledgeBaseApi.help.byRoute).toHaveBeenCalledWith('/procurement/orders/:id', { page: 2, pageSize: 20 })

      await action(wrapper)
      await flushPromises()

      expect(router.currentRoute.value.query.fromPage).toBeUndefined()
      expect(router.currentRoute.value.query.routePath).toBeUndefined()
    }

    await expectRouteAssociationCleared(async (wrapper) => {
      await wrapper.find('input[name="knowledge-keyword"]').setValue('库存')
      await wrapper.find('[data-test="search-knowledge"]').trigger('click')
    })

    await expectRouteAssociationCleared(async (wrapper) => {
      await wrapper.find('[data-test="reset-knowledge"]').trigger('click')
    })

    await expectRouteAssociationCleared(async (wrapper) => {
      await wrapper.findAll('.category-link')[1].trigger('click')
    })

    await expectRouteAssociationCleared(async (wrapper) => {
      await wrapper.findAll('select')[1].setValue('PROCESS')
    })
  })
})
