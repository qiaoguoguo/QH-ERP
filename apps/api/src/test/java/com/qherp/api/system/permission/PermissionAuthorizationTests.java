package com.qherp.api.system.permission;

import com.qherp.api.support.PostgresIntegrationTest;
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
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=task4-permission-authorization")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PermissionAuthorizationTests extends PostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void permissionTreeReturnsMenuAndActionNodesAndAuditLogCanBeQueried() {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");

		ResponseEntity<String> tree = get("/api/admin/permissions/tree", admin);
		assertThat(tree.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(tree.getBody()).contains("\"code\":\"system\"");
		assertThat(tree.getBody()).contains("\"type\":\"MENU\"");
		assertThat(tree.getBody()).contains("\"code\":\"system:user:view\"");
		assertThat(tree.getBody()).contains("\"children\"");

		ResponseEntity<String> audit = get("/api/admin/audit-logs?page=1&pageSize=20", admin);
		assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(audit.getBody()).contains("\"items\"");
	}

	@Test
	void auditLogQueryFiltersByOperatorKeywordAndCreatedAtRange() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		insertAuditLog("admin", "TASK4_AUDIT_FILTER", "TASK4_AUDIT", now.minusMinutes(10));
		insertAuditLog("other-operator", "TASK4_AUDIT_FILTER", "TASK4_AUDIT", now.minusMinutes(10));
		insertAuditLog("admin", "TASK4_AUDIT_FILTER", "TASK4_AUDIT", now.minusDays(2));

		ResponseEntity<String> audit = get("/api/admin/audit-logs?operatorKeyword=adm&targetType=TASK4_AUDIT"
				+ "&action=TASK4_AUDIT_FILTER&startAt=" + now.minusHours(1) + "&endAt=" + now.plusHours(1)
				+ "&page=1&pageSize=20", admin);

		assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode data = data(audit);
		assertThat(data.get("total").longValue()).isOne();
		JsonNode item = data.get("items").get(0);
		assertThat(item.get("operatorUsername").asText()).isEqualTo("admin");
		assertThat(item.get("action").asText()).isEqualTo("TASK4_AUDIT_FILTER");
		assertThat(item.get("targetType").asText()).isEqualTo("TASK4_AUDIT");
	}

	@Test
	void permissionChangesAndRoleDisabledAffectAuthorizationImmediately() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long roleId = createRole("LIMITED_ROLE", "受限角色", admin);
		long userId = createUser("limited-user", List.of(roleId), admin);
		AuthenticatedSession limited = login("limited-user", "Qherp@2026!");

		ResponseEntity<String> noPermission = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", "should-forbid", "displayName", "应拒绝", "initialPassword", "Qherp@2026!",
						"status", "ENABLED", "roleIds", List.of()),
				limited);
		assertThat(noPermission.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(noPermission.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");

		exchange(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/permissions",
				Map.of("permissionIds", List.of(permissionId("system:user:create"))), admin);
		ResponseEntity<String> allowed = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", "allowed-by-role", "displayName", "授权成功", "initialPassword", "Qherp@2026!",
						"status", "ENABLED", "roleIds", List.of()),
				limited);
		assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);

		exchange(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/disable", Map.of(), admin);
		ResponseEntity<String> forbiddenAfterRoleDisabled = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", "blocked-by-role", "displayName", "角色停用", "initialPassword", "Qherp@2026!",
						"status", "ENABLED", "roleIds", List.of()),
				limited);
		assertThat(forbiddenAfterRoleDisabled.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		exchange(HttpMethod.PUT, "/api/admin/users/" + userId + "/disable", Map.of(), admin);
		ResponseEntity<String> unauthorizedAfterUserDisabled = get("/api/admin/permissions/tree", limited);
		assertThat(unauthorizedAfterUserDisabled.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(unauthorizedAfterUserDisabled.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
	}

	@Test
	void unmappedAdminPathIsForbiddenAndPathVariableRequestStillMatchesPermission() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		ResponseEntity<String> unmapped = get("/api/admin/unmapped-path", admin);
		assertThat(unmapped.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(unmapped.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");

		long roleId = createRole("USER_VIEW_ONLY_ROLE", "用户查看角色", admin);
		exchange(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/permissions",
				Map.of("permissionIds", List.of(permissionId("system:user:view"))), admin);
		long targetUserId = createUser("path-variable-target", List.of(), admin);
		createUser("path-variable-viewer", List.of(roleId), admin);
		AuthenticatedSession viewer = login("path-variable-viewer", "Qherp@2026!");

		ResponseEntity<String> detail = get("/api/admin/users/" + targetUserId, viewer);

		assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(detail.getBody()).contains("\"username\":\"path-variable-target\"");
	}

	private long createRole(String code, String name, AuthenticatedSession session) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/roles",
				Map.of("code", code, "name", name, "description", "测试", "status", "ENABLED"), session);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createUser(String username, List<Long> roleIds, AuthenticatedSession session) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", username, "displayName", username, "initialPassword", "Qherp@2026!", "status",
						"ENABLED", "roleIds", roleIds),
				session);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long permissionId(String code) {
		return this.jdbcTemplate.queryForObject("select id from sys_permission where code = ?", Long.class, code);
	}

	private void insertAuditLog(String operatorUsername, String action, String targetType, OffsetDateTime createdAt) {
		this.jdbcTemplate.update("""
				insert into sys_audit_log (
					operator_username, action, target_type, target_id, target_summary,
					request_method, request_path, ip_address, result, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", operatorUsername, action, targetType, "task4-audit-filter", "审计过滤测试", "GET",
				"/api/admin/audit-logs", "127.0.0.1", "SUCCESS", createdAt);
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
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null), String.class);
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
		return this.objectMapper.readTree(response.getBody()).get("data");
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
