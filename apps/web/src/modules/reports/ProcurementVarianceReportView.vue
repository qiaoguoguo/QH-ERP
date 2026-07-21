<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  businessReportingApi,
  type ProcurementVarianceReportRow,
  type ProcurementVarianceReportSummary,
  type ReportTraceRecord,
} from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'
import {
  canOpenTrace,
  displayValue,
  filterText,
  firstKeyword,
  operatingFinanceBaseFields,
  optionalFilterText,
  reportErrorMessage,
  statusText,
  supplierKeywordReferenceField,
  traceUnavailableText,
} from './operatingFinanceReportHelpers'

const filters = reactive<Record<string, string | number>>({
  periodCode: '',
  analysisMode: 'LIVE',
  projectId: '',
  supplierKeyword: '',
  basis: '',
  reconciliationStatus: '',
})
const fields: ReportFilterField[] = [
  ...operatingFinanceBaseFields,
  supplierKeywordReferenceField,
  { key: 'basis', label: '采购归属', name: 'report-basis', placeholder: 'PROJECT 或 PUBLIC' },
  { key: 'reconciliationStatus', label: '差异状态', name: 'report-reconciliation-status' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<ProcurementVarianceReportRow[]>([])
const summary = ref<ProcurementVarianceReportSummary | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const metrics = computed(() => summary.value ? [
  { label: '订单净额', value: displayValue(summary.value.orderAmount) },
  { label: '收货净额', value: displayValue(summary.value.receiptAmount) },
  { label: '发票净额', value: displayValue(summary.value.invoiceAmount) },
  { label: '付款核销', value: displayValue(summary.value.paidAmount) },
  { label: '三单匹配差异', value: displayValue(summary.value.matchVarianceAmount) },
  { label: '来源数量', value: summary.value.sourceCount },
] : [])
function updateFilters(value: Record<string, string | number | undefined>) { Object.assign(filters, value) }
async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.procurementVariance.list({
      periodCode: filterText(filters.periodCode),
      analysisMode: filterText(filters.analysisMode),
      projectId: filters.projectId || undefined,
      basis: optionalFilterText(filters.basis),
      reconciliationStatus: optionalFilterText(filters.reconciliationStatus),
      keyword: firstKeyword(filters.supplierKeyword),
      page: targetPage,
      pageSize: pageSize.value,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = reportErrorMessage(cause, '采购差异报表加载失败')
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}
function search() { void loadReport(1) }
function reset() {
  Object.assign(filters, { periodCode: '', analysisMode: 'LIVE', projectId: '', supplierKeyword: '', basis: '', reconciliationStatus: '' })
  void loadReport(1)
}
function changePageSize(size: number) { pageSize.value = size; void loadReport(1) }
async function openTrace(row: ProcurementVarianceReportRow) {
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
    const result = await businessReportingApi.procurementVariance.traces.list({
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
      <h1>采购差异</h1>
      <p>固定展示订单、收货、发票、付款、三单匹配和外协差异，公共采购不猜测项目归属。</p>
    </header>
    <ReportFilterBar :model-value="filters" :fields="fields" :loading="loading" @update:model-value="updateFilters" @search="search" @reset="reset" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <ReportMetricStrip :metrics="metrics" />
    <div data-test="report-table-scroll" class="report-table-scroll">
      <el-table :data="rows" empty-text="暂无采购差异数据" stripe>
        <el-table-column prop="sourceNo" label="采购来源" min-width="150" fixed show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="140" show-overflow-tooltip />
        <el-table-column prop="projectNo" label="项目" min-width="120" />
        <el-table-column label="归属" min-width="100"><template #default="{ row }">{{ statusText(row.basis) }}</template></el-table-column>
        <el-table-column label="订单净额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.orderAmount) }}</span></template></el-table-column>
        <el-table-column label="收货净额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.receiptAmount) }}</span></template></el-table-column>
        <el-table-column label="发票净额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.invoiceAmount) }}</span></template></el-table-column>
        <el-table-column label="付款核销" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.paidAmount) }}</span></template></el-table-column>
        <el-table-column label="未收货订单" min-width="130" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.unreceivedOrderAmount) }}</span></template></el-table-column>
        <el-table-column label="已收未票" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.receivedUninvoicedAmount) }}</span></template></el-table-column>
        <el-table-column label="票货差异" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.invoiceReceiptDifferenceAmount) }}</span></template></el-table-column>
        <el-table-column label="未付金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.unpaidAmount) }}</span></template></el-table-column>
        <el-table-column label="三单匹配差异" min-width="140" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.matchVarianceAmount) }}</span></template></el-table-column>
        <el-table-column label="外协已收未结" min-width="140" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.outsourcingUnsettledAmount) }}</span></template></el-table-column>
        <el-table-column label="差异状态" min-width="110"><template #default="{ row }">{{ statusText(row.reconciliationStatus) }}</template></el-table-column>
        <el-table-column label="来源" width="100" fixed="right">
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
