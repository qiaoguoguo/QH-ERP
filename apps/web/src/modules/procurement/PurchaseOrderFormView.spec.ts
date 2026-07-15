import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { MaterialRecord, PartnerRecord } from '../../shared/api/masterDataApi'
import type {
  PriceAgreementSummaryRecord,
  ProcurementInquirySummaryRecord,
  ProcurementRequisitionDetailRecord,
  PurchaseOrderDetailRecord,
  SupplierQuoteRecord,
} from '../../shared/api/procurementApi'
import type { SalesProjectSummary } from '../../shared/api/salesProjectApi'
import { useAuthStore } from '../../stores/authStore'
import PurchaseOrderFormView from './PurchaseOrderFormView.vue'

const procurementApiMock = vi.hoisted(() => ({
  requisitions: {
    get: vi.fn(),
  },
  inquiries: {
    list: vi.fn(),
  },
  quotes: {
    list: vi.fn(),
  },
  priceAgreements: {
    list: vi.fn(),
  },
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

const salesProjectApiMock = vi.hoisted(() => ({
  projects: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../shared/api/salesProjectApi', () => ({
  salesProjectApi: salesProjectApiMock,
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
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
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

const projectA: SalesProjectSummary = {
  id: 30,
  projectNo: 'PRJ-024',
  name: '华东产线改造',
  customerId: 1000,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  ownerUserId: 1,
  ownerUsername: 'buyer',
  ownerDisplayName: '采购员',
  status: 'ACTIVE',
  targetRevenue: '0.000000',
  targetCost: '0.000000',
  contractSummaryRestricted: false,
  salesOrderSummaryRestricted: false,
  createdByName: '采购员',
  createdAt: '2026-07-01T09:00:00+08:00',
  updatedAt: '2026-07-15T09:00:00+08:00',
  version: 2,
}

const approvedRequisition: ProcurementRequisitionDetailRecord = {
  id: 1,
  requisitionNo: 'REQ-024-001',
  title: '项目专采钢板',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  requiredDate: '2026-07-25',
  status: 'APPROVED',
  approvalStatus: 'APPROVED',
  approvalStatusName: '审批通过',
  materialSummary: 'RM-001 冷轧钢板',
  lineCount: 1,
  totalQuantity: '12.500000',
  orderedQuantity: '0.000000',
  remainingQuantity: '12.500000',
  allowedActions: ['CREATE_ORDER'],
  createdByName: '采购员',
  createdAt: '2026-07-15T09:00:00+08:00',
  updatedAt: '2026-07-15T10:00:00+08:00',
  version: 3,
  lines: [{
    id: 901,
    lineNo: 10,
    procurementMode: 'PROJECT',
    projectId: 30,
    projectCode: 'PRJ-024',
    projectName: '华东产线改造',
    materialId: 10,
    materialCode: 'RM-001',
    materialName: '冷轧钢板',
    unitId: 2,
    unitName: '千克',
    quantity: '12.500000',
    orderedQuantity: '0.000000',
    remainingQuantity: '12.500000',
    requiredDate: '2026-07-25',
    suggestedSupplierId: 100,
    suggestedSupplierName: '华东五金',
    taxRate: '0.130000',
    purpose: '项目专采生产配套',
  }],
}

const inquiryA: ProcurementInquirySummaryRecord = {
  id: 201,
  inquiryNo: 'INQ-024-001',
  title: '项目钢板询价',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  status: 'COMPLETED',
  supplierCount: 1,
  quoteCount: 1,
  materialSummary: 'RM-001 冷轧钢板',
  allowedActions: [],
  createdByName: '采购员',
  createdAt: '2026-07-15T09:00:00+08:00',
  updatedAt: '2026-07-15T10:00:00+08:00',
  version: 4,
}

const quoteLineA: SupplierQuoteRecord = {
  id: 301,
  inquiryId: 201,
  inquiryNo: 'INQ-024-001',
  quoteNo: 'SQ-024-001',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  supplierId: 100,
  supplierCode: 'SUP-A',
  supplierName: '华东五金',
  materialId: 10,
  materialCode: 'RM-001',
  materialName: '冷轧钢板',
  quantity: '12.500000',
  taxRate: '0.130000',
  taxIncludedUnitPrice: '11.300000',
  taxExcludedUnitPrice: '10.000000',
  taxIncludedAmount: '141.250000',
  taxExcludedAmount: '125.000000',
  currency: 'CNY',
  status: 'SELECTED',
  lowestEffectiveQuote: true,
  priceSourceTypeName: '最低有效报价',
  allowedActions: [],
  version: 6,
}

const agreementLineA: PriceAgreementSummaryRecord = {
  id: 401,
  agreementNo: 'PA-024-001',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  supplierId: 100,
  supplierCode: 'SUP-A',
  supplierName: '华东五金',
  materialId: 10,
  materialCode: 'RM-001',
  materialName: '冷轧钢板',
  taxRate: '0.130000',
  taxIncludedUnitPrice: '11.300000',
  taxExcludedUnitPrice: '10.000000',
  currency: 'CNY',
  validFrom: '2026-07-01',
  validTo: '2026-12-31',
  status: 'ACTIVE',
  statusName: '已生效',
  priceSourceTypeName: '价格协议',
  allowedActions: [],
  createdByName: '采购员',
  createdAt: '2026-07-01T09:00:00+08:00',
  updatedAt: '2026-07-15T10:00:00+08:00',
  version: 8,
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
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  requisitionNo: 'REQ-024-001',
  inquiryNo: 'INQ-024-001',
  quoteNo: 'SQ-024-001',
  agreementNo: 'PA-024-001',
  approvalStatusName: '待提交',
  exceptionApprovalStatus: 'NOT_REQUIRED',
  priceSourceTypeName: '供应商报价',
  priceSourceNo: 'SQ-024-001',
  priceSourceReason: '最低有效报价',
  taxExcludedUnitPrice: '10.000000',
  taxIncludedUnitPrice: '11.300000',
  taxRate: '0.130000',
  lineCount: 1,
  totalQuantity: '12.500000',
  receivedQuantity: '0.000000',
  remainingQuantity: '12.500000',
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
      receivedQuantity: '0.000000',
      remainingQuantity: '12.500000',
      currency: 'CNY',
      unitPrice: '3.100000',
      requisitionLineId: 901,
      requisitionNo: 'REQ-024-001',
      quoteLineId: 301,
      quoteNo: 'SQ-024-001',
      priceAgreementLineId: 401,
      agreementNo: 'PA-024-001',
      procurementMode: 'PROJECT',
      projectId: 30,
      projectCode: 'PRJ-024',
      projectName: '华东产线改造',
      priceSourceTypeName: '供应商报价',
      priceSourceNo: 'SQ-024-001',
      taxExcludedUnitPrice: '10.000000',
      taxIncludedUnitPrice: '11.300000',
      taxRate: '0.130000',
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

async function setSelectValueByTest(wrapper: VueWrapper, testId: string, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' }).find((component) => component.attributes('data-test') === testId)
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
  await setSelectValueByTest(wrapper, 'purchase-order-supplier-id', 100)
  await wrapper.find('input[name="purchase-order-date"]').setValue('2026-07-04')
  await wrapper.find('input[name="purchase-order-expected-date"]').setValue('2026-07-12')
  await setSelectValueByTest(wrapper, 'purchase-order-line-material-id-0', 10)
  await wrapper.find('input[name="purchase-order-line-quantity-0"]').setValue(quantity)
  await wrapper.find('input[name="purchase-order-line-unit-price-0"]').setValue(unitPrice)
  await wrapper.find('input[name="purchase-order-line-expected-date-0"]').setValue('2026-07-12')
}

describe('采购订单表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.requisitions.get.mockResolvedValue(approvedRequisition)
    procurementApiMock.inquiries.list.mockResolvedValue({
      items: [inquiryA],
      page: 1,
      pageSize: 50,
      total: 1,
      totalPages: 1,
    })
    procurementApiMock.quotes.list.mockResolvedValue({
      items: [quoteLineA],
      page: 1,
      pageSize: 50,
      total: 1,
      totalPages: 1,
    })
    procurementApiMock.priceAgreements.list.mockResolvedValue({
      items: [agreementLineA],
      page: 1,
      pageSize: 50,
      total: 1,
      totalPages: 1,
    })
    procurementApiMock.orders.get.mockResolvedValue(draftOrder)
    procurementApiMock.orders.create.mockResolvedValue(draftOrder)
    procurementApiMock.orders.update.mockResolvedValue(draftOrder)
    salesProjectApiMock.projects.list.mockResolvedValue({
      items: [projectA],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
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
    await setSelectValueByTest(wrapper, 'purchase-order-line-material-id-1', 10)
    await wrapper.find('input[name="purchase-order-line-quantity-1"]').setValue('2')
    await wrapper.find('input[name="purchase-order-line-unit-price-1"]').setValue('1')
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('同一采购订单内来源组合不能重复')
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
      procurementMode: 'PUBLIC',
      projectId: null,
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

  it('从请购创建订单时加载已审批请购行并预填真实来源行、模式项目和税价', async () => {
    const { wrapper } = await mountForm('/procurement/orders/create?requisitionId=1')

    expect(procurementApiMock.requisitions.get).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).toContain('请购来源：REQ-024-001 / 行 10 / RM-001 冷轧钢板')
    expect(wrapper.text()).toContain('根据价格来源与协议规则自动判断，确认后发起例外审批')
    expect((wrapper.find('input[name="purchase-order-line-quantity-0"]').element as HTMLInputElement).value).toBe('12.500000')
    expect((wrapper.find('input[name="purchase-order-line-tax-rate-0"]').element as HTMLInputElement).value).toBe('0.130000')

    await wrapper.find('input[name="purchase-order-date"]').setValue('2026-07-16')
    await wrapper.find('input[name="purchase-order-line-unit-price-0"]').setValue('10.000000')
    await wrapper.find('input[name="purchase-order-line-tax-excluded-unit-price-0"]').setValue('10.000000')
    await wrapper.find('input[name="purchase-order-line-tax-included-unit-price-0"]').setValue('11.300000')
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.orders.create).toHaveBeenCalledWith(expect.objectContaining({
      procurementMode: 'PROJECT',
      projectId: 30,
      supplierId: 100,
      lines: [expect.objectContaining({
        materialId: 10,
        requisitionLineId: 901,
        taxRate: '0.130000',
      })],
    }))
    expect(procurementApiMock.orders.create.mock.calls[0][0].lines[0]).not.toHaveProperty('quoteId')
    expect(procurementApiMock.orders.create.mock.calls[0][0].lines[0]).not.toHaveProperty('agreementId')
  })

  it('已有项目上下文时选择缺少项目字段的报价和协议来源不会清空项目', async () => {
    procurementApiMock.quotes.list.mockResolvedValueOnce({
      items: [{ ...quoteLineA, projectId: null, projectCode: null, projectName: null }],
      page: 1,
      pageSize: 50,
      total: 1,
      totalPages: 1,
    })
    procurementApiMock.priceAgreements.list.mockResolvedValueOnce({
      items: [{ ...agreementLineA, projectId: null, projectCode: null, projectName: null }],
      page: 1,
      pageSize: 50,
      total: 1,
      totalPages: 1,
    })
    const { wrapper } = await mountForm('/procurement/orders/create?requisitionId=1')

    await setSelectValueByTest(wrapper, 'purchase-order-line-quote-line-id-0', 301)
    await setSelectValueByTest(wrapper, 'purchase-order-line-price-agreement-line-id-0', 401)
    await flushPromises()

    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).not.toContain('项目未返回')

    await wrapper.find('input[name="purchase-order-date"]').setValue('2026-07-16')
    await wrapper.find('input[name="purchase-order-line-unit-price-0"]').setValue('10.000000')
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.orders.create).toHaveBeenCalledWith(expect.objectContaining({
      procurementMode: 'PROJECT',
      projectId: 30,
      lines: [expect.objectContaining({
        quoteLineId: 301,
        priceAgreementLineId: 401,
      })],
    }))
  })

  it('候选明确提供项目时来源选择会更新采购模式和项目上下文', async () => {
    const { wrapper } = await mountForm()

    await setSelectValueByTest(wrapper, 'purchase-order-line-price-agreement-line-id-0', 401)
    await flushPromises()

    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).not.toContain('公共采购')
  })

  it('新建订单可选择报价行和协议行来源并提交 canonical 行级来源 ID', async () => {
    const { wrapper } = await mountForm()

    expect(procurementApiMock.inquiries.list).toHaveBeenCalled()
    expect(procurementApiMock.quotes.list).toHaveBeenCalledWith(201, expect.objectContaining({ page: 1, pageSize: 50 }))
    expect(procurementApiMock.priceAgreements.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 50 }))
    expect(wrapper.text()).toContain('报价来源')
    expect(wrapper.text()).toContain('SQ-024-001 / 华东五金 / RM-001 冷轧钢板')
    expect(wrapper.text()).toContain('协议来源')
    expect(wrapper.text()).toContain('PA-024-001 / 华东五金 / RM-001 冷轧钢板')

    await fillValidOrder(wrapper)
    await setSelectValueByTest(wrapper, 'purchase-order-line-quote-line-id-0', 301)
    await setSelectValueByTest(wrapper, 'purchase-order-line-price-agreement-line-id-0', 401)
    await wrapper.find('input[name="purchase-order-line-tax-rate-0"]').setValue('0.130000')
    await wrapper.find('input[name="purchase-order-line-tax-excluded-unit-price-0"]').setValue('10.000000')
    await wrapper.find('input[name="purchase-order-line-tax-included-unit-price-0"]').setValue('11.300000')
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.orders.create).toHaveBeenCalledWith(expect.objectContaining({
      lines: [expect.objectContaining({
        quoteLineId: 301,
        priceAgreementLineId: 401,
        taxRate: '0.130000',
        taxExcludedUnitPrice: '10.000000',
        taxIncludedUnitPrice: '11.300000',
      })],
    }))
    expect(procurementApiMock.orders.create.mock.calls[0][0].lines[0]).not.toHaveProperty('quoteId')
    expect(procurementApiMock.orders.create.mock.calls[0][0].lines[0]).not.toHaveProperty('agreementId')
  })

  it('编辑草稿时回填明细并提交更新', async () => {
    const { wrapper, router } = await mountForm('/procurement/orders/99/edit')

    expect(procurementApiMock.orders.get).toHaveBeenCalledWith('99')
    expect((wrapper.find('input[name="purchase-order-date"]').element as HTMLInputElement).value).toBe('2026-07-04')
    expect((wrapper.find('input[name="purchase-order-line-quantity-0"]').element as HTMLInputElement).value).toBe('12.500000')
    expect(wrapper.text()).toContain('PO-20260704-001')
    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).toContain('请购 REQ-024-001')
    expect(wrapper.text()).toContain('报价 SQ-024-001')
    expect(wrapper.text()).toContain('协议 PA-024-001')
    expect(wrapper.text()).toContain('审批状态：待提交')
    expect(wrapper.text()).toContain('例外审批：不需要')
    expect(wrapper.text()).toContain('未税单价 10')
    expect(wrapper.text()).toContain('含税单价 11.3')
    expect(wrapper.text()).toContain('税率 0.13')
    expect(wrapper.text()).toContain('CNY')

    await wrapper.find('input[name="purchase-order-line-unit-price-0"]').setValue('4.200000')
    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.orders.update).toHaveBeenCalledWith(99, expect.objectContaining({
      supplierId: 100,
      lines: [expect.objectContaining({ unitPrice: '4.200000' })],
    }))
    expect(router.currentRoute.value.name).toBe('procurement-order-detail')
  })

  it('编辑回填只使用 canonical 行级来源 ID，不把报价或协议单头 ID 写入行字段', async () => {
    procurementApiMock.orders.get.mockResolvedValueOnce({
      ...draftOrder,
      quoteId: 9001,
      agreementId: 9002,
      lines: [
        {
          ...draftOrder.lines[0],
          quoteLineId: null,
          sourceQuoteLineId: 301,
          quoteId: 9001,
          priceAgreementLineId: null,
          agreementId: 9002,
        },
      ],
    })
    const { wrapper } = await mountForm('/procurement/orders/99/edit')

    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.orders.update).toHaveBeenCalledWith(99, expect.objectContaining({
      lines: [expect.objectContaining({
        quoteLineId: 301,
      })],
    }))
    const submittedLine = procurementApiMock.orders.update.mock.calls[0][1].lines[0]
    expect(submittedLine).not.toHaveProperty('priceAgreementLineId')
    expect(submittedLine).not.toHaveProperty('quoteId')
    expect(submittedLine).not.toHaveProperty('agreementId')
  })

  it('允许同物料不同来源行并在更新时保留来源组合', async () => {
    procurementApiMock.orders.get.mockResolvedValueOnce({
      ...draftOrder,
      lineCount: 2,
      lines: [
        {
          ...draftOrder.lines[0],
          id: 501,
          lineNo: 10,
          requisitionLineId: 901,
          quoteLineId: 301,
          priceAgreementLineId: null,
        },
        {
          ...draftOrder.lines[0],
          id: 502,
          lineNo: 20,
          requisitionLineId: 902,
          quoteLineId: 302,
          priceAgreementLineId: null,
          quantity: '5.000000',
        },
      ],
    })
    const { wrapper } = await mountForm('/procurement/orders/99/edit')

    await wrapper.find('[data-test="save-purchase-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('同一采购订单内来源组合不能重复')
    expect(procurementApiMock.orders.update).toHaveBeenCalledWith(99, expect.objectContaining({
      lines: [
        expect.objectContaining({ materialId: 10, requisitionLineId: 901, quoteLineId: 301 }),
        expect.objectContaining({ materialId: 10, requisitionLineId: 902, quoteLineId: 302 }),
      ],
    }))
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
