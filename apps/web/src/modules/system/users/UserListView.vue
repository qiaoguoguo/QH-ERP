<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { accountPermissionApi, type RoleRecord, type UserRecord, type UserStatus } from '../../../shared/api/accountPermissionApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusLabel, statusTagType } from '../shared/pageHelpers'

const authStore = useAuthStore()

const filters = reactive<{
  keyword: string
  status?: UserStatus
}>({
  keyword: '',
  status: undefined,
})
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})
const users = ref<UserRecord[]>([])
const roles = ref<RoleRecord[]>([])
const loading = ref(true)
const error = ref('')
const formVisible = ref(false)
const roleDialogVisible = ref(false)
const editingUser = ref<UserRecord | null>(null)
const roleTarget = ref<UserRecord | null>(null)
const form = reactive({
  username: '',
  displayName: '',
  phone: '',
  email: '',
  initialPassword: 'Qherp@2026!',
  status: 'ENABLED' as UserStatus,
  roleIds: [] as Array<string | number>,
})
const formError = ref('')

const canCreate = computed(() => authStore.hasPermission('system:user:create'))
const canUpdate = computed(() => authStore.hasPermission('system:user:update'))
const canResetPassword = computed(() => authStore.hasPermission('system:user:reset-password'))

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    const page = await accountPermissionApi.users.list({
      keyword: filters.keyword,
      status: filters.status,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    users.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    users.value = []
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  const page = await accountPermissionApi.roles.list({ page: 1, pageSize: 100 })
  roles.value = pageItems(page)
}

function search() {
  pagination.page = 1
  void loadUsers()
}

function resetSearch() {
  filters.keyword = ''
  filters.status = undefined
  pagination.page = 1
  void loadUsers()
}

function changePage(page: number) {
  pagination.page = page
  void loadUsers()
}

async function openCreate() {
  editingUser.value = null
  Object.assign(form, {
    username: '',
    displayName: '',
    phone: '',
    email: '',
    initialPassword: 'Qherp@2026!',
    status: 'ENABLED',
    roleIds: [],
  })
  formError.value = ''
  await loadRoles()
  formVisible.value = true
}

async function openEdit(user: UserRecord) {
  editingUser.value = user
  Object.assign(form, {
    username: user.username,
    displayName: user.displayName,
    phone: user.phone ?? '',
    email: user.email ?? '',
    initialPassword: 'Qherp@2026!',
    status: user.status,
    roleIds: user.roles?.map((role) => role.id) ?? [],
  })
  formError.value = ''
  await loadRoles()
  formVisible.value = true
}

async function saveUser() {
  formError.value = ''
  if (!form.displayName.trim() || (!editingUser.value && !form.username.trim())) {
    formError.value = '请完整填写账号和显示姓名'
    return
  }

  try {
    if (editingUser.value) {
      await accountPermissionApi.users.update(editingUser.value.id, {
        displayName: form.displayName,
        phone: form.phone,
        email: form.email,
        status: form.status,
        roleIds: form.roleIds,
      })
    } else {
      await accountPermissionApi.users.create({
        username: form.username,
        displayName: form.displayName,
        phone: form.phone,
        email: form.email,
        initialPassword: form.initialPassword,
        status: form.status,
        roleIds: form.roleIds,
      })
    }
    formVisible.value = false
    await loadUsers()
  } catch (caught) {
    formError.value = errorMessage(caught)
  }
}

async function resetPassword(user: UserRecord) {
  if (!window.confirm(`确认重置用户“${user.displayName}”的密码？`)) {
    return
  }
  await accountPermissionApi.users.resetPassword(user.id, { newPassword: 'Qherp@2026!' })
}

async function changeStatus(user: UserRecord) {
  const nextAction = user.status === 'DISABLED' ? '启用' : '停用'
  if (!window.confirm(`确认${nextAction}用户“${user.displayName}”？`)) {
    return
  }
  if (user.status === 'DISABLED') {
    await accountPermissionApi.users.enable(user.id)
  } else {
    await accountPermissionApi.users.disable(user.id)
  }
  await loadUsers()
}

async function openRoleDialog(user: UserRecord) {
  roleTarget.value = user
  form.roleIds = user.roles?.map((role) => role.id) ?? []
  await loadRoles()
  roleDialogVisible.value = true
}

async function saveRoleAssignment() {
  if (!roleTarget.value) {
    return
  }
  await accountPermissionApi.users.update(roleTarget.value.id, {
    displayName: roleTarget.value.displayName,
    phone: roleTarget.value.phone ?? '',
    email: roleTarget.value.email ?? '',
    status: roleTarget.value.status,
    roleIds: form.roleIds,
  })
  roleDialogVisible.value = false
  await loadUsers()
}

onMounted(loadUsers)
</script>

<template>
  <section class="module-page">
    <header class="page-heading">
      <div>
        <h1>用户管理</h1>
        <p>维护内部账号、状态和角色归属。</p>
      </div>
      <el-button v-if="canCreate" data-test="create-user" type="primary" @click="openCreate">新增用户</el-button>
    </header>

    <el-card class="query-card" shadow="never">
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="user-keyword" clearable placeholder="账号或姓名" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="user-search" type="primary" @click="search">查询</el-button>
          <el-button data-test="user-reset" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
    <el-alert v-if="loading" class="state-alert" type="info" title="用户数据加载中" :closable="false" />

    <el-card class="table-card" shadow="never">
      <div class="table-scroll">
        <el-table :data="users" :empty-text="loading ? '加载中' : '暂无用户数据'" stripe>
          <el-table-column prop="username" label="账号" min-width="130" />
          <el-table-column prop="displayName" label="姓名" min-width="120" />
          <el-table-column label="状态" min-width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="180">
            <template #default="{ row }">
              {{ row.roles?.map((role: RoleRecord) => role.name).join('、') || '未分配' }}
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" min-width="130" />
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column label="操作" fixed="right" min-width="280">
            <template #default="{ row }">
              <el-button v-if="canUpdate" size="small" text data-test="edit-user" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="canUpdate" size="small" text data-test="assign-role" @click="openRoleDialog(row)">分配角色</el-button>
              <el-button v-if="canResetPassword" size="small" text data-test="reset-password" @click="resetPassword(row)">重置密码</el-button>
              <el-button
                v-if="canUpdate"
                size="small"
                text
                :type="row.status === 'DISABLED' ? 'success' : 'danger'"
                :data-test="row.status === 'DISABLED' ? 'enable-user' : 'disable-user'"
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

    <el-dialog v-model="formVisible" :title="editingUser ? '编辑用户' : '新增用户'" width="560px">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="登录账号">
          <el-input v-model="form.username" :disabled="Boolean(editingUser)" />
        </el-form-item>
        <el-form-item label="显示姓名">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="初始密码">
          <el-input v-model="form.initialPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="选择角色">
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="分配角色" width="520px">
      <p class="dialog-summary">当前用户：{{ roleTarget?.displayName }}</p>
      <el-select v-model="form.roleIds" multiple placeholder="选择角色" style="width: 100%">
        <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
      </el-select>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoleAssignment">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
