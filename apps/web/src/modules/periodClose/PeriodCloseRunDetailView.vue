<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import {
  businessPeriodCloseApi,
  type BusinessPeriodCloseCheckItem,
  type BusinessPeriodCloseRunDetail,
} from '../../shared/api/businessPeriodCloseApi'
import { returnLocation } from '../../shared/navigation/navigationReturn'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../system/shared/pageHelpers'
import PeriodCloseSeverityTag from './PeriodCloseSeverityTag.vue'
import PeriodCloseStatusTag from './PeriodCloseStatusTag.vue'
import {
  formatPeriodCloseAmount,
  formatPeriodCloseDateTime,
  businessPeriodStatusLabel,
  periodCloseAuditActionLabel,
  periodCloseActionDisabledReason,
  periodCloseAllowed,
  periodCloseDomainLabel,
  periodCloseErrorMessage,
  periodCloseMessages,
  periodCloseStatusLabel,
  restrictedMoneyReason,
} from './periodClosePageHelpers'
import './PeriodCloseShared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<BusinessPeriodCloseRunDetail | null>(null)
const checkItems = ref<BusinessPeriodCloseCheckItem[]>([])
const loading = ref(false)
const checkLoading = ref(false)
const error = ref('')
const actionError = ref('')
const conflictAdvice = ref(false)
const actionLoading = ref(false)
const checkPagination = reactive({ page: 1, pageSize: 10, total: 0 })
const closeVisible = ref(false)
const reopenVisible = ref(false)
const closeForm = reactive({
  reason: '',
  warningAcknowledged: false,
})
const reopenForm = reactive({
  reason: '',
})

const runId = computed(() => route.params.runId as string)
const amountRestrictedReason = computed(() => restrictedMoneyReason(record.value))
const canClose = computed(() => Boolean(record.value)
  && authStore.hasPermission('system:business-period-close:close')
  && periodCloseAllowed(record.value, 'CLOSE'))
const canReopen = computed(() => Boolean(record.value)
  && authStore.hasPermission('system:business-period-close:reopen')
  && periodCloseAllowed(record.value, 'REOPEN'))
const canViewSnapshot = computed(() => Boolean(record.value?.snapshotId)
  && authStore.hasPermission('system:business-period-close:snapshot-view')
  && periodCloseAllowed(record.value, 'SNAPSHOT_VIEW'))
const closeSubmitDisabled = computed(() => actionLoading.value
  || !canClose.value
  || !closeForm.reason.trim()
  || (Number(record.value?.warningCount ?? 0) > 0 && !closeForm.warningAcknowledged))
