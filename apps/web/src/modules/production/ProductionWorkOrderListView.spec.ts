import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { installElementPlus } from '../../elementPlus'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { ProductionWorkOrderSummaryRecord } from '../../shared/api/productionApi'
import type { ProjectProductionWorkOrderSummaryRecord } from '../../shared/api/projectProductionApi'
import { useAuthStore } from '../../stores/authStore'
import ProductionWorkOrderListView from './ProductionWorkOrderListView.vue'

const qherpElementPlusPlugin = { install: installElementPlus }

const productionApiMock = vi.hoisted(() => ({
  workOrders: {
    list: vi.fn(),
    release: vi.fn(),
    complete: vi.fn(),
    cancel: vi.fn(),
  },
}))

const projectProductionApiMock = vi.hoisted(() => ({
  workOrders: {
    list: vi.fn(),
    release: vi.fn(),
    complete: vi.fn(),
    cancel: vi.fn(),
  },
}))

vi.mock('../../shared/api/productionApi', () => ({
  productionApi: productionApiMock,
}))

vi.mock('../../shared/api/projectProductionApi', () => ({
  projectProductionApi: projectProductionApiMock,
}))

vi.mock('../../shared/ui/confirmDialog', () => ({
  confirmAction: vi.fn(async () => true),
}))

const draftWorkOrder: ProductionWorkOrderSummaryRecord = {
  id: 1,
  workOrderNo: 'WO-DRAFT-001',
  productMaterialId: 10,
  productMaterialCode: 'FG-001',
  productMaterialName: '成品 A',
  bomId: 20,
  bomCode: 'BOM-FG-001',
  bomVersionCode: 'V1',
  plannedQuantity: 100,
  reportedQuantity: 0,
  qualifiedQuantity: 0,
  defectiveQuantity: 0,
  receivedQuantity: 0,
  issueWarehouseId: 30,
  issueWarehouseName: '原料仓',
  receiptWarehouseId: 31,
  receiptWarehouseName: '成品仓',
  plannedStartDate: '2026-07-03',
  plannedFinishDate: '2026-07-10',
  status: 'DRAFT',
  remark: null,
  createdByName: '管理员',
  createdAt: '2026-07-03T08:00:00+08:00',
  updatedAt: '2026-07-03T09:00:00+08:00',
}

const releasedWorkOrder: ProductionWorkOrderSummaryRecord = {
  ...draftWorkOrder,
  id: 2,
  workOrderNo: 'WO-REL-001',
  status: 'RELEASED',
  reportedQuantity: 20,
}

const inProgressWorkOrder: ProductionWorkOrderSummaryRecord = {
  ...draftWorkOrder,
  id: 3,
  workOrderNo: 'WO-PROG-001',
  status: 'IN_PROGRESS',
  reportedQuantity: 40,
  receivedQuantity: 10,
}

const completedWorkOrder: ProductionWorkOrderSummaryRecord = {
  ...draftWorkOrder,
  id: 4,
  workOrderNo: 'WO-DONE-001',
  status: 'COMPLETED',
  reportedQuantity: 100,
  receivedQuantity: 100,
}

const workOrderPage: PageResult<ProductionWorkOrderSummaryRecord> = {
  items: [draftWorkOrder, releasedWorkOrder, inProgressWorkOrder, completedWorkOrder],
  page: 1,
  pageSize: 10,
  total: 4,
  totalPages: 1,
}

function projectCompatibleWorkOrder(
  record: ProductionWorkOrderSummaryRecord,
  allowedActions?: string[],
): ProjectProductionWorkOrderSummaryRecord {
  return {
    ...record,
    ownershipType: 'PUBLIC',
    plannedQuantity: String(record.plannedQuantity),
    reportedQuantity: String(record.reportedQuantity),
    qualifiedQuantity: String(record.qualifiedQuantity),
    defectiveQuantity: String(record.defectiveQuantity),
    receivedQuantity: String(record.receivedQuantity),
    allowedActions,
    version: Number(record.id),
  }
}

const compatibleWorkOrderPage: PageResult<ProjectProductionWorkOrderSummaryRecord> = {
  items: [
    projectCompatibleWorkOrder(draftWorkOrder, ['UPDATE', 'RELEASE', 'CANCEL']),
    projectCompatibleWorkOrder(releasedWorkOrder, ['ISSUE', 'REPORT', 'RECEIPT', 'COMPLETE', 'CANCEL']),
    projectCompatibleWorkOrder(inProgressWorkOrder, ['ISSUE', 'REPORT', 'RECEIPT', 'COMPLETE']),
    projectCompatibleWorkOrder(completedWorkOrder, []),
  ],
  page: 1,
  pageSize: 10,
  total: 4,
  totalPages: 1,
}

