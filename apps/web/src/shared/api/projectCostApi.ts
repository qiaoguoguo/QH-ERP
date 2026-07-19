import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type ProjectCostAmount = string

export type ProjectCostCalculationStatus = 'DRAFT' | 'CALCULATED' | 'CONFIRMED' | 'CANCELLED'
export type ProjectCostFreshnessStatus = 'CURRENT' | 'STALE'
export type ProjectCostCompletenessStatus = 'COMPLETE' | 'INCOMPLETE'
export type ProjectCostCategory =
  | 'MATERIAL'
  | 'LABOR'
  | 'OUTSOURCING'
  | 'MANUFACTURING_OVERHEAD'
  | 'PROJECT_EXPENSE'
  | 'ADJUSTMENT'
export type ProjectCostStage = 'WIP' | 'FINISHED' | 'DELIVERED' | 'DIRECT_PROJECT'
export type ProjectCostSourceStatus = 'ACTUAL' | 'PROVISIONAL' | 'UNPRICED' | 'ADJUSTED' | 'RESTRICTED' | 'EXCLUDED'
export type ProjectCostVarianceSeverity = 'INFO' | 'WARNING' | 'BLOCKING'
export type ProjectCostVarianceStatus = 'OPEN' | 'RESOLVED' | 'SUPERSEDED'
export type ProjectCostAdjustmentStatus = 'DRAFT' | 'SUBMITTED' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'
export type ProjectCostAdjustmentType = 'MANUAL_ADJUSTMENT' | 'PUBLIC_EXPENSE_ALLOCATION' | 'REVERSAL'
export type ProjectCostAdjustmentDirection = 'INCREASE' | 'DECREASE'

export interface ProjectCostListParams {
  keyword?: string | null
  ownerUserId?: ResourceId | null
  projectStatus?: string | null
  freshnessStatus?: ProjectCostFreshnessStatus | null
  varianceStatus?: ProjectCostVarianceStatus | null
  completenessStatus?: ProjectCostCompletenessStatus | null
  cutoffDateFrom?: string | null
  cutoffDateTo?: string | null
  page: number
  pageSize: number
}

export interface ProjectCostCalculationCreatePayload {
  cutoffDate: string
  idempotencyKey: string
}

export interface ProjectCostActionPayload {
  version: number
  sourceFingerprint?: string | null
  idempotencyKey: string
}

export interface ProjectCostVisibility {
  amountVisible: boolean
  sourceVisible: boolean
  restrictedReason?: string | null
}

export interface ProjectCostActionState {
  version: number
  allowedActions: string[]
  actionDisabledReasons: Record<string, string>
  sourceFingerprint?: string | null
}

export interface ProjectCostWorkbenchRecord extends ProjectCostVisibility, ProjectCostActionState {
  projectId: ResourceId
  projectNo: string
  projectName: string
  customerName?: string | null
  ownerDisplayName?: string | null
  projectStatus: string
  calculationId?: ResourceId | null
  calculationNo?: string | null
  calculationStatus: ProjectCostCalculationStatus
  freshnessStatus: ProjectCostFreshnessStatus
  completenessStatus: ProjectCostCompletenessStatus
  cutoffDate?: string | null
  totalCost: ProjectCostAmount | null
  wipCost: ProjectCostAmount | null
  deliveredCost: ProjectCostAmount | null
  shipmentPretaxRevenue: ProjectCostAmount | null
  shipmentGrossMargin: ProjectCostAmount | null
  shipmentGrossMarginRate: ProjectCostAmount | null
  openVarianceCount?: number | null
  blockingVarianceCount?: number | null
  provisionalSourceCount?: number | null
  unpricedSourceCount?: number | null
}

export interface ProjectCostCategorySummary {
  category: ProjectCostCategory
  amount: ProjectCostAmount | null
  sourceCount?: number | null
}

export interface ProjectCostStageSummary {
  stage: ProjectCostStage
  amount: ProjectCostAmount | null
}

export interface ProjectCostRunSummary {
  id: ResourceId
  calculationNo: string
  status: ProjectCostCalculationStatus
  cutoffDate: string
  calculatedAt?: string | null
}

export interface ProjectCostAuditRecord {
  action: string
  operatorUsername: string
  createdAt: string
  amountSummary?: string | null
}

