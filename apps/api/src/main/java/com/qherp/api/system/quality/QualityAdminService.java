package com.qherp.api.system.quality;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.inventory.InventoryAvailabilityService;
import com.qherp.api.system.inventory.InventoryQualityStatus;
import com.qherp.api.system.inventory.InventoryPostingService;
import com.qherp.api.system.inventory.InventoryTrackingMethod;
import com.qherp.api.system.inventory.InventoryTrackingService;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class QualityAdminService {

	private static final String QUALITY_INSPECTION_SOURCE = "QUALITY_INSPECTION";

	private static final String QUALITY_STATUS_TRANSFER_SOURCE = "QUALITY_STATUS_TRANSFER";

	private static final String QUALITY_INSPECTION_TARGET = "QUALITY_INSPECTION";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger INSPECTION_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final InventoryPostingService inventoryPostingService;

	private final InventoryAvailabilityService inventoryAvailabilityService;

	private final BusinessPeriodGuard businessPeriodGuard;

	private final AuditService auditService;

	private final InventoryTrackingService inventoryTrackingService;

	public QualityAdminService(JdbcTemplate jdbcTemplate, InventoryPostingService inventoryPostingService,
			InventoryAvailabilityService inventoryAvailabilityService, BusinessPeriodGuard businessPeriodGuard,
			AuditService auditService, InventoryTrackingService inventoryTrackingService) {
		this.jdbcTemplate = jdbcTemplate;
		this.inventoryPostingService = inventoryPostingService;
		this.inventoryAvailabilityService = inventoryAvailabilityService;
		this.businessPeriodGuard = businessPeriodGuard;
		this.auditService = auditService;
		this.inventoryTrackingService = inventoryTrackingService;
	}

	@Transactional(readOnly = true)
	public PageResponse<QualityInspectionResponse> inspections(String keyword, String sourceType, String status,
			String qualityStatus, Long warehouseId, Long materialId, LocalDate dateFrom, LocalDate dateTo, int page,
			int pageSize) {
		ValidatedInspectionFilters filters = validateFilters(sourceType, status, qualityStatus, dateFrom, dateTo);
		QueryParts queryParts = inspectionQueryParts(keyword, filters, warehouseId, materialId);
		long total = this.jdbcTemplate.queryForObject("select count(*) from qua_quality_inspection qi "
				+ queryParts.where(), Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<QualityInspectionResponse> items = this.jdbcTemplate.query("""
				select qi.id, qi.inspection_no, qi.source_type, qi.source_id, qi.source_line_id,
				       qi.warehouse_id, w.code as warehouse_code, w.name as warehouse_name,
				       qi.material_id, m.code as material_code, m.name as material_name,
				       qi.unit_id, u.name as unit_name, qi.business_date, qi.inspection_quantity,
				       qi.qualified_quantity, qi.rejected_quantity, qi.frozen_quantity, qi.status,
				       qi.reason, qi.remark, qi.created_by, qi.created_at, qi.updated_by, qi.updated_at,
				       qi.completed_by, qi.completed_at, qi.version
				from qua_quality_inspection qi
				join mst_warehouse w on w.id = qi.warehouse_id
				join mst_material m on m.id = qi.material_id
				join mst_unit u on u.id = qi.unit_id
				%s
				order by qi.updated_at desc, qi.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapInspectionResponse, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public QualityInspectionResponse inspection(Long id) {
		return this.jdbcTemplate.query("""
				select qi.id, qi.inspection_no, qi.source_type, qi.source_id, qi.source_line_id,
				       qi.warehouse_id, w.code as warehouse_code, w.name as warehouse_name,
				       qi.material_id, m.code as material_code, m.name as material_name,
				       qi.unit_id, u.name as unit_name, qi.business_date, qi.inspection_quantity,
				       qi.qualified_quantity, qi.rejected_quantity, qi.frozen_quantity, qi.status,
				       qi.reason, qi.remark, qi.created_by, qi.created_at, qi.updated_by, qi.updated_at,
				       qi.completed_by, qi.completed_at, qi.version
				from qua_quality_inspection qi
				join mst_warehouse w on w.id = qi.warehouse_id
				join mst_material m on m.id = qi.material_id
				join mst_unit u on u.id = qi.unit_id
				where qi.id = ?
				""", this::mapInspectionResponse, id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.QUALITY_INSPECTION_NOT_FOUND));
	}

	@Transactional
	public QualityInspectionResponse processInspection(Long id, QualityInspectionProcessRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		InspectionRow inspection = lockInspection(id)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.QUALITY_INSPECTION_NOT_FOUND));
		LocalDate processBusinessDate = request == null ? null : request.businessDate();
		this.businessPeriodGuard.assertWritable(processBusinessDate, BusinessPeriodOperation.UPDATE,
				QUALITY_INSPECTION_SOURCE, inspection.id());
		if (inspection.status() != QualityInspectionStatus.PENDING) {
			throw new BusinessException(ApiErrorCode.QUALITY_INSPECTION_STATUS_INVALID);
		}
		if (request == null || !hasText(request.reason())) {
			throw new BusinessException(ApiErrorCode.QUALITY_STATUS_REASON_REQUIRED);
		}
		BigDecimal qualifiedQuantity = parseNonNegativeQuantity(request.qualifiedQuantity());
		BigDecimal rejectedQuantity = parseNonNegativeQuantity(request.rejectedQuantity());
		BigDecimal frozenQuantity = parseNonNegativeQuantity(request.frozenQuantity());
		BigDecimal total = qualifiedQuantity.add(rejectedQuantity).add(frozenQuantity);
		if (total.compareTo(inspection.inspectionQuantity()) != 0) {
			throw new BusinessException(ApiErrorCode.QUALITY_INSPECTION_QUANTITY_MISMATCH);
		}
		String reason = validateText(request.reason(), 200, ApiErrorCode.QUALITY_STATUS_REASON_REQUIRED);
		String remark = validateOptionalText(request.remark(), 500);
		if (shouldUseTracking(inspection.materialId(), request.trackingAllocations())) {
			List<InventoryTrackingService.ResolvedTrackingAllocation> trackingAllocations = this.inventoryTrackingService
				.resolveQualityAllocations(inspection.warehouseId(), inspection.materialId(), inspection.unitId(),
						InventoryQualityStatus.PENDING_INSPECTION, total, request.trackingAllocations());
			assertTrackedQualityQuantity(trackingAllocations, InventoryQualityStatus.QUALIFIED, qualifiedQuantity);
			assertTrackedQualityQuantity(trackingAllocations, InventoryQualityStatus.REJECTED, rejectedQuantity);
			assertTrackedQualityQuantity(trackingAllocations, InventoryQualityStatus.FROZEN, frozenQuantity);
			transferTrackedFromPending(inspection, trackingAllocations, processBusinessDate, reason, remark,
					operator.username());
		}
		else {
			transferFromPending(inspection, InventoryQualityStatus.QUALIFIED, qualifiedQuantity, processBusinessDate, reason,
					remark, operator.username());
			transferFromPending(inspection, InventoryQualityStatus.REJECTED, rejectedQuantity, processBusinessDate, reason,
					remark, operator.username());
			transferFromPending(inspection, InventoryQualityStatus.FROZEN, frozenQuantity, processBusinessDate, reason,
					remark, operator.username());
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update qua_quality_inspection
				set status = ?, qualified_quantity = ?, rejected_quantity = ?, frozen_quantity = ?,
				    reason = ?, remark = ?, completed_by = ?, completed_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", QualityInspectionStatus.COMPLETED.name(), qualifiedQuantity, rejectedQuantity, frozenQuantity,
				reason, remark, operator.username(), now, operator.username(), now, inspection.id());
		this.auditService.record(operator, "QUALITY_INSPECTION_PROCESS", QUALITY_INSPECTION_TARGET, inspection.id(),
				inspection.inspectionNo(), servletRequest);
		return inspection(id);
	}

	@Transactional
	public QualityStatusTransferResponse freeze(QualityStatusTransferRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return transferQualityStatus(request, InventoryQualityStatus.QUALIFIED, InventoryQualityStatus.FROZEN,
				"QUALITY_STATUS_FREEZE", "质量冻结", operator, servletRequest);
	}

	@Transactional
	public QualityStatusTransferResponse unfreeze(QualityStatusTransferRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return transferQualityStatus(request, InventoryQualityStatus.FROZEN, InventoryQualityStatus.QUALIFIED,
				"QUALITY_STATUS_UNFREEZE", "质量解冻", operator, servletRequest);
	}

	@Transactional
	public Long createPendingInspection(QualityInspectionSourceType sourceType, Long sourceId, Long sourceLineId,
			Long warehouseId, Long materialId, Long unitId, LocalDate businessDate, BigDecimal quantity,
			String operatorName) {
		if (sourceType == null || sourceId == null || sourceLineId == null || warehouseId == null || materialId == null
				|| unitId == null || businessDate == null || quantity == null || quantity.compareTo(ZERO) <= 0
				|| !hasText(operatorName)) {
			throw new BusinessException(ApiErrorCode.QUALITY_INSPECTION_SOURCE_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			return this.jdbcTemplate.queryForObject("""
					insert into qua_quality_inspection (
						inspection_no, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id,
						business_date, inspection_quantity, status, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, nextInspectionNo(sourceType, sourceLineId), sourceType.name(), sourceId, sourceLineId,
					warehouseId, materialId, unitId, businessDate, quantity, QualityInspectionStatus.PENDING.name(),
					operatorName, now, operatorName, now);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.QUALITY_INSPECTION_SOURCE_INVALID);
		}
	}

	private QualityStatusTransferResponse transferQualityStatus(QualityStatusTransferRequest request,
			InventoryQualityStatus fromStatus, InventoryQualityStatus toStatus, String auditAction, String defaultReason,
			CurrentUser operator, HttpServletRequest servletRequest) {
		if (request == null || request.warehouseId() == null || request.materialId() == null || request.unitId() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		this.businessPeriodGuard.assertWritable(request.businessDate(), BusinessPeriodOperation.UPDATE,
				QUALITY_STATUS_TRANSFER_SOURCE, null);
		if (!hasText(request.reason())) {
			throw new BusinessException(ApiErrorCode.QUALITY_STATUS_REASON_REQUIRED);
		}
		BigDecimal quantity = parsePositiveQuantity(request.quantity());
		String reason = validateText(request.reason(), 200, ApiErrorCode.QUALITY_STATUS_REASON_REQUIRED);
		String remark = validateOptionalText(request.remark(), 500);
		boolean useTracking = shouldUseTracking(request.materialId(), request.trackingAllocations());
		if (fromStatus == InventoryQualityStatus.QUALIFIED && toStatus == InventoryQualityStatus.FROZEN) {
			if (useTracking) {
				assertTrackedFreezeAvailable(request.warehouseId(), request.materialId(), quantity);
			}
			else {
				this.inventoryAvailabilityService.assertQualifiedAvailable(request.warehouseId(), request.materialId(),
						quantity, ApiErrorCode.INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE);
			}
		}
		long sourceId = nextQualityStatusTransferSourceId();
		if (useTracking) {
			List<InventoryTrackingService.ResolvedTrackingAllocation> trackingAllocations = this.inventoryTrackingService
				.resolveQualityAllocations(request.warehouseId(), request.materialId(), request.unitId(), fromStatus,
						quantity, request.trackingAllocations(), toStatus, "trackingAllocations");
			assertTrackedQualityQuantity(trackingAllocations, toStatus, quantity);
			TrackedTransferSummary trackedResult = transferTrackedQualityStatus(request.warehouseId(),
					request.materialId(), request.unitId(), fromStatus, toStatus, trackingAllocations, sourceId,
					request.businessDate(), hasText(reason) ? reason : defaultReason, remark, operator.username());
			this.auditService.record(operator, auditAction, QUALITY_STATUS_TRANSFER_SOURCE, sourceId, reason,
					servletRequest);
			return new QualityStatusTransferResponse(fromStatus.name(), fromStatus.displayName(), toStatus.name(),
					toStatus.displayName(), formatQuantity(quantity), formatQuantity(trackedResult.fromBeforeQuantity()),
					formatQuantity(trackedResult.fromAfterQuantity()), formatQuantity(trackedResult.toBeforeQuantity()),
					formatQuantity(trackedResult.toAfterQuantity()), trackedResult.trackingAllocations());
		}
		InventoryPostingService.QualityTransferResult result = this.inventoryPostingService.transferQualityStatus(
				request.warehouseId(), request.materialId(), request.unitId(), fromStatus, toStatus, quantity,
				QUALITY_STATUS_TRANSFER_SOURCE, sourceId, sourceId, request.businessDate(),
				hasText(reason) ? reason : defaultReason, remark, operator.username());
		this.auditService.record(operator, auditAction, QUALITY_STATUS_TRANSFER_SOURCE, sourceId, reason,
				servletRequest);
		return new QualityStatusTransferResponse(fromStatus.name(), fromStatus.displayName(), toStatus.name(),
				toStatus.displayName(), formatQuantity(quantity), formatQuantity(result.fromBeforeQuantity()),
				formatQuantity(result.fromAfterQuantity()), formatQuantity(result.toBeforeQuantity()),
				formatQuantity(result.toAfterQuantity()), List.of());
	}

	private void transferFromPending(InspectionRow inspection, InventoryQualityStatus targetStatus, BigDecimal quantity,
			LocalDate businessDate, String reason, String remark, String operatorName) {
		if (quantity.compareTo(ZERO) == 0) {
			return;
		}
		long sourceLineId = Math.addExact(Math.multiplyExact(inspection.id(), 100L), targetStatus.ordinal());
		this.inventoryPostingService.transferQualityStatus(inspection.warehouseId(), inspection.materialId(),
				inspection.unitId(), InventoryQualityStatus.PENDING_INSPECTION, targetStatus, quantity,
				QUALITY_INSPECTION_SOURCE, inspection.id(), sourceLineId, businessDate, reason, remark,
				operatorName);
	}

	private void transferTrackedFromPending(InspectionRow inspection,
			List<InventoryTrackingService.ResolvedTrackingAllocation> trackingAllocations, LocalDate businessDate,
			String reason, String remark, String operatorName) {
		int index = 1;
		for (InventoryTrackingService.ResolvedTrackingAllocation allocation : trackingAllocations) {
			long sourceLineId = trackedSourceLineId(inspection.id(), index++);
			InventoryPostingService.QualityTransferResult result = this.inventoryPostingService.transferQualityStatus(
					inspection.warehouseId(), inspection.materialId(), inspection.unitId(),
					InventoryQualityStatus.PENDING_INSPECTION, allocation.qualityStatus(), allocation.quantity(),
					QUALITY_INSPECTION_SOURCE, inspection.id(), sourceLineId, businessDate, reason, remark,
					operatorName, allocation.batchId(), allocation.serialId());
			this.inventoryTrackingService.recordQualityAllocation(QUALITY_INSPECTION_SOURCE, inspection.id(),
					sourceLineId, inspection.warehouseId(), inspection.materialId(), inspection.unitId(),
					allocation.qualityStatus(), allocation, result.toMovementId(), operatorName);
			this.inventoryTrackingService.markSerialQuality(allocation, inspection.warehouseId(),
					allocation.qualityStatus(), result.toMovementId(), operatorName);
		}
	}

	private TrackedTransferSummary transferTrackedQualityStatus(Long warehouseId, Long materialId, Long unitId,
			InventoryQualityStatus fromStatus, InventoryQualityStatus toStatus,
			List<InventoryTrackingService.ResolvedTrackingAllocation> trackingAllocations, Long sourceId,
			LocalDate businessDate, String reason, String remark, String operatorName) {
		BigDecimal fromBeforeQuantity = ZERO;
		BigDecimal fromAfterQuantity = ZERO;
		BigDecimal toBeforeQuantity = ZERO;
		BigDecimal toAfterQuantity = ZERO;
		List<InventoryTrackingService.TrackingAllocationResponse> responses = new ArrayList<>();
		InventoryTrackingMethod trackingMethod = this.inventoryTrackingService.trackingMethod(materialId);
		int index = 1;
		for (InventoryTrackingService.ResolvedTrackingAllocation allocation : trackingAllocations) {
			long sourceLineId = trackedSourceLineId(sourceId, index++);
			InventoryPostingService.QualityTransferResult result = this.inventoryPostingService.transferQualityStatus(
					warehouseId, materialId, unitId, fromStatus, toStatus, allocation.quantity(),
					QUALITY_STATUS_TRANSFER_SOURCE, sourceId, sourceLineId, businessDate, reason, remark,
					operatorName, allocation.batchId(), allocation.serialId());
			Long allocationId = this.inventoryTrackingService.recordQualityAllocation(QUALITY_STATUS_TRANSFER_SOURCE, sourceId,
					sourceLineId, warehouseId, materialId, unitId, toStatus, allocation, result.toMovementId(),
					operatorName);
			this.inventoryTrackingService.markSerialQuality(allocation, warehouseId, toStatus, result.toMovementId(),
					operatorName);
			fromBeforeQuantity = fromBeforeQuantity.add(result.fromBeforeQuantity());
			fromAfterQuantity = fromAfterQuantity.add(result.fromAfterQuantity());
			toBeforeQuantity = toBeforeQuantity.add(result.toBeforeQuantity());
			toAfterQuantity = toAfterQuantity.add(result.toAfterQuantity());
			responses.add(trackingResponse(allocation, toStatus, result.toMovementId(), allocationId, trackingMethod,
					QUALITY_STATUS_TRANSFER_SOURCE, sourceId, sourceLineId));
		}
		return new TrackedTransferSummary(fromBeforeQuantity, fromAfterQuantity, toBeforeQuantity, toAfterQuantity,
				responses);
	}

	private void assertTrackedQualityQuantity(List<InventoryTrackingService.ResolvedTrackingAllocation> allocations,
			InventoryQualityStatus qualityStatus, BigDecimal expectedQuantity) {
		BigDecimal actualQuantity = allocations.stream()
			.filter((allocation) -> allocation.qualityStatus() == qualityStatus)
			.map(InventoryTrackingService.ResolvedTrackingAllocation::quantity)
			.reduce(ZERO, BigDecimal::add);
		if (actualQuantity.compareTo(expectedQuantity) != 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_TRACKING_QUANTITY_MISMATCH);
		}
	}

	private void assertTrackedFreezeAvailable(Long warehouseId, Long materialId, BigDecimal quantity) {
		BigDecimal quantityOnHand = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				""", BigDecimal.class, warehouseId, materialId);
		BigDecimal lockedQuantity = this.jdbcTemplate.query("""
				select quantity, released_quantity, consumed_quantity
				from inv_stock_reservation
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and status = 'ACTIVE'
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity")
				.subtract(rs.getBigDecimal("released_quantity"))
				.subtract(rs.getBigDecimal("consumed_quantity")), warehouseId, materialId)
			.stream()
			.reduce(ZERO, BigDecimal::add);
		BigDecimal availableQuantity = (quantityOnHand == null ? ZERO : quantityOnHand).subtract(lockedQuantity);
		if (availableQuantity.compareTo(quantity) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_TRACKING_NOT_AVAILABLE);
		}
	}

	private boolean shouldUseTracking(Long materialId,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
		if (trackingAllocations != null && !trackingAllocations.isEmpty()) {
			return true;
		}
		return this.inventoryTrackingService.trackingMethod(materialId) != InventoryTrackingMethod.NONE;
	}

	private long trackedSourceLineId(Long sourceId, int index) {
		return Math.addExact(Math.multiplyExact(sourceId, 1000L), index);
	}

	private InventoryTrackingService.TrackingAllocationResponse trackingResponse(
			InventoryTrackingService.ResolvedTrackingAllocation allocation, InventoryQualityStatus qualityStatus,
			Long movementId, Long allocationId, InventoryTrackingMethod trackingMethod, String documentType,
			Long documentId, Long documentLineId) {
		return new InventoryTrackingService.TrackingAllocationResponse(allocationId, trackingMethod.name(),
				trackingMethod.displayName(), allocation.batchId(), allocation.batchNo(), allocation.serialId(),
				allocation.serialNo(), allocation.quantity(), qualityStatus.name(), qualityStatus.displayName(),
				movementId, documentType, documentId, documentLineId, documentType, documentId, documentLineId,
				documentType + "-" + documentId, null);
	}

	private long nextQualityStatusTransferSourceId() {
		Long sourceId = this.jdbcTemplate.queryForObject("select nextval('qua_quality_status_transfer_source_seq')",
				Long.class);
		if (sourceId == null) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
		return sourceId;
	}

	private Optional<InspectionRow> lockInspection(Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return this.jdbcTemplate.query("""
				select id, inspection_no, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id,
				       business_date, inspection_quantity, status
				from qua_quality_inspection
				where id = ?
				for update
				""", this::mapInspectionRow, id).stream().findFirst();
	}

	private InspectionRow mapInspectionRow(ResultSet rs, int rowNum) throws SQLException {
		return new InspectionRow(rs.getLong("id"), rs.getString("inspection_no"), rs.getString("source_type"),
				rs.getLong("source_id"), rs.getLong("source_line_id"), rs.getLong("warehouse_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getObject("business_date", LocalDate.class),
				rs.getBigDecimal("inspection_quantity"), QualityInspectionStatus.valueOf(rs.getString("status")));
	}

	private QualityInspectionResponse mapInspectionResponse(ResultSet rs, int rowNum) throws SQLException {
		String sourceType = rs.getString("source_type");
		QualityInspectionSourceType parsedSourceType = QualityInspectionSourceType.from(sourceType)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.QUALITY_INSPECTION_SOURCE_INVALID));
		QualityInspectionStatus status = QualityInspectionStatus.valueOf(rs.getString("status"));
		BigDecimal inspectionQuantity = rs.getBigDecimal("inspection_quantity");
		BigDecimal qualifiedQuantity = rs.getBigDecimal("qualified_quantity");
		BigDecimal rejectedQuantity = rs.getBigDecimal("rejected_quantity");
		BigDecimal frozenQuantity = rs.getBigDecimal("frozen_quantity");
		BigDecimal processedQuantity = qualifiedQuantity.add(rejectedQuantity).add(frozenQuantity);
		BigDecimal remainingQuantity = status == QualityInspectionStatus.PENDING
				? inspectionQuantity.subtract(processedQuantity) : ZERO;
		Long sourceId = rs.getLong("source_id");
		Long sourceLineId = rs.getLong("source_line_id");
		return new QualityInspectionResponse(rs.getLong("id"), rs.getString("inspection_no"), sourceType,
				parsedSourceType.displayName(), sourceId, sourceLineId, sourceDocumentNo(sourceType, sourceId),
				rs.getLong("warehouse_id"), rs.getString("warehouse_code"), rs.getString("warehouse_name"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				rs.getLong("unit_id"), rs.getString("unit_name"), rs.getObject("business_date", LocalDate.class),
				formatQuantity(inspectionQuantity), InventoryQualityStatus.PENDING_INSPECTION.name(),
				InventoryQualityStatus.PENDING_INSPECTION.displayName(),
				InventoryQualityStatus.PENDING_INSPECTION.name(),
				InventoryQualityStatus.PENDING_INSPECTION.displayName(), status.name(), status.displayName(),
				formatQuantity(qualifiedQuantity), formatQuantity(rejectedQuantity), formatQuantity(frozenQuantity),
				formatQuantity(remainingQuantity), status == QualityInspectionStatus.PENDING,
				status == QualityInspectionStatus.PENDING ? null : "当前质量确认已处理", rs.getString("reason"),
				rs.getString("remark"), rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("completed_by"), rs.getObject("completed_at", OffsetDateTime.class), rs.getInt("version"),
				this.inventoryTrackingService.allocationResponses(QUALITY_INSPECTION_SOURCE, rs.getLong("id")));
	}

	private String sourceDocumentNo(String sourceType, Long sourceId) {
		try {
			return switch (sourceType) {
				case "PURCHASE_RECEIPT" -> this.jdbcTemplate.queryForObject(
						"select receipt_no from proc_purchase_receipt where id = ?", String.class, sourceId);
				case "PRODUCTION_COMPLETION" -> this.jdbcTemplate.queryForObject(
						"select receipt_no from mfg_completion_receipt where id = ?", String.class, sourceId);
				case "SALES_RETURN" -> this.jdbcTemplate.queryForObject(
						"select return_no from sal_sales_return where id = ?", String.class, sourceId);
				case "PRODUCTION_RETURN" -> this.jdbcTemplate.queryForObject(
						"select return_no from mfg_material_return where id = ?", String.class, sourceId);
				default -> null;
			};
		}
		catch (EmptyResultDataAccessException exception) {
			return null;
		}
	}

	private ValidatedInspectionFilters validateFilters(String sourceType, String status, String qualityStatus,
			LocalDate dateFrom, LocalDate dateTo) {
		QualityInspectionSourceType parsedSourceType = null;
		if (hasText(sourceType)) {
			parsedSourceType = QualityInspectionSourceType.from(sourceType)
				.orElseThrow(() -> new BusinessException(ApiErrorCode.QUALITY_INSPECTION_SOURCE_INVALID));
		}
		QualityInspectionStatus parsedStatus = null;
		if (hasText(status)) {
			try {
				parsedStatus = QualityInspectionStatus.valueOf(status);
			}
			catch (IllegalArgumentException exception) {
				throw new BusinessException(ApiErrorCode.QUALITY_INSPECTION_STATUS_INVALID);
			}
		}
		if (hasText(qualityStatus) && InventoryQualityStatus.PENDING_INSPECTION != qualityStatus(qualityStatus)) {
			return new ValidatedInspectionFilters(parsedSourceType, parsedStatus, InventoryQualityStatus.valueOf(qualityStatus),
					dateFrom, dateTo);
		}
		if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return new ValidatedInspectionFilters(parsedSourceType, parsedStatus,
				hasText(qualityStatus) ? InventoryQualityStatus.PENDING_INSPECTION : null, dateFrom, dateTo);
	}

	private QueryParts inspectionQueryParts(String keyword, ValidatedInspectionFilters filters, Long warehouseId,
			Long materialId) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(qi.inspection_no ilike ? or cast(qi.source_id as varchar) ilike ?)");
			args.add("%" + keyword + "%");
			args.add("%" + keyword + "%");
		}
		if (filters.sourceType() != null) {
			conditions.add("qi.source_type = ?");
			args.add(filters.sourceType().name());
		}
		if (filters.status() != null) {
			conditions.add("qi.status = ?");
			args.add(filters.status().name());
		}
		if (filters.qualityStatus() != null && filters.qualityStatus() != InventoryQualityStatus.PENDING_INSPECTION) {
			conditions.add("1 = 0");
		}
		if (warehouseId != null) {
			conditions.add("qi.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (materialId != null) {
			conditions.add("qi.material_id = ?");
			args.add(materialId);
		}
		if (filters.dateFrom() != null) {
			conditions.add("qi.business_date >= ?");
			args.add(filters.dateFrom());
		}
		if (filters.dateTo() != null) {
			conditions.add("qi.business_date <= ?");
			args.add(filters.dateTo());
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args);
	}

	private InventoryQualityStatus qualityStatus(String value) {
		try {
			return InventoryQualityStatus.valueOf(value);
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUALITY_STATUS_INVALID);
		}
	}

	private BigDecimal parsePositiveQuantity(String value) {
		BigDecimal quantity = parseQuantity(value);
		if (quantity.compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUANTITY_INVALID);
		}
		return quantity;
	}

	private BigDecimal parseNonNegativeQuantity(String value) {
		BigDecimal quantity = parseQuantity(value);
		if (quantity.compareTo(ZERO) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUANTITY_INVALID);
		}
		return quantity;
	}

	private BigDecimal parseQuantity(String value) {
		if (!hasText(value)) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUANTITY_INVALID);
		}
		try {
			BigDecimal quantity = new BigDecimal(value);
			if (quantity.scale() > 6 || quantity.precision() - quantity.scale() > 12) {
				throw new BusinessException(ApiErrorCode.INVENTORY_QUANTITY_INVALID);
			}
			return quantity.setScale(6, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException | NumberFormatException exception) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUANTITY_INVALID);
		}
	}

	private String validateText(String value, int maxLength, ApiErrorCode blankErrorCode) {
		if (!hasText(value)) {
			throw new BusinessException(blankErrorCode);
		}
		if (value.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value;
	}

	private String validateOptionalText(String value, int maxLength) {
		if (!hasText(value)) {
			return null;
		}
		if (value.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value;
	}

	private String nextInspectionNo(QualityInspectionSourceType sourceType, Long sourceLineId) {
		int sequence = Math.floorMod(INSPECTION_NO_SEQUENCE.getAndIncrement(), 1000);
		return "QI-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + sourceType.name() + "-" + sourceLineId
				+ "-" + String.format("%03d", sequence);
	}

	private static String formatQuantity(BigDecimal value) {
		return (value == null ? ZERO : value).setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record QualityInspectionProcessRequest(@NotNull LocalDate businessDate, String qualifiedQuantity,
			String rejectedQuantity, String frozenQuantity, String reason, String remark,
			@Valid List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	public record QualityStatusTransferRequest(Long warehouseId, Long materialId, Long unitId,
			@NotNull LocalDate businessDate, String quantity, String reason, String remark,
			@Valid List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	public record QualityInspectionResponse(Long id, String inspectionNo, String sourceType, String sourceTypeName,
			Long sourceId, Long sourceLineId, String sourceDocumentNo, Long warehouseId, String warehouseCode,
			String warehouseName, Long materialId, String materialCode, String materialName, Long unitId,
			String unitName, LocalDate businessDate, String inspectionQuantity, String qualityStatus,
			String qualityStatusName, String currentQualityStatus, String currentQualityStatusName, String status,
			String statusName, String qualifiedQuantity, String rejectedQuantity, String frozenQuantity,
			String remainingQuantity, boolean canProcess, String disabledReason, String reason, String remark,
			String createdBy, OffsetDateTime createdAt, String updatedBy, OffsetDateTime updatedAt, String completedBy,
			OffsetDateTime completedAt, Integer version,
			List<InventoryTrackingService.TrackingAllocationResponse> trackingAllocations) {
	}

	public record QualityStatusTransferResponse(String fromQualityStatus, String fromQualityStatusName,
			String toQualityStatus, String toQualityStatusName, String quantity, String fromBeforeQuantity,
			String fromAfterQuantity, String toBeforeQuantity, String toAfterQuantity,
			List<InventoryTrackingService.TrackingAllocationResponse> trackingAllocations) {
	}

	private record InspectionRow(Long id, String inspectionNo, String sourceType, Long sourceId, Long sourceLineId,
			Long warehouseId, Long materialId, Long unitId, LocalDate businessDate, BigDecimal inspectionQuantity,
			QualityInspectionStatus status) {
	}

	private record ValidatedInspectionFilters(QualityInspectionSourceType sourceType, QualityInspectionStatus status,
			InventoryQualityStatus qualityStatus, LocalDate dateFrom, LocalDate dateTo) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

	private record TrackedTransferSummary(BigDecimal fromBeforeQuantity, BigDecimal fromAfterQuantity,
			BigDecimal toBeforeQuantity, BigDecimal toAfterQuantity,
			List<InventoryTrackingService.TrackingAllocationResponse> trackingAllocations) {
	}

}
