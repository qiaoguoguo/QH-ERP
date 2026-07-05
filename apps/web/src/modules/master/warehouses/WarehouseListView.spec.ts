import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WarehouseListView from './WarehouseListView.vue'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { WarehouseRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  warehouses: {
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

const warehouse: WarehouseRecord = {
  id: 1,
  code: 'WH-RAW',
  name: '原料仓',
  warehouseType: '原料',
  managerName: '张三',
  address: '一号厂房',
  status: 'ENABLED',
  remark: '生产原料存放',
}
const emptyPage: PageResult<WarehouseRecord> = { items: [], page: 1, pageSize: 10, total: 0, totalPages: 0 }

function mountWarehouses(permissions = ['master:warehouse:view', 'master:warehouse:create', 'master:warehouse:update']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(WarehouseListView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
}

describe('仓库列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.warehouses.list.mockResolvedValue(emptyPage)
    apiMock.warehouses.create.mockResolvedValue(warehouse)
    apiMock.warehouses.update.mockResolvedValue(warehouse)
    apiMock.warehouses.enable.mockResolvedValue(warehouse)
    apiMock.warehouses.disable.mockResolvedValue(warehouse)
  })

  it('新增仓库时提交仓库类型、负责人和地址', async () => {
    const wrapper = mountWarehouses()
    await flushPromises()

    await wrapper.find('[data-test="create-record"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="record-code"]').setValue('WH-RAW')
    await wrapper.find('input[name="record-name"]').setValue('原料仓')
    await wrapper.find('input[name="record-warehouse-type"]').setValue('原料')
    await wrapper.find('input[name="record-manager-name"]').setValue('张三')
    await wrapper.find('input[name="record-address"]').setValue('一号厂房')
    await wrapper.find('[data-test="submit-record"]').trigger('click')
    await flushPromises()

    expect(apiMock.warehouses.create).toHaveBeenCalledWith(expect.objectContaining({
      code: 'WH-RAW',
      name: '原料仓',
      warehouseType: '原料',
      managerName: '张三',
      address: '一号厂房',
    }))
  })

  it('只有查看权限时隐藏新增仓库按钮', async () => {
    const wrapper = mountWarehouses(['master:warehouse:view'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-record"]').exists()).toBe(false)
  })
})
