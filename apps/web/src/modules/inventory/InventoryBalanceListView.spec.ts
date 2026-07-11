import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { InventoryBalanceRecord } from '../../shared/api/inventoryApi'
import type { MaterialRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
import { useAuthStore } from '../../stores/authStore'
import InventoryBalanceListView from './InventoryBalanceListView.vue'

const inventoryApiMock = vi.hoisted(() => ({
  balances: {
    list: vi.fn(),
  },
  reservations: {
    list: vi.fn(),
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
  return mountBalancesWithPermissions(['inventory:balance:view', 'inventory:reservation:view', 'inventory:movement:view'])
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
    inventoryApiMock.reservations.list.mockResolvedValue(reservationPage)
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

    expect(wrapper.text()).toContain('库存余额')
    expect(wrapper.text()).toContain('展示账面库存、合格现存、占用预留、现货净可用、物料级采购在途参考、可承诺量和净需求缺口。')
    expect(wrapper.text()).toContain('原料仓')
    expect(wrapper.text()).toContain('RM-STEEL')
    expect(wrapper.text()).toContain('原材料')
    expect(wrapper.text()).toContain('合格')
    expect(wrapper.text()).toContain('账面库存')
    expect(wrapper.text()).toContain('合格现存')
    expect(wrapper.text()).toContain('占用库存')
    expect(wrapper.text()).toContain('预留库存')
    expect(wrapper.text()).toContain('现货净可用')
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

  it('按关键词、仓库、物料、物料类型、质量状态和正库存条件查询并支持重置', async () => {
    const { wrapper } = await mountBalances()

    await wrapper.find('input[name="inventory-balance-keyword"]').setValue('钢板')
    await setSelectValue(wrapper, 'inventory-balance-warehouse-id', 1)
    await setSelectValue(wrapper, 'inventory-balance-material-id', 2)
    await setSelectValue(wrapper, 'inventory-balance-material-type', 'RAW_MATERIAL')
    await setSelectValue(wrapper, 'inventory-balance-quality-status', 'QUALIFIED')
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
      onlyPositive: false,
      includeZeroQualityStatuses: false,
      page: 1,
      pageSize: 10,
    })
  })

  it('无数据和错误状态有明确提示', async () => {
    inventoryApiMock.balances.list.mockResolvedValueOnce(emptyBalancePage)
    const { wrapper } = await mountBalances()
    expect(wrapper.text()).toContain('暂无库存余额')

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
    expect(router.currentRoute.value.query).toEqual({ warehouseId: '1', materialId: '2', qualityStatus: 'QUALIFIED' })
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
})
