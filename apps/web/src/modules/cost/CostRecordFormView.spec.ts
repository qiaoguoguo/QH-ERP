import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import type { PageResult } from '../../shared/api/accountPermissionApi'
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

describe('成本记录表单页关键保存保护', () => {
  it('编辑路由成本记录未加载完成时禁用保存且不会创建记录', async () => {
    productionApiMock.workOrders.list.mockResolvedValue(pageResult([workOrder]))
    masterDataApiMock.units.list.mockResolvedValue(pageResult(units))
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
    productionApiMock.workOrders.list.mockResolvedValue(pageResult([workOrder]))
    masterDataApiMock.units.list.mockResolvedValue(pageResult(units))
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
})
