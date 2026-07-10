package com.qherp.api.system.period;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=task2-business-period")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BusinessPeriodAdminControllerTests extends PostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BusinessPeriodGuard businessPeriodGuard;

	@Test
	void migrationCreatesBusinessPeriodTablesAndConstraints() {
		assertThat(tableExists("biz_business_period")).isTrue();
		assertThat(tableExists("biz_business_period_audit")).isTrue();
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from pg_indexes where indexname = ?", Long.class,
				"idx_biz_business_period_date_range")).isOne();
	}

	@Test
	void createsUpdatesAndRejectsOverlappingPeriods() throws Exception {
		AuthenticatedSession admin = login();
		long id = createPeriod(admin, "2027-07", LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 31));
		ResponseEntity<String> updated = exchange(HttpMethod.PUT, "/api/admin/system/business-periods/" + id,
				periodPayload("2027-07", "2027年07月更新", LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 31)), admin);
		assertOk(updated);
		assertThat(data(updated).get("periodName").asText()).isEqualTo("2027年07月更新");

		ResponseEntity<String> overlapped = exchange(HttpMethod.POST, "/api/admin/system/business-periods",
				periodPayload("2027-07-B", "重叠期间", LocalDate.of(2027, 7, 15), LocalDate.of(2027, 8, 15)), admin);
		assertError(overlapped, HttpStatus.CONFLICT, "BUSINESS_PERIOD_OVERLAPPED");
	}

	@Test
	void generatesMonthlyPeriodsAtomically() throws Exception {
		AuthenticatedSession admin = login();
		ResponseEntity<String> generated = exchange(HttpMethod.POST,
				"/api/admin/system/business-periods/generate-monthly",
				Map.of("startMonth", "2026-07", "endMonth", "2026-09"), admin);
		assertOk(generated);
		assertThat(data(generated)).hasSize(3);
		assertThat(data(generated).get(0).get("periodCode").asText()).isEqualTo("2026-07");
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from biz_business_period where period_code between ? and ?",
				Long.class, "2026-07", "2026-09")).isEqualTo(3L);

		createPeriod(admin, "2030-11", LocalDate.of(2030, 11, 1), LocalDate.of(2030, 11, 30));
		ResponseEntity<String> rejected = exchange(HttpMethod.POST,
				"/api/admin/system/business-periods/generate-monthly",
				Map.of("startMonth", "2030-10", "endMonth", "2030-12"), admin);
		assertError(rejected, HttpStatus.CONFLICT, "BUSINESS_PERIOD_OVERLAPPED");
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from biz_business_period where period_code in (?, ?)",
				Long.class, "2030-10", "2030-12")).isZero();
	}

	@Test
	void lockPeriodRejectsWritableBusinessDate() throws Exception {
		AuthenticatedSession admin = login();
		long id = createPeriod(admin, "2026-12", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
		assertOk(exchange(HttpMethod.POST, "/api/admin/system/business-periods/" + id + "/lock",
				Map.of("reason", "月末业务保护"), admin));

		assertThatThrownBy(() -> this.businessPeriodGuard.assertWritable(LocalDate.of(2026, 12, 10),
				BusinessPeriodOperation.POST, "INVENTORY_DOCUMENT", 1L))
			.isInstanceOfSatisfying(BusinessException.class,
					exception -> assertThat(exception.errorCode()).isEqualTo(ApiErrorCode.BUSINESS_PERIOD_LOCKED));
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from biz_business_period_audit where action = ?", Long.class,
				"POST")).isOne();

		assertOk(exchange(HttpMethod.POST, "/api/admin/system/business-periods/" + id + "/unlock",
				Map.of("reason", "更正期间"), admin));
		this.businessPeriodGuard.assertWritable(LocalDate.of(2026, 12, 10), BusinessPeriodOperation.POST,
				"INVENTORY_DOCUMENT", 1L);
	}

	private long createPeriod(AuthenticatedSession session, String code, LocalDate startDate, LocalDate endDate)
			throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/system/business-periods",
				periodPayload(code, code + "期间", startDate, endDate), session);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private Map<String, Object> periodPayload(String code, String name, LocalDate startDate, LocalDate endDate) {
		return Map.of("periodCode", code, "periodName", name, "startDate", startDate.toString(), "endDate",
				endDate.toString());
	}

	private boolean tableExists(String tableName) {
		return Boolean.TRUE.equals(this.jdbcTemplate.queryForObject("select exists (select 1 from information_schema.tables where table_name = ?)",
				Boolean.class, tableName));
	}

	private AuthenticatedSession login() {
		ResponseEntity<String> csrfResponse = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		try {
			JsonNode csrf = data(csrfResponse);
			String sessionCookie = sessionCookie(csrfResponse);
			CsrfSession csrfSession = new CsrfSession(sessionCookie, csrf.get("token").asText(), csrf.get("headerName").asText());
			ResponseEntity<String> login = this.restTemplate.postForEntity("/api/auth/login",
					entity(Map.of("username", "admin", "password", "Qherp@2026!"), sessionCookie, csrfSession), String.class);
			return new AuthenticatedSession(sessionCookie(login), csrfSession);
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()), String.class);
	}

	private HttpEntity<Object> entity(Object body, String cookie, CsrfSession csrf) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, cookie);
		headers.add(csrf.headerName(), csrf.token());
		return new HttpEntity<>(body, headers);
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertOk(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(code(response)).isEqualTo("OK");
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String expectedCode) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(expectedCode);
	}

	private String sessionCookie(ResponseEntity<String> response) {
		return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream().filter(value -> value.startsWith("JSESSIONID="))
			.findFirst().map(value -> value.split(";", 2)[0]).orElseThrow();
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}
}
