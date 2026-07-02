<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  masterDataApi,
  type CategoryPayload,
  type CategoryRecord,
  type MasterDataStatus,
  type ResourceId,
} from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'
import { errorMessage, pageItems, statusTagType } from '../../system/shared/pageHelpers'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { masterStatusLabel } from '../../master/shared/masterPageHelpers'

interface CategoryTreeNode extends CategoryRecord {
  children: CategoryTreeNode[]
}

const authStore = useAuthStore()
const treeProps = {
  label: 'name',
  children: 'children',
}

const filters = reactive<{
  keyword: string
  status?: MasterDataStatus
}>({
  keyword: '',
  status: undefined,
})
const records = ref<CategoryRecord[]>([])
const total = ref(0)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionLoading = ref(false)
const formVisible = ref(false)
const formSubmitting = ref(false)
const formError = ref('')
const editingRecord = ref<CategoryRecord | null>(null)
const form = reactive<{
  code: string
  name: string
  parentId: ResourceId | ''
  sortOrder: string
  status: MasterDataStatus
  remark: string
}>({
  code: '',
  name: '',
  parentId: '',
  sortOrder: '',
  status: 'ENABLED',
  remark: '',
})

const canCreate = computed(() => authStore.hasPermission('master:material-category:create'))
const canUpdate = computed(() => authStore.hasPermission('master:material-category:update'))
const categoryTree = computed(() => buildCategoryTree(records.value))
const parentOptions = computed(() => records.value.filter((record) => {
  if (!editingRecord.value) {
    return true
  }
  return String(record.id) !== String(editingRecord.value.id)
}))

