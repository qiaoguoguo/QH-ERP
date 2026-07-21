import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createPlatformGovernanceApi,
  type BatchOperationRecord,
  type DataRepairDetail,
  type HistoryImportDetail,
} from './platformGovernanceApi'

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
      traceId: 'trace-034',
    }),
  } as Response
}

function csrfResponse() {
  return apiResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' })
}

function apiFailure(code = 'DATA_REPAIR_STATUS_INVALID', message = '当前状态不可执行', status = 409) {
  return {
    ok: false,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => ({
      success: false,
      code,
      message,
      data: null,
      traceId: 'trace-034',
    }),
  } as Response
}

const repairDetail: DataRepairDetail = {
  id: 501,
  requestNo: 'DR-202607-0001',
  adapterCode: 'MATERIAL_PROFILE_CORRECTION_V1',
  targetObjectType: 'MATERIAL',
  targetObjectId: 31,
  targetObjectNo: 'MAT-001',
  targetObjectSummary: '旧规格电机',
  targetObjectVersion: 7,
  reason: '历史引用后需留痕更正',
  riskSummary: '修正物料规格说明',
  status: 'READY_TO_EXECUTE',
  createdByUserId: 8,
  createdByUsername: '资料员',
  createdAt: '2026-07-21T10:00:00+08:00',
  submittedAt: '2026-07-21T10:10:00+08:00',
  version: 3,
  availableActions: ['EXECUTE', 'CANCEL'],
  beforeSummary: { specification: '旧规格', remark: '旧备注' },
  afterSummary: { specification: '新规格', remark: '新备注' },
  changes: [{ fieldName: 'specification', beforeValueSummary: '旧规格', afterValueSummary: '新规格' }],
  checks: [{
    checkType: 'PRECHECK',
    status: 'PASSED',
    code: null,
    message: '对象版本已固化',
    detail: { target: 'MAT-001' },
    createdAt: '2026-07-21T10:01:00+08:00',
  }],
  events: [{
    eventType: 'SUBMIT',
    operatorUsername: '资料员',
    statusBefore: 'DRAFT',
    statusAfter: 'PENDING_APPROVAL',
    detail: { comment: '提交审批' },
    createdAt: '2026-07-21T10:10:00+08:00',
  }],
  approvalSummary: { id: 70, status: 'APPROVED', version: 1, taskId: 701, taskVersion: 16 },
  auditSummary: { auditLogId: 90, summary: '已写入脱敏审计' },
  attachmentObjectType: 'DATA_REPAIR_REQUEST',
  attachmentObjectId: 501,
}

const importDetail: HistoryImportDetail = {
  id: 601,
  taskId: 601,
  taskNo: 'HI-202607-0001',
  adapterCode: 'CUSTOMER_MASTER_V1',
  adapterName: '客户历史导入',
  sourceFileName: '客户历史.xlsx',
  sourceSha256: 'sha256',
  templateCode: 'CUSTOMER_MASTER_V1_TEMPLATE',
  templateVersion: 1,
  status: 'READY_TO_COMMIT',
  stage: 'VALIDATE',
  progressPercent: 100,
  totalRows: 20,
  successRows: 20,
  failedRows: 0,
  createdByName: '管理员',
  createdAt: '2026-07-21T11:00:00+08:00',
  version: 4,
  availableActions: ['CONFIRM', 'DOWNLOAD'],
  validationSummary: { totalRows: 20, successRows: 20, failedRows: 0, summary: '预检通过' },
  errorSummary: { totalErrors: 0 },
  auditSummary: { auditLogId: 91, summary: '已记录导入预检' },
  relatedTaskId: 601,
}

const batchOperation: BatchOperationRecord = {
  id: 701,
  operationNo: 'BOP20260721120000001',
  toolCode: 'CUSTOMER_STATUS_CHANGE_V1',
  targetObjectType: 'CUSTOMER',
  actionCode: 'STATUS_CHANGE',
  status: 'PRECHECKED',
  totalRows: 2,
  successRows: 0,
  failedRows: 0,
  errorMessage: null,
  createdByName: '管理员',
  executedByName: null,
  executedAt: null,
  createdAt: '2026-07-21T12:00:00+08:00',
  version: 1,
  availableActions: ['EXECUTE'],
  items: [
    {
      lineNo: 1,
      targetObjectType: 'CUSTOMER',
      targetObjectId: 1,
      targetObjectNo: 'CUS-001',
      targetObjectSummary: '华南设备客户',
      targetObjectVersion: 5,
      status: 'READY',
      message: '预检通过',
    },
    {
      lineNo: 2,
      targetObjectType: 'CUSTOMER',
      targetObjectId: 2,
      targetObjectNo: 'CUS-002',
      targetObjectSummary: '华东客户',
      targetObjectVersion: 6,
      status: 'READY',
      message: '预检通过',
    },
  ],
}

