import { AccountPermissionApiError, type ApiEnvelope, type CsrfToken, type PageResult } from './accountPermissionApi'

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>
export type ResourceId = string | number
export type MasterDataStatus = 'ENABLED' | 'DISABLED'
export type MaterialType = 'RAW_MATERIAL' | 'SEMI_FINISHED' | 'FINISHED_GOOD' | 'AUXILIARY'
export type MaterialSourceType = 'PURCHASED' | 'SELF_MADE' | 'OUTSOURCED'
export type MaterialTrackingMethod = 'NONE' | 'BATCH' | 'SERIAL'
export type CostCategory =
  | 'DIRECT_MATERIAL'
  | 'AUXILIARY_MATERIAL'
  | 'SEMI_FINISHED'
  | 'FINISHED_GOOD'
  | 'OUTSOURCING'
  | 'SERVICE'
  | 'UNCLASSIFIED'
export type InventoryValuationCategory =
  | 'VALUATED_MATERIAL'
  | 'NON_VALUATED_CONSUMABLE'
  | 'SERVICE_NON_STOCK'
  | 'UNCLASSIFIED'
export type RoundingMode = 'HALF_UP' | 'UP' | 'DOWN'
export type CodingObjectType = 'MATERIAL' | 'CUSTOMER' | 'SUPPLIER' | 'BOM' | 'BOM_ECO'
export type CodingDatePattern = 'NONE' | 'YYYY' | 'YYYYMM' | 'YYYYMMDD'
export type CodingResetCycle = 'NEVER' | 'YEAR' | 'MONTH' | 'DAY'
export type InvoiceType = 'GENERAL_VAT' | 'SPECIAL_VAT' | 'NONE'
export type SettlementMethod = 'MONTHLY' | 'CASH_ON_DELIVERY' | 'ADVANCE' | 'CUSTOM'

export interface VersionPayload {
  version: number
}

export interface MasterDataListQuery {
  keyword?: string
  status?: MasterDataStatus
  page: number
  pageSize: number
}

export type MaterialCategoryListQuery = MasterDataListQuery

export interface MaterialListQuery extends MasterDataListQuery {
  materialType?: MaterialType
  sourceType?: MaterialSourceType
  trackingMethod?: MaterialTrackingMethod
  categoryId?: ResourceId
}

export interface CandidateListQuery {
  keyword?: string
  page: number
  pageSize: number
  selectedIds?: ResourceId[]
}

export interface CandidateItem {
  id: ResourceId
  code: string
  name: string
  status?: string
  disabled?: boolean
  disabledReason?: string | null
  summary?: string | null
  [key: string]: unknown
}

export interface CandidatePageResult<TItem extends CandidateItem = CandidateItem> extends PageResult<TItem> {
  selectedItems: TItem[]
}

interface BaseMasterRecord {
  id: ResourceId
  code: string
  name: string
  status: MasterDataStatus
  remark?: string | null
  createdAt?: string
  updatedAt?: string
  version?: number
}

export interface UnitRecord extends BaseMasterRecord {
  precisionScale: number
  sortOrder: number
}

export interface WarehouseRecord extends BaseMasterRecord {
  warehouseType?: string | null
  managerName?: string | null
  address?: string | null
}

export interface PartnerRecord extends BaseMasterRecord {
  contactName?: string | null
  contactPhone?: string | null
  settlementTaxSummary?: SettlementTaxSummary | null
}

export interface CategoryRecord extends BaseMasterRecord {
  parentId?: ResourceId | null
  sortOrder: number
}

export interface MaterialRecord extends BaseMasterRecord {
  specification?: string | null
  materialType: MaterialType
  sourceType: MaterialSourceType
  trackingMethod: MaterialTrackingMethod
  trackingMethodName: string
  trackingMethodImmutableReason?: string | null
  categoryId: ResourceId
  categoryName?: string | null
  unitId: ResourceId
  unitName?: string | null
  businessUnitSummary?: string | null
  baseUnitImmutableReason?: string | null
  costCategory?: CostCategory | null
  inventoryValuationCategory?: InventoryValuationCategory | null
  inventoryValueEnabled?: boolean | null
  projectCostEnabled?: boolean | null
  costAttributeCompleted?: boolean | null
  costRemark?: string | null
}

