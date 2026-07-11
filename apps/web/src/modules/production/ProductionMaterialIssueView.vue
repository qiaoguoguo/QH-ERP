<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  productionApi,
  type ProductionMaterialIssuePayload,
  type ProductionWorkOrderDetailRecord,
  type ProductionWorkOrderMaterialRecord,
  type ResourceId,
} from '../../shared/api/productionApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import QualityStatusTag from '../quality/QualityStatusTag.vue'
import ProductionWorkOrderStatusTag from './ProductionWorkOrderStatusTag.vue'
import {
  formatProductionQuantity,
  productionErrorMessage,
  todayText,
  validateProductionQuantity,
} from './productionPageHelpers'

interface IssueLineDraft {
  workOrderMaterialId: ResourceId
  lineNo: number
  warehouseId: ResourceId | ''
  quantity: string
  remark: string
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const workOrder = ref<ProductionWorkOrderDetailRecord | null>(null)
const lines = ref<IssueLineDraft[]>([])
const loading = ref(true)
const error = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const lineErrors = ref<Record<number, string>>({})
const form = reactive({
  businessDate: todayText(),
  reason: '生产领料',
  remark: '',
})

const executable = computed(() => workOrder.value?.status === 'RELEASED' || workOrder.value?.status === 'IN_PROGRESS')
const canCreateIssue = computed(() => authStore.hasPermission('production:issue:create'))
const canSubmitIssue = computed(() => executable.value && canCreateIssue.value)

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

async function loadWorkOrder() {
  loading.value = true
  error.value = ''
  try {
    const detail = await productionApi.workOrders.get(route.params.id as ResourceId)
    workOrder.value = detail
    lines.value = detail.materials.map((material) => ({
      workOrderMaterialId: material.id,
      lineNo: material.lineNo,
      warehouseId: detail.issueWarehouseId,
      quantity: '',
      remark: '',
    }))
  } catch (caught) {
    workOrder.value = null
    lines.value = []
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function materialForLine(line: IssueLineDraft): ProductionWorkOrderMaterialRecord | undefined {
  return workOrder.value?.materials.find((material) => String(material.id) === String(line.workOrderMaterialId))
}

function numericMaterialValue(material: ProductionWorkOrderMaterialRecord | undefined, key: 'maxSelectableQuantity'): number | null {
  const value = material?.[key]
  if (value === null || value === undefined || value === '') {
    return null
  }
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

function materialDisabledReason(material: ProductionWorkOrderMaterialRecord | undefined): string {
  return material?.disabledReason ?? '该候选库存不可领料'
}

function materialUnavailable(material: ProductionWorkOrderMaterialRecord | undefined): boolean {
  const maxSelectableQuantity = numericMaterialValue(material, 'maxSelectableQuantity')
  return material?.selectable === false || (maxSelectableQuantity !== null && maxSelectableQuantity <= 0)
}

function validateForm(): ProductionMaterialIssuePayload | null {
  if (!workOrder.value) {
    formError.value = '生产工单未加载'
    return null
  }
  if (!canCreateIssue.value) {
    formError.value = '缺少生产领料创建权限'
    return null
  }
  if (!executable.value) {
    formError.value = '仅已发布或生产中的工单可领料'
    return null
  }
  if (!form.businessDate.trim() || !form.reason.trim()) {
    formError.value = '请填写领料日期和原因'
    return null
  }

  const nextLineErrors: Record<number, string> = {}
  const payloadLines = []
  let firstUnavailableReason = ''
  for (const line of lines.value) {
    const material = materialForLine(line)
    if (materialUnavailable(material)) {
      firstUnavailableReason ||= materialDisabledReason(material)
      if (line.quantity.trim()) {
        nextLineErrors[line.lineNo] = materialDisabledReason(material)
      }
      continue
    }
    if (!line.quantity.trim()) {
      continue
    }
    const quantityResult = validateProductionQuantity(line.quantity)
    if (quantityResult.payloadValue === null) {
      nextLineErrors[line.lineNo] = quantityResult.message ?? '数量不正确'
      continue
    }
    if (material && quantityResult.value !== null && quantityResult.value > Number(material.remainingQuantity)) {
      nextLineErrors[line.lineNo] = '本次领料不能大于未领数量'
      continue
    }
    const maxSelectableQuantity = numericMaterialValue(material, 'maxSelectableQuantity')
    if (maxSelectableQuantity !== null && quantityResult.value !== null && quantityResult.value > maxSelectableQuantity) {
      nextLineErrors[line.lineNo] = '本次领料不能大于本次最多领料'
      continue
    }
    const warehouseId = normalizeRequiredId(line.warehouseId)
    if (warehouseId === null) {
      nextLineErrors[line.lineNo] = '请选择仓库'
      continue
    }
    if (String(warehouseId) !== String(workOrder.value.issueWarehouseId)) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行领料仓库必须与工单领料仓库一致，按工单领料仓库消耗预留`
      continue
    }
    payloadLines.push({
      workOrderMaterialId: line.workOrderMaterialId,
      lineNo: line.lineNo,
      warehouseId,
      quantity: quantityResult.payloadValue,
      ...(line.remark.trim() ? { remark: line.remark.trim() } : {}),
    })
  }

  lineErrors.value = nextLineErrors
  if (Object.keys(nextLineErrors).length > 0) {
    formError.value = ''
    return null
  }
  if (payloadLines.length === 0) {
    formError.value = firstUnavailableReason || '至少填写一行本次领料数量'
    return null
  }

  formError.value = ''
  return {
    businessDate: form.businessDate.trim(),
    reason: form.reason.trim(),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    lines: payloadLines,
  }
}

async function submitIssue() {
  if (!workOrder.value || formSubmitting.value) {
    return
  }
  if (!canCreateIssue.value) {
    formError.value = '缺少生产领料创建权限'
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }
  formSubmitting.value = true
  try {
    await productionApi.materialIssues.create(workOrder.value.id, payload)
    await router.push({ name: 'production-work-order-detail', params: { id: String(workOrder.value.id) } })
  } catch (caught) {
    formError.value = productionErrorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

function cancel() {
  void router.push({ name: 'production-work-order-detail', params: { id: String(route.params.id) } })
}

onMounted(() => {
  void loadWorkOrder()
})
</script>

<template>
  <MasterDataTableView title="生产领料" description="从工单用料快照创建领料草稿，按工单领料仓库消耗预留，过账后扣减库存。">
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="生产领料页面加载中" :closable="false" />
      <el-alert v-if="workOrder && !canCreateIssue" class="state-alert" type="warning" title="缺少生产领料创建权限，无法保存领料单" :closable="false" />
      <el-alert v-if="workOrder && !executable" class="state-alert" type="warning" title="当前工单状态不可领料" :closable="false" />
    </template>

    <div v-if="workOrder" class="production-execution-page">
      <section class="work-order-summary">
        <div>
          <span>工单编号</span>
          <strong>{{ workOrder.workOrderNo }}</strong>
        </div>
        <div>
          <span>产品</span>
          <strong>{{ workOrder.productMaterialCode }} {{ workOrder.productMaterialName }}</strong>
        </div>
        <div>
          <span>状态</span>
          <ProductionWorkOrderStatusTag :status="workOrder.status" />
        </div>
        <div>
          <span>领料仓库</span>
          <strong>{{ workOrder.issueWarehouseName }}</strong>
        </div>
      </section>

      <el-form label-position="top" class="execution-form">
        <div class="execution-form-grid">
          <el-form-item label="领料日期">
            <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.businessDate" name="production-issue-date" placeholder="选择日期" :disabled="!canSubmitIssue" />
          </el-form-item>
          <el-form-item label="原因">
            <el-input v-model="form.reason" name="production-issue-reason" :disabled="!canSubmitIssue" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" name="production-issue-remark" placeholder="可选" :disabled="!canSubmitIssue" />
          </el-form-item>
        </div>
      </el-form>

      <div class="table-scroll">
        <el-table :data="lines" empty-text="暂无用料快照" stripe>
          <el-table-column prop="lineNo" label="行号" width="76" />
          <el-table-column label="物料" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <template v-if="materialForLine(row)">
                {{ materialForLine(row)?.materialCode }} {{ materialForLine(row)?.materialName }}
              </template>
            </template>
          </el-table-column>
          <el-table-column label="应领" min-width="100" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(materialForLine(row)?.requiredQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已领" min-width="100" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(materialForLine(row)?.issuedQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="未领" min-width="100" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(materialForLine(row)?.remainingQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="质量状态" min-width="110">
            <template #default="{ row }">
              <QualityStatusTag
                :quality-status="materialForLine(row)?.qualityStatus"
                :quality-status-name="materialForLine(row)?.qualityStatusName"
              />
            </template>
          </el-table-column>
          <el-table-column label="现存数量" min-width="120" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(materialForLine(row)?.quantityOnHand) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="占用库存" min-width="120" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(materialForLine(row)?.occupiedQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预留库存" min-width="120" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(materialForLine(row)?.reservedQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="现货净可用" min-width="130" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(materialForLine(row)?.availableQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="可承诺量" min-width="120" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(materialForLine(row)?.availableToPromiseQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="本次最多领料" min-width="140" align="right">
            <template #default="{ row }">
              <span class="numeric-cell">{{ formatProductionQuantity(materialForLine(row)?.maxSelectableQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="禁用原因" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="candidate-disabled-reason">{{ materialForLine(row)?.disabledReason || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="本次领料" min-width="150">
            <template #default="{ row }">
              <el-input
                  v-model="row.quantity"
                  :disabled="!canSubmitIssue || materialUnavailable(materialForLine(row)) || Number(materialForLine(row)?.remainingQuantity ?? 0) <= 0"
                  placeholder="0.000000"
                />
              <div v-if="lineErrors[row.lineNo]" class="line-error">{{ lineErrors[row.lineNo] }}</div>
            </template>
          </el-table-column>
          <el-table-column label="领料仓库" min-width="170">
            <template #default="{ row }">
              <el-select v-model="row.warehouseId" placeholder="工单领料仓库" style="width: 100%" disabled>
                <el-option :label="workOrder.issueWarehouseName" :value="workOrder.issueWarehouseId" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.remark" placeholder="可选" :disabled="!canSubmitIssue" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button
        type="primary"
        :loading="formSubmitting"
        :disabled="formSubmitting || !canSubmitIssue"
        :title="!canCreateIssue ? '缺少生产领料创建权限' : ''"
        @click="submitIssue"
      >
        保存领料单
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.production-execution-page {
  padding: 14px;
}

.work-order-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.work-order-summary > div {
  border: 1px solid var(--qherp-border);
  border-radius: 8px;
  min-width: 0;
  padding: 12px;
}

.work-order-summary span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
}

.work-order-summary strong {
  display: block;
  margin-top: 4px;
  word-break: break-word;
}

.execution-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 14px;
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.line-error {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 4px;
}

.candidate-disabled-reason {
  color: var(--el-color-danger);
  font-size: 12px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--qherp-border);
}

@media (max-width: 900px) {
  .execution-form-grid,
  .work-order-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .execution-form-grid,
  .work-order-summary {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
