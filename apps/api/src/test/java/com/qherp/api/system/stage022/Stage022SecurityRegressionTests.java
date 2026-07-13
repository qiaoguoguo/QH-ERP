package com.qherp.api.system.stage022;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=stage022-security-regression")
@AutoConfigureTestRestTemplate
@DirtiesContext
class Stage022SecurityRegressionTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Admin@123456";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void messageListDoesNotExposeBusinessObjectAfterViewPermissionIsLost() throws Exception {
		TestUser user = createUserAndLogin("stage022-message-lost-contract", "S22_MSG_LOST",
				List.of("platform:message:view", "platform:message:read"));
		long messageId = insertContractMessage(user.userId(), "敏感合同审批结果", "合同 C-022-SECRET 已通过");

		JsonNode page = data(get(user.session(), "/api/admin/messages/my?unreadOnly=false&page=1&pageSize=20"));

		assertThat(page.get("total").longValue()).isZero();
		assertThat(page.get("items")).isEmpty();
		assertError(put(user.session(), "/api/admin/messages/" + messageId + "/read", Map.of("version", 0)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	@Test
	void permissionDeniedRequestsAreAudited() throws Exception {
		TestUser user = createUserAndLogin("stage022-denied-audit", "S22_DENIED_AUDIT",
				List.of("platform:message:view"));

		ResponseEntity<String> response = get(user.session(), "/api/admin/print-templates?objectType=APPROVAL_INSTANCE");

		assertError(response, HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertThat(deniedAuditCount(user.username(), "GET", "/api/admin/print-templates", "AUTH_FORBIDDEN")).isOne();
	}

	private long insertContractMessage(long recipientUserId, String title, String content) {
		return this.jdbcTemplate.queryForObject("""
				insert into platform_message (
					recipient_user_id, title, content, message_type, status, related_object_type,
					related_object_id, created_at
				)
				values (?, ?, ?, 'APPROVAL_DONE', 'UNREAD', 'SALES_PROJECT_CONTRACT', ?, now())
				returning id
				""", Long.class, recipientUserId, title, content, 900000L + SEQUENCE.incrementAndGet());
	}

	private TestUser createUserAndLogin(String usernamePrefix, String rolePrefix, List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "022 安全回归角色" + suffix, "022 安全回归角色");
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), username);
		this.jdbcTemplate.update(
				"insert into sys_user_role (user_id, role_id, created_by, created_at) values (?, ?, 'test', now())",
				userId, roleId);
		for (String permissionCode : permissionCodes) {
			this.jdbcTemplate.update("""
					insert into sys_role_permission (role_id, permission_id, created_by, created_at)
					select ?, id, 'test', now()
					from sys_permission
					where code = ?
					""", roleId, permissionCode);
		}
		return new TestUser(userId, username, login(username, ADMIN_PASSWORD));
	}

	private long deniedAuditCount(String username, String method, String path, String errorCode) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where operator_username = ?
				and request_method = ?
				and request_path = ?
				and result = 'FAILURE'
				and error_code = ?
				""", Long.class, username, method, path, errorCode);
	}

	private AuthenticatedSession login(String username, String password) {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				new HttpEntity<>(Map.of("username", username, "password", password),
						headers(csrf.sessionCookie(), csrf.headerName(), csrf.token())),
				String.class);
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

	private ResponseEntity<String> get(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(session)), String.class);
	}

	private ResponseEntity<String> put(AuthenticatedSession session, String path, Object body) {
		return this.restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers(session)), String.class);
	}

	private HttpHeaders headers(AuthenticatedSession session) {
		return headers(session.sessionCookie(), session.csrf().headerName(), session.csrf().token());
	}

	private HttpHeaders headers(String cookie, String csrfHeaderName, String csrfToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, cookie);
		headers.add(csrfHeaderName, csrfToken);
		return headers;
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(code);
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

	private record TestUser(long userId, String username, AuthenticatedSession session) {
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrf) {
	}

}
