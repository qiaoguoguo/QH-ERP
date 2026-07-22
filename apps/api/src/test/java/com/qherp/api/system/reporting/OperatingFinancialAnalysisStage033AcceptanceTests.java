package com.qherp.api.system.reporting;

import com.qherp.api.support.PostgresIntegrationTest;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=stage033-operating-financial-analysis")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OperatingFinancialAnalysisStage033AcceptanceTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private static final List<String> OPERATING_FINANCE_PERMISSIONS = List.of(
			"report:operating-finance:view", "report:project-profit:view",
			"report:contract-collection:view", "report:procurement-variance:view",
			"report:inventory-capital:view", "report:receivable-payable:view",
			"report:operating-accounting:view", "report:financial-summary:view");

	private static final List<String> LEGACY_REPORT_PATHS = List.of(
			"/api/admin/reports/sales-summary", "/api/admin/reports/procurement-summary",
			"/api/admin/reports/inventory-stock-flow", "/api/admin/reports/production-execution",
			"/api/admin/reports/cost-collection", "/api/admin/reports/settlement-summary",
			"/api/admin/reports/exceptions");

	private static final List<ReportEndpoint> SNAPSHOT_SUPPORTED_ENDPOINTS = List.of(
			new ReportEndpoint("/api/admin/reports/project-profit", "report:project-profit:view"),
			new ReportEndpoint("/api/admin/reports/contract-collections", "report:contract-collection:view"),
			new ReportEndpoint("/api/admin/reports/procurement-variances", "report:procurement-variance:view"),
			new ReportEndpoint("/api/admin/reports/inventory-capital", "report:inventory-capital:view"),
			new ReportEndpoint("/api/admin/reports/receivable-payable", "report:receivable-payable:view"));

	private static final List<ReportEndpoint> SNAPSHOT_UNSUPPORTED_ENDPOINTS = List.of(
			new ReportEndpoint("/api/admin/reports/operating-accounting-reconciliation",
					"report:operating-accounting:view"),
			new ReportEndpoint("/api/admin/reports/financial-summary", "report:financial-summary:view"));

	private static final Map<String, String> ACCEPTANCE_MAPPING = acceptanceMapping();

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	@Order(1)
	void 验收资产映射必须覆盖A01到A21() {
		List<String> expectedIds = IntStream.rangeClosed(1, 21)
			.mapToObj((value) -> "A%02d".formatted(value))
			.toList();

		assertThat(ACCEPTANCE_MAPPING.keySet()).containsExactlyElementsOf(expectedIds);
		assertThat(ACCEPTANCE_MAPPING.values()).allSatisfy((description) -> assertThat(description).isNotBlank());
	}

	@Test
	@Order(2)
	void a01旧经营报表契约必须保持summaryItems追溯筛选和权限兼容() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String emptyPeriod = "?dateFrom=2096-01-01&dateTo=2096-01-31&page=1&pageSize=20";

		JsonNode overview = data(get(admin, "/api/admin/reports/overview?dateFrom=2096-01-01&dateTo=2096-01-31"));
		assertThat(overview.get("formalAccounting").booleanValue()).isFalse();
		assertForbiddenFinancialStatementTerms(overview.toString());

		for (String path : LEGACY_REPORT_PATHS) {
			JsonNode report = data(get(admin, path + emptyPeriod));
			assertReportPage(report);
		}

		AuthenticatedSession noReport = createUserAndLogin("033-no-report-", "033_NO_REPORT_", List.of());
		assertError(get(noReport, "/api/admin/reports/sales-summary" + emptyPeriod),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	@Test
	@Order(3)
	void a02到a17新增固定报表必须复用014契约并保持分页完整汇总和候选直达() throws Exception {
		OperatingFinanceFixture fixture = seedProjectProfitFixture(12);
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Map<String, String> upstreamBefore = upstreamFactsSummary();

		JsonNode overview = data(get(admin,
				"/api/admin/reports/operating-finance-overview?periodCode=2097-01&analysisMode=LIVE"));
		assertThat(overview.toString()).contains("project-profit", "contract-collection",
				"procurement-variance", "inventory-capital", "receivable-payable",
				"operating-accounting-reconciliation", "financial-summary");
		assertForbiddenFinancialStatementTerms(overview.toString());

		JsonNode firstPage = data(get(admin,
				"/api/admin/reports/project-profit?periodCode=2097-01&analysisMode=LIVE&page=1&pageSize=10"));
		assertReportPage(firstPage);
		assertThat(firstPage.get("total").asInt()).isGreaterThanOrEqualTo(12);
		assertThat(firstPage.get("items")).hasSizeLessThanOrEqualTo(10);
		assertSummaryCountAtLeast(firstPage, 12);

		JsonNode directProject = data(get(admin, "/api/admin/reports/project-profit?periodCode=2097-01"
				+ "&analysisMode=LIVE&projectId=" + fixture.lastProjectId()
				+ "&page=1&pageSize=10"));
		assertReportPage(directProject);
		assertThat(directProject.get("items")).hasSize(1);
		assertThat(directProject.toString()).contains(fixture.lastProjectNo());

		JsonNode projectDetail = data(get(admin, "/api/admin/reports/project-profit/"
				+ fixture.firstProjectId() + "?periodCode=2097-01&analysisMode=LIVE"));
		assertThat(projectDetail.toString()).contains("1000.00", "650.00", "350.00", "COMPLETE");
		assertThat(projectDetail.toString()).doesNotContain("\"shipmentGrossMarginRate\":0.0");

		for (ReportEndpoint endpoint : SNAPSHOT_SUPPORTED_ENDPOINTS) {
			JsonNode report = data(get(admin, endpoint.path()
					+ "?periodCode=2097-01&analysisMode=LIVE&page=1&pageSize=10"));
			assertReportPage(report);
			assertForbiddenFinancialStatementTerms(report.toString());
		}

		assertThat(upstreamFactsSummary()).isEqualTo(upstreamBefore);
	}

	@Test
	@Order(4)
	void a05a10a12会计对照必须只取已记账项目辅助发生额且零正式事实不可伪装为零利润() throws Exception {
		OperatingFinanceFixture fixture = seedProjectProfitFixture(1);
		moveProjectCostCutoff(fixture.firstProjectId(), LocalDate.of(2097, 2, 28));
		seedPostedAccountingEntries(fixture.firstProjectId(), fixture.firstProjectNo());
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		JsonNode reconciliation = data(get(admin,
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2097-02"
						+ "&analysisMode=LIVE&projectId=" + fixture.firstProjectId()
						+ "&page=1&pageSize=10"));
		assertReportPage(reconciliation);
		assertThat(reconciliation.toString()).contains("900.00", "500.00", "400.00", "77.00");
		assertThat(reconciliation.toString()).contains("DIFFERENT");
		assertThat(reconciliation.toString()).doesNotContain("DRAFT", "UNPOSTED");

		JsonNode zeroFormalFacts = data(get(admin,
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2097-09"
						+ "&analysisMode=LIVE&page=1&pageSize=10"));
		assertThat(zeroFormalFacts.toString()).contains("UNAVAILABLE");
		assertThat(zeroFormalFacts.toString()).doesNotContain("\"accountingProfit\":\"0.00\"");
	}

	@Test
	@Order(5)
	void a12业务快照只适用于五个经营侧报表且后两类不得实时冒充快照() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String snapshotQuery = "?periodCode=2097-03&analysisMode=BUSINESS_SNAPSHOT&page=1&pageSize=10";

		for (ReportEndpoint endpoint : SNAPSHOT_SUPPORTED_ENDPOINTS) {
			ResponseEntity<String> response = get(admin, endpoint.path() + snapshotQuery);
			if (response.getStatusCode() == HttpStatus.OK) {
				JsonNode report = data(response);
				assertThat(report.toString()).doesNotContain("\"analysisMode\":\"LIVE\"");
			}
			else {
				assertControlledSnapshotError(response, "REPORT_SNAPSHOT_NOT_INCLUDED", "REPORT_PERIOD_UNAVAILABLE");
			}
		}

		for (ReportEndpoint endpoint : SNAPSHOT_UNSUPPORTED_ENDPOINTS) {
			ResponseEntity<String> response = get(admin, endpoint.path() + snapshotQuery);
			if (response.getStatusCode() == HttpStatus.OK) {
				JsonNode report = data(response);
				assertThat(report.toString())
					.containsAnyOf("UNAVAILABLE", "REPORT_BASIS_INVALID", "REPORT_PERIOD_UNAVAILABLE");
				assertThat(report.toString()).doesNotContain("\"analysisMode\":\"LIVE\"", "CURRENT");
			}
			else {
				assertControlledSnapshotError(response, "REPORT_BASIS_INVALID", "REPORT_PERIOD_UNAVAILABLE");
			}
		}
	}

	@Test
	@Order(6)
	void a15权限脱敏必须同时收敛报表金额来源总账库存银行税务敏感信息() throws Exception {
		OperatingFinanceFixture fixture = seedProjectProfitFixture(1);
		AuthenticatedSession noReport = createUserAndLogin("033-denied-", "033_DENIED_",
				List.of("cost:project-cost:amount-view", "gl:amount:view", "financial-close:amount:view"));
		assertError(get(noReport,
				"/api/admin/reports/project-profit?periodCode=2097-01&analysisMode=LIVE&page=1&pageSize=10"),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		List<String> reportOnlyPermissions = new ArrayList<>();
		reportOnlyPermissions.add("report");
		reportOnlyPermissions.addAll(OPERATING_FINANCE_PERMISSIONS);
		AuthenticatedSession reportOnly = createUserAndLogin("033-report-only-", "033_REPORT_ONLY_",
				reportOnlyPermissions);
		JsonNode restricted = data(get(reportOnly,
				"/api/admin/reports/project-profit?periodCode=2097-01&analysisMode=LIVE&projectId="
						+ fixture.firstProjectId() + "&page=1&pageSize=10"));
		assertThat(restricted.toString()).contains("RESTRICTED");
		assertThat(restricted.toString()).doesNotContain("1000.00", "650.00", "350.00",
				fixture.firstProjectNo() + "-SOURCE");
	}

	@Test
	@Order(7)
	void a19a20正式零事实合法且对象一致性规则不少于八不写死十八() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode financialSummary = data(get(admin,
				"/api/admin/reports/financial-summary?periodCode=2097-10&analysisMode=LIVE"));
		assertThat(financialSummary.toString()).contains("UNAVAILABLE");
		assertForbiddenFinancialStatementTerms(financialSummary.toString());

		ensureAvailableFileObjects();
		assertThat(availableFileObjectCount()).isGreaterThanOrEqualTo(8L);
		assertThat(validatorObjectRuleDoesNotFixEighteen()).isTrue();
	}

	@Test
	@Order(8)
	void a06到a09四类经营侧报表必须读取真实正例而不是固定空态() throws Exception {
		Stage033PositiveFixture fixture = seedStage033PositiveFixture();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		List<String> reportOnlyPermissions = new ArrayList<>();
		reportOnlyPermissions.add("report");
		reportOnlyPermissions.addAll(List.of("report:contract-collection:view",
				"report:procurement-variance:view", "report:inventory-capital:view",
				"report:receivable-payable:view"));
		AuthenticatedSession reportOnly = createUserAndLogin("033-positive-report-only-",
				"033_POSITIVE_REPORT_ONLY_", reportOnlyPermissions);
		AuthenticatedSession denied = createUserAndLogin("033-positive-denied-", "033_POSITIVE_DENIED_",
				List.of());
		SoftAssertions softly = new SoftAssertions();

		String contractQuery = "periodCode=2026-06&analysisMode=LIVE&keyword="
				+ encoded(fixture.contract().marker());
		JsonNode contractPage = data(get(admin, "/api/admin/reports/contract-collections?" + contractQuery
				+ "&page=1&pageSize=1"));
		JsonNode contractAll = data(get(admin, "/api/admin/reports/contract-collections?" + contractQuery
				+ "&page=1&pageSize=20"));
		assertReportPage(contractPage, 1);
		assertReportPage(contractAll);
		assertPagedSummarySeesFullSet(softly, "合同回款", contractPage, 2,
				List.of("1500.00", "1000.00", "300.00", "700.00", "600.00", "200.00"));
		assertReportTextContains(softly, "合同回款", contractAll, List.of(fixture.contract().mainContractNo(),
				fixture.contract().supplementContractNo(), fixture.contract().salesInvoiceNo(),
				fixture.contract().receiptNo(), fixture.contract().receivableNo()));
		assertTraceFromItemContaining(softly, admin, contractAll, "/api/admin/reports/contract-collections/traces",
				"合同回款", fixture.contract().mainContractNo(),
				List.of(new TraceExpectation("SALES_PROJECT_CONTRACT", fixture.contract().mainContractId()),
						new TraceExpectation("SALES_INVOICE", fixture.contract().salesInvoiceId()),
						new TraceExpectation("RECEIVABLE", fixture.contract().receivableId()),
						new TraceExpectation("RECEIPT", fixture.contract().receiptId()),
						new TraceExpectation("SETTLEMENT_ALLOCATION", fixture.contract().allocationId())));
		assertTraceMaskedWhenSourcePermissionsMissing(softly, reportOnly, contractAll,
				"/api/admin/reports/contract-collections/traces", "合同回款", fixture.contract().mainContractNo());
		assertEndpointForbidden(softly, denied, "/api/admin/reports/contract-collections?" + contractQuery,
				"合同回款");

		String procurementQuery = "periodCode=2026-06&analysisMode=LIVE&keyword="
				+ encoded(fixture.procurement().marker());
		JsonNode procurementPage = data(get(admin, "/api/admin/reports/procurement-variances?"
				+ procurementQuery + "&page=1&pageSize=1"));
		JsonNode procurementAll = data(get(admin, "/api/admin/reports/procurement-variances?"
				+ procurementQuery + "&page=1&pageSize=20"));
		assertReportPage(procurementPage, 1);
		assertReportPage(procurementAll);
		assertPagedSummarySeesFullSet(softly, "采购差异", procurementPage, 3,
				List.of("800.00", "950.00", "200.00", "150.00"));
		assertReportTextContains(softly, "采购差异", procurementAll,
				List.of(fixture.procurement().projectPurchaseOrderNo(),
						fixture.procurement().publicPurchaseOrderNo(), fixture.procurement().outsourcingOrderNo(),
						fixture.procurement().purchaseInvoiceNo(), fixture.procurement().paymentNo(), "PROJECT",
						"PUBLIC", "OUTSOURCING"));
		assertTraceFromItemContaining(softly, admin, procurementAll,
				"/api/admin/reports/procurement-variances/traces", "采购差异",
				fixture.procurement().projectPurchaseOrderNo(),
				List.of(new TraceExpectation("PROCUREMENT_ORDER", fixture.procurement().projectPurchaseOrderId()),
						new TraceExpectation("PURCHASE_RECEIPT", fixture.procurement().projectPurchaseReceiptId()),
						new TraceExpectation("PURCHASE_RETURN", fixture.procurement().purchaseReturnId()),
						new TraceExpectation("PURCHASE_INVOICE", fixture.procurement().purchaseInvoiceId()),
						new TraceExpectation("PAYMENT", fixture.procurement().paymentId())));
		assertTraceFromItemContaining(softly, admin, procurementAll,
				"/api/admin/reports/procurement-variances/traces", "采购差异外协",
				fixture.procurement().outsourcingOrderNo(),
				List.of(new TraceExpectation("OUTSOURCING_ORDER", fixture.procurement().outsourcingOrderId()),
						new TraceExpectation("OUTSOURCING_RECEIPT", fixture.procurement().outsourcingReceiptId()),
						new TraceExpectation("PURCHASE_INVOICE", fixture.procurement().outsourcingInvoiceId())));
		assertTraceMaskedWhenSourcePermissionsMissing(softly, reportOnly, procurementAll,
				"/api/admin/reports/procurement-variances/traces", "采购差异",
				fixture.procurement().projectPurchaseOrderNo());
		assertEndpointForbidden(softly, denied, "/api/admin/reports/procurement-variances?" + procurementQuery,
				"采购差异");

		String inventoryQuery = "periodCode=2026-06&analysisMode=LIVE&keyword="
				+ encoded(fixture.inventory().marker());
		JsonNode inventoryPage = data(get(admin, "/api/admin/reports/inventory-capital?" + inventoryQuery
				+ "&page=1&pageSize=1"));
		JsonNode inventoryAll = data(get(admin, "/api/admin/reports/inventory-capital?" + inventoryQuery
				+ "&page=1&pageSize=20"));
		JsonNode inventorySnapshot = data(get(admin,
				"/api/admin/reports/inventory-capital?periodCode=2026-06&analysisMode=BUSINESS_SNAPSHOT&keyword="
						+ encoded(fixture.inventory().marker()) + "&page=1&pageSize=1"));
		assertReportPage(inventoryPage, 1);
		assertReportPage(inventoryAll);
		assertReportPage(inventorySnapshot, 1);
		assertPagedSummarySeesFullSet(softly, "库存资金", inventoryPage, 3,
				List.of("18.000", "325.00", "210.00", "115.00", "3.000"));
		assertReportTextContains(softly, "库存资金", inventoryAll,
				List.of(fixture.inventory().marker(), fixture.inventory().projectNo(), "PUBLIC", "PROJECT",
						"VALUED", "LEGACY_UNVALUED"));
		assertReportTextContains(softly, "库存资金快照", inventorySnapshot,
				List.of("BUSINESS_SNAPSHOT", fixture.inventory().snapshotMarker(), "210.00"));
		softly.assertThat(inventorySnapshot.toString()).as("库存资金快照不得用 LIVE 实时金额冒充")
			.doesNotContain("325.00");
		assertTraceFromItemContaining(softly, admin, inventoryAll, "/api/admin/reports/inventory-capital/traces",
				"库存资金", fixture.inventory().projectNo(),
				List.of(new TraceExpectation("INVENTORY_BALANCE", fixture.inventory().projectValuedBalanceId()),
						new TraceExpectation("INVENTORY_MOVEMENT", fixture.inventory().projectValuedMovementId())));
		assertTraceMaskedWhenSourcePermissionsMissing(softly, reportOnly, inventoryAll,
				"/api/admin/reports/inventory-capital/traces", "库存资金", fixture.inventory().projectNo());
		assertEndpointForbidden(softly, denied, "/api/admin/reports/inventory-capital?" + inventoryQuery,
				"库存资金");

		String receivablePayableQuery = "periodCode=2026-06&analysisMode=LIVE&keyword="
				+ encoded(fixture.receivablePayable().marker());
		JsonNode receivablePayablePage = data(get(admin, "/api/admin/reports/receivable-payable?"
				+ receivablePayableQuery + "&page=1&pageSize=1"));
		JsonNode receivablePayableAll = data(get(admin, "/api/admin/reports/receivable-payable?"
				+ receivablePayableQuery + "&page=1&pageSize=20"));
		assertReportPage(receivablePayablePage, 1);
		assertReportPage(receivablePayableAll);
		assertPagedSummarySeesFullSet(softly, "往来账龄", receivablePayablePage, 6,
				List.of("1400.00", "200.00", "120.00", "90.00", "1200.00", "1300.00"));
		assertReportTextContains(softly, "往来账龄", receivablePayableAll,
				List.of(fixture.receivablePayable().notDueReceivableNo(),
						fixture.receivablePayable().days1To30ReceivableNo(),
						fixture.receivablePayable().days31To60ReceivableNo(),
						fixture.receivablePayable().days61To90ReceivableNo(),
						fixture.receivablePayable().daysOver90ReceivableNo(),
						fixture.receivablePayable().payableNo()));
		assertReportTextContainsAnyOf(softly, "往来账龄未到期桶", receivablePayableAll, "NOT_DUE", "未到期");
		assertReportTextContainsAnyOf(softly, "往来账龄1到30天桶", receivablePayableAll, "DAYS_1_30", "1-30");
		assertReportTextContainsAnyOf(softly, "往来账龄31到60天桶", receivablePayableAll, "DAYS_31_60", "31-60");
		assertReportTextContainsAnyOf(softly, "往来账龄61到90天桶", receivablePayableAll, "DAYS_61_90", "61-90");
		assertReportTextContainsAnyOf(softly, "往来账龄90天以上桶", receivablePayableAll, "DAYS_OVER_90", "90天以上");
		assertTraceFromItemContaining(softly, admin, receivablePayableAll,
				"/api/admin/reports/receivable-payable/traces", "往来应收",
				fixture.receivablePayable().days31To60ReceivableNo(),
				List.of(new TraceExpectation("RECEIVABLE", fixture.receivablePayable().allocatedReceivableId()),
						new TraceExpectation("RECEIPT", fixture.receivablePayable().receiptId()),
						new TraceExpectation("SETTLEMENT_ALLOCATION", fixture.receivablePayable().allocationId())));
		assertTraceFromItemContaining(softly, admin, receivablePayableAll,
				"/api/admin/reports/receivable-payable/traces", "往来应付",
				fixture.receivablePayable().payableNo(),
				List.of(new TraceExpectation("PAYABLE", fixture.receivablePayable().payableId()),
						new TraceExpectation("PAYMENT", fixture.receivablePayable().paymentId()),
						new TraceExpectation("PURCHASE_RECEIPT", fixture.receivablePayable().purchaseReceiptId())));
		assertTraceMaskedWhenSourcePermissionsMissing(softly, reportOnly, receivablePayableAll,
				"/api/admin/reports/receivable-payable/traces", "往来账龄",
				fixture.receivablePayable().days31To60ReceivableNo());
		assertEndpointForbidden(softly, denied, "/api/admin/reports/receivable-payable?" + receivablePayableQuery,
				"往来账龄");

		softly.assertAll();
	}

	@Test
	@Order(9)
	void a12整改业务快照读取必须按当前请求筛选分页并重新执行读取者权限() throws Exception {
		SnapshotProbeFixture fixture = seedSnapshotRequeryFixture("2098-01", 105, 11);
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode adminPage = data(get(admin, "/api/admin/reports/inventory-capital?periodCode="
				+ fixture.periodCode() + "&analysisMode=BUSINESS_SNAPSHOT&keyword="
				+ encoded(fixture.targetKeyword()) + "&page=2&pageSize=7"));

		assertThat(adminPage.get("page").asInt()).as("BUSINESS_SNAPSHOT 读取时必须使用当前请求页码")
			.isEqualTo(2);
		assertThat(adminPage.get("pageSize").asInt()).as("BUSINESS_SNAPSHOT 读取时必须使用当前请求 pageSize")
			.isEqualTo(7);
		assertThat(adminPage.get("total").asInt()).as("BUSINESS_SNAPSHOT total 必须基于当前筛选全集")
			.isEqualTo(11);
		assertThat(adminPage.get("items")).as("BUSINESS_SNAPSHOT 当前页不得返回超过 pageSize 的旧 payload")
			.hasSize(4);
		assertEveryItemContains(adminPage, fixture.targetKeyword());
		assertThat(adminPage.toString()).as("快照响应不得保留生成 payload 的 LIVE/CURRENT 口径")
			.contains("\"analysisMode\":\"BUSINESS_SNAPSHOT\"")
			.doesNotContain("\"analysisMode\":\"LIVE\"", "\"freshnessStatus\":\"CURRENT\"");

		AuthenticatedSession lowPrivilege = createUserAndLogin("033-snapshot-low-", "033_SNAPSHOT_LOW_",
				List.of("report", "report:inventory-capital:view"));
		JsonNode lowPrivilegePage = data(get(lowPrivilege, "/api/admin/reports/inventory-capital?periodCode="
				+ fixture.periodCode() + "&analysisMode=BUSINESS_SNAPSHOT&keyword="
				+ encoded(fixture.targetKeyword()) + "&page=1&pageSize=7"));
		assertThat(lowPrivilegePage.toString()).as("低权限读取快照时不得泄露冻结事实金额和来源主键")
			.doesNotContain("9000.", "SNAPSHOT_SOURCE_SECRET");
	}

	@Test
	@Order(10)
	void a12a13整改旧期间缺033快照必须返回legacyNotIncluded且不得回退live() throws Exception {
		insertClosedBusinessSnapshotWithout033Report("2098-02", "033-LEGACY-SNAPSHOT-" + SEQUENCE.incrementAndGet());
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		JsonNode report = data(get(admin,
				"/api/admin/reports/inventory-capital?periodCode=2098-02&analysisMode=BUSINESS_SNAPSHOT&page=1&pageSize=10"));

		assertThat(report.toString()).as("旧期间缺 033 报表快照必须稳定表达 LEGACY_NOT_INCLUDED")
			.contains("LEGACY_NOT_INCLUDED")
			.doesNotContain("\"analysisMode\":\"LIVE\"", "\"freshnessStatus\":\"CURRENT\"");
	}

	@Test
	@Order(11)
	void a15整改金额权限必须按报表类别分别失败关闭而不是共用成本和总账权限() throws Exception {
		OperatingFinanceFixture profitFixture = seedProjectProfitFixture(1);
		Stage033PositiveFixture operatingFixture = seedStage033PositiveFixture();
		AuthenticatedSession projectCostViewer = createUserAndLogin("033-project-cost-viewer-",
				"033_PROJECT_COST_VIEWER_", List.of("report", "report:project-profit:view",
						"cost:project-cost:amount-view"));
		AuthenticatedSession inventoryValuationViewer = createUserAndLogin("033-inventory-value-viewer-",
				"033_INVENTORY_VALUE_VIEWER_", List.of("report", "report:inventory-capital:view",
						"inventory:valuation:view"));
		AuthenticatedSession settlementViewer = createUserAndLogin("033-settlement-viewer-",
				"033_SETTLEMENT_VIEWER_", List.of("report", "report:receivable-payable:view",
						"finance:receivable:view", "finance:receipt:view", "finance:payable:view",
						"finance:payment:view", "finance:advance-receipt:view", "finance:prepayment:view",
						"finance:settlement-allocation:view"));

		JsonNode projectProfit = data(get(projectCostViewer,
				"/api/admin/reports/project-profit?periodCode=2097-01&analysisMode=LIVE&projectId="
						+ profitFixture.firstProjectId() + "&page=1&pageSize=10"));
		assertThat(projectProfit.toString()).as("项目经营成本金额不应额外依赖总账金额权限")
			.contains("1000.00", "650.00", "350.00")
			.doesNotContain("RESTRICTED", "缺少上游金额权限");

		JsonNode inventory = data(get(inventoryValuationViewer,
				"/api/admin/reports/inventory-capital?periodCode=2026-06&analysisMode=LIVE&keyword="
						+ encoded(operatingFixture.inventory().marker()) + "&page=1&pageSize=20"));
		assertThat(inventory.toString()).as("库存资金金额应由库存估值金额权限控制")
			.contains("325.00", "250.00", "75.00")
			.doesNotContain("RESTRICTED", "缺少上游金额权限");

		JsonNode receivablePayable = data(get(settlementViewer,
				"/api/admin/reports/receivable-payable?periodCode=2026-06&analysisMode=LIVE&keyword="
						+ encoded(operatingFixture.receivablePayable().marker()) + "&page=1&pageSize=20"));
		assertThat(receivablePayable.toString()).as("往来金额应由结算来源权限控制")
			.contains("1400.00", "1200.00", "1300.00")
			.doesNotContain("RESTRICTED", "缺少上游金额权限");
	}

	@Test
	@Order(12)
	void a10a11a14整改状态筛选固定摘要和三类关键追溯必须读取真实来源() throws Exception {
		OperatingFinanceFixture fixture = seedProjectProfitFixture(1);
		moveProjectCostCutoff(fixture.firstProjectId(), LocalDate.of(2097, 2, 28));
		seedPostedAccountingEntries(fixture.firstProjectId(), fixture.firstProjectNo());
		seedFinancialSummaryBasisFacts("2097-02");
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SoftAssertions softly = new SoftAssertions();

		JsonNode projectProfit = data(get(admin, "/api/admin/reports/project-profit?periodCode=2097-02"
				+ "&analysisMode=LIVE&projectId=" + fixture.firstProjectId() + "&page=1&pageSize=10"));
		assertTraceFromItemContaining(softly, admin, projectProfit,
				"/api/admin/reports/project-profit/" + fixture.firstProjectId() + "/traces",
				"项目利润", fixture.firstProjectNo(),
				List.of(new TraceExpectation("PROJECT_COST_CALCULATION",
						projectCostCalculationId(fixture.firstProjectId()))),
				"2097-02");

		JsonNode reconciliation = data(get(admin,
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2097-02"
						+ "&analysisMode=LIVE&projectId=" + fixture.firstProjectId() + "&page=1&pageSize=20"));
		JsonNode reconciliationTrace = traceFromFirstItemContaining(softly, admin, reconciliation,
				"/api/admin/reports/operating-accounting-reconciliation/traces",
				"经营会计对照", fixture.firstProjectNo(), "2097-02");
		assertTraceContainsAnySourceId(softly, reconciliationTrace, "GL_LEDGER_ENTRY",
				ledgerEntryIdsForPeriod("2097-02"), "经营会计对照");

		JsonNode matchedFiltered = data(get(admin,
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2097-02"
						+ "&analysisMode=LIVE&projectId=" + fixture.firstProjectId()
						+ "&reconciliationStatus=MATCHED&page=1&pageSize=20"));
		softly.assertThat(matchedFiltered.get("items")).as("reconciliationStatus 筛选必须实际生效")
			.isEmpty();

		JsonNode financialSummary = data(get(admin,
				"/api/admin/reports/financial-summary?periodCode=2097-02&analysisMode=LIVE"));
		softly.assertThat(hasNonNullField(financialSummary, "assetBalanceAmount")).as("固定摘要必须返回资产类别余额")
			.isTrue();
		softly.assertThat(hasNonNullField(financialSummary, "liabilityBalanceAmount")).as("固定摘要必须返回负债类别余额")
			.isTrue();
		softly.assertThat(hasNonNullField(financialSummary, "equityBalanceAmount")).as("固定摘要必须返回权益类别余额")
			.isTrue();
		softly.assertThat(financialSummary.get("trialBalanceStatus").asText()).as("固定摘要必须读取试算状态")
			.isEqualTo("MATCHED");
		softly.assertThat(financialSummary.get("bankReconciliationStatus").asText()).as("固定摘要必须读取银行对账状态")
			.isEqualTo("CONFIRMED");
		softly.assertThat(financialSummary.get("taxSummaryStatus").asText()).as("固定摘要必须读取税务基础状态")
			.isEqualTo("CONFIRMED");
		JsonNode financialTrace = traceDataAtPeriod(softly, admin, "/api/admin/reports/financial-summary/traces",
				financialSummary.get("traceKey").asText(), "固定财务摘要", "2097-02");
		assertTraceContainsAnySourceId(softly, financialTrace, "GL_LEDGER_ENTRY",
				ledgerEntryIdsForPeriod("2097-02"), "固定财务摘要");

		softly.assertAll();
	}

	@Test
	@Order(13)
	void a10a14差异跨期间对照和live状态筛选必须基于真实期间与完整性() throws Exception {
		OperatingFinanceFixture filterFixture = seedProjectProfitFixture(2);
		moveProjectCostCutoff(filterFixture.firstProjectId(), LocalDate.of(2097, 2, 28));
		moveProjectCostCutoff(filterFixture.lastProjectId(), LocalDate.of(2097, 2, 28));
		seedPostedAccountingEntries(filterFixture.firstProjectId(), filterFixture.firstProjectNo());
		OperatingFinanceFixture crossPeriodFixture = seedProjectProfitFixture(1);
		moveProjectCostCutoff(crossPeriodFixture.firstProjectId(), LocalDate.of(2097, 3, 31));
		seedPostedAccountingEntries(crossPeriodFixture.firstProjectId(), crossPeriodFixture.firstProjectNo());
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SoftAssertions softly = new SoftAssertions();

		JsonNode completeProfit = data(get(admin,
				"/api/admin/reports/project-profit?periodCode=2097-02&analysisMode=LIVE&completenessStatus=COMPLETE"
						+ "&page=1&pageSize=20"));
		softly.assertThat(completeProfit.toString()).as("项目利润 LIVE 必须应用 COMPLETE 筛选")
			.contains(filterFixture.firstProjectNo())
			.doesNotContain(filterFixture.lastProjectNo());

		JsonNode differentProfit = data(get(admin,
				"/api/admin/reports/project-profit?periodCode=2097-02&analysisMode=LIVE&reconciliationStatus=DIFFERENT"
						+ "&page=1&pageSize=20"));
		softly.assertThat(differentProfit.toString()).as("项目利润 LIVE 必须应用 reconciliationStatus 筛选")
			.contains(filterFixture.firstProjectNo())
			.doesNotContain(filterFixture.lastProjectNo());

		JsonNode completeReconciliation = data(get(admin,
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2097-02"
						+ "&analysisMode=LIVE&completenessStatus=COMPLETE&page=1&pageSize=20"));
		softly.assertThat(completeReconciliation.toString()).as("经营会计对照必须按真实完整性筛选")
			.contains(filterFixture.firstProjectNo())
			.doesNotContain(filterFixture.lastProjectNo());

		JsonNode crossPeriod = data(get(admin,
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2097-02"
						+ "&dateFrom=2097-02-01&dateTo=2097-02-28&analysisMode=LIVE&projectId="
						+ crossPeriodFixture.firstProjectId() + "&page=1&pageSize=20"));
		softly.assertThat(crossPeriod.get("items")).as("经营会计对照不得把 2097-03 成本混入 2097-02")
			.isEmpty();
		softly.assertThat(crossPeriod.toString()).as("跨期间项目不得出现在 2097-02 对照响应")
			.doesNotContain(crossPeriodFixture.firstProjectNo(), "650.00", "350.00");

		softly.assertAll();
	}

	@Test
	@Order(14)
	void a05a10a11差异会计口径和固定摘要必须汇总阶段科目下级科目() throws Exception {
		OperatingFinanceFixture fixture = seedProjectProfitFixture(1);
		moveProjectCostCutoff(fixture.firstProjectId(), LocalDate.of(2097, 4, 30));
		String revenueChildCode = ensureChildAccount("6001", "6001.033" + SEQUENCE.incrementAndGet(),
				"033 主营收入下级科目");
		String costChildCode = ensureChildAccount("6401", "6401.033" + SEQUENCE.incrementAndGet(),
				"033 主营成本下级科目");
		seedPostedAccountingEntriesWithChildAccounts("2097-04", fixture.firstProjectId(), fixture.firstProjectNo(),
				revenueChildCode, costChildCode);
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SoftAssertions softly = new SoftAssertions();

		JsonNode projectProfit = data(get(admin, "/api/admin/reports/project-profit?periodCode=2097-04"
				+ "&analysisMode=LIVE&projectId=" + fixture.firstProjectId() + "&page=1&pageSize=20"));
		softly.assertThat(projectProfit.toString()).as("项目利润会计口径必须包含收入和成本下级科目")
			.contains("880.00", "330.00", "550.00");

		JsonNode reconciliation = data(get(admin,
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2097-04"
						+ "&analysisMode=LIVE&projectId=" + fixture.firstProjectId() + "&page=1&pageSize=20"));
		softly.assertThat(reconciliation.toString()).as("经营会计对照必须包含下级科目发生额")
			.contains("880.00", "330.00", "550.00");

		JsonNode financialSummary = data(get(admin,
				"/api/admin/reports/financial-summary?periodCode=2097-04&analysisMode=LIVE"));
		softly.assertThat(financialSummary.toString()).as("固定摘要必须把阶段约定科目的下级科目纳入分类汇总")
			.contains("\"revenueAmount\":\"880.00\"", "\"mainCostAmount\":\"330.00\"");

		softly.assertAll();
	}

	@Test
	@Order(15)
	void a14差异新鲜度必须识别项目成本来源变化业务重开和财务反结账() throws Exception {
		OperatingFinanceFixture fixture = seedProjectProfitFixture(1);
		moveProjectCostCutoff(fixture.firstProjectId(), LocalDate.of(2097, 5, 31));
		insertProjectCostSourceLine(fixture.firstProjectId(), "033-FRESH-SOURCE-" + SEQUENCE.incrementAndGet(),
				LocalDate.of(2097, 5, 20));
		insertClosedBusinessSnapshot("2097-05", "033-FRESHNESS-CLOSED-" + SEQUENCE.incrementAndGet());
		updateProjectCostFingerprint(fixture.firstProjectId(), "033-FRESHNESS-CHANGED-" + SEQUENCE.incrementAndGet());
		reopenBusinessPeriod("2097-05", "033 业务月结重开后新鲜度验收");
		seedClosedFinancialStatus(ensureAccountingPeriod("2097-05", LocalDate.of(2097, 5, 1),
				LocalDate.of(2097, 5, 31)), "2097-05");
		reopenFinancialClose("2097-05", "033 财务反结账后新鲜度验收");
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		JsonNode projectProfit = data(get(admin, "/api/admin/reports/project-profit?periodCode=2097-05"
				+ "&analysisMode=LIVE&projectId=" + fixture.firstProjectId() + "&page=1&pageSize=20"));
		assertThat(projectProfit.toString()).as("来源变化、030 重开和 032 反结账后不得仍声明 CURRENT")
			.contains(fixture.firstProjectNo())
			.doesNotContain("\"freshnessStatus\":\"CURRENT\"");
	}

	@Test
	@Order(16)
	void a08差异库存资金必须显式区分已知估值金额和未知估值风险() throws Exception {
		Stage033PositiveFixture fixture = seedStage033PositiveFixture();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		JsonNode inventory = data(get(admin,
				"/api/admin/reports/inventory-capital?periodCode=2026-06&analysisMode=LIVE&keyword="
						+ encoded(fixture.inventory().marker()) + "&page=1&pageSize=20"));
		JsonNode summary = inventory.get("summary");
		assertThat(summary.get("knownValuationAmount")).as("库存资金 summary 必须显式返回已知估值金额")
			.isNotNull();
		assertThat(summary.get("knownValuationAmount").asText()).isEqualTo("325.00");
		assertThat(summary.get("unknownValuationQuantity")).as("库存资金 summary 必须显式返回未知估值数量")
			.isNotNull();
		assertThat(summary.get("unknownValuationQuantity").asText()).isEqualTo("3.000000");
		assertThat(summary.get("completenessStatus")).as("库存资金 summary 必须表达估值完整性")
			.isNotNull();
		assertThat(summary.get("completenessStatus").asText()).isEqualTo("INCOMPLETE");
		JsonNode unvalued = firstItemContaining(inventory, "LEGACY_UNVALUED");
		assertThat(unvalued).as("库存资金夹具必须包含未知估值行").isNotNull();
		assertThat(unvalued.get("amount")).as("未知估值行金额必须保持 null，不能按 0 汇总").isNotNull();
		assertThat(unvalued.get("amount").isNull()).isTrue();
	}

	@Test
	@Order(17)
	void a15差异最小和缺一来源权限必须关闭列表与追溯反推() throws Exception {
		OperatingFinanceFixture profitFixture = seedProjectProfitFixture(1);
		insertProjectCostSourceLine(profitFixture.firstProjectId(),
				"033-PROFIT-SOURCE-SECRET-" + SEQUENCE.incrementAndGet(), LocalDate.of(2097, 1, 20));
		Stage033PositiveFixture operatingFixture = seedStage033PositiveFixture();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SoftAssertions softly = new SoftAssertions();

		String projectProfitPath = "/api/admin/reports/project-profit?periodCode=2097-01&analysisMode=LIVE&projectId="
				+ profitFixture.firstProjectId() + "&page=1&pageSize=20";
		JsonNode projectProfit = data(get(admin, projectProfitPath));
		assertMinimalSourceVisible(softly, "项目利润", projectProfitPath,
				List.of("report", "report:project-profit:view", "cost:project-cost:amount-view",
						"cost:project-cost:view", "gl:amount:view", "gl:ledger:view"),
				List.of(profitFixture.firstProjectNo(), "650.00"));
		assertSourceRestrictedListAndTrace(softly, "项目利润",
				createUserAndLogin("033-profit-source-missing-", "033_PROFIT_SOURCE_MISSING_",
						List.of("report", "report:project-profit:view", "cost:project-cost:amount-view",
								"gl:amount:view", "gl:ledger:view")),
				projectProfit, projectProfitPath, "/api/admin/reports/project-profit/"
						+ profitFixture.firstProjectId() + "/traces",
				"2097-01", profitFixture.firstProjectNo(), List.of("033-PROFIT-SOURCE-SECRET-"));

		String contractPath = "/api/admin/reports/contract-collections?periodCode=2026-06&analysisMode=LIVE&keyword="
				+ encoded(operatingFixture.contract().marker()) + "&page=1&pageSize=20";
		JsonNode contract = data(get(admin, contractPath));
		List<String> contractCollectionFullPermissions = List.of("report", "report:contract-collection:view",
				"sales:contract:view", "finance:sales-invoice:view", "finance:receivable:view",
				"finance:receipt:view", "finance:settlement-allocation:view");
		assertMinimalSourceVisible(softly, "合同回款", contractPath,
				contractCollectionFullPermissions,
				List.of(operatingFixture.contract().salesInvoiceNo(), operatingFixture.contract().receiptNo(),
						operatingFixture.contract().receivableNo()));
		assertSourceRestrictedListAndTrace(softly, "合同回款",
				createUserAndLogin("033-contract-source-missing-", "033_CONTRACT_SOURCE_MISSING_",
						List.of("report", "report:contract-collection:view", "sales:contract:view",
								"finance:sales-invoice:view", "finance:receivable:view",
								"finance:settlement-allocation:view")),
				contract, contractPath, "/api/admin/reports/contract-collections/traces",
				"2026-06", operatingFixture.inventory().projectNo(),
				List.of(operatingFixture.contract().receiptNo()));

		ContractCollectionSnapshotProbeFixture contractSnapshot =
				seedContractCollectionSnapshotFixture(operatingFixture, "2098-05");
		String contractSnapshotPath = "/api/admin/reports/contract-collections?periodCode="
				+ contractSnapshot.periodCode() + "&analysisMode=BUSINESS_SNAPSHOT&projectId="
				+ contractSnapshot.projectId() + "&page=1&pageSize=20";
		AuthenticatedSession contractSnapshotFullSource = createUserAndLogin("033-contract-snapshot-source-full-",
				"033_CONTRACT_SNAPSHOT_SOURCE_FULL_", contractCollectionFullPermissions);
		JsonNode fullContractSnapshot = data(get(contractSnapshotFullSource, contractSnapshotPath));
		JsonNode fullContractSnapshotItem = firstItemContaining(fullContractSnapshot, contractSnapshot.contractNo());
		if (fullContractSnapshotItem == null) {
			softly.fail("合同回款业务快照完整来源权限必须能按合同号定位冻结行：" + fullContractSnapshot);
		}
		else {
			softly.assertThat(textOrNull(fullContractSnapshotItem, "contractNo"))
				.as("合同回款业务快照完整来源权限必须显示 contractNo")
				.isEqualTo(contractSnapshot.contractNo());
			softly.assertThat(intField(fullContractSnapshotItem, "sourceCount"))
				.as("合同回款业务快照完整来源权限必须显示来源数量")
				.isGreaterThan(0);
			String fullSnapshotTraceKey = textOrNull(fullContractSnapshotItem, "traceKey");
			softly.assertThat(fullSnapshotTraceKey)
				.as("合同回款业务快照完整来源权限必须返回可调用 traceKey")
				.isNotBlank();
			softly.assertThat(fullContractSnapshotItem.toString())
				.as("合同回款业务快照完整来源权限必须显示真实来源单号")
				.contains(contractSnapshot.invoiceNo(), contractSnapshot.receiptNo(), contractSnapshot.receivableNo());
			if (fullSnapshotTraceKey != null && !fullSnapshotTraceKey.isBlank()) {
				JsonNode fullSnapshotTrace = data(get(contractSnapshotFullSource,
						"/api/admin/reports/contract-collections/traces?periodCode=" + contractSnapshot.periodCode()
								+ "&analysisMode=BUSINESS_SNAPSHOT&traceKey="
								+ encoded(fullSnapshotTraceKey) + "&page=1&pageSize=20"));
				softly.assertThat(fullSnapshotTrace.get("total").asInt())
					.as("合同回款业务快照完整来源权限 trace 必须可用")
					.isGreaterThan(0);
			}
		}
		AuthenticatedSession contractSnapshotMissingAllocation = createUserAndLogin(
				"033-contract-snapshot-source-missing-", "033_CONTRACT_SNAPSHOT_SOURCE_MISSING_",
				List.of("report", "report:contract-collection:view", "sales:contract:view",
						"finance:sales-invoice:view", "finance:receivable:view", "finance:receipt:view"));
		JsonNode restrictedContractSnapshot = data(get(contractSnapshotMissingAllocation, contractSnapshotPath));
		JsonNode restrictedContractSnapshotItem =
				firstItemContaining(restrictedContractSnapshot, contractSnapshot.customerName());
		if (restrictedContractSnapshotItem == null) {
			softly.fail("合同回款业务快照缺核销来源权限仍应返回金额可见的稳定受限行：" + restrictedContractSnapshot);
		}
		else {
			softly.assertThat(restrictedContractSnapshot.get("summary").get("sourceCount").asInt())
				.as("合同回款业务快照缺核销来源权限 summary sourceCount 不得泄露")
				.isZero();
			softly.assertThat(textOrNull(restrictedContractSnapshotItem, "contractAmount"))
				.as("合同回款业务快照缺核销来源权限金额仍应按金额权限可见")
				.isEqualTo("2000.00");
			softly.assertThat(textOrNull(restrictedContractSnapshotItem, "invoiceAmount"))
				.as("合同回款业务快照缺核销来源权限发票金额仍应可见")
				.isEqualTo("900.00");
			softly.assertThat(textOrNull(restrictedContractSnapshotItem, "receivedAmount"))
				.as("合同回款业务快照缺核销来源权限收款金额仍应可见")
				.isEqualTo("300.00");
			softly.assertThat(textOrNull(restrictedContractSnapshotItem, "contractNo"))
				.as("合同回款业务快照缺核销来源权限不得泄露 contractNo")
				.isNullOrEmpty();
			softly.assertThat(intField(restrictedContractSnapshotItem, "sourceCount"))
				.as("合同回款业务快照缺核销来源权限列表 sourceCount 不得泄露")
				.isZero();
			softly.assertThat(textOrNull(restrictedContractSnapshotItem, "traceKey"))
				.as("合同回款业务快照缺核销来源权限列表不得返回 traceKey")
				.isNullOrEmpty();
			softly.assertThat(restrictedContractSnapshotItem.toString())
				.as("合同回款业务快照缺核销来源权限不得泄露来源单号")
				.doesNotContain(contractSnapshot.invoiceNo(), contractSnapshot.receiptNo(),
						contractSnapshot.receivableNo());
			if (fullContractSnapshotItem != null) {
				assertRestrictedTraceEmptyPage(softly, "合同回款业务快照", contractSnapshotMissingAllocation,
						"/api/admin/reports/contract-collections/traces",
						textOrNull(fullContractSnapshotItem, "traceKey"), contractSnapshot.periodCode(),
						"BUSINESS_SNAPSHOT");
			}
		}

		String procurementPath = "/api/admin/reports/procurement-variances?periodCode=2026-06&analysisMode=LIVE&keyword="
				+ encoded(operatingFixture.procurement().marker()) + "&page=1&pageSize=20";
		JsonNode procurement = data(get(admin, procurementPath));
		assertMinimalSourceVisible(softly, "采购差异", procurementPath,
				List.of("report", "report:procurement-variance:view", "procurement:order:view",
						"procurement:receipt:view", "procurement:return:view",
						"finance:purchase-invoice:view", "finance:payable:view", "finance:payment:view",
						"production:outsourcing:view", "production:outsourcing-receipt:view"),
				List.of(operatingFixture.procurement().purchaseInvoiceNo(), operatingFixture.procurement().paymentNo()));
		assertSourceRestrictedListAndTrace(softly, "采购差异",
				createUserAndLogin("033-procurement-source-missing-", "033_PROCUREMENT_SOURCE_MISSING_",
						List.of("report", "report:procurement-variance:view", "procurement:order:view",
								"procurement:receipt:view", "finance:purchase-invoice:view",
								"finance:payable:view", "finance:payment:view")),
				procurement, procurementPath, "/api/admin/reports/procurement-variances/traces",
				"2026-06", operatingFixture.inventory().projectNo(),
				List.of(operatingFixture.procurement().purchaseInvoiceNo()));

		String inventoryPath = "/api/admin/reports/inventory-capital?periodCode=2026-06&analysisMode=LIVE&keyword="
				+ encoded(operatingFixture.inventory().marker()) + "&page=1&pageSize=20";
		JsonNode inventory = data(get(admin, inventoryPath));
		assertMinimalSourceVisible(softly, "库存资金", inventoryPath,
				List.of("report", "report:inventory-capital:view", "inventory:valuation:view",
						"inventory:balance:view", "inventory:movement:view"),
				List.of(operatingFixture.inventory().projectNo(), "325.00"));
		assertSourceRestrictedListAndTrace(softly, "库存资金",
				createUserAndLogin("033-inventory-source-missing-", "033_INVENTORY_SOURCE_MISSING_",
						List.of("report", "report:inventory-capital:view", "inventory:valuation:view",
								"inventory:balance:view")),
				inventory, inventoryPath, "/api/admin/reports/inventory-capital/traces",
				"2026-06", operatingFixture.inventory().projectNo(), List.of());

		String receivablePayablePath = "/api/admin/reports/receivable-payable?periodCode=2026-06&analysisMode=LIVE&keyword="
				+ encoded(operatingFixture.receivablePayable().marker()) + "&page=1&pageSize=20";
		JsonNode receivablePayable = data(get(admin, receivablePayablePath));
		assertMinimalSourceVisible(softly, "往来资金", receivablePayablePath,
				List.of("report", "report:receivable-payable:view", "finance:receivable:view",
						"finance:receipt:view", "finance:payable:view", "finance:payment:view",
						"finance:settlement-allocation:view", "procurement:receipt:view"),
				List.of(operatingFixture.receivablePayable().days31To60ReceivableNo(),
						operatingFixture.receivablePayable().payableNo()));
		assertSourceRestrictedListAndTrace(softly, "往来资金",
				createUserAndLogin("033-settlement-source-missing-", "033_SETTLEMENT_SOURCE_MISSING_",
						List.of("report", "report:receivable-payable:view", "finance:receivable:view",
								"finance:receipt:view", "finance:payable:view", "finance:payment:view")),
				receivablePayable, receivablePayablePath, "/api/admin/reports/receivable-payable/traces",
				"2026-06", operatingFixture.inventory().projectNo(),
				List.of(operatingFixture.receivablePayable().days31To60ReceivableNo()));

		softly.assertAll();
	}

	@Test
	@Order(18)
	void a18_projectCostSourceLineTraceStatusNameContract() throws Exception {
		OperatingFinanceFixture fixture = seedProjectProfitFixture(1);
		Map<String, String> expectedStatusNames = new LinkedHashMap<>();
		expectedStatusNames.put("ACTUAL", "实际");
		expectedStatusNames.put("PROVISIONAL", "暂估");
		expectedStatusNames.put("UNPRICED", "未定价");
		expectedStatusNames.put("ADJUSTED", "已调整");
		expectedStatusNames.put("RESTRICTED", "来源受限");
		expectedStatusNames.put("EXCLUDED", "已排除");
		Map<String, String> sourceNos = new LinkedHashMap<>();
		int dayOffset = 0;
		for (Map.Entry<String, String> entry : expectedStatusNames.entrySet()) {
			String sourceNo = "033-PROFIT-SOURCE-STATUS-" + entry.getKey() + "-"
					+ SEQUENCE.incrementAndGet();
			sourceNos.put(entry.getKey(), sourceNo);
			insertProjectCostSourceLine(fixture.firstProjectId(), sourceNo,
					LocalDate.of(2097, 1, 21).plusDays(dayOffset), entry.getKey());
			dayOffset++;
		}
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SoftAssertions softly = new SoftAssertions();
		String reportPath = "/api/admin/reports/project-profit?periodCode=2097-01&analysisMode=LIVE&projectId="
				+ fixture.firstProjectId() + "&page=1&pageSize=20";
		JsonNode projectProfit = data(get(admin, reportPath));
		String tracePath = "/api/admin/reports/project-profit/" + fixture.firstProjectId() + "/traces";
		JsonNode trace = traceFromFirstItemContaining(softly, admin, projectProfit, tracePath,
				"项目利润来源状态显示", fixture.firstProjectNo(), "2097-01");

		if (trace != null) {
			for (Map.Entry<String, String> entry : expectedStatusNames.entrySet()) {
				String sourceNo = sourceNos.get(entry.getKey());
				JsonNode source = firstTraceSourceBySourceNo(trace, "PROJECT_COST_SOURCE_LINE", sourceNo);
				if (source == null) {
					softly.fail("项目成本来源行 trace 缺少状态样本：" + sourceNo + "，trace：" + trace);
				}
				else {
					softly.assertThat(textOrNull(source, "status"))
						.as("项目成本来源行必须保留原始技术状态码：" + sourceNo)
						.isEqualTo(entry.getKey());
					softly.assertThat(textOrNull(source, "statusName"))
						.as("项目成本来源行必须返回后端确定的中文状态名：" + entry.getKey())
						.isEqualTo(entry.getValue());
					softly.assertThat(textOrNull(source, "sourceType")).as("旧字段 sourceType 不得变化")
						.isEqualTo("PROJECT_COST_SOURCE_LINE");
					softly.assertThat(textOrNull(source, "sourceNo")).as("旧字段 sourceNo 不得变化")
						.isEqualTo(sourceNo);
					softly.assertThat(textOrNull(source, "resourceRouteName"))
						.as("旧字段 resourceRouteName 不得变化")
						.isEqualTo("project-cost-source-line");
					softly.assertThat(hasNonNullField(source, "sourceId")).as("旧字段 sourceId 必须仍返回")
						.isTrue();
					softly.assertThat(hasNonNullField(source, "businessDate")).as("旧字段 businessDate 必须仍返回")
						.isTrue();
					softly.assertThat(hasNonNullField(source, "quantity")).as("旧字段 quantity 必须仍返回")
						.isTrue();
					softly.assertThat(hasNonNullField(source, "amount")).as("旧字段 amount 必须仍返回")
						.isTrue();
					softly.assertThat(booleanField(source, "canViewResource")).as("管理员来源权限不得被削弱")
						.isTrue();
					softly.assertThat(booleanField(source, "restricted")).as("管理员来源不得被误标记受限")
						.isFalse();
					softly.assertThat(textOrNull(source, "restrictedMessage")).as("管理员来源不得返回受限说明")
						.isNull();
				}
			}
		}
		assertSourceRestrictedListAndTrace(softly, "项目利润来源状态显示",
				createUserAndLogin("033-profit-status-source-missing-", "033_PROFIT_STATUS_SOURCE_MISSING_",
						List.of("report", "report:project-profit:view", "cost:project-cost:amount-view",
								"gl:amount:view", "gl:ledger:view")),
				projectProfit, reportPath, tracePath, "2097-01", fixture.firstProjectNo(),
				List.copyOf(sourceNos.values()));
		softly.assertAll();
	}

	@Test
	@Order(19)
	void a12差异项目利润业务快照详情必须保持冻结口径() throws Exception {
		SnapshotProjectDetailFixture fixture = seedProjectProfitSnapshotDetailFixture("2098-03");
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode snapshotList = data(get(admin,
				"/api/admin/reports/project-profit?periodCode=2098-03&analysisMode=BUSINESS_SNAPSHOT&projectId="
						+ fixture.projectId() + "&page=1&pageSize=20"));
		assertThat(snapshotList.toString()).contains(fixture.projectNo(), "BUSINESS_SNAPSHOT", "FROZEN",
				"1234.00", "678.00", "556.00");

		JsonNode snapshotDetail = data(get(admin, "/api/admin/reports/project-profit/" + fixture.projectId()
				+ "?periodCode=2098-03&analysisMode=BUSINESS_SNAPSHOT"));
		assertThat(snapshotDetail.toString()).as("项目利润快照详情必须读取 030 冻结 payload 而不是误报历史未包含或回退 LIVE")
			.contains(fixture.projectNo(), "FROZEN", "1234.00", "678.00", "556.00")
			.doesNotContain("\"analysisMode\":\"LIVE\"", "\"freshnessStatus\":\"CURRENT\"");
	}

	@Test
	@Order(20)
	void a11差异固定摘要空来源合法trace必须返回空页() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode financialSummary = data(get(admin,
				"/api/admin/reports/financial-summary?periodCode=2098-04&analysisMode=LIVE"));
		String traceKey = textOrNull(financialSummary, "traceKey");
		assertThat(traceKey).as("固定摘要空来源也必须返回合法 traceKey").isNotBlank();

		JsonNode trace = data(get(admin, "/api/admin/reports/financial-summary/traces?traceKey="
				+ encoded(traceKey) + "&periodCode=2098-04&analysisMode=LIVE&page=1&pageSize=20"));
		assertThat(trace.get("items")).as("固定摘要空来源 trace 必须继承 014 空分页契约").isEmpty();
		assertThat(trace.get("total").asInt()).isZero();
		assertThat(trace.get("totalPages").asInt()).isZero();
	}

	private static Map<String, String> acceptanceMapping() {
		Map<String, String> mapping = new LinkedHashMap<>();
		mapping.put("A01", "014 旧经营报表接口、页面契约和权限回归。");
		mapping.put("A02", "033 固定页面和 API 仍在 /reports 与 /api/admin/reports。");
		mapping.put("A03", "V35 双路径迁移由 OperatingFinancialAnalysisV35MigrationRegressionTests 锁定。");
		mapping.put("A04", "项目利润列表、详情、三种经营收入、成本和完整性。");
		mapping.put("A05", "会计项目利润只取 POSTED 和 PROJECT 辅助，公共金额单列。");
		mapping.put("A06", "合同回款固定报表继承 summary + items 与追溯契约。");
		mapping.put("A07", "采购差异固定报表继承 summary + items 与不完整来源状态。");
		mapping.put("A08", "库存资金固定报表继承实时/快照模式和未估值风险。");
		mapping.put("A09", "往来账龄固定报表继承余额、核销、逾期和账龄桶。");
		mapping.put("A10", "经营/会计对照锁定差异、公共金额和不可对账状态。");
		mapping.put("A11", "固定经营财务摘要锁定非三大报表表达。");
		mapping.put("A12", "LIVE/BUSINESS_SNAPSHOT 与后两类不进 030 快照边界。");
		mapping.put("A13", "历史快照 LEGACY_NOT_INCLUDED 由快照模式测试与验证器规则覆盖。");
		mapping.put("A14", "来源变化、反结账和不完整状态必须显式返回。");
		mapping.put("A15", "报表、金额、来源、总账、库存、银行和税务组合权限失败关闭。");
		mapping.put("A16", "十进制字符串、空分母、负数和后端舍入。");
		mapping.put("A17", "分页完整汇总、候选直达和追溯 returnTo。");
		mapping.put("A18", "真实桌面页面规范由集中审查前浏览器验收执行，本资产锁接口状态。");
		mapping.put("A19", "正式零事实合法，不补造正式库数据。");
		mapping.put("A20", "对象一致性不少于八且不写死十八。");
		mapping.put("A21", "唯一全量窗口与主远端同步为阶段交付门禁，本资产提供前置缺陷发现。");
		return mapping;
	}

	private OperatingFinanceFixture seedProjectProfitFixture(int projectCount) {
		long customerId = insertCustomer("033-CUST-" + SEQUENCE.incrementAndGet());
		long ownerUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		Long firstProjectId = null;
		String firstProjectNo = null;
		Long lastProjectId = null;
		String lastProjectNo = null;
		for (int index = 1; index <= projectCount; index++) {
			String projectNo = "033-PROFIT-" + SEQUENCE.incrementAndGet();
			long projectId = insertProject(projectNo, customerId, ownerUserId, index);
			insertProjectCostCalculation(projectId, projectNo, index);
			if (firstProjectId == null) {
				firstProjectId = projectId;
				firstProjectNo = projectNo;
			}
			lastProjectId = projectId;
			lastProjectNo = projectNo;
		}
		return new OperatingFinanceFixture(firstProjectId, firstProjectNo, lastProjectId, lastProjectNo);
	}

	private void moveProjectCostCutoff(long projectId, LocalDate cutoffDate) {
		this.jdbcTemplate.update("""
				update prj_cost_calculation
				set cutoff_date = ?,
				    updated_at = now()
				where project_id = ?
				""", cutoffDate, projectId);
	}

	private long projectCostCalculationId(long projectId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from prj_cost_calculation
				where project_id = ?
				order by id desc
				limit 1
				""", Long.class, projectId);
	}

	private SnapshotProbeFixture seedSnapshotRequeryFixture(String periodCode, int totalItems, int targetItems)
			throws Exception {
		String marker = "033-SNAPSHOT-REQUERY-" + SEQUENCE.incrementAndGet();
		String targetKeyword = marker + "-TARGET";
		long snapshotId = insertClosedBusinessSnapshot(periodCode, marker);
		List<Map<String, Object>> items = new ArrayList<>();
		for (int index = 1; index <= totalItems; index++) {
			boolean target = index <= targetItems;
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("snapshotMarker", marker);
			item.put("sourceNo", (target ? targetKeyword : marker + "-OTHER") + "-%03d".formatted(index));
			item.put("amount", "9000.%02d".formatted(index % 100));
			item.put("sourceSecretNo", "SNAPSHOT_SOURCE_SECRET-" + index);
			item.put("analysisMode", "BUSINESS_SNAPSHOT");
			item.put("freshnessStatus", "CURRENT");
			item.put("traceKey", "inventory-capital:BALANCE:" + (900000 + index));
			items.add(item);
		}
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("quantity", "105.000");
		summary.put("amount", "900000.00");
		summary.put("snapshotAmount", "800000.00");
		summary.put("differenceAmount", "100000.00");
		summary.put("riskQuantity", "0.000");
		summary.put("sourceCount", totalItems);
		summary.put("analysisMode", "LIVE");
		summary.put("freshnessStatus", "CURRENT");
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("summary", summary);
		payload.put("items", items);
		payload.put("page", 1);
		payload.put("pageSize", 100);
		payload.put("total", totalItems);
		payload.put("totalPages", 2);
		insertReportSnapshot(snapshotId, "INVENTORY_CAPITAL", payload, totalItems, marker + "-inventory-capital");
		return new SnapshotProbeFixture(periodCode, marker, targetKeyword);
	}

	private ContractCollectionSnapshotProbeFixture seedContractCollectionSnapshotFixture(
			Stage033PositiveFixture fixture, String periodCode) throws Exception {
		String marker = "033-CONTRACT-SNAPSHOT-" + SEQUENCE.incrementAndGet();
		String customerName = "033 合同快照客户 " + marker;
		long snapshotId = insertClosedBusinessSnapshot(periodCode, marker);
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("snapshotMarker", marker);
		item.put("projectId", fixture.inventory().projectId());
		item.put("projectNo", fixture.inventory().projectNo());
		item.put("contractId", fixture.contract().mainContractId());
		item.put("contractNo", fixture.contract().mainContractNo());
		item.put("customerName", customerName);
		item.put("contractAmount", "2000.00");
		item.put("invoiceAmount", "900.00");
		item.put("receivedAmount", "300.00");
		item.put("allocatedAmount", "300.00");
		item.put("unreceivedAmount", "700.00");
		item.put("advanceReceiptAmount", "150.00");
		item.put("overdueAmount", "700.00");
		item.put("collectionRate", "15.00");
		item.put("status", "OVERDUE");
		item.put("sourceCount", 5);
		item.put("traceKey", "contract-collection:CONTRACT:" + fixture.contract().mainContractId());
		item.put("invoiceNos", fixture.contract().salesInvoiceNo());
		item.put("receiptNos", fixture.contract().receiptNo());
		item.put("receivableNos", fixture.contract().receivableNo());
		item.put("analysisMode", "LIVE");
		item.put("freshnessStatus", "CURRENT");
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("contractAmount", "2000.00");
		summary.put("invoiceAmount", "900.00");
		summary.put("receivedAmount", "300.00");
		summary.put("unreceivedAmount", "700.00");
		summary.put("overdueAmount", "700.00");
		summary.put("advanceReceiptAmount", "150.00");
		summary.put("sourceCount", 5);
		summary.put("analysisMode", "LIVE");
		summary.put("freshnessStatus", "CURRENT");
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("summary", summary);
		payload.put("items", List.of(item));
		payload.put("page", 1);
		payload.put("pageSize", 20);
		payload.put("total", 1);
		payload.put("totalPages", 1);
		insertReportSnapshot(snapshotId, "CONTRACT_COLLECTION", payload, 5, marker + "-contract-collection");
		return new ContractCollectionSnapshotProbeFixture(periodCode, fixture.inventory().projectId(),
				fixture.inventory().projectNo(), fixture.contract().mainContractNo(), customerName,
				fixture.contract().salesInvoiceNo(), fixture.contract().receiptNo(),
				fixture.contract().receivableNo());
	}

	private void insertClosedBusinessSnapshotWithout033Report(String periodCode, String marker) throws Exception {
		long snapshotId = insertClosedBusinessSnapshot(periodCode, marker);
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("recordCount", 0);
		summary.put("analysisMode", "BUSINESS_SNAPSHOT");
		summary.put("freshnessStatus", "CURRENT");
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("summary", summary);
		payload.put("items", List.of());
		payload.put("page", 1);
		payload.put("pageSize", 20);
		payload.put("total", 0);
		payload.put("totalPages", 0);
		insertReportSnapshot(snapshotId, "SALES_SUMMARY", payload, 0, marker + "-sales-summary");
	}

	private SnapshotProjectDetailFixture seedProjectProfitSnapshotDetailFixture(String periodCode) throws Exception {
		OperatingFinanceFixture fixture = seedProjectProfitFixture(1);
		String marker = "033-SNAPSHOT-DETAIL-" + SEQUENCE.incrementAndGet();
		long snapshotId = insertClosedBusinessSnapshot(periodCode, marker);
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("projectId", fixture.firstProjectId());
		item.put("projectNo", fixture.firstProjectNo());
		item.put("projectName", "033 快照项目详情");
		item.put("customerName", "033 快照客户");
		item.put("shipmentRevenueAmount", "1234.00");
		item.put("invoiceRevenueAmount", "1200.00");
		item.put("targetRevenueAmount", "1500.00");
		item.put("projectCostAmount", "678.00");
		item.put("operatingGrossProfitAmount", "556.00");
		item.put("operatingGrossProfitRate", "45.06");
		item.put("accountingRevenueAmount", "1000.00");
		item.put("accountingCostAmount", "400.00");
		item.put("accountingProfitAmount", "600.00");
		item.put("differenceAmount", "-44.00");
		item.put("shipmentRevenue", "1234.00");
		item.put("invoiceRevenue", "1200.00");
		item.put("targetRevenue", "1500.00");
		item.put("projectCostTotal", "678.00");
		item.put("shipmentGrossMargin", "556.00");
		item.put("shipmentGrossMarginRate", "45.06");
		item.put("accountingRevenue", "1000.00");
		item.put("accountingCost", "400.00");
		item.put("accountingProfit", "600.00");
		item.put("completenessStatus", "COMPLETE");
		item.put("freshnessStatus", "FROZEN");
		item.put("reconciliationStatus", "DIFFERENT");
		item.put("finalityStatus", "FINAL");
		item.put("sourceCount", 3);
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("projectCount", 1);
		summary.put("shipmentRevenueAmount", "1234.00");
		summary.put("projectCostAmount", "678.00");
		summary.put("operatingGrossProfitAmount", "556.00");
		summary.put("sourceCount", 3);
		summary.put("amountVisible", true);
		summary.put("completenessStatus", "COMPLETE");
		summary.put("freshnessStatus", "FROZEN");
		summary.put("reconciliationStatus", "DIFFERENT");
		summary.put("finalityStatus", "FINAL");
		summary.put("analysisMode", "BUSINESS_SNAPSHOT");
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("summary", summary);
		payload.put("items", List.of(item));
		payload.put("page", 1);
		payload.put("pageSize", 20);
		payload.put("total", 1);
		payload.put("totalPages", 1);
		insertReportSnapshot(snapshotId, "PROJECT_PROFIT", payload, 3, marker + "-project-profit");
		return new SnapshotProjectDetailFixture(periodCode, fixture.firstProjectId(), fixture.firstProjectNo());
	}

	private long insertClosedBusinessSnapshot(String periodCode, String marker) {
		LocalDate startDate = LocalDate.parse(periodCode + "-01");
		LocalDate endDate = startDate.plusMonths(1).minusDays(1);
		long periodId = ensureBusinessPeriod(periodCode, startDate, endDate);
		long runId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_close_run (
					period_id, revision_no, status, source_fingerprint, inventory_fingerprint,
					wip_fingerprint, project_cost_fingerprint, report_fingerprint, blocking_count,
					warning_count, closed_by, closed_at, close_reason, created_by, created_at,
					updated_by, updated_at
				)
				values (?, 1, 'CLOSED', ?, ?, ?, ?, ?, 0, 0, 'test', now(), ?,
					'test', now(), 'test', now())
				returning id
		""", Long.class, periodId, fingerprint(marker + "-source"), fingerprint(marker + "-inventory"),
				fingerprint(marker + "-wip"), fingerprint(marker + "-project-cost"),
				fingerprint(marker + "-report"), marker + " 业务快照整改验收");
		long checkRunId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_close_check_run (
					run_id, period_id, revision_no, status, source_fingerprint, inventory_fingerprint,
					wip_fingerprint, project_cost_fingerprint, report_fingerprint, blocking_count,
					warning_count, started_by, started_at, completed_at
				)
				values (?, ?, 1, 'READY', ?, ?, ?, ?, ?, 0, 0, 'test', now(), now())
				returning id
				""", Long.class, runId, periodId, fingerprint(marker + "-source"),
				fingerprint(marker + "-inventory"), fingerprint(marker + "-wip"),
				fingerprint(marker + "-project-cost"), fingerprint(marker + "-report"));
		long snapshotId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_snapshot (
					run_id, period_id, revision_no, source_check_run_id, source_fingerprint,
					inventory_fingerprint, wip_fingerprint, project_cost_fingerprint, report_fingerprint,
					generated_by, generated_at
				)
				values (?, ?, 1, ?, ?, ?, ?, ?, ?, 'test', now())
				returning id
				""", Long.class, runId, periodId, checkRunId, fingerprint(marker + "-source"),
				fingerprint(marker + "-inventory"), fingerprint(marker + "-wip"),
				fingerprint(marker + "-project-cost"), fingerprint(marker + "-report"));
		this.jdbcTemplate.update("""
				update biz_period_close_run
				set latest_check_run_id = ?,
				    snapshot_id = ?,
				    updated_at = now()
				where id = ?
				""", checkRunId, snapshotId, runId);
		return snapshotId;
	}

	private void reopenBusinessPeriod(String periodCode, String reason) {
		this.jdbcTemplate.update("""
				update biz_period_close_run run
				set status = 'REOPENED',
				    reopened_by = 'test',
				    reopened_at = now(),
				    reopen_reason = ?,
				    updated_by = 'test',
				    updated_at = now()
				from biz_business_period period
				where run.period_id = period.id
				and period.period_code = ?
				and run.status = 'CLOSED'
				""", reason, periodCode);
	}

	private void reopenFinancialClose(String periodCode, String reason) {
		this.jdbcTemplate.update("""
				update fin_close_run run
				set status = 'REOPENED',
				    reopened_by = 'test',
				    reopened_at = now(),
				    reopen_reason = ?,
				    updated_at = now()
				from gl_accounting_period period
				where run.period_id = period.id
				and period.period_code = ?
				and run.status = 'CLOSED'
				""", reason, periodCode);
	}

	private void insertReportSnapshot(long snapshotId, String reportCode, Object payload, int sourceCount,
			String marker) throws Exception {
		this.jdbcTemplate.update("""
				insert into biz_period_report_snapshot (
					snapshot_id, report_code, schema_version, result_json, source_count, fingerprint, created_at
				)
				values (?, ?, 1, ?::jsonb, ?, ?, now())
				""", snapshotId, reportCode, this.objectMapper.writeValueAsString(payload), sourceCount,
				fingerprint(marker));
	}

	private long insertCustomer(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "033 验收客户 " + code);
	}

	private long insertProject(String projectNo, long customerId, long ownerUserId, int index) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, remark, created_by, created_at, updated_by, updated_at,
					activated_by, activated_at
				)
				values (?, ?, ?, ?, date '2097-01-01', date '2097-01-31', 'ACTIVE',
					1200.00 + ?, 600.00 + ?, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, projectNo, "033 项目利润验收 " + index, customerId, ownerUserId,
				BigDecimal.valueOf(index), BigDecimal.valueOf(index), "033 验收项目");
	}

	private void insertProjectCostCalculation(long projectId, String projectNo, int index) {
		this.jdbcTemplate.update("""
				insert into prj_cost_calculation (
					project_id, calculation_no, cutoff_date, status, is_current, source_fingerprint,
					project_cost_total, wip_cost, finished_cost, delivered_cost, direct_project_cost,
					shipment_revenue, invoice_revenue, target_revenue,
					shipment_gross_margin, invoice_gross_margin, target_gross_margin,
					shipment_gross_margin_rate, invoice_gross_margin_rate, target_gross_margin_rate,
					margin_completeness, completeness_reason, idempotency_key, request_fingerprint,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, date '2097-01-31', 'CONFIRMED', true, ?,
					650.00, 120.00, 180.00, 300.00, 50.00,
					1000.00, 900.00, 1200.00,
					350.00, 250.00, 550.00,
					35.000000, 27.777778, 45.833333,
					?, ?, ?, ?, 'test', now(), 'test', now(), 'test', now())
				""", projectId, "033-CALC-" + projectNo, fingerprint("calc-" + projectNo),
				index == 1 ? "COMPLETE" : "INCOMPLETE",
				index == 1 ? null : "LEGACY_UNVALUED 来源未估值，不能按零汇总。",
				"033-calc-" + projectNo, fingerprint("request-" + projectNo));
	}

	private void insertProjectCostSourceLine(long projectId, String sourceNo, LocalDate businessDate) {
		insertProjectCostSourceLine(projectId, sourceNo, businessDate, "ACTUAL");
	}

	private void insertProjectCostSourceLine(long projectId, String sourceNo, LocalDate businessDate,
			String sourceStatus) {
		long calculationId = projectCostCalculationId(projectId);
		long sourceId = SEQUENCE.incrementAndGet();
		long sourceLineId = SEQUENCE.incrementAndGet();
		this.jdbcTemplate.update("""
				insert into prj_cost_source_line (
					calculation_id, project_id, cost_category, cost_stage, entry_type, source_type,
					source_id, source_line_id, source_no, source_status, business_date, quantity,
					unit_cost, source_amount, calculated_amount, source_fingerprint
				)
				values (?, ?, 'MATERIAL', 'DELIVERED', 'SOURCE_TO_WIP', 'STAGE033_ACCEPTANCE',
					?, ?, ?, ?, ?, 1.000000, 50.000000, 50.00, 50.00, ?)
				""", calculationId, projectId, sourceId, sourceLineId, sourceNo, sourceStatus, businessDate,
				fingerprint(sourceNo));
	}

	private void updateProjectCostFingerprint(long projectId, String marker) {
		this.jdbcTemplate.update("""
				update prj_cost_calculation
				set source_fingerprint = ?,
				    updated_by = 'test',
				    updated_at = now()
				where project_id = ?
				and is_current = true
				""", fingerprint(marker), projectId);
	}

	private String ensureChildAccount(String parentCode, String childCode, String name) {
		ChildAccountParent parent = this.jdbcTemplate.queryForObject("""
				select id, ledger_id, category, balance_direction, level_no
				from gl_account
				where code = ?
				""", (rs, rowNum) -> new ChildAccountParent(rs.getLong("id"), rs.getLong("ledger_id"),
				rs.getString("category"), rs.getString("balance_direction"), rs.getInt("level_no")),
				parentCode);
		this.jdbcTemplate.update("""
				update gl_account
				set is_leaf = false,
				    updated_by = 'test',
				    updated_at = now()
				where id = ?
				""", parent.id());
		return this.jdbcTemplate.queryForObject("""
				insert into gl_account (
					ledger_id, parent_id, code, name, category, balance_direction, level_no,
					is_leaf, postable, enabled, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, true, true, true, 'test', now(), 'test', now())
				on conflict (ledger_id, code) do update
				set parent_id = excluded.parent_id,
				    name = excluded.name,
				    category = excluded.category,
				    balance_direction = excluded.balance_direction,
				    postable = true,
				    enabled = true,
				    updated_by = 'test',
				    updated_at = now()
				returning code
				""", String.class, parent.ledgerId(), parent.id(), childCode, name, parent.category(),
				parent.balanceDirection(), parent.levelNo() + 1);
	}

	private void seedPostedAccountingEntriesWithChildAccounts(String periodCode, long projectId, String projectNo,
			String revenueChildCode, String costChildCode) {
		LocalDate startDate = LocalDate.parse(periodCode + "-01");
		long periodId = ensureAccountingPeriod(periodCode, startDate, startDate.plusMonths(1).minusDays(1));
		insertBalancedVoucher(periodId, projectId, projectNo,
				"033-CHILD-REVENUE-" + periodCode + "-" + SEQUENCE.incrementAndGet(),
				List.of(new VoucherLineSeed("1001", "DEBIT", "880.00", null),
						new VoucherLineSeed(revenueChildCode, "CREDIT", "880.00", projectNo)));
		insertBalancedVoucher(periodId, projectId, projectNo,
				"033-CHILD-COST-" + periodCode + "-" + SEQUENCE.incrementAndGet(),
				List.of(new VoucherLineSeed(costChildCode, "DEBIT", "330.00", projectNo),
						new VoucherLineSeed("1001", "CREDIT", "330.00", null)));
	}

	private Stage033PositiveFixture seedStage033PositiveFixture() {
		String marker = "033-POS-" + SEQUENCE.incrementAndGet();
		Stage033BaseFixture base = seedStage033BaseFixture(marker);
		ContractCollectionPositiveFacts contract = seedContractCollectionPositiveFacts(base, marker + "-CC");
		ProcurementVariancePositiveFacts procurement = seedProcurementVariancePositiveFacts(base, marker + "-PV");
		InventoryCapitalPositiveFacts inventory = seedInventoryCapitalPositiveFacts(base, marker);
		ReceivablePayablePositiveFacts receivablePayable = seedReceivablePayablePositiveFacts(base, marker + "-RP");
		return new Stage033PositiveFixture(marker, contract, procurement, inventory, receivablePayable);
	}

	private Stage033BaseFixture seedStage033BaseFixture(String marker) {
		long unitId = insertUnit(marker + "-UNIT", "033 正例单位 " + marker);
		long warehouseId = insertWarehouse(marker + "-WH", "033 正例仓库 " + marker);
		long customerId = insertCustomer(marker + "-CUST");
		long supplierId = insertSupplier(marker + "-SUP", "033 正例供应商 " + marker);
		long categoryId = insertMaterialCategory(marker + "-CAT", "033 正例分类 " + marker);
		long finishedMaterialId = insertMaterial(marker + "-FIN", "033 正例成品 " + marker,
				"FINISHED_GOOD", "SELF_MADE", categoryId, unitId, "FINISHED_GOOD");
		long rawMaterialId = insertMaterial(marker + "-RAW", "033 正例原料 " + marker,
				"RAW_MATERIAL", "PURCHASED", categoryId, unitId, "DIRECT_MATERIAL");
		long ownerUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		String projectNo = marker + "-PROJECT";
		long projectId = insertProject(projectNo, customerId, ownerUserId, 1);
		String mainContractNo = marker + "-CONTRACT-MAIN";
		long mainContractId = insertProjectContract(projectId, mainContractNo, "MAIN", null, "1200.00");
		return new Stage033BaseFixture(unitId, warehouseId, customerId, supplierId, categoryId,
				finishedMaterialId, rawMaterialId, projectId, projectNo, mainContractId, mainContractNo, marker);
	}

	private ContractCollectionPositiveFacts seedContractCollectionPositiveFacts(Stage033BaseFixture base,
			String marker) {
		LocalDate date = LocalDate.of(2026, 6, 10);
		SalesShipmentFact shipment = insertSalesShipment(base, base.mainContractId(), base.mainContractNo(),
				marker + "-SHIP-MAIN", date, "6.000000", "150.000000");
		long salesInvoiceId = insertSalesInvoice(base, shipment, marker + "-INV-MAIN", date.plusDays(1),
				date.plusDays(20), "900.00");
		ReceivableFact receivable = insertReceivable(shipment, marker + "-AR-MAIN", date,
				LocalDate.of(2026, 6, 15), "900.00", "300.00", "600.00", "PARTIALLY_RECEIVED");
		linkSalesInvoiceReceivable(salesInvoiceId, receivable.receivableId());
		long receiptId = insertReceipt(base.customerId(), marker + "-REC-MAIN", date.plusDays(3), "500.00",
				"POSTED");
		long receiptAllocationId = insertReceiptAllocation(receiptId, receivable.receivableId(), "300.00");
		insertReceiptBalance(receiptId, base.customerId(), "PROJECT", base.projectId(), "500.00", "300.00",
				"200.00", "POSTED");
		long allocationId = insertSettlementAllocation(marker + "-ALLOC-MAIN", "RECEIVABLE", "RECEIPT",
				receiptId, base.customerId(), "PROJECT", base.projectId(), date.plusDays(3), "300.00",
				"RECEIVABLE", receivable.receivableId());

		String supplementContractNo = marker + "-CONTRACT-SUPP";
		long supplementContractId = insertProjectContract(base.projectId(), supplementContractNo, "SUPPLEMENT",
				base.mainContractId(), "300.00");
		SalesShipmentFact supplementShipment = insertSalesShipment(base, supplementContractId,
				supplementContractNo, marker + "-SHIP-SUPP", date.plusDays(2), "1.000000", "100.000000");
		insertSalesInvoice(base, supplementShipment, marker + "-INV-SUPP", date.plusDays(3),
				LocalDate.of(2026, 7, 10), "100.00");
		insertReceivable(supplementShipment, marker + "-AR-SUPP", date.plusDays(2),
				LocalDate.of(2026, 7, 10), "100.00", "0.00", "100.00", "CONFIRMED");

		return new ContractCollectionPositiveFacts(marker, base.mainContractId(), base.mainContractNo(),
				supplementContractId, supplementContractNo, salesInvoiceId, marker + "-INV-MAIN",
				receivable.receivableId(), receivable.receivableNo(), receiptId, marker + "-REC-MAIN",
				receiptAllocationId, allocationId);
	}

	private ProcurementVariancePositiveFacts seedProcurementVariancePositiveFacts(Stage033BaseFixture base,
			String marker) {
		LocalDate date = LocalDate.of(2026, 6, 12);
		PurchaseReceiptFact projectReceipt = insertPurchaseReceipt(base, marker + "-PROJECT", "PROJECT",
				base.projectId(), base.rawMaterialId(), date, "10.000000", "50.000000");
		long purchaseReturnId = insertPurchaseReturn(base, projectReceipt, marker + "-RETURN-PROJECT", date.plusDays(1),
				"2.000000", "50.000000", "PROJECT", base.projectId());
		long purchaseInvoiceId = insertPurchaseInvoice(base, projectReceipt, marker + "-PINV-PROJECT",
				"STANDARD_PURCHASE", "PURCHASE_RECEIPT", projectReceipt.receiptId(), projectReceipt.receiptNo(),
				date.plusDays(2), "PROJECT", base.projectId(), "650.00", "EXCEPTION");
		insertPurchaseInvoiceDifference(purchaseInvoiceId, "THREE_WAY_MATCH_AMOUNT", "500.00", "650.00",
				"采购订单、收货和发票金额不一致");
		PayableFact payable = insertPayable(projectReceipt, marker + "-AP-PROJECT", "650.00", "200.00",
				"450.00", "PARTIALLY_PAID");
		linkPurchaseInvoicePayable(purchaseInvoiceId, payable.payableId());
		long paymentId = insertPayment(base.supplierId(), marker + "-PAY-PROJECT", date.plusDays(3),
				"200.00", "POSTED");
		insertPaymentAllocation(paymentId, payable.payableId(), "200.00");

		PurchaseReceiptFact publicReceipt = insertPurchaseReceipt(base, marker + "-PUBLIC", "PUBLIC", null,
				base.rawMaterialId(), date.plusDays(1), "4.000000", "20.000000");
		insertPurchaseInvoice(base, publicReceipt, marker + "-PINV-PUBLIC", "STANDARD_PURCHASE",
				"PURCHASE_RECEIPT", publicReceipt.receiptId(), publicReceipt.receiptNo(), date.plusDays(2),
				"PUBLIC", null, "80.00", "MATCHED");

		OutsourcingReceiptFact outsourcing = insertOutsourcingReceipt(base, marker + "-OUT", date.plusDays(2),
				"2.000000", "110.000000");
		long outsourcingInvoiceId = insertOutsourcingPurchaseInvoice(base, outsourcing, marker + "-PINV-OUT",
				date.plusDays(3), "220.00");

		return new ProcurementVariancePositiveFacts(marker, projectReceipt.orderId(),
				projectReceipt.orderNo(), projectReceipt.receiptId(), projectReceipt.receiptNo(),
				purchaseReturnId, purchaseInvoiceId, marker + "-PINV-PROJECT", paymentId,
				marker + "-PAY-PROJECT", publicReceipt.orderId(), publicReceipt.orderNo(),
				outsourcing.orderId(), outsourcing.orderNo(), outsourcing.receiptId(), outsourcing.receiptNo(),
				outsourcingInvoiceId);
	}

	private InventoryCapitalPositiveFacts seedInventoryCapitalPositiveFacts(Stage033BaseFixture base,
			String marker) {
		LocalDate date = LocalDate.of(2026, 6, 18);
		long projectValuedBalanceId = insertStockBalance(base, base.rawMaterialId(), "PROJECT", base.projectId(),
				"10.000000", "VALUED", "250.00", "25.000000");
		long projectValuedMovementId = insertStockMovementWithValue(base, base.rawMaterialId(), "PROJECT",
				base.projectId(), date, "10.000000", "VALUED", "25.000000", "250.00",
				projectValuedBalanceId, marker + "-MOV-PROJECT-VALUED");
		long publicValuedBalanceId = insertStockBalance(base, base.rawMaterialId(), "PUBLIC", null,
				"5.000000", "VALUED", "75.00", "15.000000");
		long publicValuedMovementId = insertStockMovementWithValue(base, base.rawMaterialId(), "PUBLIC",
				null, date, "5.000000", "VALUED", "15.000000", "75.00", publicValuedBalanceId,
				marker + "-MOV-PUBLIC-VALUED");
		long projectUnvaluedBalanceId = insertStockBalance(base, base.finishedMaterialId(), "PROJECT",
				base.projectId(), "3.000000", "LEGACY_UNVALUED", null, null);
		long projectUnvaluedMovementId = insertStockMovementWithValue(base, base.finishedMaterialId(),
				"PROJECT", base.projectId(), date, "3.000000", "LEGACY_UNVALUED", null, null,
				projectUnvaluedBalanceId, marker + "-MOV-PROJECT-UNVALUED");
		String snapshotMarker = marker + "-SNAPSHOT";
		insertInventoryCapitalSnapshot(marker, snapshotMarker);
		return new InventoryCapitalPositiveFacts(marker, snapshotMarker, base.projectId(), base.projectNo(),
				projectValuedBalanceId, projectValuedMovementId, publicValuedBalanceId, publicValuedMovementId,
				projectUnvaluedBalanceId, projectUnvaluedMovementId);
	}

	private ReceivablePayablePositiveFacts seedReceivablePayablePositiveFacts(Stage033BaseFixture base,
			String marker) {
		LocalDate businessDate = LocalDate.of(2026, 6, 5);
		ReceivableFact notDue = seedReceivableBucket(base, marker + "-AR-NOT-DUE", businessDate,
				LocalDate.of(2026, 7, 15), "100.00", "0.00", "100.00");
		ReceivableFact days1To30 = seedReceivableBucket(base, marker + "-AR-1-30", businessDate,
				LocalDate.of(2026, 6, 20), "200.00", "0.00", "200.00");
		ReceivableFact days31To60 = seedReceivableBucket(base, marker + "-AR-31-60", businessDate,
				LocalDate.of(2026, 5, 20), "300.00", "100.00", "200.00");
		ReceivableFact days61To90 = seedReceivableBucket(base, marker + "-AR-61-90", businessDate,
				LocalDate.of(2026, 4, 15), "400.00", "0.00", "400.00");
		ReceivableFact daysOver90 = seedReceivableBucket(base, marker + "-AR-OVER-90", businessDate,
				LocalDate.of(2026, 3, 1), "500.00", "0.00", "500.00");
		long receiptId = insertReceipt(base.customerId(), marker + "-REC-ALLOC", businessDate.plusDays(1),
				"100.00", "POSTED");
		insertReceiptAllocation(receiptId, days31To60.receivableId(), "100.00");
		long allocationId = insertSettlementAllocation(marker + "-ALLOC-AR", "RECEIVABLE", "RECEIPT",
				receiptId, base.customerId(), "PROJECT", base.projectId(), businessDate.plusDays(1),
				"100.00", "RECEIVABLE", days31To60.receivableId());
		long advanceReceiptId = insertReceipt(base.customerId(), marker + "-REC-ADVANCE", businessDate.plusDays(2),
				"120.00", "POSTED");
		insertReceiptBalance(advanceReceiptId, base.customerId(), "PROJECT", base.projectId(), "120.00",
				"0.00", "120.00", "POSTED");

		PurchaseReceiptFact payableReceipt = insertPurchaseReceipt(base, marker + "-AP-SOURCE", "PROJECT",
				base.projectId(), base.rawMaterialId(), businessDate, "5.000000", "50.000000");
		PayableFact payable = insertPayable(payableReceipt, marker + "-AP-MAIN", "250.00", "50.00",
				"200.00", "PARTIALLY_PAID", LocalDate.of(2026, 5, 25));
		long paymentId = insertPayment(base.supplierId(), marker + "-PAY-ALLOC", businessDate.plusDays(3),
				"50.00", "POSTED");
		insertPaymentAllocation(paymentId, payable.payableId(), "50.00");
		long prepaymentId = insertPayment(base.supplierId(), marker + "-PAY-ADVANCE", businessDate.plusDays(4),
				"90.00", "POSTED");
		insertPaymentBalance(prepaymentId, base.supplierId(), "PROJECT", base.projectId(), "90.00",
				"0.00", "90.00", "POSTED");
		return new ReceivablePayablePositiveFacts(marker, notDue.receivableNo(), days1To30.receivableNo(),
				days31To60.receivableNo(), days61To90.receivableNo(), daysOver90.receivableNo(),
				days31To60.receivableId(), receiptId, allocationId, advanceReceiptId, payable.payableId(),
				payable.payableNo(), payableReceipt.receiptId(), paymentId, prepaymentId);
	}

	private void seedPostedAccountingEntries(long projectId, String projectNo) {
		long periodId = ensureAccountingPeriod("2097-02", LocalDate.of(2097, 2, 1),
				LocalDate.of(2097, 2, 28));
		insertBalancedVoucher(periodId, projectId, projectNo, "033-PROJECT-ACCOUNTING",
				List.of(new VoucherLineSeed("1001", "DEBIT", "900.00", null),
						new VoucherLineSeed("6001", "CREDIT", "900.00", projectNo)));
		insertBalancedVoucher(periodId, projectId, projectNo, "033-PROJECT-COST",
				List.of(new VoucherLineSeed("6401", "DEBIT", "500.00", projectNo),
						new VoucherLineSeed("1001", "CREDIT", "500.00", null)));
		insertBalancedVoucher(periodId, projectId, projectNo, "033-PUBLIC-REVENUE",
				List.of(new VoucherLineSeed("1001", "DEBIT", "77.00", null),
						new VoucherLineSeed("6001", "CREDIT", "77.00", null)));
	}

	private void seedFinancialSummaryBasisFacts(String periodCode) {
		LocalDate startDate = LocalDate.parse(periodCode + "-01");
		long periodId = ensureAccountingPeriod(periodCode, startDate, startDate.plusMonths(1).minusDays(1));
		insertBalancedVoucher(periodId, 0L, "", "033-FINANCIAL-SUMMARY-ASSET-EQUITY",
				List.of(new VoucherLineSeed("1001", "DEBIT", "300.00", null),
						new VoucherLineSeed("4001", "CREDIT", "300.00", null)));
		insertBalancedVoucher(periodId, 0L, "", "033-FINANCIAL-SUMMARY-ASSET-LIABILITY",
				List.of(new VoucherLineSeed("1001", "DEBIT", "200.00", null),
						new VoucherLineSeed("2202", "CREDIT", "200.00", null)));
		seedClosedFinancialStatus(periodId, periodCode);
	}

	private void seedClosedFinancialStatus(long periodId, String periodCode) {
		if (exists("""
				select 1
				from fin_close_run
				where period_id = ?
				and status = 'CLOSED'
				""", periodId)) {
			return;
		}
		long ledgerId = this.jdbcTemplate.queryForObject("""
				select ledger_id
				from gl_accounting_period
				where id = ?
				""", Long.class, periodId);
		String marker = "033-FIN-STATUS-" + periodCode + "-" + SEQUENCE.incrementAndGet();
		long checkRunId = this.jdbcTemplate.queryForObject("""
				insert into fin_close_check_run (
					ledger_id, period_id, status, close_version, source_fingerprint,
					blocking_count, warning_count, created_by, created_at, completed_at
				)
				values (?, ?, 'CONSUMED', 1, ?, 0, 0, 'test', now(), now())
				returning id
				""", Long.class, ledgerId, periodId, fingerprint(marker + "-check"));
		long runId = this.jdbcTemplate.queryForObject("""
				insert into fin_close_run (
					ledger_id, period_id, check_run_id, close_version, status, source_fingerprint,
					closed_by, closed_at, close_reason, created_at, updated_at
				)
				values (?, ?, ?, 1, 'CLOSED', ?, 'test', now(), '033 固定摘要财务关闭正例', now(), now())
				returning id
				""", Long.class, ledgerId, periodId, checkRunId, fingerprint(marker + "-close"));
		long snapshotId = this.jdbcTemplate.queryForObject("""
				insert into fin_close_snapshot (
					close_run_id, period_id, close_version, source_fingerprint, trial_balance_json,
					bank_reconciliation_json, tax_summary_json, created_by, created_at
				)
				values (?, ?, 1, ?, '{"status":"MATCHED"}'::jsonb,
					'{"status":"CONFIRMED"}'::jsonb, '{"status":"CONFIRMED"}'::jsonb, 'test', now())
				returning id
				""", Long.class, runId, periodId, fingerprint(marker + "-snapshot"));
		this.jdbcTemplate.update("""
				update fin_close_run
				set snapshot_id = ?,
				    updated_at = now()
				where id = ?
				""", snapshotId, runId);
		long bankAccountId = this.jdbcTemplate.queryForObject("""
				insert into fin_bank_account (
					account_name, account_type, bank_name, currency, gl_account_id, account_fingerprint,
					account_last4, account_masked, status, opened_on, created_by, updated_by
				)
				select ?, 'BASIC', '033 验收银行', 'CNY', id, ?, '0033', '****0033',
					'ENABLED', ?::date, 'test', 'test'
				from gl_account
				where code = '1002'
				returning id
				""", Long.class, "033 固定摘要银行 " + marker, fingerprint(marker + "-bank-account"),
				periodCode + "-01");
		this.jdbcTemplate.update("""
				insert into fin_bank_reconciliation_run (
					period_id, bank_account_id, status, statement_balance, ledger_balance, difference_amount,
					source_fingerprint, confirmed_by, confirmed_at, created_by, updated_by
				)
				values (?, ?, 'CONFIRMED', 0, 0, 0, ?, 'test', now(), 'test', 'test')
				""", periodId, bankAccountId, fingerprint(marker + "-bank-reconciliation"));
		this.jdbcTemplate.update("""
				insert into fin_tax_period_summary (
					period_id, period_code, tax_type, status, source_fingerprint, output_vat, input_vat,
					vat_payable, disclaimer, current_flag, created_by, updated_by
				)
				values (?, ?, 'VAT', 'CONFIRMED', ?, 10.00, 2.00, 8.00,
					'033 税务基础状态验收，不代表正式申报', true, 'test', 'test')
				""", periodId, periodCode, fingerprint(marker + "-tax"));
	}

	private long ensureAccountingPeriod(String periodCode, LocalDate startDate, LocalDate endDate) {
		List<Long> existing = this.jdbcTemplate.queryForList(
				"select id from gl_accounting_period where period_code = ?", Long.class, periodCode);
		if (!existing.isEmpty()) {
			return existing.get(0);
		}
		long ledgerId = this.jdbcTemplate.queryForObject("select id from gl_ledger where code = 'MAIN'",
				Long.class);
		return this.jdbcTemplate.queryForObject("""
				insert into gl_accounting_period (
					ledger_id, period_code, start_date, end_date, status,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 'OPEN', 'test', now(), 'test', now())
				returning id
				""", Long.class, ledgerId, periodCode, startDate, endDate);
	}

	private void insertBalancedVoucher(long periodId, long projectId, String projectNo, String marker,
			List<VoucherLineSeed> lines) {
		long ledgerId = this.jdbcTemplate.queryForObject("select ledger_id from gl_accounting_period where id = ?",
				Long.class, periodId);
		int number = SEQUENCE.incrementAndGet();
		String voucherNo = "记-" + number;
		BigDecimal debitTotal = lines.stream()
			.filter((line) -> "DEBIT".equals(line.direction()))
			.map((line) -> new BigDecimal(line.amount()))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal creditTotal = lines.stream()
			.filter((line) -> "CREDIT".equals(line.direction()))
			.map((line) -> new BigDecimal(line.amount()))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		long voucherId = this.jdbcTemplate.queryForObject("""
				insert into gl_voucher (
					ledger_id, accounting_period_id, draft_no, voucher_type, voucher_date, status,
					summary, source_type, source_id, source_no, source_fingerprint, source_payload,
					currency, debit_total, credit_total, voucher_word,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, 'GENERAL', date '2097-02-10', 'DRAFT',
					?, 'MANUAL', null, ?, ?, '{}'::jsonb, 'CNY', ?, ?, '记',
					'test', now(), 'test', now())
				returning id
				""", Long.class, ledgerId, periodId, "033-DRAFT-" + marker + "-" + number,
				marker, marker, fingerprint(marker), debitTotal, creditTotal);
		int lineNo = 1;
		for (VoucherLineSeed line : lines) {
			long lineId = insertVoucherLine(voucherId, lineNo, line);
			if (line.projectAuxiliaryCode() != null) {
				insertProjectAuxiliary(lineId, projectId, projectNo);
			}
			insertLedgerEntry(ledgerId, periodId, voucherId, lineId, lineNo, voucherNo, number, line,
					line.projectAuxiliaryCode() == null ? "[]" : """
							[{"dimensionCode":"PROJECT","objectId":%d,"objectCode":"%s"}]
							""".formatted(projectId, projectNo).trim());
			lineNo++;
		}
		this.jdbcTemplate.update("""
				update gl_voucher
				set status = 'POSTED',
				    voucher_number = ?,
				    voucher_no = ?,
				    submitted_by = 'test',
				    submitted_at = now(),
				    posted_by = 'test',
				    posted_at = now(),
				    updated_at = now()
				where id = ?
				""", number, voucherNo, voucherId);
	}

	private long insertVoucherLine(long voucherId, int lineNo, VoucherLineSeed line) {
		AccountSnapshot account = account(line.accountCode());
		BigDecimal amount = new BigDecimal(line.amount());
		BigDecimal debit = "DEBIT".equals(line.direction()) ? amount : BigDecimal.ZERO;
		BigDecimal credit = "CREDIT".equals(line.direction()) ? amount : BigDecimal.ZERO;
		return this.jdbcTemplate.queryForObject("""
				insert into gl_voucher_line (
					voucher_id, line_no, summary, account_id, account_code, account_name,
					account_category, account_balance_direction, debit_amount, credit_amount, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
				returning id
				""", Long.class, voucherId, lineNo, "033 会计对照验收", account.id(), account.code(),
				account.name(), account.category(), account.balanceDirection(), debit, credit);
	}

	private void insertProjectAuxiliary(long voucherLineId, long projectId, String projectNo) {
		AuxDimension project = projectDimension();
		this.jdbcTemplate.update("""
				insert into gl_voucher_line_auxiliary (
					voucher_line_id, dimension_id, dimension_code, dimension_name, object_type,
					object_id, object_code, object_name, aux_item_id, created_at
				)
				values (?, ?, 'PROJECT', ?, 'PROJECT', ?, ?, ?, null, now())
				""", voucherLineId, project.id(), project.name(), projectId, projectNo, "033 项目辅助");
	}

	private void insertLedgerEntry(long ledgerId, long periodId, long voucherId, long lineId, int lineNo,
			String voucherNo, int voucherNumber, VoucherLineSeed line, String auxiliarySnapshot) {
		AccountSnapshot account = account(line.accountCode());
		BigDecimal amount = new BigDecimal(line.amount());
		BigDecimal debit = "DEBIT".equals(line.direction()) ? amount : BigDecimal.ZERO;
		BigDecimal credit = "CREDIT".equals(line.direction()) ? amount : BigDecimal.ZERO;
		this.jdbcTemplate.update("""
				insert into gl_ledger_entry (
					ledger_id, period_id, voucher_id, voucher_line_id, voucher_date, voucher_no,
					voucher_word, voucher_number, line_no, summary, account_id, account_code,
					account_name, balance_direction, voucher_type, debit_amount, credit_amount,
					auxiliary_snapshot, source_type, source_id, source_no, source_route,
					posted_by, posted_at, created_at
				)
				values (?, ?, ?, ?, date '2097-02-10', ?, '记', ?, ?, '033 会计对照验收',
					?, ?, ?, ?, 'GENERAL', ?, ?, ?::jsonb, 'MANUAL', null, null, null,
					'test', now(), now())
				""", ledgerId, periodId, voucherId, lineId, voucherNo, voucherNumber, lineNo,
				account.id(), account.code(), account.name(), account.balanceDirection(), debit, credit,
				auxiliarySnapshot);
	}

	private AccountSnapshot account(String code) {
		return this.jdbcTemplate.queryForObject("""
				select id, code, name, category, balance_direction
				from gl_account
				where code = ?
				""", (rs, rowNum) -> new AccountSnapshot(rs.getLong("id"), rs.getString("code"),
				rs.getString("name"), rs.getString("category"), rs.getString("balance_direction")), code);
	}

	private AuxDimension projectDimension() {
		return this.jdbcTemplate.queryForObject("""
				select id, name
				from gl_aux_dimension
				where code = 'PROJECT'
				""", (rs, rowNum) -> new AuxDimension(rs.getLong("id"), rs.getString("name")));
	}

	private long insertUnit(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 6, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertWarehouse(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertSupplier(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertMaterialCategory(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertMaterial(String code, String name, String materialType, String sourceType, long categoryId,
			long unitId, String costCategory) {
		long materialId = this.jdbcTemplate.queryForObject("""
				insert into mst_material (
					code, name, specification, material_type, source_type, category_id, unit_id, status,
					remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, '033 正例规格', ?, ?, ?, ?, 'ENABLED', '033 正例物料',
					'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, materialType, sourceType, categoryId, unitId);
		this.jdbcTemplate.update("""
				update mst_material
				set cost_category = ?,
				    inventory_valuation_category = 'VALUATED_MATERIAL',
				    inventory_value_enabled = true,
				    project_cost_enabled = true,
				    updated_by = 'test',
				    updated_at = now()
				where id = ?
				""", costCategory, materialId);
		return materialId;
	}

	private long insertProjectContract(long projectId, String contractNo, String contractType, Long mainContractId,
			String amount) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project_contract (
					contract_no, external_contract_no, project_id, contract_type, main_contract_id, name,
					signed_date, effective_start_date, effective_end_date, amount, status, remark,
					created_by, created_at, updated_by, updated_at, activated_by, activated_at
				)
				values (?, ?, ?, ?, ?, ?, date '2026-06-01', date '2026-06-01', date '2026-12-31',
					?::numeric, 'EFFECTIVE', '033 合同回款正例', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, contractNo, contractNo + "-EXT", projectId, contractType, mainContractId,
				"033 正例合同 " + contractNo, amount);
	}

	private SalesShipmentFact insertSalesShipment(Stage033BaseFixture base, long contractId, String contractNo,
			String documentNo, LocalDate businessDate, String quantity, String unitPrice) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, project_id, contract_id,
					currency, tax_excluded_amount, tax_amount, tax_included_amount, sales_fulfillment_compatible,
					remark, created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'SHIPPED', ?, ?, 'CNY', ?, 0, ?, true, ?,
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, documentNo + "-SO", base.customerId(), businessDate,
				businessDate.plusDays(5), base.projectId(), contractId, amount, amount, contractNo);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, remark, reservation_warehouse_id, price_source_type, source_no,
					currency, tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, 'CONTRACT', ?, 'CNY', 0, ?, ?, ?, 0, ?, now(), now())
				returning id
				""", Long.class, orderId, base.finishedMaterialId(), base.unitId(), quantityValue, quantityValue,
				unitPriceValue, businessDate.plusDays(5), contractNo, base.warehouseId(), contractNo,
				unitPriceValue, unitPriceValue, amount, amount);
		long shipmentId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, documentNo, orderId, base.customerId(), base.warehouseId(), businessDate,
				contractNo);
		long shipmentLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, price_source_type, source_no, currency, tax_rate,
					tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_amount,
					tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, null, null, ?, 'CONTRACT', ?, 'CNY', 0,
					?, ?, ?, 0, ?, now(), now())
				returning id
				""", Long.class, shipmentId, orderLineId, base.finishedMaterialId(), base.unitId(),
				quantityValue, quantityValue, quantityValue, contractNo, contractNo, unitPriceValue,
				unitPriceValue, amount, amount);
		return new SalesShipmentFact(shipmentId, documentNo, shipmentLineId, orderId, orderLineId);
	}

	private long insertSalesInvoice(Stage033BaseFixture base, SalesShipmentFact shipment, String invoiceNo,
			LocalDate invoiceDate, LocalDate dueDate, String amount) {
		long invoiceId = this.jdbcTemplate.queryForObject("""
				insert into fin_sales_invoice (
					invoice_no, customer_id, ownership_type, project_id, source_type, source_id, source_no,
					invoice_date, due_date, external_invoice_no, invoice_type, currency, tax_excluded_amount,
					tax_amount, tax_included_amount, status, party_snapshot, source_snapshot, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'PROJECT', ?, 'SALES_SHIPMENT', ?, ?, ?, ?, ?, 'NONE', 'CNY',
					?::numeric, 0, ?::numeric, 'CONFIRMED', '{}'::jsonb, '{}'::jsonb, '033 合同回款发票',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, invoiceNo, base.customerId(), base.projectId(), shipment.shipmentId(),
				shipment.shipmentNo(), invoiceDate, dueDate, invoiceNo + "-EXT", amount, amount);
		this.jdbcTemplate.update("""
				insert into fin_sales_invoice_line (
					sales_invoice_id, line_no, source_line_id, sales_order_id, sales_order_line_id,
					material_id, unit_id, quantity, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
					tax_excluded_amount, tax_amount, tax_included_amount, source_snapshot, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, 1.000000, 0, ?::numeric, ?::numeric,
					?::numeric, 0, ?::numeric, '{}'::jsonb, now(), now())
				""", invoiceId, shipment.shipmentLineId(), shipment.orderId(), shipment.orderLineId(),
				base.finishedMaterialId(), base.unitId(), amount, amount, amount, amount);
		return invoiceId;
	}

	private ReceivableFact insertReceivable(SalesShipmentFact shipment, String receivableNo,
			LocalDate businessDate, LocalDate dueDate, String totalAmount, String receivedAmount,
			String unreceivedAmount, String status) {
		long customerId = this.jdbcTemplate.queryForObject(
				"select customer_id from sal_sales_shipment where id = ?", Long.class, shipment.shipmentId());
		long receivableId = this.jdbcTemplate.queryForObject("""
				insert into fin_receivable (
					receivable_no, customer_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, received_amount, unreceived_amount, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'SALES_SHIPMENT', ?, ?, ?, ?, ?::numeric, ?::numeric, ?::numeric, ?,
					'033 往来应收正例', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, receivableNo, customerId, shipment.shipmentId(), shipment.shipmentNo(),
				businessDate, dueDate, totalAmount, receivedAmount, unreceivedAmount, status);
		this.jdbcTemplate.update("""
				insert into fin_receivable_source (
					receivable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'SALES_SHIPMENT', ?, ?, ?, 1, ?::numeric)
				""", receivableId, shipment.shipmentId(), shipment.shipmentNo(), shipment.shipmentLineId(),
				totalAmount);
		return new ReceivableFact(receivableId, receivableNo);
	}

	private void linkSalesInvoiceReceivable(long salesInvoiceId, long receivableId) {
		this.jdbcTemplate.update("""
				update fin_sales_invoice
				set linked_receivable_id = ?, updated_by = 'test', updated_at = now()
				where id = ?
				""", receivableId, salesInvoiceId);
		this.jdbcTemplate.update("""
				insert into fin_sales_invoice_receivable_link (
					sales_invoice_id, receivable_id, link_mode, created_by, created_at
				)
				values (?, ?, 'BIND_EXISTING', 'test', now())
				""", salesInvoiceId, receivableId);
	}

	private long insertReceipt(long customerId, String receiptNo, LocalDate receiptDate, String amount,
			String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_receipt (
					receipt_no, customer_id, receipt_date, amount, method, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?::numeric, 'BANK', ?, '033 收款正例', 'test', now(), 'test', now(), ?,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, receiptNo, customerId, receiptDate, amount, status,
				"POSTED".equals(status) ? "test" : null, status);
	}

	private long insertReceiptAllocation(long receiptId, long receivableId, String amount) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_receipt_allocation (receipt_id, receivable_id, allocated_amount)
				values (?, ?, ?::numeric)
				returning id
				""", Long.class, receiptId, receivableId, amount);
	}

	private void insertReceiptBalance(long receiptId, long customerId, String ownershipType, Long projectId,
			String originalAmount, String allocatedAmount, String availableAmount, String status) {
		this.jdbcTemplate.update("""
				insert into fin_receipt_balance (
					receipt_id, customer_id, ownership_type, project_id, original_amount, allocated_amount,
					available_amount, status, updated_at
				)
				values (?, ?, ?, ?, ?::numeric, ?::numeric, ?::numeric, ?, now())
				""", receiptId, customerId, ownershipType, projectId, originalAmount, allocatedAmount,
				availableAmount, status);
	}

	private long insertSettlementAllocation(String allocationNo, String settlementSide, String cashSourceType,
			long cashSourceId, long partyId, String ownershipType, Long projectId, LocalDate businessDate,
			String amount, String targetType, long targetId) {
		long allocationId = this.jdbcTemplate.queryForObject("""
				insert into fin_settlement_allocation (
					allocation_no, settlement_side, cash_source_type, cash_source_id, party_id, ownership_type,
					project_id, business_date, total_amount, status, idempotency_key, request_fingerprint,
					remark, created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?::numeric, 'POSTED', ?, ?, '033 核销正例',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, allocationNo, settlementSide, cashSourceType, cashSourceId, partyId,
				ownershipType, projectId, businessDate, amount, allocationNo + "-IDEMP",
				fingerprint(allocationNo));
		this.jdbcTemplate.update("""
				insert into fin_settlement_allocation_line (
					allocation_id, line_no, target_type, target_id, amount, created_at
				)
				values (?, 1, ?, ?, ?::numeric, now())
				""", allocationId, targetType, targetId, amount);
		return allocationId;
	}

	private PurchaseReceiptFact insertPurchaseReceipt(Stage033BaseFixture base, String documentNo,
			String purchaseMode, Long projectId, long materialId, LocalDate businessDate, String quantity,
			String unitPrice) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, purchase_mode,
					project_id, currency, public_direct_reason, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'RECEIVED', ?, ?, 'CNY', ?, '033 采购差异正例',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, documentNo + "-PO", base.supplierId(), businessDate,
				businessDate.plusDays(5), purchaseMode, projectId,
				"PUBLIC".equals(purchaseMode) ? "033 公共采购正例" : null);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					expected_arrival_date, remark, price_source_type, tax_rate, tax_excluded_unit_price,
					tax_included_unit_price, tax_excluded_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, '033 采购订单行正例', 'MANUAL', 0, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, materialId, base.unitId(), quantityValue, quantityValue,
				unitPriceValue, businessDate.plusDays(5), unitPriceValue, unitPriceValue, amount, amount);
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', '033 采购收货正例',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, documentNo + "-PR", orderId, base.supplierId(), base.warehouseId(),
				businessDate);
		long receiptLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, purchase_mode, project_id, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, null, null, '033 采购收货行正例', ?, ?, now(), now())
				returning id
				""", Long.class, receiptId, orderLineId, materialId, base.unitId(), quantityValue,
				quantityValue, quantityValue, purchaseMode, projectId);
		return new PurchaseReceiptFact(orderId, documentNo + "-PO", orderLineId, receiptId,
				documentNo + "-PR", receiptLineId, base.supplierId(), materialId, businessDate);
	}

	private long insertPurchaseReturn(Stage033BaseFixture base, PurchaseReceiptFact receipt, String returnNo,
			LocalDate businessDate, String quantity, String unitPrice, String purchaseMode, Long projectId) {
		BigDecimal amount = new BigDecimal(quantity).multiply(new BigDecimal(unitPrice));
		long returnId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_return (
					return_no, supplier_id, source_receipt_id, source_receipt_no, warehouse_id, business_date,
					status, total_amount, remark, purchase_mode, project_id,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, 'POSTED', ?, '033 采购退货正例', ?, ?,
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, returnNo, base.supplierId(), receipt.receiptId(), receipt.receiptNo(),
				base.warehouseId(), businessDate, amount, purchaseMode, projectId);
		this.jdbcTemplate.update("""
				insert into proc_purchase_return_line (
					return_id, source_receipt_line_id, purchase_order_line_id, material_id, unit_id, line_no,
					returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount,
					quality_status, reason, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, 1, 0, ?::numeric, ?::numeric, ?::numeric, ?::numeric,
					'QUALIFIED', '033 采购退货差异正例', now(), now())
				""", returnId, receipt.receiptLineId(), receipt.orderLineId(), receipt.materialId(), base.unitId(),
				quantity, quantity, unitPrice, amount);
		return returnId;
	}

	private long insertPurchaseInvoice(Stage033BaseFixture base, PurchaseReceiptFact receipt, String invoiceNo,
			String settlementKind, String sourceType, long sourceId, String sourceNo, LocalDate invoiceDate,
			String ownershipType, Long projectId, String amount, String matchStatus) {
		long invoiceId = this.jdbcTemplate.queryForObject("""
				insert into fin_purchase_invoice (
					invoice_no, supplier_id, settlement_kind, ownership_type, project_id, source_type,
					source_id, source_no, invoice_date, due_date, supplier_invoice_no, invoice_type,
					currency, match_status, tax_excluded_amount, tax_amount, tax_included_amount, status,
					party_snapshot, source_snapshot, remark, created_by, created_at, updated_by, updated_at,
					matched_by, matched_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NONE', 'CNY', ?, ?::numeric, 0, ?::numeric,
					'CONFIRMED', '{}'::jsonb, '{}'::jsonb, '033 采购发票正例',
					'test', now(), 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, invoiceNo, base.supplierId(), settlementKind, ownershipType, projectId,
				sourceType, sourceId, sourceNo, invoiceDate, invoiceDate.plusDays(10), invoiceNo + "-SUP",
				matchStatus, amount, amount);
		this.jdbcTemplate.update("""
				insert into fin_purchase_invoice_line (
					purchase_invoice_id, line_no, source_line_id, purchase_order_id, purchase_order_line_id,
					material_id, unit_id, quantity, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
					tax_excluded_amount, tax_amount, tax_included_amount, match_status, source_snapshot,
					created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, 1.000000, 0, ?::numeric, ?::numeric, ?::numeric, 0,
					?::numeric, ?, '{}'::jsonb, now(), now())
				""", invoiceId, receipt.receiptLineId(), receipt.orderId(), receipt.orderLineId(),
				receipt.materialId(), base.unitId(), amount, amount, amount, amount,
				"EXCEPTION".equals(matchStatus) ? "EXCEPTION" : "MATCHED");
		return invoiceId;
	}

	private void insertPurchaseInvoiceDifference(long invoiceId, String differenceType, String expectedValue,
			String actualValue, String message) {
		this.jdbcTemplate.update("""
				insert into fin_purchase_invoice_match_difference (
					purchase_invoice_id, purchase_invoice_line_id, difference_type, expected_value,
					actual_value, message, created_at
				)
				select ?, id, ?, ?, ?, ?, now()
				from fin_purchase_invoice_line
				where purchase_invoice_id = ?
				limit 1
				""", invoiceId, differenceType, expectedValue, actualValue, message, invoiceId);
	}

	private PayableFact insertPayable(PurchaseReceiptFact receipt, String payableNo, String totalAmount,
			String paidAmount, String unpaidAmount, String status) {
		return insertPayable(receipt, payableNo, totalAmount, paidAmount, unpaidAmount, status,
				receipt.businessDate().plusDays(15));
	}

	private PayableFact insertPayable(PurchaseReceiptFact receipt, String payableNo, String totalAmount,
			String paidAmount, String unpaidAmount, String status, LocalDate dueDate) {
		long payableId = this.jdbcTemplate.queryForObject("""
				insert into fin_payable (
					payable_no, supplier_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, paid_amount, unpaid_amount, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?::numeric, ?::numeric, ?::numeric, ?,
					'033 应付正例', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, payableNo, receipt.supplierId(), receipt.receiptId(), receipt.receiptNo(),
				receipt.businessDate(), dueDate, totalAmount, paidAmount, unpaidAmount, status);
		this.jdbcTemplate.update("""
				insert into fin_payable_source (
					payable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'PURCHASE_RECEIPT', ?, ?, ?, 1, ?::numeric)
				""", payableId, receipt.receiptId(), receipt.receiptNo(), receipt.receiptLineId(),
				totalAmount);
		return new PayableFact(payableId, payableNo);
	}

	private void linkPurchaseInvoicePayable(long purchaseInvoiceId, long payableId) {
		this.jdbcTemplate.update("""
				update fin_purchase_invoice
				set linked_payable_id = ?, updated_by = 'test', updated_at = now()
				where id = ?
				""", payableId, purchaseInvoiceId);
		this.jdbcTemplate.update("""
				insert into fin_purchase_invoice_payable_link (
					purchase_invoice_id, payable_id, link_mode, created_by, created_at
				)
				values (?, ?, 'BIND_EXISTING', 'test', now())
				""", purchaseInvoiceId, payableId);
	}

	private long insertPayment(long supplierId, String paymentNo, LocalDate paymentDate, String amount,
			String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_payment (
					payment_no, supplier_id, payment_date, amount, method, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?::numeric, 'BANK', ?, '033 付款正例',
					'test', now(), 'test', now(), ?, case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, paymentNo, supplierId, paymentDate, amount, status,
				"POSTED".equals(status) ? "test" : null, status);
	}

	private long insertPaymentAllocation(long paymentId, long payableId, String amount) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_payment_allocation (payment_id, payable_id, allocated_amount)
				values (?, ?, ?::numeric)
				returning id
				""", Long.class, paymentId, payableId, amount);
	}

	private void insertPaymentBalance(long paymentId, long supplierId, String ownershipType, Long projectId,
			String originalAmount, String allocatedAmount, String availableAmount, String status) {
		this.jdbcTemplate.update("""
				insert into fin_payment_balance (
					payment_id, supplier_id, ownership_type, project_id, original_amount, allocated_amount,
					available_amount, status, updated_at
				)
				values (?, ?, ?, ?, ?::numeric, ?::numeric, ?::numeric, ?, now())
				""", paymentId, supplierId, ownershipType, projectId, originalAmount, allocatedAmount,
				availableAmount, status);
	}

	private OutsourcingReceiptFact insertOutsourcingReceipt(Stage033BaseFixture base, String documentNo,
			LocalDate businessDate, String quantity, String unitCost) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitCostValue = new BigDecimal(unitCost);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into mfg_outsourcing_order (
					outsourcing_order_no, supplier_id, product_material_id, planned_quantity,
					issued_quantity, received_quantity, issue_warehouse_id, receipt_warehouse_id,
					planned_issue_date, planned_receipt_date, status, ownership_type, project_id,
					provisional_unit_cost, remark, created_by, created_at, updated_by, updated_at,
					released_by, released_at
				)
				values (?, ?, ?, ?, 0, ?, ?, ?, ?, ?, 'COMPLETED', 'PROJECT', ?, ?, '033 外协正例',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, documentNo + "-OO", base.supplierId(), base.finishedMaterialId(),
				quantityValue, quantityValue, base.warehouseId(), base.warehouseId(), businessDate,
				businessDate.plusDays(5), base.projectId(), unitCostValue);
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into mfg_outsourcing_receipt (
					receipt_no, outsourcing_order_id, status, business_date, receipt_warehouse_id, quantity,
					rejected_quantity, provisional_unit_cost, unit_cost, valuation_state, ownership_type,
					project_id, before_quantity, after_quantity, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, 'POSTED', ?, ?, ?, 0, ?, ?, 'VALUED', 'PROJECT', ?, 0, ?, '033 外协收货正例',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, documentNo + "-OR", orderId, businessDate, base.warehouseId(), quantityValue,
				unitCostValue, unitCostValue, base.projectId(), quantityValue);
		long receiptLineId = this.jdbcTemplate.queryForObject("""
				insert into mfg_outsourcing_receipt_line (
					receipt_id, line_no, accepted_quantity, rejected_quantity, provisional_unit_cost,
					unit_cost, before_quantity, after_quantity, remark, created_at, updated_at
				)
				values (?, 1, ?, 0, ?, ?, 0, ?, '033 外协收货行正例', now(), now())
				returning id
				""", Long.class, receiptId, quantityValue, unitCostValue, unitCostValue, quantityValue);
		return new OutsourcingReceiptFact(orderId, documentNo + "-OO", receiptId, documentNo + "-OR",
				receiptLineId);
	}

	private long insertOutsourcingPurchaseInvoice(Stage033BaseFixture base, OutsourcingReceiptFact receipt,
			String invoiceNo, LocalDate invoiceDate, String amount) {
		long invoiceId = this.jdbcTemplate.queryForObject("""
				insert into fin_purchase_invoice (
					invoice_no, supplier_id, settlement_kind, ownership_type, project_id, source_type,
					source_id, source_no, invoice_date, due_date, supplier_invoice_no, invoice_type,
					currency, match_status, tax_excluded_amount, tax_amount, tax_included_amount, status,
					party_snapshot, source_snapshot, remark, created_by, created_at, updated_by, updated_at,
					matched_by, matched_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'OUTSOURCING', 'PROJECT', ?, 'OUTSOURCING_RECEIPT', ?, ?, ?, ?, ?,
					'NONE', 'CNY', 'NOT_APPLICABLE', ?::numeric, 0, ?::numeric, 'CONFIRMED',
					'{}'::jsonb, '{}'::jsonb, '033 外协发票正例',
					'test', now(), 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, invoiceNo, base.supplierId(), base.projectId(), receipt.receiptId(),
				receipt.receiptNo(), invoiceDate, invoiceDate.plusDays(10), invoiceNo + "-SUP", amount, amount);
		this.jdbcTemplate.update("""
				insert into fin_purchase_invoice_line (
					purchase_invoice_id, line_no, source_line_id, outsourcing_order_id, material_id, unit_id,
					quantity, tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_amount, tax_included_amount, match_status, source_snapshot, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 1.000000, 0, ?::numeric, ?::numeric, ?::numeric, 0,
					?::numeric, 'NOT_APPLICABLE', '{}'::jsonb, now(), now())
				""", invoiceId, receipt.receiptLineId(), receipt.orderId(), base.finishedMaterialId(),
				base.unitId(), amount, amount, amount, amount);
		return invoiceId;
	}

	private long insertStockBalance(Stage033BaseFixture base, long materialId, String ownershipType, Long projectId,
			String quantity, String valuationState, String inventoryAmount, String averageUnitCost) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quality_status, quantity_on_hand, locked_quantity,
					ownership_type, project_id, valuation_state, inventory_amount, average_unit_cost,
					created_at, updated_at
				)
				values (?, ?, ?, 'QUALIFIED', ?::numeric, 0, ?, ?, ?, ?::numeric, ?::numeric, now(), now())
				returning id
				""", Long.class, base.warehouseId(), materialId, base.unitId(), quantity, ownershipType,
				projectId, valuationState, inventoryAmount, averageUnitCost);
	}

	private long insertStockMovementWithValue(Stage033BaseFixture base, long materialId, String ownershipType,
			Long projectId, LocalDate businessDate, String quantity, String valuationState, String unitCost,
			String inventoryAmount, long sourceId, String movementNo) {
		long sourceLineId = SEQUENCE.incrementAndGet();
		long movementId = this.jdbcTemplate.queryForObject("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					quality_status, before_quantity, after_quantity, source_type, source_id, source_line_id,
					business_date, reason, remark, operator_name, occurred_at, ownership_type, project_id,
					valuation_state, valuation_method, unit_cost, inventory_amount
				)
				values (?, 'ADJUSTMENT_INCREASE', 'IN', ?, ?, ?, ?::numeric, 'QUALIFIED', 0,
					?::numeric, 'INVENTORY_BALANCE', ?, ?, ?, '033 库存资金正例', '033 库存资金正例',
					'test', now(), ?, ?, ?, ?, ?::numeric, ?::numeric)
				returning id
				""", Long.class, movementNo, base.warehouseId(), materialId, base.unitId(), quantity, quantity,
				sourceId, sourceLineId, businessDate, ownershipType, projectId, valuationState,
				unitCost == null ? "NOT_VALUED" : "MANUAL_UNIT_COST", unitCost, inventoryAmount);
		long valueMovementId = this.jdbcTemplate.queryForObject("""
				insert into inv_value_movement (
					stock_movement_id, movement_no, movement_type, direction, warehouse_id, material_id,
					ownership_type, project_id, quantity, unit_cost, inventory_amount, valuation_method,
					valuation_state, source_type, source_id, source_line_id, business_date, created_at
				)
				values (?, ?, 'ADJUSTMENT_INCREASE', 'IN', ?, ?, ?, ?, ?::numeric, ?::numeric,
					?::numeric, ?, ?, 'INVENTORY_BALANCE', ?, ?, ?, now())
				returning id
				""", Long.class, movementId, movementNo, base.warehouseId(), materialId, ownershipType,
				projectId, quantity, unitCost, inventoryAmount,
				unitCost == null ? "NOT_VALUED" : "MANUAL_UNIT_COST", valuationState, sourceId, sourceLineId,
				businessDate);
		this.jdbcTemplate.update("""
				update inv_stock_movement
				set value_movement_id = ?
				where id = ?
				""", valueMovementId, movementId);
		return movementId;
	}

	private void insertInventoryCapitalSnapshot(String marker, String snapshotMarker) {
		long periodId = ensureBusinessPeriod("2026-06", LocalDate.of(2026, 6, 1),
				LocalDate.of(2026, 6, 30));
		int revisionNo = this.jdbcTemplate.queryForObject("""
				select coalesce(max(revision_no), 0) + 1
				from biz_period_close_run
				where period_id = ?
				""", Integer.class, periodId);
		this.jdbcTemplate.update("""
				update biz_period_close_run
				set status = 'REOPENED',
				    reopened_by = 'test',
				    reopened_at = now(),
				    reopen_reason = '033 库存资金快照夹具隔离重开旧关闭',
				    updated_by = 'test',
				    updated_at = now()
				where period_id = ?
				and status = 'CLOSED'
				""", periodId);
		long runId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_close_run (
					period_id, revision_no, status, source_fingerprint, inventory_fingerprint,
					wip_fingerprint, project_cost_fingerprint, report_fingerprint, blocking_count,
					warning_count, closed_by, closed_at, close_reason, created_by, created_at,
					updated_by, updated_at
				)
				values (?, ?, 'CLOSED', ?, ?, ?, ?, ?, 0, 0, 'test', now(), '033 库存资金快照正例',
					'test', now(), 'test', now())
				returning id
				""", Long.class, periodId, revisionNo, fingerprint(marker + "-source"), fingerprint(marker + "-inventory"),
				fingerprint(marker + "-wip"), fingerprint(marker + "-project-cost"),
				fingerprint(marker + "-report"));
		long checkRunId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_close_check_run (
					run_id, period_id, revision_no, status, source_fingerprint, inventory_fingerprint,
					wip_fingerprint, project_cost_fingerprint, report_fingerprint, blocking_count,
					warning_count, started_by, started_at, completed_at
				)
				values (?, ?, ?, 'READY', ?, ?, ?, ?, ?, 0, 0, 'test', now(), now())
				returning id
				""", Long.class, runId, periodId, revisionNo, fingerprint(marker + "-source"),
				fingerprint(marker + "-inventory"), fingerprint(marker + "-wip"),
				fingerprint(marker + "-project-cost"), fingerprint(marker + "-report"));
		long snapshotId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_snapshot (
					run_id, period_id, revision_no, source_check_run_id, source_fingerprint,
					inventory_fingerprint, wip_fingerprint, project_cost_fingerprint, report_fingerprint,
					generated_by, generated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'test', now())
				returning id
				""", Long.class, runId, periodId, revisionNo, checkRunId, fingerprint(marker + "-source"),
				fingerprint(marker + "-inventory"), fingerprint(marker + "-wip"),
				fingerprint(marker + "-project-cost"), fingerprint(marker + "-report"));
		this.jdbcTemplate.update("""
				update biz_period_close_run
				set latest_check_run_id = ?, snapshot_id = ?, updated_at = now()
				where id = ?
				""", checkRunId, snapshotId, runId);
		this.jdbcTemplate.update("""
				insert into biz_period_report_snapshot (
					snapshot_id, report_code, schema_version, result_json, source_count, fingerprint, created_at
				)
				values (?, 'INVENTORY_CAPITAL', 1, ?::jsonb, 3, ?, now())
				""", snapshotId, """
				{
				  "summary": {
				    "quantity": "18.000",
				    "amount": "210.00",
				    "snapshotAmount": "210.00",
				    "differenceAmount": "115.00",
				    "riskQuantity": "3.000",
				    "sourceCount": 3,
				    "analysisMode": "BUSINESS_SNAPSHOT",
				    "freshnessStatus": "SNAPSHOT"
				  },
				  "items": [
				    {
				      "snapshotMarker": "%s",
				      "quantity": "18.000",
				      "amount": "210.00",
				      "analysisMode": "BUSINESS_SNAPSHOT"
				    }
				  ],
				  "page": 1,
				  "pageSize": 1,
				  "total": 1,
				  "totalPages": 1
				}
				""".formatted(snapshotMarker), fingerprint(marker + "-snapshot"));
	}

	private long ensureBusinessPeriod(String periodCode, LocalDate startDate, LocalDate endDate) {
		List<Long> existing = this.jdbcTemplate.queryForList(
				"select id from biz_business_period where period_code = ?", Long.class, periodCode);
		if (!existing.isEmpty()) {
			return existing.get(0);
		}
		return this.jdbcTemplate.queryForObject("""
				insert into biz_business_period (
					period_code, period_name, start_date, end_date, status, locked_by, locked_at,
					lock_reason, created_at, updated_at
				)
				values (?, ?, ?, ?, 'LOCKED', 'test', now(), '033 正例业务快照', now(), now())
				returning id
				""", Long.class, periodCode, periodCode + " 033 正例期间", startDate, endDate);
	}

	private ReceivableFact seedReceivableBucket(Stage033BaseFixture base, String documentNo,
			LocalDate businessDate, LocalDate dueDate, String totalAmount, String receivedAmount,
			String unreceivedAmount) {
		SalesShipmentFact shipment = insertSalesShipment(base, base.mainContractId(), base.mainContractNo(),
				documentNo + "-SHIP", businessDate, "1.000000", totalAmount);
		return insertReceivable(shipment, documentNo, businessDate, dueDate, totalAmount, receivedAmount,
				unreceivedAmount, "0.00".equals(receivedAmount) ? "CONFIRMED" : "PARTIALLY_RECEIVED");
	}

	private Map<String, String> upstreamFactsSummary() {
		return Map.of("projectcost", rowSummary("""
				select id, status, updated_at
				from prj_cost_calculation
				"""), "periodclose", rowSummary("""
				select id, status, updated_at
				from biz_period_close_run
				"""), "gl", rowSummary("""
				select id, status, updated_at
				from gl_voucher
				"""), "financialclose", rowSummary("""
				select id, status, updated_at
				from fin_close_run
				"""));
	}

	private String rowSummary(String sql) {
		return this.jdbcTemplate.queryForObject("""
				select md5(coalesce(string_agg(row_to_json(t)::text, '|' order by row_to_json(t)::text), ''))
				from (%s) t
				""".formatted(sql), String.class);
	}

	private void assertReportPage(JsonNode page) {
		assertThat(page.get("summary")).as("固定报表必须返回 summary").isNotNull();
		assertThat(page.get("items")).as("固定报表必须返回 items").isNotNull();
		assertThat(page.get("items").isArray()).isTrue();
		assertThat(page.get("page").asInt()).isGreaterThanOrEqualTo(1);
		assertThat(page.get("pageSize").asInt()).isIn(10, 20, 50, 100);
		assertThat(page.get("total").asInt()).isGreaterThanOrEqualTo(0);
		assertThat(page.get("totalPages").asInt()).isGreaterThanOrEqualTo(0);
	}

	private void assertReportPage(JsonNode page, int expectedPageSize) {
		assertThat(page.get("summary")).as("固定报表必须返回 summary").isNotNull();
		assertThat(page.get("items")).as("固定报表必须返回 items").isNotNull();
		assertThat(page.get("items").isArray()).isTrue();
		assertThat(page.get("page").asInt()).isGreaterThanOrEqualTo(1);
		assertThat(page.get("pageSize").asInt()).isEqualTo(expectedPageSize);
		assertThat(page.get("total").asInt()).isGreaterThanOrEqualTo(0);
		assertThat(page.get("totalPages").asInt()).isGreaterThanOrEqualTo(0);
	}

	private void assertSummaryCountAtLeast(JsonNode page, int expectedCount) {
		JsonNode summary = page.get("summary");
		List<String> candidateFields = List.of("recordCount", "projectCount", "sourceCount");
		boolean matched = candidateFields.stream()
			.anyMatch((field) -> summary.has(field) && summary.get(field).asInt() >= expectedCount);
		assertThat(matched)
			.as("summary 必须统计完整筛选集，不能只统计当前页；期望至少 %s 条".formatted(expectedCount))
			.isTrue();
	}

	private void assertPagedSummarySeesFullSet(SoftAssertions softly, String label, JsonNode page,
			int expectedTotal, List<String> expectedSummaryFragments) {
		softly.assertThat(page.get("pageSize").asInt()).as(label + "分页必须按当前页大小返回").isEqualTo(1);
		softly.assertThat(page.get("items").size()).as(label + "当前页最多只能返回一行").isLessThanOrEqualTo(1);
		softly.assertThat(page.get("total").asInt()).as(label + "必须返回完整筛选集总数")
			.isGreaterThanOrEqualTo(expectedTotal);
		assertSummaryCountAtLeast(softly, label, page, expectedTotal);
		String summaryText = page.get("summary").toString();
		for (String fragment : expectedSummaryFragments) {
			softly.assertThat(summaryText).as(label + " summary 必须包含完整筛选集汇总：" + fragment)
				.contains(fragment);
		}
	}

	private void assertSummaryCountAtLeast(SoftAssertions softly, String label, JsonNode page, int expectedCount) {
		JsonNode summary = page.get("summary");
		List<String> candidateFields = List.of("recordCount", "contractCount", "projectCount",
				"materialCount", "partyCount", "sourceCount");
		boolean matched = candidateFields.stream()
			.anyMatch((field) -> summary.has(field) && summary.get(field).asInt() >= expectedCount);
		softly.assertThat(matched).as(label + " summary 必须统计完整筛选集，不能只统计当前页").isTrue();
	}

	private void assertReportTextContains(SoftAssertions softly, String label, JsonNode report,
			List<String> fragments) {
		String text = report.toString();
		for (String fragment : fragments) {
			softly.assertThat(text).as(label + "响应必须包含真实业务事实：" + fragment).contains(fragment);
		}
	}

	private void assertReportTextContainsAnyOf(SoftAssertions softly, String label, JsonNode report,
			String... candidates) {
		softly.assertThat(report.toString()).as(label + "必须存在固定业务状态或桶标识")
			.containsAnyOf(candidates);
	}

	private void assertMinimalSourceVisible(SoftAssertions softly, String label, String path,
			List<String> permissionCodes, List<String> expectedFragments) throws Exception {
		JsonNode report = data(get(createUserAndLogin("033-min-source-", "033_MIN_SOURCE_", permissionCodes), path));
		String text = report.toString();
		for (String fragment : expectedFragments) {
			softly.assertThat(text).as(label + "最小来源权限必须能看到真实来源事实：" + fragment)
				.contains(fragment);
		}
	}

	private void assertSourceRestrictedListAndTrace(SoftAssertions softly, String label,
			AuthenticatedSession restrictedSession, JsonNode adminReport, String reportPath, String tracePath,
			String periodCode, String itemNeedle, List<String> forbiddenFragments) throws Exception {
		JsonNode restrictedReport = data(get(restrictedSession, reportPath));
		JsonNode restrictedItem = firstItemContaining(restrictedReport, itemNeedle);
		if (restrictedItem == null) {
			softly.fail(label + "缺一来源权限仍应返回稳定受限行，当前无法定位业务行：" + restrictedReport);
		}
		else {
			softly.assertThat(intField(restrictedItem, "sourceCount")).as(label + "缺一来源权限列表 sourceCount 不得泄露")
				.isZero();
			softly.assertThat(textOrNull(restrictedItem, "traceKey")).as(label + "缺一来源权限列表不得返回可调用 traceKey")
				.isNullOrEmpty();
		}
		String restrictedText = restrictedReport.toString();
		for (String fragment : forbiddenFragments) {
			softly.assertThat(restrictedText).as(label + "缺一来源权限列表不得泄露来源单号：" + fragment)
				.doesNotContain(fragment);
		}
		JsonNode adminItem = firstItemContaining(adminReport, itemNeedle);
		if (adminItem == null) {
			softly.fail(label + "管理员列表未返回用于低权限追溯验证的业务行：" + adminReport);
			return;
		}
		String traceKey = textOrNull(adminItem, "traceKey");
		if (traceKey == null || traceKey.isBlank()) {
			softly.fail(label + "管理员列表行必须返回 traceKey 供缺一权限追溯验证：" + adminItem);
			return;
		}
		assertRestrictedTraceEmptyOrUnavailable(softly, label, restrictedSession, tracePath, traceKey, periodCode);
	}

	private void assertRestrictedTraceEmptyOrUnavailable(SoftAssertions softly, String label,
			AuthenticatedSession restrictedSession, String tracePath, String traceKey, String periodCode) throws Exception {
		ResponseEntity<String> response = get(restrictedSession, tracePath + "?traceKey=" + encoded(traceKey)
				+ "&periodCode=" + periodCode + "&analysisMode=LIVE&page=1&pageSize=20");
		if (response.getStatusCode() == HttpStatus.OK && "OK".equals(code(response))) {
			JsonNode trace = data(response);
			softly.assertThat(trace.get("items")).as(label + "缺一来源权限 trace 必须返回空页").isEmpty();
			softly.assertThat(trace.get("total").asInt()).as(label + "缺一来源权限 trace total 不得泄露")
				.isZero();
			softly.assertThat(trace.get("totalPages").asInt()).as(label + "缺一来源权限 trace totalPages 不得泄露")
				.isZero();
			return;
		}
		softly.assertThat(response.getStatusCode()).as(label + "缺一来源权限 trace 必须是稳定不可用状态")
			.isIn(HttpStatus.FORBIDDEN, HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT);
		String code = code(response);
		softly.assertThat(code).as(label + "缺一来源权限 trace 不得返回内部错误或真实来源分页")
			.isIn("AUTH_FORBIDDEN", "REPORT_TRACE_KEY_INVALID", "REPORT_SOURCE_INCOMPLETE",
					"REPORT_BASIS_INVALID");
	}

	private void assertRestrictedTraceEmptyPage(SoftAssertions softly, String label,
			AuthenticatedSession restrictedSession, String tracePath, String traceKey, String periodCode,
			String analysisMode) throws Exception {
		if (traceKey == null || traceKey.isBlank()) {
			softly.fail(label + "完整来源权限列表必须先返回 traceKey，才能验证低权限 trace 空分页");
			return;
		}
		ResponseEntity<String> response = get(restrictedSession, tracePath + "?traceKey=" + encoded(traceKey)
				+ "&periodCode=" + periodCode + "&analysisMode=" + analysisMode + "&page=1&pageSize=20");
		softly.assertThat(response.getStatusCode()).as(label + "缺一来源权限 trace 必须返回稳定空分页")
			.isEqualTo(HttpStatus.OK);
		if (response.getStatusCode() != HttpStatus.OK || !"OK".equals(code(response))) {
			softly.fail(label + "缺一来源权限 trace 必须返回 OK 空分页，当前响应：" + response.getBody());
			return;
		}
		JsonNode trace = data(response);
		softly.assertThat(trace.get("items")).as(label + "缺一来源权限 trace items 必须为空").isEmpty();
		softly.assertThat(trace.get("total").asInt()).as(label + "缺一来源权限 trace total 不得泄露").isZero();
		softly.assertThat(trace.get("totalPages").asInt()).as(label + "缺一来源权限 trace totalPages 不得泄露")
			.isZero();
	}

	private void assertTraceFromItemContaining(SoftAssertions softly, AuthenticatedSession session, JsonNode report,
			String tracePath, String label, String itemNeedle, List<TraceExpectation> expectations)
			throws Exception {
		assertTraceFromItemContaining(softly, session, report, tracePath, label, itemNeedle, expectations,
				"2026-06");
	}

	private void assertTraceFromItemContaining(SoftAssertions softly, AuthenticatedSession session, JsonNode report,
			String tracePath, String label, String itemNeedle, List<TraceExpectation> expectations,
			String periodCode) throws Exception {
		JsonNode item = firstItemContaining(report, itemNeedle);
		if (item == null) {
			softly.fail(label + "列表未返回包含 " + itemNeedle + " 的行，无法验证 traceKey 和真实追溯。响应："
					+ report);
			return;
		}
		JsonNode traceKeyNode = item.get("traceKey");
		if (traceKeyNode == null || traceKeyNode.isNull() || traceKeyNode.asText().isBlank()) {
			softly.fail(label + "列表行必须返回可用 traceKey。行：" + item);
			return;
		}
		JsonNode trace = traceDataAtPeriod(softly, session, tracePath, traceKeyNode.asText(), label, periodCode);
		if (trace == null) {
			return;
		}
		softly.assertThat(trace.get("items").size()).as(label + " trace 必须返回真实来源行").isGreaterThan(0);
		for (TraceExpectation expectation : expectations) {
			JsonNode source = firstTraceSource(trace, expectation.sourceType(), expectation.sourceId());
			if (source == null) {
				softly.fail(label + " trace 缺少真实来源 " + expectation.sourceType() + "#"
						+ expectation.sourceId() + "。trace：" + trace);
			}
			else {
				softly.assertThat(booleanField(source, "canViewResource")).as(label + " trace 管理员应可查看来源")
					.isTrue();
				softly.assertThat(booleanField(source, "restricted")).as(label + " trace 管理员来源不得被脱敏")
					.isFalse();
				softly.assertThat(source.get("resourceRouteName")).as(label + " trace 必须携带 returnTo 可用的来源路由")
					.isNotNull();
			}
		}
	}

	private void assertTraceMaskedWhenSourcePermissionsMissing(SoftAssertions softly, AuthenticatedSession session,
			JsonNode report, String tracePath, String label, String itemNeedle) throws Exception {
		JsonNode item = firstItemContaining(report, itemNeedle);
		if (item == null) {
			softly.fail(label + "列表未返回包含 " + itemNeedle + " 的行，无法验证追溯权限脱敏。响应：" + report);
			return;
		}
		JsonNode traceKeyNode = item.get("traceKey");
		if (traceKeyNode == null || traceKeyNode.isNull() || traceKeyNode.asText().isBlank()) {
			softly.fail(label + "列表行必须返回可用于权限脱敏验证的 traceKey。行：" + item);
			return;
		}
		JsonNode trace = traceData(softly, session, tracePath, traceKeyNode.asText(), label + "权限脱敏");
		if (trace == null) {
			return;
		}
		if (trace.get("items").isEmpty()) {
			softly.assertThat(trace.get("total").asInt()).as(label + "缺少来源权限时 trace 空页不得泄露总数")
				.isZero();
			softly.assertThat(trace.get("totalPages").asInt()).as(label + "缺少来源权限时 trace 空页不得泄露页数")
				.isZero();
			return;
		}
		softly.assertThat(hasMaskedSource(trace)).as(label + "缺少来源权限时 trace 必须失败关闭并脱敏来源主键")
			.isTrue();
	}

	private void assertEndpointForbidden(SoftAssertions softly, AuthenticatedSession session, String path,
			String label) throws Exception {
		ResponseEntity<String> response = get(session, path);
		softly.assertThat(response.getStatusCode()).as(label + "缺少报表权限必须 403，响应：" + response.getBody())
			.isEqualTo(HttpStatus.FORBIDDEN);
		if (response.getStatusCode() == HttpStatus.FORBIDDEN) {
			softly.assertThat(code(response)).as(label + "缺少报表权限必须返回稳定错误码")
				.isEqualTo("AUTH_FORBIDDEN");
		}
	}

	private JsonNode traceData(SoftAssertions softly, AuthenticatedSession session, String tracePath,
			String traceKey, String label) throws Exception {
		return traceDataAtPeriod(softly, session, tracePath, traceKey, label, "2026-06");
	}

	private JsonNode traceDataAtPeriod(SoftAssertions softly, AuthenticatedSession session, String tracePath,
			String traceKey, String label, String periodCode) throws Exception {
		ResponseEntity<String> response = get(session, tracePath + "?traceKey=" + encoded(traceKey)
				+ "&periodCode=" + periodCode + "&analysisMode=LIVE&page=1&pageSize=20");
		if (response.getStatusCode() != HttpStatus.OK) {
			softly.fail(label + " traceKey 必须可用，当前响应 " + response.getStatusCode() + "："
					+ response.getBody());
			return null;
		}
		JsonNode body = this.objectMapper.readTree(response.getBody());
		if (!"OK".equals(body.get("code").asText())) {
			softly.fail(label + " trace 必须返回 OK，当前响应：" + response.getBody());
			return null;
		}
		JsonNode data = body.get("data");
		if (data == null || data.get("items") == null || !data.get("items").isArray()) {
			softly.fail(label + " trace 必须继承 014 PageResponse 契约，当前响应：" + response.getBody());
			return null;
		}
		return data;
	}

	private JsonNode traceFromFirstItemContaining(SoftAssertions softly, AuthenticatedSession session, JsonNode report,
			String tracePath, String label, String itemNeedle, String periodCode) throws Exception {
		JsonNode item = firstItemContaining(report, itemNeedle);
		if (item == null) {
			softly.fail(label + "列表未返回包含 " + itemNeedle + " 的行，无法验证 traceKey。响应：" + report);
			return null;
		}
		JsonNode traceKeyNode = item.get("traceKey");
		if (traceKeyNode == null || traceKeyNode.isNull() || traceKeyNode.asText().isBlank()) {
			softly.fail(label + "列表行必须返回可用 traceKey。行：" + item);
			return null;
		}
		return traceDataAtPeriod(softly, session, tracePath, traceKeyNode.asText(), label, periodCode);
	}

	private void assertTraceContainsAnySourceId(SoftAssertions softly, JsonNode trace, String sourceType,
			List<Long> sourceIds, String label) {
		if (trace == null) {
			return;
		}
		JsonNode items = trace.get("items");
		softly.assertThat(items.size()).as(label + " trace 必须返回真实来源行").isGreaterThan(0);
		boolean matched = false;
		for (JsonNode item : items) {
			JsonNode type = item.get("sourceType");
			JsonNode id = item.get("sourceId");
			if (type != null && id != null && sourceType.equals(type.asText())
					&& sourceIds.contains(id.longValue())) {
				matched = true;
				break;
			}
		}
		softly.assertThat(matched).as(label + " trace 必须返回真实来源类型与主键：" + sourceType + sourceIds)
			.isTrue();
	}

	private void assertEveryItemContains(JsonNode report, String fragment) {
		for (JsonNode item : report.get("items")) {
			assertThat(item.toString()).as("当前页 item 必须按请求筛选：" + fragment).contains(fragment);
		}
	}

	private List<Long> ledgerEntryIdsForPeriod(String periodCode) {
		return this.jdbcTemplate.queryForList("""
				select e.id
				from gl_ledger_entry e
				join gl_accounting_period p on p.id = e.period_id
				where p.period_code = ?
				order by e.id
				""", Long.class, periodCode);
	}

	private JsonNode firstItemContaining(JsonNode report, String needle) {
		JsonNode items = report.get("items");
		if (items == null || !items.isArray()) {
			return null;
		}
		for (JsonNode item : items) {
			if (item.toString().contains(needle)) {
				return item;
			}
		}
		return null;
	}

	private JsonNode firstTraceSource(JsonNode trace, String sourceType, long sourceId) {
		JsonNode items = trace.get("items");
		if (items == null || !items.isArray()) {
			return null;
		}
		for (JsonNode item : items) {
			JsonNode type = item.get("sourceType");
			JsonNode id = item.get("sourceId");
			if (type != null && id != null && sourceType.equals(type.asText()) && id.longValue() == sourceId) {
				return item;
			}
		}
		return null;
	}

	private JsonNode firstTraceSourceBySourceNo(JsonNode trace, String sourceType, String sourceNo) {
		JsonNode items = trace.get("items");
		if (items == null || !items.isArray()) {
			return null;
		}
		for (JsonNode item : items) {
			JsonNode type = item.get("sourceType");
			JsonNode no = item.get("sourceNo");
			if (type != null && no != null && sourceType.equals(type.asText()) && sourceNo.equals(no.asText())) {
				return item;
			}
		}
		return null;
	}

	private boolean hasMaskedSource(JsonNode trace) {
		JsonNode items = trace.get("items");
		if (items == null || !items.isArray()) {
			return false;
		}
		for (JsonNode item : items) {
			JsonNode sourceId = item.get("sourceId");
			if (!booleanField(item, "canViewResource") && booleanField(item, "restricted")
					&& (sourceId == null || sourceId.isNull())) {
				return true;
			}
		}
		return false;
	}

	private boolean booleanField(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value != null && !value.isNull() && value.booleanValue();
	}

	private boolean hasNonNullField(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value != null && !value.isNull();
	}

	private String textOrNull(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}

	private int intField(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value == null || value.isNull() ? 0 : value.intValue();
	}

	private String encoded(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private void assertControlledSnapshotError(ResponseEntity<String> response, String... expectedCodes)
			throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody())
			.isIn(HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT);
		assertThat(List.of(expectedCodes)).contains(code(response));
	}

	private void assertForbiddenFinancialStatementTerms(String body) {
		assertThat(body).doesNotContain("资产负债表", "利润表", "现金流量表", "税控", "已申报", "报送成功");
	}

	private AuthenticatedSession createUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "033 报表验收角色" + suffix, "033 报表验收角色");
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
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf),
				String.class);
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrf);
	}

	private CsrfSession csrfSession() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		try {
			JsonNode data = data(response);
			return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private ResponseEntity<String> get(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null),
				String.class);
	}

	private HttpEntity<Object> entity(Object body, String cookie, CsrfSession csrf) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		if (csrf != null) {
			headers.add(csrf.headerName(), csrf.token());
		}
		return new HttpEntity<>(body, headers);
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		assertOk(response);
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertOk(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		assertThat(code(response)).isEqualTo("OK");
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
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

	private String fingerprint(String value) {
		return "%064d".formatted(Math.abs(value.hashCode()));
	}

	private boolean exists(String sql, Object... args) {
		Boolean value = this.jdbcTemplate.queryForObject("select exists (" + sql + ")", Boolean.class, args);
		return Boolean.TRUE.equals(value);
	}

	private long availableFileObjectCount() {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_file_object
				where status = 'AVAILABLE'
				""", Long.class);
	}

	private void ensureAvailableFileObjects() {
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		this.jdbcTemplate.update("""
				insert into platform_file_object (
					bucket, object_key, original_filename, content_type, size_bytes, sha256, etag, file_usage,
					status, created_by_user_id, created_by_username
				)
				select 'qherp-test-stage033', 'stage033/object-' || gs || '.txt',
					'stage033-object-' || gs || '.txt', 'text/plain', 32 + gs,
					lpad(to_hex(gs), 64, '0'), 'stage033-etag-' || gs, 'ACCEPTANCE',
					'AVAILABLE', ?, 'admin'
				from generate_series(1, 8) gs
				on conflict (bucket, object_key) do nothing
				""", adminUserId);
	}

	private boolean validatorObjectRuleDoesNotFixEighteen() throws Exception {
		java.nio.file.Path workingDirectory = java.nio.file.Path.of(System.getProperty("user.dir"));
		java.nio.file.Path sqlPath = workingDirectory.resolve("../../tools/demo-data/sql/validate-demo-data.sql")
			.normalize();
		java.nio.file.Path validatorPath = workingDirectory.resolve("../../tools/demo-data/validate-demo-data.ps1")
			.normalize();
		if (!java.nio.file.Files.exists(sqlPath)) {
			sqlPath = workingDirectory.resolve("tools/demo-data/sql/validate-demo-data.sql").normalize();
		}
		if (!java.nio.file.Files.exists(validatorPath)) {
			validatorPath = workingDirectory.resolve("tools/demo-data/validate-demo-data.ps1").normalize();
		}
		String sql = java.nio.file.Files.readString(sqlPath);
		String validator = java.nio.file.Files.readString(validatorPath);
		return sql.contains("FILE_OBJECTS_AVAILABLE_MIN_8")
				&& validator.contains("MINIO_BUCKET_OBJECTS_MIN_8")
				&& validator.contains("bucket == database available and >= 8")
				&& !sql.contains("count(*) = 18")
				&& !validator.contains("MINIO_BUCKET_OBJECTS_18");
	}

	private record ReportEndpoint(String path, String permission) {
	}

	private record OperatingFinanceFixture(long firstProjectId, String firstProjectNo, long lastProjectId,
			String lastProjectNo) {
	}

	private record SnapshotProbeFixture(String periodCode, String marker, String targetKeyword) {
	}

	private record ContractCollectionSnapshotProbeFixture(String periodCode, long projectId, String projectNo,
			String contractNo, String customerName, String invoiceNo, String receiptNo, String receivableNo) {
	}

	private record SnapshotProjectDetailFixture(String periodCode, long projectId, String projectNo) {
	}

	private record VoucherLineSeed(String accountCode, String direction, String amount,
			String projectAuxiliaryCode) {
	}

	private record AccountSnapshot(long id, String code, String name, String category,
			String balanceDirection) {
	}

	private record ChildAccountParent(long id, long ledgerId, String category, String balanceDirection,
			int levelNo) {
	}

	private record AuxDimension(long id, String name) {
	}

	private record TraceExpectation(String sourceType, long sourceId) {
	}

	private record Stage033PositiveFixture(String marker, ContractCollectionPositiveFacts contract,
			ProcurementVariancePositiveFacts procurement, InventoryCapitalPositiveFacts inventory,
			ReceivablePayablePositiveFacts receivablePayable) {
	}

	private record Stage033BaseFixture(long unitId, long warehouseId, long customerId, long supplierId,
			long categoryId, long finishedMaterialId, long rawMaterialId, long projectId, String projectNo,
			long mainContractId, String mainContractNo, String marker) {
	}

	private record ContractCollectionPositiveFacts(String marker, long mainContractId, String mainContractNo,
			long supplementContractId, String supplementContractNo, long salesInvoiceId, String salesInvoiceNo,
			long receivableId, String receivableNo, long receiptId, String receiptNo, long receiptAllocationId,
			long allocationId) {
	}

	private record ProcurementVariancePositiveFacts(String marker, long projectPurchaseOrderId,
			String projectPurchaseOrderNo, long projectPurchaseReceiptId, String projectPurchaseReceiptNo,
			long purchaseReturnId, long purchaseInvoiceId, String purchaseInvoiceNo, long paymentId,
			String paymentNo, long publicPurchaseOrderId, String publicPurchaseOrderNo, long outsourcingOrderId,
			String outsourcingOrderNo, long outsourcingReceiptId, String outsourcingReceiptNo,
			long outsourcingInvoiceId) {
	}

	private record InventoryCapitalPositiveFacts(String marker, String snapshotMarker, long projectId,
			String projectNo, long projectValuedBalanceId, long projectValuedMovementId,
			long publicValuedBalanceId, long publicValuedMovementId, long projectUnvaluedBalanceId,
			long projectUnvaluedMovementId) {
	}

	private record ReceivablePayablePositiveFacts(String marker, String notDueReceivableNo,
			String days1To30ReceivableNo, String days31To60ReceivableNo, String days61To90ReceivableNo,
			String daysOver90ReceivableNo, long allocatedReceivableId, long receiptId, long allocationId,
			long advanceReceiptId, long payableId, String payableNo, long purchaseReceiptId, long paymentId,
			long prepaymentId) {
	}

	private record SalesShipmentFact(long shipmentId, String shipmentNo, long shipmentLineId, long orderId,
			long orderLineId) {
	}

	private record ReceivableFact(long receivableId, String receivableNo) {
	}

	private record PurchaseReceiptFact(long orderId, String orderNo, long orderLineId, long receiptId,
			String receiptNo, long receiptLineId, long supplierId, long materialId, LocalDate businessDate) {
	}

	private record PayableFact(long payableId, String payableNo) {
	}

	private record OutsourcingReceiptFact(long orderId, String orderNo, long receiptId, String receiptNo,
			long receiptLineId) {
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}

}
