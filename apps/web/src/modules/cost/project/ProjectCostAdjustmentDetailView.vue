<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { projectCostApi, type ProjectCostAdjustmentRecord } from '../../../shared/api/projectCostApi'
import { returnLocation } from '../../../shared/navigation/navigationReturn'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import CategoryTag from './CategoryTag.vue'
import StageTag from './StageTag.vue'
import {
  formatProjectCostAmount,
  projectCostAdjustmentStatusLabel,
  projectCostErrorMessage,
  restrictedMoneyReason,
} from './projectCostPageHelpers'
import './ProjectCostShared.css'

const route = useRoute()
const router = useRouter()
const record = ref<ProjectCostAdjustmentRecord | null>(null)
const loading = ref(true)
const error = ref('')
const amountRestrictedReason = computed(() => restrictedMoneyReason(record.value))
const adjustmentLines = computed(() => record.value?.lines ?? [])

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await projectCostApi.adjustments.get(route.params.id as string)
  } catch (caught) {
    record.value = null
    error.value = projectCostErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function back() {
  void router.push(returnLocation(route, { name: 'cost-project-cost-adjustments' }))
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="成本调整/分配详情" description="查看调整原因、审批状态、公共费用来源和项目成本影响。">
    <template #actions>
      <el-button @click="back">返回列表</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="成本调整/分配详情加载中" :closable="false" />
    </template>

    <div v-if="record">
      <section class="project-cost-summary-strip">
        <div><span>调整编号</span><strong>{{ record.adjustmentNo }}</strong></div>
        <div><span>状态</span><strong>{{ projectCostAdjustmentStatusLabel(record.status) }}</strong></div>
        <div><span>审批状态</span><strong>{{ projectCostAdjustmentStatusLabel(record.approvalStatus || record.status) }}</strong></div>
        <div><span>业务日期</span><strong>{{ record.businessDate }}</strong></div>
        <div><span>总金额</span><strong>{{ formatProjectCostAmount(record.totalAmount, amountRestrictedReason || undefined) }}</strong></div>
        <div><span>原因</span><strong>{{ record.reason || '-' }}</strong></div>
        <div><span>拒绝原因</span><strong>{{ record.rejectedReason || '-' }}</strong></div>
        <div><span>原调整</span><strong>{{ record.originalAdjustmentNo || '-' }}</strong></div>
      </section>

      <div class="project-cost-section-grid">
        <section class="project-cost-section">
          <span class="project-cost-section-title">调整行</span>
          <div class="table-scroll">
            <el-table :data="adjustmentLines" empty-text="暂无调整行" stripe>
              <el-table-column label="项目" min-width="210" show-overflow-tooltip>
                <template #default="{ row }">{{ row.projectNo }} {{ row.projectName }}</template>
              </el-table-column>
              <el-table-column label="分类" min-width="120"><template #default="{ row }"><CategoryTag :category="row.costCategory" /></template></el-table-column>
              <el-table-column label="阶段" min-width="120"><template #default="{ row }"><StageTag :stage="row.costStage" /></template></el-table-column>
              <el-table-column label="方向" min-width="100"><template #default="{ row }">{{ row.direction === 'DECREASE' ? '减少' : '增加' }}</template></el-table-column>
              <el-table-column label="金额" min-width="140" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.amount, amountRestrictedReason || undefined) }}</span></template></el-table-column>
              <el-table-column label="公共费用行" min-width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ row.publicExpenseLineId || '-' }}</template>
              </el-table-column>
              <el-table-column prop="sourceNo" label="来源" min-width="150" show-overflow-tooltip />
              <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
            </el-table>
          </div>
        </section>
      </div>
    </div>
  </MasterDataTableView>
</template>
