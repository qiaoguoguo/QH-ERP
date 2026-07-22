<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { queryWithReturnTo, routeReturnTo } from '../../shared/navigation/navigationReturn'
import {
  financeApi,
  type ReceivableCandidateSource,
  type ReceivableDetailRecord,
  type ResourceId,
} from '../../shared/api/financeApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import ReceivableStatusTag from './ReceivableStatusTag.vue'
import { financeErrorMessage, formatFinanceAmount } from './financePageHelpers'

const route = useRoute()
const router = useRouter()
const candidates = ref<ReceivableCandidateSource[]>([])
const editingRecord = ref<ReceivableDetailRecord | null>(null)
const loading = ref(true)
const candidateLoading = ref(false)
const formError = ref('')
const submitting = ref(false)
const sourceFilters = reactive({
  keyword: '',
  customerId: '' as ResourceId | '',
  dateFrom: '',
  dateTo: '',
})
const form = reactive({
  sourceId: '' as ResourceId | '',
  dueDate: '',
  remark: '',
})

const isEdit = computed(() => Boolean(route.params.id))
const selectedCandidate = computed(() => candidates.value.find((candidate) => String(candidate.sourceId) === String(form.sourceId)))
const pageTitle = computed(() => (isEdit.value ? '编辑应收草稿' : '基于销售出库生成应收'))
const isReadonlyEdit = computed(() => editingRecord.value !== null && editingRecord.value.status !== 'DRAFT')
const canSubmit = computed(() => !submitting.value && !isReadonlyEdit.value)

async function loadCandidates() {
  candidateLoading.value = true
  formError.value = ''
  try {
    candidates.value = pageItems(await financeApi.sources.receivableCandidates.list({
      keyword: sourceFilters.keyword,
      customerId: sourceFilters.customerId === '' ? undefined : sourceFilters.customerId,
      dateFrom: sourceFilters.dateFrom,
      dateTo: sourceFilters.dateTo,
      settlementGenerated: false,
      page: 1,
      pageSize: 20,
    }))
  } catch (caught) {
    candidates.value = []
    formError.value = financeErrorMessage(caught)
  } finally {
    candidateLoading.value = false
  }
}

