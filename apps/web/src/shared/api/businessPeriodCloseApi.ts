import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type PeriodCloseDecimal = string

export type BusinessPeriodCloseStatus = 'PENDING_CHECK' | 'BLOCKED' | 'READY' | 'CLOSED' | 'REOPENED' | 'NOT_CHECKED' | 'MANUAL_LOCKED_WITHOUT_SNAPSHOT'
export type BusinessPeriodClosePeriodStatus = 'OPEN' | 'LOCKED'
export type BusinessPeriodCloseCheckSeverity = 'BLOCKING' | 'WARNING' | 'INFO'
export type BusinessPeriodCloseCheckDomain = 'INVENTORY' | 'WIP' | 'PROJECT_COST' | 'REPORT'
export type BusinessPeriodCloseCheckResult = 'BLOCKING' | 'WARNING' | 'PASSED'
export type BusinessPeriodCloseAction = 'CHECK' | 'CLOSE' | 'REOPEN' | 'SNAPSHOT_VIEW'
export type BusinessPeriodCloseReportCode =
  | 'OVERVIEW'
  | 'SALES_SUMMARY'
  | 'PROCUREMENT_SUMMARY'
  | 'INVENTORY_STOCK_FLOW'
  | 'PRODUCTION_EXECUTION'
  | 'COST_COLLECTION'
  | 'SETTLEMENT_SUMMARY'
  | 'EXCEPTIONS'

export interface BusinessPeriodCloseVisibility {
  amountVisible: boolean
  sourceVisible: boolean
  restrictedReason?: string | null
}

export interface BusinessPeriodCloseActionState {
  version: number
  sourceFingerprint?: string | null
  allowedActions: string[]
  actionDisabledReasons: Record<string, string>
}

export interface BusinessPeriodCloseListParams {
  periodCode?: string | null
  startDate?: string | null
  endDate?: string | null
  closeStatus?: BusinessPeriodCloseStatus | null
  checkResult?: BusinessPeriodCloseCheckResult | null
  hasBlocking?: boolean | null
  page: number
  pageSize: number
}

export interface BusinessPeriodCloseRunRecord extends BusinessPeriodCloseVisibility, BusinessPeriodCloseActionState {
  runId: ResourceId
  periodId: ResourceId
  periodCode: string
  periodName?: string | null
  startDate: string
  endDate: string
  periodStatus: BusinessPeriodClosePeriodStatus | string
  periodStatusName?: string | null
  closeStatus: BusinessPeriodCloseStatus
  closeStatusName?: string | null
  revisionNo: number
  latestCheckId?: ResourceId | null
  latestCheckedAt?: string | null
  latestCheckResult?: BusinessPeriodCloseCheckResult | string | null
  blockingCount: number
  warningCount: number
  snapshotId?: ResourceId | null
  snapshotValueAmount: PeriodCloseDecimal | null
  closedByName?: string | null
  closedAt?: string | null
  reopenedByName?: string | null
  reopenedAt?: string | null
}

export interface BusinessPeriodCloseRunVersion {
  runId: ResourceId
  revisionNo: number
  closeStatus: BusinessPeriodCloseStatus | string
  closedAt?: string | null
  reopenedAt?: string | null
}

export interface BusinessPeriodCloseAuditRecord {
  action: string
  operatorUsername?: string | null
  operatorName?: string | null
  reason?: string | null
  createdAt: string
}

export interface BusinessPeriodCloseRunDetail extends BusinessPeriodCloseRunRecord {
  historyVersions: BusinessPeriodCloseRunVersion[]
  auditSummary: BusinessPeriodCloseAuditRecord[]
}

export interface BusinessPeriodClosePeriodSummary extends BusinessPeriodCloseActionState {
  periodId: ResourceId
  periodCode: string
  periodName?: string | null
  closeStatus: BusinessPeriodCloseStatus | string
  closeStatusName?: string | null
  currentRunId?: ResourceId | null
  currentRevisionNo?: number | null
  latestCheckId?: ResourceId | null
  latestCheckedAt?: string | null
  blockingCount?: number | null
  warningCount?: number | null
  snapshotId?: ResourceId | null
  versions: BusinessPeriodCloseRunVersion[]
}

