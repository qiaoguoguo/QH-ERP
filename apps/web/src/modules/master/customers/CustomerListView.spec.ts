import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CustomerListView from './CustomerListView.vue'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { PartnerRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  customers: {
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

const customer: PartnerRecord = {
  id: 1,
  code: 'CUS-001',
  name: '华南设备客户',
  contactName: '王五',
  contactPhone: '13800000002',
  settlementTaxSummary: {
    hasData: true,
    sensitiveRestricted: true,
    taxNoMasked: '9144********1234',
    defaultTaxRate: '0.1300',
    settlementMethod: 'MONTHLY',
    paymentTermDays: 30,
  },
  status: 'ENABLED',
  remark: '设备整机客户',
}
const emptyPage: PageResult<PartnerRecord> = { items: [], page: 1, pageSize: 10, total: 0, totalPages: 0 }

const restrictedSettlementTax = {
  ownerType: 'CUSTOMER',
  ownerId: 1,
  hasData: true,
  sensitiveRestricted: true,
  restrictedMessage: '无权限查看完整结算税务资料',
  invoiceTitle: '华南设备客户',
  taxNo: null,
  taxNoMasked: '9144********1234',
  registeredAddress: null,
  registeredPhone: null,
  bankName: null,
  bankAccount: null,
  bankAccountMasked: '6222********8899',
  defaultTaxRate: '0.1300',
  invoiceType: 'SPECIAL_VAT',
  settlementMethod: 'MONTHLY',
  paymentTermDays: 30,
  paymentTerms: '月结30天',
  remark: null,
  version: 5,
}

function mountCustomers(permissions = ['master:customer:view', 'master:customer:create', 'master:customer:update']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(CustomerListView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
}

function expectQueryFormsUseStandardGrid(wrapper: ReturnType<typeof mountCustomers>) {
  const queryForms = wrapper.findAllComponents({ name: 'ElForm' })
    .filter((form) => String(form.attributes('class') ?? '').split(/\s+/).includes('query-form'))
  expect(queryForms.length).toBeGreaterThan(0)
  queryForms.forEach((form) => {
    expect(form.props('inline')).not.toBe(true)
    expect(form.props('labelPosition')).toBe('top')
  })
}

function expectDefaultTableKeepsStatusScannable(wrapper: ReturnType<typeof mountCustomers>) {
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

function expectHistoryImportButton(wrapper: ReturnType<typeof mountCustomers>) {
  const link = wrapper.find('[data-test="customer-history-import-entry"]')
  expect(link.exists()).toBe(true)
  expect(link.element.tagName).toBe('A')
  expect(link.attributes('href')).toBe('/platform/history-imports?adapterCode=CUSTOMER_MASTER_V1')
  const button = link.findComponent({ name: 'ElButton' })
  expect(button.exists()).toBe(true)
  expect(button.props('tag')).toBe('span')
}

describe('客户列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.customers.list.mockResolvedValue(emptyPage)
    apiMock.customers.create.mockResolvedValue(customer)
    apiMock.customers.update.mockResolvedValue(customer)
    apiMock.customers.enable.mockResolvedValue(customer)
    apiMock.customers.disable.mockResolvedValue(customer)
    apiMock.customers.getSettlementTax.mockResolvedValue(restrictedSettlementTax)
    apiMock.customers.updateSettlementTax.mockResolvedValue({ ...restrictedSettlementTax, paymentTermDays: 45, version: 6 })
  })

  it('新增客户时提交联系人和联系电话', async () => {
    const wrapper = mountCustomers()
    await flushPromises()

    await wrapper.find('[data-test="create-record"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="record-code"]').setValue('CUS-001')
    await wrapper.find('input[name="record-name"]').setValue('华南设备客户')
    await wrapper.find('input[name="record-contact-name"]').setValue('王五')
    await wrapper.find('input[name="record-contact-phone"]').setValue('13800000002')
    await wrapper.find('[data-test="submit-record"]').trigger('click')
    await flushPromises()

    expect(apiMock.customers.create).toHaveBeenCalledWith(expect.objectContaining({
      code: 'CUS-001',
      name: '华南设备客户',
      contactName: '王五',
      contactPhone: '13800000002',
    }))
  })

  it('只有查看权限时隐藏新增客户按钮', async () => {
    const wrapper = mountCustomers(['master:customer:view'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-record"]').exists()).toBe(false)
  })

  it('列表展示结算税务摘要、脱敏值和受限标签，不出现完整敏感值', async () => {
    apiMock.customers.list.mockResolvedValue({ items: [customer], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountCustomers([
      'master:customer:view',
      'master:customer-settlement:view',
      'master:customer-settlement:update',
    ])
    await flushPromises()

    expect(wrapper.text()).toContain('9144********1234')
    expect(wrapper.text()).toContain('13%')
    expect(wrapper.text()).toContain('月结')
    expect(wrapper.text()).toContain('受限')
    expect(wrapper.text()).not.toContain('914403001234567890')
    expect(wrapper.text()).not.toContain('6222000011118899')
  })

  it('维护结算税务资料走独立接口并提交 version，不复用普通客户更新', async () => {
    apiMock.customers.list.mockResolvedValue({ items: [customer], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountCustomers([
      'master:customer:view',
      'master:customer-update',
      'master:customer-settlement:view',
      'master:customer-settlement:update',
      'master:customer-settlement:sensitive-update',
    ])
    await flushPromises()

    await wrapper.find('[data-test="edit-customer-settlement-tax"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="settlement-payment-term-days"]').setValue('45')
    await wrapper.find('[data-test="submit-settlement-tax"]').trigger('click')
    await flushPromises()

    expect(apiMock.customers.getSettlementTax).toHaveBeenCalledWith(1)
    expect(apiMock.customers.updateSettlementTax).toHaveBeenCalledWith(1, expect.objectContaining({
      paymentTermDays: 45,
      version: 5,
    }))
    expect(apiMock.customers.update).not.toHaveBeenCalled()
  })

  it('034 主列表同时提供固定历史导入和可执行批量状态入口', async () => {
    apiMock.customers.list.mockResolvedValue({ items: [customer], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountCustomers([
      'master:customer:view',
      'platform:history-import:view',
      'platform:batch-tool:view',
    ])
    await flushPromises()

    expectQueryFormsUseStandardGrid(wrapper)
    expectDefaultTableKeepsStatusScannable(wrapper)
    expectHistoryImportButton(wrapper)
    expect(wrapper.find('[data-test="customer-batch-status-entry"]').exists()).toBe(true)
  })
})
