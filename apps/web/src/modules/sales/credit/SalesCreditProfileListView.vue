<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { salesFulfillmentApi, type SalesCreditProfileRecord } from '../../../shared/api/salesFulfillmentApi'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
import { formatSalesDecimal, normalizeSalesId, salesFulfillmentErrorMessage } from '../salesFulfillmentPageHelpers'

const authStore = useAuthStore()
const records = ref<SalesCreditProfileRecord[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const drafts = reactive<Record<string, { creditLimit: string; frozen: boolean; blockOverdue: boolean; reviewDate: string; remark: string }>>({})
const filters = reactive({ keyword: '', page: 1, pageSize: 10 })

function isSystemAdmin() {
  return authStore.roles.some((role) => role.code === 'SYSTEM_ADMIN')
}

function canViewCredit() {
  return authStore.hasPermission('sales:credit:view') || isSystemAdmin()
}

function canManageCredit(record: SalesCreditProfileRecord) {
  return authStore.hasPermission('sales:credit:manage')
    && !record.creditRestricted
    && (record.allowedActions ?? []).includes('UPDATE')
}

function ensureDraft(record: SalesCreditProfileRecord) {
  const key = String(record.customerId)
  if (!drafts[key]) {
    drafts[key] = {
      creditLimit: record.creditLimit ?? '',
      frozen: Boolean(record.frozen),
      blockOverdue: Boolean(record.blockOverdue),
      reviewDate: record.reviewDate ?? '',
      remark: record.remark ?? '',
    }
  }
  return drafts[key]
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    if (!canViewCredit()) {
      records.value = []
      total.value = 0
      error.value = '无信用档案查看权限'
      return
    }
    const page = await salesFulfillmentApi.creditProfiles.list({
      keyword: filters.keyword,
      page: filters.page,
      pageSize: filters.pageSize,
    })
    records.value = pageItems(page)
    total.value = pageTotal(page)
    records.value.forEach(ensureDraft)
  } catch (caught) {
    records.value = []
    total.value = 0
    error.value = salesFulfillmentErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function searchRecords() {
  filters.page = 1
  await loadRecords()
}

async function resetFilters() {
  filters.keyword = ''
  filters.page = 1
  await loadRecords()
}

async function changePage(page: number) {
  filters.page = page
  await loadRecords()
}

async function changePageSize(pageSize: number) {
  filters.pageSize = pageSize
  filters.page = 1
  await loadRecords()
}

async function saveRecord(record: SalesCreditProfileRecord) {
  if (actionLoading.value || !canManageCredit(record)) {
    return
  }
  const draft = ensureDraft(record)
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.creditProfiles.upsert(record.customerId, {
      customerId: normalizeSalesId(record.customerId),
      creditLimit: draft.creditLimit,
      frozen: draft.frozen,
      blockOverdue: draft.blockOverdue,
      reviewDate: draft.reviewDate,
      remark: draft.remark,
      version: record.version,
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView
    title="信用档案"
    description="商业信用额度、三段占用、逾期风险和权限受限状态。"
  >
    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
    </template>

    <template #filters>
    <el-form class="query-form" label-position="top">
      <el-form-item label="关键词">
        <el-input v-model="filters.keyword" placeholder="客户编码或名称" clearable />
      </el-form-item>
      <el-form-item class="query-actions" label="操作">
        <el-button data-test="search-sales-credit-profiles" type="primary" @click="searchRecords">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>
    </template>

    <div class="table-scroll">
      <el-table v-loading="loading" :data="records" row-key="customerId" :empty-text="loading ? '加载中' : '暂无信用档案'">
        <el-table-column label="客户与额度" min-width="240">
          <template #default="{ row }">
            <strong>{{ row.customerCode }} {{ row.customerName }}</strong>
            <span v-if="row.creditRestricted">信用信息受限</span>
            <span v-else>商业信用额度 {{ formatSalesDecimal(row.creditLimit) }} CNY</span>
          </template>
        </el-table-column>
        <el-table-column label="信用占用" min-width="280">
          <template #default="{ row }">
            <template v-if="!row.creditRestricted">
              <span>订单承诺 {{ formatSalesDecimal(row.exposure?.orderCommitmentAmount) }}</span>
              <span>待建应收出库 {{ formatSalesDecimal(row.exposure?.unsettledShipmentAmount) }}</span>
              <span>基础应收未收 {{ formatSalesDecimal(row.exposure?.receivableOutstandingAmount) }}</span>
              <span>可用额度 {{ formatSalesDecimal(row.exposure?.availableCredit) }}</span>
            </template>
            <span v-else>额度、占用、逾期和例外原因已脱敏</span>
          </template>
        </el-table-column>
        <el-table-column label="维护草稿" min-width="260">
          <template #default="{ row }">
            <template v-if="!row.creditRestricted">
              <input
                v-model="ensureDraft(row).creditLimit"
                :data-test="`credit-limit-${row.customerId}`"
              />
              <span>冻结：{{ ensureDraft(row).frozen ? '是' : '否' }} / 逾期阻断：{{ ensureDraft(row).blockOverdue ? '是' : '否' }}</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="150">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                v-if="canManageCredit(row)"
                :data-test="`save-credit-profile-${row.customerId}`"
                text
                type="primary"
                :disabled="actionLoading"
                @click="saveRecord(row)"
              >
                保存
              </el-button>
              <span v-if="row.actionDisabledReason">{{ row.actionDisabledReason }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="filters.page"
      :page-size="filters.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>

<style scoped>
input {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  min-height: 30px;
  padding: 0 8px;
}

.table-scroll span {
  display: block;
}

.row-actions {
  display: grid;
  gap: 8px;
  justify-items: end;
}
</style>
