import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PurchaseOrderDetailRecord } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import PurchaseOrderDetailView from './PurchaseOrderDetailView.vue'

const procurementApiMock = vi.hoisted(() => ({
  orders: {
    get: vi.fn(),
    confirm: vi.fn(),
    submitException: vi.fn(),
    cancel: vi.fn(),
    close: vi.fn(),
  },
}))

const documentPlatformApiMock = vi.hoisted(() => ({
  documentTasks: {
    get: vi.fn(),
    errors: vi.fn(),
    download: vi.fn(),
    cancel: vi.fn(),
  },
  imports: {
    confirm: vi.fn(),
  },
  printTasks: {
    create: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
}))

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
  createIdempotencyKey: () => 'order-print-key',
}))

const draftOrder: PurchaseOrderDetailRecord = {
  id: 99,
  orderNo: 'PO-20260704-001',
  supplierId: 100,
  supplierCode: 'SUP-A',
  supplierName: '华东五金',
  orderDate: '2026-07-04',
  expectedArrivalDate: '2026-07-12',
  status: 'DRAFT',
  lineCount: 1,
  totalQuantity: '12.500000',
  receivedQuantity: '0.000000',
  remainingQuantity: '12.500000',
  inTransitQuantity: '0.000000',
  inTransitStatus: 'NOT_COUNTED',
  inTransitStatusName: '不计入在途',
  currency: 'CNY',
  allowedActions: ['UPDATE', 'CONFIRM', 'CANCEL'],
  version: 7,
  remark: '首批采购',
  createdByName: '采购员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
  lines: [
    {
      id: 501,
      lineNo: 10,
      materialId: 10,
      materialCode: 'RM-001',
      materialName: '冷轧钢板',
      materialSpec: '1.5mm',
      unitId: 2,
      unitName: '千克',
      quantity: '12.500000',
      receivedQuantity: '0.000000',
      remainingQuantity: '12.500000',
      inTransitQuantity: '0.000000',
      inTransitStatus: 'NOT_COUNTED',
      inTransitStatusName: '不计入在途',
      currency: 'CNY',
      unitPrice: '3.100000',
      expectedArrivalDate: '2026-07-12',
      remark: '按周到货',
    },
  ],
  receipts: [
    {
      id: 700,
      receiptNo: 'PR-20260705-001',
      orderId: 99,
      orderNo: 'PO-20260704-001',
      supplierId: 100,
      supplierName: '华东五金',
      warehouseId: 30,
      warehouseName: '原料仓',
      businessDate: '2026-07-05',
      status: 'POSTED',
      lineCount: 1,
      totalQuantity: '5.000000',
      version: 3,
      remark: null,
      createdByName: '仓管员',
      createdAt: '2026-07-05T08:00:00+08:00',
      updatedAt: '2026-07-05T09:00:00+08:00',
      postedByName: '仓管员',
      postedAt: '2026-07-05T09:00:00+08:00',
    },
    {
      id: 701,
      receiptNo: 'PR-20260706-001',
      orderId: 99,
      orderNo: 'PO-20260704-001',
      supplierId: 100,
      supplierName: '华东五金',
      warehouseId: 30,
      warehouseName: '原料仓',
      businessDate: '2026-07-06',
      status: 'DRAFT',
      lineCount: 1,
      totalQuantity: '3.000000',
      version: 4,
      remark: null,
      createdByName: '仓管员',
      createdAt: '2026-07-06T08:00:00+08:00',
      updatedAt: '2026-07-06T08:30:00+08:00',
      postedByName: null,
      postedAt: null,
    },
  ],
}

const confirmedOrder: PurchaseOrderDetailRecord = {
  ...draftOrder,
  status: 'CONFIRMED',
  receivedQuantity: '0.000000',
  remainingQuantity: '12.500000',
  inTransitQuantity: '12.500000',
  inTransitStatus: 'NORMAL',
  inTransitStatusName: '正常在途',
  allowedActions: ['CANCEL', 'CLOSE', 'CREATE_RECEIPT', 'UPDATE_SCHEDULES', 'PRINT'],
  lines: draftOrder.lines.map((line) => ({
    ...line,
    inTransitQuantity: '12.500000',
    inTransitStatus: 'NORMAL',
    inTransitStatusName: '正常在途',
  })),
}

const partialOrder: PurchaseOrderDetailRecord = {
  ...draftOrder,
  status: 'PARTIALLY_RECEIVED',
  receivedQuantity: '5.000000',
  remainingQuantity: '7.500000',
  inTransitQuantity: '7.500000',
  inTransitStatus: 'DUE_SOON',
  inTransitStatusName: '临近到货',
  allowedActions: ['CLOSE', 'CREATE_RECEIPT'],
  lines: draftOrder.lines.map((line) => ({
    ...line,
    receivedQuantity: '5.000000',
    remainingQuantity: '7.500000',
    inTransitQuantity: '7.500000',
    inTransitStatus: 'DUE_SOON',
    inTransitStatusName: '临近到货',
  })),
}

