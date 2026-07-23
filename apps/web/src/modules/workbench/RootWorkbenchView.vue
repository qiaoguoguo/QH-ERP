<script setup lang="ts">
import type { Component } from 'vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  Box,
  Briefcase,
  Calendar,
  CircleCheck,
  Cpu,
  Document,
  Refresh,
  ShoppingCart,
  TrendCharts,
  Warning,
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import {
  documentPlatformApi,
  type ApprovalTaskRecord,
  type DocumentTaskRecord,
  type DocumentTaskStatus,
} from '../../shared/api/documentPlatformApi'
import {
  businessReportingApi,
  type ExceptionReportRow,
  type OperatingFinanceOverviewRecord,
  type ReportOverviewRecord,
} from '../../shared/api/businessReportingApi'
import { useAuthStore } from '../../stores/authStore'
import {
  documentTaskStatusTagType,
  platformErrorMessage,
} from '../platform/platformPageHelpers'
import { statusText } from '../reports/operatingFinanceReportHelpers'
import {
  approvalTitle,
  clampProgress,
  documentTaskRoute,
  exceptionRowKey,
  exceptionSeverityText,
  exceptionTypeText,
  formatWorkbenchDateTime,
  formatWorkbenchMoney,
  formatWorkbenchNumber,
  mergeTaskPages,
  taskDisplayName,
  taskStatusText,
} from './workbenchPageHelpers'

interface QuickEntry {
  label: string
  to: string
  permission: string
  icon: Component
}

interface KeyStatusRow {
  label: string
  value: string
  rawStatus: string
  to: string | null
  actionLabel: string
}

const authStore = useAuthStore()
const router = useRouter()

const quickEntryDefinitions: QuickEntry[] = [
  { label: '销售项目', to: '/sales/projects/create', permission: 'sales:project:create', icon: Briefcase },
  { label: '采购请购', to: '/procurement/requisitions/create', permission: 'procurement:requisition:create', icon: ShoppingCart },
  { label: '生产工单', to: '/production/work-orders/create', permission: 'production:work-order:create', icon: Cpu },
  { label: '库存单据', to: '/inventory/documents/create', permission: 'inventory:document:create', icon: Box },
  { label: '质量检验', to: '/quality/inspections', permission: 'quality:inspection:view', icon: CircleCheck },
]
const activeTaskStatuses: DocumentTaskStatus[] = ['QUEUED', 'RUNNING', 'READY_TO_COMMIT']
const failedTaskStatuses: DocumentTaskStatus[] = ['VALIDATION_FAILED', 'FAILED']

const canViewApprovals = computed(() => authStore.hasPermission('platform:todo:view'))
const canViewDocumentTasks = computed(() => authStore.hasPermission('platform:document-task:view'))
const canViewOverview = computed(() => authStore.hasPermission('report:overview:view'))
const canViewOperatingFinance = computed(() => authStore.hasPermission('report:operating-finance:view'))
const canViewExceptions = computed(() => authStore.hasPermission('report:exception:view'))
const hasWorkflowPermission = computed(() => canViewApprovals.value || canViewDocumentTasks.value)
const hasSideContent = computed(() => canViewOverview.value || canViewOperatingFinance.value)
const quickEntries = computed(() =>
  quickEntryDefinitions.filter((entry) => authStore.hasPermission(entry.permission)),
)

const approvalItems = ref<ApprovalTaskRecord[]>([])
const approvalTotal = ref(0)
const approvalLoading = ref(false)
const approvalError = ref('')
const activeTaskItems = ref<DocumentTaskRecord[]>([])
const activeTaskTotal = ref(0)
const activeTaskLoading = ref(false)
const activeTaskError = ref('')
const failedTaskItems = ref<DocumentTaskRecord[]>([])
const failedTaskTotal = ref(0)
const failedTaskLoading = ref(false)
const failedTaskError = ref('')
const overviewRecord = ref<ReportOverviewRecord | null>(null)
const overviewLoading = ref(false)
const overviewError = ref('')
const operatingFinanceRecord = ref<OperatingFinanceOverviewRecord | null>(null)
const operatingFinanceLoading = ref(false)
const operatingFinanceError = ref('')
const exceptionItems = ref<ExceptionReportRow[]>([])
const exceptionsLoading = ref(false)
const exceptionsError = ref('')
const lastUpdatedAt = ref<Date | null>(null)
let loadGeneration = 0
let disposed = false

