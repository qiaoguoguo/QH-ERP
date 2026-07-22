import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  createIdempotencyKey: () => 'sales-return-key',
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

const shipmentLineSource = {
  ...unrestrictedSource,
  sourceType: 'SALES_SHIPMENT_LINE',
  sourceLineId: 101,
  lineNo: 10,
  resourceRouteQuery: { lineId: 101 },
}

const salesReturnSourceView = {
  sourceType: 'SALES_RETURN',
  sourceId: 1,
  sourceNo: 'SR202607050001',
  canViewSource: true,
  restricted: false,
  resourceRouteName: 'sales-return-detail',
  resourceRouteParams: { id: 1 },
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
  version: 5,
  allowedActions: ['UPDATE', 'POST', 'CANCEL'],
  actionDisabledReason: null,
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
      source: shipmentLineSource,
    },
  ],
  traces: [
    {
      traceKey: 'SALES_RETURN:1:0:SALES_SHIPMENT_LINE:10:101',
      direction: 'REVERSE_TO_SOURCE',
      source: shipmentLineSource,
      reverse: salesReturnSourceView,
      businessDate: '2026-07-05',
      quantity: '2.000000',
      amount: '100.00',
      status: 'DRAFT',
      canViewResource: true,
      restricted: false,
      resourceRouteName: 'sales-return-detail',
      resourceRouteParams: { id: 1 },
    },
  ],
}

const inventoryMovementTrace = {
  traceKey: 'SALES_SHIPMENT_LINE:10:101:SALES_RETURN:1:0:INVENTORY_MOVEMENT:701',
  direction: 'SOURCE_TO_REVERSE',
  source: shipmentLineSource,
  reverse: salesReturnSourceView,
  inventoryMovementId: 701,
  businessDate: '2026-07-05',
  quantity: '2.000000',
  amount: '100.00',
  status: 'POSTED',
  canViewResource: true,
  restricted: false,
  resourceRouteName: 'inventory-movements',
  resourceRouteQuery: { sourceId: 701 },
}

const receivableAdjustmentTrace = {
  traceKey: 'SALES_SHIPMENT_LINE:10:101:SALES_RETURN:1:0:SETTLEMENT_ADJUSTMENT:801',
  direction: 'SOURCE_TO_REVERSE',
  source: shipmentLineSource,
  reverse: salesReturnSourceView,
  settlementAdjustmentId: 801,
  businessDate: '2026-07-05',
  amount: '100.00',
  status: 'POSTED',
  canViewResource: true,
  restricted: false,
  resourceRouteName: 'finance-receivable-detail',
  resourceRouteParams: { id: 801 },
}

const restrictedInventoryTrace = {
  traceKey: 'SALES_SHIPMENT_LINE:10:101:SALES_RETURN:1:0:INVENTORY_MOVEMENT:999',
  direction: 'SOURCE_TO_REVERSE',
  source: {
    sourceType: 'SALES_SHIPMENT_LINE',
    canViewSource: false,
    restricted: true,
    restrictedMessage: '来源无查看权限',
  },
  reverse: salesReturnSourceView,
  inventoryMovementId: 999,
  businessDate: '2026-07-05',
  quantity: '9.000000',
  amount: '999.00',
  status: 'POSTED',
  canViewResource: false,
  restricted: true,
  restrictedMessage: '来源无查看权限',
}

