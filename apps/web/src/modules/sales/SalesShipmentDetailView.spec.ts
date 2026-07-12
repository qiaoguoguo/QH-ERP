import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { SalesShipmentDetailRecord } from '../../shared/api/salesApi'
import { useConfirmActionMock } from '../../test/setup'
import { useAuthStore } from '../../stores/authStore'
import SalesShipmentDetailView from './SalesShipmentDetailView.vue'

const confirmActionMock = useConfirmActionMock()

const salesApiMock = vi.hoisted(() => ({
  shipments: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

vi.mock('../../shared/api/salesApi', () => ({
  salesApi: salesApiMock,
}))

const draftShipment: SalesShipmentDetailRecord = {
  id: 700,
  shipmentNo: 'SS-20260705-001',
  orderId: 99,
  orderNo: 'SO-20260704-001',
  customerId: 100,
  customerName: '华东客户',
  warehouseId: 30,
  warehouseName: '成品仓',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  lineCount: 1,
  totalQuantity: 2.5,
  remark: '首批销售出库',
  createdByName: '仓管员',
  createdAt: '2026-07-05T08:00:00+08:00',
  updatedAt: '2026-07-05T08:30:00+08:00',
  postedByName: null,
  postedAt: null,
  orderSummary: {
    id: 99,
    orderNo: 'SO-20260704-001',
    customerId: 100,
    customerCode: 'CUS-A',
    customerName: '华东客户',
    orderDate: '2026-07-04',
    expectedShipDate: '2026-07-12',
    status: 'CONFIRMED',
    lineCount: 1,
    totalQuantity: 12.5,
    shippedQuantity: 5,
    remainingQuantity: 7.5,
    remark: '首批销售',
    createdByName: '销售员',
    createdAt: '2026-07-04T08:00:00+08:00',
    updatedAt: '2026-07-04T09:00:00+08:00',
    version: 4,
  },
  lines: [
    {
      id: 900,
      lineNo: 10,
      orderLineId: 501,
      materialId: 10,
      materialCode: 'FG-001',
      materialName: '标准成品',
      unitId: 2,
      unitName: '件',
      orderedQuantity: 12.5,
      shippedQuantityBefore: 5,
      remainingQuantityBefore: 7.5,
      quantity: '2.5',
      beforeQuantity: null,
      afterQuantity: null,
      trackingMethod: 'BATCH',
      trackingMethodName: '批次管理',
      trackingAllocations: [
        {
          batchId: 320,
          batchNo: 'B-FG-001',
          quantity: '2.500000',
          qualityStatusName: '合格',
          sourceDocumentNo: 'PR-20260701-001',
        },
      ],
      remark: '按单出库',
    },
  ],
  inventoryMovements: [],
}

const postedShipment: SalesShipmentDetailRecord = {
  ...draftShipment,
  status: 'POSTED',
  postedByName: '仓管员',
  postedAt: '2026-07-05T09:00:00+08:00',
  inventoryMovements: [
    {
      id: 1,
      movementNo: 'MOV-20260705-001',
      movementType: 'SALES_SHIPMENT',
      direction: 'OUT',
      warehouseName: '成品仓',
      materialCode: 'FG-001',
      materialName: '标准成品',
      quantity: 2.5,
      beforeQuantity: 10,
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
  record: SalesShipmentDetailRecord = draftShipment,
  permissions = [
    'sales:order:view',
    'sales:shipment:view',
    'sales:shipment:update',
    'sales:shipment:post',
    'inventory:movement:view',
  ],
) {
  salesApiMock.shipments.get.mockResolvedValue(record)
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
      { path: '/sales/shipments', name: 'sales-shipments', component: { render: () => null } },
      { path: '/sales/shipments/:id', name: 'sales-shipment-detail', component: SalesShipmentDetailView },
      { path: '/sales/shipments/:id/edit', name: 'sales-shipment-edit', component: { render: () => null } },
      { path: '/sales/orders/:id', name: 'sales-order-detail', component: { render: () => null } },
      { path: '/inventory/movements', name: 'inventory-movements', component: { render: () => null } },
    ],
  })
  await router.push('/sales/shipments/700')
  await router.isReady()
  const wrapper = mount(SalesShipmentDetailView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('销售出库详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    salesApiMock.shipments.post.mockResolvedValue(postedShipment)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('加载销售出库详情并展示汇总、来源订单、明细和库存流水空状态', async () => {
    const { wrapper } = await mountDetail()

    expect(salesApiMock.shipments.get).toHaveBeenCalledWith('700')
    expect(wrapper.text()).toContain('销售出库详情')
    expect(wrapper.text()).toContain('SS-20260705-001')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('总数量')
    expect(wrapper.text()).toContain('2.5')
    expect(wrapper.text()).toContain('业务日期')
    expect(wrapper.text()).toContain('2026-07-05')
    expect(wrapper.text()).toContain('成品仓')
    expect(wrapper.text()).toContain('来源订单')
    expect(wrapper.text()).toContain('SO-20260704-001')
    expect(wrapper.text()).toContain('华东客户')
    expect(wrapper.text()).toContain('FG-001 标准成品')
    expect(wrapper.text()).toContain('本次出库数量')
    expect(wrapper.text()).toContain('批次/序列')
    expect(wrapper.text()).toContain('B-FG-001')
    expect(wrapper.text()).toContain('合格')
    expect(wrapper.text()).toContain('暂无库存流水')
    expect(wrapper.text()).not.toContain('DRAFT')
  })

  it('按权限和状态展示操作按钮并可跳转来源订单和库存流水', async () => {
    const { wrapper, router } = await mountDetail()

    expect(buttonsByText(wrapper, '编辑')).toHaveLength(1)
    expect(buttonsByText(wrapper, '过账')).toHaveLength(1)
    expect(buttonsByText(wrapper, '查看来源订单')).toHaveLength(1)
    expect(buttonsByText(wrapper, '查看库存流水')).toHaveLength(1)

    await wrapper.find('[data-test="view-source-sales-order"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-order-detail')
    expect(router.currentRoute.value.params.id).toBe('99')

    await router.push('/sales/shipments/700')
    await flushPromises()
    await wrapper.find('[data-test="view-sales-shipment-movements"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('inventory-movements')
    expect(router.currentRoute.value.query).toEqual({
      movementType: 'SALES_SHIPMENT',
      warehouseId: '30',
    })

    const readonly = await mountDetail(draftShipment, ['sales:shipment:view'])
    expect(buttonsByText(readonly.wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(readonly.wrapper, '过账')).toHaveLength(0)

    const posted = await mountDetail(postedShipment)
    expect(posted.wrapper.text()).toContain('已过账销售出库只读')
    expect(buttonsByText(posted.wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(posted.wrapper, '过账')).toHaveLength(0)
  })

  it('过账使用二次确认，成功后刷新详情，库存不足失败时显示错误', async () => {
    const { wrapper } = await mountDetail()

    await wrapper.find('[data-test="post-sales-shipment-detail"]').trigger('click')
    await flushPromises()
    expect(confirmActionMock).toHaveBeenCalledWith('确认过账销售出库“SS-20260705-001”？')
    expect(salesApiMock.shipments.post).toHaveBeenCalledWith(700)
    expect(salesApiMock.shipments.get).toHaveBeenCalledTimes(2)

    vi.clearAllMocks()
    salesApiMock.shipments.post.mockRejectedValueOnce(new Error('库存不足，不能过账销售出库'))
    const failed = await mountDetail()
    await failed.wrapper.find('[data-test="post-sales-shipment-detail"]').trigger('click')
    await flushPromises()

    expect(failed.wrapper.text()).toContain('库存不足，不能过账销售出库')
    expect(salesApiMock.shipments.get).toHaveBeenCalledTimes(1)
  })

  it('展示销售出库库存流水追溯摘要', async () => {
    const { wrapper } = await mountDetail(postedShipment)

    expect(wrapper.text()).toContain('库存流水追溯')
    expect(wrapper.text()).toContain('MOV-20260705-001')
    expect(wrapper.text()).toContain('销售出库')
    expect(wrapper.text()).toContain('出库')
    expect(wrapper.text()).toContain('FG-001 标准成品')
    expect(wrapper.text()).toContain('10')
    expect(wrapper.text()).toContain('7.5')
    expect(wrapper.text()).not.toContain('暂无库存流水')
  })

  it('详情加载失败时显示错误状态', async () => {
    salesApiMock.shipments.get.mockRejectedValueOnce(new Error('销售出库不存在'))
    const { wrapper } = await mountDetail()

    expect(wrapper.text()).toContain('销售出库不存在')
    expect(wrapper.text()).toContain('销售出库详情加载失败')
  })
})
