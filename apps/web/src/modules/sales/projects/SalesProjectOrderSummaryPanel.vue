<script setup lang="ts">
import type { ProjectSalesOrderAggregate } from '../../../shared/api/salesProjectApi'
import { formatProjectAmount } from './salesProjectPageHelpers'

defineProps<{
  restricted: boolean
  summary: ProjectSalesOrderAggregate | null
}>()
</script>

<template>
  <section class="section-block">
    <div class="section-title">关联销售订单</div>
    <el-alert v-if="restricted" type="warning" title="订单摘要受限" :closable="false" />
    <el-empty v-else-if="!summary" description="暂无关联销售订单" />
    <div v-else class="project-order-summary">
      <span>订单数量：{{ summary.orderCount }}</span>
      <span>业务金额：{{ formatProjectAmount(summary.businessAmount) }}</span>
      <span>最近订单：{{ summary.latestOrderDate || '-' }}</span>
    </div>
  </section>
</template>
