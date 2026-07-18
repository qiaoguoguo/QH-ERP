package com.qherp.api.system.production.outsourcing;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.inventory.InventoryPostingService;
import com.qherp.api.system.inventory.InventoryQualityStatus;
import com.qherp.api.system.inventory.InventoryTrackingService;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
import com.qherp.api.system.production.ProductionActionIdempotencyService;
import com.qherp.api.system.production.ProductionInventoryPostingCoordinator;
import com.qherp.api.system.production.ProductionOwnershipPolicy;
import jakarta.servlet.http.HttpServletRequest;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ProductionOutsourcingService {

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final BigDecimal ONE = BigDecimal.ONE;

	private static final String ORDER_TARGET = "MFG_OUTSOURCING_ORDER";

	private static final String ISSUE_TARGET = "MFG_OUTSOURCING_ISSUE";

	private static final String RECEIPT_TARGET = "MFG_OUTSOURCING_RECEIPT";

	private static final String ISSUE_SOURCE_TYPE = "PRODUCTION_OUTSOURCING_ISSUE";

	private static final String RECEIPT_SOURCE_TYPE = "PRODUCTION_OUTSOURCING_RECEIPT";

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger ORDER_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger ISSUE_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger RECEIPT_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final ProductionInventoryPostingCoordinator productionInventoryPostingCoordinator;

	private final ProductionOwnershipPolicy productionOwnershipPolicy;

	private final InventoryTrackingService inventoryTrackingService;

	private final BusinessPeriodGuard businessPeriodGuard;

	private final AuditService auditService;

	private final ProductionActionIdempotencyService actionIdempotencyService;

	public ProductionOutsourcingService(JdbcTemplate jdbcTemplate,
			ProductionInventoryPostingCoordinator productionInventoryPostingCoordinator,
			ProductionOwnershipPolicy productionOwnershipPolicy, BusinessPeriodGuard businessPeriodGuard,
			AuditService auditService, ProductionActionIdempotencyService actionIdempotencyService,
			InventoryTrackingService inventoryTrackingService) {
		this.jdbcTemplate = jdbcTemplate;
		this.productionInventoryPostingCoordinator = productionInventoryPostingCoordinator;
		this.productionOwnershipPolicy = productionOwnershipPolicy;
		this.inventoryTrackingService = inventoryTrackingService;
		this.businessPeriodGuard = businessPeriodGuard;
		this.auditService = auditService;
		this.actionIdempotencyService = actionIdempotencyService;
	}

	@Transactional(readOnly = true)
	public OutsourcingOrderDetailResponse order(Long id, CurrentUser currentUser) {
		return orderDetail(id, requireCurrentUser(currentUser));
	}

	@Transactional(readOnly = true)
	public PageResponse<OutsourcingOrderSummaryResponse> orders(String keyword, Long projectId, Long supplierId,
			Long productMaterialId, String status, LocalDate plannedDateFrom, LocalDate plannedDateTo, int page,
			int pageSize, CurrentUser currentUser) {
		CurrentUser viewer = requireCurrentUser(currentUser);
		QueryParts queryParts = outsourcingOrderQueryParts(keyword, projectId, supplierId, productMaterialId, status,
				plannedDateFrom, plannedDateTo);
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_outsourcing_order o
				join mst_material pm on pm.id = o.product_material_id
				left join mst_supplier s on s.id = o.supplier_id
				left join mfg_bom b on b.id = o.bom_id
				left join mst_warehouse iw on iw.id = o.issue_warehouse_id
				left join mst_warehouse rw on rw.id = o.receipt_warehouse_id
				left join sal_project p on p.id = o.project_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<OutsourcingOrderSummaryResponse> items = this.jdbcTemplate.query("""
				select o.*, s.code as supplier_code, s.name as supplier_name,
				       pm.code as product_material_code, pm.name as product_material_name, u.name as unit_name,
				       b.bom_code, b.version_code as bom_version_code,
				       iw.name as issue_warehouse_name, rw.name as receipt_warehouse_name,
				       p.project_no, p.name as project_name,
				       coalesce(rr.rejected_quantity, 0.000000) as rejected_quantity
				from mfg_outsourcing_order o
				join mst_material pm on pm.id = o.product_material_id
				left join mst_unit u on u.id = pm.unit_id
				left join mst_supplier s on s.id = o.supplier_id
				left join mfg_bom b on b.id = o.bom_id
				left join mst_warehouse iw on iw.id = o.issue_warehouse_id
				left join mst_warehouse rw on rw.id = o.receipt_warehouse_id
				left join sal_project p on p.id = o.project_id
				left join (
					select outsourcing_order_id, sum(rejected_quantity) as rejected_quantity
					from mfg_outsourcing_receipt
					where status <> 'CANCELLED'
					group by outsourcing_order_id
				) rr on rr.outsourcing_order_id = o.id
				%s
				order by o.updated_at desc, o.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapOrderSummary(rs, rowNum, viewer),
				args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional
	public OutsourcingOrderDetailResponse createOrder(OutsourcingOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = this.actionIdempotencyService.fingerprint("OUTSOURCING_ORDER_CREATE", ORDER_TARGET, 0L,
				null, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_ORDER_CREATE", ORDER_TARGET, 0L, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return orderDetail(existing.get().resultResourceId(), operator);
		}
		ValidatedOrder validated = validateOrderRequest(request);
		OffsetDateTime now = OffsetDateTime.now();
		Long id = insertOrderWithRetry(validated, operator.username(), now);
		String orderNo = this.jdbcTemplate.queryForObject(
				"select outsourcing_order_no from mfg_outsourcing_order where id = ?", String.class, id);
		this.auditService.record(operator, "MFG_OUTSOURCING_ORDER_CREATE", ORDER_TARGET, id, orderNo, servletRequest);
		OutsourcingOrderDetailResponse detail = orderDetail(id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_ORDER_CREATE", ORDER_TARGET, 0L, null,
				request.idempotencyKey(), fingerprint, ORDER_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional
	public OutsourcingOrderDetailResponse updateOrder(Long id, OutsourcingOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = this.actionIdempotencyService.fingerprint("OUTSOURCING_ORDER_UPDATE", ORDER_TARGET, id,
				request.version(), request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_ORDER_UPDATE", ORDER_TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return orderDetail(existing.get().resultResourceId(), operator);
		}
		OrderRow current = lockOrder(id).orElseThrow(this::orderNotFound);
		requireVersion(request.version(), current.version());
		if (!"DRAFT".equals(current.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
		ValidatedOrder validated = validateOrderRequest(request);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_outsourcing_order
				set supplier_id = ?, product_material_id = ?, bom_id = ?, planned_quantity = ?,
				    issue_warehouse_id = ?, receipt_warehouse_id = ?, planned_issue_date = ?,
				    planned_receipt_date = ?, ownership_type = ?, project_id = ?, provisional_unit_cost = ?,
				    remark = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", validated.supplierId(), validated.productMaterial().id(), validated.bom().id(),
				validated.plannedQuantity(), validated.issueWarehouseId(), validated.receiptWarehouseId(),
				validated.plannedIssueDate(), validated.plannedReceiptDate(), validated.ownershipType(),
				validated.projectId(), validated.provisionalUnitCost(), blankToNull(validated.remark()),
				operator.username(), now, id);
		this.auditService.record(operator, "MFG_OUTSOURCING_ORDER_UPDATE", ORDER_TARGET, id,
				current.outsourcingOrderNo(), servletRequest);
		OutsourcingOrderDetailResponse detail = orderDetail(id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_ORDER_UPDATE", ORDER_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, ORDER_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional
	public OutsourcingOrderDetailResponse releaseOrder(Long id, OutsourcingActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = actionFingerprint("OUTSOURCING_ORDER_RELEASE", ORDER_TARGET, id, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_ORDER_RELEASE", ORDER_TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return orderDetail(existing.get().resultResourceId(), operator);
		}
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		requireVersion(request.version(), order.version());
		if (!"DRAFT".equals(order.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
		if (order.supplierId() == null || order.bomId() == null || order.issueWarehouseId() == null
				|| order.receiptWarehouseId() == null || order.plannedIssueDate() == null
				|| order.plannedReceiptDate() == null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_PLANNING_SUGGESTION_INVALID);
		}
		validateSupplier(order.supplierId());
		MaterialRef product = validateProductMaterial(order.productMaterialId());
		BomRef bom = validateBom(order.bomId(), product.id(), order.plannedIssueDate());
		validateEnabledWarehouse(order.issueWarehouseId());
		validateEnabledWarehouse(order.receiptWarehouseId());
		List<BomItemRef> items = validateBomItems(bom.id());
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("delete from mfg_outsourcing_order_material where outsourcing_order_id = ?", id);
		BigDecimal factor = order.plannedQuantity().divide(bom.baseQuantity(), 12, RoundingMode.HALF_UP);
		for (BomItemRef item : items) {
			BigDecimal requiredQuantity = factor.multiply(item.baseQuantity())
				.multiply(ONE.add(item.lossRate()))
				.setScale(6, RoundingMode.HALF_UP);
			validatePositiveQuantity(requiredQuantity);
			this.jdbcTemplate.update("""
					insert into mfg_outsourcing_order_material (
						outsourcing_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity,
						issued_quantity, loss_rate, remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", id, item.lineNo(), item.id(), item.material().id(), item.baseUnitId(), requiredQuantity, ZERO,
					item.lossRate(), blankToNull(item.remark()), now, now);
		}
		this.jdbcTemplate.update("""
				update mfg_outsourcing_order
				set status = 'RELEASED', released_by = ?, released_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "MFG_OUTSOURCING_ORDER_RELEASE", ORDER_TARGET, id,
				order.outsourcingOrderNo(), servletRequest);
		OutsourcingOrderDetailResponse detail = orderDetail(id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_ORDER_RELEASE", ORDER_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, ORDER_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional
	public OutsourcingOrderDetailResponse closeOrder(Long id, OutsourcingActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = actionFingerprint("OUTSOURCING_ORDER_CLOSE", ORDER_TARGET, id, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_ORDER_CLOSE", ORDER_TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return orderDetail(existing.get().resultResourceId(), operator);
		}
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		requireVersion(request.version(), order.version());
		if (!"COMPLETED".equals(order.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_outsourcing_order
				set status = 'CLOSED', closed_by = ?, closed_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "MFG_OUTSOURCING_ORDER_CLOSE", ORDER_TARGET, id, order.outsourcingOrderNo(),
				servletRequest);
		OutsourcingOrderDetailResponse detail = orderDetail(id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_ORDER_CLOSE", ORDER_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, ORDER_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional
	public OutsourcingOrderDetailResponse cancelOrder(Long id, OutsourcingActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = actionFingerprint("OUTSOURCING_ORDER_CANCEL", ORDER_TARGET, id, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_ORDER_CANCEL", ORDER_TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return orderDetail(existing.get().resultResourceId(), operator);
		}
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		requireVersion(request.version(), order.version());
		if (!"DRAFT".equals(order.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_outsourcing_order
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "MFG_OUTSOURCING_ORDER_CANCEL", ORDER_TARGET, id,
				order.outsourcingOrderNo(), servletRequest);
		OutsourcingOrderDetailResponse detail = orderDetail(id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_ORDER_CANCEL", ORDER_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, ORDER_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional
	public OutsourcingIssueDetailResponse createIssue(Long orderId, OutsourcingIssueRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = this.actionIdempotencyService.fingerprint("OUTSOURCING_ISSUE_CREATE", ISSUE_TARGET, 0L,
				null, orderId, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_ISSUE_CREATE", ISSUE_TARGET, 0L, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return issueDetail(orderId, existing.get().resultResourceId(), operator);
		}
		OrderRow order = lockOrder(orderId).orElseThrow(this::orderNotFound);
		if (!List.of("RELEASED", "IN_PROGRESS").contains(order.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
		ValidatedIssue validated = validateIssueRequest(order, request);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.CREATE,
				ISSUE_SOURCE_TYPE, null);
		OffsetDateTime now = OffsetDateTime.now();
		Long issueId = insertIssueWithRetry(order.id(), validated, operator.username(), now);
		String issueNo = this.jdbcTemplate.queryForObject("select issue_no from mfg_outsourcing_issue where id = ?",
				String.class, issueId);
		this.auditService.record(operator, "MFG_OUTSOURCING_ISSUE_CREATE", ISSUE_TARGET, issueId, issueNo,
				servletRequest);
		OutsourcingIssueDetailResponse detail = issueDetail(orderId, issueId, operator);
		this.actionIdempotencyService.record("OUTSOURCING_ISSUE_CREATE", ISSUE_TARGET, 0L, null,
				request.idempotencyKey(), fingerprint, ISSUE_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional(readOnly = true)
	public PageResponse<OutsourcingDocumentSummaryResponse> issues(Long orderId, int page, int pageSize,
			CurrentUser currentUser) {
		CurrentUser viewer = requireCurrentUser(currentUser);
		orderRow(orderId).orElseThrow(this::orderNotFound);
		Long total = this.jdbcTemplate.queryForObject(
				"select count(*) from mfg_outsourcing_issue where outsourcing_order_id = ?", Long.class, orderId);
		List<OutsourcingDocumentSummaryResponse> items = this.jdbcTemplate.query("""
				select i.id, i.issue_no as document_no, i.issue_no, null::varchar as receipt_no,
				       i.outsourcing_order_id, i.status, i.business_date, count(l.id)::integer as line_count,
				       i.version
				from mfg_outsourcing_issue i
				left join mfg_outsourcing_issue_line l on l.issue_id = i.id
				where i.outsourcing_order_id = ?
				group by i.id
				order by i.updated_at desc, i.id desc
				limit ? offset ?
				""", (rs, rowNum) -> mapDocumentSummary(rs, rowNum, "production:outsourcing-issue", viewer),
				orderId, limit(pageSize), offset(page, pageSize));
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public OutsourcingIssueDetailResponse issue(Long orderId, Long id, CurrentUser currentUser) {
		return issueDetail(orderId, id, requireCurrentUser(currentUser));
	}

	@Transactional
	public OutsourcingIssueDetailResponse updateIssue(Long orderId, Long id, OutsourcingIssueRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = this.actionIdempotencyService.fingerprint("OUTSOURCING_ISSUE_UPDATE", ISSUE_TARGET, id,
				request.version(), orderId, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_ISSUE_UPDATE", ISSUE_TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return issueDetail(orderId, existing.get().resultResourceId(), operator);
		}
		OrderRow order = lockOrder(orderId).orElseThrow(this::orderNotFound);
		DocumentRow issue = lockIssue(orderId, id).orElseThrow(this::issueNotFound);
		requireVersion(request.version(), issue.version());
		if (!"DRAFT".equals(issue.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
		ValidatedIssue validated = validateIssueRequest(order, request);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.UPDATE,
				ISSUE_SOURCE_TYPE, id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_outsourcing_issue
				set business_date = ?, reason = ?, remark = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", validated.businessDate(), validated.reason(), blankToNull(validated.remark()),
				operator.username(), now, id);
		this.jdbcTemplate.update("delete from mfg_outsourcing_issue_line where issue_id = ?", id);
		this.jdbcTemplate.update("delete from inv_stock_tracking_allocation where document_type = ? and document_id = ?",
				ISSUE_SOURCE_TYPE, id);
		insertIssueLines(id, validated.lines(), operator.username(), now);
		this.auditService.record(operator, "MFG_OUTSOURCING_ISSUE_UPDATE", ISSUE_TARGET, id, issue.documentNo(),
				servletRequest);
		OutsourcingIssueDetailResponse detail = issueDetail(orderId, id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_ISSUE_UPDATE", ISSUE_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, ISSUE_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional
	public OutsourcingIssueDetailResponse cancelIssue(Long orderId, Long id, OutsourcingActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = actionFingerprint("OUTSOURCING_ISSUE_CANCEL", ISSUE_TARGET, id, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_ISSUE_CANCEL", ISSUE_TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return issueDetail(orderId, existing.get().resultResourceId(), operator);
		}
		lockOrder(orderId).orElseThrow(this::orderNotFound);
		DocumentRow issue = lockIssue(orderId, id).orElseThrow(this::issueNotFound);
		requireVersion(request.version(), issue.version());
		if (!"DRAFT".equals(issue.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(issue.businessDate(), BusinessPeriodOperation.CANCEL, ISSUE_SOURCE_TYPE,
				id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_outsourcing_issue
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "MFG_OUTSOURCING_ISSUE_CANCEL", ISSUE_TARGET, id, issue.documentNo(),
				servletRequest);
		OutsourcingIssueDetailResponse detail = issueDetail(orderId, id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_ISSUE_CANCEL", ISSUE_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, ISSUE_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional
	public OutsourcingIssueDetailResponse postIssue(Long orderId, Long id, OutsourcingActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = actionFingerprint("OUTSOURCING_ISSUE_POST", ISSUE_TARGET, id, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_ISSUE_POST", ISSUE_TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return issueDetail(orderId, existing.get().resultResourceId(), operator);
		}
		OrderRow order = lockOrder(orderId).orElseThrow(this::orderNotFound);
		DocumentRow issue = lockIssue(orderId, id).orElseThrow(this::issueNotFound);
		requireVersion(request.version(), issue.version());
		requireExecutableOrderForPosting(order.status());
		if (!"DRAFT".equals(issue.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_DUPLICATE_POST);
		}
		this.businessPeriodGuard.assertWritable(issue.businessDate(), BusinessPeriodOperation.POST, ISSUE_SOURCE_TYPE,
				id);
		List<IssueLineRow> lines = issueLines(id).stream()
			.sorted(Comparator.comparing(IssueLineRow::warehouseId)
				.thenComparing(IssueLineRow::materialId)
				.thenComparing(IssueLineRow::id))
			.toList();
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_EMPTY_LINES);
		}
		this.productionInventoryPostingCoordinator.lockPostingScopes(lines.stream()
			.map((line) -> this.productionInventoryPostingCoordinator.postingScope(line.warehouseId(), line.materialId()))
			.toList());
		OffsetDateTime now = OffsetDateTime.now();
		for (IssueLineRow line : lines) {
			postIssueLine(order, issue, line, operator, now);
		}
		BigDecimal issuedQuantity = lines.stream()
			.map(IssueLineRow::quantity)
			.reduce(ZERO, BigDecimal::add);
		this.jdbcTemplate.update("""
				update mfg_outsourcing_issue
				set status = 'POSTED', posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), now, operator.username(), now, id);
		this.jdbcTemplate.update("""
				update mfg_outsourcing_order
				set status = 'IN_PROGRESS', issued_quantity = issued_quantity + ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				and status in ('RELEASED', 'IN_PROGRESS')
				""", issuedQuantity, operator.username(), now, order.id());
		this.auditService.record(operator, "MFG_OUTSOURCING_ISSUE_POST", ISSUE_TARGET, id, issue.documentNo(),
				servletRequest);
		OutsourcingIssueDetailResponse detail = issueDetail(orderId, id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_ISSUE_POST", ISSUE_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, ISSUE_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional
	public OutsourcingReceiptResponse createReceipt(Long orderId, OutsourcingReceiptRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = this.actionIdempotencyService.fingerprint("OUTSOURCING_RECEIPT_CREATE", RECEIPT_TARGET,
				0L, null, orderId, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_RECEIPT_CREATE", RECEIPT_TARGET, 0L, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return receiptDetail(orderId, existing.get().resultResourceId(), operator);
		}
		OrderRow order = lockOrder(orderId).orElseThrow(this::orderNotFound);
		if (!List.of("RELEASED", "IN_PROGRESS").contains(order.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
		ValidatedReceipt validated = validateReceiptRequest(order, request);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.CREATE,
				RECEIPT_SOURCE_TYPE, null);
		OffsetDateTime now = OffsetDateTime.now();
		Long receiptId = insertReceiptWithRetry(order, validated, operator.username(), now);
		insertReceiptLines(order, receiptId, validated, operator.username(), now);
		String receiptNo = this.jdbcTemplate.queryForObject(
				"select receipt_no from mfg_outsourcing_receipt where id = ?", String.class, receiptId);
		this.auditService.record(operator, "MFG_OUTSOURCING_RECEIPT_CREATE", RECEIPT_TARGET, receiptId, receiptNo,
				servletRequest);
		OutsourcingReceiptResponse detail = receiptDetail(orderId, receiptId, operator);
		this.actionIdempotencyService.record("OUTSOURCING_RECEIPT_CREATE", RECEIPT_TARGET, 0L, null,
				request.idempotencyKey(), fingerprint, RECEIPT_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional(readOnly = true)
	public PageResponse<OutsourcingDocumentSummaryResponse> receipts(Long orderId, int page, int pageSize,
			CurrentUser currentUser) {
		CurrentUser viewer = requireCurrentUser(currentUser);
		orderRow(orderId).orElseThrow(this::orderNotFound);
		Long total = this.jdbcTemplate.queryForObject(
				"select count(*) from mfg_outsourcing_receipt where outsourcing_order_id = ?", Long.class, orderId);
		List<OutsourcingDocumentSummaryResponse> items = this.jdbcTemplate.query("""
				select r.id, r.receipt_no as document_no, null::varchar as issue_no, r.receipt_no,
				       r.outsourcing_order_id, r.status, r.business_date,
				       coalesce(lc.line_count, 0)::integer as line_count, r.version
				from mfg_outsourcing_receipt r
				left join (
				    select receipt_id, count(*) as line_count
				    from mfg_outsourcing_receipt_line
				    group by receipt_id
				) lc on lc.receipt_id = r.id
				where r.outsourcing_order_id = ?
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""", (rs, rowNum) -> mapDocumentSummary(rs, rowNum, "production:outsourcing-receipt", viewer),
				orderId, limit(pageSize), offset(page, pageSize));
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public OutsourcingReceiptResponse receipt(Long orderId, Long id, CurrentUser currentUser) {
		return receiptDetail(orderId, id, requireCurrentUser(currentUser));
	}

	@Transactional
	public OutsourcingReceiptResponse updateReceipt(Long orderId, Long id, OutsourcingReceiptRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = this.actionIdempotencyService.fingerprint("OUTSOURCING_RECEIPT_UPDATE", RECEIPT_TARGET,
				id, request.version(), orderId, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_RECEIPT_UPDATE", RECEIPT_TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return receiptDetail(orderId, existing.get().resultResourceId(), operator);
		}
		OrderRow order = lockOrder(orderId).orElseThrow(this::orderNotFound);
		ReceiptRow receipt = lockReceipt(orderId, id).orElseThrow(this::receiptNotFound);
		requireVersion(request.version(), receipt.version());
		if (!"DRAFT".equals(receipt.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
		ValidatedReceipt validated = validateReceiptRequest(order, request);
		this.businessPeriodGuard.assertWritable(validated.businessDate(), BusinessPeriodOperation.UPDATE,
				RECEIPT_SOURCE_TYPE, id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_outsourcing_receipt
				set business_date = ?, receipt_warehouse_id = ?, quantity = ?, rejected_quantity = ?,
				    provisional_unit_cost = ?, remark = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", validated.businessDate(), validated.receiptWarehouseId(), validated.quantity(),
				validated.rejectedQuantity(), validated.provisionalUnitCost(), blankToNull(validated.remark()),
				operator.username(), now, id);
		this.jdbcTemplate.update("delete from mfg_outsourcing_receipt_line where receipt_id = ?", id);
		this.jdbcTemplate.update("delete from inv_stock_tracking_allocation where document_type = ? and document_id = ?",
				RECEIPT_SOURCE_TYPE, id);
		insertReceiptLines(order, id, validated, operator.username(), now);
		this.auditService.record(operator, "MFG_OUTSOURCING_RECEIPT_UPDATE", RECEIPT_TARGET, id, receipt.receiptNo(),
				servletRequest);
		OutsourcingReceiptResponse detail = receiptDetail(orderId, id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_RECEIPT_UPDATE", RECEIPT_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, RECEIPT_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional
	public OutsourcingReceiptResponse cancelReceipt(Long orderId, Long id, OutsourcingActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = actionFingerprint("OUTSOURCING_RECEIPT_CANCEL", RECEIPT_TARGET, id, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_RECEIPT_CANCEL", RECEIPT_TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return receiptDetail(orderId, existing.get().resultResourceId(), operator);
		}
		lockOrder(orderId).orElseThrow(this::orderNotFound);
		ReceiptRow receipt = lockReceipt(orderId, id).orElseThrow(this::receiptNotFound);
		requireVersion(request.version(), receipt.version());
		if (!"DRAFT".equals(receipt.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(receipt.businessDate(), BusinessPeriodOperation.CANCEL,
				RECEIPT_SOURCE_TYPE, id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_outsourcing_receipt
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "MFG_OUTSOURCING_RECEIPT_CANCEL", RECEIPT_TARGET, id, receipt.receiptNo(),
				servletRequest);
		OutsourcingReceiptResponse detail = receiptDetail(orderId, id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_RECEIPT_CANCEL", RECEIPT_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, RECEIPT_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	@Transactional
	public OutsourcingReceiptResponse postReceipt(Long orderId, Long id, OutsourcingActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		operator = requireCurrentUser(operator);
		String fingerprint = actionFingerprint("OUTSOURCING_RECEIPT_POST", RECEIPT_TARGET, id, request);
		Optional<ProductionActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService.existing(
				"OUTSOURCING_RECEIPT_POST", RECEIPT_TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return receiptDetail(orderId, existing.get().resultResourceId(), operator);
		}
		OrderRow order = lockOrder(orderId).orElseThrow(this::orderNotFound);
		ReceiptRow receipt = lockReceipt(orderId, id).orElseThrow(this::receiptNotFound);
		requireVersion(request.version(), receipt.version());
		requireExecutableOrderForPosting(order.status());
		if (!"DRAFT".equals(receipt.status())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_DUPLICATE_POST);
		}
		this.businessPeriodGuard.assertWritable(receipt.businessDate(), BusinessPeriodOperation.POST,
				RECEIPT_SOURCE_TYPE, id);
		validateProductMaterial(order.productMaterialId());
		validateEnabledWarehouse(receipt.receiptWarehouseId());
		MaterialRef product = validateEnabledMaterial(order.productMaterialId());
		List<ReceiptLineRow> lines = lockReceiptLines(receipt.id());
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.productionInventoryPostingCoordinator.lockPostingScopes(List
			.of(this.productionInventoryPostingCoordinator.postingScope(receipt.receiptWarehouseId(), order.productMaterialId())));
		BigDecimal firstBeforeQuantity = null;
		BigDecimal lastAfterQuantity = null;
		Long lastMovementId = null;
		Long lastValueMovementId = null;
		BigDecimal lastUnitCost = null;
		for (ReceiptLineRow line : lines) {
			if (line.acceptedQuantity().compareTo(ZERO) <= 0) {
				continue;
			}
			BigDecimal provisionalUnitCost = line.provisionalUnitCost() == null
					? (receipt.provisionalUnitCost() == null ? order.provisionalUnitCost()
							: receipt.provisionalUnitCost())
					: line.provisionalUnitCost();
			List<InventoryTrackingService.ResolvedTrackingAllocation> allocations = this.inventoryTrackingService
				.resolveStoredInboundAllocations(RECEIPT_SOURCE_TYPE, receipt.id(), line.id(), order.productMaterialId(),
						line.acceptedQuantity(), InventoryQualityStatus.PENDING_INSPECTION, "trackingAllocations");
			InventoryPostingService.PostingResult posting = null;
			for (InventoryTrackingService.ResolvedTrackingAllocation allocation : allocations) {
				InventoryPostingService.PostingResult current =
						this.productionInventoryPostingCoordinator.postOutsourcingReceipt(
								new ProductionInventoryPostingCoordinator.InboundPostingCommand(receipt.receiptWarehouseId(),
										order.productMaterialId(), product.unitId(), allocation.quantity(),
										InventoryQualityStatus.PENDING_INSPECTION, RECEIPT_SOURCE_TYPE, receipt.id(),
										line.id(), receipt.businessDate(), "外协收货", line.remark(), operator.username(),
										allocation.batchId(), allocation.serialId(), order.ownershipType(), order.projectId(),
										provisionalUnitCost, null, null));
				this.inventoryTrackingService.attachMovement(allocation.allocationId(), current.movementId());
				this.inventoryTrackingService.markInboundPosted(allocation, receipt.receiptWarehouseId(),
						InventoryQualityStatus.PENDING_INSPECTION, current.movementId(), operator.username());
				if (posting == null) {
					posting = current;
				}
				else {
					posting = new InventoryPostingService.PostingResult(posting.beforeQuantity(),
							current.afterQuantity(), current.movementId(), current.unitCost(),
							current.inventoryAmount(), current.valuationMethod(), current.costLayerId(),
							current.valueMovementId());
				}
			}
			if (posting == null) {
				throw new BusinessException(ApiErrorCode.CONFLICT);
			}
			if (firstBeforeQuantity == null) {
				firstBeforeQuantity = posting.beforeQuantity();
			}
			lastAfterQuantity = posting.afterQuantity();
			lastMovementId = posting.movementId();
			lastValueMovementId = posting.valueMovementId();
			lastUnitCost = posting.unitCost();
			this.jdbcTemplate.update("""
					update mfg_outsourcing_receipt_line
					set before_quantity = ?, after_quantity = ?, stock_movement_id = ?, value_movement_id = ?,
					    unit_cost = ?, updated_at = ?, version = version + 1
					where id = ?
					""", posting.beforeQuantity(), posting.afterQuantity(), posting.movementId(),
					posting.valueMovementId(), posting.unitCost(), now, line.id());
		}
		String valuationState = lastValueMovementId == null ? "NON_VALUED" : "MANUAL_PROVISIONAL";
		this.jdbcTemplate.update("""
				update mfg_outsourcing_receipt
				set status = 'POSTED', before_quantity = ?, after_quantity = ?, stock_movement_id = ?,
				    value_movement_id = ?, unit_cost = ?, valuation_state = ?, posted_by = ?, posted_at = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", firstBeforeQuantity, lastAfterQuantity, lastMovementId, lastValueMovementId,
				lastUnitCost, valuationState, operator.username(), now, operator.username(), now, id);
		BigDecimal receivedAfter = order.receivedQuantity().add(receipt.quantity());
		String status = receivedAfter.compareTo(order.plannedQuantity()) >= 0 ? "COMPLETED" : "IN_PROGRESS";
		this.jdbcTemplate.update("""
				update mfg_outsourcing_order
				set received_quantity = received_quantity + ?, status = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", receipt.quantity(), status, operator.username(), now, order.id());
		this.auditService.record(operator, "MFG_OUTSOURCING_RECEIPT_POST", RECEIPT_TARGET, id, receipt.receiptNo(),
				servletRequest);
		OutsourcingReceiptResponse detail = receiptDetail(orderId, id, operator);
		this.actionIdempotencyService.record("OUTSOURCING_RECEIPT_POST", RECEIPT_TARGET, id, request.version(),
				request.idempotencyKey(), fingerprint, RECEIPT_TARGET, detail.id(), detail.version(), operator);
		return detail;
	}

	private void postIssueLine(OrderRow order, DocumentRow issue, IssueLineRow line, CurrentUser operator,
			OffsetDateTime now) {
		OrderMaterialRow material = lockOrderMaterial(order.id(), line.orderMaterialId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PRODUCTION_MATERIAL_INVALID));
		validateEnabledWarehouse(line.warehouseId());
		if (!line.warehouseId().equals(order.issueWarehouseId())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_WAREHOUSE_MISMATCH);
		}
		validateEnabledMaterial(material.materialId());
		validateEnabledUnit(material.unitId());
		if (material.issuedQuantity().add(line.quantity()).compareTo(material.requiredQuantity()) > 0) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_EXCEEDS_REQUIRED);
		}
		List<InventoryTrackingService.ResolvedTrackingAllocation> allocations = this.inventoryTrackingService
			.resolveStoredOutboundAllocations(ISSUE_SOURCE_TYPE, issue.id(), line.id(), line.warehouseId(),
					material.materialId(), material.unitId(), line.quantity(), "trackingAllocations");
		InventoryPostingService.PostingResult posting = null;
		for (InventoryTrackingService.ResolvedTrackingAllocation allocation : allocations) {
			InventoryPostingService.PostingResult current =
					this.productionInventoryPostingCoordinator.postOutsourcingIssue(
							new ProductionInventoryPostingCoordinator.OutboundPostingCommand(line.warehouseId(),
									material.materialId(), material.unitId(), allocation.quantity(), ISSUE_SOURCE_TYPE,
									issue.id(), line.id(), issue.businessDate(), issue.reason(), line.remark(),
									operator.username(), false, allocation.batchId(), allocation.serialId(),
									line.ownershipType(), line.projectId(), line.costLayerId(), null, null, null));
			this.inventoryTrackingService.attachMovement(allocation.allocationId(), current.movementId());
			this.inventoryTrackingService.markOutboundPosted(allocation, current.movementId(), operator.username());
			if (posting == null) {
				posting = current;
			}
			else {
				posting = new InventoryPostingService.PostingResult(posting.beforeQuantity(), current.afterQuantity(),
						current.movementId(), current.unitCost(), current.inventoryAmount(), current.valuationMethod(),
						current.costLayerId(), current.valueMovementId());
			}
		}
		if (posting == null) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
		this.jdbcTemplate.update("""
				update mfg_outsourcing_order_material
				set issued_quantity = issued_quantity + ?, updated_at = ?, version = version + 1
				where id = ?
				""", line.quantity(), now, material.id());
		this.jdbcTemplate.update("""
				update mfg_outsourcing_issue_line
				set before_quantity = ?, after_quantity = ?, stock_movement_id = ?, cost_layer_id = ?,
				    value_movement_id = ?, updated_at = ?
				where id = ?
				""", posting.beforeQuantity(), posting.afterQuantity(), posting.movementId(), posting.costLayerId(),
				posting.valueMovementId(), now, line.id());
	}

	private ValidatedOrder validateOrderRequest(OutsourcingOrderRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.supplierId() == null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_SUPPLIER_REQUIRED);
		}
		LocalDate plannedIssueDate = request.plannedIssueDate() == null ? request.plannedStartDate()
				: request.plannedIssueDate();
		LocalDate plannedReceiptDate = request.plannedReceiptDate() == null ? request.plannedFinishDate()
				: request.plannedReceiptDate();
		validateSupplier(request.supplierId());
		MaterialRef product = validateProductMaterial(request.productMaterialId());
		BomRef bom = validateBom(request.bomId(), product.id(), plannedIssueDate);
		validateEnabledWarehouse(request.issueWarehouseId());
		validateEnabledWarehouse(request.receiptWarehouseId());
		BigDecimal plannedQuantity = validatePositiveQuantity(request.plannedQuantity());
		if (plannedIssueDate == null || plannedReceiptDate == null || plannedIssueDate.isAfter(plannedReceiptDate)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ProductionOwnershipPolicy.Ownership ownership =
				this.productionOwnershipPolicy.normalizeTarget(request.ownershipType(), request.projectId());
		BigDecimal provisionalUnitCost = request.provisionalUnitCost();
		if (provisionalUnitCost != null && (provisionalUnitCost.compareTo(ZERO) < 0
				|| provisionalUnitCost.scale() > 6 || integerDigits(provisionalUnitCost) > 12L)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
		}
		return new ValidatedOrder(request.supplierId(), product, bom, plannedQuantity, request.issueWarehouseId(),
				request.receiptWarehouseId(), plannedIssueDate, plannedReceiptDate, ownership.ownershipType(),
				ownership.projectId(), provisionalUnitCost, request.remark());
	}

	private ValidatedIssue validateIssueRequest(OrderRow order, OutsourcingIssueRequest request) {
		if (request == null || request.businessDate() == null || request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_EMPTY_LINES);
		}
		String reason = hasText(request.reason()) ? request.reason().trim() : "外协发料";
		List<ValidatedIssueLine> lines = request.lines()
			.stream()
			.map((line) -> validateIssueLine(order, request.warehouseId(), line))
			.toList();
		return new ValidatedIssue(request.businessDate(), reason, request.remark(), lines);
	}

	private ValidatedIssueLine validateIssueLine(OrderRow order, Long headerWarehouseId,
			OutsourcingIssueLineRequest request) {
		if (request == null || request.lineNo() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		OrderMaterialRow material = lockOrderMaterial(order.id(), request.orderMaterialId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PRODUCTION_MATERIAL_INVALID));
		Long warehouseId = request.warehouseId() == null ? headerWarehouseId : request.warehouseId();
		validateEnabledWarehouse(warehouseId);
		if (!warehouseId.equals(order.issueWarehouseId())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_ISSUE_WAREHOUSE_MISMATCH);
		}
		validatePositiveQuantity(request.quantity());
		ProductionOwnershipPolicy.Ownership source =
				this.productionOwnershipPolicy.normalizeTarget(request.ownershipType(), request.projectId());
		this.productionOwnershipPolicy.requireLineSourceAllowed(
				new ProductionOwnershipPolicy.Ownership(order.ownershipType(), order.projectId()), source,
				request.costLayerId());
		return new ValidatedIssueLine(request.lineNo(), material.id(), warehouseId, material.materialId(),
				material.unitId(), request.quantity(), source.ownershipType(), source.projectId(),
				"PROJECT".equals(source.ownershipType()) ? request.costLayerId() : null, request.remark(),
				request.trackingAllocations() == null ? List.of() : request.trackingAllocations());
	}

	private ValidatedReceipt validateReceiptRequest(OrderRow order, OutsourcingReceiptRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		Long warehouseId = request.receiptWarehouseId() == null ? order.receiptWarehouseId() : request.receiptWarehouseId();
		validateEnabledWarehouse(warehouseId);
		if (!warehouseId.equals(order.receiptWarehouseId())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_WAREHOUSE_INVALID);
		}
		ReceiptQuantity receiptQuantity = normalizeReceiptQuantity(request);
		if (order.receivedQuantity().add(receiptQuantity.acceptedQuantity()).compareTo(order.plannedQuantity()) > 0) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_RECEIPT_EXCEEDS_REPORTED);
		}
		BigDecimal provisionalUnitCost = request.provisionalUnitCost() == null ? receiptQuantity.provisionalUnitCost()
				: request.provisionalUnitCost();
		if (provisionalUnitCost != null && (provisionalUnitCost.compareTo(ZERO) < 0
				|| provisionalUnitCost.scale() > 6 || integerDigits(provisionalUnitCost) > 12L)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
		}
		return new ValidatedReceipt(request.businessDate(), warehouseId, receiptQuantity.acceptedQuantity(),
				receiptQuantity.rejectedQuantity(), provisionalUnitCost, request.remark(), receiptQuantity.lines());
	}

	private ReceiptQuantity normalizeReceiptQuantity(OutsourcingReceiptRequest request) {
		if (request.businessDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.quantity() != null) {
			BigDecimal quantity = validatePositiveQuantity(request.quantity());
			return new ReceiptQuantity(quantity, ZERO, request.provisionalUnitCost(),
					List.of(new ValidatedReceiptLine(1, quantity, ZERO, request.provisionalUnitCost(),
							request.remark(),
							request.trackingAllocations() == null ? List.of() : request.trackingAllocations())));
		}
		if (request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
		}
		BigDecimal acceptedQuantity = ZERO;
		BigDecimal rejectedQuantity = ZERO;
		BigDecimal provisionalUnitCost = request.provisionalUnitCost();
		List<ValidatedReceiptLine> lines = new ArrayList<>();
		Set<Integer> lineNos = new HashSet<>();
		for (OutsourcingReceiptLineRequest line : request.lines()) {
			if (line == null || line.lineNo() == null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if (!lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.CONFLICT);
			}
			BigDecimal accepted = line.acceptedQuantity() == null ? ZERO : line.acceptedQuantity();
			BigDecimal rejected = line.rejectedQuantity() == null ? ZERO : line.rejectedQuantity();
			validateNonNegativeReceiptQuantity(accepted);
			validateNonNegativeReceiptQuantity(rejected);
			if (accepted.add(rejected).compareTo(ZERO) <= 0) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
			}
			if (line.provisionalUnitCost() != null && (line.provisionalUnitCost().compareTo(ZERO) < 0
					|| line.provisionalUnitCost().scale() > 6 || integerDigits(line.provisionalUnitCost()) > 12L)) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
			}
			acceptedQuantity = acceptedQuantity.add(accepted);
			rejectedQuantity = rejectedQuantity.add(rejected);
			if (provisionalUnitCost == null && line.provisionalUnitCost() != null) {
				provisionalUnitCost = line.provisionalUnitCost();
			}
			lines.add(new ValidatedReceiptLine(line.lineNo(), accepted, rejected, line.provisionalUnitCost(),
					line.remark(), line.trackingAllocations() == null ? List.of() : line.trackingAllocations()));
		}
		return new ReceiptQuantity(validatePositiveQuantity(acceptedQuantity), rejectedQuantity, provisionalUnitCost,
				lines);
	}

	private void validateNonNegativeReceiptQuantity(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) < 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
		}
	}

	private Long insertOrderWithRetry(ValidatedOrder order, String operatorName, OffsetDateTime now) {
		for (int attempt = 0; attempt < 5; attempt++) {
			try {
				return this.jdbcTemplate.queryForObject("""
						insert into mfg_outsourcing_order (
							outsourcing_order_no, supplier_id, product_material_id, bom_id, planned_quantity,
							issue_warehouse_id, receipt_warehouse_id, planned_issue_date, planned_receipt_date,
							status, ownership_type, project_id, provisional_unit_cost, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, nextNo("MFG-OS", ORDER_SEQUENCE), order.supplierId(),
						order.productMaterial().id(), order.bom().id(), order.plannedQuantity(),
						order.issueWarehouseId(), order.receiptWarehouseId(), order.plannedIssueDate(),
						order.plannedReceiptDate(), order.ownershipType(), order.projectId(), order.provisionalUnitCost(),
						blankToNull(order.remark()), operatorName, now, operatorName, now);
			}
			catch (DuplicateKeyException exception) {
				if (attempt == 4) {
					throw new BusinessException(ApiErrorCode.CONFLICT);
				}
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private Long insertIssueWithRetry(Long orderId, ValidatedIssue issue, String operatorName, OffsetDateTime now) {
		for (int attempt = 0; attempt < 5; attempt++) {
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into mfg_outsourcing_issue (
							issue_no, outsourcing_order_id, status, business_date, reason, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, nextNo("MFG-OSI", ISSUE_SEQUENCE), orderId, issue.businessDate(),
						issue.reason(), blankToNull(issue.remark()), operatorName, now, operatorName, now);
				insertIssueLines(id, issue.lines(), operatorName, now);
				return id;
			}
			catch (DuplicateKeyException exception) {
				if (attempt == 4) {
					throw new BusinessException(ApiErrorCode.CONFLICT);
				}
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertIssueLines(Long issueId, List<ValidatedIssueLine> lines, String operatorName,
			OffsetDateTime now) {
		for (ValidatedIssueLine line : lines) {
			Long lineId = this.jdbcTemplate.queryForObject("""
					insert into mfg_outsourcing_issue_line (
						issue_id, order_material_id, line_no, warehouse_id, material_id, unit_id, quantity,
						ownership_type, project_id, cost_layer_id, remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, issueId, line.orderMaterialId(), line.lineNo(), line.warehouseId(), line.materialId(),
					line.unitId(), line.quantity(), line.ownershipType(), line.projectId(), line.costLayerId(),
					blankToNull(line.remark()), now, now);
			this.inventoryTrackingService.prepareOutboundAllocations(ISSUE_SOURCE_TYPE, issueId, lineId,
					line.warehouseId(), line.materialId(), line.unitId(), line.quantity(), line.trackingAllocations(),
					operatorName, "lines[" + (line.lineNo() - 1) + "].trackingAllocations");
		}
	}

	private Long insertReceiptWithRetry(OrderRow order, ValidatedReceipt receipt, String operatorName,
			OffsetDateTime now) {
		for (int attempt = 0; attempt < 5; attempt++) {
			try {
				return this.jdbcTemplate.queryForObject("""
						insert into mfg_outsourcing_receipt (
							receipt_no, outsourcing_order_id, status, business_date, receipt_warehouse_id, quantity,
							rejected_quantity, provisional_unit_cost, ownership_type, project_id, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, nextNo("MFG-OSR", RECEIPT_SEQUENCE), order.id(), receipt.businessDate(),
						receipt.receiptWarehouseId(), receipt.quantity(), receipt.rejectedQuantity(), receipt.provisionalUnitCost(),
						order.ownershipType(), order.projectId(), blankToNull(receipt.remark()), operatorName, now,
						operatorName, now);
			}
			catch (DuplicateKeyException exception) {
				if (attempt == 4) {
					throw new BusinessException(ApiErrorCode.CONFLICT);
				}
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertReceiptLines(OrderRow order, Long receiptId, ValidatedReceipt receipt, String operatorName,
			OffsetDateTime now) {
		MaterialRef product = validateEnabledMaterial(order.productMaterialId());
		for (ValidatedReceiptLine line : receipt.lines()) {
			Long lineId = this.jdbcTemplate.queryForObject("""
					insert into mfg_outsourcing_receipt_line (
						receipt_id, line_no, accepted_quantity, rejected_quantity, provisional_unit_cost,
						remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, receiptId, line.lineNo(), line.acceptedQuantity(), line.rejectedQuantity(),
					line.provisionalUnitCost(), blankToNull(line.remark()), now, now);
			if (line.acceptedQuantity().compareTo(ZERO) > 0) {
				this.inventoryTrackingService.prepareInboundAllocations(RECEIPT_SOURCE_TYPE, receiptId, lineId,
						receipt.receiptWarehouseId(), order.productMaterialId(), product.unitId(),
						line.acceptedQuantity(), InventoryQualityStatus.PENDING_INSPECTION, receipt.businessDate(),
						line.trackingAllocations(), operatorName, "lines[" + (line.lineNo() - 1)
								+ "].trackingAllocations");
			}
		}
	}

	private OutsourcingOrderDetailResponse orderDetail(Long id, CurrentUser currentUser) {
		OrderRow order = orderRow(id).orElseThrow(this::orderNotFound);
		OutsourcingOrderSummaryResponse summary = orderSummary(order.id(), currentUser).orElseThrow(this::orderNotFound);
		return new OutsourcingOrderDetailResponse(summary.id(), summary.outsourcingOrderNo(), summary.orderNo(),
				summary.ownershipType(), summary.projectId(), summary.projectNo(), summary.projectName(),
				summary.supplierId(), summary.supplierCode(), summary.supplierName(), summary.productMaterialId(),
				summary.productMaterialCode(), summary.productMaterialName(), summary.bomId(), summary.bomCode(),
				summary.bomVersionCode(), summary.unitName(), summary.plannedQuantity(), summary.issuedQuantity(),
				summary.receivedQuantity(), summary.acceptedQuantity(), summary.rejectedQuantity(),
				summary.issueWarehouseId(), summary.issueWarehouseName(), summary.receiptWarehouseId(),
				summary.receiptWarehouseName(), summary.plannedIssueDate(), summary.plannedReceiptDate(),
				summary.plannedStartDate(), summary.plannedFinishDate(), summary.status(), summary.statusName(),
				summary.provisionalUnitCost(), summary.remark(), summary.costVisible(), summary.allowedActions(),
				summary.actionDisabledReason(), summary.version(), orderMaterials(order.id()),
				issues(order.id(), 1, 100, currentUser).items(), receipts(order.id(), 1, 100, currentUser).items(),
				List.of());
	}

	private OutsourcingIssueDetailResponse issueDetail(Long orderId, Long id, CurrentUser currentUser) {
		DocumentRow issue = issueRow(orderId, id).orElseThrow(this::issueNotFound);
		List<IssueLineRow> lines = issueLines(issue.id());
		Long warehouseId = lines.isEmpty() ? null : lines.getFirst().warehouseId();
		boolean costVisible = costVisible(currentUser);
		return new OutsourcingIssueDetailResponse(issue.id(), issue.documentNo(), issue.outsourcingOrderId(),
				issue.documentNo(), issue.status(), issue.businessDate(), issue.reason(), issue.remark(),
				issue.version(), warehouseId, lines.size(), documentActionDisabledReason(issue.status(),
						"production:outsourcing-issue", currentUser),
				documentAllowedActions(issue.status(), "production:outsourcing-issue", currentUser), costVisible,
				lines.stream()
					.map((line) -> new OutsourcingIssueLineResponse(line.id(), line.issueId(), line.orderMaterialId(),
							line.lineNo(), line.warehouseId(), line.materialId(), line.unitId(),
							quantityString(line.quantity()), quantityString(line.beforeQuantity()),
							quantityString(line.afterQuantity()), line.ownershipType(), line.projectId(),
							costVisible ? line.costLayerId() : null, line.remark(),
							trackingAllocations(ISSUE_SOURCE_TYPE, issue.id(), line.id())))
					.toList());
	}

	private OutsourcingReceiptResponse receiptDetail(Long orderId, Long id, CurrentUser currentUser) {
		ReceiptRow receipt = receiptRow(orderId, id).orElseThrow(this::receiptNotFound);
		List<ReceiptLineRow> lines = receiptLines(receipt.id());
		boolean costVisible = costVisible(currentUser);
		return new OutsourcingReceiptResponse(receipt.id(), receipt.receiptNo(), receipt.receiptNo(),
				receipt.outsourcingOrderId(), receipt.status(), receipt.businessDate(), receipt.receiptWarehouseId(),
				quantityString(receipt.quantity()), quantityString(receipt.rejectedQuantity()),
				quantityString(receipt.beforeQuantity()), quantityString(receipt.afterQuantity()),
				costVisible ? quantityString(receipt.provisionalUnitCost()) : null,
				costVisible ? quantityString(receipt.unitCost()) : null, costVisible ? receipt.valuationState() : null,
				receipt.ownershipType(), receipt.projectId(), receipt.remark(), lines.size(),
				documentActionDisabledReason(receipt.status(), "production:outsourcing-receipt", currentUser),
				documentAllowedActions(receipt.status(), "production:outsourcing-receipt", currentUser),
				costVisible, receipt.version(), lines.stream()
					.map((line) -> new OutsourcingReceiptLineResponse(line.lineNo(),
							quantityString(line.acceptedQuantity()), quantityString(line.rejectedQuantity()),
							costVisible ? quantityString(line.provisionalUnitCost()) : null, line.remark(),
							trackingAllocations(RECEIPT_SOURCE_TYPE, receipt.id(), line.id())))
					.toList());
	}

	private List<OutsourcingTrackingAllocationResponse> trackingAllocations(String documentType, Long documentId,
			Long documentLineId) {
		return this.inventoryTrackingService.allocationResponses(documentType, documentId, documentLineId)
			.stream()
			.map((allocation) -> new OutsourcingTrackingAllocationResponse(allocation.allocationId(),
					allocation.sourceAllocationId(), allocation.trackingMethod(), allocation.trackingMethodName(),
					allocation.batchId(), allocation.batchNo(), allocation.serialId(), allocation.serialNo(),
					quantityString(allocation.quantity()), allocation.qualityStatus(), allocation.qualityStatusName(),
					allocation.movementId()))
			.toList();
	}

	private Optional<OrderRow> lockOrder(Long id) {
		return this.jdbcTemplate.query("""
				select *
				from mfg_outsourcing_order
				where id = ?
				for update
				""", this::mapOrderRow, id).stream().findFirst();
	}

	private Optional<OrderRow> orderRow(Long id) {
		return this.jdbcTemplate.query("select * from mfg_outsourcing_order where id = ?", this::mapOrderRow, id)
			.stream()
			.findFirst();
	}

	private Optional<OutsourcingOrderSummaryResponse> orderSummary(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select o.*, s.code as supplier_code, s.name as supplier_name,
				       pm.code as product_material_code, pm.name as product_material_name, u.name as unit_name,
				       b.bom_code, b.version_code as bom_version_code,
				       iw.name as issue_warehouse_name, rw.name as receipt_warehouse_name,
				       p.project_no, p.name as project_name,
				       coalesce(rr.rejected_quantity, 0.000000) as rejected_quantity
				from mfg_outsourcing_order o
				join mst_material pm on pm.id = o.product_material_id
				left join mst_unit u on u.id = pm.unit_id
				left join mst_supplier s on s.id = o.supplier_id
				left join mfg_bom b on b.id = o.bom_id
				left join mst_warehouse iw on iw.id = o.issue_warehouse_id
				left join mst_warehouse rw on rw.id = o.receipt_warehouse_id
				left join sal_project p on p.id = o.project_id
				left join (
					select outsourcing_order_id, sum(rejected_quantity) as rejected_quantity
					from mfg_outsourcing_receipt
					where status <> 'CANCELLED'
					group by outsourcing_order_id
				) rr on rr.outsourcing_order_id = o.id
				where o.id = ?
				""", (rs, rowNum) -> mapOrderSummary(rs, rowNum, currentUser), id).stream().findFirst();
	}

	private Optional<DocumentRow> lockIssue(Long orderId, Long id) {
		return this.jdbcTemplate.query("""
				select *
				from mfg_outsourcing_issue
				where outsourcing_order_id = ?
				and id = ?
				for update
				""", this::mapIssueRow, orderId, id).stream().findFirst();
	}

	private Optional<DocumentRow> issueRow(Long orderId, Long id) {
		return this.jdbcTemplate.query("""
				select *
				from mfg_outsourcing_issue
				where outsourcing_order_id = ?
				and id = ?
				""", this::mapIssueRow, orderId, id).stream().findFirst();
	}

	private Optional<ReceiptRow> lockReceipt(Long orderId, Long id) {
		return this.jdbcTemplate.query("""
				select *
				from mfg_outsourcing_receipt
				where outsourcing_order_id = ?
				and id = ?
				for update
				""", this::mapReceiptRow, orderId, id).stream().findFirst();
	}

	private Optional<ReceiptRow> receiptRow(Long orderId, Long id) {
		return this.jdbcTemplate.query("""
				select *
				from mfg_outsourcing_receipt
				where outsourcing_order_id = ?
				and id = ?
				""", this::mapReceiptRow, orderId, id).stream().findFirst();
	}

	private Optional<OrderMaterialRow> lockOrderMaterial(Long orderId, Long id) {
		return this.jdbcTemplate.query("""
				select *
				from mfg_outsourcing_order_material
				where outsourcing_order_id = ?
				and id = ?
				for update
				""", this::mapOrderMaterialRow, orderId, id).stream().findFirst();
	}

	private List<OrderMaterialResponse> orderMaterials(Long orderId) {
		return this.jdbcTemplate.query("""
				select om.*, m.code as material_code, m.name as material_name, u.name as unit_name
				from mfg_outsourcing_order_material om
				join mst_material m on m.id = om.material_id
				join mst_unit u on u.id = om.unit_id
				where om.outsourcing_order_id = ?
				order by om.line_no, om.id
				""", (rs, rowNum) -> new OrderMaterialResponse(rs.getLong("id"), rs.getLong("outsourcing_order_id"),
				rs.getInt("line_no"), nullableLong(rs, "bom_item_id"), rs.getLong("material_id"),
				rs.getString("material_code"), rs.getString("material_name"), rs.getLong("unit_id"),
				rs.getString("unit_name"), rs.getBigDecimal("required_quantity"),
				rs.getBigDecimal("issued_quantity"), rs.getBigDecimal("loss_rate"), rs.getString("remark")), orderId);
	}

	private List<OrderMaterialRow> orderMaterialRows(Long orderId) {
		return this.jdbcTemplate.query("""
				select *
				from mfg_outsourcing_order_material
				where outsourcing_order_id = ?
				order by line_no, id
				""", this::mapOrderMaterialRow, orderId);
	}

	private List<IssueLineRow> issueLines(Long issueId) {
		return this.jdbcTemplate.query("""
				select *
				from mfg_outsourcing_issue_line
				where issue_id = ?
				order by line_no, id
				""", this::mapIssueLineRow, issueId);
	}

	private List<ReceiptLineRow> lockReceiptLines(Long receiptId) {
		return this.jdbcTemplate.query("""
				select *
				from mfg_outsourcing_receipt_line
				where receipt_id = ?
				order by line_no, id
				for update
				""", this::mapReceiptLineRow, receiptId);
	}

	private List<ReceiptLineRow> receiptLines(Long receiptId) {
		return this.jdbcTemplate.query("""
				select *
				from mfg_outsourcing_receipt_line
				where receipt_id = ?
				order by line_no, id
				""", this::mapReceiptLineRow, receiptId);
	}

	private OutsourcingOrderSummaryResponse mapOrderSummary(ResultSet rs, int rowNum, CurrentUser currentUser)
			throws SQLException {
		LocalDate plannedIssueDate = rs.getObject("planned_issue_date", LocalDate.class);
		LocalDate plannedReceiptDate = rs.getObject("planned_receipt_date", LocalDate.class);
		String status = rs.getString("status");
		BigDecimal receivedQuantity = rs.getBigDecimal("received_quantity");
		boolean costVisible = costVisible(currentUser);
		return new OutsourcingOrderSummaryResponse(rs.getLong("id"), rs.getString("outsourcing_order_no"),
				rs.getString("outsourcing_order_no"), rs.getString("ownership_type"), nullableLong(rs, "project_id"),
				rs.getString("project_no"), rs.getString("project_name"), nullableLong(rs, "supplier_id"),
				rs.getString("supplier_code"), rs.getString("supplier_name"), rs.getLong("product_material_id"),
				rs.getString("product_material_code"), rs.getString("product_material_name"), nullableLong(rs, "bom_id"),
				rs.getString("bom_code"), rs.getString("bom_version_code"), rs.getString("unit_name"),
				quantityString(rs.getBigDecimal("planned_quantity")), quantityString(rs.getBigDecimal("issued_quantity")),
				quantityString(receivedQuantity), quantityString(receivedQuantity),
				quantityString(rs.getBigDecimal("rejected_quantity")), nullableLong(rs, "issue_warehouse_id"),
				rs.getString("issue_warehouse_name"), nullableLong(rs, "receipt_warehouse_id"),
				rs.getString("receipt_warehouse_name"), plannedIssueDate, plannedReceiptDate, plannedIssueDate,
				plannedReceiptDate, status, outsourcingStatusName(status),
				costVisible ? quantityString(rs.getBigDecimal("provisional_unit_cost")) : null, rs.getString("remark"),
				costVisible, outsourcingOrderAllowedActions(status, currentUser),
				outsourcingOrderActionDisabledReason(status, currentUser), rs.getLong("version"));
	}

	private OutsourcingDocumentSummaryResponse mapDocumentSummary(ResultSet rs, int rowNum, String permissionPrefix,
			CurrentUser currentUser) throws SQLException {
		String status = rs.getString("status");
		return new OutsourcingDocumentSummaryResponse(rs.getLong("id"), rs.getString("document_no"),
				rs.getString("issue_no"), rs.getString("receipt_no"), rs.getLong("outsourcing_order_id"),
				status, rs.getObject("business_date", LocalDate.class), rs.getInt("line_count"),
				documentActionDisabledReason(status, permissionPrefix, currentUser),
				documentAllowedActions(status, permissionPrefix, currentUser), rs.getLong("version"));
	}

	private OrderRow mapOrderRow(ResultSet rs, int rowNum) throws SQLException {
		return new OrderRow(rs.getLong("id"), rs.getString("outsourcing_order_no"), nullableLong(rs, "supplier_id"),
				rs.getLong("product_material_id"), nullableLong(rs, "bom_id"), rs.getBigDecimal("planned_quantity"),
				rs.getBigDecimal("issued_quantity"), rs.getBigDecimal("received_quantity"),
				nullableLong(rs, "issue_warehouse_id"), nullableLong(rs, "receipt_warehouse_id"),
				rs.getObject("planned_issue_date", LocalDate.class),
				rs.getObject("planned_receipt_date", LocalDate.class), rs.getString("status"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"),
				rs.getBigDecimal("provisional_unit_cost"), rs.getString("remark"), rs.getLong("version"));
	}

	private DocumentRow mapIssueRow(ResultSet rs, int rowNum) throws SQLException {
		return new DocumentRow(rs.getLong("id"), rs.getString("issue_no"), rs.getLong("outsourcing_order_id"),
				rs.getString("status"), rs.getObject("business_date", LocalDate.class), rs.getString("reason"),
				rs.getString("remark"), rs.getLong("version"));
	}

	private ReceiptRow mapReceiptRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceiptRow(rs.getLong("id"), rs.getString("receipt_no"), rs.getLong("outsourcing_order_id"),
				rs.getString("status"), rs.getObject("business_date", LocalDate.class),
				rs.getLong("receipt_warehouse_id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("rejected_quantity"), rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
				rs.getBigDecimal("provisional_unit_cost"), rs.getBigDecimal("unit_cost"),
				rs.getString("valuation_state"), rs.getString("ownership_type"), nullableLong(rs, "project_id"),
				rs.getString("remark"), rs.getLong("version"));
	}

	private OrderMaterialRow mapOrderMaterialRow(ResultSet rs, int rowNum) throws SQLException {
		return new OrderMaterialRow(rs.getLong("id"), rs.getLong("outsourcing_order_id"), rs.getInt("line_no"),
				nullableLong(rs, "bom_item_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("required_quantity"), rs.getBigDecimal("issued_quantity"),
				rs.getBigDecimal("loss_rate"), rs.getString("remark"));
	}

	private IssueLineRow mapIssueLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new IssueLineRow(rs.getLong("id"), rs.getLong("issue_id"), rs.getLong("order_material_id"),
				rs.getInt("line_no"), rs.getLong("warehouse_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"), nullableLong(rs, "cost_layer_id"),
				rs.getString("remark"));
	}

	private ReceiptLineRow mapReceiptLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceiptLineRow(rs.getLong("id"), rs.getLong("receipt_id"), rs.getInt("line_no"),
				rs.getBigDecimal("accepted_quantity"), rs.getBigDecimal("rejected_quantity"),
				rs.getBigDecimal("provisional_unit_cost"), rs.getBigDecimal("unit_cost"),
				rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
				nullableLong(rs, "stock_movement_id"), nullableLong(rs, "value_movement_id"),
				rs.getString("remark"));
	}

	private Optional<MaterialRef> materialRef(Long materialId) {
		return this.jdbcTemplate.query("""
				select m.id, m.code, m.name, m.material_type, m.source_type, m.unit_id, u.name as unit_name, m.status
				from mst_material m
				left join mst_unit u on u.id = m.unit_id
				where m.id = ?
				""", (rs, rowNum) -> new MaterialRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getString("material_type"), rs.getString("source_type"), rs.getLong("unit_id"),
				rs.getString("unit_name"), rs.getString("status")), materialId).stream().findFirst();
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

	private void validateSupplier(Long supplierId) {
		String status = this.jdbcTemplate.query("select status from mst_supplier where id = ?",
				(rs, rowNum) -> rs.getString("status"), supplierId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_SUPPLIER_INVALID);
		}
	}

	private BomRef validateBom(Long bomId, Long productMaterialId, LocalDate businessDate) {
		if (bomId == null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_BOM_INVALID);
		}
		BomRef bom = this.jdbcTemplate.query("""
				select id, parent_material_id, base_quantity, base_unit_id, status, effective_from, effective_to
				from mfg_bom
				where id = ?
				""", (rs, rowNum) -> new BomRef(rs.getLong("id"), rs.getLong("parent_material_id"),
				rs.getBigDecimal("base_quantity"), rs.getLong("base_unit_id"), rs.getString("status"),
				rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class)), bomId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PRODUCTION_BOM_INVALID));
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
		List<BomItemRef> items = this.jdbcTemplate.query("""
				select i.id, i.line_no, i.child_material_id, i.loss_rate, i.remark,
				       i.base_unit_id, i.base_quantity, i.quantity_basis,
				       m.code, m.name, m.material_type, m.source_type, m.unit_id, m.status
				from mfg_bom_item i
				join mst_material m on m.id = i.child_material_id
				where i.bom_id = ?
				order by i.line_no, i.id
				""", (rs, rowNum) -> new BomItemRef(rs.getLong("id"), rs.getInt("line_no"),
				new MaterialRef(rs.getLong("child_material_id"), rs.getString("code"), rs.getString("name"),
						rs.getString("material_type"), rs.getString("source_type"), rs.getLong("unit_id"), null,
						rs.getString("status")),
				nullableLong(rs, "base_unit_id"), rs.getBigDecimal("base_quantity"), rs.getBigDecimal("loss_rate"),
				rs.getString("quantity_basis"), rs.getString("remark")), bomId);
		if (items.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_BOM_EMPTY_ITEMS);
		}
		for (BomItemRef item : items) {
			validateEnabledMaterial(item.material().id());
			if ("LEGACY_BUSINESS_UNIT".equals(item.quantityBasis()) || item.baseUnitId() == null
					|| item.baseQuantity() == null) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_UNIT_CONVERSION_REQUIRED);
			}
			validateEnabledUnit(item.baseUnitId());
			validatePositiveQuantity(item.baseQuantity());
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

	private BigDecimal validatePositiveQuantity(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) <= 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_QUANTITY_INVALID);
		}
		return value;
	}

	private QueryParts outsourcingOrderQueryParts(String keyword, Long projectId, Long supplierId,
			Long productMaterialId, String status, LocalDate plannedDateFrom, LocalDate plannedDateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(o.outsourcing_order_no ilike ? or pm.code ilike ? or pm.name ilike ?
					 or s.code ilike ? or s.name ilike ?)
					""");
			String like = "%" + keyword.trim() + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (projectId != null) {
			conditions.add("o.project_id = ?");
			args.add(projectId);
		}
		if (supplierId != null) {
			conditions.add("o.supplier_id = ?");
			args.add(supplierId);
		}
		if (productMaterialId != null) {
			conditions.add("o.product_material_id = ?");
			args.add(productMaterialId);
		}
		if (hasText(status)) {
			String normalizedStatus = status.trim().toUpperCase();
			conditions.add("o.status = ?");
			args.add(normalizedStatus);
		}
		if (plannedDateFrom != null) {
			conditions.add("coalesce(o.planned_issue_date, o.planned_receipt_date) >= ?");
			args.add(plannedDateFrom);
		}
		if (plannedDateTo != null) {
			conditions.add("coalesce(o.planned_issue_date, o.planned_receipt_date) <= ?");
			args.add(plannedDateTo);
		}
		return where(conditions, args);
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

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String outsourcingStatusName(String status) {
		return switch (status) {
			case "DRAFT" -> "草稿";
			case "RELEASED" -> "已发布";
			case "IN_PROGRESS" -> "执行中";
			case "COMPLETED" -> "已完成";
			case "CLOSED" -> "已关闭";
			case "CANCELLED" -> "已取消";
			default -> status;
		};
	}

	private List<String> outsourcingOrderAllowedActions(String status, CurrentUser currentUser) {
		List<String> actions = new ArrayList<>();
		if ("DRAFT".equals(status)) {
			if (hasPermission(currentUser, "production:outsourcing:update")) {
				actions.add("UPDATE");
			}
			if (hasPermission(currentUser, "production:outsourcing:release")) {
				actions.add("RELEASE");
			}
			if (hasPermission(currentUser, "production:outsourcing:cancel")) {
				actions.add("CANCEL");
			}
			return actions;
		}
		if ("RELEASED".equals(status) || "IN_PROGRESS".equals(status)) {
			if (hasPermission(currentUser, "production:outsourcing-issue:create")) {
				actions.add("ISSUE");
			}
			if (hasPermission(currentUser, "production:outsourcing-receipt:create")) {
				actions.add("RECEIPT");
			}
			return actions;
		}
		if ("COMPLETED".equals(status) && hasPermission(currentUser, "production:outsourcing:close")) {
			actions.add("CLOSE");
		}
		return actions;
	}

	private String outsourcingOrderActionDisabledReason(String status, CurrentUser currentUser) {
		if (outsourcingOrderAllowedActions(status, currentUser).isEmpty()
				&& List.of("DRAFT", "RELEASED", "IN_PROGRESS", "COMPLETED").contains(status)
				&& currentUser != null) {
			return "当前用户权限不足";
		}
		return null;
	}

	private void requireVersion(Long expectedVersion, Long actualVersion) {
		if (expectedVersion == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (!expectedVersion.equals(actualVersion)) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
	}

	private void requireExecutableOrderForPosting(String orderStatus) {
		if (!List.of("RELEASED", "IN_PROGRESS").contains(orderStatus)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_STATUS_INVALID);
		}
	}

	private String actionFingerprint(String action, String resourceType, Long resourceId,
			OutsourcingActionRequest request) {
		return this.actionIdempotencyService.fingerprint(action, resourceType, resourceId, request.version(),
				request.reason());
	}

	private List<String> documentAllowedActions(String status, String permissionPrefix, CurrentUser currentUser) {
		if (!"DRAFT".equals(status)) {
			return List.of();
		}
		List<String> actions = new ArrayList<>();
		if (hasPermission(currentUser, permissionPrefix + ":update")) {
			actions.add("UPDATE");
		}
		if (hasPermission(currentUser, permissionPrefix + ":post")) {
			actions.add("POST");
		}
		if (hasPermission(currentUser, permissionPrefix + ":cancel")) {
			actions.add("CANCEL");
		}
		return actions;
	}

	private String documentActionDisabledReason(String status, String permissionPrefix, CurrentUser currentUser) {
		if ("DRAFT".equals(status) && documentAllowedActions(status, permissionPrefix, currentUser).isEmpty()
				&& currentUser != null) {
			return "当前用户权限不足";
		}
		return null;
	}

	private boolean costVisible(CurrentUser currentUser) {
		return hasPermission(currentUser, "inventory:valuation:view");
	}

	private CurrentUser requireCurrentUser(CurrentUser currentUser) {
		if (currentUser == null) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
		return currentUser;
	}

	private boolean hasPermission(CurrentUser currentUser, String permissionCode) {
		return currentUser != null && currentUser.permissions().contains(permissionCode);
	}

	private static String quantityString(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	private BusinessException orderNotFound() {
		return new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_NOT_FOUND);
	}

	private BusinessException issueNotFound() {
		return new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_ISSUE_NOT_FOUND);
	}

	private BusinessException receiptNotFound() {
		return new BusinessException(ApiErrorCode.PRODUCTION_OUTSOURCING_RECEIPT_NOT_FOUND);
	}

	private static String nextNo(String prefix, AtomicInteger sequence) {
		int value = sequence.updateAndGet((current) -> current >= 999 ? 1 : current + 1);
		return prefix + "-" + OffsetDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", value);
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static long integerDigits(BigDecimal value) {
		BigDecimal normalized = value.abs().stripTrailingZeros();
		int precision = normalized.precision();
		int scale = Math.max(normalized.scale(), 0);
		return Math.max(precision - scale, 0);
	}

	public record OutsourcingOrderRequest(Long supplierId, Long productMaterialId, Long bomId,
			BigDecimal plannedQuantity, Long issueWarehouseId, Long receiptWarehouseId, LocalDate plannedIssueDate,
			LocalDate plannedReceiptDate, LocalDate plannedStartDate, LocalDate plannedFinishDate, String ownershipType,
			Long projectId, BigDecimal provisionalUnitCost, String remark, Long version, String idempotencyKey) {
	}

	public record OutsourcingIssueLineRequest(Integer lineNo, Long orderMaterialId, Long warehouseId,
			BigDecimal quantity, String ownershipType, Long projectId, Long costLayerId, String remark,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	public record OutsourcingIssueRequest(LocalDate businessDate, String reason, Long warehouseId, String remark,
			List<OutsourcingIssueLineRequest> lines, Long version, String idempotencyKey) {
	}

	public record OutsourcingReceiptRequest(LocalDate businessDate, Long receiptWarehouseId, BigDecimal quantity,
			BigDecimal provisionalUnitCost, String remark,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations,
			List<OutsourcingReceiptLineRequest> lines, Long version, String idempotencyKey) {
	}

	public record OutsourcingReceiptLineRequest(Integer lineNo, BigDecimal acceptedQuantity, BigDecimal rejectedQuantity,
			BigDecimal provisionalUnitCost, String remark,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	public record OutsourcingActionRequest(@NotNull Long version, String reason, @NotBlank String idempotencyKey) {
	}

	public record OutsourcingOrderSummaryResponse(Long id, String outsourcingOrderNo, String orderNo,
			String ownershipType, Long projectId, String projectNo, String projectName, Long supplierId,
			String supplierCode, String supplierName, Long productMaterialId, String productMaterialCode,
			String productMaterialName, Long bomId, String bomCode, String bomVersionCode, String unitName,
			String plannedQuantity, String issuedQuantity, String receivedQuantity,
			String acceptedQuantity, String rejectedQuantity, Long issueWarehouseId,
			String issueWarehouseName, Long receiptWarehouseId, String receiptWarehouseName,
			LocalDate plannedIssueDate, LocalDate plannedReceiptDate, LocalDate plannedStartDate,
			LocalDate plannedFinishDate, String status, String statusName, String provisionalUnitCost,
			String remark, boolean costVisible, List<String> allowedActions, String actionDisabledReason, Long version) {
	}

	public record OutsourcingOrderDetailResponse(Long id, String outsourcingOrderNo, String orderNo,
			String ownershipType, Long projectId, String projectNo, String projectName, Long supplierId,
			String supplierCode, String supplierName, Long productMaterialId, String productMaterialCode,
			String productMaterialName, Long bomId, String bomCode, String bomVersionCode, String unitName,
			String plannedQuantity, String issuedQuantity, String receivedQuantity,
			String acceptedQuantity, String rejectedQuantity, Long issueWarehouseId,
			String issueWarehouseName, Long receiptWarehouseId, String receiptWarehouseName,
			LocalDate plannedIssueDate, LocalDate plannedReceiptDate, LocalDate plannedStartDate,
			LocalDate plannedFinishDate, String status, String statusName, String provisionalUnitCost,
			String remark, boolean costVisible, List<String> allowedActions, String actionDisabledReason, Long version,
			List<OrderMaterialResponse> materials, List<OutsourcingDocumentSummaryResponse> materialIssues,
			List<OutsourcingDocumentSummaryResponse> receipts, List<Object> traceLinks) {
	}

	public record OrderMaterialResponse(Long id, Long outsourcingOrderId, Integer lineNo, Long bomItemId,
			Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			BigDecimal requiredQuantity, BigDecimal issuedQuantity, BigDecimal lossRate, String remark) {
	}

	public record OutsourcingDocumentSummaryResponse(Long id, String documentNo, String issueNo, String receiptNo,
			Long outsourcingOrderId, String status, LocalDate businessDate, Integer lineCount,
			String actionDisabledReason, List<String> allowedActions, Long version) {
	}

	public record OutsourcingIssueDetailResponse(Long id, String issueNo, Long outsourcingOrderId, String documentNo,
			String status, LocalDate businessDate, String reason, String remark, Long version,
			Long warehouseId, Integer lineCount, String actionDisabledReason, List<String> allowedActions,
			boolean costVisible, List<OutsourcingIssueLineResponse> lines) {
	}

	public record OutsourcingIssueLineResponse(Long id, Long issueId, Long orderMaterialId, Integer lineNo,
			Long warehouseId, Long materialId, Long unitId, String quantity, String beforeQuantity,
			String afterQuantity, String ownershipType, Long projectId, Long costLayerId, String remark,
			List<OutsourcingTrackingAllocationResponse> trackingAllocations) {
	}

	public record OutsourcingReceiptResponse(Long id, String receiptNo, String documentNo, Long outsourcingOrderId,
			String status, LocalDate businessDate, Long receiptWarehouseId, String quantity,
			String rejectedQuantity, String beforeQuantity, String afterQuantity,
			String provisionalUnitCost, String unitCost, String valuationState, String ownershipType,
			Long projectId, String remark, Integer lineCount, String actionDisabledReason,
			List<String> allowedActions, boolean costVisible, Long version, List<OutsourcingReceiptLineResponse> lines) {
	}

	public record OutsourcingReceiptLineResponse(Integer lineNo, String acceptedQuantity,
			String rejectedQuantity, String provisionalUnitCost, String remark,
			List<OutsourcingTrackingAllocationResponse> trackingAllocations) {
	}

	public record OutsourcingTrackingAllocationResponse(Long allocationId, Long sourceAllocationId,
			String trackingMethod, String trackingMethodName, Long batchId, String batchNo, Long serialId,
			String serialNo, String quantity, String qualityStatus, String qualityStatusName, Long movementId) {
	}

	private record ValidatedOrder(Long supplierId, MaterialRef productMaterial, BomRef bom, BigDecimal plannedQuantity,
			Long issueWarehouseId, Long receiptWarehouseId, LocalDate plannedIssueDate, LocalDate plannedReceiptDate,
			String ownershipType, Long projectId, BigDecimal provisionalUnitCost, String remark) {
	}

	private record ValidatedIssue(LocalDate businessDate, String reason, String remark,
			List<ValidatedIssueLine> lines) {
	}

	private record ValidatedIssueLine(Integer lineNo, Long orderMaterialId, Long warehouseId, Long materialId,
			Long unitId, BigDecimal quantity, String ownershipType, Long projectId, Long costLayerId, String remark,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	private record ValidatedReceipt(LocalDate businessDate, Long receiptWarehouseId, BigDecimal quantity,
			BigDecimal rejectedQuantity, BigDecimal provisionalUnitCost, String remark,
			List<ValidatedReceiptLine> lines) {
	}

	private record ReceiptQuantity(BigDecimal acceptedQuantity, BigDecimal rejectedQuantity,
			BigDecimal provisionalUnitCost, List<ValidatedReceiptLine> lines) {
	}

	private record ValidatedReceiptLine(Integer lineNo, BigDecimal acceptedQuantity,
			BigDecimal rejectedQuantity, BigDecimal provisionalUnitCost, String remark,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	private record OrderRow(Long id, String outsourcingOrderNo, Long supplierId, Long productMaterialId, Long bomId,
			BigDecimal plannedQuantity, BigDecimal issuedQuantity, BigDecimal receivedQuantity, Long issueWarehouseId,
			Long receiptWarehouseId, LocalDate plannedIssueDate, LocalDate plannedReceiptDate, String status,
			String ownershipType, Long projectId, BigDecimal provisionalUnitCost, String remark, Long version) {
	}

	private record DocumentRow(Long id, String documentNo, Long outsourcingOrderId, String status,
			LocalDate businessDate, String reason, String remark, Long version) {
	}

	private record ReceiptRow(Long id, String receiptNo, Long outsourcingOrderId, String status, LocalDate businessDate,
			Long receiptWarehouseId, BigDecimal quantity, BigDecimal rejectedQuantity, BigDecimal beforeQuantity,
			BigDecimal afterQuantity, BigDecimal provisionalUnitCost, BigDecimal unitCost, String valuationState,
			String ownershipType, Long projectId, String remark, Long version) {
	}

	private record OrderMaterialRow(Long id, Long outsourcingOrderId, Integer lineNo, Long bomItemId, Long materialId,
			Long unitId, BigDecimal requiredQuantity, BigDecimal issuedQuantity, BigDecimal lossRate, String remark) {
	}

	public record IssueLineRow(Long id, Long issueId, Long orderMaterialId, Integer lineNo, Long warehouseId,
			Long materialId, Long unitId, BigDecimal quantity, BigDecimal beforeQuantity, BigDecimal afterQuantity,
			String ownershipType, Long projectId, Long costLayerId, String remark) {
	}

	private record ReceiptLineRow(Long id, Long receiptId, Integer lineNo, BigDecimal acceptedQuantity,
			BigDecimal rejectedQuantity, BigDecimal provisionalUnitCost, BigDecimal unitCost,
			BigDecimal beforeQuantity, BigDecimal afterQuantity, Long stockMovementId, Long valueMovementId,
			String remark) {
	}

	private record MaterialRef(Long id, String code, String name, String materialType, String sourceType, Long unitId,
			String unitName, String status) {
	}

	private record BomRef(Long id, Long parentMaterialId, BigDecimal baseQuantity, Long baseUnitId, String status,
			LocalDate effectiveFrom, LocalDate effectiveTo) {
	}

	private record BomItemRef(Long id, Integer lineNo, MaterialRef material, Long baseUnitId, BigDecimal baseQuantity,
			BigDecimal lossRate, String quantityBasis, String remark) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
