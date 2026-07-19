<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  projectCostApi,
  type ProjectCostCalculationDetail,
  type ProjectCostEntryRecord,
  type ProjectCostSourceRecord,
  type ProjectCostVarianceRecord,
  type ResourceId,
} from '../../../shared/api/projectCostApi'
import { returnLocation } from '../../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../../stores/authStore'
import { confirmAction } from '../../../shared/ui/confirmDialog'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
import CategoryTag from './CategoryTag.vue'
import CompletenessTag from './CompletenessTag.vue'
import ProjectCostCalculationStatusTag from './ProjectCostCalculationStatusTag.vue'
import ProjectCostSourceTraceDrawer from './ProjectCostSourceTraceDrawer.vue'
import StageTag from './StageTag.vue'
import VarianceSeverityTag from './VarianceSeverityTag.vue'
import {
  formatProjectCostAmount,
  formatProjectCostDateTime,
  formatProjectCostRate,
  projectCostActionDisabledReason,
  projectCostAllowed,
  projectCostErrorMessage,
  projectCostFreshnessLabel,
  projectCostMessages,
  projectCostVarianceStatusLabel,
  projectCostVarianceTypeLabel,
  restrictedMoneyReason,
} from './projectCostPageHelpers'
import './ProjectCostShared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<ProjectCostCalculationDetail | null>(null)
const sources = ref<ProjectCostSourceRecord[]>([])
const entries = ref<ProjectCostEntryRecord[]>([])
const variances = ref<ProjectCostVarianceRecord[]>([])
const sourceDrawerOpen = ref(false)
const loading = ref(true)
const sourceLoading = ref(false)
const entryLoading = ref(false)
const varianceLoading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const sourcePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const entryPagination = reactive({ page: 1, pageSize: 10, total: 0 })
const variancePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const calculationId = computed<ResourceId>(() => normalizeId(route.params.id as string))
const amountRestrictedReason = computed(() => restrictedMoneyReason(record.value))

