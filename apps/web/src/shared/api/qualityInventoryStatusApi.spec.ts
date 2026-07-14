import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createQualityInventoryStatusApi,
  type QualityInspectionDetail,
  type QualityInspectionRecord,
  type QualityInspectionProcessPayload,
  type QualityStatusTransferPayload,
} from './qualityInventoryStatusApi'
import type { InventoryTrackingAllocationPayload } from './inventoryApi'

type AssertTrue<T extends true> = T
type IsOptional<T, K extends keyof T> = Record<string, never> extends Pick<T, K> ? true : false

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
  } as Response
}

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'BUSINESS_PERIOD_LOCKED',
      message: '业务日期 2026-07-10 所属期间 2026-07 已锁定',
      data: null,
      traceId: 'trace-quality',
    }),
  } as Response
}

function inspectionRecord(patch: Record<string, unknown> = {}) {
  return {
    id: 9,
    inspectionNo: 'QI202607100001',
    sourceType: 'PURCHASE_RECEIPT',
    sourceTypeName: '采购入库',
    sourceId: 20,
    sourceLineId: 201,
    sourceDocumentNo: 'RC202607100001',
    warehouseId: 1,
    warehouseCode: 'RAW',
    warehouseName: '原料仓',
    materialId: 11,
    materialCode: 'RM-001',
    materialName: '冷轧钢板',
    materialSpec: '1.2mm',
    unitId: 3,
    unitName: '千克',
    inspectionQuantity: '10.000000',
    remainingQuantity: '10.000000',
    qualifiedQuantity: '0.000000',
    rejectedQuantity: '0.000000',
    frozenQuantity: '0.000000',
    status: 'PENDING',
    statusName: '待处理',
    businessDate: '2026-07-10',
    createdByName: '管理员',
    createdAt: '2026-07-10T10:00:00+08:00',
    completedByName: null,
    completedAt: null,
    reason: null,
    remark: null,
    version: 1,
    canProcess: true,
    disabledReason: null,
    ...patch,
  }
}

