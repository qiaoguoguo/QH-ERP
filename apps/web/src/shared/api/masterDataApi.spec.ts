import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import { createMasterDataApi } from './masterDataApi'

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

function apiFailure(status = 400) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'MASTER_DATA_CODE_EXISTS',
      message: '编码已存在',
      data: null,
      traceId: 'trace-1',
    }),
  }
}

describe('基础资料与物料 API', () => {
  it('创建单位前先请求 CSRF，再提交业务字段', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 1, code: 'PCS', name: '件' }))
    const api = createMasterDataApi({ fetcher })

    await api.units.create({
      code: 'PCS',
      name: '件',
      precisionScale: 0,
      sortOrder: 10,
      status: 'ENABLED',
    })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/master/units', {
      body: JSON.stringify({
        code: 'PCS',
        name: '件',
        precisionScale: 0,
        sortOrder: 10,
        status: 'ENABLED',
      }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'POST',
    })
  })

  it('按查询条件分页获取单位列表', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 2, pageSize: 20 }))
    const api = createMasterDataApi({ fetcher })

    await api.units.list({ keyword: '件', status: 'ENABLED', page: 2, pageSize: 20 })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/master/units?keyword=%E4%BB%B6&status=ENABLED&page=2&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('列表查询只发送后端支持的筛选字段', async () => {
    const fetcher = vi.fn().mockResolvedValue(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createMasterDataApi({ fetcher })

    await api.categories.list({
      keyword: '钢',
      status: 'ENABLED',
      page: 1,
      pageSize: 20,
      parentId: 7,
    } as never)
    await api.materials.list({
      keyword: '板',
      status: 'ENABLED',
      materialType: 'RAW_MATERIAL',
      sourceType: 'PURCHASED',
      categoryId: 3,
      unitId: 1,
      page: 1,
      pageSize: 20,
    } as never)

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/master/material-categories?keyword=%E9%92%A2&status=ENABLED&page=1&pageSize=20',
      expect.any(Object),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/master/materials?keyword=%E6%9D%BF&status=ENABLED&page=1&pageSize=20&materialType=RAW_MATERIAL&sourceType=PURCHASED&categoryId=3',
      expect.any(Object),
    )
  })

  it('更新物料时提交物料核心业务字段', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 9 }))
    const api = createMasterDataApi({ fetcher })

    await api.materials.update(9, {
      code: 'MAT-001',
      name: '冷轧钢板',
      materialType: 'RAW_MATERIAL',
      sourceType: 'PURCHASED',
      categoryId: 3,
      unitId: 1,
      status: 'ENABLED',
    })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/master/materials/9', {
      body: JSON.stringify({
        code: 'MAT-001',
        name: '冷轧钢板',
        materialType: 'RAW_MATERIAL',
        sourceType: 'PURCHASED',
        categoryId: 3,
        unitId: 1,
        status: 'ENABLED',
      }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'PUT',
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
    const api = createMasterDataApi({ fetcher })

    await api.units.enable(1)
    await api.units.disable(1)

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/master/units/1/enable', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/master/units/1/disable', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'PUT',
    })
  })

  it('后端返回非成功 envelope 时抛出账号权限 API 错误并保留状态', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure(409))
    const api = createMasterDataApi({ fetcher })
    const request = api.units.list({ page: 1, pageSize: 10 })

    await expect(request).rejects.toMatchObject({
      code: 'MASTER_DATA_CODE_EXISTS',
      status: 409,
      traceId: 'trace-1',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
