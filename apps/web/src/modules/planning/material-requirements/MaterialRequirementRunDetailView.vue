<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  materialRequirementApi,
  type MaterialRequirementAllocationRecord,
  type MaterialRequirementRequirementLineRecord,
  type MaterialRequirementRunDetailRecord,
  type MaterialRequirementSubstituteHintRecord,
  type MaterialRequirementSuggestionRecord,
  type ResourceId,
} from '../../../shared/api/materialRequirementApi'
import { useAuthStore } from '../../../stores/authStore'
import { pageItems } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import {
  coverageStatusLabel,
  failedRunSummaryLabel,
  formatMaterialRequirementDateTime,
  formatMaterialRequirementQuantity,
  hasAllowedAction,
  materialRequirementErrorMessage,
  materialRequirementPermissions,
  reasonCodeLabel,
  runStatusLabel,
  runStatusTagType,
  suggestionStatusLabel,
  suggestionStatusTagType,
  suggestionTypeLabel,
} from './materialRequirementPageHelpers'

type TraceAllocationGroupKey = 'inventory' | 'procurement' | 'production' | 'reservation'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const run = ref<MaterialRequirementRunDetailRecord | null>(null)
const requirements = ref<MaterialRequirementRequirementLineRecord[]>([])
const suggestions = ref<MaterialRequirementSuggestionRecord[]>([])
const traceDrawer = reactive({
  visible: false,
  loading: false,
  line: null as MaterialRequirementRequirementLineRecord | null,
  allocations: [] as MaterialRequirementAllocationRecord[],
  substituteHints: [] as MaterialRequirementSubstituteHintRecord[],
})
const dismissDialog = reactive({
  visible: false,
  suggestion: null as MaterialRequirementSuggestionRecord | null,
  reason: '',
})

const canManageSuggestion = computed(() => authStore.hasPermission(materialRequirementPermissions.manageSuggestion))
const canConvertRequisition = computed(() => (
  authStore.hasPermission(materialRequirementPermissions.convertRequisition)
  && authStore.hasPermission('procurement:requisition:create')
))
const canViewSalesSource = computed(() => (
  authStore.hasPermission('sales:effective-demand:view')
  || authStore.hasPermission('sales:order:view')
  || authStore.hasPermission('sales:delivery-plan:view')
))
const canViewBomSource = computed(() => authStore.hasPermission('material:bom:view'))
const canViewInventorySource = computed(() => (
  authStore.hasPermission('inventory:balance:view')
  || authStore.hasPermission('inventory:movement:view')
))
const canViewProcurementSource = computed(() => (
  authStore.hasPermission('procurement:supply:view')
  || authStore.hasPermission('procurement:order:view')
))
const canViewProductionSource = computed(() => authStore.hasPermission('production:work-order:view'))
const suggestionsDisabled = computed(() => (
  run.value?.status === 'STALE'
  || run.value?.status === 'EXPIRED'
  || run.value?.stale === true
  || run.value?.expired === true
))
const failedRunSummary = computed(() => (
  run.value?.status === 'FAILED'
    ? failedRunSummaryLabel(run.value.failureCode, run.value.failureSummary)
    : ''
))
const runId = computed<ResourceId>(() => run.value?.id ?? routeId())
const traceAllocationGroups: Array<{
  key: TraceAllocationGroupKey
  title: string
  restrictedTitle: string
}> = [
  { key: 'inventory', title: '项目/公共库存', restrictedTitle: '库存来源权限受限' },
  { key: 'procurement', title: '采购供给', restrictedTitle: '采购来源权限受限' },
  { key: 'production', title: '工单供给', restrictedTitle: '生产来源权限受限' },
  { key: 'reservation', title: '预留占用', restrictedTitle: '库存来源权限受限' },
]

