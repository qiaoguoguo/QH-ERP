import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type { DownloadedFile, Fetcher, ResourceId } from './documentPlatformApi'

export type DataRepairStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'READY_TO_EXECUTE'
  | 'EXECUTING'
  | 'EXECUTED'
  | 'VERIFIED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'FAILED'
  | 'VERIFY_FAILED'
export type DataRepairAction = 'SUBMIT' | 'EXECUTE' | 'VERIFY' | 'CANCEL' | 'DOWNLOAD' | 'VIEW_AUDIT'
export type DataRepairRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type DataRepairCheckStage = 'PRECHECK' | 'EXECUTE' | 'VERIFY'
export type DataRepairCheckStatus = 'PASSED' | 'FAILED' | 'WARNING'
export type HistoryImportStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'READY_TO_COMMIT'
  | 'VALIDATION_FAILED'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED'
export type HistoryImportAction = 'CONFIRM' | 'CANCEL' | 'DOWNLOAD' | 'ERRORS'
export type BatchOperationStatus =
  | 'PRECHECKED'
  | 'PRECHECK_FAILED'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED'
export type BatchOperationAction = 'EXECUTE' | 'CANCEL' | 'ERRORS'

export interface PlatformGovernanceApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export interface DataRepairAdapterRecord {
  adapterCode: string
  name: string
  targetObjectType: string
  description?: string | null
  allowedFields?: string[]
  requiredPermissionCode?: string | null
  requiredPermission?: string | null
  version?: number | null
}

export type DataRepairSummaryMap = Record<string, unknown>

export interface DataRepairCheckRecord {
  checkType: DataRepairCheckStage | string
  status: DataRepairCheckStatus | string
  code?: string | null
  message?: string | null
  detail?: Record<string, unknown> | null
  createdAt?: string | null
}

export interface DataRepairEventRecord {
  eventType: string
  operatorUsername?: string | null
  statusBefore?: string | null
  statusAfter?: string | null
  detail?: Record<string, unknown> | null
  createdAt?: string | null
}

export interface PlatformApprovalSummary {
  id?: ResourceId | null
  status?: string | null
  version?: number | null
  taskId?: ResourceId | null
  taskVersion?: number | null
}

export interface PlatformAuditSummary {
  auditLogId?: ResourceId | null
  summary?: string | null
}

export interface DataRepairRecord {
  id: ResourceId
  requestNo: string
  adapterCode: string
  targetObjectType: string
  targetObjectId: ResourceId
  targetObjectNo?: string | null
  targetObjectSummary?: string | null
  targetObjectVersion?: number | null
  reason?: string | null
  riskSummary?: string | null
  beforeSummary?: DataRepairSummaryMap | null
  afterSummary?: DataRepairSummaryMap | null
  requestFingerprint?: string | null
  changes?: DataRepairChangeRecord[]
  status: DataRepairStatus | string
  createdByUserId?: ResourceId | null
  createdByUsername?: string | null
  submittedByUsername?: string | null
  createdAt?: string | null
  submittedAt?: string | null
  executedByUsername?: string | null
  executedAt?: string | null
  verifiedByUsername?: string | null
  verifiedAt?: string | null
  errorSummary?: string | null
  updatedAt?: string | null
  version: number
  availableActions?: DataRepairAction[] | string[]
  repairNo?: string
  targetObjectName?: string | null
  title?: string
  riskLevel?: DataRepairRiskLevel | string | null
  approvalStatus?: string | null
  applicantName?: string | null
}

export interface DataRepairChangeRecord {
  fieldName: string
  beforeValueSummary?: string | null
  afterValueSummary?: string | null
}

export interface DataRepairDetail extends DataRepairRecord {
  checks?: DataRepairCheckRecord[]
  events?: DataRepairEventRecord[]
  approvalSummary?: PlatformApprovalSummary | null
  auditSummary?: PlatformAuditSummary | null
  relatedDocumentTaskIds?: ResourceId[]
  attachmentObjectType?: string | null
  attachmentObjectId?: ResourceId | null
}

export interface DataRepairListQuery {
  adapterCode?: string
  keyword?: string
  targetObjectType?: string
  status?: DataRepairStatus | string
  page: number
  pageSize: number
}

export interface DataRepairCreatePayload {
  adapterCode: string
  targetObjectType: string
  targetObjectId: ResourceId
  targetVersion: number
  reason: string
  riskSummary?: string | null
  changes: Array<{ fieldName: string; afterValue: unknown }>
  idempotencyKey: string
}

