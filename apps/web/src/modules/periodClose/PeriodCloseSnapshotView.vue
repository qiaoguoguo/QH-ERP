<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  businessPeriodCloseApi,
  type BusinessPeriodCloseReportCode,
  type BusinessPeriodCloseSnapshotInventoryRecord,
  type BusinessPeriodCloseSnapshotOverview,
  type BusinessPeriodCloseSnapshotProjectCostRecord,
  type BusinessPeriodCloseSnapshotReport,
  type BusinessPeriodCloseSnapshotWipRecord,
} from '../../shared/api/businessPeriodCloseApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation } from '../../shared/navigation/navigationReturn'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../system/shared/pageHelpers'
import {
  formatPeriodCloseAmount,
  formatPeriodCloseDateTime,
  formatPeriodCloseQuantity,
  formatPeriodCloseRawDecimal,
  periodCloseErrorMessage,
  periodCloseReportCodes,
  periodCloseReportLabels,
  periodCloseSnapshotStageLabel,
  restrictedMoneyReason,
} from './periodClosePageHelpers'
import './PeriodCloseShared.css'

const route = useRoute()
const router = useRouter()
const overview = ref<BusinessPeriodCloseSnapshotOverview | null>(null)
const inventory = ref<BusinessPeriodCloseSnapshotInventoryRecord[]>([])
const wip = ref<BusinessPeriodCloseSnapshotWipRecord[]>([])
const projectCosts = ref<BusinessPeriodCloseSnapshotProjectCostRecord[]>([])
const report = ref<BusinessPeriodCloseSnapshotReport | null>(null)
const loading = ref(false)
const inventoryLoading = ref(false)
const wipLoading = ref(false)
const projectCostLoading = ref(false)
const error = ref('')
const inventoryPagination = reactive({ page: 1, pageSize: 10, total: 0 })
const wipPagination = reactive({ page: 1, pageSize: 10, total: 0 })
const projectCostPagination = reactive({ page: 1, pageSize: 10, total: 0 })
const activeReportCode = ref<BusinessPeriodCloseReportCode>('OVERVIEW')

const runId = computed(() => route.params.runId as string)
const reportEntries = computed(() => Object.entries(report.value?.result ?? {}))
const reportIsEmpty = computed(() => reportEntries.value.length === 0)

