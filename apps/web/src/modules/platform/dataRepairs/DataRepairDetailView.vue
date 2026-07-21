<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  platformGovernanceApi,
  type DataRepairDetail,
} from '../../../shared/api/platformGovernanceApi'
import { createIdempotencyKey, type ResourceId } from '../../../shared/api/documentPlatformApi'
import { confirmAction } from '../../../shared/ui/confirmDialog'
import AttachmentPanel from '../components/AttachmentPanel.vue'
import { formatPlatformDateTime, platformErrorMessage } from '../platformPageHelpers'
import { dataRepairRiskLabel, dataRepairStatusLabel, dataRepairStatusTagType } from '../platformGovernanceLabels'

const props = defineProps<{
  repairId: ResourceId
}>()

const detail = ref<DataRepairDetail | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const actionComment = ref('')

const actionSet = computed(() => new Set(detail.value?.availableActions ?? []))
const canSubmit = computed(() => actionSet.value.has('SUBMIT'))
const canExecute = computed(() => actionSet.value.has('EXECUTE'))
const canVerify = computed(() => actionSet.value.has('VERIFY'))
const canCancel = computed(() => actionSet.value.has('CANCEL'))
const detailNumber = computed(() => detail.value?.requestNo || detail.value?.repairNo || String(props.repairId))
const detailTitle = computed(() => detail.value?.riskSummary || detail.value?.title || detailNumber.value)
const targetSummary = computed(() => {
  const record = detail.value
  if (!record) {
    return '-'
  }
  return [record.targetObjectNo, record.targetObjectSummary || record.targetObjectName].filter(Boolean).join(' ') || '-'
})
const applicantName = computed(() => detail.value?.createdByUsername || detail.value?.applicantName || '-')
const summaryRows = computed(() => {
  const record = detail.value
  if (!record) {
    return []
  }
  const beforeRaw = record.beforeSummary as unknown
  const afterRaw = record.afterSummary as unknown
  if (Array.isArray(beforeRaw) || Array.isArray(afterRaw)) {
    return [...legacySummaryRows(beforeRaw), ...legacySummaryRows(afterRaw)]
  }
  const beforeSummary = objectSummary(beforeRaw)
  const afterSummary = objectSummary(afterRaw)
  if (record.changes?.length) {
    return record.changes.map((change) => ({
      fieldName: change.fieldName,
      beforeValue: change.beforeValueSummary ?? summaryValue(beforeSummary[change.fieldName]),
      afterValue: change.afterValueSummary ?? summaryValue(afterSummary[change.fieldName]),
    }))
  }
  return Array.from(new Set([...Object.keys(beforeSummary), ...Object.keys(afterSummary)]))
    .map((fieldName) => ({
      fieldName,
      beforeValue: summaryValue(beforeSummary[fieldName]),
      afterValue: summaryValue(afterSummary[fieldName]),
    }))
})
const checkRows = computed(() => (detail.value?.checks ?? []).map((item) => {
  const row = item as unknown as Record<string, unknown>
  return {
    stage: String(row.checkType ?? row.stage ?? '-'),
    status: String(row.status ?? '-'),
    summary: String(row.message ?? row.summary ?? row.code ?? '-'),
    checkedAt: String(row.createdAt ?? row.checkedAt ?? ''),
  }
}))
const timelineRows = computed(() => (detail.value?.events ?? []).map((item, index) => {
  const row = item as unknown as Record<string, unknown>
  return {
    key: `${String(row.eventType ?? row.action ?? index)}-${String(row.createdAt ?? row.occurredAt ?? index)}`,
    timestamp: String(row.createdAt ?? row.occurredAt ?? ''),
    text: eventText(row),
  }
}))
const attachmentObjectId = computed(() => detail.value?.attachmentObjectId ?? detail.value?.id ?? props.repairId)
const isReadonly = computed(() => !canSubmit.value && !canExecute.value && !canVerify.value && !canCancel.value)

function objectSummary(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }
  return value as Record<string, unknown>
}

function legacySummaryRows(value: unknown) {
  if (!Array.isArray(value)) {
    return []
  }
  return value.map((item) => {
    const row = item as Record<string, unknown>
    return {
      fieldName: String(row.fieldName ?? row.fieldCode ?? '-'),
      beforeValue: summaryValue(row.beforeValue),
      afterValue: summaryValue(row.afterValue),
    }
  })
}

function summaryValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

function eventText(row: Record<string, unknown>): string {
  const operator = String(row.operatorUsername ?? row.operatorName ?? '-')
  const detailValue = objectSummary(row.detail)
  const comment = summaryValue(detailValue.comment ?? detailValue.message ?? row.summary ?? row.eventType ?? row.action)
  const from = row.statusBefore ? String(row.statusBefore) : ''
  const to = row.statusAfter ? String(row.statusAfter) : ''
  return [operator, from && to ? `${from} 至 ${to}` : '', comment].filter(Boolean).join(' ')
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await platformGovernanceApi.dataRepairs.get(props.repairId)
  } catch (caught) {
    detail.value = null
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function isStaleActionError(caught: unknown): boolean {
  const message = caught instanceof Error ? caught.message : String(caught)
  return /过期|并发|状态|CONCURRENT|VERSION|STATUS|OBJECT_CHANGED/.test(message)
}

function freezeStaleActions(caught: unknown) {
  if (!detail.value || !isStaleActionError(caught)) {
    return false
  }
  detail.value = { ...detail.value, availableActions: [] }
  actionError.value = '数据修复动作已过期需刷新'
  return true
}

async function submitRepair() {
  if (!detail.value || actionLoading.value) {
    return
  }
  await runVersionedAction('submit', async () =>
    platformGovernanceApi.dataRepairs.submit(detail.value!.id, {
      version: detail.value!.version,
      comment: actionComment.value.trim() || undefined,
      idempotencyKey: createIdempotencyKey('data-repair-submit'),
    }))
}

async function executeRepair() {
  if (!detail.value || actionLoading.value) {
    return
  }
  const confirmed = await confirmAction(
    `确认执行数据修复“${detailNumber.value}”？执行后会形成不可变事件并进入验证。`,
    { title: '执行数据修复', risk: 'danger' },
  )
  if (!confirmed) {
    return
  }
  await runVersionedAction('execute', async () =>
    platformGovernanceApi.dataRepairs.execute(detail.value!.id, {
      version: detail.value!.version,
      comment: actionComment.value.trim() || undefined,
      idempotencyKey: createIdempotencyKey('data-repair-execute'),
    }))
}

async function verifyRepair(passed: boolean) {
  if (!detail.value || actionLoading.value) {
    return
  }
  const actionText = passed ? '验证通过' : '验证失败'
  const confirmed = await confirmAction(`确认${actionText}数据修复“${detailNumber.value}”？`, {
    title: '验证数据修复',
    risk: passed ? 'warning' : 'danger',
  })
  if (!confirmed) {
    return
  }
  await runVersionedAction('verify', async () =>
    platformGovernanceApi.dataRepairs.verify(detail.value!.id, {
      version: detail.value!.version,
      comment: actionComment.value.trim() || undefined,
      passed,
      idempotencyKey: createIdempotencyKey('data-repair-verify'),
    }))
}

async function cancelRepair() {
  if (!detail.value || actionLoading.value) {
    return
  }
  const confirmed = await confirmAction(`确认取消数据修复“${detailNumber.value}”？`, { title: '取消数据修复', risk: 'warning' })
  if (!confirmed) {
    return
  }
  await runVersionedAction('cancel', async () =>
    platformGovernanceApi.dataRepairs.cancel(detail.value!.id, {
      version: detail.value!.version,
      reason: actionComment.value.trim() || '用户取消',
      idempotencyKey: createIdempotencyKey('data-repair-cancel'),
    }))
}

async function runVersionedAction(_action: string, request: () => Promise<DataRepairDetail>) {
  actionLoading.value = true
  actionError.value = ''
  try {
    detail.value = await request()
    actionComment.value = ''
  } catch (caught) {
    if (!freezeStaleActions(caught)) {
      actionError.value = platformErrorMessage(caught)
    }
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <section class="module-page">
    <header class="page-heading">
      <div>
        <h1>数据修复详情</h1>
        <p>查看修复申请、前后摘要、证据、审批、执行、验证和不可变时间线。</p>
      </div>
    </header>

    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
    <el-alert v-if="loading" class="state-alert" type="info" title="数据修复详情加载中" :closable="false" />
    <el-alert v-if="detail && isReadonly" class="state-alert" type="info" title="当前记录为只读状态，所有动作以服务端 availableActions 为准。" :closable="false" />

    <template v-if="detail">
      <section class="section-block">
        <h2>{{ detailTitle }}</h2>
        <dl class="platform-panel-list">
          <dt>修复编号</dt><dd>{{ detailNumber }}</dd>
          <dt>对象</dt><dd>{{ targetSummary }}</dd>
          <dt>对象版本</dt><dd>V{{ detail.targetObjectVersion ?? '-' }}</dd>
          <dt>状态</dt><dd><el-tag :type="dataRepairStatusTagType(detail.status)">{{ dataRepairStatusLabel(detail.status) }}</el-tag></dd>
          <dt>风险摘要</dt><dd>{{ detail.riskLevel ? dataRepairRiskLabel(detail.riskLevel) : (detail.riskSummary || '-') }}</dd>
          <dt>原因</dt><dd>{{ detail.reason || '-' }}</dd>
          <dt>申请人</dt><dd>{{ applicantName }}</dd>
          <dt>创建时间</dt><dd>{{ formatPlatformDateTime(detail.createdAt) }}</dd>
        </dl>
      </section>

      <section class="section-block">
        <h2>前后摘要</h2>
        <div class="table-scroll">
          <el-table :data="summaryRows" empty-text="暂无前后摘要" stripe>
            <el-table-column prop="fieldName" label="字段" width="150" show-overflow-tooltip />
            <el-table-column prop="beforeValue" label="修复前" min-width="180" show-overflow-tooltip />
            <el-table-column prop="afterValue" label="修复后" min-width="180" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>预检与验证</h2>
        <div class="table-scroll">
          <el-table :data="checkRows" empty-text="暂无预检或验证记录" stripe>
            <el-table-column prop="stage" label="阶段" width="120" />
            <el-table-column prop="status" label="结果" width="120" />
            <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatPlatformDateTime(row.checkedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>证据附件</h2>
        <AttachmentPanel
          object-type="DATA_REPAIR_REQUEST"
          :object-id="attachmentObjectId"
          title="证据附件"
          :readonly="isReadonly"
        />
      </section>

      <section class="section-block">
        <h2>固定审批</h2>
        <dl class="platform-panel-list">
          <dt>审批场景</dt><dd>PLATFORM_DATA_REPAIR_EXECUTION</dd>
          <dt>审批实例</dt><dd>{{ detail.approvalSummary?.id ?? '-' }}</dd>
          <dt>审批任务</dt><dd>{{ detail.approvalSummary?.taskId ?? '-' }}</dd>
          <dt>审批状态</dt><dd>{{ detail.approvalSummary?.status || detail.approvalStatus || '-' }}</dd>
          <dt>审计摘要</dt><dd>{{ detail.auditSummary?.summary || '-' }}</dd>
        </dl>
      </section>

      <section class="section-block">
        <h2>不可变时间线</h2>
        <el-timeline>
          <el-timeline-item
            v-for="event in timelineRows"
            :key="event.key"
            :timestamp="formatPlatformDateTime(event.timestamp)"
          >
            {{ event.text }}
          </el-timeline-item>
        </el-timeline>
      </section>

      <section class="section-block">
        <h2>处理意见</h2>
        <el-input
          data-test="data-repair-action-comment"
          v-model="actionComment"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="提交、执行、验证或取消时填写处理意见"
        />
        <div class="detail-action-bar">
          <el-button v-if="canCancel" data-test="cancel-data-repair" type="warning" plain :loading="actionLoading" @click="cancelRepair">取消</el-button>
          <el-button v-if="canSubmit" data-test="submit-data-repair" type="primary" :loading="actionLoading" @click="submitRepair">提交审批</el-button>
          <el-button v-if="canExecute" data-test="execute-data-repair" type="danger" :loading="actionLoading" @click="executeRepair">执行</el-button>
          <el-button v-if="canVerify" data-test="fail-data-repair-verification" type="danger" plain :loading="actionLoading" @click="verifyRepair(false)">验证失败</el-button>
          <el-button v-if="canVerify" data-test="verify-data-repair" type="primary" :loading="actionLoading" @click="verifyRepair(true)">验证通过</el-button>
        </div>
      </section>
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
  margin-top: 12px;
}
</style>
