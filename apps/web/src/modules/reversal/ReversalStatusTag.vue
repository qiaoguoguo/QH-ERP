<script setup lang="ts">
import type { ReversalStatus } from '../../shared/api/returnRefundReversalApi'

defineProps<{
  status: ReversalStatus
}>()

const statusLabels: Record<ReversalStatus, string> = {
  DRAFT: '草稿',
  POSTED: '已过账',
  CANCELLED: '已取消',
}

const statusTypes: Record<ReversalStatus, 'info' | 'success' | 'danger'> = {
  DRAFT: 'info',
  POSTED: 'success',
  CANCELLED: 'danger',
}
</script>

<template>
  <el-tag :type="statusTypes[status]" size="small">
    {{ statusLabels[status] }}
  </el-tag>
</template>
