import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { WarehouseRecord } from '../../shared/api/masterDataApi'
import type { SalesOrderDetailRecord, SalesShipmentDetailRecord } from '../../shared/api/salesApi'
import { useAuthStore } from '../../stores/authStore'
import SalesShipmentFormView from './SalesShipmentFormView.vue'
import SalesShipmentLineEditor from './SalesShipmentLineEditor.vue'

const salesApiMock = vi.hoisted(() => ({
  orders: {
    get: vi.fn(),
  },
  shipments: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  warehouses: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/salesApi', () => ({
  salesApi: salesApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const warehouseA: WarehouseRecord = {
  id: 30,
  code: 'WH-FG',
  name: '成品仓',
  status: 'ENABLED',
}

const sourceOrder: SalesOrderDetailRecord = {
  id: 99,
  orderNo: 'SO-20260704-001',
  customerId: 100,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  orderDate: '2026-07-04',
  expectedShipDate: '2026-07-12',
  status: 'CONFIRMED',
  lineCount: 2,
  totalQuantity: 15.5,
  shippedQuantity: 5,
  remainingQuantity: 10.5,
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
      shippedQuantity: 5,
      remainingQuantity: 7.5,
      qualityStatus: 'QUALIFIED',
      qualityStatusName: '合格',
      quantityOnHand: '9.000000',
      availableQuantity: '9.000000',
      selectable: true,
      disabledReasonCode: null,
      disabledReason: null,
      maxSelectableQuantity: '7.500000',
      unitPrice: '88.100000',
      expectedShipDate: '2026-07-12',
      remark: '按周出库',
    },
    {
      id: 502,
      lineNo: 20,
      materialId: 11,
      materialCode: 'SF-001',
      materialName: '半成品组件',
      unitId: 3,
      unitName: '套',
      quantity: '3.000000',
      shippedQuantity: 0,
      remainingQuantity: 3,
      qualityStatus: 'REJECTED',
      qualityStatusName: '不合格',
      quantityOnHand: '3.000000',
      availableQuantity: '0.000000',
      selectable: false,
      disabledReasonCode: 'NON_QUALIFIED_NOT_AVAILABLE',
      disabledReason: '不合格库存不可销售出库',
      maxSelectableQuantity: '0.000000',
      unitPrice: '36.000000',
      expectedShipDate: '2026-07-12',
      remark: null,
    },
  ],
  shipments: [],
}

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
  orderSummary: sourceOrder,
  inventoryMovements: [],
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
      remark: '按单出库',
    },
  ],
}

const postedShipment: SalesShipmentDetailRecord = {
  ...draftShipment,
  status: 'POSTED',
  postedByName: '仓管员',
  postedAt: '2026-07-05T09:00:00+08:00',
}

