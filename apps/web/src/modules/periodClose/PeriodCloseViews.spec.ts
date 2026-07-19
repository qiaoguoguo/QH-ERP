import { defineComponent, h, inject, provide, type Component } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useConfirmActionMock } from '../../test/setup'
import { useAuthStore } from '../../stores/authStore'
import PeriodCloseWorkbenchView from './PeriodCloseWorkbenchView.vue'
import PeriodCloseRunDetailView from './PeriodCloseRunDetailView.vue'
import PeriodCloseCheckDetailView from './PeriodCloseCheckDetailView.vue'
import PeriodCloseSnapshotView from './PeriodCloseSnapshotView.vue'
import type {
  BusinessPeriodCloseCheckItem,
  BusinessPeriodCloseRunDetail,
  BusinessPeriodCloseRunRecord,
  BusinessPeriodCloseSnapshotInventoryRecord,
  BusinessPeriodCloseSnapshotProjectCostRecord,
  BusinessPeriodCloseSnapshotWipRecord,
} from '../../shared/api/businessPeriodCloseApi'

const confirmActionMock = useConfirmActionMock()

const apiMock = vi.hoisted(() => ({
  periods: {
    getSummary: vi.fn(),
  },
  runs: {
    list: vi.fn(),
    get: vi.fn(),
    close: vi.fn(),
    reopen: vi.fn(),
  },
  checks: {
    create: vi.fn(),
    history: vi.fn(),
    items: vi.fn(),
  },
  snapshots: {
    get: vi.fn(),
    inventory: vi.fn(),
    wip: vi.fn(),
    projectCosts: vi.fn(),
    report: vi.fn(),
  },
}))

function apiResponse<T>(data: T) {
  return {
    ok: true,
    status: 200,
    json: async () => ({
      success: true,
      code: 'OK',
      message: '成功',
      data,
      traceId: 'trace-period-close-view',
    }),
  } as Response
}

vi.mock('../../shared/api/businessPeriodCloseApi', () => ({
  businessPeriodCloseApi: apiMock,
}))

vi.mock('../../shared/api/documentPlatformApi', () => ({
  createIdempotencyKey: (prefix = 'period-close') => `${prefix}-key`,
}))

const readyRun: BusinessPeriodCloseRunRecord = {
  runId: 11,
  periodId: 7,
  periodCode: '2026-07',
  periodName: '2026年07月',
  startDate: '2026-07-01',
  endDate: '2026-07-31',
  periodStatus: 'OPEN',
  periodStatusName: '开放',
  closeStatus: 'READY',
  closeStatusName: '可月结',
  revisionNo: 1,
  latestCheckId: 21,
  latestCheckedAt: '2026-07-31T22:00:00+08:00',
  latestCheckResult: 'WARNING',
  blockingCount: 0,
  warningCount: 2,
  snapshotId: null,
  snapshotValueAmount: '838.000000',
  amountVisible: true,
  sourceVisible: true,
  restrictedReason: null,
  version: 5,
  sourceFingerprint: 'fp-030',
  allowedActions: ['CHECK', 'CLOSE'],
  actionDisabledReasons: {},
}

const blockedRun: BusinessPeriodCloseRunRecord = {
  ...readyRun,
  runId: 12,
  periodId: 8,
  periodCode: '2026-08',
  closeStatus: 'BLOCKED',
  closeStatusName: '检查未通过',
  blockingCount: 3,
  warningCount: 1,
  amountVisible: false,
  sourceVisible: false,
  restrictedReason: '无权查看成本金额',
  snapshotValueAmount: null,
  allowedActions: ['CHECK'],
  actionDisabledReasons: { CLOSE: '存在阻断项，不能月结' },
}

const closedDetail: BusinessPeriodCloseRunDetail = {
  ...readyRun,
  closeStatus: 'CLOSED',
  closeStatusName: '已月结',
  periodStatus: 'LOCKED',
  periodStatusName: '已锁定',
  snapshotId: 81,
  closedByName: '财务主管',
  closedAt: '2026-07-31T23:30:00+08:00',
  allowedActions: ['REOPEN', 'SNAPSHOT_VIEW'],
  historyVersions: [{ runId: 11, revisionNo: 1, closeStatus: 'CLOSED', closedAt: '2026-07-31T23:30:00+08:00' }],
  auditSummary: [{ action: 'CLOSE', operatorName: '财务主管', reason: '警告已确认', createdAt: '2026-07-31T23:30:00+08:00' }],
}

