<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeVoucherDraftApi, type VoucherDraftRecord, type VoucherDraftStatus, type VoucherSourceType } from '../../shared/api/financeVoucherDraftApi'
import { financeInvoiceApi, type InvoiceStatus } from '../../shared/api/financeInvoiceApi'
import { financeExpenseApi, type ExpenseStatus } from '../../shared/api/financeExpenseApi'
import { financeSettlementApi } from '../../shared/api/financeSettlementApi'
import { financeApi, type PaymentSummaryRecord, type ReceiptSummaryRecord } from '../../shared/api/financeApi'
import { glApi, type GlVoucherRecord } from '../../shared/api/glApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, financePermissions, financeSourceTypeText, formatFinanceAmount, ownershipTypeText, voucherDraftStatusText } from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const filters = reactive<{ keyword: string; sourceType: '' | VoucherSourceType; status?: VoucherDraftStatus; balanced?: boolean; businessDateFrom: string; businessDateTo: string }>({
  keyword: '',
  sourceType: '',
  status: undefined,
  balanced: undefined,
  businessDateFrom: '',
  businessDateTo: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<VoucherDraftRecord[]>([])
const linkedGlVouchers = ref<Record<string, GlVoucherRecord>>({})
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const generating = ref(false)
const glActionLoadingId = ref<string | number | null>(null)
const sourceCandidatesLoading = ref(false)
const generation = reactive<{ sourceType: VoucherSourceType; sourceKeyword: string; selectedKey: string }>({
  sourceType: 'SALES_INVOICE',
  sourceKeyword: '',
  selectedKey: '',
})

interface VoucherSourceCandidate {
  key: string
  sourceType: VoucherSourceType
  sourceId: string | number
  sourceNo: string
  businessDate?: string | null
  partyName?: string | null
  ownershipText?: string
  amount?: string | number | null
  version?: number
  status?: string
  summary: string
  disabledReason?: string
}

const sourceCandidates = ref<VoucherSourceCandidate[]>([])
const sourceCandidatesQueried = ref(false)
const selectedSourceCandidate = computed(() => sourceCandidates.value.find((item) => item.key === generation.selectedKey) ?? null)
const canQueryGlVouchers = computed(() => authStore.hasPermission('gl:voucher:view'))
const canConvertGlVoucher = computed(() => authStore.hasPermission('gl:voucher:convert'))
const generateDisabled = computed(() => {
  const source = selectedSourceCandidate.value
  return generating.value
    || !authStore.hasPermission(financePermissions.voucherDraftGenerate)
    || !source
    || source.version === undefined
    || Boolean(source.disabledReason)
})

function balanceText(record: VoucherDraftRecord) {
  return record.balanced ? '借贷平衡' : '借贷不平衡'
}

