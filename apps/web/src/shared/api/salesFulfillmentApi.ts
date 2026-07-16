import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type { ApprovalInstanceDetail, ResourceId } from './documentPlatformApi'
import type { SalesOrderStatus } from './salesApi'
import type { SalesProjectContractType } from './salesProjectApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type DecimalString = string

export type SalesApprovalStatus = 'NONE' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'CANCELLED'
export type SalesQuoteStatus = 'DRAFT' | 'APPROVED' | 'CONVERTED' | 'EXPIRED' | 'CANCELLED'
export type SalesQuotePriceMode = 'TAX_INCLUDED' | 'UNTAXED'
export type SalesQuoteAction = 'UPDATE' | 'SUBMIT_APPROVAL' | 'CANCEL' | 'CONVERT_ORDER' | 'CONVERT_CONTRACT' | 'PRINT' | 'EXPORT'
export type SalesDeliveryPlanStatus = 'PLANNED' | 'PARTIALLY_SHIPPED' | 'SHIPPED' | 'CLOSED' | 'CANCELLED'
export type SalesDeliveryPlanAction = 'UPDATE' | 'CLOSE' | 'CREATE_SHIPMENT'
export type SalesOrderChangeStatus = 'DRAFT' | 'APPLIED' | 'CANCELLED'
export type SalesOrderChangeAction = 'UPDATE' | 'SUBMIT_APPROVAL' | 'CANCEL'
export type SalesCreditAction = 'UPDATE' | 'SUBMIT_OVERRIDE'
export type SalesProjectFulfillmentStatus = 'OPEN' | 'CLOSED'
export type SalesEffectiveDemandStatus = 'OPEN' | 'PARTIALLY_SHIPPED' | 'OVERDUE' | 'EXCLUDED'
export type SalesPriceSourceType = 'MANUAL' | 'QUOTE' | 'LEGACY_MANUAL'

export interface VersionedActionPayload {
  version: number
  reason?: string
  idempotencyKey: string
}

export interface SalesQuoteListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  projectId?: ResourceId | null
  status?: SalesQuoteStatus | null
  approvalStatus?: SalesApprovalStatus | null
  validFrom?: string | null
  validTo?: string | null
  page: number
  pageSize: number
}

export interface SalesQuoteLinePayload {
  lineNo: number
  materialId: ResourceId
  unitId: ResourceId
  quantity: DecimalString
  untaxedUnitPrice: DecimalString
  taxIncludedUnitPrice: DecimalString
  taxRate: DecimalString
  untaxedAmount: DecimalString
  taxAmount: DecimalString
  taxIncludedAmount: DecimalString
  promisedDate?: string
  remark?: string
}

export interface SalesQuoteCreatePayload {
  customerId: ResourceId
  projectId?: ResourceId | null
  quoteDate: string
  validUntil: string
  deliveryCommitment?: string
  currency: 'CNY'
  priceMode: SalesQuotePriceMode
  defaultTaxRate: DecimalString
  settlementMethod?: string
  paymentTermDays?: number | null
  paymentTerms?: string
  remark?: string
  lines: SalesQuoteLinePayload[]
}

export interface SalesQuoteUpdatePayload extends SalesQuoteCreatePayload {
  version: number
}

export interface SalesQuoteLineRecord extends Omit<SalesQuoteLinePayload, 'untaxedUnitPrice' | 'untaxedAmount' | 'promisedDate'> {
  id: ResourceId
  materialCode: string
  materialName: string
  unitName: string
  untaxedUnitPrice?: DecimalString | null
  taxExcludedUnitPrice?: DecimalString | null
  untaxedAmount?: DecimalString | null
  taxExcludedAmount?: DecimalString | null
  promisedDate?: string | null
  requiredDate?: string | null
}

export interface SalesQuoteSummaryRecord {
  id: ResourceId
  quoteNo: string
  customerId: ResourceId
  customerCode?: string | null
  customerName: string
  projectId?: ResourceId | null
  projectNo?: string | null
  projectCode?: string | null
  projectName?: string | null
  quoteDate: string
  validUntil: string
  currency: 'CNY'
  totalUntaxedAmount?: DecimalString | null
  totalTaxAmount?: DecimalString | null
  totalTaxIncludedAmount?: DecimalString | null
  taxExcludedAmount?: DecimalString | null
  taxAmount?: DecimalString | null
  taxIncludedAmount?: DecimalString | null
  status: SalesQuoteStatus
  approvalStatus?: SalesApprovalStatus | null
  convertedTargetType?: 'ORDER' | 'CONTRACT' | null
  convertedTargetId?: ResourceId | null
  convertedTargetNo?: string | null
  allowedActions: SalesQuoteAction[]
  actionDisabledReason?: string | null
  creditRestricted?: boolean
  contractRestricted?: boolean
  amountRestricted?: boolean
  createdByName?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  version: number
}

