package com.qherp.api.system.salesproject;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SalesProjectAdminService {

	private static final String PROJECT_TARGET = "SALES_PROJECT";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger PROJECT_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final SalesProjectContractService contractService;

	private final SalesOrderProjectLinkService orderProjectLinkService;

	public SalesProjectAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			SalesProjectContractService contractService, SalesOrderProjectLinkService orderProjectLinkService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.contractService = contractService;
		this.orderProjectLinkService = orderProjectLinkService;
	}

	@Transactional(readOnly = true)
	public PageResponse<ProjectResponse> projects(String keyword, Long customerId, String status, Long ownerUserId,
			LocalDate plannedStartFrom, LocalDate plannedStartTo, LocalDate plannedFinishFrom,
			LocalDate plannedFinishTo, int page, int pageSize, CurrentUser currentUser) {
		QueryParts queryParts = projectQueryParts(keyword, customerId, status, ownerUserId, plannedStartFrom,
				plannedStartTo, plannedFinishFrom, plannedFinishTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_project p
				join mst_customer c on c.id = p.customer_id
				left join sys_user u on u.id = p.owner_user_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<ProjectResponse> items = this.jdbcTemplate.query("""
				select p.id, p.project_no, p.name, p.customer_id, c.code as customer_code, c.name as customer_name,
				       p.owner_user_id, u.username as owner_username, u.display_name as owner_display_name,
				       p.planned_start_date, p.planned_finish_date, p.status, p.target_revenue, p.target_cost,
				       p.remark, p.created_by, p.created_at, p.updated_by, p.updated_at, p.activated_by,
				       p.activated_at, p.closed_by, p.closed_at, p.close_reason, p.cancelled_by, p.cancelled_at,
				       p.cancel_reason, p.version
				from sal_project p
				join mst_customer c on c.id = p.customer_id
				left join sys_user u on u.id = p.owner_user_id
				%s
				order by p.updated_at desc, p.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> projectResponse(rs, currentUser, false),
				args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public ProjectResponse get(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select p.id, p.project_no, p.name, p.customer_id, c.code as customer_code, c.name as customer_name,
				       p.owner_user_id, u.username as owner_username, u.display_name as owner_display_name,
				       p.planned_start_date, p.planned_finish_date, p.status, p.target_revenue, p.target_cost,
				       p.remark, p.created_by, p.created_at, p.updated_by, p.updated_at, p.activated_by,
				       p.activated_at, p.closed_by, p.closed_at, p.close_reason, p.cancelled_by, p.cancelled_at,
				       p.cancel_reason, p.version
				from sal_project p
				join mst_customer c on c.id = p.customer_id
				left join sys_user u on u.id = p.owner_user_id
				where p.id = ?
				""", (rs, rowNum) -> projectResponse(rs, currentUser, true), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public SalesFulfillmentResponse salesFulfillment(Long id, boolean includeLegacy, CurrentUser currentUser) {
		FulfillmentProjectRow project = fulfillmentProject(id);
		return salesFulfillmentResponse(project, includeLegacy, currentUser);
	}

	@Transactional(readOnly = true)
	public ProductionSummaryResponse productionSummary(Long id, CurrentUser currentUser) {
		if (!exists("select count(*) from sal_project where id = ?", id)) {
			throw new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND);
		}
		boolean canViewWorkOrders = hasPermission(currentUser, "production:work-order:view");
		boolean canViewOutsourcing = hasPermission(currentUser, "production:outsourcing:view");
		if (!canViewWorkOrders && !canViewOutsourcing) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
		boolean costVisible = hasPermission(currentUser, "inventory:valuation:view");
		ProductionWorkOrderAggregate workOrders = canViewWorkOrders ? this.jdbcTemplate.queryForObject("""
				select count(*) as work_order_count,
				       count(*) filter (where status = 'RELEASED') as released_work_order_count,
				       count(*) filter (where status = 'COMPLETED') as completed_work_order_count,
				       coalesce(sum(planned_quantity), 0.000000) as planned_quantity,
				       coalesce(sum(received_quantity), 0.000000) as completed_quantity,
				       (select work_order_no from mfg_work_order latest
				        where latest.project_id = ? and latest.ownership_type = 'PROJECT'
				        and latest.status <> 'CANCELLED'
				        order by latest.updated_at desc, latest.id desc limit 1) as latest_work_order_no
				from mfg_work_order
				where project_id = ?
				and ownership_type = 'PROJECT'
				and status <> 'CANCELLED'
				""", (rs, rowNum) -> new ProductionWorkOrderAggregate(rs.getLong("work_order_count"),
				rs.getLong("released_work_order_count"), rs.getLong("completed_work_order_count"),
				rs.getBigDecimal("planned_quantity"), rs.getBigDecimal("completed_quantity"),
				rs.getString("latest_work_order_no")), id, id) : null;
		ProductionOutsourcingAggregate outsourcing = canViewOutsourcing ? this.jdbcTemplate.queryForObject("""
				select count(*) as outsourcing_order_count,
				       count(*) filter (where status = 'IN_PROGRESS') as outsourcing_in_progress_count,
				       count(*) filter (where status = 'COMPLETED') as outsourcing_completed_count,
				       coalesce(sum(planned_quantity), 0.000000) as outsourcing_planned_quantity,
				       coalesce(sum(received_quantity), 0.000000) as outsourcing_received_quantity,
				       (select outsourcing_order_no from mfg_outsourcing_order latest
				        where latest.project_id = ? and latest.ownership_type = 'PROJECT'
				        and latest.status <> 'CANCELLED'
				        order by latest.updated_at desc, latest.id desc limit 1) as latest_outsourcing_order_no
				from mfg_outsourcing_order
				where project_id = ?
				and ownership_type = 'PROJECT'
				and status <> 'CANCELLED'
				""", (rs, rowNum) -> new ProductionOutsourcingAggregate(rs.getLong("outsourcing_order_count"),
				rs.getLong("outsourcing_in_progress_count"), rs.getLong("outsourcing_completed_count"),
				rs.getBigDecimal("outsourcing_planned_quantity"), rs.getBigDecimal("outsourcing_received_quantity"),
				rs.getString("latest_outsourcing_order_no")), id, id) : null;
		return new ProductionSummaryResponse(id, canViewWorkOrders ? workOrders.workOrderCount() : null,
				canViewWorkOrders ? workOrders.releasedWorkOrderCount() : null,
				canViewWorkOrders ? workOrders.completedWorkOrderCount() : null,
				canViewOutsourcing ? outsourcing.outsourcingOrderCount() : null,
				canViewOutsourcing ? outsourcing.outsourcingInProgressCount() : null,
				canViewOutsourcing ? outsourcing.outsourcingCompletedCount() : null,
				canViewWorkOrders ? quantityString(workOrders.plannedQuantity()) : null,
				canViewWorkOrders ? quantityString(workOrders.completedQuantity()) : null,
				canViewOutsourcing ? quantityString(outsourcing.plannedQuantity()) : null,
				canViewOutsourcing ? quantityString(outsourcing.receivedQuantity()) : null, costVisible,
				costVisible ? null : "无库存估值查看权限",
				canViewWorkOrders ? workOrders.latestWorkOrderNo() : null,
				canViewOutsourcing ? outsourcing.latestOutsourcingOrderNo() : null);
	}

	@Transactional
	public SalesFulfillmentResponse closeSalesFulfillment(Long id, FulfillmentCloseRequest request,
			CurrentUser operator, HttpServletRequest requestContext) {
		validateFulfillmentCloseRequest(request);
		SalesFulfillmentResponse idempotent = idempotentFulfillmentCloseResult(id, request, operator);
		if (idempotent != null) {
			return idempotent;
		}
		FulfillmentProjectRow project = lockFulfillmentProject(id);
		requireVersion(project.version(), request.version());
		if ("CLOSED".equals(project.salesFulfillmentStatus())) {
			throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
		}
		if (!fulfillmentBlockReasons(id).isEmpty()) {
			throw new BusinessException(ApiErrorCode.PROJECT_HAS_OPEN_BUSINESS);
		}
		OffsetDateTime now = OffsetDateTime.now();
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update sal_project
				set sales_fulfillment_status = 'CLOSED',
				    sales_fulfillment_closed_by = ?,
				    sales_fulfillment_closed_at = ?,
				    sales_fulfillment_close_reason = ?,
				    updated_by = ?,
				    updated_at = ?,
				    version = version + 1
				where id = ?
				returning version
				""", Long.class, operator.username(), now, validateReason(request.reason()), operator.username(),
				now, id);
		recordFulfillmentCloseIdempotency(id, request, newVersion, operator);
		this.auditService.record(operator, "SALES_PROJECT_FULFILLMENT_CLOSE", PROJECT_TARGET, id,
				"销售履约关闭：" + request.reason(), requestContext);
		return salesFulfillment(id, false, operator);
	}

	@Transactional
	public ProjectResponse create(ProjectCreateRequest request, CurrentUser operator, HttpServletRequest requestContext) {
		ValidatedProject project = validateCreate(request);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into sal_project (
						project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
						status, target_revenue, target_cost, remark, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, nextNo("SP", PROJECT_SEQUENCE), project.name(), project.customerId(),
					project.ownerUserId(), project.plannedStartDate(), project.plannedFinishDate(),
					project.targetRevenue(), project.targetCost(), blankToNull(project.remark()), operator.username(),
					now, operator.username(), now);
			this.auditService.record(operator, "SALES_PROJECT_CREATE", PROJECT_TARGET, id,
					"创建项目 " + project.name(), requestContext);
			return get(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
	}

	@Transactional
	public ProjectResponse update(Long id, ProjectUpdateRequest request, CurrentUser operator,
			HttpServletRequest requestContext) {
		ProjectRow current = lockProject(id).orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
		requireVersion(current.version(), request == null ? null : request.version());
		if (current.status() == SalesProjectStatus.CLOSED || current.status() == SalesProjectStatus.CANCELLED) {
			throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
		}
		ValidatedProjectUpdate update = validateUpdate(request, current);
		OffsetDateTime now = OffsetDateTime.now();
		String changedFields = changedFields(current, update);
		this.jdbcTemplate.update("""
				update sal_project
				set name = ?, owner_user_id = ?, planned_start_date = ?, planned_finish_date = ?, target_revenue = ?,
				    target_cost = ?, remark = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", update.name(), update.ownerUserId(), update.plannedStartDate(), update.plannedFinishDate(),
				update.targetRevenue(), update.targetCost(), blankToNull(update.remark()), operator.username(), now,
				id);
		this.auditService.record(operator, "SALES_PROJECT_UPDATE", PROJECT_TARGET, id,
				"更新字段 " + changedFields, requestContext);
		return get(id, operator);
	}

	@Transactional
	public ProjectResponse activate(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest requestContext) {
		ProjectRow current = lockProject(id).orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
		requireVersion(current.version(), request == null ? null : request.version());
		if (current.status() != SalesProjectStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
		}
		Long effectiveMainCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_project_contract
				where project_id = ?
				and contract_type = 'MAIN'
				and status = 'EFFECTIVE'
				""", Long.class, id);
		if (effectiveMainCount == null || effectiveMainCount == 0) {
			throw new BusinessException(ApiErrorCode.PROJECT_MAIN_CONTRACT_REQUIRED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_project
				set status = 'ACTIVE', activated_by = ?, activated_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "SALES_PROJECT_ACTIVATE", PROJECT_TARGET, id, "项目激活", requestContext);
		return get(id, operator);
	}

	@Transactional
	public ProjectResponse close(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest requestContext) {
		ProjectRow current = lockProject(id).orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
		requireVersion(current.version(), request == null ? null : request.version());
		String reason = validateReason(request == null ? null : request.reason());
		if (current.status() != SalesProjectStatus.ACTIVE) {
			throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
		}
		Long openContracts = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_project_contract
				where project_id = ?
				and status not in ('CLOSED', 'TERMINATED', 'CANCELLED')
				""", Long.class, id);
		Long openOrders = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_order
				where project_id = ?
				and status not in ('SHIPPED', 'CLOSED', 'CANCELLED')
				""", Long.class, id);
		if ((openContracts != null && openContracts > 0) || (openOrders != null && openOrders > 0)) {
			throw new BusinessException(ApiErrorCode.PROJECT_HAS_OPEN_BUSINESS);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_project
				set status = 'CLOSED', closed_by = ?, closed_at = ?, close_reason = ?, updated_by = ?,
				    updated_at = ?, version = version + 1
				where id = ?
				""", operator.username(), now, reason, operator.username(), now, id);
		this.auditService.record(operator, "SALES_PROJECT_CLOSE", PROJECT_TARGET, id, "关闭原因：" + reason,
				requestContext);
		return get(id, operator);
	}

	@Transactional
	public ProjectResponse cancel(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest requestContext) {
		ProjectRow current = lockProject(id).orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
		requireVersion(current.version(), request == null ? null : request.version());
		String reason = validateReason(request == null ? null : request.reason());
		if (current.status() != SalesProjectStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
		}
		Long effectiveContracts = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_project_contract
				where project_id = ?
				and status in ('EFFECTIVE', 'CLOSED', 'TERMINATED')
				""", Long.class, id);
		Long linkedOrders = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_order
				where project_id = ?
				""", Long.class, id);
		if ((effectiveContracts != null && effectiveContracts > 0) || (linkedOrders != null && linkedOrders > 0)) {
			throw new BusinessException(ApiErrorCode.PROJECT_HAS_EFFECTIVE_BUSINESS);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_project
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, cancel_reason = ?, updated_by = ?,
				    updated_at = ?, version = version + 1
				where id = ?
				""", operator.username(), now, reason, operator.username(), now, id);
		this.auditService.record(operator, "SALES_PROJECT_CANCEL", PROJECT_TARGET, id, "取消原因：" + reason,
				requestContext);
		return get(id, operator);
	}

	@Transactional(readOnly = true)
	public PageResponse<OwnerCandidateResponse> ownerCandidates(String keyword, int page, int pageSize) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("status = 'ENABLED'");
		if (hasText(keyword)) {
			conditions.add("(username ilike ? or display_name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
		}
		QueryParts queryParts = where(conditions, args);
		long total = this.jdbcTemplate.queryForObject("select count(*) from sys_user " + queryParts.where(),
				Long.class, queryParts.args().toArray());
		List<Object> pageArgs = new ArrayList<>(queryParts.args());
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<OwnerCandidateResponse> items = this.jdbcTemplate.query("""
				select id, username, display_name
				from sys_user
				%s
				order by id asc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> new OwnerCandidateResponse(rs.getLong("id"),
				rs.getString("username"), rs.getString("display_name")), pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	private ProjectResponse projectResponse(ResultSet rs, CurrentUser currentUser, boolean detail) throws SQLException {
		ProjectBase base = new ProjectBase(rs.getLong("id"), rs.getString("project_no"), rs.getString("name"),
				rs.getLong("customer_id"), rs.getString("customer_code"), rs.getString("customer_name"),
				nullableLong(rs, "owner_user_id"), rs.getString("owner_username"), rs.getString("owner_display_name"),
				rs.getObject("planned_start_date", LocalDate.class),
				rs.getObject("planned_finish_date", LocalDate.class), rs.getString("status"),
				rs.getBigDecimal("target_revenue"), rs.getBigDecimal("target_cost"), rs.getString("remark"),
				rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("activated_by"), rs.getObject("activated_at", OffsetDateTime.class),
				rs.getString("closed_by"), rs.getObject("closed_at", OffsetDateTime.class),
				rs.getString("close_reason"), rs.getString("cancelled_by"),
				rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("cancel_reason"),
				rs.getLong("version"));
		boolean contractAllowed = currentUser.permissions().contains("sales:contract:view");
		boolean orderAllowed = currentUser.permissions().contains("sales:order:view");
		ContractSummary contractSummary = contractAllowed ? contractSummary(base.id()) : ContractSummary.restricted();
		SalesOrderProjectLinkService.SalesOrderSummary orderSummary = orderAllowed
				? this.orderProjectLinkService.salesOrderSummary(base.id()) : null;
		List<SalesProjectContractService.ContractResponse> contracts = detail && contractAllowed
				? this.contractService.listByProject(base.id()) : List.of();
		List<OperationResponse> operations = detail ? operations(base.id(), contractAllowed, orderAllowed) : List.of();
		return new ProjectResponse(base.id(), base.projectNo(), base.name(), base.customerId(), base.customerCode(),
				base.customerName(), base.ownerUserId(), base.ownerUsername(), base.ownerDisplayName(),
				base.plannedStartDate(), base.plannedFinishDate(), base.status(), base.targetRevenue(),
				base.targetCost(), base.remark(), contractSummary.mainContractId(), contractSummary.mainContractNo(),
				contractSummary.mainContractStatus(), contractSummary.effectiveContractAmount(),
				contractSummary.contractCount(), contractSummary.supplementContractCount(),
				orderAllowed ? orderSummary.salesOrderCount() : null,
				!contractAllowed, !orderAllowed, contracts, orderAllowed ? orderSummary : null, operations,
				base.createdByName(), base.createdAt(), base.updatedByName(), base.updatedAt(), base.activatedByName(),
				base.activatedAt(), base.closedByName(), base.closedAt(), base.closeReason(), base.cancelledByName(),
				base.cancelledAt(), base.cancelReason(), base.version());
	}

	private ContractSummary contractSummary(Long projectId) {
		return this.jdbcTemplate.queryForObject("""
				select
					(select id from sal_project_contract where project_id = ? and contract_type = 'MAIN'
					 and status <> 'CANCELLED' order by id asc limit 1) as main_contract_id,
					(select contract_no from sal_project_contract where project_id = ? and contract_type = 'MAIN'
					 and status <> 'CANCELLED' order by id asc limit 1) as main_contract_no,
					(select status from sal_project_contract where project_id = ? and contract_type = 'MAIN'
					 and status <> 'CANCELLED' order by id asc limit 1) as main_contract_status,
					(select coalesce(sum(amount), 0) from sal_project_contract where project_id = ?
					 and status = 'EFFECTIVE') as effective_contract_amount,
					(select count(*) from sal_project_contract where project_id = ?) as contract_count,
					(select count(*) from sal_project_contract where project_id = ? and contract_type = 'SUPPLEMENT'
					 and status <> 'CANCELLED') as supplement_contract_count
				""", (rs, rowNum) -> new ContractSummary(nullableLong(rs, "main_contract_id"),
				rs.getString("main_contract_no"), rs.getString("main_contract_status"),
				rs.getBigDecimal("effective_contract_amount"), rs.getLong("contract_count"),
				rs.getLong("supplement_contract_count")), projectId, projectId, projectId, projectId, projectId,
				projectId);
	}

	private List<OperationResponse> operations(Long projectId, boolean includeContracts, boolean includeOrders) {
		List<Object> args = new ArrayList<>();
		args.add(PROJECT_TARGET);
		args.add(projectId.toString());
		String contractClause = "";
		if (includeContracts) {
			List<String> contractIds = this.jdbcTemplate.queryForList("""
					select id::text
					from sal_project_contract
					where project_id = ?
					""", String.class, projectId);
			if (!contractIds.isEmpty()) {
				contractClause = " or (target_type = 'SALES_PROJECT_CONTRACT' and target_id in (%s))"
					.formatted(String.join(",", contractIds.stream().map((ignored) -> "?").toList()));
				args.addAll(contractIds);
			}
		}
		return this.jdbcTemplate.query("""
				select id, operator_username, action, target_type, target_id, target_summary, created_at
				from sys_audit_log
				where ((target_type = ? and target_id = ?)%s)
				order by created_at desc, id desc
				limit 50
				""".formatted(contractClause), (rs, rowNum) -> {
			String action = rs.getString("action");
			if (!includeOrders && action.startsWith("SALES_ORDER_PROJECT_")) {
				return null;
			}
			String targetSummary = rs.getString("target_summary");
			if (!includeContracts && action.startsWith("SALES_ORDER_PROJECT_")) {
				targetSummary = sanitizeOrderProjectAuditSummary(targetSummary);
			}
			return new OperationResponse(rs.getLong("id"), rs.getString("operator_username"), action,
					rs.getString("target_type"), rs.getString("target_id"), targetSummary,
					rs.getObject("created_at", OffsetDateTime.class));
		}, args.toArray()).stream().filter(Objects::nonNull).toList();
	}

	private String sanitizeOrderProjectAuditSummary(String targetSummary) {
		if (!hasText(targetSummary)) {
			return targetSummary;
		}
		int marker = targetSummary.indexOf(" 项目合同关联 ");
		if (marker < 0) {
			return "销售订单项目合同关联已变更";
		}
		return targetSummary.substring(0, marker) + " 项目合同关联已变更";
	}

	private SalesFulfillmentResponse salesFulfillmentResponse(FulfillmentProjectRow project, boolean includeLegacy,
			CurrentUser currentUser) {
		boolean contractVisible = currentUser.permissions().contains("sales:contract:view");
		boolean creditVisible = currentUser.permissions().contains("sales:credit:view");
		List<String> blockReasons = fulfillmentBlockReasons(project.id());
		boolean closeBlocked = !blockReasons.isEmpty();
		String actionDisabledReason = closeBlocked ? String.join(",", blockReasons) : null;
		List<String> allowedActions = "OPEN".equals(project.salesFulfillmentStatus()) && !closeBlocked
				&& currentUser.permissions().contains("sales:fulfillment:close") ? List.of("CLOSE") : List.of();
		BigDecimal orderAmount = moneyValue("""
				select coalesce(sum(l.tax_included_amount), 0)
				from sal_sales_order o
				join sal_sales_order_line l on l.order_id = o.id
				where o.project_id = ?
				and o.status <> 'CANCELLED'
				""", project.id());
		BigDecimal shippedAmount = moneyValue("""
				select coalesce(sum(sl.tax_included_amount), 0)
				from sal_sales_shipment_line sl
				join sal_sales_shipment sh on sh.id = sl.shipment_id
				join sal_sales_order o on o.id = sh.order_id
				where o.project_id = ?
				and sh.status = 'POSTED'
				""", project.id());
		BigDecimal returnedAmount = moneyValue("""
				select coalesce(sum(rl.amount), 0)
				from sal_sales_return_line rl
				join sal_sales_return r on r.id = rl.return_id
				join sal_sales_shipment sh on sh.id = r.source_shipment_id
				join sal_sales_order o on o.id = sh.order_id
				where o.project_id = ?
				and r.status = 'POSTED'
				""", project.id());
		BigDecimal contractAmount = moneyValue("""
				select coalesce(sum(amount), 0)
				from sal_project_contract
				where project_id = ?
				and status = 'EFFECTIVE'
				""", project.id());
		BigDecimal plannedQuantity = projectOrderQuantity(project.id());
		BigDecimal shippedQuantity = projectShippedQuantity(project.id());
		BigDecimal returnedQuantity = projectReturnedQuantity(project.id());
		BigDecimal netDeliveredQuantity = shippedQuantity.subtract(returnedQuantity);
		List<FulfillmentOrderResponse> legacyCompatibleOrders = legacyFulfillmentOrders(project.id());
		List<FulfillmentOrderResponse> visibleLegacyOrders = includeLegacy ? legacyCompatibleOrders : List.of();
		return new SalesFulfillmentResponse(project.id(), project.projectNo(), project.name(),
				project.customerId(), project.customerCode(), project.customerName(),
				project.salesFulfillmentStatus(), closeBlocked, actionDisabledReason,
				contractVisible ? moneyString(contractAmount) : null, moneyString(orderAmount), moneyString(shippedAmount),
				moneyString(returnedAmount), moneyString(shippedAmount.subtract(returnedAmount)),
				quantityString(openDemandQuantity(project.id())), !creditVisible,
				creditVisible ? moneyString(customerCreditLimit(project.customerId())) : null,
				creditVisible ? moneyString(customerUsedCredit(project.customerId())) : null,
				salesFulfillmentOrders(project.id()), effectiveDemandSummaries(project.id()),
				visibleLegacyOrders, !legacyCompatibleOrders.isEmpty(), project.version(),
				project.salesFulfillmentStatus(), !contractVisible,
				contractVisible ? moneyString(contractAmount) : null, moneyString(orderAmount),
				quantityString(plannedQuantity), quantityString(shippedQuantity), quantityString(returnedQuantity),
				quantityString(netDeliveredQuantity), overduePlanCount(project.id()),
				creditVisible ? null : "信用信息无权限查看", blockReasons, allowedActions, actionDisabledReason);
	}

	private FulfillmentProjectRow fulfillmentProject(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.project_no, p.name, p.customer_id, c.code as customer_code, c.name as customer_name,
				       p.sales_fulfillment_status, p.version
				from sal_project p
				join mst_customer c on c.id = p.customer_id
				where p.id = ?
				""", this::mapFulfillmentProject, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
	}

	private FulfillmentProjectRow lockFulfillmentProject(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.project_no, p.name, p.customer_id, c.code as customer_code, c.name as customer_name,
				       p.sales_fulfillment_status, p.version
				from sal_project p
				join mst_customer c on c.id = p.customer_id
				where p.id = ?
				for update of p
				""", this::mapFulfillmentProject, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
	}

	private FulfillmentProjectRow mapFulfillmentProject(ResultSet rs, int rowNum) throws SQLException {
		return new FulfillmentProjectRow(rs.getLong("id"), rs.getString("project_no"), rs.getString("name"),
				rs.getLong("customer_id"), rs.getString("customer_code"), rs.getString("customer_name"),
				rs.getString("sales_fulfillment_status"), rs.getLong("version"));
	}

	private List<FulfillmentOrderResponse> salesFulfillmentOrders(Long projectId) {
		return this.jdbcTemplate.query("""
				select o.id, o.order_no, o.status, coalesce(sum(l.tax_included_amount), 0) as tax_included_amount,
				       o.version
				from sal_sales_order o
				left join sal_sales_order_line l on l.order_id = o.id
				where o.project_id = ?
				group by o.id, o.order_no, o.status, o.version
				order by o.id asc
				""", (rs, rowNum) -> new FulfillmentOrderResponse(rs.getLong("id"), rs.getString("order_no"),
				rs.getString("status"), moneyString(rs.getBigDecimal("tax_included_amount")), rs.getLong("version")),
				projectId);
	}

	private List<EffectiveDemandSummaryResponse> effectiveDemandSummaries(Long projectId) {
		return this.jdbcTemplate.query("""
				select order_id, order_no, order_line_id, material_id, material_code, material_name,
				       open_demand_quantity, counted_as_effective_demand, excluded_reason_code
				from sal_effective_sales_demand
				where project_id = ?
				order by order_id asc, order_line_id asc
				""", (rs, rowNum) -> new EffectiveDemandSummaryResponse(rs.getLong("order_id"),
				rs.getString("order_no"), rs.getLong("order_line_id"), rs.getLong("material_id"),
				rs.getString("material_code"), rs.getString("material_name"),
				quantityString(rs.getBigDecimal("open_demand_quantity")),
				rs.getBoolean("counted_as_effective_demand"), rs.getString("excluded_reason_code")), projectId);
	}

	private List<FulfillmentOrderResponse> legacyFulfillmentOrders(Long projectId) {
		return this.jdbcTemplate.query("""
				select o.id, o.order_no, o.status, coalesce(sum(l.tax_included_amount), 0) as tax_included_amount,
				       o.version
				from sal_sales_order o
				left join sal_sales_order_line l on l.order_id = o.id
				where o.project_id = ?
				and o.status = 'SHIPPED'
				and o.sales_fulfillment_compatible = true
				group by o.id, o.order_no, o.status, o.version
				order by o.id asc
				""", (rs, rowNum) -> new FulfillmentOrderResponse(rs.getLong("id"), rs.getString("order_no"),
				rs.getString("status"), moneyString(rs.getBigDecimal("tax_included_amount")), rs.getLong("version")),
				projectId);
	}

	private List<String> fulfillmentBlockReasons(Long projectId) {
		List<String> reasons = new ArrayList<>();
		if (openDemandCount(projectId) > 0) {
			reasons.add("OPEN_DEMAND");
		}
		if (exists("""
				select count(*)
				from sal_sales_order
				where project_id = ?
				and not (
					status in ('CLOSED', 'CANCELLED')
					or (status = 'SHIPPED' and sales_fulfillment_compatible = true)
				)
				""", projectId)) {
			reasons.add("NON_TERMINAL_ORDER");
		}
		if (exists("""
				select count(*)
				from sal_sales_delivery_plan p
				join sal_sales_order o on o.id = p.order_id
				where o.project_id = ?
				and p.status in ('PLANNED', 'PARTIALLY_SHIPPED')
				and p.planned_quantity > p.shipped_quantity
				""", projectId)) {
			reasons.add("OPEN_DELIVERY_PLAN");
		}
		if (exists("""
				select count(*)
				from sal_sales_quote
				where project_id = ?
				and status in ('DRAFT', 'APPROVED')
				and converted_order_id is null
				and converted_contract_id is null
				""", projectId)) {
			reasons.add("PENDING_QUOTE_CONVERSION");
		}
		if (exists("""
				select count(*)
				from sal_sales_order_change c
				join sal_sales_order o on o.id = c.order_id
				left join platform_approval_instance ai on ai.id = c.approval_instance_id
				where o.project_id = ?
				and (
					c.status = 'DRAFT'
					or ai.status = 'SUBMITTED'
				)
				""", projectId)) {
			reasons.add("PENDING_ORDER_CHANGE");
		}
		if (exists("""
				select count(*)
				from platform_approval_instance ai
				where ai.status = 'SUBMITTED'
				and (
					(ai.scene_code = 'SALES_QUOTE_APPROVAL'
					 and exists (select 1 from sal_sales_quote q where q.project_id = ? and q.id = ai.business_object_id))
					or (ai.scene_code in ('SALES_ORDER_CREDIT_OVERRIDE', 'SALES_ORDER_SHORT_CLOSE')
					 and exists (select 1 from sal_sales_order o where o.project_id = ? and o.id = ai.business_object_id))
					or (ai.scene_code in ('SALES_ORDER_CHANGE_APPROVAL', 'SALES_ORDER_CHANGE_CREDIT_OVERRIDE')
					 and exists (
						select 1
						from sal_sales_order_change c
						join sal_sales_order o on o.id = c.order_id
						where o.project_id = ?
						and c.id = ai.business_object_id
					 ))
				)
				""", projectId, projectId, projectId)) {
			reasons.add("PENDING_APPROVAL");
		}
		return reasons;
	}

	private boolean exists(String sql, Object... args) {
		Long count = this.jdbcTemplate.queryForObject(sql, Long.class, args);
		return count != null && count > 0;
	}

	private void requirePermission(CurrentUser currentUser, String permissionCode) {
		if (!hasPermission(currentUser, permissionCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private boolean hasPermission(CurrentUser currentUser, String permissionCode) {
		return currentUser != null && currentUser.permissions().contains(permissionCode);
	}

	private long openDemandCount(Long projectId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_effective_sales_demand
				where project_id = ?
				and counted_as_effective_demand = true
				and open_demand_quantity > 0
				""", Long.class, projectId);
		return count == null ? 0 : count;
	}

	private BigDecimal openDemandQuantity(Long projectId) {
		return moneyValue("""
				select coalesce(sum(open_demand_quantity), 0)
				from sal_effective_sales_demand
				where project_id = ?
				and counted_as_effective_demand = true
				""", projectId);
	}

	private BigDecimal projectOrderQuantity(Long projectId) {
		return moneyValue("""
				select coalesce(sum(l.quantity), 0)
				from sal_sales_order_line l
				join sal_sales_order o on o.id = l.order_id
				where o.project_id = ?
				and o.status <> 'CANCELLED'
				""", projectId);
	}

	private BigDecimal projectShippedQuantity(Long projectId) {
		return moneyValue("""
				select coalesce(sum(sl.quantity), 0)
				from sal_sales_shipment_line sl
				join sal_sales_shipment sh on sh.id = sl.shipment_id
				join sal_sales_order o on o.id = sh.order_id
				where o.project_id = ?
				and sh.status = 'POSTED'
				""", projectId);
	}

	private BigDecimal projectReturnedQuantity(Long projectId) {
		return moneyValue("""
				select coalesce(sum(rl.quantity), 0)
				from sal_sales_return_line rl
				join sal_sales_return r on r.id = rl.return_id
				join sal_sales_shipment sh on sh.id = r.source_shipment_id
				join sal_sales_order o on o.id = sh.order_id
				where o.project_id = ?
				and r.status = 'POSTED'
				""", projectId);
	}

	private long overduePlanCount(Long projectId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_delivery_plan p
				join sal_sales_order o on o.id = p.order_id
				where o.project_id = ?
				and p.status in ('PLANNED', 'PARTIALLY_SHIPPED')
				and p.planned_quantity > p.shipped_quantity
				and p.planned_date < current_date
				""", Long.class, projectId);
		return count == null ? 0 : count;
	}

	private BigDecimal customerCreditLimit(Long customerId) {
		return moneyValue("""
				select coalesce(max(credit_limit), 0)
				from sal_customer_credit_profile
				where customer_id = ?
				and status = 'ACTIVE'
				""", customerId);
	}

	private BigDecimal customerUsedCredit(Long customerId) {
		return moneyValue("""
				select coalesce(sum(o.tax_included_amount), 0)
				from sal_sales_order o
				where o.customer_id = ?
				and o.status in ('CONFIRMED', 'PARTIALLY_SHIPPED')
				""", customerId);
	}

	private BigDecimal moneyValue(String sql, Object... args) {
		BigDecimal value = this.jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
		return value == null ? ZERO : value;
	}

	private SalesFulfillmentResponse idempotentFulfillmentCloseResult(Long projectId, FulfillmentCloseRequest request,
			CurrentUser operator) {
		List<ExistingAction> existing = this.jdbcTemplate.query("""
				select result_resource_id, request_fingerprint
				from sal_action_idempotency
				where operator_user_id = ?
				and action = 'CLOSE_SALES_FULFILLMENT'
				and resource_type = 'SALES_PROJECT'
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingAction(rs.getLong("result_resource_id"),
				rs.getString("request_fingerprint")), operator.id(), projectId, request.idempotencyKey().trim());
		if (existing.isEmpty()) {
			return null;
		}
		ExistingAction action = existing.getFirst();
		if (!action.requestFingerprint().equals(fulfillmentCloseFingerprint(projectId, request))) {
			throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
		}
		return salesFulfillment(action.resultResourceId(), false, operator);
	}

	private void recordFulfillmentCloseIdempotency(Long projectId, FulfillmentCloseRequest request, Long resultVersion,
			CurrentUser operator) {
		try {
			this.jdbcTemplate.update("""
					insert into sal_action_idempotency (
						operator_user_id, action, resource_type, resource_id, resource_version, idempotency_key,
						request_fingerprint, result_resource_type, result_resource_id, result_version
					)
					values (?, 'CLOSE_SALES_FULFILLMENT', 'SALES_PROJECT', ?, ?, ?, ?, 'SALES_PROJECT', ?, ?)
					""", operator.id(), projectId, request.version(), request.idempotencyKey().trim(),
					fulfillmentCloseFingerprint(projectId, request), projectId, resultVersion);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
		}
	}

	private void validateFulfillmentCloseRequest(FulfillmentCloseRequest request) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateReason(request.reason());
	}

	private String fulfillmentCloseFingerprint(Long projectId, FulfillmentCloseRequest request) {
		return sha256("CLOSE_SALES_FULFILLMENT|SALES_PROJECT|" + projectId + "|" + request.version() + "|"
				+ blankToNull(request.reason()));
	}

	private QueryParts projectQueryParts(String keyword, Long customerId, String status, Long ownerUserId,
			LocalDate plannedStartFrom, LocalDate plannedStartTo, LocalDate plannedFinishFrom,
			LocalDate plannedFinishTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(p.project_no ilike ? or p.name ilike ? or c.code ilike ? or c.name ilike ? "
					+ "or p.remark ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (customerId != null) {
			conditions.add("p.customer_id = ?");
			args.add(customerId);
		}
		if (hasText(status)) {
			parseStatus(status);
			conditions.add("p.status = ?");
			args.add(status);
		}
		if (ownerUserId != null) {
			conditions.add("p.owner_user_id = ?");
			args.add(ownerUserId);
		}
		if (plannedStartFrom != null) {
			conditions.add("p.planned_start_date >= ?");
			args.add(plannedStartFrom);
		}
		if (plannedStartTo != null) {
			conditions.add("p.planned_start_date <= ?");
			args.add(plannedStartTo);
		}
		if (plannedFinishFrom != null) {
			conditions.add("p.planned_finish_date >= ?");
			args.add(plannedFinishFrom);
		}
		if (plannedFinishTo != null) {
			conditions.add("p.planned_finish_date <= ?");
			args.add(plannedFinishTo);
		}
		return where(conditions, args);
	}

	private ValidatedProject validateCreate(ProjectCreateRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String name = validateName(request.name());
		validateCustomer(request.customerId());
		validateOwner(request.ownerUserId());
		validatePlanRange(request.plannedStartDate(), request.plannedFinishDate());
		BigDecimal revenue = validateTarget(request.targetRevenue());
		BigDecimal cost = validateTarget(request.targetCost());
		validateOptionalText(request.remark(), 500);
		return new ValidatedProject(name, request.customerId(), request.ownerUserId(), request.plannedStartDate(),
				request.plannedFinishDate(), revenue, cost, request.remark());
	}

	private ValidatedProjectUpdate validateUpdate(ProjectUpdateRequest request, ProjectRow current) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String name = current.name();
		if (request.name() != null) {
			if (current.status() != SalesProjectStatus.DRAFT) {
				throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
			}
			name = validateName(request.name());
		}
		validateOwner(request.ownerUserId());
		validatePlanRange(request.plannedStartDate(), request.plannedFinishDate());
		BigDecimal revenue = validateTarget(request.targetRevenue());
		BigDecimal cost = validateTarget(request.targetCost());
		validateOptionalText(request.remark(), 500);
		return new ValidatedProjectUpdate(name, request.ownerUserId(), request.plannedStartDate(),
				request.plannedFinishDate(), revenue, cost, request.remark());
	}

	private String validateName(String name) {
		if (!hasText(name) || name.length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return name;
	}

	private void validateCustomer(Long customerId) {
		String status = this.jdbcTemplate.query("select status from mst_customer where id = ?",
				(rs, rowNum) -> rs.getString("status"), customerId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.SALES_CUSTOMER_INVALID);
		}
	}

	private void validateOwner(Long ownerUserId) {
		if (ownerUserId == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String status = this.jdbcTemplate.query("select status from sys_user where id = ?",
				(rs, rowNum) -> rs.getString("status"), ownerUserId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void validatePlanRange(LocalDate start, LocalDate finish) {
		if (start != null && finish != null && start.isAfter(finish)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private BigDecimal validateTarget(BigDecimal value) {
		BigDecimal normalized = value == null ? ZERO : value;
		if (normalized.compareTo(ZERO) < 0 || normalized.scale() > 2 || normalized.precision() - normalized.scale() > 16) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return normalized;
	}

	private String validateReason(String value) {
		if (!hasText(value) || value.length() > 200) {
			throw new BusinessException(ApiErrorCode.PROJECT_REASON_REQUIRED);
		}
		return value;
	}

	private void requireVersion(long currentVersion, Long requestedVersion) {
		if (requestedVersion == null || requestedVersion != currentVersion) {
			throw new BusinessException(ApiErrorCode.PROJECT_CONCURRENT_MODIFICATION);
		}
	}

	private String changedFields(ProjectRow current, ValidatedProjectUpdate update) {
		List<String> fields = new ArrayList<>();
		if (!Objects.equals(current.name(), update.name())) {
			fields.add("name");
		}
		if (!Objects.equals(current.ownerUserId(), update.ownerUserId())) {
			fields.add("ownerUserId");
		}
		if (!Objects.equals(current.plannedStartDate(), update.plannedStartDate())) {
			fields.add("plannedStartDate");
		}
		if (!Objects.equals(current.plannedFinishDate(), update.plannedFinishDate())) {
			fields.add("plannedFinishDate");
		}
		if (current.targetRevenue().compareTo(update.targetRevenue()) != 0) {
			fields.add("targetRevenue");
		}
		if (current.targetCost().compareTo(update.targetCost()) != 0) {
			fields.add("targetCost");
		}
		if (!Objects.equals(current.remark(), update.remark())) {
			fields.add("remark");
		}
		return fields.isEmpty() ? "无" : String.join(",", fields);
	}

	private SalesProjectStatus parseStatus(String status) {
		try {
			return SalesProjectStatus.valueOf(status);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
		}
	}

	private java.util.Optional<ProjectRow> lockProject(Long id) {
		return this.jdbcTemplate.query("""
				select id, name, customer_id, owner_user_id, planned_start_date, planned_finish_date, status,
				       target_revenue, target_cost, remark, version
				from sal_project
				where id = ?
				for update
				""", (rs, rowNum) -> new ProjectRow(rs.getLong("id"), rs.getString("name"),
				rs.getLong("customer_id"), nullableLong(rs, "owner_user_id"), rs.getObject("planned_start_date", LocalDate.class),
				rs.getObject("planned_finish_date", LocalDate.class),
				SalesProjectStatus.valueOf(rs.getString("status")), rs.getBigDecimal("target_revenue"),
				rs.getBigDecimal("target_cost"), rs.getString("remark"), rs.getLong("version")), id)
			.stream()
			.findFirst();
	}

	private String nextNo(String prefix, AtomicInteger sequence) {
		return prefix + OffsetDateTime.now().format(NUMBER_FORMATTER) + String.format("%03d",
				Math.floorMod(sequence.incrementAndGet(), 1000));
	}

	private void validateOptionalText(String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private static QueryParts where(List<String> conditions, List<Object> args) {
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static String moneyString(BigDecimal value) {
		return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
	}

	private static String quantityString(BigDecimal value) {
		return value == null ? null : value.setScale(6, java.math.RoundingMode.HALF_UP).toPlainString();
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

	public record ProjectCreateRequest(@NotNull String name, @NotNull Long customerId, @NotNull Long ownerUserId,
			LocalDate plannedStartDate, LocalDate plannedFinishDate, BigDecimal targetRevenue, BigDecimal targetCost,
			String remark) {
	}

	public record ProjectUpdateRequest(String name, Long ownerUserId, LocalDate plannedStartDate, LocalDate plannedFinishDate,
			BigDecimal targetRevenue, BigDecimal targetCost, String remark, @NotNull Long version) {
	}

	public record VersionedActionRequest(@NotNull Long version, String reason) {
	}

	public record FulfillmentCloseRequest(@NotNull Long version, String reason, @NotNull String idempotencyKey) {
	}

	public record ProjectResponse(Long id, String projectNo, String name, Long customerId, String customerCode,
			String customerName, Long ownerUserId, String ownerUsername, String ownerDisplayName,
			LocalDate plannedStartDate, LocalDate plannedFinishDate, String status, BigDecimal targetRevenue,
			BigDecimal targetCost, String remark, Long mainContractId, String mainContractNo,
			String mainContractStatus, BigDecimal effectiveContractAmount, Long contractCount,
			Long supplementContractCount, Long salesOrderCount, boolean contractSummaryRestricted,
			boolean salesOrderSummaryRestricted, List<SalesProjectContractService.ContractResponse> contracts,
			SalesOrderProjectLinkService.SalesOrderSummary salesOrderSummary, List<OperationResponse> operations,
			String createdByName, OffsetDateTime createdAt, String updatedByName, OffsetDateTime updatedAt,
			String activatedByName, OffsetDateTime activatedAt, String closedByName, OffsetDateTime closedAt,
			String closeReason, String cancelledByName, OffsetDateTime cancelledAt, String cancelReason,
			Long version) {
	}

	public record OwnerCandidateResponse(Long userId, String username, String displayName) {
	}

	public record SalesFulfillmentResponse(Long projectId, String projectNo, String projectName, Long customerId,
			String customerCode, String customerName, String salesFulfillmentStatus, boolean closeBlocked,
			String closeBlockedReason, String contractAmount, String orderAmount, String shippedAmount,
			String returnedAmount, String netFulfilledAmount, String openDemandQuantity, boolean creditRestricted,
			String creditLimit, String usedCredit, List<FulfillmentOrderResponse> salesOrders,
			List<EffectiveDemandSummaryResponse> effectiveDemands,
			List<FulfillmentOrderResponse> legacyDeliveryPlanCompatibleOrders,
			boolean legacyDeliveryPlanCompatible, Long version, String status, boolean contractRestricted,
			String contractEffectiveAmount, String orderTaxIncludedAmount, String plannedQuantity,
			String shippedQuantity, String returnedQuantity, String netDeliveredQuantity, Long overduePlanCount,
			String creditRiskSummary, List<String> blockReasons, List<String> allowedActions, String actionDisabledReason) {
	}

	public record ProductionSummaryResponse(Long projectId, Long workOrderCount, Long releasedWorkOrderCount,
			Long completedWorkOrderCount, Long outsourcingOrderCount, Long outsourcingInProgressCount,
			Long outsourcingCompletedCount, String plannedQuantity, String completedQuantity,
			String outsourcingPlannedQuantity, String outsourcingReceivedQuantity, boolean costVisible,
			String costRestrictedReason, String latestWorkOrderNo, String latestOutsourcingOrderNo) {
	}

	public record FulfillmentOrderResponse(Long orderId, String orderNo, String status, String taxIncludedAmount,
			Long version) {
	}

	public record EffectiveDemandSummaryResponse(Long orderId, String orderNo, Long orderLineId, Long materialId,
			String materialCode, String materialName, String openDemandQuantity, boolean countedAsEffectiveDemand,
			String excludedReasonCode) {
	}

	public record OperationResponse(Long id, String operatorUsername, String action, String targetType,
			String targetId, String targetSummary, OffsetDateTime createdAt) {
	}

	private record ProjectBase(Long id, String projectNo, String name, Long customerId, String customerCode,
			String customerName, Long ownerUserId, String ownerUsername, String ownerDisplayName,
			LocalDate plannedStartDate, LocalDate plannedFinishDate, String status, BigDecimal targetRevenue,
			BigDecimal targetCost, String remark, String createdByName, OffsetDateTime createdAt,
			String updatedByName, OffsetDateTime updatedAt, String activatedByName, OffsetDateTime activatedAt,
			String closedByName, OffsetDateTime closedAt, String closeReason, String cancelledByName,
			OffsetDateTime cancelledAt, String cancelReason, Long version) {
	}

	private record ValidatedProject(String name, Long customerId, Long ownerUserId, LocalDate plannedStartDate,
			LocalDate plannedFinishDate, BigDecimal targetRevenue, BigDecimal targetCost, String remark) {
	}

	private record ValidatedProjectUpdate(String name, Long ownerUserId, LocalDate plannedStartDate, LocalDate plannedFinishDate,
			BigDecimal targetRevenue, BigDecimal targetCost, String remark) {
	}

	private record ProjectRow(Long id, String name, Long customerId, Long ownerUserId, LocalDate plannedStartDate,
			LocalDate plannedFinishDate, SalesProjectStatus status, BigDecimal targetRevenue, BigDecimal targetCost,
			String remark, long version) {
	}

	private record ContractSummary(Long mainContractId, String mainContractNo, String mainContractStatus,
			BigDecimal effectiveContractAmount, Long contractCount, Long supplementContractCount) {

		static ContractSummary restricted() {
			return new ContractSummary(null, null, null, null, null, null);
		}
	}

	private record QueryParts(String where, List<Object> args) {
	}

	private record FulfillmentProjectRow(Long id, String projectNo, String name, Long customerId, String customerCode,
			String customerName, String salesFulfillmentStatus, Long version) {
	}

	private record ExistingAction(Long resultResourceId, String requestFingerprint) {
	}

	private record ProductionWorkOrderAggregate(Long workOrderCount, Long releasedWorkOrderCount,
			Long completedWorkOrderCount, BigDecimal plannedQuantity, BigDecimal completedQuantity,
			String latestWorkOrderNo) {
	}

	private record ProductionOutsourcingAggregate(Long outsourcingOrderCount, Long outsourcingInProgressCount,
			Long outsourcingCompletedCount, BigDecimal plannedQuantity, BigDecimal receivedQuantity,
			String latestOutsourcingOrderNo) {
	}

}
