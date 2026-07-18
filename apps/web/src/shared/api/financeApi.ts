import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type FinanceAmount = string | number
export type FinanceSourceType = 'SALES_SHIPMENT' | 'PURCHASE_RECEIPT' | 'SALES_INVOICE' | 'PURCHASE_INVOICE' | 'EXPENSE'
export type ReceivableStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CLOSED' | 'CANCELLED'
export type ReceiptStatus = 'DRAFT' | 'POSTED' | 'CANCELLED'
export type PayableStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_PAID' | 'PAID' | 'CLOSED' | 'CANCELLED'
export type PaymentStatus = 'DRAFT' | 'POSTED' | 'CANCELLED'
export type FinanceMoneyPayload = string

export interface ReceivableCandidateSourceListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  dateFrom?: string | null
  dateTo?: string | null
  settlementGenerated?: boolean | null
  page: number
  pageSize: number
}

export interface PayableCandidateSourceListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  dateFrom?: string | null
  dateTo?: string | null
  settlementGenerated?: boolean | null
  page: number
  pageSize: number
}

export interface ReceivableCandidateSource {
  sourceType: 'SALES_SHIPMENT'
  sourceId: ResourceId
  sourceNo: string
  salesOrderId: ResourceId
  salesOrderNo: string
  customerId: ResourceId
  customerCode: string
  customerName: string
  businessDate: string
  totalAmount: FinanceAmount
  lineCount: number
  settlementGenerated: boolean
}

export interface PayableCandidateSource {
  sourceType: 'PURCHASE_RECEIPT'
  sourceId: ResourceId
  sourceNo: string
  purchaseOrderId: ResourceId
  purchaseOrderNo: string
  supplierId: ResourceId
  supplierCode: string
  supplierName: string
  businessDate: string
  totalAmount: FinanceAmount
  lineCount: number
  settlementGenerated: boolean
}

export interface ReceivableListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  status?: ReceivableStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  dueDateFrom?: string | null
  dueDateTo?: string | null
  sourceNo?: string | null
  page: number
  pageSize: number
}

export interface ReceiptListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  status?: ReceiptStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  receivableId?: ResourceId | null
  page: number
  pageSize: number
}

export interface PayableListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  status?: PayableStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  dueDateFrom?: string | null
  dueDateTo?: string | null
  sourceNo?: string | null
  page: number
  pageSize: number
}

export interface PaymentListParams {
  keyword?: string | null
  supplierId?: ResourceId | null
  status?: PaymentStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  payableId?: ResourceId | null
  page: number
  pageSize: number
}

export interface ReceivableSummaryRecord {
  id: ResourceId
  receivableNo: string
  customerId: ResourceId
  customerCode: string
  customerName: string
  sourceType: 'SALES_SHIPMENT'
  sourceId: ResourceId
  sourceNo: string
  salesOrderId: ResourceId
  salesOrderNo: string
  businessDate: string
  dueDate: string
  totalAmount: FinanceAmount
  receivedAmount: FinanceAmount
  unreceivedAmount: FinanceAmount
  status: ReceivableStatus
  remark?: string | null
  createdByName: string
  createdAt: string
  updatedAt: string
  invoiceLinks?: Array<{ invoiceNo: string; amount: FinanceAmount }>
  allocationSummary?: { targetCount?: number; allocatedAmount?: FinanceAmount; availableAmount?: FinanceAmount }
  voucherDrafts?: Array<{ draftNo: string; status: string }>
}

export interface ReceivableSourceRecord {
  id: ResourceId
  sourceType: 'SALES_SHIPMENT'
  sourceId: ResourceId
  sourceNo: string
  sourceLineId: ResourceId
  sourceLineNo: number
  sourceBusinessDate: string
  salesOrderId: ResourceId
  salesOrderNo: string
  salesOrderLineId: ResourceId
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName: string
  quantity: FinanceAmount
  unitPrice: FinanceAmount
  sourceAmount: FinanceAmount
}

