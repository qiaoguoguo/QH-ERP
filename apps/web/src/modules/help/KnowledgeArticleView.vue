<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  knowledgeBaseApi,
  knowledgeTypeLabel,
  type KnowledgeArticleDetail,
  type KnowledgeArticleSummary,
} from '../../shared/api/knowledgeBaseApi'
import MasterDataTableView from '../master/shared/MasterDataTableView.vue'
import { errorMessage } from '../system/shared/pageHelpers'
import KnowledgeContentRenderer from './KnowledgeContentRenderer.vue'
import { currentPageHelpReturnPath } from './pageHelp'

const route = useRoute()
const router = useRouter()
const article = ref<KnowledgeArticleDetail | null>(null)
const relatedArticles = ref<KnowledgeArticleSummary[]>([])
const loading = ref(true)
const error = ref('')

function articleId() {
  return String(route.params.id)
}

function headingId(text: string, index: number) {
  return `section-${index}-${text.toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-').replace(/(^-)|(-$)/g, '') || 'item'}`
}

const headingLinks = computed(() => {
  let index = 0
  return (article.value?.content || '')
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((line) => {
      const match = /^(#{1,2})\s+(.+)$/.exec(line.trim())
      if (!match) {
        return null
      }
      index += 1
      return {
        level: match[1].length,
        text: match[2],
        id: headingId(match[2], index),
      }
    })
    .filter((item): item is { level: number; text: string; id: string } => Boolean(item))
})

const helpCenterQuery = computed(() => ({
  keyword: route.query.keyword || undefined,
  categoryId: route.query.categoryId || undefined,
  knowledgeType: route.query.knowledgeType || undefined,
  page: route.query.page || undefined,
  pageSize: route.query.pageSize || undefined,
  routePath: route.query.routePath || undefined,
  fromPage: route.query.fromPage || undefined,
}))
const canReturnOriginalPage = computed(() => route.query.fromPage === '1' && Boolean(currentPageHelpReturnPath()))

async function loadArticle() {
  loading.value = true
  error.value = ''
  try {
    article.value = await knowledgeBaseApi.help.get(articleId())
    relatedArticles.value = await knowledgeBaseApi.help.related(articleId())
  } catch (caught) {
    article.value = null
    relatedArticles.value = []
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function backToHelp() {
  void router.push({ name: 'help-center', query: helpCenterQuery.value })
}

function returnOriginalPage() {
  const target = currentPageHelpReturnPath()
  if (target) {
    void router.push(target)
  }
}

function relatedLocation(item: KnowledgeArticleSummary) {
  return {
    name: 'help-article',
    params: { id: String(item.id) },
    query: helpCenterQuery.value,
  }
}

onMounted(() => {
  void loadArticle()
})

watch(() => route.params.id, () => {
  void loadArticle()
})
</script>

<template>
  <MasterDataTableView
    :title="article?.title || '知识文章详情'"
    :description="article?.summary || '查看系统操作知识的正文、目录和关联知识。'"
  >
    <template #actions>
      <el-button v-if="canReturnOriginalPage" data-test="return-original-page" @click="returnOriginalPage">
        返回原页面
      </el-button>
      <el-button data-test="return-help-center" @click="backToHelp">返回帮助中心</el-button>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="loading" class="state-alert" type="info" title="知识文章加载中" :closable="false" />
    </template>

    <el-empty v-if="!loading && !article" description="知识内容不存在或已停用" />
    <article v-else-if="article" class="article-detail">
      <div class="article-meta-card">
        <div>
          <span class="meta-label">分类</span>
          <strong>{{ article.categoryName }}</strong>
        </div>
        <div>
          <span class="meta-label">类型</span>
          <strong>{{ article.knowledgeTypeName || knowledgeTypeLabel(article.knowledgeType) }}</strong>
        </div>
        <div>
          <span class="meta-label">更新时间</span>
          <strong>{{ article.updatedAt || '-' }}</strong>
        </div>
      </div>

      <div class="article-body-layout">
        <aside class="article-toc">
          <h2>正文目录</h2>
          <el-empty v-if="headingLinks.length === 0" description="暂无目录" />
          <a
            v-for="heading in headingLinks"
            :key="heading.id"
            :class="['toc-link', `level-${heading.level}`]"
            :href="`#${heading.id}`"
          >
            {{ heading.text }}
          </a>
        </aside>

        <section class="article-content-card">
          <p v-if="article.permissionNote" class="permission-note">
            权限说明：{{ article.permissionNote }}
          </p>
          <KnowledgeContentRenderer :content="article.content" />
        </section>
      </div>

      <section class="related-section">
        <h2>关联知识</h2>
        <el-empty v-if="relatedArticles.length === 0" description="暂无关联知识" />
        <div v-else class="related-list">
          <router-link
            v-for="item in relatedArticles"
            :key="item.id"
            class="related-card"
            :to="relatedLocation(item)"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.summary }}</span>
          </router-link>
        </div>
      </section>
    </article>
  </MasterDataTableView>
</template>

<style scoped>
.article-detail {
  display: grid;
  gap: 16px;
}

.article-meta-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 14px;
  background: #f9fafb;
}

.meta-label {
  display: block;
  margin-bottom: 4px;
  color: #6b7280;
  font-size: 12px;
}

.article-body-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
}

.article-toc,
.article-content-card,
.related-section {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 16px;
  background: #fff;
}

.article-toc h2,
.related-section h2 {
  margin: 0 0 12px;
  font-size: 16px;
}

.toc-link {
  display: block;
  border-radius: 8px;
  padding: 7px 8px;
  color: #374151;
  text-decoration: none;
}

.toc-link:hover {
  color: #2563eb;
  background: #f3f4f6;
}

.toc-link.level-2 {
  padding-left: 20px;
  font-size: 13px;
}

.permission-note {
  margin: 0 0 14px;
  border-left: 4px solid #2563eb;
  padding: 8px 12px;
  color: #1f2937;
  background: #eff6ff;
}

.related-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.related-card {
  display: grid;
  gap: 6px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px;
  color: #374151;
  text-decoration: none;
}

.related-card:hover {
  border-color: #93c5fd;
  color: #2563eb;
}

.related-card span {
  color: #6b7280;
  font-size: 13px;
}

@media (max-width: 960px) {
  .article-meta-card,
  .article-body-layout,
  .related-list {
    grid-template-columns: 1fr;
  }
}
</style>
