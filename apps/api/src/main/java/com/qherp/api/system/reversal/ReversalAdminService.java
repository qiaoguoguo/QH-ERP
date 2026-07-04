package com.qherp.api.system.reversal;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.finance.ReceivableStatus;
import com.qherp.api.system.inventory.InventoryDirection;
import com.qherp.api.system.inventory.InventoryMovementType;
import com.qherp.api.system.inventory.InventoryPostingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReversalAdminService {

	private static final String SALES_RETURN_TARGET = "SALES_RETURN";

	private static final String SALES_SHIPMENT_SOURCE = "SALES_SHIPMENT";

	private static final String SALES_SHIPMENT_LINE_SOURCE = "SALES_SHIPMENT_LINE";

	private static final String SALES_RETURN_SOURCE = "SALES_RETURN";

	private static final String SETTLEMENT_ADJUSTMENT_SOURCE = "SETTLEMENT_ADJUSTMENT";

	private static final String RESTRICTED_MESSAGE = "来源无查看权限";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger SALES_RETURN_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger SETTLEMENT_ADJUSTMENT_NO_SEQUENCE = new AtomicInteger();

	private static final int MAX_NO_ATTEMPTS = 3;

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final InventoryPostingService inventoryPostingService;

	public ReversalAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			InventoryPostingService inventoryPostingService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.inventoryPostingService = inventoryPostingService;
	}

	public PageResponse<Object> emptyPage(int page, int pageSize) {
		return PageResponse.of(List.of(), page, pageSize, 0);
	}

	@Transactional(readOnly = true)
	public PageResponse<SalesReturnSourceResponse> salesReturnSources(String keyword, Long customerId,
			Long warehouseId, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize, CurrentUser currentUser) {
		if (!canViewSalesShipment(currentUser)) {
			return PageResponse.of(List.of(), page, limit(pageSize), 0);
		}
		QueryParts queryParts = salesReturnSourceQuery(keyword, customerId, warehouseId, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_shipment sh
				join mst_customer c on c.id = sh.customer_id
				join mst_warehouse w on w.id = sh.warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<SalesReturnSourceResponse> sources = this.jdbcTemplate.query("""
				select sh.id, sh.shipment_no, sh.customer_id, c.name as customer_name, sh.warehouse_id,
				       w.name as warehouse_name, sh.business_date, sh.status
				from sal_sales_shipment sh
				join mst_customer c on c.id = sh.customer_id
				join mst_warehouse w on w.id = sh.warehouse_id
				%s
				order by sh.business_date desc, sh.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapSalesReturnSource(rs), args.toArray());
		return PageResponse.of(sources, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<SalesReturnSummaryResponse> salesReturns(String keyword, Long customerId, Long warehouseId,
			String status, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize, CurrentUser currentUser) {
		QueryParts queryParts = salesReturnQuery(keyword, customerId, warehouseId, status, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_return r
				join mst_customer c on c.id = r.customer_id
				join mst_warehouse w on w.id = r.warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<SalesReturnSummaryResponse> items = this.jdbcTemplate.query("""
				select r.id, r.return_no, r.customer_id, c.name as customer_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_shipment_id, r.source_shipment_no, sh.business_date as source_date,
				       sh.status as source_status, r.business_date, r.status,
				       coalesce((select sum(l.quantity) from sal_sales_return_line l where l.return_id = r.id), 0) as total_quantity,
				       r.total_amount, r.created_at, r.updated_at
				from sal_sales_return r
				join mst_customer c on c.id = r.customer_id
				join mst_warehouse w on w.id = r.warehouse_id
				join sal_sales_shipment sh on sh.id = r.source_shipment_id
				%s
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapSalesReturnSummary(rs, currentUser),
				args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public SalesReturnDetailResponse salesReturn(Long id, CurrentUser currentUser) {
		SalesReturnRow row = salesReturnRow(id).orElseThrow(this::sourceNotFoundException);
		return salesReturnDetail(row, currentUser);
	}

	@Transactional
	public SalesReturnDetailResponse createSalesReturn(SalesReturnRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedSalesReturn validated = validateSalesReturnCreate(request);
		ShipmentRow shipment = lockShipment(validated.sourceShipmentId()).orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(shipment.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		Optional<SalesReturnRow> existing = existingSalesReturn(validated.sourceShipmentId(),
				validated.clientRequestId());
		if (existing.isPresent()) {
			return salesReturnDetail(existing.get(), operator);
		}
		List<ValidatedSalesReturnLine> lines = validateSalesReturnLines(shipment, validated.lines());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertSalesReturnWithRetry(shipment, validated, totalAmount(lines),
					operator.username(), now);
			insertSalesReturnLines(created.id(), lines, now);
			this.auditService.record(operator, "SALES_RETURN_CREATE", SALES_RETURN_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return salesReturn(created.id(), operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public SalesReturnDetailResponse updateSalesReturn(Long id, SalesReturnRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		SalesReturnRow current = lockSalesReturn(id).orElseThrow(this::sourceNotFoundException);
		if (current.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(current);
		ValidatedSalesReturn validated = validateSalesReturnCreate(request);
		if (!current.sourceShipmentId().equals(validated.sourceShipmentId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		ShipmentRow shipment = lockShipment(current.sourceShipmentId()).orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(shipment.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		List<ValidatedSalesReturnLine> lines = validateSalesReturnLines(shipment, validated.lines());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update sal_sales_return
					set business_date = ?, total_amount = ?, client_request_id = ?, remark = ?, updated_by = ?,
					    updated_at = ?, version = version + 1
					where id = ?
					""", validated.businessDate(), totalAmount(lines), blankToNull(validated.clientRequestId()),
					blankToNull(validated.remark()), operator.username(), now, id);
			this.jdbcTemplate.update("delete from sal_sales_return_line where return_id = ?", id);
			insertSalesReturnLines(id, lines, now);
			this.auditService.record(operator, "SALES_RETURN_UPDATE", SALES_RETURN_TARGET, id, current.returnNo(),
					servletRequest);
			return salesReturn(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public SalesReturnDetailResponse postSalesReturn(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		SalesReturnRow salesReturn = lockSalesReturn(id).orElseThrow(this::sourceNotFoundException);
		if (salesReturn.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(salesReturn);
		ShipmentRow shipment = lockShipment(salesReturn.sourceShipmentId()).orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(shipment.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		List<SalesReturnLineRow> lines = lockSalesReturnLines(id);
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_INVALID);
		}
		ReceivableRow receivable = lockReceivableForShipment(shipment.id()).orElseThrow(this::sourceNotFoundException);
		requireAdjustableReceivable(receivable);
		BigDecimal totalAmount = totalAmountFromRows(lines);
		if (totalAmount.compareTo(receivable.unreceivedAmount()) > 0) {
			throw new BusinessException(ApiErrorCode.REVERSAL_AMOUNT_EXCEEDS_AVAILABLE);
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			for (SalesReturnLineRow line : lines) {
				ShipmentLineRow sourceLine = lockShipmentLine(shipment.id(), line.sourceShipmentLineId())
					.orElseThrow(this::sourceNotFoundException);
				validateLineStillReturnable(sourceLine, line);
				InventoryPostingService.PostingResult posting = this.inventoryPostingService.post(
						new InventoryPostingService.PostingRequest(InventoryMovementType.SALES_RETURN_IN,
								InventoryDirection.IN, salesReturn.warehouseId(), line.materialId(), line.unitId(),
								line.quantity(), SALES_RETURN_SOURCE, salesReturn.id(), line.id(),
								salesReturn.businessDate(), "销售退货入库", line.reason(), operator.username()));
				Long movementId = movementId(SALES_RETURN_SOURCE, line.id());
				this.jdbcTemplate.update("""
						update sal_sales_return_line
						set stock_movement_id = ?, updated_at = ?
						where id = ?
						""", movementId, now, line.id());
				insertReversalLink(shipment, sourceLine, salesReturn, line, operator.username(), now);
			}
			Long adjustmentId = insertPostedReceivableAdjustment(salesReturn, receivable, totalAmount, operator.username(),
					now);
			applyReceivableAdjustment(receivable, totalAmount, operator.username(), now);
			this.jdbcTemplate.update("""
					update sal_sales_return
					set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					""", ReversalDocumentStatus.POSTED.name(), operator.username(), now, operator.username(), now, id);
			this.auditService.record(operator, "SALES_RETURN_POST", SALES_RETURN_TARGET, id, salesReturn.returnNo(),
					servletRequest);
			return salesReturn(id, operator).withSettlementAdjustmentId(adjustmentId);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public SalesReturnDetailResponse cancelSalesReturn(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		SalesReturnRow salesReturn = lockSalesReturn(id).orElseThrow(this::sourceNotFoundException);
		if (salesReturn.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(salesReturn);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_sales_return
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", ReversalDocumentStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "SALES_RETURN_CANCEL", SALES_RETURN_TARGET, id, salesReturn.returnNo(),
				servletRequest);
		return salesReturn(id, operator);
	}

	@Transactional(readOnly = true)
	public List<ReversalTraceRecord> traces(String sourceType, Long sourceId, Long sourceLineId, String direction,
			CurrentUser currentUser) {
		if (!hasText(sourceType) || sourceId == null) {
			return List.of();
		}
		List<TraceLinkRow> links = new ArrayList<>();
		if (!"REVERSE_TO_SOURCE".equals(direction)) {
			links.addAll(sourceTraceLinks(sourceType, sourceId, sourceLineId));
		}
		if (!"SOURCE_TO_REVERSE".equals(direction)) {
			links.addAll(reverseTraceLinks(sourceType, sourceId, sourceLineId));
		}
		return traceRecords(links, currentUser);
	}

	public Object sourceNotFound() {
		throw sourceNotFoundException();
	}

	private SalesReturnDetailResponse salesReturnDetail(SalesReturnRow row, CurrentUser currentUser) {
		boolean canViewSource = canViewSalesShipment(currentUser);
		List<ReversalDocumentLine> lines = salesReturnLines(row.id(), row.sourceShipmentId(), row.sourceShipmentNo(),
				canViewSource);
		List<ReversalTraceRecord> traces = traceRecords(reverseTraceLinks(SALES_RETURN_SOURCE, row.id(), null),
				currentUser);
		return new SalesReturnDetailResponse(row.id(), row.returnNo(), row.customerId(), row.customerName(),
				row.warehouseId(), row.warehouseName(), row.businessDate(), row.status().name(),
				quantity(totalQuantity(lines)), amount(row.totalAmount()), sourceView(SALES_SHIPMENT_SOURCE,
						row.sourceShipmentId(), null, row.sourceShipmentNo(), null, row.sourceBusinessDate(),
						row.sourceStatus(), null, null, canViewSource, "sales-shipment-detail",
						Map.of("id", row.sourceShipmentId()), null),
				row.createdAt(), row.updatedAt(), row.clientRequestId(), row.remark(), lines, traces);
	}

	private SalesReturnSourceResponse mapSalesReturnSource(ResultSet rs) throws SQLException {
		Long shipmentId = rs.getLong("id");
		List<SalesReturnSourceLineResponse> lines = salesReturnSourceLines(shipmentId);
		return new SalesReturnSourceResponse(shipmentId, rs.getString("shipment_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), lines);
	}

	private SalesReturnSummaryResponse mapSalesReturnSummary(ResultSet rs, CurrentUser currentUser) throws SQLException {
		boolean canViewSource = canViewSalesShipment(currentUser);
		Long sourceShipmentId = rs.getLong("source_shipment_id");
		return new SalesReturnSummaryResponse(rs.getLong("id"), rs.getString("return_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"),
				quantity(rs.getBigDecimal("total_quantity")), amount(rs.getBigDecimal("total_amount")),
				sourceView(SALES_SHIPMENT_SOURCE, sourceShipmentId, null, rs.getString("source_shipment_no"), null,
						rs.getObject("source_date", LocalDate.class), rs.getString("source_status"), null, null,
						canViewSource, "sales-shipment-detail", Map.of("id", sourceShipmentId), null),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private List<SalesReturnSourceLineResponse> salesReturnSourceLines(Long shipmentId) {
		return this.jdbcTemplate.query("""
				select sl.id, sl.order_line_id, sl.line_no, sl.material_id, m.code as material_code,
				       m.name as material_name, sl.unit_id, u.name as unit_name, sl.quantity,
				       coalesce((
				           select sum(rl.quantity)
				           from sal_sales_return_line rl
				           join sal_sales_return r on r.id = rl.return_id
				           where rl.source_shipment_line_id = sl.id
				           and r.status = 'POSTED'
				       ), 0) as returned_quantity,
				       ol.unit_price
				from sal_sales_shipment_line sl
				join sal_sales_order_line ol on ol.id = sl.order_line_id
				join mst_material m on m.id = sl.material_id
				join mst_unit u on u.id = sl.unit_id
				where sl.shipment_id = ?
				order by sl.line_no asc, sl.id asc
				""", (rs, rowNum) -> {
			BigDecimal shipped = rs.getBigDecimal("quantity");
			BigDecimal returned = rs.getBigDecimal("returned_quantity");
			BigDecimal returnable = shipped.subtract(returned);
			return new SalesReturnSourceLineResponse(rs.getLong("id"), rs.getLong("order_line_id"),
					rs.getInt("line_no"), rs.getLong("material_id"), rs.getString("material_code"),
					rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
					quantity(shipped), quantity(returned), quantity(returnable), quantity(rs.getBigDecimal("unit_price")),
					amount(returnable.multiply(rs.getBigDecimal("unit_price"))));
		}, shipmentId).stream().filter((line) -> new BigDecimal(line.returnableQuantity()).compareTo(ZERO) > 0)
			.toList();
	}

	private List<ReversalDocumentLine> salesReturnLines(Long returnId, Long sourceShipmentId, String sourceShipmentNo,
			boolean canViewSource) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.source_shipment_line_id, l.sales_order_line_id, l.material_id,
				       m.code as material_code, m.name as material_name, l.unit_id, u.name as unit_name,
				       l.returned_quantity_before, l.returnable_quantity_before, l.quantity, l.unit_price,
				       l.amount, l.reason, l.stock_movement_id, ssl.line_no as source_line_no,
				       sh.business_date as source_business_date, sh.status as source_status
				from sal_sales_return_line l
				join sal_sales_shipment_line ssl on ssl.id = l.source_shipment_line_id
				join sal_sales_shipment sh on sh.id = ssl.shipment_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.return_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> new ReversalDocumentLine(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("source_shipment_line_id"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				quantity(rs.getBigDecimal("returned_quantity_before")),
				quantity(rs.getBigDecimal("returnable_quantity_before")), quantity(rs.getBigDecimal("quantity")),
				quantity(rs.getBigDecimal("unit_price")), amount(rs.getBigDecimal("amount")), rs.getString("reason"),
				nullableLong(rs, "stock_movement_id"), null,
				sourceView(SALES_SHIPMENT_LINE_SOURCE, sourceShipmentId, rs.getLong("source_shipment_line_id"),
						sourceShipmentNo, rs.getInt("source_line_no"),
						rs.getObject("source_business_date", LocalDate.class), rs.getString("source_status"),
						quantity(rs.getBigDecimal("quantity")), amount(rs.getBigDecimal("amount")), canViewSource,
						"sales-shipment-detail", Map.of("id", sourceShipmentId),
						Map.of("lineId", rs.getLong("source_shipment_line_id")))),
				returnId);
	}

	private QueryParts salesReturnSourceQuery(String keyword, Long customerId, Long warehouseId, LocalDate dateFrom,
			LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("sh.status = 'POSTED'");
		conditions.add("""
				exists (
					select 1
					from sal_sales_shipment_line slx
					where slx.shipment_id = sh.id
					and slx.quantity > coalesce((
						select sum(rl.quantity)
						from sal_sales_return_line rl
						join sal_sales_return r on r.id = rl.return_id
						where rl.source_shipment_line_id = slx.id
						and r.status = 'POSTED'
					), 0)
				)
				""");
		if (hasText(keyword)) {
			conditions.add("""
					(sh.shipment_no ilike ? or c.name ilike ? or exists (
						select 1
						from sal_sales_shipment_line sl
						join mst_material m on m.id = sl.material_id
						where sl.shipment_id = sh.id
						and (m.code ilike ? or m.name ilike ?)
					))
					""");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (customerId != null) {
			conditions.add("sh.customer_id = ?");
			args.add(customerId);
		}
		if (warehouseId != null) {
			conditions.add("sh.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (dateFrom != null) {
			conditions.add("sh.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("sh.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private QueryParts salesReturnQuery(String keyword, Long customerId, Long warehouseId, String status,
			LocalDate dateFrom, LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(r.return_no ilike ? or r.source_shipment_no ilike ? or c.name ilike ? or r.remark ilike ?
					or exists (
						select 1
						from sal_sales_return_line rl
						join mst_material m on m.id = rl.material_id
						where rl.return_id = r.id
						and (m.code ilike ? or m.name ilike ?)
					))
					""");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (customerId != null) {
			conditions.add("r.customer_id = ?");
			args.add(customerId);
		}
		if (warehouseId != null) {
			conditions.add("r.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (hasText(status)) {
			conditions.add("r.status = ?");
			args.add(parseReversalStatus(status).name());
		}
		if (dateFrom != null) {
			conditions.add("r.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("r.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private ValidatedSalesReturn validateSalesReturnCreate(SalesReturnRequest request) {
		if (request == null || request.sourceShipmentId() == null || request.businessDate() == null
				|| request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
		}
		if (hasText(request.clientRequestId()) && request.clientRequestId().length() > 64) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.remark() != null && request.remark().length() > 500) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return new ValidatedSalesReturn(request.sourceShipmentId(), request.businessDate(),
				blankToNull(request.clientRequestId()), blankToNull(request.remark()), request.lines());
	}

	private List<ValidatedSalesReturnLine> validateSalesReturnLines(ShipmentRow shipment,
			List<SalesReturnLineRequest> requests) {
		Set<Long> sourceLineIds = new HashSet<>();
		List<ValidatedSalesReturnLine> lines = new ArrayList<>();
		int lineNo = 1;
		for (SalesReturnLineRequest request : requests) {
			if (request == null || request.sourceShipmentLineId() == null
					|| !sourceLineIds.add(request.sourceShipmentLineId())) {
				throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
			}
			BigDecimal quantity = validateQuantity(request.quantity());
			ShipmentLineRow sourceLine = shipmentLine(shipment.id(), request.sourceShipmentLineId())
				.orElseThrow(this::sourceNotFoundException);
			BigDecimal returnedQuantity = postedReturnedQuantity(sourceLine.id());
			BigDecimal returnableQuantity = sourceLine.quantity().subtract(returnedQuantity);
			if (quantity.compareTo(returnableQuantity) > 0) {
				throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_EXCEEDS_AVAILABLE);
			}
			String reason = request.reason();
			if (reason != null && reason.length() > 200) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			BigDecimal amount = money(quantity.multiply(sourceLine.unitPrice()));
			lines.add(new ValidatedSalesReturnLine(lineNo++, sourceLine.id(), sourceLine.orderLineId(),
					sourceLine.materialId(), sourceLine.unitId(), returnedQuantity, returnableQuantity, quantity,
					sourceLine.unitPrice(), amount, blankToNull(reason)));
		}
		return lines;
	}

	private void validateLineStillReturnable(ShipmentLineRow sourceLine, SalesReturnLineRow line) {
		if (!sourceLine.materialId().equals(line.materialId()) || !sourceLine.unitId().equals(line.unitId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		BigDecimal returnedQuantity = postedReturnedQuantity(sourceLine.id());
		BigDecimal returnableQuantity = sourceLine.quantity().subtract(returnedQuantity);
		if (line.quantity().compareTo(returnableQuantity) > 0) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_EXCEEDS_AVAILABLE);
		}
	}

	private BigDecimal validateQuantity(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) <= 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_INVALID);
		}
		return value;
	}

	private CreatedDocument insertSalesReturnWithRetry(ShipmentRow shipment, ValidatedSalesReturn salesReturn,
			BigDecimal totalAmount, String operator, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String returnNo = nextNo("SR", SALES_RETURN_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into sal_sales_return (
							return_no, customer_id, source_shipment_id, source_shipment_no, warehouse_id,
							business_date, status, total_amount, client_request_id, remark, created_by, created_at,
							updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, returnNo, shipment.customerId(), shipment.id(), shipment.shipmentNo(),
						shipment.warehouseId(), salesReturn.businessDate(), ReversalDocumentStatus.DRAFT.name(),
						totalAmount, blankToNull(salesReturn.clientRequestId()), blankToNull(salesReturn.remark()),
						operator, now, operator, now);
				return new CreatedDocument(id, returnNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_sal_sales_return_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertSalesReturnLines(Long returnId, List<ValidatedSalesReturnLine> lines, OffsetDateTime now) {
		for (ValidatedSalesReturnLine line : lines) {
			this.jdbcTemplate.update("""
					insert into sal_sales_return_line (
						return_id, source_shipment_line_id, sales_order_line_id, material_id, unit_id, line_no,
						returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount, reason,
						created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", returnId, line.sourceShipmentLineId(), line.salesOrderLineId(), line.materialId(),
					line.unitId(), line.lineNo(), line.returnedQuantityBefore(), line.returnableQuantityBefore(),
					line.quantity(), line.unitPrice(), line.amount(), blankToNull(line.reason()), now, now);
		}
	}

	private Long insertPostedReceivableAdjustment(SalesReturnRow salesReturn, ReceivableRow receivable,
			BigDecimal amount, String operator, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String adjustmentNo = nextNo("ADJ", SETTLEMENT_ADJUSTMENT_NO_SEQUENCE);
			try {
				return this.jdbcTemplate.queryForObject("""
						insert into fin_settlement_adjustment (
							adjustment_no, settlement_side, adjustment_type, source_type, source_id, target_id,
							business_date, amount, status, remark, created_by, created_at, updated_by, updated_at,
							posted_by, posted_at
						)
						values (?, 'RECEIVABLE', 'RETURN_OFFSET', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, adjustmentNo, SALES_RETURN_SOURCE, salesReturn.id(), receivable.id(),
						salesReturn.businessDate(), amount, ReversalDocumentStatus.POSTED.name(), "销售退货冲减应收",
						operator, now, operator, now, operator, now);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_fin_settlement_adjustment_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void applyReceivableAdjustment(ReceivableRow receivable, BigDecimal amount, String operator,
			OffsetDateTime now) {
		BigDecimal adjustedAmount = money(receivable.adjustedAmount().add(amount));
		BigDecimal unreceivedAmount = money(receivable.unreceivedAmount().subtract(amount));
		ReceivableStatus nextStatus = unreceivedAmount.compareTo(ZERO) == 0 ? ReceivableStatus.RECEIVED
				: ReceivableStatus.PARTIALLY_RECEIVED;
		this.jdbcTemplate.update("""
				update fin_receivable
				set adjusted_amount = ?, unreceived_amount = ?, status = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", adjustedAmount, unreceivedAmount, nextStatus.name(), operator, now, receivable.id());
	}

	private void insertReversalLink(ShipmentRow shipment, ShipmentLineRow sourceLine, SalesReturnRow salesReturn,
			SalesReturnLineRow returnLine, String operator, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				insert into biz_reversal_link (
					source_type, source_id, source_line_id, reverse_type, reverse_id, reverse_line_id, business_date,
					quantity, amount, created_by, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", SALES_SHIPMENT_SOURCE, shipment.id(), sourceLine.id(), SALES_RETURN_SOURCE, salesReturn.id(),
				returnLine.id(), salesReturn.businessDate(), returnLine.quantity(), returnLine.amount(), operator, now);
	}

	private Optional<SalesReturnRow> existingSalesReturn(Long sourceShipmentId, String clientRequestId) {
		if (!hasText(clientRequestId)) {
			return Optional.empty();
		}
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.customer_id, c.name as customer_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_shipment_id, r.source_shipment_no,
				       sh.business_date as source_business_date, sh.status as source_status, r.business_date,
				       r.status, r.total_amount, r.client_request_id, r.remark, r.created_at, r.updated_at
				from sal_sales_return r
				join mst_customer c on c.id = r.customer_id
				join mst_warehouse w on w.id = r.warehouse_id
				join sal_sales_shipment sh on sh.id = r.source_shipment_id
				where r.source_shipment_id = ?
				and r.client_request_id = ?
				""", this::mapSalesReturnRow, sourceShipmentId, clientRequestId).stream().findFirst();
	}

	private Optional<SalesReturnRow> salesReturnRow(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.customer_id, c.name as customer_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_shipment_id, r.source_shipment_no,
				       sh.business_date as source_business_date, sh.status as source_status, r.business_date,
				       r.status, r.total_amount, r.client_request_id, r.remark, r.created_at, r.updated_at
				from sal_sales_return r
				join mst_customer c on c.id = r.customer_id
				join mst_warehouse w on w.id = r.warehouse_id
				join sal_sales_shipment sh on sh.id = r.source_shipment_id
				where r.id = ?
				""", this::mapSalesReturnRow, id).stream().findFirst();
	}

	private Optional<SalesReturnRow> lockSalesReturn(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.customer_id, c.name as customer_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_shipment_id, r.source_shipment_no,
				       sh.business_date as source_business_date, sh.status as source_status, r.business_date,
				       r.status, r.total_amount, r.client_request_id, r.remark, r.created_at, r.updated_at
				from sal_sales_return r
				join mst_customer c on c.id = r.customer_id
				join mst_warehouse w on w.id = r.warehouse_id
				join sal_sales_shipment sh on sh.id = r.source_shipment_id
				where r.id = ?
				for update
				""", this::mapSalesReturnRow, id).stream().findFirst();
	}

	private List<SalesReturnLineRow> lockSalesReturnLines(Long returnId) {
		return this.jdbcTemplate.query("""
				select id, return_id, source_shipment_line_id, sales_order_line_id, material_id, unit_id, line_no,
				       returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount, reason,
				       stock_movement_id
				from sal_sales_return_line
				where return_id = ?
				order by line_no asc, id asc
				for update
				""", this::mapSalesReturnLineRow, returnId);
	}

	private Optional<ShipmentRow> lockShipment(Long id) {
		return this.jdbcTemplate.query("""
				select id, shipment_no, order_id, customer_id, warehouse_id, business_date, status
				from sal_sales_shipment
				where id = ?
				for update
				""", this::mapShipmentRow, id).stream().findFirst();
	}

	private Optional<ShipmentLineRow> shipmentLine(Long shipmentId, Long lineId) {
		return this.jdbcTemplate.query("""
				select sl.id, sl.shipment_id, sl.line_no, sl.order_line_id, sl.material_id, sl.unit_id,
				       sl.quantity, ol.unit_price
				from sal_sales_shipment_line sl
				join sal_sales_order_line ol on ol.id = sl.order_line_id
				where sl.shipment_id = ?
				and sl.id = ?
				""", this::mapShipmentLineRow, shipmentId, lineId).stream().findFirst();
	}

	private Optional<ShipmentLineRow> lockShipmentLine(Long shipmentId, Long lineId) {
		return this.jdbcTemplate.query("""
				select sl.id, sl.shipment_id, sl.line_no, sl.order_line_id, sl.material_id, sl.unit_id,
				       sl.quantity, ol.unit_price
				from sal_sales_shipment_line sl
				join sal_sales_order_line ol on ol.id = sl.order_line_id
				where sl.shipment_id = ?
				and sl.id = ?
				for update
				""", this::mapShipmentLineRow, shipmentId, lineId).stream().findFirst();
	}

	private Optional<ReceivableRow> lockReceivableForShipment(Long shipmentId) {
		return this.jdbcTemplate.query("""
				select id, receivable_no, source_id, total_amount, received_amount, adjusted_amount,
				       unreceived_amount, status
				from fin_receivable
				where source_type = ?
				and source_id = ?
				for update
				""", this::mapReceivableRow, SALES_SHIPMENT_SOURCE, shipmentId).stream().findFirst();
	}

	private BigDecimal postedReturnedQuantity(Long shipmentLineId) {
		BigDecimal returned = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(rl.quantity), 0)
				from sal_sales_return_line rl
				join sal_sales_return r on r.id = rl.return_id
				where rl.source_shipment_line_id = ?
				and r.status = ?
				""", BigDecimal.class, shipmentLineId, ReversalDocumentStatus.POSTED.name());
		return returned == null ? ZERO : returned;
	}

	private Long movementId(String sourceType, Long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""", Long.class, sourceType, sourceLineId);
	}

	private List<TraceLinkRow> reverseTraceLinks(String reverseType, Long reverseId, Long reverseLineId) {
		List<Object> args = new ArrayList<>();
		args.add(reverseType);
		args.add(reverseId);
		String condition = "";
		if (reverseLineId != null) {
			condition = "and bl.reverse_line_id = ?";
			args.add(reverseLineId);
		}
		return traceLinks("""
				where bl.reverse_type = ?
				and bl.reverse_id = ?
				%s
				""".formatted(condition), args);
	}

	private List<TraceLinkRow> sourceTraceLinks(String sourceType, Long sourceId, Long sourceLineId) {
		List<Object> args = new ArrayList<>();
		String normalizedType = SALES_SHIPMENT_LINE_SOURCE.equals(sourceType) ? SALES_SHIPMENT_SOURCE : sourceType;
		args.add(normalizedType);
		args.add(sourceId);
		String condition = "";
		if (sourceLineId != null) {
			condition = "and bl.source_line_id = ?";
			args.add(sourceLineId);
		}
		return traceLinks("""
				where bl.source_type = ?
				and bl.source_id = ?
				%s
				""".formatted(condition), args);
	}

	private List<TraceLinkRow> traceLinks(String where, List<Object> args) {
		return this.jdbcTemplate.query("""
				select bl.source_type, bl.source_id, bl.source_line_id, bl.reverse_type, bl.reverse_id,
				       bl.reverse_line_id, bl.business_date, bl.quantity, bl.amount,
				       sh.shipment_no, sh.business_date as shipment_date, sh.status as shipment_status,
				       ssl.line_no as shipment_line_no,
				       sr.return_no, sr.business_date as return_date, sr.status as return_status,
				       srl.line_no as return_line_no, srl.stock_movement_id,
				       fsa.id as settlement_adjustment_id
				from biz_reversal_link bl
				join sal_sales_shipment sh on sh.id = bl.source_id
				join sal_sales_shipment_line ssl on ssl.id = bl.source_line_id
				join sal_sales_return sr on sr.id = bl.reverse_id
				join sal_sales_return_line srl on srl.id = bl.reverse_line_id
				left join fin_settlement_adjustment fsa on fsa.source_type = 'SALES_RETURN'
					and fsa.source_id = sr.id
					and fsa.status = 'POSTED'
				%s
				order by bl.business_date asc, bl.id asc
				""".formatted(where), this::mapTraceLinkRow, args.toArray());
	}

	private List<ReversalTraceRecord> traceRecords(List<TraceLinkRow> links, CurrentUser currentUser) {
		boolean canViewShipment = canViewSalesShipment(currentUser);
		boolean canViewReturn = canViewSalesReturn(currentUser);
		return links.stream()
			.map((link) -> {
				Map<String, Object> source = sourceView(SALES_SHIPMENT_LINE_SOURCE, link.sourceId(),
						link.sourceLineId(), link.shipmentNo(), link.shipmentLineNo(), link.shipmentDate(),
						link.shipmentStatus(), quantity(link.quantity()), amount(link.amount()), canViewShipment,
						"sales-shipment-detail", Map.of("id", link.sourceId()), Map.of("lineId", link.sourceLineId()));
				Map<String, Object> reverse = sourceView(SALES_RETURN_SOURCE, link.reverseId(), link.reverseLineId(),
						link.returnNo(), link.returnLineNo(), link.returnDate(), link.returnStatus(),
						quantity(link.quantity()), amount(link.amount()), canViewReturn, "sales-return-detail",
						Map.of("id", link.reverseId()), Map.of("lineId", link.reverseLineId()));
				boolean restricted = !canViewShipment || !canViewReturn;
				return new ReversalTraceRecord(traceKey(link), "SOURCE_TO_REVERSE", source, reverse,
						link.stockMovementId(), link.settlementAdjustmentId(), null, link.businessDate(),
						quantity(link.quantity()), amount(link.amount()), link.returnStatus(), !restricted, restricted,
						restricted ? RESTRICTED_MESSAGE : null, canViewReturn ? "sales-return-detail" : null,
						canViewReturn ? Map.of("id", link.reverseId()) : null,
						canViewReturn ? Map.of("lineId", link.reverseLineId()) : null);
			})
			.toList();
	}

	private String traceKey(TraceLinkRow link) {
		return link.sourceType() + ":" + link.sourceId() + ":" + link.sourceLineId() + ":" + link.reverseType() + ":"
				+ link.reverseId() + ":" + link.reverseLineId();
	}

	private Map<String, Object> sourceView(String sourceType, Long sourceId, Long sourceLineId, String sourceNo,
			Integer lineNo, LocalDate businessDate, String status, String quantity, String amount, boolean canViewSource,
			String resourceRouteName, Map<String, Object> resourceRouteParams, Map<String, Object> resourceRouteQuery) {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("sourceType", sourceType);
		source.put("canViewSource", canViewSource);
		source.put("restricted", !canViewSource);
		if (!canViewSource) {
			source.put("restrictedMessage", RESTRICTED_MESSAGE);
			return source;
		}
		source.put("sourceId", sourceId);
		if (sourceLineId != null) {
			source.put("sourceLineId", sourceLineId);
		}
		source.put("sourceNo", sourceNo);
		if (lineNo != null) {
			source.put("lineNo", lineNo);
		}
		source.put("businessDate", businessDate);
		source.put("status", status);
		if (quantity != null) {
			source.put("quantity", quantity);
		}
		if (amount != null) {
			source.put("amount", amount);
		}
		source.put("resourceRouteName", resourceRouteName);
		if (resourceRouteParams != null) {
			source.put("resourceRouteParams", resourceRouteParams);
		}
		if (resourceRouteQuery != null) {
			source.put("resourceRouteQuery", resourceRouteQuery);
		}
		return source;
	}

	private SalesReturnRow mapSalesReturnRow(ResultSet rs, int rowNum) throws SQLException {
		return new SalesReturnRow(rs.getLong("id"), rs.getString("return_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getLong("source_shipment_id"), rs.getString("source_shipment_no"),
				rs.getObject("source_business_date", LocalDate.class), rs.getString("source_status"),
				rs.getObject("business_date", LocalDate.class), ReversalDocumentStatus.valueOf(rs.getString("status")),
				rs.getBigDecimal("total_amount"), rs.getString("client_request_id"), rs.getString("remark"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private SalesReturnLineRow mapSalesReturnLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new SalesReturnLineRow(rs.getLong("id"), rs.getLong("return_id"),
				rs.getLong("source_shipment_line_id"), nullableLong(rs, "sales_order_line_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getInt("line_no"),
				rs.getBigDecimal("returned_quantity_before"), rs.getBigDecimal("returnable_quantity_before"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"),
				rs.getString("reason"), nullableLong(rs, "stock_movement_id"));
	}

	private ShipmentRow mapShipmentRow(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentRow(rs.getLong("id"), rs.getString("shipment_no"), rs.getLong("order_id"),
				rs.getLong("customer_id"), rs.getLong("warehouse_id"), rs.getObject("business_date", LocalDate.class),
				rs.getString("status"));
	}

	private ShipmentLineRow mapShipmentLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentLineRow(rs.getLong("id"), rs.getLong("shipment_id"), rs.getInt("line_no"),
				rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"));
	}

	private ReceivableRow mapReceivableRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceivableRow(rs.getLong("id"), rs.getString("receivable_no"), rs.getLong("source_id"),
				rs.getBigDecimal("total_amount"), rs.getBigDecimal("received_amount"),
				rs.getBigDecimal("adjusted_amount"), rs.getBigDecimal("unreceived_amount"),
				ReceivableStatus.valueOf(rs.getString("status")));
	}

	private TraceLinkRow mapTraceLinkRow(ResultSet rs, int rowNum) throws SQLException {
		return new TraceLinkRow(rs.getString("source_type"), rs.getLong("source_id"), rs.getLong("source_line_id"),
				rs.getString("reverse_type"), rs.getLong("reverse_id"), rs.getLong("reverse_line_id"),
				rs.getObject("business_date", LocalDate.class), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("amount"), rs.getString("shipment_no"),
				rs.getObject("shipment_date", LocalDate.class), rs.getString("shipment_status"),
				rs.getInt("shipment_line_no"), rs.getString("return_no"), rs.getObject("return_date", LocalDate.class),
				rs.getString("return_status"), rs.getInt("return_line_no"), nullableLong(rs, "stock_movement_id"),
				nullableLong(rs, "settlement_adjustment_id"));
	}

	private void requireDraft(SalesReturnRow salesReturn) {
		if (salesReturn.status() != ReversalDocumentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.REVERSAL_STATUS_NOT_ALLOWED);
		}
	}

	private void requireAdjustableReceivable(ReceivableRow receivable) {
		if (receivable.status() != ReceivableStatus.CONFIRMED
				&& receivable.status() != ReceivableStatus.PARTIALLY_RECEIVED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
	}

	private ReversalDocumentStatus parseReversalStatus(String value) {
		try {
			return ReversalDocumentStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.REVERSAL_STATUS_NOT_ALLOWED);
		}
	}

	private BusinessException duplicateReversalException(DuplicateKeyException exception) {
		if (containsConstraint(exception, "uk_sal_sales_return_client_request")
				|| containsConstraint(exception, "uk_sal_sales_return_line_source")
				|| containsConstraint(exception, "uk_biz_reversal_link_reverse_line")
				|| containsConstraint(exception, "uk_biz_reversal_link_source_reverse")) {
			return new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		if (containsConstraint(exception, "uk_inv_stock_movement_source")) {
			return new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private BusinessException sourceNotFoundException() {
		return new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
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

	private String nextNo(String prefix, AtomicInteger sequence) {
		int value = Math.floorMod(sequence.getAndIncrement(), 1000);
		return prefix + "-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", value);
	}

	private BigDecimal totalAmount(List<ValidatedSalesReturnLine> lines) {
		return money(lines.stream().map(ValidatedSalesReturnLine::amount).reduce(ZERO, BigDecimal::add));
	}

	private BigDecimal totalAmountFromRows(List<SalesReturnLineRow> lines) {
		return money(lines.stream().map(SalesReturnLineRow::amount).reduce(ZERO, BigDecimal::add));
	}

	private BigDecimal totalQuantity(List<ReversalDocumentLine> lines) {
		return lines.stream().map((line) -> new BigDecimal(line.quantity())).reduce(ZERO, BigDecimal::add);
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private String amount(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private String quantity(BigDecimal value) {
		return value == null ? null : value.toPlainString();
	}

	private long integerDigits(BigDecimal value) {
		return Math.max(0L, (long) value.precision() - value.scale());
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private boolean canViewSalesShipment(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("sales:shipment:view");
	}

	private boolean canViewSalesReturn(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("sales:return:view");
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

	public record SalesReturnRequest(@NotNull Long sourceShipmentId, @NotNull LocalDate businessDate,
			String clientRequestId, String remark, @Valid List<SalesReturnLineRequest> lines) {
	}

	public record SalesReturnLineRequest(@NotNull Long sourceShipmentLineId, @NotNull BigDecimal quantity,
			String reason) {
	}

	public record SalesReturnSourceResponse(Long shipmentId, String shipmentNo, Long customerId, String customerName,
			Long warehouseId, String warehouseName, LocalDate businessDate, String status,
			List<SalesReturnSourceLineResponse> lines) {
	}

	public record SalesReturnSourceLineResponse(Long shipmentLineId, Long salesOrderLineId, Integer lineNo,
			Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			String shippedQuantity, String returnedQuantity, String returnableQuantity, String unitPrice,
			String returnableAmount) {
	}

	public record SalesReturnSummaryResponse(Long id, String returnNo, Long customerId, String customerName,
			Long warehouseId, String warehouseName, LocalDate businessDate, String status, String totalQuantity,
			String totalAmount, Map<String, Object> source, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record SalesReturnDetailResponse(Long id, String returnNo, Long customerId, String customerName,
			Long warehouseId, String warehouseName, LocalDate businessDate, String status, String totalQuantity,
			String totalAmount, Map<String, Object> source, OffsetDateTime createdAt, OffsetDateTime updatedAt,
			String clientRequestId, String remark, List<ReversalDocumentLine> lines,
			List<ReversalTraceRecord> traces) {

		private SalesReturnDetailResponse withSettlementAdjustmentId(Long settlementAdjustmentId) {
			if (settlementAdjustmentId == null || this.traces.isEmpty()) {
				return this;
			}
			List<ReversalTraceRecord> updatedTraces = this.traces.stream()
				.map((trace) -> new ReversalTraceRecord(trace.traceKey(), trace.direction(), trace.source(),
						trace.reverse(), trace.inventoryMovementId(), settlementAdjustmentId, trace.costRecordId(),
						trace.businessDate(), trace.quantity(), trace.amount(), trace.status(),
						trace.canViewResource(), trace.restricted(), trace.restrictedMessage(),
						trace.resourceRouteName(), trace.resourceRouteParams(), trace.resourceRouteQuery()))
				.toList();
			return new SalesReturnDetailResponse(this.id, this.returnNo, this.customerId, this.customerName,
					this.warehouseId, this.warehouseName, this.businessDate, this.status, this.totalQuantity,
					this.totalAmount, this.source, this.createdAt, this.updatedAt, this.clientRequestId, this.remark,
					this.lines, updatedTraces);
		}
	}

	public record ReversalDocumentLine(Long id, Integer lineNo, Long sourceLineId, Long materialId,
			String materialCode, String materialName, Long unitId, String unitName, String returnedQuantityBefore,
			String returnableQuantityBefore, String quantity, String unitPrice, String amount, String reason,
			Long stockMovementId, Long costRecordId, Map<String, Object> source) {
	}

	public record ReversalTraceRecord(String traceKey, String direction, Map<String, Object> source,
			Map<String, Object> reverse, Long inventoryMovementId, Long settlementAdjustmentId, Long costRecordId,
			LocalDate businessDate, String quantity, String amount, String status, boolean canViewResource,
			boolean restricted, String restrictedMessage, String resourceRouteName, Object resourceRouteParams,
			Object resourceRouteQuery) {
	}

	private record ValidatedSalesReturn(Long sourceShipmentId, LocalDate businessDate, String clientRequestId,
			String remark, List<SalesReturnLineRequest> lines) {
	}

	private record ValidatedSalesReturnLine(Integer lineNo, Long sourceShipmentLineId, Long salesOrderLineId,
			Long materialId, Long unitId, BigDecimal returnedQuantityBefore, BigDecimal returnableQuantityBefore,
			BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, String reason) {
	}

	private record SalesReturnRow(Long id, String returnNo, Long customerId, String customerName, Long warehouseId,
			String warehouseName, Long sourceShipmentId, String sourceShipmentNo, LocalDate sourceBusinessDate,
			String sourceStatus, LocalDate businessDate, ReversalDocumentStatus status, BigDecimal totalAmount,
			String clientRequestId, String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	private record SalesReturnLineRow(Long id, Long returnId, Long sourceShipmentLineId, Long salesOrderLineId,
			Long materialId, Long unitId, Integer lineNo, BigDecimal returnedQuantityBefore,
			BigDecimal returnableQuantityBefore, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount,
			String reason, Long stockMovementId) {
	}

	private record ShipmentRow(Long id, String shipmentNo, Long orderId, Long customerId, Long warehouseId,
			LocalDate businessDate, String status) {
	}

	private record ShipmentLineRow(Long id, Long shipmentId, Integer lineNo, Long orderLineId, Long materialId,
			Long unitId, BigDecimal quantity, BigDecimal unitPrice) {
	}

	private record ReceivableRow(Long id, String receivableNo, Long sourceId, BigDecimal totalAmount,
			BigDecimal receivedAmount, BigDecimal adjustedAmount, BigDecimal unreceivedAmount,
			ReceivableStatus status) {
	}

	private record TraceLinkRow(String sourceType, Long sourceId, Long sourceLineId, String reverseType,
			Long reverseId, Long reverseLineId, LocalDate businessDate, BigDecimal quantity, BigDecimal amount,
			String shipmentNo, LocalDate shipmentDate, String shipmentStatus, Integer shipmentLineNo, String returnNo,
			LocalDate returnDate, String returnStatus, Integer returnLineNo, Long stockMovementId,
			Long settlementAdjustmentId) {
	}

	private record CreatedDocument(Long id, String documentNo) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