export interface ReceiptSummaryRecord {
  id: ResourceId
  receiptNo: string
  receivableId: ResourceId
  receivableNo: string
  customerId: ResourceId
  customerName: string
  receiptDate: string
  amount: FinanceAmount
  method: string
  status: ReceiptStatus
  remark?: string | null
  createdByName: string
  postedByName?: string | null
  postedAt?: string | null
  allocatedAmount?: FinanceAmount
  availableAmount?: FinanceAmount
  allocationTargetCount?: number
  advanceReceiptStatus?: string | null
  invoiceLinks?: Array<{ invoiceNo: string; amount: FinanceAmount }>
  voucherDrafts?: Array<{ draftNo: string; status: string }>
}

export interface ReceiptAllocationRecord {
  id: ResourceId
  receiptId: ResourceId
  receiptNo: string
  receivableId: ResourceId
  receivableNo: string
  customerId: ResourceId
  customerName: string
  allocatedAmount: FinanceAmount
}

export interface ReceivableDetailRecord extends ReceivableSummaryRecord {
  sources: ReceivableSourceRecord[]
  receipts: ReceiptSummaryRecord[]
  auditSummary?: unknown[]
}

export interface ReceiptDetailRecord extends ReceiptSummaryRecord {
  allocations: ReceiptAllocationRecord[]
}

export interface PayableSummaryRecord {
  id: ResourceId
  payableNo: string
  supplierId: ResourceId
  supplierCode: string
  supplierName: string
  sourceType: 'PURCHASE_RECEIPT'
  sourceId: ResourceId
  sourceNo: string
  purchaseOrderId: ResourceId
  purchaseOrderNo: string
  businessDate: string
  dueDate: string
  totalAmount: FinanceAmount
  paidAmount: FinanceAmount
  unpaidAmount: FinanceAmount
  status: PayableStatus
  remark?: string | null
  createdByName: string
  createdAt: string
  updatedAt: string
  invoiceLinks?: Array<{ invoiceNo: string; amount: FinanceAmount }>
  expenseLinks?: Array<{ expenseNo: string; amount: FinanceAmount }>
  allocationSummary?: { targetCount?: number; allocatedAmount?: FinanceAmount; availableAmount?: FinanceAmount }
  voucherDrafts?: Array<{ draftNo: string; status: string }>
}

export interface PayableSourceRecord {
  id: ResourceId
  sourceType: 'PURCHASE_RECEIPT'
  sourceId: ResourceId
  sourceNo: string
  sourceLineId: ResourceId
  sourceLineNo: number
  sourceBusinessDate: string
  purchaseOrderId: ResourceId
  purchaseOrderNo: string
  purchaseOrderLineId: ResourceId
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName: string
  quantity: FinanceAmount
  unitPrice: FinanceAmount
  sourceAmount: FinanceAmount
}

export interface PaymentSummaryRecord {
  id: ResourceId
  paymentNo: string
  payableId: ResourceId
  payableNo: string
  supplierId: ResourceId
  supplierName: string
  paymentDate: string
  amount: FinanceAmount
  method: string
  status: PaymentStatus
  remark?: string | null
  createdByName: string
  postedByName?: string | null
  postedAt?: string | null
  allocatedAmount?: FinanceAmount
  availableAmount?: FinanceAmount
  allocationTargetCount?: number
  prepaymentStatus?: string | null
  invoiceLinks?: Array<{ invoiceNo: string; amount: FinanceAmount }>
  expenseLinks?: Array<{ expenseNo: string; amount: FinanceAmount }>
  voucherDrafts?: Array<{ draftNo: string; status: string }>
}

export interface PaymentAllocationRecord {
  id: ResourceId
  paymentId: ResourceId
  paymentNo: string
  payableId: ResourceId
  payableNo: string
  supplierId: ResourceId
  supplierName: string
  allocatedAmount: FinanceAmount
}

export interface PayableDetailRecord extends PayableSummaryRecord {
  sources: PayableSourceRecord[]
  payments: PaymentSummaryRecord[]
  auditSummary?: unknown[]
}

export interface PaymentDetailRecord extends PaymentSummaryRecord {
  allocations: PaymentAllocationRecord[]
}

export interface ReceivablePayload {
  sourceType: 'SALES_SHIPMENT'
  sourceId: ResourceId
  dueDate: string
  remark?: string
}

export interface ReceivableUpdatePayload {
  dueDate: string
  remark?: string
}

export interface ReceiptPayload {
  receiptDate: string
  amount: FinanceMoneyPayload
  method: string
  remark?: string
}

