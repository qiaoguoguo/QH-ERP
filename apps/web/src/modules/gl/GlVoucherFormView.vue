<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { glApi, type GlAccountRecord, type GlVoucherLineRecord, type GlVoucherRecord } from '../../shared/api/glApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { createGlIdempotencyKey, formatGlAmount, glErrorMessage, glPageItems } from './glPageHelpers'
import './GlShared.css'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const actionError = ref('')
const existing = ref<GlVoucherRecord | null>(null)
const accountCandidates = ref<GlAccountRecord[]>([])
const accountKeyword = ref('')
const form = reactive({
  voucherType: 'GENERAL',
  voucherDate: '2026-07-20',
  accountingPeriodCode: '2026-07',
  summary: '手工凭证',
  lines: [
    { lineNo: 1, summary: '借方分录', accountId: 1002, debitAmount: '100.00', creditAmount: '0.00', auxiliaryItems: [] },
    { lineNo: 2, summary: '贷方分录', accountId: 6001, debitAmount: '0.00', creditAmount: '100.00', auxiliaryItems: [] },
  ] as GlVoucherLineRecord[],
})

const isEdit = computed(() => Boolean(route.params.id))
const returnTo = computed(() => typeof route.query.returnTo === 'string' ? route.query.returnTo : '/gl/vouchers')
const debitTotal = computed(() => addDecimalStrings(form.lines.map((line) => line.debitAmount)))
const creditTotal = computed(() => addDecimalStrings(form.lines.map((line) => line.creditAmount)))
const balanceDifference = computed(() => subtractDecimalStrings(debitTotal.value, creditTotal.value))
const validationReasons = computed(() => {
  const reasons: string[] = []
  if (form.lines.length < 2) {
    reasons.push('至少两行分录')
  }
  if (balanceDifference.value !== '0.00') {
    reasons.push(`借贷差额 ${formatGlAmount(balanceDifference.value)}`)
  }
  if (form.lines.some((line) => !line.accountId)) {
    reasons.push('分录科目必填')
  }
  return reasons
})
const saveDisabled = computed(() => saving.value || validationReasons.value.length > 0)

function decimalToCents(value: string) {
  const raw = String(value || '0.00').trim()
  if (!/^\d+(\.\d{1,2})?$/.test(raw)) {
    return 0n
  }
  const [integer, decimal = ''] = raw.split('.')
  return BigInt(integer) * 100n + BigInt(`${decimal}00`.slice(0, 2))
}

function addDecimalStrings(values: string[]) {
  const total = values.reduce((sum, value) => sum + decimalToCents(value), 0n)
  return centsToDecimal(total)
}

function centsToDecimal(value: bigint) {
  const sign = value < 0n ? '-' : ''
  const unsigned = value < 0n ? -value : value
  return `${sign}${unsigned / 100n}.${String(unsigned % 100n).padStart(2, '0')}`
}

function subtractDecimalStrings(left: string, right: string) {
  return centsToDecimal(decimalToCents(left) - decimalToCents(right))
}

function setVoucherDate(value: string | null) {
  form.voucherDate = value ?? ''
}

