import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { MaterialRecord, PartnerRecord } from '../../shared/api/masterDataApi'
import type { PriceAgreementDetailRecord, PriceAgreementSummaryRecord } from '../../shared/api/procurementApi'
import type { SalesProjectSummary } from '../../shared/api/salesProjectApi'
import {
  expectNoBareIdFilters,
  expectStandardDetailPage,
  expectStandardFormPage,
  expectStandardListPage,
} from '../../test/pageGovernanceAssertions'
import { useAuthStore } from '../../stores/authStore'
import PriceAgreementDetailView from './PriceAgreementDetailView.vue'
import PriceAgreementFormView from './PriceAgreementFormView.vue'
import PriceAgreementListView from './PriceAgreementListView.vue'

const procurementApiMock = vi.hoisted(() => ({
  priceAgreements: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    submitActivation: vi.fn(),
    disable: vi.fn(),
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
  },
  exports: {
    createProcurementPriceAgreements: vi.fn(),
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
  createIdempotencyKey: () => 'agreement-export-key',
}))

const agreement: PriceAgreementSummaryRecord = {
  id: 401,
  agreementNo: 'AGR-001',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  supplierId: 100,
  supplierName: '华东五金',
  materialId: 5,
  materialCode: 'M-100',
  materialName: '伺服电机',
  taxRate: '0.130000',
  taxIncludedUnitPrice: '113.000000',
  taxExcludedUnitPrice: '100.000000',
  currency: 'CNY',
  minPurchaseQuantity: '10.000000',
  validFrom: '2026-07-15',
  validTo: '2026-12-31',
  status: 'DRAFT',
  statusName: '草稿',
  approvalStatusName: '未提交',
  usageCount: 0,
  allowedActions: ['SUBMIT'],
  createdByName: '采购员',
  createdAt: '2026-07-15T09:00:00+08:00',
  updatedAt: '2026-07-15T10:00:00+08:00',
  version: 1,
}

