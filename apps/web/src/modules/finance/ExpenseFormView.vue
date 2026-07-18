<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeExpenseApi, type ExpenseCategoryRecord, type ExpensePayload, type ExpenseRecord, type ExpenseSourceCandidateRecord, type ExpenseSourceType } from '../../shared/api/financeExpenseApi'
import type { OwnershipType } from '../../shared/api/financeStage028ApiCore'
import { masterDataApi, type PartnerRecord } from '../../shared/api/masterDataApi'
import { salesProjectApi, type SalesProjectSummary } from '../../shared/api/salesProjectApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { addFinanceAmounts, financeErrorMessage, financeSourceTypeText, formatFinanceAmount, normalizeOptionalId, ownershipTypeText } from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => route.name === 'finance-expense-edit')
const detail = ref<ExpenseRecord | null>(null)
const suppliers = ref<PartnerRecord[]>([])
const projects = ref<SalesProjectSummary[]>([])
const categories = ref<ExpenseCategoryRecord[]>([])
const sources = ref<ExpenseSourceCandidateRecord[]>([])
const selectedSource = ref<ExpenseSourceCandidateRecord | null>(null)
const error = ref('')
const loading = ref(false)
const sourcesLoading = ref(false)
const submitting = ref(false)
const sourcePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const sourceFilters = reactive({ keyword: '', businessDateFrom: '', businessDateTo: '' })
const form = reactive({
  ownershipType: 'PUBLIC' as OwnershipType,
  supplierId: '' as string | number | '',
  projectId: '' as string | number | '',
  categoryId: '' as string | number | '',
  businessDate: '',
  sourceType: 'NONE' as ExpenseSourceType,
  pretaxAmount: '',
  taxRate: '',
  taxAmount: '',
  totalAmount: '',
  description: '',
  remark: '',
})

const selectedSupplierName = computed(() => suppliers.value.find((item) => String(item.id) === String(form.supplierId))?.name ?? '')
const selectedProjectName = computed(() => form.ownershipType === 'PROJECT'
  ? projects.value.find((item) => String(item.id) === String(form.projectId))?.name ?? ''
  : '')
const selectedCategoryName = computed(() => categories.value.find((item) => String(item.id) === String(form.categoryId))?.name ?? '')
const calculatedTaxAmount = computed(() => form.taxAmount.trim() || multiplyAmountByRate(form.pretaxAmount, form.taxRate) || '0.00')
const calculatedTotalAmount = computed(() => form.totalAmount.trim() || addFinanceAmounts([form.pretaxAmount || '0.00', calculatedTaxAmount.value]) || '0.00')
const saveDisabledReason = computed(() => {
  if (!form.supplierId) return '请选择供应商'
  if (form.ownershipType === 'PROJECT' && !form.projectId) return '请选择项目'
  if (!form.categoryId) return '请选择费用分类'
  if (!form.businessDate) return '请选择业务日期'
  if (!/^\d+(\.\d{1,2})?$/.test(form.pretaxAmount.trim())) return '请填写未税金额，最多两位小数'
  if (!/^\d+(\.\d{1,6})?$/.test(form.taxRate.trim())) return '请填写税率，最多六位小数'
  return ''
})

function multiplyAmountByRate(amount: string, rate: string) {
  const amountRaw = amount.trim()
  const rateRaw = rate.trim()
  if (!/^\d+(\.\d{1,2})?$/.test(amountRaw) || !/^\d+(\.\d{1,6})?$/.test(rateRaw)) {
    return null
  }
  const [amountInteger, amountDecimal = ''] = amountRaw.split('.')
  const [rateInteger, rateDecimal = ''] = rateRaw.split('.')
  const amountCents = BigInt(amountInteger || '0') * 100n + BigInt(`${amountDecimal}00`.slice(0, 2))
  const rateMicros = BigInt(rateInteger || '0') * 1000000n + BigInt(`${rateDecimal}000000`.slice(0, 6))
  const taxCents = (amountCents * rateMicros + 500000n) / 1000000n
  const integer = taxCents / 100n
  const decimal = String(taxCents % 100n).padStart(2, '0')
  return `${integer}.${decimal}`
}

function fillFromDetail(record: ExpenseRecord) {
  form.supplierId = (record as ExpenseRecord & { supplierId?: string | number }).supplierId ?? ''
  form.ownershipType = record.ownershipType
  form.projectId = record.ownershipType === 'PROJECT' ? ((record as ExpenseRecord & { projectId?: string | number | null }).projectId ?? '') : ''
  form.categoryId = (record as ExpenseRecord & { categoryId?: string | number }).categoryId ?? ''
  form.businessDate = record.businessDate
  form.sourceType = record.sourceType === 'PURCHASE_RECEIPT' || record.sourceType === 'OUTSOURCING_RECEIPT'
    ? record.sourceType
    : 'NONE'
  form.pretaxAmount = String(record.pretaxAmount ?? '')
  form.taxAmount = String(record.taxAmount ?? '')
  form.totalAmount = String(record.totalAmount ?? '')
  form.taxRate = String(record.lines?.[0]?.taxRate ?? '')
  form.description = record.lines?.[0]?.categoryName ?? ''
}

