<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { masterDataApi, type PartnerRecord, type ResourceId } from '../../../shared/api/masterDataApi'
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
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createError = ref('')
const customerLoading = ref(false)
const customerOptions = ref<PartnerRecord[]>([])
const editingRecord = ref<SalesCreditProfileRecord | null>(null)
const createForm = reactive<{
  customerId: ResourceId | ''
  creditLimit: string
  frozen: boolean
  blockOverdue: boolean
  remark: string
}>({
  customerId: '',
  creditLimit: '',
  frozen: false,
  blockOverdue: false,
  remark: '',
})
const filters = reactive({ keyword: '', page: 1, pageSize: 10 })

function isSystemAdmin() {
  return authStore.roles.some((role) => role.code === 'SYSTEM_ADMIN')
}

function canViewCredit() {
  return authStore.hasPermission('sales:credit:view') || isSystemAdmin()
}

function hasManageCredit() {
  return authStore.hasPermission('sales:credit:manage') || isSystemAdmin()
}

function canManageCredit(record: SalesCreditProfileRecord) {
  const actions = record.allowedActions ?? ['UPDATE']
  return hasManageCredit()
    && !record.creditRestricted
    && actions.includes('UPDATE')
}

function resetCreateForm() {
  editingRecord.value = null
  createForm.customerId = ''
  createForm.creditLimit = ''
  createForm.frozen = false
  createForm.blockOverdue = false
  createForm.remark = ''
  createError.value = ''
}

function customerLabel(customer: PartnerRecord) {
  return [customer.code, customer.name].filter(Boolean).join(' / ')
}

async function loadCustomerOptions(keyword = '') {
  customerLoading.value = true
  try {
    const page = await masterDataApi.customers.list({
      keyword,
      status: 'ENABLED',
      page: 1,
      pageSize: 20,
    })
    customerOptions.value = pageItems(page)
  } catch (caught) {
    createError.value = salesFulfillmentErrorMessage(caught)
  } finally {
    customerLoading.value = false
  }
}

async function openCreateDialog() {
  resetCreateForm()
  createDialogVisible.value = true
  if (customerOptions.value.length === 0) {
    await loadCustomerOptions()
  }
}

function openEditDialog(record: SalesCreditProfileRecord) {
  resetCreateForm()
  editingRecord.value = record
  createForm.customerId = record.customerId
  createForm.creditLimit = record.creditLimit ?? ''
  createForm.frozen = Boolean(record.frozen)
  createForm.blockOverdue = Boolean(record.blockOverdue)
  createForm.remark = record.remark ?? ''
  if (!customerOptions.value.some((customer) => String(customer.id) === String(record.customerId))) {
    customerOptions.value = [
      {
        id: record.customerId,
        code: record.customerCode ?? '',
        name: record.customerName,
        status: 'ENABLED',
        version: record.version,
      } as PartnerRecord,
      ...customerOptions.value,
    ]
  }
  createDialogVisible.value = true
}

async function saveCreateProfile() {
  createError.value = ''
  if (!createForm.customerId) {
    createError.value = '请选择客户'
    return
  }
  if (!createForm.creditLimit.trim()) {
    createError.value = '请输入信用额度'
    return
  }

  createLoading.value = true
  try {
    const customerId = normalizeSalesId(createForm.customerId)
    const payload = {
      customerId,
      creditLimit: createForm.creditLimit.trim(),
      frozen: createForm.frozen,
      blockOverdue: createForm.blockOverdue,
      remark: createForm.remark.trim() || undefined,
    }
    await salesFulfillmentApi.creditProfiles.upsert(
      customerId,
      editingRecord.value ? { ...payload, version: editingRecord.value.version } : payload,
    )
    createDialogVisible.value = false
    resetCreateForm()
    await loadRecords()
  } catch (caught) {
    createError.value = salesFulfillmentErrorMessage(caught)
  } finally {
    createLoading.value = false
  }
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

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView
    title="信用档案"
    description="商业信用额度、三段占用、逾期风险和权限受限状态。"
  >
    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="客户编码或名称" clearable />
        </el-form-item>
        <el-form-item class="query-actions" label="操作">
          <el-button data-test="search-sales-credit-profiles" type="primary" @click="searchRecords">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button v-if="hasManageCredit()" data-test="create-sales-credit-profile" type="primary" @click="openCreateDialog">
            新增信用档案
          </el-button>
        </el-form-item>
      </el-form>
    </template>

    <div class="table-scroll">
      <el-table v-loading="loading" :data="records" row-key="customerId" :empty-text="loading ? '加载中' : '暂无信用档案，请点击新增信用档案录入客户额度'">
        <el-table-column label="客户" min-width="220">
          <template #default="{ row }">
            <strong>{{ row.customerCode }} {{ row.customerName }}</strong>
            <span v-if="row.creditRestricted">信用信息受限</span>
            <span v-else>{{ row.remark || '未填写备注' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="信用额度" min-width="180">
          <template #default="{ row }">
            <strong>{{ formatSalesDecimal(row.creditLimit) }} CNY</strong>
            <span>{{ row.frozen ? '已冻结' : '正常使用' }}</span>
            <span>{{ row.blockOverdue ? '逾期自动阻断' : '逾期不自动阻断' }}</span>
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
        <el-table-column label="操作" fixed="right" width="190">
          <template #default="{ row }">
            <div class="actions-cell">
              <el-button
                v-if="canManageCredit(row)"
                :data-test="`edit-credit-profile-${row.customerId}`"
                size="small"
                text
                @click="openEditDialog(row)"
              >
                编辑
              </el-button>
              <span v-else>{{ row.actionDisabledReason || '无维护权限' }}</span>
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

  <el-dialog
    v-model="createDialogVisible"
    :title="editingRecord ? '编辑信用档案' : '新增信用档案'"
    width="560px"
    destroy-on-close
    @closed="resetCreateForm"
  >
    <el-alert v-if="createError" class="page-alert" type="error" :title="createError" show-icon :closable="false" />
    <el-form class="credit-create-form" label-position="top">
      <el-form-item label="客户">
        <el-select
          v-model="createForm.customerId"
          data-test="credit-profile-customer"
          filterable
          remote
          reserve-keyword
          clearable
          :disabled="Boolean(editingRecord)"
          :remote-method="loadCustomerOptions"
          :loading="customerLoading"
          placeholder="搜索客户编码或名称"
        >
          <el-option
            v-for="customer in customerOptions"
            :key="customer.id"
            :label="customerLabel(customer)"
            :value="customer.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="信用额度">
        <el-input v-model="createForm.creditLimit" data-test="credit-profile-limit" placeholder="例如 100000.00" />
      </el-form-item>
      <el-form-item label="风险控制">
        <div class="switch-row">
          <el-switch v-model="createForm.frozen" active-text="冻结客户信用" />
          <el-switch v-model="createForm.blockOverdue" active-text="逾期阻断订单确认" />
        </div>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="createForm.remark" type="textarea" :rows="3" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="createLoading" @click="createDialogVisible = false">取消</el-button>
      <el-button data-test="save-sales-credit-profile" type="primary" :loading="createLoading" @click="saveCreateProfile">
        {{ editingRecord ? '保存修改' : '保存信用档案' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.table-scroll span {
  display: block;
}

.actions-cell {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.credit-create-form {
  display: grid;
  gap: 4px;
}

.credit-create-form :deep(.el-select) {
  width: 100%;
}

.switch-row {
  align-items: flex-start;
  display: grid;
  gap: 12px;
}
</style>
