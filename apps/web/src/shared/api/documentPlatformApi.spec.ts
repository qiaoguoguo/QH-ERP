import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import { createDocumentPlatformApi, type DocumentTaskRecord } from './documentPlatformApi'

function apiResponse<T>(data: T, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => ({
      success: status >= 200 && status < 300,
      code: status >= 200 && status < 300 ? 'OK' : 'ERROR',
      message: status >= 200 && status < 300 ? '成功' : '失败',
      data,
      traceId: 'trace-022',
    }),
  } as Response
}

function apiFailure(code = 'DOCUMENT_RESULT_EXPIRED', message = '结果已过期', status = 410) {
  return {
    ok: false,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => ({
      success: false,
      code,
      message,
      data: null,
      traceId: 'trace-022',
    }),
  } as Response
}

function csrfResponse() {
  return apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' })
}

const task: DocumentTaskRecord = {
  id: 91,
  taskNo: 'TASK-202607-0001',
  taskType: 'MATERIAL_IMPORT',
  objectType: 'MATERIAL',
  direction: 'IMPORT',
  stage: 'VALIDATE',
  status: 'QUEUED',
  progressPercent: 0,
  totalRows: 0,
  successRows: 0,
  failedRows: 0,
  createdByName: '管理员',
  createdAt: '2026-07-13T10:00:00+08:00',
  version: 1,
  availableActions: ['CANCEL'],
}

describe('022 文档平台 API', () => {
  it('提交合同与 ECO 审批使用冻结路径、对象版本和幂等键', async () => {
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

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/approvals/sales-project-contract-activation/55/submit', {
      body: JSON.stringify({ version: 3, reason: '合同草稿确认无误', idempotencyKey: 'contract-key' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/approvals/bom-eco-application/100/submit', {
      body: JSON.stringify({ version: 2, reason: '材料替代方案已确认', idempotencyKey: 'eco-key' }),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      method: 'POST',
    })
  })

  it('审批任务动作携带任务版本并按后端 availableActions 消费', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 7, status: 'APPROVED' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 8, status: 'REJECTED' }))
    const api = createDocumentPlatformApi({ fetcher })

    await api.approvalTasks.approve(7, { version: 4, comment: '同意' })
    await api.approvalTasks.reject(8, { version: 5, comment: '合同金额需调整' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/approval-tasks/7/approve', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 4, comment: '同意' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/approval-tasks/8/reject', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 5, comment: '合同金额需调整' }),
    }))
  })

  it('附件上传使用 FormData、CSRF 和幂等键且不手写 Content-Type', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(csrfResponse()).mockResolvedValueOnce(apiResponse({ id: 5 }))
    const api = createDocumentPlatformApi({ fetcher })
    const file = new File(['hello'], '合同附件.pdf', { type: 'application/pdf' })

    await api.attachments.upload({
      objectType: 'SALES_PROJECT_CONTRACT',
      objectId: 55,
      file,
      description: '纸质合同扫描件',
      idempotencyKey: 'attachment-key',
    })

    const init = fetcher.mock.calls[1][1] as RequestInit
    expect(fetcher.mock.calls[1][0]).toBe('/api/admin/attachments')
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
    expect(init.headers).toEqual({
      Accept: 'application/json',
      'X-CSRF-TOKEN': 'csrf-token',
      'Idempotency-Key': 'attachment-key',
    })
  })

  it('模板、任务结果和附件下载成功返回 Blob 文件名，失败时解析 JSON envelope', async () => {
    const blob = new Blob(['xlsx'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          'content-disposition': "attachment; filename*=UTF-8''%E7%89%A9%E6%96%99%E6%A8%A1%E6%9D%BF.xlsx",
        }),
        blob: async () => blob,
      } as Response)
      .mockResolvedValueOnce(apiFailure())
    const api = createDocumentPlatformApi({ fetcher })

    const result = await api.importTemplates.downloadMaterials()

    expect(result.fileName).toBe('物料模板.xlsx')
    expect(result.blob).toBe(blob)
    const failedDownload = api.documentTasks.download(91)
    await expect(failedDownload).rejects.toMatchObject({
      code: 'DOCUMENT_RESULT_EXPIRED',
      status: 410,
      traceId: 'trace-022',
    })
    await expect(failedDownload).rejects.toBeInstanceOf(AccountPermissionApiError)
  })

  it('物料导出、BOM 草稿导入和确认任务使用冻结路径与 Idempotency-Key', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse(task))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse(task))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...task, status: 'RUNNING' }))
    const api = createDocumentPlatformApi({ fetcher })
    const file = new File(['bom'], 'BOM草稿.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })

    await api.exports.createMaterials({ filters: { keyword: '钢板', status: 'ENABLED' }, idempotencyKey: 'export-key' })
    await api.imports.uploadBomDraft({
      mode: 'UPDATE_DRAFT',
      bomId: 1,
      version: 3,
      file,
      idempotencyKey: 'bom-import-key',
    })
    await api.imports.confirm(91, { version: 1, idempotencyKey: 'confirm-key' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/exports/materials', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ filters: { keyword: '钢板', status: 'ENABLED' } }),
      headers: expect.objectContaining({ 'Idempotency-Key': 'export-key' }),
    }))
    expect(fetcher.mock.calls[3][0]).toBe('/api/admin/imports/bom-drafts')
    expect(fetcher.mock.calls[3][1].headers).toEqual(expect.objectContaining({
      'Idempotency-Key': 'bom-import-key',
      'X-CSRF-TOKEN': 'csrf-token',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/imports/91/confirm', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 1 }),
      headers: expect.objectContaining({ 'Idempotency-Key': 'confirm-key' }),
    }))
  })
})