export interface DataRepairUpdatePayload {
  version: number
  reason?: string
  riskSummary?: string | null
  changes?: Array<{ fieldName: string; afterValue: unknown }>
  idempotencyKey: string
}

export interface GovernanceVersionedActionPayload {
  version: number
  comment?: string
  reason?: string
  passed?: boolean
  idempotencyKey: string
}

export interface HistoryImportAdapterRecord {
  adapterCode: string
  name: string
  targetObjectType?: string | null
  templateCode?: string | null
  templateVersion: number
  description?: string | null
  maxRows?: number | null
  requiredPermissionCode?: string | null
  requiredPermission?: string | null
  version?: number | null
}

export interface HistoryImportValidationSummary {
  totalRows?: number | null
  successRows?: number | null
  failedRows?: number | null
  summary?: string | null
}

export interface HistoryImportErrorSummary {
  totalErrors?: number | null
  summary?: string | null
}

export interface HistoryImportRecord {
  id: ResourceId
  taskId?: ResourceId | null
  taskNo: string
  adapterCode: string
  targetObjectType?: string | null
  adapterName?: string | null
  sourceFileName?: string | null
  sourceSha256?: string | null
  templateCode?: string | null
  templateVersion?: number | null
  status: HistoryImportStatus | string
  stage?: string | null
  progressPercent?: number | null
  totalRows?: number | null
  successRows?: number | null
  failedRows?: number | null
  errorMessage?: string | null
  createdByName?: string | null
  createdAt?: string | null
  precheckedAt?: string | null
  confirmedAt?: string | null
  completedAt?: string | null
  expiresAt?: string | null
  version: number
  availableActions?: HistoryImportAction[]
}

export interface HistoryImportDetail extends HistoryImportRecord {
  validationSummary?: HistoryImportValidationSummary | null
  errorSummary?: HistoryImportErrorSummary | null
  auditSummary?: PlatformAuditSummary | null
  relatedTaskId?: ResourceId | null
}

export interface HistoryImportListQuery {
  adapterCode?: string
  keyword?: string
  status?: string
  page: number
  pageSize: number
}

export interface HistoryImportUploadPayload {
  file: File
  idempotencyKey: string
}

export interface HistoryImportErrorRecord {
  rowNo?: number | null
  columnName?: string | null
  field?: string | null
  code?: string | null
  errorCode?: string | null
  message: string
  suggestion?: string | null
  rawValue?: string | null
}

export interface BatchToolRecord {
  toolCode: string
  name: string
  targetObjectType: string
  actionCode?: string | null
  maxItems?: number | null
  requiredPermissionCode?: string | null
  description?: string | null
  enabled?: boolean
  version?: number | null
}

export interface BatchToolTargetPayload {
  targetObjectId: ResourceId
  version: number
}

export interface BatchToolPreviewPayload {
  actionCode: string
  targetStatus: 'ENABLED' | 'DISABLED' | string
  reason?: string | null
  targets: BatchToolTargetPayload[]
  idempotencyKey: string
}

export interface BatchOperationItemRecord {
  lineNo: number
  targetObjectType: string
  targetObjectId?: ResourceId | null
  targetObjectNo?: string | null
  targetObjectSummary?: string | null
  targetObjectVersion?: number | null
  status: string
  message?: string | null
}

export interface BatchOperationRecord {
  id: ResourceId
  operationNo: string
  toolCode: string
  targetObjectType: string
  actionCode: string
  status: BatchOperationStatus | string
  totalRows: number
  successRows: number
  failedRows: number
  errorMessage?: string | null
  createdByName?: string | null
  executedByName?: string | null
  executedAt?: string | null
  createdAt?: string | null
  version: number
  availableActions?: BatchOperationAction[] | string[]
  items?: BatchOperationItemRecord[]
}

export interface DeliveryAssetTemplateRecord {
  templateCode: string
  name: string
  templateVersion: number
  objectType?: string | null
  sceneCode?: string | null
  status?: string | null
  enabled?: boolean
}

export interface DeliveryAssetAdapterRecord {
  code: string
  name: string
  targetObjectType?: string | null
  status?: string | null
  version?: number | null
}

export interface DeliveryAssetBatchToolRecord {
  code: string
  name: string
  targetObjectType?: string | null
  actionCode?: string | null
  status?: string | null
  version?: number | null
}

export interface DeliveryStaticAssetRecord {
  code: string
  path: string
  note?: string | null
}

