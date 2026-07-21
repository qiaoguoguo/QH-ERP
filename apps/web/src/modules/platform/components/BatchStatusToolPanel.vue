<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { createIdempotencyKey, type ResourceId } from '../../../shared/api/documentPlatformApi'
import {
  platformGovernanceApi,
  type BatchOperationItemRecord,
  type BatchOperationRecord,
  type BatchToolRecord,
} from '../../../shared/api/platformGovernanceApi'
import { useAuthStore } from '../../../stores/authStore'
import { confirmAction } from '../../../shared/ui/confirmDialog'
import { formatPlatformDateTime, platformErrorMessage } from '../platformPageHelpers'
import {
  candidateMissingVersionMessage,
  hasStableCandidateVersion,
  listPlatformTargetCandidates,
  stableCandidateVersion,
} from '../platformTargetCandidates'

interface BatchToolCandidate {
  id: ResourceId
  code: string
  name: string
  status?: string | null
  version?: number | null
}

const props = withDefaults(defineProps<{
  toolCode: string
  title: string
  buttonTestId: string
  defaultCandidates?: BatchToolCandidate[]
}>(), {
  defaultCandidates: () => [],
})

const authStore = useAuthStore()
const visible = ref(false)
const tools = ref<BatchToolRecord[]>([])
const candidates = ref<BatchToolCandidate[]>([])
const selectedCandidates = ref<BatchToolCandidate[]>([])
const targetStatus = ref<'ENABLED' | 'DISABLED'>('DISABLED')
const reason = ref('')
const candidateKeyword = ref('')
const operation = ref<BatchOperationRecord | null>(null)
const loading = ref(false)
const candidateLoading = ref(false)
const actionError = ref('')
const candidatePagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
  totalPages: 0,
})

const canView = computed(() => authStore.hasPermission('platform:batch-tool:view'))
const tool = computed(() => tools.value.find((item) => item.toolCode === props.toolCode) ?? null)
const hasToolPermission = computed(() => {
  const permission = tool.value?.requiredPermissionCode
  return !permission || authStore.hasPermission(permission)
})
const canPreview = computed(() => (
  authStore.hasPermission('platform:batch-tool:preview')
  && hasToolPermission.value
  && !loading.value
))
const canExecute = computed(() => (
  Boolean(operation.value?.availableActions?.includes('EXECUTE'))
  && authStore.hasPermission('platform:batch-tool:execute')
  && hasToolPermission.value
  && !loading.value
))
const canLoadMoreCandidates = computed(() => (
  candidatePagination.totalPages
    ? candidatePagination.page < candidatePagination.totalPages
    : candidates.value.length < candidatePagination.total
))
const readyCount = computed(() => (operation.value?.items ?? []).filter((item) => item.status === 'READY').length)
const blockedCount = computed(() => (operation.value?.items ?? []).filter((item) => item.status !== 'READY' && item.status !== 'SUCCEEDED').length)

function candidateKey(candidate: BatchToolCandidate): string {
  return String(candidate.id)
}

function candidateVersion(candidate: BatchToolCandidate): number | null {
  return stableCandidateVersion(candidate)
}

function addCandidates(candidatesToAdd: BatchToolCandidate[]) {
  const missingVersion = candidatesToAdd.find((candidate) => !hasStableCandidateVersion(candidate))
  if (missingVersion) {
    actionError.value = candidateMissingVersionMessage(missingVersion)
    return
  }
  const next = new Map(selectedCandidates.value.map((candidate) => [candidateKey(candidate), candidate]))
  candidatesToAdd.forEach((candidate) => {
    next.set(candidateKey(candidate), candidate)
  })
  selectedCandidates.value = Array.from(next.values())
  operation.value = null
  actionError.value = ''
}

function selectCurrentCandidates() {
  addCandidates(props.defaultCandidates)
}

function removeCandidate(candidate: BatchToolCandidate) {
  selectedCandidates.value = selectedCandidates.value.filter((item) => candidateKey(item) !== candidateKey(candidate))
  operation.value = null
}

function clearCandidates() {
  selectedCandidates.value = []
  operation.value = null
}

