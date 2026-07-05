import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { h, inject, provide, type VNodeChild } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { CostRecordSummaryRecord } from '../../shared/api/costCollectionApi'
import { useAuthStore } from '../../stores/authStore'
import CostRecordListView from './CostRecordListView.vue'

const costCollectionApiMock = vi.hoisted(() => ({
  records: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/costCollectionApi', () => ({
  costCollectionApi: costCollectionApiMock,
}))

function pageResult<T>(items: T[], total = items.length): PageResult<T> {
  return { items, total, page: 1, pageSize: 10 }
}

const records: CostRecordSummaryRecord[] = [
  {
    id: 701,
    recordNo: 'COST-001',
    workOrderId: 9,
    workOrderNo: 'WO-001',
    productMaterialId: 10,
    productMaterialCode: 'FG-001',
    productMaterialName: '成品 A',
    costType: 'MATERIAL',
    sourceType: 'AUTO_PRODUCTION',
    sourceDocumentType: 'PRODUCTION_MATERIAL_ISSUE',
    sourceDocumentNo: 'MI-001',
    sourceDocumentId: 300,
    sourceLineId: 301,
    basisType: 'SOURCE_QUANTITY_ONLY',
    materialId: 11,
    materialCode: 'RM-001',
    materialName: '原材料 A',
    unitId: 3,
    unitName: '千克',
    quantity: 80,
    unitPrice: null,
    amount: null,
    businessDate: '2026-07-04',
    status: 'ACTIVE',
    remark: null,
    recordedByName: '仓管员',
    recordedAt: '2026-07-04T09:00:00+08:00',
    createdByName: '仓管员',
    createdAt: '2026-07-04T09:00:00+08:00',
    updatedAt: '2026-07-04T09:00:00+08:00',
  },
  {
    id: 702,
    recordNo: 'COST-002',
    workOrderId: 9,
    workOrderNo: 'WO-001',
    productMaterialId: 10,
    productMaterialCode: 'FG-001',
    productMaterialName: '成品 A',
    costType: 'MANUFACTURING_OVERHEAD',
    sourceType: 'MANUAL_ENTRY',
    sourceDocumentType: 'MANUAL_COST_RECORD',
    sourceDocumentNo: 'MANUAL-001',
    sourceDocumentId: null,
    sourceLineId: null,
    basisType: 'MANUAL_AMOUNT',
    materialId: null,
    materialCode: null,
    materialName: null,
    unitId: null,
    unitName: null,
    quantity: null,
    unitPrice: null,
    amount: 300,
    businessDate: '2026-07-05',
    status: 'ACTIVE',
    remark: '制造费用业务记录',
    recordedByName: '成本管理员',
    recordedAt: '2026-07-05T10:00:00+08:00',
    createdByName: '成本管理员',
    createdAt: '2026-07-05T10:00:00+08:00',
    updatedAt: '2026-07-05T10:00:00+08:00',
  },
]

const tableRowKey = Symbol('table-row')
const RowScope = {
  props: ['row'],
  setup(props: { row: unknown }, { slots }: { slots: { default?: () => VNodeChild } }) {
    provide(tableRowKey, props.row)
    return () => h('div', { class: 'table-row' }, slots.default?.() ?? undefined)
  },
}

const stubs = {
  MasterDataTableView: {
    props: ['title', 'description'],
    template: '<section><header><slot name="actions" /><slot name="filters" /><slot name="alerts" /></header><slot /></section>',
  },
  CostSourceTypeTag: {
    props: ['type'],
    template: '<span>{{ type }}</span>',
  },
  CostTypeTag: {
    props: ['type'],
    template: '<span>{{ type }}</span>',
  },
  ElAlert: {
    props: ['title'],
    template: '<div>{{ title }}</div>',
  },
  ElButton: {
    props: ['disabled', 'loading', 'type'],
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\', $event)"><slot /></button>',
  },
  ElEmpty: {
    props: ['description'],
    template: '<div>{{ description }}</div>',
  },
  ElForm: {
    template: '<form><slot /></form>',
  },
  ElFormItem: {
    props: ['label'],
    template: '<label><span>{{ label }}</span><slot /></label>',
  },
  ElInput: {
    props: ['modelValue', 'disabled', 'name', 'placeholder'],
    emits: ['update:modelValue'],
    template:
      '<input :name="name" :disabled="disabled" :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElDatePicker: {
    props: ['modelValue', 'disabled', 'name', 'placeholder'],
    emits: ['update:modelValue'],
    template:
      '<input type="date" :name="name" :disabled="disabled" :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElOption: {
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>',
  },
  ElPagination: {
    props: ['total', 'currentPage'],
    emits: ['current-change'],
    template: '<nav><span>总数 {{ total }}</span><button data-test="next-page" @click="$emit(\'current-change\', 2)">下一页</button></nav>',
  },
  ElSelect: {
    props: ['modelValue', 'disabled', 'placeholder'],
    emits: ['update:modelValue'],
    template:
      '<select :disabled="disabled" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value || undefined)"><option value="">{{ placeholder }}</option><slot /></select>',
  },
  ElTable: {
    props: ['data', 'emptyText'],
    setup(props: { data?: unknown[]; emptyText?: string }, { slots }: { slots: { default?: () => VNodeChild } }) {
      return () => h('div', { class: 'table' }, props.data?.length
        ? props.data.map((row) => h(RowScope, { row }, () => slots.default?.()))
        : h('div', props.emptyText))
    },
  },
  ElTableColumn: {
    props: ['prop', 'label'],
    setup(props: { prop?: string; label?: string }, { slots }: { slots: { default?: (scope: { row: Record<string, unknown> }) => VNodeChild } }) {
      const row = inject<Record<string, unknown> | null>(tableRowKey, null)
      return () => h('span', { class: 'table-cell' }, [
        props.label ? h('span', { class: 'column-label' }, props.label) : null,
        slots.default && row ? slots.default({ row }) : props.prop && row ? String(row[props.prop] ?? '') : '',
      ])
    },
  },
}

