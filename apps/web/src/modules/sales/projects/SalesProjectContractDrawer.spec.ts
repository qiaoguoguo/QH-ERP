import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { SalesProjectContractDetail, SalesProjectDetail } from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import SalesProjectContractDrawer from './SalesProjectContractDrawer.vue'

const salesProjectApiMock = vi.hoisted(() => ({
  contracts: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    activate: vi.fn(),
    close: vi.fn(),
    terminate: vi.fn(),
    cancel: vi.fn(),
  },
}))

vi.mock('../../../shared/api/salesProjectApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/salesProjectApi')>()),
  salesProjectApi: salesProjectApiMock,
}))

const project = {
  id: 12,
  projectNo: 'SP-202607-001',
  name: '华东扩产项目',
  status: 'ACTIVE',
  version: 5,
  mainContractId: 55,
  mainContractNo: 'SC-001',
} as SalesProjectDetail

const contract: SalesProjectContractDetail = {
  id: 55,
  contractNo: 'SC-001',
  externalContractNo: 'EXT-001',
  projectId: 12,
  projectNo: 'SP-202607-001',
  projectName: '华东扩产项目',
  contractType: 'MAIN',
  mainContractId: null,
  mainContractNo: null,
  name: '主合同',
  signedDate: '2026-07-02',
  effectiveStartDate: '2026-07-03',
  effectiveEndDate: '2026-12-31',
  amount: '100000.00',
  status: 'DRAFT',
  updatedAt: '2026-07-12T09:00:00+08:00',
  version: 2,
  remark: '纸质合同',
  createdByName: '管理员',
  createdAt: '2026-07-12T08:00:00+08:00',
}

async function mountDrawer(
  props: Partial<InstanceType<typeof SalesProjectContractDrawer>['$props']> = {},
  permissions = [
    'sales:contract:view',
    'sales:contract:create',
    'sales:contract:update',
    'sales:contract:activate',
    'sales:contract:close',
    'sales:contract:terminate',
    'sales:contract:cancel',
  ],
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const wrapper = mount(SalesProjectContractDrawer, {
    props: {
      modelValue: true,
      mode: 'create',
      project,
      contractId: undefined,
      ...props,
    },
    global: {
      plugins: [pinia, ElementPlus],
    },
  })
  await flushPromises()
  return wrapper
}

