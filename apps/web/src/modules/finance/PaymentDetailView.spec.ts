import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PaymentDetailRecord } from '../../shared/api/financeApi'
import PaymentDetailView from './PaymentDetailView.vue'
import { mountFinanceView } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  payments: { get: vi.fn(), post: vi.fn(), cancel: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))

const payment: PaymentDetailRecord = {
  id: 20,
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
  allocations: [{
    id: 1,
    paymentId: 20,
    paymentNo: 'PY-001',
    payableId: 10,
    payableNo: 'AP-001',
    supplierId: 100,
    supplierName: '华东供应商',
    allocatedAmount: '100.00',
  }],
}

describe('付款详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    financeApiMock.payments.get.mockResolvedValue(payment)
    financeApiMock.payments.post.mockResolvedValue({ ...payment, status: 'POSTED' })
    financeApiMock.payments.cancel.mockResolvedValue({ ...payment, status: 'CANCELLED' })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('展示付款详情、核销应付并支持跳转应付', async () => {
    const { wrapper, router } = await mountFinanceView(PaymentDetailView, [
      'finance:payment:view',
      'finance:payment:update',
      'finance:payment:post',
      'finance:payment:cancel',
      'finance:payable:view',
    ], '/finance/payments/20')

    expect(wrapper.text()).toContain('PY-001')
    expect(wrapper.text()).toContain('多目标核销明细')
    expect(wrapper.text()).toContain('预付余额')
    expect(wrapper.text()).toContain('费用链接')
    expect(wrapper.text()).toContain('凭证草稿摘要')
    expect(wrapper.text()).toContain('AP-001')
    expect(wrapper.find('[data-test="post-payment-detail"]').exists()).toBe(true)

    await wrapper.find('[data-test="view-payable-from-payment"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('finance-payable-detail')
  })

  it('过账动作调用付款接口', async () => {
    const { wrapper } = await mountFinanceView(PaymentDetailView, ['finance:payment:view', 'finance:payment:post'], '/finance/payments/20')

    await wrapper.find('[data-test="post-payment-detail"]').trigger('click')
    await flushPromises()
    expect(financeApiMock.payments.post).toHaveBeenCalledWith(20)
  })
})
