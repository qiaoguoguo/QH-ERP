<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { accountPermissionApi, type PermissionNode, type RoleRecord } from '../../../shared/api/accountPermissionApi'
import { errorMessage } from '../shared/pageHelpers'
import PermissionTree from '../shared/PermissionTree.vue'
import { confirmAction } from '../../../shared/ui/confirmDialog'

const route = useRoute()
const router = useRouter()
const roleId = computed(() => String(route.params.id))

const role = ref<RoleRecord | null>(null)
const permissions = ref<PermissionNode[]>([])
const selectedIds = ref<Array<string | number>>([])
const originalIds = ref<Array<string | number>>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

const selectedCount = computed(() => selectedIds.value.length)
const dirty = computed(() => sortedKey(selectedIds.value) !== sortedKey(originalIds.value))

function sortedKey(values: Array<string | number>) {
  return [...values].map(String).sort().join('|')
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [roleResponse, permissionTree] = await Promise.all([
      accountPermissionApi.roles.get(roleId.value),
      accountPermissionApi.permissions.tree(),
    ])
    role.value = roleResponse
    permissions.value = permissionTree
    selectedIds.value = [...(roleResponse.permissionIds ?? [])]
    originalIds.value = [...selectedIds.value]
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function savePermissions() {
  success.value = ''
  error.value = ''
  saving.value = true
  try {
    const permissionIds = withAncestors(selectedIds.value, permissions.value).sort(
      (left, right) => Number(left) - Number(right),
    )
    await accountPermissionApi.roles.savePermissions(roleId.value, { permissionIds })
    originalIds.value = [...permissionIds]
    selectedIds.value = [...permissionIds]
    success.value = '权限已保存'
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    saving.value = false
  }
}

async function cancel() {
  if (dirty.value && !(await confirmAction('存在未保存的权限修改，确认取消？'))) {
    return
  }
  void router.push({ name: 'system-roles' })
}

function withAncestors(values: Array<string | number>, tree: PermissionNode[]) {
  const parentById = new Map<string, string | number | null | undefined>()
  const collect = (nodes: PermissionNode[]) => {
    nodes.forEach((node) => {
      parentById.set(String(node.id), node.parentId)
      collect(node.children ?? [])
    })
  }
  collect(tree)

  const expanded = new Set<string | number>(values)
  values.forEach((value) => {
    let parent = parentById.get(String(value))
    while (parent !== null && parent !== undefined) {
      expanded.add(parent)
      parent = parentById.get(String(parent))
    }
  })
  return Array.from(expanded)
}

onMounted(loadData)
</script>

<template>
  <section class="module-page">
    <header class="page-heading">
      <div>
        <h1>角色权限配置</h1>
        <p>{{ role ? `${role.name}（${role.code}）` : '加载角色权限信息' }}</p>
      </div>
      <div class="page-actions">
        <el-button data-test="cancel-permissions" @click="cancel">取消</el-button>
        <el-button data-test="save-permissions" type="primary" :loading="saving" @click="savePermissions">保存权限</el-button>
      </div>
    </header>

    <el-alert v-if="loading" class="state-alert" type="info" title="权限数据加载中" :closable="false" />
    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="success" class="state-alert" type="success" :title="success" :closable="false" />

    <el-card class="permission-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>权限树</span>
          <el-tag type="info">已选择 {{ selectedCount }} 个权限点</el-tag>
        </div>
      </template>

      <el-empty v-if="!loading && permissions.length === 0" description="暂无权限数据" />
      <PermissionTree v-else v-model="selectedIds" :nodes="permissions" />
    </el-card>
  </section>
</template>
