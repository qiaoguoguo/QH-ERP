<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  salesFulfillmentApi,
  type SalesProjectFulfillmentRecord,
} from '../../../shared/api/salesFulfillmentApi'
import {
  salesProjectApi,
  type SalesProjectDetail,
  type SalesProjectSummary,
} from '../../../shared/api/salesProjectApi'
import type { ResourceId } from '../../../shared/api/salesApi'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import SalesProjectContractDrawer from './SalesProjectContractDrawer.vue'
import SalesProjectContractStatusTag from './SalesProjectContractStatusTag.vue'
import SalesProjectOperationsPanel from './SalesProjectOperationsPanel.vue'
import SalesProjectOrderSummaryPanel from './SalesProjectOrderSummaryPanel.vue'
import SalesProjectStatusTag from './SalesProjectStatusTag.vue'
import {
  formatSalesDecimal,
  salesFulfillmentErrorMessage,
} from '../salesFulfillmentPageHelpers'
import {
  formatProjectAmount,
  formatProjectDateTime,
  projectApiErrorMessage,
  salesProjectContractTypeLabel,
  validateProjectReason,
} from './salesProjectPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<SalesProjectDetail | null>(null)
const fulfillment = ref<SalesProjectFulfillmentRecord | null>(null)
const loading = ref(true)
const fulfillmentLoading = ref(false)
const error = ref('')
const fulfillmentError = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const contractDrawerOpen = ref(false)
const contractDrawerMode = ref<'create' | 'edit' | 'view'>('create')
const selectedContractId = ref<ResourceId | undefined>(undefined)
const actionDialog = reactive<{
  visible: boolean
  action: 'activate' | 'close' | 'cancel' | ''
  title: string
  reason: string
  error: string
}>({
  visible: false,
  action: '',
  title: '',
  reason: '',
  error: '',
})
const fulfillmentCloseDialog = reactive({
  visible: false,
  reason: '',
  error: '',
})

const canEdit = computed(() => Boolean(record.value) && authStore.hasPermission('sales:project:update')
  && record.value?.status !== 'CLOSED' && record.value?.status !== 'CANCELLED')
const canActivate = computed(() => record.value?.status === 'DRAFT' && authStore.hasPermission('sales:project:activate'))
const canClose = computed(() => record.value?.status === 'ACTIVE' && authStore.hasPermission('sales:project:close'))
const canCancel = computed(() => record.value?.status === 'DRAFT' && authStore.hasPermission('sales:project:cancel'))
const canViewSalesOrders = computed(() => authStore.hasPermission('sales:order:view'))
const canViewFulfillment = computed(() => authStore.hasPermission('sales:fulfillment:view'))
const canCloseFulfillment = computed(() => Boolean(fulfillment.value)
  && authStore.hasPermission('sales:fulfillment:close')
  && (fulfillment.value?.allowedActions ?? []).includes('CLOSE'))
const projectActivateDisabledReason = computed(() => {
  if (record.value?.status === 'DRAFT' && record.value.mainContractStatus !== 'EFFECTIVE') {
    return '项目需先存在已生效主合同后才能激活'
  }
  return ''
})
const defaultContractType = computed(() =>
  record.value?.status === 'ACTIVE' && record.value.mainContractStatus === 'EFFECTIVE' ? 'SUPPLEMENT' : 'MAIN')
const contractCreateState = computed(() => resolveContractCreateState(record.value, authStore.hasPermission('sales:contract:create')))
const projectConfirmButtonType = computed(() => {
  if (actionDialog.action === 'close') {
    return 'warning'
  }
  if (actionDialog.action === 'cancel') {
    return 'danger'
  }
  return 'primary'
})

function resolveContractCreateState(project: SalesProjectDetail | null, hasCreatePermission: boolean) {
  if (!project || !hasCreatePermission || project.status === 'CLOSED' || project.status === 'CANCELLED') {
    return { visible: false, disabled: false, reason: '' }
  }
  if (project.status === 'DRAFT') {
    return {
      visible: project.mainContractId === null,
      disabled: false,
      reason: '',
    }
  }
  if (project.status === 'ACTIVE') {
    return {
      visible: true,
      disabled: project.mainContractStatus !== 'EFFECTIVE',
      reason: project.mainContractStatus === 'EFFECTIVE' ? '' : '项目主合同生效后才能新增补充合同',
    }
  }
  return { visible: false, disabled: false, reason: '' }
}

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await salesProjectApi.projects.get(route.params.id as ResourceId)
    if (canViewFulfillment.value) {
      await loadFulfillment()
    } else {
      fulfillment.value = null
      fulfillmentError.value = ''
    }
  } catch (caught) {
    record.value = null
    error.value = projectApiErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadFulfillment() {
  if (!record.value) {
    return
  }
  fulfillmentLoading.value = true
  fulfillmentError.value = ''
  try {
    fulfillment.value = await salesFulfillmentApi.projectFulfillment.get(record.value.id)
  } catch (caught) {
    fulfillment.value = null
    fulfillmentError.value = salesFulfillmentErrorMessage(caught)
  } finally {
    fulfillmentLoading.value = false
  }
}

function backToList() {
  void router.push({ name: 'sales-projects' })
}

function editProject() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'sales-project-edit', params: { id: String(record.value.id) } })
}

