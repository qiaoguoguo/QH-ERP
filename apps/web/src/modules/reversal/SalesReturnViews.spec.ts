import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SalesReturnDetailView from './SalesReturnDetailView.vue'
import SalesReturnFormView from './SalesReturnFormView.vue'
import SalesReturnListView from './SalesReturnListView.vue'
import ReversalStatusTag from './ReversalStatusTag.vue'
import { useAuthStore } from '../../stores/authStore'

const returnRefundReversalApiMock = vi.hoisted(() => ({
  salesReturns: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    post: vi.fn(),
    cancel: vi.fn(),
  },
  salesReturnSources: {
    list: vi.fn(),
  },
  traces: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/returnRefundReversalApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/returnRefundReversalApi')>()),
  returnRefundReversalApi: returnRefundReversalApiMock,
}))

const unrestrictedSource = {
  sourceType: 'SALES_SHIPMENT',
  sourceId: 10,
  sourceNo: 'SS202607050001',
  businessDate: '2026-07-05',
  status: 'POSTED',
  quantity: '10.000000',
  amount: '500.00',
  canViewSource: true,
  restricted: false,
  resourceRouteName: 'sales-shipment-detail',
  resourceRouteParams: { id: 10 },
}

const salesReturnDetail = {
  id: 1,
  returnNo: 'SR202607050001',
  customerId: 8,
  customerName: '示例客户',
  warehouseId: 2,
  warehouseName: '成品仓',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  totalQuantity: '2.000000',
  totalAmount: '100.00',
  source: unrestrictedSource,
  createdAt: '2026-07-05T10:00:00+08:00',
  updatedAt: '2026-07-05T10:00:00+08:00',
  clientRequestId: 'client-1',
  remark: '客户退货',
  lines: [
    {
      id: 11,
      lineNo: 10,
      sourceLineId: 101,
      materialId: 31,
      materialCode: 'FG-001',
      materialName: '示例成品',
      unitId: 1,
      unitName: '件',
      returnedQuantityBefore: '0.000000',
      returnableQuantityBefore: '10.000000',
      quantity: '2.000000',
      unitPrice: '50.00',
      amount: '100.00',
      reason: '客户退回',
      source: unrestrictedSource,
    },
  ],
  traces: [
    {
      traceKey: 'SALES_RETURN:1:0:SALES_SHIPMENT:10:0',
      direction: 'REVERSE_TO_SOURCE',
      source: unrestrictedSource,
      reverse: {
        sourceType: 'SALES_RETURN',
        sourceId: 1,
        sourceNo: 'SR202607050001',
        canViewSource: true,
        restricted: false,
        resourceRouteName: 'sales-return-detail',
        resourceRouteParams: { id: 1 },
      },
      businessDate: '2026-07-05',
      quantity: '2.000000',
      amount: '100.00',
      status: 'DRAFT',
      canViewResource: true,
      restricted: false,
      resourceRouteName: 'sales-shipment-detail',
      resourceRouteParams: { id: 10 },
    },
  ],
}

const salesReturnSource = {
  shipmentId: 10,
  shipmentNo: 'SS202607050001',
  customerId: 8,
  customerName: '示例客户',
  warehouseId: 2,
  warehouseName: '成品仓',
  businessDate: '2026-07-05',
  status: 'POSTED',
  lines: [
    {
      shipmentLineId: 101,
      salesOrderLineId: 201,
      lineNo: 10,
      materialId: 31,
      materialCode: 'FG-001',
      materialName: '示例成品',
      unitId: 1,
      unitName: '件',
      shippedQuantity: '10.000000',
      returnedQuantity: '0.000000',
      returnableQuantity: '10.000000',
      unitPrice: '50.00',
      returnableAmount: '500.00',
    },
  ],
}

const page = <T>(items: T[]) => ({ items, page: 1, pageSize: 20, total: items.length })