describe('销售项目合同抽屉', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    salesProjectApiMock.contracts.get.mockResolvedValue(contract)
    salesProjectApiMock.contracts.create.mockResolvedValue(contract)
    salesProjectApiMock.contracts.update.mockResolvedValue(contract)
    salesProjectApiMock.contracts.activate.mockResolvedValue({ ...contract, status: 'EFFECTIVE', version: 3 })
    salesProjectApiMock.contracts.close.mockResolvedValue({ ...contract, status: 'CLOSED', version: 3 })
    salesProjectApiMock.contracts.terminate.mockResolvedValue({ ...contract, status: 'TERMINATED', version: 3 })
    salesProjectApiMock.contracts.cancel.mockResolvedValue({ ...contract, status: 'CANCELLED', version: 3 })
  })

  it('创建主合同时提交创建 payload，补充合同金额为 0 时阻止提交', async () => {
    const wrapper = await mountDrawer()

    await wrapper.find('input[name="contract-name"]').setValue('主合同')
    await wrapper.find('input[name="contract-signed-date"]').setValue('2026-07-02')
    await wrapper.find('input[name="contract-amount"]').setValue('100000.00')
    await wrapper.find('[data-test="save-sales-project-contract"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.contracts.create).toHaveBeenCalledWith(12, expect.objectContaining({
      contractType: 'MAIN',
      name: '主合同',
      signedDate: '2026-07-02',
      amount: '100000.00',
    }))

    const supplement = await mountDrawer({ mode: 'create', defaultContractType: 'SUPPLEMENT' })
    await supplement.find('input[name="contract-name"]').setValue('补充合同')
    await supplement.find('input[name="contract-signed-date"]').setValue('2026-07-03')
    await supplement.find('input[name="contract-amount"]').setValue('0')
    await supplement.find('[data-test="save-sales-project-contract"]').trigger('click')
    await flushPromises()
    expect(supplement.text()).toContain('补充合同金额不得为 0')
  })

  it('编辑合同加载详情并提交带 version 的更新 payload', async () => {
    const wrapper = await mountDrawer({ mode: 'edit', contractId: 55 })

    expect(salesProjectApiMock.contracts.get).toHaveBeenCalledWith(55)
    await wrapper.find('input[name="contract-name"]').setValue('主合同调整')
    await wrapper.find('[data-test="save-sales-project-contract"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.contracts.update).toHaveBeenCalledWith(55, expect.objectContaining({
      version: 2,
      name: '主合同调整',
    }))
  })

  it('合同终态动作必须填写原因并携带摘要 version', async () => {
    const wrapper = await mountDrawer({ mode: 'edit', contractId: 55 })

    await wrapper.find('[data-test="contract-action-cancel"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="confirm-contract-action"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请填写 1-200 字原因')
    expect(salesProjectApiMock.contracts.cancel).not.toHaveBeenCalled()

    await wrapper.find('textarea[name="contract-action-reason"]').setValue('合同草稿作废')
    await wrapper.find('[data-test="confirm-contract-action"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.contracts.cancel).toHaveBeenCalledWith(55, { version: 2, reason: '合同草稿作废' })
  })

  it('视图模式强制只读并隐藏保存和状态动作', async () => {
    const wrapper = await mountDrawer({ mode: 'view', contractId: 55 })

    expect(wrapper.find('[data-test="save-sales-project-contract"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="contract-action-activate"]').exists()).toBe(false)
    expect(wrapper.find('input[name="contract-name"]').attributes('disabled')).toBeDefined()
  })

  it('状态动作按权限和状态展示，草稿生效使用生效文案', async () => {
    salesProjectApiMock.contracts.get.mockResolvedValueOnce({ ...contract, status: 'EFFECTIVE' })
    const noActionPermission = await mountDrawer({ mode: 'edit', contractId: 55 }, ['sales:contract:view', 'sales:contract:update'])

    expect(noActionPermission.find('[data-test="contract-action-close"]').exists()).toBe(false)
    expect(noActionPermission.find('[data-test="contract-action-terminate"]').exists()).toBe(false)

    const draft = await mountDrawer({ mode: 'edit', contractId: 55 })
    expect(draft.find('[data-test="contract-action-activate"]').text()).toContain('生效')
  })

  it('草稿合同存在未保存变更时不能直接生效，提示先保存草稿', async () => {
    const wrapper = await mountDrawer({ mode: 'edit', contractId: 55 })

    await wrapper.find('input[name="contract-name"]').setValue('主合同调整')
    await wrapper.find('[data-test="contract-action-activate"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请先保存合同草稿后再生效')
    expect(salesProjectApiMock.contracts.activate).not.toHaveBeenCalled()
  })

  it('展示合同全生命周期人员、时间和原因，并使用响应式抽屉尺寸', async () => {
    salesProjectApiMock.contracts.get.mockResolvedValueOnce({
      ...contract,
      activatedByName: '销售主管',
      activatedAt: '2026-07-12T10:00:00+08:00',
      closedByName: '财务',
      closedAt: '2026-07-20T10:00:00+08:00',
      closedReason: '履约完成',
      terminatedByName: '法务',
      terminatedAt: '2026-07-21T10:00:00+08:00',
      terminatedReason: '客户终止',
      cancelledByName: '管理员',
      cancelledAt: '2026-07-22T10:00:00+08:00',
      cancelledReason: '录入错误',
    })
    const wrapper = await mountDrawer({ mode: 'view', contractId: 55 })

    expect(wrapper.findComponent({ name: 'ElDrawer' }).props('size')).toBe('min(720px, calc(100vw - 24px))')
    expect(wrapper.text()).toContain('创建管理员 2026-07-12 08:00')
    expect(wrapper.text()).toContain('生效销售主管 2026-07-12 10:00')
    expect(wrapper.text()).toContain('关闭财务 2026-07-20 10:00 履约完成')
    expect(wrapper.text()).toContain('终止法务 2026-07-21 10:00 客户终止')
    expect(wrapper.text()).toContain('取消管理员 2026-07-22 10:00 录入错误')
    expect(wrapper.find('.contract-drawer-body').exists()).toBe(true)
  })
})