describe('质量库存状态 API', () => {
  const qualityTrackingTypeContract = {
    processAcceptsTrackingAllocations: true as AssertTrue<
      QualityInspectionProcessPayload extends {
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
    transferAcceptsTrackingAllocations: true as AssertTrue<
      QualityStatusTransferPayload extends {
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
    transferRequiresOwnershipDimension: true as AssertTrue<
      QualityStatusTransferPayload extends {
        ownershipType: 'PUBLIC' | 'PROJECT'
        projectId?: unknown
        costLayerId?: unknown
      } ? IsOptional<QualityStatusTransferPayload, 'ownershipType'> extends false ? true : false : false
    >,
    inspectionRecordReturnsTrackingAllocations: true as AssertTrue<
      QualityInspectionRecord extends {
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
    inspectionDetailReturnsTrackingAllocations: true as AssertTrue<
      QualityInspectionDetail extends {
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
  }

  it('质量确认和冻结解冻载荷支持追踪分配', () => {
    expect(qualityTrackingTypeContract).toMatchObject({
      processAcceptsTrackingAllocations: true,
      transferAcceptsTrackingAllocations: true,
      transferRequiresOwnershipDimension: true,
      inspectionRecordReturnsTrackingAllocations: true,
      inspectionDetailReturnsTrackingAllocations: true,
    })
  })

  it('按质量确认筛选条件分页查询列表并获取详情', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ items: [inspectionRecord()], page: 1, pageSize: 20, total: 1 }))
      .mockResolvedValueOnce(apiResponse({
        ...inspectionRecord(),
        sourceSummary: { sourceDocumentNo: 'RC202607100001' },
        currentQualityStatus: 'PENDING_INSPECTION',
        currentQualityStatusName: '待检',
        auditRecords: [],
      }))
    const api = createQualityInventoryStatusApi({ fetcher })

    await api.inspections.list({
      keyword: 'RM-001',
      sourceType: 'PURCHASE_RECEIPT',
      status: 'PENDING',
      qualityStatus: 'PENDING_INSPECTION',
      warehouseId: 1,
      materialId: 11,
      businessDateFrom: '2026-07-01',
      businessDateTo: '2026-07-10',
      page: 1,
      pageSize: 20,
    })
    await api.inspections.get(9)

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/quality/inspections?keyword=RM-001&sourceType=PURCHASE_RECEIPT&status=PENDING&warehouseId=1&materialId=11&businessDateFrom=2026-07-01&businessDateTo=2026-07-10&qualityStatus=PENDING_INSPECTION&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/quality/inspections/9', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })

  it('处理质量确认前获取 CSRF 并保持字符串数量请求体', async () => {
    const calls: Array<{ input: string; init: RequestInit }> = []
    const fetcher = vi.fn(async (input: string, init: RequestInit) => {
      calls.push({ input, init })
      if (input.endsWith('/api/auth/csrf')) {
        return apiResponse({ token: 'csrf', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' })
      }
      return apiResponse(inspectionRecord({ status: 'COMPLETED', statusName: '已处理' }))
    })
    const api = createQualityInventoryStatusApi({ fetcher })
    const payload: QualityInspectionProcessPayload = {
      businessDate: '2026-07-10',
      qualifiedQuantity: '8.000000',
      rejectedQuantity: '1.000000',
      frozenQuantity: '1.000000',
      reason: '检验完成',
      remark: '外观轻微瑕疵',
      trackingAllocations: [
        { batchId: 31, quantity: '8.000000', qualityStatus: 'QUALIFIED' },
        { batchId: 31, quantity: '1.000000', qualityStatus: 'REJECTED' },
        { batchId: 31, quantity: '1.000000', qualityStatus: 'FROZEN' },
      ],
    }

    await api.inspections.process(9, payload)

    expect(calls[1].input).toBe('/api/admin/quality/inspections/9/process')
    expect(calls[1].init.method).toBe('POST')
    expect(calls[1].init.headers).toMatchObject({
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': 'csrf',
    })
    expect(JSON.parse(calls[1].init.body as string)).toEqual(payload)
  })

  it('冻结和解冻质量状态库存使用对应接口和字符串数量', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(apiResponse({ token: 'csrf', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
    fetcher
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-freeze', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ transferNo: 'QT-FREEZE-1' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-unfreeze', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ transferNo: 'QT-UNFREEZE-1' }))
    const api = createQualityInventoryStatusApi({ fetcher })

    await api.qualityTransfers.freeze({
      businessDate: '2026-07-10',
      warehouseId: 1,
      materialId: 11,
      unitId: 3,
      quantity: '2.000000',
      ownershipType: 'PROJECT',
      projectId: 501,
      costLayerId: 9001,
      reason: '客户投诉隔离',
      trackingAllocations: [{ batchId: 31, quantity: '2.000000' }],
    })
    await api.qualityTransfers.unfreeze({
      businessDate: '2026-07-11',
      warehouseId: 1,
      materialId: 11,
      unitId: 3,
      quantity: '1.000000',
      ownershipType: 'PROJECT',
      projectId: 501,
      costLayerId: 9001,
      reason: '复核通过',
      trackingAllocations: [{ batchId: 31, quantity: '1.000000' }],
    })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/inventory/quality-transfers/freeze', expect.objectContaining({
      body: expect.stringContaining('"quantity":"2.000000"'),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/inventory/quality-transfers/freeze', expect.objectContaining({
      body: expect.stringContaining('"ownershipType":"PROJECT"'),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/inventory/quality-transfers/freeze', expect.objectContaining({
      body: expect.stringContaining('"costLayerId":9001'),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/inventory/quality-transfers/freeze', expect.objectContaining({
      body: expect.stringContaining('"trackingAllocations"'),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/inventory/quality-transfers/unfreeze', expect.objectContaining({
      body: expect.stringContaining('"quantity":"1.000000"'),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/inventory/quality-transfers/unfreeze', expect.objectContaining({
      body: expect.stringContaining('"ownershipType":"PROJECT"'),
    }))
  })

  it('后端错误信封抛出统一 API 错误并保留 traceId', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createQualityInventoryStatusApi({ fetcher })
    const request = api.inspections.list({ page: 1, pageSize: 20 })

    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
    await expect(request).rejects.toMatchObject({
      code: 'BUSINESS_PERIOD_LOCKED',
      status: 409,
      traceId: 'trace-quality',
    })
  })
})
