import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PurchaseReceiptDetailRecord } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import PurchaseReceiptDetailView from './PurchaseReceiptDetailView.vue'

const procurementApiMock = vi.hoisted(() => ({
  receipts: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
}))

const draftReceipt: PurchaseReceiptDetailRecord = {
  id: 700,
  receiptNo: 'PR-20260705-001',
  orderId: 99,
  orderNo: 'PO-20260704-001',
  supplierId: 100,
  supplierName: '华东五金',
  warehouseId: 30,
  warehouseName: '原料仓',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  lineCount: 1,
  totalQuantity: 2.5,
  remark: '首批入库',
  createdByName: '仓管员',
  createdAt: '2026-07-05T08:00:00+08:00',
  updatedAt: '2026-07-05T08:30:00+08:00',
  postedByName: null,
  postedAt: null,
  orderSummary: {
    id: 99,
    orderNo: 'PO-20260704-001',
    supplierId: 100,
    supplierCode: 'SUP-A',
    supplierName: '华东五金',
    orderDate: '2026-07-04',
    expectedArrivalDate: '2026-07-12',
    status: 'CONFIRMED',
    lineCount: 1,
    totalQuantity: 12.5,
    receivedQuantity: 5,
    remainingQuantity: 7.5,
    remark: '首批采购',
    createdByName: '采购员',
    createdAt: '2026-07-04T08:00:00+08:00',
    updatedAt: '2026-07-04T09:00:00+08:00',
  },
  lines: [
    {
      id: 900,
      lineNo: 10,
      orderLineId: 501,
      materialId: 10,
      materialCode: 'RM-001',
      materialName: '冷轧钢板',
      unitId: 2,
      unitName: '千克',
      orderedQuantity: 12.5,
      receivedQuantityBefore: 5,
      remainingQuantityBefore: 7.5,
      quantity: 2.5,
      beforeQuantity: null,
      afterQuantity: null,
      remark: '按单入库',
    },
  ],
  inventoryMovements: [],
}

const postedReceipt: PurchaseReceiptDetailRecord = {
  ...draftReceipt,
  status: 'POSTED',
  postedByName: '仓管员',
  postedAt: '2026-07-05T09:00:00+08:00',
  inventoryMovements: [
    {
      id: 1,
      movementNo: 'MOV-20260705-001',
      movementType: 'PURCHASE_RECEIPT',
      direction: 'IN',
      warehouseName: '原料仓',
      materialCode: 'RM-001',
      materialName: '冷轧钢板',
      quantity: 2.5,
      beforeQuantity: 5,
      afterQuantity: 7.5,
      businessDate: '2026-07-05',
      operatorName: '仓管员',
      occurredAt: '2026-07-05T09:00:00+08:00',
    },
  ],
}

function buttonsByText(wrapper: VueWrapper, text: string): VueWrapper[] {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

async function mountDetail(
  record: PurchaseReceiptDetailRecord = draftReceipt,
  permissions = [
    'procurement:order:view',
    'procurement:receipt:view',
    'procurement:receipt:update',
    'procurement:receipt:post',
  ],
) {
  procurementApiMock.receipts.get.mockResolvedValue(record)
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
      { path: '/procurement/receipts', name: 'procurement-receipts', component: { render: () => null } },
      { path: '/procurement/receipts/:id', name: 'procurement-receipt-detail', component: PurchaseReceiptDetailView },
      { path: '/procurement/receipts/:id/edit', name: 'procurement-receipt-edit', component: { render: () => null } },
      { path: '/procurement/orders/:id', name: 'procurement-order-detail', component: { render: () => null } },
    ],
  })
  await router.push('/procurement/receipts/700')
  await router.isReady()
  const wrapper = mount(PurchaseReceiptDetailView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('采购入库详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.receipts.post.mockResolvedValue(postedReceipt)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('加载采购入库详情并展示汇总、来源订单、明细和库存流水空状态', async () => {
    const { wrapper } = await mountDetail()

    expect(procurementApiMock.receipts.get).toHaveBeenCalledWith('700')
    expect(wrapper.text()).toContain('采购入库详情')
    expect(wrapper.text()).toContain('PR-20260705-001')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('总数量')
    expect(wrapper.text()).toContain('2.5')
    expect(wrapper.text()).toContain('业务日期')
    expect(wrapper.text()).toContain('2026-07-05')
    expect(wrapper.text()).toContain('原料仓')
    expect(wrapper.text()).toContain('来源订单')
    expect(wrapper.text()).toContain('PO-20260704-001')
    expect(wrapper.text()).toContain('华东五金')
    expect(wrapper.text()).toContain('RM-001 冷轧钢板')
    expect(wrapper.text()).toContain('本次入库数量')
    expect(wrapper.text()).toContain('暂无库存流水追溯')
    expect(wrapper.text()).not.toContain('DRAFT')
  })

  it('按权限和状态展示操作按钮并可跳转来源订单', async () => {
    const { wrapper, router } = await mountDetail()

    expect(buttonsByText(wrapper, '编辑')).toHaveLength(1)
    expect(buttonsByText(wrapper, '过账')).toHaveLength(1)
    expect(buttonsByText(wrapper, '查看来源订单')).toHaveLength(1)

    await wrapper.find('[data-test="view-source-purchase-order"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-order-detail')
    expect(router.currentRoute.value.params.id).toBe('99')

    const readonly = await mountDetail(draftReceipt, ['procurement:receipt:view'])
    expect(buttonsByText(readonly.wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '过账')).toHaveLength(0)

    const posted = await mountDetail(postedReceipt)
    expect(posted.wrapper.text()).toContain('已过账采购入库只读')
    expect(buttonsByText(posted.wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(posted.wrapper, '过账')).toHaveLength(0)
  })

  it('过账成功后刷新详情，失败时显示错误', async () => {
    const { wrapper } = await mountDetail()

    await wrapper.find('[data-test="post-purchase-receipt-detail"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.receipts.post).toHaveBeenCalledWith(700)
    expect(procurementApiMock.receipts.get).toHaveBeenCalledTimes(2)

    vi.clearAllMocks()
    procurementApiMock.receipts.post.mockRejectedValueOnce(new Error('采购入库明细数量无效'))
    const failed = await mountDetail()
    await failed.wrapper.find('[data-test="post-purchase-receipt-detail"]').trigger('click')
    await flushPromises()

    expect(failed.wrapper.text()).toContain('采购入库明细数量无效')
    expect(procurementApiMock.receipts.get).toHaveBeenCalledTimes(1)
  })

  it('展示库存流水追溯摘要', async () => {
    const { wrapper } = await mountDetail(postedReceipt)

    expect(wrapper.text()).toContain('库存流水追溯')
    expect(wrapper.text()).toContain('MOV-20260705-001')
    expect(wrapper.text()).toContain('采购入库')
    expect(wrapper.text()).toContain('RM-001 冷轧钢板')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('7.5')
    expect(wrapper.text()).not.toContain('暂无库存流水追溯')
  })

  it('详情加载失败时显示错误状态', async () => {
    procurementApiMock.receipts.get.mockRejectedValueOnce(new Error('采购入库不存在'))
    const { wrapper } = await mountDetail()

    expect(wrapper.text()).toContain('采购入库不存在')
    expect(wrapper.text()).toContain('采购入库详情加载失败')
  })
})
