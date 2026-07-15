package com.qherp.api.system.procurement;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.platform.PlatformApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Lazy;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class ProcurementRequisitionService {

	private static final String TARGET = "PROCUREMENT_REQUISITION";

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger REQUISITION_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final PlatformApprovalService approvalService;

	private final ProcurementActionIdempotencyService actionIdempotencyService;

	public ProcurementRequisitionService(JdbcTemplate jdbcTemplate, AuditService auditService,
			@Lazy PlatformApprovalService approvalService,
			ProcurementActionIdempotencyService actionIdempotencyService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.approvalService = approvalService;
		this.actionIdempotencyService = actionIdempotencyService;
	}

	@Transactional
	public PurchaseRequisitionDetailResponse create(PurchaseRequisitionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedRequisition validated = validateRequest(request);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			String requisitionNo = nextNo();
			Long id = this.jdbcTemplate.queryForObject("""
					insert into proc_purchase_requisition (
						requisition_no, purchase_mode, project_id, required_date, status, purpose, client_request_id,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, requisitionNo, validated.purchaseMode().name(), validated.projectId(),
					validated.requiredDate(), validated.purpose(), blankToNull(request.clientRequestId()),
					operator.username(), now, operator.username(), now);
			insertLines(id, validated.lines(), now);
			this.auditService.record(operator, "PROCUREMENT_REQUISITION_CREATE", TARGET, id, requisitionNo,
					servletRequest);
			return detail(id);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<PurchaseRequisitionSummaryResponse> list(String keyword, String procurementMode,
			Long projectId, String status, String approvalStatus, LocalDate requiredDateFrom, LocalDate requiredDateTo,
			int page, int pageSize) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(lower(r.requisition_no) like ? or lower(r.purpose) like ?)");
			String like = "%" + keyword.trim().toLowerCase() + "%";
			args.add(like);
			args.add(like);
		}
		PurchaseMode mode = parseNullableMode(procurementMode);
		if (mode != null) {
			conditions.add("r.purchase_mode = ?");
			args.add(mode.name());
		}
		if (projectId != null) {
			conditions.add("r.project_id = ?");
			args.add(projectId);
		}
		if (hasText(status)) {
			conditions.add("r.status = ?");
			args.add(status.trim());
		}
		if (hasText(approvalStatus)) {
			conditions.add("r.status = ?");
			args.add(approvalStatus.trim());
		}
		if (requiredDateFrom != null) {
			conditions.add("r.required_date >= ?");
			args.add(requiredDateFrom);
		}
		if (requiredDateTo != null) {
			conditions.add("r.required_date <= ?");
			args.add(requiredDateTo);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_requisition r
				%s
				""".formatted(where), Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<PurchaseRequisitionSummaryResponse> items = this.jdbcTemplate.query("""
				select r.id, r.requisition_no, r.purchase_mode, r.project_id, p.project_no as project_code,
				       p.name as project_name, r.required_date, r.status, r.purpose,
				       r.approval_instance_id, r.closed_reason, r.version, r.created_by, r.created_at, r.updated_at,
				       count(l.id) as line_count,
				       coalesce(sum(l.quantity), 0) as total_quantity,
				       coalesce(sum(l.ordered_quantity), 0) as ordered_quantity
				from proc_purchase_requisition r
				left join proc_purchase_requisition_line l on l.requisition_id = r.id
				left join sal_project p on p.id = r.project_id
				%s
				group by r.id, p.project_no, p.name
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""".formatted(where), this::mapSummary, pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public PurchaseRequisitionDetailResponse detail(Long id) {
		RequisitionRow row = requisition(id).orElseThrow(this::notFound);
		RequisitionTotals totals = requisitionTotals(id);
		return new PurchaseRequisitionDetailResponse(row.id(), row.requisitionNo(), row.purchaseMode(),
				row.purchaseMode().name(), row.purchaseMode().name(), row.projectId(), row.projectCode(),
				row.projectName(), row.requiredDate(), row.status(), row.purpose(), row.approvalInstanceId(),
				approvalStatus(row.status(), row.approvalInstanceId()), row.closedReason(), row.version(),
				totals.lineCount(), decimalString(totals.totalQuantity()), decimalString(totals.orderedQuantity()),
				decimalString(totals.totalQuantity().subtract(totals.orderedQuantity())), allowedActions(row.status()),
				row.createdBy(), row.createdAt(), row.updatedAt(), lines(id));
	}

	@Transactional
	public PurchaseRequisitionDetailResponse update(Long id, PurchaseRequisitionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		RequisitionRow row = lockRequisition(id).orElseThrow(this::notFound);
		requireVersion(row.version(), request == null ? null : request.version());
		if (row.status() != PurchaseRequisitionStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_STATUS_INVALID);
		}
		ValidatedRequisition validated = validateRequest(request);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update proc_purchase_requisition
				set purchase_mode = ?, project_id = ?, required_date = ?, purpose = ?, updated_by = ?,
				    updated_at = ?, version = version + 1
				where id = ?
				""", validated.purchaseMode().name(), validated.projectId(), validated.requiredDate(),
				validated.purpose(), operator.username(), now, id);
		this.jdbcTemplate.update("delete from proc_purchase_requisition_line where requisition_id = ?", id);
		insertLines(id, validated.lines(), now);
		this.auditService.record(operator, "PROCUREMENT_REQUISITION_UPDATE", TARGET, id, row.requisitionNo(),
				servletRequest);
		return detail(id);
	}

	@Transactional
	public PlatformApprovalService.ApprovalInstanceRecord submitApproval(Long id,
			PlatformApprovalService.ApprovalSubmitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PlatformApprovalService.ApprovalInstanceRecord idempotent = this.approvalService
			.idempotentSubmitResult("PROCUREMENT_REQUISITION_APPROVAL", id, request, operator);
		if (idempotent != null) {
			return idempotent;
		}
		RequisitionRow row = lockRequisition(id).orElseThrow(this::notFound);
		requireVersion(row.version(), request.version());
		if (row.status() != PurchaseRequisitionStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_STATUS_INVALID);
		}
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService
			.submitProcurementRequisition(id, request, operator, servletRequest);
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update proc_purchase_requisition
				set status = 'SUBMITTED', approval_instance_id = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				returning version
				""", Long.class, approval.id(), operator.username(), OffsetDateTime.now(), id);
		this.approvalService.updateBusinessObjectVersion(approval.id(), newVersion);
		this.auditService.record(operator, "PROCUREMENT_REQUISITION_SUBMIT", TARGET, id, row.requisitionNo(),
				servletRequest);
		return this.approvalService.get(approval.id(), operator);
	}

	@Transactional
	public PurchaseRequisitionDetailResponse cancel(Long id, ProcurementSourcingService.VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return idempotentAction("CANCEL", id, request, operator, () -> {
			RequisitionRow row = lockRequisition(id).orElseThrow(this::notFound);
			requireVersion(row.version(), request.version());
			if (row.status() != PurchaseRequisitionStatus.DRAFT && row.status() != PurchaseRequisitionStatus.SUBMITTED) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_STATUS_INVALID);
			}
			this.jdbcTemplate.update("""
					update proc_purchase_requisition
					set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					""", operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
			this.auditService.record(operator, "PROCUREMENT_REQUISITION_CANCEL", TARGET, id, row.requisitionNo(),
					servletRequest);
			return detail(id);
		});
	}

	@Transactional
	public PurchaseRequisitionDetailResponse close(Long id, ProcurementSourcingService.VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return idempotentAction("CLOSE", id, request, operator, () -> {
			RequisitionRow row = lockRequisition(id).orElseThrow(this::notFound);
			requireVersion(row.version(), request.version());
			if (row.status() != PurchaseRequisitionStatus.APPROVED
					&& row.status() != PurchaseRequisitionStatus.PARTIALLY_ORDERED) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_STATUS_INVALID);
			}
			String reason = validateText(request.reason(), 200);
			this.jdbcTemplate.update("""
					update proc_purchase_requisition
					set status = 'CLOSED', closed_reason = ?, closed_by = ?, closed_at = ?, updated_by = ?,
					    updated_at = ?, version = version + 1
					where id = ?
					""", reason, operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
			this.auditService.record(operator, "PROCUREMENT_REQUISITION_CLOSE", TARGET, id, row.requisitionNo(),
					servletRequest);
			return detail(id);
		});
	}

	@Transactional
	public void approveFromApproval(Long id, Long expectedVersion, CurrentUser operator) {
		RequisitionRow row = lockRequisition(id).orElseThrow(this::notFound);
		requireVersion(row.version(), expectedVersion);
		if (row.status() != PurchaseRequisitionStatus.SUBMITTED) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_STATUS_INVALID);
		}
		this.jdbcTemplate.update("""
				update proc_purchase_requisition
				set status = 'APPROVED', approved_by = ?, approved_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
	}

	@Transactional
	public void reopenAfterApprovalTerminal(Long id, CurrentUser operator) {
		this.jdbcTemplate.update("""
				update proc_purchase_requisition
				set status = 'DRAFT', approval_instance_id = null, updated_by = ?, updated_at = ?
				where id = ?
				and status = 'SUBMITTED'
				""", operator.username(), OffsetDateTime.now(), id);
	}

	@Transactional
	public void addOrderedQuantity(Long requisitionLineId, BigDecimal quantity, CurrentUser operator) {
		RequisitionLineRow line = lockLine(requisitionLineId).orElseThrow(this::notFound);
		RequisitionRow header = lockRequisition(line.requisitionId()).orElseThrow(this::notFound);
		if (header.status() != PurchaseRequisitionStatus.APPROVED
				&& header.status() != PurchaseRequisitionStatus.PARTIALLY_ORDERED) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_STATUS_INVALID);
		}
		if (line.orderedQuantity().add(quantity).compareTo(line.quantity()) > 0) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_QUANTITY_INVALID);
		}
		this.jdbcTemplate.update("""
				update proc_purchase_requisition_line
				set ordered_quantity = ordered_quantity + ?, updated_at = ?, version = version + 1
				where id = ?
				""", quantity, OffsetDateTime.now(), requisitionLineId);
		updateHeaderOrderedStatus(header.id(), operator.username());
	}

	@Transactional(readOnly = true)
	public RequisitionLineSource sourceLine(Long requisitionLineId) {
		return this.jdbcTemplate.query("""
				select rl.id, rl.requisition_id, r.purchase_mode, r.project_id, r.status, rl.material_id, rl.unit_id,
				       rl.quantity, rl.ordered_quantity
				from proc_purchase_requisition_line rl
				join proc_purchase_requisition r on r.id = rl.requisition_id
				where rl.id = ?
				""", (rs, rowNum) -> new RequisitionLineSource(rs.getLong("id"), rs.getLong("requisition_id"),
				PurchaseMode.valueOf(rs.getString("purchase_mode")), nullableLong(rs, "project_id"),
				PurchaseRequisitionStatus.valueOf(rs.getString("status")), rs.getLong("material_id"),
				rs.getLong("unit_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("ordered_quantity")),
				requisitionLineId).stream().findFirst().orElseThrow(this::notFound);
	}

	private ValidatedRequisition validateRequest(PurchaseRequisitionRequest request) {
		if (request == null || request.requiredDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		PurchaseMode mode = parseMode(request.purchaseMode());
		Long projectId = request.projectId();
		if (mode == PurchaseMode.PROJECT) {
			if (projectId == null) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_PROJECT_REQUIRED);
			}
			validateActiveProject(projectId);
		}
		else {
			projectId = null;
		}
		String purpose = validateText(request.purpose(), 500);
		if (request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		List<ValidatedLine> lines = new ArrayList<>();
		for (PurchaseRequisitionLineRequest line : request.lines()) {
			if (line == null || line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			MaterialRef material = validatePurchasableMaterial(line.materialId());
			Long unitId = line.unitId() == null ? material.unitId() : line.unitId();
			validateEnabledUnit(unitId, material.unitId());
			lines.add(new ValidatedLine(line.lineNo(), material.id(), unitId, validateQuantity(line.quantity()),
					line.requiredDate() == null ? request.requiredDate() : line.requiredDate(),
					validateText(line.purpose(), 500), line.suggestedSupplierId()));
		}
		return new ValidatedRequisition(mode, projectId, request.requiredDate(), purpose, lines);
	}

	private void insertLines(Long requisitionId, List<ValidatedLine> lines, OffsetDateTime now) {
		for (ValidatedLine line : lines) {
			this.jdbcTemplate.update("""
					insert into proc_purchase_requisition_line (
						requisition_id, line_no, material_id, unit_id, quantity, ordered_quantity, required_date,
						purpose, suggested_supplier_id, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
					""", requisitionId, line.lineNo(), line.materialId(), line.unitId(), line.quantity(),
					line.requiredDate(), line.purpose(), line.suggestedSupplierId(), now, now);
		}
	}

	private List<PurchaseRequisitionLineResponse> lines(Long requisitionId) {
		return this.jdbcTemplate.query("""
				select rl.id, rl.line_no, rl.material_id, m.code as material_code, m.name as material_name,
				       m.specification as material_spec, rl.unit_id, u.name as unit_name, rl.quantity,
				       rl.ordered_quantity, rl.quantity - rl.ordered_quantity as remaining_quantity,
				       rl.required_date, rl.purpose, rl.suggested_supplier_id, s.name as suggested_supplier_name,
				       r.purchase_mode, r.project_id, p.project_no as project_code, p.name as project_name
				from proc_purchase_requisition_line rl
				join proc_purchase_requisition r on r.id = rl.requisition_id
				join mst_material m on m.id = rl.material_id
				join mst_unit u on u.id = rl.unit_id
				left join mst_supplier s on s.id = rl.suggested_supplier_id
				left join sal_project p on p.id = r.project_id
				where rl.requisition_id = ?
				order by rl.line_no asc, rl.id asc
				""", (rs, rowNum) -> new PurchaseRequisitionLineResponse(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				rs.getString("material_spec"), rs.getLong("unit_id"), rs.getString("unit_name"),
				decimalString(rs.getBigDecimal("quantity")), decimalString(rs.getBigDecimal("quantity")),
				decimalString(rs.getBigDecimal("ordered_quantity")), decimalString(rs.getBigDecimal("remaining_quantity")),
				rs.getObject("required_date", LocalDate.class),
				rs.getString("purpose"), nullableLong(rs, "suggested_supplier_id"),
				rs.getString("suggested_supplier_name"), PurchaseMode.valueOf(rs.getString("purchase_mode")),
				rs.getString("purchase_mode"), nullableLong(rs, "project_id"), rs.getString("project_code"),
				rs.getString("project_name")), requisitionId);
	}

	private PurchaseRequisitionSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
		BigDecimal totalQuantity = rs.getBigDecimal("total_quantity");
		BigDecimal orderedQuantity = rs.getBigDecimal("ordered_quantity");
		PurchaseRequisitionStatus status = PurchaseRequisitionStatus.valueOf(rs.getString("status"));
		PurchaseMode purchaseMode = PurchaseMode.valueOf(rs.getString("purchase_mode"));
		return new PurchaseRequisitionSummaryResponse(rs.getLong("id"), rs.getString("requisition_no"),
				rs.getString("purpose"), purchaseMode, purchaseMode.name(), purchaseMode.name(),
				nullableLong(rs, "project_id"), rs.getString("project_code"), rs.getString("project_name"),
				rs.getObject("required_date", LocalDate.class), status, nullableLong(rs, "approval_instance_id"),
				approvalStatus(status, nullableLong(rs, "approval_instance_id")), rs.getString("closed_reason"),
				rs.getInt("line_count"), decimalString(totalQuantity), decimalString(orderedQuantity),
				decimalString(totalQuantity.subtract(orderedQuantity)), allowedActions(status), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getLong("version"));
	}

	private RequisitionTotals requisitionTotals(Long requisitionId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*) as line_count, coalesce(sum(quantity), 0) as total_quantity,
				       coalesce(sum(ordered_quantity), 0) as ordered_quantity
				from proc_purchase_requisition_line
				where requisition_id = ?
				""", (rs, rowNum) -> new RequisitionTotals(rs.getInt("line_count"),
				rs.getBigDecimal("total_quantity"), rs.getBigDecimal("ordered_quantity")), requisitionId);
	}

	private Optional<RequisitionRow> requisition(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.requisition_no, r.purchase_mode, r.project_id, p.project_no as project_code,
				       p.name as project_name, r.required_date, r.status, r.purpose,
				       r.approval_instance_id, r.closed_reason, r.version, r.created_by, r.created_at, r.updated_at
				from proc_purchase_requisition r
				left join sal_project p on p.id = r.project_id
				where r.id = ?
				""", this::mapRequisition, id).stream().findFirst();
	}

	private Optional<RequisitionRow> lockRequisition(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.requisition_no, r.purchase_mode, r.project_id, p.project_no as project_code,
				       p.name as project_name, r.required_date, r.status, r.purpose,
				       r.approval_instance_id, r.closed_reason, r.version, r.created_by, r.created_at, r.updated_at
				from proc_purchase_requisition r
				left join sal_project p on p.id = r.project_id
				where r.id = ?
				for update of r
				""", this::mapRequisition, id).stream().findFirst();
	}

	private Optional<RequisitionLineRow> lockLine(Long id) {
		return this.jdbcTemplate.query("""
				select id, requisition_id, quantity, ordered_quantity
				from proc_purchase_requisition_line
				where id = ?
				for update
				""", (rs, rowNum) -> new RequisitionLineRow(rs.getLong("id"), rs.getLong("requisition_id"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("ordered_quantity")), id).stream().findFirst();
	}

	private RequisitionRow mapRequisition(ResultSet rs, int rowNum) throws SQLException {
		return new RequisitionRow(rs.getLong("id"), rs.getString("requisition_no"),
				PurchaseMode.valueOf(rs.getString("purchase_mode")), nullableLong(rs, "project_id"),
				rs.getString("project_code"), rs.getString("project_name"), rs.getObject("required_date", LocalDate.class),
				PurchaseRequisitionStatus.valueOf(rs.getString("status")), rs.getString("purpose"),
				nullableLong(rs, "approval_instance_id"), rs.getString("closed_reason"), rs.getLong("version"),
				rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class));
	}

	private void updateHeaderOrderedStatus(Long requisitionId, String operatorName) {
		this.jdbcTemplate.update("""
				update proc_purchase_requisition r
				set status = case
						when exists (
							select 1 from proc_purchase_requisition_line rl
							where rl.requisition_id = r.id and rl.ordered_quantity < rl.quantity
						) then 'PARTIALLY_ORDERED'
						else 'ORDERED'
					end,
				    updated_by = ?, updated_at = ?
				where r.id = ?
				""", operatorName, OffsetDateTime.now(), requisitionId);
	}

	private void validateActiveProject(Long projectId) {
		List<String> statuses = this.jdbcTemplate.query("""
				select status from sal_project where id = ?
				""", (rs, rowNum) -> rs.getString("status"), projectId);
		if (statuses.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_PROJECT_INVALID);
		}
		if (!"ACTIVE".equals(statuses.getFirst())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_PROJECT_INVALID);
		}
	}

	private MaterialRef validatePurchasableMaterial(Long materialId) {
		List<MaterialRef> materials = this.jdbcTemplate.query("""
				select id, unit_id, source_type, status
				from mst_material
				where id = ?
				""", (rs, rowNum) -> new MaterialRef(rs.getLong("id"), rs.getLong("unit_id"),
				rs.getString("source_type"), rs.getString("status")), materialId);
		if (materials.isEmpty() || !"ENABLED".equals(materials.getFirst().status())
				|| !"PURCHASED".equals(materials.getFirst().sourceType())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_MATERIAL_INVALID);
		}
		return materials.getFirst();
	}

	private void validateEnabledUnit(Long unitId, Long materialUnitId) {
		if (unitId == null || !unitId.equals(materialUnitId)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_UNIT_INVALID);
		}
		Integer count = this.jdbcTemplate.queryForObject("""
				select count(*) from mst_unit where id = ? and status = 'ENABLED'
				""", Integer.class, unitId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_UNIT_INVALID);
		}
	}

	private PurchaseMode parseMode(String value) {
		try {
			return value == null ? PurchaseMode.PUBLIC : PurchaseMode.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private PurchaseMode parseNullableMode(String value) {
		if (!hasText(value)) {
			return null;
		}
		return parseMode(value.trim());
	}

	private BigDecimal validateQuantity(BigDecimal value) {
		if (value == null || value.compareTo(BigDecimal.ZERO) <= 0 || integerDigits(value) > 12) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_QUANTITY_INVALID);
		}
		return value;
	}

	private String validateText(String value, int maxLength) {
		String trimmed = blankToNull(value);
		if (trimmed == null || trimmed.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return trimmed;
	}

	private List<String> allowedActions(PurchaseRequisitionStatus status) {
		return switch (status) {
			case DRAFT -> List.of("UPDATE", "SUBMIT_APPROVAL", "SUBMIT", "CANCEL");
			case SUBMITTED -> List.of("CANCEL");
			case APPROVED, PARTIALLY_ORDERED -> List.of("CREATE_INQUIRY", "CREATE_ORDER", "CLOSE");
			case ORDERED, CLOSED, CANCELLED -> List.of();
		};
	}

	private String approvalStatus(PurchaseRequisitionStatus status, Long approvalInstanceId) {
		return approvalInstanceId == null ? null : status.name();
	}

	private PurchaseRequisitionDetailResponse idempotentAction(String action, Long id,
			ProcurementSourcingService.VersionedActionRequest request, CurrentUser operator,
			Supplier<PurchaseRequisitionDetailResponse> callback) {
		ProcurementSourcingService.VersionedActionRequest actionRequest = requireActionRequest(request);
		String fingerprint = this.actionIdempotencyService.fingerprint(action, TARGET, id, actionRequest.version(),
				actionRequest.reason());
		Optional<ProcurementActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService
			.existing(action, TARGET, id, actionRequest.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return detail(existing.get().resultResourceId());
		}
		PurchaseRequisitionDetailResponse result = callback.get();
		this.actionIdempotencyService.record(action, TARGET, id, actionRequest.version(),
				actionRequest.idempotencyKey(), fingerprint, TARGET, result.id(), result.version(), operator);
		return result;
	}

	private ProcurementSourcingService.VersionedActionRequest requireActionRequest(
			ProcurementSourcingService.VersionedActionRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return request;
	}

	private void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
	}

	private BusinessException notFound() {
		return new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_NOT_FOUND);
	}

	private String nextNo() {
		return "PRQ" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
				+ NUMBER_FORMATTER.format(java.time.LocalDateTime.now()) + String.format("%03d",
						REQUISITION_NO_SEQUENCE.updateAndGet((value) -> value >= 999 ? 1 : value + 1));
	}

	private long integerDigits(BigDecimal value) {
		return value.precision() - value.scale();
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String decimalString(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record PurchaseRequisitionRequest(String purchaseMode, String procurementMode, String ownershipType,
			Long projectId, String title, @NotNull LocalDate requiredDate, String purpose, String remark,
			Long suggestedSupplierId, Long version, String clientRequestId, String idempotencyKey,
			@Valid List<PurchaseRequisitionLineRequest> lines) {

		@Override
		public String purchaseMode() {
			if (purchaseMode != null && !purchaseMode.isBlank()) {
				return purchaseMode;
			}
			if (procurementMode != null && !procurementMode.isBlank()) {
				return procurementMode;
			}
			return ownershipType;
		}

		@Override
		public String purpose() {
			if (purpose != null && !purpose.isBlank()) {
				return purpose;
			}
			if (remark != null && !remark.isBlank()) {
				return remark;
			}
			return title;
		}
	}

	public record PurchaseRequisitionLineRequest(@NotNull Integer lineNo, @NotNull Long materialId, Long unitId,
			BigDecimal quantity, BigDecimal requestedQuantity, String procurementMode, String ownershipType,
			Long projectId, LocalDate requiredDate, String purpose, String remark, Long suggestedSupplierId) {

		@Override
		public BigDecimal quantity() {
			return quantity == null ? requestedQuantity : quantity;
		}

		@Override
		public String purpose() {
			return purpose != null && !purpose.isBlank() ? purpose : remark;
		}
	}

	public record PurchaseRequisitionSummaryResponse(Long id, String requisitionNo, String title,
			PurchaseMode purchaseMode, String procurementMode, String ownershipType, Long projectId,
			String projectCode, String projectName, LocalDate requiredDate, PurchaseRequisitionStatus status,
			Long approvalInstanceId, String approvalStatus, String closeReason, int lineCount, String totalQuantity,
			String orderedQuantity, String remainingQuantity, List<String> allowedActions, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

	public record PurchaseRequisitionDetailResponse(Long id, String requisitionNo, PurchaseMode purchaseMode,
			String procurementMode, String ownershipType, Long projectId, String projectCode, String projectName,
			LocalDate requiredDate, PurchaseRequisitionStatus status, String purpose, Long approvalInstanceId,
			String approvalStatus, String closeReason, Long version, int lineCount, String totalQuantity,
			String orderedQuantity, String remainingQuantity, List<String> allowedActions, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, List<PurchaseRequisitionLineResponse> lines) {

		public String title() {
			return purpose;
		}
	}

	public record PurchaseRequisitionLineResponse(Long id, Integer lineNo, Long materialId, String materialCode,
			String materialName, String materialSpec, Long unitId, String unitName, String quantity,
			String requestedQuantity, String orderedQuantity, String remainingQuantity, LocalDate requiredDate, String purpose,
			Long suggestedSupplierId, String suggestedSupplierName, PurchaseMode procurementMode, String ownershipType,
			Long projectId, String projectCode, String projectName) {
	}

	public record RequisitionLineSource(Long id, Long requisitionId, PurchaseMode purchaseMode, Long projectId,
			PurchaseRequisitionStatus status, Long materialId, Long unitId, BigDecimal quantity,
			BigDecimal orderedQuantity) {
	}

	private record ValidatedRequisition(PurchaseMode purchaseMode, Long projectId, LocalDate requiredDate,
			String purpose, List<ValidatedLine> lines) {
	}

	private record ValidatedLine(Integer lineNo, Long materialId, Long unitId, BigDecimal quantity,
			LocalDate requiredDate, String purpose, Long suggestedSupplierId) {
	}

	private record RequisitionRow(Long id, String requisitionNo, PurchaseMode purchaseMode, Long projectId,
			String projectCode, String projectName, LocalDate requiredDate, PurchaseRequisitionStatus status,
			String purpose, Long approvalInstanceId, String closedReason, Long version, String createdBy,
			OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	private record RequisitionTotals(int lineCount, BigDecimal totalQuantity, BigDecimal orderedQuantity) {
	}

	private record RequisitionLineRow(Long id, Long requisitionId, BigDecimal quantity, BigDecimal orderedQuantity) {
	}

	private record MaterialRef(Long id, Long unitId, String sourceType, String status) {
	}

}
