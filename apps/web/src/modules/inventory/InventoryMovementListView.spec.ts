import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { InventoryMovementRecord } from '../../shared/api/inventoryApi'
import type { MaterialRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
import { useAuthStore } from '../../stores/authStore'
import InventoryMovementListView from './InventoryMovementListView.vue'

const inventoryApiMock = vi.hoisted(() => ({
  movements: {
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
  quantity: 3,
  beforeQuantity: 120,
  afterQuantity: 117,
  sourceType: 'INVENTORY_DOCUMENT',
  sourceId: 99,
  sourceLineId: 100,
  businessDate: '2026-07-03',
  reason: '生产损耗调整',
  remark: null,
  operatorName: '管理员',
  occurredAt: '2026-07-03T10:00:00+08:00',
}

const movementPage: PageResult<InventoryMovementRecord> = {
  items: [movement],
  page: 1,
  pageSize: 20,
  total: 1,
  totalPages: 1,
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountMovements(initialQuery: Record<string, string> = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: ['inventory:movement:view', 'inventory:document:view'],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/inventory/movements', name: 'inventory-movements', component: InventoryMovementListView },
      { path: '/inventory/documents/:id', name: 'inventory-document-detail', component: { render: () => null } },
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

    expect(wrapper.text()).toContain('库存变动流水')
    expect(wrapper.text()).toContain('调减')
    expect(wrapper.text()).toContain('出库')
    expect(wrapper.text()).toContain('生产损耗调整')
    expect(wrapper.findAll('[data-test="create-inventory-document"]')).toHaveLength(0)
    expect(wrapper.find('[data-test="movement-quantity-cell"]').classes()).toContain('numeric-cell')
  })

  it('按筛选条件查询并支持来自余额页的仓库物料查询参数', async () => {
    const { wrapper } = await mountMovements({ warehouseId: '1', materialId: '2' })

    expect(inventoryApiMock.movements.list).toHaveBeenLastCalledWith(expect.objectContaining({
      warehouseId: 1,
      materialId: 2,
    }))

    await wrapper.find('input[name="inventory-movement-keyword"]').setValue('钢板')
    await setSelectValue(wrapper, 'inventory-movement-type', 'ADJUSTMENT_DECREASE')
    await setSelectValue(wrapper, 'inventory-movement-direction', 'OUT')
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
      dateFrom: '2026-07-01',
      dateTo: '2026-07-03',
      page: 1,
      pageSize: 20,
    })
  })

  it('来源单据可跳转到库存单据详情', async () => {
    const { wrapper, router } = await mountMovements()

    await wrapper.find('[data-test="view-source-document"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('inventory-document-detail')
    expect(router.currentRoute.value.params.id).toBe('99')
  })
})
