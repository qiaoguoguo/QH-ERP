import { flushPromises, mount, type DOMWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { h, inject, provide, type VNodeChild } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CostRecordDetailRecord } from '../../shared/api/costCollectionApi'
import { useAuthStore } from '../../stores/authStore'
import CostRecordDetailView from './CostRecordDetailView.vue'

const costCollectionApiMock = vi.hoisted(() => ({
  records: {
    get: vi.fn(),
  },
}))

vi.mock('../../shared/api/costCollectionApi', () => ({
  costCollectionApi: costCollectionApiMock,
}))

const detailRecord: CostRecordDetailRecord = {
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
  updatedAt: '2026-07-05T10:30:00+08:00',
  workOrderStatus: 'IN_PROGRESS',
  sourceStatus: 'ACTIVE',
  sourceSummary: {
    sourceStatus: 'ACTIVE',
    sourceDocumentNo: 'MANUAL-001',
    quantity: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    unitId: null,
    unitName: null,
  },
  outputTrace: [
    {
      receiptId: 500,
      receiptNo: 'CR-001',
      workOrderId: 9,
      businessDate: '2026-07-05',
      receiptWarehouseId: 31,
      receiptWarehouseName: '成品仓',
      quantity: 40,
      beforeQuantity: 10,
      afterQuantity: 50,
      postedByName: '仓管员',
      postedAt: '2026-07-05T09:00:00+08:00',
    },
  ],
  auditSummary: [
    {
      id: 900,
      operatorUsername: 'cost_admin',
      action: 'MFG_COST_RECORD_UPDATE',
      createdAt: '2026-07-05T10:30:00+08:00',
    },
  ],
}

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
    template: '<section><header><slot name="actions" /><slot name="alerts" /></header><slot /></section>',
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

function buttonsByText(wrapper: ReturnType<typeof mount>, text: string): DOMWrapper<HTMLButtonElement>[] {
  return wrapper.findAll('button').filter((button) => button.text().trim() === text)
}

async function mountDetail(
  permissions = ['cost:record:view', 'cost:record:update'],
  path = '/cost/records/702',
) {
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
      { path: '/cost/records/:id', name: 'cost-record-detail', component: CostRecordDetailView },
      { path: '/cost/records/:id/edit', name: 'cost-record-edit', component: { render: () => null } },
      { path: '/cost/records', name: 'cost-records', component: { render: () => null } },
      { path: '/production/work-orders/:id', name: 'production-work-order-detail', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(CostRecordDetailView, {
    global: {
      plugins: [pinia, router],
      stubs,
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('成本记录详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    costCollectionApiMock.records.get.mockResolvedValue(detailRecord)
  })

  it('展示来源摘要、产出追溯和审计摘要', async () => {
    const { wrapper } = await mountDetail()

    expect(costCollectionApiMock.records.get).toHaveBeenCalledWith('702')
    expect(wrapper.text()).toContain('COST-002')
    expect(wrapper.text()).toContain('FG-001 成品 A')
    expect(wrapper.text()).toContain('制造费用业务记录')
    expect(wrapper.text()).toContain('来源摘要')
    expect(wrapper.text()).toContain('MANUAL-001')
    expect(wrapper.text()).toContain('完工入库产出追溯')
    expect(wrapper.text()).toContain('CR-001')
    expect(wrapper.text()).toContain('审计摘要')
    expect(wrapper.text()).toContain('cost_admin')
    expect(wrapper.text()).toContain('MFG_COST_RECORD_UPDATE')
  })

  it('可进入生产工单和手工成本编辑页', async () => {
    const { wrapper, router } = await mountDetail()

    await buttonsByText(wrapper, '生产工单')[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('production-work-order-detail')
    expect(router.currentRoute.value.params.id).toBe('9')

    await router.push('/cost/records/702')
    await router.isReady()
    await wrapper.find('[data-test="edit-cost-record-detail"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('cost-record-edit')
    expect(router.currentRoute.value.params.id).toBe('702')
  })

  it('从追溯来源进入详情后再编辑时保留原返回上下文', async () => {
    const { wrapper, router } = await mountDetail(
      ['cost:record:view', 'cost:record:update'],
      '/cost/records/702?returnTo=/reports/cost',
    )

    await wrapper.find('[data-test="edit-cost-record-detail"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('cost-record-edit')
    expect(router.currentRoute.value.query.returnTo).toBe('/reports/cost')
  })

  it('缺少更新权限时不展示编辑入口', async () => {
    const { wrapper } = await mountDetail(['cost:record:view'])

    expect(wrapper.find('[data-test="edit-cost-record-detail"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('COST-002')
  })

  it('加载失败时展示错误状态', async () => {
    costCollectionApiMock.records.get.mockRejectedValueOnce(new Error('成本记录不存在'))

    const { wrapper } = await mountDetail()

    expect(wrapper.text()).toContain('成本记录不存在')
    expect(wrapper.text()).not.toContain('COST-002')
  })
})
