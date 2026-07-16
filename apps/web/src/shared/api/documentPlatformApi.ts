import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type ResourceId = string | number
export type Fetcher = (input: string, init: RequestInit) => Promise<Response>

export type ApprovalScope = 'TODO' | 'DONE' | 'STARTED'
export type ApprovalInstanceStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'CANCELLED'
export type ApprovalTaskStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type ApprovalAction = 'APPROVE' | 'REJECT' | 'WITHDRAW' | 'CANCEL'
export type ApprovalSceneCode =
  | 'SALES_PROJECT_CONTRACT_ACTIVATION'
  | 'SALES_QUOTE_APPROVAL'
  | 'SALES_ORDER_CHANGE_APPROVAL'
  | 'SALES_ORDER_CHANGE_CREDIT_OVERRIDE'
  | 'SALES_ORDER_CREDIT_OVERRIDE'
  | 'SALES_ORDER_SHORT_CLOSE'
  | 'BOM_ECO_APPLICATION'
export type MessageStatus = 'UNREAD' | 'READ'
export type AttachmentObjectType =
  | 'SALES_PROJECT'
  | 'SALES_PROJECT_CONTRACT'
  | 'SALES_QUOTE'
  | 'SALES_ORDER_CHANGE'
  | 'BOM_ENGINEERING_CHANGE'
  | 'INVENTORY_STOCKTAKE'
export type AttachmentStatus = 'AVAILABLE' | 'DELETED'
export type AttachmentAction = 'DOWNLOAD' | 'DELETE'
export type DocumentTaskType =
  | 'MATERIAL_IMPORT'
  | 'MATERIAL_EXPORT'
  | 'BOM_DRAFT_IMPORT'
  | 'BOM_DRAFT_EXPORT'
  | 'APPROVAL_PRINT'
  | 'PROCUREMENT_REQUISITION_EXPORT'
  | 'PROCUREMENT_INQUIRY_EXPORT'
  | 'PROCUREMENT_QUOTE_IMPORT'
  | 'PROCUREMENT_QUOTE_EXPORT'
  | 'PROCUREMENT_PRICE_AGREEMENT_EXPORT'
  | 'PROCUREMENT_ORDER_EXPORT'
  | 'PROCUREMENT_ORDER_PRINT'
  | 'PROCUREMENT_SCHEDULE_EXPORT'
  | 'PROCUREMENT_SUPPLY_EXPORT'
  | 'SALES_QUOTE_PRINT'
  | 'SALES_QUOTE_EXPORT'
  | 'SALES_DELIVERY_PLAN_EXPORT'
  | 'SALES_EFFECTIVE_DEMAND_EXPORT'
export type DocumentTaskDirection = 'IMPORT' | 'EXPORT' | 'PRINT'
export type DocumentTaskStage = 'VALIDATE' | 'COMMIT' | 'EXPORT' | 'PRINT'
export type DocumentTaskStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'READY_TO_COMMIT'
  | 'VALIDATION_FAILED'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED'
export type DocumentTaskAction =
  | 'CONFIRM'
  | 'CANCEL'
  | 'DOWNLOAD'
  | 'ERRORS'
export type BomDraftImportMode = 'CREATE' | 'UPDATE_DRAFT'

export interface SubmitApprovalPayload {
  version: number
  reason?: string
  idempotencyKey: string
}

export interface ApprovalActionPayload {
  version: number
  comment?: string
  idempotencyKey: string
}

export interface ApprovalTaskRecord {
  id: ResourceId
  taskId?: ResourceId | null
  taskNo?: string | null
  sceneCode: ApprovalSceneCode | string
  objectType: string
  objectId: ResourceId
  objectNo?: string | null
  objectName?: string | null
  status: ApprovalTaskStatus
  currentStepName?: string | null
  applicantName?: string | null
  assignedAt?: string | null
  completedAt?: string | null
  availableActions: ApprovalAction[]
  version: number
}

export interface ApprovalTaskListQuery {
  scope: ApprovalScope
  keyword?: string
  status?: ApprovalTaskStatus | ApprovalInstanceStatus
  page: number
  pageSize: number
}

export interface ApprovalStepRecord {
  stepName: string
  status: ApprovalTaskStatus
  taskId?: ResourceId | null
  version?: number | null
  candidatePermission?: string | null
  completedByName?: string | null
  completedAt?: string | null
}

