<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  businessReportingApi,
  type InventoryCapitalReportRow,
  type InventoryCapitalReportSummary,
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
  inventoryQualityStatusText,
  materialKeywordReferenceField,
  operatingFinanceBaseFields,
  reportErrorMessage,
  statusText,
  traceUnavailableText,
  valuationCompletenessText,
  warehouseKeywordReferenceField,
} from './operatingFinanceReportHelpers'

const filters = reactive<Record<string, string | number>>({
  periodCode: '',
  analysisMode: 'LIVE',
  projectId: '',
  warehouseKeyword: '',
  materialKeyword: '',
  keyword: '',
})
const fields: ReportFilterField[] = [
  ...operatingFinanceBaseFields,
  warehouseKeywordReferenceField,
  materialKeywordReferenceField,
  { key: 'keyword', label: '关键字', name: 'report-keyword', placeholder: '仓库、物料或项目' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<InventoryCapitalReportRow[]>([])
const summary = ref<InventoryCapitalReportSummary | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const metrics = computed(() => summary.value ? [
  { label: '库存数量', value: displayValue(summary.value.quantity) },
  { label: '已知估值金额', value: displayValue(summary.value.knownValuationAmount ?? summary.value.amount) },
  { label: '快照金额', value: displayValue(summary.value.snapshotAmount) },
  { label: '差异金额', value: displayValue(summary.value.differenceAmount) },
  { label: '未知估值数量', value: displayValue(summary.value.unknownValuationQuantity) },
  { label: '风险数量', value: displayValue(summary.value.riskQuantity) },
  { label: '估值完整性', value: valuationCompletenessText(summary.value.completenessStatus) },
  { label: '来源数量', value: summary.value.sourceCount },
] : [])
function updateFilters(value: Record<string, string | number | undefined>) { Object.assign(filters, value) }
async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.inventoryCapital.list({
      periodCode: filterText(filters.periodCode),
      analysisMode: filterText(filters.analysisMode),
      projectId: filters.projectId || undefined,
      keyword: firstKeyword(filters.warehouseKeyword, filters.materialKeyword, filters.keyword),
      page: targetPage,
      pageSize: pageSize.value,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = reportErrorMessage(cause, '库存资金报表加载失败')
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}
function search() { void loadReport(1) }
function reset() {
  Object.assign(filters, { periodCode: '', analysisMode: 'LIVE', projectId: '', warehouseKeyword: '', materialKeyword: '', keyword: '' })
  void loadReport(1)
}
function changePageSize(size: number) { pageSize.value = size; void loadReport(1) }
async function openTrace(row: InventoryCapitalReportRow) {
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
    const result = await businessReportingApi.inventoryCapital.traces.list({
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
      <h1>库存资金</h1>
      <p>展示公共/项目库存的已知估值金额、未知估值数量和业务月结快照差异；未估值金额保持不可用，不按零汇总。</p>
    </header>
    <ReportFilterBar :model-value="filters" :fields="fields" :loading="loading" @update:model-value="updateFilters" @search="search" @reset="reset" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <ReportMetricStrip :metrics="metrics" />
    <div data-test="report-table-scroll" class="report-table-scroll">
      <el-table :data="rows" empty-text="暂无库存资金数据" stripe>
        <el-table-column label="归属" min-width="100" fixed><template #default="{ row }">{{ statusText(row.ownerType) }}</template></el-table-column>
        <el-table-column prop="projectNo" label="项目" min-width="120" />
        <el-table-column prop="warehouseName" label="仓库" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料" min-width="160" show-overflow-tooltip />
        <el-table-column label="质量" min-width="100"><template #default="{ row }">{{ inventoryQualityStatusText(row.qualityStatus) }}</template></el-table-column>
        <el-table-column label="冻结" min-width="100"><template #default="{ row }">{{ statusText(row.freezeStatus) }}</template></el-table-column>
        <el-table-column label="估值" min-width="100"><template #default="{ row }">{{ statusText(row.valuationStatus) }}</template></el-table-column>
        <el-table-column label="估值完整性" min-width="120"><template #default="{ row }">{{ valuationCompletenessText(row.completenessStatus) }}</template></el-table-column>
        <el-table-column label="数量" min-width="110" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.quantity) }}</span></template></el-table-column>
        <el-table-column label="已知估值金额" min-width="150" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.knownValuationAmount ?? row.amount) }}</span></template></el-table-column>
        <el-table-column label="快照金额" min-width="130" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.snapshotAmount) }}</span></template></el-table-column>
        <el-table-column label="差异金额" min-width="130" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.differenceAmount) }}</span></template></el-table-column>
        <el-table-column label="未知估值数量" min-width="140" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.unknownValuationQuantity) }}</span></template></el-table-column>
        <el-table-column label="风险数量" min-width="120" align="right"><template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.riskQuantity) }}</span></template></el-table-column>
        <el-table-column label="当前性" min-width="130"><template #default="{ row }">{{ statusText(row.freshnessStatus) }}</template></el-table-column>
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