const refreshing = computed(() =>
  approvalLoading.value
  || activeTaskLoading.value
  || failedTaskLoading.value
  || overviewLoading.value
  || operatingFinanceLoading.value
  || exceptionsLoading.value,
)
const lastUpdatedText = computed(() => {
  if (!lastUpdatedAt.value) {
    return '尚未更新'
  }
  return `更新于 ${lastUpdatedAt.value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })}`
})
const workflowEmpty = computed(() =>
  !approvalLoading.value
  && !activeTaskLoading.value
  && !failedTaskLoading.value
  && !approvalError.value
  && !activeTaskError.value
  && !failedTaskError.value
  && approvalItems.value.length === 0
  && activeTaskItems.value.length === 0
  && failedTaskItems.value.length === 0,
)
const overviewEmpty = computed(() => {
  const record = overviewRecord.value
  return record
    ? [
        record.salesShipmentAmount,
        record.purchaseReceiptAmount,
        record.productionCompletedQuantity,
        record.costAmount,
      ].every((value) => Number(value) === 0)
    : false
})
const overviewMetrics = computed(() => {
  const record = overviewRecord.value
  return record
    ? [
        { label: '销售出库经营金额', value: formatWorkbenchMoney(record.salesShipmentAmount) },
        { label: '采购入库经营金额', value: formatWorkbenchMoney(record.purchaseReceiptAmount) },
        { label: '生产完工数量', value: formatWorkbenchNumber(record.productionCompletedQuantity) },
        { label: '成本归集金额', value: formatWorkbenchMoney(record.costAmount) },
      ]
    : []
})
const keyStatusRows = computed<KeyStatusRow[]>(() => {
  const record = operatingFinanceRecord.value
  if (!record) {
    return []
  }
  return [
    {
      label: '业务月结',
      value: businessStatusText(record.businessPeriodStatus),
      rawStatus: record.businessPeriodStatus,
      to: authStore.hasPermission('system:business-period-close:view') ? '/period-close/runs' : null,
      actionLabel: '进入月结',
    },
    {
      label: '会计期间',
      value: businessStatusText(record.accountingPeriodStatus),
      rawStatus: record.accountingPeriodStatus,
      to: authStore.hasPermission('gl:period:view') ? '/gl/accounting-periods' : null,
      actionLabel: '管理期间',
    },
    {
      label: '财务关闭',
      value: businessStatusText(record.financialCloseStatus),
      rawStatus: record.financialCloseStatus,
      to: authStore.hasPermission('financial-close:period:view') ? '/gl/financial-close' : null,
      actionLabel: '查看详情',
    },
    {
      label: '经营/会计定稿',
      value: businessStatusText(record.finalityStatus),
      rawStatus: record.finalityStatus,
      to: null,
      actionLabel: '',
    },
  ]
})

function currentRun(runId: number): boolean {
  return !disposed && runId === loadGeneration
}

function sectionError(cause: unknown, fallback: string): string {
  const message = platformErrorMessage(cause)
  return message === '请求失败，请重试' ? fallback : message
}

async function loadApprovals(runId = loadGeneration) {
  if (!canViewApprovals.value) {
    return
  }
  approvalLoading.value = true
  approvalError.value = ''
  try {
    const result = await documentPlatformApi.approvalTasks.list({ scope: 'TODO', page: 1, pageSize: 10 })
    if (currentRun(runId)) {
      approvalItems.value = (result.items ?? []).slice(0, 3)
      approvalTotal.value = Number(result.total ?? 0)
    }
  } catch (cause) {
    if (currentRun(runId)) {
      approvalError.value = sectionError(cause, '审批待办加载失败')
    }
  } finally {
    if (currentRun(runId)) {
      approvalLoading.value = false
    }
  }
}

