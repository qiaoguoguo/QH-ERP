<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { businessReportingApi, type ReportOverviewRecord } from '../../shared/api/businessReportingApi'
import { useAuthStore } from '../../stores/authStore'
import ReportFilterBar, { type ReportFilterField } from './ReportFilterBar.vue'
import ReportMetricStrip from './ReportMetricStrip.vue'
import { reportPermissions, reportRouteConfigs } from './reportPageHelpers'
import { displayValue, statusText } from './operatingFinanceReportHelpers'

const filters = reactive<Record<string, string>>({
  dateFrom: '',
  dateTo: '',
})
const fields: ReportFilterField[] = [
  { key: 'dateFrom', label: '开始日期', name: 'report-date-from', type: 'date' },
  { key: 'dateTo', label: '结束日期', name: 'report-date-to', type: 'date' },
]
const loading = ref(false)
const error = ref('')
const record = ref<ReportOverviewRecord | null>(null)
const operatingFinanceLoading = ref(false)
const operatingFinanceError = ref('')
const operatingFinanceRecord = ref<Awaited<ReturnType<typeof businessReportingApi.operatingFinanceOverview.get>> | null>(null)
const authStore = useAuthStore()
const fixedReportEntries = computed(() =>
  reportRouteConfigs.filter((item) => item.routeName !== 'reports-overview' && item.menuVisible !== false && item.group !== 'operatingFinance' && authStore.hasPermission(item.permission)),
)
const operatingFinanceEntries = computed(() =>
  reportRouteConfigs.filter((item) => item.group === 'operatingFinance' && item.menuVisible !== false && authStore.hasPermission(item.permission)),
)
const canViewOperatingFinance = computed(() => authStore.hasPermission(reportPermissions.operatingFinanceView))

const metrics = computed(() => record.value ? [
  { label: '销售出库经营金额', value: record.value.salesShipmentAmount },
  { label: '采购入库经营金额', value: record.value.purchaseReceiptAmount },
  { label: '库存入库数量', value: record.value.inventoryInQuantity },
  { label: '库存出库数量', value: record.value.inventoryOutQuantity },
  { label: '生产计划数量', value: record.value.productionPlannedQuantity },
  { label: '生产完工数量', value: record.value.productionCompletedQuantity },
  { label: '成本归集金额', value: record.value.costAmount },
  { label: '应收余额', value: record.value.receivableBalance },
  { label: '应付余额', value: record.value.payableBalance },
  { label: '已收金额', value: record.value.receivedAmount },
  { label: '已付金额', value: record.value.paidAmount },
  { label: '经营异常数量', value: record.value.exceptionCount },
] : [])
const operatingFinanceMetrics = computed(() => operatingFinanceRecord.value ? [
  { label: '项目利润', value: displayValue(operatingFinanceRecord.value.projectProfitAmount) },
  { label: '合同未收', value: displayValue(operatingFinanceRecord.value.contractUnreceivedAmount) },
  { label: '采购差异', value: displayValue(operatingFinanceRecord.value.procurementVarianceAmount) },
  { label: '库存资金', value: displayValue(operatingFinanceRecord.value.inventoryCapitalAmount) },
  { label: '往来余额', value: displayValue(operatingFinanceRecord.value.receivablePayableBalanceAmount) },
  { label: '会计差异', value: displayValue(operatingFinanceRecord.value.accountingDifferenceAmount) },
  { label: '经营/会计定稿', value: statusText(operatingFinanceRecord.value.finalityStatus) },
  { label: '来源数量', value: operatingFinanceRecord.value.sourceCount },
] : [])
function isZeroDecimalText(value: string) {
  return /^-?0+(?:\.0+)?$/.test(value.trim())
}

const empty = computed(() => {
  if (!record.value) {
    return false
  }
  return [
    record.value.salesShipmentAmount,
    record.value.purchaseReceiptAmount,
    record.value.inventoryInQuantity,
    record.value.inventoryOutQuantity,
    record.value.productionPlannedQuantity,
    record.value.productionCompletedQuantity,
    record.value.costAmount,
    record.value.receivableBalance,
    record.value.payableBalance,
    record.value.receivedAmount,
    record.value.paidAmount,
  ].every(isZeroDecimalText) && record.value.exceptionCount === 0
})

function updateFilters(value: Record<string, string | number | undefined>) {
  Object.assign(filters, value)
}

async function loadOverview() {
  loading.value = true
  error.value = ''
  try {
    record.value = await businessReportingApi.overview.get({
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
    })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '经营概览加载失败'
    record.value = null
  } finally {
    loading.value = false
  }
}

