package com.qherp.api.system.knowledge;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "qherp.test.context=knowledge-migration-regression")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KnowledgeMigrationRegressionTests extends PostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private KnowledgeSeedInitializer seedInitializer;

	private static final List<String> REPORT_ROUTES = List.of("/reports/overview", "/reports/sales",
			"/reports/procurement", "/reports/inventory", "/reports/production", "/reports/cost",
			"/reports/settlement", "/reports/exceptions", "/reports/project-profit",
			"/reports/project-profit/:projectId", "/reports/contract-collection", "/reports/procurement-variance",
			"/reports/inventory-capital", "/reports/receivable-payable", "/reports/operating-accounting",
			"/reports/financial-summary");

	private static final List<String> KNOWLEDGE_MANAGEMENT_ROUTES = List.of("/system/knowledge",
			"/system/knowledge/create", "/system/knowledge/:id/edit");

	@Test
	void V41迁移必须创建知识表约束索引和系统管理员权限种子() {
		assertThat(tableExists("sys_knowledge_category")).isTrue();
		assertThat(tableExists("sys_knowledge_article")).isTrue();
		assertThat(tableExists("sys_knowledge_article_relation")).isTrue();

		assertThat(constraintExists("uk_sys_knowledge_category_code")).isTrue();
		assertThat(constraintExists("ck_sys_knowledge_category_status")).isTrue();
		assertThat(constraintExists("fk_sys_knowledge_category_parent")).isTrue();
		assertThat(constraintExists("uk_sys_knowledge_article_slug")).isTrue();
		assertThat(constraintExists("ck_sys_knowledge_article_status")).isTrue();
		assertThat(constraintExists("ck_sys_knowledge_article_type")).isTrue();
		assertThat(constraintExists("fk_sys_knowledge_article_category")).isTrue();
		assertThat(constraintExists("pk_sys_knowledge_article_relation")).isTrue();
		assertThat(constraintExists("ck_sys_knowledge_article_relation_self")).isTrue();

		assertThat(indexExists("idx_sys_knowledge_category_parent_sort")).isTrue();
		assertThat(indexExists("idx_sys_knowledge_category_status_sort")).isTrue();
		assertThat(indexExists("idx_sys_knowledge_article_status_sort")).isTrue();
		assertThat(indexExists("idx_sys_knowledge_article_category_status")).isTrue();
		assertThat(indexExists("idx_sys_knowledge_article_type_status")).isTrue();
		assertThat(indexExists("idx_sys_knowledge_article_updated_at")).isTrue();
		assertThat(indexExists("idx_sys_knowledge_article_relation_related")).isTrue();

		assertThat(permissionCount("system:knowledge")).isOne();
		assertThat(permissionCount("system:knowledge:manage")).isOne();
		assertThat(systemAdminPermissionCount()).isEqualTo(2L);
	}

	@Test
	void 初始知识资源必须为8个分类63篇文章46题且分类和关联引用有效并完成装载() throws Exception {
		SeedResources resources = readSeedResources();
		assertThat(resources.categories()).hasSize(8);
		assertThat(resources.articles()).hasSize(63);
		assertThat(evaluationQuestionCount()).isEqualTo(46);

		Set<String> categoryCodes = new HashSet<>();
		for (JsonNode category : resources.categories()) {
			String code = text(category, "code");
			assertThat(code).isNotBlank();
			assertThat(text(category, "name")).isNotBlank();
			assertThat(categoryCodes.add(code)).as("分类编码不能重复：" + code).isTrue();
		}

		Set<String> articleSlugs = new HashSet<>();
		for (JsonNode article : resources.articles()) {
			String slug = text(article, "slug");
			assertThat(slug).isNotBlank();
			assertThat(articleSlugs.add(slug)).as("知识标识不能重复：" + slug).isTrue();
			assertThat(text(article, "title")).isNotBlank();
			assertThat(text(article, "summary")).isNotBlank();
			assertThat(text(article, "content")).isNotBlank();
			assertThat(text(article, "knowledgeType")).isIn("PAGE", "PROCESS", "FIELD", "STATUS", "ERROR",
					"PERMISSION", "IMPORT_EXPORT", "CONCEPT");
			assertThat(categoryCodes).contains(text(article, "categoryCode"));
		}
		for (JsonNode article : resources.articles()) {
			JsonNode related = article.get("relatedSlugs");
			if (related == null || !related.isArray()) {
				continue;
			}
			for (JsonNode relatedSlug : related) {
				assertThat(articleSlugs).contains(relatedSlug.asText());
				assertThat(relatedSlug.asText()).isNotEqualTo(text(article, "slug"));
			}
		}
		assertArticleRoutes(resources, "reports-overview", REPORT_ROUTES);
		assertArticleRoutes(resources, "report-permissions-data-boundary", REPORT_ROUTES);
		assertArticleRoutes(resources, "system-knowledge-management", KNOWLEDGE_MANAGEMENT_ROUTES);

		assertThat(count("sys_knowledge_category")).isEqualTo(8L);
		assertThat(count("sys_knowledge_article")).isEqualTo(63L);
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_knowledge_article_relation r
				left join sys_knowledge_article a on a.id = r.article_id
				left join sys_knowledge_article related on related.id = r.related_article_id
				where a.id is null or related.id is null or a.id = related.id
				""", Long.class)).isZero();
	}

	@Test
	void 种子装载不得覆盖管理员已修改内容() throws Exception {
		String slug = this.jdbcTemplate.queryForObject("""
				select slug
				from sys_knowledge_article
				order by id
				limit 1
				""", String.class);
		this.jdbcTemplate.update("""
				update sys_knowledge_article
				set title = '管理员已修改标题', summary = '管理员已修改摘要', status = 'DISABLED', updated_by = 'admin', version = version + 1
				where slug = ?
				""", slug);

		this.seedInitializer.run(null);

		JsonNode article = this.jdbcTemplate.queryForObject("""
				select title, summary, status, updated_by
				from sys_knowledge_article
				where slug = ?
				""", (rs, rowNum) -> this.objectMapper.readTree("""
				{"title":"%s","summary":"%s","status":"%s","updatedBy":"%s"}
				""".formatted(rs.getString("title"), rs.getString("summary"), rs.getString("status"),
					rs.getString("updated_by"))), slug);
		assertThat(article.get("title").asText()).isEqualTo("管理员已修改标题");
		assertThat(article.get("summary").asText()).isEqualTo("管理员已修改摘要");
		assertThat(article.get("status").asText()).isEqualTo("DISABLED");
		assertThat(article.get("updatedBy").asText()).isEqualTo("admin");
	}

	private SeedResources readSeedResources() throws Exception {
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource categoryResource = resolver.getResource("classpath:/knowledge/categories.json");
		List<JsonNode> categories = jsonArray(categoryResource, "categories");
		List<JsonNode> articles = new ArrayList<>();
		for (Resource resource : resolver.getResources("classpath*:/knowledge/*.json")) {
			if (resource.getFilename() == null || "categories.json".equals(resource.getFilename())) {
				continue;
			}
			articles.addAll(jsonArray(resource, "articles"));
		}
		return new SeedResources(categories, articles);
	}

	private List<JsonNode> jsonArray(Resource resource, String fieldName) throws Exception {
		JsonNode root = this.objectMapper.readTree(resource.getInputStream());
		JsonNode array = root.isArray() ? root : root.get(fieldName);
		List<JsonNode> nodes = new ArrayList<>();
		if (array != null && array.isArray()) {
			for (JsonNode node : array) {
				nodes.add(node);
			}
		}
		else if (root.has("slug")) {
			nodes.add(root);
		}
		return nodes;
	}

	private boolean tableExists(String tableName) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from information_schema.tables
				where table_schema = 'public'
				and table_name = ?
				""", Long.class, tableName) == 1L;
	}

	private boolean constraintExists(String constraintName) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from pg_constraint
				where conname = ?
				""", Long.class, constraintName) == 1L;
	}

	private boolean indexExists(String indexName) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from pg_indexes
				where schemaname = 'public'
				and indexname = ?
				""", Long.class, indexName) == 1L;
	}

	private long permissionCount(String code) {
		return this.jdbcTemplate.queryForObject("select count(*) from sys_permission where code = ?", Long.class, code);
	}

	private long systemAdminPermissionCount() {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_role_permission rp
				join sys_role r on r.id = rp.role_id
				join sys_permission p on p.id = rp.permission_id
				where r.code = 'SYSTEM_ADMIN'
				and p.code in ('system:knowledge', 'system:knowledge:manage')
				""", Long.class);
	}

	private long count(String tableName) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
	}

	private static String text(JsonNode node, String fieldName) {
		JsonNode value = node.get(fieldName);
		return value == null || value.isNull() ? "" : value.asText();
	}

	private static void assertArticleRoutes(SeedResources resources, String slug, List<String> expectedRoutes) {
		JsonNode article = resources.articles()
			.stream()
			.filter((candidate) -> slug.equals(text(candidate, "slug")))
			.findFirst()
			.orElseThrow(() -> new AssertionError("缺少知识文章：" + slug));
		List<String> routePaths = text(article, "routePaths")
			.replace("\r\n", "\n")
			.lines()
			.map(String::trim)
			.filter((line) -> !line.isEmpty())
			.toList();
		assertThat(routePaths).hasSize(expectedRoutes.size());
		assertThat(routePaths).containsExactlyInAnyOrderElementsOf(expectedRoutes);
	}

	private static long evaluationQuestionCount() throws Exception {
		return Files.readAllLines(Path.of("..", "..", "docs", "knowledge-base", "evaluation-questions.md"))
			.stream()
			.filter((line) -> line.matches("^\\| Q\\d+.*"))
			.count();
	}

	private record SeedResources(List<JsonNode> categories, List<JsonNode> articles) {
	}

}
