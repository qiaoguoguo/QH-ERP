import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReceivableDetailRecord, ReceiptDetailRecord } from '../../shared/api/financeApi'
import ReceiptFormView from './ReceiptFormView.vue'
import { mountFinanceView } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  receivables: { get: vi.fn() },
  receipts: { create: vi.fn(), get: vi.fn(), update: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))

const receivable: ReceivableDetailRecord = {
  id: 10,
  receivableNo: 'AR-001',
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
  totalAmount: '600.00',
  receivedAmount: '200.00',
  unreceivedAmount: '400.00',
  status: 'PARTIALLY_RECEIVED',
  createdByName: '财务员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
  sources: [],
  receipts: [],
}
const receipt: ReceiptDetailRecord = {
  id: 20,
  receiptNo: 'RC-001',
  receivableId: 10,
  receivableNo: 'AR-001',
  customerId: 100,
  customerName: '华东客户',
  receiptDate: '2026-07-10',
  amount: '100.00',
  method: 'BANK_TRANSFER',
  status: 'DRAFT',
  createdByName: '财务员',
  version: 3,
  allocations: [],
}

describe('收款表单页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    financeApiMock.receivables.get.mockResolvedValue(receivable)
    financeApiMock.receipts.create.mockResolvedValue(receipt)
    financeApiMock.receipts.get.mockResolvedValue(receipt)
    financeApiMock.receipts.update.mockResolvedValue(receipt)
  })

  it('展示应收摘要并校验超额和零金额', async () => {
    const { wrapper } = await mountFinanceView(ReceiptFormView, ['finance:receipt:create'], '/finance/receivables/10/receipts/create')

    expect(financeApiMock.receivables.get).toHaveBeenCalledWith('10')
    expect(wrapper.text()).toContain('AR-001')
    expect(wrapper.text()).toContain('华东客户')
    expect(wrapper.text()).toContain('未收金额')

    await wrapper.find('input[name="receipt-amount"]').setValue('500.00')
    await wrapper.find('[data-test="save-receipt"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('收款金额不能超过未收金额')

    await wrapper.find('input[name="receipt-amount"]').setValue('0')
    await wrapper.find('[data-test="save-receipt"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('收款金额必须大于 0')
  })

  it('提交收款草稿并跳转详情', async () => {
    const { wrapper, router } = await mountFinanceView(ReceiptFormView, ['finance:receipt:create'], '/finance/receivables/10/receipts/create')

    expect(wrapper.text()).toContain('多目标分配可在对账核销工作台继续处理')
    expect(wrapper.find('[data-test="receipt-method-select"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('银行转账')
    await wrapper.find('input[name="receipt-date"]').setValue('2026-07-10')
    await wrapper.find('input[name="receipt-amount"]').setValue('300.00')
    await wrapper.find('input[name="receipt-remark"]').setValue('登记收款')
    await wrapper.find('[data-test="save-receipt"]').trigger('click')
    await flushPromises()

    expect(financeApiMock.receipts.create).toHaveBeenCalledWith('10', {
      receiptDate: '2026-07-10',
      amount: '300.00',
      method: 'BANK_TRANSFER',
      remark: '登记收款',
    })
    expect(router.currentRoute.value.name).toBe('finance-receipt-detail')
    expect(router.currentRoute.value.params.id).toBe('20')
  })

  it('已取消收款编辑页只读且不可保存', async () => {
    financeApiMock.receipts.get.mockResolvedValueOnce({ ...receipt, status: 'CANCELLED' })
    const { wrapper } = await mountFinanceView(ReceiptFormView, ['finance:receipt:update'], '/finance/receipts/20/edit')

    expect(wrapper.text()).toContain('非草稿收款只读')
    expect(wrapper.find('input[name="receipt-date"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('input[name="receipt-amount"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="receipt-method-select"]').classes()).toContain('is-disabled')
    expect(wrapper.find('input[name="receipt-remark"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="save-receipt"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-test="save-receipt"]').trigger('click')
    await flushPromises()
    expect(financeApiMock.receipts.update).not.toHaveBeenCalled()
  })
})
