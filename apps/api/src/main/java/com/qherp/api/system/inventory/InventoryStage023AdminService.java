package com.qherp.api.system.inventory;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.platform.PlatformApprovalService;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Service
public class InventoryStage023AdminService {

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger NUMBER_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final InventoryPostingService inventoryPostingService;

	private final InventoryValuationService inventoryValuationService;

	private final PlatformApprovalService approvalService;

	private final AuditService auditService;

	private final BusinessPeriodGuard businessPeriodGuard;

	public InventoryStage023AdminService(JdbcTemplate jdbcTemplate, InventoryPostingService inventoryPostingService,
			InventoryValuationService inventoryValuationService, PlatformApprovalService approvalService,
			AuditService auditService, BusinessPeriodGuard businessPeriodGuard) {
		this.jdbcTemplate = jdbcTemplate;
		this.inventoryPostingService = inventoryPostingService;
		this.inventoryValuationService = inventoryValuationService;
		this.approvalService = approvalService;
		this.auditService = auditService;
		this.businessPeriodGuard = businessPeriodGuard;
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> costLayers(Long costLayerId, String ownershipType, Long projectId,
			Long materialId, Long warehouseId, String sourceType, Long sourceId, String batchNo, String serialNo,
			String status, String keyword, int page, int pageSize, CurrentUser currentUser) {
		boolean costVisible = currentUser != null && currentUser.permissions().contains("inventory:valuation:view");
		List<Object> args = new ArrayList<>();
		StringBuilder where = new StringBuilder(" where 1 = 1");
		if (costLayerId != null) {
			where.append(" and l.id = ?");
			args.add(costLayerId);
		}
		if (ownershipType != null && !ownershipType.isBlank()) {
			if (!"PROJECT".equals(ownershipType.trim().toUpperCase())) {
				where.append(" and 1 = 0");
			}
		}
		if (projectId != null) {
			where.append(" and l.project_id = ?");
			args.add(projectId);
		}
		if (materialId != null) {
			where.append(" and l.material_id = ?");
			args.add(materialId);
		}
		if (warehouseId != null) {
			where.append("""
					 and exists (
						select 1
						from inv_stock_balance b
						where b.cost_layer_id = l.id
						and b.warehouse_id = ?
						and b.quantity_on_hand > 0
					)
					""");
			args.add(warehouseId);
		}
		if (sourceType != null && !sourceType.isBlank()) {
			where.append(" and l.source_type = ?");
			args.add(sourceType.trim());
		}
		if (sourceId != null) {
			where.append(" and l.source_id = ?");
			args.add(sourceId);
		}
		if (batchNo != null && !batchNo.isBlank()) {
			where.append(" and bt.batch_no ilike ?");
			args.add("%" + batchNo.trim() + "%");
		}
		if (serialNo != null && !serialNo.isBlank()) {
			where.append(" and sr.serial_no ilike ?");
			args.add("%" + serialNo.trim() + "%");
		}
		if (status != null && !status.isBlank()) {
			where.append(" and l.status = ?");
			args.add(status.trim().toUpperCase());
		}
		if (keyword != null && !keyword.isBlank()) {
			where.append(" and (m.code ilike ? or m.name ilike ? or l.source_type ilike ?)");
			String pattern = "%" + keyword.trim() + "%";
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
		}
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_project_cost_layer l
				join mst_material m on m.id = l.material_id
				left join inv_batch bt on bt.id = l.batch_id
				left join inv_serial sr on sr.id = l.serial_id
				%s
				""".formatted(where), Long.class, args.toArray());
		args.add(pageSize);
		args.add((Math.max(page, 1) - 1) * pageSize);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select l.id, l.project_id, l.material_id, m.code as material_code, m.name as material_name,
				       l.source_type, l.source_id, l.source_line_id, l.parent_layer_id, l.batch_id, l.serial_id,
				       bt.batch_no, sr.serial_no,
				       l.original_quantity, l.original_amount, l.remaining_quantity, l.remaining_amount,
				       l.unit_cost, l.status, l.created_at, l.version
				from inv_project_cost_layer l
				join mst_material m on m.id = l.material_id
				left join inv_batch bt on bt.id = l.batch_id
				left join inv_serial sr on sr.id = l.serial_id
				%s
				order by l.id
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", rs.getLong("id"));
			row.put("projectId", rs.getLong("project_id"));
			row.put("materialId", rs.getLong("material_id"));
			row.put("materialCode", rs.getString("material_code"));
			row.put("materialName", rs.getString("material_name"));
			row.put("sourceType", rs.getString("source_type"));
			row.put("sourceId", rs.getLong("source_id"));
			row.put("sourceLineId", rs.getLong("source_line_id"));
			row.put("parentLayerId", nullableLong(rs, "parent_layer_id"));
			row.put("batchId", nullableLong(rs, "batch_id"));
			row.put("serialId", nullableLong(rs, "serial_id"));
			row.put("batchNo", rs.getString("batch_no"));
			row.put("serialNo", rs.getString("serial_no"));
			row.put("originalQuantity", decimal(rs.getBigDecimal("original_quantity")));
			row.put("remainingQuantity", decimal(rs.getBigDecimal("remaining_quantity")));
			row.put("status", rs.getString("status"));
			row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			row.put("version", rs.getLong("version"));
			if (costVisible) {
				row.put("unitCost", decimal(rs.getBigDecimal("unit_cost")));
				row.put("originalAmount", money(rs.getBigDecimal("original_amount")));
				row.put("remainingAmount", money(rs.getBigDecimal("remaining_amount")));
			}
			return row;
		}, args.toArray());
		return PageResponse.of(items, page, pageSize, total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> reconciliation(Long materialId, CurrentUser currentUser) {
		boolean costVisible = currentUser != null && currentUser.permissions().contains("inventory:valuation:view");
		BigDecimal publicAmount = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(amount), 0)
				from inv_public_valuation_pool
				where (?::bigint is null or material_id = ?)
				""", BigDecimal.class, materialId, materialId);
		BigDecimal projectAmount = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(remaining_amount), 0)
				from inv_project_cost_layer
				where status <> 'CANCELLED'
				and (?::bigint is null or material_id = ?)
				""", BigDecimal.class, materialId, materialId);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("costVisible", costVisible);
		if (costVisible) {
			result.put("totalInventoryAmount", money(nullToZero(publicAmount).add(nullToZero(projectAmount))));
		}
		return result;
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> warehouseTransfers(String status, String keyword, int page, int pageSize,
			CurrentUser currentUser) {
		return documentPage("inv_warehouse_transfer", "transfer_no", status, keyword, page, pageSize,
				"WAREHOUSE_TRANSFER", currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> ownershipConversions(String status, String keyword, int page, int pageSize,
			CurrentUser currentUser) {
		return documentPage("inv_ownership_conversion", "conversion_no", status, keyword, page, pageSize,
				"OWNERSHIP_CONVERSION", currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> stocktakes(String status, String keyword, int page, int pageSize,
			CurrentUser currentUser) {
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		if (status != null && !status.isBlank()) {
			conditions.add("status = ?");
			args.add(status.trim().toUpperCase());
		}
		if (keyword != null && !keyword.isBlank()) {
			conditions.add("(stocktake_no ilike ? or reason ilike ?)");
			String pattern = "%" + keyword.trim() + "%";
			args.add(pattern);
			args.add(pattern);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		long total = this.jdbcTemplate.queryForObject("select count(*) from inv_stocktake " + where, Long.class,
				args.toArray());
		args.add(pageSize);
		args.add((Math.max(page, 1) - 1) * pageSize);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id, stocktake_no as document_no, business_date, scope_type, warehouse_id, material_id,
				       reason, status, started_at, posted_at, cancelled_at, created_by_username, created_at,
				       updated_by_username, updated_at, version
				from inv_stocktake
				%s
				order by id desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> row = documentRow(rs.getLong("id"), rs.getString("document_no"),
					rs.getObject("business_date", LocalDate.class), rs.getString("reason"), rs.getString("status"),
					rs.getObject("posted_at", OffsetDateTime.class), rs.getObject("cancelled_at", OffsetDateTime.class),
					rs.getString("created_by_username"), rs.getObject("created_at", OffsetDateTime.class),
					rs.getString("updated_by_username"), rs.getObject("updated_at", OffsetDateTime.class),
					rs.getLong("version"), "STOCKTAKE", currentUser);
			row.put("scopeType", rs.getString("scope_type"));
			row.put("warehouseId", nullableLong(rs, "warehouse_id"));
			row.put("materialId", nullableLong(rs, "material_id"));
			row.put("startedAt", rs.getObject("started_at", OffsetDateTime.class));
			return row;
		}, args.toArray());
		return PageResponse.of(items, page, pageSize, total);
	}

	@Transactional
	public Map<String, Object> createWarehouseTransfer(WarehouseTransferRequest request, CurrentUser operator) {
		this.businessPeriodGuard.assertWritable(request.businessDate(), BusinessPeriodOperation.CREATE,
				"INVENTORY_WAREHOUSE_TRANSFER", null);
		validateWarehouseTransferLines(request.lines());
		for (WarehouseTransferLineRequest line : request.lines()) {
			requireNoStocktakeLock(line.sourceWarehouseId(), line.materialId());
		}
		Long id = this.jdbcTemplate.queryForObject("""
				insert into inv_warehouse_transfer (
					transfer_no, business_date, reason, status, idempotency_key,
					created_by_user_id, created_by_username, updated_by_username
				)
				values (?, ?, ?, 'DRAFT', ?, ?, ?, ?)
				returning id
				""", Long.class, documentNo("INV-TRF"), request.businessDate(), request.reason(),
				request.idempotencyKey(), operator.id(), operator.username(), operator.username());
		for (WarehouseTransferLineRequest line : request.lines()) {
			this.jdbcTemplate.update("""
					insert into inv_warehouse_transfer_line (
						transfer_id, line_no, source_warehouse_id, target_warehouse_id, ownership_type, project_id,
						material_id, unit_id, quality_status, batch_id, serial_id, quantity, source_cost_layer_id
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", id, line.lineNo(), line.sourceWarehouseId(), line.targetWarehouseId(),
					defaultOwnership(line.ownershipType()), publicProject(line.ownershipType(), line.projectId()),
					line.materialId(), line.unitId(), defaultQuality(line.qualityStatus()), line.batchId(),
					line.serialId(), line.quantity(), projectSourceCostLayerId(line));
		}
		return warehouseTransfer(id, operator);
	}

	@Transactional
	public Map<String, Object> updateWarehouseTransfer(Long id, WarehouseTransferRequest request,
			CurrentUser operator) {
		validateWarehouseTransferLines(request.lines());
		Header transfer = lockHeader("inv_warehouse_transfer", id);
		requireVersion(transfer.version(), request.version());
		this.businessPeriodGuard.assertWritable(request.businessDate(), BusinessPeriodOperation.UPDATE,
				"INVENTORY_WAREHOUSE_TRANSFER", id);
		if (!"DRAFT".equals(transfer.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		for (WarehouseTransferLineRequest line : request.lines()) {
			requireNoStocktakeLock(line.sourceWarehouseId(), line.materialId());
		}
		this.jdbcTemplate.update("""
				update inv_warehouse_transfer
				set business_date = ?, reason = ?, updated_by_username = ?, updated_at = now(), version = version + 1
				where id = ?
				""", request.businessDate(), request.reason(), operator.username(), id);
		this.jdbcTemplate.update("delete from inv_warehouse_transfer_line where transfer_id = ?", id);
		insertWarehouseTransferLines(id, request.lines());
		this.auditService.record(operator, "INVENTORY_WAREHOUSE_TRANSFER_UPDATE",
				"INVENTORY_WAREHOUSE_TRANSFER", id, transfer.documentNo(), null);
		return warehouseTransfer(id, operator);
	}

	@Transactional
	public Map<String, Object> postWarehouseTransfer(Long id, VersionedActionRequest request, CurrentUser operator) {
		Header transfer = lockHeader("inv_warehouse_transfer", id);
		requireVersion(transfer.version(), request.version());
		this.businessPeriodGuard.assertWritable(transfer.businessDate(), BusinessPeriodOperation.POST,
				"INVENTORY_WAREHOUSE_TRANSFER", id);
		if (!"DRAFT".equals(transfer.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		List<TransferLine> lines = transferLines(id);
		lockWarehouseTransferPostingScopes(lines);
		for (TransferLine line : lines) {
			requireNoStocktakeLock(line.sourceWarehouseId(), line.materialId());
			ensureWarehouseTransferSourceLayer(line);
			InventoryPostingService.PostingResult out = this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
					InventoryMovementType.WAREHOUSE_TRANSFER_OUT, InventoryDirection.OUT, line.sourceWarehouseId(),
					line.materialId(), line.unitId(), line.quantity(), InventoryQualityStatus.valueOf(line.qualityStatus()),
					"WAREHOUSE_TRANSFER", id, line.id() * 10 + 1, transfer.businessDate(), transfer.reason(),
					null, operator.username(), false, line.batchId(), line.serialId(),
					new InventoryPostingService.ValuationContext(line.ownershipType(), line.projectId(), null,
							line.sourceCostLayerId(), null)));
			InventoryPostingService.ValuationContext inContext = new InventoryPostingService.ValuationContext(
					line.ownershipType(), line.projectId(), out.unitCost(), out.costLayerId(), out.valueMovementId(),
					out.inventoryAmount());
			InventoryPostingService.PostingResult in = this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
					InventoryMovementType.WAREHOUSE_TRANSFER_IN, InventoryDirection.IN, line.targetWarehouseId(),
					line.materialId(), line.unitId(), line.quantity(), InventoryQualityStatus.valueOf(line.qualityStatus()),
					"WAREHOUSE_TRANSFER", id, line.id() * 10 + 2, transfer.businessDate(), transfer.reason(),
					null, operator.username(), false, line.batchId(), line.serialId(), inContext));
			this.jdbcTemplate.update("""
					update inv_warehouse_transfer_line
					set source_movement_id = ?, target_movement_id = ?, version = version + 1
					where id = ?
					""", out.movementId(), in.movementId(), line.id());
		}
		this.jdbcTemplate.update("""
				update inv_warehouse_transfer
				set status = 'POSTED', posted_at = now(), updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""", operator.username(), id);
		this.auditService.record(operator, "INVENTORY_WAREHOUSE_TRANSFER_POST", "INVENTORY_WAREHOUSE_TRANSFER",
				id, transfer.documentNo(), null);
		return warehouseTransfer(id, operator);
	}

	@Transactional
	public Map<String, Object> cancelWarehouseTransfer(Long id, VersionedActionRequest request, CurrentUser operator) {
		Header transfer = lockHeader("inv_warehouse_transfer", id);
		requireVersion(transfer.version(), request.version());
		this.businessPeriodGuard.assertWritable(transfer.businessDate(), BusinessPeriodOperation.CANCEL,
				"INVENTORY_WAREHOUSE_TRANSFER", id);
		if (!"DRAFT".equals(transfer.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		this.jdbcTemplate.update("""
				update inv_warehouse_transfer
				set status = 'CANCELLED', cancelled_at = now(), updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""", operator.username(), id);
		this.auditService.record(operator, "INVENTORY_WAREHOUSE_TRANSFER_CANCEL",
				"INVENTORY_WAREHOUSE_TRANSFER", id, transfer.documentNo(), null);
		return warehouseTransfer(id, operator);
	}

	@Transactional
	public Map<String, Object> createOwnershipConversion(OwnershipConversionRequest request, CurrentUser operator) {
		this.businessPeriodGuard.assertWritable(request.businessDate(), BusinessPeriodOperation.CREATE,
				"INVENTORY_OWNERSHIP_CONVERSION", null);
		validateLines(request.lines());
		Long id = this.jdbcTemplate.queryForObject("""
				insert into inv_ownership_conversion (
					conversion_no, business_date, reason, status, idempotency_key,
					created_by_user_id, created_by_username, updated_by_username
				)
				values (?, ?, ?, 'DRAFT', ?, ?, ?, ?)
				returning id
				""", Long.class, documentNo("INV-OWN"), request.businessDate(), request.reason(),
				request.idempotencyKey(), operator.id(), operator.username(), operator.username());
		for (OwnershipConversionLineRequest line : request.lines()) {
			this.jdbcTemplate.update("""
					insert into inv_ownership_conversion_line (
						conversion_id, line_no, source_ownership_type, source_project_id, target_ownership_type,
						target_project_id, source_warehouse_id, target_warehouse_id, material_id, unit_id,
						quality_status, batch_id, serial_id, quantity, source_unit_cost, source_cost_layer_id
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", id, line.lineNo(), line.sourceOwnershipType(), line.sourceProjectId(),
					line.targetOwnershipType(), line.targetProjectId(), line.sourceWarehouseId(),
					line.targetWarehouseId(), line.materialId(), line.unitId(), defaultQuality(line.qualityStatus()),
					line.batchId(), line.serialId(), line.quantity(), line.sourceUnitCost(), line.sourceCostLayerId());
		}
		return ownershipConversion(id, operator);
	}

	@Transactional
	public Map<String, Object> updateOwnershipConversion(Long id, OwnershipConversionRequest request,
			CurrentUser operator) {
		validateLines(request.lines());
		Header header = lockHeader("inv_ownership_conversion", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(request.businessDate(), BusinessPeriodOperation.UPDATE,
				"INVENTORY_OWNERSHIP_CONVERSION", id);
		if (!"DRAFT".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		this.jdbcTemplate.update("""
				update inv_ownership_conversion
				set business_date = ?, reason = ?, updated_by_username = ?, updated_at = now(), version = version + 1
				where id = ?
				""", request.businessDate(), request.reason(), operator.username(), id);
		this.jdbcTemplate.update("delete from inv_ownership_conversion_line where conversion_id = ?", id);
		insertOwnershipConversionLines(id, request.lines());
		this.auditService.record(operator, "INVENTORY_OWNERSHIP_CONVERSION_UPDATE",
				"INVENTORY_OWNERSHIP_CONVERSION", id, header.documentNo(), null);
		return ownershipConversion(id, operator);
	}

	@Transactional
	public Map<String, Object> submitOwnershipConversion(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		Header header = lockHeader("inv_ownership_conversion", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.UPDATE,
				"INVENTORY_OWNERSHIP_CONVERSION", id);
		if (!"DRAFT".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService.submitInventoryOwnershipConversion(id,
				new PlatformApprovalService.ApprovalSubmitRequest(request.version(), request.reason(),
						request.idempotencyKey()),
				operator, servletRequest);
		this.jdbcTemplate.update("""
				update inv_ownership_conversion
				set status = 'SUBMITTED', approval_instance_id = ?, updated_by_username = ?, updated_at = now()
				where id = ?
				""", approval.id(), operator.username(), id);
		this.auditService.record(operator, "INVENTORY_OWNERSHIP_CONVERSION_SUBMIT",
				"INVENTORY_OWNERSHIP_CONVERSION", id, header.documentNo(), servletRequest);
		return ownershipConversion(id, operator);
	}

	@Transactional
	public Map<String, Object> withdrawOwnershipConversion(Long id, VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return withdrawApprovalBackToDraft("inv_ownership_conversion", id, request, operator, servletRequest,
				this::ownershipConversion);
	}

	@Transactional
	public Map<String, Object> cancelOwnershipConversion(Long id, VersionedActionRequest request,
			CurrentUser operator) {
		Header header = lockHeader("inv_ownership_conversion", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.CANCEL,
				"INVENTORY_OWNERSHIP_CONVERSION", id);
		if (!"DRAFT".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		this.jdbcTemplate.update("""
				update inv_ownership_conversion
				set status = 'CANCELLED', cancelled_at = now(), updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""", operator.username(), id);
		this.auditService.record(operator, "INVENTORY_OWNERSHIP_CONVERSION_CANCEL",
				"INVENTORY_OWNERSHIP_CONVERSION", id, header.documentNo(), null);
		return ownershipConversion(id, operator);
	}

	@Transactional
	public void postOwnershipConversionFromApproval(Long id, CurrentUser operator) {
		Header header = lockHeader("inv_ownership_conversion", id);
		if ("POSTED".equals(header.status())) {
			return;
		}
		if (!"SUBMITTED".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.POST,
				"INVENTORY_OWNERSHIP_CONVERSION", id);
		List<OwnershipLine> lines = ownershipLines(id);
		lockOwnershipConversionPostingScopes(lines);
		Map<Long, Long> sourceCostLayerIds = resolveOwnershipConversionSourceLayers(lines);
		for (OwnershipLine line : lines) {
			Long sourceCostLayerId = sourceCostLayerIds.get(line.id());
			InventoryPostingService.PostingResult out = this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
					InventoryMovementType.OWNERSHIP_CONVERSION_OUT, InventoryDirection.OUT, line.sourceWarehouseId(),
					line.materialId(), line.unitId(), line.quantity(), InventoryQualityStatus.valueOf(line.qualityStatus()),
					"OWNERSHIP_CONVERSION", id, line.id() * 10 + 1, header.businessDate(), header.reason(),
					null, operator.username(), false, line.batchId(), line.serialId(),
					new InventoryPostingService.ValuationContext(line.sourceOwnershipType(), line.sourceProjectId(),
							null, sourceCostLayerId, null)));
			BigDecimal targetUnitCost = out.unitCost();
			InventoryPostingService.PostingResult in = this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
					InventoryMovementType.OWNERSHIP_CONVERSION_IN, InventoryDirection.IN, line.targetWarehouseId(),
					line.materialId(), line.unitId(), line.quantity(), InventoryQualityStatus.valueOf(line.qualityStatus()),
					"OWNERSHIP_CONVERSION", id, line.id() * 10 + 2, header.businessDate(), header.reason(),
					null, operator.username(), false, line.batchId(), line.serialId(),
					new InventoryPostingService.ValuationContext(line.targetOwnershipType(), line.targetProjectId(),
							targetUnitCost, out.costLayerId(), out.valueMovementId(), out.inventoryAmount())));
			this.jdbcTemplate.update("""
					update inv_ownership_conversion_line
					set source_movement_id = ?, target_movement_id = ?, version = version + 1
					where id = ?
					""", out.movementId(), in.movementId(), line.id());
		}
		this.jdbcTemplate.update("""
				update inv_ownership_conversion
				set status = 'POSTED', posted_at = now(), updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""", operator.username(), id);
	}

	@Transactional
	public Map<String, Object> createStocktake(StocktakeRequest request, CurrentUser operator) {
		this.businessPeriodGuard.assertWritable(request.businessDate(), BusinessPeriodOperation.CREATE,
				"INVENTORY_STOCKTAKE", null);
		Long id = this.jdbcTemplate.queryForObject("""
				insert into inv_stocktake (
					stocktake_no, business_date, scope_type, warehouse_id, material_id, reason, status,
					idempotency_key, created_by_user_id, created_by_username, updated_by_username
				)
				values (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)
				returning id
				""", Long.class, documentNo("INV-STK"), request.businessDate(), request.scopeType(),
				request.warehouseId(), request.materialId(), request.reason(), request.idempotencyKey(), operator.id(),
				operator.username(), operator.username());
		return stocktake(id, operator);
	}

	@Transactional
	public Map<String, Object> startStocktake(Long id, VersionedActionRequest request, CurrentUser operator) {
		Header header = lockHeader("inv_stocktake", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.UPDATE,
				"INVENTORY_STOCKTAKE", id);
		if (!"DRAFT".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		lockStocktakeRangeForMutation(header.warehouseId(), header.materialId());
		requireNoStocktakeLock(header.warehouseId(), header.materialId());
		this.jdbcTemplate.update("""
				insert into inv_stocktake_range_lock (stocktake_id, warehouse_id, material_id)
				values (?, ?, ?)
				""", id, header.warehouseId(), header.materialId());
		List<Long> balanceIds = this.jdbcTemplate.query("""
				select id
				from inv_stock_balance
				where (?::bigint is null or warehouse_id = ?)
				and (?::bigint is null or material_id = ?)
				and quantity_on_hand > 0
				order by id
				""", (rs, rowNum) -> rs.getLong("id"), header.warehouseId(), header.warehouseId(),
				header.materialId(), header.materialId());
		int lineNo = 1;
		for (Long balanceId : balanceIds) {
			this.jdbcTemplate.update("""
					insert into inv_stocktake_line (
						stocktake_id, balance_id, line_no, warehouse_id, material_id, unit_id, quality_status,
						ownership_type, project_id, batch_id, serial_id, book_quantity
					)
					select ?, id, ?, warehouse_id, material_id, unit_id, quality_status, ownership_type,
					       project_id, batch_id, serial_id, quantity_on_hand
					from inv_stock_balance
					where id = ?
					""", id, lineNo++, balanceId);
		}
		this.jdbcTemplate.update("""
				update inv_stocktake
				set status = 'COUNTING', started_at = now(), updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""", operator.username(), id);
		return stocktake(id, operator);
	}

	@Transactional
	public Map<String, Object> updateStocktakeLines(Long id, StocktakeLineUpdateRequest request, CurrentUser operator) {
		Header header = lockHeader("inv_stocktake", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.UPDATE,
				"INVENTORY_STOCKTAKE", id);
		if (!"COUNTING".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		if (request == null || request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		boolean canEditCost = costVisible(operator);
		for (StocktakeLineCount line : request.lines()) {
			if (line == null || line.id() == null || line.version() == null || line.countedQuantity() == null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if (line.varianceUnitCost() != null && line.varianceUnitCost().compareTo(BigDecimal.ZERO) < 0) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if (line.varianceUnitCost() != null && !canEditCost) {
				throw new BusinessException(ApiErrorCode.INVENTORY_COST_PERMISSION_REQUIRED);
			}
			String varianceReason = trimToNull(line.varianceReason());
			int updated = this.jdbcTemplate.update("""
					update inv_stocktake_line
					set counted_quantity = ?,
					    variance_quantity = ? - book_quantity,
					    variance_unit_cost = case
					        when ? - book_quantity <= 0 then null
					        when cast(? as boolean) then ?::numeric
					        else variance_unit_cost
					    end,
					    variance_reason = case when ? - book_quantity > 0 then ? else null end,
					    updated_at = now(),
					    version = version + 1
					where id = ? and stocktake_id = ? and version = ?
					""", line.countedQuantity(), line.countedQuantity(), line.countedQuantity(), canEditCost,
					line.varianceUnitCost(), line.countedQuantity(), varianceReason, line.id(), id,
					line.version());
			if (updated == 0) {
				throw new BusinessException(ApiErrorCode.CONFLICT);
			}
		}
		this.jdbcTemplate.update("""
				update inv_stocktake
				set updated_by_username = ?, updated_at = now(), version = version + 1
				where id = ?
				""", operator.username(), id);
		return stocktake(id, operator);
	}

	@Transactional
	public Map<String, Object> confirmStocktakeVariance(Long id, VersionedActionRequest request, CurrentUser operator) {
		Header header = lockHeader("inv_stocktake", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.UPDATE,
				"INVENTORY_STOCKTAKE", id);
		if (!"COUNTING".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		Long uncounted = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stocktake_line
				where stocktake_id = ?
				and counted_quantity is null
				""", Long.class, id);
		if (uncounted != null && uncounted > 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_STOCKTAKE_UNCOUNTED_LINE_EXISTS);
		}
		validateStocktakeVarianceRequirements(id, false);
		this.jdbcTemplate.update("""
				update inv_stocktake
				set status = 'RECONCILED', updated_by_username = ?, updated_at = now(), version = version + 1
				where id = ?
				""", operator.username(), id);
		this.auditService.record(operator, "INVENTORY_STOCKTAKE_RECONCILE", "INVENTORY_STOCKTAKE", id,
				header.documentNo(), null);
		return stocktake(id, operator);
	}

	@Transactional
	public Map<String, Object> submitStocktake(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		Header header = lockHeader("inv_stocktake", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.UPDATE,
				"INVENTORY_STOCKTAKE", id);
		if (!"RECONCILED".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		validateStocktakeVarianceRequirements(id, true);
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService.submitInventoryStocktake(id,
				new PlatformApprovalService.ApprovalSubmitRequest(request.version(), request.reason(),
						request.idempotencyKey()),
				operator, servletRequest);
		this.jdbcTemplate.update("""
				update inv_stocktake
				set status = 'SUBMITTED', approval_instance_id = ?, updated_by_username = ?, updated_at = now()
				where id = ?
				""", approval.id(), operator.username(), id);
		this.auditService.record(operator, "INVENTORY_STOCKTAKE_SUBMIT", "INVENTORY_STOCKTAKE", id,
				header.documentNo(), servletRequest);
		return stocktake(id, operator);
	}

	@Transactional
	public Map<String, Object> completeZeroVarianceStocktake(Long id, VersionedActionRequest request,
			CurrentUser operator) {
		Header header = lockHeader("inv_stocktake", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.POST,
				"INVENTORY_STOCKTAKE", id);
		if (!"RECONCILED".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		Long varianceRows = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stocktake_line
				where stocktake_id = ?
				and coalesce(variance_quantity, 0) <> 0
				""", Long.class, id);
		if (varianceRows != null && varianceRows > 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_STOCKTAKE_VARIANCE_REQUIRES_APPROVAL);
		}
		this.jdbcTemplate.update("update inv_stocktake_range_lock set released_at = now() where stocktake_id = ?",
				id);
		this.jdbcTemplate.update("""
				update inv_stocktake
				set status = 'POSTED', posted_at = now(), updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""", operator.username(), id);
		this.auditService.record(operator, "INVENTORY_STOCKTAKE_COMPLETE_ZERO_VARIANCE", "INVENTORY_STOCKTAKE",
				id, header.documentNo(), null);
		return stocktake(id, operator);
	}

	@Transactional
	public Map<String, Object> cancelStocktake(Long id, VersionedActionRequest request, CurrentUser operator) {
		Header header = lockHeader("inv_stocktake", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.CANCEL,
				"INVENTORY_STOCKTAKE", id);
		if (!"DRAFT".equals(header.status()) && !"COUNTING".equals(header.status())
				&& !"RECONCILED".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		this.jdbcTemplate.update("update inv_stocktake_range_lock set released_at = now() where stocktake_id = ?",
				id);
		this.jdbcTemplate.update("""
				update inv_stocktake
				set status = 'CANCELLED', cancelled_at = now(), updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""", operator.username(), id);
		this.auditService.record(operator, "INVENTORY_STOCKTAKE_CANCEL", "INVENTORY_STOCKTAKE", id,
				header.documentNo(), null);
		return stocktake(id, operator);
	}

	@Transactional
	public void postStocktakeFromApproval(Long id, CurrentUser operator) {
		Header header = lockHeader("inv_stocktake", id);
		if ("POSTED".equals(header.status())) {
			return;
		}
		if (!"SUBMITTED".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.POST,
				"INVENTORY_STOCKTAKE", id);
		validateStocktakeVarianceRequirements(id, true);
		for (StocktakeLine line : stocktakeLines(id)) {
			BigDecimal varianceQuantity = stocktakeVarianceQuantity(line);
			if (varianceQuantity.compareTo(BigDecimal.ZERO) == 0) {
				continue;
			}
			InventoryDirection direction = varianceQuantity.compareTo(BigDecimal.ZERO) > 0
					? InventoryDirection.IN : InventoryDirection.OUT;
			InventoryMovementType movementType = direction == InventoryDirection.IN
					? InventoryMovementType.STOCKTAKE_VARIANCE_IN : InventoryMovementType.STOCKTAKE_VARIANCE_OUT;
			InventoryPostingService.PostingResult posting = this.inventoryPostingService.post(
					new InventoryPostingService.PostingRequest(movementType, direction, line.warehouseId(),
							line.materialId(), line.unitId(), varianceQuantity.abs(),
							InventoryQualityStatus.valueOf(line.qualityStatus()), "STOCKTAKE", id, line.id(),
							header.businessDate(), stocktakeMovementReason(header, line), null, operator.username(),
							false, line.batchId(), line.serialId(), stocktakeValuationContext(line, direction)));
			this.jdbcTemplate.update("""
					update inv_stocktake_line
					set variance_movement_id = ?, version = version + 1
					where id = ?
					""", posting.movementId(), line.id());
		}
		this.jdbcTemplate.update("update inv_stocktake_range_lock set released_at = now() where stocktake_id = ?",
				id);
		this.jdbcTemplate.update("""
				update inv_stocktake
				set status = 'POSTED', posted_at = now(), updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""", operator.username(), id);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> valuationAdjustments(String status, String keyword, int page,
			int pageSize, CurrentUser currentUser) {
		boolean costVisible = costVisible(currentUser);
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		if (status != null && !status.isBlank()) {
			conditions.add("status = ?");
			args.add(status.trim().toUpperCase());
		}
		if (keyword != null && !keyword.isBlank()) {
			conditions.add("(adjustment_no ilike ? or reason ilike ?)");
			String pattern = "%" + keyword.trim() + "%";
			args.add(pattern);
			args.add(pattern);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		long total = this.jdbcTemplate.queryForObject("select count(*) from inv_valuation_adjustment " + where,
				Long.class, args.toArray());
		args.add(pageSize);
		args.add((Math.max(page, 1) - 1) * pageSize);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id, adjustment_no as document_no, adjustment_type, business_date, reason, status,
				       posted_at, cancelled_at, created_by_username, created_at, updated_by_username,
				       updated_at, version
				from inv_valuation_adjustment
				%s
				order by id desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", rs.getLong("id"));
			row.put("documentNo", rs.getString("document_no"));
			row.put("adjustmentType", rs.getString("adjustment_type"));
			row.put("businessDate", rs.getObject("business_date", LocalDate.class));
			row.put("reason", rs.getString("reason"));
			row.put("status", rs.getString("status"));
			row.put("postedAt", rs.getObject("posted_at", OffsetDateTime.class));
			row.put("cancelledAt", rs.getObject("cancelled_at", OffsetDateTime.class));
			row.put("createdByName", rs.getString("created_by_username"));
			row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			row.put("updatedByName", rs.getString("updated_by_username"));
			row.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
			row.put("version", rs.getLong("version"));
			row.put("costVisible", costVisible(currentUser));
			row.put("allowedActions", allowedActions("VALUATION_ADJUSTMENT", rs.getString("status"), currentUser,
					rs.getLong("id")));
			return row;
		}, args.toArray());
		return PageResponse.of(items, page, pageSize, total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> valuationAdjustment(Long id, CurrentUser currentUser) {
		return valuationAdjustmentMap(id, currentUser);
	}

	@Transactional
	public Map<String, Object> createValuationAdjustment(ValuationAdjustmentRequest request, CurrentUser operator) {
		this.businessPeriodGuard.assertWritable(request.businessDate(), BusinessPeriodOperation.CREATE,
				"INVENTORY_VALUATION_ADJUSTMENT", null);
		validateValuationAdjustmentRequest(request);
		Long id = this.jdbcTemplate.queryForObject("""
				insert into inv_valuation_adjustment (
					adjustment_no, adjustment_type, business_date, reason, status, idempotency_key,
					created_by_user_id, created_by_username, updated_by_username
				)
				values (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)
				returning id
				""", Long.class, documentNo("INV-VAL"), request.adjustmentType(), request.businessDate(),
				request.reason().trim(), request.idempotencyKey().trim(), operator.id(), operator.username(),
				operator.username());
		insertValuationAdjustmentLines(id, request.lines());
		return valuationAdjustmentMap(id, operator);
	}

	@Transactional
	public Map<String, Object> updateValuationAdjustment(Long id, ValuationAdjustmentRequest request,
			CurrentUser operator) {
		validateValuationAdjustmentRequest(request);
		Header header = lockHeader("inv_valuation_adjustment", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(request.businessDate(), BusinessPeriodOperation.UPDATE,
				"INVENTORY_VALUATION_ADJUSTMENT", id);
		if (!"DRAFT".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		this.jdbcTemplate.update("""
				update inv_valuation_adjustment
				set adjustment_type = ?, business_date = ?, reason = ?, updated_by_username = ?,
				    updated_at = now(), version = version + 1
				where id = ?
				""", request.adjustmentType(), request.businessDate(), request.reason().trim(), operator.username(),
				id);
		this.jdbcTemplate.update("delete from inv_valuation_adjustment_line where adjustment_id = ?", id);
		insertValuationAdjustmentLines(id, request.lines());
		return valuationAdjustmentMap(id, operator);
	}

	@Transactional
	public Map<String, Object> submitValuationAdjustment(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		Header header = lockHeader("inv_valuation_adjustment", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.UPDATE,
				"INVENTORY_VALUATION_ADJUSTMENT", id);
		if (!"DRAFT".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService
			.submitInventoryValuationAdjustment(id,
					new PlatformApprovalService.ApprovalSubmitRequest(request.version(), request.reason(),
							request.idempotencyKey()),
					operator, servletRequest);
		this.jdbcTemplate.update("""
				update inv_valuation_adjustment
				set status = 'SUBMITTED', approval_instance_id = ?, updated_by_username = ?, updated_at = now()
				where id = ?
				""", approval.id(), operator.username(), id);
		this.auditService.record(operator, "INVENTORY_VALUATION_ADJUSTMENT_SUBMIT",
				"INVENTORY_VALUATION_ADJUSTMENT", id, header.documentNo(), servletRequest);
		return valuationAdjustmentMap(id, operator);
	}

	@Transactional
	public Map<String, Object> cancelValuationAdjustment(Long id, VersionedActionRequest request,
			CurrentUser operator) {
		Header header = lockHeader("inv_valuation_adjustment", id);
		requireVersion(header.version(), request.version());
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.CANCEL,
				"INVENTORY_VALUATION_ADJUSTMENT", id);
		if (!"DRAFT".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		this.jdbcTemplate.update("""
				update inv_valuation_adjustment
				set status = 'CANCELLED', cancelled_at = now(), updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""", operator.username(), id);
		this.auditService.record(operator, "INVENTORY_VALUATION_ADJUSTMENT_CANCEL",
				"INVENTORY_VALUATION_ADJUSTMENT", id, header.documentNo(), null);
		return valuationAdjustmentMap(id, operator);
	}

	@Transactional
	public Map<String, Object> withdrawValuationAdjustment(Long id, VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return withdrawApprovalBackToDraft("inv_valuation_adjustment", id, request, operator, servletRequest,
				(detailId) -> valuationAdjustmentMap(detailId, operator));
	}

	@Transactional
	public void postValuationAdjustmentFromApproval(Long id, CurrentUser operator) {
		Header header = lockHeader("inv_valuation_adjustment", id);
		if ("POSTED".equals(header.status())) {
			return;
		}
		if (!"SUBMITTED".equals(header.status())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(header.businessDate(), BusinessPeriodOperation.POST,
				"INVENTORY_VALUATION_ADJUSTMENT", id);
		for (ValuationAdjustmentLine line : valuationAdjustmentLines(id)) {
			if ("LEGACY_OPENING".equals(header.adjustmentType())) {
				if (!"PUBLIC".equals(line.ownershipType())) {
					throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
				}
				InventoryValuationService.ValuationResult result = this.inventoryValuationService
					.applyLegacyPublicOpeningAdjustment(line.materialId(), line.quantity(), line.unitCost(),
							line.adjustmentAmount(), "VALUATION_ADJUSTMENT", id, line.id(), header.businessDate(),
							header.reason(), operator.username(), OffsetDateTime.now());
				this.jdbcTemplate.update("""
						update inv_valuation_adjustment_line
						set value_movement_id = ?, version = version + 1
						where id = ?
						""", result.valueMovementId(), line.id());
				continue;
			}
			if (!"PROVISIONAL_REVALUATION".equals(header.adjustmentType())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			InventoryValuationService.ValuationResult result = "PROJECT".equals(line.ownershipType())
					? this.inventoryValuationService.applyProjectProvisionalRevaluationAdjustment(line.projectId(),
							line.materialId(), line.costLayerId(), line.unitCost(), line.adjustmentAmount(),
							"VALUATION_ADJUSTMENT", id, line.id(), header.businessDate(), header.reason(),
							operator.username(), OffsetDateTime.now())
					: this.inventoryValuationService.applyPublicProvisionalRevaluationAdjustment(line.materialId(),
							line.unitCost(), line.adjustmentAmount(), "VALUATION_ADJUSTMENT", id, line.id(),
							header.businessDate(), header.reason(), operator.username(), OffsetDateTime.now());
			this.jdbcTemplate.update("""
					update inv_valuation_adjustment_line
					set value_movement_id = ?, version = version + 1
					where id = ?
					""", result.valueMovementId(), line.id());
		}
		this.jdbcTemplate.update("""
				update inv_valuation_adjustment
				set status = 'POSTED', posted_at = now(), updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""", operator.username(), id);
	}

	@Transactional
	public void reopenAfterApprovalTerminal(String sceneCode, Long objectId, CurrentUser operator) {
		if ("INVENTORY_OWNERSHIP_CONVERSION_POST".equals(sceneCode)) {
			reopenSubmittedDocument("inv_ownership_conversion", objectId, "DRAFT", operator);
			return;
		}
		if ("INVENTORY_STOCKTAKE_VARIANCE_POST".equals(sceneCode)) {
			reopenSubmittedDocument("inv_stocktake", objectId, "RECONCILED", operator);
			releaseStocktakeRange(objectId);
			return;
		}
		if ("INVENTORY_VALUATION_ADJUSTMENT_POST".equals(sceneCode)) {
			reopenSubmittedDocument("inv_valuation_adjustment", objectId, "DRAFT", operator);
		}
	}

	private void reopenSubmittedDocument(String tableName, Long id, String targetStatus, CurrentUser operator) {
		this.jdbcTemplate.update("""
				update %s
				set status = ?, approval_instance_id = null, updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				and status = 'SUBMITTED'
				""".formatted(tableName), targetStatus, operator.username(), id);
	}

	private void releaseStocktakeRange(Long stocktakeId) {
		this.jdbcTemplate.update("""
				update inv_stocktake_range_lock
				set released_at = now()
				where stocktake_id = ?
				and released_at is null
				""", stocktakeId);
	}

	private PageResponse<Map<String, Object>> documentPage(String tableName, String noColumn, String status,
			String keyword, int page, int pageSize, String documentType, CurrentUser currentUser) {
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		if (status != null && !status.isBlank()) {
			conditions.add("status = ?");
			args.add(status.trim().toUpperCase());
		}
		if (keyword != null && !keyword.isBlank()) {
			conditions.add("(%s ilike ? or reason ilike ?)".formatted(noColumn));
			String pattern = "%" + keyword.trim() + "%";
			args.add(pattern);
			args.add(pattern);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		long total = this.jdbcTemplate.queryForObject("select count(*) from %s %s".formatted(tableName, where),
				Long.class, args.toArray());
		args.add(pageSize);
		args.add((Math.max(page, 1) - 1) * pageSize);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id, %s as document_no, business_date, reason, status, posted_at, cancelled_at,
				       created_by_username, created_at, updated_by_username, updated_at, version
				from %s
				%s
				order by id desc
				limit ? offset ?
				""".formatted(noColumn, tableName, where), (rs, rowNum) -> documentRow(rs.getLong("id"),
				rs.getString("document_no"), rs.getObject("business_date", LocalDate.class),
				rs.getString("reason"), rs.getString("status"), rs.getObject("posted_at", OffsetDateTime.class),
				rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("created_by_username"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getString("updated_by_username"),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"), documentType,
				currentUser),
				args.toArray());
		return PageResponse.of(items, page, pageSize, total);
	}

	private Map<String, Object> documentRow(Long id, String documentNo, LocalDate businessDate, String reason,
			String status, OffsetDateTime postedAt, OffsetDateTime cancelledAt, String createdByName,
			OffsetDateTime createdAt, String updatedByName, OffsetDateTime updatedAt, Long version,
			String documentType, CurrentUser currentUser) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id);
		row.put("documentNo", documentNo);
		row.put("businessDate", businessDate);
		row.put("reason", reason);
		row.put("status", status);
		row.put("postedAt", postedAt);
		row.put("cancelledAt", cancelledAt);
		row.put("createdByName", createdByName);
		row.put("createdAt", createdAt);
		row.put("updatedByName", updatedByName);
		row.put("updatedAt", updatedAt);
		row.put("version", version);
		row.put("costVisible", costVisible(currentUser));
		row.put("allowedActions", allowedActions(documentType, status, currentUser, id));
		row.put("approvalSummary", approvalSummaryForDocument(documentType, id));
		row.put("keyInfoSummary", Map.of("documentNo", documentNo, "businessDate", businessDate, "status", status));
		row.put("amountImpactSummary", costVisible(currentUser) ? Map.of() : null);
		return row;
	}

	private void insertWarehouseTransferLines(Long id, List<WarehouseTransferLineRequest> lines) {
		for (WarehouseTransferLineRequest line : lines) {
			this.jdbcTemplate.update("""
					insert into inv_warehouse_transfer_line (
						transfer_id, line_no, source_warehouse_id, target_warehouse_id, ownership_type, project_id,
						material_id, unit_id, quality_status, batch_id, serial_id, quantity, source_cost_layer_id
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", id, line.lineNo(), line.sourceWarehouseId(), line.targetWarehouseId(),
					defaultOwnership(line.ownershipType()), publicProject(line.ownershipType(), line.projectId()),
					line.materialId(), line.unitId(), defaultQuality(line.qualityStatus()), line.batchId(),
					line.serialId(), line.quantity(), projectSourceCostLayerId(line));
		}
	}

	private void insertOwnershipConversionLines(Long id, List<OwnershipConversionLineRequest> lines) {
		for (OwnershipConversionLineRequest line : lines) {
			this.jdbcTemplate.update("""
					insert into inv_ownership_conversion_line (
						conversion_id, line_no, source_ownership_type, source_project_id, target_ownership_type,
						target_project_id, source_warehouse_id, target_warehouse_id, material_id, unit_id,
						quality_status, batch_id, serial_id, quantity, source_unit_cost, source_cost_layer_id
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", id, line.lineNo(), defaultOwnership(line.sourceOwnershipType()),
					publicProject(line.sourceOwnershipType(), line.sourceProjectId()),
					defaultOwnership(line.targetOwnershipType()),
					publicProject(line.targetOwnershipType(), line.targetProjectId()), line.sourceWarehouseId(),
					line.targetWarehouseId(), line.materialId(), line.unitId(), defaultQuality(line.qualityStatus()),
					line.batchId(), line.serialId(), line.quantity(), line.sourceUnitCost(), line.sourceCostLayerId());
		}
	}

	private Map<String, Object> withdrawApprovalBackToDraft(String tableName, Long id, VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest, Function<Long, Map<String, Object>> detail) {
		Header header = lockHeader(tableName, id);
		requireVersion(header.version(), request.version());
		if (!"SUBMITTED".equals(header.status()) || header.approvalInstanceId() == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID);
		}
		Long approvalVersion = this.jdbcTemplate.queryForObject("""
				select version
				from platform_approval_instance
				where id = ?
				""", Long.class, header.approvalInstanceId());
		this.approvalService.withdraw(header.approvalInstanceId(),
				new PlatformApprovalService.ApprovalActionRequest(approvalVersion, request.reason(),
						request.idempotencyKey()),
				operator, servletRequest);
		this.jdbcTemplate.update("""
				update %s
				set status = 'DRAFT', approval_instance_id = null, updated_by_username = ?, updated_at = now(),
				    version = version + 1
				where id = ?
				""".formatted(tableName), operator.username(), id);
		this.auditService.record(operator, tableAuditPrefix(tableName) + "_WITHDRAW", tableTargetType(tableName),
				id, header.documentNo(), servletRequest);
		return detail.apply(id);
	}

	private Map<String, Object> stocktakeScope(Long id) {
		return this.jdbcTemplate.query("""
				select scope_type
				from inv_stocktake
				where id = ?
				""", (rs, rowNum) -> Map.<String, Object>of("scopeType", rs.getString("scope_type")), id)
			.stream()
			.findFirst()
			.orElse(Map.of());
	}

	private Map<String, Object> stocktakeLineSummary(Long id) {
		return this.jdbcTemplate.queryForObject("""
				select count(*) as total_lines,
				       count(*) filter (where counted_quantity is not null) as counted_lines,
				       count(*) filter (where coalesce(variance_quantity, 0) <> 0) as variance_lines,
				       count(*) filter (where coalesce(variance_quantity, 0) > 0) as positive_variance_lines,
				       count(*) filter (where coalesce(variance_quantity, 0) < 0) as negative_variance_lines,
				       count(*) filter (where counted_quantity is null) as uncounted_lines
				from inv_stocktake_line
				where stocktake_id = ?
				""", (rs, rowNum) -> {
			Map<String, Object> summary = new LinkedHashMap<>();
			summary.put("totalLines", rs.getLong("total_lines"));
			summary.put("countedLines", rs.getLong("counted_lines"));
			summary.put("varianceLines", rs.getLong("variance_lines"));
			summary.put("positiveVarianceLines", rs.getLong("positive_variance_lines"));
			summary.put("negativeVarianceLines", rs.getLong("negative_variance_lines"));
			summary.put("uncountedLines", rs.getLong("uncounted_lines"));
			return summary;
		}, id);
	}

	private List<String> allowedActions(String documentType, String status, CurrentUser currentUser, Long documentId) {
		if (currentUser == null) {
			return List.of();
		}
		List<String> permissions = currentUser.permissions();
		return switch (documentType) {
			case "WAREHOUSE_TRANSFER" -> {
				if (!"DRAFT".equals(status)) {
					yield List.of();
				}
				List<String> actions = new ArrayList<>();
				addIfPermitted(actions, permissions, "inventory:warehouse-transfer:update", "UPDATE");
				addIfPermitted(actions, permissions, "inventory:warehouse-transfer:post", "POST");
				addIfPermitted(actions, permissions, "inventory:warehouse-transfer:cancel", "CANCEL");
				yield actions;
			}
			case "OWNERSHIP_CONVERSION" -> approvalDocumentActions(status, permissions,
					"inventory:ownership-conversion:update", "inventory:ownership-conversion:submit",
					"inventory:ownership-conversion:withdraw", "inventory:ownership-conversion:cancel");
			case "VALUATION_ADJUSTMENT" -> approvalDocumentActions(status, permissions,
					"inventory:valuation-adjustment:update", "inventory:valuation-adjustment:submit",
					"inventory:valuation-adjustment:withdraw", "inventory:valuation-adjustment:cancel");
			case "STOCKTAKE" -> stocktakeActions(status, permissions, documentId);
			default -> List.of();
		};
	}

	private List<String> approvalDocumentActions(String status, List<String> permissions, String updatePermission,
			String submitPermission, String withdrawPermission, String cancelPermission) {
		List<String> actions = new ArrayList<>();
		if ("DRAFT".equals(status)) {
			addIfPermitted(actions, permissions, updatePermission, "UPDATE");
			addIfPermitted(actions, permissions, submitPermission, "SUBMIT_APPROVAL");
			addIfPermitted(actions, permissions, cancelPermission, "CANCEL");
		}
		else if ("SUBMITTED".equals(status)) {
			addIfPermitted(actions, permissions, withdrawPermission, "WITHDRAW");
		}
		return actions;
	}

	private List<String> stocktakeActions(String status, List<String> permissions, Long documentId) {
		List<String> actions = new ArrayList<>();
		if ("DRAFT".equals(status)) {
			addIfPermitted(actions, permissions, "inventory:stocktake:update", "START");
			addIfPermitted(actions, permissions, "inventory:stocktake:cancel", "CANCEL");
		}
		else if ("COUNTING".equals(status)) {
			addIfPermitted(actions, permissions, "inventory:stocktake:update", "UPDATE_LINES");
			addIfPermitted(actions, permissions, "inventory:stocktake:update", "RECONCILE");
			addIfPermitted(actions, permissions, "inventory:stocktake:cancel", "CANCEL");
		}
		else if ("RECONCILED".equals(status)) {
			if (hasStocktakeVariance(documentId)) {
				addIfPermitted(actions, permissions, "inventory:stocktake:submit", "SUBMIT_APPROVAL");
			}
			else {
				addIfPermitted(actions, permissions, "inventory:stocktake:update", "COMPLETE_ZERO_VARIANCE");
			}
			addIfPermitted(actions, permissions, "inventory:stocktake:cancel", "CANCEL");
		}
		return actions;
	}

	private void addIfPermitted(List<String> actions, List<String> permissions, String permission, String action) {
		if (permissions.contains(permission)) {
			actions.add(action);
		}
	}

	private boolean hasStocktakeVariance(Long documentId) {
		if (documentId == null) {
			return true;
		}
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stocktake_line
				where stocktake_id = ?
				and coalesce(variance_quantity, 0) <> 0
				""", Long.class, documentId);
		return count != null && count > 0;
	}

	private void validateStocktakeVarianceRequirements(Long stocktakeId, boolean requireProjectEvidence) {
		boolean projectPositiveVariance = false;
		for (StocktakeLine line : stocktakeLines(stocktakeId)) {
			StocktakeValuationRequirement requirement = valuationRequirement(line);
			if ("EXPLICIT_UNIT_COST".equals(requirement.mode()) && line.varianceUnitCost() == null) {
				throw new BusinessException(ApiErrorCode.INVENTORY_VALUATION_UNIT_COST_REQUIRED);
			}
			if ("PROJECT_EXPLICIT_UNIT_COST".equals(requirement.mode())) {
				projectPositiveVariance = true;
				if (line.varianceUnitCost() == null) {
					throw new BusinessException(ApiErrorCode.INVENTORY_VALUATION_UNIT_COST_REQUIRED);
				}
				if (!hasText(line.varianceReason())) {
					throw new BusinessException(ApiErrorCode.INVENTORY_STOCKTAKE_VARIANCE_REASON_REQUIRED);
				}
			}
		}
		if (requireProjectEvidence && projectPositiveVariance && !hasActiveStocktakeEvidence(stocktakeId)) {
			throw new BusinessException(ApiErrorCode.INVENTORY_STOCKTAKE_EVIDENCE_REQUIRED);
		}
	}

	private boolean hasActiveStocktakeEvidence(Long stocktakeId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_business_attachment a
				join platform_file_object f on f.id = a.file_id
				where a.object_type = 'INVENTORY_STOCKTAKE'
				  and a.object_id = ?
				  and a.status = 'AVAILABLE'
				  and f.status = 'AVAILABLE'
				""", Long.class, stocktakeId);
		return count != null && count > 0;
	}

	private String tableAuditPrefix(String tableName) {
		return switch (tableName) {
			case "inv_ownership_conversion" -> "INVENTORY_OWNERSHIP_CONVERSION";
			case "inv_valuation_adjustment" -> "INVENTORY_VALUATION_ADJUSTMENT";
			default -> "INVENTORY_DOCUMENT";
		};
	}

	private String tableTargetType(String tableName) {
		return tableAuditPrefix(tableName);
	}

	private void validateLines(List<?> lines) {
		if (lines == null || lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateWarehouseTransferLines(List<WarehouseTransferLineRequest> lines) {
		validateLines(lines);
	}

	private Long projectSourceCostLayerId(WarehouseTransferLineRequest line) {
		return "PROJECT".equals(defaultOwnership(line.ownershipType())) ? line.sourceCostLayerId() : null;
	}

	private void lockWarehouseTransferPostingScopes(List<TransferLine> lines) {
		List<InventoryPostingService.PostingScope> scopes = new ArrayList<>();
		for (TransferLine line : lines) {
			scopes.add(new InventoryPostingService.PostingScope(line.sourceWarehouseId(), line.materialId()));
			scopes.add(new InventoryPostingService.PostingScope(line.targetWarehouseId(), line.materialId()));
		}
		this.inventoryPostingService.lockPostingScopes(scopes);
	}

	private void lockOwnershipConversionPostingScopes(List<OwnershipLine> lines) {
		List<InventoryPostingService.PostingScope> scopes = new ArrayList<>();
		for (OwnershipLine line : lines) {
			scopes.add(new InventoryPostingService.PostingScope(line.sourceWarehouseId(), line.materialId()));
			scopes.add(new InventoryPostingService.PostingScope(line.targetWarehouseId(), line.materialId()));
		}
		this.inventoryPostingService.lockPostingScopes(scopes);
	}

	private Map<Long, Long> resolveOwnershipConversionSourceLayers(List<OwnershipLine> lines) {
		Map<Long, Long> sourceCostLayerIds = new LinkedHashMap<>();
		for (OwnershipLine line : lines) {
			Long sourceCostLayerId = line.sourceCostLayerId() == null
					? sourceCostLayerId(line.sourceOwnershipType(), line.sourceProjectId(), line.sourceWarehouseId(),
							line.materialId(), line.qualityStatus(), line.batchId(), line.serialId())
					: line.sourceCostLayerId();
			sourceCostLayerIds.put(line.id(), sourceCostLayerId);
		}
		return sourceCostLayerIds;
	}

	private void ensureWarehouseTransferSourceLayer(TransferLine line) {
		if (!"PROJECT".equals(line.ownershipType())) {
			return;
		}
		if (line.sourceCostLayerId() == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
		List<Long> balances = this.jdbcTemplate.query("""
				select id
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				and ownership_type = 'PROJECT'
				and project_id = ?
				and cost_layer_id = ?
				and quantity_on_hand >= ?
				for update
				""", (rs, rowNum) -> rs.getLong("id"), line.sourceWarehouseId(), line.materialId(),
				defaultQuality(line.qualityStatus()), line.batchId(), line.serialId(), line.projectId(),
				line.sourceCostLayerId(), line.quantity());
		if (balances.isEmpty()) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
	}

	private void requireNoStocktakeLock(Long warehouseId, Long materialId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stocktake_range_lock
				where released_at is null
				and (warehouse_id is null or warehouse_id = ?)
				and (material_id is null or material_id = ?)
				""", Long.class, warehouseId, materialId);
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_STOCKTAKE_RANGE_LOCKED);
		}
	}

	private void lockStocktakeRangeForMutation(Long warehouseId, Long materialId) {
		long warehouseKey = warehouseId == null ? 0L : warehouseId;
		this.jdbcTemplate.query("select pg_advisory_xact_lock(230023, ?::int)",
				(rs) -> {}, Math.toIntExact(Math.floorMod(warehouseKey, Integer.MAX_VALUE)));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> warehouseTransfer(Long id) {
		return warehouseTransfer(id, null);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> warehouseTransfer(Long id, CurrentUser currentUser) {
		Header header = header("inv_warehouse_transfer", id);
		boolean costVisible = costVisible(currentUser);
		Map<String, Object> result = headerMap(header);
		result.put("costVisible", costVisible);
		result.put("allowedActions", allowedActions("WAREHOUSE_TRANSFER", header.status(), currentUser, id));
		result.put("lines", transferLines(id).stream().map((line) -> lineMap(line, costVisible)).toList());
		return result;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> ownershipConversion(Long id) {
		return ownershipConversion(id, null);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> ownershipConversion(Long id, CurrentUser currentUser) {
		Header header = header("inv_ownership_conversion", id);
		boolean costVisible = costVisible(currentUser);
		Map<String, Object> result = headerMap(header);
		result.put("costVisible", costVisible);
		result.put("allowedActions", allowedActions("OWNERSHIP_CONVERSION", header.status(), currentUser, id));
		result.put("lines", ownershipLines(id).stream().map((line) -> lineMap(line, costVisible)).toList());
		result.put("approvalSummary", approvalSummary("INVENTORY_OWNERSHIP_CONVERSION_POST",
				"INVENTORY_OWNERSHIP_CONVERSION", id));
		return result;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> stocktake(Long id) {
		return stocktake(id, null);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> stocktake(Long id, CurrentUser currentUser) {
		Header header = header("inv_stocktake", id);
		boolean costVisible = costVisible(currentUser);
		Map<String, Object> result = headerMap(header);
		result.put("scopeType", stocktakeScope(id).get("scopeType"));
		result.put("warehouseId", header.warehouseId());
		result.put("materialId", header.materialId());
		result.put("costVisible", costVisible);
		result.put("allowedActions", allowedActions("STOCKTAKE", header.status(), currentUser, id));
		result.put("lineSummary", stocktakeLineSummary(id));
		result.put("approvalSummary", approvalSummary("INVENTORY_STOCKTAKE_VARIANCE_POST", "INVENTORY_STOCKTAKE",
				id));
		return result;
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> stocktakeLines(Long id, int page, int pageSize,
			CurrentUser currentUser) {
		header("inv_stocktake", id);
		boolean costVisible = costVisible(currentUser);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stocktake_line
				where stocktake_id = ?
				""", Long.class, id);
		int safePageSize = Math.max(pageSize, 1);
		int safePage = Math.max(page, 1);
		List<Map<String, Object>> items = stocktakeLines(id, safePageSize, (safePage - 1) * safePageSize)
			.stream()
			.map((line) -> lineMap(line, costVisible))
			.toList();
		return PageResponse.of(items, safePage, safePageSize, total);
	}

	private Map<String, Object> valuationAdjustmentMap(Long id, CurrentUser currentUser) {
		Header header = header("inv_valuation_adjustment", id);
		boolean costVisible = costVisible(currentUser);
		Map<String, Object> result = headerMap(header);
		result.put("adjustmentType", header.adjustmentType());
		result.put("costVisible", costVisible);
		result.put("allowedActions", allowedActions("VALUATION_ADJUSTMENT", header.status(), currentUser, id));
		result.put("lines", valuationAdjustmentLines(id).stream().map((line) -> lineMap(line, costVisible)).toList());
		result.put("approvalSummary", approvalSummary("INVENTORY_VALUATION_ADJUSTMENT_POST",
				"INVENTORY_VALUATION_ADJUSTMENT", id));
		return result;
	}

	private void validateValuationAdjustmentRequest(ValuationAdjustmentRequest request) {
		if (request == null || request.businessDate() == null || request.adjustmentType() == null
				|| request.reason() == null || request.reason().isBlank() || request.idempotencyKey() == null
				|| request.idempotencyKey().isBlank()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (!"LEGACY_OPENING".equals(request.adjustmentType())
				&& !"PROVISIONAL_REVALUATION".equals(request.adjustmentType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateLines(request.lines());
		int lineNo = 1;
		for (ValuationAdjustmentLineRequest line : request.lines()) {
			if (line == null || line.materialId() == null || line.adjustmentAmount() == null
					|| line.adjustmentAmount().compareTo(BigDecimal.ZERO) < 0) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			String ownershipType = defaultOwnership(line.ownershipType());
			if (!"PUBLIC".equals(ownershipType) && !"PROJECT".equals(ownershipType)) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if ("LEGACY_OPENING".equals(request.adjustmentType())
					&& (!"PUBLIC".equals(ownershipType) || line.quantity() == null
							|| line.quantity().compareTo(BigDecimal.ZERO) <= 0)) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if ("PROVISIONAL_REVALUATION".equals(request.adjustmentType()) && "PROJECT".equals(ownershipType)
					&& (line.projectId() == null || line.costLayerId() == null)) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if (line.unitCost() != null && line.unitCost().compareTo(BigDecimal.ZERO) < 0) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if (line.lineNo() != null && line.lineNo() < 1) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			lineNo++;
		}
	}

	private void insertValuationAdjustmentLines(Long adjustmentId, List<ValuationAdjustmentLineRequest> lines) {
		int fallbackLineNo = 1;
		for (ValuationAdjustmentLineRequest line : lines) {
			this.jdbcTemplate.update("""
					insert into inv_valuation_adjustment_line (
						adjustment_id, line_no, ownership_type, project_id, material_id, quantity, unit_cost,
						adjustment_amount, cost_layer_id
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", adjustmentId, line.lineNo() == null ? fallbackLineNo : line.lineNo(),
					defaultOwnership(line.ownershipType()), publicProject(line.ownershipType(), line.projectId()),
					line.materialId(), line.quantity(), line.unitCost(), line.adjustmentAmount(), line.costLayerId());
			fallbackLineNo++;
		}
	}

	private Map<String, Object> approvalSummary(String sceneCode, String objectType, Long objectId) {
		return this.jdbcTemplate.query("""
				select id, scene_code, status, submitted_at, version
				from platform_approval_instance
				where scene_code = ?
				and business_object_type = ?
				and business_object_id = ?
				order by submitted_at desc, id desc
				limit 1
				""", (rs, rowNum) -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", rs.getLong("id"));
			row.put("sceneCode", rs.getString("scene_code"));
			row.put("status", rs.getString("status"));
			row.put("submittedAt", rs.getObject("submitted_at", OffsetDateTime.class));
			row.put("version", rs.getLong("version"));
			return row;
				}, sceneCode, objectType, objectId).stream().findFirst().orElse(null);
	}

	private Map<String, Object> approvalSummaryForDocument(String documentType, Long objectId) {
		return switch (documentType) {
			case "OWNERSHIP_CONVERSION" -> approvalSummary("INVENTORY_OWNERSHIP_CONVERSION_POST",
					"INVENTORY_OWNERSHIP_CONVERSION", objectId);
			case "STOCKTAKE" -> approvalSummary("INVENTORY_STOCKTAKE_VARIANCE_POST", "INVENTORY_STOCKTAKE",
					objectId);
			case "VALUATION_ADJUSTMENT" -> approvalSummary("INVENTORY_VALUATION_ADJUSTMENT_POST",
					"INVENTORY_VALUATION_ADJUSTMENT", objectId);
			default -> null;
		};
	}

	private Header lockHeader(String tableName, Long id) {
		return queryHeader(tableName, id, " for update");
	}

	private Header header(String tableName, Long id) {
		return queryHeader(tableName, id, "");
	}

	private Header queryHeader(String tableName, Long id, String lockSql) {
		if ("inv_stocktake".equals(tableName)) {
			return this.jdbcTemplate.query("""
					select id, stocktake_no as document_no, business_date, reason, status, version,
					       warehouse_id, material_id, approval_instance_id
					from inv_stocktake
					where id = ?
					%s
					""".formatted(lockSql), (rs, rowNum) -> new Header(rs.getLong("id"),
					rs.getString("document_no"), rs.getObject("business_date", LocalDate.class),
					rs.getString("reason"), rs.getString("status"), rs.getLong("version"),
					nullableLong(rs, "warehouse_id"), nullableLong(rs, "material_id"), null,
					nullableLong(rs, "approval_instance_id")), id)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_NOT_FOUND));
		}
		if ("inv_valuation_adjustment".equals(tableName)) {
			return this.jdbcTemplate.query("""
					select id, adjustment_no as document_no, adjustment_type, business_date, reason, status,
					       version, null::bigint as warehouse_id, null::bigint as material_id, approval_instance_id
					from inv_valuation_adjustment
					where id = ?
					%s
					""".formatted(lockSql), (rs, rowNum) -> new Header(rs.getLong("id"),
					rs.getString("document_no"), rs.getObject("business_date", LocalDate.class),
					rs.getString("reason"), rs.getString("status"), rs.getLong("version"),
					nullableLong(rs, "warehouse_id"), nullableLong(rs, "material_id"),
					rs.getString("adjustment_type"), nullableLong(rs, "approval_instance_id")), id)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_NOT_FOUND));
		}
		if ("inv_ownership_conversion".equals(tableName)) {
			return this.jdbcTemplate.query("""
					select id, conversion_no as document_no, business_date, reason, status, version,
					       null::bigint as warehouse_id, null::bigint as material_id, approval_instance_id
					from inv_ownership_conversion
					where id = ?
					%s
					""".formatted(lockSql), (rs, rowNum) -> new Header(rs.getLong("id"),
					rs.getString("document_no"), rs.getObject("business_date", LocalDate.class),
					rs.getString("reason"), rs.getString("status"), rs.getLong("version"),
					nullableLong(rs, "warehouse_id"), nullableLong(rs, "material_id"), null,
					nullableLong(rs, "approval_instance_id")), id)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_NOT_FOUND));
		}
		return this.jdbcTemplate.query("""
				select id, %s_no as document_no, business_date, reason, status, version,
				       null::bigint as warehouse_id, null::bigint as material_id
				from %s
				where id = ?
				%s
				""".formatted(tablePrefix(tableName), tableName, lockSql), (rs, rowNum) -> new Header(
				rs.getLong("id"), rs.getString("document_no"), rs.getObject("business_date", LocalDate.class),
				rs.getString("reason"), rs.getString("status"), rs.getLong("version"), nullableLong(rs, "warehouse_id"),
				nullableLong(rs, "material_id"), null, null), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_DOCUMENT_NOT_FOUND));
	}

	private String tablePrefix(String tableName) {
		return switch (tableName) {
			case "inv_warehouse_transfer" -> "transfer";
			case "inv_ownership_conversion" -> "conversion";
			case "inv_stocktake" -> "stocktake";
			default -> "adjustment";
		};
	}

	private List<TransferLine> transferLines(Long transferId) {
		return this.jdbcTemplate.query("""
				select id, line_no, source_warehouse_id, target_warehouse_id, ownership_type, project_id,
				       material_id, unit_id, quality_status, batch_id, serial_id, quantity, source_cost_layer_id
				from inv_warehouse_transfer_line
				where transfer_id = ?
				order by line_no, id
				""", (rs, rowNum) -> new TransferLine(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("source_warehouse_id"), rs.getLong("target_warehouse_id"), rs.getString("ownership_type"),
				nullableLong(rs, "project_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getString("quality_status"), nullableLong(rs, "batch_id"), nullableLong(rs, "serial_id"),
				rs.getBigDecimal("quantity"), nullableLong(rs, "source_cost_layer_id")), transferId);
	}

	private List<OwnershipLine> ownershipLines(Long conversionId) {
		return this.jdbcTemplate.query("""
				select id, line_no, source_ownership_type, source_project_id, target_ownership_type,
				       target_project_id, source_warehouse_id, target_warehouse_id, material_id, unit_id,
				       quality_status, batch_id, serial_id, quantity, source_unit_cost, source_cost_layer_id
				from inv_ownership_conversion_line
				where conversion_id = ?
				order by line_no, id
				""", (rs, rowNum) -> new OwnershipLine(rs.getLong("id"), rs.getInt("line_no"),
				rs.getString("source_ownership_type"), nullableLong(rs, "source_project_id"),
				rs.getString("target_ownership_type"), nullableLong(rs, "target_project_id"),
				rs.getLong("source_warehouse_id"), rs.getLong("target_warehouse_id"), rs.getLong("material_id"),
				rs.getLong("unit_id"), rs.getString("quality_status"), nullableLong(rs, "batch_id"),
				nullableLong(rs, "serial_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("source_unit_cost"),
				nullableLong(rs, "source_cost_layer_id")), conversionId);
	}

	private List<StocktakeLine> stocktakeLines(Long stocktakeId) {
		return stocktakeLines(stocktakeId, null, null);
	}

	private List<StocktakeLine> stocktakeLines(Long stocktakeId, Integer limit, Integer offset) {
		List<Object> args = new ArrayList<>();
		args.add(stocktakeId);
		String pageSql = "";
		if (limit != null && offset != null) {
			pageSql = " limit ? offset ?";
			args.add(limit);
			args.add(offset);
		}
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.warehouse_id, l.material_id, l.unit_id, l.quality_status,
				       l.ownership_type, l.project_id, l.batch_id, l.serial_id, l.book_quantity,
				       l.counted_quantity, coalesce(l.variance_quantity, 0) as variance_quantity,
				       l.variance_unit_cost, l.variance_reason, b.cost_layer_id, l.version
				from inv_stocktake_line l
				join inv_stock_balance b on b.id = l.balance_id
				where l.stocktake_id = ?
				order by l.line_no, l.id
				%s
				""".formatted(pageSql), (rs, rowNum) -> new StocktakeLine(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("warehouse_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getString("quality_status"), rs.getString("ownership_type"), nullableLong(rs, "project_id"),
				nullableLong(rs, "batch_id"), nullableLong(rs, "serial_id"), rs.getBigDecimal("book_quantity"),
				rs.getBigDecimal("counted_quantity"), rs.getBigDecimal("variance_quantity"),
				rs.getBigDecimal("variance_unit_cost"), rs.getString("variance_reason"),
				nullableLong(rs, "cost_layer_id"), rs.getLong("version")), args.toArray());
	}

	private List<ValuationAdjustmentLine> valuationAdjustmentLines(Long adjustmentId) {
		return this.jdbcTemplate.query("""
				select id, line_no, ownership_type, project_id, material_id, quantity, unit_cost,
				       adjustment_amount, cost_layer_id, value_movement_id, version
				from inv_valuation_adjustment_line
				where adjustment_id = ?
				order by line_no, id
				""", (rs, rowNum) -> new ValuationAdjustmentLine(rs.getLong("id"), rs.getInt("line_no"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"), rs.getLong("material_id"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_cost"), rs.getBigDecimal("adjustment_amount"),
				nullableLong(rs, "cost_layer_id"), nullableLong(rs, "value_movement_id"), rs.getLong("version")),
				adjustmentId);
	}

	private Map<String, Object> headerMap(Header header) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", header.id());
		result.put("documentNo", header.documentNo());
		result.put("adjustmentType", header.adjustmentType());
		result.put("businessDate", header.businessDate());
		result.put("reason", header.reason());
		result.put("status", header.status());
		result.put("version", header.version());
		return result;
	}

	private Map<String, Object> lineMap(TransferLine line) {
		return lineMap(line, true);
	}

	private Map<String, Object> lineMap(TransferLine line, boolean costVisible) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", line.id());
		row.put("lineNo", line.lineNo());
		row.put("sourceWarehouseId", line.sourceWarehouseId());
		row.put("sourceWarehouseName", warehouseName(line.sourceWarehouseId()));
		row.put("targetWarehouseId", line.targetWarehouseId());
		row.put("targetWarehouseName", warehouseName(line.targetWarehouseId()));
		row.put("ownershipType", line.ownershipType());
		row.put("projectId", line.projectId());
		putProject(row, "", line.projectId());
		row.put("materialId", line.materialId());
		putMaterial(row, line.materialId());
		row.put("unitId", line.unitId());
		row.put("unitName", unitName(line.unitId()));
		row.put("qualityStatus", line.qualityStatus());
		row.put("batchId", line.batchId());
		row.put("batchNo", batchNo(line.batchId()));
		row.put("serialId", line.serialId());
		row.put("serialNo", serialNo(line.serialId()));
		row.put("quantity", decimal(line.quantity()));
		if (costVisible) {
			row.put("sourceCostLayerId", line.sourceCostLayerId());
		}
		return row;
	}

	private Map<String, Object> lineMap(OwnershipLine line) {
		return lineMap(line, true);
	}

	private Map<String, Object> lineMap(OwnershipLine line, boolean costVisible) {
		Map<String, Object> row = lineMap(new TransferLine(line.id(), line.lineNo(), line.sourceWarehouseId(),
				line.targetWarehouseId(), line.targetOwnershipType(), line.targetProjectId(), line.materialId(),
				line.unitId(), line.qualityStatus(), line.batchId(), line.serialId(), line.quantity(),
				line.sourceCostLayerId()), costVisible);
		row.put("sourceOwnershipType", line.sourceOwnershipType());
		row.put("sourceProjectId", line.sourceProjectId());
		putProject(row, "source", line.sourceProjectId());
		row.put("targetOwnershipType", line.targetOwnershipType());
		row.put("targetProjectId", line.targetProjectId());
		putProject(row, "target", line.targetProjectId());
		if (costVisible) {
			row.put("sourceUnitCost", decimal(line.sourceUnitCost()));
			row.put("sourceCostLayerId", line.sourceCostLayerId());
		}
		return row;
	}

	private Map<String, Object> lineMap(StocktakeLine line) {
		return lineMap(line, true);
	}

	private Map<String, Object> lineMap(StocktakeLine line, boolean costVisible) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", line.id());
		row.put("lineNo", line.lineNo());
		row.put("warehouseId", line.warehouseId());
		row.put("warehouseName", warehouseName(line.warehouseId()));
		row.put("materialId", line.materialId());
		putMaterial(row, line.materialId());
		row.put("unitId", line.unitId());
		row.put("unitName", unitName(line.unitId()));
		row.put("qualityStatus", line.qualityStatus());
		row.put("ownershipType", line.ownershipType());
		row.put("projectId", line.projectId());
		putProject(row, "", line.projectId());
		row.put("batchId", line.batchId());
		row.put("batchNo", batchNo(line.batchId()));
		row.put("serialId", line.serialId());
		row.put("serialNo", serialNo(line.serialId()));
		if (costVisible) {
			row.put("costLayerId", line.costLayerId());
			row.put("varianceUnitCost", decimal(line.varianceUnitCost()));
		}
		row.put("bookQuantity", decimal(line.bookQuantity()));
		row.put("countedQuantity", decimal(line.countedQuantity()));
		row.put("varianceQuantity", decimal(stocktakeVarianceQuantity(line)));
		row.put("varianceReason", line.varianceReason());
		row.put("valuationRequirement", valuationRequirementMap(line, costVisible));
		row.put("version", line.version());
		return row;
	}

	private Map<String, Object> lineMap(ValuationAdjustmentLine line) {
		return lineMap(line, true);
	}

	private Map<String, Object> lineMap(ValuationAdjustmentLine line, boolean costVisible) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", line.id());
		row.put("lineNo", line.lineNo());
		row.put("ownershipType", line.ownershipType());
		row.put("projectId", line.projectId());
		putProject(row, "", line.projectId());
		row.put("materialId", line.materialId());
		putMaterial(row, line.materialId());
		row.put("quantity", decimal(line.quantity()));
		if (costVisible) {
			row.put("unitCost", decimal(line.unitCost()));
			row.put("adjustmentAmount", money(line.adjustmentAmount()));
			row.put("costLayerId", line.costLayerId());
			row.put("valueMovementId", line.valueMovementId());
		}
		row.put("version", line.version());
		return row;
	}

	private Map<String, Object> valuationRequirementMap(StocktakeLine line, boolean costVisible) {
		StocktakeValuationRequirement requirement = valuationRequirement(line);
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("mode", requirement.mode());
		row.put("requiredUnitCost", requirement.requiredUnitCost());
		row.put("requiredReason", requirement.requiredReason());
		row.put("requiredAttachment", requirement.requiredAttachment());
		if (costVisible && requirement.unitCost() != null) {
			row.put("unitCost", decimal(requirement.unitCost()));
		}
		return row;
	}

	private StocktakeValuationRequirement valuationRequirement(StocktakeLine line) {
		if (stocktakeVarianceQuantity(line).compareTo(BigDecimal.ZERO) <= 0 || !inventoryValueEnabled(line.materialId())) {
			return new StocktakeValuationRequirement("NONE", false, false, false, null);
		}
		if ("PROJECT".equals(line.ownershipType())) {
			return new StocktakeValuationRequirement("PROJECT_EXPLICIT_UNIT_COST", true, true, true, null);
		}
		BigDecimal averageUnitCost = publicAverageUnitCost(line.materialId());
		if (averageUnitCost != null) {
			return new StocktakeValuationRequirement("AUTO_PUBLIC_AVERAGE", false, false, false,
					averageUnitCost);
		}
		return new StocktakeValuationRequirement("EXPLICIT_UNIT_COST", true, false, false, null);
	}

	private InventoryPostingService.ValuationContext stocktakeValuationContext(StocktakeLine line,
			InventoryDirection direction) {
		if (direction == InventoryDirection.OUT) {
			return new InventoryPostingService.ValuationContext(line.ownershipType(), line.projectId(), null,
					line.costLayerId(), null);
		}
		StocktakeValuationRequirement requirement = valuationRequirement(line);
		return switch (requirement.mode()) {
			case "AUTO_PUBLIC_AVERAGE" -> InventoryPostingService.ValuationContext.publicStock(requirement.unitCost());
			case "EXPLICIT_UNIT_COST" -> InventoryPostingService.ValuationContext.publicStock(line.varianceUnitCost());
			case "PROJECT_EXPLICIT_UNIT_COST" -> new InventoryPostingService.ValuationContext("PROJECT",
					line.projectId(), line.varianceUnitCost(), null, null);
			default -> new InventoryPostingService.ValuationContext(line.ownershipType(), line.projectId(), null,
					line.costLayerId(), null);
		};
	}

	private String stocktakeMovementReason(Header header, StocktakeLine line) {
		return hasText(line.varianceReason()) ? line.varianceReason().trim() : header.reason();
	}

	private BigDecimal stocktakeVarianceQuantity(StocktakeLine line) {
		if (line.countedQuantity() != null) {
			return line.countedQuantity().subtract(line.bookQuantity());
		}
		return line.varianceQuantity() == null ? BigDecimal.ZERO : line.varianceQuantity();
	}

	private boolean inventoryValueEnabled(Long materialId) {
		Boolean enabled = this.jdbcTemplate.queryForObject("""
				select coalesce(inventory_value_enabled, false)
				from mst_material
				where id = ?
				""", Boolean.class, materialId);
		return Boolean.TRUE.equals(enabled);
	}

	private BigDecimal publicAverageUnitCost(Long materialId) {
		return this.inventoryValuationService.currentPublicAverageUnitCost(materialId).orElse(null);
	}

	private boolean costVisible(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("inventory:valuation:view");
	}

	private void putMaterial(Map<String, Object> row, Long materialId) {
		if (materialId == null) {
			return;
		}
		Map<String, Object> material = this.jdbcTemplate.query("""
				select code, name
				from mst_material
				where id = ?
				""", (rs, rowNum) -> Map.<String, Object>of("code", rs.getString("code"), "name",
				rs.getString("name")), materialId).stream().findFirst().orElse(Map.of());
		row.put("materialCode", material.get("code"));
		row.put("materialName", material.get("name"));
	}

	private void putProject(Map<String, Object> row, String prefix, Long projectId) {
		String normalizedPrefix = prefix == null || prefix.isBlank() ? "" : prefix;
		String idKey = normalizedPrefix.isBlank() ? "projectId"
				: normalizedPrefix + "ProjectId";
		String noKey = normalizedPrefix.isBlank() ? "projectNo"
				: normalizedPrefix + "ProjectNo";
		String nameKey = normalizedPrefix.isBlank() ? "projectName"
				: normalizedPrefix + "ProjectName";
		row.put(idKey, projectId);
		if (projectId == null) {
			row.put(noKey, null);
			row.put(nameKey, null);
			return;
		}
		Map<String, Object> project = this.jdbcTemplate.query("""
				select project_no, name
				from sal_project
				where id = ?
				""", (rs, rowNum) -> Map.<String, Object>of("projectNo", rs.getString("project_no"), "projectName",
				rs.getString("name")), projectId).stream().findFirst().orElse(Map.of());
		row.put(noKey, project.get("projectNo"));
		row.put(nameKey, project.get("projectName"));
	}

	private String warehouseName(Long warehouseId) {
		return lookupName("mst_warehouse", "name", warehouseId);
	}

	private String unitName(Long unitId) {
		return lookupName("mst_unit", "name", unitId);
	}

	private String batchNo(Long batchId) {
		return lookupName("inv_batch", "batch_no", batchId);
	}

	private String serialNo(Long serialId) {
		return lookupName("inv_serial", "serial_no", serialId);
	}

	private String lookupName(String tableName, String columnName, Long id) {
		if (id == null) {
			return null;
		}
		return this.jdbcTemplate.query("select %s from %s where id = ?".formatted(columnName, tableName),
				(rs, rowNum) -> rs.getString(1), id).stream().findFirst().orElse(null);
	}

	private Long sourceCostLayerId(String ownershipType, Long projectId, Long warehouseId, Long materialId,
			String qualityStatus, Long batchId, Long serialId) {
		if (!"PROJECT".equals(ownershipType)) {
			return null;
		}
		List<Long> costLayerIds = this.jdbcTemplate.query("""
				select cost_layer_id
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				and ownership_type = 'PROJECT'
				and project_id = ?
				and quantity_on_hand > 0
				order by id
				for update
				""", (rs, rowNum) -> nullableLong(rs, "cost_layer_id"), warehouseId, materialId,
				defaultQuality(qualityStatus), batchId, serialId, projectId);
		if (costLayerIds.size() != 1 || costLayerIds.getFirst() == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
		return costLayerIds.getFirst();
	}

	private static void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
	}

	private static String defaultQuality(String value) {
		return value == null || value.isBlank() ? "QUALIFIED" : value;
	}

	private static String defaultOwnership(String value) {
		return value == null || value.isBlank() ? "PUBLIC" : value;
	}

	private static Long publicProject(String ownershipType, Long projectId) {
		return "PROJECT".equals(defaultOwnership(ownershipType)) ? projectId : null;
	}

	private static String documentNo(String prefix) {
		return prefix + "-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-"
				+ String.format("%03d", Math.floorMod(NUMBER_SEQUENCE.getAndIncrement(), 1000));
	}

	private static String decimal(BigDecimal value) {
		return value == null ? null : value.setScale(6).toPlainString();
	}

	private static String money(BigDecimal value) {
		return value == null ? null : value.setScale(2).toPlainString();
	}

	private static BigDecimal nullToZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private static String trimToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record VersionedActionRequest(Long version, String reason, String idempotencyKey) {
	}

	public record WarehouseTransferRequest(@NotNull LocalDate businessDate, @NotNull String reason,
			@NotNull String idempotencyKey, List<WarehouseTransferLineRequest> lines, Long version) {
	}

	public record WarehouseTransferLineRequest(Integer lineNo, Long sourceWarehouseId, Long targetWarehouseId,
			String ownershipType, Long projectId, Long materialId, Long unitId, String qualityStatus, Long batchId,
			Long serialId, BigDecimal quantity, Long sourceCostLayerId) {
	}

	public record OwnershipConversionRequest(@NotNull LocalDate businessDate, @NotNull String reason,
			@NotNull String idempotencyKey, List<OwnershipConversionLineRequest> lines, Long version) {
	}

	public record OwnershipConversionLineRequest(Integer lineNo, String sourceOwnershipType, Long sourceProjectId,
			String targetOwnershipType, Long targetProjectId, Long sourceWarehouseId, Long targetWarehouseId,
			Long materialId, Long unitId, String qualityStatus, Long batchId, Long serialId, BigDecimal quantity,
			BigDecimal sourceUnitCost, Long sourceCostLayerId) {
	}

	public record StocktakeRequest(@NotNull LocalDate businessDate, @NotNull String scopeType, Long warehouseId,
			Long materialId, @NotNull String reason, @NotNull String idempotencyKey) {
	}

	public record StocktakeLineUpdateRequest(Long version, List<StocktakeLineCount> lines) {
	}

	public record StocktakeLineCount(Long id, Long version, BigDecimal countedQuantity,
			BigDecimal varianceUnitCost, String varianceReason) {
	}

	public record ValuationAdjustmentRequest(Long version, @NotNull String adjustmentType,
			@NotNull LocalDate businessDate, @NotNull String reason, @NotNull String idempotencyKey,
			List<ValuationAdjustmentLineRequest> lines) {
	}

	public record ValuationAdjustmentLineRequest(Integer lineNo, String ownershipType, Long projectId,
			Long materialId, BigDecimal quantity, BigDecimal unitCost, BigDecimal adjustmentAmount,
			Long costLayerId) {
	}

	private record Header(Long id, String documentNo, LocalDate businessDate, String reason, String status,
			Long version, Long warehouseId, Long materialId, String adjustmentType, Long approvalInstanceId) {
	}

	private record TransferLine(Long id, Integer lineNo, Long sourceWarehouseId, Long targetWarehouseId,
			String ownershipType, Long projectId, Long materialId, Long unitId, String qualityStatus, Long batchId,
			Long serialId, BigDecimal quantity, Long sourceCostLayerId) {
	}

	private record OwnershipLine(Long id, Integer lineNo, String sourceOwnershipType, Long sourceProjectId,
			String targetOwnershipType, Long targetProjectId, Long sourceWarehouseId, Long targetWarehouseId,
			Long materialId, Long unitId, String qualityStatus, Long batchId, Long serialId, BigDecimal quantity,
			BigDecimal sourceUnitCost, Long sourceCostLayerId) {
	}

	private record StocktakeLine(Long id, Integer lineNo, Long warehouseId, Long materialId, Long unitId,
			String qualityStatus, String ownershipType, Long projectId, Long batchId, Long serialId,
			BigDecimal bookQuantity, BigDecimal countedQuantity, BigDecimal varianceQuantity,
			BigDecimal varianceUnitCost, String varianceReason, Long costLayerId, Long version) {
	}

	private record StocktakeValuationRequirement(String mode, boolean requiredUnitCost, boolean requiredReason,
			boolean requiredAttachment, BigDecimal unitCost) {
	}

	private record ValuationAdjustmentLine(Long id, Integer lineNo, String ownershipType, Long projectId,
			Long materialId, BigDecimal quantity, BigDecimal unitCost, BigDecimal adjustmentAmount,
			Long costLayerId, Long valueMovementId, Long version) {
	}

}
