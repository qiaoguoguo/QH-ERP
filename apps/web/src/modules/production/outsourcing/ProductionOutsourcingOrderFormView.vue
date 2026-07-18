<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { bomApi } from '../../../shared/api/bomApi'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import { masterDataApi } from '../../../shared/api/masterDataApi'
import {
  productionOutsourcingApi,
  type OutsourcingOrderDetailRecord,
  type ProductionOutsourcingApi,
} from '../../../shared/api/productionOutsourcingApi'
import type { ProductionOwnershipType, ResourceId } from '../../../shared/api/projectProductionApi'
import { salesProjectApi } from '../../../shared/api/salesProjectApi'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { pageItems } from '../../system/shared/pageHelpers'
import { productionErrorMessage, validateProductionQuantity } from '../productionPageHelpers'

type Option = { id: ResourceId; label: string }

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const record = ref<OutsourcingOrderDetailRecord | null>(null)
const projects = ref<Option[]>([])
const suppliers = ref<Option[]>([])
const materials = ref<Option[]>([])
const boms = ref<Option[]>([])
const warehouses = ref<Option[]>([])
const form = reactive({
  ownershipType: 'PROJECT' as ProductionOwnershipType,
  projectId: '' as ResourceId | '',
  supplierId: '' as ResourceId | '',
  productMaterialId: '' as ResourceId | '',
  bomId: '' as ResourceId | '',
  issueWarehouseId: '' as ResourceId | '',
  receiptWarehouseId: '' as ResourceId | '',
  plannedQuantity: '',
  plannedIssueDate: '',
  plannedReceiptDate: '',
  remark: '',
})

const isEdit = computed(() => Boolean(route.params.id))
const pageTitle = computed(() => (isEdit.value ? '编辑外协订单' : '新建外协订单'))

function optionLabel(source: Record<string, unknown>, codeKey: string, nameKey: string) {
  return [source[codeKey], source[nameKey]].filter(Boolean).join(' ')
}

function normalizeRequiredId(value: ResourceId | ''): ResourceId | null {
  if (value === '' || value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return value
  }
  const trimmed = String(value).trim()
  if (!trimmed) {
    return null
  }
  return /^\d+$/.test(trimmed) ? Number(trimmed) : trimmed
}

async function loadOptions() {
  const [projectPage, supplierPage, materialPage, bomPage, warehousePage] = await Promise.all([
    salesProjectApi.projects.list({ keyword: '', status: 'ACTIVE', page: 1, pageSize: 100 }),
    masterDataApi.suppliers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
    masterDataApi.materials.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
    bomApi.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
    masterDataApi.warehouses.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
  ])
  projects.value = pageItems(projectPage).map((project) => ({
    id: project.id,
    label: optionLabel(project as unknown as Record<string, unknown>, 'projectNo', 'name'),
  }))
  suppliers.value = pageItems(supplierPage).map((supplier) => ({
    id: supplier.id,
    label: optionLabel(supplier as unknown as Record<string, unknown>, 'code', 'name'),
  }))
  materials.value = pageItems(materialPage).map((material) => ({
    id: material.id,
    label: optionLabel(material as unknown as Record<string, unknown>, 'code', 'name'),
  }))
  boms.value = pageItems(bomPage).map((bom) => ({
    id: bom.id,
    label: [bom.bomCode, bom.versionCode, bom.name].filter(Boolean).join(' '),
  }))
  warehouses.value = pageItems(warehousePage).map((warehouse) => ({
    id: warehouse.id,
    label: optionLabel(warehouse as unknown as Record<string, unknown>, 'code', 'name'),
  }))
}

