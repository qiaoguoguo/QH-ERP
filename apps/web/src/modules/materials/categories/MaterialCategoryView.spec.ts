import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MaterialCategoryView from './MaterialCategoryView.vue'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { CategoryRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  categories: {
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

const category: CategoryRecord = {
  id: 1,
  code: 'RAW',
  name: '原材料',
  parentId: null,
  sortOrder: 1,
  status: 'ENABLED',
}
const categoryPage: PageResult<CategoryRecord> = {
  items: [category],
  page: 1,
  pageSize: 100,
  total: 1,
  totalPages: 1,
}

function mountCategories(
  permissions = [
    'master:material-category:view',
    'master:material-category:create',
    'master:material-category:update',
  ],
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(MaterialCategoryView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
}

describe('物料分类页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.categories.list.mockResolvedValue(categoryPage)
    apiMock.categories.create.mockResolvedValue({ ...category, id: 2, code: 'SEMI', name: '半成品' })
    apiMock.categories.update.mockResolvedValue(category)
    apiMock.categories.enable.mockResolvedValue(category)
    apiMock.categories.disable.mockResolvedValue(category)
  })

  it('展示分类并按用户输入新增物料分类', async () => {
    const wrapper = mountCategories()
    await flushPromises()

    expect(apiMock.categories.list).toHaveBeenCalledWith({
      page: 1,
      pageSize: 100,
      keyword: '',
      status: undefined,
    })
    expect(wrapper.text()).toContain('原材料')

    await wrapper.find('[data-test="create-category"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="category-code"]').setValue('SEMI')
    await wrapper.find('input[name="category-name"]').setValue('半成品')
    await wrapper.find('input[name="category-sort-order"]').setValue('1')
    await wrapper.find('[data-test="submit-category"]').trigger('click')
    await flushPromises()

    expect(apiMock.categories.create).toHaveBeenCalledWith({
      code: 'SEMI',
      name: '半成品',
      parentId: null,
      sortOrder: 1,
      status: 'ENABLED',
    })
  })

  it('缺失排序时不提交并在弹窗内展示校验提示', async () => {
    const wrapper = mountCategories()
    await flushPromises()

    await wrapper.find('[data-test="create-category"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="category-code"]').setValue('SEMI')
    await wrapper.find('input[name="category-name"]').setValue('半成品')
    await wrapper.find('[data-test="submit-category"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('排序为必填')
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
    expect(apiMock.categories.create).not.toHaveBeenCalled()
  })

  it.each([
    ['1.2'],
    ['-1'],
    ['1e309'],
    ['abc'],
  ])('排序为非法数字 %s 时不提交', async (sortOrder) => {
    const wrapper = mountCategories()
    await flushPromises()

    await wrapper.find('[data-test="create-category"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="category-code"]').setValue('SEMI')
    await wrapper.find('input[name="category-name"]').setValue('半成品')
    await wrapper.find('input[name="category-sort-order"]').setValue(sortOrder)
    await wrapper.find('[data-test="submit-category"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('排序必须为非负整数')
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
    expect(apiMock.categories.create).not.toHaveBeenCalled()
  })

  it('没有创建权限时隐藏新增按钮', async () => {
    const wrapper = mountCategories(['master:material-category:view', 'master:material-category:update'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-category"]').exists()).toBe(false)
  })
})
