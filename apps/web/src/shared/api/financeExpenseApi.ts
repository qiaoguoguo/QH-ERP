import type { PageResult } from './accountPermissionApi'
import {
  createFinanceStage028Transport,
  type FinanceAmount,
  type FinanceMoneyPayload,
  type FinanceStage028ApiOptions,
  type OwnershipType,
  type ResourceId,
  type SettlementStatus,
  type VersionedActionPayload,
} from './financeStage028ApiCore'

export type ExpenseStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED'
export type ExpenseSourceType = 'PURCHASE_RECEIPT' | 'OUTSOURCING_RECEIPT' | 'NONE'

export interface ExpenseListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  categoryId?: ResourceId | null
  ownershipType?: OwnershipType | null
  sourceType?: ExpenseSourceType | null
  status?: ExpenseStatus | null
  settlementStatus?: SettlementStatus | null
  businessDateFrom?: string | null
  businessDateTo?: string | null
  costRestricted?: boolean | null
  page: number
  pageSize: number
}

export interface ExpenseCategoryListParams {
  keyword?: string | null
  status?: string | null
  page: number
  pageSize: number
}

export interface ExpenseSourceCandidateListParams {
  keyword?: string | null
  sourceType?: ExpenseSourceType | null
  page: number
  pageSize: number
}

export interface ExpensePayload extends VersionedActionPayload {
  supplierId: ResourceId
  ownershipType: OwnershipType
  projectId?: ResourceId | null
  categoryId: ResourceId
  businessDate: string
  lines: Array<{
    expenseCategory?: string
    categoryId: ResourceId
    description?: string
    sourceType?: ExpenseSourceType | string | null
    sourceId?: ResourceId | null
    sourceNo?: string | null
    taxExcludedAmount?: FinanceMoneyPayload
    pretaxAmount: FinanceMoneyPayload
    taxRate: FinanceMoneyPayload
    taxAmount: FinanceMoneyPayload
    taxIncludedAmount?: FinanceMoneyPayload
    totalAmount: FinanceMoneyPayload
  }>
  remark?: string
}

export interface ExpenseRecord {
  id: ResourceId
  expenseNo: string
  supplierName: string
  categoryName: string
  ownershipType: OwnershipType
  projectName?: string | null
  sourceType?: ExpenseSourceType | string | null
  sourceNo?: string | null
  businessDate: string
  status: ExpenseStatus
  settlementStatus: SettlementStatus
  pretaxAmount: FinanceAmount
  taxAmount: FinanceAmount
  totalAmount: FinanceAmount
  unsettledAmount: FinanceAmount
  version: number
  allowedActions: string[]
  lines?: Array<{ categoryName: string; pretaxAmount: FinanceAmount; taxRate: FinanceAmount; taxAmount: FinanceAmount; totalAmount: FinanceAmount }>
  sources?: Array<{ sourceType: string; sourceNo: string; summary?: string; restricted?: boolean; restrictedReason?: string | null }>
  payableLinks?: Array<{ payableNo: string; amount: FinanceAmount }>
  settlements?: Array<{ documentNo?: string; amount: FinanceAmount }>
  voucherDrafts?: Array<{ draftNo: string; status: string }>
  auditSummary?: unknown[]
}

export interface ExpenseCategoryRecord {
  id: ResourceId
  name: string
  status: string
}

export interface ExpenseSourceCandidateRecord {
  sourceId?: ResourceId
  sourceType: string
  sourceNo: string
  supplierId?: ResourceId
  supplierName?: string
  ownershipType?: OwnershipType
  projectId?: ResourceId | null
  projectName?: string | null
  businessDate?: string | null
  availableAmount?: FinanceAmount
  summary?: string
}

export interface FinanceExpenseApi {
  expenses: {
    list(params: ExpenseListParams): Promise<PageResult<ExpenseRecord>>
    get(id: ResourceId): Promise<ExpenseRecord>
    create(payload: ExpensePayload): Promise<ExpenseRecord>
    update(id: ResourceId, payload: ExpensePayload): Promise<ExpenseRecord>
    confirm(id: ResourceId, payload: VersionedActionPayload): Promise<ExpenseRecord>
    cancel(id: ResourceId, payload: VersionedActionPayload): Promise<ExpenseRecord>
  }
  expenseCategories: {
    list(params: ExpenseCategoryListParams): Promise<PageResult<ExpenseCategoryRecord>>
  }
  expenseSourceCandidates: {
    list(params: ExpenseSourceCandidateListParams): Promise<PageResult<ExpenseSourceCandidateRecord>>
  }
}

export function createFinanceExpenseApi(options: FinanceStage028ApiOptions = {}): FinanceExpenseApi {
  const api = createFinanceStage028Transport(options)
  const expensePath = (id?: ResourceId) => `/api/admin/finance/expenses${id === undefined ? '' : `/${api.encodeId(id)}`}`
  const expenseQueryKeys = ['keyword', 'supplierId', 'categoryId', 'ownershipType', 'sourceType', 'status', 'settlementStatus', 'businessDateFrom', 'businessDateTo', 'costRestricted', 'page', 'pageSize'] as const
  const categoryQueryKeys = ['keyword', 'status', 'page', 'pageSize'] as const
  const sourceQueryKeys = ['keyword', 'sourceType', 'page', 'pageSize'] as const

  return {
    expenses: {
      list: (params) => api.get<PageResult<ExpenseRecord>>(expensePath(), api.pickQuery(params, expenseQueryKeys)),
      get: (id) => api.get<ExpenseRecord>(expensePath(id)),
      create: (payload) => api.write<ExpenseRecord>('POST', expensePath(), payload),
      update: (id, payload) => api.write<ExpenseRecord>('PUT', expensePath(id), payload),
      confirm: (id, payload) => api.write<ExpenseRecord>('PUT', `${expensePath(id)}/confirm`, payload),
      cancel: (id, payload) => api.write<ExpenseRecord>('PUT', `${expensePath(id)}/cancel`, payload),
    },
    expenseCategories: {
      list: (params) => api.get<PageResult<ExpenseCategoryRecord>>(`${expensePath()}/categories`, api.pickQuery(params, categoryQueryKeys)),
    },
    expenseSourceCandidates: {
      list: (params) => api.get<PageResult<ExpenseSourceCandidateRecord>>(`${expensePath()}/source-candidates`, api.pickQuery(params, sourceQueryKeys)),
    },
  }
}

export const financeExpenseApi = createFinanceExpenseApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
