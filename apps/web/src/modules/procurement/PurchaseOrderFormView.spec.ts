import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { MaterialRecord, PartnerRecord } from '../../shared/api/masterDataApi'
import type { PurchaseOrderDetailRecord } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import PurchaseOrderFormView from './PurchaseOrderFormView.vue'

const procurementApiMock = vi.hoisted(() => ({
  orders: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  suppliers: {
    list: vi.fn(),
  },
  materials: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const supplierA: PartnerRecord = {
  id: 100,
  code: 'SUP-A',
  name: '华东五金',
  status: 'ENABLED',
}

const materialA: MaterialRecord = {
  id: 10,
  code: 'RM-001',
  name: '冷轧钢板',
  specification: '1.5mm',
  materialType: 'RAW_MATERIAL',
  sourceType: 'PURCHASED',
  categoryId: 1,
  unitId: 2,
  unitName: '千克',
  status: 'ENABLED',
}

const materialB: MaterialRecord = {
  ...materialA,
  id: 11,
  code: 'RM-002',
  name: '紧固件',
  unitId: 3,
  unitName: '件',
}

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
  receipts: [],
}

const confirmedOrder: PurchaseOrderDetailRecord = {
  ...draftOrder,
  status: 'CONFIRMED',
}

async function setSelectValue(wrapper: VueWrapper, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index] as VueWrapper | undefined
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountForm(path = '/procurement/orders/create') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: [
      'procurement:order:view',
      'procurement:order:create',
      'procurement:order:update',
    ],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/procurement/orders', name: 'procurement-orders', component: { render: () => null } },
      { path: '/procurement/orders/create', name: 'procurement-order-create', component: PurchaseOrderFormView },
      { path: '/procurement/orders/:id', name: 'procurement-order-detail', component: { render: () => null } },
      { path: '/procurement/orders/:id/edit', name: 'procurement-order-edit', component: PurchaseOrderFormView },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(PurchaseOrderFormView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function fillValidOrder(wrapper: VueWrapper, quantity = '12.500000', unitPrice = '3.100000') {
  await setSelectValue(wrapper, 0, 100)
  await wrapper.find('input[name="purchase-order-date"]').setValue('2026-07-04')
  await wrapper.find('input[name="purchase-order-expected-date"]').setValue('2026-07-12')
  await setSelectValue(wrapper, 1, 10)
  await wrapper.find('input[name="purchase-order-line-quantity-0"]').setValue(quantity)
  await wrapper.find('input[name="purchase-order-line-unit-price-0"]').setValue(unitPrice)
  await wrapper.find('input[name="purchase-order-line-expected-date-0"]').setValue('2026-07-12')
}

describe('采购订单表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.orders.get.mockResolvedValue(draftOrder)
    procurementApiMock.orders.create.mockResolvedValue(draftOrder)
    procurementApiMock.orders.update.mockResolvedValue(draftOrder)
    masterDataApiMock.suppliers.list.mockResolvedValue({
      items: [supplierA],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
    masterDataApiMock.materials.list.mockResolvedValue({
      items: [materialA, materialB],
      page: 1,
      pageSize: 200,
      total: 2,
      totalPages: 1,
    })
  })

  it('加载供应商和可采购物料，缺少必填项时阻止保存', async () => {
    const { wrapper } = await mountForm()

    expect(masterDataApiMock.suppliers.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    expect(masterDataApiMock.materials.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      sourceType: 'PURCHASED',
      page: 1,
      pageSize: 200,
    })

    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请完整填写供应商、订单日期和明细')
    expect(procurementApiMock.orders.create).not.toHaveBeenCalled()
  })

  it('校验明细数量、单价格式和重复物料', async () => {
    const { wrapper } = await mountForm()

    await fillValidOrder(wrapper, '1.1234567')
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('数量最多 6 位小数')

    await wrapper.find('input[name="purchase-order-line-quantity-0"]').setValue('1')
    await wrapper.find('input[name="purchase-order-line-unit-price-0"]').setValue('-1')
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('单价仅支持普通十进制非负数')

    await wrapper.find('input[name="purchase-order-line-unit-price-0"]').setValue('3.1')
    await wrapper.find('[data-test="add-purchase-order-line"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 2, 10)
    await wrapper.find('input[name="purchase-order-line-quantity-1"]').setValue('2')
    await wrapper.find('input[name="purchase-order-line-unit-price-1"]').setValue('1')
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('同一采购订单内物料不能重复')
    expect(procurementApiMock.orders.create).not.toHaveBeenCalled()
  })

  it('新增和删除明细后保存创建 payload，数量和单价保持字符串', async () => {
    const { wrapper, router } = await mountForm()

    await fillValidOrder(wrapper)
    await wrapper.find('[data-test="add-purchase-order-line"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('20')

    await wrapper.findAll('[data-test="remove-purchase-order-line"]')[1].trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('20')

    await wrapper.find('input[name="purchase-order-remark"]').setValue('首批采购')
    await wrapper.find('input[name="purchase-order-line-remark-0"]').setValue('按周到货')
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.orders.create).toHaveBeenCalledWith({
      supplierId: 100,
      orderDate: '2026-07-04',
      expectedArrivalDate: '2026-07-12',
      remark: '首批采购',
      lines: [
        {
          lineNo: 10,
          materialId: 10,
          unitId: 2,
          quantity: '12.500000',
          unitPrice: '3.100000',
          expectedArrivalDate: '2026-07-12',
          remark: '按周到货',
        },
      ],
    })
    expect(router.currentRoute.value.name).toBe('procurement-order-detail')
    expect(router.currentRoute.value.params.id).toBe('99')
  })

  it('编辑草稿时回填明细并提交更新', async () => {
    const { wrapper, router } = await mountForm('/procurement/orders/99/edit')

    expect(procurementApiMock.orders.get).toHaveBeenCalledWith('99')
    expect((wrapper.find('input[name="purchase-order-date"]').element as HTMLInputElement).value).toBe('2026-07-04')
    expect((wrapper.find('input[name="purchase-order-line-quantity-0"]').element as HTMLInputElement).value).toBe('12.5')
    expect(wrapper.text()).toContain('PO-20260704-001')

    await wrapper.find('input[name="purchase-order-line-unit-price-0"]').setValue('4.200000')
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.orders.update).toHaveBeenCalledWith(99, expect.objectContaining({
      supplierId: 100,
      lines: [expect.objectContaining({ unitPrice: '4.200000' })],
    }))
    expect(router.currentRoute.value.name).toBe('procurement-order-detail')
  })

  it('非草稿采购订单不可提交', async () => {
    procurementApiMock.orders.get.mockResolvedValueOnce(confirmedOrder)
    const { wrapper } = await mountForm('/procurement/orders/99/edit')

    expect(wrapper.text()).toContain('仅草稿采购订单可编辑')
    expect(wrapper.find('[data-test="save-purchase-order"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.orders.update).not.toHaveBeenCalled()
  })

  it('编辑加载失败后禁止误创建新采购订单', async () => {
    procurementApiMock.orders.get.mockRejectedValueOnce(new Error('采购订单不存在'))
    const { wrapper } = await mountForm('/procurement/orders/99/edit')

    expect(wrapper.text()).toContain('采购订单不存在')

    await fillValidOrder(wrapper)
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.orders.create).not.toHaveBeenCalled()
    expect(procurementApiMock.orders.update).not.toHaveBeenCalled()
  })
})