export interface UnitPayload {
  code: string
  name: string
  precisionScale: number
  sortOrder: number
  status?: MasterDataStatus
  remark?: string
}

export interface WarehousePayload {
  code: string
  name: string
  warehouseType?: string
  managerName?: string
  address?: string
  status?: MasterDataStatus
  remark?: string
}

export interface PartnerPayload {
  code: string
  name: string
  contactName?: string
  contactPhone?: string
  status?: MasterDataStatus
  remark?: string
}

export interface CategoryPayload {
  code: string
  name: string
  parentId?: ResourceId | null
  status?: MasterDataStatus
  sortOrder: number
  remark?: string
}

export interface MaterialPayload {
  code: string
  name: string
  specification?: string
  materialType: MaterialType
  sourceType: MaterialSourceType
  trackingMethod: MaterialTrackingMethod
  categoryId: ResourceId
  unitId: ResourceId
  costCategory?: CostCategory
  inventoryValuationCategory?: InventoryValuationCategory
  inventoryValueEnabled?: boolean
  projectCostEnabled?: boolean
  costRemark?: string | null
  status?: MasterDataStatus
  remark?: string
  version?: number
}

export interface UnitConversionListQuery extends MasterDataListQuery {
  materialId?: ResourceId
  businessUnitId?: ResourceId
  effectiveDate?: string
}

export interface UnitConversionPayload {
  materialId: ResourceId
  businessUnitId: ResourceId
  conversionRate: string
  quantityScale: number
  roundingMode: RoundingMode
  effectiveFrom?: string | null
  effectiveTo?: string | null
  remark?: string | null
  version?: number
}

export interface UnitConversionRecord {
  id: ResourceId
  materialId: ResourceId
  materialCode: string
  materialName: string
  baseUnitId: ResourceId
  baseUnitName: string
  businessUnitId: ResourceId
  businessUnitName: string
  conversionRate: string
  quantityScale: number
  roundingMode: RoundingMode
  effectiveFrom?: string | null
  effectiveTo?: string | null
  status: MasterDataStatus
  lockedReason?: string | null
  remark?: string | null
  createdAt?: string
  updatedAt?: string
  version: number
}

export interface UnitConversionPreviewRequest {
  materialId: ResourceId
  businessUnitId: ResourceId
  businessQuantity: string
  businessDate?: string
}

export interface UnitConversionPreviewResult {
  conversionId: ResourceId
  materialId: ResourceId
  businessUnitId: ResourceId
  businessQuantity: string
  baseUnitId: ResourceId
  baseQuantity: string
  conversionRateSnapshot: string
  quantityScaleSnapshot: number
  roundingModeSnapshot: RoundingMode
}

export interface CodingRuleListQuery extends MasterDataListQuery {
  objectType?: CodingObjectType
}

export interface CodingRulePayload {
  ruleCode: string
  name: string
  objectType: CodingObjectType
  prefix: string
  datePattern: CodingDatePattern
  serialLength: number
  resetCycle: CodingResetCycle
  nextSerialNo: number
  status: MasterDataStatus
  remark?: string | null
  version?: number
}

export interface CodingRuleRecord extends CodingRulePayload {
  id: ResourceId
  lastGeneratedCode?: string | null
  lastGeneratedAt?: string | null
  createdAt?: string
  updatedAt?: string
  version: number
}

export interface CodingRuleGenerateRequest {
  objectType: CodingObjectType
  contextDate?: string
}

export interface CodingRuleGenerateResult {
  objectType: CodingObjectType
  ruleId: ResourceId
  generatedCode: string
  generatedAt: string
}

export interface SettlementTaxSummary {
  hasData: boolean
  sensitiveRestricted?: boolean
  taxNoMasked?: string | null
  bankAccountMasked?: string | null
  defaultTaxRate?: string | null
  invoiceType?: InvoiceType | null
  settlementMethod?: SettlementMethod | null
  paymentTermDays?: number | null
}

