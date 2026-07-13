package com.qherp.api.system.platform;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.bom.BomEngineeringChangeAdminService;
import com.qherp.api.system.salesproject.SalesProjectContractService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PlatformApprovalService {

	private static final String APPROVAL_TARGET = "APPROVAL_INSTANCE";

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final SalesProjectContractService contractService;

	private final BomEngineeringChangeAdminService engineeringChangeService;

	public PlatformApprovalService(JdbcTemplate jdbcTemplate, AuditService auditService,
			SalesProjectContractService contractService,
			BomEngineeringChangeAdminService engineeringChangeService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.contractService = contractService;
		this.engineeringChangeService = engineeringChangeService;
	}

	@Transactional
	public ApprovalInstanceRecord submitContractActivation(Long contractId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("SALES_PROJECT_CONTRACT_ACTIVATION", contractId, request, operator, servletRequest);
	}

	@Transactional
	public ApprovalInstanceRecord submitEcoApplication(Long ecoId, ApprovalSubmitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return submit("BOM_ECO_APPLICATION", ecoId, request, operator, servletRequest);
	}

	@Transactional(readOnly = true)
	public ApprovalInstanceRecord get(Long id) {
		return instance(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<ApprovalTaskRecord> listTasks(String scope, int page, int pageSize, CurrentUser currentUser) {
		if (currentUser.permissions().isEmpty()) {
			return PageResponse.of(List.of(), page, limit(pageSize), 0);
		}
		String status = "DONE".equalsIgnoreCase(scope) ? "APPROVED" : "PENDING";
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("t.status = ?");
		args.add(status);
		conditions.add("t.candidate_permission_code in (" + placeholders(currentUser.permissions().size()) + ")");
		args.addAll(currentUser.permissions());
		if ("TODO".equalsIgnoreCase(scope) || !hasText(scope)) {
			conditions.add("i.status = 'SUBMITTED'");
		}
		String where = "where " + String.join(" and ", conditions);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_task t
				join platform_approval_instance i on i.id = t.instance_id
				%s
				""".formatted(where), Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<ApprovalTaskRecord> items = this.jdbcTemplate.query("""
				select t.id, t.instance_id, i.scene_code, i.business_object_type, i.business_object_id,
				       i.business_object_no, i.business_object_summary, i.submitted_by_user_id,
				       i.submitted_by_username, i.submitted_at, t.step_no, t.candidate_permission_code,
				       t.status, t.handled_by_user_id, t.handled_by_username, t.handled_at, t.comment,
				       t.created_at, t.updated_at, t.version
				from platform_approval_task t
				join platform_approval_instance i on i.id = t.instance_id
				%s
				order by t.created_at desc, t.id desc
				limit ? offset ?
				""".formatted(where), this::mapTask, pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional
	public ApprovalInstanceRecord approve(Long taskId, ApprovalActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ApprovalTaskState task = lockTask(taskId);
		requireTaskVersion(task, request == null ? null : request.version());
		if (!"PENDING".equals(task.taskStatus()) || !"SUBMITTED".equals(task.instanceStatus())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_STATUS_INVALID);
		}
		if (task.submittedByUserId().equals(operator.id())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_SELF_ACTION_FORBIDDEN);
		}
		requirePermission(operator, task.candidatePermissionCode());
		requireBusinessViewPermission(operator, task.sceneCode());
		BusinessObjectSnapshot current = businessObject(task.sceneCode(), task.businessObjectId());
		if (!current.version().equals(task.businessObjectVersion()) || !"DRAFT".equals(current.status())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_BUSINESS_OBJECT_CHANGED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		int taskUpdated = this.jdbcTemplate.update("""
				update platform_approval_task
				set status = 'APPROVED', handled_by_user_id = ?, handled_by_username = ?, handled_at = ?,
				    comment = ?, updated_at = ?, version = version + 1
				where id = ? and version = ? and status = 'PENDING'
				""", operator.id(), operator.username(), now, trimToNull(request.comment()), now, task.id(),
				task.taskVersion());
		if (taskUpdated == 0) {
			throw new BusinessException(ApiErrorCode.APPROVAL_CONCURRENT_MODIFICATION);
		}
		int instanceUpdated = this.jdbcTemplate.update("""
				update platform_approval_instance
				set status = 'APPROVED', completed_by_username = ?, completed_at = ?, completed_comment = ?,
				    updated_at = ?, version = version + 1
				where id = ? and version = ? and status = 'SUBMITTED'
				""", operator.username(), now, trimToNull(request.comment()), now, task.instanceId(),
				task.instanceVersion());
		if (instanceUpdated == 0) {
			throw new BusinessException(ApiErrorCode.APPROVAL_CONCURRENT_MODIFICATION);
		}
		executeBusinessAction(task, operator, servletRequest);
		recordHistory(task.instanceId(), "APPROVE", operator, request.comment());
		this.auditService.record(operator, "APPROVAL_APPROVE", APPROVAL_TARGET, task.instanceId(),
				task.sceneCode() + ":" + task.businessObjectNo(), servletRequest);
		createMessage(task.submittedByUserId(), "审批已通过", task.businessObjectSummary(), "APPROVAL_DONE",
				task.businessObjectType(), task.businessObjectId());
		return instance(task.instanceId());
	}

	@Transactional
	public ApprovalInstanceRecord reject(Long taskId, ApprovalActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ApprovalTaskState task = lockTask(taskId);
		requireTaskVersion(task, request == null ? null : request.version());
		requireActionComment(request);
		if (!"PENDING".equals(task.taskStatus()) || !"SUBMITTED".equals(task.instanceStatus())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_STATUS_INVALID);
		}
		if (task.submittedByUserId().equals(operator.id())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_SELF_ACTION_FORBIDDEN);
		}
		requirePermission(operator, task.candidatePermissionCode());
		requireBusinessViewPermission(operator, task.sceneCode());
		OffsetDateTime now = OffsetDateTime.now();
		int taskUpdated = this.jdbcTemplate.update("""
				update platform_approval_task
				set status = 'REJECTED', handled_by_user_id = ?, handled_by_username = ?, handled_at = ?,
				    comment = ?, updated_at = ?, version = version + 1
				where id = ? and version = ? and status = 'PENDING'
				""", operator.id(), operator.username(), now, request.comment().trim(), now, task.id(),
				task.taskVersion());
		if (taskUpdated == 0) {
			throw new BusinessException(ApiErrorCode.APPROVAL_CONCURRENT_MODIFICATION);
		}
		updateInstanceTerminal(task.instanceId(), task.instanceVersion(), "REJECTED", operator, request.comment());
		recordHistory(task.instanceId(), "REJECT", operator, request.comment());
		this.auditService.record(operator, "APPROVAL_REJECT", APPROVAL_TARGET, task.instanceId(),
				task.sceneCode() + ":" + task.businessObjectNo(), servletRequest);
		createMessage(task.submittedByUserId(), "审批已驳回", task.businessObjectSummary(), "APPROVAL_DONE",
				task.businessObjectType(), task.businessObjectId());
		return instance(task.instanceId());
	}

	@Transactional
	public ApprovalInstanceRecord withdraw(Long instanceId, ApprovalActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ApprovalInstanceState instance = lockInstance(instanceId);
		requireInstanceVersion(instance, request == null ? null : request.version());
		requireActionComment(request);
		if (!"SUBMITTED".equals(instance.status())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_STATUS_INVALID);
		}
		if (!instance.submittedByUserId().equals(operator.id())) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
		Long handled = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_task
				where instance_id = ?
				and status <> 'PENDING'
				""", Long.class, instance.id());
		if (handled != null && handled > 0) {
			throw new BusinessException(ApiErrorCode.APPROVAL_STATUS_INVALID);
		}
		cancelPendingTasks(instance.id(), operator, request.comment());
		updateInstanceTerminal(instance.id(), instance.version(), "WITHDRAWN", operator, request.comment());
		recordHistory(instance.id(), "WITHDRAW", operator, request.comment());
		this.auditService.record(operator, "APPROVAL_WITHDRAW", APPROVAL_TARGET, instance.id(),
				instance.sceneCode() + ":" + instance.businessObjectNo(), servletRequest);
		createMessage(instance.submittedByUserId(), "审批已撤回", instance.businessObjectSummary(), "APPROVAL_DONE",
				instance.businessObjectType(), instance.businessObjectId());
		return instance(instance.id());
	}

	@Transactional
	public ApprovalInstanceRecord cancel(Long instanceId, ApprovalActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ApprovalInstanceState instance = lockInstance(instanceId);
		requireInstanceVersion(instance, request == null ? null : request.version());
		requireActionComment(request);
		if (!"SUBMITTED".equals(instance.status())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_STATUS_INVALID);
		}
		requirePermission(operator, "platform:approval:cancel");
		cancelPendingTasks(instance.id(), operator, request.comment());
		updateInstanceTerminal(instance.id(), instance.version(), "CANCELLED", operator, request.comment());
		recordHistory(instance.id(), "CANCEL", operator, request.comment());
		this.auditService.record(operator, "APPROVAL_CANCEL", APPROVAL_TARGET, instance.id(),
				instance.sceneCode() + ":" + instance.businessObjectNo(), servletRequest);
		createMessage(instance.submittedByUserId(), "审批已取消", instance.businessObjectSummary(), "APPROVAL_DONE",
				instance.businessObjectType(), instance.businessObjectId());
		return instance(instance.id());
	}

	private ApprovalInstanceRecord submit(String sceneCode, Long objectId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		validateSubmitRequest(request);
		ApprovalDefinition definition = definition(sceneCode);
		BusinessObjectSnapshot object = businessObject(sceneCode, objectId);
		if (!object.version().equals(request.version()) || !"DRAFT".equals(object.status())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_BUSINESS_OBJECT_CHANGED);
		}
		List<ApprovalInstanceRecord> existing = this.jdbcTemplate.query("""
				select id, scene_code, business_object_type, business_object_id, business_object_no,
				       business_object_summary, business_object_version, status, submit_reason,
				       submitted_by_user_id, submitted_by_username, submitted_at, completed_by_username,
				       completed_at, completed_comment, version
				from platform_approval_instance
				where submitted_by_user_id = ?
				and scene_code = ?
				and idempotency_key = ?
				""", this::mapInstance, operator.id(), sceneCode, request.idempotencyKey());
		if (!existing.isEmpty()) {
			return existing.getFirst();
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into platform_approval_instance (
						scene_code, definition_id, definition_version, business_object_type, business_object_id,
						business_object_no, business_object_summary, business_object_version, status,
						submit_reason, idempotency_key, submitted_by_user_id, submitted_by_username,
						submitted_at, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, definition.sceneCode(), definition.id(), definition.definitionVersion(),
					definition.businessObjectType(), object.id(), object.businessObjectNo(), object.summary(),
					object.version(), request.reason().trim(), request.idempotencyKey().trim(), operator.id(),
					operator.username(), now, now, now);
			this.jdbcTemplate.update("""
					insert into platform_approval_task (
						instance_id, step_id, step_no, candidate_permission_code, status, created_at, updated_at
					)
					values (?, ?, ?, ?, 'PENDING', ?, ?)
					""", id, definition.stepId(), definition.stepNo(), definition.candidatePermissionCode(), now, now);
			recordHistory(id, "SUBMIT", operator, request.reason());
			this.auditService.record(operator, "APPROVAL_SUBMIT", APPROVAL_TARGET, id,
					sceneCode + ":" + object.businessObjectNo(), servletRequest);
			notifyCandidateUsers(definition.candidatePermissionCode(), object);
			return instance(id);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.APPROVAL_DUPLICATE_ACTIVE);
		}
	}

	private void executeBusinessAction(ApprovalTaskState task, CurrentUser operator, HttpServletRequest servletRequest) {
		if ("SALES_PROJECT_CONTRACT_ACTIVATION".equals(task.sceneCode())) {
			this.contractService.activateFromApproval(task.businessObjectId(),
					new SalesProjectContractService.VersionedActionRequest(task.businessObjectVersion(), "审批通过"),
					operator, servletRequest);
			return;
		}
		if ("BOM_ECO_APPLICATION".equals(task.sceneCode())) {
			this.engineeringChangeService.applyFromApproval(task.businessObjectId(),
					new BomEngineeringChangeAdminService.VersionRequest(task.businessObjectVersion()), operator,
					servletRequest);
			return;
		}
		throw new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED);
	}

	private ApprovalDefinition definition(String sceneCode) {
		return this.jdbcTemplate.query("""
				select d.id, d.scene_code, d.business_object_type, d.definition_version,
				       s.id as step_id, s.step_no, s.candidate_permission_code
				from platform_approval_definition d
				join platform_approval_definition_step s on s.definition_id = d.id
				where d.scene_code = ?
				and d.status = 'ENABLED'
				order by s.step_no
				limit 1
				""", (rs, rowNum) -> new ApprovalDefinition(rs.getLong("id"), rs.getString("scene_code"),
				rs.getString("business_object_type"), rs.getInt("definition_version"), rs.getLong("step_id"),
				rs.getInt("step_no"), rs.getString("candidate_permission_code")), sceneCode)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.APPROVAL_DEFINITION_NOT_FOUND));
	}

	private BusinessObjectSnapshot businessObject(String sceneCode, Long objectId) {
		if ("SALES_PROJECT_CONTRACT_ACTIVATION".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, contract_no, name, status, version
					from sal_project_contract
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"), rs.getString("contract_no"),
					rs.getString("name"), rs.getString("status"), rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.CONTRACT_NOT_FOUND));
		}
		if ("BOM_ECO_APPLICATION".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, eco_no, change_summary, status, version
					from mfg_bom_engineering_change
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"), rs.getString("eco_no"),
					rs.getString("change_summary"), rs.getString("status"), rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.BOM_ENGINEERING_CHANGE_NOT_FOUND));
		}
		throw new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED);
	}

	private ApprovalInstanceRecord instance(Long id) {
		return this.jdbcTemplate.query("""
				select id, scene_code, business_object_type, business_object_id, business_object_no,
				       business_object_summary, business_object_version, status, submit_reason,
				       submitted_by_user_id, submitted_by_username, submitted_at, completed_by_username,
				       completed_at, completed_comment, version
				from platform_approval_instance
				where id = ?
				""", this::mapInstance, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED));
	}

	private ApprovalTaskState lockTask(Long taskId) {
		return this.jdbcTemplate.query("""
				select t.id, t.instance_id, t.candidate_permission_code, t.status as task_status, t.version as task_version,
				       i.scene_code, i.business_object_type, i.business_object_id, i.business_object_no,
				       i.business_object_summary, i.business_object_version, i.status as instance_status,
				       i.submitted_by_user_id, i.version as instance_version
				from platform_approval_task t
				join platform_approval_instance i on i.id = t.instance_id
				where t.id = ?
				for update of t, i
				""", this::mapTaskState, taskId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED));
	}

	private ApprovalInstanceState lockInstance(Long instanceId) {
		return this.jdbcTemplate.query("""
				select id, scene_code, business_object_type, business_object_id, business_object_no,
				       business_object_summary, business_object_version, status, submitted_by_user_id, version
				from platform_approval_instance
				where id = ?
				for update
				""", (rs, rowNum) -> new ApprovalInstanceState(rs.getLong("id"), rs.getString("scene_code"),
				rs.getString("business_object_type"), rs.getLong("business_object_id"),
				rs.getString("business_object_no"), rs.getString("business_object_summary"),
				rs.getLong("business_object_version"), rs.getString("status"), rs.getLong("submitted_by_user_id"),
				rs.getLong("version")), instanceId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED));
	}

	private ApprovalInstanceRecord mapInstance(ResultSet rs, int rowNum) throws SQLException {
		return new ApprovalInstanceRecord(rs.getLong("id"), rs.getString("scene_code"),
				rs.getString("business_object_type"), rs.getLong("business_object_id"),
				rs.getString("business_object_no"), rs.getString("business_object_summary"),
				rs.getLong("business_object_version"), rs.getString("status"), rs.getString("submit_reason"),
				rs.getLong("submitted_by_user_id"), rs.getString("submitted_by_username"),
				rs.getObject("submitted_at", OffsetDateTime.class), rs.getString("completed_by_username"),
				rs.getObject("completed_at", OffsetDateTime.class), rs.getString("completed_comment"),
				rs.getLong("version"));
	}

	private ApprovalTaskRecord mapTask(ResultSet rs, int rowNum) throws SQLException {
		return new ApprovalTaskRecord(rs.getLong("id"), rs.getLong("instance_id"), rs.getString("scene_code"),
				rs.getString("business_object_type"), rs.getLong("business_object_id"),
				rs.getString("business_object_no"), rs.getString("business_object_summary"),
				rs.getLong("submitted_by_user_id"), rs.getString("submitted_by_username"),
				rs.getObject("submitted_at", OffsetDateTime.class), rs.getInt("step_no"),
				rs.getString("candidate_permission_code"), rs.getString("status"), nullableLong(rs, "handled_by_user_id"),
				rs.getString("handled_by_username"), rs.getObject("handled_at", OffsetDateTime.class),
				rs.getString("comment"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private ApprovalTaskState mapTaskState(ResultSet rs, int rowNum) throws SQLException {
		return new ApprovalTaskState(rs.getLong("id"), rs.getLong("instance_id"),
				rs.getString("candidate_permission_code"), rs.getString("task_status"), rs.getLong("task_version"),
				rs.getString("scene_code"), rs.getString("business_object_type"),
				rs.getLong("business_object_id"), rs.getString("business_object_no"),
				rs.getString("business_object_summary"), rs.getLong("business_object_version"),
				rs.getString("instance_status"), rs.getLong("submitted_by_user_id"), rs.getLong("instance_version"));
	}

	private void validateSubmitRequest(ApprovalSubmitRequest request) {
		if (request == null || request.version() == null || !hasText(request.reason())
				|| !hasText(request.idempotencyKey()) || request.reason().length() > 500
				|| request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void requireTaskVersion(ApprovalTaskState task, Long version) {
		if (version == null || !task.taskVersion().equals(version)) {
			throw new BusinessException(ApiErrorCode.APPROVAL_CONCURRENT_MODIFICATION);
		}
	}

	private void requireInstanceVersion(ApprovalInstanceState instance, Long version) {
		if (version == null || !instance.version().equals(version)) {
			throw new BusinessException(ApiErrorCode.APPROVAL_CONCURRENT_MODIFICATION);
		}
	}

	private void requireActionComment(ApprovalActionRequest request) {
		if (request == null || !hasText(request.comment()) || request.comment().length() > 500) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void updateInstanceTerminal(Long instanceId, Long version, String status, CurrentUser operator,
			String comment) {
		int updated = this.jdbcTemplate.update("""
				update platform_approval_instance
				set status = ?, completed_by_username = ?, completed_at = ?, completed_comment = ?,
				    updated_at = ?, version = version + 1
				where id = ? and version = ? and status = 'SUBMITTED'
				""", status, operator.username(), OffsetDateTime.now(), trimToNull(comment), OffsetDateTime.now(),
				instanceId, version);
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.APPROVAL_CONCURRENT_MODIFICATION);
		}
	}

	private void cancelPendingTasks(Long instanceId, CurrentUser operator, String comment) {
		this.jdbcTemplate.update("""
				update platform_approval_task
				set status = 'CANCELLED', handled_by_user_id = ?, handled_by_username = ?, handled_at = ?,
				    comment = ?, updated_at = ?, version = version + 1
				where instance_id = ?
				and status = 'PENDING'
				""", operator.id(), operator.username(), OffsetDateTime.now(), trimToNull(comment),
				OffsetDateTime.now(), instanceId);
	}

	private void requirePermission(CurrentUser operator, String permissionCode) {
		if (!operator.permissions().contains(permissionCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void requireBusinessViewPermission(CurrentUser operator, String sceneCode) {
		if ("SALES_PROJECT_CONTRACT_ACTIVATION".equals(sceneCode)) {
			requirePermission(operator, "sales:contract:view");
		}
		else if ("BOM_ECO_APPLICATION".equals(sceneCode)) {
			requirePermission(operator, "material:bom-eco:view");
		}
	}

	private void recordHistory(Long instanceId, String action, CurrentUser operator, String comment) {
		this.jdbcTemplate.update("""
				insert into platform_approval_history (
					instance_id, action, operator_user_id, operator_username, comment, created_at
				)
				values (?, ?, ?, ?, ?, ?)
				""", instanceId, action, operator.id(), operator.username(), trimToNull(comment), OffsetDateTime.now());
	}

	private void notifyCandidateUsers(String permissionCode, BusinessObjectSnapshot object) {
		List<Long> userIds = this.jdbcTemplate.query("""
				select distinct u.id
				from sys_user u
				join sys_user_role ur on ur.user_id = u.id
				join sys_role r on r.id = ur.role_id and r.status = 'ENABLED'
				join sys_role_permission rp on rp.role_id = r.id
				join sys_permission p on p.id = rp.permission_id
				where u.status = 'ENABLED'
				and p.code = ?
				""", (rs, rowNum) -> rs.getLong("id"), permissionCode);
		for (Long userId : userIds) {
			createMessage(userId, "新的审批待办", object.summary(), "APPROVAL_TODO", null, object.id());
		}
	}

	private void createMessage(Long recipientUserId, String title, String content, String messageType,
			String relatedObjectType, Long relatedObjectId) {
		this.jdbcTemplate.update("""
				insert into platform_message (
					recipient_user_id, title, content, message_type, status, related_object_type,
					related_object_id, created_at
				)
				values (?, ?, ?, ?, 'UNREAD', ?, ?, ?)
				""", recipientUserId, title, content, messageType, relatedObjectType, relatedObjectId,
				OffsetDateTime.now());
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String placeholders(int size) {
		return String.join(",", java.util.Collections.nCopies(Math.max(size, 1), "?"));
	}

	private static String trimToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record ApprovalSubmitRequest(@NotNull Long version, @NotNull String reason,
			@NotNull String idempotencyKey) {
	}

	public record ApprovalActionRequest(@NotNull Long version, String comment) {
	}

	public record ApprovalInstanceRecord(Long id, String sceneCode, String businessObjectType, Long businessObjectId,
			String businessObjectNo, String businessObjectSummary, Long businessObjectVersion, String status,
			String submitReason, Long submittedByUserId, String submittedByUsername, OffsetDateTime submittedAt,
			String completedByUsername, OffsetDateTime completedAt, String completedComment, Long version) {
	}

	public record ApprovalTaskRecord(Long id, Long instanceId, String sceneCode, String businessObjectType,
			Long businessObjectId, String businessObjectNo, String businessObjectSummary, Long submittedByUserId,
			String submittedByUsername, OffsetDateTime submittedAt, int stepNo, String candidatePermissionCode,
			String status, Long handledByUserId, String handledByUsername, OffsetDateTime handledAt, String comment,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

	private record ApprovalDefinition(Long id, String sceneCode, String businessObjectType, int definitionVersion,
			Long stepId, int stepNo, String candidatePermissionCode) {
	}

	private record BusinessObjectSnapshot(Long id, String businessObjectNo, String summary, String status,
			Long version) {
	}

	private record ApprovalTaskState(Long id, Long instanceId, String candidatePermissionCode, String taskStatus,
			Long taskVersion, String sceneCode, String businessObjectType, Long businessObjectId,
			String businessObjectNo, String businessObjectSummary, Long businessObjectVersion, String instanceStatus,
			Long submittedByUserId, Long instanceVersion) {
	}

	private record ApprovalInstanceState(Long id, String sceneCode, String businessObjectType, Long businessObjectId,
			String businessObjectNo, String businessObjectSummary, Long businessObjectVersion, String status,
			Long submittedByUserId, Long version) {
	}

}
