import ElementPlus from 'element-plus'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import ProductionWorkOrderActionBar from './ProductionWorkOrderActionBar.vue'

function mountActionBar(props: Partial<InstanceType<typeof ProductionWorkOrderActionBar>['$props']> = {}) {
  return mount(ProductionWorkOrderActionBar, {
    props: {
      actionLoading: false,
      canEdit: true,
      canRelease: false,
      canCreateIssue: true,
      canCreateReport: true,
      canCreateReceipt: true,
      canComplete: true,
      canCancel: true,
      ...props,
    },
    global: {
      plugins: [ElementPlus],
    },
    attachTo: document.body,
  })
}

async function openMore(wrapper: VueWrapper) {
  await wrapper.find('[data-test="production-work-order-action-more"]').trigger('click')
  await flushPromises()
}

function teleportedMoreItems(): HTMLElement[] {
  return Array.from(document.body.querySelectorAll<HTMLElement>('[data-action-test^="production-work-order-more-action-"]'))
}

function teleportedMoreItem(action: string): HTMLElement {
  const item = document.body.querySelector<HTMLElement>(`[data-action-test="production-work-order-more-action-${action}"]`)
  expect(item).not.toBeNull()
  return item!
}

describe('生产工单详情标题动作栏', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('只直显返回列表和当前主流程动作，其余动作进入更多并保留顺序', () => {
    const wrapper = mountActionBar()

    const directActions = wrapper.findAll('[data-action-test^="production-work-order-direct-action-"]')
    expect(directActions.map((item) => item.text().trim())).toEqual(['返回列表', '领料'])
    expect(wrapper.find('[data-test="production-work-order-action-more"]').exists()).toBe(true)
  })

  it('更多菜单保留非直显动作顺序', async () => {
    const wrapper = mountActionBar()

    await openMore(wrapper)

    expect(teleportedMoreItems().map((item) => item.textContent?.trim())).toEqual([
      '编辑',
      '报工',
      '完工入库',
      '完成',
      '取消',
    ])
  })

  it('更多菜单动作继续触发原事件且加载态禁用状态不变', async () => {
    const releaseOnly = mountActionBar({
      canEdit: true,
      canRelease: true,
      canCreateIssue: false,
      canCreateReport: false,
      canCreateReceipt: false,
      canComplete: false,
      canCancel: true,
    })
    expect(releaseOnly.findAll('[data-action-test^="production-work-order-direct-action-"]').map((item) => item.text().trim())).toEqual([
      '返回列表',
      '发布',
    ])
    await openMore(releaseOnly)
    expect(teleportedMoreItem('cancel').textContent?.trim()).toBe('取消')
    releaseOnly.unmount()
    document.body.innerHTML = ''

    const wrapper = mountActionBar()
    await openMore(wrapper)
    teleportedMoreItem('createReport').click()
    teleportedMoreItem('createReceipt').click()
    teleportedMoreItem('complete').click()
    teleportedMoreItem('cancel').click()
    await flushPromises()

    expect(wrapper.emitted('createReport')).toHaveLength(1)
    expect(wrapper.emitted('createReceipt')).toHaveLength(1)
    expect(wrapper.emitted('complete')).toHaveLength(1)
    expect(wrapper.emitted('cancel')).toHaveLength(1)

    wrapper.unmount()
    document.body.innerHTML = ''
    const loadingWrapper = mountActionBar({ actionLoading: true })
    await openMore(loadingWrapper)
    expect(teleportedMoreItem('complete').getAttribute('aria-disabled')).toBe('true')
    expect(teleportedMoreItem('cancel').getAttribute('aria-disabled')).toBe('true')
  })
})
