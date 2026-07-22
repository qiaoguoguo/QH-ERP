<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  projectCostApi,
  type ProjectCostAdjustmentRecord,
  type ProjectCostAdjustmentStatus,
  type ResourceId,
} from '../../../shared/api/projectCostApi'
import { currentRouteReturnTo, queryWithReturnTo } from '../../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../../stores/authStore'
import { confirmAction } from '../../../shared/ui/confirmDialog'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
import {
  formatProjectCostAmount,
  projectCostActionDisabledReason,
  projectCostAdjustmentStatusLabel,
  projectCostAdjustmentTypeLabel,
  projectCostAllowed,
  projectCostErrorMessage,
  restrictedMoneyReason,
} from './projectCostPageHelpers'
import './ProjectCostShared.css'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const filters = reactive<{ keyword: string; status?: ProjectCostAdjustmentStatus; businessDateFrom: string; businessDateTo: string }>({
  keyword: '',
  status: undefined,
  businessDateFrom: '',
  businessDateTo: '',
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<ProjectCostAdjustmentRecord[]>([])
const loading = ref(true)
const error = ref('')
const actionLoadingId = ref<string | null>(null)

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await projectCostApi.adjustments.list({
      keyword: filters.keyword,
      status: filters.status,
      businessDateFrom: filters.businessDateFrom,
      businessDateTo: filters.businessDateTo,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = pageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = projectCostErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function createAdjustment() {
  void router.push({ name: 'cost-project-cost-adjustment-create' })
}

function viewAdjustment(record: ProjectCostAdjustmentRecord) {
  void router.push({
    name: 'cost-project-cost-adjustment-detail',
    params: { id: String(record.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editAdjustment(record: ProjectCostAdjustmentRecord) {
  void router.push({ name: 'cost-project-cost-adjustment-edit', params: { id: String(record.id) } })
}

async function runAction(record: ProjectCostAdjustmentRecord, action: 'SUBMIT' | 'CANCEL') {
  if (actionLoadingId.value || !projectCostAllowed(record, action)) {
    return
  }
  const label = action === 'SUBMIT' ? '提交' : '取消'
  const confirmOptions = action === 'SUBMIT'
    ? { title: '提交成本调整', type: 'warning' as const, risk: 'project-cost-adjustment-submit' }
    : { title: '取消成本调整', type: 'warning' as const, risk: 'project-cost-adjustment-cancel' }
  if (!(await confirmAction(`${label}成本调整/分配“${record.adjustmentNo}”？`, confirmOptions))) {
    return
  }
  actionLoadingId.value = String(record.id)
  error.value = ''
  try {
    const payload = {
      version: record.version,
      sourceFingerprint: `adjustment-${record.id}-v${record.version}`,
      idempotencyKey: createIdempotencyKey(`project-cost-adjustment-${action.toLowerCase()}`),
    }
    if (action === 'SUBMIT') {
      await projectCostApi.adjustments.submit(normalizeId(record.id), payload)
    } else {
      await projectCostApi.adjustments.cancel(normalizeId(record.id), payload)
    }
    await loadRecords()
  } catch (caught) {
    error.value = projectCostErrorMessage(caught)
  } finally {
    actionLoadingId.value = null
  }
}

function normalizeId(value: ResourceId): ResourceId {
  return value
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.status = undefined
  filters.businessDateFrom = ''
  filters.businessDateTo = ''
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

function canEdit(record: ProjectCostAdjustmentRecord) {
  return authStore.hasPermission('cost:project-cost-adjustment:update') && projectCostAllowed(record, 'UPDATE')
}

function canSubmit(record: ProjectCostAdjustmentRecord) {
  return authStore.hasPermission('cost:project-cost-adjustment:submit') && projectCostAllowed(record, 'SUBMIT')
}

function canCancel(record: ProjectCostAdjustmentRecord) {
  return authStore.hasPermission('cost:project-cost-adjustment:cancel') && projectCostAllowed(record, 'CANCEL')
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="成本调整/分配" description="维护项目成本调整、公共制造费用分配和审批流转状态。">
    <template #actions>
      <el-button v-if="authStore.hasPermission('cost:project-cost-adjustment:create')" type="primary" @click="createAdjustment">新增调整/分配</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="调整号、项目、来源" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已拒绝" value="REJECTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期起"><el-date-picker v-model="filters.businessDateFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="起始日期" /></el-form-item>
        <el-form-item label="业务日期止"><el-date-picker v-model="filters.businessDateTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="截止日期" /></el-form-item>
        <el-form-item label="操作">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="成本调整/分配加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无成本调整/分配'" stripe>
        <el-table-column prop="adjustmentNo" label="调整编号" min-width="170" show-overflow-tooltip />
        <el-table-column label="类型" min-width="150"><template #default="{ row }">{{ projectCostAdjustmentTypeLabel(row.adjustmentType) }}</template></el-table-column>
        <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ projectCostAdjustmentStatusLabel(row.status) }}</template></el-table-column>
        <el-table-column prop="businessDate" label="业务日期" min-width="110" />
        <el-table-column label="金额" min-width="140" align="right"><template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.totalAmount, restrictedMoneyReason(row) || undefined) }}</span></template></el-table-column>
        <el-table-column prop="reason" label="原因" min-width="190" show-overflow-tooltip />
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button size="small" text @click="viewAdjustment(row)">详情</el-button>
            <el-button v-if="canEdit(row)" size="small" text @click="editAdjustment(row)">编辑</el-button>
            <el-dropdown trigger="click" class="table-actions-more" v-if="(canSubmit(row)) || (canCancel(row))">
              <el-button size="small" text>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu class="table-actions-more-menu">
                  <el-button v-if="canSubmit(row)" size="small" text type="primary" data-test="submit-project-cost-adjustment" :title="projectCostActionDisabledReason(row, 'SUBMIT')" @click="runAction(row, 'SUBMIT')">提交</el-button>
                  <el-button v-if="canCancel(row)" size="small" text type="danger" data-test="cancel-project-cost-adjustment" :title="projectCostActionDisabledReason(row, 'CANCEL')" @click="runAction(row, 'CANCEL')">取消</el-button>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
