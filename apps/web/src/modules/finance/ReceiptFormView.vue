<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeApi, type ReceivableDetailRecord, type ReceiptDetailRecord, type ResourceId } from '../../shared/api/financeApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import ReceivableStatusTag from './ReceivableStatusTag.vue'
import ReceiptStatusTag from './ReceiptStatusTag.vue'
import {
  compareFinanceAmount,
  financeErrorMessage,
  formatFinanceAmount,
  isPositiveFinanceAmount,
} from './financePageHelpers'

const route = useRoute()
const router = useRouter()
const receivable = ref<ReceivableDetailRecord | null>(null)
const editingRecord = ref<ReceiptDetailRecord | null>(null)
const loading = ref(true)
const formError = ref('')
const submitting = ref(false)
const form = reactive({
  receiptDate: '',
  amount: '',
  method: '',
  remark: '',
})

const isEdit = computed(() => route.name === 'finance-receipt-edit')
const targetReceivableId = computed(() => (isEdit.value ? undefined : route.params.id as ResourceId | undefined))
const pageTitle = computed(() => (isEdit.value ? '编辑收款草稿' : '登记收款'))
const isReadonlyEdit = computed(() => editingRecord.value !== null && editingRecord.value.status !== 'DRAFT')

async function loadData() {
  loading.value = true
  formError.value = ''
  try {
    if (isEdit.value) {
      const detail = await financeApi.receipts.get(route.params.id as ResourceId)
      editingRecord.value = detail
      form.receiptDate = detail.receiptDate
      form.amount = String(detail.amount)
      form.method = detail.method
      form.remark = detail.remark ?? ''
      receivable.value = await financeApi.receivables.get(detail.receivableId)
    } else if (targetReceivableId.value) {
      receivable.value = await financeApi.receivables.get(targetReceivableId.value)
    }
  } catch (caught) {
    formError.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateForm() {
  if (!receivable.value) {
    formError.value = '应收信息加载失败'
    return false
  }
  const trimmedAmount = form.amount.trim()
  if (trimmedAmount && !/^\d+(\.\d{1,2})?$/.test(trimmedAmount)) {
    formError.value = '收款金额最多保留两位小数'
    return false
  }
  if (trimmedAmount && !isPositiveFinanceAmount(trimmedAmount)) {
    formError.value = '收款金额必须大于 0'
    return false
  }
  const compared = trimmedAmount ? compareFinanceAmount(trimmedAmount, receivable.value.unreceivedAmount) : null
  if (compared !== null && compared > 0) {
    formError.value = '收款金额不能超过未收金额'
    return false
  }
  if (!form.receiptDate.trim() || !trimmedAmount || !form.method.trim()) {
    formError.value = '请完整填写收款日期、金额和方式'
    return false
  }
  formError.value = ''
  return true
}

async function saveReceipt() {
  if (submitting.value || isReadonlyEdit.value || !validateForm()) {
    return
  }
  submitting.value = true
  try {
    const payload = {
      receiptDate: form.receiptDate.trim(),
      amount: form.amount.trim(),
      method: form.method.trim(),
      ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
    }
    const result = isEdit.value
      ? await financeApi.receipts.update(route.params.id as ResourceId, payload)
      : await financeApi.receipts.create(targetReceivableId.value!, payload)
    await router.push({ name: 'finance-receipt-detail', params: { id: String(result.id) } })
  } catch (caught) {
    formError.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

function cancel() {
  if (editingRecord.value) {
    void router.push({ name: 'finance-receipt-detail', params: { id: String(editingRecord.value.id) } })
    return
  }
  if (receivable.value) {
    void router.push({ name: 'finance-receivable-detail', params: { id: String(receivable.value.id) } })
  }
}

onMounted(loadData)
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="登记业务收款草稿，过账前不更新应收余额。">
    <template #alerts>
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="收款表单加载中" :closable="false" />
      <el-alert v-if="isReadonlyEdit" class="state-alert" type="warning" title="非草稿收款只读" :closable="false" />
    </template>

    <div v-if="receivable" class="summary-strip">
      <div><span>应收单号</span><strong>{{ receivable.receivableNo }}</strong></div>
      <div><span>客户</span><strong>{{ receivable.customerName }}</strong></div>
      <div><span>应收金额</span><strong>{{ formatFinanceAmount(receivable.totalAmount) }}</strong></div>
      <div><span>已收金额</span><strong>{{ formatFinanceAmount(receivable.receivedAmount) }}</strong></div>
      <div><span>未收金额</span><strong>{{ formatFinanceAmount(receivable.unreceivedAmount) }}</strong></div>
      <div><span>状态</span><ReceivableStatusTag :status="receivable.status" /></div>
      <div><span>来源销售出库</span><strong>{{ receivable.sourceNo }}</strong></div>
      <div><span>来源销售订单</span><strong>{{ receivable.salesOrderNo }}</strong></div>
    </div>
    <div v-if="editingRecord" class="edit-status">
      <span>{{ editingRecord.receiptNo }}</span>
      <ReceiptStatusTag :status="editingRecord.status" />
    </div>

    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="收款日期">
          <el-input v-model="form.receiptDate" name="receipt-date" placeholder="YYYY-MM-DD" :disabled="isReadonlyEdit" />
        </el-form-item>
        <el-form-item label="收款金额">
          <el-input v-model="form.amount" name="receipt-amount" placeholder="0.00" :disabled="isReadonlyEdit" />
        </el-form-item>
        <el-form-item label="收款方式">
          <el-input v-model="form.method" name="receipt-method" placeholder="BANK_TRANSFER" :disabled="isReadonlyEdit" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="receipt-remark" placeholder="可选" :disabled="isReadonlyEdit" />
        </el-form-item>
      </div>
    </el-form>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button data-test="save-receipt" type="primary" :loading="submitting" :disabled="submitting || isReadonlyEdit" @click="saveReceipt">
        保存收款
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
