<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  salesApi,
  type ResourceId,
  type SalesOrderAction,
  type SalesOrderDetailRecord,
  type SalesOrderLineRecord,
} from '../../shared/api/salesApi'
import { createIdempotencyKey } from '../../shared/api/documentPlatformApi'
import {
  salesFulfillmentApi,
  type SalesDeliveryPlanRecord,
  type SalesOrderChangeLinePayload,
  type SalesOrderChangeRecord,
} from '../../shared/api/salesFulfillmentApi'
import { currentRouteReturnTo, queryWithReturnTo, returnLocation, routeReturnTo } from '../../shared/navigation/navigationReturn'
import { useAuthStore } from '../../stores/authStore'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import FixedPrintAction from '../platform/components/FixedPrintAction.vue'
import SalesOrderStatusTag from './SalesOrderStatusTag.vue'
import {
  approvalStatusLabel,
  deliveryPlanDate,
  deliveryPlanStatusLabel,
  formatSalesDecimal,
  orderChangeStatusLabel,
  salesFulfillmentErrorMessage,
  salesSourceChainLabel,
} from './salesFulfillmentPageHelpers'
import {
  formatSalesDateTime,
  formatSalesQuantity,
  salesErrorMessage,
  salesOrderTaxIncludedAmount,
  salesShipmentStatusLabel,
  salesShipmentStatusTagType,
} from './salesPageHelpers'
import { confirmAction } from '../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<SalesOrderDetailRecord | null>(null)
const deliveryPlans = ref<SalesDeliveryPlanRecord[]>([])
const orderChanges = ref<SalesOrderChangeRecord[]>([])
const loading = ref(true)
const deliveryPlanLoading = ref(false)
const orderChangeLoading = ref(false)
const error = ref('')
const deliveryPlanError = ref('')
const orderChangeError = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const shortCloseDialog = ref({
  visible: false,
  reason: '',
  error: '',
})
const creditOverrideDialog = ref({
  visible: false,
  reason: '',
  error: '',
})
interface OrderChangeTargetDraft {
  targetQuantity: string
  untaxedUnitPrice: string
  taxIncludedUnitPrice: string
  taxRate: string
  plannedDate: string
}

const orderChangeDialog = ref<{
  visible: boolean
  mode: 'create' | 'edit'
  record: SalesOrderChangeRecord | null
  reason: string
  targets: Record<string, OrderChangeTargetDraft>
  error: string
}>({
  visible: false,
  mode: 'create',
  record: null,
  reason: '',
  targets: {},
  error: '',
})
const deliveryPlanDialog = ref<{
  visible: boolean
  lines: Record<string, { planDate: string; quantity: string }>
  error: string
}>({
  visible: false,
  lines: {},
  error: '',
})
const closePlanDialog = ref<{
  visible: boolean
  plan: SalesDeliveryPlanRecord | null
  reason: string
  error: string
}>({
  visible: false,
  plan: null,
  reason: '',
  error: '',
})

function hasAllowedAction(action: SalesOrderAction) {
  return (record.value?.allowedActions ?? []).includes(action)
}

