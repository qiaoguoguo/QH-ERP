import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { BomDetailRecord, BomSummaryRecord } from '../../shared/api/bomApi'
import type { MaterialRecord, WarehouseRecord } from '../../shared/api/masterDataApi'
import type { ProductionWorkOrderDetailRecord } from '../../shared/api/productionApi'
import { useAuthStore } from '../../stores/authStore'
import ProductionWorkOrderFormView from './ProductionWorkOrderFormView.vue'

const productionApiMock = vi.hoisted(() => ({
  workOrders: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}))

const bomApiMock = vi.hoisted(() => ({
  list: vi.fn(),
  get: vi.fn(),
}))

const masterDataApiMock = vi.hoisted(() => ({
  materials: {
    list: vi.fn(),
  },
  warehouses: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/productionApi', () => ({
  productionApi: productionApiMock,
}))

vi.mock('../../shared/api/bomApi', () => ({
  bomApi: bomApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const productMaterial: MaterialRecord = {
  id: 10,
  code: 'FG-001',
  name: '成品 A',
  materialType: 'FINISHED_GOOD',
  sourceType: 'SELF_MADE',
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  categoryId: 1,
  unitId: 2,
  unitName: '件',
  status: 'ENABLED',
}

const rawMaterial: MaterialRecord = {
  ...productMaterial,
  id: 11,
  code: 'RM-001',
  name: '原材料 A',
  materialType: 'RAW_MATERIAL',
}

const bomSummary: BomSummaryRecord = {
  id: 20,
  bomCode: 'BOM-FG-001',
  parentMaterialId: 10,
  parentMaterialCode: 'FG-001',
  parentMaterialName: '成品 A',
  versionCode: 'V1',
  name: '成品 A 标准 BOM',
  baseQuantity: '1.0000',
  baseUnitId: 2,
  baseUnitName: '件',
  status: 'ENABLED',
  itemCount: 1,
  version: 1,
}

const bomDetail: BomDetailRecord = {
  ...bomSummary,
  items: [
    {
      id: 200,
      lineNo: 10,
      childMaterialId: 11,
      childMaterialCode: 'RM-001',
      childMaterialName: '原材料 A',
      childMaterialType: 'RAW_MATERIAL',
      businessUnitId: 3,
      businessUnitName: '千克',
      businessQuantity: '2.5000',
      baseUnitId: 3,
      baseUnitName: '千克',
      baseQuantity: '2.5000',
      conversionRateSnapshot: '1.0000',
      quantityScaleSnapshot: 4,
      roundingModeSnapshot: 'HALF_UP',
      quantityBasis: 'BASE_UNIT',
      lossRate: '0.0500',
      remark: '标准用料',
    },
  ],
  historyRelations: [],
}

const issueWarehouse: WarehouseRecord = {
  id: 30,
  code: 'WH-RAW',
  name: '原料仓',
  status: 'ENABLED',
}

const receiptWarehouse: WarehouseRecord = {
  id: 31,
  code: 'WH-FG',
  name: '成品仓',
  status: 'ENABLED',
}

const savedWorkOrder: ProductionWorkOrderDetailRecord = {
  id: 99,
  workOrderNo: 'WO-20260703-001',
  productMaterialId: 10,
  productMaterialCode: 'FG-001',
  productMaterialName: '成品 A',
  bomId: 20,
  bomCode: 'BOM-FG-001',
  bomVersionCode: 'V1',
  plannedQuantity: 100,
  reportedQuantity: 0,
  qualifiedQuantity: 0,
  defectiveQuantity: 0,
  receivedQuantity: 0,
  issueWarehouseId: 30,
  issueWarehouseName: '原料仓',
  receiptWarehouseId: 31,
  receiptWarehouseName: '成品仓',
  plannedStartDate: '2026-07-03',
  plannedFinishDate: '2026-07-10',
  status: 'DRAFT',
  remark: null,
  createdByName: '管理员',
  createdAt: '2026-07-03T08:00:00+08:00',
  updatedAt: '2026-07-03T09:00:00+08:00',
  materials: [],
  materialIssues: [],
  reports: [],
  completionReceipts: [],
  movements: [],
}

async function setSelectValue(wrapper: VueWrapper, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index] as VueWrapper | undefined
  expect(select?.exists()).toBe(true)
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountForm(path = '/production/work-orders/create') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions: [
      'production:work-order:view',
      'production:work-order:create',
      'production:work-order:update',
    ],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/production/work-orders', name: 'production-work-orders', component: { render: () => null } },
      { path: '/production/work-orders/create', name: 'production-work-order-create', component: ProductionWorkOrderFormView },
      { path: '/production/work-orders/:id', name: 'production-work-order-detail', component: { render: () => null } },
      { path: '/production/work-orders/:id/edit', name: 'production-work-order-edit', component: ProductionWorkOrderFormView },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(ProductionWorkOrderFormView, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function fillRequiredForm(wrapper: VueWrapper, quantity = '100') {
  await setSelectValue(wrapper, 0, 10)
  await setSelectValue(wrapper, 1, 20)
  await wrapper.find('input[name="production-planned-quantity"]').setValue(quantity)
  await wrapper.find('input[name="production-planned-start-date"]').setValue('2026-07-03')
  await wrapper.find('input[name="production-planned-finish-date"]').setValue('2026-07-10')
  await setSelectValue(wrapper, 2, 30)
  await setSelectValue(wrapper, 3, 31)
}

describe('生产工单表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    productionApiMock.workOrders.get.mockResolvedValue(savedWorkOrder)
    productionApiMock.workOrders.create.mockResolvedValue(savedWorkOrder)
    productionApiMock.workOrders.update.mockResolvedValue(savedWorkOrder)
    bomApiMock.list.mockResolvedValue({
      items: [bomSummary],
      page: 1,
      pageSize: 200,
      total: 1,
      totalPages: 1,
    })
    bomApiMock.get.mockResolvedValue(bomDetail)
    masterDataApiMock.materials.list.mockResolvedValue({
      items: [productMaterial, rawMaterial],
      page: 1,
      pageSize: 200,
      total: 2,
      totalPages: 1,
    })
    masterDataApiMock.warehouses.list.mockResolvedValue({
      items: [issueWarehouse, receiptWarehouse],
      page: 1,
      pageSize: 200,
      total: 2,
      totalPages: 1,
    })
  })

  it('缺少必填项时阻止保存', async () => {
    const { wrapper } = await mountForm()

    await wrapper.find('[data-test="save-production-work-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请完整选择产品物料、BOM、领料仓库和入库仓库')
    expect(productionApiMock.workOrders.create).not.toHaveBeenCalled()
  })

  it('计划数量必须大于 0', async () => {
    const { wrapper } = await mountForm()

    await fillRequiredForm(wrapper, '0')
    await wrapper.find('[data-test="save-production-work-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('数量必须大于 0')
    expect(productionApiMock.workOrders.create).not.toHaveBeenCalled()
  })

  it('计划完工日期不得早于计划开工日期', async () => {
    const { wrapper } = await mountForm()

    await fillRequiredForm(wrapper)
    await wrapper.find('input[name="production-planned-finish-date"]').setValue('2026-07-02')
    await wrapper.find('[data-test="save-production-work-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('计划完工日期不得早于计划开工日期')
    expect(productionApiMock.workOrders.create).not.toHaveBeenCalled()
  })

  it('选择 BOM 后展示 BOM 明细预览', async () => {
    const { wrapper } = await mountForm()

    await setSelectValue(wrapper, 0, 10)
    await setSelectValue(wrapper, 1, 20)
    await flushPromises()

    expect(bomApiMock.get).toHaveBeenCalledWith(20)
    expect(wrapper.text()).toContain('BOM 明细预览')
    expect(wrapper.text()).toContain('RM-001 原材料 A')
    expect(wrapper.text()).toContain('2.5')
    expect(wrapper.text()).toContain('千克')
  })

  it('保存失败后错误可见且输入保留', async () => {
    productionApiMock.workOrders.create.mockRejectedValueOnce(new Error('BOM 已停用，不能创建生产工单'))
    const { wrapper } = await mountForm()

    await fillRequiredForm(wrapper, '12.500000')
    await wrapper.find('input[name="production-work-order-remark"]').setValue('首批试产')
    await wrapper.find('[data-test="save-production-work-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('BOM 已停用，不能创建生产工单')
    expect((wrapper.find('input[name="production-planned-quantity"]').element as HTMLInputElement).value).toBe('12.500000')
    expect((wrapper.find('input[name="production-work-order-remark"]').element as HTMLInputElement).value).toBe('首批试产')
    expect(wrapper.find('[data-test="save-production-work-order"]').attributes('disabled')).toBeUndefined()
  })
})
