<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  businessReportingApi,
  type ResourceId,
  type ProjectProfitReportRow,
  type ProjectProfitReportSummary,
  type ReportTraceRecord,
} from '../../shared/api/businessReportingApi'
import { queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'
import {
  displayValue,
  filterText,
  canOpenTrace,
  operatingFinanceBaseFields,
  optionalFilterText,
  reconciliationStatusText,
  reportErrorMessage,
  statusText,
  traceUnavailableText,
} from './operatingFinanceReportHelpers'

const route = useRoute()
function queryText(key: string) {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

const filters = reactive<Record<string, string | number>>({
  periodCode: queryText('periodCode'),
  analysisMode: queryText('analysisMode') || 'LIVE',
  projectId: queryText('projectId'),
  completenessStatus: queryText('completenessStatus'),
  reconciliationStatus: queryText('reconciliationStatus'),
})
const fields: ReportFilterField[] = [
  ...operatingFinanceBaseFields,
  { key: 'completenessStatus', label: '完整性', name: 'report-completeness-status' },
  { key: 'reconciliationStatus', label: '对账状态', name: 'report-reconciliation-status' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<ProjectProfitReportRow[]>([])
const summary = ref<ProjectProfitReportSummary | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')

const metrics = computed(() => summary.value ? [
  { label: '项目数量', value: summary.value.projectCount ?? 0 },
  { label: '发货经营收入', value: displayValue(summary.value.shipmentRevenueAmount) },
  { label: '项目成本', value: displayValue(summary.value.projectCostAmount) },
  { label: '经营毛利', value: displayValue(summary.value.operatingGrossProfitAmount) },
  { label: '会计利润', value: displayValue(summary.value.accountingProfitAmount) },
  { label: '差异金额', value: displayValue(summary.value.differenceAmount) },
  { label: '来源数量', value: summary.value.sourceCount },
] : [])

function updateFilters(value: Record<string, string | number | undefined>) {
  Object.assign(filters, value)
}

async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.projectProfit.list({
      periodCode: filterText(filters.periodCode),
      analysisMode: filterText(filters.analysisMode),
      projectId: filters.projectId || undefined,
      completenessStatus: optionalFilterText(filters.completenessStatus),
      reconciliationStatus: optionalFilterText(filters.reconciliationStatus),
      page: targetPage,
      pageSize: pageSize.value,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = reportErrorMessage(cause, '项目利润报表加载失败')
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}

function search() { void loadReport(1) }
function reset() {
  Object.assign(filters, { periodCode: '', analysisMode: 'LIVE', projectId: '', completenessStatus: '', reconciliationStatus: '' })
  void loadReport(1)
}
function changePageSize(size: number) {
  pageSize.value = size
  void loadReport(1)
}
function listQuery() {
  const query: Record<string, string | number> = {}
  ;(['periodCode', 'analysisMode', 'projectId', 'completenessStatus', 'reconciliationStatus'] as const).forEach((key) => {
    const value = filters[key]
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query[key] = value
    }
  })
  return query
}
function listReturnTo() {
  const search = new URLSearchParams()
  Object.entries(listQuery()).forEach(([key, value]) => {
    search.set(key, String(value))
  })
  const queryString = search.toString()
  return `/reports/project-profit${queryString ? `?${queryString}` : ''}`
}
function detailLocation(row: ProjectProfitReportRow) {
  return {
    name: 'reports-project-profit-detail',
    params: { projectId: row.projectId as ResourceId },
    query: queryWithReturnTo(listQuery(), listReturnTo()),
  }
}
async function openTrace(row: ProjectProfitReportRow) {
  const traceKey = row.traceKey
  if (!canOpenTrace(traceKey, row.restrictedReason)) {
    traceRows.value = []
    traceError.value = traceUnavailableText(row.restrictedReason)
    return
  }
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    const result = await businessReportingApi.projectProfit.traces.list({
      projectId: row.projectId,
      traceKey,
      periodCode: filterText(filters.periodCode),
      analysisMode: filterText(filters.analysisMode),
      page: 1,
      pageSize: pageSize.value,
    })
    traceRows.value = result.items
  } catch (cause) {
    traceError.value = cause instanceof Error ? cause.message : '来源追溯加载失败'
    traceRows.value = []
  } finally {
    traceLoading.value = false
  }
}
onMounted(() => { void loadReport(1) })
</script>

<template>
  <section class="report-page">
    <header class="report-page__header">
      <h1>项目利润</h1>
      <p>按 029 经营项目成本和收入口径查看项目利润，并并列显示会计项目口径与差异状态。</p>
    </header>
    <ReportFilterBar :model-value="filters" :fields="fields" :loading="loading" @update:model-value="updateFilters" @search="search" @reset="reset" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <ReportMetricStrip :metrics="metrics" />
    <div data-test="report-table-scroll" class="report-table-scroll">
      <el-table :data="rows" empty-text="暂无项目利润数据" stripe>
        <el-table-column label="项目" min-width="170" fixed>
          <template #default="{ row }">
            <router-link data-test="report-project-detail" :to="detailLocation(row)">{{ row.projectNo }}</router-link>
            <div class="report-subtext">{{ row.projectName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column label="发货经营收入" min-width="130" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.shipmentRevenueAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="开票收入" min-width="120" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.invoiceRevenueAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="目标收入" min-width="120" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.targetRevenueAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="项目成本" min-width="120" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.projectCostAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="经营毛利" min-width="120" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.operatingGrossProfitAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="毛利率" min-width="110" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.operatingGrossProfitRate) }}</span></template>
        </el-table-column>
        <el-table-column label="会计利润" min-width="120" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.accountingProfitAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="差异" min-width="110" align="right">
          <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.differenceAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="完整性" min-width="100">
          <template #default="{ row }">{{ statusText(row.completenessStatus) }}</template>
        </el-table-column>
        <el-table-column label="当前性" min-width="110">
          <template #default="{ row }">{{ statusText(row.freshnessStatus) }}</template>
        </el-table-column>
        <el-table-column label="对账" min-width="110">
          <template #default="{ row }">{{ reconciliationStatusText(row.reconciliationStatus) }}</template>
        </el-table-column>
        <el-table-column label="来源" min-width="120">
          <template #default="{ row }">
            <el-button v-if="canOpenTrace(row.traceKey, row.restrictedReason)" data-test="open-report-trace" link type="primary" @click="openTrace(row)">追溯</el-button>
            <span v-else class="report-subtext">{{ traceUnavailableText(row.restrictedReason) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination :current-page="page" :page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="loadReport" @size-change="changePageSize" />
    <ReportTracePanel :visible="traceVisible" :rows="traceRows" :loading="traceLoading" :error="traceError" @close="traceVisible = false" />
  </section>
</template>

<style scoped>
.report-page__header h1 { font-size: 22px; margin: 0 0 6px; }
.report-page__header p,
.report-subtext { color: var(--qherp-steel); margin: 0; }
.report-table-scroll { min-width: 0; overflow-x: auto; }
.numeric-cell { font-variant-numeric: tabular-nums; }
</style>
