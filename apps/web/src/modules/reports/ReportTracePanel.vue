<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { ReportTraceRecord } from '../../shared/api/businessReportingApi'

defineProps<{
  visible: boolean
  rows: ReportTraceRecord[]
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
}>()

const router = useRouter()

function restricted(row: ReportTraceRecord) {
  return row.restricted || !row.canViewResource
}

function viewSource(row: ReportTraceRecord) {
  if (restricted(row) || !row.resourceRouteName) {
    return
  }
  void router.push({
    name: row.resourceRouteName,
    params: row.resourceRouteParams ?? {},
    query: row.resourceRouteQuery ?? {},
  })
}
</script>

<template>
  <section v-if="visible" class="report-trace-panel">
    <div class="report-trace-panel__header">
      <h2>来源追溯</h2>
      <el-button link type="primary" @click="emit('close')">关闭</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="report-state">来源追溯加载中</div>
    <el-empty v-else-if="rows.length === 0" description="暂无来源追溯" />
    <div v-else class="report-table-scroll">
      <el-table :data="rows" stripe>
        <el-table-column label="来源单据" min-width="180">
          <template #default="{ row }">
            <span v-if="restricted(row)">{{ row.restrictedMessage || '当前账号没有查看来源详情的权限' }}</span>
            <el-button
              v-else-if="row.resourceRouteName"
              data-test="view-trace-source"
              link
              type="primary"
              @click="viewSource(row)"
            >
              {{ row.sourceNo }}
            </el-button>
            <span v-else>{{ row.sourceNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源类型" min-width="150">
          <template #default="{ row }">
            <span v-if="!restricted(row)">{{ row.sourceType }}</span>
          </template>
        </el-table-column>
        <el-table-column label="业务日期" min-width="120">
          <template #default="{ row }">
            <span v-if="!restricted(row)">{{ row.businessDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }">
            <span v-if="!restricted(row)">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" min-width="110" align="right">
          <template #default="{ row }">
            <span v-if="!restricted(row)" class="numeric-cell">{{ row.quantity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="130" align="right">
          <template #default="{ row }">
            <span v-if="!restricted(row)" class="numeric-cell">{{ row.amount }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<style scoped>
.report-trace-panel {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  margin-top: 16px;
  padding: 14px;
}

.report-trace-panel__header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.report-trace-panel__header h2 {
  font-size: 16px;
  margin: 0;
}

.report-table-scroll {
  overflow-x: auto;
}

.report-state {
  color: #606266;
  padding: 16px 0;
}

.numeric-cell {
  font-variant-numeric: tabular-nums;
}
</style>
