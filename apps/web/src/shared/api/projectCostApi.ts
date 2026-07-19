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
export type ProjectCostAdjustmentType = 'PROJECT_ADJUSTMENT' | 'PUBLIC_EXPENSE_ALLOCATION' | 'VARIANCE_SETTLEMENT'
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
  sourceRestricted?: boolean | null
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
  businessDateFrom?: string | null
  businessDateTo?: string | null
  sourceRestricted?: boolean | null
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
  costCategory: ProjectCostCategory
  costStage: ProjectCostStage
  direction: ProjectCostAdjustmentDirection
  amount: ProjectCostAmount
  publicExpenseLineId?: ResourceId | null
  sourceNo?: string | null
  reason?: string | null
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
    costCategory: ProjectCostCategory
    costStage: ProjectCostStage
    direction: ProjectCostAdjustmentDirection
    amount: ProjectCostAmount
    publicExpenseLineId?: ResourceId | null
    originalAdjustmentLineId?: ResourceId | null
    reason?: string | null
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
  taxExcludedAmount: ProjectCostAmount | null
  allocatedAmount: ProjectCostAmount | null
  availableAmount: ProjectCostAmount | null
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

type ApiRecord = Record<string, unknown>

function asRecord(value: unknown): ApiRecord {
  return value && typeof value === 'object' ? value as ApiRecord : {}
}

function asPage<T>(value: unknown, mapper: (item: ApiRecord) => T): PageResult<T> {
  const raw = asRecord(value)
  const items = Array.isArray(raw.items) ? raw.items.map((item) => mapper(asRecord(item))) : []
  return {
    ...raw,
    items,
    total: typeof raw.total === 'number' ? raw.total : items.length,
    page: typeof raw.page === 'number' ? raw.page : 1,
    pageSize: typeof raw.pageSize === 'number' ? raw.pageSize : Math.max(items.length, 10),
  } as PageResult<T>
}

function amountValue(raw: ApiRecord, ...keys: string[]): ProjectCostAmount | null {
  for (const key of keys) {
    if (raw[key] !== undefined) {
      return raw[key] === null ? null : String(raw[key])
    }
  }
  return null
}

function stringValue(raw: ApiRecord, ...keys: string[]): string {
  for (const key of keys) {
    if (raw[key] !== undefined && raw[key] !== null) {
      return String(raw[key])
    }
  }
  return ''
}

function optionalString(raw: ApiRecord, ...keys: string[]): string | null {
  const value = stringValue(raw, ...keys)
  return value || null
}

function idValue(raw: ApiRecord, ...keys: string[]): ResourceId {
  for (const key of keys) {
    const value = raw[key]
    if (typeof value === 'number' || typeof value === 'string') {
      return value
    }
  }
  return ''
}

function optionalIdValue(raw: ApiRecord, ...keys: string[]): ResourceId | null {
  for (const key of keys) {
    const value = raw[key]
    if (typeof value === 'number' || typeof value === 'string') {
      return value
    }
  }
  return null
}

function booleanValue(raw: ApiRecord, key: string, fallback: boolean): boolean {
  return typeof raw[key] === 'boolean' ? raw[key] as boolean : fallback
}

function mapVisibility(raw: ApiRecord): ProjectCostVisibility {
  const sourceRestricted = raw.sourceRestricted === true
  return {
    amountVisible: booleanValue(raw, 'amountVisible', !sourceRestricted),
    sourceVisible: booleanValue(raw, 'sourceVisible', !sourceRestricted) && !sourceRestricted,
    restrictedReason: optionalString(raw, 'restrictedReason'),
  }
}

function mapActionState(raw: ApiRecord): ProjectCostActionState {
  const disabledReasons = asRecord(raw.actionDisabledReasons)
  return {
    version: typeof raw.version === 'number' ? raw.version : 0,
    allowedActions: Array.isArray(raw.allowedActions) ? raw.allowedActions.map(String) : [],
    actionDisabledReasons: Object.fromEntries(Object.entries(disabledReasons).map(([key, value]) => [key, String(value)])),
    sourceFingerprint: optionalString(raw, 'sourceFingerprint'),
  }
}

