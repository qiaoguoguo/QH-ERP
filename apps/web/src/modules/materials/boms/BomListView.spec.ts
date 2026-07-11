import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResult } from '../../../shared/api/accountPermissionApi'
import type { BomDetailRecord, BomSummaryRecord } from '../../../shared/api/bomApi'
import type { MaterialRecord, UnitRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'
import BomListView from './BomListView.vue'

const bomApiMock = vi.hoisted(() => ({
  list: vi.fn(),
  get: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  copy: vi.fn(),
  enable: vi.fn(),
  disable: vi.fn(),
}))

const masterDataApiMock = vi.hoisted(() => ({
  materials: {
    list: vi.fn(),
  },
  units: {
    list: vi.fn(),
  },
}))

vi.mock('../../../shared/api/bomApi', () => ({
  bomApi: bomApiMock,
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: masterDataApiMock,
}))

const finishedGood: MaterialRecord = {
  id: 1,
  code: 'FG-A',
  name: '成品A',
  materialType: 'FINISHED_GOOD',
  sourceType: 'SELF_MADE',
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  categoryId: 1,
  unitId: 1,
  unitName: '件',
  status: 'ENABLED',
}
const rawMaterial: MaterialRecord = {
  id: 2,
  code: 'RM-STEEL',
  name: '冷轧钢板',
  materialType: 'RAW_MATERIAL',
  sourceType: 'PURCHASED',
  trackingMethod: 'NONE',
  trackingMethodName: '不追踪',
  categoryId: 2,
  unitId: 2,
  unitName: '千克',
  status: 'ENABLED',
}
const unitEach: UnitRecord = {
  id: 1,
  code: 'PCS',
  name: '件',
  precisionScale: 0,
  sortOrder: 1,
  status: 'ENABLED',
}
const unitKg: UnitRecord = {
  id: 2,
  code: 'KG',
  name: '千克',
  precisionScale: 2,
  sortOrder: 2,
  status: 'ENABLED',
}
const draftBom: BomSummaryRecord = {
  id: 1,
  bomCode: 'BOM-FG-A',
  parentMaterialId: 1,
  parentMaterialCode: 'FG-A',
  parentMaterialName: '成品A',
  versionCode: 'V1.0',
  name: '成品A标准 BOM',
  baseQuantity: 1,
  baseUnitId: 1,
  baseUnitName: '件',
  status: 'DRAFT',
  itemCount: 1,
  updatedAt: '2026-07-03T05:00:00+08:00',
}
const enabledBom: BomSummaryRecord = {
  ...draftBom,
  id: 2,
  bomCode: 'BOM-FG-A-V2',
  versionCode: 'V2.0',
  status: 'ENABLED',
}
const draftDetail: BomDetailRecord = {
  ...draftBom,
  items: [
    {
      id: 10,
      lineNo: 10,
      childMaterialId: 2,
      childMaterialCode: 'RM-STEEL',
      childMaterialName: '冷轧钢板',
      childMaterialType: 'RAW_MATERIAL',
      unitId: 2,
      unitName: '千克',
      quantity: 2.5,
      lossRate: 0.02,
    },
  ],
}
const bomPage: PageResult<BomSummaryRecord> = {
  items: [draftBom, enabledBom],
  page: 1,
  pageSize: 10,
  total: 2,
  totalPages: 1,
}
const emptyBomPage: PageResult<BomSummaryRecord> = {
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
}

function mountBoms(
  permissions = [
    'material:bom:view',
    'material:bom:create',
    'material:bom:update',
    'material:bom:copy',
    'material:bom:enable',
    'material:bom:disable',
  ],
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })

  return mount(BomListView, {
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

async function fillValidBomForm(wrapper: VueWrapper) {
  await wrapper.find('input[name="bom-code"]').setValue('BOM-FG-A')
  await wrapper.find('input[name="bom-version-code"]').setValue('V1.0')
  await wrapper.find('input[name="bom-name"]').setValue('成品A标准 BOM')
  await wrapper.find('input[name="bom-base-quantity"]').setValue('1')
  await setSelectValue(wrapper, 'bom-parent-material-id', 1)
  await setSelectValue(wrapper, 'bom-base-unit-id', 1)
  await setSelectValue(wrapper, 'bom-line-child-material-id-0', 2)
  await setSelectValue(wrapper, 'bom-line-unit-id-0', 2)
  await wrapper.find('input[name="bom-line-quantity-0"]').setValue('2.5')
  await wrapper.find('input[name="bom-line-loss-rate-0"]').setValue('0.02')
}

describe('BOM 管理页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    bomApiMock.list.mockResolvedValue(bomPage)
    bomApiMock.get.mockResolvedValue(draftDetail)
    bomApiMock.create.mockResolvedValue(draftDetail)
    bomApiMock.update.mockResolvedValue(draftDetail)
    bomApiMock.copy.mockResolvedValue({ ...draftDetail, id: 3, bomCode: 'BOM-FG-A-V11', versionCode: 'V1.1' })
    bomApiMock.enable.mockResolvedValue({ ...draftDetail, status: 'ENABLED' })
    bomApiMock.disable.mockResolvedValue({ ...draftDetail, status: 'DISABLED' })
    masterDataApiMock.materials.list.mockResolvedValue({
      items: [finishedGood, rawMaterial],
      page: 1,
      pageSize: 100,
      total: 2,
      totalPages: 1,
    })
    masterDataApiMock.units.list.mockResolvedValue({
      items: [unitEach, unitKg],
      page: 1,
      pageSize: 100,
      total: 2,
      totalPages: 1,
    })
  })

  it('加载列表后显示 BOM 编码、父项物料、版本和状态', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    expect(wrapper.text()).toContain('BOM-FG-A')
    expect(wrapper.text()).toContain('成品A')
    expect(wrapper.text()).toContain('V1.0')
    expect(wrapper.text()).toContain('草稿')
  })

  it('点击查询会按关键词、状态和父项物料调用列表接口', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('input[name="bom-keyword"]').setValue('成品A')
    await setSelectValue(wrapper, 'filter-bom-status', 'DRAFT')
    await setSelectValue(wrapper, 'filter-bom-parent-material-id', 1)
    await wrapper.find('[data-test="search-bom"]').trigger('click')
    await flushPromises()

    const calls = bomApiMock.list.mock.calls
    expect(calls[calls.length - 1][0]).toEqual({
      keyword: '成品A',
      status: 'DRAFT',
      parentMaterialId: 1,
      page: 1,
      pageSize: 10,
    })
  })

  it('无数据时显示空状态', async () => {
    bomApiMock.list.mockResolvedValue(emptyBomPage)
    const wrapper = mountBoms()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无 BOM 数据')
  })

  it('有创建权限时显示新建按钮，无创建权限时隐藏', async () => {
    const wrapper = mountBoms()
    await flushPromises()
    expect(wrapper.find('[data-test="create-bom"]').exists()).toBe(true)

    const readonlyWrapper = mountBoms(['material:bom:view'])
    await flushPromises()
    expect(readonlyWrapper.find('[data-test="create-bom"]').exists()).toBe(false)
  })

  it('草稿行显示编辑和启用按钮，启用行不显示编辑按钮', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    expect(wrapper.findAll('[data-test="edit-bom"]')).toHaveLength(1)
    expect(wrapper.find('[data-test="enable-bom"]').exists()).toBe(true)
  })

  it('表单必填为空时展示错误并不提交', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="submit-bom"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请完整填写 BOM 编码、版本、名称和父项物料')
    expect(bomApiMock.create).not.toHaveBeenCalled()
  })

  it('新增 BOM 弹窗为明细操作列提供响应式宽度和横向滚动容器', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()

    const dialog = wrapper.findComponent({ name: 'ElDialog' })
    expect(dialog.props('width')).toBe('min(1120px, calc(100vw - 48px))')
    expect(wrapper.find('[data-test="bom-line-scroll"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="remove-bom-line"]').exists()).toBe(true)
  })

  it('明细用量为 0 时展示错误并不提交', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()
    await fillValidBomForm(wrapper)
    await wrapper.find('input[name="bom-line-quantity-0"]').setValue('0')
    await wrapper.find('[data-test="submit-bom"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('第 10 行用量必须大于 0')
    expect(bomApiMock.create).not.toHaveBeenCalled()
  })

  it('重复子项时展示错误并不提交', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()
    await fillValidBomForm(wrapper)
    await wrapper.find('[data-test="add-bom-line"]').trigger('click')
    await flushPromises()
    await setSelectValue(wrapper, 'bom-line-child-material-id-1', 2)
    await setSelectValue(wrapper, 'bom-line-unit-id-1', 2)
    await wrapper.find('input[name="bom-line-quantity-1"]').setValue('1')
    await wrapper.find('[data-test="submit-bom"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('BOM 明细子项不能重复')
    expect(bomApiMock.create).not.toHaveBeenCalled()
  })

  it('保存失败时表单仍然可见并恢复按钮状态', async () => {
    bomApiMock.create.mockRejectedValue(new Error('BOM 编码已存在'))
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="create-bom"]').trigger('click')
    await flushPromises()
    await fillValidBomForm(wrapper)
    await wrapper.find('[data-test="submit-bom"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('BOM 编码已存在')
    expect(wrapper.find('[data-test="submit-bom"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
  })

  it('点击复制会提交新编码和版本', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="copy-bom"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="copy-bom-code"]').setValue('BOM-FG-A-V11')
    await wrapper.find('input[name="copy-bom-version-code"]').setValue('V1.1')
    await wrapper.find('input[name="copy-bom-name"]').setValue('成品A标准 BOM V1.1')
    await wrapper.find('[data-test="submit-copy-bom"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.copy).toHaveBeenCalledWith(1, {
      bomCode: 'BOM-FG-A-V11',
      versionCode: 'V1.1',
      name: '成品A标准 BOM V1.1',
    })
  })

  it('点击启用和停用会调用对应接口', async () => {
    const wrapper = mountBoms()
    await flushPromises()

    await wrapper.find('[data-test="enable-bom"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="disable-bom"]').trigger('click')
    await flushPromises()

    expect(bomApiMock.enable).toHaveBeenCalledWith(1)
    expect(bomApiMock.disable).toHaveBeenCalled()
  })
})
