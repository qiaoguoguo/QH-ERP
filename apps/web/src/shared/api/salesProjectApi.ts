import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'
import type { ResourceId, SalesOrderStatus } from './salesApi'

export type SalesProjectStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'CANCELLED'
export type SalesProjectContractStatus = 'DRAFT' | 'EFFECTIVE' | 'CLOSED' | 'TERMINATED' | 'CANCELLED'
export type SalesProjectContractType = 'MAIN' | 'SUPPLEMENT'

export interface SalesProjectListParams {
  keyword?: string | null
  customerId?: ResourceId | null
  ownerUserId?: ResourceId | null
  status?: SalesProjectStatus | null
  plannedStartFrom?: string | null
  plannedStartTo?: string | null
  plannedFinishFrom?: string | null
  plannedFinishTo?: string | null
  page: number
  pageSize: number
}

export interface SalesProjectCreatePayload {
  name: string
  customerId: ResourceId
  ownerUserId: ResourceId
  plannedStartDate?: string
  plannedFinishDate?: string
  targetRevenue?: string
  targetCost?: string
  remark?: string
}

export interface SalesProjectUpdatePayload {
  version: number
  name?: string
  ownerUserId?: ResourceId
  plannedStartDate?: string
  plannedFinishDate?: string
  targetRevenue?: string
  targetCost?: string
  remark?: string
}