function mapCalculationStatus(raw: ApiRecord): ProjectCostCalculationStatus {
  const status = stringValue(raw, 'calculationStatus', 'status')
  return (status || 'DRAFT') as ProjectCostCalculationStatus
}

function mapFreshnessStatus(raw: ApiRecord): ProjectCostFreshnessStatus {
  const status = stringValue(raw, 'freshnessStatus')
  if (status) {
    return status as ProjectCostFreshnessStatus
  }
  if (raw.isCurrent === false) {
    return 'STALE'
  }
  return 'CURRENT'
}

function mapCompletenessStatus(raw: ApiRecord): ProjectCostCompletenessStatus {
  const status = stringValue(raw, 'completenessStatus', 'marginCompleteness')
  return (status || 'COMPLETE') as ProjectCostCompletenessStatus
}

function mapSourceStatus(raw: ApiRecord): ProjectCostSourceStatus {
  if (raw.sourceRestricted === true) {
    return 'RESTRICTED'
  }
  const status = stringValue(raw, 'sourceStatus', 'status')
  const aliases: Record<string, ProjectCostSourceStatus> = {
    VALUED: 'ACTUAL',
    CONFIRMED: 'ACTUAL',
    POSTED: 'ACTUAL',
    ESTIMATED: 'PROVISIONAL',
    SOURCE_QUANTITY_ONLY: 'UNPRICED',
  }
  return aliases[status] ?? (status || 'ACTUAL') as ProjectCostSourceStatus
}

function mapVarianceSeverity(raw: ApiRecord): ProjectCostVarianceSeverity {
  const severity = stringValue(raw, 'severity')
  return (severity === 'ERROR' ? 'BLOCKING' : severity || 'INFO') as ProjectCostVarianceSeverity
}

function mapVarianceStatus(raw: ApiRecord): ProjectCostVarianceStatus {
  const status = stringValue(raw, 'status')
  const aliases: Record<string, ProjectCostVarianceStatus> = {
    CLOSED: 'RESOLVED',
    IGNORED: 'SUPERSEDED',
  }
  return aliases[status] ?? (status || 'OPEN') as ProjectCostVarianceStatus
}

function mapWorkbenchRecord(raw: ApiRecord): ProjectCostWorkbenchRecord {
  return {
    ...raw,
    ...mapVisibility(raw),
    ...mapActionState(raw),
    projectId: idValue(raw, 'projectId'),
    projectNo: stringValue(raw, 'projectNo'),
    projectName: stringValue(raw, 'projectName'),
    customerName: optionalString(raw, 'customerName'),
    ownerDisplayName: optionalString(raw, 'ownerDisplayName', 'ownerName'),
    projectStatus: stringValue(raw, 'projectStatus') || 'ACTIVE',
    calculationId: optionalIdValue(raw, 'calculationId', 'latestCalculationId'),
    calculationNo: optionalString(raw, 'calculationNo', 'latestCalculationNo'),
    calculationStatus: mapCalculationStatus(raw),
    freshnessStatus: mapFreshnessStatus(raw),
    completenessStatus: mapCompletenessStatus(raw),
    cutoffDate: optionalString(raw, 'cutoffDate'),
    totalCost: amountValue(raw, 'totalCost', 'projectCostTotal'),
    wipCost: amountValue(raw, 'wipCost'),
    deliveredCost: amountValue(raw, 'deliveredCost'),
    shipmentPretaxRevenue: amountValue(raw, 'shipmentPretaxRevenue', 'shipmentRevenue'),
    shipmentGrossMargin: amountValue(raw, 'shipmentGrossMargin'),
    shipmentGrossMarginRate: amountValue(raw, 'shipmentGrossMarginRate'),
    openVarianceCount: typeof raw.openVarianceCount === 'number' ? raw.openVarianceCount : null,
    blockingVarianceCount: typeof raw.blockingVarianceCount === 'number' ? raw.blockingVarianceCount : null,
    provisionalSourceCount: typeof raw.provisionalSourceCount === 'number' ? raw.provisionalSourceCount : null,
    unpricedSourceCount: typeof raw.unpricedSourceCount === 'number' ? raw.unpricedSourceCount : null,
  } as ProjectCostWorkbenchRecord
}