export interface BusinessPeriodCloseCheckCreatePayload {
  periodId: ResourceId
  idempotencyKey: string
}

export interface BusinessPeriodCloseActionPayload {
  version: number
  idempotencyKey: string
  sourceFingerprint?: string | null
}

export interface BusinessPeriodCloseClosePayload extends BusinessPeriodCloseActionPayload {
  warningAcknowledged: boolean
  reason: string
}

export interface BusinessPeriodCloseReopenPayload extends BusinessPeriodCloseActionPayload {
  reason: string
}

export interface BusinessPeriodCloseSourceRoute {
  name?: string
  path?: string
  params?: Record<string, ResourceId>
  query?: Record<string, string>
}

export interface BusinessPeriodCloseCheckRun {
  checkRunId: ResourceId
  runId: ResourceId
  startedAt?: string | null
  completedAt?: string | null
  operatorName?: string | null
  result?: BusinessPeriodCloseCheckResult | string | null
  blockingCount: number
  warningCount: number
  sourceFingerprint?: string | null
  partitionFingerprints?: Record<string, string>
}

export interface BusinessPeriodCloseCheckItem extends BusinessPeriodCloseVisibility {
  id: ResourceId
  checkRunId: ResourceId
  domain: BusinessPeriodCloseCheckDomain | string
  severity: BusinessPeriodCloseCheckSeverity
  checkCode: string
  title: string
  description: string
  objectType?: string | null
  objectId?: ResourceId | null
  objectNo?: string | null
  businessImpact?: string | null
  suggestion?: string | null
  sourceRoute?: BusinessPeriodCloseSourceRoute | null
}

export interface BusinessPeriodCloseCheckListParams {
  checkRunId?: ResourceId | null
  page: number
  pageSize: number
}

export interface BusinessPeriodCloseSnapshotPartition extends BusinessPeriodCloseVisibility {
  code: string
  name: string
  recordCount?: number | null
  sourceFingerprint?: string | null
}

export interface BusinessPeriodCloseSnapshotOverview {
  snapshotId: ResourceId
  runId: ResourceId
  periodCode: string
  startDate?: string | null
  endDate?: string | null
  generatedBy?: string | null
  closeStatus?: string | null
  revisionNo: number
  generatedAt: string
  sourceCheckRunId?: ResourceId | null
  sourceFingerprint?: string | null
  isHistoricalRevision: boolean
  partitions: BusinessPeriodCloseSnapshotPartition[]
}

export interface BusinessPeriodCloseSnapshotListParams {
  page: number
  pageSize: number
}

export interface BusinessPeriodCloseSnapshotInventoryRecord extends BusinessPeriodCloseVisibility {
  id: ResourceId
  materialCode?: string | null
  materialName?: string | null
  warehouseName?: string | null
  projectNo?: string | null
  endingQuantity: PeriodCloseDecimal | null
  lockedQuantity?: PeriodCloseDecimal | null
  availableQuantity?: PeriodCloseDecimal | null
  inboundQuantity?: PeriodCloseDecimal | null
  outboundQuantity?: PeriodCloseDecimal | null
  adjustmentQuantity?: PeriodCloseDecimal | null
  unitCost: PeriodCloseDecimal | null
  endingValue: PeriodCloseDecimal | null
}

export interface BusinessPeriodCloseSnapshotWipRecord extends BusinessPeriodCloseVisibility {
  id: ResourceId
  projectNo?: string | null
  projectName?: string | null
  workOrderNo?: string | null
  materialCode?: string | null
  materialName?: string | null
  stage?: string | null
  wipQuantity: PeriodCloseDecimal | null
  wipAmount: PeriodCloseDecimal | null
  sourceSummary?: string | null
}

export interface BusinessPeriodCloseSnapshotProjectCostRecord extends BusinessPeriodCloseVisibility {
  id: ResourceId
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  calculationId?: ResourceId | null
  calculationNo?: string | null
  cutoffDate?: string | null
  totalCost: PeriodCloseDecimal | null
  wipCost?: PeriodCloseDecimal | null
  deliveredCost?: PeriodCloseDecimal | null
  revenueAmount?: PeriodCloseDecimal | null
  grossMarginAmount?: PeriodCloseDecimal | null
  grossMarginRate: PeriodCloseDecimal | null
  completenessStatus?: string | null
  sourceFingerprint?: string | null
}