export interface SalesQuoteDetailRecord extends SalesQuoteSummaryRecord {
  deliveryCommitment?: string | null
  priceMode: SalesQuotePriceMode
  defaultTaxRate: DecimalString
  settlementMethod?: string | null
  paymentTermDays?: number | null
  paymentTerms?: string | null
  remark?: string | null
  approvalSummary?: unknown
  lines: SalesQuoteLineRecord[]
}

export interface SalesQuoteConvertOrderPayload {
  version: number
  projectId?: ResourceId | null
  contractId?: ResourceId | null
  idempotencyKey: string
}

export interface SalesQuoteConvertContractPayload {
  version: number
  projectId: ResourceId
  contractType: SalesProjectContractType
  mainContractId?: ResourceId | null
  idempotencyKey: string
}

export interface SalesOrderDeliveryPlanListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  projectId?: ResourceId | null
  contractId?: ResourceId | null
  orderId?: ResourceId | null
  materialId?: ResourceId | null
  status?: SalesDeliveryPlanStatus | null
  expectedDateFrom?: string | null
  expectedDateTo?: string | null
  countedOnly?: boolean | null
  page: number
  pageSize: number
}

export interface SalesDeliveryPlanRecord {
  id: ResourceId
  planNo?: string | null
  orderId: ResourceId
  orderNo: string
  orderLineId: ResourceId
  lineNo: number
  customerId: ResourceId
  customerName: string
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  contractId?: ResourceId | null
  contractNo?: string | null
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName: string
  planDate?: string | null
  plannedDate?: string | null
  plannedQuantity: DecimalString
  shippedQuantity: DecimalString
  remainingQuantity: DecimalString
  status: SalesDeliveryPlanStatus
  closeReason?: string | null
  allowedActions: SalesDeliveryPlanAction[]
  actionDisabledReason?: string | null
  legacyDeliveryPlanCompatible?: boolean
  version: number
}

export interface SalesOrderDeliveryPlanPayloadLine {
  orderLineId: ResourceId
  planDate: string
  quantity: DecimalString
  remark?: string
}

export interface SalesOrderDeliveryPlanReplacePayload extends VersionedActionPayload {
  lines: SalesOrderDeliveryPlanPayloadLine[]
}

export interface SalesOrderChangeListParams {
  status?: SalesOrderChangeStatus | null
  page: number
  pageSize: number
}

export interface SalesOrderChangeLinePayload {
  orderLineId: ResourceId
  targetQuantity?: DecimalString
  untaxedUnitPrice?: DecimalString
  taxIncludedUnitPrice?: DecimalString
  taxRate?: DecimalString
  plannedDate?: string
}

export interface SalesOrderChangeLineRecord extends SalesOrderChangeLinePayload {
  id?: ResourceId
  lineNo?: number
  materialId?: ResourceId
  materialCode?: string
  materialName?: string
  currentQuantity?: DecimalString
  shippedQuantity?: DecimalString
  newQuantity?: DecimalString
  taxExcludedUnitPrice?: DecimalString
  newPlannedDate?: string | null
}

export interface SalesOrderChangeCreatePayload {
  version: number
  idempotencyKey: string
  reason: string
  lines: SalesOrderChangeLinePayload[]
}

export interface SalesOrderChangeUpdatePayload extends SalesOrderChangeCreatePayload {}

export interface SalesOrderChangeRecord {
  id: ResourceId
  changeNo: string
  orderId: ResourceId
  orderNo: string
  status: SalesOrderChangeStatus
  approvalStatus?: SalesApprovalStatus | null
  reason: string
  allowedActions: SalesOrderChangeAction[]
  actionDisabledReason?: string | null
  version: number
  lines?: SalesOrderChangeLineRecord[]
}

export interface SalesCreditProfileListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  frozen?: boolean | null
  page: number
  pageSize: number
}

