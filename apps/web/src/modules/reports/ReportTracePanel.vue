<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import type { ReportTraceRecord } from '../../shared/api/businessReportingApi'
import { currentRouteReturnTo, queryWithReturnTo, safeReturnTo } from '../../shared/navigation/navigationReturn'
import { reportSourceTypeText, reportStatusText } from './reportPageHelpers'

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
const route = useRoute()

function restricted(row: ReportTraceRecord) {
  return row.restricted || !row.canViewResource
}

function viewSource(row: ReportTraceRecord) {
  if (restricted(row) || !row.resourceRouteName) {
    return
  }
  const routeName = typeof route.name === 'string' ? route.name : ''
  const returnTo = routeName.startsWith('reports-')
    ? safeReturnTo(route.fullPath) ?? currentRouteReturnTo(route)
    : currentRouteReturnTo(route)
  void router.push({
    name: row.resourceRouteName,
    params: row.resourceRouteParams ?? {},
    query: queryWithReturnTo(row.resourceRouteQuery, returnTo),
  })
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    :teleported="false"
    direction="rtl"
    size="720px"
    class="report-trace-drawer"
    @close="emit('close')"
  >
    <template #header>
      <div class="report-trace-panel__header">
        <h2>来源追溯</h2>
      </div>
    </template>
    <section v-if="visible" data-test="report-trace-drawer" class="report-trace-panel">
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
              <span v-if="!restricted(row)">{{ reportSourceTypeText(row.sourceType) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="业务日期" min-width="120">
            <template #default="{ row }">
              <span v-if="!restricted(row)">{{ row.businessDate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="110">
            <template #default="{ row }">
              <span v-if="!restricted(row)">{{ reportStatusText(row.status) }}</span>
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
  </el-drawer>
</template>

<style scoped>
.report-trace-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
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
  min-width: 0;
  overflow: auto;
}

.report-state {
  color: var(--qherp-steel);
  padding: 16px 0;
}

.numeric-cell {
  font-variant-numeric: tabular-nums;
}

:deep(.report-trace-drawer) {
  max-width: 92vw;
}

:deep(.report-trace-drawer .el-drawer__body) {
  padding-top: 0;
}
</style>
