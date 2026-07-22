<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { accountPermissionApi, type RoleRecord, type UserRecord, type UserStatus } from '../../../shared/api/accountPermissionApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusLabel, statusTagType } from '../shared/pageHelpers'
import { confirmAction } from '../../../shared/ui/confirmDialog'

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
  pageSize: 10,
  total: 0,
})
const users = ref<UserRecord[]>([])
const roles = ref<RoleRecord[]>([])
const loading = ref(true)
const error = ref('')
const formVisible = ref(false)
const roleDialogVisible = ref(false)
const resetPasswordVisible = ref(false)
const editingUser = ref<UserRecord | null>(null)
const roleTarget = ref<UserRecord | null>(null)
const resetPasswordTarget = ref<UserRecord | null>(null)
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
const actionError = ref('')
const roleDialogError = ref('')
const resetPasswordError = ref('')
const actionLoading = ref(false)
const formSubmitting = ref(false)
const resetPasswordForm = reactive({
  newPassword: '',
  confirmPassword: '',
})

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

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadUsers()
}

function normalizeRoleIds(roleIds: Array<string | number>) {
  return roleIds.map((roleId) => {
    if (typeof roleId === 'string' && /^\d+$/.test(roleId)) {
      return Number(roleId)
    }
    return roleId
  })
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
  if (formSubmitting.value) {
    return
  }
  formError.value = ''
  if (!form.displayName.trim() || (!editingUser.value && !form.username.trim())) {
    formError.value = '请完整填写账号和显示姓名'
    return
  }

  const roleIds = normalizeRoleIds(form.roleIds)
  formSubmitting.value = true
  try {
    if (editingUser.value) {
      await accountPermissionApi.users.update(editingUser.value.id, {
        displayName: form.displayName,
        phone: form.phone,
        email: form.email,
        status: form.status,
        roleIds,
      })
    } else {
      await accountPermissionApi.users.create({
        username: form.username,
        displayName: form.displayName,
        phone: form.phone,
        email: form.email,
        initialPassword: form.initialPassword,
        status: form.status,
        roleIds,
      })
    }
    formVisible.value = false
    await loadUsers()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

async function resetPassword(user: UserRecord) {
  resetPasswordTarget.value = user
  resetPasswordForm.newPassword = ''
  resetPasswordForm.confirmPassword = ''
  resetPasswordError.value = ''
  resetPasswordVisible.value = true
}

function validatePassword() {
  if (
    resetPasswordForm.newPassword.length < 8 ||
    !/[A-Z]/.test(resetPasswordForm.newPassword) ||
    !/[a-z]/.test(resetPasswordForm.newPassword) ||
    !/\d/.test(resetPasswordForm.newPassword) ||
    !/[^A-Za-z0-9]/.test(resetPasswordForm.newPassword)
  ) {
    resetPasswordError.value = '密码至少 8 位，并包含大小写字母、数字和特殊字符'
    return false
  }
  if (resetPasswordForm.newPassword !== resetPasswordForm.confirmPassword) {
    resetPasswordError.value = '两次输入的新密码不一致'
    return false
  }
  resetPasswordError.value = ''
  return true
}

async function submitResetPassword() {
  if (!resetPasswordTarget.value || !validatePassword()) {
    return
  }
  actionLoading.value = true
  try {
    await accountPermissionApi.users.resetPassword(resetPasswordTarget.value.id, {
      newPassword: resetPasswordForm.newPassword,
    })
    resetPasswordVisible.value = false
  } catch (caught) {
    resetPasswordError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function changeStatus(user: UserRecord) {
  const nextAction = user.status === 'DISABLED' ? '启用' : '停用'
  if (!(await confirmAction(`确认${nextAction}用户“${user.displayName}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    if (user.status === 'DISABLED') {
      await accountPermissionApi.users.enable(user.id)
    } else {
      await accountPermissionApi.users.disable(user.id)
    }
    await loadUsers()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function openRoleDialog(user: UserRecord) {
  roleTarget.value = user
  form.roleIds = user.roles?.map((role) => role.id) ?? []
  roleDialogError.value = ''
  await loadRoles()
  roleDialogVisible.value = true
}

async function saveRoleAssignment() {
  if (!roleTarget.value) {
    return
  }
  const roleIds = normalizeRoleIds(form.roleIds)
  actionLoading.value = true
  roleDialogError.value = ''
  try {
    await accountPermissionApi.users.update(roleTarget.value.id, {
      displayName: roleTarget.value.displayName,
      phone: roleTarget.value.phone ?? '',
      email: roleTarget.value.email ?? '',
      status: roleTarget.value.status,
      roleIds,
    })
    roleDialogVisible.value = false
    await loadUsers()
  } catch (caught) {
    roleDialogError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
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
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="user-keyword" clearable placeholder="账号或姓名" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
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
    <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
    <el-alert v-if="loading" class="state-alert" type="info" title="用户数据加载中" :closable="false" />

    <el-card class="table-card" shadow="never">
      <div class="table-scroll">
        <el-table :data="users" :empty-text="loading ? '加载中' : '暂无用户数据'" stripe>
          <el-table-column prop="username" label="账号" min-width="160" show-overflow-tooltip />
          <el-table-column prop="displayName" label="姓名" min-width="140" show-overflow-tooltip />
          <el-table-column label="状态" min-width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.roles?.map((role: RoleRecord) => role.name).join('、') || '未分配' }}
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <el-button v-if="canUpdate" size="small" text data-test="edit-user" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="canUpdate" size="small" text data-test="assign-role" @click="openRoleDialog(row)">分配角色</el-button>
              <el-dropdown trigger="click" class="table-actions-more" v-if="(canResetPassword) || (canUpdate)">
                <el-button size="small" text>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu class="table-actions-more-menu">
                    <el-button v-if="canResetPassword" size="small" text data-test="reset-password" @click="resetPassword(row)">重置密码</el-button>
                    <el-button
                      v-if="canUpdate"
                      size="small"
                      text
                      :type="row.status === 'DISABLED' ? 'success' : 'warning'"
                      :data-test="row.status === 'DISABLED' ? 'enable-user' : 'disable-user'"
                      @click="changeStatus(row)"
                    >
                      {{ row.status === 'DISABLED' ? '启用' : '停用' }}
                    </el-button>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-pagination
        class="table-pagination"
        layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        :page-size="pagination.pageSize"
        :current-page="pagination.page"
        @current-change="changePage" @size-change="changePageSize"
      />
    </el-card>

    <el-dialog v-model="formVisible" :title="editingUser ? '编辑用户' : '新增用户'" width="560px">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="登录账号">
          <el-input v-model="form.username" name="user-form-username" :disabled="Boolean(editingUser)" />
        </el-form-item>
        <el-form-item label="显示姓名">
          <el-input v-model="form.displayName" name="user-form-display-name" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="初始密码">
          <el-input v-model="form.initialPassword" name="user-form-initial-password" type="password" show-password />
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
        <el-button
          data-test="submit-user"
          type="primary"
          :loading="formSubmitting"
          :disabled="formSubmitting"
          @click="saveUser"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="分配角色" width="520px">
      <el-alert v-if="roleDialogError" class="form-alert" type="error" :title="roleDialogError" :closable="false" />
      <p class="dialog-summary">当前用户：{{ roleTarget?.displayName }}</p>
      <el-select
        v-model="form.roleIds"
        multiple
        placeholder="选择角色"
        style="width: 100%"
      >
        <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
      </el-select>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button data-test="submit-role-assignment" type="primary" :loading="actionLoading" @click="saveRoleAssignment">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetPasswordVisible" title="重置密码" width="520px">
      <el-alert v-if="resetPasswordError" class="form-alert" type="error" :title="resetPasswordError" :closable="false" />
      <p class="dialog-summary">当前用户：{{ resetPasswordTarget?.displayName }}</p>
      <el-form label-position="top">
        <el-form-item label="新密码">
          <el-input v-model="resetPasswordForm.newPassword" name="new-password" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="resetPasswordForm.confirmPassword" name="confirm-password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPasswordVisible = false">取消</el-button>
        <el-button
          data-test="submit-reset-password"
          type="primary"
          :loading="actionLoading"
          @click="submitResetPassword"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
