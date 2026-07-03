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
  quantityOnHand: 120.5,
  lockedQuantity: 0,
  availableQuantity: 120.5,
  updatedAt: '2026-07-03T09:30:00+08:00',
}

const balancePage: PageResult<InventoryBalanceRecord> = {
  items: [balance],
  page: 1,
  pageSize: 20,
  total: 1,
  totalPages: 1,
}

const emptyBalancePage: PageResult<InventoryBalanceRecord> = {
  items: [],
  page: 1,
  pageSize: 20,
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
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: ['inventory:balance:view', 'inventory:movement:view'],
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
    expect(wrapper.text()).toContain('原料仓')
    expect(wrapper.text()).toContain('RM-STEEL')
    expect(wrapper.text()).toContain('原材料')
    expect(wrapper.text()).toContain('120.5')
    expect(wrapper.find('[data-test="quantity-on-hand-cell"]').classes()).toContain('numeric-cell')
  })

  it('按关键词、仓库、物料、物料类型和正库存条件查询并支持重置', async () => {
    const { wrapper } = await mountBalances()

    await wrapper.find('input[name="inventory-balance-keyword"]').setValue('钢板')
    await setSelectValue(wrapper, 'inventory-balance-warehouse-id', 1)
    await setSelectValue(wrapper, 'inventory-balance-material-id', 2)
    await setSelectValue(wrapper, 'inventory-balance-material-type', 'RAW_MATERIAL')
    await wrapper.find('input[name="inventory-balance-only-positive"]').setValue(true)
    await wrapper.find('[data-test="search-inventory-balances"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.balances.list).toHaveBeenLastCalledWith({
      keyword: '钢板',
      warehouseId: 1,
      materialId: 2,
      materialType: 'RAW_MATERIAL',
      onlyPositive: true,
      page: 1,
      pageSize: 20,
    })

    await wrapper.find('[data-test="reset-inventory-balances"]').trigger('click')
    await flushPromises()
    expect(inventoryApiMock.balances.list).toHaveBeenLastCalledWith({
      keyword: '',
      warehouseId: undefined,
      materialId: undefined,
      materialType: undefined,
      onlyPositive: false,
      page: 1,
      pageSize: 20,
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
    expect(router.currentRoute.value.query).toEqual({ warehouseId: '1', materialId: '2' })
  })
})
