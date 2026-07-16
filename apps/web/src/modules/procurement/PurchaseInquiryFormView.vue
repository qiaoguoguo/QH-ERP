<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../shared/api/masterDataApi'
import {
  procurementApi,
  type ProcurementInquiryDetailRecord,
  type ProcurementInquiryPayload,
  type ProcurementMode,
  type ProcurementRequisitionDetailRecord,
  type ResourceId,
} from '../../shared/api/procurementApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import {
  normalizeOptionalId,
  normalizeRequiredId,
  procurementErrorMessage,
  procurementModeFrom,
  procurementModeDisplay,
  validatePurchaseQuantity,
} from './procurementPageHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const loading = ref(false)
const formError = ref('')
const referenceError = ref('')
const editLoadFailed = ref(false)
const projects = ref<SalesProjectSummary[]>([])
const materials = ref<MaterialRecord[]>([])
const suppliers = ref<PartnerRecord[]>([])
const editingRecord = ref<ProcurementInquiryDetailRecord | null>(null)
const requisitionSourceLabel = ref('')
const form = reactive({
  procurementMode: 'PROJECT' as ProcurementMode,
  projectId: '' as ResourceId | '',
  materialId: '' as ResourceId | '',
  requisitionLineId: null as ResourceId | null,
  supplierIds: [] as ResourceId[],
  quantity: '',
  remark: '',
})