async function loadTaskGroup(kind: 'active' | 'failed', runId = loadGeneration) {
  if (!canViewDocumentTasks.value) {
    return
  }
  const statuses = kind === 'active' ? activeTaskStatuses : failedTaskStatuses
  const loading = kind === 'active' ? activeTaskLoading : failedTaskLoading
  const error = kind === 'active' ? activeTaskError : failedTaskError
  loading.value = true
  error.value = ''
  try {
    const pages = await Promise.all(statuses.map((status) =>
      documentPlatformApi.documentTasks.list({ status, page: 1, pageSize: 10 })))
    if (currentRun(runId)) {
      const merged = mergeTaskPages(pages, kind === 'active' ? 3 : 2)
      if (kind === 'active') {
        activeTaskItems.value = merged.items
        activeTaskTotal.value = merged.total
      } else {
        failedTaskItems.value = merged.items
        failedTaskTotal.value = merged.total
      }
    }
  } catch (cause) {
    if (currentRun(runId)) {
      error.value = sectionError(cause, kind === 'active' ? '处理中任务加载失败' : '失败任务加载失败')
    }
  } finally {
    if (currentRun(runId)) {
      loading.value = false
    }
  }
}

async function loadOverview(runId = loadGeneration) {
  if (!canViewOverview.value) {
    return
  }
  overviewLoading.value = true
  overviewError.value = ''
  try {
    const result = await businessReportingApi.overview.get()
    if (currentRun(runId)) {
      overviewRecord.value = result
    }
  } catch (cause) {
    if (currentRun(runId)) {
      overviewError.value = sectionError(cause, '经营概况加载失败')
    }
  } finally {
    if (currentRun(runId)) {
      overviewLoading.value = false
    }
  }
}

async function loadOperatingFinance(runId = loadGeneration) {
  if (!canViewOperatingFinance.value) {
    return
  }
  operatingFinanceLoading.value = true
  operatingFinanceError.value = ''
  try {
    const result = await businessReportingApi.operatingFinanceOverview.get({ analysisMode: 'LIVE' })
    if (currentRun(runId)) {
      operatingFinanceRecord.value = result
    }
  } catch (cause) {
    if (currentRun(runId)) {
      operatingFinanceError.value = sectionError(cause, '关键状态加载失败')
    }
  } finally {
    if (currentRun(runId)) {
      operatingFinanceLoading.value = false
    }
  }
}

async function loadExceptions(runId = loadGeneration) {
  if (!canViewExceptions.value) {
    return
  }
  exceptionsLoading.value = true
  exceptionsError.value = ''
  try {
    const result = await businessReportingApi.exceptions.list({ page: 1, pageSize: 10 })
    if (currentRun(runId)) {
      exceptionItems.value = (result.items ?? []).slice(0, 4)
    }
  } catch (cause) {
    if (currentRun(runId)) {
      exceptionsError.value = sectionError(cause, '业务关注加载失败')
    }
  } finally {
    if (currentRun(runId)) {
      exceptionsLoading.value = false
    }
  }
}

async function refreshAll() {
  const runId = ++loadGeneration
  const requests: Promise<unknown>[] = []
  if (canViewApprovals.value) {
    requests.push(loadApprovals(runId))
  }
  if (canViewDocumentTasks.value) {
    requests.push(loadTaskGroup('active', runId), loadTaskGroup('failed', runId))
  }
  if (canViewOverview.value) {
    requests.push(loadOverview(runId))
  }
  if (canViewOperatingFinance.value) {
    requests.push(loadOperatingFinance(runId))
  }
  if (canViewExceptions.value) {
    requests.push(loadExceptions(runId))
  }
  await Promise.allSettled(requests)
  if (currentRun(runId) && requests.length > 0) {
    lastUpdatedAt.value = new Date()
  }
}

function navigate(to: string) {
  void router.push(to)
}

function businessStatusText(rawStatus: string | null | undefined): string {
  return rawStatus === 'NOT_CLOSED' ? '未关闭' : statusText(rawStatus)
}

