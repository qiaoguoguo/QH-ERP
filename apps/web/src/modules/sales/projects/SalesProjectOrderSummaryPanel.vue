<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  salesProjectApi,
  type ProjectSalesOrderAggregate,
  type ProjectSalesOrderSummary,
} from '../../../shared/api/salesProjectApi'
import type { ResourceId, SalesOrderStatus } from '../../../shared/api/salesApi'
import { pageItems } from '../../system/shared/pageHelpers'
import SalesOrderStatusTag from '../SalesOrderStatusTag.vue'
import { formatProjectAmount, projectApiErrorMessage } from './salesProjectPageHelpers'

const props = defineProps<{
  projectId: ResourceId
  canViewDetails: boolean
  restricted: boolean
  summary: ProjectSalesOrderAggregate | null
}>()

const router = useRouter()
const loading = ref(false)
const error = ref('')
const orders = ref<ProjectSalesOrderSummary[]>([])
const pagination = reactive({ page: 1, pageSize: 5, total: 0 })

const statusLabels: Record<SalesOrderStatus, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  PARTIALLY_SHIPPED: '部分出库',
  SHIPPED: '全部出库',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

async function loadOrders() {
  if (props.restricted || !props.canViewDetails || !props.summary) {
    orders.value = []
    pagination.total = 0
    return
  }
  loading.value = true
  error.value = ''
  try {
    const page = await salesProjectApi.projectSalesOrders(props.projectId, {
      keyword: '',
      contractId: undefined,
      status: undefined,
      dateFrom: '',
      dateTo: '',
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    orders.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    orders.value = []
    pagination.total = 0
    error.value = projectApiErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function viewOrder(order: ProjectSalesOrderSummary) {
  void router.push({ name: 'sales-order-detail', params: { id: String(order.id) } })
}

onMounted(loadOrders)

watch(() => [props.projectId, props.restricted, props.canViewDetails, props.summary], () => {
  pagination.page = 1
  void loadOrders()
})
</script>

<template>
  <section class="section-block">
    <div class="section-title">关联销售订单</div>
    <el-alert v-if="restricted" type="warning" title="订单摘要受限" :closable="false" />
    <template v-else>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-else-if="loading" class="state-alert" type="info" title="关联销售订单加载中" :closable="false" />
      <el-empty v-else-if="!summary" description="暂无关联销售订单" />
      <div v-else class="project-order-summary">
        <span>订单数量：{{ summary.orderCount }}</span>
        <span>业务金额：{{ formatProjectAmount(summary.businessAmount) }}</span>
        <span>最近订单：{{ summary.latestOrderDate || '-' }}</span>
      </div>
      <div v-if="summary" class="project-order-status-grid">
        <span>草稿：{{ summary.draftCount }}</span>
        <span>已确认：{{ summary.confirmedCount }}</span>
        <span>部分出库：{{ summary.partiallyShippedCount }}</span>
        <span>全部出库：{{ summary.shippedCount }}</span>
        <span>已关闭：{{ summary.closedCount }}</span>
        <span>已取消：{{ summary.cancelledCount }}</span>
      </div>
      <div v-if="summary && orders.length > 0" class="table-scroll project-order-table-scroll">
        <el-table :data="orders" empty-text="暂无关联销售订单" stripe>
          <el-table-column prop="orderNo" label="订单编号" min-width="160" show-overflow-tooltip />
          <el-table-column prop="contractNo" label="合同编号" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <SalesOrderStatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="状态摘要" min-width="100">
            <template #default="{ row }">{{ statusLabels[row.status as SalesOrderStatus] }}</template>
          </el-table-column>
          <el-table-column prop="orderDate" label="订单日期" min-width="110" />
          <el-table-column label="数量" min-width="110" align="right">
            <template #default="{ row }">{{ row.totalQuantity }}</template>
          </el-table-column>
          <el-table-column label="业务金额" min-width="120" align="right">
            <template #default="{ row }">{{ formatProjectAmount(row.businessAmount) }}</template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="90">
            <template #default="{ row }">
              <el-button v-if="canViewDetails" data-test="view-project-sales-order" size="small" text @click="viewOrder(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.project-order-summary,
.project-order-status-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-bottom: 10px;
  overflow-wrap: anywhere;
}

.project-order-status-grid span {
  color: var(--qherp-muted);
}
</style>
