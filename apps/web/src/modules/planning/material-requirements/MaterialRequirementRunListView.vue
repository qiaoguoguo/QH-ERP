<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AccountPermissionApiError } from '../../../shared/api/accountPermissionApi'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../../shared/api/documentPlatformApi'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../../shared/api/masterDataApi'
import {
  materialRequirementApi,
  type MaterialRequirementRunRecord,
  type MaterialRequirementRunStatus,
} from '../../../shared/api/materialRequirementApi'
import { salesApi, type SalesOrderSummaryRecord } from '../../../shared/api/salesApi'
import { salesProjectApi, type SalesProjectSummary } from '../../../shared/api/salesProjectApi'
import { useAuthStore } from '../../../stores/authStore'
import BusinessReferenceSelect from '../../system/shared/BusinessReferenceSelect.vue'
import type { BusinessReferenceOption } from '../../system/shared/businessReferenceSelectTypes'
import { pageItems } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import {
  failedRunSummaryLabel,
  formatMaterialRequirementDateTime,
  hasAllowedAction,
  materialRequirementErrorMessage,
  materialRequirementPermissions,
  normalizeOptionalId,
  runStatusLabel,
  runStatusTagType,
} from './materialRequirementPageHelpers'

const authStore = useAuthStore()
const router = useRouter()
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const records = ref<MaterialRequirementRunRecord[]>([])
const total = ref(0)
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const createDialog = reactive({ visible: false, includePublicDemand: true })
const filters = reactive({
  projectId: '' as string | number | '',
  customerId: '' as string | number | '',
  contractId: '' as string | number | '',
  orderId: '' as string | number | '',
  materialId: '' as string | number | '',
  requiredDateTo: '',
  status: undefined as MaterialRequirementRunStatus | undefined,
  expired: undefined as boolean | undefined,
  page: 1,
  pageSize: 10,
})

const canCalculate = computed(() => authStore.hasPermission(materialRequirementPermissions.calculate))
const canExport = computed(() => authStore.hasPermission(materialRequirementPermissions.export))

function projectOption(project: SalesProjectSummary): BusinessReferenceOption {
  return { id: project.id, label: `${project.projectNo} ${project.name}` }
}

function customerOption(customer: PartnerRecord): BusinessReferenceOption {
  return { id: customer.id, label: `${customer.code} ${customer.name}` }
}

function materialOption(material: MaterialRecord): BusinessReferenceOption {
  return { id: material.id, label: `${material.code} ${material.name}` }
}

function orderOption(order: SalesOrderSummaryRecord): BusinessReferenceOption {
  return { id: order.id, label: `${order.orderNo} ${order.customerName}` }
}

function contractOption(candidate: { contractId?: string | number | null; contractNo?: string | null; contractName?: string | null }): BusinessReferenceOption {
  return { id: candidate.contractId ?? '', label: [candidate.contractNo, candidate.contractName].filter(Boolean).join(' ') || '合同未返回' }
}

async function loadProjectOptions(keyword: string) {
  return loadReferenceOptions(async () => {
    const page = await salesProjectApi.projects.list({ keyword, status: 'ACTIVE', page: 1, pageSize: 50 })
    return pageItems(page).map(projectOption)
  })
}

async function loadCustomerOptions(keyword: string) {
  return loadReferenceOptions(async () => {
    const page = await masterDataApi.customers.list({ keyword, status: 'ENABLED', page: 1, pageSize: 50 })
    return pageItems(page).map(customerOption)
  })
}

async function loadContractOptions(keyword: string) {
  return loadReferenceOptions(async () => {
    const page = await salesProjectApi.listOrderLinkCandidates({ keyword, page: 1, pageSize: 50 })
    return pageItems(page).filter((item) => item.contractId !== null && item.contractId !== undefined).map(contractOption)
  })
}

async function loadOrderOptions(keyword: string) {
  return loadReferenceOptions(async () => {
    const page = await salesApi.orders.list({ keyword, page: 1, pageSize: 50 })
    return pageItems(page).map(orderOption)
  })
}

