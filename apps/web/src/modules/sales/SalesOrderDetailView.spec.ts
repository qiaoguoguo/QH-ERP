import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { SalesOrderDetailRecord } from '../../shared/api/salesApi'
import { useAuthStore } from '../../stores/authStore'
import SalesOrderDetailView from './SalesOrderDetailView.vue'

const salesApiMock = vi.hoisted(() => ({
  orders: {
    get: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
    close: vi.fn(),
  },
}))

vi.mock('../../shared/api/salesApi', () => ({
  salesApi: salesApiMock,
}))

const draftOrder: SalesOrderDetailRecord = {
  id: 99,
  orderNo: 'SO-20260704-001',
  customerId: 100,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  orderDate: '2026-07-04',
  expectedShipDate: '2026-07-12',
  projectId: 12,
  projectNo: 'SP-202607-001',
  projectName: '华东扩产项目',
  contractId: 55,
  contractNo: 'SC-001',
  externalContractNo: 'EXT-001',
  status: 'DRAFT',
  lineCount: 1,
  totalQuantity: 12.5,
  shippedQuantity: 0,
  remainingQuantity: 12.5,
  remark: '首批销售',
  createdByName: '销售员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
  lines: [
    {
      id: 501,
      lineNo: 10,
      materialId: 10,
      materialCode: 'FG-001',
      materialName: '标准成品',
      materialSpec: 'A1',
      unitId: 2,
      unitName: '件',
      quantity: '12.500000',
      shippedQuantity: 0,
      remainingQuantity: 12.5,
      reservationWarehouseId: 30,
      reservationWarehouseName: '成品仓',
      unitPrice: '88.100000',
      expectedShipDate: '2026-07-12',
      remark: '按周发货',
    },
  ],
  shipments: [
    {
      id: 700,
      shipmentNo: 'SS-20260705-001',
      orderId: 99,
      orderNo: 'SO-20260704-001',
      customerId: 100,
      customerName: '华东客户',
      warehouseId: 30,
      warehouseName: '成品仓',
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
      shipmentNo: 'SS-20260706-001',
      orderId: 99,
      orderNo: 'SO-20260704-001',
      customerId: 100,
      customerName: '华东客户',
      warehouseId: 30,
      warehouseName: '成品仓',
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

const confirmedOrder: SalesOrderDetailRecord = {
  ...draftOrder,
  status: 'CONFIRMED',
  shippedQuantity: 0,
  remainingQuantity: 12.5,
}

const partialOrder: SalesOrderDetailRecord = {
  ...draftOrder,
  status: 'PARTIALLY_SHIPPED',
  shippedQuantity: 5,
  remainingQuantity: 7.5,
}

function buttonsByText(wrapper: VueWrapper, text: string): VueWrapper[] {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

async function mountDetail(
  record: SalesOrderDetailRecord = draftOrder,
  permissions = [
    'sales:order:view',
    'sales:order:update',
    'sales:order:confirm',
    'sales:order:cancel',
    'sales:order:close',
    'sales:shipment:create',
    'sales:shipment:view',
  ],
) {
  salesApiMock.orders.get.mockResolvedValue(record)
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
      { path: '/sales/orders', name: 'sales-orders', component: { render: () => null } },
      { path: '/sales/orders/:id', name: 'sales-order-detail', component: SalesOrderDetailView },
      { path: '/sales/orders/:id/edit', name: 'sales-order-edit', component: { render: () => null } },
      {
        path: '/sales/orders/:orderId/shipments/create',
        name: 'sales-shipment-create',
        component: { render: () => null },
      },
      { path: '/sales/shipments/:id', name: 'sales-shipment-detail', component: { render: () => null } },
    ],
  })
  await router.push('/sales/orders/99')
  await router.isReady()
  const wrapper = mount(SalesOrderDetailView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('销售订单详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    salesApiMock.orders.confirm.mockResolvedValue(confirmedOrder)
    salesApiMock.orders.cancel.mockResolvedValue({ ...draftOrder, status: 'CANCELLED' })
    salesApiMock.orders.close.mockResolvedValue({ ...confirmedOrder, status: 'CLOSED' })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('加载销售订单详情并展示汇总、基础信息、明细和出库摘要', async () => {
    const { wrapper } = await mountDetail()

    expect(salesApiMock.orders.get).toHaveBeenCalledWith('99')
    expect(wrapper.text()).toContain('SO-20260704-001')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('总数量')
    expect(wrapper.text()).toContain('12.5')
    expect(wrapper.text()).toContain('已出库')
    expect(wrapper.text()).toContain('未出库')
    expect(wrapper.text()).toContain('华东客户')
    expect(wrapper.text()).toContain('项目合同')
    expect(wrapper.text()).toContain('SP-202607-001 华东扩产项目 / SC-001')
    expect(wrapper.text()).toContain('FG-001 标准成品')
    expect(wrapper.text()).toContain('预留仓库')
    expect(wrapper.text()).toContain('成品仓')
    expect(wrapper.text()).toContain('件')
    expect(wrapper.text()).toContain('出库记录')
    expect(wrapper.text()).toContain('SS-20260705-001')
    expect(wrapper.text()).toContain('SS-20260706-001')
    expect(wrapper.text()).toContain('成品仓')
    expect(wrapper.text()).toContain('已过账')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).not.toContain('POSTED')
    expect(wrapper.text()).not.toContain('DRAFT')
  })

  it('历史未关联项目合同的销售订单显示明确空态', async () => {
    const legacyOrder: SalesOrderDetailRecord = {
      ...draftOrder,
      projectId: null,
      projectNo: null,
      projectName: null,
      contractId: null,
      contractNo: null,
      externalContractNo: null,
    }
    const { wrapper } = await mountDetail(legacyOrder)

    expect(wrapper.text()).toContain('项目合同')
    expect(wrapper.text()).toContain('未关联项目')
  })

  it('按权限和状态展示操作按钮并进入销售出库占位路由', async () => {
    const { wrapper, router } = await mountDetail(confirmedOrder)

    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(1)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(1)
    expect(buttonsByText(wrapper, '创建出库')).toHaveLength(1)
    expect(wrapper.find('[data-test="view-sales-shipment-summary"]').exists()).toBe(true)

    await wrapper.find('[data-test="create-sales-shipment-detail"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-shipment-create')
    expect(router.currentRoute.value.params.orderId).toBe('99')

    const readonly = await mountDetail(confirmedOrder, ['sales:order:view'])
    expect(buttonsByText(readonly.wrapper, '取消')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '关闭')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '创建出库')).toHaveLength(0)
    expect(readonly.wrapper.find('[data-test="view-sales-shipment-summary"]').exists()).toBe(false)
  })

  it('确认、取消和关闭动作成功后刷新详情', async () => {
    const draft = await mountDetail(draftOrder)
    await draft.wrapper.find('[data-test="confirm-sales-order-detail"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.confirm).toHaveBeenCalledWith(99)
    expect(salesApiMock.orders.get).toHaveBeenCalledTimes(2)

    vi.clearAllMocks()
    const cancellable = await mountDetail(confirmedOrder)
    await cancellable.wrapper.find('[data-test="cancel-sales-order-detail"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.cancel).toHaveBeenCalledWith(99)
    expect(salesApiMock.orders.get).toHaveBeenCalledTimes(2)

    vi.clearAllMocks()
    const closable = await mountDetail(partialOrder)
    await closable.wrapper.find('[data-test="close-sales-order-detail"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.close).toHaveBeenCalledWith(99)
    expect(salesApiMock.orders.get).toHaveBeenCalledTimes(2)
  })

  it('确认前发现明细缺少预留仓库时显示业务提示并阻止调用确认接口', async () => {
    const legacyDraftOrder: SalesOrderDetailRecord = {
      ...draftOrder,
      lines: draftOrder.lines.map((line) => ({
        ...line,
        reservationWarehouseId: null,
        reservationWarehouseName: null,
      })),
    }
    const { wrapper } = await mountDetail(legacyDraftOrder)

    await wrapper.find('[data-test="confirm-sales-order-detail"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('销售订单确认前每行必须选择预留仓库，确认只会按预留仓库现货库存预留，不使用采购在途')
    expect(salesApiMock.orders.confirm).not.toHaveBeenCalled()
  })

  it('状态操作失败时显示错误并保留详情', async () => {
    salesApiMock.orders.confirm.mockRejectedValueOnce(new Error('客户已停用，不能确认销售订单'))
    const { wrapper } = await mountDetail(draftOrder)

    await wrapper.find('[data-test="confirm-sales-order-detail"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('客户已停用，不能确认销售订单')
    expect(wrapper.text()).toContain('SO-20260704-001')
  })

  it('详情加载失败时显示错误状态', async () => {
    salesApiMock.orders.get.mockRejectedValueOnce(new Error('销售订单不存在'))
    const { wrapper } = await mountDetail(draftOrder)

    expect(wrapper.text()).toContain('销售订单不存在')
    expect(wrapper.text()).toContain('销售订单详情加载失败')
  })
})
