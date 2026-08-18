<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  knowledgeBaseApi,
  knowledgeTypeLabel,
  type KnowledgeArticleSummary,
  type KnowledgeCategoryRecord,
  type KnowledgeType,
} from '../../shared/api/knowledgeBaseApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { errorMessage } from '../system/shared/pageHelpers'

const route = useRoute()
const router = useRouter()
const categories = ref<KnowledgeCategoryRecord[]>([])
const records = ref<KnowledgeArticleSummary[]>([])
const routeRecords = ref<KnowledgeArticleSummary[]>([])
const loading = ref(false)
const categoryLoading = ref(false)
const error = ref('')
const categoryError = ref('')
const routeAssociationLoaded = ref(false)
const routeAssociationTotal = ref(0)

const filters = reactive<{
  keyword: string
  categoryId: string | number | ''
  knowledgeType: KnowledgeType | ''
}>({
  keyword: '',
  categoryId: '',
  knowledgeType: '',
})
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
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

const routePath = computed(() => String(route.query.routePath ?? ''))
const fromPageHelp = computed(() => route.query.fromPage === '1')
const routeAssociationActive = computed(() => fromPageHelp.value && Boolean(routePath.value))
const hasRouteArticles = computed(() => routeAssociationActive.value && routeAssociationLoaded.value && routeAssociationTotal.value > 0)
const displayedRecords = computed(() => {
  if (!hasRouteArticles.value) {
    return records.value
  }
  return routeRecords.value
})

function syncFiltersFromRoute() {
  filters.keyword = String(route.query.keyword ?? '')
  filters.categoryId = String(route.query.categoryId ?? '')
  filters.knowledgeType = (String(route.query.knowledgeType ?? '') as KnowledgeType | '')
  pagination.page = Number(route.query.page ?? 1) || 1
  pagination.pageSize = Number(route.query.pageSize ?? 10) || 10
}

function searchQuery(overrides: Record<string, unknown> = {}, preservePageHelp = true) {
  return {
    keyword: filters.keyword || undefined,
    categoryId: filters.categoryId || undefined,
    knowledgeType: filters.knowledgeType || undefined,
    page: pagination.page,
    pageSize: pagination.pageSize,
    routePath: preservePageHelp && routeAssociationActive.value ? routePath.value : undefined,
    fromPage: preservePageHelp && routeAssociationActive.value ? '1' : undefined,
    ...overrides,
  }
}

async function loadCategories() {
  categoryLoading.value = true
  categoryError.value = ''
  try {
    categories.value = await knowledgeBaseApi.help.categories()
  } catch (caught) {
    categories.value = []
    categoryError.value = errorMessage(caught)
  } finally {
    categoryLoading.value = false
  }
}

