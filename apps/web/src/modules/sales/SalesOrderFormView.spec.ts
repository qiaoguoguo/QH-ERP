import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { MaterialRecord, PartnerRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
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
  warehouses: {
    list: vi.fn(),
  },
}))

const salesProjectApiMock = vi.hoisted(() => ({
  listOrderLinkCandidates: vi.fn(),
}))

vi.mock('../../shared/api/salesApi', () => ({
  salesApi: salesApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../shared/api/salesProjectApi', () => ({
  salesProjectApi: salesProjectApiMock,
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
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
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

const warehouseA: WarehouseRecord = {
  id: 30,
  code: 'WH-FG',
  name: '成品仓',
  status: 'ENABLED',
}

const draftOrder: SalesOrderDetailRecord = {
  id: 99,
  orderNo: 'SO-20260704-001',
  customerId: 100,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  orderDate: '2026-07-04',
  expectedShipDate: '2026-07-12',
  version: 4,
  projectId: 12,
  projectNo: 'SP-202607-001',
  projectName: '华东扩产项目',
  contractId: 55,
  contractNo: 'SC-001',
  externalContractNo: 'EXT-001',
  contractName: '追加合同',
  contractType: 'SUPPLEMENT',
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
      priceSourceType: 'MANUAL',
      priceSourceNo: null,
      untaxedUnitPrice: '78.000000',
      taxIncludedUnitPrice: '88.100000',
      taxRate: '0.130000',
      taxIncludedAmount: '1101.250000',
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
  await setSelectValue(wrapper, 2, 30)
  await wrapper.find('input[name="sales-order-line-quantity-0"]').setValue(quantity)
  await wrapper.find('input[name="sales-order-line-unit-price-0"]').setValue(unitPrice)
  await wrapper.find('input[name="sales-order-line-untaxed-unit-price-0"]').setValue('78.000000')
  await wrapper.find('input[name="sales-order-line-tax-included-unit-price-0"]').setValue('88.100000')
  await wrapper.find('input[name="sales-order-line-tax-rate-0"]').setValue('0.130000')
  await wrapper.find('input[name="sales-order-line-expected-date-0"]').setValue('2026-07-12')
}

describe('销售订单表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    salesApiMock.orders.get.mockResolvedValue(draftOrder)
    salesApiMock.orders.create.mockResolvedValue(draftOrder)
    salesApiMock.orders.update.mockResolvedValue(draftOrder)
    salesProjectApiMock.listOrderLinkCandidates.mockResolvedValue({
      items: [{
        projectId: 12,
        projectNo: 'SP-202607-001',
        projectName: '华东扩产项目',
        customerId: 100,
        customerName: '华东客户',
        contractId: 55,
        contractNo: 'SC-001',
        externalContractNo: 'EXT-001',
        contractName: '主合同',
        contractType: 'MAIN',
      }],
      page: 1,
      pageSize: 50,
      total: 1,
      totalPages: 1,
    })
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
    masterDataApiMock.warehouses.list.mockResolvedValue({
      items: [warehouseA],
      page: 1,
      pageSize: 200,
      total: 1,
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
    expect(masterDataApiMock.warehouses.list).toHaveBeenCalledWith({
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

  it('校验明细数量和销售单价格式，并允许同物料多行按独立行提交', async () => {
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
    await setSelectValue(wrapper, 3, 10)
    await setSelectValue(wrapper, 4, 30)
    await wrapper.find('input[name="sales-order-line-quantity-1"]').setValue('2')
    await wrapper.find('input[name="sales-order-line-unit-price-1"]').setValue('1')
    await wrapper.find('input[name="sales-order-line-untaxed-unit-price-1"]').setValue('0.884956')
    await wrapper.find('input[name="sales-order-line-tax-included-unit-price-1"]').setValue('1.000000')
    await wrapper.find('input[name="sales-order-line-tax-rate-1"]').setValue('0.130000')
    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('同一销售订单内物料不能重复')
    expect(salesApiMock.orders.create).toHaveBeenCalledWith(expect.objectContaining({
      lines: [
        expect.objectContaining({ lineNo: 10, materialId: 10 }),
        expect.objectContaining({ lineNo: 20, materialId: 10 }),
      ],
    }))
  })

  it('销售明细必须选择预留仓库，确认预留口径使用清晰中文提示', async () => {
    const { wrapper } = await mountForm()

    await setSelectValue(wrapper, 0, 100)
    await wrapper.find('input[name="sales-order-date"]').setValue('2026-07-04')
    await setSelectValue(wrapper, 1, 10)
    await wrapper.find('input[name="sales-order-line-quantity-0"]').setValue('2')
    await wrapper.find('input[name="sales-order-line-unit-price-0"]').setValue('88.1')
    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('第 10 行请选择预留仓库，销售订单确认会按该仓库预留现货库存')
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
          reservationWarehouseId: 30,
          quantity: '12.500000',
          unitPrice: '88.100000',
          priceSourceType: 'MANUAL',
          untaxedUnitPrice: '78.000000',
          taxIncludedUnitPrice: '88.100000',
          taxRate: '0.130000',
          expectedShipDate: '2026-07-12',
          remark: '按周发货',
        },
      ],
    })
    expect(router.currentRoute.value.name).toBe('sales-order-detail')
    expect(router.currentRoute.value.params.id).toBe('99')
  })

  it('草稿销售订单选择项目合同时同选同清，并随保存 payload 提交成对字段', async () => {
    const { wrapper } = await mountForm()

    await fillValidOrder(wrapper)
    await setSelectValue(wrapper, 3, '12:55')
    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.listOrderLinkCandidates).toHaveBeenCalledWith(expect.objectContaining({
      customerId: 100,
      page: 1,
      pageSize: 20,
    }))
    expect(salesApiMock.orders.create).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 12,
      contractId: 55,
    }))
  })

  it('新建态可见并提交手工订单价格来源和税价字符串', async () => {
    const { wrapper } = await mountForm()

    await fillValidOrder(wrapper)
    expect(wrapper.text()).toContain('价格来源')
    expect(wrapper.text()).toContain('手工订单')
    expect(wrapper.text()).toContain('未税单价')
    expect(wrapper.text()).toContain('含税单价')
    expect(wrapper.text()).toContain('税率')

    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()

    expect(salesApiMock.orders.create).toHaveBeenCalledWith(expect.objectContaining({
      lines: [expect.objectContaining({
        priceSourceType: 'MANUAL',
        untaxedUnitPrice: '78.000000',
        taxIncludedUnitPrice: '88.100000',
        taxRate: '0.130000',
      })],
    }))
  })

  it('候选加载失败时保留已有项目合同显示并禁用保存，避免误解除关联', async () => {
    salesProjectApiMock.listOrderLinkCandidates.mockRejectedValue(new Error('项目合同候选加载失败'))
    const { wrapper } = await mountForm('/sales/orders/99/edit')

    expect(wrapper.text()).toContain('项目合同候选加载失败')
    expect(wrapper.text()).toContain('SP-202607-001 华东扩产项目 / SC-001 追加合同 补充合同')
    expect(wrapper.find('[data-test="save-sales-order"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.update).not.toHaveBeenCalled()
  })

  it('既有关联缺少合同类型时显示数据错误并禁用保存，不能默认为主合同', async () => {
    salesProjectApiMock.listOrderLinkCandidates.mockRejectedValue(new Error('项目合同候选加载失败'))
    salesApiMock.orders.get.mockResolvedValueOnce({
      ...draftOrder,
      contractType: null,
    })
    const { wrapper } = await mountForm('/sales/orders/99/edit')

    expect(wrapper.text()).toContain('项目合同关联数据缺少合同类型，请联系管理员修复')
    expect(wrapper.text()).not.toContain('追加合同 主合同')
    expect(wrapper.find('[data-test="save-sales-order"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.update).not.toHaveBeenCalled()
  })

  it('既有关联使用后端返回的补充合同类型，不回退为主合同', async () => {
    salesProjectApiMock.listOrderLinkCandidates.mockRejectedValue(new Error('项目合同候选加载失败'))
    const { wrapper } = await mountForm('/sales/orders/99/edit')

    expect(wrapper.text()).toContain('SP-202607-001 华东扩产项目 / SC-001 追加合同 补充合同')
    expect(wrapper.text()).not.toContain('追加合同 主合同')
  })

  it('项目合同候选按客户和远程关键词分页加载，并展示主补合同类型', async () => {
    salesProjectApiMock.listOrderLinkCandidates.mockResolvedValue({
      items: [{
        projectId: 12,
        projectNo: 'SP-202607-001',
        projectName: '华东扩产项目',
        customerId: 100,
        customerName: '华东客户',
        contractId: 56,
        contractNo: 'SC-002',
        externalContractNo: 'EXT-002',
        contractName: '追加合同',
        contractType: 'SUPPLEMENT',
      }],
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1,
    })
    const { wrapper } = await mountForm()

    await setSelectValue(wrapper, 0, 100)
    const projectContractSelect = wrapper.findAllComponents({ name: 'ElSelect' })[3] as VueWrapper
    const remoteMethod = (projectContractSelect.props() as Record<string, unknown>).remoteMethod as
      ((keyword: string) => Promise<void> | void)
    await remoteMethod('追加')
    await flushPromises()

    expect(salesProjectApiMock.listOrderLinkCandidates).toHaveBeenLastCalledWith({
      customerId: 100,
      keyword: '追加',
      page: 1,
      pageSize: 20,
    })
    const optionLabels = wrapper.findAllComponents({ name: 'ElOption' }).map((option) => String(option.props('label')))
    expect(optionLabels.some((label) => label.includes('SC-002 追加合同 补充合同'))).toBe(true)
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
      version: 4,
      customerId: 100,
      lines: [expect.objectContaining({ reservationWarehouseId: 30, unitPrice: '99.200000' })],
    }))
    expect(router.currentRoute.value.name).toBe('sales-order-detail')
  })

  it('编辑草稿时保留报价行、合同行和价格来源字段，保存不丢失来源链', async () => {
    salesApiMock.orders.get.mockResolvedValueOnce({
      ...draftOrder,
      lines: [
        {
          ...draftOrder.lines[0],
          id: 501,
          lineNo: 10,
          materialId: 10,
          priceSourceType: 'QUOTE',
          priceSourceNo: 'SQ-001',
          quoteLineId: 901,
          contractLineId: null,
        },
        {
          ...draftOrder.lines[0],
          id: 502,
          lineNo: 20,
          materialId: 10,
          priceSourceType: 'QUOTE',
          priceSourceNo: 'SQ-002',
          quoteLineId: 902,
          contractLineId: 702,
        },
      ],
    })
    const { wrapper } = await mountForm('/sales/orders/99/edit')

    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()

    expect(salesApiMock.orders.update).toHaveBeenCalledWith(99, expect.objectContaining({
      lines: [
        expect.objectContaining({
          lineNo: 10,
          materialId: 10,
          priceSourceType: 'QUOTE',
          quoteLineId: 901,
          contractLineId: null,
        }),
        expect.objectContaining({
          lineNo: 20,
          materialId: 10,
          priceSourceType: 'QUOTE',
          quoteLineId: 902,
          contractLineId: 702,
        }),
      ],
    }))
  })

  it('非草稿销售订单不可提交', async () => {
    salesApiMock.orders.get.mockResolvedValueOnce(confirmedOrder)
    const { wrapper } = await mountForm('/sales/orders/99/edit')

    expect(wrapper.text()).toContain('仅草稿销售订单可编辑')
    expect(wrapper.text()).toContain('SP-202607-001')
    expect(wrapper.text()).toContain('SC-001')
    expect(wrapper.find('[data-test="save-sales-order"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-test="save-sales-order"]').trigger('click')
    await flushPromises()
    expect(salesApiMock.orders.update).not.toHaveBeenCalled()
  })
})
