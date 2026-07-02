package com.qherp.api.system.user;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=task4-user-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserAdminControllerTests extends PostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void adminCanCreateQueryUpdateDisableEnableResetPasswordAndAssignRoles() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long roleId = createRole("USER_TEST_ROLE", "用户测试角色", admin);

		ResponseEntity<String> createResponse = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", "task4-user", "displayName", "任务四用户", "phone", "13800000000",
						"email", "task4-user@example.com", "initialPassword", "Qherp@2026!", "status", "ENABLED",
						"roleIds", List.of(roleId)),
				admin);

		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode user = data(createResponse);
		long userId = user.get("id").longValue();
		assertThat(user.get("username").asText()).isEqualTo("task4-user");
		assertThat(user.get("roles").get(0).get("code").asText()).isEqualTo("USER_TEST_ROLE");
		assertThat(this.jdbcTemplate.queryForObject(
				"select count(*) from sys_audit_log where action = 'USER_CREATE' and target_type = 'USER'",
				Long.class)).isGreaterThanOrEqualTo(1);

		ResponseEntity<String> listResponse = get("/api/admin/users?keyword=task4-user&page=1&pageSize=20", admin);
		assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(listResponse.getBody()).contains("\"username\":\"task4-user\"");
		assertThat(data(listResponse).get("page").intValue()).isEqualTo(1);
		assertThat(data(listResponse).get("items").isArray()).isTrue();

		ResponseEntity<String> detailResponse = get("/api/admin/users/" + userId, admin);
		assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(detailResponse.getBody()).contains("\"displayName\":\"任务四用户\"");

		ResponseEntity<String> updateResponse = exchange(HttpMethod.PUT, "/api/admin/users/" + userId,
				Map.of("displayName", "任务四用户改", "phone", "13900000000", "email", "task4-user-new@example.com",
						"status", "ENABLED", "roleIds", List.of(roleId)),
				admin);
		assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updateResponse.getBody()).contains("\"displayName\":\"任务四用户改\"");

		ResponseEntity<String> resetResponse = exchange(HttpMethod.PUT, "/api/admin/users/" + userId + "/password",
				Map.of("newPassword", "Qherp@2026!New"), admin);
		assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String passwordHash = this.jdbcTemplate.queryForObject("select password_hash from sys_user where id = ?",
				String.class, userId);
		assertThat(this.passwordEncoder.matches("Qherp@2026!New", passwordHash)).isTrue();

		assertThat(exchange(HttpMethod.PUT, "/api/admin/users/" + userId + "/disable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertThat(this.jdbcTemplate.queryForObject("select status from sys_user where id = ?", String.class, userId))
			.isEqualTo("DISABLED");

		assertThat(exchange(HttpMethod.PUT, "/api/admin/users/" + userId + "/enable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertThat(this.jdbcTemplate.queryForObject("select status from sys_user where id = ?", String.class, userId))
			.isEqualTo("ENABLED");
	}

	@Test
	void duplicateUsernameReturnsAuthUsernameExists() {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		Map<String, Object> request = Map.of("username", "duplicate-user", "displayName", "重复用户",
				"initialPassword", "Qherp@2026!", "status", "ENABLED", "roleIds", List.of());

		assertThat(exchange(HttpMethod.POST, "/api/admin/users", request, admin).getStatusCode()).isEqualTo(HttpStatus.OK);
		ResponseEntity<String> duplicate = exchange(HttpMethod.POST, "/api/admin/users", request, admin);

		assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(duplicate.getBody()).contains("\"code\":\"AUTH_USERNAME_EXISTS\"");
	}

	@Test
	void unauthenticatedAndUnauthorizedUsersCannotCreateUser() {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> unauthenticated = this.restTemplate.exchange("/api/admin/users", HttpMethod.POST,
				entity(Map.of("username", "anonymous-user"), csrf.sessionCookie(), csrf), String.class);
		assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(unauthenticated.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");

		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		createUser("readonly-user", List.of(), admin);
		AuthenticatedSession readonly = login("readonly-user", "Qherp@2026!");
		ResponseEntity<String> forbidden = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", "forbidden-user", "displayName", "无权限用户", "initialPassword", "Qherp@2026!",
						"status", "ENABLED", "roleIds", List.of()),
				readonly);

		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(forbidden.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
	}

	private long createRole(String code, String name, AuthenticatedSession session) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/roles",
				Map.of("code", code, "name", name, "description", "测试", "status", "ENABLED"), session);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createUser(String username, List<Long> roleIds, AuthenticatedSession session) {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", username, "displayName", username, "initialPassword", "Qherp@2026!", "status",
						"ENABLED", "roleIds", roleIds),
				session);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		try {
			return data(response).get("id").longValue();
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
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