export interface BusinessPeriodCloseSnapshotReport extends BusinessPeriodCloseVisibility {
  reportCode: BusinessPeriodCloseReportCode
  reportName: string
  schemaVersion: number
  generatedAt: string
  sourceCount: number
  sourceFingerprint?: string | null
  result: Record<string, unknown>
}

export interface BusinessPeriodCloseApi {
  periods: {
    getSummary(periodId: ResourceId): Promise<BusinessPeriodClosePeriodSummary>
  }
  runs: {
    list(params: BusinessPeriodCloseListParams): Promise<PageResult<BusinessPeriodCloseRunRecord>>
    get(runId: ResourceId): Promise<BusinessPeriodCloseRunDetail>
    close(runId: ResourceId, payload: BusinessPeriodCloseClosePayload): Promise<BusinessPeriodCloseRunDetail>
    reopen(runId: ResourceId, payload: BusinessPeriodCloseReopenPayload): Promise<BusinessPeriodCloseRunDetail>
  }
  checks: {
    create(payload: BusinessPeriodCloseCheckCreatePayload): Promise<BusinessPeriodCloseRunDetail>
    history(runId: ResourceId, params: Omit<BusinessPeriodCloseCheckListParams, 'checkRunId'>): Promise<PageResult<BusinessPeriodCloseCheckRun>>
    items(runId: ResourceId, checkRunId: ResourceId, params: Omit<BusinessPeriodCloseCheckListParams, 'checkRunId'>): Promise<PageResult<BusinessPeriodCloseCheckItem>>
  }
  snapshots: {
    get(runId: ResourceId): Promise<BusinessPeriodCloseSnapshotOverview>
    inventory(runId: ResourceId, params: BusinessPeriodCloseSnapshotListParams): Promise<PageResult<BusinessPeriodCloseSnapshotInventoryRecord>>
    wip(runId: ResourceId, params: BusinessPeriodCloseSnapshotListParams): Promise<PageResult<BusinessPeriodCloseSnapshotWipRecord>>
    projectCosts(runId: ResourceId, params: BusinessPeriodCloseSnapshotListParams): Promise<PageResult<BusinessPeriodCloseSnapshotProjectCostRecord>>
    report(runId: ResourceId, reportCode: BusinessPeriodCloseReportCode): Promise<BusinessPeriodCloseSnapshotReport>
  }
}

export interface BusinessPeriodCloseApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

type ApiRecord = Record<string, unknown>

const allowedPageSizes = [10, 20, 50, 100] as const

const reportNames: Record<BusinessPeriodCloseReportCode, string> = {
  OVERVIEW: '经营概览',
  SALES_SUMMARY: '销售汇总',
  PROCUREMENT_SUMMARY: '采购汇总',
  INVENTORY_STOCK_FLOW: '库存收发存',
  PRODUCTION_EXECUTION: '生产执行',
  COST_COLLECTION: '成本归集',
  SETTLEMENT_SUMMARY: '往来结算',
  EXCEPTIONS: '异常清单',
}

function asRecord(value: unknown): ApiRecord {
  return value && typeof value === 'object' ? value as ApiRecord : {}
}

