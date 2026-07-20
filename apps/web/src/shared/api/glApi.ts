import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type GlAmount = string

export type GlAccountCategory = 'ASSET' | 'LIABILITY' | 'COMMON' | 'EQUITY' | 'COST' | 'PROFIT_LOSS'
export type GlBalanceDirection = 'DEBIT' | 'CREDIT'
export type GlVoucherStatus = 'DRAFT' | 'SUBMITTED' | 'POSTED' | 'CANCELLED'
export type GlVoucherType = 'GENERAL' | 'OPENING'
export type GlVoucherSourceType = 'MANUAL' | 'FIN_VOUCHER_DRAFT' | 'REVERSAL' | string

export interface GlApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export interface GlVersionedActionPayload {
  version: number
  idempotencyKey: string
  reason?: string
}

export interface GlRulePreviewContext {
  sourceType?: string
  sourceId?: ResourceId
  sourceNo?: string
  sourceVersion?: number | string
  sourceFingerprint?: string
  businessDate?: string
}

export interface GlPostingRuleValidatePayload extends GlVersionedActionPayload {
  sourceType?: string
  sourceId?: ResourceId
  sourceVersion?: number | string
}

export interface GlAuxItemPayload {
  code: string
  name: string
  enabled: boolean
  version: number
  idempotencyKey: string
}

export interface GlPeriodCreatePayload {
  periodCode: string
  version?: number
  idempotencyKey: string
}

export interface GlLedgerRecord {
  id?: ResourceId
  ledgerCode?: string
  ledgerName?: string
  baseCurrency: 'CNY' | string
  initialized: boolean
  startPeriodCode?: string | null
  version?: number | null
}

export interface GlAccountingPeriodRecord {
  id?: ResourceId
  periodCode: string
  startDate: string
  endDate: string
  status: 'OPEN' | string
  voucherCount?: number | null
  lastPostedAt?: string | null
  version?: number | null
  financialCloseStatus?: string | null
  latestFinancialCloseCheckRunId?: ResourceId | null
  financialCloseDisabledReason?: string | null
}

export interface GlAuxRequirement {
  dimensionCode: string
  dimensionName?: string | null
  requirement: 'REQUIRED' | 'OPTIONAL' | string
}

export interface GlActionState {
  version: number
  allowedActions: string[]
  actionDisabledReasons: Record<string, string>
}

export interface GlAccountRecord extends GlActionState {
  id: ResourceId
  code: string
  name: string
  category: GlAccountCategory | string
  level: number
  parentId?: ResourceId | null
  isLeaf: boolean
  postable: boolean
  balanceDirection: GlBalanceDirection | string
  enabled: boolean
  auxiliaryRequirements: GlAuxRequirement[]
  referenced?: boolean | null
}

export interface GlAuxDimensionRecord extends GlActionState {
  id: ResourceId
  code: string
  name: string
  dimensionType: 'SYSTEM' | 'CUSTOM' | string
  enabled: boolean
  itemCount?: number | null
}

export interface GlAuxCandidateRecord {
  id?: ResourceId | null
  objectId?: ResourceId | null
  sourceId?: ResourceId | null
  auxItemId?: ResourceId | null
  objectCode?: string | null
  objectName?: string | null
  enabled?: boolean | null
  version?: number | null
  allowedActions?: string[] | null
  actionDisabledReasons?: Record<string, string> | null
  restricted?: boolean | null
  restrictedReason?: string | null
}

export interface GlPostingRuleLineRecord {
  lineNo?: number | null
  normalizedFactCode?: string | null
  direction?: string | null
  accountId?: ResourceId | null
  accountCode?: string | null
  summaryTemplate?: string | null
  auxiliaryMappings?: unknown[]
}

export interface GlPostingRulePreviewLineRecord {
  lineNo?: number | null
  normalizedFactCode?: string | null
  direction?: string | null
  accountId?: ResourceId | null
  accountCode?: string | null
  accountName?: string | null
  summary?: string | null
  summaryTemplate?: string | null
  amount?: GlAmount | null
  debitAmount?: GlAmount | null
  creditAmount?: GlAmount | null
  auxiliaryMappings?: unknown[]
  auxiliaryValues?: unknown[]
}