export interface SalesCreditProfilePayload {
  customerId: ResourceId
  creditLimit: DecimalString
  frozen: boolean
  blockOverdue: boolean
  reviewDate?: string
  remark?: string
}

export interface SalesCreditProfileUpdatePayload extends SalesCreditProfilePayload {
  version: number
}

export interface SalesCreditExposureRecord {
  orderCommitmentAmount: DecimalString | null
  unsettledShipmentAmount: DecimalString | null
  receivableOutstandingAmount: DecimalString | null
  usedCredit: DecimalString | null
  availableCredit: DecimalString | null
  overdueRisk?: boolean | null
}

export interface SalesCreditProfileRecord {
  customerId: ResourceId
  customerCode?: string | null
  customerName: string
  creditLimit: DecimalString | null
  frozen: boolean | null
  blockOverdue: boolean | null
  reviewDate?: string | null
  remark?: string | null
  exposure?: SalesCreditExposureRecord | null
  creditRestricted: boolean
  allowedActions: SalesCreditAction[]
  actionDisabledReason?: string | null
  version: number
}

export interface SalesProjectFulfillmentRecord {
  projectId: ResourceId
  projectNo?: string | null
  projectName?: string | null
  status: SalesProjectFulfillmentStatus
  contractRestricted?: boolean
  creditRestricted?: boolean
  contractEffectiveAmount?: DecimalString | null
  orderTaxIncludedAmount?: DecimalString | null
  plannedQuantity?: DecimalString | null
  shippedQuantity?: DecimalString | null
  returnedQuantity?: DecimalString | null
  netDeliveredQuantity?: DecimalString | null
  openDemandQuantity?: DecimalString | null
  overduePlanCount?: number | null
  creditRiskSummary?: string | null
  legacyDeliveryPlanCompatible?: boolean
  blockReasons: string[]
  allowedActions: Array<'CLOSE'>
  actionDisabledReason?: string | null
  version: number
}

export interface SalesEffectiveDemandListParams {
  projectId?: ResourceId | null
  customerId?: ResourceId | null
  contractId?: ResourceId | null
  materialId?: ResourceId | null
  status?: SalesEffectiveDemandStatus | null
  expectedDateFrom?: string | null
  expectedDateTo?: string | null
  countedOnly?: boolean | null
  page: number
  pageSize: number
}

export interface SalesEffectiveDemandRecord {
  id: ResourceId
  sourceType: string
  sourceId: ResourceId
  sourceNo: string
  sourceVersion: number
  orderId: ResourceId
  orderNo: string
  orderLineId: ResourceId
  deliveryPlanId?: ResourceId | null
  projectId?: ResourceId | null
  projectNo?: string | null
  projectName?: string | null
  customerId: ResourceId
  customerName: string
  contractId?: ResourceId | null
  contractNo?: string | null
  quoteId?: ResourceId | null
  quoteNo?: string | null
  materialId: ResourceId
  materialCode: string
  materialName: string
  unitName: string
  orderQuantity: DecimalString
  plannedQuantity: DecimalString
  shippedQuantity: DecimalString
  returnedQuantity: DecimalString
  netQuantity: DecimalString
  openQuantity: DecimalString
  expectedDate: string
  status: SalesEffectiveDemandStatus
  countedAsEffectiveDemand: boolean
  excludedReasonCode?: string | null
  updatedAt: string
}

