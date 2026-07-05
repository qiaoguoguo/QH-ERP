<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { queryWithReturnTo, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { bomApi, type BomDetailRecord, type BomSummaryRecord } from '../../shared/api/bomApi'
import { masterDataApi, type MaterialRecord, type WarehouseRecord } from '../../shared/api/masterDataApi'
import {
  productionApi,
  type ProductionWorkOrderDetailRecord,
  type ProductionWorkOrderPayload,
  type ResourceId,
} from '../../shared/api/productionApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { materialTypeLabel } from '../master/shared/masterPageHelpers'
import { pageItems } from '../system/shared/pageHelpers'
import ProductionWorkOrderStatusTag from './ProductionWorkOrderStatusTag.vue'
import {
  formatProductionQuantity,
  productionErrorMessage,
  validateProductionQuantity,
} from './productionPageHelpers'

const route = useRoute()
const router = useRouter()
const materials = ref<MaterialRecord[]>([])
const boms = ref<BomSummaryRecord[]>([])
const warehouses = ref<WarehouseRecord[]>([])
const selectedBomDetail = ref<BomDetailRecord | null>(null)
const editingRecord = ref<ProductionWorkOrderDetailRecord | null>(null)
const referenceLoading = ref(true)
const loading = ref(false)
const referenceError = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const bomLoading = ref(false)

const form = reactive({
  productMaterialId: '' as ResourceId | '',
  bomId: '' as ResourceId | '',
  plannedQuantity: '',
  plannedStartDate: '',
  plannedFinishDate: '',
  issueWarehouseId: '' as ResourceId | '',
  receiptWarehouseId: '' as ResourceId | '',
  remark: '',
})

const isEdit = computed(() => Boolean(route.params.id))
const isDraftRecord = computed(() => !editingRecord.value || editingRecord.value.status === 'DRAFT')
const pageTitle = computed(() => (isEdit.value ? '编辑生产工单' : '新建生产工单'))
const productMaterials = computed(() => materials.value.filter((material) => (
  material.materialType === 'FINISHED_GOOD' || material.materialType === 'SEMI_FINISHED'
)))
const availableBoms = computed(() => boms.value.filter((bom) => (
  !form.productMaterialId || String(bom.parentMaterialId) === String(form.productMaterialId)
)))

function normalizeOptionalId(value: ResourceId | ''): ResourceId | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined
  }
  const trimmedValue = String(value).trim()
  if (!trimmedValue) {
    return undefined
  }
  const numericValue = Number(trimmedValue)
  return Number.isFinite(numericValue) ? numericValue : trimmedValue
}

