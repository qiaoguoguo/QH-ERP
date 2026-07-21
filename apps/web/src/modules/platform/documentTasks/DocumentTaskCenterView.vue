<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, reactive, ref, watch } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
  type DocumentTaskStatus,
  type DocumentTaskType,
  type ImportFailureRecord,
} from '../../../shared/api/documentPlatformApi'
import { platformGovernanceApi } from '../../../shared/api/platformGovernanceApi'
import { pageItems } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { downloadFile } from '../../../shared/file/download'
import { isDocumentTaskTerminalStatus, useDocumentTaskPolling } from '../../../shared/composables/useDocumentTaskPolling'
import {
  documentTaskStageLabel,
  documentTaskStatusLabel,
  documentTaskStatusTagType,
  documentTaskTypeLabel,
  formatPlatformDateTime,
  platformErrorMessage,
} from '../platformPageHelpers'
import { confirmAction } from '../../../shared/ui/confirmDialog'

const filters = reactive<{
  keyword: string
  domain: string
  taskType: DocumentTaskType | ''
  objectKeyword: string
  createdByKeyword: string
  createdAtRange: [string, string] | []
  status: DocumentTaskStatus | ''
}>({
  keyword: '',
  domain: '',
  taskType: '',
  objectKeyword: '',
  createdByKeyword: '',
  createdAtRange: [],
  status: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<DocumentTaskRecord[]>([])
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const errorDrawerVisible = ref(false)
const selectedTask = ref<DocumentTaskRecord | null>(null)
const failureRows = ref<ImportFailureRecord[]>([])
const failurePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const pollingTaskIds = computed(() => records.value
  .filter((item) => !isDocumentTaskTerminalStatus(item.status))
  .map((item) => item.id))
const polling = useDocumentTaskPolling(pollingTaskIds, (id) => documentPlatformApi.documentTasks.get(id), { intervalMs: 2500 })
const hasRunningTask = computed(() => records.value.some((item) => !isDocumentTaskTerminalStatus(item.status)))
const routeProxy = getCurrentInstance()?.proxy as unknown as { $route?: { query?: Record<string, unknown> } } | null

const taskTypeOptions: DocumentTaskType[] = [
  'MATERIAL_IMPORT',
  'MATERIAL_EXPORT',
  'BOM_DRAFT_IMPORT',
  'BOM_DRAFT_EXPORT',
  'APPROVAL_PRINT',
  'PROCUREMENT_REQUISITION_EXPORT',
  'PROCUREMENT_INQUIRY_EXPORT',
  'PROCUREMENT_QUOTE_IMPORT',
  'PROCUREMENT_QUOTE_EXPORT',
  'PROCUREMENT_PRICE_AGREEMENT_EXPORT',
  'PROCUREMENT_ORDER_EXPORT',
  'PROCUREMENT_ORDER_PRINT',
  'PROCUREMENT_SCHEDULE_EXPORT',
  'PROCUREMENT_SUPPLY_EXPORT',
  'SALES_QUOTE_PRINT',
  'SALES_QUOTE_EXPORT',
  'SALES_DELIVERY_PLAN_EXPORT',
  'SALES_EFFECTIVE_DEMAND_EXPORT',
  'MATERIAL_REQUIREMENT_RUN_EXPORT',
  'DATA_REPAIR_EXECUTE',
  'HISTORY_IMPORT_CUSTOMER',
  'HISTORY_IMPORT_SUPPLIER',
  'HISTORY_IMPORT_MATERIAL',
  'HISTORY_IMPORT_BOM_DRAFT',
  'HISTORY_IMPORT_SALES_PROJECT',
  'CUSTOMER_MASTER_V1_HISTORY_IMPORT',
  'SUPPLIER_MASTER_V1_HISTORY_IMPORT',
  'MATERIAL_MASTER_V1_HISTORY_IMPORT',
  'BOM_DRAFT_V1_HISTORY_IMPORT',
  'SALES_PROJECT_V1_HISTORY_IMPORT',
  'BATCH_CUSTOMER_STATUS_CHANGE',
  'BATCH_SUPPLIER_STATUS_CHANGE',
  'BATCH_MATERIAL_STATUS_CHANGE',
  'FIXED_DOCUMENT_BATCH_PRINT',
  'SALES_ORDER_PRINT',
  'SALES_SHIPMENT_PRINT',
  'PROCUREMENT_RECEIPT_PRINT',
  'INVENTORY_TRANSFER_PRINT',
  'PRODUCTION_WORK_ORDER_PRINT',
  'PRODUCTION_MATERIAL_ISSUE_PRINT',
  'PRODUCTION_COMPLETION_RECEIPT_PRINT',
  'SALES_INVOICE_PRINT',
  'PURCHASE_INVOICE_PRINT',
  'ACCOUNTING_VOUCHER_PRINT',
]
const statusOptions: DocumentTaskStatus[] = [
  'QUEUED',
  'RUNNING',
  'READY_TO_COMMIT',
  'VALIDATION_FAILED',
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
  'EXPIRED',
]
const domainOptions = [
  { value: 'IMPORT_EXPORT', label: '导入导出' },
  { value: 'PRINT', label: '固定打印' },
  { value: 'DATA_REPAIR', label: '数据修复' },
  { value: 'HISTORY_IMPORT', label: '历史导入' },
  { value: 'BATCH_TOOL', label: '批量工具' },
]

function queryValue(name: string): string | undefined {
  const routeValue = routeProxy?.$route?.query?.[name]
  const normalizedRouteValue = Array.isArray(routeValue) ? routeValue[0] : routeValue
  if (normalizedRouteValue !== undefined && normalizedRouteValue !== null && String(normalizedRouteValue).trim()) {
    return String(normalizedRouteValue)
  }
  const urlValue = new URLSearchParams(window.location.search).get(name)
  return urlValue && urlValue.trim() ? urlValue : undefined
}

const taskIdFilter = computed(() => queryValue('taskId'))
const batchOperationIdFilter = computed(() => queryValue('batchOperationId'))
const routeQuerySignature = computed(() => `${taskIdFilter.value ?? ''}|${batchOperationIdFilter.value ?? ''}`)

watch(() => polling.latestTasks.value, (tasks) => {
  if (!tasks.length) {
    return
  }
  const taskMap = new Map(tasks.map((task) => [String(task.id), task]))
  records.value = records.value.map((record) => taskMap.get(String(record.id)) ?? record)
  syncPolling()
})

watch(pollingTaskIds, () => {
  syncPolling()
})

function syncPolling() {
  if (pollingTaskIds.value.length) {
    polling.start()
  } else {
    polling.stop()
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await documentPlatformApi.documentTasks.list({
      taskId: taskIdFilter.value,
      batchOperationId: batchOperationIdFilter.value,
      keyword: filters.keyword,
      domain: filters.domain,
      taskType: filters.taskType || undefined,
      objectKeyword: filters.objectKeyword,
      createdByKeyword: filters.createdByKeyword,
      createdAtFrom: filters.createdAtRange[0] ?? '',
      createdAtTo: filters.createdAtRange[1] ?? '',
      status: filters.status || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
    syncPolling()
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function isHistoryImportTask(record: DocumentTaskRecord): boolean {
  const taskType = String(record.taskType ?? '')
  return record.businessDomain === 'HISTORY_IMPORT'
    || taskType.startsWith('HISTORY_IMPORT_')
    || taskType.endsWith('_HISTORY_IMPORT')
}

function resetFilters() {
  filters.keyword = ''
  filters.domain = ''
  filters.taskType = ''
  filters.objectKeyword = ''
  filters.createdByKeyword = ''
  filters.createdAtRange = []
  filters.status = ''
  pagination.page = 1
  void loadRecords()
}

async function openErrors(record: DocumentTaskRecord) {
  selectedTask.value = record
  errorDrawerVisible.value = true
  failurePagination.page = 1
  await loadFailureRows()
}

async function loadFailureRows() {
  if (!selectedTask.value) {
    return
  }
  actionError.value = ''
  try {
    const page = await documentPlatformApi.documentTasks.errors(selectedTask.value.id, {
      page: failurePagination.page,
      pageSize: failurePagination.pageSize,
    })
    failureRows.value = pageItems(page)
    failurePagination.total = Number(page.total)
  } catch (caught) {
    failureRows.value = []
    failurePagination.total = 0
    actionError.value = platformErrorMessage(caught)
  }
}

async function downloadTask(record: DocumentTaskRecord) {
  actionLoading.value = true
  actionError.value = ''
  try {
    downloadFile(await documentPlatformApi.documentTasks.download(record.id))
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function confirmTask(record: DocumentTaskRecord) {
  if (!(await confirmAction(`确认提交任务“${record.taskNo}”？确认后将按服务端预检和版本执行。`, { title: '确认任务', risk: 'warning' }))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    if (isHistoryImportTask(record)) {
      await platformGovernanceApi.historyImports.confirm(record.id, {
        version: record.version,
        idempotencyKey: createIdempotencyKey('history-import-confirm'),
      })
    } else {
      await documentPlatformApi.imports.confirm(record.id, {
        version: record.version,
        idempotencyKey: createIdempotencyKey('document-task-confirm'),
      })
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function cancelTask(record: DocumentTaskRecord) {
  if (!(await confirmAction(`确认取消任务“${record.taskNo}”？`, { title: '取消任务', risk: 'warning' }))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    if (isHistoryImportTask(record)) {
      await platformGovernanceApi.historyImports.cancel(record.id, {
        version: record.version,
        idempotencyKey: createIdempotencyKey('history-import-cancel'),
      })
    } else {
      await documentPlatformApi.documentTasks.cancel(record.id, { version: record.version, reason: '用户取消' })
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

function confirmActionLabel(record: DocumentTaskRecord): string {
  return record.taskType.endsWith('_IMPORT') ? '确认导入' : '确认提交'
}

function businessDomainLabel(domain?: string | null): string {
  return domainOptions.find((item) => item.value === domain)?.label ?? domain ?? '-'
}

onMounted(() => {
  void loadRecords()
})

watch(routeQuerySignature, () => {
  pagination.page = 1
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="任务中心" description="查看导入、导出和固定打印任务。">
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="document-task-keyword" clearable placeholder="任务号或对象" />
        </el-form-item>
        <el-form-item label="业务域">
          <el-select v-model="filters.domain" clearable placeholder="全部业务域">
            <el-option v-for="domain in domainOptions" :key="domain.value" :label="domain.label" :value="domain.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.taskType" clearable filterable placeholder="全部类型">
            <el-option v-for="type in taskTypeOptions" :key="type" :label="documentTaskTypeLabel(type)" :value="type" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象">
          <el-input v-model="filters.objectKeyword" name="document-task-object-keyword" clearable placeholder="对象编号或名称" />
        </el-form-item>
        <el-form-item label="发起人">
          <el-input v-model="filters.createdByKeyword" name="document-task-created-by" clearable placeholder="发起人姓名" />
        </el-form-item>
        <el-form-item label="创建日期">
          <el-date-picker
            v-model="filters.createdAtRange"
            name="document-task-created-at-range"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            value-on-clear=""
            clearable
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option v-for="status in statusOptions" :key="status" :label="documentTaskStatusLabel(status)" :value="status" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-document-tasks" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-document-tasks" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="hasRunningTask" class="state-alert" type="info" title="存在执行中的文档任务，页面会自动刷新状态。" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无文档任务'" stripe>
        <el-table-column prop="taskNo" label="任务号" width="150" show-overflow-tooltip />
        <el-table-column label="业务域" width="110">
          <template #default="{ row }">{{ businessDomainLabel(row.businessDomain) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">{{ documentTaskTypeLabel(row.taskType) }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.objectNo || '-' }} {{ row.objectName || '' }}</template>
        </el-table-column>
        <el-table-column label="阶段" width="90">
          <template #default="{ row }">{{ documentTaskStageLabel(row.stage) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="documentTaskStatusTagType(row.status)" size="small">{{ documentTaskStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.progressPercent ?? 0)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column prop="totalRows" label="总行数" width="90" />
        <el-table-column prop="successRows" label="成功数" width="90" />
        <el-table-column prop="failedRows" label="错误数" width="90" />
        <el-table-column prop="createdByName" label="发起人" width="110" show-overflow-tooltip />
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatPlatformDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="160">
          <template #default="{ row }">{{ formatPlatformDateTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column label="结果过期" width="160">
          <template #default="{ row }">{{ formatPlatformDateTime(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="230">
          <template #default="{ row }">
            <el-button
              v-if="(row.availableActions ?? []).includes('CONFIRM')"
              data-test="confirm-document-task"
              size="small"
              text
              type="primary"
              :disabled="actionLoading"
              @click="confirmTask(row)"
            >
              {{ confirmActionLabel(row) }}
            </el-button>
            <el-button
              v-if="(row.availableActions ?? []).includes('ERRORS')"
              data-test="view-task-errors"
              size="small"
              text
              @click="openErrors(row)"
            >
              错误明细
            </el-button>
            <el-button
              v-if="row.status !== 'EXPIRED' && (row.availableActions ?? []).includes('DOWNLOAD')"
              data-test="download-task-result"
              size="small"
              text
              :disabled="actionLoading"
              @click="downloadTask(row)"
            >
              下载结果
            </el-button>
            <el-button
              v-if="(row.availableActions ?? []).includes('CANCEL')"
              data-test="cancel-document-task"
              size="small"
              text
              type="danger"
              :disabled="actionLoading"
              @click="cancelTask(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />

    <el-drawer v-model="errorDrawerVisible" title="导入错误明细" size="min(720px, 92vw)">
      <div class="table-scroll">
        <el-table :data="failureRows" :empty-text="'暂无错误明细'" stripe>
          <el-table-column prop="rowNo" label="行号" width="80" />
          <el-table-column prop="columnName" label="列名" width="140" show-overflow-tooltip />
          <el-table-column prop="code" label="错误码" width="180" show-overflow-tooltip />
          <el-table-column prop="message" label="错误" min-width="220" show-overflow-tooltip />
          <el-table-column prop="suggestion" label="建议" min-width="180" show-overflow-tooltip />
        </el-table>
      </div>
      <el-pagination
        class="table-pagination"
        layout="total, prev, pager, next"
        :total="failurePagination.total"
        :page-size="failurePagination.pageSize"
        :current-page="failurePagination.page"
        @current-change="(page: number) => { failurePagination.page = page; void loadFailureRows() }"
      />
    </el-drawer>
  </MasterDataTableView>
</template>
