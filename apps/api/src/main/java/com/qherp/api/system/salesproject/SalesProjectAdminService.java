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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
			int page, int pageSize, CurrentUser currentUser) {
		QueryParts queryParts = projectQueryParts(keyword, customerId, status, ownerUserId);
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

	private QueryParts projectQueryParts(String keyword, Long customerId, String status, Long ownerUserId) {
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

	public record ProjectCreateRequest(@NotNull String name, @NotNull Long customerId, @NotNull Long ownerUserId,
			LocalDate plannedStartDate, LocalDate plannedFinishDate, BigDecimal targetRevenue, BigDecimal targetCost,
			String remark) {
	}

	public record ProjectUpdateRequest(String name, Long ownerUserId, LocalDate plannedStartDate, LocalDate plannedFinishDate,
			BigDecimal targetRevenue, BigDecimal targetCost, String remark, @NotNull Long version) {
	}

	public record VersionedActionRequest(@NotNull Long version, String reason) {
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

}
