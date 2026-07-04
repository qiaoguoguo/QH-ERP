import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PartnerRecord } from '../../shared/api/masterDataApi'
import type { ReceivableSummaryRecord } from '../../shared/api/financeApi'
import ReceivableListView from './ReceivableListView.vue'
import { buttonsByText, mountFinanceView, page, setSelectValue } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  receivables: { list: vi.fn(), confirm: vi.fn(), cancel: vi.fn(), close: vi.fn() },
}))
const masterDataApiMock = vi.hoisted(() => ({
  customers: { list: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))
vi.mock('../../shared/api/masterDataApi', () => ({ masterDataApi: masterDataApiMock }))

const customer: PartnerRecord = { id: 100, code: 'CUS-A', name: '华东客户', status: 'ENABLED' }
const draftReceivable: ReceivableSummaryRecord = {
  id: 1,
  receivableNo: 'AR-DRAFT-001',
  customerId: 100,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  sourceType: 'SALES_SHIPMENT',
  sourceId: 200,
  sourceNo: 'SS-001',
  salesOrderId: 300,
  salesOrderNo: 'SO-001',
  businessDate: '2026-07-04',
  dueDate: '2026-07-31',
  totalAmount: '12345678901234567890.12',
  receivedAmount: '0.00',
  unreceivedAmount: '12345678901234567890.12',
  status: 'DRAFT',
  remark: '应收草稿',
  createdByName: '财务员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
}
const confirmedReceivable: ReceivableSummaryRecord = {
  ...draftReceivable,
  id: 2,
  receivableNo: 'AR-CONF-001',
  status: 'CONFIRMED',
  receivedAmount: '0.00',
  unreceivedAmount: '600.00',
  totalAmount: '600.00',
}
const partialReceivable: ReceivableSummaryRecord = {
  ...draftReceivable,
  id: 3,
  receivableNo: 'AR-PART-001',
  status: 'PARTIALLY_RECEIVED',
  receivedAmount: '200.00',
  unreceivedAmount: '400.00',
  totalAmount: '600.00',
}

describe('应收台账列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('confirm', vi.fn(() => true))
    masterDataApiMock.customers.list.mockResolvedValue(page([customer]))
    financeApiMock.receivables.list.mockResolvedValue(page([draftReceivable, confirmedReceivable, partialReceivable]))
    financeApiMock.receivables.confirm.mockResolvedValue({ ...draftReceivable, status: 'CONFIRMED' })
    financeApiMock.receivables.cancel.mockResolvedValue({ ...draftReceivable, status: 'CANCELLED' })
    financeApiMock.receivables.close.mockResolvedValue({ ...confirmedReceivable, status: 'CLOSED' })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('初始加载客户和应收台账并展示关键字段与安全金额格式', async () => {
    const { wrapper } = await mountFinanceView(ReceivableListView, [
      'finance:receivable:view',
      'finance:receivable:create',
      'finance:receivable:update',
      'finance:receivable:confirm',
      'finance:receivable:cancel',
      'finance:receivable:close',
      'finance:receipt:create',
    ], '/finance/receivables')

    expect(masterDataApiMock.customers.list).toHaveBeenCalledWith({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 })
    expect(financeApiMock.receivables.list).toHaveBeenCalledWith({
      keyword: '',
      customerId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      dueDateFrom: '',
      dueDateTo: '',
      sourceNo: '',
      page: 1,
      pageSize: 20,
    })
    expect(wrapper.text()).toContain('应收台账')
    expect(wrapper.text()).toContain('AR-DRAFT-001')
    expect(wrapper.text()).toContain('华东客户')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('12,345,678,901,234,567,890.12')
    expect(wrapper.find('[data-test="create-receivable"]').exists()).toBe(true)
  })

  it('支持筛选、重置、空态和加载失败', async () => {
    const { wrapper } = await mountFinanceView(ReceivableListView, ['finance:receivable:view'], '/finance/receivables')

    await wrapper.find('input[name="receivable-keyword"]').setValue('AR')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 'CONFIRMED')
    await wrapper.find('input[name="receivable-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="receivable-date-to"]').setValue('2026-07-31')
    await wrapper.find('input[name="receivable-due-date-from"]').setValue('2026-08-01')
    await wrapper.find('input[name="receivable-due-date-to"]').setValue('2026-08-31')
    await wrapper.find('input[name="receivable-source-no"]').setValue('SS')
    await wrapper.find('[data-test="search-receivables"]').trigger('click')
    await flushPromises()

    expect(financeApiMock.receivables.list).toHaveBeenLastCalledWith({
      keyword: 'AR',
      customerId: 100,
      status: 'CONFIRMED',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      dueDateFrom: '2026-08-01',
      dueDateTo: '2026-08-31',
      sourceNo: 'SS',
      page: 1,
      pageSize: 20,
    })

    financeApiMock.receivables.list.mockResolvedValueOnce(page([]))
    await wrapper.find('[data-test="reset-receivables"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无应收台账')

    financeApiMock.receivables.list.mockRejectedValueOnce(new Error('应收接口异常'))
    await wrapper.find('[data-test="search-receivables"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('应收接口异常')
  })

  it('按权限和状态展示操作按钮并执行状态动作', async () => {
    const { wrapper, router } = await mountFinanceView(ReceivableListView, [
      'finance:receivable:view',
      'finance:receivable:update',
      'finance:receivable:confirm',
      'finance:receivable:cancel',
      'finance:receivable:close',
      'finance:receipt:create',
    ], '/finance/receivables')

    expect(buttonsByText(wrapper, '详情')).toHaveLength(3)
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(1)
    expect(buttonsByText(wrapper, '确认')).toHaveLength(1)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(2)
    expect(buttonsByText(wrapper, '关闭')).toHaveLength(2)
    expect(buttonsByText(wrapper, '登记收款')).toHaveLength(2)

    await wrapper.find('[data-test="create-receipt"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('finance-receipt-create')
    expect(router.currentRoute.value.params.id).toBe('2')

    await wrapper.find('[data-test="confirm-receivable"]').trigger('click')
    await flushPromises()
    expect(financeApiMock.receivables.confirm).toHaveBeenCalledWith(1)

    const readonly = await mountFinanceView(ReceivableListView, ['finance:receivable:view'], '/finance/receivables')
    expect(readonly.wrapper.find('[data-test="create-receivable"]').exists()).toBe(false)
    expect(buttonsByText(readonly.wrapper, '详情')).toHaveLength(3)
    expect(buttonsByText(readonly.wrapper, '编辑')).toHaveLength(0)
  })
})
