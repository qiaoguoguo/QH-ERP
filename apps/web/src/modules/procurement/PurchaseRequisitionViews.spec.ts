import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { MaterialRecord, PartnerRecord } from '../../shared/api/masterDataApi'
import type { ProcurementRequisitionDetailRecord, ProcurementRequisitionSummaryRecord } from '../../shared/api/procurementApi'
import type { SalesProjectSummary } from '../../shared/api/salesProjectApi'
import {
  expectNoBareIdFilters,
  expectStandardDetailPage,
  expectStandardFormPage,
  expectStandardListPage,
} from '../../test/pageGovernanceAssertions'
import { useAuthStore } from '../../stores/authStore'
import PurchaseRequisitionDetailView from './PurchaseRequisitionDetailView.vue'
import PurchaseRequisitionFormView from './PurchaseRequisitionFormView.vue'
import PurchaseRequisitionListView from './PurchaseRequisitionListView.vue'

const procurementApiMock = vi.hoisted(() => ({
  requisitions: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    submitApproval: vi.fn(),
    close: vi.fn(),
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
    createProcurementRequisitions: vi.fn(),
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
  createIdempotencyKey: () => 'requisition-export-key',
}))

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
  id: 100,
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

const supplier: PartnerRecord = {
  id: 200,
  code: 'SUP-200',
  name: '华东五金',
  status: 'ENABLED',
}

const projectRequisition: ProcurementRequisitionSummaryRecord = {
  id: 1,
  requisitionNo: 'REQ-PRJ-001',
  title: '项目专采电机',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  requiredDate: '2026-07-25',
  status: 'APPROVED',
  approvalStatus: 'APPROVED',
  approvalStatusName: '审批通过',
  materialSummary: 'M-100 电机',
  lineCount: 1,
  totalQuantity: '100.000000',
  orderedQuantity: '40.000000',
  remainingQuantity: '60.000000',
  allowedActions: ['SUBMIT_APPROVAL', 'CREATE_INQUIRY', 'CREATE_ORDER', 'CLOSE'],
  closeReason: null,
  createdByName: '采购员',
  createdAt: '2026-07-15T09:00:00+08:00',
  updatedAt: '2026-07-15T10:00:00+08:00',
  version: 3,
}

const publicRequisition: ProcurementRequisitionSummaryRecord = {
  ...projectRequisition,
  id: 2,
  requisitionNo: 'REQ-PUB-001',
  title: '公共备料',
  procurementMode: 'PUBLIC',
  projectId: null,
  projectCode: null,
  projectName: null,
  status: 'CLOSED',
  approvalStatusName: '审批通过',
  materialSummary: 'M-200 铜排',
  remainingQuantity: '0.000000',
  allowedActions: [],
  closeReason: '项目设计变更，未转数量不再采购',
}

const requisitionPage: PageResult<ProcurementRequisitionSummaryRecord> = {
  items: [projectRequisition, publicRequisition],
  page: 1,
  pageSize: 10,
  total: 2,
  totalPages: 1,
}

const requisitionDetail: ProcurementRequisitionDetailRecord = {
  ...projectRequisition,
  lines: [{
    id: 11,
    lineNo: 10,
    procurementMode: 'PROJECT',
    projectId: 30,
    projectCode: 'PRJ-024',
    projectName: '华东产线改造',
    materialId: 100,
    materialCode: 'M-100',
    materialName: '伺服电机',
    unitId: 1,
    unitName: '台',
    quantity: '100.000000',
    orderedQuantity: '40.000000',
    remainingQuantity: '60.000000',
    requiredDate: '2026-07-25',
    suggestedSupplierName: '华东五金',
    taxRate: '0.130000',
    purpose: '项目专采生产配套',
  }],
  sourceChain: [{ sourceType: 'PROJECT', sourceNo: 'PRJ-024', summary: '华东产线改造' }],
}

function setupAuth(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'buyer', displayName: '采购员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return pinia
}

