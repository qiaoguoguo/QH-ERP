import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductionMaterialReturnDetailView from './ProductionMaterialReturnDetailView.vue'
import ProductionMaterialReturnFormView from './ProductionMaterialReturnFormView.vue'
import ProductionMaterialReturnListView from './ProductionMaterialReturnListView.vue'
import ProductionMaterialSupplementDetailView from './ProductionMaterialSupplementDetailView.vue'
import ProductionMaterialSupplementFormView from './ProductionMaterialSupplementFormView.vue'
import ProductionMaterialSupplementListView from './ProductionMaterialSupplementListView.vue'
import ReversalTracePanel from './ReversalTracePanel.vue'
import { useAuthStore } from '../../stores/authStore'

const returnRefundReversalApiMock = vi.hoisted(() => ({
  productionMaterialReturns: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    post: vi.fn(),
    cancel: vi.fn(),
  },
  productionMaterialReturnSources: {
    list: vi.fn(),
  },
  productionMaterialSupplements: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    post: vi.fn(),
    cancel: vi.fn(),
  },
  productionMaterialSupplementSources: {
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

const materialIssueSource = {
  sourceType: 'PRODUCTION_MATERIAL_ISSUE',
  sourceId: 40,
  sourceNo: 'MI202607050001',
  businessDate: '2026-07-05',
  status: 'POSTED',
  quantity: '10.000000',
  amount: '200.00',
  canViewSource: true,
  restricted: false,
  resourceRouteName: 'production-work-order-material-issues',
  resourceRouteParams: { id: 30 },
  resourceRouteQuery: { issueId: 40 },
}

const materialIssueLineSource = {
  ...materialIssueSource,
  sourceType: 'PRODUCTION_MATERIAL_ISSUE_LINE',
  sourceLineId: 401,
  lineNo: 10,
  resourceRouteQuery: { issueId: 40, lineId: 401 },
}

const workOrderSource = {
  sourceType: 'PRODUCTION_WORK_ORDER',
  sourceId: 30,
  sourceNo: 'WO202607050001',
  businessDate: '2026-07-05',
  status: 'IN_PROGRESS',
  canViewSource: true,
  restricted: false,
  resourceRouteName: 'production-work-order-detail',
  resourceRouteParams: { id: 30 },
}

const workOrderMaterialSource = {
  ...workOrderSource,
  sourceType: 'PRODUCTION_WORK_ORDER_MATERIAL',
  sourceLineId: 501,
  lineNo: 20,
  quantity: '12.000000',
  amount: '360.00',
}

const materialReturnSourceView = {
  sourceType: 'PRODUCTION_MATERIAL_RETURN',
  sourceId: 3,
  sourceNo: 'MR202607050001',
  canViewSource: true,
  restricted: false,
  resourceRouteName: 'production-material-return-detail',
  resourceRouteParams: { id: 3 },
}

const materialSupplementSourceView = {
  sourceType: 'PRODUCTION_MATERIAL_SUPPLEMENT',
  sourceId: 4,
  sourceNo: 'MS202607050001',
  canViewSource: true,
  restricted: false,
  resourceRouteName: 'production-material-supplement-detail',
  resourceRouteParams: { id: 4 },
}

const returnInventoryTrace = {
  traceKey: 'PRODUCTION_MATERIAL_ISSUE_LINE:40:401:PRODUCTION_MATERIAL_RETURN:3:0:INVENTORY_MOVEMENT:901',
  direction: 'SOURCE_TO_REVERSE',
  source: materialIssueLineSource,
  reverse: materialReturnSourceView,
  inventoryMovementId: 901,
  businessDate: '2026-07-05',
  quantity: '3.000000',
  amount: '60.00',
  status: 'POSTED',
  canViewResource: true,
  restricted: false,
  resourceRouteName: 'inventory-movements',
  resourceRouteQuery: { sourceType: 'PRODUCTION_MATERIAL_RETURN', sourceId: 3 },
}

const returnCostTrace = {
  traceKey: 'PRODUCTION_MATERIAL_RETURN:3:COST_RECORD:1001',
  direction: 'SOURCE_TO_REVERSE',
  source: materialIssueLineSource,
  reverse: materialReturnSourceView,
  costRecordId: 1001,
  businessDate: '2026-07-05',
  quantity: '3.000000',
  amount: '60.00',
  status: 'ACTIVE',
  canViewResource: true,
  restricted: false,
  resourceRouteName: 'cost-record-detail',
  resourceRouteParams: { id: 1001 },
}

const returnInventoryAndCostTrace = {
  ...returnInventoryTrace,
  traceKey: 'PRODUCTION_MATERIAL_RETURN:3:INVENTORY_MOVEMENT:901:COST_RECORD:1001',
  direction: 'SOURCE_TO_REVERSE' as const,
  settlementAdjustmentId: 777,
  costRecordId: 1001,
  resourceRouteName: 'inventory-movements',
  resourceRouteQuery: { sourceType: 'PRODUCTION_MATERIAL_RETURN', sourceId: 3 },
}

const supplementInventoryTrace = {
  traceKey: 'PRODUCTION_WORK_ORDER_MATERIAL:30:501:PRODUCTION_MATERIAL_SUPPLEMENT:4:0:INVENTORY_MOVEMENT:902',
  direction: 'SOURCE_TO_REVERSE',
  source: workOrderMaterialSource,
  reverse: materialSupplementSourceView,
  inventoryMovementId: 902,
  businessDate: '2026-07-05',
  quantity: '2.000000',
  amount: '60.00',
  status: 'POSTED',
  canViewResource: true,
  restricted: false,
  resourceRouteName: 'inventory-movements',
  resourceRouteQuery: { sourceType: 'PRODUCTION_MATERIAL_SUPPLEMENT', sourceId: 4 },
}

const supplementCostTrace = {
  traceKey: 'PRODUCTION_MATERIAL_SUPPLEMENT:4:COST_RECORD:1002',
  direction: 'SOURCE_TO_REVERSE',
  source: workOrderMaterialSource,
  reverse: materialSupplementSourceView,
  costRecordId: 1002,
  businessDate: '2026-07-05',
  quantity: '2.000000',
  amount: '60.00',
  status: 'ACTIVE',
  canViewResource: true,
  restricted: false,
  resourceRouteName: 'cost-record-detail',
  resourceRouteParams: { id: 1002 },
}

const supplementInventoryAndCostTrace = {
  ...supplementInventoryTrace,
  traceKey: 'PRODUCTION_MATERIAL_SUPPLEMENT:4:INVENTORY_MOVEMENT:902:COST_RECORD:1002',
  direction: 'SOURCE_TO_REVERSE' as const,
  costRecordId: 1002,
  resourceRouteName: 'inventory-movements',
  resourceRouteQuery: { sourceType: 'PRODUCTION_MATERIAL_SUPPLEMENT', sourceId: 4 },
}

const restrictedTrace = {
  traceKey: 'opaque-trace-key',
  direction: 'SOURCE_TO_REVERSE',
  source: {
    sourceType: 'PRODUCTION_MATERIAL_ISSUE_LINE',
    canViewSource: false,
    restricted: true,
    restrictedMessage: '来源无查看权限',
  },
  reverse: materialReturnSourceView,
  inventoryMovementId: 999,
  canViewResource: false,
  restricted: true,
  restrictedMessage: '来源无查看权限',
}

const materialReturnDetail = {
  id: 3,
  returnNo: 'MR202607050001',
  workOrderId: 30,
  workOrderNo: 'WO202607050001',
  warehouseId: 4,
  warehouseName: '线边仓',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  totalQuantity: '3.000000',
  source: materialIssueSource,
  createdAt: '2026-07-05T12:00:00+08:00',
  updatedAt: '2026-07-05T12:00:00+08:00',
  clientRequestId: 'material-return-client-1',
  remark: '余料退回',
  lines: [
    {
      id: 31,
      lineNo: 10,
      sourceLineId: 401,
      materialId: 51,
      materialCode: 'RM-RET-001',
      materialName: '退料原料',
      unitId: 1,
      unitName: 'kg',
      returnedQuantityBefore: '0.000000',
      returnableQuantityBefore: '10.000000',
      quantity: '3.000000',
      unitPrice: '20.00',
      amount: '60.00',
      reason: '余料退回',
      source: materialIssueLineSource,
    },
  ],
  traces: [returnInventoryTrace, returnCostTrace],
}

const materialSupplementDetail = {
  id: 4,
  supplementNo: 'MS202607050001',
  workOrderId: 30,
  workOrderNo: 'WO202607050001',
  warehouseId: 4,
  warehouseName: '线边仓',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  totalQuantity: '2.000000',
  source: workOrderSource,
  createdAt: '2026-07-05T13:00:00+08:00',
  updatedAt: '2026-07-05T13:00:00+08:00',
  clientRequestId: 'material-supplement-client-1',
  remark: '损耗补料',
  lines: [
    {
      id: 41,
      lineNo: 20,
      sourceLineId: 501,
      materialId: 52,
      materialCode: 'RM-SUP-001',
      materialName: '补料原料',
      unitId: 1,
      unitName: 'kg',
      quantity: '2.000000',
      unitPrice: '30.00',
      amount: '60.00',
      reason: '损耗补料',
      source: workOrderMaterialSource,
    },
  ],
  traces: [supplementInventoryTrace, supplementCostTrace],
}

const materialReturnSource = {
  issueId: 40,
  issueNo: 'MI202607050001',
  workOrderId: 30,
  workOrderNo: 'WO202607050001',
  warehouseId: 4,
  warehouseName: '线边仓',
  businessDate: '2026-07-05',
  status: 'POSTED',
  lines: [
    {
      issueLineId: 401,
      workOrderMaterialId: 501,
      lineNo: 10,
      materialId: 51,
      materialCode: 'RM-RET-001',
      materialName: '退料原料',
      unitId: 1,
      unitName: 'kg',
      issuedQuantity: '10.000000',
      returnedQuantity: '0.000000',
      returnableQuantity: '10.000000',
      unitPrice: '20.00',
      returnableAmount: '200.00',
    },
  ],
}

const materialSupplementSource = {
  workOrderId: 30,
  workOrderNo: 'WO202607050001',
  workOrderStatus: 'IN_PROGRESS',
  warehouseId: 4,
  warehouseName: '线边仓',
  materials: [
    {
      workOrderMaterialId: 501,
      lineNo: 20,
      materialId: 52,
      materialCode: 'RM-SUP-001',
      materialName: '补料原料',
      unitId: 1,
      unitName: 'kg',
      plannedQuantity: '12.000000',
      issuedQuantity: '8.000000',
      supplementedQuantity: '1.000000',
      availableStockQuantity: '9.000000',
      unitPrice: '30.00',
    },
  ],
}

const page = <T>(items: T[]) => ({ items, page: 1, pageSize: 20, total: items.length })

async function mountReversalView(component: Component, path: string, permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'production-reversal-user', displayName: '生产反向用户', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { render: () => null } },
      { path: '/production/material-returns', name: 'production-material-returns', component },
      { path: '/production/material-returns/create', name: 'production-material-return-create', component },
      { path: '/production/material-returns/:id', name: 'production-material-return-detail', component },
      { path: '/production/material-returns/:id/edit', name: 'production-material-return-edit', component },
      { path: '/production/material-supplements', name: 'production-material-supplements', component },
      { path: '/production/material-supplements/create', name: 'production-material-supplement-create', component },
      { path: '/production/material-supplements/:id', name: 'production-material-supplement-detail', component },
      { path: '/production/material-supplements/:id/edit', name: 'production-material-supplement-edit', component },
      { path: '/production/work-orders/:id/material-issues', name: 'production-work-order-material-issues', component: { render: () => null } },
      { path: '/production/work-orders/:id', name: 'production-work-order-detail', component: { render: () => null } },
      { path: '/inventory/movements', name: 'inventory-movements', component: { render: () => null } },
      { path: '/cost/records/:id', name: 'cost-record-detail', component: { render: () => null } },
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

describe('生产退料补料前端页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('confirm', vi.fn(() => true))
    returnRefundReversalApiMock.productionMaterialReturns.list.mockResolvedValue(page([materialReturnDetail]))
    returnRefundReversalApiMock.productionMaterialReturns.get.mockResolvedValue(materialReturnDetail)
    returnRefundReversalApiMock.productionMaterialReturns.create.mockResolvedValue({ ...materialReturnDetail, status: 'DRAFT' })
    returnRefundReversalApiMock.productionMaterialReturns.update.mockResolvedValue({ ...materialReturnDetail, remark: '更新退料' })
    returnRefundReversalApiMock.productionMaterialReturns.post.mockResolvedValue({ ...materialReturnDetail, status: 'POSTED' })
    returnRefundReversalApiMock.productionMaterialReturns.cancel.mockResolvedValue({ ...materialReturnDetail, status: 'CANCELLED' })
    returnRefundReversalApiMock.productionMaterialReturnSources.list.mockResolvedValue(page([materialReturnSource]))
    returnRefundReversalApiMock.productionMaterialSupplements.list.mockResolvedValue(page([materialSupplementDetail]))
    returnRefundReversalApiMock.productionMaterialSupplements.get.mockResolvedValue(materialSupplementDetail)
    returnRefundReversalApiMock.productionMaterialSupplements.create.mockResolvedValue({ ...materialSupplementDetail, status: 'DRAFT' })
    returnRefundReversalApiMock.productionMaterialSupplements.update.mockResolvedValue({ ...materialSupplementDetail, remark: '更新补料' })
    returnRefundReversalApiMock.productionMaterialSupplements.post.mockResolvedValue({ ...materialSupplementDetail, status: 'POSTED' })
    returnRefundReversalApiMock.productionMaterialSupplements.cancel.mockResolvedValue({ ...materialSupplementDetail, status: 'CANCELLED' })
    returnRefundReversalApiMock.productionMaterialSupplementSources.list.mockResolvedValue(page([materialSupplementSource]))
    returnRefundReversalApiMock.traces.list.mockResolvedValue(materialReturnDetail.traces)
  })

  it('生产退料列表支持筛选、创建入口和权限按钮', async () => {
    const { wrapper, router } = await mountReversalView(ProductionMaterialReturnListView, '/production/material-returns', [
      'production:material-return:view',
      'production:material-return:create',
      'production:material-return:update',
      'production:material-return:post',
      'production:material-return:cancel',
    ])

    expect(wrapper.text()).toContain('生产退料')
    expect(wrapper.text()).toContain('MR202607050001')
    expect(wrapper.text()).toContain('WO202607050001')
    expect(wrapper.find('[data-test="create-material-return"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="edit-material-return"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-material-return"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-material-return"]').exists()).toBe(true)

    await wrapper.find('input[name="material-return-keyword"]').setValue('MR')
    await wrapper.find('input[name="material-return-work-order-id"]').setValue('30')
    await wrapper.find('[data-test="search-material-returns"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.productionMaterialReturns.list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: 'MR',
      workOrderId: 30,
      page: 1,
    }))

    await wrapper.find('[data-test="create-material-return"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('production-material-return-create')
  })

  it('生产补料列表支持筛选、创建入口和权限按钮', async () => {
    const { wrapper, router } = await mountReversalView(ProductionMaterialSupplementListView, '/production/material-supplements', [
      'production:material-supplement:view',
      'production:material-supplement:create',
      'production:material-supplement:update',
      'production:material-supplement:post',
      'production:material-supplement:cancel',
    ])

    expect(wrapper.text()).toContain('生产补料')
    expect(wrapper.text()).toContain('MS202607050001')
    expect(wrapper.text()).toContain('WO202607050001')
    expect(wrapper.find('[data-test="create-material-supplement"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="edit-material-supplement"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-material-supplement"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-material-supplement"]').exists()).toBe(true)

    await wrapper.find('input[name="material-supplement-keyword"]').setValue('MS')
    await wrapper.find('input[name="material-supplement-work-order-id"]').setValue('30')
    await wrapper.find('[data-test="search-material-supplements"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.productionMaterialSupplements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: 'MS',
      workOrderId: 30,
      page: 1,
    }))

    await wrapper.find('[data-test="create-material-supplement"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('production-material-supplement-create')
  })

  it('生产退料表单加载候选生产领料并以字符串数量提交创建请求', async () => {
    const { wrapper, router } = await mountReversalView(ProductionMaterialReturnFormView, '/production/material-returns/create', ['production:material-return:create'])

    expect(wrapper.text()).toContain('新建生产退料')
    expect(wrapper.text()).toContain('MI202607050001')
    expect(wrapper.text()).toContain('已领数量')
    expect(wrapper.text()).toContain('可退数量')
    await wrapper.find('input[name="material-return-business-date"]').setValue('2026-07-05')
    await wrapper.find('textarea[name="material-return-remark"]').setValue('余料退回')
    await wrapper.find('input[name="material-return-line-quantity-401"]').setValue('3.000000')
    await wrapper.find('input[name="material-return-line-reason-401"]').setValue('余料退回')
    await wrapper.find('[data-test="submit-material-return"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.productionMaterialReturns.create).toHaveBeenCalledWith(expect.objectContaining({
      sourceIssueId: 40,
      businessDate: '2026-07-05',
      remark: '余料退回',
      lines: [{ sourceIssueLineId: 401, quantity: '3.000000', reason: '余料退回' }],
    }))
    expect(router.currentRoute.value.name).toBe('production-material-return-detail')
  })

  it('生产补料表单加载工单物料并以字符串数量提交创建请求', async () => {
    const { wrapper, router } = await mountReversalView(ProductionMaterialSupplementFormView, '/production/material-supplements/create', ['production:material-supplement:create'])

    expect(wrapper.text()).toContain('新建生产补料')
    expect(wrapper.text()).toContain('WO202607050001')
    expect(wrapper.text()).toContain('已领数量')
    expect(wrapper.text()).toContain('可用库存')
    expect(wrapper.text()).not.toContain('已退数量')
    expect(wrapper.text()).not.toContain('可退数量')
    await wrapper.find('input[name="material-supplement-business-date"]').setValue('2026-07-05')
    await wrapper.find('textarea[name="material-supplement-remark"]').setValue('损耗补料')
    await wrapper.find('input[name="material-supplement-line-quantity-501"]').setValue('2.000000')
    await wrapper.find('input[name="material-supplement-line-reason-501"]').setValue('损耗补料')
    await wrapper.find('[data-test="submit-material-supplement"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.productionMaterialSupplements.create).toHaveBeenCalledWith(expect.objectContaining({
      workOrderId: 30,
      warehouseId: 4,
      businessDate: '2026-07-05',
      remark: '损耗补料',
      lines: [{ workOrderMaterialId: 501, quantity: '2.000000', reason: '损耗补料' }],
    }))
    expect(router.currentRoute.value.name).toBe('production-material-supplement-detail')
  })

  it.each([
    [ProductionMaterialReturnFormView, '/production/material-returns/3/edit', 'production:material-return:update', returnRefundReversalApiMock.productionMaterialReturns.get, { ...materialReturnDetail, status: 'POSTED' }, '当前生产退料已过账，不可编辑', 'submit-material-return'],
    [ProductionMaterialSupplementFormView, '/production/material-supplements/4/edit', 'production:material-supplement:update', returnRefundReversalApiMock.productionMaterialSupplements.get, { ...materialSupplementDetail, status: 'CANCELLED' }, '当前生产补料已取消，不可编辑', 'submit-material-supplement'],
  ])('已过账或已取消的生产反向单据不可编辑', async (component, path, permission, getter, detail, message, submitTestId) => {
    getter.mockResolvedValueOnce(detail)
    const { wrapper } = await mountReversalView(component as Component, path as string, [permission as string])

    expect(wrapper.text()).toContain(message)
    expect(wrapper.find(`[data-test="${submitTestId}"]`).exists()).toBe(false)
  })

  it('生产退料来源受限编辑可保存草稿行且不提交来源主键', async () => {
    returnRefundReversalApiMock.productionMaterialReturns.get.mockResolvedValueOnce({
      ...materialReturnDetail,
      source: {
        sourceType: 'PRODUCTION_MATERIAL_ISSUE',
        canViewSource: false,
        restricted: true,
        restrictedMessage: '来源无查看权限',
      },
      lines: [
        {
          ...materialReturnDetail.lines[0],
          sourceLineId: undefined,
          source: {
            sourceType: 'PRODUCTION_MATERIAL_ISSUE_LINE',
            canViewSource: false,
            restricted: true,
            restrictedMessage: '来源无查看权限',
          },
        },
      ],
    })
    const { wrapper } = await mountReversalView(ProductionMaterialReturnFormView, '/production/material-returns/3/edit', ['production:material-return:update'])

    expect(wrapper.text()).toContain('来源无查看权限')
    expect(wrapper.text()).not.toContain('MI202607050001')
    await wrapper.find('input[name="material-return-line-quantity-31"]').setValue('4.000000')
    await wrapper.find('input[name="material-return-line-reason-31"]').setValue('受限退料调整')
    await wrapper.find('[data-test="submit-material-return"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.productionMaterialReturns.update).toHaveBeenCalledWith('3', expect.objectContaining({
      businessDate: '2026-07-05',
      remark: '余料退回',
      lines: [{ id: 31, quantity: '4.000000', reason: '受限退料调整' }],
    }))
    const payload = returnRefundReversalApiMock.productionMaterialReturns.update.mock.calls[0][1]
    expect(payload).not.toHaveProperty('sourceIssueId')
    expect(payload.lines[0]).not.toHaveProperty('sourceIssueLineId')
  })

  it('生产退料编辑页不把通用来源数量展示成已领数量', async () => {
    returnRefundReversalApiMock.productionMaterialReturns.get.mockResolvedValueOnce({
      ...materialReturnDetail,
      lines: [
        {
          ...materialReturnDetail.lines[0],
          quantity: '3.123456',
          returnedQuantityBefore: '8.888888',
          returnableQuantityBefore: '9.999999',
          source: {
            ...materialIssueLineSource,
            quantity: '7.777777',
            amount: '155.55',
          },
        },
      ],
    })
    const { wrapper } = await mountReversalView(ProductionMaterialReturnFormView, '/production/material-returns/3/edit', ['production:material-return:update'])

    expect(wrapper.text()).toContain('编辑生产退料')
    expect(wrapper.text()).toContain('已领数量')
    expect(wrapper.text()).not.toContain('7.777777')
    const quantityInput = wrapper.find('input[name="material-return-line-quantity-401"]')
    expect((quantityInput.element as HTMLInputElement).value).toBe('3.123456')
  })

  it('生产补料来源受限编辑可保存草稿行且不提交来源主键', async () => {
    returnRefundReversalApiMock.productionMaterialSupplements.get.mockResolvedValueOnce({
      ...materialSupplementDetail,
      source: {
        sourceType: 'PRODUCTION_WORK_ORDER',
        canViewSource: false,
        restricted: true,
        restrictedMessage: '来源无查看权限',
      },
      lines: [
        {
          ...materialSupplementDetail.lines[0],
          sourceLineId: undefined,
          source: {
            sourceType: 'PRODUCTION_WORK_ORDER_MATERIAL',
            canViewSource: false,
            restricted: true,
            restrictedMessage: '来源无查看权限',
          },
        },
      ],
    })
    const { wrapper } = await mountReversalView(ProductionMaterialSupplementFormView, '/production/material-supplements/4/edit', ['production:material-supplement:update'])

    expect(wrapper.text()).toContain('来源无查看权限')
    expect(wrapper.text()).not.toContain('WO202607050001')
    await wrapper.find('input[name="material-supplement-line-quantity-41"]').setValue('2.500000')
    await wrapper.find('input[name="material-supplement-line-reason-41"]').setValue('受限补料调整')
    await wrapper.find('[data-test="submit-material-supplement"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.productionMaterialSupplements.update).toHaveBeenCalledWith('4', expect.objectContaining({
      businessDate: '2026-07-05',
      remark: '损耗补料',
      lines: [{ id: 41, quantity: '2.500000', reason: '受限补料调整' }],
    }))
    const payload = returnRefundReversalApiMock.productionMaterialSupplements.update.mock.calls[0][1]
    expect(payload).not.toHaveProperty('workOrderId')
    expect(payload).not.toHaveProperty('warehouseId')
    expect(payload.lines[0]).not.toHaveProperty('workOrderMaterialId')
  })

  it('追溯面板同一条记录同时展示库存、往来和成本影响资源', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'home', component: { render: () => null } },
        { path: '/inventory/movements', name: 'inventory-movements', component: { render: () => null } },
        { path: '/cost/records/:id', name: 'cost-record-detail', component: { render: () => null } },
      ],
    })
    await router.push('/')
    await router.isReady()

    const wrapper = mount(ReversalTracePanel, {
      props: {
        visible: true,
        rows: [returnInventoryAndCostTrace],
      },
      global: {
        plugins: [router, ElementPlus],
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('库存流水 #901')
    expect(wrapper.text()).toContain('往来冲减 #777')
    expect(wrapper.text()).toContain('成本记录 #1001')

    const impactButtons = wrapper.findAll('[data-test="view-reversal-impact-resource"]')
    expect(impactButtons).toHaveLength(2)
    await impactButtons[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('inventory-movements')
    expect(router.currentRoute.value.query.sourceId).toBe('3')
    await impactButtons[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('cost-record-detail')
    expect(router.currentRoute.value.params.id).toBe('1001')
  })

  it('生产补料编辑页不把通用反向字段展示成计划、已领、已补或可用库存', async () => {
    returnRefundReversalApiMock.productionMaterialSupplements.get.mockResolvedValueOnce({
      ...materialSupplementDetail,
      lines: [
        {
          ...materialSupplementDetail.lines[0],
          quantity: '2.123456',
          returnedQuantityBefore: '22.222222',
          returnableQuantityBefore: '33.333333',
          source: {
            ...workOrderMaterialSource,
            quantity: '11.111111',
            amount: '444.44',
          },
        },
      ],
    })
    const { wrapper } = await mountReversalView(ProductionMaterialSupplementFormView, '/production/material-supplements/4/edit', ['production:material-supplement:update'])

    expect(wrapper.text()).toContain('编辑生产补料')
    expect(wrapper.text()).toContain('计划用量')
    expect(wrapper.text()).toContain('已领数量')
    expect(wrapper.text()).toContain('已补数量')
    expect(wrapper.text()).toContain('可用库存')
    expect(wrapper.text()).not.toContain('11.111111')
    expect(wrapper.text()).not.toContain('22.222222')
    expect(wrapper.text()).not.toContain('33.333333')
    const quantityInput = wrapper.find('input[name="material-supplement-line-quantity-501"]')
    expect((quantityInput.element as HTMLInputElement).value).toBe('2.123456')
  })

  it('生产退料详情展示库存入库、成本影响、追溯和权限操作', async () => {
    returnRefundReversalApiMock.traces.list.mockResolvedValueOnce([returnInventoryAndCostTrace, restrictedTrace])
    const { wrapper, router } = await mountReversalView(ProductionMaterialReturnDetailView, '/production/material-returns/3', [
      'production:material-return:view',
      'production:material-return:update',
      'production:material-return:post',
      'production:material-return:cancel',
      'business:reversal:view',
    ])

    expect(wrapper.text()).toContain('生产退料详情')
    expect(wrapper.text()).toContain('MR202607050001')
    expect(wrapper.text()).toContain('库存入库影响')
    expect(wrapper.text()).toContain('成本影响')
    expect(wrapper.text()).toContain('成本记录 #1001')
    expect(wrapper.find('[data-test="edit-material-return-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-material-return-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-material-return-detail"]').exists()).toBe(true)

    await wrapper.find('[data-test="open-reversal-trace"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.traces.list).toHaveBeenCalledWith({
      sourceType: 'PRODUCTION_MATERIAL_RETURN',
      sourceId: 3,
      direction: 'REVERSE_TO_SOURCE',
      includeRestricted: true,
    })
    expect(wrapper.text()).toContain('生产退料')
    expect(wrapper.text()).toContain('库存流水 #901')
    expect(wrapper.text()).toContain('成本记录 #1001')
    expect(wrapper.text()).toContain('来源无查看权限')

    const impactButtons = wrapper.findAll('[data-test="view-reversal-impact-resource"]')
    expect(impactButtons.length).toBeGreaterThanOrEqual(1)
    await impactButtons[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('inventory-movements')
    expect(router.currentRoute.value.query.sourceId).toBe('3')
  })

  it('生产补料详情展示库存出库、成本影响且不沿用退料语义', async () => {
    returnRefundReversalApiMock.productionMaterialSupplements.get.mockResolvedValueOnce({ ...materialSupplementDetail, status: 'POSTED' })
    returnRefundReversalApiMock.traces.list.mockResolvedValueOnce([supplementInventoryAndCostTrace])
    const { wrapper } = await mountReversalView(ProductionMaterialSupplementDetailView, '/production/material-supplements/4', [
      'production:material-supplement:view',
      'business:reversal:view',
    ])

    expect(wrapper.text()).toContain('生产补料详情')
    expect(wrapper.text()).toContain('MS202607050001')
    expect(wrapper.text()).toContain('库存出库影响')
    expect(wrapper.text()).toContain('成本影响')
    expect(wrapper.text()).toContain('补料数量')
    expect(wrapper.text()).not.toContain('已退数量')
    expect(wrapper.text()).not.toContain('可退数量')

    await wrapper.find('[data-test="open-reversal-trace"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.traces.list).toHaveBeenCalledWith({
      sourceType: 'PRODUCTION_MATERIAL_SUPPLEMENT',
      sourceId: 4,
      direction: 'REVERSE_TO_SOURCE',
      includeRestricted: true,
    })
    expect(wrapper.text()).toContain('生产补料')
    expect(wrapper.text()).toContain('库存流水 #902')
    expect(wrapper.text()).toContain('成本记录 #1002')
  })
})
