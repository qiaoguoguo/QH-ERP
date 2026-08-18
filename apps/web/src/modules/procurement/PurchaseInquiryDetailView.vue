<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../shared/api/documentPlatformApi'
import {
  procurementApi,
  type ProcurementInquiryDetailRecord,
  type ResourceId,
  type SupplierQuoteRecord,
} from '../../shared/api/procurementApi'
import {
  currentRouteReturnTo,
  queryWithReturnTo,
  returnLocation,
} from '../../shared/navigation/navigationReturn'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import { formatPlatformDateTime } from '../platform/platformPageHelpers'
import AttachmentPanel from '../platform/components/AttachmentPanel.vue'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  formatProcurementQuantity,
  procurementErrorMessage,
  procurementInquiryStatusLabel,
  procurementModeDisplay,
} from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'
import SupplierQuoteCompareView from './SupplierQuoteCompareView.vue'
import SupplierQuoteFormDialog from './SupplierQuoteFormDialog.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const referenceError = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const record = ref<ProcurementInquiryDetailRecord | null>(null)
const suppliers = ref<PartnerRecord[]>([])
const quoteDialogVisible = ref(false)
const editingQuote = ref<SupplierQuoteRecord | null>(null)
const quoteRefreshKey = ref(0)

const pageTitle = computed(() => record.value?.inquiryNo ?? '询价比价详情')
const pageDescription = computed(() => (
  record.value
    ? procurementModeDisplay(record.value.procurementMode, record.value.projectCode, record.value.projectName)
    : '查看询价明细、供应商报价、附件和来源追溯。'
))

function allowed(action: string): boolean {
  return Boolean(record.value?.allowedActions?.includes(action))
}

const canEdit = computed(() => allowed('UPDATE') && authStore.hasPermission('procurement:inquiry:update'))
const canRelease = computed(() => allowed('RELEASE') && authStore.hasPermission('procurement:inquiry:release'))
const canComplete = computed(() => allowed('COMPLETE') && authStore.hasPermission('procurement:inquiry:complete'))
const canCancel = computed(() => allowed('CANCEL') && authStore.hasPermission('procurement:inquiry:cancel'))
const canCreateQuote = computed(() => (
  record.value?.status === 'RELEASED' && authStore.hasPermission('procurement:quote:create')
))
const canImportQuotes = computed(() => (
  record.value?.status === 'RELEASED'
  && authStore.hasPermission('procurement:quote:import')
  && authStore.hasPermission('platform:document-task:create')
))
const canExportQuotes = computed(() => (
  authStore.hasPermission('procurement:quote:export')
  && authStore.hasPermission('platform:document-task:create')
))
const attachmentReadonly = computed(() => (
  record.value?.status !== 'DRAFT' && record.value?.status !== 'RELEASED'
))

const quoteSummary = computed(() => {
  const quotes = record.value?.quotes ?? []
  const supplierCount = new Set(quotes.map((quote) => String(quote.supplierId))).size
  return {
    supplierCount: record.value?.supplierCount ?? supplierCount,
    quoteCount: record.value?.quoteCount ?? quotes.length,
  }
})

const nextStepText = computed(() => {
  switch (record.value?.status) {
    case 'DRAFT':
      return '核对询价明细后发布，发布后才能录入或导入供应商报价。'
    case 'RELEASED':
      return '录入供应商报价；报价收集完成后结束询价。'
    case 'COMPLETED':
      return '比较有效报价并选择最终采购价格来源。'
    case 'AWARDED':
      return '询价已经完成选价，可作为采购订单价格来源。'
    case 'CANCELLED':
      return '询价已取消，仅保留查询和追溯。'
    default:
      return '按当前业务状态处理询价。'
  }
})

const sourceItems = computed(() => {
  if (record.value?.sourceChain?.length) {
    return record.value.sourceChain
  }
  return (record.value?.lines ?? [])
    .filter((line) => line.requisitionNo)
    .map((line) => ({
      sourceType: 'REQUISITION',
      sourceNo: line.requisitionNo || '-',
      sourceId: line.requisitionLineId,
      summary: `请购行 ${line.lineNo} · ${line.materialCode} ${line.materialName}`,
    }))
})

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

async function loadSuppliers() {
  referenceError.value = ''
  try {
    const page = await masterDataApi.suppliers.list({
      keyword: '',
      status: 'ENABLED',
      page: 1,
      pageSize: 200,
    })
    suppliers.value = page.items
  } catch (caught) {
    suppliers.value = []
    referenceError.value = procurementErrorMessage(caught)
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'procurement-inquiries' }))
}

