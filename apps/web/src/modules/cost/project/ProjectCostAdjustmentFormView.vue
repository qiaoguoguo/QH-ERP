<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  projectCostApi,
  type ProjectCostAdjustmentRecord,
  type ProjectCostPublicExpenseCandidate,
  type ProjectCostWorkbenchRecord,
  type ResourceId,
} from '../../../shared/api/projectCostApi'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
import {
  formatProjectCostAmount,
  projectCostMessages,
  projectCostErrorMessage,
  restrictedMoneyReason,
  restrictedSourceReason,
} from './projectCostPageHelpers'
import './ProjectCostShared.css'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => route.name === 'cost-project-cost-adjustment-edit')
const detail = ref<ProjectCostAdjustmentRecord | null>(null)
const projectCandidates = ref<ProjectCostWorkbenchRecord[]>([])
const candidates = ref<ProjectCostPublicExpenseCandidate[]>([])
const selectedProject = ref<ProjectCostWorkbenchRecord | null>(null)
const selectedCandidate = ref<ProjectCostPublicExpenseCandidate | null>(null)
const loading = ref(true)
const projectCandidateLoading = ref(false)
const candidateLoading = ref(false)
const submitting = ref(false)
const error = ref('')
const projectCandidatePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const candidatePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const projectCandidateFilters = reactive({ keyword: '' })
const candidateFilters = reactive({ keyword: '', businessDateFrom: '', businessDateTo: '' })
const form = reactive({
  projectId: String(route.query.projectId ?? ''),
  businessDate: '',
  amount: '',
  reason: '',
  remark: '',
})

function candidateRestrictedReason(candidate: ProjectCostPublicExpenseCandidate | null | undefined): string {
  if (!candidate) {
    return ''
  }
  if (candidate.expenseLineId === null || candidate.expenseLineId === undefined || candidate.expenseLineId === '') {
    return restrictedSourceReason(candidate) || projectCostMessages.sourceRestricted
  }
  return restrictedSourceReason(candidate) || restrictedMoneyReason(candidate) || ''
}

const adjustmentAmountRestrictedReason = computed(() => {
  if (detail.value?.amountVisible === false) {
    return restrictedMoneyReason(detail.value) || projectCostMessages.amountForbidden
  }
  if (detail.value?.lines[0]?.amount === null) {
    return projectCostMessages.amountForbidden
  }
  return candidateRestrictedReason(selectedCandidate.value)
})

const selectedPublicExpenseLineId = computed(() =>
  selectedCandidate.value?.expenseLineId ?? detail.value?.lines[0]?.publicExpenseLineId ?? null,
)

const fieldErrors = computed(() => ({
  projectId: form.projectId.trim() ? '' : '请填写目标项目',
  businessDate: form.businessDate ? '' : '请选择业务日期',
  amount: adjustmentAmountRestrictedReason.value || (/^\d+(\.\d{1,6})?$/.test(form.amount.trim()) ? '' : '请填写分配金额，最多六位小数'),
  publicExpense: selectedPublicExpenseLineId.value ? '' : '请选择公共费用候选',
}))

const saveDisabledReason = computed(() => {
  return fieldErrors.value.projectId
    || fieldErrors.value.businessDate
    || fieldErrors.value.publicExpense
    || fieldErrors.value.amount
})

function fillFromDetail(record: ProjectCostAdjustmentRecord) {
  const line = record.lines[0]
  form.projectId = line?.projectId === undefined ? '' : String(line.projectId)
  form.businessDate = record.businessDate
  form.amount = line?.amount ?? ''
  form.reason = record.reason ?? ''
  form.remark = line?.reason ?? ''
}

async function loadProjectCandidates() {
  projectCandidateLoading.value = true
  try {
    const page = await projectCostApi.projectCosts.list({
      keyword: projectCandidateFilters.keyword,
      page: projectCandidatePagination.page,
      pageSize: projectCandidatePagination.pageSize,
    })
    projectCandidates.value = pageItems(page)
    projectCandidatePagination.total = pageTotal(page)
  } catch (caught) {
    error.value = projectCostErrorMessage(caught)
  } finally {
    projectCandidateLoading.value = false
  }
}

