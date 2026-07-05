<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeApi, type ReceivableDetailRecord, type ResourceId } from '../../shared/api/financeApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import FinanceSourceTracePanel from './FinanceSourceTracePanel.vue'
import ReceivableStatusTag from './ReceivableStatusTag.vue'
import ReceiptStatusTag from './ReceiptStatusTag.vue'
import {
  financeErrorMessage,
  financePermissions,
  formatFinanceAmount,
} from './financePageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<ReceivableDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canEdit = computed(() => record.value?.status === 'DRAFT' && authStore.hasPermission(financePermissions.receivableUpdate))
const canConfirm = computed(() => record.value?.status === 'DRAFT' && authStore.hasPermission(financePermissions.receivableConfirm))
const canCancel = computed(() => Boolean(record.value) && authStore.hasPermission(financePermissions.receivableCancel)
  && (record.value?.status === 'DRAFT' || (record.value?.status === 'CONFIRMED' && Number(record.value?.receivedAmount) <= 0)))
const canClose = computed(() => Boolean(record.value) && authStore.hasPermission(financePermissions.receivableClose)
  && (record.value?.status === 'CONFIRMED' || record.value?.status === 'PARTIALLY_RECEIVED'))
const canCreateReceipt = computed(() => Boolean(record.value) && authStore.hasPermission(financePermissions.receiptCreate)
  && (record.value?.status === 'CONFIRMED' || record.value?.status === 'PARTIALLY_RECEIVED'))
const canViewReceipt = computed(() => authStore.hasPermission(financePermissions.receiptView))
const canViewSalesOrder = computed(() => authStore.hasPermission('sales:order:view'))
const canViewSalesShipment = computed(() => authStore.hasPermission('sales:shipment:view'))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await financeApi.receivables.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'finance-receivables' }))
}