function openCreateContract() {
  if (!contractCreateState.value.visible || contractCreateState.value.disabled) {
    return
  }
  selectedContractId.value = undefined
  contractDrawerMode.value = 'create'
  contractDrawerOpen.value = true
}

function openEditContract(contract: { id: ResourceId }) {
  selectedContractId.value = contract.id
  contractDrawerMode.value = 'edit'
  contractDrawerOpen.value = true
}

function openProjectAction(action: 'activate' | 'close' | 'cancel') {
  const titles = {
    activate: '激活项目',
    close: '关闭项目',
    cancel: '取消项目',
  }
  actionDialog.visible = true
  actionDialog.action = action
  actionDialog.title = titles[action]
  actionDialog.reason = ''
  actionDialog.error = ''
}

function openFulfillmentClose() {
  fulfillmentCloseDialog.visible = true
  fulfillmentCloseDialog.reason = ''
  fulfillmentCloseDialog.error = ''
}

async function confirmFulfillmentClose() {
  if (!record.value || !fulfillment.value || actionLoading.value) {
    return
  }
  const reasonError = validateProjectReason(fulfillmentCloseDialog.reason)
  if (reasonError) {
    fulfillmentCloseDialog.error = reasonError
    return
  }
  actionLoading.value = true
  fulfillmentCloseDialog.error = ''
  try {
    await salesFulfillmentApi.projectFulfillment.close(record.value.id, {
      version: fulfillment.value.version,
      reason: fulfillmentCloseDialog.reason.trim(),
      idempotencyKey: createIdempotencyKey('sales-project-fulfillment-close'),
    })
    fulfillmentCloseDialog.visible = false
    await loadFulfillment()
  } catch (caught) {
    fulfillmentCloseDialog.error = salesFulfillmentErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function confirmProjectAction() {
  if (!record.value || !actionDialog.action || actionLoading.value) {
    return
  }
  const reasonRequired = actionDialog.action === 'close' || actionDialog.action === 'cancel'
  const reasonError = reasonRequired ? validateProjectReason(actionDialog.reason) : ''
  if (reasonError) {
    actionDialog.error = reasonError
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = {
      version: record.value.version,
      ...(reasonRequired ? { reason: actionDialog.reason.trim() } : {}),
    }
    if (actionDialog.action === 'activate') {
      record.value = await salesProjectApi.projects.activate(record.value.id, payload)
    } else if (actionDialog.action === 'close') {
      record.value = await salesProjectApi.projects.close(record.value.id, payload)
    } else {
      record.value = await salesProjectApi.projects.cancel(record.value.id, payload)
    }
    actionDialog.visible = false
  } catch (caught) {
    actionDialog.error = projectApiErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function contractRestricted(project: SalesProjectSummary) {
  return project.contractSummaryRestricted
}

function fulfillmentStatusLabel(status: string) {
  return status === 'CLOSED' ? '已关闭' : '开放'
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="销售项目详情" description="查看项目基础信息、合同、关联订单和操作记录。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canEdit" type="primary" @click="editProject">编辑项目</el-button>
      <el-button
        v-if="canActivate"
        type="success"
        :loading="actionLoading"
        :disabled="Boolean(projectActivateDisabledReason)"
        :title="projectActivateDisabledReason"
        @click="openProjectAction('activate')"
      >
        激活项目
      </el-button>
      <el-button v-if="canClose" type="warning" :loading="actionLoading" @click="openProjectAction('close')">关闭项目</el-button>
      <el-button v-if="canCancel" type="danger" :loading="actionLoading" @click="openProjectAction('cancel')">取消项目</el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="销售项目详情加载中" :closable="false" />
    </template>

    <div v-if="record" class="sales-project-detail">
      <section class="summary-strip summary-strip-responsive">
        <div>
          <span>项目编号</span>
          <strong>{{ record.projectNo }}</strong>
        </div>
        <div>
          <span>状态</span>
          <SalesProjectStatusTag :status="record.status" />
        </div>
        <div>
          <span>目标收入</span>
          <strong>{{ formatProjectAmount(record.targetRevenue) }}</strong>
        </div>
        <div>
          <span>目标成本</span>
          <strong>{{ formatProjectAmount(record.targetCost) }}</strong>
        </div>
        <div>
          <span>版本</span>
          <strong>{{ record.version }}</strong>
        </div>
      </section>

      <dl class="sales-project-detail-list">
        <dt>项目名称</dt>
        <dd>{{ record.name }}</dd>
        <dt>客户</dt>
        <dd>{{ record.customerCode }} {{ record.customerName }}</dd>
        <dt>负责人</dt>
        <dd>{{ record.ownerUsername }} {{ record.ownerDisplayName }}</dd>
        <dt>计划周期</dt>
        <dd>{{ record.plannedStartDate || '-' }} 至 {{ record.plannedFinishDate || '-' }}</dd>
        <dt>更新时间</dt>
        <dd>{{ formatProjectDateTime(record.updatedAt) }}</dd>
        <dt>备注</dt>
        <dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <section class="section-block">
        <div class="section-title">
          <span>项目合同</span>
          <el-button
            v-if="contractCreateState.visible"
            size="small"
            type="primary"
            plain
            :disabled="contractCreateState.disabled"
            :title="contractCreateState.reason"
            @click="openCreateContract"
          >
            新增合同
          </el-button>
        </div>
        <el-alert v-if="contractRestricted(record)" type="warning" title="合同摘要受限" :closable="false" />
        <el-empty v-else-if="record.contracts.length === 0" description="暂无项目合同" />
        <div v-else class="table-scroll sales-project-contract-table-scroll">
          <el-table :data="record.contracts" empty-text="暂无项目合同" stripe>
            <el-table-column prop="contractNo" label="合同编号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="externalContractNo" label="外部合同号" min-width="140" show-overflow-tooltip />
            <el-table-column label="类型" min-width="100">
              <template #default="{ row }">{{ salesProjectContractTypeLabel(row.contractType) }}</template>
            </el-table-column>
            <el-table-column prop="name" label="合同名称" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }"><SalesProjectContractStatusTag :status="row.status" /></template>
            </el-table-column>
            <el-table-column label="金额" min-width="120" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatProjectAmount(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column prop="signedDate" label="签订日期" min-width="110" />
            <el-table-column label="操作" fixed="right" width="90">
              <template #default="{ row }">
                <el-button size="small" text data-test="edit-sales-project-contract" @click="openEditContract(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <SalesProjectOrderSummaryPanel
        :project-id="record.id"
        :can-view-details="canViewSalesOrders"
        :restricted="record.salesOrderSummaryRestricted"
        :contract-summary-restricted="record.contractSummaryRestricted"
        :summary="record.salesOrderSummary"
      />

      <section v-if="canViewFulfillment" class="section-block">
        <div class="section-title">
          <span>销售履约</span>
          <el-button
            v-if="canCloseFulfillment"
            size="small"
            type="warning"
            plain
            data-test="close-sales-fulfillment"
            :loading="actionLoading"
            @click="openFulfillmentClose"
          >
            关闭销售履约
          </el-button>
        </div>
        <el-alert
          v-if="fulfillmentError"
          class="state-alert"
          type="error"
          :title="fulfillmentError"
          :closable="false"
        />
        <el-alert
          v-if="fulfillmentLoading"
          class="state-alert"
          type="info"
          title="销售履约加载中"
          :closable="false"
        />
        <div v-if="fulfillment" class="fulfillment-grid">
          <div>
            <span>状态</span>
            <strong>{{ fulfillmentStatusLabel(fulfillment.status) }}</strong>
          </div>
          <div>
            <span>合同有效金额</span>
            <strong>{{ fulfillment.contractRestricted ? '合同信息受限' : formatSalesDecimal(fulfillment.contractEffectiveAmount) }}</strong>
          </div>
          <div>
            <span>订单含税金额</span>
            <strong>{{ fulfillment.contractRestricted ? '合同信息受限' : formatSalesDecimal(fulfillment.orderTaxIncludedAmount) }}</strong>
          </div>
          <div>
            <span>计划数量</span>
            <strong>{{ formatSalesDecimal(fulfillment.plannedQuantity) }}</strong>
          </div>
          <div>
            <span>已发数量</span>
            <strong>{{ formatSalesDecimal(fulfillment.shippedQuantity) }}</strong>
          </div>
          <div>
            <span>退货数量</span>
            <strong>{{ formatSalesDecimal(fulfillment.returnedQuantity) }}</strong>
          </div>
          <div>
            <span>净交付</span>
            <strong>{{ formatSalesDecimal(fulfillment.netDeliveredQuantity) }}</strong>
          </div>
          <div>
            <span>开放需求</span>
            <strong>{{ formatSalesDecimal(fulfillment.openDemandQuantity) }}</strong>
          </div>
          <div>
            <span>逾期计划</span>
            <strong>{{ fulfillment.overduePlanCount ?? '-' }}</strong>
          </div>
          <div>
            <span>信用风险</span>
            <strong>{{ fulfillment.creditRestricted ? '信用信息受限' : (fulfillment.creditRiskSummary || '无') }}</strong>
          </div>
        </div>
        <el-alert
          v-if="fulfillment?.legacyDeliveryPlanCompatible"
          class="state-alert"
          type="warning"
          title="历史交付计划兼容：该项目包含旧阶段已发货订单，未伪造交付计划；如需新计划事实，请通过订单详情显式初始化或拆分交付计划。"
          :closable="false"
          show-icon
        />
        <div v-if="fulfillment?.blockReasons?.length" class="block-reasons">
          <div class="section-subtitle">关闭阻断</div>
          <el-tag v-for="reason in fulfillment.blockReasons" :key="reason" type="warning" effect="plain">
            {{ reason }}
          </el-tag>
        </div>
        <el-empty v-if="!fulfillmentLoading && !fulfillment && !fulfillmentError" description="暂无销售履约汇总" />
      </section>
      <SalesProjectOperationsPanel :operations="record.operations" />

      <SalesProjectContractDrawer
        v-model="contractDrawerOpen"
        :mode="contractDrawerMode"
        :project="record"
        :contract-id="selectedContractId"
        :default-contract-type="defaultContractType"
        @saved="loadRecord"
      />

      <el-dialog v-model="actionDialog.visible" :title="actionDialog.title" :teleported="false" width="420px">
        <el-alert v-if="actionDialog.error" class="state-alert" type="error" :title="actionDialog.error" :closable="false" />
        <el-input
          v-if="actionDialog.action === 'close' || actionDialog.action === 'cancel'"
          v-model="actionDialog.reason"
          name="sales-project-action-reason"
          type="textarea"
          :rows="4"
          maxlength="200"
          show-word-limit
          placeholder="请输入 1-200 字原因"
        />
        <p v-else>确认执行该状态动作？</p>
        <template #footer>
          <el-button @click="actionDialog.visible = false">取消</el-button>
          <el-button
            data-test="confirm-project-action"
            :type="projectConfirmButtonType"
            :loading="actionLoading"
            @click="confirmProjectAction"
          >
            确认
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="fulfillmentCloseDialog.visible" title="关闭销售履约" :teleported="false" width="420px">
        <el-alert
          v-if="fulfillmentCloseDialog.error"
          class="state-alert"
          type="error"
          :title="fulfillmentCloseDialog.error"
          :closable="false"
        />
        <el-input
          v-model="fulfillmentCloseDialog.reason"
          name="sales-project-fulfillment-close-reason"
          type="textarea"
          :rows="4"
          maxlength="200"
          show-word-limit
          placeholder="请输入 1-200 字销售履约关闭原因"
        />
        <template #footer>
          <el-button @click="fulfillmentCloseDialog.visible = false">取消</el-button>
          <el-button
            data-test="confirm-sales-fulfillment-close"
            type="warning"
            :loading="actionLoading"
            @click="confirmFulfillmentClose"
          >
            确认
          </el-button>
        </template>
      </el-dialog>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.sales-project-detail {
  padding: 14px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
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
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.sales-project-detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.sales-project-detail-list dt {
  color: var(--qherp-muted);
}

.sales-project-detail-list dd {
  margin: 0;
  min-width: 0;
  word-break: break-word;
}

.section-block {
  margin-top: 16px;
}

.section-title {
  align-items: center;
  display: flex;
  font-weight: 600;
  gap: 10px;
  justify-content: space-between;
  margin-bottom: 10px;
}

.section-subtitle {
  color: var(--qherp-muted);
  font-size: 12px;
  margin-bottom: 8px;
}

.fulfillment-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.fulfillment-grid > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  min-width: 0;
  padding: 10px 12px;
}

.fulfillment-grid span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.fulfillment-grid strong {
  font-size: 17px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.block-reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.numeric-cell {
  display: inline-block;
  min-width: 82px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .fulfillment-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .sales-project-detail-list {
    grid-template-columns: 88px minmax(0, 1fr);
  }

  .fulfillment-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 390px) {
  .summary-strip-responsive {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
