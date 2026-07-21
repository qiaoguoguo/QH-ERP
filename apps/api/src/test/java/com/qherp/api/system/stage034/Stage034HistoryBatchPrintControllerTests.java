package com.qherp.api.system.stage034;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.platform.PlatformDocumentTaskWorker;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"qherp.test.context=stage034-history-batch-print",
				"qherp.platform.task.worker.enabled=false",
				"qherp.delivery.environment-code=stage034-it",
				"qherp.delivery.manual-version=manual-034.1",
				"qherp.delivery.manual-updated-at=2026-07-22T10:00:00+08:00",
				"qherp.delivery.demo-data-version=demo-034.1",
				"qherp.delivery.demo-data-status=VERIFIED",
				"qherp.delivery.demo-data-verified-at=2026-07-22T11:00:00+08:00"
		})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Stage034HistoryBatchPrintControllerTests extends PostgresIntegrationTest {

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
		registry.add("qherp.storage.s3.bucket", () -> "qherp-test-private-stage034");
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
	void customerHistoryImportPrechecksConfirmsIdempotentlyAndRejectsDuplicateRowsAtomically() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode adapters = data(get(admin, "/api/admin/platform/history-import-adapters"));
		assertThat(adapters.toString()).contains("CUSTOMER_MASTER_V1", "SUPPLIER_MASTER_V1", "MATERIAL_MASTER_V1",
				"BOM_DRAFT_V1", "SALES_PROJECT_DRAFT_V1");
		AuthenticatedSession customerImporter = createUserAndLogin("stage034-history-customer-only",
				"S34_HISTORY_CUS_ONLY", List.of("platform:history-import:view", "master:customer:create"));
		JsonNode scopedAdapters = data(get(customerImporter, "/api/admin/platform/history-import-adapters"));
		assertThat(scopedAdapters.toString()).contains("CUSTOMER_MASTER_V1");
		assertThat(scopedAdapters.toString()).doesNotContain("SUPPLIER_MASTER_V1", "MATERIAL_MASTER_V1",
				"BOM_DRAFT_V1", "SALES_PROJECT_DRAFT_V1");
		ResponseEntity<byte[]> template = downloadBytes(admin,
				"/api/admin/platform/history-import-adapters/CUSTOMER_MASTER_V1/template");
		assertThat(template.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(template.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains(".xlsx");

		String customerCode = "S34_HIS_CUS_" + SEQUENCE.incrementAndGet();
		byte[] workbook = customerWorkbook(customerCode, "034 历史客户");
		JsonNode staged = data(uploadImport(admin, "/api/admin/platform/history-imports/CUSTOMER_MASTER_V1",
				"customers.xlsx", workbook, "history-customer-" + customerCode));
		long taskId = staged.get("id").longValue();
		assertThat(staged.get("adapterCode").asText()).isEqualTo("CUSTOMER_MASTER_V1");
		assertThat(staged.get("status").asText()).isEqualTo("READY_TO_COMMIT");
		assertThat(staged.get("totalRows").intValue()).isOne();
		assertThat(staged.get("failedRows").intValue()).isZero();
		assertThat(staged.get("availableActions").toString()).contains("CONFIRM");
		assertThat(customerCount(customerCode)).isZero();

		JsonNode same = data(uploadImport(admin, "/api/admin/platform/history-imports/CUSTOMER_MASTER_V1",
				"customers.xlsx", workbook, "history-customer-" + customerCode));
		assertThat(same.get("id").longValue()).isEqualTo(taskId);
		assertError(uploadImport(admin, "/api/admin/platform/history-imports/CUSTOMER_MASTER_V1",
				"customers-other.xlsx", customerWorkbook("S34_HIS_CUS_OTHER_" + SEQUENCE.incrementAndGet(), "同键不同文件"),
				"history-customer-" + customerCode), HttpStatus.CONFLICT, "DOCUMENT_TASK_IDEMPOTENCY_CONFLICT");

		JsonNode confirmed = data(post(admin, "/api/admin/platform/history-imports/" + taskId + "/confirm",
				Map.of("version", staged.get("version").longValue(), "idempotencyKey", "history-confirm-" + taskId)));
		assertThat(confirmed.get("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(customerCount(customerCode)).isOne();
		JsonNode repeatedConfirm = data(post(admin, "/api/admin/platform/history-imports/" + taskId + "/confirm",
				Map.of("version", confirmed.get("version").longValue(), "idempotencyKey", "history-confirm-" + taskId)));
		assertThat(repeatedConfirm.get("id").longValue()).isEqualTo(taskId);
		assertThat(customerCount(customerCode)).isOne();

		JsonNode duplicate = data(uploadImport(admin, "/api/admin/platform/history-imports/CUSTOMER_MASTER_V1",
				"duplicate-customers.xlsx", customerWorkbook(customerCode, "034 重复客户"),
				"history-duplicate-" + customerCode));
		assertThat(duplicate.get("status").asText()).isEqualTo("VALIDATION_FAILED");
		assertThat(duplicate.get("failedRows").intValue()).isOne();
		assertThat(customerCount(customerCode)).isOne();
	}

	@Test
	void materialHistoryImportRejectsInvalidEnumBeforeConfirmAndConfirmsValidRows() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String categoryCode = "S34_HIS_MAT_CAT_" + SEQUENCE.incrementAndGet();
		String unitCode = "S34_HIS_MAT_UNIT_" + SEQUENCE.incrementAndGet();
		insertMaterialCategory(categoryCode, "034 历史物料分类");
		insertUnit(unitCode, "034 历史物料单位");

		String validCode = "S34_HIS_MAT_OK_" + SEQUENCE.incrementAndGet();
		String invalidCode = "S34_HIS_MAT_BAD_" + SEQUENCE.incrementAndGet();
		JsonNode rejected = data(uploadImport(admin, "/api/admin/platform/history-imports/MATERIAL_MASTER_V1",
				"materials-invalid-cost.xlsx",
				materialWorkbook(List.<String[]>of(
						materialRow(validCode, "034 合法物料", categoryCode, unitCode, "DIRECT_MATERIAL",
								"VALUATED_MATERIAL", "true", "true"),
						materialRow(invalidCode, "034 非法成本物料", categoryCode, unitCode, "STANDARD",
								"VALUATED_MATERIAL", "true", "true"))),
				"history-material-invalid-" + invalidCode));
		long rejectedTaskId = rejected.get("id").longValue();
		assertThat(rejected.get("status").asText()).isEqualTo("VALIDATION_FAILED");
		assertThat(rejected.get("failedRows").intValue()).isGreaterThan(0);
		assertThat(rejected.get("availableActions").toString()).doesNotContain("CONFIRM");
		JsonNode rejectedTask = data(get(admin, "/api/admin/document-tasks?taskId=" + rejectedTaskId
				+ "&pageSize=50")).get("items").get(0);
		assertThat(rejectedTask.get("availableActions").toString()).contains("ERRORS", "CANCEL")
			.doesNotContain("CONFIRM");
		assertTaskError(admin, rejectedTaskId, 3, "costCategory", "IMPORT_VALIDATION_FAILED");
		JsonNode importErrors = data(get(admin, "/api/admin/document-tasks/" + rejectedTaskId
				+ "/errors?page=1&pageSize=1"));
		assertThat(importErrors.get("total").longValue()).isGreaterThanOrEqualTo(1);
		assertThat(importErrors.get("items").toString()).contains("\"rowNo\":3",
				"\"columnName\":\"costCategory\"", "\"errorCode\":\"IMPORT_VALIDATION_FAILED\"");
		AuthenticatedSession historyViewOnly = createUserAndLogin("stage034-history-errors-no-adapter",
				"S34_HISTORY_ERR_NO_ADAPTER", List.of("platform:document-task:view",
						"platform:document-task:view-all", "platform:history-import:view"));
		assertError(get(historyViewOnly, "/api/admin/document-tasks/" + rejectedTaskId + "/errors?pageSize=50"),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertThat(materialCount(validCode)).isZero();
		assertThat(materialCount(invalidCode)).isZero();
		assertError(post(admin, "/api/admin/platform/history-imports/" + rejectedTaskId + "/confirm",
				Map.of("version", rejected.get("version").longValue(), "idempotencyKey", "history-confirm-invalid-"
						+ rejectedTaskId)),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_STATUS_INVALID");
		JsonNode cancelledRejected = data(post(admin, "/api/admin/platform/history-imports/" + rejectedTaskId
				+ "/cancel", Map.of("version", rejected.get("version").longValue(), "reason", "034 预检失败取消",
						"idempotencyKey", "history-cancel-invalid-" + rejectedTaskId)));
		assertThat(cancelledRejected.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(materialCount(validCode)).isZero();
		assertThat(materialCount(invalidCode)).isZero();

		String legalCode = "S34_HIS_MAT_LEGAL_" + SEQUENCE.incrementAndGet();
		JsonNode staged = data(uploadImport(admin, "/api/admin/platform/history-imports/MATERIAL_MASTER_V1",
				"materials-valid-cost.xlsx",
				materialWorkbook(List.<String[]>of(materialRow(legalCode, "034 合法成本物料", categoryCode, unitCode,
						"DIRECT_MATERIAL", "VALUATED_MATERIAL", "true", "true"))),
				"history-material-valid-" + legalCode));
		assertThat(staged.get("status").asText()).isEqualTo("READY_TO_COMMIT");
		assertThat(staged.get("failedRows").intValue()).isZero();
		JsonNode confirmed = data(post(admin, "/api/admin/platform/history-imports/" + staged.get("id").longValue()
				+ "/confirm", Map.of("version", staged.get("version").longValue(),
						"idempotencyKey", "history-confirm-valid-" + legalCode)));
		assertThat(confirmed.get("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(materialCount(legalCode)).isOne();
		assertThat(materialCostCategory(legalCode)).isEqualTo("DIRECT_MATERIAL");
	}

	@Test
	void historyImportConfirmRejectsTemplateVersionAndSourceFingerprintDrift() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String templateDriftCode = "S34_HIS_TMPL_DRIFT_" + SEQUENCE.incrementAndGet();
		JsonNode templateDrift = data(uploadImport(admin, "/api/admin/platform/history-imports/CUSTOMER_MASTER_V1",
				"template-drift-customers.xlsx", customerWorkbook(templateDriftCode, "034 模板漂移客户"),
				"history-template-drift-" + templateDriftCode));
		int originalTemplateVersion = this.jdbcTemplate.queryForObject("""
				select template_version
				from platform_import_adapter_definition
				where adapter_code = 'CUSTOMER_MASTER_V1'
				""", Integer.class);
		try {
			this.jdbcTemplate.update("""
					update platform_import_adapter_definition
					set template_version = template_version + 1, version = version + 1
					where adapter_code = 'CUSTOMER_MASTER_V1'
					""");
			assertError(post(admin, "/api/admin/platform/history-imports/" + templateDrift.get("id").longValue()
					+ "/confirm", Map.of("version", templateDrift.get("version").longValue(),
							"idempotencyKey", "history-confirm-template-drift-" + templateDriftCode)),
					HttpStatus.CONFLICT, "HISTORY_IMPORT_TEMPLATE_VERSION_MISMATCH");
			assertThat(customerCount(templateDriftCode)).isZero();
		}
		finally {
			this.jdbcTemplate.update("""
					update platform_import_adapter_definition
					set template_version = ?, version = version + 1
					where adapter_code = 'CUSTOMER_MASTER_V1'
					""", originalTemplateVersion);
		}

		String fingerprintDriftCode = "S34_HIS_SHA_DRIFT_" + SEQUENCE.incrementAndGet();
		JsonNode fingerprintDrift = data(uploadImport(admin, "/api/admin/platform/history-imports/CUSTOMER_MASTER_V1",
				"fingerprint-drift-customers.xlsx", customerWorkbook(fingerprintDriftCode, "034 指纹漂移客户"),
				"history-fingerprint-drift-" + fingerprintDriftCode));
		this.jdbcTemplate.update("""
				update platform_file_object
				set sha256 = repeat('0', 64), version = version + 1
				where id = (
					select source_file_id
					from platform_document_task
					where id = ?
				)
				""", fingerprintDrift.get("id").longValue());
		assertError(post(admin, "/api/admin/platform/history-imports/" + fingerprintDrift.get("id").longValue()
				+ "/confirm", Map.of("version", fingerprintDrift.get("version").longValue(),
						"idempotencyKey", "history-confirm-fingerprint-drift-" + fingerprintDriftCode)),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_CONCURRENT_MODIFICATION");
		assertThat(customerCount(fingerprintDriftCode)).isZero();
	}

	@Test
	void historyImportTasksRejectLegacyConfirmCancelAndSupportDocumentTaskDeepLinks() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String customerCode = "S34_LEGACY_IMPORT_" + SEQUENCE.incrementAndGet();
		JsonNode staged = data(uploadImport(admin, "/api/admin/platform/history-imports/CUSTOMER_MASTER_V1",
				"legacy-history-customer.xlsx", customerWorkbook(customerCode, "034 旧确认旁路客户"),
				"history-legacy-upload-" + customerCode));
		long taskId = staged.get("id").longValue();

		JsonNode taskPage = data(get(admin, "/api/admin/document-tasks?taskId=" + taskId + "&pageSize=50"));
		assertThat(taskPage.get("total").longValue()).isOne();
		assertThat(taskPage.get("items").get(0).get("id").longValue()).isEqualTo(taskId);
		assertThat(taskPage.get("items").get(0).get("availableActions").toString()).contains("CONFIRM",
				"CANCEL");
		assertError(postWithIdempotency(admin, "/api/admin/imports/" + taskId + "/confirm",
				Map.of("version", staged.get("version").longValue()), "legacy-history-confirm-" + taskId),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_STATUS_INVALID");
		assertError(post(admin, "/api/admin/document-tasks/" + taskId + "/cancel",
				Map.of("version", staged.get("version").longValue(), "reason", "旧通用取消不得处理历史导入")),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_STATUS_INVALID");

		JsonNode cancelled = data(post(admin, "/api/admin/platform/history-imports/" + taskId + "/cancel",
				Map.of("version", staged.get("version").longValue(), "reason", "034 平台历史导入取消",
						"idempotencyKey", "history-platform-cancel-" + taskId)));
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");

		String queuedCode = "S34_LEGACY_IMPORT_QUEUED_" + SEQUENCE.incrementAndGet();
		JsonNode queued = data(uploadImport(admin, "/api/admin/platform/history-imports/CUSTOMER_MASTER_V1",
				"queued-history-customer.xlsx", customerWorkbook(queuedCode, "034 队列中历史客户"),
				"history-queued-upload-" + queuedCode));
		long queuedTaskId = queued.get("id").longValue();
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'QUEUED', version = version + 1
				where id = ?
				""", queuedTaskId);
		JsonNode queuedTask = data(get(admin, "/api/admin/document-tasks?taskId=" + queuedTaskId + "&pageSize=50"))
			.get("items")
			.get(0);
		assertThat(queuedTask.get("availableActions").toString()).doesNotContain("CONFIRM", "CANCEL");
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'RUNNING', version = version + 1
				where id = ?
				""", queuedTaskId);
		JsonNode runningTask = data(get(admin, "/api/admin/document-tasks?taskId=" + queuedTaskId + "&pageSize=50"))
			.get("items")
			.get(0);
		assertThat(runningTask.get("availableActions").toString()).doesNotContain("CONFIRM", "CANCEL");
	}

	@Test
	void deliveryAssetCatalogExposesFrozenReleaseMetadata() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode catalog = data(get(admin, "/api/admin/platform/delivery-assets"));
		assertThat(catalog.get("stageCode").asText()).isEqualTo("034");
		assertThat(catalog.get("environmentCode").asText()).isEqualTo("stage034-it");
		assertThat(catalog.get("manual").get("version").asText()).isEqualTo("manual-034.1");
		assertThat(catalog.get("manual").get("updatedAt").asText()).isEqualTo("2026-07-22T10:00:00+08:00");
		assertThat(catalog.get("demoData").get("version").asText()).isEqualTo("demo-034.1");
		assertThat(catalog.get("demoData").get("status").asText()).isEqualTo("VERIFIED");
		assertThat(catalog.get("demoData").get("verifiedAt").asText()).isEqualTo("2026-07-22T11:00:00+08:00");
		assertThat(catalog.get("historyImportAdapters").size()).isEqualTo(5);
		assertThat(catalog.get("dataRepairAdapters").size()).isEqualTo(3);
		assertThat(catalog.get("batchTools").size()).isEqualTo(4);
		assertThat(catalog.get("printTemplates").size()).isEqualTo(14);
		assertThat(catalog.get("staticAssets").size()).isGreaterThanOrEqualTo(2);
	}

	@Test
	void bomHistoryImportConfirmsV18QuantitySnapshotsAndRejectsInvalidItems() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String categoryCode = "S34_HIS_BOM_CAT_" + SEQUENCE.incrementAndGet();
		long categoryId = insertMaterialCategory(categoryCode, "034 BOM 历史分类");
		String baseUnitCode = "S34_HIS_BOM_PCS_" + SEQUENCE.incrementAndGet();
		String boxUnitCode = "S34_HIS_BOM_BOX_" + SEQUENCE.incrementAndGet();
		String noConversionUnitCode = "S34_HIS_BOM_BAG_" + SEQUENCE.incrementAndGet();
		long baseUnitId = insertUnit(baseUnitCode, "034 BOM 基准单位");
		long boxUnitId = insertUnit(boxUnitCode, "034 BOM 业务单位");
		insertUnit(noConversionUnitCode, "034 BOM 无换算单位");
		String parentCode = "S34_HIS_BOM_PARENT_" + SEQUENCE.incrementAndGet();
		String childCode = "S34_HIS_BOM_CHILD_" + SEQUENCE.incrementAndGet();
		long parentId = insertMaterial(parentCode, "034 BOM 父项", categoryId, baseUnitId);
		long childId = insertMaterial(childCode, "034 BOM 子项", categoryId, baseUnitId);
		long conversionId = insertUnitConversion(childId, baseUnitId, boxUnitId, "2.500000");

		String bomCode = "S34_HIS_BOM_" + SEQUENCE.incrementAndGet();
		JsonNode staged = data(uploadImport(admin, "/api/admin/platform/history-imports/BOM_DRAFT_V1",
				"bom-valid.xlsx",
				bomWorkbook(bomCode, parentCode, "V1", baseUnitCode,
						List.<String[]>of(bomItemRow("1", childCode, boxUnitCode, "4", "0.125", "034 BOM 明细"))),
				"history-bom-valid-" + bomCode));
		assertThat(staged.get("status").asText()).isEqualTo("READY_TO_COMMIT");
		assertThat(staged.get("failedRows").intValue()).isZero();

		JsonNode confirmed = data(post(admin, "/api/admin/platform/history-imports/" + staged.get("id").longValue()
				+ "/confirm", Map.of("version", staged.get("version").longValue(),
						"idempotencyKey", "history-confirm-bom-" + bomCode)));
		assertThat(confirmed.get("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(bomCount(bomCode)).isOne();
		Map<String, Object> item = bomItemSnapshot(bomCode, 1);
		assertThat(number(item, "child_material_id")).isEqualTo(childId);
		assertThat(number(item, "unit_id")).isEqualTo(boxUnitId);
		assertDecimal(item, "quantity", "4.000000");
		assertThat(number(item, "business_unit_id")).isEqualTo(boxUnitId);
		assertDecimal(item, "business_quantity", "4.000000");
		assertThat(number(item, "base_unit_id")).isEqualTo(baseUnitId);
		assertDecimal(item, "base_quantity", "10.000000");
		assertThat(number(item, "conversion_id")).isEqualTo(conversionId);
		assertDecimal(item, "conversion_rate_snapshot", "2.500000");
		assertThat(number(item, "quantity_scale_snapshot")).isEqualTo(6);
		assertThat(item.get("rounding_mode_snapshot")).isEqualTo("HALF_UP");
		assertThat(item.get("quantity_basis")).isEqualTo("CONVERTED_BUSINESS_UNIT");

		String invalidBomCode = "S34_HIS_BOM_BAD_" + SEQUENCE.incrementAndGet();
		JsonNode rejected = data(uploadImport(admin, "/api/admin/platform/history-imports/BOM_DRAFT_V1",
				"bom-invalid.xlsx",
				bomWorkbook(invalidBomCode, parentCode, "V2", baseUnitCode,
						List.<String[]>of(
								bomItemRow("1", childCode, boxUnitCode, "2", "0", "034 可用明细"),
								bomItemRow("2", childCode, noConversionUnitCode, "3", "0", "034 无换算"),
								bomItemRow("3", childCode, boxUnitCode, "-1", "0", "034 非法数量"),
								bomItemRow("4", childCode, "", "1", "0", "034 缺业务单位"))),
				"history-bom-invalid-" + invalidBomCode));
		long rejectedTaskId = rejected.get("id").longValue();
		assertThat(rejected.get("status").asText()).isEqualTo("VALIDATION_FAILED");
		assertThat(rejected.get("failedRows").intValue()).isGreaterThanOrEqualTo(3);
		assertThat(rejected.get("availableActions").toString()).doesNotContain("CONFIRM");
		assertTaskError(admin, rejectedTaskId, 2, "businessUnit", "UNIT_CONVERSION_REQUIRED");
		assertTaskError(admin, rejectedTaskId, 3, "businessQuantity", "IMPORT_VALIDATION_FAILED");
		assertTaskError(admin, rejectedTaskId, 4, "businessUnit", "MASTER_DATA_REFERENCE_INVALID");
		assertThat(bomCount(invalidBomCode)).isZero();
		assertError(post(admin, "/api/admin/platform/history-imports/" + rejectedTaskId + "/confirm",
				Map.of("version", rejected.get("version").longValue(), "idempotencyKey", "history-confirm-invalid-bom-"
						+ rejectedTaskId)),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_STATUS_INVALID");
		assertThat(bomCount(invalidBomCode)).isZero();
		assertThat(number(bomItemSnapshot(bomCode, 1), "conversion_id")).isEqualTo(conversionId);
	}

	@Test
	void historyImportCancelAndExpiryPreventCommit() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String cancelledCustomerCode = "S34_HIS_CANCEL_CUS_" + SEQUENCE.incrementAndGet();
		JsonNode staged = data(uploadImport(admin, "/api/admin/platform/history-imports/CUSTOMER_MASTER_V1",
				"cancel-customers.xlsx", customerWorkbook(cancelledCustomerCode, "034 取消历史客户"),
				"history-cancel-" + cancelledCustomerCode));
		long cancelledTaskId = staged.get("id").longValue();
		JsonNode cancelled = data(post(admin, "/api/admin/platform/history-imports/" + cancelledTaskId + "/cancel",
				Map.of("version", staged.get("version").longValue(), "idempotencyKey", "history-cancel-action-"
						+ cancelledTaskId)));
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(cancelled.get("availableActions").toString()).doesNotContain("CONFIRM");
		assertThat(historyImportBatchStatus(cancelledTaskId)).isEqualTo("CANCELLED");
		assertError(post(admin, "/api/admin/platform/history-imports/" + cancelledTaskId + "/confirm",
				Map.of("version", cancelled.get("version").longValue(), "idempotencyKey", "history-confirm-cancelled-"
						+ cancelledTaskId)),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_STATUS_INVALID");
		assertThat(customerCount(cancelledCustomerCode)).isZero();

		String expiredCustomerCode = "S34_HIS_EXPIRE_CUS_" + SEQUENCE.incrementAndGet();
		JsonNode expiring = data(uploadImport(admin, "/api/admin/platform/history-imports/CUSTOMER_MASTER_V1",
				"expire-customers.xlsx", customerWorkbook(expiredCustomerCode, "034 过期历史客户"),
				"history-expire-" + expiredCustomerCode));
		long expiringTaskId = expiring.get("id").longValue();
		this.jdbcTemplate.update("""
				update platform_document_task
				set expires_at = now() - interval '1 second'
				where id = ?
				""", expiringTaskId);
		JsonNode expired = data(get(admin, "/api/admin/platform/history-imports/" + expiringTaskId));
		assertThat(expired.get("status").asText()).isEqualTo("EXPIRED");
		assertThat(historyImportBatchStatus(expiringTaskId)).isEqualTo("CANCELLED");
		assertError(post(admin, "/api/admin/platform/history-imports/" + expiringTaskId + "/confirm",
				Map.of("version", expired.get("version").longValue(), "idempotencyKey", "history-confirm-expired-"
						+ expiringTaskId)),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_STATUS_INVALID");
		assertThat(customerCount(expiredCustomerCode)).isZero();
	}

	@Test
	void customerStatusBatchToolIsAllOrNothingAndRevalidatesBeforeExecute() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long firstId = insertCustomer("S34_BATCH_CUS_A_" + SEQUENCE.incrementAndGet(), "034 批量客户 A");
		long secondId = insertCustomer("S34_BATCH_CUS_B_" + SEQUENCE.incrementAndGet(), "034 批量客户 B");
		JsonNode tools = data(get(admin, "/api/admin/platform/batch-tools"));
		assertThat(tools.toString()).contains("CUSTOMER_STATUS_CHANGE_V1", "SUPPLIER_STATUS_CHANGE_V1",
				"MATERIAL_STATUS_CHANGE_V1", "FIXED_DOCUMENT_BATCH_PRINT_V1");

		Map<String, Object> payload = batchPayload("DISABLED", List.of(target(firstId), target(secondId)),
				"batch-customer-" + firstId);
		JsonNode preview = data(post(admin, "/api/admin/platform/batch-tools/CUSTOMER_STATUS_CHANGE_V1/preview",
				payload));
		long operationId = preview.get("id").longValue();
		assertThat(preview.get("status").asText()).isEqualTo("PRECHECKED");
		assertThat(preview.get("totalRows").intValue()).isEqualTo(2);
		assertThat(preview.get("availableActions").toString()).contains("EXECUTE");
		JsonNode samePreview = data(post(admin, "/api/admin/platform/batch-tools/CUSTOMER_STATUS_CHANGE_V1/preview",
				payload));
		assertThat(samePreview.get("id").longValue()).isEqualTo(operationId);
		assertError(post(admin, "/api/admin/platform/batch-tools/CUSTOMER_STATUS_CHANGE_V1/preview",
				batchPayload("ENABLED", List.of(target(firstId), target(secondId)), "batch-customer-" + firstId)),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_IDEMPOTENCY_CONFLICT");

		this.jdbcTemplate.update("update mst_customer set remark = '034 并发变化', version = version + 1 where id = ?",
				secondId);
		assertError(post(admin, "/api/admin/platform/batch-operations/" + operationId + "/execute",
				Map.of("version", preview.get("version").longValue(), "idempotencyKey", "batch-execute-" + operationId)),
				HttpStatus.CONFLICT, "BATCH_OPERATION_OBJECT_CHANGED");
		assertThat(customerStatus(firstId)).isEqualTo("ENABLED");
		assertThat(customerStatus(secondId)).isEqualTo("ENABLED");

		Map<String, Object> freshPayload = batchPayload("DISABLED", List.of(target(firstId), target(secondId)),
				"batch-customer-fresh-" + firstId);
		JsonNode freshPreview = data(post(admin, "/api/admin/platform/batch-tools/CUSTOMER_STATUS_CHANGE_V1/preview",
				freshPayload));
		JsonNode executed = data(post(admin, "/api/admin/platform/batch-operations/"
				+ freshPreview.get("id").longValue() + "/execute",
				Map.of("version", freshPreview.get("version").longValue(),
						"idempotencyKey", "batch-execute-fresh-" + firstId)));
		assertThat(executed.get("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(customerStatus(firstId)).isEqualTo("DISABLED");
		assertThat(customerStatus(secondId)).isEqualTo("DISABLED");
	}

	@Test
	void batchToolDirectoryActionsAndStatusPrecheckFilterPermissionsAndOpenReferences() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession customerPreviewOnly = createUserAndLogin("stage034-batch-customer-preview",
				"S34_BATCH_CUS_PREVIEW", List.of("platform:batch-tool:view", "platform:batch-tool:preview",
						"master:customer:update"));
		JsonNode scopedTools = data(get(customerPreviewOnly, "/api/admin/platform/batch-tools"));
		assertThat(scopedTools.toString()).contains("CUSTOMER_STATUS_CHANGE_V1");
		assertThat(scopedTools.toString()).doesNotContain("SUPPLIER_STATUS_CHANGE_V1",
				"MATERIAL_STATUS_CHANGE_V1", "FIXED_DOCUMENT_BATCH_PRINT_V1");

		long plainCustomerId = insertCustomer("S34_BATCH_ACTION_CUS_" + SEQUENCE.incrementAndGet(),
				"034 批量动作客户");
		JsonNode previewOnly = data(post(customerPreviewOnly,
				"/api/admin/platform/batch-tools/CUSTOMER_STATUS_CHANGE_V1/preview",
				batchPayload("DISABLED", List.of(target(plainCustomerId)),
						"batch-preview-only-" + plainCustomerId)));
		assertThat(previewOnly.get("status").asText()).isEqualTo("PRECHECKED");
		assertThat(previewOnly.get("availableActions").toString()).doesNotContain("EXECUTE", "CANCEL");

		long referencedCustomerId = insertCustomer("S34_BATCH_REF_CUS_" + SEQUENCE.incrementAndGet(),
				"034 批量引用客户");
		insertSalesOrder(referencedCustomerId, "S34_BATCH_REF_SO_" + SEQUENCE.incrementAndGet());
		JsonNode blocked = data(post(admin, "/api/admin/platform/batch-tools/CUSTOMER_STATUS_CHANGE_V1/preview",
				batchPayload("DISABLED", List.of(target(referencedCustomerId)),
						"batch-open-ref-" + referencedCustomerId)));
		assertThat(blocked.get("status").asText()).isEqualTo("PRECHECK_FAILED");
		assertThat(blocked.get("failedRows").intValue()).isOne();
		assertThat(blocked.get("availableActions").toString()).doesNotContain("EXECUTE", "CANCEL");
		assertThat(blocked.get("items").toString()).contains("BATCH_OPERATION_PRECHECK_FAILED");
		assertThat(customerStatus(referencedCustomerId)).isEqualTo("ENABLED");
	}

	@Test
	void fixedDocumentBatchPrintCreatesTasksIdempotentlyAndRevalidatesBeforeExecute() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("S34_BATCH_PRINT_CUS_" + SEQUENCE.incrementAndGet(), "034 批量打印客户");
		long firstOrderId = insertSalesOrder(customerId, "S34_BPSO_A_" + SEQUENCE.incrementAndGet());
		long secondOrderId = insertSalesOrder(customerId, "S34_BPSO_B_" + SEQUENCE.incrementAndGet());

		Map<String, Object> payload = fixedPrintBatchPayload("SALES_ORDER", "SALES_ORDER_V1",
				List.of(salesOrderTarget(firstOrderId), salesOrderTarget(secondOrderId)),
				"batch-print-" + firstOrderId);
		JsonNode preview = data(post(admin,
				"/api/admin/platform/batch-tools/FIXED_DOCUMENT_BATCH_PRINT_V1/preview", payload));
		long operationId = preview.get("id").longValue();
		assertThat(preview.get("status").asText()).isEqualTo("PRECHECKED");
		assertThat(preview.get("totalRows").intValue()).isEqualTo(2);
		assertThat(preview.get("availableActions").toString()).contains("EXECUTE");

		this.jdbcTemplate.update("update sal_sales_order set remark = '034 批量打印并发变化', version = version + 1 where id = ?",
				secondOrderId);
		assertError(post(admin, "/api/admin/platform/batch-operations/" + operationId + "/execute",
				Map.of("version", preview.get("version").longValue(), "idempotencyKey", "batch-print-execute-"
						+ operationId)),
				HttpStatus.CONFLICT, "BATCH_OPERATION_OBJECT_CHANGED");
		assertThat(fixedPrintTaskCount(firstOrderId, secondOrderId)).isZero();

		JsonNode freshPreview = data(post(admin,
				"/api/admin/platform/batch-tools/FIXED_DOCUMENT_BATCH_PRINT_V1/preview",
				fixedPrintBatchPayload("SALES_ORDER", "SALES_ORDER_V1",
						List.of(salesOrderTarget(firstOrderId), salesOrderTarget(secondOrderId)),
						"batch-print-fresh-" + firstOrderId)));
		JsonNode executed = data(post(admin, "/api/admin/platform/batch-operations/"
				+ freshPreview.get("id").longValue() + "/execute",
				Map.of("version", freshPreview.get("version").longValue(),
						"idempotencyKey", "batch-print-execute-fresh-" + firstOrderId)));
		assertThat(executed.get("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(executed.get("items").toString()).contains("执行成功");
		assertThat(batchDocumentTaskId(freshPreview.get("id").longValue())).isNotNull();
		JsonNode batchTaskPage = data(get(admin, "/api/admin/document-tasks?batchOperationId="
				+ freshPreview.get("id").longValue() + "&pageSize=50"));
		assertThat(batchTaskPage.get("total").longValue()).isOne();
		assertThat(batchTaskPage.get("items").get(0).get("id").longValue())
			.isEqualTo(batchDocumentTaskId(freshPreview.get("id").longValue()));
		assertThat(fixedPrintTaskCount(firstOrderId, secondOrderId)).isEqualTo(2);
		assertThat(this.objectMapper.readTree(firstFixedPrintTaskPayload(firstOrderId)).get("templateVersion").intValue())
			.isOne();
		JsonNode repeatedExecute = data(post(admin, "/api/admin/platform/batch-operations/"
				+ freshPreview.get("id").longValue() + "/execute",
				Map.of("version", executed.get("version").longValue(),
						"idempotencyKey", "batch-print-execute-fresh-" + firstOrderId)));
		assertThat(repeatedExecute.get("id").longValue()).isEqualTo(freshPreview.get("id").longValue());
		assertThat(fixedPrintTaskCount(firstOrderId, secondOrderId)).isEqualTo(2);
	}

	@Test
	void newFixedPrintTemplateKeepsVersionAndReauthorizesWorkerAndDownload() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode templates = data(get(admin, "/api/admin/print-templates"));
		assertThat(templates.toString()).contains("SALES_ORDER_V1", "ACCOUNTING_VOUCHER_V1",
				"PROCUREMENT_ORDER_V1", "SALES_QUOTE_V1");
		long customerId = insertCustomer("S34_PRINT_CUS_" + SEQUENCE.incrementAndGet(), "034 打印客户");
		long categoryId = insertMaterialCategory("S34_PRINT_CAT_" + SEQUENCE.incrementAndGet(), "034 打印分类");
		long unitId = insertUnit("S34_PRINT_UNIT_" + SEQUENCE.incrementAndGet(), "034 打印单位");
		long materialId = insertMaterial("S34_PRINT_MAT_" + SEQUENCE.incrementAndGet(), "034 打印物料",
				categoryId, unitId);
		String orderNo = "S34_SO_" + SEQUENCE.incrementAndGet();
		long orderId = insertSalesOrder(customerId, orderNo);
		insertSalesOrderLine(orderId, 1, materialId, unitId, "3.000000", "12.500000");

		JsonNode salesOrderTemplates = data(get(admin, "/api/admin/print-templates?objectType=SALES_ORDER"));
		assertThat(salesOrderTemplates.toString()).contains("SALES_ORDER_V1");
		assertThat(salesOrderTemplates.toString()).doesNotContain("ACCOUNTING_VOUCHER_V1");
		JsonNode preview = data(get(admin, "/api/admin/print-previews?objectType=SALES_ORDER&objectId="
				+ orderId + "&templateCode=SALES_ORDER_V1"));
		assertThat(preview.toString()).contains(orderNo, "034 打印客户", "034 打印物料", "明细");

		JsonNode task = data(postWithIdempotency(admin, "/api/admin/print-tasks",
				Map.of("objectType", "SALES_ORDER", "objectId", orderId, "templateCode", "SALES_ORDER_V1"),
				"fixed-print-" + orderId));
		long taskId = task.get("id").longValue();
		assertThat(task.get("taskType").asText()).isEqualTo("FIXED_DOCUMENT_PRINT");
		assertThat(task.get("objectType").asText()).isEqualTo("SALES_ORDER");
		assertThat(task.get("objectId").longValue()).isEqualTo(orderId);
		assertThat(this.objectMapper.readTree(printPayload(taskId)).get("templateVersion").intValue()).isOne();

		JsonNode succeeded = processTaskUntilStatus(admin, taskId, "SUCCEEDED", 8);
		assertThat(succeeded.get("status").asText()).isEqualTo("SUCCEEDED");
		ResponseEntity<byte[]> downloaded = downloadBytes(admin, "/api/admin/document-tasks/" + taskId + "/download");
		assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(downloaded.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
		JsonNode filteredTasks = data(get(admin, "/api/admin/document-tasks?taskType=FIXED_DOCUMENT_PRINT&status=SUCCEEDED"
				+ "&objectKeyword=" + orderNo + "&createdByKeyword=admin&pageSize=50"));
		assertThat(filteredTasks.get("total").longValue()).isOne();
		assertThat(filteredTasks.get("items").get(0).get("id").longValue()).isEqualTo(taskId);

		AuthenticatedSession printOnly = createUserAndLogin("stage034-print-only", "S34_PRINT_ONLY",
				List.of("platform:document-task:view-all", "platform:print:generate"));
		assertError(get(printOnly, "/api/admin/document-tasks/" + taskId + "/download"), HttpStatus.FORBIDDEN,
				"AUTH_FORBIDDEN");
	}

	@Test
	void fixedPrintTemplatesUseDomainFactsAndStatusMatrix() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		List<PrintCase> cases = fixedPrintCases();
		for (PrintCase printCase : cases) {
			JsonNode preview = data(get(admin, "/api/admin/print-previews?objectType=" + printCase.objectType()
					+ "&objectId=" + printCase.objectId() + "&templateCode=" + printCase.templateCode()));
			String text = preview.toString();
			assertThat(text).contains("单据抬头", "业务明细", "业务汇总");
			assertThat(text).contains(printCase.expectedSnippets());
		}
		long draftOrderId = insertSalesOrderWithStatus(insertCustomer("S34_PRINT_DRAFT_CUS_"
				+ SEQUENCE.incrementAndGet(), "034 禁止草稿客户"), "S34_PRINT_DRAFT_SO_" + SEQUENCE.incrementAndGet(),
				"DRAFT");
		assertError(get(admin, "/api/admin/print-previews?objectType=SALES_ORDER&objectId=" + draftOrderId
				+ "&templateCode=SALES_ORDER_V1"), HttpStatus.CONFLICT, "DOCUMENT_TASK_STATUS_INVALID");
	}

	@Test
	void accountingVoucherFixedPrintMasksAmountAndSourceAcrossPreviewDownloadAndBatchPrint() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long ledgerId = ensureLedger();
		long periodId = ensureOpenPeriod(ledgerId);
		long debitAccountId = insertGlAccount(ledgerId, "S34MD" + SEQUENCE.incrementAndGet(), "034 保密借方科目",
				"ASSET", "DEBIT");
		long creditAccountId = insertGlAccount(ledgerId, "S34MC" + SEQUENCE.incrementAndGet(), "034 保密贷方科目",
				"LIABILITY", "CREDIT");
		String draftNo = "S34_MASK_VCH_" + SEQUENCE.incrementAndGet();
		String sourceNo = "S34_SECRET_SRC_" + SEQUENCE.incrementAndGet();
		String secretAmount = "9876.54";
		long voucherId = insertGlVoucher(ledgerId, periodId, draftNo, "DRAFT");
		this.jdbcTemplate.update("""
				update gl_voucher
				set source_type = 'FIN_VOUCHER_DRAFT', source_id = 340034, source_no = ?,
				    source_original_type = 'SALES_INVOICE', source_original_id = 340035,
				    source_original_no = ?, debit_total = ?::numeric, credit_total = ?::numeric,
				    version = version + 1
				where id = ?
				""", sourceNo, sourceNo, secretAmount, secretAmount, voucherId);
		insertGlVoucherLine(voucherId, 1, debitAccountId, "034 保密借方摘要", "034 保密借方科目", "ASSET",
				"DEBIT", secretAmount, "0.00");
		insertGlVoucherLine(voucherId, 2, creditAccountId, "034 保密贷方摘要", "034 保密贷方科目", "LIABILITY",
				"CREDIT", "0.00", secretAmount);

		JsonNode fullPreview = data(get(admin, "/api/admin/print-previews?objectType=ACCOUNTING_VOUCHER&objectId="
				+ voucherId + "&templateCode=ACCOUNTING_VOUCHER_V1"));
		assertThat(fullPreview.toString()).contains(sourceNo, secretAmount);

		AuthenticatedSession restricted = createUserAndLogin("stage034-gl-print-masked-",
				"S34_GL_PRINT_MASK", List.of("platform:print:generate", "gl:voucher:view",
						"platform:document-task:view", "platform:document-task:download", "platform:batch-tool:view",
						"platform:batch-tool:preview", "platform:batch-tool:execute"));
		JsonNode maskedPreview = data(get(restricted,
				"/api/admin/print-previews?objectType=ACCOUNTING_VOUCHER&objectId=" + voucherId
						+ "&templateCode=ACCOUNTING_VOUCHER_V1"));
		assertThat(maskedPreview.toString()).contains(draftNo, "034 保密借方科目", "034 保密贷方科目");
		assertThat(maskedPreview.toString()).doesNotContain(sourceNo, secretAmount);

		JsonNode maskedTask = data(postWithIdempotency(restricted, "/api/admin/print-tasks",
				Map.of("objectType", "ACCOUNTING_VOUCHER", "objectId", voucherId,
						"templateCode", "ACCOUNTING_VOUCHER_V1"),
				"masked-gl-voucher-print-" + voucherId));
		long maskedTaskId = maskedTask.get("id").longValue();
		processTaskUntilStatus(restricted, maskedTaskId, "SUCCEEDED", 8);
		ResponseEntity<byte[]> maskedDownload = downloadBytes(restricted, "/api/admin/document-tasks/" + maskedTaskId
				+ "/download");
		assertThat(maskedDownload.getStatusCode()).isEqualTo(HttpStatus.OK);
		String maskedPdf = pdfText(maskedDownload.getBody());
		assertThat(maskedPdf).contains(draftNo, "034 保密借方科目", "034 保密贷方科目");
		assertThat(maskedPdf).doesNotContain(sourceNo, secretAmount);

		JsonNode fullTask = data(postWithIdempotency(admin, "/api/admin/print-tasks",
				Map.of("objectType", "ACCOUNTING_VOUCHER", "objectId", voucherId,
						"templateCode", "ACCOUNTING_VOUCHER_V1"),
				"full-gl-voucher-print-" + voucherId));
		long fullTaskId = fullTask.get("id").longValue();
		processTaskUntilStatus(admin, fullTaskId, "SUCCEEDED", 8);
		AuthenticatedSession restrictedViewAll = createUserAndLogin("stage034-gl-print-view-all-",
				"S34_GL_PRINT_VIEW_ALL", List.of("platform:document-task:view-all", "platform:document-task:download",
						"platform:print:generate", "gl:voucher:view"));
		assertError(get(restrictedViewAll, "/api/admin/document-tasks/" + fullTaskId + "/download"),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		JsonNode batchPreview = data(post(restricted,
				"/api/admin/platform/batch-tools/FIXED_DOCUMENT_BATCH_PRINT_V1/preview",
				fixedPrintBatchPayload("ACCOUNTING_VOUCHER", "ACCOUNTING_VOUCHER_V1",
						List.of(Map.of("targetObjectId", voucherId, "version", voucherVersion(voucherId))),
						"masked-gl-voucher-batch-" + voucherId)));
		JsonNode batchExecuted = data(post(restricted, "/api/admin/platform/batch-operations/"
				+ batchPreview.get("id").longValue() + "/execute",
				Map.of("version", batchPreview.get("version").longValue(),
						"idempotencyKey", "masked-gl-voucher-batch-execute-" + voucherId)));
		assertThat(batchExecuted.get("status").asText()).isEqualTo("SUCCEEDED");
		Long batchTaskId = batchDocumentTaskId(batchPreview.get("id").longValue());
		assertThat(batchTaskId).isNotNull();
		processTaskUntilStatus(restricted, batchTaskId, "SUCCEEDED", 8);
		String batchPdf = pdfText(downloadBytes(restricted, "/api/admin/document-tasks/" + batchTaskId
				+ "/download").getBody());
		assertThat(batchPdf).contains(draftNo, "034 保密借方科目", "034 保密贷方科目");
		assertThat(batchPdf).doesNotContain(sourceNo, secretAmount);
	}

	private List<PrintCase> fixedPrintCases() {
		long categoryId = insertMaterialCategory("S34_PRINT_ALL_CAT_" + SEQUENCE.incrementAndGet(), "034 全模板分类");
		long unitId = insertUnit("S34_PRINT_ALL_UNIT_" + SEQUENCE.incrementAndGet(), "034 全模板单位");
		long productId = insertMaterial("S34_PRINT_PRODUCT_" + SEQUENCE.incrementAndGet(), "034 全模板成品",
				categoryId, unitId);
		long componentId = insertMaterial("S34_PRINT_COMPONENT_" + SEQUENCE.incrementAndGet(), "034 全模板组件",
				categoryId, unitId);
		long customerId = insertCustomer("S34_PRINT_ALL_CUS_" + SEQUENCE.incrementAndGet(), "034 全模板客户");
		long supplierId = insertSupplier("S34_PRINT_ALL_SUP_" + SEQUENCE.incrementAndGet(), "034 全模板供应商");
		long sourceWarehouseId = insertWarehouse("S34_PRINT_SRC_WH_" + SEQUENCE.incrementAndGet(), "034 调出仓");
		long targetWarehouseId = insertWarehouse("S34_PRINT_DST_WH_" + SEQUENCE.incrementAndGet(), "034 调入仓");

		String salesOrderNo = "S34_PRINT_SO_" + SEQUENCE.incrementAndGet();
		long salesOrderId = insertSalesOrder(customerId, salesOrderNo);
		long salesOrderLineId = insertSalesOrderLine(salesOrderId, 1, productId, unitId, "5.000000", "20.000000");
		String shipmentNo = "S34_PRINT_SHIP_" + SEQUENCE.incrementAndGet();
		long shipmentId = insertSalesShipment(salesOrderId, customerId, sourceWarehouseId, shipmentNo, "POSTED");
		long shipmentLineId = insertSalesShipmentLine(shipmentId, salesOrderLineId, productId, unitId,
				"2.000000", "20.000000");

		String purchaseOrderNo = "S34_PRINT_PO_" + SEQUENCE.incrementAndGet();
		long purchaseOrderId = insertPurchaseOrder(supplierId, purchaseOrderNo);
		long purchaseOrderLineId = insertPurchaseOrderLine(purchaseOrderId, componentId, unitId, "6.000000",
				"11.000000");
		String purchaseReceiptNo = "S34_PRINT_PR_" + SEQUENCE.incrementAndGet();
		long purchaseReceiptId = insertPurchaseReceipt(purchaseOrderId, supplierId, sourceWarehouseId,
				purchaseReceiptNo, "POSTED");
		long purchaseReceiptLineId = insertPurchaseReceiptLine(purchaseReceiptId, purchaseOrderLineId, componentId,
				unitId, "3.000000");

		String transferNo = "S34_PRINT_TR_" + SEQUENCE.incrementAndGet();
		long transferId = insertWarehouseTransfer(transferNo, "CANCELLED", sourceWarehouseId, targetWarehouseId,
				componentId, unitId, "4.000000");

		String bomCode = "S34_PRINT_BOM_" + SEQUENCE.incrementAndGet();
		long bomId = insertBom(productId, unitId, bomCode);
		long bomItemId = insertBomItem(bomId, 1, componentId, unitId, "1.500000");
		String workOrderNo = "S34_PRINT_WO_" + SEQUENCE.incrementAndGet();
		long workOrderId = insertWorkOrder(productId, bomId, sourceWarehouseId, targetWarehouseId, workOrderNo,
				"RELEASED");
		long workOrderMaterialId = insertWorkOrderMaterial(workOrderId, bomItemId, componentId, unitId,
				"3.000000");
		String issueNo = "S34_PRINT_ISS_" + SEQUENCE.incrementAndGet();
		long issueId = insertMaterialIssue(workOrderId, issueNo, "POSTED");
		insertMaterialIssueLine(issueId, workOrderMaterialId, sourceWarehouseId, componentId, unitId, "1.250000");
		String completionNo = "S34_PRINT_REC_" + SEQUENCE.incrementAndGet();
		long completionId = insertCompletionReceipt(workOrderId, targetWarehouseId, completionNo, "POSTED",
				"1.000000");

		String salesInvoiceNo = "S34_PRINT_SINV_" + SEQUENCE.incrementAndGet();
		long salesInvoiceId = insertSalesInvoice(customerId, shipmentId, shipmentNo, salesInvoiceNo, "CONFIRMED");
		insertSalesInvoiceLine(salesInvoiceId, shipmentLineId, salesOrderId, salesOrderLineId, productId, unitId,
				"2.000000", "40.00");
		String purchaseInvoiceNo = "S34_PRINT_PINV_" + SEQUENCE.incrementAndGet();
		long purchaseInvoiceId = insertPurchaseInvoice(supplierId, purchaseReceiptId, purchaseReceiptNo,
				purchaseInvoiceNo, "CONFIRMED");
		insertPurchaseInvoiceLine(purchaseInvoiceId, purchaseReceiptLineId, purchaseOrderId, purchaseOrderLineId,
				componentId, unitId, "3.000000", "33.00");

		long ledgerId = ensureLedger();
		long periodId = ensureOpenPeriod(ledgerId);
		long debitAccountId = insertGlAccount(ledgerId, "S34D" + SEQUENCE.incrementAndGet(), "034 借方科目",
				"ASSET", "DEBIT");
		long creditAccountId = insertGlAccount(ledgerId, "S34C" + SEQUENCE.incrementAndGet(), "034 贷方科目",
				"LIABILITY", "CREDIT");
		String voucherDraftNo = "S34_PRINT_VCH_" + SEQUENCE.incrementAndGet();
		long voucherId = insertGlVoucher(ledgerId, periodId, voucherDraftNo, "DRAFT");
		insertGlVoucherLine(voucherId, 1, debitAccountId, "S34 借方摘要", "034 借方科目", "ASSET", "DEBIT",
				"100.00", "0.00");
		insertGlVoucherLine(voucherId, 2, creditAccountId, "S34 贷方摘要", "034 贷方科目", "LIABILITY", "CREDIT",
				"0.00", "100.00");

		return List.of(
				printCase("SALES_SHIPMENT", "SALES_SHIPMENT_V1", shipmentId, shipmentNo, "034 全模板客户",
						"034 全模板成品", "2"),
				printCase("PROCUREMENT_RECEIPT", "PROCUREMENT_RECEIPT_V1", purchaseReceiptId, purchaseReceiptNo,
						"034 全模板供应商", "034 全模板组件", "3"),
				printCase("INVENTORY_TRANSFER", "INVENTORY_TRANSFER_V1", transferId, transferNo, "034 调出仓",
						"034 调入仓", "034 全模板组件"),
				printCase("PRODUCTION_WORK_ORDER", "PRODUCTION_WORK_ORDER_V1", workOrderId, workOrderNo,
						"034 全模板成品", "034 全模板组件", "3"),
				printCase("PRODUCTION_MATERIAL_ISSUE", "PRODUCTION_MATERIAL_ISSUE_V1", issueId, issueNo,
						workOrderNo, "034 全模板组件", "1.25"),
				printCase("PRODUCTION_COMPLETION_RECEIPT", "PRODUCTION_COMPLETION_RECEIPT_V1", completionId,
						completionNo, workOrderNo, "034 调入仓", "1"),
				printCase("SALES_INVOICE", "SALES_INVOICE_V1", salesInvoiceId, salesInvoiceNo, "034 全模板客户",
						shipmentNo, "40"),
				printCase("PURCHASE_INVOICE", "PURCHASE_INVOICE_V1", purchaseInvoiceId, purchaseInvoiceNo,
						"034 全模板供应商", purchaseReceiptNo, "33"),
				printCase("ACCOUNTING_VOUCHER", "ACCOUNTING_VOUCHER_V1", voucherId, voucherDraftNo, "034 借方科目",
						"034 贷方科目", "100"));
	}

	private PrintCase printCase(String objectType, String templateCode, long objectId, String... expectedSnippets) {
		return new PrintCase(objectType, templateCode, objectId, expectedSnippets);
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

	private Map<String, Object> batchPayload(String targetStatus, List<Map<String, Object>> targets,
			String idempotencyKey) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("actionCode", "STATUS_CHANGE");
		payload.put("targetStatus", targetStatus);
		payload.put("reason", "034 批量状态治理");
		payload.put("targets", targets);
		payload.put("idempotencyKey", idempotencyKey);
		return payload;
	}

	private Map<String, Object> target(long id) {
		return Map.of("targetObjectId", id, "version", customerVersion(id));
	}

	private Map<String, Object> salesOrderTarget(long id) {
		return Map.of("targetObjectId", id, "version", salesOrderVersion(id));
	}

	private Map<String, Object> fixedPrintBatchPayload(String objectType, String templateCode,
			List<Map<String, Object>> targets, String idempotencyKey) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("actionCode", "BATCH_PRINT");
		payload.put("objectType", objectType);
		payload.put("templateCode", templateCode);
		payload.put("reason", "034 固定单据批量打印");
		payload.put("targets", targets);
		payload.put("idempotencyKey", idempotencyKey);
		return payload;
	}

	private byte[] customerWorkbook(String code, String name) throws Exception {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("customers");
			Row header = sheet.createRow(0);
			String[] headers = { "code", "name", "contactName", "contactPhone", "status", "remark" };
			for (int i = 0; i < headers.length; i++) {
				header.createCell(i).setCellValue(headers[i]);
			}
			Row row = sheet.createRow(1);
			String[] values = { code, name, "034 联系人", "13900000000", "ENABLED", "034 历史导入" };
			for (int i = 0; i < values.length; i++) {
				row.createCell(i).setCellValue(values[i]);
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private byte[] materialWorkbook(List<String[]> rows) throws Exception {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("materials");
			Row header = sheet.createRow(0);
			String[] headers = { "code", "name", "specification", "materialType", "sourceType", "trackingMethod",
					"categoryCode", "unitCode", "status", "costCategory", "inventoryValuationCategory",
					"inventoryValueEnabled", "projectCostEnabled", "costRemark", "remark" };
			for (int i = 0; i < headers.length; i++) {
				header.createCell(i).setCellValue(headers[i]);
			}
			for (int i = 0; i < rows.size(); i++) {
				Row row = sheet.createRow(i + 1);
				String[] values = rows.get(i);
				for (int column = 0; column < values.length; column++) {
					row.createCell(column).setCellValue(values[column]);
				}
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private String[] materialRow(String code, String name, String categoryCode, String unitCode, String costCategory,
			String inventoryValuationCategory, String inventoryValueEnabled, String projectCostEnabled) {
		return new String[] { code, name, "034 规格", "RAW_MATERIAL", "PURCHASED", "NONE", categoryCode, unitCode,
				"ENABLED", costCategory, inventoryValuationCategory, inventoryValueEnabled, projectCostEnabled,
				"034 成本备注", "034 历史导入" };
	}

	private byte[] bomWorkbook(String bomCode, String parentMaterialCode, String versionCode, String baseUnitCode,
			List<String[]> itemRows) throws Exception {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet bom = workbook.createSheet("bom");
			Row bomHeader = bom.createRow(0);
			String[] bomHeaders = { "mode", "bomId", "version", "bomCode", "parentMaterialCode", "versionCode",
					"name", "baseQuantity", "baseUnit", "effectiveFrom", "effectiveTo", "remark" };
			for (int i = 0; i < bomHeaders.length; i++) {
				bomHeader.createCell(i).setCellValue(bomHeaders[i]);
			}
			Row bomRow = bom.createRow(1);
			String[] bomValues = { "CREATE", "", "", bomCode, parentMaterialCode, versionCode, "034 历史 BOM",
					"1", baseUnitCode, LocalDate.now().toString(), "", "034 BOM 历史导入" };
			for (int i = 0; i < bomValues.length; i++) {
				bomRow.createCell(i).setCellValue(bomValues[i]);
			}

			Sheet items = workbook.createSheet("items");
			Row itemHeader = items.createRow(0);
			String[] itemHeaders = { "lineNo", "childMaterialCode", "businessUnit", "businessQuantity", "lossRate",
					"warehouse", "remark" };
			for (int i = 0; i < itemHeaders.length; i++) {
				itemHeader.createCell(i).setCellValue(itemHeaders[i]);
			}
			for (int i = 0; i < itemRows.size(); i++) {
				Row row = items.createRow(i + 1);
				String[] values = itemRows.get(i);
				for (int column = 0; column < values.length; column++) {
					row.createCell(column).setCellValue(values[column]);
				}
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private String[] bomItemRow(String lineNo, String childMaterialCode, String businessUnit, String businessQuantity,
			String lossRate, String remark) {
		return new String[] { lineNo, childMaterialCode, businessUnit, businessQuantity, lossRate, "", remark };
	}

	private long insertCustomer(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertSalesOrder(long customerId, String orderNo) {
		return insertSalesOrderWithStatus(customerId, orderNo, "CONFIRMED");
	}

	private long insertSalesOrderWithStatus(long customerId, String orderNo, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, '034 固定打印', 'test', now(), 'test', now())
				returning id
				""", Long.class, orderNo, customerId, LocalDate.now(), LocalDate.now().plusDays(7), status);
	}

	private long insertSalesOrderLine(long orderId, int lineNo, long materialId, long unitId, String quantity,
			String unitPrice) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue).setScale(2, java.math.RoundingMode.HALF_UP);
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, reservation_warehouse_id, remark, price_source_type, currency, tax_rate,
					tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_amount,
					tax_included_amount, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, 0, ?, current_date + interval '7 day', null,
					'034 固定打印明细', 'MANUAL', 'CNY', 0, ?, ?, ?, 0, ?, now(), now())
				returning id
				""", Long.class, orderId, lineNo, materialId, unitId, quantityValue, unitPriceValue,
				unitPriceValue, unitPriceValue, amount, amount);
	}

	private long insertSupplier(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertWarehouse(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'RAW_MATERIAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertPurchaseOrder(long supplierId, String orderNo) {
		return this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 'CONFIRMED', '034 固定打印采购订单', 'test', now(), 'test', now())
				returning id
				""", Long.class, orderNo, supplierId, LocalDate.now(), LocalDate.now().plusDays(7));
	}

	private long insertPurchaseOrderLine(long orderId, long materialId, long unitId, String quantity,
			String unitPrice) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue).setScale(2, java.math.RoundingMode.HALF_UP);
		return this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					expected_arrival_date, remark, price_source_type, tax_rate, tax_excluded_unit_price,
					tax_included_unit_price, tax_excluded_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, 0, ?, ?, '034 固定打印采购明细', 'MANUAL', 0,
					?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, materialId, unitId, quantityValue, unitPriceValue,
				LocalDate.now().plusDays(7), unitPriceValue, unitPriceValue, amount, amount);
	}

	private long insertPurchaseReceipt(long orderId, long supplierId, long warehouseId, String receiptNo,
			String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, '034 固定打印采购入库', 'test', now(), 'test', now(),
					case when ? = 'POSTED' then 'test' else null end,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, receiptNo, orderId, supplierId, warehouseId, LocalDate.now(), status, status,
				status);
	}

	private long insertPurchaseReceiptLine(long receiptId, long orderLineId, long materialId, long unitId,
			String quantity) {
		return this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, 0, ?, '034 固定打印采购入库明细', now(), now())
				returning id
				""", Long.class, receiptId, orderLineId, materialId, unitId, new BigDecimal(quantity),
				new BigDecimal(quantity), new BigDecimal(quantity), new BigDecimal(quantity));
	}

	private long insertSalesShipment(long orderId, long customerId, long warehouseId, String shipmentNo,
			String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, '034 固定打印销售出库', 'test', now(), 'test', now(),
					case when ? = 'POSTED' then 'test' else null end,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, shipmentNo, orderId, customerId, warehouseId, LocalDate.now(), status, status,
				status);
	}

	private long insertSalesShipmentLine(long shipmentId, long orderLineId, long materialId, long unitId,
			String quantity, String unitPrice) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue).setScale(2, java.math.RoundingMode.HALF_UP);
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, tax_excluded_unit_price, tax_included_unit_price,
					tax_excluded_amount, tax_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, 0, ?, '034 固定打印销售出库明细',
					?, ?, ?, 0, ?, now(), now())
				returning id
				""", Long.class, shipmentId, orderLineId, materialId, unitId, quantityValue, quantityValue,
				quantityValue, quantityValue, unitPriceValue, unitPriceValue, amount, amount);
	}

	private long insertWarehouseTransfer(String transferNo, String status, long sourceWarehouseId,
			long targetWarehouseId, long materialId, long unitId, String quantity) {
		long transferId = this.jdbcTemplate.queryForObject("""
				insert into inv_warehouse_transfer (
					transfer_no, business_date, reason, status, idempotency_key, posted_at, cancelled_at,
					created_by_user_id, created_by_username, updated_by_username
				)
				select ?, ?, '034 固定打印调拨', ?, ?, case when ? = 'POSTED' then now() else null end,
				       case when ? = 'CANCELLED' then now() else null end, id, username, username
				from sys_user
				where username = 'admin'
				returning id
				""", Long.class, transferNo, LocalDate.now(), status, "transfer-" + transferNo, status, status);
		this.jdbcTemplate.update("""
				insert into inv_warehouse_transfer_line (
					transfer_id, line_no, source_warehouse_id, target_warehouse_id, ownership_type,
					material_id, unit_id, quality_status, quantity
				)
				values (?, 1, ?, ?, 'PUBLIC', ?, ?, 'QUALIFIED', ?)
				""", transferId, sourceWarehouseId, targetWarehouseId, materialId, unitId, new BigDecimal(quantity));
		return transferId;
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

	private long insertUnit(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 2, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertMaterial(String code, String name, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (
					code, name, specification, material_type, source_type, tracking_method, category_id, unit_id,
					status, remark, cost_category, inventory_valuation_category, inventory_value_enabled,
					project_cost_enabled, cost_remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, '034 规格', 'RAW_MATERIAL', 'PURCHASED', 'NONE', ?, ?, 'ENABLED', '034 测试物料',
					'DIRECT_MATERIAL', 'VALUATED_MATERIAL', true, true, '034 成本', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, categoryId, unitId);
	}

	private long insertUnitConversion(long materialId, long baseUnitId, long businessUnitId, String conversionRate) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_unit_conversion (
					material_id, base_unit_id, business_unit_id, conversion_rate, quantity_scale, rounding_mode,
					effective_from, effective_to, status, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 6, 'HALF_UP', ?, null, 'ENABLED', '034 BOM 换算', 'test', now(), 'test', now())
				returning id
				""", Long.class, materialId, baseUnitId, businessUnitId, new BigDecimal(conversionRate),
				LocalDate.now().minusDays(1));
	}

	private long insertBom(long parentMaterialId, long unitId, String bomCode) {
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (
					bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id, status,
					effective_from, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'V1', '034 固定打印 BOM', 1, ?, 'DRAFT', ?, '034 固定打印 BOM',
					'test', now(), 'test', now())
				returning id
				""", Long.class, bomCode, parentMaterialId, unitId, LocalDate.now());
	}

	private long insertBomItem(long bomId, int lineNo, long childMaterialId, long unitId, String quantity) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_bom_item (
					bom_id, line_no, child_material_id, unit_id, quantity, business_unit_id,
					business_quantity, base_unit_id, base_quantity, quantity_basis, loss_rate, remark,
					created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'BASE_UNIT', 0, '034 固定打印 BOM 明细', now(), now())
				returning id
				""", Long.class, bomId, lineNo, childMaterialId, unitId, quantityValue, unitId, quantityValue,
				unitId, quantityValue);
	}

	private long insertWorkOrder(long productMaterialId, long bomId, long issueWarehouseId, long receiptWarehouseId,
			String workOrderNo, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order (
					work_order_no, product_material_id, bom_id, planned_quantity, reported_quantity,
					qualified_quantity, defective_quantity, received_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_start_date, planned_finish_date, status, remark,
					created_by, created_at, updated_by, updated_at, released_by, released_at
				)
				values (?, ?, ?, 10, 0, 0, 0, 0, ?, ?, ?, ?, ?, '034 固定打印生产工单',
					'test', now(), 'test', now(),
					case when ? in ('RELEASED', 'IN_PROGRESS', 'COMPLETED') then 'test' else null end,
					case when ? in ('RELEASED', 'IN_PROGRESS', 'COMPLETED') then now() else null end)
				returning id
				""", Long.class, workOrderNo, productMaterialId, bomId, issueWarehouseId, receiptWarehouseId,
				LocalDate.now(), LocalDate.now().plusDays(3), status, status, status);
	}

	private long insertWorkOrderMaterial(long workOrderId, long bomItemId, long materialId, long unitId,
			String quantity) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order_material (
					work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity,
					issued_quantity, business_unit_id, business_quantity, base_unit_id,
					base_required_quantity, quantity_basis, loss_rate, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, ?, ?, 'BASE_UNIT', 0,
					'034 固定打印工单用料', now(), now())
				returning id
				""", Long.class, workOrderId, bomItemId, materialId, unitId, quantityValue, unitId,
				quantityValue, unitId, quantityValue);
	}

	private long insertMaterialIssue(long workOrderId, String issueNo, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_material_issue (
					issue_no, work_order_id, status, business_date, reason, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, '034 固定打印领料', '034 固定打印领料',
					'test', now(), 'test', now(),
					case when ? = 'POSTED' then 'test' else null end,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, issueNo, workOrderId, status, LocalDate.now(), status, status);
	}

	private long insertMaterialIssueLine(long issueId, long workOrderMaterialId, long warehouseId, long materialId,
			long unitId, String quantity) {
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_material_issue_line (
					issue_id, work_order_material_id, line_no, warehouse_id, material_id, unit_id,
					quantity, before_quantity, after_quantity, remark, created_at, updated_at
				)
				values (?, ?, 1, ?, ?, ?, ?, 10, 8.75, '034 固定打印领料明细', now(), now())
				returning id
				""", Long.class, issueId, workOrderMaterialId, warehouseId, materialId, unitId,
				new BigDecimal(quantity));
	}

	private long insertCompletionReceipt(long workOrderId, long warehouseId, String receiptNo, String status,
			String quantity) {
		return this.jdbcTemplate.queryForObject("""
				insert into mfg_completion_receipt (
					receipt_no, work_order_id, status, business_date, receipt_warehouse_id, quantity,
					before_quantity, after_quantity, remark, created_by, created_at, updated_by, updated_at,
					posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, 0, ?, '034 固定打印完工入库',
					'test', now(), 'test', now(),
					case when ? = 'POSTED' then 'test' else null end,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, receiptNo, workOrderId, status, LocalDate.now(), warehouseId,
				new BigDecimal(quantity), new BigDecimal(quantity), status, status);
	}

	private long insertSalesInvoice(long customerId, long shipmentId, String shipmentNo, String invoiceNo,
			String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_sales_invoice (
					invoice_no, customer_id, ownership_type, source_type, source_id, source_no,
					invoice_date, due_date, invoice_type, currency, tax_excluded_amount, tax_amount,
					tax_included_amount, status, party_snapshot, source_snapshot, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'PUBLIC', 'SALES_SHIPMENT', ?, ?, ?, ?, 'GENERAL_VAT', 'CNY',
					40.00, 0.00, 40.00, ?, '{}'::jsonb, '{}'::jsonb, '034 固定打印销售发票',
					'test', now(), 'test', now(),
					case when ? = 'CONFIRMED' then 'test' else null end,
					case when ? = 'CONFIRMED' then now() else null end)
				returning id
				""", Long.class, invoiceNo, customerId, shipmentId, shipmentNo, LocalDate.now(),
				LocalDate.now().plusDays(30), status, status, status);
	}

	private long insertSalesInvoiceLine(long invoiceId, long shipmentLineId, long salesOrderId,
			long salesOrderLineId, long materialId, long unitId, String quantity, String amount) {
		BigDecimal amountValue = new BigDecimal(amount);
		return this.jdbcTemplate.queryForObject("""
				insert into fin_sales_invoice_line (
					sales_invoice_id, line_no, source_line_id, sales_order_id, sales_order_line_id,
					material_id, unit_id, quantity, tax_rate, tax_excluded_unit_price,
					tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount,
					source_snapshot, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 0.00, ?, '{}'::jsonb, now(), now())
				returning id
				""", Long.class, invoiceId, shipmentLineId, salesOrderId, salesOrderLineId, materialId, unitId,
				new BigDecimal(quantity), amountValue, amountValue, amountValue, amountValue);
	}

	private long insertPurchaseInvoice(long supplierId, long receiptId, String receiptNo, String invoiceNo,
			String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_purchase_invoice (
					invoice_no, supplier_id, settlement_kind, ownership_type, source_type, source_id,
					source_no, invoice_date, due_date, invoice_type, currency, match_status,
					tax_excluded_amount, tax_amount, tax_included_amount, status, party_snapshot,
					source_snapshot, remark, created_by, created_at, updated_by, updated_at, matched_by,
					matched_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'STANDARD_PURCHASE', 'PUBLIC', 'PURCHASE_RECEIPT', ?, ?, ?, ?,
					'GENERAL_VAT', 'CNY', 'MATCHED', 33.00, 0.00, 33.00, ?, '{}'::jsonb, '{}'::jsonb,
					'034 固定打印采购发票', 'test', now(), 'test', now(), 'test', now(),
					case when ? = 'CONFIRMED' then 'test' else null end,
					case when ? = 'CONFIRMED' then now() else null end)
				returning id
				""", Long.class, invoiceNo, supplierId, receiptId, receiptNo, LocalDate.now(),
				LocalDate.now().plusDays(30), status, status, status);
	}

	private long insertPurchaseInvoiceLine(long invoiceId, long receiptLineId, long purchaseOrderId,
			long purchaseOrderLineId, long materialId, long unitId, String quantity, String amount) {
		BigDecimal amountValue = new BigDecimal(amount);
		return this.jdbcTemplate.queryForObject("""
				insert into fin_purchase_invoice_line (
					purchase_invoice_id, line_no, source_line_id, purchase_order_id, purchase_order_line_id,
					material_id, unit_id, quantity, tax_rate, tax_excluded_unit_price,
					tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount,
					match_status, source_snapshot, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 0.00, ?, 'MATCHED', '{}'::jsonb, now(), now())
				returning id
				""", Long.class, invoiceId, receiptLineId, purchaseOrderId, purchaseOrderLineId, materialId,
				unitId, new BigDecimal(quantity), amountValue, amountValue, amountValue, amountValue);
	}

	private long ensureLedger() {
		return this.jdbcTemplate.query("select id from gl_ledger where code = 'MAIN'",
				(rs, rowNum) -> rs.getLong("id")).stream().findFirst().orElseGet(() -> this.jdbcTemplate.queryForObject("""
						insert into gl_ledger (
							code, name, currency, initialized, created_by, created_at, updated_by, updated_at
						)
						values ('MAIN', '034 固定打印主账簿', 'CNY', true, 'test', now(), 'test', now())
						returning id
						""", Long.class));
	}

	private long ensureOpenPeriod(long ledgerId) {
		return this.jdbcTemplate.query("select id from gl_accounting_period where ledger_id = ? order by id limit 1",
				(rs, rowNum) -> rs.getLong("id"), ledgerId).stream().findFirst()
			.orElseGet(() -> this.jdbcTemplate.queryForObject("""
					insert into gl_accounting_period (
						ledger_id, period_code, start_date, end_date, status,
						created_by, created_at, updated_by, updated_at
					)
					values (?, '2099-12', date '2099-12-01', date '2099-12-31', 'OPEN',
						'test', now(), 'test', now())
					returning id
					""", Long.class, ledgerId));
	}

	private long insertGlAccount(long ledgerId, String code, String name, String category, String balanceDirection) {
		return this.jdbcTemplate.queryForObject("""
				insert into gl_account (
					ledger_id, code, name, category, balance_direction, level_no, is_leaf, postable,
					enabled, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, 1, true, true, true, 'test', now(), 'test', now())
				returning id
				""", Long.class, ledgerId, code, name, category, balanceDirection);
	}

	private long insertGlVoucher(long ledgerId, long periodId, String draftNo, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into gl_voucher (
					ledger_id, accounting_period_id, draft_no, voucher_type, voucher_date, status,
					summary, source_type, currency, debit_total, credit_total, created_by, created_at,
					updated_by, updated_at
				)
				values (?, ?, ?, 'GENERAL', ?, ?, '034 固定打印凭证摘要', 'MANUAL', 'CNY',
					100.00, 100.00, 'test', now(), 'test', now())
				returning id
				""", Long.class, ledgerId, periodId, draftNo, LocalDate.now(), status);
	}

	private long insertGlVoucherLine(long voucherId, int lineNo, long accountId, String summary,
			String accountName, String accountCategory, String balanceDirection, String debitAmount,
			String creditAmount) {
		String accountCode = this.jdbcTemplate.queryForObject("select code from gl_account where id = ?",
				String.class, accountId);
		return this.jdbcTemplate.queryForObject("""
				insert into gl_voucher_line (
					voucher_id, line_no, summary, account_id, account_code, account_name,
					account_category, account_balance_direction, debit_amount, credit_amount, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
				returning id
				""", Long.class, voucherId, lineNo, summary, accountId, accountCode, accountName,
				accountCategory, balanceDirection, new BigDecimal(debitAmount), new BigDecimal(creditAmount));
	}

	private long customerCount(String code) {
		return this.jdbcTemplate.queryForObject("select count(*) from mst_customer where code = ?", Long.class, code);
	}

	private long materialCount(String code) {
		return this.jdbcTemplate.queryForObject("select count(*) from mst_material where code = ?", Long.class, code);
	}

	private String materialCostCategory(String code) {
		return this.jdbcTemplate.queryForObject("select cost_category from mst_material where code = ?", String.class,
				code);
	}

	private long bomCount(String bomCode) {
		return this.jdbcTemplate.queryForObject("select count(*) from mfg_bom where bom_code = ?", Long.class,
				bomCode);
	}

	private Map<String, Object> bomItemSnapshot(String bomCode, int lineNo) {
		return this.jdbcTemplate.queryForMap("""
				select i.child_material_id, i.unit_id, i.quantity, i.business_unit_id, i.business_quantity,
				       i.base_unit_id, i.base_quantity, i.conversion_id, i.conversion_rate_snapshot,
				       i.quantity_scale_snapshot, i.rounding_mode_snapshot, i.quantity_basis
				from mfg_bom_item i
				join mfg_bom b on b.id = i.bom_id
				where b.bom_code = ? and i.line_no = ?
				""", bomCode, lineNo);
	}

	private long number(Map<String, Object> row, String columnName) {
		return ((Number) row.get(columnName)).longValue();
	}

	private void assertDecimal(Map<String, Object> row, String columnName, String expected) {
		assertThat((BigDecimal) row.get(columnName)).isEqualByComparingTo(expected);
	}

	private void assertTaskError(AuthenticatedSession session, long taskId, int rowNo, String columnName,
			String errorCode) throws Exception {
		JsonNode errors = data(get(session, "/api/admin/document-tasks/" + taskId + "/errors?pageSize=50"));
		assertThat(errors.get("items").toString()).contains("\"rowNo\":" + rowNo,
				"\"columnName\":\"" + columnName + "\"", "\"errorCode\":\"" + errorCode + "\"");
	}

	private String historyImportBatchStatus(long taskId) {
		return this.jdbcTemplate.queryForObject("select status from platform_import_batch where task_id = ?",
				String.class, taskId);
	}

	private long customerVersion(long id) {
		return this.jdbcTemplate.queryForObject("select version from mst_customer where id = ?", Long.class, id);
	}

	private long salesOrderVersion(long id) {
		return this.jdbcTemplate.queryForObject("select version from sal_sales_order where id = ?", Long.class, id);
	}

	private String customerStatus(long id) {
		return this.jdbcTemplate.queryForObject("select status from mst_customer where id = ?", String.class, id);
	}

	private long fixedPrintTaskCount(long firstOrderId, long secondOrderId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_document_task
				where task_type = 'FIXED_DOCUMENT_PRINT'
				  and (request_payload ->> 'objectType') = 'SALES_ORDER'
				  and (request_payload ->> 'objectId')::bigint in (?, ?)
				""", Long.class, firstOrderId, secondOrderId);
	}

	private Long batchDocumentTaskId(long operationId) {
		return this.jdbcTemplate.queryForObject("""
				select document_task_id
				from platform_batch_operation
				where id = ?
				""", Long.class, operationId);
	}

	private String firstFixedPrintTaskPayload(long orderId) {
		return this.jdbcTemplate.queryForObject("""
				select request_payload::text
				from platform_document_task
				where task_type = 'FIXED_DOCUMENT_PRINT'
				  and (request_payload ->> 'objectType') = 'SALES_ORDER'
				  and (request_payload ->> 'objectId')::bigint = ?
				order by id
				limit 1
				""", String.class, orderId);
	}

	private String printPayload(long taskId) {
		return this.jdbcTemplate.queryForObject("select request_payload::text from platform_document_task where id = ?",
				String.class, taskId);
	}

	private ResponseEntity<String> uploadImport(AuthenticatedSession session, String path, String filename,
			byte[] content, String idempotencyKey) {
		org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(content) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		HttpHeaders headers = headers(session);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.add("Idempotency-Key", idempotencyKey);
		return this.restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> post(AuthenticatedSession session, String path, Object body) {
		return this.restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(session)),
				String.class);
	}

	private ResponseEntity<String> postWithIdempotency(AuthenticatedSession session, String path, Object body,
			String idempotencyKey) {
		HttpHeaders headers = headers(session);
		headers.add("Idempotency-Key", idempotencyKey);
		return this.restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> get(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers(session)), String.class);
	}

	private ResponseEntity<byte[]> downloadBytes(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers(session)),
				byte[].class);
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
				""", Long.class, rolePrefix + suffix, "034 测试角色" + suffix, "034 测试角色");
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

	private AuthenticatedSession login(String username, String password) {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				new HttpEntity<>(Map.of("username", username, "password", password), headers(csrf.sessionCookie(),
						csrf.headerName(), csrf.token())),
				String.class);
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

	private HttpHeaders headers(AuthenticatedSession session) {
		return headers(session.sessionCookie(), session.csrf().headerName(), session.csrf().token());
	}

	private HttpHeaders headers(String cookie, String csrfHeaderName, String csrfToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, cookie);
		headers.add(csrfHeaderName, csrfToken);
		return headers;
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(code);
	}

	private String sessionCookie(ResponseEntity<?> response) {
		return response.getHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE)
			.stream()
			.filter((cookie) -> cookie.startsWith("JSESSIONID="))
			.findFirst()
			.map((cookie) -> cookie.split(";", 2)[0])
			.orElseThrow();
	}

	private long voucherVersion(long voucherId) {
		return this.jdbcTemplate.queryForObject("select version from gl_voucher where id = ?", Long.class,
				voucherId);
	}

	private String pdfText(byte[] content) throws Exception {
		try (PDDocument document = Loader.loadPDF(content)) {
			return new PDFTextStripper().getText(document);
		}
	}

	private record PrintCase(String objectType, String templateCode, long objectId, String[] expectedSnippets) {
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrf) {
	}

}
