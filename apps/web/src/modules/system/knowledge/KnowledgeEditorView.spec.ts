import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'
import KnowledgeEditorView from './KnowledgeEditorView.vue'
import { knowledgeBaseApi } from '../../../shared/api/knowledgeBaseApi'

vi.mock('../../../shared/api/knowledgeBaseApi', () => ({
  knowledgeBaseApi: {
    admin: {
      categories: vi.fn(),
      articles: vi.fn(),
      article: vi.fn(),
      createArticle: vi.fn(),
      updateArticle: vi.fn(),
    },
  },
  knowledgeTypeLabel: (type: string) => `类型-${type}`,
}))

const ElementStubs = {
  MasterDataTableView: { template: '<section><slot name="actions" /><slot name="alerts" /><slot /></section>' },
  ElCard: { template: '<section><slot name="header" /><slot /></section>' },
  ElButton: { template: '<button type="button" v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' },
  ElAlert: { props: ['title'], template: '<p class="alert">{{ title }}</p>' },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { props: ['error'], template: '<label><slot /><span v-if="error" class="field-error">{{ error }}</span></label>' },
  ElInput: {
    props: ['modelValue', 'type'],
    emits: ['update:modelValue'],
    template: '<textarea v-if="type === \'textarea\'" v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /><input v-else v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElInputNumber: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input type="number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />' },
  ElSelect: {
    props: { modelValue: null, multiple: Boolean },
    emits: ['update:modelValue', 'change', 'focus'],
    template: '<select v-bind="$attrs" :multiple="multiple" :value="modelValue" @focus="$emit(\'focus\')" @change="$emit(\'update:modelValue\', multiple ? Array.from($event.target.selectedOptions).map((option) => option.value) : $event.target.value); $emit(\'change\', multiple ? Array.from($event.target.selectedOptions).map((option) => option.value) : $event.target.value)"><slot /></select>',
  },
  ElOption: { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
}

const relatedSummary = {
  id: 22,
  title: '采购订单确认后怎么入库',
  slug: 'purchase-order-receipt',
  summary: '采购订单入库说明',
  categoryId: 1,
  categoryCode: 'procurement',
  categoryName: '采购管理',
  knowledgeType: 'PAGE' as const,
  knowledgeTypeName: '页面操作',
  routePaths: '/procurement/receipts',
  pageNames: '采购入库',
  keywords: '采购入库',
  status: 'ENABLED' as const,
  updatedAt: '2026-08-14 10:00:00',
}

const savedArticle = {
  ...relatedSummary,
  id: 10,
  slug: 'purchase-order-confirm',
  title: '采购订单如何确认',
  summary: '采购订单确认说明',
  content: '# 采购订单确认',
  permissionNote: '',
  relatedArticleIds: [],
  sortOrder: 100,
  createdAt: '2026-08-14 09:00:00',
  version: 1,
}

async function mountEditor(initialPath = '/system/knowledge/create', waitForMounted = true) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/system/knowledge', name: 'system-knowledge', component: { template: '<div />' } },
      { path: '/system/knowledge/create', name: 'system-knowledge-create', component: KnowledgeEditorView },
      { path: '/system/knowledge/:id/edit', name: 'system-knowledge-edit', component: KnowledgeEditorView },
    ],
  })
  await router.push(initialPath)
  await router.isReady()
  const wrapper = mount(KnowledgeEditorView, { global: { plugins: [router], stubs: ElementStubs } })
  if (waitForMounted) {
    await flushPromises()
    await flushPromises()
  }
  return { wrapper, router }
}

