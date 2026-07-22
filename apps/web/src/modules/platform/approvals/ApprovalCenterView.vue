<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type ApprovalInstanceDetail,
  type ApprovalScope,
  type ApprovalTaskRecord,
} from '../../../shared/api/documentPlatformApi'
import { pageItems } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import {
  approvalActionLabel,
  approvalScopeLabel,
  approvalStatusLabel,
  approvalStatusTagType,
  formatPlatformDateTime,
  platformErrorMessage,
} from '../platformPageHelpers'

const scopes: ApprovalScope[] = ['TODO', 'DONE', 'STARTED']
const activeScope = ref<ApprovalScope>('TODO')
const filters = reactive({ keyword: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<ApprovalTaskRecord[]>([])
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ApprovalInstanceDetail | null>(null)
const selectedTask = ref<ApprovalTaskRecord | null>(null)
const actionComment = ref('')

const currentTask = computed(() => {
  const approval = detail.value
  if (!approval) {
    return null
  }
  const matchedStep = (approval.steps ?? []).find((step) =>
    step.taskId !== null
    && step.taskId !== undefined
    && (approval.taskId === null || approval.taskId === undefined || String(step.taskId) === String(approval.taskId)),
  )
  const taskId = approval.taskId ?? matchedStep?.taskId ?? null
  const taskVersion = approval.taskVersion ?? matchedStep?.version ?? null
  return taskId === null || taskId === undefined ? null : { id: taskId, version: taskVersion }
})
const hasPendingTaskActionWithoutTaskId = computed(() => {
  const actions = detail.value?.availableActions ?? []
  return (!currentTask.value || currentTask.value.version === null || currentTask.value.version === undefined)
    && (actions.includes('APPROVE') || actions.includes('REJECT'))
})
const hasCurrentTaskVersion = computed(() =>
  currentTask.value?.version !== null && currentTask.value?.version !== undefined,
)
const canApprove = computed(() => hasCurrentTaskVersion.value && (detail.value?.availableActions?.includes('APPROVE') ?? false))
const canReject = computed(() => hasCurrentTaskVersion.value && (detail.value?.availableActions?.includes('REJECT') ?? false))
const canWithdraw = computed(() => detail.value?.availableActions?.includes('WITHDRAW') ?? false)
const canCancel = computed(() => detail.value?.availableActions?.includes('CANCEL') ?? false)
const approveButtonText = computed(() => {
  if (detail.value?.sceneCode === 'GL_VOUCHER_POST') {
    return '通过并记账'
  }
  if (detail.value?.sceneCode === 'FINANCIAL_PERIOD_REOPEN') {
    return '通过并反结账'
  }
  return '通过'
})

const businessObjectPaths: Record<string, string> = {
  INVENTORY_STOCKTAKE: '/inventory/stocktakes',
  INVENTORY_OWNERSHIP_CONVERSION: '/inventory/ownership-conversions',
  INVENTORY_VALUATION_ADJUSTMENT: '/inventory/valuation-adjustments',
  SALES_QUOTE: '/sales/quotes',
  GL_VOUCHER: '/gl/vouchers',
  FINANCIAL_PERIOD_REOPEN: '/gl/financial-close',
  DATA_REPAIR_REQUEST: '/platform/data-repairs',
  HISTORY_IMPORT_TASK: '/platform/history-imports',
  BATCH_OPERATION: '/platform/document-tasks',
}

const businessObjectLabels: Record<string, string> = {
  INVENTORY_STOCKTAKE: '库存盘点',
  INVENTORY_OWNERSHIP_CONVERSION: '库存权属转换',
  INVENTORY_VALUATION_ADJUSTMENT: '库存估值调整',
  SALES_QUOTE: '销售报价',
  GL_VOUCHER: '会计凭证',
  FINANCIAL_PERIOD_REOPEN: '反结账申请',
  DATA_REPAIR_REQUEST: '数据修复',
  HISTORY_IMPORT_TASK: '历史导入',
  BATCH_OPERATION: '批量操作',
}

function businessObjectRoute(record: { sceneCode?: string | null, objectType?: string | null, objectId?: string | number | null }) {
  if (record.sceneCode === 'FINANCIAL_PERIOD_REOPEN' || record.objectType === 'FINANCIAL_PERIOD_REOPEN') {
    return `/gl/financial-close?returnTo=${encodeURIComponent('/platform/approvals')}`
  }
  if (!record.objectType || record.objectId === null || record.objectId === undefined) {
    return null
  }
  const basePath = businessObjectPaths[record.objectType]
  if (!basePath) {
    return null
  }
  if (record.objectType === 'DATA_REPAIR_REQUEST') {
    return `${basePath}/${encodeURIComponent(String(record.objectId))}?returnTo=${encodeURIComponent('/platform/approvals')}`
  }
  if (record.objectType === 'HISTORY_IMPORT_TASK') {
    return `${basePath}/${encodeURIComponent(String(record.objectId))}?returnTo=${encodeURIComponent('/platform/approvals')}`
  }
  if (record.objectType === 'BATCH_OPERATION') {
    return `${basePath}?batchOperationId=${encodeURIComponent(String(record.objectId))}&returnTo=${encodeURIComponent('/platform/approvals')}`
  }
  const path = `${basePath}/${encodeURIComponent(String(record.objectId))}`
  return record.objectType === 'GL_VOUCHER'
    ? `${path}?returnTo=${encodeURIComponent('/platform/approvals')}`
    : path
}

function businessObjectLabel(objectType?: string | null) {
  return objectType ? businessObjectLabels[objectType] : null
}

function approvalHistoryText(history: { operatorName?: string | null; comment?: string | null; action?: string | null }) {
  const comment = history.comment?.trim()
  return `${history.operatorName || '-'} ${comment || approvalActionLabel(history.action)}`
}

function isStaleActionError(caught: unknown): boolean {
  const message = caught instanceof Error ? caught.message : String(caught)
  return /过期|并发|状态|CONCURRENT|VERSION|STATUS/.test(message)
}

function freezeStaleActions(caught: unknown) {
  if (!isStaleActionError(caught) || !detail.value) {
    return false
  }
  detail.value = {
    ...detail.value,
    availableActions: [],
  }
  actionError.value = '审批动作已过期需刷新'
  return true
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await documentPlatformApi.approvalTasks.list({
      scope: activeScope.value,
      keyword: filters.keyword,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function switchScope(scope: ApprovalScope) {
  activeScope.value = scope
  pagination.page = 1
  void loadRecords()
}

function search() {
  pagination.page = 1
  void loadRecords()
}

async function openDetail(record: ApprovalTaskRecord) {
  selectedTask.value = record
  detailVisible.value = true
  detailLoading.value = true
  actionError.value = ''
  actionComment.value = ''
  try {
    detail.value = await documentPlatformApi.approvals.get(record.id)
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    detailLoading.value = false
  }
}

async function submitTaskAction(action: 'approve' | 'reject') {
  if (!detail.value || actionLoading.value) {
    return
  }
  const task = currentTask.value
  if (!task || task.version === null || task.version === undefined) {
    actionError.value = '当前审批任务不可处理，请刷新审批详情'
    return
  }
  if (action === 'reject' && !actionComment.value.trim()) {
    actionError.value = '驳回原因必填'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const comment = actionComment.value.trim()
    if (action === 'approve') {
      detail.value = await documentPlatformApi.approvalTasks.approve(task.id, {
        version: task.version,
        ...(comment ? { comment } : {}),
        idempotencyKey: createIdempotencyKey('approval-approve'),
      })
    } else {
      detail.value = await documentPlatformApi.approvalTasks.reject(task.id, {
        version: task.version,
        comment,
        idempotencyKey: createIdempotencyKey('approval-reject'),
      })
    }
    await loadRecords()
  } catch (caught) {
    if (!freezeStaleActions(caught)) {
      actionError.value = platformErrorMessage(caught)
    }
  } finally {
    actionLoading.value = false
  }
}

async function withdrawApproval() {
  if (!detail.value || actionLoading.value) {
    return
  }
  if (!actionComment.value.trim()) {
    actionError.value = '撤回原因必填'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    detail.value = await documentPlatformApi.approvals.withdraw(detail.value.id, {
      version: detail.value.version,
      comment: actionComment.value.trim(),
      idempotencyKey: createIdempotencyKey('approval-withdraw'),
    })
    await loadRecords()
  } catch (caught) {
    if (!freezeStaleActions(caught)) {
      actionError.value = platformErrorMessage(caught)
    }
  } finally {
    actionLoading.value = false
  }
}

async function cancelApproval() {
  if (!detail.value || actionLoading.value) {
    return
  }
  if (!actionComment.value.trim()) {
    actionError.value = '取消原因必填'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    detail.value = await documentPlatformApi.approvals.cancel(detail.value.id, {
      version: detail.value.version,
      comment: actionComment.value.trim(),
      idempotencyKey: createIdempotencyKey('approval-cancel'),
    })
    await loadRecords()
  } catch (caught) {
    if (!freezeStaleActions(caught)) {
      actionError.value = platformErrorMessage(caught)
    }
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

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="审批待办" description="处理固定审批待办、查看已处理和我发起的审批。">
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="范围">
          <el-radio-group :model-value="activeScope">
            <el-radio-button
              v-for="scope in scopes"
              :key="scope"
              :value="scope"
              @click="switchScope(scope)"
            >
              {{ approvalScopeLabel(scope) }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="approval-keyword" clearable placeholder="对象编号或名称" />
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-approvals" type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="审批列表加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无审批任务'" stripe>
        <el-table-column prop="taskNo" label="任务号" width="150" show-overflow-tooltip />
        <el-table-column label="对象" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.objectNo || '-' }} {{ row.objectName || '' }}</span>
            <RouterLink
              v-if="businessObjectRoute(row)"
              class="inline-action-link"
              data-test="approval-business-link"
              :to="businessObjectRoute(row) || ''"
            >
              查看业务单据<span v-if="businessObjectLabel(row.objectType)">（{{ businessObjectLabel(row.objectType) }}）</span>
            </RouterLink>
          </template>
        </el-table-column>
        <el-table-column prop="currentStepName" label="当前步骤" width="130" show-overflow-tooltip />
        <el-table-column prop="applicantName" label="提交人" width="110" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="approvalStatusTagType(row.status)" size="small">{{ approvalStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分配时间" width="160">
          <template #default="{ row }">{{ formatPlatformDateTime(row.assignedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="120">
          <template #default="{ row }">
            <el-button data-test="open-approval-detail" size="small" text @click="openDetail(row)">详情</el-button>
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

    <el-drawer v-model="detailVisible" title="审批详情" size="min(680px, 92vw)">
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="detailLoading" class="state-alert" type="info" title="审批详情加载中" :closable="false" />
      <el-alert
        v-if="hasPendingTaskActionWithoutTaskId"
        class="state-alert"
        type="warning"
        title="当前审批任务不可处理，请刷新审批详情"
        :closable="false"
      />
      <dl v-if="detail" class="platform-detail-list">
        <dt>对象</dt><dd>{{ detail.objectNo || '-' }} {{ detail.objectName || '' }}</dd>
        <dt>业务单据</dt>
        <dd>
          <RouterLink
            v-if="businessObjectRoute(detail)"
            data-test="approval-detail-business-link"
            :to="businessObjectRoute(detail) || ''"
          >
            查看业务单据<span v-if="businessObjectLabel(detail.objectType)">（{{ businessObjectLabel(detail.objectType) }}）</span>
          </RouterLink>
          <span v-else>无可跳转业务单据</span>
        </dd>
        <dt>审批状态</dt><dd><el-tag :type="approvalStatusTagType(detail.status)">{{ approvalStatusLabel(detail.status) }}</el-tag></dd>
        <dt>提交人</dt><dd>{{ detail.applicantName || '-' }}</dd>
        <dt>提交时间</dt><dd>{{ formatPlatformDateTime(detail.submittedAt) }}</dd>
      </dl>
      <h3>固定步骤</h3>
      <el-timeline>
        <el-timeline-item v-for="step in detail?.steps ?? []" :key="step.stepName" :timestamp="formatPlatformDateTime(step.completedAt)">
          {{ step.stepName }} / {{ approvalStatusLabel(step.status) }}
        </el-timeline-item>
      </el-timeline>
      <h3>审批记录</h3>
      <el-timeline>
        <el-timeline-item v-for="history in detail?.histories ?? []" :key="`${history.action}-${history.operatedAt}`" :timestamp="formatPlatformDateTime(history.operatedAt)">
          {{ approvalHistoryText(history) }}
        </el-timeline-item>
      </el-timeline>
      <h3>附件快照</h3>
      <el-table :data="detail?.attachmentSnapshots ?? []" empty-text="暂无附件快照" stripe>
        <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="fileSize" label="大小" width="100" />
        <el-table-column prop="sha256" label="SHA-256" min-width="180" show-overflow-tooltip />
      </el-table>
      <el-form label-position="top">
        <el-form-item label="处理意见">
          <el-input data-test="approval-comment" v-model="actionComment" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="驳回、撤回、取消时必填；通过可填写意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="canCancel" data-test="approval-cancel" type="warning" plain :loading="actionLoading" @click="cancelApproval">治理取消</el-button>
        <el-button v-if="canWithdraw" data-test="withdraw-approval" :loading="actionLoading" @click="withdrawApproval">撤回</el-button>
        <el-button v-if="canReject" data-test="reject-task" type="danger" plain :loading="actionLoading" @click="submitTaskAction('reject')">驳回</el-button>
        <el-button v-if="canApprove" data-test="approve-task" type="primary" :loading="actionLoading" @click="submitTaskAction('approve')">{{ approveButtonText }}</el-button>
      </template>
    </el-drawer>
  </MasterDataTableView>
</template>