function asPage<T>(value: unknown, mapper: (item: ApiRecord) => T): PageResult<T> {
  const raw = asRecord(value)
  const rawItems = Array.isArray(raw.items)
    ? raw.items
    : Array.isArray(raw.records)
      ? raw.records
      : Array.isArray(raw.content)
        ? raw.content
        : []
  const items = rawItems.map((item) => mapper(asRecord(item)))
  return {
    ...raw,
    items,
    total: typeof raw.total === 'number' ? raw.total : items.length,
    page: typeof raw.page === 'number' ? raw.page : 1,
    pageSize: typeof raw.pageSize === 'number' ? raw.pageSize : 10,
  } as PageResult<T>
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

function stringValue(raw: ApiRecord, ...keys: string[]): string {
  for (const key of keys) {
    const value = raw[key]
    if (value !== undefined && value !== null) {
      return String(value)
    }
  }
  return ''
}

function optionalString(raw: ApiRecord, ...keys: string[]): string | null {
  const value = stringValue(raw, ...keys)
  return value || null
}

function decimalValue(raw: ApiRecord, ...keys: string[]): PeriodCloseDecimal | null {
  for (const key of keys) {
    if (raw[key] !== undefined) {
      return raw[key] === null ? null : String(raw[key])
    }
  }
  return null
}

function numberValue(raw: ApiRecord, key: string, fallback = 0): number {
  return typeof raw[key] === 'number' ? raw[key] as number : fallback
}

function pageSizeValue(value: unknown): number {
  const pageSize = typeof value === 'number' ? value : Number(value)
  return allowedPageSizes.includes(pageSize as typeof allowedPageSizes[number]) ? pageSize : 10
}

function booleanValue(raw: ApiRecord, key: string, fallback: boolean): boolean {
  return typeof raw[key] === 'boolean' ? raw[key] as boolean : fallback
}

function mapVisibility(raw: ApiRecord): BusinessPeriodCloseVisibility {
  const sourceRestricted = raw.sourceRestricted === true
  return {
    amountVisible: booleanValue(raw, 'amountVisible', !sourceRestricted),
    sourceVisible: booleanValue(raw, 'sourceVisible', !sourceRestricted) && !sourceRestricted,
    restrictedReason: optionalString(raw, 'restrictedReason'),
  }
}

function mapActionState(raw: ApiRecord): BusinessPeriodCloseActionState {
  const disabledReasons = asRecord(raw.actionDisabledReasons)
  return {
    version: numberValue(raw, 'version'),
    sourceFingerprint: optionalString(raw, 'sourceFingerprint'),
    allowedActions: Array.isArray(raw.allowedActions) ? raw.allowedActions.map(String) : [],
    actionDisabledReasons: Object.fromEntries(Object.entries(disabledReasons).map(([key, value]) => [key, String(value)])),
  }
}

function mapRunRecord(raw: ApiRecord): BusinessPeriodCloseRunRecord {
  return {
    ...raw,
    ...mapVisibility(raw),
    ...mapActionState(raw),
    runId: idValue(raw, 'runId', 'id', 'currentRunId'),
    periodId: idValue(raw, 'periodId'),
    periodCode: stringValue(raw, 'periodCode'),
    periodName: optionalString(raw, 'periodName'),
    startDate: stringValue(raw, 'startDate'),
    endDate: stringValue(raw, 'endDate'),
    periodStatus: stringValue(raw, 'periodStatus') as BusinessPeriodClosePeriodStatus,
    periodStatusName: optionalString(raw, 'periodStatusName'),
    closeStatus: stringValue(raw, 'closeStatus', 'status') as BusinessPeriodCloseStatus,
    closeStatusName: optionalString(raw, 'closeStatusName', 'statusName'),
    revisionNo: numberValue(raw, 'revisionNo', numberValue(raw, 'currentRevisionNo', 1)),
    latestCheckId: optionalIdValue(raw, 'latestCheckId', 'latestCheckRunId'),
    latestCheckedAt: optionalString(raw, 'latestCheckedAt'),
    latestCheckResult: optionalString(raw, 'latestCheckResult'),
    blockingCount: numberValue(raw, 'blockingCount'),
    warningCount: numberValue(raw, 'warningCount'),
    snapshotId: optionalIdValue(raw, 'snapshotId', 'currentSnapshotId'),
    snapshotValueAmount: decimalValue(raw, 'snapshotValueAmount', 'snapshotAmount', 'totalAmount'),
    closedByName: optionalString(raw, 'closedByName'),
    closedAt: optionalString(raw, 'closedAt'),
    reopenedByName: optionalString(raw, 'reopenedByName'),
    reopenedAt: optionalString(raw, 'reopenedAt'),
  }
}

function mapRunVersion(raw: ApiRecord): BusinessPeriodCloseRunVersion {
  return {
    runId: idValue(raw, 'runId', 'id'),
    revisionNo: numberValue(raw, 'revisionNo', 1),
    closeStatus: stringValue(raw, 'closeStatus', 'status'),
    closedAt: optionalString(raw, 'closedAt'),
    reopenedAt: optionalString(raw, 'reopenedAt'),
  }
}

function mapAudit(raw: ApiRecord): BusinessPeriodCloseAuditRecord {
  return {
    action: stringValue(raw, 'action'),
    operatorUsername: optionalString(raw, 'operatorUsername'),
    operatorName: optionalString(raw, 'operatorName'),
    reason: optionalString(raw, 'reason'),
    createdAt: stringValue(raw, 'createdAt'),
  }
}

function mapRunDetail(raw: ApiRecord): BusinessPeriodCloseRunDetail {
  const historySource = Array.isArray(raw.historyVersions)
    ? raw.historyVersions
    : Array.isArray(raw.history)
      ? raw.history
      : []
  return {
    ...mapRunRecord(raw),
    historyVersions: historySource.map((item) => mapRunVersion(asRecord(item))),
    auditSummary: Array.isArray(raw.auditSummary) ? raw.auditSummary.map((item) => mapAudit(asRecord(item))) : [],
  }
}

function mapPeriodSummary(raw: ApiRecord): BusinessPeriodClosePeriodSummary {
  const versionSource = Array.isArray(raw.versions)
    ? raw.versions
    : Array.isArray(raw.history)
      ? raw.history
      : []
  return {
    ...raw,
    ...mapActionState(raw),
    periodId: idValue(raw, 'periodId', 'id'),
    periodCode: stringValue(raw, 'periodCode'),
    periodName: optionalString(raw, 'periodName'),
    closeStatus: stringValue(raw, 'closeStatus', 'status'),
    closeStatusName: optionalString(raw, 'closeStatusName', 'statusName'),
    currentRunId: optionalIdValue(raw, 'currentRunId', 'runId'),
    currentRevisionNo: typeof raw.currentRevisionNo === 'number' ? raw.currentRevisionNo : null,
    latestCheckId: optionalIdValue(raw, 'latestCheckId', 'latestCheckRunId'),
    latestCheckedAt: optionalString(raw, 'latestCheckedAt'),
    blockingCount: typeof raw.blockingCount === 'number' ? raw.blockingCount : null,
    warningCount: typeof raw.warningCount === 'number' ? raw.warningCount : null,
    snapshotId: optionalIdValue(raw, 'snapshotId', 'currentSnapshotId'),
    versions: versionSource.map((item) => mapRunVersion(asRecord(item))),
  }
}

function mapCheckRun(raw: ApiRecord): BusinessPeriodCloseCheckRun {
  return {
    checkRunId: idValue(raw, 'checkRunId', 'id'),
    runId: idValue(raw, 'runId'),
    startedAt: optionalString(raw, 'startedAt'),
    completedAt: optionalString(raw, 'completedAt'),
    operatorName: optionalString(raw, 'operatorName', 'startedBy'),
    result: optionalString(raw, 'result', 'checkResult', 'status'),
    blockingCount: numberValue(raw, 'blockingCount'),
    warningCount: numberValue(raw, 'warningCount'),
    sourceFingerprint: optionalString(raw, 'sourceFingerprint'),
    partitionFingerprints: asRecord(raw.partitionFingerprints) as Record<string, string>,
  }
}

function mapSourceRoute(raw: unknown): BusinessPeriodCloseSourceRoute | null {
  let value = raw
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value) as unknown
    } catch {
      return null
    }
  }
  const route = asRecord(value)
  if (!route.name && !route.path) {
    return null
  }
  return route as BusinessPeriodCloseSourceRoute
}

