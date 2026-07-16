<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import { procurementApi, type ProcurementRequisitionDetailRecord, type ResourceId } from '../../shared/api/procurementApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import {
  formatProcurementDateTime,
  formatProcurementQuantity,
  procurementErrorMessage,
  procurementOwnershipDisplay,
  procurementRequisitionStatusLabel,
  procurementRequisitionStatusTagType,
} from './procurementPageHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const record = ref<ProcurementRequisitionDetailRecord | null>(null)
const pageTitle = computed(() => record.value?.requisitionNo ?? '采购请购详情')
const pageDescription = '查看采购请购的状态、来源、明细、审批与转化进度。'
const approvalStatusLabels: Record<string, string> = {
  NONE: '未提交',
  NOT_SUBMITTED: '未提交',
  DRAFT: '未提交',
  SUBMITTED: '审批中',
  IN_APPROVAL: '审批中',
  APPROVED: '审批通过',
  REJECTED: '审批驳回',
  WITHDRAWN: '已撤回',
  CANCELLED: '已取消',
}
const approvalStatusTagTypes: Record<string, 'info' | 'success' | 'warning' | 'danger'> = {
  NONE: 'info',
  NOT_SUBMITTED: 'info',
  DRAFT: 'info',
  SUBMITTED: 'warning',
  IN_APPROVAL: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'warning',
  CANCELLED: 'danger',
}
const sourceTypeLabels: Record<string, string> = {
  PROJECT: '项目来源',
  REQUISITION: '请购来源',
  INQUIRY: '询价来源',
  QUOTE: '报价来源',
  AGREEMENT: '协议来源',
  ORDER: '订单来源',
}

function allowed(action: string): boolean {
  return Boolean(record.value?.allowedActions?.includes(action))
}

const canSubmitApproval = computed(() => (
  (allowed('SUBMIT_APPROVAL') || allowed('SUBMIT')) && authStore.hasPermission('procurement:requisition:submit')
))
const canCreateInquiry = computed(() => allowed('CREATE_INQUIRY') && authStore.hasPermission('procurement:inquiry:create'))
const canCreateOrder = computed(() => allowed('CREATE_ORDER') && authStore.hasPermission('procurement:order:create'))
const canClose = computed(() => allowed('CLOSE') && authStore.hasPermission('procurement:requisition:close'))
const sourceChain = computed(() => record.value?.sourceChain ?? [])
const sourceSummary = computed(() => {
  if (sourceChain.value.length === 0) {
    return '暂无来源链'
  }
  return sourceChain.value
    .map((source) => [source.sourceNo, source.summary].filter(Boolean).join(' '))
    .join('；')
})
const businessStatusText = computed(() => procurementRequisitionStatusLabel(record.value?.status, record.value?.statusName))
const businessStatusTagType = computed(() => procurementRequisitionStatusTagType(record.value?.status))
const approvalStatusText = computed(() => statusLabel(record.value?.approvalStatus, record.value?.approvalStatusName, approvalStatusLabels, '未提交'))
const approvalStatusTagType = computed(() => statusTagType(record.value?.approvalStatus, approvalStatusTagTypes))
const closeStateText = computed(() => record.value?.closeReason ? '已结案' : '未结案')
const materialSummaryText = computed(() => {
  const explicitSummary = record.value?.materialSummary?.trim()
  if (explicitSummary) {
    return explicitSummary
  }
  const materialLabels = (record.value?.lines ?? [])
    .map((line) => [line.materialCode, line.materialName].filter(Boolean).join(' ').trim())
    .filter(Boolean)
  if (materialLabels.length === 0) {
    return '-'
  }
  if (materialLabels.length <= 2) {
    return materialLabels.join('、')
  }
  return `${materialLabels.slice(0, 2).join('、')}等${materialLabels.length}项`
})

function normalizedCode(value: unknown): string {
  return String(value ?? '').trim().toUpperCase()
}

function isRawCodeText(value: string): boolean {
  return /^[A-Z][A-Z0-9_]*$/.test(value)
}

function statusLabel(
  status: unknown,
  statusName: unknown,
  labels: Record<string, string>,
  emptyFallback: string,
): string {
  const code = normalizedCode(status)
  const displayName = String(statusName ?? '').trim()
  if (displayName && displayName !== code && !isRawCodeText(displayName)) {
    return displayName
  }
  if (!code) {
    return emptyFallback
  }
  return labels[code] ?? '未知状态'
}

function statusTagType(
  status: unknown,
  types: Record<string, 'info' | 'success' | 'warning' | 'danger'>,
): 'info' | 'success' | 'warning' | 'danger' {
  return types[normalizedCode(status)] ?? 'info'
}

