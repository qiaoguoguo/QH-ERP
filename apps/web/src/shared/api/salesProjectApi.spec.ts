import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createSalesProjectApi,
  type SalesOrderProjectContractCandidate,
  type SalesProjectContractUpdatePayload,
  type SalesProjectCreatePayload,
  type SalesProjectUpdatePayload,
} from './salesProjectApi'

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
      traceId: 'trace-project',
    }),
  } as Response
}

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'PROJECT_CONCURRENT_MODIFICATION',
      message: '项目版本已变化，请刷新后重试',
      data: null,
      traceId: 'trace-conflict',
    }),
  } as Response
}

describe('销售项目 API', () => {
  const typeContract = {
    createPayloadHasNoVersion: true as AssertTrue<'version' extends keyof SalesProjectCreatePayload ? false : true>,
    updatePayloadRequiresVersion: true as AssertTrue<SalesProjectUpdatePayload extends { version: number } ? true : false>,
    contractUpdateRequiresVersion: true as AssertTrue<
      SalesProjectContractUpdatePayload extends { version: number } ? true : false
    >,
    orderCandidateHasProjectAndContract: true as AssertTrue<
      SalesOrderProjectContractCandidate extends { projectId: unknown; contractId: unknown } ? true : false
    >,
  }

  it('声明创建和更新 payload 分离，更新与状态动作必须携带版本', () => {
    expect(typeContract).toMatchObject({
      createPayloadHasNoVersion: true,
      updatePayloadRequiresVersion: true,
      contractUpdateRequiresVersion: true,
      orderCandidateHasProjectAndContract: true,
    })
  })

  it('按契约请求项目、合同、负责人候选和销售订单关联候选', async () => {
    const fetcher = vi.fn()
    fetcher
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 10 }))
      .mockResolvedValueOnce(apiResponse({ id: 12, projectNo: 'SP-1', contracts: [], operations: [] }))
      .mockResolvedValueOnce(apiResponse({ items: [{ userId: 7, username: 'owner', displayName: '负责人' }], total: 1, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [{ projectId: 12, contractId: 55 }], total: 1, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 12, projectNo: 'SP-1' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-activate', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 12, status: 'ACTIVE', version: 4 }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-contract', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 55, contractNo: 'SC-1', version: 2 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 10 }))
    const api = createSalesProjectApi({ fetcher })

    await api.projects.list({ keyword: '华东', customerId: 3, status: 'ACTIVE', page: 1, pageSize: 10 })
    await api.projects.get(12)
    await api.ownerCandidates({ keyword: 'owner', page: 1, pageSize: 20 })
    await api.listOrderLinkCandidates({ customerId: 3, keyword: 'SC', page: 1, pageSize: 20 })
    await api.projects.create({
      name: '华东项目',
      customerId: 3,
      ownerUserId: 7,
      targetRevenue: '100.00',
      targetCost: '20.00',
    })
    await api.projects.activate(12, { version: 3 })
    await api.contracts.update(55, { version: 1, name: '主合同调整' })
    await api.projectSalesOrders(12, { contractId: 55, status: 'CONFIRMED', page: 1, pageSize: 10 })

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      '/api/admin/sales-projects?keyword=%E5%8D%8E%E4%B8%9C&customerId=3&status=ACTIVE&page=1&pageSize=10',
      '/api/admin/sales-projects/12',
      '/api/admin/sales-projects/owner-candidates?keyword=owner&page=1&pageSize=20',
      '/api/admin/sales-projects/order-link-candidates?customerId=3&keyword=SC&page=1&pageSize=20',
      '/api/auth/csrf',
      '/api/admin/sales-projects',
      '/api/auth/csrf',
      '/api/admin/sales-projects/12/activate',
      '/api/auth/csrf',
      '/api/admin/sales-project-contracts/55',
      '/api/admin/sales-projects/12/sales-orders?contractId=55&status=CONFIRMED&page=1&pageSize=10',
    ])
    expect(JSON.parse(fetcher.mock.calls[5][1].body as string)).not.toHaveProperty('version')
    expect(JSON.parse(fetcher.mock.calls[7][1].body as string)).toEqual({ version: 3 })
    expect(JSON.parse(fetcher.mock.calls[9][1].body as string)).toEqual({ version: 1, name: '主合同调整' })
  })

  it('后端返回失败 envelope 时抛出统一 API 错误', async () => {
    const api = createSalesProjectApi({ fetcher: vi.fn().mockResolvedValueOnce(apiFailure()) })
    const request = api.projects.list({ page: 1, pageSize: 10 })

    await expect(request).rejects.toMatchObject({
      code: 'PROJECT_CONCURRENT_MODIFICATION',
      status: 409,
      traceId: 'trace-conflict',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