const projectWorkOrder: ProjectProductionWorkOrderSummaryRecord = {
  id: 27,
  workOrderNo: 'WO-PROJ-027',
  ownershipType: 'PROJECT',
  projectId: 3001,
  projectNo: 'SP-027',
  projectName: '销售项目 027',
  productMaterialId: 10,
  productMaterialCode: 'FG-001',
  productMaterialName: '成品 A',
  bomId: 20,
  bomCode: 'BOM-FG-001',
  bomVersionCode: 'V1',
  plannedQuantity: '100.000000',
  reportedQuantity: '0.000000',
  qualifiedQuantity: '0.000000',
  defectiveQuantity: '0.000000',
  receivedQuantity: '0.000000',
  issueWarehouseId: 30,
  issueWarehouseName: '原料仓',
  receiptWarehouseId: 31,
  receiptWarehouseName: '成品仓',
  plannedStartDate: '2026-07-03',
  plannedFinishDate: '2026-07-10',
  status: 'DRAFT',
  sourceMrpRunId: 9001,
  sourceMrpSuggestionId: 9101,
  sourceSuggestionNo: 'MRP-SUG-027',
  allowedActions: ['UPDATE', 'RELEASE', 'CANCEL'],
  version: 6,
  createdByName: '管理员',
  createdAt: '2026-07-03T08:00:00+08:00',
  updatedAt: '2026-07-03T09:00:00+08:00',
}

const lockedProjectWorkOrder: ProjectProductionWorkOrderSummaryRecord = {
  ...projectWorkOrder,
  id: 28,
  workOrderNo: 'WO-PROJ-LOCKED',
  status: 'RELEASED',
  allowedActions: [],
  actionDisabledReason: '项目工单已被外协转换锁定',
  version: 7,
}

const projectWorkOrderPage: PageResult<ProjectProductionWorkOrderSummaryRecord> = {
  items: [projectWorkOrder, lockedProjectWorkOrder],
  page: 1,
  pageSize: 10,
  total: 2,
  totalPages: 1,
}

const emptyWorkOrderPage: PageResult<ProductionWorkOrderSummaryRecord> = {
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
}

async function setSelectValue(wrapper: VueWrapper, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index] as VueWrapper | undefined
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

function buttonsByText(wrapper: VueWrapper, text: string): VueWrapper[] {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

async function openMoreActions(wrapper: VueWrapper, index = 0) {
  await closeOpenDropdowns()
  const moreButtons = wrapper.findAll('button').filter((button) => button.text() === '更多')
  expect(moreButtons.length).toBeGreaterThan(index)
  await moreButtons[index].trigger('click')
  await flushPromises()
}

async function closeOpenDropdowns() {
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
  document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flushPromises()
}

function visibleDropdownPoppers() {
  return Array.from(document.body.querySelectorAll<HTMLElement>('.el-popper')).filter((popper) => {
    return popper.getAttribute('aria-hidden') !== 'true' && popper.style.display !== 'none'
  })
}

function findVisibleTeleportedAction(testId: string) {
  for (const popper of visibleDropdownPoppers()) {
    const action = popper.querySelector<HTMLElement>(`[data-test="${testId}"]`)
    if (action) {
      return action
    }
  }
  return null
}

async function waitForTeleportedAction(testId: string) {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const action = findVisibleTeleportedAction(testId)
    if (action) {
      return action
    }
    await flushPromises()
  }
  const action = findVisibleTeleportedAction(testId)
  expect(action).not.toBeNull()
  return action!
}

async function expectTeleportedMenuItem(testId: string, text: string) {
  const action = await waitForTeleportedAction(testId)
  expect(action.textContent?.trim()).toBe(text)
  expect(action.closest('[role="menuitem"]')).not.toBeNull()
  return action
}

async function clickTeleportedAction(wrapper: VueWrapper, testId: string, moreIndex = 0) {
  await openMoreActions(wrapper, moreIndex)
  ;(await waitForTeleportedAction(testId)).click()
  await flushPromises()
}

