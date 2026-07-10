<script setup lang="ts">
import type { InventoryQualityStatus } from '../../shared/api/qualityInventoryStatusApi'

const props = defineProps<{
  qualityStatus?: InventoryQualityStatus | string | null
  qualityStatusName?: string | null
}>()

const labels: Record<string, string> = {
  PENDING_INSPECTION: '待检',
  QUALIFIED: '合格',
  REJECTED: '不合格',
  FROZEN: '冻结',
}

const types: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  PENDING_INSPECTION: 'warning',
  QUALIFIED: 'success',
  REJECTED: 'danger',
  FROZEN: 'info',
}
</script>

<template>
  <el-tag size="small" :type="types[String(props.qualityStatus ?? '')] ?? 'info'">
    {{ props.qualityStatusName || labels[String(props.qualityStatus ?? '')] || props.qualityStatus || '-' }}
  </el-tag>
</template>
