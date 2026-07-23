<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  productionOutsourcingApi,
  type OutsourcingOrderDetailRecord,
  type OutsourcingTraceLink,
} from '../../../shared/api/productionOutsourcingApi'
import { confirmAction } from '../../../shared/ui/confirmDialog'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import {
  productionDocumentStatusLabel,
  productionErrorMessage,
  formatProductionQuantity,
  outsourcingOrderStatusLabel,
} from '../productionPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<OutsourcingOrderDetailRecord | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

const canRelease = computed(() => authStore.hasPermission('production:outsourcing:release'))
const canClose = computed(() => authStore.hasPermission('production:outsourcing:close'))
const canCancel = computed(() => authStore.hasPermission('production:outsourcing:cancel'))
const canIssue = computed(() => authStore.hasPermission('production:outsourcing-issue:create'))
const canReceipt = computed(() => authStore.hasPermission('production:outsourcing-receipt:create'))

function hasAction(action: string) {
  return Boolean(record.value?.allowedActions?.includes(action))
}

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await productionOutsourcingApi.orders.get(route.params.id as string)
  } catch (caught) {
    record.value = null
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function ownershipText() {
  if (record.value?.ownershipType === 'PROJECT') {
    return [record.value.projectNo, record.value.projectName].filter(Boolean).join(' ') || '项目未返回'
  }
  return '公共/未绑定'
}

function canCreateIssueDocument() {
  return canIssue.value && hasAction('ISSUE')
}

function canCreateReceiptDocument() {
  return canReceipt.value && hasAction('RECEIPT')
}

const showDetailMoreActions = computed(() => Boolean(record.value)
  && (canRelease.value || canClose.value || canCancel.value || canIssue.value))

function traceTarget(link: OutsourcingTraceLink) {
  if (link.restricted) {
    return null
  }
  if (link.routeName) {
    return link.targetId === undefined || link.targetId === null
      ? { name: link.routeName }
      : { name: link.routeName, params: { id: String(link.targetId) } }
  }
  return link.routePath || null
}

function openTraceLink(link: OutsourcingTraceLink) {
  const target = traceTarget(link)
  if (!target) {
    return
  }
  void router.push(target)
}

function openIssueDocument() {
  if (!record.value || !canCreateIssueDocument()) {
    return
  }
  void router.push({
    name: 'production-outsourcing-order-material-issues',
    params: { id: String(record.value.id) },
  })
}

function openReceiptDocument() {
  if (!record.value || !canCreateReceiptDocument()) {
    return
  }
  void router.push({
    name: 'production-outsourcing-order-receipts',
    params: { id: String(record.value.id) },
  })
}

async function runOrderAction(action: 'release' | 'close' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const labels = { release: '发布', close: '关闭', cancel: '取消' }
  if (!(await confirmAction(`确认${labels[action]}外协订单“${record.value.orderNo}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = {
      version: record.value.version,
      idempotencyKey: createIdempotencyKey(`production-outsourcing-${action}`),
    }
    if (action === 'release') {
      await productionOutsourcingApi.orders.release(record.value.id, payload)
    } else if (action === 'close') {
      await productionOutsourcingApi.orders.close(record.value.id, payload)
    } else {
      await productionOutsourcingApi.orders.cancel(record.value.id, payload)
    }
    await loadRecord()
  } catch (caught) {
    actionError.value = productionErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function handleDetailMoreAction(command: string | number | object) {
  const action = String(command)
  if (action === 'release' && canRelease.value && hasAction('RELEASE')) {
    await runOrderAction('release')
  } else if (action === 'close' && canClose.value && hasAction('CLOSE')) {
    await runOrderAction('close')
  } else if (action === 'cancel' && canCancel.value && hasAction('CANCEL')) {
    await runOrderAction('cancel')
  } else if (action === 'issue') {
    openIssueDocument()
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="外协订单详情" description="查看外协项目归属、供应商、来源建议、用料、发料、收货和追溯入口。">
    <template #actions>
      <div
        data-test="outsourcing-detail-actions"
        class="outsourcing-detail-actions outsourcing-detail-actions--single-line"
      >
        <el-button data-test="back-outsourcing-order-list" @click="router.push({ name: 'production-outsourcing-orders' })">返回列表</el-button>
        <el-button
          v-if="record && canReceipt"
          data-test="create-outsourcing-receipt"
          type="primary"
          :disabled="!canCreateReceiptDocument()"
          :title="canCreateReceiptDocument() ? '' : record.actionDisabledReason || '当前外协订单不可收货'"
          @click="openReceiptDocument"
        >
          外协收货
        </el-button>
        <el-dropdown
          v-if="showDetailMoreActions"
          data-test="outsourcing-detail-more-actions"
          trigger="click"
          @command="handleDetailMoreAction"
        >
          <el-button data-test="outsourcing-detail-more-trigger">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-if="record && canRelease"
                data-test="release-outsourcing-order"
                command="release"
                :disabled="actionLoading || !hasAction('RELEASE')"
              >
                发布
              </el-dropdown-item>
              <el-dropdown-item
                v-if="record && canClose"
                data-test="close-outsourcing-order"
                command="close"
                :disabled="actionLoading || !hasAction('CLOSE')"
              >
                关闭
              </el-dropdown-item>
              <el-dropdown-item
                v-if="record && canCancel"
                data-test="cancel-outsourcing-order"
                class="outsourcing-detail-more-action--danger"
                command="cancel"
                :disabled="actionLoading || !hasAction('CANCEL')"
              >
                取消
              </el-dropdown-item>
              <el-dropdown-item
                v-if="record && canIssue"
                data-test="create-outsourcing-issue"
                command="issue"
                :disabled="!canCreateIssueDocument()"
                :title="canCreateIssueDocument() ? '' : record.actionDisabledReason || '当前外协订单不可发料'"
              >
                外协发料
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </template>

    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="外协订单加载中" :closable="false" />
      <el-alert
        v-if="record?.actionDisabledReason"
        type="warning"
        :title="record.actionDisabledReason"
        :closable="false"
      />
    </template>

    <section v-if="record" class="section-block">
      <div class="detail-title-line">
        <div>
          <h2>{{ record.orderNo }}</h2>
          <p>{{ ownershipText() }} · {{ record.supplierName }}</p>
        </div>
        <el-tag>{{ outsourcingOrderStatusLabel(record.status, record.statusName) }}</el-tag>
      </div>
      <div data-test="outsourcing-detail-summary" class="summary-strip outsourcing-detail-summary">
        <div><strong>计划数量</strong><span>{{ formatProductionQuantity(record.plannedQuantity) }}</span></div>
        <div><strong>已发数量</strong><span>{{ formatProductionQuantity(record.issuedQuantity) }}</span></div>
        <div><strong>已收数量</strong><span>{{ formatProductionQuantity(record.receivedQuantity) }}</span></div>
        <div><strong>来源建议</strong><span>{{ record.sourceSuggestionNo || record.sourceMrpSuggestionId || '-' }}</span></div>
        <div><strong>BOM</strong><span>{{ [record.bomCode, record.bomVersionCode].filter(Boolean).join(' / ') || '-' }}</span></div>
        <div><strong>成本状态</strong><span>{{ record.costVisible === false ? '成本受限' : '可查看' }}</span></div>
      </div>
      <dl data-test="outsourcing-detail-fields" class="outsourcing-detail-fields">
        <div>
          <dt>项目/归属</dt>
          <dd>{{ ownershipText() }}</dd>
        </div>
        <div>
          <dt>供应商</dt>
          <dd>{{ record.supplierName }}</dd>
        </div>
        <div>
          <dt>物料</dt>
          <dd>{{ record.productMaterialCode }} {{ record.productMaterialName }}</dd>
        </div>
        <div>
          <dt>计划发料</dt>
          <dd>{{ record.plannedIssueDate || '-' }}</dd>
        </div>
        <div>
          <dt>计划收货</dt>
          <dd>{{ record.plannedReceiptDate || '-' }}</dd>
        </div>
        <div>
          <dt>更新时间</dt>
          <dd>{{ record.updatedAt || '-' }}</dd>
        </div>
      </dl>
      <el-alert
        v-if="record.costVisible === false"
        type="warning"
        :title="record.costRestrictedReason || '成本受限'"
        :closable="false"
        show-icon
      />
    </section>

    <section v-if="record" class="section-block">
      <h2>外协用料</h2>
      <div data-test="outsourcing-materials-table-scroll" class="table-scroll">
        <el-table :data="record.materials" empty-text="暂无外协用料" stripe>
          <el-table-column prop="lineNo" label="行号" width="90" />
          <el-table-column label="材料" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
          </el-table-column>
          <el-table-column label="需求/已发" min-width="150" align="right">
            <template #default="{ row }">{{ formatProductionQuantity(row.requiredQuantity) }} / {{ formatProductionQuantity(row.issuedQuantity) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section v-if="record" class="section-block">
      <h2>发料与收货</h2>
      <div data-test="outsourcing-executions-table-scroll" class="table-scroll">
        <el-table :data="[...record.materialIssues, ...record.receipts]" empty-text="暂无执行单据" stripe>
          <el-table-column label="单号" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.issueNo || row.receiptNo || row.documentNo }}</template>
          </el-table-column>
          <el-table-column label="状态" min-width="110">
            <template #default="{ row }">{{ productionDocumentStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="businessDate" label="业务日期" min-width="120" />
          <el-table-column prop="lineCount" label="行数" min-width="90" />
        </el-table>
      </div>
    </section>

    <section v-if="record" class="section-block">
      <h2>来源追溯</h2>
      <div class="table-scroll">
        <el-table :data="record.traceLinks ?? []" :empty-text="record.sourceSuggestionNo || '暂无来源建议'" stripe>
          <el-table-column label="来源" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button
                v-if="traceTarget(row)"
                data-test="outsourcing-trace-link"
                link
                type="primary"
                @click="openTraceLink(row)"
              >
                {{ row.label }}
              </el-button>
              <span v-else>{{ row.label }}</span>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.restrictedReason" class="operation-muted">{{ row.restrictedReason }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
    <el-empty v-else-if="!loading" description="暂无外协订单详情" />
  </MasterDataTableView>
</template>

<style scoped>
.outsourcing-detail-actions {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  min-width: 0;
}

.outsourcing-detail-actions--single-line {
  flex-wrap: nowrap;
  white-space: nowrap;
}

.outsourcing-detail-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.outsourcing-detail-more-action--danger {
  color: var(--el-color-danger);
}

.section-block {
  background: var(--qherp-surface);
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  display: grid;
  gap: 12px;
  padding: 14px;
}

.section-block h2 {
  font-size: 16px;
  margin: 0;
}

.detail-title-line {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.detail-title-line h2 {
  font-size: 18px;
  margin: 0 0 4px;
}

.detail-title-line p {
  color: var(--qherp-muted);
  margin: 0;
}

.summary-strip {
  background: #f7f9fc;
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  display: grid;
  gap: 10px 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 12px 14px;
}

.summary-strip div,
.outsourcing-detail-fields > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.summary-strip strong,
.outsourcing-detail-fields dt {
  color: var(--qherp-muted);
  font-size: 12px;
  font-weight: 500;
}

.summary-strip span,
.outsourcing-detail-fields dd {
  margin: 0;
  min-width: 0;
  overflow-wrap: anywhere;
}

.outsourcing-detail-fields {
  display: grid;
  gap: 10px 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
}

.table-scroll {
  overflow-x: auto;
}

.operation-muted {
  color: var(--qherp-muted);
}
</style>
