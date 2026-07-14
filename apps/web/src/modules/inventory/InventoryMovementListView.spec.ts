import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { InventoryMovementRecord, InventoryTraceDetailRecord } from '../../shared/api/inventoryApi'
import type { MaterialRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
import { useAuthStore } from '../../stores/authStore'
import InventoryMovementListView from './InventoryMovementListView.vue'

const inventoryApiMock = vi.hoisted(() => ({
  movements: {
    list: vi.fn(),
  },
  costLayers: {
    list: vi.fn(),
  },
  traces: {
    getBatchTrace: vi.fn(),
    getSerialTrace: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  warehouses: {
    list: vi.fn(),
  },
  materials: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/inventoryApi', () => ({
  inventoryApi: inventoryApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const warehouse: WarehouseRecord = {
  id: 1,
  code: 'WH-RAW',
  name: '原料仓',
  status: 'ENABLED',
}

const material: MaterialRecord = {
  id: 2,
  code: 'RM-STEEL',
  name: '冷轧钢板',
  materialType: 'RAW_MATERIAL',
  sourceType: 'PURCHASED',
  trackingMethod: 'BATCH',
  trackingMethodName: '批次管理',
  categoryId: 1,
  unitId: 3,
  unitName: '千克',
  status: 'ENABLED',
}

const movement: InventoryMovementRecord = {
  id: 20,
  movementNo: 'MV202607030001',
  movementType: 'ADJUSTMENT_DECREASE',
  direction: 'OUT',
  warehouseId: 1,
  warehouseName: '原料仓',
  materialId: 2,
  materialCode: 'RM-STEEL',
  materialName: '冷轧钢板',
  unitId: 3,
  unitName: '千克',
  trackingMethod: 'BATCH',
  trackingMethodName: '批次管理',
  batchId: 31,
  batchNo: 'B-20260711-001',
  serialId: null,
  serialNo: null,
  quantity: 3,
  beforeQuantity: 120,
  afterQuantity: 117,
  sourceType: 'INVENTORY_DOCUMENT',
  sourceId: 99,
  sourceLineId: 100,
  targetDocumentNo: 'QT-20260711-001',
  businessDate: '2026-07-03',
  reason: '生产损耗调整',
  remark: null,
  operatorName: '管理员',
  occurredAt: '2026-07-03T10:00:00+08:00',
}

const valuedMovement = {
  ...movement,
  id: 27,
  ownershipType: 'PROJECT',
  ownershipTypeName: '项目库存',
  projectId: 501,
  projectNo: 'PRJ-001',
  projectName: '一号项目',
  valuationMethod: 'PROJECT_ACTUAL_LAYER',
  valuationMethodName: '项目实际成本层',
  valuationState: 'VALUED',
  valuationStateName: '已估值',
  unitCost: '11.000000',
  movementAmount: '33.00',
  costVisible: true,
  valueFlowId: 8801,
  originalValueFlowId: 7701,
  costLayerId: 9001,
} as InventoryMovementRecord

const restrictedValuedMovement = {
  ...valuedMovement,
  id: 28,
  costVisible: false,
  unitCost: null,
  movementAmount: null,
} as InventoryMovementRecord

const productionIssueMovement: InventoryMovementRecord = {
  ...movement,
  id: 21,
  movementNo: 'MV202607030002',
  movementType: 'PRODUCTION_ISSUE',
  direction: 'OUT',
  quantity: 5,
  beforeQuantity: 117,
  afterQuantity: 112,
  sourceType: 'PRODUCTION_MATERIAL_ISSUE',
  sourceId: 300,
  sourceLineId: 301,
  reason: '生产工单领料',
  occurredAt: '2026-07-03T11:00:00+08:00',
}

const productionReceiptMovement: InventoryMovementRecord = {
  ...movement,
  id: 22,
  movementNo: 'MV202607030003',
  movementType: 'PRODUCTION_RECEIPT',
  direction: 'IN',
  quantity: 2,
  beforeQuantity: 112,
  afterQuantity: 114,
  sourceType: 'PRODUCTION_RECEIPT',
  sourceId: 300,
  sourceLineId: 302,
  reason: '完工入库',
  occurredAt: '2026-07-03T12:00:00+08:00',
}

const purchaseReceiptMovement: InventoryMovementRecord = {
  ...movement,
  id: 23,
  movementNo: 'MV202607030004',
  movementType: 'PURCHASE_RECEIPT',
  direction: 'IN',
  quantity: 8,
  beforeQuantity: 114,
  afterQuantity: 122,
  sourceType: 'PURCHASE_RECEIPT',
  sourceId: 700,
  sourceLineId: 701,
  reason: '采购入库过账',
  occurredAt: '2026-07-03T13:00:00+08:00',
}

const salesShipmentMovement: InventoryMovementRecord = {
  ...movement,
  id: 24,
  movementNo: 'MV202607030005',
  movementType: 'SALES_SHIPMENT',
  direction: 'OUT',
  quantity: 6,
  beforeQuantity: 122,
  afterQuantity: 116,
  sourceType: 'SALES_SHIPMENT',
  sourceId: 800,
  sourceLineId: 801,
  reason: '销售出库过账',
  occurredAt: '2026-07-03T14:00:00+08:00',
}

const warehouseTransferMovement: InventoryMovementRecord = {
  ...valuedMovement,
  id: 29,
  movementNo: 'MV202607030006',
  movementType: 'WAREHOUSE_TRANSFER_OUT',
  sourceType: 'WAREHOUSE_TRANSFER',
  sourceId: 1001,
  sourceLineId: 2001,
  targetDocumentNo: 'WT-001',
  reason: '仓库调拨出库',
}

const ownershipConversionMovement: InventoryMovementRecord = {
  ...valuedMovement,
  id: 30,
  movementNo: 'MV202607030007',
  movementType: 'OWNERSHIP_CONVERSION_OUT',
  sourceType: 'OWNERSHIP_CONVERSION',
  sourceId: 1002,
  sourceLineId: 2002,
  targetDocumentNo: 'OC-001',
  reason: '所有权转出',
}

const stocktakeMovement: InventoryMovementRecord = {
  ...valuedMovement,
  id: 31,
  movementNo: 'MV202607030008',
  movementType: 'STOCKTAKE_GAIN',
  sourceType: 'STOCKTAKE',
  sourceId: 1003,
  sourceLineId: 2003,
  targetDocumentNo: 'ST-001',
  reason: '盘盈入库',
}

const valuationAdjustmentMovement: InventoryMovementRecord = {
  ...valuedMovement,
  id: 32,
  movementNo: 'MV202607030009',
  movementType: 'VALUATION_ADJUSTMENT',
  sourceType: 'VALUATION_ADJUSTMENT',
  sourceId: 1004,
  sourceLineId: 2004,
  targetDocumentNo: 'VA-001',
  reason: '估值调整',
}

const movementPage: PageResult<InventoryMovementRecord> = {
  items: [movement],
  page: 1,
  pageSize: 10,
  total: 1,
  totalPages: 1,
}

const emptyMovementPage: PageResult<InventoryMovementRecord> = {
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
}

const movementWithoutSource: InventoryMovementRecord = {
  ...movement,
  id: 25,
  sourceType: '',
  sourceId: '',
  sourceLineId: null,
  sourceDocumentNo: null,
}

const movementWithoutTrackingIdentity: InventoryMovementRecord = {
  ...movement,
  id: 26,
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  batchId: null,
  batchNo: null,
  serialId: null,
  serialNo: null,
}

const traceDetail: InventoryTraceDetailRecord = {
  subject: {
    trackingMethod: 'BATCH',
    batchId: 31,
    batchNo: 'B-20260711-001',
    materialCode: 'RM-STEEL',
    materialName: '冷轧钢板',
  },
  currentBalances: [],
  activeReservations: [],
  sourceRecords: [],
  qualityEvents: [],
  outboundRecords: [],
  returnRecords: [],
  movements: [
    {
      nodeTypeName: '库存流水',
      documentNo: 'MV202607030001',
      businessDate: '2026-07-03',
      direction: 'OUT',
      quantity: '3.000000',
      warehouseName: '原料仓',
      permissionRestricted: false,
    },
  ],
  restrictedSources: [],
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountMovements(
  initialQuery: Record<string, string> = {},
  permissions = [
    'inventory:movement:view',
    'inventory:document:view',
    'procurement:receipt:view',
    'sales:shipment:view',
    'inventory:warehouse-transfer:view',
    'inventory:ownership-conversion:view',
    'inventory:stocktake:view',
    'inventory:valuation-adjustment:view',
    'inventory:trace:view',
  ],
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
      { path: '/inventory/movements', name: 'inventory-movements', component: InventoryMovementListView },
      { path: '/inventory/documents/:id', name: 'inventory-document-detail', component: { render: () => null } },
      { path: '/inventory/warehouse-transfers/:id', name: 'inventory-warehouse-transfer-detail', component: { render: () => null } },
      { path: '/inventory/ownership-conversions/:id', name: 'inventory-ownership-conversion-detail', component: { render: () => null } },
      { path: '/inventory/stocktakes/:id', name: 'inventory-stocktake-detail', component: { render: () => null } },
      { path: '/inventory/valuation-adjustments/:id', name: 'inventory-valuation-adjustment-detail', component: { render: () => null } },
      { path: '/procurement/receipts/:id', name: 'procurement-receipt-detail', component: { render: () => null } },
      { path: '/sales/shipments/:id', name: 'sales-shipment-detail', component: { render: () => null } },
    ],
  })
  await router.push({ path: '/inventory/movements', query: initialQuery })
  await router.isReady()
  const wrapper = mount(InventoryMovementListView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('库存变动流水页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    inventoryApiMock.movements.list.mockResolvedValue(movementPage)
    inventoryApiMock.costLayers.list.mockResolvedValue({
      items: [{
        id: 9001,
        layerNo: 'CL-PRJ-001',
        ownershipType: 'PROJECT',
        ownershipTypeName: '项目库存',
        projectId: 501,
        projectNo: 'PRJ-001',
        projectName: '一号项目',
        materialId: 2,
        materialCode: 'RM-STEEL',
        materialName: '冷轧钢板',
        originalQuantity: '120.500000',
        originalAmount: '1325.50',
        remainingQuantity: '80.000000',
        remainingAmount: '880.00',
        unitCost: '11.000000',
        status: 'OPEN',
        statusName: '开放',
        sourceType: 'OWNERSHIP_CONVERSION',
        sourceDocumentNo: 'OC-001',
        createdAt: '2026-07-03T09:00:00+08:00',
      }],
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1,
    })
    inventoryApiMock.traces.getBatchTrace.mockResolvedValue(traceDetail)
    inventoryApiMock.traces.getSerialTrace.mockResolvedValue(traceDetail)
    masterDataApiMock.warehouses.list.mockResolvedValue({
      items: [warehouse],
      page: 1,
      pageSize: 100,
      total: 1,
      totalPages: 1,
    })
    masterDataApiMock.materials.list.mockResolvedValue({
      items: [material],
      page: 1,
      pageSize: 100,
      total: 1,
      totalPages: 1,
    })
  })

  it('加载流水并用文字展示变动类型和出入库方向', async () => {
    const { wrapper } = await mountMovements()

    expect(wrapper.text()).toContain('库存流水与价值追溯')
    expect(wrapper.text()).toContain('调减')
    expect(wrapper.text()).toContain('出库')
    expect(wrapper.text()).toContain('批次管理')
    expect(wrapper.text()).toContain('B-20260711-001')
    expect(wrapper.text()).toContain('QT-20260711-001')
    expect(wrapper.text()).toContain('生产损耗调整')
    expect(wrapper.findAll('[data-test="create-inventory-document"]')).toHaveLength(0)
    expect(wrapper.find('[data-test="movement-quantity-cell"]').classes()).toContain('numeric-cell')
  })

  it('升级为库存流水与价值追溯，金额受限时不展示完整金额并可查看成本层', async () => {
    inventoryApiMock.movements.list.mockResolvedValueOnce({
      items: [valuedMovement, restrictedValuedMovement],
      page: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
    })
    const { wrapper } = await mountMovements({}, [
      'inventory:movement:view',
      'inventory:document:view',
      'inventory:valuation:view',
      'inventory:trace:view',
    ])

    expect(wrapper.text()).toContain('库存流水与价值追溯')
    expect(wrapper.text()).toContain('所有权')
    expect(wrapper.text()).toContain('项目')
    expect(wrapper.text()).toContain('计价方法')
    expect(wrapper.text()).toContain('单位成本')
    expect(wrapper.text()).toContain('变动金额')
    expect(wrapper.text()).toContain('价值流水')
    expect(wrapper.text()).toContain('项目库存')
    expect(wrapper.text()).toContain('PRJ-001 一号项目')
    expect(wrapper.text()).toContain('11.000000')
    expect(wrapper.text()).toContain('33.00')
    expect(wrapper.text()).toContain('金额受限')
    expect(wrapper.find('[data-test="movement-cost-restricted-28"]').text()).toContain('金额受限')

    await wrapper.find('[data-test="view-movement-cost-layer"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.costLayers.list).toHaveBeenCalledWith(expect.objectContaining({
      costLayerId: 9001,
      page: 1,
      pageSize: 20,
    }))
    expect(wrapper.text()).toContain('成本层追溯')
    expect(wrapper.text()).toContain('CL-PRJ-001')
  })

  it('用中文展示生产领料和完工入库流水类型', async () => {
    inventoryApiMock.movements.list.mockResolvedValueOnce({
      items: [productionIssueMovement, productionReceiptMovement],
      page: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
    })

    const { wrapper } = await mountMovements()

    const movementTypeCells = wrapper.findAll('[data-test="movement-type-cell"]')
    expect(movementTypeCells).toHaveLength(2)
    expect(movementTypeCells.map((cell) => cell.text())).toEqual(['生产领料', '完工入库'])
  })

  it('按筛选条件查询并支持来自余额页的仓库物料和追踪查询参数', async () => {
    const { wrapper } = await mountMovements({
      warehouseId: '1',
      materialId: '2',
      qualityStatus: 'QUALIFIED',
      trackingMethod: 'BATCH',
      batchId: '31',
      batchNo: 'B-20260711-001',
    })

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      warehouseId: 1,
      materialId: 2,
      qualityStatus: 'QUALIFIED',
      trackingMethod: 'BATCH',
      batchId: 31,
      batchNo: 'B-20260711-001',
    }))

    await wrapper.find('input[name="inventory-movement-keyword"]').setValue('钢板')
    await setSelectValue(wrapper, 'inventory-movement-type', 'ADJUSTMENT_DECREASE')
    await setSelectValue(wrapper, 'inventory-movement-direction', 'OUT')
    await setSelectValue(wrapper, 'inventory-movement-quality-status', 'QUALIFIED')
    await setSelectValue(wrapper, 'inventory-movement-tracking-method', 'SERIAL')
    await wrapper.find('input[name="inventory-movement-batch-no"]').setValue('B-20260711-001')
    await wrapper.find('input[name="inventory-movement-serial-no"]').setValue('SN-20260711-001')
    await wrapper.find('input[name="inventory-movement-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="inventory-movement-date-to"]').setValue('2026-07-03')
    await wrapper.find('[data-test="search-inventory-movements"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith({
      keyword: '钢板',
      warehouseId: 1,
      materialId: 2,
      movementType: 'ADJUSTMENT_DECREASE',
      direction: 'OUT',
      qualityStatus: 'QUALIFIED',
      trackingMethod: 'SERIAL',
      batchId: 31,
      batchNo: 'B-20260711-001',
      serialId: undefined,
      serialNo: 'SN-20260711-001',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-03',
      page: 1,
      pageSize: 10,
    })
  })

  it('按所有权、项目、计价方法和成本层查询，发送冻结查询字段', async () => {
    const { wrapper } = await mountMovements()

    await setSelectValue(wrapper, 'inventory-movement-ownership-type', 'PROJECT')
    await wrapper.find('input[name="inventory-movement-project-id"]').setValue('501')
    await setSelectValue(wrapper, 'inventory-movement-valuation-method', 'PROJECT_ACTUAL_LAYER')
    await wrapper.find('input[name="inventory-movement-cost-layer-id"]').setValue('9001')
    await wrapper.find('[data-test="search-inventory-movements"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      ownershipType: 'PROJECT',
      projectId: 501,
      valuationMethod: 'PROJECT_ACTUAL_LAYER',
      costLayerId: 9001,
    }))
  })

  it('默认每页显示 10 条并支持切换每页条数', async () => {
    const { wrapper } = await mountMovements()

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 1,
      pageSize: 10,
    }))

    const pagination = wrapper.findComponent({ name: 'ElPagination' })
    expect(pagination.props('pageSize')).toBe(10)
    expect(pagination.props('pageSizes')).toEqual([10, 20, 50, 100])

    pagination.vm.$emit('current-change', 2)
    await flushPromises()
    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 2,
      pageSize: 10,
    }))

    pagination.vm.$emit('size-change', 50)
    await flushPromises()
    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 1,
      pageSize: 50,
    }))
  })

  it('支持从路由查询参数初始化销售出库流水类型', async () => {
    await mountMovements({ movementType: 'SALES_SHIPMENT' })

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      movementType: 'SALES_SHIPMENT',
    }))
  })

  it('忽略路由中的非法流水类型查询参数', async () => {
    await mountMovements({ movementType: 'NOT_A_MOVEMENT' })

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      movementType: undefined,
    }))
  })

  it('变动类型筛选提供生产、采购和销售出库选项并可传递对应流水类型参数', async () => {
    const { wrapper } = await mountMovements()
    const movementTypeOptions = wrapper
      .findAllComponents({ name: 'ElOption' })
      .filter((option) => [
        'OPENING',
        'ADJUSTMENT_INCREASE',
        'ADJUSTMENT_DECREASE',
        'PRODUCTION_ISSUE',
        'PRODUCTION_RECEIPT',
        'PURCHASE_RECEIPT',
        'SALES_SHIPMENT',
      ].includes(String(option.props('value'))))

    expect(movementTypeOptions.map((option) => option.props('label'))).toEqual([
      '期初',
      '调增',
      '调减',
      '生产领料',
      '完工入库',
      '采购入库',
      '销售出库',
    ])

    await setSelectValue(wrapper, 'inventory-movement-type', 'PRODUCTION_ISSUE')
    await wrapper.find('[data-test="search-inventory-movements"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      movementType: 'PRODUCTION_ISSUE',
    }))

    await setSelectValue(wrapper, 'inventory-movement-type', 'PRODUCTION_RECEIPT')
    await wrapper.find('[data-test="search-inventory-movements"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      movementType: 'PRODUCTION_RECEIPT',
    }))

    await setSelectValue(wrapper, 'inventory-movement-type', 'PURCHASE_RECEIPT')
    await wrapper.find('[data-test="search-inventory-movements"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      movementType: 'PURCHASE_RECEIPT',
    }))

    await setSelectValue(wrapper, 'inventory-movement-type', 'SALES_SHIPMENT')
    await wrapper.find('[data-test="search-inventory-movements"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      movementType: 'SALES_SHIPMENT',
    }))
  })

  it('来源单据可跳转到库存单据详情', async () => {
    const { wrapper, router } = await mountMovements()

    await wrapper.find('[data-test="view-source-document"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('inventory-document-detail')
    expect(router.currentRoute.value.params.id).toBe('99')
  })

  it('无数据状态只保留一个清晰空态', async () => {
    inventoryApiMock.movements.list.mockResolvedValueOnce(emptyMovementPage)
    const { wrapper } = await mountMovements()

    expect(wrapper.text()).toContain('暂无库存变动流水')
    expect(wrapper.text().match(/暂无库存变动流水/g)).toHaveLength(1)
  })

  it('点击追溯入口会按批次加载追溯抽屉', async () => {
    const { wrapper } = await mountMovements()

    await wrapper.find('[data-test="view-movement-trace"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.traces.getBatchTrace).toHaveBeenCalledWith(31)
    expect(wrapper.text()).toContain('来源去向追溯')
    expect(wrapper.text()).toContain('MV202607030001')
  })

  it('采购入库来源流水可跳转到采购入库详情', async () => {
    inventoryApiMock.movements.list.mockResolvedValueOnce({
      items: [purchaseReceiptMovement],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const { wrapper, router } = await mountMovements()

    expect(wrapper.text()).toContain('采购入库')
    await wrapper.find('[data-test="view-source-document"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('procurement-receipt-detail')
    expect(router.currentRoute.value.params.id).toBe('700')
  })

  it('缺少采购入库查看权限时不暴露采购入库来源跳转', async () => {
    inventoryApiMock.movements.list.mockResolvedValueOnce({
      items: [purchaseReceiptMovement],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const { wrapper } = await mountMovements({}, ['inventory:movement:view', 'inventory:document:view'])

    expect(wrapper.text()).toContain('采购入库')
    expect(wrapper.text()).toContain('无来源单据权限')
    expect(wrapper.find('[data-test="view-source-document"]').exists()).toBe(false)
  })

  it('销售出库来源流水可跳转到销售出库详情', async () => {
    inventoryApiMock.movements.list.mockResolvedValueOnce({
      items: [salesShipmentMovement],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const { wrapper, router } = await mountMovements()

    expect(wrapper.text()).toContain('销售出库')
    await wrapper.find('[data-test="view-source-document"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('sales-shipment-detail')
    expect(router.currentRoute.value.params.id).toBe('800')
  })

  it('缺少销售出库查看权限时不暴露销售出库来源跳转', async () => {
    inventoryApiMock.movements.list.mockResolvedValueOnce({
      items: [salesShipmentMovement],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const { wrapper } = await mountMovements(
      {},
      ['inventory:movement:view', 'inventory:document:view', 'procurement:receipt:view'],
    )

    expect(wrapper.text()).toContain('销售出库')
    expect(wrapper.text()).toContain('无来源单据权限')
    expect(wrapper.find('[data-test="view-source-document"]').exists()).toBe(false)
  })

  it.each([
    [warehouseTransferMovement, 'inventory-warehouse-transfer-detail', '1001', '仓库调拨出库'],
    [ownershipConversionMovement, 'inventory-ownership-conversion-detail', '1002', '所有权转出'],
    [stocktakeMovement, 'inventory-stocktake-detail', '1003', '盘盈入库'],
    [valuationAdjustmentMovement, 'inventory-valuation-adjustment-detail', '1004', '估值调整'],
  ])('受控库存来源流水可跳转到对应受控单据详情', async (sourceMovement, routeName, id, text) => {
    inventoryApiMock.movements.list.mockResolvedValueOnce({
      items: [sourceMovement],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const { wrapper, router } = await mountMovements()

    expect(wrapper.text()).toContain(text)
    await wrapper.find('[data-test="view-source-document"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe(routeName)
    expect(router.currentRoute.value.params.id).toBe(id)
  })

  it('来源和追溯不可用时展示明确状态文案', async () => {
    inventoryApiMock.movements.list.mockResolvedValueOnce({
      items: [movementWithoutSource, movementWithoutTrackingIdentity],
      page: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
    })
    const { wrapper } = await mountMovements({}, ['inventory:movement:view', 'inventory:document:view'])

    expect(wrapper.text()).toContain('无来源单据')
    expect(wrapper.text()).toContain('无追溯权限')

    inventoryApiMock.movements.list.mockResolvedValueOnce({
      items: [movementWithoutTrackingIdentity],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const withTracePermission = await mountMovements()
    expect(withTracePermission.wrapper.text()).toContain('不追踪物料无追溯')
  })
})