async function loadMaterialOptions(keyword: string) {
  return loadReferenceOptions(async () => {
    const page = await masterDataApi.materials.list({ keyword, status: 'ENABLED', page: 1, pageSize: 50 })
    return pageItems(page).map(materialOption)
  })
}

async function loadReferenceOptions(loader: () => Promise<BusinessReferenceOption[]>): Promise<BusinessReferenceOption[]> {
  try {
    return await loader()
  } catch (caught) {
    if (caught instanceof AccountPermissionApiError && caught.status === 403) {
      return []
    }
    throw caught
  }
}

function queryPayload() {
  return {
    projectId: normalizeOptionalId(filters.projectId),
    customerId: normalizeOptionalId(filters.customerId),
    contractId: normalizeOptionalId(filters.contractId),
    orderId: normalizeOptionalId(filters.orderId),
    materialId: normalizeOptionalId(filters.materialId),
    requiredDateTo: filters.requiredDateTo,
    status: filters.status,
    expired: filters.expired,
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await materialRequirementApi.runs.list({
      ...queryPayload(),
      page: filters.page,
      pageSize: filters.pageSize,
    })
    records.value = pageItems(page)
    total.value = page.total
  } catch (caught) {
    records.value = []
    total.value = 0
    error.value = materialRequirementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function searchRecords() {
  filters.page = 1
  void loadRecords()
}

function resetFilters() {
  filters.projectId = ''
  filters.customerId = ''
  filters.contractId = ''
  filters.orderId = ''
  filters.materialId = ''
  filters.requiredDateTo = ''
  filters.status = undefined
  filters.expired = undefined
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

async function exportRuns() {
  if (actionLoading.value || !canExport.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createMaterialRequirementRuns({
      ...queryPayload(),
      idempotencyKey: createIdempotencyKey('material-requirement-export'),
    })
  } catch (caught) {
    actionError.value = materialRequirementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function createRun() {
  if (actionLoading.value || !canCalculate.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    const run = await materialRequirementApi.runs.create({
      ...queryPayload(),
      includePublicDemand: createDialog.includePublicDemand,
      idempotencyKey: createIdempotencyKey('material-requirement-run'),
    })
    createDialog.visible = false
    await router.push({ name: 'planning-material-requirement-detail', params: { id: String(run.id) } })
  } catch (caught) {
    actionError.value = materialRequirementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function recalculateRun(row: MaterialRequirementRunRecord) {
  if (actionLoading.value || !canCalculate.value || !hasAllowedAction(row, 'RECALCULATE')) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    const run = await materialRequirementApi.runs.recalculate(row.id, {
      version: row.version,
      idempotencyKey: createIdempotencyKey('material-requirement-recalculate'),
    })
    await router.push({ name: 'planning-material-requirement-detail', params: { id: String(run.id) } })
  } catch (caught) {
    actionError.value = materialRequirementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function viewRun(row: MaterialRequirementRunRecord) {
  void router.push({ name: 'planning-material-requirement-detail', params: { id: String(row.id) } })
}

function runAvailabilityText(row: MaterialRequirementRunRecord): string {
  if (row.status === 'FAILED') {
    return failedRunSummaryLabel(row.failureCode, row.failureSummary)
  }
  if (row.stale) {
    return row.sourceChangedReason || '来源已变化'
  }
  if (row.expired) {
    return '已过期，需重算'
  }
  return '当前可用'
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="订单缺料分析" description="按已冻结 026 口径查看可追溯缺料快照、来源状态和供给建议。">
    <template #actions>
      <el-button
        v-if="canCalculate"
        data-test="create-material-requirement-run"
        type="primary"
        @click="createDialog.visible = true"
      >
        新建分析
      </el-button>
      <el-button
        v-if="canExport"
        data-test="export-material-requirements"
        :loading="actionLoading"
        @click="exportRuns"
      >
        当前筛选导出
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="项目">
          <BusinessReferenceSelect v-model="filters.projectId" placeholder="项目编号或名称" :load-options="loadProjectOptions" />
        </el-form-item>
        <el-form-item label="客户">
          <BusinessReferenceSelect v-model="filters.customerId" placeholder="客户编码或名称" :load-options="loadCustomerOptions" />
        </el-form-item>
        <el-form-item label="合同">
          <BusinessReferenceSelect v-model="filters.contractId" placeholder="合同编号或名称" :load-options="loadContractOptions" />
        </el-form-item>
        <el-form-item label="销售订单">
          <BusinessReferenceSelect v-model="filters.orderId" placeholder="销售订单号" :load-options="loadOrderOptions" />
        </el-form-item>
        <el-form-item label="物料">
          <BusinessReferenceSelect v-model="filters.materialId" placeholder="物料编码或名称" :load-options="loadMaterialOptions" />
        </el-form-item>
        <el-form-item label="需求截止">
          <el-date-picker
            v-model="filters.requiredDateTo"
            value-on-clear=""
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="需求截止日期"
          />
        </el-form-item>
        <el-form-item label="运行状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="来源已变化" value="STALE" />
            <el-option label="已过期" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="过期状态">
          <el-select v-model="filters.expired" clearable placeholder="全部">
            <el-option label="仅未过期" :value="false" />
            <el-option label="仅已过期" :value="true" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-material-requirements" type="primary" @click="searchRecords">查询</el-button>
          <el-button data-test="reset-material-requirements" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <el-alert
        v-if="latestDocumentTask"
        class="page-alert"
        type="success"
        :title="`导出任务 ${latestDocumentTask.taskNo}`"
        show-icon
        :closable="false"
      />
    </template>

    <div class="table-scroll">
      <div v-if="loading" class="inline-loading" role="status">加载中</div>
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无订单缺料分析快照'" stripe>
        <el-table-column prop="runNo" label="快照编号" min-width="150" fixed show-overflow-tooltip />
        <el-table-column label="分析范围" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">{{ row.scopeSummary || '范围未返回' }}</template>
        </el-table-column>
        <el-table-column label="运行状态" min-width="130">
          <template #default="{ row }">
            <el-tag :type="runStatusTagType(row.status)">{{ runStatusLabel(row.status, row.statusName) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="运行人/时间" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createdByName || '-' }} · {{ formatMaterialRequirementDateTime(row.completedAt || row.asOfTime) }}</template>
        </el-table-column>
        <el-table-column label="基准日期" min-width="110">
          <template #default="{ row }">{{ row.asOfBusinessDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="结果摘要" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            需求行 {{ row.requirementLineCount }} / 短缺物料 {{ row.shortageMaterialCount }} / 采购建议 {{ row.purchaseSuggestionCount }} / 生产建议 {{ row.productionSuggestionCount }}
          </template>
        </el-table-column>
        <el-table-column label="过期状态" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ runAvailabilityText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button text type="primary" @click="viewRun(row)">查看</el-button>
            <el-button
              v-if="canCalculate && hasAllowedAction(row, 'RECALCULATE')"
              :data-test="`recalculate-run-${row.id}`"
              text
              type="primary"
              :loading="actionLoading"
              @click="recalculateRun(row)"
            >
              重算
            </el-button>
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

    <el-dialog v-model="createDialog.visible" title="新建订单缺料分析" :teleported="false" width="460px">
      <el-alert title="将按当前筛选范围触发 026 快照计算，旧快照不会被覆盖。" type="info" :closable="false" />
      <label class="native-checkbox">
        <input v-model="createDialog.includePublicDemand" type="checkbox" />
        <span>包含公共需求</span>
      </label>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="createRun">开始分析</el-button>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>

<style scoped>
.inline-loading {
  padding: 10px 12px;
  color: #475569;
  font-size: 13px;
}

.native-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  color: #1f2937;
  font-size: 14px;
}

.native-checkbox input {
  width: 16px;
  height: 16px;
  margin: 0;
}
</style>
