<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { procurementApi, type ProcurementInquirySummaryRecord } from '../../shared/api/procurementApi'
import { createIdempotencyKey, documentPlatformApi, type DocumentTaskRecord } from '../../shared/api/documentPlatformApi'
import { useAuthStore } from '../../stores/authStore'
import { pageItems } from '../system/shared/pageHelpers'
import { procurementErrorMessage, procurementModeDisplay } from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'

const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const records = ref<ProcurementInquirySummaryRecord[]>([])
const filters = reactive({
  keyword: '',
  procurementMode: undefined as 'PUBLIC' | 'PROJECT' | undefined,
  projectId: undefined as string | number | undefined,
  status: undefined as ProcurementInquirySummaryRecord['status'] | undefined,
  page: 1,
  pageSize: 10,
})

const canExport = computed(() => (
  authStore.hasPermission('procurement:inquiry:view')
  && authStore.hasPermission('platform:document-task:create')
  && authStore.hasPermission('procurement:document:export')
))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await procurementApi.inquiries.list({
      keyword: filters.keyword,
      procurementMode: filters.procurementMode,
      projectId: filters.projectId,
      status: filters.status,
      page: filters.page,
      pageSize: filters.pageSize,
    })
    records.value = pageItems(page)
  } catch (caught) {
    records.value = []
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function exportInquiries() {
  if (actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createProcurementInquiries({
      keyword: filters.keyword,
      procurementMode: filters.procurementMode,
      projectId: filters.projectId,
      status: filters.status,
      idempotencyKey: createIdempotencyKey('procurement-inquiry-export'),
    })
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <section class="procurement-list-page">
    <header class="page-header">
      <div>
        <h1>询价比价</h1>
        <p>同一询价范围内收集报价，导入和导出复用文档任务。</p>
      </div>
      <div class="toolbar">
        <el-button
          v-if="canExport"
          data-test="export-inquiries"
          :loading="actionLoading"
          @click="exportInquiries"
        >
          当前筛选导出
        </el-button>
      </div>
    </header>
    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    <ProcurementDocumentTaskPanel :task="latestDocumentTask" />
    <el-empty v-if="!loading && records.length === 0" description="暂无询价" />
    <div class="procurement-table" v-loading="loading">
      <article v-for="record in records" :key="record.id" class="procurement-row">
        <div class="decision-column">
          <strong>{{ record.inquiryNo }}</strong>
          <span>{{ procurementModeDisplay(record.procurementMode, record.projectCode, record.projectName) }}</span>
          <span>{{ record.materialSummary || '物料摘要未返回' }}</span>
        </div>
        <div class="state-column">
          <span>业务状态：{{ record.statusName || record.status }}</span>
          <span>供应商 {{ record.supplierCount }} 家 / 报价 {{ record.quoteCount }} 条</span>
        </div>
        <div class="action-column">
          <router-link :to="{ name: 'procurement-inquiry-detail', params: { id: String(record.id) } }">详情</router-link>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.procurement-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.procurement-row {
  display: grid;
  gap: 12px;
  grid-template-columns: 1fr auto;
}

.page-header h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.page-header p {
  color: #606266;
  margin: 0;
}

.toolbar,
.decision-column,
.state-column,
.action-column {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.procurement-table {
  display: grid;
  gap: 10px;
}

.procurement-row {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  grid-template-columns: minmax(320px, 1.5fr) minmax(220px, 1fr) minmax(120px, auto);
  padding: 12px;
}
</style>
