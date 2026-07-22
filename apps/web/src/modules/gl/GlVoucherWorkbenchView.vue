<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { glApi, type GlVoucherRecord, type GlVoucherStatus } from '../../shared/api/glApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import {
  formatGlAmount,
  glActionDisabledReason,
  glAllowedActionsText,
  glCombinedActionDisabledReason,
  glBusinessSourceMetaText,
  glBusinessSourceText,
  glErrorMessage,
  glFormalSourceText,
  glPageItems,
  glPageSizes,
  glPageTotal,
  glVoucherDisplayNo,
  glVoucherStatusText,
} from './glPageHelpers'
import './GlShared.css'

const router = useRouter()
const route = useRoute()
const filters = reactive<{ keyword: string; status: '' | GlVoucherStatus; periodCode: string; sourceType: string }>({
  keyword: '',
  status: '',
  periodCode: '',
  sourceType: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<GlVoucherRecord[]>([])
const loading = ref(false)
const error = ref('')

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await glApi.vouchers.list({
      keyword: filters.keyword,
      status: filters.status || undefined,
      periodCode: filters.periodCode,
      sourceType: filters.sourceType,
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

function amountText(record: GlVoucherRecord, field: 'debitTotal' | 'creditTotal') {
  if (record.amountVisible === false) {
    return record.restrictedReason || '无权查看GL金额'
  }
  return formatGlAmount(record[field])
}

function sourceText(record: GlVoucherRecord) {
  return `${glFormalSourceText(record)} / ${glBusinessSourceText(record)} / ${glBusinessSourceMetaText(record)}`
}

function openDetail(record: GlVoucherRecord) {
  return router.push({
    name: 'gl-voucher-detail',
    params: { id: record.id },
    query: { returnTo: route.fullPath || '/gl/vouchers' },
  })
}

function openCreate() {
  return router.push({ name: 'gl-voucher-create', query: { returnTo: route.fullPath || '/gl/vouchers' } })
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.status = ''
  filters.periodCode = ''
  filters.sourceType = ''
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
  <MasterDataTableView title="正式凭证工作台" description="处理手工凭证和 028 READY 凭证草稿转正式凭证；审批通过后才同事务记账。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button type="primary" @click="openCreate">新增手工凭证</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="凭证号、草稿号或摘要" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="SUBMITTED" />
            <el-option label="已记账" value="POSTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间"><el-input v-model="filters.periodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item label="来源"><el-input v-model="filters.sourceType" clearable placeholder="MANUAL / FIN_VOUCHER_DRAFT" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="正式凭证加载中" :closable="false" />
      <el-alert type="info" title="无直接记账按钮；GL_VOUCHER_POST 固定审批最终通过并记账。" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无正式凭证'" stripe>
        <el-table-column label="凭证号" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ glVoucherDisplayNo(row) }}</template>
        </el-table-column>
        <el-table-column prop="voucherDate" label="凭证日期" min-width="110" />
        <el-table-column prop="accountingPeriodCode" label="会计期间" min-width="110" />
        <el-table-column label="状态" min-width="100"><template #default="{ row }">{{ glVoucherStatusText(row.status) }}</template></el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
        <el-table-column label="来源" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ sourceText(row) }}</template></el-table-column>
        <el-table-column label="借方合计" min-width="130" align="right"><template #default="{ row }"><span class="gl-amount">{{ amountText(row, 'debitTotal') }}</span></template></el-table-column>
        <el-table-column label="贷方合计" min-width="130" align="right"><template #default="{ row }"><span class="gl-amount">{{ amountText(row, 'creditTotal') }}</span></template></el-table-column>
        <el-table-column label="动作状态" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ glCombinedActionDisabledReason(row) || glActionDisabledReason(row, 'CANCEL') || glAllowedActionsText(row.allowedActions) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="100">
          <template #default="{ row }">
            <el-button data-test="gl-voucher-detail" text @click="openDetail(row)">详情</el-button>
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
  </MasterDataTableView>
</template>
