<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { procurementApi, type ProcurementInquirySummaryRecord } from '../../shared/api/procurementApi'
import { createIdempotencyKey, documentPlatformApi, type DocumentTaskRecord } from '../../shared/api/documentPlatformApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import { useAuthStore } from '../../stores/authStore'
import { pageItems } from '../system/shared/pageHelpers'
import {
  normalizeOptionalId,
  procurementErrorMessage,
  procurementInquiryStatusLabel,
  procurementModeDisplay,
} from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import BusinessReferenceSelect from '../system/shared/BusinessReferenceSelect.vue'
import type { BusinessReferenceOption } from '../system/shared/businessReferenceSelectTypes'

const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const records = ref<ProcurementInquirySummaryRecord[]>([])
const total = ref(0)
const filters = reactive({
  keyword: '',
  procurementMode: undefined as 'PUBLIC' | 'PROJECT' | undefined,
  projectId: '' as string | number | '',
  status: undefined as ProcurementInquirySummaryRecord['status'] | undefined,
  page: 1,
  pageSize: 10,
})

const canExport = computed(() => (
  authStore.hasPermission('procurement:inquiry:view')
  && authStore.hasPermission('platform:document-task:create')
  && authStore.hasPermission('procurement:document:export')
))

function projectOption(project: SalesProjectSummary): BusinessReferenceOption {
  return { id: project.id, label: `${project.projectNo} ${project.name}` }
}

async function loadProjectOptions(keyword: string) {
  const page = await salesProjectApi.projects.list({
    keyword,
    status: 'ACTIVE',
    page: 1,
    pageSize: 50,
  })
  return pageItems(page).map(projectOption)
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await procurementApi.inquiries.list({
      keyword: filters.keyword,
      procurementMode: filters.procurementMode,
      projectId: normalizeOptionalId(filters.projectId),
      status: filters.status,
      page: filters.page,
      pageSize: filters.pageSize,
    })
    records.value = pageItems(page)
    total.value = page.total
  } catch (caught) {
    records.value = []
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function exportInquiries() {
  if (actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createProcurementInquiries({
      keyword: filters.keyword,
      procurementMode: filters.procurementMode,
      projectId: normalizeOptionalId(filters.projectId),
      status: filters.status,
      idempotencyKey: createIdempotencyKey('procurement-inquiry-export'),
    })
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function searchRecords() {
  filters.page = 1
  void loadRecords()
}

function resetFilters() {
  filters.keyword = ''
  filters.procurementMode = undefined
  filters.projectId = ''
  filters.status = undefined
  filters.page = 1
  filters.pageSize = 10
  void loadRecords()
}

function changePage(page: number) {
  filters.page = page
  void loadRecords()
}

function changePageSize(pageSize: number) {
  filters.pageSize = pageSize
  filters.page = 1
  void loadRecords()
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="询价比价" description="同一询价范围内收集报价，导入和导出复用文档任务。">
    <template #actions>
        <el-button
          v-if="canExport"
          data-test="export-inquiries"
          :loading="actionLoading"
          @click="exportInquiries"
        >
          当前筛选导出
        </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="询价号、标题、物料" />
        </el-form-item>
        <el-form-item label="采购模式">
          <el-select v-model="filters.procurementMode" clearable placeholder="全部模式">
            <el-option label="公共采购" value="PUBLIC" />
            <el-option label="项目专采" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <BusinessReferenceSelect
            v-model="filters.projectId"
            data-test="inquiry-project-filter"
            placeholder="项目编号或名称"
            :load-options="loadProjectOptions"
          />
        </el-form-item>
        <el-form-item label="业务状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="RELEASED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-inquiries" type="primary" @click="searchRecords">查询</el-button>
          <el-button data-test="reset-inquiries" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <ProcurementDocumentTaskPanel :task="latestDocumentTask" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无询价'" stripe v-loading="loading">
        <el-table-column prop="inquiryNo" label="询价号" min-width="150" show-overflow-tooltip />
        <el-table-column label="采购模式/项目" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ procurementModeDisplay(row.procurementMode, row.projectCode, row.projectName) }}</template>
        </el-table-column>
        <el-table-column label="物料摘要" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialSummary || '物料摘要未返回' }}</template>
        </el-table-column>
        <el-table-column label="业务状态" min-width="130">
          <template #default="{ row }">业务状态：{{ procurementInquiryStatusLabel(row.status, row.statusName) }}</template>
        </el-table-column>
        <el-table-column label="供应商/报价" min-width="170">
          <template #default="{ row }">供应商 {{ row.supplierCount }} 家 / 报价 {{ row.quoteCount }} 条</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <router-link :to="{ name: 'procurement-inquiry-detail', params: { id: String(row.id) } }">详情</router-link>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      background
      layout="total, sizes, prev, pager, next"
      :current-page="filters.page"
      :page-size="filters.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>

<style scoped>
</style>
