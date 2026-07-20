<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { glApi, type GlAccountRecord } from '../../shared/api/glApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  glAccountCategoryText,
  glActionAllowed,
  glActionDisabledReason,
  glBalanceDirectionText,
  createGlIdempotencyKey,
  glErrorMessage,
  glPageItems,
  glPageSizes,
  glPageTotal,
  glPermissions,
} from './glPageHelpers'
import './GlShared.css'

const authStore = useAuthStore()
const filters = reactive({ keyword: '', category: '', enabled: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<GlAccountRecord[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const accountDialogVisible = ref(false)
const accountDialogTitle = ref('新增下级科目')
const accountForm = reactive({
  id: null as string | number | null,
  parentId: null as string | number | null,
  code: '',
  name: '',
  category: 'ASSET',
  balanceDirection: 'DEBIT',
  postable: true,
  enabled: true,
  version: 0,
  auxiliaryRequirements: [] as GlAccountRecord['auxiliaryRequirements'],
})

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await glApi.accounts.list({
      keyword: filters.keyword,
      category: filters.category || undefined,
      enabled: filters.enabled || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = glPageItems(page)
    pagination.total = glPageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = glErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function resetAccountForm(parent?: GlAccountRecord) {
  accountForm.id = null
  accountForm.parentId = parent?.id ?? null
  accountForm.code = ''
  accountForm.name = ''
  accountForm.category = parent?.category ?? 'ASSET'
  accountForm.balanceDirection = parent?.balanceDirection ?? 'DEBIT'
  accountForm.postable = true
  accountForm.enabled = true
  accountForm.version = 0
  accountForm.auxiliaryRequirements = parent?.auxiliaryRequirements ?? []
}

function openCreateChild(row?: GlAccountRecord) {
  resetAccountForm(row ?? records.value[0])
  accountDialogTitle.value = '新增下级科目'
  accountDialogVisible.value = true
}

function openEditAccount(row: GlAccountRecord) {
  accountForm.id = row.id
  accountForm.parentId = row.parentId ?? null
  accountForm.code = row.code
  accountForm.name = row.name
  accountForm.category = row.category
  accountForm.balanceDirection = row.balanceDirection
  accountForm.postable = row.postable
  accountForm.enabled = row.enabled
  accountForm.version = row.version
  accountForm.auxiliaryRequirements = row.auxiliaryRequirements ?? []
  accountDialogTitle.value = '编辑科目'
  accountDialogVisible.value = true
}

async function saveAccount() {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = {
      parentId: accountForm.parentId,
      code: accountForm.code,
      name: accountForm.name,
      category: accountForm.category,
      balanceDirection: accountForm.balanceDirection,
      postable: accountForm.postable,
      enabled: accountForm.enabled,
      auxiliaryRequirements: accountForm.auxiliaryRequirements.map((item) => ({
        dimensionCode: item.dimensionCode,
        requirementType: item.requirement,
      })),
      version: accountForm.version,
      idempotencyKey: createGlIdempotencyKey('gl-account-save'),
    }
    if (accountForm.id) {
      await glApi.accounts.update(accountForm.id, payload)
    } else {
      await glApi.accounts.create(payload)
    }
    accountDialogVisible.value = false
    await loadRecords()
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function disableAccount(row: GlAccountRecord) {
  if (actionLoading.value) {
    return
  }
  const reason = glActionDisabledReason(row, 'DISABLE')
  if (reason && !glActionAllowed(row, 'DISABLE')) {
    actionError.value = reason
    return
  }
  if (!(await confirmAction(`停用会计科目“${row.code} ${row.name}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await glApi.accounts.disable(row.id, {
      version: row.version,
      reason: `停用 ${row.code} ${row.name}`,
      idempotencyKey: createGlIdempotencyKey('gl-account-disable'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function auxiliaryText(record: GlAccountRecord) {
  return record.auxiliaryRequirements?.length
    ? record.auxiliaryRequirements.map((item) => `${item.dimensionName || item.dimensionCode} ${item.requirement === 'REQUIRED' ? '必填' : '可选'}`).join('、')
    : '无'
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.category = ''
  filters.enabled = ''
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

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="会计科目" description="企业会计准则制造业基础科目模板，支持科目分类编码扩展和辅助核算要求。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button
        data-test="create-child-account"
        type="primary"
        :disabled="!authStore.hasPermission(glPermissions.accountCreate)"
        @click="openCreateChild()"
      >
        新增下级科目
      </el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="科目编码或名称" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.category" clearable placeholder="全部分类">
            <el-option label="资产" value="ASSET" />
            <el-option label="负债" value="LIABILITY" />
            <el-option label="权益" value="EQUITY" />
            <el-option label="成本" value="COST" />
            <el-option label="损益" value="PROFIT_LOSS" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-select v-model="filters.enabled" clearable placeholder="全部">
            <el-option label="启用" value="true" />
            <el-option label="停用" value="false" />
          </el-select>
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
      <el-alert v-if="loading" type="info" title="会计科目加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无会计科目'" stripe>
        <el-table-column prop="code" label="科目编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="name" label="科目名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="分类" min-width="100"><template #default="{ row }">{{ glAccountCategoryText(row.category) }}</template></el-table-column>
        <el-table-column prop="level" label="级次" min-width="80" align="right" />
        <el-table-column label="余额方向" min-width="100"><template #default="{ row }">{{ glBalanceDirectionText(row.balanceDirection) }}</template></el-table-column>
        <el-table-column label="辅助核算" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ auxiliaryText(row) }}</template></el-table-column>
        <el-table-column label="状态" min-width="90"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
        <el-table-column label="动作状态" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ glActionDisabledReason(row, 'DISABLE') || (row.allowedActions?.join('、') || '-') }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="190">
          <template #default="{ row }">
            <el-button data-test="create-child-account" text @click="openCreateChild(row)">新增下级</el-button>
            <el-button data-test="edit-gl-account" text :disabled="!glActionAllowed(row, 'UPDATE')" @click="openEditAccount(row)">编辑</el-button>
            <el-button data-test="disable-gl-account" text type="danger" @click="disableAccount(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="glPageSizes"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />
    <el-drawer v-model="accountDialogVisible" :title="accountDialogTitle" size="min(720px, 92vw)" :teleported="false">
      <el-form class="gl-dialog-form" label-position="top">
        <el-form-item label="上级科目">
          <el-input :model-value="accountForm.parentId ?? '根科目'" disabled />
        </el-form-item>
        <el-form-item label="科目编码">
          <el-input v-model="accountForm.code" name="gl-account-code" clearable placeholder="例如 100201" />
        </el-form-item>
        <el-form-item label="科目名称">
          <el-input v-model="accountForm.name" name="gl-account-name" clearable placeholder="科目名称" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="accountForm.category">
            <el-option label="资产" value="ASSET" />
            <el-option label="负债" value="LIABILITY" />
            <el-option label="权益" value="EQUITY" />
            <el-option label="成本" value="COST" />
            <el-option label="损益" value="PROFIT_LOSS" />
          </el-select>
        </el-form-item>
        <el-form-item label="余额方向">
          <el-select v-model="accountForm.balanceDirection">
            <el-option label="借方" value="DEBIT" />
            <el-option label="贷方" value="CREDIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="辅助核算">
          <el-tag v-for="item in accountForm.auxiliaryRequirements" :key="item.dimensionCode" class="gl-inline-tag">
            {{ item.dimensionName || item.dimensionCode }} {{ item.requirement === 'REQUIRED' ? '必填' : '可选' }}
          </el-tag>
          <span v-if="accountForm.auxiliaryRequirements.length === 0" class="gl-muted">无辅助核算要求</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button data-test="save-gl-account" type="primary" :loading="actionLoading" @click="saveAccount">保存</el-button>
      </template>
    </el-drawer>
  </MasterDataTableView>
</template>