export interface DeliveryAssetRecord {
  stageCode: string
  generatedAt: string
  historyImportAdapters: DeliveryAssetAdapterRecord[]
  dataRepairAdapters: DeliveryAssetAdapterRecord[]
  batchTools: DeliveryAssetBatchToolRecord[]
  printTemplates: DeliveryAssetTemplateRecord[]
  staticAssets: DeliveryStaticAssetRecord[]
  releaseVersion?: string
  environmentCode?: string | null
  templates?: DeliveryAssetTemplateRecord[]
  importAdapters?: Array<Pick<HistoryImportAdapterRecord, 'adapterCode' | 'name' | 'templateVersion' | 'maxRows'>>
  manual?: { version?: string | null; updatedAt?: string | null } | null
  demoData?: { version?: string | null; status?: string | null; verifiedAt?: string | null } | null
}

export interface PlatformGovernanceApi {
  dataRepairAdapters: {
    list(): Promise<DataRepairAdapterRecord[]>
  }
  dataRepairs: {
    list(query: DataRepairListQuery): Promise<PageResult<DataRepairRecord>>
    get(id: ResourceId): Promise<DataRepairDetail>
    create(payload: DataRepairCreatePayload): Promise<DataRepairDetail>
    update(id: ResourceId, payload: DataRepairUpdatePayload): Promise<DataRepairDetail>
    submit(id: ResourceId, payload: GovernanceVersionedActionPayload): Promise<DataRepairDetail>
    execute(id: ResourceId, payload: GovernanceVersionedActionPayload): Promise<DataRepairDetail>
    verify(id: ResourceId, payload: GovernanceVersionedActionPayload): Promise<DataRepairDetail>
    cancel(id: ResourceId, payload: GovernanceVersionedActionPayload): Promise<DataRepairDetail>
  }
  historyImportAdapters: {
    list(): Promise<HistoryImportAdapterRecord[]>
    downloadTemplate(code: string): Promise<DownloadedFile>
  }
  historyImports: {
    list(query: HistoryImportListQuery): Promise<PageResult<HistoryImportRecord>>
    upload(code: string, payload: HistoryImportUploadPayload): Promise<HistoryImportDetail>
    get(taskId: ResourceId): Promise<HistoryImportDetail>
    confirm(taskId: ResourceId, payload: GovernanceVersionedActionPayload): Promise<HistoryImportDetail>
    cancel(taskId: ResourceId, payload: GovernanceVersionedActionPayload): Promise<HistoryImportDetail>
    errors(taskId: ResourceId, query: { page: number; pageSize: number }): Promise<PageResult<HistoryImportErrorRecord>>
  }
  batchTools: {
    list(): Promise<BatchToolRecord[]>
    preview(code: string, payload: BatchToolPreviewPayload): Promise<BatchOperationRecord>
  }
  batchOperations: {
    list(query: { status?: string; page: number; pageSize: number }): Promise<PageResult<BatchOperationRecord>>
    get(id: ResourceId): Promise<BatchOperationRecord>
    execute(id: ResourceId, payload: { version: number; idempotencyKey: string }): Promise<BatchOperationRecord>
  }
  deliveryAssets: {
    get(): Promise<DeliveryAssetRecord>
  }
}

