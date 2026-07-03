<script setup lang="ts">
import type { CostType } from '../../shared/api/costCollectionApi'
import { costTypeLabel, costTypeTagType } from './costPageHelpers'

defineProps<{
  type: CostType
}>()
</script>

<template>
  <el-tag size="small" :type="costTypeTagType(type)" effect="light">
    {{ costTypeLabel(type) }}
  </el-tag>
</template>
