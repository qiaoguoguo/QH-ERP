package com.qherp.api.system.planning;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.platform.PlatformDocumentTaskWorker;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.SoftAssertions;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
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
		properties = { "qherp.test.context=planning-stage026", "qherp.platform.task.worker.enabled=false" })
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MaterialRequirementPlanningStage026Tests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Container
	static final GenericContainer<?> minio = new GenericContainer<>(
			DockerImageName.parse("minio/minio:RELEASE.2024-01-16T16-07-38Z"))
		.withEnv("MINIO_ROOT_USER", "qherpminio")
		.withEnv("MINIO_ROOT_PASSWORD", "qherpminio123")
		.withCommand("server /data --console-address :9001")
		.withExposedPorts(9000)
		.waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).withStartupTimeout(Duration.ofSeconds(90)));

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("qherp.storage.s3.endpoint", () -> "http://" + minio.getHost() + ":"
				+ minio.getMappedPort(9000));
		registry.add("qherp.storage.s3.region", () -> "us-east-1");
		registry.add("qherp.storage.s3.bucket", () -> "qherp-test-private");
		registry.add("qherp.storage.s3.access-key", () -> "qherpminio");
		registry.add("qherp.storage.s3.secret-key", () -> "qherpminio123");
		registry.add("qherp.storage.s3.path-style", () -> "true");
	}

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PlatformDocumentTaskWorker documentTaskWorker;

	@Test
	void projectRunPersistsHybridSnapshotAndConvertsPurchaseSuggestionIdempotently() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();
		Map<String, Object> request = runRequest(fixture.projectId(), fixture.demandDate(),
				"MRP-RUN-" + SEQUENCE.incrementAndGet());

		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs", request));

		assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
		assertThat(run.get("scopeHash").asText()).isNotBlank();
		assertThat(run.get("sourceFingerprint").asText()).isNotBlank();
		assertThat(run.get("expiresAt").asText()).isNotBlank();
		assertThat(run.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("RECALCULATE"));

		JsonNode sameKeyRun = data(post(admin, "/api/admin/planning/material-requirement-runs", request));
		assertThat(sameKeyRun.get("id").longValue()).isEqualTo(run.get("id").longValue());
		assertError(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate().plusDays(1), request.get("idempotencyKey"))),
				HttpStatus.CONFLICT, "MATERIAL_REQUIREMENT_RUN_CONFLICT");
		JsonNode sameScopeRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RUN-" + SEQUENCE.incrementAndGet())));
		assertThat(sameScopeRun.get("id").longValue()).isEqualTo(run.get("id").longValue());

		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/requirements?page=1&pageSize=50"));
		JsonNode rawRequirement = findRequirement(requirements.get("items"), fixture.rawMaterialId());
		assertThat(rawRequirement.get("demandType").asText()).isEqualTo("BOM_COMPONENT");
		assertDecimal(rawRequirement, "requiredQuantity", "18.000000");
		assertDecimal(rawRequirement, "coveredQuantity", "13.000000");
		assertDecimal(rawRequirement, "shortageQuantity", "5.000000");

		JsonNode allocations = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/allocations?page=1&pageSize=50"));
		assertAllocated(allocations.get("items"), rawRequirement.get("id").longValue(), "PROJECT_STOCK", "3.000000");
		assertAllocated(allocations.get("items"), rawRequirement.get("id").longValue(), "PROJECT_PURCHASE",
				"4.000000");
		assertAllocated(allocations.get("items"), rawRequirement.get("id").longValue(), "PUBLIC_STOCK", "5.000000");
		assertAllocated(allocations.get("items"), rawRequirement.get("id").longValue(), "PUBLIC_PURCHASE",
				"1.000000");

		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode suggestion = findSuggestion(suggestions.get("items"), fixture.rawMaterialId(), "PURCHASE_REQUISITION");
		assertThat(suggestion.get("status").asText()).isEqualTo("OPEN");
		assertThat(suggestion.get("conversionAllowed").asBoolean()).isTrue();
		assertDecimal(suggestion, "suggestedQuantity", "5.000000");

		JsonNode confirmed = data(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + suggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", suggestion.get("version").longValue(), "idempotencyKey",
						"MRP-SUG-CONFIRM-" + suggestion.get("id").longValue())));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");

		Map<String, Object> convertRequest = Map.of("version", confirmed.get("version").longValue(),
				"idempotencyKey", "MRP-SUG-CONVERT-" + confirmed.get("id").longValue());
		JsonNode converted = data(post(admin,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-requisition",
				convertRequest));
		JsonNode convertedAgain = data(post(admin,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-requisition",
				convertRequest));
		assertThat(converted.get("id").longValue()).isEqualTo(confirmed.get("id").longValue());
		assertThat(converted.get("status").asText()).isEqualTo("CONVERTED");
		assertThat(converted.get("convertedRequisitionId").isNumber()).isTrue();
		assertThat(converted.get("convertedRequisitionNo").asText()).isNotBlank();
		assertThat(convertedAgain.get("convertedRequisitionId").longValue())
			.isEqualTo(converted.get("convertedRequisitionId").longValue());
		assertThat(convertedAgain.get("convertedRequisitionNo").asText())
			.isEqualTo(converted.get("convertedRequisitionNo").asText());
		assertThat(converted.get("projectId").longValue()).isEqualTo(fixture.projectId());

		Map<String, Object> requisitionRow = this.jdbcTemplate.queryForMap("""
				select requisition_no, status, purchase_mode, project_id
				from proc_purchase_requisition
				where id = ?
				""", converted.get("convertedRequisitionId").longValue());
		assertThat(requisitionRow.get("requisition_no")).isEqualTo(converted.get("convertedRequisitionNo").asText());
		assertThat(requisitionRow.get("status")).isEqualTo("DRAFT");
		assertThat(requisitionRow.get("purchase_mode")).isEqualTo("PROJECT");
		assertThat(((Number) requisitionRow.get("project_id")).longValue()).isEqualTo(fixture.projectId());
		Long requisitionSourceRunId = this.jdbcTemplate.queryForObject("""
				select source_mrp_run_id
				from proc_purchase_requisition
				where id = ?
				""", Long.class, converted.get("convertedRequisitionId").longValue());
		Long lineSourceSuggestionId = this.jdbcTemplate.queryForObject("""
				select source_mrp_suggestion_id
				from proc_purchase_requisition_line
				where requisition_id = ?
				""", Long.class, converted.get("convertedRequisitionId").longValue());
		assertThat(requisitionSourceRunId).isEqualTo(run.get("id").longValue());
		assertThat(lineSourceSuggestionId).isEqualTo(confirmed.get("id").longValue());
		assertThat(countRows("proc_purchase_requisition", "source_mrp_suggestion_id = ?",
				confirmed.get("id").longValue())).isOne();
	}

	@Test
	void sourceChangeMarksRunStaleAndBlocksSuggestionWrite() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RUN-" + SEQUENCE.incrementAndGet())));
		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode suggestion = findSuggestion(suggestions.get("items"), fixture.rawMaterialId(), "PURCHASE_REQUISITION");

		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set quantity = quantity + 1.000000,
				    updated_at = now(),
				    version = version + 1
				where id = ?
				""", fixture.orderLineId());

		JsonNode staleRun = data(get(admin, "/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()));
		assertThat(staleRun.get("status").asText()).isEqualTo("STALE");
		assertError(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + suggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", suggestion.get("version").longValue(), "idempotencyKey",
						"MRP-STALE-CONFIRM-" + suggestion.get("id").longValue())),
				HttpStatus.CONFLICT, "MATERIAL_REQUIREMENT_RUN_STALE");
	}

	@Test
	void recalculateAlwaysCreatesNewRunAndPreservesPreviousRun() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RUN-" + SEQUENCE.incrementAndGet())));

		JsonNode recalculated = data(post(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue() + "/recalculate",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RECALC-" + SEQUENCE.incrementAndGet())));

		assertThat(recalculated.get("id").longValue()).isNotEqualTo(run.get("id").longValue());
		Long previousRunId = this.jdbcTemplate.queryForObject("""
				select previous_run_id
				from mrp_calculation_run
				where id = ?
				""", Long.class, recalculated.get("id").longValue());
		assertThat(previousRunId).isEqualTo(run.get("id").longValue());
	}

	@Test
	void materialRequirementRunExportTaskUsesFixedTaskTypeAndPlanningPermission() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RUN-" + SEQUENCE.incrementAndGet())));

		JsonNode exportTask = data(postWithIdempotency(admin, "/api/admin/export-tasks",
				Map.of("taskType", "MATERIAL_REQUIREMENT_RUN_EXPORT", "objectType", "MATERIAL_REQUIREMENT_RUN",
						"objectId", run.get("id").longValue(), "filters", Map.of("runId", run.get("id").longValue())),
				"MRP-EXPORT-" + run.get("id").longValue()));

		assertThat(exportTask.get("taskType").asText()).isEqualTo("MATERIAL_REQUIREMENT_RUN_EXPORT");
		assertThat(exportTask.get("objectType").asText()).isEqualTo("MATERIAL_REQUIREMENT_RUN");
		assertThat(exportTask.get("objectId").longValue()).isEqualTo(run.get("id").longValue());
	}

	@Test
	void outsourcedShortageUsesProductionOrderSuggestionTypeAndCannotConvertIn026() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate demandDate = LocalDate.now().plusDays(9);
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		long unitId = insertUnit("MRP_OUT_U_" + suffix);
		long categoryId = insertCategory("MRP_OUT_CAT_" + suffix);
		long customerId = insertCustomer("MRP_OUT_CUS_" + suffix);
		long projectId = insertProject("MRP_OUT_PRJ_" + suffix, customerId, adminUserId);
		long contractId = insertContract("MRP_OUT_CON_" + suffix, projectId);
		long outsourcedId = insertMaterial("MRP_OUT_MAT_" + suffix, "FINISHED_GOOD", "OUTSOURCED", categoryId,
				unitId);
		long rawId = insertMaterial("MRP_OUT_RAW_" + suffix, "RAW_MATERIAL", "PURCHASED", categoryId, unitId);
		insertBom("MRP_OUT_BOM_" + suffix, outsourcedId, unitId, LocalDate.now().minusDays(1),
				List.of(bomItem(1, rawId, unitId, "1.000000")));
		insertSalesDemand("MRP_OUT_SO_" + suffix, customerId, projectId, contractId, outsourcedId, unitId, demandDate,
				"6.000000");

		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(projectId, demandDate, "MRP-OUT-RUN-" + suffix)));
		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/requirements?page=1&pageSize=20"));
		JsonNode rawRequirement = findRequirementByMaterial(requirements.get("items"), rawId);
		assertDecimal(rawRequirement, "requiredQuantity", "6.000000");
		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
				+ "/suggestions?page=1&pageSize=20"));

		JsonNode suggestion = findSuggestion(suggestions.get("items"), outsourcedId, "PRODUCTION_ORDER");
		assertThat(suggestion.get("suggestionType").asText()).isEqualTo("PRODUCTION_ORDER");
		assertThat(suggestion.get("status").asText()).isEqualTo("OPEN");
		assertThat(suggestion.get("conversionAllowed").asBoolean()).isTrue();
		assertThat(suggestion.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("CONFIRM"));
		JsonNode confirmed = data(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + suggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", suggestion.get("version").longValue(), "idempotencyKey",
						"MRP-OUT-CONFIRM-" + suggestion.get("id").longValue())));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(confirmed.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("CONVERT_OUTSOURCING_ORDER"));
		assertThat(confirmed.hasNonNull("actionDisabledReason")).isFalse();
		assertDecimal(suggestion, "suggestedQuantity", "6.000000");
	}

	@Test
	void latePurchaseSupplyIsRecordedAsDelayedButDoesNotCoverOnTimeDemand() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate demandDate = LocalDate.now().plusDays(6);
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		long unitId = insertUnit("MRP_LATE_U_" + suffix);
		long categoryId = insertCategory("MRP_LATE_CAT_" + suffix);
		long customerId = insertCustomer("MRP_LATE_CUS_" + suffix);
		long supplierId = insertSupplier("MRP_LATE_SUP_" + suffix);
		long projectId = insertProject("MRP_LATE_PRJ_" + suffix, customerId, adminUserId);
		long contractId = insertContract("MRP_LATE_CON_" + suffix, projectId);
		long purchasedId = insertMaterial("MRP_LATE_MAT_" + suffix, "RAW_MATERIAL", "PURCHASED", categoryId,
				unitId);
		insertSalesDemand("MRP_LATE_SO_" + suffix, customerId, projectId, contractId, purchasedId, unitId, demandDate,
				"6.000000");
		insertPurchaseSupply("MRP_LATE_PO_" + suffix, supplierId, null, purchasedId, unitId, demandDate.plusDays(4),
				"6.000000", "PUBLIC");

		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(projectId, demandDate, "MRP-LATE-RUN-" + suffix)));
		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/requirements?page=1&pageSize=20"));
		JsonNode requirement = findRequirementByMaterial(requirements.get("items"), purchasedId);
		assertDecimal(requirement, "coveredQuantity", "0.000000");
		assertDecimal(requirement, "shortageQuantity", "6.000000");

		JsonNode allocations = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/allocations?page=1&pageSize=20"));
		assertThat(allocations.get("items")).anySatisfy((item) -> {
			assertThat(item.get("supplyType").asText()).isEqualTo("PUBLIC_PURCHASE");
			assertThat(item.get("onTime").asBoolean()).isFalse();
			assertThat(item.get("availableDate").asText()).isEqualTo(demandDate.plusDays(4).toString());
			assertThat(item.get("excludedReasonCode").asText()).isEqualTo("SUPPLY_LATE");
		});
	}

	@Test
	void multipleDemandsAllocateProjectAndPublicSupplyDeterministicallyWithoutReusingPublicOrOtherProjectSupply()
			throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate firstDemandDate = LocalDate.now().plusDays(8);
		LocalDate secondDemandDate = firstDemandDate.plusDays(1);
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		long unitId = insertUnit("MRP_DET_U_" + suffix);
		long warehouseId = insertWarehouse("MRP_DET_WH_" + suffix);
		long categoryId = insertCategory("MRP_DET_CAT_" + suffix);
		long customerId = insertCustomer("MRP_DET_CUS_" + suffix);
		long supplierId = insertSupplier("MRP_DET_SUP_" + suffix);
		long projectId = insertProject("MRP_DET_PRJ_" + suffix, customerId, adminUserId);
		long otherProjectId = insertProject("MRP_DET_OTHER_PRJ_" + suffix, customerId, adminUserId);
		long contractId = insertContract("MRP_DET_CON_" + suffix, projectId);
		long materialId = insertMaterial("MRP_DET_MAT_" + suffix, "RAW_MATERIAL", "PURCHASED", categoryId,
				unitId);
		long firstLineId = insertSalesDemand("MRP_DET_SO_A_" + suffix, customerId, projectId, contractId, materialId,
				unitId, firstDemandDate, "8.000000");
		long secondLineId = insertSalesDemand("MRP_DET_SO_B_" + suffix, customerId, projectId, contractId, materialId,
				unitId, secondDemandDate, "8.000000");
		insertStock(warehouseId, materialId, unitId, "PROJECT", projectId, "4.000000");
		insertPurchaseSupply("MRP_DET_PO_PRJ_" + suffix, supplierId, projectId, materialId, unitId,
				firstDemandDate.minusDays(1), "3.000000", "PROJECT");
		insertStock(warehouseId, materialId, unitId, "PUBLIC", null, "5.000000");
		insertPurchaseSupply("MRP_DET_PO_PUB_" + suffix, supplierId, null, materialId, unitId,
				firstDemandDate.minusDays(1), "2.000000", "PUBLIC");
		insertStock(warehouseId, materialId, unitId, "PROJECT", otherProjectId, "10.000000");
		insertPurchaseSupply("MRP_DET_PO_OTHER_" + suffix, supplierId, otherProjectId, materialId, unitId,
				firstDemandDate.minusDays(1), "10.000000", "PROJECT");

		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(projectId, secondDemandDate, "MRP-DET-RUN-" + suffix)));

		assertDeterministicSupplySplit(admin, run.get("id").longValue(), firstLineId, secondLineId, otherProjectId);
		JsonNode recalculated = data(post(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue() + "/recalculate",
				runRequest(projectId, secondDemandDate, "MRP-DET-RECALC-" + suffix)));
		assertDeterministicSupplySplit(admin, recalculated.get("id").longValue(), firstLineId, secondLineId,
				otherProjectId);
	}

	@Test
	void invalidBomInputsReturnStablePlanningReasonCodes() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate demandDate = LocalDate.now().plusDays(8);

		DemandSeed missingBom = selfMadeDemand("MRP_NO_BOM_" + suffix, demandDate);
		String missingBomKey = "MRP-NO-BOM-" + suffix;
		assertPlanningRunRejectedWithCode(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(missingBom.projectId(), demandDate, missingBomKey)), missingBomKey,
				missingBom.projectId(), demandDate, "BOM_NOT_FOUND");

		DemandSeed futureBom = selfMadeDemand("MRP_FUTURE_BOM_" + suffix, demandDate);
		long futureChildId = insertMaterial("MRP_FUTURE_CHILD_" + suffix, "RAW_MATERIAL", "PURCHASED",
				futureBom.categoryId(), futureBom.unitId());
		insertBom("MRP_FUTURE_BOM_" + suffix, futureBom.materialId(), futureBom.unitId(), demandDate.plusDays(1),
				List.of(bomItem(1, futureChildId, futureBom.unitId(), "1.000000")));
		String futureBomKey = "MRP-FUTURE-BOM-" + suffix;
		assertPlanningRunRejectedWithCode(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(futureBom.projectId(), demandDate, futureBomKey)), futureBomKey, futureBom.projectId(),
				demandDate, "BOM_NOT_EFFECTIVE");

		DemandSeed cycleBom = selfMadeDemand("MRP_CYCLE_BOM_" + suffix, demandDate);
		long cycleChildId = insertMaterial("MRP_CYCLE_CHILD_" + suffix, "SEMI_FINISHED", "SELF_MADE",
				cycleBom.categoryId(), cycleBom.unitId());
		insertBom("MRP_CYCLE_PARENT_" + suffix, cycleBom.materialId(), cycleBom.unitId(), demandDate.minusDays(1),
				List.of(bomItem(1, cycleChildId, cycleBom.unitId(), "1.000000")));
		insertBom("MRP_CYCLE_CHILD_" + suffix, cycleChildId, cycleBom.unitId(), demandDate.minusDays(1),
				List.of(bomItem(1, cycleBom.materialId(), cycleBom.unitId(), "1.000000")));
		String cycleBomKey = "MRP-CYCLE-BOM-" + suffix;
		assertPlanningRunRejectedWithCode(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(cycleBom.projectId(), demandDate, cycleBomKey)), cycleBomKey, cycleBom.projectId(),
				demandDate, "BOM_CYCLE_DETECTED");

		DemandSeed conversionBom = selfMadeDemand("MRP_CONV_BOM_" + suffix, demandDate);
		long businessUnitId = insertUnit("MRP_CONV_BUS_U_" + suffix);
		long conversionChildId = insertMaterial("MRP_CONV_CHILD_" + suffix, "RAW_MATERIAL", "PURCHASED",
				conversionBom.categoryId(), conversionBom.unitId());
		insertBomMissingConversion("MRP_CONV_BOM_" + suffix, conversionBom.materialId(), conversionBom.unitId(),
				conversionChildId, businessUnitId, demandDate.minusDays(1));
		String conversionBomKey = "MRP-CONV-BOM-" + suffix;
		assertPlanningRunRejectedWithCode(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(conversionBom.projectId(), demandDate, conversionBomKey)), conversionBomKey,
				conversionBom.projectId(), demandDate, "UNIT_CONVERSION_REQUIRED");
	}

	@Test
	void snapshotExpiryConcurrencyAndIdempotencyDoNotDuplicateRunsOrAllowExpiredWrites() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate demandDate = LocalDate.now().plusDays(8);

		DemandSeed missingBom = selfMadeDemand("MRP_IDEMP_FAIL_" + suffix, demandDate);
		String failureKey = "MRP-IDEMP-FAIL-" + suffix;
		assertPlanningRunRejectedWithCode(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(missingBom.projectId(), demandDate, failureKey)), failureKey, missingBom.projectId(),
				demandDate, "BOM_NOT_FOUND");
		long failedRunId = runIdByIdempotencyKey(failureKey);
		assertPlanningRunRejectedWithCode(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(missingBom.projectId(), demandDate, failureKey)), failureKey, missingBom.projectId(),
				demandDate, "BOM_NOT_FOUND");
		assertThat(runIdByIdempotencyKey(failureKey)).isEqualTo(failedRunId);

		Fixture idempotentFixture = fixture();
		String successKey = "MRP-IDEMP-SUCCESS-" + suffix;
		Map<String, Object> successRequest = runRequest(idempotentFixture.projectId(),
				idempotentFixture.demandDate(), successKey);
		JsonNode successRun = data(post(admin, "/api/admin/planning/material-requirement-runs", successRequest));
		JsonNode sameSuccessRun = data(post(admin, "/api/admin/planning/material-requirement-runs", successRequest));
		assertThat(sameSuccessRun.get("id").longValue()).isEqualTo(successRun.get("id").longValue());
		assertThat(countRowsForSql(
				"select count(*) from mrp_calculation_run where idempotency_key = ?", successKey)).isOne();
		assertError(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(idempotentFixture.projectId(), idempotentFixture.demandDate().plusDays(1), successKey)),
				HttpStatus.CONFLICT, "MATERIAL_REQUIREMENT_RUN_CONFLICT");

		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + successRun.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode suggestion = findSuggestion(suggestions.get("items"), idempotentFixture.rawMaterialId(),
				"PURCHASE_REQUISITION");
		this.jdbcTemplate.update("""
				update mrp_calculation_run
				set expires_at = now() - interval '1 minute'
				where id = ?
				""", successRun.get("id").longValue());
		JsonNode expiredRun = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + successRun.get("id").longValue()));
		assertThat(expiredRun.get("status").asText()).isEqualTo("EXPIRED");
		assertError(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + suggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", suggestion.get("version").longValue(), "idempotencyKey",
						"MRP-EXPIRED-CONFIRM-" + suffix)),
				HttpStatus.CONFLICT, "MATERIAL_REQUIREMENT_RUN_EXPIRED");

		Fixture concurrentFixture = fixture();
		AuthenticatedSession firstAdmin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession secondAdmin = login("admin", ADMIN_PASSWORD);
		Map<String, Object> firstRequest = runRequest(concurrentFixture.projectId(), concurrentFixture.demandDate(),
				"MRP-CONCURRENT-A-" + suffix);
		Map<String, Object> secondRequest = runRequest(concurrentFixture.projectId(), concurrentFixture.demandDate(),
				"MRP-CONCURRENT-B-" + suffix);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ResponseEntity<String>> first = executor.submit(() -> postAfterStart(firstAdmin,
					"/api/admin/planning/material-requirement-runs", firstRequest, start));
			Future<ResponseEntity<String>> second = executor.submit(() -> postAfterStart(secondAdmin,
					"/api/admin/planning/material-requirement-runs", secondRequest, start));
			start.countDown();
			JsonNode firstRun = data(response(first));
			JsonNode secondRun = data(response(second));
			assertThat(secondRun.get("id").longValue()).isEqualTo(firstRun.get("id").longValue());
			assertThat(countRowsForSql("""
					select count(*)
					from mrp_calculation_run
					where scope_hash = ?
					and source_fingerprint = ?
					and status = 'COMPLETED'
					""", firstRun.get("scopeHash").asText(), firstRun.get("sourceFingerprint").asText())).isOne();
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void planningPermissionsRejectMissingViewRunSuggestionManageAndRequisitionPermissions() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		Fixture fixture = fixture();
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-AUTH-RUN-" + suffix)));
		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode suggestion = findSuggestion(suggestions.get("items"), fixture.rawMaterialId(), "PURCHASE_REQUISITION");

		AuthenticatedSession exportOnly = createPlanningUserAndLogin("mrp-no-view-", "MRP_NO_VIEW_",
				List.of("planning:material-requirement:export"));
		assertError(get(exportOnly, "/api/admin/planning/material-requirement-runs?page=1&pageSize=20"),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertError(get(exportOnly, "/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertError(postWithIdempotency(exportOnly, "/api/admin/export-tasks",
				Map.of("taskType", "MATERIAL_REQUIREMENT_RUN_EXPORT", "objectType", "MATERIAL_REQUIREMENT_RUN",
						"objectId", run.get("id").longValue(), "filters", Map.of("runId", run.get("id").longValue())),
				"MRP-AUTH-EXPORT-" + suffix),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		AuthenticatedSession viewOnly = createPlanningUserAndLogin("mrp-view-only-", "MRP_VIEW_ONLY_",
				List.of("planning:material-requirement:view"));
		assertError(post(viewOnly, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-AUTH-CALC-" + suffix)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertError(post(viewOnly,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue() + "/recalculate",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-AUTH-RECALC-" + suffix)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		AuthenticatedSession withoutSuggestionManage = createPlanningUserAndLogin("mrp-no-suggest-manage-",
				"MRP_NO_SUGGEST_MANAGE_", List.of("planning:material-requirement:view",
						"planning:material-requirement:calculate"));
		assertError(put(withoutSuggestionManage,
				"/api/admin/planning/material-requirement-suggestions/" + suggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", suggestion.get("version").longValue(), "idempotencyKey",
						"MRP-AUTH-CONFIRM-" + suffix)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertError(put(withoutSuggestionManage,
				"/api/admin/planning/material-requirement-suggestions/" + suggestion.get("id").longValue()
						+ "/dismiss",
				Map.of("version", suggestion.get("version").longValue(), "idempotencyKey",
						"MRP-AUTH-DISMISS-" + suffix, "reason", "权限拒绝验证")),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		JsonNode confirmed = data(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + suggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", suggestion.get("version").longValue(), "idempotencyKey",
						"MRP-AUTH-ADMIN-CONFIRM-" + suffix)));
		AuthenticatedSession convertOnly = createPlanningUserAndLogin("mrp-convert-only-", "MRP_CONVERT_ONLY_",
				List.of("planning:material-requirement:view", "planning:material-requirement:convert-requisition"));
		assertError(post(convertOnly,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-requisition",
				Map.of("version", confirmed.get("version").longValue(), "idempotencyKey",
						"MRP-AUTH-CONVERT-NO-024-" + suffix)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		AuthenticatedSession procurementOnly = createPlanningUserAndLogin("mrp-procurement-only-",
				"MRP_PROCUREMENT_ONLY_", List.of("planning:material-requirement:view",
						"procurement:requisition:create"));
		assertError(post(procurementOnly,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-requisition",
				Map.of("version", confirmed.get("version").longValue(), "idempotencyKey",
						"MRP-AUTH-CONVERT-NO-026-" + suffix)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	@Test
	void stage027ProductionConversionRequiresPlanningAndTargetCreatePermissions() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate selfMadeDate = LocalDate.now().plusDays(12);
		DemandSeed selfMade = selfMadeDemand("MRP_027_AUTH_WO_" + suffix, selfMadeDate);
		insertBom("MRP_027_AUTH_WO_BOM_" + suffix, selfMade.materialId(), selfMade.unitId(),
				LocalDate.now().minusDays(1), List.of());
		JsonNode selfMadeRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(selfMade.projectId(), selfMadeDate, "MRP-027-AUTH-WO-RUN-" + suffix)));
		JsonNode selfMadeSuggestion = findSuggestion(data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + selfMadeRun.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50")).get("items"), selfMade.materialId(),
				"PRODUCTION_ORDER");
		JsonNode selfMadeConfirmed = data(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + selfMadeSuggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", selfMadeSuggestion.get("version").longValue(), "idempotencyKey",
						"MRP-027-AUTH-WO-CONFIRM-" + suffix)));
		JsonNode confirmedSelfMadeForAdmin = findSuggestion(data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + selfMadeRun.get("id").longValue()
						+ "/suggestions?status=CONFIRMED&page=1&pageSize=50")).get("items"),
				selfMade.materialId(), "PRODUCTION_ORDER");
		assertThat(confirmedSelfMadeForAdmin.get("conversionAllowed").asBoolean()).isTrue();
		assertThat(confirmedSelfMadeForAdmin.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("CONVERT_WORK_ORDER"));
		assertThat(confirmedSelfMadeForAdmin.hasNonNull("actionDisabledReason")).isFalse();

		AuthenticatedSession planningOnly = createPlanningUserAndLogin("mrp-027-prod-planning-only-",
				"MRP_027_PROD_PLANNING_ONLY_", List.of("planning:material-requirement:view",
						"planning:material-requirement:convert-production"));
		JsonNode selfMadePlanningOnly = findSuggestion(data(get(planningOnly,
				"/api/admin/planning/material-requirement-runs/" + selfMadeRun.get("id").longValue()
						+ "/suggestions?status=CONFIRMED&page=1&pageSize=50")).get("items"),
				selfMade.materialId(), "PRODUCTION_ORDER");
		assertThat(selfMadePlanningOnly.get("conversionAllowed").asBoolean()).isFalse();
		assertThat(selfMadePlanningOnly.get("allowedActions")).isEmpty();
		assertThat(selfMadePlanningOnly.get("actionDisabledReason").asText()).isEqualTo("当前用户权限不足");
		assertError(post(planningOnly,
				"/api/admin/planning/material-requirement-suggestions/" + selfMadeConfirmed.get("id").longValue()
						+ "/convert-work-order",
				Map.of("version", selfMadeConfirmed.get("version").longValue(), "idempotencyKey",
						"MRP-027-AUTH-WO-PLANNING-ONLY-" + suffix)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		AuthenticatedSession workOrderCreateOnly = createPlanningUserAndLogin("mrp-027-prod-target-only-",
				"MRP_027_PROD_TARGET_ONLY_", List.of("production:work-order:create"));
		AuthenticatedSession workOrderCreateViewOnly = createPlanningUserAndLogin("mrp-027-prod-target-view-only-",
				"MRP_027_PROD_TARGET_VIEW_ONLY_",
				List.of("planning:material-requirement:view", "production:work-order:create"));
		JsonNode selfMadeCreateOnly = findSuggestion(data(get(workOrderCreateViewOnly,
				"/api/admin/planning/material-requirement-runs/" + selfMadeRun.get("id").longValue()
						+ "/suggestions?status=CONFIRMED&page=1&pageSize=50")).get("items"),
				selfMade.materialId(), "PRODUCTION_ORDER");
		assertThat(selfMadeCreateOnly.get("conversionAllowed").asBoolean()).isFalse();
		assertThat(selfMadeCreateOnly.get("allowedActions")).isEmpty();
		assertThat(selfMadeCreateOnly.get("actionDisabledReason").asText()).isEqualTo("当前用户权限不足");
		assertError(post(workOrderCreateOnly,
				"/api/admin/planning/material-requirement-suggestions/" + selfMadeConfirmed.get("id").longValue()
						+ "/convert-work-order",
				Map.of("version", selfMadeConfirmed.get("version").longValue(), "idempotencyKey",
						"MRP-027-AUTH-WO-TARGET-ONLY-" + suffix)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		LocalDate outsourcedDate = LocalDate.now().plusDays(13);
		DemandSeed outsourced = outsourcedDemand("MRP_027_AUTH_OS_" + suffix, outsourcedDate);
		insertBom("MRP_027_AUTH_OS_BOM_" + suffix, outsourced.materialId(), outsourced.unitId(),
				LocalDate.now().minusDays(1), List.of());
		JsonNode outsourcedRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(outsourced.projectId(), outsourcedDate, "MRP-027-AUTH-OS-RUN-" + suffix)));
		JsonNode outsourcedSuggestion = findSuggestion(data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + outsourcedRun.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50")).get("items"), outsourced.materialId(),
				"PRODUCTION_ORDER");
		JsonNode outsourcedConfirmed = data(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + outsourcedSuggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", outsourcedSuggestion.get("version").longValue(), "idempotencyKey",
						"MRP-027-AUTH-OS-CONFIRM-" + suffix)));
		JsonNode confirmedOutsourcedForAdmin = findSuggestion(data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + outsourcedRun.get("id").longValue()
						+ "/suggestions?status=CONFIRMED&page=1&pageSize=50")).get("items"),
				outsourced.materialId(), "PRODUCTION_ORDER");
		assertThat(confirmedOutsourcedForAdmin.get("conversionAllowed").asBoolean()).isTrue();
		assertThat(confirmedOutsourcedForAdmin.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("CONVERT_OUTSOURCING_ORDER"));
		assertThat(confirmedOutsourcedForAdmin.hasNonNull("actionDisabledReason")).isFalse();

		AuthenticatedSession outsourcingPlanningOnly = createPlanningUserAndLogin("mrp-027-os-planning-only-",
				"MRP_027_OS_PLANNING_ONLY_", List.of("planning:material-requirement:view",
						"planning:material-requirement:convert-outsourcing"));
		JsonNode outsourcedPlanningOnly = findSuggestion(data(get(outsourcingPlanningOnly,
				"/api/admin/planning/material-requirement-runs/" + outsourcedRun.get("id").longValue()
						+ "/suggestions?status=CONFIRMED&page=1&pageSize=50")).get("items"),
				outsourced.materialId(), "PRODUCTION_ORDER");
		assertThat(outsourcedPlanningOnly.get("conversionAllowed").asBoolean()).isFalse();
		assertThat(outsourcedPlanningOnly.get("allowedActions")).isEmpty();
		assertThat(outsourcedPlanningOnly.get("actionDisabledReason").asText()).isEqualTo("当前用户权限不足");
		assertError(post(outsourcingPlanningOnly,
				"/api/admin/planning/material-requirement-suggestions/" + outsourcedConfirmed.get("id").longValue()
						+ "/convert-outsourcing-order",
				Map.of("version", outsourcedConfirmed.get("version").longValue(), "idempotencyKey",
						"MRP-027-AUTH-OS-PLANNING-ONLY-" + suffix)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		AuthenticatedSession outsourcingCreateOnly = createPlanningUserAndLogin("mrp-027-os-target-only-",
				"MRP_027_OS_TARGET_ONLY_", List.of("production:outsourcing:create"));
		AuthenticatedSession outsourcingCreateViewOnly = createPlanningUserAndLogin("mrp-027-os-target-view-only-",
				"MRP_027_OS_TARGET_VIEW_ONLY_",
				List.of("planning:material-requirement:view", "production:outsourcing:create"));
		JsonNode outsourcedCreateOnly = findSuggestion(data(get(outsourcingCreateViewOnly,
				"/api/admin/planning/material-requirement-runs/" + outsourcedRun.get("id").longValue()
						+ "/suggestions?status=CONFIRMED&page=1&pageSize=50")).get("items"),
				outsourced.materialId(), "PRODUCTION_ORDER");
		assertThat(outsourcedCreateOnly.get("conversionAllowed").asBoolean()).isFalse();
		assertThat(outsourcedCreateOnly.get("allowedActions")).isEmpty();
		assertThat(outsourcedCreateOnly.get("actionDisabledReason").asText()).isEqualTo("当前用户权限不足");
		assertError(post(outsourcingCreateOnly,
				"/api/admin/planning/material-requirement-suggestions/" + outsourcedConfirmed.get("id").longValue()
						+ "/convert-outsourcing-order",
				Map.of("version", outsourcedConfirmed.get("version").longValue(), "idempotencyKey",
						"MRP-027-AUTH-OS-TARGET-ONLY-" + suffix)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertThat(countRowsForSql("select count(*) from mfg_work_order where source_mrp_suggestion_id = ?",
				selfMadeConfirmed.get("id").longValue())).isZero();
		assertThat(countRowsForSql("select count(*) from mfg_outsourcing_order where source_mrp_suggestion_id = ?",
				outsourcedConfirmed.get("id").longValue())).isZero();
	}

	@Test
	void substituteMaterialsOnlyCreateHintsWithoutChangingCoverageOrAutomaticSuggestions() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		Fixture fixture = fixture();
		insertStock(fixture.warehouseId(), fixture.substituteMaterialId(), fixture.unitId(), "PROJECT",
				fixture.projectId(), "99.000000");

		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-SUB-RUN-" + suffix)));
		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/requirements?page=1&pageSize=50"));
		JsonNode rawRequirement = findRequirement(requirements.get("items"), fixture.rawMaterialId());
		assertDecimal(rawRequirement, "requiredQuantity", "18.000000");
		assertDecimal(rawRequirement, "coveredQuantity", "13.000000");
		assertDecimal(rawRequirement, "shortageQuantity", "5.000000");

		JsonNode hints = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/substitute-hints?page=1&pageSize=50"));
		JsonNode hint = findSubstituteHint(hints.get("items"), rawRequirement.get("id").longValue(),
				fixture.rawMaterialId(), fixture.substituteMaterialId());
		assertThat(hint.get("priority").intValue()).isEqualTo(1);
		assertDecimal(hint, "substituteRate", "1.000000");

		JsonNode allocations = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/allocations?page=1&pageSize=50"));
		assertNoMaterial(allocations.get("items"), fixture.substituteMaterialId(), "allocation");
		assertThat(countRowsForSql(
				"select count(*) from mrp_supply_allocation where run_id = ? and material_id = ?",
				run.get("id").longValue(), fixture.substituteMaterialId())).isZero();

		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode purchaseSuggestion = findSuggestion(suggestions.get("items"), fixture.rawMaterialId(),
				"PURCHASE_REQUISITION");
		assertDecimal(purchaseSuggestion, "suggestedQuantity", "5.000000");
		assertNoMaterial(suggestions.get("items"), fixture.substituteMaterialId(), "suggestion");
		assertThat(countRowsForSql("select count(*) from mrp_suggestion where run_id = ? and material_id = ?",
				run.get("id").longValue(), fixture.substituteMaterialId())).isZero();
	}

	@Test
	void redA_deliveryPlansRemainSeparateDemandSourcesAndApiTraceable() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		long unitId = insertUnit("MRP_RED_A_DEL_U_" + suffix);
		long categoryId = insertCategory("MRP_RED_A_DEL_CAT_" + suffix);
		long customerId = insertCustomer("MRP_RED_A_DEL_CUS_" + suffix);
		long projectId = insertProject("MRP_RED_A_DEL_PRJ_" + suffix, customerId, adminUserId);
		long contractId = insertContract("MRP_RED_A_DEL_CON_" + suffix, projectId);
		long materialId = insertMaterial("MRP_RED_A_DEL_MAT_" + suffix, "FINISHED_GOOD", "PURCHASED", categoryId,
				unitId);
		LocalDate firstDate = LocalDate.now().plusDays(7);
		LocalDate secondDate = LocalDate.now().plusDays(14);
		DeliveryPlanDemandSeed demand = insertSalesDemandWithDeliveryPlans("MRP_RED_A_DEL_SO_" + suffix,
				customerId, projectId, contractId, materialId, unitId, firstDate, secondDate, "4.000000",
				"6.000000");

		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(projectId, secondDate, "MRP-RED-A-DELIVERY-" + suffix)));
		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/requirements?page=1&pageSize=50"));

		assertThat(requirements.get("items").size()).isEqualTo(2);
		assertThat(requirements.get("items"))
			.anySatisfy((item) -> {
				assertThat(item.get("demandSourceLineId").longValue()).isEqualTo(demand.firstPlanId());
				assertThat(item.hasNonNull("deliveryPlanNo")).isTrue();
				assertThat(item.get("deliveryPlanNo").asText()).isEqualTo("DP-1");
				assertThat(item.hasNonNull("requiredDate")).isTrue();
				assertThat(item.get("requiredDate").asText()).isEqualTo(firstDate.toString());
				assertDecimal(item, "requiredQuantity", "4.000000");
			})
			.anySatisfy((item) -> {
				assertThat(item.get("demandSourceLineId").longValue()).isEqualTo(demand.secondPlanId());
				assertThat(item.hasNonNull("deliveryPlanNo")).isTrue();
				assertThat(item.get("deliveryPlanNo").asText()).isEqualTo("DP-2");
				assertThat(item.hasNonNull("requiredDate")).isTrue();
				assertThat(item.get("requiredDate").asText()).isEqualTo(secondDate.toString());
				assertDecimal(item, "requiredQuantity", "6.000000");
			});
	}

	@Test
	void redA_stockNetAvailabilityDoesNotSubtractActiveReservationTwice() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		long unitId = insertUnit("MRP_RED_A_STOCK_U_" + suffix);
		long warehouseId = insertWarehouse("MRP_RED_A_STOCK_WH_" + suffix);
		long categoryId = insertCategory("MRP_RED_A_STOCK_CAT_" + suffix);
		long customerId = insertCustomer("MRP_RED_A_STOCK_CUS_" + suffix);
		long projectId = insertProject("MRP_RED_A_STOCK_PRJ_" + suffix, customerId, adminUserId);
		long contractId = insertContract("MRP_RED_A_STOCK_CON_" + suffix, projectId);
		long materialId = insertMaterial("MRP_RED_A_STOCK_MAT_" + suffix, "RAW_MATERIAL", "PURCHASED", categoryId,
				unitId);
		LocalDate demandDate = LocalDate.now().plusDays(10);
		insertSalesDemand("MRP_RED_A_STOCK_SO_" + suffix, customerId, projectId, contractId, materialId, unitId,
				demandDate, "6.000000");
		long costLayerId = insertProjectCostLayer(projectId, materialId, "MRP_RED_A_STOCK_LAYER_" + suffix,
				"10.000000");
		insertStockWithAvailability(warehouseId, materialId, unitId, "PROJECT", projectId, "10.000000",
				"4.000000", "QUALIFIED", costLayerId);
		insertReservation("MRP_RED_A_STOCK_RSV_" + suffix, warehouseId, materialId, unitId, "PROJECT", projectId,
				costLayerId, "4.000000");
		insertStockWithAvailability(warehouseId, materialId, unitId, "PROJECT", projectId, "100.000000",
				"0.000000", "REJECTED", costLayerId);

		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(projectId, demandDate, "MRP-RED-A-STOCK-" + suffix)));
		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/requirements?page=1&pageSize=50"));
		JsonNode requirement = findRequirementByMaterial(requirements.get("items"), materialId);

		assertDecimal(requirement, "coveredQuantity", "6.000000");
		assertDecimal(requirement, "shortageQuantity", "0.000000");
		JsonNode allocations = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/allocations?page=1&pageSize=50"));
		assertAllocationTotal(allocations.get("items"), requirement.get("id").longValue(), "PROJECT_STOCK",
				"6.000000");
	}

	@Test
	void redB_recalculateWithOnlyVersionAndIdempotencyKeyReusesOriginalScope() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();
		Map<String, Object> originalRequest = runRequest(fixture.projectId(), fixture.demandDate(),
				"MRP-RED-B-RECALC-ORIGINAL-" + SEQUENCE.incrementAndGet());
		originalRequest.put("includePublicDemand", false);
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs", originalRequest));
		Map<String, Object> oldRunBefore = this.jdbcTemplate.queryForMap("""
				select scope_type, project_id, demand_date_to, include_public_demand, status, version
				from mrp_calculation_run
				where id = ?
				""", run.get("id").longValue());

		JsonNode recalculated = data(post(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue() + "/recalculate",
				Map.of("version", run.get("version").longValue(), "idempotencyKey",
						"MRP-RED-B-RECALC-NEXT-" + SEQUENCE.incrementAndGet())));

		assertThat(recalculated.get("id").longValue()).isNotEqualTo(run.get("id").longValue());
		assertThat(recalculated.get("scopeType").asText()).isEqualTo("PROJECT");
		assertThat(recalculated.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(recalculated.get("demandDateTo").asText()).isEqualTo(fixture.demandDate().toString());
		assertThat(recalculated.get("includePublicDemand").asBoolean()).isFalse();
		Long previousRunId = this.jdbcTemplate.queryForObject("""
				select previous_run_id
				from mrp_calculation_run
				where id = ?
				""", Long.class, recalculated.get("id").longValue());
		assertThat(previousRunId).isEqualTo(run.get("id").longValue());
		Map<String, Object> oldRunAfter = this.jdbcTemplate.queryForMap("""
				select scope_type, project_id, demand_date_to, include_public_demand, status, version
				from mrp_calculation_run
				where id = ?
				""", run.get("id").longValue());
		assertThat(oldRunAfter).isEqualTo(oldRunBefore);
	}

	@Test
	void redB_lateSupplyExplanationDoesNotConsumeLaterOnTimeCoverage() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		long unitId = insertUnit("MRP_RED_B_LATE_U_" + suffix);
		long categoryId = insertCategory("MRP_RED_B_LATE_CAT_" + suffix);
		long customerId = insertCustomer("MRP_RED_B_LATE_CUS_" + suffix);
		long supplierId = insertSupplier("MRP_RED_B_LATE_SUP_" + suffix);
		long projectId = insertProject("MRP_RED_B_LATE_PRJ_" + suffix, customerId, adminUserId);
		long contractId = insertContract("MRP_RED_B_LATE_CON_" + suffix, projectId);
		long materialId = insertMaterial("MRP_RED_B_LATE_MAT_" + suffix, "RAW_MATERIAL", "PURCHASED", categoryId,
				unitId);
		LocalDate earlyDate = LocalDate.now().plusDays(9);
		LocalDate laterDate = LocalDate.now().plusDays(14);
		long earlyLineId = insertSalesDemand("MRP_RED_B_LATE_SO_EARLY_" + suffix, customerId, projectId,
				contractId, materialId, unitId, earlyDate, "5.000000");
		long laterLineId = insertSalesDemand("MRP_RED_B_LATE_SO_LATER_" + suffix, customerId, projectId,
				contractId, materialId, unitId, laterDate, "5.000000");
		insertPurchaseSupply("MRP_RED_B_LATE_PO_" + suffix, supplierId, null, materialId, unitId, laterDate,
				"5.000000", "PUBLIC");

		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(projectId, laterDate, "MRP-RED-B-LATE-" + suffix)));
		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/requirements?page=1&pageSize=50"));
		JsonNode earlyRequirement = findRequirementBySourceLine(requirements.get("items"), earlyLineId);
		JsonNode laterRequirement = findRequirementBySourceLine(requirements.get("items"), laterLineId);
		assertDecimal(earlyRequirement, "coveredQuantity", "0.000000");
		assertDecimal(earlyRequirement, "shortageQuantity", "5.000000");
		assertDecimal(laterRequirement, "coveredQuantity", "5.000000");
		assertDecimal(laterRequirement, "shortageQuantity", "0.000000");

		JsonNode allocations = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/allocations?page=1&pageSize=50"));
		assertThat(allocations.get("items")).anySatisfy((item) -> {
			assertThat(item.get("requirementLineId").longValue()).isEqualTo(earlyRequirement.get("id").longValue());
			assertThat(item.get("supplyType").asText()).isEqualTo("PUBLIC_PURCHASE");
			assertThat(item.get("onTime").asBoolean()).isFalse();
			assertThat(item.get("excludedReasonCode").asText()).isEqualTo("SUPPLY_LATE");
		});
		assertAllocationTotal(allocations.get("items"), laterRequirement.get("id").longValue(), "PUBLIC_PURCHASE",
				"5.000000");
		BigDecimal effectiveAllocated = BigDecimal.ZERO;
		for (JsonNode item : allocations.get("items")) {
			if (item.get("onTime").asBoolean()) {
				effectiveAllocated = effectiveAllocated.add(new BigDecimal(item.get("allocatedQuantity").asText()));
			}
		}
		assertThat(effectiveAllocated).isLessThanOrEqualTo(new BigDecimal("5.000000"));
	}

	@Test
	void redB_historicalDetailRemainsReadableAndStaleWhenBomOrMaterialSourceInvalidated() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate bomDate = LocalDate.now().plusDays(220);
		DemandSeed bomSeed = selfMadeDemand("MRP_RED_B_STALE_BOM_" + suffix, bomDate);
		long bomChildId = insertMaterial("MRP_RED_B_STALE_BOM_RAW_" + suffix, "RAW_MATERIAL", "PURCHASED",
				bomSeed.categoryId(), bomSeed.unitId());
		insertBom("MRP_RED_B_STALE_BOM_" + suffix, bomSeed.materialId(), bomSeed.unitId(), LocalDate.now().minusDays(1),
				List.of(bomItem(1, bomChildId, bomSeed.unitId(), "1.000000")));
		JsonNode bomRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(bomSeed.projectId(), bomDate, "MRP-RED-B-STALE-BOM-" + suffix)));
		this.jdbcTemplate.update("""
				update mfg_bom
				set status = 'DISABLED',
				    updated_at = now(),
				    version = version + 1
				where parent_material_id = ?
				""", bomSeed.materialId());
		JsonNode staleBomRun = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + bomRun.get("id").longValue()));
		assertThat(staleBomRun.get("status").asText()).isEqualTo("STALE");
		assertThat(staleBomRun.get("statusReason").asText()).isEqualTo("SOURCE_CHANGED");

		LocalDate materialDate = LocalDate.now().plusDays(221);
		DemandSeed materialSeed = selfMadeDemand("MRP_RED_B_STALE_MAT_" + suffix, materialDate);
		long materialChildId = insertMaterial("MRP_RED_B_STALE_MAT_RAW_" + suffix, "RAW_MATERIAL", "PURCHASED",
				materialSeed.categoryId(), materialSeed.unitId());
		insertBom("MRP_RED_B_STALE_MAT_" + suffix, materialSeed.materialId(), materialSeed.unitId(),
				LocalDate.now().minusDays(1), List.of(bomItem(1, materialChildId, materialSeed.unitId(), "1.000000")));
		JsonNode materialRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(materialSeed.projectId(), materialDate, "MRP-RED-B-STALE-MAT-" + suffix)));
		this.jdbcTemplate.update("""
				update mst_material
				set status = 'DISABLED',
				    updated_at = now(),
				    version = version + 1
				where id = ?
				""", materialSeed.materialId());
		JsonNode staleMaterialRun = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + materialRun.get("id").longValue()));
		assertThat(staleMaterialRun.get("status").asText()).isEqualTo("STALE");
		assertThat(staleMaterialRun.get("statusReason").asText()).isEqualTo("SOURCE_CHANGED");
	}

	@Test
	void redC_suggestionTypesAndOutsourcedBomContractsMatchV28() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		SoftAssertions softly = new SoftAssertions();
		String suggestionTypeConstraint = this.jdbcTemplate.queryForObject("""
				select pg_get_constraintdef(oid)
				from pg_constraint
				where conname = 'ck_mrp_suggestion_type'
				""", String.class);
		softly.assertThat(suggestionTypeConstraint)
			.contains("PURCHASE_REQUISITION", "PRODUCTION_ORDER", "USE_PUBLIC_STOCK", "USE_EXISTING_SUPPLY")
			.doesNotContain("OUTSOURCING");

		Fixture supplyFixture = fixture();
		JsonNode supplyRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(supplyFixture.projectId(), supplyFixture.demandDate(), "MRP-RED-C-SUG-" + suffix)));
		JsonNode supplySuggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + supplyRun.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		List<String> suggestionTypes = suggestionTypes(supplySuggestions.get("items"));
		softly.assertThat(suggestionTypes)
			.contains("USE_PUBLIC_STOCK", "USE_EXISTING_SUPPLY")
			.doesNotContain("OUTSOURCING");
		for (JsonNode suggestion : supplySuggestions.get("items")) {
			if ("USE_PUBLIC_STOCK".equals(suggestion.get("suggestionType").asText())
					|| "USE_EXISTING_SUPPLY".equals(suggestion.get("suggestionType").asText())) {
				softly.assertThat(suggestion.get("conversionAllowed").asBoolean()).isFalse();
				softly.assertThat(suggestion.hasNonNull("actionDisabledReason")).isTrue();
			}
		}

		LocalDate demandDate = LocalDate.now().plusDays(18);
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		long unitId = insertUnit("MRP_RED_C_OUT_U_" + suffix);
		long categoryId = insertCategory("MRP_RED_C_OUT_CAT_" + suffix);
		long customerId = insertCustomer("MRP_RED_C_OUT_CUS_" + suffix);
		long projectId = insertProject("MRP_RED_C_OUT_PRJ_" + suffix, customerId, adminUserId);
		long contractId = insertContract("MRP_RED_C_OUT_CON_" + suffix, projectId);
		long outsourcedId = insertMaterial("MRP_RED_C_OUT_MAT_" + suffix, "FINISHED_GOOD", "OUTSOURCED",
				categoryId, unitId);
		long rawId = insertMaterial("MRP_RED_C_OUT_RAW_" + suffix, "RAW_MATERIAL", "PURCHASED", categoryId,
				unitId);
		insertBom("MRP_RED_C_OUT_BOM_" + suffix, outsourcedId, unitId, LocalDate.now().minusDays(1),
				List.of(bomItem(1, rawId, unitId, "2.000000")));
		insertSalesDemand("MRP_RED_C_OUT_SO_" + suffix, customerId, projectId, contractId, outsourcedId, unitId,
				demandDate, "4.000000");
		JsonNode outsourcedRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(projectId, demandDate, "MRP-RED-C-OUT-" + suffix)));
		JsonNode outsourcedRequirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + outsourcedRun.get("id").longValue()
						+ "/requirements?page=1&pageSize=50"));
		JsonNode rawRequirement = findRequirementByMaterialOrNull(outsourcedRequirements.get("items"), rawId);
		softly.assertThat(rawRequirement).isNotNull();
		if (rawRequirement != null) {
			softly.assertThat(new BigDecimal(rawRequirement.get("requiredQuantity").asText()))
				.isEqualByComparingTo("8.000000");
		}
		JsonNode outsourcedSuggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + outsourcedRun.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode productionSuggestion = findSuggestionOrNull(outsourcedSuggestions.get("items"), outsourcedId,
				"PRODUCTION_ORDER");
		softly.assertThat(productionSuggestion).isNotNull();
		if (productionSuggestion != null) {
			softly.assertThat(productionSuggestion.hasNonNull("materialSourceType")).isTrue();
			if (productionSuggestion.hasNonNull("materialSourceType")) {
				softly.assertThat(productionSuggestion.get("materialSourceType").asText()).isEqualTo("OUTSOURCED");
			}
			softly.assertThat(productionSuggestion.get("conversionAllowed").asBoolean()).isTrue();
			softly.assertThat(productionSuggestion.get("allowedActions"))
				.anySatisfy((action) -> assertThat(action.asText()).isEqualTo("CONFIRM"));
		}

		long noBomMaterialId = insertMaterial("MRP_RED_C_OUT_NO_BOM_" + suffix, "FINISHED_GOOD", "OUTSOURCED",
				categoryId, unitId);
		insertSalesDemand("MRP_RED_C_OUT_NO_BOM_SO_" + suffix, customerId, projectId, contractId, noBomMaterialId,
				unitId, demandDate, "1.000000");
		ResponseEntity<String> noBomResponse = post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(projectId, demandDate, "MRP-RED-C-OUT-NO-BOM-" + suffix));
		softly.assertThat(noBomResponse.getStatusCode()).as(noBomResponse.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
		if (noBomResponse.getBody() != null) {
			softly.assertThat(this.objectMapper.readTree(noBomResponse.getBody()).get("code").asText())
				.isEqualTo("BOM_NOT_FOUND");
		}
		softly.assertAll();
	}

	@Test
	void redC_stableFailureAndExclusionReasonCodesAreVisibleAndDoNotCoverDemand() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		SoftAssertions softly = new SoftAssertions();
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);

		LocalDate disabledDate = LocalDate.now().plusDays(30);
		long disabledUnitId = insertUnit("MRP_RED_C_DISABLED_U_" + suffix);
		long disabledCategoryId = insertCategory("MRP_RED_C_DISABLED_CAT_" + suffix);
		long disabledCustomerId = insertCustomer("MRP_RED_C_DISABLED_CUS_" + suffix);
		long disabledProjectId = insertProject("MRP_RED_C_DISABLED_PRJ_" + suffix, disabledCustomerId, adminUserId);
		long disabledContractId = insertContract("MRP_RED_C_DISABLED_CON_" + suffix, disabledProjectId);
		long disabledMaterialId = insertMaterial("MRP_RED_C_DISABLED_MAT_" + suffix, "RAW_MATERIAL", "PURCHASED",
				disabledCategoryId, disabledUnitId);
		insertSalesDemand("MRP_RED_C_DISABLED_SO_" + suffix, disabledCustomerId, disabledProjectId,
				disabledContractId, disabledMaterialId, disabledUnitId, disabledDate, "1.000000");
		this.jdbcTemplate.update("""
				update mst_material
				set status = 'DISABLED',
				    updated_at = now(),
				    version = version + 1
				where id = ?
				""", disabledMaterialId);
		String disabledKey = "MRP-RED-C-DISABLED-" + suffix;
		ResponseEntity<String> disabledResponse = post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(disabledProjectId, disabledDate, disabledKey));
		softly.assertThat(disabledResponse.getStatusCode()).as(disabledResponse.getBody())
			.isEqualTo(HttpStatus.BAD_REQUEST);
		if (disabledResponse.getBody() != null) {
			softly.assertThat(this.objectMapper.readTree(disabledResponse.getBody()).get("code").asText())
				.isEqualTo("MATERIAL_DISABLED");
		}
		List<FailureRunRow> disabledRuns = failureRuns(disabledKey);
		softly.assertThat(disabledRuns).hasSize(1);
		if (!disabledRuns.isEmpty()) {
			softly.assertThat(disabledRuns.get(0).status()).isEqualTo("FAILED");
			softly.assertThat(disabledRuns.get(0).failureCode()).isEqualTo("MATERIAL_DISABLED");
		}

		LocalDate demandDate = LocalDate.now().plusDays(31);
		long unitId = insertUnit("MRP_RED_C_REASON_U_" + suffix);
		long warehouseId = insertWarehouse("MRP_RED_C_REASON_WH_" + suffix);
		long categoryId = insertCategory("MRP_RED_C_REASON_CAT_" + suffix);
		long customerId = insertCustomer("MRP_RED_C_REASON_CUS_" + suffix);
		long projectId = insertProject("MRP_RED_C_REASON_PRJ_" + suffix, customerId, adminUserId);
		long otherProjectId = insertProject("MRP_RED_C_REASON_OTHER_PRJ_" + suffix, customerId, adminUserId);
		long contractId = insertContract("MRP_RED_C_REASON_CON_" + suffix, projectId);
		long supplierId = insertSupplier("MRP_RED_C_REASON_SUP_" + suffix);
		long materialId = insertMaterial("MRP_RED_C_REASON_MAT_" + suffix, "RAW_MATERIAL", "PURCHASED", categoryId,
				unitId);
		insertSalesDemand("MRP_RED_C_REASON_SO_" + suffix, customerId, projectId, contractId, materialId, unitId,
				demandDate, "5.000000");
		long projectCostLayerId = insertProjectCostLayer(projectId, materialId, "MRP_RED_C_REASON_LAYER_" + suffix,
				"5.000000");
		insertStockWithAvailability(warehouseId, materialId, unitId, "PROJECT", projectId, "5.000000", "5.000000",
				"QUALIFIED", projectCostLayerId);
		insertReservation("MRP_RED_C_REASON_RSV_" + suffix, warehouseId, materialId, unitId, "PROJECT", projectId,
				projectCostLayerId, "5.000000");
		insertStockWithAvailability(warehouseId, materialId, unitId, "PROJECT", projectId, "9.000000", "0.000000",
				"REJECTED", projectCostLayerId);
		long otherCostLayerId = insertProjectCostLayer(otherProjectId, materialId,
				"MRP_RED_C_REASON_OTHER_LAYER_" + suffix, "5.000000");
		insertStockWithAvailability(warehouseId, materialId, unitId, "PROJECT", otherProjectId, "5.000000",
				"0.000000", "QUALIFIED", otherCostLayerId);
		insertPurchaseSupply("MRP_RED_C_REASON_PO_" + suffix, supplierId, null, materialId, unitId, demandDate,
				"5.000000", "PUBLIC");
		this.jdbcTemplate.update("update proc_purchase_order set status = 'DRAFT' where order_no = ?",
				"MRP_RED_C_REASON_PO_" + suffix);
		insertWorkOrder("MRP_RED_C_REASON_WO_" + suffix, materialId, unitId, warehouseId, demandDate, "5.000000",
				"0.000000", "RELEASED");

		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(projectId, demandDate, "MRP-RED-C-REASON-" + suffix)));
		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/requirements?page=1&pageSize=20"));
		JsonNode requirement = findRequirementByMaterial(requirements.get("items"), materialId);
		softly.assertThat(new BigDecimal(requirement.get("coveredQuantity").asText()))
			.isEqualByComparingTo("0.000000");
		softly.assertThat(new BigDecimal(requirement.get("shortageQuantity").asText()))
			.isEqualByComparingTo("5.000000");
		JsonNode allocations = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/allocations?page=1&pageSize=50"));
		List<String> reasons = allocationReasons(allocations.get("items"));
		softly.assertThat(reasons).contains("STOCK_NOT_QUALIFIED", "STOCK_RESERVED_OR_OCCUPIED",
				"SUPPLY_STATUS_NOT_COUNTED", "WORK_ORDER_NOT_PROJECT_BOUND", "CROSS_PROJECT_NOT_ALLOWED");
		softly.assertAll();
	}

	@Test
	void redC_auditLogsCoverRunAndSuggestionCriticalActions() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();

		Fixture successFixture = fixture();
		JsonNode successRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(successFixture.projectId(), successFixture.demandDate(), "MRP-RED-C-AUDIT-RUN-" + suffix)));
		assertAuditCount("MATERIAL_REQUIREMENT_RUN_CALCULATE", "SUCCESS", "MATERIAL_REQUIREMENT_RUN",
				successRun.get("id").asText(), null, 1);

		LocalDate failureDate = LocalDate.now().plusDays(40);
		DemandSeed failureSeed = selfMadeDemand("MRP_RED_C_AUDIT_FAIL_" + suffix, failureDate);
		String failureKey = "MRP-RED-C-AUDIT-FAIL-" + suffix;
		assertPlanningRunRejectedWithCode(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(failureSeed.projectId(), failureDate, failureKey)), failureKey, failureSeed.projectId(),
				failureDate, "BOM_NOT_FOUND");
		long failedRunId = runIdByIdempotencyKey(failureKey);
		assertAuditCount("MATERIAL_REQUIREMENT_RUN_CALCULATE", "FAILURE", "MATERIAL_REQUIREMENT_RUN",
				Long.toString(failedRunId), "BOM_NOT_FOUND", 1);

		Fixture convertFixture = fixture();
		JsonNode convertRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(convertFixture.projectId(), convertFixture.demandDate(), "MRP-RED-C-AUDIT-CONVERT-" + suffix)));
		JsonNode convertSuggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + convertRun.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode convertSuggestion = findSuggestion(convertSuggestions.get("items"), convertFixture.rawMaterialId(),
				"PURCHASE_REQUISITION");
		JsonNode confirmed = data(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + convertSuggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", convertSuggestion.get("version").longValue(), "idempotencyKey",
						"MRP-RED-C-AUDIT-CONFIRM-" + suffix)));
		assertAuditCount("MATERIAL_REQUIREMENT_SUGGESTION_CONFIRM", "SUCCESS", "MATERIAL_REQUIREMENT_SUGGESTION",
				convertSuggestion.get("id").asText(), null, 1);
		data(post(admin,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-requisition",
				Map.of("version", confirmed.get("version").longValue(), "idempotencyKey",
						"MRP-RED-C-AUDIT-CONVERT-REQ-" + suffix)));
		assertAuditCount("MATERIAL_REQUIREMENT_SUGGESTION_CONVERT_REQUISITION", "SUCCESS",
				"MATERIAL_REQUIREMENT_SUGGESTION", convertSuggestion.get("id").asText(), null, 1);

		Fixture dismissFixture = fixture();
		JsonNode dismissRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(dismissFixture.projectId(), dismissFixture.demandDate(), "MRP-RED-C-AUDIT-DISMISS-" + suffix)));
		JsonNode dismissSuggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + dismissRun.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode dismissSuggestion = findSuggestion(dismissSuggestions.get("items"), dismissFixture.rawMaterialId(),
				"PURCHASE_REQUISITION");
		data(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + dismissSuggestion.get("id").longValue()
						+ "/dismiss",
				Map.of("version", dismissSuggestion.get("version").longValue(), "idempotencyKey",
						"MRP-RED-C-AUDIT-DISMISS-SUG-" + suffix)));
		assertAuditCount("MATERIAL_REQUIREMENT_SUGGESTION_DISMISS", "SUCCESS", "MATERIAL_REQUIREMENT_SUGGESTION",
				dismissSuggestion.get("id").asText(), null, 1);
	}

	@Test
	void redD_listAndSubresourceFiltersRejectInvalidPaginationAndHonorCanonicalFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		SoftAssertions softly = new SoftAssertions();
		Fixture target = fixture();
		DemandScope targetScope = demandScope(target.orderLineId());
		Fixture other = fixture(target.demandDate().plusDays(14));
		JsonNode targetRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(target.projectId(), target.demandDate(), "MRP-RED-D-LIST-TARGET-" + suffix)));
		JsonNode otherRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(other.projectId(), other.demandDate(), "MRP-RED-D-LIST-OTHER-" + suffix)));
		this.jdbcTemplate.update("""
				update mrp_calculation_run
				set expires_at = now() - interval '1 minute'
				where id = ?
				""", otherRun.get("id").longValue());
		data(get(admin, "/api/admin/planning/material-requirement-runs/" + otherRun.get("id").longValue()));

		assertRunFilterOnlyReturns(softly, admin, "projectId=" + target.projectId(), targetRun, otherRun,
				"projectId");
		assertRunFilterOnlyReturns(softly, admin, "customerId=" + targetScope.customerId(), targetRun, otherRun,
				"customerId");
		assertRunFilterOnlyReturns(softly, admin, "contractId=" + targetScope.contractId(), targetRun, otherRun,
				"contractId");
		assertRunFilterOnlyReturns(softly, admin, "orderId=" + targetScope.orderId(), targetRun, otherRun,
				"orderId");
		assertRunFilterOnlyReturns(softly, admin, "materialId=" + targetScope.materialId(), targetRun, otherRun,
				"materialId");
		assertRunFilterOnlyReturns(softly, admin, "requiredDateTo=" + target.demandDate(), targetRun, otherRun,
				"requiredDateTo");
		JsonNode missingProject = data(get(admin,
				"/api/admin/planning/material-requirement-runs?projectId=999999999&page=1&pageSize=20"));
		softly.assertThat(missingProject.get("items").size()).as("不存在项目必须返回空列表").isZero();
		JsonNode expiredRuns = data(get(admin,
				"/api/admin/planning/material-requirement-runs?expired=true&page=1&pageSize=20"));
		softly.assertThat(pageContainsId(expiredRuns, otherRun.get("id").longValue())).as("expired=true 应包含过期快照")
			.isTrue();
		softly.assertThat(pageContainsId(expiredRuns, targetRun.get("id").longValue())).as("expired=true 不应包含未过期快照")
			.isFalse();
		assertInvalidPaginationRejected(softly, admin, "/api/admin/planning/material-requirement-runs?status=COMPLETED",
				"运行列表");

		long runId = targetRun.get("id").longValue();
		JsonNode allRequirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/requirements?page=1&pageSize=50"));
		JsonNode rawRequirement = findRequirementByMaterial(allRequirements.get("items"), target.rawMaterialId());
		JsonNode materialRequirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/requirements?materialId="
						+ target.rawMaterialId() + "&page=1&pageSize=50"));
		assertItemsOnlyLong(softly, materialRequirements, "materialId", target.rawMaterialId(), "requirements.materialId");
		JsonNode shortageRequirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId
						+ "/requirements?shortageOnly=true&page=1&pageSize=50"));
		assertItemsPositiveDecimal(softly, shortageRequirements, "shortageQuantity", "requirements.shortageOnly");
		JsonNode shortageStatusRequirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId
						+ "/requirements?coverageStatus=SHORTAGE&page=1&pageSize=50"));
		assertItemsOnlyText(softly, shortageStatusRequirements, "coverageStatus", "SHORTAGE",
				"requirements.coverageStatus");
		assertInvalidPaginationRejected(softly, admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/requirements?materialId="
						+ target.rawMaterialId(),
				"需求行分页");

		JsonNode allocations = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/allocations?requirementLineId="
						+ rawRequirement.get("id").longValue() + "&page=1&pageSize=50"));
		assertItemsOnlyLong(softly, allocations, "requirementLineId", rawRequirement.get("id").longValue(),
				"allocations.requirementLineId");
		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId
						+ "/suggestions?suggestionType=PURCHASE_REQUISITION&page=1&pageSize=50"));
		assertItemsOnlyText(softly, suggestions, "suggestionType", "PURCHASE_REQUISITION",
				"suggestions.suggestionType");
		JsonNode openSuggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId
						+ "/suggestions?status=OPEN&page=1&pageSize=50"));
		assertItemsOnlyText(softly, openSuggestions, "status", "OPEN", "suggestions.status");
		JsonNode hints = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/substitute-hints?requirementLineId="
						+ rawRequirement.get("id").longValue() + "&page=1&pageSize=50"));
		assertItemsOnlyLong(softly, hints, "requirementLineId", rawRequirement.get("id").longValue(),
				"substitute-hints.requirementLineId");
		assertInvalidPaginationRejected(softly, admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/allocations?requirementLineId="
						+ rawRequirement.get("id").longValue(),
				"分配分页");
		softly.assertAll();
	}

	@Test
	void redD_frontendCanonicalPayloadAndRunDetailDtoRemainStable() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		SoftAssertions softly = new SoftAssertions();
		Fixture fixture = fixture();
		DemandScope scope = demandScope(fixture.orderLineId());
		Map<String, Object> frontendPayload = new LinkedHashMap<>();
		frontendPayload.put("orderId", scope.orderId());
		frontendPayload.put("requiredDateTo", fixture.demandDate().toString());
		frontendPayload.put("includePublicDemand", true);
		frontendPayload.put("idempotencyKey", "MRP-RED-D-FRONTEND-PAYLOAD-" + suffix);

		JsonNode frontendRun = data(post(admin, "/api/admin/planning/material-requirement-runs", frontendPayload));
		Map<String, Object> persistedRun = this.jdbcTemplate.queryForMap("""
				select scope_type, sales_order_id, demand_date_to
				from mrp_calculation_run
				where id = ?
				""", frontendRun.get("id").longValue());
		softly.assertThat(persistedRun.get("scope_type")).as("orderId 不能静默退回 GLOBAL").isNotEqualTo("GLOBAL");
		softly.assertThat(persistedRun.get("sales_order_id")).as("前端 orderId 必须映射后端 sales_order_id")
			.isEqualTo(scope.orderId());
		softly.assertThat(dateIsoString(persistedRun.get("demand_date_to")))
			.as("前端 requiredDateTo 必须映射后端需求日期")
			.isEqualTo(fixture.demandDate().toString());

		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RED-D-DETAIL-" + suffix)));
		JsonNode detail = data(get(admin, "/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()));
		assertHasJsonFields(softly, detail, "运行详情 DTO", "scopeSummary", "projectCount", "requirementLineCount",
				"shortageMaterialCount", "purchaseSuggestionCount", "productionSuggestionCount", "exceptionCount",
				"asOfBusinessDate", "asOfTime", "completedAt", "createdByName", "stale", "expired",
				"failureCode", "failureSummary", "allowedActions", "previousRunId", "sourceCounts");
		assertJsonNumberOrNull(softly, detail, "projectCount", "运行详情 projectCount 类型");
		assertJsonNumberOrNull(softly, detail, "requirementLineCount", "运行详情 requirementLineCount 类型");
		assertJsonNumberOrNull(softly, detail, "shortageMaterialCount", "运行详情 shortageMaterialCount 类型");
		assertJsonBooleanOrNull(softly, detail, "stale", "运行详情 stale 类型");
		assertJsonBooleanOrNull(softly, detail, "expired", "运行详情 expired 类型");
		softly.assertAll();
	}

	@Test
	void redD_requirementAllocationSuggestionAndExportContractsMatchFrontend() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		SoftAssertions softly = new SoftAssertions();
		Fixture fixture = fixture();
		DemandScope scope = demandScope(fixture.orderLineId());
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RED-D-CONTRACTS-" + suffix)));
		long runId = run.get("id").longValue();
		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/requirements?page=1&pageSize=50"));
		JsonNode requirement = findRequirementByMaterial(requirements.get("items"), fixture.rawMaterialId());
		assertHasJsonFields(softly, requirement, "需求行 DTO", "orderNo", "deliveryPlanNo", "projectNo",
				"projectName", "finishedMaterialCode", "finishedMaterialName", "bomVersionNo", "requiredDate",
				"estimatedAvailableDate", "coverageStatus", "suggestionType", "exceptionReasonCode");

		JsonNode allocations = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/allocations?requirementLineId="
						+ requirement.get("id").longValue() + "&page=1&pageSize=50"));
		JsonNode allocation = firstItem(allocations);
		assertHasJsonFields(softly, allocation, "分配 DTO", "requirementLineId", "supplyTypeName", "projectNo",
				"warehouseName", "sourceNo");
		assertItemsOnlyLong(softly, allocations, "requirementLineId", requirement.get("id").longValue(),
				"allocations.requirementLineId");

		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/suggestions?status=OPEN&page=1&pageSize=50"));
		JsonNode suggestion = findSuggestion(suggestions.get("items"), fixture.rawMaterialId(),
				"PURCHASE_REQUISITION");
		assertHasJsonFields(softly, suggestion, "建议 DTO", "suggestionNo", "materialSourceType", "projectNo",
				"projectName", "suggestedDate", "reasonCode", "reasonMessage", "convertedRequisitionId",
				"convertedRequisitionNo", "actionDisabledReason");
		assertItemsOnlyText(softly, suggestions, "status", "OPEN", "suggestions.status");

		AuthenticatedSession planningExportOnly = createPlanningUserAndLogin("mrp-export-no-platform-",
				"MRP_EXPORT_NO_PLATFORM_", List.of("planning:material-requirement:view",
						"planning:material-requirement:export"));
		Map<String, Object> filters = new LinkedHashMap<>();
		filters.put("projectId", fixture.projectId());
		filters.put("customerId", scope.customerId());
		filters.put("contractId", scope.contractId());
		filters.put("orderId", scope.orderId());
		filters.put("materialId", scope.materialId());
		filters.put("requiredDateTo", fixture.demandDate().toString());
		filters.put("status", "COMPLETED");
		filters.put("expired", false);
		ResponseEntity<String> exportResponse = postWithIdempotency(planningExportOnly, "/api/admin/export-tasks",
				Map.of("taskType", "MATERIAL_REQUIREMENT_RUN_EXPORT", "objectType", "MATERIAL_REQUIREMENT_RUN",
						"objectId", runId, "filters", filters),
				"MRP-RED-D-EXPORT-" + suffix);
		softly.assertThat(exportResponse.getStatusCode()).as(exportResponse.getBody()).isEqualTo(HttpStatus.OK);
		if (exportResponse.getStatusCode().is2xxSuccessful()) {
			JsonNode exportTask = data(exportResponse);
			String payload = this.jdbcTemplate.queryForObject("""
					select request_payload
					from platform_document_task
					where id = ?
					""", String.class, exportTask.get("id").longValue());
			softly.assertThat(payload).contains("MATERIAL_REQUIREMENT_RUN_EXPORT", "projectId", "customerId",
					"contractId", "orderId", "materialId", "requiredDateTo", "status", "expired");
		}
		softly.assertAll();
	}

	@Test
	void redE_planningViewDoesNotBypassSalesBomInventoryProcurementOrProductionSourcePermissions() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		SoftAssertions softly = new SoftAssertions();
		Fixture fixture = fixture();
		DemandScope scope = demandScope(fixture.orderLineId());
		long bomId = bomIdForParentMaterial(scope.materialId());
		long workOrderId = insertWorkOrder("MRP_RED_E_WO_" + suffix, fixture.rawMaterialId(), fixture.unitId(),
				fixture.warehouseId(), fixture.demandDate().minusDays(1), "2.000000", "0.000000", "RELEASED");
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RED-E-SOURCE-" + suffix)));
		long runId = run.get("id").longValue();

		AuthenticatedSession planningViewOnly = createPlanningUserAndLogin("mrp-source-view-only-",
				"MRP_SOURCE_VIEW_ONLY_", List.of("planning:material-requirement:view"));
		JsonNode restrictedRequirements = data(get(planningViewOnly,
				"/api/admin/planning/material-requirement-runs/" + runId + "/requirements?page=1&pageSize=50"));
		JsonNode restrictedRequirement = findRequirementByMaterial(restrictedRequirements.get("items"),
				fixture.rawMaterialId());
		softly.assertThat(new BigDecimal(restrictedRequirement.get("requiredQuantity").asText()))
			.as("仅规划查看权限仍可看净算数量")
			.isGreaterThan(BigDecimal.ZERO);
		assertSourceFieldsRedacted(softly, restrictedRequirement, "低权限需求行", "demandSourceId",
				"demandSourceLineId", "deliveryPlanId", "orderNo", "deliveryPlanNo", "bomPath", "bomVersionNo");
		JsonNode restrictedAllocations = data(get(planningViewOnly,
				"/api/admin/planning/material-requirement-runs/" + runId + "/allocations?page=1&pageSize=50"));
		for (JsonNode allocation : restrictedAllocations.get("items")) {
			assertSourceFieldsRedacted(softly, allocation, "低权限分配", "sourceTable", "sourceId", "sourceLineId",
					"sourceNo", "projectId", "projectNo", "warehouseName");
		}
		assertErrorSoft(softly, get(planningViewOnly, "/api/admin/sales/orders/" + scope.orderId()),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "低权限直接销售来源");
		assertErrorSoft(softly, get(planningViewOnly, "/api/admin/boms/" + bomId), HttpStatus.FORBIDDEN,
				"AUTH_FORBIDDEN", "低权限直接 BOM 来源");
		assertErrorSoft(softly, get(planningViewOnly, "/api/admin/inventory/balances?page=1&pageSize=20"),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "低权限直接库存来源");
		assertErrorSoft(softly, get(planningViewOnly, "/api/admin/procurement/effective-supplies?page=1&pageSize=20"),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "低权限直接采购来源");
		assertErrorSoft(softly, get(planningViewOnly, "/api/admin/production/work-orders/" + workOrderId),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "低权限直接工单来源");

		AuthenticatedSession sourceVisible = createPlanningUserAndLogin("mrp-source-visible-", "MRP_SOURCE_VISIBLE_",
				List.of("planning:material-requirement:view", "sales:order:view", "material:bom:view",
						"inventory:balance:view", "procurement:supply:view", "production:work-order:view"));
		JsonNode visibleRequirements = data(get(sourceVisible,
				"/api/admin/planning/material-requirement-runs/" + runId + "/requirements?page=1&pageSize=50"));
		JsonNode visibleRequirement = findRequirementByMaterial(visibleRequirements.get("items"), fixture.rawMaterialId());
		assertHasTextJsonFields(softly, visibleRequirement, "来源权限补齐后的需求行", "orderNo", "bomVersionNo",
				"projectNo", "projectName");
		JsonNode visibleAllocations = data(get(sourceVisible,
				"/api/admin/planning/material-requirement-runs/" + runId + "/allocations?page=1&pageSize=50"));
		JsonNode visibleAllocation = firstItem(visibleAllocations);
		assertHasTextJsonFields(softly, visibleAllocation, "来源权限补齐后的分配", "sourceNo", "supplyTypeName",
				"warehouseName");
		softly.assertAll();
	}

	@Test
	void redReview_sourceRestrictedSuggestionsAndSubstituteHintsRedactAndRecoverSourceFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		SoftAssertions softly = new SoftAssertions();
		Fixture fixture = fixture();
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RED-REVIEW-SOURCE-" + suffix)));
		long runId = run.get("id").longValue();
		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/requirements?page=1&pageSize=50"));
		JsonNode requirement = findRequirementByMaterial(requirements.get("items"), fixture.rawMaterialId());

		AuthenticatedSession planningViewOnly = createPlanningUserAndLogin("mrp-review-view-only-",
				"MRP_REVIEW_VIEW_ONLY_", List.of("planning:material-requirement:view"));
		JsonNode restrictedSuggestions = data(get(planningViewOnly,
				"/api/admin/planning/material-requirement-runs/" + runId + "/suggestions?page=1&pageSize=50"));
		JsonNode restrictedSuggestion = findSuggestion(restrictedSuggestions.get("items"), fixture.rawMaterialId(),
				"PURCHASE_REQUISITION");
		assertSourceFieldsRedacted(softly, restrictedSuggestion, "低权限建议", "projectNo", "projectName");
		JsonNode restrictedHints = data(get(planningViewOnly,
				"/api/admin/planning/material-requirement-runs/" + runId + "/substitute-hints?requirementLineId="
						+ requirement.get("id").longValue() + "&page=1&pageSize=50"));
		JsonNode restrictedHint = firstItem(restrictedHints);
		assertSourceFieldsRedacted(softly, restrictedHint, "低权限替代提示", "substituteMaterialCode",
				"substituteMaterialName");

		AuthenticatedSession sourceVisible = createPlanningUserAndLogin("mrp-review-source-visible-",
				"MRP_REVIEW_SOURCE_VISIBLE_",
				List.of("planning:material-requirement:view", "sales:project:view", "sales:order:view",
						"material:bom:view", "material:substitute:view", "master:material:view",
						"inventory:balance:view", "procurement:supply:view", "production:work-order:view"));
		JsonNode visibleSuggestions = data(get(sourceVisible,
				"/api/admin/planning/material-requirement-runs/" + runId + "/suggestions?page=1&pageSize=50"));
		JsonNode visibleSuggestion = findSuggestion(visibleSuggestions.get("items"), fixture.rawMaterialId(),
				"PURCHASE_REQUISITION");
		assertHasTextJsonFields(softly, visibleSuggestion, "来源权限补齐后的建议", "projectNo", "projectName");
		JsonNode visibleHints = data(get(sourceVisible,
				"/api/admin/planning/material-requirement-runs/" + runId + "/substitute-hints?requirementLineId="
						+ requirement.get("id").longValue() + "&page=1&pageSize=50"));
		JsonNode visibleHint = firstItem(visibleHints);
		assertHasTextJsonFields(softly, visibleHint, "来源权限补齐后的替代提示", "substituteMaterialCode",
				"substituteMaterialName");
		softly.assertAll();
	}

	@Test
	void redReview_selfMadeTopRequirementExposesRootBomVersionWhenBomSourceVisible() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		Fixture fixture = fixture();
		DemandScope scope = demandScope(fixture.orderLineId());
		String expectedRootBomCode = this.jdbcTemplate.queryForObject("""
				select bom_code
				from mfg_bom
				where parent_material_id = ?
				order by id asc
				limit 1
				""", String.class, scope.materialId());
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RED-REVIEW-TOP-BOM-" + suffix)));
		long runId = run.get("id").longValue();

		AuthenticatedSession withoutBomSource = createPlanningUserAndLogin("mrp-review-no-bom-source-",
				"MRP_REVIEW_NO_BOM_SOURCE_",
				List.of("planning:material-requirement:view", "sales:project:view", "sales:order:view"));
		JsonNode redactedRequirements = data(get(withoutBomSource,
				"/api/admin/planning/material-requirement-runs/" + runId + "/requirements?page=1&pageSize=50"));
		JsonNode redactedTopRequirement = findRequirementByMaterial(redactedRequirements.get("items"),
				scope.materialId());
		assertThat(redactedTopRequirement.has("bomVersionNo")).as("无 BOM 查看权限仍应返回稳定 null 字段").isTrue();
		assertThat(redactedTopRequirement.get("bomVersionNo").isNull()).as("无 BOM 查看权限不能泄露根 BOM 编码")
			.isTrue();

		AuthenticatedSession bomSourceVisible = createPlanningUserAndLogin("mrp-review-bom-source-visible-",
				"MRP_REVIEW_BOM_SOURCE_VISIBLE_",
				List.of("planning:material-requirement:view", "sales:project:view", "sales:order:view",
						"material:bom:view"));
		JsonNode visibleRequirements = data(get(bomSourceVisible,
				"/api/admin/planning/material-requirement-runs/" + runId + "/requirements?page=1&pageSize=50"));
		JsonNode visibleTopRequirement = findRequirementByMaterial(visibleRequirements.get("items"),
				scope.materialId());
		assertThat(visibleTopRequirement.path("bomVersionNo").asText())
			.as("顶层自制 FG 需求必须可追溯到实际匹配并展开的根 BOM")
			.isEqualTo(expectedRootBomCode);
	}

	@Test
	void redReview_allocationsExposeFrontendAvailableDateAndExcludedReasonCodeFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		SoftAssertions softly = new SoftAssertions();
		Fixture fixture = fixture();
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RED-REVIEW-ALLOCATION-DTO-" + suffix)));
		JsonNode allocations = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/allocations?page=1&pageSize=50"));
		JsonNode allocation = firstItem(allocations);
		assertHasJsonFields(softly, allocation, "分配 DTO 前端字段", "availableDate", "excludedReasonCode");
		softly.assertThat(allocation.has("supplyDate")).as("分配 DTO 不应只暴露前端不消费的 supplyDate").isFalse();
		softly.assertThat(allocation.has("reason")).as("分配 DTO 不应只暴露前端不消费的 reason").isFalse();
		JsonNode excluded = firstAllocationWithReason(allocations.get("items"));
		softly.assertThat(excluded.hasNonNull("availableDate")).as("排除供给仍应暴露真实可用日期").isTrue();
		softly.assertThat(excluded.path("excludedReasonCode").asText()).as("排除供给应暴露稳定原因码")
			.isIn("SUPPLY_LATE", "STOCK_RESERVED_OR_OCCUPIED", "SUPPLY_STATUS_NOT_COUNTED",
					"WORK_ORDER_NOT_PROJECT_BOUND", "CROSS_PROJECT_NOT_ALLOWED");
		softly.assertAll();
	}

	@Test
	void redReview_convertRequisitionReturnsConvertedSuggestionRecordAndReusesDraftIdempotently() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		SoftAssertions softly = new SoftAssertions();
		Fixture fixture = fixture();
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(fixture.projectId(), fixture.demandDate(), "MRP-RED-REVIEW-CONVERT-" + suffix)));
		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode suggestion = findSuggestion(suggestions.get("items"), fixture.rawMaterialId(), "PURCHASE_REQUISITION");
		JsonNode confirmed = data(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + suggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", suggestion.get("version").longValue(), "idempotencyKey",
						"MRP-RED-REVIEW-CONFIRM-" + suffix)));
		Map<String, Object> convertRequest = Map.of("version", confirmed.get("version").longValue(),
				"idempotencyKey", "MRP-RED-REVIEW-CONVERT-SUG-" + suffix);
		JsonNode converted = data(post(admin,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-requisition",
				convertRequest));
		JsonNode convertedAgain = data(post(admin,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-requisition",
				convertRequest));
		softly.assertThat(converted.path("id").asLong(-1)).as("转请购响应应保持 suggestion record")
			.isEqualTo(confirmed.get("id").longValue());
		softly.assertThat(converted.path("status").asText()).as("转请购后建议状态").isEqualTo("CONVERTED");
		softly.assertThat(converted.path("convertedRequisitionId").isNumber()).as("响应应返回 convertedRequisitionId")
			.isTrue();
		softly.assertThat(converted.path("convertedRequisitionNo").asText()).as("响应应返回 convertedRequisitionNo")
			.isNotBlank();
		softly.assertThat(convertedAgain.path("convertedRequisitionId").asLong(-1)).as("同幂等键重复转请购复用同一 DRAFT")
			.isEqualTo(converted.path("convertedRequisitionId").asLong());
		softly.assertThat(convertedAgain.path("convertedRequisitionNo").asText()).as("同幂等键重复转请购复用同一 DRAFT 编号")
			.isEqualTo(converted.path("convertedRequisitionNo").asText());
		softly.assertAll();
	}

	@Test
	void stage027ConfirmedSelfMadeSuggestionConvertsToDraftProjectWorkOrderIdempotently() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate demandDate = LocalDate.now().plusDays(9);
		DemandSeed seed = selfMadeDemand("MRP_027_WO_" + suffix, demandDate);
		insertBom("MRP_027_WO_BOM_" + suffix, seed.materialId(), seed.unitId(), LocalDate.now().minusDays(1),
				List.of());
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(seed.projectId(), demandDate, "MRP-027-WO-RUN-" + suffix)));
		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode suggestion = findSuggestion(suggestions.get("items"), seed.materialId(), "PRODUCTION_ORDER");
		JsonNode confirmed = data(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + suggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", suggestion.get("version").longValue(), "idempotencyKey",
						"MRP-027-WO-CONFIRM-" + suffix)));
		Map<String, Object> convertRequest = Map.of("version", confirmed.get("version").longValue(),
				"idempotencyKey", "MRP-027-WO-CONVERT-" + suffix);
		JsonNode converted = data(post(admin,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-work-order",
				convertRequest));
		JsonNode convertedAgain = data(post(admin,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-work-order",
				convertRequest));

		assertProductionConversionRecord(converted, confirmed.get("id").longValue(), "WORK_ORDER", "MFG-WO",
				"/production/work-orders/");
		assertThat(converted.get("status").asText()).isEqualTo("CONVERTED");
		assertThat(converted.get("targetObjectId").isNumber()).isTrue();
		assertThat(convertedAgain.get("targetObjectId").longValue()).isEqualTo(converted.get("targetObjectId")
			.longValue());
		assertThat(convertedAgain.get("targetObjectNo").asText()).isEqualTo(converted.get("targetObjectNo").asText());
		assertThat(convertedAgain.get("targetRoute").asText()).isEqualTo(converted.get("targetRoute").asText());

		Map<String, Object> workOrder = this.jdbcTemplate.queryForMap("""
				select work_order_no, status, product_material_id, planned_quantity, ownership_type, project_id,
				       source_mrp_run_id, source_mrp_suggestion_id, bom_id, issue_warehouse_id,
				       receipt_warehouse_id, planned_start_date, planned_finish_date
				from mfg_work_order
				where id = ?
				""", converted.get("targetObjectId").longValue());
		assertThat(workOrder.get("work_order_no")).isEqualTo(converted.get("targetObjectNo").asText());
		assertThat(workOrder.get("status")).isEqualTo("DRAFT");
		assertThat(((Number) workOrder.get("product_material_id")).longValue()).isEqualTo(seed.materialId());
		assertThat((BigDecimal) workOrder.get("planned_quantity")).isEqualByComparingTo("1.000000");
		assertThat(workOrder.get("ownership_type")).isEqualTo("PROJECT");
		assertThat(((Number) workOrder.get("project_id")).longValue()).isEqualTo(seed.projectId());
		assertThat(((Number) workOrder.get("source_mrp_run_id")).longValue()).isEqualTo(run.get("id").longValue());
		assertThat(((Number) workOrder.get("source_mrp_suggestion_id")).longValue()).isEqualTo(confirmed.get("id")
			.longValue());
		assertThat(workOrder.get("bom_id")).isNull();
		assertThat(workOrder.get("issue_warehouse_id")).isNull();
		assertThat(workOrder.get("receipt_warehouse_id")).isNull();
		assertThat(workOrder.get("planned_start_date")).isNull();
		assertThat(workOrder.get("planned_finish_date")).isNull();
	}

	@Test
	void stage027ConfirmedOutsourcedSuggestionConvertsToDraftOutsourcingOrderIdempotently() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate demandDate = LocalDate.now().plusDays(10);
		DemandSeed seed = outsourcedDemand("MRP_027_OS_" + suffix, demandDate);
		insertBom("MRP_027_OS_BOM_" + suffix, seed.materialId(), seed.unitId(), LocalDate.now().minusDays(1),
				List.of());
		JsonNode run = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(seed.projectId(), demandDate, "MRP-027-OS-RUN-" + suffix)));
		JsonNode suggestions = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + run.get("id").longValue()
						+ "/suggestions?page=1&pageSize=50"));
		JsonNode suggestion = findSuggestion(suggestions.get("items"), seed.materialId(), "PRODUCTION_ORDER");
		JsonNode confirmed = data(put(admin,
				"/api/admin/planning/material-requirement-suggestions/" + suggestion.get("id").longValue()
						+ "/confirm",
				Map.of("version", suggestion.get("version").longValue(), "idempotencyKey",
						"MRP-027-OS-CONFIRM-" + suffix)));
		Map<String, Object> convertRequest = Map.of("version", confirmed.get("version").longValue(),
				"idempotencyKey", "MRP-027-OS-CONVERT-" + suffix);
		JsonNode converted = data(post(admin,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-outsourcing-order",
				convertRequest));
		JsonNode convertedAgain = data(post(admin,
				"/api/admin/planning/material-requirement-suggestions/" + confirmed.get("id").longValue()
						+ "/convert-outsourcing-order",
				convertRequest));

		assertProductionConversionRecord(converted, confirmed.get("id").longValue(), "OUTSOURCING_ORDER", "MFG-OS",
				"/production/outsourcing-orders/");
		assertThat(converted.get("status").asText()).isEqualTo("CONVERTED");
		assertThat(converted.get("targetObjectId").isNumber()).isTrue();
		assertThat(convertedAgain.get("targetObjectId").longValue()).isEqualTo(converted.get("targetObjectId")
			.longValue());
		assertThat(convertedAgain.get("targetObjectNo").asText()).isEqualTo(converted.get("targetObjectNo").asText());
		assertThat(convertedAgain.get("targetRoute").asText()).isEqualTo(converted.get("targetRoute").asText());

		Map<String, Object> outsourcingOrder = this.jdbcTemplate.queryForMap("""
				select outsourcing_order_no, status, product_material_id, planned_quantity, ownership_type, project_id,
				       source_mrp_run_id, source_mrp_suggestion_id, supplier_id, bom_id, issue_warehouse_id,
				       receipt_warehouse_id, planned_issue_date, planned_receipt_date
				from mfg_outsourcing_order
				where id = ?
				""", converted.get("targetObjectId").longValue());
		assertThat(outsourcingOrder.get("outsourcing_order_no")).isEqualTo(converted.get("targetObjectNo").asText());
		assertThat(outsourcingOrder.get("status")).isEqualTo("DRAFT");
		assertThat(((Number) outsourcingOrder.get("product_material_id")).longValue()).isEqualTo(seed.materialId());
		assertThat((BigDecimal) outsourcingOrder.get("planned_quantity")).isEqualByComparingTo("1.000000");
		assertThat(outsourcingOrder.get("ownership_type")).isEqualTo("PROJECT");
		assertThat(((Number) outsourcingOrder.get("project_id")).longValue()).isEqualTo(seed.projectId());
		assertThat(((Number) outsourcingOrder.get("source_mrp_run_id")).longValue()).isEqualTo(run.get("id")
			.longValue());
		assertThat(((Number) outsourcingOrder.get("source_mrp_suggestion_id")).longValue()).isEqualTo(confirmed.get("id")
			.longValue());
		assertThat(outsourcingOrder.get("supplier_id")).isNull();
		assertThat(outsourcingOrder.get("bom_id")).isNull();
		assertThat(outsourcingOrder.get("issue_warehouse_id")).isNull();
		assertThat(outsourcingOrder.get("receipt_warehouse_id")).isNull();
		assertThat(outsourcingOrder.get("planned_issue_date")).isNull();
		assertThat(outsourcingOrder.get("planned_receipt_date")).isNull();
	}

	@Test
	void redReview_materialRequirementExportAppliesResultLevelFilters() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		int suffix = SEQUENCE.incrementAndGet();
		Fixture target = fixture(LocalDate.now().plusDays(7));
		Fixture excluded = fixture(LocalDate.now().plusDays(21));
		DemandScope targetScope = demandScope(target.orderLineId());
		JsonNode targetRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(target.projectId(), target.demandDate(), "MRP-RED-REVIEW-EXPORT-TARGET-" + suffix)));
		JsonNode excludedRun = data(post(admin, "/api/admin/planning/material-requirement-runs",
				runRequest(excluded.projectId(), excluded.demandDate(), "MRP-RED-REVIEW-EXPORT-EXCLUDED-" + suffix)));
		this.jdbcTemplate.update("""
				update mrp_calculation_run
				set expires_at = now() - interval '1 minute'
				where id = ?
				""", excludedRun.get("id").longValue());
		Map<String, Object> filters = new LinkedHashMap<>();
		filters.put("customerId", targetScope.customerId());
		filters.put("contractId", targetScope.contractId());
		filters.put("orderId", targetScope.orderId());
		filters.put("materialId", targetScope.materialId());
		filters.put("requiredDateTo", target.demandDate().toString());
		filters.put("expired", false);
		filters.put("status", "COMPLETED");
		AuthenticatedSession planningExportOnly = createPlanningUserAndLogin("mrp-review-export-",
				"MRP_REVIEW_EXPORT_", List.of("planning:material-requirement:view",
						"planning:material-requirement:export"));
		JsonNode exportTask = data(postWithIdempotency(planningExportOnly, "/api/admin/export-tasks",
				Map.of("taskType", "MATERIAL_REQUIREMENT_RUN_EXPORT", "objectType", "MATERIAL_REQUIREMENT_RUN",
						"filters", filters),
				"MRP-RED-REVIEW-EXPORT-" + suffix));
		JsonNode succeeded = processTaskUntilStatus(admin, exportTask.get("id").longValue(), "SUCCEEDED", 8);
		assertThat(succeeded.get("status").asText()).as(succeeded.toString()).isEqualTo("SUCCEEDED");
		String exportText = workbookText(downloadBytes(admin, "/api/admin/document-tasks/"
				+ exportTask.get("id").longValue() + "/download").getBody());
		assertThat(exportText).contains(targetRun.get("runNo").asText());
		assertThat(exportText).doesNotContain(excludedRun.get("runNo").asText());
	}

	private Fixture fixture() {
		return fixture(LocalDate.now().plusDays(7));
	}

	private Fixture fixture(LocalDate demandDate) {
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate today = LocalDate.now();
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		long unitId = insertUnit("MRP_U_" + suffix);
		long warehouseId = insertWarehouse("MRP_WH_" + suffix);
		long categoryId = insertCategory("MRP_CAT_" + suffix);
		long customerId = insertCustomer("MRP_CUS_" + suffix);
		long supplierId = insertSupplier("MRP_SUP_" + suffix);
		long projectId = insertProject("MRP_PRJ_" + suffix, customerId, adminUserId);
		long contractId = insertContract("MRP_CON_" + suffix, projectId);
		long finishedId = insertMaterial("MRP_FG_" + suffix, "FINISHED_GOOD", "SELF_MADE", categoryId, unitId);
		long semiId = insertMaterial("MRP_SEMI_" + suffix, "SEMI_FINISHED", "SELF_MADE", categoryId, unitId);
		long rawId = insertMaterial("MRP_RAW_" + suffix, "RAW_MATERIAL", "PURCHASED", categoryId, unitId);
		long substituteId = insertMaterial("MRP_SUB_" + suffix, "RAW_MATERIAL", "PURCHASED", categoryId, unitId);
		insertBom("MRP_BOM_FG_" + suffix, finishedId, unitId, today.minusDays(1),
				List.of(bomItem(1, semiId, unitId, "1.000000"), bomItem(2, rawId, unitId, "1.000000")));
		insertBom("MRP_BOM_SEMI_" + suffix, semiId, unitId, today.minusDays(1),
				List.of(bomItem(1, rawId, unitId, "2.000000")));
		long orderLineId = insertSalesDemand("MRP_SO_" + suffix, customerId, projectId, contractId, finishedId,
				unitId, demandDate, "10.000000");
		insertStock(warehouseId, finishedId, unitId, "PROJECT", projectId, "2.000000");
		insertStock(warehouseId, semiId, unitId, "PROJECT", projectId, "1.000000");
		insertStock(warehouseId, semiId, unitId, "PUBLIC", null, "2.000000");
		insertStock(warehouseId, rawId, unitId, "PROJECT", projectId, "3.000000");
		insertStock(warehouseId, rawId, unitId, "PUBLIC", null, "5.000000");
		insertPurchaseSupply("MRP_PO_PRJ_" + suffix, supplierId, projectId, rawId, unitId, demandDate.minusDays(1),
				"4.000000", "PROJECT");
		insertPurchaseSupply("MRP_PO_PUB_" + suffix, supplierId, null, rawId, unitId, demandDate.minusDays(1),
				"1.000000", "PUBLIC");
		insertPurchaseSupply("MRP_PO_LATE_" + suffix, supplierId, null, rawId, unitId, demandDate.plusDays(5),
				"7.000000", "PUBLIC");
		insertSubstitute(rawId, substituteId, today.minusDays(1));
		return new Fixture(projectId, rawId, orderLineId, demandDate, warehouseId, unitId, substituteId);
	}

	private DemandSeed selfMadeDemand(String prefix, LocalDate demandDate) {
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		long unitId = insertUnit(prefix + "_U");
		long categoryId = insertCategory(prefix + "_CAT");
		long customerId = insertCustomer(prefix + "_CUS");
		long projectId = insertProject(prefix + "_PRJ", customerId, adminUserId);
		long contractId = insertContract(prefix + "_CON", projectId);
		long materialId = insertMaterial(prefix + "_MAT", "FINISHED_GOOD", "SELF_MADE", categoryId, unitId);
		long demandLineId = insertSalesDemand(prefix + "_SO", customerId, projectId, contractId, materialId, unitId,
				demandDate, "1.000000");
		return new DemandSeed(projectId, materialId, unitId, categoryId, demandLineId);
	}

	private DemandSeed outsourcedDemand(String prefix, LocalDate demandDate) {
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		long unitId = insertUnit(prefix + "_U");
		long categoryId = insertCategory(prefix + "_CAT");
		long customerId = insertCustomer(prefix + "_CUS");
		long projectId = insertProject(prefix + "_PRJ", customerId, adminUserId);
		long contractId = insertContract(prefix + "_CON", projectId);
		long materialId = insertMaterial(prefix + "_MAT", "FINISHED_GOOD", "OUTSOURCED", categoryId, unitId);
		long demandLineId = insertSalesDemand(prefix + "_SO", customerId, projectId, contractId, materialId, unitId,
				demandDate, "1.000000");
		return new DemandSeed(projectId, materialId, unitId, categoryId, demandLineId);
	}

	private Map<String, Object> runRequest(long projectId, LocalDate demandDateTo, Object idempotencyKey) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("scopeType", "PROJECT");
		payload.put("projectId", projectId);
		payload.put("demandDateTo", demandDateTo.toString());
		payload.put("idempotencyKey", idempotencyKey);
		return payload;
	}

	private long insertUnit(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, 6, 'ENABLED', 10, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + " unit");
	}

	private long insertWarehouse(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (code, name, warehouse_type, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + " warehouse");
	}

	private long insertCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 10, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + " category");
	}

	private long insertCustomer(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + " customer");
	}

	private long insertSupplier(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + " supplier");
	}

	private long insertProject(String code, long customerId, long ownerUserId) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (project_no, name, customer_id, owner_user_id, planned_start_date,
					planned_finish_date, status, target_revenue, target_cost, created_by, created_at, updated_by,
					updated_at, activated_by, activated_at)
				values (?, ?, ?, ?, ?, ?, 'ACTIVE', 1000.00, 100.00, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + " project", customerId, ownerUserId, LocalDate.now(),
				LocalDate.now().plusDays(30));
	}

	private long insertContract(String code, long projectId) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project_contract (contract_no, project_id, contract_type, name, signed_date,
					effective_start_date, effective_end_date, amount, status, created_by, created_at, updated_by,
					updated_at, activated_by, activated_at)
				values (?, ?, 'MAIN', ?, current_date, current_date, current_date + interval '30 days', 1000.00,
					'EFFECTIVE', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, code, projectId, code + " contract");
	}

	private long insertMaterial(String code, String materialType, String sourceType, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id,
					unit_id, status, cost_category, inventory_valuation_category, inventory_value_enabled,
					project_cost_enabled, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'S', ?, ?, ?, ?, 'ENABLED', 'DIRECT_MATERIAL', 'VALUATED_MATERIAL', false, false,
					'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + " material", materialType, sourceType, categoryId, unitId);
	}

	private BomItemSeed bomItem(int lineNo, long materialId, long unitId, String quantity) {
		return new BomItemSeed(lineNo, materialId, unitId, new BigDecimal(quantity));
	}

	private void insertBom(String code, long parentMaterialId, long unitId, LocalDate effectiveFrom,
			List<BomItemSeed> items) {
		long bomId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id,
					status, effective_from, created_by, created_at, updated_by, updated_at, enabled_by, enabled_at)
				values (?, ?, 'V1', ?, 1.000000, ?, 'ENABLED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, code, parentMaterialId, code + " bom", unitId, effectiveFrom);
		for (BomItemSeed item : items) {
			this.jdbcTemplate.update("""
					insert into mfg_bom_item (bom_id, line_no, child_material_id, unit_id, quantity, loss_rate,
						business_unit_id, business_quantity, base_unit_id, base_quantity, quantity_basis,
						created_at, updated_at)
					values (?, ?, ?, ?, ?, 0.000000, ?, ?, ?, ?, 'BASE_UNIT', now(), now())
					""", bomId, item.lineNo(), item.materialId(), item.unitId(), item.quantity(), item.unitId(),
					item.quantity(), item.unitId(), item.quantity());
		}
	}

	private void insertBomMissingConversion(String code, long parentMaterialId, long parentUnitId, long childMaterialId,
			long businessUnitId, LocalDate effectiveFrom) {
		long bomId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id,
					status, effective_from, created_by, created_at, updated_by, updated_at, enabled_by, enabled_at)
				values (?, ?, 'V1', ?, 1.000000, ?, 'ENABLED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, code, parentMaterialId, code + " bom", parentUnitId, effectiveFrom);
		this.jdbcTemplate.update("""
				insert into mfg_bom_item (bom_id, line_no, child_material_id, unit_id, quantity, loss_rate,
					business_unit_id, business_quantity, base_unit_id, base_quantity, conversion_id,
					conversion_rate_snapshot, quantity_scale_snapshot, rounding_mode_snapshot, quantity_basis,
					created_at, updated_at)
				values (?, 1, ?, ?, 1.000000, 0.000000, ?, 1.000000, null, null, null, null, null, null,
					'LEGACY_BUSINESS_UNIT', now(), now())
				""", bomId, childMaterialId, businessUnitId, businessUnitId);
	}

	private long insertSalesDemand(String orderNo, long customerId, long projectId, long contractId, long materialId,
			long unitId, LocalDate demandDate, String quantity) {
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (order_no, customer_id, project_id, contract_id, order_date,
					expected_ship_date, status, created_by, created_at, updated_by, updated_at, confirmed_by,
					confirmed_at, currency, price_mode, tax_excluded_amount, tax_amount, tax_included_amount,
					sales_fulfillment_compatible)
				values (?, ?, ?, ?, current_date, ?, 'CONFIRMED', 'test', now(), 'test', now(), 'test', now(),
					'CNY', 'TAX_INCLUDED', 0.00, 0.00, 0.00, true)
				returning id
				""", Long.class, orderNo, customerId, projectId, contractId, demandDate);
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (order_id, line_no, material_id, unit_id, quantity, shipped_quantity,
					unit_price, expected_ship_date, reservation_warehouse_id, price_source_type, currency, tax_rate,
					tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_amount,
					tax_included_amount, created_at, updated_at)
				values (?, 1, ?, ?, ?, 0.000000, 0.000000, ?, null, 'MANUAL', 'CNY', 0.000000, 0.000000,
					0.000000, 0.00, 0.00, 0.00, now(), now())
				returning id
				""", Long.class, orderId, materialId, unitId, new BigDecimal(quantity), demandDate);
	}

	private DeliveryPlanDemandSeed insertSalesDemandWithDeliveryPlans(String orderNo, long customerId, long projectId,
			long contractId, long materialId, long unitId, LocalDate firstDate, LocalDate secondDate,
			String firstQuantity, String secondQuantity) {
		BigDecimal first = new BigDecimal(firstQuantity);
		BigDecimal second = new BigDecimal(secondQuantity);
		BigDecimal total = first.add(second);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (order_no, customer_id, project_id, contract_id, order_date,
					expected_ship_date, status, created_by, created_at, updated_by, updated_at, confirmed_by,
					confirmed_at, currency, price_mode, tax_excluded_amount, tax_amount, tax_included_amount,
					sales_fulfillment_compatible)
				values (?, ?, ?, ?, current_date, ?, 'CONFIRMED', 'test', now(), 'test', now(), 'test', now(),
					'CNY', 'TAX_INCLUDED', 0.00, 0.00, 0.00, true)
				returning id
				""", Long.class, orderNo, customerId, projectId, contractId, secondDate);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (order_id, line_no, material_id, unit_id, quantity, shipped_quantity,
					unit_price, expected_ship_date, reservation_warehouse_id, price_source_type, currency, tax_rate,
					tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_amount,
					tax_included_amount, created_at, updated_at)
				values (?, 1, ?, ?, ?, 0.000000, 0.000000, ?, null, 'MANUAL', 'CNY', 0.000000, 0.000000,
					0.000000, 0.00, 0.00, 0.00, now(), now())
				returning id
				""", Long.class, orderId, materialId, unitId, total, secondDate);
		long firstPlanId = insertDeliveryPlan(orderId, orderLineId, 1, firstDate, first);
		long secondPlanId = insertDeliveryPlan(orderId, orderLineId, 2, secondDate, second);
		return new DeliveryPlanDemandSeed(orderId, orderLineId, firstPlanId, secondPlanId);
	}

	private long insertDeliveryPlan(long orderId, long orderLineId, int lineNo, LocalDate plannedDate,
			BigDecimal plannedQuantity) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_delivery_plan (order_id, order_line_id, line_no, planned_date, planned_quantity,
					shipped_quantity, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, ?, ?, 0.000000, 'PLANNED', 'test', now(), 'test', now())
				returning id
				""", Long.class, orderId, orderLineId, lineNo, plannedDate, plannedQuantity);
	}

	private void insertStock(long warehouseId, long materialId, long unitId, String ownershipType, Long projectId,
			String quantity) {
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, ownership_type, project_id, valuation_state, created_at, updated_at)
				values (?, ?, ?, ?, 0.000000, 'QUALIFIED', ?, ?, 'NON_VALUED', now(), now())
				""", warehouseId, materialId, unitId, new BigDecimal(quantity), ownershipType, projectId);
	}

	private void insertStockWithAvailability(long warehouseId, long materialId, long unitId, String ownershipType,
			Long projectId, String quantityOnHand, String lockedQuantity, String qualityStatus, Long costLayerId) {
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, ownership_type, project_id, valuation_state, cost_layer_id, created_at,
					updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, 'NON_VALUED', ?, now(), now())
				""", warehouseId, materialId, unitId, new BigDecimal(quantityOnHand), new BigDecimal(lockedQuantity),
				qualityStatus, ownershipType, projectId, costLayerId);
	}

	private long insertProjectCostLayer(long projectId, long materialId, String sourceNo, String quantity) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_project_cost_layer (project_id, material_id, source_type, source_id, source_line_id,
					original_quantity, original_amount, remaining_quantity, remaining_amount, unit_cost, status,
					created_at, updated_at)
				values (?, ?, 'TEST', ?, ?, ?, 0.00, ?, 0.00, 0.000000, 'ACTIVE', now(), now())
				returning id
				""", Long.class, projectId, materialId, Math.abs(sourceNo.hashCode()), SEQUENCE.incrementAndGet(),
				new BigDecimal(quantity), new BigDecimal(quantity));
	}

	private void insertReservation(String reservationNo, long warehouseId, long materialId, long unitId,
			String ownershipType, Long projectId, Long costLayerId, String quantity) {
		this.jdbcTemplate.update("""
				insert into inv_stock_reservation (reservation_no, reservation_type, status, warehouse_id, material_id,
					unit_id, quality_status, quantity, released_quantity, consumed_quantity, source_type, source_id,
					source_line_id, source_document_no, business_date, reason, created_by, created_at, updated_by,
					updated_at, ownership_type, project_id, cost_layer_id)
				values (?, 'RESERVATION', 'ACTIVE', ?, ?, ?, 'QUALIFIED', ?, 0.000000, 0.000000, 'TEST',
					?, ?, ?, current_date, '测试预留', 'test', now(), 'test', now(), ?, ?, ?)
				""", reservationNo, warehouseId, materialId, unitId, new BigDecimal(quantity),
				Math.abs(reservationNo.hashCode()), SEQUENCE.incrementAndGet(), reservationNo, ownershipType,
				projectId, costLayerId);
	}

	private void insertPurchaseSupply(String orderNo, long supplierId, Long projectId, long materialId, long unitId,
			LocalDate expectedDate, String quantity, String purchaseMode) {
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (order_no, supplier_id, order_date, expected_arrival_date, status,
					purchase_mode, project_id, currency, created_by, created_at, updated_by, updated_at, confirmed_by,
					confirmed_at)
				values (?, ?, current_date, ?, 'CONFIRMED', ?, ?, 'CNY', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, orderNo, supplierId, expectedDate, purchaseMode, projectId);
		long lineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (order_id, line_no, material_id, unit_id, quantity,
					received_quantity, unit_price, expected_arrival_date, price_source_type, tax_rate,
					tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_included_amount,
					created_at, updated_at)
				values (?, 1, ?, ?, ?, 0.000000, 1.000000, ?, 'MANUAL', 0.000000, 1.000000, 1.000000, 1.00,
					1.00, now(), now())
				returning id
				""", Long.class, orderId, materialId, unitId, new BigDecimal(quantity), expectedDate);
		this.jdbcTemplate.update("""
				insert into proc_purchase_order_schedule (order_line_id, line_no, planned_date, planned_quantity,
					received_quantity, status, created_at, updated_at)
				values (?, 1, ?, ?, 0.000000, 'PLANNED', now(), now())
				""", lineId, expectedDate, new BigDecimal(quantity));
	}

	private long insertWorkOrder(String workOrderNo, long materialId, long unitId, long warehouseId,
			LocalDate plannedFinishDate, String plannedQuantity, String receivedQuantity, String status) {
		long bomId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id,
					status, effective_from, created_by, created_at, updated_by, updated_at, enabled_by, enabled_at)
				values (?, ?, 'V1', ?, 1.000000, ?, 'ENABLED', current_date - interval '1 day',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, workOrderNo + "_BOM", materialId, workOrderNo + " bom", unitId);
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order (work_order_no, product_material_id, bom_id, planned_quantity,
					reported_quantity, qualified_quantity, defective_quantity, received_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_start_date, planned_finish_date, status, created_by, created_at,
					updated_by, updated_at, released_by, released_at)
				values (?, ?, ?, ?, 0.000000, 0.000000, 0.000000, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now(),
					'test', now())
				returning id
				""", Long.class, workOrderNo, materialId, bomId, new BigDecimal(plannedQuantity),
				new BigDecimal(receivedQuantity), warehouseId, warehouseId, plannedFinishDate.minusDays(2),
				plannedFinishDate, status);
	}

	private void insertSubstitute(long mainMaterialId, long substituteMaterialId, LocalDate effectiveFrom) {
		this.jdbcTemplate.update("""
				insert into mst_material_substitute (main_material_id, substitute_material_id, scope_type, priority,
					substitute_rate, effective_from, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'GLOBAL', 1, 1.000000, ?, 'ENABLED', 'test', now(), 'test', now())
				""", mainMaterialId, substituteMaterialId, effectiveFrom);
	}

	private JsonNode findRequirement(JsonNode items, long materialId) {
		for (JsonNode item : items) {
			if (item.get("materialId").longValue() == materialId
					&& item.get("shortageQuantity").asText().equals("5.000000")) {
				return item;
			}
		}
		throw new AssertionError("not found raw requirement: " + items);
	}

	private JsonNode findRequirementByMaterial(JsonNode items, long materialId) {
		for (JsonNode item : items) {
			if (item.get("materialId").longValue() == materialId) {
				return item;
			}
		}
		throw new AssertionError("not found requirement: " + items);
	}

	private JsonNode findRequirementByMaterialOrNull(JsonNode items, long materialId) {
		for (JsonNode item : items) {
			if (item.get("materialId").longValue() == materialId) {
				return item;
			}
		}
		return null;
	}

	private JsonNode findRequirementBySourceLine(JsonNode items, long sourceLineId) {
		for (JsonNode item : items) {
			if (item.get("demandSourceLineId").longValue() == sourceLineId) {
				return item;
			}
		}
		throw new AssertionError("not found requirement for source line " + sourceLineId + ": " + items);
	}

	private JsonNode findSuggestion(JsonNode items, long materialId, String suggestionType) {
		for (JsonNode item : items) {
			if (item.get("materialId").longValue() == materialId
					&& item.get("suggestionType").asText().equals(suggestionType)) {
				return item;
			}
		}
		throw new AssertionError("not found suggestion: " + items);
	}

	private JsonNode findSuggestionOrNull(JsonNode items, long materialId, String suggestionType) {
		for (JsonNode item : items) {
			if (item.get("materialId").longValue() == materialId
					&& item.get("suggestionType").asText().equals(suggestionType)) {
				return item;
			}
		}
		return null;
	}

	private List<String> suggestionTypes(JsonNode items) {
		List<String> types = new ArrayList<>();
		for (JsonNode item : items) {
			types.add(item.get("suggestionType").asText());
		}
		return types;
	}

	private List<String> allocationReasons(JsonNode items) {
		List<String> reasons = new ArrayList<>();
		for (JsonNode item : items) {
			if (item.hasNonNull("excludedReasonCode")) {
				reasons.add(item.get("excludedReasonCode").asText());
			}
		}
		return reasons;
	}

	private JsonNode findSubstituteHint(JsonNode items, long requirementLineId, long mainMaterialId,
			long substituteMaterialId) {
		for (JsonNode item : items) {
			if (item.get("requirementLineId").longValue() == requirementLineId
					&& item.get("mainMaterialId").longValue() == mainMaterialId
					&& item.get("substituteMaterialId").longValue() == substituteMaterialId) {
				return item;
			}
		}
		throw new AssertionError("not found substitute hint: " + items);
	}

	private void assertNoMaterial(JsonNode items, long materialId, String label) {
		for (JsonNode item : items) {
			if (item.hasNonNull("materialId") && item.get("materialId").longValue() == materialId) {
				throw new AssertionError("unexpected substitute material " + label + ": " + item);
			}
		}
	}

	private void assertDeterministicSupplySplit(AuthenticatedSession admin, long runId, long firstLineId,
			long secondLineId, long otherProjectId) throws Exception {
		JsonNode requirements = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/requirements?page=1&pageSize=50"));
		JsonNode firstRequirement = findRequirementBySourceLine(requirements.get("items"), firstLineId);
		JsonNode secondRequirement = findRequirementBySourceLine(requirements.get("items"), secondLineId);
		assertDecimal(firstRequirement, "requiredQuantity", "8.000000");
		assertDecimal(firstRequirement, "coveredQuantity", "8.000000");
		assertDecimal(firstRequirement, "shortageQuantity", "0.000000");
		assertDecimal(secondRequirement, "requiredQuantity", "8.000000");
		assertDecimal(secondRequirement, "coveredQuantity", "6.000000");
		assertDecimal(secondRequirement, "shortageQuantity", "2.000000");

		JsonNode allocations = data(get(admin,
				"/api/admin/planning/material-requirement-runs/" + runId + "/allocations?page=1&pageSize=50"));
		JsonNode items = allocations.get("items");
		long firstRequirementId = firstRequirement.get("id").longValue();
		long secondRequirementId = secondRequirement.get("id").longValue();
		assertAllocationTotal(items, firstRequirementId, "PROJECT_STOCK", "4.000000");
		assertAllocationTotal(items, firstRequirementId, "PROJECT_PURCHASE", "3.000000");
		assertAllocationTotal(items, firstRequirementId, "PUBLIC_STOCK", "1.000000");
		assertAllocationTotal(items, firstRequirementId, "PUBLIC_PURCHASE", "0.000000");
		assertAllocationTotal(items, secondRequirementId, "PROJECT_STOCK", "0.000000");
		assertAllocationTotal(items, secondRequirementId, "PROJECT_PURCHASE", "0.000000");
		assertAllocationTotal(items, secondRequirementId, "PUBLIC_STOCK", "4.000000");
		assertAllocationTotal(items, secondRequirementId, "PUBLIC_PURCHASE", "2.000000");
		assertThat(items).allSatisfy((item) -> {
			if (item.hasNonNull("projectId")) {
				assertThat(item.get("projectId").longValue()).isNotEqualTo(otherProjectId);
			}
		});
		Long overAllocatedPublicSources = this.jdbcTemplate.queryForObject("""
				select count(*)
				from (
					select source_table, source_id, source_line_id, sum(allocated_quantity) as allocated_quantity,
					       max(available_quantity) as available_quantity
					from mrp_supply_allocation
					where run_id = ?
					and ownership_type = 'PUBLIC'
					group by source_table, source_id, source_line_id
					having sum(allocated_quantity) > max(available_quantity)
				) over_allocated
				""", Long.class, runId);
		assertThat(overAllocatedPublicSources).isZero();
	}

	private void assertAllocated(JsonNode items, long requirementLineId, String supplyType, String quantity) {
		for (JsonNode item : items) {
			if (item.get("requirementLineId").longValue() == requirementLineId
					&& item.get("supplyType").asText().equals(supplyType)) {
				assertDecimal(item, "allocatedQuantity", quantity);
				assertThat(item.get("onTime").asBoolean()).isTrue();
				return;
			}
		}
		throw new AssertionError("not found allocation " + supplyType + ": " + items);
	}

	private void assertAllocationTotal(JsonNode items, long requirementLineId, String supplyType, String quantity) {
		BigDecimal total = BigDecimal.ZERO;
		for (JsonNode item : items) {
			if (item.get("requirementLineId").longValue() == requirementLineId
					&& item.get("supplyType").asText().equals(supplyType)) {
				total = total.add(new BigDecimal(item.get("allocatedQuantity").asText()));
			}
		}
		assertThat(total).isEqualByComparingTo(new BigDecimal(quantity));
	}

	private void assertDecimal(JsonNode node, String field, String expected) {
		assertThat(new BigDecimal(node.get(field).asText())).isEqualByComparingTo(new BigDecimal(expected));
	}

	private void assertRunFilterOnlyReturns(SoftAssertions softly, AuthenticatedSession session, String filter,
			JsonNode expectedRun, JsonNode excludedRun, String label) throws Exception {
		JsonNode page = data(get(session, "/api/admin/planning/material-requirement-runs?" + filter
				+ "&page=1&pageSize=20"));
		softly.assertThat(pageContainsId(page, expectedRun.get("id").longValue())).as(label + " 应包含目标运行")
			.isTrue();
		softly.assertThat(pageContainsId(page, excludedRun.get("id").longValue())).as(label + " 不应包含其他运行")
			.isFalse();
	}

	private void assertInvalidPaginationRejected(SoftAssertions softly, AuthenticatedSession session, String basePath,
			String label) throws Exception {
		assertErrorSoft(softly, get(session, appendQuery(basePath, "page=0&pageSize=20")), HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR", label + " page=0");
		assertErrorSoft(softly, get(session, appendQuery(basePath, "page=1&pageSize=25")), HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR", label + " pageSize=25");
		assertErrorSoft(softly, get(session, appendQuery(basePath, "page=1&pageSize=101")), HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR", label + " pageSize=101");
	}

	private String appendQuery(String basePath, String query) {
		return basePath + (basePath.contains("?") ? "&" : "?") + query;
	}

	private boolean pageContainsId(JsonNode page, long id) {
		for (JsonNode item : page.get("items")) {
			if (item.hasNonNull("id") && item.get("id").longValue() == id) {
				return true;
			}
		}
		return false;
	}

	private JsonNode firstItem(JsonNode page) {
		if (page.get("items").isEmpty()) {
			throw new AssertionError("page has no items: " + page);
		}
		return page.get("items").get(0);
	}

	private JsonNode firstAllocationWithReason(JsonNode items) {
		for (JsonNode item : items) {
			if (item.hasNonNull("excludedReasonCode") && !item.get("excludedReasonCode").asText().isBlank()) {
				return item;
			}
		}
		throw new AssertionError("not found excluded allocation: " + items);
	}

	private void assertItemsOnlyLong(SoftAssertions softly, JsonNode page, String field, long expected, String label) {
		softly.assertThat(page.get("items").size()).as(label + " 至少应有一行").isGreaterThan(0);
		for (JsonNode item : page.get("items")) {
			softly.assertThat(item.hasNonNull(field)).as(label + " 字段存在").isTrue();
			if (item.hasNonNull(field)) {
				softly.assertThat(item.get(field).longValue()).as(label).isEqualTo(expected);
			}
		}
	}

	private void assertItemsOnlyText(SoftAssertions softly, JsonNode page, String field, String expected, String label) {
		softly.assertThat(page.get("items").size()).as(label + " 至少应有一行").isGreaterThan(0);
		for (JsonNode item : page.get("items")) {
			softly.assertThat(item.hasNonNull(field)).as(label + " 字段存在").isTrue();
			if (item.hasNonNull(field)) {
				softly.assertThat(item.get(field).asText()).as(label).isEqualTo(expected);
			}
		}
	}

	private void assertItemsPositiveDecimal(SoftAssertions softly, JsonNode page, String field, String label) {
		softly.assertThat(page.get("items").size()).as(label + " 至少应有一行").isGreaterThan(0);
		for (JsonNode item : page.get("items")) {
			softly.assertThat(item.hasNonNull(field)).as(label + " 字段存在").isTrue();
			if (item.hasNonNull(field)) {
				softly.assertThat(new BigDecimal(item.get(field).asText())).as(label).isGreaterThan(BigDecimal.ZERO);
			}
		}
	}

	private void assertHasJsonFields(SoftAssertions softly, JsonNode node, String label, String... fields) {
		for (String field : fields) {
			softly.assertThat(node.has(field)).as(label + " 缺字段 " + field).isTrue();
		}
	}

	private void assertHasTextJsonFields(SoftAssertions softly, JsonNode node, String label, String... fields) {
		for (String field : fields) {
			softly.assertThat(node.hasNonNull(field)).as(label + " 缺可读字段 " + field).isTrue();
			if (node.hasNonNull(field)) {
				softly.assertThat(node.get(field).asText()).as(label + " 字段 " + field).isNotBlank();
			}
		}
	}

	private void assertJsonNumberOrNull(SoftAssertions softly, JsonNode node, String field, String label) {
		if (node.hasNonNull(field)) {
			softly.assertThat(node.get(field).isNumber()).as(label).isTrue();
		}
	}

	private void assertJsonBooleanOrNull(SoftAssertions softly, JsonNode node, String field, String label) {
		if (node.hasNonNull(field)) {
			softly.assertThat(node.get(field).isBoolean()).as(label).isTrue();
		}
	}

	private void assertProductionConversionRecord(JsonNode node, long suggestionId, String targetObjectType,
			String targetNoPrefix, String targetRoutePrefix) {
		assertThat(node.has("id")).as("027 生产转换响应不得继续返回完整建议 DTO：" + node).isFalse();
		assertThat(node.has("materialId")).as("027 生产转换响应不得泄露建议列表字段：" + node).isFalse();
		assertThat(node.get("suggestionId").longValue()).isEqualTo(suggestionId);
		assertThat(node.get("status").asText()).isEqualTo("CONVERTED");
		assertThat(node.get("targetObjectType").asText()).isEqualTo(targetObjectType);
		assertThat(node.get("targetObjectNo").asText()).startsWith(targetNoPrefix);
		assertThat(node.get("targetRoute").asText()).isEqualTo(targetRoutePrefix + node.get("targetObjectId").asText());
		assertThat(node.get("version").isNumber()).isTrue();
	}

	private void assertSourceFieldsRedacted(SoftAssertions softly, JsonNode node, String label, String... fields) {
		for (String field : fields) {
			if (node.has(field) && !node.get(field).isNull()) {
				JsonNode value = node.get(field);
				boolean redactedText = value.isTextual()
						&& (value.asText().isBlank() || value.asText().contains("***") || value.asText().contains("受限"));
				softly.assertThat(redactedText).as(label + " 不应泄露来源字段 " + field + "=" + value).isTrue();
			}
		}
	}

	private void assertErrorSoft(SoftAssertions softly, ResponseEntity<String> response, HttpStatus status, String code,
			String label) throws Exception {
		softly.assertThat(response.getStatusCode()).as(label + " HTTP: " + response.getBody()).isEqualTo(status);
		if (response.getBody() != null) {
			softly.assertThat(this.objectMapper.readTree(response.getBody()).get("code").asText()).as(label + " code")
				.isEqualTo(code);
		}
	}

	private DemandScope demandScope(long orderLineId) {
		return this.jdbcTemplate.queryForObject("""
				select so.id as order_id, so.customer_id, so.contract_id, sol.material_id
				from sal_sales_order_line sol
				join sal_sales_order so on so.id = sol.order_id
				where sol.id = ?
				""", (rs, rowNum) -> new DemandScope(rs.getLong("order_id"), rs.getLong("customer_id"),
				rs.getLong("contract_id"), rs.getLong("material_id")), orderLineId);
	}

	private String dateIsoString(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof LocalDate localDate) {
			return localDate.toString();
		}
		if (value instanceof java.sql.Date date) {
			return date.toLocalDate().toString();
		}
		String text = value.toString();
		return text.length() >= 10 ? text.substring(0, 10) : text;
	}

	private long bomIdForParentMaterial(long materialId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from mfg_bom
				where parent_material_id = ?
				order by id asc
				limit 1
				""", Long.class, materialId);
	}

	private long countRows(String table, String where, Object arg) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + table + " where " + where, Long.class, arg);
	}

	private AuthenticatedSession createPlanningUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) throws Exception {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "026 测试角色" + suffix, "026 测试角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
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

	private ResponseEntity<String> get(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null),
				String.class);
	}

	private ResponseEntity<byte[]> downloadBytes(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null),
				byte[].class);
	}

	private ResponseEntity<String> post(AuthenticatedSession session, String path, Object body) {
		return this.restTemplate.exchange(path, HttpMethod.POST,
				entity(body, session.sessionCookie(), session.csrfSession()), String.class);
	}

	private ResponseEntity<String> postWithIdempotency(AuthenticatedSession session, String path, Object body,
			String idempotencyKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, session.sessionCookie());
		headers.add(session.csrfSession().headerName(), session.csrfSession().token());
		headers.add("Idempotency-Key", idempotencyKey);
		return this.restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> put(AuthenticatedSession session, String path, Object body) {
		return this.restTemplate.exchange(path, HttpMethod.PUT,
				entity(body, session.sessionCookie(), session.csrfSession()), String.class);
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
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private JsonNode processTaskUntilStatus(AuthenticatedSession session, long taskId, String status, int maxAttempts)
			throws Exception {
		JsonNode task = data(get(session, "/api/admin/document-tasks/" + taskId));
		for (int i = 0; i < maxAttempts && !status.equals(task.get("status").asText()); i++) {
			if (!this.documentTaskWorker.processAvailableOnce()) {
				Thread.sleep(1100);
			}
			task = data(get(session, "/api/admin/document-tasks/" + taskId));
		}
		return task;
	}

	private String workbookText(byte[] content) throws Exception {
		StringBuilder text = new StringBuilder();
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
			for (Sheet sheet : workbook) {
				text.append(sheet.getSheetName()).append('\n');
				for (Row row : sheet) {
					row.forEach((cell) -> text.append(cell.toString()).append('\n'));
				}
			}
		}
		return text.toString();
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(this.objectMapper.readTree(response.getBody()).get("code").asText()).isEqualTo(code);
	}

	private void assertPlanningRunRejectedWithCode(ResponseEntity<String> response, String idempotencyKey, long projectId,
			LocalDate demandDateTo, String code) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(this.objectMapper.readTree(response.getBody()).get("code").asText()).isEqualTo(code);
		List<FailureRunRow> runs = failureRuns(idempotencyKey);
		assertThat(runs).hasSize(1);
		FailureRunRow run = runs.get(0);
		assertThat(run.status()).isEqualTo("FAILED");
		assertThat(run.scopeType()).isEqualTo("PROJECT");
		assertThat(run.projectId()).isEqualTo(projectId);
		assertThat(run.demandDateTo()).isEqualTo(demandDateTo);
		assertThat(run.failureCode()).isEqualTo(code);
		assertThat(run.failureSummary()).isNotBlank();
		assertThat(countRows("select count(*) from mrp_requirement_line where run_id = ?", run.id())).isZero();
		assertThat(countRows("select count(*) from mrp_supply_allocation where run_id = ?", run.id())).isZero();
		assertThat(countRows("select count(*) from mrp_suggestion where run_id = ?", run.id())).isZero();
	}

	private List<FailureRunRow> failureRuns(String idempotencyKey) {
		return this.jdbcTemplate.query("""
				select id, status, scope_type, project_id, demand_date_to, failure_code, failure_summary
				from mrp_calculation_run
				where idempotency_key = ?
				order by id asc
				""", (rs, rowNum) -> new FailureRunRow(rs.getLong("id"), rs.getString("status"),
				rs.getString("scope_type"), rs.getObject("project_id", Long.class),
				rs.getObject("demand_date_to", LocalDate.class), rs.getString("failure_code"),
				rs.getString("failure_summary")), idempotencyKey);
	}

	private void assertAuditCount(String action, String result, String targetType, String targetId, String errorCode,
			long expected) {
		long count = errorCode == null ? countRowsForSql("""
				select count(*)
				from sys_audit_log
				where action = ?
				and result = ?
				and target_type = ?
				and target_id = ?
				""", action, result, targetType, targetId) : countRowsForSql("""
				select count(*)
				from sys_audit_log
				where action = ?
				and result = ?
				and target_type = ?
				and target_id = ?
				and error_code = ?
				""", action, result, targetType, targetId, errorCode);
		assertThat(count).isEqualTo(expected);
	}

	private long countRows(String sql, long runId) {
		Long count = this.jdbcTemplate.queryForObject(sql, Long.class, runId);
		return count == null ? 0 : count;
	}

	private long countRowsForSql(String sql, Object... args) {
		Long count = this.jdbcTemplate.queryForObject(sql, Long.class, args);
		return count == null ? 0 : count;
	}

	private long runIdByIdempotencyKey(String idempotencyKey) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from mrp_calculation_run
				where idempotency_key = ?
				""", Long.class, idempotencyKey);
	}

	private ResponseEntity<String> postAfterStart(AuthenticatedSession session, String path, Object body,
			CountDownLatch start) throws InterruptedException {
		start.await(5, TimeUnit.SECONDS);
		return post(session, path, body);
	}

	private ResponseEntity<String> response(Future<ResponseEntity<String>> future) throws Exception {
		return future.get(15, TimeUnit.SECONDS);
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

	private record DeliveryPlanDemandSeed(long orderId, long orderLineId, long firstPlanId, long secondPlanId) {
	}

	private record Fixture(long projectId, long rawMaterialId, long orderLineId, LocalDate demandDate,
			long warehouseId, long unitId, long substituteMaterialId) {
	}

	private record DemandScope(long orderId, long customerId, long contractId, long materialId) {
	}

	private record DemandSeed(long projectId, long materialId, long unitId, long categoryId, long demandLineId) {
	}

	private record FailureRunRow(long id, String status, String scopeType, Long projectId, LocalDate demandDateTo,
			String failureCode, String failureSummary) {
	}

	private record BomItemSeed(int lineNo, long materialId, long unitId, BigDecimal quantity) {
	}

}
