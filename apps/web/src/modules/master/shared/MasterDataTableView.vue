<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <section class="module-page">
    <header class="page-heading">
      <div>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="page-actions">
        <slot name="actions" />
      </div>
    </header>

    <el-card v-if="$slots.filters" class="query-card" shadow="never">
      <slot name="filters" />
    </el-card>

    <div v-if="$slots.alerts" class="master-alerts">
      <slot name="alerts" />
    </div>

    <el-card class="table-card" shadow="never">
      <slot />
    </el-card>
  </section>
</template>
