import { describe, expect, it } from 'vitest'
import { knowledgeRoutes } from './knowledgeRoutes'

describe('AI助手路由', () => {
  it('对所有已登录用户开放独立只读咨询页面', () => {
    const route = knowledgeRoutes.find((item) => item.name === 'ai-assistant')
    expect(route).toMatchObject({
      path: '/ai-assistant',
      meta: { requiresAuth: true },
    })
    expect(route?.meta).not.toHaveProperty('requiredPermission')
  })
})