const reopenSubmitDisabled = computed(() => actionLoading.value || !canReopen.value || !reopenForm.reason.trim())

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await businessPeriodCloseApi.runs.get(runId.value)
  } catch (caught) {
    record.value = null
    error.value = periodCloseErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadCheckItems() {
  const latestCheckId = record.value?.latestCheckId
  if (!latestCheckId) {
    checkItems.value = []
    checkPagination.total = 0
    return
  }
  checkLoading.value = true
  try {
    const page = await businessPeriodCloseApi.checks.items(runId.value, latestCheckId, {
      page: checkPagination.page,
      pageSize: checkPagination.pageSize,
    })
    checkItems.value = pageItems(page)
    checkPagination.total = pageTotal(page)
  } catch (caught) {
    actionError.value = periodCloseErrorMessage(caught)
  } finally {
    checkLoading.value = false
  }
}

async function loadAll() {
  await loadRecord()
  await loadCheckItems()
}

function back() {
  void router.push(returnLocation(route, { name: 'period-close-runs' }))
}

function viewSnapshot() {
  void router.push({
    name: 'period-close-run-snapshot',
    params: { runId: runId.value },
    query: route.query,
  })
}

function viewCheck() {
  if (!record.value?.latestCheckId) {
    return
  }
  void router.push({
    name: 'period-close-check-detail',
    params: { runId: runId.value, checkId: String(record.value.latestCheckId) },
    query: route.query,
  })
}

function openClose() {
  closeForm.reason = ''
  closeForm.warningAcknowledged = false
  actionError.value = ''
  conflictAdvice.value = false
  closeVisible.value = true
}

function openReopen() {
  reopenForm.reason = ''
  actionError.value = ''
  conflictAdvice.value = false
  reopenVisible.value = true
}

async function submitClose() {
  if (!record.value || closeSubmitDisabled.value) {
    return
  }
  actionError.value = ''
  conflictAdvice.value = false
  const reason = closeForm.reason.trim()
  if (!reason) {
    actionError.value = '请填写关闭原因'
    return
  }
  if (record.value.warningCount > 0 && !closeForm.warningAcknowledged) {
    actionError.value = '存在警告项时必须确认并填写原因'
    return
  }
  const confirmed = await confirmAction(
    `确认关闭业务期间“${record.value.periodCode}”？将生成库存、在制、项目成本和经营报表快照，并将锁定业务期间。`,
    { title: '关闭业务月结', type: 'warning', risk: 'period-close-close' },
  )
  if (!confirmed) {
    return
  }
  actionLoading.value = true
  try {
    await businessPeriodCloseApi.runs.close(record.value.runId, {
      version: record.value.version,
      sourceFingerprint: record.value.sourceFingerprint,
      warningAcknowledged: closeForm.warningAcknowledged,
      reason,
      idempotencyKey: createIdempotencyKey('period-close-close'),
    })
    closeVisible.value = false
    await loadAll()
  } catch (caught) {
    actionError.value = periodCloseErrorMessage(caught)
    conflictAdvice.value = isConflict(caught)
  } finally {
    actionLoading.value = false
  }
}

async function submitReopen() {
  if (!record.value || reopenSubmitDisabled.value) {
    return
  }
  actionError.value = ''
  conflictAdvice.value = false
  const reason = reopenForm.reason.trim()
  if (!reason) {
    actionError.value = '请填写重开原因'
    return
  }
  const confirmed = await confirmAction(
    `确认重开业务期间“${record.value.periodCode}”？旧快照会保留为历史版本，新业务可进入该期间。`,
    { title: '重开业务月结', type: 'warning', risk: 'period-close-reopen' },
  )
  if (!confirmed) {
    return
  }
  actionLoading.value = true
  try {
    await businessPeriodCloseApi.runs.reopen(record.value.runId, {
      version: record.value.version,
      reason,
      idempotencyKey: createIdempotencyKey('period-close-reopen'),
    })
    reopenVisible.value = false
    await loadAll()
  } catch (caught) {
    actionError.value = periodCloseErrorMessage(caught)
    conflictAdvice.value = isConflict(caught)
  } finally {
    actionLoading.value = false
  }
}

function isConflict(error: unknown): boolean {
  const status = Number((error as { status?: unknown } | null)?.status)
  const message = periodCloseErrorMessage(error)
  return status === 409 || message.includes('来源已变化') || message.includes('冲突')
}

function changeCheckPage(page: number) {
  checkPagination.page = page
  void loadCheckItems()
}

function changeCheckPageSize(pageSize: number) {
  checkPagination.pageSize = pageSize
  checkPagination.page = 1
  void loadCheckItems()
}

onMounted(loadAll)
</script>

<template>
  <MasterDataTableView title="月结详情" description="查看单个业务期间月结运行、最近检查、快照摘要、历史版本和审计记录。">
    <template #actions>
      <el-button @click="back">返回</el-button>
      <el-button v-if="record?.latestCheckId" data-test="period-close-check-detail-link" @click="viewCheck">检查详情</el-button>
      <el-button v-if="canViewSnapshot" data-test="period-close-snapshot-link" type="primary" plain @click="viewSnapshot">查看快照</el-button>
      <el-button
        v-if="canClose"
        data-test="period-close-close"
        type="warning"
        :loading="actionLoading"
        :title="periodCloseActionDisabledReason(record, 'CLOSE')"
        @click="openClose"
      >
        关闭月结
      </el-button>
      <el-button
        v-if="canReopen"
        data-test="period-close-reopen"
        type="danger"
        :loading="actionLoading"
        :title="periodCloseActionDisabledReason(record, 'REOPEN')"
        @click="openReopen"
      >
        重开期间
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="conflictAdvice" class="state-alert" type="warning" :title="periodCloseMessages.conflictAdvice" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="月结详情加载中" :closable="false" />
    </template>

    <div v-if="record">
      <section class="period-close-summary-strip">
        <div><span>期间</span><strong>{{ record.periodCode }}</strong></div>
        <div><span>起止日期</span><strong>{{ record.startDate }} 至 {{ record.endDate }}</strong></div>
        <div><span>月结状态</span><PeriodCloseStatusTag :status="record.closeStatus" :label="record.closeStatusName" /></div>
        <div><span>业务期间状态</span><strong>{{ record.periodStatusName || businessPeriodStatusLabel(record.periodStatus) }}</strong></div>
        <div><span>版本</span><strong>{{ record.revisionNo }} / {{ record.version }}</strong></div>
        <div><span>最近检查</span><strong>{{ formatPeriodCloseDateTime(record.latestCheckedAt) }}</strong></div>
        <div><span>阻断/警告</span><strong>{{ record.blockingCount }} / {{ record.warningCount }}</strong></div>
        <div><span>快照价值</span><strong>{{ formatPeriodCloseAmount(record.snapshotValueAmount, amountRestrictedReason) }}</strong></div>
      </section>

      <section class="period-close-section">
        <div class="period-close-section-heading">
          <span class="period-close-section-title">最近检查清单</span>
          <span class="period-close-muted">阻断项不能关闭；警告项关闭前必须确认并填写原因。</span>
        </div>
        <div class="table-scroll">
          <el-table :data="checkItems" :empty-text="checkLoading ? '加载中' : '暂无检查项'" stripe>
            <el-table-column label="领域" min-width="110">
              <template #default="{ row }">{{ periodCloseDomainLabel(row.domain) }}</template>
            </el-table-column>
            <el-table-column label="级别" min-width="90">
              <template #default="{ row }"><PeriodCloseSeverityTag :severity="row.severity" /></template>
            </el-table-column>
            <el-table-column prop="title" label="结论" min-width="190" show-overflow-tooltip />
            <el-table-column prop="businessImpact" label="影响口径" min-width="180" show-overflow-tooltip />
            <el-table-column prop="suggestion" label="处理建议" min-width="220" show-overflow-tooltip />
          </el-table>
        </div>
        <el-pagination
          class="table-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="checkPagination.total"
          :page-size="checkPagination.pageSize"
          :current-page="checkPagination.page"
          @current-change="changeCheckPage"
          @size-change="changeCheckPageSize"
        />
      </section>

      <div class="period-close-section-grid">
        <section class="period-close-section">
          <span class="period-close-section-title">历史版本</span>
          <p v-for="version in record.historyVersions" :key="`${version.runId}-${version.revisionNo}`">
            版本 {{ version.revisionNo }}：{{ periodCloseStatusLabel(version.closeStatus) }} · {{ formatPeriodCloseDateTime(version.closedAt || version.reopenedAt) }}
          </p>
          <el-empty v-if="record.historyVersions.length === 0" description="暂无历史版本" />
        </section>
        <section class="period-close-section">
          <span class="period-close-section-title">审计摘要</span>
          <p v-for="audit in record.auditSummary" :key="`${audit.action}-${audit.createdAt}`">
            {{ periodCloseAuditActionLabel(audit.action) }} · {{ audit.operatorName || audit.operatorUsername || '-' }} · {{ formatPeriodCloseDateTime(audit.createdAt) }} · {{ audit.reason || '-' }}
          </p>
          <el-empty v-if="record.auditSummary.length === 0" description="暂无审计摘要" />
        </section>
      </div>
    </div>

    <el-dialog v-model="closeVisible" title="关闭业务月结" width="560px">
      <el-alert v-if="actionError" class="form-alert" type="error" :title="actionError" :closable="false" />
      <p class="period-close-dialog-summary">
        当前期间：{{ record?.periodCode }}；阻断 {{ record?.blockingCount ?? 0 }}，警告 {{ record?.warningCount ?? 0 }}。
        关闭将生成库存、在制、项目成本和经营报表快照，并将锁定业务期间。
      </p>
      <el-form label-position="top">
        <el-form-item label="关闭原因">
          <el-input
            v-model="closeForm.reason"
            name="period-close-close-reason"
            type="textarea"
            :rows="4"
            maxlength="300"
            show-word-limit
            placeholder="请填写警告确认、关闭依据或管理说明"
          />
        </el-form-item>
        <label class="period-close-checkbox">
          <input v-model="closeForm.warningAcknowledged" data-test="period-close-warning-ack" type="checkbox">
          <span>确认警告项已由业务负责人判断可关闭</span>
        </label>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button
          data-test="submit-period-close-close"
          type="warning"
          :loading="actionLoading"
          :disabled="closeSubmitDisabled"
          @click="submitClose"
        >
          确认关闭
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reopenVisible" title="重开业务期间" width="560px">
      <el-alert v-if="actionError" class="form-alert" type="error" :title="actionError" :closable="false" />
      <p class="period-close-dialog-summary">旧快照将保留为历史版本，新业务可进入该期间；重开原因必填。</p>
      <el-form label-position="top">
        <el-form-item label="重开原因">
          <el-input
            v-model="reopenForm.reason"
            name="period-close-reopen-reason"
            type="textarea"
            :rows="4"
            maxlength="300"
            show-word-limit
            placeholder="请填写重开原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reopenVisible = false">取消</el-button>
        <el-button
          data-test="submit-period-close-reopen"
          type="danger"
          :loading="actionLoading"
          :disabled="reopenSubmitDisabled"
          @click="submitReopen"
        >
          确认重开
        </el-button>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>
