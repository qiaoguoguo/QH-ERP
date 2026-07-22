<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  businessReportingApi,
  type ReceivablePayableReportRow,
  type ReceivablePayableReportSummary,
  type ReportTraceRecord,
} from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'
import {
  canOpenTrace,
  customerKeywordReferenceField,
  displayValue,
  filterText,
  firstKeyword,
  operatingFinanceBaseFields,
  reportErrorMessage,
  statusText,
  supplierKeywordReferenceField,
  traceUnavailableText,
} from './operatingFinanceReportHelpers'

const filters = reactive<Record<string, string | number>>({
  periodCode: '',
  analysisMode: 'LIVE',
  projectId: '',
  customerKeyword: '',
  supplierKeyword: '',
  keyword: '',
})
const fields: ReportFilterField[] = [
  ...operatingFinanceBaseFields,
  customerKeywordReferenceField,
  supplierKeywordReferenceField,
  { key: 'keyword', label: '关键字', name: 'report-keyword', placeholder: '往来对象或项目' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<ReceivablePayableReportRow[]>([])
const summary = ref<ReceivablePayableReportSummary | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const metrics = computed(() => summary.value ? [
  { label: '应收', value: displayValue(summary.value.receivableAmount) },
  { label: '应付', value: displayValue(summary.value.payableAmount) },
  { label: '预收', value: displayValue(summary.value.advanceReceiptAmount) },
  { label: '预付', value: displayValue(summary.value.prepaymentAmount) },
  { label: '余额', value: displayValue(summary.value.balanceAmount) },
  { label: '逾期', value: displayValue(summary.value.overdueAmount) },
  { label: '来源数量', value: summary.value.sourceCount },
] : [])
function updateFilters(value: Record<string, string | number | undefined>) { Object.assign(filters, value) }
async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.receivablePayable.list({
      periodCode: filterText(filters.periodCode),
      analysisMode: filterText(filters.analysisMode),
      projectId: filters.projectId || undefined,
      keyword: firstKeyword(filters.customerKeyword, filters.supplierKeyword, filters.keyword),
      page: targetPage,
      pageSize: pageSize.value,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = reportErrorMessage(cause, '往来账龄报表加载失败')
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}
function search() { void loadReport(1) }
function reset() {
  Object.assign(filters, { periodCode: '', analysisMode: 'LIVE', projectId: '', customerKeyword: '', supplierKeyword: '', keyword: '' })
  void loadReport(1)
}
function changePageSize(size: number) { pageSize.value = size; void loadReport(1) }
async function openTrace(row: ReceivablePayableReportRow) {
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
    const result = await businessReportingApi.receivablePayable.traces.list({
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
      <h1>往来账龄</h1>
      <p>固定查看应收、应付、预收、预付、核销、余额和五档账龄，不改变 028 往来事实。</p>
    </header>
    <ReportFilterBar :model-value="filters" :fields="fields" :loading="loading" @update:model-value="updateFilters" @search="search" @reset="reset" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <ReportMetricStrip :metrics="metrics" />
    <div data-test="report-table-scroll" class="report-table-scroll">
      <el-table :data="rows" empty-text="暂无往来账龄数据" stripe>
        <el-table-column label="对象类型" min-width="100" fixed><template #default="{ row }">{{ statusText(row.partyType) }}</template></el-table-column>
        <el-table-column prop="partyName" label="往来对象" min-width="150" show-overflow-tooltip />
        <el-table-column prop="projectNo" label="项目" min-width="120" />
        <el-table-column label="应收" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.receivableAmount) }}</span></template></el-table-column>
        <el-table-column label="应付" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.payableAmount) }}</span></template></el-table-column>
        <el-table-column label="预收" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.advanceReceiptAmount) }}</span></template></el-table-column>
        <el-table-column label="预付" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.prepaymentAmount) }}</span></template></el-table-column>
        <el-table-column label="已核销" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.settledAmount) }}</span></template></el-table-column>
        <el-table-column label="余额" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.balanceAmount) }}</span></template></el-table-column>
        <el-table-column label="未到期" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.notDueAmount) }}</span></template></el-table-column>
        <el-table-column label="1-30天" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.aging1To30Amount) }}</span></template></el-table-column>
        <el-table-column label="31-60天" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.aging31To60Amount) }}</span></template></el-table-column>
        <el-table-column label="61-90天" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.aging61To90Amount) }}</span></template></el-table-column>
        <el-table-column label="90天以上" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.agingOver90Amount) }}</span></template></el-table-column>
        <el-table-column label="逾期" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.overdueAmount) }}</span></template></el-table-column>
        <el-table-column label="来源" min-width="120">
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