function voucherPartyName(record: VoucherDraftRecord) {
  return record.partyName || record.partnerName || '-'
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financeVoucherDraftApi.voucherDrafts.list({
      keyword: filters.keyword,
      sourceType: filters.sourceType || undefined,
      status: filters.status,
      balanced: filters.balanced,
      businessDateFrom: filters.businessDateFrom,
      businessDateTo: filters.businessDateTo,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
    await loadLinkedGlVouchers(records.value)
  } catch (caught) {
    records.value = []
    linkedGlVouchers.value = {}
    pagination.total = 0
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadLinkedGlVouchers(drafts: VoucherDraftRecord[]) {
  if (!canQueryGlVouchers.value || drafts.length === 0) {
    linkedGlVouchers.value = {}
    return
  }
  const entries = await Promise.all(drafts.map(async (draft) => {
    try {
      const page = await glApi.vouchers.list({
        sourceType: 'FIN_VOUCHER_DRAFT',
        sourceId: draft.id,
        page: 1,
        pageSize: 10,
      })
      const linked = pageItems(page).find((voucher) => isLinkedGlVoucher(draft, voucher))
      return [String(draft.id), linked] as const
    } catch {
      return [String(draft.id), undefined] as const
    }
  }))
  linkedGlVouchers.value = Object.fromEntries(entries.filter((entry): entry is readonly [string, GlVoucherRecord] => Boolean(entry[1])))
}

function isLinkedGlVoucher(draft: VoucherDraftRecord, voucher: GlVoucherRecord) {
  return voucher.sourceType === 'FIN_VOUCHER_DRAFT' && String(voucher.sourceId) === String(draft.id)
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.sourceType = ''
  filters.status = undefined
  filters.balanced = undefined
  filters.businessDateFrom = ''
  filters.businessDateTo = ''
  pagination.page = 1
  void loadRecords()
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

function sourceCandidateKey(sourceType: VoucherSourceType, sourceId: string | number) {
  return `${sourceType}:${sourceId}`
}

function invoiceCandidate(record: { id: string | number; invoiceNo: string; customerName?: string | null; supplierName?: string | null; ownershipType?: string | null; projectName?: string | null; totalAmount?: string | number | null; version: number; status?: string }, sourceType: VoucherSourceType): VoucherSourceCandidate {
  const partyName = sourceType === 'SALES_INVOICE' ? record.customerName : record.supplierName
  return {
    key: sourceCandidateKey(sourceType, record.id),
    sourceType,
    sourceId: record.id,
    sourceNo: record.invoiceNo,
    partyName,
    ownershipText: `${ownershipTypeText(record.ownershipType)} ${record.projectName ?? ''}`.trim(),
    amount: record.totalAmount,
    version: record.version,
    status: record.status,
    summary: `${financeSourceTypeText(sourceType)} ${record.invoiceNo}${partyName ? ` ${partyName}` : ''}`,
  }
}

function expenseCandidate(record: { id: string | number; expenseNo: string; supplierName?: string | null; ownershipType?: string | null; projectName?: string | null; totalAmount?: string | number | null; version: number; status?: string }): VoucherSourceCandidate {
  return {
    key: sourceCandidateKey('EXPENSE', record.id),
    sourceType: 'EXPENSE',
    sourceId: record.id,
    sourceNo: record.expenseNo,
    partyName: record.supplierName,
    ownershipText: `${ownershipTypeText(record.ownershipType)} ${record.projectName ?? ''}`.trim(),
    amount: record.totalAmount,
    version: record.version,
    status: record.status,
    summary: `费用单 ${record.expenseNo}${record.supplierName ? ` ${record.supplierName}` : ''}`,
  }
}

function cashCandidate(record: ReceiptSummaryRecord | PaymentSummaryRecord, sourceType: 'RECEIPT' | 'PAYMENT'): VoucherSourceCandidate {
  const sourceNo = sourceType === 'RECEIPT'
    ? (record as ReceiptSummaryRecord).receiptNo
    : (record as PaymentSummaryRecord).paymentNo
  const partyName = sourceType === 'RECEIPT'
    ? (record as ReceiptSummaryRecord).customerName
    : (record as PaymentSummaryRecord).supplierName
  return {
    key: sourceCandidateKey(sourceType, record.id),
    sourceType,
    sourceId: record.id,
    sourceNo,
    partyName,
    ownershipText: '',
    amount: record.amount,
    version: record.version,
    status: record.status,
    summary: `${financeSourceTypeText(sourceType)} ${sourceNo}${partyName ? ` ${partyName}` : ''}`,
    disabledReason: record.version === undefined ? '来源缺少版本，暂不能生成凭证草稿' : undefined,
  }
}

function allocationCandidate(record: { id: string | number; allocationNo?: string; partnerName?: string | null; ownershipType?: string | null; projectName?: string | null; totalAmount?: string | number | null; amount?: string | number | null; version: number; status?: string }): VoucherSourceCandidate {
  const sourceNo = record.allocationNo ?? String(record.id)
  return {
    key: sourceCandidateKey('SETTLEMENT_ALLOCATION', record.id),
    sourceType: 'SETTLEMENT_ALLOCATION',
    sourceId: record.id,
    sourceNo,
    partyName: record.partnerName,
    ownershipText: `${ownershipTypeText(record.ownershipType)} ${record.projectName ?? ''}`.trim(),
    amount: record.totalAmount ?? record.amount,
    version: record.version,
    status: record.status,
    summary: `核销 ${sourceNo}${record.partnerName ? ` ${record.partnerName}` : ''}`,
  }
}

async function loadSourceCandidates() {
  sourceCandidatesLoading.value = true
  sourceCandidatesQueried.value = true
  actionError.value = ''
  try {
    if (generation.sourceType === 'SALES_INVOICE') {
      const page = await financeInvoiceApi.salesInvoices.list({ keyword: generation.sourceKeyword, status: 'CONFIRMED' as InvoiceStatus, page: 1, pageSize: 20 })
      sourceCandidates.value = pageItems(page).map((item) => invoiceCandidate(item, 'SALES_INVOICE'))
    } else if (generation.sourceType === 'PURCHASE_INVOICE') {
      const page = await financeInvoiceApi.purchaseInvoices.list({ keyword: generation.sourceKeyword, status: 'CONFIRMED' as InvoiceStatus, page: 1, pageSize: 20 })
      sourceCandidates.value = pageItems(page).map((item) => invoiceCandidate(item, 'PURCHASE_INVOICE'))
    } else if (generation.sourceType === 'EXPENSE') {
      const page = await financeExpenseApi.expenses.list({ keyword: generation.sourceKeyword, status: 'CONFIRMED' as ExpenseStatus, page: 1, pageSize: 20 })
      sourceCandidates.value = pageItems(page).map(expenseCandidate)
    } else if (generation.sourceType === 'RECEIPT') {
      const page = await financeApi.receipts.list({ keyword: generation.sourceKeyword, status: 'POSTED', page: 1, pageSize: 20 })
      sourceCandidates.value = pageItems(page).map((item) => cashCandidate(item, 'RECEIPT'))
    } else if (generation.sourceType === 'PAYMENT') {
      const page = await financeApi.payments.list({ keyword: generation.sourceKeyword, status: 'POSTED', page: 1, pageSize: 20 })
      sourceCandidates.value = pageItems(page).map((item) => cashCandidate(item, 'PAYMENT'))
    } else {
      const page = await financeSettlementApi.settlementWorkbench.allocations({ keyword: generation.sourceKeyword, status: 'POSTED', page: 1, pageSize: 20 })
      sourceCandidates.value = pageItems(page).map(allocationCandidate)
    }
    if (!sourceCandidates.value.some((item) => item.key === generation.selectedKey)) {
      generation.selectedKey = ''
    }
  } catch (caught) {
    sourceCandidates.value = []
    actionError.value = financeErrorMessage(caught)
  } finally {
    sourceCandidatesLoading.value = false
  }
}

function changeGenerationSourceType() {
  generation.selectedKey = ''
  void loadSourceCandidates()
}

async function generateDraft() {
  if (generating.value || !authStore.hasPermission(financePermissions.voucherDraftGenerate)) {
    return
  }
  const source = selectedSourceCandidate.value
  if (!source) {
    actionError.value = '请选择已确认或已过账的业务来源'
    return
  }
  if (source.disabledReason || source.version === undefined) {
    actionError.value = source.disabledReason ?? '来源缺少版本，暂不能生成凭证草稿'
    return
  }
  if (!(await confirmAction('从当前来源生成非正式凭证草稿？'))) {
    return
  }
  generating.value = true
  actionError.value = ''
  try {
    await financeVoucherDraftApi.voucherDrafts.generate({
      sourceType: source.sourceType,
      sourceId: source.sourceId,
      version: source.version,
      idempotencyKey: `voucher-draft-generate-${Date.now()}`,
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    generating.value = false
  }
}

function linkedGlVoucher(record: VoucherDraftRecord) {
  return linkedGlVouchers.value[String(record.id)]
}

function viewGlVoucher(record: VoucherDraftRecord) {
  const voucher = linkedGlVoucher(record)
  if (!voucher) {
    return
  }
  void router.push({
    name: 'gl-voucher-detail',
    params: { id: voucher.id },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

async function convertToGlVoucher(record: VoucherDraftRecord) {
  if (glActionLoadingId.value || !canConvertGlVoucher.value || !canQueryGlVouchers.value || record.status !== 'READY') {
    return
  }
  if (linkedGlVoucher(record)) {
    viewGlVoucher(record)
    return
  }
  if (!(await confirmAction(`将凭证草稿“${record.draftNo}”生成正式凭证草稿？`))) {
    return
  }
  glActionLoadingId.value = record.id
  actionError.value = ''
  try {
    const voucher = await glApi.vouchers.fromFinanceDraft(record.id, {
      version: record.version,
      idempotencyKey: `gl-convert-finance-draft-${record.id}-${Date.now()}`,
    })
    linkedGlVouchers.value = { ...linkedGlVouchers.value, [String(record.id)]: voucher }
    await router.push({
      name: 'gl-voucher-detail',
      params: { id: voucher.id },
      query: queryWithReturnTo({}, currentRouteReturnTo(route)),
    })
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    glActionLoadingId.value = null
  }
}

watch(() => generation.sourceKeyword, () => {
  generation.selectedKey = ''
})

onMounted(() => {
  void loadRecords()
  void loadSourceCandidates()
})
</script>

<template>
  <MasterDataTableView title="凭证草稿" description="仅为 031 正式制证提供业务分类建议，不是正式凭证。">
    <template #actions>
      <el-select v-model="generation.sourceType" placeholder="选择来源类型" style="width: 150px" @change="changeGenerationSourceType">
        <el-option label="销售发票" value="SALES_INVOICE" />
        <el-option label="采购发票" value="PURCHASE_INVOICE" />
        <el-option label="费用单" value="EXPENSE" />
        <el-option label="收款" value="RECEIPT" />
        <el-option label="付款" value="PAYMENT" />
        <el-option label="核销" value="SETTLEMENT_ALLOCATION" />
      </el-select>
      <el-input v-model="generation.sourceKeyword" name="voucher-source-keyword" clearable placeholder="业务编号或往来方" style="width: 170px" @keyup.enter="loadSourceCandidates" />
      <el-button :loading="sourceCandidatesLoading" @click="loadSourceCandidates">查询来源</el-button>
      <el-select
        v-model="generation.selectedKey"
        data-test="voucher-source-candidate"
        filterable
        clearable
        :loading="sourceCandidatesLoading"
        placeholder="选择已确认/已过账来源"
        style="width: 280px"
      >
        <el-option
          v-for="source in sourceCandidates"
          :key="source.key"
          :label="`${source.sourceNo} ${source.summary}`"
          :value="source.key"
          :disabled="Boolean(source.disabledReason)"
        >
          <span>{{ source.sourceNo }} {{ source.summary }}</span>
          <span class="finance-muted-note"> {{ source.disabledReason ?? source.ownershipText }} {{ formatFinanceAmount(source.amount) }}</span>
        </el-option>
      </el-select>
      <el-button data-test="generate-voucher-draft" type="primary" :loading="generating" :disabled="generateDisabled" @click="generateDraft">从来源生成草稿</el-button>
      <span v-if="sourceCandidatesQueried && !sourceCandidatesLoading && sourceCandidates.length === 0" class="finance-muted-note">
        暂无可生成来源，请调整筛选条件
      </span>
    </template>
    <template #filters>
      <el-form class="query-form">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="草稿号、来源或往来方" /></el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="filters.sourceType" clearable placeholder="全部来源">
            <el-option label="销售发票" value="SALES_INVOICE" />
            <el-option label="采购发票" value="PURCHASE_INVOICE" />
            <el-option label="费用单" value="EXPENSE" />
            <el-option label="收款" value="RECEIPT" />
            <el-option label="付款" value="PAYMENT" />
            <el-option label="核销" value="SETTLEMENT_ALLOCATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待正式制证" value="READY" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="平衡">
          <el-select v-model="filters.balanced" clearable placeholder="全部">
            <el-option label="借贷平衡" :value="true" />
            <el-option label="借贷不平衡" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker v-model="filters.businessDateFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker v-model="filters.businessDateTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="凭证草稿加载中" :closable="false" />
      <el-alert type="warning" title="凭证草稿不产生科目余额、会计期间或总账影响" :closable="false" />
      <el-alert type="warning" title="会计期间已财务关闭时，生成正式凭证草稿会失败关闭且不回写 028 状态" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无凭证草稿" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无凭证草稿'">
        <el-table-column prop="draftNo" label="草稿号" min-width="150" show-overflow-tooltip />
        <el-table-column label="来源类型" min-width="120"><template #default="{ row }">{{ financeSourceTypeText(row.sourceType) }}</template></el-table-column>
        <el-table-column prop="sourceNo" label="来源单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="往来方" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ voucherPartyName(row) }}</template></el-table-column>
        <el-table-column label="项目/公共" min-width="130"><template #default="{ row }">{{ ownershipTypeText(row.ownershipType) }} {{ row.projectName ?? '' }}</template></el-table-column>
        <el-table-column label="平衡状态" min-width="120"><template #default="{ row }">{{ balanceText(row) }}</template></el-table-column>
        <el-table-column label="借方合计" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.debitTotal) }}</template></el-table-column>
        <el-table-column label="贷方合计" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.creditTotal) }}</template></el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
        <el-table-column label="状态" fixed="right" width="126"><template #default="{ row }">{{ voucherDraftStatusText(row.status) }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button text @click="router.push({ name: 'finance-voucher-draft-detail', params: { id: row.id } })">详情</el-button>
            <el-dropdown
              v-if="(linkedGlVoucher(row) && canQueryGlVouchers) || (row.status === 'READY' && canConvertGlVoucher && canQueryGlVouchers)"
              trigger="click"
              class="table-actions-more"
            >
              <el-button size="small" text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu class="table-actions-more-menu">
                  <el-dropdown-item
                    v-if="linkedGlVoucher(row) && canQueryGlVouchers"
                    class="table-actions-more-item"
                    data-test="view-gl-voucher"
                    @click="viewGlVoucher(row)"
                  >
                    查看正式凭证
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-else-if="row.status === 'READY' && canConvertGlVoucher && canQueryGlVouchers"
                    class="table-actions-more-item"
                    data-test="convert-gl-voucher"
                    :disabled="glActionLoadingId === row.id"
                    @click="convertToGlVoucher(row)"
                  >
                    生成正式凭证草稿
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pagination.total" :page-size="pagination.pageSize" :current-page="pagination.page" @current-change="changePage" @size-change="changePageSize" />
  </MasterDataTableView>
</template>
