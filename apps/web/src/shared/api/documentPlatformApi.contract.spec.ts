import { describe, expect, it, vi } from 'vitest'
import { createDocumentPlatformApi } from './documentPlatformApi'

function apiResponse<T>(data: T) {
  return {
    ok: true,
    status: 200,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => ({
      success: true,
      code: 'OK',
      message: '成功',
      data,
      traceId: 'trace-022-contract',
    }),
  } as Response
}

function csrfResponse() {
  return apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' })
}

describe('022 文档平台前后端契约', () => {
  it('提交审批请求体必须使用后端 ApprovalSubmitRequest.version 字段', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 1, status: 'SUBMITTED' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 2, status: 'SUBMITTED' }))
    const api = createDocumentPlatformApi({ fetcher })

    await api.approvals.submitSalesProjectContractActivation(55, {
      version: 3,
      reason: '合同草稿确认无误',
      idempotencyKey: 'contract-key',
    })
    await api.approvals.submitBomEcoApplication(100, {
      version: 2,
      reason: '材料替代方案已确认',
      idempotencyKey: 'eco-key',
    })

    expect(JSON.parse(String(fetcher.mock.calls[1][1].body))).toEqual({
      version: 3,
      reason: '合同草稿确认无误',
      idempotencyKey: 'contract-key',
    })
    expect(JSON.parse(String(fetcher.mock.calls[3][1].body))).toEqual({
      version: 2,
      reason: '材料替代方案已确认',
      idempotencyKey: 'eco-key',
    })
  })

  it('审批动作请求体必须使用 comment，不再发送 reason', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 1, status: 'REJECTED' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 1, status: 'WITHDRAWN' }))
    const api = createDocumentPlatformApi({ fetcher })

    await api.approvalTasks.reject(701, {
      version: 16,
      comment: '合同金额需调整',
      idempotencyKey: 'reject-key',
    })
    await api.approvals.withdraw(3, {
      version: 6,
      comment: '补充附件',
      idempotencyKey: 'withdraw-key',
    })

    expect(JSON.parse(String(fetcher.mock.calls[1][1].body))).toEqual({
      version: 16,
      comment: '合同金额需调整',
      idempotencyKey: 'reject-key',
    })
    expect(JSON.stringify(JSON.parse(String(fetcher.mock.calls[1][1].body)))).not.toContain('reason')
    expect(JSON.parse(String(fetcher.mock.calls[3][1].body))).toEqual({
      version: 6,
      comment: '补充附件',
      idempotencyKey: 'withdraw-key',
    })
  })
})
