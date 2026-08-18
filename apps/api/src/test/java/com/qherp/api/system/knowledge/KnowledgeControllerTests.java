package com.qherp.api.system.knowledge;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=knowledge-controller")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KnowledgeControllerTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final List<String> SEED_CATEGORY_CODES = List.of("COMMON_SYSTEM", "MASTER_MATERIAL_BOM",
			"INVENTORY_QUALITY", "PROCUREMENT", "SALES", "PLANNING_PRODUCTION", "COST_FINANCE_REPORTS",
			"PLATFORM_IMPORT_EXPORT");

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	@AfterEach
	void cleanAutoKnowledgeData() {
		this.jdbcTemplate.update("""
				delete from sys_knowledge_article_relation
				where article_id in (
					select id from sys_knowledge_article where slug like 'kb-auto-article-%'
				)
				or related_article_id in (
					select id from sys_knowledge_article where slug like 'kb-auto-article-%'
				)
				""");
		this.jdbcTemplate.update("delete from sys_knowledge_article where slug like 'kb-auto-article-%'");
		this.jdbcTemplate.update("delete from sys_knowledge_category where code like 'KB_AUTO_%'");
	}

	@Test
	void 普通登录账号可读取启用知识且未登录拒绝() throws Exception {
		AuthenticatedSession viewer = createUserAndLogin("kb-viewer-", List.of());
		long enabledArticleId = firstEnabledArticleId();
		String enabledRoute = firstEnabledRoutePath();

		assertError(get("/api/help/articles?page=1&pageSize=10", null), HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED");

		JsonNode categories = data(get("/api/help/categories", viewer));
		assertSeedCategories(categories);

		JsonNode page = data(get("/api/help/articles?keyword=采购&page=1&pageSize=10", viewer));
		assertPageContract(page);
		assertThat(page.get("items").size()).isGreaterThan(0);
		assertThat(page.get("items").get(0).has("title")).isTrue();

		JsonNode detail = data(get("/api/help/articles/" + enabledArticleId, viewer));
		assertThat(detail.get("status").asText()).isEqualTo("ENABLED");
		assertThat(detail.get("content").asText()).isNotBlank();

		ResponseEntity<String> byRouteResponse = get(
				"/api/help/articles/by-route?routePath=" + enabledRoute + "&page=1&pageSize=10", viewer);
		JsonNode byRoute = data(byRouteResponse);
		assertPageContract(byRoute);
		assertThat(byRoute.get("items").size()).as("by-route routePath=%s 完整响应: %s", enabledRoute,
				byRouteResponse.getBody()).isGreaterThan(0);

		JsonNode related = data(get("/api/help/articles/" + enabledArticleId + "/related", viewer));
		assertThat(related.isArray()).isTrue();
	}

	@Test
	void 普通账号不能调用管理接口且管理员维护保存后立即影响只读查询并记录审计() throws Exception {
		AuthenticatedSession viewer = createUserAndLogin("kb-no-manage-", List.of());
		assertError(get("/api/admin/system/knowledge/articles?page=1&pageSize=10", viewer), HttpStatus.FORBIDDEN,
				"AUTH_FORBIDDEN");
		assertError(exchange(HttpMethod.POST, "/api/admin/system/knowledge/categories",
				Map.of("code", "KB_DENIED", "name", "应拒绝", "sortOrder", 100, "status", "ENABLED"), viewer),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		String categoryCode = "KB_AUTO_" + suffix;
		String slug = "kb-auto-article-" + suffix;
		long relatedId = firstEnabledArticleId();

		JsonNode category = data(exchange(HttpMethod.POST, "/api/admin/system/knowledge/categories",
				Map.of("code", categoryCode, "name", "自动化分类" + suffix, "sortOrder", 900 + suffix,
						"status", "ENABLED"), admin));
		long categoryId = category.get("id").longValue();
		assertThat(category.has("parentCode")).isTrue();
		assertThat(category.get("parentCode").isNull()).isTrue();

		Map<String, Object> payload = articlePayload(slug, "自动化知识标题" + suffix, "自动化知识摘要",
				categoryId, "PAGE", "# 功能用途\n用于验证知识库 CRUD。", "自动化,CRUD", relatedId, 1);
		JsonNode created = data(exchange(HttpMethod.POST, "/api/admin/system/knowledge/articles", payload, admin));
		long articleId = created.get("id").longValue();
		assertThat(created.get("relatedArticleIds")).hasSize(1);

		JsonNode visible = data(get("/api/help/articles/" + articleId, viewer));
		assertThat(visible.get("slug").asText()).isEqualTo(slug);

		JsonNode disabled = data(exchange(HttpMethod.POST,
				"/api/admin/system/knowledge/articles/" + articleId + "/disable", Map.of(), admin));
		assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");
		assertError(get("/api/help/articles/" + articleId, viewer), HttpStatus.NOT_FOUND, "KNOWLEDGE_ARTICLE_NOT_FOUND");

		JsonNode enabled = data(exchange(HttpMethod.POST,
				"/api/admin/system/knowledge/articles/" + articleId + "/enable", Map.of(), admin));
		assertThat(enabled.get("status").asText()).isEqualTo("ENABLED");
		assertThat(data(get("/api/help/articles/" + articleId, viewer)).get("slug").asText()).isEqualTo(slug);

		Map<String, Object> updatedPayload = articlePayload(slug, "自动化知识标题已更新" + suffix,
				"自动化知识摘要已更新", categoryId, "PROCESS", "# 操作步骤\n1. 更新后立即生效。", "自动化,更新",
				relatedId, 2);
		JsonNode updated = data(exchange(HttpMethod.PUT, "/api/admin/system/knowledge/articles/" + articleId,
				updatedPayload, admin));
		assertThat(updated.get("title").asText()).contains("已更新");
		assertThat(data(get("/api/help/articles/" + articleId, viewer)).get("title").asText()).contains("已更新");

		assertError(exchange(HttpMethod.DELETE, "/api/admin/system/knowledge/categories/" + categoryId, null, admin),
				HttpStatus.CONFLICT, "KNOWLEDGE_CATEGORY_IN_USE");
		assertThat(exchange(HttpMethod.DELETE, "/api/admin/system/knowledge/articles/" + articleId, null, admin)
				.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(exchange(HttpMethod.DELETE, "/api/admin/system/knowledge/categories/" + categoryId, null, admin)
				.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(auditCount("KNOWLEDGE_ARTICLE_CREATE", articleId)).isOne();
		assertThat(auditCount("KNOWLEDGE_ARTICLE_UPDATE", articleId)).isOne();
		assertThat(auditCount("KNOWLEDGE_ARTICLE_DISABLE", articleId)).isOne();
		assertThat(auditCount("KNOWLEDGE_ARTICLE_ENABLE", articleId)).isOne();
	}

	@Test
	void 管理列表和只读列表都必须返回items分页字段() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode publicPage = data(get("/api/help/articles?page=1&pageSize=20", admin));
		JsonNode adminPage = data(get("/api/admin/system/knowledge/articles?page=1&pageSize=20&status=ENABLED", admin));

		assertPageContract(publicPage);
		assertPageContract(adminPage);
		assertThat(publicPage.get("items").size()).isLessThanOrEqualTo(20);
		assertThat(adminPage.get("items").size()).isLessThanOrEqualTo(20);
	}

	private void assertPageContract(JsonNode page) {
		assertThat(page.has("items")).as("分页响应必须提供 items 字段，实际响应: %s", page).isTrue();
		assertThat(page.has("total")).as("分页响应必须提供 total 字段，实际响应: %s", page).isTrue();
		assertThat(page.has("page")).as("分页响应必须提供 page 字段，实际响应: %s", page).isTrue();
		assertThat(page.has("pageSize")).as("分页响应必须提供 pageSize 字段，实际响应: %s", page).isTrue();
		assertThat(page.has("totalPages")).as("分页响应必须提供 totalPages 字段，实际响应: %s", page).isTrue();
		assertThat(page.get("items").isArray()).isTrue();
	}

	private void assertSeedCategories(JsonNode categories) {
		List<String> codes = new ArrayList<>();
		for (JsonNode category : categories) {
			assertThat(category.has("parentCode")).isTrue();
			assertThat(category.get("parentCode").isNull()).isTrue();
			codes.add(category.get("code").asText());
		}
		assertThat(codes).doesNotContain("KB_AUTO_1", "KB_AUTO_2");
		assertThat(codes).containsExactlyInAnyOrderElementsOf(SEED_CATEGORY_CODES);
	}

	private long firstEnabledArticleId() {
		return this.jdbcTemplate.queryForObject("""
				select id
				from sys_knowledge_article
				where status = 'ENABLED'
				order by id
				limit 1
				""", Long.class);
	}

	private String firstEnabledRoutePath() {
		return this.jdbcTemplate.queryForObject("""
				select trim(route_path)
				from sys_knowledge_article a
				cross join regexp_split_to_table(coalesce(a.route_paths, ''), E'\\r?\\n') route_path
				where a.status = 'ENABLED'
				and trim(route_path) <> ''
				order by a.id
				limit 1
				""", String.class);
	}

	private Map<String, Object> articlePayload(String slug, String title, String summary, long categoryId,
			String knowledgeType, String content, String keywords, long relatedId, int sortOrder) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("slug", slug);
		payload.put("title", title);
		payload.put("summary", summary);
		payload.put("categoryId", categoryId);
		payload.put("knowledgeType", knowledgeType);
		payload.put("content", content);
		payload.put("keywords", keywords);
		payload.put("routePaths", "/test/knowledge-auto");
		payload.put("pageNames", "自动化页面");
		payload.put("permissionNote", "所有登录用户可读");
		payload.put("relatedArticleIds", List.of(relatedId));
		payload.put("sortOrder", sortOrder);
		payload.put("status", "ENABLED");
		return payload;
	}

	private AuthenticatedSession createUserAndLogin(String usernamePrefix, List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		Long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "KB_ROLE_" + suffix, "知识库测试角色" + suffix);
		Long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), username);
		this.jdbcTemplate.update("insert into sys_user_role (user_id, role_id, created_by, created_at) values (?, ?, 'test', now())",
				userId, roleId);
		for (String permissionCode : permissionCodes) {
			this.jdbcTemplate.update("""
					insert into sys_role_permission (role_id, permission_id, created_by, created_at)
					select ?, id, 'test', now()
					from sys_permission
					where code = ?
					""", roleId, permissionCode);
		}
		return login(username, ADMIN_PASSWORD);
	}

	private AuthenticatedSession login(String username, String password) {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrf);
	}

	private CsrfSession csrfSession() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		try {
			JsonNode data = data(response);
			return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET,
				entity(null, session == null ? null : session.sessionCookie(), null), String.class);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()),
				String.class);
	}

	private HttpEntity<Object> entity(Object body, String cookie, CsrfSession csrf) {
		HttpHeaders headers = new HttpHeaders();
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		if (csrf != null) {
			headers.add(csrf.headerName(), csrf.token());
		}
		return new HttpEntity<>(body, headers);
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as("响应状态非 OK，完整 body: %s", response.getBody())
			.isEqualTo(HttpStatus.OK);
		JsonNode body = this.objectMapper.readTree(response.getBody());
		assertThat(body.get("code").asText()).as("响应业务码非 OK，完整 body: %s", response.getBody()).isEqualTo("OK");
		return body.get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String expectedCode) throws Exception {
		assertThat(response.getStatusCode()).as("错误响应状态不符合预期，完整 body: %s", response.getBody())
			.isEqualTo(status);
		assertThat(code(response)).as("错误业务码不符合预期，完整 body: %s", response.getBody()).isEqualTo(expectedCode);
	}

	private long auditCount(String action, long targetId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				and target_type = 'KNOWLEDGE_ARTICLE'
				and target_id = ?
				and result = 'SUCCESS'
				""", Long.class, action, String.valueOf(targetId));
	}

	private String sessionCookie(ResponseEntity<String> response) {
		return response.getHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE)
			.stream()
			.filter((cookie) -> cookie.startsWith("JSESSIONID="))
			.findFirst()
			.map((cookie) -> cookie.split(";", 2)[0])
			.orElseThrow();
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}

}