const checkItems: BusinessPeriodCloseCheckItem[] = [
  {
    id: 31,
    checkRunId: 21,
    domain: 'INVENTORY',
    severity: 'BLOCKING',
    checkCode: 'INVENTORY_UNVALUED_SOURCE',
    title: '存在未估值库存来源',
    description: '库存价值来源断链',
    objectType: 'INVENTORY_SOURCE',
    objectNo: 'INV-001',
    businessImpact: '期末库存价值不可信',
    suggestion: '完成库存估值后重新检查',
    sourceRoute: { path: '/inventory/movements', query: { sourceNo: 'INV-001' } },
    amountVisible: true,
    sourceVisible: true,
    restrictedReason: null,
  },
  {
    id: 32,
    checkRunId: 21,
    domain: 'PROJECT_COST',
    severity: 'WARNING',
    checkCode: 'PROJECT_HAS_PROVISIONAL_COST',
    title: '存在合法暂估项目成本',
    description: '受限来源摘要',
    objectType: 'PROJECT',
    objectNo: null,
    businessImpact: '毛利仍可解释但需确认',
    suggestion: '确认暂估依据并填写关闭原因',
    sourceRoute: null,
    amountVisible: false,
    sourceVisible: false,
    restrictedReason: '来源权限受限，仅显示脱敏摘要',
  },
]

const inventorySnapshot: BusinessPeriodCloseSnapshotInventoryRecord = {
  id: 801,
  materialCode: 'MAT-001',
  materialName: '铜件',
  warehouseName: '一号仓',
  endingQuantity: '12.000000',
  lockedQuantity: '1.000000',
  availableQuantity: '11.000000',
  unitCost: null,
  endingValue: null,
  amountVisible: false,
  sourceVisible: false,
  restrictedReason: '无权查看库存金额',
}

const wipSnapshot: BusinessPeriodCloseSnapshotWipRecord = {
  id: 901,
  projectNo: 'SP-001',
  projectName: '华东扩产项目',
  workOrderNo: 'WO-001',
  materialCode: 'MAT-001',
  materialName: '铜件',
  stage: '在制',
  wipQuantity: '2.000000',
  wipAmount: '80.000000',
  amountVisible: true,
  sourceVisible: true,
}

const projectCostSnapshot: BusinessPeriodCloseSnapshotProjectCostRecord = {
  id: 1001,
  projectId: 12,
  projectNo: 'SP-001',
  projectName: '华东扩产项目',
  calculationId: 91,
  calculationNo: 'PCC-001',
  cutoffDate: '2026-07-31',
  totalCost: '838.000000',
  wipCost: '80.000000',
  deliveredCost: '758.000000',
  revenueAmount: '10000.000000',
  grossMarginAmount: '9162.000000',
  grossMarginRate: '0.916200',
  amountVisible: true,
  sourceVisible: true,
}

function page<T>(items: T[], pageSize = 10) {
  return { items, total: items.length, page: 1, pageSize, totalPages: 1 }
}

function setupAuth(permissions = [
  'system:business-period-close:view',
  'system:business-period-close:check',
  'system:business-period-close:close',
  'system:business-period-close:reopen',
  'system:business-period-close:snapshot-view',
]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'period_close_user', displayName: '月结用户', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return pinia
}

