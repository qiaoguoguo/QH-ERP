<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { glApi, type GlPostingRuleRecord } from '../../shared/api/glApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { glActionDisabledReason, glErrorMessage, glPageItems, glPageSizes, glPageTotal } from './glPageHelpers'
import './GlShared.css'

const filters = reactive({ sourceType: '', status: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<GlPostingRuleRecord[]>([])
const loading = ref(false)
const error = ref('')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await glApi.postingRules.list({
      sourceType: filters.sourceType || undefined,
      status: filters.status || undefined,
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

function search() {
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
  <MasterDataTableView title="自动制证规则" description="维护业务事实到正式凭证草稿的映射；预览不制证，不影响账簿。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="来源类型"><el-input v-model="filters.sourceType" clearable placeholder="SALES_INVOICE" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用" value="ACTIVE" />
            <el-option label="已替代" value="SUPERSEDED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="规则预览不制证，仅用于校验科目、辅助核算与金额方向。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="制证规则加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无自动制证规则'" stripe>
        <el-table-column prop="sourceType" label="来源类型" min-width="150" />
        <el-table-column prop="sourceVariant" label="来源变体" min-width="140" />
        <el-table-column prop="versionNo" label="版本" min-width="90" align="right" />
        <el-table-column prop="status" label="状态" min-width="110" />
        <el-table-column prop="validationStatus" label="校验状态" min-width="120" />
        <el-table-column prop="lineCount" label="分录行数" min-width="100" align="right" />
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
