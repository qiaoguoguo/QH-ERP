<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { glApi, type GlPostingRulePreviewLineRecord, type GlPostingRuleRecord, type GlPostingRuleValidationSummary } from '../../shared/api/glApi'
import { confirmAction } from '../../shared/ui/confirmDialog'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { createGlIdempotencyKey, formatGlAmount, glActionAllowed, glActionDisabledReason, glErrorMessage, glPageItems, glPageSizes, glPageTotal } from './glPageHelpers'
import './GlShared.css'

const filters = reactive({ sourceType: '', status: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<GlPostingRuleRecord[]>([])
const detail = ref<GlPostingRuleRecord | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const actionMessage = ref('')
const sourcePreviewWarning = ref('')
const sourcePreviewSummary = ref<GlPostingRuleValidationSummary | null>(null)
const sourcePreviewContext = ref('')
const detailDrawerVisible = ref(false)
const ruleDialogVisible = ref(false)
const ruleDialogTitle = ref('新增制证规则')
const ruleForm = reactive({
  id: null as string | number | null,
  name: '',
  description: '',
  effectiveFrom: '',
  effectiveTo: '',
  sourceType: '',
  sourceVariant: 'DEFAULT',
  status: 'DRAFT',
  version: 0,
  linesJson: '[]',
})
const previewContext = reactive({
  sourceType: '',
  sourceId: '',
  sourceVersion: '',
})

const actionCodeMap = {
  newVersion: 'NEW_VERSION',
  validate: 'VALIDATE',
  activate: 'ACTIVATE',
  disable: 'DISABLE',
} as const
const sourcePreviewLines = computed(() => sourcePreviewSummary.value?.previewLines ?? [])

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await glApi.postingRules.list({
      sourceType: filters.sourceType || undefined,
      status: filters.status || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = glPageItems(page)
    pagination.total = glPageTotal(page)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = glErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function clearSourcePreview() {
  sourcePreviewSummary.value = null
  sourcePreviewContext.value = ''
  sourcePreviewWarning.value = ''
}

async function openDetail(row: GlPostingRuleRecord) {
  detailDrawerVisible.value = true
  actionError.value = ''
  actionMessage.value = ''
  clearSourcePreview()
  previewContext.sourceType = ''
  previewContext.sourceId = ''
  previewContext.sourceVersion = ''
  try {
    detail.value = await glApi.postingRules.get(row.id)
  } catch (caught) {
    detail.value = row
    actionError.value = glErrorMessage(caught)
  }
}

async function runRuleAction(action: 'newVersion' | 'validate' | 'activate' | 'disable', row = detail.value) {
  if (!row || actionLoading.value) {
    return
  }
  const actionCode = actionCodeMap[action]
  if (!glActionAllowed(row, actionCode)) {
    actionError.value = glActionDisabledReason(row, actionCode) || '当前状态不可执行该动作'
    return
  }
  const label = action === 'newVersion' ? '复制新版本' : action === 'validate' ? '预览校验' : action === 'activate' ? '激活规则' : '停用规则'
  if (action !== 'validate' && !(await confirmAction(`${label} ${row.sourceType}/${row.sourceVariant} V${row.versionNo}？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  actionMessage.value = ''
  if (action === 'validate') {
    clearSourcePreview()
  }
  try {
    const sourceId = String(previewContext.sourceId || '').trim()
    const sourceType = previewContext.sourceType || row.sourceType
    const sourceVersion = String(previewContext.sourceVersion || '').trim()
    const payload = {
      version: row.version ?? row.versionNo,
      idempotencyKey: createGlIdempotencyKey(`gl-rule-${action}`),
      ...(action === 'validate'
        && sourceId
        ? {
            sourceType,
            sourceId,
            sourceVersion: sourceVersion || undefined,
          }
        : {}),
    }
    if (action === 'newVersion') {
      detail.value = await glApi.postingRules.newVersion(row.id, payload)
      actionMessage.value = '已复制新版本并刷新列表'
    } else if (action === 'validate') {
      detail.value = await glApi.postingRules.validate(row.id, payload)
      const summary = detail.value.validationSummary
      if (summary?.sourcePreview) {
        sourcePreviewContext.value = `${sourceType} / ${sourceId} / ${sourceVersion || '-'}`
        if ((summary.previewLines ?? []).length > 0) {
          sourcePreviewSummary.value = summary
          actionMessage.value = '预览校验完成，已返回分录建议'
        } else {
          sourcePreviewWarning.value = '来源预览未返回分录建议'
        }
      } else {
        actionMessage.value = '预览校验完成'
      }
    } else if (action === 'activate') {
      detail.value = await glApi.postingRules.activate(row.id, payload)
      actionMessage.value = '规则已激活'
    } else {
      detail.value = await glApi.postingRules.disable(row.id, payload)
      actionMessage.value = '规则已停用'
    }
    await loadRecords()
  } catch (caught) {
    if (action === 'validate') {
      clearSourcePreview()
    }
    actionError.value = glErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function previewDirectionText(value: string | null | undefined) {
  if (value === 'DEBIT') {
    return '借方'
  }
  if (value === 'CREDIT') {
    return '贷方'
  }
  return value || '-'
}

function previewAccountText(line: GlPostingRulePreviewLineRecord) {
  return [line.accountCode, line.accountName].filter(Boolean).join(' ') || String(line.accountId ?? '-')
}

function previewSummaryText(line: GlPostingRulePreviewLineRecord) {
  return line.summary || line.summaryTemplate || '-'
}

function previewAuxiliaryText(line: GlPostingRulePreviewLineRecord) {
  const values = [...(line.auxiliaryValues ?? []), ...(line.auxiliaryMappings ?? [])]
  if (values.length === 0) {
    return '-'
  }
  return values.map((item) => {
    if (item !== null && typeof item === 'object' && !Array.isArray(item)) {
      const record = item as Record<string, unknown>
      return [
        record.dimensionCode,
        record.mappingType,
        record.objectCode,
        record.objectName,
        record.fixedAuxItemId ? `固定${record.fixedAuxItemId}` : '',
        record.value,
      ].filter(Boolean).join(' ')
    }
    return String(item)
  }).filter(Boolean).join('、') || '-'
}

function openCreateRule() {
  ruleForm.id = null
  ruleForm.name = ''
  ruleForm.description = ''
  ruleForm.effectiveFrom = ''
  ruleForm.effectiveTo = ''
  ruleForm.sourceType = ''
  ruleForm.sourceVariant = 'DEFAULT'
  ruleForm.status = 'DRAFT'
  ruleForm.version = 0
  ruleForm.linesJson = '[]'
  ruleDialogTitle.value = '新增制证规则'
  ruleDialogVisible.value = true
}

function openEditRule(row = detail.value) {
  if (!row) {
    return
  }
  if (!glActionAllowed(row, 'UPDATE')) {
    actionError.value = glActionDisabledReason(row, 'UPDATE') || '只有草稿规则可编辑'
    return
  }
  ruleForm.id = row.id
  ruleForm.name = row.name || ''
  ruleForm.description = row.description || ''
  ruleForm.effectiveFrom = row.effectiveFrom || ''
  ruleForm.effectiveTo = row.effectiveTo || ''
  ruleForm.sourceType = row.sourceType
  ruleForm.sourceVariant = row.sourceVariant
  ruleForm.status = row.status
  ruleForm.version = row.version ?? row.versionNo
  ruleForm.linesJson = JSON.stringify(row.lines ?? [], null, 2)
  ruleDialogTitle.value = '编辑草稿规则'
  ruleDialogVisible.value = true
}

function normalizeRuleLinesForPayload(lines: unknown[]) {
  return lines.map((line, index) => {
    const record = line !== null && typeof line === 'object' && !Array.isArray(line)
      ? line as Record<string, unknown>
      : {}
    return {
      lineNo: record.lineNo ?? index + 1,
      normalizedFactCode: String(record.normalizedFactCode ?? record.factCode ?? ''),
      direction: String(record.direction ?? ''),
      accountId: record.accountId ?? null,
      summaryTemplate: record.summaryTemplate ? String(record.summaryTemplate) : '',
      auxiliaryMappings: Array.isArray(record.auxiliaryMappings) ? record.auxiliaryMappings : [],
    }
  })
}

async function saveRule() {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  actionMessage.value = ''
  try {
    if (!ruleForm.name.trim()) {
      throw new Error('规则名称必填')
    }
    let lines: unknown[] = []
    try {
      const parsed = JSON.parse(ruleForm.linesJson || '[]') as unknown
      lines = Array.isArray(parsed) ? parsed : []
    } catch {
      throw new Error('规则行必须是 JSON 数组')
    }
    const payload = {
      name: ruleForm.name.trim(),
      description: ruleForm.description.trim() || null,
      effectiveFrom: ruleForm.effectiveFrom || null,
      effectiveTo: ruleForm.effectiveTo || null,
      sourceType: ruleForm.sourceType,
      sourceVariant: ruleForm.sourceVariant || 'DEFAULT',
      lines: normalizeRuleLinesForPayload(lines),
      version: ruleForm.version,
      idempotencyKey: createGlIdempotencyKey('gl-rule-save'),
    }
    if (ruleForm.id) {
      detail.value = await glApi.postingRules.update(ruleForm.id, payload)
      actionMessage.value = '草稿规则已保存'
    } else {
      detail.value = await glApi.postingRules.create(payload)
      actionMessage.value = '制证规则已创建'
    }
    ruleDialogVisible.value = false
    await loadRecords()
  } catch (caught) {
    actionError.value = glErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function search() {
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

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="自动制证规则" description="维护业务事实到正式凭证草稿的映射；预览不制证，不影响账簿。">
    <template #actions>
      <el-button @click="loadRecords">刷新</el-button>
      <el-button data-test="create-posting-rule" type="primary" @click="openCreateRule">新增规则</el-button>
    </template>
    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="来源类型"><el-input v-model="filters.sourceType" clearable placeholder="SALES_INVOICE" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用" value="ACTIVE" />
            <el-option label="已替代" value="SUPERSEDED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
    </template>
    <template #alerts>
      <el-alert type="info" title="规则预览不制证，仅用于校验科目、辅助核算与金额方向。" :closable="false" />
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="actionMessage" type="success" :title="actionMessage" :closable="false" />
      <el-alert v-if="loading" type="info" title="制证规则加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无自动制证规则'" stripe>
        <el-table-column prop="sourceType" label="来源类型" min-width="150" />
        <el-table-column prop="sourceVariant" label="来源变体" min-width="140" />
        <el-table-column prop="versionNo" label="版本" min-width="90" align="right" />
        <el-table-column prop="status" label="状态" min-width="110" />
        <el-table-column prop="validationStatus" label="校验状态" min-width="120" />
        <el-table-column prop="lineCount" label="分录行数" min-width="100" align="right" />
        <el-table-column label="动作状态" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ glActionDisabledReason(row, 'DISABLE') || (row.allowedActions?.join('、') || '-') }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="180">
          <template #default="{ row }">
            <el-button data-test="view-posting-rule" text @click="openDetail(row)">详情</el-button>
            <el-button
              data-test="validate-posting-rule"
              text
              :title="glActionDisabledReason(row, 'VALIDATE')"
              :disabled="!glActionAllowed(row, 'VALIDATE')"
              @click="runRuleAction('validate', row)"
            >
              预览校验
            </el-button>
            <el-button
              data-test="disable-posting-rule"
              text
              type="danger"
              :title="glActionDisabledReason(row, 'DISABLE')"
              :disabled="!glActionAllowed(row, 'DISABLE')"
              @click="runRuleAction('disable', row)"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="glPageSizes"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />
    <el-drawer v-model="detailDrawerVisible" title="规则详情" size="min(720px, 92vw)" :teleported="false">
      <el-alert type="info" title="预览校验只检查规则，不占用来源，不生成正式凭证草稿。" :closable="false" />
      <dl v-if="detail" class="gl-drawer-list">
        <dt>规则名称</dt><dd>{{ detail.name || '-' }}</dd>
        <dt>规则说明</dt><dd>{{ detail.description || '-' }}</dd>
        <dt>来源类型</dt><dd>{{ detail.sourceType }}</dd>
        <dt>来源变体</dt><dd>{{ detail.sourceVariant }}</dd>
        <dt>生效日期</dt><dd>{{ detail.effectiveFrom || '-' }} 至 {{ detail.effectiveTo || '长期' }}</dd>
        <dt>版本</dt><dd>{{ detail.versionNo }}</dd>
        <dt>状态</dt><dd>{{ detail.status }}</dd>
        <dt>校验状态</dt><dd>{{ detail.validationStatus || '-' }}</dd>
      </dl>
      <el-form class="query-form" inline label-position="top">
        <el-form-item label="预览来源类型"><el-input v-model="previewContext.sourceType" placeholder="默认使用规则来源" /></el-form-item>
        <el-form-item label="来源ID"><el-input v-model="previewContext.sourceId" name="gl-rule-preview-source-id" clearable placeholder="来源ID" /></el-form-item>
        <el-form-item label="来源版本"><el-input v-model="previewContext.sourceVersion" name="gl-rule-preview-source-version" clearable placeholder="版本" /></el-form-item>
      </el-form>
      <div class="gl-toolbar">
        <el-button data-test="edit-posting-rule" :disabled="!detail || !glActionAllowed(detail, 'UPDATE')" @click="openEditRule()">编辑草稿</el-button>
        <el-button data-test="new-version-posting-rule" :disabled="!detail || !glActionAllowed(detail, 'NEW_VERSION')" :loading="actionLoading" @click="runRuleAction('newVersion')">复制新版本</el-button>
        <el-button data-test="validate-posting-rule" :disabled="!detail || !glActionAllowed(detail, 'VALIDATE')" :loading="actionLoading" @click="runRuleAction('validate')">预览校验</el-button>
        <el-button data-test="activate-posting-rule" type="primary" :disabled="!detail || !glActionAllowed(detail, 'ACTIVATE')" :loading="actionLoading" @click="runRuleAction('activate')">激活</el-button>
        <el-button data-test="disable-posting-rule" type="danger" plain :disabled="!detail || !glActionAllowed(detail, 'DISABLE')" :loading="actionLoading" @click="runRuleAction('disable')">停用</el-button>
      </div>
      <el-alert v-if="sourcePreviewWarning" type="warning" :title="sourcePreviewWarning" :closable="false" />
      <section v-if="sourcePreviewSummary && sourcePreviewLines.length > 0" data-test="posting-rule-source-preview">
        <h3 class="gl-section-title">来源预览 / 分录建议</h3>
        <div class="gl-summary-strip">
          <div><span>来源上下文</span><strong>{{ sourcePreviewContext }}</strong></div>
          <div><span>借方合计</span><strong>{{ formatGlAmount(sourcePreviewSummary.debitTotal) }}</strong></div>
          <div><span>贷方合计</span><strong>{{ formatGlAmount(sourcePreviewSummary.creditTotal) }}</strong></div>
          <div><span>事实/分录</span><strong>{{ sourcePreviewSummary.factCount ?? '-' }} / {{ sourcePreviewSummary.lineCount ?? sourcePreviewLines.length }}</strong></div>
        </div>
        <div class="table-scroll">
          <el-table :data="sourcePreviewLines" empty-text="暂无来源预览分录建议">
            <el-table-column prop="lineNo" label="行号" min-width="72" align="right" />
            <el-table-column label="科目" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ previewAccountText(row) }}</template>
            </el-table-column>
            <el-table-column label="方向" min-width="90">
              <template #default="{ row }">{{ previewDirectionText(row.direction) }}</template>
            </el-table-column>
            <el-table-column label="金额" min-width="120" align="right">
              <template #default="{ row }">{{ formatGlAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="借方" min-width="120" align="right">
              <template #default="{ row }">{{ formatGlAmount(row.debitAmount) }}</template>
            </el-table-column>
            <el-table-column label="贷方" min-width="120" align="right">
              <template #default="{ row }">{{ formatGlAmount(row.creditAmount) }}</template>
            </el-table-column>
            <el-table-column label="摘要" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ previewSummaryText(row) }}</template>
            </el-table-column>
            <el-table-column label="辅助" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ previewAuxiliaryText(row) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <div class="table-scroll">
        <el-table :data="detail?.lines ?? []" empty-text="暂无规则行">
          <el-table-column prop="normalizedFactCode" label="事实代码" min-width="160" show-overflow-tooltip />
          <el-table-column prop="direction" label="方向" min-width="100" />
          <el-table-column prop="accountCode" label="科目" min-width="140" show-overflow-tooltip />
          <el-table-column prop="summaryTemplate" label="摘要模板" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
    </el-drawer>
    <el-drawer v-model="ruleDialogVisible" :title="ruleDialogTitle" size="min(720px, 92vw)" :teleported="false">
      <el-form label-position="top">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.name" name="gl-rule-name" clearable placeholder="采购发票默认规则" />
        </el-form-item>
        <el-form-item label="规则说明">
          <el-input v-model="ruleForm.description" name="gl-rule-description" clearable placeholder="规则用途说明" />
        </el-form-item>
        <el-form-item label="来源类型">
          <el-input v-model="ruleForm.sourceType" name="gl-rule-source-type" clearable placeholder="SALES_INVOICE" />
        </el-form-item>
        <el-form-item label="来源变体">
          <el-input v-model="ruleForm.sourceVariant" name="gl-rule-source-variant" clearable placeholder="DEFAULT" />
        </el-form-item>
        <el-form-item label="生效日期">
          <div class="gl-toolbar">
            <el-date-picker v-model="ruleForm.effectiveFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="生效开始" />
            <el-date-picker v-model="ruleForm.effectiveTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" placeholder="生效结束" />
          </div>
        </el-form-item>
        <el-form-item label="规则行 JSON">
          <el-input v-model="ruleForm.linesJson" name="gl-rule-lines-json" type="textarea" :rows="8" placeholder="[]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button data-test="save-posting-rule" type="primary" :loading="actionLoading" @click="saveRule">保存</el-button>
      </template>
    </el-drawer>
  </MasterDataTableView>
</template>