function buttonsByText(wrapper: VueWrapper, text: string): VueWrapper[] {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

async function mountDetail(
  record: PurchaseOrderDetailRecord = draftOrder,
  permissions = [
    'procurement:order:view',
    'procurement:order:update',
    'procurement:order:confirm',
    'procurement:order:exception-submit',
    'procurement:order:cancel',
    'procurement:order:close',
    'procurement:order:print',
    'procurement:receipt:create',
    'procurement:receipt:view',
  ],
) {
  procurementApiMock.orders.get.mockResolvedValue(record)
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/procurement/orders', name: 'procurement-orders', component: { render: () => null } },
      { path: '/procurement/orders/:id', name: 'procurement-order-detail', component: PurchaseOrderDetailView },
      { path: '/procurement/orders/:id/edit', name: 'procurement-order-edit', component: { render: () => null } },
      { path: '/procurement/orders/:id/schedules', name: 'procurement-order-schedules', component: { render: () => null } },
      {
        path: '/procurement/orders/:orderId/receipts/create',
        name: 'procurement-receipt-create',
        component: { render: () => null },
      },
      { path: '/procurement/receipts/:id', name: 'procurement-receipt-detail', component: { render: () => null } },
    ],
  })
  await router.push('/procurement/orders/99')
  await router.isReady()
  const wrapper = mount(PurchaseOrderDetailView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('采购订单详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.orders.confirm.mockResolvedValue(confirmedOrder)
    procurementApiMock.orders.submitException.mockResolvedValue({
      id: 912,
      sceneCode: 'PROCUREMENT_ORDER_EXCEPTION_CONFIRM',
      objectType: 'PROCUREMENT_ORDER',
      objectId: 99,
      status: 'SUBMITTED',
      availableActions: [],
      version: 1,
      steps: [],
      histories: [],
      attachmentSnapshots: [],
    })
    procurementApiMock.orders.cancel.mockResolvedValue({ ...draftOrder, status: 'CANCELLED' })
    procurementApiMock.orders.close.mockResolvedValue({ ...confirmedOrder, status: 'CLOSED' })
    documentPlatformApiMock.printTasks.create.mockResolvedValue({
      id: 909,
      taskNo: 'TASK-ORDER-PRINT',
      taskType: 'PROCUREMENT_ORDER_PRINT',
      direction: 'PRINT',
      stage: 'PRINT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('加载采购订单详情并展示汇总、基础信息、明细和入库摘要', async () => {
    const { wrapper } = await mountDetail()

    expect(procurementApiMock.orders.get).toHaveBeenCalledWith('99')
    expect(wrapper.text()).toContain('PO-20260704-001')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('总数量')
    expect(wrapper.text()).toContain('12.5')
    expect(wrapper.text()).toContain('已入库')
    expect(wrapper.text()).toContain('未入库')
    expect(wrapper.text()).toContain('采购在途参考')
    expect(wrapper.text()).toContain('在途状态')
    expect(wrapper.text()).toContain('不计入在途')
    expect(wrapper.text()).toContain('华东五金')
    expect(wrapper.text()).toContain('RM-001 冷轧钢板')
    expect(wrapper.text()).toContain('千克')
    expect(wrapper.text()).toContain('行在途参考')
    expect(wrapper.text()).toContain('入库记录')
    expect(wrapper.text()).toContain('PR-20260705-001')
    expect(wrapper.text()).toContain('PR-20260706-001')
    expect(wrapper.text()).toContain('原料仓')
    expect(wrapper.text()).toContain('已过账')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).not.toContain('POSTED')
    expect(wrapper.text()).not.toContain('DRAFT')
  })

  it('按权限和状态展示操作按钮并进入入库占位路由', async () => {
    const { wrapper, router } = await mountDetail(confirmedOrder)

    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(1)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(1)
    expect(buttonsByText(wrapper, '创建入库')).toHaveLength(1)
    expect(buttonsByText(wrapper, '到货计划')).toHaveLength(1)
    expect(buttonsByText(wrapper, '固定打印')).toHaveLength(1)
    expect(wrapper.find('[data-test="view-purchase-receipt-summary"]').exists()).toBe(true)

    await wrapper.find('[data-test="create-purchase-receipt-detail"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-receipt-create')
    expect(router.currentRoute.value.params.orderId).toBe('99')

    await router.push('/procurement/orders/99')
    await flushPromises()
    await wrapper.find('[data-test="manage-purchase-schedules-detail"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-order-schedules')
    expect(router.currentRoute.value.params.id).toBe('99')

    await wrapper.find('[data-test="print-purchase-order-detail"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.printTasks.create).toHaveBeenCalledWith({
      objectType: 'PROCUREMENT_ORDER',
      objectId: 99,
      templateCode: 'PROCUREMENT_ORDER_V1',
      idempotencyKey: 'order-print-key',
    })
    expect(wrapper.text()).toContain('TASK-ORDER-PRINT')

    const readonly = await mountDetail(confirmedOrder, ['procurement:order:view'])
    expect(buttonsByText(readonly.wrapper, '取消')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '关闭')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '创建入库')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '到货计划')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '固定打印')).toHaveLength(0)
    expect(readonly.wrapper.find('[data-test="view-purchase-receipt-summary"]').exists()).toBe(false)
  })

  it('024 订单详情按 allowedActions 展示动作并显示项目专采、价格来源、税价和到货计划', async () => {
    const projectOrder: PurchaseOrderDetailRecord = {
      ...confirmedOrder,
      procurementMode: 'PROJECT',
      projectId: 30,
      projectCode: 'PRJ-024',
      projectName: '华东产线改造',
      approvalStatusName: '审批通过',
      exceptionApprovalStatus: 'APPROVED',
      exceptionReason: '非最低选价，供应商交期更短',
      priceSourceTypeName: '非最低选价',
      priceSourceReason: '交期更短，需要例外审批',
      currency: 'CNY',
      taxRate: '0.130000',
      taxExcludedUnitPrice: '100.000000',
      taxIncludedUnitPrice: '113.000000',
      nextArrivalDate: '2026-07-20',
      closeReason: '项目设计变更，未收数量不再采购',
      allowedActions: ['SUBMIT_EXCEPTION', 'CLOSE'],
      lines: confirmedOrder.lines.map((line) => ({
        ...line,
        procurementMode: 'PROJECT',
        projectId: 30,
        projectCode: 'PRJ-024',
        projectName: '华东产线改造',
        requisitionNo: 'REQ-PRJ-001',
        quoteNo: 'QUO-002',
        agreementNo: null,
        currency: 'CNY',
        taxRate: '0.130000',
        taxExcludedUnitPrice: '100.000000',
        taxIncludedUnitPrice: '113.000000',
        taxExcludedAmount: '10000.000000',
        taxIncludedAmount: '11300.000000',
        priceSourceTypeName: '非最低选价',
        schedules: [{
          id: 601,
          orderId: 99,
          orderLineId: 501,
          orderNo: 'PO-20260704-001',
          lineNo: 10,
          scheduleSeq: 10,
          expectedArrivalDate: '2026-07-20',
          plannedQuantity: '12.500000',
          receivedQuantity: '5.000000',
          remainingQuantity: '7.500000',
          status: 'PARTIALLY_RECEIVED',
          statusName: '部分到货',
          version: 31,
        }],
      })),
    }
    const { wrapper } = await mountDetail(projectOrder)

    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).toContain('审批状态：审批通过')
    expect(wrapper.text()).toContain('例外原因：非最低选价，供应商交期更短')
    expect(wrapper.text()).toContain('价格来源：非最低选价')
    expect(wrapper.text()).toContain('未税单价 100')
    expect(wrapper.text()).toContain('含税单价 113')
    expect(wrapper.text()).toContain('税率 0.13')
    expect(wrapper.text()).toContain('CNY')
    expect(wrapper.text()).toContain('下一到货日')
    expect(wrapper.text()).toContain('2026-07-20')
    expect(wrapper.text()).toContain('计划/已入库/剩余：12.5/5/7.5')
    expect(wrapper.text()).toContain('结案原因：项目设计变更，未收数量不再采购')
    expect(buttonsByText(wrapper, '提交例外审批')).toHaveLength(1)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(1)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(0)
    expect(buttonsByText(wrapper, '创建入库')).toHaveLength(0)

    await wrapper.find('[data-test="submit-purchase-order-exception"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.submitException).toHaveBeenCalledWith(99, expect.objectContaining({
      version: 7,
      reason: '非最低选价，供应商交期更短',
      idempotencyKey: expect.any(String),
    }))
    expect(procurementApiMock.orders.get).toHaveBeenCalledTimes(2)
  })

  it('订单详情从行级真实快照展示多行税价和价格来源编号', async () => {
    const projectOrder: PurchaseOrderDetailRecord = {
      ...confirmedOrder,
      procurementMode: 'PROJECT',
      projectId: 30,
      projectCode: 'PRJ-024',
      projectName: '华东产线改造',
      priceSourceType: 'MIXED',
      priceSourceTypeName: null,
      priceSourceNo: null,
      currency: 'CNY',
      taxRate: undefined,
      taxExcludedUnitPrice: undefined,
      taxIncludedUnitPrice: undefined,
      lines: [
        {
          ...confirmedOrder.lines[0],
          id: 501,
          lineNo: 10,
          priceSourceType: 'QUOTE',
          priceSourceTypeName: '供应商报价',
          priceSourceNo: 'QUO-LINE-001',
          taxRate: '0.130000',
          taxExcludedUnitPrice: '100.000000',
          taxIncludedUnitPrice: '113.000000',
        },
        {
          ...confirmedOrder.lines[0],
          id: 502,
          lineNo: 20,
          materialCode: 'RM-002',
          materialName: '铜排',
          priceSourceType: 'AGREEMENT',
          priceSourceTypeName: '价格协议',
          priceSourceNo: 'AGR-LINE-002',
          taxRate: '0.060000',
          taxExcludedUnitPrice: '200.000000',
          taxIncludedUnitPrice: '212.000000',
        },
      ],
    }

    const { wrapper } = await mountDetail(projectOrder)

    expect(wrapper.text()).toContain('2 行税价见明细')
    expect(wrapper.text()).toContain('价格来源：混合来源')
    expect(wrapper.text()).toContain('供应商报价 QUO-LINE-001')
    expect(wrapper.text()).toContain('价格协议 AGR-LINE-002')
    expect(wrapper.text()).toContain('未税单价 100')
    expect(wrapper.text()).toContain('含税单价 113')
    expect(wrapper.text()).toContain('税率 0.13')
    expect(wrapper.text()).toContain('未税单价 200')
    expect(wrapper.text()).toContain('含税单价 212')
    expect(wrapper.text()).toContain('税率 0.06')
    expect(wrapper.text()).not.toContain('未税单价 -')
  })

  it('确认、取消和关闭动作成功后刷新详情', async () => {
    const draft = await mountDetail(draftOrder)
    await draft.wrapper.find('[data-test="confirm-purchase-order-detail"]').trigger('click')
    await flushPromises()
    const confirmPayload = procurementApiMock.orders.confirm.mock.calls[0][1]
    expect(procurementApiMock.orders.confirm).toHaveBeenCalledWith(99, expect.objectContaining({ version: 7 }))
    expect(confirmPayload.idempotencyKey).toEqual(expect.any(String))
    expect(confirmPayload.idempotencyKey).not.toHaveLength(0)
    expect(procurementApiMock.orders.get).toHaveBeenCalledTimes(2)

    vi.clearAllMocks()
    const cancellable = await mountDetail(confirmedOrder)
    await cancellable.wrapper.find('[data-test="cancel-purchase-order-detail"]').trigger('click')
    await flushPromises()
    const cancelPayload = procurementApiMock.orders.cancel.mock.calls[0][1]
    expect(procurementApiMock.orders.cancel).toHaveBeenCalledWith(99, expect.objectContaining({ version: 7 }))
    expect(cancelPayload.idempotencyKey).toEqual(expect.any(String))
    expect(cancelPayload.idempotencyKey).not.toHaveLength(0)
    expect(procurementApiMock.orders.get).toHaveBeenCalledTimes(2)

    vi.clearAllMocks()
    const closable = await mountDetail(partialOrder)
    await closable.wrapper.find('[data-test="close-purchase-order-detail"]').trigger('click')
    await flushPromises()
    const closePayload = procurementApiMock.orders.close.mock.calls[0][1]
    expect(procurementApiMock.orders.close).toHaveBeenCalledWith(99, expect.objectContaining({ version: 7 }))
    expect(closePayload.idempotencyKey).toEqual(expect.any(String))
    expect(closePayload.idempotencyKey).not.toHaveLength(0)
    expect(procurementApiMock.orders.get).toHaveBeenCalledTimes(2)
  })

  it('状态操作失败时显示错误并保留详情', async () => {
    procurementApiMock.orders.confirm.mockRejectedValueOnce(new Error('供应商已停用，不能确认采购订单'))
    const { wrapper } = await mountDetail(draftOrder)

    await wrapper.find('[data-test="confirm-purchase-order-detail"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('供应商已停用，不能确认采购订单')
    expect(wrapper.text()).toContain('PO-20260704-001')
    expect(procurementApiMock.orders.confirm).toHaveBeenCalledTimes(1)
    expect(procurementApiMock.orders.confirm.mock.calls[0][1]).toMatchObject({
      version: 7,
      idempotencyKey: expect.any(String),
    })
    expect(procurementApiMock.orders.get).toHaveBeenCalledTimes(2)
  })

  it('详情加载失败时显示错误状态', async () => {
    procurementApiMock.orders.get.mockRejectedValueOnce(new Error('采购订单不存在'))
    const { wrapper } = await mountDetail(draftOrder)

    expect(wrapper.text()).toContain('采购订单不存在')
    expect(wrapper.text()).toContain('采购订单详情加载失败')
  })
})
