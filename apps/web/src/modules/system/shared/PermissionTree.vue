<script setup lang="ts">
import { computed } from 'vue'
import type { PermissionNode } from '../../../shared/api/accountPermissionApi'

const props = defineProps<{
  nodes: PermissionNode[]
  modelValue: Array<string | number>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Array<string | number>]
}>()

const selected = computed(() => new Set(props.modelValue))

function descendants(node: PermissionNode): Array<string | number> {
  return [node.id, ...(node.children ?? []).flatMap((child) => descendants(child))]
}

function isChecked(node: PermissionNode) {
  const ids = descendants(node)
  return ids.length > 0 && ids.every((id) => selected.value.has(id))
}

function isIndeterminate(node: PermissionNode) {
  const ids = descendants(node)
  const checkedCount = ids.filter((id) => selected.value.has(id)).length
  return checkedCount > 0 && checkedCount < ids.length
}

function toggle(node: PermissionNode, checked: boolean) {
  const next = new Set(selected.value)
  descendants(node).forEach((id) => {
    if (checked) {
      next.add(id)
    } else {
      next.delete(id)
    }
  })
  emit('update:modelValue', Array.from(next))
}
</script>

<template>
  <div class="permission-tree">
    <ul class="permission-tree__list">
      <li v-for="node in nodes" :key="node.id" class="permission-tree__node">
        <label class="permission-tree__row">
          <input
            :data-test="`permission-checkbox-${node.id}`"
            type="checkbox"
            :checked="isChecked(node)"
            :indeterminate.prop="isIndeterminate(node)"
            @change="toggle(node, ($event.target as HTMLInputElement).checked)"
          />
          <span class="permission-tree__name">{{ node.name }}</span>
          <span class="permission-tree__code">{{ node.code }}</span>
        </label>
        <PermissionTree
          v-if="node.children?.length"
          class="permission-tree__children"
          :nodes="node.children"
          :model-value="modelValue"
          @update:model-value="emit('update:modelValue', $event)"
        />
      </li>
    </ul>
  </div>
</template>
