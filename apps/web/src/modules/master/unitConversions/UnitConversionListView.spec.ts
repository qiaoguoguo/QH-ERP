import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UnitConversionListView from './UnitConversionListView.vue'
import type { CandidatePageResult, UnitConversionRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  unitConversions: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    enable: vi.fn(),
    disable: vi.fn(),
    convert: vi.fn(),
    materialCandidates: vi.fn(),
    unitCandidates: vi.fn(),
  },
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: apiMock,
}))

const conversion: UnitConversionRecord = {
  id: 1,
  materialId: 10,
  materialCode: 'MAT-001',
  materialName: '冷轧钢板',
  baseUnitId: 1,
  baseUnitName: '千克',
  businessUnitId: 2,
  businessUnitName: '卷',
  conversionRate: '25.0000',
  quantityScale: 4,
  roundingMode: 'HALF_UP',
  effectiveFrom: '2026-07-01',
  effectiveTo: null,
  status: 'ENABLED',
  lockedReason: '已被 BOM 引用',
  remark: '采购卷换算',
  updatedAt: '2026-07-13T10:00:00+08:00',
  version: 3,
}

const emptyCandidatePage: CandidatePageResult = {
  items: [],
  selectedItems: [],
  page: 1,
  pageSize: 20,
  total: 0,
  totalPages: 0,
}

