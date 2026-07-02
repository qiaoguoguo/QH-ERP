package com.qherp.api.system.role;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=task4-role-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoleAdminControllerTests extends PostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void adminCanCreateUpdateDisableEnableAndSaveRolePermissions() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long permissionId = permissionId("system:user:view");

		ResponseEntity<String> createResponse = exchange(HttpMethod.POST, "/api/admin/roles",
				Map.of("code", "TASK4_ROLE", "name", "任务四角色", "description", "角色说明", "status", "ENABLED"),
				admin);
		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		long roleId = data(createResponse).get("id").longValue();

		ResponseEntity<String> updateResponse = exchange(HttpMethod.PUT, "/api/admin/roles/" + roleId,
				Map.of("name", "任务四角色改", "description", "更新说明", "status", "ENABLED"), admin);
		assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updateResponse.getBody()).contains("\"name\":\"任务四角色改\"");

		ResponseEntity<String> permissionsResponse = exchange(HttpMethod.PUT,
				"/api/admin/roles/" + roleId + "/permissions", Map.of("permissionIds", List.of(permissionId)), admin);
		assertThat(permissionsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(this.jdbcTemplate.queryForObject(
				"select count(*) from sys_role_permission where role_id = ? and permission_id = ?", Long.class,
				roleId, permissionId)).isOne();
		assertThat(this.jdbcTemplate.queryForObject(
				"select count(*) from sys_audit_log where action = 'ROLE_PERMISSION_UPDATE' and target_type = 'ROLE'",
				Long.class)).isGreaterThanOrEqualTo(1);

		assertThat(exchange(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/disable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertThat(this.jdbcTemplate.queryForObject("select status from sys_role where id = ?", String.class, roleId))
			.isEqualTo("DISABLED");

		assertThat(exchange(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/enable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertThat(this.jdbcTemplate.queryForObject("select status from sys_role where id = ?", String.class, roleId))
			.isEqualTo("ENABLED");

		ResponseEntity<String> detail = get("/api/admin/roles/" + roleId, admin);
		assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(detail.getBody()).contains("\"code\":\"TASK4_ROLE\"");
		assertThat(detail.getBody()).contains("\"permissionIds\"");

		ResponseEntity<String> list = get("/api/admin/roles?keyword=TASK4_ROLE&page=1&pageSize=20", admin);
		assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(list.getBody()).contains("\"code\":\"TASK4_ROLE\"");
	}

	@Test
	void duplicateRoleCodeReturnsAuthRoleCodeExists() {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		Map<String, String> request = Map.of("code", "DUP_ROLE", "name", "重复角色", "description", "重复",
				"status", "ENABLED");

		assertThat(exchange(HttpMethod.POST, "/api/admin/roles", request, admin).getStatusCode()).isEqualTo(HttpStatus.OK);
		ResponseEntity<String> duplicate = exchange(HttpMethod.POST, "/api/admin/roles", request, admin);

		assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(duplicate.getBody()).contains("\"code\":\"AUTH_ROLE_CODE_EXISTS\"");
	}

	private long permissionId(String code) {
		return this.jdbcTemplate.queryForObject("select id from sys_permission where code = ?", Long.class, code);
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
