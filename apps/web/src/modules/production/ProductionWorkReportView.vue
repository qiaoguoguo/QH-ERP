<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  productionApi,
  type ProductionWorkOrderDetailRecord,
  type ProductionWorkReportPayload,
  type ResourceId,
} from '../../shared/api/productionApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import ProductionWorkOrderStatusTag from './ProductionWorkOrderStatusTag.vue'
import {
  formatProductionQuantity,
  productionErrorMessage,
  todayText,
  validateProductionQuantity,
} from './productionPageHelpers'

const route = useRoute()
const router = useRouter()
const workOrder = ref<ProductionWorkOrderDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const form = reactive({
  businessDate: todayText(),
  qualifiedQuantity: '',
  defectiveQuantity: '0',
  remark: '',
})

const executable = computed(() => workOrder.value?.status === 'RELEASED' || workOrder.value?.status === 'IN_PROGRESS')
const remainingReportQuantity = computed(() => {
  if (!workOrder.value) {
    return 0
  }
  return Math.max(0, Number(workOrder.value.plannedQuantity) - Number(workOrder.value.reportedQuantity))
})

async function loadWorkOrder() {
  loading.value = true
  error.value = ''
  try {
    workOrder.value = await productionApi.workOrders.get(route.params.id as ResourceId)
  } catch (caught) {
    workOrder.value = null
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateForm(): ProductionWorkReportPayload | null {
  if (!workOrder.value) {
    formError.value = '生产工单未加载'
    return null
  }
  if (!executable.value) {
    formError.value = '仅已发布或生产中的工单可报工'
    return null
  }
  if (!form.businessDate.trim()) {
    formError.value = '请填写报工日期'
    return null
  }

  const qualified = validateProductionQuantity(form.qualifiedQuantity, { allowZero: true })
  if (qualified.payloadValue === null) {
    formError.value = `合格数量${qualified.message ?? '不正确'}`
    return null
  }
  const defective = validateProductionQuantity(form.defectiveQuantity, { allowZero: true })
  if (defective.payloadValue === null) {
    formError.value = `不良数量${defective.message ?? '不正确'}`
    return null
  }
  const totalQuantity = Number(qualified.value) + Number(defective.value)
  if (totalQuantity <= 0) {
    formError.value = '合格数量和不良数量合计必须大于 0'
    return null
  }
  if (totalQuantity > remainingReportQuantity.value) {
    formError.value = '累计报工不能超过计划数量'
    return null
  }

  formError.value = ''
  return {
    businessDate: form.businessDate.trim(),
    qualifiedQuantity: qualified.payloadValue,
    defectiveQuantity: defective.payloadValue,
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
  }
}

async function submitReport() {
  if (!workOrder.value || formSubmitting.value) {
    return
  }
  const payload = validateForm()
  if (!payload) {
    return
  }
  formSubmitting.value = true
  try {
    await productionApi.reports.create(workOrder.value.id, payload)
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

onMounted(loadWorkOrder)
</script>

<template>
  <MasterDataTableView title="生产报工" description="记录工单生产结果，报工过账后更新累计报工数量。">
    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="生产报工页面加载中" :closable="false" />
      <el-alert v-if="workOrder && !executable" class="state-alert" type="warning" title="当前工单状态不可报工" :closable="false" />
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
          <span>剩余可报工</span>
          <strong>{{ formatProductionQuantity(remainingReportQuantity) }}</strong>
        </div>
      </section>

      <el-form label-position="top" class="execution-form">
        <div class="execution-form-grid">
          <el-form-item label="报工日期">
            <el-input v-model="form.businessDate" name="production-report-date" placeholder="YYYY-MM-DD" :disabled="!executable" />
          </el-form-item>
          <el-form-item label="合格数量">
            <el-input v-model="form.qualifiedQuantity" name="production-qualified-quantity" placeholder="0.000000" :disabled="!executable" />
          </el-form-item>
          <el-form-item label="不良数量">
            <el-input v-model="form.defectiveQuantity" name="production-defective-quantity" placeholder="0.000000" :disabled="!executable" />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="production-report-remark" type="textarea" :rows="3" placeholder="可选" :disabled="!executable" />
        </el-form-item>
      </el-form>
    </div>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button type="primary" :loading="formSubmitting" :disabled="formSubmitting || !executable" @click="submitReport">
        保存报工单
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
