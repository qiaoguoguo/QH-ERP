import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDocumentTaskPolling } from './useDocumentTaskPolling'
import { useAuthStore } from '../../stores/authStore'

describe('文档任务轮询 composable', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    useAuthStore().setSession({
      user: { id: 1, username: 'admin', displayName: '管理员', status: 'ENABLED' },
      menus: [],
      permissions: ['platform:document-task:view'],
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('按有界 setTimeout 轮询，任务终态和组件卸载时停止', async () => {
    const loader = vi
      .fn()
      .mockResolvedValueOnce({ status: 'RUNNING' })
      .mockResolvedValueOnce({ status: 'SUCCEEDED' })
    const wrapper = mount(defineComponent({
      setup() {
        const taskId = ref(91)
        const polling = useDocumentTaskPolling(taskId, loader, { intervalMs: 1000 })
        polling.start()
        return () => h('span', polling.running.value ? '运行中' : '已停止')
      },
    }))

    await nextTick()
    await vi.runOnlyPendingTimersAsync()
    expect(loader).toHaveBeenCalledTimes(1)
    await vi.runOnlyPendingTimersAsync()
    expect(loader).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('已停止')

    wrapper.unmount()
    await vi.runOnlyPendingTimersAsync()
    expect(loader).toHaveBeenCalledTimes(2)
  })

  it('切换账号时停止旧轮询，避免跨用户继续拉取任务', async () => {
    const loader = vi.fn().mockResolvedValue({ status: 'RUNNING' })
    mount(defineComponent({
      setup() {
        const taskId = ref(91)
        const polling = useDocumentTaskPolling(taskId, loader, { intervalMs: 1000 })
        polling.start()
        return () => h('span')
      },
    }))
    await nextTick()
    await vi.runOnlyPendingTimersAsync()
    expect(loader).toHaveBeenCalledTimes(1)

    useAuthStore().setSession({
      user: { id: 2, username: 'other', displayName: '其他用户', status: 'ENABLED' },
      menus: [],
      permissions: ['platform:document-task:view'],
    })
    await nextTick()
    await vi.runOnlyPendingTimersAsync()

    expect(loader).toHaveBeenCalledTimes(1)
  })
})