const detail: PriceAgreementDetailRecord = {
  ...agreement,
  sourceChain: [{ sourceType: 'QUOTE', sourceNo: 'QUO-001', summary: '最低有效报价' }],
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

const supplier: PartnerRecord = {
  id: 100,
  code: 'SUP-100',
  name: '华东五金',
  status: 'ENABLED',
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
      { path: '/procurement/price-agreements', name: 'procurement-price-agreements', component: PriceAgreementListView },
      { path: '/procurement/price-agreements/create', name: 'procurement-price-agreement-create', component: PriceAgreementFormView },
      { path: '/procurement/price-agreements/:id/edit', name: 'procurement-price-agreement-edit', component: PriceAgreementFormView },
      { path: '/procurement/price-agreements/:id', name: 'procurement-price-agreement-detail', component: PriceAgreementDetailView },
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

function expectActionLinkButton(wrapper: VueWrapper, testId: string, href: string, label: string) {
  const link = wrapper.find(`[data-test="${testId}"]`)
  expect(link.exists()).toBe(true)
  expect(link.element.tagName).toBe('A')
  expect(link.classes()).toContain('action-button-link')
  expect(link.attributes('href')).toBe(href)
  const button = link.findComponent({ name: 'ElButton' })
  expect(button.exists()).toBe(true)
  expect(button.props('tag')).toBe('span')
  expect(button.props('text')).toBe(true)
  expect(button.props('size')).toBe('small')
  expect(button.text()).toContain(label)
}

describe('采购价格协议页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.priceAgreements.list.mockResolvedValue({
      items: [agreement],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    } satisfies PageResult<PriceAgreementSummaryRecord>)
    procurementApiMock.priceAgreements.get.mockResolvedValue(detail)
    procurementApiMock.priceAgreements.create.mockResolvedValue(detail)
    procurementApiMock.priceAgreements.update.mockResolvedValue({ ...detail, version: 2 })
    procurementApiMock.priceAgreements.submitActivation.mockResolvedValue({
      id: 811,
      sceneCode: 'PROCUREMENT_PRICE_AGREEMENT_ACTIVATION',
      objectType: 'PROCUREMENT_PRICE_AGREEMENT',
      objectId: 401,
      status: 'SUBMITTED',
      availableActions: [],
      version: 1,
      steps: [],
      histories: [],
      attachmentSnapshots: [],
    })
    procurementApiMock.priceAgreements.disable.mockResolvedValue({ ...detail, status: 'DISABLED' })
    masterDataApiMock.materials.list.mockResolvedValue({ items: [material], page: 1, pageSize: 200, total: 1, totalPages: 1 })
    masterDataApiMock.suppliers.list.mockResolvedValue({ items: [supplier], page: 1, pageSize: 200, total: 1, totalPages: 1 })
    salesProjectApiMock.projects.list.mockResolvedValue({ items: [project], page: 1, pageSize: 200, total: 1, totalPages: 1 })
    documentPlatformApiMock.exports.createProcurementPriceAgreements.mockResolvedValue({
      id: 906,
      taskNo: 'TASK-AGR-EXPORT',
      taskType: 'PROCUREMENT_PRICE_AGREEMENT_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
  })

  it('列表展示适用范围、税价、审批状态和 allowedActions', async () => {
    const pinia = setup([
      'procurement:price-agreement:view',
      'procurement:price-agreement:submit',
      'platform:document-task:create',
      'procurement:document:export',
    ])
    const router = await createTestRouter('/procurement/price-agreements')
    const wrapper = mount(PriceAgreementListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardListPage(wrapper)
    expectNoBareIdFilters(wrapper, ['供应商 ID', '物料 ID', '项目 ID'])
    expect(procurementApiMock.priceAgreements.list).toHaveBeenCalledWith({
      keyword: '',
      supplierId: undefined,
      materialId: undefined,
      procurementMode: undefined,
      projectId: undefined,
      status: undefined,
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).toContain('业务状态：草稿')
    expect(wrapper.text()).toContain('审批状态：未提交')
    expect(wrapper.text()).toContain('未税单价 100')
    expect(wrapper.text()).toContain('含税单价 113')
    expect(wrapper.text()).toContain('税率 0.13')
    expect(wrapper.text()).toContain('CNY')
    expect(wrapper.text()).toContain('提交激活审批')
    expectActionLinkButton(wrapper, 'price-agreement-detail-link', '/procurement/price-agreements/401', '详情')

    await wrapper.find('[data-test="export-price-agreements"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.exports.createProcurementPriceAgreements).toHaveBeenCalledWith({
      keyword: '',
      supplierId: undefined,
      materialId: undefined,
      procurementMode: undefined,
      projectId: undefined,
      status: undefined,
      idempotencyKey: 'agreement-export-key',
    })
    expect(wrapper.text()).toContain('TASK-AGR-EXPORT')
  })

  it('列表和详情按真实后端字段回填价格协议采购模式与项目标识', async () => {
    const backendAgreement = {
      ...agreement,
      purchaseMode: 'PROJECT',
      procurementMode: undefined,
      ownershipType: 'PROJECT',
      projectCode: null,
      projectName: null,
    }
    procurementApiMock.priceAgreements.list.mockResolvedValueOnce({
      items: [backendAgreement],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    procurementApiMock.priceAgreements.get.mockResolvedValueOnce({
      ...detail,
      purchaseMode: 'PROJECT',
      procurementMode: undefined,
      ownershipType: 'PROJECT',
      projectCode: null,
      projectName: null,
    })

    const listPinia = setup(['procurement:price-agreement:view'])
    const listRouter = await createTestRouter('/procurement/price-agreements')
    const listWrapper = mount(PriceAgreementListView, { global: { plugins: [listPinia, listRouter, ElementPlus] } })
    await flushPromises()

    expect(listWrapper.text()).toContain('项目专采 · 项目ID 30')
    expect(listWrapper.text()).not.toContain('采购模式未返回')

    const detailPinia = setup(['procurement:price-agreement:view'])
    const detailRouter = await createTestRouter('/procurement/price-agreements/401')
    const detailWrapper = mount(PriceAgreementDetailView, { global: { plugins: [detailPinia, detailRouter, ElementPlus] } })
    await flushPromises()

    expect(detailWrapper.text()).toContain('项目专采 · 项目ID 30')
    expect(detailWrapper.text()).not.toContain('采购模式未返回')
  })

  it('列表提交激活审批按 submit 权限和 allowedActions 调用提交动作后刷新', async () => {
    const pinia = setup(['procurement:price-agreement:view', 'procurement:price-agreement:submit'])
    const router = await createTestRouter('/procurement/price-agreements')
    const wrapper = mount(PriceAgreementListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    await wrapper.find('[data-test="submit-price-agreement-activation-list"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.priceAgreements.submitActivation).toHaveBeenCalledWith(401, expect.objectContaining({
      version: 1,
      reason: expect.any(String),
      idempotencyKey: expect.any(String),
    }))
    expect(procurementApiMock.priceAgreements.list).toHaveBeenCalledTimes(2)
  })

  it('列表和详情提交激活审批入口只按 submit 权限显示，approve 权限不能替代提交权限', async () => {
    const submitPinia = setup(['procurement:price-agreement:view', 'procurement:price-agreement:submit'])
    const submitRouter = await createTestRouter('/procurement/price-agreements/401')
    const submitWrapper = mount(PriceAgreementDetailView, { global: { plugins: [submitPinia, submitRouter, ElementPlus] } })
    await flushPromises()

    expect(submitWrapper.find('[data-test="submit-price-agreement-activation"]').exists()).toBe(true)

    const approveOnlyDetailPinia = setup(['procurement:price-agreement:view', 'procurement:price-agreement:approve'])
    const approveOnlyDetailRouter = await createTestRouter('/procurement/price-agreements/401')
    const approveOnlyDetailWrapper = mount(PriceAgreementDetailView, {
      global: { plugins: [approveOnlyDetailPinia, approveOnlyDetailRouter, ElementPlus] },
    })
    await flushPromises()
    expect(approveOnlyDetailWrapper.find('[data-test="submit-price-agreement-activation"]').exists()).toBe(false)

    const approveOnlyListPinia = setup(['procurement:price-agreement:view', 'procurement:price-agreement:approve'])
    const approveOnlyListRouter = await createTestRouter('/procurement/price-agreements')
    const approveOnlyListWrapper = mount(PriceAgreementListView, {
      global: { plugins: [approveOnlyListPinia, approveOnlyListRouter, ElementPlus] },
    })
    await flushPromises()
    expect(approveOnlyListWrapper.find('[data-test="submit-price-agreement-activation-list"]').exists()).toBe(false)
  })

  it('表单提交价格协议时保持税价字符串', async () => {
    const pinia = setup(['procurement:price-agreement:create'])
    const router = await createTestRouter('/procurement/price-agreements/create')
    const wrapper = mount(PriceAgreementFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardFormPage(wrapper, ['agreement-valid-from', 'agreement-valid-to'])
    await setSelectValue(wrapper, 0, 'PROJECT')
    await setSelectValue(wrapper, 1, 30)
    await setSelectValue(wrapper, 2, 100)
    await setSelectValue(wrapper, 3, 5)
    await wrapper.find('input[name="agreement-tax-rate"]').setValue('0.130000')
    await wrapper.find('input[name="agreement-tax-excluded-unit-price"]').setValue('100.000000')
    await wrapper.find('input[name="agreement-tax-included-unit-price"]').setValue('113.000000')
    await wrapper.find('input[name="agreement-valid-from"]').setValue('2026-07-15')
    await wrapper.find('input[name="agreement-valid-to"]').setValue('2026-12-31')
    await wrapper.find('[data-test="save-price-agreement"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.priceAgreements.create).toHaveBeenCalledWith(expect.objectContaining({
      procurementMode: 'PROJECT',
      projectId: 30,
      supplierId: 100,
      materialId: 5,
      taxRate: '0.130000',
      taxExcludedUnitPrice: '100.000000',
      taxIncludedUnitPrice: '113.000000',
      currency: 'CNY',
    }))
  })

  it('PUBLIC 协议不要求项目，编辑路由按 id 回填并携带 version 更新', async () => {
    const pinia = setup(['procurement:price-agreement:update'])
    const router = await createTestRouter('/procurement/price-agreements/401/edit')
    const wrapper = mount(PriceAgreementFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(procurementApiMock.priceAgreements.get).toHaveBeenCalledWith('401')
    expect(wrapper.text()).toContain('编辑价格协议')
    expect(wrapper.text()).toContain('PRJ-024 华东产线改造')

    await setSelectValue(wrapper, 0, 'PUBLIC')
    await wrapper.find('input[name="agreement-tax-excluded-unit-price"]').setValue('101.000000')
    await wrapper.find('[data-test="save-price-agreement"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.priceAgreements.update).toHaveBeenCalledWith(401, expect.objectContaining({
      version: 1,
      procurementMode: 'PUBLIC',
      projectId: null,
      supplierId: 100,
      materialId: 5,
      taxExcludedUnitPrice: '101.000000',
    }))
    expect(router.currentRoute.value.name).toBe('procurement-price-agreement-detail')
  })

  it('价格协议编辑加载失败时只显示错误与返回动作，不渲染可编辑表单或保存入口', async () => {
    procurementApiMock.priceAgreements.get.mockRejectedValueOnce(new Error('FORBIDDEN'))
    const pinia = setup(['procurement:price-agreement:update'])
    const router = await createTestRouter('/procurement/price-agreements/404/edit')
    const wrapper = mount(PriceAgreementFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('FORBIDDEN')
    expect(wrapper.find('[data-test="save-price-agreement"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="agreement-supplier-id"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="back-price-agreements"]').exists()).toBe(true)
  })

  it('详情展示来源链、审批、附件和审计，并提交激活审批后重新加载', async () => {
    const pinia = setup(['procurement:price-agreement:view', 'procurement:price-agreement:submit'])
    const router = await createTestRouter('/procurement/price-agreements/401')
    const wrapper = mount(PriceAgreementDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardDetailPage(wrapper)
    expect(wrapper.text()).toContain('AGR-001')
    expect(wrapper.text()).toContain('最低有效报价')
    expect(wrapper.text()).toContain('来源链')
    expect(wrapper.text()).toContain('审批状态：未提交')
    expect(wrapper.text()).toContain('附件')
    expect(wrapper.text()).toContain('审计')

    await wrapper.find('[data-test="submit-price-agreement-activation"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.priceAgreements.submitActivation).toHaveBeenCalledWith(401, expect.objectContaining({
      version: 1,
      reason: expect.any(String),
      idempotencyKey: expect.any(String),
    }))
    expect(procurementApiMock.priceAgreements.get).toHaveBeenCalledTimes(2)
  })
})
