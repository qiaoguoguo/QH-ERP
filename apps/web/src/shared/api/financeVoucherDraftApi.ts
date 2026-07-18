import type { PageResult } from './accountPermissionApi'
import {
  createFinanceStage028Transport,
  type FinanceAmount,
  type FinanceStage028ApiOptions,
  type OwnershipType,
  type ResourceId,
  type VersionedActionPayload,
} from './financeStage028ApiCore'

export type VoucherDraftStatus = 'DRAFT' | 'READY' | 'CANCELLED'
export type VoucherSourceType = 'SALES_INVOICE' | 'PURCHASE_INVOICE' | 'EXPENSE' | 'RECEIPT' | 'PAYMENT' | 'SETTLEMENT_ALLOCATION'

export interface VoucherDraftListParams {
  keyword?: string | null
  sourceType?: VoucherSourceType | null
  sourceNo?: string | null
  partnerId?: ResourceId | null
  projectId?: ResourceId | null
  status?: VoucherDraftStatus | null
  balanced?: boolean | null
  businessDateFrom?: string | null
  businessDateTo?: string | null
  generatedAtFrom?: string | null
  generatedAtTo?: string | null
  page: number
  pageSize: number
}

export interface VoucherDraftGeneratePayload extends VersionedActionPayload {
  sourceType: VoucherSourceType
  sourceId: ResourceId
}

export interface VoucherDraftRecord {
  id: ResourceId
  draftNo: string
  sourceType: VoucherSourceType | string
  sourceNo: string
  businessDate: string
  partyName?: string | null
  partnerName?: string | null
  ownershipType?: OwnershipType | null
  projectName?: string | null
  status: VoucherDraftStatus
  debitTotal: FinanceAmount
  creditTotal: FinanceAmount
  balanced: boolean
  generationVersion: number
  updatedAt?: string
  version: number
  allowedActions: string[]
  lines?: Array<{
    direction: 'DEBIT' | 'CREDIT'
    businessCategory: string
    summary: string
    amount?: FinanceAmount
    pretaxAmount: FinanceAmount
    taxAmount: FinanceAmount
    totalAmount: FinanceAmount
    sourceType?: string
    sourceId?: ResourceId
    partnerName?: string | null
    projectName?: string | null
  }>
  sourceSummary?: string | {
    sourceType: string
    sourceNo: string
    summary?: string | null
    restricted?: boolean
    restrictedReason?: string | null
  }
  auditSummary?: unknown[]
}

export interface FinanceVoucherDraftApi {
  voucherDrafts: {
    list(params: VoucherDraftListParams): Promise<PageResult<VoucherDraftRecord>>
    get(id: ResourceId): Promise<VoucherDraftRecord>
    generate(payload: VoucherDraftGeneratePayload): Promise<VoucherDraftRecord>
    markReady(id: ResourceId, payload: VersionedActionPayload): Promise<VoucherDraftRecord>
    cancel(id: ResourceId, payload: VersionedActionPayload): Promise<VoucherDraftRecord>
  }
}

export function createFinanceVoucherDraftApi(options: FinanceStage028ApiOptions = {}): FinanceVoucherDraftApi {
  const api = createFinanceStage028Transport(options)
  const voucherDraftPath = (id?: ResourceId) => `/api/admin/finance/voucher-drafts${id === undefined ? '' : `/${api.encodeId(id)}`}`
  const voucherQueryKeys = ['keyword', 'sourceType', 'sourceNo', 'partnerId', 'projectId', 'status', 'balanced', 'businessDateFrom', 'businessDateTo', 'generatedAtFrom', 'generatedAtTo', 'page', 'pageSize'] as const
  const normalizeRecord = (raw: VoucherDraftRecord & {
    debitAmount?: FinanceAmount
    creditAmount?: FinanceAmount
    summary?: string | null
    sourceSummary?: string | VoucherDraftRecord['sourceSummary']
  }): VoucherDraftRecord => {
    const sourceSummary = typeof raw.sourceSummary === 'string'
      ? { sourceType: raw.sourceType, sourceNo: raw.sourceNo, summary: raw.sourceSummary }
      : raw.sourceSummary
        ? { ...raw.sourceSummary, summary: raw.sourceSummary.summary ?? raw.summary ?? null }
        : undefined
    return {
      ...raw,
      debitTotal: raw.debitTotal ?? raw.debitAmount ?? '0.00',
      creditTotal: raw.creditTotal ?? raw.creditAmount ?? '0.00',
      sourceSummary,
      lines: raw.lines?.map((line) => {
        const amount = line.amount ?? line.totalAmount ?? line.pretaxAmount ?? '0.00'
        return {
          ...line,
          amount,
          pretaxAmount: line.pretaxAmount ?? amount,
          taxAmount: line.taxAmount ?? '0.00',
          totalAmount: line.totalAmount ?? amount,
        }
      }),
    }
  }
  const normalizePage = (page: PageResult<VoucherDraftRecord>) => {
    const items = page.items.map((item) => normalizeRecord(item))
    return {
      ...page,
      items,
      records: page.records?.map((item) => normalizeRecord(item)),
      content: page.content?.map((item) => normalizeRecord(item)),
    }
  }

  return {
    voucherDrafts: {
      list: async (params) => normalizePage(await api.get<PageResult<VoucherDraftRecord>>(voucherDraftPath(), api.pickQuery(params, voucherQueryKeys))),
      get: async (id) => normalizeRecord(await api.get<VoucherDraftRecord>(voucherDraftPath(id))),
      generate: async (payload) => normalizeRecord(await api.write<VoucherDraftRecord>('POST', `${voucherDraftPath()}/generate`, payload)),
      markReady: async (id, payload) => normalizeRecord(await api.write<VoucherDraftRecord>('PUT', `${voucherDraftPath(id)}/ready`, payload)),
      cancel: async (id, payload) => normalizeRecord(await api.write<VoucherDraftRecord>('PUT', `${voucherDraftPath(id)}/cancel`, payload)),
    },
  }
}

export const financeVoucherDraftApi = createFinanceVoucherDraftApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