export interface GlPostingRuleValidationSummary {
  balanced?: boolean | null
  sourcePreview?: boolean | null
  previewOnly?: boolean | null
  lineCount?: number | null
  factCount?: number | null
  debitTotal?: GlAmount | null
  creditTotal?: GlAmount | null
  previewLines?: GlPostingRulePreviewLineRecord[]
}

export interface GlPostingRuleRecord extends GlActionState {
  id: ResourceId
  name?: string | null
  description?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  sourceType: string
  sourceVariant: string
  versionNo: number
  status: 'DRAFT' | 'ACTIVE' | 'SUPERSEDED' | 'DISABLED' | string
  validationStatus?: string | null
  lineCount?: number | null
  lines?: GlPostingRuleLineRecord[]
  validationSummary?: GlPostingRuleValidationSummary | null
}

export interface GlVoucherLineRecord {
  id?: ResourceId
  lineNo: number
  summary: string
  accountId: ResourceId
  accountCode?: string | null
  accountName?: string | null
  debitAmount: GlAmount
  creditAmount: GlAmount
  auxiliaryItems: Array<{
    dimensionCode: string
    dimensionName?: string | null
    objectId?: ResourceId | null
    sourceId?: ResourceId | null
    auxItemId?: ResourceId | null
    objectCode?: string | null
    objectName?: string | null
    restricted?: boolean | null
    restrictedReason?: string | null
  }>
  normalizedFactCode?: string | null
  sourceRoute?: unknown
}

export interface GlApprovalSummary {
  id?: ResourceId | null
  sceneCode?: string | null
  status?: string | null
  submittedAt?: string | null
}

export interface GlVoucherRecord extends GlActionState {
  id: ResourceId
  draftNo: string
  voucherType?: GlVoucherType | string
  voucherNo?: string | null
  voucherDate?: string | null
  accountingPeriodCode?: string | null
  status: GlVoucherStatus | string
  summary?: string | null
  sourceType?: GlVoucherSourceType | null
  sourceId?: ResourceId | null
  sourceOriginalType?: string | null
  businessSourceType?: string | null
  sourceOriginalId?: ResourceId | null
  businessSourceId?: ResourceId | null
  sourceOriginalNo?: string | null
  businessSourceNo?: string | null
  sourceOriginalVersion?: number | null
  businessSourceVersion?: number | null
  sourceOriginalFingerprint?: string | null
  businessSourceFingerprint?: string | null
  sourceNo?: string | null
  currency?: 'CNY' | string
  debitTotal?: GlAmount | null
  creditTotal?: GlAmount | null
  amountVisible?: boolean | null
  sourceVisible?: boolean | null
  restrictedReason?: string | null
  lines?: GlVoucherLineRecord[]
  approvalSummary?: GlApprovalSummary | null
  reversalSummary?: unknown
  auditSummary?: unknown[]
}

export interface GlVoucherPayload {
  voucherType: GlVoucherType | string
  voucherDate: string
  summary: string
  lines: GlVoucherLineRecord[]
  idempotencyKey: string
}

export interface GlLedgerRow {
  periodCode?: string | null
  voucherDate?: string | null
  voucherNo?: string | null
  voucherId?: ResourceId | null
  summary?: string | null
  accountCode: string
  accountName: string
  openingDebit?: GlAmount | null
  openingCredit?: GlAmount | null
  periodDebit?: GlAmount | null
  periodCredit?: GlAmount | null
  endingDebit?: GlAmount | null
  endingCredit?: GlAmount | null
  debitAmount?: GlAmount | null
  creditAmount?: GlAmount | null
  runningBalance?: GlAmount | null
  balanceDirection?: GlBalanceDirection | string | null
  balanced?: boolean | null
  restricted?: boolean | null
  restrictedReason?: string | null
  sourceSummary?: string | null
  sourceRoute?: unknown
}

export interface GlTrialBalanceRecord {
  balanced: boolean
  openingDebitTotal?: GlAmount | null
  openingCreditTotal?: GlAmount | null
  periodDebitTotal?: GlAmount | null
  periodCreditTotal?: GlAmount | null
  endingDebitTotal?: GlAmount | null
  endingCreditTotal?: GlAmount | null
  differenceAmount?: GlAmount | null
  differences?: Array<{ accountCode?: string | null; accountName?: string | null; differenceAmount?: GlAmount | null }>
  restricted?: boolean | null
  restrictedReason?: string | null
}

