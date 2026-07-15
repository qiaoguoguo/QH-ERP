import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DocumentTaskRecord } from '../../shared/api/documentPlatformApi'
import { useAuthStore } from '../../stores/authStore'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'

const documentPlatformApiMock = vi.hoisted(() => ({
  documentTasks: {
    get: vi.fn(),
    errors: vi.fn(),
    download: vi.fn(),
    cancel: vi.fn(),
  },
  imports: {
    confirm: vi.fn(),
  },
}))

const downloadFileMock = vi.hoisted(() => vi.fn())

vi.mock('../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
  createIdempotencyKey: () => 'procurement-document-key',
}))

vi.mock('../../shared/file/download', () => ({
  downloadFile: downloadFileMock,
}))

const baseTask: DocumentTaskRecord = {
  id: 91,
  taskNo: 'TASK-PROC-001',
  taskType: 'PROCUREMENT_QUOTE_IMPORT',
  direction: 'IMPORT',
  stage: 'VALIDATE',
  status: 'SUCCEEDED',
  progressPercent: 100,
  totalRows: 3,
  successRows: 3,
  failedRows: 0,
  expiresAt: '2026-07-16T10:00:00+08:00',
  availableActions: ['DOWNLOAD'],
  version: 4,
}

function setupSession() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'buyer', displayName: '采购员', status: 'ENABLED' },
    menus: [],
    permissions: ['platform:document-task:view'],
  })
  return pinia
}

describe('采购文档任务面板', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    documentPlatformApiMock.documentTasks.get.mockResolvedValue({ ...baseTask, status: 'SUCCEEDED' })
    documentPlatformApiMock.documentTasks.errors.mockResolvedValue({
      items: [{ rowNo: 2, columnName: 'taxRate', code: 'INVALID_TAX', message: '税率无效', suggestion: '填写 0.13' }],
      page: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
    })
    documentPlatformApiMock.documentTasks.download.mockResolvedValue({ blob: new Blob(['pdf']), fileName: '采购任务.pdf' })
    documentPlatformApiMock.documentTasks.cancel.mockResolvedValue({ ...baseTask, status: 'CANCELLED' })
    documentPlatformApiMock.imports.confirm.mockResolvedValue({ ...baseTask, status: 'RUNNING' })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('展示任务状态、失败明细、结果下载和过期文案', async () => {
    const wrapper = mount(ProcurementDocumentTaskPanel, {
      props: {
        task: {
          ...baseTask,
          status: 'VALIDATION_FAILED',
          failedRows: 1,
          availableActions: ['ERRORS', 'DOWNLOAD'],
        },
      },
      global: { plugins: [setupSession(), ElementPlus] },
    })

    expect(wrapper.text()).toContain('TASK-PROC-001')
    expect(wrapper.text()).toContain('采购报价导入')
    expect(wrapper.text()).toContain('校验失败')
    expect(wrapper.text()).toContain('结果过期')

    await wrapper.find('[data-test="procurement-task-errors"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.documentTasks.errors).toHaveBeenCalledWith(91, { page: 1, pageSize: 10 })
    expect(wrapper.text()).toContain('税率无效')

    await wrapper.find('[data-test="procurement-task-download"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.documentTasks.download).toHaveBeenCalledWith(91)
    expect(downloadFileMock).toHaveBeenCalled()

    await wrapper.setProps({ task: { ...baseTask, status: 'EXPIRED', availableActions: ['DOWNLOAD'] } })
    await flushPromises()
    expect(wrapper.text()).toContain('结果已过期，请重新发起文档任务')
    expect(wrapper.find('[data-test="procurement-task-download"]').exists()).toBe(false)
  })

  it('待确认和执行中任务复用确认动作与轮询刷新', async () => {
    vi.useFakeTimers()
    documentPlatformApiMock.documentTasks.get.mockResolvedValueOnce({
      ...baseTask,
      status: 'RUNNING',
      availableActions: ['CANCEL'],
    })
    const wrapper = mount(ProcurementDocumentTaskPanel, {
      props: {
        task: {
          ...baseTask,
          status: 'READY_TO_COMMIT',
          availableActions: ['CONFIRM', 'CANCEL'],
        },
      },
      global: { plugins: [setupSession(), ElementPlus] },
    })

    await wrapper.find('[data-test="procurement-task-confirm"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.imports.confirm).toHaveBeenCalledWith(91, {
      version: 4,
      idempotencyKey: 'procurement-document-key',
    })

    await wrapper.setProps({ task: { ...baseTask, status: 'RUNNING', availableActions: ['CANCEL'] } })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(2500)
    await flushPromises()
    expect(documentPlatformApiMock.documentTasks.get).toHaveBeenCalledWith(91)
    expect(wrapper.text()).toContain('执行中，自动刷新状态')

    await wrapper.find('[data-test="procurement-task-cancel"]').trigger('click')
    await flushPromises()
    expect(documentPlatformApiMock.documentTasks.cancel).toHaveBeenCalledWith(91, { version: 4, reason: '用户取消' })
  })
})
