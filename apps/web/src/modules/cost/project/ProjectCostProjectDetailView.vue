<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  projectCostApi,
  type ProjectCostCategory,
  type ProjectCostProjectDetail,
  type ProjectCostSourceListParams,
  type ProjectCostStage,
} from '../../../shared/api/projectCostApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation } from '../../../shared/navigation/navigationReturn'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import CategoryTag from './CategoryTag.vue'
import CompletenessTag from './CompletenessTag.vue'
import ProjectCostCalculationStatusTag from './ProjectCostCalculationStatusTag.vue'
import ProjectCostSourceTraceDrawer from './ProjectCostSourceTraceDrawer.vue'
import StageTag from './StageTag.vue'
import {
  formatProjectCostAmount,
  formatProjectCostDateTime,
  formatProjectCostRate,
  projectCostErrorMessage,
  projectCostFreshnessLabel,
  projectCostMessages,
  restrictedMoneyReason,
} from './projectCostPageHelpers'
import './ProjectCostShared.css'

const route = useRoute()
const router = useRouter()
const record = ref<ProjectCostProjectDetail | null>(null)
const sourceDrawerOpen = ref(false)
const sourceTraceFilters = ref<Partial<ProjectCostSourceListParams>>({})
const loading = ref(true)
const error = ref('')
const amountRestrictedReason = computed(() => restrictedMoneyReason(record.value))
const categorySummaries = computed(() => record.value?.categorySummaries ?? [])
const stageSummaries = computed(() => record.value?.stageSummaries ?? [])
const calculations = computed(() => record.value?.calculations ?? [])
const auditSummary = computed(() => record.value?.auditSummary ?? [])
const latestCalculationId = computed(() => record.value?.latestCalculationId ?? record.value?.calculationId ?? null)

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await projectCostApi.projectCosts.getProject(route.params.projectId as string)
  } catch (caught) {
    record.value = null
    error.value = projectCostErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function back() {
  void router.push(returnLocation(route, { name: 'cost-project-costs' }))
}

function viewCalculation(id: string | number) {
  void router.push({
    name: 'cost-project-cost-calculation-detail',
    params: { id: String(id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function openCategoryTrace(category: ProjectCostCategory) {
  if (!latestCalculationId.value) {
    return
  }
  sourceTraceFilters.value = { category }
  sourceDrawerOpen.value = true
}

function openStageTrace(stage: ProjectCostStage) {
  if (!latestCalculationId.value) {
    return
  }
  sourceTraceFilters.value = { stage }
  sourceDrawerOpen.value = true
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="项目成本详情" description="查看项目当前成本、阶段分布、经营毛利口径和历史核算运行。">
    <template #actions>
      <el-button @click="back">返回列表</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="项目成本详情加载中" :closable="false" />
    </template>

    <div v-if="record">
      <section class="project-cost-summary-strip">
        <div>
          <span>项目</span>
          <strong>{{ record.projectNo }} {{ record.projectName }}</strong>
        </div>
        <div>
          <span>截止日期</span>
          <strong>{{ record.cutoffDate || '-' }}</strong>
        </div>
        <div>
          <span>核算状态</span>
          <ProjectCostCalculationStatusTag :status="record.calculationStatus" />
        </div>
        <div>
          <span>当前性</span>
          <strong>{{ projectCostFreshnessLabel(record.freshnessStatus) }}</strong>
        </div>
        <div>
          <span>完整性</span>
          <CompletenessTag :status="record.completenessStatus" />
        </div>
        <div>
          <span>项目总成本</span>
          <strong>{{ formatProjectCostAmount(record.totalCost, amountRestrictedReason || undefined) }}</strong>
        </div>
        <div>
          <span>在制成本</span>
          <strong>{{ formatProjectCostAmount(record.wipCost, amountRestrictedReason || undefined) }}</strong>
        </div>
        <div>
          <span>发货经营毛利</span>
          <strong>{{ formatProjectCostAmount(record.shipmentGrossMargin, amountRestrictedReason || undefined) }}</strong>
        </div>
      </section>

      <div class="project-cost-section-grid">
        <section v-if="record.completenessStatus === 'INCOMPLETE'" class="project-cost-section">
          <span class="project-cost-section-title">完整性说明</span>
          <p>{{ projectCostMessages.incompleteMargin }}</p>
        </section>

        <section class="project-cost-section">
          <div class="project-cost-section-heading">
            <span class="project-cost-section-title">成本分类</span>
          </div>
          <div class="table-scroll">
            <el-table :data="categorySummaries" empty-text="暂无分类成本" stripe>
              <el-table-column label="分类" min-width="150">
                <template #default="{ row }"><CategoryTag :category="row.category" /></template>
              </el-table-column>
              <el-table-column label="金额" min-width="150" align="right">
                <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.amount, amountRestrictedReason || undefined) }}</span></template>
              </el-table-column>
              <el-table-column prop="sourceCount" label="来源数" min-width="100" />
              <el-table-column label="操作" min-width="90">
                <template #default="{ row }">
                  <el-button size="small" text data-test="trace-project-cost-category" :disabled="!latestCalculationId" @click="openCategoryTrace(row.category)">追溯</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section class="project-cost-section">
          <span class="project-cost-section-title">成本阶段</span>
          <div class="table-scroll">
            <el-table :data="stageSummaries" empty-text="暂无阶段成本" stripe>
              <el-table-column label="阶段" min-width="150">
                <template #default="{ row }"><StageTag :stage="row.stage" /></template>
              </el-table-column>
              <el-table-column label="金额" min-width="150" align="right">
                <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.amount, amountRestrictedReason || undefined) }}</span></template>
              </el-table-column>
              <el-table-column label="操作" min-width="90">
                <template #default="{ row }">
                  <el-button size="small" text data-test="trace-project-cost-stage" :disabled="!latestCalculationId" @click="openStageTrace(row.stage)">追溯</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section class="project-cost-section">
          <span class="project-cost-section-title">经营毛利口径</span>
          <div class="project-cost-summary-strip">
            <div>
              <span>发货经营口径</span>
              <strong>{{ formatProjectCostAmount(record.shipmentGrossMargin, amountRestrictedReason || undefined) }}</strong>
            </div>
            <div>
              <span>发货毛利率</span>
              <strong>{{ formatProjectCostRate(record.shipmentGrossMarginRate, amountRestrictedReason || undefined) }}</strong>
            </div>
            <div>
              <span>开票辅助口径</span>
              <strong>{{ formatProjectCostAmount(record.invoiceGrossMargin, amountRestrictedReason || undefined) }}</strong>
            </div>
            <div>
              <span>目标辅助口径</span>
              <strong>{{ formatProjectCostAmount(record.targetGrossMargin, amountRestrictedReason || undefined) }}</strong>
            </div>
          </div>
        </section>

        <section class="project-cost-section">
          <span class="project-cost-section-title">核算运行</span>
          <div class="table-scroll">
            <el-table :data="calculations" empty-text="暂无核算运行" stripe>
              <el-table-column prop="calculationNo" label="运行编号" min-width="170" show-overflow-tooltip />
              <el-table-column label="状态" min-width="110">
                <template #default="{ row }"><ProjectCostCalculationStatusTag :status="row.status" /></template>
              </el-table-column>
              <el-table-column prop="cutoffDate" label="截止日期" min-width="110" />
              <el-table-column label="计算时间" min-width="150">
                <template #default="{ row }">{{ formatProjectCostDateTime(row.calculatedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" min-width="90">
                <template #default="{ row }">
                  <el-button size="small" text data-test="view-cost-calculation" @click="viewCalculation(row.id)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section class="project-cost-section">
          <span class="project-cost-section-title">审计摘要</span>
          <p v-if="!auditSummary.length">暂无审计记录</p>
          <p v-for="item in auditSummary" :key="`${item.action}-${item.createdAt}`">
            {{ formatProjectCostDateTime(item.createdAt) }} · {{ item.operatorUsername }} · {{ item.action }}
          </p>
        </section>
      </div>

      <ProjectCostSourceTraceDrawer
        v-model="sourceDrawerOpen"
        :calculation-id="latestCalculationId"
        :initial-filters="sourceTraceFilters"
      />
    </div>
  </MasterDataTableView>
</template>