async function loadTools() {
  if (!canView.value || tools.value.length > 0) {
    return
  }
  loading.value = true
  actionError.value = ''
  try {
    tools.value = await platformGovernanceApi.batchTools.list()
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function openPanel() {
  visible.value = true
  await loadTools()
}

async function loadCandidates(page = 1, append = false) {
  const activeTool = tool.value
  if (!activeTool) {
    actionError.value = '批量工具目录未返回当前工具'
    return
  }
  candidateLoading.value = true
  actionError.value = ''
  try {
    const result = await listPlatformTargetCandidates(activeTool.targetObjectType, {
      keyword: candidateKeyword.value.trim(),
      page,
      pageSize: candidatePagination.pageSize,
    })
    candidates.value = append
      ? [...candidates.value, ...(result.items ?? [])]
      : (result.items ?? [])
    candidatePagination.page = Number(result.page ?? page)
    candidatePagination.pageSize = Number(result.pageSize ?? candidatePagination.pageSize)
    candidatePagination.total = Number(result.total ?? candidates.value.length)
    candidatePagination.totalPages = Number(result.totalPages ?? 0)
    const missingVersion = candidates.value.find((candidate) => !hasStableCandidateVersion(candidate))
    if (missingVersion) {
      actionError.value = candidateMissingVersionMessage(missingVersion)
    }
  } catch (caught) {
    candidates.value = append ? candidates.value : []
    actionError.value = platformErrorMessage(caught)
  } finally {
    candidateLoading.value = false
  }
}

function searchCandidates() {
  void loadCandidates(1, false)
}

function loadMoreCandidates() {
  if (!canLoadMoreCandidates.value || candidateLoading.value) {
    return
  }
  void loadCandidates(candidatePagination.page + 1, true)
}

function selectCandidate(candidate: BatchToolCandidate) {
  addCandidates([candidate])
}

async function previewOperation() {
  const activeTool = tool.value
  if (!activeTool) {
    actionError.value = '批量工具目录未返回当前工具'
    return
  }
  if (!selectedCandidates.value.length) {
    actionError.value = '请先加入候选对象'
    return
  }
  const invalidCandidate = selectedCandidates.value.find((candidate) => candidateVersion(candidate) === null)
  if (invalidCandidate) {
    actionError.value = `候选对象 ${invalidCandidate.code || invalidCandidate.id} 缺少版本`
    return
  }
  loading.value = true
  actionError.value = ''
  try {
    operation.value = await platformGovernanceApi.batchTools.preview(props.toolCode, {
      actionCode: activeTool.actionCode || 'STATUS_CHANGE',
      targetStatus: targetStatus.value,
      reason: reason.value.trim() || null,
      targets: selectedCandidates.value.map((candidate) => ({
        targetObjectId: candidate.id,
        version: candidateVersion(candidate)!,
      })),
      idempotencyKey: createIdempotencyKey('batch-tool-preview'),
    })
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function executeOperation() {
  if (!operation.value || !canExecute.value) {
    return
  }
  if (!(await confirmAction(`确认执行批量状态“${operation.value.operationNo}”？`, { risk: 'danger' }))) {
    return
  }
  loading.value = true
  actionError.value = ''
  try {
    operation.value = await platformGovernanceApi.batchOperations.execute(operation.value.id, {
      version: operation.value.version,
      idempotencyKey: createIdempotencyKey('batch-tool-execute'),
    })
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function operationStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    PRECHECKED: '预检通过',
    PRECHECK_FAILED: '预检失败',
    EXECUTING: '执行中',
    SUCCEEDED: '执行成功',
    FAILED: '执行失败',
    CANCELLED: '已取消',
    EXPIRED: '已过期',
  }
  return labels[status] ?? status
}

function itemStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    READY: '可执行',
    BLOCKED: '阻断',
    SUCCEEDED: '成功',
    FAILED: '失败',
  }
  return labels[status] ?? status
}

function itemStatusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'READY' || status === 'SUCCEEDED') {
    return 'success'
  }
  if (status === 'BLOCKED' || status === 'FAILED') {
    return 'danger'
  }
  return 'warning'
}

function targetText(item: BatchOperationItemRecord): string {
  return [item.targetObjectNo, item.targetObjectSummary].filter(Boolean).join(' ') || String(item.targetObjectId ?? '-')
}
</script>

