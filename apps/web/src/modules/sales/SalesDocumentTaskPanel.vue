<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskAction,
  type DocumentTaskRecord,
  type ImportFailureRecord,
} from '../../shared/api/documentPlatformApi'
import { isDocumentTaskTerminalStatus, useDocumentTaskPolling } from '../../shared/composables/useDocumentTaskPolling'
import { downloadFile } from '../../shared/file/download'
import { pageItems } from '../system/shared/pageHelpers'
import {
  documentTaskStageLabel,
  documentTaskStatusLabel,
  documentTaskStatusTagType,
  documentTaskTypeLabel,
  platformErrorMessage,
} from '../platform/platformPageHelpers'

const props = defineProps<{ task?: DocumentTaskRecord | null }>()
const currentTask = ref<DocumentTaskRecord | null>(props.task ?? null)
const actionError = ref('')
const actionLoading = ref(false)
const errorDrawerVisible = ref(false)
const failureRows = ref<ImportFailureRecord[]>([])
const failurePagination = ref({ page: 1, pageSize: 10, total: 0 })

const pollingTaskId = computed(() => {
  if (!currentTask.value || isDocumentTaskTerminalStatus(currentTask.value.status)) {
    return null
  }
  return currentTask.value.id
})
const polling = useDocumentTaskPolling(pollingTaskId, (taskId) => documentPlatformApi.documentTasks.get(taskId), { intervalMs: 2500 })
const taskActions = computed(() => currentTask.value?.availableActions ?? [])
const isExpired = computed(() => currentTask.value?.status === 'EXPIRED')
const shouldShowDownload = computed(() => !isExpired.value && taskActions.value.includes('DOWNLOAD'))

watch(() => props.task, (task) => {
  currentTask.value = task ?? null
}, { immediate: true })

watch(pollingTaskId, (taskId) => {
  if (taskId !== null) {
    polling.start()
  } else {
    polling.stop()
  }
}, { immediate: true })

watch(() => polling.latestTask.value, (task) => {
  if (task) {
    currentTask.value = task
  }
})

function hasAction(action: DocumentTaskAction) {
  return taskActions.value.includes(action)
}

async function openErrors() {
  if (!currentTask.value) {
    return
  }
  errorDrawerVisible.value = true
  actionError.value = ''
  try {
    const page = await documentPlatformApi.documentTasks.errors(currentTask.value.id, {
      page: failurePagination.value.page,
      pageSize: failurePagination.value.pageSize,
    })
    failureRows.value = pageItems(page)
    failurePagination.value.total = Number(page.total)
  } catch (caught) {
    failureRows.value = []
    failurePagination.value.total = 0
    actionError.value = platformErrorMessage(caught)
  }
}

async function downloadTask() {
  if (!currentTask.value || actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    downloadFile(await documentPlatformApi.documentTasks.download(currentTask.value.id))
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function confirmTask() {
  if (!currentTask.value || actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    currentTask.value = await documentPlatformApi.imports.confirm(currentTask.value.id, {
      version: currentTask.value.version,
      idempotencyKey: createIdempotencyKey('sales-document-confirm'),
    })
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function cancelTask() {
  if (!currentTask.value || actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    currentTask.value = await documentPlatformApi.documentTasks.cancel(currentTask.value.id, {
      version: currentTask.value.version,
      reason: '用户取消',
    })
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}
</script>

<template>
  <section v-if="currentTask" class="sales-document-task-panel">
    <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    <div class="task-summary">
      <div>
        <strong>{{ currentTask.taskNo }}</strong>
        <span>{{ documentTaskTypeLabel(currentTask.taskType) }} / {{ documentTaskStageLabel(currentTask.stage) }}</span>
        <span v-if="pollingTaskId">执行中，自动刷新状态</span>
        <span v-if="isExpired" class="task-expired">结果已过期，请重新发起文档任务</span>
      </div>
      <div class="task-state">
        <el-tag :type="documentTaskStatusTagType(currentTask.status)" size="small">
          {{ documentTaskStatusLabel(currentTask.status) }}
        </el-tag>
        <span>总行数 {{ currentTask.totalRows ?? 0 }} / 失败 {{ currentTask.failedRows ?? 0 }}</span>
      </div>
      <div class="task-actions">
        <el-button v-if="hasAction('CONFIRM')" size="small" type="primary" :loading="actionLoading" @click="confirmTask">
          确认导入
        </el-button>
        <el-button v-if="hasAction('ERRORS')" size="small" @click="openErrors">错误明细</el-button>
        <el-button v-if="shouldShowDownload" size="small" :loading="actionLoading" @click="downloadTask">下载结果</el-button>
        <el-button v-if="hasAction('CANCEL')" size="small" type="danger" :loading="actionLoading" @click="cancelTask">
          取消
        </el-button>
      </div>
    </div>

    <el-drawer v-model="errorDrawerVisible" title="任务错误明细" size="min(720px, 92vw)">
      <el-table :data="failureRows" empty-text="暂无错误明细" stripe>
        <el-table-column prop="rowNo" label="行号" width="80" />
        <el-table-column prop="columnName" label="列名" width="140" show-overflow-tooltip />
        <el-table-column prop="code" label="错误码" width="160" show-overflow-tooltip />
        <el-table-column prop="message" label="错误" min-width="220" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="建议" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </section>
</template>

<style scoped>
.sales-document-task-panel {
  display: grid;
  gap: 10px;
}

.task-summary {
  align-items: start;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(260px, 1.3fr) minmax(160px, 0.7fr) auto;
  padding: 10px 12px;
}

.task-summary > div,
.task-state,
.task-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.task-actions {
  align-items: flex-end;
  min-width: 120px;
}

.task-expired {
  color: #c45656;
}
</style>
