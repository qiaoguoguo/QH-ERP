import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/authStore'
import QualityInspectionListView from './QualityInspectionListView.vue'

const qualityApiMock = vi.hoisted(() => ({
  inspections: {
    list: vi.fn(),
    get: vi.fn(),
    process: vi.fn(),
  },
  qualityTransfers: {
    freeze: vi.fn(),
    unfreeze: vi.fn(),
  },
}))

vi.mock('../../shared/api/qualityInventoryStatusApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/qualityInventoryStatusApi')>()),
  qualityInventoryStatusApi: qualityApiMock,
}))

const pendingInspection = {
  id: 9,
  inspectionNo: 'QI202607100001',
  sourceType: 'PURCHASE_RECEIPT',
  sourceTypeName: '采购入库',
  sourceId: 20,
  sourceLineId: 201,
  sourceDocumentNo: 'RC202607100001',
  warehouseId: 1,
  warehouseCode: 'RAW',
  warehouseName: '原料仓',
  materialId: 11,
  materialCode: 'RM-001',
  materialName: '冷轧钢板',
  materialSpec: '1.2mm',
  unitId: 3,
  unitName: '千克',
  inspectionQuantity: '10.000000',
  remainingQuantity: '10.000000',
  qualifiedQuantity: '0.000000',
  rejectedQuantity: '0.000000',
  frozenQuantity: '0.000000',
  status: 'PENDING',
  statusName: '待处理',
  businessDate: '2026-07-10',
  createdByName: '管理员',
  createdAt: '2026-07-10T10:00:00+08:00',
  completedByName: null,
  completedAt: null,
  reason: null,
  remark: null,
  version: 1,
  canProcess: true,
  disabledReason: null,
}

const completedInspection = {
  ...pendingInspection,
  id: 10,
  inspectionNo: 'QI202607100002',
  status: 'COMPLETED',
  statusName: '已处理',
  remainingQuantity: '0.000000',
  qualifiedQuantity: '8.000000',
  rejectedQuantity: '1.000000',
  frozenQuantity: '1.000000',
  canProcess: false,
  disabledReason: '已处理记录不可重复确认',
}

const inspectionDetail = {
  ...pendingInspection,
  sourceSummary: {
    sourceDocumentNo: 'RC202607100001',
    supplierName: '示例供应商',
  },
  currentQualityStatus: 'PENDING_INSPECTION',
  currentQualityStatusName: '待检',
  auditRecords: [
    {
      action: 'CREATE',
      actionName: '生成待检',
      operatorName: '管理员',
      operatedAt: '2026-07-10T10:00:00+08:00',
      businessDate: '2026-07-10',
      reason: '采购入库形成待检',
      remark: null,
    },
  ],
}

