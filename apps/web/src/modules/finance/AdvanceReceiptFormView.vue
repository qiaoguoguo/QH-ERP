<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { financeSettlementApi } from '../../shared/api/financeSettlementApi'
import type { OwnershipType } from '../../shared/api/financeStage028ApiCore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { financeErrorMessage } from './financePageHelpers'
import './Finance028Shared.css'

const router = useRouter()
const error = ref('')
const submitting = ref(false)
const form = reactive<{ businessDate: string; amount: string; method: string; partnerId: number; ownershipType: OwnershipType; projectId: number }>({
  businessDate: '',
  amount: '500.00',
  method: 'BANK_TRANSFER',
  partnerId: 8,
  ownershipType: 'PROJECT',
  projectId: 18,
})

async function save() {
  submitting.value = true
  try {
    const result = await financeSettlementApi.advanceReceipts.create({
      partnerId: form.partnerId,
      ownershipType: form.ownershipType,
      projectId: form.projectId,
      businessDate: form.businessDate,
      amount: form.amount,
      method: form.method,
      allocations: [],
      version: 0,
      idempotencyKey: `advance-receipt-${Date.now()}`,
    })
    await router.push({ name: 'finance-advance-receipt-detail', params: { id: String(result.id) } })
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <MasterDataTableView title="登记预收款" description="登记真实收款资金草稿，可保留未核销余额并后续发起核销。">
    <template #alerts><el-alert v-if="error" type="error" :title="error" :closable="false" /></template>
    <div class="finance-summary-strip"><div><span>往来方</span><strong>华东客户</strong></div><div><span>项目/公共归属</span><strong>项目 A</strong></div><div><span>分配摘要</span><strong>零到多个目标</strong></div><div><span>未核销余额</span><strong>{{ form.amount }}</strong></div></div>
    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="业务日期"><el-date-picker v-model="form.businessDate" name="advance-business-date" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="选择业务日期" /></el-form-item>
        <el-form-item label="资金金额"><el-input v-model="form.amount" name="advance-amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="资金方式">
          <el-select v-model="form.method" data-test="fund-method-select" placeholder="选择资金方式">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
          </el-select>
        </el-form-item>
      </div>
    </el-form>
    <div class="finance-form-footer"><el-button @click="router.back()">取消</el-button><el-button data-test="save-advance-receipt" type="primary" :loading="submitting" @click="save">保存预收款</el-button></div>
  </MasterDataTableView>
</template>