function editInquiry() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-inquiry-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

async function runInquiryAction(action: 'release' | 'complete' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const actionLabels = {
    release: '发布',
    complete: '结束',
    cancel: '取消',
  }
  if (!(await confirmAction(`确认${actionLabels[action]}询价“${record.value.inquiryNo}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    const payload = {
      version: record.value.version,
      idempotencyKey: createIdempotencyKey(`procurement-inquiry-${action}`),
    }
    if (action === 'release') {
      record.value = await procurementApi.inquiries.release(record.value.id, payload)
    } else if (action === 'complete') {
      record.value = await procurementApi.inquiries.complete(record.value.id, payload)
    } else {
      record.value = await procurementApi.inquiries.cancel(record.value.id, payload)
    }
    quoteRefreshKey.value += 1
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

function createQuote() {
  editingQuote.value = null
  quoteDialogVisible.value = true
}

function editQuote(quote: SupplierQuoteRecord) {
  editingQuote.value = quote
  quoteDialogVisible.value = true
}

async function quoteSaved() {
  quoteRefreshKey.value += 1
  await loadRecord()
}

async function importQuotes(event: Event) {
  if (!record.value || !canImportQuotes.value || actionLoading.value) {
    return
  }
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
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
  if (!record.value || !canExportQuotes.value || actionLoading.value) {
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

onMounted(async () => {
  await Promise.all([loadRecord(), loadSuppliers()])
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" :description="pageDescription">
    <template #actions>
      <div class="page-actions">
        <el-button data-test="back-inquiry-list" @click="backToList">返回列表</el-button>
        <el-button v-if="canEdit" data-test="edit-inquiry" @click="editInquiry">编辑</el-button>
        <el-button
          v-if="canRelease"
          data-test="release-inquiry"
          type="primary"
          :loading="actionLoading"
          @click="runInquiryAction('release')"
        >
          发布询价
        </el-button>
        <el-button
          v-if="canCreateQuote"
          data-test="create-supplier-quote"
          type="primary"
          :disabled="actionLoading"
          @click="createQuote"
        >
          新增供应商报价
        </el-button>
        <label v-if="canImportQuotes" class="file-action" :class="{ 'is-disabled': actionLoading }">
          报价导入
          <input data-test="quote-import-file" type="file" accept=".xlsx" :disabled="actionLoading" @change="importQuotes">
        </label>
        <el-button
          v-if="canComplete"
          data-test="complete-inquiry"
          type="success"
          :loading="actionLoading"
          @click="runInquiryAction('complete')"
        >
          结束询价
        </el-button>
        <el-button
          v-if="canExportQuotes"
          data-test="export-quotes"
          :loading="actionLoading"
          @click="exportQuotes"
        >
          报价导出
        </el-button>
        <el-button
          v-if="canCancel"
          data-test="cancel-inquiry"
          type="danger"
          plain
          :loading="actionLoading"
          @click="runInquiryAction('cancel')"
        >
          取消询价
        </el-button>
      </div>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="referenceError" class="page-alert" type="warning" :title="referenceError" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <el-alert v-if="loading" class="page-alert" type="info" title="询价详情加载中" show-icon :closable="false" />
      <ProcurementDocumentTaskPanel :task="latestDocumentTask" />
    </template>

    <template v-if="record">
      <section class="summary-grid" aria-label="询价状态摘要">
        <div class="summary-item">
          <span>业务状态</span>
          <el-tag :type="record.status === 'AWARDED' ? 'success' : record.status === 'CANCELLED' ? 'info' : 'warning'">
            {{ procurementInquiryStatusLabel(record.status, record.statusName) }}
          </el-tag>
        </div>
        <div class="summary-item">
          <span>供应商</span>
          <strong>{{ quoteSummary.supplierCount }} 家</strong>
        </div>
        <div class="summary-item">
          <span>报价</span>
          <strong>{{ quoteSummary.quoteCount }} 条</strong>
        </div>
        <div class="summary-item summary-next-step">
          <span>下一步</span>
          <strong>{{ nextStepText }}</strong>
        </div>
      </section>

      <section class="section-card section-block">
        <div class="section-heading">
          <div>
            <h2>询价明细</h2>
            <p>本次询价仅覆盖以下物料和请购来源。</p>
          </div>
        </div>
        <div class="table-scroll">
          <el-table :data="record.lines" empty-text="暂无询价明细" stripe>
            <el-table-column prop="lineNo" label="行号" width="80" />
            <el-table-column label="物料" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column prop="unitName" label="单位" width="100" />
            <el-table-column label="数量" min-width="120" align="right">
              <template #default="{ row }">{{ formatProcurementQuantity(row.quantity) }}</template>
            </el-table-column>
            <el-table-column prop="requiredDate" label="需求日期" min-width="120">
              <template #default="{ row }">{{ row.requiredDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="请购来源" min-width="210" show-overflow-tooltip>
              <template #default="{ row }">{{ row.requisitionNo || '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <SupplierQuoteCompareView
        :inquiry-id="record.id"
        :inquiry-status="record.status"
        :refresh-key="quoteRefreshKey"
        @edit="editQuote"
        @changed="quoteSaved"
      />

      <div class="detail-grid">
        <section class="section-card section-block">
          <div class="section-heading">
            <div>
              <h2>来源链</h2>
              <p>追溯询价对应的已批准请购。</p>
            </div>
          </div>
          <el-empty v-if="!sourceItems.length" :image-size="54" description="暂无来源记录" />
          <ul v-else class="source-list">
            <li v-for="source in sourceItems" :key="`${source.sourceType}-${source.sourceId ?? source.sourceNo}`">
              <strong>{{ source.sourceNo }}</strong>
              <span>{{ source.summary || '-' }}</span>
            </li>
          </ul>
        </section>

        <section class="section-card section-block">
          <div class="section-heading">
            <div>
              <h2>审计信息</h2>
              <p>记录询价创建和最近更新时间。</p>
            </div>
          </div>
          <dl class="audit-list">
            <div><dt>创建人</dt><dd>{{ record.createdByName || '-' }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ formatPlatformDateTime(record.createdAt) }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ formatPlatformDateTime(record.updatedAt) }}</dd></div>
          </dl>
        </section>
      </div>

      <AttachmentPanel
        object-type="PROCUREMENT_INQUIRY"
        :object-id="record.id"
        title="询价附件"
        :readonly="attachmentReadonly"
      />

      <SupplierQuoteFormDialog
        v-model:visible="quoteDialogVisible"
        :inquiry-id="record.id"
        :lines="record.lines"
        :suppliers="suppliers"
        :quote="editingQuote"
        @saved="quoteSaved"
      />
    </template>
  </MasterDataTableView>
</template>

<style scoped>
.page-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.file-action {
  align-items: center;
  background: var(--el-color-primary);
  border: 1px solid var(--el-color-primary);
  border-radius: var(--el-border-radius-base);
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  font-size: 14px;
  height: 32px;
  padding: 0 15px;
}

.file-action:hover {
  background: var(--el-color-primary-light-3);
  border-color: var(--el-color-primary-light-3);
}

.file-action.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.file-action input {
  display: none;
}

.summary-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(3, minmax(140px, 0.7fr)) minmax(320px, 2fr);
  margin-bottom: 16px;
}

.summary-item {
  align-items: flex-start;
  background: linear-gradient(145deg, #f8fafc, #fff);
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 74px;
  padding: 14px 16px;
}

.summary-item > span {
  color: #667085;
  font-size: 13px;
}

.summary-item strong {
  color: #101828;
  font-size: 15px;
  line-height: 1.5;
}

.summary-next-step {
  border-left: 3px solid var(--el-color-primary);
}

.section-card,
:deep(.quote-compare-view),
:deep(.platform-panel) {
  background: #fff;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  margin-bottom: 16px;
  padding: 16px;
}

.section-heading {
  align-items: flex-start;
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-heading h2 {
  color: #101828;
  font-size: 17px;
  margin: 0;
}

.section-heading p {
  color: #667085;
  font-size: 13px;
  margin: 5px 0 0;
}

.detail-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.6fr);
}

.source-list {
  display: grid;
  gap: 10px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.source-list li {
  background: #f8fafc;
  border-radius: 6px;
  display: grid;
  gap: 4px;
  padding: 11px 12px;
}

.source-list span {
  color: #667085;
  font-size: 13px;
}

.audit-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.audit-list > div {
  border-bottom: 1px solid #eef1f5;
  display: grid;
  gap: 12px;
  grid-template-columns: 76px 1fr;
  padding-bottom: 10px;
}

.audit-list dt {
  color: #667085;
}

.audit-list dd {
  margin: 0;
  text-align: right;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