async function loadOverview() {
  loading.value = true
  error.value = ''
  try {
    overview.value = await businessPeriodCloseApi.snapshots.get(runId.value)
  } catch (caught) {
    overview.value = null
    error.value = periodCloseErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadInventory() {
  inventoryLoading.value = true
  try {
    const page = await businessPeriodCloseApi.snapshots.inventory(runId.value, {
      page: inventoryPagination.page,
      pageSize: inventoryPagination.pageSize,
    })
    inventory.value = pageItems(page)
    inventoryPagination.total = pageTotal(page)
  } catch (caught) {
    error.value = periodCloseErrorMessage(caught)
  } finally {
    inventoryLoading.value = false
  }
}

async function loadWip() {
  wipLoading.value = true
  try {
    const page = await businessPeriodCloseApi.snapshots.wip(runId.value, {
      page: wipPagination.page,
      pageSize: wipPagination.pageSize,
    })
    wip.value = pageItems(page)
    wipPagination.total = pageTotal(page)
  } catch (caught) {
    error.value = periodCloseErrorMessage(caught)
  } finally {
    wipLoading.value = false
  }
}

async function loadProjectCosts() {
  projectCostLoading.value = true
  try {
    const page = await businessPeriodCloseApi.snapshots.projectCosts(runId.value, {
      page: projectCostPagination.page,
      pageSize: projectCostPagination.pageSize,
    })
    projectCosts.value = pageItems(page)
    projectCostPagination.total = pageTotal(page)
  } catch (caught) {
    error.value = periodCloseErrorMessage(caught)
  } finally {
    projectCostLoading.value = false
  }
}

async function loadReport() {
  try {
    report.value = await businessPeriodCloseApi.snapshots.report(runId.value, activeReportCode.value)
  } catch (caught) {
    error.value = periodCloseErrorMessage(caught)
  }
}

async function loadAll() {
  await Promise.all([loadOverview(), loadInventory(), loadWip(), loadProjectCosts(), loadReport()])
}

function back() {
  void router.push(returnLocation(route, { name: 'period-close-run-detail', params: { runId: runId.value } }))
}

function viewSourceCheck() {
  if (!overview.value?.sourceCheckRunId) {
    return
  }
  void router.push({
    name: 'period-close-check-detail',
    params: { runId: runId.value, checkId: String(overview.value.sourceCheckRunId) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function changeInventoryPage(page: number) {
  inventoryPagination.page = page
  void loadInventory()
}

function changeInventoryPageSize(pageSize: number) {
  inventoryPagination.pageSize = pageSize
  inventoryPagination.page = 1
  void loadInventory()
}

function changeWipPage(page: number) {
  wipPagination.page = page
  void loadWip()
}

function changeWipPageSize(pageSize: number) {
  wipPagination.pageSize = pageSize
  wipPagination.page = 1
  void loadWip()
}

function changeProjectCostPage(page: number) {
  projectCostPagination.page = page
  void loadProjectCosts()
}

function changeProjectCostPageSize(pageSize: number) {
  projectCostPagination.pageSize = pageSize
  projectCostPagination.page = 1
  void loadProjectCosts()
}

async function selectReport(code: BusinessPeriodCloseReportCode) {
  activeReportCode.value = code
  await loadReport()
}

function formatReportValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

onMounted(loadAll)
</script>

<template>
  <MasterDataTableView title="期间快照详情" description="只读查看业务月结冻结的库存、在制、项目成本和八类经营报表基线；实时数据不得冒充快照。">
    <template #actions>
      <el-button @click="back">返回</el-button>
      <el-button v-if="overview?.sourceCheckRunId" data-test="period-close-snapshot-source-check" type="primary" plain @click="viewSourceCheck">
        来源检查
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="期间快照加载中" :closable="false" />
      <el-alert v-if="overview?.isHistoricalRevision" class="state-alert" type="warning" title="已重开历史快照：旧版本保留为当时真实冻结基线" :closable="false" />
    </template>

    <section class="period-close-summary-strip">
      <div><span>冻结期间</span><strong>{{ overview?.periodCode || '-' }}</strong></div>
      <div><span>期间日期</span><strong>{{ overview?.startDate || '-' }} 至 {{ overview?.endDate || '-' }}</strong></div>
      <div><span>快照版本</span><strong>{{ overview?.revisionNo ?? '-' }}</strong></div>
      <div><span>生成时间</span><strong>{{ formatPeriodCloseDateTime(overview?.generatedAt) }}</strong></div>
      <div><span>生成操作人</span><strong>{{ overview?.generatedBy || '-' }}</strong></div>
      <div><span>来源检查</span><strong>{{ overview?.sourceCheckRunId || '-' }}</strong></div>
      <div><span>来源指纹</span><strong>{{ overview?.sourceFingerprint || '-' }}</strong></div>
    </section>

    <section class="period-close-summary-strip">
      <div v-for="partition in overview?.partitions ?? []" :key="partition.code">
        <span>{{ partition.name }} · {{ partition.recordCount ?? 0 }} 条</span>
        <strong>{{ partition.restrictedReason || partition.sourceFingerprint || '已冻结' }}</strong>
      </div>
    </section>

    <div class="period-close-section-grid">
      <section class="period-close-section">
        <div class="period-close-section-heading">
          <span class="period-close-section-title">库存快照</span>
          <span class="period-close-muted">数量和价值为期间关闭时冻结口径。</span>
        </div>
        <div class="table-scroll">
          <el-table :data="inventory" :empty-text="inventoryLoading ? '加载中' : '暂无库存快照'" stripe>
            <el-table-column prop="materialCode" label="物料编码" min-width="130" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" min-width="120" show-overflow-tooltip />
            <el-table-column label="期末数量" min-width="130" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatPeriodCloseQuantity(row.endingQuantity) }}</span></template>
            </el-table-column>
            <el-table-column label="单位成本" min-width="130" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatPeriodCloseAmount(row.unitCost, restrictedMoneyReason(row)) }}</span></template>
            </el-table-column>
            <el-table-column label="期末价值" min-width="140" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatPeriodCloseAmount(row.endingValue, restrictedMoneyReason(row)) }}</span></template>
            </el-table-column>
          </el-table>
        </div>
        <el-pagination
          class="table-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="inventoryPagination.total"
          :page-size="inventoryPagination.pageSize"
          :current-page="inventoryPagination.page"
          @current-change="changeInventoryPage"
          @size-change="changeInventoryPageSize"
        />
      </section>

      <section class="period-close-section">
        <div class="period-close-section-heading">
          <span class="period-close-section-title">在制/生产</span>
          <span class="period-close-muted">在制存在可作为警告通过，来源断链仍阻断。</span>
        </div>
        <div class="table-scroll">
          <el-table :data="wip" :empty-text="wipLoading ? '加载中' : '暂无在制快照'" stripe>
            <el-table-column prop="projectNo" label="项目" min-width="130" show-overflow-tooltip />
            <el-table-column prop="workOrderNo" label="工单" min-width="130" show-overflow-tooltip />
            <el-table-column prop="materialCode" label="物料" min-width="130" />
            <el-table-column label="阶段" min-width="100">
              <template #default="{ row }">{{ periodCloseSnapshotStageLabel(row.stage) }}</template>
            </el-table-column>
            <el-table-column label="在制数量" min-width="130" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatPeriodCloseQuantity(row.wipQuantity) }}</span></template>
            </el-table-column>
            <el-table-column label="在制金额" min-width="140" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatPeriodCloseAmount(row.wipAmount, restrictedMoneyReason(row)) }}</span></template>
            </el-table-column>
          </el-table>
        </div>
        <el-pagination
          class="table-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="wipPagination.total"
          :page-size="wipPagination.pageSize"
          :current-page="wipPagination.page"
          @current-change="changeWipPage"
          @size-change="changeWipPageSize"
        />
      </section>

      <section class="period-close-section">
        <div class="period-close-section-heading">
          <span class="period-close-section-title">项目成本</span>
          <span class="period-close-muted">引用截止日匹配的 029 有效运行，不重算项目成本。</span>
        </div>
        <div class="table-scroll">
          <el-table :data="projectCosts" :empty-text="projectCostLoading ? '加载中' : '暂无项目成本快照'" stripe>
            <el-table-column prop="projectNo" label="项目" min-width="130" show-overflow-tooltip />
            <el-table-column prop="calculationNo" label="核算运行" min-width="140" />
            <el-table-column prop="cutoffDate" label="截止日" min-width="110" />
            <el-table-column label="总成本" min-width="140" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatPeriodCloseAmount(row.totalCost, restrictedMoneyReason(row)) }}</span></template>
            </el-table-column>
            <el-table-column label="收入" min-width="140" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatPeriodCloseAmount(row.revenueAmount, restrictedMoneyReason(row)) }}</span></template>
            </el-table-column>
            <el-table-column label="毛利率" min-width="120" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatPeriodCloseRawDecimal(row.grossMarginRate, restrictedMoneyReason(row)) }}</span></template>
            </el-table-column>
          </el-table>
        </div>
        <el-pagination
          class="table-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="projectCostPagination.total"
          :page-size="projectCostPagination.pageSize"
          :current-page="projectCostPagination.page"
          @current-change="changeProjectCostPage"
          @size-change="changeProjectCostPageSize"
        />
      </section>

      <section class="period-close-section">
        <div class="period-close-section-heading">
          <span class="period-close-section-title">经营报表基线</span>
          <span class="period-close-muted">以下为 030 冻结报表，不是实时查询或正式财务报表。</span>
        </div>
        <div class="period-close-report-grid">
          <button
            v-for="code in periodCloseReportCodes"
            :key="code"
            type="button"
            class="period-close-report-chip"
            :aria-pressed="activeReportCode === code"
            @click="selectReport(code)"
          >
            {{ periodCloseReportLabels[code] }}
          </button>
        </div>
        <el-alert
          v-if="report?.restrictedReason"
          class="state-alert"
          type="warning"
          :title="report.restrictedReason"
          :closable="false"
        />
        <dl v-if="report" class="period-close-detail-list">
          <div>
            <dt>报表</dt>
            <dd>{{ report.reportName }}</dd>
          </div>
          <div>
            <dt>生成时间</dt>
            <dd>{{ formatPeriodCloseDateTime(report.generatedAt) }}</dd>
          </div>
          <div>
            <dt>来源数量</dt>
            <dd>{{ report.sourceCount }}</dd>
          </div>
          <div>
            <dt>来源指纹</dt>
            <dd>{{ report.sourceFingerprint || '-' }}</dd>
          </div>
        </dl>
        <el-empty v-if="report && reportIsEmpty" description="该冻结报表本期无明细数据" />
        <dl v-else class="period-close-detail-list">
          <div v-for="[key, value] in reportEntries" :key="key">
            <dt>{{ key }}</dt>
            <dd>{{ formatReportValue(value) }}</dd>
          </div>
        </dl>
      </section>
    </div>
  </MasterDataTableView>
</template>
