package com.qherp.api.system.reversal;

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
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=reversal-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReversalAdminControllerTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void adminCanQueryStableEmptyReversalSkeletons() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		for (String path : List.of("/api/admin/sales/return-sources", "/api/admin/sales/returns",
				"/api/admin/procurement/return-sources", "/api/admin/procurement/returns",
				"/api/admin/production/material-return-sources", "/api/admin/production/material-returns",
				"/api/admin/production/material-supplement-sources",
				"/api/admin/production/material-supplements",
				"/api/admin/finance/settlement-adjustment-sources",
				"/api/admin/finance/settlement-adjustments")) {
			assertEmptyPage(get(path, admin));
		}

		ResponseEntity<String> traces = get("/api/admin/reversal-traces?sourceType=SALES_RETURN&sourceId=1",
				admin);
		assertThat(traces.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(traces).isArray()).isTrue();
		assertThat(data(traces).size()).isZero();
	}

	@Test
	void reversalEndpointsRequireAuthenticationAndModulePermission() throws Exception {
		ResponseEntity<String> unauthenticated = this.restTemplate.getForEntity("/api/admin/sales/returns",
				String.class);
		assertError(unauthenticated, HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED");

		AuthenticatedSession noPermission = createUserAndLogin("reversal-no-permission", List.of());
		assertError(get("/api/admin/sales/returns", noPermission), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertError(get("/api/admin/reversal-traces?sourceType=SALES_RETURN&sourceId=1", noPermission),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	@Test
	void writeAndDetailSkeletonsReturnControlledReversalErrors() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		assertError(post("/api/admin/sales/returns", Map.of("sourceShipmentId", 1, "lines", List.of()), admin),
				HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");
		assertError(put("/api/admin/procurement/returns/999/post", Map.of(), admin), HttpStatus.NOT_FOUND,
				"REVERSAL_SOURCE_NOT_FOUND");
		assertError(get("/api/admin/production/material-returns/999", admin), HttpStatus.NOT_FOUND,
				"REVERSAL_SOURCE_NOT_FOUND");
		assertError(put("/api/admin/production/material-supplements/999/cancel", Map.of(), admin),
				HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");
		assertError(post("/api/admin/finance/settlement-adjustments",
				Map.of("settlementSide", "RECEIVABLE", "sourceType", "SALES_RETURN", "sourceId", 1,
						"targetId", 1, "amount", "1.00"),
				admin), HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");
	}

	private AuthenticatedSession createUserAndLogin(String username, List<Long> roleIds) throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", username, "displayName", username, "initialPassword", ADMIN_PASSWORD, "status",
						"ENABLED", "roleIds", roleIds),
				admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return login(username, ADMIN_PASSWORD);
	}

	private void assertEmptyPage(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode data = data(response);
		assertThat(data.get("items").isArray()).isTrue();
		assertThat(data.get("items").size()).isZero();
		assertThat(data.get("page").intValue()).isOne();
		assertThat(data.get("pageSize").intValue()).isEqualTo(20);
		assertThat(data.get("total").longValue()).isZero();
		assertThat(data.get("totalPages").intValue()).isZero();
	}

	private AuthenticatedSession login(String username, String password) throws Exception {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrf);
	}

	private CsrfSession csrfSession() throws Exception {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode data = data(response);
		return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
	}

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null),
				String.class);
	}

	private ResponseEntity<String> post(String path, Object body, AuthenticatedSession session) {
		return exchange(HttpMethod.POST, path, body, session);
	}

	private ResponseEntity<String> put(String path, Object body, AuthenticatedSession session) {
		return exchange(HttpMethod.PUT, path, body, session);
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

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(this.objectMapper.readTree(response.getBody()).get("code").asText()).isEqualTo(code);
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