describe('034 平台治理 API', () => {
  it('数据修复目录、列表和详情使用冻结路径与分页筛选', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse([{ adapterCode: 'MATERIAL_PROFILE_CORRECTION_V1', name: '物料资料修复', targetObjectType: 'MATERIAL', allowedFields: ['specification'], requiredPermissionCode: 'master:material:update', version: 1 }]))
      .mockResolvedValueOnce(apiResponse({ items: [repairDetail], total: 1, page: 1, pageSize: 10 }))
      .mockResolvedValueOnce(apiResponse(repairDetail))
    const api = createPlatformGovernanceApi({ fetcher })

    await api.dataRepairAdapters.list()
    await api.dataRepairs.list({
      keyword: 'MAT',
      adapterCode: 'MATERIAL_PROFILE_CORRECTION_V1',
      targetObjectType: 'MATERIAL',
      status: 'READY_TO_EXECUTE',
      page: 1,
      pageSize: 10,
    })
    const detail = await api.dataRepairs.get(501)

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/platform/data-repair-adapters', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/platform/data-repairs?keyword=MAT&adapterCode=MATERIAL_PROFILE_CORRECTION_V1&targetObjectType=MATERIAL&status=READY_TO_EXECUTE&page=1&pageSize=10', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/platform/data-repairs/501', expect.objectContaining({ method: 'GET' }))
    expect(detail.requestNo).toBe('DR-202607-0001')
    expect(detail.beforeSummary).toEqual({ specification: '旧规格', remark: '旧备注' })
    expect(detail.availableActions).toEqual(['EXECUTE', 'CANCEL'])
  })

  it('数据修复创建、更新、提交、执行、验证和取消均携带冻结版本与幂等契约', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse(repairDetail))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...repairDetail, status: 'DRAFT', version: 4 }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...repairDetail, status: 'PENDING_APPROVAL' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...repairDetail, status: 'EXECUTING' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...repairDetail, status: 'VERIFIED' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...repairDetail, status: 'VERIFY_FAILED' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...repairDetail, status: 'CANCELLED' }))
    const api = createPlatformGovernanceApi({ fetcher })

    await api.dataRepairs.create({
      adapterCode: 'MATERIAL_PROFILE_CORRECTION_V1',
      targetObjectType: 'MATERIAL',
      targetObjectId: 31,
      targetVersion: 7,
      riskSummary: '修正规格',
      reason: '历史留痕',
      changes: [{ fieldName: 'specification', afterValue: '新规格' }],
      idempotencyKey: 'repair-create-key',
    })
    await api.dataRepairs.update(501, {
      version: 3,
      riskSummary: '修正规格更新',
      reason: '补充更新原因',
      changes: [{ fieldName: 'remark', afterValue: '新备注' }],
      idempotencyKey: 'repair-update-key',
    })
    await api.dataRepairs.submit(501, { version: 3, comment: '提交审批', idempotencyKey: 'repair-submit-key' })
    await api.dataRepairs.execute(501, { version: 4, comment: '审批后执行', idempotencyKey: 'repair-execute-key' })
    await api.dataRepairs.verify(501, { version: 5, comment: '验证通过', passed: true, idempotencyKey: 'repair-verify-key' })
    await api.dataRepairs.verify(501, { version: 6, comment: '验证失败', passed: false, idempotencyKey: 'repair-verify-failed-key' })
    await api.dataRepairs.cancel(501, { version: 6, reason: '对象状态变化', idempotencyKey: 'repair-cancel-key' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/platform/data-repairs', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        adapterCode: 'MATERIAL_PROFILE_CORRECTION_V1',
        targetObjectType: 'MATERIAL',
        targetObjectId: 31,
        targetVersion: 7,
        riskSummary: '修正规格',
        reason: '历史留痕',
        changes: [{ fieldName: 'specification', afterValue: '新规格' }],
      }),
      headers: expect.objectContaining({ 'Idempotency-Key': 'repair-create-key', 'X-CSRF-TOKEN': 'csrf-token' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/platform/data-repairs/501', expect.objectContaining({
      method: 'PUT',
      headers: expect.objectContaining({ 'Idempotency-Key': 'repair-update-key' }),
    }))
    expect(JSON.parse(fetcher.mock.calls[3][1].body as string)).toEqual({
      version: 3,
      reason: '补充更新原因',
      riskSummary: '修正规格更新',
      changes: [{ fieldName: 'remark', afterValue: '新备注' }],
      idempotencyKey: 'repair-update-key',
    })
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/platform/data-repairs/501/submit', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 3, comment: '提交审批', idempotencyKey: 'repair-submit-key' }),
      headers: expect.objectContaining({ 'Idempotency-Key': 'repair-submit-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/platform/data-repairs/501/execute', expect.objectContaining({
      body: JSON.stringify({ version: 4, comment: '审批后执行', idempotencyKey: 'repair-execute-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/platform/data-repairs/501/verify', expect.objectContaining({
      body: JSON.stringify({ version: 5, comment: '验证通过', passed: true, idempotencyKey: 'repair-verify-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(12, '/api/admin/platform/data-repairs/501/verify', expect.objectContaining({
      body: JSON.stringify({ version: 6, comment: '验证失败', passed: false, idempotencyKey: 'repair-verify-failed-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(14, '/api/admin/platform/data-repairs/501/cancel', expect.objectContaining({
      body: JSON.stringify({ version: 6, reason: '对象状态变化', idempotencyKey: 'repair-cancel-key' }),
    }))
  })

  it('历史导入目录、模板下载、上传、确认和取消使用冻结路径、FormData 与二进制错误 envelope', async () => {
    const templateBlob = new Blob(['xlsx'])
    const file = new File(['customers'], '客户历史.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse([{ adapterCode: 'CUSTOMER_MASTER_V1', name: '客户历史导入', targetObjectType: 'CUSTOMER', templateCode: 'CUSTOMER_MASTER_V1_TEMPLATE', templateVersion: 1, maxRows: 10000, requiredPermissionCode: 'master:customer:create', version: 1 }]))
      .mockResolvedValueOnce({ ok: true, status: 200, headers: new Headers({ 'content-disposition': "attachment; filename*=UTF-8''customer.xlsx" }), blob: async () => templateBlob } as Response)
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse(importDetail))
      .mockResolvedValueOnce(apiResponse(importDetail))
      .mockResolvedValueOnce(apiResponse({ items: [{ rowNo: 3, columnName: '客户编码', errorCode: 'HISTORY_IMPORT_ALREADY_EXISTS', message: '客户编码已存在' }], total: 1, page: 1, pageSize: 10 }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...importDetail, status: 'RUNNING' }))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...importDetail, status: 'CANCELLED' }))
      .mockResolvedValueOnce(apiFailure('HISTORY_IMPORT_TEMPLATE_VERSION_MISMATCH', '模板版本不一致', 409))
    const api = createPlatformGovernanceApi({ fetcher })

    await api.historyImportAdapters.list()
    const template = await api.historyImportAdapters.downloadTemplate('CUSTOMER_MASTER_V1')
    await api.historyImports.upload('CUSTOMER_MASTER_V1', { file, idempotencyKey: 'history-upload-key' })
    await api.historyImports.get(601)
    await api.historyImports.errors(601, { page: 1, pageSize: 10 })
    await api.historyImports.confirm(601, { version: 4, idempotencyKey: 'history-confirm-key' })
    await api.historyImports.cancel(601, { version: 5, idempotencyKey: 'history-cancel-key' })

    expect(template.blob).toBe(templateBlob)
    expect(template.fileName).toBe('customer.xlsx')
    expect(fetcher.mock.calls[3][0]).toBe('/api/admin/platform/history-imports/CUSTOMER_MASTER_V1')
    expect(fetcher.mock.calls[3][1].body).toBeInstanceOf(FormData)
    expect(fetcher.mock.calls[3][1].headers).toEqual(expect.objectContaining({
      'Idempotency-Key': 'history-upload-key',
      'X-CSRF-TOKEN': 'csrf-token',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/document-tasks/601/errors?page=1&pageSize=10', expect.objectContaining({
      method: 'GET',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(8, '/api/admin/platform/history-imports/601/confirm', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 4, idempotencyKey: 'history-confirm-key' }),
      headers: expect.objectContaining({ 'Idempotency-Key': 'history-confirm-key' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(10, '/api/admin/platform/history-imports/601/cancel', expect.objectContaining({
      body: JSON.stringify({ version: 5, idempotencyKey: 'history-cancel-key' }),
    }))
    await expect(api.historyImportAdapters.downloadTemplate('CUSTOMER_MASTER_V1')).rejects.toBeInstanceOf(AccountPermissionApiError)
  })

  it('历史导入列表发送冻结筛选参数，不做前端假过滤', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ items: [importDetail], total: 1, page: 2, pageSize: 50 }))
    const api = createPlatformGovernanceApi({ fetcher })

    await api.historyImports.list({
      keyword: '客户历史.xlsx',
      adapterCode: 'CUSTOMER_MASTER_V1',
      status: 'READY_TO_COMMIT',
      page: 2,
      pageSize: 50,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/platform/history-imports?keyword=%E5%AE%A2%E6%88%B7%E5%8E%86%E5%8F%B2.xlsx&adapterCode=CUSTOMER_MASTER_V1&status=READY_TO_COMMIT&page=2&pageSize=50',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('交付资料、批量工具和错误码使用平台治理 API，不污染 documentPlatformApi', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({
        stageCode: '034',
        generatedAt: '2026-07-21T12:00:00+08:00',
        historyImportAdapters: [{ code: 'CUSTOMER_MASTER_V1', name: '客户历史导入', targetObjectType: 'CUSTOMER', status: 'ACTIVE', version: 1 }],
        dataRepairAdapters: [{ code: 'MATERIAL_PROFILE_CORRECTION_V1', name: '物料资料修复', targetObjectType: 'MATERIAL', status: 'ACTIVE', version: 1 }],
        batchTools: [{ code: 'CUSTOMER_STATUS_CHANGE_V1', name: '客户状态批量变更', targetObjectType: 'CUSTOMER', actionCode: 'STATUS_CHANGE', status: 'ACTIVE', version: 1 }],
        printTemplates: [{ templateCode: 'SALES_ORDER_V1', sceneCode: 'SALES_ORDER_PRINT', name: '销售订单', templateVersion: 1, objectType: 'SALES_ORDER', status: 'ACTIVE' }],
        staticAssets: [{ code: 'OPERATION_MANUAL', path: 'docs/manual/system-operation-manual.md', note: '操作手册只读目录引用' }],
      }))
      .mockResolvedValueOnce(apiResponse([{ toolCode: 'CUSTOMER_STATUS_CHANGE_V1', name: '客户状态批量变更', targetObjectType: 'CUSTOMER', enabled: true }]))
    const api = createPlatformGovernanceApi({ fetcher })

    const assets = await api.deliveryAssets.get()
    const tools = await api.batchTools.list()

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/admin/platform/delivery-assets', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/platform/batch-tools', expect.objectContaining({ method: 'GET' }))
    expect(assets.stageCode).toBe('034')
    expect(assets.printTemplates[0].templateCode).toBe('SALES_ORDER_V1')
    expect(assets.staticAssets[0].code).toBe('OPERATION_MANUAL')
    expect(tools[0].toolCode).toBe('CUSTOMER_STATUS_CHANGE_V1')
  })

  it('批量工具预检、详情和确认执行使用冻结路径、版本与幂等字段', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse(batchOperation))
      .mockResolvedValueOnce(apiResponse(batchOperation))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(apiResponse({ ...batchOperation, status: 'SUCCEEDED', successRows: 2, version: 3, availableActions: [] }))
    const api = createPlatformGovernanceApi({ fetcher })

    await api.batchTools.preview('CUSTOMER_STATUS_CHANGE_V1', {
      actionCode: 'STATUS_CHANGE',
      targetStatus: 'DISABLED',
      reason: '停用历史客户',
      targets: [
        { targetObjectId: 1, version: 5 },
        { targetObjectId: 2, version: 6 },
      ],
      idempotencyKey: 'batch-preview-key',
    })
    await api.batchOperations.get(701)
    await api.batchOperations.execute(701, { version: 1, idempotencyKey: 'batch-execute-key' })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/platform/batch-tools/CUSTOMER_STATUS_CHANGE_V1/preview', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        actionCode: 'STATUS_CHANGE',
        targetStatus: 'DISABLED',
        reason: '停用历史客户',
        targets: [
          { targetObjectId: 1, version: 5 },
          { targetObjectId: 2, version: 6 },
        ],
        idempotencyKey: 'batch-preview-key',
      }),
      headers: expect.objectContaining({ 'Idempotency-Key': 'batch-preview-key', 'X-CSRF-TOKEN': 'csrf-token' }),
    }))
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/admin/platform/batch-operations/701', expect.objectContaining({ method: 'GET' }))
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/admin/platform/batch-operations/701/execute', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 1, idempotencyKey: 'batch-execute-key' }),
      headers: expect.objectContaining({ 'Idempotency-Key': 'batch-execute-key' }),
    }))
  })
})
