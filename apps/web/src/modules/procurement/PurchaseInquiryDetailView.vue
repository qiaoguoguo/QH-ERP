<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { procurementApi, type ProcurementInquiryDetailRecord, type ResourceId } from '../../shared/api/procurementApi'
import { createIdempotencyKey, documentPlatformApi, type DocumentTaskRecord } from '../../shared/api/documentPlatformApi'
import { useAuthStore } from '../../stores/authStore'
import {
  formatProcurementQuantity,
  procurementErrorMessage,
  procurementModeDisplay,
} from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'
import SupplierQuoteCompareView from './SupplierQuoteCompareView.vue'

const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const record = ref<ProcurementInquiryDetailRecord | null>(null)

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await procurementApi.inquiries.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function importQuotes(event: Event) {
  if (!record.value || actionLoading.value) {
    return
  }
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.imports.uploadProcurementQuotes(record.value.id, {
      file,
      idempotencyKey: createIdempotencyKey('procurement-quote-import'),
    })
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function exportQuotes() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createProcurementQuotes(record.value.id, {
      supplierId: undefined,
      status: undefined,
      idempotencyKey: createIdempotencyKey('procurement-quote-export'),
    })
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadRecord()
})
</script>

<template>
  <section class="procurement-detail-page" v-loading="loading">
    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    <template v-if="record">
      <header class="detail-header">
        <div>
          <h1>{{ record.inquiryNo }}</h1>
          <p>{{ procurementModeDisplay(record.procurementMode, record.projectCode, record.projectName) }}</p>
        </div>
        <div class="state-box">
          <span>业务状态：{{ record.statusName || record.status }}</span>
          <span>供应商 {{ record.supplierCount }} 家 / 报价 {{ record.quoteCount }} 条</span>
          <span class="task-actions">
            <label v-if="authStore.hasPermission('procurement:quote:import')" class="file-action">
              报价导入
              <input data-test="quote-import-file" type="file" accept=".xlsx" @change="importQuotes" />
            </label>
            <el-button
              v-if="authStore.hasPermission('procurement:quote:export')"
              data-test="export-quotes"
              size="small"
              :loading="actionLoading"
              @click="exportQuotes"
            >
              报价导出
            </el-button>
          </span>
        </div>
      </header>
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <ProcurementDocumentTaskPanel :task="latestDocumentTask" />

      <section>
        <h2>询价明细</h2>
        <table class="plain-table">
          <thead>
            <tr>
              <th>物料</th>
              <th>数量</th>
              <th>需求日期</th>
              <th>请购来源</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in record.lines" :key="line.id">
              <td>{{ line.materialCode }} {{ line.materialName }}</td>
              <td>{{ formatProcurementQuantity(line.quantity) }}</td>
              <td>{{ line.requiredDate || '-' }}</td>
              <td>{{ line.requisitionNo || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <SupplierQuoteCompareView :inquiry-id="record.id" />

      <section class="trace-grid">
        <div>
          <h2>来源链</h2>
          <p v-for="source in record.sourceChain ?? []" :key="`${source.sourceType}-${source.sourceNo}`">
            {{ source.sourceNo }} {{ source.summary }}
          </p>
        </div>
        <div>
          <h2>附件</h2>
          <p>询价附件复用 022 平台附件。</p>
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
.trace-grid {
  display: grid;
  gap: 12px;
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

.task-actions {
  align-items: flex-end;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.file-action {
  color: #409eff;
  cursor: pointer;
  font-size: 14px;
}

.file-action input {
  display: none;
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
  vertical-align: top;
}
</style>
