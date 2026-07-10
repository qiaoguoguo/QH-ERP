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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=task2-business-period")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BusinessPeriodAdminControllerTests extends PostgresIntegrationTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BusinessPeriodGuard businessPeriodGuard;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void migrationCreatesBusinessPeriodTablesAndConstraints() {
		assertThat(tableExists("biz_business_period")).isTrue();
		assertThat(tableExists("biz_business_period_audit")).isTrue();
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from pg_indexes where indexname = ?", Long.class,
				"idx_biz_business_period_date_range")).isOne();
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from pg_constraint where conname = ?", Long.class,
				"ex_biz_business_period_no_overlap")).isOne();
		insertPeriodDirectly("2045-01", LocalDate.of(2045, 1, 1), LocalDate.of(2045, 1, 31));
		assertThatThrownBy(() -> insertPeriodDirectly("2045-01-B", LocalDate.of(2045, 1, 15),
				LocalDate.of(2045, 2, 15))).isInstanceOf(DataIntegrityViolationException.class);
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
	void listsPeriodsWithFiltersPaginationAndResolvesBusinessDate() throws Exception {
		AuthenticatedSession admin = login();
		createPeriod(admin, "2044-01", LocalDate.of(2044, 1, 1), LocalDate.of(2044, 1, 31));
		long lockedId = createPeriod(admin, "2044-02", LocalDate.of(2044, 2, 1), LocalDate.of(2044, 2, 29));
		createPeriod(admin, "2044-03", LocalDate.of(2044, 3, 1), LocalDate.of(2044, 3, 31));
		assertOk(exchange(HttpMethod.POST, "/api/admin/system/business-periods/" + lockedId + "/lock",
				Map.of("reason", "月度经营数据核对完成"), admin));

		ResponseEntity<String> list = exchange(HttpMethod.GET,
				"/api/admin/system/business-periods?periodCode=2044&status=LOCKED&startDate=2044-02-01&endDate=2044-02-29&page=1&pageSize=10",
				null, admin);
		assertOk(list);
		assertThat(data(list).get("items")).hasSize(1);
		assertThat(data(list).get("items").get(0).get("periodCode").asText()).isEqualTo("2044-02");
		assertThat(data(list).get("items").get(0).get("statusName").asText()).isEqualTo("已锁定");
		assertThat(data(list).get("total").asLong()).isEqualTo(1L);
		assertThat(data(list).get("page").asInt()).isOne();

		ResponseEntity<String> resolvedLocked = exchange(HttpMethod.GET,
				"/api/admin/system/business-periods/resolve?businessDate=2044-02-10", null, admin);
		assertOk(resolvedLocked);
		assertThat(data(resolvedLocked).get("configured").asBoolean()).isTrue();
		assertThat(data(resolvedLocked).get("period").get("periodCode").asText()).isEqualTo("2044-02");
		assertThat(data(resolvedLocked).get("statusName").asText()).isEqualTo("已锁定");
		assertThat(data(resolvedLocked).get("message").asText()).contains("已锁定");

		ResponseEntity<String> unresolved = exchange(HttpMethod.GET,
				"/api/admin/system/business-periods/resolve?businessDate=2099-12-31", null, admin);
		assertOk(unresolved);
		assertThat(data(unresolved).get("configured").asBoolean()).isFalse();
		assertThat(data(unresolved).get("period").isNull()).isTrue();
		assertThat(data(unresolved).get("statusName").asText()).isEqualTo("未配置");
		assertThat(data(unresolved).get("message").asText()).isEqualTo("未配置业务期间，按开放处理");
	}

	@Test
	void generateMonthlyRejectsExistingCodeWithoutPartialWrites() throws Exception {
		AuthenticatedSession admin = login();
		createPeriod(admin, "2041-08", LocalDate.of(2042, 8, 1), LocalDate.of(2042, 8, 31));

		ResponseEntity<String> rejected = exchange(HttpMethod.POST,
				"/api/admin/system/business-periods/generate-monthly",
				Map.of("startMonth", "2041-07", "endMonth", "2041-09"), admin);

		assertError(rejected, HttpStatus.CONFLICT, "BUSINESS_PERIOD_OVERLAPPED");
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from biz_business_period where period_code in (?, ?)",
				Long.class, "2041-07", "2041-09")).isZero();
	}

	@Test
	void usersWithoutBusinessPeriodPermissionsCannotWriteOrChangePeriodState() throws Exception {
		AuthenticatedSession admin = login();
		long periodId = createPeriod(admin, "2043-01", LocalDate.of(2043, 1, 1), LocalDate.of(2043, 1, 31));
		AuthenticatedSession unauthorized = login(createUserWithoutBusinessPeriodPermissions());

		assertError(exchange(HttpMethod.POST, "/api/admin/system/business-periods",
				periodPayload("2043-02", "越权创建", LocalDate.of(2043, 2, 1), LocalDate.of(2043, 2, 28)), unauthorized),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertError(exchange(HttpMethod.POST, "/api/admin/system/business-periods/generate-monthly",
				Map.of("startMonth", "2043-03", "endMonth", "2043-04"), unauthorized), HttpStatus.FORBIDDEN,
				"AUTH_FORBIDDEN");
		assertError(exchange(HttpMethod.POST, "/api/admin/system/business-periods/" + periodId + "/lock",
				Map.of("reason", "越权锁定"), unauthorized), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		assertOk(exchange(HttpMethod.POST, "/api/admin/system/business-periods/" + periodId + "/lock",
				Map.of("reason", "管理员锁定"), admin));
		assertError(exchange(HttpMethod.POST, "/api/admin/system/business-periods/" + periodId + "/unlock",
				Map.of("reason", "越权解锁"), unauthorized), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertThat(this.jdbcTemplate.queryForObject("select status from biz_business_period where id = ?", String.class,
				periodId)).isEqualTo("LOCKED");
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from biz_business_period where period_code in (?, ?, ?)",
				Long.class, "2043-02", "2043-03", "2043-04")).isZero();
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

	private void insertPeriodDirectly(String code, LocalDate startDate, LocalDate endDate) {
		this.jdbcTemplate.update("""
				insert into biz_business_period (period_code, period_name, start_date, end_date, status, created_at, updated_at)
				values (?, ?, ?, ?, 'OPEN', ?, ?)
				""", code, code, startDate, endDate, OffsetDateTime.now(), OffsetDateTime.now());
	}

	private String createUserWithoutBusinessPeriodPermissions() {
		int sequence = SEQUENCE.incrementAndGet();
		String username = "period-no-permission-" + sequence;
		OffsetDateTime now = OffsetDateTime.now();
		Long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, 'ENABLED', 'test', ?, 'test', ?) returning id
				""", Long.class, username, this.passwordEncoder.encode("Qherp@2026!"), username, now, now);
		Long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', ?, 'test', ?) returning id
				""", Long.class, "PERIOD_NO_PERMISSION_" + sequence, "无业务期间权限", now, now);
		this.jdbcTemplate.update("insert into sys_user_role (user_id, role_id, created_by, created_at) values (?, ?, 'test', ?)",
				userId, roleId, now);
		return username;
	}

	private AuthenticatedSession login() {
		return login("admin");
	}

	private AuthenticatedSession login(String username) {
		ResponseEntity<String> csrfResponse = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		try {
			JsonNode csrf = data(csrfResponse);
			String sessionCookie = sessionCookie(csrfResponse);
			CsrfSession csrfSession = new CsrfSession(sessionCookie, csrf.get("token").asText(), csrf.get("headerName").asText());
			ResponseEntity<String> login = this.restTemplate.postForEntity("/api/auth/login",
					entity(Map.of("username", username, "password", "Qherp@2026!"), sessionCookie, csrfSession), String.class);
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
