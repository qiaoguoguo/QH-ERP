import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { WarehouseRecord } from '../../shared/api/masterDataApi'
import type { PurchaseOrderDetailRecord, PurchaseReceiptDetailRecord } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import TrackingAllocationEditor from '../inventory/tracking/TrackingAllocationEditor.vue'
import PurchaseReceiptFormView from './PurchaseReceiptFormView.vue'
import PurchaseReceiptLineEditor from './PurchaseReceiptLineEditor.vue'

const procurementApiMock = vi.hoisted(() => ({
  orders: {
    get: vi.fn(),
  },
  receipts: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  materials: {
    get: vi.fn(),
  },
  warehouses: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const warehouseA: WarehouseRecord = {
  id: 30,
  code: 'WH-RM',
  name: '原料仓',
  status: 'ENABLED',
}

const sourceOrder: PurchaseOrderDetailRecord = {
  id: 99,
  orderNo: 'PO-20260704-001',
  supplierId: 100,
  supplierCode: 'SUP-A',
  supplierName: '华东五金',
  orderDate: '2026-07-04',
  expectedArrivalDate: '2026-07-12',
  status: 'CONFIRMED',
  lineCount: 2,
  totalQuantity: '15.500000',
  receivedQuantity: '5.000000',
  remainingQuantity: '10.500000',
  currency: 'CNY',
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
      receivedQuantity: '5.000000',
      remainingQuantity: '7.500000',
      inTransitQuantity: '7.500000',
      inTransitStatus: 'NORMAL',
      inTransitStatusName: '正常在途',
      currency: 'CNY',
      unitPrice: '3.100000',
      expectedArrivalDate: '2026-07-12',
      remark: '按周到货',
    },
    {
      id: 502,
      lineNo: 20,
      materialId: 11,
      materialCode: 'RM-002',
      materialName: '紧固件',
      unitId: 3,
      unitName: '件',
      quantity: '3.000000',
      receivedQuantity: '0.000000',
      remainingQuantity: '3.000000',
      inTransitQuantity: '3.000000',
      inTransitStatus: 'DUE_SOON',
      inTransitStatusName: '临近到货',
      currency: 'CNY',
      unitPrice: '1.200000',
      expectedArrivalDate: '2026-07-12',
      remark: null,
    },
  ],
  receipts: [],
}

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
  totalQuantity: '2.500000',
  version: 21,
  remark: '首批入库',
  createdByName: '仓管员',
  createdAt: '2026-07-05T08:00:00+08:00',
  updatedAt: '2026-07-05T08:30:00+08:00',
  postedByName: null,
  postedAt: null,
  orderSummary: sourceOrder,
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
      orderedQuantity: '12.500000',
      receivedQuantityBefore: '5.000000',
      remainingQuantityBefore: '7.500000',
      quantity: '2.500000',
      beforeQuantity: null,
      afterQuantity: null,
      remark: '按单入库',
    },
  ],
}