export interface SalesFulfillmentApi {
  quotes: {
    list(params: SalesQuoteListParams): Promise<PageResult<SalesQuoteSummaryRecord>>
    get(id: ResourceId): Promise<SalesQuoteDetailRecord>
    create(payload: SalesQuoteCreatePayload): Promise<SalesQuoteDetailRecord>
    update(id: ResourceId, payload: SalesQuoteUpdatePayload): Promise<SalesQuoteDetailRecord>
    submitApproval(id: ResourceId, payload: VersionedActionPayload): Promise<ApprovalInstanceDetail>
    cancel(id: ResourceId, payload: VersionedActionPayload): Promise<SalesQuoteDetailRecord>
    convertOrder(id: ResourceId, payload: SalesQuoteConvertOrderPayload): Promise<{ id: ResourceId; orderNo: string }>
    convertContract(id: ResourceId, payload: SalesQuoteConvertContractPayload): Promise<{ id: ResourceId; contractNo: string }>
  }
  deliveryPlans: {
    list(params: SalesOrderDeliveryPlanListParams): Promise<PageResult<SalesDeliveryPlanRecord>>
    listByOrder(orderId: ResourceId): Promise<PageResult<SalesDeliveryPlanRecord>>
    replaceForOrder(orderId: ResourceId, payload: SalesOrderDeliveryPlanReplacePayload): Promise<{ lines: SalesDeliveryPlanRecord[] }>
    close(orderId: ResourceId, planId: ResourceId, payload: VersionedActionPayload): Promise<SalesDeliveryPlanRecord>
  }
  orderChanges: {
    list(orderId: ResourceId, params: SalesOrderChangeListParams): Promise<PageResult<SalesOrderChangeRecord>>
    get(id: ResourceId): Promise<SalesOrderChangeRecord>
    create(orderId: ResourceId, payload: SalesOrderChangeCreatePayload): Promise<SalesOrderChangeRecord>
    update(id: ResourceId, payload: SalesOrderChangeUpdatePayload): Promise<SalesOrderChangeRecord>
    submitApproval(id: ResourceId, payload: VersionedActionPayload): Promise<ApprovalInstanceDetail>
    cancel(id: ResourceId, payload: VersionedActionPayload): Promise<SalesOrderChangeRecord>
  }
  creditProfiles: {
    list(params: SalesCreditProfileListParams): Promise<PageResult<SalesCreditProfileRecord>>
    get(customerId: ResourceId): Promise<SalesCreditProfileRecord>
    upsert(customerId: ResourceId, payload: SalesCreditProfilePayload | SalesCreditProfileUpdatePayload): Promise<SalesCreditProfileRecord>
    exposure(customerId: ResourceId): Promise<SalesCreditExposureRecord>
  }
  orders: {
    submitCreditOverride(id: ResourceId, payload: VersionedActionPayload): Promise<ApprovalInstanceDetail>
    close(id: ResourceId, payload: VersionedActionPayload): Promise<{ id: ResourceId; status: SalesOrderStatus; version: number }>
    submitShortClose(id: ResourceId, payload: VersionedActionPayload): Promise<ApprovalInstanceDetail>
  }
  projectFulfillment: {
    get(projectId: ResourceId): Promise<SalesProjectFulfillmentRecord>
    close(projectId: ResourceId, payload: VersionedActionPayload): Promise<SalesProjectFulfillmentRecord>
  }
  effectiveDemands: {
    list(params: SalesEffectiveDemandListParams): Promise<PageResult<SalesEffectiveDemandRecord>>
  }
}