export interface ApprovalHistoryRecord {
  action: string
  operatorName?: string | null
  operatedAt?: string | null
  comment?: string | null
  resultStatus?: ApprovalInstanceStatus | null
}

export interface ApprovalAttachmentSnapshot {
  attachmentId: ResourceId
  fileName: string
  fileSize?: number | null
  sha256?: string | null
}

export interface ApprovalInstanceDetail {
  id: ResourceId
  taskId?: ResourceId | null
  taskVersion?: number | null
  sceneCode: ApprovalSceneCode | string
  objectType: string
  objectId: ResourceId
  objectNo?: string | null
  objectName?: string | null
  status: ApprovalInstanceStatus
  applicantName?: string | null
  submittedAt?: string | null
  finishedAt?: string | null
  availableActions: ApprovalAction[]
  version: number
  steps: ApprovalStepRecord[]
  histories: ApprovalHistoryRecord[]
  attachmentSnapshots: ApprovalAttachmentSnapshot[]
}

export interface MessageRecord {
  id: ResourceId
  title: string
  content?: string | null
  status: MessageStatus
  category?: string | null
  messageType?: string | null
  relatedObjectType?: string | null
  relatedObjectId?: ResourceId | null
  businessRoute?: string | null
  createdAt?: string | null
  readAt?: string | null
  version: number
}

export interface MessagePageResult extends PageResult<MessageRecord> {
  unreadCount?: number
}

export interface MessageListQuery {
  unreadOnly?: boolean
  keyword?: string
  page: number
  pageSize: number
}

export interface AttachmentRecord {
  id: ResourceId
  objectType: AttachmentObjectType
  objectId: ResourceId
  fileName: string
  fileSize: number
  contentType?: string | null
  description?: string | null
  status: AttachmentStatus
  uploadedByName?: string | null
  uploadedAt?: string | null
  availableActions?: AttachmentAction[]
  restricted?: boolean
  restrictedMessage?: string | null
  version: number
}

export interface AttachmentUploadPayload {
  objectType: AttachmentObjectType
  objectId: ResourceId
  file: File
  description?: string
  idempotencyKey: string
}

export interface AttachmentDeletePayload {
  version: number
  reason: string
}

export interface DownloadedFile {
  blob: Blob
  fileName: string
}

export interface DocumentTaskRecord {
  id: ResourceId
  taskNo: string
  taskType: DocumentTaskType
  objectType?: string | null
  objectId?: ResourceId | null
  objectNo?: string | null
  objectName?: string | null
  direction: DocumentTaskDirection
  stage: DocumentTaskStage
  status: DocumentTaskStatus
  progressPercent?: number | null
  totalRows?: number | null
  successRows?: number | null
  failedRows?: number | null
  errorMessage?: string | null
  createdByName?: string | null
  createdAt?: string | null
  completedAt?: string | null
  expiresAt?: string | null
  availableActions?: DocumentTaskAction[]
  version: number
}

export interface DocumentTaskListQuery {
  keyword?: string
  taskType?: DocumentTaskType
  status?: DocumentTaskStatus
  page: number
  pageSize: number
}

export interface AttachmentListQuery {
  objectType: AttachmentObjectType
  objectId: ResourceId
  page: number
  pageSize: number
}

export interface MessageReadPayload {
  version: number
}

export interface MaterialExportPayload {
  keyword?: string
  status?: string
  categoryId?: ResourceId
  materialType?: string
  sourceType?: string
  trackingMethod?: string
  idempotencyKey: string
}

export interface ProcurementInquiryExportPayload {
  keyword?: string
  procurementMode?: string
  projectId?: ResourceId
  status?: string
  idempotencyKey: string
}

export interface ProcurementRequisitionExportPayload extends ProcurementInquiryExportPayload {
  approvalStatus?: string
  requiredDateFrom?: string
  requiredDateTo?: string
}

export interface ProcurementQuoteExportPayload {
  supplierId?: ResourceId
  status?: string
  idempotencyKey: string
}

export interface ProcurementPriceAgreementExportPayload {
  keyword?: string
  supplierId?: ResourceId
  materialId?: ResourceId
  procurementMode?: string
  projectId?: ResourceId
  status?: string
  idempotencyKey: string
}

export interface ProcurementOrderExportPayload {
  keyword?: string
  supplierId?: ResourceId
  status?: string
  procurementMode?: string
  projectId?: ResourceId
  dateFrom?: string
  dateTo?: string
  expectedDateFrom?: string
  expectedDateTo?: string
  idempotencyKey: string
}

