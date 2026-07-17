<script setup lang="ts">
import type { ProjectProductionTraceLink } from '../../shared/api/projectProductionApi'

defineProps<{
  links?: ProjectProductionTraceLink[]
}>()

function traceTarget(link: ProjectProductionTraceLink) {
  if (link.restricted) {
    return null
  }
  if (link.routeName) {
    return link.targetId === undefined || link.targetId === null
      ? { name: link.routeName }
      : { name: link.routeName, params: { id: String(link.targetId) } }
  }
  return link.routePath || null
}
</script>

<template>
  <section v-if="links?.length" class="section-block">
    <h2>追溯链接</h2>
    <div class="trace-link-list">
      <span v-for="link in links" :key="`${link.label}-${link.targetId || link.routeName || link.routePath}`">
        <router-link
          v-if="traceTarget(link)"
          data-test="production-work-order-trace-link"
          class="trace-link"
          :to="traceTarget(link)!"
        >
          {{ link.label }}
        </router-link>
        <span v-else class="trace-link-restricted">
          {{ link.label }}
          <span v-if="link.restrictedReason"> {{ link.restrictedReason }}</span>
        </span>
      </span>
    </div>
  </section>
</template>

<style scoped>
.trace-link-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.trace-link {
  color: var(--el-color-primary);
  font-size: 13px;
  text-decoration: none;
}

.trace-link-restricted {
  color: var(--qherp-muted);
  font-size: 13px;
}
</style>
