import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PayableCandidateSource, PayableDetailRecord } from '../../shared/api/financeApi'
import PayableFormView from './PayableFormView.vue'
import { mountFinanceView, page, setSelectValue } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  sources: { payableCandidates: { list: vi.fn() } },
  payables: { create: vi.fn(), get: vi.fn(), update: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))

const candidate: PayableCandidateSource = {
  sourceType: 'PURCHASE_RECEIPT',
  sourceId: 200,
  sourceNo: 'PR-001',
  purchaseOrderId: 300,
  purchaseOrderNo: 'PO-001',
  supplierId: 100,
  supplierCode: 'SUP-A',
  supplierName: '华东供应商',
  businessDate: '2026-07-04',
  totalAmount: '900.50',
  lineCount: 2,
  settlementGenerated: false,
}

const detail: PayableDetailRecord = {
  id: 1,
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
  totalAmount: '900.50',
  paidAmount: '0.00',
  unpaidAmount: '900.50',
  status: 'DRAFT',
  createdByName: '财务员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
  sources: [],
  payments: [],
}

describe('应付生成和编辑表单', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    financeApiMock.sources.payableCandidates.list.mockResolvedValue(page([candidate]))
    financeApiMock.payables.create.mockResolvedValue(detail)
    financeApiMock.payables.get.mockResolvedValue(detail)
    financeApiMock.payables.update.mockResolvedValue(detail)
  })

  it('加载候选采购入库并提交生成应付', async () => {
    const { wrapper, router } = await mountFinanceView(PayableFormView, ['finance:payable:create'], '/finance/payables/create')

    expect(financeApiMock.sources.payableCandidates.list).toHaveBeenCalledWith({
      keyword: '',
      supplierId: undefined,
      dateFrom: '',
      dateTo: '',
      settlementGenerated: false,
      page: 1,
      pageSize: 20,
    })
    expect(wrapper.text()).toContain('PR-001')
    expect(wrapper.text()).toContain('PO-001')
    expect(wrapper.text()).toContain('华东供应商')

    await setSelectValue(wrapper, 0, 200)
    await wrapper.find('input[name="payable-due-date"]').setValue('2026-08-01')
    await wrapper.find('input[name="payable-remark"]').setValue('生成应付')
    await wrapper.find('[data-test="save-payable"]').trigger('click')
    await flushPromises()

    expect(financeApiMock.payables.create).toHaveBeenCalledWith({
      sourceType: 'PURCHASE_RECEIPT',
      sourceId: 200,
      dueDate: '2026-08-01',
      remark: '生成应付',
    })
    expect(router.currentRoute.value.name).toBe('finance-payable-detail')
  })

  it('非草稿应付编辑页只读且不可保存', async () => {
    financeApiMock.payables.get.mockResolvedValueOnce({ ...detail, status: 'CONFIRMED' })
    const { wrapper } = await mountFinanceView(PayableFormView, ['finance:payable:update'], '/finance/payables/1/edit')

    expect(wrapper.text()).toContain('非草稿应付不可编辑')
    expect(wrapper.find('input[name="payable-due-date"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="save-payable"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-test="save-payable"]').trigger('click')
    await flushPromises()
    expect(financeApiMock.payables.update).not.toHaveBeenCalled()
  })
})