export interface SettlementTaxRecord {
  ownerType: 'CUSTOMER' | 'SUPPLIER'
  ownerId: ResourceId
  hasData: boolean
  sensitiveRestricted: boolean
  restrictedMessage?: string | null
  invoiceTitle?: string | null
  taxNo?: string | null
  taxNoMasked?: string | null
  registeredAddress?: string | null
  registeredPhone?: string | null
  bankName?: string | null
  bankAccount?: string | null
  bankAccountMasked?: string | null
  defaultTaxRate?: string | null
  invoiceType?: InvoiceType | null
  settlementMethod?: SettlementMethod | null
  paymentTermDays?: number | null
  paymentTerms?: string | null
  remark?: string | null
  createdAt?: string
  updatedAt?: string
  version: number
}

export interface SettlementTaxPayload {
  invoiceTitle?: string | null
  taxNo?: string | null
  registeredAddress?: string | null
  registeredPhone?: string | null
  bankName?: string | null
  bankAccount?: string | null
  defaultTaxRate?: string | null
  invoiceType?: InvoiceType | null
  settlementMethod?: SettlementMethod | null
  paymentTermDays?: number | null
  paymentTerms?: string | null
  remark?: string | null
  version: number
}

export interface MasterDataResource<TRecord, TPayload, TQuery extends MasterDataListQuery = MasterDataListQuery> {
  list(query: TQuery): Promise<PageResult<TRecord>>
  get(id: ResourceId): Promise<TRecord>
  create(payload: TPayload): Promise<TRecord>
  update(id: ResourceId, payload: TPayload): Promise<TRecord>
  enable(id: ResourceId, payload?: VersionPayload): Promise<TRecord>
  disable(id: ResourceId, payload?: VersionPayload): Promise<TRecord>
}

export interface PartnerResource extends MasterDataResource<PartnerRecord, PartnerPayload> {
  getSettlementTax(id: ResourceId): Promise<SettlementTaxRecord>
  updateSettlementTax(id: ResourceId, payload: SettlementTaxPayload): Promise<SettlementTaxRecord>
}

export interface UnitConversionResource
  extends MasterDataResource<UnitConversionRecord, UnitConversionPayload, UnitConversionListQuery> {
  convert(payload: UnitConversionPreviewRequest): Promise<UnitConversionPreviewResult>
  materialCandidates(query: CandidateListQuery): Promise<CandidatePageResult>
  unitCandidates(query: CandidateListQuery): Promise<CandidatePageResult>
}

export interface CodingRuleResource
  extends MasterDataResource<CodingRuleRecord, CodingRulePayload, CodingRuleListQuery> {
  generate(payload: CodingRuleGenerateRequest): Promise<CodingRuleGenerateResult>
}

export interface MasterDataApi {
  units: MasterDataResource<UnitRecord, UnitPayload>
  unitConversions: UnitConversionResource
  codingRules: CodingRuleResource
  warehouses: MasterDataResource<WarehouseRecord, WarehousePayload>
  suppliers: PartnerResource
  customers: PartnerResource
  categories: MasterDataResource<CategoryRecord, CategoryPayload, MaterialCategoryListQuery>
  materials: MasterDataResource<MaterialRecord, MaterialPayload, MaterialListQuery>
}

export interface MasterDataApiOptions {
  baseUrl?: string
  fetcher?: Fetcher
}

