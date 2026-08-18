<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  knowledgeBaseApi,
  knowledgeTypeLabel,
  type KnowledgeArticlePayload,
  type KnowledgeArticleSummary,
  type KnowledgeCategoryRecord,
  type KnowledgeId,
  type KnowledgeStatus,
  type KnowledgeType,
} from '../../../shared/api/knowledgeBaseApi'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { errorMessage } from '../../system/shared/pageHelpers'

const route = useRoute()
const router = useRouter()
const categories = ref<KnowledgeCategoryRecord[]>([])
const loading = ref(false)
const saving = ref(false)
const loadFailed = ref(false)
const error = ref('')
const isEdit = computed(() => route.name === 'system-knowledge-edit')
const pageTitle = computed(() => isEdit.value ? '编辑知识' : '新增知识')
const pageDescription = computed(() => '维护系统操作知识正文、关键词、页面关联和启用状态。保存失败时保留当前输入。')
const formDisabled = computed(() => loading.value || saving.value || loadFailed.value)
const currentArticleId = computed(() => isEdit.value ? String(route.params.id ?? '') : '')
const relatedArticleOptions = ref<KnowledgeArticleSummary[]>([])
const relatedArticleLoading = ref(false)
let relatedSearchSerial = 0

const form = reactive<KnowledgeArticlePayload>({
  slug: '',
  title: '',
  summary: '',
  categoryId: '',
  knowledgeType: 'PAGE',
  content: '',
  keywords: '',
  routePaths: '',
  pageNames: '',
  permissionNote: '',
  relatedArticleIds: [],
  sortOrder: 100,
  status: 'ENABLED',
})

type RequiredField = 'slug' | 'title' | 'summary' | 'categoryId' | 'knowledgeType' | 'content' | 'status'

