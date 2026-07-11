import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PurchaseReturnDetailView from './PurchaseReturnDetailView.vue'
import PurchaseReturnFormView from './PurchaseReturnFormView.vue'
import PurchaseReturnListView from './PurchaseReturnListView.vue'
import { useAuthStore } from '../../stores/authStore'
import TrackingPickerDrawer from '../inventory/tracking/TrackingPickerDrawer.vue'

const returnRefundReversalApiMock = vi.hoisted(() => ({
  purchaseReturns: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    post: vi.fn(),
    cancel: vi.fn(),
  },
  purchaseReturnSources: {
    list: vi.fn(),
  },
  traces: {
    list: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  materials: {
    get: vi.fn(),
  },
}))

const inventoryApiMock = vi.hoisted(() => ({
  batches: {
    list: vi.fn(),
  },
  serials: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/returnRefundReversalApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/returnRefundReversalApi')>()),
  returnRefundReversalApi: returnRefundReversalApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../shared/api/inventoryApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/inventoryApi')>()),
  inventoryApi: inventoryApiMock,
}))

const unrestrictedSource = {
  sourceType: 'PURCHASE_RECEIPT',
  sourceId: 20,
  sourceNo: 'RC202607050001',
  businessDate: '2026-07-05',
  status: 'POSTED',
  quantity: '8.000000',
  amount: '640.00',
  canViewSource: true,
  restricted: false,
  resourceRouteName: 'procurement-receipt-detail',
  resourceRouteParams: { id: 20 },
}

const receiptLineSource = {
  ...unrestrictedSource,
  sourceType: 'PURCHASE_RECEIPT_LINE',
  sourceLineId: 201,
  lineNo: 10,
  resourceRouteQuery: { lineId: 201 },
}

const purchaseReturnSourceView = {
  sourceType: 'PURCHASE_RETURN',
  sourceId: 2,
  sourceNo: 'PR202607050001',
  canViewSource: true,
  restricted: false,
  resourceRouteName: 'procurement-return-detail',
  resourceRouteParams: { id: 2 },
}

const purchaseReturnDetail = {
  id: 2,
  returnNo: 'PR202607050001',
  supplierId: 9,
  supplierName: '示例供应商',
  warehouseId: 3,
  warehouseName: '原料仓',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  totalQuantity: '1.500000',
  totalAmount: '120.00',
  source: unrestrictedSource,
  createdAt: '2026-07-05T11:00:00+08:00',
  updatedAt: '2026-07-05T11:00:00+08:00',
  clientRequestId: 'client-2',
  remark: '来料退回',
  lines: [
    {
      id: 21,
      lineNo: 10,
      sourceLineId: 201,
      materialId: 41,
      materialCode: 'RM-001',
      materialName: '示例原料',
      unitId: 1,
      unitName: 'kg',
      returnedQuantityBefore: '0.000000',
      returnableQuantityBefore: '8.000000',
      quantity: '1.500000',
      unitPrice: '80.00',
      amount: '120.00',
      reason: '来料退回',
      source: receiptLineSource,
    },
  ],
  traces: [
    {
      traceKey: 'PURCHASE_RETURN:2:0:PURCHASE_RECEIPT_LINE:20:201',
      direction: 'REVERSE_TO_SOURCE',
      source: receiptLineSource,
      reverse: purchaseReturnSourceView,
      businessDate: '2026-07-05',
      quantity: '1.500000',
      amount: '120.00',
      status: 'DRAFT',
      canViewResource: true,
      restricted: false,
      resourceRouteName: 'procurement-return-detail',
      resourceRouteParams: { id: 2 },
    },
  ],
}

const inventoryMovementTrace = {
  traceKey: 'PURCHASE_RECEIPT_LINE:20:201:PURCHASE_RETURN:2:0:INVENTORY_MOVEMENT:702',
  direction: 'SOURCE_TO_REVERSE',
  source: receiptLineSource,
  reverse: purchaseReturnSourceView,
  inventoryMovementId: 702,
  businessDate: '2026-07-05',
  quantity: '1.500000',
  amount: '120.00',
  status: 'POSTED',
  canViewResource: true,
  restricted: false,
  resourceRouteName: 'inventory-movements',
  resourceRouteQuery: { sourceId: 702 },
}