async function loadRecord() {
  if (!route.params.id) {
    return
  }
  loading.value = true
  formError.value = ''
  try {
    const detail = await financeApi.receivables.get(route.params.id as ResourceId)
    editingRecord.value = detail
    form.dueDate = detail.dueDate
    form.remark = detail.remark ?? ''
  } catch (caught) {
    formError.value = financeErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function validateForm() {
  if (!isEdit.value && !selectedCandidate.value) {
    formError.value = '请选择来源销售出库'
    return false
  }
  if (!form.dueDate.trim()) {
    formError.value = '请填写到期日期'
    return false
  }
  formError.value = ''
  return true
}

async function saveReceivable() {
  if (!canSubmit.value || !validateForm()) {
    return
  }
  submitting.value = true
  try {
    let result: ReceivableDetailRecord
    if (isEdit.value) {
      result = await financeApi.receivables.update(route.params.id as ResourceId, {
        dueDate: form.dueDate.trim(),
        ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
      })
    } else {
      result = await financeApi.receivables.create({
        sourceType: 'SALES_SHIPMENT',
        sourceId: selectedCandidate.value!.sourceId,
        dueDate: form.dueDate.trim(),
        ...(form.remark.trim() ? { remark: form.remark.trim() } : {}),
      })
    }
    await router.push({
      name: 'finance-receivable-detail',
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
      name: 'finance-receivable-detail',
      params: { id: String(editingRecord.value.id) },
      query: queryWithReturnTo({}, routeReturnTo(route)),
    })
    return
  }
  void router.push({ name: 'finance-receivables' })
}

onMounted(async () => {
  if (isEdit.value) {
    await loadRecord()
  } else {
    await loadCandidates()
    loading.value = false
  }
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" description="来源和金额只读，前端只维护到期日期和备注。">
    <template #alerts>
      <el-alert v-if="formError" class="state-alert" type="error" :title="formError" :closable="false" />
      <el-alert v-if="loading || candidateLoading" class="state-alert" type="info" title="应收表单加载中" :closable="false" />
      <el-alert v-if="editingRecord && editingRecord.status !== 'DRAFT'" class="state-alert" type="warning" title="非草稿应收不可编辑" :closable="false" />
    </template>

    <div v-if="!isEdit" class="source-query">
      <el-form class="query-form">
        <el-form-item label="候选来源">
          <el-input v-model="sourceFilters.keyword" name="receivable-source-keyword" placeholder="销售出库、销售订单、客户" />
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="sourceFilters.dateFrom" name="receivable-source-date-from" placeholder="起始日期" />
        </el-form-item>
        <el-form-item>
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="sourceFilters.dateTo" name="receivable-source-date-to" placeholder="截止日期" />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-receivable-sources" @click="loadCandidates">查询来源</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div v-if="!isEdit" class="source-summary">
      <el-form label-position="top">
        <el-form-item label="来源销售出库">
          <el-select v-model="form.sourceId" data-test="receivable-source-id" filterable placeholder="选择已过账且未生成应收的销售出库" style="width: 100%">
            <el-option
              v-for="candidate in candidates"
              :key="candidate.sourceId"
              :label="`${candidate.sourceNo} / ${candidate.salesOrderNo} / ${candidate.customerName}`"
              :value="candidate.sourceId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table v-if="candidates.length" :data="candidates" size="small" class="candidate-table table-scroll">
        <el-table-column prop="sourceNo" label="销售出库" min-width="130" />
        <el-table-column prop="salesOrderNo" label="销售订单" min-width="130" />
        <el-table-column prop="customerName" label="客户" min-width="150" />
        <el-table-column prop="businessDate" label="业务日期" width="120" />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            {{ formatFinanceAmount(row.totalAmount) }}
          </template>
        </el-table-column>
        <el-table-column prop="lineCount" label="明细数量" width="90" />
      </el-table>
      <div v-if="selectedCandidate" class="summary-strip">
        <div><span>来源销售出库</span><strong>{{ selectedCandidate.sourceNo }}</strong></div>
        <div><span>来源销售订单</span><strong>{{ selectedCandidate.salesOrderNo }}</strong></div>
        <div><span>客户</span><strong>{{ selectedCandidate.customerName }}</strong></div>
        <div><span>业务日期</span><strong>{{ selectedCandidate.businessDate }}</strong></div>
        <div><span>来源金额</span><strong>{{ formatFinanceAmount(selectedCandidate.totalAmount) }}</strong></div>
        <div><span>明细数量</span><strong>{{ selectedCandidate.lineCount }}</strong></div>
      </div>
      <el-empty v-else description="请选择来源销售出库" />
    </div>

    <div v-if="editingRecord" class="summary-strip">
      <div><span>应收单号</span><strong>{{ editingRecord.receivableNo }}</strong></div>
      <div><span>来源销售出库</span><strong>{{ editingRecord.sourceNo }}</strong></div>
      <div><span>来源销售订单</span><strong>{{ editingRecord.salesOrderNo }}</strong></div>
      <div><span>客户</span><strong>{{ editingRecord.customerName }}</strong></div>
      <div><span>应收金额</span><strong>{{ formatFinanceAmount(editingRecord.totalAmount) }}</strong></div>
      <div><span>状态</span><ReceivableStatusTag :status="editingRecord.status" /></div>
    </div>

    <el-form label-position="top" class="finance-form">
      <div class="finance-form-grid">
        <el-form-item label="到期日期">
          <el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.dueDate" name="receivable-due-date" placeholder="选择日期" :disabled="isReadonlyEdit" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="receivable-remark" placeholder="可选" :disabled="isReadonlyEdit" />
        </el-form-item>
      </div>
    </el-form>

    <div class="form-footer">
      <el-button @click="cancel">取消</el-button>
      <el-button data-test="save-receivable" type="primary" :loading="submitting" :disabled="!canSubmit" @click="saveReceivable">
        保存应收
      </el-button>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.source-query,
.source-summary,
.finance-form {
  padding: 14px 14px 0;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 14px;
}

.candidate-table {
  margin-bottom: 12px;
}

.summary-strip > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  min-width: 0;
  padding: 10px 12px;
}

.summary-strip span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.summary-strip strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.finance-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
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
