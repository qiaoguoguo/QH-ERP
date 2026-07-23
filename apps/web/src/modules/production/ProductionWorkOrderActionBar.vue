<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  actionLoading: boolean
  canEdit: boolean
  canRelease: boolean
  canCreateIssue: boolean
  canCreateReport: boolean
  canCreateReceipt: boolean
  canComplete: boolean
  canCancel: boolean
}>()

const emit = defineEmits<{
  back: []
  edit: []
  release: []
  createIssue: []
  createReport: []
  createReceipt: []
  complete: []
  cancel: []
}>()

type ActionKey = 'edit' | 'release' | 'createIssue' | 'createReport' | 'createReceipt' | 'complete' | 'cancel'

interface ActionItem {
  key: ActionKey
  label: string
  visible: boolean
  type?: 'primary' | 'success' | 'danger'
  plain?: boolean
  disabled?: boolean
  loading?: boolean
  legacyTestId?: string
}

const actions = computed<ActionItem[]>(() => [
  {
    key: 'edit',
    label: '编辑',
    visible: props.canEdit,
    type: 'primary',
    legacyTestId: 'edit-production-work-order-detail',
  },
  {
    key: 'release',
    label: '发布',
    visible: props.canRelease,
    type: 'success',
    loading: props.actionLoading,
    disabled: props.actionLoading,
  },
  {
    key: 'createIssue',
    label: '领料',
    visible: props.canCreateIssue,
  },
  {
    key: 'createReport',
    label: '报工',
    visible: props.canCreateReport,
  },
  {
    key: 'createReceipt',
    label: '完工入库',
    visible: props.canCreateReceipt,
  },
  {
    key: 'complete',
    label: '完成',
    visible: props.canComplete,
    type: 'success',
    loading: props.actionLoading,
    disabled: props.actionLoading,
  },
  {
    key: 'cancel',
    label: '取消',
    visible: props.canCancel,
    type: 'danger',
    plain: true,
    loading: props.actionLoading,
    disabled: props.actionLoading,
  },
])

const primaryActionPriority: ActionKey[] = [
  'release',
  'createIssue',
  'createReport',
  'createReceipt',
  'complete',
  'edit',
  'cancel',
]

const visibleActions = computed(() => actions.value.filter((action) => action.visible))
const primaryAction = computed(() => (
  primaryActionPriority
    .map((key) => visibleActions.value.find((action) => action.key === key))
    .find((action): action is ActionItem => Boolean(action))
))
const directActions = computed(() => primaryAction.value ? [primaryAction.value] : [])
const moreActions = computed(() => (
  visibleActions.value.filter((action) => action.key !== primaryAction.value?.key)
))

function emitAction(action: ActionItem) {
  if (action.disabled) {
    return
  }
  switch (action.key) {
    case 'edit':
      emit('edit')
      break
    case 'release':
      emit('release')
      break
    case 'createIssue':
      emit('createIssue')
      break
    case 'createReport':
      emit('createReport')
      break
    case 'createReceipt':
      emit('createReceipt')
      break
    case 'complete':
      emit('complete')
      break
    case 'cancel':
      emit('cancel')
      break
  }
}
</script>

<template>
  <div class="production-work-order-action-bar" data-test="production-work-order-action-bar">
    <el-button
      data-test="back-production-work-order-list"
      data-action-test="production-work-order-direct-action-back"
      @click="emit('back')"
    >
      返回列表
    </el-button>
    <el-button
      v-for="action in directActions"
      :key="action.key"
      :data-test="action.legacyTestId"
      :data-action-test="`production-work-order-direct-action-${action.key}`"
      :type="action.type"
      :plain="action.plain"
      :loading="action.loading"
      :disabled="action.disabled"
      @click="emitAction(action)"
    >
      {{ action.label }}
    </el-button>
    <el-dropdown v-if="moreActions.length" trigger="click" class="table-actions-more">
      <el-button data-test="production-work-order-action-more">
        更多
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="action in moreActions"
            :key="action.key"
            :data-test="action.legacyTestId"
            :data-action-test="`production-work-order-more-action-${action.key}`"
            :disabled="action.disabled"
            :class="{ 'is-danger-action': action.type === 'danger' }"
            @click="emitAction(action)"
          >
            {{ action.label }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped>
.production-work-order-action-bar {
  align-items: center;
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
}

.production-work-order-action-bar :deep(.el-button + .el-button) {
  margin-left: 0;
}

.is-danger-action {
  color: var(--el-color-danger);
}
</style>
