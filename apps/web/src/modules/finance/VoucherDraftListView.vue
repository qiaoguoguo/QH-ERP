<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { financeVoucherDraftApi, type VoucherDraftRecord, type VoucherDraftStatus, type VoucherSourceType } from '../../shared/api/financeVoucherDraftApi'
import { useAuthStore } from '../../stores/authStore'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, financePermissions, financeSourceTypeText, formatFinanceAmount, ownershipTypeText, voucherDraftStatusText } from './financePageHelpers'
import './Finance028Shared.css'

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
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const generating = ref(false)
const generation = reactive<{ sourceType: VoucherSourceType; sourceId: string; version: string }>({
  sourceType: 'SALES_INVOICE',
  sourceId: '',
  version: '',
})

function balanceText(record: VoucherDraftRecord) {
  return record.balanced ? '借贷平衡' : '借贷不平衡'
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
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
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

async function generateDraft() {
  if (generating.value || !authStore.hasPermission(financePermissions.voucherDraftGenerate)) {
    return
  }
  if (!generation.sourceId.trim() || !/^\d+$/.test(generation.version.trim())) {
    actionError.value = '请填写来源 ID 和来源版本'
    return
  }
  if (!(await confirmAction('从当前来源生成非正式凭证草稿？'))) {
    return
  }
  generating.value = true
  actionError.value = ''
  try {
    await financeVoucherDraftApi.voucherDrafts.generate({
      sourceType: generation.sourceType,
      sourceId: generation.sourceId.trim(),
      version: Number(generation.version.trim()),
      idempotencyKey: `voucher-draft-generate-${Date.now()}`,
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financeErrorMessage(caught)
  } finally {
    generating.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="凭证草稿" description="仅为 031 正式制证提供业务分类建议，不是正式凭证。">
    <template #actions>
      <el-select v-model="generation.sourceType" placeholder="选择来源类型" style="width: 150px">
        <el-option label="销售发票" value="SALES_INVOICE" />
        <el-option label="采购发票" value="PURCHASE_INVOICE" />
        <el-option label="费用单" value="EXPENSE" />
        <el-option label="收款" value="RECEIPT" />
        <el-option label="付款" value="PAYMENT" />
        <el-option label="核销" value="SETTLEMENT_ALLOCATION" />
      </el-select>
      <el-input v-model="generation.sourceId" name="voucher-source-id" clearable placeholder="来源 ID" style="width: 120px" />
      <el-input v-model="generation.version" name="voucher-source-version" clearable placeholder="来源版本" style="width: 120px" />
      <el-button data-test="generate-voucher-draft" type="primary" :loading="generating" :disabled="generating || !authStore.hasPermission(financePermissions.voucherDraftGenerate)" @click="generateDraft">从来源生成草稿</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="草稿号、来源或往来方" /></el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="filters.sourceType" clearable placeholder="全部来源">
            <el-option label="销售发票" value="SALES_INVOICE" />
            <el-option label="采购发票" value="PURCHASE_INVOICE" />
            <el-option label="费用单" value="EXPENSE" />
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
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无凭证草稿" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无凭证草稿'">
        <el-table-column prop="draftNo" label="草稿号" min-width="150" show-overflow-tooltip />
        <el-table-column label="来源类型" min-width="120"><template #default="{ row }">{{ financeSourceTypeText(row.sourceType) }}</template></el-table-column>
        <el-table-column prop="sourceNo" label="来源单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column prop="partnerName" label="往来方" min-width="150" show-overflow-tooltip />
        <el-table-column label="项目/公共" min-width="130"><template #default="{ row }">{{ ownershipTypeText(row.ownershipType) }} {{ row.projectName ?? '' }}</template></el-table-column>
        <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ voucherDraftStatusText(row.status) }}</template></el-table-column>
        <el-table-column label="平衡状态" min-width="120"><template #default="{ row }">{{ balanceText(row) }}</template></el-table-column>
        <el-table-column label="借方合计" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.debitTotal) }}</template></el-table-column>
        <el-table-column label="贷方合计" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.creditTotal) }}</template></el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
        <el-table-column label="操作" fixed="right" min-width="100"><template #default="{ row }"><el-button text @click="router.push({ name: 'finance-voucher-draft-detail', params: { id: row.id } })">详情</el-button></template></el-table-column>
      </el-table>
    </div>
    <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pagination.total" :page-size="pagination.pageSize" :current-page="pagination.page" @current-change="changePage" @size-change="changePageSize" />
  </MasterDataTableView>
</template>