function displayValue(value: unknown, fallback = '-'): string {
  if (value === null || value === undefined || value === '') {
    return fallback
  }
  return String(value)
}

function procurementModeShortText(source: unknown): string {
  const mode = normalizedCode((source as { procurementMode?: unknown; purchaseMode?: unknown; ownershipType?: unknown } | null)?.procurementMode
    ?? (source as { purchaseMode?: unknown } | null)?.purchaseMode
    ?? (source as { ownershipType?: unknown } | null)?.ownershipType)
  if (mode === 'PROJECT') {
    return '项目专采'
  }
  if (mode === 'PUBLIC') {
    return '公共采购'
  }
  return '未知模式'
}

function sourceTypeLabel(value: unknown): string {
  return sourceTypeLabels[normalizedCode(value)] ?? '来源'
}

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await procurementApi.requisitions.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'procurement-requisitions' }))
}

onMounted(() => {
  void loadRecord()
})

async function submitApproval() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.requisitions.submitApproval(record.value.id, {
      version: record.value.version,
      reason: '提交采购请购审批',
      idempotencyKey: createIdempotencyKey('requisition-submit-approval'),
    })
    await loadRecord()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

async function closeRequisition() {
  if (!record.value || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.requisitions.close(record.value.id, {
      version: record.value.version,
      reason: '请购结案',
      idempotencyKey: createIdempotencyKey('requisition-close'),
    })
    await loadRecord()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

function createInquiryFromRequisition() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-inquiry-create',
    query: queryWithReturnTo({ requisitionId: String(record.value.id) }, currentRouteReturnTo(route)),
  })
}

function createOrderFromRequisition() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-order-create',
    query: queryWithReturnTo({ requisitionId: String(record.value.id) }, currentRouteReturnTo(route)),
  })
}
</script>

