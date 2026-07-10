package com.qherp.api.system.cost;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
import com.qherp.api.system.production.ProductionWorkOrderStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CostAdminService {

	private static final String TARGET_TYPE = "MFG_COST_RECORD";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final int MAX_NO_ATTEMPTS = 3;

	private static final AtomicInteger COST_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final BusinessPeriodGuard businessPeriodGuard;

	public CostAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			BusinessPeriodGuard businessPeriodGuard) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.businessPeriodGuard = businessPeriodGuard;
	}

	@Transactional(readOnly = true)
	public PageResponse<CostRecordSummaryResponse> records(String keyword, Long workOrderId, Long productMaterialId,
			String costType, String sourceType, String sourceDocumentType, String sourceDocumentNo, LocalDate dateFrom,
			LocalDate dateTo, int page, int pageSize) {
		QueryParts queryParts = costRecordQueryParts(keyword, workOrderId, productMaterialId, costType, sourceType,
				sourceDocumentType, sourceDocumentNo, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_cost_record cr
				join mfg_work_order wo on wo.id = cr.work_order_id
				join mst_material pm on pm.id = cr.product_material_id
				left join mst_material m on m.id = cr.material_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<CostRecordSummaryResponse> items = this.jdbcTemplate.query("""
				select cr.id, cr.record_no, cr.work_order_id, wo.work_order_no, cr.product_material_id,
				       pm.code as product_material_code, pm.name as product_material_name, cr.cost_type,
				       cr.source_type, cr.source_document_type, cr.source_document_no, cr.source_document_id,
				       cr.source_line_id, cr.work_order_material_id, cr.material_id, m.code as material_code,
				       m.name as material_name, cr.unit_id, u.name as unit_name, cr.quantity, cr.unit_price,
				       cr.amount, cr.basis_type, cr.business_date, cr.status, cr.remark, cr.recorded_by,
				       cr.recorded_at, cr.created_by, cr.created_at, cr.updated_at
				from mfg_cost_record cr
				join mfg_work_order wo on wo.id = cr.work_order_id
				join mst_material pm on pm.id = cr.product_material_id
				left join mst_material m on m.id = cr.material_id
				left join mst_unit u on u.id = cr.unit_id
				%s
				order by cr.business_date desc, cr.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapCostRecordSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public CostRecordDetailResponse record(Long id) {
		CostRecordRow row = costRecordRow(id).orElseThrow(this::recordNotFound);
		return toDetail(row);
	}

	@Transactional
	public CostRecordDetailResponse createRecord(CostRecordPayload request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedManualCost validated = validateManualCost(request, null);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.CREATE, "COST_RECORD",
				null);
		OffsetDateTime now = OffsetDateTime.now();
		Long id = insertManualCostWithRetry(validated, operator.username(), now);
		this.auditService.record(operator, "MFG_COST_RECORD_CREATE", TARGET_TYPE, id, recordNo(id), servletRequest);
		return record(id);
	}

	@Transactional
	public CostRecordDetailResponse updateRecord(Long id, CostRecordPayload request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		CostRecordRow current = lockCostRecord(id).orElseThrow(this::recordNotFound);
		if (current.sourceType() != CostSourceType.MANUAL_ENTRY) {
			throw new BusinessException(ApiErrorCode.COST_GENERATED_RECORD_IMMUTABLE);
		}
		ValidatedManualCost validated = validateManualCost(request, current.workOrderId());
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.UPDATE, "COST_RECORD",
				id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_cost_record
				set cost_type = ?, source_document_no = ?, unit_id = ?, quantity = ?, unit_price = ?, amount = ?,
				    basis_type = ?, business_date = ?, remark = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", validated.costType().name(), validated.sourceDocumentNo(), validated.unitId(),
				validated.quantity(), validated.unitPrice(), validated.amount(), validated.basisType().name(),
				validated.businessDate(), validated.remark(), operator.username(), now, id);
		this.auditService.record(operator, "MFG_COST_RECORD_UPDATE", TARGET_TYPE, id, current.recordNo(),
				servletRequest);
		return record(id);
	}

	@Transactional(readOnly = true)
	public WorkOrderCostSummaryResponse workOrderSummary(Long workOrderId) {
		WorkOrderRef workOrder = workOrderRef(workOrderId).orElseThrow(this::workOrderNotFound);
		List<CostRecordSummaryResponse> records = records(null, workOrderId, null, null, null, null, null, null, null,
				1, 100).items();
		List<CostAmountSummaryResponse> amountSummaries = this.jdbcTemplate.query("""
				select cost_type, coalesce(sum(amount), 0) as amount
				from mfg_cost_record
				where work_order_id = ?
				and status = ?
				and amount is not null
				group by cost_type
				order by cost_type asc
				""", (rs, rowNum) -> new CostAmountSummaryResponse(rs.getString("cost_type"),
				rs.getBigDecimal("amount")), workOrderId, CostRecordStatus.ACTIVE.name());
		List<CostQuantitySummaryResponse> quantitySummaries = this.jdbcTemplate.query("""
				select cost_type, coalesce(sum(quantity), 0) as quantity
				from mfg_cost_record
				where work_order_id = ?
				and status = ?
				and quantity is not null
				group by cost_type
				order by cost_type asc
				""", (rs, rowNum) -> new CostQuantitySummaryResponse(rs.getString("cost_type"),
				rs.getBigDecimal("quantity")), workOrderId, CostRecordStatus.ACTIVE.name());
		return new WorkOrderCostSummaryResponse(workOrder.id(), workOrder.workOrderNo(), workOrder.productMaterialId(),
				workOrder.productMaterialCode(), workOrder.productMaterialName(), false, records, amountSummaries,
				quantitySummaries, outputTraces(workOrderId));
	}

	private CostRecordDetailResponse toDetail(CostRecordRow row) {
		String sourceStatus = sourceStatus(row.sourceDocumentType(), row.sourceDocumentId());
		return new CostRecordDetailResponse(row.id(), row.recordNo(), row.workOrderId(), row.workOrderNo(),
				row.productMaterialId(), row.productMaterialCode(), row.productMaterialName(), row.costType().name(),
				row.sourceType().name(), row.sourceDocumentType().name(), row.sourceDocumentNo(),
				row.sourceDocumentId(), row.sourceLineId(), row.basisType().name(), row.materialId(),
				row.materialCode(), row.materialName(), row.unitId(), row.unitName(), row.quantity(), row.unitPrice(),
				row.amount(), row.businessDate(), row.status().name(), row.remark(), row.recordedBy(),
				row.recordedAt(), row.createdBy(), row.createdAt(), row.updatedAt(), row.workOrderStatus(), sourceStatus,
				sourceSummary(row, sourceStatus), outputTraces(row.workOrderId()), auditSummaries(row.id()));
	}

	private SourceSummaryResponse sourceSummary(CostRecordRow row, String sourceStatus) {
		return new SourceSummaryResponse(sourceStatus, row.sourceDocumentNo(), row.quantity(), row.materialId(),
				row.materialCode(), row.materialName(), row.unitId(), row.unitName());
	}

	private List<OutputTraceResponse> outputTraces(Long workOrderId) {
		return this.jdbcTemplate.query("""
				select r.id, r.receipt_no, r.work_order_id, r.business_date, r.receipt_warehouse_id,
				       w.name as receipt_warehouse_name, r.quantity, r.before_quantity, r.after_quantity,
				       r.posted_by, r.posted_at
				from mfg_completion_receipt r
				join mst_warehouse w on w.id = r.receipt_warehouse_id
				where r.work_order_id = ?
				and r.status = 'POSTED'
				order by r.posted_at desc, r.id desc
				""", (rs, rowNum) -> new OutputTraceResponse(rs.getLong("id"), rs.getString("receipt_no"),
				rs.getLong("work_order_id"), rs.getObject("business_date", LocalDate.class),
				rs.getLong("receipt_warehouse_id"), rs.getString("receipt_warehouse_name"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
				rs.getString("posted_by"), rs.getObject("posted_at", OffsetDateTime.class)), workOrderId);
	}

	private List<AuditSummaryResponse> auditSummaries(Long recordId) {
		return this.jdbcTemplate.query("""
				select id, operator_username, action, created_at
				from sys_audit_log
				where target_type = ?
				and target_id = ?
				order by created_at desc, id desc
				""", (rs, rowNum) -> new AuditSummaryResponse(rs.getLong("id"),
				rs.getString("operator_username"), rs.getString("action"),
				rs.getObject("created_at", OffsetDateTime.class)), TARGET_TYPE, recordId.toString());
	}

	private ValidatedManualCost validateManualCost(CostRecordPayload request, Long expectedWorkOrderId) {
		if (request == null || request.workOrderId() == null || request.businessDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (expectedWorkOrderId != null && !expectedWorkOrderId.equals(request.workOrderId())) {
			throw new BusinessException(ApiErrorCode.COST_WORK_ORDER_STATUS_INVALID);
		}
		WorkOrderRef workOrder = workOrderRef(request.workOrderId()).orElseThrow(this::workOrderNotFound);
		if (workOrder.status() == ProductionWorkOrderStatus.CANCELLED) {
			throw new BusinessException(ApiErrorCode.COST_WORK_ORDER_STATUS_INVALID);
		}
		CostType costType = parseCostType(request.costType());
		CostBasisType basisType = parseManualBasisType(request.basisType());
		String remark = validateRequiredText(request.remark(), 500);
		String sourceDocumentNo = validateOptionalText(request.sourceDocumentNo(), 64);
		BigDecimal quantity = validateQuantity(request.quantity());
		BigDecimal unitPrice = validateUnitPrice(request.unitPrice());
		BigDecimal amount = validateAmount(request.amount());
		if (basisType == CostBasisType.MANUAL_AMOUNT) {
			if (amount == null) {
				throw new BusinessException(ApiErrorCode.COST_AMOUNT_INVALID);
			}
		}
		else if (basisType == CostBasisType.MANUAL_UNIT_PRICE_QUANTITY) {
			if (quantity == null || unitPrice == null) {
				throw new BusinessException(quantity == null ? ApiErrorCode.COST_QUANTITY_INVALID
						: ApiErrorCode.COST_AMOUNT_INVALID);
			}
			amount = validateAmount(unitPrice.multiply(quantity).setScale(6, RoundingMode.HALF_UP));
		}
		if (quantity == null && amount == null) {
			throw new BusinessException(ApiErrorCode.COST_BASIS_INVALID);
		}
		Long unitId = request.unitId();
		if (unitId != null) {
			validateEnabledUnit(unitId);
		}
		return new ValidatedManualCost(workOrder, costType, basisType, request.businessDate(), quantity, unitId,
				unitPrice, amount, sourceDocumentNo, remark);
	}

	private Long insertManualCostWithRetry(ValidatedManualCost cost, String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String recordNo = nextNo();
			try {
				return this.jdbcTemplate.queryForObject("""
						insert into mfg_cost_record (
							record_no, work_order_id, product_material_id, cost_type, source_type,
							source_document_type, source_document_no, unit_id, quantity, unit_price, amount,
							basis_type, business_date, status, remark, recorded_by, recorded_at,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, recordNo, cost.workOrder().id(), cost.workOrder().productMaterialId(),
						cost.costType().name(), CostSourceType.MANUAL_ENTRY.name(),
						CostSourceDocumentType.MANUAL_COST_RECORD.name(), cost.sourceDocumentNo(), cost.unitId(),
						cost.quantity(), cost.unitPrice(), cost.amount(), cost.basisType().name(), cost.businessDate(),
						CostRecordStatus.ACTIVE.name(), cost.remark(), operatorName, now, operatorName, now,
						operatorName, now);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_mfg_cost_record_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw new BusinessException(ApiErrorCode.CONFLICT);
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private QueryParts costRecordQueryParts(String keyword, Long workOrderId, Long productMaterialId, String costType,
			String sourceType, String sourceDocumentType, String sourceDocumentNo, LocalDate dateFrom,
			LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(cr.record_no ilike ? or wo.work_order_no ilike ? or pm.code ilike ? or pm.name ilike ?
					or cr.source_document_no ilike ?)
					""");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (workOrderId != null) {
			conditions.add("cr.work_order_id = ?");
			args.add(workOrderId);
		}
		if (productMaterialId != null) {
			conditions.add("cr.product_material_id = ?");
			args.add(productMaterialId);
		}
		if (hasText(costType)) {
			conditions.add("cr.cost_type = ?");
			args.add(parseCostType(costType).name());
		}
		if (hasText(sourceType)) {
			conditions.add("cr.source_type = ?");
			args.add(parseSourceType(sourceType).name());
		}
		if (hasText(sourceDocumentType)) {
			conditions.add("cr.source_document_type = ?");
			args.add(parseSourceDocumentType(sourceDocumentType).name());
		}
		if (hasText(sourceDocumentNo)) {
			conditions.add("cr.source_document_no ilike ?");
			args.add("%" + sourceDocumentNo + "%");
		}
		if (dateFrom != null) {
			conditions.add("cr.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("cr.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private java.util.Optional<CostRecordRow> costRecordRow(Long id) {
		return this.jdbcTemplate.query("""
				select cr.id, cr.record_no, cr.work_order_id, wo.work_order_no, cr.product_material_id,
				       pm.code as product_material_code, pm.name as product_material_name, wo.status as work_order_status,
				       cr.cost_type, cr.source_type, cr.source_document_type, cr.source_document_no,
				       cr.source_document_id, cr.source_line_id, cr.work_order_material_id, cr.material_id,
				       m.code as material_code, m.name as material_name, cr.unit_id, u.name as unit_name,
				       cr.quantity, cr.unit_price, cr.amount, cr.basis_type, cr.business_date, cr.status,
				       cr.remark, cr.recorded_by, cr.recorded_at, cr.created_by, cr.created_at, cr.updated_by,
				       cr.updated_at
				from mfg_cost_record cr
				join mfg_work_order wo on wo.id = cr.work_order_id
				join mst_material pm on pm.id = cr.product_material_id
				left join mst_material m on m.id = cr.material_id
				left join mst_unit u on u.id = cr.unit_id
				where cr.id = ?
				""", this::mapCostRecordRow, id).stream().findFirst();
	}

	private java.util.Optional<CostRecordRow> lockCostRecord(Long id) {
		return this.jdbcTemplate.query("""
				select cr.id, cr.record_no, cr.work_order_id, wo.work_order_no, cr.product_material_id,
				       pm.code as product_material_code, pm.name as product_material_name, wo.status as work_order_status,
				       cr.cost_type, cr.source_type, cr.source_document_type, cr.source_document_no,
				       cr.source_document_id, cr.source_line_id, cr.work_order_material_id, cr.material_id,
				       m.code as material_code, m.name as material_name, cr.unit_id, u.name as unit_name,
				       cr.quantity, cr.unit_price, cr.amount, cr.basis_type, cr.business_date, cr.status,
				       cr.remark, cr.recorded_by, cr.recorded_at, cr.created_by, cr.created_at, cr.updated_by,
				       cr.updated_at
				from mfg_cost_record cr
				join mfg_work_order wo on wo.id = cr.work_order_id
				join mst_material pm on pm.id = cr.product_material_id
				left join mst_material m on m.id = cr.material_id
				left join mst_unit u on u.id = cr.unit_id
				where cr.id = ?
				for update of cr
				""", this::mapCostRecordRow, id).stream().findFirst();
	}

	private java.util.Optional<WorkOrderRef> workOrderRef(Long workOrderId) {
		return this.jdbcTemplate.query("""
				select wo.id, wo.work_order_no, wo.product_material_id, pm.code as product_material_code,
				       pm.name as product_material_name, wo.status
				from mfg_work_order wo
				join mst_material pm on pm.id = wo.product_material_id
				where wo.id = ?
				""", (rs, rowNum) -> new WorkOrderRef(rs.getLong("id"), rs.getString("work_order_no"),
				rs.getLong("product_material_id"), rs.getString("product_material_code"),
				rs.getString("product_material_name"), ProductionWorkOrderStatus.valueOf(rs.getString("status"))),
				workOrderId).stream().findFirst();
	}

	private String sourceStatus(CostSourceDocumentType sourceDocumentType, Long sourceDocumentId) {
		if (sourceDocumentId == null) {
			return null;
		}
		String table;
		if (sourceDocumentType == CostSourceDocumentType.PRODUCTION_MATERIAL_ISSUE) {
			table = "mfg_material_issue";
		}
		else if (sourceDocumentType == CostSourceDocumentType.PRODUCTION_WORK_REPORT) {
			table = "mfg_work_report";
		}
		else if (sourceDocumentType == CostSourceDocumentType.PRODUCTION_COMPLETION_RECEIPT) {
			table = "mfg_completion_receipt";
		}
		else {
			return null;
		}
		return this.jdbcTemplate.query("select status from " + table + " where id = ?",
				(rs, rowNum) -> rs.getString("status"), sourceDocumentId).stream().findFirst().orElse(null);
	}

	private void validateEnabledUnit(Long unitId) {
		String status = this.jdbcTemplate.query("select status from mst_unit where id = ?",
				(rs, rowNum) -> rs.getString("status"), unitId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private CostRecordSummaryResponse mapCostRecordSummary(ResultSet rs, int rowNum) throws SQLException {
		return new CostRecordSummaryResponse(rs.getLong("id"), rs.getString("record_no"), rs.getLong("work_order_id"),
				rs.getString("work_order_no"), rs.getLong("product_material_id"),
				rs.getString("product_material_code"), rs.getString("product_material_name"),
				rs.getString("cost_type"), rs.getString("source_type"), rs.getString("source_document_type"),
				rs.getString("source_document_no"), nullableLong(rs, "source_document_id"),
				nullableLong(rs, "source_line_id"), rs.getString("basis_type"), nullableLong(rs, "material_id"),
				rs.getString("material_code"), rs.getString("material_name"), nullableLong(rs, "unit_id"),
				rs.getString("unit_name"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
				rs.getBigDecimal("amount"), rs.getObject("business_date", LocalDate.class), rs.getString("status"),
				rs.getString("remark"), rs.getString("recorded_by"),
				rs.getObject("recorded_at", OffsetDateTime.class), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private CostRecordRow mapCostRecordRow(ResultSet rs, int rowNum) throws SQLException {
		return new CostRecordRow(rs.getLong("id"), rs.getString("record_no"), rs.getLong("work_order_id"),
				rs.getString("work_order_no"), rs.getLong("product_material_id"),
				rs.getString("product_material_code"), rs.getString("product_material_name"),
				rs.getString("work_order_status"), CostType.valueOf(rs.getString("cost_type")),
				CostSourceType.valueOf(rs.getString("source_type")),
				CostSourceDocumentType.valueOf(rs.getString("source_document_type")),
				rs.getString("source_document_no"), nullableLong(rs, "source_document_id"),
				nullableLong(rs, "source_line_id"), nullableLong(rs, "work_order_material_id"),
				nullableLong(rs, "material_id"), rs.getString("material_code"), rs.getString("material_name"),
				nullableLong(rs, "unit_id"), rs.getString("unit_name"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"),
				CostBasisType.valueOf(rs.getString("basis_type")), rs.getObject("business_date", LocalDate.class),
				CostRecordStatus.valueOf(rs.getString("status")), rs.getString("remark"),
				rs.getString("recorded_by"), rs.getObject("recorded_at", OffsetDateTime.class),
				rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private BigDecimal validateQuantity(BigDecimal value) {
		if (value == null) {
			return null;
		}
		if (value.compareTo(ZERO) <= 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.COST_QUANTITY_INVALID);
		}
		return value;
	}

	private BigDecimal validateUnitPrice(BigDecimal value) {
		if (value == null) {
			return null;
		}
		if (value.compareTo(ZERO) < 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.COST_AMOUNT_INVALID);
		}
		return value;
	}

	private BigDecimal validateAmount(BigDecimal value) {
		if (value == null) {
			return null;
		}
		if (value.compareTo(ZERO) < 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.COST_AMOUNT_INVALID);
		}
		return value;
	}

	private CostType parseCostType(String value) {
		try {
			return CostType.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.COST_TYPE_INVALID);
		}
	}

	private CostSourceType parseSourceType(String value) {
		try {
			return CostSourceType.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private CostSourceDocumentType parseSourceDocumentType(String value) {
		try {
			return CostSourceDocumentType.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private CostBasisType parseManualBasisType(String value) {
		try {
			CostBasisType basisType = CostBasisType.valueOf(value);
			if (basisType == CostBasisType.MANUAL_AMOUNT || basisType == CostBasisType.MANUAL_UNIT_PRICE_QUANTITY) {
				return basisType;
			}
			throw new BusinessException(ApiErrorCode.COST_BASIS_INVALID);
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.COST_BASIS_INVALID);
		}
	}

	private BusinessException recordNotFound() {
		return new BusinessException(ApiErrorCode.COST_RECORD_NOT_FOUND);
	}

	private BusinessException workOrderNotFound() {
		return new BusinessException(ApiErrorCode.COST_WORK_ORDER_NOT_FOUND);
	}

	private QueryParts where(List<String> conditions, List<Object> args) {
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args);
	}

	private List<Object> paginationArgs(QueryParts queryParts, int pageSize, int page) {
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		return args;
	}

	private String recordNo(Long id) {
		return this.jdbcTemplate.queryForObject("select record_no from mfg_cost_record where id = ?", String.class, id);
	}

	private String nextNo() {
		int value = Math.floorMod(COST_NO_SEQUENCE.getAndIncrement(), 1000);
		return "COST-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", value);
	}

	private long integerDigits(BigDecimal value) {
		return Math.max(0L, (long) value.precision() - value.scale());
	}

	private String validateRequiredText(String value, int maxLength) {
		if (!hasText(value) || value.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value;
	}

	private String validateOptionalText(String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return blankToNull(value);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record CostRecordPayload(Long workOrderId, String costType, String basisType, LocalDate businessDate,
			BigDecimal quantity, Long unitId, BigDecimal unitPrice, BigDecimal amount, String sourceDocumentNo,
			String remark) {
	}

	public record CostRecordSummaryResponse(Long id, String recordNo, Long workOrderId, String workOrderNo,
			Long productMaterialId, String productMaterialCode, String productMaterialName, String costType,
			String sourceType, String sourceDocumentType, String sourceDocumentNo, Long sourceDocumentId,
			Long sourceLineId, String basisType, Long materialId, String materialCode, String materialName, Long unitId,
			String unitName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, LocalDate businessDate,
			String status, String remark, String recordedByName, OffsetDateTime recordedAt, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record CostRecordDetailResponse(Long id, String recordNo, Long workOrderId, String workOrderNo,
			Long productMaterialId, String productMaterialCode, String productMaterialName, String costType,
			String sourceType, String sourceDocumentType, String sourceDocumentNo, Long sourceDocumentId,
			Long sourceLineId, String basisType, Long materialId, String materialCode, String materialName, Long unitId,
			String unitName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, LocalDate businessDate,
			String status, String remark, String recordedByName, OffsetDateTime recordedAt, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String workOrderStatus, String sourceStatus,
			SourceSummaryResponse sourceSummary, List<OutputTraceResponse> outputTrace,
			List<AuditSummaryResponse> auditSummary) {
	}

	public record WorkOrderCostSummaryResponse(Long workOrderId, String workOrderNo, Long productMaterialId,
			String productMaterialCode, String productMaterialName, boolean formalAccounting,
			List<CostRecordSummaryResponse> records, List<CostAmountSummaryResponse> amountSummaries,
			List<CostQuantitySummaryResponse> quantitySummaries, List<OutputTraceResponse> outputTraces) {
	}

	public record CostAmountSummaryResponse(String costType, BigDecimal amount) {
	}

	public record CostQuantitySummaryResponse(String costType, BigDecimal quantity) {
	}

	public record SourceSummaryResponse(String sourceStatus, String sourceDocumentNo, BigDecimal quantity,
			Long materialId, String materialCode, String materialName, Long unitId, String unitName) {
	}

	public record OutputTraceResponse(Long receiptId, String receiptNo, Long workOrderId, LocalDate businessDate,
			Long receiptWarehouseId, String receiptWarehouseName, BigDecimal quantity, BigDecimal beforeQuantity,
			BigDecimal afterQuantity, String postedByName, OffsetDateTime postedAt) {
	}

	public record AuditSummaryResponse(Long id, String operatorUsername, String action, OffsetDateTime createdAt) {
	}

	private record ValidatedManualCost(WorkOrderRef workOrder, CostType costType, CostBasisType basisType,
			LocalDate businessDate, BigDecimal quantity, Long unitId, BigDecimal unitPrice, BigDecimal amount,
			String sourceDocumentNo, String remark) {
	}

	private record WorkOrderRef(Long id, String workOrderNo, Long productMaterialId, String productMaterialCode,
			String productMaterialName, ProductionWorkOrderStatus status) {
	}

	private record CostRecordRow(Long id, String recordNo, Long workOrderId, String workOrderNo,
			Long productMaterialId, String productMaterialCode, String productMaterialName, String workOrderStatus,
			CostType costType, CostSourceType sourceType, CostSourceDocumentType sourceDocumentType,
			String sourceDocumentNo, Long sourceDocumentId, Long sourceLineId, Long workOrderMaterialId,
			Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, CostBasisType basisType,
			LocalDate businessDate, CostRecordStatus status, String remark, String recordedBy,
			OffsetDateTime recordedAt, String createdBy, OffsetDateTime createdAt, String updatedBy,
			OffsetDateTime updatedAt) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
