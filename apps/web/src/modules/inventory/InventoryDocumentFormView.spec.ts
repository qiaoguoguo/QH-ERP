import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { InventoryDocumentDetailRecord } from '../../shared/api/inventoryApi'
import type { MaterialRecord, UnitRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
import { useAuthStore } from '../../stores/authStore'
import InventoryDocumentFormView from './InventoryDocumentFormView.vue'

const inventoryApiMock = vi.hoisted(() => ({
  documents: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  warehouses: {
    list: vi.fn(),
  },
  materials: {
    list: vi.fn(),
  },
  units: {
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
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  categoryId: 1,
  unitId: 3,
  unitName: '千克',
  status: 'ENABLED',
}

const unit: UnitRecord = {
  id: 3,
  code: 'KG',
  name: '千克',
  precisionScale: 3,
  sortOrder: 1,
  status: 'ENABLED',
}

const savedDocument: InventoryDocumentDetailRecord = {
  id: 99,
  documentNo: 'INV-OPEN-001',
  documentType: 'OPENING',
  status: 'DRAFT',
  businessDate: '2026-07-03',
  reason: '期初导入',
  remark: null,
  lineCount: 1,
  createdByName: '管理员',
  createdAt: '2026-07-03T09:00:00+08:00',
  updatedAt: '2026-07-03T09:00:00+08:00',
  postedByName: null,
  postedAt: null,
  lines: [
    {
      id: 100,
      lineNo: 10,
      warehouseId: 1,
      warehouseName: '原料仓',
      materialId: 2,
      materialCode: 'RM-STEEL',
      materialName: '冷轧钢板',
      unitId: 3,
      unitName: '千克',
      quantity: 12.5,
      adjustmentDirection: null,
    },
  ],
}

const postedDocument: InventoryDocumentDetailRecord = {
  ...savedDocument,
  status: 'POSTED',
  postedByName: '管理员',
  postedAt: '2026-07-03T10:00:00+08:00',
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountForm(query: Record<string, string> = { type: 'OPENING' }) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: ['inventory:document:create', 'inventory:document:update', 'inventory:document:view'],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/inventory/documents/create', name: 'inventory-document-create', component: InventoryDocumentFormView },
      { path: '/inventory/documents/:id', name: 'inventory-document-detail', component: { render: () => null } },
      { path: '/inventory/documents/:id/edit', name: 'inventory-document-edit', component: InventoryDocumentFormView },
    ],
  })
  await router.push({ path: '/inventory/documents/create', query })
  await router.isReady()
  const wrapper = mount(InventoryDocumentFormView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function mountEditForm(record: InventoryDocumentDetailRecord) {
  inventoryApiMock.documents.get.mockResolvedValueOnce(record)
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: ['inventory:document:update', 'inventory:document:view'],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/inventory/documents', name: 'inventory-documents', component: { render: () => null } },
      { path: '/inventory/documents/:id', name: 'inventory-document-detail', component: { render: () => null } },
      { path: '/inventory/documents/:id/edit', name: 'inventory-document-edit', component: InventoryDocumentFormView },
    ],
  })
  await router.push('/inventory/documents/99/edit')
  await router.isReady()
  const wrapper = mount(InventoryDocumentFormView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function fillHeader(wrapper: VueWrapper) {
  await wrapper.find('input[name="inventory-document-business-date"]').setValue('2026-07-03')
  await wrapper.find('input[name="inventory-document-reason"]').setValue('期初导入')
}

async function fillFirstLine(wrapper: VueWrapper, quantity = '12.5') {
  await setSelectValue(wrapper, 'inventory-line-warehouse-id-0', 1)
  await setSelectValue(wrapper, 'inventory-line-material-id-0', 2)
  await wrapper.find('input[name="inventory-line-quantity-0"]').setValue(quantity)
}

describe('库存单据表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    inventoryApiMock.documents.create.mockResolvedValue(savedDocument)
    inventoryApiMock.documents.update.mockResolvedValue(savedDocument)
    inventoryApiMock.documents.get.mockResolvedValue(savedDocument)
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
    masterDataApiMock.units.list.mockResolvedValue({
      items: [unit],
      page: 1,
      pageSize: 100,
      total: 1,
      totalPages: 1,
    })
  })

  it('期初模式固定单据类型、隐藏调整方向，并保存明细后跳转详情', async () => {
    const { wrapper, router } = await mountForm({ type: 'OPENING' })

    expect(wrapper.text()).toContain('期初库存')
    expect(wrapper.text()).not.toContain('调整方向')
    expect(wrapper.find('[data-test="inventory-line-adjustment-direction-0"]').exists()).toBe(false)

    await fillHeader(wrapper)
    await fillFirstLine(wrapper)
    await wrapper.find('[data-test="save-inventory-document"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.documents.create).toHaveBeenCalledWith({
      documentType: 'OPENING',
      businessDate: '2026-07-03',
      reason: '期初导入',
      lines: [
        {
          lineNo: 10,
          warehouseId: 1,
          materialId: 2,
          unitId: 3,
          quantity: '12.5',
        },
      ],
    })
    expect(router.currentRoute.value.name).toBe('inventory-document-detail')
    expect(router.currentRoute.value.params.id).toBe('99')
  })

  it('调整模式要求调整方向并提交调增或调减方向', async () => {
    const { wrapper } = await mountForm({ type: 'ADJUSTMENT' })

    expect(wrapper.text()).toContain('库存调整')
    expect(wrapper.text()).toContain('调整方向')
    expect(wrapper.find('[data-test="inventory-line-adjustment-direction-0"]').exists()).toBe(true)

    await fillHeader(wrapper)
    await fillFirstLine(wrapper)
    await wrapper.find('[data-test="save-inventory-document"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('第 10 行请选择调整方向')
    expect(inventoryApiMock.documents.create).not.toHaveBeenCalled()

    await setSelectValue(wrapper, 'inventory-line-adjustment-direction-0', 'DECREASE')
    await wrapper.find('[data-test="save-inventory-document"]').trigger('click')
    await flushPromises()
    expect(inventoryApiMock.documents.create).toHaveBeenCalledWith(expect.objectContaining({
      documentType: 'ADJUSTMENT',
      lines: [expect.objectContaining({ adjustmentDirection: 'DECREASE' })],
    }))
  })

  it('重复仓库物料和无效数量会阻止提交', async () => {
    const { wrapper } = await mountForm({ type: 'OPENING' })

    await fillHeader(wrapper)
    await fillFirstLine(wrapper, '0')
    await wrapper.find('[data-test="save-inventory-document"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('第 10 行数量必须大于 0')
    expect(inventoryApiMock.documents.create).not.toHaveBeenCalled()

    await wrapper.find('input[name="inventory-line-quantity-0"]').setValue('12')
    await wrapper.find('[data-test="add-inventory-line"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 'inventory-line-warehouse-id-1', 1)
    await setSelectValue(wrapper, 'inventory-line-material-id-1', 2)
    await wrapper.find('input[name="inventory-line-quantity-1"]').setValue('1')
    await wrapper.find('[data-test="save-inventory-document"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('同一单据内仓库和物料不能重复')
    expect(inventoryApiMock.documents.create).not.toHaveBeenCalled()
  })

  it.each([
    ['0.0000001', '第 10 行数量最多 6 位小数'],
    ['1000000000000', '第 10 行数量整数部分最多 12 位'],
    ['1e2', '第 10 行数量仅支持普通十进制正数'],
  ])('数量 %s 不符合 NUMERIC(18,6) 口径时阻止提交', async (quantity, errorText) => {
    const { wrapper } = await mountForm({ type: 'OPENING' })

    await fillHeader(wrapper)
    await fillFirstLine(wrapper, quantity)
    await wrapper.find('[data-test="save-inventory-document"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain(errorText)
    expect(inventoryApiMock.documents.create).not.toHaveBeenCalled()
  })

  it('合法最大边界数量提交时保持十进制字符串不被 JS number 舍入', async () => {
    const maxQuantity = '999999999999.999999'
    const { wrapper } = await mountForm({ type: 'OPENING' })

    await fillHeader(wrapper)
    await fillFirstLine(wrapper, maxQuantity)
    await wrapper.find('[data-test="save-inventory-document"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.documents.create).toHaveBeenCalledWith(expect.objectContaining({
      lines: [expect.objectContaining({ quantity: maxQuantity })],
    }))
  })

  it('保存失败时错误可见且表单数据保留', async () => {
    inventoryApiMock.documents.create.mockRejectedValueOnce(new Error('同一仓库物料已存在已过账期初'))
    const { wrapper } = await mountForm({ type: 'OPENING' })

    await fillHeader(wrapper)
    await fillFirstLine(wrapper, '12.5')
    await wrapper.find('[data-test="save-inventory-document"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('同一仓库物料已存在已过账期初')
    expect((wrapper.find('input[name="inventory-document-reason"]').element as HTMLInputElement).value).toBe('期初导入')
    expect((wrapper.find('input[name="inventory-line-quantity-0"]').element as HTMLInputElement).value).toBe('12.5')
    expect(wrapper.find('[data-test="save-inventory-document"]').attributes('disabled')).toBeUndefined()
  })

  it('直接访问已过账单据编辑页时只读且不能保存', async () => {
    const { wrapper } = await mountEditForm(postedDocument)

    expect(wrapper.text()).toContain('已过账，不可编辑')
    const warehouseSelectProps = (wrapper.findComponent('[data-test="inventory-line-warehouse-id-0"]') as VueWrapper)
      .props() as { disabled?: boolean }
    expect(warehouseSelectProps.disabled).toBe(true)
    expect(wrapper.find('[data-test="save-inventory-document"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-test="save-inventory-document"]').trigger('click')
    await flushPromises()

    expect(inventoryApiMock.documents.update).not.toHaveBeenCalled()
  })
})
