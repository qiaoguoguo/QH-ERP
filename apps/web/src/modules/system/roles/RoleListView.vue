<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { accountPermissionApi, type RoleRecord, type RoleStatus } from '../../../shared/api/accountPermissionApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusLabel, statusTagType } from '../shared/pageHelpers'

const router = useRouter()
const authStore = useAuthStore()

const filters = reactive<{
  keyword: string
  status?: RoleStatus
}>({
  keyword: '',
  status: undefined,
})
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})
const roles = ref<RoleRecord[]>([])
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const editingRole = ref<RoleRecord | null>(null)
const form = reactive({
  code: '',
  name: '',
  description: '',
  status: 'ENABLED' as RoleStatus,
})
const formErrors = reactive({
  code: '',
  name: '',
  submit: '',
})

const canCreate = computed(() => authStore.hasPermission('system:role:create'))
const canUpdate = computed(() => authStore.hasPermission('system:role:update'))
const canAssignPermission = computed(() => authStore.hasPermission('system:role:assign-permission'))

async function loadRoles() {
  loading.value = true
  error.value = ''
  try {
    const page = await accountPermissionApi.roles.list({
      keyword: filters.keyword,
      status: filters.status,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    roles.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    roles.value = []
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  void loadRoles()
}

function resetSearch() {
  filters.keyword = ''
  filters.status = undefined
  pagination.page = 1
  void loadRoles()
}

function changePage(page: number) {
  pagination.page = page
  void loadRoles()
}

function resetForm() {
  Object.assign(form, {
    code: '',
    name: '',
    description: '',
    status: 'ENABLED',
  })
  Object.assign(formErrors, {
    code: '',
    name: '',
    submit: '',
  })
}

function openCreate() {
  editingRole.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(role: RoleRecord) {
  editingRole.value = role
  resetForm()
  Object.assign(form, {
    code: role.code,
    name: role.name,
    description: role.description ?? '',
    status: role.status ?? 'ENABLED',
  })
  formVisible.value = true
}

function validateForm() {
  formErrors.code = form.code.trim() ? '' : '请输入角色编码'
  formErrors.name = form.name.trim() ? '' : '请输入角色名称'
  return !formErrors.code && !formErrors.name
}

async function saveRole() {
  formErrors.submit = ''
  if (!validateForm()) {
    return
  }

  try {
    if (editingRole.value) {
      await accountPermissionApi.roles.update(editingRole.value.id, {
        name: form.name,
        description: form.description,
        status: form.status,
      })
    } else {
      await accountPermissionApi.roles.create({
        code: form.code,
        name: form.name,
        description: form.description,
        status: form.status,
      })
    }
    formVisible.value = false
    await loadRoles()
  } catch (caught) {
    formErrors.submit = errorMessage(caught)
  }
}

async function changeStatus(role: RoleRecord) {
  const nextAction = role.status === 'DISABLED' ? '启用' : '停用'
  if (!window.confirm(`确认${nextAction}角色“${role.name}”？`)) {
    return
  }
  if (role.status === 'DISABLED') {
    await accountPermissionApi.roles.enable(role.id)
  } else {
    await accountPermissionApi.roles.disable(role.id)
  }
  await loadRoles()
}

function configurePermissions(role: RoleRecord) {
  void router.push({ name: 'system-role-permissions', params: { id: String(role.id) } })
}

onMounted(loadRoles)
</script>

<template>
  <section class="module-page">
    <header class="page-heading">
      <div>
        <h1>角色管理</h1>
        <p>维护角色编码、状态和菜单按钮权限入口。</p>
      </div>
      <el-button v-if="canCreate" data-test="create-role" type="primary" @click="openCreate">新增角色</el-button>
    </header>

    <el-card class="query-card" shadow="never">
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="role-keyword" clearable placeholder="编码或名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="role-search" type="primary" @click="search">查询</el-button>
          <el-button data-test="role-reset" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="loading" class="state-alert" type="info" title="角色数据加载中" :closable="false" />

    <el-card class="table-card" shadow="never">
      <div class="table-scroll">
        <el-table :data="roles" :empty-text="loading ? '加载中' : '暂无角色数据'" stripe>
          <el-table-column prop="code" label="角色编码" min-width="150" />
          <el-table-column prop="name" label="角色名称" min-width="140" />
          <el-table-column prop="description" label="说明" min-width="220" />
          <el-table-column label="状态" min-width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" min-width="260">
            <template #default="{ row }">
              <el-button v-if="canUpdate" size="small" text data-test="edit-role" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="canAssignPermission" size="small" text data-test="configure-permission" @click="configurePermissions(row)">
                权限配置
              </el-button>
              <el-button
                v-if="canUpdate"
                size="small"
                text
                :type="row.status === 'DISABLED' ? 'success' : 'danger'"
                :data-test="row.status === 'DISABLED' ? 'enable-role' : 'disable-role'"
                @click="changeStatus(row)"
              >
                {{ row.status === 'DISABLED' ? '启用' : '停用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-pagination
        class="table-pagination"
        layout="total, prev, pager, next"
        :total="pagination.total"
        :page-size="pagination.pageSize"
        :current-page="pagination.page"
        @current-change="changePage"
      />
    </el-card>

    <el-dialog v-model="formVisible" :title="editingRole ? '编辑角色' : '新增角色'" width="560px">
      <el-alert v-if="formErrors.submit" class="form-alert" type="error" :title="formErrors.submit" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="角色编码" :error="formErrors.code">
          <el-input v-model="form.code" :disabled="Boolean(editingRole)" />
          <div v-if="formErrors.code" class="field-error">{{ formErrors.code }}</div>
        </el-form-item>
        <el-form-item label="角色名称" :error="formErrors.name">
          <el-input v-model="form.name" />
          <div v-if="formErrors.name" class="field-error">{{ formErrors.name }}</div>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button data-test="submit-role" type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