async function mountList(permissions = [
  'production:work-order:view',
  'production:work-order:create',
  'production:work-order:update',
  'production:work-order:release',
  'production:work-order:complete',
  'production:work-order:cancel',
  'production:issue:create',
  'production:report:create',
  'production:receipt:create',
], path = '/production/work-orders') {
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
      { path: '/production/work-orders', name: 'production-work-orders', component: ProductionWorkOrderListView },
      { path: '/production/work-orders/create', name: 'production-work-order-create', component: { render: () => null } },
      { path: '/production/work-orders/:id', name: 'production-work-order-detail', component: { render: () => null } },
      { path: '/production/work-orders/:id/edit', name: 'production-work-order-edit', component: { render: () => null } },
      { path: '/production/work-orders/:id/material-issues', name: 'production-work-order-material-issues', component: { render: () => null } },
      { path: '/production/work-orders/:id/reports', name: 'production-work-order-reports', component: { render: () => null } },
      { path: '/production/work-orders/:id/completion-receipts', name: 'production-work-order-completion-receipts', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(ProductionWorkOrderListView, {
    attachTo: document.body,
    global: {
      plugins: [pinia, router, qherpElementPlusPlugin],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('生产工单列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    productionApiMock.workOrders.list.mockResolvedValue(workOrderPage)
    productionApiMock.workOrders.release.mockResolvedValue(releasedWorkOrder)
    productionApiMock.workOrders.complete.mockResolvedValue(completedWorkOrder)
    productionApiMock.workOrders.cancel.mockResolvedValue({ ...draftWorkOrder, status: 'CANCELLED' })
    projectProductionApiMock.workOrders.list.mockResolvedValue(compatibleWorkOrderPage)
    projectProductionApiMock.workOrders.release.mockResolvedValue({ ...projectWorkOrder, status: 'RELEASED', version: 7 })
    projectProductionApiMock.workOrders.complete.mockResolvedValue({ ...projectWorkOrder, status: 'COMPLETED', version: 8 })
    projectProductionApiMock.workOrders.cancel.mockResolvedValue({ ...projectWorkOrder, status: 'CANCELLED', version: 8 })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('初始加载生产工单并使用默认分页参数', async () => {
    const { wrapper } = await mountList()

    expect(projectProductionApiMock.workOrders.list).toHaveBeenCalledWith({
      keyword: '',
      status: undefined,
      dateFrom: '',
      dateTo: '',
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('WO-DRAFT-001')
    expect(wrapper.text()).toContain('FG-001 成品 A')
    expect(wrapper.text()).toContain('BOM-FG-001 / V1')
    expect(wrapper.text()).toContain('100')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.find('[data-test="create-production-work-order"]').exists()).toBe(true)
  })

  it('支持按关键词、状态和计划日期筛选并重置', async () => {
    const { wrapper } = await mountList()

    await wrapper.find('input[name="production-work-order-keyword"]').setValue('WO')
    await setSelectValue(wrapper, 0, 'RELEASED')
    await wrapper.find('input[name="production-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="production-date-to"]').setValue('2026-07-31')
    await wrapper.find('[data-test="search-production-work-orders"]').trigger('click')
    await flushPromises()

    expect(projectProductionApiMock.workOrders.list).toHaveBeenLastCalledWith({
      keyword: 'WO',
      status: 'RELEASED',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      page: 1,
      pageSize: 10,
    })

    await wrapper.find('[data-test="reset-production-work-orders"]').trigger('click')
    await flushPromises()
    expect(projectProductionApiMock.workOrders.list).toHaveBeenLastCalledWith({
      keyword: '',
      status: undefined,
      dateFrom: '',
      dateTo: '',
      page: 1,
      pageSize: 10,
    })
  })

  it('无数据和加载失败时显示明确状态', async () => {
    projectProductionApiMock.workOrders.list.mockResolvedValueOnce({
      ...emptyWorkOrderPage,
      items: [],
    })
    const { wrapper } = await mountList()

    expect(wrapper.text()).toContain('暂无生产工单')

    projectProductionApiMock.workOrders.list.mockRejectedValueOnce(new Error('生产工单接口异常'))
    await wrapper.find('[data-test="search-production-work-orders"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('生产工单接口异常')
    expect(wrapper.text()).toContain('暂无生产工单')
  })

  it('只读权限仅展示查看入口，不展示写操作入口', async () => {
    const { wrapper } = await mountList(['production:work-order:view'])

    expect(wrapper.find('[data-test="create-production-work-order"]').exists()).toBe(false)
    expect(buttonsByText(wrapper, '详情')).toHaveLength(4)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '发布')).toHaveLength(0)
    expect(buttonsByText(wrapper, '领料')).toHaveLength(0)
    expect(buttonsByText(wrapper, '报工')).toHaveLength(0)
    expect(buttonsByText(wrapper, '完工入库')).toHaveLength(0)
    expect(buttonsByText(wrapper, '完成')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(0)
  })

  it('按工单状态展示行操作', async () => {
    const { wrapper } = await mountList()

    expect(buttonsByText(wrapper, '详情')).toHaveLength(4)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(1)
    expect(buttonsByText(wrapper, '更多')).toHaveLength(3)

    await openMoreActions(wrapper, 0)
    await expectTeleportedMenuItem('release-production-work-order', '发布')
    await expectTeleportedMenuItem('cancel-production-work-order', '取消')

    await openMoreActions(wrapper, 1)
    await expectTeleportedMenuItem('create-production-material-issue', '领料')
    await expectTeleportedMenuItem('create-production-report', '报工')
    await expectTeleportedMenuItem('create-production-completion-receipt', '完工入库')
    await expectTeleportedMenuItem('complete-production-work-order', '完成')
    await expectTeleportedMenuItem('cancel-production-work-order', '取消')
  })

  it('生产执行入口按权限和合法状态展示，不依赖工单级 allowedActions 或旧私有码', async () => {
    projectProductionApiMock.workOrders.list.mockResolvedValue({
      items: [
        {
          ...projectWorkOrder,
          id: 30,
          workOrderNo: 'WO-EXEC-REL',
          status: 'RELEASED',
          allowedActions: [],
        },
        {
          ...projectWorkOrder,
          id: 31,
          workOrderNo: 'WO-EXEC-DRAFT',
          status: 'DRAFT',
          allowedActions: ['CREATE_ISSUE', 'CREATE_REPORT', 'CREATE_RECEIPT'],
        },
      ],
      page: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
    })
    const { wrapper, router } = await mountList()

    expect(buttonsByText(wrapper, '更多')).toHaveLength(1)

    await clickTeleportedAction(wrapper, 'create-production-material-issue')
    expect(router.currentRoute.value.path).toBe('/production/work-orders/30/material-issues')
  })

  it('027 按项目、归属和来源筛选，并使用 allowedActions 与版本执行动作', async () => {
    projectProductionApiMock.workOrders.list.mockResolvedValue(projectWorkOrderPage)
    const { wrapper } = await mountList()

    expect(projectProductionApiMock.workOrders.list).toHaveBeenCalledWith({
      keyword: '',
      status: undefined,
      dateFrom: '',
      dateTo: '',
      page: 1,
      pageSize: 10,
    })
    expect(wrapper.text()).toContain('SP-027 销售项目 027')
    expect(wrapper.text()).toContain('项目工单')
    expect(wrapper.text()).toContain('MRP-SUG-027')
    expect(wrapper.text()).toContain('项目工单已被外协转换锁定')
    expect(buttonsByText(wrapper, '更多').length).toBeGreaterThan(0)

    await wrapper.find('input[name="production-project-id"]').setValue('3001')
    await setSelectValue(wrapper, 1, 'PROJECT')
    await wrapper.find('[data-test="search-production-work-orders"]').trigger('click')
    await flushPromises()

    expect(projectProductionApiMock.workOrders.list).toHaveBeenLastCalledWith({
      keyword: '',
      status: undefined,
      projectId: '3001',
      ownershipType: 'PROJECT',
      dateFrom: '',
      dateTo: '',
      page: 1,
      pageSize: 10,
    })

    await clickTeleportedAction(wrapper, 'release-production-work-order')

    expect(projectProductionApiMock.workOrders.release).toHaveBeenCalledWith(27, {
      version: 6,
      idempotencyKey: expect.stringMatching(/^production-work-order-release-/),
    })
  })

  it('027 从路由读取并监听项目与 MRP 来源筛选', async () => {
    projectProductionApiMock.workOrders.list.mockResolvedValue(projectWorkOrderPage)
    const { router } = await mountList([
      'production:work-order:view',
    ], '/production/work-orders?projectId=3001&sourceMrpSuggestionId=9101')

    expect(projectProductionApiMock.workOrders.list).toHaveBeenLastCalledWith(expect.objectContaining({
      projectId: '3001',
      sourceMrpSuggestionId: '9101',
    }))

    await router.push('/production/work-orders?projectId=3001&sourceMrpSuggestionId=9202')
    await flushPromises()
    expect(projectProductionApiMock.workOrders.list).toHaveBeenLastCalledWith(expect.objectContaining({
      projectId: '3001',
      sourceMrpSuggestionId: '9202',
    }))
  })
})
