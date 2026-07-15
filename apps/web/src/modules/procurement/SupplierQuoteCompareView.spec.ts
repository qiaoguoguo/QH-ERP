import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { SupplierQuoteRecord } from '../../shared/api/procurementApi'
import { useAuthStore } from '../../stores/authStore'
import SupplierQuoteCompareView from './SupplierQuoteCompareView.vue'

const procurementApiMock = vi.hoisted(() => ({
  quotes: {
    list: vi.fn(),
    select: vi.fn(),
  },
}))

vi.mock('../../shared/api/procurementApi', () => ({
  procurementApi: procurementApiMock,
}))

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  createIdempotencyKey: () => 'quote-select-key',
}))

const lowestQuote: SupplierQuoteRecord = {
  id: 301,
  inquiryId: 201,
  inquiryNo: 'INQ-001',
  quoteNo: 'QUO-001',
  procurementMode: 'PROJECT',
  projectId: 30,
  projectCode: 'PRJ-024',
  projectName: '华东产线改造',
  supplierId: 100,
  supplierName: '华东五金',
  materialId: 5,
  materialCode: 'M-100',
  materialName: '伺服电机',
  quantity: '100.000000',
  taxRate: '0.130000',
  taxIncludedUnitPrice: '113.000000',
  taxExcludedUnitPrice: '100.000000',
  taxIncludedAmount: '11300.000000',
  taxExcludedAmount: '10000.000000',
  currency: 'CNY',
  deliveryDate: '2026-07-25',
  validFrom: '2026-07-15',
  validTo: '2026-08-01',
  status: 'VALID',
  lowestEffectiveQuote: true,
  priceSourceTypeName: '最低有效报价',
  allowedActions: ['SELECT'],
  version: 5,
}

const nonLowestQuote: SupplierQuoteRecord = {
  ...lowestQuote,
  id: 302,
  quoteNo: 'QUO-002',
  supplierId: 101,
  supplierName: '华北机电',
  taxIncludedUnitPrice: '118.000000',
  taxExcludedUnitPrice: '104.424779',
  taxIncludedAmount: '11800.000000',
  taxExcludedAmount: '10442.477900',
  deliveryDate: '2026-07-20',
  lowestEffectiveQuote: false,
  priceSourceTypeName: '非最低选价',
  selectedReason: '交期更短，需要例外审批',
  version: 6,
}

function quotePage(items: SupplierQuoteRecord[]): PageResult<SupplierQuoteRecord> {
  return {
    items,
    total: items.length,
    page: 1,
    pageSize: 50,
  }
}

function setup(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'buyer', displayName: '采购员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return pinia
}

describe('供应商报价比较视图', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    procurementApiMock.quotes.list.mockResolvedValue(quotePage([lowestQuote, nonLowestQuote]))
    procurementApiMock.quotes.select.mockResolvedValue({ ...lowestQuote, status: 'SELECTED' })
  })

  it('按询价上下文加载报价并展示最低报价、非最低原因、有效期、交期和 CNY 税价', async () => {
    const pinia = setup(['procurement:quote:select'])
    const wrapper = mount(SupplierQuoteCompareView, {
      props: { inquiryId: 201 },
      global: { plugins: [pinia, ElementPlus] },
    })
    await flushPromises()

    expect(procurementApiMock.quotes.list).toHaveBeenCalledWith(201, {
      page: 1,
      pageSize: 50,
    })
    expect(wrapper.text()).toContain('最低有效报价')
    expect(wrapper.text()).toContain('华东五金')
    expect(wrapper.text()).toContain('华北机电')
    expect(wrapper.text()).toContain('非最低选价')
    expect(wrapper.text()).toContain('交期更短，需要例外审批')
    expect(wrapper.text()).toContain('有效期：2026-07-15 至 2026-08-01')
    expect(wrapper.text()).toContain('交期：2026-07-25')
    expect(wrapper.text()).toContain('未税单价 100')
    expect(wrapper.text()).toContain('含税单价 113')
    expect(wrapper.text()).toContain('税率 0.13')
    expect(wrapper.text()).toContain('CNY')
  })

  it('选择报价按 allowedActions 显示动作并提交 version、原因和幂等键', async () => {
    const pinia = setup(['procurement:quote:select'])
    const wrapper = mount(SupplierQuoteCompareView, {
      props: { inquiryId: 201 },
      global: { plugins: [pinia, ElementPlus] },
    })
    await flushPromises()

    await wrapper.find('[data-test="select-reason-302"]').setValue('交期更短，需要例外审批')
    await wrapper.find('[data-test="select-quote-302"]').trigger('click')
    await flushPromises()

    expect(procurementApiMock.quotes.select).toHaveBeenCalledWith(201, 302, {
      version: 6,
      reason: '交期更短，需要例外审批',
      idempotencyKey: 'quote-select-key',
    })
    expect(procurementApiMock.quotes.list).toHaveBeenCalledTimes(2)
  })

  it('无选择权限时隐藏报价选择动作', async () => {
    const pinia = setup(['procurement:inquiry:view'])
    const wrapper = mount(SupplierQuoteCompareView, {
      props: { inquiryId: 201 },
      global: { plugins: [pinia, ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.find('[data-test="select-quote-301"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="select-quote-302"]').exists()).toBe(false)
  })
})
