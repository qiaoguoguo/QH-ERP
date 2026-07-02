<script setup lang="ts">
import type { BomStatus } from '../../../shared/api/bomApi'
import { bomStatusLabel, bomStatusTagType } from './bomPageHelpers'

defineProps<{
  status: BomStatus
}>()
</script>

<template>
  <el-tag :type="bomStatusTagType(status)" size="small">
    {{ bomStatusLabel(status) }}
  </el-tag>
</template>
