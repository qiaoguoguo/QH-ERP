<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { procurementApi, type PurchaseOrderDetailRecord, type ResourceId } from '../../shared/api/procurementApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import PurchaseOrderStatusTag from './PurchaseOrderStatusTag.vue'
import {
  formatProcurementDateTime,
  formatProcurementQuantity,
  procurementErrorMessage,
  purchaseReceiptStatusLabel,
  purchaseReceiptStatusTagType,
} from './procurementPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<PurchaseOrderDetailRecord | null>(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const canEdit = computed(() => (
  record.value?.status === 'DRAFT' && authStore.hasPermission('procurement:order:update')
))
const canConfirm = computed(() => (
  record.value?.status === 'DRAFT' && authStore.hasPermission('procurement:order:confirm')
))
const canCancel = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('procurement:order:cancel')
  && (record.value?.status === 'DRAFT' || (
    record.value?.status === 'CONFIRMED' && Number(record.value.receivedQuantity) <= 0
  ))
))
const canClose = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('procurement:order:close')
  && (
    record.value?.status === 'CONFIRMED'
    || record.value?.status === 'PARTIALLY_RECEIVED'
    || record.value?.status === 'RECEIVED'
  )
))
const canCreateReceipt = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('procurement:receipt:create')
  && (record.value?.status === 'CONFIRMED' || record.value?.status === 'PARTIALLY_RECEIVED')
))
const canViewReceipt = computed(() => authStore.hasPermission('procurement:receipt:view'))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await procurementApi.orders.get(route.params.id as ResourceId)
  } catch (caught) {
    record.value = null
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'procurement-orders' }))
}

function editOrder() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'procurement-order-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
}

function createReceipt() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'procurement-receipt-create', params: { orderId: String(record.value.id) } })
}

function viewReceipt(receiptId: ResourceId) {
  void router.push({
    name: 'procurement-receipt-detail',
    params: { id: String(receiptId) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
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
  if (!(await confirmAction(`确认${actionLabels[action]}采购订单“${record.value.orderNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    if (action === 'confirm') {
      await procurementApi.orders.confirm(record.value.id)
    } else if (action === 'cancel') {
      await procurementApi.orders.cancel(record.value.id)
    } else {
      await procurementApi.orders.close(record.value.id)
    }
    await loadRecord()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <MasterDataTableView title="采购订单详情" description="查看采购订单主表、明细、入库进度和来源追溯摘要。">
    <template #actions>
      <el-button @click="backToList">返回列表</el-button>
      <el-button v-if="canEdit" data-test="edit-purchase-order-detail" type="primary" @click="editOrder">
        编辑
      </el-button>
      <el-button
        v-if="canConfirm"
        data-test="confirm-purchase-order-detail"
        type="success"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="runOrderAction('confirm')"
      >
        确认
      </el-button>
      <el-button
        v-if="canCancel"
        data-test="cancel-purchase-order-detail"
        type="danger"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="runOrderAction('cancel')"
      >
        取消
      </el-button>
      <el-button
        v-if="canClose"
        data-test="close-purchase-order-detail"
        type="warning"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="runOrderAction('close')"
      >
        关闭
      </el-button>
      <el-button
        v-if="canCreateReceipt"
        data-test="create-purchase-receipt-detail"
        type="primary"
        plain
        @click="createReceipt"
      >
        创建入库
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="采购订单详情加载中" :closable="false" />
      <el-alert v-if="!loading && error" class="state-alert" type="warning" title="采购订单详情加载失败" :closable="false" />
    </template>

    <div v-if="record" class="purchase-order-detail">
      <section class="summary-strip">
        <div>
          <span>总数量</span>
          <strong>{{ formatProcurementQuantity(record.totalQuantity) }}</strong>
        </div>
        <div>
          <span>已入库</span>
          <strong>{{ formatProcurementQuantity(record.receivedQuantity) }}</strong>
        </div>
        <div>
          <span>未入库</span>
          <strong>{{ formatProcurementQuantity(record.remainingQuantity) }}</strong>
        </div>
        <div>
          <span>采购在途参考</span>
          <strong>{{ formatProcurementQuantity(record.inTransitQuantity) }}</strong>
        </div>
        <div>
          <span>在途状态</span>
          <strong>{{ record.inTransitStatusName || '-' }}</strong>
        </div>
        <div>
          <span>订单日期</span>
          <strong>{{ record.orderDate }}</strong>
        </div>
        <div>
          <span>状态</span>
          <PurchaseOrderStatusTag :status="record.status" />
        </div>
      </section>

      <dl class="purchase-order-detail-list">
        <dt>订单号</dt>
        <dd>{{ record.orderNo }}</dd>
        <dt>供应商</dt>
        <dd>{{ record.supplierCode }} {{ record.supplierName }}</dd>
        <dt>预计到货</dt>
        <dd>{{ record.expectedArrivalDate || '-' }}</dd>
        <dt>明细行数</dt>
        <dd>{{ record.lineCount }}</dd>
        <dt>创建人</dt>
        <dd>{{ record.createdByName }}</dd>
        <dt>创建时间</dt>
        <dd>{{ formatProcurementDateTime(record.createdAt) }}</dd>
        <dt>更新时间</dt>
        <dd>{{ formatProcurementDateTime(record.updatedAt) }}</dd>
        <dt>备注</dt>
        <dd>{{ record.remark || '未填写' }}</dd>
      </dl>

      <section class="section-block">
        <div class="section-title">采购明细</div>
        <div class="table-scroll">
          <el-table :data="record.lines" empty-text="暂无采购明细" stripe>
            <el-table-column prop="lineNo" label="行号" width="78" />
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column prop="materialSpec" label="规格" min-width="120" show-overflow-tooltip />
            <el-table-column prop="unitName" label="单位" min-width="90" />
            <el-table-column label="采购数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已入库" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.receivedQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="未入库" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.remainingQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="行在途参考" min-width="130" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.inTransitQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="在途状态" min-width="120">
              <template #default="{ row }">
                {{ row.inTransitStatusName || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="采购单价" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.unitPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="expectedArrivalDate" label="预计到货" min-width="120" />
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-title">入库记录</div>
        <el-empty v-if="record.receipts.length === 0" description="暂无入库记录" />
        <div v-else class="table-scroll">
          <el-table :data="record.receipts" empty-text="暂无入库记录" stripe>
            <el-table-column prop="receiptNo" label="入库单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
            <el-table-column prop="businessDate" label="业务日期" min-width="110" />
            <el-table-column label="状态" min-width="90">
              <template #default="{ row }">
                <el-tag :type="purchaseReceiptStatusTagType(row.status)" size="small">
                  {{ purchaseReceiptStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="入库数量" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatProcurementQuantity(row.totalQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="postedByName" label="过账人" min-width="100" />
            <el-table-column label="过账时间" min-width="150">
              <template #default="{ row }">
                {{ formatProcurementDateTime(row.postedAt) }}
              </template>
            </el-table-column>
            <el-table-column v-if="canViewReceipt" label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text data-test="view-purchase-receipt-summary" @click="viewReceipt(row.id)">
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
.purchase-order-detail {
  padding: 14px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
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

.purchase-order-detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 96px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.purchase-order-detail-list dt {
  color: var(--qherp-muted);
}

.purchase-order-detail-list dd {
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
  .purchase-order-detail-list {
    grid-template-columns: 88px minmax(0, 1fr);
  }
}
</style>
