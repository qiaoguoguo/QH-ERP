<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { businessReportingApi, type BusinessExceptionType, type ExceptionReportRow, type ExceptionReportSummary, type ReportTraceRecord } from '../../shared/api/businessReportingApi'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import ReportTracePanel from './ReportTracePanel.vue'
import { reportDictionaryText } from './reportPageHelpers'

const filters = reactive<Record<string, string>>({
  dateFrom: '',
  dateTo: '',
  keyword: '',
  type: '',
})
const fields: ReportFilterField[] = [
  { key: 'dateFrom', label: '开始日期', name: 'report-date-from', type: 'date' },
  { key: 'dateTo', label: '结束日期', name: 'report-date-to', type: 'date' },
  { key: 'keyword', label: '关键字', name: 'report-keyword', placeholder: '异常对象或说明' },
  { key: 'type', label: '异常类型', name: 'report-type' },
]
const loading = ref(false)
const error = ref('')
const rows = ref<ExceptionReportRow[]>([])
const summary = ref<ExceptionReportSummary | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const traceVisible = ref(false)
const traceRows = ref<ReportTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')

const metrics = computed(() => summary.value ? [
  { label: '异常总数', value: summary.value.exceptionCount },
  { label: '严重异常', value: summary.value.criticalCount },
  { label: '普通异常', value: summary.value.warningCount },
  { label: '库存不足', value: summary.value.countsByType.INVENTORY_SHORTAGE ?? 0 },
] : [])

function updateFilters(value: Record<string, string | number | undefined>) {
  Object.assign(filters, value)
}

function exceptionTypeText(type: BusinessExceptionType | string) {
  const text: Record<string, string> = {
    SALES_DELIVERY_OVERDUE: '销售交付逾期',
    PROCUREMENT_RECEIPT_OVERDUE: '采购到货逾期',
    INVENTORY_SHORTAGE: '库存不足',
    PRODUCTION_OVERDUE: '生产逾期',
    COST_MISSING: '成本未归集',
    RECEIVABLE_OVERDUE: '应收逾期',
    PAYABLE_DUE_SOON: '应付临期',
  }
  return reportDictionaryText(text, type, '未知异常类型')
}

function exceptionSeverityText(severity: string | null | undefined) {
  const text: Record<string, string> = {
    CRITICAL: '严重',
    WARNING: '普通',
  }
  return reportDictionaryText(text, severity, '未知严重程度')
}

async function loadReport(targetPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await businessReportingApi.exceptions.list({
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      keyword: filters.keyword,
      type: (filters.type || undefined) as BusinessExceptionType | undefined,
      page: targetPage,
      pageSize: pageSize.value,
    })
    rows.value = result.items
    summary.value = result.summary
    page.value = result.page
    total.value = result.total
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '经营异常清单加载失败'
    rows.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}

function search() {
  void loadReport(1)
}

function reset() {
  filters.dateFrom = ''
  filters.dateTo = ''
  filters.keyword = ''
  filters.type = ''
  void loadReport(1)
}

function changePageSize(size: number) {
  pageSize.value = size
  void loadReport(1)
}

async function openTrace(row: ExceptionReportRow) {
  if (!row.traceKey || !row.canViewResource) {
    return
  }
  traceVisible.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    const result = await businessReportingApi.exceptions.traces.list({
      traceKey: row.traceKey,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
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

onMounted(() => {
  void loadReport(1)
})
</script>

<template>
  <section class="report-page">
    <header class="report-page__header">
      <h1>异常清单</h1>
      <p>经营异常清单只展示可行动事项，不引入消息中心、审批流或自动通知。</p>
    </header>
    <ReportFilterBar
      :model-value="filters"
      :fields="fields"
      :loading="loading"
      @update:model-value="updateFilters"
      @search="search"
      @reset="reset"
    />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <ReportMetricStrip :metrics="metrics" />
    <div class="report-table-scroll">
      <el-table :data="rows" empty-text="暂无经营异常" stripe>
        <el-table-column label="异常类型" min-width="140">
          <template #default="{ row }">{{ exceptionTypeText(row.exceptionType) }}</template>
        </el-table-column>
        <el-table-column label="严重程度" min-width="110">
          <template #default="{ row }">{{ exceptionSeverityText(row.severity) }}</template>
        </el-table-column>
        <el-table-column label="异常对象" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.canViewResource ? row.objectName : '来源受限' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="业务日期" min-width="120">
          <template #default="{ row }">
            <span>{{ row.canViewResource ? row.businessDate : '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-button v-if="row.canViewResource && row.traceKey" data-test="open-report-trace" link type="primary" @click="openTrace(row)">
              追溯
            </el-button>
            <span v-else>无详情权限</span>
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
.report-table-scroll { overflow-x: auto; }
</style>
