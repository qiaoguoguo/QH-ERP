<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  financeInvoiceApi,
  type InvoiceType,
  type PurchaseInvoiceCandidateLine,
  type PurchaseInvoicePayload,
  type PurchaseInvoiceRecord,
  type PurchaseInvoiceSourceType,
} from '../../shared/api/financeInvoiceApi'
import type { OwnershipType } from '../../shared/api/financeStage028ApiCore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, formatFinanceAmount } from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const candidates = ref<PurchaseInvoiceCandidateLine[]>([])
const selected = ref<PurchaseInvoiceCandidateLine[]>([])
const detail = ref<PurchaseInvoiceRecord | null>(null)
const error = ref('')
const submitting = ref(false)
const form = reactive({
  invoiceDate: '',
  invoiceType: 'SPECIAL_VAT' as InvoiceType,
  externalInvoiceNo: '',
  supplierId: 9,
  sourceType: 'PURCHASE_RECEIPT' as PurchaseInvoiceSourceType,
  ownershipType: 'PUBLIC' as OwnershipType,
  remark: '',
})

async function loadData() {
  try {
    if (route.name === 'finance-purchase-invoice-edit') {
      detail.value = await financeInvoiceApi.purchaseInvoices.get(route.params.id as string)
    }
    candidates.value = pageItems(await financeInvoiceApi.purchaseInvoiceCandidates.list({
      keyword: '',
      supplierId: undefined,
      ownershipType: undefined,
      sourceType: undefined,
      businessDateFrom: '',
      businessDateTo: '',
      page: 1,
      pageSize: 50,
    }))
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  }
}

function selectLine(line: PurchaseInvoiceCandidateLine) {
  if (!selected.value.some((item) => item.sourceLineId === line.sourceLineId)) {
    selected.value = [...selected.value, line]
  }
}

async function save() {
  submitting.value = true
  try {
    const payload: PurchaseInvoicePayload = {
      invoiceDate: form.invoiceDate || '2026-08-04',
      invoiceType: form.invoiceType,
      externalInvoiceNo: form.externalInvoiceNo,
      supplierId: form.supplierId,
      sourceType: form.sourceType,
      ownershipType: form.ownershipType,
      sourceLines: selected.value.map((line) => ({
        orderLineId: line.sourceLineId,
        receiptLineId: line.sourceLineId,
        invoiceQuantity: String(line.invoiceQuantity),
        taxRate: String(line.taxRate ?? '0.130000'),
      })),
      remark: form.remark,
      version: detail.value?.version ?? 0,
      idempotencyKey: `purchase-invoice-${Date.now()}`,
    }
    const result = detail.value
      ? await financeInvoiceApi.purchaseInvoices.update(route.params.id as string, payload)
      : await financeInvoiceApi.purchaseInvoices.create(payload)
    await router.push({ name: 'finance-purchase-invoice-detail', params: { id: String(result.id) } })
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <MasterDataTableView title="新增采购发票" description="标准采购发票执行零容差三单匹配；外协结算只追溯外协来源和暂估差异。">
    <template #alerts><el-alert v-if="error" type="error" :title="error" :closable="false" /></template>
    <div class="finance-summary-strip">
      <div><span>标准采购发票</span><strong>采购订单、入库和发票逐行匹配</strong></div>
      <div><span>外协结算</span><strong>外协收货实际结算不回写库存价值</strong></div>
      <div><span>匹配状态</span><strong>保存后由后端重算</strong></div>
      <div><span>价税合计</span><strong>{{ selected.length ? formatFinanceAmount(selected[0].totalAmount) : '0.00' }}</strong></div>
    </div>
    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="发票日期"><el-date-picker v-model="form.invoiceDate" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="选择发票日期" /></el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="form.sourceType" placeholder="选择来源类型">
            <el-option label="标准采购发票" value="PURCHASE_RECEIPT" />
            <el-option label="外协结算" value="OUTSOURCING_RECEIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="外部发票号码"><el-input v-model="form.externalInvoiceNo" clearable placeholder="填写外部发票号码" /></el-form-item>
      </div>
    </el-form>
    <div class="table-scroll">
      <el-table :data="candidates" empty-text="当前条件下无可用采购或外协来源">
        <el-table-column prop="sourceNo" label="来源单号" min-width="150" />
        <el-table-column prop="materialName" label="物料" min-width="160" />
        <el-table-column prop="availableQuantity" label="可开票数量" min-width="130" align="right" />
        <el-table-column prop="invoiceQuantity" label="发票数量" min-width="120" align="right" />
        <el-table-column prop="pretaxUnitPrice" label="未税单价" min-width="120" align="right" />
        <el-table-column prop="taxRate" label="税率" min-width="100" align="right" />
        <el-table-column prop="totalAmount" label="含税金额" min-width="120" align="right" />
        <el-table-column label="操作" fixed="right" min-width="90"><template #default="{ row }"><el-button data-test="select-purchase-source-line" text @click="selectLine(row)">选择</el-button></template></el-table-column>
      </el-table>
    </div>
    <div class="finance-form-footer">
      <el-button @click="router.back()">取消</el-button>
      <el-button data-test="save-purchase-invoice" type="primary" :loading="submitting" @click="save">保存草稿</el-button>
    </div>
  </MasterDataTableView>
</template>
