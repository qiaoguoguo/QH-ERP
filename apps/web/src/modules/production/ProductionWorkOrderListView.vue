<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  projectProductionApi,
  type ProjectProductionWorkOrderStatus,
  type ProjectProductionWorkOrderSummaryRecord,
  type ProductionOwnershipType,
} from '../../shared/api/projectProductionApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import ProductionWorkOrderStatusTag from './ProductionWorkOrderStatusTag.vue'
import {
  formatProductionDateTime,
  formatProductionQuantity,
  createProductionIdempotencyKey,
  productionErrorMessage,
  workOrderStatusLabel,
} from './productionPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const filters = reactive<{
  keyword: string
  status?: ProjectProductionWorkOrderStatus
  ownershipType?: ProductionOwnershipType
  projectId: string
  sourceMrpSuggestionId: string
  dateFrom: string
  dateTo: string
}>({
  keyword: '',
  status: undefined,
  ownershipType: undefined,
  projectId: '',
  sourceMrpSuggestionId: '',
  dateFrom: '',
  dateTo: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<ProjectProductionWorkOrderSummaryRecord[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canCreate = computed(() => authStore.hasPermission('production:work-order:create'))
const canUpdate = computed(() => authStore.hasPermission('production:work-order:update'))
const canRelease = computed(() => authStore.hasPermission('production:work-order:release'))
const canComplete = computed(() => authStore.hasPermission('production:work-order:complete'))
const canCancel = computed(() => authStore.hasPermission('production:work-order:cancel'))
const canCreateIssue = computed(() => authStore.hasPermission('production:issue:create'))
const canCreateReport = computed(() => authStore.hasPermission('production:report:create'))
const canCreateReceipt = computed(() => authStore.hasPermission('production:receipt:create'))

function queryValue(name: string) {
  const value = route.query[name]
  return Array.isArray(value) ? String(value[0] ?? '') : String(value ?? '')
}

function applyRouteFilters() {
  filters.projectId = queryValue('projectId')
  filters.sourceMrpSuggestionId = queryValue('sourceMrpSuggestionId')
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await projectProductionApi.workOrders.list({
      keyword: filters.keyword,
      status: filters.status,
      ...(filters.projectId.trim() ? { projectId: filters.projectId.trim() } : {}),
      ...(filters.sourceMrpSuggestionId.trim() ? { sourceMrpSuggestionId: filters.sourceMrpSuggestionId.trim() } : {}),
      ...(filters.ownershipType ? { ownershipType: filters.ownershipType } : {}),
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.status = undefined
  filters.ownershipType = undefined
  filters.projectId = queryValue('projectId')
  filters.sourceMrpSuggestionId = queryValue('sourceMrpSuggestionId')
  filters.dateFrom = ''
  filters.dateTo = ''
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

function createWorkOrder() {
  void router.push({ name: 'production-work-order-create' })
}

function viewWorkOrder(record: ProjectProductionWorkOrderSummaryRecord) {
  void router.push({
    name: 'production-work-order-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editWorkOrder(record: ProjectProductionWorkOrderSummaryRecord) {
  void router.push({ name: 'production-work-order-edit', params: { id: String(record.id) } })
}

function createMaterialIssue(record: ProjectProductionWorkOrderSummaryRecord) {
  void router.push({ name: 'production-work-order-material-issues', params: { id: String(record.id) } })
}

function createReport(record: ProjectProductionWorkOrderSummaryRecord) {
  void router.push({ name: 'production-work-order-reports', params: { id: String(record.id) } })
}

function createCompletionReceipt(record: ProjectProductionWorkOrderSummaryRecord) {
  void router.push({ name: 'production-work-order-completion-receipts', params: { id: String(record.id) } })
}

function canExecute(record: ProjectProductionWorkOrderSummaryRecord) {
  return record.status === 'RELEASED' || record.status === 'IN_PROGRESS'
}

function actionAllowed(
  record: ProjectProductionWorkOrderSummaryRecord,
  codes: string[],
  fallback: boolean,
): boolean {
  if (Array.isArray(record.allowedActions)) {
    return codes.some((code) => record.allowedActions?.includes(code))
  }
  return fallback
}

function canEditRecord(record: ProjectProductionWorkOrderSummaryRecord) {
  return canUpdate.value && actionAllowed(record, ['UPDATE'], record.status === 'DRAFT')
}

function canReleaseRecord(record: ProjectProductionWorkOrderSummaryRecord) {
  return canRelease.value && actionAllowed(record, ['RELEASE'], record.status === 'DRAFT')
}

function canCreateIssueRecord(record: ProjectProductionWorkOrderSummaryRecord) {
  return canCreateIssue.value && canExecute(record)
}

function canCreateReportRecord(record: ProjectProductionWorkOrderSummaryRecord) {
  return canCreateReport.value && canExecute(record)
}

function canCreateReceiptRecord(record: ProjectProductionWorkOrderSummaryRecord) {
  return canCreateReceipt.value && canExecute(record)
}

function canCompleteRecord(record: ProjectProductionWorkOrderSummaryRecord) {
  return canComplete.value && actionAllowed(record, ['COMPLETE'], canExecute(record))
}

function canCancelRecord(record: ProjectProductionWorkOrderSummaryRecord) {
  return canCancel.value && actionAllowed(record, ['CANCEL'], record.status === 'DRAFT' || record.status === 'RELEASED')
}

function ownershipText(record: ProjectProductionWorkOrderSummaryRecord) {
  return record.ownershipType === 'PROJECT' ? '项目工单' : '公共工单'
}

function projectText(record: ProjectProductionWorkOrderSummaryRecord) {
  if (record.ownershipType !== 'PROJECT') {
    return '公共'
  }
  return `${record.projectNo || record.projectId || '-'} ${record.projectName || ''}`.trim()
}

async function runWorkOrderAction(record: ProjectProductionWorkOrderSummaryRecord, action: 'release' | 'complete' | 'cancel') {
  if (actionLoading.value) {
    return
  }
  const actionLabels = {
    release: '发布',
    complete: '完成',
    cancel: '取消',
  }
  if (!(await confirmAction(`确认${actionLabels[action]}生产工单“${record.workOrderNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'release') {
      await projectProductionApi.workOrders.release(record.id, {
        version: record.version,
        idempotencyKey: createProductionIdempotencyKey('production-work-order-release'),
      })
    } else if (action === 'complete') {
      await projectProductionApi.workOrders.complete(record.id, {
        version: record.version,
        idempotencyKey: createProductionIdempotencyKey('production-work-order-complete'),
      })
    } else {
      await projectProductionApi.workOrders.cancel(record.id, {
        version: record.version,
        idempotencyKey: createProductionIdempotencyKey('production-work-order-cancel'),
      })
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = productionErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

watch(
  () => [route.query.projectId, route.query.sourceMrpSuggestionId],
  () => {
    const nextProjectId = queryValue('projectId')
    const nextSourceId = queryValue('sourceMrpSuggestionId')
    if (filters.projectId === nextProjectId && filters.sourceMrpSuggestionId === nextSourceId) {
      return
    }
    filters.projectId = nextProjectId
    filters.sourceMrpSuggestionId = nextSourceId
    pagination.page = 1
    void loadRecords()
  },
)

onMounted(() => {
  applyRouteFilters()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="生产工单" description="按工单组织生产领料、报工、完工入库和库存追溯。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-production-work-order" type="primary" @click="createWorkOrder">
        新建工单
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="production-work-order-keyword" clearable placeholder="工单号、产品" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="filters.status"
            clearable
            placeholder="全部状态"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已下达" value="RELEASED" />
            <el-option label="生产中" value="IN_PROGRESS" />
            <el-option label="已完工" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属">
          <el-select v-model="filters.ownershipType" clearable placeholder="全部归属">
            <el-option label="项目工单" value="PROJECT" />
            <el-option label="公共工单" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-input v-model="filters.projectId" name="production-project-id" clearable placeholder="项目 ID" />
        </el-form-item>
        <el-form-item label="来源建议">
          <el-input v-model="filters.sourceMrpSuggestionId" name="production-source-mrp-suggestion-id" clearable placeholder="MRP 建议 ID" />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateFrom" name="production-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="filters.dateTo" name="production-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-production-work-orders" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-production-work-orders" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="生产工单加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && records.length === 0" description="暂无生产工单" />
    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无生产工单'" stripe>
        <el-table-column prop="workOrderNo" label="工单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="产品" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.productMaterialCode }} {{ row.productMaterialName }}
          </template>
        </el-table-column>
        <el-table-column label="项目归属" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ projectText(row) }}</div>
            <span class="operation-muted">{{ ownershipText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.sourceSuggestionNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="BOM 版本" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.bomCode }} / {{ row.bomVersionCode }}
          </template>
        </el-table-column>
        <el-table-column label="计划数量" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProductionQuantity(row.plannedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已报工" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProductionQuantity(row.reportedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已入库" min-width="110" align="right">
          <template #default="{ row }">
            <span class="numeric-cell">{{ formatProductionQuantity(row.receivedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划日期" min-width="170">
          <template #default="{ row }">
            {{ row.plannedStartDate }} 至 {{ row.plannedFinishDate }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <ProductionWorkOrderStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ formatProductionDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button size="small" text data-test="view-production-work-order" @click="viewWorkOrder(row)">详情</el-button>
            <el-button v-if="canEditRecord(row)" size="small" text @click="editWorkOrder(row)">
              编辑
            </el-button>
            <el-dropdown trigger="click" class="table-actions-more" v-if="(canReleaseRecord(row)) || (canCreateIssueRecord(row)) || (canCreateReportRecord(row)) || (canCreateReceiptRecord(row)) || (canCompleteRecord(row)) || (canCancelRecord(row))">
              <el-button size="small" text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu class="table-actions-more-menu">
                  <el-dropdown-item
                    v-if="canReleaseRecord(row)"
                    class="table-actions-more-item table-actions-more-item--success"
                    data-test="release-production-work-order"
                    :disabled="actionLoading"
                    @click="runWorkOrderAction(row, 'release')"
                  >
                    发布
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="canCreateIssueRecord(row)"
                    class="table-actions-more-item"
                    data-test="create-production-material-issue"
                    @click="createMaterialIssue(row)"
                  >
                    领料
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="canCreateReportRecord(row)"
                    class="table-actions-more-item"
                    data-test="create-production-report"
                    @click="createReport(row)"
                  >
                    报工
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="canCreateReceiptRecord(row)"
                    class="table-actions-more-item"
                    data-test="create-production-completion-receipt"
                    @click="createCompletionReceipt(row)"
                  >
                    完工入库
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="canCompleteRecord(row)"
                    class="table-actions-more-item table-actions-more-item--success"
                    data-test="complete-production-work-order"
                    :disabled="actionLoading"
                    @click="runWorkOrderAction(row, 'complete')"
                  >
                    完成
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="canCancelRecord(row)"
                    class="table-actions-more-item table-actions-more-item--danger"
                    data-test="cancel-production-work-order"
                    :disabled="actionLoading"
                    @click="runWorkOrderAction(row, 'cancel')"
                  >
                    取消
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <span v-if="row.status === 'COMPLETED' || row.status === 'CANCELLED'" class="operation-muted">
              {{ workOrderStatusLabel(row.status) }}
            </span>
            <span v-else-if="row.actionDisabledReason" class="operation-muted">
              {{ row.actionDisabledReason }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage" @size-change="changePageSize"
    />
  </MasterDataTableView>
</template>

<style scoped>
.numeric-cell {
  display: inline-block;
  min-width: 76px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.operation-muted {
  color: var(--qherp-muted);
  font-size: 12px;
  margin-left: 8px;
  white-space: nowrap;
}
</style>
