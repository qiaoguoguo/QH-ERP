<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { masterDataApi, type MaterialRecord, type PartnerRecord } from '../../shared/api/masterDataApi'
import {
  procurementApi,
  type PriceAgreementDetailRecord,
  type PriceAgreementPayload,
  type ProcurementMode,
  type ResourceId,
} from '../../shared/api/procurementApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import {
  normalizeRequiredId,
  procurementErrorMessage,
  procurementModeFrom,
  validateProcurementDecimal,
  validatePurchaseUnitPrice,
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
const suppliers = ref<PartnerRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const editingRecord = ref<PriceAgreementDetailRecord | null>(null)
const form = reactive({
  procurementMode: 'PROJECT' as ProcurementMode,
  projectId: '' as ResourceId | '',
  supplierId: '' as ResourceId | '',
  materialId: '' as ResourceId | '',
  taxRate: '',
  taxExcludedUnitPrice: '',
  taxIncludedUnitPrice: '',
  validFrom: '',
  validTo: '',
  remark: '',
})

const isEdit = computed(() => Boolean(route.params.id))
const pageTitle = computed(() => (isEdit.value ? '编辑价格协议' : '新建价格协议'))
const selectedProject = computed(() => projects.value.find((project) => String(project.id) === String(form.projectId)))
const selectedSupplier = computed(() => suppliers.value.find((supplier) => String(supplier.id) === String(form.supplierId)))
const selectedMaterial = computed(() => materials.value.find((material) => String(material.id) === String(form.materialId)))

