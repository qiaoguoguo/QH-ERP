import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type FinancialCloseAmount = string
export type FinancialCloseResourceId = string | number
export type FinancialClosePageParams = Record<string, unknown> & { page: number; pageSize: number }
export type FinancialCloseStatus = 'OPEN' | 'CLOSED' | string
export type FinancialCloseCheckStatus = 'CHECKING' | 'BLOCKED' | 'READY' | 'STALE' | 'CONSUMED' | 'FAILED' | string

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>

export interface FinancialCloseApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export interface FinancialCloseActionState {
  version: number
  allowedActions: string[]
  actionDisabledReasons: Record<string, string>
}

export interface FinancialCloseVisibilityState {
  amountVisible?: boolean | null
  sourceVisible?: boolean | null
  bankSensitiveVisible?: boolean | null
  restrictedReason?: string | null
}

export interface FinancialCloseActionPayload {
  version: number
  idempotencyKey: string
  reason?: string
}

export interface FinancialClosePeriodRecord extends FinancialCloseActionState, FinancialCloseVisibilityState {
  id: FinancialCloseResourceId
  periodCode: string
  status: FinancialCloseStatus
  closeStatus?: string | null
  latestCheckId?: FinancialCloseResourceId | null
  latestCheckRunId?: FinancialCloseResourceId | null
  latestCheckStatus?: string | null
  closeRunId?: FinancialCloseResourceId | null
  voucherCount?: number | null
  bankDifference?: FinancialCloseAmount | null
  taxPayableAmount?: FinancialCloseAmount | null
}

export interface FinancialCloseCheckItem {
  code: string
  severity?: string | null
  status?: string | null
  conclusion?: string | null
  actualValue?: string | null
  expectedValue?: string | null
  sourceType?: string | null
  sourceId?: FinancialCloseResourceId | null
  sourceNo?: string | null
  sourceVisible?: boolean | null
}

export interface FinancialCloseCheckRunRecord extends FinancialCloseActionState, FinancialCloseVisibilityState {
  id: FinancialCloseResourceId
  periodCode?: string | null
  status: FinancialCloseCheckStatus
  sourceFingerprint?: string | null
  items?: FinancialCloseCheckItem[]
  checkItems?: FinancialCloseCheckItem[]
  closeVersion?: number | null
  closeSnapshot?: Record<string, unknown> | null
  reopenRequests?: unknown[]
}

export interface ProfitLossTransferRecord extends FinancialCloseActionState, FinancialCloseVisibilityState {
  id: FinancialCloseResourceId
  periodCode?: string | null
  status?: string | null
  debitTotal?: FinancialCloseAmount | null
  creditTotal?: FinancialCloseAmount | null
  voucherId?: FinancialCloseResourceId | null
  voucherNo?: string | null
  sourceFingerprint?: string | null
  lines?: Array<Record<string, unknown>>
}

export interface BankAccountRecord extends FinancialCloseActionState, FinancialCloseVisibilityState {
  id: FinancialCloseResourceId
  accountName?: string | null
  accountType?: string | null
  bankName?: string | null
  accountNoMasked?: string | null
  accountNoLast4?: string | null
  accountNoFingerprint?: string | null
  glAccountId?: FinancialCloseResourceId | null
  glAccountCode?: string | null
  enabled?: boolean | null
}

export interface BankStatementRecord extends FinancialCloseActionState, FinancialCloseVisibilityState {
  id: FinancialCloseResourceId
  statementNo?: string | null
  bankAccountName?: string | null
  transactionDate?: string | null
  direction?: string | null
  amount?: FinancialCloseAmount | null
  status?: string | null
  duplicate?: boolean | null
}

export interface BankReconciliationRecord extends FinancialCloseActionState, FinancialCloseVisibilityState {
  id: FinancialCloseResourceId
  reconciliationNo?: string | null
  periodCode?: string | null
  bankAccountName?: string | null
  status?: string | null
  bankEndingBalance?: FinancialCloseAmount | null
  glEndingBalance?: FinancialCloseAmount | null
  adjustedBankBalance?: FinancialCloseAmount | null
  adjustedBookBalance?: FinancialCloseAmount | null
  difference?: FinancialCloseAmount | null
  matches?: Array<Record<string, unknown>>
  exceptions?: Array<Record<string, unknown>>
}