function businessStatusType(rawStatus: string): 'success' | 'warning' | 'danger' | 'info' {
  if (['OPEN', 'CURRENT', 'FINAL', 'MATCHED', 'COMPLETE'].includes(rawStatus)) {
    return 'success'
  }
  if (['CLOSED', 'FAILED', 'DIFFERENT'].includes(rawStatus)) {
    return 'danger'
  }
  if (['NOT_CLOSED', 'PREVIEW', 'INCOMPLETE', 'STALE'].includes(rawStatus)) {
    return 'warning'
  }
  return 'info'
}

function exceptionTagType(severity: string): 'danger' | 'warning' {
  return severity === 'CRITICAL' ? 'danger' : 'warning'
}

function taskSummary(record: DocumentTaskRecord): string {
  return record.objectName?.trim() || record.objectNo?.trim() || record.taskNo
}

function taskFailureReason(record: DocumentTaskRecord): string {
  return record.errorMessage?.trim() || '请进入任务中心查看失败原因'
}

onMounted(() => {
  void refreshAll()
})
onBeforeUnmount(() => {
  disposed = true
  loadGeneration += 1
})
</script>

<template>
  <section class="module-page workbench-page">
    <header class="page-heading workbench-heading">
      <div>
        <h1 data-test="workbench-title">工作台</h1>
        <p>从待处理到异常处置，按业务节奏推进今日工作。</p>
      </div>
      <div class="workbench-heading__actions">
        <el-button
          data-test="workbench-refresh"
          :icon="Refresh"
          :loading="refreshing"
          :disabled="refreshing"
          @click="refreshAll"
        >
          刷新
        </el-button>
        <span class="workbench-updated">{{ lastUpdatedText }}</span>
      </div>
    </header>

    <section v-if="quickEntries.length" class="workbench-section workbench-quick">
      <div class="workbench-section__heading">
        <div>
          <h2>常用入口</h2>
          <p>只展示当前账号可使用的常用业务入口。</p>
        </div>
      </div>
      <div class="workbench-quick__grid">
        <el-button
          v-for="entry in quickEntries"
          :key="entry.to"
          data-test="workbench-quick-entry"
          class="workbench-quick__button"
          @click="navigate(entry.to)"
        >
          <el-icon><component :is="entry.icon" /></el-icon>
          <span>{{ entry.label }}</span>
        </el-button>
      </div>
    </section>

    <div
      v-if="hasWorkflowPermission || hasSideContent"
      :class="['workbench-primary-grid', { 'workbench-primary-grid--single': !hasWorkflowPermission || !hasSideContent }]"
    >
      <section v-if="hasWorkflowPermission" class="workbench-section workbench-flow">
        <div class="workbench-section__heading">
          <div>
            <h2>我的工作流</h2>
            <p>集中查看需要本人处理、系统处理中和需要重新处理的事项。</p>
          </div>
        </div>

        <div v-if="workflowEmpty" class="workbench-inline-empty">当前没有需要处理的事项</div>

        <section v-if="canViewApprovals" class="workbench-flow-group">
          <header class="workbench-flow-group__heading">
            <div class="workbench-flow-group__title">
              <Document class="workbench-flow-group__icon" />
              <h3>等待我处理</h3>
              <span class="workbench-count">{{ approvalTotal }}</span>
            </div>
            <router-link class="workbench-section-link" to="/platform/approvals">查看全部</router-link>
          </header>
          <div v-if="approvalLoading && approvalItems.length === 0" class="workbench-loading">审批待办加载中</div>
          <div v-else-if="approvalError" class="workbench-inline-error">
            <span>{{ approvalError }}</span>
            <el-button text type="primary" @click="loadApprovals()">重新加载</el-button>
          </div>
          <div v-else-if="approvalItems.length === 0" class="workbench-group-empty">暂无审批待办</div>
          <div v-else class="workbench-flow-list">
            <article v-for="item in approvalItems" :key="String(item.id)" class="workbench-flow-row">
              <div class="workbench-flow-row__content">
                <strong>{{ approvalTitle(item) }}</strong>
                <span>{{ item.currentStepName || '待处理审批' }} · {{ item.applicantName || '申请人未记录' }}</span>
              </div>
              <time>{{ formatWorkbenchDateTime(item.assignedAt) }}</time>
              <router-link class="action-button-link" to="/platform/approvals">
                <el-button tag="span" text type="primary" size="small">处理</el-button>
              </router-link>
            </article>
          </div>
        </section>

        <section v-if="canViewDocumentTasks" class="workbench-flow-group">
          <header class="workbench-flow-group__heading">
            <div class="workbench-flow-group__title">
              <TrendCharts class="workbench-flow-group__icon workbench-flow-group__icon--success" />
              <h3>系统处理中</h3>
              <span class="workbench-count">{{ activeTaskTotal }}</span>
            </div>
            <router-link class="workbench-section-link" to="/platform/document-tasks">查看全部</router-link>
          </header>
          <div v-if="activeTaskLoading && activeTaskItems.length === 0" class="workbench-loading">处理中任务加载中</div>
          <div v-else-if="activeTaskError" class="workbench-inline-error">
            <span>{{ activeTaskError }}</span>
            <el-button text type="primary" @click="loadTaskGroup('active')">重新加载</el-button>
          </div>
          <div v-else-if="activeTaskItems.length === 0" class="workbench-group-empty">暂无系统处理中的任务</div>
          <div v-else class="workbench-flow-list">
            <article v-for="item in activeTaskItems" :key="String(item.id)" class="workbench-flow-row">
              <div class="workbench-flow-row__content">
                <strong>{{ taskDisplayName(item) }}</strong>
                <span>{{ taskSummary(item) }}</span>
                <el-progress
                  v-if="clampProgress(item.progressPercent) !== null"
                  class="workbench-progress"
                  :percentage="clampProgress(item.progressPercent) ?? 0"
                  :stroke-width="4"
                  :show-text="false"
                />
              </div>
              <div class="workbench-flow-row__status">
                <el-tag size="small" :type="documentTaskStatusTagType(item.status)">
                  {{ taskStatusText(item.status) }}
                </el-tag>
                <time>{{ formatWorkbenchDateTime(item.createdAt) }}</time>
              </div>
              <router-link class="action-button-link" :to="documentTaskRoute(item.id, item.status)">
                <el-button tag="span" text type="primary" size="small">查看</el-button>
              </router-link>
            </article>
          </div>
        </section>

        <section v-if="canViewDocumentTasks" class="workbench-flow-group">
          <header class="workbench-flow-group__heading">
            <div class="workbench-flow-group__title">
              <Warning class="workbench-flow-group__icon workbench-flow-group__icon--danger" />
              <h3>需要重新处理</h3>
              <span class="workbench-count workbench-count--danger">{{ failedTaskTotal }}</span>
            </div>
            <router-link class="workbench-section-link" to="/platform/document-tasks?status=FAILED&returnTo=%2F">查看全部</router-link>
          </header>
          <div v-if="failedTaskLoading && failedTaskItems.length === 0" class="workbench-loading">失败任务加载中</div>
          <div v-else-if="failedTaskError" class="workbench-inline-error">
            <span>{{ failedTaskError }}</span>
            <el-button text type="primary" @click="loadTaskGroup('failed')">重新加载</el-button>
          </div>
          <div v-else-if="failedTaskItems.length === 0" class="workbench-group-empty">暂无需要重新处理的任务</div>
          <div v-else class="workbench-flow-list">
            <article v-for="item in failedTaskItems" :key="String(item.id)" class="workbench-flow-row">
              <div class="workbench-flow-row__content">
                <strong>{{ taskDisplayName(item) }}</strong>
                <span class="workbench-danger-text">{{ taskFailureReason(item) }}</span>
              </div>
              <div class="workbench-flow-row__status">
                <el-tag size="small" :type="documentTaskStatusTagType(item.status)">
                  {{ taskStatusText(item.status) }}
                </el-tag>
                <time>{{ formatWorkbenchDateTime(item.completedAt || item.createdAt) }}</time>
              </div>
              <router-link class="action-button-link" :to="documentTaskRoute(item.id, item.status)">
                <el-button tag="span" text type="primary" size="small">处理</el-button>
              </router-link>
            </article>
          </div>
        </section>
      </section>

      <aside v-if="hasSideContent" class="workbench-side">
        <section v-if="canViewOverview" class="workbench-section workbench-overview">
          <div class="workbench-section__heading">
            <div>
              <h2>本月经营概况</h2>
              <p v-if="overviewRecord">{{ overviewRecord.period.dateFrom }} 至 {{ overviewRecord.period.dateTo }}</p>
            </div>
            <Calendar class="workbench-section__heading-icon" />
          </div>
          <div v-if="overviewLoading && !overviewRecord" class="workbench-loading workbench-side-state">经营概况加载中</div>
          <div v-else-if="overviewError" class="workbench-inline-error workbench-side-state">
            <span>{{ overviewError }}</span>
            <el-button text type="primary" @click="loadOverview()">重新加载</el-button>
          </div>
          <div v-else-if="overviewEmpty" class="workbench-inline-empty">当前期间暂无经营概况</div>
          <div v-else-if="overviewRecord" class="workbench-metric-grid">
            <div v-for="metric in overviewMetrics" :key="metric.label" class="workbench-metric">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </div>
          </div>
          <router-link v-if="overviewRecord" class="workbench-footer-link" to="/reports/overview">查看经营概览</router-link>
        </section>

        <section v-if="canViewOperatingFinance" class="workbench-section workbench-status">
          <div class="workbench-section__heading">
            <div>
              <h2>关键状态</h2>
              <p v-if="operatingFinanceRecord">当前期间 {{ operatingFinanceRecord.periodCode }}</p>
            </div>
          </div>
          <div v-if="operatingFinanceLoading && !operatingFinanceRecord" class="workbench-loading workbench-side-state">关键状态加载中</div>
          <div v-else-if="operatingFinanceError" class="workbench-inline-error workbench-side-state">
            <span>{{ operatingFinanceError }}</span>
            <el-button text type="primary" @click="loadOperatingFinance()">重新加载</el-button>
          </div>
          <div v-else-if="keyStatusRows.length === 0" class="workbench-inline-empty">暂无经营与关账状态</div>
          <div v-else class="workbench-status-list">
            <div v-for="row in keyStatusRows" :key="row.label" class="workbench-status-row">
              <span>{{ row.label }}</span>
              <el-tag size="small" :type="businessStatusType(row.rawStatus)">{{ row.value }}</el-tag>
              <router-link v-if="row.to" class="workbench-section-link" :to="row.to">{{ row.actionLabel }}</router-link>
              <span v-else class="workbench-status-row__placeholder">—</span>
            </div>
          </div>
        </section>
      </aside>
    </div>

    <section v-if="canViewExceptions" class="workbench-section workbench-attention">
      <div class="workbench-section__heading">
        <div>
          <h2>业务关注</h2>
          <p>优先处理影响交付、生产、库存、成本和资金周转的经营异常。</p>
        </div>
      </div>
      <div v-if="exceptionsLoading && exceptionItems.length === 0" class="workbench-loading workbench-attention-state">业务关注加载中</div>
      <div v-else-if="exceptionsError" class="workbench-inline-error workbench-attention-state">
        <span>{{ exceptionsError }}</span>
        <el-button text type="primary" @click="loadExceptions()">重新加载</el-button>
      </div>
      <div v-else-if="exceptionItems.length === 0" class="workbench-inline-empty">当前没有需要关注的经营异常</div>
      <div v-else class="workbench-attention-list">
        <article
          v-for="(item, index) in exceptionItems"
          :key="exceptionRowKey(item, index)"
          class="workbench-attention-row"
        >
          <div class="workbench-attention-row__type">
            <el-tag size="small" :type="exceptionTagType(item.severity)">{{ exceptionSeverityText(item.severity) }}</el-tag>
            <strong>{{ exceptionTypeText(item.exceptionType) }}</strong>
          </div>
          <div class="workbench-attention-row__content">
            <strong>{{ item.canViewResource ? (item.objectName || '异常对象未命名') : '来源受限' }}</strong>
            <span>{{ item.description }}</span>
          </div>
          <time>{{ item.canViewResource ? (item.businessDate || '—') : '—' }}</time>
          <router-link class="action-button-link" to="/reports/exceptions">
            <el-button tag="span" text type="primary" size="small">进入清单</el-button>
          </router-link>
        </article>
      </div>
      <router-link v-if="exceptionItems.length" class="workbench-footer-link" to="/reports/exceptions">查看全部业务关注</router-link>
    </section>

    <section
      v-if="!quickEntries.length && !hasWorkflowPermission && !hasSideContent && !canViewExceptions"
      class="workbench-section workbench-page-empty"
    >
      当前账号暂无可展示的工作台数据，请从左侧菜单进入已授权功能。
    </section>
  </section>
