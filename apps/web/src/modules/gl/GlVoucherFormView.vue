<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { glApi, type GlAccountingPeriodRecord, type GlAccountRecord, type GlAuxCandidateRecord, type GlAuxDimensionRecord, type GlAuxRequirement, type GlVoucherLineRecord, type GlVoucherRecord } from '../../shared/api/glApi'
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
const accountCandidatePagination = reactive({ page: 1, pageSize: 20, total: 0 })
const openPeriods = ref<GlAccountingPeriodRecord[]>([])
const auxDimensions = ref<GlAuxDimensionRecord[]>([])
const auxCandidatePools = reactive<Record<string, {
  keyword: string
  page: number
  pageSize: number
  total: number
  items: GlAuxCandidateRecord[]
  loading: boolean
}>>({})
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
const balanceDifference = computed(() => subtractDecimalStrings(debitTotal.value, creditTotal.value))
const resolvedPeriod = computed(() => {
  if (!form.voucherDate) {
    return null
  }
  return openPeriods.value.find((period) => period.status === 'OPEN'
    && form.voucherDate >= period.startDate
    && form.voucherDate <= period.endDate) ?? null
})
const periodText = computed(() => {
  if (!form.voucherDate) {
    return '请先选择凭证日期'
  }
  if (resolvedPeriod.value) {
    return `${resolvedPeriod.value.periodCode}（由凭证日期匹配唯一 OPEN 会计期间）`
  }
  return '未找到匹配的 OPEN 会计期间，保存将被禁用'
})
const validationReasons = computed(() => {
  const reasons: string[] = []
  if (!form.voucherDate) {
    reasons.push('请先选择凭证日期')
  } else if (!resolvedPeriod.value) {
    reasons.push('凭证日期未匹配 OPEN 会计期间')
  }
  if (form.lines.length < 2) {
    reasons.push('至少两行分录')
  }
  if (balanceDifference.value !== '0.00') {
    reasons.push(`借贷差额 ${formatGlAmount(balanceDifference.value)}`)
  }
  if (form.lines.some((line) => !line.accountId)) {
    reasons.push('分录科目必填')
  }
  form.lines.forEach((line) => {
    currentAuxiliaryRequirements(line)
      .filter((item) => item.requirement === 'REQUIRED')
      .forEach((item) => {
        if (!selectedAuxiliary(line, item.dimensionCode)?.objectId) {
          reasons.push(`第 ${line.lineNo} 行${item.dimensionName || item.dimensionCode}辅助核算必填`)
        }
      })
  })
  return reasons
})
const saveDisabled = computed(() => saving.value || validationReasons.value.length > 0)
const systemAuxDimensionCodes = new Set(['CUSTOMER', 'SUPPLIER', 'PROJECT'])

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
    form.summary = record.summary || ''
    form.lines = record.lines?.length ? record.lines : form.lines
  } catch (caught) {
    error.value = glErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadPeriods() {
  try {
    const page = await glApi.accountingPeriods.list({ page: 1, pageSize: 100 })
    openPeriods.value = glPageItems(page).filter((period) => period.status === 'OPEN')
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  }
}

async function loadAuxDimensions() {
  try {
    const page = await glApi.auxDimensions.list({ enabled: 'true', page: 1, pageSize: 100 })
    auxDimensions.value = glPageItems(page)
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  }
}

function selectedAccountIds() {
  return [...new Set(form.lines.map((line) => line.accountId).filter(Boolean).map(String))].join(',')
}

function mergeById<T extends { id?: string | number | null }>(left: T[], right: T[]) {
  const seen = new Set<string>()
  return [...left, ...right].filter((item) => {
    const key = String(item.id ?? '')
    if (!key || seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

async function loadAccountCandidates(keyword = accountKeyword.value, append = false) {
  accountKeyword.value = keyword
  if (!append) {
    accountCandidatePagination.page = 1
  }
  try {
    const page = await glApi.accounts.candidates({
      keyword,
      selectedIds: selectedAccountIds(),
      page: accountCandidatePagination.page,
      pageSize: accountCandidatePagination.pageSize,
    })
    const items = glPageItems(page)
    const selectedIdSet = new Set(selectedAccountIds().split(',').filter(Boolean))
    accountCandidates.value = append
      ? mergeById(accountCandidates.value, items)
      : mergeById(items, accountCandidates.value.filter((account) => selectedIdSet.has(String(account.id))))
    accountCandidatePagination.total = page.total ?? items.length
    hydrateSelectedAccounts()
    loadAllAuxiliaryCandidates()
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  }
}

async function loadMoreAccountCandidates() {
  if (accountCandidates.value.length >= accountCandidatePagination.total) {
    return
  }
  accountCandidatePagination.page += 1
  await loadAccountCandidates(accountKeyword.value, true)
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

function hydrateSelectedAccounts() {
  form.lines.forEach((line) => {
    const account = selectedAccount(line)
    if (account) {
      line.accountCode = account.code
      line.accountName = account.name
    }
  })
}

function currentAuxiliaryRequirements(line: GlVoucherLineRecord): GlAuxRequirement[] {
  return selectedAccount(line)?.auxiliaryRequirements ?? []
}

function selectedAuxiliary(line: GlVoucherLineRecord, dimensionCode: string) {
  return line.auxiliaryItems.find((item) => item.dimensionCode === dimensionCode)
}

function auxiliaryDisplayId(item: GlVoucherLineRecord['auxiliaryItems'][number] | undefined) {
  return item?.objectId ?? item?.sourceId ?? item?.auxItemId ?? ''
}

function selectedAuxiliaryId(line: GlVoucherLineRecord, dimensionCode: string) {
  return auxiliaryDisplayId(selectedAuxiliary(line, dimensionCode))
}

function selectedAuxiliaryIds(dimensionCode: string) {
  return [...new Set(form.lines
    .flatMap((line) => line.auxiliaryItems)
    .filter((item) => item.dimensionCode === dimensionCode && auxiliaryDisplayId(item))
    .map((item) => String(auxiliaryDisplayId(item))))].join(',')
}

function ensureAuxCandidatePool(dimensionCode: string) {
  auxCandidatePools[dimensionCode] ??= {
    keyword: '',
    page: 1,
    pageSize: 20,
    total: 0,
    items: [],
    loading: false,
  }
  return auxCandidatePools[dimensionCode]
}

function mergeAuxCandidates(left: GlAuxCandidateRecord[], right: GlAuxCandidateRecord[]) {
  const seen = new Set<string>()
  return [...left, ...right].filter((item) => {
    const key = String(candidateDisplayId(item) ?? '')
    if (!key || seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

async function loadAuxiliaryCandidates(dimensionCode: string, keyword?: string, append = false) {
  const pool = ensureAuxCandidatePool(dimensionCode)
  if (keyword !== undefined) {
    pool.keyword = keyword
  }
  if (!append) {
    pool.page = 1
  }
  pool.loading = true
  actionError.value = ''
  try {
    const page = await glApi.auxDimensions.candidates(dimensionCode, {
      keyword: pool.keyword,
      selectedIds: selectedAuxiliaryIds(dimensionCode),
      page: pool.page,
      pageSize: pool.pageSize,
    })
    const items = glPageItems(page)
    const selectedIdSet = new Set(selectedAuxiliaryIds(dimensionCode).split(',').filter(Boolean))
    pool.items = append
      ? mergeAuxCandidates(pool.items, items)
      : mergeAuxCandidates(items, pool.items.filter((item) => selectedIdSet.has(String(candidateDisplayId(item)))))
    pool.total = page.total ?? items.length
  } catch (caught) {
    if (!append) {
      pool.items = []
      pool.total = 0
    }
    actionError.value = glErrorMessage(caught)
  } finally {
    pool.loading = false
  }
}

function loadAllAuxiliaryCandidates() {
  const dimensionCodes = [...new Set(form.lines.flatMap((line) => currentAuxiliaryRequirements(line).map((item) => item.dimensionCode)))]
  dimensionCodes.forEach((dimensionCode) => {
    void loadAuxiliaryCandidates(dimensionCode)
  })
}

function isSystemAuxDimension(dimensionCode: string) {
  const dimension = auxDimensions.value.find((item) => item.code === dimensionCode)
  return dimension?.dimensionType === 'SYSTEM' || systemAuxDimensionCodes.has(dimensionCode)
}

function candidateDisplayId(candidate: GlAuxCandidateRecord | undefined) {
  return candidate?.objectId ?? candidate?.sourceId ?? candidate?.auxItemId ?? candidate?.id ?? ''
}

function setLineAuxiliary(line: GlVoucherLineRecord, requirement: GlAuxRequirement, objectId: string | number | '') {
  line.auxiliaryItems = line.auxiliaryItems.filter((item) => item.dimensionCode !== requirement.dimensionCode)
  if (!objectId) {
    return
  }
  const candidate = ensureAuxCandidatePool(requirement.dimensionCode).items.find((item) => String(candidateDisplayId(item)) === String(objectId))
  const displayId = candidateDisplayId(candidate) || objectId
  const systemDimension = isSystemAuxDimension(requirement.dimensionCode)
  line.auxiliaryItems.push({
    dimensionCode: requirement.dimensionCode,
    dimensionName: requirement.dimensionName,
    objectId: displayId,
    sourceId: systemDimension ? (candidate?.sourceId ?? displayId) : null,
    auxItemId: systemDimension ? null : (candidate?.auxItemId ?? displayId),
    objectCode: candidate?.objectCode ?? null,
    objectName: candidate?.objectName ?? null,
    restricted: candidate?.restricted ?? false,
    restrictedReason: candidate?.restrictedReason ?? null,
  })
}

function auxiliaryCandidateText(candidate: GlAuxCandidateRecord) {
  if (candidate.restricted) {
    return candidate.restrictedReason || '无权查看候选'
  }
  return [candidate.objectCode, candidate.objectName].filter(Boolean).join(' ')
}

function selectedAuxiliaryText(line: GlVoucherLineRecord, dimensionCode: string) {
  const selected = selectedAuxiliary(line, dimensionCode)
  if (!auxiliaryDisplayId(selected)) {
    return '未选择'
  }
  return [selected?.objectCode, selected?.objectName].filter(Boolean).join(' ') || String(auxiliaryDisplayId(selected))
}

function setLineAccount(line: GlVoucherLineRecord, accountId: string | number) {
  line.accountId = accountId
  const account = selectedAccount(line)
  if (account) {
    line.accountCode = account.code
    line.accountName = account.name
    line.auxiliaryItems = line.auxiliaryItems.filter((item) =>
      (account.auxiliaryRequirements ?? []).some((requirement) => requirement.dimensionCode === item.dimensionCode && auxiliaryDisplayId(item)))
    ;(account.auxiliaryRequirements ?? []).forEach((item) => {
      void loadAuxiliaryCandidates(item.dimensionCode)
    })
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

function payloadAuxiliaryItems(line: GlVoucherLineRecord) {
  return line.auxiliaryItems
    .filter((item) => Boolean(auxiliaryDisplayId(item)))
    .map((item) => {
      if (isSystemAuxDimension(item.dimensionCode)) {
        return {
          dimensionCode: item.dimensionCode,
          sourceId: item.sourceId ?? item.objectId,
        }
      }
      return {
        dimensionCode: item.dimensionCode,
        auxItemId: item.auxItemId ?? item.objectId,
      }
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
      summary: form.summary,
      lines: form.lines.map((line) => ({
        lineNo: line.lineNo,
        summary: line.summary,
        accountId: line.accountId,
        debitAmount: line.debitAmount,
        creditAmount: line.creditAmount,
        auxiliaryItems: payloadAuxiliaryItems(line),
      })),
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
  void loadPeriods()
  void loadAuxDimensions()
  void loadRecord().then(() => loadAccountCandidates())
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
        <el-form-item label="会计期间"><el-input :model-value="periodText" disabled placeholder="由凭证日期解析" /></el-form-item>
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
      <el-alert
        v-if="validationReasons.includes('请先选择凭证日期') || validationReasons.includes('凭证日期未匹配 OPEN 会计期间')"
        type="warning"
        :title="periodText"
        :closable="false"
      />
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
      <el-button
        v-if="accountCandidates.length < accountCandidatePagination.total"
        data-test="load-more-account-candidates"
        @click="loadMoreAccountCandidates"
      >
        加载更多科目
      </el-button>
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
        <el-table-column label="辅助核算" min-width="260">
          <template #default="{ row }">
            <div class="gl-aux-cell">
              <div v-if="currentAuxiliaryRequirements(row).length === 0" class="gl-muted">无辅助核算要求</div>
              <div v-for="requirement in currentAuxiliaryRequirements(row)" :key="requirement.dimensionCode" class="gl-aux-editor">
                <span class="gl-muted">{{ requirement.dimensionName || requirement.dimensionCode }} {{ requirement.requirement === 'REQUIRED' ? '必填' : '可选' }}</span>
                <el-select
                  :data-test="`select-gl-line-aux-${requirement.dimensionCode}-${row.lineNo}`"
                  :model-value="selectedAuxiliaryId(row, requirement.dimensionCode)"
                  clearable
                  filterable
                  remote
                  reserve-keyword
                  :remote-method="(keyword: string) => loadAuxiliaryCandidates(requirement.dimensionCode, keyword)"
                  placeholder="选择辅助对象"
                  @visible-change="(visible: boolean) => visible && loadAuxiliaryCandidates(requirement.dimensionCode)"
                  @update:model-value="(value: string | number | '') => setLineAuxiliary(row, requirement, value)"
                >
                  <el-option
                    v-for="candidate in ensureAuxCandidatePool(requirement.dimensionCode).items"
                    :key="candidateDisplayId(candidate)"
                    :label="auxiliaryCandidateText(candidate)"
                    :value="candidateDisplayId(candidate)"
                    :disabled="candidate.restricted"
                  />
                </el-select>
                <span class="gl-muted">{{ selectedAuxiliaryText(row, requirement.dimensionCode) }}</span>
              </div>
            </div>
          </template>
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
