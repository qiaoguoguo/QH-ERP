<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { financialCloseApi, type BankAccountRecord } from '../../shared/api/financialCloseApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  bankAccountTypeText,
  bankSensitiveText,
  createFinancialCloseIdempotencyKey,
  financialCloseActionState,
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
const accountDialogVisible = ref(false)
const accountForm = reactive({
  mode: 'create' as 'create' | 'edit',
  id: null as BankAccountRecord['id'] | null,
  accountName: '',
  accountType: 'BASIC',
  bankName: '',
  currency: 'CNY',
  glAccountId: '100201',
  openedOn: '2026-01-01',
  accountNo: '',
  version: 0,
})

const canManageAccounts = computed(() => authStore.hasPermission(financialClosePermissions.bankAccountManage))

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
  const state = bankAccountAction(record, 'DISABLE')
  if (actionLoading.value || !state.allowed) {
    actionError.value = state.reason
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

function bankAccountAction(record: BankAccountRecord, action: string) {
  return financialCloseActionState(record, action, canManageAccounts.value, '无银行账户维护权限')
}

function openCreateDialog() {
  if (!canManageAccounts.value) {
    actionError.value = '无银行账户维护权限'
    return
  }
  Object.assign(accountForm, {
    mode: 'create',
    id: null,
    accountName: '',
    accountType: 'BASIC',
    bankName: '',
    currency: 'CNY',
    glAccountId: '100201',
    openedOn: '2026-01-01',
    accountNo: '',
    version: 0,
  })
  accountDialogVisible.value = true
}

function openEditDialog(record: BankAccountRecord) {
  const state = bankAccountAction(record, 'UPDATE')
  if (!state.allowed) {
    actionError.value = state.reason
    return
  }
  Object.assign(accountForm, {
    mode: 'edit',
    id: record.id,
    accountName: record.accountName || '',
    accountType: record.accountType || 'BASIC',
    bankName: record.bankName || '',
    currency: 'CNY',
    glAccountId: record.glAccountCode || '100201',
    openedOn: '2026-01-01',
    accountNo: '',
    version: record.version,
  })
  accountDialogVisible.value = true
}

async function saveBankAccount() {
  if (!canManageAccounts.value) {
    actionError.value = '无银行账户维护权限'
    return
  }
  if (!accountForm.accountName.trim() || !accountForm.bankName.trim() || !accountForm.glAccountId.trim()) {
    actionError.value = '账户名称、开户行和绑定科目必填'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  const glAccountIdNumber = Number(accountForm.glAccountId)
  const glAccountId = Number.isFinite(glAccountIdNumber) ? glAccountIdNumber : accountForm.glAccountId
  const payload = {
    accountName: accountForm.accountName.trim(),
    accountType: accountForm.accountType,
    bankName: accountForm.bankName.trim(),
    currency: accountForm.currency,
    glAccountId,
    openedOn: accountForm.openedOn,
    accountNo: accountForm.accountNo.trim() || undefined,
    idempotencyKey: createFinancialCloseIdempotencyKey('bank-account-save'),
    ...(accountForm.mode === 'edit' ? { version: accountForm.version } : {}),
  }
  try {
    if (accountForm.mode === 'edit' && accountForm.id !== null) {
      await financialCloseApi.bankAccounts.update(accountForm.id, payload)
    } else {
      await financialCloseApi.bankAccounts.create(payload)
    }
    accountDialogVisible.value = false
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
      <el-button
        data-test="open-bank-account-create"
        type="primary"
        :disabled="!canManageAccounts"
        @click="openCreateDialog"
      >
        新增银行账户
      </el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="账户、银行或科目" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadRecords">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="银行账户只绑定启用、可记账的 1002 银行存款末级科目，不接银行直连。" :closable="false" />
      <el-alert v-if="!canManageAccounts" type="warning" title="无银行账户维护权限" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无银行账户'" stripe>
        <el-table-column prop="accountName" label="账户名称" min-width="160" />
        <el-table-column label="账户类型" min-width="100"><template #default="{ row }">{{ bankAccountTypeText(row.accountType) }}</template></el-table-column>
        <el-table-column prop="bankName" label="开户行" min-width="160" show-overflow-tooltip />
        <el-table-column prop="accountNoMasked" label="账号" min-width="140" />
        <el-table-column prop="glAccountCode" label="绑定科目" min-width="130" />
        <el-table-column label="敏感权限" min-width="140">
          <template #default="{ row }">{{ bankSensitiveText(row.bankSensitiveVisible) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="90"><template #default="{ row }">{{ row.enabled === false ? '停用' : '启用' }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" min-width="110">
          <template #default="{ row }">
            <div class="financial-close-table-actions">
              <el-button
                data-test="open-bank-account-edit"
                text
                :disabled="!bankAccountAction(row, 'UPDATE').allowed"
                @click="openEditDialog(row)"
              >
                编辑
              </el-button>
              <el-button
                data-test="disable-bank-account"
                text
                type="warning"
                :loading="actionLoading"
                :disabled="!bankAccountAction(row, 'DISABLE').allowed"
                @click="disableAccount(row)"
              >
                停用
              </el-button>
              <span v-if="bankAccountAction(row, 'DISABLE').reason" class="financial-close-disabled-reason">{{ bankAccountAction(row, 'DISABLE').reason }}</span>
            </div>
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
    <el-dialog v-model="accountDialogVisible" title="银行账户维护" width="min(640px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="账户名称"><el-input v-model="accountForm.accountName" name="bank-account-name" /></el-form-item>
        <el-form-item label="账户类型">
          <el-select v-model="accountForm.accountType">
            <el-option label="基本户" value="BASIC" />
            <el-option label="一般户" value="GENERAL" />
            <el-option label="专用户" value="SPECIAL" />
            <el-option label="临时户" value="TEMPORARY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="开户行"><el-input v-model="accountForm.bankName" name="bank-account-bank-name" /></el-form-item>
        <el-form-item label="绑定总账科目 ID"><el-input v-model="accountForm.glAccountId" name="bank-account-gl-account-id" /></el-form-item>
        <el-form-item label="启用日期"><el-input v-model="accountForm.openedOn" name="bank-account-opened-on" /></el-form-item>
        <el-form-item label="账号">
          <el-input v-model="accountForm.accountNo" name="bank-account-no" placeholder="仅提交后端生成不可恢复指纹；编辑可留空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button data-test="save-bank-account" type="primary" :loading="actionLoading" @click="saveBankAccount">保存</el-button>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>