function mapCheckItem(raw: ApiRecord): BusinessPeriodCloseCheckItem {
  return {
    ...raw,
    ...mapVisibility(raw),
    id: idValue(raw, 'id', 'itemId'),
    checkRunId: idValue(raw, 'checkRunId'),
    domain: stringValue(raw, 'domain') as BusinessPeriodCloseCheckDomain,
    severity: stringValue(raw, 'severity') as BusinessPeriodCloseCheckSeverity,
    checkCode: stringValue(raw, 'checkCode', 'code'),
    title: stringValue(raw, 'title'),
    description: stringValue(raw, 'description'),
    objectType: optionalString(raw, 'objectType'),
    objectId: optionalIdValue(raw, 'objectId'),
    objectNo: optionalString(raw, 'objectNo'),
    businessImpact: optionalString(raw, 'businessImpact', 'impact', 'description'),
    suggestion: optionalString(raw, 'suggestion'),
    sourceRoute: mapSourceRoute(raw.sourceRoute ?? raw.sourceRouteJson),
  }
}

function mapSnapshotPartition(raw: ApiRecord): BusinessPeriodCloseSnapshotPartition {
  return {
    ...raw,
    ...mapVisibility(raw),
    code: stringValue(raw, 'code'),
    name: stringValue(raw, 'name'),
    recordCount: typeof raw.recordCount === 'number'
      ? raw.recordCount
      : typeof raw.itemCount === 'number'
        ? raw.itemCount
        : null,
    sourceFingerprint: optionalString(raw, 'sourceFingerprint'),
  }
}

