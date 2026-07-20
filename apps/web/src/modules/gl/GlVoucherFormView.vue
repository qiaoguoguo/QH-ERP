<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { glApi, type GlVoucherLineRecord, type GlVoucherRecord } from '../../shared/api/glApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { createGlIdempotencyKey, formatGlAmount, glErrorMessage } from './glPageHelpers'
import './GlShared.css'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const actionError = ref('')
const existing = ref<GlVoucherRecord | null>(null)
const form = reactive({
  voucherType: 'GENERAL',
  voucherDate: '2026-07-20',
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
  return `${total / 100n}.${String(total % 100n).padStart(2, '0')}`
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
    form.summary = record.summary || ''
    form.lines = record.lines?.length ? record.lines : form.lines
  } catch (caught) {
    error.value = glErrorMessage(caught)
  } finally {
    loading.value = false
  }
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

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="凭证编辑" description="以凭证分录为主体维护正式凭证草稿，金额字段保持字符串十进制，不在前端使用浮点重算。">
    <template #actions>
      <el-button @click="router.push(returnTo)">返回</el-button>
      <el-button data-test="save-gl-voucher" type="primary" :loading="saving" :disabled="saving" @click="saveVoucher">保存凭证</el-button>
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
        <el-form-item label="凭证摘要"><el-input v-model="form.summary" clearable placeholder="凭证摘要" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.voucherType" disabled /></el-form-item>
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
      <div><span>平衡状态</span><strong>{{ debitTotal === creditTotal ? '借贷平衡' : '借贷不平衡' }}</strong></div>
      <div><span>审批边界</span><strong>提交后进入固定审批</strong></div>
    </div>

    <div class="table-scroll" data-test="gl-voucher-lines-table">
      <el-table :data="form.lines" empty-text="暂无凭证分录" stripe>
        <el-table-column prop="lineNo" label="行号" min-width="80" align="right" />
        <el-table-column label="摘要" min-width="180"><template #default="{ row }"><el-input v-model="row.summary" /></template></el-table-column>
        <el-table-column label="科目" min-width="150"><template #default="{ row }"><el-input v-model="row.accountId" /></template></el-table-column>
        <el-table-column label="借方金额" min-width="130" align="right"><template #default="{ row }"><el-input v-model="row.debitAmount" /></template></el-table-column>
        <el-table-column label="贷方金额" min-width="130" align="right"><template #default="{ row }"><el-input v-model="row.creditAmount" /></template></el-table-column>
      </el-table>
    </div>
  </MasterDataTableView>
</template>