export interface ProjectCostProjectDetail extends ProjectCostWorkbenchRecord {
  latestCalculationId?: ResourceId | null
  latestCalculationNo?: string | null
  finishedCost?: ProjectCostAmount | null
  adjustmentAmount?: ProjectCostAmount | null
  invoicePretaxRevenue?: ProjectCostAmount | null
  invoiceGrossMargin?: ProjectCostAmount | null
  targetRevenue?: ProjectCostAmount | null
  targetGrossMargin?: ProjectCostAmount | null
  categorySummaries: ProjectCostCategorySummary[]
  stageSummaries: ProjectCostStageSummary[]
  calculations: ProjectCostRunSummary[]
  auditSummary: ProjectCostAuditRecord[]
}

export interface ProjectCostCalculationDetail extends ProjectCostVisibility, ProjectCostActionState {
  id: ResourceId
  projectId: ResourceId
  projectNo: string
  projectName: string
  calculationNo: string
  status: ProjectCostCalculationStatus
  freshnessStatus: ProjectCostFreshnessStatus
  completenessStatus: ProjectCostCompletenessStatus
  cutoffDate: string
  isCurrent?: boolean | null
  totalCost: ProjectCostAmount | null
  shipmentPretaxRevenue: ProjectCostAmount | null
  shipmentGrossMargin: ProjectCostAmount | null
  shipmentGrossMarginRate: ProjectCostAmount | null
  openVarianceCount?: number | null
  blockingVarianceCount?: number | null
  provisionalSourceCount?: number | null
  unpricedSourceCount?: number | null
  calculatedByName?: string | null
  calculatedAt?: string | null
  confirmedByName?: string | null
  confirmedAt?: string | null
}

export interface ProjectCostSourceListParams {
  category?: ProjectCostCategory | null
  stage?: ProjectCostStage | null
  sourceStatus?: ProjectCostSourceStatus | null
  sourceType?: string | null
  projectId?: ResourceId | null
  businessDateFrom?: string | null
  businessDateTo?: string | null
  page: number
  pageSize: number
}

export interface ProjectCostEntryListParams {
  category?: ProjectCostCategory | null
  stage?: ProjectCostStage | null
  page: number
  pageSize: number
}

export interface ProjectCostVarianceListParams {
  projectId?: ResourceId | null
  varianceType?: string | null
  severity?: ProjectCostVarianceSeverity | null
  status?: ProjectCostVarianceStatus | null
  sourceType?: string | null
  page: number
  pageSize: number
}

export interface ProjectCostSourceRoute {
  name?: string
  path?: string
  params?: Record<string, ResourceId>
  query?: Record<string, string>
}

export interface ProjectCostSourceRecord extends ProjectCostVisibility {
  id: ResourceId
  calculationId: ResourceId
  projectId: ResourceId
  category: ProjectCostCategory
  stage: ProjectCostStage
  sourceStatus: ProjectCostSourceStatus
  sourceType: string
  sourceNo?: string | null
  sourceSummary?: string | null
  sourceRoute?: ProjectCostSourceRoute | null
  businessDate?: string | null
  materialCode?: string | null
  materialName?: string | null
  unitName?: string | null
  quantity: ProjectCostAmount | null
  unitPrice: ProjectCostAmount | null
  sourceAmount: ProjectCostAmount | null
}

export interface ProjectCostEntryRecord {
  id: ResourceId
  calculationId: ResourceId
  category: ProjectCostCategory
  stage: ProjectCostStage
  direction?: 'DEBIT' | 'CREDIT' | string
  amount: ProjectCostAmount | null
  description?: string | null
  sourceCount?: number | null
}

export interface ProjectCostVarianceRecord extends ProjectCostVisibility {
  id: ResourceId
  calculationId?: ResourceId | null
  projectId: ResourceId
  projectNo?: string | null
  projectName?: string | null
  varianceType: string
  severity: ProjectCostVarianceSeverity
  sourceType?: string | null
  sourceNo?: string | null
  sourceSummary?: string | null
  expectedAmount: ProjectCostAmount | null
  actualAmount: ProjectCostAmount | null
  differenceAmount: ProjectCostAmount | null
  status: ProjectCostVarianceStatus
  resolvedAdjustmentNo?: string | null
  description?: string | null
}

export interface ProjectCostAdjustmentListParams {
  keyword?: string | null
  status?: ProjectCostAdjustmentStatus | null
  projectId?: ResourceId | null
  businessDateFrom?: string | null
  businessDateTo?: string | null
  page: number
  pageSize: number
}

