import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SupplierListView from './SupplierListView.vue'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { PartnerRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  suppliers: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    enable: vi.fn(),
    disable: vi.fn(),
    getSettlementTax: vi.fn(),
    updateSettlementTax: vi.fn(),
  },
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: apiMock,
}))

const supplier: PartnerRecord = {
  id: 1,
  code: 'SUP-001',
  name: '华东五金供应商',
  contactName: '李四',
  contactPhone: '13800000001',
  settlementTaxSummary: {
    hasData: true,
    sensitiveRestricted: true,
    taxNoMasked: '9131********5678',
    defaultTaxRate: '0.1300',
    settlementMethod: 'MONTHLY',
    paymentTermDays: 60,
  },
  status: 'ENABLED',
  remark: '标准件供应',
}
const emptyPage: PageResult<PartnerRecord> = { items: [], page: 1, pageSize: 10, total: 0, totalPages: 0 }

const restrictedSettlementTax = {
  ownerType: 'SUPPLIER',
  ownerId: 1,
  hasData: true,
  sensitiveRestricted: true,
  restrictedMessage: '无权限查看完整结算税务资料',
  invoiceTitle: '华东五金供应商',
  taxNo: null,
  taxNoMasked: '9131********5678',
  registeredAddress: null,
  registeredPhone: null,
  bankName: null,
  bankAccount: null,
  bankAccountMasked: '6228********7788',
  defaultTaxRate: '0.1300',
  invoiceType: 'SPECIAL_VAT',
  settlementMethod: 'MONTHLY',
  paymentTermDays: 60,
  paymentTerms: '月结60天',
  remark: null,
  version: 4,
}

function mountSuppliers(permissions = ['master:supplier:view', 'master:supplier:create', 'master:supplier:update']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(SupplierListView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
}

function expectQueryFormsUseStandardGrid(wrapper: ReturnType<typeof mountSuppliers>) {
  const queryForms = wrapper.findAllComponents({ name: 'ElForm' })
    .filter((form) => String(form.attributes('class') ?? '').split(/\s+/).includes('query-form'))
  expect(queryForms.length).toBeGreaterThan(0)
  queryForms.forEach((form) => {
    expect(form.props('inline')).not.toBe(true)
    expect(form.props('labelPosition')).toBe('top')
  })
}

function expectDefaultTableKeepsStatusScannable(wrapper: ReturnType<typeof mountSuppliers>) {
  const columns = wrapper.findAllComponents({ name: 'ElTableColumn' }).map((column) => column.props() as Record<string, unknown>)
  expect(columns.map((column) => column.label)).toEqual([
    '编码',
    '名称',
    '状态',
    '联系人',
    '联系电话',
    '结算税务摘要',
    '备注',
    '操作',
  ])
  expect(columns[2].label).toBe('状态')
  expect(columns.at(-1)?.label).toBe('操作')
  expect(columns.at(-1)?.fixed).toBe('right')
  expect(Number(columns.at(-1)?.width)).toBe(184)
  expect(columns.at(-1)?.minWidth).toBeFalsy()
}

function expectHistoryImportButton(wrapper: ReturnType<typeof mountSuppliers>) {
  const link = wrapper.find('[data-test="supplier-history-import-entry"]')
  expect(link.exists()).toBe(true)
  expect(link.element.tagName).toBe('A')
  expect(link.attributes('href')).toBe('/platform/history-imports?adapterCode=SUPPLIER_MASTER_V1')
  const button = link.findComponent({ name: 'ElButton' })
  expect(button.exists()).toBe(true)
  expect(button.props('tag')).toBe('span')
}

describe('供应商列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.suppliers.list.mockResolvedValue(emptyPage)
    apiMock.suppliers.create.mockResolvedValue(supplier)
    apiMock.suppliers.update.mockResolvedValue(supplier)
    apiMock.suppliers.enable.mockResolvedValue(supplier)
    apiMock.suppliers.disable.mockResolvedValue(supplier)
    apiMock.suppliers.getSettlementTax.mockResolvedValue(restrictedSettlementTax)
    apiMock.suppliers.updateSettlementTax.mockResolvedValue({ ...restrictedSettlementTax, paymentTermDays: 45, version: 5 })
  })

  it('新增供应商时提交联系人和联系电话', async () => {
    const wrapper = mountSuppliers()
    await flushPromises()

    await wrapper.find('[data-test="create-record"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="record-code"]').setValue('SUP-001')
    await wrapper.find('input[name="record-name"]').setValue('华东五金供应商')
    await wrapper.find('input[name="record-contact-name"]').setValue('李四')
    await wrapper.find('input[name="record-contact-phone"]').setValue('13800000001')
    await wrapper.find('[data-test="submit-record"]').trigger('click')
    await flushPromises()

    expect(apiMock.suppliers.create).toHaveBeenCalledWith(expect.objectContaining({
      code: 'SUP-001',
      name: '华东五金供应商',
      contactName: '李四',
      contactPhone: '13800000001',
    }))
  })

  it('只有查看权限时隐藏新增供应商按钮', async () => {
    const wrapper = mountSuppliers(['master:supplier:view'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-record"]').exists()).toBe(false)
  })

  it('列表展示供应商结算税务摘要和受限态，不泄露完整敏感值', async () => {
    apiMock.suppliers.list.mockResolvedValue({ items: [supplier], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountSuppliers([
      'master:supplier:view',
      'master:supplier-settlement:view',
      'master:supplier-settlement:update',
    ])
    await flushPromises()

    expect(wrapper.text()).toContain('9131********5678')
    expect(wrapper.text()).toContain('13%')
    expect(wrapper.text()).toContain('月结')
    expect(wrapper.text()).toContain('受限')
    expect(wrapper.text()).not.toContain('913100001234567890')
    expect(wrapper.text()).not.toContain('6228000011117788')
  })

  it('维护供应商结算税务资料走独立接口并提交 version', async () => {
    apiMock.suppliers.list.mockResolvedValue({ items: [supplier], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountSuppliers([
      'master:supplier:view',
      'master:supplier-settlement:view',
      'master:supplier-settlement:update',
      'master:supplier-settlement:sensitive-update',
    ])
    await flushPromises()

    await wrapper.find('[data-test="edit-supplier-settlement-tax"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="settlement-payment-term-days"]').setValue('45')
    await wrapper.find('[data-test="submit-settlement-tax"]').trigger('click')
    await flushPromises()

    expect(apiMock.suppliers.getSettlementTax).toHaveBeenCalledWith(1)
    expect(apiMock.suppliers.updateSettlementTax).toHaveBeenCalledWith(1, expect.objectContaining({
      paymentTermDays: 45,
      version: 4,
    }))
    expect(apiMock.suppliers.update).not.toHaveBeenCalled()
  })

  it('034 主列表同时提供固定历史导入和可执行批量状态入口', async () => {
    apiMock.suppliers.list.mockResolvedValue({ items: [supplier], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountSuppliers([
      'master:supplier:view',
      'platform:history-import:view',
      'platform:batch-tool:view',
    ])
    await flushPromises()

    expectQueryFormsUseStandardGrid(wrapper)
    expectDefaultTableKeepsStatusScannable(wrapper)
    expectHistoryImportButton(wrapper)
    expect(wrapper.find('[data-test="supplier-batch-status-entry"]').exists()).toBe(true)
  })
})
