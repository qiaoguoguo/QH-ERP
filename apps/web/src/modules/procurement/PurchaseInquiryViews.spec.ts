import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { MaterialRecord, PartnerRecord } from '../../shared/api/masterDataApi'
import type {
  ProcurementInquiryDetailRecord,
  ProcurementInquirySummaryRecord,
  ProcurementRequisitionDetailRecord,
  SupplierQuoteRecord,
} from '../../shared/api/procurementApi'
import type { SalesProjectSummary } from '../../shared/api/salesProjectApi'
import {
  expectNoBareIdFilters,
  expectStandardDetailPage,
  expectStandardFormPage,
  expectStandardListPage,
} from '../../test/pageGovernanceAssertions'
import { useAuthStore } from '../../stores/authStore'
import PurchaseInquiryDetailView from './PurchaseInquiryDetailView.vue'
import PurchaseInquiryFormView from './PurchaseInquiryFormView.vue'
import PurchaseInquiryListView from './PurchaseInquiryListView.vue'

const procurementApiMock = vi.hoisted(() => ({
  requisitions: {
    get: vi.fn(),
  },
  inquiries: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    release: vi.fn(),
  },
  quotes: {
    list: vi.fn(),
    select: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  materials: {
    list: vi.fn(),
  },
  suppliers: {
    list: vi.fn(),
  },
}))

const salesProjectApiMock = vi.hoisted(() => ({
  projects: {
    list: vi.fn(),
  },
}))