export type GlPageParams = Record<string, unknown> & { page: number; pageSize: number }

type RawRecord = Record<string, unknown>

function isRecord(value: unknown): value is RawRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function normalizeAllowedActions(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value
    .map((item) => {
      if (typeof item === 'string') {
        return item
      }
      if (isRecord(item) && item.enabled !== false && typeof item.code === 'string') {
        return item.code
      }
      return ''
    })
    .filter((item) => item.length > 0)
}

function normalizeActionState<T extends RawRecord>(record: T): T & GlActionState {
  return {
    ...record,
    version: Number(record.version ?? 0),
    allowedActions: normalizeAllowedActions(record.allowedActions),
    actionDisabledReasons: isRecord(record.actionDisabledReasons) ? (record.actionDisabledReasons as Record<string, string>) : {},
  }
}

function normalizePageItems<T>(page: RawRecord, normalize: (item: RawRecord) => T): unknown {
  const itemKeys = ['items', 'records', 'content'] as const
  const normalized = { ...page }
  itemKeys.forEach((key) => {
    if (Array.isArray(page[key])) {
      normalized[key] = page[key].map((item) => isRecord(item) ? normalize(item) : item)
    }
  })
  return normalized
}

function normalizeRecordOrPage<T>(data: unknown, normalize: (item: RawRecord) => T): unknown {
  if (!isRecord(data)) {
    return data
  }
  if (Array.isArray(data.items) || Array.isArray(data.records) || Array.isArray(data.content)) {
    return normalizePageItems(data, normalize)
  }
  return normalize(data)
}

function normalizeAuxRequirement(item: unknown): GlAuxRequirement {
  const record = isRecord(item) ? item : {}
  return {
    ...record,
    dimensionCode: String(record.dimensionCode ?? ''),
    dimensionName: record.dimensionName as string | null | undefined,
    requirement: String(record.requirement ?? record.requirementType ?? ''),
  }
}

function normalizeAccount(record: RawRecord): GlAccountRecord {
  const normalized = normalizeActionState(record)
  return {
    ...normalized,
    id: (normalized.id ?? normalized.accountId) as ResourceId,
    code: String(normalized.code ?? normalized.accountCode ?? ''),
    name: String(normalized.name ?? normalized.accountName ?? ''),
    category: String(normalized.category ?? ''),
    level: Number(normalized.level ?? normalized.levelNo ?? 0),
    parentId: normalized.parentId as ResourceId | null | undefined,
    isLeaf: Boolean(normalized.isLeaf),
    postable: Boolean(normalized.postable),
    balanceDirection: String(normalized.balanceDirection ?? ''),
    enabled: normalized.enabled !== false,
    auxiliaryRequirements: Array.isArray(normalized.auxiliaryRequirements)
      ? normalized.auxiliaryRequirements.map(normalizeAuxRequirement)
      : [],
    referenced: normalized.referenced as boolean | null | undefined,
  }
}

function normalizeAuxCandidate(record: RawRecord): GlAuxCandidateRecord {
  const normalized = normalizeActionState(record)
  const objectId = normalized.objectId ?? normalized.sourceId ?? normalized.auxItemId ?? normalized.id
  return {
    ...normalized,
    id: normalized.id as ResourceId | null | undefined,
    objectId: objectId as ResourceId | null | undefined,
    sourceId: normalized.sourceId as ResourceId | null | undefined,
    auxItemId: normalized.auxItemId as ResourceId | null | undefined,
    objectCode: (normalized.objectCode ?? normalized.code) as string | null | undefined,
    objectName: (normalized.objectName ?? normalized.name) as string | null | undefined,
    enabled: normalized.enabled as boolean | null | undefined,
    version: normalized.version,
    allowedActions: normalized.allowedActions,
    actionDisabledReasons: normalized.actionDisabledReasons,
    restricted: normalized.restricted as boolean | null | undefined,
    restrictedReason: normalized.restrictedReason as string | null | undefined,
  }
}

function normalizeAuxDimension(record: RawRecord): GlAuxDimensionRecord {
  const normalized = normalizeActionState(record)
  return {
    ...normalized,
    id: normalized.id as ResourceId,
    code: String(normalized.code ?? ''),
    name: String(normalized.name ?? ''),
    dimensionType: String(normalized.dimensionType ?? ''),
    enabled: normalized.enabled !== false,
    itemCount: normalized.itemCount as number | null | undefined,
  }
}

