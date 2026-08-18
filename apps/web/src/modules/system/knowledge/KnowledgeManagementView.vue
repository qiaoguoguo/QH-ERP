<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  knowledgeBaseApi,
  knowledgeTypeLabel,
  type KnowledgeArticleSummary,
  type KnowledgeCategoryPayload,
  type KnowledgeCategoryRecord,
  type KnowledgeId,
  type KnowledgeStatus,
  type KnowledgeType,
} from '../../../shared/api/knowledgeBaseApi'
import { confirmAction } from '../../../shared/ui/confirmDialog'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { errorMessage, statusLabel, statusTagType } from '../../system/shared/pageHelpers'

const route = useRoute()
const router = useRouter()
const categories = ref<KnowledgeCategoryRecord[]>([])
const records = ref<KnowledgeArticleSummary[]>([])
const loading = ref(false)
const categoryLoading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const categoryError = ref('')
const categoryDialogVisible = ref(false)
const categorySaving = ref(false)
const editingCategoryId = ref<KnowledgeId | null>(null)

const filters = reactive<{
  keyword: string
  categoryId: KnowledgeId | ''
  knowledgeType: KnowledgeType | ''
  status: KnowledgeStatus | ''
}>({
  keyword: '',
  categoryId: '',
  knowledgeType: '',
  status: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})
const categoryForm = reactive<KnowledgeCategoryPayload>({
  code: '',
  name: '',
  parentId: null,
  sortOrder: 100,
  status: 'ENABLED',
})

const knowledgeTypeOptions: Array<{ label: string; value: KnowledgeType }> = [
  { label: '页面操作', value: 'PAGE' },
  { label: '业务流程', value: 'PROCESS' },
  { label: '字段解释', value: 'FIELD' },
  { label: '状态解释', value: 'STATUS' },
  { label: '错误处理', value: 'ERROR' },
  { label: '权限说明', value: 'PERMISSION' },
  { label: '导入导出', value: 'IMPORT_EXPORT' },
  { label: '业务概念', value: 'CONCEPT' },
]

function syncFromRoute() {
  filters.keyword = String(route.query.keyword ?? '')
  filters.categoryId = String(route.query.categoryId ?? '')
  filters.knowledgeType = (String(route.query.knowledgeType ?? '') as KnowledgeType | '')
  filters.status = (String(route.query.status ?? '') as KnowledgeStatus | '')
  pagination.page = Number(route.query.page ?? 1) || 1
  pagination.pageSize = Number(route.query.pageSize ?? 10) || 10
}

function currentQuery(overrides: Record<string, unknown> = {}) {
  return {
    keyword: filters.keyword || undefined,
    categoryId: filters.categoryId || undefined,
    knowledgeType: filters.knowledgeType || undefined,
    status: filters.status || undefined,
    page: pagination.page,
    pageSize: pagination.pageSize,
    ...overrides,
  }
}

