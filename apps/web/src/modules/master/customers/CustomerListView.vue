<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  masterDataApi,
  type InvoiceType,
  type MasterDataStatus,
  type PartnerPayload,
  type PartnerRecord,
  type SettlementMethod,
  type SettlementTaxPayload,
  type SettlementTaxRecord,
} from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusTagType } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../shared/MasterDataTableView.vue'
import BatchStatusToolPanel from '../../platform/components/BatchStatusToolPanel.vue'
import { invoiceTypeLabel, masterStatusLabel, percentLabel, settlementMethodLabel } from '../shared/masterPageHelpers'
import { confirmAction } from '../../../shared/ui/confirmDialog'

const authStore = useAuthStore()
const invoiceTypeOptions: Array<{ label: string; value: InvoiceType }> = [
  { label: '增值税普通发票', value: 'GENERAL_VAT' },
  { label: '增值税专用发票', value: 'SPECIAL_VAT' },
  { label: '不开票', value: 'NONE' },
]
const settlementMethodOptions: Array<{ label: string; value: SettlementMethod }> = [
  { label: '月结', value: 'MONTHLY' },
  { label: '货到付款', value: 'CASH_ON_DELIVERY' },
  { label: '预付', value: 'ADVANCE' },
  { label: '自定义', value: 'CUSTOM' },
]