function normalizeRequiredId(value: ResourceId | ''): ResourceId | null {
  return normalizeOptionalId(value) ?? null
}

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    const [materialPage, bomPage, warehousePage] = await Promise.all([
      masterDataApi.materials.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      bomApi.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      masterDataApi.warehouses.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    ])
    materials.value = pageItems(materialPage)
    boms.value = pageItems(bomPage)
    warehouses.value = pageItems(warehousePage)
  } catch (caught) {
    materials.value = []
    boms.value = []
    warehouses.value = []
    referenceError.value = productionErrorMessage(caught)
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
    const detail = await productionApi.workOrders.get(route.params.id as ResourceId)
    editingRecord.value = detail
    form.productMaterialId = detail.productMaterialId
    form.bomId = detail.bomId
    form.plannedQuantity = String(detail.plannedQuantity)
    form.plannedStartDate = detail.plannedStartDate
    form.plannedFinishDate = detail.plannedFinishDate
    form.issueWarehouseId = detail.issueWarehouseId
    form.receiptWarehouseId = detail.receiptWarehouseId
    form.remark = detail.remark ?? ''
    await loadBomDetail(detail.bomId)
  } catch (caught) {
    formError.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadBomDetail(bomId: ResourceId | '') {
  const normalizedBomId = normalizeOptionalId(bomId)
  selectedBomDetail.value = null
  if (normalizedBomId === undefined) {
    return
  }
  bomLoading.value = true
  try {
    selectedBomDetail.value = await bomApi.get(normalizedBomId)
  } catch (caught) {
    formError.value = productionErrorMessage(caught)
  } finally {
    bomLoading.value = false
  }
}

function validateForm(): ProductionWorkOrderPayload | null {
  const productMaterialId = normalizeRequiredId(form.productMaterialId)
  const bomId = normalizeRequiredId(form.bomId)
  const issueWarehouseId = normalizeRequiredId(form.issueWarehouseId)
  const receiptWarehouseId = normalizeRequiredId(form.receiptWarehouseId)

  if (productMaterialId === null || bomId === null || issueWarehouseId === null || receiptWarehouseId === null) {
    formError.value = '请完整选择产品物料、BOM、领料仓库和入库仓库'
    return null
  }
  if (!form.plannedStartDate.trim() || !form.plannedFinishDate.trim()) {
    formError.value = '请填写计划开工日期和计划完工日期'
    return null
  }
  if (form.plannedFinishDate < form.plannedStartDate) {
    formError.value = '计划完工日期不得早于计划开工日期'
    return null
  }
  const quantityResult = validateProductionQuantity(form.plannedQuantity)
  if (quantityResult.payloadValue === null) {
    formError.value = quantityResult.message ?? '计划数量不正确'
    return null
  }

  formError.value = ''
  return {
    productMaterialId,
    bomId,
    plannedQuantity: quantityResult.payloadValue,
    issueWarehouseId,
    receiptWarehouseId,
    plannedStartDate: form.plannedStartDate.trim(),
    plannedFinishDate: form.plannedFinishDate.trim(),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
  }
}

async function saveWorkOrder() {
  if (formSubmitting.value) {
    return
  }
  if (!isDraftRecord.value) {
    formError.value = '仅草稿工单可编辑'
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }
  formSubmitting.value = true
  try {
    const result = editingRecord.value
      ? await productionApi.workOrders.update(editingRecord.value.id, payload)
      : await productionApi.workOrders.create(payload)
    await router.push({
      name: 'production-work-order-detail',
      params: { id: String(result.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
  } catch (caught) {
    formError.value = productionErrorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function cancel() {
  if (editingRecord.value) {
    void router.push({
      name: 'production-work-order-detail',
      params: { id: String(editingRecord.value.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
    return
  }
  void router.push({ name: 'production-work-orders' })
}

watch(() => form.productMaterialId, () => {
  if (isEdit.value && editingRecord.value && String(editingRecord.value.productMaterialId) === String(form.productMaterialId)) {
    return
  }
  form.bomId = ''
  selectedBomDetail.value = null
})

watch(() => form.bomId, (bomId) => {
  void loadBomDetail(bomId)
})

onMounted(async () => {
  await loadReferences()
  await loadRecord()
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="维护生产工单草稿，发布后生成 BOM 用料快照并进入执行。">
    <template #alerts>
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="生产工单表单加载中" :closable="false" />
      <el-alert v-if="editingRecord && !isDraftRecord" class="state-alert" type="warning" title="仅草稿工单可编辑" :closable="false" />
    </template>

    <el-form label-position="top" class="production-form">
      <div v-if="editingRecord" class="edit-status">
        <span>当前状态</span>
        <ProductionWorkOrderStatusTag :status="editingRecord.status" />
      </div>
      <div class="production-form-grid">
        <el-form-item label="产品物料">
          <el-select
            v-model="form.productMaterialId"
            filterable
            placeholder="请选择成品或半成品"
            style="width: 100%"
            :disabled="!isDraftRecord"
          >
            <el-option
              v-for="material in productMaterials"
              :key="material.id"
              :label="`${material.code} ${material.name}`"
              :value="material.id"
            >
              <span>{{ material.code }} {{ material.name }}</span>
              <span class="option-meta">{{ materialTypeLabel(material.materialType) }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="BOM">
          <el-select
            v-model="form.bomId"
            filterable
            placeholder="请选择启用 BOM"
            style="width: 100%"
            :disabled="!isDraftRecord"
          >
            <el-option
              v-for="bom in availableBoms"
              :key="bom.id"
              :label="`${bom.bomCode} / ${bom.versionCode}`"
              :value="bom.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划数量">
          <el-input v-model="form.plannedQuantity" name="production-planned-quantity" placeholder="大于 0，最多 6 位小数" :disabled="!isDraftRecord" />
        </el-form-item>
        <el-form-item label="计划开工日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.plannedStartDate" name="production-planned-start-date" placeholder="选择日期" :disabled="!isDraftRecord" />
        </el-form-item>
        <el-form-item label="计划完工日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.plannedFinishDate" name="production-planned-finish-date" placeholder="选择日期" :disabled="!isDraftRecord" />
        </el-form-item>
        <el-form-item label="领料仓库">
          <el-select v-model="form.issueWarehouseId" filterable placeholder="请选择领料仓库" style="width: 100%" :disabled="!isDraftRecord">
            <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.name" :value="warehouse.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库仓库">
          <el-select v-model="form.receiptWarehouseId" filterable placeholder="请选择入库仓库" style="width: 100%" :disabled="!isDraftRecord">
            <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.name" :value="warehouse.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="production-work-order-remark" placeholder="可选" :disabled="!isDraftRecord" />
        </el-form-item>
      </div>
    </el-form>

    <section class="section-block">
      <div class="section-title">BOM 明细预览</div>
      <el-alert v-if="bomLoading" class="state-alert" type="info" title="BOM 明细加载中" :closable="false" />
      <el-empty v-if="!selectedBomDetail && !bomLoading" description="选择 BOM 后显示用料明细" />
      <div v-if="selectedBomDetail" class="table-scroll">
        <el-table :data="selectedBomDetail.items" empty-text="暂无 BOM 明细" stripe>
          <el-table-column prop="lineNo" label="行号" width="78" />
          <el-table-column label="子项物料" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.childMaterialCode }} {{ row.childMaterialName }}
            </template>
          </el-table-column>
          <el-table-column label="用量" min-width="110" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(row.quantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="unitName" label="单位" min-width="90" />
          <el-table-column label="损耗率" min-width="100" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(row.lossRate) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>
    </section>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button
        data-test="save-production-work-order"
        type="primary"
        :loading="formSubmitting"
        :disabled="formSubmitting || !isDraftRecord"
        @click="saveWorkOrder"
      >
        保存
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.production-form {
  padding: 14px;
}

.production-form-grid {
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

.edit-status span {
  color: var(--qherp-muted);
}

.section-block {
  padding: 0 14px 14px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.option-meta {
  color: var(--qherp-muted);
  float: right;
  font-size: 12px;
  margin-left: 12px;
}

@media (max-width: 760px) {
  .production-form-grid {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
