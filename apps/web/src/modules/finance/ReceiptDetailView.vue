<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeApi, type ReceiptDetailRecord, type ResourceId } from '../../shared/api/financeApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import ReceiptStatusTag from './ReceiptStatusTag.vue'
import {
  financeErrorMessage,
  financeMethodText,
  financePermissions,
  formatFinanceAmount,
  settlementStatusText,
  voucherDraftStatusText,
} from './financePageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<ReceiptDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const isDraft = computed(() => record.value?.status === 'DRAFT')
const isPosted = computed(() => record.value?.status === 'POSTED')
const canEdit = computed(() => isDraft.value && authStore.hasPermission(financePermissions.receiptUpdate))
const canPost = computed(() => isDraft.value && authStore.hasPermission(financePermissions.receiptPost))
const canCancel = computed(() => isDraft.value && authStore.hasPermission(financePermissions.receiptCancel))
const canViewReceivable = computed(() => authStore.hasPermission(financePermissions.receivableView))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await financeApi.receipts.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'finance-receipts' }))
}

function editReceipt() {
  if (record.value) {
    void router.push({
    name: 'finance-receipt-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
  }
}

function viewReceivable() {
  if (record.value) {
    void router.push({
      name: 'finance-receivable-detail',
      params: { id: String(record.value.receivableId) },
      query: queryWithReturnTo({}, currentRouteReturnTo(route)),
    })
  }
}

async function runReceiptAction(action: 'post' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const label = action === 'post' ? '过账' : '取消'
  if (!(await confirmAction(`确认${label}收款“${record.value.receiptNo}”，金额 ${formatFinanceAmount(record.value.amount)}？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'post') {
      await financeApi.receipts.post(record.value.id)
    } else {
      await financeApi.receipts.cancel(record.value.id)
    }
    await loadRecord()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function advanceReceiptStatusText(status: string | null | undefined) {
  return status ? settlementStatusText(status) : '未形成预收'
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="收款详情" description="查看收款草稿、过账状态和对应应收核销信息。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canEdit" data-test="edit-receipt-detail" type="primary" @click="editReceipt">编辑</el-button>
      <el-button v-if="canPost" data-test="post-receipt-detail" type="success" :loading="actionLoading" :disabled="actionLoading" @click="runReceiptAction('post')">过账</el-button>
      <el-button v-if="canCancel" data-test="cancel-receipt-detail" type="danger" :loading="actionLoading" :disabled="actionLoading" @click="runReceiptAction('cancel')">取消</el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="收款详情加载中" :closable="false" />
      <el-alert v-if="isPosted" class="state-alert" type="warning" title="已过账收款只读" :closable="false" />
    </template>

    <div v-if="record" class="finance-detail">
      <section class="summary-strip">
        <div><span>收款金额</span><strong>{{ formatFinanceAmount(record.amount) }}</strong></div>
        <div><span>收款日期</span><strong>{{ record.receiptDate }}</strong></div>
        <div><span>收款方式</span><strong>{{ financeMethodText(record.method) }}</strong></div>
        <div><span>状态</span><ReceiptStatusTag :status="record.status" /></div>
        <div><span>已核销金额</span><strong>{{ formatFinanceAmount(record.allocatedAmount ?? record.amount) }}</strong></div>
        <div><span>预收余额</span><strong>{{ formatFinanceAmount(record.availableAmount ?? '0.00') }}</strong></div>
        <div><span>多目标数</span><strong>{{ record.allocationTargetCount ?? record.allocations.length }}</strong></div>
        <div><span>预收状态</span><strong>{{ advanceReceiptStatusText(record.advanceReceiptStatus) }}</strong></div>
      </section>

      <dl class="detail-list">
        <dt>收款单号</dt><dd>{{ record.receiptNo }}</dd>
        <dt>对应应收</dt>
        <dd>
          <el-button v-if="canViewReceivable" data-test="view-receivable-from-receipt" link type="primary" @click="viewReceivable">
            {{ record.receivableNo }}
          </el-button>
          <span v-else>{{ record.receivableNo }}</span>
        </dd>
        <dt>客户</dt><dd>{{ record.customerName }}</dd>
        <dt>创建人</dt><dd>{{ record.createdByName }}</dd>
        <dt>过账人</dt><dd>{{ record.postedByName || '-' }}</dd>
        <dt>过账时间</dt><dd>{{ record.postedAt || '-' }}</dd>
        <dt>备注</dt><dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <section class="section-block">
        <div class="section-title">多目标核销明细</div>
        <div class="table-scroll">
          <el-table :data="record.allocations" empty-text="暂无核销记录" stripe>
            <el-table-column prop="receivableNo" label="应收单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
            <el-table-column label="核销金额" min-width="130" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatFinanceAmount(row.allocatedAmount) }}</span></template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="section-block">
        <div class="section-title">发票链接</div>
        <p v-if="!record.invoiceLinks?.length">暂无发票链接，历史单目标记录继续按原对象可读。</p>
        <p v-for="link in record.invoiceLinks" :key="link.invoiceNo">{{ link.invoiceNo }} {{ formatFinanceAmount(link.amount) }}</p>
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