function routeId(): ResourceId {
  const rawId = String(route.params.id ?? '')
  return /^\d+$/.test(rawId) ? +rawId : rawId
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    const loadedRun = await materialRequirementApi.runs.get(routeId())
    run.value = loadedRun
    const [requirementPage, suggestionPage] = await Promise.all([
      materialRequirementApi.runs.requirements(loadedRun.id, { page: 1, pageSize: 10 }),
      materialRequirementApi.runs.suggestions(loadedRun.id, { page: 1, pageSize: 10 }),
    ])
    requirements.value = pageItems(requirementPage)
    suggestions.value = pageItems(suggestionPage)
  } catch (caught) {
    requirements.value = []
    suggestions.value = []
    error.value = materialRequirementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function refreshSuggestions() {
  if (!run.value) {
    return
  }
  const suggestionPage = await materialRequirementApi.runs.suggestions(run.value.id, { page: 1, pageSize: 10 })
  suggestions.value = pageItems(suggestionPage)
}

async function openTrace(line: MaterialRequirementRequirementLineRecord) {
  traceDrawer.visible = true
  traceDrawer.loading = true
  traceDrawer.line = line
  traceDrawer.allocations = []
  traceDrawer.substituteHints = []
  try {
    const [allocationPage, hintPage] = await Promise.all([
      materialRequirementApi.runs.allocations(runId.value, {
        requirementLineId: line.id,
        page: 1,
        pageSize: 50,
      }),
      materialRequirementApi.runs.substituteHints(runId.value, {
        requirementLineId: line.id,
        page: 1,
        pageSize: 10,
      }),
    ])
    traceDrawer.allocations = pageItems(allocationPage).filter((record) => String(record.requirementLineId) === String(line.id))
    traceDrawer.substituteHints = pageItems(hintPage).filter((record) => String(record.requirementLineId) === String(line.id))
  } catch (caught) {
    actionError.value = materialRequirementErrorMessage(caught)
  } finally {
    traceDrawer.loading = false
  }
}

function lineProjectText(line?: MaterialRequirementRequirementLineRecord | null): string {
  if (!line) {
    return '需求行未返回'
  }
  const project = [line.projectNo, line.projectName].filter(Boolean).join(' ')
  if (project) {
    return project
  }
  return line.projectId ? '项目未返回' : '公共需求'
}

function lineDemandSourceText(line?: MaterialRequirementRequirementLineRecord | null): string {
  if (!line) {
    return '订单/计划未返回'
  }
  const source = line.demandSourceNo || [line.orderNo, line.deliveryPlanNo].filter(Boolean).join('/')
  return `${source || '订单/计划未返回'} · ${lineProjectText(line)}`
}

function requirementDemandSourceText(line: MaterialRequirementRequirementLineRecord): string {
  if (!canViewSalesSource.value) {
    return '来源权限受限'
  }
  const source = line.demandSourceNo || line.orderNo || '订单/计划未返回'
  return `${lineProjectText(line)} · ${source}`
}

function lineFinishedBomText(line?: MaterialRequirementRequirementLineRecord | null): string {
  if (!line) {
    return '成品未返回 · 暂无 BOM 来源'
  }
  const finished = [line.finishedMaterialCode, line.finishedMaterialName].filter(Boolean).join(' ')
  return `${finished || '成品未返回'} · ${line.bomVersionNo || '暂无 BOM 来源'}`
}

function requirementFinishedBomText(line: MaterialRequirementRequirementLineRecord): string {
  if (!canViewSalesSource.value || !canViewBomSource.value) {
    return '来源权限受限'
  }
  return lineFinishedBomText(line)
}

function lineBomPathText(line?: MaterialRequirementRequirementLineRecord | null): string {
  return line?.bomPath || 'BOM 路径未返回'
}

function estimatedAvailableText(line: MaterialRequirementRequirementLineRecord): string {
  return `预计可用 ${line.estimatedAvailableDate || '暂无可承诺日期'}`
}

function ownershipTypeLabel(type?: string | null): string {
  const labels: Record<string, string> = {
    PROJECT: '项目库存',
    PUBLIC: '公共库存',
  }
  return labels[String(type ?? '')] ?? String(type ?? '所有权未返回')
}

function supplyTypeLabel(type?: string | null): string {
  const labels: Record<string, string> = {
    PROJECT_STOCK: '项目库存',
    PUBLIC_STOCK: '公共库存',
    RESERVED_STOCK: '预留占用库存',
    PURCHASE_SUPPLY: '采购供给',
    WORK_ORDER_SUPPLY: '工单供给',
    PRODUCTION_SUPPLY: '生产供给',
  }
  return labels[String(type ?? '')] ?? String(type ?? '供给类型未返回')
}

function allocationSourceText(row: MaterialRequirementAllocationRecord): string {
  return `${row.supplyTypeName || supplyTypeLabel(row.supplyType)} ${row.sourceNo || '来源编号未返回'}`
}

function allocationOwnershipText(row: MaterialRequirementAllocationRecord): string {
  return `${ownershipTypeLabel(row.ownershipType)} ${row.projectNo || ''}`.trim()
}

function allocationGroupKey(row: MaterialRequirementAllocationRecord): TraceAllocationGroupKey {
  const type = String(row.supplyType ?? '').toUpperCase()
  const name = String(row.supplyTypeName ?? '')
  const reason = String(row.excludedReasonCode ?? '').toUpperCase()
  if (type.includes('RESERV') || name.includes('预留') || name.includes('占用') || reason === 'STOCK_RESERVED_OR_OCCUPIED') {
    return 'reservation'
  }
  if (type.includes('PURCHASE') || type.includes('PROCUREMENT') || name.includes('采购')) {
    return 'procurement'
  }
  if (type.includes('WORK_ORDER') || type.includes('PRODUCTION') || name.includes('工单') || name.includes('生产')) {
    return 'production'
  }
  return 'inventory'
}

function allocationRows(group: TraceAllocationGroupKey): MaterialRequirementAllocationRecord[] {
  return traceDrawer.allocations.filter((row) => allocationGroupKey(row) === group)
}

function canViewAllocationGroup(group: TraceAllocationGroupKey): boolean {
  if (group === 'procurement') {
    return canViewProcurementSource.value
  }
  if (group === 'production') {
    return canViewProductionSource.value
  }
  return canViewInventorySource.value
}

function isOutsourcedProductionSuggestion(row: MaterialRequirementSuggestionRecord): boolean {
  return row.suggestionType === 'PRODUCTION_ORDER'
    && String(row.materialSourceType ?? '').toUpperCase() === 'OUTSOURCED'
}

function suggestionTypeText(row: MaterialRequirementSuggestionRecord): string {
  if (isOutsourcedProductionSuggestion(row)) {
    return '外协生产建议，027 后可执行'
  }
  return suggestionTypeLabel(row.suggestionType)
}

function suggestionDescription(row: MaterialRequirementSuggestionRecord): string {
  if (isOutsourcedProductionSuggestion(row) && row.conversionAllowed === false) {
    return row.actionDisabledReason || '外协生产建议，027 后可执行'
  }
  return row.actionDisabledReason || row.reasonMessage || reasonCodeLabel(row.reasonCode)
}

function suggestionProjectMaterialText(row: MaterialRequirementSuggestionRecord): string {
  const material = `${row.materialCode} ${row.materialName}`.trim()
  if (!canViewSalesSource.value) {
    return `来源权限受限 · ${material}`
  }
  return `${[row.projectNo, row.projectName].filter(Boolean).join(' ') || '公共需求'} · ${material}`
}

function canConfirmSuggestion(row: MaterialRequirementSuggestionRecord): boolean {
  return !suggestionsDisabled.value
    && canManageSuggestion.value
    && row.status === 'OPEN'
    && hasAllowedAction(row, 'CONFIRM')
}

function canDismissSuggestion(row: MaterialRequirementSuggestionRecord): boolean {
  return !suggestionsDisabled.value
    && canManageSuggestion.value
    && (row.status === 'OPEN' || row.status === 'CONFIRMED')
    && hasAllowedAction(row, 'DISMISS')
}

function canConvertSuggestion(row: MaterialRequirementSuggestionRecord): boolean {
  return !suggestionsDisabled.value
    && canConvertRequisition.value
    && row.status === 'CONFIRMED'
    && row.suggestionType === 'PURCHASE_REQUISITION'
    && row.conversionAllowed !== false
    && hasAllowedAction(row, 'CONVERT_REQUISITION')
}

async function confirmSuggestion(row: MaterialRequirementSuggestionRecord) {
  if (!canConfirmSuggestion(row) || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await materialRequirementApi.suggestions.confirm(row.id, {
      version: row.version,
      idempotencyKey: createIdempotencyKey('material-requirement-suggestion-confirm'),
    })
    await refreshSuggestions()
  } catch (caught) {
    actionError.value = materialRequirementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function openDismissDialog(row: MaterialRequirementSuggestionRecord) {
  dismissDialog.suggestion = row
  dismissDialog.reason = ''
  dismissDialog.visible = true
}

async function submitDismissSuggestion() {
  const suggestion = dismissDialog.suggestion
  if (!suggestion || !canDismissSuggestion(suggestion) || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await materialRequirementApi.suggestions.dismiss(suggestion.id, {
      version: suggestion.version,
      reason: dismissDialog.reason,
      idempotencyKey: createIdempotencyKey('material-requirement-suggestion-dismiss'),
    })
    dismissDialog.visible = false
    await refreshSuggestions()
  } catch (caught) {
    actionError.value = materialRequirementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function convertSuggestion(row: MaterialRequirementSuggestionRecord) {
  if (!canConvertSuggestion(row) || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    const converted = await materialRequirementApi.suggestions.convertRequisition(row.id, {
      version: row.version,
      idempotencyKey: createIdempotencyKey('material-requirement-suggestion-convert'),
    })
    if (converted.convertedRequisitionId) {
      await router.push({
        name: 'procurement-requisition-detail',
        params: { id: String(converted.convertedRequisitionId) },
        query: { returnTo: route.fullPath },
      })
      return
    }
    await refreshSuggestions()
  } catch (caught) {
    actionError.value = materialRequirementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <MasterDataTableView title="订单缺料分析详情" description="查看 026 快照的需求、覆盖、短缺、建议和来源追溯。">
    <template #actions>
      <el-button @click="router.push({ name: 'planning-material-requirements' })">返回列表</el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon :closable="false" />
      <el-alert v-if="actionError" class="page-alert" type="error" :title="actionError" show-icon :closable="false" />
      <el-alert
        v-if="failedRunSummary"
        class="page-alert"
        type="error"
        :title="failedRunSummary"
        show-icon
        :closable="false"
      />
      <el-alert
        v-if="suggestionsDisabled"
        class="page-alert"
        type="warning"
        title="来源已变化，建议动作已禁用"
        show-icon
        :closable="false"
      />
    </template>

    <section class="section-block">
      <div class="detail-title-line">
        <div>
          <h2>{{ run?.runNo || '快照加载中' }}</h2>
          <p>{{ run?.scopeSummary || '分析范围加载中' }}</p>
        </div>
        <el-tag v-if="run" :type="runStatusTagType(run.status)">{{ runStatusLabel(run.status, run.statusName) }}</el-tag>
      </div>
      <div class="summary-strip">
        <div><strong>需求行数</strong><span>{{ run?.requirementLineCount ?? '-' }}</span></div>
        <div><strong>项目数</strong><span>{{ run?.projectCount ?? '-' }}</span></div>
        <div><strong>短缺物料</strong><span>{{ run?.shortageMaterialCount ?? '-' }}</span></div>
        <div><strong>采购建议</strong><span>{{ run?.purchaseSuggestionCount ?? '-' }}</span></div>
        <div><strong>生产建议</strong><span>{{ run?.productionSuggestionCount ?? '-' }}</span></div>
        <div><strong>异常/受限</strong><span>{{ run?.exceptionCount ?? 0 }}</span></div>
        <div><strong>基准时间</strong><span>{{ formatMaterialRequirementDateTime(run?.asOfTime) }}</span></div>
      </div>
    </section>

    <el-tabs model-value="requirements" class="workbench-tabs">
      <el-tab-pane label="缺料结果" name="requirements">
        <div class="table-scroll">
          <div v-if="loading" class="inline-loading" role="status">加载中</div>
          <el-table :data="requirements" :empty-text="loading ? '加载中' : '暂无缺料结果'" stripe>
            <el-table-column label="状态" min-width="110">
              <template #default="{ row }">{{ coverageStatusLabel(row.coverageStatus) }}</template>
            </el-table-column>
            <el-table-column label="项目/订单/计划" min-width="230" show-overflow-tooltip>
              <template #default="{ row }">{{ requirementDemandSourceText(row) }}</template>
            </el-table-column>
            <el-table-column label="成品/BOM" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ requirementFinishedBomText(row) }}</template>
            </el-table-column>
            <el-table-column label="需求物料" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="需求量" min-width="100" align="right">
              <template #default="{ row }">需求 {{ formatMaterialRequirementQuantity(row.requiredQuantity) }}</template>
            </el-table-column>
            <el-table-column label="覆盖量" min-width="100" align="right">
              <template #default="{ row }">覆盖 {{ formatMaterialRequirementQuantity(row.coveredQuantity) }}</template>
            </el-table-column>
            <el-table-column label="短缺量" min-width="100" align="right">
              <template #default="{ row }">短缺 {{ formatMaterialRequirementQuantity(row.shortageQuantity) }}</template>
            </el-table-column>
            <el-table-column label="预计可用日期" min-width="150">
              <template #default="{ row }">{{ estimatedAvailableText(row) }}</template>
            </el-table-column>
            <el-table-column label="建议类型" min-width="140">
              <template #default="{ row }">{{ suggestionTypeLabel(row.suggestionType) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button :data-test="`trace-requirement-${row.id}`" text type="primary" @click="openTrace(row)">追溯</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
      <el-tab-pane label="物料汇总" name="summary">
        <div class="table-scroll">
          <el-table :data="requirements" empty-text="暂无物料汇总" stripe>
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="需求/覆盖/短缺" min-width="220" align="right">
              <template #default="{ row }">
                {{ formatMaterialRequirementQuantity(row.requiredQuantity) }} /
                {{ formatMaterialRequirementQuantity(row.coveredQuantity) }} /
                {{ formatMaterialRequirementQuantity(row.shortageQuantity) }}
              </template>
            </el-table-column>
            <el-table-column label="排除原因" min-width="170">
              <template #default="{ row }">{{ reasonCodeLabel(row.exceptionReasonCode) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
      <el-tab-pane label="建议查看" name="suggestions">
        <div class="table-scroll">
          <el-table :data="suggestions" empty-text="暂无供给建议" stripe>
            <el-table-column prop="suggestionNo" label="建议编号" min-width="140" fixed show-overflow-tooltip />
            <el-table-column label="状态" min-width="110">
              <template #default="{ row }">
                <el-tag :type="suggestionStatusTagType(row.status)">{{ suggestionStatusLabel(row.status, row.statusName) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="建议类型" min-width="150">
              <template #default="{ row }">{{ suggestionTypeText(row) }}</template>
            </el-table-column>
            <el-table-column label="项目/物料" min-width="250" show-overflow-tooltip>
              <template #default="{ row }">{{ suggestionProjectMaterialText(row) }}</template>
            </el-table-column>
            <el-table-column label="建议数量" min-width="120" align="right">
              <template #default="{ row }">{{ formatMaterialRequirementQuantity(row.suggestedQuantity) }} {{ row.unitName || '' }}</template>
            </el-table-column>
            <el-table-column label="建议日期" min-width="120">
              <template #default="{ row }">{{ row.suggestedDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="说明" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ suggestionDescription(row) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canConfirmSuggestion(row)"
                  :data-test="`confirm-suggestion-${row.id}`"
                  text
                  type="primary"
                  :loading="actionLoading"
                  @click="confirmSuggestion(row)"
                >
                  确认
                </el-button>
                <el-button
                  v-if="canDismissSuggestion(row)"
                  :data-test="`dismiss-suggestion-${row.id}`"
                  text
                  type="danger"
                  :loading="actionLoading"
                  @click="openDismissDialog(row)"
                >
                  驳回
                </el-button>
                <el-button
                  v-if="canConvertSuggestion(row)"
                  :data-test="`convert-suggestion-${row.id}`"
                  text
                  type="success"
                  :loading="actionLoading"
                  @click="convertSuggestion(row)"
                >
                  转请购
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="traceDrawer.visible" title="来源追溯" :size="'min(880px, 94vw)'" :teleported="false">
      <section class="trace-section">
        <h3>需求链</h3>
        <p v-if="canViewSalesSource">{{ lineDemandSourceText(traceDrawer.line) }}</p>
        <el-alert v-else title="销售来源权限受限" type="warning" :closable="false" show-icon />
      </section>
      <section class="trace-section">
        <h3>BOM</h3>
        <p v-if="canViewBomSource">{{ lineFinishedBomText(traceDrawer.line) }} · {{ lineBomPathText(traceDrawer.line) }}</p>
        <el-alert v-else title="BOM 来源权限受限" type="warning" :closable="false" show-icon />
      </section>
      <section v-for="group in traceAllocationGroups" :key="group.key" class="trace-section">
        <h3>{{ group.title }}来源</h3>
        <el-alert
          v-if="!canViewAllocationGroup(group.key)"
          :title="group.restrictedTitle"
          type="warning"
          :closable="false"
          show-icon
        />
        <div v-else class="table-scroll">
          <div v-if="traceDrawer.loading" class="inline-loading" role="status">加载中</div>
          <el-table :data="allocationRows(group.key)" :empty-text="traceDrawer.loading ? '加载中' : '暂无' + group.title" stripe>
            <el-table-column label="供给来源" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ allocationSourceText(row) }}</template>
            </el-table-column>
            <el-table-column label="所有权/项目" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ allocationOwnershipText(row) }}</template>
            </el-table-column>
            <el-table-column label="可用日期" min-width="120">
              <template #default="{ row }">{{ row.availableDate || '暂无可承诺日期' }}</template>
            </el-table-column>
            <el-table-column label="分配数量" min-width="120" align="right">
              <template #default="{ row }">{{ formatMaterialRequirementQuantity(row.allocatedQuantity) }}</template>
            </el-table-column>
            <el-table-column label="排除原因" min-width="150">
              <template #default="{ row }">{{ reasonCodeLabel(row.excludedReasonCode) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="trace-section">
        <h3>替代料提示</h3>
        <el-alert v-if="!canViewBomSource" title="BOM 来源权限受限" type="warning" :closable="false" show-icon />
        <div v-else class="table-scroll">
          <el-table :data="traceDrawer.substituteHints" empty-text="暂无替代料提示" stripe>
            <el-table-column label="主物料" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="替代物料" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.substituteMaterialCode }} {{ row.substituteMaterialName }}</template>
            </el-table-column>
            <el-table-column label="提示" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ row.hintMessage || '仅供人工评估，不抵扣缺料' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="trace-section">
        <h3>排除原因</h3>
        <p>{{ reasonCodeLabel(traceDrawer.line?.exceptionReasonCode) }}</p>
      </section>
    </el-drawer>

    <el-dialog v-model="dismissDialog.visible" title="驳回建议" :teleported="false" width="460px">
      <el-form label-position="top">
        <el-form-item label="驳回原因">
          <el-input
            v-model="dismissDialog.reason"
            data-test="dismiss-suggestion-reason"
            type="textarea"
            :rows="3"
            placeholder="请输入人工驳回原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dismissDialog.visible = false">取消</el-button>
        <el-button
          data-test="submit-dismiss-suggestion"
          type="danger"
          :loading="actionLoading"
          @click="submitDismissSuggestion"
        >
          确认驳回
        </el-button>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>

<style scoped>
.detail-title-line {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.detail-title-line h2 {
  margin: 0 0 6px;
  font-size: 18px;
}

.detail-title-line p {
  margin: 0;
  color: #64748b;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(116px, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.summary-strip div {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
}

.summary-strip strong {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}

.summary-strip span {
  display: block;
  margin-top: 4px;
  font-weight: 600;
}

.workbench-tabs {
  margin-top: 14px;
}

.inline-loading {
  padding: 10px 12px;
  color: #475569;
  font-size: 13px;
}

.trace-section + .trace-section {
  margin-top: 16px;
}

.trace-section h3 {
  margin: 0 0 8px;
  font-size: 14px;
}
</style>