export function createPlatformGovernanceApi(options: PlatformGovernanceApiOptions = {}): PlatformGovernanceApi {
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
    body: object | undefined,
    idempotencyKey?: string,
  ) => {
    const csrf = await getCsrf()
    return request<T>(path, {
      body: body === undefined ? undefined : JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        [csrf.headerName]: csrf.token,
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      },
      method,
    })
  }

  const writeVersionedAction = <T>(path: string, payload: GovernanceVersionedActionPayload) => {
    return writeJson<T>('POST', path, payload, payload.idempotencyKey)
  }

  const writeForm = async <T>(path: string, formData: FormData, idempotencyKey: string) => {
    const csrf = await getCsrf()
    return request<T>(path, {
      body: formData,
      headers: {
        [csrf.headerName]: csrf.token,
        'Idempotency-Key': idempotencyKey,
      },
      method: 'POST',
    })
  }

  const download = async (path: string): Promise<DownloadedFile> => {
    const response = await fetcher(buildUrl(path), {
      credentials: 'include',
      headers: { Accept: '*/*' },
      method: 'GET',
    })
    if (!response.ok) {
      await throwEnvelopeError(response)
    }
    return {
      blob: await response.blob(),
      fileName: fileNameFromContentDisposition(response.headers.get('content-disposition')) ?? '下载文件',
    }
  }

  return {
    dataRepairAdapters: {
      list: () => get<DataRepairAdapterRecord[]>('/api/admin/platform/data-repair-adapters'),
    },
    dataRepairs: {
      list: (query) => get<PageResult<DataRepairRecord>>('/api/admin/platform/data-repairs', query),
      get: (id) => get<DataRepairDetail>(`/api/admin/platform/data-repairs/${encodeURIComponent(String(id))}`),
      create: (payload) => {
        const { idempotencyKey, ...body } = payload
        return writeJson<DataRepairDetail>('POST', '/api/admin/platform/data-repairs', body, idempotencyKey)
      },
      update: (id, payload) =>
        writeJson<DataRepairDetail>(
          'PUT',
          `/api/admin/platform/data-repairs/${encodeURIComponent(String(id))}`,
          payload,
          payload.idempotencyKey,
        ),
      submit: (id, payload) =>
        writeVersionedAction<DataRepairDetail>(`/api/admin/platform/data-repairs/${encodeURIComponent(String(id))}/submit`, payload),
      execute: (id, payload) =>
        writeVersionedAction<DataRepairDetail>(`/api/admin/platform/data-repairs/${encodeURIComponent(String(id))}/execute`, payload),
      verify: (id, payload) =>
        writeVersionedAction<DataRepairDetail>(`/api/admin/platform/data-repairs/${encodeURIComponent(String(id))}/verify`, payload),
      cancel: (id, payload) =>
        writeVersionedAction<DataRepairDetail>(`/api/admin/platform/data-repairs/${encodeURIComponent(String(id))}/cancel`, payload),
    },
    historyImportAdapters: {
      list: () => get<HistoryImportAdapterRecord[]>('/api/admin/platform/history-import-adapters'),
      downloadTemplate: (code) =>
        download(`/api/admin/platform/history-import-adapters/${encodeURIComponent(code)}/template`),
    },
    historyImports: {
      list: (query) => get<PageResult<HistoryImportRecord>>('/api/admin/platform/history-imports', query),
      upload: (code, payload) => {
        const formData = new FormData()
        formData.append('file', payload.file)
        return writeForm<HistoryImportDetail>(
          `/api/admin/platform/history-imports/${encodeURIComponent(code)}`,
          formData,
          payload.idempotencyKey,
        )
      },
      get: (taskId) => get<HistoryImportDetail>(`/api/admin/platform/history-imports/${encodeURIComponent(String(taskId))}`),
      confirm: (taskId, payload) =>
        writeJson<HistoryImportDetail>(
          'POST',
          `/api/admin/platform/history-imports/${encodeURIComponent(String(taskId))}/confirm`,
          { version: payload.version, idempotencyKey: payload.idempotencyKey },
          payload.idempotencyKey,
        ),
      cancel: (taskId, payload) =>
        writeJson<HistoryImportDetail>(
          'POST',
          `/api/admin/platform/history-imports/${encodeURIComponent(String(taskId))}/cancel`,
          { version: payload.version, idempotencyKey: payload.idempotencyKey },
          payload.idempotencyKey,
        ),
      errors: (taskId, query) =>
        get<PageResult<HistoryImportErrorRecord>>(
          `/api/admin/document-tasks/${encodeURIComponent(String(taskId))}/errors`,
          query,
        ),
    },
    batchTools: {
      list: () => get<BatchToolRecord[]>('/api/admin/platform/batch-tools'),
      preview: (code, payload) =>
        writeJson<BatchOperationRecord>(
          'POST',
          `/api/admin/platform/batch-tools/${encodeURIComponent(code)}/preview`,
          payload,
          payload.idempotencyKey,
        ),
    },
    batchOperations: {
      list: (query) => get<PageResult<BatchOperationRecord>>('/api/admin/platform/batch-operations', query),
      get: (id) => get<BatchOperationRecord>(`/api/admin/platform/batch-operations/${encodeURIComponent(String(id))}`),
      execute: (id, payload) =>
        writeJson<BatchOperationRecord>(
          'POST',
          `/api/admin/platform/batch-operations/${encodeURIComponent(String(id))}/execute`,
          payload,
          payload.idempotencyKey,
        ),
    },
    deliveryAssets: {
      get: () => get<DeliveryAssetRecord>('/api/admin/platform/delivery-assets'),
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

export const platformGovernanceApi = createPlatformGovernanceApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
