<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeExpenseApi, type ExpenseCategoryRecord, type ExpensePayload, type ExpenseRecord, type ExpenseSourceCandidateRecord, type ExpenseSourceType } from '../../shared/api/financeExpenseApi'
import type { OwnershipType } from '../../shared/api/financeStage028ApiCore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage } from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const detail = ref<ExpenseRecord | null>(null)
const categories = ref<ExpenseCategoryRecord[]>([])
const sources = ref<ExpenseSourceCandidateRecord[]>([])
const error = ref('')
const submitting = ref(false)
const form = reactive({
  ownershipType: 'PUBLIC' as OwnershipType,
  supplierId: 9,
  projectId: 18,
  categoryId: 3,
  businessDate: '',
  sourceType: 'OUTSOURCING_RECEIPT' as ExpenseSourceType,
  remark: '',
})

async function loadData() {
  try {
    if (route.name === 'finance-expense-edit') {
      detail.value = await financeExpenseApi.expenses.get(route.params.id as string)
    }
    categories.value = pageItems(await financeExpenseApi.expenseCategories.list({ keyword: '', status: 'ENABLED', page: 1, pageSize: 100 }))
    sources.value = pageItems(await financeExpenseApi.expenseSourceCandidates.list({
      keyword: '',
      sourceType: form.sourceType,
      supplierId: form.supplierId,
      ownershipType: form.ownershipType,
      projectId: form.projectId,
      businessDateFrom: '',
      businessDateTo: '',
      page: 1,
      pageSize: 50,
    }))
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  }
}

async function save() {
  submitting.value = true
  try {
    const payload: ExpensePayload = {
      supplierId: form.supplierId,
      ownershipType: form.ownershipType,
      projectId: form.ownershipType === 'PROJECT' ? form.projectId : null,
      categoryId: form.categoryId,
      businessDate: form.businessDate || '2026-08-05',
      lines: [{ categoryId: form.categoryId, pretaxAmount: '100.00', taxRate: '0.060000', taxAmount: '6.00', totalAmount: '106.00' }],
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
  <MasterDataTableView title="新增费用单" description="维护项目/公共供应商费用和可选采购、外协来源，不写正式项目成本。">
    <template #alerts><el-alert v-if="error" type="error" :title="error" :closable="false" /></template>
    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="项目/公共归属">
          <el-select v-model="form.ownershipType" placeholder="选择归属">
            <el-option label="项目费用" value="PROJECT" />
            <el-option label="公共费用" value="PUBLIC" />
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
      </div>
    </el-form>
    <div class="finance-section-grid">
      <section class="finance-section">
        <span class="finance-section-title">来源候选</span>
        <p v-if="sources.length === 0">当前条件下无可用来源</p>
        <p v-for="source in sources" :key="source.sourceNo">{{ source.summary ?? source.sourceNo }}</p>
      </section>
    </div>
    <div class="finance-form-footer">
      <el-button @click="router.back()">取消</el-button>
      <el-button data-test="save-expense" type="primary" :loading="submitting" @click="save">保存草稿</el-button>
    </div>
  </MasterDataTableView>
</template>
