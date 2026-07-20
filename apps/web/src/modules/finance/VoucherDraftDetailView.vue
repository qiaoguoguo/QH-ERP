<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeVoucherDraftApi, type VoucherDraftRecord } from '../../shared/api/financeVoucherDraftApi'
import { glApi, type GlVoucherRecord } from '../../shared/api/glApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import {
  financeErrorMessage,
  financePermissions,
  financeSourceTypeText,
  formatFinanceAmount,
  ownershipTypeText,
  voucherBusinessCategoryText,
  voucherDraftStatusText,
} from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<VoucherDraftRecord | null>(null)
const linkedGlVoucher = ref<GlVoucherRecord | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canReady = computed(() => record.value?.allowedActions?.includes('READY') && authStore.hasPermission(financePermissions.voucherDraftReady))
const canCancel = computed(() => record.value?.allowedActions?.includes('CANCEL') && authStore.hasPermission(financePermissions.voucherDraftCancel))
const canQueryGlVoucher = computed(() => authStore.hasPermission('gl:voucher:view'))
const canConvertGlVoucher = computed(() =>
  Boolean(record.value && record.value.status === 'READY' && !linkedGlVoucher.value)
  && authStore.hasPermission('gl:voucher:convert')
  && canQueryGlVoucher.value,
)
const balanceText = computed(() => record.value?.balanced ? '借贷平衡' : '借贷不平衡')

function debitTotalText(draft: VoucherDraftRecord) {
  return (draft.debitTotal ?? (draft as VoucherDraftRecord & { debitAmount?: string }).debitAmount ?? '0.00')
}

function creditTotalText(draft: VoucherDraftRecord) {
  return (draft.creditTotal ?? (draft as VoucherDraftRecord & { creditAmount?: string }).creditAmount ?? '0.00')
}

function lineAmountText(line: NonNullable<VoucherDraftRecord['lines']>[number]) {
  return line.totalAmount ?? line.amount ?? line.pretaxAmount ?? '0.00'
}

function sourceSummaryText(draft: VoucherDraftRecord) {
  if (!draft.sourceSummary) {
    return '暂无来源摘要'
  }
  if (typeof draft.sourceSummary === 'string') {
    return draft.sourceSummary
  }
  if (draft.sourceSummary.restricted) {
    return draft.sourceSummary.restrictedReason ?? '来源受限'
  }
  return draft.sourceSummary.summary || `${financeSourceTypeText(draft.sourceSummary.sourceType)} ${draft.sourceSummary.sourceNo}`
}

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await financeVoucherDraftApi.voucherDrafts.get(route.params.id as string)
    await loadLinkedGlVoucher()
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadLinkedGlVoucher() {
  if (!record.value || !canQueryGlVoucher.value) {
    linkedGlVoucher.value = null
    return
  }
  try {
    const page = await glApi.vouchers.list({
      sourceType: 'FIN_VOUCHER_DRAFT',
      sourceId: record.value.id,
      page: 1,
      pageSize: 10,
    })
    linkedGlVoucher.value = pageItems(page)[0] ?? null
  } catch {
    linkedGlVoucher.value = null
  }
}

