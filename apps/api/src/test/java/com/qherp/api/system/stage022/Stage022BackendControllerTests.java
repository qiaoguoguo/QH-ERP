package com.qherp.api.system.stage022;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.platform.PlatformDocumentTaskWorker;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"qherp.test.context=stage022-backend",
				"qherp.platform.task.worker.enabled=false"
		})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Stage022BackendControllerTests extends PostgresIntegrationTest {

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
	void contractActivationApprovalBlocksDirectEndpointRejectsSelfApprovalAndApprovesThroughTask()
			throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("S22_CUS_" + SEQUENCE.incrementAndGet(), "二十二客户", "ENABLED");
		long ownerUserId = userId("admin");
		long projectId = data(createProject(admin, projectPayload(customerId, ownerUserId))).get("id").longValue();
		JsonNode contract = data(createContract(admin, projectId, contractPayload("022 合同生效审批")));
		long contractId = contract.get("id").longValue();

		assertError(action(admin, "/api/admin/sales-project-contracts/" + contractId + "/activate",
				Map.of("version", contract.get("version").longValue())), HttpStatus.CONFLICT, "APPROVAL_REQUIRED");

		JsonNode submitted = data(post(admin,
				"/api/admin/approvals/sales-project-contract-activation/" + contractId + "/submit",
				Map.of("version", contract.get("version").longValue(), "reason", "合同资料齐备",
						"idempotencyKey", "contract-approval-" + contractId)));
		long approvalId = submitted.get("id").longValue();
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		assertThat(submitted.get("sceneCode").asText()).isEqualTo("SALES_PROJECT_CONTRACT_ACTIVATION");

		JsonNode sameSubmit = data(post(admin,
				"/api/admin/approvals/sales-project-contract-activation/" + contractId + "/submit",
				Map.of("version", contract.get("version").longValue(), "reason", "合同资料齐备",
						"idempotencyKey", "contract-approval-" + contractId)));
		assertThat(sameSubmit.get("id").longValue()).isEqualTo(approvalId);
		assertError(post(admin, "/api/admin/approvals/sales-project-contract-activation/" + contractId + "/submit",
				Map.of("version", contract.get("version").longValue(), "reason", "重复提交",
						"idempotencyKey", "contract-approval-duplicate-" + contractId)),
				HttpStatus.CONFLICT, "APPROVAL_DUPLICATE_ACTIVE");

		JsonNode adminTask = firstTask(admin, "TODO");
		assertError(post(admin, "/api/admin/approval-tasks/" + adminTask.get("id").longValue() + "/approve",
				Map.of("version", adminTask.get("version").longValue(), "comment", "自批应拒绝")), HttpStatus.FORBIDDEN,
				"APPROVAL_SELF_ACTION_FORBIDDEN");

		AuthenticatedSession approver = createUserAndLogin("stage022-contract-approver", "S22_APPROVER",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"sales:contract:view", "sales:project:view", "sales:contract:activate-approve"));
		JsonNode approverTask = firstTask(approver, "TODO");
		JsonNode approved = data(post(approver, "/api/admin/approval-tasks/" + approverTask.get("id").longValue()
				+ "/approve", Map.of("version", approverTask.get("version").longValue(), "comment", "同意生效")));
		assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
		assertThat(contractStatus(contractId)).isEqualTo("EFFECTIVE");
		assertAudit("APPROVAL_APPROVE", "APPROVAL_INSTANCE", approvalId);
		assertAudit("SALES_PROJECT_CONTRACT_ACTIVATE", "SALES_PROJECT_CONTRACT", contractId);
	}

	@Test
	void approvalRejectWithdrawAndCancelCloseTasksCreateMessagesAndKeepBusinessDraft() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("S22_APPR_END_CUS_" + SEQUENCE.incrementAndGet(), "二十二审批终态客户",
				"ENABLED");
		long ownerUserId = userId("admin");
		long projectId = data(createProject(admin, projectPayload(customerId, ownerUserId))).get("id").longValue();
		AuthenticatedSession approver = createUserAndLogin("stage022-end-approver", "S22_END_APPROVER",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"sales:contract:view", "sales:project:view", "sales:contract:activate-approve"));

		JsonNode rejectContract = data(createContract(admin, projectId, contractPayload("审批驳回合同")));
		long rejectContractId = rejectContract.get("id").longValue();
		JsonNode rejectApproval = data(post(admin,
				"/api/admin/approvals/sales-project-contract-activation/" + rejectContractId + "/submit",
				Map.of("version", rejectContract.get("version").longValue(), "reason", "提交后驳回",
						"idempotencyKey", "reject-approval-" + rejectContractId)));
		JsonNode rejectTask = firstTask(approver, "TODO");
		JsonNode rejected = data(post(approver,
				"/api/admin/approval-tasks/" + rejectTask.get("id").longValue() + "/reject",
				Map.of("version", rejectTask.get("version").longValue(), "comment", "资料不完整")));
		assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
		assertThat(contractStatus(rejectContractId)).isEqualTo("DRAFT");
		assertThat(taskStatus(rejectTask.get("id").longValue())).isEqualTo("REJECTED");
		assertAudit("APPROVAL_REJECT", "APPROVAL_INSTANCE", rejectApproval.get("id").longValue());
		assertMessage(userId("admin"), "APPROVAL_DONE");

		long withdrawProjectId = data(createProject(admin, projectPayload(customerId, ownerUserId))).get("id").longValue();
		JsonNode withdrawContract = data(createContract(admin, withdrawProjectId, contractPayload("审批撤回合同")));
		long withdrawContractId = withdrawContract.get("id").longValue();
		JsonNode withdrawApproval = data(post(admin,
				"/api/admin/approvals/sales-project-contract-activation/" + withdrawContractId + "/submit",
				Map.of("version", withdrawContract.get("version").longValue(), "reason", "提交后撤回",
						"idempotencyKey", "withdraw-approval-" + withdrawContractId)));
		JsonNode withdrawn = data(post(admin,
				"/api/admin/approvals/" + withdrawApproval.get("id").longValue() + "/withdraw",
				Map.of("version", withdrawApproval.get("version").longValue(), "comment", "资料需补充")));
		assertThat(withdrawn.get("status").asText()).isEqualTo("WITHDRAWN");
		assertThat(contractStatus(withdrawContractId)).isEqualTo("DRAFT");
		assertThat(pendingTaskCount(withdrawApproval.get("id").longValue())).isZero();
		assertAudit("APPROVAL_WITHDRAW", "APPROVAL_INSTANCE", withdrawApproval.get("id").longValue());

		long cancelProjectId = data(createProject(admin, projectPayload(customerId, ownerUserId))).get("id").longValue();
		JsonNode cancelContract = data(createContract(admin, cancelProjectId, contractPayload("审批治理取消合同")));
		long cancelContractId = cancelContract.get("id").longValue();
		JsonNode cancelApproval = data(post(admin,
				"/api/admin/approvals/sales-project-contract-activation/" + cancelContractId + "/submit",
				Map.of("version", cancelContract.get("version").longValue(), "reason", "提交后治理取消",
						"idempotencyKey", "cancel-approval-" + cancelContractId)));
		JsonNode cancelled = data(post(admin,
				"/api/admin/approvals/" + cancelApproval.get("id").longValue() + "/cancel",
				Map.of("version", cancelApproval.get("version").longValue(), "comment", "异常治理取消")));
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(contractStatus(cancelContractId)).isEqualTo("DRAFT");
		assertThat(pendingTaskCount(cancelApproval.get("id").longValue())).isZero();
		assertAudit("APPROVAL_CANCEL", "APPROVAL_INSTANCE", cancelApproval.get("id").longValue());
	}

	@Test
	void contractAttachmentUsesPrivateStorageDedupeAndBusinessPermissionForDownload() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("S22_ATT_CUS_" + SEQUENCE.incrementAndGet(), "二十二附件客户", "ENABLED");
		long ownerUserId = userId("admin");
		long projectId = data(createProject(admin, projectPayload(customerId, ownerUserId))).get("id").longValue();
		long contractId = data(createContract(admin, projectId, contractPayload("附件合同"))).get("id").longValue();

		JsonNode uploaded = data(uploadAttachment(admin, "SALES_PROJECT_CONTRACT", contractId, "合同附件.txt",
				"text/plain", "合同附件正文".getBytes(), "contract-file-" + contractId));
		long attachmentId = uploaded.get("id").longValue();
		assertThat(uploaded.get("originalFilename").asText()).isEqualTo("合同附件.txt");
		assertThat(uploaded.has("objectKey")).isFalse();
		assertThat(uploaded.get("sha256").asText()).hasSize(64);

		JsonNode duplicate = data(uploadAttachment(admin, "SALES_PROJECT_CONTRACT", contractId, "合同附件.txt",
				"text/plain", "合同附件正文".getBytes(), "contract-file-" + contractId + "-duplicate"));
		assertThat(duplicate.get("id").longValue()).isEqualTo(attachmentId);

		ResponseEntity<byte[]> downloaded = downloadBytes(admin, "/api/admin/attachments/" + attachmentId
				+ "/download");
		assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(new String(downloaded.getBody())).isEqualTo("合同附件正文");
		assertThat(downloaded.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");

		AuthenticatedSession noContractPermission = createUserAndLogin("stage022-attachment-no-contract",
				"S22_ATT_NO_CONTRACT", List.of("platform:attachment:download"));
		ResponseEntity<String> forbidden = getString(noContractPermission, "/api/admin/attachments/" + attachmentId
				+ "/download");
		assertError(forbidden, HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		JsonNode deleted = data(put(admin, "/api/admin/attachments/" + attachmentId + "/delete",
				Map.of("version", uploaded.get("version").longValue(), "reason", "测试删除")));
		assertThat(deleted.get("status").asText()).isEqualTo("DELETED");
		assertError(getString(admin, "/api/admin/attachments/" + attachmentId + "/download"), HttpStatus.NOT_FOUND,
				"ATTACHMENT_NOT_FOUND");
		assertAudit("ATTACHMENT_DELETE", "SALES_PROJECT_CONTRACT", contractId);
	}

	@Test
	void materialExportTaskQueuesThenWorkerCreatesResultRequiresAuthorizationAndCannotCancelSucceededTask()
			throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long unitId = createUnit(admin, "S22_EXP_UNIT_" + SEQUENCE.incrementAndGet(), "二十二导出单位");
		long categoryId = createCategory(admin, "S22_EXP_CAT_" + SEQUENCE.incrementAndGet(), "二十二导出分类");
		createMaterial(admin, "S22_EXP_MAT_" + SEQUENCE.incrementAndGet(), "二十二导出物料", categoryId, unitId);

		JsonNode task = data(postWithIdempotency(admin, "/api/admin/exports/materials",
				Map.of("keyword", "S22_EXP_MAT"), "material-export-" + SEQUENCE.incrementAndGet()));
		long taskId = task.get("id").longValue();
		assertThat(task.get("taskType").asText()).isEqualTo("MATERIAL_EXPORT");
		assertThat(task.get("status").asText()).isEqualTo("QUEUED");
		assertThat(task.get("resultFileId").isNull()).isTrue();

		JsonNode listed = data(get(admin, "/api/admin/document-tasks?page=1&pageSize=20"));
		assertThat(containsId(listed.get("items"), taskId)).isTrue();

		assertError(getString(admin, "/api/admin/document-tasks/" + taskId + "/download"), HttpStatus.CONFLICT,
				"DOCUMENT_TASK_STATUS_INVALID");

		assertThat(this.documentTaskWorker.processAvailableOnce()).isTrue();
		JsonNode succeeded = data(get(admin, "/api/admin/document-tasks/" + taskId));
		assertThat(succeeded.get("status").asText()).as(succeeded.toString()).isEqualTo("SUCCEEDED");
		assertThat(succeeded.get("resultFileId").isNumber()).isTrue();

		ResponseEntity<byte[]> downloaded = downloadBytes(admin, "/api/admin/document-tasks/" + taskId
				+ "/download");
		assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(downloaded.getBody()).isNotEmpty();
		assertThat(downloaded.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains(".xlsx");

		assertError(post(admin, "/api/admin/document-tasks/" + taskId + "/cancel",
				Map.of("version", succeeded.get("version").longValue(), "reason", "终态不能取消")),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_STATUS_INVALID");

		assertError(postWithIdempotency(admin, "/api/admin/exports/materials",
				Map.of("keyword", "S22_EXP_OTHER"), task.get("idempotencyKey").asText()), HttpStatus.CONFLICT,
				"DOCUMENT_TASK_IDEMPOTENCY_CONFLICT");
		this.jdbcTemplate.update("update platform_document_task set expires_at = now() - interval '1 second' where id = ?",
				taskId);
		assertError(getString(admin, "/api/admin/document-tasks/" + taskId + "/download"), HttpStatus.GONE,
				"DOCUMENT_RESULT_EXPIRED");
		assertThat(data(get(admin, "/api/admin/document-tasks/" + taskId)).get("status").asText()).isEqualTo("EXPIRED");
	}

	@Test
	void documentTaskWorkerReclaimsExpiredLeaseAndStopsAfterThreeRetries() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long unitId = createUnit(admin, "S22_LEASE_UNIT_" + SEQUENCE.incrementAndGet(), "二十二租约单位");
		long categoryId = createCategory(admin, "S22_LEASE_CAT_" + SEQUENCE.incrementAndGet(), "二十二租约分类");
		createMaterial(admin, "S22_LEASE_MAT_" + SEQUENCE.incrementAndGet(), "二十二租约物料", categoryId, unitId);

		long taskId = data(postWithIdempotency(admin, "/api/admin/exports/materials",
				Map.of("keyword", "S22_LEASE_MAT"), "material-lease-" + SEQUENCE.incrementAndGet()))
			.get("id")
			.longValue();
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'RUNNING', lease_owner = 'dead-worker', lease_until = now() - interval '1 minute',
				    heartbeat_at = now() - interval '2 minutes', attempt_count = 1
				where id = ?
				""", taskId);
		assertThat(this.documentTaskWorker.processAvailableOnce()).isTrue();
		assertThat(this.jdbcTemplate.queryForObject("""
				select status || ':' || attempt_count || ':' || coalesce(lease_owner, '')
				from platform_document_task
				where id = ?
				""", String.class, taskId)).startsWith("SUCCEEDED:2:");

		long failingTaskId = insertUnsupportedDocumentTask(userId("admin"));
		for (int i = 0; i < 3; i++) {
			assertThat(this.documentTaskWorker.processAvailableOnce()).isTrue();
			this.jdbcTemplate.update("update platform_document_task set next_run_at = now() - interval '1 second' where id = ?",
					failingTaskId);
		}
		assertThat(this.jdbcTemplate.queryForObject("""
				select status || ':' || attempt_count
				from platform_document_task
				where id = ?
				""", String.class, failingTaskId)).isEqualTo("FAILED:3");
	}

	@Test
	void approvalMessagesAndPrintTaskAreQueuedAndGeneratedByWorker() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("S22_PRINT_CUS_" + SEQUENCE.incrementAndGet(), "二十二打印客户", "ENABLED");
		long ownerUserId = userId("admin");
		long projectId = data(createProject(admin, projectPayload(customerId, ownerUserId))).get("id").longValue();
		JsonNode contract = data(createContract(admin, projectId, contractPayload("打印审批合同")));
		JsonNode approval = data(post(admin,
				"/api/admin/approvals/sales-project-contract-activation/" + contract.get("id").longValue()
						+ "/submit",
				Map.of("version", contract.get("version").longValue(), "reason", "打印审批",
						"idempotencyKey", "print-approval-" + contract.get("id").longValue())));
		long approvalId = approval.get("id").longValue();

		JsonNode messages = data(get(admin, "/api/admin/messages/my?unreadOnly=true&page=1&pageSize=20"));
		assertThat(messages.get("items")).isNotEmpty();
		long messageId = messages.get("items").get(0).get("id").longValue();
		JsonNode readMessage = data(put(admin, "/api/admin/messages/" + messageId + "/read", Map.of("version",
				messages.get("items").get(0).get("version").longValue())));
		assertThat(readMessage.get("status").asText()).isEqualTo("READ");

		JsonNode templates = data(get(admin, "/api/admin/print-templates?objectType=APPROVAL_INSTANCE"));
		assertThat(templates).isNotEmpty();
		JsonNode preview = data(get(admin, "/api/admin/print-previews/" + approvalId));
		assertThat(preview.get("templateCode").asText()).isEqualTo("CONTRACT_ACTIVATION_APPROVAL_V1");

		JsonNode task = data(postWithIdempotency(admin, "/api/admin/print-tasks",
				Map.of("approvalInstanceId", approvalId, "templateCode", "CONTRACT_ACTIVATION_APPROVAL_V1"),
				"print-task-" + approvalId));
		assertThat(task.get("taskType").asText()).isEqualTo("APPROVAL_PRINT");
		assertThat(task.get("status").asText()).isEqualTo("QUEUED");

		JsonNode succeeded = processTaskUntilStatus(admin, task.get("id").longValue(), "SUCCEEDED", 8);
		assertThat(succeeded.get("status").asText()).as(succeeded.toString()).isEqualTo("SUCCEEDED");
		ResponseEntity<byte[]> downloaded = downloadBytes(admin,
				"/api/admin/document-tasks/" + task.get("id").longValue() + "/download");
		assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(new String(downloaded.getBody(), 0, 4)).isEqualTo("%PDF");
		try (PDDocument document = Loader.loadPDF(downloaded.getBody())) {
			String text = new PDFTextStripper().getText(document);
			assertThat(text).contains("合同生效审批单", "CONTRACT_ACTIVATION_APPROVAL_V1", "打印审批合同");
			assertThat(document.getDocumentInformation().getTitle()).isEqualTo("合同生效审批单");
			assertThat(document.getDocumentInformation().getSubject()).isEqualTo("CONTRACT_ACTIVATION_APPROVAL_V1");
			assertThat(document.getDocumentInformation().getCustomMetadataValue("templateVersion")).isEqualTo("1");
			assertThat(document.getDocumentInformation().getCustomMetadataValue("approvalInstanceId"))
				.isEqualTo(Long.toString(approvalId));
		}
	}

	@Test
	void messagesRequireRelatedBusinessObjectPermissionForListReadAndReadAll() throws Exception {
		AuthenticatedSession contractDenied = createUserAndLogin("stage022-msg-contract-denied", "S22_MSG_CON_DENIED",
				List.of("platform:message:view", "platform:message:read"));
		long contractDeniedUserId = userId("stage022-msg-contract-denied" + SEQUENCE.get());
		long contractMessageId = insertMessage(contractDeniedUserId, "合同审批结果", "敏感合同 C-022",
				"APPROVAL_DONE", "SALES_PROJECT_CONTRACT", 920001L);
		JsonNode deniedContractPage = data(get(contractDenied,
				"/api/admin/messages/my?unreadOnly=false&page=1&pageSize=20"));
		assertThat(deniedContractPage.get("total").longValue()).isZero();
		assertThat(deniedContractPage.get("items")).isEmpty();
		assertError(put(contractDenied, "/api/admin/messages/" + contractMessageId + "/read", Map.of("version", 0)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		JsonNode contractReadAll = data(put(contractDenied, "/api/admin/messages/read-all", Map.of()));
		assertThat(contractReadAll.get("updatedCount").intValue()).isZero();
		assertThat(messageStatus(contractMessageId)).isEqualTo("UNREAD");

		AuthenticatedSession projectOnly = createUserAndLogin("stage022-msg-contract-project-only",
				"S22_MSG_CON_PROJECT_ONLY", List.of("platform:message:view", "platform:message:read",
						"sales:project:view"));
		long projectOnlyUserId = userId("stage022-msg-contract-project-only" + SEQUENCE.get());
		long projectOnlyContractMessageId = insertMessage(projectOnlyUserId, "合同审批项目可见但合同不可见", "合同摘要",
				"APPROVAL_DONE", "SALES_PROJECT_CONTRACT", 920002L);
		JsonNode projectOnlyPage = data(get(projectOnly,
				"/api/admin/messages/my?unreadOnly=false&page=1&pageSize=20"));
		assertThat(projectOnlyPage.get("total").longValue()).isZero();
		assertThat(projectOnlyPage.get("items")).isEmpty();
		assertError(put(projectOnly, "/api/admin/messages/" + projectOnlyContractMessageId + "/read",
				Map.of("version", 0)), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		JsonNode projectOnlyReadAll = data(put(projectOnly, "/api/admin/messages/read-all", Map.of()));
		assertThat(projectOnlyReadAll.get("updatedCount").intValue()).isZero();
		assertThat(messageStatus(projectOnlyContractMessageId)).isEqualTo("UNREAD");

		AuthenticatedSession contractAllowed = createUserAndLogin("stage022-msg-contract-allowed",
				"S22_MSG_CON_ALLOW", List.of("platform:message:view", "platform:message:read",
						"sales:contract:view"));
		long contractAllowedUserId = userId("stage022-msg-contract-allowed" + SEQUENCE.get());
		long allowedContractMessageId = insertMessage(contractAllowedUserId, "合同审批可见", "合同摘要",
				"APPROVAL_DONE", "SALES_PROJECT_CONTRACT", 920003L);
		JsonNode allowedContractPage = data(get(contractAllowed,
				"/api/admin/messages/my?unreadOnly=false&page=1&pageSize=20"));
		assertThat(allowedContractPage.get("total").longValue()).isOne();
		assertThat(allowedContractPage.get("items").get(0).get("id").longValue())
			.isEqualTo(allowedContractMessageId);
		assertThat(data(put(contractAllowed, "/api/admin/messages/" + allowedContractMessageId + "/read",
				Map.of("version", 0))).get("status").asText()).isEqualTo("READ");

		AuthenticatedSession ecoDenied = createUserAndLogin("stage022-msg-eco-denied", "S22_MSG_ECO_DENIED",
				List.of("platform:message:view", "platform:message:read"));
		long ecoDeniedUserId = userId("stage022-msg-eco-denied" + SEQUENCE.get());
		insertMessage(ecoDeniedUserId, "ECO 审批结果", "敏感 ECO", "APPROVAL_DONE",
				"BOM_ENGINEERING_CHANGE", 930001L);
		JsonNode deniedEcoPage = data(get(ecoDenied, "/api/admin/messages/my?unreadOnly=false&page=1&pageSize=20"));
		assertThat(deniedEcoPage.get("total").longValue()).isZero();

		AuthenticatedSession ecoAllowed = createUserAndLogin("stage022-msg-eco-allowed", "S22_MSG_ECO_ALLOW",
				List.of("platform:message:view", "platform:message:read", "material:bom-eco:view"));
		long ecoAllowedUserId = userId("stage022-msg-eco-allowed" + SEQUENCE.get());
		long ecoMessageId = insertMessage(ecoAllowedUserId, "ECO 审批可见", "ECO 摘要", "APPROVAL_DONE",
				"BOM_ENGINEERING_CHANGE", 930002L);
		JsonNode allowedEcoPage = data(get(ecoAllowed, "/api/admin/messages/my?unreadOnly=false&page=1&pageSize=20"));
		assertThat(allowedEcoPage.get("total").longValue()).isOne();
		assertThat(allowedEcoPage.get("items").get(0).get("id").longValue()).isEqualTo(ecoMessageId);

		AuthenticatedSession documentDenied = createUserAndLogin("stage022-msg-doc-denied", "S22_MSG_DOC_DENIED",
				List.of("platform:message:view", "platform:message:read", "platform:document-task:view"));
		long documentDeniedUserId = userId("stage022-msg-doc-denied" + SEQUENCE.get());
		long materialTaskId = insertDocumentTask(documentDeniedUserId, "MATERIAL_EXPORT");
		insertMessage(documentDeniedUserId, "导出完成", "物料导出结果", "DOCUMENT_TASK_SUCCEEDED",
				"DOCUMENT_TASK", materialTaskId);
		JsonNode deniedDocumentPage = data(get(documentDenied,
				"/api/admin/messages/my?unreadOnly=false&page=1&pageSize=20"));
		assertThat(deniedDocumentPage.get("total").longValue()).isZero();

		AuthenticatedSession documentAllowed = createUserAndLogin("stage022-msg-doc-allowed", "S22_MSG_DOC_ALLOW",
				List.of("platform:message:view", "platform:message:read", "platform:document-task:view",
						"master:material:export"));
		long documentAllowedUserId = userId("stage022-msg-doc-allowed" + SEQUENCE.get());
		long allowedMaterialTaskId = insertDocumentTask(documentAllowedUserId, "MATERIAL_EXPORT");
		long documentMessageId = insertMessage(documentAllowedUserId, "导出可见", "物料导出结果",
				"DOCUMENT_TASK_SUCCEEDED", "DOCUMENT_TASK", allowedMaterialTaskId);
		JsonNode allowedDocumentPage = data(get(documentAllowed,
				"/api/admin/messages/my?unreadOnly=false&page=1&pageSize=20"));
		assertThat(allowedDocumentPage.get("total").longValue()).isOne();
		assertThat(allowedDocumentPage.get("items").get(0).get("id").longValue()).isEqualTo(documentMessageId);

		AuthenticatedSession unknownTarget = createUserAndLogin("stage022-msg-unknown", "S22_MSG_UNKNOWN",
				List.of("platform:message:view", "platform:message:read", "sales:project:view",
						"material:bom-eco:view", "platform:document-task:view", "master:material:export"));
		long unknownTargetUserId = userId("stage022-msg-unknown" + SEQUENCE.get());
		insertMessage(unknownTargetUserId, "未知目标", "不应泄露", "SYSTEM", "UNKNOWN_TARGET", 940001L);
		JsonNode unknownPage = data(get(unknownTarget, "/api/admin/messages/my?unreadOnly=false&page=1&pageSize=20"));
		assertThat(unknownPage.get("total").longValue()).isZero();
	}

	@Test
	void permissionDeniedRequestsCreateSafeAuditRecords() throws Exception {
		AuthenticatedSession denied = createUserAndLogin("stage022-denied-audit", "S22_DENIED_AUDIT",
				List.of("platform:message:view"));
		String deniedUsername = "stage022-denied-audit" + SEQUENCE.get();
		String printTemplatesPath = "/api/admin/print-templates";
		long deniedAuditBefore = permissionDeniedAuditCount(deniedUsername, "GET", printTemplatesPath);

		assertError(get(denied,
				printTemplatesPath + "?objectType=APPROVAL_INSTANCE&token=secret-file.xlsx&credential=hidden"),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertThat(permissionDeniedAuditCount(deniedUsername, "GET", printTemplatesPath))
			.isEqualTo(deniedAuditBefore + 1);
		Map<String, Object> firstAudit = latestPermissionDeniedAudit(deniedUsername, "GET", printTemplatesPath);
		assertThat(firstAudit.get("action")).isEqualTo("PERMISSION_DENIED");
		assertThat(firstAudit.get("target_type")).isEqualTo("API_PERMISSION");
		assertThat(firstAudit.get("target_summary")).isEqualTo("GET /api/admin/print-templates");
		assertThat(firstAudit.get("result")).isEqualTo("FAILURE");
		assertThat(firstAudit.get("error_code")).isEqualTo("AUTH_FORBIDDEN");
		assertThat((String) firstAudit.get("target_summary")).doesNotContain("APPROVAL_INSTANCE", "secret",
				"credential", "xlsx");

		assertError(get(denied, printTemplatesPath + "?objectType=APPROVAL_INSTANCE"), HttpStatus.FORBIDDEN,
				"AUTH_FORBIDDEN");
		assertThat(permissionDeniedAuditCount(deniedUsername, "GET", printTemplatesPath))
			.isEqualTo(deniedAuditBefore + 2);

		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long adminDeniedAuditBefore = permissionDeniedAuditCount("admin", "GET", printTemplatesPath);
		assertThat(data(get(admin, printTemplatesPath + "?objectType=APPROVAL_INSTANCE"))).isNotEmpty();
		assertThat(permissionDeniedAuditCount("admin", "GET", printTemplatesPath)).isEqualTo(adminDeniedAuditBefore);
	}

	@Test
	void materialImportConfirmUsesQueuedWorkerAndIdempotencyWithoutDuplicateCreate() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long unitId = createUnit(admin, "S22_IMP_UNIT_" + SEQUENCE.incrementAndGet(), "二十二导入单位");
		long categoryId = createCategory(admin, "S22_IMP_CAT_" + SEQUENCE.incrementAndGet(), "二十二导入分类");
		String materialCode = "S22_IMP_MAT_" + SEQUENCE.incrementAndGet();
		byte[] workbook = materialImportWorkbook(materialCode, "导入物料", "S22_IMP_CAT_",
				"S22_IMP_UNIT_");

		ResponseEntity<byte[]> template = downloadBytes(admin, "/api/admin/import-templates/materials");
		assertThat(template.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(template.getBody()).isNotEmpty();
		JsonNode task = data(uploadImport(admin, "/api/admin/imports/materials", "materials.xlsx", workbook,
				"material-import-" + materialCode));
		assertThat(task.get("taskType").asText()).isEqualTo("MATERIAL_IMPORT");
		assertThat(task.get("stage").asText()).isEqualTo("VALIDATE");
		assertThat(task.get("status").asText()).isEqualTo("QUEUED");

		JsonNode ready = processTaskUntilStatus(admin, task.get("id").longValue(), "READY_TO_COMMIT", 8);
		assertThat(ready.get("status").asText()).as(ready.toString()).isEqualTo("READY_TO_COMMIT");
		JsonNode commitQueued = data(postWithIdempotency(admin,
				"/api/admin/imports/" + task.get("id").longValue() + "/confirm",
				Map.of("version", ready.get("version").longValue()), "material-import-confirm-" + materialCode));
		assertThat(commitQueued.get("stage").asText()).isEqualTo("COMMIT");
		assertThat(commitQueued.get("status").asText()).isEqualTo("QUEUED");

		JsonNode succeeded = processTaskUntilStatus(admin, task.get("id").longValue(), "SUCCEEDED", 8);
		assertThat(succeeded.get("status").asText()).as(succeeded.toString()).isEqualTo("SUCCEEDED");
		assertThat(countMaterial(materialCode)).isOne();

		JsonNode sameConfirm = data(postWithIdempotency(admin,
				"/api/admin/imports/" + task.get("id").longValue() + "/confirm",
				Map.of("version", succeeded.get("version").longValue()), "material-import-confirm-" + materialCode));
		assertThat(sameConfirm.get("id").longValue()).isEqualTo(task.get("id").longValue());
		assertThat(countMaterial(materialCode)).isOne();
		assertThat(unitId).isPositive();
		assertThat(categoryId).isPositive();

		insertMaterialCodingRule("S22_AUTO_MAT_", 1);
		byte[] blankCodeWorkbook = materialImportWorkbook(null, "自动编码导入物料", "S22_IMP_CAT_",
				"S22_IMP_UNIT_");
		JsonNode blankCodeTask = data(uploadImport(admin, "/api/admin/imports/materials", "blank-code-materials.xlsx",
				blankCodeWorkbook, "material-import-blank-code-" + SEQUENCE.incrementAndGet()));
		JsonNode blankReady = processTaskUntilStatus(admin, blankCodeTask.get("id").longValue(), "READY_TO_COMMIT", 8);
		assertThat(countMaterial("S22_AUTO_MAT_0001")).isZero();
		JsonNode blankCommitQueued = data(postWithIdempotency(admin,
				"/api/admin/imports/" + blankCodeTask.get("id").longValue() + "/confirm",
				Map.of("version", blankReady.get("version").longValue()),
				"blank-code-confirm-" + SEQUENCE.incrementAndGet()));
		assertThat(blankCommitQueued.get("status").asText()).isEqualTo("QUEUED");
		JsonNode blankSucceeded = processTaskUntilStatus(admin, blankCodeTask.get("id").longValue(), "SUCCEEDED", 8);
		assertThat(blankSucceeded.get("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(countMaterial("S22_AUTO_MAT_0001")).isOne();
	}

	@Test
	void bomDraftImportAndExportUseDocumentWorker() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long unitId = createUnit(admin, "S22_BOM_UNIT_" + SEQUENCE.incrementAndGet(), "二十二 BOM 单位");
		long categoryId = createCategory(admin, "S22_BOM_CAT_" + SEQUENCE.incrementAndGet(), "二十二 BOM 分类");
		String parentCode = "S22_BOM_PARENT_" + SEQUENCE.incrementAndGet();
		String childCode = "S22_BOM_CHILD_" + SEQUENCE.incrementAndGet();
		createMaterial(admin, parentCode, "BOM 父项", categoryId, unitId, "FINISHED_GOOD", "SELF_MADE");
		createMaterial(admin, childCode, "BOM 子项", categoryId, unitId);
		String bomCode = "S22_BOM_IMP_" + SEQUENCE.incrementAndGet();

		JsonNode importTask = data(uploadImport(admin, "/api/admin/imports/bom-drafts", "bom-draft.xlsx",
				bomDraftImportWorkbook("CREATE", null, null, bomCode, parentCode, "V1", childCode),
				"bom-import-" + bomCode));
		assertThat(importTask.get("taskType").asText()).isEqualTo("BOM_DRAFT_IMPORT");
		JsonNode ready = processTaskUntilStatus(admin, importTask.get("id").longValue(), "READY_TO_COMMIT", 8);
		assertThat(ready.get("status").asText()).as(ready.toString()).isEqualTo("READY_TO_COMMIT");
		data(postWithIdempotency(admin, "/api/admin/imports/" + importTask.get("id").longValue() + "/confirm",
				Map.of("version", ready.get("version").longValue()), "bom-import-confirm-" + bomCode));
		JsonNode committed = processTaskUntilStatus(admin, importTask.get("id").longValue(), "SUCCEEDED", 8);
		assertThat(committed.get("status").asText()).as(committed.toString()).isEqualTo("SUCCEEDED");

		long bomId = this.jdbcTemplate.queryForObject("select id from mfg_bom where bom_code = ?", Long.class,
				bomCode);
		JsonNode exportTask = data(postWithIdempotency(admin, "/api/admin/exports/bom-drafts/" + bomId, Map.of(),
				"bom-export-" + bomId));
		assertThat(exportTask.get("taskType").asText()).isEqualTo("BOM_DRAFT_EXPORT");
		assertThat(exportTask.get("status").asText()).isEqualTo("QUEUED");
		JsonNode succeeded = processTaskUntilStatus(admin, exportTask.get("id").longValue(), "SUCCEEDED", 8);
		assertThat(succeeded.get("status").asText()).as(succeeded.toString()).isEqualTo("SUCCEEDED");
		assertThat(downloadBytes(admin,
				"/api/admin/document-tasks/" + exportTask.get("id").longValue() + "/download").getBody()).isNotEmpty();
	}

	private ResponseEntity<String> uploadAttachment(AuthenticatedSession session, String objectType, long objectId,
			String filename, String contentType, byte[] content, String idempotencyKey) {
		org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
		body.add("objectType", objectType);
		body.add("objectId", Long.toString(objectId));
		body.add("description", "测试附件");
		body.add("idempotencyKey", idempotencyKey);
		body.add("file", new ByteArrayResource(content) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		HttpHeaders headers = headers(session);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.set("X-QHERP-Test-Content-Type", contentType);
		return this.restTemplate.exchange("/api/admin/attachments", HttpMethod.POST, new HttpEntity<>(body, headers),
				String.class);
	}

	private JsonNode firstTask(AuthenticatedSession session, String scope) throws Exception {
		JsonNode page = data(get(session, "/api/admin/approval-tasks?scope=" + scope + "&page=1&pageSize=20"));
		assertThat(page.get("items")).isNotEmpty();
		return page.get("items").get(0);
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

	private ResponseEntity<String> createProject(AuthenticatedSession session, Map<String, Object> body) {
		return exchange(session, HttpMethod.POST, "/api/admin/sales-projects", body);
	}

	private ResponseEntity<String> createContract(AuthenticatedSession session, long projectId, Map<String, Object> body) {
		return exchange(session, HttpMethod.POST, "/api/admin/sales-projects/" + projectId + "/contracts", body);
	}

	private ResponseEntity<String> action(AuthenticatedSession session, String path, Map<String, Object> body) {
		return exchange(session, HttpMethod.PUT, path, body);
	}

	private ResponseEntity<String> post(AuthenticatedSession session, String path, Map<String, Object> body) {
		return exchange(session, HttpMethod.POST, path, body);
	}

	private ResponseEntity<String> postWithIdempotency(AuthenticatedSession session, String path, Map<String, Object> body,
			String idempotencyKey) {
		HttpHeaders headers = headers(session);
		headers.add("Idempotency-Key", idempotencyKey);
		return this.restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> put(AuthenticatedSession session, String path, Map<String, Object> body) {
		return exchange(session, HttpMethod.PUT, path, body);
	}

	private ResponseEntity<String> get(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers(session)), String.class);
	}

	private ResponseEntity<String> getString(AuthenticatedSession session, String path) {
		HttpHeaders headers = headers(session);
		return this.restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
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

	private ResponseEntity<byte[]> downloadBytes(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers(session)), byte[].class);
	}

	private ResponseEntity<String> exchange(AuthenticatedSession session, HttpMethod method, String path,
			Object body) {
		return this.restTemplate.exchange(path, method, new HttpEntity<>(body, headers(session)), String.class);
	}

	private Map<String, Object> projectPayload(long customerId, long ownerUserId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("name", "022 销售项目 " + SEQUENCE.incrementAndGet());
		payload.put("customerId", customerId);
		payload.put("ownerUserId", ownerUserId);
		payload.put("plannedStartDate", LocalDate.now().toString());
		payload.put("plannedFinishDate", LocalDate.now().plusDays(30).toString());
		payload.put("targetRevenue", "100000.00");
		payload.put("targetCost", "60000.00");
		payload.put("remark", "022 项目");
		return payload;
	}

	private Map<String, Object> contractPayload(String name) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("contractType", "MAIN");
		payload.put("name", name);
		payload.put("signedDate", LocalDate.now().toString());
		payload.put("effectiveStartDate", LocalDate.now().toString());
		payload.put("effectiveEndDate", LocalDate.now().plusDays(60).toString());
		payload.put("amount", "1000.00");
		payload.put("remark", "022 合同");
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

	private String contractStatus(long contractId) {
		return this.jdbcTemplate.queryForObject("select status from sal_project_contract where id = ?", String.class,
				contractId);
	}

	private long createUnit(AuthenticatedSession session, String code, String name) throws Exception {
		return data(exchange(session, HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", code, "name", name, "precisionScale", 2, "sortOrder", 10, "status", "ENABLED")))
			.get("id")
			.longValue();
	}

	private long createCategory(AuthenticatedSession session, String code, String name) throws Exception {
		return data(exchange(session, HttpMethod.POST, "/api/admin/master/material-categories",
				Map.of("code", code, "name", name, "status", "ENABLED", "sortOrder", 10)))
			.get("id")
			.longValue();
	}

	private long createMaterial(AuthenticatedSession session, String code, String name, long categoryId, long unitId)
			throws Exception {
		return createMaterial(session, code, name, categoryId, unitId, "RAW_MATERIAL");
	}

	private long createMaterial(AuthenticatedSession session, String code, String name, long categoryId, long unitId,
			String materialType) throws Exception {
		return createMaterial(session, code, name, categoryId, unitId, materialType, "PURCHASED");
	}

	private long createMaterial(AuthenticatedSession session, String code, String name, long categoryId, long unitId,
			String materialType, String sourceType) throws Exception {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("code", code);
		payload.put("name", name);
		payload.put("specification", "S");
		payload.put("materialType", materialType);
		payload.put("sourceType", sourceType);
		payload.put("categoryId", categoryId);
		payload.put("unitId", unitId);
		payload.put("status", "ENABLED");
		return data(exchange(session, HttpMethod.POST, "/api/admin/master/materials", payload)).get("id").longValue();
	}

	private long countMaterial(String code) {
		return this.jdbcTemplate.queryForObject("select count(*) from mst_material where code = ?", Long.class, code);
	}

	private String taskStatus(long taskId) {
		return this.jdbcTemplate.queryForObject("select status from platform_approval_task where id = ?", String.class,
				taskId);
	}

	private long pendingTaskCount(long approvalId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_task
				where instance_id = ?
				and status = 'PENDING'
				""", Long.class, approvalId);
	}

	private void insertMaterialCodingRule(String prefix, long nextSerialNo) {
		this.jdbcTemplate.update("""
				insert into sys_coding_rule (
					rule_code, name, object_type, prefix, date_pattern, serial_length, reset_cycle,
					next_serial_no, status, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'MATERIAL', ?, 'NONE', 4, 'NEVER', ?, 'ENABLED', 'test', now(), 'test', now())
				""", "S22_MATERIAL_RULE_" + SEQUENCE.incrementAndGet(), "022 物料自动编码", prefix, nextSerialNo);
	}

	private byte[] materialImportWorkbook(String code, String name, String categoryCodePrefix, String unitCodePrefix)
			throws Exception {
		String categoryCode = this.jdbcTemplate.queryForObject(
				"select code from mst_material_category where code like ? order by id desc limit 1", String.class,
				categoryCodePrefix + "%");
		String unitCode = this.jdbcTemplate.queryForObject(
				"select code from mst_unit where code like ? order by id desc limit 1", String.class,
				unitCodePrefix + "%");
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("materials");
			Row header = sheet.createRow(0);
			String[] headers = { "code", "name", "specification", "materialType", "sourceType", "trackingMethod",
					"categoryCode", "unitCode", "status", "costCategory", "inventoryValuationCategory",
					"inventoryValueEnabled", "projectCostEnabled", "costRemark", "remark" };
			for (int i = 0; i < headers.length; i++) {
				header.createCell(i).setCellValue(headers[i]);
			}
			Row row = sheet.createRow(1);
			String[] values = { code, name, "S", "RAW_MATERIAL", "PURCHASED", "NONE", categoryCode, unitCode,
					"ENABLED", "DIRECT_MATERIAL", "VALUATED_MATERIAL", "true", "true", "导入成本", "导入备注" };
			for (int i = 0; i < values.length; i++) {
				if (values[i] != null) {
					row.createCell(i).setCellValue(values[i]);
				}
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private byte[] bomDraftImportWorkbook(String mode, Long bomId, Long bomVersion, String bomCode,
			String parentMaterialCode, String versionCode, String childMaterialCode) throws Exception {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet headerSheet = workbook.createSheet("bom");
			Row header = headerSheet.createRow(0);
			String[] headerNames = { "mode", "bomId", "version", "bomCode", "parentMaterialCode", "versionCode",
					"name", "baseQuantity", "effectiveFrom", "effectiveTo", "remark" };
			for (int i = 0; i < headerNames.length; i++) {
				header.createCell(i).setCellValue(headerNames[i]);
			}
			Row row = headerSheet.createRow(1);
			row.createCell(0).setCellValue(mode);
			if (bomId != null) {
				row.createCell(1).setCellValue(bomId);
			}
			if (bomVersion != null) {
				row.createCell(2).setCellValue(bomVersion);
			}
			row.createCell(3).setCellValue(bomCode);
			row.createCell(4).setCellValue(parentMaterialCode);
			row.createCell(5).setCellValue(versionCode);
			row.createCell(6).setCellValue("导入 BOM");
			row.createCell(7).setCellValue("1");
			row.createCell(8).setCellValue(LocalDate.now().toString());
			row.createCell(9).setCellValue(LocalDate.now().plusDays(30).toString());
			row.createCell(10).setCellValue("BOM 导入");

			Sheet itemsSheet = workbook.createSheet("items");
			Row itemHeader = itemsSheet.createRow(0);
			String[] itemHeaders = { "lineNo", "childMaterialCode", "quantity", "lossRate", "remark" };
			for (int i = 0; i < itemHeaders.length; i++) {
				itemHeader.createCell(i).setCellValue(itemHeaders[i]);
			}
			Row item = itemsSheet.createRow(1);
			item.createCell(0).setCellValue("10");
			item.createCell(1).setCellValue(childMaterialCode);
			item.createCell(2).setCellValue(BigDecimal.ONE.toPlainString());
			item.createCell(3).setCellValue("0");
			item.createCell(4).setCellValue("导入明细");
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private long insertUnsupportedDocumentTask(long userId) {
		return this.jdbcTemplate.queryForObject("""
				insert into platform_document_task (
					task_no, task_type, stage, status, request_payload, idempotency_key, created_by_user_id,
					created_by_username, next_run_at, created_at
				)
				values (?, 'UNSUPPORTED_EXPORT', 'EXPORT', 'QUEUED', cast(? as jsonb), ?, ?, 'admin', now(), now())
				returning id
				""", Long.class, "S22FAIL" + SEQUENCE.incrementAndGet(), "{}",
				"failing-export-" + SEQUENCE.incrementAndGet(), userId);
	}

	private long insertDocumentTask(long userId, String taskType) {
		return this.jdbcTemplate.queryForObject("""
				insert into platform_document_task (
					task_no, task_type, stage, status, request_payload, idempotency_key, created_by_user_id,
					created_by_username, next_run_at, created_at
				)
				values (?, ?, 'EXPORT', 'SUCCEEDED', cast(? as jsonb), ?, ?, 'message-owner', now(), now())
				returning id
				""", Long.class, "S22MSG" + SEQUENCE.incrementAndGet(), taskType, "{}",
				"message-task-" + SEQUENCE.incrementAndGet(), userId);
	}

	private long insertMessage(long recipientUserId, String title, String content, String messageType,
			String relatedObjectType, Long relatedObjectId) {
		return this.jdbcTemplate.queryForObject("""
				insert into platform_message (
					recipient_user_id, title, content, message_type, status, related_object_type,
					related_object_id, created_at
				)
				values (?, ?, ?, ?, 'UNREAD', ?, ?, now())
				returning id
				""", Long.class, recipientUserId, title, content, messageType, relatedObjectType, relatedObjectId);
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
				""", Long.class, rolePrefix + suffix, "022 测试角色" + suffix, "022 测试角色");
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
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

	private long permissionDeniedAuditCount(String username, String method, String path) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where operator_username = ?
				and action = 'PERMISSION_DENIED'
				and request_method = ?
				and request_path = ?
				and result = 'FAILURE'
				and error_code = 'AUTH_FORBIDDEN'
				""", Long.class, username, method, path);
	}

	private Map<String, Object> latestPermissionDeniedAudit(String username, String method, String path) {
		return this.jdbcTemplate.queryForMap("""
				select action, target_type, target_summary, request_method, request_path, result, error_code
				from sys_audit_log
				where operator_username = ?
				and action = 'PERMISSION_DENIED'
				and request_method = ?
				and request_path = ?
				and result = 'FAILURE'
				and error_code = 'AUTH_FORBIDDEN'
				order by id desc
				limit 1
				""", username, method, path);
	}

	private void assertMessage(long recipientUserId, String messageType) {
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_message
				where recipient_user_id = ?
				and message_type = ?
				""", Long.class, recipientUserId, messageType)).as(messageType).isPositive();
	}

	private String messageStatus(long messageId) {
		return this.jdbcTemplate.queryForObject("select status from platform_message where id = ?", String.class,
				messageId);
	}

	private boolean containsId(JsonNode items, long id) {
		for (JsonNode item : items) {
			if (item.get("id").longValue() == id) {
				return true;
			}
		}
		return false;
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

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrf) {
	}

}