function page(items: unknown[]) {
  return {
    items,
    page: 1,
    pageSize: 10,
    total: items.length,
    totalPages: 1,
  }
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function mountQuality(permissions = ['quality:inspection:view', 'quality:inspection:process']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'quality_user', displayName: '质量员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const wrapper = mount(QualityInspectionListView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
  await flushPromises()
  return wrapper
}

describe('质量确认前端页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    qualityApiMock.inspections.list.mockResolvedValue(page([pendingInspection, completedInspection]))
    qualityApiMock.inspections.get.mockResolvedValue(inspectionDetail)
    qualityApiMock.inspections.process.mockResolvedValue({
      ...inspectionDetail,
      status: 'COMPLETED',
      statusName: '已处理',
      remainingQuantity: '0.000000',
    })
  })

  it('展示质量确认列表、待检质量状态、不可处理原因和固定右侧操作列', async () => {
    const wrapper = await mountQuality()

    expect(wrapper.text()).toContain('质量确认')
    expect(wrapper.text()).toContain('待检库存经质量确认转为合格、不合格或冻结')
    expect(wrapper.text()).toContain('QI202607100001')
    expect(wrapper.text()).toContain('RC202607100001')
    expect(wrapper.text()).toContain('冷轧钢板')
    expect(wrapper.text()).toContain('待检')
    expect(wrapper.text()).toContain('已处理记录不可重复确认')
    expect(wrapper.find('[data-test="process-quality-inspection"]').exists()).toBe(true)
    const operationColumn = wrapper.findAllComponents({ name: 'ElTableColumn' })
      .find((column) => column.props('label') === '操作')
    expect(operationColumn?.props('fixed')).toBe('right')
  })

  it('支持列表筛选和重置', async () => {
    const wrapper = await mountQuality()

    await wrapper.find('input[name="quality-inspection-keyword"]').setValue('RM-001')
    await setSelectValue(wrapper, 'quality-inspection-status', 'PENDING')
    await setSelectValue(wrapper, 'quality-inspection-source-type', 'PURCHASE_RECEIPT')
    await wrapper.find('input[name="quality-inspection-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="quality-inspection-date-to"]').setValue('2026-07-10')
    await wrapper.find('[data-test="search-quality-inspections"]').trigger('click')
    await flushPromises()

    expect(qualityApiMock.inspections.list).toHaveBeenLastCalledWith({
      keyword: 'RM-001',
      sourceType: 'PURCHASE_RECEIPT',
      status: 'PENDING',
      qualityStatus: 'PENDING_INSPECTION',
      businessDateFrom: '2026-07-01',
      businessDateTo: '2026-07-10',
      page: 1,
      pageSize: 10,
    })

    await wrapper.find('[data-test="reset-quality-inspections"]').trigger('click')
    await flushPromises()
    expect(qualityApiMock.inspections.list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: '',
      sourceType: undefined,
      status: undefined,
      qualityStatus: 'PENDING_INSPECTION',
      businessDateFrom: '',
      businessDateTo: '',
    }))
  })

  it('处理抽屉按固定字段顺序展示并拦截合计不平，错误后保留输入且不关闭', async () => {
    const wrapper = await mountQuality()

    await wrapper.find('[data-test="process-quality-inspection"]').trigger('click')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('合格数量')
    expect(text.indexOf('业务日期')).toBeLessThan(text.indexOf('合格数量'))
    expect(text.indexOf('合格数量')).toBeLessThan(text.indexOf('不合格数量'))
    expect(text.indexOf('不合格数量')).toBeLessThan(text.indexOf('冻结数量'))
    expect(text.indexOf('冻结数量')).toBeLessThan(text.indexOf('原因'))
    expect(text.indexOf('原因')).toBeLessThan(text.indexOf('备注'))
    expect(wrapper.find('input[name="quality-process-business-date"]').exists()).toBe(true)

    await wrapper.find('input[name="quality-process-qualified-quantity"]').setValue('7.000000')
    await wrapper.find('input[name="quality-process-rejected-quantity"]').setValue('1.000000')
    await wrapper.find('input[name="quality-process-frozen-quantity"]').setValue('1.000000')
    await wrapper.find('textarea[name="quality-process-reason"]').setValue('检验完成')
    await wrapper.find('[data-test="submit-quality-process"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('合格、不合格和冻结数量合计必须等于待检数量')
    expect((wrapper.find('input[name="quality-process-qualified-quantity"]').element as HTMLInputElement).value)
      .toBe('7.000000')
    expect(wrapper.text()).toContain('处理质量确认')
    expect(qualityApiMock.inspections.process).not.toHaveBeenCalled()
  })

  it('处理抽屉使用响应式宽度并兼容详情质量状态字段', async () => {
    qualityApiMock.inspections.get.mockResolvedValueOnce({
      ...inspectionDetail,
      currentQualityStatus: undefined,
      currentQualityStatusName: undefined,
      qualityStatus: 'PENDING_INSPECTION',
      qualityStatusName: '待检',
    })
    const wrapper = await mountQuality()

    await wrapper.find('[data-test="process-quality-inspection"]').trigger('click')
    await flushPromises()

    expect(wrapper.findComponent({ name: 'ElDrawer' }).props('size')).toBe('min(560px, calc(100vw - 16px))')
    expect(wrapper.text()).toContain('当前质量状态')
    expect(wrapper.text()).toContain('待检')
  })

  it('提交处理时传递字符串数量，后端错误可见且抽屉保持打开', async () => {
    qualityApiMock.inspections.process.mockRejectedValueOnce(new Error('业务日期 2026-07-10 所属期间 2026-07 已锁定'))
    const wrapper = await mountQuality()

    await wrapper.find('[data-test="process-quality-inspection"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="quality-process-qualified-quantity"]').setValue('8.000000')
    await wrapper.find('input[name="quality-process-rejected-quantity"]').setValue('1.000000')
    await wrapper.find('input[name="quality-process-frozen-quantity"]').setValue('1.000000')
    await wrapper.find('textarea[name="quality-process-reason"]').setValue('检验完成')
    await wrapper.find('[data-test="submit-quality-process"]').trigger('click')
    await flushPromises()

    expect(qualityApiMock.inspections.process).toHaveBeenCalledWith(9, {
      businessDate: '2026-07-10',
      qualifiedQuantity: '8.000000',
      rejectedQuantity: '1.000000',
      frozenQuantity: '1.000000',
      reason: '检验完成',
    })
    expect(wrapper.text()).toContain('业务日期 2026-07-10 所属期间 2026-07 已锁定')
    expect(wrapper.text()).toContain('处理质量确认')
  })

  it('只读用户可查看列表但不能处理质量确认', async () => {
    const wrapper = await mountQuality(['quality:inspection:view'])

    expect(wrapper.text()).toContain('QI202607100001')
    expect(wrapper.find('[data-test="process-quality-inspection"]').exists()).toBe(false)
  })
})
