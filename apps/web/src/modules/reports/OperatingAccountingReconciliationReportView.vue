<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  businessReportingApi,
  type OperatingAccountingReconciliationReportRow,
  type OperatingAccountingReconciliationReportSummary,
  type ReportTraceRecord,
} from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'
import {
  canOpenTrace,
  displayValue,
  filterText,
  operatingFinanceBaseFields,
  optionalFilterText,
  reconciliationStatusText,
  reportErrorMessage,
  statusText,
  traceUnavailableText,
} from './operatingFinanceReportHelpers'

const filters = reactive<Record<string, string | number>>({ periodCode: '', analysisMode: 'LIVE', projectId: '', finalityStatus: '' })
const fields: ReportFilterField[] = [
  ...operatingFinanceBaseFields,
  { key: 'finalityStatus', label: '定稿状态', name: 'report-finality-status' },
]
const loading = ref(false)
const error = ref('')
const unavailable = ref('')
const rows = ref<OperatingAccountingReconciliationReportRow[]>([])
const summary = ref<OperatingAccountingReconciliationReportSummary | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const metrics = computed(() => summary.value ? [
  { label: '管理口径利润', value: displayValue(summary.value.operatingProfitAmount) },
  { label: '会计项目利润', value: displayValue(summary.value.accountingProfitAmount) },
  { label: '公共/无法归集', value: displayValue(summary.value.publicUnallocatedAmount) },
  { label: '差异金额', value: displayValue(summary.value.differenceAmount) },
  { label: '来源数量', value: summary.value.sourceCount },
] : [])
function updateFilters(value: Record<string, string | number | undefined>) { Object.assign(filters, value) }
async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  unavailable.value = ''
  try {
    const result = await businessReportingApi.operatingAccountingReconciliation.list({
      periodCode: filterText(filters.periodCode),
      analysisMode: filterText(filters.analysisMode),
      projectId: filters.projectId || undefined,
      finalityStatus: optionalFilterText(filters.finalityStatus),
      page: targetPage,
      pageSize: pageSize.value,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    const message = reportErrorMessage(cause, '经营/会计对照报表加载失败')
    if (filters.analysisMode === 'BUSINESS_SNAPSHOT') {
      unavailable.value = message
    } else {
      error.value = message
    }
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}
function search() { void loadReport(1) }
function reset() { Object.assign(filters, { periodCode: '', analysisMode: 'LIVE', projectId: '', finalityStatus: '' }); void loadReport(1) }
function changePageSize(size: number) { pageSize.value = size; void loadReport(1) }
async function openTrace(row: OperatingAccountingReconciliationReportRow) {
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
    const result = await businessReportingApi.operatingAccountingReconciliation.traces.list({
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
      <h1>经营/会计对照</h1>
      <p>并列展示 029 管理口径与 031 已记账项目辅助会计口径，差异不自动合并为单一利润。</p>
    </header>
    <ReportFilterBar :model-value="filters" :fields="fields" :loading="loading" @update:model-value="updateFilters" @search="search" @reset="reset" />
    <el-alert v-if="unavailable" :title="unavailable" description="快照口径不可用，经营/会计对照不进入业务月结快照。" type="warning" show-icon :closable="false" />
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false" />
    <ReportMetricStrip :metrics="metrics" />
    <div data-test="report-table-scroll" class="report-table-scroll">
      <el-table :data="rows" empty-text="暂无经营/会计对照数据" stripe>
        <el-table-column prop="projectNo" label="项目" min-width="130" fixed />
        <el-table-column prop="projectName" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="管理收入" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.operatingRevenueAmount) }}</span></template></el-table-column>
        <el-table-column label="管理成本" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.operatingCostAmount) }}</span></template></el-table-column>
        <el-table-column label="管理利润" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.operatingProfitAmount) }}</span></template></el-table-column>
        <el-table-column label="会计收入" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.accountingRevenueAmount) }}</span></template></el-table-column>
        <el-table-column label="会计成本" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.accountingCostAmount) }}</span></template></el-table-column>
        <el-table-column label="会计利润" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.accountingProfitAmount) }}</span></template></el-table-column>
        <el-table-column label="公共/无法归集" min-width="140" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.publicUnallocatedAmount) }}</span></template></el-table-column>
        <el-table-column label="差异" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.differenceAmount) }}</span></template></el-table-column>
        <el-table-column label="对账状态" min-width="120"><template #default="{ row }">{{ reconciliationStatusText(row.reconciliationStatus) }}</template></el-table-column>
        <el-table-column label="定稿" min-width="100"><template #default="{ row }">{{ statusText(row.finalityStatus) }}</template></el-table-column>
        <el-table-column prop="varianceReason" label="差异原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="来源" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canOpenTrace(row.traceKey, row.restrictedReason)" data-test="open-report-trace" link type="primary" @click="openTrace(row)">追溯</el-button>
            <span v-else class="trace-unavailable">{{ traceUnavailableText(row.restrictedReason) }}</span>
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
.report-page__header p { color: var(--qherp-steel); margin: 0; }
.report-table-scroll { min-width: 0; overflow-x: auto; }
.numeric-cell { font-variant-numeric: tabular-nums; }
.trace-unavailable { color: var(--qherp-steel); }
</style>
