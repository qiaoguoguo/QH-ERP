<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import { procurementApi, type ProcurementRequisitionDetailRecord, type ResourceId } from '../../shared/api/procurementApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import {
  formatProcurementQuantity,
  procurementErrorMessage,
  procurementOwnershipDisplay,
} from './procurementPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const record = ref<ProcurementRequisitionDetailRecord | null>(null)

function allowed(action: string): boolean {
  return Boolean(record.value?.allowedActions?.includes(action))
}

const canSubmitApproval = computed(() => (
  (allowed('SUBMIT_APPROVAL') || allowed('SUBMIT')) && authStore.hasPermission('procurement:requisition:submit')
))
const canCreateInquiry = computed(() => allowed('CREATE_INQUIRY') && authStore.hasPermission('procurement:inquiry:create'))
const canCreateOrder = computed(() => allowed('CREATE_ORDER') && authStore.hasPermission('procurement:order:create'))
const canClose = computed(() => allowed('CLOSE') && authStore.hasPermission('procurement:requisition:close'))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await procurementApi.requisitions.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadRecord()
})

async function submitApproval() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.requisitions.submitApproval(record.value.id, {
      version: record.value.version,
      reason: '提交采购请购审批',
      idempotencyKey: createIdempotencyKey('requisition-submit-approval'),
    })
    await loadRecord()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

async function closeRequisition() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.requisitions.close(record.value.id, {
      version: record.value.version,
      reason: '请购结案',
      idempotencyKey: createIdempotencyKey('requisition-close'),
    })
    await loadRecord()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

function createInquiryFromRequisition() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-inquiry-create',
    query: queryWithReturnTo({ requisitionId: String(record.value.id) }, currentRouteReturnTo(route)),
  })
}

function createOrderFromRequisition() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-order-create',
    query: queryWithReturnTo({ requisitionId: String(record.value.id) }, currentRouteReturnTo(route)),
  })
}
</script>

<template>
  <section class="procurement-detail-page" v-loading="loading">
    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    <template v-if="record">
      <header class="detail-header">
        <div>
          <h1>{{ record.requisitionNo }}</h1>
          <p>{{ procurementOwnershipDisplay(record) }}</p>
        </div>
        <div class="state-box">
          <span>业务状态：{{ record.statusName || record.status }}</span>
          <span>审批状态：{{ record.approvalStatusName || record.approvalStatus || '未提交' }}</span>
        </div>
      </header>

      <div class="action-bar">
        <el-button
          v-if="canSubmitApproval"
          data-test="submit-requisition-approval"
          type="primary"
          :loading="actionLoading"
          :disabled="actionLoading"
          @click="submitApproval"
        >
          提交审批
        </el-button>
        <el-button
          v-if="canCreateInquiry"
          data-test="create-inquiry-from-requisition"
          :disabled="actionLoading"
          @click="createInquiryFromRequisition"
        >
          创建询价
        </el-button>
        <el-button
          v-if="canCreateOrder"
          data-test="create-order-from-requisition"
          :disabled="actionLoading"
          @click="createOrderFromRequisition"
        >
          转采购订单
        </el-button>
        <el-button
          v-if="canClose"
          data-test="close-requisition"
          type="warning"
          :loading="actionLoading"
          :disabled="actionLoading"
          @click="closeRequisition"
        >
          结案
        </el-button>
      </div>

      <section class="summary-grid">
        <div>物料摘要：{{ record.materialSummary || '-' }}</div>
        <div>需求日期：{{ record.requiredDate }}</div>
        <div>
          计划/已转/剩余：{{ formatProcurementQuantity(record.totalQuantity) }}/{{
            formatProcurementQuantity(record.orderedQuantity)
          }}/{{ formatProcurementQuantity(record.remainingQuantity) }}
        </div>
        <div>结案原因：{{ record.closeReason || '未结案' }}</div>
      </section>

      <section>
        <h2>请购明细</h2>
        <table class="plain-table">
          <thead>
            <tr>
              <th>行号</th>
              <th>物料</th>
              <th>项目/公共</th>
              <th>数量</th>
              <th>已转</th>
              <th>剩余</th>
              <th>用途</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in record.lines" :key="line.id">
              <td>{{ line.lineNo }}</td>
              <td>{{ line.materialCode }} {{ line.materialName }}</td>
              <td>{{ procurementOwnershipDisplay(line) }}</td>
              <td>{{ formatProcurementQuantity(line.quantity) }}</td>
              <td>{{ formatProcurementQuantity(line.orderedQuantity) }}</td>
              <td>{{ formatProcurementQuantity(line.remainingQuantity) }}</td>
              <td>{{ line.purpose || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="trace-grid">
        <div>
          <h2>来源链</h2>
          <p v-for="source in record.sourceChain ?? []" :key="`${source.sourceType}-${source.sourceNo}`">
            {{ source.sourceNo }} {{ source.summary }}
          </p>
          <p v-if="!(record.sourceChain ?? []).length">暂无来源链</p>
        </div>
        <div>
          <h2>审批</h2>
          <p>审批状态：{{ record.approvalStatusName || record.approvalStatus || '未提交' }}</p>
        </div>
        <div>
          <h2>附件</h2>
          <p>附件随 022 平台能力展示。</p>
        </div>
        <div>
          <h2>审计</h2>
          <p>创建人：{{ record.createdByName }}，更新时间：{{ record.updatedAt }}</p>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.procurement-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-header,
.summary-grid,
.trace-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
}

.detail-header {
  align-items: start;
  grid-template-columns: 1fr auto;
}

.detail-header h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.state-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-align: right;
}

.action-bar {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.plain-table {
  border-collapse: collapse;
  width: 100%;
}

.plain-table th,
.plain-table td {
  border-bottom: 1px solid #ebeef5;
  padding: 8px;
  text-align: left;
}
</style>