export interface TaxProfileRecord extends FinancialCloseActionState, FinancialCloseVisibilityState {
  id: FinancialCloseResourceId
  companyName?: string | null
  taxpayerType?: string | null
  creditCode?: string | null
  taxAuthority?: string | null
  vatPeriodicity?: string | null
  incomeTaxRate?: string | null
  urbanMaintenanceRate?: string | null
  effectiveFrom?: string | null
  unifiedSocialCreditCodeMasked?: string | null
  cityMaintenanceTaxRate?: string | null
}

export interface TaxSummaryRecord extends FinancialCloseActionState, FinancialCloseVisibilityState {
  id: FinancialCloseResourceId
  periodCode?: string | null
  taxType?: string | null
  status?: string | null
  disclaimer?: string | null
  outputTaxAmount?: FinancialCloseAmount | null
  outputVat?: FinancialCloseAmount | null
  inputTaxAmount?: FinancialCloseAmount | null
  inputVat?: FinancialCloseAmount | null
  payableTaxAmount?: FinancialCloseAmount | null
  vatPayable?: FinancialCloseAmount | null
  retainedTaxAmount?: FinancialCloseAmount | null
  endingCreditVat?: FinancialCloseAmount | null
  estimatedIncomeTaxAmount?: FinancialCloseAmount | null
  incomeTaxEstimated?: FinancialCloseAmount | null
  adjustmentAmount?: FinancialCloseAmount | null
  stale?: boolean | null
  current?: boolean | null
  voucherId?: FinancialCloseResourceId | null
  voucherNo?: string | null
}

export interface TaxPaymentRecord extends FinancialCloseActionState, FinancialCloseVisibilityState {
  id: FinancialCloseResourceId
  periodCode?: string | null
  taxType?: string | null
  paymentDate?: string | null
  amount?: FinancialCloseAmount | null
  voucherId?: FinancialCloseResourceId | null
  voucherNo?: string | null
  paymentSourceType?: string | null
  bankAccountMasked?: string | null
  bankAccountDisplay?: string | null
}

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

function normalizeActionState<T extends RawRecord>(record: T): T & FinancialCloseActionState {
  return {
    ...record,
    version: Number(record.version ?? 0),
    allowedActions: normalizeAllowedActions(record.allowedActions),
    actionDisabledReasons: isRecord(record.actionDisabledReasons) ? (record.actionDisabledReasons as Record<string, string>) : {},
  }
}

function normalizeRecord(record: RawRecord) {
  const normalized = normalizeActionState(record)
  return {
    ...normalized,
    latestCheckRunId: normalized.latestCheckRunId ?? normalized.latestCheckId,
    latestCheckId: normalized.latestCheckId ?? normalized.latestCheckRunId,
    checkItems: normalized.checkItems ?? normalized.items,
    accountNoMasked: normalized.accountNoMasked ?? normalized.accountMasked ?? normalized.maskedAccountNo,
    accountNoLast4: normalized.accountNoLast4 ?? normalized.accountLast4,
    unifiedSocialCreditCodeMasked: normalized.unifiedSocialCreditCodeMasked ?? normalized.creditCodeMasked ?? normalized.creditCode,
    cityMaintenanceTaxRate: normalized.cityMaintenanceTaxRate ?? normalized.urbanMaintenanceRate,
    outputTaxAmount: normalized.outputTaxAmount ?? normalized.outputVat,
    inputTaxAmount: normalized.inputTaxAmount ?? normalized.inputVat,
    payableTaxAmount: normalized.payableTaxAmount ?? normalized.vatPayable,
    retainedTaxAmount: normalized.retainedTaxAmount ?? normalized.endingCreditVat,
    estimatedIncomeTaxAmount: normalized.estimatedIncomeTaxAmount ?? normalized.incomeTaxEstimated,
    bankAccountMasked: normalized.bankAccountMasked ?? normalized.bankAccountDisplay ?? normalized.accountMasked,
    rate: normalized.rate ?? normalized.rateValue,
    enabled: normalized.enabled ?? (normalized.status === 'ENABLED' ? true : normalized.status === 'DISABLED' ? false : normalized.enabled),
  }
}