function mountConversions(permissions = [
  'master:unit-conversion:view',
  'master:unit-conversion:create',
  'master:unit-conversion:update',
  'master:unit-conversion:enable',
  'master:unit-conversion:disable',
]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })

  return mount(UnitConversionListView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

describe('物料单位换算页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.unitConversions.list.mockResolvedValue({ items: [conversion], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    apiMock.unitConversions.get.mockResolvedValue(conversion)
    apiMock.unitConversions.create.mockResolvedValue(conversion)
    apiMock.unitConversions.update.mockResolvedValue(conversion)
    apiMock.unitConversions.enable.mockResolvedValue({ ...conversion, status: 'ENABLED', version: 4 })
    apiMock.unitConversions.disable.mockResolvedValue({ ...conversion, status: 'DISABLED', version: 4 })
    apiMock.unitConversions.convert.mockResolvedValue({
      conversionId: 1,
      materialId: 10,
      businessUnitId: 2,
      businessQuantity: '2.0000',
      baseUnitId: 1,
      baseQuantity: '50.0000',
      conversionRateSnapshot: '25.0000',
      quantityScaleSnapshot: 4,
      roundingModeSnapshot: 'HALF_UP',
    })
    apiMock.unitConversions.materialCandidates.mockResolvedValue({
      ...emptyCandidatePage,
      items: [{ id: 10, code: 'MAT-001', name: '冷轧钢板', status: 'ENABLED' }],
    })
    apiMock.unitConversions.unitCandidates.mockResolvedValue({
      ...emptyCandidatePage,
      items: [{ id: 2, code: 'ROLL', name: '卷', status: 'ENABLED' }],
    })
  })

  it('展示物料、基本单位、业务单位、decimal 比例、有效期和锁定原因', async () => {
    const wrapper = mountConversions()
    await flushPromises()

    expect(wrapper.text()).toContain('物料单位换算')
    expect(wrapper.text()).toContain('MAT-001')
    expect(wrapper.text()).toContain('千克')
    expect(wrapper.text()).toContain('卷')
    expect(wrapper.text()).toContain('25.0000')
    expect(wrapper.text()).toContain('2026-07-01')
    expect(wrapper.text()).toContain('已被 BOM 引用')
  })

  it('新增换算关系提交候选 ID、decimal 字符串、精度、舍入和有效期', async () => {
    const wrapper = mountConversions()
    await flushPromises()

    await wrapper.find('[data-test="create-unit-conversion"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 'unit-conversion-material-id', 10)
    await setSelectValue(wrapper, 'unit-conversion-business-unit-id', 2)
    await wrapper.find('input[name="unit-conversion-rate"]').setValue('25.0000')
    await wrapper.find('input[name="unit-conversion-scale"]').setValue('4')
    await setSelectValue(wrapper, 'unit-conversion-rounding-mode', 'HALF_UP')
    await wrapper.find('input[name="unit-conversion-effective-from"]').setValue('2026-07-01')
    await wrapper.find('[data-test="submit-unit-conversion"]').trigger('click')
    await flushPromises()

    expect(apiMock.unitConversions.create).toHaveBeenCalledWith({
      materialId: 10,
      businessUnitId: 2,
      conversionRate: '25.0000',
      quantityScale: 4,
      roundingMode: 'HALF_UP',
      effectiveFrom: '2026-07-01',
      effectiveTo: null,
      remark: null,
    })
  })

  it('有效期开始晚于结束时不提交并在表单内提示', async () => {
    const wrapper = mountConversions()
    await flushPromises()

    await wrapper.find('[data-test="create-unit-conversion"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 'unit-conversion-material-id', 10)
    await setSelectValue(wrapper, 'unit-conversion-business-unit-id', 2)
    await wrapper.find('input[name="unit-conversion-rate"]').setValue('25.0000')
    await wrapper.find('input[name="unit-conversion-scale"]').setValue('4')
    await wrapper.find('input[name="unit-conversion-effective-from"]').setValue('2026-08-01')
    await wrapper.find('input[name="unit-conversion-effective-to"]').setValue('2026-07-01')
    await wrapper.find('[data-test="submit-unit-conversion"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('开始日期不能晚于结束日期')
    expect(apiMock.unitConversions.create).not.toHaveBeenCalled()
  })

  it('换算预览调用后端 convert 并展示基本单位数量快照', async () => {
    const wrapper = mountConversions()
    await flushPromises()

    await wrapper.find('[data-test="preview-unit-conversion"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="unit-conversion-preview-quantity"]').setValue('2.0000')
    await wrapper.find('[data-test="submit-unit-conversion-preview"]').trigger('click')
    await flushPromises()

    expect(apiMock.unitConversions.convert).toHaveBeenCalledWith({
      materialId: 10,
      businessUnitId: 2,
      businessQuantity: '2.0000',
      businessDate: undefined,
    })
    expect(wrapper.text()).toContain('50.0000')
    expect(wrapper.text()).toContain('25.0000')
  })

  it('只有查看权限时隐藏维护动作', async () => {
    const wrapper = mountConversions(['master:unit-conversion:view'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-unit-conversion"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-unit-conversion"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="disable-unit-conversion"]').exists()).toBe(false)
  })

  it('表单候选支持远程搜索并保留 selectedIds 回显', async () => {
    const wrapper = mountConversions()
    await flushPromises()

    await wrapper.find('[data-test="create-unit-conversion"]').trigger('click')
    await flushPromises()

    const materialSelect = wrapper.findComponent('[data-test="unit-conversion-material-id"]') as VueWrapper
    const unitSelect = wrapper.findComponent('[data-test="unit-conversion-business-unit-id"]') as VueWrapper
    const materialSelectProps = materialSelect.props() as Record<string, unknown>
    const unitSelectProps = unitSelect.props() as Record<string, unknown>
    expect(materialSelectProps.remote).toBe(true)
    expect(unitSelectProps.remote).toBe(true)

    const loadMaterials = materialSelectProps.remoteMethod as (keyword: string) => void
    const loadUnits = unitSelectProps.remoteMethod as (keyword: string) => void
    loadMaterials('钢')
    loadUnits('卷')
    await flushPromises()

    expect(apiMock.unitConversions.materialCandidates).toHaveBeenLastCalledWith({
      keyword: '钢',
      page: 1,
      pageSize: 20,
      selectedIds: [],
    })
    expect(apiMock.unitConversions.unitCandidates).toHaveBeenLastCalledWith({
      keyword: '卷',
      page: 1,
      pageSize: 20,
      selectedIds: [],
    })

    await wrapper.find('[data-test="edit-unit-conversion"]').trigger('click')
    await flushPromises()
    expect(apiMock.unitConversions.materialCandidates).toHaveBeenLastCalledWith(expect.objectContaining({
      selectedIds: [10],
    }))
    expect(apiMock.unitConversions.unitCandidates).toHaveBeenLastCalledWith(expect.objectContaining({
      selectedIds: [2],
    }))
  })
})