export interface SalesFulfillmentApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createSalesFulfillmentApi(options: SalesFulfillmentApiOptions = {}): SalesFulfillmentApi {
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
    const headers: Record<string, string> = { [csrf.headerName]: csrf.token }
    const init: RequestInit = { headers, method }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }
    return request<T>(path, init)
  }

  const quotePath = (id?: ResourceId) =>
    `/api/admin/sales/quotes${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const orderPath = (id: ResourceId) => `/api/admin/sales/orders/${encodeURIComponent(String(id))}`
  const orderChangePath = (id: ResourceId) => `/api/admin/sales/order-changes/${encodeURIComponent(String(id))}`
  const projectPath = (id: ResourceId) => `/api/admin/sales-projects/${encodeURIComponent(String(id))}`
  const quoteKeys = ['keyword', 'customerId', 'projectId', 'status', 'approvalStatus', 'validFrom', 'validTo', 'page', 'pageSize'] as const
  const planKeys = ['keyword', 'customerId', 'projectId', 'contractId', 'orderId', 'materialId', 'status', 'expectedDateFrom', 'expectedDateTo', 'countedOnly', 'page', 'pageSize'] as const
  const changeKeys = ['status', 'page', 'pageSize'] as const
  const creditKeys = ['keyword', 'customerId', 'frozen', 'page', 'pageSize'] as const
  const demandKeys = ['projectId', 'customerId', 'contractId', 'materialId', 'status', 'expectedDateFrom', 'expectedDateTo', 'countedOnly', 'page', 'pageSize'] as const

  return {
    quotes: {
      list: (params) => get<PageResult<SalesQuoteSummaryRecord>>(quotePath(), pickQuery(params, quoteKeys)),
      get: (id) => get<SalesQuoteDetailRecord>(quotePath(id)),
      create: (payload) => write<SalesQuoteDetailRecord>('POST', quotePath(), payload),
      update: (id, payload) => write<SalesQuoteDetailRecord>('PUT', quotePath(id), payload),
      submitApproval: (id, payload) =>
        write<ApprovalInstanceDetail>('POST', `${quotePath(id)}/submit-approval`, payload),
      cancel: (id, payload) => write<SalesQuoteDetailRecord>('POST', `${quotePath(id)}/cancel`, payload),
      convertOrder: (id, payload) =>
        write<{ id: ResourceId; orderNo: string }>('POST', `${quotePath(id)}/convert-order`, payload),
      convertContract: (id, payload) =>
        write<{ id: ResourceId; contractNo: string }>('POST', `${quotePath(id)}/convert-contract`, payload),
    },
    deliveryPlans: {
      list: (params) =>
        get<PageResult<SalesDeliveryPlanRecord>>('/api/admin/sales/delivery-plans', pickQuery(params, planKeys)),
      listByOrder: (orderId) => get<PageResult<SalesDeliveryPlanRecord>>(`${orderPath(orderId)}/delivery-plans`),
      replaceForOrder: (orderId, payload) =>
        write<{ lines: SalesDeliveryPlanRecord[] }>('PUT', `${orderPath(orderId)}/delivery-plans`, payload),
      close: (orderId, planId, payload) =>
        write<SalesDeliveryPlanRecord>('PUT', `${orderPath(orderId)}/delivery-plans/${encodeURIComponent(String(planId))}/close`, payload),
    },
    orderChanges: {
      list: (orderId, params) =>
        get<PageResult<SalesOrderChangeRecord>>(`${orderPath(orderId)}/changes`, pickQuery(params, changeKeys)),
      get: (id) => get<SalesOrderChangeRecord>(orderChangePath(id)),
      create: (orderId, payload) =>
        write<SalesOrderChangeRecord>('POST', `${orderPath(orderId)}/changes`, payload),
      update: (id, payload) =>
        write<SalesOrderChangeRecord>('PUT', orderChangePath(id), payload),
      submitApproval: (id, payload) =>
        write<ApprovalInstanceDetail>('POST', `${orderChangePath(id)}/submit-approval`, payload),
      cancel: (id, payload) => write<SalesOrderChangeRecord>('POST', `${orderChangePath(id)}/cancel`, payload),
    },
    creditProfiles: {
      list: (params) =>
        get<PageResult<SalesCreditProfileRecord>>('/api/admin/sales/credit-profiles', pickQuery(params, creditKeys)),
      get: (customerId) =>
        get<SalesCreditProfileRecord>(`/api/admin/sales/credit-profiles/${encodeURIComponent(String(customerId))}`),
      upsert: (customerId, payload) =>
        write<SalesCreditProfileRecord>('POST', '/api/admin/sales/credit-profiles', {
          ...payload,
          customerId,
        }),
      exposure: (customerId) =>
        get<SalesCreditExposureRecord>(`/api/admin/sales/customers/${encodeURIComponent(String(customerId))}/credit-exposure`),
    },
    orders: {
      submitCreditOverride: (id, payload) =>
        write<ApprovalInstanceDetail>('POST', `${orderPath(id)}/submit-credit-override`, payload),
      close: (id, payload) =>
        write<{ id: ResourceId; status: SalesOrderStatus; version: number }>('PUT', `${orderPath(id)}/close`, payload),
      submitShortClose: (id, payload) =>
        write<ApprovalInstanceDetail>('POST', `${orderPath(id)}/submit-short-close`, payload),
    },
    projectFulfillment: {
      get: (projectId) => get<SalesProjectFulfillmentRecord>(`${projectPath(projectId)}/fulfillment`),
      close: (projectId, payload) =>
        write<SalesProjectFulfillmentRecord>('POST', `${projectPath(projectId)}/close-sales-fulfillment`, payload),
    },
    effectiveDemands: {
      list: (params) =>
        get<PageResult<SalesEffectiveDemandRecord>>('/api/admin/sales/effective-demands', pickQuery({
          ...params,
          countedOnly: params.countedOnly ?? true,
        }, demandKeys)),
    },
  }
}

export const salesFulfillmentApi = createSalesFulfillmentApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
