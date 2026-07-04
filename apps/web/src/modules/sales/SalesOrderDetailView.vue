<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { salesApi, type ResourceId, type SalesOrderDetailRecord } from '../../shared/api/salesApi'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import SalesOrderStatusTag from './SalesOrderStatusTag.vue'
import {
  formatSalesDateTime,
  formatSalesQuantity,
  salesErrorMessage,
  salesShipmentStatusLabel,
  salesShipmentStatusTagType,
} from './salesPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<SalesOrderDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canEdit = computed(() => (
  record.value?.status === 'DRAFT' && authStore.hasPermission('sales:order:update')
))
const canConfirm = computed(() => (
  record.value?.status === 'DRAFT' && authStore.hasPermission('sales:order:confirm')
))
const canCancel = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('sales:order:cancel')
  && (record.value?.status === 'DRAFT' || (
    record.value?.status === 'CONFIRMED' && Number(record.value.shippedQuantity) <= 0
  ))
))
const canClose = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('sales:order:close')
  && (
    record.value?.status === 'CONFIRMED'
    || record.value?.status === 'PARTIALLY_SHIPPED'
    || record.value?.status === 'SHIPPED'
  )
))
const canCreateShipment = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('sales:shipment:create')
  && (record.value?.status === 'CONFIRMED' || record.value?.status === 'PARTIALLY_SHIPPED')
))
const canViewShipment = computed(() => authStore.hasPermission('sales:shipment:view'))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await salesApi.orders.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push({ name: 'sales-orders' })
}

function editOrder() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'sales-order-edit', params: { id: String(record.value.id) } })
}

function createShipment() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'sales-shipment-create', params: { orderId: String(record.value.id) } })
}

function viewShipment(shipmentId: ResourceId) {
  void router.push({ name: 'sales-shipment-detail', params: { id: String(shipmentId) } })
}

async function runOrderAction(action: 'confirm' | 'cancel' | 'close') {
  if (!record.value || actionLoading.value) {
    return
  }
  const actionLabels = {
    confirm: '确认',
    cancel: '取消',
    close: '关闭',
  }
  if (!window.confirm(`确认${actionLabels[action]}销售订单“${record.value.orderNo}”？`)) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'confirm') {
      await salesApi.orders.confirm(record.value.id)
    } else if (action === 'cancel') {
      await salesApi.orders.cancel(record.value.id)
    } else {
      await salesApi.orders.close(record.value.id)
    }
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
  <MasterDataTableView title="销售订单详情" description="查看销售订单主表、明细、出库进度和来源追溯摘要。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canEdit" data-test="edit-sales-order-detail" type="primary" @click="editOrder">
        编辑
      </el-button>
      <el-button
        v-if="canConfirm"
        data-test="confirm-sales-order-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="runOrderAction('confirm')"
      >
        确认
      </el-button>
      <el-button
        v-if="canCancel"
        data-test="cancel-sales-order-detail"
        type="danger"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="runOrderAction('cancel')"
      >
        取消
      </el-button>
      <el-button
        v-if="canClose"
        data-test="close-sales-order-detail"
        type="warning"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="runOrderAction('close')"
      >
        关闭
      </el-button>
      <el-button
        v-if="canCreateShipment"
        data-test="create-sales-shipment-detail"
        type="primary"
        plain
        @click="createShipment"
      >
        创建出库
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="销售订单详情加载中" :closable="false" />
      <el-alert v-if="!loading && error" class="state-alert" type="warning" title="销售订单详情加载失败" :closable="false" />
    </template>

    <div v-if="record" class="sales-order-detail">
      <section class="summary-strip">
        <div>
          <span>总数量</span>
          <strong>{{ formatSalesQuantity(record.totalQuantity) }}</strong>
        </div>
        <div>
          <span>已出库</span>
          <strong>{{ formatSalesQuantity(record.shippedQuantity) }}</strong>
        </div>
        <div>
          <span>未出库</span>
          <strong>{{ formatSalesQuantity(record.remainingQuantity) }}</strong>
        </div>
        <div>
          <span>订单日期</span>
          <strong>{{ record.orderDate }}</strong>
        </div>
        <div>
          <span>状态</span>
          <SalesOrderStatusTag :status="record.status" />
        </div>
      </section>

      <dl class="sales-order-detail-list">
        <dt>订单号</dt>
        <dd>{{ record.orderNo }}</dd>
        <dt>客户</dt>
        <dd>{{ record.customerCode }} {{ record.customerName }}</dd>
        <dt>预计交付</dt>
        <dd>{{ record.expectedShipDate || '-' }}</dd>
        <dt>明细行数</dt>
        <dd>{{ record.lineCount }}</dd>
        <dt>创建人</dt>
        <dd>{{ record.createdByName }}</dd>
        <dt>创建时间</dt>
        <dd>{{ formatSalesDateTime(record.createdAt) }}</dd>
        <dt>更新时间</dt>
        <dd>{{ formatSalesDateTime(record.updatedAt) }}</dd>
        <dt>备注</dt>
        <dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <section class="section-block">
        <div class="section-title">销售明细</div>
        <div class="table-scroll">
          <el-table :data="record.lines" empty-text="暂无销售明细" stripe>
            <el-table-column prop="lineNo" label="行号" width="78" />
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column prop="materialSpec" label="规格" min-width="120" show-overflow-tooltip />
            <el-table-column prop="unitName" label="单位" min-width="90" />
            <el-table-column label="销售数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已出库" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.shippedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="未出库" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.remainingQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="销售单价" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.unitPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="expectedShipDate" label="预计交付" min-width="120" />
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-title">出库记录</div>
        <el-empty v-if="record.shipments.length === 0" description="暂无出库记录" />
        <div v-else class="table-scroll">
          <el-table :data="record.shipments" empty-text="暂无出库记录" stripe>
            <el-table-column prop="shipmentNo" label="出库单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column label="状态" min-width="90">
              <template #default="{ row }">
                <el-tag :type="salesShipmentStatusTagType(row.status)" size="small">
                  {{ salesShipmentStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="出库数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesQuantity(row.totalQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="postedByName" label="过账人" min-width="100" />
            <el-table-column label="过账时间" min-width="150">
              <template #default="{ row }">
                {{ formatSalesDateTime(row.postedAt) }}
              </template>
            </el-table-column>
            <el-table-column v-if="canViewShipment" label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text data-test="view-sales-shipment-summary" @click="viewShipment(row.id)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.sales-order-detail {
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

.sales-order-detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.sales-order-detail-list dt {
  color: var(--qherp-muted);
}

.sales-order-detail-list dd {
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
  .sales-order-detail-list {
    grid-template-columns: 88px minmax(0, 1fr);
  }
}
</style>
