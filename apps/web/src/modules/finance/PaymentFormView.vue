<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { queryWithReturnTo, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { financeApi, type PayableDetailRecord, type PaymentDetailRecord, type ResourceId } from '../../shared/api/financeApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import PayableStatusTag from './PayableStatusTag.vue'
import PaymentStatusTag from './PaymentStatusTag.vue'
import {
  compareFinanceAmount,
  financeErrorMessage,
  formatFinanceAmount,
  isPositiveFinanceAmount,
} from './financePageHelpers'

const route = useRoute()
const router = useRouter()
const payable = ref<PayableDetailRecord | null>(null)
const editingRecord = ref<PaymentDetailRecord | null>(null)
const loading = ref(true)
const formError = ref('')
const submitting = ref(false)
const form = reactive({
  paymentDate: '',
  amount: '',
  method: '',
  remark: '',
})

const isEdit = computed(() => route.name === 'finance-payment-edit')
const targetPayableId = computed(() => (isEdit.value ? undefined : route.params.id as ResourceId | undefined))
const pageTitle = computed(() => (isEdit.value ? '编辑付款草稿' : '登记付款'))
const isReadonlyEdit = computed(() => editingRecord.value !== null && editingRecord.value.status !== 'DRAFT')

async function loadData() {
  loading.value = true
  formError.value = ''
  try {
    if (isEdit.value) {
      const detail = await financeApi.payments.get(route.params.id as ResourceId)
      editingRecord.value = detail
      form.paymentDate = detail.paymentDate
      form.amount = String(detail.amount)
      form.method = detail.method
      form.remark = detail.remark ?? ''
      payable.value = await financeApi.payables.get(detail.payableId)
    } else if (targetPayableId.value) {
      payable.value = await financeApi.payables.get(targetPayableId.value)
    }
  } catch (caught) {
    formError.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateForm() {
  if (!payable.value) {
    formError.value = '应付信息加载失败'
    return false
  }
  const trimmedAmount = form.amount.trim()
  if (trimmedAmount && !/^\d+(\.\d{1,2})?$/.test(trimmedAmount)) {
    formError.value = '付款金额最多保留两位小数'
    return false
  }
  if (trimmedAmount && !isPositiveFinanceAmount(trimmedAmount)) {
    formError.value = '付款金额必须大于 0'
    return false
  }
  const compared = trimmedAmount ? compareFinanceAmount(trimmedAmount, payable.value.unpaidAmount) : null
  if (compared !== null && compared > 0) {
    formError.value = '付款金额不能超过未付金额'
    return false
  }
  if (!form.paymentDate.trim() || !trimmedAmount || !form.method.trim()) {
    formError.value = '请完整填写付款日期、金额和方式'
    return false
  }
  formError.value = ''
  return true
}

async function savePayment() {
  if (submitting.value || isReadonlyEdit.value || !validateForm()) {
    return
  }
  submitting.value = true
  try {
    const payload = {
      paymentDate: form.paymentDate.trim(),
      amount: form.amount.trim(),
      method: form.method.trim(),
      ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    }
    const result = isEdit.value
      ? await financeApi.payments.update(route.params.id as ResourceId, payload)
      : await financeApi.payments.create(targetPayableId.value!, payload)
    await router.push({
      name: 'finance-payment-detail',
      params: { id: String(result.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
  } catch (caught) {
    formError.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

function cancel() {
  if (editingRecord.value) {
    void router.push({
      name: 'finance-payment-detail',
      params: { id: String(editingRecord.value.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
    return
  }
  if (payable.value) {
    void router.push({
      name: 'finance-payable-detail',
      params: { id: String(payable.value.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
  }
}

onMounted(loadData)
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="登记业务付款草稿，过账前不更新应付余额。">
    <template #alerts>
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="付款表单加载中" :closable="false" />
      <el-alert v-if="isReadonlyEdit" class="state-alert" type="warning" title="非草稿付款只读" :closable="false" />
    </template>

    <div v-if="payable" class="summary-strip">
      <div><span>应付单号</span><strong>{{ payable.payableNo }}</strong></div>
      <div><span>供应商</span><strong>{{ payable.supplierName }}</strong></div>
      <div><span>应付金额</span><strong>{{ formatFinanceAmount(payable.totalAmount) }}</strong></div>
      <div><span>已付金额</span><strong>{{ formatFinanceAmount(payable.paidAmount) }}</strong></div>
      <div><span>未付金额</span><strong>{{ formatFinanceAmount(payable.unpaidAmount) }}</strong></div>
      <div><span>状态</span><PayableStatusTag :status="payable.status" /></div>
      <div><span>来源采购入库</span><strong>{{ payable.sourceNo }}</strong></div>
      <div><span>来源采购订单</span><strong>{{ payable.purchaseOrderNo }}</strong></div>
    </div>
    <div v-if="editingRecord" class="edit-status">
      <span>{{ editingRecord.paymentNo }}</span>
      <PaymentStatusTag :status="editingRecord.status" />
    </div>

    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="付款日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.paymentDate" name="payment-date" placeholder="选择日期" :disabled="isReadonlyEdit" />
        </el-form-item>
        <el-form-item label="付款金额">
          <el-input v-model="form.amount" name="payment-amount" placeholder="0.00" :disabled="isReadonlyEdit" />
        </el-form-item>
        <el-form-item label="付款方式">
          <el-input v-model="form.method" name="payment-method" placeholder="BANK_TRANSFER" :disabled="isReadonlyEdit" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="payment-remark" placeholder="可选" :disabled="isReadonlyEdit" />
        </el-form-item>
      </div>
    </el-form>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button data-test="save-payment" type="primary" :loading="submitting" :disabled="submitting || isReadonlyEdit" @click="savePayment">
        保存付款
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  padding: 14px;
}

.summary-strip > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  padding: 10px 12px;
}

.summary-strip span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.finance-form {
  padding: 14px;
}

.finance-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.edit-status {
  align-items: center;
  display: flex;
  gap: 8px;
  padding: 0 14px;
}

.form-footer {
  border-top: 1px solid var(--qherp-border);
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 12px 14px 14px;
}

@media (max-width: 900px) {
  .summary-strip,
  .finance-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
