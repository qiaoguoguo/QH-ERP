package com.qherp.api.auth;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.user.SystemUser;
import com.qherp.api.system.user.SystemUserRepository;
import com.qherp.api.system.user.SystemUserStatus;
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
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerTests extends PostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private SystemUserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void loginReturnsCurrentUserAndSessionCookie() {
		ResponseEntity<String> response = login(csrfSession(), "admin", "Qherp@2026!");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNotEmpty();
		assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
			.anySatisfy((cookie) -> assertThat(cookie).contains("JSESSIONID=", "HttpOnly"));
		assertThat(response.getBody()).contains("\"success\":true");
		assertThat(response.getBody()).contains("\"code\":\"OK\"");
		assertThat(response.getBody()).contains("\"user\"");
		assertThat(response.getBody()).contains("\"username\":\"admin\"");
		assertThat(response.getBody()).contains("\"roles\"");
		assertThat(response.getBody()).contains("\"SYSTEM_ADMIN\"");
		assertThat(response.getBody()).contains("\"permissions\"");
		assertThat(response.getBody()).contains("\"system:user:view\"");
		assertThat(response.getBody()).contains("\"menus\"");
	}

	@Test
	void loginRequiresCsrfToken() {
		ResponseEntity<String> response = loginWithoutCsrf("admin", "Qherp@2026!");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"success\":false");
		assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
	}

	@Test
	void loginChangesExistingSessionId() {
		CsrfSession csrfSession = csrfSession();
		String beforeLoginSessionId = sessionId(csrfSession.sessionCookie());

		ResponseEntity<String> response = login(csrfSession, "admin", "Qherp@2026!");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(sessionId(sessionCookie(response))).isNotEqualTo(beforeLoginSessionId);
	}

	@Test
	void currentUserRequiresLogin() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/me", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"success\":false");
		assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
	}

	@Test
	void currentUserReturnsSessionUserAfterLogin() {
		AuthenticatedSession session = loginSession("admin", "Qherp@2026!");

		ResponseEntity<String> response = this.restTemplate.exchange("/api/auth/me", HttpMethod.GET,
				withCookie(session.sessionCookie()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"success\":true");
		assertThat(response.getBody()).contains("\"username\":\"admin\"");
		assertThat(response.getBody()).contains("\"SYSTEM_ADMIN\"");
		assertThat(response.getBody()).contains("\"system:user:view\"");
	}

	@Test
	void logoutClearsSession() {
		AuthenticatedSession session = loginSession("admin", "Qherp@2026!");

		ResponseEntity<String> logoutResponse = this.restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
				withCookieAndCsrf(session.sessionCookie(), session.csrfSession()), String.class);
		ResponseEntity<String> meResponse = this.restTemplate.exchange("/api/auth/me", HttpMethod.GET,
				withCookie(session.sessionCookie()), String.class);

		assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(logoutResponse.getBody()).contains("\"success\":true");
		assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(meResponse.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
	}

	@Test
	void logoutRequiresCsrfToken() {
		AuthenticatedSession session = loginSession("admin", "Qherp@2026!");

		ResponseEntity<String> response = this.restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
				withCookie(session.sessionCookie()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"success\":false");
		assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
	}

	@Test
	void disabledUserCannotLogin() {
		ensureDisabledUser();

		ResponseEntity<String> response = login(csrfSession(), "disabled-user", "Qherp@2026!");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"success\":false");
		assertThat(response.getBody()).contains("\"code\":\"AUTH_ACCOUNT_DISABLED\"");
	}

	@Test
	void invalidCredentialsReturnUnauthorizedEnvelope() {
		ResponseEntity<String> wrongPassword = login(csrfSession(), "admin", "wrong-password");
		ResponseEntity<String> missingUser = login(csrfSession(), "missing-user", "Qherp@2026!");

		assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(wrongPassword.getBody()).contains("\"success\":false");
		assertThat(wrongPassword.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
		assertThat(wrongPassword.getBody()).doesNotContain("admin");

		assertThat(missingUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(missingUser.getBody()).contains("\"success\":false");
		assertThat(missingUser.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
		assertThat(missingUser.getBody()).doesNotContain("missing-user");
	}

	@Test
	void currentUserRejectsSessionAfterAccountDisabled() {
		ensureEnabledUser("session-disabled-user");
		AuthenticatedSession session = loginSession("session-disabled-user", "Qherp@2026!");

		disableUser("session-disabled-user");
		ResponseEntity<String> response = this.restTemplate.exchange("/api/auth/me", HttpMethod.GET,
				withCookie(session.sessionCookie()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"success\":false");
		assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
	}

	private AuthenticatedSession loginSession(String username, String password) {
		CsrfSession csrfSession = csrfSession();
		ResponseEntity<String> response = login(csrfSession, username, password);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrfSession);
	}

	private ResponseEntity<String> login(CsrfSession csrfSession, String username, String password) {
		return this.restTemplate.postForEntity("/api/auth/login",
				withCookieAndCsrf(Map.of("username", username, "password", password), csrfSession), String.class);
	}

	private ResponseEntity<String> loginWithoutCsrf(String username, String password) {
		return this.restTemplate.postForEntity("/api/auth/login", Map.of("username", username, "password", password),
				String.class);
	}

	private HttpEntity<Void> withCookie(String sessionCookie) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, sessionCookie);
		return new HttpEntity<>(headers);
	}

	private HttpEntity<Void> withCookieAndCsrf(String sessionCookie, CsrfSession csrfSession) {
		HttpHeaders headers = csrfHeaders(csrfSession);
		headers.add(HttpHeaders.COOKIE, sessionCookie);
		return new HttpEntity<>(headers);
	}

	private HttpEntity<Map<String, String>> withCookieAndCsrf(Map<String, String> body, CsrfSession csrfSession) {
		HttpHeaders headers = csrfHeaders(csrfSession);
		headers.add(HttpHeaders.COOKIE, csrfSession.sessionCookie());
		return new HttpEntity<>(body, headers);
	}

	private HttpHeaders csrfHeaders(CsrfSession csrfSession) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(csrfSession.headerName(), csrfSession.token());
		return headers;
	}

	private CsrfSession csrfSession() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		try {
			var data = this.objectMapper.readTree(response.getBody()).get("data");
			return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText(),
					data.get("parameterName").asText());
		}
		catch (Exception exception) {
			throw new AssertionError("无法解析 CSRF 响应", exception);
		}
	}

	private String sessionCookie(ResponseEntity<String> loginResponse) {
		List<String> cookies = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
		assertThat(cookies).isNotEmpty();
		return cookies.stream()
			.filter((cookie) -> cookie.startsWith("JSESSIONID="))
			.findFirst()
			.map((cookie) -> cookie.split(";", 2)[0])
			.orElseThrow();
	}

	private String sessionId(String sessionCookie) {
		assertThat(sessionCookie).startsWith("JSESSIONID=");
		return sessionCookie.substring("JSESSIONID=".length());
	}

	private void ensureEnabledUser(String username) {
		this.userRepository.findByUsername(username)
			.orElseGet(() -> this.userRepository.save(new SystemUser(username,
					this.passwordEncoder.encode("Qherp@2026!"), "会话停用测试用户", SystemUserStatus.ENABLED, "test")));
		this.jdbcTemplate.update("update sys_user set status = 'ENABLED' where username = ?", username);
	}

	private void ensureDisabledUser() {
		this.userRepository.findByUsername("disabled-user")
			.orElseGet(() -> this.userRepository.save(new SystemUser("disabled-user",
					this.passwordEncoder.encode("Qherp@2026!"), "停用用户", SystemUserStatus.DISABLED, "test")));
	}

	private void disableUser(String username) {
		this.jdbcTemplate.update("update sys_user set status = 'DISABLED' where username = ?", username);
	}

	private record CsrfSession(String sessionCookie, String token, String headerName, String parameterName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}

}
