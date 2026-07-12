<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
const loading = ref(true)
const error = ref('')
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

const canEdit = computed(() => Boolean(record.value) && authStore.hasPermission('sales:project:update')
  && record.value?.status !== 'CLOSED' && record.value?.status !== 'CANCELLED')
const canActivate = computed(() => record.value?.status === 'DRAFT' && authStore.hasPermission('sales:project:activate'))
const canClose = computed(() => record.value?.status === 'ACTIVE' && authStore.hasPermission('sales:project:close'))
const canCancel = computed(() => record.value?.status === 'DRAFT' && authStore.hasPermission('sales:project:cancel'))
const canCreateContract = computed(() => Boolean(record.value) && authStore.hasPermission('sales:contract:create')
  && record.value?.status !== 'CLOSED' && record.value?.status !== 'CANCELLED')

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await salesProjectApi.projects.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = projectApiErrorMessage(caught)
  } finally {
    loading.value = false
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

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="销售项目详情" description="查看项目基础信息、合同、关联订单和操作记录。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canEdit" type="primary" @click="editProject">编辑项目</el-button>
      <el-button v-if="canActivate" type="success" :loading="actionLoading" @click="openProjectAction('activate')">激活项目</el-button>
      <el-button v-if="canClose" type="warning" :loading="actionLoading" @click="openProjectAction('close')">关闭项目</el-button>
      <el-button v-if="canCancel" type="danger" :loading="actionLoading" @click="openProjectAction('cancel')">取消项目</el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="销售项目详情加载中" :closable="false" />
    </template>

    <div v-if="record" class="sales-project-detail">
      <section class="summary-strip">
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
          <el-button v-if="canCreateContract" size="small" type="primary" plain @click="openCreateContract">新增合同</el-button>
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

      <SalesProjectOrderSummaryPanel :restricted="record.salesOrderSummaryRestricted" :summary="record.salesOrderSummary" />
      <SalesProjectOperationsPanel :operations="record.operations" />

      <SalesProjectContractDrawer
        v-model="contractDrawerOpen"
        :mode="contractDrawerMode"
        :project="record"
        :contract-id="selectedContractId"
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
          <el-button data-test="confirm-project-action" type="primary" :loading="actionLoading" @click="confirmProjectAction">确认</el-button>
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
}

@media (max-width: 760px) {
  .sales-project-detail-list {
    grid-template-columns: 88px minmax(0, 1fr);
  }
}
</style>
