import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { ProjectSalesOrderSummary, SalesProjectDetail } from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import SalesProjectDetailView from './SalesProjectDetailView.vue'

const salesProjectApiMock = vi.hoisted(() => ({
  projects: {
    get: vi.fn(),
    activate: vi.fn(),
    close: vi.fn(),
    cancel: vi.fn(),
  },
  projectSalesOrders: vi.fn(),
}))

vi.mock('../../../shared/api/salesProjectApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/salesProjectApi')>()),
  salesProjectApi: salesProjectApiMock,
}))

const project: SalesProjectDetail = {
  id: 12,
  projectNo: 'SP-202607-001',
  name: '华东扩产项目',
  customerId: 100,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  ownerUserId: 7,
  ownerUsername: 'owner',
  ownerDisplayName: '负责人',
  plannedStartDate: '2026-07-01',
  plannedFinishDate: '2026-08-31',
  status: 'ACTIVE',
  targetRevenue: '100000.00',
  targetCost: '70000.00',
  contractSummaryRestricted: false,
  mainContractId: 55,
  mainContractNo: 'SC-001',
  mainContractStatus: 'EFFECTIVE',
  effectiveContractAmount: '100000.00',
  contractCount: 1,
  supplementContractCount: 0,
  salesOrderCount: 2,
  salesOrderSummaryRestricted: false,
  remark: '重点项目',
  createdByName: '管理员',
  createdAt: '2026-07-12T08:00:00+08:00',
  updatedAt: '2026-07-12T09:00:00+08:00',
  version: 5,
  contracts: [{
    id: 55,
    contractNo: 'SC-001',
    externalContractNo: 'EXT-001',
    projectId: 12,
    contractType: 'MAIN',
    mainContractId: null,
    mainContractNo: null,
    name: '主合同',
    signedDate: '2026-07-02',
    effectiveStartDate: '2026-07-03',
    effectiveEndDate: '2026-12-31',
    amount: '100000.00',
    status: 'EFFECTIVE',
    updatedAt: '2026-07-12T09:00:00+08:00',
    version: 2,
  }],
  salesOrderSummary: {
    orderCount: 2,
    draftCount: 1,
    confirmedCount: 1,
    partiallyShippedCount: 0,
    shippedCount: 0,
    closedCount: 0,
    cancelledCount: 0,
    businessAmount: '88000.00',
    latestOrderDate: '2026-07-10',
  },
  operations: [{
    action: 'PROJECT_UPDATE',
    targetType: 'SALES_PROJECT',
    targetId: 12,
    targetSummary: '更新目标成本',
    operatorUsername: 'admin',
    createdAt: '2026-07-12T10:00:00+08:00',
  }],
}

const projectOrder: ProjectSalesOrderSummary = {
  id: 99,
  orderNo: 'SO-20260710-001',
  projectId: 12,
  projectNo: 'SP-202607-001',
  projectName: '华东扩产项目',
  contractId: 55,
  contractNo: 'SC-001',
  externalContractNo: 'EXT-001',
  customerId: 100,
  customerName: '华东客户',
  orderDate: '2026-07-10',
  expectedShipDate: '2026-07-20',
  status: 'CONFIRMED',
  lineCount: 2,
  totalQuantity: '12.500000',
  businessAmount: '88000.00',
  createdAt: '2026-07-10T08:00:00+08:00',
  updatedAt: '2026-07-10T09:00:00+08:00',
}

