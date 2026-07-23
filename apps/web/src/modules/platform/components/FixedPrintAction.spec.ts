import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ResourceId } from '../../../shared/api/documentPlatformApi'
import FixedPrintAction from './FixedPrintAction.vue'
import { useAuthStore } from '../../../stores/authStore'
import { installElementPlus } from '../../../elementPlus'

const documentPlatformApiMock = vi.hoisted(() => ({
  printTemplates: { list: vi.fn() },
  printPreviews: { previewObject: vi.fn() },
  printTasks: { create: vi.fn() },
}))

vi.mock('../../../shared/api/documentPlatformApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/api/documentPlatformApi')>()),
  documentPlatformApi: documentPlatformApiMock,
}))

interface FixedPrintActionProps {
  objectType: string
  objectId?: ResourceId | null
  objectNo?: string | null
  objectStatus?: string | null
  allowedObjectStatuses?: string[]
  title: string
  variant?: 'panel' | 'compact'
}

function mountWithAuth(props: FixedPrintActionProps, permissions = ['platform:print:generate']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(FixedPrintAction, {
    props,
    global: {
      plugins: [pinia, installElementPlus],
    },
    attachTo: document.body,
  })
}

function mountWithAuthAndAttrs(
  props: FixedPrintActionProps,
  attrs: Record<string, unknown>,
  permissions = ['platform:print:generate'],
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  return mount(FixedPrintAction, {
    props,
    attrs,
    global: {
      plugins: [pinia, installElementPlus],
    },
    attachTo: document.body,
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

async function openCompactPrint(wrapper: ReturnType<typeof mountWithAuth>) {
  await wrapper.find('[data-test="open-fixed-print-action"]').trigger('click')
  await flushPromises()
}

function teleportedButtonByTest(testId: string): HTMLButtonElement {
  const button = document.body.querySelector<HTMLButtonElement>(`button[data-test="${testId}"]`)
  expect(button).not.toBeNull()
  return button!
}

describe('034 对象级固定打印入口', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
    documentPlatformApiMock.printTemplates.list.mockResolvedValue([
      {
        templateCode: 'SALES_ORDER_V1',
        name: '销售订单',
        templateVersion: 1,
        objectType: 'SALES_ORDER',
      },
    ])
    documentPlatformApiMock.printPreviews.previewObject.mockResolvedValue({
      objectType: 'SALES_ORDER',
      objectId: 88,
      templateCode: 'SALES_ORDER_V1',
      templateVersion: 1,
      sections: [{ title: '订单信息', fields: [{ label: '订单号', value: 'SO-001' }] }],
    })
    documentPlatformApiMock.printTasks.create.mockResolvedValue({
      id: 93,
      taskNo: 'PRINT-034-001',
      taskType: 'SALES_ORDER_PRINT',
      status: 'QUEUED',
      version: 1,
    })
  })

  it('按对象类型查询固定模板，必须先预览同一模板再生成 PDF 任务', async () => {
    const wrapper = mountWithAuth({
      objectType: 'SALES_ORDER',
      objectId: 88,
      objectNo: 'SO-001',
      title: '固定打印',
    })
    await flushPromises()

    expect(documentPlatformApiMock.printTemplates.list).toHaveBeenCalledWith({ objectType: 'SALES_ORDER' })
    expect(wrapper.text()).toContain('销售订单')
    expect(buttonByTest(wrapper, 'create-fixed-print-task').props('disabled')).toBe(true)

    await clickButtonByTest(wrapper, 'preview-fixed-print')
    expect(documentPlatformApiMock.printPreviews.previewObject).toHaveBeenCalledWith({
      objectType: 'SALES_ORDER',
      objectId: 88,
      templateCode: 'SALES_ORDER_V1',
    })
    expect(wrapper.text()).toContain('模板代码：SALES_ORDER_V1')
    expect(wrapper.text()).toContain('模板版本：V1')
    expect(wrapper.text()).toContain('订单号')
    expect(wrapper.text()).toContain('SO-001')

    await clickButtonByTest(wrapper, 'create-fixed-print-task')
    expect(documentPlatformApiMock.printTasks.create).toHaveBeenCalledWith({
      objectType: 'SALES_ORDER',
      objectId: 88,
      templateCode: 'SALES_ORDER_V1',
      idempotencyKey: expect.stringContaining('fixed-print-'),
    })
    expect(wrapper.text()).toContain('PRINT-034-001')
  })

  it('缺少固定打印权限时隐藏预览和生成入口', async () => {
    const wrapper = mountWithAuth({
      objectType: 'SALES_ORDER',
      objectId: 88,
      title: '固定打印',
    }, [])
    await flushPromises()

    expect(wrapper.text()).toContain('无固定打印权限')
    expect(wrapper.find('[data-test="preview-fixed-print"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="create-fixed-print-task"]').exists()).toBe(false)
  })

  it('无固定模板时显示清晰空状态且不暴露生成任务入口', async () => {
    documentPlatformApiMock.printTemplates.list.mockResolvedValueOnce([])
    const wrapper = mountWithAuth({
      objectType: 'SALES_ORDER',
      objectId: 88,
      title: '固定打印',
    })
    await flushPromises()

    expect(wrapper.text()).toContain('暂无固定模板')
    expect(wrapper.find('[data-test="preview-fixed-print"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="create-fixed-print-task"]').exists()).toBe(false)
  })

  it('预览失败时显示错误并保持生成任务禁用', async () => {
    documentPlatformApiMock.printPreviews.previewObject.mockRejectedValueOnce(new Error('固定打印预览失败'))
    const wrapper = mountWithAuth({
      objectType: 'SALES_ORDER',
      objectId: 88,
      title: '固定打印',
    })
    await flushPromises()

    await clickButtonByTest(wrapper, 'preview-fixed-print')

    expect(wrapper.text()).toContain('固定打印预览失败')
    expect(buttonByTest(wrapper, 'create-fixed-print-task').props('disabled')).toBe(true)
    expect(documentPlatformApiMock.printTasks.create).not.toHaveBeenCalled()
  })

  it('对象状态不允许时显示状态原因，不调用预览或生成任务', async () => {
    const wrapper = mountWithAuth({
      objectType: 'SALES_ORDER',
      objectId: 88,
      objectStatus: 'DRAFT',
      allowedObjectStatuses: ['APPROVED'],
      title: '固定打印',
    })
    await flushPromises()

    expect(wrapper.text()).toContain('对象状态为草稿，不允许固定打印')
    expect(wrapper.text()).not.toContain('对象状态 DRAFT')
    expect(buttonByTest(wrapper, 'preview-fixed-print').props('disabled')).toBe(true)
    expect(buttonByTest(wrapper, 'create-fixed-print-task').props('disabled')).toBe(true)

    await clickButtonByTest(wrapper, 'preview-fixed-print')
    await clickButtonByTest(wrapper, 'create-fixed-print-task')

    expect(documentPlatformApiMock.printPreviews.previewObject).not.toHaveBeenCalled()
    expect(documentPlatformApiMock.printTasks.create).not.toHaveBeenCalled()
  })

  it('行级紧凑入口只直显固定打印，预览和生成 PDF 留在弹出承载内', async () => {
    const wrapper = mountWithAuth({
      objectType: 'PRODUCTION_MATERIAL_ISSUE',
      objectId: 301,
      objectNo: 'MI-001',
      objectStatus: 'POSTED',
      allowedObjectStatuses: ['POSTED'],
      title: '生产领料固定打印',
      variant: 'compact',
    })
    await flushPromises()

    expect(wrapper.find('[data-test="open-fixed-print-action"]').text()).toBe('固定打印')
    expect(wrapper.find('.platform-panel').exists()).toBe(false)
    await openCompactPrint(wrapper)
    expect(teleportedButtonByTest('preview-fixed-print').textContent).toContain('预览')
    expect(teleportedButtonByTest('create-fixed-print-task').textContent).toContain('生成 PDF')
    expect(teleportedButtonByTest('create-fixed-print-task').disabled).toBe(true)

    teleportedButtonByTest('preview-fixed-print').click()
    await flushPromises()
    teleportedButtonByTest('create-fixed-print-task').click()
    await flushPromises()

    expect(documentPlatformApiMock.printPreviews.previewObject).toHaveBeenCalledWith({
      objectType: 'PRODUCTION_MATERIAL_ISSUE',
      objectId: 301,
      templateCode: 'SALES_ORDER_V1',
    })
    expect(documentPlatformApiMock.printTasks.create).toHaveBeenCalledWith({
      objectType: 'PRODUCTION_MATERIAL_ISSUE',
      objectId: 301,
      templateCode: 'SALES_ORDER_V1',
      idempotencyKey: expect.stringContaining('fixed-print-'),
    })
  })

  it('页面级固定打印入口透传调用方 class 且不产生多根属性告警', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const wrapper = mountWithAuthAndAttrs({
      objectType: 'SALES_ORDER',
      objectId: 88,
      objectNo: 'SO-001',
      title: '固定打印',
    }, { class: 'section-block' })
    await flushPromises()

    expect(wrapper.find('.fixed-print-action.section-block').exists()).toBe(true)
    expect(warnSpy.mock.calls.some((call) => String(call[0]).includes('Extraneous non-props attributes'))).toBe(false)
    warnSpy.mockRestore()
  })
})
