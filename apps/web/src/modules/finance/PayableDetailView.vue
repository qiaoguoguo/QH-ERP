<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeApi, type PayableDetailRecord, type ResourceId } from '../../shared/api/financeApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import FinanceSourceTracePanel from './FinanceSourceTracePanel.vue'
import PayableStatusTag from './PayableStatusTag.vue'
import PaymentStatusTag from './PaymentStatusTag.vue'
import {
  compareFinanceAmount,
  financeErrorMessage,
  financePermissions,
  financeMethodText,
  formatFinanceAmount,
  formatFinanceDateTime,
} from './financePageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<PayableDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canEdit = computed(() => record.value?.status === 'DRAFT' && authStore.hasPermission(financePermissions.payableUpdate))
const canConfirm = computed(() => record.value?.status === 'DRAFT' && authStore.hasPermission(financePermissions.payableConfirm))
const canCancel = computed(() => Boolean(record.value) && authStore.hasPermission(financePermissions.payableCancel)
  && (record.value?.status === 'DRAFT' || (record.value?.status === 'CONFIRMED' && compareFinanceAmount(record.value.paidAmount, '0.00') !== 1)))
const canClose = computed(() => Boolean(record.value) && authStore.hasPermission(financePermissions.payableClose)
  && (record.value?.status === 'CONFIRMED' || record.value?.status === 'PARTIALLY_PAID'))
const canCreatePayment = computed(() => Boolean(record.value) && authStore.hasPermission(financePermissions.paymentCreate)
  && (record.value?.status === 'CONFIRMED' || record.value?.status === 'PARTIALLY_PAID'))
const canViewPayment = computed(() => authStore.hasPermission(financePermissions.paymentView))
const canViewPurchaseOrder = computed(() => authStore.hasPermission('procurement:order:view'))
const canViewPurchaseReceipt = computed(() => authStore.hasPermission('procurement:receipt:view'))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await financeApi.payables.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'finance-payables' }))
}

function editPayable() {
  if (record.value) {
    void router.push({
    name: 'finance-payable-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
  }
}

function createPayment() {
  if (record.value) {
    void router.push({ name: 'finance-payment-create', params: { id: String(record.value.id) } })
  }
}

function viewPayment(id: ResourceId) {
  void router.push({
    name: 'finance-payment-detail',
    params: { id: String(id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function viewPurchaseOrder(id: ResourceId) {
  void router.push({
    name: 'procurement-order-detail',
    params: { id: String(id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function viewPurchaseReceipt(id: ResourceId) {
  void router.push({
    name: 'procurement-receipt-detail',
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

async function runPayableAction(action: 'confirm' | 'cancel' | 'close') {
  if (!record.value || actionLoading.value) {
    return
  }
  const labels = { confirm: '确认', cancel: '取消', close: '关闭' }
  if (!(await confirmAction(`确认${labels[action]}应付“${record.value.payableNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'confirm') {
      await financeApi.payables.confirm(record.value.id)
    } else if (action === 'cancel') {
      await financeApi.payables.cancel(record.value.id)
    } else {
      await financeApi.payables.close(record.value.id)
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
  <MasterDataTableView title="应付详情" description="查看应付主信息、金额进度、来源追溯和付款记录。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canEdit" data-test="edit-payable-detail" type="primary" @click="editPayable">编辑</el-button>
      <el-button v-if="canConfirm" data-test="confirm-payable-detail" type="success" :loading="actionLoading" :disabled="actionLoading" @click="runPayableAction('confirm')">确认</el-button>
      <el-button v-if="canCancel" data-test="cancel-payable-detail" type="danger" :loading="actionLoading" :disabled="actionLoading" @click="runPayableAction('cancel')">取消</el-button>
      <el-button v-if="canClose" data-test="close-payable-detail" type="warning" :loading="actionLoading" :disabled="actionLoading" @click="runPayableAction('close')">关闭</el-button>
      <el-button v-if="canCreatePayment" data-test="create-payment-detail" type="primary" plain @click="createPayment">登记付款</el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="应付详情加载中" :closable="false" />
      <el-alert v-if="record && ['PAID', 'CLOSED', 'CANCELLED'].includes(record.status)" class="state-alert" type="warning" title="当前应付不可继续登记付款" :closable="false" />
    </template>

    <div v-if="record" class="finance-detail">
      <section class="summary-strip">
        <div><span>应付金额</span><strong>{{ formatFinanceAmount(record.totalAmount) }}</strong></div>
        <div><span>已付金额</span><strong>{{ formatFinanceAmount(record.paidAmount) }}</strong></div>
        <div><span>未付金额</span><strong>{{ formatFinanceAmount(record.unpaidAmount) }}</strong></div>
        <div><span>到期日期</span><strong>{{ record.dueDate }}</strong></div>
        <div><span>状态</span><PayableStatusTag :status="record.status" /></div>
      </section>

      <dl class="detail-list">
        <dt>应付单号</dt><dd>{{ record.payableNo }}</dd>
        <dt>供应商</dt><dd>{{ record.supplierCode }} {{ record.supplierName }}</dd>
        <dt>来源采购入库</dt><dd>{{ record.sourceNo }}</dd>
        <dt>来源采购订单</dt><dd>{{ record.purchaseOrderNo }}</dd>
        <dt>业务日期</dt><dd>{{ record.businessDate }}</dd>
        <dt>创建人</dt><dd>{{ record.createdByName }}</dd>
        <dt>更新时间</dt><dd>{{ record.updatedAt }}</dd>
        <dt>备注</dt><dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <FinanceSourceTracePanel
        :sources="record.sources"
        :can-view-purchase-order="canViewPurchaseOrder"
        :can-view-purchase-receipt="canViewPurchaseReceipt"
        @view-purchase-order="viewPurchaseOrder"
        @view-purchase-receipt="viewPurchaseReceipt"
      />

      <section class="section-block">
        <div class="section-title">付款记录</div>
        <el-empty v-if="record.payments.length === 0" description="暂无付款记录" />
        <div v-else class="table-scroll">
          <el-table :data="record.payments" empty-text="暂无付款记录" stripe>
            <el-table-column prop="paymentNo" label="付款单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="paymentDate" label="付款日期" min-width="110" />
            <el-table-column label="付款金额" min-width="130" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="方式" min-width="120">
              <template #default="{ row }">{{ financeMethodText(row.method) }}</template>
            </el-table-column>
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }"><PaymentStatusTag :status="row.status" /></template>
            </el-table-column>
            <el-table-column prop="postedByName" label="过账人" min-width="110" />
            <el-table-column label="过账时间" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ formatFinanceDateTime(row.postedAt) }}</template>
            </el-table-column>
            <el-table-column v-if="canViewPayment" label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text data-test="view-payment-summary" @click="viewPayment(row.id)">详情</el-button>
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
