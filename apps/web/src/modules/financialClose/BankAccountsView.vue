<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { financialCloseApi, type BankAccountRecord } from '../../shared/api/financialCloseApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  bankSensitiveText,
  createFinancialCloseIdempotencyKey,
  financialCloseErrorMessage,
  financialClosePageItems,
  financialClosePageSizes,
  financialClosePageTotal,
  financialClosePermissions,
} from './financialClosePageHelpers'
import './FinancialCloseShared.css'

const authStore = useAuthStore()
const filters = reactive({ keyword: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<BankAccountRecord[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await financialCloseApi.bankAccounts.list({
      keyword: filters.keyword,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = financialClosePageItems(page)
    pagination.total = financialClosePageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = financialCloseErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function disableAccount(record: BankAccountRecord) {
  if (actionLoading.value || !authStore.hasPermission(financialClosePermissions.bankAccountManage)) {
    return
  }
  if (!(await confirmAction(`停用银行账户 ${record.accountName || record.id}？`, { title: '停用银行账户', risk: 'warning' }))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await financialCloseApi.bankAccounts.disable(record.id, {
      version: record.version,
      reason: '停用银行账户',
      idempotencyKey: createFinancialCloseIdempotencyKey('bank-account-disable'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = financialCloseErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
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

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="银行账户" description="维护 1002 银行存款下的银行账户，账号只保存不可恢复指纹、末四位和脱敏显示。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button type="primary" :disabled="!authStore.hasPermission(financialClosePermissions.bankAccountManage)">新增银行账户</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="账户、银行或科目" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="银行账户只绑定启用、可记账的 1002 银行存款末级科目，不接银行直连。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无银行账户'" stripe>
        <el-table-column prop="accountName" label="账户名称" min-width="160" />
        <el-table-column prop="accountType" label="账户类型" min-width="100" />
        <el-table-column prop="bankName" label="开户行" min-width="160" show-overflow-tooltip />
        <el-table-column prop="accountNoMasked" label="账号" min-width="140" />
        <el-table-column prop="glAccountCode" label="绑定科目" min-width="130" />
        <el-table-column label="敏感权限" min-width="140">
          <template #default="{ row }">{{ bankSensitiveText(row.bankSensitiveVisible) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="90"><template #default="{ row }">{{ row.enabled === false ? '停用' : '启用' }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" min-width="110">
          <template #default="{ row }">
            <el-button data-test="disable-bank-account" text type="warning" :loading="actionLoading" @click="disableAccount(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="financialClosePageSizes"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>