const documentPlatformApiMock = vi.hoisted(() => ({
  documentTasks: {
    get: vi.fn(),
    errors: vi.fn(),
    download: vi.fn(),
    cancel: vi.fn(),
  },
  imports: {
    confirm: vi.fn(),
    uploadProcurementQuotes: vi.fn(),
  },
  exports: {
    createProcurementInquiries: vi.fn(),
    createProcurementQuotes: vi.fn(),
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

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
  createIdempotencyKey: () => 'procurement-document-key',
}))

const quoteA: SupplierQuoteRecord = {
  id: 301,
  inquiryId: 201,
  inquiryNo: 'INQ-001',
  quoteNo: 'QUO-001',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  supplierId: 100,
  supplierName: '华东五金',
  materialId: 5,
  materialCode: 'M-100',
  materialName: '伺服电机',
  quantity: '100.000000',
  taxRate: '0.130000',
  taxIncludedUnitPrice: '113.000000',
  taxExcludedUnitPrice: '100.000000',
  taxIncludedAmount: '11300.000000',
  taxExcludedAmount: '10000.000000',
  currency: 'CNY',
  deliveryDate: '2026-07-25',
  validTo: '2026-08-01',
  status: 'VALID',
  lowestEffectiveQuote: true,
  priceSourceTypeName: '最低有效报价',
  allowedActions: ['SELECT'],
  version: 5,
}

const project: SalesProjectSummary = {
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

const material: MaterialRecord = {
  id: 5,
  code: 'M-100',
  name: '伺服电机',
  materialType: 'RAW_MATERIAL',
  sourceType: 'PURCHASED',
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  categoryId: 1,
  unitId: 1,
  unitName: '台',
  status: 'ENABLED',
}

const supplierA: PartnerRecord = {
  id: 100,
  code: 'SUP-100',
  name: '华东五金',
  status: 'ENABLED',
}

const supplierB: PartnerRecord = {
  ...supplierA,
  id: 101,
  code: 'SUP-101',
  name: '华北机电',
}

const quoteB: SupplierQuoteRecord = {
  ...quoteA,
  id: 302,
  quoteNo: 'QUO-002',
  supplierId: 101,
  supplierName: '华北机电',
  taxIncludedUnitPrice: '118.000000',
  taxExcludedUnitPrice: '104.424779',
  taxIncludedAmount: '11800.000000',
  taxExcludedAmount: '10442.477900',
  lowestEffectiveQuote: false,
  priceSourceTypeName: '非最低选价',
  selectedReason: '交期更短，需要例外审批',
  version: 6,
}

const inquiry: ProcurementInquirySummaryRecord = {
  id: 201,
  inquiryNo: 'INQ-001',
  title: '项目电机询价',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  status: 'COMPLETED',
  statusName: '已完成',
  supplierCount: 2,
  quoteCount: 2,
  materialSummary: 'M-100 伺服电机',
  allowedActions: ['AWARD'],
  createdByName: '采购员',
  createdAt: '2026-07-15T09:00:00+08:00',
  updatedAt: '2026-07-15T10:00:00+08:00',
  version: 4,
}

const detail: ProcurementInquiryDetailRecord = {
  ...inquiry,
  lines: [{
    id: 211,
    lineNo: 10,
    procurementMode: 'PROJECT',
    projectId: 30,
    projectCode: 'PRJ-024',
    projectName: '华东产线改造',
    materialId: 5,
    materialCode: 'M-100',
    materialName: '伺服电机',
    unitName: '台',
    quantity: '100.000000',
    requiredDate: '2026-07-25',
    requisitionNo: 'REQ-PRJ-001',
  }],
  quotes: [quoteA, quoteB],
  sourceChain: [{ sourceType: 'REQUISITION', sourceNo: 'REQ-PRJ-001', summary: '项目专采电机' }],
}

const approvedRequisition: ProcurementRequisitionDetailRecord = {
  id: 1,
  requisitionNo: 'REQ-PRJ-001',
  title: '项目电机请购',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  requiredDate: '2026-07-25',
  status: 'APPROVED',
  approvalStatus: 'APPROVED',
  approvalStatusName: '审批通过',
  materialSummary: 'M-100 伺服电机',
  lineCount: 1,
  totalQuantity: '100.000000',
  orderedQuantity: '0.000000',
  remainingQuantity: '100.000000',
  allowedActions: ['CREATE_INQUIRY'],
  createdByName: '采购员',
  createdAt: '2026-07-15T09:00:00+08:00',
  updatedAt: '2026-07-15T10:00:00+08:00',
  version: 3,
  lines: [{
    id: 211,
    lineNo: 10,
    procurementMode: 'PROJECT',
    projectId: 30,
    projectCode: 'PRJ-024',
    projectName: '华东产线改造',
    materialId: 5,
    materialCode: 'M-100',
    materialName: '伺服电机',
    unitId: 1,
    unitName: '台',
    quantity: '100.000000',
    orderedQuantity: '0.000000',
    remainingQuantity: '100.000000',
    requiredDate: '2026-07-25',
    suggestedSupplierId: 100,
    suggestedSupplierName: '华东五金',
    taxRate: '0.130000',
    purpose: '项目专采生产配套',
  }],
}

function setup(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'buyer', displayName: '采购员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return pinia
}

async function createTestRouter(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/procurement/inquiries', name: 'procurement-inquiries', component: PurchaseInquiryListView },
      { path: '/procurement/inquiries/create', name: 'procurement-inquiry-create', component: PurchaseInquiryFormView },
      { path: '/procurement/inquiries/:id/edit', name: 'procurement-inquiry-edit', component: PurchaseInquiryFormView },
      { path: '/procurement/inquiries/:id', name: 'procurement-inquiry-detail', component: PurchaseInquiryDetailView },
    ],
  })
  await router.push(path)
  await router.isReady()
  return router
}

async function setSelectValue(wrapper: ReturnType<typeof mount>, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index]
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

