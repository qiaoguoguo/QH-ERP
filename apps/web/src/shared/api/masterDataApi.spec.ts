import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createMasterDataApi,
  type CodingRulePayload,
  type SettlementTaxRecord,
  type UnitConversionPayload,
  type MaterialPayload,
  type MaterialRecord,
  type MaterialTrackingMethod,
} from './masterDataApi'

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
  const materialTrackingTypeContract = {
    recordRequiresTrackingMethod: true as AssertTrue<
      MaterialRecord extends { trackingMethod: MaterialTrackingMethod; trackingMethodName: string } ? true : false
    >,
    payloadRequiresTrackingMethod: true as AssertTrue<
      MaterialPayload extends { trackingMethod: MaterialTrackingMethod } ? true : false
    >,
  }

  it('物料响应和写入载荷按契约必带追踪方式', () => {
    expect(materialTrackingTypeContract).toMatchObject({
      recordRequiresTrackingMethod: true,
      payloadRequiresTrackingMethod: true,
    })
  })

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
      trackingMethod: 'BATCH',
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
      '/api/admin/master/materials?keyword=%E6%9D%BF&status=ENABLED&page=1&pageSize=20&materialType=RAW_MATERIAL&sourceType=PURCHASED&trackingMethod=BATCH&categoryId=3',
      expect.any(Object),
    )
  })

  it('更新物料时提交物料核心业务字段和追踪方式', async () => {
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
      trackingMethod: 'BATCH',
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
        trackingMethod: 'BATCH',
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

  it('单位换算按冻结契约提交 decimal 字符串、候选分页和 version 状态动作', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ items: [], selectedItems: [], total: 0, page: 1, pageSize: 20, totalPages: 0 }))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 12, version: 4 }))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 12, status: 'DISABLED', version: 5 }))
    const api = createMasterDataApi({ fetcher })
    const payload: UnitConversionPayload = {
      materialId: 3,
      businessUnitId: 2,
      conversionRate: '2.5000',
      quantityScale: 4,
      roundingMode: 'HALF_UP',
      effectiveFrom: '2026-07-01',
      effectiveTo: null,
      remark: '采购箱换算',
      version: 4,
    }

    await api.unitConversions.materialCandidates({ keyword: '钢', selectedIds: [3, '7'], page: 1, pageSize: 20 })
    await api.unitConversions.update(12, payload)
    await api.unitConversions.disable(12, { version: 5 })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/master/unit-conversions/material-candidates?keyword=%E9%92%A2&page=1&pageSize=20&selectedIds=3%2C7',
      expect.any(Object),
    )
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/master/unit-conversions/12', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/admin/master/unit-conversions/12/disable', {
      body: JSON.stringify({ version: 5 }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'PUT',
    })
  })

  it('编码规则生成编码只调用后端 generate 接口并保留稳定错误码', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ objectType: 'MATERIAL', ruleId: 8, generatedCode: 'MAT-202607-0001', generatedAt: '2026-07-13T10:00:00+08:00' }))
    const api = createMasterDataApi({ fetcher })
    const payload: CodingRulePayload = {
      ruleCode: 'MAT-RULE',
      name: '物料编码',
      objectType: 'MATERIAL',
      prefix: 'MAT',
      datePattern: 'YYYYMM',
      serialLength: 4,
      resetCycle: 'MONTH',
      nextSerialNo: 1,
      status: 'ENABLED',
      remark: null,
    }

    await api.codingRules.generate({ objectType: payload.objectType, contextDate: '2026-07-13' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/coding-rules/generate', {
      body: JSON.stringify({ objectType: 'MATERIAL', contextDate: '2026-07-13' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'POST',
    })
  })

  it('物料更新和状态动作提交 version 与成本属性字段', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 9, version: 8 }))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 9, status: 'ENABLED', version: 9 }))
    const api = createMasterDataApi({ fetcher })

    await api.materials.update(9, {
      code: 'MAT-001',
      name: '冷轧钢板',
      materialType: 'RAW_MATERIAL',
      sourceType: 'PURCHASED',
      trackingMethod: 'BATCH',
      categoryId: 3,
      unitId: 1,
      costCategory: 'DIRECT_MATERIAL',
      inventoryValuationCategory: 'VALUATED_MATERIAL',
      inventoryValueEnabled: true,
      projectCostEnabled: true,
      costRemark: '项目材料',
      status: 'ENABLED',
      version: 7,
    })
    await api.materials.enable(9, { version: 8 })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/master/materials/9', expect.objectContaining({
      body: expect.stringContaining('"version":7'),
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/master/materials/9/enable', expect.objectContaining({
      body: JSON.stringify({ version: 8 }),
      method: 'PUT',
    }))
  })

  it('客户结算税务资料使用独立接口，受限响应完整敏感字段为 null', async () => {
    const restrictedRecord: SettlementTaxRecord = {
      ownerType: 'CUSTOMER',
      ownerId: 1,
      hasData: true,
      sensitiveRestricted: true,
      restrictedMessage: '无敏感查看权限',
      invoiceTitle: '华南设备客户',
      taxNo: null,
      taxNoMasked: '9144********1234',
      registeredAddress: null,
      registeredPhone: null,
      bankName: null,
      bankAccount: null,
      bankAccountMasked: '6222********8899',
      defaultTaxRate: '0.1300',
      invoiceType: 'SPECIAL_VAT',
      settlementMethod: 'MONTHLY',
      paymentTermDays: 30,
      paymentTerms: '月结30天',
      remark: null,
      version: 6,
    }
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse(restrictedRecord))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ ...restrictedRecord, paymentTermDays: 45, version: 7 }))
    const api = createMasterDataApi({ fetcher })

    const record = await api.customers.getSettlementTax(1)
    await api.customers.updateSettlementTax(1, { paymentTermDays: 45, version: record.version })

    expect(record.taxNo).toBeNull()
    expect(record.bankAccount).toBeNull()
    expect(record.taxNoMasked).toBe('9144********1234')
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/master/customers/1/settlement-tax', {
      body: JSON.stringify({ paymentTermDays: 45, version: 6 }),
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