export interface ProjectCostAdjustmentLineRecord {
  id?: ResourceId
  projectId: ResourceId
  projectNo?: string | null
  projectName?: string | null
  category: ProjectCostCategory
  stage: ProjectCostStage
  direction: ProjectCostAdjustmentDirection
  amount: ProjectCostAmount
  sourceExpenseLineId?: ResourceId | null
  sourceNo?: string | null
  remark?: string | null
}

export interface ProjectCostAdjustmentRecord extends ProjectCostVisibility, ProjectCostActionState {
  id: ResourceId
  adjustmentNo: string
  adjustmentType: ProjectCostAdjustmentType
  status: ProjectCostAdjustmentStatus
  businessDate: string
  reason?: string | null
  approvalStatus?: string | null
  rejectedReason?: string | null
  originalAdjustmentNo?: string | null
  totalAmount: ProjectCostAmount | null
  lines: ProjectCostAdjustmentLineRecord[]
}

export interface ProjectCostAdjustmentPayload extends ProjectCostActionPayload {
  adjustmentType: ProjectCostAdjustmentType
  businessDate: string
  reason?: string | null
  lines: Array<{
    projectId: ResourceId
    category: ProjectCostCategory
    stage: ProjectCostStage
    direction: ProjectCostAdjustmentDirection
    amount: ProjectCostAmount
    sourceExpenseLineId?: ResourceId | null
    originalAdjustmentLineId?: ResourceId | null
    remark?: string | null
  }>
}

export interface ProjectCostPublicExpenseCandidateListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  businessDateFrom?: string | null
  businessDateTo?: string | null
  page: number
  pageSize: number
}

export interface ProjectCostPublicExpenseCandidate extends ProjectCostVisibility {
  expenseLineId: ResourceId
  expenseNo: string
  supplierName?: string | null
  categoryName?: string | null
  businessDate?: string | null
  totalAmount: ProjectCostAmount | null
  allocatedAmount: ProjectCostAmount | null
  remainingAmount: ProjectCostAmount | null
}

export interface ProjectCostApi {
  projectCosts: {
    list(params: ProjectCostListParams): Promise<PageResult<ProjectCostWorkbenchRecord>>
    getProject(projectId: ResourceId): Promise<ProjectCostProjectDetail>
    createCalculation(projectId: ResourceId, payload: ProjectCostCalculationCreatePayload): Promise<ProjectCostCalculationDetail>
  }
  calculations: {
    get(id: ResourceId): Promise<ProjectCostCalculationDetail>
    sources(id: ResourceId, params: ProjectCostSourceListParams): Promise<PageResult<ProjectCostSourceRecord>>
    entries(id: ResourceId, params: ProjectCostEntryListParams): Promise<PageResult<ProjectCostEntryRecord>>
    variances(id: ResourceId | undefined, params: ProjectCostVarianceListParams): Promise<PageResult<ProjectCostVarianceRecord>>
    recalculate(id: ResourceId, payload: ProjectCostActionPayload): Promise<ProjectCostCalculationDetail>
    confirm(id: ResourceId, payload: ProjectCostActionPayload): Promise<ProjectCostCalculationDetail>
    cancel(id: ResourceId, payload: ProjectCostActionPayload): Promise<ProjectCostCalculationDetail>
  }
  adjustments: {
    list(params: ProjectCostAdjustmentListParams): Promise<PageResult<ProjectCostAdjustmentRecord>>
    get(id: ResourceId): Promise<ProjectCostAdjustmentRecord>
    create(payload: ProjectCostAdjustmentPayload): Promise<ProjectCostAdjustmentRecord>
    update(id: ResourceId, payload: ProjectCostAdjustmentPayload): Promise<ProjectCostAdjustmentRecord>
    submit(id: ResourceId, payload: ProjectCostActionPayload): Promise<ProjectCostAdjustmentRecord>
    cancel(id: ResourceId, payload: ProjectCostActionPayload): Promise<ProjectCostAdjustmentRecord>
    publicExpenseCandidates(params: ProjectCostPublicExpenseCandidateListParams): Promise<PageResult<ProjectCostPublicExpenseCandidate>>
  }
}

