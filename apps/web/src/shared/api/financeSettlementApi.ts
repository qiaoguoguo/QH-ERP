import type { PageResult } from './accountPermissionApi'
import {
  createFinanceStage028Transport,
  type FinanceAmount,
  type FinanceMoneyPayload,
  type FinanceStage028ApiOptions,
  type OwnershipType,
  type ResourceId,
  type VersionedActionPayload,
} from './financeStage028ApiCore'

export type SettlementDirection = 'CUSTOMER' | 'SUPPLIER'
export type AdvanceFundStatus = 'DRAFT' | 'AVAILABLE' | 'PARTIALLY_APPLIED' | 'APPLIED' | 'CANCELLED'
export type FundType = 'ADVANCE_RECEIPT' | 'PREPAYMENT' | 'RECEIPT' | 'PAYMENT'
export type TargetType = 'SALES_INVOICE' | 'PURCHASE_INVOICE' | 'EXPENSE' | 'RECEIVABLE' | 'PAYABLE'

export interface AdvanceFundListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  supplierId?: ResourceId | null
  ownershipType?: OwnershipType | null
  projectId?: ResourceId | null
  status?: AdvanceFundStatus | null
  settlementStatus?: string | null
  businessDateFrom?: string | null
  businessDateTo?: string | null
  availableOnly?: boolean | null
  page: number
  pageSize: number
}

export interface SettlementPoolParams {
  direction: SettlementDirection
  fundType?: FundType | null
  fundId?: ResourceId | null
  targetType?: TargetType | null
  partnerId?: ResourceId | null
  ownershipType?: OwnershipType | null
  projectId?: ResourceId | null
  page: number
  pageSize: number
}

export interface AdvanceFundPayload extends VersionedActionPayload {
  partnerId: ResourceId
  customerId?: ResourceId | null
  supplierId?: ResourceId | null
  ownershipType: OwnershipType
  projectId?: ResourceId | null
  businessDate: string
  amount: FinanceMoneyPayload
  method: string
  allocations: Array<{ targetType: TargetType; targetId: ResourceId; amount: FinanceMoneyPayload }>
  remark?: string
}

export interface SettlementAllocationPayload {
  settlementSide?: 'RECEIVABLE' | 'PAYABLE'
  cashSourceType?: FundType | 'RECEIPT' | 'PAYMENT'
  cashSourceId?: ResourceId
  businessDate?: string
  direction: SettlementDirection
  partnerId: ResourceId
  ownershipType: OwnershipType
  projectId?: ResourceId | null
  funds: Array<{ fundType: FundType; fundId: ResourceId; version: number; amount: FinanceMoneyPayload }>
  targets: Array<{ targetType: TargetType; targetId: ResourceId; version: number; amount: FinanceMoneyPayload }>
  idempotencyKey: string
}

export interface SettlementAllocationRecord {
  id: ResourceId
  allocationNo?: string
  settlementSide?: string
  direction?: SettlementDirection
  cashSourceType?: string
  cashSourceId?: ResourceId
  fundNo?: string | null
  partnerName?: string | null
  ownershipType?: OwnershipType | null
  projectName?: string | null
  businessDate?: string | null
  totalAmount?: FinanceAmount
  amount?: FinanceAmount
  status: string
  version: number
  allowedActions?: string[]
  restrictedReasons?: string[]
  lines: Array<{
    targetType: string
    targetId?: ResourceId
    targetNo?: string
    amount: FinanceAmount
    sourceSummary?: string | null
    restrictedReason?: string | null
  }>
  auditSummary?: unknown[]
}

export interface AdvanceFundRecord {
  id: ResourceId
  advanceNo: string
  fundNo: string
  partnerName: string
  ownershipType: OwnershipType
  projectName?: string | null
  businessDate: string
  amount: FinanceAmount
  allocatedAmount: FinanceAmount
  availableAmount: FinanceAmount
  status: AdvanceFundStatus
  settlementStatus?: string
  lastAllocatedAt?: string | null
  version: number
  allowedActions: string[]
  restrictedReason?: string | null
  allocations?: Array<{ targetType: string; targetNo: string; amount: FinanceAmount }>
  voucherDrafts?: Array<{ draftNo: string; status: string }>
  auditSummary?: unknown[]
}

export interface SettlementTargetRecord {
  targetType: TargetType
  targetId: ResourceId
  targetNo: string
  businessDate?: string | null
  originalAmount: FinanceAmount
  settledAmount: FinanceAmount
  adjustedAmount: FinanceAmount
  allocatedAmount: FinanceAmount
  unsettledAmount: FinanceAmount
  status: string
  sourceSummary?: string
  restrictedReason?: string | null
  version: number
}