async function setSelectValue(wrapper: VueWrapper, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index] as VueWrapper | undefined
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountForm(path = '/sales/orders/99/shipments/create') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: [
      'sales:order:view',
      'sales:shipment:view',
      'sales:shipment:create',
      'sales:shipment:update',
    ],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sales/shipments/:id', name: 'sales-shipment-detail', component: { render: () => null } },
      {
        path: '/sales/orders/:orderId/shipments/create',
        name: 'sales-shipment-create',
        component: SalesShipmentFormView,
      },
      { path: '/sales/shipments/:id/edit', name: 'sales-shipment-edit', component: SalesShipmentFormView },
      { path: '/sales/shipments', name: 'sales-shipments', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(SalesShipmentFormView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function fillValidShipment(wrapper: VueWrapper, quantity = '2.500000') {
  await setSelectValue(wrapper, 0, 30)
  await wrapper.find('input[name="sales-shipment-business-date"]').setValue('2026-07-05')
  await setSelectValue(wrapper, 1, 501)
  await wrapper.find('input[name="sales-shipment-line-quantity-0"]').setValue(quantity)
}

describe('销售出库表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    salesApiMock.orders.get.mockResolvedValue(sourceOrder)
    salesApiMock.shipments.get.mockResolvedValue(draftShipment)
    salesApiMock.shipments.create.mockResolvedValue(draftShipment)
    salesApiMock.shipments.update.mockResolvedValue(draftShipment)
    masterDataApiMock.warehouses.list.mockResolvedValue({
      items: [warehouseA],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
  })

  it('新建时加载来源订单和启用仓库，选择来源行后带出物料与未出库数量', async () => {
    const { wrapper } = await mountForm()

    expect(salesApiMock.orders.get).toHaveBeenCalledWith('99')
    expect(masterDataApiMock.warehouses.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    expect(wrapper.text()).toContain('销售出库')
    expect(wrapper.text()).toContain('SO-20260704-001')
    expect(wrapper.text()).toContain('华东客户')
    expect(wrapper.text()).toContain('订单未出库')
    expect(wrapper.text()).not.toContain('客户已停用')

    await setSelectValue(wrapper, 1, 501)

    expect(wrapper.text()).toContain('FG-001 标准成品')
    expect(wrapper.text()).toContain('7.5')
  })

  it('销售出库候选行展示质量状态、现存、合格可用、最大可选和禁用原因', async () => {
    const { wrapper } = await mountForm()

    await setSelectValue(wrapper, 1, 502)

    expect(wrapper.text()).toContain('不合格')
    expect(wrapper.text()).toContain('现存数量')
    expect(wrapper.text()).toContain('合格可用')
    expect(wrapper.text()).toContain('最大可选')
    expect(wrapper.text()).toContain('禁用原因')
    expect(wrapper.text()).toContain('不合格库存不可销售出库')
    expect(wrapper.text()).toContain('0')
    expect(wrapper.text()).not.toContain('canUse')
  })

  it('销售出库不可选候选行禁用数量输入并阻止保存', async () => {
    const { wrapper } = await mountForm()

    await setSelectValue(wrapper, 0, 30)
    await wrapper.find('input[name="sales-shipment-business-date"]').setValue('2026-07-05')
    await setSelectValue(wrapper, 1, 502)

    const quantityInput = wrapper.find('input[name="sales-shipment-line-quantity-0"]')
    expect(quantityInput.attributes('disabled')).toBeDefined()
    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('不合格库存不可销售出库')
    expect(salesApiMock.shipments.create).not.toHaveBeenCalled()
  })

  it('销售出库明细在表单内提供独立横向滚动边界', async () => {
    const { wrapper } = await mountForm()

    const lineFormItem = wrapper.find('[data-test="sales-shipment-line-form-item"]')
    const lineScroll = wrapper.findComponent(SalesShipmentLineEditor).find('[data-test="sales-shipment-line-scroll"]')

    expect(lineFormItem.exists()).toBe(true)
    expect(lineScroll.exists()).toBe(true)
    expect(lineScroll.classes()).toContain('table-scroll')
    expect(lineScroll.classes()).toContain('sales-shipment-line-scroll')
  })

  it('缺少必填项时阻止保存', async () => {
    const { wrapper } = await mountForm()

    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请完整填写出库仓库、业务日期和明细')
    expect(salesApiMock.shipments.create).not.toHaveBeenCalled()
  })

  it('校验数量格式、超出库和重复来源订单行', async () => {
    const { wrapper } = await mountForm()

    await fillValidShipment(wrapper, '1.1234567')
    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('数量最多 6 位小数')

    await wrapper.find('input[name="sales-shipment-line-quantity-0"]').setValue('8')
    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('本次出库数量不能超过未出库数量')

    await wrapper.find('input[name="sales-shipment-line-quantity-0"]').setValue('2')
    await wrapper.find('[data-test="add-sales-shipment-line"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 2, 501)
    await wrapper.find('input[name="sales-shipment-line-quantity-1"]').setValue('1')
    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('同一销售出库内来源订单行不能重复')
    expect(salesApiMock.shipments.create).not.toHaveBeenCalled()
  })

  it('新建保存创建 payload，出库数量保持字符串', async () => {
    const { wrapper, router } = await mountForm()

    await fillValidShipment(wrapper)
    await wrapper.find('input[name="sales-shipment-remark"]').setValue('首批销售出库')
    await wrapper.find('input[name="sales-shipment-line-remark-0"]').setValue('按单出库')
    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()

    expect(salesApiMock.shipments.create).toHaveBeenCalledWith('99', {
      warehouseId: 30,
      businessDate: '2026-07-05',
      remark: '首批销售出库',
      lines: [
        {
          lineNo: 10,
          orderLineId: 501,
          materialId: 10,
          unitId: 2,
          quantity: '2.500000',
          remark: '按单出库',
        },
      ],
    })
    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
    expect(router.currentRoute.value.params.id).toBe('700')
  })

  it('编辑草稿时回填明细并提交更新', async () => {
    const { wrapper, router } = await mountForm('/sales/shipments/700/edit')

    expect(salesApiMock.shipments.get).toHaveBeenCalledWith('700')
    expect(wrapper.text()).toContain('SS-20260705-001')
    expect((wrapper.find('input[name="sales-shipment-business-date"]').element as HTMLInputElement).value).toBe('2026-07-05')
    expect((wrapper.find('input[name="sales-shipment-line-quantity-0"]').element as HTMLInputElement).value).toBe('2.5')

    await wrapper.find('input[name="sales-shipment-line-quantity-0"]').setValue('3.000000')
    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()

    expect(salesApiMock.shipments.update).toHaveBeenCalledWith(700, expect.objectContaining({
      warehouseId: 30,
      lines: [expect.objectContaining({ quantity: '3.000000' })],
    }))
    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
  })

  it('编辑草稿时可追加同一来源订单的其他未出库行并保存', async () => {
    salesApiMock.orders.get.mockResolvedValueOnce({
      ...sourceOrder,
      lines: sourceOrder.lines.map((line) => (
        line.id === 502
          ? {
            ...line,
            qualityStatus: 'QUALIFIED',
            qualityStatusName: '合格',
            availableQuantity: '3.000000',
            selectable: true,
            disabledReasonCode: null,
            disabledReason: null,
            maxSelectableQuantity: '3.000000',
          }
          : line
      )),
    })
    const { wrapper } = await mountForm('/sales/shipments/700/edit')

    expect(salesApiMock.shipments.get).toHaveBeenCalledWith('700')
    expect(salesApiMock.orders.get).toHaveBeenCalledWith(99)
    expect(wrapper.findComponent(SalesShipmentLineEditor).props('sourceLines')).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: 501, materialCode: 'FG-001' }),
      expect.objectContaining({ id: 502, materialCode: 'SF-001' }),
    ]))

    await wrapper.find('[data-test="add-sales-shipment-line"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 2, 502)
    await wrapper.find('input[name="sales-shipment-line-quantity-1"]').setValue('1.000000')
    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('同一销售出库内来源订单行不能重复')
    expect(salesApiMock.shipments.update).toHaveBeenCalledWith(700, expect.objectContaining({
      lines: [
        expect.objectContaining({ orderLineId: 501, quantity: '2.5' }),
        expect.objectContaining({
          lineNo: 20,
          orderLineId: 502,
          materialId: 11,
          unitId: 3,
          quantity: '1.000000',
        }),
      ],
    }))
  })

  it('编辑草稿时按当前来源订单剩余量校验已有行，避免使用旧快照超出库', async () => {
    salesApiMock.orders.get.mockResolvedValueOnce({
      ...sourceOrder,
      shippedQuantity: 14.5,
      remainingQuantity: 1,
      lines: sourceOrder.lines.map((line) => (
        line.id === 501
          ? { ...line, shippedQuantity: 11.5, remainingQuantity: 1 }
          : line
      )),
    })
    const { wrapper } = await mountForm('/sales/shipments/700/edit')

    expect(salesApiMock.shipments.get).toHaveBeenCalledWith('700')
    expect(salesApiMock.orders.get).toHaveBeenCalledWith(99)

    await wrapper.find('input[name="sales-shipment-line-quantity-0"]').setValue('2')
    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('本次出库数量不能超过未出库数量')
    expect(salesApiMock.shipments.update).not.toHaveBeenCalled()
  })

  it('已过账销售出库不可提交', async () => {
    salesApiMock.shipments.get.mockResolvedValueOnce(postedShipment)
    const { wrapper } = await mountForm('/sales/shipments/700/edit')

    expect(wrapper.text()).toContain('已过账销售出库不可编辑')
    expect(wrapper.find('[data-test="save-sales-shipment"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.shipments.update).not.toHaveBeenCalled()
  })

  it('来源订单状态不允许创建销售出库时禁止保存', async () => {
    salesApiMock.orders.get.mockResolvedValueOnce({ ...sourceOrder, status: 'DRAFT' })
    const { wrapper } = await mountForm()

    expect(wrapper.text()).toContain('仅已确认或部分出库销售订单可创建销售出库')
    expect(wrapper.find('[data-test="save-sales-shipment"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-test="save-sales-shipment"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.shipments.create).not.toHaveBeenCalled()
  })
})