function createTestRouter(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/procurement/requisitions', name: 'procurement-requisitions', component: PurchaseRequisitionListView },
      { path: '/procurement/requisitions/create', name: 'procurement-requisition-create', component: PurchaseRequisitionFormView },
      { path: '/procurement/requisitions/:id', name: 'procurement-requisition-detail', component: PurchaseRequisitionDetailView },
      { path: '/procurement/requisitions/:id/edit', name: 'procurement-requisition-edit', component: PurchaseRequisitionFormView },
      { path: '/procurement/inquiries/create', name: 'procurement-inquiry-create', component: { render: () => null } },
      { path: '/procurement/orders/create', name: 'procurement-order-create', component: { render: () => null } },
    ],
  })
  return router.push(path).then(() => router.isReady()).then(() => router)
}

async function setSelectValue(wrapper: ReturnType<typeof mount>, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index]
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function openMoreActions(wrapper: VueWrapper) {
  const moreButton = wrapper.findAll('button').find((button) => button.text() === '更多')
  expect(moreButton).toBeTruthy()
  await moreButton!.trigger('click')
  await flushPromises()
}

function teleportedAction(testId: string) {
  const actions = Array.from(document.body.querySelectorAll<HTMLElement>(`[data-test="${testId}"]`))
  const action = actions.at(-1)
  expect(action).not.toBeNull()
  return action!
}

async function clickTeleportedAction(wrapper: VueWrapper, testId: string) {
  await openMoreActions(wrapper)
  teleportedAction(testId).click()
  await flushPromises()
}