export function createMasterDataApi(options: MasterDataApiOptions = {}): MasterDataApi {
  const fetcher = options.fetcher ?? ((input: string, init: RequestInit) => fetch(input, init))
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '')
  const defaultQueryKeys = ['keyword', 'status', 'page', 'pageSize'] as const
  const materialQueryKeys = [
    'keyword',
    'status',
    'page',
    'pageSize',
    'materialType',
    'sourceType',
    'trackingMethod',
    'categoryId',
  ] as const
  const unitConversionQueryKeys = [
    'keyword',
    'status',
    'materialId',
    'businessUnitId',
    'effectiveDate',
    'page',
    'pageSize',
  ] as const
  const codingRuleQueryKeys = ['keyword', 'objectType', 'status', 'page', 'pageSize'] as const
  const candidateQueryKeys = ['keyword', 'page', 'pageSize', 'selectedIds'] as const

  const pickQuery = (query: object | undefined, keys: readonly string[]) => {
    const result: Record<string, unknown> = {}
    keys.forEach((key) => {
      const value = (query as Record<string, unknown> | undefined)?.[key]
      if (value !== undefined) {
          result[key] = Array.isArray(value) ? value.join(',') : value
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

  const createResource = <TRecord, TPayload, TQuery extends MasterDataListQuery = MasterDataListQuery>(
    path: string,
    queryKeys: readonly string[] = defaultQueryKeys,
  ): MasterDataResource<TRecord, TPayload, TQuery> => ({
    list: (query) => get<PageResult<TRecord>>(path, pickQuery(query, queryKeys)),
    get: (id) => get<TRecord>(`${path}/${encodeURIComponent(String(id))}`),
    create: (payload) => write<TRecord>('POST', path, payload as object),
    update: (id, payload) => write<TRecord>('PUT', `${path}/${encodeURIComponent(String(id))}`, payload as object),
    enable: (id, payload) => write<TRecord>('PUT', `${path}/${encodeURIComponent(String(id))}/enable`, payload),
    disable: (id, payload) => write<TRecord>('PUT', `${path}/${encodeURIComponent(String(id))}/disable`, payload),
  })

  const createPartnerResource = (path: string): PartnerResource => ({
    ...createResource<PartnerRecord, PartnerPayload>(path),
    getSettlementTax: (id) => get<SettlementTaxRecord>(`${path}/${encodeURIComponent(String(id))}/settlement-tax`),
    updateSettlementTax: (id, payload) =>
      write<SettlementTaxRecord>('PUT', `${path}/${encodeURIComponent(String(id))}/settlement-tax`, payload),
  })

  const unitConversions = {
    ...createResource<UnitConversionRecord, UnitConversionPayload, UnitConversionListQuery>(
      '/api/admin/master/unit-conversions',
      unitConversionQueryKeys,
    ),
    convert: (payload: UnitConversionPreviewRequest) =>
      write<UnitConversionPreviewResult>('POST', '/api/admin/master/unit-conversions/convert', payload),
    materialCandidates: (query: CandidateListQuery) =>
      get<CandidatePageResult>('/api/admin/master/unit-conversions/material-candidates', pickQuery(query, candidateQueryKeys)),
    unitCandidates: (query: CandidateListQuery) =>
      get<CandidatePageResult>('/api/admin/master/unit-conversions/unit-candidates', pickQuery(query, candidateQueryKeys)),
  }

  const codingRules = {
    ...createResource<CodingRuleRecord, CodingRulePayload, CodingRuleListQuery>(
      '/api/admin/coding-rules',
      codingRuleQueryKeys,
    ),
    generate: (payload: CodingRuleGenerateRequest) =>
      write<CodingRuleGenerateResult>('POST', '/api/admin/coding-rules/generate', payload),
  }

  return {
    units: createResource<UnitRecord, UnitPayload>('/api/admin/master/units'),
    unitConversions,
    codingRules,
    warehouses: createResource<WarehouseRecord, WarehousePayload>('/api/admin/master/warehouses'),
    suppliers: createPartnerResource('/api/admin/master/suppliers'),
    customers: createPartnerResource('/api/admin/master/customers'),
    categories: createResource<CategoryRecord, CategoryPayload, MaterialCategoryListQuery>(
      '/api/admin/master/material-categories',
    ),
    materials: createResource<MaterialRecord, MaterialPayload, MaterialListQuery>(
      '/api/admin/master/materials',
      materialQueryKeys,
    ),
  }
}

export const masterDataApi = createMasterDataApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
})
