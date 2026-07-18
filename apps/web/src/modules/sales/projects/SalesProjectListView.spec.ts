import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { PartnerRecord } from '../../../shared/api/masterDataApi'
import type { ProjectOwnerCandidate, SalesProjectSummary } from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import SalesProjectListView from './SalesProjectListView.vue'

const salesProjectApiMock = vi.hoisted(() => ({
  projects: {
    list: vi.fn(),
  },
  ownerCandidates: vi.fn(),
}))

const masterDataApiMock = vi.hoisted(() => ({
  customers: {
    list: vi.fn(),
  },
}))

vi.mock('../../../shared/api/salesProjectApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/salesProjectApi')>()),
  salesProjectApi: salesProjectApiMock,
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const customer: PartnerRecord = { id: 100, code: 'CUS-A', name: '华东客户', status: 'ENABLED' }
const owner: ProjectOwnerCandidate = { userId: 7, username: 'owner', displayName: '负责人' }

const project: SalesProjectSummary = {
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
  contractSummaryRestricted: true,
  mainContractId: null,
  mainContractNo: null,
  mainContractStatus: null,
  effectiveContractAmount: null,
  contractCount: null,
  supplementContractCount: null,
  salesOrderCount: null,
  salesOrderSummaryRestricted: true,
  remark: '重点项目',
  createdByName: '管理员',
  createdAt: '2026-07-12T08:00:00+08:00',
  updatedAt: '2026-07-12T09:00:00+08:00',
  version: 3,
}

const projectPage: PageResult<SalesProjectSummary> = {
  items: [project],
  page: 1,
  pageSize: 10,
  total: 1,
  totalPages: 1,
}

async function setSelectValue(wrapper: VueWrapper, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index] as VueWrapper | undefined
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountList(permissions = [
  'sales:project:view',
  'sales:project:create',
  'sales:project:update',
]) {
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
      { path: '/sales/projects', name: 'sales-projects', component: SalesProjectListView },
      { path: '/sales/projects/create', name: 'sales-project-create', component: { render: () => null } },
      { path: '/sales/projects/:id', name: 'sales-project-detail', component: { render: () => null } },
      { path: '/sales/projects/:id/edit', name: 'sales-project-edit', component: { render: () => null } },
    ],
  })
  await router.push('/sales/projects')
  await router.isReady()
  const wrapper = mount(SalesProjectListView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('销售项目列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    salesProjectApiMock.projects.list.mockResolvedValue(projectPage)
    salesProjectApiMock.ownerCandidates.mockResolvedValue({
      items: [owner],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
    masterDataApiMock.customers.list.mockResolvedValue({
      items: [customer],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
  })

  it('加载销售项目并把合同和订单受限态展示为受限而不是 0', async () => {
    const { wrapper } = await mountList()

    expect(salesProjectApiMock.projects.list).toHaveBeenCalledWith({
      keyword: '',
      customerId: undefined,
      ownerUserId: undefined,
      status: undefined,
      plannedStartFrom: '',
      plannedStartTo: '',
      plannedFinishFrom: '',
      plannedFinishTo: '',
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('SP-202607-001')
    expect(wrapper.text()).toContain('华东扩产项目')
    expect(wrapper.text()).toContain('执行中')
    expect(wrapper.text()).toContain('合同摘要受限')
    expect(wrapper.text()).toContain('订单摘要受限')
    expect(wrapper.find('.sales-project-table-scroll').exists()).toBe(true)
  })

  it('支持筛选、重置和项目页面导航', async () => {
    const { wrapper, router } = await mountList()

    await wrapper.find('input[name="sales-project-keyword"]').setValue('华东')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 2, 'ACTIVE')
    await wrapper.find('input[name="sales-project-planned-start-from"]').setValue('2026-07-01')
    await wrapper.find('[data-test="search-sales-projects"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.projects.list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: '华东',
      customerId: 100,
      status: 'ACTIVE',
      plannedStartFrom: '2026-07-01',
      page: 1,
    }))

    await wrapper.find('[data-test="view-sales-project"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-project-detail')
    expect(router.currentRoute.value.params.id).toBe('12')

    await router.push('/sales/projects')
    await flushPromises()
    await wrapper.find('[data-test="create-sales-project"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-project-create')
  })

  it('负责人筛选使用负责人候选接口并参与项目列表查询', async () => {
    const { wrapper } = await mountList()

    expect(salesProjectApiMock.ownerCandidates).toHaveBeenCalledWith({ keyword: '', page: 1, pageSize: 200 })
    await setSelectValue(wrapper, 1, 7)
    await wrapper.find('[data-test="search-sales-projects"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.projects.list).toHaveBeenLastCalledWith(expect.objectContaining({
      ownerUserId: 7,
      page: 1,
    }))
  })

  it('列表加载失败时只显示错误态，不同时显示空态或表格空文案', async () => {
    salesProjectApiMock.projects.list.mockRejectedValueOnce(new Error('销售项目加载失败'))
    const { wrapper } = await mountList()

    expect(wrapper.text()).toContain('销售项目加载失败')
    expect(wrapper.findComponent({ name: 'ElEmpty' }).exists()).toBe(false)
    expect(wrapper.find('.sales-project-table-scroll').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('暂无销售项目')
  })

  it('列表空数据只保留主空态，不显示表格空态或零条分页', async () => {
    salesProjectApiMock.projects.list.mockResolvedValueOnce({
      items: [],
      page: 1,
      pageSize: 10,
      total: 0,
      totalPages: 0,
    })
    const { wrapper } = await mountList()

    expect(wrapper.findComponent({ name: 'ElEmpty' }).exists()).toBe(true)
    expect(wrapper.find('.sales-project-table-scroll').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'ElPagination' }).exists()).toBe(false)
    expect(wrapper.text().match(/暂无销售项目/g)).toHaveLength(1)
  })
})
