import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { MaterialRecord, PartnerRecord } from '../../shared/api/masterDataApi'
import type { SalesOrderDetailRecord } from '../../shared/api/salesApi'
import { useAuthStore } from '../../stores/authStore'
import SalesOrderFormView from './SalesOrderFormView.vue'
import SalesOrderLineEditor from './SalesOrderLineEditor.vue'

const salesApiMock = vi.hoisted(() => ({
  orders: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  customers: {
    list: vi.fn(),
  },
  materials: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/salesApi', () => ({
  salesApi: salesApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const customerA: PartnerRecord = {
  id: 100,
  code: 'CUS-A',
  name: '华东客户',
  status: 'ENABLED',
}

const finishedGood: MaterialRecord = {
  id: 10,
  code: 'FG-001',
  name: '标准成品',
  specification: 'A1',
  materialType: 'FINISHED_GOOD',
  sourceType: 'SELF_MADE',
  categoryId: 1,
  unitId: 2,
  unitName: '件',
  status: 'ENABLED',
}

const semiFinished: MaterialRecord = {
  ...finishedGood,
  id: 11,
  code: 'SF-001',
  name: '半成品组件',
  materialType: 'SEMI_FINISHED',
  unitId: 3,
  unitName: '套',
}

const rawMaterial: MaterialRecord = {
  ...finishedGood,
  id: 12,
  code: 'RM-001',
  name: '冷轧钢板',
  materialType: 'RAW_MATERIAL',
}

const auxiliary: MaterialRecord = {
  ...finishedGood,
  id: 13,
  code: 'AX-001',
  name: '包装辅料',
  materialType: 'AUXILIARY',
}

const draftOrder: SalesOrderDetailRecord = {
  id: 99,
  orderNo: 'SO-20260704-001',
  customerId: 100,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  orderDate: '2026-07-04',
  expectedShipDate: '2026-07-12',
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
      unitPrice: '88.100000',
      expectedShipDate: '2026-07-12',
      remark: '按周发货',
    },
  ],
  shipments: [],
}

const confirmedOrder: SalesOrderDetailRecord = {
  ...draftOrder,
  status: 'CONFIRMED',
}

async function setSelectValue(wrapper: VueWrapper, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index] as VueWrapper | undefined
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountForm(path = '/sales/orders/create') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: [
      'sales:order:view',
      'sales:order:create',
      'sales:order:update',
    ],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sales/orders', name: 'sales-orders', component: { render: () => null } },
      { path: '/sales/orders/create', name: 'sales-order-create', component: SalesOrderFormView },
      { path: '/sales/orders/:id', name: 'sales-order-detail', component: { render: () => null } },
      { path: '/sales/orders/:id/edit', name: 'sales-order-edit', component: SalesOrderFormView },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(SalesOrderFormView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function fillValidOrder(wrapper: VueWrapper, quantity = '12.500000', unitPrice = '88.100000') {
  await setSelectValue(wrapper, 0, 100)
  await wrapper.find('input[name="sales-order-date"]').setValue('2026-07-04')
  await wrapper.find('input[name="sales-order-expected-date"]').setValue('2026-07-12')
  await setSelectValue(wrapper, 1, 10)
  await wrapper.find('input[name="sales-order-line-quantity-0"]').setValue(quantity)
  await wrapper.find('input[name="sales-order-line-unit-price-0"]').setValue(unitPrice)
  await wrapper.find('input[name="sales-order-line-expected-date-0"]').setValue('2026-07-12')
}

describe('销售订单表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    salesApiMock.orders.get.mockResolvedValue(draftOrder)
    salesApiMock.orders.create.mockResolvedValue(draftOrder)
    salesApiMock.orders.update.mockResolvedValue(draftOrder)
    masterDataApiMock.customers.list.mockResolvedValue({
      items: [customerA],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
    masterDataApiMock.materials.list.mockResolvedValue({
      items: [finishedGood, semiFinished, rawMaterial, auxiliary],
      page: 1,
      pageSize: 200,
      total: 4,
      totalPages: 1,
    })
  })

  it('加载客户和可销售物料，过滤原材料和辅料，缺少必填项时阻止保存', async () => {
    const { wrapper } = await mountForm()

    expect(masterDataApiMock.customers.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    expect(masterDataApiMock.materials.list).toHaveBeenCalledWith({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    const lineEditor = wrapper.findComponent(SalesOrderLineEditor)
    const materialNames = (lineEditor.props('materials') as MaterialRecord[]).map((item) => item.name)
    expect(materialNames).toEqual(['标准成品', '半成品组件'])
    expect(materialNames).not.toContain('冷轧钢板')
    expect(materialNames).not.toContain('包装辅料')

    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请完整填写客户、订单日期和明细')
    expect(salesApiMock.orders.create).not.toHaveBeenCalled()
  })

  it('校验明细数量、销售单价格式和重复物料', async () => {
    const { wrapper } = await mountForm()

    await fillValidOrder(wrapper, '1.1234567')
    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('数量最多 6 位小数')

    await wrapper.find('input[name="sales-order-line-quantity-0"]').setValue('1')
    await wrapper.find('input[name="sales-order-line-unit-price-0"]').setValue('-1')
    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('单价仅支持普通十进制非负数')

    await wrapper.find('input[name="sales-order-line-unit-price-0"]').setValue('88.1')
    await wrapper.find('[data-test="add-sales-order-line"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 2, 10)
    await wrapper.find('input[name="sales-order-line-quantity-1"]').setValue('2')
    await wrapper.find('input[name="sales-order-line-unit-price-1"]').setValue('1')
    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('同一销售订单内物料不能重复')
    expect(salesApiMock.orders.create).not.toHaveBeenCalled()
  })

  it('新增和删除明细后保存创建 payload，数量和销售单价保持字符串', async () => {
    const { wrapper, router } = await mountForm()

    await fillValidOrder(wrapper)
    await wrapper.find('[data-test="add-sales-order-line"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('20')

    await wrapper.findAll('[data-test="remove-sales-order-line"]')[1].trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('20')

    await wrapper.find('input[name="sales-order-remark"]').setValue('首批销售')
    await wrapper.find('input[name="sales-order-line-remark-0"]').setValue('按周发货')
    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()

    expect(salesApiMock.orders.create).toHaveBeenCalledWith({
      customerId: 100,
      orderDate: '2026-07-04',
      expectedShipDate: '2026-07-12',
      remark: '首批销售',
      lines: [
        {
          lineNo: 10,
          materialId: 10,
          unitId: 2,
          quantity: '12.500000',
          unitPrice: '88.100000',
          expectedShipDate: '2026-07-12',
          remark: '按周发货',
        },
      ],
    })
    expect(router.currentRoute.value.name).toBe('sales-order-detail')
    expect(router.currentRoute.value.params.id).toBe('99')
  })

  it('编辑草稿时回填明细并提交更新', async () => {
    const { wrapper, router } = await mountForm('/sales/orders/99/edit')

    expect(salesApiMock.orders.get).toHaveBeenCalledWith('99')
    expect((wrapper.find('input[name="sales-order-date"]').element as HTMLInputElement).value).toBe('2026-07-04')
    expect((wrapper.find('input[name="sales-order-line-quantity-0"]').element as HTMLInputElement).value).toBe('12.500000')
    expect(wrapper.text()).toContain('SO-20260704-001')

    await wrapper.find('input[name="sales-order-line-unit-price-0"]').setValue('99.200000')
    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()

    expect(salesApiMock.orders.update).toHaveBeenCalledWith(99, expect.objectContaining({
      customerId: 100,
      lines: [expect.objectContaining({ unitPrice: '99.200000' })],
    }))
    expect(router.currentRoute.value.name).toBe('sales-order-detail')
  })

  it('非草稿销售订单不可提交', async () => {
    salesApiMock.orders.get.mockResolvedValueOnce(confirmedOrder)
    const { wrapper } = await mountForm('/sales/orders/99/edit')

    expect(wrapper.text()).toContain('仅草稿销售订单可编辑')
    expect(wrapper.find('[data-test="save-sales-order"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.update).not.toHaveBeenCalled()
  })
})
