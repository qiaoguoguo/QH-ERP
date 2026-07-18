<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { financeInvoiceApi, type InvoiceType, type SalesInvoiceCandidateLine, type SalesInvoicePayload, type SalesInvoiceRecord } from '../../shared/api/financeInvoiceApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import { financeErrorMessage, formatFinanceAmount } from './financePageHelpers'
import './Finance028Shared.css'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => route.name === 'finance-sales-invoice-edit')
const candidates = ref<SalesInvoiceCandidateLine[]>([])
const selected = ref<SalesInvoiceCandidateLine[]>([])
const detail = ref<SalesInvoiceRecord | null>(null)
const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const form = reactive({
  invoiceDate: '',
  invoiceType: 'SPECIAL_VAT' as InvoiceType,
  externalInvoiceNo: '',
  customerId: 8,
  ownershipType: 'PROJECT' as const,
  projectId: 18,
  remark: '',
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    if (isEdit.value) {
      detail.value = await financeInvoiceApi.salesInvoices.get(route.params.id as string)
      form.invoiceDate = detail.value.invoiceDate
      form.externalInvoiceNo = detail.value.externalInvoiceNo ?? ''
    }
    candidates.value = pageItems(await financeInvoiceApi.salesInvoiceCandidates.list({
      keyword: '',
      customerId: undefined,
      projectId: undefined,
      contractNo: '',
      orderNo: '',
      shipmentDateFrom: '',
      shipmentDateTo: '',
      page: 1,
      pageSize: 50,
    }))
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function selectSourceLine(line: SalesInvoiceCandidateLine) {
  if (!selected.value.some((item) => item.sourceLineId === line.sourceLineId)) {
    selected.value = [...selected.value, line]
  }
}

async function save() {
  if (submitting.value) {
    return
  }
  submitting.value = true
  try {
    const payload: SalesInvoicePayload = {
      invoiceDate: form.invoiceDate,
      invoiceType: form.invoiceType,
      externalInvoiceNo: form.externalInvoiceNo,
      customerId: form.customerId,
      ownershipType: form.ownershipType,
      projectId: form.projectId,
      sourceLines: selected.value.map((line) => ({
        sourceLineId: line.sourceLineId,
        invoiceQuantity: String(line.invoiceQuantity),
      })),
      remark: form.remark,
      version: detail.value?.version ?? 0,
      idempotencyKey: `sales-invoice-${Date.now()}`,
    }
    const result = isEdit.value
      ? await financeInvoiceApi.salesInvoices.update(route.params.id as string, payload)
      : await financeInvoiceApi.salesInvoices.create(payload)
    await router.push({ name: 'finance-sales-invoice-detail', params: { id: String(result.id) } })
  } catch (caught) {
    error.value = financeErrorMessage(caught)
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <MasterDataTableView :title="isEdit ? '编辑销售发票草稿' : '新增销售发票'" description="选择已过账销售出库来源，按税价快照形成销售发票草稿。">
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" type="info" title="销售发票表单加载中" :closable="false" />
    </template>

    <div class="finance-summary-strip">
      <div><span>客户</span><strong>华东客户</strong></div>
      <div><span>项目/公共归属</span><strong>项目 A</strong></div>
      <div><span>来源出库净可开票余额</span><strong>{{ candidates.length ? formatFinanceAmount(candidates[0].totalAmount) : '0.00' }}</strong></div>
      <div><span>本次价税合计</span><strong>{{ selected.length ? formatFinanceAmount(selected[0].totalAmount) : '0.00' }}</strong></div>
    </div>

    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="开票日期">
          <el-date-picker v-model="form.invoiceDate" name="sales-invoice-date" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="选择开票日期" />
        </el-form-item>
        <el-form-item label="外部发票号码">
          <el-input v-model="form.externalInvoiceNo" name="sales-invoice-external-no" clearable placeholder="填写外部发票号码" />
        </el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="form.invoiceType" placeholder="选择发票类型">
            <el-option label="增值税专用发票" value="SPECIAL_VAT" />
            <el-option label="增值税普通发票" value="NORMAL_VAT" />
          </el-select>
        </el-form-item>
      </div>
    </el-form>

    <div class="table-scroll">
      <el-table :data="candidates" empty-text="当前条件下无可用销售出库来源" stripe>
        <el-table-column prop="sourceNo" label="出库号" min-width="150" />
        <el-table-column prop="lineNo" label="行号" min-width="80" />
        <el-table-column prop="materialName" label="物料" min-width="160" show-overflow-tooltip />
        <el-table-column prop="unitName" label="单位" min-width="80" />
        <el-table-column prop="availableQuantity" label="可开票数量" min-width="130" align="right" />
        <el-table-column prop="invoicedQuantity" label="已开票数量" min-width="130" align="right" />
        <el-table-column prop="invoiceQuantity" label="本次数量" min-width="120" align="right" />
        <el-table-column prop="pretaxUnitPrice" label="未税单价" min-width="120" align="right" />
        <el-table-column prop="taxRate" label="税率" min-width="100" align="right" />
        <el-table-column prop="totalAmount" label="含税金额" min-width="120" align="right" />
        <el-table-column label="操作" fixed="right" min-width="90">
          <template #default="{ row }">
            <el-button data-test="select-source-line" text @click="selectSourceLine(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="finance-form-footer">
      <el-button @click="router.back()">取消</el-button>
      <el-button data-test="save-sales-invoice" type="primary" :loading="submitting" :disabled="submitting || selected.length === 0" @click="save">保存草稿</el-button>
    </div>
  </MasterDataTableView>
</template>