async function loadRecord() {
  if (!isEdit.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const record = await glApi.vouchers.get(route.params.id as string)
    existing.value = record
    form.voucherType = record.voucherType || 'GENERAL'
    form.voucherDate = record.voucherDate || ''
    form.accountingPeriodCode = record.accountingPeriodCode || ''
    form.summary = record.summary || ''
    form.lines = record.lines?.length ? record.lines : form.lines
  } catch (caught) {
    error.value = glErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadAccountCandidates(keyword = accountKeyword.value) {
  accountKeyword.value = keyword
  try {
    const page = await glApi.accounts.list({
      keyword,
      enabled: true,
      postable: true,
      page: 1,
      pageSize: 20,
    })
    accountCandidates.value = glPageItems(page)
    hydrateSelectedAccounts()
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  }
}

function accountText(account: Pick<GlAccountRecord, 'code' | 'name'>) {
  return `${account.code} ${account.name}`
}

function selectedAccount(line: GlVoucherLineRecord) {
  return accountCandidates.value.find((account) => String(account.id) === String(line.accountId))
}

function selectedAccountText(line: GlVoucherLineRecord) {
  const account = selectedAccount(line)
  if (account) {
    return accountText(account)
  }
  return [line.accountCode, line.accountName].filter(Boolean).join(' ') || '请选择科目'
}

function accountAuxiliaryText(line: GlVoucherLineRecord) {
  const account = selectedAccount(line)
  const requirements = account?.auxiliaryRequirements ?? []
  if (requirements.length === 0) {
    return '无辅助核算要求'
  }
  return requirements.map((item) => `${item.dimensionName || item.dimensionCode} ${item.requirement === 'REQUIRED' ? '必填' : '可选'}`).join('、')
}

function hydrateSelectedAccounts() {
  form.lines.forEach((line) => {
    const account = selectedAccount(line)
    if (account) {
      line.accountCode = account.code
      line.accountName = account.name
    }
  })
}

function setLineAccount(line: GlVoucherLineRecord, accountId: string | number) {
  line.accountId = accountId
  const account = selectedAccount(line)
  if (account) {
    line.accountCode = account.code
    line.accountName = account.name
    line.auxiliaryItems = (account.auxiliaryRequirements ?? []).map((item) => ({
      dimensionCode: item.dimensionCode,
      dimensionName: item.dimensionName,
      objectId: null,
      objectCode: null,
      objectName: item.requirement === 'REQUIRED' ? '待选择' : '可选',
    }))
  }
}

function addLine() {
  form.lines.push({
    lineNo: form.lines.length + 1,
    summary: '',
    accountId: '',
    debitAmount: '0.00',
    creditAmount: '0.00',
    auxiliaryItems: [],
  })
}

function removeLine(index: number) {
  if (form.lines.length <= 2) {
    actionError.value = '至少保留两行分录'
    return
  }
  form.lines.splice(index, 1)
  form.lines.forEach((line, lineIndex) => {
    line.lineNo = lineIndex + 1
  })
}

async function saveVoucher() {
  if (saving.value) {
    return
  }
  saving.value = true
  actionError.value = ''
  try {
    const payload = {
      voucherType: form.voucherType,
      voucherDate: form.voucherDate,
      accountingPeriodCode: form.accountingPeriodCode,
      summary: form.summary,
      lines: form.lines,
      idempotencyKey: createGlIdempotencyKey('gl-voucher-save'),
    }
    const saved = isEdit.value && existing.value
      ? await glApi.vouchers.update(existing.value.id, { ...payload, version: existing.value.version })
      : await glApi.vouchers.create(payload)
    await router.push({ name: 'gl-voucher-detail', params: { id: saved.id }, query: { returnTo: returnTo.value } })
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadRecord()
  void loadAccountCandidates()
})
</script>

<template>
  <MasterDataTableView title="凭证编辑" description="以凭证分录为主体维护正式凭证草稿，金额字段保持字符串十进制，不在前端使用浮点重算。">
    <template #actions>
      <el-button @click="router.push(returnTo)">返回</el-button>
      <el-button data-test="save-gl-voucher" type="primary" :loading="saving" :disabled="saveDisabled" @click="saveVoucher">保存凭证</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline label-position="top">
        <el-form-item label="凭证日期">
          <el-date-picker
            :model-value="form.voucherDate"
            value-on-clear=""
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="凭证日期"
            @update:model-value="setVoucherDate"
          />
        </el-form-item>
        <el-form-item label="会计期间"><el-input v-model="form.accountingPeriodCode" clearable placeholder="2026-07" /></el-form-item>
        <el-form-item label="凭证摘要"><el-input v-model="form.summary" clearable placeholder="凭证摘要" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.voucherType" disabled /></el-form-item>
        <el-form-item label="科目候选">
          <el-input
            v-model="accountKeyword"
            data-test="gl-account-candidate-search"
            name="gl-account-candidate-search"
            clearable
            placeholder="搜索科目编码或名称"
            @input="loadAccountCandidates"
          />
        </el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="凭证加载中" :closable="false" />
    </template>

    <div class="gl-summary-strip">
      <div><span>借方合计</span><strong>{{ formatGlAmount(debitTotal) }}</strong></div>
      <div><span>贷方合计</span><strong>{{ formatGlAmount(creditTotal) }}</strong></div>
      <div><span>差额</span><strong>{{ formatGlAmount(balanceDifference) }}</strong></div>
      <div><span>平衡状态</span><strong>{{ debitTotal === creditTotal ? '借贷平衡' : '借贷不平衡' }}</strong></div>
      <div><span>禁用原因</span><strong>{{ validationReasons.join('、') || '可保存 GENERAL/OPENING 草稿' }}</strong></div>
    </div>

    <div class="gl-toolbar">
      <el-button data-test="add-gl-voucher-line" @click="addLine">新增分录</el-button>
    </div>
    <div class="table-scroll" data-test="gl-voucher-lines-table">
      <el-table :data="form.lines" empty-text="暂无凭证分录" stripe>
        <el-table-column prop="lineNo" label="行号" min-width="80" align="right" />
        <el-table-column label="摘要" min-width="180"><template #default="{ row }"><el-input v-model="row.summary" /></template></el-table-column>
        <el-table-column label="科目" min-width="240">
          <template #default="{ row }">
            <el-select
              :model-value="row.accountId"
              filterable
              placeholder="选择科目"
              @update:model-value="(value: string | number) => setLineAccount(row, value)"
            >
              <el-option
                v-for="account in accountCandidates"
                :key="account.id"
                :label="accountText(account)"
                :value="account.id"
              />
            </el-select>
            <div class="gl-muted">{{ selectedAccountText(row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="辅助核算" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ accountAuxiliaryText(row) }}</template>
        </el-table-column>
        <el-table-column label="借方金额" min-width="130" align="right"><template #default="{ row }"><el-input v-model="row.debitAmount" /></template></el-table-column>
        <el-table-column label="贷方金额" min-width="130" align="right"><template #default="{ row }"><el-input v-model="row.creditAmount" /></template></el-table-column>
        <el-table-column label="操作" fixed="right" min-width="100">
          <template #default="{ $index }">
            <el-button data-test="remove-gl-voucher-line" text type="danger" @click="removeLine($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </MasterDataTableView>
</template>
