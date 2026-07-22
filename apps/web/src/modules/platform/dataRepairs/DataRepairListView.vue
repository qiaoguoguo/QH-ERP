<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  platformGovernanceApi,
  type DataRepairAdapterRecord,
  type DataRepairRecord,
  type DataRepairStatus,
} from '../../../shared/api/platformGovernanceApi'
import { pageItems } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { useAuthStore } from '../../../stores/authStore'
import { formatPlatformDateTime, platformErrorMessage } from '../platformPageHelpers'
import { dataRepairRiskLabel, dataRepairStatusLabel, dataRepairStatusTagType } from '../platformGovernanceLabels'

const authStore = useAuthStore()
const filters = reactive<{ keyword: string; adapterCode: string; status: string }>({
  keyword: '',
  adapterCode: '',
  status: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const adapters = ref<DataRepairAdapterRecord[]>([])
const records = ref<DataRepairRecord[]>([])
const loading = ref(false)
const error = ref('')
const canCreate = computed(() => authStore.hasPermission('platform:data-repair:create'))
const selectedFilterAdapter = computed(() => adapters.value.find((item) => item.adapterCode === filters.adapterCode) ?? null)

const statusOptions: Array<{ value: DataRepairStatus; label: string }> = [
  'DRAFT',
  'PENDING_APPROVAL',
  'READY_TO_EXECUTE',
  'EXECUTING',
  'EXECUTED',
  'VERIFIED',
  'REJECTED',
  'CANCELLED',
  'FAILED',
  'VERIFY_FAILED',
].map((status) => ({ value: status as DataRepairStatus, label: dataRepairStatusLabel(status) }))

async function loadAdapters() {
  adapters.value = await platformGovernanceApi.dataRepairAdapters.list()
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await platformGovernanceApi.dataRepairs.list({
      keyword: filters.keyword,
      adapterCode: filters.adapterCode,
      targetObjectType: selectedFilterAdapter.value?.targetObjectType,
      status: filters.status,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = platformErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function adapterName(code: string) {
  return adapters.value.find((item) => item.adapterCode === code)?.name ?? code
}

function detailRoute(record: DataRepairRecord) {
  return `/platform/data-repairs/${encodeURIComponent(String(record.id))}?returnTo=${encodeURIComponent('/platform/data-repairs')}`
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetFilters() {
  filters.keyword = ''
  filters.adapterCode = ''
  filters.status = ''
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

onMounted(async () => {
  try {
    await loadAdapters()
  } catch (caught) {
    error.value = platformErrorMessage(caught)
  }
  await loadRecords()
})
</script>

<template>
  <MasterDataTableView title="数据修复记录" description="受控创建、审批、执行和验证历史资料修复请求。">
    <template #actions>
      <RouterLink
        v-if="canCreate"
        data-test="create-data-repair"
        class="inline-action-link"
        :to="`/platform/data-repairs/create?returnTo=${encodeURIComponent('/platform/data-repairs')}`"
      >
        新增修复申请
      </RouterLink>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="data-repair-keyword" clearable placeholder="修复编号、对象编号或标题" />
        </el-form-item>
        <el-form-item label="适配器">
          <el-select v-model="filters.adapterCode" clearable placeholder="全部适配器">
            <el-option v-for="adapter in adapters" :key="adapter.adapterCode" :label="adapter.name" :value="adapter.adapterCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option v-for="status in statusOptions" :key="status.value" :label="status.label" :value="status.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-data-repairs" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-data-repairs" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="数据修复记录加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无数据修复记录'" stripe>
        <el-table-column label="修复编号" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.requestNo || row.repairNo }}</template>
        </el-table-column>
        <el-table-column label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.riskSummary || row.title || row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column label="适配器" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ adapterName(row.adapterCode) }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.targetObjectNo || '-' }} {{ row.targetObjectSummary || row.targetObjectName || '' }}</template>
        </el-table-column>
        <el-table-column label="风险" width="90">
          <template #default="{ row }">{{ dataRepairRiskLabel(row.riskLevel) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="dataRepairStatusTagType(row.status)" size="small">{{ dataRepairStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createdByUsername || row.applicantName || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatPlatformDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <RouterLink data-test="data-repair-detail-link" :to="detailRoute(row)">查看详情</RouterLink>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>