<template>
  <MasterDataTableView :title="pageTitle" :description="pageDescription">
    <template #actions>
      <el-button data-test="back-requisition-list" @click="backToList">返回列表</el-button>
      <template v-if="record">
        <el-button
          v-if="canSubmitApproval"
          data-test="submit-requisition-approval"
          type="primary"
          :loading="actionLoading"
          :disabled="actionLoading"
          @click="submitApproval"
        >
          提交审批
        </el-button>
        <el-button
          v-if="canCreateInquiry"
          data-test="create-inquiry-from-requisition"
          :disabled="actionLoading"
          @click="createInquiryFromRequisition"
        >
          创建询价
        </el-button>
        <el-button
          v-if="canCreateOrder"
          data-test="create-order-from-requisition"
          :disabled="actionLoading"
          @click="createOrderFromRequisition"
        >
          转采购订单
        </el-button>
        <el-button
          v-if="canClose"
          data-test="close-requisition"
          type="warning"
          :loading="actionLoading"
          :disabled="actionLoading"
          @click="closeRequisition"
        >
          结案
        </el-button>
      </template>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <el-alert v-if="loading" class="page-alert" type="info" title="采购请购详情加载中" show-icon :closable="false" />
    </template>

    <section v-if="!loading && !record && error" class="section-block empty-detail-state">
      <div class="section-title">无法加载采购请购</div>
      <p>{{ error }}</p>
      <el-button data-test="back-requisition-list-empty" @click="backToList">返回请购列表</el-button>
    </section>

    <div v-if="record" class="purchase-requisition-detail">
      <section class="summary-strip">
        <div>
          <span>业务状态</span>
          <el-tag data-test="requisition-business-status" size="small" :type="businessStatusTagType">
            {{ businessStatusText }}
          </el-tag>
        </div>
        <div>
          <span>审批状态</span>
          <el-tag data-test="requisition-approval-status" size="small" :type="approvalStatusTagType">
            {{ approvalStatusText }}
          </el-tag>
        </div>
        <div>
          <span>总数量</span>
          <strong>{{ formatProcurementQuantity(record.totalQuantity) }}</strong>
        </div>
        <div>
          <span>已转</span>
          <strong>{{ formatProcurementQuantity(record.orderedQuantity) }}</strong>
        </div>
        <div>
          <span>剩余</span>
          <strong>{{ formatProcurementQuantity(record.remainingQuantity) }}</strong>
        </div>
        <div>
          <span>需求日期</span>
          <strong>{{ displayValue(record.requiredDate) }}</strong>
        </div>
        <div>
          <span>采购模式</span>
          <strong data-test="requisition-summary-mode">{{ procurementModeShortText(record) }}</strong>
        </div>
        <div>
          <span>结案状态</span>
          <strong>{{ closeStateText }}</strong>
        </div>
      </section>

      <dl class="purchase-requisition-detail-list">
        <dt>请购号</dt>
        <dd>{{ displayValue(record.requisitionNo) }}</dd>
        <dt>标题</dt>
        <dd>{{ displayValue(record.title, '未填写') }}</dd>
        <dt>采购模式</dt>
        <dd>{{ procurementOwnershipDisplay(record) }}</dd>
        <dt>项目/公共</dt>
        <dd>{{ procurementOwnershipDisplay(record) }}</dd>
        <dt>物料摘要</dt>
        <dd data-test="requisition-material-summary">{{ materialSummaryText }}</dd>
        <dt>来源摘要</dt>
        <dd>{{ sourceSummary }}</dd>
        <dt>创建人</dt>
        <dd>{{ displayValue(record.createdByName) }}</dd>
        <dt>创建时间</dt>
        <dd>{{ formatProcurementDateTime(record.createdAt) }}</dd>
        <dt>更新时间</dt>
        <dd>{{ formatProcurementDateTime(record.updatedAt) }}</dd>
        <dt>备注</dt>
        <dd>{{ displayValue(record.remark, '未填写') }}</dd>
        <dt>结案原因</dt>
        <dd>{{ displayValue(record.closeReason, '未结案') }}</dd>
      </dl>

      <section class="section-block">
        <div class="section-title">请购明细</div>
        <div class="table-scroll">
          <el-table :data="record.lines" empty-text="暂无请购明细" stripe>
            <el-table-column prop="lineNo" label="行号" width="78" />
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column prop="materialSpec" label="规格" min-width="120" show-overflow-tooltip />
            <el-table-column prop="unitName" label="单位" min-width="90" />
            <el-table-column label="项目/公共" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ procurementOwnershipDisplay(row) }}</template>
            </el-table-column>
            <el-table-column prop="requiredDate" label="需求日期" min-width="120" />
            <el-table-column prop="suggestedSupplierName" label="建议供应商" min-width="150" show-overflow-tooltip />
            <el-table-column label="数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已转" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.orderedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="剩余" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.remainingQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="税率" min-width="100" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.taxRate) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="purpose" label="用途" min-width="200" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-title">来源链</div>
        <dl class="purchase-requisition-detail-list trace-list">
            <template v-if="sourceChain.length">
            <template v-for="source in sourceChain" :key="`${source.sourceType}-${source.sourceNo}`">
              <dt>{{ sourceTypeLabel(source.sourceType) }}</dt>
              <dd>{{ displayValue(source.sourceNo) }} {{ displayValue(source.summary) }}</dd>
            </template>
          </template>
          <template v-else>
            <dt>来源链</dt>
            <dd>暂无来源链</dd>
          </template>
        </dl>
      </section>

      <section class="section-block">
        <div class="section-title">审批与附件</div>
        <dl class="purchase-requisition-detail-list">
          <dt>审批状态</dt>
          <dd>审批状态：{{ approvalStatusText }}</dd>
          <dt>审批实例</dt>
          <dd>{{ record.approvalInstanceId ? `#${record.approvalInstanceId}` : '未提交' }}</dd>
          <dt>附件</dt>
          <dd>附件随 022 平台能力展示。</dd>
        </dl>
      </section>

      <section class="section-block">
        <div class="section-title">审计</div>
        <dl class="purchase-requisition-detail-list">
          <dt>创建人</dt>
          <dd>{{ displayValue(record.createdByName) }}</dd>
          <dt>创建时间</dt>
          <dd>{{ formatProcurementDateTime(record.createdAt) }}</dd>
          <dt>更新时间</dt>
          <dd>{{ formatProcurementDateTime(record.updatedAt) }}</dd>
          <dt>版本</dt>
          <dd>{{ record.version }}</dd>
        </dl>
      </section>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.purchase-requisition-detail {
  padding: 14px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.summary-strip > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  padding: 10px 12px;
}

.summary-strip span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.summary-strip strong {
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.purchase-requisition-detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.purchase-requisition-detail-list dt {
  color: var(--qherp-muted);
}

.purchase-requisition-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

.section-block {
  margin-top: 16px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.empty-detail-state {
  margin: 14px;
}

.empty-detail-state p {
  margin: 0 0 12px;
}

.trace-list {
  margin-bottom: 0;
}

@media (max-width: 900px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .purchase-requisition-detail-list {
    grid-template-columns: 88px minmax(0, 1fr);
  }
}
</style>
