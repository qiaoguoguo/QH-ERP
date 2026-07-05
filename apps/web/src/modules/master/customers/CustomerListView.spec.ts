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
  status: 'ENABLED',
  remark: '设备整机客户',
}
const emptyPage: PageResult<PartnerRecord> = { items: [], page: 1, pageSize: 10, total: 0, totalPages: 0 }

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

describe('客户列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.customers.list.mockResolvedValue(emptyPage)
    apiMock.customers.create.mockResolvedValue(customer)
    apiMock.customers.update.mockResolvedValue(customer)
    apiMock.customers.enable.mockResolvedValue(customer)
    apiMock.customers.disable.mockResolvedValue(customer)
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
})
