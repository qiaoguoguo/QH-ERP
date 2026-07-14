import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { AccountPermissionApiError } from '../../shared/api/accountPermissionApi'
import { useAuthStore } from '../../stores/authStore'
import InventoryControlledDocumentView from './InventoryControlledDocumentView.vue'

const inventoryApiMock = vi.hoisted(() => ({
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
    global: { plugins: [pinia, router, ElementPlus] },
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

  it('新建仓库调拨提交后端完整 DTO，包含质量、批次、序列、项目和来源成本层', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/warehouse-transfers/create')

    await wrapper.find('input[name="inventory-controlled-business-date"]').setValue('2026-07-15')
    await wrapper.find('input[name="inventory-controlled-reason"]').setValue('项目仓调拨')
    await setSelectValue(wrapper, 'inventory-controlled-quality-status', 'QUALIFIED')
    await wrapper.find('input[name="inventory-controlled-source-warehouse-id"]').setValue('1')
    await wrapper.find('input[name="inventory-controlled-target-warehouse-id"]').setValue('2')
    await wrapper.find('input[name="inventory-controlled-material-id"]').setValue('3')
    await wrapper.find('input[name="inventory-controlled-unit-id"]').setValue('4')
    await wrapper.find('input[name="inventory-controlled-quantity"]').setValue('7.000000')
    await wrapper.find('input[name="inventory-controlled-project-id"]').setValue('501')
    await wrapper.find('input[name="inventory-controlled-batch-id"]').setValue('610')
    await wrapper.find('input[name="inventory-controlled-serial-id"]').setValue('711')
    await wrapper.find('input[name="inventory-controlled-source-cost-layer-id"]').setValue('9001')
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

    expect((wrapper.find('input[name="inventory-controlled-source-warehouse-id"]').element as HTMLInputElement).value).toBe('1')
    expect((wrapper.find('input[name="inventory-controlled-target-warehouse-id"]').element as HTMLInputElement).value).toBe('2')
    expect((wrapper.find('input[name="inventory-controlled-material-id"]').element as HTMLInputElement).value).toBe('3')
    expect((wrapper.find('input[name="inventory-controlled-unit-id"]').element as HTMLInputElement).value).toBe('4')
    expect((wrapper.find('input[name="inventory-controlled-batch-id"]').element as HTMLInputElement).value).toBe('610')
    expect((wrapper.find('input[name="inventory-controlled-serial-id"]').element as HTMLInputElement).value).toBe('711')
    expect((wrapper.find('input[name="inventory-controlled-source-cost-layer-id"]').element as HTMLInputElement).value).toBe('9001')

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

  it('所有权转换支持项目间转换并提交来源目标仓库、项目、单位、质量、追踪身份和成本层', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/ownership-conversions/create')

    await wrapper.find('input[name="inventory-controlled-business-date"]').setValue('2026-07-15')
    await wrapper.find('input[name="inventory-controlled-reason"]').setValue('项目间借用')
    await setSelectValue(wrapper, 'inventory-controlled-source-ownership-type', 'PROJECT')
    await setSelectValue(wrapper, 'inventory-controlled-target-ownership-type', 'PROJECT')
    await setSelectValue(wrapper, 'inventory-controlled-quality-status', 'QUALIFIED')
    await wrapper.find('input[name="inventory-controlled-source-warehouse-id"]').setValue('1')
    await wrapper.find('input[name="inventory-controlled-target-warehouse-id"]').setValue('2')
    await wrapper.find('input[name="inventory-controlled-source-project-id"]').setValue('501')
    await wrapper.find('input[name="inventory-controlled-target-project-id"]').setValue('502')
    await wrapper.find('input[name="inventory-controlled-material-id"]').setValue('3')
    await wrapper.find('input[name="inventory-controlled-unit-id"]').setValue('4')
    await wrapper.find('input[name="inventory-controlled-quantity"]').setValue('5.000000')
    await wrapper.find('input[name="inventory-controlled-batch-id"]').setValue('610')
    await wrapper.find('input[name="inventory-controlled-serial-id"]').setValue('711')
    await wrapper.find('input[name="inventory-controlled-source-cost-layer-id"]').setValue('9001')
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

  it('盘点详情严格区分未盘和实盘为零，行保存发送单据版本、行版本和 countedQuantity', async () => {
    const { wrapper } = await mountInventoryDocument('/inventory/stocktakes/1003')

    expect(wrapper.text()).toContain('库存盘点')
    expect(wrapper.text()).toContain('未盘')
    expect(wrapper.text()).toContain('0')
    expect(wrapper.find('[data-test="stocktake-line-actual-2003"]').text()).toBe('未盘')
    expect(wrapper.find('[data-test="stocktake-line-actual-2004"]').text()).toBe('0')

    await wrapper.find('input[name="stocktake-line-actual-2003"]').setValue('12.000000')
    await wrapper.find('input[name="stocktake-line-actual-2004"]').setValue('')
    await firstButton(wrapper, '保存实盘').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.stocktakes.updateLines).toHaveBeenCalledWith(1003, {
      version: 5,
      lines: [
        { id: 2003, version: 7, countedQuantity: '12.000000' },
        { id: 2004, version: 8, countedQuantity: null },
      ],
    })
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
    await wrapper.find('input[name="inventory-controlled-business-date"]').setValue('2026-07-15')
    await wrapper.find('input[name="inventory-controlled-reason"]').setValue('暂估价修正')
    await wrapper.find('input[name="inventory-controlled-material-id"]').setValue('3')
    await wrapper.find('input[name="inventory-controlled-quantity"]').setValue('10.000000')
    await wrapper.find('input[name="inventory-controlled-unit-cost"]').setValue('12.000000')
    await wrapper.find('input[name="inventory-controlled-adjustment-amount"]').setValue('120.00')
    await wrapper.find('input[name="inventory-controlled-project-id"]').setValue('501')
    await wrapper.find('input[name="inventory-controlled-cost-layer-id"]').setValue('9001')
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
})
