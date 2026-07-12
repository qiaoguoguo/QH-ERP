<script setup lang="ts">
import type { SalesProjectOperation } from '../../../shared/api/salesProjectApi'
import { formatProjectDateTime } from './salesProjectPageHelpers'

defineProps<{
  operations: SalesProjectOperation[]
}>()
</script>

<template>
  <section class="section-block">
    <div class="section-title">操作记录</div>
    <el-empty v-if="operations.length === 0" description="暂无操作记录" />
    <el-timeline v-else>
      <el-timeline-item v-for="operation in operations" :key="`${operation.targetType}-${operation.targetId}-${operation.createdAt}`" :timestamp="formatProjectDateTime(operation.createdAt)">
        {{ operation.operatorUsername }} {{ operation.targetSummary }}
      </el-timeline-item>
    </el-timeline>
  </section>
</template>