function normalizePostingRuleLine(item: unknown): GlPostingRuleLineRecord {
  const record = isRecord(item) ? item : {}
  return {
    ...record,
    lineNo: record.lineNo as number | null | undefined,
    normalizedFactCode: (record.normalizedFactCode ?? record.factCode) as string | null | undefined,
    direction: record.direction as string | null | undefined,
    accountId: record.accountId as ResourceId | null | undefined,
    accountCode: record.accountCode as string | null | undefined,
    summaryTemplate: record.summaryTemplate as string | null | undefined,
    auxiliaryMappings: Array.isArray(record.auxiliaryMappings) ? record.auxiliaryMappings : [],
  }
}

function normalizePostingRulePreviewLine(item: unknown): GlPostingRulePreviewLineRecord {
  const record = isRecord(item) ? item : {}
  return {
    ...record,
    lineNo: record.lineNo as number | null | undefined,
    normalizedFactCode: (record.normalizedFactCode ?? record.factCode) as string | null | undefined,
    direction: record.direction as string | null | undefined,
    accountId: record.accountId as ResourceId | null | undefined,
    accountCode: record.accountCode as string | null | undefined,
    accountName: record.accountName as string | null | undefined,
    summary: record.summary as string | null | undefined,
    summaryTemplate: record.summaryTemplate as string | null | undefined,
    amount: record.amount as GlAmount | null | undefined,
    debitAmount: record.debitAmount as GlAmount | null | undefined,
    creditAmount: record.creditAmount as GlAmount | null | undefined,
    auxiliaryMappings: Array.isArray(record.auxiliaryMappings) ? record.auxiliaryMappings : [],
    auxiliaryValues: Array.isArray(record.auxiliaryValues) ? record.auxiliaryValues : [],
  }
}

function normalizePostingRuleValidationSummary(summary: unknown): GlPostingRuleValidationSummary | null {
  if (!isRecord(summary)) {
    return null
  }
  return {
    ...summary,
    balanced: summary.balanced as boolean | null | undefined,
    sourcePreview: summary.sourcePreview as boolean | null | undefined,
    previewOnly: summary.previewOnly as boolean | null | undefined,
    lineCount: summary.lineCount as number | null | undefined,
    factCount: summary.factCount as number | null | undefined,
    debitTotal: summary.debitTotal as GlAmount | null | undefined,
    creditTotal: summary.creditTotal as GlAmount | null | undefined,
    previewLines: Array.isArray(summary.previewLines) ? summary.previewLines.map(normalizePostingRulePreviewLine) : [],
  }
}

function normalizePostingRule(record: RawRecord): GlPostingRuleRecord {
  const normalized = normalizeActionState(record)
  return {
    ...normalized,
    id: normalized.id as ResourceId,
    name: normalized.name as string | null | undefined,
    description: normalized.description as string | null | undefined,
    effectiveFrom: normalized.effectiveFrom as string | null | undefined,
    effectiveTo: normalized.effectiveTo as string | null | undefined,
    sourceType: String(normalized.sourceType ?? ''),
    sourceVariant: String(normalized.sourceVariant ?? ''),
    versionNo: Number(normalized.versionNo ?? normalized.ruleVersion ?? 0),
    status: String(normalized.status ?? ''),
    validationStatus: normalized.validationStatus as string | null | undefined,
    lineCount: normalized.lineCount as number | null | undefined,
    lines: Array.isArray(normalized.lines) ? normalized.lines.map(normalizePostingRuleLine) : undefined,
    validationSummary: normalizePostingRuleValidationSummary(normalized.validationSummary),
  }
}

function normalizeVoucherAuxiliaryItem(item: unknown): GlVoucherLineRecord['auxiliaryItems'][number] {
  const record = isRecord(item) ? item : {}
  const objectId = record.objectId ?? record.sourceId ?? record.auxItemId
  return {
    ...record,
    dimensionCode: String(record.dimensionCode ?? ''),
    dimensionName: record.dimensionName as string | null | undefined,
    objectId: objectId as ResourceId | null | undefined,
    sourceId: record.sourceId as ResourceId | null | undefined,
    auxItemId: record.auxItemId as ResourceId | null | undefined,
    objectCode: record.objectCode as string | null | undefined,
    objectName: record.objectName as string | null | undefined,
    restricted: record.restricted as boolean | null | undefined,
    restrictedReason: record.restrictedReason as string | null | undefined,
  }
}

