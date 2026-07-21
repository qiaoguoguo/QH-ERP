package com.qherp.api.system.reporting;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=reporting-stage-033")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportingStage033ControllerTests extends PostgresIntegrationTest {

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
	void 三十三阶段报表必须沿用reports前缀并提供稳定空态骨架() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String period = "?dateFrom=2055-01-01&dateTo=2055-01-31&keyword=__033_EMPTY__";

		JsonNode overview = data(get("/api/admin/reports/operating-finance-overview" + period, admin));
		assertThat(overview.get("analysisMode").asText()).isEqualTo("LIVE");
		assertThat(overview.get("businessPeriodStatus").asText()).isEqualTo("OPEN");
		assertThat(overview.get("accountingPeriodStatus").asText()).isEqualTo("UNAVAILABLE");
		assertThat(overview.get("financialCloseStatus").asText()).isEqualTo("OPEN");
		assertThat(overview.get("finalityStatus").asText()).isEqualTo("UNAVAILABLE");
		assertThat(recursiveValues(overview, "routePath")).contains("/reports/project-profit",
				"/reports/financial-summary");

		for (String path : List.of("/api/admin/reports/project-profit",
				"/api/admin/reports/contract-collections", "/api/admin/reports/procurement-variances",
				"/api/admin/reports/inventory-capital", "/api/admin/reports/receivable-payable",
				"/api/admin/reports/operating-accounting-reconciliation")) {
			JsonNode page = data(get(path + period + "&page=1&pageSize=20", admin));
			assertThat(page.get("summary").get("analysisMode").asText()).isEqualTo("LIVE");
			assertThat(page.get("summary").get("freshnessStatus").asText()).isEqualTo("CURRENT");
			assertThat(page.get("items").isArray()).isTrue();
			assertThat(page.get("items")).hasSize(0);
			assertThat(page.get("total").longValue()).isZero();
		}

		JsonNode financialSummary = data(get("/api/admin/reports/financial-summary" + period, admin));
		assertThat(financialSummary.get("analysisMode").asText()).isEqualTo("LIVE");
		assertThat(financialSummary.get("legalReport").booleanValue()).isFalse();
		assertThat(financialSummary.get("disclaimer").asText()).contains("不是法定财务报表");
	}

	@Test
	void 会计对照和固定摘要请求业务快照必须稳定拒绝且不能冒充实时结果() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		assertError(get("""
				/api/admin/reports/operating-accounting-reconciliation?periodCode=2055-01&analysisMode=BUSINESS_SNAPSHOT&page=1&pageSize=20
				""".strip(), admin), HttpStatus.BAD_REQUEST, "REPORT_BASIS_INVALID");
		assertError(get("""
				/api/admin/reports/financial-summary?periodCode=2055-01&analysisMode=BUSINESS_SNAPSHOT
				""".strip(), admin), HttpStatus.BAD_REQUEST, "REPORT_BASIS_INVALID");
	}

	@Test
	void 业务快照读取必须按当前请求重新筛选分页并按当前账号脱敏() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String periodCode = insertProjectProfitBusinessSnapshot();

		JsonNode filtered = data(get("/api/admin/reports/project-profit?periodCode=" + periodCode
				+ "&analysisMode=BUSINESS_SNAPSHOT&keyword=KEEP&page=1&pageSize=1", admin));
		assertThat(filtered.get("summary").get("analysisMode").asText()).isEqualTo("BUSINESS_SNAPSHOT");
		assertThat(filtered.get("summary").get("freshnessStatus").asText()).isEqualTo("FROZEN");
		assertThat(filtered.get("summary").get("shipmentRevenue").asText()).isEqualTo("120.00");
		assertThat(filtered.get("items")).hasSize(1);
		assertThat(filtered.get("items").get(0).get("projectNo").asText()).contains("KEEP");
		assertThat(filtered.get("page").intValue()).isEqualTo(1);
		assertThat(filtered.get("pageSize").intValue()).isEqualTo(1);
		assertThat(filtered.get("total").longValue()).isEqualTo(1);

		JsonNode snapshotDetail = data(get("/api/admin/reports/project-profit/900001?periodCode=" + periodCode
				+ "&analysisMode=BUSINESS_SNAPSHOT", admin));
		assertThat(snapshotDetail.get("projectId").longValue()).isEqualTo(900001L);
		assertThat(snapshotDetail.get("freshnessStatus").asText()).isEqualTo("FROZEN");
		assertThat(snapshotDetail.get("managementBasis").get("shipmentRevenue").asText()).isEqualTo("120.00");
		assertThat(snapshotDetail.get("accountingBasis").get("accountingProfit").asText()).isEqualTo("70.00");

		AuthenticatedSession reportOnly = createUserAndLogin("033-snapshot-report-only-",
				List.of("report:project-profit:view"));
		JsonNode masked = data(get("/api/admin/reports/project-profit?periodCode=" + periodCode
				+ "&analysisMode=BUSINESS_SNAPSHOT&page=1&pageSize=20", reportOnly));
		assertThat(masked.get("summary").get("amountVisible").booleanValue()).isFalse();
		assertThat(masked.get("summary").get("shipmentRevenue").isNull()).isTrue();
		assertThat(masked.get("summary").get("accountingRevenue").isNull()).isTrue();
		assertThat(masked.get("summary").get("sourceCount").intValue()).isZero();
		assertThat(masked.get("items").get(0).get("shipmentRevenue").isNull()).isTrue();
		assertThat(masked.get("items").get(0).get("shipmentGrossMarginRate").isNull()).isTrue();
		assertThat(masked.get("items").get(0).get("sourceCount").intValue()).isZero();
		assertThat(masked.get("items").get(0).get("traceKey").isNull()).isTrue();
	}

	@Test
	void 项目利润必须并列029经营口径和031带PROJECT辅助的已记账会计口径() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProjectFixture fixture = createProjectProfitFixture("2055-02", false);

		JsonNode page = data(get("/api/admin/reports/project-profit?periodCode=2055-02&projectId="
				+ fixture.projectId() + "&page=1&pageSize=20", admin));

		assertThat(page.get("summary").get("analysisMode").asText()).isEqualTo("LIVE");
		assertThat(page.get("summary").get("finalityStatus").asText()).isEqualTo("PREVIEW");
		assertThat(page.get("summary").get("shipmentRevenue").asText()).isEqualTo("1000.00");
		assertThat(page.get("summary").get("projectCostTotal").asText()).isEqualTo("400.00");
		assertThat(page.get("summary").get("shipmentGrossMargin").asText()).isEqualTo("600.00");
		assertThat(page.get("summary").get("accountingRevenue").asText()).isEqualTo("800.00");
		assertThat(page.get("summary").get("accountingCost").asText()).isEqualTo("300.00");
		assertThat(page.get("summary").get("accountingProfit").asText()).isEqualTo("500.00");
		assertThat(page.get("summary").get("unassignedAccountingAmount").asText()).isEqualTo("111.00");
		assertThat(page.get("summary").get("reconciliationStatus").asText()).isEqualTo("DIFFERENT");

		JsonNode item = page.get("items").get(0);
		assertThat(item.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(item.get("projectNo").asText()).isEqualTo(fixture.projectNo());
		assertThat(item.get("shipmentGrossMarginRate").asText()).isEqualTo("60.00");
		assertThat(item.get("accountingRevenue").asText()).isEqualTo("800.00");
		assertThat(item.get("finalityStatus").asText()).isEqualTo("PREVIEW");

		JsonNode detail = data(get("/api/admin/reports/project-profit/" + fixture.projectId()
				+ "?periodCode=2055-02", admin));
		assertThat(detail.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(detail.get("managementBasis").get("shipmentRevenue").asText()).isEqualTo("1000.00");
		assertThat(detail.get("accountingBasis").get("accountingProfit").asText()).isEqualTo("500.00");
	}

	@Test
	void 项目利润没有已记账PROJECT会计事实时会计字段和差异状态必须不可用() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProjectFixture fixture = createProjectProfitFixture("2055-08", false, false);

		JsonNode page = data(get("/api/admin/reports/project-profit?periodCode=2055-08&projectId="
				+ fixture.projectId() + "&page=1&pageSize=20", admin));
		assertThat(page.get("summary").get("accountingRevenue").isNull()).isTrue();
		assertThat(page.get("summary").get("accountingCost").isNull()).isTrue();
		assertThat(page.get("summary").get("accountingProfit").isNull()).isTrue();
		assertThat(page.get("summary").get("difference").isNull()).isTrue();
		assertThat(page.get("summary").get("reconciliationStatus").asText()).isEqualTo("UNAVAILABLE");
		JsonNode item = page.get("items").get(0);
		assertThat(item.get("accountingRevenue").isNull()).isTrue();
		assertThat(item.get("accountingProfit").isNull()).isTrue();
		assertThat(item.get("differenceAmount").isNull()).isTrue();
		assertThat(item.get("reconciliationStatus").asText()).isEqualTo("UNAVAILABLE");

		JsonNode detail = data(get("/api/admin/reports/project-profit/" + fixture.projectId()
				+ "?periodCode=2055-08", admin));
		assertThat(detail.get("accountingBasis").get("accountingProfit").isNull()).isTrue();
		assertThat(detail.get("differenceAmount").isNull()).isTrue();
		assertThat(recursiveValues(detail.get("varianceReasons"), "reasonCode")).contains("NO_ACCOUNTING_FACT");
	}

	@Test
	void 三十三项目利润对照和固定摘要追溯必须返回真实来源且保持来源权限脱敏() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProjectFixture fixture = createProjectProfitFixture("2055-09", false);

		JsonNode profitPage = data(get("/api/admin/reports/project-profit?periodCode=2055-09&projectId="
				+ fixture.projectId() + "&page=1&pageSize=20", admin));
		String profitTraceKey = profitPage.get("items").get(0).get("traceKey").asText();
		JsonNode profitTraces = data(get("/api/admin/reports/project-profit/" + fixture.projectId()
				+ "/traces?periodCode=2055-09&traceKey=" + profitTraceKey + "&page=1&pageSize=20", admin));
		assertThat(recursiveValues(profitTraces.get("items"), "sourceType")).contains("PROJECT_COST_CALCULATION",
				"PROJECT_COST_SOURCE_LINE", "GL_LEDGER_ENTRY");
		assertThat(glLedgerTraceStatuses(profitTraces)).isNotEmpty().containsOnly("POSTED");

		JsonNode reconciliationPage = data(get(
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2055-09&projectId="
						+ fixture.projectId() + "&page=1&pageSize=20",
				admin));
		String reconciliationTraceKey = reconciliationPage.get("items").get(0).get("traceKey").asText();
		JsonNode reconciliationTraces = data(get(
				"/api/admin/reports/operating-accounting-reconciliation/traces?periodCode=2055-09&traceKey="
						+ reconciliationTraceKey + "&page=1&pageSize=20",
				admin));
		assertThat(recursiveValues(reconciliationTraces.get("items"), "sourceType")).contains(
				"PROJECT_COST_CALCULATION", "GL_LEDGER_ENTRY");
		assertThat(glLedgerTraceStatuses(reconciliationTraces)).isNotEmpty().containsOnly("POSTED");

		JsonNode summary = data(get("/api/admin/reports/financial-summary?periodCode=2055-09", admin));
		JsonNode summaryTraces = data(get("/api/admin/reports/financial-summary/traces?periodCode=2055-09&traceKey="
				+ summary.get("traceKey").asText() + "&page=1&pageSize=20", admin));
		assertThat(recursiveValues(summaryTraces.get("items"), "sourceType")).contains("GL_LEDGER_ENTRY");
		assertThat(glLedgerTraceStatuses(summaryTraces)).isNotEmpty().containsOnly("POSTED");

		AuthenticatedSession reportOnly = createUserAndLogin("033-trace-report-only-",
				List.of("report:project-profit:view", "report:operating-accounting:view",
						"report:financial-summary:view"));
		JsonNode restrictedTraces = data(get("/api/admin/reports/project-profit/" + fixture.projectId()
				+ "/traces?periodCode=2055-09&traceKey=" + profitTraceKey + "&page=1&pageSize=20", reportOnly));
		assertThat(restrictedTraces.get("total").intValue()).isZero();
		assertThat(restrictedTraces.get("totalPages").intValue()).isZero();
		assertThat(restrictedTraces.get("items")).isEmpty();
	}

	@Test
	void 项目利润金额权限不足时必须失败关闭脱敏且不可反推金额() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProjectFixture fixture = createProjectProfitFixture("2055-03", false);
		AuthenticatedSession reportOnly = createUserAndLogin("033-report-only-",
				List.of("report:project-profit:view"));

		JsonNode page = data(get("/api/admin/reports/project-profit?periodCode=2055-03&projectId="
				+ fixture.projectId() + "&page=1&pageSize=20", reportOnly));

		assertThat(page.get("summary").get("amountVisible").booleanValue()).isFalse();
		assertThat(page.get("summary").get("shipmentRevenue").isNull()).isTrue();
		assertThat(page.get("summary").get("projectCostTotal").isNull()).isTrue();
		assertThat(page.get("summary").get("accountingRevenue").isNull()).isTrue();
		assertThat(page.get("summary").get("sourceCount").intValue()).isZero();
		assertThat(page.get("items").get(0).get("shipmentRevenue").isNull()).isTrue();
		assertThat(page.get("items").get(0).get("shipmentGrossMarginRate").isNull()).isTrue();
		assertThat(page.get("items").get(0).get("sourceCount").intValue()).isZero();
		assertThat(page.get("items").get(0).get("traceKey").isNull()).isTrue();
	}

	@Test
	void 项目利润实时查询必须应用完整性和对账状态筛选() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProjectFixture incomplete = createProjectProfitFixture("2056-01", false);
		updateProjectCostCompleteness(incomplete.projectId(), "INCOMPLETE");

		JsonNode completeOnly = data(get("/api/admin/reports/project-profit?periodCode=2056-01&projectId="
				+ incomplete.projectId() + "&completenessStatus=COMPLETE&page=1&pageSize=20", admin));
		assertThat(completeOnly.get("total").longValue()).isZero();

		JsonNode incompleteOnly = data(get("/api/admin/reports/project-profit?periodCode=2056-01&projectId="
				+ incomplete.projectId() + "&completenessStatus=INCOMPLETE&page=1&pageSize=20", admin));
		assertThat(incompleteOnly.get("total").longValue()).isEqualTo(1);
		assertThat(incompleteOnly.get("items").get(0).get("completenessStatus").asText()).isEqualTo("INCOMPLETE");

		ProjectFixture unavailable = createProjectProfitFixture("2056-02", false, false);
		JsonNode differentOnly = data(get("/api/admin/reports/project-profit?periodCode=2056-02&projectId="
				+ unavailable.projectId() + "&reconciliationStatus=DIFFERENT&page=1&pageSize=20", admin));
		assertThat(differentOnly.get("total").longValue()).isZero();
	}

	@Test
	void 经营会计对照必须按期间约束029成本并应用真实完整性筛选() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProjectFixture current = createProjectProfitFixture("2056-03", false);
		ProjectFixture nextPeriod = createProjectProfitFixture("2056-04", false);

		JsonNode currentPeriod = data(get(
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2056-03&page=1&pageSize=100",
				admin));
		assertThat(recursiveValues(currentPeriod.get("items"), "projectNo")).contains(current.projectNo())
			.doesNotContain(nextPeriod.projectNo());

		updateProjectCostCompleteness(current.projectId(), "INCOMPLETE");
		JsonNode completeOnly = data(get(
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2056-03&projectId="
						+ current.projectId() + "&completenessStatus=COMPLETE&page=1&pageSize=20",
				admin));
		assertThat(completeOnly.get("total").longValue()).isZero();

		JsonNode incompleteOnly = data(get(
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2056-03&projectId="
						+ current.projectId() + "&completenessStatus=INCOMPLETE&page=1&pageSize=20",
				admin));
		assertThat(incompleteOnly.get("total").longValue()).isEqualTo(1);
	}

	@Test
	void 三十三会计口径必须覆盖阶段科目后代() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProjectFixture fixture = createProjectProfitFixture("2056-05", false);
		String childRevenueCode = insertDescendantAccount("6001", "0336001" + SEQUENCE.incrementAndGet(),
				"033主营收入下级科目");
		long ledgerId = this.jdbcTemplate.queryForObject("select id from gl_ledger where code = 'MAIN'", Long.class);
		long periodId = this.jdbcTemplate.queryForObject("""
				select id
				from gl_accounting_period
				where ledger_id = ?
				and period_code = '2056-05'
				""", Long.class, ledgerId);
		insertLedgerEntry(ledgerId, periodId, fixture.projectId(), childRevenueCode, BigDecimal.ZERO,
				new BigDecimal("222.00"), true, "033下级收入", 4, LocalDate.parse("2056-05-20"));

		JsonNode profit = data(get("/api/admin/reports/project-profit?periodCode=2056-05&projectId="
				+ fixture.projectId() + "&page=1&pageSize=20", admin));
		assertThat(profit.get("summary").get("accountingRevenue").asText()).isEqualTo("1022.00");

		JsonNode reconciliation = data(get(
				"/api/admin/reports/operating-accounting-reconciliation?periodCode=2056-05&projectId="
						+ fixture.projectId() + "&page=1&pageSize=20",
				admin));
		assertThat(reconciliation.get("summary").get("accountingProfitAmount").asText()).isEqualTo("722.00");

		JsonNode financialSummary = data(get("/api/admin/reports/financial-summary?periodCode=2056-05", admin));
		assertThat(financialSummary.get("revenueAmount").asText()).isEqualTo("1133.00");
	}

	@Test
	void 新鲜度必须识别项目成本过期030重开和032反结账() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProjectFixture outdated = createProjectProfitFixture("2056-06", false);
		updateProjectCostCutoffDate(outdated.projectId(), LocalDate.parse("2056-06-15"));

		JsonNode outdatedPage = data(get("/api/admin/reports/project-profit?periodCode=2056-06&projectId="
				+ outdated.projectId() + "&page=1&pageSize=20", admin));
		assertThat(outdatedPage.get("summary").get("freshnessStatus").asText()).isEqualTo("STALE");
		assertThat(outdatedPage.get("items").get(0).get("freshnessStatus").asText()).isEqualTo("STALE");
		JsonNode outdatedOverview = data(get("/api/admin/reports/operating-finance-overview?periodCode=2056-06",
				admin));
		assertThat(outdatedOverview.get("freshnessStatus").asText()).isEqualTo("STALE");

		ProjectFixture reopenedBusiness = createProjectProfitFixture("2056-07", false);
		insertReopenedBusinessPeriod("2056-07");
		JsonNode reopenedBusinessPage = data(get("/api/admin/reports/project-profit?periodCode=2056-07&projectId="
				+ reopenedBusiness.projectId() + "&page=1&pageSize=20", admin));
		assertThat(reopenedBusinessPage.get("summary").get("freshnessStatus").asText()).isEqualTo("STALE");

		ProjectFixture reopenedFinancial = createProjectProfitFixture("2056-08", true);
		reopenFinancialClose("2056-08");
		JsonNode reopenedFinancialPage = data(get("/api/admin/reports/project-profit?periodCode=2056-08&projectId="
				+ reopenedFinancial.projectId() + "&page=1&pageSize=20", admin));
		assertThat(reopenedFinancialPage.get("summary").get("freshnessStatus").asText()).isEqualTo("STALE");
	}

	@Test
	void 固定摘要合法追溯键在空事实期间必须返回空分页() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		createProjectProfitFixture("2056-09", false, false);

		JsonNode summary = data(get("/api/admin/reports/financial-summary?periodCode=2056-09", admin));
		JsonNode traces = data(get("/api/admin/reports/financial-summary/traces?periodCode=2056-09&traceKey="
				+ summary.get("traceKey").asText() + "&page=1&pageSize=20", admin));

		assertThat(traces.get("page").intValue()).isEqualTo(1);
		assertThat(traces.get("pageSize").intValue()).isEqualTo(20);
		assertThat(traces.get("total").intValue()).isZero();
		assertThat(traces.get("totalPages").intValue()).isZero();
		assertThat(traces.get("items")).isEmpty();
	}

	@Test
	void 合同回款必须基于合同发票收款核销和预收生成非空报表与追溯() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ContractCollectionFixture fixture = createContractCollectionFixture("2055-04");

		JsonNode page = data(get("/api/admin/reports/contract-collections?periodCode=2055-04&projectId="
				+ fixture.projectId() + "&page=1&pageSize=1", admin));

		assertThat(page.get("summary").get("contractAmount").asText()).isEqualTo("2000.00");
		assertThat(page.get("summary").get("invoiceAmount").asText()).isEqualTo("900.00");
		assertThat(page.get("summary").get("receivedAmount").asText()).isEqualTo("300.00");
		assertThat(page.get("summary").get("unreceivedAmount").asText()).isEqualTo("700.00");
		assertThat(page.get("summary").get("overdueAmount").asText()).isEqualTo("700.00");
		assertThat(page.get("summary").get("sourceCount").intValue()).isGreaterThanOrEqualTo(5);
		assertThat(page.get("total").longValue()).isEqualTo(1);

		JsonNode item = page.get("items").get(0);
		assertThat(item.get("contractId").longValue()).isEqualTo(fixture.contractId());
		assertThat(item.get("contractNo").asText()).isEqualTo(fixture.contractNo());
		assertThat(item.get("customerName").asText()).isEqualTo(fixture.customerName());
		assertThat(item.get("contractAmount").asText()).isEqualTo("2000.00");
		assertThat(item.get("invoiceAmount").asText()).isEqualTo("900.00");
		assertThat(item.get("allocatedAmount").asText()).isEqualTo("300.00");
		assertThat(item.get("advanceReceiptAmount").asText()).isEqualTo("150.00");
		assertThat(item.get("collectionRate").asText()).isEqualTo("15.00");
		assertThat(item.get("status").asText()).isEqualTo("OVERDUE");

		JsonNode traces = data(get("/api/admin/reports/contract-collections/traces?periodCode=2055-04&traceKey="
				+ item.get("traceKey").asText() + "&page=1&pageSize=20", admin));
		assertThat(recursiveValues(traces.get("items"), "sourceType")).contains("SALES_PROJECT_CONTRACT",
				"SALES_INVOICE", "RECEIVABLE", "RECEIPT", "ADVANCE_RECEIPT");
	}

	@Test
	void 采购差异必须基于订单收货发票付款生成非空报表与追溯() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementVarianceFixture fixture = createProcurementVarianceFixture("2055-05");

		JsonNode page = data(get("/api/admin/reports/procurement-variances?periodCode=2055-05&projectId="
				+ fixture.projectId() + "&page=1&pageSize=1", admin));

		assertThat(page.get("summary").get("orderAmount").asText()).isEqualTo("1000.00");
		assertThat(page.get("summary").get("receiptAmount").asText()).isEqualTo("800.00");
		assertThat(page.get("summary").get("invoiceAmount").asText()).isEqualTo("750.00");
		assertThat(page.get("summary").get("paidAmount").asText()).isEqualTo("300.00");
		assertThat(page.get("summary").get("matchVarianceAmount").asText()).isEqualTo("250.00");
		assertThat(page.get("summary").get("sourceCount").intValue()).isGreaterThanOrEqualTo(5);
		assertThat(page.get("total").longValue()).isEqualTo(1);

		JsonNode item = page.get("items").get(0);
		assertThat(item.get("sourceNo").asText()).isEqualTo(fixture.orderNo());
		assertThat(item.get("supplierName").asText()).isEqualTo(fixture.supplierName());
		assertThat(item.get("projectNo").asText()).isEqualTo(fixture.projectNo());
		assertThat(item.get("basis").asText()).isEqualTo("PROJECT");
		assertThat(item.get("unreceivedOrderAmount").asText()).isEqualTo("200.00");
		assertThat(item.get("receivedUninvoicedAmount").asText()).isEqualTo("50.00");
		assertThat(item.get("invoiceReceiptDifferenceAmount").asText()).isEqualTo("-50.00");
		assertThat(item.get("unpaidAmount").asText()).isEqualTo("450.00");
		assertThat(item.get("reconciliationStatus").asText()).isEqualTo("DIFFERENT");

		JsonNode traces = data(get("/api/admin/reports/procurement-variances/traces?periodCode=2055-05&traceKey="
				+ item.get("traceKey").asText() + "&page=1&pageSize=20", admin));
		assertThat(recursiveValues(traces.get("items"), "sourceType")).contains("PROCUREMENT_ORDER",
				"PURCHASE_RECEIPT", "PURCHASE_INVOICE", "PAYABLE", "PAYMENT");
	}

	@Test
	void 库存资金必须基于统一库存估值区分项目公共冻结质量和未估值空值() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryCapitalFixture fixture = createInventoryCapitalFixture();

		JsonNode page = data(get("/api/admin/reports/inventory-capital?periodCode=2055-06&page=1&pageSize=10",
				admin));

		assertThat(page.get("summary").get("quantity").asText()).isEqualTo("8.000000");
		assertThat(page.get("summary").get("amount").asText()).isEqualTo("1000.00");
		assertThat(page.get("summary").path("knownValuationAmount").asText()).isEqualTo("1000.00");
		assertThat(page.get("summary").path("unknownValuationQuantity").asText()).isEqualTo("3.000000");
		assertThat(page.get("summary").path("completenessStatus").asText()).isEqualTo("INCOMPLETE");
		assertThat(page.get("summary").get("riskQuantity").asText()).isEqualTo("4.000000");
		assertThat(page.get("summary").get("sourceCount").intValue()).isGreaterThanOrEqualTo(2);
		assertThat(page.get("total").longValue()).isGreaterThanOrEqualTo(2);

		JsonNode valued = findItemByText(page.get("items"), "materialName", fixture.valuedMaterialName());
		assertThat(valued.get("ownerType").asText()).isEqualTo("PROJECT");
		assertThat(valued.get("projectNo").asText()).isEqualTo(fixture.projectNo());
		assertThat(valued.get("warehouseName").asText()).isEqualTo(fixture.warehouseName());
		assertThat(valued.get("qualityStatus").asText()).isEqualTo("QUALIFIED");
		assertThat(valued.get("freezeStatus").asText()).isEqualTo("LOCKED");
		assertThat(valued.get("valuationStatus").asText()).isEqualTo("VALUED");
		assertThat(valued.get("amount").asText()).isEqualTo("1000.00");

		JsonNode unvalued = findItemByText(page.get("items"), "materialName", fixture.unvaluedMaterialName());
		assertThat(unvalued.get("ownerType").asText()).isEqualTo("PUBLIC");
		assertThat(unvalued.get("amount").isNull()).isTrue();
		assertThat(unvalued.get("valuationStatus").asText()).isEqualTo("LEGACY_UNVALUED");

		JsonNode traces = data(get("/api/admin/reports/inventory-capital/traces?periodCode=2055-06&traceKey="
				+ valued.get("traceKey").asText() + "&page=1&pageSize=20", admin));
		assertThat(recursiveValues(traces.get("items"), "sourceType")).contains("INVENTORY_BALANCE");
	}

	@Test
	void 往来分析必须基于应收应付预收预付核销和固定五桶账龄生成非空报表与追溯() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ReceivablePayableFixture fixture = createReceivablePayableFixture("2055-07");

		JsonNode page = data(get("/api/admin/reports/receivable-payable?periodCode=2055-07&projectId="
				+ fixture.projectId() + "&page=1&pageSize=10", admin));

		assertThat(page.get("summary").get("receivableAmount").asText()).isEqualTo("700.00");
		assertThat(page.get("summary").get("payableAmount").asText()).isEqualTo("600.00");
		assertThat(page.get("summary").get("advanceReceiptAmount").asText()).isEqualTo("150.00");
		assertThat(page.get("summary").get("prepaymentAmount").asText()).isEqualTo("120.00");
		assertThat(page.get("summary").get("balanceAmount").asText()).isEqualTo("100.00");
		assertThat(page.get("summary").get("overdueAmount").asText()).isEqualTo("700.00");
		assertThat(page.get("summary").get("sourceCount").intValue()).isGreaterThanOrEqualTo(4);

		JsonNode customer = findItemByText(page.get("items"), "partyName", fixture.customerName());
		assertThat(customer.get("partyType").asText()).isEqualTo("CUSTOMER");
		assertThat(customer.get("receivableAmount").asText()).isEqualTo("700.00");
		assertThat(customer.get("advanceReceiptAmount").asText()).isEqualTo("150.00");
		assertThat(customer.get("settledAmount").asText()).isEqualTo("300.00");
		assertThat(customer.get("aging1To30Amount").asText()).isEqualTo("700.00");

		JsonNode supplier = findItemByText(page.get("items"), "partyName", fixture.supplierName());
		assertThat(supplier.get("partyType").asText()).isEqualTo("SUPPLIER");
		assertThat(supplier.get("payableAmount").asText()).isEqualTo("600.00");
		assertThat(supplier.get("prepaymentAmount").asText()).isEqualTo("120.00");
		assertThat(supplier.get("agingOver90Amount").asText()).isEqualTo("600.00");

		JsonNode traces = data(get("/api/admin/reports/receivable-payable/traces?periodCode=2055-07&traceKey="
				+ customer.get("traceKey").asText() + "&page=1&pageSize=20", admin));
		assertThat(recursiveValues(traces.get("items"), "sourceType")).contains("RECEIVABLE", "RECEIPT",
				"ADVANCE_RECEIPT");
	}

	@Test
	void 合同回款缺收款来源权限必须关闭列表来源与追溯() throws Exception {
		ContractCollectionFixture fixture = createContractCollectionFixture("2055-10");
		AuthenticatedSession fullSource = createUserAndLogin("033-cc-source-full-",
				contractCollectionSourcePermissions());

		JsonNode fullPage = data(get("/api/admin/reports/contract-collections?periodCode=2055-10&projectId="
				+ fixture.projectId() + "&page=1&pageSize=1", fullSource));
		JsonNode fullItem = fullPage.get("items").get(0);
		assertSourceVisible(fullItem, "invoiceNos", "receiptNos", "receivableNos");
		String traceKey = fullItem.get("traceKey").asText();
		JsonNode fullTraces = data(get("/api/admin/reports/contract-collections/traces?periodCode=2055-10&traceKey="
				+ traceKey + "&page=1&pageSize=20", fullSource));
		assertThat(fullTraces.get("total").intValue()).isGreaterThan(0);

		AuthenticatedSession missingReceipt = createUserAndLogin("033-cc-source-missing-",
				without(contractCollectionSourcePermissions(), "finance:receipt:view"));
		JsonNode restrictedPage = data(get("/api/admin/reports/contract-collections?periodCode=2055-10&projectId="
				+ fixture.projectId() + "&page=1&pageSize=1", missingReceipt));
		assertThat(restrictedPage.get("summary").get("sourceCount").intValue()).isZero();
		assertSourceClosed(restrictedPage.get("items").get(0), "invoiceNos", "receiptNos", "receivableNos");
		assertTraceClosed("/api/admin/reports/contract-collections/traces?periodCode=2055-10&traceKey=" + traceKey
				+ "&page=1&pageSize=20", missingReceipt);
	}

	@Test
	void 合同回款业务快照缺核销来源权限必须关闭合同号和追溯且金额仍独立可见() throws Exception {
		ContractCollectionSnapshotFixture snapshot = insertContractCollectionBusinessSnapshot();
		ContractCollectionFixture fixture = snapshot.contract();
		AuthenticatedSession fullSource = createUserAndLogin("033-cc-snapshot-source-full-",
				contractCollectionSourcePermissions());

		JsonNode fullPage = data(get("/api/admin/reports/contract-collections?periodCode=" + snapshot.periodCode()
				+ "&analysisMode=BUSINESS_SNAPSHOT&projectId=" + fixture.projectId() + "&page=1&pageSize=1",
				fullSource));
		JsonNode fullItem = fullPage.get("items").get(0);
		assertThat(fullPage.get("summary").get("analysisMode").asText()).isEqualTo("BUSINESS_SNAPSHOT");
		assertThat(fullPage.get("summary").get("freshnessStatus").asText()).isEqualTo("FROZEN");
		assertThat(fullItem.get("contractNo").asText()).isEqualTo(fixture.contractNo());
		assertSourceVisible(fullItem, "contractNo", "invoiceNos", "receiptNos", "receivableNos");
		String traceKey = fullItem.get("traceKey").asText();
		JsonNode fullTraces = data(get("/api/admin/reports/contract-collections/traces?periodCode="
				+ snapshot.periodCode() + "&analysisMode=BUSINESS_SNAPSHOT&traceKey=" + traceKey
				+ "&page=1&pageSize=20", fullSource));
		assertThat(fullTraces.get("total").intValue()).isGreaterThan(0);

		AuthenticatedSession missingAllocation = createUserAndLogin("033-cc-snapshot-source-missing-",
				without(contractCollectionSourcePermissions(), "finance:settlement-allocation:view"));
		JsonNode restrictedPage = data(get("/api/admin/reports/contract-collections?periodCode=" + snapshot.periodCode()
				+ "&analysisMode=BUSINESS_SNAPSHOT&projectId=" + fixture.projectId() + "&page=1&pageSize=1",
				missingAllocation));
		assertThat(restrictedPage.get("summary").get("sourceCount").intValue()).isZero();
		JsonNode restrictedItem = restrictedPage.get("items").get(0);
		assertThat(restrictedItem.get("contractAmount").asText()).isEqualTo("2000.00");
		assertThat(restrictedItem.get("invoiceAmount").asText()).isEqualTo("900.00");
		assertSourceClosed(restrictedItem, "contractNo", "invoiceNos", "receiptNos", "receivableNos");
		assertTraceClosed("/api/admin/reports/contract-collections/traces?periodCode=" + snapshot.periodCode()
				+ "&analysisMode=BUSINESS_SNAPSHOT&traceKey=" + traceKey + "&page=1&pageSize=20",
				missingAllocation);
	}

	@Test
	void 采购差异缺退货来源权限必须关闭列表来源与追溯且金额仍独立可见() throws Exception {
		ProcurementVarianceFixture fixture = createProcurementVarianceFixture("2055-11");
		insertPurchaseReturnForOrder("033-PV-RET-" + SEQUENCE.incrementAndGet(), fixture.orderId(),
				LocalDate.parse("2055-11-09"), new BigDecimal("1.000000"), new BigDecimal("50.000000"));
		AuthenticatedSession fullSource = createUserAndLogin("033-pv-source-full-",
				procurementVarianceSourcePermissions());

		JsonNode fullPage = data(get("/api/admin/reports/procurement-variances?periodCode=2055-11&projectId="
				+ fixture.projectId() + "&page=1&pageSize=1", fullSource));
		JsonNode fullItem = fullPage.get("items").get(0);
		assertSourceVisible(fullItem, "sourceNo", "purchaseInvoiceNos", "paymentNos");
		String traceKey = fullItem.get("traceKey").asText();
		JsonNode fullTraces = data(get("/api/admin/reports/procurement-variances/traces?periodCode=2055-11&traceKey="
				+ traceKey + "&page=1&pageSize=20", fullSource));
		assertThat(recursiveValues(fullTraces.get("items"), "sourceType")).contains("PURCHASE_RETURN");

		AuthenticatedSession missingReturn = createUserAndLogin("033-pv-source-missing-",
				without(procurementVarianceSourcePermissions(), "procurement:return:view"));
		JsonNode restrictedPage = data(get("/api/admin/reports/procurement-variances?periodCode=2055-11&projectId="
				+ fixture.projectId() + "&page=1&pageSize=1", missingReturn));
		assertThat(restrictedPage.get("summary").get("sourceCount").intValue()).isZero();
		JsonNode restrictedItem = restrictedPage.get("items").get(0);
		assertThat(restrictedItem.get("orderAmount").asText()).isEqualTo("1000.00");
		assertSourceClosed(restrictedItem, "sourceNo", "purchaseInvoiceNos", "paymentNos");
		assertTraceClosed("/api/admin/reports/procurement-variances/traces?periodCode=2055-11&traceKey=" + traceKey
				+ "&page=1&pageSize=20", missingReturn);
	}

	@Test
	void 库存资金缺移动来源权限必须关闭列表来源与追溯且估值金额仍独立可见() throws Exception {
		InventoryCapitalFixture fixture = createInventoryCapitalFixture();
		AuthenticatedSession fullSource = createUserAndLogin("033-ic-source-full-",
				inventoryCapitalSourcePermissions());

		JsonNode fullPage = data(get("/api/admin/reports/inventory-capital?periodCode=2055-12&page=1&pageSize=10",
				fullSource));
		JsonNode fullItem = findItemByText(fullPage.get("items"), "materialName", fixture.valuedMaterialName());
		assertSourceVisible(fullItem);
		String traceKey = fullItem.get("traceKey").asText();
		JsonNode fullTraces = data(get("/api/admin/reports/inventory-capital/traces?periodCode=2055-12&traceKey="
				+ traceKey + "&page=1&pageSize=20", fullSource));
		assertThat(fullTraces.get("total").intValue()).isGreaterThan(0);

		AuthenticatedSession missingMovement = createUserAndLogin("033-ic-source-missing-",
				without(inventoryCapitalSourcePermissions(), "inventory:movement:view"));
		JsonNode restrictedPage = data(get("/api/admin/reports/inventory-capital?periodCode=2055-12&page=1&pageSize=10",
				missingMovement));
		assertThat(restrictedPage.get("summary").get("sourceCount").intValue()).isZero();
		JsonNode restrictedItem = findItemByText(restrictedPage.get("items"), "materialName",
				fixture.valuedMaterialName());
		assertThat(restrictedItem.get("amount").asText()).isEqualTo("1000.00");
		assertSourceClosed(restrictedItem);
		assertTraceClosed("/api/admin/reports/inventory-capital/traces?periodCode=2055-12&traceKey=" + traceKey
				+ "&page=1&pageSize=20", missingMovement);
	}

	@Test
	void 往来分析缺核销来源权限必须关闭列表来源与追溯且金额仍独立可见() throws Exception {
		ReceivablePayableFixture fixture = createReceivablePayableFixture("2055-12");
		insertReceivableSettlementAllocation("033-RP-ALLOC-" + SEQUENCE.incrementAndGet(), fixture.projectId(),
				fixture.customerName(), LocalDate.parse("2055-12-08"), new BigDecimal("50.00"));
		AuthenticatedSession fullSource = createUserAndLogin("033-rp-source-full-",
				receivablePayableSourcePermissions());

		JsonNode fullPage = data(get("/api/admin/reports/receivable-payable?periodCode=2055-12&projectId="
				+ fixture.projectId() + "&page=1&pageSize=10", fullSource));
		JsonNode fullItem = findItemByText(fullPage.get("items"), "partyName", fixture.customerName());
		assertSourceVisible(fullItem, "sourceNo");
		String traceKey = fullItem.get("traceKey").asText();
		JsonNode fullTraces = data(get("/api/admin/reports/receivable-payable/traces?periodCode=2055-12&traceKey="
				+ traceKey + "&page=1&pageSize=20", fullSource));
		assertThat(recursiveValues(fullTraces.get("items"), "sourceType")).contains("SETTLEMENT_ALLOCATION");

		AuthenticatedSession missingAllocation = createUserAndLogin("033-rp-source-missing-",
				without(receivablePayableSourcePermissions(), "finance:settlement-allocation:view"));
		JsonNode restrictedPage = data(get("/api/admin/reports/receivable-payable?periodCode=2055-12&projectId="
				+ fixture.projectId() + "&page=1&pageSize=10", missingAllocation));
		assertThat(restrictedPage.get("summary").get("sourceCount").intValue()).isZero();
		JsonNode restrictedItem = findItemByText(restrictedPage.get("items"), "partyName", fixture.customerName());
		assertThat(restrictedItem.get("receivableAmount").asText()).isEqualTo("700.00");
		assertSourceClosed(restrictedItem, "sourceNo");
		assertTraceClosed("/api/admin/reports/receivable-payable/traces?periodCode=2055-12&traceKey=" + traceKey
				+ "&page=1&pageSize=20", missingAllocation);
	}

	private void assertSourceVisible(JsonNode item, String... sourceFields) {
		assertThat(item.get("sourceCount").intValue()).isGreaterThan(0);
		assertThat(item.get("traceKey").asText()).isNotBlank();
		for (String field : sourceFields) {
			assertThat(item.get(field).asText()).isNotBlank();
		}
	}

	private void assertSourceClosed(JsonNode item, String... sourceFields) {
		assertThat(item.get("sourceCount").intValue()).isZero();
		assertThat(item.get("traceKey").isNull()).isTrue();
		for (String field : sourceFields) {
			assertThat(item.get(field).isNull()).isTrue();
		}
	}

	private void assertTraceClosed(String path, AuthenticatedSession session) throws Exception {
		JsonNode traces = data(get(path, session));
		assertThat(traces.get("total").intValue()).isZero();
		assertThat(traces.get("totalPages").intValue()).isZero();
		assertThat(traces.get("items")).isEmpty();
	}

	private List<String> without(List<String> permissions, String omitted) {
		return permissions.stream().filter((permission) -> !omitted.equals(permission)).toList();
	}

	private List<String> contractCollectionSourcePermissions() {
		return List.of("report:contract-collection:view", "sales:contract:view", "finance:sales-invoice:view",
				"finance:receivable:view", "finance:receipt:view", "finance:settlement-allocation:view");
	}

	private List<String> procurementVarianceSourcePermissions() {
		return List.of("report:procurement-variance:view", "procurement:order:view", "procurement:receipt:view",
				"procurement:return:view", "finance:purchase-invoice:view", "finance:payable:view",
				"finance:payment:view", "production:outsourcing:view", "production:outsourcing-receipt:view");
	}

	private List<String> inventoryCapitalSourcePermissions() {
		return List.of("report:inventory-capital:view", "inventory:valuation:view", "inventory:balance:view",
				"inventory:movement:view");
	}

	private List<String> receivablePayableSourcePermissions() {
		return List.of("report:receivable-payable:view", "finance:receivable:view", "finance:receipt:view",
				"finance:payable:view", "finance:payment:view", "finance:settlement-allocation:view",
				"procurement:receipt:view");
	}

	private ContractCollectionFixture createContractCollectionFixture(String periodCode) {
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate periodStart = LocalDate.parse(periodCode + "-01");
		long unitId = insertUnit("033_CC_UNIT_" + suffix, "033回款单位" + suffix);
		long warehouseId = insertWarehouse("033_CC_WH_" + suffix, "033回款仓" + suffix);
		long customerId = insertCustomer("033_CC_C_" + suffix, "033回款客户" + suffix);
		long categoryId = insertMaterialCategory("033_CC_CAT_" + suffix);
		long materialId = insertMaterial("033_CC_MAT_" + suffix, "033回款物料" + suffix, "FINISHED_GOOD",
				"SELF_MADE", categoryId, unitId);
		ProjectFixture project = insertProject("033-CC-PRJ-" + suffix, "033回款项目" + suffix, customerId);
		long contractId = insertContract("033-CC-CON-" + suffix, project.projectId(), periodStart.minusDays(3),
				new BigDecimal("2000.00"));
		SalesShipmentFact shipment = insertSalesShipment("033-CC-SO-" + suffix, "033-CC-SH-" + suffix, customerId,
				warehouseId, materialId, unitId, project.projectId(), contractId, periodStart.plusDays(2),
				new BigDecimal("10.000000"), new BigDecimal("100.000000"));
		long receivableId = insertReceivable("033-CC-AR-" + suffix, customerId, shipment, periodStart.plusDays(3),
				periodStart.plusDays(9), new BigDecimal("1000.00"), new BigDecimal("300.00"),
				new BigDecimal("700.00"));
		long receiptId = insertReceipt("033-CC-RC-" + suffix, customerId, periodStart.plusDays(5),
				new BigDecimal("300.00"));
		insertReceiptAllocation(receiptId, receivableId, new BigDecimal("300.00"));
		long advanceReceiptId = insertReceipt("033-CC-ADV-" + suffix, customerId, periodStart.plusDays(6),
				new BigDecimal("150.00"));
		insertReceiptBalance(advanceReceiptId, customerId, project.projectId(), new BigDecimal("150.00"),
				BigDecimal.ZERO, new BigDecimal("150.00"));
		insertSalesInvoice("033-CC-INV-" + suffix, customerId, project.projectId(), shipment, receivableId,
				periodStart.plusDays(7), new BigDecimal("900.00"));
		return new ContractCollectionFixture(project.projectId(), project.projectNo(), contractId, "033-CC-CON-"
				+ suffix, "033回款客户" + suffix);
	}

	private ProcurementVarianceFixture createProcurementVarianceFixture(String periodCode) {
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate periodStart = LocalDate.parse(periodCode + "-01");
		long unitId = insertUnit("033_PV_UNIT_" + suffix, "033采购单位" + suffix);
		long warehouseId = insertWarehouse("033_PV_WH_" + suffix, "033采购仓" + suffix);
		long customerId = insertCustomer("033_PV_C_" + suffix, "033采购项目客户" + suffix);
		long supplierId = insertSupplier("033_PV_SUP_" + suffix, "033采购供应商" + suffix);
		long categoryId = insertMaterialCategory("033_PV_CAT_" + suffix);
		long materialId = insertMaterial("033_PV_MAT_" + suffix, "033采购物料" + suffix, "RAW_MATERIAL",
				"PURCHASED", categoryId, unitId);
		ProjectFixture project = insertProject("033-PV-PRJ-" + suffix, "033采购项目" + suffix, customerId);
		PurchaseReceiptFact receipt = insertPurchaseReceipt("033-PV-PO-" + suffix, "033-PV-PR-" + suffix,
				supplierId, warehouseId, materialId, unitId, project.projectId(), periodStart.plusDays(4),
				new BigDecimal("10.000000"), new BigDecimal("8.000000"), new BigDecimal("100.000000"));
		long payableId = insertPayable("033-PV-AP-" + suffix, supplierId, receipt, periodStart.plusDays(5),
				periodStart.plusDays(20), new BigDecimal("750.00"), new BigDecimal("300.00"),
				new BigDecimal("450.00"));
		insertPurchaseInvoice("033-PV-INV-" + suffix, supplierId, project.projectId(), receipt, payableId,
				periodStart.plusDays(6), new BigDecimal("750.00"));
		long paymentId = insertPayment("033-PV-PAY-" + suffix, supplierId, periodStart.plusDays(7),
				new BigDecimal("300.00"));
		insertPaymentAllocation(paymentId, payableId, new BigDecimal("300.00"));
		return new ProcurementVarianceFixture(project.projectId(), project.projectNo(), receipt.orderId(),
				receipt.orderNo(), "033采购供应商" + suffix);
	}

	private InventoryCapitalFixture createInventoryCapitalFixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("033_IC_UNIT_" + suffix, "033库存单位" + suffix);
		long warehouseId = insertWarehouse("033_IC_WH_" + suffix, "033库存仓" + suffix);
		long customerId = insertCustomer("033_IC_C_" + suffix, "033库存项目客户" + suffix);
		long categoryId = insertMaterialCategory("033_IC_CAT_" + suffix);
		long valuedMaterialId = insertMaterial("033_IC_VAL_" + suffix, "033已估值物料" + suffix, "RAW_MATERIAL",
				"PURCHASED", categoryId, unitId);
		long unvaluedMaterialId = insertMaterial("033_IC_UNV_" + suffix, "033未估值物料" + suffix, "RAW_MATERIAL",
				"PURCHASED", categoryId, unitId);
		ProjectFixture project = insertProject("033-IC-PRJ-" + suffix, "033库存项目" + suffix, customerId);
		insertStockBalance(warehouseId, valuedMaterialId, unitId, project.projectId(), "PROJECT", "QUALIFIED",
				new BigDecimal("5.000000"), new BigDecimal("1.000000"), "VALUED", new BigDecimal("1000.00"),
				new BigDecimal("200.000000"));
		insertStockBalance(warehouseId, unvaluedMaterialId, unitId, null, "PUBLIC", "FROZEN",
				new BigDecimal("3.000000"), BigDecimal.ZERO, "LEGACY_UNVALUED", null, null);
		return new InventoryCapitalFixture(project.projectNo(), "033库存仓" + suffix, "033已估值物料" + suffix,
				"033未估值物料" + suffix);
	}

	private ReceivablePayableFixture createReceivablePayableFixture(String periodCode) {
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate periodStart = LocalDate.parse(periodCode + "-01");
		long unitId = insertUnit("033_RP_UNIT_" + suffix, "033往来单位" + suffix);
		long warehouseId = insertWarehouse("033_RP_WH_" + suffix, "033往来仓" + suffix);
		long customerId = insertCustomer("033_RP_C_" + suffix, "033往来客户" + suffix);
		long supplierId = insertSupplier("033_RP_SUP_" + suffix, "033往来供应商" + suffix);
		long categoryId = insertMaterialCategory("033_RP_CAT_" + suffix);
		long finishedMaterialId = insertMaterial("033_RP_FG_" + suffix, "033往来成品" + suffix, "FINISHED_GOOD",
				"SELF_MADE", categoryId, unitId);
		long rawMaterialId = insertMaterial("033_RP_RM_" + suffix, "033往来原料" + suffix, "RAW_MATERIAL",
				"PURCHASED", categoryId, unitId);
		ProjectFixture project = insertProject("033-RP-PRJ-" + suffix, "033往来项目" + suffix, customerId);
		long contractId = insertContract("033-RP-CON-" + suffix, project.projectId(), periodStart.minusDays(2),
				new BigDecimal("1200.00"));
		SalesShipmentFact shipment = insertSalesShipment("033-RP-SO-" + suffix, "033-RP-SH-" + suffix, customerId,
				warehouseId, finishedMaterialId, unitId, project.projectId(), contractId, periodStart.plusDays(1),
				new BigDecimal("10.000000"), new BigDecimal("100.000000"));
		long receivableId = insertReceivable("033-RP-AR-" + suffix, customerId, shipment, periodStart.plusDays(2),
				periodStart.plusDays(9), new BigDecimal("1000.00"), new BigDecimal("300.00"),
				new BigDecimal("700.00"));
		long receiptId = insertReceipt("033-RP-RC-" + suffix, customerId, periodStart.plusDays(4),
				new BigDecimal("300.00"));
		insertReceiptAllocation(receiptId, receivableId, new BigDecimal("300.00"));
		long advanceReceiptId = insertReceipt("033-RP-ADV-" + suffix, customerId, periodStart.plusDays(5),
				new BigDecimal("150.00"));
		insertReceiptBalance(advanceReceiptId, customerId, project.projectId(), new BigDecimal("150.00"),
				BigDecimal.ZERO, new BigDecimal("150.00"));
		PurchaseReceiptFact purchaseReceipt = insertPurchaseReceipt("033-RP-PO-" + suffix, "033-RP-PR-" + suffix,
				supplierId, warehouseId, rawMaterialId, unitId, project.projectId(), periodStart.plusDays(3),
				new BigDecimal("8.000000"), new BigDecimal("8.000000"), new BigDecimal("100.000000"));
		long payableId = insertPayable("033-RP-AP-" + suffix, supplierId, purchaseReceipt, periodStart.plusDays(4),
				periodStart.minusMonths(3), new BigDecimal("800.00"), new BigDecimal("200.00"),
				new BigDecimal("600.00"));
		long paymentId = insertPayment("033-RP-PAY-" + suffix, supplierId, periodStart.plusDays(6),
				new BigDecimal("200.00"));
		insertPaymentAllocation(paymentId, payableId, new BigDecimal("200.00"));
		long prepaymentId = insertPayment("033-RP-PRE-" + suffix, supplierId, periodStart.plusDays(7),
				new BigDecimal("120.00"));
		insertPaymentBalance(prepaymentId, supplierId, project.projectId(), new BigDecimal("120.00"), BigDecimal.ZERO,
				new BigDecimal("120.00"));
		return new ReceivablePayableFixture(project.projectId(), project.projectNo(), "033往来客户" + suffix,
				"033往来供应商" + suffix);
	}

	private ProjectFixture insertProject(String projectNo, String projectName, long customerId) {
		long projectId = this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, date '2055-01-01', date '2055-12-31', 'ACTIVE', 0.00, 0.00,
					'test', now(), 'test', now())
				returning id
				""", Long.class, projectNo, projectName, customerId, adminUserId());
		return new ProjectFixture(projectId, projectNo);
	}

	private long insertContract(String contractNo, long projectId, LocalDate signedDate, BigDecimal amount) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project_contract (
					contract_no, project_id, contract_type, name, signed_date, effective_start_date,
					effective_end_date, amount, status, created_by, created_at, updated_by, updated_at,
					activated_by, activated_at
				)
				values (?, ?, 'MAIN', ?, ?, ?, date '2055-12-31', ?, 'EFFECTIVE',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, contractNo, projectId, contractNo + "合同", signedDate, signedDate, amount);
	}

	private SalesShipmentFact insertSalesShipment(String orderNo, String shipmentNo, long customerId, long warehouseId,
			long materialId, long unitId, long projectId, long contractId, LocalDate businessDate, BigDecimal quantity,
			BigDecimal unitPrice) {
		BigDecimal amount = quantity.multiply(unitPrice).setScale(2);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, project_id, contract_id,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'SHIPPED', ?, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, orderNo, customerId, businessDate.minusDays(2), businessDate.plusDays(3),
				projectId, contractId);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
					tax_excluded_amount, tax_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 0, ?, now(), now())
				returning id
				""", Long.class, orderId, materialId, unitId, quantity, quantity, unitPrice,
				businessDate.plusDays(3), unitPrice, unitPrice, amount, amount);
		long shipmentId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, shipmentNo, orderId, customerId, warehouseId, businessDate);
		long shipmentLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity, tax_rate,
					tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, 0, ?, ?, ?, 0, ?, now(), now())
				returning id
				""", Long.class, shipmentId, orderLineId, materialId, unitId, quantity, quantity, quantity,
				unitPrice, unitPrice, amount, amount);
		return new SalesShipmentFact(shipmentId, shipmentNo, shipmentLineId);
	}

	private long insertReceivable(String receivableNo, long customerId, SalesShipmentFact shipment,
			LocalDate businessDate, LocalDate dueDate, BigDecimal totalAmount, BigDecimal receivedAmount,
			BigDecimal unreceivedAmount) {
		long receivableId = this.jdbcTemplate.queryForObject("""
				insert into fin_receivable (
					receivable_no, customer_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, received_amount, unreceived_amount, status, created_by, created_at,
					updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'SALES_SHIPMENT', ?, ?, ?, ?, ?, ?, ?, 'PARTIALLY_RECEIVED',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, receivableNo, customerId, shipment.shipmentId(), shipment.shipmentNo(),
				businessDate, dueDate, totalAmount, receivedAmount, unreceivedAmount);
		this.jdbcTemplate.update("""
				insert into fin_receivable_source (
					receivable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'SALES_SHIPMENT', ?, ?, ?, 1, ?)
				""", receivableId, shipment.shipmentId(), shipment.shipmentNo(), shipment.shipmentLineId(),
				totalAmount);
		return receivableId;
	}

	private long insertReceipt(String receiptNo, long customerId, LocalDate receiptDate, BigDecimal amount) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_receipt (
					receipt_no, customer_id, receipt_date, amount, method, status,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, 'BANK_TRANSFER', 'POSTED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, receiptNo, customerId, receiptDate, amount);
	}

	private void insertReceiptAllocation(long receiptId, long receivableId, BigDecimal amount) {
		this.jdbcTemplate.update("""
				insert into fin_receipt_allocation (receipt_id, receivable_id, allocated_amount)
				values (?, ?, ?)
				""", receiptId, receivableId, amount);
	}

	private void insertReceiptBalance(long receiptId, long customerId, Long projectId, BigDecimal originalAmount,
			BigDecimal allocatedAmount, BigDecimal availableAmount) {
		this.jdbcTemplate.update("""
				insert into fin_receipt_balance (
					receipt_id, customer_id, ownership_type, project_id, original_amount, allocated_amount,
					available_amount, status, updated_at
				)
				values (?, ?, 'PROJECT', ?, ?, ?, ?, 'POSTED', now())
				""", receiptId, customerId, projectId, originalAmount, allocatedAmount, availableAmount);
	}

	private void insertSalesInvoice(String invoiceNo, long customerId, long projectId, SalesShipmentFact shipment,
			long receivableId, LocalDate invoiceDate, BigDecimal amount) {
		this.jdbcTemplate.update("""
				insert into fin_sales_invoice (
					invoice_no, customer_id, ownership_type, project_id, source_type, source_id, source_no,
					invoice_date, due_date, invoice_type, tax_excluded_amount, tax_amount,
					tax_included_amount, status, linked_receivable_id, created_by, created_at, updated_by,
					updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'PROJECT', ?, 'SALES_SHIPMENT', ?, ?, ?, ?, 'NONE', ?, 0.00,
					?, 'CONFIRMED', ?, 'test', now(), 'test', now(), 'test', now())
				""", invoiceNo, customerId, projectId, shipment.shipmentId(), shipment.shipmentNo(), invoiceDate,
				invoiceDate.plusDays(30), amount, amount, receivableId);
	}

	private PurchaseReceiptFact insertPurchaseReceipt(String orderNo, String receiptNo, long supplierId,
			long warehouseId, long materialId, long unitId, long projectId, LocalDate businessDate,
			BigDecimal orderedQuantity, BigDecimal receivedQuantity, BigDecimal unitPrice) {
		BigDecimal orderAmount = orderedQuantity.multiply(unitPrice).setScale(2);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, purchase_mode, project_id,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'PARTIALLY_RECEIVED', 'PROJECT', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, orderNo, supplierId, businessDate.minusDays(2), businessDate.plusDays(3),
				projectId);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_included_amount, expected_arrival_date, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, materialId, unitId, orderedQuantity, receivedQuantity, unitPrice,
				unitPrice, unitPrice, orderAmount, orderAmount, businessDate.plusDays(3));
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, receiptNo, orderId, supplierId, warehouseId, businessDate);
		long receiptLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, purchase_mode, project_id,
					created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, 'PROJECT', ?, now(), now())
				returning id
				""", Long.class, receiptId, orderLineId, materialId, unitId, orderedQuantity, orderedQuantity,
				receivedQuantity, projectId);
		return new PurchaseReceiptFact(orderId, orderNo, orderLineId, receiptId, receiptNo, receiptLineId);
	}

	private long insertPayable(String payableNo, long supplierId, PurchaseReceiptFact receipt, LocalDate businessDate,
			LocalDate dueDate, BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal unpaidAmount) {
		long payableId = this.jdbcTemplate.queryForObject("""
				insert into fin_payable (
					payable_no, supplier_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, paid_amount, unpaid_amount, status, created_by, created_at, updated_by,
					updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?, ?, ?, 'PARTIALLY_PAID',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, payableNo, supplierId, receipt.receiptId(), receipt.receiptNo(), businessDate,
				dueDate, totalAmount, paidAmount, unpaidAmount);
		this.jdbcTemplate.update("""
				insert into fin_payable_source (
					payable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'PURCHASE_RECEIPT', ?, ?, ?, 1, ?)
				""", payableId, receipt.receiptId(), receipt.receiptNo(), receipt.receiptLineId(), totalAmount);
		return payableId;
	}

	private void insertPurchaseInvoice(String invoiceNo, long supplierId, long projectId, PurchaseReceiptFact receipt,
			long payableId, LocalDate invoiceDate, BigDecimal amount) {
		this.jdbcTemplate.update("""
				insert into fin_purchase_invoice (
					invoice_no, supplier_id, settlement_kind, ownership_type, project_id, source_type,
					source_id, source_no, invoice_date, due_date, invoice_type, match_status,
					tax_excluded_amount, tax_amount, tax_included_amount, status, linked_payable_id,
					created_by, created_at, updated_by, updated_at, matched_by, matched_at,
					confirmed_by, confirmed_at
				)
				values (?, ?, 'STANDARD_PURCHASE', 'PROJECT', ?, 'PURCHASE_RECEIPT', ?, ?, ?, ?,
					'NONE', 'EXCEPTION', ?, 0.00, ?, 'CONFIRMED', ?, 'test', now(), 'test', now(),
					'test', now(), 'test', now())
				""", invoiceNo, supplierId, projectId, receipt.receiptId(), receipt.receiptNo(), invoiceDate,
				invoiceDate.plusDays(30), amount, amount, payableId);
		Long invoiceId = this.jdbcTemplate.queryForObject("select id from fin_purchase_invoice where invoice_no = ?",
				Long.class, invoiceNo);
		this.jdbcTemplate.update("""
				insert into fin_purchase_invoice_line (
					purchase_invoice_id, line_no, source_line_id, purchase_order_id, purchase_order_line_id,
					material_id, unit_id, quantity, tax_rate, tax_excluded_unit_price,
					tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount,
					match_status, created_at, updated_at
				)
				select ?, 1, ?, r.order_id, rl.order_line_id, rl.material_id, rl.unit_id, rl.quantity,
				       0, (? / rl.quantity), (? / rl.quantity), ?, 0.00, ?, 'EXCEPTION', now(), now()
				from proc_purchase_receipt_line rl
				join proc_purchase_receipt r on r.id = rl.receipt_id
				where rl.id = ?
				""", invoiceId, receipt.receiptLineId(), amount, amount, amount, amount, receipt.receiptLineId());
	}

	private long insertPayment(String paymentNo, long supplierId, LocalDate paymentDate, BigDecimal amount) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_payment (
					payment_no, supplier_id, payment_date, amount, method, status,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, 'BANK_TRANSFER', 'POSTED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, paymentNo, supplierId, paymentDate, amount);
	}

	private void insertPaymentAllocation(long paymentId, long payableId, BigDecimal amount) {
		this.jdbcTemplate.update("""
				insert into fin_payment_allocation (payment_id, payable_id, allocated_amount)
				values (?, ?, ?)
				""", paymentId, payableId, amount);
	}

	private void insertPaymentBalance(long paymentId, long supplierId, Long projectId, BigDecimal originalAmount,
			BigDecimal allocatedAmount, BigDecimal availableAmount) {
		this.jdbcTemplate.update("""
				insert into fin_payment_balance (
					payment_id, supplier_id, ownership_type, project_id, original_amount, allocated_amount,
					available_amount, status, updated_at
				)
				values (?, ?, 'PROJECT', ?, ?, ?, ?, 'POSTED', now())
				""", paymentId, supplierId, projectId, originalAmount, allocatedAmount, availableAmount);
	}

	private void insertPurchaseReturnForOrder(String returnNo, long orderId, LocalDate businessDate,
			BigDecimal quantity, BigDecimal unitPrice) {
		Map<String, Object> source = this.jdbcTemplate.queryForMap("""
				select po.supplier_id, po.purchase_mode, po.project_id, pr.id as receipt_id,
				       pr.receipt_no, pr.warehouse_id, prl.id as receipt_line_id, prl.order_line_id,
				       prl.material_id, prl.unit_id
				from proc_purchase_order po
				join proc_purchase_receipt pr on pr.order_id = po.id
				join proc_purchase_receipt_line prl on prl.receipt_id = pr.id
				where po.id = ?
				order by pr.id, prl.id
				limit 1
				""", orderId);
		BigDecimal amount = quantity.multiply(unitPrice).setScale(2);
		long returnId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_return (
					return_no, supplier_id, source_receipt_id, source_receipt_no, warehouse_id, business_date,
					status, total_amount, remark, purchase_mode, project_id,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, 'POSTED', ?, '033 来源权限采购退货',
					?, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, returnNo, longValue(source, "supplier_id"), longValue(source, "receipt_id"),
				source.get("receipt_no"), longValue(source, "warehouse_id"), businessDate, amount,
				source.get("purchase_mode"), nullableLong(source, "project_id"));
		this.jdbcTemplate.update("""
				insert into proc_purchase_return_line (
					return_id, source_receipt_line_id, purchase_order_line_id, material_id, unit_id,
					line_no, returned_quantity_before, returnable_quantity_before, quantity,
					unit_price, amount, quality_status, reason, purchase_mode, project_id,
					created_at, updated_at
				)
				values (?, ?, ?, ?, ?, 1, 0, ?, ?, ?, ?, 'QUALIFIED', '033 来源权限采购退货',
					?, ?, now(), now())
				""", returnId, longValue(source, "receipt_line_id"), longValue(source, "order_line_id"),
				longValue(source, "material_id"), longValue(source, "unit_id"), quantity, quantity, unitPrice,
				amount, source.get("purchase_mode"), nullableLong(source, "project_id"));
	}

	private void insertReceivableSettlementAllocation(String allocationNo, long projectId, String customerName,
			LocalDate businessDate, BigDecimal amount) {
		Map<String, Object> source = this.jdbcTemplate.queryForMap("""
				select r.id as receivable_id, r.customer_id, rc.id as receipt_id
				from fin_receivable r
				join sal_sales_shipment sh on sh.id = r.source_id and r.source_type = 'SALES_SHIPMENT'
				join sal_sales_order so on so.id = sh.order_id
				join mst_customer customer on customer.id = r.customer_id
				join fin_receipt_allocation ra on ra.receivable_id = r.id
				join fin_receipt rc on rc.id = ra.receipt_id
				where so.project_id = ?
				and customer.name = ?
				order by r.id, rc.id
				limit 1
				""", projectId, customerName);
		long allocationId = this.jdbcTemplate.queryForObject("""
				insert into fin_settlement_allocation (
					allocation_no, settlement_side, cash_source_type, cash_source_id, party_id, ownership_type,
					project_id, business_date, total_amount, status, idempotency_key, request_fingerprint,
					remark, created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, 'RECEIVABLE', 'RECEIPT', ?, ?, 'PROJECT', ?, ?, ?, 'POSTED',
					?, ?, '033 来源权限核销', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, allocationNo, longValue(source, "receipt_id"), longValue(source, "customer_id"),
				projectId, businessDate, amount, allocationNo + "-IDEMP", "0".repeat(64));
		this.jdbcTemplate.update("""
				insert into fin_settlement_allocation_line (
					allocation_id, line_no, target_type, target_id, amount, created_at
				)
				values (?, 1, 'RECEIVABLE', ?, ?, now())
				""", allocationId, longValue(source, "receivable_id"), amount);
	}

	private long longValue(Map<String, Object> values, String name) {
		return ((Number) values.get(name)).longValue();
	}

	private Long nullableLong(Map<String, Object> values, String name) {
		Object value = values.get(name);
		return value == null ? null : ((Number) value).longValue();
	}

	private void insertStockBalance(long warehouseId, long materialId, long unitId, Long projectId,
			String ownershipType, String qualityStatus, BigDecimal quantity, BigDecimal lockedQuantity,
			String valuationState, BigDecimal inventoryAmount, BigDecimal averageUnitCost) {
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					ownership_type, project_id, valuation_state, inventory_amount, average_unit_cost,
					created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
				""", warehouseId, materialId, unitId, quantity, lockedQuantity, qualityStatus, ownershipType,
				projectId, valuationState, inventoryAmount, averageUnitCost);
	}

	private ContractCollectionSnapshotFixture insertContractCollectionBusinessSnapshot() throws Exception {
		String periodCode = "2055-09";
		ContractCollectionFixture fixture = createContractCollectionFixture(periodCode);
		LocalDate startDate = LocalDate.parse(periodCode + "-01");
		LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
		long periodId = this.jdbcTemplate.queryForObject("""
				insert into biz_business_period (
					period_code, period_name, start_date, end_date, status, locked_by, locked_at, lock_reason,
					created_at, updated_at
				)
				values (?, ?, ?, ?, 'LOCKED', 'test', now(), '033 合同回款快照脱敏测试', now(), now())
				returning id
				""", Long.class, periodCode, periodCode + "合同回款快照脱敏测试期间", startDate, endDate);
		String fingerprint = "b".repeat(64);
		long runId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_close_run (
					period_id, revision_no, status, schema_version, source_fingerprint, inventory_fingerprint,
					wip_fingerprint, project_cost_fingerprint, report_fingerprint, blocking_count, warning_count,
					warning_acknowledged, closed_by, closed_at, close_reason, created_by, updated_by
				)
				values (?, 1, 'CLOSED', 1, ?, ?, ?, ?, ?, 0, 0, true, 'test', now(), '033 合同回款快照脱敏测试',
					'test', 'test')
				returning id
				""", Long.class, periodId, fingerprint, fingerprint, fingerprint, fingerprint, fingerprint);
		long checkRunId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_close_check_run (
					run_id, period_id, revision_no, status, schema_version, source_fingerprint,
					inventory_fingerprint, wip_fingerprint, project_cost_fingerprint, report_fingerprint,
					blocking_count, warning_count, started_by
				)
				values (?, ?, 1, 'READY', 1, ?, ?, ?, ?, ?, 0, 0, 'test')
				returning id
				""", Long.class, runId, periodId, fingerprint, fingerprint, fingerprint, fingerprint, fingerprint);
		long snapshotId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_snapshot (
					run_id, period_id, revision_no, schema_version, source_check_run_id, source_fingerprint,
					inventory_fingerprint, wip_fingerprint, project_cost_fingerprint, report_fingerprint,
					generated_by
				)
				values (?, ?, 1, 1, ?, ?, ?, ?, ?, ?, 'test')
				returning id
				""", Long.class, runId, periodId, checkRunId, fingerprint, fingerprint, fingerprint, fingerprint,
				fingerprint);
		this.jdbcTemplate.update("update biz_period_close_run set latest_check_run_id = ?, snapshot_id = ? where id = ?",
				checkRunId, snapshotId, runId);
		this.jdbcTemplate.update("""
				insert into biz_period_report_snapshot (
					snapshot_id, report_code, schema_version, result_json, source_count, fingerprint
				)
				values (?, 'CONTRACT_COLLECTION', 1, cast(? as jsonb), 5, ?)
				""", snapshotId, contractCollectionSnapshotJson(fixture), fingerprint);
		return new ContractCollectionSnapshotFixture(periodCode, fixture);
	}

	private String contractCollectionSnapshotJson(ContractCollectionFixture fixture) {
		String sourceSuffix = fixture.contractNo().replace("033-CC-CON-", "");
		return """
				{
				  "summary": {
				    "contractAmount": "2000.00",
				    "invoiceAmount": "900.00",
				    "receivedAmount": "300.00",
				    "unreceivedAmount": "700.00",
				    "overdueAmount": "700.00",
				    "advanceReceiptAmount": "150.00",
				    "sourceCount": 5,
				    "analysisMode": "LIVE",
				    "freshnessStatus": "CURRENT"
				  },
				  "items": [
				    {
				      "projectId": %d,
				      "projectNo": "%s",
				      "contractId": %d,
				      "contractNo": "%s",
				      "customerName": "%s",
				      "contractAmount": "2000.00",
				      "invoiceAmount": "900.00",
				      "receivedAmount": "300.00",
				      "allocatedAmount": "300.00",
				      "unreceivedAmount": "700.00",
				      "advanceReceiptAmount": "150.00",
				      "overdueAmount": "700.00",
				      "collectionRate": "15.00",
				      "status": "OVERDUE",
				      "sourceCount": 5,
				      "traceKey": "contract-collection:CONTRACT:%d",
				      "invoiceNos": "033-CC-INV-%s",
				      "receiptNos": "033-CC-RC-%s,033-CC-ADV-%s",
				      "receivableNos": "033-CC-AR-%s",
				      "analysisMode": "LIVE",
				      "freshnessStatus": "CURRENT"
				    }
				  ],
				  "page": 1,
				  "pageSize": 999,
				  "total": 1,
				  "totalPages": 1
				}
				""".formatted(fixture.projectId(), fixture.projectNo(), fixture.contractId(), fixture.contractNo(),
				fixture.customerName(), fixture.contractId(), sourceSuffix, sourceSuffix, sourceSuffix, sourceSuffix);
	}

	private String insertProjectProfitBusinessSnapshot() throws Exception {
		int suffix = SEQUENCE.incrementAndGet();
		String periodCode = (2062 + suffix) + "-08";
		LocalDate startDate = LocalDate.parse(periodCode + "-01");
		LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
		long periodId = this.jdbcTemplate.queryForObject("""
				insert into biz_business_period (
					period_code, period_name, start_date, end_date, status, locked_by, locked_at, lock_reason,
					created_at, updated_at
				)
				values (?, ?, ?, ?, 'LOCKED', 'test', now(), '033 快照读取测试', now(), now())
				returning id
				""", Long.class, periodCode, periodCode + "快照读取测试期间", startDate, endDate);
		String fingerprint = "a".repeat(64);
		long runId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_close_run (
					period_id, revision_no, status, schema_version, source_fingerprint, inventory_fingerprint,
					wip_fingerprint, project_cost_fingerprint, report_fingerprint, blocking_count, warning_count,
					warning_acknowledged, closed_by, closed_at, close_reason, created_by, updated_by
				)
				values (?, 1, 'CLOSED', 1, ?, ?, ?, ?, ?, 0, 0, true, 'test', now(), '033 快照读取测试',
					'test', 'test')
				returning id
				""", Long.class, periodId, fingerprint, fingerprint, fingerprint, fingerprint, fingerprint);
		long checkRunId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_close_check_run (
					run_id, period_id, revision_no, status, schema_version, source_fingerprint,
					inventory_fingerprint, wip_fingerprint, project_cost_fingerprint, report_fingerprint,
					blocking_count, warning_count, started_by
				)
				values (?, ?, 1, 'READY', 1, ?, ?, ?, ?, ?, 0, 0, 'test')
				returning id
				""", Long.class, runId, periodId, fingerprint, fingerprint, fingerprint, fingerprint, fingerprint);
		long snapshotId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_snapshot (
					run_id, period_id, revision_no, schema_version, source_check_run_id, source_fingerprint,
					inventory_fingerprint, wip_fingerprint, project_cost_fingerprint, report_fingerprint,
					generated_by
				)
				values (?, ?, 1, 1, ?, ?, ?, ?, ?, ?, 'test')
				returning id
				""", Long.class, runId, periodId, checkRunId, fingerprint, fingerprint, fingerprint, fingerprint,
				fingerprint);
		this.jdbcTemplate.update("update biz_period_close_run set latest_check_run_id = ?, snapshot_id = ? where id = ?",
				checkRunId, snapshotId, runId);
		this.jdbcTemplate.update("""
				insert into biz_period_report_snapshot (
					snapshot_id, report_code, schema_version, result_json, source_count, fingerprint
				)
				values (?, 'PROJECT_PROFIT', 1, cast(? as jsonb), 2, ?)
				""", snapshotId, projectProfitSnapshotJson(), fingerprint);
		return periodCode;
	}

	private String projectProfitSnapshotJson() {
		return """
				{
				  "summary": {
				    "projectCount": 2,
				    "shipmentRevenueAmount": "300.00",
				    "invoiceRevenueAmount": "260.00",
				    "targetRevenueAmount": "400.00",
				    "projectCostAmount": "120.00",
				    "operatingGrossProfitAmount": "180.00",
				    "accountingProfitAmount": "170.00",
				    "differenceAmount": "10.00",
				    "publicUnallocatedAmount": "0.00",
				    "sourceCount": 2,
				    "amountVisible": true,
				    "completenessStatus": "COMPLETE",
				    "freshnessStatus": "CURRENT",
				    "reconciliationStatus": "DIFFERENT",
				    "finalityStatus": "PREVIEW",
				    "shipmentRevenue": "300.00",
				    "projectCostTotal": "120.00",
				    "shipmentGrossMargin": "180.00",
				    "accountingProfit": "170.00",
				    "difference": "10.00",
				    "invoiceRevenue": "260.00",
				    "targetRevenue": "400.00",
				    "unassignedAccountingAmount": "0.00",
				    "analysisMode": "LIVE",
				    "accountingRevenue": "290.00",
				    "accountingCost": "120.00"
				  },
				  "items": [
				    {
				      "projectId": 900001,
				      "projectNo": "SNAP-KEEP-001",
				      "projectName": "KEEP 快照项目",
				      "customerName": "快照客户",
				      "shipmentRevenueAmount": "120.00",
				      "invoiceRevenueAmount": "100.00",
				      "targetRevenueAmount": "180.00",
				      "projectCostAmount": "50.00",
				      "operatingGrossProfitAmount": "70.00",
				      "operatingGrossProfitRate": "58.33",
				      "accountingRevenueAmount": "115.00",
				      "accountingCostAmount": "45.00",
				      "accountingProfitAmount": "70.00",
				      "completenessStatus": "COMPLETE",
				      "freshnessStatus": "CURRENT",
				      "reconciliationStatus": "MATCHED",
				      "finalityStatus": "PREVIEW",
				      "sourceCount": 1,
				      "traceKey": "project-profit:PROJECT:900001",
				      "amountVisible": true,
				      "differenceAmount": "0.00",
				      "shipmentRevenue": "120.00",
				      "invoiceRevenue": "100.00",
				      "targetRevenue": "180.00",
				      "projectCostTotal": "50.00",
				      "shipmentGrossMargin": "70.00",
				      "shipmentGrossMarginRate": "58.33",
				      "accountingRevenue": "115.00",
				      "accountingCost": "45.00",
				      "accountingProfit": "70.00"
				    },
				    {
				      "projectId": 900002,
				      "projectNo": "SNAP-DROP-002",
				      "projectName": "DROP 快照项目",
				      "customerName": "快照客户",
				      "shipmentRevenueAmount": "180.00",
				      "invoiceRevenueAmount": "160.00",
				      "targetRevenueAmount": "220.00",
				      "projectCostAmount": "70.00",
				      "operatingGrossProfitAmount": "110.00",
				      "operatingGrossProfitRate": "61.11",
				      "accountingRevenueAmount": "175.00",
				      "accountingCostAmount": "75.00",
				      "accountingProfitAmount": "100.00",
				      "completenessStatus": "COMPLETE",
				      "freshnessStatus": "CURRENT",
				      "reconciliationStatus": "DIFFERENT",
				      "finalityStatus": "PREVIEW",
				      "sourceCount": 1,
				      "traceKey": "project-profit:PROJECT:900002",
				      "amountVisible": true,
				      "differenceAmount": "10.00",
				      "shipmentRevenue": "180.00",
				      "invoiceRevenue": "160.00",
				      "targetRevenue": "220.00",
				      "projectCostTotal": "70.00",
				      "shipmentGrossMargin": "110.00",
				      "shipmentGrossMarginRate": "61.11",
				      "accountingRevenue": "175.00",
				      "accountingCost": "75.00",
				      "accountingProfit": "100.00"
				    }
				  ],
				  "page": 1,
				  "pageSize": 999,
				  "total": 2,
				  "totalPages": 1
				}
				""";
	}

	private ProjectFixture createProjectProfitFixture(String periodCode, boolean closedFinancially) {
		return createProjectProfitFixture(periodCode, closedFinancially, true);
	}

	private ProjectFixture createProjectProfitFixture(String periodCode, boolean closedFinancially,
			boolean withAccountingFacts) {
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate periodStart = LocalDate.parse(periodCode + "-01");
		LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
		long customerId = insertCustomer("033-C-" + suffix, "033客户" + suffix);
		long projectId = this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, date '2055-01-01', date '2055-12-31', 'ACTIVE', 1200.00, 500.00,
					'test', now(), 'test', now())
				returning id
				""", Long.class, "033-PRJ-" + suffix, "033项目" + suffix, customerId, adminUserId());
		this.jdbcTemplate.update("""
				insert into prj_cost_calculation (
					project_id, calculation_no, cutoff_date, status, is_current, source_fingerprint,
					project_cost_total, wip_cost, finished_cost, delivered_cost, direct_project_cost,
					shipment_revenue, invoice_revenue, target_revenue, shipment_gross_margin,
					invoice_gross_margin, target_gross_margin, shipment_gross_margin_rate,
					invoice_gross_margin_rate, target_gross_margin_rate, margin_completeness,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, 'CONFIRMED', true, ?, 400.00, 10.00, 20.00, 300.00,
					70.00, 1000.00, 900.00, 1200.00, 600.00, 500.00, 800.00, 0.600000,
					0.555556, 0.666667, 'COMPLETE', 'test', now(), 'test', now(), 'test', now())
				""", projectId, "033-PCC-" + suffix, periodEnd, "033-fp-" + suffix);
		this.jdbcTemplate.update("""
				insert into prj_cost_source_line (
					calculation_id, project_id, cost_category, cost_stage, entry_type, source_type, source_id,
					source_no, source_status, business_date, quantity, unit_cost, source_amount,
					calculated_amount, source_fingerprint
				)
				select id, project_id, 'MATERIAL', 'DELIVERED', 'SOURCE_TO_WIP', 'SALES_SHIPMENT', ?,
					'033-SRC-' || ?, 'ACTUAL', ?, 1.000000, 400.000000, 400.00,
					400.00, source_fingerprint
				from prj_cost_calculation
				where project_id = ?
				""", projectId, suffix, periodStart.plusDays(9), projectId);

		long ledgerId = this.jdbcTemplate.queryForObject("select id from gl_ledger where code = 'MAIN'", Long.class);
		long periodId = this.jdbcTemplate.queryForObject("""
				insert into gl_accounting_period (
					ledger_id, period_code, start_date, end_date, status, created_by, created_at, updated_by,
					updated_at
				)
				values (?, ?, ?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, ledgerId, periodCode, periodStart, periodEnd, closedFinancially ? "CLOSED" : "OPEN");
		if (withAccountingFacts) {
			insertLedgerEntry(ledgerId, periodId, projectId, "6001", BigDecimal.ZERO, new BigDecimal("800.00"), true,
					"033主营收入" + suffix, 1, periodStart.plusDays(19));
			insertLedgerEntry(ledgerId, periodId, projectId, "6401", new BigDecimal("300.00"), BigDecimal.ZERO, true,
					"033主营成本" + suffix, 2, periodStart.plusDays(19));
			insertLedgerEntry(ledgerId, periodId, projectId, "6001", BigDecimal.ZERO, new BigDecimal("111.00"), false,
					"033公共收入" + suffix, 3, periodStart.plusDays(19));
		}
		if (closedFinancially) {
			insertFinancialCloseRun(ledgerId, periodId, suffix);
		}
		return new ProjectFixture(projectId, "033-PRJ-" + suffix);
	}

	private void updateProjectCostCompleteness(long projectId, String completenessStatus) {
		this.jdbcTemplate.update("""
				update prj_cost_calculation
				set margin_completeness = ?,
				    updated_at = now()
				where project_id = ?
				""", completenessStatus, projectId);
	}

	private void updateProjectCostCutoffDate(long projectId, LocalDate cutoffDate) {
		this.jdbcTemplate.update("""
				update prj_cost_calculation
				set cutoff_date = ?,
				    updated_at = now()
				where project_id = ?
				""", cutoffDate, projectId);
	}

	private String insertDescendantAccount(String parentCode, String childCode, String childName) {
		long parentId = this.jdbcTemplate.queryForObject("select id from gl_account where code = ?", Long.class,
				parentCode);
		this.jdbcTemplate.update("update gl_account set is_leaf = false where id = ?", parentId);
		this.jdbcTemplate.update("""
				insert into gl_account (
					ledger_id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable,
					enabled, template_source, created_by, created_at, updated_by, updated_at
				)
				select ledger_id, id, ?, ?, category, balance_direction, level_no + 1, true, true,
				       true, 'STAGE_033_TEST', 'test', now(), 'test', now()
				from gl_account
				where id = ?
				""", childCode, childName, parentId);
		return childCode;
	}

	private void insertReopenedBusinessPeriod(String periodCode) {
		LocalDate startDate = LocalDate.parse(periodCode + "-01");
		LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
		long periodId = this.jdbcTemplate.queryForObject("""
				insert into biz_business_period (
					period_code, period_name, start_date, end_date, status, locked_by, locked_at, lock_reason,
					created_at, updated_at
				)
				values (?, ?, ?, ?, 'LOCKED', 'test', now(), '033 重开新鲜度测试', now(), now())
				returning id
				""", Long.class, periodCode, periodCode + "重开测试期间", startDate, endDate);
		String fingerprint = "b".repeat(64);
		this.jdbcTemplate.update("""
				insert into biz_period_close_run (
					period_id, revision_no, status, source_fingerprint, inventory_fingerprint,
					wip_fingerprint, project_cost_fingerprint, report_fingerprint, blocking_count, warning_count,
					reopened_by, reopened_at, reopen_reason, created_by, updated_by
				)
				values (?, 1, 'REOPENED', ?, ?, ?, ?, ?, 0, 0, 'test', now(), '033 重开新鲜度测试',
					'test', 'test')
				""", periodId, fingerprint, fingerprint, fingerprint, fingerprint, fingerprint);
	}

	private void reopenFinancialClose(String periodCode) {
		this.jdbcTemplate.update("""
				update fin_close_run r
				set status = 'REOPENED',
				    reopened_by = 'test',
				    reopened_at = now(),
				    reopen_reason = '033 反结账新鲜度测试',
				    updated_at = now()
				from gl_accounting_period p
				where p.id = r.period_id
				and p.period_code = ?
				and r.status = 'CLOSED'
				""", periodCode);
	}

	private void insertLedgerEntry(long ledgerId, long periodId, long projectId, String accountCode,
			BigDecimal debitAmount, BigDecimal creditAmount, boolean withProjectAuxiliary, String summary, int lineNo,
			LocalDate voucherDate) {
		long accountId = this.jdbcTemplate.queryForObject("select id from gl_account where code = ?", Long.class,
				accountCode);
		String accountName = this.jdbcTemplate.queryForObject("select name from gl_account where id = ?", String.class,
				accountId);
		String direction = this.jdbcTemplate.queryForObject("select balance_direction from gl_account where id = ?",
				String.class, accountId);
		int suffix = SEQUENCE.incrementAndGet();
		long voucherId = this.jdbcTemplate.queryForObject("""
				insert into gl_voucher (
					ledger_id, accounting_period_id, draft_no, voucher_type, voucher_date, status, summary,
					source_type, source_fingerprint, created_by, created_at, updated_by, updated_at,
					submitted_by, submitted_at, posted_by, posted_at, debit_total, credit_total,
					voucher_number, voucher_no
				)
				values (?, ?, ?, 'GENERAL', ?, 'DRAFT', ?, 'MANUAL', ?, 'test', now(), 'test', now(),
					null, null, null, null, ?, ?, null, null)
				returning id
				""", Long.class, ledgerId, periodId, "033-GL-D-" + suffix, voucherDate, summary,
				"033-gl-fp-" + suffix, debitAmount.max(creditAmount), debitAmount.max(creditAmount));
		long voucherLineId = this.jdbcTemplate.queryForObject("""
				insert into gl_voucher_line (
					voucher_id, line_no, summary, account_id, account_code, account_name, account_category,
					account_balance_direction, debit_amount, credit_amount, created_at
				)
				values (?, ?, ?, ?, ?, ?, 'PROFIT_LOSS', ?, ?, ?, now())
				returning id
				""", Long.class, voucherId, lineNo, summary, accountId, accountCode, accountName, direction,
				debitAmount, creditAmount);
		this.jdbcTemplate.update("""
				update gl_voucher
				set status = 'POSTED',
					submitted_by = 'test',
					submitted_at = now(),
					posted_by = 'test',
					posted_at = now(),
					voucher_number = ?,
					voucher_no = ?,
					updated_by = 'test',
					updated_at = now()
				where id = ?
				""", suffix, "记-" + suffix, voucherId);
		String auxiliary = withProjectAuxiliary
				? """
						[{"dimensionCode":"PROJECT","objectId":%d,"objectCode":"033-project","objectName":"033项目"}]
						""".formatted(projectId).strip()
				: "[]";
		this.jdbcTemplate.update("""
				insert into gl_ledger_entry (
					ledger_id, period_id, voucher_id, voucher_line_id, voucher_date, voucher_no, voucher_word,
					voucher_number, line_no, summary, account_id, account_code, account_name, balance_direction,
					voucher_type, debit_amount, credit_amount, auxiliary_snapshot, source_type, source_id,
					source_no, posted_by, posted_at, created_at
				)
				values (?, ?, ?, ?, ?, ?, '记', ?, ?, ?, ?, ?, ?, ?, 'GENERAL', ?, ?,
					cast(? as jsonb), 'MANUAL', ?, ?, 'test', now(), now())
				""", ledgerId, periodId, voucherId, voucherLineId, voucherDate, "记-" + suffix, suffix, lineNo,
				summary, accountId, accountCode, accountName, direction, debitAmount, creditAmount, auxiliary,
				projectId, "033-GL-SRC-" + suffix);
	}

	private void insertFinancialCloseRun(long ledgerId, long periodId, int suffix) {
		long checkRunId = this.jdbcTemplate.queryForObject("""
				insert into fin_close_check_run (
					ledger_id, period_id, status, close_version, source_fingerprint, created_by, completed_at
				)
				values (?, ?, 'CONSUMED', 1, ?, 'test', now())
				returning id
				""", Long.class, ledgerId, periodId, "033-fin-check-" + suffix);
		this.jdbcTemplate.update("""
				insert into fin_close_run (
					ledger_id, period_id, check_run_id, close_version, status, source_fingerprint,
					closed_by, closed_at, close_reason
				)
				values (?, ?, ?, 1, 'CLOSED', ?, 'test', now(), '033测试关闭')
				""", ledgerId, periodId, checkRunId, "033-fin-close-" + suffix);
	}

	private AuthenticatedSession createUserAndLogin(String usernamePrefix, List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, "REPORT_033_ROLE_" + suffix, "033报表角色" + suffix, "033报表角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), username);
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

	private JsonNode findItemByText(JsonNode items, String field, String expected) {
		for (JsonNode item : items) {
			if (item.has(field) && expected.equals(item.get(field).asText())) {
				return item;
			}
		}
		throw new AssertionError("未找到 " + field + "=" + expected + " 的候选行：" + items);
	}

	private long insertUnit(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, 6, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertWarehouse(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (code, name, warehouse_type, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertCustomer(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
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

	private long insertMaterialCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "033分类" + code);
	}

	private long insertMaterial(String code, String name, String materialType, String sourceType, long categoryId,
			long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (
					code, name, specification, material_type, source_type, category_id, unit_id, status,
					cost_category, inventory_valuation_category, inventory_value_enabled, project_cost_enabled,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, '033规格', ?, ?, ?, ?, 'ENABLED', 'DIRECT_MATERIAL', 'VALUATED_MATERIAL',
					true, true, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, materialType, sourceType, categoryId, unitId);
	}

	private long adminUserId() {
		return this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'", Long.class);
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

	private List<String> recursiveValues(JsonNode node, String fieldName) {
		List<String> values = new java.util.ArrayList<>();
		collectValues(node, fieldName, values);
		return values;
	}

	private List<String> glLedgerTraceStatuses(JsonNode traces) {
		List<String> statuses = new java.util.ArrayList<>();
		traces.get("items").forEach((item) -> {
			if ("GL_LEDGER_ENTRY".equals(item.get("sourceType").asText())) {
				JsonNode status = item.get("status");
				statuses.add(status == null || status.isNull() ? null : status.asText());
			}
		});
		return statuses;
	}

	private void collectValues(JsonNode node, String fieldName, List<String> values) {
		if (node == null || node.isNull()) {
			return;
		}
		if (node.isObject()) {
			node.properties().forEach((entry) -> {
				if (entry.getKey().equals(fieldName) && !entry.getValue().isNull()) {
					values.add(entry.getValue().asText());
				}
				collectValues(entry.getValue(), fieldName, values);
			});
		}
		else if (node.isArray()) {
			node.forEach((item) -> collectValues(item, fieldName, values));
		}
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

	private record ProjectFixture(long projectId, String projectNo) {
	}

	private record ContractCollectionFixture(long projectId, String projectNo, long contractId, String contractNo,
			String customerName) {
	}

	private record ContractCollectionSnapshotFixture(String periodCode, ContractCollectionFixture contract) {
	}

	private record ProcurementVarianceFixture(long projectId, String projectNo, long orderId, String orderNo,
			String supplierName) {
	}

	private record InventoryCapitalFixture(String projectNo, String warehouseName, String valuedMaterialName,
			String unvaluedMaterialName) {
	}

	private record ReceivablePayableFixture(long projectId, String projectNo, String customerName,
			String supplierName) {
	}

	private record SalesShipmentFact(long shipmentId, String shipmentNo, long shipmentLineId) {
	}

	private record PurchaseReceiptFact(long orderId, String orderNo, long orderLineId, long receiptId,
			String receiptNo, long receiptLineId) {
	}

}
