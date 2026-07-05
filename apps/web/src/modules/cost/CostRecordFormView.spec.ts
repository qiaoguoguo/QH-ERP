import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { CostRecordDetailRecord } from '../../shared/api/costCollectionApi'
import type { UnitRecord } from '../../shared/api/masterDataApi'
import type { ProductionWorkOrderSummaryRecord } from '../../shared/api/productionApi'
import CostRecordFormView from './CostRecordFormView.vue'

const costCollectionApiMock = vi.hoisted(() => ({
  records: {
    create: vi.fn(),
    get: vi.fn(),
    update: vi.fn(),
  },
}))

const masterDataApiMock = vi.hoisted(() => ({
  units: {
    list: vi.fn(),
  },
}))

const productionApiMock = vi.hoisted(() => ({
  workOrders: {
    get: vi.fn(),
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/costCollectionApi', () => ({
  costCollectionApi: costCollectionApiMock,
}))

vi.mock('../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

vi.mock('../../shared/api/productionApi', () => ({
  productionApi: productionApiMock,
}))

function pageResult<T>(items: T[]): PageResult<T> {
  return { items, total: items.length, page: 1, pageSize: 100 }
}

const workOrder: ProductionWorkOrderSummaryRecord = {
  id: 9,
  workOrderNo: 'WO-001',
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
  issueWarehouseId: 1,
  issueWarehouseName: '原料仓',
  receiptWarehouseId: 2,
  receiptWarehouseName: '成品仓',
  plannedStartDate: '2026-07-03',
  plannedFinishDate: '2026-07-10',
  status: 'RELEASED',
  createdByName: '管理员',
  createdAt: '2026-07-03T08:00:00+08:00',
  updatedAt: '2026-07-03T08:00:00+08:00',
}

const units: UnitRecord[] = [
  {
    id: 3,
    code: 'PCS',
    name: '件',
    status: 'ENABLED',
    precisionScale: 6,
    sortOrder: 1,
  },
]

const manualDetail: CostRecordDetailRecord = {
  id: 12,
  recordNo: 'COST-012',
  workOrderId: 9,
  workOrderNo: 'WO-001',
  productMaterialId: 10,
  productMaterialCode: 'FG-001',
  productMaterialName: '成品 A',
  costType: 'MANUFACTURING_OVERHEAD',
  sourceType: 'MANUAL_ENTRY',
  sourceDocumentType: 'MANUAL_COST_RECORD',
  sourceDocumentNo: 'MANUAL-001',
  sourceDocumentId: null,
  sourceLineId: null,
  basisType: 'MANUAL_AMOUNT',
  materialId: null,
  materialCode: null,
  materialName: null,
  unitId: null,
  unitName: null,
  quantity: null,
  unitPrice: null,
  amount: 100,
  businessDate: '2026-07-03',
  status: 'ACTIVE',
  remark: '制造费用记录',
  recordedByName: '成本管理员',
  recordedAt: '2026-07-03T10:00:00+08:00',
  createdByName: '成本管理员',
  createdAt: '2026-07-03T10:00:00+08:00',
  updatedAt: '2026-07-03T10:00:00+08:00',
  workOrderStatus: 'IN_PROGRESS',
  sourceStatus: 'ACTIVE',
  sourceSummary: null,
  outputTrace: [],
  auditSummary: [],
}

const automaticDetail: CostRecordDetailRecord = {
  ...manualDetail,
  id: 13,
  recordNo: 'COST-013',
  costType: 'MATERIAL',
  sourceType: 'AUTO_PRODUCTION',
  sourceDocumentType: 'PRODUCTION_MATERIAL_ISSUE',
  sourceDocumentNo: 'MI-001',
  sourceDocumentId: 300,
  sourceLineId: 301,
  basisType: 'SOURCE_QUANTITY_ONLY',
  materialId: 11,
  materialCode: 'RM-001',
  materialName: '原材料 A',
  quantity: 12.5,
  amount: null,
  remark: '自动材料成本',
}

const stubs = {
  MasterDataTableView: {
    props: ['title', 'description'],
    template: '<section><slot name="alerts" /><slot /></section>',
  },
  CostSourceTypeTag: {
    template: '<span />',
  },
  CostTypeTag: {
    template: '<span />',
  },
  ElAlert: {
    props: ['title'],
    template: '<div>{{ title }}</div>',
  },
  ElButton: {
    props: ['disabled', 'loading', 'type'],
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\', $event)"><slot /></button>',
  },
  ElForm: {
    template: '<form><slot /></form>',
  },
  ElFormItem: {
    props: ['label'],
    template: '<label><span>{{ label }}</span><slot /></label>',
  },
  ElInput: {
    props: ['modelValue', 'disabled', 'name', 'placeholder'],
    emits: ['update:modelValue'],
    template:
      '<input :name="name" :disabled="disabled" :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElDatePicker: {
    props: ['modelValue', 'disabled', 'name', 'placeholder'],
    emits: ['update:modelValue'],
    template:
      '<input type="date" :name="name" :disabled="disabled" :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElOption: {
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>',
  },
  ElSelect: {
    props: ['modelValue', 'disabled', 'placeholder'],
    emits: ['update:modelValue'],
    template:
      '<select :disabled="disabled" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option value="">{{ placeholder }}</option><slot /></select>',
  },
}

async function mountForm(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/cost/records/create', name: 'cost-record-create', component: CostRecordFormView },
      { path: '/cost/records/:id/edit', name: 'cost-record-edit', component: CostRecordFormView },
      { path: '/cost/records/:id', name: 'cost-record-detail', component: { render: () => null } },
      { path: '/cost/records', name: 'cost-records', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(CostRecordFormView, {
    global: {
      plugins: [router],
      stubs,
    },
  })
  await flushPromises()
  return { router, wrapper }
}

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text().trim() === text)
  if (!button) {
    throw new Error(`未找到按钮：${text}`)
  }
  return button
}

describe('成本记录表单页关键保存保护', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    productionApiMock.workOrders.list.mockResolvedValue(pageResult([workOrder]))
    masterDataApiMock.units.list.mockResolvedValue(pageResult(units))
  })

  it('编辑路由成本记录未加载完成时禁用保存且不会创建记录', async () => {
    costCollectionApiMock.records.get.mockRejectedValue(new Error('成本记录不存在'))
    const { wrapper } = await mountForm('/cost/records/7/edit')

    const saveButton = wrapper.find('[data-test="save-cost-record"]')
    expect(saveButton.attributes('disabled')).toBeDefined()

    await saveButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('成本记录不存在')
    expect(costCollectionApiMock.records.create).not.toHaveBeenCalled()
    expect(costCollectionApiMock.records.update).not.toHaveBeenCalled()
  })

  it('手工单价数量口径不选择单位时仍提交且 payload 不带 unitId', async () => {
    costCollectionApiMock.records.create.mockResolvedValue({ id: 12 })
    const { wrapper } = await mountForm('/cost/records/create')

    const selects = wrapper.findAll('select')
    await selects[0].setValue('9')
    await selects[1].setValue('LABOR')
    await selects[2].setValue('MANUAL_UNIT_PRICE_QUANTITY')
    await flushPromises()

    await wrapper.find('input[name="cost-business-date"]').setValue('2026-07-03')
    await wrapper.find('input[name="cost-quantity"]').setValue('12.500000')
    await wrapper.find('input[name="cost-unit-price"]').setValue('88.123456')
    await wrapper.find('input[name="cost-remark"]').setValue('人工单价数量业务记录')
    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await flushPromises()

    expect(costCollectionApiMock.records.create).toHaveBeenCalledWith(expect.objectContaining({
      basisType: 'MANUAL_UNIT_PRICE_QUANTITY',
      quantity: '12.500000',
      unitPrice: '88.123456',
      workOrderId: 9,
    }))
    expect(costCollectionApiMock.records.create.mock.calls[0][0]).not.toHaveProperty('unitId')
  })

  it('手工金额口径校验金额并以字符串 payload 保存', async () => {
    costCollectionApiMock.records.create.mockResolvedValue({ id: 14 })
    const { wrapper } = await mountForm('/cost/records/create')

    await wrapper.findAll('select')[0].setValue('9')
    await wrapper.find('input[name="cost-business-date"]').setValue('2026-07-03')
    await wrapper.find('input[name="cost-amount"]').setValue('1.0000001')
    await wrapper.find('input[name="cost-remark"]').setValue('制造费用金额记录')
    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('金额最多 6 位小数')
    expect(costCollectionApiMock.records.create).not.toHaveBeenCalled()

    await wrapper.find('input[name="cost-amount"]').setValue('100.123456')
    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await flushPromises()

    expect(costCollectionApiMock.records.create).toHaveBeenCalledWith(expect.objectContaining({
      amount: '100.123456',
      basisType: 'MANUAL_AMOUNT',
      workOrderId: 9,
    }))
  })

  it('手工单价数量口径校验数量和单价', async () => {
    costCollectionApiMock.records.create.mockResolvedValue({ id: 15 })
    const { wrapper } = await mountForm('/cost/records/create')

    await wrapper.findAll('select')[0].setValue('9')
    await wrapper.findAll('select')[2].setValue('MANUAL_UNIT_PRICE_QUANTITY')
    await flushPromises()
    await wrapper.find('input[name="cost-business-date"]').setValue('2026-07-03')
    await wrapper.find('input[name="cost-quantity"]').setValue('0.000000')
    await wrapper.find('input[name="cost-unit-price"]').setValue('1.000000')
    await wrapper.find('input[name="cost-remark"]').setValue('人工数量记录')
    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('数量必须大于 0')
    expect(costCollectionApiMock.records.create).not.toHaveBeenCalled()

    await wrapper.find('input[name="cost-quantity"]').setValue('2.500000')
    await wrapper.find('input[name="cost-unit-price"]').setValue('1e3')
    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('单价仅支持普通十进制非负数')
    expect(costCollectionApiMock.records.create).not.toHaveBeenCalled()
  })

  it('保存失败时保留输入并显示错误', async () => {
    costCollectionApiMock.records.create.mockRejectedValue(new Error('成本记录保存失败'))
    const { wrapper } = await mountForm('/cost/records/create')

    await wrapper.findAll('select')[0].setValue('9')
    await wrapper.find('input[name="cost-business-date"]').setValue('2026-07-03')
    await wrapper.find('input[name="cost-amount"]').setValue('88.000000')
    await wrapper.find('input[name="cost-remark"]').setValue('保存失败保留')
    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('成本记录保存失败')
    expect((wrapper.find('input[name="cost-amount"]').element as HTMLInputElement).value).toBe('88.000000')
    expect((wrapper.find('input[name="cost-remark"]').element as HTMLInputElement).value).toBe('保存失败保留')
  })

  it('提交中防止重复保存', async () => {
    let resolveSave!: () => void
    costCollectionApiMock.records.create.mockImplementation(() => new Promise((resolve) => {
      resolveSave = () => resolve({ id: 16 })
    }))
    const { wrapper } = await mountForm('/cost/records/create')

    await wrapper.findAll('select')[0].setValue('9')
    await wrapper.find('input[name="cost-business-date"]').setValue('2026-07-03')
    await wrapper.find('input[name="cost-amount"]').setValue('66.000000')
    await wrapper.find('input[name="cost-remark"]').setValue('防重复提交')
    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await flushPromises()

    expect(costCollectionApiMock.records.create).toHaveBeenCalledTimes(1)

    resolveSave()
    await flushPromises()
  })

  it('自动来源成本记录不可编辑也不会提交更新', async () => {
    costCollectionApiMock.records.get.mockResolvedValue(automaticDetail)
    const { wrapper } = await mountForm('/cost/records/13/edit')

    expect(wrapper.text()).toContain('自动来源成本记录不可编辑')
    expect(wrapper.find('[data-test="save-cost-record"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await flushPromises()

    expect(costCollectionApiMock.records.create).not.toHaveBeenCalled()
    expect(costCollectionApiMock.records.update).not.toHaveBeenCalled()
  })

  it('编辑手工记录时调用更新接口并进入详情页', async () => {
    costCollectionApiMock.records.get.mockResolvedValue(manualDetail)
    costCollectionApiMock.records.update.mockResolvedValue({ id: 12 })
    const { router, wrapper } = await mountForm('/cost/records/12/edit')

    await wrapper.find('input[name="cost-amount"]').setValue('123.000000')
    await wrapper.find('input[name="cost-remark"]').setValue('编辑后的说明')
    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await flushPromises()

    expect(costCollectionApiMock.records.update).toHaveBeenCalledWith(12, expect.objectContaining({
      amount: '123.000000',
      basisType: 'MANUAL_AMOUNT',
      remark: '编辑后的说明',
      workOrderId: 9,
    }))
    expect(router.currentRoute.value.name).toBe('cost-record-detail')
    expect(router.currentRoute.value.params.id).toBe('12')
  })

  it('编辑页取消时保留原返回上下文回到详情', async () => {
    costCollectionApiMock.records.get.mockResolvedValue(manualDetail)
    const { router, wrapper } = await mountForm('/cost/records/12/edit?returnTo=/reports/cost')

    await buttonByText(wrapper, '取消').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('cost-record-detail')
    expect(router.currentRoute.value.params.id).toBe('12')
    expect(router.currentRoute.value.query.returnTo).toBe('/reports/cost')
  })

  it('编辑页保存后保留原返回上下文回到详情', async () => {
    costCollectionApiMock.records.get.mockResolvedValue(manualDetail)
    costCollectionApiMock.records.update.mockResolvedValue({ id: 12 })
    const { router, wrapper } = await mountForm('/cost/records/12/edit?returnTo=/reports/cost')

    await wrapper.find('[data-test="save-cost-record"]').trigger('click')
    await flushPromises()

    expect(costCollectionApiMock.records.update).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('cost-record-detail')
    expect(router.currentRoute.value.params.id).toBe('12')
    expect(router.currentRoute.value.query.returnTo).toBe('/reports/cost')
  })
})
