import type { ReportFilterField } from './ReportFilterBar.vue'
import {
  masterDataApi,
  type MaterialRecord,
  type PartnerRecord,
  type WarehouseRecord,
} from '../../shared/api/masterDataApi'
import {
  salesProjectApi,
  type SalesOrderProjectContractCandidate,
  type SalesProjectContractDetail,
  type SalesProjectSummary,
} from '../../shared/api/salesProjectApi'
import type { BusinessReferenceId, BusinessReferenceOption } from '../system/shared/businessReferenceSelectTypes'
import { reportDictionaryText, reportStatusText } from './reportPageHelpers'

export const operatingFinanceBaseFields: ReportFilterField[] = [
  { key: 'periodCode', label: '期间', name: 'report-period-code', placeholder: '例如 2026-07' },
  { key: 'analysisMode', label: '口径模式', name: 'report-analysis-mode', placeholder: '实时经营口径或业务月结快照' },
  {
    key: 'projectId',
    label: '项目',
    name: 'report-project-id',
    placeholder: '搜索项目编号、名称或客户',
    type: 'reference',
    loadOptions: loadProjectOptions,
  },
]

export const contractReferenceField: ReportFilterField = {
  key: 'contractId',
  label: '合同',
  name: 'report-contract-id',
  placeholder: '搜索合同编号、名称或项目',
  type: 'reference',
  loadOptions: loadContractOptions,
}

export const customerKeywordReferenceField: ReportFilterField = {
  key: 'customerKeyword',
  label: '客户',
  name: 'report-customer-keyword',
  placeholder: '搜索客户编码或名称',
  type: 'reference',
  loadOptions: loadCustomerKeywordOptions,
}

export const supplierKeywordReferenceField: ReportFilterField = {
  key: 'supplierKeyword',
  label: '供应商',
  name: 'report-supplier-keyword',
  placeholder: '搜索供应商编码或名称',
  type: 'reference',
  loadOptions: loadSupplierKeywordOptions,
}

export const warehouseKeywordReferenceField: ReportFilterField = {
  key: 'warehouseKeyword',
  label: '仓库',
  name: 'report-warehouse-keyword',
  placeholder: '搜索仓库编码或名称',
  type: 'reference',
  loadOptions: loadWarehouseKeywordOptions,
}

export const materialKeywordReferenceField: ReportFilterField = {
  key: 'materialKeyword',
  label: '物料',
  name: 'report-material-keyword',
  placeholder: '搜索物料编码或名称',
  type: 'reference',
  loadOptions: loadMaterialKeywordOptions,
}

function compactLabel(parts: Array<string | number | null | undefined>) {
  return parts.filter((part) => part !== null && part !== undefined && String(part).trim() !== '').join(' ')
}

