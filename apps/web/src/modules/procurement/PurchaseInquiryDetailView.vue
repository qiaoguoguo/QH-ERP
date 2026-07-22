<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { procurementApi, type ProcurementInquiryDetailRecord, type ResourceId } from '../../shared/api/procurementApi'
import { createIdempotencyKey, documentPlatformApi, type DocumentTaskRecord } from '../../shared/api/documentPlatformApi'
import { useAuthStore } from '../../stores/authStore'
import {
  formatProcurementQuantity,
  procurementErrorMessage,
  procurementInquiryStatusLabel,
  procurementModeDisplay,
} from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'
import SupplierQuoteCompareView from './SupplierQuoteCompareView.vue'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'

const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const record = ref<ProcurementInquiryDetailRecord | null>(null)
const pageTitle = computed(() => record.value?.inquiryNo ?? '询价比价详情')
const pageDescription = computed(() => (
  record.value ? procurementModeDisplay(record.value.procurementMode, record.value.projectCode, record.value.projectName) : '查看询价明细、报价比较和文档任务。'
))

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
  <MasterDataTableView :title="pageTitle" :description="pageDescription">
    <template #actions>
      <div v-if="record" class="state-box">
          <span>业务状态：{{ procurementInquiryStatusLabel(record.status, record.statusName) }}</span>
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
    </template>
    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <el-alert v-if="loading" class="page-alert" type="info" title="询价详情加载中" show-icon :closable="false" />
      <ProcurementDocumentTaskPanel :task="latestDocumentTask" />
    </template>

    <template v-if="record">
      <section class="section-block">
        <h2>询价明细</h2>
        <div class="table-scroll">
          <el-table :data="record.lines" empty-text="暂无询价明细" stripe>
            <el-table-column label="物料" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="数量" min-width="110" align="right">
              <template #default="{ row }">{{ formatProcurementQuantity(row.quantity) }}</template>
            </el-table-column>
            <el-table-column prop="requiredDate" label="需求日期" min-width="120" />
            <el-table-column label="请购来源" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">{{ row.requisitionNo || '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
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
  </MasterDataTableView>
</template>

<style scoped>
.trace-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: 1fr auto;
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

.section-block {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  display: grid;
  gap: 12px;
  padding: 14px;
}

.section-block h2 {
  font-size: 16px;
  margin: 0;
}
</style>