const isEdit = computed(() => Boolean(route.params.id))
const pageTitle = computed(() => (isEdit.value ? '编辑询价' : '新建询价'))
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
    const detail = await procurementApi.inquiries.get(route.params.id as ResourceId)
    const firstLine = detail.lines[0]
    editingRecord.value = detail
    form.procurementMode = procurementModeFrom(detail) ?? procurementModeFrom(firstLine) ?? 'PROJECT'
    form.projectId = normalizeOptionalId(detail.projectId ?? firstLine?.projectId ?? '') ?? ''
    form.materialId = normalizeOptionalId(firstLine?.materialId ?? '') ?? ''
    form.supplierIds = Array.from(new Set((detail.quotes ?? []).map((quote) => quote.supplierId)))
    form.quantity = firstLine?.quantity ?? ''
    form.remark = detail.remark ?? ''
  } catch (caught) {
    editLoadFailed.value = true
    formError.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function lineMaterialLabel(line: { materialCode?: string | null; materialName?: string | null }) {
  return [line.materialCode, line.materialName].filter(Boolean).join(' ')
}

async function loadRequisitionSource() {
  if (isEdit.value || !route.query.requisitionId) {
    return
  }
  const requisition = await procurementApi.requisitions.get(route.query.requisitionId as ResourceId) as ProcurementRequisitionDetailRecord
  if (requisition.status !== 'APPROVED' && requisition.status !== 'PARTIALLY_ORDERED') {
    formError.value = '仅审批通过且可询价的请购可创建询价'
    return
  }
  const firstLine = requisition.lines[0]
  if (!firstLine) {
    formError.value = '请购没有可询价明细'
    return
  }
  form.procurementMode = procurementModeFrom(firstLine) ?? procurementModeFrom(requisition) ?? 'PROJECT'
  form.projectId = form.procurementMode === 'PROJECT'
    ? normalizeOptionalId(firstLine.projectId ?? requisition.projectId ?? '') ?? ''
    : ''
  form.materialId = firstLine.materialId
  form.requisitionLineId = firstLine.id
  form.quantity = firstLine.remainingQuantity || firstLine.quantity
  form.supplierIds = firstLine.suggestedSupplierId ? [firstLine.suggestedSupplierId] : []
  requisitionSourceLabel.value = `${requisition.requisitionNo} / 行 ${firstLine.lineNo} / ${lineMaterialLabel(firstLine)}`
}

function buildPayload(): ProcurementInquiryPayload | null {
  const quantity = validatePurchaseQuantity(form.quantity)
  const materialId = normalizeRequiredId(form.materialId)
  const projectId = normalizeRequiredId(form.projectId)
  if (materialId === null || quantity.payloadValue === null) {
    formError.value = quantity.message || '请完整填写物料和数量'
    return null
  }
  if (form.procurementMode === 'PROJECT' && projectId === null) {
    formError.value = '项目询价必须选择项目'
    return null
  }
  return {
    procurementMode: form.procurementMode,
    projectId: form.procurementMode === 'PROJECT' ? projectId : null,
    title: selectedMaterial.value?.name,
    supplierIds: form.supplierIds,
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    lines: [{
      lineNo: 10,
      ...(form.requisitionLineId ? { requisitionLineId: form.requisitionLineId } : {}),
      materialId,
      ...(selectedMaterial.value?.unitId ? { unitId: selectedMaterial.value.unitId } : {}),
      quantity: quantity.payloadValue,
    }],
  }
}

async function saveInquiry() {
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
      ? await procurementApi.inquiries.update(editingRecord.value.id, { ...payload, version: editingRecord.value.version })
      : await procurementApi.inquiries.create(payload)
    await router.push({ name: 'procurement-inquiry-detail', params: { id: String(result.id) } })
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
  await loadRequisitionSource()
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="报价导入仅限当前询价范围，不能跨询价写入。">
    <template #alerts>
      <el-alert v-if="referenceError" class="page-alert" type="error" :title="referenceError" show-icon :closable="false" />
      <el-alert v-if="formError" class="page-alert" type="error" :title="formError" show-icon :closable="false" />
      <el-alert v-if="loading" class="page-alert" type="info" title="询价加载中" show-icon :closable="false" />
    </template>
    <section v-if="editLoadFailed" class="section-block">
      <h2>无法编辑询价</h2>
      <p>{{ formError || '询价不存在或无权编辑' }}</p>
      <el-button data-test="back-inquiries" type="primary" @click="router.push({ name: 'procurement-inquiries' })">
        返回询价列表
      </el-button>
    </section>
    <template v-else>
    <section v-if="requisitionSourceLabel" class="section-block source-summary">
      <h2>请购来源</h2>
      <span>请购来源：{{ requisitionSourceLabel }}</span>
      <span>{{ procurementModeDisplay(form.procurementMode, selectedProject?.projectNo, selectedProject?.name) }}</span>
    </section>
    <section class="section-block">
      <h2>询价基础信息</h2>
      <div class="form-grid">
      <label>
        采购模式
        <el-select v-model="form.procurementMode" data-test="inquiry-procurement-mode" style="width: 100%">
          <el-option label="项目专采" value="PROJECT" />
          <el-option label="公共采购" value="PUBLIC" />
        </el-select>
      </label>
      <label>
        项目
        <el-select
          v-model="form.projectId"
          data-test="inquiry-project-id"
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
        <el-select v-model="form.materialId" data-test="inquiry-material-id" style="width: 100%" filterable placeholder="选择物料">
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
        邀请供应商
        <el-select v-model="form.supplierIds" data-test="inquiry-supplier-ids" style="width: 100%" multiple filterable placeholder="选择供应商">
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
        <input v-model="form.quantity" name="inquiry-quantity" autocomplete="off">
      </label>
      <label class="full-row">
        备注
        <textarea v-model="form.remark" name="inquiry-remark" rows="2" />
      </label>
      </div>
    </section>
    <div class="action-bar">
      <el-button data-test="save-inquiry" type="primary" :loading="submitting" @click="saveInquiry">保存询价</el-button>
    </div>
    </template>
  </MasterDataTableView>
</template>

<style scoped>
.form-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
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

.source-summary {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 12px;
}

.full-row {
  grid-column: 1 / -1;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
}

.section-block {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  display: grid;
  gap: 12px;
  padding: 14px;
}

.section-block h2 {
  font-size: 16px;
  margin: 0;
}
</style>
