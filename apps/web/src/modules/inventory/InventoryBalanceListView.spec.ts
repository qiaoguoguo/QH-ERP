import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { InventoryBalanceRecord, InventoryTraceDetailRecord } from '../../shared/api/inventoryApi'
import type { MaterialRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
import { expectDrawerWideTableGoverned } from '../../test/pageGovernanceAssertions'
import { useAuthStore } from '../../stores/authStore'
import InventoryBalanceListView from './InventoryBalanceListView.vue'
import InventoryTraceDrawer from './tracking/InventoryTraceDrawer.vue'
import TrackingAllocationEditor from './tracking/TrackingAllocationEditor.vue'
import TrackingAllocationReadonlyTable from './tracking/TrackingAllocationReadonlyTable.vue'
import TrackingPickerDrawer from './tracking/TrackingPickerDrawer.vue'

const inventoryApiMock = vi.hoisted(() => ({
  balances: {
    list: vi.fn(),
  },
  costLayers: {
    list: vi.fn(),
  },
  reservations: {
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
  specification: '1.2mm',
}

const balance: InventoryBalanceRecord = {
  id: 10,
  warehouseId: 1,
  warehouseCode: 'WH-RAW',
  warehouseName: '原料仓',
  materialId: 2,
  materialCode: 'RM-STEEL',
  materialName: '冷轧钢板',
  materialSpec: '1.2mm',
  materialType: 'RAW_MATERIAL',
  trackingMethod: 'BATCH',
  trackingMethodName: '批次管理',
  batchId: 31,
  batchNo: 'B-20260711-001',
  serialId: null,
  serialNo: null,
  traceableQuantity: '120.500000',
  unitId: 3,
  unitName: '千克',
  qualityStatus: 'QUALIFIED',
  qualityStatusName: '合格',
  bookQuantity: '140.500000',
  quantityOnHand: '120.500000',
  totalQuantityOnHand: '140.500000',
  lockedQuantity: '19.000000',
  availableQuantity: '101.500000',
  pendingInspectionQuantity: '12.000000',
  qualifiedQuantity: '120.500000',
  rejectedQuantity: '3.000000',
  frozenQuantity: '5.000000',
  reservedQuantity: '15.000000',
  occupiedQuantity: '4.000000',
  inTransitQuantity: '30.000000',
  availableToPromiseQuantity: '101.500000',
  netRequirementShortageQuantity: '10.000000',
  unavailableReason: null,
  updatedAt: '2026-07-03T09:30:00+08:00',
}

const projectValuedBalance = {
  ...balance,
  id: 13,
  ownershipType: 'PROJECT',
  ownershipTypeName: '项目库存',
  projectId: 501,
  projectNo: 'PRJ-001',
  projectName: '一号项目',
  costVisible: true,
  valuationState: 'PROJECT_ACTUAL_LAYER',
  valuationStateName: '项目实际成本层',
  inventoryAmount: '1325.50',
  averageUnitCost: '11.000000',
  costLayerCount: 2,
} as InventoryBalanceRecord

const restrictedCostBalance = {
  ...projectValuedBalance,
  id: 14,
  costVisible: false,
  inventoryAmount: null,
  averageUnitCost: null,
} as InventoryBalanceRecord

const nonTrackingBalance: InventoryBalanceRecord = {
  ...balance,
  id: 11,
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  batchId: null,
  batchNo: null,
  serialId: null,
  serialNo: null,
}

const missingTrackingIdentityBalance: InventoryBalanceRecord = {
  ...balance,
  id: 12,
  batchId: null,
  batchNo: null,
}

const reservationPage = {
  items: [
    {
      id: 91,
      reservationNo: 'RSV-20260711-001',
      reservationType: 'RESERVATION',
      reservationTypeName: '销售预留',
      status: 'ACTIVE',
      statusName: '生效',
      warehouseId: 1,
      warehouseName: '原料仓',
      materialId: 2,
      materialCode: 'RM-STEEL',
      materialName: '冷轧钢板',
      unitId: 3,
      unitName: '千克',
      qualityStatus: 'QUALIFIED',
      qualityStatusName: '合格',
      quantity: '15.000000',
      remainingQuantity: '12.000000',
      releasedQuantity: '3.000000',
      consumedQuantity: '0.000000',
      sourceType: 'SALES_ORDER',
      sourceTypeName: '销售订单',
      sourceId: 501,
      sourceLineId: 5001,
      sourceDocumentNo: 'SO-20260711-001',
      businessDate: '2026-07-11',
      createdByName: '销售员',
      createdAt: '2026-07-11T09:00:00+08:00',
    },
  ],
  page: 1,
  pageSize: 20,
  total: 1,
  totalPages: 1,
}

const traceDetail: InventoryTraceDetailRecord = {
  subject: {
    trackingMethod: 'BATCH',
    batchId: 31,
    batchNo: 'B-20260711-001',
    materialCode: 'RM-STEEL',
    materialName: '冷轧钢板',
  },
  currentBalances: [
    {
      warehouseName: '原料仓',
      qualityStatus: 'QUALIFIED',
      qualityStatusName: '合格',
      quantityOnHand: '120.500000',
      availableQuantity: '101.500000',
    },
  ],
  activeReservations: [],
  sourceRecords: [
    {
      nodeType: 'SOURCE',
      nodeTypeName: '来源',
      documentType: 'PURCHASE_RECEIPT',
      documentNo: 'PR-20260711-001',
      businessDate: '2026-07-11',
      direction: 'IN',
      quantity: '120.500000',
      warehouseName: '原料仓',
      permissionRestricted: false,
    },
  ],
  qualityEvents: [],
  outboundRecords: [],
  returnRecords: [],
  movements: [],
  restrictedSources: [
    {
      nodeTypeName: '受限来源',
      documentType: 'PRODUCTION_ORDER',
      documentNo: 'MO-20260711-001',
      businessDate: '2026-07-11',
      permissionRestricted: true,
    },
  ],
}

const balancePage: PageResult<InventoryBalanceRecord> = {
  items: [balance],
  page: 1,
  pageSize: 10,
  total: 1,
  totalPages: 1,
}

const emptyBalancePage: PageResult<InventoryBalanceRecord> = {
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountBalances() {
  return mountBalancesWithPermissions([
    'inventory:balance:view',
    'inventory:reservation:view',
    'inventory:movement:view',
    'inventory:trace:view',
  ])
}

async function mountBalancesWithPermissions(permissions: string[]) {
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
      { path: '/', component: { render: () => null } },
      { path: '/inventory/movements', name: 'inventory-movements', component: { render: () => null } },
    ],
  })
  await router.push('/')
  await router.isReady()

  const wrapper = mount(InventoryBalanceListView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('库存余额页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    inventoryApiMock.balances.list.mockResolvedValue(balancePage)
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
        batchNo: 'B-20260711-001',
        serialNo: 'SN-001',
        originalQuantity: '120.500000',
        originalAmount: '1325.50',
        remainingQuantity: '80.000000',
        remainingAmount: '880.00',
        unitCost: '11.000000',
        status: 'OPEN',
        statusName: '开放',
        sourceType: 'OWNERSHIP_CONVERSION',
        sourceDocumentNo: 'OC-001',
        parentLayerId: 8801,
        createdAt: '2026-07-03T09:00:00+08:00',
      }],
      page: 1,
      pageSize: 20,
      total: 1,
      totalPages: 1,
    })
    inventoryApiMock.reservations.list.mockResolvedValue(reservationPage)
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

  it('加载库存余额并显示仓库、物料、物料类型和右对齐数量', async () => {
    const { wrapper } = await mountBalances()

    expect(wrapper.text()).toContain('库存余额与价值')
    expect(wrapper.text()).toContain('展示公共和项目库存的数量、可用量、估值状态和可授权查看的库存价值。')
    expect(wrapper.text()).toContain('原料仓')
    expect(wrapper.text()).toContain('RM-STEEL')
    expect(wrapper.text()).toContain('原材料')
    expect(wrapper.text()).toContain('批次管理')
    expect(wrapper.text()).toContain('B-20260711-001')
    expect(wrapper.text()).toContain('合格')
    expect(wrapper.text()).toContain('账面数')
    expect(wrapper.text()).toContain('合格现存')
    expect(wrapper.text()).toContain('占用库存')
    expect(wrapper.text()).toContain('预留库存')
    expect(wrapper.text()).toContain('可用数')
    expect(wrapper.text()).toContain('采购在途参考')
    expect(wrapper.text()).toContain('可承诺量')
    expect(wrapper.text()).toContain('净需求缺口')
    expect(wrapper.text()).toContain('待检')
    expect(wrapper.text()).toContain('不合格')
    expect(wrapper.text()).toContain('冻结')
    expect(wrapper.text()).toContain('140.5')
    expect(wrapper.text()).toContain('120.5')
    expect(wrapper.text()).toContain('101.5')
    expect(wrapper.text()).not.toContain('131.5')
    expect(wrapper.text()).toContain('10')
    expect(wrapper.find('[data-test="book-quantity-cell"]').classes()).toContain('numeric-cell')
  })

  it('按关键词、仓库、物料、物料类型、质量状态、追踪条件和正库存条件查询并支持重置', async () => {
    const { wrapper } = await mountBalances()

    await wrapper.find('input[name="inventory-balance-keyword"]').setValue('钢板')
    await setSelectValue(wrapper, 'inventory-balance-warehouse-id', 1)
    await setSelectValue(wrapper, 'inventory-balance-material-id', 2)
    await setSelectValue(wrapper, 'inventory-balance-material-type', 'RAW_MATERIAL')
    await setSelectValue(wrapper, 'inventory-balance-quality-status', 'QUALIFIED')
    await setSelectValue(wrapper, 'inventory-balance-tracking-method', 'BATCH')
    await wrapper.find('input[name="inventory-balance-batch-no"]').setValue('B-20260711-001')
    await wrapper.find('input[name="inventory-balance-serial-no"]').setValue('SN-20260711-001')
    await wrapper.find('input[name="inventory-balance-only-positive"]').setValue(true)
    await wrapper.find('input[name="inventory-balance-include-zero-quality-statuses"]').setValue(true)
    await wrapper.find('[data-test="search-inventory-balances"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.balances.list).toHaveBeenLastCalledWith({
      keyword: '钢板',
      warehouseId: 1,
      materialId: 2,
      materialType: 'RAW_MATERIAL',
      qualityStatus: 'QUALIFIED',
      trackingMethod: 'BATCH',
      batchNo: 'B-20260711-001',
      serialNo: 'SN-20260711-001',
      onlyPositive: true,
      includeZeroQualityStatuses: true,
      page: 1,
      pageSize: 10,
    })

    await wrapper.find('[data-test="reset-inventory-balances"]').trigger('click')
    await flushPromises()
    expect(inventoryApiMock.balances.list).toHaveBeenLastCalledWith({
      keyword: '',
      warehouseId: undefined,
      materialId: undefined,
      materialType: undefined,
      qualityStatus: undefined,
      trackingMethod: undefined,
      batchNo: '',
      serialNo: '',
      onlyPositive: false,
      includeZeroQualityStatuses: false,
      page: 1,
      pageSize: 10,
    })
  })

  it('升级为库存余额与价值视图，成本权限受限时不展示完整金额并可下钻成本层', async () => {
    inventoryApiMock.balances.list.mockResolvedValueOnce({
      items: [projectValuedBalance, restrictedCostBalance],
      page: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
    })
    const { wrapper } = await mountBalancesWithPermissions([
      'inventory:balance:view',
      'inventory:movement:view',
      'inventory:valuation:view',
    ])

    expect(wrapper.text()).toContain('库存余额与价值')
    expect(wrapper.text()).toContain('所有权')
    expect(wrapper.text()).toContain('项目')
    expect(wrapper.text()).toContain('估值状态')
    expect(wrapper.text()).toContain('金额')
    expect(wrapper.text()).toContain('均价')
    expect(wrapper.text()).toContain('成本层')
    expect(wrapper.text()).toContain('项目库存')
    expect(wrapper.text()).toContain('PRJ-001 一号项目')
    expect(wrapper.text()).toContain('1,325.50')
    expect(wrapper.text()).toContain('11.000000')
    expect(wrapper.text()).toContain('金额受限')

    const restrictedRowText = wrapper.find('[data-test="balance-cost-restricted-14"]').text()
    expect(restrictedRowText).toContain('金额受限')
    expect(restrictedRowText).not.toContain('1325.50')

    await wrapper.find('[data-test="view-cost-layers-13"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.costLayers.list).toHaveBeenCalledWith(expect.objectContaining({
      ownershipType: 'PROJECT',
      projectId: 501,
      materialId: 2,
      warehouseId: 1,
      page: 1,
      pageSize: 20,
    }))
    expect(wrapper.text()).toContain('成本层追溯')
    expect(wrapper.text()).toContain('批次/序列')
    expect(wrapper.text()).toContain('原始金额')
    expect(wrapper.text()).toContain('父层')
    expect(wrapper.text()).toContain('来源类型')
    expectDrawerWideTableGoverned(wrapper)
    expect(wrapper.text()).toContain('CL-PRJ-001')
    expect(wrapper.text()).toContain('B-20260711-001 / SN-001')
    expect(wrapper.text()).toContain('1,325.50')
    expect(wrapper.text()).toContain('8801')
    expect(wrapper.text()).toContain('所有权转换')
    expect(wrapper.text()).toContain('880.00')
  })

  it('同仓同项目同物料的不同成本层余额行可区分并按当前层下钻', async () => {
    const firstLayerBalance = {
      ...projectValuedBalance,
      id: 21,
      costLayerId: 9001,
      costLayerCount: 1,
      quantityOnHand: '1.000000',
      availableQuantity: '1.000000',
    } as InventoryBalanceRecord
    const secondLayerBalance = {
      ...projectValuedBalance,
      id: 22,
      costLayerId: 9002,
      costLayerCount: 1,
      quantityOnHand: '1.000000',
      availableQuantity: '1.000000',
    } as InventoryBalanceRecord
    inventoryApiMock.balances.list.mockResolvedValueOnce({
      items: [firstLayerBalance, secondLayerBalance],
      page: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
    })
    const { wrapper, router } = await mountBalancesWithPermissions([
      'inventory:balance:view',
      'inventory:movement:view',
      'inventory:valuation:view',
    ])

    expect(wrapper.text()).toContain('层 #9001')
    expect(wrapper.text()).toContain('层 #9002')

    await wrapper.find('[data-test="view-inventory-movements"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual(expect.objectContaining({
      costLayerId: '9001',
    }))

    await router.push('/')
    await flushPromises()
    await wrapper.find('[data-test="view-cost-layers-21"]').trigger('click')
    await flushPromises()
    expect(inventoryApiMock.costLayers.list).toHaveBeenCalledWith(expect.objectContaining({
      costLayerId: 9001,
    }))
  })

  it('成本权限受限时不展示项目成本层身份', async () => {
    inventoryApiMock.balances.list.mockResolvedValueOnce({
      items: [{
        ...restrictedCostBalance,
        id: 23,
        costLayerId: 9101,
        costLayerCount: 1,
      } as InventoryBalanceRecord],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    const { wrapper } = await mountBalancesWithPermissions([
      'inventory:balance:view',
      'inventory:movement:view',
      'inventory:valuation:view',
    ])

    expect(wrapper.text()).toContain('金额受限')
    expect(wrapper.text()).not.toContain('层 #9101')
  })

  it('桌面首屏优先展示仓库物料、双数量、金额均价、估值状态和成本层列', async () => {
    const { wrapper } = await mountBalancesWithPermissions([
      'inventory:balance:view',
      'inventory:movement:view',
      'inventory:valuation:view',
    ])
    const labels = wrapper.findAllComponents({ name: 'ElTableColumn' })
      .map((column) => column.props('label'))
      .filter(Boolean)

    expect(labels.slice(0, 12)).toEqual([
      '仓库',
      '物料',
      '质量',
      '所有权',
      '项目',
      '账面数',
      '可用数',
      '金额',
      '均价',
      '估值状态',
      '成本层',
      '操作',
    ])
    const actionColumn = wrapper.findAllComponents({ name: 'ElTableColumn' })
      .find((column) => column.props('label') === '操作')
    expect(actionColumn?.props('fixed')).toBe('right')
  })

  it('按所有权、项目和估值状态查询，发送冻结查询字段', async () => {
    const { wrapper } = await mountBalances()

    await setSelectValue(wrapper, 'inventory-balance-ownership-type', 'PROJECT')
    await wrapper.find('input[name="inventory-balance-project-id"]').setValue('501')
    await setSelectValue(wrapper, 'inventory-balance-valuation-state', 'LEGACY_UNVALUED')
    await wrapper.find('[data-test="search-inventory-balances"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.balances.list).toHaveBeenLastCalledWith(expect.objectContaining({
      ownershipType: 'PROJECT',
      projectId: 501,
      valuationState: 'LEGACY_UNVALUED',
    }))
  })

  it('无数据和错误状态有明确提示', async () => {
    inventoryApiMock.balances.list.mockResolvedValueOnce(emptyBalancePage)
    const { wrapper } = await mountBalances()
    expect(wrapper.text()).toContain('暂无库存余额')
    expect(wrapper.text().match(/暂无库存余额/g)).toHaveLength(1)

    inventoryApiMock.balances.list.mockRejectedValueOnce(new Error('库存余额接口异常'))
    await wrapper.find('[data-test="search-inventory-balances"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('库存余额接口异常')
  })

  it('点击查看流水会带仓库和物料筛选跳转到库存变动页', async () => {
    const { wrapper, router } = await mountBalances()

    await wrapper.find('[data-test="view-inventory-movements"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/inventory/movements')
    expect(router.currentRoute.value.query).toEqual({
      warehouseId: '1',
      materialId: '2',
      qualityStatus: 'QUALIFIED',
      trackingMethod: 'BATCH',
      batchId: '31',
      batchNo: 'B-20260711-001',
    })
  })

  it('点击追溯入口会按批次加载追溯抽屉并展示受限摘要', async () => {
    const { wrapper } = await mountBalances()

    await wrapper.find('[data-test="view-inventory-trace"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.traces.getBatchTrace).toHaveBeenCalledWith(31)
    expect(wrapper.text()).toContain('来源去向追溯')
    expect(wrapper.text()).toContain('PR-20260711-001')
    expect(wrapper.text()).toContain('MO-20260711-001')
    expect(wrapper.text()).toContain('权限受限')
  })

  it('追溯入口按权限和追踪身份显示可读状态', async () => {
    const balanceWithoutTracePermissionPage: PageResult<InventoryBalanceRecord> = {
      items: [balance],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    }
    inventoryApiMock.balances.list.mockResolvedValueOnce(balanceWithoutTracePermissionPage)
    const noPermission = await mountBalancesWithPermissions(['inventory:balance:view', 'inventory:movement:view'])
    expect(noPermission.wrapper.text()).toContain('无追溯权限')
    expect(noPermission.wrapper.find('[data-test="view-inventory-trace"]').exists()).toBe(false)

    const noTrackingIdentityPage: PageResult<InventoryBalanceRecord> = {
      items: [nonTrackingBalance, missingTrackingIdentityBalance],
      page: 1,
      pageSize: 10,
      total: 2,
      totalPages: 1,
    }
    inventoryApiMock.balances.list.mockResolvedValueOnce(noTrackingIdentityPage)
    const withPermission = await mountBalances()
    expect(withPermission.wrapper.text()).toContain('不追踪物料无追溯')
    expect(withPermission.wrapper.text()).toContain('缺少批次或序列身份')
  })

  it('占用预留抽屉加载并展示真实来源字段，采购在途仅展示物料级参考摘要', async () => {
    const { wrapper } = await mountBalances()

    const drawers = wrapper.findAllComponents({ name: 'ElDrawer' })
    expect(drawers[0].props('size')).toBe('min(520px, 92vw)')
    expect(drawers[1].props('size')).toBe('min(520px, 92vw)')

    await wrapper.find('[data-test="view-inventory-reservations"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.reservations.list).toHaveBeenCalledWith({
      warehouseId: 1,
      materialId: 2,
      status: 'ACTIVE',
      page: 1,
      pageSize: 20,
    })
    expect(wrapper.text()).toContain('占用预留追溯')
    expect(wrapper.text()).toContain('RSV-20260711-001')
    expect(wrapper.text()).toContain('销售订单')
    expect(wrapper.text()).toContain('SO-20260711-001')
    expect(wrapper.text()).toContain('12')

    await wrapper.find('[data-test="view-inventory-in-transit"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('采购在途参考摘要')
    expect(wrapper.text()).toContain('采购在途按物料汇总展示，不代表当前仓库现货可用。')
    expect(wrapper.text()).not.toContain('采购在途追溯')
  })

  it('缺少占用预留权限时隐藏抽屉入口，避免已进入页面后接口 403', async () => {
    const { wrapper } = await mountBalancesWithPermissions(['inventory:balance:view', 'inventory:movement:view'])

    expect(wrapper.find('[data-test="view-inventory-reservations"]').exists()).toBe(false)
    expect(inventoryApiMock.reservations.list).not.toHaveBeenCalled()
    expect(wrapper.find('[data-test="view-inventory-movements"]').exists()).toBe(true)
  })

  it('通用追踪组件覆盖编辑校验、只读回显、候选禁用和追溯状态', async () => {
    const editor = mount(TrackingAllocationEditor, {
      props: {
        trackingMethod: 'SERIAL',
        expectedQuantity: '2',
        disabled: true,
        disabledReason: '期间已锁定，仅允许查看追踪分配',
        modelValue: [
          { serialNo: 'SN-001', quantity: '1' },
          { serialNo: 'SN-001', quantity: '1' },
          { serialNo: '', quantity: '1' },
        ],
      },
      global: { plugins: [ElementPlus] },
    })
    expect(editor.findAllComponents({ name: 'ElAlert' }).map((alert) => alert.props('title'))).toContain(
      '追踪分配存在 3 项校验问题',
    )
    expect(editor.text()).toContain('序列号重复')
    expect(editor.text()).toContain('第 3 行序列号不能为空')
    expect(editor.text()).toContain('期间已锁定，仅允许查看追踪分配')
    expect(editor.findAll('[data-test="tracking-allocation-error"]')).toHaveLength(3)

    const readonly = mount(TrackingAllocationReadonlyTable, {
      props: {
        trackingMethod: 'BATCH',
        allocations: [{ batchNo: 'B-20260711-001', quantity: '5.000000', qualityStatusName: '合格' }],
      },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(readonly.text()).toContain('B-20260711-001')
    expect(readonly.text()).toContain('5')

    const picker = mount(TrackingPickerDrawer, {
      props: {
        modelValue: true,
        trackingMethod: 'BATCH',
        candidates: [
          {
            id: 31,
            trackingNo: 'B-20260711-001',
            materialCode: 'RM-STEEL',
            materialName: '冷轧钢板',
            warehouseName: '原料仓',
            qualityStatusName: '合格',
            availableQuantity: '0.000000',
            disabled: true,
            disabledReason: '库存已冻结',
          },
        ],
        error: '',
      },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(picker.text()).toContain('库存已冻结')
    expect(picker.find('[data-test="tracking-candidate-disabled-reason"]').text()).toContain('库存已冻结')
    expect(picker.find('[data-test="tracking-candidate-disabled-reason"]').classes()).toContain('candidate-disabled-reason')

    const traceDrawer = mount(InventoryTraceDrawer, {
      props: {
        modelValue: true,
        detail: traceDetail,
        loading: false,
        error: '',
      },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(traceDrawer.text()).toContain('来源去向追溯')
    expect(traceDrawer.text()).toContain('PR-20260711-001')
    expect(traceDrawer.text()).toContain('权限受限')
  })
})