const payableAdjustmentTrace = {
  traceKey: 'PURCHASE_RECEIPT_LINE:20:201:PURCHASE_RETURN:2:0:SETTLEMENT_ADJUSTMENT:802',
  direction: 'SOURCE_TO_REVERSE',
  source: receiptLineSource,
  reverse: purchaseReturnSourceView,
  settlementAdjustmentId: 802,
  businessDate: '2026-07-05',
  amount: '120.00',
  status: 'POSTED',
  canViewResource: true,
  restricted: false,
  resourceRouteName: 'finance-payable-detail',
  resourceRouteParams: { id: 802 },
}

const restrictedInventoryTrace = {
  traceKey: 'PURCHASE_RECEIPT_LINE:20:201:PURCHASE_RETURN:2:0:INVENTORY_MOVEMENT:999',
  direction: 'SOURCE_TO_REVERSE',
  source: {
    sourceType: 'PURCHASE_RECEIPT_LINE',
    canViewSource: false,
    restricted: true,
    restrictedMessage: '来源无查看权限',
  },
  reverse: purchaseReturnSourceView,
  inventoryMovementId: 999,
  businessDate: '2026-07-05',
  quantity: '9.000000',
  amount: '999.00',
  status: 'POSTED',
  canViewResource: false,
  restricted: true,
  restrictedMessage: '来源无查看权限',
}

const impactedPurchaseReturnDetail = {
  ...purchaseReturnDetail,
  status: 'POSTED',
  lines: purchaseReturnDetail.lines.map((line) => ({ ...line, stockMovementId: 702 })),
  traces: [
    purchaseReturnDetail.traces[0],
    inventoryMovementTrace,
    payableAdjustmentTrace,
  ],
}

const purchaseReturnSource = {
  receiptId: 20,
  receiptNo: 'RC202607050001',
  supplierId: 9,
  supplierName: '示例供应商',
  warehouseId: 3,
  warehouseName: '原料仓',
  businessDate: '2026-07-05',
  status: 'POSTED',
  lines: [
    {
      receiptLineId: 201,
      purchaseOrderLineId: 301,
      lineNo: 10,
      materialId: 41,
      materialCode: 'RM-001',
      materialName: '示例原料',
      unitId: 1,
      unitName: 'kg',
      receivedQuantity: '8.000000',
      returnedQuantity: '0.000000',
      returnableQuantity: '8.000000',
      availableStockQuantity: '8.000000',
      qualityStatus: 'QUALIFIED',
      qualityStatusName: '合格',
      quantityOnHand: '8.000000',
      availableQuantity: '8.000000',
      selectable: true,
      disabledReasonCode: null,
      disabledReason: null,
      maxSelectableQuantity: '8.000000',
      unitPrice: '80.00',
      returnableAmount: '640.00',
    },
  ],
}

const page = <T>(items: T[], pageSize = 10) => ({ items, page: 1, pageSize, total: items.length })

