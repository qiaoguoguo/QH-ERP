import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CodingRuleListView from './CodingRuleListView.vue'
import type { CodingRuleRecord } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'

const apiMock = vi.hoisted(() => ({
  codingRules: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    enable: vi.fn(),
    disable: vi.fn(),
    generate: vi.fn(),
  },
}))

vi.mock('../../../shared/api/masterDataApi', () => ({
  masterDataApi: apiMock,
}))

const rule: CodingRuleRecord = {
  id: 1,
  ruleCode: 'MAT-RULE',
  name: '物料编码规则',
  objectType: 'MATERIAL',
  prefix: 'MAT',
  datePattern: 'YYYYMM',
  serialLength: 4,
  resetCycle: 'MONTH',
  nextSerialNo: 12,
  status: 'ENABLED',
  lastGeneratedCode: 'MAT-202607-0011',
  lastGeneratedAt: '2026-07-13T10:00:00+08:00',
  remark: '物料自动编码',
  updatedAt: '2026-07-13T10:00:00+08:00',
  version: 2,
}

function mountRules(permissions = [
  'master:coding-rule:view',
  'master:coding-rule:create',
  'master:coding-rule:update',
  'master:coding-rule:enable',
  'master:coding-rule:disable',
]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })

  return mount(CodingRuleListView, {
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

describe('编码规则页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.codingRules.list.mockResolvedValue({ items: [rule], page: 1, pageSize: 10, total: 1, totalPages: 1 })
    apiMock.codingRules.get.mockResolvedValue(rule)
    apiMock.codingRules.create.mockResolvedValue(rule)
    apiMock.codingRules.update.mockResolvedValue(rule)
    apiMock.codingRules.enable.mockResolvedValue({ ...rule, status: 'ENABLED', version: 3 })
    apiMock.codingRules.disable.mockResolvedValue({ ...rule, status: 'DISABLED', version: 3 })
    apiMock.codingRules.generate.mockResolvedValue({
      objectType: 'MATERIAL',
      ruleId: 1,
      generatedCode: 'MAT-202607-0012',
      generatedAt: '2026-07-13T11:00:00+08:00',
    })
  })

  it('展示规则组成、流水控制和最后生成编码摘要', async () => {
    const wrapper = mountRules()
    await flushPromises()

    expect(wrapper.text()).toContain('编码规则')
    expect(wrapper.text()).toContain('MAT-RULE')
    expect(wrapper.text()).toContain('物料')
    expect(wrapper.text()).toContain('YYYYMM')
    expect(wrapper.text()).toContain('MAT-202607-0011')
  })

  it('新增规则提交固定对象类型、日期段、流水长度、重置周期和 version 无关字段', async () => {
    const wrapper = mountRules()
    await flushPromises()

    await wrapper.find('[data-test="create-coding-rule"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="coding-rule-code"]').setValue('CUS-RULE')
    await wrapper.find('input[name="coding-rule-name"]').setValue('客户编码规则')
    await setSelectValue(wrapper, 'coding-rule-object-type', 'CUSTOMER')
    await wrapper.find('input[name="coding-rule-prefix"]').setValue('CUS')
    await setSelectValue(wrapper, 'coding-rule-date-pattern', 'YYYYMM')
    await wrapper.find('input[name="coding-rule-serial-length"]').setValue('5')
    await setSelectValue(wrapper, 'coding-rule-reset-cycle', 'MONTH')
    await wrapper.find('input[name="coding-rule-next-serial-no"]').setValue('1')
    await wrapper.find('[data-test="submit-coding-rule"]').trigger('click')
    await flushPromises()

    expect(apiMock.codingRules.create).toHaveBeenCalledWith({
      ruleCode: 'CUS-RULE',
      name: '客户编码规则',
      objectType: 'CUSTOMER',
      prefix: 'CUS',
      datePattern: 'YYYYMM',
      serialLength: 5,
      resetCycle: 'MONTH',
      nextSerialNo: 1,
      status: 'ENABLED',
      remark: null,
    })
  })

  it('只提供 021 固定编码对象，不出现未来单据规则入口', async () => {
    const wrapper = mountRules()
    await flushPromises()

    await wrapper.find('[data-test="create-coding-rule"]').trigger('click')
    await flushPromises()

    const optionText = wrapper.text()
    expect(optionText).toContain('物料')
    expect(optionText).toContain('客户')
    expect(optionText).toContain('供应商')
    expect(optionText).toContain('BOM')
    expect(optionText).toContain('BOM 工程变更')
    expect(optionText).not.toContain('发票')
    expect(optionText).not.toContain('总账')
  })

  it('数字字段非法时保留弹窗并不提交', async () => {
    const wrapper = mountRules()
    await flushPromises()

    await wrapper.find('[data-test="create-coding-rule"]').trigger('click')
    await flushPromises()
    await wrapper.find('input[name="coding-rule-code"]').setValue('CUS-RULE')
    await wrapper.find('input[name="coding-rule-name"]').setValue('客户编码规则')
    await setSelectValue(wrapper, 'coding-rule-object-type', 'CUSTOMER')
    await wrapper.find('input[name="coding-rule-serial-length"]').setValue('0')
    await wrapper.find('input[name="coding-rule-next-serial-no"]').setValue('-1')
    await wrapper.find('[data-test="submit-coding-rule"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('流水长度必须为正整数')
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
    expect(apiMock.codingRules.create).not.toHaveBeenCalled()
  })

  it('只有查看权限时隐藏新增、编辑和停用动作', async () => {
    const wrapper = mountRules(['master:coding-rule:view'])
    await flushPromises()

    expect(wrapper.find('[data-test="create-coding-rule"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-coding-rule"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="disable-coding-rule"]').exists()).toBe(false)
  })
})
