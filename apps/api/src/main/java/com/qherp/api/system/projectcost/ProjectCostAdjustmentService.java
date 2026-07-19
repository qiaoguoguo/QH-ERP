package com.qherp.api.system.projectcost;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.platform.PlatformApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ProjectCostAdjustmentService {

	private static final String TARGET_TYPE = "PROJECT_COST_ADJUSTMENT";

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger NUMBER_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final PlatformApprovalService approvalService;

	private final AuditService auditService;

	public ProjectCostAdjustmentService(JdbcTemplate jdbcTemplate, @Lazy PlatformApprovalService approvalService,
			AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.approvalService = approvalService;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<PublicExpenseCandidateResponse> publicExpenseCandidates(String keyword, Long supplierId,
			LocalDate businessDateFrom, LocalDate businessDateTo, int page, int pageSize, CurrentUser currentUser) {
		QueryParts query = publicExpenseQuery(keyword, supplierId, businessDateFrom, businessDateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_expense_line l
				join fin_expense e on e.id = l.expense_id
				%s
				""".formatted(query.where()), Long.class, query.args().toArray());
		List<Object> args = new ArrayList<>(query.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<PublicExpenseCandidateResponse> items = this.jdbcTemplate.query("""
				select e.id as expense_id, e.expense_no, l.id as expense_line_id, l.expense_category,
				       l.description, l.tax_excluded_amount,
				       coalesce((
					       select sum(al.amount)
					       from prj_cost_adjustment_line al
					       join prj_cost_adjustment a on a.id = al.adjustment_id
					       where al.public_expense_line_id = l.id
					       and a.status in ('SUBMITTED', 'CONFIRMED')
				       ), 0) as allocated_amount
				from fin_expense_line l
				join fin_expense e on e.id = l.expense_id
				%s
				order by e.expense_date desc, e.id desc, l.id
				limit ? offset ?
				""".formatted(query.where()), (rs, rowNum) -> mapCandidate(rs, currentUser), args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional
	public AdjustmentResponse create(AdjustmentRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		request = normalized(request);
		validateRequest(request);
		requireVisiblePublicExpenseSources(request, operator);
		String requestFingerprint = fingerprint(request.toString());
		AdjustmentState existing = idempotentAdjustment(operator.username(), request.idempotencyKey());
		if (existing != null) {
			if (!requestFingerprint.equals(existing.requestFingerprint())) {
				throw new BusinessException(ApiErrorCode.PROJECT_COST_IDEMPOTENCY_CONFLICT);
			}
			return get(existing.id(), operator);
		}
		for (AdjustmentLineRequest line : request.lines()) {
			validateAvailable(line.publicExpenseLineId(), line.amount(), null);
		}
		OffsetDateTime now = OffsetDateTime.now();
		Long id;
		try {
			id = this.jdbcTemplate.queryForObject("""
					insert into prj_cost_adjustment (
						adjustment_no, adjustment_type, business_date, status, reason, idempotency_key,
						request_fingerprint, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, nextAdjustmentNo(), request.adjustmentType(), request.businessDate(),
					request.reason(), request.idempotencyKey(), requestFingerprint, operator.username(), now,
					operator.username(), now);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_IDEMPOTENCY_CONFLICT);
		}
		int lineNo = 1;
		for (AdjustmentLineRequest line : request.lines()) {
			this.jdbcTemplate.update("""
					insert into prj_cost_adjustment_line (
						adjustment_id, line_no, project_id, cost_category, cost_stage, direction, amount,
						public_expense_line_id, reason, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", id, lineNo++, line.projectId(), line.costCategory(), line.costStage(), line.direction(),
					line.amount(), line.publicExpenseLineId(), line.reason(), now, now);
		}
		this.auditService.record(operator, "PROJECT_COST_ADJUSTMENT_CREATE", TARGET_TYPE, id, request.reason(),
				servletRequest);
		return get(id, operator);
	}

	@Transactional(readOnly = true)
	public PageResponse<AdjustmentResponse> list(AdjustmentListFilter filter, int page, int pageSize,
			CurrentUser currentUser) {
		QueryParts query = adjustmentQuery(filter);
		List<Object> args = new ArrayList<>(query.args());
		String where = query.where();
		long total = this.jdbcTemplate.queryForObject("select count(*) from prj_cost_adjustment " + where, Long.class,
				args.toArray());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<AdjustmentResponse> items = this.jdbcTemplate.query("""
				select *
				from prj_cost_adjustment
				%s
				order by business_date desc, id desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> mapAdjustment(rs, currentUser), args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public AdjustmentResponse get(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select *
				from prj_cost_adjustment
				where id = ?
				""", (rs, rowNum) -> mapAdjustment(rs, currentUser), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_COST_PROJECT_INVALID));
	}

	@Transactional
	public AdjustmentResponse update(Long id, AdjustmentRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		AdjustmentState state = lockAdjustment(id);
		if (!"DRAFT".equals(state.status())) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_ACTION_NOT_ALLOWED);
		}
		request = normalized(request);
		validateRequest(request);
		requireVisiblePublicExpenseSources(request, operator);
		for (AdjustmentLineRequest line : request.lines()) {
			validateAvailable(line.publicExpenseLineId(), line.amount(), id);
		}
		this.jdbcTemplate.update("""
				update prj_cost_adjustment
				set adjustment_type = ?, business_date = ?, reason = ?, request_fingerprint = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", request.adjustmentType(), request.businessDate(), request.reason(), fingerprint(request.toString()),
				operator.username(), OffsetDateTime.now(), id);
		this.jdbcTemplate.update("delete from prj_cost_adjustment_line where adjustment_id = ?", id);
		int lineNo = 1;
		for (AdjustmentLineRequest line : request.lines()) {
			this.jdbcTemplate.update("""
					insert into prj_cost_adjustment_line (
						adjustment_id, line_no, project_id, cost_category, cost_stage, direction, amount,
						public_expense_line_id, reason, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", id, lineNo++, line.projectId(), line.costCategory(), line.costStage(), line.direction(),
					line.amount(), line.publicExpenseLineId(), line.reason(), OffsetDateTime.now(), OffsetDateTime.now());
		}
		this.auditService.record(operator, "PROJECT_COST_ADJUSTMENT_UPDATE", TARGET_TYPE, id, state.adjustmentNo(),
				servletRequest);
		return get(id, operator);
	}

	@Transactional
	public AdjustmentResponse submit(Long id, SubmitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		AdjustmentState state = lockAdjustment(id);
		requireVersion(state, request.version());
		if (!"DRAFT".equals(state.status())) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_ACTION_NOT_ALLOWED);
		}
		validatePersistedAdjustmentMatrix(id);
		requireVisiblePublicExpenseSources(id, operator);
		validateAdjustmentAvailability(id, false);
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService.submitProjectCostAdjustment(id,
				new PlatformApprovalService.ApprovalSubmitRequest(state.version(), request.reason(),
						request.idempotencyKey()),
				operator, servletRequest);
		int updated = this.jdbcTemplate.update("""
				update prj_cost_adjustment
				set status = 'SUBMITTED', approval_instance_id = ?, submitted_by = ?, submitted_at = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ? and status = 'DRAFT'
				""", approval.id(), operator.username(), OffsetDateTime.now(), operator.username(),
				OffsetDateTime.now(), id, state.version());
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_VERSION_CONFLICT);
		}
		long newVersion = version(id);
		this.approvalService.updateBusinessObjectVersion(approval.id(), newVersion);
		this.auditService.record(operator, "PROJECT_COST_ADJUSTMENT_SUBMIT", TARGET_TYPE, id, state.adjustmentNo(),
				servletRequest);
		return get(id, operator);
	}

	@Transactional
	public AdjustmentResponse cancel(Long id, VersionedRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		AdjustmentState state = lockAdjustment(id);
		requireVersion(state, request.version());
		if (!"DRAFT".equals(state.status())) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_ACTION_NOT_ALLOWED);
		}
		this.jdbcTemplate.update("""
				update prj_cost_adjustment
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
		this.auditService.record(operator, "PROJECT_COST_ADJUSTMENT_CANCEL", TARGET_TYPE, id, state.adjustmentNo(),
				servletRequest);
		return get(id, operator);
	}

	@Transactional
	public void confirmFromApproval(Long id, Long version, CurrentUser operator, HttpServletRequest servletRequest) {
		AdjustmentState state = lockAdjustment(id);
		if (!state.version().equals(version) || !"SUBMITTED".equals(state.status())) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_ACTION_NOT_ALLOWED);
		}
		validatePersistedAdjustmentMatrix(id);
		requireVisiblePublicExpenseSources(id, operator);
		validateAdjustmentAvailability(id, true);
		this.jdbcTemplate.update("""
				update prj_cost_adjustment
				set status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
		this.auditService.record(operator, "PROJECT_COST_ADJUSTMENT_CONFIRM", TARGET_TYPE, id,
				state.adjustmentNo(), servletRequest);
	}

	@Transactional
	public void reopenAfterApprovalTerminal(Long id, CurrentUser operator) {
		AdjustmentState state = lockAdjustment(id);
		if (!"SUBMITTED".equals(state.status())) {
			return;
		}
		this.jdbcTemplate.update("""
				update prj_cost_adjustment
				set status = 'REJECTED', updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), id);
	}

	private PublicExpenseCandidateResponse mapCandidate(ResultSet rs, CurrentUser currentUser) throws SQLException {
		boolean sourceVisible = sourceVisible(currentUser);
		boolean amountVisible = sourceVisible && amountVisible(currentUser);
		BigDecimal amount = scale(rs.getBigDecimal("tax_excluded_amount"));
		BigDecimal allocated = scale(rs.getBigDecimal("allocated_amount"));
		BigDecimal available = amount.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
		String expenseNo = rs.getString("expense_no");
		return new PublicExpenseCandidateResponse(sourceVisible ? rs.getLong("expense_id") : null,
				sourceVisible ? expenseNo : null, sourceVisible ? rs.getLong("expense_line_id") : null,
				sourceVisible ? rs.getString("expense_category") : null,
				sourceVisible ? rs.getString("description") : null, amount(amountVisible, amount),
				amount(amountVisible, allocated), amount(amountVisible, available), amountVisible, sourceVisible,
				sourceVisible ? (amountVisible ? null : "无权查看项目成本金额") : "费用来源权限受限，仅返回受限候选",
				sourceVisible ? "费用单 " + expenseNo : "受限公共费用来源");
	}

	private AdjustmentResponse mapAdjustment(ResultSet rs, CurrentUser currentUser) throws SQLException {
		boolean amountVisible = amountVisible(currentUser);
		boolean sourceVisible = sourceVisible(currentUser);
		Long id = rs.getLong("id");
		BigDecimal total = scale(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(amount), 0)
				from prj_cost_adjustment_line
				where adjustment_id = ?
				""", BigDecimal.class, id));
		String status = rs.getString("status");
		return new AdjustmentResponse(id, rs.getString("adjustment_no"), rs.getString("adjustment_type"),
				rs.getObject("business_date", LocalDate.class), status, rs.getString("reason"),
				amount(amountVisible, total), nullableLong(rs, "approval_instance_id"), rs.getLong("version"),
				amountVisible, sourceVisible, sourceVisible ? null : "来源权限受限，仅显示脱敏摘要",
				status, null, null, allowedActions(status), actionDisabledReasons(status),
				lines(id, amountVisible, sourceVisible));
	}

	private List<AdjustmentLineResponse> lines(Long adjustmentId, boolean amountVisible, boolean sourceVisible) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.project_id, p.project_no, p.name as project_name, l.cost_category,
				       l.cost_stage, l.direction, l.amount, l.public_expense_line_id, l.reason, e.expense_no
				from prj_cost_adjustment_line l
				join sal_project p on p.id = l.project_id
				left join fin_expense_line el on el.id = l.public_expense_line_id
				left join fin_expense e on e.id = el.expense_id
				where l.adjustment_id = ?
				order by l.line_no
				""", (rs, rowNum) -> new AdjustmentLineResponse(rs.getLong("id"), rs.getLong("project_id"),
				rs.getString("project_no"), rs.getString("project_name"), rs.getString("cost_category"),
				rs.getString("cost_stage"), rs.getString("direction"), amount(amountVisible, rs.getBigDecimal("amount")),
				sourceVisible ? nullableLong(rs, "public_expense_line_id") : null,
				sourceVisible ? rs.getString("expense_no") : null, rs.getString("reason")), adjustmentId);
	}

	private QueryParts publicExpenseQuery(String keyword, Long supplierId, LocalDate businessDateFrom,
			LocalDate businessDateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("e.status = 'CONFIRMED'");
		conditions.add("e.ownership_type = 'PUBLIC'");
		if (hasText(keyword)) {
			conditions.add("(e.expense_no ilike ? or l.description ilike ?)");
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
		}
		if (supplierId != null) {
			conditions.add("e.supplier_id = ?");
			args.add(supplierId);
		}
		if (businessDateFrom != null) {
			conditions.add("e.expense_date >= ?");
			args.add(businessDateFrom);
		}
		if (businessDateTo != null) {
			conditions.add("e.expense_date <= ?");
			args.add(businessDateTo);
		}
		return new QueryParts("where " + String.join(" and ", conditions), args);
	}

	private QueryParts adjustmentQuery(AdjustmentListFilter filter) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (filter != null) {
			if (hasText(filter.keyword())) {
				conditions.add("(adjustment_no ilike ? or reason ilike ?)");
				args.add("%" + filter.keyword().trim() + "%");
				args.add("%" + filter.keyword().trim() + "%");
			}
			if (hasText(filter.status())) {
				conditions.add("status = ?");
				args.add(filter.status().trim().toUpperCase());
			}
			if (filter.projectId() != null) {
				conditions.add("""
						exists (
							select 1
							from prj_cost_adjustment_line l
							where l.adjustment_id = prj_cost_adjustment.id
							and l.project_id = ?
						)
						""");
				args.add(filter.projectId());
			}
			if (filter.businessDateFrom() != null) {
				conditions.add("business_date >= ?");
				args.add(filter.businessDateFrom());
			}
			if (filter.businessDateTo() != null) {
				conditions.add("business_date <= ?");
				args.add(filter.businessDateTo());
			}
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private void validateRequest(AdjustmentRequest request) {
		if (request == null || !hasText(request.adjustmentType()) || request.businessDate() == null
				|| !hasText(request.reason()) || !hasText(request.idempotencyKey()) || request.lines() == null
				|| request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String adjustmentType = request.adjustmentType().trim().toUpperCase();
		if (!List.of("PROJECT_ADJUSTMENT", "PUBLIC_EXPENSE_ALLOCATION", "VARIANCE_SETTLEMENT")
			.contains(adjustmentType)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		for (AdjustmentLineRequest line : request.lines()) {
			if (line.projectId() == null || !hasText(line.costCategory()) || !hasText(line.costStage())
					|| !hasText(line.direction()) || line.amount() == null
					|| line.amount().compareTo(BigDecimal.ZERO) <= 0) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if (!List.of("MATERIAL", "LABOR", "OUTSOURCING", "MANUFACTURING_OVERHEAD", "PROJECT_EXPENSE",
					"ADJUSTMENT").contains(line.costCategory().trim().toUpperCase())
					|| !List.of("WIP", "FINISHED", "DELIVERED", "DIRECT_PROJECT")
						.contains(line.costStage().trim().toUpperCase())
					|| !List.of("INCREASE", "DECREASE").contains(line.direction().trim().toUpperCase())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			validateAdjustmentLineMatrix(adjustmentType, line.costCategory().trim().toUpperCase(),
					line.costStage().trim().toUpperCase(), line.direction().trim().toUpperCase(),
					line.publicExpenseLineId());
		}
	}

	private void validateAdjustmentLineMatrix(String adjustmentType, String costCategory, String costStage,
			String direction, Long publicExpenseLineId) {
		if ("PUBLIC_EXPENSE_ALLOCATION".equals(adjustmentType)) {
			if (publicExpenseLineId == null || !"MANUFACTURING_OVERHEAD".equals(costCategory)
					|| !"DIRECT_PROJECT".equals(costStage) || !"INCREASE".equals(direction)) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			return;
		}
		if (publicExpenseLineId != null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private AdjustmentRequest normalized(AdjustmentRequest request) {
		if (request == null || request.lines() == null) {
			return request;
		}
		List<AdjustmentLineRequest> lines = request.lines()
			.stream()
			.map((line) -> new AdjustmentLineRequest(line.projectId(),
					line.costCategory() == null ? null : line.costCategory().trim().toUpperCase(),
					line.costStage() == null ? null : line.costStage().trim().toUpperCase(),
					line.direction() == null ? null : line.direction().trim().toUpperCase(), line.amount(),
					line.publicExpenseLineId(), line.reason()))
			.toList();
		return new AdjustmentRequest(request.adjustmentType() == null ? null : request.adjustmentType().trim().toUpperCase(),
				request.businessDate(), request.reason(), request.idempotencyKey(), lines);
	}

	private void validateAvailable(Long publicExpenseLineId, BigDecimal amount, Long currentAdjustmentId) {
		if (publicExpenseLineId == null) {
			return;
		}
		BigDecimal total = scale(this.jdbcTemplate.queryForObject("""
				select l.tax_excluded_amount
				from fin_expense_line l
				join fin_expense e on e.id = l.expense_id
				where l.id = ?
				and e.status = 'CONFIRMED'
				and e.ownership_type = 'PUBLIC'
				""", BigDecimal.class, publicExpenseLineId));
		BigDecimal allocated = scale(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(al.amount), 0)
				from prj_cost_adjustment_line al
				join prj_cost_adjustment a on a.id = al.adjustment_id
				where al.public_expense_line_id = ?
				and a.status = 'CONFIRMED'
				and (cast(? as bigint) is null or a.id <> cast(? as bigint))
				""", BigDecimal.class, publicExpenseLineId, currentAdjustmentId, currentAdjustmentId));
		if (allocated.add(scale(amount)).compareTo(total) > 0) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_ADJUSTMENT_OVER_ALLOCATED);
		}
	}

	private void requireVisiblePublicExpenseSources(AdjustmentRequest request, CurrentUser operator) {
		boolean hasPublicExpenseSource = request.lines()
			.stream()
			.anyMatch((line) -> line.publicExpenseLineId() != null);
		if (hasPublicExpenseSource && !sourceVisible(operator)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void requireVisiblePublicExpenseSources(Long adjustmentId, CurrentUser operator) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from prj_cost_adjustment_line
				where adjustment_id = ?
				and public_expense_line_id is not null
				""", Long.class, adjustmentId);
		if (count != null && count > 0 && !sourceVisible(operator)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void validatePersistedAdjustmentMatrix(Long adjustmentId) {
		String adjustmentType = this.jdbcTemplate.queryForObject("""
				select adjustment_type
				from prj_cost_adjustment
				where id = ?
				""", String.class, adjustmentId);
		this.jdbcTemplate.query("""
				select cost_category, cost_stage, direction, public_expense_line_id
				from prj_cost_adjustment_line
				where adjustment_id = ?
				order by line_no
				""", (rs) -> {
			while (rs.next()) {
				validateAdjustmentLineMatrix(adjustmentType, rs.getString("cost_category"),
						rs.getString("cost_stage"), rs.getString("direction"),
						nullableLong(rs, "public_expense_line_id"));
			}
			return null;
		}, adjustmentId);
	}

	private void validateAdjustmentAvailability(Long adjustmentId, boolean includeCurrentSubmitted) {
		List<AllocationLine> lines = this.jdbcTemplate.query("""
				select public_expense_line_id, sum(amount) as amount
				from prj_cost_adjustment_line
				where adjustment_id = ?
				and public_expense_line_id is not null
				group by public_expense_line_id
				order by public_expense_line_id
				""", (rs, rowNum) -> new AllocationLine(rs.getLong("public_expense_line_id"),
				rs.getBigDecimal("amount")), adjustmentId);
		for (AllocationLine line : lines) {
			lockPublicExpenseLine(line.publicExpenseLineId());
			BigDecimal total = scale(this.jdbcTemplate.queryForObject("""
					select l.tax_excluded_amount
					from fin_expense_line l
					join fin_expense e on e.id = l.expense_id
					where l.id = ?
					and e.status = 'CONFIRMED'
					and e.ownership_type = 'PUBLIC'
					""", BigDecimal.class, line.publicExpenseLineId()));
			String currentFilter = includeCurrentSubmitted ? "" : "and a.id <> ?";
			List<Object> args = new ArrayList<>();
			args.add(line.publicExpenseLineId());
			if (!includeCurrentSubmitted) {
				args.add(adjustmentId);
			}
			BigDecimal allocated = scale(this.jdbcTemplate.queryForObject("""
					select coalesce(sum(al.amount), 0)
					from prj_cost_adjustment_line al
					join prj_cost_adjustment a on a.id = al.adjustment_id
					where al.public_expense_line_id = ?
					and a.status in ('SUBMITTED', 'CONFIRMED')
					%s
					""".formatted(currentFilter), BigDecimal.class, args.toArray()));
			BigDecimal candidate = includeCurrentSubmitted ? allocated : allocated.add(scale(line.amount()));
			if (candidate.compareTo(total) > 0) {
				throw new BusinessException(ApiErrorCode.PROJECT_COST_ADJUSTMENT_OVER_ALLOCATED);
			}
		}
	}

	private void lockPublicExpenseLine(Long publicExpenseLineId) {
		this.jdbcTemplate.queryForObject("""
				select l.id
				from fin_expense_line l
				join fin_expense e on e.id = l.expense_id
				where l.id = ?
				and e.status = 'CONFIRMED'
				and e.ownership_type = 'PUBLIC'
				for update of l
				""", Long.class, publicExpenseLineId);
	}

	private AdjustmentState idempotentAdjustment(String username, String idempotencyKey) {
		if (!hasText(idempotencyKey)) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select id, adjustment_no, status, request_fingerprint, version
				from prj_cost_adjustment
				where created_by = ?
				and idempotency_key = ?
				""", this::mapAdjustmentState, username, idempotencyKey)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private AdjustmentState lockAdjustment(Long id) {
		return this.jdbcTemplate.query("""
				select id, adjustment_no, status, request_fingerprint, version
				from prj_cost_adjustment
				where id = ?
				for update
				""", this::mapAdjustmentState, id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_COST_PROJECT_INVALID));
	}

	private AdjustmentState mapAdjustmentState(ResultSet rs, int rowNum) throws SQLException {
		return new AdjustmentState(rs.getLong("id"), rs.getString("adjustment_no"), rs.getString("status"),
				rs.getString("request_fingerprint"), rs.getLong("version"));
	}

	private long version(Long id) {
		return this.jdbcTemplate.queryForObject("select version from prj_cost_adjustment where id = ?", Long.class,
				id);
	}

	private void requireVersion(AdjustmentState state, Long version) {
		if (!state.version().equals(version)) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_VERSION_CONFLICT);
		}
	}

	private List<String> allowedActions(String status) {
		if ("DRAFT".equals(status)) {
			return List.of("UPDATE", "SUBMIT", "CANCEL");
		}
		return List.of();
	}

	private Map<String, String> actionDisabledReasons(String status) {
		if ("DRAFT".equals(status)) {
			return Map.of();
		}
		return Map.of("UPDATE", "当前状态不允许更新", "SUBMIT", "当前状态不允许提交", "CANCEL", "当前状态不允许取消");
	}

	private String nextAdjustmentNo() {
		return "PCA" + NUMBER_FORMATTER.format(OffsetDateTime.now()) + String.format("%03d",
				NUMBER_SEQUENCE.updateAndGet((value) -> value >= 999 ? 1 : value + 1));
	}

	private boolean amountVisible(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("cost:project-cost:amount-view");
	}

	private boolean sourceVisible(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("finance:expense:view");
	}

	private String amount(boolean amountVisible, BigDecimal amount) {
		return amountVisible ? decimal(amount) : null;
	}

	private static BigDecimal scale(BigDecimal value) {
		return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
	}

	private static String decimal(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private String fingerprint(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	public record AdjustmentRequest(String adjustmentType, LocalDate businessDate, String reason,
			String idempotencyKey, List<AdjustmentLineRequest> lines) {
	}

	public record AdjustmentLineRequest(Long projectId, String costCategory, String costStage, String direction,
			BigDecimal amount, Long publicExpenseLineId, String reason) {
	}

	public record SubmitRequest(Long version, String reason, String idempotencyKey) {
	}

	public record VersionedRequest(Long version, String idempotencyKey) {
	}

	public record AdjustmentListFilter(String keyword, String status, Long projectId, LocalDate businessDateFrom,
			LocalDate businessDateTo) {
	}

	public record AdjustmentResponse(Long id, String adjustmentNo, String adjustmentType, LocalDate businessDate,
			String status, String reason, String totalAmount, Long approvalInstanceId, Long version,
			boolean amountVisible, boolean sourceVisible, String restrictedReason, String approvalStatus,
			String rejectedReason, String originalAdjustmentNo, List<String> allowedActions,
			Map<String, String> actionDisabledReasons, List<AdjustmentLineResponse> lines) {
	}

	public record AdjustmentLineResponse(Long id, Long projectId, String projectNo, String projectName,
			String costCategory, String costStage, String direction, String amount, Long publicExpenseLineId,
			String sourceNo, String reason) {
	}

	public record PublicExpenseCandidateResponse(Long expenseId, String expenseNo, Long expenseLineId,
			String expenseCategory, String description, String taxExcludedAmount, String allocatedAmount,
			String availableAmount, boolean amountVisible, boolean sourceVisible, String restrictedReason,
			String sourceSummary) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

	private record AdjustmentState(Long id, String adjustmentNo, String status, String requestFingerprint,
			Long version) {
	}

	private record AllocationLine(Long publicExpenseLineId, BigDecimal amount) {
	}

}