function applyRecord(detail: OutsourcingOrderDetailRecord) {
  record.value = detail
  form.ownershipType = detail.ownershipType
  form.projectId = detail.projectId ?? ''
  form.supplierId = detail.supplierId
  form.productMaterialId = detail.productMaterialId
  form.bomId = detail.bomId ?? ''
  form.issueWarehouseId = detail.issueWarehouseId ?? ''
  form.receiptWarehouseId = detail.receiptWarehouseId ?? ''
  form.plannedQuantity = detail.plannedQuantity
  form.plannedIssueDate = detail.plannedIssueDate ?? detail.plannedStartDate ?? ''
  form.plannedReceiptDate = detail.plannedReceiptDate ?? detail.plannedFinishDate ?? ''
  form.remark = detail.remark ?? ''
}

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    await loadOptions()
    if (isEdit.value) {
      applyRecord(await productionOutsourcingApi.orders.get(route.params.id as ResourceId))
    }
  } catch (caught) {
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function buildPayload() {
  const quantity = validateProductionQuantity(form.plannedQuantity)
  if (quantity.message || quantity.payloadValue === null) {
    throw new Error(quantity.message ?? '数量不能为空')
  }
  const supplierId = normalizeRequiredId(form.supplierId)
  const productMaterialId = normalizeRequiredId(form.productMaterialId)
  const bomId = normalizeRequiredId(form.bomId)
  const issueWarehouseId = normalizeRequiredId(form.issueWarehouseId)
  const receiptWarehouseId = normalizeRequiredId(form.receiptWarehouseId)
  if (!supplierId || !productMaterialId || !bomId || !issueWarehouseId || !receiptWarehouseId) {
    throw new Error('供应商、物料、BOM 和仓库不能为空')
  }
  if (form.ownershipType === 'PROJECT' && !normalizeRequiredId(form.projectId)) {
    throw new Error('项目外协订单必须选择项目')
  }
  return {
    ownershipType: form.ownershipType,
    projectId: form.ownershipType === 'PROJECT' ? normalizeRequiredId(form.projectId) : null,
    supplierId,
    productMaterialId,
    bomId,
    plannedQuantity: quantity.payloadValue,
    issueWarehouseId,
    receiptWarehouseId,
    plannedIssueDate: form.plannedIssueDate,
    plannedReceiptDate: form.plannedReceiptDate,
    remark: form.remark,
    idempotencyKey: createIdempotencyKey('production-outsourcing-order-save'),
  }
}

async function saveOrder(api: ProductionOutsourcingApi = productionOutsourcingApi) {
  if (saving.value) {
    return
  }
  saving.value = true
  error.value = ''
  try {
    const payload = buildPayload()
    const saved = isEdit.value && record.value
      ? await api.orders.update(record.value.id, { ...payload, version: record.value.version })
      : await api.orders.create(payload)
    await router.push({ name: 'production-outsourcing-order-detail', params: { id: String(saved.id) } })
  } catch (caught) {
    error.value = productionErrorMessage(caught)
  } finally {
    saving.value = false
  }
}

onMounted(loadPage)
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="维护外协订单的项目/公共归属、供应商、物料、BOM、仓库和计划数量。">
    <template #actions>
      <el-button @click="router.push({ name: 'production-outsourcing-orders' })">返回列表</el-button>
      <el-button data-test="save-outsourcing-order" type="primary" :loading="saving" @click="saveOrder()">保存</el-button>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="外协订单加载中" :closable="false" />
    </template>

    <el-form class="detail-form" label-position="top">
      <section class="section-block">
        <h2>归属与来源</h2>
        <div class="form-grid">
          <el-form-item label="归属">
            <el-select v-model="form.ownershipType" data-test="outsourcing-ownership-type" placeholder="请选择归属">
              <el-option label="项目" value="PROJECT" />
              <el-option label="公共" value="PUBLIC" />
            </el-select>
          </el-form-item>
          <el-form-item label="项目">
            <el-select
              v-model="form.projectId"
              data-test="outsourcing-project-id"
              clearable
              filterable
              placeholder="请选择项目"
              :disabled="form.ownershipType === 'PUBLIC'"
            >
              <el-option v-for="project in projects" :key="project.id" :label="project.label" :value="project.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="供应商">
            <el-select v-model="form.supplierId" data-test="outsourcing-supplier-id" filterable placeholder="请选择供应商">
              <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.label" :value="supplier.id" />
            </el-select>
          </el-form-item>
        </div>
      </section>

      <section class="section-block">
        <h2>物料与计划</h2>
        <div class="form-grid">
          <el-form-item label="成品物料">
            <el-select v-model="form.productMaterialId" data-test="outsourcing-product-material-id" filterable placeholder="请选择成品物料">
              <el-option v-for="material in materials" :key="material.id" :label="material.label" :value="material.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="BOM">
            <el-select v-model="form.bomId" data-test="outsourcing-bom-id" filterable placeholder="请选择 BOM">
              <el-option v-for="bom in boms" :key="bom.id" :label="bom.label" :value="bom.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="计划数量">
            <el-input v-model="form.plannedQuantity" name="outsourcing-planned-quantity" />
          </el-form-item>
          <el-form-item label="计划发料">
            <el-input v-model="form.plannedIssueDate" name="outsourcing-planned-issue-date" />
          </el-form-item>
          <el-form-item label="计划收货">
            <el-input v-model="form.plannedReceiptDate" name="outsourcing-planned-receipt-date" />
          </el-form-item>
        </div>
      </section>

      <section class="section-block">
        <h2>仓库</h2>
        <div class="form-grid">
          <el-form-item label="发料仓库">
            <el-select v-model="form.issueWarehouseId" data-test="outsourcing-issue-warehouse-id" filterable placeholder="请选择发料仓库">
              <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.label" :value="warehouse.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="收货仓库">
            <el-select v-model="form.receiptWarehouseId" data-test="outsourcing-receipt-warehouse-id" filterable placeholder="请选择收货仓库">
              <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.label" :value="warehouse.id" />
            </el-select>
          </el-form-item>
        </div>
      </section>
    </el-form>
  </MasterDataTableView>
</template>
