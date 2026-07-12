<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type PartnerRecord } from '../../../shared/api/masterDataApi'
import {
  salesProjectApi,
  type ProjectOwnerCandidate,
  type SalesProjectDetail,
} from '../../../shared/api/salesProjectApi'
import type { ResourceId } from '../../../shared/api/salesApi'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems } from '../../system/shared/pageHelpers'
import SalesProjectStatusTag from './SalesProjectStatusTag.vue'
import { normalizeProjectOptionalId, projectApiErrorMessage } from './salesProjectPageHelpers'

const route = useRoute()
const router = useRouter()
const customers = ref<PartnerRecord[]>([])
const owners = ref<ProjectOwnerCandidate[]>([])
const editingRecord = ref<SalesProjectDetail | null>(null)
const referenceLoading = ref(true)
const loading = ref(false)
const referenceError = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const form = reactive({
  name: '',
  customerId: '' as ResourceId | '',
  ownerUserId: '' as ResourceId | '',
  plannedStartDate: '',
  plannedFinishDate: '',
  targetRevenue: '',
  targetCost: '',
  remark: '',
})

const isEdit = computed(() => Boolean(route.params.id))
const canEditForm = computed(() => !editingRecord.value || (
  editingRecord.value.status !== 'CLOSED' && editingRecord.value.status !== 'CANCELLED'
))
const pageTitle = computed(() => (isEdit.value ? '编辑销售项目' : '新建销售项目'))
const nameEditable = computed(() => !editingRecord.value || editingRecord.value.status === 'DRAFT')

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [customerPage, ownerPage] = await Promise.all([
      masterDataApi.customers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      salesProjectApi.ownerCandidates({ keyword: '', page: 1, pageSize: 200 }),
    ])
    customers.value = pageItems(customerPage)
    owners.value = pageItems(ownerPage)
  } catch (caught) {
    customers.value = []
    owners.value = []
    referenceError.value = projectApiErrorMessage(caught)
  } finally {
    referenceLoading.value = false
  }
}

async function loadRecord() {
  if (!route.params.id) {
    return
  }
  loading.value = true
  formError.value = ''
  try {
    const detail = await salesProjectApi.projects.get(route.params.id as ResourceId)
    editingRecord.value = detail
    form.name = detail.name
    form.customerId = detail.customerId
    form.ownerUserId = detail.ownerUserId
    form.plannedStartDate = detail.plannedStartDate ?? ''
    form.plannedFinishDate = detail.plannedFinishDate ?? ''
    form.targetRevenue = detail.targetRevenue
    form.targetCost = detail.targetCost
    form.remark = detail.remark ?? ''
  } catch (caught) {
    formError.value = projectApiErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateBase() {
  const customerId = normalizeProjectOptionalId(form.customerId)
  const ownerUserId = normalizeProjectOptionalId(form.ownerUserId)
  if (!form.name.trim() || customerId === undefined || ownerUserId === undefined) {
    formError.value = '请完整填写项目名称、客户和负责人'
    return null
  }
  if (form.plannedStartDate && form.plannedFinishDate && form.plannedFinishDate < form.plannedStartDate) {
    formError.value = '计划结束日期不得早于计划开始日期'
    return null
  }
  formError.value = ''
  return { customerId, ownerUserId }
}

async function saveProject() {
  if (formSubmitting.value || !canEditForm.value) {
    return
  }
  const ids = validateBase()
  if (!ids) {
    return
  }
  formSubmitting.value = true
  try {
    const common = {
      ...(form.plannedStartDate ? { plannedStartDate: form.plannedStartDate } : {}),
      ...(form.plannedFinishDate ? { plannedFinishDate: form.plannedFinishDate } : {}),
      ...(form.targetRevenue.trim() ? { targetRevenue: form.targetRevenue.trim() } : {}),
      ...(form.targetCost.trim() ? { targetCost: form.targetCost.trim() } : {}),
      ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    }
    const result = isEdit.value && editingRecord.value
      ? await salesProjectApi.projects.update(editingRecord.value.id, {
        version: editingRecord.value.version,
        ...(nameEditable.value ? { name: form.name.trim() } : {}),
        ownerUserId: ids.ownerUserId,
        ...common,
      })
      : await salesProjectApi.projects.create({
        name: form.name.trim(),
        customerId: ids.customerId,
        ownerUserId: ids.ownerUserId,
        ...common,
      })
    await router.push({ name: 'sales-project-detail', params: { id: String(result.id) } })
  } catch (caught) {
    formError.value = projectApiErrorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function cancel() {
  if (editingRecord.value) {
    void router.push({ name: 'sales-project-detail', params: { id: String(editingRecord.value.id) } })
    return
  }
  void router.push({ name: 'sales-projects' })
}

onMounted(async () => {
  await loadReferences()
  await loadRecord()
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="创建或编辑销售项目主档，客户创建后锁定，更新需携带版本。">
    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="销售项目表单加载中" :closable="false" />
      <el-alert v-if="editingRecord && !canEditForm" class="state-alert" type="warning" title="终态项目不可编辑" :closable="false" />
    </template>

    <el-form label-position="top" class="sales-project-form">
      <div v-if="editingRecord" class="edit-status">
        <span>{{ editingRecord.projectNo }}</span>
        <SalesProjectStatusTag :status="editingRecord.status" />
        <span>版本 {{ editingRecord.version }}</span>
      </div>
      <div class="sales-project-form-grid">
        <el-form-item label="项目名称">
          <el-input v-model="form.name" name="sales-project-name" placeholder="请输入项目名称" :disabled="!canEditForm || !nameEditable" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="form.customerId" filterable placeholder="请选择启用客户" :disabled="isEdit || !canEditForm">
            <el-option v-for="customer in customers" :key="customer.id" :label="`${customer.code} ${customer.name}`" :value="customer.id" />
          </el-select>
          <small v-if="isEdit" class="field-hint">客户创建后不可修改</small>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.ownerUserId" filterable placeholder="请选择负责人" :disabled="!canEditForm">
            <el-option v-for="owner in owners" :key="owner.userId" :label="`${owner.username} ${owner.displayName}`" :value="owner.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.plannedStartDate" name="sales-project-planned-start-date" placeholder="选择日期" :disabled="!canEditForm" />
        </el-form-item>
        <el-form-item label="计划结束日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.plannedFinishDate" name="sales-project-planned-finish-date" placeholder="选择日期" :disabled="!canEditForm" />
        </el-form-item>
        <el-form-item label="目标收入">
          <el-input v-model="form.targetRevenue" name="sales-project-target-revenue" placeholder="0.00" :disabled="!canEditForm" />
        </el-form-item>
        <el-form-item label="目标成本">
          <el-input v-model="form.targetCost" name="sales-project-target-cost" placeholder="0.00" :disabled="!canEditForm" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="sales-project-remark" placeholder="可选" :disabled="!canEditForm" />
        </el-form-item>
      </div>
    </el-form>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button data-test="save-sales-project" type="primary" :loading="formSubmitting" :disabled="formSubmitting || !canEditForm" @click="saveProject">
        保存
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.sales-project-form {
  padding: 14px;
}

.sales-project-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.edit-status {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.field-hint {
  color: var(--qherp-muted);
  display: block;
  margin-top: 4px;
}

.form-footer {
  border-top: 1px solid var(--qherp-border);
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 12px 14px 14px;
}

@media (max-width: 760px) {
  .sales-project-form-grid {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