</template>

<style scoped>
.workbench-page {
  padding-bottom: 8px;
}

.workbench-heading {
  align-items: flex-start;
}

.workbench-heading__actions {
  display: flex;
  min-height: 40px;
  flex: 0 0 auto;
  align-items: center;
  gap: 12px;
}

.workbench-updated {
  color: var(--qherp-stone);
  font-size: 12px;
  white-space: nowrap;
}

.workbench-section {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--qherp-border);
  border-radius: var(--qherp-radius-lg);
  background: var(--qherp-surface);
}

.workbench-section__heading {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px 14px;
  border-bottom: 1px solid var(--qherp-border-soft);
}

.workbench-section__heading h2 {
  color: var(--qherp-text);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}

.workbench-section__heading p {
  margin-top: 4px;
  color: var(--qherp-steel);
  font-size: 13px;
}

.workbench-section__heading-icon {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  color: var(--qherp-stone);
}

.workbench-quick__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(132px, 1fr));
  gap: 10px;
  padding: 14px 18px 16px;
}

.workbench-quick__button.el-button {
  width: 100%;
  height: 42px;
  justify-content: flex-start;
  margin: 0;
  padding: 0 14px;
  color: var(--qherp-charcoal);
  background: var(--qherp-surface);
  border-color: var(--qherp-border);
}

