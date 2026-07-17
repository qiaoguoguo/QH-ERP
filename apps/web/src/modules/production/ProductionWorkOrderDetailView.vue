<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  costCollectionApi,
  type CostRecordSummaryRecord,
  type WorkOrderCostSummaryRecord,
} from '../../shared/api/costCollectionApi'
import {
  projectProductionApi,
  type ProjectProductionDocumentSummaryRecord,
  type ProjectProductionWorkOrderDetailRecord,
  type ResourceId,
} from '../../shared/api/projectProductionApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import CostSourceTypeTag from '../cost/CostSourceTypeTag.vue'
import CostTypeTag from '../cost/CostTypeTag.vue'
import {
  costErrorMessage,
  costTypeLabel,
  formatCostAmount,
  formatCostDateTime,
  formatCostQuantity,
} from '../cost/costPageHelpers'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import ProductionWorkOrderActionBar from './ProductionWorkOrderActionBar.vue'
import ProductionWorkOrderExecutionRecords from './ProductionWorkOrderExecutionRecords.vue'
import ProductionWorkOrderSourceSummary from './ProductionWorkOrderSourceSummary.vue'
import ProductionWorkOrderStatusTag from './ProductionWorkOrderStatusTag.vue'
import ProductionWorkOrderTraceLinks from './ProductionWorkOrderTraceLinks.vue'
import {
  formatProductionDateTime,
  formatProductionQuantity,
  createProductionIdempotencyKey,
  productionErrorMessage,
} from './productionPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<ProjectProductionWorkOrderDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const costSummary = ref<WorkOrderCostSummaryRecord | null>(null)
const costLoading = ref(false)
const costError = ref('')

const canExecute = computed(() => record.value?.status === 'RELEASED' || record.value?.status === 'IN_PROGRESS')
const canPostIssue = computed(() => authStore.hasPermission('production:issue:post'))
const canPostReport = computed(() => authStore.hasPermission('production:report:post'))
const canPostReceipt = computed(() => authStore.hasPermission('production:receipt:post'))
const canViewCost = computed(() => authStore.hasPermission('cost:record:view'))
const canCreateCost = computed(() => authStore.hasPermission('cost:record:create'))
const canUpdateCost = computed(() => authStore.hasPermission('cost:record:update'))

function actionAllowed(codes: string[], fallback: boolean): boolean {
  if (Array.isArray(record.value?.allowedActions)) {
    return codes.some((code) => record.value?.allowedActions?.includes(code))
  }
  return fallback
}

const canEdit = computed(() => authStore.hasPermission('production:work-order:update') && actionAllowed(['UPDATE'], record.value?.status === 'DRAFT'))
const canRelease = computed(() => authStore.hasPermission('production:work-order:release') && actionAllowed(['RELEASE'], record.value?.status === 'DRAFT'))
const canCreateIssue = computed(() => authStore.hasPermission('production:issue:create') && actionAllowed(['ISSUE', 'CREATE_ISSUE'], canExecute.value))
const canCreateReport = computed(() => authStore.hasPermission('production:report:create') && actionAllowed(['REPORT', 'CREATE_REPORT'], canExecute.value))
const canCreateReceipt = computed(() => authStore.hasPermission('production:receipt:create') && actionAllowed(['RECEIPT', 'CREATE_RECEIPT'], canExecute.value))
const canComplete = computed(() => authStore.hasPermission('production:work-order:complete') && actionAllowed(['COMPLETE'], canExecute.value))
const canCancel = computed(() => authStore.hasPermission('production:work-order:cancel') && actionAllowed(
  ['CANCEL'],
  record.value?.status === 'DRAFT' || record.value?.status === 'RELEASED',
))

const postActionConfig = {
  materialIssue: {
    label: '领料单',
    permission: 'production:issue:post',
  },
  report: {
    label: '报工单',
    permission: 'production:report:post',
  },
  completionReceipt: {
    label: '完工入库单',
    permission: 'production:receipt:post',
  },
} as const

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await projectProductionApi.workOrders.get(route.params.id as ResourceId)
    if (canViewCost.value) {
      await loadCostSummary(record.value.id)
    } else {
      costSummary.value = null
      costError.value = ''
    }
  } catch (caught) {
    record.value = null
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadCostSummary(workOrderId: ResourceId) {
  costLoading.value = true
  costError.value = ''
  try {
    costSummary.value = await costCollectionApi.workOrders.summary(workOrderId)
  } catch (caught) {
    costSummary.value = null
    costError.value = costErrorMessage(caught)
  } finally {
    costLoading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'production-work-orders' }))
}

function editRecord() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'production-work-order-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
}

function createMaterialIssue() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'production-work-order-material-issues', params: { id: String(record.value.id) } })
}