async function mountList(permissions = ['cost:record:view', 'cost:record:create', 'cost:record:update']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'cost-admin', displayName: '成本管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/cost/records', name: 'cost-records', component: CostRecordListView },
      { path: '/cost/records/create', name: 'cost-record-create', component: { render: () => null } },
      { path: '/cost/records/:id', name: 'cost-record-detail', component: { render: () => null } },
      { path: '/cost/records/:id/edit', name: 'cost-record-edit', component: { render: () => null } },
    ],
  })
  await router.push('/cost/records')
  await router.isReady()
  const wrapper = mount(CostRecordListView, {
    global: {
      plugins: [pinia, router],
      stubs,
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('成本记录列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    costCollectionApiMock.records.list.mockResolvedValue(pageResult(records, 2))
  })

  it('展示成本记录并按权限显示新增和手工记录编辑入口', async () => {
    const { wrapper } = await mountList()

    expect(wrapper.text()).toContain('COST-001')
    expect(wrapper.text()).toContain('COST-002')
    expect(wrapper.text()).toContain('WO-001')
    expect(wrapper.text()).toContain('新增手工成本')
    expect(wrapper.find('[data-test="edit-cost-record"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-test="edit-cost-record"]')).toHaveLength(1)
  })

  it('只读权限不显示新增和编辑入口', async () => {
    const { wrapper } = await mountList(['cost:record:view'])

    expect(wrapper.text()).not.toContain('新增手工成本')
    expect(wrapper.find('[data-test="edit-cost-record"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('COST-001')
  })

  it('按筛选条件查询、重置并分页', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('input[name="cost-record-keyword"]').setValue('WO-001')
    await wrapper.findAll('select')[0].setValue('MATERIAL')
    await wrapper.findAll('select')[1].setValue('AUTO_PRODUCTION')
    await wrapper.findAll('select')[2].setValue('PRODUCTION_MATERIAL_ISSUE')
    await wrapper.find('input[name="cost-source-document-no"]').setValue('MI-001')
    await wrapper.find('input[name="cost-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="cost-date-to"]').setValue('2026-07-31')
    await wrapper.find('[data-test="search-cost-records"]').trigger('click')
    await flushPromises()

    expect(costCollectionApiMock.records.list).toHaveBeenLastCalledWith(expect.objectContaining({
      costType: 'MATERIAL',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      keyword: 'WO-001',
      page: 1,
      sourceDocumentNo: 'MI-001',
      sourceDocumentType: 'PRODUCTION_MATERIAL_ISSUE',
      sourceType: 'AUTO_PRODUCTION',
    }))

    await wrapper.find('[data-test="next-page"]').trigger('click')
    await flushPromises()
    expect(costCollectionApiMock.records.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))

    await wrapper.find('[data-test="reset-cost-records"]').trigger('click')
    await flushPromises()
    expect(costCollectionApiMock.records.list).toHaveBeenLastCalledWith(expect.objectContaining({
      costType: undefined,
      dateFrom: '',
      dateTo: '',
      keyword: '',
      page: 1,
      sourceDocumentNo: '',
      sourceDocumentType: undefined,
      sourceType: undefined,
    }))
  })

  it('展示空状态和加载失败错误', async () => {
    costCollectionApiMock.records.list.mockResolvedValueOnce(pageResult([], 0))
    const empty = await mountList()
    expect(empty.wrapper.text()).toContain('暂无成本业务记录')

    costCollectionApiMock.records.list.mockRejectedValueOnce(new Error('成本记录加载失败'))
    const failed = await mountList()
    expect(failed.wrapper.text()).toContain('成本记录加载失败')
    expect(failed.wrapper.text()).toContain('暂无成本业务记录')
  })
})