const canEdit = computed(() => (
  hasAllowedAction('UPDATE') && authStore.hasPermission('sales:order:update')
))
const canConfirm = computed(() => (
  hasAllowedAction('CONFIRM') && authStore.hasPermission('sales:order:confirm')
))
const canCancel = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('sales:order:cancel')
  && hasAllowedAction('CANCEL')
))
const canClose = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('sales:order:close')
  && hasAllowedAction('CLOSE')
))
const canCreateShipment = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('sales:shipment:create')
  && hasAllowedAction('CREATE_SHIPMENT')
))
const canViewShipment = computed(() => authStore.hasPermission('sales:shipment:view'))
const canViewDeliveryPlans = computed(() => authStore.hasPermission('sales:delivery-plan:view'))
const canViewOrderChanges = computed(() => authStore.hasPermission('sales:order-change:view'))
const canManageDeliveryPlans = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('sales:delivery-plan:manage')
  && hasAllowedAction('UPDATE_DELIVERY_PLAN')
))
const canCreateOrderChange = computed(() => (
  Boolean(record.value)
  && authStore.hasPermission('sales:order-change:create')
  && hasAllowedAction('CREATE_CHANGE')
))
const canSubmitShortClose = computed(() => (
  hasAllowedAction('SUBMIT_SHORT_CLOSE') && authStore.hasPermission('sales:order:short-close-submit')
))
const canSubmitCreditOverride = computed(() => (
  hasAllowedAction('SUBMIT_CREDIT_OVERRIDE') && authStore.hasPermission('sales:credit:override-submit')
))

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    const detail = await salesApi.orders.get(route.params.id as ResourceId)
    record.value = {
      ...detail,
      lines: Array.isArray(detail.lines) ? detail.lines : [],
      shipments: Array.isArray(detail.shipments) ? detail.shipments : [],
    }
    await Promise.all([
      canViewDeliveryPlans.value ? loadDeliveryPlans() : Promise.resolve(),
      canViewOrderChanges.value ? loadOrderChanges() : Promise.resolve(),
    ])
  } catch (caught) {
    record.value = null
    error.value = salesErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadDeliveryPlans() {
  if (!record.value) {
    return
  }
  deliveryPlanLoading.value = true
  deliveryPlanError.value = ''
  try {
    const response = await salesFulfillmentApi.deliveryPlans.listByOrder(record.value.id)
    const payload = response as { lines?: SalesDeliveryPlanRecord[]; items?: SalesDeliveryPlanRecord[] }
    deliveryPlans.value = Array.isArray(payload.lines)
      ? payload.lines
      : (Array.isArray(payload.items) ? payload.items : [])
  } catch (caught) {
    deliveryPlans.value = []
    deliveryPlanError.value = salesFulfillmentErrorMessage(caught)
  } finally {
    deliveryPlanLoading.value = false
  }
}

async function loadOrderChanges() {
  if (!record.value) {
    return
  }
  orderChangeLoading.value = true
  orderChangeError.value = ''
  try {
    const page = await salesFulfillmentApi.orderChanges.list(record.value.id, {
      status: undefined,
      page: 1,
      pageSize: 20,
    })
    orderChanges.value = Array.isArray(page.items) ? page.items : []
  } catch (caught) {
    orderChanges.value = []
    orderChangeError.value = salesFulfillmentErrorMessage(caught)
  } finally {
    orderChangeLoading.value = false
  }
}

function backToList() {
  void router.push(returnLocation(route, { name: 'sales-orders' }))
}

function editOrder() {
  if (!record.value) {
    return
  }
  void router.push({
    name: 'sales-order-edit',
    params: { id: String(record.value.id) },
    query: queryWithReturnTo({}, routeReturnTo(route)),
  })
}

function createShipment() {
  if (!record.value) {
    return
  }
  void router.push({ name: 'sales-shipment-create', params: { orderId: String(record.value.id) } })
}

function viewShipment(shipmentId: ResourceId) {
  void router.push({
    name: 'sales-shipment-detail',
    params: { id: String(shipmentId) },
    query: queryWithReturnTo({}, currentRouteReturnTo(route)),
  })
}

function openShortCloseDialog() {
  shortCloseDialog.value = { visible: true, reason: '', error: '' }
}

function openCreditOverrideDialog() {
  creditOverrideDialog.value = { visible: true, reason: '', error: '' }
}

function orderChangeAllowed(change: SalesOrderChangeRecord, action: 'UPDATE' | 'SUBMIT_APPROVAL' | 'CANCEL') {
  const permission = {
    UPDATE: 'sales:order-change:update',
    SUBMIT_APPROVAL: 'sales:order-change:submit',
    CANCEL: 'sales:order-change:cancel',
  }[action]
  return (change.allowedActions ?? []).includes(action) && authStore.hasPermission(permission)
}

function newOrderChangeTarget(): OrderChangeTargetDraft {
  return {
    targetQuantity: '',
    untaxedUnitPrice: '',
    taxIncludedUnitPrice: '',
    taxRate: '',
    plannedDate: '',
  }
}

function openCreateOrderChange() {
  if (!record.value) {
    return
  }
  orderChangeDialog.value = {
    visible: true,
    mode: 'create',
    record: null,
    reason: '',
    targets: Object.fromEntries(record.value.lines.map((line) => [String(line.id), newOrderChangeTarget()])),
    error: '',
  }
}

async function openEditOrderChange(change: SalesOrderChangeRecord) {
  if (!record.value || actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const detail = await salesFulfillmentApi.orderChanges.get(change.id)
    const targets = Object.fromEntries(record.value.lines.map((line) => [String(line.id), newOrderChangeTarget()]))
    ;(detail.lines ?? []).forEach((line) => {
      targets[String(line.orderLineId)] = {
        targetQuantity: line.targetQuantity ?? line.newQuantity ?? '',
        untaxedUnitPrice: line.untaxedUnitPrice ?? line.taxExcludedUnitPrice ?? '',
        taxIncludedUnitPrice: line.taxIncludedUnitPrice ?? '',
        taxRate: line.taxRate ?? '',
        plannedDate: line.plannedDate ?? line.newPlannedDate ?? '',
      }
    })
    orderChangeDialog.value = {
      visible: true,
      mode: 'edit',
      record: detail,
      reason: detail.reason,
      targets,
      error: '',
    }
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadOrderChanges()
  } finally {
    actionLoading.value = false
  }
}

function orderChangeLines(requireValue: boolean) {
  if (!record.value) {
    return []
  }
  return record.value.lines
    .map((line) => {
      const target = orderChangeDialog.value.targets[String(line.id)] ?? newOrderChangeTarget()
      const targetQuantity = target.targetQuantity.trim()
      const untaxedUnitPrice = target.untaxedUnitPrice.trim()
      const taxIncludedUnitPrice = target.taxIncludedUnitPrice.trim()
      const taxRate = target.taxRate.trim()
      const plannedDate = target.plannedDate.trim()
      if (!targetQuantity && !untaxedUnitPrice && !taxIncludedUnitPrice && !taxRate && !plannedDate) {
        if (requireValue) {
          return null
        }
        return null
      }
      return {
        orderLineId: line.id,
        ...(targetQuantity ? { targetQuantity } : {}),
        ...(untaxedUnitPrice ? { untaxedUnitPrice } : {}),
        ...(taxIncludedUnitPrice ? { taxIncludedUnitPrice } : {}),
        ...(taxRate ? { taxRate } : {}),
        ...(plannedDate ? { plannedDate } : {}),
      }
    })
    .filter((line): line is SalesOrderChangeLinePayload => line !== null)
}

async function confirmOrderChange() {
  if (!record.value || actionLoading.value) {
    return
  }
  const reasonError = validateActionReason(orderChangeDialog.value.reason)
  if (reasonError) {
    orderChangeDialog.value.error = reasonError
    return
  }
  const lines = orderChangeLines(orderChangeDialog.value.mode === 'create')
  if (!lines.length) {
    orderChangeDialog.value.error = '请至少填写一行变更数量'
    return
  }
  actionLoading.value = true
  orderChangeDialog.value.error = ''
  try {
    if (orderChangeDialog.value.mode === 'edit' && orderChangeDialog.value.record) {
      await salesFulfillmentApi.orderChanges.update(orderChangeDialog.value.record.id, {
        version: orderChangeDialog.value.record.version,
        reason: orderChangeDialog.value.reason.trim(),
        idempotencyKey: createIdempotencyKey('sales-order-change-update'),
        lines,
      })
    } else {
      await salesFulfillmentApi.orderChanges.create(record.value.id, {
        version: record.value.version,
        reason: orderChangeDialog.value.reason.trim(),
        idempotencyKey: createIdempotencyKey('sales-order-change-create'),
        lines,
      })
    }
    orderChangeDialog.value = {
      visible: false,
      mode: 'create',
      record: null,
      reason: '',
      targets: {},
      error: '',
    }
    await loadOrderChanges()
  } catch (caught) {
    orderChangeDialog.value.error = salesFulfillmentErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

async function submitOrderChange(change: SalesOrderChangeRecord) {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.orderChanges.submitApproval(change.id, {
      version: change.version,
      reason: '提交订单变更审批',
      idempotencyKey: createIdempotencyKey('sales-order-change-submit'),
    })
    await loadOrderChanges()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

async function cancelOrderChange(change: SalesOrderChangeRecord) {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await salesFulfillmentApi.orderChanges.cancel(change.id, {
      version: change.version,
      reason: '取消订单变更',
      idempotencyKey: createIdempotencyKey('sales-order-change-cancel'),
    })
    await loadOrderChanges()
  } catch (caught) {
    actionError.value = salesFulfillmentErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

function openDeliveryPlanAdjust() {
  if (!record.value) {
    return
  }
  deliveryPlanDialog.value = {
    visible: true,
    error: '',
    lines: Object.fromEntries(record.value.lines.map((line) => [
      String(line.id),
      {
        planDate: line.expectedShipDate ?? record.value?.expectedShipDate ?? '',
        quantity: '',
      },
    ])),
  }
}

async function confirmDeliveryPlanAdjust() {
  if (!record.value || actionLoading.value) {
    return
  }
  const lines = record.value.lines
    .map((line) => ({
      orderLineId: line.id,
      draft: deliveryPlanDialog.value.lines[String(line.id)],
    }))
    .filter(({ draft }) => draft?.planDate.trim() && draft.quantity.trim())
    .map(({ orderLineId, draft }) => ({
      orderLineId,
      planDate: draft.planDate.trim(),
      quantity: draft.quantity.trim(),
    }))
  if (!lines.length) {
    deliveryPlanDialog.value.error = '请至少填写一行计划日期和数量'
    return
  }
  actionLoading.value = true
  deliveryPlanDialog.value.error = ''
  try {
    await salesFulfillmentApi.deliveryPlans.replaceForOrder(record.value.id, {
      version: record.value.version,
      reason: '调整交付计划',
      idempotencyKey: createIdempotencyKey('sales-delivery-plan-adjust'),
      lines,
    })
    deliveryPlanDialog.value.visible = false
    await loadDeliveryPlans()
  } catch (caught) {
    deliveryPlanDialog.value.error = salesFulfillmentErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

function openCloseDeliveryPlan(plan: SalesDeliveryPlanRecord) {
  closePlanDialog.value = {
    visible: true,
    plan,
    reason: '',
    error: '',
  }
}

async function confirmCloseDeliveryPlan() {
  if (!record.value || !closePlanDialog.value.plan || actionLoading.value) {
    return
  }
  const reasonError = validateActionReason(closePlanDialog.value.reason)
  if (reasonError) {
    closePlanDialog.value.error = reasonError
    return
  }
  actionLoading.value = true
  closePlanDialog.value.error = ''
  try {
    await salesFulfillmentApi.deliveryPlans.close(record.value.id, closePlanDialog.value.plan.id, {
      version: closePlanDialog.value.plan.version,
      reason: closePlanDialog.value.reason.trim(),
      idempotencyKey: createIdempotencyKey('sales-delivery-plan-close'),
    })
    closePlanDialog.value.visible = false
    await loadDeliveryPlans()
  } catch (caught) {
    closePlanDialog.value.error = salesFulfillmentErrorMessage(caught)
    await loadRecord()
  } finally {
    actionLoading.value = false
  }
}

function sourceSummary() {
  if (!record.value) {
    return '-'
  }
  if (record.value.priceSourceType === 'QUOTE') {
    return `报价 ${record.value.priceSourceNo ?? record.value.sourceQuoteNo ?? '来源未返回'}`
  }
  if (record.value.priceSourceType === 'LEGACY_MANUAL') {
    return '历史手工订单'
  }
  return '手工订单'
}

function amountSummary() {
  if (!record.value) {
    return '-'
  }
  if (record.value.amountRestricted) {
    return '金额受限'
  }
  return `含税 ${formatSalesDecimal(salesOrderTaxIncludedAmount(record.value))} ${record.value.currency ?? 'CNY'}`
}

function creditSummary() {
  if (!record.value) {
    return '-'
  }
  if (record.value.creditRestricted) {
    return '信用信息受限'
  }
  return record.value.creditStatusName ?? '信用状态未返回'
}

function linePriceSource(line: SalesOrderLineRecord) {
  if (line.priceSourceType === 'QUOTE') {
    return `报价 ${line.priceSourceNo ?? '来源未返回'}`
  }
  if (line.priceSourceType === 'LEGACY_MANUAL') {
    return '历史手工'
  }
  return '手工'
}

function deliveryPlanQuantitySummary(plan: SalesDeliveryPlanRecord) {
  return `${formatSalesDecimal(plan.plannedQuantity)}/${formatSalesDecimal(plan.shippedQuantity)}/${formatSalesDecimal(plan.remainingQuantity)}`
}

function validateActionReason(reason: string) {
  const text = reason.trim()
  if (text.length < 1 || text.length > 200) {
    return '请填写 1-200 字原因'
  }
  return ''
}

async function submitShortCloseApproval() {
  if (!record.value || actionLoading.value) {
    return
  }
  const reasonError = validateActionReason(shortCloseDialog.value.reason)
  if (reasonError) {
    shortCloseDialog.value.error = reasonError
    return
  }
  actionLoading.value = true
  shortCloseDialog.value.error = ''
  try {
    await salesFulfillmentApi.orders.submitShortClose(record.value.id, {
      version: record.value.version,
      reason: shortCloseDialog.value.reason.trim(),
      idempotencyKey: createIdempotencyKey('sales-order-short-close'),
    })
    shortCloseDialog.value.visible = false
    await loadRecord()
  } catch (caught) {
    shortCloseDialog.value.error = salesFulfillmentErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function submitCreditOverrideApproval() {
  if (!record.value || actionLoading.value) {
    return
  }
  const reasonError = validateActionReason(creditOverrideDialog.value.reason)
  if (reasonError) {
    creditOverrideDialog.value.error = reasonError
    return
  }
  actionLoading.value = true
  creditOverrideDialog.value.error = ''
  try {
    await salesFulfillmentApi.orders.submitCreditOverride(record.value.id, {
      version: record.value.version,
      reason: creditOverrideDialog.value.reason.trim(),
      idempotencyKey: createIdempotencyKey('sales-order-credit-override'),
    })
    creditOverrideDialog.value.visible = false
    await loadRecord()
  } catch (caught) {
    creditOverrideDialog.value.error = salesFulfillmentErrorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function runOrderAction(action: 'confirm' | 'cancel' | 'close') {
  if (!record.value || actionLoading.value) {
    return
  }
  if (action === 'confirm' && record.value.lines.some((line) => !line.reservationWarehouseId)) {
    actionError.value = '销售订单确认前每行必须选择预留仓库，确认只会按预留仓库现货库存预留，不使用采购在途'
    return
  }
  const actionLabels = {
    confirm: '确认',
    cancel: '取消',
    close: '关闭',
  }
  if (!(await confirmAction(`确认${actionLabels[action]}销售订单“${record.value.orderNo}”？`))) {
    return
  }

  actionError.value = ''
  actionLoading.value = true
  const payload = {
    version: record.value.version,
    idempotencyKey: createIdempotencyKey(`sales-order-${action}`),
    ...(action === 'cancel' ? { reason: '客户取消' } : {}),
    ...(action === 'close' ? { reason: '履约完成' } : {}),
  }
  try {
    if (action === 'confirm') {
      await salesApi.orders.confirm(record.value.id, payload)
    } else if (action === 'cancel') {
      await salesApi.orders.cancel(record.value.id, payload)
    } else {
      await salesApi.orders.close(record.value.id, payload)
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
      <el-button
        v-if="canSubmitShortClose"
        data-test="submit-sales-order-short-close"
        type="warning"
        plain
        :loading="actionLoading"
        @click="openShortCloseDialog"
      >
        提交短交关闭审批
      </el-button>
      <el-button
        v-if="canSubmitCreditOverride"
        data-test="submit-sales-order-credit-override"
        type="warning"
        plain
        :loading="actionLoading"
        @click="openCreditOverrideDialog"
      >
        提交信用例外审批
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
        <div>
          <span>税价</span>
          <strong>{{ amountSummary() }}</strong>
        </div>
        <div>
          <span>信用</span>
          <strong>{{ creditSummary() }}</strong>
        </div>
      </section>

      <dl class="sales-order-detail-list">
        <dt>订单号</dt>
        <dd>{{ record.orderNo }}</dd>
        <dt>客户</dt>
        <dd>{{ record.customerCode }} {{ record.customerName }}</dd>
        <dt>项目合同</dt>
        <dd v-if="record.projectId && record.contractId">
          {{ record.projectNo }} {{ record.projectName }} / {{ record.contractNo }}
        </dd>
        <dd v-else>未关联项目</dd>
        <dt>预计交付</dt>
        <dd>{{ record.expectedShipDate || '-' }}</dd>
        <dt>来源链</dt>
        <dd>{{ salesSourceChainLabel(Boolean(record.sourceQuoteId)) }}</dd>
        <dt>价格来源</dt>
        <dd>{{ sourceSummary() }}</dd>
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

      <FixedPrintAction
        class="section-block"
        object-type="SALES_ORDER"
        :object-id="record.id"
        :object-no="record.orderNo"
        :object-status="record.status"
        :allowed-object-statuses="['CONFIRMED', 'PARTIALLY_SHIPPED', 'SHIPPED', 'CLOSED']"
        title="销售订单固定打印"
      />

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
            <el-table-column label="预留仓库" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.reservationWarehouseName || '-' }}
              </template>
            </el-table-column>
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
            <el-table-column label="含税单价" min-width="110" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesDecimal(row.taxIncludedUnitPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="税率" min-width="90" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesDecimal(row.taxRate) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="含税金额" min-width="120" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ formatSalesDecimal(row.taxIncludedAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="价格来源" min-width="120">
              <template #default="{ row }">
                {{ linePriceSource(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="expectedShipDate" label="预计交付" min-width="120" />
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-title section-title-with-actions">
          <span>交付计划</span>
          <el-button
            v-if="canManageDeliveryPlans"
            data-test="adjust-sales-delivery-plans"
            size="small"
            type="primary"
            plain
            :loading="actionLoading"
            @click="openDeliveryPlanAdjust"
          >
            {{ deliveryPlans.length === 0 ? '初始化/拆分交付计划' : '拆分/调整' }}
          </el-button>
        </div>
        <el-alert
          v-if="deliveryPlanError"
          class="state-alert"
          type="error"
          :title="deliveryPlanError"
          :closable="false"
        />
        <el-alert
          v-if="deliveryPlanLoading"
          class="state-alert"
          type="info"
          title="交付计划加载中"
          :closable="false"
        />
        <el-empty v-if="!deliveryPlanLoading && deliveryPlans.length === 0" description="暂无交付计划" />
        <div v-else class="table-scroll">
          <el-table :data="deliveryPlans" empty-text="暂无交付计划" stripe>
            <el-table-column prop="planNo" label="计划号" min-width="140" show-overflow-tooltip />
            <el-table-column label="物料" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.materialCode }} {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column label="承诺日期" min-width="110">
              <template #default="{ row }">
                {{ deliveryPlanDate(row) }}
              </template>
            </el-table-column>
            <el-table-column label="计划/已发/剩余" min-width="140" align="right">
              <template #default="{ row }">
                <span class="numeric-cell">{{ deliveryPlanQuantitySummary(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                {{ deliveryPlanStatusLabel(row.status) }}
              </template>
            </el-table-column>
            <el-table-column prop="closeReason" label="关闭原因" min-width="160" show-overflow-tooltip />
            <el-table-column v-if="authStore.hasPermission('sales:delivery-plan:manage')" label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canManageDeliveryPlans && (row.allowedActions ?? []).includes('CLOSE')"
                  :data-test="`close-sales-delivery-plan-${row.id}`"
                  size="small"
                  text
                  type="warning"
                  :disabled="actionLoading"
                  @click="openCloseDeliveryPlan(row)"
                >
                  关闭计划
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-title section-title-with-actions">
          <span>订单变更</span>
          <el-button
            v-if="canCreateOrderChange"
            data-test="create-sales-order-change"
            size="small"
            type="primary"
            plain
            :loading="actionLoading"
            @click="openCreateOrderChange"
          >
            新建变更
          </el-button>
        </div>
        <el-alert
          v-if="orderChangeError"
          class="state-alert"
          type="error"
          :title="orderChangeError"
          :closable="false"
        />
        <el-alert
          v-if="orderChangeLoading"
          class="state-alert"
          type="info"
          title="订单变更加载中"
          :closable="false"
        />
        <el-empty v-if="!orderChangeLoading && orderChanges.length === 0" description="暂无订单变更" />
        <div v-else class="table-scroll">
          <el-table :data="orderChanges" empty-text="暂无订单变更" stripe>
            <el-table-column prop="changeNo" label="变更单号" min-width="150" show-overflow-tooltip />
            <el-table-column label="业务状态" min-width="100">
              <template #default="{ row }">
                {{ orderChangeStatusLabel(row.status) }}
              </template>
            </el-table-column>
            <el-table-column label="审批状态" min-width="100">
              <template #default="{ row }">
                {{ approvalStatusLabel(row.approvalStatus) }}
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="变更原因" min-width="220" show-overflow-tooltip />
            <el-table-column prop="actionDisabledReason" label="动作限制" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="orderChangeAllowed(row, 'UPDATE')"
                  :data-test="`edit-sales-order-change-${row.id}`"
                  size="small"
                  text
                  :disabled="actionLoading"
                  @click="openEditOrderChange(row)"
                >
                  编辑变更
                </el-button>
                <el-button
                  v-if="orderChangeAllowed(row, 'SUBMIT_APPROVAL')"
                  :data-test="`submit-sales-order-change-${row.id}`"
                  size="small"
                  text
                  type="success"
                  :disabled="actionLoading"
                  @click="submitOrderChange(row)"
                >
                  提交
                </el-button>
                <el-button
                  v-if="orderChangeAllowed(row, 'CANCEL')"
                  :data-test="`cancel-sales-order-change-${row.id}`"
                  size="small"
                  text
                  type="danger"
                  :disabled="actionLoading"
                  @click="cancelOrderChange(row)"
                >
                  取消变更
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-title">出库记录</div>
        <el-empty v-if="(record.shipments ?? []).length === 0" description="暂无出库记录" />
        <div v-else class="table-scroll">
          <el-table :data="record.shipments ?? []" empty-text="暂无出库记录" stripe>
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

      <el-dialog v-model="orderChangeDialog.visible" title="订单变更" :teleported="false" destroy-on-close width="620px">
        <template v-if="orderChangeDialog.visible">
          <el-alert
            v-if="orderChangeDialog.error"
            class="state-alert"
            type="error"
            :title="orderChangeDialog.error"
            :closable="false"
          />
          <el-input
            v-model="orderChangeDialog.reason"
            name="sales-order-change-reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请输入 1-200 字变更原因"
          />
          <div class="dialog-lines">
            <label v-for="line in record.lines" :key="line.id">
              {{ line.materialCode }} {{ line.materialName }} / 当前 {{ formatSalesQuantity(line.quantity) }}
              <input
                v-model="orderChangeDialog.targets[String(line.id)].targetQuantity"
                :name="`sales-order-change-target-${line.id}`"
                placeholder="目标数量，可留空"
              />
              <input
                v-model="orderChangeDialog.targets[String(line.id)].untaxedUnitPrice"
                :name="`sales-order-change-untaxed-price-${line.id}`"
                placeholder="目标未税单价，可留空"
              />
              <input
                v-model="orderChangeDialog.targets[String(line.id)].taxIncludedUnitPrice"
                :name="`sales-order-change-tax-included-price-${line.id}`"
                placeholder="目标含税单价，可留空"
              />
              <input
                v-model="orderChangeDialog.targets[String(line.id)].taxRate"
                :name="`sales-order-change-tax-rate-${line.id}`"
                placeholder="目标税率，可留空"
              />
              <input
                v-model="orderChangeDialog.targets[String(line.id)].plannedDate"
                :name="`sales-order-change-planned-date-${line.id}`"
                placeholder="目标交期，可留空"
              />
            </label>
          </div>
        </template>
        <template #footer>
          <template v-if="orderChangeDialog.visible">
            <el-button @click="orderChangeDialog.visible = false">取消</el-button>
            <el-button
              data-test="confirm-sales-order-change"
              type="primary"
              :loading="actionLoading"
              @click="confirmOrderChange"
            >
              确认
            </el-button>
          </template>
        </template>
      </el-dialog>

      <el-dialog v-model="deliveryPlanDialog.visible" title="拆分/调整交付计划" :teleported="false" width="680px">
        <el-alert
          v-if="deliveryPlanDialog.error"
          class="state-alert"
          type="error"
          :title="deliveryPlanDialog.error"
          :closable="false"
        />
        <div class="dialog-lines">
          <label v-for="line in record.lines" :key="line.id">
            {{ line.materialCode }} {{ line.materialName }} / 未出 {{ formatSalesQuantity(line.remainingQuantity) }}
            <input
              v-model="deliveryPlanDialog.lines[String(line.id)].planDate"
              :name="`sales-delivery-plan-date-${line.id}`"
              placeholder="计划日期"
            />
            <input
              v-model="deliveryPlanDialog.lines[String(line.id)].quantity"
              :name="`sales-delivery-plan-quantity-${line.id}`"
              placeholder="计划数量"
            />
          </label>
        </div>
        <template #footer>
          <el-button @click="deliveryPlanDialog.visible = false">取消</el-button>
          <el-button
            data-test="confirm-sales-delivery-plan-adjust"
            type="primary"
            :loading="actionLoading"
            @click="confirmDeliveryPlanAdjust"
          >
            确认
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="closePlanDialog.visible" title="关闭交付计划" :teleported="false" width="420px">
        <el-alert
          v-if="closePlanDialog.error"
          class="state-alert"
          type="error"
          :title="closePlanDialog.error"
          :closable="false"
        />
        <el-input
          v-model="closePlanDialog.reason"
          name="sales-delivery-plan-close-reason"
          type="textarea"
          :rows="4"
          maxlength="200"
          show-word-limit
          placeholder="请输入 1-200 字关闭原因"
        />
        <template #footer>
          <el-button @click="closePlanDialog.visible = false">取消</el-button>
          <el-button
            data-test="confirm-sales-delivery-plan-close"
            type="warning"
            :loading="actionLoading"
            @click="confirmCloseDeliveryPlan"
          >
            确认
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="shortCloseDialog.visible" title="提交短交关闭审批" :teleported="false" width="420px">
        <el-alert
          v-if="shortCloseDialog.error"
          class="state-alert"
          type="error"
          :title="shortCloseDialog.error"
          :closable="false"
        />
        <el-input
          v-model="shortCloseDialog.reason"
          name="sales-order-short-close-reason"
          type="textarea"
          :rows="4"
          maxlength="200"
          show-word-limit
          placeholder="请输入 1-200 字短交关闭原因"
        />
        <template #footer>
          <el-button @click="shortCloseDialog.visible = false">取消</el-button>
          <el-button
            data-test="confirm-sales-order-short-close"
            type="warning"
            :loading="actionLoading"
            @click="submitShortCloseApproval"
          >
            确认
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="creditOverrideDialog.visible" title="提交信用例外审批" :teleported="false" width="420px">
        <el-alert
          v-if="creditOverrideDialog.error"
          class="state-alert"
          type="error"
          :title="creditOverrideDialog.error"
          :closable="false"
        />
        <el-input
          v-model="creditOverrideDialog.reason"
          name="sales-order-credit-override-reason"
          type="textarea"
          :rows="4"
          maxlength="200"
          show-word-limit
          placeholder="请输入 1-200 字信用例外原因"
        />
        <template #footer>
          <el-button @click="creditOverrideDialog.visible = false">取消</el-button>
          <el-button
            data-test="confirm-sales-order-credit-override"
            type="warning"
            :loading="actionLoading"
            @click="submitCreditOverrideApproval"
          >
            确认
          </el-button>
        </template>
      </el-dialog>
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

.section-title-with-actions {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.dialog-lines {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.dialog-lines label {
  display: grid;
  gap: 6px;
}

.dialog-lines input {
  border: 1px solid var(--qherp-border);
  border-radius: 4px;
  min-height: 32px;
  padding: 0 10px;
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
