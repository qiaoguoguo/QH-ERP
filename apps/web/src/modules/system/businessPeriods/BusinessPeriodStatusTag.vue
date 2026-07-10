<script setup lang="ts">
import type { BusinessPeriodStatus } from '../../../shared/api/businessPeriodApi'

const props = defineProps<{
  status: BusinessPeriodStatus
  statusName?: string
}>()

const labels: Record<BusinessPeriodStatus, string> = {
  OPEN: '开放',
  LOCKED: '已锁定',
}

const tagTypes: Record<BusinessPeriodStatus, 'success' | 'warning'> = {
  OPEN: 'success',
  LOCKED: 'warning',
}
</script>

<template>
  <el-tag :type="tagTypes[props.status]" size="small">
    {{ props.statusName || labels[props.status] }}
  </el-tag>
</template>