<template>
  <el-button v-if="canView" :data-test="buttonTestId" @click="openPanel">
    批量状态
  </el-button>

  <el-dialog v-model="visible" :title="title" width="min(920px, 96vw)" :teleported="false">
    <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
    <el-alert v-if="loading" class="state-alert" type="info" title="批量工具处理中" :closable="false" />
    <el-alert
      v-if="tool && !hasToolPermission"
      class="state-alert"
      type="warning"
      :title="`缺少业务权限 ${tool.requiredPermissionCode}`"
      :closable="false"
    />

    <section class="batch-tool-grid">
      <el-form label-position="top">
        <el-form-item label="目标状态">
          <el-select v-model="targetStatus" data-test="batch-target-status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="reason" name="batch-operation-reason" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>

      <section class="candidate-panel">
        <div class="section-title-row">
          <h3>候选池</h3>
          <div class="batch-toolbar">
            <el-button data-test="select-current-batch-candidates" @click="selectCurrentCandidates">
              加入当前页
            </el-button>
            <el-button @click="clearCandidates">清空</el-button>
          </div>
        </div>
        <div class="candidate-search-row">
          <el-input
            v-model="candidateKeyword"
            name="batch-candidate-keyword"
            clearable
            placeholder="按编码或名称搜索候选对象"
          />
          <el-button
            data-test="search-batch-candidates"
            :loading="candidateLoading"
            :disabled="candidateLoading"
            @click="searchCandidates"
          >
            搜索
          </el-button>
        </div>
        <div class="table-scroll candidate-table">
          <el-table :data="candidates" empty-text="暂无搜索候选" stripe>
            <el-table-column prop="code" label="编码" min-width="130" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="status" label="当前状态" min-width="100" />
            <el-table-column prop="version" label="版本" width="90" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button
                  v-if="hasStableCandidateVersion(row)"
                  size="small"
                  text
                  :data-test="`select-batch-candidate-${row.id}`"
                  @click="selectCandidate(row)"
                >
                  加入
                </el-button>
                <span v-else class="candidate-error">缺少版本</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-button
          v-if="canLoadMoreCandidates"
          data-test="load-more-batch-candidates"
          class="load-more-button"
          :loading="candidateLoading"
          :disabled="candidateLoading"
          @click="loadMoreCandidates"
        >
          继续加载
        </el-button>
        <h3 class="selected-title">已选候选</h3>
        <div class="table-scroll candidate-table">
          <el-table :data="selectedCandidates" empty-text="暂无候选" stripe>
            <el-table-column prop="code" label="编码" min-width="130" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="status" label="当前状态" min-width="100" />
            <el-table-column prop="version" label="版本" width="90" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" text @click="removeCandidate(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </section>

    <section v-if="operation" class="operation-panel">
      <div class="batch-summary-strip">
        <div><span>操作号</span><strong>{{ operation.operationNo }}</strong></div>
        <div><span>状态</span><strong>{{ operationStatusLabel(operation.status) }}</strong></div>
        <div><span>总数</span><strong>{{ operation.totalRows }}</strong></div>
        <div><span>可执行</span><strong>可执行 {{ readyCount }}</strong></div>
        <div><span>阻断</span><strong>{{ blockedCount }}</strong></div>
        <div><span>创建人</span><strong>{{ operation.createdByName || '-' }}</strong></div>
        <div><span>创建时间</span><strong>{{ formatPlatformDateTime(operation.createdAt) }}</strong></div>
      </div>

      <div class="table-scroll">
        <el-table :data="operation.items ?? []" empty-text="暂无批量明细" stripe>
          <el-table-column prop="lineNo" label="行号" width="78" />
          <el-table-column label="目标" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ targetText(row) }}</template>
          </el-table-column>
          <el-table-column prop="targetObjectVersion" label="版本" width="90" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="itemStatusTagType(row.status)" size="small">{{ itemStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="错误/结果" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
    </section>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button data-test="preview-batch-tool" type="primary" :loading="loading" :disabled="!canPreview" @click="previewOperation">
        预检
      </el-button>
      <el-button
        v-if="canExecute"
        data-test="execute-batch-operation"
        type="danger"
        :loading="loading"
        :disabled="loading"
        @click="executeOperation"
      >
        确认执行
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.batch-tool-grid {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  gap: 14px;
}

.candidate-panel,
.operation-panel {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  padding: 12px;
}

.section-title-row,
.batch-toolbar {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
}

.section-title-row h3 {
  font-size: 15px;
  margin: 0;
}

.candidate-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-top: 10px;
}

.candidate-table {
  margin-top: 10px;
}

.candidate-error {
  color: var(--el-color-danger);
  font-size: 12px;
}

.load-more-button {
  margin-top: 8px;
}

.selected-title {
  font-size: 15px;
  margin: 14px 0 0;
}

.operation-panel {
  margin-top: 14px;
}

.batch-summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.batch-summary-strip > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  padding: 10px 12px;
}

.batch-summary-strip span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
}

.batch-summary-strip strong {
  display: block;
  margin-top: 4px;
  word-break: break-word;
}
</style>