function mapSnapshotOverview(raw: ApiRecord): BusinessPeriodCloseSnapshotOverview {
  const partitions = Array.isArray(raw.partitions)
    ? raw.partitions.map((item) => mapSnapshotPartition(asRecord(item)))
    : fallbackSnapshotPartitions(raw)
  return {
    snapshotId: idValue(raw, 'snapshotId', 'id'),
    runId: idValue(raw, 'runId'),
    periodCode: stringValue(raw, 'periodCode'),
    startDate: optionalString(raw, 'startDate'),
    endDate: optionalString(raw, 'endDate'),
    generatedBy: optionalString(raw, 'generatedBy'),
    closeStatus: optionalString(raw, 'closeStatus', 'status'),
    revisionNo: numberValue(raw, 'revisionNo', 1),
    generatedAt: stringValue(raw, 'generatedAt'),
    sourceCheckRunId: optionalIdValue(raw, 'sourceCheckRunId'),
    sourceFingerprint: optionalString(raw, 'sourceFingerprint', 'fingerprint'),
    isHistoricalRevision: booleanValue(raw, 'isHistoricalRevision', optionalString(raw, 'status') === 'REOPENED'),
    partitions,
  }
}

function fallbackSnapshotPartitions(raw: ApiRecord): BusinessPeriodCloseSnapshotPartition[] {
  const partitions: BusinessPeriodCloseSnapshotPartition[] = []
  if (raw.inventoryItemCount !== undefined) {
    partitions.push({
      amountVisible: booleanValue(raw, 'inventoryAmountVisible', true),
      code: 'INVENTORY',
      name: '库存快照',
      recordCount: numberValue(raw, 'inventoryItemCount'),
      restrictedReason: booleanValue(raw, 'inventoryAmountVisible', true) ? null : '缺少库存金额权限',
      sourceFingerprint: optionalString(raw, 'inventoryFingerprint'),
      sourceVisible: booleanValue(raw, 'sourceVisible', true),
    })
  }
  if (raw.wipItemCount !== undefined) {
    partitions.push({
      amountVisible: booleanValue(raw, 'projectCostAmountVisible', true),
      code: 'WIP',
      name: '在制/生产',
      recordCount: numberValue(raw, 'wipItemCount'),
      restrictedReason: booleanValue(raw, 'projectCostAmountVisible', true) ? null : '缺少项目成本金额权限',
      sourceFingerprint: optionalString(raw, 'wipFingerprint'),
      sourceVisible: booleanValue(raw, 'sourceVisible', true),
    })
  }
  if (raw.projectCostItemCount !== undefined) {
    partitions.push({
      amountVisible: booleanValue(raw, 'projectCostAmountVisible', true),
      code: 'PROJECT_COST',
      name: '项目成本',
      recordCount: numberValue(raw, 'projectCostItemCount'),
      restrictedReason: booleanValue(raw, 'projectCostAmountVisible', true) ? null : '缺少项目成本金额权限',
      sourceFingerprint: optionalString(raw, 'projectCostFingerprint'),
      sourceVisible: booleanValue(raw, 'sourceVisible', true),
    })
  }
  if (Array.isArray(raw.reportCodes)) {
    partitions.push({
      amountVisible: true,
      code: 'REPORTS',
      name: '经营报表基线',
      recordCount: raw.reportCodes.length,
      restrictedReason: null,
      sourceFingerprint: optionalString(raw, 'reportFingerprint'),
      sourceVisible: booleanValue(raw, 'sourceVisible', true),
    })
  }
  return partitions
}

