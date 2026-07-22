<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  platformGovernanceApi,
  type HistoryImportAdapterRecord,
  type HistoryImportRecord,
} from '../../../shared/api/platformGovernanceApi'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import { downloadFile } from '../../../shared/file/download'
import { pageItems } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { useAuthStore } from '../../../stores/authStore'
import { formatPlatformDateTime, platformErrorMessage } from '../platformPageHelpers'
import { historyImportStatusLabel, historyImportStatusTagType } from '../platformGovernanceLabels'

const authStore = useAuthStore()
const initialAdapterCode = new URLSearchParams(window.location.search).get('adapterCode')?.trim() ?? ''
const filters = reactive({ keyword: '', adapterCode: initialAdapterCode, status: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const adapters = ref<HistoryImportAdapterRecord[]>([])
const records = ref<HistoryImportRecord[]>([])
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const statusOptions = [
  ['QUEUED', '排队中'],
  ['RUNNING', '执行中'],
  ['READY_TO_COMMIT', '待确认'],
  ['VALIDATION_FAILED', '预检失败'],
  ['SUCCEEDED', '已完成'],
  ['FAILED', '执行失败'],
  ['CANCELLED', '已取消'],
  ['EXPIRED', '已过期'],
] as const

async function loadAdapters() {
  adapters.value = await platformGovernanceApi.historyImportAdapters.list()
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await platformGovernanceApi.historyImports.list({
      keyword: filters.keyword,
      adapterCode: filters.adapterCode,
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

function requiredPermission(adapter: HistoryImportAdapterRecord): string | null {
  return adapter.requiredPermissionCode ?? adapter.requiredPermission ?? null
}

function canUploadAdapter(adapter: HistoryImportAdapterRecord) {
  const permission = requiredPermission(adapter)
  return authStore.hasPermission('platform:history-import:create')
    && (!permission || authStore.hasPermission(permission))
}

async function downloadTemplate(adapter: HistoryImportAdapterRecord) {
  actionLoading.value = true
  actionError.value = ''
  try {
    downloadFile(await platformGovernanceApi.historyImportAdapters.downloadTemplate(adapter.adapterCode))
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function uploadHistoryFile(adapterCode: string, file: File) {
  actionLoading.value = true
  actionError.value = ''
  try {
    await platformGovernanceApi.historyImports.upload(adapterCode, {
      file,
      idempotencyKey: createIdempotencyKey('history-import-upload'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = platformErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function handleUpload(adapter: HistoryImportAdapterRecord, uploadFile: { raw?: File }) {
  if (uploadFile.raw) {
    void uploadHistoryFile(adapter.adapterCode, uploadFile.raw)
  }
}

function detailRoute(record: HistoryImportRecord) {
  return `/platform/history-imports/${encodeURIComponent(String(record.taskId ?? record.id))}?returnTo=${encodeURIComponent('/platform/history-imports')}`
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
  <MasterDataTableView title="历史数据导入" description="固定适配器模板下载、上传预检、错误明细和人工确认。">
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="history-import-keyword" clearable placeholder="批次号、任务号或文件名" />
        </el-form-item>
        <el-form-item label="适配器">
          <el-select v-model="filters.adapterCode" clearable placeholder="全部适配器">
            <el-option v-for="adapter in adapters" :key="adapter.adapterCode" :label="adapter.name" :value="adapter.adapterCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option v-for="[value, label] in statusOptions" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-history-imports" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-history-imports" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="历史导入批次加载中" :closable="false" />
    </template>

    <section class="section-block governance-directory">
      <h2>适配器目录</h2>
      <div class="table-scroll">
        <el-table :data="adapters" empty-text="暂无历史导入适配器" stripe>
          <el-table-column prop="adapterCode" label="适配器代码" min-width="210" show-overflow-tooltip />
          <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="templateVersion" label="模板版本" width="100" />
          <el-table-column prop="maxRows" label="行数上限" width="100" />
          <el-table-column label="领域权限" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ requiredPermission(row) || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <el-button data-test="download-history-template" size="small" text :loading="actionLoading" @click="downloadTemplate(row)">
                下载模板
              </el-button>
              <el-upload
                v-if="canUploadAdapter(row)"
                :auto-upload="false"
                :show-file-list="false"
                accept=".xlsx"
                :on-change="(file: { raw?: File }) => handleUpload(row, file)"
              >
                <el-button data-test="upload-history-file" size="small" text :loading="actionLoading">上传预检</el-button>
              </el-upload>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无历史导入批次'" stripe>
        <el-table-column prop="taskNo" label="批次/任务号" width="160" show-overflow-tooltip />
        <el-table-column label="适配器" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ adapterName(row.adapterCode) }}</template>
        </el-table-column>
        <el-table-column prop="sourceFileName" label="源文件" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="historyImportStatusTagType(row.status)" size="small">{{ historyImportStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalRows" label="总行数" width="90" />
        <el-table-column prop="successRows" label="成功数" width="90" />
        <el-table-column prop="failedRows" label="错误数" width="90" />
        <el-table-column prop="createdByName" label="上传人" width="110" show-overflow-tooltip />
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatPlatformDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <RouterLink data-test="history-import-detail-link" :to="detailRoute(row)">查看详情</RouterLink>
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

<style scoped>
.section-block {
  padding: 16px;
  border-bottom: 1px solid var(--qherp-border-soft);
  background: var(--qherp-surface);
}

.section-block h2 {
  margin-bottom: 12px;
  font-size: 18px;
}

.governance-directory {
  border-radius: 0;
}
</style>
