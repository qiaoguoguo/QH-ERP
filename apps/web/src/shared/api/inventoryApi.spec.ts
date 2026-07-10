import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createInventoryApi,
  type InventoryDocumentPayload,
  type InventoryMovementType,
} from './inventoryApi'

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
      code: 'INVENTORY_STOCK_NOT_ENOUGH',
      message: '库存不足，调减后库存不能小于 0',
      data: null,
      traceId: 'trace-inventory',
    }),
  }
}

describe('库存 API', () => {
  it('按查询条件分页获取库存余额', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 2, pageSize: 50 }))
    const api = createInventoryApi({ fetcher })

    await api.balances.list({
      keyword: '原材料',
      warehouseId: 1,
      materialId: 2,
      materialType: 'RAW_MATERIAL',
      onlyPositive: true,
      page: 2,
      pageSize: 50,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/inventory/balances?keyword=%E5%8E%9F%E6%9D%90%E6%96%99&warehouseId=1&materialId=2&materialType=RAW_MATERIAL&onlyPositive=true&page=2&pageSize=50',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('库存余额查询支持质量状态和零数量质量状态行参数', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createInventoryApi({ fetcher })

    await api.balances.list({
      keyword: '钢板',
      qualityStatus: 'QUALIFIED',
      includeZeroQualityStatuses: true,
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/inventory/balances?keyword=%E9%92%A2%E6%9D%BF&qualityStatus=QUALIFIED&includeZeroQualityStatuses=true&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('按查询条件分页获取库存变动和库存单据', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 3, pageSize: 10 }))
    const api = createInventoryApi({ fetcher })

    await api.movements.list({
      keyword: 'MOV',
      warehouseId: 1,
      materialId: 2,
      movementType: 'ADJUSTMENT_DECREASE',
      direction: 'OUT',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-03',
      page: 1,
      pageSize: 20,
    })
    await api.documents.list({
      keyword: 'INV',
      documentType: 'ADJUSTMENT',
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-03',
      page: 3,
      pageSize: 10,
    })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/inventory/movements?keyword=MOV&warehouseId=1&materialId=2&movementType=ADJUSTMENT_DECREASE&direction=OUT&dateFrom=2026-07-01&dateTo=2026-07-03&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/inventory/documents?keyword=INV&documentType=ADJUSTMENT&status=DRAFT&dateFrom=2026-07-01&dateTo=2026-07-03&page=3&pageSize=10',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('库存变动查询支持销售出库流水类型', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createInventoryApi({ fetcher })
    const salesShipmentType: InventoryMovementType = 'SALES_SHIPMENT'

    await api.movements.list({
      keyword: '销售出库',
      movementType: salesShipmentType,
      direction: 'OUT',
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/inventory/movements?keyword=%E9%94%80%E5%94%AE%E5%87%BA%E5%BA%93&movementType=SALES_SHIPMENT&direction=OUT&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('库存变动查询支持质量状态和来源追溯筛选', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createInventoryApi({ fetcher })

    await api.movements.list({
      qualityStatus: 'PENDING_INSPECTION',
      sourceType: 'QUALITY_INSPECTION',
      sourceId: 9,
      sourceLineId: 90,
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/inventory/movements?qualityStatus=PENDING_INSPECTION&sourceType=QUALITY_INSPECTION&sourceId=9&sourceLineId=90&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('按单据标识获取库存单据详情', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ id: 9, documentNo: 'INV-20260703-001' }))
    const api = createInventoryApi({ fetcher })

    await api.documents.get(9)

    expect(fetcher).toHaveBeenCalledWith('/api/admin/inventory/documents/9', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })

  it('创建、更新和过账库存单据前先获取 CSRF，过账不发送空 JSON body', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 1, documentNo: 'INV-1' }))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-update', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 1, documentNo: 'INV-1' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-post', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 1, status: 'POSTED' }))
    const api = createInventoryApi({ fetcher })
    const payload: InventoryDocumentPayload = {
      documentType: 'OPENING',
      businessDate: '2026-07-03',
      reason: '期初库存',
      remark: '上线期初',
      lines: [{ lineNo: 1, warehouseId: 1, materialId: 2, unitId: 3, quantity: '999999999999.999999' }],
    }

    await api.documents.create(payload)
    await api.documents.update(1, payload)
    await api.documents.post(1)

    expect(JSON.parse((fetcher.mock.calls[1][1].body as string))).toMatchObject({
      lines: [{ quantity: '999999999999.999999' }],
    })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/inventory/documents', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-create',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/inventory/documents/1', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-update',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/inventory/documents/1/post', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-post',
      },
      method: 'PUT',
    })
  })

  it('后端返回非成功 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createInventoryApi({ fetcher })
    const request = api.balances.list({ page: 1, pageSize: 20 })

    await expect(request).rejects.toMatchObject({
      code: 'INVENTORY_STOCK_NOT_ENOUGH',
      status: 409,
      traceId: 'trace-inventory',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
