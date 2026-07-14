import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { WarehouseRecord } from '../../shared/api/masterDataApi'
import type { ProductionWorkOrderDetailRecord } from '../../shared/api/productionApi'
import { useAuthStore } from '../../stores/authStore'
import TrackingAllocationEditor from '../inventory/tracking/TrackingAllocationEditor.vue'
import TrackingPickerDrawer from '../inventory/tracking/TrackingPickerDrawer.vue'
import ProductionCompletionReceiptView from './ProductionCompletionReceiptView.vue'
import ProductionMaterialIssueView from './ProductionMaterialIssueView.vue'
import ProductionWorkReportView from './ProductionWorkReportView.vue'
import { todayText } from './productionPageHelpers'

const productionApiMock = vi.hoisted(() => ({
  workOrders: {
    get: vi.fn(),
  },
  materialIssues: {
    create: vi.fn(),
  },
  reports: {
    create: vi.fn(),
  },
  completionReceipts: {
    create: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  materials: {
    get: vi.fn(),
  },
  warehouses: {
    list: vi.fn(),
  },
}))

const inventoryApiMock = vi.hoisted(() => ({
  batches: {
    list: vi.fn(),
  },
  serials: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/productionApi', () => ({
  productionApi: productionApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../shared/api/inventoryApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/inventoryApi')>()),
  inventoryApi: inventoryApiMock,
}))

const warehouse: WarehouseRecord = {
  id: 30,
  code: 'WH-RAW',
  name: '原料仓',
  status: 'ENABLED',
}

const spareWarehouse: WarehouseRecord = {
  id: 31,
  code: 'WH-SPARE',
  name: '备品仓',
  status: 'ENABLED',
}

const workOrder: ProductionWorkOrderDetailRecord = {
  id: 9,
  workOrderNo: 'WO-20260703-001',
  productMaterialId: 10,
  productMaterialCode: 'FG-001',
  productMaterialName: '成品 A',
  bomId: 20,
  bomCode: 'BOM-FG-001',
  bomVersionCode: 'V1',
  plannedQuantity: 100,
  reportedQuantity: 50,
  qualifiedQuantity: 40,
  defectiveQuantity: 10,
  receivedQuantity: 30,
  issueWarehouseId: 30,
  issueWarehouseName: '原料仓',
  receiptWarehouseId: 31,
  receiptWarehouseName: '成品仓',
  plannedStartDate: '2026-07-03',
  plannedFinishDate: '2026-07-10',
  status: 'IN_PROGRESS',
  remark: null,
  createdByName: '管理员',
  createdAt: '2026-07-03T08:00:00+08:00',
  updatedAt: '2026-07-03T09:00:00+08:00',
  completionValuationState: 'CURRENT_AVERAGE_PROVISIONAL',
  requiresManualProvisionalUnitCost: false,
  currentAverageUnitCost: '10.000000',
  costVisible: true,
  materials: [
    {
      id: 100,
      lineNo: 10,
      bomItemId: 200,
      materialId: 11,
      materialCode: 'RM-001',
      materialName: '原材料 A',
      materialType: 'RAW_MATERIAL',
      unitId: 3,
      unitName: '千克',
      requiredQuantity: 100,
      issuedQuantity: 60,
      remainingQuantity: 40,
      qualityStatus: 'PENDING_INSPECTION',
      qualityStatusName: '待检',
      quantityOnHand: '12.000000',
      reservedQuantity: '0.000000',
      occupiedQuantity: '0.000000',
      availableQuantity: '0.000000',
      availableToPromiseQuantity: '0.000000',
      selectable: false,
      disabledReasonCode: 'NON_QUALIFIED_NOT_AVAILABLE',
      disabledReason: '待检库存不可领料',
      maxSelectableQuantity: '0.000000',
      lossRate: 0,
      remark: null,
    },
  ],
  materialIssues: [],
  reports: [],
  completionReceipts: [],
  movements: [],
}

const manualProvisionalWorkOrder = {
  ...workOrder,
  completionValuationState: 'MANUAL_PROVISIONAL_REQUIRED',
  requiresManualProvisionalUnitCost: true,
  currentAverageUnitCost: null,
  costVisible: true,
} as ProductionWorkOrderDetailRecord

const currentAverageProvisionalWorkOrder = {
  ...workOrder,
  completionValuationState: 'CURRENT_AVERAGE_PROVISIONAL',
  requiresManualProvisionalUnitCost: false,
  currentAverageUnitCost: '11.000000',
  costVisible: true,
} as ProductionWorkOrderDetailRecord

const selectableWorkOrder: ProductionWorkOrderDetailRecord = {
  ...workOrder,
  materials: workOrder.materials.map((material) => ({
    ...material,
    qualityStatus: 'QUALIFIED',
    qualityStatusName: '合格',
    quantityOnHand: '50.000000',
    reservedQuantity: '6.000000',
    occupiedQuantity: '4.000000',
    availableQuantity: '40.000000',
    availableToPromiseQuantity: '45.000000',
    selectable: true,
    disabledReasonCode: null,
    disabledReason: null,
    maxSelectableQuantity: '40.000000',
  })),
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((innerResolve, innerReject) => {
    resolve = innerResolve
    reject = innerReject
  })
  return { promise, resolve, reject }
}

function buttonsByText(wrapper: VueWrapper, text: string): VueWrapper[] {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

function submitButton(wrapper: VueWrapper, text: string): VueWrapper {
  const button = buttonsByText(wrapper, text)[0]
  expect(button?.exists()).toBe(true)
  return button
}

function isDisabled(button: VueWrapper): boolean {
  return Boolean((button.props() as { disabled?: boolean }).disabled)
}

function inputValue(wrapper: VueWrapper, name: string): string {
  const input = wrapper.find<HTMLInputElement>(`input[name="${name}"]`)
  expect(input.exists()).toBe(true)
  return input.element.value
}

async function mountExecution(
  component: typeof ProductionMaterialIssueView | typeof ProductionWorkReportView | typeof ProductionCompletionReceiptView,
  path: string,
  permissions: string[],
) {
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
      { path: '/production/work-orders/:id', name: 'production-work-order-detail', component: { render: () => null } },
      { path: '/production/work-orders/:id/material-issues', name: 'production-work-order-material-issues', component },
      { path: '/production/work-orders/:id/reports', name: 'production-work-order-reports', component },
      { path: '/production/work-orders/:id/completion-receipts', name: 'production-work-order-completion-receipts', component },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('生产执行表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    productionApiMock.workOrders.get.mockResolvedValue(workOrder)
    productionApiMock.materialIssues.create.mockResolvedValue({ id: 1, issueNo: 'MI-001' })
    productionApiMock.reports.create.mockResolvedValue({ id: 2, reportNo: 'WR-001' })
    productionApiMock.completionReceipts.create.mockResolvedValue({ id: 3, receiptNo: 'CR-001' })
    masterDataApiMock.materials.get.mockImplementation((id: number) => Promise.resolve({
      id,
      code: id === 10 ? 'FG-001' : 'RM-001',
      name: id === 10 ? '成品 A' : '原材料 A',
      status: 'ENABLED',
      materialType: id === 10 ? 'FINISHED_GOOD' : 'RAW_MATERIAL',
      sourceType: id === 10 ? 'SELF_MADE' : 'PURCHASED',
      trackingMethod: 'BATCH',
      trackingMethodName: '批次管理',
      categoryId: 1,
      unitId: id === 10 ? 2 : 3,
    }))
    inventoryApiMock.batches.list.mockResolvedValue({
      items: [
        {
          id: 51,
          batchNo: 'B-RM-001',
          materialId: 11,
          materialCode: 'RM-001',
          materialName: '原材料 A',
          warehouseId: 30,
          warehouseName: '原料仓',
          qualityStatus: 'QUALIFIED',
          qualityStatusName: '合格',
          stockStatus: 'IN_STOCK',
          stockStatusName: '在库',
          quantityOnHand: '40.000000',
          availableQuantity: '40.000000',
          selectable: true,
          disabledReasonCode: null,
          disabledReason: null,
          updatedAt: '2026-07-05T09:00:00+08:00',
        },
      ],
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1,
    })
    inventoryApiMock.serials.list.mockResolvedValue({ items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 })
    masterDataApiMock.warehouses.list.mockResolvedValue({
      items: [warehouse, spareWarehouse],
      page: 1,
      pageSize: 200,
      total: 2,
      totalPages: 1,
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('三类执行表单默认业务日期使用本地日期而不是 UTC 日期', async () => {
    const chinaEarlyMorning = new Date('2026-07-03T00:30:00+08:00')
    vi.useFakeTimers()
    vi.setSystemTime(chinaEarlyMorning)

    expect(chinaEarlyMorning.toISOString().slice(0, 10)).toBe('2026-07-02')
    expect(todayText()).toBe('2026-07-03')

    const issue = await mountExecution(
      ProductionMaterialIssueView,
      '/production/work-orders/9/material-issues',
      ['production:work-order:view', 'production:issue:view', 'production:issue:create'],
    )
    const report = await mountExecution(
      ProductionWorkReportView,
      '/production/work-orders/9/reports',
      ['production:work-order:view', 'production:report:view', 'production:report:create'],
    )
    const receipt = await mountExecution(
      ProductionCompletionReceiptView,
      '/production/work-orders/9/completion-receipts',
      ['production:work-order:view', 'production:receipt:view', 'production:receipt:create'],
    )

    expect(inputValue(issue.wrapper, 'production-issue-date')).toBe('2026-07-03')
    expect(inputValue(report.wrapper, 'production-report-date')).toBe('2026-07-03')
    expect(inputValue(receipt.wrapper, 'production-receipt-date')).toBe('2026-07-03')
  })

  it('领料数量不能超过未领数量', async () => {
    productionApiMock.workOrders.get.mockResolvedValueOnce(selectableWorkOrder)
    const { wrapper } = await mountExecution(
      ProductionMaterialIssueView,
      '/production/work-orders/9/material-issues',
      ['production:work-order:view', 'production:issue:view', 'production:issue:create'],
    )

    await wrapper.find('input[placeholder="0.000000"]').setValue('40.000001')
    await submitButton(wrapper, '保存领料单').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('本次领料不能大于未领数量')
    expect(productionApiMock.materialIssues.create).not.toHaveBeenCalled()
  })

  it('领料数量不能超过后端返回的本次最多领料', async () => {
    productionApiMock.workOrders.get.mockResolvedValueOnce({
      ...selectableWorkOrder,
      materials: selectableWorkOrder.materials.map((material) => ({
        ...material,
        remainingQuantity: 40,
        maxSelectableQuantity: '12.000000',
      })),
    })
    const { wrapper } = await mountExecution(
      ProductionMaterialIssueView,
      '/production/work-orders/9/material-issues',
      ['production:work-order:view', 'production:issue:view', 'production:issue:create'],
    )

    await wrapper.find('input[placeholder="0.000000"]').setValue('12.000001')
    await submitButton(wrapper, '保存领料单').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('本次领料不能大于本次最多领料')
    expect(productionApiMock.materialIssues.create).not.toHaveBeenCalled()
  })

  it('生产领料候选行展示质量状态、现存、占用预留、可承诺、本次最多领料和禁用原因', async () => {
    const { wrapper } = await mountExecution(
      ProductionMaterialIssueView,
      '/production/work-orders/9/material-issues',
      ['production:work-order:view', 'production:issue:view', 'production:issue:create'],
    )

    expect(wrapper.text()).toContain('待检')
    expect(wrapper.text()).toContain('现存数量')
    expect(wrapper.text()).toContain('占用库存')
    expect(wrapper.text()).toContain('预留库存')
    expect(wrapper.text()).toContain('现货净可用')
    expect(wrapper.text()).toContain('可承诺量')
    expect(wrapper.text()).toContain('本次最多领料')
    expect(wrapper.text()).toContain('禁用原因')
    expect(wrapper.text()).toContain('待检库存不可领料')
    expect(wrapper.text()).not.toContain('canUse')
  })

  it('生产领料按工单领料仓库消耗预留，仓库不一致时阻止提交', async () => {
    productionApiMock.workOrders.get.mockResolvedValueOnce(selectableWorkOrder)
    const { wrapper } = await mountExecution(
      ProductionMaterialIssueView,
      '/production/work-orders/9/material-issues',
      ['production:work-order:view', 'production:issue:view', 'production:issue:create'],
    )

    expect(wrapper.text()).toContain('按工单领料仓库消耗预留')
    expect(wrapper.text()).toContain('原料仓')

    const warehouseSelect = wrapper.findComponent({ name: 'ElSelect' }) as VueWrapper
    warehouseSelect.vm.$emit('update:modelValue', 31)
    await flushPromises()
    await wrapper.find('input[placeholder="0.000000"]').setValue('2')
    await submitButton(wrapper, '保存领料单').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('第 10 行领料仓库必须与工单领料仓库一致，按工单领料仓库消耗预留')
    expect(productionApiMock.materialIssues.create).not.toHaveBeenCalled()
  })

  it('生产领料不可选候选行禁用数量输入并保留禁用原因', async () => {
    const { wrapper } = await mountExecution(
      ProductionMaterialIssueView,
      '/production/work-orders/9/material-issues',
      ['production:work-order:view', 'production:issue:view', 'production:issue:create'],
    )

    const quantityInput = wrapper.find('input[placeholder="0.000000"]')
    expect(quantityInput.attributes('disabled')).toBeDefined()
    await submitButton(wrapper, '保存领料单').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('待检库存不可领料')
    expect(productionApiMock.materialIssues.create).not.toHaveBeenCalled()
  })

  it('批次管理生产领料通过候选抽屉选择批次并提交', async () => {
    productionApiMock.workOrders.get.mockResolvedValueOnce(selectableWorkOrder)
    const { wrapper } = await mountExecution(
      ProductionMaterialIssueView,
      '/production/work-orders/9/material-issues',
      ['production:work-order:view', 'production:issue:view', 'production:issue:create'],
    )

    await wrapper.find('input[placeholder="0.000000"]').setValue('2.000000')
    await wrapper.find('[data-test="open-production-issue-tracking-0"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.batches.list).toHaveBeenCalledWith(expect.objectContaining({
      materialId: 11,
      warehouseId: 30,
      onlyAvailable: false,
    }))
    wrapper.findComponent(TrackingPickerDrawer).vm.$emit('select', {
      id: 51,
      trackingNo: 'B-RM-001',
      availableQuantity: '40.000000',
    })
    await flushPromises()
    await submitButton(wrapper, '保存领料单').trigger('click')
    await flushPromises()

    expect(productionApiMock.materialIssues.create).toHaveBeenCalledWith(9, expect.objectContaining({
      lines: [
        expect.objectContaining({
          workOrderMaterialId: 100,
          trackingAllocations: [{ batchId: 51, quantity: '2.000000' }],
        }),
      ],
    }))
  })

  it('报工累计不能超过计划数量', async () => {
    const { wrapper } = await mountExecution(
      ProductionWorkReportView,
      '/production/work-orders/9/reports',
      ['production:work-order:view', 'production:report:view', 'production:report:create'],
    )

    await wrapper.find('input[name="production-qualified-quantity"]').setValue('50.000001')
    await wrapper.find('input[name="production-defective-quantity"]').setValue('0')
    await submitButton(wrapper, '保存报工单').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('累计报工不能超过计划数量')
    expect(productionApiMock.reports.create).not.toHaveBeenCalled()
  })

  it('入库数量不能超过累计合格报工减已入库数量', async () => {
    const { wrapper } = await mountExecution(
      ProductionCompletionReceiptView,
      '/production/work-orders/9/completion-receipts',
      ['production:work-order:view', 'production:receipt:view', 'production:receipt:create'],
    )

    await wrapper.find('input[name="production-receipt-quantity"]').setValue('10.000001')
    await submitButton(wrapper, '保存入库单').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('入库数量不能超过累计合格报工减已入库数量')
    expect(productionApiMock.completionReceipts.create).not.toHaveBeenCalled()
  })

  it('批次管理完工入库显示追踪分配并提交', async () => {
    const { wrapper } = await mountExecution(
      ProductionCompletionReceiptView,
      '/production/work-orders/9/completion-receipts',
      ['production:work-order:view', 'production:receipt:view', 'production:receipt:create'],
    )

    await wrapper.find('input[name="production-receipt-quantity"]').setValue('5.000000')
    expect(wrapper.findComponent(TrackingAllocationEditor).exists()).toBe(true)
    wrapper.findComponent(TrackingAllocationEditor).vm.$emit('update:modelValue', [
      { batchNo: 'B-FG-001', quantity: '5.000000' },
    ])
    await flushPromises()
    await submitButton(wrapper, '保存入库单').trigger('click')
    await flushPromises()

    expect(productionApiMock.completionReceipts.create).toHaveBeenCalledWith(9, expect.objectContaining({
      quantity: '5.000000',
      trackingAllocations: [{ batchNo: 'B-FG-001', quantity: '5.000000' }],
    }))
  })

  it('首次完工无公共平均价时必须录入暂估单价并以字符串提交', async () => {
    productionApiMock.workOrders.get.mockResolvedValueOnce(manualProvisionalWorkOrder)
    const { wrapper } = await mountExecution(
      ProductionCompletionReceiptView,
      '/production/work-orders/9/completion-receipts',
      ['production:work-order:view', 'production:receipt:view', 'production:receipt:create'],
    )

    expect(wrapper.text()).toContain('首次完工需要录入暂估单价')
    expect(wrapper.text()).toContain('暂估库存后续出库价值不会被重写')
    await wrapper.find('input[name="production-receipt-quantity"]').setValue('5.000000')
    wrapper.findComponent(TrackingAllocationEditor).vm.$emit('update:modelValue', [
      { batchNo: 'B-FG-001', quantity: '5.000000' },
    ])
    await submitButton(wrapper, '保存入库单').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请输入暂估单价')
    expect(productionApiMock.completionReceipts.create).not.toHaveBeenCalled()

    await wrapper.find('input[name="production-receipt-provisional-unit-cost"]').setValue('12.345678')
    await submitButton(wrapper, '保存入库单').trigger('click')
    await flushPromises()

    expect(productionApiMock.completionReceipts.create).toHaveBeenCalledWith(9, expect.objectContaining({
      quantity: '5.000000',
      provisionalUnitCost: '12.345678',
    }))
  })

  it('暂估单价限制 6 位小数且已有公共平均价时只提示沿用平均价', async () => {
    productionApiMock.workOrders.get.mockResolvedValueOnce(manualProvisionalWorkOrder)
    const manual = await mountExecution(
      ProductionCompletionReceiptView,
      '/production/work-orders/9/completion-receipts',
      ['production:work-order:view', 'production:receipt:view', 'production:receipt:create'],
    )
    await manual.wrapper.find('input[name="production-receipt-provisional-unit-cost"]').setValue('12.3456789')
    await manual.wrapper.find('input[name="production-receipt-quantity"]').setValue('5.000000')
    manual.wrapper.findComponent(TrackingAllocationEditor).vm.$emit('update:modelValue', [
      { batchNo: 'B-FG-001', quantity: '5.000000' },
    ])
    await submitButton(manual.wrapper, '保存入库单').trigger('click')
    await flushPromises()

    expect(manual.wrapper.text()).toContain('暂估单价最多 6 位小数')
    expect(productionApiMock.completionReceipts.create).not.toHaveBeenCalled()

    productionApiMock.workOrders.get.mockResolvedValueOnce(currentAverageProvisionalWorkOrder)
    const average = await mountExecution(
      ProductionCompletionReceiptView,
      '/production/work-orders/9/completion-receipts',
      ['production:work-order:view', 'production:receipt:view', 'production:receipt:create'],
    )

    expect(average.wrapper.text()).toContain('沿用当前公共平均价')
    expect(average.wrapper.text()).toContain('11.000000')
    expect(average.wrapper.find('input[name="production-receipt-provisional-unit-cost"]').exists()).toBe(false)
  })

  it('无成本权限时完工入库不显示公共均价但仍按后端计价状态控制暂估输入', async () => {
    productionApiMock.workOrders.get.mockResolvedValueOnce({
      ...currentAverageProvisionalWorkOrder,
      costVisible: false,
    })
    const { wrapper } = await mountExecution(
      ProductionCompletionReceiptView,
      '/production/work-orders/9/completion-receipts',
      ['production:work-order:view', 'production:receipt:view', 'production:receipt:create'],
    )

    expect(wrapper.text()).not.toContain('沿用当前公共平均价')
    expect(wrapper.text()).not.toContain('11.000000')
    expect(wrapper.find('input[name="production-receipt-provisional-unit-cost"]').exists()).toBe(false)
  })

  it('批次管理完工入库追踪数量合计不一致时阻止保存', async () => {
    const { wrapper } = await mountExecution(
      ProductionCompletionReceiptView,
      '/production/work-orders/9/completion-receipts',
      ['production:work-order:view', 'production:receipt:view', 'production:receipt:create'],
    )

    await wrapper.find('input[name="production-receipt-quantity"]').setValue('5.000000')
    wrapper.findComponent(TrackingAllocationEditor).vm.$emit('update:modelValue', [
      { batchNo: 'B-FG-001', quantity: '3.000000' },
    ])
    await flushPromises()
    await submitButton(wrapper, '保存入库单').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('追踪分配')
    expect(wrapper.text()).toContain('与业务数量')
    expect(productionApiMock.completionReceipts.create).not.toHaveBeenCalled()
  })

  it('后端错误消息可见，提交期间按钮禁用，失败后恢复', async () => {
    const pending = deferred<unknown>()
    productionApiMock.reports.create.mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountExecution(
      ProductionWorkReportView,
      '/production/work-orders/9/reports',
      ['production:work-order:view', 'production:report:view', 'production:report:create'],
    )

    await wrapper.find('input[name="production-qualified-quantity"]').setValue('10')
    await submitButton(wrapper, '保存报工单').trigger('click')
    await nextTick()

    expect(isDisabled(submitButton(wrapper, '保存报工单'))).toBe(true)

    pending.reject(new Error('累计报工超过计划数量'))
    await flushPromises()

    expect(wrapper.text()).toContain('累计报工超过计划数量')
    expect(isDisabled(submitButton(wrapper, '保存报工单'))).toBe(false)
  })

  it('无生产领料创建权限时禁用表单并阻止提交', async () => {
    const { wrapper } = await mountExecution(
      ProductionMaterialIssueView,
      '/production/work-orders/9/material-issues',
      ['production:work-order:view', 'production:issue:view'],
    )

    expect(wrapper.text()).toContain('缺少生产领料创建权限')
    expect(isDisabled(submitButton(wrapper, '保存领料单'))).toBe(true)

    await submitButton(wrapper, '保存领料单').trigger('click')
    await flushPromises()

    expect(productionApiMock.materialIssues.create).not.toHaveBeenCalled()
  })

  it('无生产报工创建权限时禁用表单并阻止提交', async () => {
    const { wrapper } = await mountExecution(
      ProductionWorkReportView,
      '/production/work-orders/9/reports',
      ['production:work-order:view', 'production:report:view'],
    )

    expect(wrapper.text()).toContain('缺少生产报工创建权限')
    expect(isDisabled(submitButton(wrapper, '保存报工单'))).toBe(true)

    await submitButton(wrapper, '保存报工单').trigger('click')
    await flushPromises()

    expect(productionApiMock.reports.create).not.toHaveBeenCalled()
  })

  it('无完工入库创建权限时禁用表单并阻止提交', async () => {
    const { wrapper } = await mountExecution(
      ProductionCompletionReceiptView,
      '/production/work-orders/9/completion-receipts',
      ['production:work-order:view', 'production:receipt:view'],
    )

    expect(wrapper.text()).toContain('缺少完工入库创建权限')
    expect(isDisabled(submitButton(wrapper, '保存入库单'))).toBe(true)

    await submitButton(wrapper, '保存入库单').trigger('click')
    await flushPromises()

    expect(productionApiMock.completionReceipts.create).not.toHaveBeenCalled()
  })
})
