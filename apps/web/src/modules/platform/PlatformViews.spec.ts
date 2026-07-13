import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ApprovalCenterView from './approvals/ApprovalCenterView.vue'
import MessageCenterView from './messages/MessageCenterView.vue'
import DocumentTaskCenterView from './documentTasks/DocumentTaskCenterView.vue'
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
]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(component, {
    global: {
      plugins: [pinia, ElementPlus],
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a><slot /></a>',
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
    vi.clearAllMocks()
    documentPlatformApiMock.approvalTasks.list.mockResolvedValue({
      items: [{
        id: 7,
        instanceId: 3,
        taskNo: 'AT-001',
        scenarioCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
        objectType: 'SALES_PROJECT_CONTRACT',
        objectId: 55,
        objectNo: 'SC-001',
        objectName: '主合同',
        status: 'PENDING',
        currentStepName: '固定审批',
        applicantName: '销售',
        assignedAt: '2026-07-13T10:00:00+08:00',
        availableActions: ['APPROVE', 'REJECT'],
        version: 4,
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    documentPlatformApiMock.approvals.get.mockResolvedValue({
      id: 3,
      scenarioCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
      objectType: 'SALES_PROJECT_CONTRACT',
      objectId: 55,
      objectNo: 'SC-001',
      objectName: '主合同',
      status: 'SUBMITTED',
      applicantName: '销售',
      submittedAt: '2026-07-13T10:00:00+08:00',
      version: 6,
      availableActions: ['WITHDRAW'],
      steps: [{ stepName: '固定审批', status: 'PENDING', candidatePermission: 'sales:contract:activate-approve' }],
      histories: [{ action: 'SUBMIT', operatorName: '销售', operatedAt: '2026-07-13T10:00:00+08:00', comment: '提交' }],
      attachmentSnapshots: [],
    })
    documentPlatformApiMock.approvalTasks.approve.mockResolvedValue({ id: 3, status: 'APPROVED' })
    documentPlatformApiMock.approvalTasks.reject.mockResolvedValue({ id: 3, status: 'REJECTED' })
    documentPlatformApiMock.messages.listMine.mockResolvedValue({
      items: [{
        id: 11,
        title: '审批已通过',
        content: '合同审批完成',
        status: 'UNREAD',
        category: 'APPROVAL',
        createdAt: '2026-07-13T10:00:00+08:00',
        businessRoute: '/sales/projects/12',
      }],
      total: 1,
      page: 1,
      pageSize: 10,
      unreadCount: 1,
    })
    documentPlatformApiMock.messages.markRead.mockResolvedValue({ id: 11, status: 'READ' })
    documentPlatformApiMock.messages.markAllRead.mockResolvedValue({ unreadCount: 0 })
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
        availableActions: ['DOWNLOAD_FAILURES'],
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
  })

  it('审批中心按页签查询待办、展示详情并按 availableActions 处理任务', async () => {
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
    expect(wrapper.find('[data-test="approval-cancel"]').exists()).toBe(false)

    await wrapper.find('[data-test="approval-comment"]').setValue('同意生效')
    await wrapper.find('[data-test="approve-task"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.approvalTasks.approve).toHaveBeenCalledWith(7, { version: 4, comment: '同意生效' })
  })

  it('消息中心支持未读筛选、单条已读和全部已读，业务跳转只使用后端授权 route', async () => {
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
    expect(documentPlatformApiMock.messages.markRead).toHaveBeenCalledWith(11)

    await wrapper.find('[data-test="mark-all-messages-read"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.messages.markAllRead).toHaveBeenCalled()
  })

  it('任务中心展示任务阶段、进度、失败分页、过期态和下载动作', async () => {
    const wrapper = mountWithAuth(DocumentTaskCenterView)
    await flushPromises()

    expect(documentPlatformApiMock.documentTasks.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(wrapper.text()).toContain('物料导入')
    expect(wrapper.text()).toContain('校验失败')
    expect(wrapper.text()).not.toContain('部分成功')

    await clickButtonByTest(wrapper, 'view-task-errors')
    expect(documentPlatformApiMock.documentTasks.errors).toHaveBeenCalledWith(91, { page: 1, pageSize: 10 })
    expect(wrapper.text()).toContain('编码重复')

    await (wrapper.vm as unknown as { downloadTask: (record: unknown) => Promise<void> }).downloadTask(
      (wrapper.vm as unknown as { records: unknown[] }).records[0],
    )
    await flushPromises()
    expect(documentPlatformApiMock.documentTasks.download).toHaveBeenCalledWith(91)
  })
})
