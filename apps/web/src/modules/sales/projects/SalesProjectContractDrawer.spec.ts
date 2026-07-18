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

const documentPlatformApiMock = vi.hoisted(() => ({
  approvals: {
    submitSalesProjectContractActivation: vi.fn(),
  },
  attachments: {
    list: vi.fn(),
    upload: vi.fn(),
    download: vi.fn(),
    delete: vi.fn(),
  },
  printTemplates: {
    list: vi.fn(),
  },
  printPreviews: {
    get: vi.fn(),
  },
  printTasks: {
    create: vi.fn(),
  },
}))

vi.mock('../../../shared/api/salesProjectApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/salesProjectApi')>()),
  salesProjectApi: salesProjectApiMock,
}))

vi.mock('../../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
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

const supplementContract: SalesProjectContractDetail = {
  ...contract,
  id: 56,
  contractNo: 'SC-002',
  contractType: 'SUPPLEMENT',
  mainContractId: 55,
  mainContractNo: 'SC-001',
  name: '补充合同',
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

function buttonByTest(wrapper: Awaited<ReturnType<typeof mountDrawer>>, testId: string) {
  const button = wrapper.findAllComponents({ name: 'ElButton' })
    .find((item) => item.attributes('data-test') === testId)
  expect(button?.exists()).toBe(true)
  return button
}

function formItemByLabel(wrapper: Awaited<ReturnType<typeof mountDrawer>>, label: string) {
  const item = wrapper.findAllComponents({ name: 'ElFormItem' })
    .find((candidate) => candidate.props('label') === label)
  if (!item) {
    throw new Error(`未找到表单项：${label}`)
  }
  return item
}

async function fillRequiredContractFields(wrapper: Awaited<ReturnType<typeof mountDrawer>>, name = '项目合同') {
  await wrapper.find('input[name="contract-name"]').setValue(name)
  await wrapper.find('input[name="contract-signed-date"]').setValue('2026-07-02')
  await wrapper.find('input[name="contract-amount"]').setValue('100000.00')
}

function contractTypeSelect(wrapper: Awaited<ReturnType<typeof mountDrawer>>) {
  const select = wrapper.findComponent({ name: 'ElSelect' })
  expect(select.exists()).toBe(true)
  return select
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
    documentPlatformApiMock.approvals.submitSalesProjectContractActivation.mockResolvedValue({
      id: 900,
      status: 'SUBMITTED',
      version: 1,
    })
    documentPlatformApiMock.attachments.list.mockResolvedValue([])
    documentPlatformApiMock.printTemplates.list.mockResolvedValue([
      {
        templateCode: 'CONTRACT_ACTIVATION_APPROVAL_V1',
        name: '合同生效审批单',
        sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
        templateVersion: 1,
      },
    ])
  })

  it('创建主合同时提交创建 payload，补充合同金额为 0 时阻止提交', async () => {
    const wrapper = await mountDrawer()

    await fillRequiredContractFields(wrapper, '主合同')
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
    expect(formItemByLabel(supplement, '合同金额').props().error).toBe('补充合同金额不得为 0')
    expect(supplement.find('.state-alert').exists()).toBe(false)
  })

  it('草稿项目主合同入口锁定为主合同，不能切到补充合同提交', async () => {
    const draftProject = {
      ...project,
      status: 'DRAFT',
      mainContractId: null,
      mainContractNo: null,
      mainContractStatus: null,
    } as SalesProjectDetail
    const wrapper = await mountDrawer({ project: draftProject, defaultContractType: 'MAIN' })

    await contractTypeSelect(wrapper).vm.$emit('update:modelValue', 'SUPPLEMENT')
    await fillRequiredContractFields(wrapper, '主合同')
    await wrapper.find('[data-test="save-sales-project-contract"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.contracts.create).toHaveBeenCalledWith(12, expect.objectContaining({
      contractType: 'MAIN',
      mainContractId: null,
    }))
    expect(contractTypeSelect(wrapper).props().disabled).toBe(true)
  })

  it('执行中补充合同入口锁定为补充合同，不能切到主合同提交', async () => {
    const wrapper = await mountDrawer({ mode: 'create', defaultContractType: 'SUPPLEMENT' })

    await contractTypeSelect(wrapper).vm.$emit('update:modelValue', 'MAIN')
    await fillRequiredContractFields(wrapper, '补充合同')
    await wrapper.find('[data-test="save-sales-project-contract"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.contracts.create).toHaveBeenCalledWith(12, expect.objectContaining({
      contractType: 'SUPPLEMENT',
      mainContractId: 55,
    }))
    expect(contractTypeSelect(wrapper).props().disabled).toBe(true)
  })

  it('合同名称、签订日期和合同金额显示必填标记与字段级错误', async () => {
    const wrapper = await mountDrawer()

    expect(formItemByLabel(wrapper, '合同名称').props().required).toBe(true)
    expect(formItemByLabel(wrapper, '签订日期').props().required).toBe(true)
    expect(formItemByLabel(wrapper, '合同金额').props().required).toBe(true)

    await wrapper.find('[data-test="save-sales-project-contract"]').trigger('click')
    await flushPromises()

    expect(formItemByLabel(wrapper, '合同名称').props().error).toBe('请填写合同名称')
    expect(formItemByLabel(wrapper, '签订日期').props().error).toBe('请选择签订日期')
    expect(formItemByLabel(wrapper, '合同金额').props().error).toBe('请填写合同金额')
    expect(wrapper.find('.state-alert').exists()).toBe(false)
    expect(salesProjectApiMock.contracts.create).not.toHaveBeenCalled()
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
    expect(buttonByTest(wrapper, 'confirm-contract-action')?.props().type).toBe('danger')
    await wrapper.find('[data-test="confirm-contract-action"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请填写 1-200 字原因')
    expect(salesProjectApiMock.contracts.cancel).not.toHaveBeenCalled()

    await wrapper.find('textarea[name="contract-action-reason"]').setValue('合同草稿作废')
    await wrapper.find('[data-test="confirm-contract-action"]').trigger('click')
    await flushPromises()

    expect(salesProjectApiMock.contracts.cancel).toHaveBeenCalledWith(55, { version: 2, reason: '合同草稿作废' })
  })

  it('合同关闭确认使用警告语义，终止确认使用危险语义', async () => {
    salesProjectApiMock.contracts.get.mockResolvedValueOnce({ ...contract, status: 'EFFECTIVE' })
    const closeWrapper = await mountDrawer({ mode: 'edit', contractId: 55 })
    await closeWrapper.find('[data-test="contract-action-close"]').trigger('click')
    await flushPromises()
    expect(buttonByTest(closeWrapper, 'confirm-contract-action')?.props().type).toBe('warning')

    salesProjectApiMock.contracts.get.mockResolvedValueOnce({ ...contract, status: 'EFFECTIVE' })
    const terminateWrapper = await mountDrawer({ mode: 'edit', contractId: 55 })
    await terminateWrapper.find('[data-test="contract-action-terminate"]').trigger('click')
    await flushPromises()
    expect(buttonByTest(terminateWrapper, 'confirm-contract-action')?.props().type).toBe('danger')
  })

  it('合同业务关闭与抽屉退出使用无歧义文案', async () => {
    salesProjectApiMock.contracts.get.mockResolvedValueOnce({ ...contract, status: 'EFFECTIVE' })
    const wrapper = await mountDrawer({ mode: 'edit', contractId: 55 })

    expect(wrapper.find('[data-test="contract-action-close"]').text()).toContain('关闭合同')
    expect(wrapper.find('[data-test="collapse-sales-project-contract-drawer"]').text()).toContain('收起')
    expect(wrapper.find('[data-test="contract-action-close"]').text())
      .not.toBe(wrapper.find('[data-test="collapse-sales-project-contract-drawer"]').text())
  })

  it('视图模式强制只读并隐藏保存和状态动作', async () => {
    const wrapper = await mountDrawer({ mode: 'view', contractId: 55 })

    expect(wrapper.find('[data-test="save-sales-project-contract"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="contract-action-activate"]').exists()).toBe(false)
    expect(wrapper.find('input[name="contract-name"]').attributes('disabled')).toBeDefined()
  })

  it('状态动作按权限和状态展示，草稿生效改为提交生效审批', async () => {
    salesProjectApiMock.contracts.get.mockResolvedValueOnce({ ...contract, status: 'EFFECTIVE' })
    const noActionPermission = await mountDrawer({ mode: 'edit', contractId: 55 }, ['sales:contract:view', 'sales:contract:update'])

    expect(noActionPermission.find('[data-test="contract-action-close"]').exists()).toBe(false)
    expect(noActionPermission.find('[data-test="contract-action-terminate"]').exists()).toBe(false)

    const draft = await mountDrawer({ mode: 'edit', contractId: 55 })
    expect(draft.find('[data-test="contract-action-activate"]').text()).toContain('提交生效审批')
  })

  it('补充合同生效按钮按项目和主合同状态矩阵禁用并给出原因', async () => {
    salesProjectApiMock.contracts.get.mockResolvedValueOnce(supplementContract)
    const inactiveProject = {
      ...project,
      status: 'DRAFT',
      mainContractStatus: 'EFFECTIVE',
    } as SalesProjectDetail
    const inactive = await mountDrawer({ mode: 'edit', contractId: 56, project: inactiveProject })
    const inactiveButton = inactive.find('[data-test="contract-action-activate"]')

    expect(inactiveButton.attributes('disabled')).toBeDefined()
    expect(inactiveButton.attributes('title')).toBe('补充合同需项目执行中且主合同已生效后才能生效')

    salesProjectApiMock.contracts.get.mockResolvedValueOnce(supplementContract)
    const noEffectiveMainProject = {
      ...project,
      mainContractStatus: 'DRAFT',
    } as SalesProjectDetail
    const noEffectiveMain = await mountDrawer({ mode: 'edit', contractId: 56, project: noEffectiveMainProject })
    const noEffectiveMainButton = noEffectiveMain.find('[data-test="contract-action-activate"]')

    expect(noEffectiveMainButton.attributes('disabled')).toBeDefined()
    expect(noEffectiveMainButton.attributes('title')).toBe('补充合同需项目执行中且主合同已生效后才能生效')
  })

  it('草稿合同存在未保存变更时不能直接生效，提示先保存草稿', async () => {
    const wrapper = await mountDrawer({ mode: 'edit', contractId: 55 })

    await wrapper.find('input[name="contract-name"]').setValue('主合同调整')
    await wrapper.find('[data-test="contract-action-activate"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请先保存合同草稿后再生效')
    expect(salesProjectApiMock.contracts.activate).not.toHaveBeenCalled()
  })

  it('草稿合同提交生效审批，不再调用合同直接生效接口，并展示审批附件打印入口', async () => {
    const wrapper = await mountDrawer({ mode: 'edit', contractId: 55 })

    expect(wrapper.text()).toContain('审批状态')
    expect(wrapper.text()).toContain('合同附件')
    expect(wrapper.text()).toContain('合同生效审批单')

    await wrapper.find('[data-test="contract-action-activate"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="confirm-contract-action"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('请填写 1-200 字原因')
    expect(documentPlatformApiMock.approvals.submitSalesProjectContractActivation).not.toHaveBeenCalled()

    await wrapper.find('[data-test="approval-submit-reason"]').setValue('合同草稿确认无误')
    await wrapper.find('[data-test="confirm-contract-action"]').trigger('click')
    await flushPromises()

    expect(documentPlatformApiMock.approvals.submitSalesProjectContractActivation).toHaveBeenCalledWith(55, {
      version: 2,
      reason: '合同草稿确认无误',
      idempotencyKey: expect.any(String),
    })
    expect(salesProjectApiMock.contracts.activate).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('审批实例')
    expect(wrapper.text()).toContain('900')
    expect(wrapper.find('input[data-test="attachment-file"]').exists()).toBe(false)
  })

  it('重新打开审批中的草稿合同时识别最新审批摘要并锁定附件区', async () => {
    salesProjectApiMock.contracts.get.mockResolvedValueOnce({
      ...contract,
      status: 'DRAFT',
      approvalSummary: {
        id: 900,
        status: 'SUBMITTED',
        submittedAt: '2026-07-13T10:00:00+08:00',
      },
    })
    const wrapper = await mountDrawer({ mode: 'edit', contractId: 55 })

    expect(wrapper.text()).toContain('审批中')
    expect(wrapper.text()).toContain('审批实例')
    expect(wrapper.text()).toContain('900')
    expect(wrapper.find('input[data-test="attachment-file"]').exists()).toBe(false)
    expect(documentPlatformApiMock.printTemplates.list).toHaveBeenCalledWith({
      sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
    })
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
