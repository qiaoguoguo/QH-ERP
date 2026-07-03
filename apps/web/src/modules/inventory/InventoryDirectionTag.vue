<script setup lang="ts">
import type { InventoryDirection } from '../../shared/api/inventoryApi'
import { directionLabel, directionTagType } from './inventoryPageHelpers'

defineProps<{
  direction: InventoryDirection
}>()
</script>

<template>
  <el-tag :type="directionTagType(direction)" size="small">
    {{ directionLabel(direction) }}
  </el-tag>
</template>
