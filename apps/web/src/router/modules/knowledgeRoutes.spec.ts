import { describe, expect, it } from 'vitest'
import { knowledgeRoutes } from './knowledgeRoutes'

describe('knowledgeRoutes', () => {
  function route(name: string) {
    const found = knowledgeRoutes.find((item) => item.name === name)
    expect(found, `缺少路由 ${name}`).toBeTruthy()
    return found!
  }

  it('帮助中心和文章详情只要求登录，不要求知识库管理权限', () => {
    expect(route('help-center').meta?.requiresAuth).toBe(true)
    expect(route('help-center').meta?.requiredPermission).toBeUndefined()
    expect(route('help-article').meta?.requiresAuth).toBe(true)
    expect(route('help-article').meta?.requiredPermission).toBeUndefined()
  })

  it('知识库管理入口和编辑入口要求system:knowledge:manage权限', () => {
    expect(route('system-knowledge').meta?.requiredPermission).toBe('system:knowledge:manage')
    expect(route('system-knowledge-create').meta?.requiredPermission).toBe('system:knowledge:manage')
    expect(route('system-knowledge-edit').meta?.requiredPermission).toBe('system:knowledge:manage')
  })
})