export interface PayablePayload {
  sourceType: 'PURCHASE_RECEIPT'
  sourceId: ResourceId
  dueDate: string
  remark?: string
}

export interface PayableUpdatePayload {
  dueDate: string
  remark?: string
}

export interface PaymentPayload {
  paymentDate: string
  amount: FinanceMoneyPayload
  method: string
  remark?: string
}

export interface FinanceApi {
  receivables: {
    list(params: ReceivableListParams): Promise<PageResult<ReceivableSummaryRecord>>
    get(id: ResourceId): Promise<ReceivableDetailRecord>
    create(payload: ReceivablePayload): Promise<ReceivableDetailRecord>
    update(id: ResourceId, payload: ReceivableUpdatePayload): Promise<ReceivableDetailRecord>
    confirm(id: ResourceId): Promise<ReceivableDetailRecord>
    cancel(id: ResourceId): Promise<ReceivableDetailRecord>
    close(id: ResourceId): Promise<ReceivableDetailRecord>
  }
  receipts: {
    list(params: ReceiptListParams): Promise<PageResult<ReceiptSummaryRecord>>
    get(id: ResourceId): Promise<ReceiptDetailRecord>
    create(receivableId: ResourceId, payload: ReceiptPayload): Promise<ReceiptDetailRecord>
    update(id: ResourceId, payload: ReceiptPayload): Promise<ReceiptDetailRecord>
    post(id: ResourceId): Promise<ReceiptDetailRecord>
    cancel(id: ResourceId): Promise<ReceiptDetailRecord>
  }
  payables: {
    list(params: PayableListParams): Promise<PageResult<PayableSummaryRecord>>
    get(id: ResourceId): Promise<PayableDetailRecord>
    create(payload: PayablePayload): Promise<PayableDetailRecord>
    update(id: ResourceId, payload: PayableUpdatePayload): Promise<PayableDetailRecord>
    confirm(id: ResourceId): Promise<PayableDetailRecord>
    cancel(id: ResourceId): Promise<PayableDetailRecord>
    close(id: ResourceId): Promise<PayableDetailRecord>
  }
  payments: {
    list(params: PaymentListParams): Promise<PageResult<PaymentSummaryRecord>>
    get(id: ResourceId): Promise<PaymentDetailRecord>
    create(payableId: ResourceId, payload: PaymentPayload): Promise<PaymentDetailRecord>
    update(id: ResourceId, payload: PaymentPayload): Promise<PaymentDetailRecord>
    post(id: ResourceId): Promise<PaymentDetailRecord>
    cancel(id: ResourceId): Promise<PaymentDetailRecord>
  }
  sources: {
    receivableCandidates: {
      list(params: ReceivableCandidateSourceListParams): Promise<PageResult<ReceivableCandidateSource>>
    }
    payableCandidates: {
      list(params: PayableCandidateSourceListParams): Promise<PageResult<PayableCandidateSource>>
    }
  }
}

