import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import App from './App.vue'
import { router } from './router'

describe('ERP 应用骨架', () => {
  it('展示制造业 ERP 的基础布局和导航入口', async () => {
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.text()).toContain('QH ERP')
    expect(wrapper.text()).toContain('账号权限')
    expect(wrapper.text()).toContain('物料管理')
    expect(wrapper.text()).toContain('生产管理')
    expect(wrapper.text()).toContain('工程骨架已就绪')
  })
})