async function loadRouteArticles() {
  routeAssociationLoaded.value = false
  routeRecords.value = []
  routeAssociationTotal.value = 0
  if (!routeAssociationActive.value) {
    return
  }
  try {
    const page = await knowledgeBaseApi.help.byRoute(routePath.value, {
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    routeRecords.value = page.items ?? []
    routeAssociationTotal.value = Number(page.total)
    pagination.total = routeAssociationTotal.value
  } catch {
    routeRecords.value = []
    routeAssociationTotal.value = 0
  } finally {
    routeAssociationLoaded.value = true
  }
}

async function loadArticles() {
  loading.value = true
  error.value = ''
  try {
    if (hasRouteArticles.value) {
      return
    }
    const page = await knowledgeBaseApi.help.search({
      keyword: filters.keyword,
      categoryId: filters.categoryId,
      knowledgeType: filters.knowledgeType,
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

async function refresh() {
  await loadRouteArticles()
  await loadArticles()
}

function updateRoute(overrides: Record<string, unknown> = {}, preservePageHelp = true) {
  void router.push({ name: 'help-center', query: searchQuery(overrides, preservePageHelp) })
}

function search() {
  pagination.page = 1
  updateRoute({ page: 1 }, false)
}

function resetSearch() {
  filters.keyword = ''
  filters.categoryId = ''
  filters.knowledgeType = ''
  pagination.page = 1
  updateRoute({
    keyword: undefined,
    categoryId: undefined,
    knowledgeType: undefined,
    page: 1,
  }, false)
}

function selectCategory(categoryId: string | number | '') {
  filters.categoryId = categoryId
  pagination.page = 1
  updateRoute({ categoryId: categoryId || undefined, page: 1 }, false)
}

function selectKnowledgeType(knowledgeType: KnowledgeType | '') {
  filters.knowledgeType = knowledgeType
  pagination.page = 1
  updateRoute({ knowledgeType: knowledgeType || undefined, page: 1 }, false)
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

function articleLocation(article: KnowledgeArticleSummary) {
  return {
    name: 'help-article',
    params: { id: String(article.id) },
    query: searchQuery({}, routeAssociationActive.value),
  }
}

onMounted(() => {
  syncFiltersFromRoute()
  void loadCategories()
  void refresh()
})

watch(() => route.query, () => {
  syncFiltersFromRoute()
  void refresh()
})
</script>

<template>
  <MasterDataTableView title="系统帮助中心" description="查询当前系统真实页面、字段、按钮、状态、错误和跨模块流程的操作说明。">
    <template #filters>
      <el-form class="query-form" label-position="top">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            clearable
            name="knowledge-keyword"
            placeholder="输入页面名、按钮名、字段名、状态名或报错文案"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.categoryId" clearable filterable placeholder="全部分类" @change="selectCategory">
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="知识类型">
          <el-select v-model="filters.knowledgeType" clearable placeholder="全部类型" @change="selectKnowledgeType">
            <el-option
              v-for="option in knowledgeTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" data-test="search-knowledge" @click="search">查询</el-button>
          <el-button data-test="reset-knowledge" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="categoryError" class="state-alert" type="error" :title="categoryError" :closable="false" />
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading || categoryLoading" class="state-alert" type="info" title="帮助内容加载中" :closable="false" />
      <el-alert
        v-if="fromPageHelp && routeAssociationLoaded && !hasRouteArticles"
        class="state-alert"
        type="info"
        title="当前页面暂无直接关联知识，已按页面关键词展示搜索结果。"
        :closable="false"
      />
    </template>

    <div class="help-layout">
      <aside class="help-category-panel">
        <h2>分类导航</h2>
        <el-scrollbar max-height="520px">
          <button
            type="button"
            :class="['category-link', { active: filters.categoryId === '' }]"
            @click="selectCategory('')"
          >
            全部分类
          </button>
          <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            :class="['category-link', { active: String(filters.categoryId) === String(category.id) }]"
            @click="selectCategory(category.id)"
          >
            {{ category.name }}
          </button>
        </el-scrollbar>
      </aside>

      <section class="help-results">
        <div class="result-summary">
          <strong>{{ hasRouteArticles ? '当前页面相关帮助' : '搜索结果' }}</strong>
          <span>共 {{ pagination.total }} 条</span>
        </div>

        <el-empty
          v-if="!loading && displayedRecords.length === 0"
          description="未找到匹配知识。请尝试使用页面名、按钮名、字段名、状态名或报错文案重新搜索。"
        />

        <div v-else class="article-list">
          <article v-for="article in displayedRecords" :key="article.id" class="article-result">
            <div class="article-main">
              <router-link class="article-title" :to="articleLocation(article)">
                {{ article.title }}
              </router-link>
              <p>{{ article.summary }}</p>
              <div class="article-meta">
                <el-tag size="small">{{ article.categoryName }}</el-tag>
                <el-tag size="small" type="info">{{ article.knowledgeTypeName || knowledgeTypeLabel(article.knowledgeType) }}</el-tag>
                <span v-if="article.pageNames">关联页面：{{ article.pageNames }}</span>
                <span>更新：{{ article.updatedAt || '-' }}</span>
              </div>
            </div>
          </article>
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
      </section>
    </div>
  </MasterDataTableView>
</template>

<style scoped>
.help-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
}

.help-category-panel {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 14px;
  background: #fff;
}

.help-category-panel h2 {
  margin: 0 0 12px;
  color: #111827;
  font-size: 16px;
}

.category-link {
  display: block;
  width: 100%;
  border: 0;
  border-radius: 8px;
  padding: 9px 10px;
  color: #374151;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.category-link:hover,
.category-link.active {
  color: #111827;
  background: #f3f4f6;
  font-weight: 600;
}

.help-results {
  min-width: 0;
}

.result-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #374151;
}

.article-list {
  display: grid;
  gap: 10px;
}

.article-result {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 14px 16px;
  background: #fff;
}

.article-title {
  color: #111827;
  font-size: 16px;
  font-weight: 700;
  text-decoration: none;
}

.article-title:hover {
  color: #2563eb;
}

.article-main p {
  margin: 8px 0;
  color: #4b5563;
  line-height: 1.6;
}

.article-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  color: #6b7280;
  font-size: 12px;
}

@media (max-width: 860px) {
  .help-layout {
    grid-template-columns: 1fr;
  }
}
</style>