export interface FinanceApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createFinanceApi(options: FinanceApiOptions = {}): FinanceApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const receivableQueryKeys = [
    'keyword',
    'customerId',
    'status',
    'dateFrom',
    'dateTo',
    'dueDateFrom',
    'dueDateTo',
    'sourceNo',
    'page',
    'pageSize',
  ] as const
  const receiptQueryKeys = [
    'keyword',
    'customerId',
    'status',
    'dateFrom',
    'dateTo',
    'receivableId',
    'page',
    'pageSize',
  ] as const
  const payableQueryKeys = [
    'keyword',
    'supplierId',
    'status',
    'dateFrom',
    'dateTo',
    'dueDateFrom',
    'dueDateTo',
    'sourceNo',
    'page',
    'pageSize',
  ] as const
  const paymentQueryKeys = [
    'keyword',
    'supplierId',
    'status',
    'dateFrom',
    'dateTo',
    'payableId',
    'page',
    'pageSize',
  ] as const
  const receivableSourceQueryKeys = [
    'keyword',
    'customerId',
    'dateFrom',
    'dateTo',
    'settlementGenerated',
    'page',
    'pageSize',
  ] as const
  const payableSourceQueryKeys = [
    'keyword',
    'supplierId',
    'dateFrom',
    'dateTo',
    'settlementGenerated',
    'page',
    'pageSize',
  ] as const

  const pickQuery = (query: object | undefined, keys: readonly string[]) => {
    const result: Record<string, unknown> = {}
    keys.forEach((key) => {
      const value = (query as Record<string, unknown> | undefined)?.[key]
      if (value !== undefined) {
        result[key] = value
      }
    })
    return result
  }

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
    const init: RequestInit = {
      headers,
      method,
    }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }

    return request<T>(path, init)
  }

  const receivablePath = (id?: ResourceId) =>
    `/api/admin/finance/receivables${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const receiptPath = (id?: ResourceId) =>
    `/api/admin/finance/receipts${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const payablePath = (id?: ResourceId) =>
    `/api/admin/finance/payables${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const paymentPath = (id?: ResourceId) =>
    `/api/admin/finance/payments${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`

  return {
    receivables: {
      list: (params) =>
        get<PageResult<ReceivableSummaryRecord>>(
          '/api/admin/finance/receivables',
          pickQuery(params, receivableQueryKeys),
        ),
      get: (id) => get<ReceivableDetailRecord>(receivablePath(id)),
      create: (payload) => write<ReceivableDetailRecord>('POST', receivablePath(), payload),
      update: (id, payload) => write<ReceivableDetailRecord>('PUT', receivablePath(id), payload),
      confirm: (id) => write<ReceivableDetailRecord>('PUT', `${receivablePath(id)}/confirm`),
      cancel: (id) => write<ReceivableDetailRecord>('PUT', `${receivablePath(id)}/cancel`),
      close: (id) => write<ReceivableDetailRecord>('PUT', `${receivablePath(id)}/close`),
    },
    receipts: {
      list: (params) =>
        get<PageResult<ReceiptSummaryRecord>>('/api/admin/finance/receipts', pickQuery(params, receiptQueryKeys)),
      get: (id) => get<ReceiptDetailRecord>(receiptPath(id)),
      create: (receivableId, payload) =>
        write<ReceiptDetailRecord>('POST', `${receivablePath(receivableId)}/receipts`, payload),
      update: (id, payload) => write<ReceiptDetailRecord>('PUT', receiptPath(id), payload),
      post: (id) => write<ReceiptDetailRecord>('PUT', `${receiptPath(id)}/post`),
      cancel: (id) => write<ReceiptDetailRecord>('PUT', `${receiptPath(id)}/cancel`),
    },
    payables: {
      list: (params) =>
        get<PageResult<PayableSummaryRecord>>('/api/admin/finance/payables', pickQuery(params, payableQueryKeys)),
      get: (id) => get<PayableDetailRecord>(payablePath(id)),
      create: (payload) => write<PayableDetailRecord>('POST', payablePath(), payload),
      update: (id, payload) => write<PayableDetailRecord>('PUT', payablePath(id), payload),
      confirm: (id) => write<PayableDetailRecord>('PUT', `${payablePath(id)}/confirm`),
      cancel: (id) => write<PayableDetailRecord>('PUT', `${payablePath(id)}/cancel`),
      close: (id) => write<PayableDetailRecord>('PUT', `${payablePath(id)}/close`),
    },
    payments: {
      list: (params) =>
        get<PageResult<PaymentSummaryRecord>>('/api/admin/finance/payments', pickQuery(params, paymentQueryKeys)),
      get: (id) => get<PaymentDetailRecord>(paymentPath(id)),
      create: (payableId, payload) => write<PaymentDetailRecord>('POST', `${payablePath(payableId)}/payments`, payload),
      update: (id, payload) => write<PaymentDetailRecord>('PUT', paymentPath(id), payload),
      post: (id) => write<PaymentDetailRecord>('PUT', `${paymentPath(id)}/post`),
      cancel: (id) => write<PaymentDetailRecord>('PUT', `${paymentPath(id)}/cancel`),
    },
    sources: {
      receivableCandidates: {
        list: (params) =>
          get<PageResult<ReceivableCandidateSource>>(
            '/api/admin/finance/receivable-sources',
            pickQuery(params, receivableSourceQueryKeys),
          ),
      },
      payableCandidates: {
        list: (params) =>
          get<PageResult<PayableCandidateSource>>(
            '/api/admin/finance/payable-sources',
            pickQuery(params, payableSourceQueryKeys),
          ),
      },
    },
  }
}

export const financeApi = createFinanceApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
