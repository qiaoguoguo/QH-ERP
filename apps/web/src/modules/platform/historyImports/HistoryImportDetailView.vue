<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  platformGovernanceApi,
  type HistoryImportDetail,
  type HistoryImportErrorRecord,
} from '../../../shared/api/platformGovernanceApi'
import { createIdempotencyKey, type ResourceId } from '../../../shared/api/documentPlatformApi'
import { confirmAction } from '../../../shared/ui/confirmDialog'
import { pageItems } from '../../system/shared/pageHelpers'
import { formatPlatformDateTime, platformErrorMessage } from '../platformPageHelpers'
import { historyImportStatusLabel, historyImportStatusTagType } from '../platformGovernanceLabels'

const props = defineProps<{
  taskId: ResourceId
}>()

const detail = ref<HistoryImportDetail | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const errorDrawerVisible = ref(false)
const errorRows = ref<HistoryImportErrorRecord[]>([])
const errorDetailState = ref('')
const errorPagination = reactive({ page: 1, pageSize: 10, total: 0 })

const actionSet = computed(() => new Set(detail.value?.availableActions ?? []))
const canConfirm = computed(() => actionSet.value.has('CONFIRM'))
const canCancel = computed(() => actionSet.value.has('CANCEL'))
const canViewErrors = computed(() => actionSet.value.has('ERRORS'))
const expectedErrorTotal = computed(() => Number(detail.value?.errorSummary?.totalErrors ?? detail.value?.failedRows ?? 0))

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await platformGovernanceApi.historyImports.get(props.taskId)
  } catch (caught) {
    detail.value = null
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function confirmImport() {
  if (!detail.value || actionLoading.value) {
    return
  }
  const confirmed = await confirmAction(
    `确认提交历史导入“${detail.value.taskNo}”？确认后会重新校验权限、模板版本和文件指纹。`,
    { title: '确认历史导入', risk: 'danger' },
  )
  if (!confirmed) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    detail.value = await platformGovernanceApi.historyImports.confirm(detail.value.taskId ?? detail.value.id, {
      version: detail.value.version,
      idempotencyKey: createIdempotencyKey('history-import-confirm'),
    })
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function cancelImport() {
  if (!detail.value || actionLoading.value) {
    return
  }
  const confirmed = await confirmAction(`确认取消历史导入“${detail.value.taskNo}”？`, { title: '取消历史导入', risk: 'warning' })
  if (!confirmed) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    detail.value = await platformGovernanceApi.historyImports.cancel(detail.value.taskId ?? detail.value.id, {
      version: detail.value.version,
      idempotencyKey: createIdempotencyKey('history-import-cancel'),
    })
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function openErrors() {
  errorDrawerVisible.value = true
  errorPagination.page = 1
  errorDetailState.value = ''
  await loadErrors()
}

async function loadErrors() {
  if (!detail.value) {
    return
  }
  actionError.value = ''
  errorDetailState.value = ''
  try {
    const page = await platformGovernanceApi.historyImports.errors(detail.value.taskId ?? detail.value.id, {
      page: errorPagination.page,
      pageSize: errorPagination.pageSize,
    })
    errorRows.value = pageItems(page)
    errorPagination.total = Number(page.total)
    if (expectedErrorTotal.value > 0 && errorRows.value.length === 0) {
      errorDetailState.value = '错误汇总存在但明细为空，请刷新或联系后端核查错误明细记录'
    }
  } catch (caught) {
    errorRows.value = []
    errorPagination.total = 0
    actionError.value = platformErrorMessage(caught)
    if (expectedErrorTotal.value > 0) {
      errorDetailState.value = '错误汇总存在但明细不可读取'
    }
  }
}

function changeErrorPage(page: number) {
  errorPagination.page = page
  void loadErrors()
}

function changeErrorPageSize(pageSize: number) {
  errorPagination.pageSize = pageSize
  errorPagination.page = 1
  void loadErrors()
}

function errorCode(row: HistoryImportErrorRecord) {
  return row.errorCode || row.code || '-'
}

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <section class="module-page">
    <header class="page-heading">
      <div>
        <h1>历史导入详情</h1>
        <p>按上传、预检、确认、任务、文件、错误和审计查看历史导入批次。</p>
      </div>
    </header>

    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
    <el-alert v-if="loading" class="state-alert" type="info" title="历史导入详情加载中" :closable="false" />

    <template v-if="detail">
      <section class="section-block">
        <h2>{{ detail.adapterName || detail.adapterCode }}</h2>
        <dl class="platform-panel-list">
          <dt>任务号</dt><dd>{{ detail.taskNo }}</dd>
          <dt>源文件</dt><dd>{{ detail.sourceFileName || '-' }}</dd>
          <dt>文件指纹</dt><dd>{{ detail.sourceSha256 || '-' }}</dd>
          <dt>模板</dt><dd>{{ detail.templateCode || '-' }} V{{ detail.templateVersion ?? '-' }}</dd>
          <dt>状态</dt><dd><el-tag :type="historyImportStatusTagType(detail.status)">{{ historyImportStatusLabel(detail.status) }}</el-tag></dd>
          <dt>上传人</dt><dd>{{ detail.createdByName || '-' }}</dd>
          <dt>创建时间</dt><dd>{{ formatPlatformDateTime(detail.createdAt) }}</dd>
          <dt>结果过期</dt><dd>{{ formatPlatformDateTime(detail.expiresAt) }}</dd>
        </dl>
      </section>

      <section class="section-block">
        <h2>预检结果</h2>
        <div class="table-scroll">
          <el-table :data="[detail.validationSummary ?? {}]" empty-text="暂无预检结果" stripe>
            <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
            <el-table-column prop="totalRows" label="总行数" width="90" />
            <el-table-column prop="successRows" label="成功数" width="90" />
            <el-table-column prop="failedRows" label="错误数" width="90" />
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>文件与任务</h2>
        <dl class="platform-panel-list">
          <dt>关联任务</dt>
          <dd>
            <RouterLink
              v-if="detail.relatedTaskId || detail.taskId"
              data-test="history-import-task-link"
              :to="`/platform/document-tasks?taskId=${encodeURIComponent(String(detail.relatedTaskId ?? detail.taskId))}&returnTo=${encodeURIComponent('/platform/history-imports')}`"
            >
              查看文档任务
            </RouterLink>
            <span v-else>-</span>
          </dd>
          <dt>错误汇总</dt><dd>{{ detail.errorSummary?.totalErrors ?? detail.failedRows ?? 0 }}</dd>
          <dt>审计摘要</dt><dd>{{ detail.auditSummary?.summary || '-' }}</dd>
        </dl>
      </section>

      <section class="section-block">
        <h2>批次动作</h2>
        <div class="detail-action-bar">
          <el-button
            v-if="canViewErrors"
            data-test="view-history-import-errors"
            :loading="actionLoading"
            @click="openErrors"
          >
            错误明细
          </el-button>
          <el-button
            v-if="canCancel"
            data-test="cancel-history-import"
            type="warning"
            plain
            :loading="actionLoading"
            @click="cancelImport"
          >
            取消
          </el-button>
          <el-button
            v-if="canConfirm"
            data-test="confirm-history-import"
            type="primary"
            :loading="actionLoading"
            @click="confirmImport"
          >
            确认导入
          </el-button>
        </div>
      </section>

      <el-drawer v-model="errorDrawerVisible" title="历史导入错误明细" size="min(760px, 92vw)">
        <el-alert v-if="errorDetailState" class="state-alert" type="error" :title="errorDetailState" :closable="false" />
        <div class="table-scroll">
          <el-table :data="errorRows" :empty-text="errorDetailState || '暂无错误明细'" stripe>
            <el-table-column prop="rowNo" label="行号" width="80" />
            <el-table-column prop="columnName" label="列名" width="140" show-overflow-tooltip />
            <el-table-column label="错误码" width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ errorCode(row) }}</template>
            </el-table-column>
            <el-table-column prop="message" label="错误" min-width="220" show-overflow-tooltip />
            <el-table-column prop="suggestion" label="建议" min-width="180" show-overflow-tooltip />
          </el-table>
        </div>
        <el-pagination
          class="table-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="errorPagination.total"
          :page-size="errorPagination.pageSize"
          :current-page="errorPagination.page"
          @current-change="changeErrorPage"
          @size-change="changeErrorPageSize"
        />
      </el-drawer>
    </template>
  </section>
</template>

<style scoped>
.section-block {
  padding: 16px;
  border: 1px solid var(--qherp-border);
  border-radius: var(--qherp-radius-lg);
  background: var(--qherp-surface);
}

.section-block h2 {
  margin-bottom: 12px;
  font-size: 18px;
  line-height: 1.4;
}

.detail-action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
