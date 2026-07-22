<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../shared/api/documentPlatformApi'
import { procurementApi, type ProcurementRequisitionSummaryRecord } from '../../shared/api/procurementApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatProcurementQuantity,
  normalizeOptionalId,
  procurementErrorMessage,
  procurementOwnershipDisplay,
  procurementApprovalStatusLabel,
  procurementRequisitionStatusLabel,
  procurementRequisitionStatusTagType,
} from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'
import { useAuthStore } from '../../stores/authStore'
import { currentRouteReturnTo, queryWithReturnTo } from '../../shared/navigation/navigationReturn'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import BusinessReferenceSelect from '../system/shared/BusinessReferenceSelect.vue'
import type { BusinessReferenceOption } from '../system/shared/businessReferenceSelectTypes'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const records = ref<ProcurementRequisitionSummaryRecord[]>([])
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const filters = reactive({
  keyword: '',
  procurementMode: undefined as 'PUBLIC' | 'PROJECT' | undefined,
  projectId: '' as string | number | '',
  status: undefined as ProcurementRequisitionSummaryRecord['status'] | undefined,
  approvalStatus: undefined as string | undefined,
  requiredDateFrom: '',
  requiredDateTo: '',
  page: 1,
  pageSize: 10,
})
const total = ref(0)

const canExport = computed(() => (
  authStore.hasPermission('procurement:requisition:view')
  && authStore.hasPermission('platform:document-task:create')
  && authStore.hasPermission('procurement:document:export')
))

function allowed(record: ProcurementRequisitionSummaryRecord, action: string): boolean {
  return (record.allowedActions ?? []).includes(action)
}

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
    const page = await procurementApi.requisitions.list({
      keyword: filters.keyword,
      procurementMode: filters.procurementMode,
      projectId: normalizeOptionalId(filters.projectId),
      status: filters.status,
      approvalStatus: filters.approvalStatus,
      requiredDateFrom: filters.requiredDateFrom,
      requiredDateTo: filters.requiredDateTo,
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

function viewRecord(record: ProcurementRequisitionSummaryRecord) {
  void router.push({ name: 'procurement-requisition-detail', params: { id: String(record.id) } })
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
  filters.approvalStatus = undefined
  filters.requiredDateFrom = ''
  filters.requiredDateTo = ''
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

function createInquiryFromRecord(record: ProcurementRequisitionSummaryRecord) {
  void router.push({
    name: 'procurement-inquiry-create',
    query: queryWithReturnTo({ requisitionId: String(record.id) }, currentRouteReturnTo(route)),
  })
}

function createOrderFromRecord(record: ProcurementRequisitionSummaryRecord) {
  void router.push({
    name: 'procurement-order-create',
    query: queryWithReturnTo({ requisitionId: String(record.id) }, currentRouteReturnTo(route)),
  })
}

async function closeRecord(record: ProcurementRequisitionSummaryRecord) {
  if (actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.requisitions.close(record.id, {
      version: record.version,
      reason: '请购结案',
      idempotencyKey: createIdempotencyKey('requisition-close'),
    })
    await loadRecords()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadRecords()
  } finally {
    actionLoading.value = false
  }
}

async function exportRequisitions() {
  if (actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createProcurementRequisitions({
      keyword: filters.keyword,
      procurementMode: filters.procurementMode,
      projectId: normalizeOptionalId(filters.projectId),
      status: filters.status,
      approvalStatus: filters.approvalStatus,
      requiredDateFrom: filters.requiredDateFrom,
      requiredDateTo: filters.requiredDateTo,
      idempotencyKey: createIdempotencyKey('procurement-requisition-export'),
    })
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="采购请购" description="项目专采与公共采购需求入口，审批状态和业务状态分开展示。">
    <template #actions>
      <el-button data-test="create-requisition" type="primary" @click="router.push({ name: 'procurement-requisition-create' })">
        新建请购
      </el-button>
      <el-button v-if="canExport" data-test="export-requisitions" :loading="actionLoading" @click="exportRequisitions">
        当前筛选导出
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="请购号、标题、物料" />
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
            data-test="requisition-project-filter"
            placeholder="项目编号或名称"
            :load-options="loadProjectOptions"
          />
        </el-form-item>
        <el-form-item label="业务状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="SUBMITTED" />
            <el-option label="已批准" value="APPROVED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select v-model="filters.approvalStatus" clearable placeholder="全部审批">
            <el-option label="未提交" value="NOT_SUBMITTED" />
            <el-option label="审批中" value="SUBMITTED" />
            <el-option label="审批通过" value="APPROVED" />
            <el-option label="审批驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求起始">
          <el-date-picker v-model="filters.requiredDateFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" />
        </el-form-item>
        <el-form-item label="需求截止">
          <el-date-picker v-model="filters.requiredDateTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" />
        </el-form-item>
        <el-form-item label="操作">
          <el-button data-test="search-requisitions" type="primary" @click="searchRecords">查询</el-button>
          <el-button data-test="reset-requisitions" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <ProcurementDocumentTaskPanel :task="latestDocumentTask" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无采购请购'" stripe v-loading="loading">
        <el-table-column prop="requisitionNo" label="请购号" min-width="150" show-overflow-tooltip />
        <el-table-column label="采购模式/项目" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ procurementOwnershipDisplay(row) }}</template>
        </el-table-column>
        <el-table-column label="物料摘要" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialSummary || '物料摘要未返回' }}</template>
        </el-table-column>
        <el-table-column label="业务/审批状态" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div>
              <span>业务状态：</span>
              <el-tag
                data-test="requisition-row-business-status"
                size="small"
                :type="procurementRequisitionStatusTagType(row.status)"
              >
                {{ procurementRequisitionStatusLabel(row.status, row.statusName) }}
              </el-tag>
            </div>
            <div>审批状态：{{ procurementApprovalStatusLabel(row.approvalStatus, row.approvalStatusName) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="requiredDate" label="需求日期" min-width="110" />
        <el-table-column label="进度" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">
            计划/已转/剩余：{{ formatProcurementQuantity(row.totalQuantity) }}/{{
              formatProcurementQuantity(row.orderedQuantity)
            }}/{{ formatProcurementQuantity(row.remainingQuantity) }}
          </template>
        </el-table-column>
        <el-table-column label="结案原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.closeReason || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button text type="primary" @click="viewRecord(row)">详情</el-button>
            <el-button
            v-if="allowed(row, 'CREATE_INQUIRY')"
            data-test="create-inquiry-from-requisition-list"
            text
            type="primary"
            @click="createInquiryFromRecord(row)"
          >
            创建询价
          </el-button>
            <el-dropdown trigger="click" class="table-actions-more" v-if="(allowed(row, 'CREATE_ORDER')) || (allowed(row, 'CLOSE'))">
              <el-button size="small" text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu class="table-actions-more-menu">
                  <el-button
                    v-if="allowed(row, 'CREATE_ORDER')"
                    data-test="create-order-from-requisition-list"
                    text
                    type="primary"
                    @click="createOrderFromRecord(row)"
                  >
                    转采购订单
                  </el-button>
                  <el-button
                    v-if="allowed(row, 'CLOSE')"
                    data-test="close-requisition-list"
                    text
                    type="warning"
                    :disabled="actionLoading"
                    @click="closeRecord(row)"
                  >
                    结案
                  </el-button>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
