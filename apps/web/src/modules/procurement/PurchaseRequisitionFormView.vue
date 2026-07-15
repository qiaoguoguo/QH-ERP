<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../shared/api/masterDataApi'
import {
  procurementApi,
  type ProcurementMode,
  type ProcurementRequisitionDetailRecord,
  type ProcurementRequisitionPayload,
  type ResourceId,
} from '../../shared/api/procurementApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import {
  normalizeOptionalId,
  normalizeRequiredId,
  procurementErrorMessage,
  procurementModeFrom,
  validateProcurementDecimal,
  validatePurchaseQuantity,
} from './procurementPageHelpers'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const loading = ref(false)
const formError = ref('')
const referenceError = ref('')
const projects = ref<SalesProjectSummary[]>([])
const materials = ref<MaterialRecord[]>([])
const suppliers = ref<PartnerRecord[]>([])
const editingRecord = ref<ProcurementRequisitionDetailRecord | null>(null)
const form = reactive({
  procurementMode: 'PROJECT' as ProcurementMode,
  projectId: '' as ResourceId | '',
  materialId: '' as ResourceId | '',
  suggestedSupplierId: '' as ResourceId | '',
  quantity: '',
  taxRate: '',
  requiredDate: '',
  purpose: '',
  remark: '',
})

const isEdit = computed(() => Boolean(route.params.id))
const pageTitle = computed(() => (isEdit.value ? '编辑采购请购' : '新建采购请购'))
const selectedProject = computed(() => projects.value.find((project) => String(project.id) === String(form.projectId)))
const selectedMaterial = computed(() => materials.value.find((material) => String(material.id) === String(form.materialId)))

async function loadReferences() {
  try {
    const [projectPage, materialPage, supplierPage] = await Promise.all([
      salesProjectApi.projects.list({ keyword: '', status: 'ACTIVE', page: 1, pageSize: 200 }),
      masterDataApi.materials.list({ keyword: '', status: 'ENABLED', sourceType: 'PURCHASED', page: 1, pageSize: 200 }),
      masterDataApi.suppliers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    ])
    projects.value = projectPage.items
    materials.value = materialPage.items
    suppliers.value = supplierPage.items
  } catch (caught) {
    referenceError.value = procurementErrorMessage(caught)
  }
}

