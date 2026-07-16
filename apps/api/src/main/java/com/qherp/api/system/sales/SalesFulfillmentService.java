package com.qherp.api.system.sales;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.inventory.InventoryAvailabilityService;
import com.qherp.api.system.inventory.InventoryQualityStatus;
import com.qherp.api.system.inventory.InventoryReservationType;
import com.qherp.api.system.platform.PlatformApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SalesFulfillmentService {

	private static final String ORDER_CHANGE_TARGET = "SALES_ORDER_CHANGE";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger ORDER_CHANGE_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final PlatformApprovalService approvalService;

	private final AuditService auditService;

	private final InventoryAvailabilityService inventoryAvailabilityService;

	private final TransactionTemplate creditLogTransactionTemplate;

	public SalesFulfillmentService(JdbcTemplate jdbcTemplate, PlatformApprovalService approvalService,
			AuditService auditService, InventoryAvailabilityService inventoryAvailabilityService,
			PlatformTransactionManager transactionManager) {
		this.jdbcTemplate = jdbcTemplate;
		this.approvalService = approvalService;
		this.auditService = auditService;
		this.inventoryAvailabilityService = inventoryAvailabilityService;
		this.creditLogTransactionTemplate = new TransactionTemplate(transactionManager);
		this.creditLogTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Transactional
	public CreditProfileResponse createCreditProfile(CreditProfileRequest request, CurrentUser operator) {
		if (request == null || request.customerId() == null || request.creditLimit() == null
				|| request.creditLimit().compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ensureCustomerExists(request.customerId());
		OffsetDateTime now = OffsetDateTime.now();
		Long id = this.jdbcTemplate.queryForObject("""
				insert into sal_customer_credit_profile (
					customer_id, credit_limit, status, frozen, overdue_blocked, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?)
				on conflict (customer_id) do update
				set credit_limit = excluded.credit_limit,
				    frozen = excluded.frozen,
				    overdue_blocked = excluded.overdue_blocked,
				    remark = excluded.remark,
				    updated_by = excluded.updated_by,
				    updated_at = excluded.updated_at,
				    version = sal_customer_credit_profile.version + 1
				returning id
				""", Long.class, request.customerId(), money(request.creditLimit()),
				Boolean.TRUE.equals(request.frozen()), Boolean.TRUE.equals(request.blockOverdue()),
				blankToNull(request.remark()), operator.username(), now, operator.username(), now);
		return creditProfileById(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<CreditProfileResponse> creditProfiles(Long customerId, String keyword, int page,
			int pageSize) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (customerId != null) {
			conditions.add("p.customer_id = ?");
			args.add(customerId);
		}
		if (hasText(keyword)) {
			conditions.add("(c.code ilike ? or c.name ilike ? or p.remark ilike ?)");
			String like = "%" + keyword.trim() + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_customer_credit_profile p
				join mst_customer c on c.id = p.customer_id
				%s
				""".formatted(where), Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<CreditProfileResponse> items = this.jdbcTemplate.query("""
				select p.id, p.customer_id, c.code as customer_code, c.name as customer_name, p.credit_limit,
				       p.status, p.frozen, p.overdue_blocked, p.remark, p.created_at, p.updated_at, p.version
				from sal_customer_credit_profile p
				join mst_customer c on c.id = p.customer_id
				%s
				order by p.updated_at desc, p.id desc
				limit ? offset ?
				""".formatted(where), this::mapCreditProfile, pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public CreditProfileResponse creditProfile(Long customerId) {
		ensureCustomerExists(customerId);
		CreditProfileResponse profile = creditProfileByCustomer(customerId);
		if (profile == null) {
			throw new BusinessException(ApiErrorCode.SALES_CREDIT_BLOCKED);
		}
		return profile;
	}

	@Transactional
	public CreditProfileResponse updateCreditProfile(Long customerId, CreditProfileRequest request,
			CurrentUser operator) {
		if (request == null || request.creditLimit() == null || request.version() == null
				|| request.creditLimit().compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ensureCustomerExists(customerId);
		OffsetDateTime now = OffsetDateTime.now();
		List<Long> ids = this.jdbcTemplate.query("""
				update sal_customer_credit_profile
				set credit_limit = ?, frozen = ?, overdue_blocked = ?, remark = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where customer_id = ?
				and version = ?
				returning id
				""", (rs, rowNum) -> rs.getLong("id"), money(request.creditLimit()), Boolean.TRUE.equals(request.frozen()),
				Boolean.TRUE.equals(request.blockOverdue()), blankToNull(request.remark()), operator.username(),
				now, customerId, request.version());
		if (ids.isEmpty()) {
			throw new BusinessException(ApiErrorCode.SALES_CONCURRENT_MODIFICATION);
		}
		return creditProfileById(ids.getFirst());
	}

	@Transactional(readOnly = true)
	public CreditExposureResponse creditExposure(Long customerId) {
		ensureCustomerExists(customerId);
		CreditProfileResponse profile = creditProfileByCustomer(customerId);
		BigDecimal orderCommitmentAmount = orderCommitmentAmount(customerId);
		BigDecimal unsettledShipmentAmount = unsettledShipmentAmount(customerId);
		BigDecimal receivableOutstandingAmount = receivableOutstandingAmount(customerId);
		BigDecimal usedCredit = orderCommitmentAmount.add(unsettledShipmentAmount).add(receivableOutstandingAmount);
		return new CreditExposureResponse(customerId,
				profile == null ? null : profile.creditLimit(),
				moneyString(orderCommitmentAmount), moneyString(unsettledShipmentAmount),
				moneyString(receivableOutstandingAmount), moneyString(usedCredit), false, false);
	}

	@Transactional
	public PlatformApprovalService.ApprovalInstanceRecord submitCreditOverride(Long orderId,
			PlatformApprovalService.ApprovalSubmitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PlatformApprovalService.ApprovalInstanceRecord idempotent = this.approvalService
			.idempotentSubmitResult("SALES_ORDER_CREDIT_OVERRIDE", orderId, request, operator);
		if (idempotent != null) {
			return idempotent;
		}
		OrderApprovalTarget target = lockOrderApprovalTarget(orderId);
		if (!target.version().equals(request.version())) {
			throw new BusinessException(ApiErrorCode.SALES_CONCURRENT_MODIFICATION);
		}
		if (!"DRAFT".equals(target.status())) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService
			.submitSalesOrderCreditOverride(orderId, request, operator, servletRequest);
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update sal_sales_order
				set credit_override_approval_instance_id = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				returning version
				""", Long.class, approval.id(), operator.username(), OffsetDateTime.now(), orderId);
		this.approvalService.updateBusinessObjectVersion(approval.id(), newVersion);
		return this.approvalService.get(approval.id(), operator);
	}

	@Transactional
	public OrderChangeResponse createOrderChange(Long orderId, OrderChangeRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (request == null || orderChangeRequestVersion(request) == null || !hasText(request.idempotencyKey())
				|| !hasText(request.reason()) || request.lines() == null || request.lines().isEmpty()
				|| request.reason().length() > 500 || request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String fingerprint = orderChangeCreateFingerprint(orderId, request);
		Optional<ExistingAction> existing = existingAction("CREATE_ORDER_CHANGE", "SALES_ORDER", orderId,
				request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return orderChange(existing.get().resultResourceId());
		}
		OrderApprovalTarget order = lockOrderApprovalTarget(orderId);
		if (!order.version().equals(orderChangeRequestVersion(request))) {
			throw new BusinessException(ApiErrorCode.SALES_CONCURRENT_MODIFICATION);
		}
		if (!List.of("CONFIRMED", "PARTIALLY_SHIPPED", "SHIPPED").contains(order.status())) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		Long changeId = insertOrderChangeWithRetry(orderId, request.reason().trim(), operator.username(), now);
		int lineNo = 1;
		for (OrderChangeLineRequest lineRequest : request.lines()) {
			ValidatedOrderChangeLine line = validateOrderChangeLine(orderId, lineRequest);
			this.jdbcTemplate.update("""
					insert into sal_sales_order_change_line (
						change_id, order_line_id, line_no, new_quantity, new_tax_rate,
						new_tax_excluded_unit_price, new_planned_date, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", changeId, line.orderLineId(), lineNo++, line.newQuantity(), line.taxRate(),
					line.taxExcludedUnitPrice(), line.plannedDate(), now, now);
		}
		this.auditService.record(operator, "SALES_ORDER_CHANGE_CREATE", ORDER_CHANGE_TARGET, changeId,
				order.orderNo(), servletRequest);
		recordActionIdempotency("CREATE_ORDER_CHANGE", "SALES_ORDER", orderId, orderChangeRequestVersion(request),
				request.idempotencyKey(), fingerprint, ORDER_CHANGE_TARGET, changeId, 0L, operator);
		return orderChange(changeId);
	}

	@Transactional(readOnly = true)
	public OrderChangeResponse orderChange(Long id) {
		OrderChangeHeader header = orderChangeHeader(id).orElseThrow(() -> new BusinessException(
				ApiErrorCode.SALES_ORDER_CHANGE_NOT_FOUND));
		return toOrderChangeResponse(header, orderChangeLines(id));
	}

	@Transactional(readOnly = true)
	public PageResponse<OrderChangeResponse> orderChanges(Long orderId, String status, int page, int pageSize) {
		ensureOrderExists(orderId);
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("c.order_id = ?");
		args.add(orderId);
		if (hasText(status)) {
			conditions.add("c.status = ?");
			args.add(status.trim());
		}
		String where = "where " + String.join(" and ", conditions);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_order_change c
				%s
				""".formatted(where), Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<OrderChangeResponse> items = this.jdbcTemplate.query("""
				select c.id, c.change_no, c.order_id, o.order_no, c.status, c.reason,
				       c.approval_instance_id, ai.status as approval_status, c.created_at, c.updated_at,
				       c.applied_at, c.version
				from sal_sales_order_change c
				join sal_sales_order o on o.id = c.order_id
				left join platform_approval_instance ai on ai.id = c.approval_instance_id
				%s
				order by c.updated_at desc, c.id desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			OrderChangeHeader header = mapOrderChangeHeader(rs, rowNum);
			return toOrderChangeResponse(header, orderChangeLines(header.id()));
		}, pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional
	public OrderChangeResponse updateOrderChange(Long id, OrderChangeRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| !hasText(request.reason()) || request.lines() == null || request.lines().isEmpty()
				|| request.reason().length() > 500 || request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String fingerprint = orderChangeUpdateFingerprint(id, request);
		Optional<ExistingAction> existing = existingAction("UPDATE_ORDER_CHANGE", ORDER_CHANGE_TARGET, id,
				request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return orderChange(existing.get().resultResourceId());
		}
		OrderChangeHeader change = lockOrderChange(id).orElseThrow(() -> new BusinessException(
				ApiErrorCode.SALES_ORDER_CHANGE_NOT_FOUND));
		if (!change.version().equals(request.version())) {
			throw new BusinessException(ApiErrorCode.SALES_CONCURRENT_MODIFICATION);
		}
		if (!"DRAFT".equals(change.status()) || change.approvalInstanceId() != null) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_STATUS_INVALID);
		}
		OrderApprovalTarget order = lockOrderApprovalTarget(change.orderId());
		if (!List.of("CONFIRMED", "PARTIALLY_SHIPPED", "SHIPPED").contains(order.status())) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("delete from sal_sales_order_change_line where change_id = ?", id);
		int lineNo = 1;
		for (OrderChangeLineRequest lineRequest : request.lines()) {
			ValidatedOrderChangeLine line = validateOrderChangeLine(change.orderId(), lineRequest);
			this.jdbcTemplate.update("""
					insert into sal_sales_order_change_line (
						change_id, order_line_id, line_no, new_quantity, new_tax_rate,
						new_tax_excluded_unit_price, new_planned_date, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", id, line.orderLineId(), lineNo++, line.newQuantity(), line.taxRate(),
					line.taxExcludedUnitPrice(), line.plannedDate(), now, now);
		}
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update sal_sales_order_change
				set reason = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				returning version
				""", Long.class, request.reason().trim(), operator.username(), now, id);
		this.auditService.record(operator, "SALES_ORDER_CHANGE_UPDATE", ORDER_CHANGE_TARGET, id,
				change.changeNo(), servletRequest);
		recordActionIdempotency("UPDATE_ORDER_CHANGE", ORDER_CHANGE_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, ORDER_CHANGE_TARGET, id, newVersion, operator);
		return orderChange(id);
	}

	@Transactional
	public OrderChangeResponse cancelOrderChange(Long id, SalesQuoteService.VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| !hasText(request.reason()) || request.reason().length() > 200
				|| request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String fingerprint = sha256("CANCEL_ORDER_CHANGE|" + id + "|" + request.version() + "|"
				+ request.reason().trim());
		Optional<ExistingAction> existing = existingAction("CANCEL_ORDER_CHANGE", ORDER_CHANGE_TARGET, id,
				request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return orderChange(existing.get().resultResourceId());
		}
		OrderChangeHeader change = lockOrderChange(id).orElseThrow(() -> new BusinessException(
				ApiErrorCode.SALES_ORDER_CHANGE_NOT_FOUND));
		if (!change.version().equals(request.version())) {
			throw new BusinessException(ApiErrorCode.SALES_CONCURRENT_MODIFICATION);
		}
		if (!"DRAFT".equals(change.status()) || change.approvalInstanceId() != null) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_STATUS_INVALID);
		}
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update sal_sales_order_change
				set status = 'CANCELLED', updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				returning version
				""", Long.class, operator.username(), OffsetDateTime.now(), id);
		this.auditService.record(operator, "SALES_ORDER_CHANGE_CANCEL", ORDER_CHANGE_TARGET, id,
				change.changeNo(), servletRequest);
		recordActionIdempotency("CANCEL_ORDER_CHANGE", ORDER_CHANGE_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, ORDER_CHANGE_TARGET, id, newVersion, operator);
		return orderChange(id);
	}

	@Transactional
	public PlatformApprovalService.ApprovalInstanceRecord submitOrderChangeApproval(Long id,
			PlatformApprovalService.ApprovalSubmitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PlatformApprovalService.ApprovalInstanceRecord idempotent = idempotentOrderChangeApproval(id, request,
				operator);
		if (idempotent != null) {
			return idempotent;
		}
		OrderChangeHeader change = lockOrderChange(id).orElseThrow(() -> new BusinessException(
				ApiErrorCode.SALES_ORDER_CHANGE_NOT_FOUND));
		if (!change.version().equals(request.version())) {
			throw new BusinessException(ApiErrorCode.SALES_CONCURRENT_MODIFICATION);
		}
		if (!"DRAFT".equals(change.status()) || change.approvalInstanceId() != null) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_STATUS_INVALID);
		}
		String sceneCode = orderChangeApprovalScene(change, operator);
		PlatformApprovalService.ApprovalInstanceRecord approval = "SALES_ORDER_CHANGE_CREDIT_OVERRIDE"
			.equals(sceneCode)
					? this.approvalService.submitSalesOrderChangeCreditOverride(id, request, operator,
							servletRequest)
					: this.approvalService.submitSalesOrderChangeApproval(id, request, operator, servletRequest);
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update sal_sales_order_change
				set approval_instance_id = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				returning version
				""", Long.class, approval.id(), operator.username(), OffsetDateTime.now(), id);
		this.approvalService.updateBusinessObjectVersion(approval.id(), newVersion);
		return this.approvalService.get(approval.id(), operator);
	}

	private PlatformApprovalService.ApprovalInstanceRecord idempotentOrderChangeApproval(Long changeId,
			PlatformApprovalService.ApprovalSubmitRequest request, CurrentUser operator) {
		PlatformApprovalService.ApprovalInstanceRecord normal = this.approvalService
			.idempotentSubmitResult("SALES_ORDER_CHANGE_APPROVAL", changeId, request, operator);
		if (normal != null) {
			return normal;
		}
		return this.approvalService.idempotentSubmitResult("SALES_ORDER_CHANGE_CREDIT_OVERRIDE", changeId,
				request, operator);
	}

	private String orderChangeApprovalScene(OrderChangeHeader change, CurrentUser operator) {
		try {
			enforceOrderChangeCreditIfNeeded(change, false, operator);
			return "SALES_ORDER_CHANGE_APPROVAL";
		}
		catch (BusinessException exception) {
			if (exception.errorCode() == ApiErrorCode.SALES_CREDIT_LIMIT_EXCEEDED
					|| exception.errorCode() == ApiErrorCode.SALES_CREDIT_PROFILE_MISSING) {
				return "SALES_ORDER_CHANGE_CREDIT_OVERRIDE";
			}
			throw exception;
		}
	}

	private void enforceOrderChangeCreditIfNeeded(OrderChangeHeader change, boolean creditOverridden,
			CurrentUser operator) {
		BigDecimal increasedAmount = orderChangeIncreasedExposureAmount(change.id());
		if (increasedAmount.compareTo(ZERO) <= 0) {
			return;
		}
		OrderCreditTarget target = orderCreditTarget(change.orderId());
		ensureCustomerCreditEligible(target.customerId());
		CreditProfile profile = lockCreditProfile(target.customerId());
		BigDecimal usedCredit = usedCreditAmount(target.customerId());
		if (profile == null) {
			recordOrderChangeCreditCheck(change, target, "BLOCKED", null, usedCredit, increasedAmount,
					"信用档案缺失", operator);
			if (creditOverridden) {
				return;
			}
			throw new BusinessException(ApiErrorCode.SALES_CREDIT_PROFILE_MISSING);
		}
		if (!"ACTIVE".equals(profile.status()) || profile.frozen()) {
			recordOrderChangeCreditCheck(change, target, "BLOCKED", profile.creditLimit(), usedCredit,
					increasedAmount, "信用冻结或停用", operator);
			throw new BusinessException(ApiErrorCode.SALES_CREDIT_FROZEN);
		}
		if (profile.overdueBlocked()) {
			recordOrderChangeCreditCheck(change, target, "BLOCKED", profile.creditLimit(), usedCredit,
					increasedAmount, "逾期信用阻断", operator);
			throw new BusinessException(ApiErrorCode.SALES_CREDIT_BLOCKED);
		}
		if (creditOverridden) {
			recordOrderChangeCreditCheck(change, target, "OVERRIDDEN", profile.creditLimit(), usedCredit,
					increasedAmount, "变更信用例外已批准", operator);
			return;
		}
		if (usedCredit.add(increasedAmount).compareTo(profile.creditLimit()) > 0) {
			recordOrderChangeCreditCheck(change, target, "BLOCKED", profile.creditLimit(), usedCredit,
					increasedAmount, "变更后信用额度不足", operator);
			throw new BusinessException(ApiErrorCode.SALES_CREDIT_LIMIT_EXCEEDED);
		}
		recordOrderChangeCreditCheck(change, target, "PASSED", profile.creditLimit(), usedCredit,
				increasedAmount, "变更信用检查通过", operator);
	}

	@Transactional
	public PlatformApprovalService.ApprovalInstanceRecord submitShortClose(Long orderId,
			PlatformApprovalService.ApprovalSubmitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PlatformApprovalService.ApprovalInstanceRecord idempotent = this.approvalService
			.idempotentSubmitResult("SALES_ORDER_SHORT_CLOSE", orderId, request, operator);
		if (idempotent != null) {
			return idempotent;
		}
		OrderApprovalTarget order = lockOrderApprovalTarget(orderId);
		if (!order.version().equals(request.version())) {
			throw new BusinessException(ApiErrorCode.SALES_CONCURRENT_MODIFICATION);
		}
		if (!List.of("CONFIRMED", "PARTIALLY_SHIPPED", "SHIPPED").contains(order.status())) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		QuantityTotals totals = orderQuantityTotals(orderId);
		if (totals.shippedQuantity().compareTo(ZERO) <= 0
				|| totals.shippedQuantity().compareTo(totals.totalQuantity()) >= 0) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CLOSE_BLOCKED);
		}
		return this.approvalService.submitSalesOrderShortClose(orderId, request, operator, servletRequest);
	}

	@Transactional
	public void applyOrderChangeFromApproval(Long id, Long submittedVersion, String sceneCode, CurrentUser operator,
			HttpServletRequest servletRequest) {
		OrderChangeHeader change = lockOrderChange(id).orElseThrow(() -> new BusinessException(
				ApiErrorCode.SALES_ORDER_CHANGE_NOT_FOUND));
		if (!change.version().equals(submittedVersion)) {
			throw new BusinessException(ApiErrorCode.APPROVAL_BUSINESS_OBJECT_CHANGED);
		}
		if (!"DRAFT".equals(change.status())) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_STATUS_INVALID);
		}
		OrderApprovalTarget order = lockOrderApprovalTarget(change.orderId());
		if (!List.of("CONFIRMED", "PARTIALLY_SHIPPED", "SHIPPED").contains(order.status())) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		enforceOrderChangeCreditIfNeeded(change, "SALES_ORDER_CHANGE_CREDIT_OVERRIDE".equals(sceneCode), operator);
		OffsetDateTime now = OffsetDateTime.now();
		for (OrderChangeLineDetail line : orderChangeLines(id)) {
			applyOrderChangeLine(line, operator, servletRequest, now);
		}
		refreshChangedOrder(change.orderId(), operator.username(), now);
		this.jdbcTemplate.update("""
				update sal_sales_order_change
				set status = 'APPLIED', applied_by = ?, applied_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "SALES_ORDER_CHANGE_APPLY", ORDER_CHANGE_TARGET, id, change.changeNo(),
				servletRequest);
	}

	@Transactional
	public void reopenOrderChangeAfterApprovalTerminal(Long id, CurrentUser operator) {
		OrderChangeHeader change = lockOrderChange(id).orElseThrow(() -> new BusinessException(
				ApiErrorCode.SALES_ORDER_CHANGE_NOT_FOUND));
		if (!"DRAFT".equals(change.status())) {
			return;
		}
		this.jdbcTemplate.update("""
				update sal_sales_order_change
				set approval_instance_id = null, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), id);
	}

	@Transactional(readOnly = true)
	public PageResponse<DeliveryPlanResponse> deliveryPlans(Long orderId, Boolean countedOnly, int page,
			int pageSize) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (orderId != null) {
			conditions.add("p.order_id = ?");
			args.add(orderId);
		}
		if (Boolean.TRUE.equals(countedOnly)) {
			conditions.add("p.status in ('PLANNED', 'PARTIALLY_SHIPPED')");
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_delivery_plan p
				%s
				""".formatted(where), Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<DeliveryPlanResponse> items = this.jdbcTemplate.query("""
				select p.id, p.order_id, o.order_no, p.order_line_id, p.line_no, p.planned_date,
				       p.planned_quantity, p.shipped_quantity,
				       (p.planned_quantity - p.shipped_quantity) as remaining_quantity,
				       p.status, p.close_reason, l.material_id, m.code as material_code, m.name as material_name,
				       l.unit_id, u.name as unit_name, p.created_at, p.updated_at, p.version
				from sal_sales_delivery_plan p
				join sal_sales_order o on o.id = p.order_id
				join sal_sales_order_line l on l.id = p.order_line_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				%s
				order by p.planned_date asc, p.line_no asc, p.id asc
				limit ? offset ?
				""".formatted(where), this::mapDeliveryPlan, pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<DeliveryPlanResponse> orderDeliveryPlans(Long orderId, int page, int pageSize) {
		ensureOrderExists(orderId);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_delivery_plan
				where order_id = ?
				""", Long.class, orderId);
		List<DeliveryPlanResponse> items = this.jdbcTemplate.query("""
				select p.id, p.order_id, o.order_no, p.order_line_id, p.line_no, p.planned_date,
				       p.planned_quantity, p.shipped_quantity,
				       (p.planned_quantity - p.shipped_quantity) as remaining_quantity,
				       p.status, p.close_reason, l.material_id, m.code as material_code, m.name as material_name,
				       l.unit_id, u.name as unit_name, p.created_at, p.updated_at, p.version
				from sal_sales_delivery_plan p
				join sal_sales_order o on o.id = p.order_id
				join sal_sales_order_line l on l.id = p.order_line_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where p.order_id = ?
				order by p.planned_date asc, p.line_no asc, p.id asc
				limit ? offset ?
				""", this::mapDeliveryPlan, orderId, limit(pageSize), offset(page, pageSize));
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional
	public DeliveryPlanListResponse replaceOrderDeliveryPlans(Long orderId, DeliveryPlanReplaceRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| request.lines() == null || request.lines().isEmpty() || request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String fingerprint = deliveryPlanReplaceFingerprint(orderId, request);
		Optional<ExistingAction> existing = existingAction("REPLACE_DELIVERY_PLANS", "SALES_ORDER", orderId,
				request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return orderDeliveryPlanList(orderId);
		}
		OrderApprovalTarget order = lockOrderApprovalTarget(orderId);
		if (!order.version().equals(request.version())) {
			throw new BusinessException(ApiErrorCode.SALES_CONCURRENT_MODIFICATION);
		}
		if (!List.of("CONFIRMED", "PARTIALLY_SHIPPED", "SHIPPED").contains(order.status())) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				delete from sal_sales_delivery_plan
				where order_id = ?
				and shipped_quantity = 0
				and status in ('PLANNED', 'CLOSED')
				""", orderId);
		Map<Long, Integer> planLineNos = new HashMap<>();
		for (DeliveryPlanLineRequest lineRequest : request.lines()) {
			ValidatedDeliveryPlanLine line = validateDeliveryPlanLine(orderId, lineRequest);
			Integer planLineNo = planLineNos.merge(line.orderLineId(), 1, Integer::sum);
			this.jdbcTemplate.update("""
					insert into sal_sales_delivery_plan (
						order_id, order_line_id, line_no, planned_date, planned_quantity, shipped_quantity, status,
						close_reason, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, 0, 'PLANNED', null, ?, ?, ?, ?)
					""", orderId, line.orderLineId(), planLineNo, line.plannedDate(), line.quantity(),
					operator.username(), now, operator.username(), now);
		}
		validateDeliveryPlanTotals(orderId);
		Long newOrderVersion = this.jdbcTemplate.queryForObject("""
				update sal_sales_order
				set updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				returning version
				""", Long.class, operator.username(), now, orderId);
		recordActionIdempotency("REPLACE_DELIVERY_PLANS", "SALES_ORDER", orderId, request.version(),
				request.idempotencyKey(), fingerprint, "SALES_ORDER", orderId, newOrderVersion, operator);
		this.auditService.record(operator, "SALES_DELIVERY_PLAN_REPLACE", "SALES_ORDER", orderId,
				order.orderNo(), servletRequest);
		return orderDeliveryPlanList(orderId);
	}

	@Transactional
	public DeliveryPlanResponse closeDeliveryPlan(Long orderId, Long planId,
			SalesQuoteService.VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| !hasText(request.reason()) || request.reason().length() > 200
				|| request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String fingerprint = sha256("CLOSE_DELIVERY_PLAN|" + orderId + "|" + planId + "|" + request.version()
				+ "|" + request.reason().trim());
		Optional<ExistingAction> existing = existingAction("CLOSE_DELIVERY_PLAN", "SALES_DELIVERY_PLAN", planId,
				request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return deliveryPlan(planId);
		}
		DeliveryPlanLock plan = lockDeliveryPlan(orderId, planId);
		if (!plan.version().equals(request.version())) {
			throw new BusinessException(ApiErrorCode.SALES_CONCURRENT_MODIFICATION);
		}
		if (!List.of("PLANNED", "PARTIALLY_SHIPPED").contains(plan.status())) {
			throw new BusinessException(ApiErrorCode.SALES_DELIVERY_PLAN_STATUS_INVALID);
		}
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update sal_sales_delivery_plan
				set status = 'CLOSED', close_reason = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				returning version
				""", Long.class, request.reason().trim(), operator.username(), OffsetDateTime.now(), planId);
		recordActionIdempotency("CLOSE_DELIVERY_PLAN", "SALES_DELIVERY_PLAN", planId, request.version(),
				request.idempotencyKey(), fingerprint, "SALES_DELIVERY_PLAN", planId, newVersion, operator);
		this.auditService.record(operator, "SALES_DELIVERY_PLAN_CLOSE", "SALES_DELIVERY_PLAN", planId,
				String.valueOf(planId), servletRequest);
		return deliveryPlan(planId);
	}

	@Transactional(readOnly = true)
	public PageResponse<EffectiveDemandResponse> effectiveDemands(Long projectId, Long customerId, Long contractId,
			Long orderId, Long materialId, String status, LocalDate expectedDateFrom, LocalDate expectedDateTo,
			Boolean countedOnly, int page, int pageSize) {
		QueryParts query = effectiveDemandQuery(projectId, customerId, contractId, orderId, materialId, status,
				expectedDateFrom, expectedDateTo, countedOnly);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from (
					%s
				) demand
				%s
				""".formatted(effectiveDemandSelect(), query.where()), Long.class, query.args().toArray());
		List<Object> pageArgs = new ArrayList<>(query.args());
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<EffectiveDemandResponse> items = this.jdbcTemplate.query("""
				select *
				from (
					%s
				) demand
				%s
				order by expected_date asc, order_no asc, order_line_id asc, coalesce(delivery_plan_id, 0) asc
				limit ? offset ?
				""".formatted(effectiveDemandSelect(), query.where()), this::mapEffectiveDemand, pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	private QueryParts effectiveDemandQuery(Long projectId, Long customerId, Long contractId, Long orderId,
			Long materialId, String status, LocalDate expectedDateFrom, LocalDate expectedDateTo,
			Boolean countedOnly) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (projectId != null) {
			conditions.add("demand.project_id = ?");
			args.add(projectId);
		}
		if (customerId != null) {
			conditions.add("demand.customer_id = ?");
			args.add(customerId);
		}
		if (contractId != null) {
			conditions.add("demand.contract_id = ?");
			args.add(contractId);
		}
		if (orderId != null) {
			conditions.add("demand.order_id = ?");
			args.add(orderId);
		}
		if (materialId != null) {
			conditions.add("demand.material_id = ?");
			args.add(materialId);
		}
		if (hasText(status)) {
			conditions.add("demand.status = ?");
			args.add(status.trim());
		}
		if (expectedDateFrom != null) {
			conditions.add("demand.expected_date >= ?");
			args.add(expectedDateFrom);
		}
		if (expectedDateTo != null) {
			conditions.add("demand.expected_date <= ?");
			args.add(expectedDateTo);
		}
		if (!Boolean.FALSE.equals(countedOnly)) {
			conditions.add("demand.counted_as_effective_demand = true");
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private String effectiveDemandSelect() {
		return """
				select coalesce(p.id, d.id) as id,
				       'SALES_ORDER' as source_type,
				       d.order_id as source_id,
				       d.order_no as source_no,
				       o.version as source_version,
				       d.order_id,
				       d.order_no,
				       d.order_line_id,
				       p.id as delivery_plan_id,
				       d.project_id,
				       d.project_no,
				       d.project_name,
				       d.customer_id,
				       d.customer_name,
				       d.contract_id,
				       d.contract_no,
				       o.source_quote_id as quote_id,
				       o.source_quote_no as quote_no,
				       d.material_id,
				       d.material_code,
				       d.material_name,
				       d.unit_name,
				       d.ordered_quantity as order_quantity,
				       coalesce(p.planned_quantity, d.ordered_quantity) as planned_quantity,
				       case when p.id is null then d.shipped_quantity else p.shipped_quantity end as shipped_quantity,
				       d.returned_quantity,
				       (d.shipped_quantity - d.returned_quantity) as net_quantity,
				       case
				           when p.id is null then d.open_demand_quantity
				           else greatest(p.planned_quantity - p.shipped_quantity, 0)
				       end as open_quantity,
				       case
				           when p.id is null then d.open_demand_quantity
				           else greatest(p.planned_quantity - p.shipped_quantity, 0)
				       end as open_demand_quantity,
				       coalesce(p.planned_date, o.expected_ship_date, o.order_date) as expected_date,
				       case
				           when not d.counted_as_effective_demand then 'EXCLUDED'
				           when coalesce(p.planned_date, o.expected_ship_date, o.order_date) < current_date
				                and case when p.id is null then d.open_demand_quantity
				                         else greatest(p.planned_quantity - p.shipped_quantity, 0) end > 0 then 'OVERDUE'
				           when case when p.id is null then d.shipped_quantity else p.shipped_quantity end > 0
				                then 'PARTIALLY_SHIPPED'
				           else 'OPEN'
				       end as status,
				       case
				           when not d.counted_as_effective_demand then false
				           when p.id is not null and p.status not in ('PLANNED', 'PARTIALLY_SHIPPED') then false
				           when case when p.id is null then d.open_demand_quantity
				                     else greatest(p.planned_quantity - p.shipped_quantity, 0) end <= 0 then false
				           else true
				       end as counted_as_effective_demand,
				       case
				           when not d.counted_as_effective_demand then d.excluded_reason_code
				           when p.id is not null and p.status not in ('PLANNED', 'PARTIALLY_SHIPPED')
				                then 'DELIVERY_PLAN_STATUS_NOT_COUNTED'
				           when case when p.id is null then d.open_demand_quantity
				                     else greatest(p.planned_quantity - p.shipped_quantity, 0) end <= 0
				                then 'DEMAND_ALREADY_FULFILLED'
				           else null
				       end as excluded_reason_code,
				       o.updated_at
				from sal_effective_sales_demand d
				join sal_sales_order o on o.id = d.order_id
				left join sal_sales_delivery_plan p on p.order_line_id = d.order_line_id
				""";
	}

	private Long insertOrderChangeWithRetry(Long orderId, String reason, String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= 3; attempt++) {
			String changeNo = nextNo("SOC", ORDER_CHANGE_NO_SEQUENCE);
			try {
				return this.jdbcTemplate.queryForObject("""
						insert into sal_sales_order_change (
							change_no, order_id, status, reason, created_by, created_at, updated_by, updated_at
						)
						values (?, ?, 'DRAFT', ?, ?, ?, ?, ?)
						returning id
						""", Long.class, changeNo, orderId, reason, operatorName, now, operatorName, now);
			}
			catch (DuplicateKeyException exception) {
				if (attempt < 3) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private ValidatedOrderChangeLine validateOrderChangeLine(Long orderId, OrderChangeLineRequest request) {
		if (request == null || request.orderLineId() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		OrderChangeLineBase line = this.jdbcTemplate.query("""
				select l.id, l.order_id, o.order_no, o.order_date, l.line_no,
				       l.material_id, m.code as material_code, m.name as material_name,
				       l.unit_id, u.name as unit_name, l.quantity, l.shipped_quantity, l.tax_rate,
				       l.tax_excluded_unit_price, l.expected_ship_date, l.reservation_warehouse_id
				from sal_sales_order_line l
				join sal_sales_order o on o.id = l.order_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.id = ?
				for update of l
				""", this::mapOrderChangeLineBase, request.orderLineId()).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_SOURCE_IMMUTABLE));
		if (!line.orderId().equals(orderId)) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_SOURCE_IMMUTABLE);
		}
		BigDecimal targetQuantity = request.targetQuantity() == null ? request.newQuantity() : request.targetQuantity();
		if (targetQuantity == null || targetQuantity.compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.SALES_QUANTITY_INVALID);
		}
		targetQuantity = scale6(targetQuantity);
		if (targetQuantity.compareTo(line.shippedQuantity()) < 0) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_BELOW_SHIPPED);
		}
		BigDecimal taxRate = request.taxRate() == null ? line.taxRate() : scale6(request.taxRate());
		BigDecimal taxExcludedUnitPrice = request.taxExcludedUnitPrice();
		if (taxExcludedUnitPrice == null && request.taxIncludedUnitPrice() != null) {
			taxExcludedUnitPrice = request.taxIncludedUnitPrice()
				.divide(BigDecimal.ONE.add(taxRate), 6, RoundingMode.HALF_UP);
		}
		if (taxExcludedUnitPrice == null) {
			taxExcludedUnitPrice = line.taxExcludedUnitPrice();
		}
		taxExcludedUnitPrice = scale6(taxExcludedUnitPrice);
		if (taxRate.compareTo(ZERO) < 0 || taxExcludedUnitPrice.compareTo(ZERO) < 0) {
			throw new BusinessException(ApiErrorCode.SALES_TAX_PRICE_INVALID);
		}
		LocalDate plannedDate = request.promisedDate() == null ? request.newPlannedDate() : request.promisedDate();
		if (plannedDate == null) {
			plannedDate = line.expectedShipDate() == null ? LocalDate.now() : line.expectedShipDate();
		}
		return new ValidatedOrderChangeLine(line.id(), targetQuantity, taxRate, taxExcludedUnitPrice, plannedDate);
	}

	private Optional<OrderChangeHeader> orderChangeHeader(Long id) {
		return this.jdbcTemplate.query("""
				select c.id, c.change_no, c.order_id, o.order_no, c.status, c.reason,
				       c.approval_instance_id, ai.status as approval_status, c.created_at, c.updated_at,
				       c.applied_at, c.version
				from sal_sales_order_change c
				join sal_sales_order o on o.id = c.order_id
				left join platform_approval_instance ai on ai.id = c.approval_instance_id
				where c.id = ?
				""", this::mapOrderChangeHeader, id).stream().findFirst();
	}

	private Optional<OrderChangeHeader> lockOrderChange(Long id) {
		return this.jdbcTemplate.query("""
				select c.id, c.change_no, c.order_id, o.order_no, c.status, c.reason,
				       c.approval_instance_id, ai.status as approval_status, c.created_at, c.updated_at,
				       c.applied_at, c.version
				from sal_sales_order_change c
				join sal_sales_order o on o.id = c.order_id
				left join platform_approval_instance ai on ai.id = c.approval_instance_id
				where c.id = ?
				for update of c
				""", this::mapOrderChangeHeader, id).stream().findFirst();
	}

	private List<OrderChangeLineDetail> orderChangeLines(Long changeId) {
		return this.jdbcTemplate.query("""
				select cl.id, cl.change_id, cl.line_no, cl.order_line_id, l.material_id,
				       m.code as material_code, m.name as material_name, l.unit_id, u.name as unit_name,
				       l.quantity as current_quantity, l.shipped_quantity, cl.new_quantity, cl.new_tax_rate,
				       cl.new_tax_excluded_unit_price, cl.new_planned_date
				from sal_sales_order_change_line cl
				join sal_sales_order_line l on l.id = cl.order_line_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where cl.change_id = ?
				order by cl.line_no asc, cl.id asc
				""", this::mapOrderChangeLineDetail, changeId);
	}

	private OrderChangeHeader mapOrderChangeHeader(ResultSet rs, int rowNum) throws SQLException {
		return new OrderChangeHeader(rs.getLong("id"), rs.getString("change_no"), rs.getLong("order_id"),
				rs.getString("order_no"), rs.getString("status"), rs.getString("reason"),
				rs.getObject("approval_instance_id", Long.class), rs.getString("approval_status"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getObject("applied_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private OrderChangeLineBase mapOrderChangeLineBase(ResultSet rs, int rowNum) throws SQLException {
		return new OrderChangeLineBase(rs.getLong("id"), rs.getLong("order_id"), rs.getString("order_no"),
				rs.getObject("order_date", LocalDate.class), rs.getInt("line_no"), rs.getLong("material_id"),
				rs.getString("material_code"), rs.getString("material_name"), rs.getLong("unit_id"),
				rs.getString("unit_name"), rs.getBigDecimal("quantity"), rs.getBigDecimal("shipped_quantity"),
				rs.getBigDecimal("tax_rate"), rs.getBigDecimal("tax_excluded_unit_price"),
				rs.getObject("expected_ship_date", LocalDate.class),
				rs.getObject("reservation_warehouse_id", Long.class));
	}

	private OrderChangeLineDetail mapOrderChangeLineDetail(ResultSet rs, int rowNum) throws SQLException {
		return new OrderChangeLineDetail(rs.getLong("id"), rs.getLong("change_id"), rs.getInt("line_no"),
				rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				rs.getBigDecimal("current_quantity"), rs.getBigDecimal("shipped_quantity"),
				rs.getBigDecimal("new_quantity"), rs.getBigDecimal("new_tax_rate"),
				rs.getBigDecimal("new_tax_excluded_unit_price"),
				rs.getObject("new_planned_date", LocalDate.class));
	}

	private OrderChangeResponse toOrderChangeResponse(OrderChangeHeader header, List<OrderChangeLineDetail> lines) {
		List<String> allowedActions = orderChangeAllowedActions(header);
		return new OrderChangeResponse(header.id(), header.changeNo(), header.orderId(), header.orderNo(),
				header.status(), header.approvalStatus(), header.approvalInstanceId(), header.reason(),
				header.createdAt(), header.updatedAt(), header.appliedAt(), header.version(), allowedActions,
				orderChangeActionDisabledReason(header, allowedActions),
				lines.stream().map(this::toOrderChangeLineResponse).toList());
	}

	private OrderChangeLineResponse toOrderChangeLineResponse(OrderChangeLineDetail line) {
		BigDecimal taxIncludedUnitPrice = scale6(line.newTaxExcludedUnitPrice().multiply(BigDecimal.ONE.add(
				line.newTaxRate())));
		return new OrderChangeLineResponse(line.id(), line.lineNo(), line.orderLineId(), line.materialId(),
				line.materialCode(), line.materialName(), line.unitId(), line.unitName(),
				quantityString(line.currentQuantity()), quantityString(line.shippedQuantity()),
				quantityString(line.newQuantity()), moneyString(line.newTaxRate(), 6),
				moneyString(line.newTaxExcludedUnitPrice(), 6), moneyString(taxIncludedUnitPrice, 6),
				line.newPlannedDate());
	}

	private List<String> orderChangeAllowedActions(OrderChangeHeader header) {
		if (!"DRAFT".equals(header.status()) || "SUBMITTED".equals(header.approvalStatus())) {
			return List.of();
		}
		return List.of("UPDATE", "SUBMIT_APPROVAL", "CANCEL");
	}

	private String orderChangeActionDisabledReason(OrderChangeHeader header, List<String> allowedActions) {
		if (!allowedActions.isEmpty()) {
			return null;
		}
		if ("SUBMITTED".equals(header.approvalStatus())) {
			return "销售订单变更审批中";
		}
		return ApiErrorCode.SALES_ORDER_CHANGE_STATUS_INVALID.message();
	}

	private void applyOrderChangeLine(OrderChangeLineDetail line, CurrentUser operator,
			HttpServletRequest servletRequest, OffsetDateTime now) {
		OrderChangeLineBase current = this.jdbcTemplate.query("""
				select l.id, l.order_id, o.order_no, o.order_date, l.line_no,
				       l.material_id, m.code as material_code, m.name as material_name,
				       l.unit_id, u.name as unit_name, l.quantity, l.shipped_quantity, l.tax_rate,
				       l.tax_excluded_unit_price, l.expected_ship_date, l.reservation_warehouse_id
				from sal_sales_order_line l
				join sal_sales_order o on o.id = l.order_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.id = ?
				for update of l
				""", this::mapOrderChangeLineBase, line.orderLineId()).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_SOURCE_IMMUTABLE));
		if (line.newQuantity().compareTo(current.shippedQuantity()) < 0) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_BELOW_SHIPPED);
		}
		BigDecimal taxIncludedUnitPrice = scale6(line.newTaxExcludedUnitPrice().multiply(BigDecimal.ONE.add(
				line.newTaxRate())));
		BigDecimal taxExcludedAmount = money(line.newQuantity().multiply(line.newTaxExcludedUnitPrice()));
		BigDecimal taxIncludedAmount = money(line.newQuantity().multiply(taxIncludedUnitPrice));
		BigDecimal taxAmount = money(taxIncludedAmount.subtract(taxExcludedAmount));
		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set quantity = ?, unit_price = ?, expected_ship_date = ?, tax_rate = ?,
				    tax_excluded_unit_price = ?, tax_included_unit_price = ?, tax_excluded_amount = ?,
				    tax_amount = ?, tax_included_amount = ?, updated_at = ?, version = version + 1
				where id = ?
				""", line.newQuantity(), line.newTaxExcludedUnitPrice(), line.newPlannedDate(), line.newTaxRate(),
				line.newTaxExcludedUnitPrice(), taxIncludedUnitPrice, taxExcludedAmount, taxAmount,
				taxIncludedAmount, now, line.orderLineId());
		adjustSalesOrderReservation(current, line.newQuantity(), operator, servletRequest);
		int updatedPlans = this.jdbcTemplate.update("""
				update sal_sales_delivery_plan
				set planned_quantity = ?, planned_date = ?,
				    status = case
				        when shipped_quantity >= ? then 'SHIPPED'
				        when shipped_quantity > 0 then 'PARTIALLY_SHIPPED'
				        else 'PLANNED'
				    end,
				    updated_by = ?, updated_at = ?, version = version + 1
				where order_line_id = ?
				and status in ('PLANNED', 'PARTIALLY_SHIPPED', 'SHIPPED')
				""", line.newQuantity(), line.newPlannedDate(), line.newQuantity(), operator.username(), now,
				line.orderLineId());
		if (updatedPlans == 0) {
			this.jdbcTemplate.update("""
					insert into sal_sales_delivery_plan (
						order_id, order_line_id, line_no, planned_date, planned_quantity, shipped_quantity, status,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?)
					on conflict (order_line_id, line_no) do nothing
					""", current.orderId(), line.orderLineId(), line.newPlannedDate(), line.newQuantity(),
					current.shippedQuantity(), current.shippedQuantity().compareTo(ZERO) > 0
							? "PARTIALLY_SHIPPED" : "PLANNED",
					operator.username(), now, operator.username(), now);
		}
	}

	private void adjustSalesOrderReservation(OrderChangeLineBase current, BigDecimal newQuantity,
			CurrentUser operator, HttpServletRequest servletRequest) {
		this.inventoryAvailabilityService.releaseBySourceLine(InventoryReservationType.RESERVATION,
				InventoryAvailabilityService.SALES_ORDER_SOURCE, current.id(), operator, servletRequest);
		BigDecimal remainingQuantity = newQuantity.subtract(current.shippedQuantity());
		if (remainingQuantity.compareTo(ZERO) <= 0) {
			return;
		}
		this.inventoryAvailabilityService.reserveFromWarehouse(
				new InventoryAvailabilityService.ReservationCommand(InventoryReservationType.RESERVATION,
						current.reservationWarehouseId(), current.materialId(), current.unitId(), remainingQuantity,
						InventoryAvailabilityService.SALES_ORDER_SOURCE, current.orderId(), current.id(),
						current.orderNo(), current.orderDate(), "销售订单变更预留调整", null, "PUBLIC", null, null,
						InventoryQualityStatus.QUALIFIED, null, null, null),
				operator, servletRequest);
	}

	private void refreshChangedOrder(Long orderId, String operatorName, OffsetDateTime now) {
		QuantityTotals totals = orderQuantityTotals(orderId);
		String status = "CONFIRMED";
		if (totals.shippedQuantity().compareTo(ZERO) > 0
				&& totals.shippedQuantity().compareTo(totals.totalQuantity()) >= 0) {
			status = "SHIPPED";
		}
		else if (totals.shippedQuantity().compareTo(ZERO) > 0) {
			status = "PARTIALLY_SHIPPED";
		}
		this.jdbcTemplate.update("""
				update sal_sales_order o
				set expected_ship_date = dates.expected_ship_date,
				    tax_excluded_amount = totals.tax_excluded_amount,
				    tax_amount = totals.tax_amount,
				    tax_included_amount = totals.tax_included_amount,
				    status = ?,
				    updated_by = ?,
				    updated_at = ?,
				    version = version + 1
				from (
					select order_id, max(expected_ship_date) as expected_ship_date,
					       coalesce(sum(tax_excluded_amount), 0) as tax_excluded_amount,
					       coalesce(sum(tax_amount), 0) as tax_amount,
					       coalesce(sum(tax_included_amount), 0) as tax_included_amount
					from sal_sales_order_line
					where order_id = ?
					group by order_id
				) totals
				join (
					select order_id, max(expected_ship_date) as expected_ship_date
					from sal_sales_order_line
					where order_id = ?
					group by order_id
				) dates on dates.order_id = totals.order_id
				where o.id = totals.order_id
				""", status, operatorName, now, orderId, orderId);
	}

	private QuantityTotals orderQuantityTotals(Long orderId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity), 0) as total_quantity,
				       coalesce(sum(shipped_quantity), 0) as shipped_quantity
				from sal_sales_order_line
				where order_id = ?
				""", (rs, rowNum) -> new QuantityTotals(rs.getBigDecimal("total_quantity"),
				rs.getBigDecimal("shipped_quantity")), orderId);
	}

	private Optional<ExistingAction> existingAction(String action, String resourceType, Long resourceId,
			String idempotencyKey, String fingerprint, CurrentUser operator) {
		validateIdempotencyKey(idempotencyKey);
		return this.jdbcTemplate.query("""
				select result_resource_type, result_resource_id, result_version, request_fingerprint
				from sal_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingAction(rs.getString("result_resource_type"),
				rs.getLong("result_resource_id"), rs.getLong("result_version"), rs.getString("request_fingerprint")),
				operator.id(), action, resourceType, resourceId, idempotencyKey.trim()).stream().findFirst()
			.map((record) -> {
				if (!record.requestFingerprint().equals(fingerprint)) {
					throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
				}
				return record;
			});
	}

	private void recordActionIdempotency(String action, String resourceType, Long resourceId, Long resourceVersion,
			String idempotencyKey, String fingerprint, String resultResourceType, Long resultResourceId,
			Long resultVersion, CurrentUser operator) {
		validateIdempotencyKey(idempotencyKey);
		try {
			this.jdbcTemplate.update("""
					insert into sal_action_idempotency (
						operator_user_id, action, resource_type, resource_id, resource_version, idempotency_key,
						request_fingerprint, result_resource_type, result_resource_id, result_version
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), action, resourceType, resourceId, resourceVersion, idempotencyKey.trim(),
					fingerprint, resultResourceType, resultResourceId, resultVersion);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
		}
	}

	private String orderChangeCreateFingerprint(Long orderId, OrderChangeRequest request) {
		StringBuilder builder = new StringBuilder("CREATE_ORDER_CHANGE|").append(orderId).append("|")
			.append(orderChangeRequestVersion(request)).append("|")
			.append(request.reason().trim());
		for (OrderChangeLineRequest line : request.lines()) {
			builder.append("|").append(line == null ? "null" : nullToBlank(line.orderLineId()))
				.append(":").append(line == null ? "" : nullToBlank(line.targetQuantity()))
				.append(":").append(line == null ? "" : nullToBlank(line.newQuantity()))
				.append(":").append(line == null ? "" : nullToBlank(line.taxExcludedUnitPrice()))
				.append(":").append(line == null ? "" : nullToBlank(line.taxIncludedUnitPrice()))
				.append(":").append(line == null ? "" : nullToBlank(line.taxRate()))
				.append(":").append(line == null ? "" : nullToBlank(line.promisedDate()))
				.append(":").append(line == null ? "" : nullToBlank(line.newPlannedDate()));
		}
		return sha256(builder.toString());
	}

	private String orderChangeUpdateFingerprint(Long changeId, OrderChangeRequest request) {
		StringBuilder builder = new StringBuilder("UPDATE_ORDER_CHANGE|").append(changeId).append("|")
			.append(request.version()).append("|")
			.append(request.reason().trim());
		for (OrderChangeLineRequest line : request.lines()) {
			builder.append("|").append(line == null ? "null" : nullToBlank(line.orderLineId()))
				.append(":").append(line == null ? "" : nullToBlank(line.targetQuantity()))
				.append(":").append(line == null ? "" : nullToBlank(line.newQuantity()))
				.append(":").append(line == null ? "" : nullToBlank(line.taxExcludedUnitPrice()))
				.append(":").append(line == null ? "" : nullToBlank(line.taxIncludedUnitPrice()))
				.append(":").append(line == null ? "" : nullToBlank(line.taxRate()))
				.append(":").append(line == null ? "" : nullToBlank(line.promisedDate()))
				.append(":").append(line == null ? "" : nullToBlank(line.newPlannedDate()));
		}
		return sha256(builder.toString());
	}

	private String deliveryPlanReplaceFingerprint(Long orderId, DeliveryPlanReplaceRequest request) {
		StringBuilder builder = new StringBuilder("REPLACE_DELIVERY_PLANS|").append(orderId).append("|")
			.append(request.version()).append("|")
			.append(nullToBlank(blankToNull(request.reason())));
		for (DeliveryPlanLineRequest line : request.lines()) {
			builder.append("|").append(line == null ? "null" : nullToBlank(line.orderLineId()))
				.append(":").append(line == null ? "" : nullToBlank(line.planDate()))
				.append(":").append(line == null ? "" : nullToBlank(line.plannedDate()))
				.append(":").append(line == null ? "" : nullToBlank(line.quantity()))
				.append(":").append(line == null ? "" : nullToBlank(line.plannedQuantity()));
		}
		return sha256(builder.toString());
	}

	private Long orderChangeRequestVersion(OrderChangeRequest request) {
		if (request == null) {
			return null;
		}
		return request.orderVersion() == null ? request.version() : request.orderVersion();
	}

	private static String nextNo(String prefix, AtomicInteger sequence) {
		int value = Math.floorMod(sequence.incrementAndGet(), 1000);
		return prefix + LocalDateTime.now().format(NUMBER_FORMATTER) + String.format("%03d", value);
	}

	private void ensureOrderExists(Long orderId) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from sal_sales_order where id = ?", Long.class,
				orderId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_NOT_FOUND);
		}
	}

	private void ensureCustomerExists(Long customerId) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from mst_customer where id = ?", Long.class,
				customerId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.SALES_CUSTOMER_INVALID);
		}
	}

	private CreditProfileResponse creditProfileById(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.customer_id, c.code as customer_code, c.name as customer_name, p.credit_limit,
				       p.status, p.frozen, p.overdue_blocked, p.remark, p.created_at, p.updated_at, p.version
				from sal_customer_credit_profile p
				join mst_customer c on c.id = p.customer_id
				where p.id = ?
				""", this::mapCreditProfile, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_CREDIT_BLOCKED));
	}

	private CreditProfileResponse creditProfileByCustomer(Long customerId) {
		return this.jdbcTemplate.query("""
				select p.id, p.customer_id, c.code as customer_code, c.name as customer_name, p.credit_limit,
				       p.status, p.frozen, p.overdue_blocked, p.remark, p.created_at, p.updated_at, p.version
				from sal_customer_credit_profile p
				join mst_customer c on c.id = p.customer_id
				where p.customer_id = ?
				""", this::mapCreditProfile, customerId).stream().findFirst().orElse(null);
	}

	private CreditProfileResponse mapCreditProfile(ResultSet rs, int rowNum) throws SQLException {
		return new CreditProfileResponse(rs.getLong("id"), rs.getLong("customer_id"), rs.getString("customer_code"),
				rs.getString("customer_name"), moneyString(rs.getBigDecimal("credit_limit")),
				rs.getString("status"), rs.getBoolean("frozen"), rs.getBoolean("overdue_blocked"),
				rs.getString("remark"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private BigDecimal orderCommitmentAmount(Long customerId) {
		BigDecimal value = this.jdbcTemplate.queryForObject("""
				select coalesce(sum((l.quantity - l.shipped_quantity) * l.tax_included_unit_price), 0)
				from sal_sales_order o
				join sal_sales_order_line l on l.order_id = o.id
				where o.customer_id = ?
				and o.status in ('CONFIRMED', 'PARTIALLY_SHIPPED')
				""", BigDecimal.class, customerId);
		return money(value == null ? BigDecimal.ZERO : value);
	}

	private BigDecimal unsettledShipmentAmount(Long customerId) {
		BigDecimal value = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(sl.tax_included_amount), 0)
				from sal_sales_shipment sh
				join sal_sales_shipment_line sl on sl.shipment_id = sh.id
				where sh.customer_id = ?
				and sh.status = 'POSTED'
				and not exists (
					select 1
					from fin_receivable_source rs
					join fin_receivable r on r.id = rs.receivable_id
					where rs.source_type = 'SALES_SHIPMENT'
					and rs.source_line_id = sl.id
					and r.status <> 'CANCELLED'
				)
				""", BigDecimal.class, customerId);
		return money(value == null ? BigDecimal.ZERO : value);
	}

	private BigDecimal receivableOutstandingAmount(Long customerId) {
		BigDecimal value = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(unreceived_amount), 0)
				from fin_receivable
				where customer_id = ?
				and status not in ('CANCELLED', 'RECEIVED')
				""", BigDecimal.class, customerId);
		return money(value == null ? BigDecimal.ZERO : value);
	}

	private BigDecimal usedCreditAmount(Long customerId) {
		return money(orderCommitmentAmount(customerId)
			.add(unsettledShipmentAmount(customerId))
			.add(receivableOutstandingAmount(customerId)));
	}

	private BigDecimal orderChangeIncreasedExposureAmount(Long changeId) {
		BigDecimal value = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(greatest(
					(cl.new_quantity * cl.new_tax_excluded_unit_price * (1 + cl.new_tax_rate))
						- l.tax_included_amount,
					0
				)), 0)
				from sal_sales_order_change_line cl
				join sal_sales_order_line l on l.id = cl.order_line_id
				where cl.change_id = ?
				""", BigDecimal.class, changeId);
		return money(value == null ? ZERO : value);
	}

	private OrderCreditTarget orderCreditTarget(Long orderId) {
		return this.jdbcTemplate.query("""
				select o.id, o.order_no, o.customer_id
				from sal_sales_order o
				where o.id = ?
				for update of o
				""", (rs, rowNum) -> new OrderCreditTarget(rs.getLong("id"), rs.getString("order_no"),
				rs.getLong("customer_id")), orderId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_ORDER_NOT_FOUND));
	}

	private void ensureCustomerCreditEligible(Long customerId) {
		String status = this.jdbcTemplate.query("""
				select status
				from mst_customer
				where id = ?
				""", (rs, rowNum) -> rs.getString("status"), customerId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.SALES_CUSTOMER_INVALID);
		}
	}

	private CreditProfile lockCreditProfile(Long customerId) {
		return this.jdbcTemplate.query("""
				select id, customer_id, credit_limit, status, frozen, overdue_blocked
				from sal_customer_credit_profile
				where customer_id = ?
				for update
				""", (rs, rowNum) -> new CreditProfile(rs.getLong("id"), rs.getLong("customer_id"),
				rs.getBigDecimal("credit_limit"), rs.getString("status"), rs.getBoolean("frozen"),
				rs.getBoolean("overdue_blocked")), customerId).stream().findFirst().orElse(null);
	}

	private void recordOrderChangeCreditCheck(OrderChangeHeader change, OrderCreditTarget target, String result,
			BigDecimal creditLimit, BigDecimal usedCredit, BigDecimal increasedAmount, String reason,
			CurrentUser operator) {
		this.creditLogTransactionTemplate.executeWithoutResult((status) -> this.jdbcTemplate.update("""
				insert into sal_credit_check_log (
					customer_id, source_type, source_id, source_no, check_result, credit_limit, used_credit,
					new_amount, reason, created_by, created_at
				)
				values (?, 'SALES_ORDER_CHANGE', ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", target.customerId(), change.id(), change.changeNo(), result, creditLimit, usedCredit,
				increasedAmount, reason, operator.username(), OffsetDateTime.now()));
	}

	private OrderApprovalTarget lockOrderApprovalTarget(Long orderId) {
		return this.jdbcTemplate.query("""
				select id, order_no, status, version
				from sal_sales_order
				where id = ?
				for update
				""", (rs, rowNum) -> new OrderApprovalTarget(rs.getLong("id"), rs.getString("order_no"),
				rs.getString("status"), rs.getLong("version")), orderId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_ORDER_NOT_FOUND));
	}

	private DeliveryPlanListResponse orderDeliveryPlanList(Long orderId) {
		List<DeliveryPlanResponse> lines = this.jdbcTemplate.query("""
				select p.id, p.order_id, o.order_no, p.order_line_id, p.line_no, p.planned_date,
				       p.planned_quantity, p.shipped_quantity,
				       (p.planned_quantity - p.shipped_quantity) as remaining_quantity,
				       p.status, p.close_reason, l.material_id, m.code as material_code, m.name as material_name,
				       l.unit_id, u.name as unit_name, p.created_at, p.updated_at, p.version
				from sal_sales_delivery_plan p
				join sal_sales_order o on o.id = p.order_id
				join sal_sales_order_line l on l.id = p.order_line_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where p.order_id = ?
				order by p.planned_date asc, p.line_no asc, p.id asc
				""", this::mapDeliveryPlan, orderId);
		return new DeliveryPlanListResponse(lines);
	}

	private DeliveryPlanResponse deliveryPlan(Long planId) {
		return this.jdbcTemplate.query("""
				select p.id, p.order_id, o.order_no, p.order_line_id, p.line_no, p.planned_date,
				       p.planned_quantity, p.shipped_quantity,
				       (p.planned_quantity - p.shipped_quantity) as remaining_quantity,
				       p.status, p.close_reason, l.material_id, m.code as material_code, m.name as material_name,
				       l.unit_id, u.name as unit_name, p.created_at, p.updated_at, p.version
				from sal_sales_delivery_plan p
				join sal_sales_order o on o.id = p.order_id
				join sal_sales_order_line l on l.id = p.order_line_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where p.id = ?
				""", this::mapDeliveryPlan, planId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_DELIVERY_PLAN_NOT_FOUND));
	}

	private DeliveryPlanLock lockDeliveryPlan(Long orderId, Long planId) {
		return this.jdbcTemplate.query("""
				select id, order_id, order_line_id, status, planned_quantity, shipped_quantity, version
				from sal_sales_delivery_plan
				where id = ?
				for update
				""", (rs, rowNum) -> new DeliveryPlanLock(rs.getLong("id"), rs.getLong("order_id"),
				rs.getLong("order_line_id"), rs.getString("status"), rs.getBigDecimal("planned_quantity"),
				rs.getBigDecimal("shipped_quantity"), rs.getLong("version")), planId).stream().findFirst()
			.filter((plan) -> plan.orderId().equals(orderId))
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_DELIVERY_PLAN_NOT_FOUND));
	}

	private ValidatedDeliveryPlanLine validateDeliveryPlanLine(Long orderId, DeliveryPlanLineRequest request) {
		if (request == null || request.orderLineId() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		BigDecimal quantity = request.quantity() == null ? request.plannedQuantity() : request.quantity();
		LocalDate plannedDate = request.planDate() == null ? request.plannedDate() : request.planDate();
		if (quantity == null || quantity.compareTo(ZERO) <= 0 || plannedDate == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		OrderLinePlanBase line = this.jdbcTemplate.query("""
				select id, order_id, line_no, quantity, shipped_quantity
				from sal_sales_order_line
				where id = ?
				for update
				""", (rs, rowNum) -> new OrderLinePlanBase(rs.getLong("id"), rs.getLong("order_id"),
				rs.getInt("line_no"), rs.getBigDecimal("quantity"), rs.getBigDecimal("shipped_quantity")),
				request.orderLineId()).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_SOURCE_IMMUTABLE));
		if (!line.orderId().equals(orderId)) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_SOURCE_IMMUTABLE);
		}
		return new ValidatedDeliveryPlanLine(line.id(), line.lineNo(), plannedDate, scale6(quantity));
	}

	private void validateDeliveryPlanTotals(Long orderId) {
		List<Long> invalidLineIds = this.jdbcTemplate.query("""
				select l.id
				from sal_sales_order_line l
				left join sal_sales_delivery_plan p on p.order_line_id = l.id
				where l.order_id = ?
				group by l.id, l.quantity, l.shipped_quantity
				having coalesce(sum(p.planned_quantity), 0) <> l.quantity
				   or coalesce(sum(p.planned_quantity), 0) < l.shipped_quantity
				""", (rs, rowNum) -> rs.getLong("id"), orderId);
		if (!invalidLineIds.isEmpty()) {
			throw new BusinessException(ApiErrorCode.SALES_DELIVERY_PLAN_TOTAL_MISMATCH);
		}
	}

	private DeliveryPlanResponse mapDeliveryPlan(ResultSet rs, int rowNum) throws SQLException {
		List<String> actions = allowedActions(rs.getString("status"));
		return new DeliveryPlanResponse(rs.getLong("id"), rs.getLong("order_id"), rs.getString("order_no"),
				rs.getLong("order_line_id"), rs.getInt("line_no"), rs.getObject("planned_date", LocalDate.class),
				quantityString(rs.getBigDecimal("planned_quantity")),
				quantityString(rs.getBigDecimal("shipped_quantity")),
				quantityString(rs.getBigDecimal("remaining_quantity")), rs.getString("status"),
				rs.getString("close_reason"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getLong("version"), actions, deliveryPlanActionDisabledReason(actions));
	}

	private EffectiveDemandResponse mapEffectiveDemand(ResultSet rs, int rowNum) throws SQLException {
		return new EffectiveDemandResponse(rs.getLong("id"), rs.getString("source_type"), rs.getLong("source_id"),
				rs.getString("source_no"), rs.getLong("source_version"), rs.getLong("order_id"),
				rs.getString("order_no"), rs.getLong("order_line_id"), rs.getObject("delivery_plan_id", Long.class),
				rs.getObject("project_id", Long.class), rs.getString("project_no"), rs.getString("project_name"),
				rs.getLong("customer_id"), rs.getString("customer_name"), rs.getObject("contract_id", Long.class),
				rs.getString("contract_no"), rs.getObject("quote_id", Long.class), rs.getString("quote_no"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				rs.getString("unit_name"), quantityString(rs.getBigDecimal("order_quantity")),
				quantityString(rs.getBigDecimal("planned_quantity")), quantityString(rs.getBigDecimal("shipped_quantity")),
				quantityString(rs.getBigDecimal("returned_quantity")), quantityString(rs.getBigDecimal("net_quantity")),
				quantityString(rs.getBigDecimal("open_quantity")), quantityString(rs.getBigDecimal("open_demand_quantity")),
				rs.getObject("expected_date", LocalDate.class), rs.getString("status"),
				rs.getBoolean("counted_as_effective_demand"), rs.getString("excluded_reason_code"),
				rs.getObject("updated_at", OffsetDateTime.class));
	}

	private List<String> allowedActions(String status) {
		if ("PLANNED".equals(status) || "PARTIALLY_SHIPPED".equals(status)) {
			return List.of("UPDATE", "CLOSE");
		}
		return List.of();
	}

	private String deliveryPlanActionDisabledReason(List<String> allowedActions) {
		return allowedActions.isEmpty() ? ApiErrorCode.SALES_DELIVERY_PLAN_STATUS_INVALID.message() : null;
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String quantityString(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private static String moneyString(BigDecimal value) {
		return value == null ? null : money(value).toPlainString();
	}

	private static String moneyString(BigDecimal value, int scale) {
		return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
	}

	private static BigDecimal scale6(BigDecimal value) {
		return value.setScale(6, RoundingMode.HALF_UP);
	}

	private static void validateIdempotencyKey(String idempotencyKey) {
		if (!hasText(idempotencyKey) || idempotencyKey.length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String nullToBlank(Object value) {
		return value == null ? "" : value.toString();
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

	public record DeliveryPlanResponse(Long id, Long orderId, String orderNo, Long orderLineId, Integer lineNo,
			LocalDate plannedDate, String plannedQuantity, String shippedQuantity, String remainingQuantity,
			String status, String closeReason, Long materialId, String materialCode, String materialName, Long unitId,
			String unitName, OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version,
			List<String> allowedActions, String actionDisabledReason) {
	}

	public record DeliveryPlanListResponse(List<DeliveryPlanResponse> lines) {
	}

	public record DeliveryPlanReplaceRequest(@NotNull Long version, String reason,
			@NotNull String idempotencyKey, @Valid List<DeliveryPlanLineRequest> lines) {
	}

	public record DeliveryPlanLineRequest(@NotNull Long orderLineId, LocalDate planDate, LocalDate plannedDate,
			BigDecimal quantity, BigDecimal plannedQuantity, String remark) {
	}

	public record EffectiveDemandResponse(Long id, String sourceType, Long sourceId, String sourceNo,
			Long sourceVersion, Long orderId, String orderNo, Long orderLineId, Long deliveryPlanId, Long projectId,
			String projectNo, String projectName, Long customerId, String customerName, Long contractId,
			String contractNo, Long quoteId, String quoteNo, Long materialId, String materialCode, String materialName,
			String unitName, String orderQuantity, String plannedQuantity, String shippedQuantity,
			String returnedQuantity, String netQuantity, String openQuantity, String openDemandQuantity,
			LocalDate expectedDate, String status, boolean countedAsEffectiveDemand, String excludedReasonCode,
			OffsetDateTime updatedAt) {
	}

	public record CreditProfileRequest(Long customerId, BigDecimal creditLimit, Boolean frozen, Boolean blockOverdue,
			LocalDate reviewDate, String remark, Long version) {
	}

	public record CreditProfileResponse(Long id, Long customerId, String customerCode, String customerName,
			String creditLimit, String status, boolean frozen, boolean blockOverdue, String remark,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

	public record CreditExposureResponse(Long customerId, String creditLimit, String orderCommitmentAmount,
			String unsettledShipmentAmount, String receivableOutstandingAmount, String usedCredit,
			boolean overdueRisk, boolean creditRestricted) {
	}

	public record OrderChangeRequest(Long orderVersion, Long version, @NotNull String reason,
			@NotNull String idempotencyKey, @Valid List<OrderChangeLineRequest> lines) {
	}

	public record OrderChangeLineRequest(@NotNull Long orderLineId, BigDecimal targetQuantity,
			BigDecimal newQuantity, BigDecimal taxRate, BigDecimal taxExcludedUnitPrice,
			BigDecimal taxIncludedUnitPrice, LocalDate promisedDate, LocalDate newPlannedDate) {
	}

	public record OrderChangeResponse(Long id, String changeNo, Long orderId, String orderNo, String status,
			String approvalStatus, Long approvalInstanceId, String reason, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, OffsetDateTime appliedAt, Long version, List<String> allowedActions,
			String actionDisabledReason, List<OrderChangeLineResponse> lines) {
	}

	public record OrderChangeLineResponse(Long id, Integer lineNo, Long orderLineId, Long materialId,
			String materialCode, String materialName, Long unitId, String unitName, String originalQuantity,
			String shippedQuantity, String newQuantity, String taxRate, String taxExcludedUnitPrice,
			String taxIncludedUnitPrice, LocalDate newPlannedDate) {
	}

	private record OrderApprovalTarget(Long id, String orderNo, String status, Long version) {
	}

	private record OrderChangeHeader(Long id, String changeNo, Long orderId, String orderNo, String status,
			String reason, Long approvalInstanceId, String approvalStatus, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, OffsetDateTime appliedAt, Long version) {
	}

	private record OrderChangeLineBase(Long id, Long orderId, String orderNo, LocalDate orderDate,
			Integer lineNo, Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			BigDecimal quantity, BigDecimal shippedQuantity, BigDecimal taxRate, BigDecimal taxExcludedUnitPrice,
			LocalDate expectedShipDate, Long reservationWarehouseId) {
	}

	private record OrderChangeLineDetail(Long id, Long changeId, Integer lineNo, Long orderLineId,
			Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			BigDecimal currentQuantity, BigDecimal shippedQuantity, BigDecimal newQuantity,
			BigDecimal newTaxRate, BigDecimal newTaxExcludedUnitPrice, LocalDate newPlannedDate) {
	}

	private record ValidatedOrderChangeLine(Long orderLineId, BigDecimal newQuantity, BigDecimal taxRate,
			BigDecimal taxExcludedUnitPrice, LocalDate plannedDate) {
	}

	private record ValidatedDeliveryPlanLine(Long orderLineId, Integer lineNo, LocalDate plannedDate,
			BigDecimal quantity) {
	}

	private record OrderLinePlanBase(Long id, Long orderId, Integer lineNo, BigDecimal quantity,
			BigDecimal shippedQuantity) {
	}

	private record DeliveryPlanLock(Long id, Long orderId, Long orderLineId, String status,
			BigDecimal plannedQuantity, BigDecimal shippedQuantity, Long version) {
	}

	private record QuantityTotals(BigDecimal totalQuantity, BigDecimal shippedQuantity) {
	}

	private record OrderCreditTarget(Long id, String orderNo, Long customerId) {
	}

	private record CreditProfile(Long id, Long customerId, BigDecimal creditLimit, String status, boolean frozen,
			boolean overdueBlocked) {
	}

	private record ExistingAction(String resultResourceType, Long resultResourceId, Long resultVersion,
			String requestFingerprint) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
