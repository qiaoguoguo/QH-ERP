package com.qherp.api.system.salesproject;

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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=sales-project-order-link")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SalesProjectOrderLinkControllerTests extends PostgresIntegrationTest {

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
	void orderLinkCandidatesUseOrderCreateOrUpdatePermissionWithoutLeakingContractAmounts() throws Exception {
		long customerId = insertCustomer("SPC_OL_CUS_" + SEQUENCE.incrementAndGet(), "候选客户", "ENABLED");
		long projectId = insertProject(customerId, "候选项目", "ACTIVE");
		long contractId = insertContract(projectId, "MAIN", null, "候选主合同", "EFFECTIVE", "5000.00");
		AuthenticatedSession orderCreator = createUserAndLogin("order-candidate-", "ORDER_CANDIDATE_",
				List.of("sales:order:create"));

		ResponseEntity<String> response = get("/api/admin/sales-projects/order-link-candidates?customerId="
				+ customerId + "&keyword=候选&page=1&pageSize=20", orderCreator);

		assertOk(response);
		JsonNode item = data(response).get("items").get(0);
		assertThat(item.get("projectId").longValue()).isEqualTo(projectId);
		assertThat(item.get("contractId").longValue()).isEqualTo(contractId);
		assertThat(item.get("projectNo").asText()).isNotBlank();
		assertThat(item.get("contractNo").asText()).isNotBlank();
		assertThat(item.hasNonNull("customerName")).isTrue();
		assertThat(item.get("customerName").asText()).isEqualTo("候选客户");
		assertThat(item.hasNonNull("contractName")).isTrue();
		assertThat(item.get("contractName").asText()).isEqualTo("候选主合同");
		assertThat(item.hasNonNull("contractType")).isTrue();
		assertThat(item.get("contractType").asText()).isEqualTo("MAIN");
		assertThat(item.has("amount")).isFalse();
		assertThat(item.has("status")).isFalse();
	}

	@Test
	void projectSalesOrderListRequiresOrderViewAndReturnsLinkedOrderSummaryOnlyWhenAllowed() throws Exception {
		long customerId = insertCustomer("SPC_LIST_CUS_" + SEQUENCE.incrementAndGet(), "关联订单客户", "ENABLED");
		long projectId = insertProject(customerId, "关联订单项目", "ACTIVE");
		long contractId = insertContract(projectId, "MAIN", null, "关联订单主合同", "EFFECTIVE", "7000.00");
		long orderId = insertSalesOrder(customerId, projectId, contractId, "DRAFT");
		AuthenticatedSession projectOnly = createUserAndLogin("project-only-", "PROJECT_ONLY_",
				List.of("sales:project:view"));
		AuthenticatedSession orderViewer = createUserAndLogin("project-order-viewer-", "PROJECT_ORDER_VIEWER_",
				List.of("sales:project:view", "sales:order:view"));

		assertError(get("/api/admin/sales-projects/" + projectId + "/sales-orders", projectOnly),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		ResponseEntity<String> allowed = get("/api/admin/sales-projects/" + projectId
				+ "/sales-orders?keyword=SPC-SO-&contractId=" + contractId + "&status=DRAFT&dateFrom="
				+ LocalDate.now().minusDays(1) + "&dateTo=" + LocalDate.now().plusDays(1), orderViewer);

		assertOk(allowed);
		JsonNode item = data(allowed).get("items").get(0);
		assertThat(item.get("id").longValue()).isEqualTo(orderId);
		assertThat(item.get("projectId").longValue()).isEqualTo(projectId);
		assertThat(item.get("contractId").longValue()).isEqualTo(contractId);
		assertThat(item.hasNonNull("projectNo")).isTrue();
		assertThat(item.get("projectNo").asText()).startsWith("SPC-P-");
		assertThat(item.hasNonNull("projectName")).isTrue();
		assertThat(item.get("projectName").asText()).isEqualTo("关联订单项目");
		assertThat(item.get("orderNo").asText()).startsWith("SPC-SO-");
		assertThat(item.hasNonNull("totalQuantity")).isTrue();
		assertThat(item.get("totalQuantity").asText()).isEqualTo("0");
		assertThat(item.hasNonNull("createdAt")).isTrue();
		assertThat(item.get("createdAt").asText()).isNotBlank();
		assertThat(item.hasNonNull("updatedAt")).isTrue();
		assertThat(item.get("updatedAt").asText()).isNotBlank();
	}

	private long insertCustomer(String code, String name, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, status);
	}

	private long insertProject(long customerId, String name, String status) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date, status,
					target_revenue, target_cost, remark, created_by, created_at, updated_by, updated_at, activated_by,
					activated_at
				)
				values (?, ?, ?, (select id from sys_user where username = 'admin'), ?, ?, ?, 0, 0, ?, 'test', now(),
					'test', now(), case when ? = 'ACTIVE' then 'test' end, case when ? = 'ACTIVE' then now() end)
				returning id
				""", Long.class, "SPC-P-" + suffix, name, customerId, LocalDate.now(),
				LocalDate.now().plusDays(30), status, name, status, status);
	}

	private long insertContract(long projectId, String contractType, Long mainContractId, String name, String status,
			String amount) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project_contract (
					contract_no, external_contract_no, project_id, contract_type, main_contract_id, name, signed_date,
					effective_start_date, effective_end_date, amount, status, remark, created_by, created_at,
					updated_by, updated_at, activated_by, activated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now(),
					case when ? = 'EFFECTIVE' then 'test' end, case when ? = 'EFFECTIVE' then now() end)
				returning id
				""", Long.class, "SPC-C-" + suffix, "EXT-" + suffix, projectId, contractType, mainContractId, name,
				LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal(amount), status, name,
				status, status);
	}

	private long insertSalesOrder(long customerId, long projectId, long contractId, String status) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark, project_id, contract_id,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, 'test', ?, 'test', ?)
				returning id
				""", Long.class, "SPC-SO-" + suffix, customerId, LocalDate.now(), LocalDate.now().plusDays(3),
				status, "关联订单", projectId, contractId, OffsetDateTime.now(), OffsetDateTime.now());
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
				""", Long.class, rolePrefix + suffix, "候选测试角色" + suffix, "候选测试角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), "候选测试用户" + suffix);
		this.jdbcTemplate.update("insert into sys_user_role (user_id, role_id, created_by, created_at) values (?, ?, 'test', now())",
				userId, roleId);
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

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET,
				entity(null, session == null ? null : session.sessionCookie(), null), String.class);
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

	private void assertOk(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(code(response)).isEqualTo("OK");
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

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}

}
