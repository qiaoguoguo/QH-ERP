<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { documentPlatformApi, type ApprovalScope, type ApprovalTaskRecord, type ApprovalInstanceDetail } from '../../../shared/api/documentPlatformApi'
import { pageItems } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import {
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

const canApprove = computed(() => selectedTask.value?.availableActions?.includes('APPROVE') ?? false)
const canReject = computed(() => selectedTask.value?.availableActions?.includes('REJECT') ?? false)
const canWithdraw = computed(() => detail.value?.availableActions?.includes('WITHDRAW') ?? false)

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
    detail.value = await documentPlatformApi.approvals.get(record.instanceId)
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    detailLoading.value = false
  }
}

async function submitTaskAction(action: 'approve' | 'reject') {
  if (!selectedTask.value || actionLoading.value) {
    return
  }
  if (action === 'reject' && !actionComment.value.trim()) {
    actionError.value = '驳回原因必填'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = { version: selectedTask.value.version, comment: actionComment.value.trim() }
    if (action === 'approve') {
      detail.value = await documentPlatformApi.approvalTasks.approve(selectedTask.value.id, payload)
    } else {
      detail.value = await documentPlatformApi.approvalTasks.reject(selectedTask.value.id, payload)
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
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
    })
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

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="审批待办" description="处理固定审批待办、查看已处理和我发起的审批。">
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="范围">
          <el-radio-group :model-value="activeScope">
            <el-radio-button
              v-for="scope in scopes"
              :key="scope"
              :label="scope"
              @click="switchScope(scope)"
            >
              {{ approvalScopeLabel(scope) }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="approval-keyword" clearable placeholder="对象编号或名称" />
        </el-form-item>
        <el-form-item>
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
          <template #default="{ row }">{{ row.objectNo || '-' }} {{ row.objectName || '' }}</template>
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
      :page-sizes="[10, 20, 50]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />

    <el-drawer v-model="detailVisible" title="审批详情" size="min(680px, 92vw)">
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="detailLoading" class="state-alert" type="info" title="审批详情加载中" :closable="false" />
      <dl v-if="detail" class="platform-detail-list">
        <dt>对象</dt><dd>{{ detail.objectNo || '-' }} {{ detail.objectName || '' }}</dd>
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
          {{ history.operatorName || '-' }} {{ history.comment || history.action }}
        </el-timeline-item>
      </el-timeline>
      <el-form label-position="top">
        <el-form-item label="处理意见">
          <el-input data-test="approval-comment" v-model="actionComment" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="canWithdraw" data-test="withdraw-approval" :loading="actionLoading" @click="withdrawApproval">撤回</el-button>
        <el-button v-if="canReject" data-test="reject-task" type="danger" plain :loading="actionLoading" @click="submitTaskAction('reject')">驳回</el-button>
        <el-button v-if="canApprove" data-test="approve-task" type="primary" :loading="actionLoading" @click="submitTaskAction('approve')">通过</el-button>
      </template>
    </el-drawer>
  </MasterDataTableView>
</template>