function buildCategoryTree(items: CategoryRecord[]): CategoryTreeNode[] {
  const sortedItems = [...items].sort((left, right) => {
    const orderDiff = Number(left.sortOrder) - Number(right.sortOrder)
    return orderDiff || String(left.code).localeCompare(String(right.code))
  })
  const nodeMap = new Map<string, CategoryTreeNode>()
  sortedItems.forEach((item) => {
    nodeMap.set(String(item.id), { ...item, children: [] })
  })

  const roots: CategoryTreeNode[] = []
  nodeMap.forEach((node) => {
    if (node.parentId !== null && node.parentId !== undefined && nodeMap.has(String(node.parentId))) {
      nodeMap.get(String(node.parentId))?.children.push(node)
      return
    }
    roots.push(node)
  })

  return roots
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await masterDataApi.categories.list({
      page: 1,
      pageSize: 100,
      keyword: filters.keyword,
      status: filters.status,
    })
    records.value = pageItems(page)
    total.value = Number(page.total)
  } catch (caught) {
    records.value = []
    total.value = 0
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function search() {
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.status = undefined
  void loadRecords()
}

function resetForm(record?: CategoryRecord) {
  Object.assign(form, {
    code: record?.code ?? '',
    name: record?.name ?? '',
    parentId: record?.parentId ?? '',
    sortOrder: record ? String(record.sortOrder) : '',
    status: record?.status ?? 'ENABLED',
    remark: record?.remark ?? '',
  })
  formError.value = ''
}

function openCreate() {
  editingRecord.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(record: CategoryRecord) {
  editingRecord.value = record
  resetForm(record)
  formVisible.value = true
}

function isBlank(value: string) {
  return value.trim() === ''
}

function normalizeOptionalId(value: ResourceId | ''): ResourceId | null {
  if (value === '' || value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }

  const trimmedValue = String(value).trim()
  if (!trimmedValue) {
    return null
  }
  const numericValue = Number(trimmedValue)
  return Number.isFinite(numericValue) ? numericValue : trimmedValue
}

function validateCategoryForm(): { parentId: ResourceId | null; sortOrder: number } | null {
  if (!form.code.trim() || !form.name.trim()) {
    formError.value = '请完整填写编码和名称'
    return null
  }
  if (isBlank(form.sortOrder)) {
    formError.value = '排序为必填'
    return null
  }

  const sortOrder = Number(form.sortOrder)
  if (!Number.isFinite(sortOrder) || !Number.isInteger(sortOrder) || sortOrder < 0) {
    formError.value = '排序必须为非负整数'
    return null
  }

  formError.value = ''
  return {
    parentId: normalizeOptionalId(form.parentId),
    sortOrder,
  }
}

async function saveRecord() {
  if (formSubmitting.value) {
    return
  }
  const validatedForm = validateCategoryForm()
  if (!validatedForm) {
    return
  }

  const payload: CategoryPayload = {
    code: form.code.trim(),
    name: form.name.trim(),
    parentId: validatedForm.parentId,
    sortOrder: validatedForm.sortOrder,
    status: form.status,
  }
  const remark = form.remark.trim()
  if (remark) {
    payload.remark = remark
  }

  formSubmitting.value = true
  try {
    if (editingRecord.value) {
      await masterDataApi.categories.update(editingRecord.value.id, payload)
    } else {
      await masterDataApi.categories.create(payload)
    }
    formVisible.value = false
    await loadRecords()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

async function changeStatus(record: CategoryRecord) {
  const nextAction = record.status === 'DISABLED' ? '启用' : '停用'
  if (!window.confirm(`确认${nextAction}物料分类“${record.name}”？`)) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    if (record.status === 'DISABLED') {
      await masterDataApi.categories.enable(record.id)
    } else {
      await masterDataApi.categories.disable(record.id)
    }
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <MasterDataTableView title="物料分类" description="维护物料档案、BOM 和生产领料使用的分类层级。">
    <template #actions>
      <el-button v-if="canCreate" data-test="create-category" type="primary" @click="openCreate">
        新增分类
      </el-button>
    </template>

    <template #filters>
      <el-form class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="category-keyword" clearable placeholder="编码或名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-category" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-category" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="物料分类数据加载中" :closable="false" />
    </template>

    <div class="material-category-layout">
      <aside class="category-tree-pane">
        <div class="category-tree-header">
          <strong>分类树</strong>
          <span>共 {{ total }} 项</span>
        </div>
        <el-tree
          class="category-tree"
          :data="categoryTree"
          :empty-text="loading ? '加载中' : '暂无物料分类'"
          node-key="id"
          default-expand-all
          :props="treeProps"
        />
      </aside>
      <section class="category-table-pane">
        <div class="table-scroll">
          <el-table :data="records" :empty-text="loading ? '加载中' : '暂无物料分类数据'" stripe>
            <el-table-column prop="code" label="编码" min-width="140" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column label="上级分类" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ records.find((item) => String(item.id) === String(row.parentId))?.name ?? '无' }}
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" min-width="90" />
            <el-table-column label="状态" min-width="90">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ masterStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" fixed="right" min-width="160">
              <template #default="{ row }">
                <el-button v-if="canUpdate" size="small" text data-test="edit-category" @click="openEdit(row)">
                  编辑
                </el-button>
                <el-button
                  v-if="canUpdate"
                  size="small"
                  text
                  :disabled="actionLoading"
                  :type="row.status === 'DISABLED' ? 'success' : 'danger'"
                  :data-test="row.status === 'DISABLED' ? 'enable-category' : 'disable-category'"
                  @click="changeStatus(row)"
                >
                  {{ row.status === 'DISABLED' ? '启用' : '停用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>

    <el-dialog v-model="formVisible" :title="editingRecord ? '编辑物料分类' : '新增物料分类'" width="560px">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="分类编码">
          <el-input v-model="form.code" name="category-code" :disabled="Boolean(editingRecord)" />
        </el-form-item>
        <el-form-item label="分类名称">
          <el-input v-model="form.name" name="category-name" />
        </el-form-item>
        <el-form-item label="上级分类">
          <el-select v-model="form.parentId" data-test="category-parent-id" clearable placeholder="无上级分类" style="width: 100%">
            <el-option
              v-for="option in parentOptions"
              :key="option.id"
              :label="option.name"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input v-model="form.sortOrder" name="category-sort-order" inputmode="numeric" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" data-test="category-status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" name="category-remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button
          data-test="submit-category"
          type="primary"
          :loading="formSubmitting"
          :disabled="formSubmitting"
          @click="saveRecord"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>

<style scoped>
.material-category-layout {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  min-width: 0;
}

.category-tree-pane {
  min-width: 0;
  padding: 14px;
  border-right: 1px solid var(--qherp-border);
}

.category-tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.category-tree-header strong {
  font-size: 14px;
}

.category-tree-header span {
  color: var(--qherp-muted);
  font-size: 12px;
}

.category-tree {
  min-height: 280px;
}

.category-table-pane {
  min-width: 0;
}

@media (max-width: 900px) {
  .material-category-layout {
    grid-template-columns: 1fr;
  }

  .category-tree-pane {
    border-right: 0;
    border-bottom: 1px solid var(--qherp-border);
  }
}
</style>
