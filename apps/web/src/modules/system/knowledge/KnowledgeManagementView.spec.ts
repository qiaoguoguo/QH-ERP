import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { defineComponent, h, inject, provide } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import KnowledgeManagementView from './KnowledgeManagementView.vue'
import { knowledgeBaseApi } from '../../../shared/api/knowledgeBaseApi'
import { useConfirmActionMock } from '../../../test/setup'

vi.mock('../../../shared/api/knowledgeBaseApi', () => ({
  knowledgeBaseApi: {
    admin: {
      categories: vi.fn(),
      articles: vi.fn(),
      disableArticle: vi.fn(),
      enableArticle: vi.fn(),
      deleteArticle: vi.fn(),
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      enableCategory: vi.fn(),
      disableCategory: vi.fn(),
      deleteCategory: vi.fn(),
    },
  },
  knowledgeTypeLabel: (type: string) => `类型-${type}`,
}))

const tableRowsKey = Symbol('tableRows')

const ElementStubs = {
  MasterDataTableView: { template: '<section><slot name="actions" /><slot name="filters" /><slot name="alerts" /><slot /></section>' },
  ElButton: { template: '<button type="button" v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' },
  ElAlert: { props: ['title'], template: '<p class="alert">{{ title }}</p>' },
  ElTag: { template: '<span><slot /></span>' },
  ElEmpty: { props: ['description'], template: '<p class="empty">{{ description }}</p>' },
  ElDialog: { props: ['modelValue'], template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>' },
  ElDropdown: { template: '<span><slot /><slot name="dropdown" /></span>' },
  ElDropdownMenu: { template: '<span><slot /></span>' },
  ElDropdownItem: { template: '<span><slot /></span>' },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { template: '<label><slot /></label>' },
  ElInput: {
    props: ['modelValue', 'type'],
    emits: ['update:modelValue'],
    template: '<textarea v-if="type === \'textarea\'" v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /><input v-else v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElInputNumber: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input type="number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />' },
  ElSelect: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<select v-bind="$attrs" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
  },
  ElOption: { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  ElPagination: {
    props: ['currentPage', 'pageSize', 'total'],
    emits: ['current-change', 'size-change'],
    template: '<nav><button data-test="management-page-next" type="button" @click="$emit(\'current-change\', currentPage + 1)">next</button><button data-test="management-page-size" type="button" @click="$emit(\'size-change\', 20)">size</button></nav>',
  },
  ElTable: defineComponent({
    props: ['data', 'emptyText'],
    setup(props, { slots }) {
      provide(tableRowsKey, props)
      return () => h('table', [
        ...((props.data ?? []).length === 0 && props.emptyText ? [h('caption', { class: 'table-empty' }, String(props.emptyText))] : []),
        ...(slots.default?.() ?? []),
      ])
    },
  }),
  ElTableColumn: defineComponent({
    props: ['prop'],
    setup(props, { slots }) {
      const tableProps = inject<{ data?: any[] }>(tableRowsKey, { data: [] })
      return () => {
        const rows = tableProps.data ?? []
        return h('tbody', rows.map((row) => h('tr', [h('td', slots.default ? slots.default({ row }) : String(props.prop ? row[props.prop as string] ?? '' : ''))])))
      }
    },
  }),
}

const articlePage = {
  items: [
    {
      id: 101,
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
    },
  ],
  total: 1,
  page: 1,
  pageSize: 10,
  totalPages: 1,
}

const articleDetail = {
  ...articlePage.items[0],
  content: '# 采购订单确认',
  permissionNote: '',
  relatedArticleIds: [],
  sortOrder: 100,
  createdAt: '2026-08-14 09:00:00',
  version: 1,
}

async function mountView(initialPath = '/system/knowledge?keyword=采购&page=2&pageSize=20') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/system/knowledge', name: 'system-knowledge', component: KnowledgeManagementView },
      { path: '/system/knowledge/create', name: 'system-knowledge-create', component: { template: '<div />' } },
      { path: '/system/knowledge/:id/edit', name: 'system-knowledge-edit', component: { template: '<div />' } },
    ],
  })
  await router.push(initialPath)
  await router.isReady()
  const wrapper = mount(KnowledgeManagementView, { global: { plugins: [router], stubs: ElementStubs } })
  await flushPromises()
  return { wrapper, router }
}