describe('采购询价与报价页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.requisitions.get.mockResolvedValue(approvedRequisition)
    procurementApiMock.inquiries.list.mockResolvedValue({
      items: [inquiry],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    } satisfies PageResult<ProcurementInquirySummaryRecord>)
    procurementApiMock.inquiries.get.mockResolvedValue(detail)
    procurementApiMock.inquiries.create.mockResolvedValue(detail)
    procurementApiMock.inquiries.update.mockResolvedValue({ ...detail, version: 5 })
    procurementApiMock.inquiries.release.mockResolvedValue({ ...detail, status: 'RELEASED' })
    procurementApiMock.quotes.list.mockResolvedValue({
      items: [quoteA, quoteB],
      page: 1,
      pageSize: 50,
      total: 2,
      totalPages: 1,
    } satisfies PageResult<SupplierQuoteRecord>)
    procurementApiMock.quotes.select.mockResolvedValue({ ...quoteA, status: 'SELECTED' })
    masterDataApiMock.materials.list.mockResolvedValue({ items: [material], page: 1, pageSize: 200, total: 1, totalPages: 1 })
    masterDataApiMock.suppliers.list.mockResolvedValue({ items: [supplierA, supplierB], page: 1, pageSize: 200, total: 2, totalPages: 1 })
    salesProjectApiMock.projects.list.mockResolvedValue({ items: [project], page: 1, pageSize: 200, total: 1, totalPages: 1 })
    documentPlatformApiMock.exports.createProcurementInquiries.mockResolvedValue({
      id: 901,
      taskNo: 'TASK-INQ-EXPORT',
      taskType: 'PROCUREMENT_INQUIRY_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
    documentPlatformApiMock.imports.uploadProcurementQuotes.mockResolvedValue({
      id: 902,
      taskNo: 'TASK-QUOTE-IMPORT',
      taskType: 'PROCUREMENT_QUOTE_IMPORT',
      direction: 'IMPORT',
      stage: 'VALIDATE',
      status: 'READY_TO_COMMIT',
      availableActions: ['CONFIRM', 'ERRORS'],
      version: 2,
    })
    documentPlatformApiMock.exports.createProcurementQuotes.mockResolvedValue({
      id: 903,
      taskNo: 'TASK-QUOTE-EXPORT',
      taskType: 'PROCUREMENT_QUOTE_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'SUCCEEDED',
      availableActions: ['DOWNLOAD'],
      version: 3,
    })
  })

  it('询价列表展示项目、状态、供应商报价数量并按当前筛选创建导出任务', async () => {
    const pinia = setup([
      'procurement:inquiry:view',
      'procurement:quote:import',
      'procurement:quote:export',
      'platform:document-task:create',
      'procurement:document:export',
    ])
    const router = await createTestRouter('/procurement/inquiries')
    const wrapper = mount(PurchaseInquiryListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardListPage(wrapper)
    expectNoBareIdFilters(wrapper, ['项目 ID'])
    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).toContain('业务状态：已完成')
    expect(wrapper.text()).toContain('M-100 伺服电机')
    expect(wrapper.text()).toContain('供应商 2 家 / 报价 2 条')
    expect(wrapper.text()).toContain('当前筛选导出')

    await wrapper.find('[data-test="export-inquiries"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.exports.createProcurementInquiries).toHaveBeenCalledWith({
      keyword: '',
      procurementMode: undefined,
      projectId: undefined,
      status: undefined,
      idempotencyKey: 'procurement-document-key',
    })
    expect(wrapper.text()).toContain('TASK-INQ-EXPORT')
  })

  it('询价详情展示报价比较并在单个询价范围内创建报价导入导出任务', async () => {
    const pinia = setup(['procurement:inquiry:view', 'procurement:quote:select', 'procurement:quote:import', 'procurement:quote:export'])
    const router = await createTestRouter('/procurement/inquiries/201')
    const wrapper = mount(PurchaseInquiryDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardDetailPage(wrapper)
    expect(procurementApiMock.inquiries.get).toHaveBeenCalledWith('201')
    expect(wrapper.text()).toContain('供应商报价比较')
    expect(wrapper.text()).toContain('最低有效报价')
    expect(wrapper.text()).toContain('非最低选价')
    expect(wrapper.text()).toContain('交期更短，需要例外审批')
    expect(wrapper.text()).toContain('未税单价 100')
    expect(wrapper.text()).toContain('含税单价 113')
    expect(wrapper.text()).toContain('税率 0.13')
    expect(wrapper.text()).toContain('CNY')
    expect(wrapper.text()).toContain('来源链')
    expect(wrapper.text()).toContain('附件')
    expect(wrapper.text()).toContain('审计')

    const fileInput = wrapper.find<HTMLInputElement>('[data-test="quote-import-file"]')
    Object.defineProperty(fileInput.element, 'files', {
      value: [new File(['quotes'], '供应商报价.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })],
    })
    await fileInput.trigger('change')
    await flushPromises()
    expect(documentPlatformApiMock.imports.uploadProcurementQuotes).toHaveBeenCalledWith(201, {
      file: expect.any(File),
      idempotencyKey: 'procurement-document-key',
    })

    await wrapper.find('[data-test="export-quotes"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.exports.createProcurementQuotes).toHaveBeenCalledWith(201, {
      supplierId: undefined,
      status: undefined,
      idempotencyKey: 'procurement-document-key',
    })
    expect(wrapper.text()).toContain('TASK-QUOTE-EXPORT')
  })

  it('询价表单保持数量字符串并只提交本询价范围', async () => {
    const pinia = setup(['procurement:inquiry:create'])
    const router = await createTestRouter('/procurement/inquiries/create')
    const wrapper = mount(PurchaseInquiryFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardFormPage(wrapper)
    await setSelectValue(wrapper, 1, 30)
    await setSelectValue(wrapper, 2, 5)
    await setSelectValue(wrapper, 3, [100, 101])
    await wrapper.find('input[name="inquiry-quantity"]').setValue('100.000000')
    await wrapper.find('[data-test="save-inquiry"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.inquiries.create).toHaveBeenCalledWith(expect.objectContaining({
      procurementMode: 'PROJECT',
      projectId: 30,
      supplierIds: [100, 101],
      title: '伺服电机',
      lines: [expect.objectContaining({ materialId: 5, unitId: 1, quantity: '100.000000' })],
    }))
  })

  it('从请购创建询价时消费 requisitionId 并提交请购行来源', async () => {
    const pinia = setup(['procurement:inquiry:create'])
    const router = await createTestRouter('/procurement/inquiries/create?requisitionId=1')
    const wrapper = mount(PurchaseInquiryFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(procurementApiMock.requisitions.get).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('请购来源：REQ-PRJ-001 / 行 10 / M-100 伺服电机')
    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect((wrapper.find('input[name="inquiry-quantity"]').element as HTMLInputElement).value).toBe('100.000000')

    await setSelectValue(wrapper, 3, [100])
    await wrapper.find('[data-test="save-inquiry"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.inquiries.create).toHaveBeenCalledWith(expect.objectContaining({
      procurementMode: 'PROJECT',
      projectId: 30,
      supplierIds: [100],
      lines: [expect.objectContaining({
        requisitionLineId: 211,
        materialId: 5,
        quantity: '100.000000',
      })],
    }))
  })

  it('询价编辑路由按 id 加载详情并携带 version 更新', async () => {
    const pinia = setup(['procurement:inquiry:update'])
    const router = await createTestRouter('/procurement/inquiries/201/edit')
    const wrapper = mount(PurchaseInquiryFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(procurementApiMock.inquiries.get).toHaveBeenCalledWith('201')
    expect(wrapper.text()).toContain('编辑询价')
    expect(wrapper.text()).toContain('PRJ-024 华东产线改造')
    expect(wrapper.text()).toContain('M-100 伺服电机')

    await wrapper.find('input[name="inquiry-quantity"]').setValue('88.000000')
    await wrapper.find('[data-test="save-inquiry"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.inquiries.update).toHaveBeenCalledWith(201, expect.objectContaining({
      procurementMode: 'PROJECT',
      projectId: 30,
      supplierIds: [100, 101],
      version: 4,
      lines: [expect.objectContaining({ materialId: 5, quantity: '88.000000' })],
    }))
    expect(router.currentRoute.value.name).toBe('procurement-inquiry-detail')
  })

  it('询价编辑加载失败时只显示错误与返回动作，不渲染可编辑表单或保存入口', async () => {
    procurementApiMock.inquiries.get.mockRejectedValueOnce(new Error('NOT_FOUND'))
    const pinia = setup(['procurement:inquiry:update'])
    const router = await createTestRouter('/procurement/inquiries/404/edit')
    const wrapper = mount(PurchaseInquiryFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('NOT_FOUND')
    expect(wrapper.find('[data-test="save-inquiry"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="inquiry-material-id"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="back-inquiries"]').exists()).toBe(true)
  })
})