const filters = reactive<{
  keyword: string
  status?: MasterDataStatus
}>({
  keyword: '',
  status: undefined,
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const records = ref<PartnerRecord[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const formVisible = ref(false)
const formSubmitting = ref(false)
const codeGenerating = ref(false)
const formError = ref('')
const editingRecord = ref<PartnerRecord | null>(null)
const settlementVisible = ref(false)
const settlementSubmitting = ref(false)
const settlementError = ref('')
const settlementRecord = ref<SettlementTaxRecord | null>(null)
const settlementTarget = ref<PartnerRecord | null>(null)
const form = reactive({
  code: '',
  name: '',
  contactName: '',
  contactPhone: '',
  status: 'ENABLED' as MasterDataStatus,
  remark: '',
})
const settlementForm = reactive({
  invoiceTitle: '',
  taxNo: '',
  registeredAddress: '',
  registeredPhone: '',
  bankName: '',
  bankAccount: '',
  defaultTaxRate: '',
  invoiceType: 'NONE' as InvoiceType,
  settlementMethod: 'MONTHLY' as SettlementMethod,
  paymentTermDays: '',
  paymentTerms: '',
  remark: '',
})

const canCreate = computed(() => authStore.hasPermission('master:customer:create'))
const canUpdate = computed(() => authStore.hasPermission('master:customer:update'))
const canGenerateCode = computed(() => canCreate.value && authStore.hasPermission('master:coding-rule:generate'))
const canSettlementView = computed(() => authStore.hasPermission('master:customer-settlement:view'))
const canSettlementUpdate = computed(() => authStore.hasPermission('master:customer-settlement:update'))
const canSettlementSensitiveUpdate = computed(() => authStore.hasPermission('master:customer-settlement:sensitive-update'))

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await masterDataApi.customers.list({
      keyword: filters.keyword,
      status: filters.status,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function resetSettlementForm(record: SettlementTaxRecord) {
  Object.assign(settlementForm, {
    invoiceTitle: record.invoiceTitle ?? '',
    taxNo: record.taxNo ?? '',
    registeredAddress: record.registeredAddress ?? '',
    registeredPhone: record.registeredPhone ?? '',
    bankName: record.bankName ?? '',
    bankAccount: record.bankAccount ?? '',
    defaultTaxRate: record.defaultTaxRate ?? '',
    invoiceType: record.invoiceType ?? 'NONE',
    settlementMethod: record.settlementMethod ?? 'MONTHLY',
    paymentTermDays: record.paymentTermDays === null || record.paymentTermDays === undefined ? '' : String(record.paymentTermDays),
    paymentTerms: record.paymentTerms ?? '',
    remark: record.remark ?? '',
  })
  settlementError.value = ''
}

async function openSettlement(record: PartnerRecord) {
  settlementTarget.value = record
  settlementRecord.value = null
  settlementError.value = ''
  settlementVisible.value = true
  try {
    const detail = await masterDataApi.customers.getSettlementTax(record.id)
    settlementRecord.value = detail
    resetSettlementForm(detail)
  } catch (caught) {
    settlementError.value = errorMessage(caught)
  }
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function validateSettlementForm(): SettlementTaxPayload | null {
  if (!settlementRecord.value) {
    settlementError.value = '结算税务资料尚未加载完成'
    return null
  }
  const paymentTermDays = settlementForm.paymentTermDays.trim()
    ? Number(settlementForm.paymentTermDays)
    : null
  if (paymentTermDays !== null && (!Number.isInteger(paymentTermDays) || paymentTermDays < 0)) {
    settlementError.value = '账期天数必须为非负整数'
    return null
  }
  const defaultTaxRate = settlementForm.defaultTaxRate.trim()
  if (defaultTaxRate && (!Number.isFinite(Number(defaultTaxRate)) || Number(defaultTaxRate) < 0)) {
    settlementError.value = '默认税率必须为非负 decimal 字符串'
    return null
  }
  const payload: SettlementTaxPayload = {
    invoiceTitle: settlementForm.invoiceTitle.trim() || null,
    defaultTaxRate: defaultTaxRate || null,
    invoiceType: settlementForm.invoiceType,
    settlementMethod: settlementForm.settlementMethod,
    paymentTermDays,
    paymentTerms: settlementForm.paymentTerms.trim() || null,
    remark: settlementForm.remark.trim() || null,
    version: settlementRecord.value.version,
  }
  if (canSettlementSensitiveUpdate.value) {
    payload.taxNo = settlementForm.taxNo.trim() || null
    payload.registeredAddress = settlementForm.registeredAddress.trim() || null
    payload.registeredPhone = settlementForm.registeredPhone.trim() || null
    payload.bankName = settlementForm.bankName.trim() || null
    payload.bankAccount = settlementForm.bankAccount.trim() || null
  }
  settlementError.value = ''
  return payload
}

async function saveSettlement() {
  if (!settlementTarget.value || settlementSubmitting.value) {
    return
  }
  const payload = validateSettlementForm()
  if (!payload) {
    return
  }
  settlementSubmitting.value = true
  try {
    settlementRecord.value = await masterDataApi.customers.updateSettlementTax(settlementTarget.value.id, payload)
    settlementVisible.value = false
    await loadRecords()
  } catch (caught) {
    settlementError.value = errorMessage(caught)
  } finally {
    settlementSubmitting.value = false
  }
}

function settlementSummaryText(record: PartnerRecord): string {
  const summary = record.settlementTaxSummary
  if (!summary || !summary.hasData) {
    return '未维护'
  }
  return [
    summary.taxNoMasked || '无税号',
    percentLabel(summary.defaultTaxRate),
    settlementMethodLabel(summary.settlementMethod),
    summary.paymentTermDays === null || summary.paymentTermDays === undefined ? '无账期' : `${summary.paymentTermDays}天`,
  ].join(' / ')
}

function resetSearch() {
  filters.keyword = ''
  filters.status = undefined
  pagination.page = 1
  void loadRecords()
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

function resetForm(record?: PartnerRecord) {
  Object.assign(form, {
    code: record?.code ?? '',
    name: record?.name ?? '',
    contactName: record?.contactName ?? '',
    contactPhone: record?.contactPhone ?? '',
    status: record?.status ?? 'ENABLED',
    remark: record?.remark ?? '',
  })
  formError.value = ''
}

function openCreate() {
  editingRecord.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(record: PartnerRecord) {
  editingRecord.value = record
  resetForm(record)
  formVisible.value = true
}

function validateForm() {
  if (!form.code.trim() || !form.name.trim()) {
    formError.value = '请完整填写编码和名称'
    return false
  }
  formError.value = ''
  return true
}

async function saveRecord() {
  if (formSubmitting.value) {
    return
  }
  if (!validateForm()) {
    return
  }

  const payload: PartnerPayload = {
    code: form.code.trim(),
    name: form.name.trim(),
    contactName: form.contactName.trim(),
    contactPhone: form.contactPhone.trim(),
    status: form.status,
    remark: form.remark.trim(),
  }

  formSubmitting.value = true
  try {
    if (editingRecord.value) {
      await masterDataApi.customers.update(editingRecord.value.id, payload)
    } else {
      await masterDataApi.customers.create(payload)
    }
    formVisible.value = false
    await loadRecords()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

async function generateCode() {
  if (codeGenerating.value) {
    return
  }
  formError.value = ''
  codeGenerating.value = true
  try {
    const result = await masterDataApi.codingRules.generate({ objectType: 'CUSTOMER' })
    form.code = result.generatedCode
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    codeGenerating.value = false
  }
}

async function changeStatus(record: PartnerRecord) {
  const nextAction = record.status === 'DISABLED' ? '启用' : '停用'
  if (!(await confirmAction(`确认${nextAction}客户“${record.name}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    if (record.status === 'DISABLED') {
      await masterDataApi.customers.enable(record.id)
    } else {
      await masterDataApi.customers.disable(record.id)
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="客户" description="维护销售订单和发货业务使用的客户基础资料。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-record" type="primary" @click="openCreate">新增客户</el-button>
      <a
        v-if="authStore.hasPermission('platform:history-import:view') || authStore.hasPermission('platform:history-import:create')"
        data-test="customer-history-import-entry"
        class="inline-action-link"
        href="/platform/history-imports?adapterCode=CUSTOMER_MASTER_V1"
      >
        历史导入
      </a>
      <BatchStatusToolPanel
        v-if="authStore.hasPermission('platform:batch-tool:view')"
        tool-code="CUSTOMER_STATUS_CHANGE_V1"
        title="客户批量状态"
        button-test-id="customer-batch-status-entry"
        :default-candidates="records"
      />
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="record-keyword" clearable placeholder="编码或名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-record" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-record" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="客户数据加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无客户数据'" stripe>
        <el-table-column prop="code" label="编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contactName" label="联系人" min-width="120" show-overflow-tooltip />
        <el-table-column prop="contactPhone" label="联系电话" min-width="140" show-overflow-tooltip />
        <el-table-column label="结算税务摘要" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ settlementSummaryText(row) }}</span>
            <el-tag
              v-if="row.settlementTaxSummary?.hasData && row.settlementTaxSummary?.sensitiveRestricted"
              class="summary-tag"
              type="warning"
              size="small"
            >
              受限
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ masterStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" fixed="right" min-width="230">
          <template #default="{ row }">
            <el-button v-if="canUpdate" size="small" text data-test="edit-record" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="canSettlementView"
              size="small"
              text
              data-test="edit-customer-settlement-tax"
              @click="openSettlement(row)"
            >
              结算税务
            </el-button>
            <el-button
              v-if="canUpdate"
              size="small"
              text
              :disabled="actionLoading"
              :type="row.status === 'DISABLED' ? 'success' : 'danger'"
              :data-test="row.status === 'DISABLED' ? 'enable-record' : 'disable-record'"
              @click="changeStatus(row)"
            >
              {{ row.status === 'DISABLED' ? '启用' : '停用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage" @size-change="changePageSize"
    />

    <el-dialog v-model="formVisible" :title="editingRecord ? '编辑客户' : '新增客户'" width="560px">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="客户编码">
          <div class="field-with-action">
            <el-input v-model="form.code" name="record-code" :disabled="Boolean(editingRecord)" />
            <el-button
              v-if="!editingRecord && canGenerateCode"
              data-test="generate-customer-code"
              :loading="codeGenerating"
              :disabled="codeGenerating"
              @click="generateCode"
            >
              生成编码
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="form.name" name="record-name" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" name="record-contact-name" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" name="record-contact-phone" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="record-remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          data-test="submit-record"
          type="primary"
          :loading="formSubmitting"
          :disabled="formSubmitting"
          @click="saveRecord"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="settlementVisible" title="客户结算税务资料" width="min(760px, 96vw)">
      <el-alert v-if="settlementError" class="form-alert" type="error" :title="settlementError" :closable="false" />
      <el-alert
        v-if="settlementRecord?.sensitiveRestricted"
        class="form-alert"
        type="warning"
        :title="settlementRecord.restrictedMessage || '无权限查看完整结算税务资料'"
        :closable="false"
      />
      <el-empty v-if="!settlementRecord && !settlementError" description="结算税务资料加载中" />
      <el-form v-else-if="settlementRecord" label-position="top">
        <div v-if="!settlementRecord.hasData" class="settlement-empty-state">未维护</div>
        <div class="settlement-form-grid">
          <el-form-item label="开票名称">
            <el-input v-model="settlementForm.invoiceTitle" name="settlement-invoice-title" :disabled="!canSettlementUpdate" />
          </el-form-item>
          <el-form-item label="纳税识别号">
            <el-input
              v-model="settlementForm.taxNo"
              name="settlement-tax-no"
              :placeholder="settlementRecord.taxNoMasked || ''"
              :disabled="!canSettlementUpdate || !canSettlementSensitiveUpdate"
            />
          </el-form-item>
          <el-form-item label="注册地址电话">
            <el-input
              v-model="settlementForm.registeredPhone"
              name="settlement-registered-phone"
              :disabled="!canSettlementUpdate || !canSettlementSensitiveUpdate"
            />
          </el-form-item>
          <el-form-item label="开户行">
            <el-input
              v-model="settlementForm.bankName"
              name="settlement-bank-name"
              :disabled="!canSettlementUpdate || !canSettlementSensitiveUpdate"
            />
          </el-form-item>
          <el-form-item label="银行账号">
            <el-input
              v-model="settlementForm.bankAccount"
              name="settlement-bank-account"
              :placeholder="settlementRecord.bankAccountMasked || ''"
              :disabled="!canSettlementUpdate || !canSettlementSensitiveUpdate"
            />
          </el-form-item>
          <el-form-item label="默认税率">
            <el-input v-model="settlementForm.defaultTaxRate" name="settlement-default-tax-rate" :disabled="!canSettlementUpdate" />
          </el-form-item>
          <el-form-item label="发票类型">
            <el-select v-model="settlementForm.invoiceType" :disabled="!canSettlementUpdate" style="width: 100%">
              <el-option v-for="option in invoiceTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="结算方式">
            <el-select v-model="settlementForm.settlementMethod" :disabled="!canSettlementUpdate" style="width: 100%">
              <el-option v-for="option in settlementMethodOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="账期天数">
            <el-input v-model="settlementForm.paymentTermDays" name="settlement-payment-term-days" :disabled="!canSettlementUpdate" />
          </el-form-item>
        </div>
        <el-form-item label="注册地址">
          <el-input
            v-model="settlementForm.registeredAddress"
            name="settlement-registered-address"
            :disabled="!canSettlementUpdate || !canSettlementSensitiveUpdate"
          />
        </el-form-item>
        <el-form-item label="收款条件">
          <el-input v-model="settlementForm.paymentTerms" name="settlement-payment-terms" :disabled="!canSettlementUpdate" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="settlementForm.remark" name="settlement-remark" type="textarea" :rows="3" :disabled="!canSettlementUpdate" />
        </el-form-item>
        <dl class="settlement-detail-summary">
          <dt>资料状态</dt>
          <dd>{{ settlementRecord.hasData ? '已维护' : '未维护' }}</dd>
          <dt>脱敏税号</dt>
          <dd>{{ settlementRecord.taxNoMasked || '未填写' }}</dd>
          <dt>脱敏银行账号</dt>
          <dd>{{ settlementRecord.bankAccountMasked || '未填写' }}</dd>
          <dt>发票类型</dt>
          <dd>{{ invoiceTypeLabel(settlementRecord.invoiceType) }}</dd>
        </dl>
      </el-form>
      <template #footer>
        <el-button @click="settlementVisible = false">取消</el-button>
        <el-button
          v-if="canSettlementUpdate"
          data-test="submit-settlement-tax"
          type="primary"
          :loading="settlementSubmitting"
          :disabled="settlementSubmitting || !settlementRecord"
          @click="saveSettlement"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>

<style scoped>
.summary-tag {
  margin-left: 6px;
}

.settlement-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.field-with-action {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.settlement-empty-state {
  color: var(--qherp-muted);
  margin-bottom: 12px;
}

.settlement-detail-summary {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 8px 12px;
  margin: 12px 0 0;
}

.settlement-detail-summary dt {
  color: var(--qherp-muted);
}

.settlement-detail-summary dd {
  margin: 0;
  word-break: break-word;
}

@media (max-width: 760px) {
  .settlement-form-grid,
  .field-with-action {
    grid-template-columns: 1fr;
  }
}
</style>