async function mountWithRoute(component: Component, path: string, routes?: RouteRecordRaw[]) {
  const pinia = setupAuth()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: routes ?? [routeForPath(path, component)],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router],
      stubs: {
        ...elementStubs,
        teleport: true,
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

const tableDataKey = Symbol('period-close-table-data')

const ElTableStub = defineComponent({
  name: 'ElTable',
  props: {
    data: { type: Array, default: () => [] },
    emptyText: { type: String, default: '暂无数据' },
  },
  setup(props, { slots }) {
    provide(tableDataKey, props)
    return () => h('div', { class: 'el-table' }, props.data.length ? slots.default?.() : props.emptyText)
  },
})

const ElTableColumnStub = defineComponent({
  name: 'ElTableColumn',
  props: {
    label: { type: String, default: '' },
    prop: { type: String, default: '' },
  },
  setup(props, { slots }) {
    const tableProps = inject<{ data: unknown[] }>(tableDataKey, { data: [] })
    return () => h('section', { class: 'el-table-column' }, [
      h('span', props.label),
      ...tableProps.data.map((row) => {
        const record = row as Record<string, unknown>
        return h('div', { class: 'el-table-cell' }, slots.default ? slots.default({ row }) : String(record[props.prop] ?? ''))
      }),
    ])
  },
})

const ElPaginationStub = defineComponent({
  name: 'ElPagination',
  props: {
    pageSizes: { type: Array, default: () => [] },
    total: { type: Number, default: 0 },
    pageSize: { type: Number, default: 10 },
    currentPage: { type: Number, default: 1 },
  },
  emits: ['current-change', 'size-change'],
  template: '<nav class="el-pagination"><slot />分页 {{ total }}</nav>',
})

const elementStubs = {
  ElAlert: { name: 'ElAlert', props: ['title'], template: '<div class="el-alert">{{ title }}<slot /></div>' },
  ElButton: { name: 'ElButton', props: ['disabled', 'loading'], emits: ['click'], template: '<button v-bind="$attrs" :disabled="disabled || loading" @click="$emit(\'click\', $event)"><slot /></button>' },
  ElCard: { name: 'ElCard', template: '<section class="el-card"><slot /></section>' },
  ElDatePicker: { name: 'ElDatePicker', props: ['modelValue', 'name'], emits: ['update:modelValue'], template: '<input :name="name" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)">' },
  ElDialog: { name: 'ElDialog', props: ['modelValue', 'title'], emits: ['update:modelValue'], template: '<section v-if="modelValue" class="el-dialog"><h2>{{ title }}</h2><slot /><footer><slot name="footer" /></footer></section>' },
  ElDrawer: { name: 'ElDrawer', props: ['modelValue', 'title'], emits: ['update:modelValue'], template: '<aside v-if="modelValue" class="el-drawer"><h2>{{ title }}</h2><slot /></aside>' },
  ElEmpty: { name: 'ElEmpty', props: ['description'], template: '<p class="el-empty">{{ description }}</p>' },
  ElForm: { name: 'ElForm', template: '<form><slot /></form>' },
  ElFormItem: { name: 'ElFormItem', props: ['label'], template: '<label><span>{{ label }}</span><slot /></label>' },
  ElInput: { name: 'ElInput', props: ['modelValue', 'name', 'type'], emits: ['update:modelValue'], template: '<textarea v-if="type === \'textarea\'" :name="name" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /><input v-else :name="name" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)">' },
  ElOption: { name: 'ElOption', props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  ElSelect: { name: 'ElSelect', props: ['modelValue'], emits: ['update:modelValue'], template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>' },
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
  ElPagination: ElPaginationStub,
  ElTag: { name: 'ElTag', template: '<span class="el-tag"><slot /></span>' },
}

function routeForPath(path: string, component: Component): RouteRecordRaw {
  if (path.includes('/snapshot')) {
    return { path: '/period-close/runs/:runId/snapshot', name: 'period-close-run-snapshot', component }
  }
  if (path.includes('/checks/')) {
    return { path: '/period-close/runs/:runId/checks/:checkId', name: 'period-close-check-detail', component }
  }
  if (path.startsWith('/period-close/runs/') && path !== '/period-close/runs') {
    return { path: '/period-close/runs/:runId', name: 'period-close-run-detail', component }
  }
  return { path, name: 'period-close-runs', component }
}

describe('业务月结页面族', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    confirmActionMock.mockResolvedValue(true)
    apiMock.runs.list.mockResolvedValue(page([readyRun, blockedRun]))
    apiMock.runs.get.mockResolvedValue({ ...readyRun, historyVersions: [], auditSummary: [] })
    apiMock.runs.close.mockResolvedValue(closedDetail)
    apiMock.runs.reopen.mockResolvedValue({ ...closedDetail, closeStatus: 'REOPENED', closeStatusName: '已重开' })
    apiMock.checks.create.mockResolvedValue({ ...blockedRun, historyVersions: [], auditSummary: [] })
    apiMock.checks.history.mockResolvedValue(page([{ checkRunId: 21, runId: 11, blockingCount: 0, warningCount: 2 }]))
    apiMock.checks.items.mockResolvedValue(page(checkItems))
    apiMock.snapshots.get.mockResolvedValue({
      snapshotId: 81,
      runId: 11,
      periodCode: '2026-07',
      revisionNo: 1,
      generatedAt: '2026-07-31T23:30:00+08:00',
      sourceCheckRunId: 21,
      sourceFingerprint: 'fp-030',
      isHistoricalRevision: false,
      partitions: [
        { code: 'INVENTORY', name: '库存快照', amountVisible: true, sourceVisible: true },
        { code: 'WIP', name: '在制/生产', amountVisible: true, sourceVisible: true },
        { code: 'PROJECT_COST', name: '项目成本', amountVisible: true, sourceVisible: true },
        { code: 'REPORTS', name: '经营报表基线', amountVisible: false, sourceVisible: false, restrictedReason: '报表来源权限受限' },
      ],
    })
    apiMock.snapshots.inventory.mockResolvedValue(page([inventorySnapshot]))
    apiMock.snapshots.wip.mockResolvedValue(page([wipSnapshot]))
    apiMock.snapshots.projectCosts.mockResolvedValue(page([projectCostSnapshot]))
    apiMock.snapshots.report.mockResolvedValue({
      reportCode: 'OVERVIEW',
      reportName: '经营概览',
      schemaVersion: 1,
      generatedAt: '2026-07-31T23:30:00+08:00',
      sourceCount: 6,
      amountVisible: false,
      sourceVisible: false,
      restrictedReason: '报表来源权限受限',
      result: { revenue: null, exceptionCount: 2 },
    })
  })

  it('工作台展示四项指标、筛选分页、金额脱敏和 allowedActions 检查动作', async () => {
    const { wrapper } = await mountWithRoute(PeriodCloseWorkbenchView, '/period-close/runs')

    expect(wrapper.text()).toContain('业务月结')
    expect(wrapper.text()).toContain('不是财务关账')
    expect(wrapper.text()).toContain('待检查')
    expect(wrapper.text()).toContain('检查未通过')
    expect(wrapper.text()).toContain('可月结')
    expect(wrapper.text()).toContain('已月结')
    expect(wrapper.text()).toContain('2026-07')
    expect(wrapper.text()).toContain('838.00')
    expect(wrapper.text()).toContain('无权查看成本金额')
    expect(wrapper.findAll('[data-test="period-close-run-check"]')).toHaveLength(2)
    expect(wrapper.find('[data-test="period-close-run-close"]').exists()).toBe(false)

    await wrapper.find('input[name="period-close-period-code"]').setValue('2026')
    await wrapper.find('input[name="period-close-start-date"]').setValue('2026-07-01')
    await wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('size-change', 20)
    await flushPromises()
    await wrapper.find('[data-test="period-close-search"]').trigger('click')
    await flushPromises()

    expect(apiMock.runs.list).toHaveBeenLastCalledWith(expect.objectContaining({
      periodCode: '2026',
      startDate: '2026-07-01',
      endDate: '',
      page: 1,
      pageSize: 20,
    }))

    await wrapper.find('[data-test="period-close-reset"]').trigger('click')
    await flushPromises()
    expect(apiMock.runs.list).toHaveBeenLastCalledWith(expect.objectContaining({ periodCode: '', startDate: '', endDate: '', page: 1 }))

    await wrapper.find('[data-test="period-close-run-check"]').trigger('click')
    await flushPromises()
    expect(apiMock.checks.create).toHaveBeenCalledWith({ periodId: 7, idempotencyKey: 'period-close-check-key' })
  })

  it('工作台检查结果和阻断筛选会随查询一起提交并重置回第一页', async () => {
    const { wrapper } = await mountWithRoute(PeriodCloseWorkbenchView, '/period-close/runs')

    expect(wrapper.text()).toContain('阻断筛选')
    const selects = wrapper.findAllComponents({ name: 'ElSelect' })
    selects[1].vm.$emit('update:modelValue', 'WARNING')
    selects[2].vm.$emit('update:modelValue', true)
    await flushPromises()
    await wrapper.find('[data-test="period-close-search"]').trigger('click')
    await flushPromises()

    expect(apiMock.runs.list).toHaveBeenLastCalledWith(expect.objectContaining({
      checkResult: 'WARNING',
      hasBlocking: true,
      page: 1,
      pageSize: 10,
    }))
  })

  it('运行详情只按 allowedActions 和权限展示关闭/重开，并用统一高风险确认处理 409', async () => {
    apiMock.runs.get.mockResolvedValueOnce({ ...readyRun, historyVersions: [], auditSummary: [] })
    apiMock.runs.close.mockRejectedValueOnce(Object.assign(new Error('来源已变化，请重新检查后再关闭。'), { status: 409 }))
    const { wrapper } = await mountWithRoute(PeriodCloseRunDetailView, '/period-close/runs/11')

    expect(wrapper.text()).toContain('月结详情')
    expect(wrapper.text()).toContain('2026-07')
    expect(wrapper.text()).toContain('版本')
    expect(wrapper.find('[data-test="period-close-close"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="period-close-reopen"]').exists()).toBe(false)

    await wrapper.find('[data-test="period-close-close"]').trigger('click')
    await flushPromises()
    await wrapper.find('textarea[name="period-close-close-reason"]').setValue('警告已确认，关闭 2026-07')
    await wrapper.find('[data-test="period-close-warning-ack"]').setValue(true)
    await wrapper.find('[data-test="submit-period-close-close"]').trigger('click')
    await flushPromises()

    expect(confirmActionMock).toHaveBeenCalledWith(
      expect.stringContaining('将锁定业务期间'),
      expect.objectContaining({ type: 'warning', risk: 'period-close-close' }),
    )
    expect(apiMock.runs.close).toHaveBeenCalledWith(11, {
      version: 5,
      sourceFingerprint: 'fp-030',
      warningAcknowledged: true,
      reason: '警告已确认，关闭 2026-07',
      idempotencyKey: 'period-close-close-key',
    })
    expect(wrapper.text()).toContain('来源已变化，请重新检查后再关闭。')
    expect(wrapper.text()).toContain('请刷新详情或重新检查后再执行高风险动作')
  })

  it('运行详情完整消费 allowedActions 并在原因或警告确认未满足时禁用高风险提交', async () => {
    apiMock.runs.get.mockResolvedValueOnce({ ...readyRun, historyVersions: [], auditSummary: [] })
    const { wrapper } = await mountWithRoute(PeriodCloseRunDetailView, '/period-close/runs/11')

    await wrapper.find('[data-test="period-close-close"]').trigger('click')
    await flushPromises()

    const submitClose = wrapper.find('[data-test="submit-period-close-close"]')
    expect(submitClose.attributes('disabled')).toBeDefined()

    await wrapper.find('textarea[name="period-close-close-reason"]').setValue('警告已确认，关闭 2026-07')
    await flushPromises()
    expect(submitClose.attributes('disabled')).toBeDefined()

    await wrapper.find('[data-test="period-close-warning-ack"]').setValue(true)
    await flushPromises()
    expect(submitClose.attributes('disabled')).toBeUndefined()

    apiMock.runs.get.mockResolvedValueOnce({
      ...closedDetail,
      allowedActions: ['REOPEN'],
      actionDisabledReasons: { SNAPSHOT_VIEW: '缺少业务月结快照查看权限' },
    })
    const { wrapper: restrictedWrapper } = await mountWithRoute(PeriodCloseRunDetailView, '/period-close/runs/11')
    expect(restrictedWrapper.find('[data-test="period-close-snapshot-link"]').exists()).toBe(false)
  })

  it('检查详情按领域分组展示阻断/警告、受限来源摘要和 returnTo 来源追溯', async () => {
    const { wrapper, router } = await mountWithRoute(
      PeriodCloseCheckDetailView,
      '/period-close/runs/11/checks/21',
      [{ path: '/period-close/runs/:runId/checks/:checkId', component: PeriodCloseCheckDetailView }],
    )

    expect(apiMock.checks.items).toHaveBeenCalledWith('11', '21', { page: 1, pageSize: 10 })
    expect(wrapper.text()).toContain('检查运行详情')
    expect(wrapper.text()).toContain('库存计价')
    expect(wrapper.text()).toContain('项目成本')
    expect(wrapper.text()).toContain('存在未估值库存来源')
    expect(wrapper.text()).toContain('来源权限受限，仅显示脱敏摘要')

    expect(wrapper.findAll('[data-test="period-close-source-trace"]')).toHaveLength(1)

    await wrapper.find('[data-test="period-close-source-trace"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('来源追溯')
    expect(wrapper.text()).toContain('INV-001')

    await wrapper.find('[data-test="period-close-source-route"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/inventory/movements')
    expect(router.currentRoute.value.query.returnTo).toBe('/period-close/runs/11/checks/21')
  })

  it('快照详情全只读展示库存、在制、项目成本和八类报表基线，分页保持默认选项', async () => {
    const { wrapper } = await mountWithRoute(
      PeriodCloseSnapshotView,
      '/period-close/runs/11/snapshot',
      [{ path: '/period-close/runs/:runId/snapshot', component: PeriodCloseSnapshotView }],
    )

    expect(wrapper.text()).toContain('期间快照详情')
    expect(wrapper.text()).toContain('冻结期间')
    expect(wrapper.text()).toContain('2026-07')
    expect(wrapper.text()).toContain('库存快照')
    expect(wrapper.text()).toContain('在制/生产')
    expect(wrapper.text()).toContain('项目成本')
    expect(wrapper.text()).toContain('经营概览')
    expect(wrapper.text()).toContain('销售汇总')
    expect(wrapper.text()).toContain('采购汇总')
    expect(wrapper.text()).toContain('库存收发存')
    expect(wrapper.text()).toContain('生产执行')
    expect(wrapper.text()).toContain('成本归集')
    expect(wrapper.text()).toContain('往来结算')
    expect(wrapper.text()).toContain('异常清单')
    expect(wrapper.text()).toContain('无权查看库存金额')
    expect(wrapper.text()).toContain('0.916200')
    expect(wrapper.find('[data-test="period-close-close"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="period-close-snapshot-edit"]').exists()).toBe(false)

    const pagers = wrapper.findAllComponents({ name: 'ElPagination' })
    expect(pagers[0].props('pageSizes')).toEqual([10, 20, 50, 100])
    pagers[0].vm.$emit('size-change', 20)
    await flushPromises()
    expect(apiMock.snapshots.inventory).toHaveBeenLastCalledWith('11', { page: 1, pageSize: 20 })
  })

  it('快照详情通过真实 envelope 显示后端 itemCount 分区数量', async () => {
    const actual = await vi.importActual<typeof import('../../shared/api/businessPeriodCloseApi')>(
      '../../shared/api/businessPeriodCloseApi',
    )
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({
      snapshotId: 81,
      runId: 11,
      periodCode: '2026-07',
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      revisionNo: 1,
      generatedAt: '2026-07-31T23:30:00+08:00',
      sourceCheckRunId: 21,
      sourceFingerprint: 'fp-030',
      isHistoricalRevision: false,
      partitions: [
        { code: 'INVENTORY', name: '库存快照', itemCount: 0, amountVisible: true, sourceVisible: true },
        { code: 'REPORTS', name: '经营报表基线', itemCount: 8, amountVisible: true, sourceVisible: true },
      ],
    }))
    apiMock.snapshots.get.mockImplementationOnce(() =>
      actual.createBusinessPeriodCloseApi({ fetcher }).snapshots.get('11'),
    )

    const { wrapper } = await mountWithRoute(
      PeriodCloseSnapshotView,
      '/period-close/runs/11/snapshot',
      [{ path: '/period-close/runs/:runId/snapshot', component: PeriodCloseSnapshotView }],
    )

    expect(wrapper.text()).toContain('经营报表基线 · 8 条')
    expect(fetcher).toHaveBeenCalledWith('/api/admin/period-closes/11/snapshot', expect.objectContaining({
      method: 'GET',
    }))
  })

  it('快照详情对已重开历史快照和合法空报表显示稳定中文空态', async () => {
    apiMock.snapshots.get.mockResolvedValueOnce({
      snapshotId: 82,
      runId: 11,
      periodCode: '2026-07',
      revisionNo: 1,
      generatedAt: '2026-07-31T23:30:00+08:00',
      sourceCheckRunId: 21,
      sourceFingerprint: 'fp-030',
      isHistoricalRevision: true,
      partitions: [],
    })
    apiMock.snapshots.inventory.mockResolvedValueOnce(page([]))
    apiMock.snapshots.wip.mockResolvedValueOnce(page([]))
    apiMock.snapshots.projectCosts.mockResolvedValueOnce(page([]))
    apiMock.snapshots.report.mockResolvedValueOnce({
      reportCode: 'OVERVIEW',
      reportName: '经营概览',
      schemaVersion: 1,
      generatedAt: '2026-07-31T23:30:00+08:00',
      sourceCount: 0,
      amountVisible: true,
      sourceVisible: true,
      result: {},
    })

    const { wrapper } = await mountWithRoute(
      PeriodCloseSnapshotView,
      '/period-close/runs/11/snapshot',
      [{ path: '/period-close/runs/:runId/snapshot', component: PeriodCloseSnapshotView }],
    )

    expect(wrapper.text()).toContain('已重开历史快照')
    expect(wrapper.text()).toContain('该冻结报表本期无明细数据')
    expect(wrapper.text()).not.toContain('{}')
  })
})
