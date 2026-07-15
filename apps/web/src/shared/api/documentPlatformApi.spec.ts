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

const approvalDetail = {
  id: 3,
  taskId: 701,
  taskVersion: 16,
  sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
  objectType: 'SALES_PROJECT_CONTRACT',
  objectId: 55,
  objectNo: 'SC-001',
  objectName: '主合同',
  status: 'SUBMITTED',
  applicantName: '销售',
  submittedAt: '2026-07-13T10:00:00+08:00',
  version: 6,
  availableActions: ['APPROVE', 'REJECT', 'WITHDRAW', 'CANCEL'],
  steps: [{ taskId: 701, stepName: '固定审批', status: 'PENDING', version: 16 }],
  histories: [],
  attachmentSnapshots: [],
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

  it('审批详情和动作使用冻结 sceneCode 字段、最新版本和独立幂等键', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse(approvalDetail))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 7, status: 'APPROVED' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 8, status: 'REJECTED' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 3, status: 'WITHDRAWN' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 3, status: 'CANCELLED' }))
    const api = createDocumentPlatformApi({ fetcher })

    const detail = await api.approvals.get(3)
    await api.approvalTasks.approve(7, { version: 4, comment: '同意', idempotencyKey: 'approve-key' })
    await api.approvalTasks.reject(8, { version: 5, comment: '合同金额需调整', idempotencyKey: 'reject-key' })
    await api.approvals.withdraw(3, { version: 6, comment: '补充附件', idempotencyKey: 'withdraw-key' })
    await api.approvals.cancel(3, { version: 6, comment: '治理取消', idempotencyKey: 'cancel-key' })

    expect(detail.sceneCode).toBe('SALES_PROJECT_CONTRACT_ACTIVATION')
    expect(JSON.stringify(detail)).not.toContain('businessObjectNo')
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/approval-tasks/7/approve', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 4, comment: '同意', idempotencyKey: 'approve-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/admin/approval-tasks/8/reject', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 5, comment: '合同金额需调整', idempotencyKey: 'reject-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(7, '/api/admin/approvals/3/withdraw', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 6, comment: '补充附件', idempotencyKey: 'withdraw-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(9, '/api/admin/approvals/3/cancel', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 6, comment: '治理取消', idempotencyKey: 'cancel-key' }),
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

  it('附件列表消费 PageResult 并保留真实文件字段和可用动作', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({
      items: [{
        id: 5,
        objectType: 'SALES_PROJECT_CONTRACT',
        objectId: 55,
        fileName: '合同附件.pdf',
        fileSize: 1024,
        contentType: 'application/pdf',
        uploadedByName: '管理员',
        uploadedAt: '2026-07-13T10:00:00+08:00',
        status: 'AVAILABLE',
        availableActions: ['DOWNLOAD', 'DELETE'],
        version: 2,
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    }))
    const api = createDocumentPlatformApi({ fetcher })

    const page = await api.attachments.list({ objectType: 'SALES_PROJECT_CONTRACT', objectId: 55, page: 1, pageSize: 10 })

    expect(fetcher).toHaveBeenCalledWith('/api/admin/attachments?objectType=SALES_PROJECT_CONTRACT&objectId=55&page=1&pageSize=10', expect.objectContaining({
      method: 'GET',
    }))
    expect(page.items[0]).toEqual(expect.objectContaining({
      fileName: '合同附件.pdf',
      fileSize: 1024,
      contentType: 'application/pdf',
      uploadedByName: '管理员',
      version: 2,
    }))
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

  it('物料导出、BOM 草稿导入和确认任务使用冻结路径、扁平筛选与 Idempotency-Key', async () => {
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

    await api.exports.createMaterials({ keyword: '钢板', status: 'ENABLED', materialType: 'RAW_MATERIAL', idempotencyKey: 'export-key' })
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
      body: JSON.stringify({ keyword: '钢板', status: 'ENABLED', materialType: 'RAW_MATERIAL' }),
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

  it('消息已读携带当前 version，全部已读返回 updatedCount，打印模板按 sceneCode 查询并先请求预览', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ id: 11, status: 'READ', version: 3 }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ updatedCount: 3 }))
      .mockResolvedValueOnce(apiResponse([{ templateCode: 'CONTRACT_ACTIVATION_APPROVAL_V1', name: '合同生效审批单', sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION', templateVersion: 1 }]))
      .mockResolvedValueOnce(apiResponse({ approvalInstanceId: 3, templateCode: 'CONTRACT_ACTIVATION_APPROVAL_V1', templateVersion: 1, sections: [] }))
    const api = createDocumentPlatformApi({ fetcher })

    await api.messages.markRead(11, { version: 2 })
    const readAllResult = await api.messages.markAllRead()
    await api.printTemplates.list({ sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION' })
    await api.printPreviews.get(3)

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/messages/11/read', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ version: 2 }),
    }))
    expect(readAllResult).toEqual({ updatedCount: 3 })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/messages/read-all', expect.objectContaining({
      method: 'PUT',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/admin/print-templates?sceneCode=SALES_PROJECT_CONTRACT_ACTIVATION', expect.objectContaining({
      method: 'GET',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/print-previews/3', expect.objectContaining({
      method: 'GET',
    }))
  })

  it('024 采购报价导入、筛选导出和采购订单打印复用文档任务冻结路径', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...task, taskType: 'PROCUREMENT_QUOTE_IMPORT' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...task, taskType: 'PROCUREMENT_INQUIRY_EXPORT' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...task, taskType: 'PROCUREMENT_REQUISITION_EXPORT' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...task, taskType: 'PROCUREMENT_PRICE_AGREEMENT_EXPORT' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...task, taskType: 'PROCUREMENT_ORDER_EXPORT' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...task, taskType: 'PROCUREMENT_SCHEDULE_EXPORT' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...task, taskType: 'PROCUREMENT_SUPPLY_EXPORT' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...task, taskType: 'PROCUREMENT_ORDER_PRINT' }))
    const api = createDocumentPlatformApi({ fetcher })
    const file = new File(['quotes'], '供应商报价.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })

    await api.imports.uploadProcurementQuotes(201, { file, idempotencyKey: 'quote-import-key' })
    await api.exports.createProcurementInquiries({
      keyword: '电机',
      procurementMode: 'PROJECT',
      projectId: 30,
      status: 'COMPLETED',
      idempotencyKey: 'inquiry-export-key',
    })
    await api.exports.createProcurementRequisitions({
      keyword: '电机',
      procurementMode: 'PROJECT',
      projectId: 30,
      status: 'APPROVED',
      idempotencyKey: 'requisition-export-key',
    })
    await api.exports.createProcurementPriceAgreements({
      keyword: '协议',
      procurementMode: 'PROJECT',
      projectId: 30,
      status: 'ACTIVE',
      idempotencyKey: 'agreement-export-key',
    })
    await api.exports.createProcurementOrders({
      keyword: 'PO',
      procurementMode: 'PROJECT',
      projectId: 30,
      status: 'CONFIRMED',
      idempotencyKey: 'order-export-key',
    })
    await api.exports.createProcurementSchedules(99, {
      status: 'PLANNED',
      expectedDateFrom: '2026-07-20',
      idempotencyKey: 'schedule-export-key',
    })
    await api.exports.createProcurementEffectiveSupplies({
      projectId: 30,
      procurementMode: 'PROJECT',
      countedOnly: true,
      idempotencyKey: 'supply-export-key',
    })
    await api.printTasks.create({
      objectType: 'PROCUREMENT_ORDER',
      objectId: 99,
      templateCode: 'PROCUREMENT_ORDER_V1',
      idempotencyKey: 'order-print-key',
    })

    expect(fetcher.mock.calls[1][0]).toBe('/api/admin/procurement/inquiries/201/quote-imports')
    expect(fetcher.mock.calls[1][1]).toEqual(expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'Idempotency-Key': 'quote-import-key' }),
    }))
    expect(fetcher.mock.calls[1][1].body).toBeInstanceOf(FormData)
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/export-tasks', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        taskType: 'PROCUREMENT_INQUIRY_EXPORT',
        filters: {
          keyword: '电机',
          procurementMode: 'PROJECT',
          projectId: 30,
          status: 'COMPLETED',
        },
      }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/export-tasks', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        taskType: 'PROCUREMENT_REQUISITION_EXPORT',
        filters: {
          keyword: '电机',
          procurementMode: 'PROJECT',
          projectId: 30,
          status: 'APPROVED',
        },
      }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/export-tasks', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        taskType: 'PROCUREMENT_PRICE_AGREEMENT_EXPORT',
        filters: {
          keyword: '协议',
          procurementMode: 'PROJECT',
          projectId: 30,
          status: 'ACTIVE',
        },
      }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/export-tasks', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        taskType: 'PROCUREMENT_ORDER_EXPORT',
        filters: {
          keyword: 'PO',
          procurementMode: 'PROJECT',
          projectId: 30,
          status: 'CONFIRMED',
        },
      }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/export-tasks', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        taskType: 'PROCUREMENT_SCHEDULE_EXPORT',
        objectType: 'PROCUREMENT_ORDER',
        objectId: 99,
        filters: {
          orderId: 99,
          status: 'PLANNED',
          expectedDateFrom: '2026-07-20',
        },
      }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(14, '/api/admin/export-tasks', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        taskType: 'PROCUREMENT_SUPPLY_EXPORT',
        filters: {
          projectId: 30,
          procurementMode: 'PROJECT',
          countedOnly: true,
        },
      }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(16, '/api/admin/print-tasks', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        objectType: 'PROCUREMENT_ORDER',
        objectId: 99,
        templateCode: 'PROCUREMENT_ORDER_V1',
      }),
    }))
  })
})
