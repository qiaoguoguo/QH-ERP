<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../shared/api/documentPlatformApi'
import { procurementApi, type ProcurementRequisitionSummaryRecord } from '../../shared/api/procurementApi'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatProcurementQuantity,
  procurementErrorMessage,
  procurementOwnershipDisplay,
} from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'
import { useAuthStore } from '../../stores/authStore'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const records = ref<ProcurementRequisitionSummaryRecord[]>([])
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const filters = reactive({
  keyword: '',
  procurementMode: undefined as 'PUBLIC' | 'PROJECT' | undefined,
  projectId: undefined as string | number | undefined,
  status: undefined as ProcurementRequisitionSummaryRecord['status'] | undefined,
  approvalStatus: undefined as string | undefined,
  requiredDateFrom: '',
  requiredDateTo: '',
  page: 1,
  pageSize: 10,
})

const canExport = computed(() => (
  authStore.hasPermission('procurement:requisition:view')
  && authStore.hasPermission('platform:document-task:create')
  && authStore.hasPermission('procurement:document:export')
))

function allowed(record: ProcurementRequisitionSummaryRecord, action: string): boolean {
  return (record.allowedActions ?? []).includes(action)
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await procurementApi.requisitions.list({
      keyword: filters.keyword,
      procurementMode: filters.procurementMode,
      projectId: filters.projectId,
      status: filters.status,
      approvalStatus: filters.approvalStatus,
      requiredDateFrom: filters.requiredDateFrom,
      requiredDateTo: filters.requiredDateTo,
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

function viewRecord(record: ProcurementRequisitionSummaryRecord) {
  void router.push({ name: 'procurement-requisition-detail', params: { id: String(record.id) } })
}

function createInquiryFromRecord(record: ProcurementRequisitionSummaryRecord) {
  void router.push({
    name: 'procurement-inquiry-create',
    query: queryWithReturnTo({ requisitionId: String(record.id) }, currentRouteReturnTo(route)),
  })
}

function createOrderFromRecord(record: ProcurementRequisitionSummaryRecord) {
  void router.push({
    name: 'procurement-order-create',
    query: queryWithReturnTo({ requisitionId: String(record.id) }, currentRouteReturnTo(route)),
  })
}

async function closeRecord(record: ProcurementRequisitionSummaryRecord) {
  if (actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.requisitions.close(record.id, {
      version: record.version,
      reason: '请购结案',
      idempotencyKey: createIdempotencyKey('requisition-close'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

async function exportRequisitions() {
  if (actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createProcurementRequisitions({
      keyword: filters.keyword,
      procurementMode: filters.procurementMode,
      projectId: filters.projectId,
      status: filters.status,
      approvalStatus: filters.approvalStatus,
      requiredDateFrom: filters.requiredDateFrom,
      requiredDateTo: filters.requiredDateTo,
      idempotencyKey: createIdempotencyKey('procurement-requisition-export'),
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
  <section class="procurement-page procurement-list-page">
    <header class="page-header">
      <div>
        <h1>采购请购</h1>
        <p>项目专采与公共采购需求入口，审批状态和业务状态分开展示。</p>
      </div>
      <el-button data-test="create-requisition" type="primary" @click="router.push({ name: 'procurement-requisition-create' })">
        新建请购
      </el-button>
      <el-button v-if="canExport" data-test="export-requisitions" :loading="actionLoading" @click="exportRequisitions">
        当前筛选导出
      </el-button>
    </header>

    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    <ProcurementDocumentTaskPanel :task="latestDocumentTask" />
    <el-empty v-if="!loading && records.length === 0" description="暂无采购请购" />

    <div class="procurement-table" v-loading="loading">
      <article v-for="record in records" :key="record.id" class="procurement-row">
        <div class="decision-column">
          <strong>{{ record.requisitionNo }}</strong>
          <span>{{ procurementOwnershipDisplay(record) }}</span>
          <span>{{ record.materialSummary || '物料摘要未返回' }}</span>
        </div>
        <div class="state-column">
          <span>业务状态：{{ record.statusName || record.status }}</span>
          <span>审批状态：{{ record.approvalStatusName || record.approvalStatus || '未提交' }}</span>
          <span>需求日期：{{ record.requiredDate }}</span>
        </div>
        <div class="progress-column">
          <span>
            计划/已转/剩余：{{ formatProcurementQuantity(record.totalQuantity) }}/{{
              formatProcurementQuantity(record.orderedQuantity)
            }}/{{ formatProcurementQuantity(record.remainingQuantity) }}
          </span>
          <span v-if="record.closeReason">结案原因：{{ record.closeReason }}</span>
        </div>
        <div class="action-column">
          <el-button text type="primary" @click="viewRecord(record)">详情</el-button>
          <el-button
            v-if="allowed(record, 'CREATE_INQUIRY')"
            data-test="create-inquiry-from-requisition-list"
            text
            type="primary"
            @click="createInquiryFromRecord(record)"
          >
            创建询价
          </el-button>
          <el-button
            v-if="allowed(record, 'CREATE_ORDER')"
            data-test="create-order-from-requisition-list"
            text
            type="primary"
            @click="createOrderFromRecord(record)"
          >
            转采购订单
          </el-button>
          <el-button
            v-if="allowed(record, 'CLOSE')"
            data-test="close-requisition-list"
            text
            type="warning"
            :disabled="actionLoading"
            @click="closeRecord(record)"
          >
            结案
          </el-button>
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

.page-header {
  align-items: flex-start;
  display: flex;
  gap: 16px;
  justify-content: space-between;
}

.page-header h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.page-header p {
  color: #606266;
  margin: 0;
}

.procurement-table {
  display: grid;
  gap: 10px;
}

.procurement-row {
  align-items: start;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(260px, 1.4fr) minmax(180px, 1fr) minmax(240px, 1fr) minmax(180px, auto);
  padding: 12px;
}

.decision-column,
.state-column,
.progress-column,
.action-column {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.action-column {
  align-items: flex-end;
  position: sticky;
  right: 0;
}

@media (max-width: 1280px) {
  .procurement-row {
    grid-template-columns: minmax(240px, 1fr) minmax(180px, 1fr) minmax(220px, 1fr) minmax(160px, auto);
  }
}
</style>