.workbench-quick__button.el-button:hover,
.workbench-quick__button.el-button:focus-visible {
  color: var(--qherp-text);
  background: var(--qherp-surface-soft);
  border-color: var(--qherp-stone);
}

.workbench-quick__button .el-icon {
  width: 17px;
  height: 17px;
  margin-right: 8px;
  color: var(--qherp-brand-green-deep);
}

.workbench-primary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(310px, 0.9fr);
  align-items: start;
  gap: 16px;
}

.workbench-primary-grid--single {
  grid-template-columns: minmax(0, 1fr);
}

.workbench-side {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 16px;
}

.workbench-flow-group {
  padding: 0 18px;
}

.workbench-flow-group + .workbench-flow-group {
  border-top: 1px solid var(--qherp-border-soft);
}

.workbench-flow-group__heading {
  display: flex;
  min-height: 50px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.workbench-flow-group__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.workbench-flow-group__title h3 {
  color: var(--qherp-charcoal);
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.workbench-flow-group__icon {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  color: var(--qherp-steel);
}

.workbench-flow-group__icon--success {
  color: var(--qherp-brand-green-deep);
}

.workbench-flow-group__icon--danger {
  color: var(--qherp-danger);
}

.workbench-count {
  display: inline-flex;
  min-width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  padding: 0 7px;
  color: var(--qherp-steel);
  background: var(--qherp-surface-soft);
  border-radius: var(--qherp-radius-full);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.workbench-count--danger {
  color: var(--qherp-danger);
  background: rgba(212, 86, 86, 0.09);
}

.workbench-section-link,
.workbench-footer-link {
  color: var(--qherp-brand-tag);
  font-size: 13px;
  text-decoration: none;
  white-space: nowrap;
}

.workbench-section-link:hover,
.workbench-section-link:focus-visible,
.workbench-footer-link:hover,
.workbench-footer-link:focus-visible {
  text-decoration: underline;
}

.workbench-flow-list,
.workbench-attention-list {
  border-top: 1px solid var(--qherp-border-soft);
}

.workbench-flow-row {
  display: grid;
  min-height: 56px;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 14px;
  padding: 9px 0;
}

.workbench-flow-row + .workbench-flow-row,
.workbench-attention-row + .workbench-attention-row {
  border-top: 1px solid var(--qherp-border-soft);
}

.workbench-flow-row:hover,
.workbench-attention-row:hover {
  background: var(--qherp-surface-soft);
}

.workbench-flow-row__content,
.workbench-attention-row__content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.workbench-flow-row__content strong,
.workbench-attention-row__content strong {
  overflow: hidden;
  color: var(--qherp-charcoal);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-flow-row__content span,
.workbench-attention-row__content span {
  overflow: hidden;
  color: var(--qherp-steel);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-flow-row time,
.workbench-attention-row time,
.workbench-flow-row__status time {
  color: var(--qherp-stone);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.workbench-flow-row__status {
  display: flex;
  min-width: 92px;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.workbench-progress {
  width: min(180px, 100%);
  margin-top: 3px;
}

.workbench-progress :deep(.el-progress-bar__outer) {
  background: var(--qherp-border-soft);
}

.workbench-progress :deep(.el-progress-bar__inner) {
  background: var(--qherp-brand-green-deep);
}

.workbench-danger-text {
  color: var(--qherp-danger) !important;
}

.workbench-loading,
.workbench-group-empty,
.workbench-inline-empty,
.workbench-page-empty {
  color: var(--qherp-steel);
  font-size: 13px;
}

.workbench-loading,
.workbench-group-empty {
  padding: 14px 0;
}

.workbench-side-state,
.workbench-attention-state {
  margin: 0 18px;
}

.workbench-inline-empty {
  padding: 24px 18px;
  text-align: center;
}

.workbench-page-empty {
  padding: 40px 24px;
  text-align: center;
}

.workbench-inline-error {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  color: var(--qherp-danger);
  font-size: 13px;
}

.workbench-metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.workbench-metric {
  display: flex;
  min-height: 92px;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 14px 16px;
}

.workbench-metric:nth-child(odd) {
  border-right: 1px solid var(--qherp-border-soft);
}

.workbench-metric:nth-child(n + 3) {
  border-top: 1px solid var(--qherp-border-soft);
}

.workbench-metric span {
  color: var(--qherp-steel);
  font-size: 12px;
}

.workbench-metric strong {
  color: var(--qherp-text);
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.25;
}

.workbench-footer-link {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  border-top: 1px solid var(--qherp-border-soft);
}

.workbench-status-list {
  padding: 0 16px;
}

.workbench-status-row {
  display: grid;
  min-height: 48px;
  grid-template-columns: minmax(90px, 1fr) auto minmax(60px, auto);
  align-items: center;
  gap: 10px;
}

.workbench-status-row + .workbench-status-row {
  border-top: 1px solid var(--qherp-border-soft);
}

.workbench-status-row > span:first-child {
  color: var(--qherp-charcoal);
  font-size: 13px;
}

.workbench-status-row__placeholder {
  color: var(--qherp-muted);
  text-align: right;
}

.workbench-attention-row {
  display: grid;
  min-height: 62px;
  grid-template-columns: minmax(150px, 0.7fr) minmax(0, 2fr) auto auto;
  align-items: center;
  gap: 16px;
  padding: 9px 18px;
}

.workbench-attention-row__type {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.workbench-attention-row__type strong {
  overflow: hidden;
  color: var(--qherp-charcoal);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .workbench-primary-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .workbench-side {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .workbench-heading {
    flex-direction: column;
  }

  .workbench-heading,
  .workbench-heading__actions {
    align-items: flex-start;
  }

  .workbench-quick__grid,
  .workbench-side {
    grid-template-columns: minmax(0, 1fr);
  }

  .workbench-flow-row,
  .workbench-attention-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .workbench-flow-row__status,
  .workbench-attention-row time {
    display: none;
  }

  .workbench-attention-row__type {
    grid-column: 1 / -1;
  }
}
</style>
