<script setup lang="ts">
import type { ProjectProductionWorkOrderDetailRecord } from '../../shared/api/projectProductionApi'
import { formatProductionQuantity } from './productionPageHelpers'

defineProps<{
  record: ProjectProductionWorkOrderDetailRecord
}>()
</script>

<template>
  <section v-if="record.executionSummary" class="section-block">
    <h2>执行摘要</h2>
    <section class="summary-strip">
      <div>
        <span>已领料</span>
        <strong>{{ formatProductionQuantity(record.executionSummary.issuedQuantity) }}</strong>
      </div>
      <div>
        <span>已退料</span>
        <strong>{{ formatProductionQuantity(record.executionSummary.returnedQuantity) }}</strong>
      </div>
      <div>
        <span>已补料</span>
        <strong>{{ formatProductionQuantity(record.executionSummary.supplementedQuantity) }}</strong>
      </div>
      <div>
        <span>完工进度</span>
        <strong>{{ record.executionSummary.progressPercent || '-' }}</strong>
      </div>
    </section>
  </section>
</template>