describe('KnowledgeEditorView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(knowledgeBaseApi.admin.categories).mockResolvedValue([{ id: 1, code: 'procurement', name: '采购管理', sortOrder: 10, status: 'ENABLED' }])
    vi.mocked(knowledgeBaseApi.admin.articles).mockResolvedValue({ items: [relatedSummary], total: 1, page: 1, pageSize: 20, totalPages: 1 })
    vi.mocked(knowledgeBaseApi.admin.createArticle).mockResolvedValue(savedArticle)
    vi.mocked(knowledgeBaseApi.admin.updateArticle).mockResolvedValue(savedArticle)
  })

  it('新增文章时必填字段显示字段级错误，缺失时不提交管理接口', async () => {
    const { wrapper } = await mountEditor()

    await wrapper.find('[data-test="save-knowledge-article"]').trigger('click')

    expect(wrapper.text()).toContain('请填写知识标识')
    expect(wrapper.text()).toContain('请填写标题')
    expect(wrapper.text()).toContain('请填写摘要')
    expect(wrapper.text()).toContain('请选择分类')
    expect(wrapper.text()).toContain('请填写正文')
    expect(knowledgeBaseApi.admin.createArticle).not.toHaveBeenCalled()
  })

  it('新增文章时slug必填，缺失时不提交管理接口', async () => {
    const { wrapper } = await mountEditor()

    const inputs = wrapper.findAll('input')
    const textareas = wrapper.findAll('textarea')
    await inputs[1].setValue('采购订单如何确认')
    await wrapper.findAll('select')[0].setValue('1')
    await textareas[0].setValue('采购订单确认说明')
    await textareas[1].setValue('# 采购订单确认\n\n确认后生成到货计划。')

    await wrapper.find('[data-test="save-knowledge-article"]').trigger('click')

    expect(wrapper.text()).toContain('请填写知识标识')
    expect(knowledgeBaseApi.admin.createArticle).not.toHaveBeenCalled()
  })

  it('加载中禁用保存按钮，避免重复或半加载状态提交', async () => {
    let resolveCategories: (value: Array<{ id: number; code: string; name: string; sortOrder: number; status: 'ENABLED' }>) => void = () => undefined
    vi.mocked(knowledgeBaseApi.admin.categories).mockReturnValueOnce(new Promise((resolve) => {
      resolveCategories = resolve
    }))
    const { wrapper } = await mountEditor('/system/knowledge/create', false)

    const saveButton = wrapper.find('[data-test="save-knowledge-article"]')
    expect(saveButton.attributes('disabled')).toBeDefined()

    await saveButton.trigger('click')

    expect(knowledgeBaseApi.admin.createArticle).not.toHaveBeenCalled()
    resolveCategories([{ id: 1, code: 'procurement', name: '采购管理', sortOrder: 10, status: 'ENABLED' }])
    await flushPromises()
  })

  it('关联知识通过远程多选提交文章ID，不依赖手填标识', async () => {
    const { wrapper } = await mountEditor()

    const inputs = wrapper.findAll('input')
    const textareas = wrapper.findAll('textarea')
    await inputs[0].setValue('purchase-order-confirm')
    await inputs[1].setValue('采购订单如何确认')
    await wrapper.findAll('select')[0].setValue('1')
    await textareas[0].setValue('采购订单确认说明')
    await textareas[1].setValue('# 采购订单确认\n\n确认后生成到货计划。')
    await wrapper.findAll('select')[3].setValue(['22'])

    const saveButton = wrapper.find('[data-test="save-knowledge-article"]')
    expect(saveButton.attributes('disabled')).toBeUndefined()

    await saveButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('请填写')
    expect(knowledgeBaseApi.admin.createArticle).toHaveBeenCalledWith(expect.objectContaining({
      relatedArticleIds: ['22'],
    }))
  })

  it('保存失败时保留用户输入，便于修正后重试', async () => {
    vi.mocked(knowledgeBaseApi.admin.createArticle).mockRejectedValueOnce(new Error('服务端保存失败'))
    const { wrapper } = await mountEditor()

    const inputs = wrapper.findAll('input')
    const textareas = wrapper.findAll('textarea')
    await inputs[0].setValue('purchase-order-confirm')
    await inputs[1].setValue('采购订单如何确认')
    await wrapper.findAll('select')[0].setValue('1')
    await textareas[0].setValue('采购订单确认说明')
    await textareas[1].setValue('# 采购订单确认\n\n确认后生成到货计划。')

    await wrapper.find('[data-test="save-knowledge-article"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('服务端保存失败')
    expect((inputs[0].element as HTMLInputElement).value).toBe('purchase-order-confirm')
    expect((textareas[1].element as HTMLTextAreaElement).value).toContain('确认后生成到货计划')
  })
})
