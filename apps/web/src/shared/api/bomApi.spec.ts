import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createBomApi,
  type BomDetailRecord,
  type BomHistoryRelationRecord,
  type BomHistoryRelationType,
  type QuantityBasis,
} from './bomApi'

type AssertTrue<T extends true> = T

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
  const quantityBasisTypeContract = {
    convertedBusinessUnit: true as AssertTrue<'CONVERTED_BUSINESS_UNIT' extends QuantityBasis ? true : false>,
    legacyBusinessUnit: true as AssertTrue<'LEGACY_BUSINESS_UNIT' extends QuantityBasis ? true : false>,
    oldBusinessUnitRemoved: true as AssertTrue<'BUSINESS_UNIT' extends QuantityBasis ? false : true>,
  }
  const historyRelationTypeContract = {
    source: true as AssertTrue<'SOURCE' extends BomHistoryRelationType ? true : false>,
    target: true as AssertTrue<'TARGET' extends BomHistoryRelationType ? true : false>,
  }
  const historyRelationContract: BomDetailRecord['historyRelations'][number] = {
    ecoId: 100,
    ecoNo: 'ECO-202607-0001',
    relationType: 'SOURCE',
    sourceBomId: 2,
    sourceBomCode: 'BOM-FG-A',
    sourceVersionCode: 'V1.0',
    targetBomId: 1,
    targetBomCode: 'BOM-FG-A-V2',
    targetVersionCode: 'V2.0',
    status: 'APPLIED',
    effectiveFrom: '2026-07-13',
    effectiveTo: null,
    appliedBy: 'admin',
    appliedAt: '2026-07-13T10:00:00+08:00',
  } satisfies BomHistoryRelationRecord

  it('BOM 明细数量口径枚举与冻结契约一致', () => {
    expect(quantityBasisTypeContract).toEqual({
      convertedBusinessUnit: true,
      legacyBusinessUnit: true,
      oldBusinessUnitRemoved: true,
    })
  })

  it('BOM 明细历史关系字段与冻结契约一致', () => {
    expect(historyRelationTypeContract).toEqual({
      source: true,
      target: true,
    })
    expect(historyRelationContract).toMatchObject({
      ecoNo: 'ECO-202607-0001',
      relationType: 'SOURCE',
      sourceVersionCode: 'V1.0',
      targetVersionCode: 'V2.0',
      status: 'APPLIED',
    })
  })

  it('按查询条件分页获取 BOM 列表', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 2, pageSize: 20 }))
    const api = createBomApi({ fetcher })

    await api.list({
      keyword: '成品A',
      status: 'DRAFT',
      parentMaterialId: 3,
      effectiveDate: '2026-07-13',
      includeHistory: true,
      page: 2,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/boms?keyword=%E6%88%90%E5%93%81A&status=DRAFT&parentMaterialId=3&effectiveDate=2026-07-13&includeHistory=true&page=2&pageSize=20',
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
      baseQuantity: '1.0000',
      baseUnitId: 1,
      items: [{ lineNo: 10, childMaterialId: 21, businessUnitId: 2, businessQuantity: '2.5000', lossRate: '0.0200' }],
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
      baseQuantity: '1.0000',
      version: 7,
      items: [{ lineNo: 10, childMaterialId: 21, businessUnitId: 2, businessQuantity: '2.5000' }],
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

  it('发布和停用接口按 version 提交请求体', async () => {
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

    await api.enable(1, { version: 3 })
    await api.disable(1, { version: 4 })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/boms/1/enable', {
      body: JSON.stringify({ version: 3 }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/boms/1/disable', {
      body: JSON.stringify({ version: 4 }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'PUT',
    })
  })

  it('BOM 候选接口支持 selectedIds 回显和分页搜索', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], selectedItems: [], total: 0, page: 1, pageSize: 20, totalPages: 0 }))
    const api = createBomApi({ fetcher })

    await api.materialCandidates({ keyword: 'FG', selectedIds: [1, '2'], status: 'ENABLED', materialType: 'FINISHED_GOOD', page: 1, pageSize: 20 })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/boms/material-candidates?keyword=FG&page=1&pageSize=20&selectedIds=1%2C2&status=ENABLED&materialType=FINISHED_GOOD',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('ECO 与替代料候选接口透传上下文参数', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(apiResponse({ items: [], selectedItems: [], total: 0, page: 1, pageSize: 20, totalPages: 0 }))
    const api = createBomApi({ fetcher })

    await api.engineeringChanges.sourceBomCandidates({ keyword: 'BOM', parentMaterialId: 1, selectedIds: [2], page: 1, pageSize: 20 })
    await api.engineeringChanges.targetBomCandidates({ keyword: 'V2', sourceBomId: 2, selectedIds: [1], page: 1, pageSize: 20 })
    await api.substitutes.bomCandidates({ keyword: 'BOM', parentMaterialId: 2, selectedIds: [1], page: 1, pageSize: 20 })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/bom-engineering-changes/source-bom-candidates?keyword=BOM&page=1&pageSize=20&selectedIds=2&parentMaterialId=1',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/bom-engineering-changes/target-bom-candidates?keyword=V2&page=1&pageSize=20&selectedIds=1&sourceBomId=2',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      3,
      '/api/admin/material-substitutes/bom-candidates?keyword=BOM&page=1&pageSize=20&selectedIds=1&parentMaterialId=2',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('工程变更创建、应用和取消使用冻结接口与 version 并发字段', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 30, ecoNo: 'ECO-001', status: 'DRAFT', version: 1 }))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 30, status: 'APPLIED', version: 2 }))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 31, status: 'CANCELLED', version: 3 }))
    const api = createBomApi({ fetcher })

    await api.engineeringChanges.create({
      sourceBomId: 1,
      targetBomId: 2,
      effectiveFrom: '2026-07-13',
      effectiveTo: null,
      changeReason: '材料优化',
      impactScope: '后续生产',
      changeSummary: '替换一项原材料',
      remark: null,
    })
    await api.engineeringChanges.apply(30, { version: 1 })
    await api.engineeringChanges.cancel(31, { version: 2, reason: '目标版本作废' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/bom-engineering-changes', expect.objectContaining({
      method: 'POST',
      body: expect.stringContaining('"sourceBomId":1'),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/bom-engineering-changes/30/apply', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ version: 1 }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/bom-engineering-changes/31/cancel', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ version: 2, reason: '目标版本作废' }),
    }))
  })

  it('替代料维护接口提交范围、优先级、decimal 字符串和 version 状态动作', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 40, version: 1 }))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 40, status: 'ENABLED', version: 2 }))
    const api = createBomApi({ fetcher })

    await api.substitutes.create({
      mainMaterialId: 2,
      substituteMaterialId: 3,
      scopeType: 'BOM',
      scopeId: 1,
      priority: 1,
      substituteRate: '1.0000',
      effectiveFrom: '2026-07-13',
      effectiveTo: null,
      status: 'ENABLED',
      remark: '人工识别替代',
    })
    await api.substitutes.enable(40, { version: 1 })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/material-substitutes', expect.objectContaining({
      method: 'POST',
      body: expect.stringContaining('"substituteRate":"1.0000"'),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/material-substitutes/40/enable', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ version: 1 }),
    }))
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
