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
  status: 'ENABLED',
  remark: '标准件供应',
}
const emptyPage: PageResult<PartnerRecord> = { items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 }

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

describe('供应商列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.suppliers.list.mockResolvedValue(emptyPage)
    apiMock.suppliers.create.mockResolvedValue(supplier)
    apiMock.suppliers.update.mockResolvedValue(supplier)
    apiMock.suppliers.enable.mockResolvedValue(supplier)
    apiMock.suppliers.disable.mockResolvedValue(supplier)
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
})
