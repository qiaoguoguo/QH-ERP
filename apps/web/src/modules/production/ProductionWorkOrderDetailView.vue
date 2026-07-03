<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productionApi, type ProductionWorkOrderDetailRecord, type ResourceId } from '../../shared/api/productionApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import ProductionDocumentStatusTag from './ProductionDocumentStatusTag.vue'
import ProductionWorkOrderStatusTag from './ProductionWorkOrderStatusTag.vue'
import {
  formatProductionDateTime,
  formatProductionQuantity,
  productionErrorMessage,
} from './productionPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<ProductionWorkOrderDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canEdit = computed(() => record.value?.status === 'DRAFT' && authStore.hasPermission('production:work-order:update'))
const canRelease = computed(() => record.value?.status === 'DRAFT' && authStore.hasPermission('production:work-order:release'))
const canExecute = computed(() => record.value?.status === 'RELEASED' || record.value?.status === 'IN_PROGRESS')
const canCreateIssue = computed(() => canExecute.value && authStore.hasPermission('production:issue:create'))
const canCreateReport = computed(() => canExecute.value && authStore.hasPermission('production:report:create'))
const canCreateReceipt = computed(() => canExecute.value && authStore.hasPermission('production:receipt:create'))
const canPostIssue = computed(() => authStore.hasPermission('production:issue:post'))
const canPostReport = computed(() => authStore.hasPermission('production:report:post'))
const canPostReceipt = computed(() => authStore.hasPermission('production:receipt:post'))
const canComplete = computed(() => canExecute.value && authStore.hasPermission('production:work-order:complete'))
const canCancel = computed(() => (
  (record.value?.status === 'DRAFT' || record.value?.status === 'RELEASED') &&
  authStore.hasPermission('production:work-order:cancel')
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
    record.value = await productionApi.workOrders.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function editRecord() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'production-work-order-edit', params: { id: String(record.value.id) } })
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

async function runAction(action: 'release' | 'complete' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const labels = {
    release: '发布',
    complete: '完成',
    cancel: '取消',
  }
  if (!window.confirm(`确认${labels[action]}生产工单“${record.value.workOrderNo}”？`)) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'release') {
      record.value = await productionApi.workOrders.release(record.value.id)
    } else if (action === 'complete') {
      record.value = await productionApi.workOrders.complete(record.value.id)
    } else {
      record.value = await productionApi.workOrders.cancel(record.value.id)
    }
  } catch (caught) {
    actionError.value = productionErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function postExecutionDocument(
  action: 'materialIssue' | 'report' | 'completionReceipt',
  documentId: ResourceId,
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
  if (!window.confirm(`确认过账${config.label}“${documentNo}”？过账会影响生产执行和库存结果，过账后不可撤销。`)) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'materialIssue') {
      await productionApi.materialIssues.post(record.value.id, documentId)
    } else if (action === 'report') {
      await productionApi.reports.post(record.value.id, documentId)
    } else {
      await productionApi.completionReceipts.post(record.value.id, documentId)
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
      <el-button v-if="canEdit" data-test="edit-production-work-order-detail" type="primary" @click="editRecord">
        编辑
      </el-button>
      <el-button v-if="canRelease" type="success" :loading="actionLoading" :disabled="actionLoading" @click="runAction('release')">
        发布
      </el-button>
      <el-button v-if="canCreateIssue" @click="createMaterialIssue">领料</el-button>
      <el-button v-if="canCreateReport" @click="createReport">报工</el-button>
      <el-button v-if="canCreateReceipt" @click="createCompletionReceipt">完工入库</el-button>
      <el-button v-if="canComplete" type="success" :loading="actionLoading" :disabled="actionLoading" @click="runAction('complete')">
        完成
      </el-button>
      <el-button v-if="canCancel" type="danger" plain :loading="actionLoading" :disabled="actionLoading" @click="runAction('cancel')">
        取消
      </el-button>
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

      <section class="section-block">
        <h2>领料记录</h2>
        <div class="table-scroll">
          <el-table :data="record.materialIssues" empty-text="暂无领料记录" stripe>
            <el-table-column prop="issueNo" label="领料单号" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" min-width="90">
              <template #default="{ row }"><ProductionDocumentStatusTag :status="row.status" /></template>
            </el-table-column>
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
            <el-table-column prop="lineCount" label="明细数" min-width="90" />
            <el-table-column prop="postedByName" label="过账人" min-width="110">
              <template #default="{ row }">{{ row.postedByName || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="96" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'DRAFT' && canPostIssue"
                  data-test="post-production-material-issue"
                  size="small"
                  text
                  :loading="actionLoading"
                  :disabled="actionLoading"
                  @click="postExecutionDocument('materialIssue', row.id, row.issueNo)"
                >
                  过账
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>报工记录</h2>
        <div class="table-scroll">
          <el-table :data="record.reports" empty-text="暂无报工记录" stripe>
            <el-table-column prop="reportNo" label="报工单号" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" min-width="90">
              <template #default="{ row }"><ProductionDocumentStatusTag :status="row.status" /></template>
            </el-table-column>
            <el-table-column prop="businessDate" label="报工日期" min-width="110" />
            <el-table-column label="合格" min-width="100" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatProductionQuantity(row.qualifiedQuantity) }}</span></template>
            </el-table-column>
            <el-table-column label="不良" min-width="100" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatProductionQuantity(row.defectiveQuantity) }}</span></template>
            </el-table-column>
            <el-table-column prop="reporterName" label="报工人" min-width="110" />
            <el-table-column label="操作" width="96" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'DRAFT' && canPostReport"
                  data-test="post-production-work-report"
                  size="small"
                  text
                  :loading="actionLoading"
                  :disabled="actionLoading"
                  @click="postExecutionDocument('report', row.id, row.reportNo)"
                >
                  过账
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>完工入库记录</h2>
        <div class="table-scroll">
          <el-table :data="record.completionReceipts" empty-text="暂无完工入库记录" stripe>
            <el-table-column prop="receiptNo" label="入库单号" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" min-width="90">
              <template #default="{ row }"><ProductionDocumentStatusTag :status="row.status" /></template>
            </el-table-column>
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column prop="receiptWarehouseName" label="入库仓库" min-width="140" show-overflow-tooltip />
            <el-table-column label="入库数量" min-width="110" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatProductionQuantity(row.quantity) }}</span></template>
            </el-table-column>
            <el-table-column prop="postedByName" label="过账人" min-width="110">
              <template #default="{ row }">{{ row.postedByName || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="96" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'DRAFT' && canPostReceipt"
                  data-test="post-production-completion-receipt"
                  size="small"
                  text
                  :loading="actionLoading"
                  :disabled="actionLoading"
                  @click="postExecutionDocument('completionReceipt', row.id, row.receiptNo)"
                >
                  过账
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

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

.numeric-cell {
  display: inline-block;
  min-width: 72px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .detail-list,
  .summary-strip {
    grid-template-columns: 1fr;
  }
}
</style>
