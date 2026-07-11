import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MaterialItemListView from './MaterialItemListView.vue'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { CategoryRecord, MaterialRecord, UnitRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  materials: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    enable: vi.fn(),
    disable: vi.fn(),
  },
  units: {
    list: vi.fn(),
  },
  categories: {
    list: vi.fn(),
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
const unit: UnitRecord = {
  id: 1,
  code: 'KG',
  name: '千克',
  precisionScale: 2,
  sortOrder: 1,
  status: 'ENABLED',
}
const material: MaterialRecord = {
  id: 1,
  code: 'MAT-RAW-001',
  name: '冷轧钢板',
  specification: '1.5mm',
  materialType: 'RAW_MATERIAL',
  sourceType: 'PURCHASED',
  trackingMethod: 'BATCH',
  trackingMethodName: '批次管理',
  categoryId: 1,
  categoryName: '原材料',
  unitId: 1,
  unitName: '千克',
  status: 'ENABLED',
}
const emptyMaterialPage: PageResult<MaterialRecord> = {
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
}
const categoryPage: PageResult<CategoryRecord> = {
  items: [category],
  page: 1,
  pageSize: 100,
  total: 1,
  totalPages: 1,
}
const unitPage: PageResult<UnitRecord> = {
  items: [unit],
  page: 1,
  pageSize: 100,
  total: 1,
  totalPages: 1,
}

function mountMaterials(permissions = ['master:material:view', 'master:material:create', 'master:material:update']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(MaterialItemListView, {
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
}

async function setSelectValue(wrapper: VueWrapper, dataTest: string, value: unknown) {
  const select = wrapper.findComponent(`[data-test="${dataTest}"]`) as VueWrapper
  expect(select.exists()).toBe(true)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

async function fillValidMaterialForm(wrapper: VueWrapper) {
  await wrapper.find('input[name="material-code"]').setValue('MAT-RAW-001')
  await wrapper.find('input[name="material-name"]').setValue('冷轧钢板')
  await wrapper.find('input[name="material-specification"]').setValue('1.5mm')
  await setSelectValue(wrapper, 'material-type', 'RAW_MATERIAL')
  await setSelectValue(wrapper, 'material-source-type', 'PURCHASED')
  await setSelectValue(wrapper, 'material-tracking-method', 'BATCH')
  await setSelectValue(wrapper, 'material-category-id', '1')
  await setSelectValue(wrapper, 'material-unit-id', '1')
  await setSelectValue(wrapper, 'material-status', 'ENABLED')
}

describe('物料档案页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.materials.list.mockResolvedValue(emptyMaterialPage)
    apiMock.materials.create.mockResolvedValue(material)
    apiMock.materials.update.mockResolvedValue(material)
    apiMock.materials.enable.mockResolvedValue(material)
    apiMock.materials.disable.mockResolvedValue(material)
    apiMock.units.list.mockResolvedValue(unitPage)
    apiMock.categories.list.mockResolvedValue(categoryPage)
  })

  it('新增物料时提交分类和单位等核心字段', async () => {
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="create-material"]').trigger('click')
    await flushPromises()
    await fillValidMaterialForm(wrapper)
    await wrapper.find('[data-test="submit-material"]').trigger('click')
    await flushPromises()

    expect(apiMock.materials.create).toHaveBeenCalledWith(expect.objectContaining({
      code: 'MAT-RAW-001',
      name: '冷轧钢板',
      materialType: 'RAW_MATERIAL',
      sourceType: 'PURCHASED',
      trackingMethod: 'BATCH',
      categoryId: 1,
      unitId: 1,
      status: 'ENABLED',
    }))
  })

  it('没有创建权限时隐藏新增按钮', async () => {
    const wrapper = mountMaterials(['master:material:view', 'master:material:update'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-material"]').exists()).toBe(false)
  })

  it('缺少分类或单位时不提交并展示校验提示', async () => {
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="create-material"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="material-code"]').setValue('MAT-RAW-001')
    await wrapper.find('input[name="material-name"]').setValue('冷轧钢板')
    await setSelectValue(wrapper, 'material-type', 'RAW_MATERIAL')
    await setSelectValue(wrapper, 'material-source-type', 'PURCHASED')
    await wrapper.find('[data-test="submit-material"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('分类和单位为必填')
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
    expect(apiMock.materials.create).not.toHaveBeenCalled()
  })

  it('详情抽屉展示物料分类、单位、类型和来源文案', async () => {
    apiMock.materials.list.mockResolvedValue({ items: [material], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="view-material"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('冷轧钢板')
    expect(wrapper.text()).toContain('1.5mm')
    expect(wrapper.text()).toContain('原材料')
    expect(wrapper.text()).toContain('千克')
    expect(wrapper.text()).toContain('外购')
    expect(wrapper.text()).toContain('批次管理')
  })

  it('物料编辑弹窗和详情抽屉使用响应式宽度', async () => {
    apiMock.materials.list.mockResolvedValue({ items: [material], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="create-material"]').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('width')).toBe('min(640px, 96vw)')

    await wrapper.find('[data-test="view-material"]').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElDrawer' }).props('size')).toBe('min(420px, 92vw)')
  })

  it('保存失败时保留弹窗并恢复按钮状态', async () => {
    apiMock.materials.create.mockRejectedValue(new Error('物料编码重复'))
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('[data-test="create-material"]').trigger('click')
    await flushPromises()
    await fillValidMaterialForm(wrapper)
    await wrapper.find('[data-test="submit-material"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('物料编码重复')
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
    expect(wrapper.find('[data-test="submit-material"]').attributes('disabled')).toBeUndefined()
  })

  it('按分类、物料类型、来源和追踪方式筛选时不发送 unitId 查询字段', async () => {
    const wrapper = mountMaterials()
    await flushPromises()

    await wrapper.find('input[name="material-keyword"]').setValue('钢板')
    await setSelectValue(wrapper, 'filter-material-category-id', '1')
    await setSelectValue(wrapper, 'filter-material-type', 'RAW_MATERIAL')
    await setSelectValue(wrapper, 'filter-source-type', 'PURCHASED')
    await setSelectValue(wrapper, 'filter-tracking-method', 'BATCH')
    await wrapper.find('[data-test="search-material"]').trigger('click')
    await flushPromises()

    const calls = apiMock.materials.list.mock.calls
    const lastQuery = calls[calls.length - 1][0]
    expect(lastQuery).toEqual(expect.objectContaining({
      keyword: '钢板',
      status: undefined,
      page: 1,
      pageSize: 10,
      categoryId: 1,
      materialType: 'RAW_MATERIAL',
      sourceType: 'PURCHASED',
      trackingMethod: 'BATCH',
    }))
    expect(lastQuery).not.toHaveProperty('unitId')
  })
})
