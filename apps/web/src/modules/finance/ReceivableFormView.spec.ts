import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReceivableCandidateSource, ReceivableDetailRecord } from '../../shared/api/financeApi'
import ReceivableFormView from './ReceivableFormView.vue'
import { mountFinanceView, page, setSelectValue } from './financeTestHelpers'

const financeApiMock = vi.hoisted(() => ({
  sources: { receivableCandidates: { list: vi.fn() } },
  receivables: { create: vi.fn(), get: vi.fn(), update: vi.fn() },
}))
vi.mock('../../shared/api/financeApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/financeApi')>()),
  financeApi: financeApiMock,
}))

const candidate: ReceivableCandidateSource = {
  sourceType: 'SALES_SHIPMENT',
  sourceId: 200,
  sourceNo: 'SS-001',
  salesOrderId: 300,
  salesOrderNo: 'SO-001',
  customerId: 100,
  customerCode: 'CUS-A',
  customerName: '华东客户',
  businessDate: '2026-07-04',
  totalAmount: '900.50',
  lineCount: 2,
  settlementGenerated: false,
}

const detail: ReceivableDetailRecord = {
  id: 1,
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
  totalAmount: '900.50',
  receivedAmount: '0.00',
  unreceivedAmount: '900.50',
  status: 'DRAFT',
  createdByName: '财务员',
  createdAt: '2026-07-04T08:00:00+08:00',
  updatedAt: '2026-07-04T09:00:00+08:00',
  sources: [],
  receipts: [],
}

describe('应收生成和编辑表单', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    financeApiMock.sources.receivableCandidates.list.mockResolvedValue(page([candidate]))
    financeApiMock.receivables.create.mockResolvedValue(detail)
    financeApiMock.receivables.get.mockResolvedValue(detail)
    financeApiMock.receivables.update.mockResolvedValue(detail)
  })

  it('加载候选销售出库并提交生成应收', async () => {
    const { wrapper, router } = await mountFinanceView(ReceivableFormView, ['finance:receivable:create'], '/finance/receivables/create')

    expect(financeApiMock.sources.receivableCandidates.list).toHaveBeenCalledWith({
      keyword: '',
      customerId: undefined,
      dateFrom: '',
      dateTo: '',
      settlementGenerated: false,
      page: 1,
      pageSize: 20,
    })
    expect(wrapper.text()).toContain('SS-001')
    expect(wrapper.text()).toContain('SO-001')
    expect(wrapper.text()).toContain('华东客户')
    expect(wrapper.text()).toContain('900.50')

    await setSelectValue(wrapper, 0, 200)
    await wrapper.find('input[name="receivable-due-date"]').setValue('2026-08-01')
    await wrapper.find('input[name="receivable-remark"]').setValue('生成应收')
    await wrapper.find('[data-test="save-receivable"]').trigger('click')
    await flushPromises()

    expect(financeApiMock.receivables.create).toHaveBeenCalledWith({
      sourceType: 'SALES_SHIPMENT',
      sourceId: 200,
      dueDate: '2026-08-01',
      remark: '生成应收',
    })
    expect(router.currentRoute.value.name).toBe('finance-receivable-detail')
    expect(router.currentRoute.value.params.id).toBe('1')
  })

  it('编辑草稿时只提交到期日期和备注', async () => {
    const { wrapper } = await mountFinanceView(ReceivableFormView, ['finance:receivable:update'], '/finance/receivables/1/edit')

    await wrapper.find('input[name="receivable-due-date"]').setValue('2026-08-02')
    await wrapper.find('input[name="receivable-remark"]').setValue('更新备注')
    await wrapper.find('[data-test="save-receivable"]').trigger('click')
    await flushPromises()

    expect(financeApiMock.receivables.get).toHaveBeenCalledWith('1')
    expect(financeApiMock.receivables.update).toHaveBeenCalledWith('1', {
      dueDate: '2026-08-02',
      remark: '更新备注',
    })
  })

  it('非草稿应收编辑页只读且不可保存', async () => {
    financeApiMock.receivables.get.mockResolvedValueOnce({ ...detail, status: 'CONFIRMED' })
    const { wrapper } = await mountFinanceView(ReceivableFormView, ['finance:receivable:update'], '/finance/receivables/1/edit')

    expect(wrapper.text()).toContain('非草稿应收不可编辑')
    expect(wrapper.find('input[name="receivable-due-date"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('input[name="receivable-remark"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="save-receivable"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-test="save-receivable"]').trigger('click')
    await flushPromises()
    expect(financeApiMock.receivables.update).not.toHaveBeenCalled()
  })
})
