import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import { createBusinessPeriodApi, type BusinessPeriodPayload } from './businessPeriodApi'

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
      traceId: 'trace-period',
    }),
  } as Response
}

const period = {
  id: 12,
  periodCode: '2026-07',
  periodName: '2026年07月',
  startDate: '2026-07-01',
  endDate: '2026-07-31',
  status: 'OPEN' as const,
  statusName: '开放',
  lockedBy: null,
  lockedAt: null,
  lockReason: null,
  unlockedBy: null,
  unlockedAt: null,
  unlockReason: null,
}

describe('业务期间 API', () => {
  it('按查询条件获取业务期间列表并过滤空查询值', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [period], total: 1, page: 2, pageSize: 20 }))
    const api = createBusinessPeriodApi({ fetcher })

    await api.list({
      periodCode: '2026',
      status: 'LOCKED',
      startDate: '2026-07-01',
      endDate: '',
      page: 2,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/system/business-periods?periodCode=2026&status=LOCKED&startDate=2026-07-01&page=2&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('创建、更新和按月生成业务期间时携带 CSRF 和 JSON 请求体', async () => {
    const fetcher = vi.fn()
    ;['csrf-create', 'csrf-update', 'csrf-generate'].forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse(period))
    })
    const api = createBusinessPeriodApi({ fetcher })
    const payload: BusinessPeriodPayload = {
      periodCode: '2026-07',
      periodName: '2026年07月',
      startDate: '2026-07-01',
      endDate: '2026-07-31',
    }

    await api.create(payload)
    await api.update(12, payload)
    await api.generateMonthly({ startMonth: '2026-07', endMonth: '2026-12' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/system/business-periods', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-create',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/system/business-periods/12', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-update',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/system/business-periods/generate-monthly', {
      body: JSON.stringify({ startMonth: '2026-07', endMonth: '2026-12' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-generate',
      },
      method: 'POST',
    })
  })

  it('锁定和解锁业务期间时提交必填原因', async () => {
    const fetcher = vi.fn()
    ;['csrf-lock', 'csrf-unlock'].forEach((token) => {
      fetcher.mockResolvedValueOnce(apiResponse({ token, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      fetcher.mockResolvedValueOnce(apiResponse({ ...period, status: 'LOCKED' }))
    })
    const api = createBusinessPeriodApi({ fetcher })

    await api.lock(12, { reason: '月度经营数据核对完成' })
    await api.unlock(12, { reason: '补录已审批反向业务' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/system/business-periods/12/lock', {
      body: JSON.stringify({ reason: '月度经营数据核对完成' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-lock',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/system/business-periods/12/unlock', {
      body: JSON.stringify({ reason: '补录已审批反向业务' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-unlock',
      },
      method: 'POST',
    })
  })

  it('按业务日期解析期间状态', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({
      configured: true,
      businessDate: '2026-07-10',
      period,
      statusName: '开放',
      message: '业务日期处于开放期间',
    }))
    const api = createBusinessPeriodApi({ fetcher })

    const result = await api.resolve('2026-07-10')

    expect(fetcher).toHaveBeenCalledWith('/api/admin/system/business-periods/resolve?businessDate=2026-07-10', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(result.period?.periodCode).toBe('2026-07')
  })

  it('后端返回非成功 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createBusinessPeriodApi({ fetcher })
    const request = api.list({ page: 1, pageSize: 10 })

    await expect(request).rejects.toMatchObject({
      code: 'BUSINESS_PERIOD_LOCKED',
      status: 409,
      traceId: 'trace-period',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
