package com.qherp.api.system.knowledge;

import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeSeedArticle;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeSeedCategory;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeStatus;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class KnowledgeSeedInitializer implements ApplicationRunner {

	private static final String SYSTEM_OPERATOR = "system";

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

	public KnowledgeSeedInitializer(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) throws Exception {
		loadCategories();
		loadArticles();
	}

	private void loadCategories() throws IOException {
		Resource resource = this.resourceResolver.getResource("classpath:/knowledge/categories.json");
		if (!resource.exists()) {
			return;
		}
		for (KnowledgeSeedCategory category : readCategories(resource)) {
			if (!hasText(category.code()) || !hasText(category.name())) {
				continue;
			}
			if (existsByCode(category.code())) {
				continue;
			}
			Long parentId = hasText(category.parentCode()) ? categoryIdByCode(category.parentCode()) : null;
			OffsetDateTime now = OffsetDateTime.now();
			this.jdbcTemplate.update("""
					insert into sys_knowledge_category (
						code, name, parent_id, sort_order, status, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", category.code().trim(), category.name().trim(), parentId,
					category.sortOrder() == null ? 0 : category.sortOrder(),
					KnowledgeModels.statusOrEnabled(category.status()).name(), SYSTEM_OPERATOR, now, SYSTEM_OPERATOR,
					now);
		}
	}

	private void loadArticles() throws IOException {
		Resource[] resources = this.resourceResolver.getResources("classpath*:/knowledge/*.json");
		List<KnowledgeSeedArticle> articles = new ArrayList<>();
		for (Resource resource : resources) {
			if (!resource.exists() || resource.getFilename() == null || "categories.json".equals(resource.getFilename())) {
				continue;
			}
			articles.addAll(readArticles(resource));
		}
		List<String> insertedSlugs = new ArrayList<>();
		for (KnowledgeSeedArticle article : articles) {
			if (insertArticleIfMissing(article)) {
				insertedSlugs.add(article.slug());
			}
		}
		for (KnowledgeSeedArticle article : articles) {
			if (insertedSlugs.contains(article.slug())) {
				insertRelationsIfMissing(article);
			}
		}
	}

	private boolean insertArticleIfMissing(KnowledgeSeedArticle article) {
		if (!hasText(article.slug()) || !hasText(article.title()) || !hasText(article.summary())
				|| !hasText(article.categoryCode()) || article.knowledgeType() == null || !hasText(article.content())
				|| existsBySlug(article.slug())) {
			return false;
		}
		Long categoryId = categoryIdByCode(article.categoryCode());
		if (categoryId == null) {
			return false;
		}
		OffsetDateTime now = OffsetDateTime.now();
		KnowledgeStatus status = KnowledgeModels.statusOrEnabled(article.status());
		this.jdbcTemplate.update("""
				insert into sys_knowledge_article (
					slug, title, summary, category_id, knowledge_type, content, keywords, route_paths,
					page_names, permission_note, sort_order, status, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", article.slug().trim(), article.title().trim(), article.summary().trim(), categoryId,
				article.knowledgeType().name(), article.content().trim(), blankToNull(article.keywords()),
				blankToNull(article.routePaths()), blankToNull(article.pageNames()), blankToNull(article.permissionNote()),
				article.sortOrder() == null ? 0 : article.sortOrder(), status.name(), SYSTEM_OPERATOR, now,
				SYSTEM_OPERATOR, now);
		return true;
	}

	private void insertRelationsIfMissing(KnowledgeSeedArticle article) {
		Long articleId = articleIdBySlug(article.slug());
		if (articleId == null || article.relatedSlugs() == null) {
			return;
		}
		for (String relatedSlug : article.relatedSlugs()) {
			Long relatedId = articleIdBySlug(relatedSlug);
			if (relatedId == null || articleId.equals(relatedId) || relationExists(articleId, relatedId)) {
				continue;
			}
			this.jdbcTemplate.update("""
					insert into sys_knowledge_article_relation (article_id, related_article_id, created_at)
					values (?, ?, ?)
					""", articleId, relatedId, OffsetDateTime.now());
		}
	}

	private List<KnowledgeSeedCategory> readCategories(Resource resource) throws IOException {
		JsonNode root = this.objectMapper.readTree(resource.getInputStream());
		JsonNode array = root.isArray() ? root : root.get("categories");
		List<KnowledgeSeedCategory> categories = new ArrayList<>();
		if (array == null || !array.isArray()) {
			return categories;
		}
		for (JsonNode node : array) {
			categories.add(this.objectMapper.treeToValue(node, KnowledgeSeedCategory.class));
		}
		return categories;
	}

	private List<KnowledgeSeedArticle> readArticles(Resource resource) throws IOException {
		JsonNode root = this.objectMapper.readTree(resource.getInputStream());
		JsonNode array = root.isArray() ? root : root.get("articles");
		List<KnowledgeSeedArticle> articles = new ArrayList<>();
		if (array == null || !array.isArray()) {
			if (root.isObject() && root.has("slug")) {
				articles.add(this.objectMapper.treeToValue(root, KnowledgeSeedArticle.class));
			}
			return articles;
		}
		for (JsonNode node : array) {
			articles.add(this.objectMapper.treeToValue(node, KnowledgeSeedArticle.class));
		}
		return articles;
	}

	private boolean existsByCode(String code) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_knowledge_category
				where code = ?
				""", Long.class, code.trim());
		return count != null && count > 0;
	}

	private boolean existsBySlug(String slug) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_knowledge_article
				where slug = ?
				""", Long.class, slug.trim());
		return count != null && count > 0;
	}

	private boolean relationExists(Long articleId, Long relatedId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_knowledge_article_relation
				where article_id = ?
				and related_article_id = ?
				""", Long.class, articleId, relatedId);
		return count != null && count > 0;
	}

	private Long categoryIdByCode(String code) {
		if (!hasText(code)) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select id
				from sys_knowledge_category
				where code = ?
				""", (rs, rowNum) -> rs.getLong("id"), code.trim()).stream().findFirst().orElse(null);
	}

	private Long articleIdBySlug(String slug) {
		if (!hasText(slug)) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select id
				from sys_knowledge_article
				where slug = ?
				""", (rs, rowNum) -> rs.getLong("id"), slug.trim()).stream().findFirst().orElse(null);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

}