export interface ProcurementScheduleExportPayload {
  status?: string
  expectedDateFrom?: string
  expectedDateTo?: string
  idempotencyKey: string
}

export interface ProcurementEffectiveSupplyExportPayload {
  projectId?: ResourceId
  materialId?: ResourceId
  supplierId?: ResourceId
  procurementMode?: string
  status?: string
  expectedDateFrom?: string
  expectedDateTo?: string
  countedOnly?: boolean
  idempotencyKey: string
}

export interface SalesQuoteExportPayload {
  keyword?: string
  customerId?: ResourceId
  projectId?: ResourceId
  status?: string
  approvalStatus?: string
  validFrom?: string
  validTo?: string
  idempotencyKey: string
}

export interface SalesDeliveryPlanExportPayload {
  keyword?: string
  customerId?: ResourceId
  projectId?: ResourceId
  contractId?: ResourceId
  orderId?: ResourceId
  materialId?: ResourceId
  status?: string
  expectedDateFrom?: string
  expectedDateTo?: string
  countedOnly?: boolean
  idempotencyKey: string
}

export interface SalesEffectiveDemandExportPayload {
  projectId?: ResourceId
  customerId?: ResourceId
  contractId?: ResourceId
  materialId?: ResourceId
  status?: string
  expectedDateFrom?: string
  expectedDateTo?: string
  countedOnly?: boolean
  idempotencyKey: string
}

export type ExportTaskType =
  | 'PROCUREMENT_REQUISITION_EXPORT'
  | 'PROCUREMENT_INQUIRY_EXPORT'
  | 'PROCUREMENT_QUOTE_EXPORT'
  | 'PROCUREMENT_PRICE_AGREEMENT_EXPORT'
  | 'PROCUREMENT_ORDER_EXPORT'
  | 'PROCUREMENT_SCHEDULE_EXPORT'
  | 'PROCUREMENT_SUPPLY_EXPORT'
  | 'SALES_QUOTE_EXPORT'
  | 'SALES_DELIVERY_PLAN_EXPORT'
  | 'SALES_EFFECTIVE_DEMAND_EXPORT'

export type ProcurementExportTaskType = Extract<ExportTaskType,
  | 'PROCUREMENT_REQUISITION_EXPORT'
  | 'PROCUREMENT_INQUIRY_EXPORT'
  | 'PROCUREMENT_QUOTE_EXPORT'
  | 'PROCUREMENT_PRICE_AGREEMENT_EXPORT'
  | 'PROCUREMENT_ORDER_EXPORT'
  | 'PROCUREMENT_SCHEDULE_EXPORT'
  | 'PROCUREMENT_SUPPLY_EXPORT'
>

export interface ImportFailureRecord {
  rowNo: number
  columnName?: string | null
  field?: string | null
  code: string
  message: string
  suggestion?: string | null
  rawValue?: string | null
}

export interface BomDraftImportPayload {
  mode: BomDraftImportMode
  bomId?: ResourceId
  version?: number
  file: File
  idempotencyKey: string
}

export interface ConfirmImportPayload {
  version: number
  idempotencyKey: string
}

export interface PrintTemplateRecord {
  templateCode: 'CONTRACT_ACTIVATION_APPROVAL_V1' | 'BOM_ECO_APPLICATION_APPROVAL_V1' | string
  name: string
  templateVersion: number
  sceneCode?: string | null
}

export interface PrintPreviewRecord {
  approvalInstanceId: ResourceId
  templateCode: string
  templateVersion: number
  sections: Array<{ title: string; fields: Array<{ label: string; value: string | null }> }>
}

export interface PrintTaskCreatePayload {
  approvalInstanceId?: ResourceId
  objectType?: string
  objectId?: ResourceId
  templateCode: string
  idempotencyKey: string
}

