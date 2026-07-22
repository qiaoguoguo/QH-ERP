<script setup lang="ts">
import type { InventoryQualityStatus } from '../../shared/api/qualityInventoryStatusApi'
import { qualityStatusLabel, qualityStatusTagType } from '../inventory/inventoryPageHelpers'

const props = defineProps<{
  qualityStatus?: InventoryQualityStatus | string | null
  qualityStatusName?: string | null
}>()

</script>

<template>
  <el-tag size="small" :type="qualityStatusTagType(props.qualityStatus)">
    {{ qualityStatusLabel(props.qualityStatus, props.qualityStatusName) }}
  </el-tag>
</template>
