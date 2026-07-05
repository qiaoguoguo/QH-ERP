<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { salesApi, type ResourceId, type SalesShipmentDetailRecord } from '../../shared/api/salesApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import SalesOrderStatusTag from './SalesOrderStatusTag.vue'
import SalesShipmentStatusTag from './SalesShipmentStatusTag.vue'
import {
  formatSalesDateTime,
  formatSalesQuantity,
  salesErrorMessage,
  salesMovementDirectionLabel,
  salesMovementTypeLabel,
} from './salesPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<SalesShipmentDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canEdit = computed(() => (
  record.value?.status === 'DRAFT' && authStore.hasPermission('sales:shipment:update')
))
const canPost = computed(() => (
  record.value?.status === 'DRAFT' && authStore.hasPermission('sales:shipment:post')
))
const canViewSourceOrder = computed(() => authStore.hasPermission('sales:order:view'))
const canViewInventoryMovements = computed(() => authStore.hasPermission('inventory:movement:view'))
const movements = computed(() => record.value?.inventoryMovements ?? [])

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await salesApi.shipments.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'sales-shipments' }))
}

function editShipment() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'sales-shipment-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
}

function viewSourceOrder() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'sales-order-detail',
    params: { id: String(record.value.orderId) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function viewInventoryMovements() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'inventory-movements',
    query: {
      movementType: 'SALES_SHIPMENT',
      warehouseId: String(record.value.warehouseId),
    },
  })
}

async function postShipment() {
  if (!record.value || actionLoading.value) {
    return
  }
  if (!(await confirmAction(`确认过账销售出库“${record.value.shipmentNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    await salesApi.shipments.post(record.value.id)
    await loadRecord()
  } catch (caught) {
    actionError.value = salesErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="销售出库详情" description="查看销售出库主表、明细、来源订单和库存流水追溯。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canViewSourceOrder && record" data-test="view-source-sales-order" @click="viewSourceOrder">
        查看来源订单
      </el-button>
      <el-button
        v-if="canViewInventoryMovements && record"
        data-test="view-sales-shipment-movements"
        @click="viewInventoryMovements"
      >
        查看库存流水
      </el-button>
      <el-button v-if="canEdit" data-test="edit-sales-shipment-detail" type="primary" @click="editShipment">
        编辑
      </el-button>
      <el-button
        v-if="canPost"
        data-test="post-sales-shipment-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="postShipment"
      >
        过账
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="销售出库详情加载中" :closable="false" />
      <el-alert v-if="record?.status === 'POSTED'" class="state-alert" type="info" title="已过账销售出库只读" :closable="false" />
      <el-alert v-if="!loading && error" class="state-alert" type="warning" title="销售出库详情加载失败" :closable="false" />
    </template>

    <div v-if="record" class="sales-shipment-detail">
      <section class="summary-strip">
        <div>
          <span>总数量</span>
          <strong>{{ formatSalesQuantity(record.totalQuantity) }}</strong>
        </div>
        <div>
          <span>业务日期</span>
          <strong>{{ record.businessDate }}</strong>
        </div>
        <div>
          <span>仓库</span>
          <strong>{{ record.warehouseName }}</strong>
        </div>
        <div>
          <span>状态</span>
          <SalesShipmentStatusTag :status="record.status" />
        </div>
      </section>

      <dl class="sales-shipment-detail-list">
        <dt>出库单号</dt>
        <dd>{{ record.shipmentNo }}</dd>
        <dt>客户</dt>
        <dd>{{ record.customerName }}</dd>
        <dt>明细行数</dt>
        <dd>{{ record.lineCount }}</dd>
        <dt>创建人</dt>
        <dd>{{ record.createdByName }}</dd>
        <dt>创建时间</dt>
        <dd>{{ formatSalesDateTime(record.createdAt) }}</dd>
        <dt>更新时间</dt>
        <dd>{{ formatSalesDateTime(record.updatedAt) }}</dd>
        <dt>过账人</dt>
        <dd>{{ record.postedByName || '-' }}</dd>
        <dt>过账时间</dt>
        <dd>{{ formatSalesDateTime(record.postedAt) }}</dd>
        <dt>备注</dt>
        <dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <section class="section-block">
        <div class="section-title">来源订单</div>
        <dl class="sales-shipment-detail-list">
          <dt>订单号</dt>
          <dd>{{ record.orderSummary.orderNo }}</dd>
          <dt>客户</dt>
          <dd>{{ record.orderSummary.customerName }}</dd>
          <dt>订单日期</dt>
          <dd>{{ record.orderSummary.orderDate }}</dd>
          <dt>订单状态</dt>
          <dd><SalesOrderStatusTag :status="record.orderSummary.status" /></dd>
          <dt>订单总数量</dt>
          <dd>{{ formatSalesQuantity(record.orderSummary.totalQuantity) }}</dd>
          <dt>订单未出库</dt>
          <dd>{{ formatSalesQuantity(record.orderSummary.remainingQuantity) }}</dd>
        </dl>
      </section>

      <section class="section-block">
        <div class="section-title">销售出库明细</div>
        <div class="table-scroll">
          <el-table :data="record.lines" empty-text="暂无销售出库明细" stripe>
            <el-table-column prop="lineNo" label="行号" width="78" />
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column prop="unitName" label="单位" min-width="90" />
            <el-table-column label="订单数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.orderedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="出库前已出" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.shippedQuantityBefore) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="出库前未出" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.remainingQuantityBefore) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="本次出库数量" min-width="130" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库存变动前" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.beforeQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库存变动后" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.afterQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-title">库存流水追溯</div>
        <el-empty v-if="movements.length === 0" description="暂无库存流水" />
        <div v-else class="table-scroll">
          <el-table :data="movements" empty-text="暂无库存流水" stripe>
            <el-table-column prop="movementNo" label="流水号" min-width="170" show-overflow-tooltip />
            <el-table-column label="类型" min-width="100">
              <template #default="{ row }">
                {{ salesMovementTypeLabel(row.movementType) }}
              </template>
            </el-table-column>
            <el-table-column label="方向" min-width="90">
              <template #default="{ row }">
                {{ salesMovementDirectionLabel(row.direction) }}
              </template>
            </el-table-column>
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column label="变动前" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.beforeQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="变动数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="变动后" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.afterQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column label="发生时间" min-width="150">
              <template #default="{ row }">
                {{ formatSalesDateTime(row.occurredAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" min-width="100" />
          </el-table>
        </div>
      </section>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.sales-shipment-detail {
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

.sales-shipment-detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.sales-shipment-detail-list dt {
  color: var(--qherp-muted);
}

.sales-shipment-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

.section-block {
  margin-top: 16px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 10px;
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
  .summary-strip,
  .sales-shipment-detail-list {
    grid-template-columns: 1fr;
  }
}
</style>