export interface DocumentPlatformApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export interface DocumentPlatformApi {
  approvals: {
    submitSalesProjectContractActivation(contractId: ResourceId, payload: SubmitApprovalPayload): Promise<ApprovalInstanceDetail>
    submitBomEcoApplication(ecoId: ResourceId, payload: SubmitApprovalPayload): Promise<ApprovalInstanceDetail>
    get(id: ResourceId): Promise<ApprovalInstanceDetail>
    withdraw(id: ResourceId, payload: ApprovalActionPayload): Promise<ApprovalInstanceDetail>
    cancel(id: ResourceId, payload: ApprovalActionPayload): Promise<ApprovalInstanceDetail>
  }
  approvalTasks: {
    list(query: ApprovalTaskListQuery): Promise<PageResult<ApprovalTaskRecord>>
    approve(taskId: ResourceId, payload: ApprovalActionPayload): Promise<ApprovalInstanceDetail>
    reject(taskId: ResourceId, payload: ApprovalActionPayload): Promise<ApprovalInstanceDetail>
  }
  messages: {
    listMine(query: MessageListQuery): Promise<MessagePageResult>
    markRead(id: ResourceId, payload: MessageReadPayload): Promise<MessageRecord>
    markAllRead(): Promise<{ updatedCount: number }>
  }
  attachments: {
    list(query: AttachmentListQuery): Promise<PageResult<AttachmentRecord>>
    upload(payload: AttachmentUploadPayload): Promise<AttachmentRecord>
    download(id: ResourceId): Promise<DownloadedFile>
    delete(id: ResourceId, payload: AttachmentDeletePayload): Promise<AttachmentRecord>
  }
  importTemplates: {
    downloadMaterials(): Promise<DownloadedFile>
    downloadBomDrafts(): Promise<DownloadedFile>
  }
  imports: {
    uploadMaterials(payload: { file: File; idempotencyKey: string }): Promise<DocumentTaskRecord>
    uploadBomDraft(payload: BomDraftImportPayload): Promise<DocumentTaskRecord>
    uploadProcurementQuotes(inquiryId: ResourceId, payload: { file: File; idempotencyKey: string }): Promise<DocumentTaskRecord>
    confirm(taskId: ResourceId, payload: ConfirmImportPayload): Promise<DocumentTaskRecord>
  }
  exports: {
    createMaterials(payload: MaterialExportPayload): Promise<DocumentTaskRecord>
    createBomDraft(bomId: ResourceId, payload: { idempotencyKey: string }): Promise<DocumentTaskRecord>
    createProcurementRequisitions(payload: ProcurementRequisitionExportPayload): Promise<DocumentTaskRecord>
    createProcurementInquiries(payload: ProcurementInquiryExportPayload): Promise<DocumentTaskRecord>
    createProcurementQuotes(inquiryId: ResourceId, payload: ProcurementQuoteExportPayload): Promise<DocumentTaskRecord>
    createProcurementPriceAgreements(payload: ProcurementPriceAgreementExportPayload): Promise<DocumentTaskRecord>
    createProcurementOrders(payload: ProcurementOrderExportPayload): Promise<DocumentTaskRecord>
    createProcurementSchedules(orderId: ResourceId, payload: ProcurementScheduleExportPayload): Promise<DocumentTaskRecord>
    createProcurementEffectiveSupplies(payload: ProcurementEffectiveSupplyExportPayload): Promise<DocumentTaskRecord>
    createSalesQuotes(payload: SalesQuoteExportPayload): Promise<DocumentTaskRecord>
    createSalesDeliveryPlans(payload: SalesDeliveryPlanExportPayload): Promise<DocumentTaskRecord>
    createSalesEffectiveDemands(payload: SalesEffectiveDemandExportPayload): Promise<DocumentTaskRecord>
  }
  printTemplates: {
    list(query: { sceneCode: string }): Promise<PrintTemplateRecord[]>
  }
  printPreviews: {
    get(approvalInstanceId: ResourceId): Promise<PrintPreviewRecord>
  }
  printTasks: {
    create(payload: PrintTaskCreatePayload): Promise<DocumentTaskRecord>
    createSalesQuote(quoteId: ResourceId, payload: { idempotencyKey: string }): Promise<DocumentTaskRecord>
  }
  documentTasks: {
    list(query: DocumentTaskListQuery): Promise<PageResult<DocumentTaskRecord>>
    get(id: ResourceId): Promise<DocumentTaskRecord>
    errors(id: ResourceId, query: { page: number; pageSize: number }): Promise<PageResult<ImportFailureRecord>>
    cancel(id: ResourceId, payload: { version: number; reason?: string }): Promise<DocumentTaskRecord>
    download(id: ResourceId): Promise<DownloadedFile>
  }
}

