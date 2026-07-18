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

export type InvoiceStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED'
export type InvoiceType = 'SPECIAL_VAT' | 'NORMAL_VAT' | 'NONE'
export type PurchaseInvoiceSourceType = 'PURCHASE_RECEIPT' | 'OUTSOURCING_RECEIPT' | 'OUTSOURCING_ORDER'
export type MatchStatus = 'UNMATCHED' | 'MATCHED' | 'EXCEPTION'

export interface SalesInvoiceListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  projectId?: ResourceId | null
  status?: InvoiceStatus | null
  settlementStatus?: SettlementStatus | null
  invoiceType?: InvoiceType | null
  invoiceDateFrom?: string | null
  invoiceDateTo?: string | null
  externalInvoiceNo?: string | null
  sourceShipmentNo?: string | null
  page: number
  pageSize: number
}

export interface SalesInvoiceCandidateListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  projectId?: ResourceId | null
  contractNo?: string | null
  orderNo?: string | null
  shipmentDateFrom?: string | null
  shipmentDateTo?: string | null
  page: number
  pageSize: number
}

export interface PurchaseInvoiceListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  sourceType?: PurchaseInvoiceSourceType | null
  status?: InvoiceStatus | null
  matchStatus?: MatchStatus | null
  settlementStatus?: SettlementStatus | null
  invoiceDateFrom?: string | null
  invoiceDateTo?: string | null
  page: number
  pageSize: number
}

export interface PurchaseInvoiceCandidateListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  ownershipType?: OwnershipType | null
  sourceType?: PurchaseInvoiceSourceType | null
  businessDateFrom?: string | null
  businessDateTo?: string | null
  page: number
  pageSize: number
}

export interface SalesInvoicePayload extends VersionedActionPayload {
  invoiceDate: string
  invoiceType: InvoiceType
  externalInvoiceNo?: string
  customerId: ResourceId
  ownershipType: OwnershipType
  projectId?: ResourceId | null
  sourceLines: Array<{
    sourceLineId: ResourceId
    invoiceQuantity: FinanceMoneyPayload
  }>
  remark?: string
}

export interface PurchaseInvoicePayload extends VersionedActionPayload {
  invoiceDate: string
  invoiceType: InvoiceType
  externalInvoiceNo?: string
  supplierId: ResourceId
  sourceType: PurchaseInvoiceSourceType
  ownershipType: OwnershipType
  projectId?: ResourceId | null
  sourceLines: Array<{
    orderLineId?: ResourceId | null
    receiptLineId?: ResourceId | null
    outsourcingReceiptLineId?: ResourceId | null
    invoiceQuantity: FinanceMoneyPayload
    taxRate?: FinanceMoneyPayload
  }>
  remark?: string
}

export interface FinanceSourceSummary {
  sourceType: string
  sourceNo: string
  summary?: string
  restricted?: boolean
  restrictedReason?: string | null
}

export interface SalesInvoiceRecord {
  id: ResourceId
  invoiceNo: string
  externalInvoiceNo?: string | null
  customerName: string
  ownershipType: OwnershipType
  projectName?: string | null
  contractNo?: string | null
  orderNo?: string | null
  invoiceDate: string
  status: InvoiceStatus
  settlementStatus: SettlementStatus
  invoiceType: InvoiceType
  pretaxAmount: FinanceAmount
  taxAmount: FinanceAmount
  totalAmount: FinanceAmount
  unsettledAmount: FinanceAmount
  updatedAt?: string
  version: number
  allowedActions: string[]
  sources?: FinanceSourceSummary[]
  receivableLinks?: Array<{ receivableNo: string; amount: FinanceAmount }>
  settlements?: Array<{ documentNo?: string; amount: FinanceAmount }>
  voucherDrafts?: Array<{ draftNo: string; status: string }>
  auditSummary?: unknown[]
}