const postedReceipt: PurchaseReceiptDetailRecord = {
  ...draftReceipt,
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

async function mountForm(path = '/procurement/orders/99/receipts/create') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: [
      'procurement:order:view',
      'procurement:receipt:view',
      'procurement:receipt:create',
      'procurement:receipt:update',
    ],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/procurement/receipts/:id', name: 'procurement-receipt-detail', component: { render: () => null } },
      {
        path: '/procurement/orders/:orderId/receipts/create',
        name: 'procurement-receipt-create',
        component: PurchaseReceiptFormView,
      },
      { path: '/procurement/receipts/:id/edit', name: 'procurement-receipt-edit', component: PurchaseReceiptFormView },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(PurchaseReceiptFormView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function fillValidReceipt(wrapper: VueWrapper, quantity = '2.500000') {
  await setSelectValue(wrapper, 0, 30)
  await wrapper.find('input[name="purchase-receipt-business-date"]').setValue('2026-07-05')
  await setSelectValue(wrapper, 1, 501)
  await wrapper.find('input[name="purchase-receipt-line-quantity-0"]').setValue(quantity)
}

describe('采购入库表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.orders.get.mockResolvedValue(sourceOrder)
    procurementApiMock.receipts.get.mockResolvedValue(draftReceipt)
    procurementApiMock.receipts.create.mockResolvedValue(draftReceipt)
    procurementApiMock.receipts.update.mockResolvedValue(draftReceipt)
    masterDataApiMock.materials.get.mockImplementation((id: number) => Promise.resolve({
      id,
      code: id === 10 ? 'RM-001' : 'RM-002',
      name: id === 10 ? '冷轧钢板' : '紧固件',
      status: 'ENABLED',
      materialType: 'RAW_MATERIAL',
      sourceType: 'PURCHASED',
      trackingMethod: id === 10 ? 'BATCH' : 'NONE',
      trackingMethodName: id === 10 ? '批次管理' : '不追踪',
      categoryId: 1,
      unitId: id === 10 ? 2 : 3,
    }))
    masterDataApiMock.warehouses.list.mockResolvedValue({
      items: [warehouseA],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
  })

  it('新建时加载来源订单和启用仓库，选择来源行后带出物料与未入库数量', async () => {
    const { wrapper } = await mountForm()

    expect(procurementApiMock.orders.get).toHaveBeenCalledWith('99')
    expect(masterDataApiMock.warehouses.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    expect(wrapper.text()).toContain('采购入库')
    expect(wrapper.text()).toContain('PO-20260704-001')
    expect(wrapper.text()).toContain('华东五金')

    await setSelectValue(wrapper, 1, 501)

    expect(wrapper.text()).toContain('RM-001 冷轧钢板')
    expect(wrapper.text()).toContain('7.5')
    expect(wrapper.text()).toContain('采购在途参考')
    expect(wrapper.text()).toContain('正常在途')
  })

  it('缺少必填项时阻止保存', async () => {
    const { wrapper } = await mountForm()

    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请完整填写入库仓库、业务日期和明细')
    expect(procurementApiMock.receipts.create).not.toHaveBeenCalled()
  })

  it('校验数量格式、超入库和重复来源订单行', async () => {
    const { wrapper } = await mountForm()

    await fillValidReceipt(wrapper, '1.1234567')
    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('数量最多 6 位小数')

    await wrapper.find('input[name="purchase-receipt-line-quantity-0"]').setValue('8')
    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('本次入库数量不能超过未入库数量')

    await wrapper.find('input[name="purchase-receipt-line-quantity-0"]').setValue('2')
    await wrapper.find('[data-test="add-purchase-receipt-line"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 2, 501)
    await wrapper.find('input[name="purchase-receipt-line-quantity-1"]').setValue('1')
    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('同一采购入库内来源订单行不能重复')
    expect(procurementApiMock.receipts.create).not.toHaveBeenCalled()
  })

  it('新建保存创建 payload，入库数量保持字符串', async () => {
    const { wrapper, router } = await mountForm()

    await fillValidReceipt(wrapper)
    wrapper.findComponent(TrackingAllocationEditor).vm.$emit('update:modelValue', [
      { batchNo: 'B-PR-001', quantity: '2.500000' },
    ])
    await flushPromises()
    await wrapper.find('input[name="purchase-receipt-remark"]').setValue('首批入库')
    await wrapper.find('input[name="purchase-receipt-line-remark-0"]').setValue('按单入库')
    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.receipts.create).toHaveBeenCalledWith('99', {
      warehouseId: 30,
      businessDate: '2026-07-05',
      remark: '首批入库',
      lines: [
        {
          lineNo: 10,
          orderLineId: 501,
          materialId: 10,
          unitId: 2,
          quantity: '2.500000',
          trackingAllocations: [{ batchNo: 'B-PR-001', quantity: '2.500000' }],
          remark: '按单入库',
        },
      ],
    })
    expect(router.currentRoute.value.name).toBe('procurement-receipt-detail')
    expect(router.currentRoute.value.params.id).toBe('700')
  })

  it('批次管理采购入库行显示追踪分配并随保存提交', async () => {
    const { wrapper } = await mountForm()

    await setSelectValue(wrapper, 0, 30)
    await wrapper.find('input[name="purchase-receipt-business-date"]').setValue('2026-07-05')
    await setSelectValue(wrapper, 1, 501)
    await wrapper.find('input[name="purchase-receipt-line-quantity-0"]').setValue('2.500000')
    await flushPromises()

    expect(masterDataApiMock.materials.get).toHaveBeenCalledWith(10)
    expect(wrapper.findComponent(TrackingAllocationEditor).exists()).toBe(true)
    expect(wrapper.text()).toContain('批次分配')

    wrapper.findComponent(TrackingAllocationEditor).vm.$emit('update:modelValue', [
      { batchNo: 'B-PR-001', quantity: '2.500000' },
    ])
    await flushPromises()

    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.receipts.create).toHaveBeenCalledWith('99', expect.objectContaining({
      lines: [
        expect.objectContaining({
          orderLineId: 501,
          trackingAllocations: [{ batchNo: 'B-PR-001', quantity: '2.500000' }],
        }),
      ],
    }))
  })

  it('批次管理采购入库追踪数量合计不一致时阻止保存', async () => {
    const { wrapper } = await mountForm()

    await fillValidReceipt(wrapper)
    wrapper.findComponent(TrackingAllocationEditor).vm.$emit('update:modelValue', [
      { batchNo: 'B-PR-001', quantity: '1.000000' },
    ])
    await flushPromises()

    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('追踪分配')
    expect(wrapper.text()).toContain('与业务数量')
    expect(procurementApiMock.receipts.create).not.toHaveBeenCalled()
  })

  it('编辑草稿时回填明细并提交更新', async () => {
    const { wrapper, router } = await mountForm('/procurement/receipts/700/edit')

    expect(procurementApiMock.receipts.get).toHaveBeenCalledWith('700')
    expect(wrapper.text()).toContain('PR-20260705-001')
    expect((wrapper.find('input[name="purchase-receipt-business-date"]').element as HTMLInputElement).value).toBe('2026-07-05')
    expect((wrapper.find('input[name="purchase-receipt-line-quantity-0"]').element as HTMLInputElement).value).toBe('2.500000')

    await wrapper.find('input[name="purchase-receipt-line-quantity-0"]').setValue('3.000000')
    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.receipts.update).toHaveBeenCalledWith(700, expect.objectContaining({
      warehouseId: 30,
      lines: [expect.objectContaining({ quantity: '3.000000' })],
    }))
    expect(router.currentRoute.value.name).toBe('procurement-receipt-detail')
  })

  it('编辑草稿时可追加同一来源订单的其他未入库行并保存', async () => {
    const { wrapper } = await mountForm('/procurement/receipts/700/edit')

    expect(procurementApiMock.receipts.get).toHaveBeenCalledWith('700')
    expect(procurementApiMock.orders.get).toHaveBeenCalledWith(99)
    expect(wrapper.findComponent(PurchaseReceiptLineEditor).props('sourceLines')).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: 501, materialCode: 'RM-001' }),
      expect.objectContaining({ id: 502, materialCode: 'RM-002' }),
    ]))

    await wrapper.find('[data-test="add-purchase-receipt-line"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 2, 502)
    await wrapper.find('input[name="purchase-receipt-line-quantity-1"]').setValue('1.000000')
    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('同一采购入库内来源订单行不能重复')
    expect(procurementApiMock.receipts.update).toHaveBeenCalledWith(700, expect.objectContaining({
      lines: [
        expect.objectContaining({ orderLineId: 501, quantity: '2.500000' }),
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

  it('编辑草稿时按当前来源订单剩余量校验已有行，避免使用旧快照超入库', async () => {
    procurementApiMock.orders.get.mockResolvedValueOnce({
      ...sourceOrder,
      receivedQuantity: '14.500000',
      remainingQuantity: '1.000000',
      lines: sourceOrder.lines.map((line) => (
        line.id === 501
          ? { ...line, receivedQuantity: '11.500000', remainingQuantity: '1.000000' }
          : line
      )),
    })
    const { wrapper } = await mountForm('/procurement/receipts/700/edit')

    expect(procurementApiMock.receipts.get).toHaveBeenCalledWith('700')
    expect(procurementApiMock.orders.get).toHaveBeenCalledWith(99)

    await wrapper.find('input[name="purchase-receipt-line-quantity-0"]').setValue('2')
    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('本次入库数量不能超过未入库数量')
    expect(procurementApiMock.receipts.update).not.toHaveBeenCalled()
  })

  it('已过账采购入库不可提交', async () => {
    procurementApiMock.receipts.get.mockResolvedValueOnce(postedReceipt)
    const { wrapper } = await mountForm('/procurement/receipts/700/edit')

    expect(wrapper.text()).toContain('已过账采购入库不可编辑')
    expect(wrapper.find('[data-test="save-purchase-receipt"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.receipts.update).not.toHaveBeenCalled()
  })

  it('来源订单状态不允许创建采购入库时禁止保存', async () => {
    procurementApiMock.orders.get.mockResolvedValueOnce({ ...sourceOrder, status: 'DRAFT' })
    const { wrapper } = await mountForm()

    expect(wrapper.text()).toContain('仅已确认或部分入库采购订单可创建采购入库')
    expect(wrapper.find('[data-test="save-purchase-receipt"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-test="save-purchase-receipt"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.receipts.create).not.toHaveBeenCalled()
  })
})