describe('采购请购页面', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.requisitions.list.mockResolvedValue(requisitionPage)
    procurementApiMock.requisitions.get.mockResolvedValue(requisitionDetail)
    procurementApiMock.requisitions.create.mockResolvedValue(requisitionDetail)
    procurementApiMock.requisitions.update.mockResolvedValue({ ...requisitionDetail, version: 4 })
    procurementApiMock.requisitions.submitApproval.mockResolvedValue({
      id: 810,
      sceneCode: 'PROCUREMENT_REQUISITION_APPROVAL',
      objectType: 'PROCUREMENT_REQUISITION',
      objectId: 1,
      status: 'SUBMITTED',
      availableActions: [],
      version: 1,
      steps: [],
      histories: [],
      attachmentSnapshots: [],
    })
    procurementApiMock.requisitions.close.mockResolvedValue({ ...requisitionDetail, status: 'CLOSED' })
    masterDataApiMock.materials.list.mockResolvedValue({ items: [material], page: 1, pageSize: 200, total: 1, totalPages: 1 })
    masterDataApiMock.suppliers.list.mockResolvedValue({ items: [supplier], page: 1, pageSize: 200, total: 1, totalPages: 1 })
    salesProjectApiMock.projects.list.mockResolvedValue({ items: [project], page: 1, pageSize: 200, total: 1, totalPages: 1 })
    documentPlatformApiMock.exports.createProcurementRequisitions.mockResolvedValue({
      id: 905,
      taskNo: 'TASK-REQ-EXPORT',
      taskType: 'PROCUREMENT_REQUISITION_EXPORT',
      direction: 'EXPORT',
      stage: 'EXPORT',
      status: 'QUEUED',
      availableActions: ['CANCEL'],
      version: 1,
    })
  })

  it('列表首屏展示模式项目、审批状态、进度、结案原因和 allowedActions 动作', async () => {
    const pinia = setupAuth([
      'procurement:requisition:view',
      'procurement:requisition:create',
      'platform:document-task:create',
      'procurement:document:export',
    ])
    const router = await createTestRouter('/procurement/requisitions')

    const wrapper = mount(PurchaseRequisitionListView, { attachTo: document.body, global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardListPage(wrapper)
    expectNoBareIdFilters(wrapper, ['项目 ID'])
    expect(procurementApiMock.requisitions.list).toHaveBeenCalledWith({
      keyword: '',
      procurementMode: undefined,
      projectId: undefined,
      status: undefined,
      approvalStatus: undefined,
      requiredDateFrom: '',
      requiredDateTo: '',
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).toContain('公共采购')
    expect(wrapper.text()).toContain('业务状态：已批准')
    expect(wrapper.text()).not.toContain('业务状态：APPROVED')
    expect(wrapper.text()).toContain('审批状态：审批通过')
    expect(wrapper.text()).toContain('M-100 电机')
    expect(wrapper.text()).toContain('计划/已转/剩余：100/40/60')
    expect(wrapper.text()).toContain('项目设计变更，未转数量不再采购')
    expect(wrapper.text()).toContain('创建询价')
    await openMoreActions(wrapper)
    expect(document.body.textContent).toContain('转采购订单')
    expect(document.body.textContent).toContain('结案')

    await wrapper.find('[data-test="export-requisitions"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.exports.createProcurementRequisitions).toHaveBeenCalledWith({
      keyword: '',
      procurementMode: undefined,
      projectId: undefined,
      status: undefined,
      approvalStatus: undefined,
      requiredDateFrom: '',
      requiredDateTo: '',
      idempotencyKey: 'requisition-export-key',
    })
    expect(wrapper.text()).toContain('TASK-REQ-EXPORT')
  })

  it('列表业务状态使用统一中文标签，不泄露 DRAFT 等内部码', async () => {
    procurementApiMock.requisitions.list.mockResolvedValueOnce({
      items: [
        {
          ...projectRequisition,
          id: 101,
          requisitionNo: 'REQ-OPEN-001',
          status: 'DRAFT',
          statusName: null,
          approvalStatus: 'NOT_SUBMITTED',
          approvalStatusName: null,
          allowedActions: [],
        },
        {
          ...projectRequisition,
          id: 102,
          requisitionNo: 'REQ-PART-001',
          status: 'PARTIALLY_ORDERED',
          statusName: null,
          approvalStatus: 'APPROVED',
          approvalStatusName: null,
          allowedActions: [],
        },
      ],
      page: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
    })
    const pinia = setupAuth(['procurement:requisition:view'])
    const router = await createTestRouter('/procurement/requisitions')
    const wrapper = mount(PurchaseRequisitionListView, { attachTo: document.body, global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    const businessStatuses = wrapper.findAll('[data-test="requisition-row-business-status"]').map((tag) => tag.text())
    expect(businessStatuses).toEqual(expect.arrayContaining(['草稿', '部分转单']))
    expect(wrapper.text()).not.toContain('DRAFT')
    expect(wrapper.text()).not.toContain('PARTIALLY_ORDERED')
  })

  it('列表和详情按真实后端字段回填请购采购模式与项目标识', async () => {
    const backendRequisition = {
      ...projectRequisition,
      purchaseMode: 'PROJECT',
      procurementMode: undefined,
      ownershipType: 'PROJECT',
      projectCode: null,
      projectName: null,
    }
    procurementApiMock.requisitions.list.mockResolvedValueOnce({
      items: [backendRequisition],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    procurementApiMock.requisitions.get.mockResolvedValueOnce({
      ...requisitionDetail,
      purchaseMode: 'PROJECT',
      procurementMode: undefined,
      ownershipType: 'PROJECT',
      projectCode: null,
      projectName: null,
      lines: requisitionDetail.lines.map((line) => ({
        ...line,
        purchaseMode: 'PROJECT',
        procurementMode: undefined,
        ownershipType: 'PROJECT',
        projectCode: null,
        projectName: null,
      })),
    })

    const listPinia = setupAuth(['procurement:requisition:view'])
    const listRouter = await createTestRouter('/procurement/requisitions')
    const listWrapper = mount(PurchaseRequisitionListView, { global: { plugins: [listPinia, listRouter, ElementPlus] } })
    await flushPromises()

    expect(listWrapper.text()).toContain('项目专采 · 项目ID 30')
    expect(listWrapper.text()).not.toContain('采购模式未返回')

    const detailPinia = setupAuth(['procurement:requisition:view'])
    const detailRouter = await createTestRouter('/procurement/requisitions/1')
    const detailWrapper = mount(PurchaseRequisitionDetailView, { global: { plugins: [detailPinia, detailRouter, ElementPlus] } })
    await flushPromises()

    expect(detailWrapper.text()).toContain('项目专采 · 项目ID 30')
    expect(detailWrapper.text()).not.toContain('采购模式未返回')
  })

  it('列表 allowedActions 动作可衔接询价、订单表单并调用请购结案', async () => {
    const pinia = setupAuth([
      'procurement:requisition:view',
      'procurement:inquiry:create',
      'procurement:order:create',
      'procurement:requisition:close',
    ])
    const router = await createTestRouter('/procurement/requisitions')
    const wrapper = mount(PurchaseRequisitionListView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    await wrapper.find('[data-test="create-inquiry-from-requisition-list"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-inquiry-create')
    expect(router.currentRoute.value.query.requisitionId).toBe('1')

    await router.push('/procurement/requisitions')
    await flushPromises()
    await clickTeleportedAction(wrapper, 'create-order-from-requisition-list')
    expect(router.currentRoute.value.name).toBe('procurement-order-create')
    expect(router.currentRoute.value.query.requisitionId).toBe('1')

    await router.push('/procurement/requisitions')
    await flushPromises()
    await clickTeleportedAction(wrapper, 'close-requisition-list')
    expect(procurementApiMock.requisitions.close).toHaveBeenCalledWith(1, expect.objectContaining({
      version: 3,
      reason: expect.any(String),
      idempotencyKey: expect.any(String),
    }))
    expect(procurementApiMock.requisitions.list).toHaveBeenCalledTimes(2)
  })

  it('表单提交保持数量和税率字符串并显示中文错误区域', async () => {
    const pinia = setupAuth(['procurement:requisition:create'])
    const router = await createTestRouter('/procurement/requisitions/create')
    const wrapper = mount(PurchaseRequisitionFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardFormPage(wrapper, ['requisition-required-date'])
    await setSelectValue(wrapper, 1, 30)
    await setSelectValue(wrapper, 2, 100)
    await setSelectValue(wrapper, 3, 200)
    await wrapper.find('input[name="requisition-quantity"]').setValue('999999999999.123456')
    await wrapper.find('input[name="requisition-tax-rate"]').setValue('0.130000')
    await wrapper.find('input[name="requisition-required-date"]').setValue('2026-07-25')
    await wrapper.find('textarea[name="requisition-purpose"]').setValue('项目专采生产配套')
    await wrapper.find('[data-test="save-requisition"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.requisitions.create).toHaveBeenCalledWith(expect.objectContaining({
      procurementMode: 'PROJECT',
      projectId: 30,
      lines: [expect.objectContaining({
        materialId: 100,
        suggestedSupplierId: 200,
        quantity: '999999999999.123456',
        taxRate: '0.130000',
      })],
    }))
    expect(wrapper.text()).not.toContain('undefined')
  })

  it('编辑路由按 id 回填详情并携带 version 调用 update', async () => {
    const pinia = setupAuth(['procurement:requisition:update'])
    const router = await createTestRouter('/procurement/requisitions/1/edit')
    const wrapper = mount(PurchaseRequisitionFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(procurementApiMock.requisitions.get).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('编辑采购请购')
    expect(wrapper.text()).toContain('PRJ-024 华东产线改造')
    expect(wrapper.text()).toContain('M-100 伺服电机')

    await wrapper.find('input[name="requisition-quantity"]').setValue('88.000000')
    await wrapper.find('[data-test="save-requisition"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.requisitions.update).toHaveBeenCalledWith(1, expect.objectContaining({
      version: 3,
      projectId: 30,
      lines: [expect.objectContaining({ materialId: 100, quantity: '88.000000' })],
    }))
  })

  it.each([
    ['读取失败', '请购加载失败'],
    ['404', '请购不存在'],
    ['403', '无权编辑采购请购'],
  ])('编辑路由%s时只显示错误和返回列表，不露出表单保存且不误创建', async (_caseName, message) => {
    procurementApiMock.requisitions.get.mockRejectedValueOnce(new Error(message))
    const pinia = setupAuth(['procurement:requisition:update'])
    const router = await createTestRouter('/procurement/requisitions/404/edit')
    const wrapper = mount(PurchaseRequisitionFormView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(procurementApiMock.requisitions.get).toHaveBeenCalledWith('404')
    expect(wrapper.text()).toContain(message)
    expect(wrapper.text()).toContain('无法编辑采购请购')
    expect(wrapper.find('[data-test="back-requisitions"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="save-requisition"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="requisition-material-id"]').exists()).toBe(false)
    expect(wrapper.find('input[name="requisition-quantity"]').exists()).toBe(false)
    expect(procurementApiMock.requisitions.create).not.toHaveBeenCalled()
    expect(procurementApiMock.requisitions.update).not.toHaveBeenCalled()
  })

  it('详情展示来源链、明细、审批和操作区，并提供真实审批与来源导航动作', async () => {
    const pinia = setupAuth([
      'procurement:requisition:view',
      'procurement:requisition:submit',
      'procurement:requisition:close',
      'procurement:inquiry:create',
      'procurement:order:create',
    ])
    const router = await createTestRouter('/procurement/requisitions/1')
    const wrapper = mount(PurchaseRequisitionDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expectStandardDetailPage(wrapper)
    expect(procurementApiMock.requisitions.get).toHaveBeenCalledWith('1')
    const pageDescription = wrapper.find('.page-heading p').text()
    expect(pageDescription).toBe('查看采购请购的状态、来源、明细、审批与转化进度。')
    expect(pageDescription).not.toContain('REQ-PRJ-001')
    expect(pageDescription).not.toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.find('.summary-strip').exists()).toBe(true)
    expect(wrapper.find('.summary-grid').exists()).toBe(false)
    expect(wrapper.find('.state-box').exists()).toBe(false)
    expect(wrapper.find('.trace-grid').exists()).toBe(false)
    expect(wrapper.find('[data-test="back-requisition-list"]').exists()).toBe(true)
    const pageActions = wrapper.find('.page-actions')
    expect(pageActions.text()).not.toContain('业务状态')
    expect(pageActions.text()).not.toContain('审批状态')
    expect(wrapper.text()).toContain('REQ-PRJ-001')
    expect(wrapper.find('.purchase-requisition-detail-list').exists()).toBe(true)
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('请购号')
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('标题')
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('采购模式')
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('项目/公共')
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('物料摘要')
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('来源摘要')
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('创建人')
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('创建时间')
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('更新时间')
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('备注')
    expect(wrapper.find('.purchase-requisition-detail-list').text()).toContain('结案原因')
    expect(wrapper.findAll('.section-title').map((section) => section.text())).toEqual(expect.arrayContaining([
      '请购明细',
      '来源链',
      '审批与附件',
      '审计',
    ]))
    expect(wrapper.findAll('.numeric-cell').length).toBeGreaterThan(0)
    expect(wrapper.text()).toContain('来源链')
    expect(wrapper.text()).toContain('项目专采 · PRJ-024/华东产线改造')
    expect(wrapper.text()).toContain('伺服电机')
    expect(wrapper.text()).toContain('审批状态：审批通过')
    expect(wrapper.text()).toContain('附件')
    expect(wrapper.text()).toContain('审计')

    await wrapper.find('[data-test="submit-requisition-approval"]').trigger('click')
    await flushPromises()
    expect(procurementApiMock.requisitions.submitApproval).toHaveBeenCalledWith(1, expect.objectContaining({
      version: 3,
      reason: expect.any(String),
      idempotencyKey: expect.any(String),
    }))
    expect(procurementApiMock.requisitions.get).toHaveBeenCalledTimes(2)

    await wrapper.find('[data-test="create-inquiry-from-requisition"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-inquiry-create')
    expect(router.currentRoute.value.query.requisitionId).toBe('1')

    await router.push('/procurement/requisitions/1')
    await flushPromises()
    await wrapper.find('[data-test="create-order-from-requisition"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-order-create')
    expect(router.currentRoute.value.query.requisitionId).toBe('1')
  })

  it('详情中文化状态与隐藏动作码，物料摘要为空时从明细回退且摘要保持短值', async () => {
    procurementApiMock.requisitions.get.mockResolvedValueOnce({
      ...requisitionDetail,
      status: 'SUBMITTED',
      statusName: null,
      approvalStatus: 'SUBMITTED',
      approvalStatusName: null,
      materialSummary: null,
      allowedActions: ['CANCEL'],
      lines: [
        requisitionDetail.lines[0],
        {
          ...requisitionDetail.lines[0],
          id: 12,
          lineNo: 20,
          materialId: 101,
          materialCode: 'M-200',
          materialName: '铜排',
        },
        {
          ...requisitionDetail.lines[0],
          id: 13,
          lineNo: 30,
          materialId: 102,
          materialCode: 'M-300',
          materialName: '控制柜',
        },
      ],
    } as unknown as ProcurementRequisitionDetailRecord)
    const pinia = setupAuth(['procurement:requisition:view'])
    const router = await createTestRouter('/procurement/requisitions/1')
    const wrapper = mount(PurchaseRequisitionDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    const summaryStrip = wrapper.find('.summary-strip')
    expect(summaryStrip.find('[data-test="requisition-business-status"]').text()).toContain('已提交')
    expect(summaryStrip.find('[data-test="requisition-approval-status"]').text()).toContain('审批中')
    expect(summaryStrip.find('[data-test="requisition-summary-mode"]').text()).toContain('项目专采')
    expect(summaryStrip.find('[data-test="requisition-summary-mode"]').text()).not.toContain('华东产线改造')
    expect(wrapper.find('[data-test="requisition-material-summary"]').text()).toContain('M-100 伺服电机、M-200 铜排等3项')
    expect(wrapper.text()).not.toContain('SUBMITTED')
    expect(wrapper.text()).not.toContain('CANCEL')
    expect(wrapper.text()).not.toContain('动作限制')
  })

  it.each([
    ['读取失败', '请购加载失败'],
    ['404', '请购不存在'],
    ['403', '无权查看采购请购'],
  ])('详情路由%s时只显示错误和返回列表，不显示详情结构或业务动作', async (_caseName, message) => {
    procurementApiMock.requisitions.get.mockRejectedValueOnce(new Error(message))
    const pinia = setupAuth(['procurement:requisition:view'])
    const router = await createTestRouter('/procurement/requisitions/404')
    const wrapper = mount(PurchaseRequisitionDetailView, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    expect(procurementApiMock.requisitions.get).toHaveBeenCalledWith('404')
    expect(wrapper.text()).toContain(message)
    expect(wrapper.text()).toContain('无法加载采购请购')
    expect(wrapper.find('[data-test="back-requisition-list"]').exists()).toBe(true)
    expect(wrapper.find('.summary-strip').exists()).toBe(false)
    expect(wrapper.find('.purchase-requisition-detail-list').exists()).toBe(false)
    expect(wrapper.find('[data-test="submit-requisition-approval"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="create-inquiry-from-requisition"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="create-order-from-requisition"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="close-requisition"]').exists()).toBe(false)

    await wrapper.find('[data-test="back-requisition-list"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('procurement-requisitions')
  })

  it('详情提交审批入口只按 submit 权限显示，approve 权限不能替代提交权限', async () => {
    const submitPinia = setupAuth(['procurement:requisition:view', 'procurement:requisition:submit'])
    const submitRouter = await createTestRouter('/procurement/requisitions/1')
    const submitWrapper = mount(PurchaseRequisitionDetailView, { global: { plugins: [submitPinia, submitRouter, ElementPlus] } })
    await flushPromises()

    expect(submitWrapper.find('[data-test="submit-requisition-approval"]').exists()).toBe(true)

    const approveOnlyPinia = setupAuth(['procurement:requisition:view', 'procurement:requisition:approve'])
    const approveOnlyRouter = await createTestRouter('/procurement/requisitions/1')
    const approveOnlyWrapper = mount(PurchaseRequisitionDetailView, {
      global: { plugins: [approveOnlyPinia, approveOnlyRouter, ElementPlus] },
    })
    await flushPromises()

    expect(approveOnlyWrapper.find('[data-test="submit-requisition-approval"]').exists()).toBe(false)
  })
})
