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

export interface GlLedgerRecord {
  id?: ResourceId
  ledgerCode?: string
  ledgerName?: string
  baseCurrency: 'CNY' | string
  initialized: boolean
  startPeriodCode?: string | null
}

export interface GlAccountingPeriodRecord {
  id?: ResourceId
  periodCode: string
  startDate: string
  endDate: string
  status: 'OPEN' | string
  voucherCount?: number | null
  lastPostedAt?: string | null
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
  objectId?: ResourceId | null
  objectCode?: string | null
  objectName?: string | null
  restricted?: boolean | null
  restrictedReason?: string | null
}

export interface GlPostingRuleRecord extends GlActionState {
  id: ResourceId
  sourceType: string
  sourceVariant: string
  versionNo: number
  status: 'DRAFT' | 'ACTIVE' | 'SUPERSEDED' | 'DISABLED' | string
  validationStatus?: string | null
  lineCount?: number | null
  lines?: unknown[]
  validationSummary?: unknown
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
    return envelope.data
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
      create: (payload: GlVersionedActionPayload) => write<GlAccountingPeriodRecord>('POST', '/api/admin/gl/accounting-periods', payload),
    },
    accounts: {
      list: (params: GlPageParams) => get<PageResult<GlAccountRecord>>('/api/admin/gl/accounts', params),
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
      candidates: (code: string, params: Record<string, unknown>) => get<PageResult<GlAuxCandidateRecord>>(`/api/admin/gl/aux-dimensions/${encodeURIComponent(code)}/candidates`, params),
    },
    postingRules: {
      list: (params: GlPageParams) => get<PageResult<GlPostingRuleRecord>>('/api/admin/gl/posting-rules', params),
      get: (id: ResourceId) => get<GlPostingRuleRecord>(`/api/admin/gl/posting-rules/${encodeId(id)}`),
      create: (payload: object) => write<GlPostingRuleRecord>('POST', '/api/admin/gl/posting-rules', payload),
      newVersion: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlPostingRuleRecord>('POST', `/api/admin/gl/posting-rules/${encodeId(id)}/new-version`, payload),
      update: (id: ResourceId, payload: object) => write<GlPostingRuleRecord>('PUT', `/api/admin/gl/posting-rules/${encodeId(id)}`, payload),
      validate: (id: ResourceId, payload: GlVersionedActionPayload) => write<GlPostingRuleRecord>('POST', `/api/admin/gl/posting-rules/${encodeId(id)}/validate`, payload),
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