function mapCategorySummary(raw: ApiRecord): ProjectCostCategorySummary {
  return {
    category: stringValue(raw, 'category', 'costCategory') as ProjectCostCategory,
    amount: amountValue(raw, 'amount', 'costAmount'),
    sourceCount: typeof raw.sourceCount === 'number' ? raw.sourceCount : null,
  }
}

function mapStageSummary(raw: ApiRecord): ProjectCostStageSummary {
  return {
    stage: stringValue(raw, 'stage', 'costStage') as ProjectCostStage,
    amount: amountValue(raw, 'amount', 'costAmount'),
  }
}

function mapProjectRun(raw: ApiRecord): ProjectCostRunSummary {
  return {
    id: idValue(raw, 'id', 'calculationId'),
    calculationNo: stringValue(raw, 'calculationNo'),
    status: mapCalculationStatus(raw),
    cutoffDate: stringValue(raw, 'cutoffDate'),
    calculatedAt: optionalString(raw, 'calculatedAt'),
  }
}

function mapAuditRecord(raw: ApiRecord): ProjectCostAuditRecord {
  return {
    action: stringValue(raw, 'action'),
    operatorUsername: stringValue(raw, 'operatorUsername', 'operatorName'),
    createdAt: stringValue(raw, 'createdAt'),
    amountSummary: optionalString(raw, 'amountSummary'),
  }
}

function mapProjectDetail(raw: ApiRecord): ProjectCostProjectDetail {
  const base = mapWorkbenchRecord(raw)
  return {
    ...base,
    latestCalculationId: optionalIdValue(raw, 'latestCalculationId', 'calculationId'),
    latestCalculationNo: optionalString(raw, 'latestCalculationNo', 'calculationNo'),
    finishedCost: amountValue(raw, 'finishedCost'),
    adjustmentAmount: amountValue(raw, 'adjustmentAmount'),
    invoicePretaxRevenue: amountValue(raw, 'invoicePretaxRevenue', 'invoiceRevenue'),
    invoiceGrossMargin: amountValue(raw, 'invoiceGrossMargin'),
    targetRevenue: amountValue(raw, 'targetRevenue'),
    targetGrossMargin: amountValue(raw, 'targetGrossMargin'),
    categorySummaries: Array.isArray(raw.categorySummaries) ? raw.categorySummaries.map((item) => mapCategorySummary(asRecord(item))) : [],
    stageSummaries: Array.isArray(raw.stageSummaries) ? raw.stageSummaries.map((item) => mapStageSummary(asRecord(item))) : [],
    calculations: Array.isArray(raw.calculations) ? raw.calculations.map((item) => mapProjectRun(asRecord(item))) : [],
    auditSummary: Array.isArray(raw.auditSummary) ? raw.auditSummary.map((item) => mapAuditRecord(asRecord(item))) : [],
  }
}

function mapCalculationDetail(raw: ApiRecord): ProjectCostCalculationDetail {
  return {
    ...raw,
    ...mapVisibility(raw),
    ...mapActionState(raw),
    id: idValue(raw, 'id', 'calculationId'),
    projectId: idValue(raw, 'projectId'),
    projectNo: stringValue(raw, 'projectNo'),
    projectName: stringValue(raw, 'projectName'),
    calculationNo: stringValue(raw, 'calculationNo'),
    status: mapCalculationStatus(raw),
    freshnessStatus: mapFreshnessStatus(raw),
    completenessStatus: mapCompletenessStatus(raw),
    cutoffDate: stringValue(raw, 'cutoffDate'),
    isCurrent: typeof raw.isCurrent === 'boolean' ? raw.isCurrent : null,
    totalCost: amountValue(raw, 'totalCost', 'projectCostTotal'),
    shipmentPretaxRevenue: amountValue(raw, 'shipmentPretaxRevenue', 'shipmentRevenue'),
    shipmentGrossMargin: amountValue(raw, 'shipmentGrossMargin'),
    shipmentGrossMarginRate: amountValue(raw, 'shipmentGrossMarginRate'),
    openVarianceCount: typeof raw.openVarianceCount === 'number' ? raw.openVarianceCount : null,
    blockingVarianceCount: typeof raw.blockingVarianceCount === 'number' ? raw.blockingVarianceCount : null,
    provisionalSourceCount: typeof raw.provisionalSourceCount === 'number' ? raw.provisionalSourceCount : null,
    unpricedSourceCount: typeof raw.unpricedSourceCount === 'number' ? raw.unpricedSourceCount : null,
    calculatedByName: optionalString(raw, 'calculatedByName'),
    calculatedAt: optionalString(raw, 'calculatedAt'),
    confirmedByName: optionalString(raw, 'confirmedByName'),
    confirmedAt: optionalString(raw, 'confirmedAt'),
  } as ProjectCostCalculationDetail
}

