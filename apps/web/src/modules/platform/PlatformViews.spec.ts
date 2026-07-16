import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ApprovalCenterView from './approvals/ApprovalCenterView.vue'
import MessageCenterView from './messages/MessageCenterView.vue'
import DocumentTaskCenterView from './documentTasks/DocumentTaskCenterView.vue'
import AttachmentPanel from './components/AttachmentPanel.vue'
import PrintAction from './components/PrintAction.vue'
import { useAuthStore } from '../../stores/authStore'

const documentPlatformApiMock = vi.hoisted(() => ({
  approvalTasks: {
    list: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
  },
  approvals: {
    get: vi.fn(),
    withdraw: vi.fn(),
    cancel: vi.fn(),
  },
  messages: {
    listMine: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  },
  documentTasks: {
    list: vi.fn(),
    get: vi.fn(),
    errors: vi.fn(),
    cancel: vi.fn(),
    download: vi.fn(),
  },
  imports: {
    confirm: vi.fn(),
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

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
}))

vi.mock('../../shared/file/download', () => ({
  downloadFile: vi.fn(),
  triggerBrowserDownload: vi.fn(),
}))

function mountWithAuth(component: unknown, permissions = [
  'platform:todo:view',
  'platform:approval:view',
  'platform:message:view',
  'platform:message:read',
  'platform:document-task:view',
  'platform:document-task:cancel',
  'platform:document-task:download',
], props: Record<string, unknown> = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(component, {
    props,
    global: {
      plugins: [pinia, ElementPlus],
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a :data-to="to"><slot /></a>',
        },
      },
    },
  })
}

function buttonByTest(wrapper: ReturnType<typeof mountWithAuth>, testId: string) {
  const button = wrapper.findAllComponents({ name: 'ElButton' })
    .find((item) => item.attributes('data-test') === testId)
  expect(button?.exists()).toBe(true)
  return button!
}

async function clickButtonByTest(wrapper: ReturnType<typeof mountWithAuth>, testId: string) {
  const button = buttonByTest(wrapper, testId)
  const onClick = (button.props() as Record<string, unknown>).onClick
  if (typeof onClick === 'function') {
    onClick(new MouseEvent('click'))
  } else {
    button.vm.$emit('click', new MouseEvent('click'))
  }
  await flushPromises()
}

