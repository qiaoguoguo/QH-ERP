package com.qherp.api.system.production;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.cost.CostRecordWriter;
import com.qherp.api.system.inventory.InventoryAvailabilityService;
import com.qherp.api.system.inventory.InventoryDirection;
import com.qherp.api.system.inventory.InventoryMovementType;
import com.qherp.api.system.inventory.InventoryPostingService;
import com.qherp.api.system.inventory.InventoryQualityStatus;
import com.qherp.api.system.inventory.InventoryReservationType;
import com.qherp.api.system.inventory.InventoryTrackingMethod;
import com.qherp.api.system.inventory.InventoryTrackingService;
import com.qherp.api.system.inventory.InventoryValuationService;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
import com.qherp.api.system.quality.QualityAdminService;
import com.qherp.api.system.quality.QualityInspectionSourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ProductionAdminService {

	private static final String WORK_ORDER_TARGET = "MFG_WORK_ORDER";

	private static final String MATERIAL_ISSUE_TARGET = "MFG_MATERIAL_ISSUE";

	private static final String WORK_REPORT_TARGET = "MFG_WORK_REPORT";

	private static final String COMPLETION_RECEIPT_TARGET = "MFG_COMPLETION_RECEIPT";

	private static final String MATERIAL_ISSUE_SOURCE = "PRODUCTION_MATERIAL_ISSUE";

	private static final String COMPLETION_RECEIPT_SOURCE = "PRODUCTION_COMPLETION_RECEIPT";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final BigDecimal ONE = BigDecimal.ONE;

	private static final String QUALIFIED_BALANCE_NOT_ENOUGH = "QUALIFIED_BALANCE_NOT_ENOUGH";

	private static final String QUALIFIED_BALANCE_NOT_ENOUGH_MESSAGE = "合格可用库存不足";

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final int MAX_NO_ATTEMPTS = 3;

	private static final AtomicInteger WORK_ORDER_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger MATERIAL_ISSUE_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger REPORT_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger RECEIPT_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final CostRecordWriter costRecordWriter;

	private final InventoryPostingService inventoryPostingService;

	private final InventoryValuationService inventoryValuationService;

	private final InventoryAvailabilityService inventoryAvailabilityService;

	private final BusinessPeriodGuard businessPeriodGuard;

	private final QualityAdminService qualityAdminService;

	private final InventoryTrackingService inventoryTrackingService;

	public ProductionAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			CostRecordWriter costRecordWriter, InventoryPostingService inventoryPostingService,
			InventoryValuationService inventoryValuationService, InventoryAvailabilityService inventoryAvailabilityService,
			BusinessPeriodGuard businessPeriodGuard, QualityAdminService qualityAdminService,
			InventoryTrackingService inventoryTrackingService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.costRecordWriter = costRecordWriter;
		this.inventoryPostingService = inventoryPostingService;
		this.inventoryValuationService = inventoryValuationService;
		this.inventoryAvailabilityService = inventoryAvailabilityService;
		this.businessPeriodGuard = businessPeriodGuard;
		this.qualityAdminService = qualityAdminService;
		this.inventoryTrackingService = inventoryTrackingService;
	}

	@Transactional(readOnly = true)
	public PageResponse<WorkOrderSummaryResponse> workOrders(String keyword, String status, Long productMaterialId,
			LocalDate dateFrom, LocalDate dateTo, int page, int pageSize) {
		QueryParts queryParts = workOrderQueryParts(keyword, status, productMaterialId, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_work_order wo
				join mst_material pm on pm.id = wo.product_material_id
				join mfg_bom b on b.id = wo.bom_id
				join mst_warehouse iw on iw.id = wo.issue_warehouse_id
				join mst_warehouse rw on rw.id = wo.receipt_warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<WorkOrderSummaryResponse> items = this.jdbcTemplate.query("""
				select wo.id, wo.work_order_no, wo.product_material_id, pm.code as product_material_code,
				       pm.name as product_material_name, wo.bom_id, b.bom_code, b.version_code,
				       wo.planned_quantity, wo.reported_quantity, wo.qualified_quantity, wo.defective_quantity,
				       wo.received_quantity, wo.issue_warehouse_id, iw.name as issue_warehouse_name,
				       wo.receipt_warehouse_id, rw.name as receipt_warehouse_name, wo.planned_start_date,
				       wo.planned_finish_date, wo.status, wo.remark, wo.created_by, wo.created_at, wo.updated_at,
				       wo.released_by, wo.released_at, wo.completed_by, wo.completed_at, wo.cancelled_by,
				       wo.cancelled_at
				from mfg_work_order wo
				join mst_material pm on pm.id = wo.product_material_id
				join mfg_bom b on b.id = wo.bom_id
				join mst_warehouse iw on iw.id = wo.issue_warehouse_id
				join mst_warehouse rw on rw.id = wo.receipt_warehouse_id
				%s
				order by wo.updated_at desc, wo.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapWorkOrderSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public WorkOrderDetailResponse workOrder(Long id) {
		return workOrder(id, null);
	}

	@Transactional(readOnly = true)
	public WorkOrderDetailResponse workOrder(Long id, CurrentUser currentUser) {
		WorkOrderSummaryResponse summary = workOrderSummary(id).orElseThrow(this::workOrderNotFound);
		return workOrderDetail(summary, currentUser);
	}

	@Transactional
	public WorkOrderDetailResponse createWorkOrder(WorkOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedWorkOrder validated = validateWorkOrderRequest(request);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertWorkOrderWithRetry(validated, operator.username(), now);
			this.auditService.record(operator, "MFG_WORK_ORDER_CREATE", WORK_ORDER_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return workOrder(created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProductionException(exception);
		}
	}

	@Transactional
	public WorkOrderDetailResponse updateWorkOrder(Long id, WorkOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		WorkOrderRow current = lockWorkOrder(id).orElseThrow(this::workOrderNotFound);
		if (current.status() != ProductionWorkOrderStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WORK_ORDER_STATUS_INVALID);
		}
		ValidatedWorkOrder validated = validateWorkOrderRequest(request);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_work_order
				set product_material_id = ?, bom_id = ?, planned_quantity = ?, issue_warehouse_id = ?,
				    receipt_warehouse_id = ?, planned_start_date = ?, planned_finish_date = ?, remark = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", validated.productMaterial().id(), validated.bom().id(), validated.plannedQuantity(),
				validated.issueWarehouseId(), validated.receiptWarehouseId(), validated.plannedStartDate(),
				validated.plannedFinishDate(), blankToNull(validated.remark()), operator.username(), now, id);
		this.auditService.record(operator, "MFG_WORK_ORDER_UPDATE", WORK_ORDER_TARGET, id, current.workOrderNo(),
				servletRequest);
		return workOrder(id);
	}

	@Transactional
	public WorkOrderDetailResponse releaseWorkOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		WorkOrderRow workOrder = lockWorkOrder(id).orElseThrow(this::workOrderNotFound);
		if (workOrder.status() != ProductionWorkOrderStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WORK_ORDER_STATUS_INVALID);
		}
		MaterialRef product = validateProductMaterial(workOrder.productMaterialId());
		BomRef bom = validateBom(workOrder.bomId(), product.id(), workOrder.plannedStartDate());
		validateEnabledWarehouse(workOrder.issueWarehouseId());
		validateEnabledWarehouse(workOrder.receiptWarehouseId());
		List<BomItemRef> items = validateBomItems(bom.id());
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("delete from mfg_work_order_material where work_order_id = ?", id);
		for (BomItemRef item : items) {
			BigDecimal factor = workOrder.plannedQuantity().divide(bom.baseQuantity(), 12, RoundingMode.HALF_UP);
			BigDecimal businessQuantity = factor.multiply(item.businessQuantity())
				.multiply(ONE.add(item.lossRate()))
				.setScale(6, RoundingMode.HALF_UP);
			BigDecimal baseRequiredQuantity = factor.multiply(item.baseQuantity())
				.multiply(ONE.add(item.lossRate()))
				.setScale(6, RoundingMode.HALF_UP);
			validatePositiveProductionQuantity(businessQuantity);
			validatePositiveProductionQuantity(baseRequiredQuantity);
			this.jdbcTemplate.update("""
					insert into mfg_work_order_material (
						work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity,
						issued_quantity, loss_rate, remark, created_at, updated_at, business_unit_id,
						business_quantity, base_unit_id, base_required_quantity, conversion_id,
						conversion_rate_snapshot, quantity_scale_snapshot, rounding_mode_snapshot, quantity_basis
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", id, item.lineNo(), item.id(), item.material().id(), item.baseUnitId(), baseRequiredQuantity,
					ZERO, item.lossRate(), blankToNull(item.remark()), now, now, item.businessUnitId(),
					businessQuantity, item.baseUnitId(), baseRequiredQuantity, item.conversionId(),
					item.conversionRateSnapshot(), item.quantityScaleSnapshot(), item.roundingModeSnapshot(),
					item.quantityBasis());
		}
		this.jdbcTemplate.update("""
				update mfg_work_order
				set status = ?, released_by = ?, released_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", ProductionWorkOrderStatus.RELEASED.name(), operator.username(), now, operator.username(), now,
				id);
		reserveWorkOrderMaterials(workOrder, operator, servletRequest);
		this.auditService.record(operator, "MFG_WORK_ORDER_RELEASE", WORK_ORDER_TARGET, id, workOrder.workOrderNo(),
				servletRequest);
		return workOrder(id);
	}

	@Transactional
	public WorkOrderDetailResponse completeWorkOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		WorkOrderRow workOrder = lockWorkOrder(id).orElseThrow(this::workOrderNotFound);
		if (workOrder.status() != ProductionWorkOrderStatus.RELEASED
				&& workOrder.status() != ProductionWorkOrderStatus.IN_PROGRESS) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WORK_ORDER_STATUS_INVALID);
		}
		if (workOrder.receivedQuantity().compareTo(workOrder.plannedQuantity()) != 0 || hasDraftBusiness(id)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WORK_ORDER_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_work_order
				set status = ?, completed_by = ?, completed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", ProductionWorkOrderStatus.COMPLETED.name(), operator.username(), now, operator.username(), now,
				id);
		this.inventoryAvailabilityService.releaseBySource(InventoryReservationType.RESERVATION,
				InventoryAvailabilityService.PRODUCTION_WORK_ORDER_SOURCE, id, operator, servletRequest);
		this.auditService.record(operator, "MFG_WORK_ORDER_COMPLETE", WORK_ORDER_TARGET, id,
				workOrder.workOrderNo(), servletRequest);
		return workOrder(id);
	}

	@Transactional
	public WorkOrderDetailResponse cancelWorkOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		WorkOrderRow workOrder = lockWorkOrder(id).orElseThrow(this::workOrderNotFound);
		if (hasPostedBusiness(id)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WORK_ORDER_HAS_POSTED_BUSINESS);
		}
		if (hasDraftBusiness(id)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WORK_ORDER_STATUS_INVALID);
		}
		if (workOrder.status() != ProductionWorkOrderStatus.DRAFT
				&& workOrder.status() != ProductionWorkOrderStatus.RELEASED) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WORK_ORDER_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_work_order
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", ProductionWorkOrderStatus.CANCELLED.name(), operator.username(), now, operator.username(), now,
				id);
		this.inventoryAvailabilityService.releaseBySource(InventoryReservationType.RESERVATION,
				InventoryAvailabilityService.PRODUCTION_WORK_ORDER_SOURCE, id, operator, servletRequest);
		this.auditService.record(operator, "MFG_WORK_ORDER_CANCEL", WORK_ORDER_TARGET, id, workOrder.workOrderNo(),
				servletRequest);
		return workOrder(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<MaterialIssueSummaryResponse> materialIssues(Long workOrderId, int page, int pageSize) {
		ensureWorkOrderExists(workOrderId);
		long total = this.jdbcTemplate.queryForObject(
				"select count(*) from mfg_material_issue where work_order_id = ?", Long.class, workOrderId);
		List<MaterialIssueSummaryResponse> items = this.jdbcTemplate.query("""
				select id, issue_no, work_order_id, status, business_date, reason, remark, created_by, created_at,
				       updated_at, posted_by, posted_at,
				       (select count(*) from mfg_material_issue_line l where l.issue_id = i.id) as line_count
				from mfg_material_issue i
				where work_order_id = ?
				order by updated_at desc, id desc
				limit ? offset ?
				""", this::mapMaterialIssueSummary, workOrderId, limit(pageSize), offset(page, pageSize));
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public MaterialIssueDetailResponse materialIssue(Long workOrderId, Long id) {
		ensureWorkOrderExists(workOrderId);
		MaterialIssueSummaryResponse summary = materialIssueSummary(workOrderId, id)
			.orElseThrow(this::issueNotFound);
		return new MaterialIssueDetailResponse(summary.id(), summary.issueNo(), summary.workOrderId(),
				summary.status(), summary.businessDate(), summary.reason(), summary.remark(), summary.lineCount(),
				summary.createdByName(), summary.createdAt(), summary.updatedAt(), summary.postedByName(),
				summary.postedAt(), materialIssueLines(id));
	}

	@Transactional
	public MaterialIssueDetailResponse createMaterialIssue(Long workOrderId, MaterialIssueRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		WorkOrderRow workOrder = lockWorkOrder(workOrderId).orElseThrow(this::workOrderNotFound);
		requireExecutableWorkOrder(workOrder);
		ValidatedMaterialIssue validated = validateMaterialIssueRequest(workOrder, null, request);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.CREATE,
				MATERIAL_ISSUE_SOURCE, null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertMaterialIssueWithRetry(workOrderId, validated, operator.username(), now);
			insertMaterialIssueLines(created.id(), validated.lines(), now);
			prepareMaterialIssueAllocations(created.id(), validated, operator.username());
			this.auditService.record(operator, "MFG_MATERIAL_ISSUE_CREATE", MATERIAL_ISSUE_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return materialIssue(workOrderId, created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProductionException(exception);
		}
	}

	@Transactional
	public MaterialIssueDetailResponse updateMaterialIssue(Long workOrderId, Long id, MaterialIssueRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		WorkOrderRow workOrder = lockWorkOrder(workOrderId).orElseThrow(this::workOrderNotFound);
		requireExecutableWorkOrder(workOrder);
		ProductionDocumentRow issue = lockMaterialIssue(workOrderId, id).orElseThrow(this::issueNotFound);
		if (issue.status() != ProductionDocumentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_DOCUMENT_POSTED_IMMUTABLE);
		}
		ValidatedMaterialIssue validated = validateMaterialIssueRequest(workOrder, id, request);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.UPDATE,
				MATERIAL_ISSUE_SOURCE, id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_material_issue
				set business_date = ?, reason = ?, remark = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", validated.businessDate(), validated.reason(), blankToNull(validated.remark()),
				operator.username(), now, id);
		this.inventoryTrackingService.deleteDraftDocumentTracking(MATERIAL_ISSUE_SOURCE, id);
		this.jdbcTemplate.update("delete from mfg_material_issue_line where issue_id = ?", id);
		insertMaterialIssueLines(id, validated.lines(), now);
		prepareMaterialIssueAllocations(id, validated, operator.username());
		this.auditService.record(operator, "MFG_MATERIAL_ISSUE_UPDATE", MATERIAL_ISSUE_TARGET, id,
				issue.documentNo(), servletRequest);
		return materialIssue(workOrderId, id);
	}

	@Transactional
	public MaterialIssueDetailResponse postMaterialIssue(Long workOrderId, Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		try {
			WorkOrderRow workOrder = lockWorkOrder(workOrderId).orElseThrow(this::workOrderNotFound);
			requireExecutableWorkOrder(workOrder);
			ProductionDocumentRow issue = lockMaterialIssue(workOrderId, id).orElseThrow(this::issueNotFound);
			if (issue.status() != ProductionDocumentStatus.DRAFT) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_DUPLICATE_POST);
			}
			this.businessPeriodGuard.assertWritable(issue.businessDate(), BusinessPeriodOperation.POST,
					MATERIAL_ISSUE_SOURCE, id);
			List<MaterialIssueLineRow> lines = materialIssueLineRows(id);
			if (lines.isEmpty()) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_EMPTY_LINES);
			}
			OffsetDateTime now = OffsetDateTime.now();
			for (MaterialIssueLineRow line : lines) {
				postMaterialIssueLine(workOrder, issue, line, operator, servletRequest, now);
			}
			this.jdbcTemplate.update("""
					update mfg_material_issue
					set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", ProductionDocumentStatus.POSTED.name(), operator.username(), now, operator.username(), now,
					id);
			markWorkOrderInProgress(workOrderId, operator.username(), now);
			this.auditService.record(operator, "MFG_MATERIAL_ISSUE_POST", MATERIAL_ISSUE_TARGET, id,
					issue.documentNo(), servletRequest);
			this.costRecordWriter.writeMaterialIssue(id, operator, servletRequest);
			return materialIssue(workOrderId, id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProductionException(exception);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<WorkReportResponse> reports(Long workOrderId, int page, int pageSize) {
		ensureWorkOrderExists(workOrderId);
		long total = this.jdbcTemplate.queryForObject("select count(*) from mfg_work_report where work_order_id = ?",
				Long.class, workOrderId);
		List<WorkReportResponse> items = this.jdbcTemplate.query("""
				select id, report_no, work_order_id, status, business_date, qualified_quantity, defective_quantity,
				       (qualified_quantity + defective_quantity) as total_quantity, reporter_name, remark,
				       created_by, created_at, updated_at, posted_by, posted_at
				from mfg_work_report
				where work_order_id = ?
				order by updated_at desc, id desc
				limit ? offset ?
				""", this::mapWorkReport, workOrderId, limit(pageSize), offset(page, pageSize));
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public WorkReportResponse report(Long workOrderId, Long id) {
		ensureWorkOrderExists(workOrderId);
		return workReport(workOrderId, id).orElseThrow(this::reportNotFound);
	}

	@Transactional
	public WorkReportResponse createReport(Long workOrderId, WorkReportRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		WorkOrderRow workOrder = lockWorkOrder(workOrderId).orElseThrow(this::workOrderNotFound);
		requireExecutableWorkOrder(workOrder);
		ValidatedReport validated = validateReportRequest(workOrder, request);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.CREATE,
				"PRODUCTION_WORK_REPORT", null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertReportWithRetry(workOrderId, validated, operator.username(), now);
			this.auditService.record(operator, "MFG_WORK_REPORT_CREATE", WORK_REPORT_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return report(workOrderId, created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProductionException(exception);
		}
	}

	@Transactional
	public WorkReportResponse updateReport(Long workOrderId, Long id, WorkReportRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		WorkOrderRow workOrder = lockWorkOrder(workOrderId).orElseThrow(this::workOrderNotFound);
		requireExecutableWorkOrder(workOrder);
		ProductionDocumentRow report = lockWorkReport(workOrderId, id).orElseThrow(this::reportNotFound);
		if (report.status() != ProductionDocumentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_DOCUMENT_POSTED_IMMUTABLE);
		}
		ValidatedReport validated = validateReportRequest(workOrder, request);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.UPDATE,
				"PRODUCTION_WORK_REPORT", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_work_report
				set business_date = ?, qualified_quantity = ?, defective_quantity = ?, reporter_name = ?,
				    remark = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", validated.businessDate(), validated.qualifiedQuantity(), validated.defectiveQuantity(),
				validated.reporterName(), blankToNull(validated.remark()), operator.username(), now, id);
		this.auditService.record(operator, "MFG_WORK_REPORT_UPDATE", WORK_REPORT_TARGET, id, report.documentNo(),
				servletRequest);
		return report(workOrderId, id);
	}

	@Transactional
	public WorkReportResponse postReport(Long workOrderId, Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		WorkOrderRow workOrder = lockWorkOrder(workOrderId).orElseThrow(this::workOrderNotFound);
		requireExecutableWorkOrder(workOrder);
		ProductionDocumentRow report = lockWorkReport(workOrderId, id).orElseThrow(this::reportNotFound);
		if (report.status() != ProductionDocumentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_DUPLICATE_POST);
		}
		this.businessPeriodGuard.assertWritable(report.businessDate(), BusinessPeriodOperation.POST,
				"PRODUCTION_WORK_REPORT", id);
		WorkReportResponse detail = report(workOrderId, id);
		BigDecimal total = detail.qualifiedQuantity().add(detail.defectiveQuantity());
		if (workOrder.reportedQuantity().add(total).compareTo(workOrder.plannedQuantity()) > 0) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_REPORT_EXCEEDS_PLAN);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_work_order
				set reported_quantity = reported_quantity + ?, qualified_quantity = qualified_quantity + ?,
				    defective_quantity = defective_quantity + ?, status = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", total, detail.qualifiedQuantity(), detail.defectiveQuantity(),
				ProductionWorkOrderStatus.IN_PROGRESS.name(), operator.username(), now, workOrderId);
		this.jdbcTemplate.update("""
				update mfg_work_report
				set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", ProductionDocumentStatus.POSTED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "MFG_WORK_REPORT_POST", WORK_REPORT_TARGET, id, report.documentNo(),
				servletRequest);
		this.costRecordWriter.writeWorkReport(id, operator, servletRequest);
		return report(workOrderId, id);
	}

	@Transactional(readOnly = true)
	public PageResponse<CompletionReceiptResponse> completionReceipts(Long workOrderId, int page, int pageSize) {
		ensureWorkOrderExists(workOrderId);
		long total = this.jdbcTemplate.queryForObject(
				"select count(*) from mfg_completion_receipt where work_order_id = ?", Long.class, workOrderId);
		List<CompletionReceiptResponse> items = this.jdbcTemplate.query("""
				select r.id, r.receipt_no, r.work_order_id, r.status, r.business_date, r.receipt_warehouse_id,
				       w.name as receipt_warehouse_name, r.quantity, r.before_quantity, r.after_quantity,
				       r.provisional_unit_cost, r.unit_cost, r.valuation_state,
				       r.remark, r.created_by, r.created_at, r.updated_at, r.posted_by, r.posted_at,
				       pm.tracking_method
				from mfg_completion_receipt r
				join mfg_work_order wo on wo.id = r.work_order_id
				join mst_material pm on pm.id = wo.product_material_id
				join mst_warehouse w on w.id = r.receipt_warehouse_id
				where r.work_order_id = ?
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""", this::mapCompletionReceipt, workOrderId, limit(pageSize), offset(page, pageSize));
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public CompletionReceiptResponse completionReceipt(Long workOrderId, Long id) {
		ensureWorkOrderExists(workOrderId);
		return completionReceiptDetail(workOrderId, id).orElseThrow(this::receiptNotFound);
	}

	@Transactional
	public CompletionReceiptResponse createCompletionReceipt(Long workOrderId, CompletionReceiptRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		WorkOrderRow workOrder = lockWorkOrder(workOrderId).orElseThrow(this::workOrderNotFound);
		requireExecutableWorkOrder(workOrder);
		ValidatedReceipt validated = validateReceiptRequest(workOrder, request);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.CREATE,
				COMPLETION_RECEIPT_SOURCE, null);
		MaterialRef product = validateProductMaterial(workOrder.productMaterialId());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertReceiptWithRetry(workOrderId, validated, operator.username(), now);
			prepareCompletionReceiptAllocations(created.id(), product, validated, operator.username());
			this.auditService.record(operator, "MFG_COMPLETION_RECEIPT_CREATE", COMPLETION_RECEIPT_TARGET,
					created.id(), created.documentNo(), servletRequest);
			return completionReceipt(workOrderId, created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProductionException(exception);
		}
	}

	@Transactional
	public CompletionReceiptResponse updateCompletionReceipt(Long workOrderId, Long id,
			CompletionReceiptRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		WorkOrderRow workOrder = lockWorkOrder(workOrderId).orElseThrow(this::workOrderNotFound);
		requireExecutableWorkOrder(workOrder);
		ProductionDocumentRow receipt = lockCompletionReceipt(workOrderId, id).orElseThrow(this::receiptNotFound);
		if (receipt.status() != ProductionDocumentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_DOCUMENT_POSTED_IMMUTABLE);
		}
		ValidatedReceipt validated = validateReceiptRequest(workOrder, request);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.UPDATE,
				COMPLETION_RECEIPT_SOURCE, id);
		MaterialRef product = validateProductMaterial(workOrder.productMaterialId());
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_completion_receipt
				set business_date = ?, receipt_warehouse_id = ?, quantity = ?, provisional_unit_cost = ?,
				    remark = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", validated.businessDate(), validated.receiptWarehouseId(), validated.quantity(),
				validated.provisionalUnitCost(), blankToNull(validated.remark()), operator.username(), now, id);
		this.inventoryTrackingService.deleteDraftDocumentTracking(COMPLETION_RECEIPT_SOURCE, id);
		prepareCompletionReceiptAllocations(id, product, validated, operator.username());
		this.auditService.record(operator, "MFG_COMPLETION_RECEIPT_UPDATE", COMPLETION_RECEIPT_TARGET, id,
				receipt.documentNo(), servletRequest);
		return completionReceipt(workOrderId, id);
	}

	@Transactional
	public CompletionReceiptResponse postCompletionReceipt(Long workOrderId, Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		try {
			WorkOrderRow workOrder = lockWorkOrder(workOrderId).orElseThrow(this::workOrderNotFound);
			requireExecutableWorkOrder(workOrder);
			ProductionDocumentRow receipt = lockCompletionReceipt(workOrderId, id).orElseThrow(this::receiptNotFound);
			if (receipt.status() != ProductionDocumentStatus.DRAFT) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_DUPLICATE_POST);
			}
			this.businessPeriodGuard.assertWritable(receipt.businessDate(), BusinessPeriodOperation.POST,
					COMPLETION_RECEIPT_SOURCE, id);
			CompletionReceiptResponse detail = completionReceipt(workOrderId, id);
			validateEnabledWarehouse(detail.receiptWarehouseId());
			if (workOrder.receivedQuantity().add(detail.quantity()).compareTo(workOrder.qualifiedQuantity()) > 0) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_RECEIPT_EXCEEDS_REPORTED);
			}
			MaterialRef product = validateProductMaterial(workOrder.productMaterialId());
			CompletionValuation completionValuation = completionValuation(product.id(), detail.provisionalUnitCost());
			OffsetDateTime now = OffsetDateTime.now();
			List<InventoryTrackingService.ResolvedTrackingAllocation> trackingAllocations = this.inventoryTrackingService
				.storedAllocations(COMPLETION_RECEIPT_SOURCE, id, id);
			InventoryPostingService.PostingResult posting;
			if (trackingAllocations.isEmpty()) {
				assertUntrackedInboundMaterial(product.id());
				posting = this.inventoryPostingService.post(
						new InventoryPostingService.PostingRequest(InventoryMovementType.PRODUCTION_RECEIPT,
								InventoryDirection.IN, detail.receiptWarehouseId(), product.id(), product.unitId(),
								detail.quantity(), InventoryQualityStatus.PENDING_INSPECTION, COMPLETION_RECEIPT_SOURCE,
								id, id, detail.businessDate(), "生产完工入库", detail.remark(), operator.username(),
								null, null, completionValuation.unitCost()));
			}
			else {
				BigDecimal beforeQuantity = ZERO;
				BigDecimal afterQuantity = ZERO;
				Long lastMovementId = null;
				for (InventoryTrackingService.ResolvedTrackingAllocation allocation : trackingAllocations) {
					InventoryPostingService.PostingResult splitPosting = this.inventoryPostingService.post(
							new InventoryPostingService.PostingRequest(InventoryMovementType.PRODUCTION_RECEIPT,
									InventoryDirection.IN, detail.receiptWarehouseId(), product.id(), product.unitId(),
									allocation.quantity(), InventoryQualityStatus.PENDING_INSPECTION,
									COMPLETION_RECEIPT_SOURCE, id, id, detail.businessDate(), "生产完工入库",
									detail.remark(), operator.username(), allocation.batchId(), allocation.serialId(),
									completionValuation.unitCost()));
					this.inventoryTrackingService.attachMovement(allocation.allocationId(), splitPosting.movementId());
					this.inventoryTrackingService.markInboundPosted(allocation, detail.receiptWarehouseId(),
							InventoryQualityStatus.PENDING_INSPECTION, splitPosting.movementId(), operator.username());
					beforeQuantity = beforeQuantity.add(splitPosting.beforeQuantity());
					afterQuantity = afterQuantity.add(splitPosting.afterQuantity());
					lastMovementId = splitPosting.movementId();
				}
				posting = new InventoryPostingService.PostingResult(beforeQuantity, afterQuantity, lastMovementId);
			}
			this.qualityAdminService.createPendingInspection(QualityInspectionSourceType.PRODUCTION_COMPLETION, id, id,
					detail.receiptWarehouseId(), product.id(), product.unitId(), detail.businessDate(),
					detail.quantity(), operator.username());
			this.jdbcTemplate.update("""
					update mfg_completion_receipt
					set before_quantity = ?, after_quantity = ?, status = ?, posted_by = ?, posted_at = ?,
					    unit_cost = ?, valuation_state = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", posting.beforeQuantity(), posting.afterQuantity(), ProductionDocumentStatus.POSTED.name(),
					operator.username(), now, completionValuation.unitCost(), completionValuation.valuationState(),
					operator.username(), now, id);
			this.jdbcTemplate.update("""
					update mfg_work_order
					set received_quantity = received_quantity + ?, status = ?, updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					""", detail.quantity(), ProductionWorkOrderStatus.IN_PROGRESS.name(), operator.username(), now,
					workOrderId);
			this.auditService.record(operator, "MFG_COMPLETION_RECEIPT_POST", COMPLETION_RECEIPT_TARGET, id,
					receipt.documentNo(), servletRequest);
			return completionReceipt(workOrderId, id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProductionException(exception);
		}
	}

	private void assertUntrackedInboundMaterial(Long materialId) {
		InventoryTrackingMethod trackingMethod = this.inventoryTrackingService.trackingMethod(materialId);
		if (trackingMethod == InventoryTrackingMethod.BATCH) {
			throw new BusinessException(ApiErrorCode.INVENTORY_BATCH_REQUIRED);
		}
		if (trackingMethod == InventoryTrackingMethod.SERIAL) {
			throw new BusinessException(ApiErrorCode.INVENTORY_SERIAL_REQUIRED);
		}
	}

	private CompletionValuation completionValuation(Long materialId, BigDecimal provisionalUnitCost) {
		if (!materialValueEnabled(materialId)) {
			return new CompletionValuation(null, "NON_VALUED");
		}
		Optional<BigDecimal> currentAverage = this.inventoryValuationService.currentPublicAverageUnitCost(materialId);
		if (currentAverage.isPresent()) {
			return new CompletionValuation(currentAverage.get(), "CURRENT_AVERAGE_PROVISIONAL");
		}
		if (provisionalUnitCost == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_VALUATION_UNIT_COST_REQUIRED);
		}
		return new CompletionValuation(provisionalUnitCost, "MANUAL_PROVISIONAL");
	}

	private boolean materialValueEnabled(Long materialId) {
		Boolean enabled = this.jdbcTemplate.queryForObject("""
				select coalesce(inventory_value_enabled, false)
				from mst_material
				where id = ?
				""", Boolean.class, materialId);
		return Boolean.TRUE.equals(enabled);
	}

	private void postMaterialIssueLine(WorkOrderRow workOrder, ProductionDocumentRow issue, MaterialIssueLineRow line,
			CurrentUser operator, HttpServletRequest servletRequest, OffsetDateTime now) {
		WorkOrderMaterialRow material = lockWorkOrderMaterial(workOrder.id(), line.workOrderMaterialId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PRODUCTION_MATERIAL_INVALID));
		validateEnabledWarehouse(line.warehouseId());
		if (!line.warehouseId().equals(workOrder.issueWarehouseId())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_WAREHOUSE_MISMATCH);
		}
		validateEnabledMaterial(material.materialId());
		validateEnabledUnit(material.unitId());
		if (material.issuedQuantity().add(line.quantity()).compareTo(material.requiredQuantity()) > 0) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_EXCEEDS_REQUIRED);
		}
		List<InventoryTrackingService.ResolvedTrackingAllocation> allocations = this.inventoryTrackingService
			.resolveStoredOutboundAllocations(MATERIAL_ISSUE_SOURCE, issue.id(), line.id(), line.warehouseId(),
					material.materialId(), material.unitId(), line.quantity(), "trackingAllocations");
		InventoryPostingService.PostingResult posting = null;
		for (InventoryTrackingService.ResolvedTrackingAllocation allocation : allocations) {
			boolean consumedReservation = consumeMaterialReservation(material.id(), allocation, operator,
					servletRequest);
			InventoryPostingService.PostingResult current = this.inventoryPostingService.post(
					new InventoryPostingService.PostingRequest(InventoryMovementType.PRODUCTION_ISSUE,
							InventoryDirection.OUT, line.warehouseId(), material.materialId(), material.unitId(),
							allocation.quantity(), InventoryQualityStatus.QUALIFIED, MATERIAL_ISSUE_SOURCE, issue.id(),
							line.id(), issue.businessDate(), issue.reason(), line.remark(), operator.username(),
							consumedReservation, allocation.batchId(), allocation.serialId(),
							InventoryAvailabilityService.PRODUCTION_WORK_ORDER_SOURCE, material.id()));
			this.inventoryTrackingService.attachMovement(allocation.allocationId(), current.movementId());
			this.inventoryTrackingService.markOutboundPosted(allocation, current.movementId(), operator.username());
			if (posting == null) {
				posting = current;
			}
			else {
				posting = new InventoryPostingService.PostingResult(posting.beforeQuantity(), current.afterQuantity(),
						current.movementId());
			}
		}
		if (posting == null) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
		this.jdbcTemplate.update("""
				update mfg_work_order_material
				set issued_quantity = issued_quantity + ?, updated_at = ?, version = version + 1
				where id = ?
				""", line.quantity(), now, material.id());
		this.jdbcTemplate.update("""
				update mfg_material_issue_line
				set before_quantity = ?, after_quantity = ?, updated_at = ?
				where id = ?
				""", posting.beforeQuantity(), posting.afterQuantity(), now, line.id());
	}

	private boolean consumeMaterialReservation(Long workOrderMaterialId,
			InventoryTrackingService.ResolvedTrackingAllocation allocation, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (allocation.batchId() != null || allocation.serialId() != null) {
			return this.inventoryAvailabilityService.consumeTrackedBySourceLine(InventoryReservationType.RESERVATION,
					InventoryAvailabilityService.PRODUCTION_WORK_ORDER_SOURCE, workOrderMaterialId,
					allocation.quantity(), allocation.batchId(), allocation.serialId(), operator, servletRequest);
		}
		return this.inventoryAvailabilityService.consumeBySourceLine(InventoryReservationType.RESERVATION,
				InventoryAvailabilityService.PRODUCTION_WORK_ORDER_SOURCE, workOrderMaterialId, allocation.quantity(),
				operator, servletRequest);
	}

	private ValidatedWorkOrder validateWorkOrderRequest(WorkOrderRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.plannedStartDate() == null || request.plannedFinishDate() == null
				|| request.plannedFinishDate().isBefore(request.plannedStartDate())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		MaterialRef product = validateProductMaterial(request.productMaterialId());
		BomRef bom = validateBom(request.bomId(), product.id(), request.plannedStartDate());
		BigDecimal plannedQuantity = validatePositiveProductionQuantity(request.plannedQuantity());
		validateEnabledWarehouse(request.issueWarehouseId());
		validateEnabledWarehouse(request.receiptWarehouseId());
		return new ValidatedWorkOrder(product, bom, plannedQuantity, request.issueWarehouseId(),
				request.receiptWarehouseId(), request.plannedStartDate(), request.plannedFinishDate(),
				validateOptionalText(request.remark(), 500));
	}

	private ValidatedMaterialIssue validateMaterialIssueRequest(WorkOrderRow workOrder, Long currentIssueId,
			MaterialIssueRequest request) {
		if (request == null || request.businessDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String reason = validateRequiredText(request.reason(), 200);
		String remark = validateOptionalText(request.remark(), 500);
		if (request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<Long> materialRows = new HashSet<>();
		List<ValidatedMaterialIssueLine> lines = new ArrayList<>();
		for (MaterialIssueLineRequest line : request.lines()) {
			if (line == null || line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())
					|| line.workOrderMaterialId() == null || !materialRows.add(line.workOrderMaterialId())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			WorkOrderMaterialRow material = workOrderMaterial(workOrder.id(), line.workOrderMaterialId())
				.orElseThrow(() -> new BusinessException(ApiErrorCode.PRODUCTION_MATERIAL_INVALID));
			validateEnabledWarehouse(line.warehouseId());
			validateEnabledMaterial(material.materialId());
			validateEnabledUnit(material.unitId());
			BigDecimal quantity = validatePositiveProductionQuantity(line.quantity());
			if (material.issuedQuantity().add(quantity).compareTo(material.requiredQuantity()) > 0) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_EXCEEDS_REQUIRED);
			}
			lines.add(new ValidatedMaterialIssueLine(line.lineNo(), material.id(), line.warehouseId(),
					material.materialId(), material.unitId(), quantity, validateOptionalText(line.remark(), 500),
					line.trackingAllocations() == null ? List.of() : line.trackingAllocations()));
		}
		return new ValidatedMaterialIssue(request.businessDate(), reason, remark, lines);
	}

	private void reserveWorkOrderMaterials(WorkOrderRow workOrder, CurrentUser operator,
			HttpServletRequest servletRequest) {
		for (WorkOrderMaterialRow material : workOrderMaterialRows(workOrder.id())) {
			BigDecimal remainingQuantity = material.requiredQuantity().subtract(material.issuedQuantity());
			if (remainingQuantity.compareTo(ZERO) <= 0) {
				continue;
			}
			this.inventoryAvailabilityService.reserveFromWarehouse(
					new InventoryAvailabilityService.ReservationCommand(InventoryReservationType.RESERVATION,
							workOrder.issueWarehouseId(), material.materialId(), material.unitId(), remainingQuantity,
							InventoryAvailabilityService.PRODUCTION_WORK_ORDER_SOURCE, workOrder.id(), material.id(),
							workOrder.workOrderNo(), workOrder.plannedStartDate(), "生产工单释放预留", null, "PUBLIC", null,
							null, InventoryQualityStatus.QUALIFIED, null, null, null),
					operator, servletRequest);
		}
	}

	private ValidatedReport validateReportRequest(WorkOrderRow workOrder, WorkReportRequest request) {
		if (request == null || request.businessDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		BigDecimal qualified = validateNonNegativeProductionQuantity(request.qualifiedQuantity());
		BigDecimal defective = validateNonNegativeProductionQuantity(request.defectiveQuantity());
		BigDecimal total = qualified.add(defective);
		if (total.compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
		}
		if (workOrder.reportedQuantity().add(total).compareTo(workOrder.plannedQuantity()) > 0) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_REPORT_EXCEEDS_PLAN);
		}
		String reporterName = hasText(request.reporterName()) ? validateRequiredText(request.reporterName(), 64)
				: "未指定";
		return new ValidatedReport(request.businessDate(), qualified, defective, reporterName,
				validateOptionalText(request.remark(), 500));
	}

	private ValidatedReceipt validateReceiptRequest(WorkOrderRow workOrder, CompletionReceiptRequest request) {
		if (request == null || request.businessDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		Long receiptWarehouseId = request.receiptWarehouseId() == null ? workOrder.receiptWarehouseId()
				: request.receiptWarehouseId();
		validateEnabledWarehouse(receiptWarehouseId);
		BigDecimal quantity = validatePositiveProductionQuantity(request.quantity());
		if (workOrder.receivedQuantity().add(quantity).compareTo(workOrder.qualifiedQuantity()) > 0) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_RECEIPT_EXCEEDS_REPORTED);
		}
		return new ValidatedReceipt(request.businessDate(), receiptWarehouseId, quantity,
				validateOptionalUnitCost(request.provisionalUnitCost()), validateOptionalText(request.remark(), 500),
				request.trackingAllocations());
	}

	private BigDecimal validateOptionalUnitCost(BigDecimal value) {
		if (value == null) {
			return null;
		}
		if (value.compareTo(ZERO) < 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.INVENTORY_VALUATION_UNIT_COST_REQUIRED);
		}
		return value;
	}

	private MaterialRef validateProductMaterial(Long materialId) {
		if (materialId == null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_PRODUCT_MATERIAL_INVALID);
		}
		MaterialRef material = materialRef(materialId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PRODUCTION_PRODUCT_MATERIAL_INVALID));
		if (!"ENABLED".equals(material.status()) || (!"FINISHED_GOOD".equals(material.materialType())
				&& !"SEMI_FINISHED".equals(material.materialType()))) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_PRODUCT_MATERIAL_INVALID);
		}
		return material;
	}

	private MaterialRef validateEnabledMaterial(Long materialId) {
		if (materialId == null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_MATERIAL_INVALID);
		}
		MaterialRef material = materialRef(materialId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PRODUCTION_MATERIAL_INVALID));
		if (!"ENABLED".equals(material.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_MATERIAL_INVALID);
		}
		return material;
	}

	private BomRef validateBom(Long bomId, Long productMaterialId, LocalDate businessDate) {
		if (bomId == null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_BOM_INVALID);
		}
		BomRef bom = bomRef(bomId).orElseThrow(() -> new BusinessException(ApiErrorCode.PRODUCTION_BOM_INVALID));
		if (!"ENABLED".equals(bom.status()) || !bom.parentMaterialId().equals(productMaterialId)
				|| bom.baseQuantity().compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_BOM_INVALID);
		}
		if (!isBomEffectiveOn(bom, businessDate)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_BOM_EFFECTIVE_DATE_INVALID);
		}
		validateEnabledUnit(bom.baseUnitId());
		return bom;
	}

	private boolean isBomEffectiveOn(BomRef bom, LocalDate businessDate) {
		return businessDate != null && (bom.effectiveFrom() == null || !businessDate.isBefore(bom.effectiveFrom()))
				&& (bom.effectiveTo() == null || !businessDate.isAfter(bom.effectiveTo()));
	}

	private List<BomItemRef> validateBomItems(Long bomId) {
		List<BomItemRef> items = bomItems(bomId);
		if (items.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_BOM_EMPTY_ITEMS);
		}
		for (BomItemRef item : items) {
			validateEnabledMaterial(item.material().id());
			if ("LEGACY_BUSINESS_UNIT".equals(item.quantityBasis()) || item.baseUnitId() == null
					|| item.baseQuantity() == null) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_UNIT_CONVERSION_REQUIRED);
			}
			validateEnabledUnit(item.businessUnitId());
			validateEnabledUnit(item.baseUnitId());
			validatePositiveProductionQuantity(item.businessQuantity());
			validatePositiveProductionQuantity(item.baseQuantity());
		}
		return items;
	}

	private void validateEnabledWarehouse(Long warehouseId) {
		if (warehouseId == null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WAREHOUSE_INVALID);
		}
		String status = this.jdbcTemplate.query("select status from mst_warehouse where id = ?",
				(rs, rowNum) -> rs.getString("status"), warehouseId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WAREHOUSE_INVALID);
		}
	}

	private void validateEnabledUnit(Long unitId) {
		if (unitId == null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_UNIT_INVALID);
		}
		String status = this.jdbcTemplate.query("select status from mst_unit where id = ?",
				(rs, rowNum) -> rs.getString("status"), unitId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_UNIT_INVALID);
		}
	}

	private BigDecimal validatePositiveProductionQuantity(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) <= 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
		}
		return value;
	}

	private BigDecimal validateNonNegativeProductionQuantity(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) < 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
		}
		return value;
	}

	private void requireExecutableWorkOrder(WorkOrderRow workOrder) {
		if (workOrder.status() != ProductionWorkOrderStatus.RELEASED
				&& workOrder.status() != ProductionWorkOrderStatus.IN_PROGRESS) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WORK_ORDER_STATUS_INVALID);
		}
	}

	private void markWorkOrderInProgress(Long workOrderId, String operatorName, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				update mfg_work_order
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				and status <> ?
				""", ProductionWorkOrderStatus.IN_PROGRESS.name(), operatorName, now, workOrderId,
				ProductionWorkOrderStatus.IN_PROGRESS.name());
	}

	private CreatedDocument insertWorkOrderWithRetry(ValidatedWorkOrder workOrder, String operatorName,
			OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String workOrderNo = nextNo("MFG-WO", WORK_ORDER_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into mfg_work_order (
							work_order_no, product_material_id, bom_id, planned_quantity, issue_warehouse_id,
							receipt_warehouse_id, planned_start_date, planned_finish_date, status, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, workOrderNo, workOrder.productMaterial().id(), workOrder.bom().id(),
						workOrder.plannedQuantity(), workOrder.issueWarehouseId(), workOrder.receiptWarehouseId(),
						workOrder.plannedStartDate(), workOrder.plannedFinishDate(),
						ProductionWorkOrderStatus.DRAFT.name(), blankToNull(workOrder.remark()), operatorName, now,
						operatorName, now);
				return new CreatedDocument(id, workOrderNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_mfg_work_order_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private CreatedDocument insertMaterialIssueWithRetry(Long workOrderId, ValidatedMaterialIssue issue,
			String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String issueNo = nextNo("MFG-ISS", MATERIAL_ISSUE_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into mfg_material_issue (
							issue_no, work_order_id, status, business_date, reason, remark, created_by, created_at,
							updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, issueNo, workOrderId, ProductionDocumentStatus.DRAFT.name(),
						issue.businessDate(), issue.reason(), blankToNull(issue.remark()), operatorName, now,
						operatorName, now);
				return new CreatedDocument(id, issueNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_mfg_material_issue_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private CreatedDocument insertReportWithRetry(Long workOrderId, ValidatedReport report, String operatorName,
			OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String reportNo = nextNo("MFG-RPT", REPORT_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into mfg_work_report (
							report_no, work_order_id, status, business_date, qualified_quantity, defective_quantity,
							reporter_name, remark, created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, reportNo, workOrderId, ProductionDocumentStatus.DRAFT.name(),
						report.businessDate(), report.qualifiedQuantity(), report.defectiveQuantity(),
						report.reporterName(), blankToNull(report.remark()), operatorName, now, operatorName, now);
				return new CreatedDocument(id, reportNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_mfg_work_report_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private CreatedDocument insertReceiptWithRetry(Long workOrderId, ValidatedReceipt receipt, String operatorName,
			OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String receiptNo = nextNo("MFG-RCP", RECEIPT_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into mfg_completion_receipt (
							receipt_no, work_order_id, status, business_date, receipt_warehouse_id, quantity,
							provisional_unit_cost, remark, created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, receiptNo, workOrderId, ProductionDocumentStatus.DRAFT.name(),
						receipt.businessDate(), receipt.receiptWarehouseId(), receipt.quantity(),
						receipt.provisionalUnitCost(),
						blankToNull(receipt.remark()), operatorName, now, operatorName, now);
				return new CreatedDocument(id, receiptNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_mfg_completion_receipt_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void prepareCompletionReceiptAllocations(Long receiptId, MaterialRef product, ValidatedReceipt receipt,
			String operatorName) {
		this.inventoryTrackingService.prepareInboundAllocations(COMPLETION_RECEIPT_SOURCE, receiptId, receiptId,
				receipt.receiptWarehouseId(), product.id(), product.unitId(), receipt.quantity(),
				InventoryQualityStatus.PENDING_INSPECTION, receipt.businessDate(), receipt.trackingAllocations(),
				operatorName, "trackingAllocations");
	}

	private void prepareMaterialIssueAllocations(Long issueId, ValidatedMaterialIssue issue, String operatorName) {
		List<MaterialIssueLineRow> rows = materialIssueLineRows(issueId);
		for (int i = 0; i < issue.lines().size(); i++) {
			ValidatedMaterialIssueLine line = issue.lines().get(i);
			MaterialIssueLineRow row = rows.stream()
				.filter((candidate) -> candidate.workOrderMaterialId().equals(line.workOrderMaterialId()))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.CONFLICT));
			this.inventoryTrackingService.prepareOutboundAllocations(MATERIAL_ISSUE_SOURCE, issueId, row.id(),
					line.warehouseId(), line.materialId(), line.unitId(), line.quantity(), line.trackingAllocations(),
					operatorName, "lines[" + i + "].trackingAllocations");
		}
	}

	private void insertMaterialIssueLines(Long issueId, List<ValidatedMaterialIssueLine> lines, OffsetDateTime now) {
		for (ValidatedMaterialIssueLine line : lines) {
			this.jdbcTemplate.update("""
					insert into mfg_material_issue_line (
						issue_id, work_order_material_id, line_no, warehouse_id, material_id, unit_id, quantity,
						remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", issueId, line.workOrderMaterialId(), line.lineNo(), line.warehouseId(), line.materialId(),
					line.unitId(), line.quantity(), blankToNull(line.remark()), now, now);
		}
	}

	private QueryParts workOrderQueryParts(String keyword, String status, Long productMaterialId, LocalDate dateFrom,
			LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(wo.work_order_no ilike ? or pm.code ilike ? or pm.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (hasText(status)) {
			conditions.add("wo.status = ?");
			args.add(parseWorkOrderStatus(status).name());
		}
		if (productMaterialId != null) {
			conditions.add("wo.product_material_id = ?");
			args.add(productMaterialId);
		}
		if (dateFrom != null) {
			conditions.add("wo.planned_start_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("wo.planned_start_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private WorkOrderDetailResponse workOrderDetail(WorkOrderSummaryResponse summary, CurrentUser currentUser) {
		CompletionValuationState completionValuationState = completionValuationState(summary.productMaterialId(),
				currentUser);
		return new WorkOrderDetailResponse(summary.id(), summary.workOrderNo(), summary.productMaterialId(),
				summary.productMaterialCode(), summary.productMaterialName(), summary.bomId(), summary.bomCode(),
				summary.bomVersionCode(), summary.plannedQuantity(), summary.reportedQuantity(),
				summary.qualifiedQuantity(), summary.defectiveQuantity(), summary.receivedQuantity(),
				summary.issueWarehouseId(), summary.issueWarehouseName(), summary.receiptWarehouseId(),
				summary.receiptWarehouseName(), summary.plannedStartDate(), summary.plannedFinishDate(),
				summary.status(), summary.remark(), summary.createdByName(), summary.createdAt(),
				summary.updatedAt(), summary.releasedByName(), summary.releasedAt(), summary.completedByName(),
				summary.completedAt(), summary.cancelledByName(), summary.cancelledAt(),
				completionValuationState.state(), completionValuationState.requiresManualProvisionalUnitCost(),
				completionValuationState.currentAverageUnitCost(), completionValuationState.costVisible(),
				workOrderMaterials(summary.id()), materialIssues(summary.id(), 1, 100).items(),
				reports(summary.id(), 1, 100).items(), completionReceipts(summary.id(), 1, 100).items(),
				productionMovements(summary.id()));
	}

	private CompletionValuationState completionValuationState(Long materialId, CurrentUser currentUser) {
		boolean costVisible = currentUser == null || currentUser.permissions().contains("inventory:valuation:view");
		if (!materialValueEnabled(materialId)) {
			return new CompletionValuationState("NON_VALUED", false, null, costVisible);
		}
		Optional<BigDecimal> currentAverage = this.inventoryValuationService.currentPublicAverageUnitCost(materialId);
		if (currentAverage.isPresent()) {
			return new CompletionValuationState("CURRENT_AVERAGE_PROVISIONAL", false,
					costVisible ? currentAverage.get().setScale(6, java.math.RoundingMode.HALF_UP).toPlainString() : null,
					costVisible);
		}
		return new CompletionValuationState("MANUAL_PROVISIONAL_REQUIRED", true, null, costVisible);
	}

	private Optional<WorkOrderSummaryResponse> workOrderSummary(Long id) {
		return this.jdbcTemplate.query("""
				select wo.id, wo.work_order_no, wo.product_material_id, pm.code as product_material_code,
				       pm.name as product_material_name, wo.bom_id, b.bom_code, b.version_code,
				       wo.planned_quantity, wo.reported_quantity, wo.qualified_quantity, wo.defective_quantity,
				       wo.received_quantity, wo.issue_warehouse_id, iw.name as issue_warehouse_name,
				       wo.receipt_warehouse_id, rw.name as receipt_warehouse_name, wo.planned_start_date,
				       wo.planned_finish_date, wo.status, wo.remark, wo.created_by, wo.created_at, wo.updated_at,
				       wo.released_by, wo.released_at, wo.completed_by, wo.completed_at, wo.cancelled_by,
				       wo.cancelled_at
				from mfg_work_order wo
				join mst_material pm on pm.id = wo.product_material_id
				join mfg_bom b on b.id = wo.bom_id
				join mst_warehouse iw on iw.id = wo.issue_warehouse_id
				join mst_warehouse rw on rw.id = wo.receipt_warehouse_id
				where wo.id = ?
				""", this::mapWorkOrderSummary, id).stream().findFirst();
	}

	private List<WorkOrderMaterialResponse> workOrderMaterials(Long workOrderId) {
		return this.jdbcTemplate.query("""
				select wom.id, wom.line_no, wom.bom_item_id, wom.material_id, m.code as material_code,
				       m.name as material_name, m.material_type, wom.unit_id, u.name as unit_name,
				       wom.required_quantity, wom.issued_quantity,
				       (wom.required_quantity - wom.issued_quantity) as remaining_quantity, wom.loss_rate,
				       wom.remark, coalesce(sb.quantity_on_hand, 0.000000) as qualified_quantity_on_hand,
				       coalesce(locked.reserved_quantity, 0.000000) as reserved_quantity,
				       coalesce(locked.occupied_quantity, 0.000000) as occupied_quantity,
				       coalesce(own.own_quantity, 0.000000) as own_reserved_quantity,
				       wom.business_unit_id, bu.name as business_unit_name, wom.business_quantity,
				       wom.base_unit_id, baseu.name as base_unit_name, wom.base_required_quantity,
				       wom.conversion_id, wom.conversion_rate_snapshot, wom.quantity_scale_snapshot,
				       wom.rounding_mode_snapshot, wom.quantity_basis
				from mfg_work_order_material wom
				join mfg_work_order wo on wo.id = wom.work_order_id
				join mst_material m on m.id = wom.material_id
				join mst_unit u on u.id = wom.unit_id
				join mst_unit bu on bu.id = wom.business_unit_id
				left join mst_unit baseu on baseu.id = wom.base_unit_id
				left join (
					select warehouse_id, material_id, quality_status,
					       sum(quantity_on_hand) as quantity_on_hand,
					       sum(locked_quantity) as locked_quantity
					from inv_stock_balance
					where quality_status = 'QUALIFIED'
					group by warehouse_id, material_id, quality_status
				) sb on sb.warehouse_id = wo.issue_warehouse_id
					and sb.material_id = wom.material_id
					and sb.quality_status = 'QUALIFIED'
				left join (
					select warehouse_id, material_id,
					       sum(case when reservation_type = 'RESERVATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as reserved_quantity,
					       sum(case when reservation_type = 'OCCUPATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as occupied_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and quality_status = 'QUALIFIED'
					group by warehouse_id, material_id
				) locked on locked.warehouse_id = wo.issue_warehouse_id and locked.material_id = wom.material_id
				left join (
					select source_line_id, sum(quantity - released_quantity - consumed_quantity) as own_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and reservation_type = 'RESERVATION'
					and source_type = 'PRODUCTION_WORK_ORDER'
					group by source_line_id
				) own on own.source_line_id = wom.id
				where wom.work_order_id = ?
				order by wom.line_no asc, wom.id asc
				""", (rs, rowNum) -> {
			BigDecimal remainingQuantity = rs.getBigDecimal("remaining_quantity");
			BigDecimal quantityOnHand = rs.getBigDecimal("qualified_quantity_on_hand");
			BigDecimal ownReservedQuantity = rs.getBigDecimal("own_reserved_quantity");
			BigDecimal reservedQuantity = rs.getBigDecimal("reserved_quantity").subtract(ownReservedQuantity).max(ZERO);
			BigDecimal occupiedQuantity = rs.getBigDecimal("occupied_quantity");
			BigDecimal availableQuantity = quantityOnHand.subtract(reservedQuantity).subtract(occupiedQuantity);
			BigDecimal maxSelectableQuantity = maxSelectableQuantity(remainingQuantity, availableQuantity);
			boolean selectable = maxSelectableQuantity.compareTo(ZERO) > 0;
			return new WorkOrderMaterialResponse(rs.getLong("id"), rs.getInt("line_no"),
					rs.getLong("bom_item_id"), rs.getLong("material_id"), rs.getString("material_code"),
					rs.getString("material_name"), rs.getString("material_type"), rs.getLong("unit_id"),
					rs.getString("unit_name"), rs.getBigDecimal("required_quantity"),
					rs.getBigDecimal("issued_quantity"), remainingQuantity, rs.getBigDecimal("loss_rate"),
					rs.getString("remark"), InventoryQualityStatus.QUALIFIED.name(),
					InventoryQualityStatus.QUALIFIED.displayName(), quantityOnHand, reservedQuantity,
					occupiedQuantity, availableQuantity, availableQuantity, selectable,
					selectable ? null : QUALIFIED_BALANCE_NOT_ENOUGH,
					selectable ? null : QUALIFIED_BALANCE_NOT_ENOUGH_MESSAGE, maxSelectableQuantity,
					rs.getLong("business_unit_id"), rs.getString("business_unit_name"),
					decimalString(rs.getBigDecimal("business_quantity")), rs.getObject("base_unit_id", Long.class),
					rs.getString("base_unit_name"), decimalString(rs.getBigDecimal("base_required_quantity")),
					rs.getObject("conversion_id", Long.class), decimalString(rs.getBigDecimal("conversion_rate_snapshot")),
					rs.getObject("quantity_scale_snapshot", Integer.class), rs.getString("rounding_mode_snapshot"),
					rs.getString("quantity_basis"));
		}, workOrderId);
	}

	private List<ProductionMovementResponse> productionMovements(Long workOrderId) {
		return this.jdbcTemplate.query("""
				select mv.id, mv.movement_no, mv.movement_type, mv.direction, mv.warehouse_id,
				       w.name as warehouse_name, mv.material_id, m.code as material_code, m.name as material_name,
				       mv.unit_id, u.name as unit_name, mv.quantity, mv.before_quantity, mv.after_quantity,
				       mv.source_type, mv.source_id, mv.source_line_id, mv.business_date, mv.reason, mv.remark,
				       mv.operator_name, mv.occurred_at, mv.batch_id, b.batch_no, mv.serial_id, s.serial_no
				from inv_stock_movement mv
				join mst_warehouse w on w.id = mv.warehouse_id
				join mst_material m on m.id = mv.material_id
				join mst_unit u on u.id = mv.unit_id
				left join inv_batch b on b.id = mv.batch_id
				left join inv_serial s on s.id = mv.serial_id
				where (
					mv.source_type = ?
					and mv.source_id in (select id from mfg_material_issue where work_order_id = ?)
				)
				or (
					mv.source_type = ?
					and mv.source_id in (select id from mfg_completion_receipt where work_order_id = ?)
				)
				order by mv.occurred_at desc, mv.id desc
				""", this::mapMovement, MATERIAL_ISSUE_SOURCE, workOrderId, COMPLETION_RECEIPT_SOURCE, workOrderId);
	}

	private Optional<WorkOrderRow> lockWorkOrder(Long id) {
		return this.jdbcTemplate.query("""
				select id, work_order_no, product_material_id, bom_id, planned_quantity, reported_quantity,
				       qualified_quantity, defective_quantity, received_quantity, issue_warehouse_id,
				       receipt_warehouse_id, planned_start_date, planned_finish_date, status, remark
				from mfg_work_order
				where id = ?
				for update
				""", this::mapWorkOrderRow, id).stream().findFirst();
	}

	private Optional<ProductionDocumentRow> lockMaterialIssue(Long workOrderId, Long id) {
		return this.jdbcTemplate.query("""
				select id, issue_no as document_no, work_order_id, status, business_date, reason
				from mfg_material_issue
				where work_order_id = ?
				and id = ?
				for update
				""", this::mapProductionDocumentRow, workOrderId, id).stream().findFirst();
	}

	private Optional<ProductionDocumentRow> lockWorkReport(Long workOrderId, Long id) {
		return this.jdbcTemplate.query("""
				select id, report_no as document_no, work_order_id, status, business_date, null as reason
				from mfg_work_report
				where work_order_id = ?
				and id = ?
				for update
				""", this::mapProductionDocumentRow, workOrderId, id).stream().findFirst();
	}

	private Optional<ProductionDocumentRow> lockCompletionReceipt(Long workOrderId, Long id) {
		return this.jdbcTemplate.query("""
				select id, receipt_no as document_no, work_order_id, status, business_date, null as reason
				from mfg_completion_receipt
				where work_order_id = ?
				and id = ?
				for update
				""", this::mapProductionDocumentRow, workOrderId, id).stream().findFirst();
	}

	private Optional<WorkOrderMaterialRow> workOrderMaterial(Long workOrderId, Long id) {
		return this.jdbcTemplate.query("""
				select id, work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity,
				       issued_quantity, loss_rate, remark
				from mfg_work_order_material
				where work_order_id = ?
				and id = ?
				""", this::mapWorkOrderMaterialRow, workOrderId, id).stream().findFirst();
	}

	private List<WorkOrderMaterialRow> workOrderMaterialRows(Long workOrderId) {
		return this.jdbcTemplate.query("""
				select id, work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity,
				       issued_quantity, loss_rate, remark
				from mfg_work_order_material
				where work_order_id = ?
				order by line_no asc, id asc
				""", this::mapWorkOrderMaterialRow, workOrderId);
	}

	private Optional<WorkOrderMaterialRow> lockWorkOrderMaterial(Long workOrderId, Long id) {
		return this.jdbcTemplate.query("""
				select id, work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity,
				       issued_quantity, loss_rate, remark
				from mfg_work_order_material
				where work_order_id = ?
				and id = ?
				for update
				""", this::mapWorkOrderMaterialRow, workOrderId, id).stream().findFirst();
	}

	private List<MaterialIssueLineRow> materialIssueLineRows(Long issueId) {
		return this.jdbcTemplate.query("""
				select id, issue_id, work_order_material_id, line_no, warehouse_id, material_id, unit_id, quantity,
				       before_quantity, after_quantity, remark
				from mfg_material_issue_line
				where issue_id = ?
				order by line_no asc, id asc
				""", this::mapMaterialIssueLineRow, issueId);
	}

	private Optional<MaterialIssueSummaryResponse> materialIssueSummary(Long workOrderId, Long id) {
		return this.jdbcTemplate.query("""
				select id, issue_no, work_order_id, status, business_date, reason, remark, created_by, created_at,
				       updated_at, posted_by, posted_at,
				       (select count(*) from mfg_material_issue_line l where l.issue_id = i.id) as line_count
				from mfg_material_issue i
				where work_order_id = ?
				and id = ?
				""", this::mapMaterialIssueSummary, workOrderId, id).stream().findFirst();
	}

	private List<MaterialIssueLineResponse> materialIssueLines(Long issueId) {
		return this.jdbcTemplate.query("""
				select l.id, l.work_order_material_id, l.line_no, l.warehouse_id, w.name as warehouse_name,
				       l.material_id, m.code as material_code, m.name as material_name, l.unit_id,
				       u.name as unit_name, l.quantity, l.before_quantity, l.after_quantity, l.remark,
				       m.tracking_method
				from mfg_material_issue_line l
				join mst_warehouse w on w.id = l.warehouse_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.issue_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> {
			InventoryTrackingMethod trackingMethod = InventoryTrackingMethod.valueOf(rs.getString("tracking_method"));
			Long lineId = rs.getLong("id");
			return new MaterialIssueLineResponse(lineId, rs.getLong("work_order_material_id"), rs.getInt("line_no"),
					rs.getLong("warehouse_id"), rs.getString("warehouse_name"), rs.getLong("material_id"),
					rs.getString("material_code"), rs.getString("material_name"), rs.getLong("unit_id"),
					rs.getString("unit_name"), rs.getBigDecimal("quantity"), rs.getBigDecimal("before_quantity"),
					rs.getBigDecimal("after_quantity"), rs.getString("remark"), trackingMethod.name(),
					trackingMethod.displayName(),
					this.inventoryTrackingService.allocationResponses(MATERIAL_ISSUE_SOURCE, issueId, lineId));
		}, issueId);
	}

	private Optional<WorkReportResponse> workReport(Long workOrderId, Long id) {
		return this.jdbcTemplate.query("""
				select id, report_no, work_order_id, status, business_date, qualified_quantity, defective_quantity,
				       (qualified_quantity + defective_quantity) as total_quantity, reporter_name, remark,
				       created_by, created_at, updated_at, posted_by, posted_at
				from mfg_work_report
				where work_order_id = ?
				and id = ?
				""", this::mapWorkReport, workOrderId, id).stream().findFirst();
	}

	private Optional<CompletionReceiptResponse> completionReceiptDetail(Long workOrderId, Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.receipt_no, r.work_order_id, r.status, r.business_date, r.receipt_warehouse_id,
				       w.name as receipt_warehouse_name, r.quantity, r.before_quantity, r.after_quantity,
				       r.provisional_unit_cost, r.unit_cost, r.valuation_state,
				       r.remark, r.created_by, r.created_at, r.updated_at, r.posted_by, r.posted_at,
				       pm.tracking_method
				from mfg_completion_receipt r
				join mfg_work_order wo on wo.id = r.work_order_id
				join mst_material pm on pm.id = wo.product_material_id
				join mst_warehouse w on w.id = r.receipt_warehouse_id
				where r.work_order_id = ?
				and r.id = ?
				""", this::mapCompletionReceipt, workOrderId, id).stream().findFirst();
	}

	private Optional<MaterialRef> materialRef(Long materialId) {
		return this.jdbcTemplate.query("""
				select m.id, m.code, m.name, m.material_type, m.source_type, m.unit_id, u.name as unit_name, m.status
				from mst_material m
				join mst_unit u on u.id = m.unit_id
				where m.id = ?
				""", (rs, rowNum) -> new MaterialRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getString("material_type"), rs.getString("source_type"), rs.getLong("unit_id"),
				rs.getString("unit_name"), rs.getString("status")), materialId).stream().findFirst();
	}

	private Optional<BomRef> bomRef(Long bomId) {
		return this.jdbcTemplate.query("""
				select id, bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id, status,
				       effective_from, effective_to
				from mfg_bom
				where id = ?
				""", (rs, rowNum) -> new BomRef(rs.getLong("id"), rs.getString("bom_code"),
				rs.getLong("parent_material_id"), rs.getString("version_code"), rs.getString("name"),
				rs.getBigDecimal("base_quantity"), rs.getLong("base_unit_id"), rs.getString("status"),
				rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class)),
				bomId)
			.stream()
			.findFirst();
	}

	private List<BomItemRef> bomItems(Long bomId) {
		return this.jdbcTemplate.query("""
				select i.id, i.line_no, i.child_material_id, m.code as material_code, m.name as material_name,
				       m.material_type, m.source_type, m.unit_id as material_unit_id, u.name as material_unit_name,
				       m.status as material_status, i.unit_id, i.quantity, i.loss_rate, i.remark,
				       i.business_unit_id, i.business_quantity, i.base_unit_id, i.base_quantity, i.conversion_id,
				       i.conversion_rate_snapshot, i.quantity_scale_snapshot, i.rounding_mode_snapshot,
				       i.quantity_basis
				from mfg_bom_item i
				join mst_material m on m.id = i.child_material_id
				join mst_unit u on u.id = m.unit_id
				where i.bom_id = ?
				order by i.line_no asc, i.id asc
				""", (rs, rowNum) -> new BomItemRef(rs.getLong("id"), rs.getInt("line_no"),
				new MaterialRef(rs.getLong("child_material_id"), rs.getString("material_code"),
						rs.getString("material_name"), rs.getString("material_type"), rs.getString("source_type"),
						rs.getLong("material_unit_id"), rs.getString("material_unit_name"),
						rs.getString("material_status")),
				rs.getLong("unit_id"), rs.getBigDecimal("quantity"), rs.getObject("business_unit_id", Long.class),
				rs.getBigDecimal("business_quantity"), rs.getObject("base_unit_id", Long.class),
				rs.getBigDecimal("base_quantity"), rs.getObject("conversion_id", Long.class),
				rs.getBigDecimal("conversion_rate_snapshot"), rs.getObject("quantity_scale_snapshot", Integer.class),
				rs.getString("rounding_mode_snapshot"), rs.getString("quantity_basis"), rs.getBigDecimal("loss_rate"),
				rs.getString("remark")), bomId);
	}

	private boolean hasDraftBusiness(Long workOrderId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select (
					(select count(*) from mfg_material_issue where work_order_id = ? and status = 'DRAFT')
					+ (select count(*) from mfg_work_report where work_order_id = ? and status = 'DRAFT')
					+ (select count(*) from mfg_completion_receipt where work_order_id = ? and status = 'DRAFT')
				)
				""", Long.class, workOrderId, workOrderId, workOrderId);
		return count != null && count > 0;
	}

	private boolean hasPostedBusiness(Long workOrderId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select (
					(select count(*) from mfg_material_issue where work_order_id = ? and status = 'POSTED')
					+ (select count(*) from mfg_work_report where work_order_id = ? and status = 'POSTED')
					+ (select count(*) from mfg_completion_receipt where work_order_id = ? and status = 'POSTED')
				)
				""", Long.class, workOrderId, workOrderId, workOrderId);
		return count != null && count > 0;
	}

	private void ensureWorkOrderExists(Long workOrderId) {
		if (workOrderSummary(workOrderId).isEmpty()) {
			throw workOrderNotFound();
		}
	}

	private WorkOrderSummaryResponse mapWorkOrderSummary(ResultSet rs, int rowNum) throws SQLException {
		return new WorkOrderSummaryResponse(rs.getLong("id"), rs.getString("work_order_no"),
				rs.getLong("product_material_id"), rs.getString("product_material_code"),
				rs.getString("product_material_name"), rs.getLong("bom_id"), rs.getString("bom_code"),
				rs.getString("version_code"), rs.getBigDecimal("planned_quantity"),
				rs.getBigDecimal("reported_quantity"), rs.getBigDecimal("qualified_quantity"),
				rs.getBigDecimal("defective_quantity"), rs.getBigDecimal("received_quantity"),
				rs.getLong("issue_warehouse_id"), rs.getString("issue_warehouse_name"),
				rs.getLong("receipt_warehouse_id"), rs.getString("receipt_warehouse_name"),
				rs.getObject("planned_start_date", LocalDate.class), rs.getObject("planned_finish_date", LocalDate.class),
				rs.getString("status"), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("released_by"), rs.getObject("released_at", OffsetDateTime.class),
				rs.getString("completed_by"), rs.getObject("completed_at", OffsetDateTime.class),
				rs.getString("cancelled_by"), rs.getObject("cancelled_at", OffsetDateTime.class));
	}

	private WorkOrderRow mapWorkOrderRow(ResultSet rs, int rowNum) throws SQLException {
		return new WorkOrderRow(rs.getLong("id"), rs.getString("work_order_no"),
				rs.getLong("product_material_id"), rs.getLong("bom_id"), rs.getBigDecimal("planned_quantity"),
				rs.getBigDecimal("reported_quantity"), rs.getBigDecimal("qualified_quantity"),
				rs.getBigDecimal("defective_quantity"), rs.getBigDecimal("received_quantity"),
				rs.getLong("issue_warehouse_id"), rs.getLong("receipt_warehouse_id"),
				rs.getObject("planned_start_date", LocalDate.class), rs.getObject("planned_finish_date", LocalDate.class),
				ProductionWorkOrderStatus.valueOf(rs.getString("status")), rs.getString("remark"));
	}

	private ProductionDocumentRow mapProductionDocumentRow(ResultSet rs, int rowNum) throws SQLException {
		return new ProductionDocumentRow(rs.getLong("id"), rs.getString("document_no"), rs.getLong("work_order_id"),
				ProductionDocumentStatus.valueOf(rs.getString("status")), rs.getObject("business_date", LocalDate.class),
				rs.getString("reason"));
	}

	private WorkOrderMaterialRow mapWorkOrderMaterialRow(ResultSet rs, int rowNum) throws SQLException {
		return new WorkOrderMaterialRow(rs.getLong("id"), rs.getLong("work_order_id"), rs.getInt("line_no"),
				rs.getLong("bom_item_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("required_quantity"), rs.getBigDecimal("issued_quantity"),
				rs.getBigDecimal("loss_rate"), rs.getString("remark"));
	}

	private MaterialIssueSummaryResponse mapMaterialIssueSummary(ResultSet rs, int rowNum) throws SQLException {
		return new MaterialIssueSummaryResponse(rs.getLong("id"), rs.getString("issue_no"),
				rs.getLong("work_order_id"), rs.getString("status"), rs.getObject("business_date", LocalDate.class),
				rs.getString("reason"), rs.getString("remark"), rs.getInt("line_count"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("posted_by"), rs.getObject("posted_at", OffsetDateTime.class));
	}

	private MaterialIssueLineRow mapMaterialIssueLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new MaterialIssueLineRow(rs.getLong("id"), rs.getLong("issue_id"),
				rs.getLong("work_order_material_id"), rs.getInt("line_no"), rs.getLong("warehouse_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"), rs.getString("remark"));
	}

	private WorkReportResponse mapWorkReport(ResultSet rs, int rowNum) throws SQLException {
		return new WorkReportResponse(rs.getLong("id"), rs.getString("report_no"), rs.getLong("work_order_id"),
				rs.getString("status"), rs.getObject("business_date", LocalDate.class),
				rs.getBigDecimal("qualified_quantity"), rs.getBigDecimal("defective_quantity"),
				rs.getBigDecimal("total_quantity"), rs.getString("reporter_name"), rs.getString("remark"),
				rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("posted_by"),
				rs.getObject("posted_at", OffsetDateTime.class));
	}

	private CompletionReceiptResponse mapCompletionReceipt(ResultSet rs, int rowNum) throws SQLException {
		Long receiptId = rs.getLong("id");
		InventoryTrackingMethod trackingMethod = InventoryTrackingMethod.valueOf(rs.getString("tracking_method"));
		return new CompletionReceiptResponse(rs.getLong("id"), rs.getString("receipt_no"),
				rs.getLong("work_order_id"), rs.getString("status"), rs.getObject("business_date", LocalDate.class),
				rs.getLong("receipt_warehouse_id"), rs.getString("receipt_warehouse_name"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
				rs.getBigDecimal("provisional_unit_cost"), rs.getBigDecimal("unit_cost"),
				rs.getString("valuation_state"), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("posted_by"), rs.getObject("posted_at", OffsetDateTime.class), trackingMethod.name(),
				trackingMethod.displayName(),
				this.inventoryTrackingService.allocationResponses(COMPLETION_RECEIPT_SOURCE, receiptId, receiptId));
	}

	private ProductionMovementResponse mapMovement(ResultSet rs, int rowNum) throws SQLException {
		return new ProductionMovementResponse(rs.getLong("id"), rs.getString("movement_no"),
				rs.getString("movement_type"), rs.getString("direction"), rs.getLong("warehouse_id"),
				rs.getString("warehouse_name"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
				rs.getString("source_type"), rs.getLong("source_id"), rs.getLong("source_line_id"),
				rs.getObject("business_date", LocalDate.class), rs.getString("reason"), rs.getString("remark"),
				rs.getString("operator_name"), rs.getObject("occurred_at", OffsetDateTime.class),
				nullableLong(rs, "batch_id"), rs.getString("batch_no"), nullableLong(rs, "serial_id"),
				rs.getString("serial_no"));
	}

	private BusinessException duplicateProductionException(DuplicateKeyException exception) {
		if (containsConstraint(exception, "uk_inv_stock_movement_source")) {
			return new BusinessException(ApiErrorCode.PRODUCTION_MOVEMENT_SOURCE_DUPLICATED);
		}
		if (containsConstraint(exception, "uk_mfg_material_issue_line_order_material")
				|| containsConstraint(exception, "uk_mfg_material_issue_line_no")) {
			return new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private ProductionWorkOrderStatus parseWorkOrderStatus(String value) {
		try {
			return ProductionWorkOrderStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private BusinessException workOrderNotFound() {
		return new BusinessException(ApiErrorCode.PRODUCTION_WORK_ORDER_NOT_FOUND);
	}

	private BusinessException issueNotFound() {
		return new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_NOT_FOUND);
	}

	private BusinessException reportNotFound() {
		return new BusinessException(ApiErrorCode.PRODUCTION_REPORT_NOT_FOUND);
	}

	private BusinessException receiptNotFound() {
		return new BusinessException(ApiErrorCode.PRODUCTION_RECEIPT_NOT_FOUND);
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
		return value;
	}

	private BigDecimal maxSelectableQuantity(BigDecimal requestedQuantity, BigDecimal availableQuantity) {
		BigDecimal normalizedRequested = requestedQuantity == null || requestedQuantity.compareTo(ZERO) < 0 ? ZERO
				: requestedQuantity;
		BigDecimal normalizedAvailable = availableQuantity == null || availableQuantity.compareTo(ZERO) < 0 ? ZERO
				: availableQuantity;
		return normalizedRequested.min(normalizedAvailable);
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

	private static String decimalString(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record WorkOrderRequest(@NotNull Long productMaterialId, @NotNull Long bomId,
			@NotNull BigDecimal plannedQuantity, @NotNull Long issueWarehouseId, @NotNull Long receiptWarehouseId,
			@NotNull LocalDate plannedStartDate, @NotNull LocalDate plannedFinishDate, String remark) {
	}

	public record MaterialIssueLineRequest(@NotNull Integer lineNo, @NotNull Long workOrderMaterialId,
			@NotNull Long warehouseId, @NotNull BigDecimal quantity, String remark,
			@Valid List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	public record MaterialIssueRequest(@NotNull LocalDate businessDate, @NotBlank String reason, String remark,
			@Valid List<MaterialIssueLineRequest> lines) {
	}

	public record WorkReportRequest(@NotNull LocalDate businessDate, @NotNull BigDecimal qualifiedQuantity,
			@NotNull BigDecimal defectiveQuantity, String reporterName, String remark) {
	}

	public record CompletionReceiptRequest(@NotNull LocalDate businessDate, Long receiptWarehouseId,
			@NotNull BigDecimal quantity, BigDecimal provisionalUnitCost, String remark,
			@Valid List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	public record WorkOrderSummaryResponse(Long id, String workOrderNo, Long productMaterialId,
			String productMaterialCode, String productMaterialName, Long bomId, String bomCode, String bomVersionCode,
			BigDecimal plannedQuantity, BigDecimal reportedQuantity, BigDecimal qualifiedQuantity,
			BigDecimal defectiveQuantity, BigDecimal receivedQuantity, Long issueWarehouseId,
			String issueWarehouseName, Long receiptWarehouseId, String receiptWarehouseName, LocalDate plannedStartDate,
			LocalDate plannedFinishDate, String status, String remark, String createdByName, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, String releasedByName, OffsetDateTime releasedAt, String completedByName,
			OffsetDateTime completedAt, String cancelledByName, OffsetDateTime cancelledAt) {
	}

	public record WorkOrderMaterialResponse(Long id, Integer lineNo, Long bomItemId, Long materialId,
			String materialCode, String materialName, String materialType, Long unitId, String unitName,
			BigDecimal requiredQuantity, BigDecimal issuedQuantity, BigDecimal remainingQuantity, BigDecimal lossRate,
			String remark, String qualityStatus, String qualityStatusName, BigDecimal quantityOnHand,
			BigDecimal reservedQuantity, BigDecimal occupiedQuantity, BigDecimal availableQuantity,
			BigDecimal availableToPromiseQuantity, boolean selectable, String disabledReasonCode,
			String disabledReason, BigDecimal maxSelectableQuantity, Long businessUnitId, String businessUnitName,
			String businessQuantity, Long baseUnitId, String baseUnitName, String baseRequiredQuantity,
			Long conversionId, String conversionRateSnapshot, Integer quantityScaleSnapshot,
			String roundingModeSnapshot, String quantityBasis) {
	}

	public record WorkOrderDetailResponse(Long id, String workOrderNo, Long productMaterialId,
			String productMaterialCode, String productMaterialName, Long bomId, String bomCode, String bomVersionCode,
			BigDecimal plannedQuantity, BigDecimal reportedQuantity, BigDecimal qualifiedQuantity,
			BigDecimal defectiveQuantity, BigDecimal receivedQuantity, Long issueWarehouseId,
			String issueWarehouseName, Long receiptWarehouseId, String receiptWarehouseName, LocalDate plannedStartDate,
			LocalDate plannedFinishDate, String status, String remark, String createdByName, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, String releasedByName, OffsetDateTime releasedAt, String completedByName,
			OffsetDateTime completedAt, String cancelledByName, OffsetDateTime cancelledAt,
			String completionValuationState, boolean requiresManualProvisionalUnitCost,
			String currentAverageUnitCost, boolean costVisible,
			List<WorkOrderMaterialResponse> materials, List<MaterialIssueSummaryResponse> materialIssues,
			List<WorkReportResponse> reports, List<CompletionReceiptResponse> completionReceipts,
			List<ProductionMovementResponse> movements) {
	}

	public record MaterialIssueSummaryResponse(Long id, String issueNo, Long workOrderId, String status,
			LocalDate businessDate, String reason, String remark, int lineCount, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt) {
	}

	public record MaterialIssueLineResponse(Long id, Long workOrderMaterialId, Integer lineNo, Long warehouseId,
			String warehouseName, Long materialId, String materialCode, String materialName, Long unitId,
			String unitName, BigDecimal quantity, BigDecimal beforeQuantity, BigDecimal afterQuantity, String remark,
			String trackingMethod, String trackingMethodName,
			List<InventoryTrackingService.TrackingAllocationResponse> trackingAllocations) {
	}

	public record MaterialIssueDetailResponse(Long id, String issueNo, Long workOrderId, String status,
			LocalDate businessDate, String reason, String remark, int lineCount, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			List<MaterialIssueLineResponse> lines) {
	}

	public record WorkReportResponse(Long id, String reportNo, Long workOrderId, String status,
			LocalDate businessDate, BigDecimal qualifiedQuantity, BigDecimal defectiveQuantity,
			BigDecimal totalQuantity, String reporterName, String remark, String createdByName, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt) {
	}

	public record CompletionReceiptResponse(Long id, String receiptNo, Long workOrderId, String status,
			LocalDate businessDate, Long receiptWarehouseId, String receiptWarehouseName, BigDecimal quantity,
			BigDecimal beforeQuantity, BigDecimal afterQuantity, BigDecimal provisionalUnitCost, BigDecimal unitCost,
			String valuationState, String remark, String createdByName, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			String trackingMethod, String trackingMethodName,
			List<InventoryTrackingService.TrackingAllocationResponse> trackingAllocations) {
	}

	public record ProductionMovementResponse(Long id, String movementNo, String movementType, String direction,
			Long warehouseId, String warehouseName, Long materialId, String materialCode, String materialName,
			Long unitId, String unitName, BigDecimal quantity, BigDecimal beforeQuantity, BigDecimal afterQuantity,
			String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String remark,
			String operatorName, OffsetDateTime occurredAt, Long batchId, String batchNo, Long serialId,
			String serialNo) {
	}

	private record ValidatedWorkOrder(MaterialRef productMaterial, BomRef bom, BigDecimal plannedQuantity,
			Long issueWarehouseId, Long receiptWarehouseId, LocalDate plannedStartDate, LocalDate plannedFinishDate,
			String remark) {
	}

	private record ValidatedMaterialIssue(LocalDate businessDate, String reason, String remark,
			List<ValidatedMaterialIssueLine> lines) {
	}

	private record ValidatedMaterialIssueLine(Integer lineNo, Long workOrderMaterialId, Long warehouseId,
			Long materialId, Long unitId, BigDecimal quantity, String remark,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	private record ValidatedReport(LocalDate businessDate, BigDecimal qualifiedQuantity, BigDecimal defectiveQuantity,
			String reporterName, String remark) {
	}

	private record ValidatedReceipt(LocalDate businessDate, Long receiptWarehouseId, BigDecimal quantity,
			BigDecimal provisionalUnitCost, String remark,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	private record CompletionValuation(BigDecimal unitCost, String valuationState) {
	}

	private record CompletionValuationState(String state, boolean requiresManualProvisionalUnitCost,
			String currentAverageUnitCost, boolean costVisible) {
	}

	private record WorkOrderRow(Long id, String workOrderNo, Long productMaterialId, Long bomId,
			BigDecimal plannedQuantity, BigDecimal reportedQuantity, BigDecimal qualifiedQuantity,
			BigDecimal defectiveQuantity, BigDecimal receivedQuantity, Long issueWarehouseId, Long receiptWarehouseId,
			LocalDate plannedStartDate, LocalDate plannedFinishDate, ProductionWorkOrderStatus status, String remark) {
	}

	private record WorkOrderMaterialRow(Long id, Long workOrderId, Integer lineNo, Long bomItemId, Long materialId,
			Long unitId, BigDecimal requiredQuantity, BigDecimal issuedQuantity, BigDecimal lossRate, String remark) {
	}

	private record ProductionDocumentRow(Long id, String documentNo, Long workOrderId, ProductionDocumentStatus status,
			LocalDate businessDate, String reason) {
	}

	private record MaterialIssueLineRow(Long id, Long issueId, Long workOrderMaterialId, Integer lineNo,
			Long warehouseId, Long materialId, Long unitId, BigDecimal quantity, BigDecimal beforeQuantity,
			BigDecimal afterQuantity, String remark) {
	}

	private record CreatedDocument(Long id, String documentNo) {
	}

	private record MaterialRef(Long id, String code, String name, String materialType, String sourceType, Long unitId,
			String unitName, String status) {
	}

	private record BomRef(Long id, String bomCode, Long parentMaterialId, String versionCode, String name,
			BigDecimal baseQuantity, Long baseUnitId, String status, LocalDate effectiveFrom, LocalDate effectiveTo) {
	}

	private record BomItemRef(Long id, Integer lineNo, MaterialRef material, Long unitId, BigDecimal quantity,
			Long businessUnitId, BigDecimal businessQuantity, Long baseUnitId, BigDecimal baseQuantity,
			Long conversionId, BigDecimal conversionRateSnapshot, Integer quantityScaleSnapshot,
			String roundingModeSnapshot, String quantityBasis, BigDecimal lossRate, String remark) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