export function createIdempotencyKey(prefix = 'web'): string {
  const random = globalThis.crypto?.randomUUID?.()
  return `${prefix}-${random ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`}`
}

export function createDocumentPlatformApi(options: DocumentPlatformApiOptions = {}): DocumentPlatformApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')

  const buildUrl = (path: string, query?: object) => {
    const search = new URLSearchParams()
    Object.entries(query ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        search.set(key, String(value))
      }
    })
    const queryString = search.toString()
    return `${baseUrl}${path}${queryString ? `?${queryString}` : ''}`
  }

  const request = async <T>(path: string, init: RequestInit, query?: object): Promise<T> => {
    const response = await fetcher(buildUrl(path, query), {
      credentials: 'include',
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.headers ?? {}),
      },
    })
    const envelope = (await response.json()) as ApiEnvelope<T>
    if (!response.ok || !envelope.success) {
      throw new AccountPermissionApiError(
        envelope.message || `请求失败：${response.status}`,
        envelope.code || 'HTTP_ERROR',
        response.status,
        envelope.traceId,
      )
    }
    return envelope.data
  }

  const getCsrf = () => request<CsrfToken>('/api/auth/csrf', { method: 'GET' })
  const get = <T>(path: string, query?: object) => request<T>(path, { method: 'GET' }, query)

  const writeJson = async <T>(
    method: 'POST' | 'PUT',
    path: string,
    body?: object,
    extraHeaders: Record<string, string> = {},
  ) => {
    const csrf = await getCsrf()
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
      ...extraHeaders,
    }
    return request<T>(path, {
      body: body === undefined ? undefined : JSON.stringify(body),
      headers,
      method,
    })
  }

  const writeForm = async <T>(path: string, formData: FormData, idempotencyKey?: string) => {
    const csrf = await getCsrf()
    return request<T>(path, {
      body: formData,
      headers: {
        [csrf.headerName]: csrf.token,
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      },
      method: 'POST',
    })
  }

  const download = async (path: string, query?: object): Promise<DownloadedFile> => {
    const response = await fetcher(buildUrl(path, query), {
      credentials: 'include',
      headers: { Accept: '*/*' },
      method: 'GET',
    })
    if (!response.ok) {
      await throwEnvelopeError(response)
    }
    const blob = await response.blob()
    return {
      blob,
      fileName: fileNameFromContentDisposition(response.headers.get('content-disposition')) ?? '下载文件',
    }
  }

  const createExportTask = (
    taskType: ExportTaskType,
    filters: Record<string, unknown>,
    idempotencyKey: string,
    context?: { objectType?: string; objectId?: ResourceId },
  ) =>
    writeJson<DocumentTaskRecord>(
      'POST',
      '/api/admin/export-tasks',
      {
        taskType,
        ...(context?.objectType ? { objectType: context.objectType } : {}),
        ...(context?.objectId !== undefined ? { objectId: context.objectId } : {}),
        filters,
      },
      { 'Idempotency-Key': idempotencyKey },
    )

  return {
    approvals: {
      submitSalesProjectContractActivation: (contractId, payload) =>
        writeJson<ApprovalInstanceDetail>(
          'POST',
          `/api/admin/approvals/sales-project-contract-activation/${encodeURIComponent(String(contractId))}/submit`,
          payload,
        ),
      submitBomEcoApplication: (ecoId, payload) =>
        writeJson<ApprovalInstanceDetail>(
          'POST',
          `/api/admin/approvals/bom-eco-application/${encodeURIComponent(String(ecoId))}/submit`,
          payload,
        ),
      get: (id) => get<ApprovalInstanceDetail>(`/api/admin/approvals/${encodeURIComponent(String(id))}`),
      withdraw: (id, payload) =>
        writeJson<ApprovalInstanceDetail>('POST', `/api/admin/approvals/${encodeURIComponent(String(id))}/withdraw`, payload),
      cancel: (id, payload) =>
        writeJson<ApprovalInstanceDetail>('POST', `/api/admin/approvals/${encodeURIComponent(String(id))}/cancel`, payload),
    },
    approvalTasks: {
      list: (query) => get<PageResult<ApprovalTaskRecord>>('/api/admin/approval-tasks', query),
      approve: (taskId, payload) =>
        writeJson<ApprovalInstanceDetail>('POST', `/api/admin/approval-tasks/${encodeURIComponent(String(taskId))}/approve`, payload),
      reject: (taskId, payload) =>
        writeJson<ApprovalInstanceDetail>('POST', `/api/admin/approval-tasks/${encodeURIComponent(String(taskId))}/reject`, payload),
    },
    messages: {
      listMine: (query) => get<MessagePageResult>('/api/admin/messages/my', query),
      markRead: (id, payload) =>
        writeJson<MessageRecord>('PUT', `/api/admin/messages/${encodeURIComponent(String(id))}/read`, payload),
      markAllRead: () => writeJson<{ updatedCount: number }>('PUT', '/api/admin/messages/read-all'),
    },
    attachments: {
      list: (query) => get<PageResult<AttachmentRecord>>('/api/admin/attachments', query),
      upload: (payload) => {
        const formData = new FormData()
        formData.append('objectType', payload.objectType)
        formData.append('objectId', String(payload.objectId))
        formData.append('file', payload.file)
        if (payload.description) {
          formData.append('description', payload.description)
        }
        return writeForm<AttachmentRecord>('/api/admin/attachments', formData, payload.idempotencyKey)
      },
      download: (id) => download(`/api/admin/attachments/${encodeURIComponent(String(id))}/download`),
      delete: (id, payload) =>
        writeJson<AttachmentRecord>('PUT', `/api/admin/attachments/${encodeURIComponent(String(id))}/delete`, payload),
    },
    importTemplates: {
      downloadMaterials: () => download('/api/admin/import-templates/materials'),
      downloadBomDrafts: () => download('/api/admin/import-templates/bom-drafts'),
    },
    imports: {
      uploadMaterials: (payload) => {
        const formData = new FormData()
        formData.append('file', payload.file)
        return writeForm<DocumentTaskRecord>('/api/admin/imports/materials', formData, payload.idempotencyKey)
      },
      uploadBomDraft: (payload) => {
        const formData = new FormData()
        formData.append('mode', payload.mode)
        if (payload.bomId !== undefined) {
          formData.append('bomId', String(payload.bomId))
        }
        if (payload.version !== undefined) {
          formData.append('version', String(payload.version))
        }
        formData.append('file', payload.file)
        return writeForm<DocumentTaskRecord>('/api/admin/imports/bom-drafts', formData, payload.idempotencyKey)
      },
      uploadProcurementQuotes: (inquiryId, payload) => {
        const formData = new FormData()
        formData.append('file', payload.file)
        return writeForm<DocumentTaskRecord>(
          `/api/admin/procurement/inquiries/${encodeURIComponent(String(inquiryId))}/quote-imports`,
          formData,
          payload.idempotencyKey,
        )
      },
      confirm: (taskId, payload) =>
        writeJson<DocumentTaskRecord>(
          'POST',
          `/api/admin/imports/${encodeURIComponent(String(taskId))}/confirm`,
          { version: payload.version },
          { 'Idempotency-Key': payload.idempotencyKey },
        ),
    },
    exports: {
      createMaterials: (payload) => {
        const { idempotencyKey, ...filters } = payload
        return writeJson<DocumentTaskRecord>(
          'POST',
          '/api/admin/exports/materials',
          filters,
          { 'Idempotency-Key': idempotencyKey },
        )
      },
      createBomDraft: (bomId, payload) =>
        writeJson<DocumentTaskRecord>(
          'POST',
          `/api/admin/exports/bom-drafts/${encodeURIComponent(String(bomId))}`,
          undefined,
          { 'Idempotency-Key': payload.idempotencyKey },
        ),
      createProcurementInquiries: (payload) => {
        const { idempotencyKey, ...filters } = payload
        return createExportTask('PROCUREMENT_INQUIRY_EXPORT', filters, idempotencyKey)
      },
      createProcurementRequisitions: (payload) => {
        const { idempotencyKey, ...filters } = payload
        return createExportTask('PROCUREMENT_REQUISITION_EXPORT', filters, idempotencyKey)
      },
      createProcurementQuotes: (inquiryId, payload) => {
        const { idempotencyKey, ...filters } = payload
        return createExportTask('PROCUREMENT_QUOTE_EXPORT', filters, idempotencyKey, {
          objectType: 'PROCUREMENT_INQUIRY',
          objectId: inquiryId,
        })
      },
      createProcurementPriceAgreements: (payload) => {
        const { idempotencyKey, ...filters } = payload
        return createExportTask('PROCUREMENT_PRICE_AGREEMENT_EXPORT', filters, idempotencyKey)
      },
      createProcurementOrders: (payload) => {
        const { idempotencyKey, ...filters } = payload
        return createExportTask('PROCUREMENT_ORDER_EXPORT', filters, idempotencyKey)
      },
      createProcurementSchedules: (orderId, payload) => {
        const { idempotencyKey, ...filters } = payload
        return createExportTask('PROCUREMENT_SCHEDULE_EXPORT', { orderId, ...filters }, idempotencyKey, {
          objectType: 'PROCUREMENT_ORDER',
          objectId: orderId,
        })
      },
      createProcurementEffectiveSupplies: (payload) => {
        const { idempotencyKey, ...filters } = payload
        return createExportTask('PROCUREMENT_SUPPLY_EXPORT', filters, idempotencyKey)
      },
      createSalesQuotes: (payload) => {
        const { idempotencyKey, ...filters } = payload
        return createExportTask('SALES_QUOTE_EXPORT', filters, idempotencyKey)
      },
      createSalesDeliveryPlans: (payload) => {
        const { idempotencyKey, ...filters } = payload
        return createExportTask('SALES_DELIVERY_PLAN_EXPORT', filters, idempotencyKey)
      },
      createSalesEffectiveDemands: (payload) => {
        const { idempotencyKey, ...filters } = payload
        return createExportTask('SALES_EFFECTIVE_DEMAND_EXPORT', filters, idempotencyKey)
      },
    },
    printTemplates: {
      list: (query) => get<PrintTemplateRecord[]>('/api/admin/print-templates', query),
    },
    printPreviews: {
      get: (approvalInstanceId) =>
        get<PrintPreviewRecord>(`/api/admin/print-previews/${encodeURIComponent(String(approvalInstanceId))}`),
    },
    printTasks: {
      create: (payload) =>
        writeJson<DocumentTaskRecord>(
          'POST',
          '/api/admin/print-tasks',
          {
            approvalInstanceId: payload.approvalInstanceId,
            objectType: payload.objectType,
            objectId: payload.objectId,
            templateCode: payload.templateCode,
          },
          { 'Idempotency-Key': payload.idempotencyKey },
        ),
      createSalesQuote: (quoteId, payload) =>
        writeJson<DocumentTaskRecord>(
          'POST',
          '/api/admin/print-tasks',
          {
            objectType: 'SALES_QUOTE',
            objectId: quoteId,
            templateCode: 'SALES_QUOTE_V1',
          },
          { 'Idempotency-Key': payload.idempotencyKey },
        ),
    },
    documentTasks: {
      list: (query) => get<PageResult<DocumentTaskRecord>>('/api/admin/document-tasks', query),
      get: (id) => get<DocumentTaskRecord>(`/api/admin/document-tasks/${encodeURIComponent(String(id))}`),
      errors: (id, query) =>
        get<PageResult<ImportFailureRecord>>(`/api/admin/document-tasks/${encodeURIComponent(String(id))}/errors`, query),
      cancel: (id, payload) =>
        writeJson<DocumentTaskRecord>('POST', `/api/admin/document-tasks/${encodeURIComponent(String(id))}/cancel`, payload),
      download: (id) => download(`/api/admin/document-tasks/${encodeURIComponent(String(id))}/download`),
    },
  }
}

async function throwEnvelopeError(response: Response): Promise<never> {
  try {
    const envelope = (await response.json()) as ApiEnvelope<unknown>
    throw new AccountPermissionApiError(
      envelope.message || `请求失败：${response.status}`,
      envelope.code || 'HTTP_ERROR',
      response.status,
      envelope.traceId,
    )
  } catch (error) {
    if (error instanceof AccountPermissionApiError) {
      throw error
    }
    throw new AccountPermissionApiError(`请求失败：${response.status}`, 'HTTP_ERROR', response.status)
  }
}

function fileNameFromContentDisposition(contentDisposition: string | null): string | null {
  if (!contentDisposition) {
    return null
  }
  const encodedMatch = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition)
  if (encodedMatch?.[1]) {
    return decodeURIComponent(encodedMatch[1].trim().replace(/^"|"$/g, ''))
  }
  const plainMatch = /filename="?([^";]+)"?/i.exec(contentDisposition)
  return plainMatch?.[1] ? plainMatch[1].trim() : null
}

export const documentPlatformApi = createDocumentPlatformApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
