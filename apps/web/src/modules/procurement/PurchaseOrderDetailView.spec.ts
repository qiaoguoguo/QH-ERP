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
    cancel: vi.fn(),
    close: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
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
  totalQuantity: 12.5,
  receivedQuantity: 0,
  remainingQuantity: 12.5,
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
      quantity: 12.5,
      receivedQuantity: 0,
      remainingQuantity: 12.5,
      unitPrice: 3.1,
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
      totalQuantity: 5,
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
      totalQuantity: 3,
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
  receivedQuantity: 0,
  remainingQuantity: 12.5,
}

const partialOrder: PurchaseOrderDetailRecord = {
  ...draftOrder,
  status: 'PARTIALLY_RECEIVED',
  receivedQuantity: 5,
  remainingQuantity: 7.5,
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
    'procurement:order:cancel',
    'procurement:order:close',
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
    vi.stubGlobal('confirm', vi.fn(() => true))
    procurementApiMock.orders.confirm.mockResolvedValue(confirmedOrder)
    procurementApiMock.orders.cancel.mockResolvedValue({ ...draftOrder, status: 'CANCELLED' })
    procurementApiMock.orders.close.mockResolvedValue({ ...confirmedOrder, status: 'CLOSED' })
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
    expect(wrapper.text()).toContain('华东五金')
    expect(wrapper.text()).toContain('RM-001 冷轧钢板')
    expect(wrapper.text()).toContain('千克')
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
    expect(wrapper.find('[data-test="view-purchase-receipt-summary"]').exists()).toBe(true)

    await wrapper.find('[data-test="create-purchase-receipt-detail"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-receipt-create')
    expect(router.currentRoute.value.params.orderId).toBe('99')

    const readonly = await mountDetail(confirmedOrder, ['procurement:order:view'])
    expect(buttonsByText(readonly.wrapper, '取消')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '关闭')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '创建入库')).toHaveLength(0)
    expect(readonly.wrapper.find('[data-test="view-purchase-receipt-summary"]').exists()).toBe(false)
  })

  it('确认、取消和关闭动作成功后刷新详情', async () => {
    const draft = await mountDetail(draftOrder)
    await draft.wrapper.find('[data-test="confirm-purchase-order-detail"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.confirm).toHaveBeenCalledWith(99)
    expect(procurementApiMock.orders.get).toHaveBeenCalledTimes(2)

    vi.clearAllMocks()
    const cancellable = await mountDetail(confirmedOrder)
    await cancellable.wrapper.find('[data-test="cancel-purchase-order-detail"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.cancel).toHaveBeenCalledWith(99)
    expect(procurementApiMock.orders.get).toHaveBeenCalledTimes(2)

    vi.clearAllMocks()
    const closable = await mountDetail(partialOrder)
    await closable.wrapper.find('[data-test="close-purchase-order-detail"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.close).toHaveBeenCalledWith(99)
    expect(procurementApiMock.orders.get).toHaveBeenCalledTimes(2)
  })

  it('状态操作失败时显示错误并保留详情', async () => {
    procurementApiMock.orders.confirm.mockRejectedValueOnce(new Error('供应商已停用，不能确认采购订单'))
    const { wrapper } = await mountDetail(draftOrder)

    await wrapper.find('[data-test="confirm-purchase-order-detail"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('供应商已停用，不能确认采购订单')
    expect(wrapper.text()).toContain('PO-20260704-001')
  })

  it('详情加载失败时显示错误状态', async () => {
    procurementApiMock.orders.get.mockRejectedValueOnce(new Error('采购订单不存在'))
    const { wrapper } = await mountDetail(draftOrder)

    expect(wrapper.text()).toContain('采购订单不存在')
    expect(wrapper.text()).toContain('采购订单详情加载失败')
  })
})
