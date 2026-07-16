import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { AccountPermissionApiError } from '../../shared/api/accountPermissionApi'
import { useAuthStore } from '../../stores/authStore'
import InventoryControlledDocumentView from './InventoryControlledDocumentView.vue'

const inventoryApiMock = vi.hoisted(() => ({
  batches: {
    list: vi.fn(),
  },
  serials: {
    list: vi.fn(),
  },
  costLayers: {
    list: vi.fn(),
  },
  warehouseTransfers: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    post: vi.fn(),
    cancel: vi.fn(),
  },
  ownershipConversions: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    submitApproval: vi.fn(),
    withdraw: vi.fn(),
    cancel: vi.fn(),
  },
  stocktakes: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    start: vi.fn(),
    listLines: vi.fn(),
    updateLines: vi.fn(),
    reconcile: vi.fn(),
    submitApproval: vi.fn(),
    completeZeroVariance: vi.fn(),
    cancel: vi.fn(),
  },
  valuationAdjustments: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    submitApproval: vi.fn(),
    withdraw: vi.fn(),
    cancel: vi.fn(),
  },
}))

vi.mock('../../shared/api/inventoryApi', () => ({
  inventoryApi: inventoryApiMock,
}))

const masterDataApiMock = vi.hoisted(() => ({
  warehouses: { list: vi.fn() },
  materials: { list: vi.fn() },
  units: { list: vi.fn() },
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const salesProjectApiMock = vi.hoisted(() => ({
  projects: { list: vi.fn() },
}))

vi.mock('../../shared/api/salesProjectApi', () => ({
  salesProjectApi: salesProjectApiMock,
}))

const transferRecord = {
  id: 1001,
  documentNo: 'WT-001',
  status: 'DRAFT',
  statusName: '草稿',
  businessDate: '2026-07-14',
  reason: '仓间补货',
  lineCount: 1,
  version: 3,
  allowedActions: ['UPDATE', 'POST', 'CANCEL'],
  createdByName: '管理员',
  createdAt: '2026-07-14T09:00:00+08:00',
  updatedAt: '2026-07-14T09:30:00+08:00',
  lines: [{
    id: 2001,
    lineNo: 10,
    sourceWarehouseId: 1,
    sourceWarehouseName: '原料仓',
    targetWarehouseId: 2,
    targetWarehouseName: '成品仓',
    materialId: 3,
    materialCode: 'RM-001',
    materialName: '钢板',
    unitId: 4,
    unitName: '千克',
    quantity: '12.345678',
    ownershipType: 'PROJECT',
    ownershipTypeName: '项目库存',
    projectId: 501,
    projectNo: 'PRJ-001',
    projectName: '一号项目',
    qualityStatus: 'QUALIFIED',
    qualityStatusName: '合格',
    batchId: 610,
    batchNo: 'B-TF-001',
    serialId: 711,
    serialNo: 'SN-TF-001',
    sourceCostLayerId: 9001,
    costLayerNo: 'CL-PRJ-001',
  }],
}

const ownershipConversionRecord = {
  id: 1002,
  documentNo: 'OC-001',
  status: 'DRAFT',
  statusName: '草稿',
  businessDate: '2026-07-14',
  reason: '项目领用',
  lineCount: 1,
  version: 4,
  allowedActions: ['UPDATE', 'SUBMIT_APPROVAL', 'CANCEL'],
  approvalSummary: { id: 3001, status: 'SUBMITTED', submittedAt: '2026-07-14T10:00:00+08:00' },
  createdByName: '管理员',
  createdAt: '2026-07-14T09:00:00+08:00',
  updatedAt: '2026-07-14T09:30:00+08:00',
  lines: [{
    id: 2002,
    lineNo: 10,
    sourceOwnershipType: 'PROJECT',
    targetOwnershipType: 'PROJECT',
    sourceProjectId: 501,
    sourceProjectNo: 'PRJ-001',
    sourceProjectName: '一号项目',
    targetProjectId: 502,
    targetProjectNo: 'PRJ-001',
    targetProjectName: '一号项目',
    sourceWarehouseId: 1,
    sourceWarehouseName: '原料仓',
    targetWarehouseId: 2,
    targetWarehouseName: '成品仓',
    materialId: 3,
    materialCode: 'RM-001',
    materialName: '钢板',
    unitId: 4,
    unitName: '千克',
    sourceCostLayerId: 9001,
    costLayerNo: 'CL-PRJ-001',
    sourceUnitCost: '11.000000',
    qualityStatus: 'QUALIFIED',
    qualityStatusName: '合格',
    batchId: 610,
    batchNo: 'B-OC-001',
    serialId: 711,
    serialNo: 'SN-OC-001',
    quantity: '5.000000',
  }],
}

const stocktakeRecord = {
  id: 1003,
  documentNo: 'ST-001',
  status: 'COUNTING',
  statusName: '盘点中',
  businessDate: '2026-07-14',
  reason: '月度盘点',
  lineCount: 2,
  version: 5,
  allowedActions: ['UPDATE_LINES', 'RECONCILE', 'CANCEL'],
  createdByName: '管理员',
  createdAt: '2026-07-14T09:00:00+08:00',
  updatedAt: '2026-07-14T09:30:00+08:00',
  lines: [
    {
      id: 2003,
      lineNo: 10,
      warehouseName: '原料仓',
      materialCode: 'RM-001',
      materialName: '钢板',
      bookQuantity: '12.000000',
      countedQuantity: null,
      varianceQuantity: null,
      version: 7,
    },
    {
      id: 2004,
      lineNo: 20,
      warehouseName: '原料仓',
      materialCode: 'RM-002',
      materialName: '螺母',
      bookQuantity: '8.000000',
      countedQuantity: '0.000000',
      varianceQuantity: '-8.000000',
      version: 8,
    },
  ],
}

const stocktakeLineSummary = {
  totalLines: 2,
  countedLines: 1,
  varianceLines: 1,
  positiveVarianceLines: 0,
  negativeVarianceLines: 1,
  uncountedLines: 1,
}

function stocktakeLinePage(items: unknown[], page = 1, pageSize = 20, total = items.length) {
  return {
    items,
    page,
    pageSize,
    total,
    totalPages: Math.ceil(total / pageSize),
  }
}

function stocktakeLine(
  overrides: Partial<{
    id: number
    lineNo: number
    warehouseName: string
    materialCode: string
    materialName: string
    ownershipType: string
    projectNo: string | null
    projectName: string | null
    bookQuantity: string
    countedQuantity: string | null
    varianceQuantity: string | null
    varianceUnitCost: string | null
    varianceReason: string | null
    valuationRequirement: {
      mode: string
      requiredUnitCost: boolean
      requiredReason: boolean
      requiredAttachment: boolean
      unitCost?: string | null
    }
    version: number
  }> = {},
) {
  return {
    id: 2003,
    lineNo: 10,
    warehouseName: '原料仓',
    materialCode: 'RM-001',
    materialName: '钢板',
    ownershipType: 'PUBLIC',
    projectNo: null,
    projectName: null,
    bookQuantity: '12.000000',
    countedQuantity: null,
    varianceQuantity: null,
    varianceUnitCost: null,
    varianceReason: null,
    valuationRequirement: {
      mode: 'NONE',
      requiredUnitCost: false,
      requiredReason: false,
      requiredAttachment: false,
    },
    version: 7,
    ...overrides,
  }
}

const warehouseCandidates = [
  { id: 1, code: 'WH-RAW', name: '原料仓', status: 'ENABLED', version: 1 },
  { id: 2, code: 'WH-FG', name: '成品仓', status: 'ENABLED', version: 1 },
]

const materialCandidates = [
  {
    id: 3,
    code: 'RM-001',
    name: '钢板',
    status: 'ENABLED',
    materialType: 'RAW_MATERIAL',
    sourceType: 'PURCHASED',
    trackingMethod: 'SERIAL',
    trackingMethodName: '序列号',
    categoryId: 10,
    unitId: 4,
    unitName: '千克',
  },
]

const unitCandidates = [
  { id: 4, code: 'KG', name: '千克', status: 'ENABLED', precisionScale: 6, sortOrder: 1 },
]

const projectCandidates = [
  {
    id: 501,
    projectNo: 'PRJ-001',
    name: '一号项目',
    customerId: 8,
    customerCode: 'CUS-008',
    customerName: '华东客户',
    ownerUserId: 1,
    ownerUsername: 'admin',
    ownerDisplayName: '管理员',
    status: 'ACTIVE',
    targetRevenue: '0.00',
    targetCost: '0.00',
    contractSummaryRestricted: false,
    salesOrderSummaryRestricted: false,
    createdByName: '管理员',
    createdAt: '2026-07-14T09:00:00+08:00',
    updatedAt: '2026-07-14T09:30:00+08:00',
    version: 1,
  },
  {
    id: 502,
    projectNo: 'PRJ-002',
    name: '二号项目',
    customerId: 8,
    customerCode: 'CUS-008',
    customerName: '华东客户',
    ownerUserId: 1,
    ownerUsername: 'admin',
    ownerDisplayName: '管理员',
    status: 'ACTIVE',
    targetRevenue: '0.00',
    targetCost: '0.00',
    contractSummaryRestricted: false,
    salesOrderSummaryRestricted: false,
    createdByName: '管理员',
    createdAt: '2026-07-14T09:00:00+08:00',
    updatedAt: '2026-07-14T09:30:00+08:00',
    version: 1,
  },
]

const batchCandidates = [
  {
    id: 610,
    batchNo: 'B-TF-001',
    materialId: 3,
    materialCode: 'RM-001',
    materialName: '钢板',
    warehouseId: 1,
    warehouseName: '原料仓',
    qualityStatus: 'QUALIFIED',
    qualityStatusName: '合格',
    quantityOnHand: '12.345678',
    availableQuantity: '12.345678',
    updatedAt: '2026-07-14T09:30:00+08:00',
  },
]

const serialCandidates = [
  {
    id: 711,
    serialNo: 'SN-TF-001',
    materialId: 3,
    materialCode: 'RM-001',
    materialName: '钢板',
    batchId: 610,
    batchNo: 'B-TF-001',
    warehouseId: 1,
    warehouseName: '原料仓',
    qualityStatus: 'QUALIFIED',
    qualityStatusName: '合格',
    availableQuantity: '1.000000',
    updatedAt: '2026-07-14T09:30:00+08:00',
  },
]

const costLayerCandidates = [
  {
    id: 9001,
    layerNo: 'CL-PRJ-001',
    ownershipType: 'PROJECT',
    projectId: 501,
    projectNo: 'PRJ-001',
    projectName: '一号项目',
    warehouseId: 1,
    warehouseName: '原料仓',
    materialId: 3,
    materialCode: 'RM-001',
    materialName: '钢板',
    batchId: 610,
    batchNo: 'B-TF-001',
    serialId: 711,
    serialNo: 'SN-TF-001',
    originalQuantity: '12.345678',
    remainingQuantity: '12.345678',
    unitCost: '11.000000',
    status: 'OPEN',
    statusName: '可用',
  },
]

const valuationAdjustmentRecord = {
  id: 1004,
  documentNo: 'VA-001',
  adjustmentType: 'LEGACY_OPENING',
  adjustmentTypeName: '期初估值',
  status: 'DRAFT',
  statusName: '草稿',
  businessDate: '2026-07-14',
  reason: '历史期初估值',
  lineCount: 1,
  version: 6,
  allowedActions: ['UPDATE', 'SUBMIT_APPROVAL', 'CANCEL'],
  approvalSummary: null,
  amountImpactSummary: '调增 1,000.00',
  createdByName: '管理员',
  createdAt: '2026-07-14T09:00:00+08:00',
  updatedAt: '2026-07-14T09:30:00+08:00',
  lines: [{
    id: 2005,
    lineNo: 10,
    materialCode: 'RM-001',
    materialName: '钢板',
    ownershipType: 'PROJECT',
    ownershipTypeName: '项目库存',
    projectId: 501,
    projectNo: 'PRJ-001',
    projectName: '一号项目',
    quantity: '100.000000',
    unitCost: '10.000000',
    adjustmentAmount: '1000.00',
    costLayerId: 9001,
    costLayerNo: 'CL-PRJ-001',
  }],
}

const routes = [
  { path: '/inventory/warehouse-transfers', name: 'inventory-warehouse-transfers', component: InventoryControlledDocumentView },
  { path: '/inventory/warehouse-transfers/create', name: 'inventory-warehouse-transfer-create', component: InventoryControlledDocumentView },
  { path: '/inventory/warehouse-transfers/:id', name: 'inventory-warehouse-transfer-detail', component: InventoryControlledDocumentView },
  { path: '/inventory/warehouse-transfers/:id/edit', name: 'inventory-warehouse-transfer-edit', component: InventoryControlledDocumentView },
  { path: '/inventory/ownership-conversions', name: 'inventory-ownership-conversions', component: InventoryControlledDocumentView },
  { path: '/inventory/ownership-conversions/create', name: 'inventory-ownership-conversion-create', component: InventoryControlledDocumentView },
  { path: '/inventory/ownership-conversions/:id', name: 'inventory-ownership-conversion-detail', component: InventoryControlledDocumentView },
  { path: '/inventory/ownership-conversions/:id/edit', name: 'inventory-ownership-conversion-edit', component: InventoryControlledDocumentView },
  { path: '/inventory/stocktakes', name: 'inventory-stocktakes', component: InventoryControlledDocumentView },
  { path: '/inventory/stocktakes/create', name: 'inventory-stocktake-create', component: InventoryControlledDocumentView },
  { path: '/inventory/stocktakes/:id', name: 'inventory-stocktake-detail', component: InventoryControlledDocumentView },
  { path: '/inventory/stocktakes/:id/edit', name: 'inventory-stocktake-edit', component: InventoryControlledDocumentView },
  { path: '/inventory/valuation-adjustments', name: 'inventory-valuation-adjustments', component: InventoryControlledDocumentView },
  { path: '/inventory/valuation-adjustments/create', name: 'inventory-valuation-adjustment-create', component: InventoryControlledDocumentView },
  { path: '/inventory/valuation-adjustments/:id', name: 'inventory-valuation-adjustment-detail', component: InventoryControlledDocumentView },
  { path: '/inventory/valuation-adjustments/:id/edit', name: 'inventory-valuation-adjustment-edit', component: InventoryControlledDocumentView },
]

async function mountInventoryDocument(path: string, permissions: string[] = [
  'inventory:warehouse-transfer:view',
  'inventory:warehouse-transfer:create',
  'inventory:warehouse-transfer:update',
  'inventory:warehouse-transfer:post',
  'inventory:warehouse-transfer:cancel',
  'inventory:ownership-conversion:view',
  'inventory:ownership-conversion:create',
  'inventory:ownership-conversion:update',
  'inventory:ownership-conversion:submit',
  'inventory:ownership-conversion:withdraw',
  'inventory:ownership-conversion:cancel',
  'inventory:stocktake:view',
  'inventory:stocktake:create',
  'inventory:stocktake:update',
  'inventory:stocktake:submit',
  'inventory:stocktake:cancel',
  'inventory:valuation-adjustment:view',
  'inventory:valuation-adjustment:create',
  'inventory:valuation-adjustment:update',
  'inventory:valuation-adjustment:submit',
  'inventory:valuation-adjustment:withdraw',
  'inventory:valuation-adjustment:cancel',
]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(InventoryControlledDocumentView, {
    global: {
      plugins: [pinia, router, ElementPlus],
      stubs: {
        AttachmentPanel: {
          props: ['objectType', 'objectId', 'title', 'readonly'],
          template: '<section data-test="stocktake-evidence-attachment">{{ objectType }} {{ objectId }} {{ title }} {{ readonly }}</section>',
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

function firstButton(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAllComponents({ name: 'ElButton' }).find((item) => item.text().trim() === text)
  expect(button?.exists()).toBe(true)
  return button as VueWrapper
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

function fieldProps(wrapper: VueWrapper, dataTest: string) {
  const field = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(field.exists()).toBe(true)
  return field.props() as Record<string, unknown>
}

async function setDatePickerValue(wrapper: VueWrapper, name: string, value: string) {
  const datePicker = wrapper
    .findAllComponents({ name: 'ElDatePicker' })
    .find((candidate) => candidate.props('name') === name) as VueWrapper | undefined
  expect(datePicker?.exists()).toBe(true)
  datePicker?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((settled) => {
    resolve = settled
  })
  return { promise, resolve }
}

describe('库存受控单据页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    inventoryApiMock.warehouseTransfers.list.mockResolvedValue({
      items: [transferRecord],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    inventoryApiMock.warehouseTransfers.get.mockResolvedValue(transferRecord)
    inventoryApiMock.warehouseTransfers.create.mockResolvedValue(transferRecord)
    inventoryApiMock.warehouseTransfers.update.mockResolvedValue(transferRecord)
    inventoryApiMock.warehouseTransfers.post.mockResolvedValue({ ...transferRecord, status: 'POSTED', statusName: '已过账' })
    inventoryApiMock.warehouseTransfers.cancel.mockResolvedValue({ ...transferRecord, status: 'CANCELLED', statusName: '已取消' })

    inventoryApiMock.ownershipConversions.list.mockResolvedValue({
      items: [ownershipConversionRecord],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    inventoryApiMock.ownershipConversions.get.mockResolvedValue(ownershipConversionRecord)
    inventoryApiMock.ownershipConversions.create.mockResolvedValue(ownershipConversionRecord)
    inventoryApiMock.ownershipConversions.update.mockResolvedValue(ownershipConversionRecord)
    inventoryApiMock.ownershipConversions.submitApproval.mockResolvedValue(ownershipConversionRecord)
    inventoryApiMock.ownershipConversions.withdraw.mockResolvedValue(ownershipConversionRecord)
    inventoryApiMock.ownershipConversions.cancel.mockResolvedValue({ ...ownershipConversionRecord, status: 'CANCELLED' })

    inventoryApiMock.stocktakes.list.mockResolvedValue({
      items: [stocktakeRecord],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    inventoryApiMock.stocktakes.get.mockResolvedValue(stocktakeRecord)
    inventoryApiMock.stocktakes.create.mockResolvedValue(stocktakeRecord)
    inventoryApiMock.stocktakes.start.mockResolvedValue(stocktakeRecord)
    inventoryApiMock.stocktakes.listLines.mockResolvedValue(stocktakeLinePage(stocktakeRecord.lines))
    inventoryApiMock.stocktakes.updateLines.mockResolvedValue(stocktakeRecord)
    inventoryApiMock.stocktakes.reconcile.mockResolvedValue({ ...stocktakeRecord, status: 'RECONCILED' })
    inventoryApiMock.stocktakes.submitApproval.mockResolvedValue(stocktakeRecord)
    inventoryApiMock.stocktakes.completeZeroVariance.mockResolvedValue({ ...stocktakeRecord, status: 'POSTED' })
    inventoryApiMock.stocktakes.cancel.mockResolvedValue({ ...stocktakeRecord, status: 'CANCELLED' })

    inventoryApiMock.valuationAdjustments.list.mockResolvedValue({
      items: [valuationAdjustmentRecord],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    inventoryApiMock.valuationAdjustments.get.mockResolvedValue(valuationAdjustmentRecord)
    inventoryApiMock.valuationAdjustments.create.mockResolvedValue(valuationAdjustmentRecord)
    inventoryApiMock.valuationAdjustments.update.mockResolvedValue(valuationAdjustmentRecord)
    inventoryApiMock.valuationAdjustments.submitApproval.mockResolvedValue(valuationAdjustmentRecord)
    inventoryApiMock.valuationAdjustments.withdraw.mockResolvedValue(valuationAdjustmentRecord)
    inventoryApiMock.valuationAdjustments.cancel.mockResolvedValue({ ...valuationAdjustmentRecord, status: 'CANCELLED' })

    inventoryApiMock.batches.list.mockResolvedValue({ items: batchCandidates, page: 1, pageSize: 200, total: 1, totalPages: 1 })
    inventoryApiMock.serials.list.mockResolvedValue({ items: serialCandidates, page: 1, pageSize: 200, total: 1, totalPages: 1 })
    inventoryApiMock.costLayers.list.mockResolvedValue({ items: costLayerCandidates, page: 1, pageSize: 200, total: 1, totalPages: 1 })
    masterDataApiMock.warehouses.list.mockResolvedValue({ items: warehouseCandidates, page: 1, pageSize: 200, total: 2, totalPages: 1 })
    masterDataApiMock.materials.list.mockResolvedValue({ items: materialCandidates, page: 1, pageSize: 200, total: 1, totalPages: 1 })
    masterDataApiMock.units.list.mockResolvedValue({ items: unitCandidates, page: 1, pageSize: 200, total: 1, totalPages: 1 })
    salesProjectApiMock.projects.list.mockResolvedValue({ items: projectCandidates, page: 1, pageSize: 200, total: 2, totalPages: 1 })
  })

  it('仓库调拨列表按 allowedActions 显示过账和取消，动作携带版本与幂等键并刷新', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/warehouse-transfers')

    expect(wrapper.text()).toContain('仓库调拨')
    expect(wrapper.text()).toContain('WT-001')
    expect(wrapper.text()).toContain('仓间补货')
    expect(wrapper.text()).toContain('过账')
    expect(wrapper.text()).toContain('取消')

    await firstButton(wrapper, '过账').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.warehouseTransfers.post).toHaveBeenCalledWith(1001, {
      version: 3,
      idempotencyKey: expect.any(String),
    })
    expect(inventoryApiMock.warehouseTransfers.list).toHaveBeenCalledTimes(2)
  })

  it('所有权转换详情展示审批状态并只通过提交审批入口触发动作', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/ownership-conversions/1002')

    expect(wrapper.text()).toContain('所有权转换')
    expect(wrapper.text()).toContain('OC-001')
    expect(wrapper.text()).toContain('审批状态')
    expect(wrapper.text()).toContain('审批中')
    expect(wrapper.text()).toContain('提交审批')
    expect(wrapper.text()).not.toContain('直接过账')

    await firstButton(wrapper, '提交审批').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.ownershipConversions.submitApproval).toHaveBeenCalledWith(1002, {
      version: 4,
      idempotencyKey: expect.any(String),
      reason: '项目领用',
    })
  })

  it('受控单据列表展示独立审批状态和金额影响摘要', async () => {
    inventoryApiMock.valuationAdjustments.list.mockResolvedValueOnce({
      items: [{
        ...valuationAdjustmentRecord,
        approvalSummary: { id: 3002, status: 'SUBMITTED', submittedAt: '2026-07-14T10:00:00+08:00' },
      }],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const { wrapper } = await mountInventoryDocument('/inventory/valuation-adjustments')

    expect(wrapper.text()).toContain('审批状态')
    expect(wrapper.text()).toContain('审批中')
    expect(wrapper.text()).toContain('金额影响')
    expect(wrapper.text()).toContain('调增 1,000.00')
  })

  it('受控单据列表和详情把原始枚举转为中文并格式化结构化金额影响', async () => {
    inventoryApiMock.warehouseTransfers.list.mockResolvedValueOnce({
      items: [{
        ...transferRecord,
        statusName: undefined,
        status: 'DRAFT',
        amountImpactSummary: {},
        keyInfoSummary: { documentNo: 'WT-001', businessDate: '2026-07-14', status: 'DRAFT' },
      }],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const transfer = await mountInventoryDocument('/inventory/warehouse-transfers')
    expect(transfer.wrapper.text()).toContain('草稿')
    expect(transfer.wrapper.text()).toContain('未形成金额影响')
    expect(transfer.wrapper.text()).not.toContain('DRAFT')
    expect(transfer.wrapper.text()).not.toContain('{}')

    inventoryApiMock.ownershipConversions.list.mockResolvedValueOnce({
      items: [{
        ...ownershipConversionRecord,
        statusName: undefined,
        status: 'SUBMITTED',
        amountImpactSummary: { direction: 'INCREASE', amount: '1200.50' },
        costVisible: true,
      }],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const conversion = await mountInventoryDocument('/inventory/ownership-conversions')
    expect(conversion.wrapper.text()).toContain('审批中')
    expect(conversion.wrapper.text()).toContain('调增 1,200.50')
    expect(conversion.wrapper.text()).not.toContain('SUBMITTED')
    expect(conversion.wrapper.text()).not.toContain('{}')

    inventoryApiMock.stocktakes.list.mockResolvedValueOnce({
      items: [{
        ...stocktakeRecord,
        statusName: undefined,
        status: 'COUNTING',
        amountImpactSummary: { direction: 'DECREASE', amount: '300.00' },
        costVisible: false,
      }],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const stocktake = await mountInventoryDocument('/inventory/stocktakes')
    expect(stocktake.wrapper.text()).toContain('盘点中')
    expect(stocktake.wrapper.text()).toContain('金额受限')
    expect(stocktake.wrapper.text()).not.toContain('300.00')
    expect(stocktake.wrapper.text()).not.toContain('COUNTING')
    expect(stocktake.wrapper.text()).not.toContain('{}')

    inventoryApiMock.valuationAdjustments.get.mockResolvedValueOnce({
      ...valuationAdjustmentRecord,
      statusName: undefined,
      status: 'DRAFT',
      adjustmentTypeName: undefined,
      adjustmentType: 'LEGACY_OPENING',
    })
    const adjustment = await mountInventoryDocument('/inventory/valuation-adjustments/1004')
    expect(adjustment.wrapper.text()).toContain('草稿')
    expect(adjustment.wrapper.text()).toContain('历史期初估值')
    expect(adjustment.wrapper.text()).not.toContain('DRAFT')
    expect(adjustment.wrapper.text()).not.toContain('LEGACY_OPENING')
  })

  it('新建仓库调拨提交后端完整 DTO，包含质量、批次、序列、项目和来源成本层', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/warehouse-transfers/create')

    const datePicker = wrapper
      .findAllComponents({ name: 'ElDatePicker' })
      .find((candidate) => candidate.props('name') === 'inventory-controlled-business-date')
    expect(datePicker?.props('valueOnClear')).toBe('')
    expect(masterDataApiMock.warehouses.list).toHaveBeenCalledWith(expect.objectContaining({ pageSize: 200 }))
    expect(masterDataApiMock.materials.list).toHaveBeenCalledWith(expect.objectContaining({ pageSize: 200 }))
    expect(masterDataApiMock.units.list).toHaveBeenCalledWith(expect.objectContaining({ pageSize: 200 }))
    expect(salesProjectApiMock.projects.list).toHaveBeenCalledWith(expect.objectContaining({ pageSize: 200 }))
    expect(inventoryApiMock.batches.list).toHaveBeenCalledWith(expect.objectContaining({ pageSize: 200 }))
    expect(inventoryApiMock.serials.list).toHaveBeenCalledWith(expect.objectContaining({ pageSize: 200 }))
    expect(inventoryApiMock.costLayers.list).toHaveBeenCalledWith(expect.objectContaining({ pageSize: 200 }))
    expect(fieldProps(wrapper, 'inventory-controlled-source-warehouse-id').filterable).toBe(true)
    expect(fieldProps(wrapper, 'inventory-controlled-material-id').filterable).toBe(true)
    expect(fieldProps(wrapper, 'inventory-controlled-source-cost-layer-id').filterable).toBe(true)
    const optionLabels = wrapper.findAllComponents({ name: 'ElOption' }).map((option) => option.props('label'))
    expect(optionLabels).toContain('WH-RAW 原料仓')
    expect(optionLabels).toContain('RM-001 钢板 / 千克')
    expect(optionLabels).toContain('KG 千克')
    expect(optionLabels).toContain('PRJ-001 一号项目')
    expect(optionLabels).toContain('B-TF-001 / RM-001 / 钢板 / 原料仓')
    expect(optionLabels).toContain('SN-TF-001 / RM-001 / 钢板 / 原料仓')
    expect(optionLabels).toContain('CL-PRJ-001 / RM-001 / 钢板 / PRJ-001 / 一号项目 / 原料仓')

    await setDatePickerValue(wrapper, 'inventory-controlled-business-date', '2026-07-15')
    await wrapper.find('input[name="inventory-controlled-reason"]').setValue('项目仓调拨')
    await setSelectValue(wrapper, 'inventory-controlled-quality-status', 'QUALIFIED')
    await setSelectValue(wrapper, 'inventory-controlled-source-warehouse-id', '1')
    await setSelectValue(wrapper, 'inventory-controlled-target-warehouse-id', '2')
    await setSelectValue(wrapper, 'inventory-controlled-material-id', '3')
    await setSelectValue(wrapper, 'inventory-controlled-unit-id', '4')
    await wrapper.find('input[name="inventory-controlled-quantity"]').setValue('7.000000')
    await setSelectValue(wrapper, 'inventory-controlled-project-id', '501')
    await setSelectValue(wrapper, 'inventory-controlled-batch-id', '610')
    await setSelectValue(wrapper, 'inventory-controlled-serial-id', '711')
    await setSelectValue(wrapper, 'inventory-controlled-source-cost-layer-id', '9001')
    await firstButton(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.warehouseTransfers.create).toHaveBeenCalledWith(expect.objectContaining({
      businessDate: '2026-07-15',
      reason: '项目仓调拨',
      idempotencyKey: expect.any(String),
      lines: [expect.objectContaining({
        sourceWarehouseId: 1,
        targetWarehouseId: 2,
        materialId: 3,
        unitId: 4,
        quantity: '7.000000',
        ownershipType: 'PROJECT',
        projectId: 501,
        qualityStatus: 'QUALIFIED',
        batchId: 610,
        serialId: 711,
        sourceCostLayerId: 9001,
      })],
    }))
  })

  it('编辑仓库调拨从详情回填质量、批次、序列和成本层并携带版本保存，避免空表单覆盖', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/warehouse-transfers/1001/edit')

    expect(fieldProps(wrapper, 'inventory-controlled-source-warehouse-id').modelValue).toBe('1')
    expect(fieldProps(wrapper, 'inventory-controlled-target-warehouse-id').modelValue).toBe('2')
    expect(fieldProps(wrapper, 'inventory-controlled-material-id').modelValue).toBe('3')
    expect(fieldProps(wrapper, 'inventory-controlled-unit-id').modelValue).toBe('4')
    expect(fieldProps(wrapper, 'inventory-controlled-batch-id').modelValue).toBe('610')
    expect(fieldProps(wrapper, 'inventory-controlled-serial-id').modelValue).toBe('711')
    expect(fieldProps(wrapper, 'inventory-controlled-source-cost-layer-id').modelValue).toBe('9001')

    await firstButton(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.warehouseTransfers.update).toHaveBeenCalledWith(1001, expect.objectContaining({
      version: 3,
      lines: [expect.objectContaining({
        sourceWarehouseId: 1,
        targetWarehouseId: 2,
        materialId: 3,
        unitId: 4,
        qualityStatus: 'QUALIFIED',
        batchId: 610,
        serialId: 711,
        sourceCostLayerId: 9001,
        quantity: '12.345678',
      })],
    }))
  })

  it('终态编辑路由显示不可编辑原因且不露出保存语义', async () => {
    inventoryApiMock.warehouseTransfers.get.mockResolvedValueOnce({
      ...transferRecord,
      status: 'POSTED',
      statusName: '已过账',
      allowedActions: [],
    })

    const { wrapper } = await mountInventoryDocument('/inventory/warehouse-transfers/1001/edit')

    expect(wrapper.text()).toContain('当前状态“已过账”不可编辑')
    expect(wrapper.findAllComponents({ name: 'ElButton' }).some((button) => button.text().trim() === '保存')).toBe(false)
    expect(inventoryApiMock.warehouseTransfers.update).not.toHaveBeenCalled()
  })

  it('所有权转换支持项目间转换并提交来源目标仓库、项目、单位、质量、追踪身份和成本层', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/ownership-conversions/create')

    await setDatePickerValue(wrapper, 'inventory-controlled-business-date', '2026-07-15')
    await wrapper.find('input[name="inventory-controlled-reason"]').setValue('项目间借用')
    await setSelectValue(wrapper, 'inventory-controlled-source-ownership-type', 'PROJECT')
    await setSelectValue(wrapper, 'inventory-controlled-target-ownership-type', 'PROJECT')
    await setSelectValue(wrapper, 'inventory-controlled-quality-status', 'QUALIFIED')
    await setSelectValue(wrapper, 'inventory-controlled-source-warehouse-id', '1')
    await setSelectValue(wrapper, 'inventory-controlled-target-warehouse-id', '2')
    await setSelectValue(wrapper, 'inventory-controlled-source-project-id', '501')
    await setSelectValue(wrapper, 'inventory-controlled-target-project-id', '502')
    await setSelectValue(wrapper, 'inventory-controlled-material-id', '3')
    await setSelectValue(wrapper, 'inventory-controlled-unit-id', '4')
    await wrapper.find('input[name="inventory-controlled-quantity"]').setValue('5.000000')
    await setSelectValue(wrapper, 'inventory-controlled-batch-id', '610')
    await setSelectValue(wrapper, 'inventory-controlled-serial-id', '711')
    await setSelectValue(wrapper, 'inventory-controlled-source-cost-layer-id', '9001')
    expect(wrapper.find('input[name="inventory-controlled-source-unit-cost"]').exists()).toBe(false)
    await firstButton(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.ownershipConversions.create).toHaveBeenCalledWith(expect.objectContaining({
      idempotencyKey: expect.any(String),
      lines: [expect.objectContaining({
        sourceOwnershipType: 'PROJECT',
        targetOwnershipType: 'PROJECT',
        sourceWarehouseId: 1,
        targetWarehouseId: 2,
        sourceProjectId: 501,
        targetProjectId: 502,
        materialId: 3,
        unitId: 4,
        sourceCostLayerId: 9001,
        qualityStatus: 'QUALIFIED',
        batchId: 610,
        serialId: 711,
        quantity: '5.000000',
      })],
    }))
    expect(inventoryApiMock.ownershipConversions.create.mock.calls[0][0].lines[0])
      .not.toHaveProperty('sourceUnitCost')
  })

  it('受控单据详情提供桌面复核字段，来源单位成本仅只读展示且受成本权限控制', async () => {
    const transfer = await mountInventoryDocument('/inventory/warehouse-transfers/1001')

    expect(transfer.wrapper.text()).toContain('项目')
    expect(transfer.wrapper.text()).toContain('PRJ-001 一号项目')
    expect(transfer.wrapper.text()).toContain('单位')
    expect(transfer.wrapper.text()).toContain('千克')
    expect(transfer.wrapper.text()).toContain('来源成本层')
    expect(transfer.wrapper.text()).toContain('CL-PRJ-001')
    expect(transfer.wrapper.text()).toContain('质量状态')
    expect(transfer.wrapper.text()).toContain('合格')
    expect(transfer.wrapper.text()).toContain('B-TF-001 / SN-TF-001')

    const conversion = await mountInventoryDocument('/inventory/ownership-conversions/1002')
    expect(conversion.wrapper.text()).toContain('来源项目')
    expect(conversion.wrapper.text()).toContain('PRJ-001 一号项目')
    expect(conversion.wrapper.text()).toContain('目标项目')
    expect(conversion.wrapper.text()).toContain('来源仓库')
    expect(conversion.wrapper.text()).toContain('目标仓库')
    expect(conversion.wrapper.text()).toContain('来源实际成本')
    expect(conversion.wrapper.text()).toContain('11.000000')
    expect(conversion.wrapper.find('input[name="inventory-controlled-source-unit-cost"]').exists()).toBe(false)

    inventoryApiMock.ownershipConversions.get.mockResolvedValueOnce({
      ...ownershipConversionRecord,
      costVisible: false,
    })
    const restricted = await mountInventoryDocument('/inventory/ownership-conversions/1002')
    expect(restricted.wrapper.text()).toContain('金额受限')
    expect(restricted.wrapper.text()).not.toContain('11.000000')

    const adjustment = await mountInventoryDocument('/inventory/valuation-adjustments/1004')
    expect(adjustment.wrapper.text()).toContain('调整类型')
    expect(adjustment.wrapper.text()).toContain('期初估值')
    expect(adjustment.wrapper.text()).toContain('所有权')
    expect(adjustment.wrapper.text()).toContain('项目库存')
    expect(adjustment.wrapper.text()).toContain('PRJ-001 一号项目')
    expect(adjustment.wrapper.text()).toContain('成本层')
    expect(adjustment.wrapper.text()).toContain('CL-PRJ-001')
  })

  it('盘点详情严格区分未盘和实盘为零，行保存只发送已填写的脏行 countedQuantity', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')

    expect(wrapper.text()).toContain('库存盘点')
    expect(wrapper.text()).toContain('未盘')
    expect(wrapper.text()).toContain('0')
    expect(wrapper.find('[data-test="stocktake-line-actual-2003"]').text()).toBe('未盘')
    expect(wrapper.find('[data-test="stocktake-line-actual-2004"]').text()).toBe('0')

    await wrapper.find('input[name="stocktake-line-actual-2003"]').setValue('12.000000')
    await firstButton(wrapper, '保存实盘').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.stocktakes.updateLines).toHaveBeenCalledWith(1003, {
      version: 5,
      lines: [
        { id: 2003, version: 7, countedQuantity: '12.000000' },
      ],
    })
  })

  it('盘点详情不依赖无界 lines，按分页接口加载行并按服务端估值要求显示单价、原因和证据附件', async () => {
    const header = {
      ...stocktakeRecord,
      lineCount: 4,
      lineSummary: {
        totalLines: 4,
        countedLines: 3,
        varianceLines: 3,
        positiveVarianceLines: 3,
        negativeVarianceLines: 0,
        uncountedLines: 1,
      },
      costVisible: true,
      lines: undefined,
    }
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce(header)
    inventoryApiMock.stocktakes.listLines.mockResolvedValueOnce(stocktakeLinePage([
      stocktakeLine({ id: 2101, lineNo: 10, countedQuantity: null, varianceQuantity: null, version: 11 }),
      stocktakeLine({
        id: 2102,
        lineNo: 20,
        countedQuantity: '6.000000',
        bookQuantity: '5.000000',
        varianceQuantity: '1.000000',
        valuationRequirement: {
          mode: 'AUTO_PUBLIC_AVERAGE',
          requiredUnitCost: false,
          requiredReason: false,
          requiredAttachment: false,
          unitCost: '8.000000',
        },
        version: 12,
      }),
      stocktakeLine({
        id: 2103,
        lineNo: 30,
        countedQuantity: '6.000000',
        bookQuantity: '5.000000',
        varianceQuantity: '1.000000',
        valuationRequirement: {
          mode: 'EXPLICIT_UNIT_COST',
          requiredUnitCost: true,
          requiredReason: false,
          requiredAttachment: false,
        },
        version: 13,
      }),
      stocktakeLine({
        id: 2104,
        lineNo: 40,
        ownershipType: 'PROJECT',
        projectNo: 'PRJ-001',
        projectName: '一号项目',
        countedQuantity: '6.000000',
        bookQuantity: '5.000000',
        varianceQuantity: '1.000000',
        valuationRequirement: {
          mode: 'PROJECT_EXPLICIT_UNIT_COST',
          requiredUnitCost: true,
          requiredReason: true,
          requiredAttachment: true,
        },
        version: 14,
      }),
    ], 1, 20, 4))

    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')

    expect(inventoryApiMock.stocktakes.listLines).toHaveBeenCalledWith(1003, { page: 1, pageSize: 20 })
    expect(wrapper.text()).toContain('总行 4')
    expect(wrapper.find('[data-test="stocktake-line-actual-2101"]').text()).toBe('未盘')
    expect(wrapper.text()).toContain('自动沿用公共均价')
    expect(wrapper.text()).toContain('8.000000')
    expect(wrapper.find('input[name="stocktake-line-unit-cost-2103"]').exists()).toBe(true)
    expect(wrapper.find('input[name="stocktake-line-reason-2104"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="stocktake-evidence-attachment"]').text()).toContain('INVENTORY_STOCKTAKE 1003')
  })

  it('盘点跨页编辑只保存脏行，保留 0 字符串并带显式估值字段', async () => {
    const header = { ...stocktakeRecord, lineSummary: stocktakeLineSummary, lines: undefined, costVisible: true }
    const firstLine = stocktakeLine({ id: 2201, countedQuantity: null, version: 21 })
    const secondLine = stocktakeLine({
      id: 2202,
      lineNo: 20,
      ownershipType: 'PROJECT',
      projectNo: 'PRJ-001',
      projectName: '一号项目',
      countedQuantity: '1.000000',
      bookQuantity: '1.000000',
      varianceQuantity: '0.000000',
      valuationRequirement: {
        mode: 'PROJECT_EXPLICIT_UNIT_COST',
        requiredUnitCost: true,
        requiredReason: true,
        requiredAttachment: true,
      },
      version: 22,
    })
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce(header)
    inventoryApiMock.stocktakes.listLines
      .mockResolvedValueOnce(stocktakeLinePage([firstLine], 1, 1, 2))
      .mockResolvedValueOnce(stocktakeLinePage([secondLine], 2, 1, 2))
      .mockResolvedValueOnce(stocktakeLinePage([{
        ...secondLine,
        countedQuantity: '9.000000',
        varianceQuantity: '8.000000',
        varianceUnitCost: '3.000000',
        version: 23,
      }], 2, 1, 2))
    inventoryApiMock.stocktakes.updateLines.mockResolvedValueOnce({ ...header, version: 6 })

    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')
    await wrapper.find('input[name="stocktake-line-actual-2201"]').setValue('0.000000')
    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
    await flushPromises()
    await wrapper.find('input[name="stocktake-line-actual-2202"]').setValue('9.000000')
    await wrapper.find('input[name="stocktake-line-unit-cost-2202"]').setValue('3.000000')
    await wrapper.find('input[name="stocktake-line-reason-2202"]').setValue('项目盘盈复核')
    await firstButton(wrapper, '保存实盘').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.stocktakes.updateLines).toHaveBeenCalledWith(1003, {
      version: 5,
      lines: [
        { id: 2201, version: 21, countedQuantity: '0.000000' },
        {
          id: 2202,
          version: 22,
          countedQuantity: '9.000000',
          varianceUnitCost: '3.000000',
          varianceReason: '项目盘盈复核',
        },
      ],
    })
  })

  it('盘点分页加载期间禁用行输入、保存和分页，旧行不能误保存到新页上下文', async () => {
    const header = { ...stocktakeRecord, lineSummary: stocktakeLineSummary, lines: undefined, costVisible: true }
    const loadingPage = deferred<ReturnType<typeof stocktakeLinePage>>()
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce(header)
    inventoryApiMock.stocktakes.listLines
      .mockResolvedValueOnce(stocktakeLinePage([stocktakeLine({ id: 2501, countedQuantity: null, version: 51 })], 1, 1, 2))
      .mockReturnValueOnce(loadingPage.promise)

    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')
    await wrapper.find('input[name="stocktake-line-actual-2501"]').setValue('12.000000')
    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
    await flushPromises()

    const input = wrapper.find('input[name="stocktake-line-actual-2501"]')
    const disabledAttribute = input.exists() ? input.attributes().disabled : undefined
    expect(!input.exists() || disabledAttribute !== undefined).toBe(true)
    expect((firstButton(wrapper, '保存实盘').props() as Record<string, unknown>).disabled).toBe(true)
    expect((wrapper.findComponent({ name: 'ElPagination' }).props() as Record<string, unknown>).disabled).toBe(true)

    await firstButton(wrapper, '保存实盘').trigger('click')
    await flushPromises()
    expect(inventoryApiMock.stocktakes.updateLines).not.toHaveBeenCalled()

    loadingPage.resolve(stocktakeLinePage([stocktakeLine({ id: 2502, lineNo: 20, countedQuantity: null, version: 52 })], 2, 1, 2))
    await flushPromises()
    expect(wrapper.find('input[name="stocktake-line-actual-2502"]').exists()).toBe(true)
  })

  it('盘点脏行重载发现服务端行版本变化时提示冲突并阻止用新版本提交旧输入', async () => {
    const header = { ...stocktakeRecord, lineSummary: stocktakeLineSummary, lines: undefined, costVisible: true }
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce(header)
    inventoryApiMock.stocktakes.listLines
      .mockResolvedValueOnce(stocktakeLinePage([stocktakeLine({ id: 2601, countedQuantity: null, version: 61 })]))
      .mockResolvedValueOnce(stocktakeLinePage([stocktakeLine({ id: 2601, countedQuantity: '8.000000', version: 62 })]))

    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')
    await wrapper.find('input[name="stocktake-line-actual-2601"]').setValue('12.000000')
    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 1)
    await flushPromises()

    expect(wrapper.text()).toContain('盘点行已被其他人更新，请刷新后重新录入')
    await firstButton(wrapper, '保存实盘').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.stocktakes.updateLines).not.toHaveBeenCalled()
  })

  it('盘点存在未保存脏行时阻止确认和提交动作，失败保存保留用户输入', async () => {
    const header = {
      ...stocktakeRecord,
      allowedActions: ['UPDATE_LINES', 'RECONCILE', 'SUBMIT_APPROVAL', 'COMPLETE_ZERO_VARIANCE'],
      lineSummary: stocktakeLineSummary,
      lines: undefined,
      costVisible: true,
    }
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce(header)
    inventoryApiMock.stocktakes.listLines.mockResolvedValue(stocktakeLinePage([
      stocktakeLine({ id: 2301, countedQuantity: null, version: 31 }),
    ]))
    inventoryApiMock.stocktakes.updateLines.mockRejectedValueOnce(new AccountPermissionApiError(
      '单据版本已过期',
      'CONFLICT',
      409,
    ))

    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')
    await wrapper.find('input[name="stocktake-line-actual-2301"]').setValue('12.000000')
    await firstButton(wrapper, '确认差异').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.stocktakes.reconcile).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('存在未保存盘点行，请先保存实盘')

    await firstButton(wrapper, '保存实盘').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('单据版本已过期')
    expect((wrapper.find('input[name="stocktake-line-actual-2301"]').element as HTMLInputElement).value).toBe('12.000000')
  })

  it('盘点无成本权限时不渲染估值单价或成本输入', async () => {
    const header = { ...stocktakeRecord, lineSummary: stocktakeLineSummary, lines: undefined, costVisible: false }
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce(header)
    inventoryApiMock.stocktakes.listLines.mockResolvedValueOnce(stocktakeLinePage([
      stocktakeLine({
        id: 2401,
        countedQuantity: '6.000000',
        bookQuantity: '5.000000',
        varianceQuantity: '1.000000',
        valuationRequirement: {
          mode: 'AUTO_PUBLIC_AVERAGE',
          requiredUnitCost: false,
          requiredReason: false,
          requiredAttachment: false,
          unitCost: '8.000000',
        },
        version: 41,
      }),
    ]))

    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')

    expect(wrapper.text()).toContain('金额受限')
    expect(wrapper.text()).not.toContain('8.000000')
    expect(wrapper.find('input[name="stocktake-line-unit-cost-2401"]').exists()).toBe(false)
  })

  it('盘点附件在确认后提交审批前可补证据，审批中只读', async () => {
    const projectPositiveLine = stocktakeLine({
      id: 2701,
      ownershipType: 'PROJECT',
      projectNo: 'PRJ-001',
      projectName: '一号项目',
      countedQuantity: '6.000000',
      bookQuantity: '5.000000',
      varianceQuantity: '1.000000',
      valuationRequirement: {
        mode: 'PROJECT_EXPLICIT_UNIT_COST',
        requiredUnitCost: true,
        requiredReason: true,
        requiredAttachment: true,
      },
      version: 71,
    })
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce({
      ...stocktakeRecord,
      status: 'RECONCILED',
      statusName: '已确认差异',
      allowedActions: ['SUBMIT_APPROVAL', 'CANCEL'],
      lineSummary: { ...stocktakeLineSummary, positiveVarianceLines: 1 },
      lines: undefined,
      costVisible: true,
    })
    inventoryApiMock.stocktakes.listLines.mockResolvedValueOnce(stocktakeLinePage([projectPositiveLine]))

    const reconciled = await mountInventoryDocument('/inventory/stocktakes/1003')
    expect(reconciled.wrapper.find('[data-test="stocktake-evidence-attachment"]').text())
      .toContain('INVENTORY_STOCKTAKE 1003 项目盘盈证据附件 false')

    inventoryApiMock.stocktakes.get.mockResolvedValueOnce({
      ...stocktakeRecord,
      status: 'SUBMITTED',
      statusName: '审批中',
      allowedActions: [],
      approvalSummary: { id: 3003, status: 'SUBMITTED', submittedAt: '2026-07-14T11:00:00+08:00' },
      lineSummary: { ...stocktakeLineSummary, positiveVarianceLines: 1 },
      lines: undefined,
      costVisible: true,
    })
    inventoryApiMock.stocktakes.listLines.mockResolvedValueOnce(stocktakeLinePage([projectPositiveLine]))

    const submitted = await mountInventoryDocument('/inventory/stocktakes/1003')
    expect(submitted.wrapper.find('[data-test="stocktake-evidence-attachment"]').text())
      .toContain('INVENTORY_STOCKTAKE 1003 项目盘盈证据附件 true')
  })

  it('PUBLIC 盘盈证据附件标题使用所有权中性文案', async () => {
    const publicPositiveLine = stocktakeLine({
      id: 2702,
      ownershipType: 'PUBLIC',
      countedQuantity: '13.000000',
      bookQuantity: '12.000000',
      varianceQuantity: '1.000000',
      varianceUnitCost: '10.000000',
      varianceReason: '公共盘盈复核',
      valuationRequirement: {
        mode: 'EXPLICIT_UNIT_COST',
        requiredUnitCost: true,
        requiredReason: true,
        requiredAttachment: true,
      },
      version: 72,
    })
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce({
      ...stocktakeRecord,
      status: 'RECONCILED',
      statusName: '已确认差异',
      allowedActions: ['SUBMIT_APPROVAL', 'CANCEL'],
      lineSummary: { ...stocktakeLineSummary, positiveVarianceLines: 1 },
      lines: undefined,
      costVisible: true,
    })
    inventoryApiMock.stocktakes.listLines.mockResolvedValueOnce(stocktakeLinePage([publicPositiveLine]))

    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')

    expect(wrapper.find('[data-test="stocktake-evidence-attachment"]').text())
      .toContain('INVENTORY_STOCKTAKE 1003 盘盈证据附件 false')
    expect(wrapper.find('[data-test="stocktake-evidence-attachment"]').text())
      .not.toContain('项目盘盈证据附件')
  })

  it('盘点无成本权限保存数量时 payload 完全省略 varianceUnitCost', async () => {
    const header = { ...stocktakeRecord, lineSummary: stocktakeLineSummary, lines: undefined, costVisible: false }
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce(header)
    inventoryApiMock.stocktakes.listLines.mockResolvedValueOnce(stocktakeLinePage([
      stocktakeLine({
        id: 2801,
        countedQuantity: null,
        bookQuantity: '5.000000',
        varianceQuantity: null,
        valuationRequirement: {
          mode: 'PROJECT_EXPLICIT_UNIT_COST',
          requiredUnitCost: true,
          requiredReason: true,
          requiredAttachment: true,
        },
        version: 81,
      }),
    ]))
    inventoryApiMock.stocktakes.updateLines.mockResolvedValueOnce({ ...header, version: 6 })

    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')
    await wrapper.find('input[name="stocktake-line-actual-2801"]').setValue('6.000000')
    await wrapper.find('input[name="stocktake-line-reason-2801"]').setValue('项目盘盈复核')
    await firstButton(wrapper, '保存实盘').trigger('click')
    await flushPromises()

    const payload = inventoryApiMock.stocktakes.updateLines.mock.calls[0][1]
    expect(payload.lines[0]).toEqual({
      id: 2801,
      version: 81,
      countedQuantity: '6.000000',
      varianceReason: '项目盘盈复核',
    })
    expect(payload.lines[0]).not.toHaveProperty('varianceUnitCost')
  })

  it('盘点 START/RECONCILE/COMPLETE_ZERO_VARIANCE 只需更新权限，提交审批才需要提交权限', async () => {
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce({
      ...stocktakeRecord,
      status: 'DRAFT',
      statusName: '草稿',
      allowedActions: ['START', 'RECONCILE', 'COMPLETE_ZERO_VARIANCE', 'SUBMIT_APPROVAL', 'CANCEL'],
    })
    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003', [
      'inventory:stocktake:view',
      'inventory:stocktake:update',
      'inventory:stocktake:cancel',
    ])

    expect(wrapper.text()).toContain('开始盘点')
    expect(wrapper.text()).toContain('确认差异')
    expect(wrapper.text()).toContain('结束零差异盘点')
    expect(wrapper.text()).not.toContain('提交审批')

    await firstButton(wrapper, '开始盘点').trigger('click')
    await flushPromises()
    expect(inventoryApiMock.stocktakes.start).toHaveBeenCalledWith(1003, {
      version: 5,
      idempotencyKey: expect.any(String),
    })
  })

  it('估值调整支持暂估重估类型和项目成本层金额影响字段', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/valuation-adjustments/create')

    await setSelectValue(wrapper, 'inventory-controlled-adjustment-type', 'PROVISIONAL_REVALUATION')
    await setDatePickerValue(wrapper, 'inventory-controlled-business-date', '2026-07-15')
    await wrapper.find('input[name="inventory-controlled-reason"]').setValue('暂估价修正')
    await setSelectValue(wrapper, 'inventory-controlled-material-id', '3')
    await wrapper.find('input[name="inventory-controlled-quantity"]').setValue('10.000000')
    await wrapper.find('input[name="inventory-controlled-unit-cost"]').setValue('12.000000')
    await wrapper.find('input[name="inventory-controlled-adjustment-amount"]').setValue('120.00')
    await setSelectValue(wrapper, 'inventory-controlled-project-id', '501')
    await setSelectValue(wrapper, 'inventory-controlled-cost-layer-id', '9001')
    await firstButton(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.valuationAdjustments.create).toHaveBeenCalledWith(expect.objectContaining({
      adjustmentType: 'PROVISIONAL_REVALUATION',
      idempotencyKey: expect.any(String),
      lines: [expect.objectContaining({
        materialId: 3,
        ownershipType: 'PROJECT',
        projectId: 501,
        costLayerId: 9001,
        quantity: '10.000000',
        unitCost: '12.000000',
        adjustmentAmount: '120.00',
      })],
    }))
  })

  it('估值调整详情展示期初估值金额但不提供前端重算入口', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/valuation-adjustments/1004')

    expect(wrapper.text()).toContain('估值调整')
    expect(wrapper.text()).toContain('期初估值')
    expect(wrapper.text()).toContain('10.000000')
    expect(wrapper.text()).toContain('1,000.00')
    expect(wrapper.text()).toContain('提交审批')
    expect(wrapper.text()).not.toContain('重新计算')
  })

  it('盘点提交审批发送已保存业务原因', async () => {
    inventoryApiMock.stocktakes.get.mockResolvedValueOnce({
      ...stocktakeRecord,
      status: 'RECONCILED',
      statusName: '已确认差异',
      allowedActions: ['SUBMIT_APPROVAL', 'CANCEL'],
      reason: '月度盘点',
    })
    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')

    await firstButton(wrapper, '提交审批').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.stocktakes.submitApproval).toHaveBeenCalledWith(1003, {
      version: 5,
      idempotencyKey: expect.any(String),
      reason: '月度盘点',
    })
  })

  it('估值调整提交审批发送已保存业务原因', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/valuation-adjustments/1004')

    await firstButton(wrapper, '提交审批').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.valuationAdjustments.submitApproval).toHaveBeenCalledWith(1004, {
      version: 6,
      idempotencyKey: expect.any(String),
      reason: '历史期初估值',
    })
  })

  it('提交审批缺少已保存业务原因时提示并阻止请求', async () => {
    inventoryApiMock.ownershipConversions.get.mockResolvedValueOnce({
      ...ownershipConversionRecord,
      reason: '   ',
    })
    const { wrapper } = await mountInventoryDocument('/inventory/ownership-conversions/1002')

    await firstButton(wrapper, '提交审批').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请先填写并保存业务原因后再提交审批')
    expect(inventoryApiMock.ownershipConversions.submitApproval).not.toHaveBeenCalled()
  })

  it('动作发生版本冲突时刷新详情并提示已过期需刷新', async () => {
    inventoryApiMock.warehouseTransfers.post.mockRejectedValueOnce(new AccountPermissionApiError(
      '单据版本已过期',
      'VERSION_CONFLICT',
      409,
    ))
    const { wrapper } = await mountInventoryDocument('/inventory/warehouse-transfers/1001')

    await firstButton(wrapper, '过账').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('数据已过期，请刷新后重试')
    expect(inventoryApiMock.warehouseTransfers.get).toHaveBeenCalledTimes(2)
  })

  it('业务 409 保留后端稳定错误消息且不按版本冲突刷新', async () => {
    inventoryApiMock.warehouseTransfers.post.mockRejectedValueOnce(new AccountPermissionApiError(
      '盘点范围已锁定',
      'INVENTORY_STOCKTAKE_RANGE_LOCKED',
      409,
    ))
    const { wrapper } = await mountInventoryDocument('/inventory/warehouse-transfers/1001')

    await firstButton(wrapper, '过账').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('盘点范围已锁定')
    expect(wrapper.text()).not.toContain('数据已过期，请刷新后重试')
    expect(inventoryApiMock.warehouseTransfers.get).toHaveBeenCalledTimes(1)
  })
})