describe('KnowledgeManagementView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useConfirmActionMock().mockResolvedValue(true)
    vi.mocked(knowledgeBaseApi.admin.categories).mockResolvedValue([{ id: 1, code: 'procurement', name: '采购管理', parentCode: null, sortOrder: 10, status: 'ENABLED' }])
    vi.mocked(knowledgeBaseApi.admin.articles).mockResolvedValue(articlePage)
    vi.mocked(knowledgeBaseApi.admin.disableArticle).mockResolvedValue(articleDetail)
    vi.mocked(knowledgeBaseApi.admin.enableArticle).mockResolvedValue(articleDetail)
    vi.mocked(knowledgeBaseApi.admin.deleteArticle).mockResolvedValue(undefined)
    vi.mocked(knowledgeBaseApi.admin.disableCategory).mockResolvedValue({ id: 1, code: 'procurement', name: '采购管理', parentCode: null, sortOrder: 10, status: 'DISABLED' })
    vi.mocked(knowledgeBaseApi.admin.enableCategory).mockResolvedValue({ id: 1, code: 'procurement', name: '采购管理', parentCode: null, sortOrder: 10, status: 'ENABLED' })
  })

  it('按items分页契约加载列表，并支持搜索、重置和统一分页', async () => {
    const { wrapper, router } = await mountView()

    expect(knowledgeBaseApi.admin.articles).toHaveBeenCalledWith(expect.objectContaining({ keyword: '采购', page: 2, pageSize: 20 }))
    expect(wrapper.text()).toContain('采购订单如何确认')

    await wrapper.find('input[placeholder="标题、摘要、页面、关键词"]').setValue('库存')
    await flushPromises()
    await wrapper.find('[data-test="search-knowledge-management"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.keyword).toBe('库存')

    await wrapper.find('[data-test="management-page-next"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('2')

    await wrapper.find('[data-test="management-page-size"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.pageSize).toBe('20')

    await wrapper.find('[data-test="reset-knowledge-management"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.keyword).toBeUndefined()
  })

  it('列表操作使用项目按钮并执行启停和删除动作', async () => {
    const { wrapper } = await mountView('/system/knowledge')

    const editButton = wrapper.find('[data-test="edit-knowledge-article"]')
    const toggleButton = wrapper.find('[data-test="toggle-knowledge-article"]')
    const deleteButton = wrapper.find('[data-test="delete-knowledge-article"]')
    expect(editButton.element.tagName).toBe('BUTTON')
    expect(toggleButton.element.tagName).toBe('BUTTON')
    expect(deleteButton.element.tagName).toBe('BUTTON')
    expect(editButton.attributes('plain')).toBeDefined()
    expect(toggleButton.attributes('plain')).toBeDefined()
    expect(deleteButton.attributes('plain')).toBeDefined()
    expect(editButton.attributes('text')).toBeUndefined()
    expect(toggleButton.attributes('text')).toBeUndefined()
    expect(deleteButton.attributes('text')).toBeUndefined()

    await toggleButton.trigger('click')
    await flushPromises()
    expect(knowledgeBaseApi.admin.disableArticle).toHaveBeenCalledWith(101)

    await deleteButton.trigger('click')
    await flushPromises()
    expect(knowledgeBaseApi.admin.deleteArticle).toHaveBeenCalledWith(101)
  })

  it('空列表只显示表格空态一次，分类弹窗提供取消按钮并可关闭', async () => {
    vi.mocked(knowledgeBaseApi.admin.articles).mockResolvedValueOnce({ items: [], total: 0, page: 1, pageSize: 10, totalPages: 0 })
    const { wrapper } = await mountView('/system/knowledge')

    expect(wrapper.findAll('.empty')).toHaveLength(0)
    expect(wrapper.text().match(/暂无知识内容/g)).toHaveLength(1)

    await wrapper.find('[data-test="manage-knowledge-categories"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('新增分类')

    const cancelButton = wrapper.findAll('button').find((button) => button.text() === '取消')
    expect(cancelButton).toBeTruthy()
    await cancelButton!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('新增分类')
  })
})