export interface ProjectCostApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createProjectCostApi(options: ProjectCostApiOptions = {}): ProjectCostApi {
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
  const write = async <T>(method: 'POST' | 'PUT', path: string, body?: object) => {
    const csrf = await getCsrf()
    const headers: Record<string, string> = {
      [csrf.headerName]: csrf.token,
    }
    const init: RequestInit = { headers, method }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }
    return request<T>(path, init)
  }

  const pickQuery = <T extends object>(query: T | undefined, keys: readonly (keyof T & string)[]) => {
    const result: Record<string, unknown> = {}
    keys.forEach((key) => {
      const value = query?.[key]
      if (value !== undefined) {
        result[key] = value
      }
    })
    return result
  }
  const encodeId = (id: ResourceId) => encodeURIComponent(String(id))
  const projectPath = (projectId: ResourceId) => `/api/admin/cost/project-costs/projects/${encodeId(projectId)}`
  const calculationPath = (id: ResourceId) => `/api/admin/cost/project-cost-calculations/${encodeId(id)}`
  const adjustmentPath = (id?: ResourceId) =>
    `/api/admin/cost/project-cost-adjustments${id === undefined ? '' : `/${encodeId(id)}`}`

  const projectCostQueryKeys = [
    'keyword',
    'ownerUserId',
    'projectStatus',
    'freshnessStatus',
    'varianceStatus',
    'completenessStatus',
    'cutoffDateFrom',
    'cutoffDateTo',
    'page',
    'pageSize',
  ] as const
  const sourceQueryKeys = [
    'category',
    'stage',
    'sourceStatus',
    'sourceType',
    'projectId',
    'businessDateFrom',
    'businessDateTo',
    'page',
    'pageSize',
  ] as const
  const entryQueryKeys = ['category', 'stage', 'page', 'pageSize'] as const
  const varianceQueryKeys = ['projectId', 'varianceType', 'severity', 'status', 'sourceType', 'page', 'pageSize'] as const
  const adjustmentQueryKeys = ['keyword', 'status', 'projectId', 'businessDateFrom', 'businessDateTo', 'page', 'pageSize'] as const
  const publicExpenseQueryKeys = ['keyword', 'supplierId', 'businessDateFrom', 'businessDateTo', 'page', 'pageSize'] as const

  return {
    projectCosts: {
      list: (params) =>
        get<PageResult<ProjectCostWorkbenchRecord>>('/api/admin/cost/project-costs', pickQuery(params, projectCostQueryKeys)),
      getProject: (projectId) => get<ProjectCostProjectDetail>(projectPath(projectId)),
      createCalculation: (projectId, payload) =>
        write<ProjectCostCalculationDetail>('POST', `${projectPath(projectId)}/calculations`, payload),
    },
    calculations: {
      get: (id) => get<ProjectCostCalculationDetail>(calculationPath(id)),
      sources: (id, params) =>
        get<PageResult<ProjectCostSourceRecord>>(`${calculationPath(id)}/sources`, pickQuery(params, sourceQueryKeys)),
      entries: (id, params) =>
        get<PageResult<ProjectCostEntryRecord>>(`${calculationPath(id)}/entries`, pickQuery(params, entryQueryKeys)),
      variances: (id, params) => {
        const path = id === undefined
          ? '/api/admin/cost/project-cost-variances'
          : `${calculationPath(id)}/variances`
        return get<PageResult<ProjectCostVarianceRecord>>(path, pickQuery(params, varianceQueryKeys))
      },
      recalculate: (id, payload) => write<ProjectCostCalculationDetail>('PUT', `${calculationPath(id)}/recalculate`, payload),
      confirm: (id, payload) => write<ProjectCostCalculationDetail>('PUT', `${calculationPath(id)}/confirm`, payload),
      cancel: (id, payload) => write<ProjectCostCalculationDetail>('PUT', `${calculationPath(id)}/cancel`, payload),
    },
    adjustments: {
      list: (params) => get<PageResult<ProjectCostAdjustmentRecord>>(adjustmentPath(), pickQuery(params, adjustmentQueryKeys)),
      get: (id) => get<ProjectCostAdjustmentRecord>(adjustmentPath(id)),
      create: (payload) => write<ProjectCostAdjustmentRecord>('POST', adjustmentPath(), payload),
      update: (id, payload) => write<ProjectCostAdjustmentRecord>('PUT', adjustmentPath(id), payload),
      submit: (id, payload) => write<ProjectCostAdjustmentRecord>('PUT', `${adjustmentPath(id)}/submit`, payload),
      cancel: (id, payload) => write<ProjectCostAdjustmentRecord>('PUT', `${adjustmentPath(id)}/cancel`, payload),
      publicExpenseCandidates: (params) =>
        get<PageResult<ProjectCostPublicExpenseCandidate>>(
          `${adjustmentPath()}/candidates/public-expenses`,
          pickQuery(params, publicExpenseQueryKeys),
        ),
    },
  }
}

export const projectCostApi = createProjectCostApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
