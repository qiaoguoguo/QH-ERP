package com.qherp.api.system.platform;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.bom.BomEngineeringChangeAdminService;
import com.qherp.api.system.financialclose.FinancialCloseService;
import com.qherp.api.system.gl.GeneralLedgerVoucherService;
import com.qherp.api.system.inventory.InventoryStage023AdminService;
import com.qherp.api.system.procurement.ProcurementRequisitionService;
import com.qherp.api.system.procurement.ProcurementSourcingService;
import com.qherp.api.system.procurement.ProcurementAdminService;
import com.qherp.api.system.projectcost.ProjectCostAdjustmentService;
import com.qherp.api.system.sales.SalesAdminService;
import com.qherp.api.system.sales.SalesFulfillmentService;
import com.qherp.api.system.sales.SalesQuoteService;
import com.qherp.api.system.salesproject.SalesProjectContractService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class PlatformApprovalService {

	private static final String APPROVAL_TARGET = "APPROVAL_INSTANCE";

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final SalesProjectContractService contractService;

	private final BomEngineeringChangeAdminService engineeringChangeService;

	private final InventoryStage023AdminService inventoryStage023AdminService;

	private final ProcurementRequisitionService procurementRequisitionService;

	private final ProcurementSourcingService procurementSourcingService;

	private final ProcurementAdminService procurementAdminService;

	private final SalesAdminService salesAdminService;

	private final SalesQuoteService salesQuoteService;

	private final SalesFulfillmentService salesFulfillmentService;

	private final ProjectCostAdjustmentService projectCostAdjustmentService;

	private final GeneralLedgerVoucherService generalLedgerVoucherService;

	private final FinancialCloseService financialCloseService;

	public PlatformApprovalService(JdbcTemplate jdbcTemplate, AuditService auditService,
			SalesProjectContractService contractService,
			BomEngineeringChangeAdminService engineeringChangeService,
			@Lazy InventoryStage023AdminService inventoryStage023AdminService,
			@Lazy ProcurementRequisitionService procurementRequisitionService,
			@Lazy ProcurementSourcingService procurementSourcingService,
			@Lazy ProcurementAdminService procurementAdminService,
			@Lazy SalesAdminService salesAdminService,
			@Lazy SalesQuoteService salesQuoteService,
			@Lazy SalesFulfillmentService salesFulfillmentService,
			@Lazy ProjectCostAdjustmentService projectCostAdjustmentService,
			@Lazy GeneralLedgerVoucherService generalLedgerVoucherService,
			@Lazy FinancialCloseService financialCloseService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.contractService = contractService;
		this.engineeringChangeService = engineeringChangeService;
		this.inventoryStage023AdminService = inventoryStage023AdminService;
		this.procurementRequisitionService = procurementRequisitionService;
		this.procurementSourcingService = procurementSourcingService;
		this.procurementAdminService = procurementAdminService;
		this.salesAdminService = salesAdminService;
		this.salesQuoteService = salesQuoteService;
		this.salesFulfillmentService = salesFulfillmentService;
		this.projectCostAdjustmentService = projectCostAdjustmentService;
		this.generalLedgerVoucherService = generalLedgerVoucherService;
		this.financialCloseService = financialCloseService;
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

	@Transactional
	public ApprovalInstanceRecord submitInventoryOwnershipConversion(Long conversionId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("INVENTORY_OWNERSHIP_CONVERSION_POST", conversionId, request, operator, servletRequest);
	}

	@Transactional
	public ApprovalInstanceRecord submitInventoryStocktake(Long stocktakeId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("INVENTORY_STOCKTAKE_VARIANCE_POST", stocktakeId, request, operator, servletRequest);
	}

	@Transactional
	public ApprovalInstanceRecord submitInventoryValuationAdjustment(Long adjustmentId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("INVENTORY_VALUATION_ADJUSTMENT_POST", adjustmentId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitProcurementRequisition(Long requisitionId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("PROCUREMENT_REQUISITION_APPROVAL", requisitionId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitProcurementPriceAgreementActivation(Long agreementId,
			ApprovalSubmitRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("PROCUREMENT_PRICE_AGREEMENT_ACTIVATION", agreementId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitProcurementOrderException(Long orderId,
			ApprovalSubmitRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("PROCUREMENT_ORDER_EXCEPTION_CONFIRM", orderId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitSalesQuoteApproval(Long quoteId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("SALES_QUOTE_APPROVAL", quoteId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitSalesOrderCreditOverride(Long orderId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("SALES_ORDER_CREDIT_OVERRIDE", orderId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitSalesOrderChangeApproval(Long changeId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("SALES_ORDER_CHANGE_APPROVAL", changeId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitSalesOrderChangeCreditOverride(Long changeId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("SALES_ORDER_CHANGE_CREDIT_OVERRIDE", changeId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitSalesOrderShortClose(Long orderId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("SALES_ORDER_SHORT_CLOSE", orderId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitProjectCostAdjustment(Long adjustmentId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("PROJECT_COST_ADJUSTMENT_CONFIRM", adjustmentId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitGlVoucherPost(Long voucherId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("GL_VOUCHER_POST", voucherId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord submitFinancialPeriodReopen(Long requestId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return submit("FINANCIAL_PERIOD_REOPEN", requestId, request, operator, servletRequest);
	}

	public ApprovalInstanceRecord idempotentSubmitResult(String sceneCode, Long objectId,
			ApprovalSubmitRequest request, CurrentUser operator) {
		validateSubmitRequest(request);
		String fingerprint = approvalFingerprint(sceneCode, objectId, request);
		List<ExistingApproval> existing = this.jdbcTemplate.query("""
				select id, request_fingerprint
				from platform_approval_instance
				where submitted_by_user_id = ?
				and scene_code = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingApproval(rs.getLong("id"), rs.getString("request_fingerprint")),
				operator.id(), sceneCode, request.idempotencyKey());
		if (existing.isEmpty()) {
			return null;
		}
		ExistingApproval approval = existing.getFirst();
		if (!fingerprint.equals(approval.requestFingerprint())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_IDEMPOTENCY_CONFLICT);
		}
		return toInstanceRecord(instanceBase(approval.id()), operator);
	}

	public void updateBusinessObjectVersion(Long instanceId, Long version) {
		this.jdbcTemplate.update("""
				update platform_approval_instance
				set business_object_version = ?, updated_at = ?
				where id = ?
				""", version, OffsetDateTime.now(), instanceId);
	}

	@Transactional(readOnly = true)
	public ApprovalInstanceRecord get(Long id, CurrentUser currentUser) {
		ApprovalInstanceBase instance = instanceBase(id);
		requireDetailAccess(instance, currentUser);
		return toInstanceRecord(instance, currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<ApprovalTaskRecord> listTasks(String scope, int page, int pageSize, CurrentUser currentUser) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		String normalizedScope = hasText(scope) ? scope.toUpperCase() : "TODO";
		if ("DONE".equals(normalizedScope)) {
			conditions.add("t.handled_by_user_id = ?");
			args.add(currentUser.id());
			conditions.add("t.status <> 'PENDING'");
		}
		else if ("STARTED".equals(normalizedScope)) {
			conditions.add("i.submitted_by_user_id = ?");
			args.add(currentUser.id());
		}
		else {
			conditions.add("t.status = 'PENDING'");
			conditions.add("i.status = 'SUBMITTED'");
			conditions.add("i.submitted_by_user_id <> ?");
			args.add(currentUser.id());
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		List<ApprovalTaskProjection> visible = this.jdbcTemplate.query("""
				select t.id, t.instance_id, i.scene_code, i.business_object_type, i.business_object_id,
				       i.business_object_no, i.business_object_summary, i.submitted_by_user_id,
				       i.submitted_by_username, i.submitted_at, t.step_no, t.candidate_permission_code,
				       t.status, t.handled_by_user_id, t.handled_by_username, t.handled_at, t.comment,
				       t.created_at, t.updated_at, t.version
				from platform_approval_task t
				join platform_approval_instance i on i.id = t.instance_id
				%s
				order by t.created_at desc, t.id desc
				""".formatted(where), this::mapTaskProjection, args.toArray())
			.stream()
			.filter((task) -> canSeeTask(task, normalizedScope, currentUser))
			.toList();
		int from = Math.min(offset(page, pageSize), visible.size());
		int to = Math.min(from + limit(pageSize), visible.size());
		List<ApprovalTaskRecord> items = visible.subList(from, to)
			.stream()
			.map((task) -> toTaskRecord(task, currentUser))
			.toList();
		return PageResponse.of(items, page, limit(pageSize), visible.size());
	}

	@Transactional
	public ApprovalInstanceRecord approve(Long taskId, ApprovalActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateActionRequest(request, false);
		ApprovalTaskState task = lockTask(taskId);
		ApprovalInstanceRecord idempotentResult = actionIdempotencyResult("APPROVE", "TASK", task.id(), request,
				operator);
		if (idempotentResult != null) {
			return idempotentResult;
		}
		requireTaskVersion(task, request.version());
		if (!"PENDING".equals(task.taskStatus()) || !"SUBMITTED".equals(task.instanceStatus())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_STATUS_INVALID);
		}
		if (task.submittedByUserId().equals(operator.id())) {
			if ("GL_VOUCHER_POST".equals(task.sceneCode())) {
				throw new BusinessException(ApiErrorCode.GL_APPROVAL_SELF_FORBIDDEN);
			}
			throw new BusinessException(ApiErrorCode.APPROVAL_SELF_ACTION_FORBIDDEN);
		}
		requirePermission(operator, task.candidatePermissionCode());
		requireBusinessViewPermission(operator, task.sceneCode());
		BusinessObjectSnapshot current = businessObject(task.sceneCode(), task.businessObjectId());
		if (!current.version().equals(task.businessObjectVersion()) || !"DRAFT".equals(current.status())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_BUSINESS_OBJECT_CHANGED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		recordActionIdempotency("APPROVE", "TASK", task.id(), request, task.instanceId(), operator);
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
		return toInstanceRecord(instanceBase(task.instanceId()), operator);
	}

	@Transactional
	public ApprovalInstanceRecord reject(Long taskId, ApprovalActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateActionRequest(request, true);
		ApprovalTaskState task = lockTask(taskId);
		ApprovalInstanceRecord idempotentResult = actionIdempotencyResult("REJECT", "TASK", task.id(), request,
				operator);
		if (idempotentResult != null) {
			return idempotentResult;
		}
		requireTaskVersion(task, request.version());
		if (!"PENDING".equals(task.taskStatus()) || !"SUBMITTED".equals(task.instanceStatus())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_STATUS_INVALID);
		}
		if (task.submittedByUserId().equals(operator.id())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_SELF_ACTION_FORBIDDEN);
		}
		requirePermission(operator, task.candidatePermissionCode());
		requireBusinessViewPermission(operator, task.sceneCode());
		OffsetDateTime now = OffsetDateTime.now();
		recordActionIdempotency("REJECT", "TASK", task.id(), request, task.instanceId(), operator);
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
		reopenBusinessObjectAfterTerminal(task.sceneCode(), task.businessObjectId(), operator);
		recordHistory(task.instanceId(), "REJECT", operator, request.comment());
		this.auditService.record(operator, "APPROVAL_REJECT", APPROVAL_TARGET, task.instanceId(),
				task.sceneCode() + ":" + task.businessObjectNo(), servletRequest);
		createMessage(task.submittedByUserId(), "审批已驳回", task.businessObjectSummary(), "APPROVAL_DONE",
				task.businessObjectType(), task.businessObjectId());
		return toInstanceRecord(instanceBase(task.instanceId()), operator);
	}

	@Transactional
	public ApprovalInstanceRecord withdraw(Long instanceId, ApprovalActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateActionRequest(request, true);
		ApprovalInstanceState instance = lockInstance(instanceId);
		ApprovalInstanceRecord idempotentResult = actionIdempotencyResult("WITHDRAW", "INSTANCE", instance.id(),
				request, operator);
		if (idempotentResult != null) {
			return idempotentResult;
		}
		requireInstanceVersion(instance, request.version());
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
		recordActionIdempotency("WITHDRAW", "INSTANCE", instance.id(), request, instance.id(), operator);
		cancelPendingTasks(instance.id(), operator, request.comment());
		updateInstanceTerminal(instance.id(), instance.version(), "WITHDRAWN", operator, request.comment());
		reopenBusinessObjectAfterTerminal(instance.sceneCode(), instance.businessObjectId(), operator);
		recordHistory(instance.id(), "WITHDRAW", operator, request.comment());
		this.auditService.record(operator, "APPROVAL_WITHDRAW", APPROVAL_TARGET, instance.id(),
				instance.sceneCode() + ":" + instance.businessObjectNo(), servletRequest);
		createMessage(instance.submittedByUserId(), "审批已撤回", instance.businessObjectSummary(), "APPROVAL_DONE",
				instance.businessObjectType(), instance.businessObjectId());
		return toInstanceRecord(instanceBase(instance.id()), operator);
	}

	@Transactional
	public ApprovalInstanceRecord cancel(Long instanceId, ApprovalActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateActionRequest(request, true);
		ApprovalInstanceState instance = lockInstance(instanceId);
		ApprovalInstanceRecord idempotentResult = actionIdempotencyResult("CANCEL", "INSTANCE", instance.id(), request,
				operator);
		if (idempotentResult != null) {
			return idempotentResult;
		}
		requireInstanceVersion(instance, request.version());
		if (!"SUBMITTED".equals(instance.status())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_STATUS_INVALID);
		}
		requirePermission(operator, "platform:approval:cancel");
		recordActionIdempotency("CANCEL", "INSTANCE", instance.id(), request, instance.id(), operator);
		cancelPendingTasks(instance.id(), operator, request.comment());
		updateInstanceTerminal(instance.id(), instance.version(), "CANCELLED", operator, request.comment());
		reopenBusinessObjectAfterTerminal(instance.sceneCode(), instance.businessObjectId(), operator);
		recordHistory(instance.id(), "CANCEL", operator, request.comment());
		this.auditService.record(operator, "APPROVAL_CANCEL", APPROVAL_TARGET, instance.id(),
				instance.sceneCode() + ":" + instance.businessObjectNo(), servletRequest);
		createMessage(instance.submittedByUserId(), "审批已取消", instance.businessObjectSummary(), "APPROVAL_DONE",
				instance.businessObjectType(), instance.businessObjectId());
		return toInstanceRecord(instanceBase(instance.id()), operator);
	}

	private ApprovalInstanceRecord submit(String sceneCode, Long objectId, ApprovalSubmitRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		validateSubmitRequest(request);
		ApprovalDefinition definition = definition(sceneCode);
		BusinessObjectSnapshot object = businessObject(sceneCode, objectId);
		if (!object.version().equals(request.version()) || !"DRAFT".equals(object.status())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_BUSINESS_OBJECT_CHANGED);
		}
		String fingerprint = approvalFingerprint(sceneCode, objectId, request);
		List<ExistingApproval> existing = this.jdbcTemplate.query("""
				select id, request_fingerprint
				from platform_approval_instance
				where submitted_by_user_id = ?
				and scene_code = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingApproval(rs.getLong("id"), rs.getString("request_fingerprint")),
				operator.id(), sceneCode, request.idempotencyKey());
		if (!existing.isEmpty()) {
			ExistingApproval approval = existing.getFirst();
			if (!fingerprint.equals(approval.requestFingerprint())) {
				throw new BusinessException(ApiErrorCode.APPROVAL_IDEMPOTENCY_CONFLICT);
			}
			return toInstanceRecord(instanceBase(approval.id()), operator);
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into platform_approval_instance (
						scene_code, definition_id, definition_version, business_object_type, business_object_id,
						business_object_no, business_object_summary, business_object_version, status,
						submit_reason, idempotency_key, request_fingerprint, submitted_by_user_id, submitted_by_username,
						submitted_at, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, definition.sceneCode(), definition.id(), definition.definitionVersion(),
					definition.businessObjectType(), object.id(), object.businessObjectNo(), object.summary(),
					object.version(), request.reason().trim(), request.idempotencyKey().trim(), fingerprint, operator.id(),
					operator.username(), now, now, now);
			this.jdbcTemplate.update("""
					insert into platform_approval_task (
						instance_id, step_id, step_no, candidate_permission_code, status, created_at, updated_at
					)
					values (?, ?, ?, ?, 'PENDING', ?, ?)
					""", id, definition.stepId(), definition.stepNo(), definition.candidatePermissionCode(), now, now);
			snapshotAttachments(id, definition.businessObjectType(), object.id());
			recordHistory(id, "SUBMIT", operator, request.reason());
			this.auditService.record(operator, "APPROVAL_SUBMIT", APPROVAL_TARGET, id,
					sceneCode + ":" + object.businessObjectNo(), servletRequest);
			notifyCandidateUsers(definition.sceneCode(), definition.candidatePermissionCode(),
					definition.businessObjectType(), object, operator.id());
			return toInstanceRecord(instanceBase(id), operator);
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
		if ("INVENTORY_OWNERSHIP_CONVERSION_POST".equals(task.sceneCode())) {
			this.inventoryStage023AdminService.postOwnershipConversionFromApproval(task.businessObjectId(), operator);
			return;
		}
		if ("INVENTORY_STOCKTAKE_VARIANCE_POST".equals(task.sceneCode())) {
			this.inventoryStage023AdminService.postStocktakeFromApproval(task.businessObjectId(), operator);
			return;
		}
		if ("INVENTORY_VALUATION_ADJUSTMENT_POST".equals(task.sceneCode())) {
			this.inventoryStage023AdminService.postValuationAdjustmentFromApproval(task.businessObjectId(), operator);
			return;
		}
		if ("PROCUREMENT_REQUISITION_APPROVAL".equals(task.sceneCode())) {
			this.procurementRequisitionService.approveFromApproval(task.businessObjectId(),
					task.businessObjectVersion(), operator);
			return;
		}
		if ("PROCUREMENT_PRICE_AGREEMENT_ACTIVATION".equals(task.sceneCode())) {
			this.procurementSourcingService.activatePriceAgreementFromApproval(task.businessObjectId(),
					task.businessObjectVersion(), operator);
			return;
		}
		if ("PROCUREMENT_ORDER_EXCEPTION_CONFIRM".equals(task.sceneCode())) {
			this.procurementAdminService.confirmOrderFromExceptionApproval(task.businessObjectId(),
					task.businessObjectVersion(), operator, servletRequest);
			return;
		}
		if ("SALES_QUOTE_APPROVAL".equals(task.sceneCode())) {
			this.salesQuoteService.approveFromApproval(task.businessObjectId(), task.businessObjectVersion(),
					operator, servletRequest);
			return;
		}
		if ("SALES_ORDER_CREDIT_OVERRIDE".equals(task.sceneCode())) {
			this.salesAdminService.confirmOrderFromCreditOverride(task.businessObjectId(), task.businessObjectVersion(),
					task.instanceId(), operator, servletRequest);
			return;
		}
		if ("SALES_ORDER_CHANGE_APPROVAL".equals(task.sceneCode())
				|| "SALES_ORDER_CHANGE_CREDIT_OVERRIDE".equals(task.sceneCode())) {
			this.salesFulfillmentService.applyOrderChangeFromApproval(task.businessObjectId(),
					task.businessObjectVersion(), task.sceneCode(), operator, servletRequest);
			return;
		}
		if ("SALES_ORDER_SHORT_CLOSE".equals(task.sceneCode())) {
			this.salesAdminService.closeOrderFromShortCloseApproval(task.businessObjectId(),
					task.businessObjectVersion(), operator, servletRequest);
			return;
		}
		if ("PROJECT_COST_ADJUSTMENT_CONFIRM".equals(task.sceneCode())) {
			this.projectCostAdjustmentService.confirmFromApproval(task.businessObjectId(),
					task.businessObjectVersion(), operator, servletRequest);
			return;
		}
		if ("GL_VOUCHER_POST".equals(task.sceneCode())) {
			this.generalLedgerVoucherService.postFromApproval(task.businessObjectId(), task.businessObjectVersion(),
					operator, servletRequest);
			return;
		}
		if ("FINANCIAL_PERIOD_REOPEN".equals(task.sceneCode())) {
			this.financialCloseService.applyReopenFromApproval(task.businessObjectId(), task.businessObjectVersion(),
					operator, servletRequest);
			return;
		}
		throw new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED);
	}

	private void reopenBusinessObjectAfterTerminal(String sceneCode, Long objectId, CurrentUser operator) {
		if ("INVENTORY_OWNERSHIP_CONVERSION_POST".equals(sceneCode)
				|| "INVENTORY_STOCKTAKE_VARIANCE_POST".equals(sceneCode)
				|| "INVENTORY_VALUATION_ADJUSTMENT_POST".equals(sceneCode)) {
			this.inventoryStage023AdminService.reopenAfterApprovalTerminal(sceneCode, objectId, operator);
		}
		if ("PROCUREMENT_REQUISITION_APPROVAL".equals(sceneCode)) {
			this.procurementRequisitionService.reopenAfterApprovalTerminal(objectId, operator);
		}
		if ("PROCUREMENT_PRICE_AGREEMENT_ACTIVATION".equals(sceneCode)) {
			this.procurementSourcingService.reopenPriceAgreementAfterApprovalTerminal(objectId, operator);
		}
		if ("PROCUREMENT_ORDER_EXCEPTION_CONFIRM".equals(sceneCode)) {
			this.procurementAdminService.reopenOrderAfterExceptionApprovalTerminal(objectId, operator);
		}
		if ("SALES_QUOTE_APPROVAL".equals(sceneCode)) {
			this.salesQuoteService.reopenAfterApprovalTerminal(objectId, operator);
		}
		if ("SALES_ORDER_CHANGE_APPROVAL".equals(sceneCode)
				|| "SALES_ORDER_CHANGE_CREDIT_OVERRIDE".equals(sceneCode)) {
			this.salesFulfillmentService.reopenOrderChangeAfterApprovalTerminal(objectId, operator);
		}
		if ("PROJECT_COST_ADJUSTMENT_CONFIRM".equals(sceneCode)) {
			this.projectCostAdjustmentService.reopenAfterApprovalTerminal(objectId, operator);
		}
		if ("GL_VOUCHER_POST".equals(sceneCode)) {
			this.generalLedgerVoucherService.reopenAfterApprovalTerminal(objectId, operator);
		}
		if ("FINANCIAL_PERIOD_REOPEN".equals(sceneCode)) {
			this.financialCloseService.reopenAfterApprovalTerminal(objectId, operator);
		}
	}

	private void snapshotAttachments(Long instanceId, String objectType, Long objectId) {
		this.jdbcTemplate.update("""
				insert into platform_approval_attachment_snapshot (
					instance_id, attachment_id, file_id, file_name, content_type, file_size, sha256,
					uploaded_by_username, uploaded_at
				)
				select ?, a.id, a.file_id, f.original_filename, f.content_type, f.size_bytes, f.sha256,
				       a.created_by_username, a.created_at
				from platform_business_attachment a
				join platform_file_object f on f.id = a.file_id
				where a.object_type = ?
				and a.object_id = ?
				and a.status = 'AVAILABLE'
				and f.status = 'AVAILABLE'
				order by a.created_at, a.id
				""", instanceId, objectType, objectId);
	}

	private String approvalFingerprint(String sceneCode, Long objectId, ApprovalSubmitRequest request) {
		return sha256(sceneCode + "|" + objectId + "|" + request.version() + "|" + request.reason().trim());
	}

	private ApprovalInstanceRecord actionIdempotencyResult(String action, String resourceType, Long resourceId,
			ApprovalActionRequest request, CurrentUser operator) {
		List<ExistingActionApproval> existing = this.jdbcTemplate.query("""
				select result_instance_id, request_fingerprint
				from platform_approval_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingActionApproval(rs.getLong("result_instance_id"),
				rs.getString("request_fingerprint")), operator.id(), action, resourceType, resourceId,
				request.idempotencyKey().trim());
		if (existing.isEmpty()) {
			return null;
		}
		ExistingActionApproval approval = existing.getFirst();
		if (!approval.requestFingerprint().equals(actionFingerprint(action, resourceType, resourceId, request))) {
			throw new BusinessException(ApiErrorCode.APPROVAL_IDEMPOTENCY_CONFLICT);
		}
		return toInstanceRecord(instanceBase(approval.resultInstanceId()), operator);
	}

	private void recordActionIdempotency(String action, String resourceType, Long resourceId,
			ApprovalActionRequest request, Long resultInstanceId, CurrentUser operator) {
		try {
			this.jdbcTemplate.update("""
					insert into platform_approval_action_idempotency (
						operator_user_id, action, resource_type, resource_id, resource_version, comment,
						idempotency_key, request_fingerprint, result_instance_id
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), action, resourceType, resourceId, request.version(),
					trimToNull(request.comment()), request.idempotencyKey().trim(),
					actionFingerprint(action, resourceType, resourceId, request), resultInstanceId);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.APPROVAL_IDEMPOTENCY_CONFLICT);
		}
	}

	private String actionFingerprint(String action, String resourceType, Long resourceId, ApprovalActionRequest request) {
		return sha256(action + "|" + resourceType + "|" + resourceId + "|" + request.version() + "|"
				+ nullToBlank(trimToNull(request.comment())));
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
		if ("INVENTORY_OWNERSHIP_CONVERSION_POST".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, conversion_no, reason,
					       case when status in ('DRAFT', 'SUBMITTED') then 'DRAFT' else status end as approval_status,
					       version
					from inv_ownership_conversion
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("conversion_no"), rs.getString("reason"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_NOT_FOUND));
		}
		if ("INVENTORY_STOCKTAKE_VARIANCE_POST".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, stocktake_no, reason,
					       case when status in ('DRAFT', 'COUNTING', 'RECONCILED', 'SUBMITTED')
					            then 'DRAFT' else status end as approval_status,
					       version
					from inv_stocktake
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("stocktake_no"), rs.getString("reason"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_NOT_FOUND));
		}
		if ("INVENTORY_VALUATION_ADJUSTMENT_POST".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, adjustment_no, reason,
					       case when status in ('DRAFT', 'SUBMITTED') then 'DRAFT' else status end as approval_status,
					       version
					from inv_valuation_adjustment
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("adjustment_no"), rs.getString("reason"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_NOT_FOUND));
		}
		if ("PROCUREMENT_REQUISITION_APPROVAL".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, requisition_no, purpose,
					       case when status in ('DRAFT', 'SUBMITTED') then 'DRAFT' else status end as approval_status,
					       version
					from proc_purchase_requisition
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("requisition_no"), rs.getString("purpose"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_NOT_FOUND));
		}
		if ("PROCUREMENT_PRICE_AGREEMENT_ACTIVATION".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, agreement_no, agreement_no as summary,
					       case when status in ('DRAFT', 'SUBMITTED') then 'DRAFT' else status end as approval_status,
					       version
					from proc_price_agreement
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("agreement_no"), rs.getString("summary"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_AGREEMENT_NOT_FOUND));
		}
		if ("PROCUREMENT_ORDER_EXCEPTION_CONFIRM".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, order_no, coalesce(exception_reason, public_direct_reason, remark, order_no) as summary,
					       case when status in ('DRAFT') then 'DRAFT' else status end as approval_status,
					       version
					from proc_purchase_order
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("order_no"), rs.getString("summary"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_NOT_FOUND));
		}
		if ("SALES_QUOTE_APPROVAL".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, quote_no, coalesce(remark, quote_no) as summary,
					       case when status = 'DRAFT' then 'DRAFT' else status end as approval_status,
					       version
					from sal_sales_quote
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("quote_no"), rs.getString("summary"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_QUOTE_NOT_FOUND));
		}
		if ("SALES_ORDER_CREDIT_OVERRIDE".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, order_no, coalesce(remark, order_no) as summary,
					       case when status = 'DRAFT' then 'DRAFT' else status end as approval_status,
					       version
					from sal_sales_order
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("order_no"), rs.getString("summary"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_ORDER_NOT_FOUND));
		}
		if ("SALES_ORDER_CHANGE_APPROVAL".equals(sceneCode)
				|| "SALES_ORDER_CHANGE_CREDIT_OVERRIDE".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, change_no, reason as summary,
					       case when status = 'DRAFT' then 'DRAFT' else status end as approval_status,
					       version
					from sal_sales_order_change
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("change_no"), rs.getString("summary"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_NOT_FOUND));
		}
		if ("SALES_ORDER_SHORT_CLOSE".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, order_no, coalesce(close_reason, remark, order_no) as summary,
					       case when status in ('CONFIRMED', 'PARTIALLY_SHIPPED', 'SHIPPED')
					            then 'DRAFT' else status end as approval_status,
					       version
					from sal_sales_order
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("order_no"), rs.getString("summary"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_ORDER_NOT_FOUND));
		}
		if ("PROJECT_COST_ADJUSTMENT_CONFIRM".equals(sceneCode)) {
			return this.jdbcTemplate.query("""
					select id, adjustment_no, reason,
					       case when status in ('DRAFT', 'SUBMITTED') then 'DRAFT' else status end as approval_status,
					       version
					from prj_cost_adjustment
					where id = ?
					""", (rs, rowNum) -> new BusinessObjectSnapshot(rs.getLong("id"),
					rs.getString("adjustment_no"), rs.getString("reason"), rs.getString("approval_status"),
					rs.getLong("version")), objectId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_COST_PROJECT_INVALID));
		}
		if ("GL_VOUCHER_POST".equals(sceneCode)) {
			GeneralLedgerVoucherService.ApprovalSnapshot snapshot = this.generalLedgerVoucherService
				.approvalSnapshot(objectId);
			return new BusinessObjectSnapshot(snapshot.id(), snapshot.no(), snapshot.summary(),
					snapshot.approvalStatus(), snapshot.version());
		}
		if ("FINANCIAL_PERIOD_REOPEN".equals(sceneCode)) {
			FinancialCloseService.ApprovalSnapshot snapshot = this.financialCloseService.approvalSnapshot(objectId);
			return new BusinessObjectSnapshot(snapshot.id(), snapshot.no(), snapshot.summary(),
					snapshot.approvalStatus(), snapshot.version());
		}
		throw new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED);
	}

	private ApprovalInstanceBase instanceBase(Long id) {
		return this.jdbcTemplate.query("""
				select id, scene_code, business_object_type, business_object_id, business_object_no,
				       business_object_summary, business_object_version, status, submit_reason,
				       submitted_by_user_id, submitted_by_username, submitted_at, completed_by_username,
				       completed_at, completed_comment, version
				from platform_approval_instance
				where id = ?
				""", this::mapInstanceBase, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED));
	}

	private ApprovalInstanceRecord toInstanceRecord(ApprovalInstanceBase instance, CurrentUser currentUser) {
		CurrentApprovalTask currentTask = currentApprovalTask(instance, currentUser);
		return new ApprovalInstanceRecord(instance.id(), instance.sceneCode(), instance.businessObjectType(),
				instance.businessObjectId(), instance.businessObjectNo(), visibleBusinessObjectSummary(instance,
						currentUser),
				instance.businessObjectVersion(), instance.status(), instance.submitReason(), instance.submittedByUserId(),
				instance.submittedByUsername(), instance.submittedAt(), instance.completedByUsername(),
				instance.completedAt(), instance.completedComment(), instance.version(),
				currentTask == null ? null : currentTask.taskId(),
				currentTask == null ? null : currentTask.version(),
				instanceAvailableActions(instance, currentUser), steps(instance.id()), histories(instance.id()),
				attachmentSnapshots(instance.id()));
	}

	private ApprovalTaskRecord toTaskRecord(ApprovalTaskProjection task, CurrentUser currentUser) {
		return new ApprovalTaskRecord(task.instanceId(), task.id(), task.sceneCode(), task.businessObjectType(),
				task.businessObjectId(), task.businessObjectNo(), visibleBusinessObjectSummary(task, currentUser),
				task.submittedByUserId(), task.submittedByUsername(), task.submittedAt(), task.stepNo(),
				task.candidatePermissionCode(), task.status(), task.handledByUserId(), task.handledByUsername(),
				task.handledAt(), task.comment(), task.createdAt(), task.updatedAt(), task.version(),
				taskAvailableActions(task, currentUser));
	}

	private String visibleBusinessObjectSummary(ApprovalInstanceBase instance, CurrentUser currentUser) {
		if ("GL_VOUCHER_POST".equals(instance.sceneCode()) && !hasSourceViewPermission(currentUser)) {
			return "来源凭证";
		}
		return instance.businessObjectSummary();
	}

	private String visibleBusinessObjectSummary(ApprovalTaskProjection task, CurrentUser currentUser) {
		if ("GL_VOUCHER_POST".equals(task.sceneCode()) && !hasSourceViewPermission(currentUser)) {
			return "来源凭证";
		}
		return task.businessObjectSummary();
	}

	private boolean hasSourceViewPermission(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("gl:source:view");
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

	private ApprovalInstanceBase mapInstanceBase(ResultSet rs, int rowNum) throws SQLException {
		return new ApprovalInstanceBase(rs.getLong("id"), rs.getString("scene_code"),
				rs.getString("business_object_type"), rs.getLong("business_object_id"),
				rs.getString("business_object_no"), rs.getString("business_object_summary"),
				rs.getLong("business_object_version"), rs.getString("status"), rs.getString("submit_reason"),
				rs.getLong("submitted_by_user_id"), rs.getString("submitted_by_username"),
				rs.getObject("submitted_at", OffsetDateTime.class), rs.getString("completed_by_username"),
				rs.getObject("completed_at", OffsetDateTime.class), rs.getString("completed_comment"),
				rs.getLong("version"));
	}

	private ApprovalTaskProjection mapTaskProjection(ResultSet rs, int rowNum) throws SQLException {
		return new ApprovalTaskProjection(rs.getLong("id"), rs.getLong("instance_id"), rs.getString("scene_code"),
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

	private boolean canSeeTask(ApprovalTaskProjection task, String scope, CurrentUser currentUser) {
		if (!hasBusinessViewPermission(currentUser, task.sceneCode())) {
			return false;
		}
		if ("DONE".equals(scope)) {
			return currentUser.id().equals(task.handledByUserId());
		}
		if ("STARTED".equals(scope)) {
			return currentUser.id().equals(task.submittedByUserId());
		}
		return "PENDING".equals(task.status()) && currentUser.permissions().contains(task.candidatePermissionCode())
				&& !currentUser.id().equals(task.submittedByUserId());
	}

	private void requireDetailAccess(ApprovalInstanceBase instance, CurrentUser currentUser) {
		if (!hasBusinessViewPermission(currentUser, instance.sceneCode())) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
		if ("GL_VOUCHER_POST".equals(instance.sceneCode())
				&& currentUser.permissions().contains("platform:approval:view")
				&& currentUser.permissions().contains("gl:voucher:view")) {
			return;
		}
		if (currentUser.id().equals(instance.submittedByUserId()) || hasHandledTask(instance.id(), currentUser.id())
				|| hasCandidateRelation(instance, currentUser) || canCancel(instance, currentUser)) {
			return;
		}
		throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
	}

	private List<String> instanceAvailableActions(ApprovalInstanceBase instance, CurrentUser currentUser) {
		if (!"SUBMITTED".equals(instance.status())) {
			return List.of();
		}
		List<String> actions = new ArrayList<>();
		if (hasCandidateRelation(instance, currentUser)) {
			actions.add("APPROVE");
			actions.add("REJECT");
		}
		if (currentUser.id().equals(instance.submittedByUserId()) && pendingTasksUntouched(instance.id())) {
			actions.add("WITHDRAW");
		}
		if (canCancel(instance, currentUser)) {
			actions.add("CANCEL");
		}
		return actions;
	}

	private List<String> taskAvailableActions(ApprovalTaskProjection task, CurrentUser currentUser) {
		if ("PENDING".equals(task.status()) && currentUser.permissions().contains(task.candidatePermissionCode())
				&& !currentUser.id().equals(task.submittedByUserId()) && hasBusinessViewPermission(currentUser,
						task.sceneCode())) {
			return List.of("APPROVE", "REJECT");
		}
		return List.of();
	}

	private boolean hasCandidateRelation(ApprovalInstanceBase instance, CurrentUser currentUser) {
		if (!"SUBMITTED".equals(instance.status()) || currentUser.id().equals(instance.submittedByUserId())) {
			return false;
		}
		if (currentUser.permissions().isEmpty()) {
			return false;
		}
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_task
				where instance_id = ?
				and status = 'PENDING'
				and candidate_permission_code in (%s)
				""".formatted(placeholders(currentUser.permissions().size())), Long.class,
				approvalTaskArgs(instance.id(), currentUser.permissions()).toArray());
		return count != null && count > 0;
	}

	private boolean hasHandledTask(Long instanceId, Long userId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_task
				where instance_id = ?
				and handled_by_user_id = ?
				and status <> 'PENDING'
				""", Long.class, instanceId, userId);
		return count != null && count > 0;
	}

	private boolean pendingTasksUntouched(Long instanceId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_task
				where instance_id = ?
				and status <> 'PENDING'
				""", Long.class, instanceId);
		return count == null || count == 0;
	}

	private boolean canCancel(ApprovalInstanceBase instance, CurrentUser currentUser) {
		return "SUBMITTED".equals(instance.status()) && currentUser.permissions().contains("platform:approval:cancel");
	}

	private CurrentApprovalTask currentApprovalTask(ApprovalInstanceBase instance, CurrentUser currentUser) {
		if (!"SUBMITTED".equals(instance.status()) || currentUser.id().equals(instance.submittedByUserId())
				|| currentUser.permissions().isEmpty() || !hasBusinessViewPermission(currentUser, instance.sceneCode())) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select id, version
				from platform_approval_task
				where instance_id = ?
				and status = 'PENDING'
				and candidate_permission_code in (%s)
				order by step_no, id
				limit 1
				""".formatted(placeholders(currentUser.permissions().size())),
				(rs, rowNum) -> new CurrentApprovalTask(rs.getLong("id"), rs.getLong("version")),
				approvalTaskArgs(instance.id(), currentUser.permissions()).toArray())
			.stream()
			.findFirst()
			.orElse(null);
	}

	private List<Object> approvalTaskArgs(Long instanceId, List<String> permissions) {
		List<Object> args = new ArrayList<>();
		args.add(instanceId);
		args.addAll(permissions);
		return args;
	}

	private List<ApprovalStepRecord> steps(Long instanceId) {
		return this.jdbcTemplate.query("""
				select t.id, s.name as step_name, t.status, t.candidate_permission_code, t.handled_by_username,
				       t.handled_at, t.version
				from platform_approval_task t
				join platform_approval_definition_step s on s.id = t.step_id
				where t.instance_id = ?
				order by t.step_no, t.id
				""", (rs, rowNum) -> new ApprovalStepRecord(rs.getString("step_name"), rs.getString("status"),
				rs.getLong("id"), rs.getLong("version"), rs.getString("candidate_permission_code"),
				rs.getString("handled_by_username"), rs.getObject("handled_at", OffsetDateTime.class)),
				instanceId);
	}

	private List<ApprovalHistoryRecord> histories(Long instanceId) {
		return this.jdbcTemplate.query("""
				select action, operator_username, comment, created_at
				from platform_approval_history
				where instance_id = ?
				order by created_at, id
				""", (rs, rowNum) -> new ApprovalHistoryRecord(rs.getString("action"),
				rs.getString("operator_username"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getString("comment"), resultStatus(rs.getString("action"))), instanceId);
	}

	private List<AttachmentSnapshotRecord> attachmentSnapshots(Long instanceId) {
		return this.jdbcTemplate.query("""
				select attachment_id, file_id, file_name, content_type, file_size, sha256,
				       uploaded_by_username, uploaded_at
				from platform_approval_attachment_snapshot
				where instance_id = ?
				order by id
				""", (rs, rowNum) -> new AttachmentSnapshotRecord(rs.getLong("attachment_id"),
				rs.getLong("file_id"), rs.getString("file_name"), rs.getString("content_type"),
				rs.getLong("file_size"), rs.getString("sha256"), rs.getString("uploaded_by_username"),
				rs.getObject("uploaded_at", OffsetDateTime.class)), instanceId);
	}

	private void validateSubmitRequest(ApprovalSubmitRequest request) {
		if (request == null || request.version() == null || !hasText(request.reason())
				|| !hasText(request.idempotencyKey()) || request.reason().length() > 500
				|| request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateActionRequest(ApprovalActionRequest request, boolean commentRequired) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.comment() != null && request.comment().length() > 500) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (commentRequired && !hasText(request.comment())) {
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
		if (!hasBusinessViewPermission(operator, sceneCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private boolean hasBusinessViewPermission(CurrentUser operator, String sceneCode) {
		if ("SALES_PROJECT_CONTRACT_ACTIVATION".equals(sceneCode)) {
			return operator.permissions().contains("sales:contract:view");
		}
		if ("BOM_ECO_APPLICATION".equals(sceneCode)) {
			return operator.permissions().contains("material:bom-eco:view");
		}
		if ("INVENTORY_OWNERSHIP_CONVERSION_POST".equals(sceneCode)
				|| "INVENTORY_STOCKTAKE_VARIANCE_POST".equals(sceneCode)) {
			return operator.permissions().contains("inventory:balance:view");
		}
		if ("INVENTORY_VALUATION_ADJUSTMENT_POST".equals(sceneCode)) {
			return operator.permissions().contains("inventory:valuation:view");
		}
		if ("PROCUREMENT_REQUISITION_APPROVAL".equals(sceneCode)) {
			return operator.permissions().contains("procurement:requisition:view");
		}
		if ("PROCUREMENT_PRICE_AGREEMENT_ACTIVATION".equals(sceneCode)) {
			return operator.permissions().contains("procurement:price-agreement:view");
		}
		if ("PROCUREMENT_ORDER_EXCEPTION_CONFIRM".equals(sceneCode)) {
			return operator.permissions().contains("procurement:order:view");
		}
		if ("SALES_QUOTE_APPROVAL".equals(sceneCode)) {
			return operator.permissions().contains("sales:quote:view");
		}
		if ("SALES_ORDER_CREDIT_OVERRIDE".equals(sceneCode)) {
			return operator.permissions().contains("sales:order:view");
		}
		if ("SALES_ORDER_CHANGE_APPROVAL".equals(sceneCode)
				|| "SALES_ORDER_CHANGE_CREDIT_OVERRIDE".equals(sceneCode)) {
			return operator.permissions().contains("sales:order-change:view");
		}
		if ("SALES_ORDER_SHORT_CLOSE".equals(sceneCode)) {
			return operator.permissions().contains("sales:order:view");
		}
		if ("PROJECT_COST_ADJUSTMENT_CONFIRM".equals(sceneCode)) {
			return operator.permissions().contains("cost:project-cost-adjustment:view");
		}
		if ("GL_VOUCHER_POST".equals(sceneCode)) {
			return operator.permissions().contains("gl:voucher:view");
		}
		if ("FINANCIAL_PERIOD_REOPEN".equals(sceneCode)) {
			return operator.permissions().contains("financial-close:period:view");
		}
		return false;
	}

	private void recordHistory(Long instanceId, String action, CurrentUser operator, String comment) {
		this.jdbcTemplate.update("""
				insert into platform_approval_history (
					instance_id, action, operator_user_id, operator_username, comment, created_at
				)
				values (?, ?, ?, ?, ?, ?)
				""", instanceId, action, operator.id(), operator.username(), trimToNull(comment), OffsetDateTime.now());
	}

	private void notifyCandidateUsers(String sceneCode, String permissionCode, String businessObjectType,
			BusinessObjectSnapshot object, Long submitterUserId) {
		String viewPermission = businessViewPermission(sceneCode);
		List<Long> userIds = this.jdbcTemplate.query("""
				select distinct u.id
				from sys_user u
				join sys_user_role ur on ur.user_id = u.id
				join sys_role r on r.id = ur.role_id and r.status = 'ENABLED'
				join sys_role_permission rp on rp.role_id = r.id
				join sys_permission p on p.id = rp.permission_id
				where u.status = 'ENABLED'
				and p.code = ?
				and u.id <> ?
				and exists (
					select 1
					from sys_user_role ur2
					join sys_role r2 on r2.id = ur2.role_id and r2.status = 'ENABLED'
					join sys_role_permission rp2 on rp2.role_id = r2.id
					join sys_permission p2 on p2.id = rp2.permission_id
					where ur2.user_id = u.id
					and p2.code = ?
				)
				""", (rs, rowNum) -> rs.getLong("id"), permissionCode, submitterUserId, viewPermission);
		for (Long userId : userIds) {
			createMessage(userId, "新的审批待办", object.summary(), "APPROVAL_TODO", businessObjectType, object.id());
		}
	}

	private String businessViewPermission(String sceneCode) {
		if ("SALES_PROJECT_CONTRACT_ACTIVATION".equals(sceneCode)) {
			return "sales:contract:view";
		}
		if ("BOM_ECO_APPLICATION".equals(sceneCode)) {
			return "material:bom-eco:view";
		}
		if ("INVENTORY_OWNERSHIP_CONVERSION_POST".equals(sceneCode)
				|| "INVENTORY_STOCKTAKE_VARIANCE_POST".equals(sceneCode)) {
			return "inventory:balance:view";
		}
		if ("INVENTORY_VALUATION_ADJUSTMENT_POST".equals(sceneCode)) {
			return "inventory:valuation:view";
		}
		if ("PROCUREMENT_REQUISITION_APPROVAL".equals(sceneCode)) {
			return "procurement:requisition:view";
		}
		if ("PROCUREMENT_PRICE_AGREEMENT_ACTIVATION".equals(sceneCode)) {
			return "procurement:price-agreement:view";
		}
		if ("PROCUREMENT_ORDER_EXCEPTION_CONFIRM".equals(sceneCode)) {
			return "procurement:order:view";
		}
		if ("SALES_QUOTE_APPROVAL".equals(sceneCode)) {
			return "sales:quote:view";
		}
		if ("SALES_ORDER_CREDIT_OVERRIDE".equals(sceneCode)) {
			return "sales:order:view";
		}
		if ("SALES_ORDER_CHANGE_APPROVAL".equals(sceneCode)
				|| "SALES_ORDER_CHANGE_CREDIT_OVERRIDE".equals(sceneCode)) {
			return "sales:order-change:view";
		}
		if ("SALES_ORDER_SHORT_CLOSE".equals(sceneCode)) {
			return "sales:order:view";
		}
		if ("PROJECT_COST_ADJUSTMENT_CONFIRM".equals(sceneCode)) {
			return "cost:project-cost-adjustment:view";
		}
		if ("GL_VOUCHER_POST".equals(sceneCode)) {
			return "gl:voucher:view";
		}
		if ("FINANCIAL_PERIOD_REOPEN".equals(sceneCode)) {
			return "financial-close:period:view";
		}
		throw new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED);
	}

	private static String resultStatus(String action) {
		return switch (action) {
			case "SUBMIT" -> "SUBMITTED";
			case "APPROVE" -> "APPROVED";
			case "REJECT" -> "REJECTED";
			case "WITHDRAW" -> "WITHDRAWN";
			case "CANCEL" -> "CANCELLED";
			default -> null;
		};
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

	private static String nullToBlank(String value) {
		return value == null ? "" : value;
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

	public record ApprovalActionRequest(@NotNull Long version, String comment, String idempotencyKey) {
	}

	public record ApprovalInstanceRecord(Long id, String sceneCode, String objectType, Long objectId,
			String objectNo, String objectName, Long objectVersion, String status, String submitReason,
			Long applicantId, String applicantName, OffsetDateTime submittedAt, String completedByUsername,
			OffsetDateTime completedAt, String completedComment, Long version, Long taskId, Long taskVersion,
			List<String> availableActions, List<ApprovalStepRecord> steps, List<ApprovalHistoryRecord> histories,
			List<AttachmentSnapshotRecord> attachmentSnapshots) {
	}

	public record ApprovalTaskRecord(Long id, Long taskId, String sceneCode, String objectType,
			Long objectId, String objectNo, String objectName, Long applicantId,
			String applicantName, OffsetDateTime submittedAt, int stepNo, String candidatePermissionCode,
			String status, Long handledByUserId, String handledByUsername, OffsetDateTime handledAt, String comment,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version, List<String> availableActions) {
	}

	public record ApprovalStepRecord(String stepName, String status, Long taskId, Long version,
			String candidatePermission, String completedByName, OffsetDateTime completedAt) {
	}

	public record ApprovalHistoryRecord(String action, String operatorName, OffsetDateTime operatedAt, String comment,
			String resultStatus) {
	}

	public record AttachmentSnapshotRecord(Long attachmentId, Long fileId, String fileName, String contentType,
			Long fileSize, String sha256, String uploadedByName, OffsetDateTime uploadedAt) {
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

	private record CurrentApprovalTask(Long taskId, Long version) {
	}

	private record ApprovalInstanceBase(Long id, String sceneCode, String businessObjectType, Long businessObjectId,
			String businessObjectNo, String businessObjectSummary, Long businessObjectVersion, String status,
			String submitReason, Long submittedByUserId, String submittedByUsername, OffsetDateTime submittedAt,
			String completedByUsername, OffsetDateTime completedAt, String completedComment, Long version) {
	}

	private record ApprovalTaskProjection(Long id, Long instanceId, String sceneCode, String businessObjectType,
			Long businessObjectId, String businessObjectNo, String businessObjectSummary, Long submittedByUserId,
			String submittedByUsername, OffsetDateTime submittedAt, int stepNo, String candidatePermissionCode,
			String status, Long handledByUserId, String handledByUsername, OffsetDateTime handledAt, String comment,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

	private record ExistingApproval(Long id, String requestFingerprint) {
	}

	private record ExistingActionApproval(Long resultInstanceId, String requestFingerprint) {
	}

}
