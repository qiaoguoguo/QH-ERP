<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { glApi, type GlAccountRecord } from '../../shared/api/glApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  glAccountCategoryText,
  glActionDisabledReason,
  glBalanceDirectionText,
  glErrorMessage,
  glPageItems,
  glPageSizes,
  glPageTotal,
} from './glPageHelpers'
import './GlShared.css'

const filters = reactive({ keyword: '', category: '', enabled: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<GlAccountRecord[]>([])
const loading = ref(false)
const error = ref('')

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
  </MasterDataTableView>
</template>
