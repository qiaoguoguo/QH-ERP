import { describe, expect, it, vi } from 'vitest'
import { createHttpClient } from './httpClient'

describe('HTTP 客户端骨架', () => {
  it('使用基础地址发起请求并解析 JSON 响应', async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: 'UP' }),
    })
    const client = createHttpClient({ baseUrl: 'http://localhost:8080', fetcher })

    await expect(client.get('/api/health')).resolves.toEqual({ status: 'UP' })
    expect(fetcher).toHaveBeenCalledWith('http://localhost:8080/api/health', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })
})