function normalizeVoucherLine(record: RawRecord): GlVoucherLineRecord {
  return {
    ...record,
    lineNo: Number(record.lineNo ?? 0),
    summary: String(record.summary ?? ''),
    accountId: record.accountId as ResourceId,
    accountCode: record.accountCode as string | null | undefined,
    accountName: record.accountName as string | null | undefined,
    debitAmount: (record.debitAmount ?? '0.00') as GlAmount,
    creditAmount: (record.creditAmount ?? '0.00') as GlAmount,
    auxiliaryItems: Array.isArray(record.auxiliaryItems) ? record.auxiliaryItems.map(normalizeVoucherAuxiliaryItem) : [],
    normalizedFactCode: record.normalizedFactCode as string | null | undefined,
    sourceRoute: record.sourceRoute,
  }
}

function normalizeVoucher(record: RawRecord): GlVoucherRecord {
  const normalized = normalizeActionState(record)
  return {
    ...normalized,
    id: normalized.id as ResourceId,
    draftNo: String(normalized.draftNo ?? ''),
    status: String(normalized.status ?? ''),
    sourceOriginalNo: (normalized.sourceOriginalNo as string | null | undefined) ?? (normalized.businessSourceNo as string | null | undefined),
    sourceOriginalVersion: (normalized.sourceOriginalVersion as number | null | undefined) ?? (normalized.businessSourceVersion as number | null | undefined),
    sourceOriginalFingerprint: (normalized.sourceOriginalFingerprint as string | null | undefined) ?? (normalized.businessSourceFingerprint as string | null | undefined),
    lines: Array.isArray(normalized.lines) ? normalized.lines.map((line) => isRecord(line) ? normalizeVoucherLine(line) : line as GlVoucherLineRecord) : undefined,
  }
}

function normalizeLedgerRow(record: RawRecord): GlLedgerRow {
  return {
    ...record,
    accountCode: String(record.accountCode ?? ''),
    accountName: String(record.accountName ?? ''),
    sourceSummary: record.sourceSummary as string | null | undefined
      ?? (record.sourceNo ? `${record.sourceType ?? '来源'} ${record.sourceNo}` : undefined),
  }
}

function groupAmount(group: unknown, key: 'debit' | 'credit' | 'difference') {
  if (!isRecord(group)) {
    return undefined
  }
  const totalKey = `${key}Total`
  return group[totalKey] ?? group[`${key}Amount`] ?? group[key]
}

function normalizeTrialBalance(record: RawRecord): GlTrialBalanceRecord {
  const opening = record.opening
  const period = record.period
  const ending = record.ending
  const differenceAmount = record.differenceAmount ?? groupAmount(period, 'difference') ?? groupAmount(ending, 'difference')
  return {
    ...record,
    balanced: Boolean(record.balanced),
    openingDebitTotal: (record.openingDebitTotal as GlAmount | null | undefined) ?? (groupAmount(opening, 'debit') as GlAmount | null | undefined),
    openingCreditTotal: (record.openingCreditTotal as GlAmount | null | undefined) ?? (groupAmount(opening, 'credit') as GlAmount | null | undefined),
    periodDebitTotal: (record.periodDebitTotal as GlAmount | null | undefined) ?? (record.periodDebit as GlAmount | null | undefined) ?? (groupAmount(period, 'debit') as GlAmount | null | undefined),
    periodCreditTotal: (record.periodCreditTotal as GlAmount | null | undefined) ?? (record.periodCredit as GlAmount | null | undefined) ?? (groupAmount(period, 'credit') as GlAmount | null | undefined),
    endingDebitTotal: (record.endingDebitTotal as GlAmount | null | undefined) ?? (groupAmount(ending, 'debit') as GlAmount | null | undefined),
    endingCreditTotal: (record.endingCreditTotal as GlAmount | null | undefined) ?? (groupAmount(ending, 'credit') as GlAmount | null | undefined),
    differenceAmount: differenceAmount as GlAmount | null | undefined,
    differences: Array.isArray(record.differences) ? (record.differences as GlTrialBalanceRecord['differences']) : [],
    restricted: (record.restricted as boolean | null | undefined) ?? record.amountVisible === false,
    restrictedReason: record.restrictedReason as string | null | undefined,
  }
}

