<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeApi, type PaymentDetailRecord, type ResourceId } from '../../shared/api/financeApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import PaymentStatusTag from './PaymentStatusTag.vue'
import {
  financeErrorMessage,
  financeMethodText,
  financePermissions,
  formatFinanceAmount,
  voucherDraftStatusText,
} from './financePageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<PaymentDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const isDraft = computed(() => record.value?.status === 'DRAFT')
const isPosted = computed(() => record.value?.status === 'POSTED')
const canEdit = computed(() => isDraft.value && authStore.hasPermission(financePermissions.paymentUpdate))
const canPost = computed(() => isDraft.value && authStore.hasPermission(financePermissions.paymentPost))
const canCancel = computed(() => isDraft.value && authStore.hasPermission(financePermissions.paymentCancel))
const canViewPayable = computed(() => authStore.hasPermission(financePermissions.payableView))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await financeApi.payments.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'finance-payments' }))
}

function editPayment() {
  if (record.value) {
    void router.push({
    name: 'finance-payment-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
  }
}

function viewPayable() {
  if (record.value) {
    void router.push({
      name: 'finance-payable-detail',
      params: { id: String(record.value.payableId) },
      query: queryWithReturnTo({}, currentRouteReturnTo(route)),
    })
  }
}

async function runPaymentAction(action: 'post' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const label = action === 'post' ? '过账' : '取消'
  if (!(await confirmAction(`确认${label}付款“${record.value.paymentNo}”，金额 ${formatFinanceAmount(record.value.amount)}？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'post') {
      await financeApi.payments.post(record.value.id)
    } else {
      await financeApi.payments.cancel(record.value.id)
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
  <MasterDataTableView title="付款详情" description="查看付款草稿、过账状态和对应应付核销信息。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canEdit" data-test="edit-payment-detail" type="primary" @click="editPayment">编辑</el-button>
      <el-button v-if="canPost" data-test="post-payment-detail" type="success" :loading="actionLoading" :disabled="actionLoading" @click="runPaymentAction('post')">过账</el-button>
      <el-button v-if="canCancel" data-test="cancel-payment-detail" type="danger" :loading="actionLoading" :disabled="actionLoading" @click="runPaymentAction('cancel')">取消</el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="付款详情加载中" :closable="false" />
      <el-alert v-if="isPosted" class="state-alert" type="warning" title="已过账付款只读" :closable="false" />
    </template>

    <div v-if="record" class="finance-detail">
      <section class="summary-strip">
        <div><span>付款金额</span><strong>{{ formatFinanceAmount(record.amount) }}</strong></div>
        <div><span>付款日期</span><strong>{{ record.paymentDate }}</strong></div>
        <div><span>付款方式</span><strong>{{ financeMethodText(record.method) }}</strong></div>
        <div><span>状态</span><PaymentStatusTag :status="record.status" /></div>
        <div><span>已核销金额</span><strong>{{ formatFinanceAmount(record.allocatedAmount ?? record.amount) }}</strong></div>
        <div><span>预付余额</span><strong>{{ formatFinanceAmount(record.availableAmount ?? '0.00') }}</strong></div>
        <div><span>多目标数</span><strong>{{ record.allocationTargetCount ?? record.allocations.length }}</strong></div>
        <div><span>预付状态</span><strong>{{ record.prepaymentStatus ?? '未形成预付' }}</strong></div>
      </section>

      <dl class="detail-list">
        <dt>付款单号</dt><dd>{{ record.paymentNo }}</dd>
        <dt>对应应付</dt>
        <dd>
          <el-button v-if="canViewPayable" data-test="view-payable-from-payment" link type="primary" @click="viewPayable">
            {{ record.payableNo }}
          </el-button>
          <span v-else>{{ record.payableNo }}</span>
        </dd>
        <dt>供应商</dt><dd>{{ record.supplierName }}</dd>
        <dt>创建人</dt><dd>{{ record.createdByName }}</dd>
        <dt>过账人</dt><dd>{{ record.postedByName || '-' }}</dd>
        <dt>过账时间</dt><dd>{{ record.postedAt || '-' }}</dd>
        <dt>备注</dt><dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <section class="section-block">
        <div class="section-title">多目标核销明细</div>
        <el-table :data="record.allocations" empty-text="暂无核销记录" stripe>
          <el-table-column prop="payableNo" label="应付单号" min-width="170" show-overflow-tooltip />
          <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip />
          <el-table-column label="核销金额" min-width="130" align="right">
            <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.allocatedAmount) }}</span></template>
          </el-table-column>
        </el-table>
      </section>
      <section class="section-block">
        <div class="section-title">费用链接</div>
        <p v-if="!record.expenseLinks?.length">暂无费用链接，历史单目标记录继续按原对象可读。</p>
        <p v-for="link in record.expenseLinks" :key="link.expenseNo">{{ link.expenseNo }} {{ formatFinanceAmount(link.amount) }}</p>
      </section>
      <section class="section-block">
        <div class="section-title">凭证草稿摘要</div>
        <p v-if="!record.voucherDrafts?.length">暂无凭证草稿。</p>
        <p v-for="draft in record.voucherDrafts" :key="draft.draftNo">{{ draft.draftNo }} {{ voucherDraftStatusText(draft.status) }}</p>
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.summary-strip > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  padding: 10px 12px;
}

.summary-strip span,
.detail-list dt {
  color: var(--qherp-muted);
}

.summary-strip span {
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.detail-list dd {
  margin: 0;
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
  font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .detail-list {
    grid-template-columns: 88px minmax(0, 1fr);
  }
}
</style>