export interface FinanceSettlementApi {
  advanceReceipts: {
    list(params: AdvanceFundListParams): Promise<PageResult<AdvanceFundRecord>>
    get(id: ResourceId): Promise<AdvanceFundRecord>
    create(payload: AdvanceFundPayload): Promise<AdvanceFundRecord>
    update(id: ResourceId, payload: AdvanceFundPayload): Promise<AdvanceFundRecord>
    post(id: ResourceId, payload: VersionedActionPayload): Promise<AdvanceFundRecord>
    cancel(id: ResourceId, payload: VersionedActionPayload): Promise<AdvanceFundRecord>
  }
  prepayments: {
    list(params: AdvanceFundListParams): Promise<PageResult<AdvanceFundRecord>>
    get(id: ResourceId): Promise<AdvanceFundRecord>
    create(payload: AdvanceFundPayload): Promise<AdvanceFundRecord>
    update(id: ResourceId, payload: AdvanceFundPayload): Promise<AdvanceFundRecord>
    post(id: ResourceId, payload: VersionedActionPayload): Promise<AdvanceFundRecord>
    cancel(id: ResourceId, payload: VersionedActionPayload): Promise<AdvanceFundRecord>
  }
  settlementWorkbench: {
    funds(params: SettlementPoolParams): Promise<PageResult<AdvanceFundRecord>>
    targets(params: SettlementPoolParams): Promise<PageResult<SettlementTargetRecord>>
    get(id: ResourceId): Promise<SettlementAllocationRecord>
    create(payload: SettlementAllocationPayload): Promise<{ id: ResourceId; allocationNo?: string }>
    post(id: ResourceId, payload: VersionedActionPayload): Promise<{ id: ResourceId; allocationNo?: string }>
    cancel(id: ResourceId, payload: VersionedActionPayload): Promise<{ id: ResourceId; allocationNo?: string }>
  }
}

export function createFinanceSettlementApi(options: FinanceStage028ApiOptions = {}): FinanceSettlementApi {
  const api = createFinanceStage028Transport(options)
  const advanceReceiptPath = (id?: ResourceId) => `/api/admin/finance/advance-receipts${id === undefined ? '' : `/${api.encodeId(id)}`}`
  const prepaymentPath = (id?: ResourceId) => `/api/admin/finance/prepayments${id === undefined ? '' : `/${api.encodeId(id)}`}`
  const allocationPath = (id?: ResourceId) => `/api/admin/finance/settlement-workbench/allocations${id === undefined ? '' : `/${api.encodeId(id)}`}`
  const advanceQueryKeys = ['keyword', 'customerId', 'supplierId', 'ownershipType', 'projectId', 'status', 'settlementStatus', 'businessDateFrom', 'businessDateTo', 'availableOnly', 'page', 'pageSize'] as const
  const poolQueryKeys = ['direction', 'fundType', 'fundId', 'targetType', 'partnerId', 'ownershipType', 'projectId', 'page', 'pageSize'] as const

  return {
    advanceReceipts: {
      list: (params) => api.get<PageResult<AdvanceFundRecord>>(advanceReceiptPath(), api.pickQuery(params, advanceQueryKeys)),
      get: (id) => api.get<AdvanceFundRecord>(advanceReceiptPath(id)),
      create: (payload) => api.write<AdvanceFundRecord>('POST', advanceReceiptPath(), payload),
      update: (id, payload) => api.write<AdvanceFundRecord>('PUT', advanceReceiptPath(id), payload),
      post: (id, payload) => api.write<AdvanceFundRecord>('PUT', `${advanceReceiptPath(id)}/post`, payload),
      cancel: (id, payload) => api.write<AdvanceFundRecord>('PUT', `${advanceReceiptPath(id)}/cancel`, payload),
    },
    prepayments: {
      list: (params) => api.get<PageResult<AdvanceFundRecord>>(prepaymentPath(), api.pickQuery(params, advanceQueryKeys)),
      get: (id) => api.get<AdvanceFundRecord>(prepaymentPath(id)),
      create: (payload) => api.write<AdvanceFundRecord>('POST', prepaymentPath(), payload),
      update: (id, payload) => api.write<AdvanceFundRecord>('PUT', prepaymentPath(id), payload),
      post: (id, payload) => api.write<AdvanceFundRecord>('PUT', `${prepaymentPath(id)}/post`, payload),
      cancel: (id, payload) => api.write<AdvanceFundRecord>('PUT', `${prepaymentPath(id)}/cancel`, payload),
    },
    settlementWorkbench: {
      funds: (params) => api.get<PageResult<AdvanceFundRecord>>('/api/admin/finance/settlement-workbench/funds', api.pickQuery(params, poolQueryKeys)),
      targets: (params) => api.get<PageResult<SettlementTargetRecord>>('/api/admin/finance/settlement-workbench/targets', api.pickQuery(params, poolQueryKeys)),
      get: (id) => api.get<SettlementAllocationRecord>(allocationPath(id)),
      create: (payload) => api.write<{ id: ResourceId; allocationNo?: string }>('POST', allocationPath(), payload),
      post: (id, payload) => api.write<{ id: ResourceId; allocationNo?: string }>('PUT', `${allocationPath(id)}/post`, payload),
      cancel: (id, payload) => api.write<{ id: ResourceId; allocationNo?: string }>('PUT', `${allocationPath(id)}/cancel`, payload),
    },
  }
}

export const financeSettlementApi = createFinanceSettlementApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