async function loadOperatingFinanceOverview() {
  if (!canViewOperatingFinance.value) {
    operatingFinanceRecord.value = null
    return
  }
  operatingFinanceLoading.value = true
  operatingFinanceError.value = ''
  try {
    operatingFinanceRecord.value = await businessReportingApi.operatingFinanceOverview.get({
      analysisMode: 'LIVE',
    })
  } catch (cause) {
    operatingFinanceError.value = cause instanceof Error ? cause.message : '经营财务摘要加载失败'
    operatingFinanceRecord.value = null
  } finally {
    operatingFinanceLoading.value = false
  }
}

function reset() {
  filters.dateFrom = ''
  filters.dateTo = ''
  void loadOverview()
  void loadOperatingFinanceOverview()
}

onMounted(() => {
  void loadOverview()
  void loadOperatingFinanceOverview()
})
</script>

<template>
  <section class="report-page">
    <header class="report-page__header">
      <h1>经营概览</h1>
      <p>按固定经营口径查看当前期间采购、销售、库存、生产、成本和往来概况。</p>
    </header>

    <ReportFilterBar
      :model-value="filters"
      :fields="fields"
      :loading="loading"
      @update:model-value="updateFilters"
      @search="loadOverview"
      @reset="reset"
    />

    <section class="report-entry-bar" aria-label="固定报表入口">
      <span class="report-entry-bar__label">固定报表</span>
      <nav class="report-entry-bar__links">
        <router-link
          v-for="entry in fixedReportEntries"
          :key="entry.routeName"
          data-test="fixed-report-entry"
          class="report-entry-bar__link"
          :to="entry.path"
        >
          {{ entry.menuName }}
        </router-link>
      </nav>
    </section>

    <section v-if="operatingFinanceEntries.length > 0 || canViewOperatingFinance" class="report-entry-bar" aria-label="项目利润与经营财务分析">
      <span class="report-entry-bar__label">项目利润与经营财务分析</span>
      <nav class="report-entry-bar__links">
        <router-link
          v-for="entry in operatingFinanceEntries"
          :key="entry.routeName"
          data-test="fixed-report-entry"
          class="report-entry-bar__link"
          :to="entry.path"
        >
          {{ entry.menuName }}
        </router-link>
      </nav>
    </section>

    <section v-if="canViewOperatingFinance" class="report-section report-section--priority">
      <h2>项目利润与经营财务分析</h2>
      <el-alert v-if="operatingFinanceError" :title="operatingFinanceError" type="error" show-icon :closable="false" />
      <div v-if="operatingFinanceLoading" class="report-state">经营财务摘要加载中</div>
      <template v-else-if="operatingFinanceRecord">
        <p class="report-note">
          期间：{{ operatingFinanceRecord.periodCode }}；{{ statusText(operatingFinanceRecord.analysisMode) }}；
          业务月结 {{ statusText(operatingFinanceRecord.businessPeriodStatus) }}，会计期间 {{ statusText(operatingFinanceRecord.accountingPeriodStatus) }}，财务关闭 {{ statusText(operatingFinanceRecord.financialCloseStatus) }}。
        </p>
        <p v-if="operatingFinanceRecord.restrictedReason" class="report-note">{{ operatingFinanceRecord.restrictedReason }}</p>
        <ReportMetricStrip :metrics="operatingFinanceMetrics" />
      </template>
      <el-empty v-else description="暂无经营财务摘要" />
    </section>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="report-state">经营概览加载中</div>
    <template v-else-if="record">
      <p class="report-note">
        当前期间：{{ record.period.dateFrom }} 至 {{ record.period.dateTo }}。业务经营口径用于经营分析，不等同正式财务入账。
      </p>
      <el-empty v-if="empty" description="暂无经营概览数据" />
      <ReportMetricStrip v-else :metrics="metrics" />
    </template>
    <el-empty v-else description="暂无经营概览数据" />
  </section>
</template>

<style scoped>
.report-page {
  min-width: 0;
}

.report-page__header h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.report-page__header p,
.report-note {
  color: var(--qherp-steel);
  margin: 0;
}

.report-entry-bar {
  align-items: center;
  border-bottom: 1px solid var(--qherp-border-soft);
  border-top: 1px solid var(--qherp-border-soft);
  display: flex;
  gap: 14px;
  margin: 0 0 16px;
  padding: 10px 0;
}

.report-entry-bar__label {
  color: var(--qherp-text);
  flex: 0 0 auto;
  font-size: 14px;
  font-weight: 600;
}

.report-entry-bar__links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.report-entry-bar__link {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  color: var(--qherp-text);
  font-size: 13px;
  line-height: 1;
  padding: 8px 10px;
  text-decoration: none;
  white-space: nowrap;
}

.report-entry-bar__link:hover,
.report-entry-bar__link.router-link-active {
  border-color: var(--qherp-brand-tag);
  color: var(--qherp-brand-tag);
}

.report-state {
  color: var(--qherp-steel);
  padding: 16px 0;
}

.report-section h2 {
  font-size: 16px;
  margin: 0 0 8px;
}

.report-section--priority {
  margin: 0 0 16px;
}
</style>