export interface PurchaseInvoiceRecord {
  id: ResourceId
  invoiceNo: string
  externalInvoiceNo?: string | null
  supplierName: string
  sourceType: PurchaseInvoiceSourceType
  ownershipType: OwnershipType
  projectName?: string | null
  purchaseOrderNo?: string | null
  receiptSummary?: string | null
  invoiceDate: string
  status: InvoiceStatus
  matchStatus: MatchStatus
  settlementStatus: SettlementStatus
  pretaxAmount: FinanceAmount
  taxAmount: FinanceAmount
  totalAmount: FinanceAmount
  unsettledAmount: FinanceAmount
  differenceCount?: number
  version: number
  allowedActions: string[]
  matching?: PurchaseInvoiceMatchingResult
  sources?: FinanceSourceSummary[]
  payableLinks?: Array<{ payableNo: string; amount: FinanceAmount }>
  settlements?: Array<{ documentNo?: string; amount: FinanceAmount }>
  voucherDrafts?: Array<{ draftNo: string; status: string }>
  auditSummary?: unknown[]
}

export interface SalesInvoiceCandidateLine {
  sourceLineId: ResourceId
  sourceNo: string
  lineNo?: number
  materialCode?: string
  materialName?: string
  unitName?: string
  availableQuantity: FinanceAmount
  invoicedQuantity?: FinanceAmount
  invoiceQuantity: FinanceAmount
  pretaxUnitPrice?: FinanceAmount
  taxRate?: FinanceAmount
  pretaxAmount?: FinanceAmount
  taxAmount?: FinanceAmount
  totalAmount?: FinanceAmount
}

export type PurchaseInvoiceCandidateLine = SalesInvoiceCandidateLine

export interface PurchaseInvoiceMatchingResult {
  status: MatchStatus
  differences: Array<{
    type: string
    message: string
    orderValue?: string | null
    receiptValue?: string | null
    invoiceValue?: string | null
  }>
}

export interface FinanceInvoiceApi {
  salesInvoices: {
    list(params: SalesInvoiceListParams): Promise<PageResult<SalesInvoiceRecord>>
    get(id: ResourceId): Promise<SalesInvoiceRecord>
    create(payload: SalesInvoicePayload): Promise<SalesInvoiceRecord>
    update(id: ResourceId, payload: SalesInvoicePayload): Promise<SalesInvoiceRecord>
    confirm(id: ResourceId, payload: VersionedActionPayload): Promise<SalesInvoiceRecord>
    cancel(id: ResourceId, payload: VersionedActionPayload): Promise<SalesInvoiceRecord>
  }
  salesInvoiceCandidates: {
    list(params: SalesInvoiceCandidateListParams): Promise<PageResult<SalesInvoiceCandidateLine>>
  }
  purchaseInvoices: {
    list(params: PurchaseInvoiceListParams): Promise<PageResult<PurchaseInvoiceRecord>>
    get(id: ResourceId): Promise<PurchaseInvoiceRecord>
    create(payload: PurchaseInvoicePayload): Promise<PurchaseInvoiceRecord>
    update(id: ResourceId, payload: PurchaseInvoicePayload): Promise<PurchaseInvoiceRecord>
    match(id: ResourceId, payload: VersionedActionPayload): Promise<PurchaseInvoiceRecord>
    confirm(id: ResourceId, payload: VersionedActionPayload): Promise<PurchaseInvoiceRecord>
    cancel(id: ResourceId, payload: VersionedActionPayload): Promise<PurchaseInvoiceRecord>
  }
  purchaseInvoiceCandidates: {
    list(params: PurchaseInvoiceCandidateListParams): Promise<PageResult<PurchaseInvoiceCandidateLine>>
  }
  purchaseInvoiceMatching: {
    get(id: ResourceId): Promise<PurchaseInvoiceMatchingResult>
  }
}

