import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PartnerRecord } from '../../../shared/api/masterDataApi'
import type { ProjectOwnerCandidate, SalesProjectDetail } from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import SalesProjectFormView from './SalesProjectFormView.vue'

const salesProjectApiMock = vi.hoisted(() => ({
  projects: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
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

const activeProject: SalesProjectDetail = {
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
  contracts: [],
  salesOrderSummary: null,
  operations: [],
}

async function setSelectValue(wrapper: VueWrapper, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index] as VueWrapper | undefined
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountForm(path = '/sales/projects/create') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: ['sales:project:view', 'sales:project:create', 'sales:project:update'],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sales/projects', name: 'sales-projects', component: { render: () => null } },
      { path: '/sales/projects/create', name: 'sales-project-create', component: SalesProjectFormView },
      { path: '/sales/projects/:id', name: 'sales-project-detail', component: { render: () => null } },
      { path: '/sales/projects/:id/edit', name: 'sales-project-edit', component: SalesProjectFormView },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(SalesProjectFormView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('销售项目表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    masterDataApiMock.customers.list.mockResolvedValue({ items: [customer], page: 1, pageSize: 200, total: 1, totalPages: 1 })
    salesProjectApiMock.ownerCandidates.mockResolvedValue({ items: [owner], page: 1, pageSize: 200, total: 1, totalPages: 1 })
    salesProjectApiMock.projects.get.mockResolvedValue(activeProject)
    salesProjectApiMock.projects.create.mockResolvedValue(activeProject)
    salesProjectApiMock.projects.update.mockResolvedValue(activeProject)
  })

  it('创建销售项目时提交不带 version 的创建 payload', async () => {
    const { wrapper, router } = await mountForm()

    await wrapper.find('input[name="sales-project-name"]').setValue('华东扩产项目')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 7)
    await wrapper.find('input[name="sales-project-planned-start-date"]').setValue('2026-07-01')
    await wrapper.find('input[name="sales-project-planned-finish-date"]').setValue('2026-08-31')
    await wrapper.find('input[name="sales-project-target-revenue"]').setValue('100000.00')
    await wrapper.find('input[name="sales-project-target-cost"]').setValue('70000.00')
    await wrapper.find('input[name="sales-project-remark"]').setValue('重点项目')
    await wrapper.find('[data-test="save-sales-project"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.projects.create).toHaveBeenCalledWith({
      name: '华东扩产项目',
      customerId: 100,
      ownerUserId: 7,
      plannedStartDate: '2026-07-01',
      plannedFinishDate: '2026-08-31',
      targetRevenue: '100000.00',
      targetCost: '70000.00',
      remark: '重点项目',
    })
    expect(salesProjectApiMock.projects.create.mock.calls[0][0]).not.toHaveProperty('version')
    expect(router.currentRoute.value.name).toBe('sales-project-detail')
  })

  it('编辑 ACTIVE 项目时锁定客户和项目编号，并提交携带 version 的更新 payload', async () => {
    const { wrapper } = await mountForm('/sales/projects/12/edit')

    expect(wrapper.text()).toContain('SP-202607-001')
    expect(wrapper.text()).toContain('客户创建后不可修改')
    await wrapper.find('input[name="sales-project-target-cost"]').setValue('71000.00')
    await wrapper.find('[data-test="save-sales-project"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.projects.update).toHaveBeenCalledWith(12, expect.objectContaining({
      version: 5,
      ownerUserId: 7,
      targetCost: '71000.00',
    }))
    expect(salesProjectApiMock.projects.update.mock.calls[0][1]).not.toHaveProperty('customerId')
    expect(salesProjectApiMock.projects.update.mock.calls[0][1]).not.toHaveProperty('projectNo')
  })

  it('编辑项目加载失败时禁用表单和保存，提供重试与返回入口且不落到创建提交', async () => {
    salesProjectApiMock.projects.get.mockRejectedValueOnce(new Error('项目不存在或无权限'))
    const { wrapper, router } = await mountForm('/sales/projects/12/edit')

    expect(wrapper.text()).toContain('项目不存在或无权限')
    expect(wrapper.find('[data-test="save-sales-project"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="retry-sales-project-load"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="return-sales-project-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="return-sales-project-list"]').exists()).toBe(true)

    await wrapper.find('[data-test="save-sales-project"]').trigger('click')
    await flushPromises()
    expect(salesProjectApiMock.projects.create).not.toHaveBeenCalled()
    expect(salesProjectApiMock.projects.update).not.toHaveBeenCalled()

    await wrapper.find('[data-test="return-sales-project-list"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('sales-projects')
  })

  it('必填字段使用必填标记和字段级错误，跨字段错误保留在顶部', async () => {
    const { wrapper } = await mountForm()
    const requiredLabels = wrapper.findAllComponents({ name: 'ElFormItem' })
      .filter((item) => item.props('required'))
      .map((item) => item.props('label'))

    expect(requiredLabels).toEqual(expect.arrayContaining(['项目名称', '客户', '负责人']))

    await wrapper.find('[data-test="save-sales-project"]').trigger('click')
    await flushPromises()
    const errorByLabel = (label: string) => wrapper.findAllComponents({ name: 'ElFormItem' })
      .find((item) => item.props('label') === label)?.props('error')
    expect(errorByLabel('项目名称')).toBe('请填写项目名称')
    expect(errorByLabel('客户')).toBe('请选择客户')
    expect(errorByLabel('负责人')).toBe('请选择负责人')

    await wrapper.find('input[name="sales-project-name"]').setValue('华东扩产项目')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 7)
    await wrapper.find('input[name="sales-project-planned-start-date"]').setValue('2026-08-31')
    await wrapper.find('input[name="sales-project-planned-finish-date"]').setValue('2026-07-01')
    await wrapper.find('[data-test="save-sales-project"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('计划结束日期不得早于计划开始日期')
  })
})
