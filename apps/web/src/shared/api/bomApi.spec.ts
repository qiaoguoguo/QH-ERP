import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import { createBomApi } from './bomApi'

function apiResponse<T>(data: T, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => ({
      success: true,
      code: 'OK',
      message: '成功',
      data,
    }),
  }
}

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'BOM_CODE_EXISTS',
      message: 'BOM 编码已存在',
      data: null,
      traceId: 'trace-bom',
    }),
  }
}

describe('BOM API', () => {
  it('按查询条件分页获取 BOM 列表', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 2, pageSize: 20 }))
    const api = createBomApi({ fetcher })

    await api.list({ keyword: '成品A', status: 'DRAFT', parentMaterialId: 3, page: 2, pageSize: 20 })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/boms?keyword=%E6%88%90%E5%93%81A&status=DRAFT&parentMaterialId=3&page=2&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('创建 BOM 前先请求 CSRF，再提交主表和明细字段', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 1, bomCode: 'BOM-FG-001' }))
    const api = createBomApi({ fetcher })
    const payload = {
      bomCode: 'BOM-FG-001',
      parentMaterialId: 11,
      versionCode: 'V1.0',
      name: '成品A标准 BOM',
      baseQuantity: 1,
      baseUnitId: 1,
      items: [{ lineNo: 10, childMaterialId: 21, unitId: 2, quantity: 2.5, lossRate: 0.02 }],
    }

    await api.create(payload)

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/boms', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'POST',
    })
  })

  it('更新和复制 BOM 使用对应业务路径', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 9 }))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 10 }))
    const api = createBomApi({ fetcher })
    const payload = {
      bomCode: 'BOM-FG-001',
      parentMaterialId: 11,
      versionCode: 'V1.0',
      name: '成品A标准 BOM',
      baseQuantity: 1,
      items: [{ lineNo: 10, childMaterialId: 21, quantity: 2.5 }],
    }

    await api.update(9, payload)
    await api.copy(9, { bomCode: 'BOM-FG-001-V2', versionCode: 'V2.0', name: '成品A标准 BOM V2.0' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/boms/9', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/boms/9/copy', {
      body: JSON.stringify({ bomCode: 'BOM-FG-001-V2', versionCode: 'V2.0', name: '成品A标准 BOM V2.0' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'POST',
    })
  })

  it('启用和停用接口不发送空 JSON body', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 1, status: 'ENABLED' }))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 1, status: 'DISABLED' }))
    const api = createBomApi({ fetcher })

    await api.enable(1)
    await api.disable(1)

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/boms/1/enable', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/boms/1/disable', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'PUT',
    })
  })

  it('后端返回非成功 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createBomApi({ fetcher })
    const request = api.list({ page: 1, pageSize: 20 })

    await expect(request).rejects.toMatchObject({
      code: 'BOM_CODE_EXISTS',
      status: 409,
      traceId: 'trace-bom',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