function mapInventorySnapshot(raw: ApiRecord): BusinessPeriodCloseSnapshotInventoryRecord {
  return {
    ...raw,
    ...mapVisibility(raw),
    id: idValue(raw, 'id'),
    materialCode: optionalString(raw, 'materialCode'),
    materialName: optionalString(raw, 'materialName'),
    warehouseName: optionalString(raw, 'warehouseName'),
    projectNo: optionalString(raw, 'projectNo'),
    endingQuantity: decimalValue(raw, 'endingQuantity'),
    lockedQuantity: decimalValue(raw, 'lockedQuantity'),
    availableQuantity: decimalValue(raw, 'availableQuantity'),
    inboundQuantity: decimalValue(raw, 'inboundQuantity', 'inQuantity'),
    outboundQuantity: decimalValue(raw, 'outboundQuantity', 'outQuantity'),
    adjustmentQuantity: decimalValue(raw, 'adjustmentQuantity'),
    unitCost: decimalValue(raw, 'unitCost'),
    endingValue: decimalValue(raw, 'endingValue', 'endingAmount'),
  }
}

function mapWipSnapshot(raw: ApiRecord): BusinessPeriodCloseSnapshotWipRecord {
  return {
    ...raw,
    ...mapVisibility(raw),
    id: idValue(raw, 'id'),
    projectNo: optionalString(raw, 'projectNo'),
    projectName: optionalString(raw, 'projectName'),
    workOrderNo: optionalString(raw, 'workOrderNo'),
    materialCode: optionalString(raw, 'materialCode', 'productMaterialCode'),
    materialName: optionalString(raw, 'materialName', 'productMaterialName'),
    stage: optionalString(raw, 'stage', 'status'),
    wipQuantity: decimalValue(raw, 'wipQuantity'),
    wipAmount: decimalValue(raw, 'wipAmount', 'wipCost'),
    sourceSummary: optionalString(raw, 'sourceSummary'),
  }
}

function mapProjectCostSnapshot(raw: ApiRecord): BusinessPeriodCloseSnapshotProjectCostRecord {
  return {
    ...raw,
    ...mapVisibility(raw),
    id: idValue(raw, 'id'),
    projectId: optionalIdValue(raw, 'projectId'),
    projectNo: optionalString(raw, 'projectNo'),
    projectName: optionalString(raw, 'projectName'),
    calculationId: optionalIdValue(raw, 'calculationId'),
    calculationNo: optionalString(raw, 'calculationNo'),
    cutoffDate: optionalString(raw, 'cutoffDate'),
    totalCost: decimalValue(raw, 'totalCost', 'projectCostTotal'),
    wipCost: decimalValue(raw, 'wipCost'),
    deliveredCost: decimalValue(raw, 'deliveredCost'),
    revenueAmount: decimalValue(raw, 'revenueAmount', 'shipmentRevenue', 'shipmentPretaxRevenue'),
    grossMarginAmount: decimalValue(raw, 'grossMarginAmount', 'shipmentGrossMargin'),
    grossMarginRate: decimalValue(raw, 'grossMarginRate', 'shipmentGrossMarginRate'),
    completenessStatus: optionalString(raw, 'completenessStatus'),
    sourceFingerprint: optionalString(raw, 'sourceFingerprint'),
  }
}

function mapReportSnapshot(raw: ApiRecord): BusinessPeriodCloseSnapshotReport {
  const reportCode = stringValue(raw, 'reportCode') as BusinessPeriodCloseReportCode
  return {
    ...raw,
    ...mapVisibility(raw),
    reportCode,
    reportName: stringValue(raw, 'reportName') || reportNames[reportCode] || reportCode,
    schemaVersion: numberValue(raw, 'schemaVersion', 1),
    generatedAt: stringValue(raw, 'generatedAt', 'createdAt'),
    sourceCount: numberValue(raw, 'sourceCount'),
    sourceFingerprint: optionalString(raw, 'sourceFingerprint', 'fingerprint'),
    result: mapReportResult(raw),
  }
}

