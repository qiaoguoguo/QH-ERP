import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { MaterialRecord, PartnerRecord } from '../../../shared/api/masterDataApi'
import type { SalesQuoteDetailRecord, SalesQuoteSummaryRecord } from '../../../shared/api/salesFulfillmentApi'
import {
  expectNoBareIdFilters,
  expectStandardDetailPage,
  expectStandardFormPage,
  expectStandardListPage,
} from '../../../test/pageGovernanceAssertions'
import { useAuthStore } from '../../../stores/authStore'
import SalesQuoteDetailView from './SalesQuoteDetailView.vue'
import SalesQuoteFormView from './SalesQuoteFormView.vue'
import SalesQuoteListView from './SalesQuoteListView.vue'

const salesFulfillmentApiMock = vi.hoisted(() => ({
  quotes: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    submitApproval: vi.fn(),
    cancel: vi.fn(),
    convertOrder: vi.fn(),
    convertContract: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  customers: { list: vi.fn() },
  materials: { list: vi.fn() },
}))

const salesProjectApiMock = vi.hoisted(() => ({
  projects: { list: vi.fn() },
  listOrderLinkCandidates: vi.fn(),
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
  },
  exports: {
    createSalesQuotes: vi.fn(),
  },
  printTasks: {
    createSalesQuote: vi.fn(),
  },
}))

vi.mock('../../../shared/api/salesFulfillmentApi', () => ({
  salesFulfillmentApi: salesFulfillmentApiMock,
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../../shared/api/salesProjectApi', () => ({
  salesProjectApi: salesProjectApiMock,
}))

vi.mock('../../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
  createIdempotencyKey: () => 'sales-doc-key',
}))

const customer: PartnerRecord = {
  id: 8,
  code: 'CUS-008',
  name: '华东客户',
  status: 'ENABLED',
}

const material: MaterialRecord = {
  id: 31,
  code: 'FG-001',
  name: '控制柜',
  materialType: 'FINISHED_GOOD',
  sourceType: 'SELF_MADE',
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  categoryId: 1,
  unitId: 2,
  unitName: '台',
  status: 'ENABLED',
}

const project = {
  id: 20,
  projectNo: 'PRJ-025',
  name: '华东产线',
  customerId: 8,
  customerName: '华东客户',
  status: 'ACTIVE',
}

const projectContractCandidate = {
  projectId: 20,
  projectNo: 'PRJ-025',
  projectName: '华东产线',
  customerId: 8,
  customerName: '华东客户',
  contractId: 55,
  contractNo: 'SC-025',
  externalContractNo: 'EXT-025',
  contractName: '华东主合同',
  contractType: 'MAIN',
}

function projectContractCandidateAt(index: number) {
  return {
    ...projectContractCandidate,
    projectId: 20,
    contractId: 1000 + index,
    contractNo: `SC-${String(index).padStart(3, '0')}`,
    contractName: `第${index}合同`,
  }
}

const quoteSummary: SalesQuoteSummaryRecord = {
  id: 9,
  quoteNo: 'SQ-202607-001',
  customerId: 8,
  customerCode: 'CUS-008',
  customerName: '华东客户',
  projectId: 20,
  projectNo: 'PRJ-025',
  projectName: '华东产线',
  quoteDate: '2026-07-16',
  validUntil: '2026-08-16',
  currency: 'CNY',
  totalUntaxedAmount: '1000.000000',
  totalTaxAmount: '130.000000',
  totalTaxIncludedAmount: '1130.000000',
  status: 'DRAFT',
  approvalStatus: 'SUBMITTED',
  allowedActions: ['SUBMIT_APPROVAL', 'CANCEL', 'CONVERT_ORDER', 'CONVERT_CONTRACT', 'PRINT', 'EXPORT'],
  actionDisabledReason: null,
  creditRestricted: false,
  contractRestricted: false,
  amountRestricted: false,
  createdByName: '销售员',
  createdAt: '2026-07-16T09:00:00+08:00',
  updatedAt: '2026-07-16T10:00:00+08:00',
  version: 3,
}

