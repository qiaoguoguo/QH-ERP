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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=project-cost-stage029")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectCostStage029Tests extends PostgresIntegrationTest {

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
	void 项目成本核算汇总838并形成9162发货毛利且不改写上游来源() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		StageFixture fixture = createFullCostFixture("029_FULL_", true);
		Map<String, String> upstreamBefore = upstreamRowSummaries();

		ResponseEntity<String> calculationResponse = exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey", "pc-calc-" + fixture.projectId()), admin);
		assertThat(calculationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode calculation = data(calculationResponse);

		assertThat(calculation.get("status").asText()).isEqualTo("CALCULATED");
		assertThat(calculation.get("calculationStatus").asText()).isEqualTo("CALCULATED");
		assertThat(calculation.get("freshnessStatus").asText()).isEqualTo("CURRENT");
		assertThat(calculation.get("completenessStatus").asText()).isEqualTo("COMPLETE");
		assertDecimal(calculation, "projectCostTotal", "838.00");
		assertDecimal(calculation, "totalCost", "838.00");
		assertDecimal(calculation, "wipCost", "0.00");
		assertDecimal(calculation, "finishedCost", "0.00");
		assertDecimal(calculation, "deliveredCost", "588.00");
		assertDecimal(calculation, "directProjectCost", "250.00");
		assertDecimal(calculation, "shipmentRevenue", "10000.00");
		assertDecimal(calculation, "shipmentPretaxRevenue", "10000.00");
		assertDecimal(calculation, "invoiceRevenue", "10000.00");
		assertDecimal(calculation, "targetRevenue", "12000.00");
		assertDecimal(calculation, "shipmentGrossMargin", "9162.00");
		assertDecimal(calculation, "invoiceGrossMargin", "9162.00");
		assertDecimal(calculation, "targetGrossMargin", "11162.00");
		assertThat(calculation.get("marginCompleteness").asText()).isEqualTo("COMPLETE");
		assertThat(calculation.get("sourceFingerprint").asText()).isNotBlank();
		assertThat(calculation.get("actionDisabledReasons").isObject()).isTrue();
		assertThat(calculation.get("isCurrent").booleanValue()).isFalse();
		assertThat(upstreamRowSummaries()).isEqualTo(upstreamBefore);

		long calculationId = calculation.get("id").longValue();
		assertFreshnessAcrossCalculationDtos(admin, fixture.projectId(), calculationId, "CURRENT");
		JsonNode sources = data(get("/api/admin/cost/project-cost-calculations/" + calculationId
				+ "/sources?page=1&pageSize=100",
				admin)).get("items");
		assertThat(sources.size()).isGreaterThanOrEqualTo(8);
		assertThat(dbSumByCategory(calculationId, "MATERIAL")).isEqualByComparingTo("168.00");
		assertThat(sumByCategory(sources, "MATERIAL")).as("API source material sum").isEqualByComparingTo("168.00");
		assertThat(sumByCategory(sources, "LABOR")).isEqualByComparingTo("300.00");
		assertThat(sumByCategory(sources, "OUTSOURCING")).isEqualByComparingTo("120.00");
		assertThat(sumByCategory(sources, "PROJECT_EXPENSE")).isEqualByComparingTo("200.00");
		assertThat(sumByCategory(sources, "MANUFACTURING_OVERHEAD")).isEqualByComparingTo("50.00");
		assertThat(sourceStatuses(sources)).contains("ACTUAL", "ADJUSTED");
		assertThat(sourceKeys(sources)).hasSize(sources.size());
		assertThat(sourceTypes(sources)).doesNotContain("MFG_COST_RECORD_MATERIAL");

		JsonNode variances = data(get("/api/admin/cost/project-cost-calculations/" + calculationId + "/variances",
				admin)).get("items");
		assertThat(hasVariance(variances, "OUTSOURCING_ACTUAL_VARIANCE")).isTrue();
		ResponseEntity<String> globalVarianceResponse = get("/api/admin/cost/project-cost-variances?projectId="
				+ fixture.projectId()
				+ "&severity=WARNING&varianceType=OUTSOURCING_ACTUAL_VARIANCE&status=OPEN&sourceRestricted=false&page=1&pageSize=20",
				admin);
		assertThat(globalVarianceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode globalVariances = data(globalVarianceResponse).get("items");
		assertThat(globalVariances.size()).isOne();
		JsonNode globalVariance = globalVariances.get(0);
		assertThat(globalVariance.get("calculationId").longValue()).isEqualTo(calculationId);
		assertThat(globalVariance.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(globalVariance.get("varianceType").asText()).isEqualTo("OUTSOURCING_ACTUAL_VARIANCE");
		assertThat(globalVariance.get("sourceRestricted").booleanValue()).isFalse();
		assertDecimal(globalVariance, "varianceAmount", "20.00");

		JsonNode entries = data(get("/api/admin/cost/project-cost-calculations/" + calculationId + "/entries",
				admin)).get("items");
		assertThat(hasEntry(entries, "SOURCE_TO_WIP")).isTrue();
		assertThat(hasEntry(entries, "WIP_TO_FINISHED")).isTrue();
		assertThat(hasEntry(entries, "FINISHED_TO_DELIVERED")).isTrue();
		assertThat(hasEntry(entries, "PROJECT_DIRECT")).isTrue();

		JsonNode confirmed = data(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + calculationId + "/confirm",
				Map.of("version", calculation.get("version").longValue(), "sourceFingerprint",
						calculation.get("sourceFingerprint").asText(), "idempotencyKey",
						"pc-confirm-" + calculationId),
				admin));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(confirmed.get("isCurrent").booleanValue()).isTrue();
		assertThat(confirmed.get("freshnessStatus").asText()).isEqualTo("CURRENT");
		assertError(exchange(HttpMethod.PUT, "/api/admin/cost/project-cost-calculations/" + calculationId
				+ "/recalculate", Map.of("version", confirmed.get("version").longValue(), "idempotencyKey",
						"pc-recalc-readonly-" + calculationId), admin), HttpStatus.CONFLICT,
				"PROJECT_COST_ACTION_NOT_ALLOWED");
		assertError(exchange(HttpMethod.PUT, "/api/admin/cost/project-cost-calculations/" + calculationId
				+ "/cancel", Map.of("version", confirmed.get("version").longValue(), "idempotencyKey",
						"pc-cancel-readonly-" + calculationId), admin), HttpStatus.CONFLICT,
				"PROJECT_COST_ACTION_NOT_ALLOWED");
		assertThat(currentCalculationIds(fixture.projectId())).containsExactly(calculationId);
		assertThat(upstreamRowSummaries()).isEqualTo(upstreamBefore);

		JsonNode secondCalculation = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey", "pc-calc-second-" + fixture.projectId()),
				admin));
		long secondCalculationId = secondCalculation.get("id").longValue();
		assertThat(secondCalculationId).isNotEqualTo(calculationId);
		assertThat(secondCalculation.get("status").asText()).isEqualTo("CALCULATED");
		assertThat(secondCalculation.get("isCurrent").booleanValue()).isFalse();
		assertThat(secondCalculation.get("freshnessStatus").asText()).isEqualTo("CURRENT");
		assertFreshnessAcrossCalculationDtos(admin, fixture.projectId(), secondCalculationId, "CURRENT");
		JsonNode firstWhileSecondCalculated = data(get("/api/admin/cost/project-cost-calculations/"
				+ calculationId, admin));
		assertThat(firstWhileSecondCalculated.get("isCurrent").booleanValue()).isTrue();
		assertThat(firstWhileSecondCalculated.get("freshnessStatus").asText()).isEqualTo("CURRENT");

		JsonNode secondConfirmed = data(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + secondCalculationId + "/confirm",
				Map.of("version", secondCalculation.get("version").longValue(), "sourceFingerprint",
						secondCalculation.get("sourceFingerprint").asText(), "idempotencyKey",
						"pc-confirm-second-" + secondCalculationId),
				admin));
		assertThat(secondConfirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(secondConfirmed.get("isCurrent").booleanValue()).isTrue();
		assertThat(secondConfirmed.get("freshnessStatus").asText()).isEqualTo("CURRENT");
		JsonNode firstAfterSecondConfirmed = data(get("/api/admin/cost/project-cost-calculations/" + calculationId,
				admin));
		assertThat(firstAfterSecondConfirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(firstAfterSecondConfirmed.get("isCurrent").booleanValue()).isFalse();
		assertThat(firstAfterSecondConfirmed.get("freshnessStatus").asText()).isEqualTo("CURRENT");
		assertThat(currentCalculationIds(fixture.projectId())).containsExactly(secondCalculationId);
		assertThat(upstreamRowSummaries()).isEqualTo(upstreamBefore);
	}

	@Test
	void 确认前来源变化必须返回409并要求重新计算() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		StageFixture fixture = createFullCostFixture("029_CHANGED_", false);

		ResponseEntity<String> calculationResponse = exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey", "pc-calc-change-" + fixture.projectId()),
				admin);
		assertThat(calculationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode calculation = data(calculationResponse);
		long calculationId = calculation.get("id").longValue();
		assertThat(calculation.get("isCurrent").booleanValue()).isFalse();
		assertFreshnessAcrossCalculationDtos(admin, fixture.projectId(), calculationId, "CURRENT");
		JsonNode recalculated = data(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + calculationId + "/recalculate",
				Map.of("version", calculation.get("version").longValue(), "idempotencyKey",
						"pc-recalc-change-" + calculationId),
				admin));
		assertThat(recalculated.get("isCurrent").booleanValue()).isFalse();
		assertThat(recalculated.get("freshnessStatus").asText()).isEqualTo("CURRENT");
		assertFreshnessAcrossCalculationDtos(admin, fixture.projectId(), calculationId, "CURRENT");

		this.jdbcTemplate.update("update inv_value_movement set inventory_amount = 49.00 where id = ?",
				fixture.firstValueMovementId());
		JsonNode staleCalculation = data(get("/api/admin/cost/project-cost-calculations/" + calculationId, admin));
		assertThat(staleCalculation.get("status").asText()).isEqualTo("CALCULATED");
		assertThat(staleCalculation.get("isCurrent").booleanValue()).isFalse();
		assertThat(staleCalculation.get("freshnessStatus").asText()).isEqualTo("STALE");
		assertFreshnessAcrossCalculationDtos(admin, fixture.projectId(), calculationId, "STALE");

		assertError(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + calculationId + "/confirm",
				Map.of("version", recalculated.get("version").longValue(), "sourceFingerprint",
						recalculated.get("sourceFingerprint").asText(), "idempotencyKey",
						"pc-confirm-change-" + calculationId),
				admin), HttpStatus.CONFLICT, "PROJECT_COST_SOURCE_CHANGED");
		JsonNode staleAfterRejectedConfirm = data(get("/api/admin/cost/project-cost-calculations/" + calculationId,
				admin));
		assertThat(staleAfterRejectedConfirm.get("status").asText()).isEqualTo("CALCULATED");
		assertThat(staleAfterRejectedConfirm.get("isCurrent").booleanValue()).isFalse();
		assertThat(staleAfterRejectedConfirm.get("freshnessStatus").asText()).isEqualTo("STALE");
	}

	@Test
	void 收入事实变化必须让核算快照过期并在确认时返回来源变化409() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		assertRevenueChangeMakesCalculationStale(admin, "029_REV_SHIP_",
				(fixture) -> this.jdbcTemplate.update("""
						update sal_sales_shipment_line
						set tax_excluded_amount = tax_excluded_amount + 1.00, updated_at = now()
						where shipment_id in (
							select sh.id
							from sal_sales_shipment sh
							join sal_sales_order so on so.id = sh.order_id
							where so.project_id = ?
						)
						""", fixture.projectId()));
		assertRevenueChangeMakesCalculationStale(admin, "029_REV_INV_",
				(fixture) -> this.jdbcTemplate.update("""
						update fin_sales_invoice
						set tax_excluded_amount = tax_excluded_amount + 1.00,
						    tax_included_amount = tax_included_amount + 1.00,
						    updated_at = now()
						where project_id = ?
						""", fixture.projectId()));
		assertRevenueChangeMakesCalculationStale(admin, "029_REV_TARGET_",
				(fixture) -> this.jdbcTemplate.update("""
						update sal_project
						set target_revenue = target_revenue + 1.00, updated_at = now()
						where id = ?
						""", fixture.projectId()));
	}

	@Test
	void 取消较新运行后工作台和详情必须选择有效latest且合法项目仍可重新计算() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		StageFixture confirmedFixture = createFullCostFixture("029_CANCEL_CONFIRMED_", false);
		JsonNode confirmedCalculation = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + confirmedFixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey",
						"pc-calc-cancel-confirmed-" + confirmedFixture.projectId()),
				admin));
		JsonNode confirmed = data(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + confirmedCalculation.get("id").longValue()
						+ "/confirm",
				Map.of("version", confirmedCalculation.get("version").longValue(), "sourceFingerprint",
						confirmedCalculation.get("sourceFingerprint").asText(), "idempotencyKey",
						"pc-confirm-cancel-base-" + confirmedCalculation.get("id").longValue()),
				admin));
		long confirmedId = confirmed.get("id").longValue();
		JsonNode newerCalculation = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + confirmedFixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey",
						"pc-calc-cancel-newer-" + confirmedFixture.projectId()),
				admin));
		long newerCalculationId = newerCalculation.get("id").longValue();
		JsonNode cancelledNewer = data(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + newerCalculationId + "/cancel",
				Map.of("version", newerCalculation.get("version").longValue(), "idempotencyKey",
						"pc-cancel-newer-" + newerCalculationId),
				admin));
		assertThat(cancelledNewer.get("status").asText()).isEqualTo("CANCELLED");
		assertEffectiveLatest(admin, confirmedFixture.projectId(), confirmedId, "CONFIRMED");
		JsonNode confirmedDetail = data(get("/api/admin/cost/project-costs/projects/"
				+ confirmedFixture.projectId(), admin));
		assertThat(calculationSummaryStatuses(confirmedDetail.get("calculations"))).contains("CANCELLED",
				"CONFIRMED");

		StageFixture cancelledOnlyFixture = createFullCostFixture("029_CANCEL_ONLY_", false);
		JsonNode onlyCalculation = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + cancelledOnlyFixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey",
						"pc-calc-cancel-only-" + cancelledOnlyFixture.projectId()),
				admin));
		JsonNode cancelledOnly = data(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + onlyCalculation.get("id").longValue() + "/cancel",
				Map.of("version", onlyCalculation.get("version").longValue(), "idempotencyKey",
						"pc-cancel-only-" + onlyCalculation.get("id").longValue()),
				admin));
		assertThat(cancelledOnly.get("status").asText()).isEqualTo("CANCELLED");

		JsonNode workbenchItems = data(get("/api/admin/cost/project-costs?projectId="
				+ cancelledOnlyFixture.projectId() + "&page=1&pageSize=20", admin)).get("items");
		assertThat(workbenchItems.size()).isOne();
		JsonNode workbench = workbenchItems.get(0);
		assertThat(workbench.get("calculationId").isNull()).isTrue();
		assertThat(workbench.get("status").asText()).isEqualTo("DRAFT");
		assertThat(actionCodes(workbench.get("allowedActions"))).contains("CALCULATE");

		JsonNode detail = data(get("/api/admin/cost/project-costs/projects/" + cancelledOnlyFixture.projectId(),
				admin));
		assertThat(detail.get("calculationId").isNull()).isTrue();
		assertThat(detail.get("latestCalculationId").isNull()).isTrue();
		assertThat(detail.get("status").asText()).isEqualTo("DRAFT");
		assertThat(actionCodes(detail.get("allowedActions"))).contains("CALCULATE");
		assertThat(calculationSummaryStatuses(detail.get("calculations"))).containsExactly("CANCELLED");
	}

	@Test
	void 多项目freshness筛选必须返回准确total和分页且三个DTO口径一致() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String prefix = "029_FRESH_PAGE_" + SEQUENCE.incrementAndGet() + "_";
		StageFixture currentA = createFullCostFixture(prefix + "A_", false);
		StageFixture currentB = createFullCostFixture(prefix + "B_", false);
		StageFixture currentC = createFullCostFixture(prefix + "C_", false);
		StageFixture stale = createFullCostFixture(prefix + "STALE_", false);
		long currentAId = calculateProject(admin, currentA.projectId(), "pc-calc-fresh-a-" + currentA.projectId());
		long currentBId = calculateProject(admin, currentB.projectId(), "pc-calc-fresh-b-" + currentB.projectId());
		long currentCId = calculateProject(admin, currentC.projectId(), "pc-calc-fresh-c-" + currentC.projectId());
		long staleId = calculateProject(admin, stale.projectId(), "pc-calc-fresh-stale-" + stale.projectId());
		this.jdbcTemplate.update("""
				update sal_project
				set target_revenue = target_revenue + 1.00, updated_at = now()
				where id = ?
				""", stale.projectId());

		JsonNode currentPage1 = data(get("/api/admin/cost/project-costs?keyword=" + prefix
				+ "&freshnessStatus=CURRENT&page=1&pageSize=2", admin));
		assertThat(currentPage1.get("total").longValue()).isEqualTo(3);
		assertThat(currentPage1.get("page").intValue()).isEqualTo(1);
		assertThat(currentPage1.get("pageSize").intValue()).isEqualTo(2);
		assertThat(currentPage1.get("totalPages").intValue()).isEqualTo(2);
		assertThat(currentPage1.get("items").size()).isEqualTo(2);
		assertThat(freshnessStatuses(currentPage1.get("items"))).containsOnly("CURRENT");

		JsonNode currentPage2 = data(get("/api/admin/cost/project-costs?keyword=" + prefix
				+ "&freshnessStatus=CURRENT&page=2&pageSize=2", admin));
		assertThat(currentPage2.get("total").longValue()).isEqualTo(3);
		assertThat(currentPage2.get("items").size()).isOne();
		assertThat(freshnessStatuses(currentPage2.get("items"))).containsOnly("CURRENT");
		assertThat(projectIds(currentPage1.get("items"))).doesNotContain(stale.projectId());
		assertThat(projectIds(currentPage2.get("items"))).doesNotContain(stale.projectId());

		JsonNode stalePage = data(get("/api/admin/cost/project-costs?keyword=" + prefix
				+ "&freshnessStatus=STALE&page=1&pageSize=20", admin));
		assertThat(stalePage.get("total").longValue()).isEqualTo(1);
		assertThat(stalePage.get("items").size()).isOne();
		JsonNode staleWorkbench = stalePage.get("items").get(0);
		assertThat(staleWorkbench.get("projectId").longValue()).isEqualTo(stale.projectId());
		assertThat(staleWorkbench.get("calculationId").longValue()).isEqualTo(staleId);
		assertThat(staleWorkbench.get("freshnessStatus").asText()).isEqualTo("STALE");
		assertFreshnessAcrossCalculationDtos(admin, stale.projectId(), staleId, "STALE");
		assertFreshnessAcrossCalculationDtos(admin, currentA.projectId(), currentAId, "CURRENT");
		assertFreshnessAcrossCalculationDtos(admin, currentB.projectId(), currentBId, "CURRENT");
		assertFreshnessAcrossCalculationDtos(admin, currentC.projectId(), currentCId, "CURRENT");
	}

	@Test
	void 数量型人工来源不得按零计入且阻止确认() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		StageFixture fixture = createFullCostFixture("029_UNPRICED_", false);
		this.jdbcTemplate.update("""
				insert into mfg_cost_record (
					record_no, work_order_id, product_material_id, cost_type, source_type,
					source_document_type, source_document_no, source_document_id, quantity, amount, basis_type,
					business_date, status, remark, recorded_by, recorded_at, created_by, created_at, updated_by,
					updated_at
				)
				values (?, ?, ?, 'LABOR', 'AUTO_PRODUCTION', 'PRODUCTION_WORK_REPORT', ?, ?, 1.000000, null,
					'SOURCE_QUANTITY_ONLY', date '2026-07-15', 'ACTIVE', '数量型报工', 'test', now(), 'test',
					now(), 'test', now())
				""", "029-UNPRICED-" + SEQUENCE.incrementAndGet(), fixture.workOrderId(),
				fixture.productMaterialId(), "WR-UNPRICED-" + fixture.projectId(), 8_000_000L + fixture.projectId());

		ResponseEntity<String> calculationResponse = exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey", "pc-calc-unpriced-" + fixture.projectId()),
				admin);
		assertThat(calculationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode calculation = data(calculationResponse);

		JsonNode variances = data(get("/api/admin/cost/project-cost-calculations/"
				+ calculation.get("id").longValue() + "/variances", admin)).get("items");
		assertThat(hasVariance(variances, "SOURCE_UNPRICED")).isTrue();
		assertThat(calculation.get("marginCompleteness").asText()).isEqualTo("INCOMPLETE");
		assertThat(actionCodes(calculation.get("allowedActions"))).doesNotContain("CONFIRM");
		assertThat(calculation.get("actionDisabledReasons").get("CONFIRM").asText()).contains("未定价");
		assertError(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + calculation.get("id").longValue() + "/confirm",
				Map.of("version", calculation.get("version").longValue(), "sourceFingerprint",
						calculation.get("sourceFingerprint").asText(), "idempotencyKey",
						"pc-confirm-unpriced-" + calculation.get("id").longValue()),
				admin), HttpStatus.CONFLICT, "PROJECT_COST_LABOR_UNPRICED");
	}

	@Test
	void 净发货超过可匹配完工量必须生成阻断差异并使用稳定确认错误码() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		StageFixture fixture = createFullCostFixture("029_DELIVERY_UNMATCHED_", false);
		this.jdbcTemplate.update("""
				delete from mfg_completion_receipt
				where work_order_id = ?
				""", fixture.workOrderId());

		JsonNode calculation = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey",
						"pc-calc-delivery-unmatched-" + fixture.projectId()),
				admin));
		JsonNode variances = data(get("/api/admin/cost/project-cost-calculations/"
				+ calculation.get("id").longValue() + "/variances", admin)).get("items");
		JsonNode variance = findVariance(variances, "DELIVERY_WITHOUT_FINISHED_COST");
		assertThat(variance.get("severity").asText()).isEqualTo("BLOCKING");
		assertThat(variance.get("status").asText()).isEqualTo("OPEN");
		assertThat(actionCodes(calculation.get("allowedActions"))).doesNotContain("CONFIRM");
		assertThat(calculation.get("actionDisabledReasons").get("CONFIRM").asText()).contains("发货");

		assertError(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + calculation.get("id").longValue() + "/confirm",
				Map.of("version", calculation.get("version").longValue(), "sourceFingerprint",
						calculation.get("sourceFingerprint").asText(), "idempotencyKey",
						"pc-confirm-delivery-unmatched-" + calculation.get("id").longValue()),
				admin), HttpStatus.CONFLICT, "PROJECT_COST_DELIVERY_UNMATCHED");
	}

	@Test
	void 材料来源金额必须叠加库存估值权限脱敏() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		StageFixture fixture = createFullCostFixture("029_MATERIAL_VALUATION_MASK_", false);
		JsonNode calculation = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey",
						"pc-calc-material-mask-" + fixture.projectId()),
				admin));
		AuthenticatedSession noInventoryValuation = createUserAndLogin("pc-no-inv-val-", "PC_NO_INV_VAL_",
				List.of("cost:project-cost:view", "cost:project-cost:source-view",
						"cost:project-cost:amount-view", "production:issue:view"));

		JsonNode sources = data(get("/api/admin/cost/project-cost-calculations/"
				+ calculation.get("id").longValue()
				+ "/sources?sourceType=PRODUCTION_MATERIAL_ISSUE&page=1&pageSize=20",
				noInventoryValuation)).get("items");
		assertThat(sources.size()).isGreaterThan(0);
		JsonNode source = sources.get(0);
		assertThat(source.get("sourceVisible").booleanValue()).isTrue();
		assertThat(source.get("amountVisible").booleanValue()).isFalse();
		assertThat(source.get("sourceId").isNull()).isFalse();
		assertThat(source.get("sourceLineId").isNull()).isFalse();
		assertThat(source.get("quantity").isNull()).isFalse();
		assertThat(source.get("unitCost").isNull()).isTrue();
		assertThat(source.get("unitPrice").isNull()).isTrue();
		assertThat(source.get("sourceAmount").isNull()).isTrue();
		assertThat(source.get("calculatedAmount").isNull()).isTrue();
		assertThat(source.get("amount").isNull()).isTrue();
	}

	@Test
	void 阶段比例必须按完工和净发货拆分且阶段转移不重复增加项目总成本() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		StageFixture fixture = createFullCostFixture("029_RATIO_", false);
		this.jdbcTemplate.update("""
				update mfg_work_order
				set planned_quantity = 2.000000, updated_at = now()
				where id = ?
				""", fixture.workOrderId());

		JsonNode calculation = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey", "pc-calc-ratio-" + fixture.projectId()),
				admin));

		assertDecimal(calculation, "projectCostTotal", "788.00");
		assertDecimal(calculation, "wipCost", "234.00");
		assertDecimal(calculation, "finishedCost", "0.00");
		assertDecimal(calculation, "deliveredCost", "354.00");
		assertDecimal(calculation, "directProjectCost", "200.00");
	}

	@Test
	void 外协发料纳入材料且销售退货按原发货未税单价扣减主收入() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		StageFixture fixture = createFullCostFixture("029_OUT_ISSUE_RETURN_", false);
		insertOutsourcingMaterialIssue(fixture, "70.00");
		insertSalesReturn(fixture.projectId(), "0.500000");

		JsonNode calculation = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey",
						"pc-calc-out-return-" + fixture.projectId()),
				admin));

		assertDecimal(calculation, "projectCostTotal", "858.00");
		assertDecimal(calculation, "shipmentRevenue", "7500.00");
		assertDecimal(calculation, "shipmentGrossMargin", "6642.00");
		JsonNode sources = data(get("/api/admin/cost/project-cost-calculations/"
				+ calculation.get("id").longValue() + "/sources?page=1&pageSize=100", admin)).get("items");
		assertThat(sourceTypes(sources)).contains("PRODUCTION_OUTSOURCING_ISSUE");
		assertThat(dbSumByCategory(calculation.get("id").longValue(), "MATERIAL")).isEqualByComparingTo("238.00");
		assertThat(sumByCategory(sources, "MATERIAL")).as("API source material sum").isEqualByComparingTo("238.00");
	}

	@Test
	void 外协没有实际发票且没有合法暂估时生成阻断差异并禁止确认() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		StageFixture fixture = createFullCostFixture("029_OUT_UNPRICED_", false);
		this.jdbcTemplate.update("""
				delete from fin_purchase_invoice_line
				where source_line_id = ?
				""", fixture.outsourcingReceiptLineId());
		this.jdbcTemplate.update("""
				delete from fin_purchase_invoice
				where project_id = ?
				and settlement_kind = 'OUTSOURCING'
				""", fixture.projectId());
		this.jdbcTemplate.update("""
				update mfg_outsourcing_receipt_line
				set provisional_unit_cost = null, unit_cost = null, updated_at = now()
				where id = ?
				""", fixture.outsourcingReceiptLineId());
		this.jdbcTemplate.update("""
				update mfg_outsourcing_receipt
				set provisional_unit_cost = null, unit_cost = null, updated_at = now()
				where id = ?
				""", fixture.outsourcingReceiptId());
		this.jdbcTemplate.update("""
				update mfg_outsourcing_order
				set provisional_unit_cost = null, updated_at = now()
				where id = ?
				""", fixture.outsourcingOrderId());

		JsonNode calculation = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey",
						"pc-calc-out-unpriced-" + fixture.projectId()),
				admin));
		JsonNode variances = data(get("/api/admin/cost/project-cost-calculations/"
				+ calculation.get("id").longValue() + "/variances", admin)).get("items");
		assertThat(hasVariance(variances, "SOURCE_UNPRICED")).isTrue();
		assertThat(hasVarianceSeverity(variances, "SOURCE_UNPRICED", "BLOCKING")).isTrue();
		assertError(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + calculation.get("id").longValue() + "/confirm",
				Map.of("version", calculation.get("version").longValue(), "sourceFingerprint",
						calculation.get("sourceFingerprint").asText(), "idempotencyKey",
						"pc-confirm-out-unpriced-" + calculation.get("id").longValue()),
				admin), HttpStatus.CONFLICT, "PROJECT_COST_SOURCE_UNVALUED");
	}

	@Test
	void 核算动作幂等必须同键同载荷重放且同键异载荷返回稳定冲突() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		StageFixture fixture = createFullCostFixture("029_ACTION_IDEMPOTENT_", false);
		Map<String, Object> payload = Map.of("cutoffDate", "2026-07-31", "idempotencyKey",
				"pc-calc-idem-" + fixture.projectId());

		JsonNode first = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations", payload,
				admin));
		JsonNode repeated = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations", payload,
				admin));
		assertThat(repeated.get("id").longValue()).isEqualTo(first.get("id").longValue());
		assertError(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-30", "idempotencyKey", "pc-calc-idem-" + fixture.projectId()),
				admin), HttpStatus.CONFLICT, "PROJECT_COST_IDEMPOTENCY_CONFLICT");

		JsonNode recalculated = data(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + first.get("id").longValue() + "/recalculate",
				Map.of("version", first.get("version").longValue(), "idempotencyKey",
						"pc-recalc-idem-" + first.get("id").longValue()),
				admin));
		JsonNode recalculateRepeated = data(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + first.get("id").longValue() + "/recalculate",
				Map.of("version", first.get("version").longValue(), "idempotencyKey",
						"pc-recalc-idem-" + first.get("id").longValue()),
				admin));
		assertThat(recalculateRepeated.get("id").longValue()).isEqualTo(recalculated.get("id").longValue());
	}

	@Test
	void 项目成本后端必须保持独立领域而不是继续堆入028财务巨型服务() throws Exception {
		java.nio.file.Path sourceRoot = java.nio.file.Path.of("src/main/java/com/qherp/api/system");
		java.nio.file.Path projectCostRoot = sourceRoot.resolve("projectcost");
		assertThat(projectCostRoot.resolve("ProjectCostAdminController.java")).exists();
		assertThat(projectCostRoot.resolve("ProjectCostCalculationService.java")).exists();
		assertThat(projectCostRoot.resolve("ProjectCostSourceCollector.java")).exists();
		assertThat(projectCostRoot.resolve("ProjectCostEntryBuilder.java")).exists();
		assertThat(projectCostRoot.resolve("ProjectCostQueryService.java")).exists();
		assertThat(projectCostRoot.resolve("ProjectCostAdjustmentService.java")).exists();
		assertThat(projectCostRoot.resolve("ProjectCostErrorCode.java")).exists();
		String finance028 = java.nio.file.Files.readString(sourceRoot.resolve("finance/FinanceStage028Service.java"));
		assertThat(finance028).doesNotContain("ProjectCost").doesNotContain("prj_cost_");
	}

	private StageFixture createFullCostFixture(String prefix, boolean includeConfirmedAllocation) {
		int suffix = SEQUENCE.incrementAndGet();
		String codePrefix = prefix + suffix + "_";
		long unitId = insertUnit(codePrefix);
		long warehouseId = insertWarehouse(codePrefix);
		long customerId = insertCustomer(codePrefix);
		long supplierId = insertSupplier(codePrefix);
		long categoryId = insertCategory(codePrefix);
		long productId = insertMaterial(codePrefix + "FG", categoryId, unitId, "FINISHED_GOODS");
		long rawId = insertMaterial(codePrefix + "RAW", categoryId, unitId, "RAW_MATERIAL");
		long projectId = insertProject(codePrefix, customerId, "12000.00");
		long contractId = insertContract(codePrefix, projectId);
		long bomId = insertBom(codePrefix, productId, rawId, unitId);
		long workOrderId = insertWorkOrder(codePrefix, projectId, productId, rawId, unitId, warehouseId, bomId);
		long issueLine1 = insertMaterialIssue(codePrefix + "ISS1", workOrderId, rawId, unitId, warehouseId,
				"1.000000");
		long firstValueMovementId = insertValueMovement("PRODUCTION_MATERIAL_ISSUE", issueLine1, issueLine1,
				"PRODUCTION_ISSUE", "OUT", warehouseId, rawId, unitId, null, "1.000000", "48.00",
				LocalDate.of(2026, 7, 10));
		linkProductionIssueLine(issueLine1, firstValueMovementId);
		long issueLine2 = insertMaterialIssue(codePrefix + "ISS2", workOrderId, rawId, unitId, warehouseId,
				"1.000000");
		long secondValueMovementId = insertValueMovement("PRODUCTION_MATERIAL_ISSUE", issueLine2, issueLine2,
				"PRODUCTION_ISSUE", "OUT", warehouseId, rawId, unitId, null, "1.000000", "110.00",
				LocalDate.of(2026, 7, 11));
		linkProductionIssueLine(issueLine2, secondValueMovementId);
		long returnLineId = insertMaterialReturn(codePrefix + "RET", workOrderId, issueLine1, rawId, unitId,
				warehouseId, "1.000000");
		long returnValueMovementId = insertValueMovement("PRODUCTION_MATERIAL_RETURN", returnLineId, returnLineId,
				"PRODUCTION_MATERIAL_RETURN_IN", "IN", warehouseId, rawId, unitId, null, "1.000000", "12.00",
				LocalDate.of(2026, 7, 12));
		linkProductionReturnLine(returnLineId, returnValueMovementId, firstValueMovementId);
		long supplementLineId = insertMaterialSupplement(codePrefix + "SUP", workOrderId, rawId, unitId,
				warehouseId, "1.000000");
		long supplementValueMovementId = insertValueMovement("PRODUCTION_MATERIAL_SUPPLEMENT", supplementLineId,
				supplementLineId, "PRODUCTION_MATERIAL_SUPPLEMENT_OUT", "OUT", warehouseId, rawId, unitId, null,
				"1.000000", "22.00", LocalDate.of(2026, 7, 13));
		linkProductionSupplementLine(supplementLineId, supplementValueMovementId);
		insertWorkOrderCost(codePrefix + "LAB", workOrderId, productId, "LABOR", "300.000000");
		insertCompletionReceipt(codePrefix + "RCPT", workOrderId, projectId, productId, unitId, warehouseId,
				"1.000000");
		long expenseLineId = insertProjectExpense(codePrefix + "EXP", supplierId, projectId, "200.00");
		long outsourcingOrderId = insertOutsourcing(codePrefix + "OUT", supplierId, projectId, productId, rawId,
				unitId, warehouseId, "1.000000", "100.000000");
		long outsourcingReceiptLineId = insertOutsourcingReceipt(codePrefix + "ORC", outsourcingOrderId, projectId,
				productId, unitId, warehouseId, "1.000000", "100.000000");
		long outsourcingReceiptId = this.jdbcTemplate.queryForObject(
				"select receipt_id from mfg_outsourcing_receipt_line where id = ?", Long.class,
				outsourcingReceiptLineId);
		insertPurchaseInvoice(codePrefix + "PI", supplierId, projectId, outsourcingOrderId, outsourcingReceiptLineId,
				productId, unitId, "120.00");
		insertSalesShipmentAndInvoice(codePrefix + "SAL", customerId, projectId, contractId, productId, unitId,
				warehouseId, "2.000000", "10000.00");
		insertLegacyMaterialCostRecord(codePrefix + "V7", workOrderId, productId, rawId, unitId, issueLine1);
		if (includeConfirmedAllocation) {
			insertConfirmedAdjustmentAllocation(codePrefix + "ADJ", projectId, expenseLineId, "50.00");
		}
		return new StageFixture(projectId, workOrderId, productId, rawId, unitId, warehouseId,
				firstValueMovementId, outsourcingOrderId, outsourcingReceiptId, outsourcingReceiptLineId);
	}

	private long insertUnit(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, 6, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "U", code + "单位");
	}

	private long insertWarehouse(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (code, name, warehouse_type, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "WH", code + "仓库");
	}

	private long insertCustomer(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "CUS", code + "客户");
	}

	private long insertSupplier(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "SUP", code + "供应商");
	}

	private long insertCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "CAT", code + "分类");
	}

	private long insertMaterial(String code, long categoryId, long unitId, String materialType) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id,
					unit_id, status, cost_category, inventory_valuation_category, inventory_value_enabled,
					project_cost_enabled, created_by, created_at, updated_by, updated_at)
				values (?, ?, '029规格', ?, 'PURCHASED', ?, ?, 'ENABLED', 'DIRECT_MATERIAL',
					'VALUATED_MATERIAL', true, true, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "物料", materialType, categoryId, unitId);
	}

	private long insertProject(String code, long customerId, String targetRevenue) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (project_no, name, customer_id, owner_user_id, planned_start_date,
					planned_finish_date, status, target_revenue, target_cost, created_by, created_at, updated_by,
					updated_at, activated_by, activated_at)
				values (?, ?, ?, 1, date '2026-07-01', date '2026-07-31', 'ACTIVE', ?::numeric, 0,
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "PRJ", code + "项目", customerId, targetRevenue);
	}

	private long insertContract(String code, long projectId) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project_contract (contract_no, project_id, contract_type, name, signed_date,
					effective_start_date, amount, status, created_by, created_at, updated_by, updated_at,
					activated_by, activated_at)
				values (?, ?, 'MAIN', ?, date '2026-07-01', date '2026-07-01', 12000.00, 'EFFECTIVE',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "CON", projectId, code + "合同");
	}

	private long insertBom(String code, long productId, long rawId, long unitId) {
		long bomId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id,
					status, effective_from, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'V1', ?, 1.000000, ?, 'ENABLED', date '2026-07-01', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "BOM", productId, code + "BOM", unitId);
		this.jdbcTemplate.update("""
				insert into mfg_bom_item (bom_id, line_no, child_material_id, unit_id, quantity, loss_rate,
					business_unit_id, business_quantity, base_unit_id, base_quantity, quantity_basis,
					created_at, updated_at)
				values (?, 1, ?, ?, 1.000000, 0, ?, 1.000000, ?, 1.000000, 'BASE_UNIT', now(), now())
				""", bomId, rawId, unitId, unitId, unitId);
		return bomId;
	}

	private long insertWorkOrder(String code, long projectId, long productId, long rawId, long unitId,
			long warehouseId, long bomId) {
		long workOrderId = this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order (work_order_no, product_material_id, bom_id, planned_quantity,
					reported_quantity, qualified_quantity, defective_quantity, received_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_start_date, planned_finish_date, status, ownership_type,
					project_id, created_by, created_at, updated_by, updated_at, released_by, released_at)
				values (?, ?, ?, 1.000000, 1.000000, 1.000000, 0, 1.000000, ?, ?, date '2026-07-01',
					date '2026-07-31', 'COMPLETED', 'PROJECT', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "WO", productId, bomId, warehouseId, warehouseId, projectId);
		long bomItemId = this.jdbcTemplate.queryForObject(
				"select id from mfg_bom_item where bom_id = ? and child_material_id = ?", Long.class, bomId, rawId);
		this.jdbcTemplate.update("""
				insert into mfg_work_order_material (work_order_id, line_no, bom_item_id, material_id, unit_id,
					required_quantity, issued_quantity, loss_rate, business_unit_id, business_quantity,
					base_unit_id, base_required_quantity, quantity_basis, created_at, updated_at)
				values (?, 1, ?, ?, ?, 1.000000, 3.000000, 0, ?, 1.000000, ?, 1.000000, 'BASE_UNIT',
					now(), now())
				""", workOrderId, bomItemId, rawId, unitId, unitId, unitId);
		return workOrderId;
	}

	private long insertMaterialIssue(String no, long workOrderId, long rawId, long unitId, long warehouseId,
			String quantity) {
		long issueId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_issue (issue_no, work_order_id, status, business_date, reason,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at)
				values (?, ?, 'POSTED', date '2026-07-10', '029领料', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, no, workOrderId);
		long workOrderMaterialId = this.jdbcTemplate.queryForObject(
				"select id from mfg_work_order_material where work_order_id = ? and material_id = ?", Long.class,
				workOrderId, rawId);
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_material_issue_line (issue_id, work_order_material_id, line_no, warehouse_id,
					material_id, unit_id, quantity, ownership_type, project_id, created_at, updated_at)
				select ?, ?, 1, ?, ?, ?, ?::numeric, 'PUBLIC', null, now(), now()
				returning id
				""", Long.class, issueId, workOrderMaterialId, warehouseId, rawId, unitId, quantity);
	}

	private long insertMaterialReturn(String no, long workOrderId, long sourceIssueLineId, long rawId, long unitId,
			long warehouseId, String quantity) {
		long sourceIssueId = this.jdbcTemplate.queryForObject(
				"select issue_id from mfg_material_issue_line where id = ?", Long.class, sourceIssueLineId);
		long returnId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_return (return_no, work_order_id, source_issue_id, warehouse_id,
					business_date, status, created_by, created_at, updated_by, updated_at, posted_by, posted_at)
				values (?, ?, ?, ?, date '2026-07-12', 'POSTED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, no, workOrderId, sourceIssueId, warehouseId);
		long workOrderMaterialId = this.jdbcTemplate.queryForObject(
				"select id from mfg_work_order_material where work_order_id = ? and material_id = ?", Long.class,
				workOrderId, rawId);
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_material_return_line (return_id, source_issue_line_id, work_order_material_id,
					material_id, unit_id, line_no, returnable_quantity_before, quantity, ownership_type, project_id,
					created_at, updated_at)
				values (?, ?, ?, ?, ?, 1, ?::numeric, ?::numeric, 'PUBLIC', null, now(), now())
				returning id
				""", Long.class, returnId, sourceIssueLineId, workOrderMaterialId, rawId, unitId, quantity,
				quantity);
	}

	private long insertMaterialSupplement(String no, long workOrderId, long rawId, long unitId, long warehouseId,
			String quantity) {
		long supplementId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_supplement (supplement_no, work_order_id, warehouse_id, business_date,
					status, created_by, created_at, updated_by, updated_at, posted_by, posted_at)
				values (?, ?, ?, date '2026-07-13', 'POSTED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, no, workOrderId, warehouseId);
		long workOrderMaterialId = this.jdbcTemplate.queryForObject(
				"select id from mfg_work_order_material where work_order_id = ? and material_id = ?", Long.class,
				workOrderId, rawId);
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_material_supplement_line (supplement_id, work_order_material_id, material_id,
					unit_id, line_no, quantity, ownership_type, project_id, created_at, updated_at)
				values (?, ?, ?, ?, 1, ?::numeric, 'PUBLIC', null, now(), now())
				returning id
				""", Long.class, supplementId, workOrderMaterialId, rawId, unitId, quantity);
	}

	private long insertValueMovement(String sourceType, long sourceId, long sourceLineId, String movementType,
			String direction, long warehouseId, long materialId, long unitId, Long projectId, String quantity,
			String amount, LocalDate businessDate) {
		long stockId = this.jdbcTemplate.queryForObject("""
				insert into inv_stock_movement (movement_no, movement_type, direction, warehouse_id, material_id,
					unit_id, quantity, before_quantity, after_quantity, source_type, source_id, source_line_id,
					business_date, reason, operator_name, occurred_at, ownership_type, project_id,
					valuation_state, valuation_method, unit_cost, inventory_amount, quality_status)
				values (?, ?, ?, ?, ?, ?, ?::numeric, 0, ?::numeric, ?, ?, ?, ?, '029来源', 'test', now(),
					?, ?, 'VALUED', 'MOVING_WEIGHTED_AVERAGE', round(?::numeric / ?::numeric, 6), ?::numeric,
					'QUALIFIED')
				returning id
				""", Long.class, "029-VM-" + SEQUENCE.incrementAndGet(), movementType, direction, warehouseId,
				materialId, unitId, quantity, quantity, sourceType, sourceId, sourceLineId, businessDate,
				projectId == null ? "PUBLIC" : "PROJECT", projectId, amount, quantity, amount);
		long valueId = this.jdbcTemplate.queryForObject("""
				insert into inv_value_movement (stock_movement_id, movement_no, movement_type, direction,
					warehouse_id, material_id, ownership_type, project_id, quantity, unit_cost, inventory_amount,
					valuation_method, valuation_state, source_type, source_id, source_line_id, business_date)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?::numeric, round(?::numeric / ?::numeric, 6), ?::numeric,
					'MOVING_WEIGHTED_AVERAGE', 'VALUED', ?, ?, ?, ?)
				returning id
				""", Long.class, stockId, "029-VMV-" + SEQUENCE.incrementAndGet(), movementType, direction,
				warehouseId, materialId, projectId == null ? "PUBLIC" : "PROJECT", projectId, quantity, amount,
				quantity, amount, sourceType, sourceId, sourceLineId, businessDate);
		this.jdbcTemplate.update("update inv_stock_movement set value_movement_id = ? where id = ?", valueId,
				stockId);
		return valueId;
	}

	private void linkProductionIssueLine(long lineId, long valueMovementId) {
		this.jdbcTemplate.update("update mfg_material_issue_line set value_movement_id = ? where id = ?",
				valueMovementId, lineId);
	}

	private void linkProductionReturnLine(long lineId, long valueMovementId, long sourceValueMovementId) {
		this.jdbcTemplate.update("""
				update mfg_material_return_line
				set value_movement_id = ?, source_value_movement_id = ?
				where id = ?
				""", valueMovementId, sourceValueMovementId, lineId);
	}

	private void linkProductionSupplementLine(long lineId, long valueMovementId) {
		this.jdbcTemplate.update("update mfg_material_supplement_line set value_movement_id = ? where id = ?",
				valueMovementId, lineId);
	}

	private void insertWorkOrderCost(String no, long workOrderId, long productId, String costType, String amount) {
		this.jdbcTemplate.update("""
				insert into mfg_cost_record (
					record_no, work_order_id, product_material_id, cost_type, source_type, source_document_type,
					source_document_no, source_document_id, quantity, amount, basis_type, business_date, status,
					remark, recorded_by, recorded_at, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 'MANUAL_ENTRY', 'MANUAL_COST_RECORD', ?, ?, 1.000000, ?::numeric,
					'MANUAL_AMOUNT', date '2026-07-14', 'ACTIVE', '029人工', 'test', now(), 'test', now(),
					'test', now())
				""", no, workOrderId, productId, costType, no, 7_000_000L + SEQUENCE.incrementAndGet(), amount);
	}

	private void insertCompletionReceipt(String no, long workOrderId, long projectId, long productId, long unitId,
			long warehouseId, String quantity) {
		this.jdbcTemplate.update("""
				insert into mfg_completion_receipt (receipt_no, work_order_id, status, business_date,
					receipt_warehouse_id, quantity, ownership_type, project_id, unit_cost, valuation_state,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at)
				values (?, ?, 'POSTED', date '2026-07-16', ?, ?::numeric, 'PROJECT', ?, 0, 'VALUED',
					'test', now(), 'test', now(), 'test', now())
				""", no, workOrderId, warehouseId, quantity, projectId);
	}

	private long insertProjectExpense(String no, long supplierId, long projectId, String amount) {
		long expenseId = this.jdbcTemplate.queryForObject("""
				insert into fin_expense (expense_no, supplier_id, ownership_type, project_id, expense_date,
					due_date, invoice_type, currency, tax_excluded_amount, tax_amount, tax_included_amount,
					status, created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at)
				values (?, ?, 'PROJECT', ?, date '2026-07-18', date '2026-07-31', 'NONE', 'CNY',
					?::numeric, 0, ?::numeric, 'CONFIRMED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, no, supplierId, projectId, amount, amount);
		return this.jdbcTemplate.queryForObject("""
				insert into fin_expense_line (expense_id, line_no, expense_category, description,
					tax_excluded_amount, tax_amount, tax_included_amount, created_at, updated_at)
				values (?, 1, 'PROJECT_EXPENSE', '029项目费用', ?::numeric, 0, ?::numeric, now(), now())
				returning id
				""", Long.class, expenseId, amount, amount);
	}

	private long insertOutsourcing(String no, long supplierId, long projectId, long productId, long rawId, long unitId,
			long warehouseId, String quantity, String provisionalUnitCost) {
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into mfg_outsourcing_order (outsourcing_order_no, supplier_id, product_material_id,
					planned_quantity, issued_quantity, received_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_issue_date, planned_receipt_date, status, ownership_type,
					project_id, provisional_unit_cost, created_by, created_at, updated_by, updated_at,
					released_by, released_at)
				values (?, ?, ?, ?::numeric, 0, ?::numeric, ?, ?, date '2026-07-08', date '2026-07-20',
					'COMPLETED', 'PROJECT', ?, ?::numeric, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, no, supplierId, productId, quantity, quantity, warehouseId, warehouseId, projectId,
				provisionalUnitCost);
		this.jdbcTemplate.update("""
				insert into mfg_outsourcing_order_material (outsourcing_order_id, line_no, material_id, unit_id,
					required_quantity, created_at, updated_at)
				values (?, 1, ?, ?, 1.000000, now(), now())
				""", orderId, rawId, unitId);
		return orderId;
	}

	private long insertOutsourcingReceipt(String no, long orderId, long projectId, long productId, long unitId,
			long warehouseId, String quantity, String provisionalUnitCost) {
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into mfg_outsourcing_receipt (receipt_no, outsourcing_order_id, status, business_date,
					receipt_warehouse_id, quantity, rejected_quantity, provisional_unit_cost, unit_cost,
					valuation_state, ownership_type, project_id, created_by, created_at, updated_by, updated_at,
					posted_by, posted_at)
				values (?, ?, 'POSTED', date '2026-07-20', ?, ?::numeric, 0, ?::numeric, ?::numeric,
					'MANUAL_PROVISIONAL', 'PROJECT', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, no, orderId, warehouseId, quantity, provisionalUnitCost, provisionalUnitCost,
				projectId);
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_outsourcing_receipt_line (receipt_id, line_no, accepted_quantity,
					rejected_quantity, provisional_unit_cost, unit_cost, created_at, updated_at)
				values (?, 1, ?::numeric, 0, ?::numeric, ?::numeric, now(), now())
				returning id
				""", Long.class, receiptId, quantity, provisionalUnitCost, provisionalUnitCost);
	}

	private void insertPurchaseInvoice(String no, long supplierId, long projectId, long outsourcingOrderId,
			long receiptLineId, long productId, long unitId, String amount) {
		long receiptId = this.jdbcTemplate.queryForObject(
				"select receipt_id from mfg_outsourcing_receipt_line where id = ?", Long.class, receiptLineId);
		long invoiceId = this.jdbcTemplate.queryForObject("""
				insert into fin_purchase_invoice (invoice_no, supplier_id, settlement_kind, ownership_type,
					project_id, source_type, source_id, source_no, invoice_date, due_date, invoice_type, currency,
					match_status, tax_excluded_amount, tax_amount, tax_included_amount, status, created_by,
					created_at, updated_by, updated_at, confirmed_by, confirmed_at)
				values (?, ?, 'OUTSOURCING', 'PROJECT', ?, 'OUTSOURCING_RECEIPT', ?, ?, date '2026-07-21',
					date '2026-07-31', 'NONE', 'CNY', 'NOT_APPLICABLE', ?::numeric, 0, ?::numeric,
					'CONFIRMED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, no, supplierId, projectId, receiptId, no + "-SRC", amount, amount);
		this.jdbcTemplate.update("""
				insert into fin_purchase_invoice_line (purchase_invoice_id, line_no, source_line_id,
					outsourcing_order_id, material_id, unit_id, quantity, tax_excluded_unit_price,
					tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount, match_status,
					created_at, updated_at)
				values (?, 1, ?, ?, ?, ?, 1.000000, ?::numeric, ?::numeric, ?::numeric, 0, ?::numeric,
					'NOT_APPLICABLE', now(), now())
				""", invoiceId, receiptLineId, outsourcingOrderId, productId, unitId, amount, amount, amount,
				amount);
	}

	private void insertSalesShipmentAndInvoice(String no, long customerId, long projectId, long contractId,
			long productId, long unitId, long warehouseId, String quantity, String amount) {
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (order_no, customer_id, order_date, expected_ship_date, status,
					project_id, contract_id, currency, tax_excluded_amount, tax_amount, tax_included_amount,
					sales_fulfillment_compatible, created_by, created_at, updated_by, updated_at, confirmed_by,
					confirmed_at)
				values (?, ?, date '2026-07-01', date '2026-07-25', 'SHIPPED', ?, ?, 'CNY', ?::numeric,
					0, ?::numeric, true, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, no + "SO", customerId, projectId, contractId, amount, amount);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (order_id, line_no, material_id, unit_id, quantity,
					shipped_quantity, unit_price, expected_ship_date, reservation_warehouse_id, price_source_type,
					source_no, currency, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
					tax_excluded_amount, tax_amount, tax_included_amount, created_at, updated_at)
				values (?, 1, ?, ?, ?::numeric, ?::numeric, ?::numeric / ?::numeric, date '2026-07-25', ?,
					'MANUAL', ?, 'CNY', 0, ?::numeric / ?::numeric, ?::numeric / ?::numeric, ?::numeric,
					0, ?::numeric, now(), now())
				returning id
				""", Long.class, orderId, productId, unitId, quantity, quantity, amount, quantity, warehouseId,
				no + "SO", amount, quantity, amount, quantity, amount, amount);
		long shipmentId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment (shipment_no, order_id, customer_id, warehouse_id, business_date,
					status, created_by, created_at, updated_by, updated_at, posted_by, posted_at)
				values (?, ?, ?, ?, date '2026-07-25', 'POSTED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, no + "SHIP", orderId, customerId, warehouseId);
		long shipmentLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment_line (shipment_id, line_no, order_line_id, material_id, unit_id,
					ordered_quantity, shipped_quantity_before, remaining_quantity_before, quantity,
					price_source_type, source_no, currency, tax_rate, tax_excluded_unit_price,
					tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount, created_at,
					updated_at)
				values (?, 1, ?, ?, ?, ?::numeric, 0, ?::numeric, ?::numeric, 'MANUAL', ?, 'CNY', 0,
					?::numeric / ?::numeric, ?::numeric / ?::numeric, ?::numeric, 0, ?::numeric, now(), now())
				returning id
				""", Long.class, shipmentId, orderLineId, productId, unitId, quantity, quantity, quantity,
				no + "SO", amount, quantity, amount, quantity, amount, amount);
		long invoiceId = this.jdbcTemplate.queryForObject("""
				insert into fin_sales_invoice (invoice_no, customer_id, ownership_type, project_id, source_type,
					source_id, source_no, invoice_date, due_date, invoice_type, currency, tax_excluded_amount,
					tax_amount, tax_included_amount, status, created_by, created_at, updated_by, updated_at,
					confirmed_by, confirmed_at)
				values (?, ?, 'PROJECT', ?, 'SALES_SHIPMENT', ?, ?, date '2026-07-26', date '2026-07-31',
					'NONE', 'CNY', ?::numeric, 0, ?::numeric, 'CONFIRMED', 'test', now(), 'test', now(),
					'test', now())
				returning id
				""", Long.class, no + "SI", customerId, projectId, shipmentId, no + "SHIP", amount, amount);
		this.jdbcTemplate.update("""
				insert into fin_sales_invoice_line (sales_invoice_id, line_no, source_line_id, sales_order_id,
					sales_order_line_id, material_id, unit_id, quantity, tax_excluded_unit_price,
					tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount, created_at,
					updated_at)
				values (?, 1, ?, ?, ?, ?, ?, ?::numeric, ?::numeric / ?::numeric, ?::numeric / ?::numeric,
					?::numeric, 0, ?::numeric, now(), now())
				""", invoiceId, shipmentLineId, orderId, orderLineId, productId, unitId, quantity, amount, quantity,
				amount, quantity, amount, amount);
	}

	private void insertOutsourcingMaterialIssue(StageFixture fixture, String amount) {
		long orderMaterialId = this.jdbcTemplate.queryForObject("""
				select id
				from mfg_outsourcing_order_material
				where outsourcing_order_id = ?
				and material_id = ?
				""", Long.class, fixture.outsourcingOrderId(), fixture.rawMaterialId());
		long issueId = this.jdbcTemplate.queryForObject("""
				insert into mfg_outsourcing_issue (
					issue_no, outsourcing_order_id, status, business_date, reason, created_by, created_at,
					updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, 'POSTED', date '2026-07-15', '029外协发料', 'test', now(), 'test', now(),
					'test', now())
				returning id
				""", Long.class, "029-OUT-ISS-" + SEQUENCE.incrementAndGet(), fixture.outsourcingOrderId());
		long lineId = this.jdbcTemplate.queryForObject("""
				insert into mfg_outsourcing_issue_line (
					issue_id, order_material_id, line_no, warehouse_id, material_id, unit_id, quantity,
					ownership_type, project_id, created_at, updated_at
				)
				values (?, ?, 1, ?, ?, ?, 1.000000, 'PUBLIC', null, now(), now())
				returning id
				""", Long.class, issueId, orderMaterialId, fixture.warehouseId(), fixture.rawMaterialId(),
				fixture.unitId());
		long valueMovementId = insertValueMovement("PRODUCTION_OUTSOURCING_ISSUE", lineId, lineId,
				"OUTSOURCING_ISSUE", "OUT", fixture.warehouseId(), fixture.rawMaterialId(),
				fixture.unitId(), null, "1.000000", amount, LocalDate.of(2026, 7, 15));
		this.jdbcTemplate.update("""
				update mfg_outsourcing_issue_line
				set value_movement_id = ?
				where id = ?
				""", valueMovementId, lineId);
	}

	private void insertSalesReturn(long projectId, String quantity) {
		Map<String, Object> shipment = this.jdbcTemplate.queryForMap("""
				select sh.id as shipment_id, sh.shipment_no, sh.customer_id, sh.warehouse_id,
				       sl.id as shipment_line_id, sl.order_line_id, sl.material_id, sl.unit_id,
				       sl.tax_excluded_unit_price
				from sal_sales_shipment_line sl
				join sal_sales_shipment sh on sh.id = sl.shipment_id
				join sal_sales_order so on so.id = sh.order_id
				where so.project_id = ?
				order by sl.id
				limit 1
				""", projectId);
		BigDecimal unitPrice = (BigDecimal) shipment.get("tax_excluded_unit_price");
		BigDecimal amount = unitPrice.multiply(new BigDecimal(quantity)).setScale(2);
		long returnId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_return (
					return_no, customer_id, source_shipment_id, source_shipment_no, warehouse_id, business_date,
					status, total_amount, created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, date '2026-07-28', 'POSTED', ?, 'test', now(), 'test', now(), 'test',
					now())
				returning id
				""", Long.class, "029-SRET-" + SEQUENCE.incrementAndGet(), shipment.get("customer_id"),
				shipment.get("shipment_id"), shipment.get("shipment_no"), shipment.get("warehouse_id"), amount);
		this.jdbcTemplate.update("""
				insert into sal_sales_return_line (
					return_id, source_shipment_line_id, sales_order_line_id, material_id, unit_id, line_no,
					returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount,
					reason, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, 1, 0, ?::numeric, ?::numeric, ?, ?, '029销售退货', now(), now())
				""", returnId, shipment.get("shipment_line_id"), shipment.get("order_line_id"),
				shipment.get("material_id"), shipment.get("unit_id"), quantity, quantity, unitPrice, amount);
	}

	private void insertLegacyMaterialCostRecord(String no, long workOrderId, long productId, long rawId, long unitId,
			long issueLineId) {
		long issueId = this.jdbcTemplate.queryForObject(
				"select issue_id from mfg_material_issue_line where id = ?", Long.class, issueLineId);
		this.jdbcTemplate.update("""
				insert into mfg_cost_record (
					record_no, work_order_id, product_material_id, cost_type, source_type, source_document_type,
					source_document_no, source_document_id, source_line_id, material_id, unit_id, quantity,
					unit_price, amount, basis_type, business_date, status, remark, recorded_by, recorded_at,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, 'MATERIAL', 'AUTO_PRODUCTION', 'PRODUCTION_MATERIAL_ISSUE', ?, ?, ?, ?, ?,
					1.000000, 999.000000, 999.000000, 'MANUAL_UNIT_PRICE_QUANTITY', date '2026-07-10',
					'ACTIVE', 'V7材料记录不得重复计额', 'test', now(), 'test', now(), 'test', now())
				""", no, workOrderId, productId, no, issueId, issueLineId, rawId, unitId);
	}

	private void insertConfirmedAdjustmentAllocation(String no, long projectId, long expenseLineId, String amount) {
		long adjustmentId = this.jdbcTemplate.queryForObject("""
				insert into prj_cost_adjustment (adjustment_no, adjustment_type, business_date, status, reason,
					idempotency_key, request_fingerprint, created_by, created_at, updated_by, updated_at,
					submitted_by, submitted_at, confirmed_by, confirmed_at)
				values (?, 'PUBLIC_EXPENSE_ALLOCATION', date '2026-07-22', 'CONFIRMED', '029公共费用分配',
					?, 'seed', 'test', now(), 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, no, no + "-idem");
		this.jdbcTemplate.update("""
				insert into prj_cost_adjustment_line (adjustment_id, line_no, project_id, cost_category,
					cost_stage, direction, amount, public_expense_line_id, reason, created_at, updated_at)
				values (?, 1, ?, 'MANUFACTURING_OVERHEAD', 'DIRECT_PROJECT', 'INCREASE', ?::numeric, ?,
					'公共制造费用分配', now(), now())
				""", adjustmentId, projectId, amount, expenseLineId);
	}

	private Map<String, Long> upstreamCounts() {
		return Map.of("inv_value_movement", tableCount("inv_value_movement"), "mfg_cost_record",
				tableCount("mfg_cost_record"), "fin_purchase_invoice", tableCount("fin_purchase_invoice"),
				"fin_expense", tableCount("fin_expense"));
	}

	private Map<String, String> upstreamRowSummaries() {
		return Map.of("inv_value_movement", rowSummary("""
				select id, inventory_amount, valuation_state, source_type, source_id, source_line_id
				from inv_value_movement
				"""), "mfg_cost_record", rowSummary("""
				select id, amount, status, source_document_type, source_document_id, source_line_id, updated_at
				from mfg_cost_record
				"""), "fin_purchase_invoice", rowSummary("""
				select id, tax_excluded_amount, status, source_type, source_id, linked_payable_id, updated_at
				from fin_purchase_invoice
				"""), "fin_expense", rowSummary("""
				select id, tax_excluded_amount, status, ownership_type, project_id, linked_payable_id, updated_at
				from fin_expense
				"""));
	}

	private String rowSummary(String sql) {
		return this.jdbcTemplate.queryForObject("""
				select md5(coalesce(string_agg(row_to_json(t)::text, '|' order by row_to_json(t)::text), ''))
				from (%s) t
				""".formatted(sql), String.class);
	}

	private long tableCount(String tableName) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
	}

	private List<Long> currentCalculationIds(long projectId) {
		return this.jdbcTemplate.queryForList("""
				select id
				from prj_cost_calculation
				where project_id = ?
				and is_current = true
				order by id
				""", Long.class, projectId);
	}

	private long calculateProject(AuthenticatedSession session, long projectId, String idempotencyKey) throws Exception {
		JsonNode calculation = data(exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + projectId + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey", idempotencyKey), session));
		assertThat(calculation.get("freshnessStatus").asText()).isEqualTo("CURRENT");
		return calculation.get("id").longValue();
	}

	private void assertRevenueChangeMakesCalculationStale(AuthenticatedSession session, String prefix,
			RevenueChange revenueChange) throws Exception {
		StageFixture fixture = createFullCostFixture(prefix, false);
		ResponseEntity<String> calculationResponse = exchange(HttpMethod.POST,
				"/api/admin/cost/project-costs/projects/" + fixture.projectId() + "/calculations",
				Map.of("cutoffDate", "2026-07-31", "idempotencyKey",
						"pc-calc-revenue-" + fixture.projectId()),
				session);
		assertThat(calculationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode calculation = data(calculationResponse);
		long calculationId = calculation.get("id").longValue();
		assertFreshnessAcrossCalculationDtos(session, fixture.projectId(), calculationId, "CURRENT");

		revenueChange.apply(fixture);

		JsonNode staleCalculation = data(get("/api/admin/cost/project-cost-calculations/" + calculationId,
				session));
		assertThat(staleCalculation.get("status").asText()).isEqualTo("CALCULATED");
		assertThat(staleCalculation.get("isCurrent").booleanValue()).isFalse();
		assertThat(staleCalculation.get("freshnessStatus").asText()).isEqualTo("STALE");
		assertFreshnessAcrossCalculationDtos(session, fixture.projectId(), calculationId, "STALE");
		assertError(exchange(HttpMethod.PUT,
				"/api/admin/cost/project-cost-calculations/" + calculationId + "/confirm",
				Map.of("version", calculation.get("version").longValue(), "sourceFingerprint",
						calculation.get("sourceFingerprint").asText(), "idempotencyKey",
						"pc-confirm-revenue-stale-" + calculationId),
				session), HttpStatus.CONFLICT, "PROJECT_COST_SOURCE_CHANGED");
	}

	private void assertEffectiveLatest(AuthenticatedSession session, long projectId, long calculationId, String status)
			throws Exception {
		JsonNode workbenchItems = data(get("/api/admin/cost/project-costs?projectId=" + projectId
				+ "&page=1&pageSize=20", session)).get("items");
		assertThat(workbenchItems.size()).isOne();
		JsonNode workbench = workbenchItems.get(0);
		assertThat(workbench.get("calculationId").longValue()).isEqualTo(calculationId);
		assertThat(workbench.get("status").asText()).isEqualTo(status);
		assertThat(workbench.get("freshnessStatus").asText()).isEqualTo("CURRENT");

		JsonNode detail = data(get("/api/admin/cost/project-costs/projects/" + projectId, session));
		assertThat(detail.get("calculationId").longValue()).isEqualTo(calculationId);
		assertThat(detail.get("latestCalculationId").longValue()).isEqualTo(calculationId);
		assertThat(detail.get("status").asText()).isEqualTo(status);
		assertThat(detail.get("freshnessStatus").asText()).isEqualTo("CURRENT");
	}

	private void assertFreshnessAcrossCalculationDtos(AuthenticatedSession session, long projectId, long calculationId,
			String expectedFreshnessStatus) throws Exception {
		JsonNode calculation = data(get("/api/admin/cost/project-cost-calculations/" + calculationId, session));
		assertThat(calculation.get("id").longValue()).isEqualTo(calculationId);
		assertThat(calculation.get("freshnessStatus").asText()).isEqualTo(expectedFreshnessStatus);

		JsonNode detail = data(get("/api/admin/cost/project-costs/projects/" + projectId, session));
		assertThat(detail.get("calculationId").longValue()).isEqualTo(calculationId);
		assertThat(detail.get("latestCalculationId").longValue()).isEqualTo(calculationId);
		assertThat(detail.get("freshnessStatus").asText()).isEqualTo(expectedFreshnessStatus);

		JsonNode workbenchItems = data(get("/api/admin/cost/project-costs?projectId=" + projectId
				+ "&page=1&pageSize=20", session)).get("items");
		assertThat(workbenchItems.size()).isOne();
		JsonNode workbench = workbenchItems.get(0);
		assertThat(workbench.get("calculationId").longValue()).isEqualTo(calculationId);
		assertThat(workbench.get("freshnessStatus").asText()).isEqualTo(expectedFreshnessStatus);

		JsonNode expectedItems = data(get("/api/admin/cost/project-costs?projectId=" + projectId
				+ "&freshnessStatus=" + expectedFreshnessStatus + "&page=1&pageSize=20", session)).get("items");
		assertThat(expectedItems.size()).isOne();
		assertThat(expectedItems.get(0).get("calculationId").longValue()).isEqualTo(calculationId);
		String oppositeFreshnessStatus = "CURRENT".equals(expectedFreshnessStatus) ? "STALE" : "CURRENT";
		JsonNode oppositeItems = data(get("/api/admin/cost/project-costs?projectId=" + projectId
				+ "&freshnessStatus=" + oppositeFreshnessStatus + "&page=1&pageSize=20", session)).get("items");
		assertThat(oppositeItems.size()).isZero();
	}

	private List<String> calculationSummaryStatuses(JsonNode summaries) {
		List<String> statuses = new java.util.ArrayList<>();
		for (JsonNode summary : summaries) {
			statuses.add(summary.get("status").asText());
		}
		return statuses;
	}

	private List<String> freshnessStatuses(JsonNode items) {
		List<String> statuses = new java.util.ArrayList<>();
		for (JsonNode item : items) {
			statuses.add(item.get("freshnessStatus").asText());
		}
		return statuses;
	}

	private List<Long> projectIds(JsonNode items) {
		List<Long> ids = new java.util.ArrayList<>();
		for (JsonNode item : items) {
			ids.add(item.get("projectId").longValue());
		}
		return ids;
	}

	private BigDecimal sumByCategory(JsonNode sources, String category) {
		BigDecimal total = BigDecimal.ZERO;
		for (JsonNode source : sources) {
			if (category.equals(source.get("costCategory").asText()) && source.get("calculatedAmount").isTextual()) {
				total = total.add(new BigDecimal(source.get("calculatedAmount").asText()));
			}
		}
		return total;
	}

	private BigDecimal dbSumByCategory(Long calculationId, String category) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(calculated_amount), 0)
				from prj_cost_source_line
				where calculation_id = ?
				and cost_category = ?
				""", BigDecimal.class, calculationId, category);
	}

	private Set<String> sourceKeys(JsonNode sources) {
		Set<String> keys = new HashSet<>();
		for (JsonNode source : sources) {
			keys.add(source.get("sourceType").asText() + "|" + source.get("sourceId").asText() + "|"
					+ source.get("sourceLineId").asText() + "|" + source.get("costCategory").asText() + "|"
					+ source.get("entryType").asText());
		}
		return keys;
	}

	private Set<String> sourceTypes(JsonNode sources) {
		Set<String> types = new HashSet<>();
		for (JsonNode source : sources) {
			types.add(source.get("sourceType").asText());
		}
		return types;
	}

	private Set<String> sourceStatuses(JsonNode sources) {
		Set<String> statuses = new HashSet<>();
		for (JsonNode source : sources) {
			statuses.add(source.get("sourceStatus").asText());
		}
		return statuses;
	}

	private boolean hasVariance(JsonNode variances, String varianceType) {
		for (JsonNode variance : variances) {
			if (varianceType.equals(variance.get("varianceType").asText())) {
				return true;
			}
		}
		return false;
	}

	private boolean hasVarianceSeverity(JsonNode variances, String varianceType, String severity) {
		for (JsonNode variance : variances) {
			if (varianceType.equals(variance.get("varianceType").asText())
					&& severity.equals(variance.get("severity").asText())) {
				return true;
			}
		}
		return false;
	}

	private JsonNode findVariance(JsonNode variances, String varianceType) {
		for (JsonNode variance : variances) {
			if (varianceType.equals(variance.get("varianceType").asText())) {
				return variance;
			}
		}
		throw new AssertionError("未找到差异 " + varianceType);
	}

	private boolean hasEntry(JsonNode entries, String entryType) {
		for (JsonNode entry : entries) {
			if (entryType.equals(entry.get("entryType").asText())) {
				return true;
			}
		}
		return false;
	}

	private List<String> actionCodes(JsonNode actions) {
		List<String> codes = new java.util.ArrayList<>();
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

	@FunctionalInterface
	private interface RevenueChange {
		void apply(StageFixture fixture) throws Exception;
	}

	private record StageFixture(Long projectId, Long workOrderId, Long productMaterialId, Long rawMaterialId,
			Long unitId, Long warehouseId, Long firstValueMovementId, Long outsourcingOrderId,
			Long outsourcingReceiptId, Long outsourcingReceiptLineId) {
	}

}