async function mountDetail(record: SalesProjectDetail = project, permissions = [
  'sales:project:view',
  'sales:project:update',
  'sales:project:activate',
  'sales:project:close',
  'sales:project:cancel',
  'sales:contract:view',
  'sales:contract:create',
  'sales:order:view',
]) {
  salesProjectApiMock.projects.get.mockResolvedValue(record)
  salesProjectApiMock.projects.activate.mockResolvedValue({ ...record, status: 'ACTIVE', version: record.version + 1 })
  salesProjectApiMock.projects.close.mockResolvedValue({ ...record, status: 'CLOSED', version: record.version + 1 })
  salesProjectApiMock.projects.cancel.mockResolvedValue({ ...record, status: 'CANCELLED', version: record.version + 1 })
  salesProjectApiMock.projectSalesOrders.mockResolvedValue({
    items: [projectOrder],
    page: 1,
    pageSize: 5,
    total: 1,
    totalPages: 1,
  })
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sales/projects', name: 'sales-projects', component: { render: () => null } },
      { path: '/sales/projects/:id', name: 'sales-project-detail', component: SalesProjectDetailView },
      { path: '/sales/projects/:id/edit', name: 'sales-project-edit', component: { render: () => null } },
      { path: '/sales/orders/:id', name: 'sales-order-detail', component: { render: () => null } },
    ],
  })
  await router.push('/sales/projects/12')
  await router.isReady()
  const wrapper = mount(SalesProjectDetailView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

function buttonsByText(wrapper: VueWrapper, text: string): VueWrapper[] {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

describe('销售项目详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('展示基础信息、合同摘要、订单摘要和操作记录', async () => {
    const { wrapper } = await mountDetail()

    expect(wrapper.text()).toContain('SP-202607-001')
    expect(wrapper.text()).toContain('华东扩产项目')
    expect(wrapper.text()).toContain('执行中')
    expect(wrapper.text()).toContain('SC-001')
    expect(wrapper.text()).toContain('已生效')
    expect(wrapper.text()).toContain('订单数量：2')
    expect(wrapper.text()).toContain('更新目标成本')
    expect(wrapper.find('.sales-project-contract-table-scroll').exists()).toBe(true)
  })

  it('展示关联销售订单状态分布、列表摘要和有权限详情入口', async () => {
    const { wrapper, router } = await mountDetail()

    expect(salesProjectApiMock.projectSalesOrders).toHaveBeenCalledWith(12, {
      keyword: '',
      contractId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      page: 1,
      pageSize: 5,
    })
    expect(wrapper.text()).toContain('草稿：1')
    expect(wrapper.text()).toContain('已确认：1')
    expect(wrapper.text()).toContain('SO-20260710-001')
    expect(wrapper.text()).toContain('SC-001')
    expect(wrapper.text()).toContain('88000.00')

    await wrapper.find('[data-test="view-project-sales-order"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-order-detail')
    expect(router.currentRoute.value.params.id).toBe('99')
  })

  it('合同和订单受限时显示受限态，不把 null 展示为 0', async () => {
    const restricted: SalesProjectDetail = {
      ...project,
      contractSummaryRestricted: true,
      mainContractId: null,
      mainContractNo: null,
      mainContractStatus: null,
      effectiveContractAmount: null,
      contractCount: null,
      supplementContractCount: null,
      salesOrderCount: null,
      salesOrderSummaryRestricted: true,
      contracts: [],
      salesOrderSummary: null,
    }
    const { wrapper } = await mountDetail(restricted, ['sales:project:view'])

    expect(wrapper.text()).toContain('合同摘要受限')
    expect(wrapper.text()).toContain('订单摘要受限')
    expect(wrapper.text()).not.toContain('合同数量：0')
    expect(wrapper.text()).not.toContain('订单数量：0')
    expect(salesProjectApiMock.projectSalesOrders).not.toHaveBeenCalled()
  })

  it('项目状态动作和新增合同入口按状态矩阵给出禁用原因与默认合同类型', async () => {
    const draftWithoutEffectiveMain: SalesProjectDetail = {
      ...project,
      status: 'DRAFT',
      mainContractId: null,
      mainContractNo: null,
      mainContractStatus: null,
      contracts: [],
      salesOrderSummary: null,
    }
    const { wrapper } = await mountDetail(draftWithoutEffectiveMain)

    const activateButton = buttonsByText(wrapper, '激活项目')[0]
    expect(activateButton.attributes('disabled')).toBeDefined()
    expect(activateButton.attributes('title')).toBe('项目需先存在已生效主合同后才能激活')

    await buttonsByText(wrapper, '新增合同')[0].trigger('click')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'SalesProjectContractDrawer' }).props('defaultContractType')).toBe('MAIN')

    const active = await mountDetail()
    await buttonsByText(active.wrapper, '新增合同')[0].trigger('click')
    await flushPromises()
    expect(active.wrapper.findComponent({ name: 'SalesProjectContractDrawer' }).props('defaultContractType')).toBe('SUPPLEMENT')
  })

  it('项目终态动作必须填写 1-200 字原因并携带 version 提交', async () => {
    const { wrapper } = await mountDetail()

    await buttonsByText(wrapper, '关闭项目')[0].trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="confirm-project-action"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('请填写 1-200 字原因')
    expect(salesProjectApiMock.projects.close).not.toHaveBeenCalled()

    await wrapper.find('textarea[name="sales-project-action-reason"]').setValue('合同履约完成')
    await wrapper.find('[data-test="confirm-project-action"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.projects.close).toHaveBeenCalledWith(12, { version: 5, reason: '合同履约完成' })
  })
})