function uniqueOptions(options: BusinessReferenceOption[]) {
  const seen = new Set<string>()
  return options.filter((option) => {
    const key = String(option.id)
    if (key === '' || seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

function hasSelected(options: BusinessReferenceOption[], selectedValue: BusinessReferenceId | '') {
  return selectedValue !== '' && options.some((option) => String(option.id) === String(selectedValue))
}

function withKeywordSelected(options: BusinessReferenceOption[], selectedValue: BusinessReferenceId | '') {
  if (selectedValue === '' || hasSelected(options, selectedValue)) {
    return options
  }
  return [{ id: selectedValue, label: String(selectedValue) }, ...options]
}

function pageItems<T>(page: { items?: T[]; records?: T[]; content?: T[] }) {
  return page.items ?? page.records ?? page.content ?? []
}

function projectOption(project: SalesProjectSummary): BusinessReferenceOption {
  return {
    id: project.id,
    label: compactLabel([project.projectNo, project.name, project.customerName]),
  }
}

function contractCandidateOption(candidate: SalesOrderProjectContractCandidate): BusinessReferenceOption {
  return {
    id: candidate.contractId,
    label: compactLabel([candidate.contractNo, candidate.contractName, '/', candidate.projectNo, candidate.projectName]),
  }
}

function contractDetailOption(contract: SalesProjectContractDetail): BusinessReferenceOption {
  return {
    id: contract.id,
    label: compactLabel([contract.contractNo, contract.name, '/', contract.projectNo, contract.projectName]),
  }
}

function partnerKeywordOption(partner: PartnerRecord): BusinessReferenceOption {
  return {
    id: partner.name,
    label: compactLabel([partner.code, partner.name]),
  }
}

function warehouseKeywordOption(warehouse: WarehouseRecord): BusinessReferenceOption {
  return {
    id: warehouse.name,
    label: compactLabel([warehouse.code, warehouse.name]),
  }
}

function materialKeywordOption(material: MaterialRecord): BusinessReferenceOption {
  return {
    id: material.name,
    label: compactLabel([material.code, material.name, material.specification]),
  }
}

export async function loadProjectOptions(keyword: string, selectedValue: BusinessReferenceId | '' = '') {
  const page = await salesProjectApi.projects.list({ keyword, page: 1, pageSize: 20 })
  const options = pageItems(page).map(projectOption)
  if (!hasSelected(options, selectedValue) && selectedValue !== '') {
    try {
      options.unshift(projectOption(await salesProjectApi.projects.get(selectedValue)))
    } catch {
      options.unshift({ id: selectedValue, label: String(selectedValue) })
    }
  }
  return uniqueOptions(options)
}

export async function loadContractOptions(keyword: string, selectedValue: BusinessReferenceId | '' = '') {
  const page = await salesProjectApi.listOrderLinkCandidates({ keyword, page: 1, pageSize: 20 })
  const options = pageItems(page).map(contractCandidateOption)
  if (!hasSelected(options, selectedValue) && selectedValue !== '') {
    try {
      options.unshift(contractDetailOption(await salesProjectApi.contracts.get(selectedValue)))
    } catch {
      options.unshift({ id: selectedValue, label: String(selectedValue) })
    }
  }
  return uniqueOptions(options)
}

export async function loadCustomerKeywordOptions(keyword: string, selectedValue: BusinessReferenceId | '' = '') {
  const page = await masterDataApi.customers.list({ keyword, status: 'ENABLED', page: 1, pageSize: 20 })
  return uniqueOptions(withKeywordSelected(pageItems(page).map(partnerKeywordOption), selectedValue))
}

export async function loadSupplierKeywordOptions(keyword: string, selectedValue: BusinessReferenceId | '' = '') {
  const page = await masterDataApi.suppliers.list({ keyword, status: 'ENABLED', page: 1, pageSize: 20 })
  return uniqueOptions(withKeywordSelected(pageItems(page).map(partnerKeywordOption), selectedValue))
}

export async function loadWarehouseKeywordOptions(keyword: string, selectedValue: BusinessReferenceId | '' = '') {
  const page = await masterDataApi.warehouses.list({ keyword, status: 'ENABLED', page: 1, pageSize: 20 })
  return uniqueOptions(withKeywordSelected(pageItems(page).map(warehouseKeywordOption), selectedValue))
}

export async function loadMaterialKeywordOptions(keyword: string, selectedValue: BusinessReferenceId | '' = '') {
  const page = await masterDataApi.materials.list({ keyword, status: 'ENABLED', page: 1, pageSize: 20 })
  return uniqueOptions(withKeywordSelected(pageItems(page).map(materialKeywordOption), selectedValue))
}

export function displayValue(value: string | number | null | undefined) {
  return value === null || value === undefined || value === '' ? '受限/不可用' : value
}

export function hasTraceKey(traceKey: string | null | undefined): traceKey is string {
  return typeof traceKey === 'string' && traceKey.trim() !== ''
}

export function traceUnavailableText(_restrictedReason?: string | null) {
  return '来源受限/不可用'
}

export function canOpenTrace(traceKey: string | null | undefined, restrictedReason?: string | null): traceKey is string {
  return hasTraceKey(traceKey) && !restrictedReason?.trim()
}

export function statusText(status: string | null | undefined) {
  return reportStatusText(status)
}

export function inventoryQualityStatusText(status: string | null | undefined) {
  if (status === 'REJECTED') {
    return '不合格'
  }
  return statusText(status)
}

export function reconciliationStatusText(status: string | null | undefined) {
  if (status === 'UNAVAILABLE') {
    return '无会计事实'
  }
  return statusText(status)
}

export function valuationCompletenessText(status: string | null | undefined) {
  if (status === 'COMPLETE') {
    return '估值完整'
  }
  if (status === 'INCOMPLETE') {
    return '估值不完整'
  }
  return statusText(status)
}

export function projectProfitRevenueBasisText(basis: string | null | undefined) {
  const labels: Record<string, string> = {
    SHIPMENT: '发货经营收入',
    INVOICE: '开票收入',
    TARGET: '目标收入',
  }
  return reportDictionaryText(labels, basis, '未知口径')
}

export function projectProfitCostStageText(stage: string | null | undefined) {
  const labels: Record<string, string> = {
    WIP: '在制',
    FINISHED: '完工',
    DELIVERED: '已交付',
    DIRECT_PROJECT: '直接项目',
    TOTAL: '合计',
  }
  return reportDictionaryText(labels, stage, '未知阶段')
}

export function projectProfitVarianceReasonText(reasonCode: string | null | undefined) {
  const labels: Record<string, string> = {
    MATCHED: '已匹配',
    DIFFERENT: '存在差异',
    UNAVAILABLE: '不可用',
    RESTRICTED: '受限',
    NO_ACCOUNTING_FACT: '无会计事实',
  }
  return reportDictionaryText(labels, reasonCode, '未知原因')
}

export function snapshotUnsupportedMessage(title: string) {
  return `${title}不进入业务月结快照`
}

export function firstKeyword(...values: Array<string | number | null | undefined>) {
  const value = values.find((item) => item !== null && item !== undefined && String(item).trim() !== '')
  return value === null || value === undefined ? '' : String(value).trim()
}

export function filterText(value: string | number | null | undefined) {
  return value === null || value === undefined ? '' : String(value)
}

export function optionalFilterText(value: string | number | null | undefined) {
  const text = filterText(value).trim()
  return text === '' ? undefined : text
}

export function reportErrorMessage(cause: unknown, fallback: string) {
  const code = typeof cause === 'object' && cause !== null && 'code' in cause ? String((cause as { code?: unknown }).code) : ''
  if (code === 'REPORT_SNAPSHOT_NOT_INCLUDED') {
    return '旧快照未包含'
  }
  if (cause instanceof Error && cause.message) {
    return cause.message
  }
  if (code === 'REPORT_BASIS_INVALID') {
    return '快照口径不可用'
  }
  if (code === 'AUTH_FORBIDDEN') {
    return '无访问权限'
  }
  return fallback
}
