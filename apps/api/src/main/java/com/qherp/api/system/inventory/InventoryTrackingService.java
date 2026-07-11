package com.qherp.api.system.inventory;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.ApiErrorDetail;
import com.qherp.api.common.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InventoryTrackingService {

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final BigDecimal ONE = BigDecimal.ONE;

	private final JdbcTemplate jdbcTemplate;

	public InventoryTrackingService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<ResolvedTrackingAllocation> prepareInboundAllocations(String documentType, Long documentId,
			Long documentLineId, Long warehouseId, Long materialId, Long unitId, BigDecimal lineQuantity,
			InventoryQualityStatus qualityStatus, LocalDate businessDate, List<TrackingAllocationRequest> allocations,
			String operatorName) {
		return prepareInboundAllocations(documentType, documentId, documentLineId, warehouseId, materialId, unitId,
				lineQuantity, qualityStatus, businessDate, allocations, operatorName, "trackingAllocations");
	}

	public List<ResolvedTrackingAllocation> prepareInboundAllocations(String documentType, Long documentId,
			Long documentLineId, Long warehouseId, Long materialId, Long unitId, BigDecimal lineQuantity,
			InventoryQualityStatus qualityStatus, LocalDate businessDate, List<TrackingAllocationRequest> allocations,
			String operatorName, String fieldPrefix) {
		InventoryTrackingMethod trackingMethod = trackingMethod(materialId);
		List<TrackingAllocationRequest> normalized = allocations == null ? List.of() : allocations;
		if (trackingMethod == InventoryTrackingMethod.NONE) {
			if (!normalized.isEmpty()) {
				throw error(ApiErrorCode.VALIDATION_ERROR, fieldPrefix);
			}
			return List.of(new ResolvedTrackingAllocation(null, null, null, null, lineQuantity, qualityStatus, null));
		}
		if (trackingMethod == InventoryTrackingMethod.BATCH && normalized.isEmpty()) {
			throw error(ApiErrorCode.INVENTORY_BATCH_REQUIRED, fieldPrefix);
		}
		if (trackingMethod == InventoryTrackingMethod.SERIAL && normalized.isEmpty()) {
			throw error(ApiErrorCode.INVENTORY_SERIAL_REQUIRED, fieldPrefix);
		}
		validateTotalQuantity(lineQuantity, normalized, fieldPrefix);
		if (trackingMethod == InventoryTrackingMethod.BATCH) {
			return prepareBatchInbound(documentType, documentId, documentLineId, warehouseId, materialId, unitId,
					qualityStatus, businessDate, normalized, operatorName, fieldPrefix);
		}
		return prepareSerialInbound(documentType, documentId, documentLineId, warehouseId, materialId, unitId,
				lineQuantity, qualityStatus, businessDate, normalized, operatorName, fieldPrefix);
	}

	public List<ResolvedTrackingAllocation> storedAllocations(String documentType, Long documentId,
			Long documentLineId) {
		return this.jdbcTemplate.query("""
				select a.id, a.batch_id, b.batch_no, a.serial_id, s.serial_no, a.quantity, a.quality_status
				from inv_stock_tracking_allocation a
				left join inv_batch b on b.id = a.batch_id
				left join inv_serial s on s.id = a.serial_id
				where a.document_type = ?
				and a.document_id = ?
				and a.document_line_id = ?
				order by a.id asc
				""", this::mapResolvedAllocation, documentType, documentId, documentLineId);
	}

	public List<TrackingAllocationResponse> allocationResponses(String documentType, Long documentId,
			Long documentLineId) {
		return this.jdbcTemplate.query("""
				select a.id, a.allocation_type, a.document_type, a.document_id, a.document_line_id,
				       a.source_type, a.source_id, a.source_line_id, a.material_id, m.tracking_method,
				       a.batch_id, b.batch_no, a.serial_id, s.serial_no, a.quantity, a.quality_status, a.movement_id
				from inv_stock_tracking_allocation a
				join mst_material m on m.id = a.material_id
				left join inv_batch b on b.id = a.batch_id
				left join inv_serial s on s.id = a.serial_id
				where a.document_type = ?
				and a.document_id = ?
				and a.document_line_id = ?
				order by a.id asc
				""", this::mapTrackingAllocationResponse, documentType, documentId, documentLineId);
	}

	public List<TrackingAllocationResponse> allocationResponses(String documentType, Long documentId) {
		return this.jdbcTemplate.query("""
				select a.id, a.allocation_type, a.document_type, a.document_id, a.document_line_id,
				       a.source_type, a.source_id, a.source_line_id, a.material_id, m.tracking_method,
				       a.batch_id, b.batch_no, a.serial_id, s.serial_no, a.quantity, a.quality_status, a.movement_id
				from inv_stock_tracking_allocation a
				join mst_material m on m.id = a.material_id
				left join inv_batch b on b.id = a.batch_id
				left join inv_serial s on s.id = a.serial_id
				where a.document_type = ?
				and a.document_id = ?
				order by a.id asc
				""", this::mapTrackingAllocationResponse, documentType, documentId);
	}

	public void deleteDocumentAllocations(String documentType, Long documentId) {
		this.jdbcTemplate.update("""
				delete from inv_stock_tracking_allocation
				where document_type = ?
				and document_id = ?
				and movement_id is null
				""", documentType, documentId);
	}

	public void deleteDraftDocumentTracking(String documentType, Long documentId) {
		deleteDocumentAllocations(documentType, documentId);
		this.jdbcTemplate.update("""
				delete from inv_serial
				where source_type = ?
				and source_id = ?
				and stock_status = 'CANCELLED'
				and last_movement_id is null
				""", documentType, documentId);
	}

	public void attachMovement(Long allocationId, Long movementId) {
		if (allocationId == null || movementId == null) {
			return;
		}
		this.jdbcTemplate.update("""
				update inv_stock_tracking_allocation
				set movement_id = ?, updated_at = now(), version = version + 1
				where id = ?
				""", movementId, allocationId);
	}

	public void markInboundPosted(ResolvedTrackingAllocation allocation, Long warehouseId,
			InventoryQualityStatus qualityStatus, Long movementId, String operatorName) {
		if (allocation.serialId() == null) {
			return;
		}
		this.jdbcTemplate.update("""
				update inv_serial
				set warehouse_id = ?, quality_status = ?, stock_status = 'IN_STOCK', last_movement_id = ?,
				    updated_by = ?, updated_at = now(), version = version + 1
				where id = ?
				""", warehouseId, qualityStatus.name(), movementId, operatorName, allocation.serialId());
	}

	public List<ResolvedTrackingAllocation> resolveQualityAllocations(Long warehouseId, Long materialId, Long unitId,
			InventoryQualityStatus fromStatus, BigDecimal totalQuantity, List<TrackingAllocationRequest> allocations) {
		return resolveQualityAllocations(warehouseId, materialId, unitId, fromStatus, totalQuantity, allocations, null,
				"trackingAllocations");
	}

	public List<ResolvedTrackingAllocation> resolveQualityAllocations(Long warehouseId, Long materialId, Long unitId,
			InventoryQualityStatus fromStatus, BigDecimal totalQuantity, List<TrackingAllocationRequest> allocations,
			InventoryQualityStatus defaultTargetStatus, String fieldPrefix) {
		InventoryTrackingMethod trackingMethod = trackingMethod(materialId);
		List<TrackingAllocationRequest> normalized = allocations == null ? List.of() : allocations;
		if (trackingMethod == InventoryTrackingMethod.NONE) {
			if (!normalized.isEmpty()) {
				throw error(ApiErrorCode.VALIDATION_ERROR, fieldPrefix);
			}
			return List.of(new ResolvedTrackingAllocation(null, null, null, null, totalQuantity, fromStatus, null));
		}
		if (trackingMethod == InventoryTrackingMethod.BATCH && normalized.isEmpty()) {
			throw error(ApiErrorCode.INVENTORY_BATCH_REQUIRED, fieldPrefix);
		}
		if (trackingMethod == InventoryTrackingMethod.SERIAL && normalized.isEmpty()) {
			throw error(ApiErrorCode.INVENTORY_SERIAL_REQUIRED, fieldPrefix);
		}
		validateTotalQuantity(totalQuantity, normalized, fieldPrefix);
		List<ResolvedTrackingAllocation> result = new ArrayList<>();
		Map<Long, BigDecimal> batchQuantities = new HashMap<>();
		Set<String> serialIdentities = new HashSet<>();
		for (int i = 0; i < normalized.size(); i++) {
			TrackingAllocationRequest allocation = normalized.get(i);
			String allocationField = indexedField(fieldPrefix, i);
			BigDecimal quantity = validateQuantity(allocation.quantity(), allocationField + ".quantity");
			Long batchId = allocation.batchId();
			Long serialId = allocation.serialId();
			InventoryQualityStatus targetStatus = parseQualityStatus(allocation.qualityStatus(), defaultTargetStatus,
					allocationField + ".qualityStatus");
			if (trackingMethod == InventoryTrackingMethod.BATCH) {
				if (batchId == null) {
					throw error(ApiErrorCode.INVENTORY_BATCH_REQUIRED, allocationField + ".batchId");
				}
				validateBatchMaterial(batchId, materialId);
				batchQuantities.merge(batchId, quantity, BigDecimal::add);
				result.add(new ResolvedTrackingAllocation(batchId, batchNo(batchId), null, null, quantity, targetStatus,
						null));
			}
			else {
				if (serialId == null) {
					throw error(ApiErrorCode.INVENTORY_SERIAL_REQUIRED, allocationField + ".serialId");
				}
				if (quantity.compareTo(ONE) != 0 || !serialIdentities.add(serialId.toString())) {
					throw error(ApiErrorCode.INVENTORY_TRACKING_QUANTITY_MISMATCH, allocationField + ".serialId");
				}
				validateSerialMaterial(serialId, materialId);
				validateTrackedBalance(warehouseId, materialId, fromStatus, null, serialId, quantity);
				result.add(new ResolvedTrackingAllocation(null, null, serialId, serialNo(serialId), quantity,
						targetStatus, null));
			}
		}
		for (Map.Entry<Long, BigDecimal> entry : batchQuantities.entrySet()) {
			validateTrackedBalance(warehouseId, materialId, fromStatus, entry.getKey(), null, entry.getValue());
		}
		return result;
	}

	public Long recordQualityAllocation(String documentType, Long documentId, Long documentLineId, Long warehouseId,
			Long materialId, Long unitId, InventoryQualityStatus qualityStatus, ResolvedTrackingAllocation allocation,
			Long movementId, String operatorName) {
		return insertAllocation(InventoryTrackingAllocationType.QUALITY_TRANSFER, documentType, documentId,
				documentLineId, documentType, documentId, documentLineId, warehouseId, materialId, unitId,
				qualityStatus, allocation.batchId(), allocation.serialId(), allocation.quantity(), movementId,
				operatorName);
	}

	public void markSerialQuality(ResolvedTrackingAllocation allocation, Long warehouseId,
			InventoryQualityStatus qualityStatus, Long movementId, String operatorName) {
		if (allocation.serialId() == null) {
			return;
		}
		this.jdbcTemplate.update("""
				update inv_serial
				set warehouse_id = ?, quality_status = ?, stock_status = 'IN_STOCK', last_movement_id = ?,
				    updated_by = ?, updated_at = now(), version = version + 1
				where id = ?
				""", warehouseId, qualityStatus.name(), movementId, operatorName, allocation.serialId());
	}

	public InventoryTrackingMethod trackingMethod(Long materialId) {
		String value = this.jdbcTemplate.queryForObject("select tracking_method from mst_material where id = ?",
				String.class, materialId);
		return InventoryTrackingMethod.valueOf(value);
	}

	private List<ResolvedTrackingAllocation> prepareBatchInbound(String documentType, Long documentId,
			Long documentLineId, Long warehouseId, Long materialId, Long unitId, InventoryQualityStatus qualityStatus,
			LocalDate businessDate, List<TrackingAllocationRequest> allocations, String operatorName,
			String fieldPrefix) {
		if (allocations.isEmpty()) {
			throw error(ApiErrorCode.INVENTORY_BATCH_REQUIRED, fieldPrefix);
		}
		List<ResolvedTrackingAllocation> result = new ArrayList<>();
		Set<Long> batchIds = new HashSet<>();
		for (int i = 0; i < allocations.size(); i++) {
			TrackingAllocationRequest allocation = allocations.get(i);
			String allocationField = indexedField(fieldPrefix, i);
			BigDecimal quantity = validateQuantity(allocation.quantity(), allocationField + ".quantity");
			Long batchId = allocation.batchId() == null
					? createBatch(materialId,
							requiredText(allocation.batchNo(), ApiErrorCode.INVENTORY_BATCH_REQUIRED,
									allocationField + ".batchNo"),
							documentType, documentId, documentLineId, businessDate, operatorName)
					: allocation.batchId();
			validateBatchMaterial(batchId, materialId);
			if (!batchIds.add(batchId)) {
				throw error(ApiErrorCode.INVENTORY_TRACKING_QUANTITY_MISMATCH, allocationField + ".batchId");
			}
			Long allocationId = insertAllocation(InventoryTrackingAllocationType.INBOUND, documentType, documentId,
					documentLineId, documentType, documentId, documentLineId, warehouseId, materialId, unitId,
					qualityStatus, batchId, null, quantity, null, operatorName);
			result.add(new ResolvedTrackingAllocation(batchId, batchNo(batchId), null, null, quantity, qualityStatus,
					allocationId));
		}
		return result;
	}

	private List<ResolvedTrackingAllocation> prepareSerialInbound(String documentType, Long documentId,
			Long documentLineId, Long warehouseId, Long materialId, Long unitId, BigDecimal lineQuantity,
			InventoryQualityStatus qualityStatus, LocalDate businessDate, List<TrackingAllocationRequest> allocations,
			String operatorName, String fieldPrefix) {
		if (allocations.isEmpty()) {
			throw error(ApiErrorCode.INVENTORY_SERIAL_REQUIRED, fieldPrefix);
		}
		if (!integerQuantity(lineQuantity) || lineQuantity.intValueExact() != allocations.size()) {
			throw error(ApiErrorCode.INVENTORY_TRACKING_QUANTITY_MISMATCH, fieldPrefix);
		}
		List<ResolvedTrackingAllocation> result = new ArrayList<>();
		Set<String> serialNos = new HashSet<>();
		for (int i = 0; i < allocations.size(); i++) {
			TrackingAllocationRequest allocation = allocations.get(i);
			String allocationField = indexedField(fieldPrefix, i);
			BigDecimal quantity = validateQuantity(allocation.quantity(), allocationField + ".quantity");
			if (quantity.compareTo(ONE) != 0) {
				throw error(ApiErrorCode.INVENTORY_TRACKING_QUANTITY_MISMATCH, allocationField + ".quantity");
			}
			String serialNo = requiredText(allocation.serialNo(), ApiErrorCode.INVENTORY_SERIAL_REQUIRED,
					allocationField + ".serialNo");
			if (!serialNos.add(serialNo)) {
				throw error(ApiErrorCode.INVENTORY_SERIAL_DUPLICATED, allocationField + ".serialNo");
			}
			Long batchId = allocation.batchId();
			if (batchId != null) {
				validateBatchMaterial(batchId, materialId);
			}
			Long serialId = createSerial(materialId, serialNo, batchId, documentType, documentId,
					documentLineId, businessDate, operatorName, allocationField + ".serialNo");
			Long allocationId = insertAllocation(InventoryTrackingAllocationType.INBOUND, documentType, documentId,
					documentLineId, documentType, documentId, documentLineId, warehouseId, materialId, unitId,
					qualityStatus, null, serialId, quantity, null, operatorName);
			result.add(new ResolvedTrackingAllocation(null, null, serialId, serialNo, quantity, qualityStatus,
					allocationId));
		}
		return result;
	}

	private void validateTotalQuantity(BigDecimal lineQuantity, List<TrackingAllocationRequest> allocations,
			String fieldPrefix) {
		BigDecimal total = ZERO;
		for (int i = 0; i < allocations.size(); i++) {
			total = total.add(validateQuantity(allocations.get(i).quantity(), indexedField(fieldPrefix, i) + ".quantity"));
		}
		if (lineQuantity == null || total.compareTo(lineQuantity) != 0) {
			throw error(ApiErrorCode.INVENTORY_TRACKING_QUANTITY_MISMATCH, fieldPrefix);
		}
	}

	private BigDecimal validateQuantity(BigDecimal quantity, String field) {
		if (quantity == null || quantity.compareTo(ZERO) <= 0) {
			throw error(ApiErrorCode.INVENTORY_TRACKING_QUANTITY_MISMATCH, field);
		}
		return quantity;
	}

	private Long createBatch(Long materialId, String batchNo, String sourceType, Long sourceId, Long sourceLineId,
			LocalDate businessDate, String operatorName) {
		return this.jdbcTemplate.query("""
				select id
				from inv_batch
				where material_id = ?
				and batch_no = ?
				""", (rs, rowNum) -> rs.getLong("id"), materialId, batchNo).stream().findFirst().orElseGet(() -> {
			try {
				return this.jdbcTemplate.queryForObject("""
						insert into inv_batch (
							material_id, batch_no, source_type, source_id, source_line_id, business_date,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, now(), ?, now())
						returning id
						""", Long.class, materialId, batchNo, sourceType, sourceId, sourceLineId, businessDate,
						operatorName, operatorName);
			}
			catch (DuplicateKeyException exception) {
				throw new BusinessException(ApiErrorCode.CONFLICT);
			}
		});
	}

	private Long createSerial(Long materialId, String serialNo, Long batchId, String sourceType, Long sourceId,
			Long sourceLineId, LocalDate businessDate, String operatorName, String field) {
		try {
			return this.jdbcTemplate.queryForObject("""
					insert into inv_serial (
						material_id, serial_no, batch_id, source_type, source_id, source_line_id, stock_status,
						business_date, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, 'CANCELLED', ?, ?, now(), ?, now())
					returning id
					""", Long.class, materialId, serialNo, batchId, sourceType, sourceId, sourceLineId, businessDate,
					operatorName, operatorName);
		}
		catch (DuplicateKeyException exception) {
			throw error(ApiErrorCode.INVENTORY_SERIAL_DUPLICATED, field);
		}
	}

	private Long insertAllocation(InventoryTrackingAllocationType allocationType, String documentType, Long documentId,
			Long documentLineId, String sourceType, Long sourceId, Long sourceLineId, Long warehouseId,
			Long materialId, Long unitId, InventoryQualityStatus qualityStatus, Long batchId, Long serialId,
			BigDecimal quantity, Long movementId, String operatorName) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_tracking_allocation (
					allocation_type, document_type, document_id, document_line_id, source_type, source_id,
					source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, serial_id,
					quantity, movement_id, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, now())
				returning id
				""", Long.class, allocationType.name(), documentType, documentId, documentLineId, sourceType,
				sourceId, sourceLineId, warehouseId, materialId, unitId, qualityStatus.name(), batchId, serialId,
				quantity, movementId, operatorName, operatorName);
	}

	private ResolvedTrackingAllocation mapResolvedAllocation(ResultSet rs, int rowNum) throws SQLException {
		return new ResolvedTrackingAllocation(nullableLong(rs, "batch_id"), rs.getString("batch_no"),
				nullableLong(rs, "serial_id"), rs.getString("serial_no"), rs.getBigDecimal("quantity"),
				InventoryQualityStatus.valueOf(rs.getString("quality_status")), nullableLong(rs, "id"));
	}

	private TrackingAllocationResponse mapTrackingAllocationResponse(ResultSet rs, int rowNum) throws SQLException {
		String trackingMethodValue = rs.getString("tracking_method");
		InventoryTrackingMethod trackingMethod = InventoryTrackingMethod.valueOf(trackingMethodValue);
		String qualityStatus = rs.getString("quality_status");
		String sourceType = rs.getString("source_type");
		Long sourceId = nullableLong(rs, "source_id");
		Long sourceLineId = nullableLong(rs, "source_line_id");
		return new TrackingAllocationResponse(nullableLong(rs, "id"), trackingMethod.name(),
				trackingMethod.displayName(), nullableLong(rs, "batch_id"), rs.getString("batch_no"),
				nullableLong(rs, "serial_id"), rs.getString("serial_no"), rs.getBigDecimal("quantity"),
				qualityStatus, InventoryQualityStatus.valueOf(qualityStatus).displayName(),
				nullableLong(rs, "movement_id"), rs.getString("document_type"), nullableLong(rs, "document_id"),
				nullableLong(rs, "document_line_id"), sourceType, sourceId, sourceLineId,
				sourceDocumentNo(sourceType, sourceId), sourceLineNo(sourceType, sourceLineId));
	}

	private String sourceDocumentNo(String sourceType, Long sourceId) {
		if (sourceType == null || sourceId == null) {
			return null;
		}
		try {
			return switch (sourceType) {
				case "PURCHASE_RECEIPT" -> this.jdbcTemplate.queryForObject(
						"select receipt_no from proc_purchase_receipt where id = ?", String.class, sourceId);
				case "PRODUCTION_COMPLETION_RECEIPT", "PRODUCTION_COMPLETION" -> this.jdbcTemplate.queryForObject(
						"select receipt_no from mfg_completion_receipt where id = ?", String.class, sourceId);
				case "QUALITY_INSPECTION" -> this.jdbcTemplate.queryForObject(
						"select inspection_no from qua_quality_inspection where id = ?", String.class, sourceId);
				case "QUALITY_STATUS_TRANSFER" -> "QUALITY_STATUS_TRANSFER-" + sourceId;
				default -> null;
			};
		}
		catch (EmptyResultDataAccessException exception) {
			return null;
		}
	}

	private Integer sourceLineNo(String sourceType, Long sourceLineId) {
		if (sourceType == null || sourceLineId == null) {
			return null;
		}
		try {
			return switch (sourceType) {
				case "PURCHASE_RECEIPT" -> this.jdbcTemplate.queryForObject(
						"select line_no from proc_purchase_receipt_line where id = ?", Integer.class, sourceLineId);
				case "PRODUCTION_COMPLETION_RECEIPT", "PRODUCTION_COMPLETION" -> 1;
				default -> null;
			};
		}
		catch (EmptyResultDataAccessException exception) {
			return null;
		}
	}

	private void validateBatchMaterial(Long batchId, Long materialId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_batch
				where id = ?
				and material_id = ?
				""", Long.class, batchId, materialId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_TRACKING_NOT_FOUND);
		}
	}

	private void validateSerialMaterial(Long serialId, Long materialId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_serial
				where id = ?
				and material_id = ?
				""", Long.class, serialId, materialId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_TRACKING_NOT_FOUND);
		}
	}

	private void validateTrackedBalance(Long warehouseId, Long materialId, InventoryQualityStatus qualityStatus,
			Long batchId, Long serialId, BigDecimal quantity) {
		BigDecimal available = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				""", BigDecimal.class, warehouseId, materialId, qualityStatus.name(), batchId, serialId);
		if (available == null || available.compareTo(quantity) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_TRACKING_STOCK_NOT_ENOUGH);
		}
	}

	private String batchNo(Long batchId) {
		return this.jdbcTemplate.queryForObject("select batch_no from inv_batch where id = ?", String.class, batchId);
	}

	private String serialNo(Long serialId) {
		return this.jdbcTemplate.queryForObject("select serial_no from inv_serial where id = ?", String.class,
				serialId);
	}

	private InventoryQualityStatus parseQualityStatus(String qualityStatus, InventoryQualityStatus defaultStatus,
			String field) {
		if (!hasText(qualityStatus)) {
			if (defaultStatus != null) {
				return defaultStatus;
			}
			throw error(ApiErrorCode.INVENTORY_QUALITY_STATUS_REQUIRED, field);
		}
		try {
			return InventoryQualityStatus.valueOf(qualityStatus);
		}
		catch (RuntimeException exception) {
			throw error(ApiErrorCode.INVENTORY_QUALITY_STATUS_INVALID, field);
		}
	}

	private String requiredText(String value, ApiErrorCode errorCode, String field) {
		if (!hasText(value)) {
			throw error(errorCode, field);
		}
		return value;
	}

	private BusinessException error(ApiErrorCode errorCode, String field) {
		return new BusinessException(errorCode, errorCode.message(), errorCode.httpStatus(),
				List.of(new ApiErrorDetail(field, errorCode.message())));
	}

	private String indexedField(String fieldPrefix, int index) {
		return fieldPrefix + "[" + index + "]";
	}

	private boolean integerQuantity(BigDecimal quantity) {
		try {
			quantity.intValueExact();
			return true;
		}
		catch (ArithmeticException exception) {
			return false;
		}
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record TrackingAllocationRequest(Long batchId, String batchNo, Long serialId, String serialNo,
			BigDecimal quantity, String qualityStatus, Long sourceAllocationId) {
	}

	public record ResolvedTrackingAllocation(Long batchId, String batchNo, Long serialId, String serialNo,
			BigDecimal quantity, InventoryQualityStatus qualityStatus, Long allocationId) {
	}

	public record TrackingAllocationResponse(Long allocationId, String trackingMethod, String trackingMethodName,
			Long batchId, String batchNo, Long serialId, String serialNo, BigDecimal quantity, String qualityStatus,
			String qualityStatusName, Long movementId, String documentType, Long documentId, Long documentLineId,
			String sourceType, Long sourceId, Long sourceLineId, String sourceDocumentNo, Integer sourceLineNo) {
	}

}