async function loadMasterData() {
  const [supplierPage, projectPage, categoryPage] = await Promise.all([
    masterDataApi.suppliers.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 200 }),
    salesProjectApi.projects.list({ keyword: '', status: 'ACTIVE', page: 1, pageSize: 200 }),
    financeExpenseApi.expenseCategories.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }),
  ])
  suppliers.value = pageItems(supplierPage)
  projects.value = pageItems(projectPage)
  categories.value = pageItems(categoryPage)
  if (!form.supplierId && suppliers.value[0]) form.supplierId = suppliers.value[0].id
  if (form.ownershipType === 'PROJECT' && !form.projectId && projects.value[0]) form.projectId = projects.value[0].id
  if (!form.categoryId && categories.value[0]) form.categoryId = categories.value[0].id
}

function selectSource(source: ExpenseSourceCandidateRecord) {
  selectedSource.value = source
  form.sourceType = source.sourceType as ExpenseSourceType
  if (source.supplierId !== undefined) form.supplierId = source.supplierId
  if (source.ownershipType) form.ownershipType = source.ownershipType
  form.projectId = source.ownershipType === 'PROJECT' ? (source.projectId ?? '') : ''
  if (source.businessDate && !form.businessDate) form.businessDate = source.businessDate
  if (source.availableAmount && !form.pretaxAmount) form.pretaxAmount = String(source.availableAmount)
}

function changeOwnership() {
  selectedSource.value = null
  if (form.ownershipType === 'PUBLIC') {
    form.projectId = ''
  } else if (!form.projectId && projects.value[0]) {
    form.projectId = projects.value[0].id
  }
  searchSources()
}

function changeSourceType() {
  selectedSource.value = null
  searchSources()
}