const impactedSalesReturnDetail = {
  ...salesReturnDetail,
  status: 'POSTED',
  lines: salesReturnDetail.lines.map((line) => ({ ...line, stockMovementId: 701 })),
  traces: [
    salesReturnDetail.traces[0],
    inventoryMovementTrace,
    receivableAdjustmentTrace,
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

const page = <T>(items: T[], pageSize = 10) => ({ items, page: 1, pageSize, total: items.length })

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
      { path: '/inventory/movements', name: 'inventory-movements', component: { render: () => null } },
      { path: '/finance/receivables/:id', name: 'finance-receivable-detail', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, {
    attachTo: document.body,
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function openMoreActions(wrapper: ReturnType<typeof mount>) {
  const moreButton = wrapper.findAll('button').find((button) => button.text() === '更多')
  expect(moreButton).toBeTruthy()
  await moreButton!.trigger('click')
  await flushPromises()
}

function teleportedAction(testId: string): HTMLElement {
  const actions = Array.from(document.body.querySelectorAll<HTMLElement>(`[data-test="${testId}"]`))
  const action = actions.at(-1)
  expect(action).not.toBeNull()
  return action!
}

async function clickTeleportedAction(testId: string) {
  teleportedAction(testId).click()
  await flushPromises()
}

describe('销售退货前端页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    returnRefundReversalApiMock.salesReturns.list.mockResolvedValue(page([salesReturnDetail]))
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValue(salesReturnDetail)
    returnRefundReversalApiMock.salesReturns.create.mockResolvedValue({ ...salesReturnDetail, status: 'DRAFT' })
    returnRefundReversalApiMock.salesReturns.update.mockResolvedValue({ ...salesReturnDetail, remark: '更新退货' })
    returnRefundReversalApiMock.salesReturns.post.mockResolvedValue({ ...salesReturnDetail, status: 'POSTED' })
    returnRefundReversalApiMock.salesReturns.cancel.mockResolvedValue({ ...salesReturnDetail, status: 'CANCELLED' })
    returnRefundReversalApiMock.salesReturnSources.list.mockResolvedValue(page([salesReturnSource], 20))
    returnRefundReversalApiMock.traces.list.mockResolvedValue(salesReturnDetail.traces)
  })

  afterEach(() => {
    document.body.innerHTML = ''
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
    await openMoreActions(wrapper)
    expect(teleportedAction('post-sales-return')).toBeTruthy()
    expect(teleportedAction('cancel-sales-return')).toBeTruthy()

    await clickTeleportedAction('post-sales-return')
    expect(returnRefundReversalApiMock.salesReturns.post).toHaveBeenCalledWith(1, {
      version: 5,
      idempotencyKey: 'sales-return-key',
    })

    await openMoreActions(wrapper)
    await clickTeleportedAction('cancel-sales-return')
    expect(returnRefundReversalApiMock.salesReturns.cancel).toHaveBeenCalledWith(1, {
      version: 5,
      reason: '用户取消销售退货',
      idempotencyKey: 'sales-return-key',
    })

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
    expect(document.body.querySelector('[data-test="post-sales-return"]')).toBeNull()
    expect(document.body.querySelector('[data-test="cancel-sales-return"]')).toBeNull()
  })

  it('销售退货列表仅按 allowedActions 和权限展示公开状态动作', async () => {
    returnRefundReversalApiMock.salesReturns.list.mockResolvedValueOnce(page([
      {
        ...salesReturnDetail,
        allowedActions: ['POST'],
        actionDisabledReason: '当前只能过账',
      },
    ]))
    const { wrapper } = await mountReversalView(SalesReturnListView, '/sales/returns', [
      'sales:return:view',
      'sales:return:update',
      'sales:return:post',
      'sales:return:cancel',
    ])

    expect(wrapper.find('[data-test="edit-sales-return"]').exists()).toBe(false)
    await openMoreActions(wrapper)
    expect(teleportedAction('post-sales-return')).toBeTruthy()
    expect(document.body.querySelector('[data-test="cancel-sales-return"]')).toBeNull()
  })

  it('销售退货表单加载候选来源并以字符串数量提交创建请求', async () => {
    const { wrapper, router } = await mountReversalView(SalesReturnFormView, '/sales/returns/create', ['sales:return:create'])

    expect(wrapper.text()).toContain('新建销售退货')
    expect(wrapper.text()).toContain('SS202607050001')
    expect(wrapper.text()).toContain('可退数量')
    expect(returnRefundReversalApiMock.salesReturnSources.list).toHaveBeenCalledWith({
      keyword: '',
      page: 1,
      pageSize: 20,
    })
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

  it('销售退货新建页从来源行继承追踪身份并提交原 sourceAllocationId', async () => {
    returnRefundReversalApiMock.salesReturnSources.list.mockResolvedValueOnce(page([
      {
        ...salesReturnSource,
        lines: salesReturnSource.lines.map((line) => ({
          ...line,
          trackingAllocations: [
            {
              sourceAllocationId: 901,
              batchId: 71,
              batchNo: 'B-SH-001',
              quantity: '2.000000',
              qualityStatusName: '合格',
              sourceDocumentNo: 'SS202607050001',
            },
          ],
        })),
      },
    ], 20))
    const { wrapper } = await mountReversalView(SalesReturnFormView, '/sales/returns/create', ['sales:return:create'])

    expect(wrapper.text()).toContain('B-SH-001')
    expect(wrapper.text()).toContain('来源继承，不可改选')

    await wrapper.find('input[name="sales-return-business-date"]').setValue('2026-07-05')
    await wrapper.find('input[name="sales-return-line-quantity-101"]').setValue('2.000000')
    await wrapper.find('input[name="sales-return-line-reason-101"]').setValue('客户退回')
    await wrapper.find('[data-test="submit-sales-return"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.salesReturns.create).toHaveBeenCalledWith(expect.objectContaining({
      lines: [
        {
          sourceShipmentLineId: 101,
          quantity: '2.000000',
          trackingAllocations: [
            { sourceAllocationId: 901, batchId: 71, quantity: '2.000000' },
          ],
          reason: '客户退回',
        },
      ],
    }))
  })

  it('编辑销售退货来源受限时可保存草稿行且不提交来源主键', async () => {
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
          sourceLineId: undefined,
          source: {
            sourceType: 'SALES_SHIPMENT_LINE',
            canViewSource: false,
            restricted: true,
            restrictedMessage: '来源无查看权限',
          },
        },
      ],
    })
    const { wrapper } = await mountReversalView(SalesReturnFormView, '/sales/returns/1/edit', ['sales:return:update'])

    expect(wrapper.text()).toContain('来源无查看权限')
    expect(wrapper.text()).not.toContain('SS202607050001')

    await wrapper.find('input[name="sales-return-line-quantity-11"]').setValue('3.000000')
    await wrapper.find('input[name="sales-return-line-reason-11"]').setValue('受限来源更新')
    await wrapper.find('[data-test="submit-sales-return"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.salesReturns.update).toHaveBeenCalledWith('1', expect.objectContaining({
      businessDate: '2026-07-05',
      remark: '客户退货',
      lines: [{ id: 11, quantity: '3.000000', reason: '受限来源更新' }],
    }))
    const payload = returnRefundReversalApiMock.salesReturns.update.mock.calls[0][1]
    expect(payload).not.toHaveProperty('sourceShipmentId')
    expect(payload.lines[0]).not.toHaveProperty('sourceShipmentLineId')
  })

  it('销售退货编辑页只读回显来源追踪身份并提交原 sourceAllocationId', async () => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce({
      ...salesReturnDetail,
      lines: [
        {
          ...salesReturnDetail.lines[0],
          trackingAllocations: [
            {
              sourceAllocationId: 901,
              batchId: 71,
              batchNo: 'B-SH-001',
              quantity: '2.000000',
              qualityStatusName: '合格',
              sourceDocumentNo: 'SS202607050001',
            },
          ],
        },
      ],
    })
    const { wrapper } = await mountReversalView(SalesReturnFormView, '/sales/returns/1/edit', ['sales:return:update'])

    expect(wrapper.text()).toContain('B-SH-001')
    expect(wrapper.text()).toContain('SS202607050001')
    expect(wrapper.find('[data-test="open-sales-return-tracking-0"]').exists()).toBe(false)

    await wrapper.find('input[name="sales-return-line-quantity-101"]').setValue('2.000000')
    await wrapper.find('input[name="sales-return-line-reason-101"]').setValue('客户退回')
    await wrapper.find('[data-test="submit-sales-return"]').trigger('click')
    await flushPromises()

    const payload = returnRefundReversalApiMock.salesReturns.update.mock.calls[0][1]
    expect(payload.lines[0].trackingAllocations).toEqual([
      {
        sourceAllocationId: 901,
        batchId: 71,
        quantity: '2.000000',
      },
    ])
  })

  it.each([
    ['POSTED', '已过账'],
    ['CANCELLED', '已取消'],
  ])('直接进入%s销售退货编辑页时显示不可编辑提示并隐藏保存入口', async (status, statusText) => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce({
      ...salesReturnDetail,
      status,
    })
    const { wrapper, router } = await mountReversalView(SalesReturnFormView, '/sales/returns/1/edit', ['sales:return:update'])

    expect(wrapper.text()).toContain(`当前销售退货${statusText}，不可编辑`)
    expect(wrapper.find('[data-test="submit-sales-return"]').exists()).toBe(false)
    expect(wrapper.find('input[name="sales-return-business-date"]').exists()).toBe(false)
    expect(returnRefundReversalApiMock.salesReturns.update).not.toHaveBeenCalled()

    await wrapper.find('[data-test="return-sales-return-detail"]').trigger('click')
    await flushPromises()
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

    await wrapper.find('[data-test="view-reversal-source"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
    expect(router.currentRoute.value.params.id).toBe('10')
    expect(router.currentRoute.value.query.lineId).toBe('101')

    await wrapper.find('[data-test="post-sales-return-detail"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.salesReturns.post).toHaveBeenCalledWith(1, {
      version: 5,
      idempotencyKey: 'sales-return-key',
    })

    await wrapper.find('[data-test="cancel-sales-return-detail"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.salesReturns.cancel).toHaveBeenCalledWith(1, {
      version: 5,
      reason: '用户取消销售退货',
      idempotencyKey: 'sales-return-key',
    })
  })

  it('销售退货详情仅按 allowedActions 和权限展示公开状态动作', async () => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce({
      ...salesReturnDetail,
      allowedActions: ['CANCEL'],
      actionDisabledReason: '当前只能取消',
    })
    const { wrapper } = await mountReversalView(SalesReturnDetailView, '/sales/returns/1', [
      'sales:return:view',
      'sales:return:update',
      'sales:return:post',
      'sales:return:cancel',
    ])

    expect(wrapper.find('[data-test="edit-sales-return-detail"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="post-sales-return-detail"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="cancel-sales-return-detail"]').exists()).toBe(true)
  })

  it('销售退货详情只读展示来源继承的批次或序列身份', async () => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce({
      ...salesReturnDetail,
      lines: [
        {
          ...salesReturnDetail.lines[0],
          trackingAllocations: [
            {
              sourceAllocationId: 901,
              batchId: 71,
              batchNo: 'B-SH-001',
              quantity: '2.000000',
              qualityStatusName: '合格',
              sourceDocumentNo: 'SS202607050001',
            },
          ],
        },
      ],
    })
    const { wrapper } = await mountReversalView(SalesReturnDetailView, '/sales/returns/1', ['sales:return:view', 'business:reversal:view'])

    expect(wrapper.text()).toContain('批次/序列')
    expect(wrapper.text()).toContain('B-SH-001')
    expect(wrapper.text()).toContain('SS202607050001')
  })

  it('销售退货详情明确展示库存入库影响和应收冲减影响', async () => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce(impactedSalesReturnDetail)
    const { wrapper } = await mountReversalView(SalesReturnDetailView, '/sales/returns/1', ['sales:return:view', 'business:reversal:view'])

    expect(wrapper.text()).toContain('库存入库影响')
    expect(wrapper.text()).toContain('库存流水')
    expect(wrapper.text()).toContain('库存流水 #701')
    expect(wrapper.text()).toContain('应收冲减影响')
    expect(wrapper.text()).toContain('应收冲减')
    expect(wrapper.text()).toContain('应收冲减 #801')
  })

  it('销售退货详情用退货行普通字段补齐缺物料的库存入库影响 trace', async () => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce({
      ...salesReturnDetail,
      status: 'POSTED',
      lines: salesReturnDetail.lines.map((line) => ({
        ...line,
        stockMovementId: 701,
      })),
      traces: [
        salesReturnDetail.traces[0],
        {
          ...inventoryMovementTrace,
          materialCode: null,
          materialName: null,
          warehouseName: null,
          quantity: undefined,
        },
      ],
    })
    const { wrapper } = await mountReversalView(SalesReturnDetailView, '/sales/returns/1', ['sales:return:view', 'business:reversal:view'])

    const inventoryTable = wrapper.findAllComponents({ name: 'ElTable' })
      .find((table) => {
        const rows = table.props('data') as Array<Record<string, unknown>>
        return rows.some((row) => row.inventoryMovementId === 701)
      })
    expect(inventoryTable?.props('data')).toEqual(expect.arrayContaining([
      expect.objectContaining({
        inventoryMovementId: 701,
        materialCode: 'FG-001',
        materialName: '示例成品',
        warehouseName: '成品仓',
        quantity: '2.000000',
      }),
    ]))
  })

  it('销售退货详情在无内部库存流水 ID 时仍按退货行普通字段展示库存影响', async () => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce({
      ...salesReturnDetail,
      status: 'POSTED',
      lines: salesReturnDetail.lines.map((line) => ({
        ...line,
        stockMovementId: null,
      })),
      traces: [],
    })
    const { wrapper } = await mountReversalView(SalesReturnDetailView, '/sales/returns/1', ['sales:return:view'])

    expect(wrapper.text()).toContain('库存入库影响')
    expect(wrapper.text()).toContain('FG-001 示例成品')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).toContain('内部库存流水编号已隐藏')
    expect(wrapper.text()).not.toContain('暂无库存入库影响')
    expect(wrapper.text()).not.toContain('库存流水 #')
  })

  it('反向追溯面板按来源、反向单据和影响资源分别跳转', async () => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce(impactedSalesReturnDetail)
    returnRefundReversalApiMock.traces.list.mockResolvedValueOnce([
      salesReturnDetail.traces[0],
      inventoryMovementTrace,
      receivableAdjustmentTrace,
    ])
    const { wrapper, router } = await mountReversalView(SalesReturnDetailView, '/sales/returns/1', ['sales:return:view', 'business:reversal:view'])

    await wrapper.find('[data-test="open-reversal-trace"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('销售出库行')
    expect(wrapper.text()).toContain('销售退货')
    expect(wrapper.text()).toContain('库存入库影响')
    expect(wrapper.text()).toContain('应收冲减')

    await wrapper.find('[data-test="view-reversal-source"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
    expect(router.currentRoute.value.params.id).toBe('10')
    expect(router.currentRoute.value.query.lineId).toBe('101')

    await wrapper.find('[data-test="view-reversal-reverse"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-return-detail')
    expect(router.currentRoute.value.params.id).toBe('1')

    const impactButtons = wrapper.findAll('[data-test="view-reversal-impact-resource"]')
    expect(impactButtons).toHaveLength(2)
    await impactButtons[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('inventory-movements')
    expect(router.currentRoute.value.query.sourceId).toBe('701')

    await impactButtons[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('finance-receivable-detail')
    expect(router.currentRoute.value.params.id).toBe('801')
  })

  it('反向追溯面板展示资源类型并在受限记录中隐藏敏感字段', async () => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce(impactedSalesReturnDetail)
    returnRefundReversalApiMock.traces.list.mockResolvedValueOnce([
      salesReturnDetail.traces[0],
      inventoryMovementTrace,
      receivableAdjustmentTrace,
      restrictedInventoryTrace,
    ])
    const { wrapper } = await mountReversalView(SalesReturnDetailView, '/sales/returns/1', ['sales:return:view', 'business:reversal:view'])

    await wrapper.find('[data-test="open-reversal-trace"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('销售出库来源')
    expect(wrapper.text()).toContain('销售退货')
    expect(wrapper.text()).toContain('库存流水')
    expect(wrapper.text()).toContain('应收冲减')
    expect(wrapper.text()).toContain('来源无查看权限')
    expect(wrapper.text()).not.toContain('IM-SECRET')
    expect(wrapper.text()).not.toContain('999.00')
  })

  it('反向追溯来源受限时不展示跳转和敏感字段', async () => {
    returnRefundReversalApiMock.salesReturns.get.mockResolvedValueOnce(impactedSalesReturnDetail)
    returnRefundReversalApiMock.traces.list.mockResolvedValueOnce([restrictedInventoryTrace])
    const { wrapper } = await mountReversalView(SalesReturnDetailView, '/sales/returns/1', ['sales:return:view', 'business:reversal:view'])

    await wrapper.find('[data-test="open-reversal-trace"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('来源无查看权限')
    expect(wrapper.text()).not.toContain('SS-SECRET')
    expect(wrapper.text()).not.toContain('999.00')
    expect(wrapper.find('[data-test="view-reversal-source"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="view-reversal-impact-resource"]').exists()).toBe(false)
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