async function loadCandidates() {
  candidateLoading.value = true
  try {
    const page = await projectCostApi.adjustments.publicExpenseCandidates({
      keyword: candidateFilters.keyword,
      businessDateFrom: candidateFilters.businessDateFrom,
      businessDateTo: candidateFilters.businessDateTo,
      page: candidatePagination.page,
      pageSize: candidatePagination.pageSize,
    })
    candidates.value = pageItems(page)
    candidatePagination.total = pageTotal(page)
  } catch (caught) {
    error.value = projectCostErrorMessage(caught)
  } finally {
    candidateLoading.value = false
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    if (isEdit.value) {
      detail.value = await projectCostApi.adjustments.get(route.params.id as string)
      fillFromDetail(detail.value)
    } else {
      form.businessDate = new Date().toISOString().slice(0, 10)
    }
    await Promise.all([loadProjectCandidates(), loadCandidates()])
  } catch (caught) {
    error.value = projectCostErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function selectProject(candidate: ProjectCostWorkbenchRecord) {
  selectedProject.value = candidate
  form.projectId = String(candidate.projectId)
}

function searchProjectCandidates() {
  projectCandidatePagination.page = 1
  selectedProject.value = null
  void loadProjectCandidates()
}

function changeProjectCandidatePage(page: number) {
  projectCandidatePagination.page = page
  void loadProjectCandidates()
}

function selectCandidate(candidate: ProjectCostPublicExpenseCandidate) {
  const restrictedReason = candidateRestrictedReason(candidate)
  if (restrictedReason) {
    selectedCandidate.value = null
    error.value = restrictedReason
    return
  }
  selectedCandidate.value = candidate
  if (!form.businessDate && candidate.businessDate) {
    form.businessDate = candidate.businessDate
  }
  if (!form.amount && candidate.availableAmount) {
    form.amount = candidate.availableAmount
  }
}

function searchCandidates() {
  candidatePagination.page = 1
  selectedCandidate.value = null
  void loadCandidates()
}

function changeCandidatePage(page: number) {
  candidatePagination.page = page
  void loadCandidates()
}

function normalizedProjectId(): ResourceId {
  return form.projectId.trim()
}

async function save() {
  if (submitting.value || saveDisabledReason.value) {
    return
  }
  submitting.value = true
  error.value = ''
  const publicExpenseLineId = selectedPublicExpenseLineId.value
  if (!publicExpenseLineId) {
    error.value = projectCostMessages.sourceRestricted
    submitting.value = false
    return
  }
  const candidateKey = publicExpenseLineId
  const payload = {
    adjustmentType: 'PUBLIC_EXPENSE_ALLOCATION' as const,
    businessDate: form.businessDate,
    reason: form.reason || '公共制造费用分配',
    version: detail.value?.version ?? 0,
    sourceFingerprint: `candidate-${candidateKey}-${form.amount.trim()}`,
    idempotencyKey: createIdempotencyKey('project-cost-adjustment-save'),
    lines: [{
      projectId: normalizedProjectId(),
      costCategory: 'MANUFACTURING_OVERHEAD' as const,
      costStage: 'DIRECT_PROJECT' as const,
      direction: 'INCREASE' as const,
      amount: form.amount.trim(),
      publicExpenseLineId,
      reason: form.remark || '公共费用分配',
    }],
  }
  try {
    const result = detail.value
      ? await projectCostApi.adjustments.update(route.params.id as string, payload)
      : await projectCostApi.adjustments.create(payload)
    await router.push({ name: 'cost-project-cost-adjustment-detail', params: { id: String(result.id) } })
  } catch (caught) {
    error.value = projectCostErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <MasterDataTableView :title="isEdit ? '编辑成本调整/分配' : '新增成本调整/分配'" description="选择目标项目和公共费用候选，提交项目制造费用分配草稿。">
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="成本调整/分配表单加载中" :closable="false" />
      <el-alert v-if="saveDisabledReason" class="state-alert" type="warning" :title="saveDisabledReason" :closable="false" />
    </template>

    <section class="project-cost-summary-strip">
      <div><span>目标项目</span><strong>{{ selectedProject ? `${selectedProject.projectNo} ${selectedProject.projectName}` : (form.projectId || '待填写') }}</strong></div>
      <div><span>剩余可分配金额</span><strong>{{ formatProjectCostAmount(selectedCandidate?.availableAmount, restrictedMoneyReason(selectedCandidate) || undefined) }}</strong></div>
      <div><span>本次分配金额</span><strong>{{ formatProjectCostAmount(form.amount || null, adjustmentAmountRestrictedReason || undefined) }}</strong></div>
      <div><span>高风险说明</span><strong>确认前请核对公共费用来源和项目归属</strong></div>
    </section>

    <el-form label-position="top" class="project-cost-form">
      <div class="project-cost-form-grid">
        <el-form-item label="目标项目" :error="fieldErrors.projectId || undefined">
          <el-input v-model="form.projectId" name="project-cost-adjustment-project-id" placeholder="填写项目 ID 或编号" />
        </el-form-item>
        <el-form-item label="业务日期" :error="fieldErrors.businessDate || undefined">
          <el-date-picker v-model="form.businessDate" value-on-clear="" name="project-cost-adjustment-date" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="业务日期" />
        </el-form-item>
        <el-form-item label="分配金额" :error="fieldErrors.amount || undefined">
          <el-input v-model="form.amount" name="project-cost-adjustment-amount" placeholder="0.000000" :disabled="Boolean(adjustmentAmountRestrictedReason)" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="form.reason" placeholder="公共制造费用分配" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="调整说明" />
        </el-form-item>
      </div>
    </el-form>

    <div class="project-cost-section-grid">
      <section class="project-cost-section">
        <div class="project-cost-section-heading">
          <span class="project-cost-section-title">项目候选</span>
          <div class="project-cost-inline-actions">
            <el-input v-model="projectCandidateFilters.keyword" placeholder="项目编号、名称、客户" />
            <el-button @click="searchProjectCandidates">查询项目</el-button>
          </div>
        </div>
        <div class="table-scroll">
          <el-table :data="projectCandidates" :empty-text="projectCandidateLoading ? '加载中' : '暂无项目候选'" stripe>
            <el-table-column prop="projectNo" label="项目编号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="ownerDisplayName" label="负责人" min-width="120" show-overflow-tooltip />
            <el-table-column prop="projectStatus" label="状态" min-width="100" />
            <el-table-column label="操作" fixed="right" min-width="90">
              <template #default="{ row }">
                <el-button size="small" text type="primary" data-test="select-project-cost-project-candidate" @click="selectProject(row)">选择</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-pagination
          class="table-pagination"
          layout="total, prev, pager, next"
          :total="projectCandidatePagination.total"
          :page-size="projectCandidatePagination.pageSize"
          :current-page="projectCandidatePagination.page"
          @current-change="changeProjectCandidatePage"
        />
      </section>

      <section class="project-cost-section">
        <div class="project-cost-section-heading">
          <span class="project-cost-section-title">公共费用候选</span>
          <div class="project-cost-inline-actions">
            <el-input v-model="candidateFilters.keyword" placeholder="费用号、供应商" />
            <el-button @click="searchCandidates">查询候选</el-button>
          </div>
        </div>
        <div class="table-scroll">
          <el-table :data="candidates" :empty-text="candidateLoading ? '加载中' : '暂无公共费用候选'" stripe>
            <el-table-column prop="expenseNo" label="费用单" min-width="150" show-overflow-tooltip />
            <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
            <el-table-column prop="categoryName" label="费用分类" min-width="120" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column label="剩余可分配金额" min-width="150" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.availableAmount, candidateRestrictedReason(row) || restrictedMoneyReason(row) || undefined) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" min-width="90">
              <template #default="{ row }">
                <el-button size="small" text :type="candidateRestrictedReason(row) ? 'info' : 'primary'" :disabled="Boolean(candidateRestrictedReason(row))" data-test="select-public-expense-candidate" @click="selectCandidate(row)">{{ candidateRestrictedReason(row) ? '受限' : '选择' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-pagination
          class="table-pagination"
          layout="total, prev, pager, next"
          :total="candidatePagination.total"
          :page-size="candidatePagination.pageSize"
          :current-page="candidatePagination.page"
          @current-change="changeCandidatePage"
        />
      </section>
    </div>

    <div class="project-cost-form-footer">
      <span class="project-cost-danger-note">提交审批前请确认分配金额不得超过公共费用剩余额度。</span>
      <el-button @click="router.push({ name: 'cost-project-cost-adjustments' })">取消</el-button>
      <el-button data-test="save-project-cost-adjustment" type="primary" :loading="submitting" :disabled="Boolean(saveDisabledReason)" @click="save">保存草稿</el-button>
    </div>
  </MasterDataTableView>
</template>
