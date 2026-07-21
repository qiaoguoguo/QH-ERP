<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  businessReportingApi,
  type ContractCollectionReportRow,
  type ContractCollectionReportSummary,
  type ReportTraceRecord,
} from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'
import {
  contractReferenceField,
  customerKeywordReferenceField,
  canOpenTrace,
  displayValue,
  filterText,
  firstKeyword,
  operatingFinanceBaseFields,
  reportErrorMessage,
  statusText,
  traceUnavailableText,
} from './operatingFinanceReportHelpers'

const filters = reactive<Record<string, string | number>>({
  periodCode: '',
  analysisMode: 'LIVE',
  projectId: '',
  contractId: '',
  customerKeyword: '',
  keyword: '',
})
const fields: ReportFilterField[] = [
  ...operatingFinanceBaseFields,
  contractReferenceField,
  customerKeywordReferenceField,
  { key: 'keyword', label: '关键字', name: 'report-keyword', placeholder: '合同、客户或项目' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<ContractCollectionReportRow[]>([])
const summary = ref<ContractCollectionReportSummary | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const metrics = computed(() => summary.value ? [
  { label: '合同金额', value: displayValue(summary.value.contractAmount) },
  { label: '已开票', value: displayValue(summary.value.invoiceAmount) },
  { label: '已收款', value: displayValue(summary.value.receivedAmount) },
  { label: '未收金额', value: displayValue(summary.value.unreceivedAmount) },
  { label: '预收', value: displayValue(summary.value.advanceReceiptAmount) },
  { label: '逾期金额', value: displayValue(summary.value.overdueAmount) },
  { label: '来源数量', value: summary.value.sourceCount },
] : [])
function updateFilters(value: Record<string, string | number | undefined>) { Object.assign(filters, value) }
async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.contractCollection.list({
      periodCode: filterText(filters.periodCode),
      analysisMode: filterText(filters.analysisMode),
      projectId: filters.projectId || undefined,
      contractId: filters.contractId || undefined,
      keyword: firstKeyword(filters.customerKeyword, filters.keyword),
      page: targetPage,
      pageSize: pageSize.value,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = reportErrorMessage(cause, '合同回款报表加载失败')
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}
function search() { void loadReport(1) }
function reset() {
  Object.assign(filters, { periodCode: '', analysisMode: 'LIVE', projectId: '', contractId: '', customerKeyword: '', keyword: '' })
  void loadReport(1)
}
function changePageSize(size: number) { pageSize.value = size; void loadReport(1) }
async function openTrace(row: ContractCollectionReportRow) {
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
    const result = await businessReportingApi.contractCollection.traces.list({
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
      <h1>合同回款</h1>
      <p>固定查看合同金额、开票、收款、核销、未收和逾期状态，回款不等同收入确认。</p>
    </header>
    <ReportFilterBar :model-value="filters" :fields="fields" :loading="loading" @update:model-value="updateFilters" @search="search" @reset="reset" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <ReportMetricStrip :metrics="metrics" />
    <div data-test="report-table-scroll" class="report-table-scroll">
      <el-table :data="rows" empty-text="暂无合同回款数据" stripe>
        <el-table-column prop="contractNo" label="合同" min-width="150" fixed show-overflow-tooltip />
        <el-table-column prop="projectNo" label="项目" min-width="120" />
        <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column label="合同金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.contractAmount) }}</span></template></el-table-column>
        <el-table-column label="开票金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.invoiceAmount) }}</span></template></el-table-column>
        <el-table-column label="收款金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.receivedAmount) }}</span></template></el-table-column>
        <el-table-column label="核销金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.allocatedAmount) }}</span></template></el-table-column>
        <el-table-column label="未收金额" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.unreceivedAmount) }}</span></template></el-table-column>
        <el-table-column label="预收" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.advanceReceiptAmount) }}</span></template></el-table-column>
        <el-table-column label="逾期" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.overdueAmount) }}</span></template></el-table-column>
        <el-table-column prop="collectionRate" label="回款率" min-width="100" align="right" />
        <el-table-column label="状态" min-width="100"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column>
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
