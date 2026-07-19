<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { ProjectCostSourceRecord } from '../../../shared/api/projectCostApi'
import { currentRouteReturnTo } from '../../../shared/navigation/navigationReturn'
import CategoryTag from './CategoryTag.vue'
import SourceStatusTag from './SourceStatusTag.vue'
import StageTag from './StageTag.vue'
import {
  formatProjectCostAmount,
  formatProjectCostQuantity,
  projectCostMessages,
  projectCostSourceTypeLabel,
  restrictedMoneyReason,
  restrictedSourceReason,
  sourceRouteLocation,
} from './projectCostPageHelpers'

const props = defineProps<{
  modelValue: boolean
  sources: ProjectCostSourceRecord[]
  title?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const route = useRoute()
const router = useRouter()
const drawerVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

function sourceNoText(row: ProjectCostSourceRecord) {
  const restrictedReason = restrictedSourceReason(row)
  if (restrictedReason) {
    return restrictedReason
  }
  return row.sourceNo || row.sourceSummary || '-'
}

function sourceAmountText(row: ProjectCostSourceRecord) {
  return formatProjectCostAmount(row.sourceAmount, row.amountVisible === false ? projectCostMessages.amountForbidden : restrictedMoneyReason(row) || undefined)
}

function rowMaterialText(row: ProjectCostSourceRecord) {
  return `${row.materialCode ?? ''} ${row.materialName ?? ''}`.trim() || row.sourceSummary || '-'
}

function viewSource(row: ProjectCostSourceRecord) {
  const target = sourceRouteLocation(row.sourceRoute, currentRouteReturnTo(route))
  if (target) {
    void router.push(target)
  }
}
</script>

<template>
  <el-drawer v-model="drawerVisible" :title="title || '来源追溯'" size="min(880px, 94vw)" :append-to-body="false">
    <div class="project-cost-drawer-body">
      <el-empty v-if="sources.length === 0" description="暂无来源追溯" />
      <div v-else class="table-scroll project-cost-source-table-scroll">
        <el-table :data="sources" empty-text="暂无来源追溯" stripe>
          <el-table-column label="分类" min-width="100">
            <template #default="{ row }"><CategoryTag :category="row.category" /></template>
          </el-table-column>
          <el-table-column label="阶段" min-width="100">
            <template #default="{ row }"><StageTag :stage="row.stage" /></template>
          </el-table-column>
          <el-table-column label="来源状态" min-width="110">
            <template #default="{ row }"><SourceStatusTag :status="row.sourceStatus" /></template>
          </el-table-column>
          <el-table-column label="来源类型" min-width="120">
            <template #default="{ row }">{{ projectCostSourceTypeLabel(row.sourceType) }}</template>
          </el-table-column>
          <el-table-column label="来源单据" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button
                v-if="row.sourceVisible !== false && row.sourceRoute"
                link
                type="primary"
                data-test="view-project-cost-source"
                @click="viewSource(row)"
              >
                {{ sourceNoText(row) }}
              </el-button>
              <span v-else>{{ sourceNoText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="业务日期" min-width="110">
            <template #default="{ row }">{{ row.businessDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="物料" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">{{ row.sourceVisible === false ? projectCostMessages.sourceRestricted : rowMaterialText(row) }}</template>
          </el-table-column>
          <el-table-column label="数量" min-width="120" align="right">
            <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostQuantity(row.quantity) }}</span></template>
          </el-table-column>
          <el-table-column label="单价" min-width="130" align="right">
            <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.unitPrice, row.amountVisible === false ? projectCostMessages.amountForbidden : restrictedMoneyReason(row) || undefined) }}</span></template>
          </el-table-column>
          <el-table-column label="来源金额" min-width="140" align="right">
            <template #default="{ row }"><span class="numeric-cell">{{ sourceAmountText(row) }}</span></template>
          </el-table-column>
        </el-table>
      </div>
    </div>
    <template #footer>
      <div class="project-cost-drawer-footer">
        <el-button @click="drawerVisible = false">关闭</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.project-cost-drawer-body {
  max-height: calc(100vh - 150px);
  overflow: auto;
}

.project-cost-source-table-scroll {
  max-width: 100%;
}

.project-cost-drawer-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
