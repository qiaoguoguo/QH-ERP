import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'
import AiAssistantView from './AiAssistantView.vue'
import { aiAssistantApi } from '../../shared/api/aiAssistantApi'
import { knowledgeBaseApi } from '../../shared/api/knowledgeBaseApi'

vi.mock('../../shared/api/aiAssistantApi', () => ({
  AiAssistantApiError: class extends Error {},
  aiAssistantApi: { status: vi.fn(), ask: vi.fn() },
}))

vi.mock('../../shared/api/knowledgeBaseApi', () => ({
  knowledgeBaseApi: { help: { byRoute: vi.fn() } },
}))

const ElementStubs = {
  ElButton: { props: ['disabled', 'loading'], template: '<button type="button" v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  ElInput: { props: ['modelValue'], emits: ['update:modelValue'], template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  ElAlert: { props: ['title'], template: '<p>{{ title }}</p>' },
}

async function mountView() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/ai-assistant', name: 'ai-assistant', component: AiAssistantView },
      { path: '/help/articles/:id', name: 'help-article', component: { template: '<div />' } },
    ],
  })
  await router.push('/ai-assistant?from=/procurement/orders/1')
  await router.isReady()
  const wrapper = mount(AiAssistantView, { global: { plugins: [router], stubs: ElementStubs } })
  await flushPromises()
  return wrapper
}

describe('AiAssistantView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.mocked(aiAssistantApi.status).mockResolvedValue({
      modelConfigured: true,
      provider: 'MiniMax',
      model: 'MiniMax-M3',
      currentMode: 'AI知识问答',
      privacyNotice: '请勿输入敏感信息',
    })
    vi.mocked(aiAssistantApi.ask).mockResolvedValue({
      answer: '进入采购订单详情后确认。',
      mode: 'MINIMAX',
      model: 'MiniMax-M3',
      generatedAt: '2026-08-18T10:00:00+08:00',
      sources: [{ type: 'MANUAL', title: '采购订单确认', summary: '确认操作说明', articleId: 1, routePath: '/procurement/orders/:id' }],
    })
    vi.mocked(knowledgeBaseApi.help.byRoute).mockResolvedValue({
      items: [{ pageNames: '采购订单' }],
      total: 1,
      page: 1,
      pageSize: 1,
      totalPages: 1,
    } as never)
  })

  it('展示服务状态并使用页面上下文提问', async () => {
    const wrapper = await mountView()
    expect(wrapper.text()).toContain('AI知识问答')
    expect(wrapper.text()).toContain('采购订单')

    await wrapper.find('[data-test="assistant-suggestion"]').trigger('click')
    await flushPromises()

    expect(aiAssistantApi.ask).toHaveBeenCalledWith(expect.objectContaining({
      routePath: '/procurement/orders/1',
      pageName: '采购订单',
    }))
    expect(wrapper.text()).toContain('进入采购订单详情后确认。')
    expect(wrapper.text()).toContain('MiniMax 回答')
  })
})