describe('022 平台页面', () => {
  beforeEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
    documentPlatformApiMock.approvalTasks.list.mockResolvedValue({
      items: [{
        id: 3,
        taskId: 70,
        taskNo: 'AT-001',
        sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
        objectType: 'SALES_PROJECT_CONTRACT',
        objectId: 55,
        objectNo: 'SC-001',
        objectName: '主合同',
        status: 'PENDING',
        currentStepName: '固定审批',
        applicantName: '销售',
        assignedAt: '2026-07-13T10:00:00+08:00',
        availableActions: [],
        version: 4,
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    documentPlatformApiMock.approvals.get.mockResolvedValue({
      id: 3,
      taskId: 701,
      taskVersion: 16,
      sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
      objectType: 'SALES_PROJECT_CONTRACT',
      objectId: 55,
      objectNo: 'SC-001',
      objectName: '主合同',
      status: 'SUBMITTED',
      applicantName: '销售',
      submittedAt: '2026-07-13T10:00:00+08:00',
      version: 6,
      availableActions: ['APPROVE', 'REJECT', 'WITHDRAW', 'CANCEL'],
      steps: [{ taskId: 701, stepName: '固定审批', status: 'PENDING', candidatePermission: 'sales:contract:activate-approve', version: 16 }],
      histories: [{ action: 'SUBMIT', operatorName: '销售', operatedAt: '2026-07-13T10:00:00+08:00', comment: '提交' }],
      attachmentSnapshots: [],
    })
    documentPlatformApiMock.approvalTasks.approve.mockResolvedValue({ id: 3, status: 'APPROVED' })
    documentPlatformApiMock.approvalTasks.reject.mockResolvedValue({ id: 3, status: 'REJECTED' })
    documentPlatformApiMock.approvals.withdraw.mockResolvedValue({ id: 3, status: 'WITHDRAWN' })
    documentPlatformApiMock.approvals.cancel.mockResolvedValue({ id: 3, status: 'CANCELLED' })
    documentPlatformApiMock.messages.listMine.mockResolvedValue({
      items: [{
        id: 11,
        title: '审批已通过',
        content: '合同审批完成',
        status: 'UNREAD',
        category: 'APPROVAL',
        createdAt: '2026-07-13T10:00:00+08:00',
        relatedObjectType: 'SALES_PROJECT_CONTRACT',
        relatedObjectId: 55,
        businessRoute: 'https://example.invalid/leak',
        version: 2,
      }],
      total: 1,
      page: 1,
      pageSize: 10,
      unreadCount: 1,
    })
    documentPlatformApiMock.messages.markRead.mockResolvedValue({ id: 11, status: 'READ' })
    documentPlatformApiMock.messages.markAllRead.mockResolvedValue({ updatedCount: 1 })
    documentPlatformApiMock.documentTasks.list.mockResolvedValue({
      items: [{
        id: 91,
        taskNo: 'TASK-001',
        taskType: 'MATERIAL_IMPORT',
        objectType: 'MATERIAL',
        direction: 'IMPORT',
        stage: 'VALIDATE',
        status: 'VALIDATION_FAILED',
        progressPercent: 100,
        totalRows: 2,
        successRows: 0,
        failedRows: 1,
        createdByName: '管理员',
        createdAt: '2026-07-13T10:00:00+08:00',
        completedAt: '2026-07-13T10:01:00+08:00',
        expiresAt: '2026-07-20T10:00:00+08:00',
        availableActions: ['ERRORS', 'DOWNLOAD'],
        version: 2,
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    documentPlatformApiMock.documentTasks.errors.mockResolvedValue({
      items: [{ rowNo: 3, columnName: '物料编码', code: 'IMPORT_FILE_INVALID', message: '编码重复', suggestion: '请修改编码' }],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    documentPlatformApiMock.documentTasks.download.mockResolvedValue({
      blob: new Blob(['errors']),
      fileName: '失败明细.xlsx',
    })
    documentPlatformApiMock.documentTasks.get.mockResolvedValue({ id: 91, status: 'SUCCEEDED', version: 3 })
    documentPlatformApiMock.imports.confirm.mockResolvedValue({ id: 91, status: 'RUNNING', version: 3 })
    documentPlatformApiMock.attachments.list.mockResolvedValue({
      items: [{
        id: 5,
        objectType: 'SALES_PROJECT_CONTRACT',
        objectId: 55,
        fileName: '合同附件.pdf',
        fileSize: 2048,
        contentType: 'application/pdf',
        uploadedByName: '管理员',
        uploadedAt: '2026-07-13T10:00:00+08:00',
        status: 'AVAILABLE',
        availableActions: ['DOWNLOAD', 'DELETE'],
        version: 2,
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    documentPlatformApiMock.printTemplates.list.mockResolvedValue([
      {
        templateCode: 'CONTRACT_ACTIVATION_APPROVAL_V1',
        name: '合同生效审批单',
        templateVersion: 1,
        sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
      },
    ])
    documentPlatformApiMock.printPreviews.get.mockResolvedValue({
      approvalInstanceId: 3,
      templateCode: 'CONTRACT_ACTIVATION_APPROVAL_V1',
      templateVersion: 1,
      sections: [{ title: '审批对象', fields: [{ label: '合同编号', value: 'SC-001' }] }],
    })
    documentPlatformApiMock.printTasks.create.mockResolvedValue({
      id: 93,
      taskNo: 'PRINT-001',
      taskType: 'APPROVAL_PRINT',
      status: 'QUEUED',
      version: 1,
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('审批中心按页签查询待办，详情动作只消费最新 availableActions 并携带版本与幂等键', async () => {
    const wrapper = mountWithAuth(ApprovalCenterView)
    await flushPromises()

    expect(documentPlatformApiMock.approvalTasks.list).toHaveBeenCalledWith(expect.objectContaining({ scope: 'TODO' }))
    expect(wrapper.text()).toContain('我的待办')
    expect(wrapper.text()).toContain('已处理')
    expect(wrapper.text()).toContain('我发起的')
    expect(wrapper.text()).toContain('SC-001')

    await wrapper.find('[data-test="open-approval-detail"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.approvals.get).toHaveBeenCalledWith(3)
    expect(wrapper.text()).toContain('固定审批')
    expect(wrapper.text()).toContain('提交')
    expect(wrapper.find('[data-test="approval-cancel"]').exists()).toBe(true)

    await wrapper.find('[data-test="approval-comment"]').setValue('同意生效')
    await wrapper.find('[data-test="approve-task"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.approvalTasks.approve).toHaveBeenCalledWith(701, {
      version: 16,
      comment: '同意生效',
      idempotencyKey: expect.any(String),
    })
  })

  it('审批列表为 023 业务对象提供业务单据入口且未知对象保持纯文本', async () => {
    documentPlatformApiMock.approvalTasks.list.mockResolvedValueOnce({
      items: [
        {
          id: 31,
          taskId: 731,
          taskNo: 'AT-STK',
          sceneCode: 'INVENTORY_STOCKTAKE_VARIANCE_POST',
          objectType: 'INVENTORY_STOCKTAKE',
          objectId: 1,
          objectNo: 'INV-STK-001',
          objectName: '差异盘点',
          status: 'PENDING',
          currentStepName: '固定审批',
          applicantName: '仓管',
          assignedAt: '2026-07-14T10:00:00+08:00',
          availableActions: [],
          version: 1,
        },
        {
          id: 32,
          taskId: 732,
          taskNo: 'AT-OWN',
          sceneCode: 'INVENTORY_OWNERSHIP_CONVERSION_POST',
          objectType: 'INVENTORY_OWNERSHIP_CONVERSION',
          objectId: 2,
          objectNo: 'INV-OWN-001',
          objectName: '权属转换',
          status: 'PENDING',
          currentStepName: '固定审批',
          applicantName: '仓管',
          assignedAt: '2026-07-14T10:00:00+08:00',
          availableActions: [],
          version: 1,
        },
        {
          id: 33,
          taskId: 733,
          taskNo: 'AT-VAL',
          sceneCode: 'INVENTORY_VALUATION_ADJUSTMENT_POST',
          objectType: 'INVENTORY_VALUATION_ADJUSTMENT',
          objectId: 3,
          objectNo: 'INV-VAL-001',
          objectName: '估值调整',
          status: 'PENDING',
          currentStepName: '固定审批',
          applicantName: '财务',
          assignedAt: '2026-07-14T10:00:00+08:00',
          availableActions: [],
          version: 1,
        },
        {
          id: 34,
          taskId: 734,
          taskNo: 'AT-UNK',
          sceneCode: 'UNKNOWN_SCENE',
          objectType: 'UNKNOWN_OBJECT',
          objectId: 4,
          objectNo: 'UNKNOWN-001',
          objectName: '未知对象',
          status: 'PENDING',
          currentStepName: '固定审批',
          applicantName: '系统',
          assignedAt: '2026-07-14T10:00:00+08:00',
          availableActions: [],
          version: 1,
        },
      ],
      total: 4,
      page: 1,
      pageSize: 10,
    })
    const wrapper = mountWithAuth(ApprovalCenterView)
    await flushPromises()

    expect(wrapper.find('[data-to="/inventory/stocktakes/1"]').text()).toContain('查看业务单据')
    expect(wrapper.find('[data-to="/inventory/ownership-conversions/2"]').text()).toContain('查看业务单据')
    expect(wrapper.find('[data-to="/inventory/valuation-adjustments/3"]').text()).toContain('查看业务单据')
    expect(wrapper.text()).toContain('UNKNOWN-001')
    expect(wrapper.find('[data-to="/inventory/unknown/4"]').exists()).toBe(false)
  })

  it.each([
    ['INVENTORY_STOCKTAKE', 1, '/inventory/stocktakes/1'],
    ['INVENTORY_OWNERSHIP_CONVERSION', 2, '/inventory/ownership-conversions/2'],
    ['INVENTORY_VALUATION_ADJUSTMENT', 3, '/inventory/valuation-adjustments/3'],
    ['SALES_QUOTE', 9, '/sales/quotes/9'],
  ])('审批详情为 %s 提供业务单据入口', async (objectType, objectId, expectedPath) => {
    documentPlatformApiMock.approvalTasks.list.mockResolvedValueOnce({
      items: [{
        id: 41,
        taskId: 741,
        taskNo: 'AT-023',
        sceneCode: 'INVENTORY_APPROVAL',
        objectType,
        objectId,
        objectNo: 'INV-023-001',
        objectName: '023 单据',
        status: 'PENDING',
        currentStepName: '固定审批',
        applicantName: '仓管',
        assignedAt: '2026-07-14T10:00:00+08:00',
        availableActions: [],
        version: 1,
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    documentPlatformApiMock.approvals.get.mockResolvedValueOnce({
      id: 41,
      taskId: 741,
      taskVersion: 1,
      sceneCode: 'INVENTORY_APPROVAL',
      objectType,
      objectId,
      objectNo: 'INV-023-001',
      objectName: '023 单据',
      status: 'SUBMITTED',
      applicantName: '仓管',
      submittedAt: '2026-07-14T10:00:00+08:00',
      version: 1,
      availableActions: [],
      steps: [],
      histories: [],
      attachmentSnapshots: [],
    })
    const wrapper = mountWithAuth(ApprovalCenterView)
    await flushPromises()

    await wrapper.find('[data-test="open-approval-detail"]').trigger('click')
    await flushPromises()

    const link = wrapper.find('[data-test="approval-detail-business-link"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('查看业务单据')
    if (objectType === 'SALES_QUOTE') {
      expect(link.text()).toContain('销售报价')
    }
    expect(link.attributes('data-to')).toBe(expectedPath)
  })

  it('审批详情当前任务 version 为 0 时仍显示通过和驳回并按 0 提交', async () => {
    const zeroVersionDetail = {
      id: 3,
      taskId: 701,
      taskVersion: 0,
      sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
      objectType: 'SALES_PROJECT_CONTRACT',
      objectId: 55,
      objectNo: 'SC-001',
      objectName: '主合同',
      status: 'SUBMITTED',
      applicantName: '销售',
      submittedAt: '2026-07-13T10:00:00+08:00',
      version: 6,
      availableActions: ['APPROVE', 'REJECT'],
      steps: [{ taskId: 701, stepName: '固定审批', status: 'PENDING', candidatePermission: 'sales:contract:activate-approve', version: 0 }],
      histories: [],
      attachmentSnapshots: [],
    }
    documentPlatformApiMock.approvals.get.mockResolvedValueOnce(zeroVersionDetail).mockResolvedValueOnce(zeroVersionDetail)

    const approveWrapper = mountWithAuth(ApprovalCenterView)
    await flushPromises()
    await approveWrapper.find('[data-test="open-approval-detail"]').trigger('click')
    await flushPromises()

    expect(approveWrapper.find('[data-test="approve-task"]').exists()).toBe(true)
    expect(approveWrapper.find('[data-test="reject-task"]').exists()).toBe(true)
    await approveWrapper.find('[data-test="approval-comment"]').setValue('同意生效')
    await approveWrapper.find('[data-test="approve-task"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.approvalTasks.approve).toHaveBeenCalledWith(701, {
      version: 0,
      comment: '同意生效',
      idempotencyKey: expect.any(String),
    })

    const rejectWrapper = mountWithAuth(ApprovalCenterView)
    await flushPromises()
    await rejectWrapper.find('[data-test="open-approval-detail"]').trigger('click')
    await flushPromises()

    expect(rejectWrapper.find('[data-test="approve-task"]').exists()).toBe(true)
    expect(rejectWrapper.find('[data-test="reject-task"]').exists()).toBe(true)
    await rejectWrapper.find('[data-test="approval-comment"]').setValue('合同金额需调整')
    await rejectWrapper.find('[data-test="reject-task"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.approvalTasks.reject).toHaveBeenCalledWith(701, {
      version: 0,
      comment: '合同金额需调整',
      idempotencyKey: expect.any(String),
    })
  })

  it('审批详情支持驳回、撤回和治理取消的原因、版本与幂等键', async () => {
    const rejectWrapper = mountWithAuth(ApprovalCenterView)
    await flushPromises()
    await rejectWrapper.find('[data-test="open-approval-detail"]').trigger('click')
    await flushPromises()
    await rejectWrapper.find('[data-test="approval-comment"]').setValue('合同金额需调整')
    await rejectWrapper.find('[data-test="reject-task"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.approvalTasks.reject).toHaveBeenCalledWith(701, {
      version: 16,
      comment: '合同金额需调整',
      idempotencyKey: expect.any(String),
    })

    const withdrawWrapper = mountWithAuth(ApprovalCenterView)
    await flushPromises()
    await withdrawWrapper.find('[data-test="open-approval-detail"]').trigger('click')
    await flushPromises()
    await withdrawWrapper.find('[data-test="approval-comment"]').setValue('补充附件')
    await withdrawWrapper.find('[data-test="withdraw-approval"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.approvals.withdraw).toHaveBeenCalledWith(3, {
      version: 6,
      comment: '补充附件',
      idempotencyKey: expect.any(String),
    })

    const cancelWrapper = mountWithAuth(ApprovalCenterView)
    await flushPromises()
    await cancelWrapper.find('[data-test="open-approval-detail"]').trigger('click')
    await flushPromises()
    await cancelWrapper.find('[data-test="approval-comment"]').setValue('对象已失效')
    await cancelWrapper.find('[data-test="approval-cancel"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.approvals.cancel).toHaveBeenCalledWith(3, {
      version: 6,
      comment: '对象已失效',
      idempotencyKey: expect.any(String),
    })
  })

  it('审批任务动作并发过期后清除旧动作并提示刷新', async () => {
    documentPlatformApiMock.approvalTasks.approve.mockRejectedValueOnce(new Error('审批已过期需刷新'))
    const wrapper = mountWithAuth(ApprovalCenterView)
    await flushPromises()

    await wrapper.find('[data-test="open-approval-detail"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="approval-comment"]').setValue('同意生效')
    await wrapper.find('[data-test="approve-task"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('已过期需刷新')
    expect(wrapper.find('[data-test="approve-task"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="reject-task"]').exists()).toBe(false)
  })

  it('审批详情缺少 taskId 时隐藏通过和驳回，且不退回使用实例 id', async () => {
    documentPlatformApiMock.approvals.get.mockResolvedValueOnce({
      id: 3,
      taskId: null,
      sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
      objectType: 'SALES_PROJECT_CONTRACT',
      objectId: 55,
      objectNo: 'SC-001',
      objectName: '主合同',
      status: 'SUBMITTED',
      applicantName: '销售',
      submittedAt: '2026-07-13T10:00:00+08:00',
      version: 6,
      availableActions: ['APPROVE', 'REJECT'],
      steps: [{ stepName: '固定审批', status: 'PENDING', candidatePermission: 'sales:contract:activate-approve' }],
      histories: [],
      attachmentSnapshots: [],
    })
    const wrapper = mountWithAuth(ApprovalCenterView)
    await flushPromises()

    await wrapper.find('[data-test="open-approval-detail"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="approve-task"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="reject-task"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('当前审批任务不可处理，请刷新审批详情')
    expect(documentPlatformApiMock.approvalTasks.approve).not.toHaveBeenCalled()
    expect(documentPlatformApiMock.approvalTasks.reject).not.toHaveBeenCalled()
  })

  it('消息中心支持未读筛选、单条已读和全部已读，已读携带版本且业务跳转只用白名单路由', async () => {
    const wrapper = mountWithAuth(MessageCenterView)
    await flushPromises()

    expect(documentPlatformApiMock.messages.listMine).toHaveBeenCalledWith(expect.objectContaining({ unreadOnly: false }))
    expect(wrapper.text()).toContain('审批已通过')
    expect(wrapper.text()).toContain('未读 1')
    expect(wrapper.text()).toContain('查看业务')

    await wrapper.find('[data-test="filter-unread-messages"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.messages.listMine).toHaveBeenLastCalledWith(expect.objectContaining({ unreadOnly: true }))

    await wrapper.find('[data-test="mark-message-read"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.messages.markRead).toHaveBeenCalledWith(11, { version: 2 })
    expect(wrapper.find('[data-to="https://example.invalid/leak"]').exists()).toBe(false)
    expect(wrapper.find('[data-to="/sales/projects?contractId=55"]').exists()).toBe(true)

    await wrapper.find('[data-test="mark-all-messages-read"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.messages.markAllRead).toHaveBeenCalled()
  })

  it('任务中心展示任务阶段、进度、失败分页、确认导入和下载动作', async () => {
    documentPlatformApiMock.documentTasks.list.mockResolvedValueOnce({
      items: [{
        id: 91,
        taskNo: 'TASK-001',
        taskType: 'MATERIAL_IMPORT',
        objectType: 'MATERIAL',
        direction: 'IMPORT',
        stage: 'VALIDATE',
        status: 'READY_TO_COMMIT',
        progressPercent: 100,
        totalRows: 2,
        successRows: 2,
        failedRows: 0,
        createdByName: '管理员',
        createdAt: '2026-07-13T10:00:00+08:00',
        completedAt: '2026-07-13T10:01:00+08:00',
        expiresAt: '2026-07-20T10:00:00+08:00',
        availableActions: ['CONFIRM', 'DOWNLOAD'],
        version: 2,
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    const wrapper = mountWithAuth(DocumentTaskCenterView)
    await flushPromises()

    expect(documentPlatformApiMock.documentTasks.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(wrapper.text()).toContain('物料导入')
    expect(wrapper.text()).toContain('待确认')
    expect(wrapper.text()).toContain('确认导入')
    expect(wrapper.text()).not.toContain('确认入库')
    expect(wrapper.text()).not.toContain('部分成功')

    await clickButtonByTest(wrapper, 'confirm-document-task')
    expect(documentPlatformApiMock.imports.confirm).toHaveBeenCalledWith(91, {
      version: 2,
      idempotencyKey: expect.any(String),
    })
  })

  it('任务中心按 availableActions 展示错误、下载和取消动作', async () => {
    const wrapper = mountWithAuth(DocumentTaskCenterView)
    await flushPromises()

    await clickButtonByTest(wrapper, 'view-task-errors')
    expect(documentPlatformApiMock.documentTasks.errors).toHaveBeenCalledWith(91, { page: 1, pageSize: 10 })
    expect(wrapper.text()).toContain('编码重复')

    await (wrapper.vm as unknown as { downloadTask: (record: unknown) => Promise<void> }).downloadTask(
      (wrapper.vm as unknown as { records: unknown[] }).records[0],
    )
    await flushPromises()
    expect(documentPlatformApiMock.documentTasks.download).toHaveBeenCalledWith(91)
  })

  it('任务中心错误明细只由 ERRORS 动作控制，不能用错误数兜底显示', async () => {
    documentPlatformApiMock.documentTasks.list.mockResolvedValueOnce({
      items: [{
        id: 94,
        taskNo: 'TASK-004',
        taskType: 'MATERIAL_IMPORT',
        direction: 'IMPORT',
        stage: 'VALIDATE',
        status: 'VALIDATION_FAILED',
        totalRows: 2,
        failedRows: 2,
        availableActions: [],
        version: 3,
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    const wrapper = mountWithAuth(DocumentTaskCenterView)
    await flushPromises()

    expect(wrapper.find('[data-test="view-task-errors"]').exists()).toBe(false)
  })

  it('任务中心会轮询所有当前可见的非终态任务', async () => {
    vi.useFakeTimers()
    documentPlatformApiMock.documentTasks.list.mockResolvedValueOnce({
      items: [
        { id: 91, taskNo: 'TASK-001', taskType: 'MATERIAL_EXPORT', direction: 'EXPORT', stage: 'EXPORT', status: 'RUNNING', version: 1, availableActions: [] },
        { id: 92, taskNo: 'TASK-002', taskType: 'APPROVAL_PRINT', direction: 'PRINT', stage: 'PRINT', status: 'QUEUED', version: 1, availableActions: ['CANCEL'] },
      ],
      total: 2,
      page: 1,
      pageSize: 10,
    })
    documentPlatformApiMock.documentTasks.get
      .mockResolvedValueOnce({ id: 91, taskNo: 'TASK-001', taskType: 'MATERIAL_EXPORT', direction: 'EXPORT', stage: 'EXPORT', status: 'SUCCEEDED', version: 2 })
      .mockResolvedValueOnce({ id: 92, taskNo: 'TASK-002', taskType: 'APPROVAL_PRINT', direction: 'PRINT', stage: 'PRINT', status: 'RUNNING', version: 2 })
    mountWithAuth(DocumentTaskCenterView)
    await flushPromises()

    await vi.advanceTimersByTimeAsync(2500)
    await flushPromises()

    expect(documentPlatformApiMock.documentTasks.get).toHaveBeenCalledWith(91)
    expect(documentPlatformApiMock.documentTasks.get).toHaveBeenCalledWith(92)
  })

  it('附件面板消费分页结果并显示真实文件字段、限制提示和可用动作', async () => {
    const wrapper = mountWithAuth(AttachmentPanel, [
      'platform:attachment:view',
      'platform:attachment:upload',
      'platform:attachment:download',
      'platform:attachment:delete',
    ], { objectType: 'SALES_PROJECT_CONTRACT', objectId: 55, title: '合同附件' })
    await flushPromises()

    expect(documentPlatformApiMock.attachments.list).toHaveBeenCalledWith({
      objectType: 'SALES_PROJECT_CONTRACT',
      objectId: 55,
      page: 1,
      pageSize: 20,
    })
    expect(wrapper.text()).toContain('合同附件.pdf')
    expect(wrapper.text()).toContain('2.0 KiB')
    expect(wrapper.text()).toContain('application/pdf')
    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('最多 20 个附件')
    expect(wrapper.find('[data-test="download-attachment"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="delete-attachment"]').exists()).toBe(true)
  })

  it('附件面板缺少 availableActions 时默认不展示下载或删除动作', async () => {
    documentPlatformApiMock.attachments.list.mockResolvedValueOnce({
      items: [{
        id: 6,
        objectType: 'SALES_PROJECT_CONTRACT',
        objectId: 55,
        fileName: '受限附件.pdf',
        fileSize: 1024,
        contentType: 'application/pdf',
        uploadedByName: '管理员',
        uploadedAt: '2026-07-13T10:00:00+08:00',
        status: 'AVAILABLE',
        version: 1,
      }],
      total: 1,
      page: 1,
      pageSize: 20,
    })
    const wrapper = mountWithAuth(AttachmentPanel, [
      'platform:attachment:view',
      'platform:attachment:download',
      'platform:attachment:delete',
    ], { objectType: 'SALES_PROJECT_CONTRACT', objectId: 55, title: '合同附件' })
    await flushPromises()

    expect(wrapper.text()).toContain('受限附件.pdf')
    expect(wrapper.find('[data-test="download-attachment"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="delete-attachment"]').exists()).toBe(false)
  })

  it('打印入口按 sceneCode 查询模板，必须先预览再创建 PDF 任务', async () => {
    const wrapper = mountWithAuth(PrintAction, undefined, {
      sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
      approvalInstanceId: 3,
      title: '合同生效审批单',
    })
    await flushPromises()

    expect(documentPlatformApiMock.printTemplates.list).toHaveBeenCalledWith({
      sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
    })
    expect(buttonByTest(wrapper, 'preview-print').props().disabled).toBe(false)
    expect(wrapper.text()).toContain('预览 合同生效审批单')

    await clickButtonByTest(wrapper, 'preview-print')
    expect(documentPlatformApiMock.printPreviews.get).toHaveBeenCalledWith(3)
    expect(wrapper.text()).toContain('模板：合同生效审批单')
    expect(wrapper.text()).toContain('模板代码：SALES_PROJECT_CONTRACT_ACTIVATION')
    expect(wrapper.text()).toContain('模板版本：V1')
    expect(wrapper.text()).toContain('合同编号')
    expect(wrapper.text()).toContain('SC-001')

    await clickButtonByTest(wrapper, 'create-print-task')
    expect(documentPlatformApiMock.printTasks.create).toHaveBeenCalledWith({
      approvalInstanceId: 3,
      templateCode: 'CONTRACT_ACTIVATION_APPROVAL_V1',
      idempotencyKey: expect.any(String),
    })
  })
})