const quoteDetail: SalesQuoteDetailRecord = {
  ...quoteSummary,
  deliveryCommitment: '30 天内交付',
  priceMode: 'TAX_INCLUDED',
  defaultTaxRate: '0.130000',
  settlementMethod: 'MONTHLY',
  paymentTermDays: 30,
  paymentTerms: '月结 30 天',
  remark: '项目报价',
  lines: [{
    id: 91,
    lineNo: 1,
    materialId: 31,
    materialCode: 'FG-001',
    materialName: '控制柜',
    unitId: 2,
    unitName: '台',
    quantity: '10.000000',
    untaxedUnitPrice: '100.000000',
    taxIncludedUnitPrice: '113.000000',
    taxRate: '0.130000',
    untaxedAmount: '1000.000000',
    taxAmount: '130.000000',
    taxIncludedAmount: '1130.000000',
    promisedDate: '2026-08-01',
  }],
}

const backendCanonicalQuoteSummary = {
  ...quoteSummary,
  projectNo: undefined,
  projectCode: 'SP20260715143401020001',
  projectName: '桥合低压柜技改项目',
  totalUntaxedAmount: undefined,
  totalTaxAmount: undefined,
  totalTaxIncludedAmount: undefined,
  taxExcludedAmount: '1000.00',
  taxAmount: '130.00',
  taxIncludedAmount: '1130.00',
} as unknown as SalesQuoteSummaryRecord

const backendCanonicalQuoteDetail = {
  ...quoteDetail,
  projectNo: undefined,
  projectCode: 'SP20260715143401020001',
  projectName: '桥合低压柜技改项目',
  totalUntaxedAmount: undefined,
  totalTaxAmount: undefined,
  totalTaxIncludedAmount: undefined,
  taxExcludedAmount: '1000.00',
  taxAmount: '130.00',
  taxIncludedAmount: '1130.00',
  lines: [{
    id: 91,
    lineNo: 1,
    materialId: 31,
    materialCode: 'FG-001',
    materialName: '控制柜',
    unitId: 2,
    unitName: '台',
    quantity: '10.000000',
    untaxedUnitPrice: undefined,
    taxExcludedUnitPrice: '100.000000',
    taxIncludedUnitPrice: '113.000000',
    taxRate: '0.130000',
    untaxedAmount: undefined,
    taxExcludedAmount: '1000.000000',
    taxAmount: '130.000000',
    taxIncludedAmount: '1130.000000',
    promisedDate: undefined,
    requiredDate: '2026-08-01',
  }],
} as unknown as SalesQuoteDetailRecord

function setup(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'seller', displayName: '销售员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return pinia
}

