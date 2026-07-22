<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createIdempotencyKey,
  documentPlatformApi,
  type DocumentTaskRecord,
} from '../../shared/api/documentPlatformApi'
import {
  procurementApi,
  type PurchaseOrderDetailRecord,
  type PurchaseScheduleRecord,
  type ResourceId,
} from '../../shared/api/procurementApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { pageItems } from '../system/shared/pageHelpers'
import {
  formatProcurementQuantity,
  procurementErrorMessage,
  purchaseOrderStatusLabel,
  purchaseScheduleStatusLabel,
} from './procurementPageHelpers'
import ProcurementDocumentTaskPanel from './ProcurementDocumentTaskPanel.vue'

interface ScheduleDraft {
  id: ResourceId
  orderLineId: ResourceId
  scheduleSeq: number
  expectedArrivalDate: string
  plannedQuantity: string
  remark: string
  closeReason: string
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const order = ref<PurchaseOrderDetailRecord | null>(null)
const schedules = ref<PurchaseScheduleRecord[]>([])
const drafts = reactive<Record<string, ScheduleDraft>>({})
const loading = ref(true)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const filters = reactive<{
  status?: PurchaseScheduleRecord['status']
  expectedDateFrom: string
  expectedDateTo: string
}>({
  status: undefined,
  expectedDateFrom: '',
  expectedDateTo: '',
})

const routeId = computed(() => route.params.id as ResourceId)
const canSave = computed(() => (
  Boolean(order.value?.allowedActions?.includes('UPDATE_SCHEDULES')) &&
  authStore.hasPermission('procurement:order:update')
))
const canCreateExportTask = computed(() => (
  authStore.hasPermission('procurement:order:view')
  && authStore.hasPermission('platform:document-task:create')
  && authStore.hasPermission('procurement:document:export')
))

function canClose(schedule: PurchaseScheduleRecord): boolean {
  return Boolean(schedule.allowedActions?.includes('CLOSE')) && authStore.hasPermission('procurement:order:close')
}

function draftKey(value: ResourceId): string {
  return String(value)
}

function syncDrafts() {
  Object.keys(drafts).forEach((key) => {
    delete drafts[key]
  })
  schedules.value.forEach((schedule) => {
    drafts[draftKey(schedule.id)] = {
      id: schedule.id,
      orderLineId: schedule.orderLineId,
      scheduleSeq: schedule.scheduleSeq,
      expectedArrivalDate: schedule.expectedArrivalDate,
      plannedQuantity: schedule.plannedQuantity,
      remark: schedule.remark ?? '',
      closeReason: schedule.closeReason ?? '',
    }
  })
}

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    const [orderRecord, schedulePage] = await Promise.all([
      procurementApi.orders.get(routeId.value),
      procurementApi.schedules.list(routeId.value, {
        status: filters.status,
        expectedDateFrom: filters.expectedDateFrom,
        expectedDateTo: filters.expectedDateTo,
        page: 1,
        pageSize: 50,
      }),
    ])
    order.value = orderRecord
    schedules.value = pageItems(schedulePage)
    syncDrafts()
  } catch (caught) {
    order.value = null
    schedules.value = []
    error.value = procurementErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function searchSchedules() {
  void loadPage()
}

function resetScheduleFilters() {
  filters.status = undefined
  filters.expectedDateFrom = ''
  filters.expectedDateTo = ''
  void loadPage()
}

function backToOrder() {
  void router.push(returnLocation(route, { name: 'procurement-order-detail', params: { id: String(routeId.value) } }))
}

function viewOrder() {
  void router.push({
    name: 'procurement-order-detail',
    params: { id: String(routeId.value) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function validateDrafts(): string {
  const firstInvalid = Object.values(drafts).find((draft) =>
    !draft.orderLineId || !draft.scheduleSeq || !draft.expectedArrivalDate || !draft.plannedQuantity.trim())
  if (firstInvalid) {
    return '请完整填写计划序号、预计日期和计划数量'
  }
  return ''
}

async function saveSchedules() {
  if (!order.value || actionLoading.value) {
    return
  }
  const validationError = validateDrafts()
  if (validationError) {
    actionError.value = validationError
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    const page = await procurementApi.schedules.replace(order.value.id, {
      version: order.value.version,
      idempotencyKey: createIdempotencyKey('purchase-schedules-replace'),
      lines: Object.values(drafts).map((draft) => ({
        orderLineId: draft.orderLineId,
        scheduleSeq: draft.scheduleSeq,
        expectedArrivalDate: draft.expectedArrivalDate,
        plannedQuantity: draft.plannedQuantity,
        remark: draft.remark,
      })),
    })
    schedules.value = pageItems(page)
    syncDrafts()
    await loadPage()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadPage()
  } finally {
    actionLoading.value = false
  }
}

async function exportSchedules() {
  if (!order.value || actionLoading.value) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createProcurementSchedules(order.value.id, {
      status: filters.status,
      expectedDateFrom: filters.expectedDateFrom,
      expectedDateTo: filters.expectedDateTo,
      idempotencyKey: createIdempotencyKey('purchase-schedules-export'),
    })
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function closeSchedule(schedule: PurchaseScheduleRecord) {
  const draft = drafts[draftKey(schedule.id)]
  if (!draft || actionLoading.value) {
    return
  }
  if (!draft.closeReason.trim()) {
    actionError.value = '请填写关闭原因'
    return
  }

  actionError.value = ''
  actionLoading.value = true
  try {
    await procurementApi.schedules.close(schedule.orderId, schedule.id, {
      version: schedule.version,
      reason: draft.closeReason,
      idempotencyKey: createIdempotencyKey('purchase-schedule-close'),
    })
    await loadPage()
  } catch (caught) {
    actionError.value = procurementErrorMessage(caught)
    await loadPage()
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  void loadPage()
})
</script>

<template>
  <MasterDataTableView title="采购到货计划" description="维护采购订单多批到货计划并追踪计划收货进度。">
    <template #actions>
      <el-button @click="backToOrder">返回订单</el-button>
      <el-button v-if="order" data-test="view-purchase-order-from-schedules" @click="viewOrder">
        查看订单
      </el-button>
      <el-button
        v-if="canCreateExportTask"
        data-test="export-purchase-schedules"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="exportSchedules"
      >
        当前筛选导出
      </el-button>
      <el-button
        v-if="canSave"
        data-test="save-purchase-schedules"
        type="primary"
        :loading="actionLoading"
        :disabled="actionLoading"
        @click="saveSchedules"
      >
        保存计划
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="计划状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="计划中" value="PLANNED" />
            <el-option label="部分到货" value="PARTIALLY_RECEIVED" />
            <el-option label="已收齐" value="RECEIVED" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计到货">
          <el-date-picker
            v-model="filters.expectedDateFrom"
            name="schedule-expected-date-from"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            value-on-clear=""
            placeholder="起始日期"
          />
        </el-form-item>
        <el-form-item>
          <el-date-picker
            v-model="filters.expectedDateTo"
            name="schedule-expected-date-to"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            value-on-clear=""
            placeholder="截止日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-purchase-schedules" type="primary" @click="searchSchedules">查询</el-button>
          <el-button data-test="reset-purchase-schedules" @click="resetScheduleFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="到货计划加载中" :closable="false" />
    </template>

    <section v-if="order" class="schedule-summary">
      <div>
        <span>采购订单</span>
        <strong>{{ order.orderNo }}</strong>
      </div>
      <div>
        <span>计划/已入库/剩余</span>
        <strong>
          {{ formatProcurementQuantity(order.totalQuantity) }}/{{
            formatProcurementQuantity(order.receivedQuantity)
          }}/{{ formatProcurementQuantity(order.remainingQuantity) }}
        </strong>
      </div>
      <div>
        <span>下一到货日</span>
        <strong>{{ order.nextArrivalDate || order.expectedArrivalDate || '-' }}</strong>
      </div>
      <div>
        <span>状态</span>
        <strong>{{ purchaseOrderStatusLabel(order.status, order.statusName) }}</strong>
      </div>
    </section>

    <ProcurementDocumentTaskPanel :task="latestDocumentTask" />

    <div class="table-scroll">
      <el-table :data="schedules" empty-text="暂无到货计划" stripe>
        <el-table-column label="计划序号" width="100">
          <template #default="{ row }">
            <el-input-number
              v-if="drafts[draftKey(row.id)]"
              v-model="drafts[draftKey(row.id)].scheduleSeq"
              :min="1"
              :disabled="!canSave || actionLoading"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="物料" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.materialCode || `行 ${row.lineNo}` }} {{ row.materialName || '' }}
          </template>
        </el-table-column>
        <el-table-column label="计划数量" min-width="150">
          <template #default="{ row }">
            <el-input
              v-if="drafts[draftKey(row.id)]"
              v-model="drafts[draftKey(row.id)].plannedQuantity"
              :name="`schedule-quantity-${row.id}`"
              :data-test="`schedule-quantity-${row.id}`"
              :disabled="!canSave || actionLoading"
            />
          </template>
        </el-table-column>
        <el-table-column label="已收/剩余" min-width="130">
          <template #default="{ row }">
            {{ formatProcurementQuantity(row.receivedQuantity) }}/{{ formatProcurementQuantity(row.remainingQuantity) }}
          </template>
        </el-table-column>
        <el-table-column label="预计日期" min-width="150">
          <template #default="{ row }">
            <el-date-picker
              v-if="drafts[draftKey(row.id)]"
              v-model="drafts[draftKey(row.id)].expectedArrivalDate"
              :disabled="!canSave || actionLoading"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              value-on-clear=""
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }">
            {{ purchaseScheduleStatusLabel(row.status, row.statusName) }}
          </template>
        </el-table-column>
        <el-table-column label="关闭原因" min-width="220">
          <template #default="{ row }">
            <div>关闭原因：{{ row.closeReason || '未关闭' }}</div>
            <el-input
              v-if="canClose(row) && drafts[draftKey(row.id)]"
              v-model="drafts[draftKey(row.id)].closeReason"
              :name="`schedule-close-reason-${row.id}`"
              :data-test="`schedule-close-reason-${row.id}`"
              placeholder="填写关闭原因"
              :disabled="actionLoading"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <el-button
              v-if="canClose(row)"
              :data-test="`close-purchase-schedule-${row.id}`"
              size="small"
              text
              type="warning"
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="closeSchedule(row)"
            >
              关闭
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.schedule-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 0 0 14px;
  padding: 0 14px;
}

.schedule-summary > div {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  padding: 10px 12px;
}

.schedule-summary span {
  color: var(--qherp-muted);
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.schedule-summary strong {
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.table-scroll {
  overflow-x: auto;
  padding: 0 14px 14px;
}
</style>
