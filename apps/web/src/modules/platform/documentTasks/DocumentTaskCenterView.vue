<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
  type DocumentTaskType,
  type ImportFailureRecord,
} from '../../../shared/api/documentPlatformApi'
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

const filters = reactive<{ keyword: string; taskType?: DocumentTaskType; status?: string }>({ keyword: '', taskType: undefined, status: undefined })
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
      keyword: filters.keyword,
      taskType: filters.taskType,
      status: filters.status as never,
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
  actionLoading.value = true
  actionError.value = ''
  try {
    await documentPlatformApi.imports.confirm(record.id, {
      version: record.version,
      idempotencyKey: createIdempotencyKey('document-task-confirm'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function cancelTask(record: DocumentTaskRecord) {
  actionLoading.value = true
  actionError.value = ''
  try {
    await documentPlatformApi.documentTasks.cancel(record.id, { version: record.version, reason: '用户取消' })
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

onMounted(() => {
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
        <el-form-item label="类型">
          <el-select v-model="filters.taskType" clearable placeholder="全部类型">
            <el-option label="物料导入" value="MATERIAL_IMPORT" />
            <el-option label="物料导出" value="MATERIAL_EXPORT" />
            <el-option label="BOM 草稿导入" value="BOM_DRAFT_IMPORT" />
            <el-option label="BOM 草稿导出" value="BOM_DRAFT_EXPORT" />
            <el-option label="审批单打印" value="APPROVAL_PRINT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-document-tasks" type="primary" @click="search">查询</el-button>
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
        <el-table-column label="类型" width="130">
          <template #default="{ row }">{{ documentTaskTypeLabel(row.taskType) }}</template>
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
        <el-table-column prop="failedRows" label="错误数" width="90" />
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
      :page-sizes="[10, 20, 50]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />

    <el-drawer v-model="errorDrawerVisible" title="导入错误明细" size="min(720px, 92vw)">
      <el-table :data="failureRows" :empty-text="'暂无错误明细'" stripe>
        <el-table-column prop="rowNo" label="行号" width="80" />
        <el-table-column prop="columnName" label="列名" width="140" show-overflow-tooltip />
        <el-table-column prop="code" label="错误码" width="180" show-overflow-tooltip />
        <el-table-column prop="message" label="错误" min-width="220" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="建议" min-width="180" show-overflow-tooltip />
      </el-table>
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