function mapSourceRecord(raw: ApiRecord): ProjectCostSourceRecord {
  return {
    ...raw,
    ...mapVisibility(raw),
    id: idValue(raw, 'id', 'sourceId'),
    calculationId: idValue(raw, 'calculationId'),
    projectId: idValue(raw, 'projectId'),
    category: stringValue(raw, 'category', 'costCategory') as ProjectCostCategory,
    stage: stringValue(raw, 'stage', 'costStage') as ProjectCostStage,
    sourceStatus: mapSourceStatus(raw),
    sourceType: stringValue(raw, 'sourceType'),
    sourceNo: optionalString(raw, 'sourceNo'),
    sourceSummary: optionalString(raw, 'sourceSummary'),
    sourceRoute: raw.sourceRoute ? asRecord(raw.sourceRoute) as unknown as ProjectCostSourceRoute : null,
    businessDate: optionalString(raw, 'businessDate'),
    materialCode: optionalString(raw, 'materialCode'),
    materialName: optionalString(raw, 'materialName'),
    unitName: optionalString(raw, 'unitName'),
    quantity: amountValue(raw, 'quantity'),
    unitPrice: amountValue(raw, 'unitPrice', 'unitCost'),
    sourceAmount: amountValue(raw, 'sourceAmount', 'calculatedAmount', 'amount'),
  }
}

function mapEntryRecord(raw: ApiRecord): ProjectCostEntryRecord {
  return {
    ...raw,
    id: idValue(raw, 'id', 'entryId'),
    calculationId: idValue(raw, 'calculationId'),
    category: stringValue(raw, 'category', 'costCategory') as ProjectCostCategory,
    stage: stringValue(raw, 'stage', 'costStage') as ProjectCostStage,
    direction: optionalString(raw, 'direction'),
    amount: amountValue(raw, 'amount'),
    description: optionalString(raw, 'description'),
    sourceCount: typeof raw.sourceCount === 'number' ? raw.sourceCount : null,
  }
}

function mapVarianceRecord(raw: ApiRecord): ProjectCostVarianceRecord {
  return {
    ...raw,
    ...mapVisibility(raw),
    id: idValue(raw, 'id', 'varianceId'),
    calculationId: optionalIdValue(raw, 'calculationId'),
    projectId: idValue(raw, 'projectId'),
    projectNo: optionalString(raw, 'projectNo'),
    projectName: optionalString(raw, 'projectName'),
    varianceType: stringValue(raw, 'varianceType'),
    severity: mapVarianceSeverity(raw),
    sourceType: optionalString(raw, 'sourceType'),
    sourceNo: optionalString(raw, 'sourceNo'),
    sourceSummary: optionalString(raw, 'sourceSummary'),
    expectedAmount: amountValue(raw, 'expectedAmount'),
    actualAmount: amountValue(raw, 'actualAmount'),
    differenceAmount: amountValue(raw, 'differenceAmount', 'varianceAmount'),
    status: mapVarianceStatus(raw),
    resolvedAdjustmentNo: optionalString(raw, 'resolvedAdjustmentNo'),
    description: optionalString(raw, 'description'),
  }
}