function createReport() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'production-work-order-reports', params: { id: String(record.value.id) } })
}

function createCompletionReceipt() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'production-work-order-completion-receipts', params: { id: String(record.value.id) } })
}

function createCostRecord() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'cost-record-create', query: { workOrderId: String(record.value.id) } })
}

function viewCostRecord(costRecord: CostRecordSummaryRecord) {
  void router.push({
    name: 'cost-record-detail',
    params: { id: String(costRecord.id) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function editCostRecord(costRecord: CostRecordSummaryRecord) {
  void router.push({
    name: 'cost-record-edit',
    params: { id: String(costRecord.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
}

async function runAction(action: 'release' | 'complete' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const labels = {
    release: '发布',
    complete: '完成',
    cancel: '取消',
  }
  if (!(await confirmAction(`确认${labels[action]}生产工单“${record.value.workOrderNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'release') {
      record.value = await projectProductionApi.workOrders.release(record.value.id, {
        version: record.value.version,
        idempotencyKey: createProductionIdempotencyKey('production-work-order-release'),
      })
    } else if (action === 'complete') {
      record.value = await projectProductionApi.workOrders.complete(record.value.id, {
        version: record.value.version,
        idempotencyKey: createProductionIdempotencyKey('production-work-order-complete'),
      })
    } else {
      record.value = await projectProductionApi.workOrders.cancel(record.value.id, {
        version: record.value.version,
        idempotencyKey: createProductionIdempotencyKey('production-work-order-cancel'),
      })
    }
  } catch (caught) {
    actionError.value = productionErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function postExecutionDocument(
  action: 'materialIssue' | 'report' | 'completionReceipt',
  document: ProjectProductionDocumentSummaryRecord,
  documentNo: string,
) {
  if (!record.value || actionLoading.value) {
    return
  }
  const config = postActionConfig[action]
  if (!authStore.hasPermission(config.permission)) {
    actionError.value = `缺少${config.label}过账权限`
    return
  }
  if (!(await confirmAction(`确认过账${config.label}“${documentNo}”？过账会影响生产执行和库存结果，过账后不可撤销。`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    const payload = {
      version: Number(document.version ?? record.value.version),
      idempotencyKey: createProductionIdempotencyKey(
        action === 'materialIssue'
          ? 'production-material-issue-post'
          : action === 'report'
            ? 'production-work-report-post'
            : 'production-completion-receipt-post',
      ),
    }
    if (action === 'materialIssue') {
      await projectProductionApi.materialIssues.post(record.value.id, document.id, payload)
    } else if (action === 'report') {
      await projectProductionApi.reports.post(record.value.id, document.id, payload)
    } else {
      await projectProductionApi.completionReceipts.post(record.value.id, document.id, payload)
    }
    await loadRecord()
  } catch (caught) {
    actionError.value = productionErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="生产工单详情" description="查看工单计划、BOM 快照、生产执行记录和库存流水追溯。">
    <template #actions>
      <ProductionWorkOrderActionBar
        :action-loading="actionLoading"
        :can-edit="canEdit"
        :can-release="canRelease"
        :can-create-issue="canCreateIssue"
        :can-create-report="canCreateReport"
        :can-create-receipt="canCreateReceipt"
        :can-complete="canComplete"
        :can-cancel="canCancel"
        @back="backToList"
        @edit="editRecord"
        @release="runAction('release')"
        @create-issue="createMaterialIssue"
        @create-report="createReport"
        @create-receipt="createCompletionReceipt"
        @complete="runAction('complete')"
        @cancel="runAction('cancel')"
      />
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="生产工单详情加载中" :closable="false" />
    </template>

    <div v-if="record" class="production-detail">
      <section class="summary-strip">
        <div>
          <span>计划数量</span>
          <strong>{{ formatProductionQuantity(record.plannedQuantity) }}</strong>
        </div>
        <div>
          <span>累计报工</span>
          <strong>{{ formatProductionQuantity(record.reportedQuantity) }}</strong>
        </div>
        <div>
          <span>合格数量</span>
          <strong>{{ formatProductionQuantity(record.qualifiedQuantity) }}</strong>
        </div>
        <div>
          <span>累计入库</span>
          <strong>{{ formatProductionQuantity(record.receivedQuantity) }}</strong>
        </div>
      </section>

      <dl class="detail-list">
        <dt>工单编号</dt>
        <dd>{{ record.workOrderNo }}</dd>
        <dt>项目来源</dt>
        <dd>
          <template v-if="record.ownershipType === 'PROJECT'">
            {{ record.projectNo || record.projectId || '-' }} {{ record.projectName || '' }}
          </template>
          <template v-else>公共工单</template>
        </dd>
        <dt>来源建议</dt>
        <dd>{{ record.sourceSummary?.sourceSuggestionNo || record.sourceSuggestionNo || '-' }}</dd>
        <dt>状态</dt>
        <dd><ProductionWorkOrderStatusTag :status="record.status" /></dd>
        <dt>产品物料</dt>
        <dd>{{ record.productMaterialCode }} {{ record.productMaterialName }}</dd>
        <dt>BOM</dt>
        <dd>{{ record.bomCode }} / {{ record.bomVersionCode }}</dd>
        <dt>领料仓库</dt>
        <dd>{{ record.issueWarehouseName }}</dd>
        <dt>入库仓库</dt>
        <dd>{{ record.receiptWarehouseName }}</dd>
        <dt>计划日期</dt>
        <dd>{{ record.plannedStartDate }} 至 {{ record.plannedFinishDate }}</dd>
        <dt>创建人</dt>
        <dd>{{ record.createdByName }}</dd>
        <dt>创建时间</dt>
        <dd>{{ formatProductionDateTime(record.createdAt) }}</dd>
        <dt>备注</dt>
        <dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <ProductionWorkOrderSourceSummary :record="record" />
      <ProductionWorkOrderTraceLinks :links="record.traceLinks" />

      <el-alert
        v-if="record.actionDisabledReason"
        class="state-alert"
        type="warning"
        :title="record.actionDisabledReason"
        :closable="false"
      />

      <section class="section-block">
        <h2>BOM 用料快照</h2>
        <div class="table-scroll">
          <el-table :data="record.materials" empty-text="暂无用料快照" stripe>
            <el-table-column prop="lineNo" label="行号" width="78" />
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column prop="unitName" label="单位" min-width="90" />
            <el-table-column label="应领" min-width="110" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatProductionQuantity(row.requiredQuantity) }}</span></template>
            </el-table-column>
            <el-table-column label="已领" min-width="110" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatProductionQuantity(row.issuedQuantity) }}</span></template>
            </el-table-column>
            <el-table-column label="未领" min-width="110" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatProductionQuantity(row.remainingQuantity) }}</span></template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <ProductionWorkOrderExecutionRecords
        :record="record"
        :action-loading="actionLoading"
        :can-post-issue="canPostIssue"
        :can-post-report="canPostReport"
        :can-post-receipt="canPostReceipt"
        @post="postExecutionDocument"
      />

      <section class="section-block">
        <h2>库存流水摘要</h2>
        <div class="table-scroll">
          <el-table :data="record.movements" empty-text="暂无库存流水" stripe>
            <el-table-column prop="movementNo" label="流水号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="movementType" label="类型" min-width="140" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
            <el-table-column label="物料" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
            </el-table-column>
            <el-table-column label="数量" min-width="110" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatProductionQuantity(row.quantity) }}</span></template>
            </el-table-column>
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column prop="operatorName" label="操作人" min-width="110" />
          </el-table>
        </div>
      </section>

      <section v-if="canViewCost" class="section-block">
        <div class="section-title-row">
          <h2>成本归集记录</h2>
          <el-button v-if="canCreateCost" size="small" type="primary" @click="createCostRecord">
            新增手工成本
          </el-button>
        </div>
        <el-alert
          class="state-alert cost-notice"
          type="info"
          title="当前为业务归集记录，不是正式财务核算结果。"
          :closable="false"
        />
        <el-alert v-if="costError" class="state-alert cost-notice" type="error" :title="costError" :closable="false" />
        <el-alert
          v-if="costLoading"
          class="state-alert cost-notice"
          type="info"
          title="成本归集记录加载中"
          :closable="false"
        />

        <template v-if="costSummary">
          <section class="cost-summary-strip">
            <div>
              <span>记录数</span>
              <strong>{{ costSummary.records.length }}</strong>
            </div>
            <div>
              <span>金额汇总项</span>
              <strong>{{ costSummary.amountSummaries.length }}</strong>
            </div>
            <div>
              <span>数量汇总项</span>
              <strong>{{ costSummary.quantitySummaries.length }}</strong>
            </div>
            <div>
              <span>产出追溯</span>
              <strong>{{ costSummary.outputTraces.length }}</strong>
            </div>
          </section>

          <div class="table-scroll">
            <el-table :data="costSummary.records" empty-text="暂无成本归集记录" stripe>
              <el-table-column prop="recordNo" label="记录编号" min-width="160" show-overflow-tooltip />
              <el-table-column label="成本类型" min-width="110">
                <template #default="{ row }"><CostTypeTag :type="row.costType" /></template>
              </el-table-column>
              <el-table-column label="来源类型" min-width="130">
                <template #default="{ row }"><CostSourceTypeTag :type="row.sourceType" /></template>
              </el-table-column>
              <el-table-column prop="sourceDocumentNo" label="来源单据" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ row.sourceDocumentNo || '-' }}</template>
              </el-table-column>
              <el-table-column label="物料" min-width="190" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.materialCode ? `${row.materialCode} ${row.materialName || ''}` : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="数量" min-width="110" align="right">
                <template #default="{ row }"><span class="numeric-cell">{{ formatCostQuantity(row.quantity) }}</span></template>
              </el-table-column>
              <el-table-column label="金额" min-width="110" align="right">
                <template #default="{ row }"><span class="numeric-cell">{{ formatCostAmount(row.amount) }}</span></template>
              </el-table-column>
              <el-table-column prop="businessDate" label="业务日期" min-width="110" />
              <el-table-column prop="recordedByName" label="记录人" min-width="110" />
              <el-table-column label="操作" fixed="right" min-width="132">
                <template #default="{ row }">
                  <el-button size="small" text @click="viewCostRecord(row)">详情</el-button>
                  <el-button
                    v-if="canUpdateCost && row.sourceType === 'MANUAL_ENTRY'"
                    data-test="edit-work-order-cost-record"
                    size="small"
                    text
                    @click="editCostRecord(row)"
                  >
                    编辑
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <section class="section-block nested">
            <h2>金额汇总</h2>
            <div class="table-scroll">
              <el-table :data="costSummary.amountSummaries" empty-text="暂无金额汇总" stripe>
                <el-table-column label="成本类型" min-width="140">
                  <template #default="{ row }">{{ costTypeLabel(row.costType) }}</template>
                </el-table-column>
                <el-table-column label="金额" min-width="140" align="right">
                  <template #default="{ row }"><span class="numeric-cell">{{ formatCostAmount(row.amount) }}</span></template>
                </el-table-column>
              </el-table>
            </div>
          </section>

          <section class="section-block nested">
            <h2>数量汇总</h2>
            <div class="table-scroll">
              <el-table :data="costSummary.quantitySummaries" empty-text="暂无数量汇总" stripe>
                <el-table-column label="成本类型" min-width="140">
                  <template #default="{ row }">{{ costTypeLabel(row.costType) }}</template>
                </el-table-column>
                <el-table-column label="数量" min-width="140" align="right">
                  <template #default="{ row }"><span class="numeric-cell">{{ formatCostQuantity(row.quantity) }}</span></template>
                </el-table-column>
              </el-table>
            </div>
          </section>

          <section class="section-block nested">
            <h2>完工入库产出追溯</h2>
            <div class="table-scroll">
              <el-table :data="costSummary.outputTraces" empty-text="暂无产出追溯" stripe>
                <el-table-column prop="receiptNo" label="入库单号" min-width="160" show-overflow-tooltip />
                <el-table-column prop="businessDate" label="业务日期" min-width="110" />
                <el-table-column prop="receiptWarehouseName" label="入库仓库" min-width="140" show-overflow-tooltip />
                <el-table-column label="入库数量" min-width="110" align="right">
                  <template #default="{ row }"><span class="numeric-cell">{{ formatCostQuantity(row.quantity) }}</span></template>
                </el-table-column>
                <el-table-column prop="postedByName" label="过账人" min-width="110" />
                <el-table-column label="过账时间" min-width="150">
                  <template #default="{ row }">{{ formatCostDateTime(row.postedAt) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </section>
        </template>
      </section>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.production-detail {
  padding: 14px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.summary-strip > div {
  border: 1px solid var(--qherp-border);
  border-radius: 8px;
  padding: 12px;
}

.summary-strip span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
}

.summary-strip strong {
  display: block;
  font-size: 20px;
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
}

.detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 18px;
}

.detail-list dt {
  color: var(--qherp-muted);
}

.detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

.section-block {
  margin-top: 18px;
}

.section-block h2 {
  font-size: 16px;
  margin: 0 0 10px;
}

.trace-link-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.trace-link-list span {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  color: var(--qherp-muted);
  padding: 4px 8px;
}

.section-title-row {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.section-title-row h2 {
  margin: 0;
}

.cost-notice {
  margin-bottom: 10px;
}

.cost-summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.cost-summary-strip > div {
  border: 1px solid var(--qherp-border);
  border-radius: 8px;
  padding: 12px;
}

.cost-summary-strip span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
}

.cost-summary-strip strong {
  display: block;
  font-size: 20px;
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
}

.section-block.nested {
  margin-top: 14px;
}

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
  .cost-summary-strip,
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .detail-list,
  .cost-summary-strip,
  .summary-strip {
    grid-template-columns: 1fr;
  }
}
</style>
