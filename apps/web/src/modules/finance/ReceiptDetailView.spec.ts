import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReceiptDetailRecord } from '../../shared/api/financeApi'
import ReceiptDetailView from './ReceiptDetailView.vue'
import { buttonsByText, mountFinanceView } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  receipts: { get: vi.fn(), post: vi.fn(), cancel: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))

const draftReceipt: ReceiptDetailRecord = {
  id: 20,
  receiptNo: 'RC-DRAFT-001',
  receivableId: 10,
  receivableNo: 'AR-001',
  customerId: 100,
  customerName: '华东客户',
  receiptDate: '2026-07-10',
  amount: '100.00',
  method: 'BANK_TRANSFER',
  status: 'DRAFT',
  createdByName: '财务员',
  allocations: [{
    id: 1,
    receiptId: 20,
    receiptNo: 'RC-DRAFT-001',
    receivableId: 10,
    receivableNo: 'AR-001',
    customerId: 100,
    customerName: '华东客户',
    allocatedAmount: '100.00',
  }],
}

describe('收款详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    financeApiMock.receipts.get.mockResolvedValue(draftReceipt)
    financeApiMock.receipts.post.mockResolvedValue({ ...draftReceipt, status: 'POSTED' })
    financeApiMock.receipts.cancel.mockResolvedValue({ ...draftReceipt, status: 'CANCELLED' })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('展示核销应收并允许草稿过账和取消', async () => {
    const { wrapper, router } = await mountFinanceView(ReceiptDetailView, [
      'finance:receipt:view',
      'finance:receipt:update',
      'finance:receipt:post',
      'finance:receipt:cancel',
      'finance:receivable:view',
    ], '/finance/receipts/20')

    expect(wrapper.text()).toContain('RC-DRAFT-001')
    expect(wrapper.text()).toContain('AR-001')
    expect(wrapper.text()).toContain('多目标核销明细')
    expect(wrapper.text()).toContain('预收余额')
    expect(wrapper.text()).toContain('发票链接')
    expect(wrapper.text()).toContain('凭证草稿摘要')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('100.00')
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(1)
    expect(buttonsByText(wrapper, '过账')).toHaveLength(1)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(1)

    await wrapper.find('[data-test="view-receivable-from-receipt"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('finance-receivable-detail')

    await wrapper.find('[data-test="post-receipt-detail"]').trigger('click')
    await flushPromises()
    expect(financeApiMock.receipts.post).toHaveBeenCalledWith(20)
  })

  it('已过账收款展示只读提示且不显示编辑取消过账按钮', async () => {
    financeApiMock.receipts.get.mockResolvedValueOnce({ ...draftReceipt, status: 'POSTED' })
    const { wrapper } = await mountFinanceView(ReceiptDetailView, [
      'finance:receipt:view',
      'finance:receipt:update',
      'finance:receipt:post',
      'finance:receipt:cancel',
    ], '/finance/receipts/20')

    expect(wrapper.text()).toContain('已过账收款只读')
    expect(buttonsByText(wrapper, '编辑')).toHaveLength(0)
    expect(buttonsByText(wrapper, '过账')).toHaveLength(0)
    expect(buttonsByText(wrapper, '取消')).toHaveLength(0)
  })
})
