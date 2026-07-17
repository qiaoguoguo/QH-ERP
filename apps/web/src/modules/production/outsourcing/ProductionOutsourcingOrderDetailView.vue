<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createIdempotencyKey } from '../../../shared/api/documentPlatformApi'
import {
  productionOutsourcingApi,
  type OutsourcingOrderDetailRecord,
  type OutsourcingTraceLink,
} from '../../../shared/api/productionOutsourcingApi'
import { confirmAction } from '../../../shared/ui/confirmDialog'
import { useAuthStore } from '../../../stores/authStore'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { productionErrorMessage, formatProductionQuantity } from '../productionPageHelpers'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const record = ref<OutsourcingOrderDetailRecord | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const actionError = ref('')

const canRelease = computed(() => authStore.hasPermission('production:outsourcing:release'))
const canClose = computed(() => authStore.hasPermission('production:outsourcing:close'))
const canCancel = computed(() => authStore.hasPermission('production:outsourcing:cancel'))
const canIssue = computed(() => authStore.hasPermission('production:outsourcing-issue:create'))
const canReceipt = computed(() => authStore.hasPermission('production:outsourcing-receipt:create'))

function hasAction(action: string) {
  return Boolean(record.value?.allowedActions?.includes(action))
}

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await productionOutsourcingApi.orders.get(route.params.id as string)
  } catch (caught) {
    record.value = null
    error.value = productionErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function ownershipText() {
  if (record.value?.ownershipType === 'PROJECT') {
    return [record.value.projectNo, record.value.projectName].filter(Boolean).join(' ') || '项目未返回'
  }
  return '公共/未绑定'
}

function statusText(status?: string | null, statusName?: string | null) {
  return statusName || status || '状态未返回'
}

function canCreateIssueDocument() {
  return canIssue.value && hasAction('ISSUE')
}

function canCreateReceiptDocument() {
  return canReceipt.value && hasAction('RECEIPT')
}

function traceTarget(link: OutsourcingTraceLink) {
  if (link.restricted) {
    return null
  }
  if (link.routeName) {
    return link.targetId === undefined || link.targetId === null
      ? { name: link.routeName }
      : { name: link.routeName, params: { id: String(link.targetId) } }
  }
  return link.routePath || null
}

function openTraceLink(link: OutsourcingTraceLink) {
  const target = traceTarget(link)
  if (!target) {
    return
  }
  void router.push(target)
}

async function runOrderAction(action: 'release' | 'close' | 'cancel') {
  if (!record.value || actionLoading.value) {
    return
  }
  const labels = { release: '发布', close: '关闭', cancel: '取消' }
  if (!(await confirmAction(`确认${labels[action]}外协订单“${record.value.orderNo}”？`))) {
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    const payload = {
      version: record.value.version,
      idempotencyKey: createIdempotencyKey(`production-outsourcing-${action}`),
    }
    if (action === 'release') {
      await productionOutsourcingApi.orders.release(record.value.id, payload)
    } else if (action === 'close') {
      await productionOutsourcingApi.orders.close(record.value.id, payload)
    } else {
      await productionOutsourcingApi.orders.cancel(record.value.id, payload)
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
  <MasterDataTableView title="外协订单详情" description="查看外协项目归属、供应商、来源建议、用料、发料、收货和追溯入口。">
    <template #actions>
      <el-button @click="router.push({ name: 'production-outsourcing-orders' })">返回列表</el-button>
      <el-button
        v-if="record && canRelease"
        data-test="release-outsourcing-order"
        :disabled="!hasAction('RELEASE')"
        :loading="actionLoading"
        @click="runOrderAction('release')"
      >
        发布
      </el-button>
      <el-button
        v-if="record && canClose"
        data-test="close-outsourcing-order"
        :disabled="!hasAction('CLOSE')"
        :loading="actionLoading"
        @click="runOrderAction('close')"
      >
        关闭
      </el-button>
      <el-button
        v-if="record && canCancel"
        data-test="cancel-outsourcing-order"
        type="danger"
        :disabled="!hasAction('CANCEL')"
        :loading="actionLoading"
        @click="runOrderAction('cancel')"
      >
        取消
      </el-button>
      <el-button
        v-if="record && canIssue"
        data-test="create-outsourcing-issue"
        :disabled="!canCreateIssueDocument()"
        :title="canCreateIssueDocument() ? '' : record.actionDisabledReason || '当前外协订单不可发料'"
        @click="router.push({ name: 'production-outsourcing-order-material-issues', params: { id: String(record.id) } })"
      >
        外协发料
      </el-button>
      <el-button
        v-if="record && canReceipt"
        data-test="create-outsourcing-receipt"
        type="primary"
        :disabled="!canCreateReceiptDocument()"
        :title="canCreateReceiptDocument() ? '' : record.actionDisabledReason || '当前外协订单不可收货'"
        @click="router.push({ name: 'production-outsourcing-order-receipts', params: { id: String(record.id) } })"
      >
        外协收货
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" type="info" title="外协订单加载中" :closable="false" />
      <el-alert
        v-if="record?.actionDisabledReason"
        type="warning"
        :title="record.actionDisabledReason"
        :closable="false"
      />
    </template>

    <section v-if="record" class="section-block">
      <div class="detail-title-line">
        <div>
          <h2>{{ record.orderNo }}</h2>
          <p>{{ ownershipText() }} · {{ record.supplierName }}</p>
        </div>
        <el-tag>{{ statusText(record.status, record.statusName) }}</el-tag>
      </div>
      <div class="summary-strip">
        <div><strong>计划数量</strong><span>{{ formatProductionQuantity(record.plannedQuantity) }}</span></div>
        <div><strong>已发数量</strong><span>{{ formatProductionQuantity(record.issuedQuantity) }}</span></div>
        <div><strong>已收数量</strong><span>{{ formatProductionQuantity(record.receivedQuantity) }}</span></div>
        <div><strong>来源建议</strong><span>{{ record.sourceSuggestionNo || record.sourceMrpSuggestionId || '-' }}</span></div>
        <div><strong>BOM</strong><span>{{ [record.bomCode, record.bomVersionCode].filter(Boolean).join(' / ') || '-' }}</span></div>
        <div><strong>成本状态</strong><span>{{ record.costVisible === false ? '成本受限' : '可查看' }}</span></div>
      </div>
      <el-alert
        v-if="record.costVisible === false"
        type="warning"
        :title="record.costRestrictedReason || '成本受限'"
        :closable="false"
        show-icon
      />
    </section>

    <section v-if="record" class="section-block">
      <h2>外协用料</h2>
      <div class="table-scroll">
        <el-table :data="record.materials" empty-text="暂无外协用料" stripe>
          <el-table-column prop="lineNo" label="行号" width="90" />
          <el-table-column label="材料" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
          </el-table-column>
          <el-table-column label="需求/已发" min-width="150" align="right">
            <template #default="{ row }">{{ formatProductionQuantity(row.requiredQuantity) }} / {{ formatProductionQuantity(row.issuedQuantity) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section v-if="record" class="section-block">
      <h2>发料与收货</h2>
      <div class="table-scroll">
        <el-table :data="[...record.materialIssues, ...record.receipts]" empty-text="暂无执行单据" stripe>
          <el-table-column label="单号" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.issueNo || row.receiptNo || row.documentNo }}</template>
          </el-table-column>
          <el-table-column label="状态" min-width="110">
            <template #default="{ row }">{{ statusText(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="businessDate" label="业务日期" min-width="120" />
          <el-table-column prop="lineCount" label="行数" min-width="90" />
        </el-table>
      </div>
    </section>

    <section v-if="record" class="section-block">
      <h2>来源追溯</h2>
      <p v-for="link in record.traceLinks" :key="link.label">
        <el-button
          v-if="traceTarget(link)"
          data-test="outsourcing-trace-link"
          link
          type="primary"
          @click="openTraceLink(link)"
        >
          {{ link.label }}
        </el-button>
        <span v-else>
          {{ link.label }}
          <span v-if="link.restrictedReason" class="operation-muted"> {{ link.restrictedReason }}</span>
        </span>
      </p>
      <p v-if="!record.traceLinks?.length">{{ record.sourceSuggestionNo || '暂无来源建议' }}</p>
    </section>
    <el-empty v-else-if="!loading" description="暂无外协订单详情" />
  </MasterDataTableView>
</template>