function mapReportResult(raw: ApiRecord): Record<string, unknown> {
  if (raw.result !== undefined) {
    return asRecord(raw.result)
  }
  if (typeof raw.resultJson === 'string') {
    try {
      return asRecord(JSON.parse(raw.resultJson) as unknown)
    } catch {
      return {}
    }
  }
  return {}
}

export function createBusinessPeriodCloseApi(options: BusinessPeriodCloseApiOptions = {}): BusinessPeriodCloseApi {
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
  const write = async <T>(path: string, body: object) => {
    const csrf = await getCsrf()
    return request<T>(path, {
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        [csrf.headerName]: csrf.token,
      },
      method: 'POST',
    })
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
  const pageQuery = (query: { page?: number; pageSize?: number }) => ({
    page: query.page ?? 1,
    pageSize: pageSizeValue(query.pageSize ?? 10),
  })
  const listQuery = (query: BusinessPeriodCloseListParams) => ({
    periodCode: query.periodCode,
    startDate: query.startDate,
    endDate: query.endDate,
    status: query.closeStatus,
    checkResult: query.checkResult,
    hasBlocking: query.hasBlocking,
    page: query.page,
    pageSize: pageSizeValue(query.pageSize),
  })
  const encodeId = (id: ResourceId) => encodeURIComponent(String(id))
  const closePath = (runId?: ResourceId) =>
    `/api/admin/period-closes${runId === undefined ? '' : `/${encodeId(runId)}`}`
  const pageQueryKeys = ['page', 'pageSize'] as const

  return {
    periods: {
      getSummary: (periodId) => get<unknown>(`${closePath()}/periods/${encodeId(periodId)}`).then((record) => mapPeriodSummary(asRecord(record))),
    },
    runs: {
      list: (params) => get<unknown>(closePath(), listQuery(params)).then((page) => asPage(page, mapRunRecord)),
      get: (runId) => get<unknown>(closePath(runId)).then((record) => mapRunDetail(asRecord(record))),
      close: (runId, payload) => write<unknown>(`${closePath(runId)}/close`, payload).then((record) => mapRunDetail(asRecord(record))),
      reopen: (runId, payload) => write<unknown>(`${closePath(runId)}/reopen`, payload).then((record) => mapRunDetail(asRecord(record))),
    },
    checks: {
      create: (payload) => write<unknown>(`${closePath()}/checks`, payload).then((record) => mapRunDetail(asRecord(record))),
      history: (runId, params) => get<unknown>(`${closePath(runId)}/checks`, pageQuery(pickQuery(params, pageQueryKeys))).then((page) => asPage(page, mapCheckRun)),
      items: (runId, checkRunId, params) =>
        get<unknown>(
          `${closePath(runId)}/checks/${encodeId(checkRunId)}/items`,
          pageQuery(pickQuery(params, pageQueryKeys)),
        ).then((page) => asPage(page, mapCheckItem)),
    },
    snapshots: {
      get: (runId) => get<unknown>(`${closePath(runId)}/snapshot`).then((record) => mapSnapshotOverview(asRecord(record))),
      inventory: (runId, params) =>
        get<unknown>(`${closePath(runId)}/snapshot/inventory`, pageQuery(pickQuery(params, pageQueryKeys))).then((page) =>
          asPage(page, mapInventorySnapshot),
        ),
      wip: (runId, params) =>
        get<unknown>(`${closePath(runId)}/snapshot/wip`, pageQuery(pickQuery(params, pageQueryKeys))).then((page) =>
          asPage(page, mapWipSnapshot),
        ),
      projectCosts: (runId, params) =>
        get<unknown>(`${closePath(runId)}/snapshot/project-costs`, pageQuery(pickQuery(params, pageQueryKeys))).then((page) =>
          asPage(page, mapProjectCostSnapshot),
        ),
      report: (runId, reportCode) =>
        get<unknown>(`${closePath(runId)}/snapshot/reports/${encodeURIComponent(reportCode)}`).then((record) =>
          mapReportSnapshot(asRecord(record)),
        ),
    },
  }
}

export const businessPeriodCloseApi = createBusinessPeriodCloseApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
