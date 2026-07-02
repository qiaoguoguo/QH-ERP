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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

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

	@Test
	void loginReturnsCurrentUserAndSessionCookie() {
		ResponseEntity<String> response = login("admin", "Qherp@2026!");

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
	void currentUserRequiresLogin() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/me", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"success\":false");
		assertThat(response.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
	}

	@Test
	void currentUserReturnsSessionUserAfterLogin() {
		String sessionCookie = sessionCookie(login("admin", "Qherp@2026!"));

		ResponseEntity<String> response = this.restTemplate.exchange("/api/auth/me", HttpMethod.GET,
				withCookie(sessionCookie), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"success\":true");
		assertThat(response.getBody()).contains("\"username\":\"admin\"");
		assertThat(response.getBody()).contains("\"SYSTEM_ADMIN\"");
		assertThat(response.getBody()).contains("\"system:user:view\"");
	}

	@Test
	void logoutClearsSession() {
		String sessionCookie = sessionCookie(login("admin", "Qherp@2026!"));

		ResponseEntity<String> logoutResponse = this.restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
				withCookie(sessionCookie), String.class);
		ResponseEntity<String> meResponse = this.restTemplate.exchange("/api/auth/me", HttpMethod.GET,
				withCookie(sessionCookie), String.class);

		assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(logoutResponse.getBody()).contains("\"success\":true");
		assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(meResponse.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
	}

	@Test
	void disabledUserCannotLogin() {
		ensureDisabledUser();

		ResponseEntity<String> response = login("disabled-user", "Qherp@2026!");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"success\":false");
		assertThat(response.getBody()).contains("\"code\":\"AUTH_ACCOUNT_DISABLED\"");
	}

	@Test
	void invalidCredentialsReturnUnauthorizedEnvelope() {
		ResponseEntity<String> wrongPassword = login("admin", "wrong-password");
		ResponseEntity<String> missingUser = login("missing-user", "Qherp@2026!");

		assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(wrongPassword.getBody()).contains("\"success\":false");
		assertThat(wrongPassword.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
		assertThat(wrongPassword.getBody()).doesNotContain("admin");

		assertThat(missingUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(missingUser.getBody()).contains("\"success\":false");
		assertThat(missingUser.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
		assertThat(missingUser.getBody()).doesNotContain("missing-user");
	}

	private ResponseEntity<String> login(String username, String password) {
		return this.restTemplate.postForEntity("/api/auth/login", Map.of("username", username, "password", password),
				String.class);
	}

	private HttpEntity<Void> withCookie(String sessionCookie) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, sessionCookie);
		return new HttpEntity<>(headers);
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

	private void ensureDisabledUser() {
		this.userRepository.findByUsername("disabled-user")
			.orElseGet(() -> this.userRepository.save(new SystemUser("disabled-user",
					this.passwordEncoder.encode("Qherp@2026!"), "停用用户", SystemUserStatus.DISABLED, "test")));
	}

}