async function runAction(action: 'ready' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const label = action === 'ready' ? '标记待制证' : '取消'
  if (!(await confirmAction(`${label}凭证草稿“${record.value.draftNo}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = {
      version: record.value.version,
      idempotencyKey: `${action}-voucher-draft-${record.value.id}-${Date.now()}`,
    }
    if (action === 'ready') {
      await financeVoucherDraftApi.voucherDrafts.markReady(record.value.id, payload)
    } else {
      await financeVoucherDraftApi.voucherDrafts.cancel(record.value.id, payload)
    }
    await loadRecord()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function viewGlVoucher() {
  if (!linkedGlVoucher.value) {
    return
  }
  void router.push({
    name: 'gl-voucher-detail',
    params: { id: linkedGlVoucher.value.id },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

async function convertToGlVoucher() {
  if (!record.value || !canConvertGlVoucher.value || actionLoading.value) {
    return
  }
  if (!(await confirmAction(`将凭证草稿“${record.value.draftNo}”生成正式凭证草稿？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    linkedGlVoucher.value = await glApi.vouchers.fromFinanceDraft(record.value.id, {
      version: record.value.version,
      idempotencyKey: `gl-convert-finance-draft-${record.value.id}-${Date.now()}`,
    })
    viewGlVoucher()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="凭证草稿详情" description="查看非正式凭证草稿、来源事件、业务分类建议和金额平衡状态。">
    <template #actions>
      <el-button @click="router.push({ name: 'finance-voucher-drafts' })">返回列表</el-button>
      <el-button @click="loadRecord">刷新</el-button>
      <el-button v-if="linkedGlVoucher && canQueryGlVoucher" data-test="view-gl-voucher" @click="viewGlVoucher">查看正式凭证</el-button>
      <el-button v-if="canConvertGlVoucher" data-test="convert-gl-voucher" type="primary" :loading="actionLoading" @click="convertToGlVoucher">生成正式凭证草稿</el-button>
      <el-button v-if="canReady" data-test="ready-voucher-draft" type="success" :loading="actionLoading" :disabled="actionLoading" @click="runAction('ready')">标记待制证</el-button>
      <el-button v-if="canCancel" data-test="cancel-voucher-draft" type="danger" :loading="actionLoading" :disabled="actionLoading" @click="runAction('cancel')">取消草稿</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="凭证草稿详情加载中" :closable="false" />
      <el-alert type="warning" title="非正式凭证草稿，不产生总账、会计期间或资金影响" :closable="false" />
    </template>

    <div v-if="record" class="finance-summary-strip">
      <div><span>草稿属性</span><strong>非正式凭证草稿</strong></div>
      <div><span>状态</span><strong>{{ voucherDraftStatusText(record.status) }}</strong></div>
      <div><span>来源事件</span><strong>{{ financeSourceTypeText(record.sourceType) }} {{ record.sourceNo }}</strong></div>
      <div><span>金额平衡</span><strong>{{ balanceText }}</strong></div>
      <div><span>借方合计</span><strong>{{ formatFinanceAmount(debitTotalText(record)) }}</strong></div>
      <div><span>贷方合计</span><strong>{{ formatFinanceAmount(creditTotalText(record)) }}</strong></div>
      <div><span>生成版本</span><strong>{{ record.generationVersion }}</strong></div>
      <div><span>项目/公共</span><strong>{{ ownershipTypeText(record.ownershipType) }} {{ record.projectName ?? '' }}</strong></div>
      <div v-if="linkedGlVoucher"><span>正式凭证</span><strong>已生成正式凭证 {{ linkedGlVoucher.voucherNo || linkedGlVoucher.draftNo }}</strong></div>
    </div>

    <div v-if="record" class="finance-section-grid">
      <section class="finance-section">
        <span class="finance-section-title">来源事件</span>
        <p>{{ financeSourceTypeText(record.sourceType) }} {{ record.sourceNo }}</p>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">分录建议</span>
        <div class="table-scroll">
          <el-table :data="record.lines ?? []" empty-text="暂无分录建议">
            <el-table-column label="借贷方向" min-width="100"><template #default="{ row }">{{ row.direction === 'DEBIT' ? '借方' : '贷方' }}</template></el-table-column>
            <el-table-column label="业务分类" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ voucherBusinessCategoryText(row.businessCategory) }}</template></el-table-column>
            <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
            <el-table-column label="未税金额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.pretaxAmount) }}</template></el-table-column>
            <el-table-column label="税额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.taxAmount) }}</template></el-table-column>
            <el-table-column label="含税金额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(lineAmountText(row)) }}</template></el-table-column>
            <el-table-column prop="partnerName" label="往来方" min-width="150" show-overflow-tooltip />
            <el-table-column prop="projectName" label="项目" min-width="150" show-overflow-tooltip />
          </el-table>
        </div>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">金额平衡</span>
        <p>{{ balanceText }}，借方 {{ formatFinanceAmount(debitTotalText(record)) }}，贷方 {{ formatFinanceAmount(creditTotalText(record)) }}。</p>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">生成版本</span>
        <p>第 {{ record.generationVersion }} 版建议。</p>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">来源追溯</span>
        <p>{{ sourceSummaryText(record) }}</p>
      </section>
      <section v-if="linkedGlVoucher" class="finance-section">
        <span class="finance-section-title">正式凭证</span>
        <p>已生成正式凭证 {{ linkedGlVoucher.voucherNo || linkedGlVoucher.draftNo }}</p>
        <el-button data-test="view-gl-voucher" text @click="viewGlVoucher">查看正式凭证</el-button>
      </section>
      <section class="finance-section">
        <span class="finance-section-title">审计</span>
        <p>{{ record.auditSummary?.length ? '已有审计记录' : '暂无审计摘要' }}</p>
      </section>
    </div>
  </MasterDataTableView>
</template>
