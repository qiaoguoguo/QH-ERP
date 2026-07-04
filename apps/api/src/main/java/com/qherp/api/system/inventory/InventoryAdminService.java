package com.qherp.api.system.inventory;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventoryAdminService {

	private static final String TARGET_TYPE = "INVENTORY_DOCUMENT";

	private static final String SOURCE_TYPE = "INVENTORY_DOCUMENT";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final int MAX_DOCUMENT_NO_ATTEMPTS = 3;

	private static final AtomicInteger DOCUMENT_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final InventoryPostingService inventoryPostingService;

	public InventoryAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			InventoryPostingService inventoryPostingService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.inventoryPostingService = inventoryPostingService;
	}

	@Transactional(readOnly = true)
	public PageResponse<InventoryBalanceResponse> balances(String keyword, Long warehouseId, Long materialId,
			String materialType, boolean onlyPositive, int page, int pageSize) {
		QueryParts queryParts = balanceQueryParts(keyword, warehouseId, materialId, materialType, onlyPositive);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_balance b
				left join mst_warehouse w on w.id = b.warehouse_id
				left join mst_material m on m.id = b.material_id
				left join mst_unit u on u.id = b.unit_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<InventoryBalanceResponse> items = this.jdbcTemplate.query("""
				select b.id, b.warehouse_id, w.code as warehouse_code, w.name as warehouse_name,
				       b.material_id, m.code as material_code, m.name as material_name,
				       m.specification as material_spec, m.material_type, b.unit_id, u.name as unit_name,
				       b.quantity_on_hand, b.locked_quantity,
				       (b.quantity_on_hand - b.locked_quantity) as available_quantity, b.updated_at
				from inv_stock_balance b
				left join mst_warehouse w on w.id = b.warehouse_id
				left join mst_material m on m.id = b.material_id
				left join mst_unit u on u.id = b.unit_id
				%s
				order by b.updated_at desc, b.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapBalance, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<InventoryMovementResponse> movements(String keyword, Long warehouseId, Long materialId,
			String movementType, String direction, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize) {
		QueryParts queryParts = movementQueryParts(keyword, warehouseId, materialId, movementType, direction, dateFrom,
				dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement mv
				join mst_warehouse w on w.id = mv.warehouse_id
				join mst_material m on m.id = mv.material_id
				join mst_unit u on u.id = mv.unit_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<InventoryMovementResponse> items = this.jdbcTemplate.query("""
				select mv.id, mv.movement_no, mv.movement_type, mv.direction, mv.warehouse_id,
				       w.name as warehouse_name, mv.material_id, m.code as material_code, m.name as material_name,
				       mv.unit_id, u.name as unit_name, mv.quantity, mv.before_quantity, mv.after_quantity,
				       mv.source_type, mv.source_id, mv.source_line_id, mv.business_date, mv.reason, mv.remark,
				       mv.operator_name, mv.occurred_at
				from inv_stock_movement mv
				join mst_warehouse w on w.id = mv.warehouse_id
				join mst_material m on m.id = mv.material_id
				join mst_unit u on u.id = mv.unit_id
				%s
				order by mv.occurred_at desc, mv.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapMovement, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<InventoryDocumentSummaryResponse> documents(String keyword, String documentType, String status,
			LocalDate dateFrom, LocalDate dateTo, int page, int pageSize) {
		QueryParts queryParts = documentQueryParts(keyword, documentType, status, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("select count(*) from inv_inventory_document d "
				+ queryParts.where(), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<InventoryDocumentSummaryResponse> items = this.jdbcTemplate.query("""
				select d.id, d.document_no, d.document_type, d.status, d.business_date, d.reason, d.remark,
				       (select count(*) from inv_inventory_document_line l where l.document_id = d.id) as line_count,
				       d.created_by, d.created_at, d.updated_at, d.posted_by, d.posted_at
				from inv_inventory_document d
				%s
				order by d.updated_at desc, d.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapDocumentSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public InventoryDocumentDetailResponse document(Long id) {
		InventoryDocumentSummaryResponse summary = documentSummary(id).orElseThrow(this::notFound);
		return new InventoryDocumentDetailResponse(summary.id(), summary.documentNo(), summary.documentType(),
				summary.status(), summary.businessDate(), summary.reason(), summary.remark(), summary.lineCount(),
				summary.createdByName(), summary.createdAt(), summary.updatedAt(), summary.postedByName(),
				summary.postedAt(), documentLines(id));
	}

	@Transactional
	public InventoryDocumentDetailResponse createDocument(InventoryDocumentRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedDocument document = validateDocument(request);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertDocumentHeaderWithRetry(document, operator, now);
			insertDocumentLines(created.id(), document.lines(), now);
			this.auditService.record(operator, "INVENTORY_DOCUMENT_CREATE", TARGET_TYPE, created.id(),
					created.documentNo(), servletRequest);
			return document(created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateInventoryException(exception);
		}
	}

	@Transactional
	public InventoryDocumentDetailResponse updateDocument(Long id, InventoryDocumentRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		DocumentRow current = lockDocument(id).orElseThrow(this::notFound);
		if (current.status() != InventoryDocumentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_POSTED_IMMUTABLE);
		}
		ValidatedDocument document = validateDocument(request);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			int updated = this.jdbcTemplate.update("""
					update inv_inventory_document
					set document_type = ?, business_date = ?, reason = ?, remark = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", document.documentType().name(), document.businessDate(), document.reason(),
					blankToNull(document.remark()), operator.username(), now, id);
			if (updated == 0) {
				throw notFound();
			}
			this.jdbcTemplate.update("delete from inv_inventory_document_line where document_id = ?", id);
			insertDocumentLines(id, document.lines(), now);
			this.auditService.record(operator, "INVENTORY_DOCUMENT_UPDATE", TARGET_TYPE, id, current.documentNo(),
					servletRequest);
			return document(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateInventoryException(exception);
		}
	}

	@Transactional
	public InventoryDocumentDetailResponse postDocument(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		try {
			DocumentRow document = lockDocument(id).orElseThrow(this::notFound);
			if (document.status() != InventoryDocumentStatus.DRAFT) {
				throw new BusinessException(ApiErrorCode.INVENTORY_DUPLICATE_POST);
			}
			List<DocumentLineRow> lines = documentLineRows(id);
			if (lines.isEmpty()) {
				throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_EMPTY_LINES);
			}
			OffsetDateTime now = OffsetDateTime.now();
			for (DocumentLineRow line : lines) {
				postLine(document, line, operator.username(), now);
			}
			this.jdbcTemplate.update("""
					update inv_inventory_document
					set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", InventoryDocumentStatus.POSTED.name(), operator.username(), now, operator.username(), now,
					id);
			this.auditService.record(operator, "INVENTORY_DOCUMENT_POST", TARGET_TYPE, id, document.documentNo(),
					servletRequest);
			return document(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateInventoryException(exception);
		}
	}

	private void postLine(DocumentRow document, DocumentLineRow line, String operatorName, OffsetDateTime now) {
		MaterialRef material = validateEnabledMaterial(line.materialId());
		validateEnabledWarehouse(line.warehouseId());
		validateUnit(line.unitId(), material);
		if (document.documentType() == InventoryDocumentType.OPENING) {
			if (line.adjustmentDirection() != null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			validateOpeningNotExists(line.warehouseId(), line.materialId());
		}
		InventoryDirection direction = direction(document.documentType(), line.adjustmentDirection());
		InventoryMovementType movementType = movementType(document.documentType(), line.adjustmentDirection());
		InventoryPostingService.PostingResult posting = this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(movementType, direction, line.warehouseId(),
						line.materialId(), line.unitId(), line.quantity(), SOURCE_TYPE, document.id(), line.id(),
						document.businessDate(), document.reason(), line.remark(), operatorName));
		this.jdbcTemplate.update("""
				update inv_inventory_document_line
				set before_quantity = ?, after_quantity = ?, updated_at = ?
				where id = ?
				""", posting.beforeQuantity(), posting.afterQuantity(), now, line.id());
	}

	private void validateOpeningNotExists(Long warehouseId, Long materialId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where warehouse_id = ?
				and material_id = ?
				and movement_type = ?
				""", Long.class, warehouseId, materialId, InventoryMovementType.OPENING.name());
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_OPENING_EXISTS);
		}
	}

	private ValidatedDocument validateDocument(InventoryDocumentRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		InventoryDocumentType documentType = parseDocumentType(request.documentType());
		if (request.businessDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String reason = validateRequiredText(request.reason(), 200);
		String remark = validateOptionalText(request.remark(), 500);
		if (request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<String> warehouseMaterials = new HashSet<>();
		List<ValidatedLine> lines = new ArrayList<>();
		for (InventoryDocumentLineRequest line : request.lines()) {
			if (line == null || line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			BigDecimal quantity = validateQuantity(line.quantity());
			String lineRemark = validateOptionalText(line.remark(), 500);
			validateEnabledWarehouse(line.warehouseId());
			MaterialRef material = validateEnabledMaterial(line.materialId());
			Long unitId = validateUnit(line.unitId(), material);
			if (!warehouseMaterials.add(line.warehouseId() + ":" + line.materialId())) {
				throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_DUPLICATE_LINE);
			}
			InventoryAdjustmentDirection adjustmentDirection = validateAdjustmentDirection(documentType,
					line.adjustmentDirection());
			lines.add(new ValidatedLine(line.lineNo(), line.warehouseId(), line.materialId(), unitId, quantity,
					adjustmentDirection, lineRemark));
		}
		return new ValidatedDocument(documentType, request.businessDate(), reason, remark, lines);
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
		return value;
	}

	private BigDecimal validateQuantity(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) <= 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUANTITY_INVALID);
		}
		return value;
	}

	private long integerDigits(BigDecimal value) {
		return Math.max(0L, (long) value.precision() - value.scale());
	}

	private InventoryAdjustmentDirection validateAdjustmentDirection(InventoryDocumentType documentType,
			String adjustmentDirection) {
		if (documentType == InventoryDocumentType.OPENING) {
			if (hasText(adjustmentDirection)) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			return null;
		}
		if (!hasText(adjustmentDirection)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return parseAdjustmentDirection(adjustmentDirection);
	}

	private MaterialRef validateEnabledMaterial(Long materialId) {
		if (materialId == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_MATERIAL_INVALID);
		}
		MaterialRef material = materialRef(materialId).orElseThrow(this::materialInvalid);
		if (!"ENABLED".equals(material.status())) {
			throw materialInvalid();
		}
		return material;
	}

	private void validateEnabledWarehouse(Long warehouseId) {
		if (warehouseId == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_WAREHOUSE_INVALID);
		}
		String status = this.jdbcTemplate.query("select status from mst_warehouse where id = ?",
				(rs, rowNum) -> rs.getString("status"), warehouseId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.INVENTORY_WAREHOUSE_INVALID);
		}
	}

	private Long validateUnit(Long requestedUnitId, MaterialRef material) {
		Long unitId = requestedUnitId == null ? material.unitId() : requestedUnitId;
		String status = this.jdbcTemplate.query("select status from mst_unit where id = ?",
				(rs, rowNum) -> rs.getString("status"), unitId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status) || !unitId.equals(material.unitId())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_UNIT_INVALID);
		}
		return unitId;
	}

	private Optional<MaterialRef> materialRef(Long materialId) {
		return this.jdbcTemplate
			.query("""
					select id, code, name, unit_id, status
					from mst_material
					where id = ?
					""", (rs, rowNum) -> new MaterialRef(rs.getLong("id"), rs.getString("code"),
					rs.getString("name"), rs.getLong("unit_id"), rs.getString("status")), materialId)
			.stream()
			.findFirst();
	}

	private void insertDocumentLines(Long documentId, List<ValidatedLine> lines, OffsetDateTime now) {
		for (ValidatedLine line : lines) {
			this.jdbcTemplate.update("""
					insert into inv_inventory_document_line (
						document_id, line_no, warehouse_id, material_id, unit_id, quantity, adjustment_direction,
						remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", documentId, line.lineNo(), line.warehouseId(), line.materialId(), line.unitId(),
					line.quantity(), line.adjustmentDirection() == null ? null : line.adjustmentDirection().name(),
					blankToNull(line.remark()), now, now);
		}
	}

	private CreatedDocument insertDocumentHeaderWithRetry(ValidatedDocument document, CurrentUser operator,
			OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_DOCUMENT_NO_ATTEMPTS; attempt++) {
			String documentNo = documentNo(document.documentType());
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into inv_inventory_document (
							document_no, document_type, status, business_date, reason, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, documentNo, document.documentType().name(),
						InventoryDocumentStatus.DRAFT.name(), document.businessDate(), document.reason(),
						blankToNull(document.remark()), operator.username(), now, operator.username(), now);
				return new CreatedDocument(id, documentNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_inv_inventory_document_no")) {
					if (attempt == MAX_DOCUMENT_NO_ATTEMPTS) {
						throw new BusinessException(ApiErrorCode.CONFLICT);
					}
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private QueryParts balanceQueryParts(String keyword, Long warehouseId, Long materialId, String materialType,
			boolean onlyPositive) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(w.name ilike ? or m.code ilike ? or m.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (warehouseId != null) {
			conditions.add("b.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (materialId != null) {
			conditions.add("b.material_id = ?");
			args.add(materialId);
		}
		if (hasText(materialType)) {
			conditions.add("m.material_type = ?");
			args.add(materialType);
		}
		if (onlyPositive) {
			conditions.add("b.quantity_on_hand > 0");
		}
		return where(conditions, args);
	}

	private QueryParts movementQueryParts(String keyword, Long warehouseId, Long materialId, String movementType,
			String direction, LocalDate dateFrom, LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(mv.movement_no ilike ? or w.name ilike ? or m.code ilike ? or m.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (warehouseId != null) {
			conditions.add("mv.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (materialId != null) {
			conditions.add("mv.material_id = ?");
			args.add(materialId);
		}
		if (hasText(movementType)) {
			conditions.add("mv.movement_type = ?");
			args.add(parseMovementType(movementType).name());
		}
		if (hasText(direction)) {
			conditions.add("mv.direction = ?");
			args.add(parseDirection(direction).name());
		}
		if (dateFrom != null) {
			conditions.add("mv.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("mv.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private QueryParts documentQueryParts(String keyword, String documentType, String status, LocalDate dateFrom,
			LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(d.document_no ilike ? or d.reason ilike ? or d.remark ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (hasText(documentType)) {
			conditions.add("d.document_type = ?");
			args.add(parseDocumentType(documentType).name());
		}
		if (hasText(status)) {
			conditions.add("d.status = ?");
			args.add(parseDocumentStatus(status).name());
		}
		if (dateFrom != null) {
			conditions.add("d.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("d.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private Optional<InventoryDocumentSummaryResponse> documentSummary(Long id) {
		return this.jdbcTemplate
			.query("""
					select d.id, d.document_no, d.document_type, d.status, d.business_date, d.reason, d.remark,
					       (select count(*) from inv_inventory_document_line l where l.document_id = d.id) as line_count,
					       d.created_by, d.created_at, d.updated_at, d.posted_by, d.posted_at
					from inv_inventory_document d
					where d.id = ?
					""", this::mapDocumentSummary, id)
			.stream()
			.findFirst();
	}

	private Optional<DocumentRow> lockDocument(Long id) {
		return this.jdbcTemplate
			.query("""
					select id, document_no, document_type, status, business_date, reason, remark
					from inv_inventory_document
					where id = ?
					for update
					""", this::mapDocumentRow, id)
			.stream()
			.findFirst();
	}

	private List<DocumentLineRow> documentLineRows(Long documentId) {
		return this.jdbcTemplate.query("""
				select id, line_no, warehouse_id, material_id, unit_id, quantity, adjustment_direction,
				       before_quantity, after_quantity, remark
				from inv_inventory_document_line
				where document_id = ?
				order by line_no asc, id asc
				""", this::mapDocumentLineRow, documentId);
	}

	private List<InventoryDocumentLineResponse> documentLines(Long documentId) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.warehouse_id, w.name as warehouse_name, l.material_id,
				       m.code as material_code, m.name as material_name, l.unit_id, u.name as unit_name,
				       l.quantity, l.adjustment_direction, l.before_quantity, l.after_quantity, l.remark
				from inv_inventory_document_line l
				join mst_warehouse w on w.id = l.warehouse_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.document_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> new InventoryDocumentLineResponse(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("warehouse_id"), rs.getString("warehouse_name"), rs.getLong("material_id"),
				rs.getString("material_code"), rs.getString("material_name"), rs.getLong("unit_id"),
				rs.getString("unit_name"), rs.getBigDecimal("quantity"), rs.getString("adjustment_direction"),
				rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"), rs.getString("remark")),
				documentId);
	}

	private InventoryBalanceResponse mapBalance(ResultSet rs, int rowNum) throws SQLException {
		return new InventoryBalanceResponse(rs.getLong("id"), rs.getLong("warehouse_id"),
				rs.getString("warehouse_code"), rs.getString("warehouse_name"), rs.getLong("material_id"),
				rs.getString("material_code"), rs.getString("material_name"), rs.getString("material_spec"),
				rs.getString("material_type"), rs.getLong("unit_id"), rs.getString("unit_name"),
				rs.getBigDecimal("quantity_on_hand"), rs.getBigDecimal("locked_quantity"),
				rs.getBigDecimal("available_quantity"), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private InventoryMovementResponse mapMovement(ResultSet rs, int rowNum) throws SQLException {
		return new InventoryMovementResponse(rs.getLong("id"), rs.getString("movement_no"),
				rs.getString("movement_type"), rs.getString("direction"), rs.getLong("warehouse_id"),
				rs.getString("warehouse_name"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
				rs.getString("source_type"), rs.getLong("source_id"), rs.getLong("source_line_id"),
				rs.getObject("business_date", LocalDate.class), rs.getString("reason"), rs.getString("remark"),
				rs.getString("operator_name"), rs.getObject("occurred_at", OffsetDateTime.class));
	}

	private InventoryDocumentSummaryResponse mapDocumentSummary(ResultSet rs, int rowNum) throws SQLException {
		return new InventoryDocumentSummaryResponse(rs.getLong("id"), rs.getString("document_no"),
				rs.getString("document_type"), rs.getString("status"), rs.getObject("business_date", LocalDate.class),
				rs.getString("reason"), rs.getString("remark"), rs.getInt("line_count"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("posted_by"), rs.getObject("posted_at", OffsetDateTime.class));
	}

	private DocumentRow mapDocumentRow(ResultSet rs, int rowNum) throws SQLException {
		return new DocumentRow(rs.getLong("id"), rs.getString("document_no"),
				InventoryDocumentType.valueOf(rs.getString("document_type")),
				InventoryDocumentStatus.valueOf(rs.getString("status")),
				rs.getObject("business_date", LocalDate.class), rs.getString("reason"), rs.getString("remark"));
	}

	private DocumentLineRow mapDocumentLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new DocumentLineRow(rs.getLong("id"), rs.getInt("line_no"), rs.getLong("warehouse_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				parseNullableAdjustmentDirection(rs.getString("adjustment_direction")),
				rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"), rs.getString("remark"));
	}

	private BusinessException duplicateInventoryException(DuplicateKeyException exception) {
		if (containsConstraint(exception, "uk_inv_document_line_material")) {
			return new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_DUPLICATE_LINE);
		}
		if (containsConstraint(exception, "uk_inv_stock_movement_source")) {
			return new BusinessException(ApiErrorCode.INVENTORY_MOVEMENT_SOURCE_DUPLICATED);
		}
		if (containsConstraint(exception, "uk_inv_stock_movement_opening_once")) {
			return new BusinessException(ApiErrorCode.INVENTORY_OPENING_EXISTS);
		}
		if (containsConstraint(exception, "uk_inv_inventory_document_no")) {
			return new BusinessException(ApiErrorCode.CONFLICT);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private InventoryDocumentType parseDocumentType(String value) {
		try {
			return InventoryDocumentType.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_TYPE_INVALID);
		}
	}

	private InventoryDocumentStatus parseDocumentStatus(String value) {
		try {
			return InventoryDocumentStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
	}

	private InventoryMovementType parseMovementType(String value) {
		try {
			return InventoryMovementType.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private InventoryDirection parseDirection(String value) {
		try {
			return InventoryDirection.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private InventoryAdjustmentDirection parseAdjustmentDirection(String value) {
		try {
			return InventoryAdjustmentDirection.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private InventoryAdjustmentDirection parseNullableAdjustmentDirection(String value) {
		return hasText(value) ? InventoryAdjustmentDirection.valueOf(value) : null;
	}

	private InventoryDirection direction(InventoryDocumentType documentType, InventoryAdjustmentDirection adjustment) {
		if (documentType == InventoryDocumentType.OPENING || adjustment == InventoryAdjustmentDirection.INCREASE) {
			return InventoryDirection.IN;
		}
		return InventoryDirection.OUT;
	}

	private InventoryMovementType movementType(InventoryDocumentType documentType,
			InventoryAdjustmentDirection adjustment) {
		if (documentType == InventoryDocumentType.OPENING) {
			return InventoryMovementType.OPENING;
		}
		return adjustment == InventoryAdjustmentDirection.INCREASE ? InventoryMovementType.ADJUSTMENT_INCREASE
				: InventoryMovementType.ADJUSTMENT_DECREASE;
	}

	private BusinessException notFound() {
		return new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_NOT_FOUND);
	}

	private BusinessException materialInvalid() {
		return new BusinessException(ApiErrorCode.INVENTORY_MATERIAL_INVALID);
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

	private String documentNo(InventoryDocumentType documentType) {
		String prefix = documentType == InventoryDocumentType.OPENING ? "INV-OPEN-" : "INV-ADJ-";
		int sequence = Math.floorMod(DOCUMENT_NO_SEQUENCE.getAndIncrement(), 1000);
		return prefix + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", sequence);
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

	public record InventoryDocumentLineRequest(@NotNull Integer lineNo, @NotNull Long warehouseId,
			@NotNull Long materialId, Long unitId, @NotNull BigDecimal quantity, String adjustmentDirection,
			String remark) {
	}

	public record InventoryDocumentRequest(@NotBlank String documentType, @NotNull LocalDate businessDate,
			@NotBlank String reason, String remark, @Valid List<InventoryDocumentLineRequest> lines) {
	}

	public record InventoryBalanceResponse(Long id, Long warehouseId, String warehouseCode, String warehouseName,
			Long materialId, String materialCode, String materialName, String materialSpec, String materialType,
			Long unitId, String unitName, BigDecimal quantityOnHand, BigDecimal lockedQuantity,
			BigDecimal availableQuantity, OffsetDateTime updatedAt) {
	}

	public record InventoryMovementResponse(Long id, String movementNo, String movementType, String direction,
			Long warehouseId, String warehouseName, Long materialId, String materialCode, String materialName,
			Long unitId, String unitName, BigDecimal quantity, BigDecimal beforeQuantity, BigDecimal afterQuantity,
			String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String remark,
			String operatorName, OffsetDateTime occurredAt) {
	}

	public record InventoryDocumentSummaryResponse(Long id, String documentNo, String documentType, String status,
			LocalDate businessDate, String reason, String remark, int lineCount, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt) {
	}

	public record InventoryDocumentLineResponse(Long id, Integer lineNo, Long warehouseId, String warehouseName,
			Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			BigDecimal quantity, String adjustmentDirection, BigDecimal beforeQuantity, BigDecimal afterQuantity,
			String remark) {
	}

	public record InventoryDocumentDetailResponse(Long id, String documentNo, String documentType, String status,
			LocalDate businessDate, String reason, String remark, int lineCount, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			List<InventoryDocumentLineResponse> lines) {
	}

	private record ValidatedDocument(InventoryDocumentType documentType, LocalDate businessDate, String reason,
			String remark, List<ValidatedLine> lines) {
	}

	private record ValidatedLine(Integer lineNo, Long warehouseId, Long materialId, Long unitId, BigDecimal quantity,
			InventoryAdjustmentDirection adjustmentDirection, String remark) {
	}

	private record DocumentRow(Long id, String documentNo, InventoryDocumentType documentType,
			InventoryDocumentStatus status, LocalDate businessDate, String reason, String remark) {
	}

	private record DocumentLineRow(Long id, Integer lineNo, Long warehouseId, Long materialId, Long unitId,
			BigDecimal quantity, InventoryAdjustmentDirection adjustmentDirection, BigDecimal beforeQuantity,
			BigDecimal afterQuantity, String remark) {
	}

	private record CreatedDocument(Long id, String documentNo) {
	}

	private record MaterialRef(Long id, String code, String name, Long unitId, String status) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
