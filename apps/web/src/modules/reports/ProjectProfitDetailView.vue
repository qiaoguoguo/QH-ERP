<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  businessReportingApi,
  type ProjectProfitDetailRecord,
  type ReportTraceRecord,
} from '../../shared/api/businessReportingApi'
import { safeReturnTo } from '../../shared/navigation/navigationReturn'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'
import {
  canOpenTrace,
  displayValue,
  projectProfitCostStageText,
  projectProfitRevenueBasisText,
  projectProfitVarianceReasonText,
  reconciliationStatusText,
  reportErrorMessage,
  statusText,
  traceUnavailableText,
} from './operatingFinanceReportHelpers'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const record = ref<ProjectProfitDetailRecord | null>(null)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const projectId = computed(() => String(route.params.projectId ?? ''))
const returnPath = computed(() => safeReturnTo(route.query.returnTo) ?? '/reports/project-profit')
const canTrace = computed(() => canOpenTrace(record.value?.traceKey, record.value?.restrictedReason))
const traceUnavailable = computed(() => record.value && !canTrace.value ? traceUnavailableText(record.value.restrictedReason) : '')

const metrics = computed(() => record.value ? [
  { label: '发货经营收入', value: displayValue(record.value.shipmentRevenueAmount) },
  { label: '开票收入', value: displayValue(record.value.invoiceRevenueAmount) },
  { label: '目标收入', value: displayValue(record.value.targetRevenueAmount) },
  { label: '项目成本', value: displayValue(record.value.projectCostAmount) },
  { label: '经营毛利', value: displayValue(record.value.operatingGrossProfitAmount) },
  { label: '毛利率', value: displayValue(record.value.operatingGrossProfitRate) },
  { label: '会计利润', value: displayValue(record.value.accountingProfitAmount) },
  { label: '定稿状态', value: statusText(record.value.finalityStatus) },
] : [])

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    record.value = await businessReportingApi.projectProfit.detail.get(projectId.value, {
      periodCode: typeof route.query.periodCode === 'string' ? route.query.periodCode : undefined,
      analysisMode: typeof route.query.analysisMode === 'string' ? route.query.analysisMode : undefined,
    })
  } catch (cause) {
    error.value = reportErrorMessage(cause, '项目利润详情加载失败')
    record.value = null
  } finally {
    loading.value = false
  }
}

function returnToList() {
  void router.push(returnPath.value)
}

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
    const result = await businessReportingApi.projectProfit.traces.list({
      projectId: projectId.value,
      traceKey,
      periodCode: typeof route.query.periodCode === 'string' ? route.query.periodCode : undefined,
      analysisMode: typeof route.query.analysisMode === 'string' ? route.query.analysisMode : undefined,
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

onMounted(() => { void loadDetail() })
</script>

<template>
  <section class="report-page">
    <header class="report-page__header">
      <div>
        <h1>项目利润详情</h1>
        <p>展示单个项目的经营收入、成本阶段、会计项目口径和差异原因，经营口径不等同正式会计利润。</p>
        <p v-if="traceUnavailable" class="trace-unavailable">{{ traceUnavailable }}</p>
      </div>
      <div class="report-page__actions">
        <el-button data-test="return-project-profit-list" @click="returnToList">返回列表</el-button>
        <el-button data-test="open-report-trace" type="primary" :disabled="!canTrace" @click="openTrace">来源追溯</el-button>
      </div>
    </header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="report-state">项目利润详情加载中</div>
    <template v-else-if="record">
      <section class="report-summary-line">
        <strong>{{ record.projectNo }}</strong>
        <span>{{ record.projectName }}</span>
        <span>{{ record.customerName ?? '-' }}</span>
        <span>{{ statusText(record.completenessStatus) }}</span>
        <span>{{ statusText(record.freshnessStatus) }}</span>
        <span>{{ reconciliationStatusText(record.reconciliationStatus) }}</span>
      </section>
      <ReportMetricStrip :metrics="metrics" />
      <section class="report-section">
        <h2>收入口径</h2>
        <div data-test="report-table-scroll" class="report-table-scroll">
          <el-table :data="record.revenueEntries" empty-text="暂无收入口径" stripe>
            <el-table-column label="口径" min-width="120">
              <template #default="{ row }">{{ projectProfitRevenueBasisText(row.basis) }}</template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="金额" min-width="140" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.amount) }}</span></template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="report-section">
        <h2>成本阶段</h2>
        <div class="report-table-scroll">
          <el-table :data="record.costStageEntries" empty-text="暂无成本阶段" stripe>
            <el-table-column label="阶段" min-width="140">
              <template #default="{ row }">{{ projectProfitCostStageText(row.stage) }}</template>
            </el-table-column>
            <el-table-column label="金额" min-width="140" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" min-width="120">
              <template #default="{ row }">{{ statusText(row.status) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="report-section">
        <h2>会计项目口径</h2>
        <el-empty v-if="record.accountingEntries.length === 0" description="无会计事实" />
        <div v-else class="report-table-scroll">
          <el-table :data="record.accountingEntries" stripe>
            <el-table-column prop="accountCode" label="科目" min-width="120" />
            <el-table-column prop="accountName" label="科目名称" min-width="180" />
            <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="金额" min-width="140" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.amount) }}</span></template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="report-section">
        <h2>差异原因</h2>
        <div class="report-table-scroll">
          <el-table :data="record.varianceReasons" empty-text="暂无差异原因" stripe>
            <el-table-column label="原因编码" min-width="160">
              <template #default="{ row }">{{ projectProfitVarianceReasonText(row.reasonCode) }}</template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
            <el-table-column label="金额" min-width="140" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ displayValue(row.amount) }}</span></template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </template>
    <el-empty v-else description="暂无项目利润详情" />
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
.report-page__actions {
  align-items: center;
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
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
.report-section h2 { font-size: 16px; margin: 0 0 8px; }
.report-table-scroll { min-width: 0; overflow-x: auto; }
.numeric-cell { font-variant-numeric: tabular-nums; }
</style>