async function loadRecord() {
  if (!isEdit.value) {
    return
  }
  loading.value = true
  formError.value = ''
  try {
    const detail = await procurementApi.requisitions.get(route.params.id as ResourceId)
    const firstLine = detail.lines[0]
    editingRecord.value = detail
    form.procurementMode = procurementModeFrom(detail) ?? procurementModeFrom(firstLine) ?? 'PROJECT'
    form.projectId = normalizeOptionalId(detail.projectId ?? firstLine?.projectId ?? '') ?? ''
    form.materialId = normalizeOptionalId(firstLine?.materialId ?? '') ?? ''
    form.suggestedSupplierId = normalizeOptionalId(firstLine?.suggestedSupplierId ?? '') ?? ''
    form.quantity = firstLine?.quantity ?? ''
    form.taxRate = firstLine?.taxRate ?? ''
    form.requiredDate = firstLine?.requiredDate ?? detail.requiredDate
    form.purpose = firstLine?.purpose ?? ''
    form.remark = detail.remark ?? ''
  } catch (caught) {
    formError.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function buildPayload(): ProcurementRequisitionPayload | null {
  const quantity = validatePurchaseQuantity(form.quantity)
  const taxRate = validateProcurementDecimal(form.taxRate, { label: '税率', allowZero: true })
  const materialId = normalizeRequiredId(form.materialId)
  const projectId = normalizeRequiredId(form.projectId)
  const suggestedSupplierId = normalizeOptionalId(form.suggestedSupplierId)
  if (!form.requiredDate.trim() || materialId === null || quantity.payloadValue === null || taxRate.payloadValue === null) {
    formError.value = quantity.message || taxRate.message || '请完整填写物料、数量、税率和需求日期'
    return null
  }
  if (form.procurementMode === 'PROJECT' && projectId === null) {
    formError.value = '项目专采必须选择项目'
    return null
  }
  if (!form.purpose.trim()) {
    formError.value = '用途说明不能为空'
    return null
  }

  return {
    procurementMode: form.procurementMode,
    projectId: form.procurementMode === 'PROJECT' ? projectId : null,
    title: selectedMaterial.value ? selectedMaterial.value.name : undefined,
    requiredDate: form.requiredDate,
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    lines: [{
      lineNo: 10,
      procurementMode: form.procurementMode,
      projectId: form.procurementMode === 'PROJECT' ? projectId : null,
      materialId,
      ...(selectedMaterial.value?.unitId ? { unitId: selectedMaterial.value.unitId } : {}),
      quantity: quantity.payloadValue,
      requiredDate: form.requiredDate,
      purpose: form.purpose.trim(),
      ...(suggestedSupplierId !== undefined ? { suggestedSupplierId } : {}),
      taxRate: taxRate.payloadValue,
    }],
  }
}

async function saveRequisition() {
  if (submitting.value) {
    return
  }
  const payload = buildPayload()
  if (!payload) {
    return
  }

  formError.value = ''
  submitting.value = true
  try {
    const result = isEdit.value && editingRecord.value
      ? await procurementApi.requisitions.update(editingRecord.value.id, { ...payload, version: editingRecord.value.version })
      : await procurementApi.requisitions.create(payload)
    await router.push({ name: 'procurement-requisition-detail', params: { id: String(result.id) } })
  } catch (caught) {
    formError.value = procurementErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

watch(() => form.procurementMode, (mode) => {
  if (mode === 'PUBLIC') {
    form.projectId = ''
  }
})

onMounted(async () => {
  await loadReferences()
  await loadRecord()
})
</script>

<template>
  <section class="procurement-form-page">
    <header class="page-header">
      <h1>{{ pageTitle }}</h1>
      <p>项目专采必须来自明确项目；公共采购显示为公共采购。</p>
    </header>

    <el-alert v-if="referenceError" class="page-alert" type="error" :title="referenceError" show-icon :closable="false" />
    <el-alert v-if="formError" class="page-alert" type="error" :title="formError" show-icon :closable="false" />
    <el-alert v-if="loading" class="page-alert" type="info" title="采购请购加载中" show-icon :closable="false" />

    <div class="form-grid">
      <label>
        采购模式
        <el-select v-model="form.procurementMode" data-test="requisition-procurement-mode" style="width: 100%">
          <el-option label="项目专采" value="PROJECT" />
          <el-option label="公共采购" value="PUBLIC" />
        </el-select>
      </label>
      <label>
        项目
        <el-select
          v-model="form.projectId"
          data-test="requisition-project-id"
          style="width: 100%"
          filterable
          clearable
          :disabled="form.procurementMode === 'PUBLIC'"
          placeholder="选择项目"
        >
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="`${project.projectNo} ${project.name}`"
            :value="project.id"
          />
        </el-select>
      </label>
      <label>
        项目显示
        <span class="readonly-field">{{ selectedProject ? `${selectedProject.projectNo} ${selectedProject.name}` : '公共采购' }}</span>
      </label>
      <label>
        物料
        <el-select v-model="form.materialId" data-test="requisition-material-id" style="width: 100%" filterable placeholder="选择物料">
          <el-option
            v-for="material in materials"
            :key="material.id"
            :label="`${material.code} ${material.name}`"
            :value="material.id"
          />
        </el-select>
      </label>
      <label>
        物料显示
        <span class="readonly-field">{{ selectedMaterial ? `${selectedMaterial.code} ${selectedMaterial.name}` : '未选择物料' }}</span>
      </label>
      <label>
        建议供应商
        <el-select v-model="form.suggestedSupplierId" data-test="requisition-supplier-id" style="width: 100%" filterable clearable placeholder="可选">
          <el-option
            v-for="supplier in suppliers"
            :key="supplier.id"
            :label="`${supplier.code} ${supplier.name}`"
            :value="supplier.id"
          />
        </el-select>
      </label>
      <label>
        数量
        <input v-model="form.quantity" name="requisition-quantity" autocomplete="off">
      </label>
      <label>
        税率
        <input v-model="form.taxRate" name="requisition-tax-rate" autocomplete="off">
      </label>
      <label>
        需求日期
        <input v-model="form.requiredDate" name="requisition-required-date" type="date">
      </label>
      <label class="full-row">
        用途说明
        <textarea v-model="form.purpose" name="requisition-purpose" rows="3" />
      </label>
      <label class="full-row">
        备注
        <textarea v-model="form.remark" name="requisition-remark" rows="2" />
      </label>
    </div>

    <div class="action-bar">
      <el-button data-test="save-requisition" type="primary" :loading="submitting" @click="saveRequisition">
        保存请购
      </el-button>
    </div>
  </section>
</template>

<style scoped>
.procurement-form-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.page-header p {
  color: #606266;
  margin: 0;
}

.form-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

input,
select,
textarea {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-sizing: border-box;
  min-height: 32px;
  padding: 6px 8px;
}

.readonly-field {
  align-items: center;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-sizing: border-box;
  display: flex;
  min-height: 32px;
  padding: 6px 8px;
}

.full-row {
  grid-column: 1 / -1;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
}
</style>