async function loadReferences() {
  try {
    const [projectPage, supplierPage, materialPage] = await Promise.all([
      salesProjectApi.projects.list({ keyword: '', status: 'ACTIVE', page: 1, pageSize: 200 }),
      masterDataApi.suppliers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      masterDataApi.materials.list({ keyword: '', status: 'ENABLED', sourceType: 'PURCHASED', page: 1, pageSize: 200 }),
    ])
    projects.value = projectPage.items
    suppliers.value = supplierPage.items
    materials.value = materialPage.items
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
    const detail = await procurementApi.priceAgreements.get(route.params.id as ResourceId)
    editingRecord.value = detail
    form.procurementMode = procurementModeFrom(detail) ?? 'PROJECT'
    form.projectId = detail.projectId ?? ''
    form.supplierId = detail.supplierId
    form.materialId = detail.materialId
    form.taxRate = detail.taxRate ?? ''
    form.taxExcludedUnitPrice = detail.taxExcludedUnitPrice ?? ''
    form.taxIncludedUnitPrice = detail.taxIncludedUnitPrice ?? ''
    form.validFrom = detail.validFrom
    form.validTo = detail.validTo
    form.remark = detail.remark ?? ''
  } catch (caught) {
    editLoadFailed.value = true
    formError.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function buildPayload(): PriceAgreementPayload | null {
  const taxRate = validateProcurementDecimal(form.taxRate, { label: '税率', allowZero: true })
  const taxExcludedUnitPrice = validatePurchaseUnitPrice(form.taxExcludedUnitPrice)
  const taxIncludedUnitPrice = validatePurchaseUnitPrice(form.taxIncludedUnitPrice)
  const supplierId = normalizeRequiredId(form.supplierId)
  const materialId = normalizeRequiredId(form.materialId)
  const projectId = normalizeRequiredId(form.projectId)
  if (
    supplierId === null
    || materialId === null
    || !form.validFrom
    || !form.validTo
    || taxRate.payloadValue === null
    || taxExcludedUnitPrice.payloadValue === null
    || taxIncludedUnitPrice.payloadValue === null
  ) {
    formError.value = taxRate.message
      || taxExcludedUnitPrice.message
      || taxIncludedUnitPrice.message
      || '请完整填写供应商、物料、税价和有效期'
    return null
  }
  if (form.procurementMode === 'PROJECT' && projectId === null) {
    formError.value = '项目专采价格协议必须选择项目'
    return null
  }
  return {
    procurementMode: form.procurementMode,
    projectId: form.procurementMode === 'PROJECT' ? projectId : null,
    supplierId,
    materialId,
    taxRate: taxRate.payloadValue,
    taxExcludedUnitPrice: taxExcludedUnitPrice.payloadValue,
    taxIncludedUnitPrice: taxIncludedUnitPrice.payloadValue,
    currency: 'CNY',
    validFrom: form.validFrom,
    validTo: form.validTo,
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
  }
}

async function saveAgreement() {
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
      ? await procurementApi.priceAgreements.update(editingRecord.value.id, { ...payload, version: editingRecord.value.version })
      : await procurementApi.priceAgreements.create(payload)
    await router.push({ name: 'procurement-price-agreement-detail', params: { id: String(result.id) } })
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
  <MasterDataTableView :title="pageTitle" description="首版币种固定 CNY，提交后进入固定激活审批。">
    <template #alerts>
      <el-alert v-if="referenceError" class="page-alert" type="error" :title="referenceError" show-icon :closable="false" />
      <el-alert v-if="formError" class="page-alert" type="error" :title="formError" show-icon :closable="false" />
      <el-alert v-if="loading" class="page-alert" type="info" title="价格协议加载中" show-icon :closable="false" />
    </template>
    <section v-if="editLoadFailed" class="section-block">
      <h2>无法编辑价格协议</h2>
      <p>{{ formError || '价格协议不存在或无权编辑' }}</p>
      <div class="action-bar">
        <el-button data-test="back-price-agreements" @click="router.push({ name: 'procurement-price-agreements' })">
          返回价格协议列表
        </el-button>
      </div>
    </section>
    <template v-else>
      <section class="section-block">
        <h2>协议基础信息</h2>
        <div class="form-grid">
          <label>
            采购模式
            <el-select v-model="form.procurementMode" data-test="agreement-procurement-mode" style="width: 100%">
              <el-option label="项目专采" value="PROJECT" />
              <el-option label="公共采购" value="PUBLIC" />
            </el-select>
          </label>
          <label>
            项目
            <el-select
              v-model="form.projectId"
              data-test="agreement-project-id"
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
            供应商
            <el-select v-model="form.supplierId" data-test="agreement-supplier-id" style="width: 100%" filterable placeholder="选择供应商">
              <el-option
                v-for="supplier in suppliers"
                :key="supplier.id"
                :label="`${supplier.code} ${supplier.name}`"
                :value="supplier.id"
              />
            </el-select>
          </label>
          <label>
            供应商显示
            <span class="readonly-field">{{ selectedSupplier ? `${selectedSupplier.code} ${selectedSupplier.name}` : '未选择供应商' }}</span>
          </label>
          <label>
            物料
            <el-select v-model="form.materialId" data-test="agreement-material-id" style="width: 100%" filterable placeholder="选择物料">
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
            税率
            <input v-model="form.taxRate" name="agreement-tax-rate" autocomplete="off">
          </label>
          <label>
            未税单价
            <input v-model="form.taxExcludedUnitPrice" name="agreement-tax-excluded-unit-price" autocomplete="off">
          </label>
          <label>
            含税单价
            <input v-model="form.taxIncludedUnitPrice" name="agreement-tax-included-unit-price" autocomplete="off">
          </label>
          <label>
            生效日期
            <el-date-picker
              v-model="form.validFrom"
              name="agreement-valid-from"
              value-on-clear=""
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              placeholder="选择生效日期"
            />
          </label>
          <label>
            失效日期
            <el-date-picker
              v-model="form.validTo"
              name="agreement-valid-to"
              value-on-clear=""
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              placeholder="选择失效日期"
            />
          </label>
          <label class="full-row">
            备注
            <textarea v-model="form.remark" name="agreement-remark" rows="2" />
          </label>
        </div>
      </section>
      <div class="action-bar">
        <el-button data-test="save-price-agreement" type="primary" :loading="submitting" @click="saveAgreement">
          保存价格协议
        </el-button>
      </div>
    </template>
  </MasterDataTableView>
</template>

<style scoped>
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
textarea {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
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