const fieldErrors = reactive<Record<RequiredField, string>>({
  slug: '',
  title: '',
  summary: '',
  categoryId: '',
  knowledgeType: '',
  content: '',
  status: '',
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

const returnQuery = computed(() => ({
  keyword: route.query.keyword || undefined,
  categoryId: route.query.categoryId || undefined,
  knowledgeType: route.query.knowledgeType || undefined,
  status: route.query.status || undefined,
  page: route.query.page || undefined,
  pageSize: route.query.pageSize || undefined,
}))

async function loadCategories() {
  categories.value = await knowledgeBaseApi.admin.categories()
}

async function loadArticle() {
  if (!isEdit.value) {
    return
  }
  loadFailed.value = false
  error.value = ''
  try {
    const article = await knowledgeBaseApi.admin.article(String(route.params.id))
    form.slug = article.slug
    form.title = article.title
    form.summary = article.summary
    form.categoryId = article.categoryId
    form.knowledgeType = article.knowledgeType
    form.content = article.content || ''
    form.keywords = article.keywords || ''
    form.routePaths = article.routePaths || ''
    form.pageNames = article.pageNames || ''
    form.permissionNote = article.permissionNote || ''
    form.relatedArticleIds = (article.relatedArticleIds ?? []).filter((id) => String(id) !== currentArticleId.value)
    form.sortOrder = article.sortOrder ?? 100
    form.status = (article.status ?? 'ENABLED') as KnowledgeStatus
    await loadSelectedRelatedArticles(form.relatedArticleIds)
  } catch (caught) {
    loadFailed.value = true
    error.value = errorMessage(caught)
  }
}

function clearFieldError(field: RequiredField) {
  fieldErrors[field] = ''
}

function clearFieldErrors() {
  ;(Object.keys(fieldErrors) as RequiredField[]).forEach((field) => {
    fieldErrors[field] = ''
  })
}

function validateForm() {
  clearFieldErrors()
  let valid = true
  if (!form.title.trim()) {
    fieldErrors.title = '请填写标题'
    valid = false
  }
  if (!form.slug.trim()) {
    fieldErrors.slug = '请填写知识标识'
    valid = false
  }
  if (!form.summary.trim()) {
    fieldErrors.summary = '请填写摘要'
    valid = false
  }
  if (!form.categoryId) {
    fieldErrors.categoryId = '请选择分类'
    valid = false
  }
  if (!form.knowledgeType) {
    fieldErrors.knowledgeType = '请选择知识类型'
    valid = false
  }
  if (!form.content.trim()) {
    fieldErrors.content = '请填写正文'
    valid = false
  }
  if (!form.status) {
    fieldErrors.status = '请选择状态'
    valid = false
  }
  return valid
}

function isCurrentArticle(article: KnowledgeArticleSummary) {
  return Boolean(currentArticleId.value) && String(article.id) === currentArticleId.value
}

function relatedArticleLabel(article: KnowledgeArticleSummary) {
  return `${article.title} / ${article.categoryName || '未分类'} / ${article.knowledgeTypeName || knowledgeTypeLabel(article.knowledgeType)}`
}

function mergeRelatedArticleOptions(items: KnowledgeArticleSummary[]) {
  const optionMap = new Map<string, KnowledgeArticleSummary>()
  relatedArticleOptions.value.forEach((item) => {
    optionMap.set(String(item.id), item)
  })
  items.forEach((item) => {
    if (!isCurrentArticle(item)) {
      optionMap.set(String(item.id), item)
    }
  })
  relatedArticleOptions.value = Array.from(optionMap.values())
}

async function loadSelectedRelatedArticles(ids: KnowledgeId[]) {
  const uniqueIds = Array.from(new Set(ids.map((id) => String(id)))).filter((id) => id !== currentArticleId.value)
  if (uniqueIds.length === 0) {
    return
  }
  const selectedArticles = await Promise.all(
    uniqueIds.map(async (id) => {
      try {
        return await knowledgeBaseApi.admin.article(id)
      } catch {
        return null
      }
    }),
  )
  const availableArticles = selectedArticles.filter((article): article is NonNullable<(typeof selectedArticles)[number]> => article !== null)
  mergeRelatedArticleOptions(availableArticles)
}

async function searchRelatedArticles(keyword = '') {
  const searchSerial = ++relatedSearchSerial
  relatedArticleLoading.value = true
  try {
    const page = await knowledgeBaseApi.admin.articles({
      keyword,
      categoryId: '',
      knowledgeType: '',
      status: '',
      page: 1,
      pageSize: 20,
    })
    if (searchSerial === relatedSearchSerial) {
      mergeRelatedArticleOptions(page.items ?? [])
    }
  } catch (caught) {
    error.value = `关联知识搜索失败：${errorMessage(caught)}`
  } finally {
    if (searchSerial === relatedSearchSerial) {
      relatedArticleLoading.value = false
    }
  }
}

function backToList() {
  void router.push({ name: 'system-knowledge', query: returnQuery.value })
}

async function saveArticle() {
  if (formDisabled.value) {
    return
  }
  error.value = ''
  if (!validateForm()) {
    return
  }
  saving.value = true
  try {
    const payload: KnowledgeArticlePayload = {
      ...form,
      slug: form.slug.trim(),
      title: form.title.trim(),
      summary: form.summary.trim(),
      content: form.content.trim(),
      keywords: form.keywords?.trim(),
      routePaths: form.routePaths?.trim(),
      pageNames: form.pageNames?.trim(),
      permissionNote: form.permissionNote?.trim(),
      relatedArticleIds: form.relatedArticleIds.filter((id) => String(id) !== currentArticleId.value),
    }
    if (isEdit.value) {
      await knowledgeBaseApi.admin.updateArticle(String(route.params.id), payload)
    } else {
      await knowledgeBaseApi.admin.createArticle(payload)
    }
    backToList()
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await loadCategories()
    await loadArticle()
    await searchRelatedArticles('')
  } catch (caught) {
    loadFailed.value = true
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <MasterDataTableView :title="pageTitle" :description="pageDescription">
    <template #actions>
      <el-button data-test="cancel-knowledge-edit" :disabled="saving" @click="backToList">返回列表</el-button>
      <el-button type="primary" data-test="save-knowledge-article" :loading="saving" :disabled="formDisabled" @click="saveArticle">
        保存知识
      </el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="知识内容加载中" :closable="false" />
    </template>

    <el-form class="knowledge-editor-form" label-position="top" :disabled="formDisabled">
      <el-card shadow="never" class="form-section">
        <template #header>基础信息</template>
        <div class="form-grid">
          <el-form-item label="知识标识" required :error="fieldErrors.slug">
            <el-input v-model="form.slug" placeholder="必填，例如 procurement-order-confirm" @input="clearFieldError('slug')" />
          </el-form-item>
          <el-form-item label="标题" required :error="fieldErrors.title">
            <el-input v-model="form.title" placeholder="例如 采购订单为什么不能确认" @input="clearFieldError('title')" />
          </el-form-item>
          <el-form-item label="分类" required :error="fieldErrors.categoryId">
            <el-select v-model="form.categoryId" filterable placeholder="请选择分类" @change="clearFieldError('categoryId')">
              <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="知识类型" required :error="fieldErrors.knowledgeType">
            <el-select v-model="form.knowledgeType" placeholder="请选择类型" @change="clearFieldError('knowledgeType')">
              <el-option v-for="option in knowledgeTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" required :error="fieldErrors.status">
            <el-select v-model="form.status" @change="clearFieldError('status')">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="0" :step="10" />
          </el-form-item>
        </div>
        <el-form-item label="摘要" required :error="fieldErrors.summary">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="说明这篇知识解决什么问题" @input="clearFieldError('summary')" />
        </el-form-item>
      </el-card>

      <el-card shadow="never" class="form-section">
        <template #header>正文内容</template>
        <el-alert
          class="format-note"
          type="info"
          title="正文只支持 # 一级标题、## 二级标题、- 无序列表、1. 有序列表和普通段落；不支持 HTML。"
          :closable="false"
        />
        <el-form-item label="正文" required :error="fieldErrors.content">
          <el-input v-model="form.content" type="textarea" :rows="16" placeholder="# 功能用途&#10;说明页面用途。&#10;# 操作步骤&#10;1. 打开页面。&#10;2. 点击按钮。" @input="clearFieldError('content')" />
        </el-form-item>
      </el-card>

      <el-card shadow="never" class="form-section">
        <template #header>检索与页面关联</template>
        <div class="form-grid">
          <el-form-item label="关键词">
            <el-input v-model="form.keywords" placeholder="多个关键词用逗号分隔" />
          </el-form-item>
          <el-form-item label="页面名称">
            <el-input v-model="form.pageNames" placeholder="多个页面可换行填写" />
          </el-form-item>
          <el-form-item label="关联路由">
            <el-input v-model="form.routePaths" type="textarea" :rows="3" placeholder="/procurement/orders&#10;/procurement/orders/:id" />
          </el-form-item>
          <el-form-item label="权限说明">
            <el-input v-model="form.permissionNote" type="textarea" :rows="3" placeholder="说明查看或维护该功能需要哪些角色或权限" />
          </el-form-item>
          <el-form-item label="关联知识">
            <el-select
              v-model="form.relatedArticleIds"
              clearable
              filterable
              multiple
              remote
              reserve-keyword
              :loading="relatedArticleLoading"
              :remote-method="searchRelatedArticles"
              placeholder="搜索标题、分类、类型或页面关键词"
              @focus="searchRelatedArticles('')"
            >
              <el-option
                v-for="article in relatedArticleOptions"
                :key="article.id"
                :label="relatedArticleLabel(article)"
                :value="article.id"
                :disabled="isCurrentArticle(article)"
              >
                <div class="related-option">
                  <strong>{{ article.title }}</strong>
                  <span>{{ article.categoryName }} / {{ article.knowledgeTypeName || knowledgeTypeLabel(article.knowledgeType) }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
        </div>
      </el-card>
    </el-form>
  </MasterDataTableView>
</template>

<style scoped>
.knowledge-editor-form {
  display: grid;
  gap: 16px;
}

.form-section {
  width: 100%;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.format-note {
  margin-bottom: 12px;
}

.related-option {
  display: grid;
  gap: 2px;
  line-height: 1.35;
}

.related-option span {
  color: #6b7280;
  font-size: 12px;
}

@media (max-width: 820px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
