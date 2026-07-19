<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { BusinessPeriodCloseCheckItem } from '../../shared/api/businessPeriodCloseApi'
import { currentRouteReturnTo } from '../../shared/navigation/navigationReturn'
import {
  periodCloseDomainLabel,
  restrictedSourceReason,
  sourceRouteLocation,
} from './periodClosePageHelpers'

const props = defineProps<{
  modelValue: boolean
  item: BusinessPeriodCloseCheckItem | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const route = useRoute()
const router = useRouter()
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const sourceRestricted = computed(() => restrictedSourceReason(props.item))
const sourceLocation = computed(() => sourceRouteLocation(props.item?.sourceRoute, currentRouteReturnTo(route)))

function goSource() {
  if (!sourceLocation.value) {
    return
  }
  void router.push(sourceLocation.value)
  visible.value = false
}
</script>

<template>
  <el-drawer v-model="visible" title="来源追溯" size="520px" class="period-close-source-drawer">
    <el-empty v-if="!item" description="未选择来源" />
    <div v-else class="period-close-source-content">
      <el-alert
        v-if="sourceRestricted"
        class="state-alert"
        type="warning"
        :title="sourceRestricted"
        :closable="false"
      />
      <dl class="period-close-detail-list">
        <div>
          <dt>检查领域</dt>
          <dd>{{ periodCloseDomainLabel(item.domain) }}</dd>
        </div>
        <div>
          <dt>检查项</dt>
          <dd>{{ item.title }}</dd>
        </div>
        <div>
          <dt>业务对象</dt>
          <dd>{{ sourceRestricted || item.objectNo || item.objectType || '受限来源摘要' }}</dd>
        </div>
        <div>
          <dt>影响口径</dt>
          <dd>{{ item.businessImpact || '-' }}</dd>
        </div>
        <div>
          <dt>处理建议</dt>
          <dd>{{ item.suggestion || '-' }}</dd>
        </div>
      </dl>
      <div class="period-close-drawer-actions">
        <el-button @click="visible = false">关闭</el-button>
        <el-button
          v-if="sourceLocation && item.sourceVisible !== false"
          data-test="period-close-source-route"
          type="primary"
          @click="goSource"
        >
          查看来源
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>