function mapAdjustmentLine(raw: ApiRecord): ProjectCostAdjustmentLineRecord {
  return {
    ...raw,
    id: optionalIdValue(raw, 'id', 'lineId') ?? undefined,
    projectId: idValue(raw, 'projectId'),
    projectNo: optionalString(raw, 'projectNo'),
    projectName: optionalString(raw, 'projectName'),
    costCategory: stringValue(raw, 'costCategory', 'category') as ProjectCostCategory,
    costStage: stringValue(raw, 'costStage', 'stage') as ProjectCostStage,
    direction: stringValue(raw, 'direction') as ProjectCostAdjustmentDirection,
    amount: amountValue(raw, 'amount') ?? '0',
    publicExpenseLineId: optionalIdValue(raw, 'publicExpenseLineId', 'sourceExpenseLineId'),
    sourceNo: optionalString(raw, 'sourceNo'),
    reason: optionalString(raw, 'reason', 'remark'),
  }
}

function mapAdjustmentRecord(raw: ApiRecord): ProjectCostAdjustmentRecord {
  return {
    ...raw,
    ...mapVisibility(raw),
    ...mapActionState(raw),
    id: idValue(raw, 'id', 'adjustmentId'),
    adjustmentNo: stringValue(raw, 'adjustmentNo'),
    adjustmentType: stringValue(raw, 'adjustmentType') as ProjectCostAdjustmentType,
    status: stringValue(raw, 'status') as ProjectCostAdjustmentStatus,
    businessDate: stringValue(raw, 'businessDate'),
    reason: optionalString(raw, 'reason'),
    approvalStatus: optionalString(raw, 'approvalStatus'),
    rejectedReason: optionalString(raw, 'rejectedReason'),
    originalAdjustmentNo: optionalString(raw, 'originalAdjustmentNo'),
    totalAmount: amountValue(raw, 'totalAmount'),
    lines: Array.isArray(raw.lines) ? raw.lines.map((item) => mapAdjustmentLine(asRecord(item))) : [],
  }
}

function mapPublicExpenseCandidate(raw: ApiRecord): ProjectCostPublicExpenseCandidate {
  return {
    ...raw,
    ...mapVisibility(raw),
    expenseLineId: idValue(raw, 'expenseLineId', 'publicExpenseLineId'),
    expenseNo: stringValue(raw, 'expenseNo'),
    supplierName: optionalString(raw, 'supplierName'),
    categoryName: optionalString(raw, 'categoryName'),
    businessDate: optionalString(raw, 'businessDate'),
    taxExcludedAmount: amountValue(raw, 'taxExcludedAmount', 'totalAmount'),
    allocatedAmount: amountValue(raw, 'allocatedAmount'),
    availableAmount: amountValue(raw, 'availableAmount', 'remainingAmount'),
  }
}

