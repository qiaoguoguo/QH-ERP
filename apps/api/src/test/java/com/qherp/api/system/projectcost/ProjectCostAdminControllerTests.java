package com.qherp.api.system.projectcost;

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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=project-cost-admin-controller")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectCostAdminControllerTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void 项目成本调整接口必须走冻结路径提交022审批并执行幂等和超额校验() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PublicExpenseFixture fixture = createPublicExpenseFixture("029_ADJ_");

		ResponseEntity<String> candidateResponse = get(
				"/api/admin/cost/project-cost-adjustments/candidates/public-expenses?keyword=" + fixture.expenseNo(),
				admin);
		assertThat(candidateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode candidates = data(candidateResponse).get("items");
		assertThat(candidates.size()).isEqualTo(1);
		JsonNode candidate = candidates.get(0);
		assertThat(candidate.get("expenseLineId").longValue()).isEqualTo(fixture.expenseLineId());
		assertDecimal(candidate, "taxExcludedAmount", "100.00");
		assertDecimal(candidate, "allocatedAmount", "0.00");
		assertDecimal(candidate, "availableAmount", "100.00");

		Map<String, Object> payload = adjustmentPayload(fixture, "80.00", "pc-adj-" + fixture.projectId());
		JsonNode created = data(exchange(HttpMethod.POST, "/api/admin/cost/project-cost-adjustments", payload,
				admin));
		assertThat(created.get("status").asText()).isEqualTo("DRAFT");
		assertThat(created.get("adjustmentType").asText()).isEqualTo("PUBLIC_EXPENSE_ALLOCATION");
		assertDecimal(created, "totalAmount", "80.00");
		assertThat(actionCodes(created.get("allowedActions"))).contains("SUBMIT", "CANCEL");

		JsonNode repeated = data(exchange(HttpMethod.POST, "/api/admin/cost/project-cost-adjustments", payload,
				admin));
		assertThat(repeated.get("id").longValue()).isEqualTo(created.get("id").longValue());
		assertError(exchange(HttpMethod.POST, "/api/admin/cost/project-cost-adjustments",
				adjustmentPayload(fixture, "81.00", "pc-adj-" + fixture.projectId()), admin), HttpStatus.CONFLICT,
				"PROJECT_COST_IDEMPOTENCY_CONFLICT");

		long adjustmentId = created.get("id").longValue();
		JsonNode submitted = data(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-adjustments/" + adjustmentId + "/submit",
				Map.of("version", created.get("version").longValue(), "reason", "提交 029 公共费用分配审批",
						"idempotencyKey", "pc-adj-submit-" + adjustmentId),
				admin));
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		assertThat(submitted.get("approvalInstanceId").longValue()).isGreaterThan(0);
		assertThat(approvalScene(submitted.get("approvalInstanceId").longValue()))
			.isEqualTo("PROJECT_COST_ADJUSTMENT_CONFIRM");

		PublicExpenseFixture overAllocated = createPublicExpenseFixture("029_OVER_");
		long confirmedId = insertConfirmedAllocation(overAllocated.projectId(), overAllocated.expenseLineId(),
				"95.00");
		assertError(exchange(HttpMethod.POST, "/api/admin/cost/project-cost-adjustments",
				adjustmentPayload(overAllocated, "6.00", "pc-adj-over-" + overAllocated.projectId()), admin),
				HttpStatus.CONFLICT, "PROJECT_COST_ADJUSTMENT_OVER_ALLOCATED");
		assertError(exchange(HttpMethod.PUT, "/api/admin/cost/project-cost-adjustments/" + confirmedId + "/cancel",
				Map.of("version", version("prj_cost_adjustment", confirmedId), "idempotencyKey",
						"pc-adj-confirmed-cancel-" + confirmedId),
				admin), HttpStatus.CONFLICT, "PROJECT_COST_ACTION_NOT_ALLOWED");
	}

	@Test
	void 项目成本接口必须使用独立权限并在无金额权限时脱敏金额() throws Exception {
		PublicExpenseFixture fixture = createPublicExpenseFixture("029_MASK_");
		long varianceProjectId = insertVarianceFixture("029_VARIANCE_MASK_");
		AuthenticatedSession noProjectCost = createUserAndLogin("pc-no-cost-", "PC_NO_COST_", List.of());
		assertError(get("/api/admin/cost/project-cost-adjustments/candidates/public-expenses", noProjectCost),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertError(get("/api/admin/cost/project-cost-variances?projectId=" + varianceProjectId, noProjectCost),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		AuthenticatedSession noAmount = createUserAndLogin("pc-no-amount-", "PC_NO_AMOUNT_",
				List.of("cost:project-cost-adjustment:view", "cost:project-cost-variance:view"));
		ResponseEntity<String> candidateResponse = get(
				"/api/admin/cost/project-cost-adjustments/candidates/public-expenses?keyword=" + fixture.expenseNo(),
				noAmount);
		assertThat(candidateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode candidates = data(candidateResponse).get("items");
		assertThat(candidates.size()).isEqualTo(1);
		JsonNode candidate = candidates.get(0);
		assertThat(candidate.get("amountVisible").booleanValue()).isFalse();
		assertThat(candidate.get("taxExcludedAmount").isNull()).isTrue();
		assertThat(candidate.get("allocatedAmount").isNull()).isTrue();
		assertThat(candidate.get("availableAmount").isNull()).isTrue();

		ResponseEntity<String> varianceResponse = get("/api/admin/cost/project-cost-variances?projectId="
				+ varianceProjectId + "&severity=ERROR&type=SOURCE_UNPRICED&status=OPEN&sourceRestricted=true",
				noAmount);
		assertThat(varianceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode variances = data(varianceResponse).get("items");
		assertThat(variances.size()).isOne();
		JsonNode variance = variances.get(0);
		assertThat(variance.get("amountVisible").booleanValue()).isFalse();
		assertThat(variance.get("varianceAmount").isNull()).isTrue();
		assertThat(variance.get("sourceRestricted").booleanValue()).isTrue();
	}

	private Map<String, Object> adjustmentPayload(PublicExpenseFixture fixture, String amount,
			String idempotencyKey) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("projectId", fixture.projectId());
		line.put("costCategory", "MANUFACTURING_OVERHEAD");
		line.put("costStage", "DIRECT_PROJECT");
		line.put("direction", "INCREASE");
		line.put("amount", amount);
		line.put("publicExpenseLineId", fixture.expenseLineId());
		line.put("reason", "029 公共制造费用分配");
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("adjustmentType", "PUBLIC_EXPENSE_ALLOCATION");
		payload.put("businessDate", "2026-07-22");
		payload.put("reason", "029 公共制造费用分配");
		payload.put("idempotencyKey", idempotencyKey);
		payload.put("lines", List.of(line));
		return payload;
	}

	private PublicExpenseFixture createPublicExpenseFixture(String prefix) {
		int suffix = SEQUENCE.incrementAndGet();
		String code = prefix + suffix + "_";
		long customerId = this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "CUS", code + "客户");
		long supplierId = this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "SUP", code + "供应商");
		long projectId = this.jdbcTemplate.queryForObject("""
				insert into sal_project (project_no, name, customer_id, owner_user_id, planned_start_date,
					planned_finish_date, status, target_revenue, target_cost, created_by, created_at, updated_by,
					updated_at, activated_by, activated_at)
				values (?, ?, ?, 1, date '2026-07-01', date '2026-07-31', 'ACTIVE', 10000.00, 0,
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "PRJ", code + "项目", customerId);
		String expenseNo = code + "EXP";
		long expenseId = this.jdbcTemplate.queryForObject("""
				insert into fin_expense (expense_no, supplier_id, ownership_type, project_id, expense_date,
					due_date, invoice_type, currency, tax_excluded_amount, tax_amount, tax_included_amount,
					status, created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at)
				values (?, ?, 'PUBLIC', null, date '2026-07-18', date '2026-07-31', 'NONE', 'CNY',
					100.00, 0, 100.00, 'CONFIRMED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, expenseNo, supplierId);
		long expenseLineId = this.jdbcTemplate.queryForObject("""
				insert into fin_expense_line (expense_id, line_no, expense_category, description,
					tax_excluded_amount, tax_amount, tax_included_amount, created_at, updated_at)
				values (?, 1, 'MANUFACTURING_OVERHEAD', '029 公共制造费用', 100.00, 0, 100.00, now(), now())
				returning id
				""", Long.class, expenseId);
		return new PublicExpenseFixture(projectId, expenseNo, expenseLineId);
	}

	private long insertConfirmedAllocation(long projectId, long publicExpenseLineId, String amount) {
		long adjustmentId = this.jdbcTemplate.queryForObject("""
				insert into prj_cost_adjustment (adjustment_no, adjustment_type, business_date, status, reason,
					idempotency_key, request_fingerprint, created_by, created_at, updated_by, updated_at,
					submitted_by, submitted_at, confirmed_by, confirmed_at)
				values (?, 'PUBLIC_EXPENSE_ALLOCATION', date '2026-07-22', 'CONFIRMED', '029 已确认分配',
					?, 'seed', 'test', now(), 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "029-ADJ-CONF-" + SEQUENCE.incrementAndGet(),
				"pc-adj-confirmed-" + SEQUENCE.incrementAndGet());
		this.jdbcTemplate.update("""
				insert into prj_cost_adjustment_line (adjustment_id, line_no, project_id, cost_category,
					cost_stage, direction, amount, public_expense_line_id, reason, created_at, updated_at)
				values (?, 1, ?, 'MANUFACTURING_OVERHEAD', 'DIRECT_PROJECT', 'INCREASE', ?::numeric, ?,
					'已确认分配', now(), now())
				""", adjustmentId, projectId, amount, publicExpenseLineId);
		return adjustmentId;
	}

	private long insertVarianceFixture(String prefix) {
		int suffix = SEQUENCE.incrementAndGet();
		String code = prefix + suffix + "_";
		long customerId = this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "CUS", code + "客户");
		long projectId = this.jdbcTemplate.queryForObject("""
				insert into sal_project (project_no, name, customer_id, owner_user_id, planned_start_date,
					planned_finish_date, status, target_revenue, target_cost, created_by, created_at, updated_by,
					updated_at, activated_by, activated_at)
				values (?, ?, ?, 1, date '2026-07-01', date '2026-07-31', 'ACTIVE', 10000.00, 0,
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "PRJ", code + "项目", customerId);
		long calculationId = this.jdbcTemplate.queryForObject("""
				insert into prj_cost_calculation (
					project_id, calculation_no, cutoff_date, status, is_current, source_fingerprint,
					project_cost_total, wip_cost, finished_cost, delivered_cost, direct_project_cost,
					shipment_revenue, invoice_revenue, target_revenue, margin_completeness, created_by,
					created_at, updated_by, updated_at
				)
				values (?, ?, date '2026-07-31', 'CALCULATED', false, 'variance-mask',
					0, 0, 0, 0, 0, 0, 0, 10000.00, 'INCOMPLETE', 'test', now(), 'test', now())
				returning id
				""", Long.class, projectId, code + "CALC");
		this.jdbcTemplate.update("""
				insert into prj_cost_variance (
					calculation_id, project_id, variance_type, severity, status, source_restricted,
					variance_amount, description, source_type, source_id, source_line_id, created_at, updated_at
				)
				values (?, ?, 'SOURCE_UNPRICED', 'ERROR', 'OPEN', true, 300.00, '未定价人工来源',
					'MFG_COST_RECORD', 900001, 900001, now(), now())
				""", calculationId, projectId);
		return projectId;
	}

	private String approvalScene(long approvalInstanceId) {
		return this.jdbcTemplate.queryForObject("select scene_code from platform_approval_instance where id = ?",
				String.class, approvalInstanceId);
	}

	private long version(String tableName, long id) {
		return this.jdbcTemplate.queryForObject("select version from " + tableName + " where id = ?", Long.class,
				id);
	}

	private AuthenticatedSession createUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "029 项目成本测试角色" + suffix, "029 项目成本测试角色");
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), username);
		this.jdbcTemplate.update("""
				insert into sys_user_role (user_id, role_id, created_by, created_at)
				values (?, ?, 'test', now())
				""", userId, roleId);
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
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(code);
	}

	private void assertDecimal(JsonNode node, String field, String expected) {
		assertThat(new BigDecimal(node.get(field).asText())).isEqualByComparingTo(expected);
	}

	private List<String> actionCodes(JsonNode actions) {
		List<String> codes = new ArrayList<>();
		for (JsonNode action : actions) {
			if (action.isTextual()) {
				codes.add(action.asText());
			}
			else if (action.has("code")) {
				codes.add(action.get("code").asText());
			}
		}
		return codes;
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

	private record PublicExpenseFixture(Long projectId, String expenseNo, Long expenseLineId) {
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}

}
