import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PayableDetailRecord, PaymentDetailRecord } from '../../shared/api/financeApi'
import PaymentFormView from './PaymentFormView.vue'
import { mountFinanceView } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  payables: { get: vi.fn() },
  payments: { create: vi.fn(), get: vi.fn(), update: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))

const payable: PayableDetailRecord = {
  id: 10,
  payableNo: 'AP-001',
  supplierId: 100,
  supplierCode: 'SUP-A',
  supplierName: '华东供应商',
  sourceType: 'PURCHASE_RECEIPT',
  sourceId: 200,
  sourceNo: 'PR-001',
  purchaseOrderId: 300,
  purchaseOrderNo: 'PO-001',
  businessDate: '2026-07-04',
  dueDate: '2026-07-31',
  totalAmount: '600.00',
  paidAmount: '200.00',
  unpaidAmount: '400.00',
  status: 'PARTIALLY_PAID',
  createdByName: '财务员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
  sources: [],
  payments: [],
}
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
  allocations: [],
}

describe('付款表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    financeApiMock.payables.get.mockResolvedValue(payable)
    financeApiMock.payments.create.mockResolvedValue(payment)
    financeApiMock.payments.get.mockResolvedValue(payment)
    financeApiMock.payments.update.mockResolvedValue(payment)
  })

  it('展示应付摘要并校验超额和零金额', async () => {
    const { wrapper } = await mountFinanceView(PaymentFormView, ['finance:payment:create'], '/finance/payables/10/payments/create')

    expect(financeApiMock.payables.get).toHaveBeenCalledWith('10')
    expect(wrapper.text()).toContain('AP-001')
    expect(wrapper.text()).toContain('未付金额')

    await wrapper.find('input[name="payment-amount"]').setValue('500.00')
    await wrapper.find('[data-test="save-payment"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('付款金额不能超过未付金额')

    await wrapper.find('input[name="payment-amount"]').setValue('0')
    await wrapper.find('[data-test="save-payment"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('付款金额必须大于 0')
  })

  it('提交付款草稿并跳转详情', async () => {
    const { wrapper, router } = await mountFinanceView(PaymentFormView, ['finance:payment:create'], '/finance/payables/10/payments/create')

    expect(wrapper.text()).toContain('多目标分配可在对账核销工作台继续处理')
    expect(wrapper.find('[data-test="payment-method-select"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('银行转账')
    await wrapper.find('input[name="payment-date"]').setValue('2026-07-10')
    await wrapper.find('input[name="payment-amount"]').setValue('300.00')
    await wrapper.find('input[name="payment-remark"]').setValue('登记付款')
    await wrapper.find('[data-test="save-payment"]').trigger('click')
    await flushPromises()

    expect(financeApiMock.payments.create).toHaveBeenCalledWith('10', {
      paymentDate: '2026-07-10',
      amount: '300.00',
      method: 'BANK_TRANSFER',
      remark: '登记付款',
    })
    expect(router.currentRoute.value.name).toBe('finance-payment-detail')
  })

  it('已过账付款编辑页只读且不可保存', async () => {
    financeApiMock.payments.get.mockResolvedValueOnce({ ...payment, status: 'POSTED' })
    const { wrapper } = await mountFinanceView(PaymentFormView, ['finance:payment:update'], '/finance/payments/20/edit')

    expect(wrapper.text()).toContain('非草稿付款只读')
    expect(wrapper.find('input[name="payment-date"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="save-payment"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-test="save-payment"]').trigger('click')
    await flushPromises()
    expect(financeApiMock.payments.update).not.toHaveBeenCalled()
  })
})
