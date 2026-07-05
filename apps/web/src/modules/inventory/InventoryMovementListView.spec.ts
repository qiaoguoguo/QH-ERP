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

const movementPage: PageResult<InventoryMovementRecord> = {
  items: [movement],
  page: 1,
  pageSize: 10,
  total: 1,
  totalPages: 1,
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountMovements(
  initialQuery: Record<string, string> = {},
  permissions = ['inventory:movement:view', 'inventory:document:view', 'procurement:receipt:view', 'sales:shipment:view'],
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
      pageSize: 10,
    })
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
    expect(wrapper.find('[data-test="view-source-document"]').exists()).toBe(false)
  })
})