async function createTestRouter(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sales/quotes', name: 'sales-quotes', component: SalesQuoteListView },
      { path: '/sales/quotes/create', name: 'sales-quote-create', component: SalesQuoteFormView },
      { path: '/sales/quotes/:id/edit', name: 'sales-quote-edit', component: SalesQuoteFormView },
      { path: '/sales/quotes/:id', name: 'sales-quote-detail', component: SalesQuoteDetailView },
      { path: '/sales/orders/create', name: 'sales-order-create', component: { render: () => null } },
      { path: '/sales/projects/:id', name: 'sales-project-detail', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  return router
}

function pageResult<T>(items: T[]): PageResult<T> {
  return { items, total: items.length, page: 1, pageSize: 10 }
}

async function openMoreActions(wrapper: VueWrapper, index = 0) {
  const moreButtons = wrapper.findAll('button').filter((button) => button.text() === '更多')
  expect(moreButtons.length).toBeGreaterThan(index)
  await moreButtons[index].trigger('click')
  await flushPromises()
}

function teleportedAction(testId: string) {
  const actions = Array.from(document.body.querySelectorAll<HTMLElement>(`[data-test="${testId}"]`))
  const visibleActions = actions.filter((action) => {
    const popper = action.closest<HTMLElement>('.el-popper')
    return !popper || (popper.getAttribute('aria-hidden') !== 'true' && popper.style.display !== 'none')
  })
  const action = visibleActions.at(-1) ?? actions.at(-1)
  expect(action).not.toBeNull()
  return action!
}

async function clickTeleportedAction(wrapper: VueWrapper, testId: string, moreIndex = 0) {
  await openMoreActions(wrapper, moreIndex)
  teleportedAction(testId).click()
  await flushPromises()
}

async function updateComponentModel(wrapper: ReturnType<typeof mount>, dataTest: string, value: string | number) {
  const component = wrapper
    .findAllComponents({ name: 'BusinessReferenceSelect' })
    .find((candidate) => candidate.props('dataTest') === dataTest)
  expect(component?.exists()).toBe(true)
  component?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function updateTextInput(wrapper: ReturnType<typeof mount>, name: string, value: string) {
  const inputComponent = wrapper
    .findAllComponents({ name: 'ElInput' })
    .find((candidate) => candidate.props('name') === name)
  if (inputComponent) {
    inputComponent.vm.$emit('update:modelValue', value)
    await flushPromises()
    return
  }
  await wrapper.find(`input[name="${name}"]`).setValue(value)
}

async function expectReferenceLabels(wrapper: ReturnType<typeof mount>, dataTest: string, expectedLabels: string[]) {
  const component = wrapper
    .findAllComponents({ name: 'BusinessReferenceSelect' })
    .find((candidate) => candidate.props('dataTest') === dataTest)
  expect(component?.exists()).toBe(true)
  const loadOptions = component?.props('loadOptions') as (keyword: string) => Promise<Array<{ label: string }>>
  const options = await loadOptions('')
  expectedLabels.forEach((label) => {
    expect(options.map((option) => option.label)).toContain(label)
  })
}

describe('025 销售报价页面', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  beforeEach(() => {
    vi.clearAllMocks()
    salesFulfillmentApiMock.quotes.list.mockResolvedValue(pageResult([quoteSummary]))
    salesFulfillmentApiMock.quotes.get.mockResolvedValue(quoteDetail)
    salesFulfillmentApiMock.quotes.create.mockResolvedValue(quoteDetail)
    salesFulfillmentApiMock.quotes.update.mockResolvedValue({ ...quoteDetail, version: 4 })
    salesFulfillmentApiMock.quotes.submitApproval.mockResolvedValue({ id: 31, status: 'SUBMITTED', version: 1 })
    salesFulfillmentApiMock.quotes.cancel.mockResolvedValue({ ...quoteDetail, status: 'CANCELLED', version: 4 })
    salesFulfillmentApiMock.quotes.convertOrder.mockResolvedValue({ id: 88, orderNo: 'SO-001' })
    salesFulfillmentApiMock.quotes.convertContract.mockResolvedValue({ id: 55, contractNo: 'SC-001' })
    masterDataApiMock.customers.list.mockResolvedValue(pageResult([customer]))
    masterDataApiMock.materials.list.mockResolvedValue(pageResult([material]))
    salesProjectApiMock.projects.list.mockResolvedValue(pageResult([project]))
    salesProjectApiMock.listOrderLinkCandidates.mockResolvedValue(pageResult([projectContractCandidate]))
    documentPlatformApiMock.exports.createSalesQuotes.mockResolvedValue({
      id: 900,
      taskNo: 'TASK-SALES-QUOTE-EXPORT',
      taskType: 'SALES_QUOTE_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
    documentPlatformApiMock.printTasks.createSalesQuote.mockResolvedValue({
      id: 901,
      taskNo: 'TASK-SALES-QUOTE-PRINT',
      taskType: 'SALES_QUOTE_PRINT',
      direction: 'PRINT',
      stage: 'PRINT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
  })

  it('报价列表展示项目/审批/税价和 allowedActions 动作，并按当前筛选导出', async () => {
    const pinia = setup([
      'sales:quote:view',
      'sales:quote:create',
      'sales:quote:submit',
      'sales:quote:cancel',
      'sales:quote:convert',
      'platform:document-task:create',
      'sales:document:export',
    ])
    const router = await createTestRouter('/sales/quotes')
    const wrapper = mount(SalesQuoteListView, { attachTo: document.body, global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardListPage(wrapper)
    expectNoBareIdFilters(wrapper, ['客户 ID', '项目 ID'])
    expect(wrapper.text()).toContain('SQ-202607-001')
    expect(wrapper.text()).toContain('项目销售 · PRJ-025/华东产线')
    expect(wrapper.text()).toContain('审批状态：已提交')
    expect(wrapper.text()).toContain('未税 1000')
    expect(wrapper.text()).toContain('含税 1130')
    expect(wrapper.text()).toContain('CNY')
    await openMoreActions(wrapper)
    expect(teleportedAction('submit-sales-quote-9')).toBeTruthy()
    expect(teleportedAction('cancel-sales-quote-9')).toBeTruthy()
    expect(teleportedAction('convert-sales-quote-order-9')).toBeTruthy()
    expect(teleportedAction('convert-sales-quote-contract-9')).toBeTruthy()

    await clickTeleportedAction(wrapper, 'convert-sales-quote-order-9')
    expect(salesFulfillmentApiMock.quotes.convertOrder).not.toHaveBeenCalled()
    expect(salesProjectApiMock.listOrderLinkCandidates).toHaveBeenCalledWith({
      customerId: 8,
      keyword: '',
      page: 1,
      pageSize: 50,
    })
    await wrapper.find('[data-test="quote-convert-project-contract"]').setValue('20:55')
    await wrapper.find('[data-test="confirm-sales-quote-conversion"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.quotes.convertOrder).toHaveBeenCalledWith(9, {
      version: 3,
      projectId: 20,
      contractId: 55,
      idempotencyKey: 'sales-doc-key',
    })

    await clickTeleportedAction(wrapper, 'convert-sales-quote-contract-9')
    await wrapper.find('[data-test="quote-convert-contract-type"]').setValue('SUPPLEMENT')
    await wrapper.find('[data-test="quote-convert-main-contract"]').setValue('20:55')
    await wrapper.find('[data-test="confirm-sales-quote-conversion"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.quotes.convertContract).toHaveBeenCalledWith(9, {
      version: 3,
      projectId: 20,
      contractType: 'SUPPLEMENT',
      mainContractId: 55,
      idempotencyKey: 'sales-doc-key',
    })

    await wrapper.find('[data-test="export-sales-quotes"]').trigger('click')
    await flushPromises()

    expect(documentPlatformApiMock.exports.createSalesQuotes).toHaveBeenCalledWith({
      keyword: '',
      customerId: undefined,
      projectId: undefined,
      status: undefined,
      approvalStatus: undefined,
      validFrom: '',
      validTo: '',
      idempotencyKey: 'sales-doc-key',
    })
    expect(wrapper.text()).toContain('TASK-SALES-QUOTE-EXPORT')
  })

  it('报价审批筛选只暴露稳定枚举并让列表与导出发送同一值', async () => {
    const pinia = setup([
      'sales:quote:view',
      'platform:document-task:create',
      'sales:document:export',
    ])
    const router = await createTestRouter('/sales/quotes')
    const wrapper = mount(SalesQuoteListView, { attachTo: document.body, global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    const optionValues = wrapper.findAllComponents({ name: 'ElOption' }).map((option) => option.props('value'))
    expect(optionValues).toEqual(expect.arrayContaining(['NONE', 'SUBMITTED', 'APPROVED', 'REJECTED']))
    expect(optionValues).not.toContain('IN_APPROVAL')
    expect(optionValues).not.toContain('NOT_SUBMITTED')

    const approvalSelect = wrapper
      .findAllComponents({ name: 'ElSelect' })
      .find((select) => select.props('placeholder') === '审批状态')
    expect(approvalSelect?.exists()).toBe(true)
    approvalSelect?.vm.$emit('update:modelValue', 'NONE')
    await wrapper.find('[data-test="search-sales-quotes"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.quotes.list).toHaveBeenLastCalledWith(expect.objectContaining({
      approvalStatus: 'NONE',
    }))

    await wrapper.find('[data-test="export-sales-quotes"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.exports.createSalesQuotes).toHaveBeenLastCalledWith(expect.objectContaining({
      approvalStatus: 'NONE',
    }))
  })

  it('报价转换候选支持远程搜索，第一页之外的第 21 条以后仍可达', async () => {
    const firstPage = Array.from({ length: 20 }, (_, index) => projectContractCandidateAt(index + 1))
    const twentyFifth = projectContractCandidateAt(25)
    salesProjectApiMock.listOrderLinkCandidates
      .mockResolvedValueOnce({ items: firstPage, page: 1, pageSize: 20, total: 25, totalPages: 2 })
      .mockResolvedValueOnce({ items: [twentyFifth], page: 1, pageSize: 50, total: 1, totalPages: 1 })
    const pinia = setup(['sales:quote:view', 'sales:quote:convert'])
    const router = await createTestRouter('/sales/quotes')
    const wrapper = mount(SalesQuoteListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    await clickTeleportedAction(wrapper, 'convert-sales-quote-order-9')
    expect(wrapper.text()).not.toContain('第25合同')

    await wrapper.find('[data-test="quote-convert-candidate-search"]').setValue('第25')
    await flushPromises()
    expect(salesProjectApiMock.listOrderLinkCandidates).toHaveBeenLastCalledWith({
      customerId: 8,
      keyword: '第25',
      page: 1,
      pageSize: 50,
    })
    expect(wrapper.text()).toContain('第25合同')

    await wrapper.find('[data-test="quote-convert-project-contract"]').setValue('20:1025')
    await wrapper.find('[data-test="confirm-sales-quote-conversion"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.quotes.convertOrder).toHaveBeenCalledWith(9, expect.objectContaining({
      projectId: 20,
      contractId: 1025,
    }))
  })

  it('报价列表和详情消费后端 canonical projectCode 与 taxIncludedAmount 字段', async () => {
    salesFulfillmentApiMock.quotes.list.mockResolvedValueOnce(pageResult([backendCanonicalQuoteSummary]))
    salesFulfillmentApiMock.quotes.get.mockResolvedValueOnce(backendCanonicalQuoteDetail)

    const pinia = setup(['sales:quote:view'])
    const listRouter = await createTestRouter('/sales/quotes')
    const listWrapper = mount(SalesQuoteListView, { global: { plugins: [pinia, listRouter, ElementPlus] } })
    await flushPromises()

    expect(listWrapper.text()).toContain('项目销售 · SP20260715143401020001/桥合低压柜技改项目')
    expect(listWrapper.text()).toContain('未税 1000')
    expect(listWrapper.text()).toContain('税额 130')
    expect(listWrapper.text()).toContain('含税 1130 CNY')
    expect(listWrapper.text()).not.toContain('项目未返回')
    expect(listWrapper.text()).not.toContain('含税 - CNY')

    const detailRouter = await createTestRouter('/sales/quotes/9')
    const detailWrapper = mount(SalesQuoteDetailView, { global: { plugins: [pinia, detailRouter, ElementPlus] } })
    await flushPromises()

    expectStandardDetailPage(detailWrapper)
    expect(detailWrapper.text()).toContain('项目销售 · SP20260715143401020001/桥合低压柜技改项目')
    expect(detailWrapper.text()).toContain('含税金额1130 CNY')
    expect(detailWrapper.text()).toContain('未税单价 100')
    expect(detailWrapper.text()).toContain('含税金额 1130 CNY')
    expect(detailWrapper.text()).toContain('承诺日期 2026-08-01')
    expect(detailWrapper.text()).not.toContain('项目未返回')
    expect(detailWrapper.text()).not.toContain('含税金额- CNY')
    expect(detailWrapper.text()).not.toContain('未税单价 -')
  })

  it('报价详情按 allowedActions 提交审批、转换和打印，所有副作用携带 version 与幂等键', async () => {
    const pinia = setup([
      'sales:quote:view',
      'sales:quote:submit',
      'sales:quote:cancel',
      'sales:quote:convert',
      'platform:document-task:create',
      'sales:document:print',
    ])
    const router = await createTestRouter('/sales/quotes/9')
    const wrapper = mount(SalesQuoteDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('FG-001 控制柜')
    expect(wrapper.text()).toContain('未税单价 100')
    expect(wrapper.text()).toContain('含税单价 113')
    expect(wrapper.text()).toContain('税率 0.13')
    expect(wrapper.text()).toContain('报价 -> 合同/订单 -> 交付计划 -> 出库 -> 退货/关闭')

    await wrapper.find('[data-test="submit-sales-quote-detail"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.quotes.submitApproval).toHaveBeenCalledWith(9, {
      version: 3,
      reason: '报价确认',
      idempotencyKey: 'sales-doc-key',
    })

    await wrapper.find('[data-test="convert-sales-quote-order"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="quote-convert-project-contract"]').setValue('20:55')
    await wrapper.find('[data-test="confirm-sales-quote-conversion"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.quotes.convertOrder).toHaveBeenCalledWith(9, {
      version: 3,
      projectId: 20,
      contractId: 55,
      idempotencyKey: 'sales-doc-key',
    })

    await wrapper.find('[data-test="print-sales-quote"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.printTasks.createSalesQuote).toHaveBeenCalledWith(9, {
      idempotencyKey: 'sales-doc-key',
    })
    expect(wrapper.text()).toContain('TASK-SALES-QUOTE-PRINT')
  })

  it('报价表单提交真实客户物料 Long ID 和精确金额十进制字符串，编辑携带 version', async () => {
    const pinia = setup(['sales:quote:create', 'sales:quote:update'])
    const router = await createTestRouter('/sales/quotes/create')
    const wrapper = mount(SalesQuoteFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardFormPage(wrapper, ['quote-date', 'quote-valid-until'])
    const quoteForm = wrapper.findComponent({ name: 'ElForm' })
    expect(quoteForm.exists()).toBe(true)
    expect(quoteForm.props('labelPosition')).toBe('top')
    expect(wrapper.findAllComponents({ name: 'ElFormItem' }).length).toBeGreaterThanOrEqual(6)
    const referenceSelects = wrapper.findAllComponents({ name: 'BusinessReferenceSelect' })
    expect(referenceSelects.map((select) => select.props('dataTest'))).toEqual(expect.arrayContaining([
      'quote-customer-select',
      'quote-project-select',
      'quote-line-material-select',
    ]))
    expect(wrapper.find('.sales-quote-form select').exists()).toBe(false)
    expect(wrapper.find('.sales-quote-form option').exists()).toBe(false)
    const lineEditor = wrapper.find('.sales-quote-line-editor')
    expect(lineEditor.exists()).toBe(true)
    expect(lineEditor.find('.table-scroll').exists()).toBe(true)
    expect(lineEditor.findComponent({ name: 'ElTable' }).exists()).toBe(true)
    expect(lineEditor.text()).toContain('物料')
    expect(lineEditor.text()).toContain('数量')
    expect(lineEditor.text()).toContain('含税单价')
    expect(lineEditor.text()).toContain('税率')
    expect(lineEditor.text()).toContain('承诺日期')
    expect(wrapper.find('.form-footer [data-test="save-sales-quote"]').exists()).toBe(true)
    await expectReferenceLabels(wrapper, 'quote-customer-select', ['CUS-008 华东客户'])
    await expectReferenceLabels(wrapper, 'quote-project-select', ['PRJ-025 华东产线'])
    await expectReferenceLabels(wrapper, 'quote-line-material-select', ['FG-001 控制柜 / 台'])
    expect(wrapper.find('[data-test="quote-customer-id"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="quote-line-material-id"]').exists()).toBe(false)

    await updateComponentModel(wrapper, 'quote-customer-select', 8)
    await updateComponentModel(wrapper, 'quote-project-select', 20)
    await wrapper.find('input[name="quote-date"]').setValue('2026-07-16')
    await wrapper.find('input[name="quote-valid-until"]').setValue('2026-08-16')
    await updateComponentModel(wrapper, 'quote-line-material-select', 31)
    await updateTextInput(wrapper, 'quote-line-quantity', '3.000000')
    await updateTextInput(wrapper, 'quote-line-untaxed-price', '10.000000')
    await updateTextInput(wrapper, 'quote-line-tax-included-price', '11.300000')
    await updateTextInput(wrapper, 'quote-line-tax-rate', '0.130000')
    await wrapper.find('[data-test="save-sales-quote"]').trigger('click')
    await flushPromises()

    expect(salesFulfillmentApiMock.quotes.create).toHaveBeenCalledWith(expect.objectContaining({
      customerId: 8,
      currency: 'CNY',
      lines: [expect.objectContaining({
        materialId: 31,
        quantity: '3.000000',
        untaxedUnitPrice: '10.000000',
        taxIncludedUnitPrice: '11.300000',
        taxRate: '0.130000',
        untaxedAmount: '30.000000',
        taxAmount: '3.900000',
        taxIncludedAmount: '33.900000',
      })],
    }))

    const editRouter = await createTestRouter('/sales/quotes/9/edit')
    const editWrapper = mount(SalesQuoteFormView, { global: { plugins: [pinia, editRouter, ElementPlus] } })
    await flushPromises()

    await editWrapper.find('[data-test="save-sales-quote"]').trigger('click')
    await flushPromises()
    expect(salesFulfillmentApiMock.quotes.update).toHaveBeenCalledWith(9, expect.objectContaining({ version: 3 }))
  })

  it('报价编辑加载失败时只显示错误与返回动作，不渲染可编辑表单或保存入口', async () => {
    salesFulfillmentApiMock.quotes.get.mockRejectedValueOnce(new Error('NOT_FOUND'))
    const pinia = setup(['sales:quote:update'])
    const router = await createTestRouter('/sales/quotes/404/edit')
    const wrapper = mount(SalesQuoteFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('NOT_FOUND')
    expect(wrapper.find('[data-test="save-sales-quote"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="quote-customer-select"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="back-sales-quotes"]').exists()).toBe(true)
  })

  it('普通报价转订单直接按手工订单提交，不强制选择项目合同', async () => {
    salesFulfillmentApiMock.quotes.list.mockResolvedValue(pageResult([{
      ...quoteSummary,
      projectId: null,
      projectNo: null,
      projectName: null,
    }]))
    const pinia = setup(['sales:quote:view', 'sales:quote:convert'])
    const router = await createTestRouter('/sales/quotes')
    const wrapper = mount(SalesQuoteListView, { attachTo: document.body, global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    await clickTeleportedAction(wrapper, 'convert-sales-quote-order-9')

    expect(salesFulfillmentApiMock.quotes.convertOrder).toHaveBeenCalledWith(9, {
      version: 3,
      projectId: null,
      contractId: null,
      idempotencyKey: 'sales-doc-key',
    })
    expect(salesProjectApiMock.listOrderLinkCandidates).not.toHaveBeenCalled()
  })
})
