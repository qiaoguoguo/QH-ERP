<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../../shared/api/documentPlatformApi'
import { salesFulfillmentApi, type SalesEffectiveDemandRecord } from '../../../shared/api/salesFulfillmentApi'
import { useAuthStore } from '../../../stores/authStore'
import { pageItems } from '../../system/shared/pageHelpers'
import SalesDocumentTaskPanel from '../SalesDocumentTaskPanel.vue'
import { formatSalesDecimal, optionalSalesId, salesFulfillmentErrorMessage, salesSourceChainLabel } from '../salesFulfillmentPageHelpers'

const authStore = useAuthStore()
const records = ref<SalesEffectiveDemandRecord[]>([])
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const filters = reactive({
  projectId: '' as string | number | '',
  customerId: '' as string | number | '',
  contractId: '' as string | number | '',
  materialId: '' as string | number | '',
  status: undefined as string | undefined,
  expectedDateFrom: '',
  expectedDateTo: '',
  countedOnly: true,
  page: 1,
  pageSize: 10,
})

function canExport() {
  return authStore.hasPermission('platform:document-task:create') && authStore.hasPermission('sales:document:export')
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await salesFulfillmentApi.effectiveDemands.list({
      projectId: optionalSalesId(filters.projectId),
      customerId: optionalSalesId(filters.customerId),
      contractId: optionalSalesId(filters.contractId),
      materialId: optionalSalesId(filters.materialId),
      status: filters.status as never,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      countedOnly: filters.countedOnly,
      page: filters.page,
      pageSize: filters.pageSize,
    })
    records.value = pageItems(page)
  } catch (caught) {
    records.value = []
    error.value = salesFulfillmentErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function diagnose() {
  filters.countedOnly = false
  await loadRecords()
}

async function restoreCountedOnly() {
  filters.countedOnly = true
  await loadRecords()
}

async function exportDemands() {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createSalesEffectiveDemands({
      projectId: optionalSalesId(filters.projectId),
      customerId: optionalSalesId(filters.customerId),
      contractId: optionalSalesId(filters.contractId),
      materialId: optionalSalesId(filters.materialId),
      status: filters.status,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      countedOnly: filters.countedOnly,
      idempotencyKey: createIdempotencyKey('sales-effective-demand-export'),
    })
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <section class="sales-list-page">
    <header class="page-header">
      <div>
        <h1>有效销售需求</h1>
        <p>026 只读消费视图，默认仅显示真实开放需求。</p>
      </div>
      <div class="header-actions">
        <el-button data-test="diagnose-effective-demands" @click="diagnose">诊断未计入候选</el-button>
        <el-button
          v-if="!filters.countedOnly"
          data-test="restore-counted-effective-demands"
          type="primary"
          plain
          @click="restoreCountedOnly"
        >
          恢复默认有效需求
        </el-button>
        <el-button v-if="canExport()" data-test="export-sales-effective-demands" :loading="actionLoading" @click="exportDemands">
          当前筛选导出
        </el-button>
      </div>
    </header>

    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    <el-alert
      v-if="!filters.countedOnly"
      class="page-alert"
      type="warning"
      title="诊断模式：含未计入候选，不能作为 026 有效输入"
      show-icon
      :closable="false"
    />
    <SalesDocumentTaskPanel :task="latestDocumentTask" />

    <el-empty v-if="!loading && records.length === 0" description="暂无有效销售需求" />
    <div class="sales-record-grid" v-loading="loading">
      <article v-for="record in records" :key="record.id" class="sales-record-row">
        <div class="decision-column">
          <strong>{{ record.orderNo }} / {{ record.sourceNo }}</strong>
          <span>{{ record.projectNo }} {{ record.projectName }}</span>
          <span>{{ record.customerName }} / {{ record.contractNo || '合同信息受限' }}</span>
        </div>
        <div class="state-column">
          <span>{{ record.materialCode }} {{ record.materialName }}</span>
          <span>{{ salesSourceChainLabel(Boolean(record.quoteId)) }}</span>
          <span>预计日期：{{ record.expectedDate }}</span>
        </div>
        <div class="progress-column">
          <span>订单 {{ formatSalesDecimal(record.orderQuantity) }} / 计划 {{ formatSalesDecimal(record.plannedQuantity) }}</span>
          <span>已发 {{ formatSalesDecimal(record.shippedQuantity) }} / 退货 {{ formatSalesDecimal(record.returnedQuantity) }}</span>
          <span>开放需求 {{ formatSalesDecimal(record.openQuantity) }}</span>
          <span v-if="!record.countedAsEffectiveDemand">排除原因：{{ record.excludedReasonCode || '未返回' }}</span>
        </div>
        <div class="action-column">
          <span>只读视图</span>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.sales-list-page,
.sales-record-grid {
  display: grid;
  gap: 12px;
}

.page-header,
.header-actions {
  align-items: flex-start;
  display: flex;
  gap: 12px;
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

.sales-record-row {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(250px, 1.2fr) minmax(230px, 1fr) minmax(220px, 1fr) minmax(110px, auto);
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
</style>
