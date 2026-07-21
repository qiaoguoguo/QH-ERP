package com.qherp.api.system.platform;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PlatformDataRepairService {

	private static final AtomicInteger REQUEST_SEQUENCE = new AtomicInteger();

	private static final DateTimeFormatter REQUEST_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private final JdbcTemplate jdbcTemplate;

	private final PlatformApprovalService approvalService;

	private final AuditService auditService;

	private final ObjectMapper objectMapper;

	public PlatformDataRepairService(JdbcTemplate jdbcTemplate, PlatformApprovalService approvalService,
			AuditService auditService, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.approvalService = approvalService;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public List<DataRepairAdapterRecord> adapters(CurrentUser currentUser) {
		requirePermission(currentUser, "platform:data-repair:view");
		return this.jdbcTemplate.query("""
				select adapter_code, name, target_object_type, description, required_permission_code, version
				from platform_data_repair_adapter_definition
				where status = 'ENABLED'
				order by id
				""", (rs, rowNum) -> new DataRepairAdapterRecord(rs.getString("adapter_code"),
				rs.getString("name"), rs.getString("target_object_type"), allowedFields(rs.getString("adapter_code")),
				rs.getString("description"), rs.getString("required_permission_code"), rs.getLong("version")))
			.stream()
			.filter((adapter) -> currentUser.permissions().contains(adapter.requiredPermissionCode()))
			.toList();
	}

	@Transactional(readOnly = true)
	public PageResponse<DataRepairRecord> list(String status, String targetObjectType, String adapterCode,
			String keyword, int page, int pageSize, CurrentUser currentUser) {
		requirePermission(currentUser, "platform:data-repair:view");
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(status)) {
			conditions.add("status = ?");
			args.add(status.trim().toUpperCase());
		}
		if (hasText(targetObjectType)) {
			conditions.add("target_object_type = ?");
			args.add(targetObjectType.trim().toUpperCase());
		}
		if (hasText(adapterCode)) {
			conditions.add("adapter_code = ?");
			args.add(adapterCode.trim().toUpperCase());
		}
		if (hasText(keyword)) {
			conditions.add("""
					(request_no ilike ? or target_object_no ilike ? or target_object_summary ilike ?
					    or created_by_username ilike ?)
					""");
			String pattern = "%" + keyword.trim() + "%";
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		List<DataRepairRecord> visible = this.jdbcTemplate.query("""
				select *
				from platform_data_repair_request
				%s
				order by created_at desc, id desc
				""".formatted(where), (rs, rowNum) -> toRecord(mapRepair(rs, rowNum), currentUser),
				args.toArray())
			.stream()
			.filter((record) -> currentUser.permissions().contains(adapter(record.adapterCode()).requiredPermissionCode()))
			.toList();
		int from = Math.min(offset(page, pageSize), visible.size());
		int to = Math.min(from + limit(pageSize), visible.size());
		return PageResponse.of(visible.subList(from, to), page, limit(pageSize), visible.size());
	}

	@Transactional
	public DataRepairRecord create(DataRepairCreateRequest request, String idempotencyKey, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		requirePermission(operator, "platform:data-repair:create");
		AdapterDefinition adapter = adapter(request == null ? null : request.adapterCode());
		requirePermission(operator, adapter.requiredPermissionCode());
		String fingerprint = createRequestFingerprint(adapter, request);
		DataRepairRecord existing = existingCreateResult(operator, adapter.adapterCode(), idempotencyKey, fingerprint);
		if (existing != null) {
			return existing;
		}
		ValidatedRepair validated = validateCreateRequest(adapter, request);
		OffsetDateTime now = OffsetDateTime.now();
		String requestNo = nextRequestNo();
		String beforeJson = json(validated.beforeSummary());
		String afterJson = json(validated.afterSummary());
		try {
			Long requestId = this.jdbcTemplate.queryForObject("""
					insert into platform_data_repair_request (
						request_no, adapter_code, target_object_type, target_object_id, target_object_no,
						target_object_summary, target_object_version, status, reason, risk_summary,
						before_summary, after_summary, request_fingerprint, idempotency_key,
						created_by_user_id, created_by_username, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, requestNo, adapter.adapterCode(), adapter.targetObjectType(),
					validated.target().id(), validated.target().objectNo(), validated.target().summary(),
					validated.target().version(), trim(request.reason()), trimToNull(request.riskSummary()), beforeJson,
					afterJson, fingerprint, idempotencyKey.trim(), operator.id(), operator.username(), now, now);
			for (int i = 0; i < validated.changes().size(); i++) {
				ValidatedChange change = validated.changes().get(i);
				this.jdbcTemplate.update("""
						insert into platform_data_repair_change (
							request_id, line_no, field_name, before_value_summary, after_value_summary
						)
						values (?, ?, ?, ?, ?)
						""", requestId, i + 1, change.fieldName(), change.beforeValue(), change.afterValue());
			}
			recordCheck(requestId, "PRECHECK", "PASSED", null, "预检通过", validated.afterSummary());
			recordEvent(requestId, "CREATE", operator, null, "DRAFT", validated.afterSummary());
			this.auditService.recordDetail(operator, "DATA_REPAIR_CREATE", "DATA_REPAIR_REQUEST", requestId,
					requestNo, json(Map.of("target", validated.target().objectNo(), "changes",
							validated.afterSummary())),
					servletRequest);
			return get(requestId, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	@Transactional
	public DataRepairRecord updateDraft(Long id, DataRepairUpdateRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateUpdateRequest(request);
		requirePermission(operator, "platform:data-repair:update");
		String fingerprint = updateFingerprint(id, request);
		DataRepairRecord existing = existingActionResult(operator, "UPDATE", "DATA_REPAIR_REQUEST", id,
				request.idempotencyKey(), fingerprint);
		if (existing != null) {
			return existing;
		}
		RepairRow row = lockRepair(id);
		requireOwner(row, operator);
		requireVersion(row.version(), request.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_STATUS_INVALID);
		}
		AdapterDefinition adapter = adapter(row.adapterCode());
		requirePermission(operator, adapter.requiredPermissionCode());
		ValidatedRepair validated = validateCreateRequest(adapter,
				new DataRepairCreateRequest(row.adapterCode(), row.targetObjectType(), row.targetObjectId(),
						row.targetObjectVersion(), request.reason(), request.riskSummary(), request.changes()));
		this.jdbcTemplate.update("delete from platform_data_repair_change where request_id = ?", id);
		for (int i = 0; i < validated.changes().size(); i++) {
			ValidatedChange change = validated.changes().get(i);
			this.jdbcTemplate.update("""
					insert into platform_data_repair_change (
						request_id, line_no, field_name, before_value_summary, after_value_summary
					)
					values (?, ?, ?, ?, ?)
					""", id, i + 1, change.fieldName(), change.beforeValue(), change.afterValue());
		}
		this.jdbcTemplate.update("""
				update platform_data_repair_request
				set reason = ?, risk_summary = ?, before_summary = cast(? as jsonb), after_summary = cast(? as jsonb),
				    request_fingerprint = ?, updated_at = now(), version = version + 1
				where id = ?
				""", trim(request.reason()), trimToNull(request.riskSummary()), json(validated.beforeSummary()),
				json(validated.afterSummary()), createFingerprint(adapter, validated, request.reason(),
						request.riskSummary()),
				id);
		recordActionIdempotency(operator, "UPDATE", "DATA_REPAIR_REQUEST", id, request.idempotencyKey(),
				fingerprint);
		recordEvent(id, "UPDATE", operator, "DRAFT", "DRAFT", validated.afterSummary());
		this.auditService.recordDetail(operator, "DATA_REPAIR_UPDATE", "DATA_REPAIR_REQUEST", id, row.requestNo(),
				json(validated.afterSummary()), servletRequest);
		return get(id, operator);
	}

	@Transactional
	public DataRepairRecord submit(Long id, DataRepairActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateActionRequest(request);
		requirePermission(operator, "platform:data-repair:submit");
		String fingerprint = actionFingerprint("SUBMIT", id, request);
		DataRepairRecord existing = existingActionResult(operator, "SUBMIT", "DATA_REPAIR_REQUEST", id,
				request.idempotencyKey(), fingerprint);
		if (existing != null) {
			return existing;
		}
		RepairRow row = lockRepair(id);
		requireOwner(row, operator);
		requireVersion(row.version(), request.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_STATUS_INVALID);
		}
		String reason = hasText(request.reason()) ? request.reason() : row.reason();
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService.submitDataRepairExecution(id,
				new PlatformApprovalService.ApprovalSubmitRequest(row.version(), reason, request.idempotencyKey()),
				operator, servletRequest);
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update platform_data_repair_request
				set status = 'PENDING_APPROVAL', approval_instance_id = ?, submitted_by_username = ?,
				    submitted_at = ?, updated_at = now(), version = version + 1
				where id = ? and version = ? and status = 'DRAFT'
				returning version
				""", Long.class, approval.id(), operator.username(), OffsetDateTime.now(), id, row.version());
		this.approvalService.updateBusinessObjectVersion(approval.id(), newVersion);
		recordActionIdempotency(operator, "SUBMIT", "DATA_REPAIR_REQUEST", id, request.idempotencyKey(),
				fingerprint);
		recordEvent(id, "SUBMIT", operator, "DRAFT", "PENDING_APPROVAL", Map.of("approvalId", approval.id()));
		this.auditService.recordDetail(operator, "DATA_REPAIR_SUBMIT", "DATA_REPAIR_REQUEST", id, row.requestNo(),
				json(Map.of("approvalId", approval.id(), "reason", reason)), servletRequest);
		return get(id, operator);
	}

	@Transactional
	public DataRepairRecord execute(Long id, DataRepairActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateActionRequest(request);
		requirePermission(operator, "platform:data-repair:execute");
		String fingerprint = actionFingerprint("EXECUTE", id, request);
		DataRepairRecord existing = existingActionResult(operator, "EXECUTE", "DATA_REPAIR_REQUEST", id,
				request.idempotencyKey(), fingerprint);
		if (existing != null) {
			return existing;
		}
		RepairRow row = lockRepair(id);
		requireVersion(row.version(), request.version());
		if (!"READY_TO_EXECUTE".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_STATUS_INVALID);
		}
		AdapterDefinition adapter = adapter(row.adapterCode());
		requirePermission(operator, adapter.requiredPermissionCode());
		TargetSnapshot target = targetSnapshot(adapter.targetObjectType(), row.targetObjectId());
		if (!target.version().equals(row.targetObjectVersion())) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_OBJECT_CHANGED);
		}
		List<DataRepairChangeRecord> changes = changes(row.id());
		if (changes.isEmpty()) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_EXECUTION_FAILED);
		}
		applyChanges(target, changes, operator);
		this.jdbcTemplate.update("""
				update platform_data_repair_request
				set status = 'EXECUTED', executed_by_username = ?, executed_at = ?, updated_at = now(),
				    version = version + 1
				where id = ? and version = ? and status = 'READY_TO_EXECUTE'
				""", operator.username(), OffsetDateTime.now(), row.id(), row.version());
		recordActionIdempotency(operator, "EXECUTE", "DATA_REPAIR_REQUEST", id, request.idempotencyKey(),
				fingerprint);
		Map<String, Object> detail = Map.of("targetObjectNo", row.targetObjectNo(), "changes", changes);
		recordCheck(id, "EXECUTION", "PASSED", null, "执行通过", detail);
		recordEvent(id, "EXECUTE", operator, "READY_TO_EXECUTE", "EXECUTED", detail);
		this.auditService.recordDetail(operator, "DATA_REPAIR_EXECUTE", "DATA_REPAIR_REQUEST", id, row.requestNo(),
				json(detail), servletRequest);
		return get(id, operator);
	}

	@Transactional
	public DataRepairRecord verify(Long id, DataRepairActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateActionRequest(request);
		if (request.passed() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requirePermission(operator, "platform:data-repair:verify");
		String fingerprint = actionFingerprint("VERIFY", id, request);
		DataRepairRecord existing = existingActionResult(operator, "VERIFY", "DATA_REPAIR_REQUEST", id,
				request.idempotencyKey(), fingerprint);
		if (existing != null) {
			return existing;
		}
		RepairRow row = lockRepair(id);
		requireVersion(row.version(), request.version());
		if (!"EXECUTED".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_STATUS_INVALID);
		}
		if (operator.username().equals(row.executedByUsername())) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_SELF_VERIFY_FORBIDDEN);
		}
		boolean passed = Boolean.TRUE.equals(request.passed());
		String newStatus = passed ? "VERIFIED" : "VERIFY_FAILED";
		this.jdbcTemplate.update("""
				update platform_data_repair_request
				set status = ?, verified_by_username = ?, verified_at = ?, error_summary = ?,
				    updated_at = now(), version = version + 1
				where id = ? and version = ?
				""", newStatus, operator.username(), OffsetDateTime.now(), passed ? null
				: trimToNull(request.comment()), id, row.version());
		recordActionIdempotency(operator, "VERIFY", "DATA_REPAIR_REQUEST", id, request.idempotencyKey(),
				fingerprint);
		Map<String, Object> detail = Map.of("passed", passed, "comment", nullToBlank(request.comment()));
		recordCheck(id, "VERIFICATION", passed ? "PASSED" : "FAILED",
				passed ? null : ApiErrorCode.DATA_REPAIR_VERIFICATION_FAILED.name(),
				passed ? "验证通过" : "验证失败", detail);
		recordEvent(id, "VERIFY", operator, row.status(), newStatus, detail);
		this.auditService.recordDetail(operator, "DATA_REPAIR_VERIFY", "DATA_REPAIR_REQUEST", id, row.requestNo(),
				json(detail), servletRequest);
		return get(id, operator);
	}

	@Transactional
	public DataRepairRecord cancel(Long id, DataRepairActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateActionRequest(request);
		requirePermission(operator, "platform:data-repair:cancel");
		String fingerprint = actionFingerprint("CANCEL", id, request);
		DataRepairRecord existing = existingActionResult(operator, "CANCEL", "DATA_REPAIR_REQUEST", id,
				request.idempotencyKey(), fingerprint);
		if (existing != null) {
			return existing;
		}
		RepairRow row = lockRepair(id);
		requireOwner(row, operator);
		requireVersion(row.version(), request.version());
		if (!"DRAFT".equals(row.status()) && !"PENDING_APPROVAL".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_STATUS_INVALID);
		}
		this.jdbcTemplate.update("""
				update platform_data_repair_request
				set status = 'CANCELLED', cancelled_by_username = ?, cancelled_at = ?, error_summary = ?,
				    updated_at = now(), version = version + 1
				where id = ? and version = ?
				""", operator.username(), OffsetDateTime.now(), trimToNull(request.reason()), id, row.version());
		recordActionIdempotency(operator, "CANCEL", "DATA_REPAIR_REQUEST", id, request.idempotencyKey(),
				fingerprint);
		recordEvent(id, "CANCEL", operator, row.status(), "CANCELLED", Map.of("reason", nullToBlank(request.reason())));
		this.auditService.recordDetail(operator, "DATA_REPAIR_CANCEL", "DATA_REPAIR_REQUEST", id, row.requestNo(),
				json(Map.of("reason", nullToBlank(request.reason()))), servletRequest);
		return get(id, operator);
	}

	@Transactional(readOnly = true)
	public DataRepairRecord get(Long id, CurrentUser currentUser) {
		requirePermission(currentUser, "platform:data-repair:view");
		RepairRow row = repair(id);
		requirePermission(currentUser, adapter(row.adapterCode()).requiredPermissionCode());
		return toRecord(row, currentUser);
	}

	private DataRepairRecord toRecord(RepairRow row, CurrentUser currentUser) {
		PlatformApprovalService.ApprovalInstanceRecord approval = null;
		if (row.approvalInstanceId() != null) {
			try {
				approval = this.approvalService.get(row.approvalInstanceId(), currentUser);
			}
			catch (BusinessException exception) {
				approval = null;
			}
		}
		ApprovalSummary approvalSummary = approval == null ? null
				: new ApprovalSummary(approval.id(), approval.status(), approval.version(), approval.taskId(),
						approval.taskVersion());
		return new DataRepairRecord(row.id(), row.requestNo(), row.adapterCode(), row.targetObjectType(),
				row.targetObjectId(), row.targetObjectNo(), row.targetObjectSummary(), row.targetObjectVersion(),
				row.status(), row.reason(), row.riskSummary(), readJsonMap(row.beforeSummaryJson()),
				readJsonMap(row.afterSummaryJson()), row.requestFingerprint(), changes(row.id()), checks(row.id()),
				events(row.id()), approvalSummary, availableActions(row, currentUser), row.createdByUserId(),
				row.createdByUsername(), row.submittedByUsername(), row.submittedAt(), row.executedByUsername(),
				row.executedAt(), row.verifiedByUsername(), row.verifiedAt(), row.errorSummary(), row.createdAt(),
				row.updatedAt(), row.version());
	}

	private List<String> availableActions(RepairRow row, CurrentUser currentUser) {
		List<String> actions = new ArrayList<>();
		if ("DRAFT".equals(row.status()) && row.createdByUserId().equals(currentUser.id())) {
			if (currentUser.permissions().contains("platform:data-repair:update")) {
				actions.add("UPDATE");
			}
			if (currentUser.permissions().contains("platform:data-repair:submit")) {
				actions.add("SUBMIT");
			}
			if (currentUser.permissions().contains("platform:data-repair:cancel")) {
				actions.add("CANCEL");
			}
		}
		if ("READY_TO_EXECUTE".equals(row.status())
				&& currentUser.permissions().contains("platform:data-repair:execute")) {
			actions.add("EXECUTE");
		}
		if ("EXECUTED".equals(row.status())
				&& currentUser.permissions().contains("platform:data-repair:verify")
				&& !currentUser.username().equals(row.executedByUsername())) {
			actions.add("VERIFY");
		}
		return actions;
	}

	private ValidatedRepair validateCreateRequest(AdapterDefinition adapter, DataRepairCreateRequest request) {
		if (request == null || !hasText(request.targetObjectType()) || request.targetObjectId() == null
				|| request.targetVersion() == null || !hasText(request.reason()) || request.reason().length() > 500
				|| request.riskSummary() != null && request.riskSummary().length() > 500
				|| request.changes() == null || request.changes().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String targetObjectType = request.targetObjectType().trim().toUpperCase();
		if (!adapter.targetObjectType().equals(targetObjectType)) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_ADAPTER_NOT_SUPPORTED);
		}
		TargetSnapshot target = targetSnapshot(targetObjectType, request.targetObjectId());
		if (!target.version().equals(request.targetVersion())) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_OBJECT_CHANGED);
		}
		Map<String, String> before = new LinkedHashMap<>();
		Map<String, String> after = new LinkedHashMap<>();
		List<ValidatedChange> changes = new ArrayList<>();
		List<String> seen = new ArrayList<>();
		for (DataRepairChangeRequest change : request.changes()) {
			if (change == null || !hasText(change.fieldName())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			String field = change.fieldName().trim();
			if (seen.contains(field) || !adapter.allowedFields().contains(field)
					|| !target.fieldColumns().containsKey(field)) {
				throw new BusinessException(ApiErrorCode.DATA_REPAIR_FIELD_NOT_ALLOWED);
			}
			seen.add(field);
			String afterValue = valueSummary(change.afterValue());
			String beforeValue = target.values().get(field);
			before.put(field, beforeValue);
			after.put(field, afterValue);
			changes.add(new ValidatedChange(field, beforeValue, afterValue));
		}
		return new ValidatedRepair(target, before, after, changes);
	}

	private void applyChanges(TargetSnapshot target, List<DataRepairChangeRecord> changes, CurrentUser operator) {
		List<String> assignments = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		for (DataRepairChangeRecord change : changes) {
			String column = target.fieldColumns().get(change.fieldName());
			if (!hasText(column)) {
				throw new BusinessException(ApiErrorCode.DATA_REPAIR_FIELD_NOT_ALLOWED);
			}
			assignments.add(column + " = ?");
			args.add(change.afterValueSummary());
		}
		assignments.add("updated_by = ?");
		args.add(operator.username());
		assignments.add("updated_at = ?");
		args.add(OffsetDateTime.now());
		assignments.add("version = version + 1");
		args.add(target.id());
		args.add(target.version());
		int updated = this.jdbcTemplate.update("""
				update %s
				set %s
				where id = ? and version = ?
				""".formatted(target.tableName(), String.join(", ", assignments)), args.toArray());
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_OBJECT_CHANGED);
		}
	}

	private RepairRow repair(Long id) {
		return this.jdbcTemplate.query("""
				select id, request_no, adapter_code, target_object_type, target_object_id, target_object_no,
				       target_object_summary, target_object_version, status, reason, risk_summary,
				       before_summary::text as before_summary, after_summary::text as after_summary,
				       request_fingerprint, approval_instance_id, created_by_user_id, created_by_username,
				       submitted_by_username, submitted_at, executed_by_username, executed_at,
				       verified_by_username, verified_at, error_summary, created_at, updated_at, version
				from platform_data_repair_request
				where id = ?
				""", this::mapRepair, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.DATA_REPAIR_NOT_FOUND));
	}

	private RepairRow lockRepair(Long id) {
		return this.jdbcTemplate.query("""
				select id, request_no, adapter_code, target_object_type, target_object_id, target_object_no,
				       target_object_summary, target_object_version, status, reason, risk_summary,
				       before_summary::text as before_summary, after_summary::text as after_summary,
				       request_fingerprint, approval_instance_id, created_by_user_id, created_by_username,
				       submitted_by_username, submitted_at, executed_by_username, executed_at,
				       verified_by_username, verified_at, error_summary, created_at, updated_at, version
				from platform_data_repair_request
				where id = ?
				for update
				""", this::mapRepair, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.DATA_REPAIR_NOT_FOUND));
	}

	private RepairRow mapRepair(ResultSet rs, int rowNum) throws SQLException {
		return new RepairRow(rs.getLong("id"), rs.getString("request_no"), rs.getString("adapter_code"),
				rs.getString("target_object_type"), rs.getLong("target_object_id"), rs.getString("target_object_no"),
				rs.getString("target_object_summary"), rs.getLong("target_object_version"), rs.getString("status"),
				rs.getString("reason"), rs.getString("risk_summary"), rs.getString("before_summary"),
				rs.getString("after_summary"), rs.getString("request_fingerprint"), nullableLong(rs,
						"approval_instance_id"),
				rs.getLong("created_by_user_id"), rs.getString("created_by_username"),
				rs.getString("submitted_by_username"), rs.getObject("submitted_at", OffsetDateTime.class),
				rs.getString("executed_by_username"), rs.getObject("executed_at", OffsetDateTime.class),
				rs.getString("verified_by_username"), rs.getObject("verified_at", OffsetDateTime.class),
				rs.getString("error_summary"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private TargetSnapshot targetSnapshot(String objectType, Long objectId) {
		return switch (objectType) {
			case "CUSTOMER" -> partnerSnapshot("CUSTOMER", "mst_customer", objectId);
			case "SUPPLIER" -> partnerSnapshot("SUPPLIER", "mst_supplier", objectId);
			case "MATERIAL" -> materialSnapshot(objectId);
			default -> throw new BusinessException(ApiErrorCode.DATA_REPAIR_ADAPTER_NOT_SUPPORTED);
		};
	}

	private TargetSnapshot partnerSnapshot(String objectType, String tableName, Long objectId) {
		return this.jdbcTemplate.query("""
				select id, code, name, contact_name, contact_phone, remark, version
				from %s
				where id = ?
				""".formatted(tableName), (rs, rowNum) -> {
			Map<String, String> values = new LinkedHashMap<>();
			values.put("name", rs.getString("name"));
			values.put("contactName", rs.getString("contact_name"));
			values.put("contactPhone", rs.getString("contact_phone"));
			values.put("remark", rs.getString("remark"));
			Map<String, String> columns = Map.of("name", "name", "contactName", "contact_name",
					"contactPhone", "contact_phone", "remark", "remark");
			return new TargetSnapshot(rs.getLong("id"), objectType, rs.getString("code"), rs.getString("name"),
					rs.getLong("version"), tableName, values, columns);
		}, objectId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.MASTER_DATA_NOT_FOUND));
	}

	private TargetSnapshot materialSnapshot(Long objectId) {
		return this.jdbcTemplate.query("""
				select id, code, name, specification, remark, version
				from mst_material
				where id = ?
				""", (rs, rowNum) -> {
			Map<String, String> values = new LinkedHashMap<>();
			values.put("name", rs.getString("name"));
			values.put("specification", rs.getString("specification"));
			values.put("remark", rs.getString("remark"));
			Map<String, String> columns = Map.of("name", "name", "specification", "specification", "remark",
					"remark");
			return new TargetSnapshot(rs.getLong("id"), "MATERIAL", rs.getString("code"), rs.getString("name"),
					rs.getLong("version"), "mst_material", values, columns);
		}, objectId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.MASTER_DATA_NOT_FOUND));
	}

	private AdapterDefinition adapter(String adapterCode) {
		if (!hasText(adapterCode)) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_ADAPTER_NOT_SUPPORTED);
		}
		return this.jdbcTemplate.query("""
				select adapter_code, target_object_type, required_permission_code
				from platform_data_repair_adapter_definition
				where adapter_code = ?
				and status = 'ENABLED'
				""", (rs, rowNum) -> new AdapterDefinition(rs.getString("adapter_code"),
				rs.getString("target_object_type"), allowedFields(rs.getString("adapter_code")),
				rs.getString("required_permission_code")), adapterCode.trim())
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.DATA_REPAIR_ADAPTER_NOT_SUPPORTED));
	}

	private List<String> allowedFields(String adapterCode) {
		return this.jdbcTemplate.queryForList("""
				select jsonb_array_elements_text(allowed_fields)
				from platform_data_repair_adapter_definition
				where adapter_code = ?
				""", String.class, adapterCode);
	}

	private List<DataRepairChangeRecord> changes(Long requestId) {
		return this.jdbcTemplate.query("""
				select field_name, before_value_summary, after_value_summary
				from platform_data_repair_change
				where request_id = ?
				order by line_no
				""", (rs, rowNum) -> new DataRepairChangeRecord(rs.getString("field_name"),
				rs.getString("before_value_summary"), rs.getString("after_value_summary")), requestId);
	}

	private List<DataRepairCheckRecord> checks(Long requestId) {
		return this.jdbcTemplate.query("""
				select check_type, status, code, message, detail_json::text as detail_json, created_at
				from platform_data_repair_check
				where request_id = ?
				order by id
				""", (rs, rowNum) -> new DataRepairCheckRecord(rs.getString("check_type"),
				rs.getString("status"), rs.getString("code"), rs.getString("message"),
				readJsonMap(rs.getString("detail_json")), rs.getObject("created_at", OffsetDateTime.class)),
				requestId);
	}

	private List<DataRepairEventRecord> events(Long requestId) {
		return this.jdbcTemplate.query("""
				select event_type, operator_username, status_before, status_after, detail_json::text as detail_json,
				       created_at
				from platform_data_repair_event
				where request_id = ?
				order by id
				""", (rs, rowNum) -> new DataRepairEventRecord(rs.getString("event_type"),
				rs.getString("operator_username"), rs.getString("status_before"), rs.getString("status_after"),
				readJsonMap(rs.getString("detail_json")), rs.getObject("created_at", OffsetDateTime.class)),
				requestId);
	}

	private void recordCheck(Long requestId, String checkType, String status, String code, String message,
			Object detail) {
		this.jdbcTemplate.update("""
				insert into platform_data_repair_check (
					request_id, check_type, status, code, message, detail_json
				)
				values (?, ?, ?, ?, ?, cast(? as jsonb))
				""", requestId, checkType, status, code, message, json(detail));
	}

	private void recordEvent(Long requestId, String eventType, CurrentUser operator, String statusBefore,
			String statusAfter, Object detail) {
		this.jdbcTemplate.update("""
				insert into platform_data_repair_event (
					request_id, event_type, operator_user_id, operator_username, status_before, status_after,
					detail_json
				)
				values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
				""", requestId, eventType, operator.id(), operator.username(), statusBefore, statusAfter,
				json(detail));
	}

	private DataRepairRecord existingCreateResult(CurrentUser operator, String adapterCode, String idempotencyKey,
			String fingerprint) {
		List<ExistingRepair> existing = this.jdbcTemplate.query("""
				select id, request_fingerprint
				from platform_data_repair_request
				where created_by_user_id = ?
				and adapter_code = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingRepair(rs.getLong("id"), rs.getString("request_fingerprint")),
				operator.id(), adapterCode, idempotencyKey.trim());
		if (existing.isEmpty()) {
			return null;
		}
		ExistingRepair repair = existing.getFirst();
		if (!fingerprint.equals(repair.requestFingerprint())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
		return get(repair.id(), operator);
	}

	private String createRequestFingerprint(AdapterDefinition adapter, DataRepairCreateRequest request) {
		if (request == null || !hasText(request.targetObjectType()) || request.targetObjectId() == null
				|| request.targetVersion() == null || !hasText(request.reason()) || request.reason().length() > 500
				|| request.riskSummary() != null && request.riskSummary().length() > 500
				|| request.changes() == null || request.changes().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String targetObjectType = request.targetObjectType().trim().toUpperCase();
		if (!adapter.targetObjectType().equals(targetObjectType)) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_ADAPTER_NOT_SUPPORTED);
		}
		StringBuilder builder = new StringBuilder();
		builder.append(adapter.adapterCode()).append('|')
			.append(targetObjectType).append('|')
			.append(request.targetObjectId()).append('|')
			.append(request.targetVersion()).append('|')
			.append(trim(request.reason())).append('|')
			.append(nullToBlank(trimToNull(request.riskSummary())));
		for (DataRepairChangeRequest change : request.changes()) {
			if (change == null || !hasText(change.fieldName())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			builder.append('|')
				.append(change.fieldName().trim())
				.append('=')
				.append(nullToBlank(valueSummary(change.afterValue())));
		}
		return sha256(builder.toString());
	}

	private DataRepairRecord existingActionResult(CurrentUser operator, String action, String targetType, Long targetId,
			String idempotencyKey, String fingerprint) {
		List<ExistingRepair> existing = this.jdbcTemplate.query("""
				select result_id, request_fingerprint
				from platform_action_idempotency
				where operator_user_id = ?
				and action = ?
				and target_type = ?
				and target_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingRepair(rs.getLong("result_id"), rs.getString("request_fingerprint")),
				operator.id(), action, targetType, targetId, idempotencyKey.trim());
		if (existing.isEmpty()) {
			return null;
		}
		ExistingRepair repair = existing.getFirst();
		if (!fingerprint.equals(repair.requestFingerprint())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
		return get(repair.id(), operator);
	}

	private void recordActionIdempotency(CurrentUser operator, String action, String targetType, Long targetId,
			String idempotencyKey, String fingerprint) {
		try {
			this.jdbcTemplate.update("""
					insert into platform_action_idempotency (
						operator_user_id, action, target_type, target_id, idempotency_key, request_fingerprint,
						result_type, result_id
					)
					values (?, ?, ?, ?, ?, ?, 'DATA_REPAIR_REQUEST', ?)
					""", operator.id(), action, targetType, targetId, idempotencyKey.trim(), fingerprint, targetId);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	private String createFingerprint(AdapterDefinition adapter, ValidatedRepair validated, String reason,
			String riskSummary) {
		StringBuilder builder = new StringBuilder();
		builder.append(adapter.adapterCode()).append('|')
			.append(validated.target().objectType()).append('|')
			.append(validated.target().id()).append('|')
			.append(validated.target().version()).append('|')
			.append(trim(reason)).append('|')
			.append(nullToBlank(trimToNull(riskSummary)));
		for (ValidatedChange change : validated.changes()) {
			builder.append('|').append(change.fieldName()).append('=').append(nullToBlank(change.afterValue()));
		}
		return sha256(builder.toString());
	}

	private String actionFingerprint(String action, Long id, DataRepairActionRequest request) {
		return sha256(action + "|" + id + "|" + request.version() + "|" + nullToBlank(trimToNull(request.reason()))
				+ "|" + nullToBlank(trimToNull(request.comment())) + "|" + request.passed());
	}

	private String updateFingerprint(Long id, DataRepairUpdateRequest request) {
		StringBuilder builder = new StringBuilder();
		builder.append("UPDATE|").append(id).append('|').append(request.version()).append('|')
			.append(trim(request.reason())).append('|')
			.append(nullToBlank(trimToNull(request.riskSummary())));
		for (DataRepairChangeRequest change : request.changes()) {
			if (change == null || !hasText(change.fieldName())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			builder.append('|')
				.append(change.fieldName().trim())
				.append('=')
				.append(nullToBlank(valueSummary(change.afterValue())));
		}
		return sha256(builder.toString());
	}

	private void validateActionRequest(DataRepairActionRequest request) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| request.idempotencyKey().length() > 120
				|| request.reason() != null && request.reason().length() > 500
				|| request.comment() != null && request.comment().length() > 500) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateUpdateRequest(DataRepairUpdateRequest request) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| request.idempotencyKey().length() > 120 || !hasText(request.reason())
				|| request.reason().length() > 500
				|| request.riskSummary() != null && request.riskSummary().length() > 500
				|| request.changes() == null || request.changes().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (!hasText(idempotencyKey) || idempotencyKey.length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void requireOwner(RepairRow row, CurrentUser operator) {
		if (!row.createdByUserId().equals(operator.id())) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void requireVersion(Long currentVersion, Long requestedVersion) {
		if (requestedVersion == null || !currentVersion.equals(requestedVersion)) {
			throw new BusinessException(ApiErrorCode.DATA_REPAIR_OBJECT_CHANGED);
		}
	}

	private void requirePermission(CurrentUser operator, String permissionCode) {
		if (operator == null || !operator.permissions().contains(permissionCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private Map<String, Object> readJsonMap(String payload) {
		if (!hasText(payload)) {
			return Map.of();
		}
		try {
			return this.objectMapper.readValue(payload, Map.class);
		}
		catch (RuntimeException exception) {
			return Map.of();
		}
	}

	private String json(Object value) {
		try {
			return this.objectMapper.writeValueAsString(value == null ? Map.of() : value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private String valueSummary(Object value) {
		if (value == null) {
			return null;
		}
		String stringValue = value.toString().trim();
		if (stringValue.length() > 500) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return stringValue;
	}

	private static String nextRequestNo() {
		return "DREP" + OffsetDateTime.now().format(REQUEST_NO_FORMATTER)
				+ String.format("%03d", Math.floorMod(REQUEST_SEQUENCE.incrementAndGet(), 1000));
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String trim(String value) {
		if (!hasText(value)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value.trim();
	}

	private static String trimToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private static String nullToBlank(String value) {
		return value == null ? "" : value;
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record DataRepairAdapterRecord(String adapterCode, String name, String targetObjectType,
			List<String> allowedFields, String description, String requiredPermissionCode, Long version) {
	}

	public record DataRepairCreateRequest(@NotBlank String adapterCode, @NotBlank String targetObjectType,
			@NotNull Long targetObjectId, @NotNull Long targetVersion, @NotBlank String reason, String riskSummary,
			List<DataRepairChangeRequest> changes) {
	}

	public record DataRepairUpdateRequest(@NotNull Long version, @NotBlank String reason, String riskSummary,
			List<DataRepairChangeRequest> changes, String idempotencyKey) {
	}

	public record DataRepairChangeRequest(@NotBlank String fieldName, Object afterValue) {
	}

	public record DataRepairActionRequest(@NotNull Long version, String reason, String comment,
			String idempotencyKey, Boolean passed) {
	}

	public record DataRepairRecord(Long id, String requestNo, String adapterCode, String targetObjectType,
			Long targetObjectId, String targetObjectNo, String targetObjectSummary, Long targetObjectVersion,
			String status, String reason, String riskSummary, Map<String, Object> beforeSummary,
			Map<String, Object> afterSummary, String requestFingerprint, List<DataRepairChangeRecord> changes,
			List<DataRepairCheckRecord> checks, List<DataRepairEventRecord> events, ApprovalSummary approvalSummary,
			List<String> availableActions, Long createdByUserId, String createdByUsername,
			String submittedByUsername, OffsetDateTime submittedAt, String executedByUsername,
			OffsetDateTime executedAt, String verifiedByUsername, OffsetDateTime verifiedAt, String errorSummary,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

	public record DataRepairChangeRecord(String fieldName, String beforeValueSummary, String afterValueSummary) {
	}

	public record DataRepairCheckRecord(String checkType, String status, String code, String message,
			Map<String, Object> detail, OffsetDateTime createdAt) {
	}

	public record DataRepairEventRecord(String eventType, String operatorUsername, String statusBefore,
			String statusAfter, Map<String, Object> detail, OffsetDateTime createdAt) {
	}

	public record ApprovalSummary(Long id, String status, Long version, Long taskId, Long taskVersion) {
	}

	private record AdapterDefinition(String adapterCode, String targetObjectType, List<String> allowedFields,
			String requiredPermissionCode) {
	}

	private record ValidatedRepair(TargetSnapshot target, Map<String, String> beforeSummary,
			Map<String, String> afterSummary, List<ValidatedChange> changes) {
	}

	private record ValidatedChange(String fieldName, String beforeValue, String afterValue) {
	}

	private record TargetSnapshot(Long id, String objectType, String objectNo, String summary, Long version,
			String tableName, Map<String, String> values, Map<String, String> fieldColumns) {
	}

	private record ExistingRepair(Long id, String requestFingerprint) {
	}

	private record RepairRow(Long id, String requestNo, String adapterCode, String targetObjectType,
			Long targetObjectId, String targetObjectNo, String targetObjectSummary, Long targetObjectVersion,
			String status, String reason, String riskSummary, String beforeSummaryJson, String afterSummaryJson,
			String requestFingerprint, Long approvalInstanceId, Long createdByUserId, String createdByUsername,
			String submittedByUsername, OffsetDateTime submittedAt, String executedByUsername,
			OffsetDateTime executedAt, String verifiedByUsername, OffsetDateTime verifiedAt, String errorSummary,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

}