function normalizeId(value: string): ResourceId {
  const numeric = Number(value)
  return Number.isFinite(numeric) && String(numeric) === value ? numeric : value
}

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await projectCostApi.calculations.get(route.params.id as string)
  } catch (caught) {
    record.value = null
    error.value = projectCostErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadSources() {
  sourceLoading.value = true
  try {
    const page = await projectCostApi.calculations.sources(route.params.id as string, {
      page: sourcePagination.page,
      pageSize: sourcePagination.pageSize,
    })
    sources.value = pageItems(page)
    sourcePagination.total = pageTotal(page)
  } catch (caught) {
    actionError.value = projectCostErrorMessage(caught)
  } finally {
    sourceLoading.value = false
  }
}

async function loadEntries() {
  entryLoading.value = true
  try {
    const page = await projectCostApi.calculations.entries(route.params.id as string, {
      page: entryPagination.page,
      pageSize: entryPagination.pageSize,
    })
    entries.value = pageItems(page)
    entryPagination.total = pageTotal(page)
  } catch (caught) {
    actionError.value = projectCostErrorMessage(caught)
  } finally {
    entryLoading.value = false
  }
}

async function loadVariances() {
  varianceLoading.value = true
  try {
    const page = await projectCostApi.calculations.variances(route.params.id as string, {
      page: variancePagination.page,
      pageSize: variancePagination.pageSize,
    })
    variances.value = pageItems(page)
    variancePagination.total = pageTotal(page)
  } catch (caught) {
    actionError.value = projectCostErrorMessage(caught)
  } finally {
    varianceLoading.value = false
  }
}

async function loadAll() {
  await loadRecord()
  await Promise.all([loadSources(), loadEntries(), loadVariances()])
}

function back() {
  void router.push(returnLocation(route, { name: 'cost-project-costs' }))
}

async function runAction(action: 'RECALCULATE' | 'CONFIRM' | 'CANCEL') {
  if (!record.value || actionLoading.value || !projectCostAllowed(record.value, action)) {
    return
  }
  const messages = {
    RECALCULATE: `重算核算运行“${record.value.calculationNo}”？`,
    CONFIRM: `确认核算运行“${record.value.calculationNo}”？确认后将形成项目成本历史快照。`,
    CANCEL: `取消核算运行“${record.value.calculationNo}”？`,
  }
  if (!(await confirmAction(messages[action]))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  const payload = {
    version: record.value.version,
    sourceFingerprint: record.value.sourceFingerprint,
    idempotencyKey: createIdempotencyKey(`project-cost-${action.toLowerCase()}`),
  }
  try {
    if (action === 'RECALCULATE') {
      await projectCostApi.calculations.recalculate(calculationId.value, payload)
    } else if (action === 'CONFIRM') {
      await projectCostApi.calculations.confirm(calculationId.value, payload)
    } else {
      await projectCostApi.calculations.cancel(calculationId.value, payload)
    }
    await loadRecord()
  } catch (caught) {
    actionError.value = projectCostErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function canRun(action: 'RECALCULATE' | 'CONFIRM' | 'CANCEL') {
  const permissionMap = {
    RECALCULATE: 'cost:project-cost:calculate',
    CONFIRM: 'cost:project-cost:confirm',
    CANCEL: 'cost:project-cost:cancel',
  }
  return Boolean(record.value)
    && authStore.hasPermission(permissionMap[action])
    && projectCostAllowed(record.value, action)
}

onMounted(loadAll)
</script>

<template>
  <MasterDataTableView title="核算运行详情" description="查看单次项目成本核算的来源、分录、差异和受控状态动作。">
    <template #actions>
      <el-button @click="back">返回</el-button>
      <el-button v-if="canRun('RECALCULATE')" data-test="recalculate-project-cost-calculation" type="primary" :loading="actionLoading" :title="projectCostActionDisabledReason(record, 'RECALCULATE')" @click="runAction('RECALCULATE')">重算</el-button>
      <el-button v-if="canRun('CONFIRM')" data-test="confirm-project-cost-calculation" type="success" :loading="actionLoading" :title="projectCostActionDisabledReason(record, 'CONFIRM')" @click="runAction('CONFIRM')">确认</el-button>
      <el-button v-if="canRun('CANCEL')" data-test="cancel-project-cost-calculation" type="danger" :loading="actionLoading" :title="projectCostActionDisabledReason(record, 'CANCEL')" @click="runAction('CANCEL')">取消</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="核算运行加载中" :closable="false" />
    </template>

    <div v-if="record">
      <section class="project-cost-summary-strip">
        <div><span>运行编号</span><strong>{{ record.calculationNo }}</strong></div>
        <div><span>项目</span><strong>{{ record.projectNo }} {{ record.projectName }}</strong></div>
        <div><span>截止日期</span><strong>{{ record.cutoffDate }}</strong></div>
        <div><span>状态</span><ProjectCostCalculationStatusTag :status="record.status" /></div>
        <div><span>当前性</span><strong>{{ projectCostFreshnessLabel(record.freshnessStatus) }}</strong></div>
        <div><span>完整性</span><CompletenessTag :status="record.completenessStatus" /></div>
        <div><span>来源指纹</span><strong>{{ record.sourceFingerprint }}</strong></div>
        <div><span>版本</span><strong>{{ record.version }}</strong></div>
      </section>

      <section v-if="record.completenessStatus === 'INCOMPLETE'" class="project-cost-section-grid">
        <div class="project-cost-section">
          <span class="project-cost-section-title">完整性说明</span>
          <p>{{ projectCostMessages.incompleteMargin }}</p>
        </div>
      </section>

      <section class="project-cost-summary-strip">
        <div><span>项目总成本</span><strong>{{ formatProjectCostAmount(record.totalCost, amountRestrictedReason || undefined) }}</strong></div>
        <div><span>发货收入</span><strong>{{ formatProjectCostAmount(record.shipmentPretaxRevenue, amountRestrictedReason || undefined) }}</strong></div>
        <div><span>发货经营毛利</span><strong>{{ formatProjectCostAmount(record.shipmentGrossMargin, amountRestrictedReason || undefined) }}</strong></div>
        <div><span>发货毛利率</span><strong>{{ formatProjectCostRate(record.shipmentGrossMarginRate, amountRestrictedReason || undefined) }}</strong></div>
      </section>

      <div class="project-cost-section-grid">
        <section class="project-cost-section">
          <div class="project-cost-section-heading">
            <span class="project-cost-section-title">来源摘要</span>
            <el-button data-test="open-source-trace" type="primary" plain size="small" @click="sourceDrawerOpen = true">来源追溯</el-button>
          </div>
          <div class="table-scroll">
            <el-table :data="sources" :empty-text="sourceLoading ? '加载中' : '暂无来源摘要'" stripe>
              <el-table-column label="分类" min-width="110"><template #default="{ row }"><CategoryTag :category="row.category" /></template></el-table-column>
              <el-table-column label="阶段" min-width="110"><template #default="{ row }"><StageTag :stage="row.stage" /></template></el-table-column>
              <el-table-column label="来源" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.sourceSummary || row.sourceNo || '-' }}</template></el-table-column>
              <el-table-column label="金额" min-width="140" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.sourceAmount, restrictedMoneyReason(row) || undefined) }}</span></template></el-table-column>
            </el-table>
          </div>
        </section>

        <section class="project-cost-section">
          <span class="project-cost-section-title">成本分录</span>
          <div class="table-scroll">
            <el-table :data="entries" :empty-text="entryLoading ? '加载中' : '暂无成本分录'" stripe>
              <el-table-column label="分类" min-width="110"><template #default="{ row }"><CategoryTag :category="row.category" /></template></el-table-column>
              <el-table-column label="阶段" min-width="110"><template #default="{ row }"><StageTag :stage="row.stage" /></template></el-table-column>
              <el-table-column prop="description" label="说明" min-width="190" show-overflow-tooltip />
              <el-table-column label="金额" min-width="140" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.amount, amountRestrictedReason || undefined) }}</span></template></el-table-column>
            </el-table>
          </div>
        </section>

        <section class="project-cost-section">
          <span class="project-cost-section-title">差异</span>
          <div class="table-scroll">
            <el-table :data="variances" :empty-text="varianceLoading ? '加载中' : '暂无项目成本差异'" stripe>
              <el-table-column label="严重级别" min-width="110"><template #default="{ row }"><VarianceSeverityTag :severity="row.severity" /></template></el-table-column>
              <el-table-column label="类型" min-width="150"><template #default="{ row }">{{ projectCostVarianceTypeLabel(row.varianceType) }}</template></el-table-column>
              <el-table-column label="差额" min-width="140" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.differenceAmount, restrictedMoneyReason(row) || undefined) }}</span></template></el-table-column>
              <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ projectCostVarianceStatusLabel(row.status) }}</template></el-table-column>
            </el-table>
          </div>
        </section>

        <section class="project-cost-section">
          <span class="project-cost-section-title">计算审计</span>
          <p>计算人：{{ record.calculatedByName || '-' }} · {{ formatProjectCostDateTime(record.calculatedAt) }}</p>
          <p>确认人：{{ record.confirmedByName || '-' }} · {{ formatProjectCostDateTime(record.confirmedAt) }}</p>
        </section>
      </div>

      <ProjectCostSourceTraceDrawer v-model="sourceDrawerOpen" :sources="sources" />
    </div>
  </MasterDataTableView>
</template>