function mapAdjustmentPayload(payload: ProjectCostAdjustmentPayload): ProjectCostAdjustmentPayload {
  return {
    ...payload,
    lines: payload.lines.map((line) => {
      const raw = line as unknown as ApiRecord
      const mapped: ProjectCostAdjustmentPayload['lines'][number] = {
        projectId: line.projectId,
        costCategory: (raw.costCategory ?? raw.category) as ProjectCostCategory,
        costStage: (raw.costStage ?? raw.stage) as ProjectCostStage,
        direction: line.direction,
        amount: line.amount,
        publicExpenseLineId: (raw.publicExpenseLineId ?? raw.sourceExpenseLineId ?? null) as ResourceId | null,
        originalAdjustmentLineId: (raw.originalAdjustmentLineId ?? null) as ResourceId | null,
        reason: (raw.reason ?? raw.remark ?? null) as string | null,
      }
      if (mapped.publicExpenseLineId === null) {
        delete mapped.publicExpenseLineId
      }
      if (mapped.originalAdjustmentLineId === null) {
        delete mapped.originalAdjustmentLineId
      }
      if (mapped.reason === null) {
        delete mapped.reason
      }
      return mapped
    }),
  }
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
    'sourceRestricted',
    'page',
    'pageSize',
  ] as const
  const entryQueryKeys = ['category', 'stage', 'page', 'pageSize'] as const
  const varianceQueryKeys = [
    'projectId',
    'varianceType',
    'severity',
    'status',
    'sourceType',
    'businessDateFrom',
    'businessDateTo',
    'sourceRestricted',
    'page',
    'pageSize',
  ] as const
  const adjustmentQueryKeys = ['keyword', 'status', 'projectId', 'businessDateFrom', 'businessDateTo', 'page', 'pageSize'] as const
  const publicExpenseQueryKeys = ['keyword', 'supplierId', 'businessDateFrom', 'businessDateTo', 'page', 'pageSize'] as const

  return {
    projectCosts: {
      list: (params) =>
        get<unknown>('/api/admin/cost/project-costs', pickQuery(params, projectCostQueryKeys)).then((page) =>
          asPage(page, mapWorkbenchRecord),
        ),
      getProject: (projectId) => get<unknown>(projectPath(projectId)).then((record) => mapProjectDetail(asRecord(record))),
      createCalculation: (projectId, payload) =>
        write<unknown>('POST', `${projectPath(projectId)}/calculations`, payload).then((record) => mapCalculationDetail(asRecord(record))),
    },
    calculations: {
      get: (id) => get<unknown>(calculationPath(id)).then((record) => mapCalculationDetail(asRecord(record))),
      sources: (id, params) =>
        get<unknown>(`${calculationPath(id)}/sources`, pickQuery(params, sourceQueryKeys)).then((page) =>
          asPage(page, mapSourceRecord),
        ),
      entries: (id, params) =>
        get<unknown>(`${calculationPath(id)}/entries`, pickQuery(params, entryQueryKeys)).then((page) =>
          asPage(page, mapEntryRecord),
        ),
      variances: (id, params) => {
        const path = id === undefined
          ? '/api/admin/cost/project-cost-variances'
          : `${calculationPath(id)}/variances`
        return get<unknown>(path, pickQuery(params, varianceQueryKeys)).then((page) => asPage(page, mapVarianceRecord))
      },
      recalculate: (id, payload) =>
        write<unknown>('PUT', `${calculationPath(id)}/recalculate`, payload).then((record) => mapCalculationDetail(asRecord(record))),
      confirm: (id, payload) =>
        write<unknown>('PUT', `${calculationPath(id)}/confirm`, payload).then((record) => mapCalculationDetail(asRecord(record))),
      cancel: (id, payload) =>
        write<unknown>('PUT', `${calculationPath(id)}/cancel`, payload).then((record) => mapCalculationDetail(asRecord(record))),
    },
    adjustments: {
      list: (params) =>
        get<unknown>(adjustmentPath(), pickQuery(params, adjustmentQueryKeys)).then((page) => asPage(page, mapAdjustmentRecord)),
      get: (id) => get<unknown>(adjustmentPath(id)).then((record) => mapAdjustmentRecord(asRecord(record))),
      create: (payload) =>
        write<unknown>('POST', adjustmentPath(), mapAdjustmentPayload(payload)).then((record) => mapAdjustmentRecord(asRecord(record))),
      update: (id, payload) =>
        write<unknown>('PUT', adjustmentPath(id), mapAdjustmentPayload(payload)).then((record) => mapAdjustmentRecord(asRecord(record))),
      submit: (id, payload) =>
        write<unknown>('PUT', `${adjustmentPath(id)}/submit`, payload).then((record) => mapAdjustmentRecord(asRecord(record))),
      cancel: (id, payload) =>
        write<unknown>('PUT', `${adjustmentPath(id)}/cancel`, payload).then((record) => mapAdjustmentRecord(asRecord(record))),
      publicExpenseCandidates: (params) =>
        get<unknown>(
          `${adjustmentPath()}/candidates/public-expenses`,
          pickQuery(params, publicExpenseQueryKeys),
        ).then((page) => asPage(page, mapPublicExpenseCandidate)),
    },
  }
}

export const projectCostApi = createProjectCostApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
