import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { SalesProjectContractDetail, SalesProjectDetail } from '../../../shared/api/salesProjectApi'
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

async function mountDrawer(props: Partial<InstanceType<typeof SalesProjectContractDrawer>['$props']> = {}) {
  const wrapper = mount(SalesProjectContractDrawer, {
    props: {
      modelValue: true,
      mode: 'create',
      project,
      contractId: undefined,
      ...props,
    },
    global: {
      plugins: [ElementPlus],
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
})
