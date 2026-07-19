<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  projectCostApi,
  type ProjectCostAdjustmentRecord,
  type ProjectCostPublicExpenseCandidate,
  type ResourceId,
} from '../../../shared/api/projectCostApi'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems, pageTotal } from '../../system/shared/pageHelpers'
import {
  formatProjectCostAmount,
  projectCostErrorMessage,
  restrictedMoneyReason,
} from './projectCostPageHelpers'
import './ProjectCostShared.css'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => route.name === 'cost-project-cost-adjustment-edit')
const detail = ref<ProjectCostAdjustmentRecord | null>(null)
const candidates = ref<ProjectCostPublicExpenseCandidate[]>([])
const selectedCandidate = ref<ProjectCostPublicExpenseCandidate | null>(null)
const loading = ref(true)
const candidateLoading = ref(false)
const submitting = ref(false)
const error = ref('')
const candidatePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const candidateFilters = reactive({ keyword: '', businessDateFrom: '', businessDateTo: '' })
const form = reactive({
  projectId: String(route.query.projectId ?? ''),
  businessDate: '',
  amount: '',
  reason: '',
  remark: '',
})

const saveDisabledReason = computed(() => {
  if (!form.projectId.trim()) {
    return '请填写目标项目'
  }
  if (!form.businessDate) {
    return '请选择业务日期'
  }
  if (!selectedCandidate.value && !isEdit.value) {
    return '请选择公共费用候选'
  }
  if (!/^\d+(\.\d{1,6})?$/.test(form.amount.trim())) {
    return '请填写分配金额，最多六位小数'
  }
  return ''
})

function fillFromDetail(record: ProjectCostAdjustmentRecord) {
  const line = record.lines[0]
  form.projectId = line?.projectId === undefined ? '' : String(line.projectId)
  form.businessDate = record.businessDate
  form.amount = line?.amount ?? ''
  form.reason = record.reason ?? ''
  form.remark = line?.remark ?? ''
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
    await loadCandidates()
  } catch (caught) {
    error.value = projectCostErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function selectCandidate(candidate: ProjectCostPublicExpenseCandidate) {
  selectedCandidate.value = candidate
  if (!form.businessDate && candidate.businessDate) {
    form.businessDate = candidate.businessDate
  }
  if (!form.amount && candidate.remainingAmount) {
    form.amount = candidate.remainingAmount
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
  const raw = form.projectId.trim()
  const numeric = Number(raw)
  return Number.isFinite(numeric) && raw !== '' ? numeric : raw
}

async function save() {
  if (submitting.value || saveDisabledReason.value) {
    return
  }
  submitting.value = true
  error.value = ''
  const candidateKey = selectedCandidate.value?.expenseLineId ?? detail.value?.lines[0]?.sourceExpenseLineId ?? 'manual'
  const payload = {
    adjustmentType: 'PUBLIC_EXPENSE_ALLOCATION' as const,
    businessDate: form.businessDate,
    reason: form.reason || '公共制造费用分配',
    version: detail.value?.version ?? 0,
    sourceFingerprint: `candidate-${candidateKey}-${form.amount.trim()}`,
    idempotencyKey: createIdempotencyKey('project-cost-adjustment-save'),
    lines: [{
      projectId: normalizedProjectId(),
      category: 'MANUFACTURING_OVERHEAD' as const,
      stage: 'DIRECT_PROJECT' as const,
      direction: 'INCREASE' as const,
      amount: form.amount.trim(),
      sourceExpenseLineId: selectedCandidate.value?.expenseLineId ?? detail.value?.lines[0]?.sourceExpenseLineId ?? null,
      remark: form.remark || '公共费用分配',
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
      <div><span>目标项目</span><strong>{{ form.projectId || '待填写' }}</strong></div>
      <div><span>剩余可分配金额</span><strong>{{ formatProjectCostAmount(selectedCandidate?.remainingAmount, restrictedMoneyReason(selectedCandidate) || undefined) }}</strong></div>
      <div><span>本次分配金额</span><strong>{{ formatProjectCostAmount(form.amount || null) }}</strong></div>
      <div><span>高风险说明</span><strong>确认前请核对公共费用来源和项目归属</strong></div>
    </section>

    <el-form label-position="top" class="project-cost-form">
      <div class="project-cost-form-grid">
        <el-form-item label="目标项目">
          <el-input v-model="form.projectId" name="project-cost-adjustment-project-id" placeholder="填写项目 ID 或编号" />
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker v-model="form.businessDate" value-on-clear="" name="project-cost-adjustment-date" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="业务日期" />
        </el-form-item>
        <el-form-item label="分配金额">
          <el-input v-model="form.amount" name="project-cost-adjustment-amount" placeholder="0.000000" />
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
              <template #default="{ row }"><span class="numeric-cell">{{ formatProjectCostAmount(row.remainingAmount, restrictedMoneyReason(row) || undefined) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" min-width="90">
              <template #default="{ row }">
                <el-button size="small" text type="primary" data-test="select-public-expense-candidate" @click="selectCandidate(row)">选择</el-button>
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