async function mountReversalView(component: Component, path: string, permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'purchase-return-user', displayName: '采购退货员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { render: () => null } },
      { path: '/procurement/returns', name: 'procurement-returns', component },
      { path: '/procurement/returns/create', name: 'procurement-return-create', component },
      { path: '/procurement/returns/:id', name: 'procurement-return-detail', component },
      { path: '/procurement/returns/:id/edit', name: 'procurement-return-edit', component },
      { path: '/procurement/receipts/:id', name: 'procurement-receipt-detail', component: { render: () => null } },
      { path: '/inventory/movements', name: 'inventory-movements', component: { render: () => null } },
      { path: '/finance/payables/:id', name: 'finance-payable-detail', component: { render: () => null } },
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

describe('采购退货前端页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    returnRefundReversalApiMock.purchaseReturns.list.mockResolvedValue(page([purchaseReturnDetail]))
    returnRefundReversalApiMock.purchaseReturns.get.mockResolvedValue(purchaseReturnDetail)
    returnRefundReversalApiMock.purchaseReturns.create.mockResolvedValue({ ...purchaseReturnDetail, status: 'DRAFT' })
    returnRefundReversalApiMock.purchaseReturns.update.mockResolvedValue({ ...purchaseReturnDetail, remark: '更新退货' })
    returnRefundReversalApiMock.purchaseReturns.post.mockResolvedValue({ ...purchaseReturnDetail, status: 'POSTED' })
    returnRefundReversalApiMock.purchaseReturns.cancel.mockResolvedValue({ ...purchaseReturnDetail, status: 'CANCELLED' })
    returnRefundReversalApiMock.purchaseReturnSources.list.mockResolvedValue(page([purchaseReturnSource], 20))
    returnRefundReversalApiMock.traces.list.mockResolvedValue(purchaseReturnDetail.traces)
    masterDataApiMock.materials.get.mockResolvedValue({
      id: 41,
      code: 'RM-001',
      name: '示例原料',
      status: 'ENABLED',
      materialType: 'RAW_MATERIAL',
      sourceType: 'PURCHASED',
      trackingMethod: 'BATCH',
      trackingMethodName: '批次管理',
      categoryId: 1,
      unitId: 1,
    })
    inventoryApiMock.batches.list.mockResolvedValue(page([
      {
        id: 910,
        batchNo: 'B-PR-001',
        materialId: 41,
        materialCode: 'RM-001',
        materialName: '示例原料',
        warehouseId: 3,
        warehouseName: '原料仓',
        qualityStatus: 'QUALIFIED',
        qualityStatusName: '合格',
        stockStatus: 'IN_STOCK',
        stockStatusName: '在库',
        quantityOnHand: '8.000000',
        availableQuantity: '8.000000',
        selectable: true,
        disabledReasonCode: null,
        disabledReason: null,
        updatedAt: '2026-07-05T11:00:00+08:00',
      },
    ], 20))
    inventoryApiMock.serials.list.mockResolvedValue(page([], 20))
  })

  it('采购退货列表支持筛选、创建入口、状态展示和权限按钮', async () => {
    const { wrapper, router } = await mountReversalView(PurchaseReturnListView, '/procurement/returns', [
      'procurement:return:view',
      'procurement:return:create',
      'procurement:return:update',
      'procurement:return:post',
      'procurement:return:cancel',
    ])

    expect(wrapper.text()).toContain('采购退货')
    expect(wrapper.text()).toContain('PR202607050001')
    expect(wrapper.text()).toContain('示例供应商')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.find('[data-test="create-purchase-return"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="edit-purchase-return"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-purchase-return"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-purchase-return"]').exists()).toBe(true)

    await wrapper.find('input[name="purchase-return-keyword"]').setValue('PR')
    await wrapper.find('input[name="purchase-return-supplier-id"]').setValue('9')
    await wrapper.find('[data-test="search-purchase-returns"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.purchaseReturns.list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: 'PR',
      supplierId: 9,
      page: 1,
    }))

    await wrapper.find('[data-test="create-purchase-return"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-return-create')
  })

  it('采购退货列表在只读权限下隐藏创建和操作按钮', async () => {
    const { wrapper } = await mountReversalView(PurchaseReturnListView, '/procurement/returns', ['procurement:return:view'])

    expect(wrapper.text()).toContain('PR202607050001')
    expect(wrapper.find('[data-test="create-purchase-return"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-purchase-return"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="post-purchase-return"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="cancel-purchase-return"]').exists()).toBe(false)
  })

  it('采购退货表单加载候选采购入库并以字符串数量提交创建请求', async () => {
    const { wrapper, router } = await mountReversalView(PurchaseReturnFormView, '/procurement/returns/create', ['procurement:return:create'])

    expect(wrapper.text()).toContain('新建采购退货')
    expect(wrapper.text()).toContain('RC202607050001')
    expect(wrapper.text()).toContain('可用库存')
    expect(returnRefundReversalApiMock.purchaseReturnSources.list).toHaveBeenCalledWith({
      keyword: '',
      page: 1,
      pageSize: 20,
    })
    await wrapper.find('input[name="purchase-return-business-date"]').setValue('2026-07-05')
    await wrapper.find('textarea[name="purchase-return-remark"]').setValue('来料退回')
    await wrapper.find('input[name="purchase-return-line-quantity-201"]').setValue('1.500000')
    await wrapper.find('input[name="purchase-return-line-reason-201"]').setValue('来料退回')
    await wrapper.find('[data-test="submit-purchase-return"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.purchaseReturns.create).toHaveBeenCalledWith(expect.objectContaining({
      sourceReceiptId: 20,
      businessDate: '2026-07-05',
      remark: '来料退回',
      lines: [{ sourceReceiptLineId: 201, qualityStatus: 'QUALIFIED', quantity: '1.500000', reason: '来料退回' }],
    }))
    expect(router.currentRoute.value.name).toBe('procurement-return-detail')
  })

  it('批次管理采购退货通过候选抽屉选择批次并随保存提交', async () => {
    const { wrapper } = await mountReversalView(PurchaseReturnFormView, '/procurement/returns/create', ['procurement:return:create'])

    await wrapper.find('input[name="purchase-return-line-quantity-201"]').setValue('1.500000')
    await wrapper.find('[data-test="open-purchase-return-tracking-0"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.batches.list).toHaveBeenCalledWith(expect.objectContaining({
      materialId: 41,
      warehouseId: 3,
      onlyAvailable: false,
    }))
    wrapper.findComponent(TrackingPickerDrawer).vm.$emit('select', {
      id: 910,
      trackingNo: 'B-PR-001',
      availableQuantity: '8.000000',
    })
    await flushPromises()
    expect(wrapper.text()).toContain('B-PR-001')

    await wrapper.find('input[name="purchase-return-line-reason-201"]').setValue('来料退回')
    await wrapper.find('[data-test="submit-purchase-return"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.purchaseReturns.create).toHaveBeenCalledWith(expect.objectContaining({
      lines: [
        {
          sourceReceiptLineId: 201,
          qualityStatus: 'QUALIFIED',
          quantity: '1.500000',
          trackingAllocations: [{ batchId: 910, quantity: '1.500000' }],
          reason: '来料退回',
        },
      ],
    }))
  })

  it('批次管理采购退货可拆分多个批次并提交多条追踪分配', async () => {
    const { wrapper } = await mountReversalView(PurchaseReturnFormView, '/procurement/returns/create', ['procurement:return:create'])

    await wrapper.find('input[name="purchase-return-line-quantity-201"]').setValue('1.500000')
    await wrapper.find('[data-test="open-purchase-return-tracking-0"]').trigger('click')
    await flushPromises()

    wrapper.findComponent(TrackingPickerDrawer).vm.$emit('confirm', [
      { batchId: 910, batchNo: 'B-PR-001', quantity: '1.000000' },
      { batchId: 911, batchNo: 'B-PR-002', quantity: '0.500000' },
    ])
    await flushPromises()

    await wrapper.find('input[name="purchase-return-line-reason-201"]').setValue('来料退回')
    await wrapper.find('[data-test="submit-purchase-return"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.purchaseReturns.create).toHaveBeenCalledWith(expect.objectContaining({
      lines: [
        {
          sourceReceiptLineId: 201,
          qualityStatus: 'QUALIFIED',
          quantity: '1.500000',
          trackingAllocations: [
            { batchId: 910, quantity: '1.000000' },
            { batchId: 911, quantity: '0.500000' },
          ],
          reason: '来料退回',
        },
      ],
    }))
  })

  it('采购退货候选行展示质量状态、现存、合格可用、最大可选和禁用原因', async () => {
    returnRefundReversalApiMock.purchaseReturnSources.list.mockResolvedValueOnce(page([
      {
        ...purchaseReturnSource,
        lines: purchaseReturnSource.lines.map((line) => ({
          ...line,
          qualityStatus: 'FROZEN',
          qualityStatusName: '冻结',
          availableQuantity: '0.000000',
          selectable: false,
          disabledReasonCode: 'FROZEN_NOT_AVAILABLE',
          disabledReason: '冻结库存需先解冻后退货',
          maxSelectableQuantity: '0.000000',
        })),
      },
    ], 20))
    const { wrapper } = await mountReversalView(PurchaseReturnFormView, '/procurement/returns/create', ['procurement:return:create'])

    expect(wrapper.text()).toContain('冻结')
    expect(wrapper.text()).toContain('现存数量')
    expect(wrapper.text()).toContain('合格可用')
    expect(wrapper.text()).toContain('最大可选')
    expect(wrapper.text()).toContain('禁用原因')
    expect(wrapper.text()).toContain('冻结库存需先解冻后退货')
    expect(wrapper.text()).not.toContain('canUse')

    const quantityInput = wrapper.find('input[name="purchase-return-line-quantity-201"]')
    expect(quantityInput.attributes('disabled')).toBeDefined()
    await quantityInput.setValue('1.000000')
    await wrapper.find('[data-test="submit-purchase-return"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('冻结库存需先解冻后退货')
    expect(returnRefundReversalApiMock.purchaseReturns.create).not.toHaveBeenCalled()
  })

  it('编辑采购退货来源受限时可保存草稿行且不提交来源主键', async () => {
    returnRefundReversalApiMock.purchaseReturns.get.mockResolvedValueOnce({
      ...purchaseReturnDetail,
      source: {
        sourceType: 'PURCHASE_RECEIPT',
        canViewSource: false,
        restricted: true,
        restrictedMessage: '来源无查看权限',
      },
      lines: [
        {
          ...purchaseReturnDetail.lines[0],
          sourceLineId: undefined,
          source: {
            sourceType: 'PURCHASE_RECEIPT_LINE',
            canViewSource: false,
            restricted: true,
            restrictedMessage: '来源无查看权限',
          },
        },
      ],
    })

    const { wrapper } = await mountReversalView(PurchaseReturnFormView, '/procurement/returns/2/edit', ['procurement:return:update'])

    expect(wrapper.text()).toContain('来源无查看权限')
    expect(wrapper.text()).toContain('编辑采购退货')
    expect(wrapper.find('[data-test="submit-purchase-return"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('RC202607050001')
    expect(wrapper.find('[data-test="view-source-document"]').exists()).toBe(false)

    await wrapper.find('input[name="purchase-return-line-quantity-21"]').setValue('2.000000')
    await wrapper.find('input[name="purchase-return-line-reason-21"]').setValue('受限来源更新')
    await wrapper.find('[data-test="submit-purchase-return"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.purchaseReturns.update).toHaveBeenCalledWith('2', expect.objectContaining({
      businessDate: '2026-07-05',
      remark: '来料退回',
      lines: [{ id: 21, quantity: '2.000000', reason: '受限来源更新' }],
    }))
    const payload = returnRefundReversalApiMock.purchaseReturns.update.mock.calls[0][1]
    expect(payload).not.toHaveProperty('sourceReceiptId')
    expect(payload.lines[0]).not.toHaveProperty('sourceReceiptLineId')
  })

  it.each([
    ['POSTED', '已过账'],
    ['CANCELLED', '已取消'],
  ])('直接进入%s采购退货编辑页时显示不可编辑提示并隐藏保存入口', async (status, statusText) => {
    returnRefundReversalApiMock.purchaseReturns.get.mockResolvedValueOnce({
      ...purchaseReturnDetail,
      status,
    })
    const { wrapper, router } = await mountReversalView(PurchaseReturnFormView, '/procurement/returns/2/edit', ['procurement:return:update'])

    expect(wrapper.text()).toContain(`当前采购退货${statusText}，不可编辑`)
    expect(wrapper.find('[data-test="submit-purchase-return"]').exists()).toBe(false)
    expect(wrapper.find('input[name="purchase-return-business-date"]').exists()).toBe(false)
    expect(returnRefundReversalApiMock.purchaseReturns.update).not.toHaveBeenCalled()

    await wrapper.find('[data-test="return-purchase-return-detail"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-return-detail')
  })

  it('采购退货详情展示来源、明细、追溯，过账和取消按钮按权限显示', async () => {
    const { wrapper, router } = await mountReversalView(PurchaseReturnDetailView, '/procurement/returns/2', [
      'procurement:return:view',
      'procurement:return:update',
      'procurement:return:post',
      'procurement:return:cancel',
      'business:reversal:view',
    ])

    expect(wrapper.text()).toContain('采购退货详情')
    expect(wrapper.text()).toContain('PR202607050001')
    expect(wrapper.text()).toContain('RC202607050001')
    expect(wrapper.text()).toContain('示例原料')
    expect(wrapper.find('[data-test="edit-purchase-return-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-purchase-return-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-purchase-return-detail"]').exists()).toBe(true)

    await wrapper.find('[data-test="open-reversal-trace"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.traces.list).toHaveBeenCalledWith({
      sourceType: 'PURCHASE_RETURN',
      sourceId: 2,
      direction: 'REVERSE_TO_SOURCE',
      includeRestricted: true,
    })
    expect(wrapper.text()).toContain('反向追溯')

    await wrapper.find('[data-test="view-reversal-source"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-receipt-detail')
    expect(router.currentRoute.value.params.id).toBe('20')
    expect(router.currentRoute.value.query.lineId).toBe('201')

    await wrapper.find('[data-test="post-purchase-return-detail"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.purchaseReturns.post).toHaveBeenCalledWith(2)
  })

  it('采购退货详情明确展示库存出库影响和应付冲减影响', async () => {
    returnRefundReversalApiMock.purchaseReturns.get.mockResolvedValueOnce(impactedPurchaseReturnDetail)
    const { wrapper } = await mountReversalView(PurchaseReturnDetailView, '/procurement/returns/2', ['procurement:return:view', 'business:reversal:view'])

    expect(wrapper.text()).toContain('库存出库影响')
    expect(wrapper.text()).toContain('库存流水')
    expect(wrapper.text()).toContain('库存流水 #702')
    expect(wrapper.text()).toContain('应付冲减影响')
    expect(wrapper.text()).toContain('应付冲减')
    expect(wrapper.text()).toContain('应付冲减 #802')
  })

  it('采购退货追溯面板能区分采购入库来源、采购退货、库存流水和应付冲减', async () => {
    returnRefundReversalApiMock.purchaseReturns.get.mockResolvedValueOnce(impactedPurchaseReturnDetail)
    returnRefundReversalApiMock.traces.list.mockResolvedValueOnce([
      purchaseReturnDetail.traces[0],
      inventoryMovementTrace,
      payableAdjustmentTrace,
    ])
    const { wrapper, router } = await mountReversalView(PurchaseReturnDetailView, '/procurement/returns/2', ['procurement:return:view', 'business:reversal:view'])

    await wrapper.find('[data-test="open-reversal-trace"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('采购入库行')
    expect(wrapper.text()).toContain('采购退货')
    expect(wrapper.text()).toContain('库存出库影响')
    expect(wrapper.text()).toContain('应付冲减')

    const impactButtons = wrapper.findAll('[data-test="view-reversal-impact-resource"]')
    expect(impactButtons).toHaveLength(2)
    await impactButtons[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('inventory-movements')
    expect(router.currentRoute.value.query.sourceId).toBe('702')

    await impactButtons[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('finance-payable-detail')
    expect(router.currentRoute.value.params.id).toBe('802')
  })

  it('采购退货追溯来源受限时不展示跳转和敏感字段', async () => {
    returnRefundReversalApiMock.purchaseReturns.get.mockResolvedValueOnce(impactedPurchaseReturnDetail)
    returnRefundReversalApiMock.traces.list.mockResolvedValueOnce([restrictedInventoryTrace])
    const { wrapper } = await mountReversalView(PurchaseReturnDetailView, '/procurement/returns/2', ['procurement:return:view', 'business:reversal:view'])

    await wrapper.find('[data-test="open-reversal-trace"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('来源无查看权限')
    expect(wrapper.text()).not.toContain('RC-SECRET')
    expect(wrapper.text()).not.toContain('999.00')
    expect(wrapper.find('[data-test="view-reversal-source"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="view-reversal-impact-resource"]').exists()).toBe(false)
  })

  it('来源受限时只展示受限提示，不展示采购入库敏感字段或跳转入口', async () => {
    returnRefundReversalApiMock.purchaseReturns.get.mockResolvedValueOnce({
      ...purchaseReturnDetail,
      source: {
        sourceType: 'PURCHASE_RECEIPT',
        canViewSource: false,
        restricted: true,
        restrictedMessage: '来源无查看权限',
      },
      lines: [
        {
          ...purchaseReturnDetail.lines[0],
          source: {
            sourceType: 'PURCHASE_RECEIPT_LINE',
            canViewSource: false,
            restricted: true,
            restrictedMessage: '来源无查看权限',
          },
        },
      ],
      traces: [],
    })
    const { wrapper } = await mountReversalView(PurchaseReturnDetailView, '/procurement/returns/2', ['procurement:return:view', 'business:reversal:view'])

    expect(wrapper.text()).toContain('来源无查看权限')
    expect(wrapper.text()).not.toContain('RC202607050001')
    expect(wrapper.text()).not.toContain('640.00')
    expect(wrapper.find('[data-test="view-source-document"]').exists()).toBe(false)
  })
})
