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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
	void projectActionsRejectMissingMainOpenBusinessAndLinkedDraftOrder() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("SPC_BLOCK_CUS_" + SEQUENCE.incrementAndGet(), "项目阻断客户", "ENABLED");
		long ownerUserId = userId("admin");

		JsonNode noMainProject = data(createProject(admin, projectPayload(customerId, ownerUserId, "无主合同项目")));
		assertError(action(admin, "/api/admin/sales-projects/" + noMainProject.get("id").longValue() + "/activate",
				Map.of("version", noMainProject.get("version").longValue())), HttpStatus.CONFLICT,
				"PROJECT_MAIN_CONTRACT_REQUIRED");
		assertThat(projectStatus(noMainProject.get("id").longValue())).isEqualTo("DRAFT");

		ProjectContractFixture openContract = activeProjectWithEffectiveMain(admin, customerId, ownerUserId,
				"未终态合同项目", "未终态主合同");
		assertError(action(admin, "/api/admin/sales-projects/" + openContract.projectId() + "/close",
				Map.of("version", openContract.projectVersion(), "reason", "存在未终态合同")), HttpStatus.CONFLICT,
				"PROJECT_HAS_OPEN_BUSINESS");
		assertThat(projectStatus(openContract.projectId())).isEqualTo("ACTIVE");

		for (String orderStatus : List.of("DRAFT", "CONFIRMED", "PARTIALLY_SHIPPED")) {
			ProjectContractFixture fixture = activeProjectWithEffectiveMain(admin, customerId, ownerUserId,
					"未终态订单项目" + orderStatus, "未终态订单主合同" + orderStatus);
			JsonNode closedMain = data(action(admin, "/api/admin/sales-project-contracts/" + fixture.mainContractId()
					+ "/close", Map.of("version", fixture.mainContractVersion(), "reason", "先结束合同")));
			assertThat(closedMain.get("status").asText()).isEqualTo("CLOSED");
			insertSalesOrder(customerId, fixture.projectId(), fixture.mainContractId(), orderStatus, LocalDate.now());
			assertError(action(admin, "/api/admin/sales-projects/" + fixture.projectId() + "/close",
					Map.of("version", projectVersion(fixture.projectId()), "reason", "存在未终态订单")), HttpStatus.CONFLICT,
					"PROJECT_HAS_OPEN_BUSINESS");
			assertThat(projectStatus(fixture.projectId())).isEqualTo("ACTIVE");
		}

		JsonNode draftProject = data(createProject(admin, projectPayload(customerId, ownerUserId, "草稿订单占用项目")));
		long draftProjectId = draftProject.get("id").longValue();
		JsonNode draftContract = data(createContract(admin, draftProjectId,
				contractPayload("MAIN", null, "草稿订单占用主合同", "1000.00", null)));
		insertSalesOrder(customerId, draftProjectId, draftContract.get("id").longValue(), "DRAFT", LocalDate.now());

		assertError(action(admin, "/api/admin/sales-projects/" + draftProjectId + "/cancel",
				Map.of("version", draftProject.get("version").longValue(), "reason", "存在关联订单")), HttpStatus.CONFLICT,
				"PROJECT_HAS_EFFECTIVE_BUSINESS");
		assertThat(projectStatus(draftProjectId)).isEqualTo("DRAFT");
	}

	@Test
	void contractStateAmountVersionReasonAndAuditRulesAreEnforced() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("SPC_CONTRACT_RULE_CUS_" + SEQUENCE.incrementAndGet(), "合同规则客户",
				"ENABLED");
		long ownerUserId = userId("admin");
		JsonNode project = data(createProject(admin, projectPayload(customerId, ownerUserId, "合同规则项目")));
		long projectId = project.get("id").longValue();

		assertError(createContract(admin, projectId,
				contractPayload("MAIN", null, "零金额主合同", "0.00", null)), HttpStatus.BAD_REQUEST,
				"CONTRACT_AMOUNT_INVALID");
		assertError(createContract(admin, projectId,
				contractPayload("MAIN", null, "负金额主合同", "-1.00", null)), HttpStatus.BAD_REQUEST,
				"CONTRACT_AMOUNT_INVALID");
		Map<String, Object> missingProjectVersion = updateProjectPayload(ownerUserId, "缺少版本", 0L);
		missingProjectVersion.remove("version");
		assertError(updateProject(admin, projectId, missingProjectVersion), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

		JsonNode mainContract = data(createContract(admin, projectId,
				contractPayload("MAIN", null, "合同规则主合同", "1000.00", null)));
		long mainContractId = mainContract.get("id").longValue();
		Map<String, Object> missingContractVersion = contractUpdatePayload("合同规则主合同改名", "1000.00", 0L);
		missingContractVersion.remove("version");
		assertError(updateContract(admin, mainContractId, missingContractVersion), HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR");
		assertError(updateContract(admin, mainContractId,
				contractUpdatePayload("合同规则主合同过期版本", "1000.00", 99L)), HttpStatus.CONFLICT,
				"CONTRACT_CONCURRENT_MODIFICATION");

		JsonNode updatedMain = data(updateContract(admin, mainContractId,
				contractUpdatePayload("合同规则主合同改名", "1100.00", mainContract.get("version").longValue())));
		assertAuditSummaryContains("SALES_PROJECT_CONTRACT_UPDATE", "SALES_PROJECT_CONTRACT", mainContractId,
				"更新字段");
		assertError(action(admin, "/api/admin/sales-project-contracts/" + mainContractId + "/activate", Map.of()),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		assertError(action(admin, "/api/admin/sales-project-contracts/" + mainContractId + "/activate",
				Map.of("version", 99L)), HttpStatus.CONFLICT, "CONTRACT_CONCURRENT_MODIFICATION");
		JsonNode effectiveMain = data(action(admin, "/api/admin/sales-project-contracts/" + mainContractId
				+ "/activate", Map.of("version", updatedMain.get("version").longValue())));

		assertError(action(admin, "/api/admin/sales-projects/" + projectId + "/activate", Map.of()),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		assertError(action(admin, "/api/admin/sales-projects/" + projectId + "/activate", Map.of("version", 99L)),
				HttpStatus.CONFLICT, "PROJECT_CONCURRENT_MODIFICATION");
		JsonNode activeProject = data(action(admin, "/api/admin/sales-projects/" + projectId + "/activate",
				Map.of("version", project.get("version").longValue())));
		assertThat(activeProject.get("status").asText()).isEqualTo("ACTIVE");

		assertError(createContract(admin, projectId,
				contractPayload("SUPPLEMENT", mainContractId, "零金额补充合同", "0.00", null)), HttpStatus.BAD_REQUEST,
				"CONTRACT_AMOUNT_INVALID");
		JsonNode supplement = data(createContract(admin, projectId,
				contractPayload("SUPPLEMENT", mainContractId, "待重校验补充合同", "50.00", null)));
		long supplementId = supplement.get("id").longValue();
		this.jdbcTemplate.update("update sal_project set status = 'DRAFT', updated_at = now(), version = version + 1 where id = ?",
				projectId);
		assertError(action(admin, "/api/admin/sales-project-contracts/" + supplementId + "/activate",
				Map.of("version", supplement.get("version").longValue())), HttpStatus.CONFLICT,
				"CONTRACT_PROJECT_NOT_ACTIVE");
		this.jdbcTemplate.update("update sal_project set status = 'ACTIVE', updated_at = now(), version = version + 1 where id = ?",
				projectId);
		this.jdbcTemplate.update("update sal_project_contract set status = 'CLOSED', close_reason = '并发关闭', closed_by = 'test', closed_at = now(), updated_at = now(), version = version + 1 where id = ?",
				mainContractId);
		assertError(action(admin, "/api/admin/sales-project-contracts/" + supplementId + "/activate",
				Map.of("version", supplement.get("version").longValue())), HttpStatus.CONFLICT,
				"CONTRACT_MAIN_NOT_EFFECTIVE");
		this.jdbcTemplate.update("update sal_project_contract set status = 'EFFECTIVE', close_reason = null, closed_by = null, closed_at = null, updated_at = now(), version = version + 1 where id = ?",
				mainContractId);

		JsonNode effectiveSupplement = data(action(admin, "/api/admin/sales-project-contracts/" + supplementId
				+ "/activate", Map.of("version", supplement.get("version").longValue())));
		assertError(updateContract(admin, supplementId,
				contractUpdatePayload("已生效补充合同改名", "60.00", effectiveSupplement.get("version").longValue())),
				HttpStatus.CONFLICT, "CONTRACT_STATUS_INVALID");
		assertError(action(admin, "/api/admin/sales-project-contracts/" + supplementId + "/terminate",
				Map.of("version", effectiveSupplement.get("version").longValue())), HttpStatus.BAD_REQUEST,
				"CONTRACT_REASON_REQUIRED");
		JsonNode terminatedSupplement = data(action(admin,
				"/api/admin/sales-project-contracts/" + supplementId + "/terminate",
				Map.of("version", effectiveSupplement.get("version").longValue(), "reason", "客户提前终止")));
		assertThat(terminatedSupplement.get("status").asText()).isEqualTo("TERMINATED");
		assertAuditSummaryContains("SALES_PROJECT_CONTRACT_TERMINATE", "SALES_PROJECT_CONTRACT", supplementId,
				"客户提前终止");
		assertError(action(admin, "/api/admin/sales-project-contracts/" + supplementId + "/close",
				Map.of("version", terminatedSupplement.get("version").longValue(), "reason", "终止后关闭")),
				HttpStatus.CONFLICT, "CONTRACT_STATUS_INVALID");

		JsonNode cancelDraft = data(createContract(admin, projectId,
				contractPayload("SUPPLEMENT", mainContractId, "待取消补充合同", "-20.00", null)));
		assertError(action(admin, "/api/admin/sales-project-contracts/" + cancelDraft.get("id").longValue() + "/cancel",
				Map.of("version", cancelDraft.get("version").longValue())), HttpStatus.BAD_REQUEST,
				"CONTRACT_REASON_REQUIRED");
		JsonNode cancelled = data(action(admin,
				"/api/admin/sales-project-contracts/" + cancelDraft.get("id").longValue() + "/cancel",
				Map.of("version", cancelDraft.get("version").longValue(), "reason", "录入错误取消")));
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		assertAuditSummaryContains("SALES_PROJECT_CONTRACT_CANCEL", "SALES_PROJECT_CONTRACT",
				cancelDraft.get("id").longValue(), "录入错误取消");

		assertError(action(admin, "/api/admin/sales-project-contracts/" + mainContractId + "/close",
				Map.of("version", contractVersion(mainContractId))), HttpStatus.BAD_REQUEST, "CONTRACT_REASON_REQUIRED");
		JsonNode closedMain = data(action(admin, "/api/admin/sales-project-contracts/" + mainContractId + "/close",
				Map.of("version", contractVersion(mainContractId), "reason", "主合同正常结束")));
		assertThat(closedMain.get("status").asText()).isEqualTo("CLOSED");
		assertAuditSummaryContains("SALES_PROJECT_CONTRACT_CLOSE", "SALES_PROJECT_CONTRACT", mainContractId,
				"主合同正常结束");
		assertThat(effectiveMain.get("status").asText()).isEqualTo("EFFECTIVE");
	}

	@Test
	void concurrentMainContractCreationAndStateActionsAllowSingleWinner() throws Exception {
		AuthenticatedSession firstAdmin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession secondAdmin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("SPC_CONCURRENT_CUS_" + SEQUENCE.incrementAndGet(), "并发客户", "ENABLED");
		long ownerUserId = userId("admin");
		JsonNode project = data(createProject(firstAdmin, projectPayload(customerId, ownerUserId, "并发主合同项目")));
		long projectId = project.get("id").longValue();

		ExecutorService executorService = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<ResponseEntity<String>> first = executorService.submit(() -> createContractAfterStart(firstAdmin,
					projectId, contractPayload("MAIN", null, "并发主合同一", "1000.00", null), start));
			Future<ResponseEntity<String>> second = executorService.submit(() -> createContractAfterStart(secondAdmin,
					projectId, contractPayload("MAIN", null, "并发主合同二", "2000.00", null), start));
			start.countDown();
			ResponseEntity<String> firstResponse = first.get(20, TimeUnit.SECONDS);
			ResponseEntity<String> secondResponse = second.get(20, TimeUnit.SECONDS);
			assertThat(List.of(code(firstResponse), code(secondResponse))).contains("OK").contains("CONFLICT");
		}
		finally {
			executorService.shutdownNow();
		}
		assertThat(nonCancelledMainContractCount(projectId)).isOne();

		long mainContractId = this.jdbcTemplate.queryForObject("""
				select id
				from sal_project_contract
				where project_id = ?
				and contract_type = 'MAIN'
				and status <> 'CANCELLED'
				""", Long.class, projectId);
		JsonNode effectiveMain = data(action(firstAdmin, "/api/admin/sales-project-contracts/" + mainContractId
				+ "/activate", Map.of("version", contractVersion(mainContractId))));
		assertThat(effectiveMain.get("status").asText()).isEqualTo("EFFECTIVE");

		assertSingleWinnerForConcurrentAction(firstAdmin, secondAdmin,
				"/api/admin/sales-projects/" + projectId + "/activate", Map.of("version", project.get("version").longValue()),
				"PROJECT_CONCURRENT_MODIFICATION");
		assertThat(projectStatus(projectId)).isEqualTo("ACTIVE");

		JsonNode closeProject = data(get("/api/admin/sales-projects/" + projectId, firstAdmin));
		JsonNode closedMain = data(action(firstAdmin, "/api/admin/sales-project-contracts/" + mainContractId + "/close",
				Map.of("version", contractVersion(mainContractId), "reason", "先结束合同")));
		assertThat(closedMain.get("status").asText()).isEqualTo("CLOSED");
		assertSingleWinnerForConcurrentAction(firstAdmin, secondAdmin, "/api/admin/sales-projects/" + projectId
				+ "/close", Map.of("version", closeProject.get("version").longValue(), "reason", "并发关闭项目"),
				"PROJECT_CONCURRENT_MODIFICATION");
		assertThat(projectStatus(projectId)).isEqualTo("CLOSED");

		ProjectContractFixture contractCloseFixture = activeProjectWithEffectiveMain(firstAdmin, customerId, ownerUserId,
				"并发关闭合同项目", "并发关闭合同");
		assertSingleWinnerForConcurrentAction(firstAdmin, secondAdmin,
				"/api/admin/sales-project-contracts/" + contractCloseFixture.mainContractId() + "/close",
				Map.of("version", contractCloseFixture.mainContractVersion(), "reason", "并发关闭合同"),
				"CONTRACT_CONCURRENT_MODIFICATION");
		assertThat(contractStatus(contractCloseFixture.mainContractId())).isEqualTo("CLOSED");
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
		String orderNo = "SO-RESTRICTED-" + SEQUENCE.incrementAndGet();
		insertAudit("SALES_ORDER_PROJECT_LINK", "SALES_PROJECT", projectId,
				"订单 " + orderNo + " 项目合同关联 未关联 -> " + project.get("projectNo").asText() + "/"
						+ mainContract.get("contractNo").asText());

		AuthenticatedSession viewer = createUserAndLogin("project-viewer-", "PROJECT_VIEWER_",
				List.of("sales:project:view"));
		AuthenticatedSession orderViewer = createUserAndLogin("project-order-viewer-", "PROJECT_ORDER_VIEWER_",
				List.of("sales:project:view", "sales:order:view"));
		AuthenticatedSession contractViewer = createUserAndLogin("project-contract-viewer-", "PROJECT_CONTRACT_VIEWER_",
				List.of("sales:project:view", "sales:contract:view"));

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
		assertThat(detail.getBody()).doesNotContain(orderNo);

		ResponseEntity<String> orderAllowedDetail = get("/api/admin/sales-projects/" + projectId, orderViewer);
		assertOk(orderAllowedDetail);
		assertThat(orderAllowedDetail.getBody()).contains(orderNo);
		assertThat(orderAllowedDetail.getBody()).doesNotContain(mainContract.get("contractNo").asText());

		ResponseEntity<String> contractAllowedDetail = get("/api/admin/sales-projects/" + projectId, contractViewer);
		assertOk(contractAllowedDetail);
		assertThat(contractAllowedDetail.getBody()).doesNotContain(orderNo);
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
		assertThat(nullable("sal_project", "owner_user_id")).isFalse();
		assertThat(nullable("sal_project_contract", "signed_date")).isFalse();
	}

	@Test
	void projectCreateRequiresOwnerAndOwnerCandidatesAllowProjectViewPermission() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("SPC_REQUIRED_CUS_" + SEQUENCE.incrementAndGet(), "必填客户", "ENABLED");
		Map<String, Object> missingOwner = projectPayload(customerId, userId("admin"), "缺少负责人");
		missingOwner.remove("ownerUserId");
		assertError(createProject(admin, missingOwner), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

		AuthenticatedSession viewer = createUserAndLogin("owner-candidate-viewer-", "OWNER_CANDIDATE_VIEWER_",
				List.of("sales:project:view"));
		ResponseEntity<String> candidates = get("/api/admin/sales-projects/owner-candidates?keyword=admin", viewer);

		assertOk(candidates);
		assertThat(data(candidates).get("items").size()).isGreaterThan(0);
	}

	@Test
	void contractCreateRequiresSignedDateAndContractListIsPagedFilteredAndOrdered() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("SPC_CONTRACT_LIST_CUS_" + SEQUENCE.incrementAndGet(), "合同列表客户",
				"ENABLED");
		long ownerUserId = userId("admin");
		JsonNode project = data(createProject(admin, projectPayload(customerId, ownerUserId, "合同列表项目")));
		long projectId = project.get("id").longValue();
		Map<String, Object> missingSignedDate = contractPayload("MAIN", null, "缺少签订日期", "1000.00", null);
		missingSignedDate.remove("signedDate");
		assertError(createContract(admin, projectId, missingSignedDate), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

		JsonNode mainContract = data(createContract(admin, projectId,
				contractPayload("MAIN", null, "列表主合同", "1000.00", "LIST-MAIN")));
		action(admin, "/api/admin/sales-project-contracts/" + mainContract.get("id").longValue() + "/activate",
				Map.of("version", mainContract.get("version").longValue()));
		JsonNode activeProject = data(action(admin, "/api/admin/sales-projects/" + projectId + "/activate",
				Map.of("version", project.get("version").longValue())));
		assertThat(activeProject.get("status").asText()).isEqualTo("ACTIVE");
		createContract(admin, projectId,
				contractPayload("SUPPLEMENT", mainContract.get("id").longValue(), "列表补充合同一", "100.00",
						"LIST-SUP-1"));
		createContract(admin, projectId,
				contractPayload("SUPPLEMENT", mainContract.get("id").longValue(), "列表补充合同二", "-50.00",
						"LIST-SUP-2"));

		ResponseEntity<String> supplements = get("/api/admin/sales-projects/" + projectId
				+ "/contracts?contractType=SUPPLEMENT&keyword=列表补充&page=1&pageSize=1", admin);
		assertOk(supplements);
		JsonNode page = data(supplements);
		assertThat(page.get("total").longValue()).isEqualTo(2L);
		assertThat(page.get("items").size()).isEqualTo(1);
		assertThat(page.get("items").get(0).get("contractType").asText()).isEqualTo("SUPPLEMENT");
		assertThat(page.get("items").get(0).get("version").isNumber()).isTrue();

		ResponseEntity<String> allContracts = get("/api/admin/sales-projects/" + projectId + "/contracts?pageSize=20",
				admin);
		assertOk(allContracts);
		assertThat(data(allContracts).get("items").get(0).get("contractType").asText()).isEqualTo("MAIN");
	}

	@Test
	void draftProjectNameIsEditableButActiveProjectNameIsLockedAndAudited() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("SPC_RENAME_CUS_" + SEQUENCE.incrementAndGet(), "改名客户", "ENABLED");
		long ownerUserId = userId("admin");
		JsonNode project = data(createProject(admin, projectPayload(customerId, ownerUserId, "草稿改名项目")));
		long projectId = project.get("id").longValue();
		Map<String, Object> draftUpdate = updateProjectPayload(ownerUserId, "草稿改名备注",
				project.get("version").longValue());
		draftUpdate.put("name", "草稿项目新名称");

		JsonNode renamed = data(updateProject(admin, projectId, draftUpdate));

		assertThat(renamed.get("name").asText()).isEqualTo("草稿项目新名称");
		assertAuditSummaryContains("SALES_PROJECT_UPDATE", "SALES_PROJECT", projectId, "name");

		JsonNode mainContract = data(createContract(admin, projectId,
				contractPayload("MAIN", null, "改名主合同", "1000.00", null)));
		action(admin, "/api/admin/sales-project-contracts/" + mainContract.get("id").longValue() + "/activate",
				Map.of("version", mainContract.get("version").longValue()));
		JsonNode activeProject = data(action(admin, "/api/admin/sales-projects/" + projectId + "/activate",
				Map.of("version", renamed.get("version").longValue())));
		Map<String, Object> activeUpdate = updateProjectPayload(ownerUserId, "ACTIVE 改名备注",
				activeProject.get("version").longValue());
		activeUpdate.put("name", "ACTIVE 不允许改名");

		assertError(updateProject(admin, projectId, activeUpdate), HttpStatus.CONFLICT, "PROJECT_STATUS_INVALID");
	}

	@Test
	void projectCancelRejectsClosedOrTerminatedContractsAndAnyLinkedOrder() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("SPC_CANCEL_CUS_" + SEQUENCE.incrementAndGet(), "取消客户", "ENABLED");
		long ownerUserId = userId("admin");
		JsonNode project = data(createProject(admin, projectPayload(customerId, ownerUserId, "取消项目")));
		long projectId = project.get("id").longValue();
		JsonNode mainContract = data(createContract(admin, projectId,
				contractPayload("MAIN", null, "取消主合同", "1000.00", null)));
		JsonNode effectiveMain = data(action(admin,
				"/api/admin/sales-project-contracts/" + mainContract.get("id").longValue() + "/activate",
				Map.of("version", mainContract.get("version").longValue())));
		action(admin, "/api/admin/sales-project-contracts/" + mainContract.get("id").longValue() + "/close",
				Map.of("version", effectiveMain.get("version").longValue(), "reason", "已结束合同"));

		assertError(action(admin, "/api/admin/sales-projects/" + projectId + "/cancel",
				Map.of("version", project.get("version").longValue(), "reason", "存在历史合同")), HttpStatus.CONFLICT,
				"PROJECT_HAS_EFFECTIVE_BUSINESS");
	}

	@Test
	void projectDetailSalesOrderSummaryIncludesStatusBucketsAndLatestOrderDate() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("SPC_ORDER_SUM_CUS_" + SEQUENCE.incrementAndGet(), "订单摘要客户", "ENABLED");
		long ownerUserId = userId("admin");
		JsonNode project = data(createProject(admin, projectPayload(customerId, ownerUserId, "订单摘要项目")));
		long projectId = project.get("id").longValue();
		JsonNode mainContract = data(createContract(admin, projectId,
				contractPayload("MAIN", null, "订单摘要主合同", "1000.00", null)));
		action(admin, "/api/admin/sales-project-contracts/" + mainContract.get("id").longValue() + "/activate",
				Map.of("version", mainContract.get("version").longValue()));
		action(admin, "/api/admin/sales-projects/" + projectId + "/activate",
				Map.of("version", project.get("version").longValue()));
		insertSalesOrder(customerId, projectId, mainContract.get("id").longValue(), "DRAFT", LocalDate.now().minusDays(1));
		insertSalesOrder(customerId, projectId, mainContract.get("id").longValue(), "CONFIRMED", LocalDate.now());

		JsonNode summary = data(get("/api/admin/sales-projects/" + projectId, admin)).get("salesOrderSummary");

		assertThat(summary.get("salesOrderCount").longValue()).isEqualTo(2L);
		assertThat(summary.hasNonNull("draftCount")).isTrue();
		assertThat(summary.get("draftCount").longValue()).isOne();
		assertThat(summary.hasNonNull("confirmedCount")).isTrue();
		assertThat(summary.get("confirmedCount").longValue()).isOne();
		assertThat(summary.hasNonNull("latestOrderDate")).isTrue();
		assertThat(summary.get("latestOrderDate").asText()).isEqualTo(LocalDate.now().toString());
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

	private Map<String, Object> contractUpdatePayload(String name, String amount, long version) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("name", name);
		payload.put("signedDate", LocalDate.now().toString());
		payload.put("effectiveStartDate", LocalDate.now().toString());
		payload.put("effectiveEndDate", LocalDate.now().plusDays(60).toString());
		payload.put("amount", amount);
		payload.put("externalContractNo", null);
		payload.put("remark", name + "备注");
		payload.put("version", version);
		return payload;
	}

	private ProjectContractFixture activeProjectWithEffectiveMain(AuthenticatedSession admin, long customerId,
			long ownerUserId, String projectRemark, String contractName) throws Exception {
		JsonNode project = data(createProject(admin, projectPayload(customerId, ownerUserId, projectRemark)));
		long projectId = project.get("id").longValue();
		JsonNode mainContract = data(createContract(admin, projectId,
				contractPayload("MAIN", null, contractName, "1000.00", null)));
		long mainContractId = mainContract.get("id").longValue();
		JsonNode effectiveMain = data(action(admin, "/api/admin/sales-project-contracts/" + mainContractId
				+ "/activate", Map.of("version", mainContract.get("version").longValue())));
		JsonNode activeProject = data(action(admin, "/api/admin/sales-projects/" + projectId + "/activate",
				Map.of("version", project.get("version").longValue())));
		return new ProjectContractFixture(projectId, activeProject.get("version").longValue(), mainContractId,
				effectiveMain.get("version").longValue());
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

	private long insertSalesOrder(long customerId, long projectId, long contractId, String status, LocalDate orderDate) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark, project_id, contract_id,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, "SPC-PSO-" + suffix, customerId, orderDate, orderDate.plusDays(3), status,
				"订单摘要", projectId, contractId);
	}

	private String projectStatus(long projectId) {
		return this.jdbcTemplate.queryForObject("select status from sal_project where id = ?", String.class,
				projectId);
	}

	private long projectVersion(long projectId) {
		return this.jdbcTemplate.queryForObject("select version from sal_project where id = ?", Long.class, projectId);
	}

	private String contractStatus(long contractId) {
		return this.jdbcTemplate.queryForObject("select status from sal_project_contract where id = ?", String.class,
				contractId);
	}

	private long contractVersion(long contractId) {
		return this.jdbcTemplate.queryForObject("select version from sal_project_contract where id = ?", Long.class,
				contractId);
	}

	private long nonCancelledMainContractCount(long projectId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_project_contract
				where project_id = ?
				and contract_type = 'MAIN'
				and status <> 'CANCELLED'
				""", Long.class, projectId);
	}

	private void insertAudit(String action, String targetType, long targetId, String targetSummary) {
		this.jdbcTemplate.update("""
				insert into sys_audit_log (
					operator_username, action, target_type, target_id, target_summary, request_method, request_path,
					ip_address, result, created_at
				)
				values ('admin', ?, ?, ?, ?, 'PUT', '/api/admin/sales/orders', '127.0.0.1', 'SUCCESS', now())
				""", action, targetType, Long.toString(targetId), targetSummary);
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

	private ResponseEntity<String> updateContract(AuthenticatedSession session, long contractId,
			Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/sales-project-contracts/" + contractId, body, session);
	}

	private ResponseEntity<String> action(AuthenticatedSession session, String path, Map<String, Object> body) {
		return exchange(HttpMethod.PUT, path, body, session);
	}

	private ResponseEntity<String> createContractAfterStart(AuthenticatedSession session, long projectId,
			Map<String, Object> body, CountDownLatch start) throws Exception {
		start.await(10, TimeUnit.SECONDS);
		return createContract(session, projectId, body);
	}

	private ResponseEntity<String> actionAfterStart(AuthenticatedSession session, String path, Map<String, Object> body,
			CountDownLatch start) throws Exception {
		start.await(10, TimeUnit.SECONDS);
		return action(session, path, body);
	}

	private void assertSingleWinnerForConcurrentAction(AuthenticatedSession firstAdmin,
			AuthenticatedSession secondAdmin, String path, Map<String, Object> body, String loserCode)
			throws Exception {
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<ResponseEntity<String>> first = executorService
				.submit(() -> actionAfterStart(firstAdmin, path, body, start));
			Future<ResponseEntity<String>> second = executorService
				.submit(() -> actionAfterStart(secondAdmin, path, body, start));
			start.countDown();
			ResponseEntity<String> firstResponse = first.get(20, TimeUnit.SECONDS);
			ResponseEntity<String> secondResponse = second.get(20, TimeUnit.SECONDS);
			assertThat(List.of(code(firstResponse), code(secondResponse))).contains("OK").contains(loserCode);
		}
		finally {
			executorService.shutdownNow();
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

	private boolean nullable(String tableName, String columnName) {
		return "YES".equals(this.jdbcTemplate.queryForObject("""
				select is_nullable
				from information_schema.columns
				where table_schema = 'public'
				and table_name = ?
				and column_name = ?
				""", String.class, tableName, columnName));
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

	private record ProjectContractFixture(long projectId, long projectVersion, long mainContractId,
			long mainContractVersion) {
	}

}
