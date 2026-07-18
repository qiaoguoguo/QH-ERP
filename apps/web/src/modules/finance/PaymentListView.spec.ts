import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PartnerRecord } from '../../shared/api/masterDataApi'
import type { PaymentSummaryRecord } from '../../shared/api/financeApi'
import PaymentListView from './PaymentListView.vue'
import { mountFinanceView, page, setSelectValue } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  payments: { list: vi.fn(), post: vi.fn(), cancel: vi.fn() },
}))
const masterDataApiMock = vi.hoisted(() => ({
  suppliers: { list: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))
vi.mock('../../shared/api/masterDataApi', () => ({ masterDataApi: masterDataApiMock }))

const supplier: PartnerRecord = { id: 100, code: 'SUP-A', name: '华东供应商', status: 'ENABLED' }
const payment: PaymentSummaryRecord = {
  id: 1,
  paymentNo: 'PY-001',
  payableId: 10,
  payableNo: 'AP-001',
  supplierId: 100,
  supplierName: '华东供应商',
  paymentDate: '2026-07-10',
  amount: '100.00',
  method: 'BANK_TRANSFER',
  status: 'DRAFT',
  createdByName: '财务员',
  version: 3,
}

describe('付款记录列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    masterDataApiMock.suppliers.list.mockResolvedValue(page([supplier]))
    financeApiMock.payments.list.mockResolvedValue(page([payment]))
    financeApiMock.payments.post.mockResolvedValue({ ...payment, status: 'POSTED' })
    financeApiMock.payments.cancel.mockResolvedValue({ ...payment, status: 'CANCELLED' })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('加载付款记录并支持筛选、分页、空态和错误态', async () => {
    const { wrapper } = await mountFinanceView(PaymentListView, ['finance:payment:view'], '/finance/payments')

    expect(wrapper.text()).toContain('付款记录')
    expect(wrapper.text()).toContain('PY-001')
    await wrapper.find('input[name="payment-keyword"]').setValue('PY')
    await setSelectValue(wrapper, 0, 100)
    await setSelectValue(wrapper, 1, 'DRAFT')
    await wrapper.find('input[name="payment-date-from"]').setValue('2026-07-01')
    await wrapper.find('input[name="payment-date-to"]').setValue('2026-07-31')
    await wrapper.find('input[name="payment-payable-id"]').setValue('10')
    await wrapper.find('[data-test="search-payments"]').trigger('click')
    await flushPromises()

    expect(financeApiMock.payments.list).toHaveBeenLastCalledWith({
      keyword: 'PY',
      supplierId: 100,
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      payableId: '10',
      page: 1,
      pageSize: 10,
    })

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
    await flushPromises()
    expect(financeApiMock.payments.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))

    financeApiMock.payments.list.mockResolvedValueOnce(page([]))
    await wrapper.find('[data-test="reset-payments"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无付款记录')

    financeApiMock.payments.list.mockRejectedValueOnce(new Error('付款接口异常'))
    await wrapper.find('[data-test="search-payments"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('付款接口异常')
  })

  it('草稿付款按权限显示编辑、过账和取消，并调用过账接口', async () => {
    const { wrapper } = await mountFinanceView(PaymentListView, [
      'finance:payment:view',
      'finance:payment:update',
      'finance:payment:post',
      'finance:payment:cancel',
    ], '/finance/payments')

    expect(wrapper.find('[data-test="edit-payment"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-payment"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-payment"]').exists()).toBe(true)
    await wrapper.find('[data-test="post-payment"]').trigger('click')
    await flushPromises()
    expect(financeApiMock.payments.post).toHaveBeenCalledWith(1)
  })
})