async function loadCategories() {
  categoryLoading.value = true
  categoryError.value = ''
  try {
    categories.value = await knowledgeBaseApi.admin.categories()
  } catch (caught) {
    categories.value = []
    categoryError.value = errorMessage(caught)
  } finally {
    categoryLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await knowledgeBaseApi.admin.articles({
      keyword: filters.keyword,
      categoryId: filters.categoryId,
      knowledgeType: filters.knowledgeType,
      status: filters.status,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = page.items ?? []
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function updateRoute(overrides: Record<string, unknown> = {}) {
  void router.push({ name: 'system-knowledge', query: currentQuery(overrides) })
}

function search() {
  pagination.page = 1
  updateRoute({ page: 1 })
}

function resetSearch() {
  filters.keyword = ''
  filters.categoryId = ''
  filters.knowledgeType = ''
  filters.status = ''
  pagination.page = 1
  updateRoute({
    keyword: undefined,
    categoryId: undefined,
    knowledgeType: undefined,
    status: undefined,
    page: 1,
  })
}

function changePage(page: number) {
  pagination.page = page
  updateRoute({ page })
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  updateRoute({ pageSize, page: 1 })
}

function createArticle() {
  void router.push({ name: 'system-knowledge-create', query: currentQuery() })
}

function editArticle(record: KnowledgeArticleSummary) {
  void router.push({ name: 'system-knowledge-edit', params: { id: String(record.id) }, query: currentQuery() })
}

async function toggleArticleStatus(record: KnowledgeArticleSummary) {
  if (actionLoading.value) {
    return
  }
  const nextAction = record.status === 'ENABLED' ? '停用' : '启用'
  if (record.status === 'ENABLED' && !(await confirmAction(`确认停用知识“${record.title}”？停用后普通用户不可见。`, { type: 'warning', risk: 'warning' }))) {
    return
  }
  actionLoading.value = true
  error.value = ''
  try {
    if (record.status === 'ENABLED') {
      await knowledgeBaseApi.admin.disableArticle(record.id)
    } else {
      await knowledgeBaseApi.admin.enableArticle(record.id)
    }
    await loadRecords()
  } catch (caught) {
    error.value = `${nextAction}失败：${errorMessage(caught)}`
  } finally {
    actionLoading.value = false
  }
}

async function deleteArticle(record: KnowledgeArticleSummary) {
  if (actionLoading.value || !(await confirmAction(`确认删除知识“${record.title}”？删除后不可恢复。`, { type: 'warning', risk: 'danger' }))) {
    return
  }
  actionLoading.value = true
  error.value = ''
  try {
    await knowledgeBaseApi.admin.deleteArticle(record.id)
    await loadRecords()
  } catch (caught) {
    error.value = `删除失败：${errorMessage(caught)}`
  } finally {
    actionLoading.value = false
  }
}

function openCategoryDialog() {
  editingCategoryId.value = null
  categoryForm.code = ''
  categoryForm.name = ''
  categoryForm.parentId = null
  categoryForm.sortOrder = 100
  categoryForm.status = 'ENABLED'
  categoryError.value = ''
  categoryDialogVisible.value = true
}

function editCategory(category: KnowledgeCategoryRecord) {
  editingCategoryId.value = category.id
  categoryForm.code = category.code
  categoryForm.name = category.name
  categoryForm.parentId = category.parentId ?? null
  categoryForm.sortOrder = category.sortOrder ?? 100
  categoryForm.status = category.status
}

async function saveCategory() {
  if (categorySaving.value) {
    return
  }
  if (!categoryForm.code || !categoryForm.name) {
    categoryError.value = '请填写分类编码和分类名称'
    return
  }
  categorySaving.value = true
  categoryError.value = ''
  try {
    if (editingCategoryId.value) {
      await knowledgeBaseApi.admin.updateCategory(editingCategoryId.value, categoryForm)
    } else {
      await knowledgeBaseApi.admin.createCategory(categoryForm)
    }
    await loadCategories()
    editingCategoryId.value = null
    categoryForm.code = ''
    categoryForm.name = ''
    categoryForm.parentId = null
    categoryForm.sortOrder = 100
    categoryForm.status = 'ENABLED'
  } catch (caught) {
    categoryError.value = errorMessage(caught)
  } finally {
    categorySaving.value = false
  }
}

async function toggleCategory(category: KnowledgeCategoryRecord) {
  if (categoryLoading.value) {
    return
  }
  if (category.status === 'ENABLED' && !(await confirmAction(`确认停用分类“${category.name}”？停用后其文章普通查询不可见。`, { type: 'warning', risk: 'warning' }))) {
    return
  }
  categoryLoading.value = true
  categoryError.value = ''
  try {
    if (category.status === 'ENABLED') {
      await knowledgeBaseApi.admin.disableCategory(category.id)
    } else {
      await knowledgeBaseApi.admin.enableCategory(category.id)
    }
    await loadCategories()
  } catch (caught) {
    categoryError.value = errorMessage(caught)
  } finally {
    categoryLoading.value = false
  }
}

async function deleteCategory(category: KnowledgeCategoryRecord) {
  if (categoryLoading.value || !(await confirmAction(`确认删除分类“${category.name}”？删除前需确认分类下没有子分类或文章。`, { type: 'warning', risk: 'danger' }))) {
    return
  }
  categoryLoading.value = true
  categoryError.value = ''
  try {
    await knowledgeBaseApi.admin.deleteCategory(category.id)
    await loadCategories()
  } catch (caught) {
    categoryError.value = errorMessage(caught)
  } finally {
    categoryLoading.value = false
  }
}

onMounted(() => {
  syncFromRoute()
  void loadCategories()
  void loadRecords()
})

watch(() => route.query, () => {
  syncFromRoute()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="知识库管理" description="维护系统操作知识分类和文章，保存后立即对帮助中心生效。">
    <template #actions>
      <el-button data-test="manage-knowledge-categories" @click="openCategoryDialog">分类维护</el-button>
      <el-button type="primary" data-test="create-knowledge-article" @click="createArticle">新增知识</el-button>
    </template>

    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="标题、摘要、页面、关键词" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.categoryId" clearable filterable placeholder="全部分类">
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识类型">
          <el-select v-model="filters.knowledgeType" clearable placeholder="全部类型">
            <el-option v-for="option in knowledgeTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" data-test="search-knowledge-management" @click="search">查询</el-button>
          <el-button data-test="reset-knowledge-management" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="categoryError" class="state-alert" type="error" :title="categoryError" :closable="false" />
      <el-alert v-if="loading || categoryLoading" class="state-alert" type="info" title="知识库数据加载中" :closable="false" />
    </template>

    <div class="table-scroll">
      <el-table :data="records" :empty-text="loading ? '加载中' : '暂无知识内容'" stripe>
        <el-table-column label="标题" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stacked-cell">
              <strong>{{ row.title }}</strong>
              <span>{{ row.summary }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" min-width="110">
          <template #default="{ row }">
            {{ row.knowledgeTypeName || knowledgeTypeLabel(row.knowledgeType) }}
          </template>
        </el-table-column>
        <el-table-column prop="pageNames" label="关联页面" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
        <el-table-column label="操作" fixed="right" width="184">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button size="small" plain type="primary" data-test="edit-knowledge-article" @click="editArticle(row)">编辑</el-button>
              <el-button
                size="small"
                plain
                :type="row.status === 'ENABLED' ? 'warning' : 'success'"
                :disabled="actionLoading"
                data-test="toggle-knowledge-article"
                @click="toggleArticleStatus(row)"
              >
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
              <el-dropdown trigger="click" class="table-actions-more">
                <el-button size="small" plain :disabled="actionLoading">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item>
                      <el-button
                        size="small"
                        plain
                        type="danger"
                        :disabled="actionLoading"
                        data-test="delete-knowledge-article"
                        @click="deleteArticle(row)"
                      >
                        删除
                      </el-button>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      :page-size="pagination.pageSize"
      :current-page="pagination.page"
      @current-change="changePage"
      @size-change="changePageSize"
    />

    <el-dialog v-model="categoryDialogVisible" title="知识分类维护" width="760px" destroy-on-close>
      <el-alert v-if="categoryError" class="state-alert" type="error" :title="categoryError" :closable="false" />
      <el-form class="knowledge-category-form" label-position="top">
        <el-form-item label="分类编码" required>
          <el-input v-model="categoryForm.code" :disabled="Boolean(editingCategoryId)" placeholder="请输入唯一分类编码" />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="categoryForm.name" placeholder="例如 采购管理" />
        </el-form-item>
        <el-form-item label="父级分类">
          <el-select v-model="categoryForm.parentId" clearable filterable placeholder="无父级">
            <el-option
              v-for="category in categories"
              :key="category.id"
              :disabled="String(category.id) === String(editingCategoryId ?? '')"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="状态" required>
          <el-select v-model="categoryForm.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="table-scroll category-table">
        <el-table :data="categories" stripe>
          <el-table-column prop="code" label="编码" min-width="140" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column label="状态" min-width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="184">
            <template #default="{ row }">
              <el-button size="small" plain type="primary" @click="editCategory(row)">编辑</el-button>
              <el-button size="small" plain :type="row.status === 'ENABLED' ? 'warning' : 'success'" @click="toggleCategory(row)">
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
              <el-dropdown trigger="click" class="table-actions-more">
                <el-button size="small" plain>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item>
                      <el-button size="small" plain type="danger" @click="deleteCategory(row)">删除</el-button>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button :disabled="categorySaving" @click="categoryDialogVisible = false">取消</el-button>
          <el-button :loading="categorySaving" type="primary" @click="saveCategory">
            {{ editingCategoryId ? '保存分类' : '新增分类' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </MasterDataTableView>
</template>

<style scoped>
.stacked-cell {
  display: grid;
  gap: 4px;
  line-height: 1.45;
}

.stacked-cell span {
  color: #6b7280;
  font-size: 12px;
}

.table-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.knowledge-category-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.category-table {
  margin-top: 10px;
}

@media (max-width: 760px) {
  .knowledge-category-form {
    grid-template-columns: 1fr;
  }
}
</style>