async function mountReversalView(component: Component, path: string, permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'sales-return-user', displayName: '销售退货员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { render: () => null } },
      { path: '/sales/returns', name: 'sales-returns', component },
      { path: '/sales/returns/create', name: 'sales-return-create', component },
      { path: '/sales/returns/:id', name: 'sales-return-detail', component },
      { path: '/sales/returns/:id/edit', name: 'sales-return-edit', component },
      { path: '/sales/shipments/:id', name: 'sales-shipment-detail', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('销售退货前端页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('confirm', vi.fn(() => true))
    returnRefundReversalApiMock.salesReturns.list.mockResolvedValue(page([salesReturnDetail]))
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValue(salesReturnDetail)
    returnRefundReversalApiMock.salesReturns.create.mockResolvedValue({ ...salesReturnDetail, status: 'DRAFT' })
    returnRefundReversalApiMock.salesReturns.update.mockResolvedValue({ ...salesReturnDetail, remark: '更新退货' })
    returnRefundReversalApiMock.salesReturns.post.mockResolvedValue({ ...salesReturnDetail, status: 'POSTED' })
    returnRefundReversalApiMock.salesReturns.cancel.mockResolvedValue({ ...salesReturnDetail, status: 'CANCELLED' })
    returnRefundReversalApiMock.salesReturnSources.list.mockResolvedValue(page([salesReturnSource]))
    returnRefundReversalApiMock.traces.list.mockResolvedValue(salesReturnDetail.traces)
  })

  it('状态标签展示销售退货中文状态', () => {
    expect(mount(ReversalStatusTag, { props: { status: 'DRAFT' }, global: { plugins: [ElementPlus] } }).text()).toContain('草稿')
    expect(mount(ReversalStatusTag, { props: { status: 'POSTED' }, global: { plugins: [ElementPlus] } }).text()).toContain('已过账')
    expect(mount(ReversalStatusTag, { props: { status: 'CANCELLED' }, global: { plugins: [ElementPlus] } }).text()).toContain('已取消')
  })

  it('销售退货列表支持筛选、创建入口、状态展示和权限按钮', async () => {
    const { wrapper, router } = await mountReversalView(SalesReturnListView, '/sales/returns', [
      'sales:return:view',
      'sales:return:create',
      'sales:return:update',
      'sales:return:post',
      'sales:return:cancel',
    ])

    expect(wrapper.text()).toContain('销售退货')
    expect(wrapper.text()).toContain('SR202607050001')
    expect(wrapper.text()).toContain('示例客户')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.find('[data-test="create-sales-return"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="edit-sales-return"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-sales-return"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-sales-return"]').exists()).toBe(true)

    await wrapper.find('input[name="sales-return-keyword"]').setValue('SR')
    await wrapper.find('input[name="sales-return-customer-id"]').setValue('8')
    await wrapper.find('[data-test="search-sales-returns"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.salesReturns.list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: 'SR',
      customerId: 8,
      page: 1,
    }))

    await wrapper.find('[data-test="create-sales-return"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-return-create')
  })

  it('销售退货列表在只读权限下隐藏创建和操作按钮', async () => {
    const { wrapper } = await mountReversalView(SalesReturnListView, '/sales/returns', ['sales:return:view'])

    expect(wrapper.text()).toContain('SR202607050001')
    expect(wrapper.find('[data-test="create-sales-return"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-sales-return"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="post-sales-return"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="cancel-sales-return"]').exists()).toBe(false)
  })

  it('销售退货表单加载候选来源并以字符串数量提交创建请求', async () => {
    const { wrapper, router } = await mountReversalView(SalesReturnFormView, '/sales/returns/create', ['sales:return:create'])

    expect(wrapper.text()).toContain('新建销售退货')
    expect(wrapper.text()).toContain('SS202607050001')
    expect(wrapper.text()).toContain('可退数量')
    await wrapper.find('input[name="sales-return-business-date"]').setValue('2026-07-05')
    await wrapper.find('textarea[name="sales-return-remark"]').setValue('客户退货')
    await wrapper.find('input[name="sales-return-line-quantity-101"]').setValue('2.000000')
    await wrapper.find('input[name="sales-return-line-reason-101"]').setValue('客户退回')
    await wrapper.find('[data-test="submit-sales-return"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.salesReturns.create).toHaveBeenCalledWith(expect.objectContaining({
      sourceShipmentId: 10,
      businessDate: '2026-07-05',
      remark: '客户退货',
      lines: [{ sourceShipmentLineId: 101, quantity: '2.000000', reason: '客户退回' }],
    }))
    expect(router.currentRoute.value.name).toBe('sales-return-detail')
  })

  it('销售退货详情展示来源、明细、追溯，过账和取消按钮按权限显示', async () => {
    const { wrapper, router } = await mountReversalView(SalesReturnDetailView, '/sales/returns/1', [
      'sales:return:view',
      'sales:return:update',
      'sales:return:post',
      'sales:return:cancel',
      'business:reversal:view',
    ])

    expect(wrapper.text()).toContain('销售退货详情')
    expect(wrapper.text()).toContain('SR202607050001')
    expect(wrapper.text()).toContain('SS202607050001')
    expect(wrapper.text()).toContain('示例成品')
    expect(wrapper.find('[data-test="edit-sales-return-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-sales-return-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-sales-return-detail"]').exists()).toBe(true)

    await wrapper.find('[data-test="open-reversal-trace"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.traces.list).toHaveBeenCalledWith({
      sourceType: 'SALES_RETURN',
      sourceId: 1,
      direction: 'REVERSE_TO_SOURCE',
      includeRestricted: true,
    })
    expect(wrapper.text()).toContain('反向追溯')

    await wrapper.find('[data-test="view-reversal-resource"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
    expect(router.currentRoute.value.params.id).toBe('10')

    await wrapper.find('[data-test="post-sales-return-detail"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.salesReturns.post).toHaveBeenCalledWith(1)
  })

  it('来源受限时只展示受限提示，不展示来源敏感字段或跳转入口', async () => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce({
      ...salesReturnDetail,
      source: {
        sourceType: 'SALES_SHIPMENT',
        canViewSource: false,
        restricted: true,
        restrictedMessage: '来源无查看权限',
      },
      lines: [
        {
          ...salesReturnDetail.lines[0],
          source: {
            sourceType: 'SALES_SHIPMENT_LINE',
            canViewSource: false,
            restricted: true,
            restrictedMessage: '来源无查看权限',
          },
        },
      ],
      traces: [],
    })
    const { wrapper } = await mountReversalView(SalesReturnDetailView, '/sales/returns/1', ['sales:return:view', 'business:reversal:view'])

    expect(wrapper.text()).toContain('来源无查看权限')
    expect(wrapper.text()).not.toContain('SS202607050001')
    expect(wrapper.text()).not.toContain('500.00')
    expect(wrapper.find('[data-test="view-source-document"]').exists()).toBe(false)
  })
})