export interface SalesProjectSummary {
  id: ResourceId
  projectNo: string
  name: string
  customerId: ResourceId
  customerCode: string
  customerName: string
  ownerUserId: ResourceId
  ownerUsername: string
  ownerDisplayName: string
  plannedStartDate?: string | null
  plannedFinishDate?: string | null
  status: SalesProjectStatus
  targetRevenue: string
  targetCost: string
  contractSummaryRestricted: boolean
  mainContractId?: ResourceId | null
  mainContractNo?: string | null
  mainContractStatus?: SalesProjectContractStatus | null
  effectiveContractAmount?: string | null
  contractCount?: number | null
  supplementContractCount?: number | null
  salesOrderCount?: number | null
  salesOrderSummaryRestricted: boolean
  remark?: string | null
  createdByName: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface SalesProjectDetail extends SalesProjectSummary {
  contracts: SalesProjectContractSummary[]
  salesOrderSummary: ProjectSalesOrderAggregate | null
  operations: SalesProjectOperation[]
  activatedByName?: string | null
  activatedAt?: string | null
  closedByName?: string | null
  closedAt?: string | null
  closedReason?: string | null
  cancelledByName?: string | null
  cancelledAt?: string | null
  cancelledReason?: string | null
}

export interface SalesProjectContractListParams {
  keyword?: string | null
  contractType?: SalesProjectContractType | null
  status?: SalesProjectContractStatus | null
  signedDateFrom?: string | null
  signedDateTo?: string | null
  page: number
  pageSize: number
}

export interface SalesProjectContractCreatePayload {
  contractType: SalesProjectContractType
  mainContractId?: ResourceId | null
  externalContractNo?: string
  name: string
  signedDate: string
  effectiveStartDate?: string
  effectiveEndDate?: string
  amount: string
  remark?: string
}

export interface SalesProjectContractUpdatePayload {
  version: number
  externalContractNo?: string
  name?: string
  signedDate?: string
  effectiveStartDate?: string
  effectiveEndDate?: string
  amount?: string
  remark?: string
}

export interface SalesProjectContractSummary {
  id: ResourceId
  contractNo: string
  externalContractNo?: string | null
  projectId: ResourceId
  contractType: SalesProjectContractType
  mainContractId?: ResourceId | null
  mainContractNo?: string | null
  name: string
  signedDate: string
  effectiveStartDate?: string | null
  effectiveEndDate?: string | null
  amount: string
  status: SalesProjectContractStatus
  updatedAt: string
  version: number
}

export interface SalesProjectContractDetail extends SalesProjectContractSummary {
  projectNo: string
  projectName: string
  remark?: string | null
  createdByName: string
  createdAt: string
  activatedByName?: string | null
  activatedAt?: string | null
  closedByName?: string | null
  closedAt?: string | null
  closedReason?: string | null
  terminatedByName?: string | null
  terminatedAt?: string | null
  terminatedReason?: string | null
  cancelledByName?: string | null
  cancelledAt?: string | null
  cancelledReason?: string | null
}

export interface SalesProjectActionPayload {
  version: number
  reason?: string
}

export interface ProjectOwnerCandidate {
  userId: ResourceId
  username: string
  displayName: string
}

export interface CandidateListParams {
  keyword?: string | null
  page: number
  pageSize: number
}

export interface SalesOrderProjectContractCandidate {
  projectId: ResourceId
  projectNo: string
  projectName: string
  customerId: ResourceId
  customerName: string
  contractId: ResourceId
  contractNo: string
  externalContractNo?: string | null
  contractName: string
  contractType: SalesProjectContractType
}

export interface SalesOrderLinkCandidateParams extends CandidateListParams {
  customerId?: ResourceId | null
}

export interface ProjectSalesOrderListParams {
  keyword?: string | null
  contractId?: ResourceId | null
  status?: SalesOrderStatus | null
  dateFrom?: string | null
  dateTo?: string | null
  page: number
  pageSize: number
}

export interface ProjectSalesOrderAggregate {
  orderCount: number
  draftCount: number
  confirmedCount: number
  partiallyShippedCount: number
  shippedCount: number
  closedCount: number
  cancelledCount: number
  businessAmount: string
  latestOrderDate?: string | null
}

export interface ProjectSalesOrderSummary {
  id: ResourceId
  orderNo: string
  projectId: ResourceId
  projectNo: string
  projectName: string
  contractId: ResourceId
  contractNo: string
  externalContractNo?: string | null
  customerId: ResourceId
  customerName: string
  orderDate: string
  expectedShipDate?: string | null
  status: SalesOrderStatus
  lineCount: number
  totalQuantity: string
  businessAmount: string
  createdAt: string
  updatedAt: string
}

export interface SalesProjectOperation {
  action: string
  targetType: string
  targetId: ResourceId
  targetSummary: string
  operatorUsername: string
  createdAt: string
}

export interface SalesProjectApi {
  projects: {
    list(params: SalesProjectListParams): Promise<PageResult<SalesProjectSummary>>
    get(id: ResourceId): Promise<SalesProjectDetail>
    create(payload: SalesProjectCreatePayload): Promise<SalesProjectDetail>
    update(id: ResourceId, payload: SalesProjectUpdatePayload): Promise<SalesProjectDetail>
    activate(id: ResourceId, payload: SalesProjectActionPayload): Promise<SalesProjectDetail>
    close(id: ResourceId, payload: SalesProjectActionPayload): Promise<SalesProjectDetail>
    cancel(id: ResourceId, payload: SalesProjectActionPayload): Promise<SalesProjectDetail>
  }
  contracts: {
    list(projectId: ResourceId, params: SalesProjectContractListParams): Promise<PageResult<SalesProjectContractSummary>>
    create(projectId: ResourceId, payload: SalesProjectContractCreatePayload): Promise<SalesProjectContractDetail>
    get(id: ResourceId): Promise<SalesProjectContractDetail>
    update(id: ResourceId, payload: SalesProjectContractUpdatePayload): Promise<SalesProjectContractDetail>
    activate(id: ResourceId, payload: SalesProjectActionPayload): Promise<SalesProjectContractDetail>
    close(id: ResourceId, payload: SalesProjectActionPayload): Promise<SalesProjectContractDetail>
    terminate(id: ResourceId, payload: SalesProjectActionPayload): Promise<SalesProjectContractDetail>
    cancel(id: ResourceId, payload: SalesProjectActionPayload): Promise<SalesProjectContractDetail>
  }
  ownerCandidates(params: CandidateListParams): Promise<PageResult<ProjectOwnerCandidate>>
  listOrderLinkCandidates(params: SalesOrderLinkCandidateParams): Promise<PageResult<SalesOrderProjectContractCandidate>>
  projectSalesOrders(projectId: ResourceId, params: ProjectSalesOrderListParams): Promise<PageResult<ProjectSalesOrderSummary>>
}

export interface SalesProjectApiOptions {
  baseUrl?: string
  fetcher?: (input: string, init: RequestInit) => Promise<Response>
}

export function createSalesProjectApi(options: SalesProjectApiOptions = {}): SalesProjectApi {
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

  const projectPath = (id?: ResourceId) =>
    `/api/admin/sales-projects${id === undefined ? '' : `/${encodeURIComponent(String(id))}`}`
  const contractPath = (id: ResourceId) => `/api/admin/sales-project-contracts/${encodeURIComponent(String(id))}`
  const projectKeys = [
    'keyword',
    'customerId',
    'ownerUserId',
    'status',
    'plannedStartFrom',
    'plannedStartTo',
    'plannedFinishFrom',
    'plannedFinishTo',
    'page',
    'pageSize',
  ] as const
  const contractKeys = ['keyword', 'contractType', 'status', 'signedDateFrom', 'signedDateTo', 'page', 'pageSize'] as const
  const candidateKeys = ['customerId', 'keyword', 'page', 'pageSize'] as const
  const orderKeys = ['keyword', 'contractId', 'status', 'dateFrom', 'dateTo', 'page', 'pageSize'] as const

  return {
    projects: {
      list: (params) => get<PageResult<SalesProjectSummary>>(projectPath(), pickQuery(params, projectKeys)),
      get: (id) => get<SalesProjectDetail>(projectPath(id)),
      create: (payload) => write<SalesProjectDetail>('POST', projectPath(), payload),
      update: (id, payload) => write<SalesProjectDetail>('PUT', projectPath(id), payload),
      activate: (id, payload) => write<SalesProjectDetail>('PUT', `${projectPath(id)}/activate`, payload),
      close: (id, payload) => write<SalesProjectDetail>('PUT', `${projectPath(id)}/close`, payload),
      cancel: (id, payload) => write<SalesProjectDetail>('PUT', `${projectPath(id)}/cancel`, payload),
    },
    contracts: {
      list: (projectId, params) =>
        get<PageResult<SalesProjectContractSummary>>(`${projectPath(projectId)}/contracts`, pickQuery(params, contractKeys)),
      create: (projectId, payload) => write<SalesProjectContractDetail>('POST', `${projectPath(projectId)}/contracts`, payload),
      get: (id) => get<SalesProjectContractDetail>(contractPath(id)),
      update: (id, payload) => write<SalesProjectContractDetail>('PUT', contractPath(id), payload),
      activate: (id, payload) => write<SalesProjectContractDetail>('PUT', `${contractPath(id)}/activate`, payload),
      close: (id, payload) => write<SalesProjectContractDetail>('PUT', `${contractPath(id)}/close`, payload),
      terminate: (id, payload) => write<SalesProjectContractDetail>('PUT', `${contractPath(id)}/terminate`, payload),
      cancel: (id, payload) => write<SalesProjectContractDetail>('PUT', `${contractPath(id)}/cancel`, payload),
    },
    ownerCandidates: (params) =>
      get<PageResult<ProjectOwnerCandidate>>('/api/admin/sales-projects/owner-candidates', pickQuery(params, candidateKeys)),
    listOrderLinkCandidates: (params) =>
      get<PageResult<SalesOrderProjectContractCandidate>>(
        '/api/admin/sales-projects/order-link-candidates',
        pickQuery(params, candidateKeys),
      ),
    projectSalesOrders: (projectId, params) =>
      get<PageResult<ProjectSalesOrderSummary>>(`${projectPath(projectId)}/sales-orders`, pickQuery(params, orderKeys)),
  }
}

export const salesProjectApi = createSalesProjectApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
