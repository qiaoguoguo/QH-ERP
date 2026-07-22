<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  businessReportingApi,
  type FinancialSummaryRecord,
  type ReportTraceRecord,
} from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'
import {
  canOpenTrace,
  displayValue,
  operatingFinanceLiveAnalysisMode,
  reportErrorMessage,
  snapshotUnsupportedMessage,
  statusText,
  traceUnavailableText,
} from './operatingFinanceReportHelpers'

const filters = reactive<Record<string, string>>({ periodCode: '', analysisMode: operatingFinanceLiveAnalysisMode, finalityStatus: '' })
const fields: ReportFilterField[] = [
  { key: 'periodCode', label: '期间', name: 'report-period-code', placeholder: '例如 2026-07' },
  { key: 'analysisMode', label: '口径模式', name: 'report-analysis-mode', placeholder: '实时经营口径' },
  { key: 'finalityStatus', label: '定稿状态', name: 'report-finality-status' },
]
const loading = ref(false)
const error = ref('')
const unavailable = ref('')
const record = ref<FinancialSummaryRecord | null>(null)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const metrics = computed(() => record.value ? [
  { label: '收入', value: displayValue(record.value.revenueAmount) },
  { label: '主营成本', value: displayValue(record.value.mainCostAmount) },
  { label: '期间费用', value: displayValue(record.value.periodExpenseAmount) },
  { label: '其他损益', value: displayValue(record.value.otherProfitLossAmount) },
  { label: '所得税费用', value: displayValue(record.value.incomeTaxExpenseAmount) },
  { label: '经营结果', value: displayValue(record.value.operatingResultAmount) },
  { label: '来源数量', value: record.value.sourceCount },
] : [])
const canTrace = computed(() => canOpenTrace(record.value?.traceKey, record.value?.restrictedReason))
const traceUnavailable = computed(() => record.value && !canTrace.value ? traceUnavailableText(record.value.restrictedReason) : '')
const balanceRows = computed(() => record.value ? [
  { category: '资产类别余额', amount: record.value.assetBalanceAmount },
  { category: '负债类别余额', amount: record.value.liabilityBalanceAmount },
  { category: '权益类别余额', amount: record.value.equityBalanceAmount },
] : [])
function updateFilters(value: Record<string, string | number | undefined>) { Object.assign(filters, value) }
async function loadSummary() {
  loading.value = true
  error.value = ''
  unavailable.value = ''
  try {
    record.value = await businessReportingApi.financialSummary.get({
      periodCode: filters.periodCode,
      analysisMode: filters.analysisMode,
      finalityStatus: filters.finalityStatus || undefined,
    })
  } catch (cause) {
    const message = reportErrorMessage(cause, '固定经营财务摘要加载失败')
    if (filters.analysisMode === 'BUSINESS_SNAPSHOT') {
      unavailable.value = message || snapshotUnsupportedMessage('固定经营财务摘要')
    } else {
      error.value = message
    }
    record.value = null
  } finally {
    loading.value = false
  }
}
function reset() { Object.assign(filters, { periodCode: '', analysisMode: operatingFinanceLiveAnalysisMode, finalityStatus: '' }); void loadSummary() }
async function openTrace() {
  const traceKey = record.value?.traceKey
  if (!record.value || !canOpenTrace(traceKey, record.value.restrictedReason)) {
    traceRows.value = []
    traceError.value = traceUnavailableText(record.value?.restrictedReason)
    return
  }
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    const result = await businessReportingApi.financialSummary.traces.list({
      traceKey,
      periodCode: filters.periodCode,
      analysisMode: filters.analysisMode,
      page: 1,
      pageSize: 10,
    })
    traceRows.value = result.items
  } catch (cause) {
    traceError.value = cause instanceof Error ? cause.message : '来源追溯加载失败'
    traceRows.value = []
  } finally {
    traceLoading.value = false
  }
}
onMounted(() => { void loadSummary() })
</script>

<template>
  <section class="report-page">
    <header class="report-page__header">
      <div>
        <h1>固定经营财务摘要</h1>
        <p>管理用固定经营财务摘要，不是资产负债表、利润表或现金流量表，也不用于申报。</p>
        <p v-if="traceUnavailable" class="trace-unavailable">{{ traceUnavailable }}</p>
      </div>
      <el-button data-test="open-report-trace" type="primary" :disabled="!canTrace" @click="openTrace">来源追溯</el-button>
    </header>
    <ReportFilterBar :model-value="filters" :fields="fields" :loading="loading" @update:model-value="updateFilters" @search="loadSummary" @reset="reset" />
    <el-alert v-if="unavailable" :title="unavailable" description="快照口径不可用，固定经营财务摘要不进入业务月结快照。" type="warning" show-icon :closable="false" />
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="report-state">固定经营财务摘要加载中</div>
    <template v-else-if="record">
      <section class="report-summary-line">
        <span>期间：{{ record.periodCode }}</span>
        <span>业务月结：{{ statusText(record.businessPeriodStatus) }}</span>
        <span>会计期间：{{ statusText(record.accountingPeriodStatus) }}</span>
        <span>财务关闭：{{ statusText(record.financialCloseStatus) }}</span>
        <span>定稿：{{ statusText(record.finalityStatus) }}</span>
      </section>
      <ReportMetricStrip :metrics="metrics" />
      <div data-test="report-table-scroll" class="report-table-scroll">
        <el-table :data="balanceRows" empty-text="暂无类别余额" stripe>
          <el-table-column label="分类" min-width="180" fixed>
            <template #default="{ row }">{{ row.category }}</template>
          </el-table-column>
          <el-table-column label="金额" min-width="160" align="right">
            <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.amount) }}</span></template>
          </el-table-column>
        </el-table>
      </div>
      <section class="report-summary-line">
        <span>三组试算：{{ statusText(record.trialBalanceStatus) }}</span>
        <span>银行对账：{{ statusText(record.bankReconciliationStatus) }}</span>
        <span>税务基础：{{ statusText(record.taxSummaryStatus) }}</span>
      </section>
    </template>
    <el-empty v-else description="暂无固定经营财务摘要" />
    <ReportTracePanel :visible="traceVisible" :rows="traceRows" :loading="traceLoading" :error="traceError" @close="traceVisible = false" />
  </section>
</template>

<style scoped>
.report-page__header {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}
.report-page__header h1 { font-size: 22px; margin: 0 0 6px; }
.report-page__header p,
.trace-unavailable,
.report-state { color: var(--qherp-steel); margin: 0; }
.report-summary-line {
  border-bottom: 1px solid var(--qherp-border-soft);
  border-top: 1px solid var(--qherp-border-soft);
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 0;
}
.report-table-scroll { min-width: 0; overflow-x: auto; }
.numeric-cell { font-variant-numeric: tabular-nums; }
</style>
