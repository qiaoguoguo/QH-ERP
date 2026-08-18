import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { knowledgeBaseApi } from './knowledgeBaseApi'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function ok<T>(data: T) {
  return jsonResponse({ success: true, code: 'OK', message: '成功', data })
}

describe('knowledgeBaseApi', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('帮助中心搜索使用items分页契约并正确传递查询参数', async () => {
    const fetcher = vi.mocked(fetch)
    fetcher.mockResolvedValueOnce(ok({ items: [{ id: 1, title: '采购订单帮助' }], total: 1, page: 2, pageSize: 20, totalPages: 1 }))

    const page = await knowledgeBaseApi.help.search({ keyword: '采购', categoryId: 3, knowledgeType: 'PAGE', page: 2, pageSize: 20 })

    expect(page.items).toHaveLength(1)
    expect(page.total).toBe(1)
    expect(fetcher).toHaveBeenCalledWith(
      '/api/help/articles?keyword=%E9%87%87%E8%B4%AD&categoryId=3&knowledgeType=PAGE&page=2&pageSize=20',
      expect.objectContaining({ method: 'GET', credentials: 'include' })
    )
  })

  it('按路由查询时发送规范化routePath和分页参数', async () => {
    const fetcher = vi.mocked(fetch)
    fetcher.mockResolvedValueOnce(ok({ items: [], total: 0, page: 2, pageSize: 20, totalPages: 0 }))

    await knowledgeBaseApi.help.byRoute('/procurement/orders/:id', { page: 2, pageSize: 20 })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/help/articles/by-route?routePath=%2Fprocurement%2Forders%2F%3Aid&page=2&pageSize=20',
      expect.objectContaining({ method: 'GET', credentials: 'include' })
    )
  })

  it('管理写接口先获取CSRF再提交，并保留后端错误信息', async () => {
    const fetcher = vi.mocked(fetch)
    fetcher
      .mockResolvedValueOnce(ok({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(ok({ id: 1, slug: 'procurement-order-confirm' }))
      .mockResolvedValueOnce(ok({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(ok(null))
      .mockResolvedValueOnce(ok({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(jsonResponse({ success: false, code: 'KNOWLEDGE_CATEGORY_IN_USE', message: '分类已被文章占用', data: null }, 409))

    await knowledgeBaseApi.admin.createArticle({
      slug: 'procurement-order-confirm',
      title: '采购订单确认',
      summary: '采购订单确认说明',
      categoryId: 1,
      knowledgeType: 'PAGE',
      content: '# 采购订单确认',
      relatedArticleIds: [],
      sortOrder: 100,
      status: 'ENABLED',
    })
    await knowledgeBaseApi.admin.disableArticle(1)
    await expect(knowledgeBaseApi.admin.deleteCategory(1)).rejects.toThrow('分类已被文章占用')

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/auth/csrf', expect.objectContaining({ method: 'GET', credentials: 'include' }))
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/system/knowledge/articles',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-token', 'Content-Type': 'application/json' }),
      })
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      4,
      '/api/admin/system/knowledge/articles/1/disable',
      expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-token' }) })
    )
  })
})
