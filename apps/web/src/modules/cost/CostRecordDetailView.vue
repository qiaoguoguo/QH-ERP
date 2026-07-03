<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  costCollectionApi,
  type CostRecordDetailRecord,
  type ResourceId,
} from '../../shared/api/costCollectionApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import CostSourceTypeTag from './CostSourceTypeTag.vue'
import CostTypeTag from './CostTypeTag.vue'
import {
  basisTypeLabel,
  costErrorMessage,
  costStatusLabel,
  formatCostAmount,
  formatCostDateTime,
  formatCostQuantity,
  sourceDocumentTypeLabel,
} from './costPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<CostRecordDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')

const canEdit = computed(() => record.value?.sourceType === 'MANUAL_ENTRY' && authStore.hasPermission('cost:record:update'))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await costCollectionApi.records.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = costErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push({ name: 'cost-records' })
}

function viewWorkOrder() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'production-work-order-detail', params: { id: String(record.value.workOrderId) } })
}

function editRecord() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'cost-record-edit', params: { id: String(record.value.id) } })
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="成本记录详情" description="查看成本归集业务记录、工单和来源单据追溯。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="record" @click="viewWorkOrder">生产工单</el-button>
      <el-button v-if="canEdit" type="primary" data-test="edit-cost-record-detail" @click="editRecord">编辑</el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="成本记录详情加载中" :closable="false" />
      <el-alert
        v-if="record"
        class="state-alert"
        type="info"
        title="当前为成本业务记录与来源追溯，不代表正式财务核算结果。"
        :closable="false"
      />
    </template>

    <div v-if="record" class="cost-detail">
      <section class="summary-strip">
        <div>
          <span>数量口径</span>
          <strong>{{ formatCostQuantity(record.quantity) }}</strong>
        </div>
        <div>
          <span>单价口径</span>
          <strong>{{ formatCostAmount(record.unitPrice) }}</strong>
        </div>
        <div>
          <span>金额口径</span>
          <strong>{{ formatCostAmount(record.amount) }}</strong>
        </div>
        <div>
          <span>业务日期</span>
          <strong>{{ record.businessDate }}</strong>
        </div>
      </section>

      <dl class="detail-list">
        <dt>记录编号</dt>
        <dd>{{ record.recordNo }}</dd>
        <dt>状态</dt>
        <dd>{{ costStatusLabel(record.status) }}</dd>
        <dt>成本类型</dt>
        <dd><CostTypeTag :type="record.costType" /></dd>
        <dt>来源类型</dt>
        <dd><CostSourceTypeTag :type="record.sourceType" /></dd>
        <dt>口径类型</dt>
        <dd>{{ basisTypeLabel(record.basisType) }}</dd>
        <dt>来源单据</dt>
        <dd>{{ sourceDocumentTypeLabel(record.sourceDocumentType) }} {{ record.sourceDocumentNo || '-' }}</dd>
        <dt>工单</dt>
        <dd>{{ record.workOrderNo }}（{{ record.workOrderStatus }}）</dd>
        <dt>产品</dt>
        <dd>{{ record.productMaterialCode }} {{ record.productMaterialName }}</dd>
        <dt>物料</dt>
        <dd>{{ record.materialCode ? `${record.materialCode} ${record.materialName || ''}` : '-' }}</dd>
        <dt>单位</dt>
        <dd>{{ record.unitName || '-' }}</dd>
        <dt>记录人</dt>
        <dd>{{ record.recordedByName }}</dd>
        <dt>记录时间</dt>
        <dd>{{ formatCostDateTime(record.recordedAt) }}</dd>
        <dt>创建人</dt>
        <dd>{{ record.createdByName }}</dd>
        <dt>创建时间</dt>
        <dd>{{ formatCostDateTime(record.createdAt) }}</dd>
        <dt>更新时间</dt>
        <dd>{{ formatCostDateTime(record.updatedAt) }}</dd>
        <dt>备注</dt>
        <dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <section class="section-block">
        <h2>来源摘要</h2>
        <dl v-if="record.sourceSummary" class="detail-list compact">
          <dt>来源状态</dt>
          <dd>{{ record.sourceSummary.sourceStatus || record.sourceStatus || '-' }}</dd>
          <dt>来源单号</dt>
          <dd>{{ record.sourceSummary.sourceDocumentNo || record.sourceDocumentNo || '-' }}</dd>
          <dt>来源数量</dt>
          <dd>{{ formatCostQuantity(record.sourceSummary.quantity) }}</dd>
          <dt>来源物料</dt>
          <dd>
            {{
              record.sourceSummary.materialCode
                ? `${record.sourceSummary.materialCode} ${record.sourceSummary.materialName || ''}`
                : '-'
            }}
          </dd>
          <dt>单位</dt>
          <dd>{{ record.sourceSummary.unitName || '-' }}</dd>
        </dl>
        <el-empty v-else description="暂无来源摘要" />
      </section>

      <section class="section-block">
        <h2>完工入库产出追溯</h2>
        <div class="table-scroll">
          <el-table :data="record.outputTrace" empty-text="暂无完工入库产出追溯" stripe>
            <el-table-column prop="receiptNo" label="入库单号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column prop="receiptWarehouseName" label="入库仓库" min-width="140" show-overflow-tooltip />
            <el-table-column label="入库数量" min-width="110" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatCostQuantity(row.quantity) }}</span></template>
            </el-table-column>
            <el-table-column label="变动前" min-width="110" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatCostQuantity(row.beforeQuantity) }}</span></template>
            </el-table-column>
            <el-table-column label="变动后" min-width="110" align="right">
              <template #default="{ row }"><span class="numeric-cell">{{ formatCostQuantity(row.afterQuantity) }}</span></template>
            </el-table-column>
            <el-table-column prop="postedByName" label="过账人" min-width="110" />
            <el-table-column label="过账时间" min-width="150">
              <template #default="{ row }">{{ formatCostDateTime(row.postedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <h2>审计摘要</h2>
        <div class="table-scroll">
          <el-table :data="record.auditSummary" empty-text="暂无审计记录" stripe>
            <el-table-column prop="id" label="审计标识" min-width="110" />
            <el-table-column prop="operatorUsername" label="操作账号" min-width="130" />
            <el-table-column prop="action" label="动作" min-width="210" show-overflow-tooltip />
            <el-table-column label="操作时间" min-width="150">
              <template #default="{ row }">{{ formatCostDateTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.cost-detail {
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
  word-break: break-word;
}

.detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 18px;
}

.detail-list.compact {
  margin-bottom: 0;
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
