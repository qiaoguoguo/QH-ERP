<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeSettlementApi, type AdvanceFundRecord } from '../../shared/api/financeSettlementApi'
import type { OwnershipType } from '../../shared/api/financeStage028ApiCore'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, financeMethodText, formatFinanceAmount, normalizeOptionalId, ownershipTypeText } from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => route.name === 'finance-prepayment-edit')
const detail = ref<AdvanceFundRecord | null>(null)
const suppliers = ref<PartnerRecord[]>([])
const projects = ref<SalesProjectSummary[]>([])
const error = ref('')
const loading = ref(false)
const submitting = ref(false)
const form = reactive<{ businessDate: string; amount: string; method: string; partnerId: string | number | ''; ownershipType: OwnershipType; projectId: string | number | '' }>({
  businessDate: '',
  amount: '',
  method: 'BANK_TRANSFER',
  partnerId: '',
  ownershipType: 'PROJECT',
  projectId: '',
})

const selectedSupplierName = computed(() => suppliers.value.find((item) => String(item.id) === String(form.partnerId))?.name ?? '')
const selectedProjectName = computed(() => projects.value.find((item) => String(item.id) === String(form.projectId))?.name ?? '')
const saveDisabledReason = computed(() => {
  if (!form.partnerId) return '请选择供应商'
  if (form.ownershipType === 'PROJECT' && !form.projectId) return '请选择项目'
  if (!form.businessDate) return '请选择业务日期'
  if (!/^\d+(\.\d{1,2})?$/.test(form.amount.trim())) return '请填写资金金额，最多两位小数'
  return ''
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [supplierPage, projectPage] = await Promise.all([
      masterDataApi.suppliers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
      salesProjectApi.projects.list({ keyword: '', status: 'ACTIVE', page: 1, pageSize: 200 }),
    ])
    suppliers.value = pageItems(supplierPage)
    projects.value = pageItems(projectPage)
    if (isEdit.value) {
      detail.value = await financeSettlementApi.prepayments.get(route.params.id as string)
      form.partnerId = (detail.value as AdvanceFundRecord & { partnerId?: string | number }).partnerId ?? ''
      form.ownershipType = detail.value.ownershipType
      form.projectId = (detail.value as AdvanceFundRecord & { projectId?: string | number | null }).projectId ?? ''
      form.businessDate = detail.value.businessDate
      form.amount = String(detail.value.amount)
      form.method = (detail.value as AdvanceFundRecord & { method?: string }).method ?? 'BANK_TRANSFER'
    }
    if (!form.partnerId && suppliers.value[0]) form.partnerId = suppliers.value[0].id
    if (!form.projectId && projects.value[0]) form.projectId = projects.value[0].id
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function save() {
  if (submitting.value || saveDisabledReason.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const payload = {
      partnerId: form.partnerId,
      supplierId: form.partnerId,
      ownershipType: form.ownershipType,
      projectId: form.ownershipType === 'PROJECT' ? normalizeOptionalId(form.projectId) ?? null : null,
      businessDate: form.businessDate,
      amount: form.amount.trim(),
      method: form.method,
      allocations: [],
      version: detail.value?.version ?? 0,
      idempotencyKey: `prepayment-${Date.now()}`,
    }
    const result = detail.value
      ? await financeSettlementApi.prepayments.update(route.params.id as string, payload)
      : await financeSettlementApi.prepayments.create(payload)
    await router.push({ name: 'finance-prepayment-detail', params: { id: String(result.id) } })
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <MasterDataTableView :title="isEdit ? '编辑预付款草稿' : '登记预付款'" description="登记真实付款资金草稿，可保留未核销余额并后续发起核销。">
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="预付款表单加载中" :closable="false" />
      <el-alert v-if="saveDisabledReason" type="warning" :title="saveDisabledReason" :closable="false" />
    </template>
    <div class="finance-summary-strip">
      <div><span>往来方</span><strong>{{ selectedSupplierName || '待选择供应商' }}</strong></div>
      <div><span>项目/公共归属</span><strong>{{ ownershipTypeText(form.ownershipType) }} {{ selectedProjectName }}</strong></div>
      <div><span>资金方式</span><strong>{{ financeMethodText(form.method) }}</strong></div>
      <div><span>未核销余额</span><strong>{{ formatFinanceAmount(form.amount) }}</strong></div>
    </div>
    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="供应商">
          <el-select v-model="form.partnerId" filterable clearable placeholder="选择供应商">
            <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目/公共归属">
          <el-select v-model="form.ownershipType" placeholder="选择归属">
            <el-option label="项目" value="PROJECT" />
            <el-option label="公共" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="form.projectId" filterable clearable :disabled="form.ownershipType === 'PUBLIC'" placeholder="选择项目">
            <el-option v-for="project in projects" :key="project.id" :label="`${project.projectNo} ${project.name}`" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期"><el-date-picker v-model="form.businessDate" name="prepayment-business-date" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="选择业务日期" /></el-form-item>
        <el-form-item label="资金金额"><el-input v-model="form.amount" name="prepayment-amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="资金方式">
          <el-select v-model="form.method" data-test="fund-method-select" placeholder="选择资金方式">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
          </el-select>
        </el-form-item>
      </div>
    </el-form>
    <div class="finance-form-footer"><el-button @click="router.back()">取消</el-button><el-button data-test="save-prepayment" type="primary" :loading="submitting" :disabled="submitting || Boolean(saveDisabledReason)" @click="save">保存预付款</el-button></div>
  </MasterDataTableView>
</template>