function normalizePageItems<T>(page: RawRecord, normalize: (item: RawRecord) => T): unknown {
  const normalized = { ...page }
  ;(['items', 'records', 'content'] as const).forEach((key) => {
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
  const hasPageMetadata = 'total' in data || 'totalElements' in data || 'page' in data || 'pageSize' in data
  if ((Array.isArray(data.items) && hasPageMetadata) || Array.isArray(data.records) || Array.isArray(data.content)) {
    return normalizePageItems(data, normalize)
  }
  return normalize(data)
}

function normalizeFinancialCloseResponse(data: unknown): unknown {
  return normalizeRecordOrPage(data, normalizeRecord)
}

export function createFinancialCloseApi(options: FinancialCloseApiOptions = {}) {
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
    return normalizeFinancialCloseResponse(envelope.data) as T
  }

  const getCsrf = () => request<CsrfToken>('/api/auth/csrf', { method: 'GET' })
  const get = <T>(path: string, query?: Record<string, unknown>) => request<T>(path, { method: 'GET' }, query)
  const write = async <T>(method: 'POST' | 'PUT' | 'DELETE', path: string, body?: object) => {
    const csrf = await getCsrf()
    const headers: Record<string, string> = { [csrf.headerName]: csrf.token }
    const init: RequestInit = { method, headers }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }
    return request<T>(path, init)
  }
  const encodeId = (id: FinancialCloseResourceId) => encodeURIComponent(String(id))

  return {
    periods: {
      list: (params: FinancialClosePageParams) => get<PageResult<FinancialClosePeriodRecord>>('/api/admin/financial-closes/periods', params),
      get: (id: FinancialCloseResourceId) => get<FinancialClosePeriodRecord>(`/api/admin/financial-closes/periods/${encodeId(id)}`),
      startCheck: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<FinancialCloseCheckRunRecord>('POST', `/api/admin/financial-closes/periods/${encodeId(id)}/checks`, payload),
    },
    checkRuns: {
      get: (id: FinancialCloseResourceId) => get<FinancialCloseCheckRunRecord>(`/api/admin/financial-closes/check-runs/${encodeId(id)}`),
      close: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<FinancialCloseCheckRunRecord>('POST', `/api/admin/financial-closes/check-runs/${encodeId(id)}/close`, payload),
    },
    closeRuns: {
      requestReopen: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<Record<string, unknown>>('POST', `/api/admin/financial-closes/close-runs/${encodeId(id)}/reopen-requests`, payload),
    },
    reopenRequests: {
      get: (id: FinancialCloseResourceId) => get<Record<string, unknown>>(`/api/admin/financial-closes/reopen-requests/${encodeId(id)}`),
    },
    profitLoss: {
      list: (periodId: FinancialCloseResourceId, params: FinancialClosePageParams) =>
        get<PageResult<ProfitLossTransferRecord>>(`/api/admin/financial-closes/periods/${encodeId(periodId)}/profit-loss-transfers`, params),
      preview: (periodId: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<ProfitLossTransferRecord>('POST', `/api/admin/financial-closes/periods/${encodeId(periodId)}/profit-loss-transfers/preview`, payload),
      generate: (periodId: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<ProfitLossTransferRecord>('POST', `/api/admin/financial-closes/periods/${encodeId(periodId)}/profit-loss-transfers`, payload),
    },
    bankAccounts: {
      list: (params: FinancialClosePageParams) => get<PageResult<BankAccountRecord>>('/api/admin/bank-accounts', params),
      create: (payload: object) => write<BankAccountRecord>('POST', '/api/admin/bank-accounts', payload),
      update: (id: FinancialCloseResourceId, payload: object) => write<BankAccountRecord>('PUT', `/api/admin/bank-accounts/${encodeId(id)}`, payload),
      disable: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<BankAccountRecord>('POST', `/api/admin/bank-accounts/${encodeId(id)}/disable`, payload),
    },
    bankStatements: {
      list: (params: FinancialClosePageParams) => get<PageResult<BankStatementRecord>>('/api/admin/bank-statements', params),
      get: (id: FinancialCloseResourceId) => get<BankStatementRecord>(`/api/admin/bank-statements/${encodeId(id)}`),
      create: (payload: object) => write<BankStatementRecord>('POST', '/api/admin/bank-statements', payload),
      importPreview: (payload: object) => write<Record<string, unknown>>('POST', '/api/admin/bank-statements/import-preview', payload),
      importConfirm: (payload: object) => write<Record<string, unknown>>('POST', '/api/admin/bank-statements/import-confirm', payload),
      ignoreLine: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<BankStatementRecord>('POST', `/api/admin/bank-statement-lines/${encodeId(id)}/ignore`, payload),
    },
    bankReconciliations: {
      list: (params: FinancialClosePageParams) => get<PageResult<BankReconciliationRecord>>('/api/admin/bank-reconciliations', params),
      get: (id: FinancialCloseResourceId) => get<BankReconciliationRecord>(`/api/admin/bank-reconciliations/${encodeId(id)}`),
      candidates: (id: FinancialCloseResourceId, params: FinancialClosePageParams) =>
        get<Record<string, unknown>>(`/api/admin/bank-reconciliations/${encodeId(id)}/candidates`, params),
      create: (payload: object) => write<BankReconciliationRecord>('POST', '/api/admin/bank-reconciliations', payload),
      createMatch: (id: FinancialCloseResourceId, payload: object) =>
        write<Record<string, unknown>>('POST', `/api/admin/bank-reconciliations/${encodeId(id)}/matches`, payload),
      deleteMatch: (id: FinancialCloseResourceId, matchGroupNo: string, payload: FinancialCloseActionPayload) =>
        write<Record<string, unknown>>('DELETE', `/api/admin/bank-reconciliations/${encodeId(id)}/matches?matchGroupNo=${encodeURIComponent(matchGroupNo)}`, payload),
      createException: (id: FinancialCloseResourceId, payload: object) =>
        write<Record<string, unknown>>('POST', `/api/admin/bank-reconciliations/${encodeId(id)}/exceptions`, payload),
      calculate: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<BankReconciliationRecord>('POST', `/api/admin/bank-reconciliations/${encodeId(id)}/calculate`, payload),
      confirm: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<BankReconciliationRecord>('POST', `/api/admin/bank-reconciliations/${encodeId(id)}/confirm`, payload),
      reopen: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<BankReconciliationRecord>('POST', `/api/admin/bank-reconciliations/${encodeId(id)}/reopen`, payload),
    },
    taxProfiles: {
      current: () => get<TaxProfileRecord>('/api/admin/tax-profiles/current'),
      update: (payload: object) => write<TaxProfileRecord>('PUT', '/api/admin/tax-profiles/current', payload),
    },
    taxRateRules: {
      list: (params: FinancialClosePageParams) => get<PageResult<Record<string, unknown>>>('/api/admin/tax-rate-rules', params),
      create: (payload: object) => write<Record<string, unknown>>('POST', '/api/admin/tax-rate-rules', payload),
    },
    taxInvoiceTypes: {
      list: (params: FinancialClosePageParams) => get<PageResult<Record<string, unknown>>>('/api/admin/tax-invoice-types', params),
      create: (payload: object) => write<Record<string, unknown>>('POST', '/api/admin/tax-invoice-types', payload),
    },
    taxSummaries: {
      list: (params: FinancialClosePageParams) => get<PageResult<TaxSummaryRecord>>('/api/admin/tax-summaries', params),
      get: (id: FinancialCloseResourceId) => get<TaxSummaryRecord>(`/api/admin/tax-summaries/${encodeId(id)}`),
      create: (payload: object) => write<TaxSummaryRecord>('POST', '/api/admin/tax-summaries', payload),
      calculate: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<TaxSummaryRecord>('POST', `/api/admin/tax-summaries/${encodeId(id)}/calculate`, payload),
      addAdjustment: (id: FinancialCloseResourceId, payload: object) =>
        write<TaxSummaryRecord>('POST', `/api/admin/tax-summaries/${encodeId(id)}/adjustments`, payload),
      confirm: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<TaxSummaryRecord>('POST', `/api/admin/tax-summaries/${encodeId(id)}/confirm`, payload),
      createVoucherDraft: (id: FinancialCloseResourceId, payload: FinancialCloseActionPayload) =>
        write<TaxSummaryRecord>('POST', `/api/admin/tax-summaries/${encodeId(id)}/voucher-drafts`, payload),
    },
    taxPayments: {
      list: (params: FinancialClosePageParams) => get<PageResult<TaxPaymentRecord>>('/api/admin/tax-payments', params),
      create: (payload: object) => write<TaxPaymentRecord>('POST', '/api/admin/tax-payments', payload),
      correct: (id: FinancialCloseResourceId, payload: object) =>
        write<TaxPaymentRecord>('POST', `/api/admin/tax-payments/${encodeId(id)}/corrections`, payload),
    },
  }
}

export type FinancialCloseApi = ReturnType<typeof createFinancialCloseApi>

export const financialCloseApi = createFinancialCloseApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
