<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { projectCostApi, type ProjectCostProjectDetail, type ResourceId } from '../../../shared/api/projectCostApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../../shared/navigation/navigationReturn'
import CompletenessTag from '../../cost/project/CompletenessTag.vue'
import ProjectCostCalculationStatusTag from '../../cost/project/ProjectCostCalculationStatusTag.vue'
import {
  formatProjectCostAmount,
  projectCostActionDisabledReason,
  projectCostErrorMessage,
  projectCostMessages,
  restrictedMoneyReason,
} from '../../cost/project/projectCostPageHelpers'

const props = defineProps<{
  projectId: ResourceId
}>()

const route = useRoute()
const router = useRouter()
const summary = ref<ProjectCostProjectDetail | null>(null)
const loading = ref(false)
const error = ref('')
const amountRestrictedReason = computed(() => restrictedMoneyReason(summary.value))

async function loadSummary() {
  loading.value = true
  error.value = ''
  try {
    summary.value = await projectCostApi.projectCosts.getProject(props.projectId)
  } catch (caught) {
    summary.value = null
    error.value = projectCostErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function viewProjectCost() {
  void router.push({
    name: 'cost-project-cost-detail',
    params: { projectId: String(props.projectId) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

onMounted(loadSummary)
</script>

<template>
  <section class="section-block">
    <div class="section-title">
      <span>项目成本摘要</span>
      <el-button
        v-if="summary"
        size="small"
        text
        type="primary"
        data-test="view-project-cost-detail"
        @click="viewProjectCost"
      >
        查看项目成本
      </el-button>
    </div>
    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="loading" class="state-alert" type="info" title="项目成本摘要加载中" :closable="false" />
    <div v-if="summary" class="fulfillment-grid">
      <div>
        <span>核算状态</span>
        <strong><ProjectCostCalculationStatusTag :status="summary.calculationStatus" /></strong>
      </div>
      <div>
        <span>完整性</span>
        <strong><CompletenessTag :status="summary.completenessStatus" /></strong>
      </div>
      <div>
        <span>项目总成本</span>
        <strong>{{ formatProjectCostAmount(summary.totalCost, amountRestrictedReason || undefined) }}</strong>
      </div>
      <div>
        <span>发货经营毛利</span>
        <strong>{{ formatProjectCostAmount(summary.shipmentGrossMargin, amountRestrictedReason || undefined) }}</strong>
      </div>
      <div>
        <span>在制成本</span>
        <strong>{{ formatProjectCostAmount(summary.wipCost, amountRestrictedReason || undefined) }}</strong>
      </div>
      <div>
        <span>阻断差异</span>
        <strong>{{ summary.sourceVisible === false ? projectCostMessages.sourceRestricted : (summary.blockingVarianceCount ?? '-') }}</strong>
      </div>
      <div>
        <span>完整性说明</span>
        <strong>{{ summary.completenessStatus === 'INCOMPLETE' ? projectCostMessages.incompleteMargin : '成本毛利完整' }}</strong>
      </div>
      <div>
        <span>动作说明</span>
        <strong>{{ projectCostActionDisabledReason(summary, 'CONFIRM') || summary.restrictedReason || '无阻断动作说明' }}</strong>
      </div>
    </div>
    <el-alert
      v-if="summary?.amountVisible === false"
      class="state-alert"
      type="warning"
      :title="summary.restrictedReason || projectCostMessages.amountForbidden"
      :closable="false"
    />
    <el-empty v-if="!loading && !summary && !error" description="暂无项目成本摘要" />
  </section>
</template>

<style scoped>
.section-block {
  border-top: 1px solid var(--qherp-border);
  margin-top: 18px;
  padding-top: 16px;
}

.section-title {
  align-items: center;
  display: flex;
  font-weight: 600;
  justify-content: space-between;
  margin-bottom: 10px;
}

.fulfillment-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.fulfillment-grid > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  padding: 10px 12px;
}

.fulfillment-grid span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

@media (max-width: 900px) {
  .fulfillment-grid {
    grid-template-columns: 1fr;
  }
}
</style>