async function loadSources() {
  sourcesLoading.value = true
  try {
    const page = await financeExpenseApi.expenseSourceCandidates.list({
      keyword: sourceFilters.keyword,
      sourceType: form.sourceType === 'NONE' ? undefined : form.sourceType,
      supplierId: normalizeOptionalId(form.supplierId),
      ownershipType: form.ownershipType,
      projectId: form.ownershipType === 'PROJECT' ? normalizeOptionalId(form.projectId) : undefined,
      businessDateFrom: sourceFilters.businessDateFrom,
      businessDateTo: sourceFilters.businessDateTo,
      page: sourcePagination.page,
      pageSize: sourcePagination.pageSize,
    })
    sources.value = pageItems(page)
    sourcePagination.total = Number(page.total)
  } finally {
    sourcesLoading.value = false
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    if (route.name === 'finance-expense-edit') {
      detail.value = await financeExpenseApi.expenses.get(route.params.id as string)
      fillFromDetail(detail.value)
    }
    await loadMasterData()
    await loadSources()
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function searchSources() {
  sourcePagination.page = 1
  selectedSource.value = null
  void loadSources()
}

function changeSourcePage(page: number) {
  sourcePagination.page = page
  void loadSources()
}

async function save() {
  if (submitting.value || saveDisabledReason.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const payload: ExpensePayload = {
      supplierId: form.supplierId,
      ownershipType: form.ownershipType,
      projectId: form.ownershipType === 'PROJECT' ? normalizeOptionalId(form.projectId) ?? null : null,
      categoryId: form.categoryId,
      businessDate: form.businessDate,
      lines: [{
        expenseCategory: selectedCategoryName.value || undefined,
        categoryId: form.categoryId,
        description: form.description,
        sourceType: selectedSource.value?.sourceType ?? null,
        sourceId: selectedSource.value?.sourceId ?? null,
        sourceNo: selectedSource.value?.sourceNo ?? null,
        taxExcludedAmount: form.pretaxAmount.trim(),
        pretaxAmount: form.pretaxAmount.trim(),
        taxRate: form.taxRate.trim(),
        taxAmount: calculatedTaxAmount.value,
        taxIncludedAmount: calculatedTotalAmount.value,
        totalAmount: calculatedTotalAmount.value,
      }],
      remark: form.remark,
      version: detail.value?.version ?? 0,
      idempotencyKey: `expense-${Date.now()}`,
    }
    const result = detail.value
      ? await financeExpenseApi.expenses.update(route.params.id as string, payload)
      : await financeExpenseApi.expenses.create(payload)
    await router.push({ name: 'finance-expense-detail', params: { id: String(result.id) } })
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <MasterDataTableView :title="isEdit ? '编辑费用单草稿' : '新增费用单'" description="维护项目/公共供应商费用和可选采购、外协来源，不写正式项目成本。">
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="费用表单加载中" :closable="false" />
      <el-alert v-if="saveDisabledReason" type="warning" :title="saveDisabledReason" :closable="false" />
    </template>
    <div class="finance-summary-strip">
      <div><span>供应商</span><strong>{{ selectedSupplierName || '待选择' }}</strong></div>
      <div><span>项目/公共</span><strong>{{ ownershipTypeText(form.ownershipType) }} {{ selectedProjectName }}</strong></div>
      <div><span>费用分类</span><strong>{{ selectedCategoryName || '待选择' }}</strong></div>
      <div><span>来源净可用金额</span><strong>{{ formatFinanceAmount(selectedSource?.availableAmount) }}</strong></div>
      <div><span>价税合计</span><strong>{{ formatFinanceAmount(calculatedTotalAmount) }}</strong></div>
    </div>
    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="项目/公共归属">
          <el-select v-model="form.ownershipType" placeholder="选择归属" @change="changeOwnership">
            <el-option label="项目费用" value="PROJECT" />
            <el-option label="公共费用" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="form.supplierId" filterable clearable placeholder="选择供应商" @change="searchSources">
            <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="form.projectId" filterable clearable :disabled="form.ownershipType === 'PUBLIC'" placeholder="选择项目" @change="searchSources">
            <el-option v-for="project in projects" :key="project.id" :label="`${project.projectNo} ${project.name}`" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用分类">
          <el-select v-model="form.categoryId" placeholder="选择费用分类">
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker v-model="form.businessDate" name="expense-business-date" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="选择业务日期" />
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="form.sourceType" placeholder="选择来源类型" @change="changeSourceType">
            <el-option label="采购入库" value="PURCHASE_RECEIPT" />
            <el-option label="外协收货" value="OUTSOURCING_RECEIPT" />
            <el-option label="无来源" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="未税金额">
          <el-input v-model="form.pretaxAmount" name="expense-pretax-amount" clearable placeholder="0.00" />
        </el-form-item>
        <el-form-item label="税率">
          <el-input v-model="form.taxRate" name="expense-tax-rate" clearable placeholder="0.060000" />
        </el-form-item>
        <el-form-item label="税额">
          <el-input v-model="form.taxAmount" name="expense-tax-amount" clearable placeholder="留空按税率计算" />
        </el-form-item>
        <el-form-item label="价税合计">
          <el-input v-model="form.totalAmount" name="expense-total-amount" clearable placeholder="留空按未税金额和税额计算" />
        </el-form-item>
      </div>
    </el-form>
    <div class="finance-section-grid">
      <section class="finance-section">
        <span class="finance-section-title">来源候选</span>
        <el-form class="query-form" inline>
          <el-form-item label="关键词"><el-input v-model="sourceFilters.keyword" clearable placeholder="来源单号或摘要" /></el-form-item>
          <el-form-item><el-button type="primary" @click="searchSources">查询来源</el-button></el-form-item>
        </el-form>
        <div class="table-scroll">
          <el-table :data="sources" :empty-text="sourcesLoading ? '来源加载中' : '当前条件下无可用来源'" stripe>
            <el-table-column label="来源类型" min-width="120"><template #default="{ row }">{{ financeSourceTypeText(row.sourceType) }}</template></el-table-column>
            <el-table-column prop="sourceNo" label="来源单号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
            <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column label="净可用金额" min-width="120" align="right"><template #default="{ row }">{{ formatFinanceAmount(row.availableAmount) }}</template></el-table-column>
            <el-table-column label="操作" fixed="right" min-width="90">
              <template #default="{ row }">
                <el-button data-test="select-expense-source" text :type="selectedSource?.sourceNo === row.sourceNo ? 'primary' : undefined" @click="selectSource(row)">
                  {{ selectedSource?.sourceNo === row.sourceNo ? '已选' : '选择' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-pagination class="table-pagination" layout="total, prev, pager, next" :total="sourcePagination.total" :page-size="sourcePagination.pageSize" :current-page="sourcePagination.page" @current-change="changeSourcePage" />
      </section>
    </div>
    <div class="finance-form-footer">
      <el-button @click="router.back()">取消</el-button>
      <el-button data-test="save-expense" type="primary" :loading="submitting" :disabled="submitting || Boolean(saveDisabledReason)" @click="save">保存草稿</el-button>
    </div>
  </MasterDataTableView>
</template>
