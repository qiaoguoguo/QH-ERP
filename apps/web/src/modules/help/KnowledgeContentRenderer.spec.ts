import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import KnowledgeContentRenderer from './KnowledgeContentRenderer.vue'

const ElementStubs = {
  ElEmpty: { props: ['description'], template: '<p class="empty">{{ description }}</p>' },
}

describe('KnowledgeContentRenderer', () => {
  it('渲染受控Markdown块，不使用v-html执行HTML或脚本', () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => undefined)
    const wrapper = mount(KnowledgeContentRenderer, {
      props: {
        content: '# 标题\n<script>alert("xss")</script>\n- <img src=x onerror=alert(1)>\n1. 步骤',
      },
      global: { stubs: ElementStubs },
    })

    expect(wrapper.find('h2').text()).toBe('标题')
    expect(wrapper.text()).toContain('<script>alert("xss")</script>')
    expect(wrapper.html()).not.toContain('<script>alert')
    expect(wrapper.html()).not.toContain('<img src')
    expect(alertSpy).not.toHaveBeenCalled()
    alertSpy.mockRestore()
  })

  it('空正文展示空态', () => {
    const wrapper = mount(KnowledgeContentRenderer, { props: { content: '' }, global: { stubs: ElementStubs } })
    expect(wrapper.text()).toContain('暂无正文内容')
  })
})
