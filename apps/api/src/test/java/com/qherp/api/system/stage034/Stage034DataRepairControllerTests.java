package com.qherp.api.system.stage034;

import com.qherp.api.support.PostgresIntegrationTest;
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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"qherp.test.context=stage034-data-repair",
				"qherp.platform.task.worker.enabled=false"
		})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Stage034DataRepairControllerTests extends PostgresIntegrationTest {

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
		registry.add("qherp.storage.s3.bucket", () -> "qherp-test-private-stage034-repair");
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

	@Test
	void customerRepairUsesWhitelistApprovalExecutionVerificationAndStructuredAudit() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("S34_CUS_" + SEQUENCE.incrementAndGet(), "034 原客户",
				"034 原联系人", "13800000000", "原备注");
		long customerVersion = customerVersion(customerId);

		JsonNode adapters = data(get(admin, "/api/admin/platform/data-repair-adapters"));
		assertThat(adapters.toString()).contains("CUSTOMER_PROFILE_CORRECTION_V1",
				"MATERIAL_PROFILE_CORRECTION_V1", "SUPPLIER_PROFILE_CORRECTION_V1");

		assertError(post(admin, "/api/admin/platform/data-repairs", repairPayload(
				"CUSTOMER_PROFILE_CORRECTION_V1", "CUSTOMER", customerId, customerVersion,
				List.of(change("code", "S34_ILLEGAL"))), "repair-bad-field-" + customerId),
				HttpStatus.BAD_REQUEST, "DATA_REPAIR_FIELD_NOT_ALLOWED");

		Map<String, Object> payload = repairPayload("CUSTOMER_PROFILE_CORRECTION_V1", "CUSTOMER",
				customerId, customerVersion, List.of(change("name", "034 修复后客户"),
						change("contactName", "034 修复联系人")));
		JsonNode draft = data(post(admin, "/api/admin/platform/data-repairs", payload,
				"repair-create-" + customerId));
		long repairId = draft.get("id").longValue();
		assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
		assertThat(draft.get("targetObjectNo").asText()).startsWith("S34_CUS_");
		assertThat(draft.get("changes").toString()).contains("034 原客户", "034 修复后客户");
		assertThat(draft.get("availableActions").toString()).contains("SUBMIT");

		JsonNode repeatedDraft = data(post(admin, "/api/admin/platform/data-repairs", payload,
				"repair-create-" + customerId));
		assertThat(repeatedDraft.get("id").longValue()).isEqualTo(repairId);
		assertError(post(admin, "/api/admin/platform/data-repairs", repairPayload(
				"CUSTOMER_PROFILE_CORRECTION_V1", "CUSTOMER", customerId, customerVersion,
				List.of(change("name", "034 同键不同内容"))), "repair-create-" + customerId),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_IDEMPOTENCY_CONFLICT");

		JsonNode submitted = data(post(admin, "/api/admin/platform/data-repairs/" + repairId + "/submit",
				Map.of("version", draft.get("version").longValue(), "reason", "客户历史名称需要修复",
						"idempotencyKey", "repair-submit-" + repairId)));
		assertThat(submitted.get("status").asText()).isEqualTo("PENDING_APPROVAL");
		long approvalId = submitted.get("approvalSummary").get("id").longValue();
		long approvalTaskId = pendingApprovalTaskId(approvalId);
		long approvalTaskVersion = approvalTaskVersion(approvalTaskId);
		assertError(post(admin, "/api/admin/approval-tasks/" + approvalTaskId + "/approve",
				Map.of("version", approvalTaskVersion, "comment", "自批应拒绝",
						"idempotencyKey", "repair-self-approve-" + repairId)),
				HttpStatus.FORBIDDEN, "DATA_REPAIR_SELF_APPROVAL_FORBIDDEN");

		AuthenticatedSession approver = createUserAndLogin("stage034-repair-approver", "S34_REPAIR_APPROVER",
				List.of("platform:approval:view", "platform:todo:view", "platform:data-repair:view",
						"platform:data-repair:approve", "platform:data-repair:verify",
						"master:customer:update"));
		JsonNode approved = data(post(approver, "/api/admin/approval-tasks/" + approvalTaskId + "/approve",
				Map.of("version", approvalTaskVersion, "comment", "同意修复",
						"idempotencyKey", "repair-approve-" + repairId)));
		assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
		JsonNode ready = data(get(admin, "/api/admin/platform/data-repairs/" + repairId));
		assertThat(ready.get("status").asText()).isEqualTo("READY_TO_EXECUTE");

		JsonNode executed = data(post(admin, "/api/admin/platform/data-repairs/" + repairId + "/execute",
				Map.of("version", ready.get("version").longValue(),
						"idempotencyKey", "repair-execute-" + repairId)));
		assertThat(executed.get("status").asText()).isEqualTo("EXECUTED");
		assertThat(customerName(customerId)).isEqualTo("034 修复后客户");
		assertThat(customerVersion(customerId)).isEqualTo(customerVersion + 1);
		assertThat(auditDetailCount("DATA_REPAIR_EXECUTE", "DATA_REPAIR_REQUEST", repairId)).isOne();

		JsonNode replayAfterExecution = data(post(admin, "/api/admin/platform/data-repairs", payload,
				"repair-create-" + customerId));
		assertThat(replayAfterExecution.get("id").longValue()).isEqualTo(repairId);
		assertThat(replayAfterExecution.get("status").asText()).isEqualTo("EXECUTED");
		assertError(post(admin, "/api/admin/platform/data-repairs", repairPayload(
				"CUSTOMER_PROFILE_CORRECTION_V1", "CUSTOMER", customerId, customerVersion,
				List.of(change("name", "034 执行后同键不同内容"))), "repair-create-" + customerId),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_IDEMPOTENCY_CONFLICT");

		AuthenticatedSession noCreatePermission = createUserAndLogin("stage034-repair-no-create",
				"S34_REPAIR_NO_CREATE", List.of("master:customer:update"));
		ResponseEntity<String> forbiddenReplay = post(noCreatePermission, "/api/admin/platform/data-repairs", payload,
				"repair-create-" + customerId);
		assertThat(forbiddenReplay.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(code(forbiddenReplay)).isEqualTo("AUTH_FORBIDDEN");
		assertThat(forbiddenReplay.getBody()).doesNotContain("\"id\":" + repairId, draft.get("requestNo").asText());

		assertError(post(admin, "/api/admin/platform/data-repairs/" + repairId + "/verify",
				Map.of("version", executed.get("version").longValue(), "passed", true, "comment", "执行人自验",
						"idempotencyKey", "repair-self-verify-" + repairId)),
				HttpStatus.FORBIDDEN, "DATA_REPAIR_SELF_VERIFY_FORBIDDEN");

		JsonNode verified = data(post(approver, "/api/admin/platform/data-repairs/" + repairId + "/verify",
				Map.of("version", executed.get("version").longValue(), "passed", true, "comment", "复核通过",
						"idempotencyKey", "repair-verify-" + repairId)));
		assertThat(verified.get("status").asText()).isEqualTo("VERIFIED");
		assertThat(eventCount(repairId, "VERIFY")).isOne();
	}

	@Test
	void dataRepairAttachmentAndVerifyFailedTerminalRequireStackedPermissions() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession customerRepairViewer = createUserAndLogin("stage034-repair-customer-view",
				"S34_REPAIR_CUS_VIEW", List.of("platform:data-repair:view", "master:customer:update"));
		JsonNode scopedAdapters = data(get(customerRepairViewer, "/api/admin/platform/data-repair-adapters"));
		assertThat(scopedAdapters.toString()).contains("CUSTOMER_PROFILE_CORRECTION_V1");
		assertThat(scopedAdapters.toString()).doesNotContain("MATERIAL_PROFILE_CORRECTION_V1",
				"SUPPLIER_PROFILE_CORRECTION_V1");

		long customerId = insertCustomer("S34_TERM_CUS_" + SEQUENCE.incrementAndGet(), "034 终态客户",
				"034 原联系人", "13800000001", "终态测试");
		long customerVersion = customerVersion(customerId);
		Map<String, Object> payload = repairPayload("CUSTOMER_PROFILE_CORRECTION_V1", "CUSTOMER",
				customerId, customerVersion, List.of(change("name", "034 失败验收客户")));
		JsonNode draft = data(post(admin, "/api/admin/platform/data-repairs", payload,
				"repair-terminal-create-" + customerId));
		long repairId = draft.get("id").longValue();

		JsonNode uploaded = data(uploadAttachment(admin, "DATA_REPAIR_REQUEST", repairId, "修复证据.txt",
				"text/plain", "034 数据修复证据".getBytes(), "repair-attachment-" + repairId));
		assertThat(uploaded.get("fileName").asText()).isEqualTo("修复证据.txt");
		JsonNode attachmentPage = data(get(admin,
				"/api/admin/attachments?objectType=DATA_REPAIR_REQUEST&objectId=" + repairId));
		assertThat(attachmentPage.get("total").longValue()).isOne();
		assertThat(attachmentPage.get("items").get(0).get("availableActions").toString()).contains("DOWNLOAD",
				"DELETE");

		AuthenticatedSession attachmentOnly = createUserAndLogin("stage034-repair-attachment-only",
				"S34_REPAIR_ATT_ONLY", List.of("platform:attachment:view", "platform:attachment:upload",
						"platform:attachment:download", "platform:attachment:delete"));
		assertError(get(attachmentOnly, "/api/admin/attachments?objectType=DATA_REPAIR_REQUEST&objectId=" + repairId),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertError(uploadAttachment(attachmentOnly, "DATA_REPAIR_REQUEST", repairId, "越权证据.txt",
				"text/plain", "越权".getBytes(), "repair-attachment-forbidden-" + repairId),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		ResponseEntity<byte[]> downloaded = downloadBytes(admin, "/api/admin/attachments/"
				+ uploaded.get("id").longValue() + "/download");
		assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(new String(downloaded.getBody())).isEqualTo("034 数据修复证据");
		JsonNode deleted = data(put(admin, "/api/admin/attachments/" + uploaded.get("id").longValue()
				+ "/delete", Map.of("version", uploaded.get("version").longValue(), "reason", "终态测试清理")));
		assertThat(deleted.get("status").asText()).isEqualTo("DELETED");

		JsonNode submitted = data(post(admin, "/api/admin/platform/data-repairs/" + repairId + "/submit",
				Map.of("version", draft.get("version").longValue(), "reason", "终态测试提交",
						"idempotencyKey", "repair-terminal-submit-" + repairId)));
		long approvalId = submitted.get("approvalSummary").get("id").longValue();
		long approvalTaskId = pendingApprovalTaskId(approvalId);
		long approvalTaskVersion = approvalTaskVersion(approvalTaskId);
		AuthenticatedSession approver = createUserAndLogin("stage034-repair-terminal-approver",
				"S34_REPAIR_TERM_APPROVER", List.of("platform:approval:view", "platform:todo:view",
						"platform:data-repair:view", "platform:data-repair:approve", "platform:data-repair:verify",
						"master:customer:update"));
		data(post(approver, "/api/admin/approval-tasks/" + approvalTaskId + "/approve",
				Map.of("version", approvalTaskVersion, "comment", "同意执行",
						"idempotencyKey", "repair-terminal-approve-" + repairId)));
		JsonNode ready = data(get(admin, "/api/admin/platform/data-repairs/" + repairId));
		JsonNode executed = data(post(admin, "/api/admin/platform/data-repairs/" + repairId + "/execute",
				Map.of("version", ready.get("version").longValue(),
						"idempotencyKey", "repair-terminal-execute-" + repairId)));

		JsonNode verifyFailed = data(post(approver, "/api/admin/platform/data-repairs/" + repairId + "/verify",
				Map.of("version", executed.get("version").longValue(), "passed", false, "comment", "复核不通过",
						"idempotencyKey", "repair-terminal-verify-failed-" + repairId)));
		assertThat(verifyFailed.get("status").asText()).isEqualTo("VERIFY_FAILED");
		assertThat(verifyFailed.get("availableActions").toString()).doesNotContain("VERIFY", "EXECUTE");
		assertError(post(approver, "/api/admin/platform/data-repairs/" + repairId + "/verify",
				Map.of("version", verifyFailed.get("version").longValue(), "passed", true, "comment", "不得复活",
						"idempotencyKey", "repair-terminal-verify-revive-" + repairId)),
				HttpStatus.CONFLICT, "DATA_REPAIR_STATUS_INVALID");
		JsonNode terminal = data(get(admin, "/api/admin/platform/data-repairs/" + repairId));
		assertThat(terminal.get("status").asText()).isEqualTo("VERIFY_FAILED");
		assertThat(terminal.get("availableActions").toString()).doesNotContain("VERIFY", "EXECUTE");
		assertThat(eventCount(repairId, "VERIFY")).isOne();
	}

	@Test
	void dataRepairVerifyRequiresExplicitPassedAndCancelIsIdempotent() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession approver = createUserAndLogin("stage034-repair-explicit-verifier",
				"S34_REPAIR_EXPLICIT_VERIFIER", List.of("platform:approval:view", "platform:todo:view",
						"platform:data-repair:view", "platform:data-repair:approve", "platform:data-repair:verify",
						"master:customer:update"));

		long cancelCustomerId = insertCustomer("S34_CANCEL_CUS_" + SEQUENCE.incrementAndGet(), "034 撤销客户",
				"034 原联系人", "13800000002", "撤销幂等");
		JsonNode cancelDraft = data(post(admin, "/api/admin/platform/data-repairs",
				repairPayload("CUSTOMER_PROFILE_CORRECTION_V1", "CUSTOMER", cancelCustomerId,
						customerVersion(cancelCustomerId), List.of(change("name", "034 撤销后客户"))),
				"repair-cancel-create-" + cancelCustomerId));
		long cancelRepairId = cancelDraft.get("id").longValue();
		Map<String, Object> cancelRequest = Map.of("version", cancelDraft.get("version").longValue(),
				"reason", "034 撤销草稿", "idempotencyKey", "repair-cancel-" + cancelRepairId);
		JsonNode cancelled = data(post(admin, "/api/admin/platform/data-repairs/" + cancelRepairId + "/cancel",
				cancelRequest));
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		JsonNode replayedCancel = data(post(admin, "/api/admin/platform/data-repairs/" + cancelRepairId + "/cancel",
				cancelRequest));
		assertThat(replayedCancel.get("id").longValue()).isEqualTo(cancelRepairId);
		assertThat(replayedCancel.get("version").longValue()).isEqualTo(cancelled.get("version").longValue());
		assertError(post(admin, "/api/admin/platform/data-repairs/" + cancelRepairId + "/cancel",
				Map.of("version", cancelDraft.get("version").longValue(), "reason", "034 同键不同撤销",
						"idempotencyKey", "repair-cancel-" + cancelRepairId)),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_IDEMPOTENCY_CONFLICT");

		long verifyCustomerId = insertCustomer("S34_VERIFY_CUS_" + SEQUENCE.incrementAndGet(), "034 显式验证客户",
				"034 原联系人", "13800000003", "验证 passed 必填");
		JsonNode draft = data(post(admin, "/api/admin/platform/data-repairs",
				repairPayload("CUSTOMER_PROFILE_CORRECTION_V1", "CUSTOMER", verifyCustomerId,
						customerVersion(verifyCustomerId), List.of(change("name", "034 显式验证后客户"))),
				"repair-explicit-create-" + verifyCustomerId));
		long repairId = draft.get("id").longValue();
		JsonNode submitted = data(post(admin, "/api/admin/platform/data-repairs/" + repairId + "/submit",
				Map.of("version", draft.get("version").longValue(), "reason", "提交显式验证",
						"idempotencyKey", "repair-explicit-submit-" + repairId)));
		long approvalTaskId = pendingApprovalTaskId(submitted.get("approvalSummary").get("id").longValue());
		data(post(approver, "/api/admin/approval-tasks/" + approvalTaskId + "/approve",
				Map.of("version", approvalTaskVersion(approvalTaskId), "comment", "同意执行",
						"idempotencyKey", "repair-explicit-approve-" + repairId)));
		JsonNode ready = data(get(admin, "/api/admin/platform/data-repairs/" + repairId));
		JsonNode executed = data(post(admin, "/api/admin/platform/data-repairs/" + repairId + "/execute",
				Map.of("version", ready.get("version").longValue(),
						"idempotencyKey", "repair-explicit-execute-" + repairId)));
		assertError(post(approver, "/api/admin/platform/data-repairs/" + repairId + "/verify",
				Map.of("version", executed.get("version").longValue(), "comment", "缺少 passed",
						"idempotencyKey", "repair-explicit-missing-passed-" + repairId)),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		JsonNode verified = data(post(approver, "/api/admin/platform/data-repairs/" + repairId + "/verify",
				Map.of("version", executed.get("version").longValue(), "passed", true, "comment", "显式通过",
						"idempotencyKey", "repair-explicit-verify-" + repairId)));
		assertThat(verified.get("status").asText()).isEqualTo("VERIFIED");
	}

	@Test
	void dataRepairDraftUpdateUsesRepairVersionAndStoredTargetConcurrency() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("S34_UPDATE_CUS_" + SEQUENCE.incrementAndGet(), "034 草稿更新客户",
				"034 原联系人", "13800000004", "草稿更新");
		JsonNode draft = data(post(admin, "/api/admin/platform/data-repairs",
				repairPayload("CUSTOMER_PROFILE_CORRECTION_V1", "CUSTOMER", customerId, customerVersion(customerId),
						List.of(change("name", "034 草稿初始修复"))),
				"repair-update-create-" + customerId));
		long repairId = draft.get("id").longValue();
		Map<String, Object> updatePayload = new LinkedHashMap<>();
		updatePayload.put("version", draft.get("version").longValue());
		updatePayload.put("reason", "034 草稿更新原因");
		updatePayload.put("riskSummary", "034 草稿更新风险");
		updatePayload.put("changes", List.of(change("contactPhone", "13900009999")));
		updatePayload.put("idempotencyKey", "repair-update-" + repairId);

		JsonNode updated = data(put(admin, "/api/admin/platform/data-repairs/" + repairId, updatePayload));
		assertThat(updated.get("status").asText()).isEqualTo("DRAFT");
		assertThat(updated.get("version").longValue()).isEqualTo(draft.get("version").longValue() + 1);
		assertThat(updated.get("changes").toString()).contains("13800000004", "13900009999");
		JsonNode replayedUpdate = data(put(admin, "/api/admin/platform/data-repairs/" + repairId, updatePayload));
		assertThat(replayedUpdate.get("id").longValue()).isEqualTo(repairId);
		assertThat(replayedUpdate.get("version").longValue()).isEqualTo(updated.get("version").longValue());
		Map<String, Object> conflictPayload = new LinkedHashMap<>(updatePayload);
		conflictPayload.put("changes", List.of(change("contactPhone", "13900008888")));
		assertError(put(admin, "/api/admin/platform/data-repairs/" + repairId, conflictPayload),
				HttpStatus.CONFLICT, "DOCUMENT_TASK_IDEMPOTENCY_CONFLICT");

		this.jdbcTemplate.update("""
				update mst_customer
				set remark = '034 目标对象并发变化', version = version + 1
				where id = ?
				""", customerId);
		Map<String, Object> staleTargetPayload = new LinkedHashMap<>();
		staleTargetPayload.put("version", updated.get("version").longValue());
		staleTargetPayload.put("reason", "034 目标并发后更新");
		staleTargetPayload.put("riskSummary", "034 目标并发风险");
		staleTargetPayload.put("changes", List.of(change("contactName", "034 并发后联系人")));
		staleTargetPayload.put("idempotencyKey", "repair-update-stale-target-" + repairId);
		assertError(put(admin, "/api/admin/platform/data-repairs/" + repairId, staleTargetPayload),
				HttpStatus.CONFLICT, "DATA_REPAIR_OBJECT_CHANGED");
	}

	private Map<String, Object> repairPayload(String adapterCode, String targetObjectType, long targetObjectId,
			long targetVersion, List<Map<String, Object>> changes) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("adapterCode", adapterCode);
		payload.put("targetObjectType", targetObjectType);
		payload.put("targetObjectId", targetObjectId);
		payload.put("targetVersion", targetVersion);
		payload.put("reason", "034 数据修复测试");
		payload.put("riskSummary", "仅修复白名单资料");
		payload.put("changes", changes);
		return payload;
	}

	private Map<String, Object> change(String fieldName, String afterValue) {
		Map<String, Object> change = new LinkedHashMap<>();
		change.put("fieldName", fieldName);
		change.put("afterValue", afterValue);
		return change;
	}

	private long insertCustomer(String code, String name, String contactName, String contactPhone, String remark) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (
					code, name, contact_name, contact_phone, status, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 'ENABLED', ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, contactName, contactPhone, remark);
	}

	private long customerVersion(long customerId) {
		return this.jdbcTemplate.queryForObject("select version from mst_customer where id = ?", Long.class,
				customerId);
	}

	private String customerName(long customerId) {
		return this.jdbcTemplate.queryForObject("select name from mst_customer where id = ?", String.class,
				customerId);
	}

	private long pendingApprovalTaskId(long approvalId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from platform_approval_task
				where instance_id = ?
				  and status = 'PENDING'
				""", Long.class, approvalId);
	}

	private long approvalTaskVersion(long taskId) {
		return this.jdbcTemplate.queryForObject("select version from platform_approval_task where id = ?",
				Long.class, taskId);
	}

	private long auditDetailCount(String action, String targetType, long targetId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				  and target_type = ?
				  and target_id = ?
				  and detail_json is not null
				""", Long.class, action, targetType, Long.toString(targetId));
	}

	private long eventCount(long repairId, String eventType) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_data_repair_event
				where request_id = ?
				  and event_type = ?
				""", Long.class, repairId, eventType);
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

	private ResponseEntity<String> get(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(session)), String.class);
	}

	private ResponseEntity<String> post(AuthenticatedSession session, String path, Object payload) {
		return this.restTemplate.postForEntity(path, new HttpEntity<>(payload, headers(session)), String.class);
	}

	private ResponseEntity<String> post(AuthenticatedSession session, String path, Object payload,
			String idempotencyKey) {
		HttpHeaders headers = headers(session);
		headers.add("Idempotency-Key", idempotencyKey);
		return this.restTemplate.postForEntity(path, new HttpEntity<>(payload, headers), String.class);
	}

	private ResponseEntity<String> put(AuthenticatedSession session, String path, Object payload) {
		return this.restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(payload, headers(session)),
				String.class);
	}

	private ResponseEntity<byte[]> downloadBytes(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers(session)),
				byte[].class);
	}

	private ResponseEntity<String> uploadAttachment(AuthenticatedSession session, String objectType, long objectId,
			String filename, String contentType, byte[] content, String idempotencyKey) {
		org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
		body.add("objectType", objectType);
		body.add("objectId", Long.toString(objectId));
		body.add("description", "034 数据修复附件");
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