export function createFinanceInvoiceApi(options: FinanceStage028ApiOptions = {}): FinanceInvoiceApi {
  const api = createFinanceStage028Transport(options)
  const salesInvoicePath = (id?: ResourceId) => `/api/admin/finance/sales-invoices${id === undefined ? '' : `/${api.encodeId(id)}`}`
  const purchaseInvoicePath = (id?: ResourceId) => `/api/admin/finance/purchase-invoices${id === undefined ? '' : `/${api.encodeId(id)}`}`
  const salesInvoiceQueryKeys = ['keyword', 'customerId', 'projectId', 'status', 'settlementStatus', 'invoiceType', 'invoiceDateFrom', 'invoiceDateTo', 'externalInvoiceNo', 'sourceShipmentNo', 'page', 'pageSize'] as const
  const salesCandidateQueryKeys = ['keyword', 'customerId', 'projectId', 'contractNo', 'orderNo', 'shipmentDateFrom', 'shipmentDateTo', 'page', 'pageSize'] as const
  const purchaseInvoiceQueryKeys = ['keyword', 'supplierId', 'sourceType', 'status', 'matchStatus', 'settlementStatus', 'invoiceDateFrom', 'invoiceDateTo', 'page', 'pageSize'] as const
  const purchaseCandidateQueryKeys = ['keyword', 'supplierId', 'ownershipType', 'sourceType', 'businessDateFrom', 'businessDateTo', 'page', 'pageSize'] as const

  return {
    salesInvoices: {
      list: (params) => api.get<PageResult<SalesInvoiceRecord>>(salesInvoicePath(), api.pickQuery(params, salesInvoiceQueryKeys)),
      get: (id) => api.get<SalesInvoiceRecord>(salesInvoicePath(id)),
      create: (payload) => api.write<SalesInvoiceRecord>('POST', salesInvoicePath(), payload),
      update: (id, payload) => api.write<SalesInvoiceRecord>('PUT', salesInvoicePath(id), payload),
      confirm: (id, payload) => api.write<SalesInvoiceRecord>('PUT', `${salesInvoicePath(id)}/confirm`, payload),
      cancel: (id, payload) => api.write<SalesInvoiceRecord>('PUT', `${salesInvoicePath(id)}/cancel`, payload),
    },
    salesInvoiceCandidates: {
      list: (params) => api.get<PageResult<SalesInvoiceCandidateLine>>(`${salesInvoicePath()}/candidates`, api.pickQuery(params, salesCandidateQueryKeys)),
    },
    purchaseInvoices: {
      list: (params) => api.get<PageResult<PurchaseInvoiceRecord>>(purchaseInvoicePath(), api.pickQuery(params, purchaseInvoiceQueryKeys)),
      get: (id) => api.get<PurchaseInvoiceRecord>(purchaseInvoicePath(id)),
      create: (payload) => api.write<PurchaseInvoiceRecord>('POST', purchaseInvoicePath(), payload),
      update: (id, payload) => api.write<PurchaseInvoiceRecord>('PUT', purchaseInvoicePath(id), payload),
      match: (id, payload) => api.write<PurchaseInvoiceRecord>('PUT', `${purchaseInvoicePath(id)}/match`, payload),
      confirm: (id, payload) => api.write<PurchaseInvoiceRecord>('PUT', `${purchaseInvoicePath(id)}/confirm`, payload),
      cancel: (id, payload) => api.write<PurchaseInvoiceRecord>('PUT', `${purchaseInvoicePath(id)}/cancel`, payload),
    },
    purchaseInvoiceCandidates: {
      list: (params) => api.get<PageResult<PurchaseInvoiceCandidateLine>>(`${purchaseInvoicePath()}/candidates`, api.pickQuery(params, purchaseCandidateQueryKeys)),
    },
    purchaseInvoiceMatching: {
      get: (id) => api.get<PurchaseInvoiceMatchingResult>(`${purchaseInvoicePath(id)}/matching`),
    },
  }
}

export const financeInvoiceApi = createFinanceInvoiceApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