function editReceivable() {
  if (record.value) {
    void router.push({
    name: 'finance-receivable-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
  }
}

function createReceipt() {
  if (record.value) {
    void router.push({ name: 'finance-receipt-create', params: { id: String(record.value.id) } })
  }
}

function viewReceipt(id: ResourceId) {
  void router.push({
    name: 'finance-receipt-detail',
    params: { id: String(id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function viewSalesOrder(id: ResourceId) {
  void router.push({
    name: 'sales-order-detail',
    params: { id: String(id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function viewSalesShipment(id: ResourceId) {
  void router.push({
    name: 'sales-shipment-detail',
    params: { id: String(id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function auditText(audit: unknown) {
  if (typeof audit === 'string') {
    return audit
  }
  if (audit && typeof audit === 'object') {
    const record = audit as Record<string, unknown>
    return [record.action, record.operatorName, record.operatedAt].filter(Boolean).join(' / ')
  }
  return String(audit)
}

async function runReceivableAction(action: 'confirm' | 'cancel' | 'close') {
  if (!record.value || actionLoading.value) {
    return
  }
  const labels = { confirm: '确认', cancel: '取消', close: '关闭' }
  if (!(await confirmAction(`确认${labels[action]}应收“${record.value.receivableNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'confirm') {
      await financeApi.receivables.confirm(record.value.id)
    } else if (action === 'cancel') {
      await financeApi.receivables.cancel(record.value.id)
    } else {
      await financeApi.receivables.close(record.value.id)
    }
    await loadRecord()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="应收详情" description="查看应收主信息、金额进度、来源追溯和收款记录。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canEdit" data-test="edit-receivable-detail" type="primary" @click="editReceivable">编辑</el-button>
      <el-button v-if="canConfirm" data-test="confirm-receivable-detail" type="success" :loading="actionLoading" :disabled="actionLoading" @click="runReceivableAction('confirm')">确认</el-button>
      <el-button v-if="canCancel" data-test="cancel-receivable-detail" type="danger" :loading="actionLoading" :disabled="actionLoading" @click="runReceivableAction('cancel')">取消</el-button>
      <el-button v-if="canClose" data-test="close-receivable-detail" type="warning" :loading="actionLoading" :disabled="actionLoading" @click="runReceivableAction('close')">关闭</el-button>
      <el-button v-if="canCreateReceipt" data-test="create-receipt-detail" type="primary" plain @click="createReceipt">登记收款</el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="应收详情加载中" :closable="false" />
      <el-alert v-if="record && ['RECEIVED', 'CLOSED', 'CANCELLED'].includes(record.status)" class="state-alert" type="warning" title="当前应收不可继续登记收款" :closable="false" />
    </template>

    <div v-if="record" class="finance-detail">
      <section class="summary-strip">
        <div><span>应收金额</span><strong>{{ formatFinanceAmount(record.totalAmount) }}</strong></div>
        <div><span>已收金额</span><strong>{{ formatFinanceAmount(record.receivedAmount) }}</strong></div>
        <div><span>未收金额</span><strong>{{ formatFinanceAmount(record.unreceivedAmount) }}</strong></div>
        <div><span>到期日期</span><strong>{{ record.dueDate }}</strong></div>
        <div><span>状态</span><ReceivableStatusTag :status="record.status" /></div>
      </section>

      <dl class="detail-list">
        <dt>应收单号</dt><dd>{{ record.receivableNo }}</dd>
        <dt>客户</dt><dd>{{ record.customerCode }} {{ record.customerName }}</dd>
        <dt>来源销售出库</dt><dd>{{ record.sourceNo }}</dd>
        <dt>来源销售订单</dt><dd>{{ record.salesOrderNo }}</dd>
        <dt>业务日期</dt><dd>{{ record.businessDate }}</dd>
        <dt>创建人</dt><dd>{{ record.createdByName }}</dd>
        <dt>更新时间</dt><dd>{{ record.updatedAt }}</dd>
        <dt>备注</dt><dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <FinanceSourceTracePanel
        :sources="record.sources"
        :can-view-sales-order="canViewSalesOrder"
        :can-view-sales-shipment="canViewSalesShipment"
        @view-sales-order="viewSalesOrder"
        @view-sales-shipment="viewSalesShipment"
      />

      <section class="section-block">
        <div class="section-title">收款记录</div>
        <el-empty v-if="record.receipts.length === 0" description="暂无收款记录" />
        <div v-else class="table-scroll">
          <el-table :data="record.receipts" empty-text="暂无收款记录" stripe>
            <el-table-column prop="receiptNo" label="收款单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="receiptDate" label="收款日期" min-width="110" />
            <el-table-column label="收款金额" min-width="130" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column prop="method" label="方式" min-width="120" />
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }"><ReceiptStatusTag :status="row.status" /></template>
            </el-table-column>
            <el-table-column prop="postedByName" label="过账人" min-width="110" />
            <el-table-column prop="postedAt" label="过账时间" min-width="160" show-overflow-tooltip />
            <el-table-column v-if="canViewReceipt" label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text data-test="view-receipt-summary" @click="viewReceipt(row.id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-title">操作记录</div>
        <el-empty v-if="!record.auditSummary?.length" description="暂无操作记录" />
        <ul v-else class="audit-list">
          <li v-for="(audit, index) in record.auditSummary" :key="index">{{ auditText(audit) }}</li>
        </ul>
      </section>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.finance-detail {
  padding: 14px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
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

.summary-strip strong,
.numeric-cell {
  font-variant-numeric: tabular-nums;
}

.detail-list {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr) 110px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.detail-list dt {
  color: var(--qherp-muted);
}

.detail-list dd {
  margin: 0;
  min-width: 0;
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
  min-width: 84px;
  text-align: right;
}

.audit-list {
  margin: 0;
  padding-left: 18px;
}

@media (max-width: 900px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .detail-list {
    grid-template-columns: 96px minmax(0, 1fr);
  }
}
</style>
