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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=sales-project-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SalesProjectAdminControllerTests extends PostgresIntegrationTest {

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
	void projectAndContractLifecycleEnforcesStateMachineVersionReasonAndAudit() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("SPC_CUS_" + SEQUENCE.incrementAndGet(), "项目客户", "ENABLED");
		long ownerUserId = userId("admin");

		ResponseEntity<String> created = createProject(admin, projectPayload(customerId, ownerUserId, "项目生命周期"));
		assertOk(created);
		JsonNode project = data(created);
		long projectId = project.get("id").longValue();
		assertThat(project.get("projectNo").asText()).isNotBlank();
		assertThat(project.get("status").asText()).isEqualTo("DRAFT");
		assertThat(project.get("version").longValue()).isZero();

		ResponseEntity<String> missingCancelReason = action(admin, "/api/admin/sales-projects/" + projectId + "/cancel",
				Map.of("version", project.get("version").longValue()));
		assertError(missingCancelReason, HttpStatus.BAD_REQUEST, "PROJECT_REASON_REQUIRED");

		ResponseEntity<String> staleUpdate = updateProject(admin, projectId,
				updateProjectPayload(ownerUserId, "项目生命周期", 99L));
		assertError(staleUpdate, HttpStatus.CONFLICT, "PROJECT_CONCURRENT_MODIFICATION");

		ResponseEntity<String> mainCreated = createContract(admin, projectId,
				contractPayload("MAIN", null, "主合同", "120000.00", "EXT-DUPLICATED"));
		assertOk(mainCreated);
		JsonNode mainContract = data(mainCreated);
		long mainContractId = mainContract.get("id").longValue();
		assertThat(mainContract.get("contractNo").asText()).isNotBlank();
		assertThat(mainContract.get("status").asText()).isEqualTo("DRAFT");

		assertError(createContract(admin, projectId,
				contractPayload("SUPPLEMENT", mainContractId, "过早补充合同", "1000.00", "EXT-DUPLICATED")),
				HttpStatus.CONFLICT, "CONTRACT_PROJECT_NOT_ACTIVE");

		JsonNode effectiveMain = data(action(admin, "/api/admin/sales-project-contracts/" + mainContractId + "/activate",
				Map.of("version", mainContract.get("version").longValue())));
		assertThat(effectiveMain.get("status").asText()).isEqualTo("EFFECTIVE");
		JsonNode activeProject = data(action(admin, "/api/admin/sales-projects/" + projectId + "/activate",
				Map.of("version", project.get("version").longValue())));
		assertThat(activeProject.get("status").asText()).isEqualTo("ACTIVE");

		JsonNode activeUpdated = data(updateProject(admin, projectId,
				updateProjectPayload(ownerUserId, "项目生命周期更新后备注", activeProject.get("version").longValue())));
		assertThat(activeUpdated.get("remark").asText()).isEqualTo("项目生命周期更新后备注");
		assertThat(activeUpdated.get("version").longValue()).isEqualTo(activeProject.get("version").longValue() + 1);

		ResponseEntity<String> duplicateExternalNo = createContract(admin, projectId,
				contractPayload("SUPPLEMENT", mainContractId, "重复外部号补充合同", "-500.00", "EXT-DUPLICATED"));
		assertOk(duplicateExternalNo);
		JsonNode supplement = data(duplicateExternalNo);
		JsonNode effectiveSupplement = data(action(admin,
				"/api/admin/sales-project-contracts/" + supplement.get("id").longValue() + "/activate",
				Map.of("version", supplement.get("version").longValue())));
		assertThat(effectiveSupplement.get("status").asText()).isEqualTo("EFFECTIVE");

		assertThat(data(get("/api/admin/sales-projects/" + projectId, admin)).get("supplementContractCount").longValue())
			.isEqualTo(1L);

		JsonNode closedSupplement = data(action(admin,
				"/api/admin/sales-project-contracts/" + supplement.get("id").longValue() + "/close",
				Map.of("version", effectiveSupplement.get("version").longValue(), "reason", "补充合同正常结束")));
		assertThat(closedSupplement.get("status").asText()).isEqualTo("CLOSED");
		JsonNode closedMain = data(action(admin, "/api/admin/sales-project-contracts/" + mainContractId + "/close",
				Map.of("version", effectiveMain.get("version").longValue(), "reason", "主合同正常结束")));
		assertThat(closedMain.get("status").asText()).isEqualTo("CLOSED");

		JsonNode latestProject = data(get("/api/admin/sales-projects/" + projectId, admin));
		JsonNode closedProject = data(action(admin, "/api/admin/sales-projects/" + projectId + "/close",
				Map.of("version", latestProject.get("version").longValue(), "reason", "项目完成关闭")));
		assertThat(closedProject.get("status").asText()).isEqualTo("CLOSED");

		assertAudit("SALES_PROJECT_CREATE", "SALES_PROJECT", projectId);
		assertAudit("SALES_PROJECT_ACTIVATE", "SALES_PROJECT", projectId);
		assertAudit("SALES_PROJECT_CLOSE", "SALES_PROJECT", projectId);
		assertAudit("SALES_PROJECT_CONTRACT_CREATE", "SALES_PROJECT_CONTRACT", mainContractId);
		assertAudit("SALES_PROJECT_CONTRACT_ACTIVATE", "SALES_PROJECT_CONTRACT", mainContractId);
		assertAuditSummaryContains("SALES_PROJECT_CLOSE", "SALES_PROJECT", projectId, "项目完成关闭");
	}

	@Test
	void projectDetailHidesContractsAndSalesOrdersWhenViewerLacksCrossPermissions() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("SPC_HIDE_CUS_" + SEQUENCE.incrementAndGet(), "受限项目客户", "ENABLED");
		long ownerUserId = userId("admin");
		long projectId = data(createProject(admin, projectPayload(customerId, ownerUserId, "权限受限项目"))).get("id")
			.longValue();
		JsonNode mainContract = data(createContract(admin, projectId,
				contractPayload("MAIN", null, "权限受限主合同", "1000.00", "HIDE-CONTRACT-NO")));
		action(admin, "/api/admin/sales-project-contracts/" + mainContract.get("id").longValue() + "/activate",
				Map.of("version", mainContract.get("version").longValue()));
		JsonNode project = data(get("/api/admin/sales-projects/" + projectId, admin));
		action(admin, "/api/admin/sales-projects/" + projectId + "/activate",
				Map.of("version", project.get("version").longValue()));

		AuthenticatedSession viewer = createUserAndLogin("project-viewer-", "PROJECT_VIEWER_",
				List.of("sales:project:view"));

		ResponseEntity<String> detail = get("/api/admin/sales-projects/" + projectId, viewer);

		assertOk(detail);
		JsonNode data = data(detail);
		assertThat(data.get("contractSummaryRestricted").booleanValue()).isTrue();
		assertThat(data.get("salesOrderSummaryRestricted").booleanValue()).isTrue();
		assertThat(data.get("mainContractId").isNull()).isTrue();
		assertThat(data.get("mainContractNo").isNull()).isTrue();
		assertThat(data.get("effectiveContractAmount").isNull()).isTrue();
		assertThat(data.get("contractCount").isNull()).isTrue();
		assertThat(data.get("supplementContractCount").isNull()).isTrue();
		assertThat(data.get("salesOrderCount").isNull()).isTrue();
		assertThat(data.get("salesOrderSummary").isNull()).isTrue();
		assertThat(data.get("contracts").size()).isZero();
		assertThat(detail.getBody()).doesNotContain("HIDE-CONTRACT-NO");
	}

	@Test
	void migrationAddsProjectContractTablesAndSalesOrderNullableLinkColumnsOnly() {
		List<String> projectColumns = columns("sal_project");
		List<String> contractColumns = columns("sal_project_contract");
		List<String> orderColumns = columns("sal_sales_order");

		assertThat(projectColumns).contains("project_no", "customer_id", "owner_user_id", "status", "target_revenue",
				"target_cost", "version");
		assertThat(contractColumns).contains("contract_no", "external_contract_no", "project_id", "contract_type",
				"main_contract_id", "amount", "status", "version");
		assertThat(orderColumns).contains("project_id", "contract_id");
		assertThat(columns("proc_purchase_order")).doesNotContain("project_id", "contract_id");
		assertThat(columns("mfg_work_order")).doesNotContain("project_id", "contract_id");
		assertThat(indexes("sal_project_contract")).contains("uk_sal_project_contract_main_active");
		assertThat(constraint("sal_sales_order", "ck_sal_sales_order_project_pair")).contains("project_id");
	}

	private Map<String, Object> projectPayload(long customerId, long ownerUserId, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("name", "销售项目" + SEQUENCE.incrementAndGet());
		payload.put("customerId", customerId);
		payload.put("ownerUserId", ownerUserId);
		payload.put("plannedStartDate", LocalDate.now().toString());
		payload.put("plannedFinishDate", LocalDate.now().plusDays(30).toString());
		payload.put("targetRevenue", "200000.00");
		payload.put("targetCost", "120000.00");
		payload.put("remark", remark);
		return payload;
	}

	private Map<String, Object> updateProjectPayload(long ownerUserId, String remark, long version) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("ownerUserId", ownerUserId);
		payload.put("plannedStartDate", LocalDate.now().plusDays(1).toString());
		payload.put("plannedFinishDate", LocalDate.now().plusDays(40).toString());
		payload.put("targetRevenue", "210000.00");
		payload.put("targetCost", "121000.00");
		payload.put("remark", remark);
		payload.put("version", version);
		return payload;
	}

	private Map<String, Object> contractPayload(String contractType, Long mainContractId, String name, String amount,
			String externalContractNo) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("contractType", contractType);
		if (mainContractId != null) {
			payload.put("mainContractId", mainContractId);
		}
		payload.put("name", name);
		payload.put("signedDate", LocalDate.now().toString());
		payload.put("effectiveStartDate", LocalDate.now().toString());
		payload.put("effectiveEndDate", LocalDate.now().plusDays(60).toString());
		payload.put("amount", amount);
		payload.put("externalContractNo", externalContractNo);
		payload.put("remark", name + "备注");
		return payload;
	}

	private long insertCustomer(String code, String name, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, status);
	}

	private long userId(String username) {
		return this.jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
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
				""", Long.class, rolePrefix + suffix, "项目测试角色" + suffix, "项目测试角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), "项目测试用户" + suffix);
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

	private ResponseEntity<String> createProject(AuthenticatedSession session, Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/sales-projects", body, session);
	}

	private ResponseEntity<String> updateProject(AuthenticatedSession session, long projectId,
			Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/sales-projects/" + projectId, body, session);
	}

	private ResponseEntity<String> createContract(AuthenticatedSession session, long projectId,
			Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/sales-projects/" + projectId + "/contracts", body, session);
	}

	private ResponseEntity<String> action(AuthenticatedSession session, String path, Map<String, Object> body) {
		return exchange(HttpMethod.PUT, path, body, session);
	}

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET,
				entity(null, session == null ? null : session.sessionCookie(), null), String.class);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()),
				String.class);
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

	private void assertAudit(String action, String targetType, long targetId) {
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				and target_type = ?
				and target_id = ?
				""", Long.class, action, targetType, Long.toString(targetId))).as(action).isOne();
	}

	private void assertAuditSummaryContains(String action, String targetType, long targetId, String text) {
		assertThat(this.jdbcTemplate.queryForObject("""
				select target_summary
				from sys_audit_log
				where action = ?
				and target_type = ?
				and target_id = ?
				order by id desc
				limit 1
				""", String.class, action, targetType, Long.toString(targetId))).contains(text);
	}

	private List<String> columns(String tableName) {
		return this.jdbcTemplate.queryForList("""
				select column_name
				from information_schema.columns
				where table_schema = 'public'
				and table_name = ?
				""", String.class, tableName);
	}

	private List<String> indexes(String tableName) {
		return this.jdbcTemplate.queryForList("""
				select indexname
				from pg_indexes
				where schemaname = 'public'
				and tablename = ?
				""", String.class, tableName);
	}

	private String constraint(String tableName, String constraintName) {
		return this.jdbcTemplate.queryForObject("""
				select pg_get_constraintdef(c.oid)
				from pg_constraint c
				join pg_class t on t.oid = c.conrelid
				where t.relname = ?
				and c.conname = ?
				""", String.class, tableName, constraintName);
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