function normalizeGlResponse(path: string, data: unknown): unknown {
  if (!path.startsWith('/api/admin/gl')) {
    return data
  }
  if (path.includes('/trial-balance')) {
    return isRecord(data) ? normalizeTrialBalance(data) : data
  }
  if (path.includes('/vouchers')) {
    return normalizeRecordOrPage(data, normalizeVoucher)
  }
  if (path.includes('/accounts') && !path.includes('/account-balances')) {
    return normalizeRecordOrPage(data, normalizeAccount)
  }
  if (path.includes('/aux-dimensions') && (path.includes('/candidates') || path.includes('/items'))) {
    return normalizeRecordOrPage(data, normalizeAuxCandidate)
  }
  if (path.includes('/aux-dimensions')) {
    return normalizeRecordOrPage(data, normalizeAuxDimension)
  }
  if (path.includes('/posting-rules')) {
    return normalizeRecordOrPage(data, normalizePostingRule)
  }
  if (path.includes('/ledgers') || path.includes('/account-balances')) {
    return normalizeRecordOrPage(data, normalizeLedgerRow)
  }
  return data
}

export function createGlApi(options: GlApiOptions = {}) {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')

  const buildUrl = (path: string, query?: Record<string, unknown>) => {
    const search = new URLSearchParams()
    Object.entries(query ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        search.set(key, String(value))
      }
    })
    const queryString = search.toString()
    return `${baseUrl}${path}${queryString ? `?${queryString}` : ''}`
  }

  const request = async <T>(path: string, init: RequestInit, query?: Record<string, unknown>): Promise<T> => {
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
    return normalizeGlResponse(path, envelope.data) as T
  }

  const getCsrf = () => request<CsrfToken>('/api/auth/csrf', { method: 'GET' })
  const get = <T>(path: string, query?: Record<string, unknown>) => request<T>(path, { method: 'GET' }, query)
  const write = async <T>(method: 'POST' | 'PUT', path: string, body?: object) => {
    const csrf = await getCsrf()
    const headers: Record<string, string> = { [csrf.headerName]: csrf.token }
    const init: RequestInit = { method, headers }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }
    return request<T>(path, init)
  }
  const encodeId = (id: ResourceId) => encodeURIComponent(String(id))

  return {
    ledger: {
      get: () => get<GlLedgerRecord>('/api/admin/gl/ledger'),
      initialize: (payload: { startYearMonth: string; idempotencyKey: string }) =>
        write<GlLedgerRecord>('POST', '/api/admin/gl/ledger/initialize', payload),
    },
    accountingPeriods: {
      list: (params: GlPageParams) => get<PageResult<GlAccountingPeriodRecord>>('/api/admin/gl/accounting-periods', params),
      create: (payload: GlPeriodCreatePayload) => write<GlAccountingPeriodRecord>('POST', '/api/admin/gl/accounting-periods', payload),
    },
    accounts: {
      list: (params: GlPageParams) => get<PageResult<GlAccountRecord>>('/api/admin/gl/accounts', params),
      candidates: (params: Record<string, unknown>) => get<PageResult<GlAccountRecord>>('/api/admin/gl/accounts/candidates', params),
      get: (id: ResourceId) => get<GlAccountRecord>(`/api/admin/gl/accounts/${encodeId(id)}`),
      create: (payload: object) => write<GlAccountRecord>('POST', '/api/admin/gl/accounts', payload),
      update: (id: ResourceId, payload: object) => write<GlAccountRecord>('PUT', `/api/admin/gl/accounts/${encodeId(id)}`, payload),
      disable: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlAccountRecord>('POST', `/api/admin/gl/accounts/${encodeId(id)}/disable`, payload),
    },
    auxDimensions: {
      list: (params: GlPageParams) => get<PageResult<GlAuxDimensionRecord>>('/api/admin/gl/aux-dimensions', params),
      create: (payload: object) => write<GlAuxDimensionRecord>('POST', '/api/admin/gl/aux-dimensions', payload),
      update: (id: ResourceId, payload: object) => write<GlAuxDimensionRecord>('PUT', `/api/admin/gl/aux-dimensions/${encodeId(id)}`, payload),
      items: (id: ResourceId, params: GlPageParams) => get<PageResult<GlAuxCandidateRecord>>(`/api/admin/gl/aux-dimensions/${encodeId(id)}/items`, params),
      createItem: (id: ResourceId, payload: GlAuxItemPayload) => write<GlAuxCandidateRecord>('POST', `/api/admin/gl/aux-dimensions/${encodeId(id)}/items`, payload),
      updateItem: (id: ResourceId, itemId: ResourceId, payload: GlAuxItemPayload) => write<GlAuxCandidateRecord>('PUT', `/api/admin/gl/aux-dimensions/${encodeId(id)}/items/${encodeId(itemId)}`, payload),
      candidates: (code: string, params: Record<string, unknown>) => get<PageResult<GlAuxCandidateRecord>>(`/api/admin/gl/aux-dimensions/${encodeURIComponent(code)}/candidates`, params),
    },
    postingRules: {
      list: (params: GlPageParams) => get<PageResult<GlPostingRuleRecord>>('/api/admin/gl/posting-rules', params),
      get: (id: ResourceId) => get<GlPostingRuleRecord>(`/api/admin/gl/posting-rules/${encodeId(id)}`),
      create: (payload: object) => write<GlPostingRuleRecord>('POST', '/api/admin/gl/posting-rules', payload),
      newVersion: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlPostingRuleRecord>('POST', `/api/admin/gl/posting-rules/${encodeId(id)}/new-version`, payload),
      update: (id: ResourceId, payload: object) => write<GlPostingRuleRecord>('PUT', `/api/admin/gl/posting-rules/${encodeId(id)}`, payload),
      validate: (id: ResourceId, payload: GlPostingRuleValidatePayload) => write<GlPostingRuleRecord>('POST', `/api/admin/gl/posting-rules/${encodeId(id)}/validate`, payload),
      activate: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlPostingRuleRecord>('POST', `/api/admin/gl/posting-rules/${encodeId(id)}/activate`, payload),
      disable: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlPostingRuleRecord>('POST', `/api/admin/gl/posting-rules/${encodeId(id)}/disable`, payload),
    },
    vouchers: {
      list: (params: GlPageParams) => get<PageResult<GlVoucherRecord>>('/api/admin/gl/vouchers', params),
      get: (id: ResourceId) => get<GlVoucherRecord>(`/api/admin/gl/vouchers/${encodeId(id)}`),
      create: (payload: GlVoucherPayload) => write<GlVoucherRecord>('POST', '/api/admin/gl/vouchers', payload),
      update: (id: ResourceId, payload: GlVoucherPayload & { version: number }) => write<GlVoucherRecord>('PUT', `/api/admin/gl/vouchers/${encodeId(id)}`, payload),
      fromFinanceDraft: (draftId: ResourceId, payload: GlVersionedActionPayload) =>
        write<GlVoucherRecord>('POST', `/api/admin/gl/vouchers/from-finance-draft/${encodeId(draftId)}`, payload),
      refreshSource: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlVoucherRecord>('POST', `/api/admin/gl/vouchers/${encodeId(id)}/refresh-source`, payload),
      submit: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlVoucherRecord>('POST', `/api/admin/gl/vouchers/${encodeId(id)}/submit`, payload),
      withdraw: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlVoucherRecord>('POST', `/api/admin/gl/vouchers/${encodeId(id)}/withdraw`, payload),
      cancel: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlVoucherRecord>('POST', `/api/admin/gl/vouchers/${encodeId(id)}/cancel`, payload),
      createReversal: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlVoucherRecord>('POST', `/api/admin/gl/vouchers/${encodeId(id)}/reversals`, payload),
    },
    ledgers: {
      general: (params: GlPageParams) => get<PageResult<GlLedgerRow>>('/api/admin/gl/ledgers/general', params),
      detail: (params: GlPageParams) => get<PageResult<GlLedgerRow>>('/api/admin/gl/ledgers/detail', params),
    },
    accountBalances: {
      list: (params: GlPageParams) => get<PageResult<GlLedgerRow>>('/api/admin/gl/account-balances', params),
    },
    trialBalance: {
      get: (params: Record<string, unknown>) => get<GlTrialBalanceRecord>('/api/admin/gl/trial-balance', params),
    },
  }
}

export type GlApi = ReturnType<typeof createGlApi>

export const glApi = createGlApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
