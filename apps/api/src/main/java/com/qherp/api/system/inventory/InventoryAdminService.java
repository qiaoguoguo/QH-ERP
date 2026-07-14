package com.qherp.api.system.inventory;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
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
import java.util.Map;
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

	private final BusinessPeriodGuard businessPeriodGuard;

	private final InventorySourceDocumentResolver sourceDocumentResolver;

	public InventoryAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			InventoryPostingService inventoryPostingService, BusinessPeriodGuard businessPeriodGuard,
			InventorySourceDocumentResolver sourceDocumentResolver) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.inventoryPostingService = inventoryPostingService;
		this.businessPeriodGuard = businessPeriodGuard;
		this.sourceDocumentResolver = sourceDocumentResolver;
	}

	@Transactional(readOnly = true)
	public PageResponse<InventoryBalanceResponse> balances(String keyword, Long warehouseId, Long materialId,
			String materialType, String qualityStatus, String trackingMethod, Long batchId, String batchNo,
			Long serialId, String serialNo, String ownershipType, Long projectId, String valuationState,
			boolean onlyPositive, boolean includeZero, int page, int pageSize, CurrentUser currentUser) {
		InventoryQualityStatus selectedQualityStatus = parseNullableQualityStatus(qualityStatus);
		InventoryTrackingMethod selectedTrackingMethod = parseNullableTrackingMethod(trackingMethod);
		boolean trackingBreakdown = trackingBreakdown(selectedTrackingMethod, batchId, batchNo, serialId, serialNo);
		QueryParts queryParts = balanceQueryParts(keyword, warehouseId, materialId, materialType,
				selectedTrackingMethod, batchId, batchNo, serialId, serialNo, ownershipType, projectId,
				valuationState);
		HavingParts havingParts = balanceHavingParts(selectedQualityStatus, onlyPositive && !includeZero);
		boolean costVisible = hasPermission(currentUser, "inventory:valuation:view");
		String ownershipCountSelect = ", b.ownership_type, b.project_id, b.valuation_state";
		String trackingCountSelect = trackingBreakdown ? ", b.batch_id, b.serial_id" : "";
		String trackingSelect = trackingBreakdown
				? "m.tracking_method, b.batch_id, bt.batch_no, b.serial_id, sr.serial_no,"
				: "m.tracking_method, null::bigint as batch_id, null::varchar as batch_no, null::bigint as serial_id, null::varchar as serial_no,";
		String trackingGroupBy = trackingBreakdown ? ", m.tracking_method, b.batch_id, bt.batch_no, b.serial_id, sr.serial_no"
				: ", m.tracking_method";
		String costLayerGroupBy = ", b.cost_layer_id";
		String selectedQuality = selectedQualityStatus == null ? null : selectedQualityStatus.name();
		String inventoryAmountSelect = selectedQuality == null ? "sum(b.inventory_amount)"
				: "sum(case when b.quality_status = '%s' then b.inventory_amount else 0 end)".formatted(selectedQuality);
		String costLayerCountSelect = selectedQuality == null
				? "count(distinct b.cost_layer_id) filter (where b.cost_layer_id is not null)"
				: "count(distinct b.cost_layer_id) filter (where b.cost_layer_id is not null and b.quality_status = '%s')"
					.formatted(selectedQuality);
		String costLayerIdSelect = "b.cost_layer_id";
		String reservationJoin = trackingBreakdown ? """
				left join (
					select warehouse_id, material_id, batch_id, serial_id, ownership_type, project_id, cost_layer_id,
					       sum(case when reservation_type = 'RESERVATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as reserved_quantity,
					       sum(case when reservation_type = 'OCCUPATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as occupied_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and quality_status = 'QUALIFIED'
					group by warehouse_id, material_id, batch_id, serial_id, ownership_type, project_id, cost_layer_id
				) r on r.warehouse_id = b.warehouse_id and r.material_id = b.material_id
					and r.batch_id is not distinct from b.batch_id
					and r.serial_id is not distinct from b.serial_id
					and r.ownership_type = b.ownership_type
					and r.project_id is not distinct from b.project_id
					and r.cost_layer_id is not distinct from b.cost_layer_id
				""" : """
				left join (
					select warehouse_id, material_id, ownership_type, project_id, cost_layer_id,
					       sum(case when reservation_type = 'RESERVATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as reserved_quantity,
					       sum(case when reservation_type = 'OCCUPATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as occupied_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and quality_status = 'QUALIFIED'
					group by warehouse_id, material_id, ownership_type, project_id, cost_layer_id
				) r on r.warehouse_id = b.warehouse_id and r.material_id = b.material_id
					and r.ownership_type = b.ownership_type
					and r.project_id is not distinct from b.project_id
					and r.cost_layer_id is not distinct from b.cost_layer_id
				""";
		List<Object> countArgs = new ArrayList<>(queryParts.args());
		countArgs.addAll(havingParts.args());
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from (
					select b.warehouse_id, b.material_id, b.unit_id%s%s%s
					from inv_stock_balance b
					left join mst_warehouse w on w.id = b.warehouse_id
					left join mst_material m on m.id = b.material_id
					left join mst_unit u on u.id = b.unit_id
					left join inv_batch bt on bt.id = b.batch_id
					left join inv_serial sr on sr.id = b.serial_id
					%s
					group by b.warehouse_id, b.material_id, b.unit_id%s%s%s
					%s
				) grouped
				""".formatted(ownershipCountSelect, trackingCountSelect, costLayerGroupBy, queryParts.where(),
						ownershipCountSelect, trackingCountSelect, costLayerGroupBy, havingParts.having()),
				Long.class, countArgs.toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.addAll(havingParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<InventoryBalanceResponse> items = this.jdbcTemplate.query("""
				select min(b.id) as id, b.warehouse_id, w.code as warehouse_code, w.name as warehouse_name,
				       b.material_id, m.code as material_code, m.name as material_name,
				       m.specification as material_spec, m.material_type, %s
				       b.ownership_type, b.project_id, b.valuation_state,
				       b.unit_id, u.name as unit_name,
				       sum(b.quantity_on_hand) as total_quantity_on_hand,
				       sum(b.locked_quantity) as total_locked_quantity,
				       sum(case when b.quality_status = 'PENDING_INSPECTION' then b.quantity_on_hand else 0 end) as pending_inspection_quantity,
				       sum(case when b.quality_status = 'PENDING_INSPECTION' then b.locked_quantity else 0 end) as pending_inspection_locked_quantity,
				       sum(case when b.quality_status = 'QUALIFIED' then b.quantity_on_hand else 0 end) as qualified_quantity,
				       sum(case when b.quality_status = 'QUALIFIED' then b.locked_quantity else 0 end) as qualified_locked_quantity,
				       sum(case when b.quality_status = 'REJECTED' then b.quantity_on_hand else 0 end) as rejected_quantity,
				       sum(case when b.quality_status = 'REJECTED' then b.locked_quantity else 0 end) as rejected_locked_quantity,
				       sum(case when b.quality_status = 'FROZEN' then b.quantity_on_hand else 0 end) as frozen_quantity,
				       sum(case when b.quality_status = 'FROZEN' then b.locked_quantity else 0 end) as frozen_locked_quantity,
				       coalesce(max(r.reserved_quantity), 0) as reserved_quantity,
				       coalesce(max(r.occupied_quantity), 0) as occupied_quantity,
				       coalesce(max(ma.material_available_quantity), 0) as material_available_quantity,
				       coalesce(max(pi.in_transit_quantity), 0) as in_transit_quantity,
				       coalesce(max(sd.sales_demand_quantity), 0) as sales_demand_quantity,
				       coalesce(max(pd.production_demand_quantity), 0) as production_demand_quantity,
				       %s as inventory_amount,
				       max(b.average_unit_cost) as average_unit_cost,
				       %s as cost_layer_count,
				       %s as cost_layer_id,
				       max(b.updated_at) as updated_at
				from inv_stock_balance b
				left join mst_warehouse w on w.id = b.warehouse_id
				left join mst_material m on m.id = b.material_id
				left join mst_unit u on u.id = b.unit_id
				left join inv_batch bt on bt.id = b.batch_id
				left join inv_serial sr on sr.id = b.serial_id
				%s
				left join (
					select q.material_id,
					       coalesce(q.qualified_quantity, 0) - coalesce(l.locked_quantity, 0) as material_available_quantity
					from (
						select material_id, sum(quantity_on_hand) as qualified_quantity
						from inv_stock_balance
						where quality_status = 'QUALIFIED'
						group by material_id
					) q
					left join (
						select material_id, sum(quantity - released_quantity - consumed_quantity) as locked_quantity
						from inv_stock_reservation
						where status = 'ACTIVE'
						and quality_status = 'QUALIFIED'
						group by material_id
					) l on l.material_id = q.material_id
				) ma on ma.material_id = b.material_id
				left join (
					select pol.material_id, sum(pol.quantity - pol.received_quantity) as in_transit_quantity
					from proc_purchase_order_line pol
					join proc_purchase_order po on po.id = pol.order_id
					where po.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
					group by pol.material_id
				) pi on pi.material_id = b.material_id
				left join (
					select sol.material_id, sum(sol.quantity - sol.shipped_quantity) as sales_demand_quantity
					from sal_sales_order_line sol
					join sal_sales_order so on so.id = sol.order_id
					where so.status in ('CONFIRMED', 'PARTIALLY_SHIPPED')
					group by sol.material_id
				) sd on sd.material_id = b.material_id
				left join (
					select wo.issue_warehouse_id as warehouse_id, wom.material_id,
					       sum(wom.required_quantity - wom.issued_quantity) as production_demand_quantity
					from mfg_work_order_material wom
					join mfg_work_order wo on wo.id = wom.work_order_id
					where wo.status in ('RELEASED', 'IN_PROGRESS')
					group by wo.issue_warehouse_id, wom.material_id
				) pd on pd.warehouse_id = b.warehouse_id and pd.material_id = b.material_id
				%s
				group by b.warehouse_id, w.code, w.name, b.material_id, m.code, m.name, m.specification,
				         m.material_type%s, b.ownership_type, b.project_id, b.valuation_state, b.cost_layer_id,
				         b.unit_id, u.name
				%s
				order by max(b.updated_at) desc, min(b.id) desc
				limit ? offset ?
				""".formatted(trackingSelect, inventoryAmountSelect, costLayerCountSelect, costLayerIdSelect,
						reservationJoin, queryParts.where(), trackingGroupBy, havingParts.having()),
				(rs, rowNum) -> mapBalance(rs, rowNum, selectedQualityStatus, costVisible), args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<InventoryMovementResponse> movements(String keyword, Long warehouseId, Long materialId,
			String movementType, String direction, LocalDate dateFrom, LocalDate dateTo, String qualityStatus, int page,
			int pageSize) {
		return movements(keyword, warehouseId, materialId, movementType, direction, dateFrom, dateTo, qualityStatus,
				null, null, null, null, null, null, null, null, null, null, null, null, page, pageSize, null);
	}

	@Transactional(readOnly = true)
	public PageResponse<InventoryMovementResponse> movements(String keyword, Long warehouseId, Long materialId,
			String movementType, String direction, LocalDate dateFrom, LocalDate dateTo, String qualityStatus,
			String trackingMethod, Long batchId, String batchNo, Long serialId, String serialNo, String ownershipType,
			Long projectId, String valuationMethod, Long costLayerId, String sourceType, Long sourceId,
			Long sourceLineId, int page, int pageSize, CurrentUser currentUser) {
		InventoryQualityStatus parsedQualityStatus = parseNullableQualityStatus(qualityStatus);
		InventoryTrackingMethod parsedTrackingMethod = parseNullableTrackingMethod(trackingMethod);
		QueryParts queryParts = movementQueryParts(keyword, warehouseId, materialId, movementType, direction, dateFrom,
				dateTo, parsedQualityStatus, parsedTrackingMethod, batchId, batchNo, serialId, serialNo, ownershipType,
				projectId, valuationMethod, costLayerId, sourceType, sourceId, sourceLineId);
		boolean costVisible = hasPermission(currentUser, "inventory:valuation:view");
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement mv
				join mst_warehouse w on w.id = mv.warehouse_id
				join mst_material m on m.id = mv.material_id
				join mst_unit u on u.id = mv.unit_id
				left join inv_batch bt on bt.id = mv.batch_id
				left join inv_serial sr on sr.id = mv.serial_id
				left join inv_value_movement vm on vm.stock_movement_id = mv.id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<InventoryMovementResponse> items = this.jdbcTemplate.query("""
				select mv.id, mv.movement_no, mv.movement_type, mv.direction, mv.warehouse_id,
				       w.name as warehouse_name, mv.material_id, m.code as material_code, m.name as material_name,
				       mv.unit_id, u.name as unit_name, mv.quantity, mv.before_quantity, mv.after_quantity,
				       mv.source_type, mv.source_id, mv.source_line_id, mv.business_date, mv.reason, mv.remark,
				       mv.operator_name, mv.occurred_at, mv.quality_status, m.tracking_method, mv.batch_id,
				       bt.batch_no, mv.serial_id, sr.serial_no, d.document_no as source_document_no,
				       null::varchar as target_document_no, mv.ownership_type, mv.project_id, mv.valuation_state,
				       vm.id as value_flow_id, vm.unit_cost, vm.inventory_amount, vm.inventory_amount as movement_amount,
				       vm.valuation_method, vm.cost_layer_id, vm.original_value_movement_id
				from inv_stock_movement mv
				join mst_warehouse w on w.id = mv.warehouse_id
				join mst_material m on m.id = mv.material_id
				join mst_unit u on u.id = mv.unit_id
				left join inv_batch bt on bt.id = mv.batch_id
				left join inv_serial sr on sr.id = mv.serial_id
				left join inv_inventory_document d on d.id = mv.source_id and mv.source_type = 'INVENTORY_DOCUMENT'
				left join inv_value_movement vm on vm.stock_movement_id = mv.id
				%s
				order by mv.occurred_at asc, mv.id asc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapMovement(rs, rowNum, costVisible),
				args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<InventoryBatchSummaryResponse> batches(String keyword, Long materialId, Long warehouseId,
			String qualityStatus, String batchNo, String sourceType, Long sourceId, boolean onlyAvailable, int page,
			int pageSize) {
		InventoryQualityStatus parsedQualityStatus = parseNullableQualityStatus(qualityStatus);
		QueryParts queryParts = batchQueryParts(keyword, materialId, warehouseId, parsedQualityStatus, batchNo,
				sourceType, sourceId);
		HavingParts havingParts = trackingAvailableHaving(onlyAvailable, "sb", "l");
		List<Object> countArgs = new ArrayList<>(queryParts.args());
		countArgs.addAll(havingParts.args());
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from (
					select b.id
					from inv_batch b
					join mst_material m on m.id = b.material_id
					left join inv_stock_balance sb on sb.batch_id = b.id
					left join (
						select batch_id, sum(quantity - released_quantity - consumed_quantity) as locked_quantity
						from inv_stock_reservation
						where status = 'ACTIVE'
						and quality_status = 'QUALIFIED'
						group by batch_id
					) l on l.batch_id = b.id
					%s
					group by b.id
					%s
				) grouped
				""".formatted(queryParts.where(), havingParts.having()), Long.class, countArgs.toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.addAll(havingParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<InventoryBatchSummaryResponse> items = this.jdbcTemplate.query("""
				select b.id, b.batch_no, b.material_id, m.code as material_code, m.name as material_name,
				       b.source_type, b.source_id, b.source_line_id, d.document_no as source_document_no,
				       min(sb.warehouse_id) as warehouse_id, min(w.name) as warehouse_name,
				       case
				           when count(distinct sb.quality_status) filter (where sb.quantity_on_hand > 0) = 1
				               then min(sb.quality_status) filter (where sb.quantity_on_hand > 0)
				           else null
				       end as quality_status,
				       b.business_date, coalesce(sum(sb.quantity_on_hand), 0) as quantity_on_hand,
				       greatest(coalesce(sum(case when sb.quality_status = 'QUALIFIED'
				                              then sb.quantity_on_hand else 0 end), 0)
				                - coalesce(max(l.locked_quantity), 0), 0) as available_quantity,
				       case when greatest(coalesce(sum(case when sb.quality_status = 'QUALIFIED'
				                                           then sb.quantity_on_hand else 0 end), 0)
				                            - coalesce(max(l.locked_quantity), 0), 0) > 0
				            then 'AVAILABLE' else 'UNAVAILABLE' end as stock_status,
				       b.updated_at
				from inv_batch b
				join mst_material m on m.id = b.material_id
				left join inv_stock_balance sb on sb.batch_id = b.id
				left join mst_warehouse w on w.id = sb.warehouse_id
				left join inv_inventory_document d on d.id = b.source_id and b.source_type = 'INVENTORY_DOCUMENT'
				left join (
					select batch_id, sum(quantity - released_quantity - consumed_quantity) as locked_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and quality_status = 'QUALIFIED'
					group by batch_id
				) l on l.batch_id = b.id
				%s
				group by b.id, b.batch_no, b.material_id, m.code, m.name, b.source_type, b.source_id,
				         b.source_line_id, d.document_no, b.business_date, b.updated_at
				%s
				order by b.updated_at desc, b.id desc
				limit ? offset ?
				""".formatted(queryParts.where(), havingParts.having()), this::mapBatchSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public InventoryBatchDetailResponse batch(Long id) {
		return this.jdbcTemplate.query("""
				select b.id, b.batch_no, b.material_id, m.code as material_code, m.name as material_name,
				       b.source_type, b.source_id, b.source_line_id, d.document_no as source_document_no,
				       b.business_date, coalesce(sum(sb.quantity_on_hand), 0) as quantity_on_hand,
				       greatest(coalesce(sum(case when sb.quality_status = 'QUALIFIED'
				                              then sb.quantity_on_hand else 0 end), 0)
				                - coalesce(max(l.locked_quantity), 0), 0) as available_quantity,
				       b.remark, b.created_by, b.created_at, b.updated_by, b.updated_at
				from inv_batch b
				join mst_material m on m.id = b.material_id
				left join inv_stock_balance sb on sb.batch_id = b.id
				left join inv_inventory_document d on d.id = b.source_id and b.source_type = 'INVENTORY_DOCUMENT'
				left join (
					select batch_id, sum(quantity - released_quantity - consumed_quantity) as locked_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and quality_status = 'QUALIFIED'
					group by batch_id
				) l on l.batch_id = b.id
				where b.id = ?
				group by b.id, b.batch_no, b.material_id, m.code, m.name, b.source_type, b.source_id,
				         b.source_line_id, d.document_no, b.business_date, b.remark, b.created_by, b.created_at,
				         b.updated_by, b.updated_at
				""", this::mapBatchDetail, id).stream().findFirst().orElseThrow(this::trackingNotFound);
	}

	@Transactional(readOnly = true)
	public PageResponse<InventorySerialSummaryResponse> serials(String keyword, Long materialId, Long warehouseId,
			String qualityStatus, String serialNo, Long batchId, String sourceType, Long sourceId,
			boolean onlyAvailable, int page, int pageSize) {
		InventoryQualityStatus parsedQualityStatus = parseNullableQualityStatus(qualityStatus);
		QueryParts queryParts = serialQueryParts(keyword, materialId, warehouseId, parsedQualityStatus, serialNo,
				batchId, sourceType, sourceId, onlyAvailable);
		String serialAvailabilityJoin = serialAvailabilityJoin();
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_serial s
				join mst_material m on m.id = s.material_id
				left join inv_batch b on b.id = s.batch_id
				left join mst_warehouse w on w.id = s.warehouse_id
				%s
				%s
				""".formatted(serialAvailabilityJoin, queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<InventorySerialSummaryResponse> items = this.jdbcTemplate.query("""
				select s.id, s.serial_no, s.material_id, m.code as material_code, m.name as material_name,
				       s.batch_id, b.batch_no, s.warehouse_id, w.name as warehouse_name,
				       s.quality_status, s.stock_status, s.source_type, s.source_id, s.source_line_id,
				       d.document_no as source_document_no,
				       case when s.stock_status = 'IN_STOCK' and s.quality_status = 'QUALIFIED'
				            then greatest(coalesce(sb.quantity_on_hand, 0) - coalesce(l.locked_quantity, 0), 0)
				            else 0 end as available_quantity,
				       s.updated_at
				from inv_serial s
				join mst_material m on m.id = s.material_id
				left join inv_batch b on b.id = s.batch_id
				left join mst_warehouse w on w.id = s.warehouse_id
				left join inv_inventory_document d on d.id = s.source_id and s.source_type = 'INVENTORY_DOCUMENT'
				%s
				%s
				order by s.updated_at desc, s.id desc
				limit ? offset ?
				""".formatted(serialAvailabilityJoin, queryParts.where()), this::mapSerialSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public InventorySerialDetailResponse serial(Long id) {
		return this.jdbcTemplate.query("""
				select s.id, s.serial_no, s.material_id, m.code as material_code, m.name as material_name,
				       s.batch_id, b.batch_no, s.warehouse_id, w.name as warehouse_name,
				       s.quality_status, s.stock_status, s.source_type, s.source_id, s.source_line_id,
				       d.document_no as source_document_no, s.business_date, s.remark, s.created_by,
				       s.created_at, s.updated_by, s.updated_at
				from inv_serial s
				join mst_material m on m.id = s.material_id
				left join inv_batch b on b.id = s.batch_id
				left join mst_warehouse w on w.id = s.warehouse_id
				left join inv_inventory_document d on d.id = s.source_id and s.source_type = 'INVENTORY_DOCUMENT'
				where s.id = ?
				""", this::mapSerialDetail, id).stream().findFirst().orElseThrow(this::trackingNotFound);
	}

	@Transactional(readOnly = true)
	public InventoryTraceDetailResponse batchTrace(Long id) {
		return batchTrace(id, null);
	}

	@Transactional(readOnly = true)
	public InventoryTraceDetailResponse batchTrace(Long id, CurrentUser currentUser) {
		InventoryTraceSubjectResponse rawSubject = batchTraceSubject(id);
		InventoryTraceSubjectResponse subject = maskTraceSubject(rawSubject, currentUser);
		List<InventoryTraceNodeResponse> activeReservations = maskTraceNodes(traceReservationNodes("batch_id", id),
				currentUser);
		List<InventoryTraceNodeResponse> sourceRecords = maskTraceNodes(sourceRecord(rawSubject), currentUser);
		List<InventoryTraceNodeResponse> movements = maskTraceNodes(traceMovementNodes("batch_id", id), currentUser);
		return new InventoryTraceDetailResponse(subject, traceBalances("batch_id", id),
				activeReservations, sourceRecords, qualityEvents(movements), outboundRecords(movements),
				returnRecords(movements), movements, restrictedSources(sourceRecords, activeReservations, movements));
	}

	@Transactional(readOnly = true)
	public InventoryTraceDetailResponse serialTrace(Long id) {
		return serialTrace(id, null);
	}

	@Transactional(readOnly = true)
	public InventoryTraceDetailResponse serialTrace(Long id, CurrentUser currentUser) {
		InventoryTraceSubjectResponse rawSubject = serialTraceSubject(id);
		InventoryTraceSubjectResponse subject = maskTraceSubject(rawSubject, currentUser);
		List<InventoryTraceNodeResponse> activeReservations = maskTraceNodes(traceReservationNodes("serial_id", id),
				currentUser);
		List<InventoryTraceNodeResponse> sourceRecords = maskTraceNodes(sourceRecord(rawSubject), currentUser);
		List<InventoryTraceNodeResponse> movements = maskTraceNodes(traceMovementNodes("serial_id", id), currentUser);
		return new InventoryTraceDetailResponse(subject, traceBalances("serial_id", id),
				activeReservations, sourceRecords, qualityEvents(movements), outboundRecords(movements),
				returnRecords(movements), movements, restrictedSources(sourceRecords, activeReservations, movements));
	}

	@Transactional(readOnly = true)
	public PageResponse<InventoryReservationResponse> reservations(String keyword, Long warehouseId, Long materialId,
			String reservationType, String status, String sourceType, Long sourceId, Long sourceLineId,
			LocalDate businessDateFrom, LocalDate businessDateTo, int page, int pageSize) {
		QueryParts queryParts = reservationQueryParts(keyword, warehouseId, materialId, reservationType, status,
				sourceType, sourceId, sourceLineId, businessDateFrom, businessDateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_reservation r
				join mst_warehouse w on w.id = r.warehouse_id
				join mst_material m on m.id = r.material_id
				join mst_unit u on u.id = r.unit_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<InventoryReservationResponse> items = this.jdbcTemplate.query("""
				select r.id, r.reservation_no, r.reservation_type, r.status, r.warehouse_id,
				       w.code as warehouse_code, w.name as warehouse_name, r.material_id,
				       m.code as material_code, m.name as material_name, m.specification as material_spec,
				       r.unit_id, u.name as unit_name, r.quality_status, r.quantity, r.released_quantity,
				       r.consumed_quantity, r.source_type, r.source_id, r.source_line_id,
				       r.source_document_no, r.business_date, r.reason, r.remark, r.created_by,
				       r.created_at, r.updated_by, r.updated_at, r.released_by, r.released_at,
				       r.ownership_type, r.project_id, r.cost_layer_id
				from inv_stock_reservation r
				join mst_warehouse w on w.id = r.warehouse_id
				join mst_material m on m.id = r.material_id
				join mst_unit u on u.id = r.unit_id
				%s
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapReservation(rs, rowNum, false),
				args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public InventoryReservationResponse reservation(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.reservation_no, r.reservation_type, r.status, r.warehouse_id,
				       w.code as warehouse_code, w.name as warehouse_name, r.material_id,
				       m.code as material_code, m.name as material_name, m.specification as material_spec,
				       r.unit_id, u.name as unit_name, r.quality_status, r.quantity, r.released_quantity,
				       r.consumed_quantity, r.source_type, r.source_id, r.source_line_id,
				       r.source_document_no, r.business_date, r.reason, r.remark, r.created_by,
				       r.created_at, r.updated_by, r.updated_at, r.released_by, r.released_at,
				       r.ownership_type, r.project_id, r.cost_layer_id
				from inv_stock_reservation r
				join mst_warehouse w on w.id = r.warehouse_id
				join mst_material m on m.id = r.material_id
				join mst_unit u on u.id = r.unit_id
				where r.id = ?
				""", (rs, rowNum) -> mapReservation(rs, rowNum, true), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_RESERVATION_NOT_FOUND));
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
				       d.created_by, d.created_at, d.updated_at, d.posted_by, d.posted_at, d.version
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
				summary.postedAt(), summary.version(), documentLines(id));
	}

	@Transactional
	public InventoryDocumentDetailResponse createDocument(InventoryDocumentRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedDocument document = validateDocument(request);
		this.businessPeriodGuard.assertWritable(document.businessDate(), BusinessPeriodOperation.CREATE, SOURCE_TYPE, null);
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
		this.businessPeriodGuard.assertWritable(document.businessDate(), BusinessPeriodOperation.UPDATE, SOURCE_TYPE, id);
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
	public InventoryDocumentDetailResponse postDocument(Long id, InventoryDocumentPostRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		try {
			DocumentRow document = lockDocument(id).orElseThrow(this::notFound);
			if (document.status() != InventoryDocumentStatus.DRAFT) {
				throw new BusinessException(ApiErrorCode.INVENTORY_DUPLICATE_POST);
			}
			if (request != null && request.version() != null && !request.version().equals(document.version())) {
				throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
			}
			this.businessPeriodGuard.assertWritable(document.businessDate(), BusinessPeriodOperation.POST, SOURCE_TYPE, id);
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
						line.materialId(), line.unitId(), line.quantity(), InventoryQualityStatus.QUALIFIED,
						SOURCE_TYPE, document.id(), line.id(), document.businessDate(), document.reason(),
						line.remark(), operatorName, null, null, line.unitPrice()));
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
				and quality_status = ?
				""", Long.class, warehouseId, materialId, InventoryMovementType.OPENING.name(),
				InventoryQualityStatus.QUALIFIED.name());
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
			BigDecimal unitPrice = validateUnitPrice(line.unitPrice());
			lines.add(new ValidatedLine(line.lineNo(), line.warehouseId(), line.materialId(), unitId, quantity,
					adjustmentDirection, lineRemark, unitPrice));
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

	private BigDecimal validateUnitPrice(BigDecimal value) {
		if (value == null) {
			return null;
		}
		if (value.compareTo(ZERO) < 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
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
						unit_price, remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", documentId, line.lineNo(), line.warehouseId(), line.materialId(), line.unitId(),
					line.quantity(), line.adjustmentDirection() == null ? null : line.adjustmentDirection().name(),
					line.unitPrice(), blankToNull(line.remark()), now, now);
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
			InventoryTrackingMethod trackingMethod, Long batchId, String batchNo, Long serialId, String serialNo,
			String ownershipType, Long projectId, String valuationState) {
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
		if (trackingMethod != null) {
			conditions.add("m.tracking_method = ?");
			args.add(trackingMethod.name());
		}
		if (batchId != null) {
			conditions.add("b.batch_id = ?");
			args.add(batchId);
		}
		if (hasText(batchNo)) {
			conditions.add("bt.batch_no ilike ?");
			args.add("%" + batchNo + "%");
		}
		if (serialId != null) {
			conditions.add("b.serial_id = ?");
			args.add(serialId);
		}
		if (hasText(serialNo)) {
			conditions.add("sr.serial_no ilike ?");
			args.add("%" + serialNo + "%");
		}
		if (hasText(ownershipType)) {
			conditions.add("b.ownership_type = ?");
			args.add(parseOwnershipType(ownershipType));
		}
		if (projectId != null) {
			conditions.add("b.project_id = ?");
			args.add(projectId);
		}
		if (hasText(valuationState)) {
			conditions.add("b.valuation_state = ?");
			args.add(parseValuationState(valuationState));
		}
		return where(conditions, args);
	}

	private QueryParts movementQueryParts(String keyword, Long warehouseId, Long materialId, String movementType,
			String direction, LocalDate dateFrom, LocalDate dateTo, InventoryQualityStatus qualityStatus,
			InventoryTrackingMethod trackingMethod, Long batchId, String batchNo, Long serialId, String serialNo,
			String ownershipType, Long projectId, String valuationMethod, Long costLayerId, String sourceType,
			Long sourceId, Long sourceLineId) {
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
		if (qualityStatus != null) {
			conditions.add("mv.quality_status = ?");
			args.add(qualityStatus.name());
		}
		if (trackingMethod != null) {
			conditions.add("m.tracking_method = ?");
			args.add(trackingMethod.name());
		}
		if (batchId != null) {
			conditions.add("mv.batch_id = ?");
			args.add(batchId);
		}
		if (hasText(batchNo)) {
			conditions.add("bt.batch_no ilike ?");
			args.add("%" + batchNo + "%");
		}
		if (serialId != null) {
			conditions.add("mv.serial_id = ?");
			args.add(serialId);
		}
		if (hasText(serialNo)) {
			conditions.add("sr.serial_no ilike ?");
			args.add("%" + serialNo + "%");
		}
		if (hasText(ownershipType)) {
			conditions.add("mv.ownership_type = ?");
			args.add(parseOwnershipType(ownershipType));
		}
		if (projectId != null) {
			conditions.add("mv.project_id = ?");
			args.add(projectId);
		}
		if (hasText(valuationMethod)) {
			conditions.add("vm.valuation_method = ?");
			args.add(valuationMethod);
		}
		if (costLayerId != null) {
			conditions.add("vm.cost_layer_id = ?");
			args.add(costLayerId);
		}
		if (hasText(sourceType)) {
			conditions.add("mv.source_type = ?");
			args.add(sourceType.trim());
		}
		if (sourceId != null) {
			conditions.add("mv.source_id = ?");
			args.add(sourceId);
		}
		if (sourceLineId != null) {
			conditions.add("mv.source_line_id = ?");
			args.add(sourceLineId);
		}
		return where(conditions, args);
	}

	private QueryParts batchQueryParts(String keyword, Long materialId, Long warehouseId,
			InventoryQualityStatus qualityStatus, String batchNo, String sourceType, Long sourceId) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(b.batch_no ilike ? or m.code ilike ? or m.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (materialId != null) {
			conditions.add("b.material_id = ?");
			args.add(materialId);
		}
		if (warehouseId != null) {
			conditions.add("sb.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (qualityStatus != null) {
			conditions.add("sb.quality_status = ?");
			args.add(qualityStatus.name());
		}
		if (hasText(batchNo)) {
			conditions.add("b.batch_no ilike ?");
			args.add("%" + batchNo + "%");
		}
		if (hasText(sourceType)) {
			conditions.add("b.source_type = ?");
			args.add(sourceType);
		}
		if (sourceId != null) {
			conditions.add("b.source_id = ?");
			args.add(sourceId);
		}
		return where(conditions, args);
	}

	private QueryParts serialQueryParts(String keyword, Long materialId, Long warehouseId,
			InventoryQualityStatus qualityStatus, String serialNo, Long batchId, String sourceType, Long sourceId,
			boolean onlyAvailable) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(s.serial_no ilike ? or m.code ilike ? or m.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (materialId != null) {
			conditions.add("s.material_id = ?");
			args.add(materialId);
		}
		if (warehouseId != null) {
			conditions.add("s.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (qualityStatus != null) {
			conditions.add("s.quality_status = ?");
			args.add(qualityStatus.name());
		}
		if (hasText(serialNo)) {
			conditions.add("s.serial_no ilike ?");
			args.add("%" + serialNo + "%");
		}
		if (batchId != null) {
			conditions.add("s.batch_id = ?");
			args.add(batchId);
		}
		if (hasText(sourceType)) {
			conditions.add("s.source_type = ?");
			args.add(sourceType);
		}
		if (sourceId != null) {
			conditions.add("s.source_id = ?");
			args.add(sourceId);
		}
		if (onlyAvailable) {
			conditions.add("s.stock_status = 'IN_STOCK'");
			conditions.add("s.quality_status = 'QUALIFIED'");
			conditions.add("greatest(coalesce(sb.quantity_on_hand, 0) - coalesce(l.locked_quantity, 0), 0) > 0");
		}
		return where(conditions, args);
	}

	private String serialAvailabilityJoin() {
		return """
				left join inv_stock_balance sb on sb.serial_id = s.id
					and sb.warehouse_id is not distinct from s.warehouse_id
					and sb.material_id = s.material_id
					and sb.quality_status = s.quality_status
				left join (
					select serial_id, sum(quantity - released_quantity - consumed_quantity) as locked_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and quality_status = 'QUALIFIED'
					group by serial_id
				) l on l.serial_id = s.id
				""";
	}

	private HavingParts trackingAvailableHaving(boolean onlyAvailable, String balanceAlias, String lockAlias) {
		if (!onlyAvailable) {
			return new HavingParts("", List.of());
		}
		return new HavingParts("""
				having greatest(coalesce(sum(case when %s.quality_status = 'QUALIFIED'
				                                then %s.quantity_on_hand else 0 end), 0)
				                 - coalesce(max(%s.locked_quantity), 0), 0) > 0
				""".formatted(balanceAlias, balanceAlias, lockAlias), List.of());
	}

	private QueryParts reservationQueryParts(String keyword, Long warehouseId, Long materialId, String reservationType,
			String status, String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDateFrom,
			LocalDate businessDateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(r.reservation_no ilike ? or r.source_document_no ilike ? or m.code ilike ? or m.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (warehouseId != null) {
			conditions.add("r.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (materialId != null) {
			conditions.add("r.material_id = ?");
			args.add(materialId);
		}
		if (hasText(reservationType)) {
			conditions.add("r.reservation_type = ?");
			args.add(parseReservationType(reservationType).name());
		}
		if (hasText(status)) {
			conditions.add("r.status = ?");
			args.add(parseReservationStatus(status).name());
		}
		if (hasText(sourceType)) {
			conditions.add("r.source_type = ?");
			args.add(sourceType);
		}
		if (sourceId != null) {
			conditions.add("r.source_id = ?");
			args.add(sourceId);
		}
		if (sourceLineId != null) {
			conditions.add("r.source_line_id = ?");
			args.add(sourceLineId);
		}
		if (businessDateFrom != null) {
			conditions.add("r.business_date >= ?");
			args.add(businessDateFrom);
		}
		if (businessDateTo != null) {
			conditions.add("r.business_date <= ?");
			args.add(businessDateTo);
		}
		return where(conditions, args);
	}

	private HavingParts balanceHavingParts(InventoryQualityStatus qualityStatus, boolean onlyPositive) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (qualityStatus != null) {
			conditions.add("sum(case when b.quality_status = ? then 1 else 0 end) > 0");
			args.add(qualityStatus.name());
			if (onlyPositive) {
				conditions.add("sum(case when b.quality_status = ? then b.quantity_on_hand else 0 end) > 0");
				args.add(qualityStatus.name());
			}
		}
		else if (onlyPositive) {
			conditions.add("sum(b.quantity_on_hand) > 0");
		}
		String having = conditions.isEmpty() ? "" : "having " + String.join(" and ", conditions);
		return new HavingParts(having, args);
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
					       d.created_by, d.created_at, d.updated_at, d.posted_by, d.posted_at, d.version
					from inv_inventory_document d
					where d.id = ?
					""", this::mapDocumentSummary, id)
			.stream()
			.findFirst();
	}

	private Optional<DocumentRow> lockDocument(Long id) {
		return this.jdbcTemplate
			.query("""
					select id, document_no, document_type, status, business_date, reason, remark, version
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
				       unit_price, before_quantity, after_quantity, remark
				from inv_inventory_document_line
				where document_id = ?
				order by line_no asc, id asc
				""", this::mapDocumentLineRow, documentId);
	}

	private List<InventoryDocumentLineResponse> documentLines(Long documentId) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.warehouse_id, w.name as warehouse_name, l.material_id,
				       m.code as material_code, m.name as material_name, l.unit_id, u.name as unit_name,
				       l.quantity, l.adjustment_direction, l.unit_price, l.before_quantity, l.after_quantity, l.remark
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
				rs.getBigDecimal("unit_price"), rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
				rs.getString("remark")),
				documentId);
	}

	private InventoryBalanceResponse mapBalance(ResultSet rs, int rowNum, InventoryQualityStatus qualityStatus,
			boolean costVisible)
			throws SQLException {
		InventoryTrackingMethod trackingMethod = InventoryTrackingMethod.valueOf(rs.getString("tracking_method"));
		Long batchId = nullableLong(rs, "batch_id");
		Long serialId = nullableLong(rs, "serial_id");
		BigDecimal totalQuantity = rs.getBigDecimal("total_quantity_on_hand");
		BigDecimal totalLockedQuantity = rs.getBigDecimal("total_locked_quantity");
		BigDecimal pendingInspectionQuantity = rs.getBigDecimal("pending_inspection_quantity");
		BigDecimal pendingInspectionLockedQuantity = rs.getBigDecimal("pending_inspection_locked_quantity");
		BigDecimal qualifiedQuantity = rs.getBigDecimal("qualified_quantity");
		BigDecimal qualifiedLockedQuantity = rs.getBigDecimal("qualified_locked_quantity");
		BigDecimal rejectedQuantity = rs.getBigDecimal("rejected_quantity");
		BigDecimal rejectedLockedQuantity = rs.getBigDecimal("rejected_locked_quantity");
		BigDecimal frozenQuantity = rs.getBigDecimal("frozen_quantity");
		BigDecimal frozenLockedQuantity = rs.getBigDecimal("frozen_locked_quantity");
		BigDecimal reservedQuantity = rs.getBigDecimal("reserved_quantity");
		BigDecimal occupiedQuantity = rs.getBigDecimal("occupied_quantity");
		BigDecimal activeLockedQuantity = reservedQuantity.add(occupiedQuantity);
		BigDecimal inTransitQuantity = rs.getBigDecimal("in_transit_quantity");
		BigDecimal demandQuantity = rs.getBigDecimal("sales_demand_quantity")
			.add(rs.getBigDecimal("production_demand_quantity"));
		BigDecimal quantityOnHand = selectedQuantity(qualityStatus, totalQuantity, pendingInspectionQuantity,
				qualifiedQuantity, rejectedQuantity, frozenQuantity);
		BigDecimal lockedQuantity = qualityStatus == null || qualityStatus == InventoryQualityStatus.QUALIFIED
				? activeLockedQuantity : selectedQuantity(qualityStatus, totalLockedQuantity,
						pendingInspectionLockedQuantity, qualifiedLockedQuantity, rejectedLockedQuantity,
						frozenLockedQuantity);
		BigDecimal visibleReservedQuantity = qualityStatus == null || qualityStatus == InventoryQualityStatus.QUALIFIED
				? reservedQuantity : ZERO;
		BigDecimal visibleOccupiedQuantity = qualityStatus == null || qualityStatus == InventoryQualityStatus.QUALIFIED
				? occupiedQuantity : ZERO;
		BigDecimal visibleInTransitQuantity = qualityStatus == null || qualityStatus == InventoryQualityStatus.QUALIFIED
				? inTransitQuantity : ZERO;
		BigDecimal availableQuantity = qualityStatus == null || qualityStatus == InventoryQualityStatus.QUALIFIED
				? qualifiedQuantity.subtract(activeLockedQuantity) : ZERO;
		BigDecimal availableToPromiseQuantity = availableQuantity;
		BigDecimal materialAvailableQuantity = qualityStatus == null || qualityStatus == InventoryQualityStatus.QUALIFIED
				? rs.getBigDecimal("material_available_quantity") : ZERO;
		BigDecimal netRequirementShortageQuantity = qualityStatus == null
				|| qualityStatus == InventoryQualityStatus.QUALIFIED
						? demandQuantity.subtract(materialAvailableQuantity.add(visibleInTransitQuantity)).max(ZERO)
						: ZERO;
		BigDecimal traceableQuantity = batchId != null || serialId != null ? quantityOnHand : null;
		BigDecimal inventoryAmount = costVisible ? rs.getBigDecimal("inventory_amount") : null;
		BigDecimal averageUnitCost = costVisible ? rs.getBigDecimal("average_unit_cost") : null;
		return new InventoryBalanceResponse(rs.getLong("id"), rs.getLong("warehouse_id"),
				rs.getString("warehouse_code"), rs.getString("warehouse_name"), rs.getLong("material_id"),
				rs.getString("material_code"), rs.getString("material_name"), rs.getString("material_spec"),
				rs.getString("material_type"), trackingMethod.name(), trackingMethod.displayName(), batchId,
				rs.getString("batch_no"), serialId, rs.getString("serial_no"), traceableQuantity,
				rs.getLong("unit_id"), rs.getString("unit_name"), qualityStatus == null ? null : qualityStatus.name(),
				qualityStatus == null ? null : qualityStatus.displayName(), quantityOnHand, lockedQuantity,
				availableQuantity, totalQuantity, visibleReservedQuantity, visibleOccupiedQuantity,
				visibleInTransitQuantity, availableToPromiseQuantity, netRequirementShortageQuantity, totalQuantity,
				pendingInspectionQuantity, qualifiedQuantity, rejectedQuantity, frozenQuantity,
				rs.getString("ownership_type"), nullableLong(rs, "project_id"), rs.getString("valuation_state"),
				costVisible, inventoryAmount, averageUnitCost, rs.getLong("cost_layer_count"),
				costVisible ? nullableLong(rs, "cost_layer_id") : null, rs.getObject("updated_at", OffsetDateTime.class));
	}

	private InventoryMovementResponse mapMovement(ResultSet rs, int rowNum, boolean costVisible) throws SQLException {
		InventoryQualityStatus qualityStatus = InventoryQualityStatus.valueOf(rs.getString("quality_status"));
		InventoryTrackingMethod trackingMethod = InventoryTrackingMethod.valueOf(rs.getString("tracking_method"));
		String sourceType = rs.getString("source_type");
		Long sourceId = nullableLong(rs, "source_id");
		Long sourceLineId = nullableLong(rs, "source_line_id");
		String sourceDocumentNo = resolveSourceDocumentNo(sourceType, sourceId, rs.getString("source_document_no"));
		return new InventoryMovementResponse(rs.getLong("id"), rs.getString("movement_no"),
				rs.getString("movement_type"), rs.getString("direction"), rs.getLong("warehouse_id"),
				rs.getString("warehouse_name"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				quantity(rs.getBigDecimal("quantity")), quantity(rs.getBigDecimal("before_quantity")),
				quantity(rs.getBigDecimal("after_quantity")),
				sourceType, sourceId, sourceLineId,
				rs.getObject("business_date", LocalDate.class), rs.getString("reason"), rs.getString("remark"),
				rs.getString("operator_name"), rs.getObject("occurred_at", OffsetDateTime.class),
				qualityStatus.name(), qualityStatus.displayName(), trackingMethod.name(), trackingMethod.displayName(),
				nullableLong(rs, "batch_id"), rs.getString("batch_no"), nullableLong(rs, "serial_id"),
				rs.getString("serial_no"), sourceDocumentNo, rs.getString("target_document_no"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"), rs.getString("valuation_state"),
				costVisible, costVisible ? quantity(rs.getBigDecimal("unit_cost")) : null,
				costVisible ? money(rs.getBigDecimal("inventory_amount")) : null,
				costVisible ? money(rs.getBigDecimal("movement_amount")) : null, rs.getString("valuation_method"),
				costVisible ? nullableLong(rs, "value_flow_id") : null, costVisible ? nullableLong(rs, "cost_layer_id") : null,
				costVisible ? nullableLong(rs, "original_value_movement_id") : null,
				costVisible ? nullableLong(rs, "original_value_movement_id") : null);
	}

	private static String quantity(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	private static String money(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private InventoryBatchSummaryResponse mapBatchSummary(ResultSet rs, int rowNum) throws SQLException {
		Long batchId = rs.getLong("id");
		String qualityStatus = rs.getString("quality_status");
		String stockStatus = rs.getString("stock_status");
		BigDecimal availableQuantity = rs.getBigDecimal("available_quantity");
		boolean selectable = availableQuantity != null && availableQuantity.compareTo(ZERO) > 0;
		String disabledReasonCode = selectable ? null : batchDisabledReasonCode(qualityStatus);
		String disabledReason = selectable ? null : batchDisabledReason(qualityStatus);
		String sourceType = rs.getString("source_type");
		Long sourceId = nullableLong(rs, "source_id");
		Long sourceLineId = nullableLong(rs, "source_line_id");
		String sourceDocumentNo = resolveSourceDocumentNo(sourceType, sourceId, rs.getString("source_document_no"));
		return new InventoryBatchSummaryResponse(batchId, rs.getString("batch_no"), rs.getLong("material_id"),
				rs.getString("material_code"), rs.getString("material_name"), sourceType, sourceId, sourceLineId,
				sourceDocumentNo, nullableLong(rs, "warehouse_id"), rs.getString("warehouse_name"), qualityStatus,
				qualityStatusName(qualityStatus), stockStatus, stockStatusName(stockStatus),
				rs.getObject("business_date", LocalDate.class), rs.getBigDecimal("quantity_on_hand"),
				availableQuantity, selectable, disabledReasonCode, disabledReason, batchQualityStatusSummary(batchId),
				rs.getObject("updated_at", OffsetDateTime.class));
	}

	private InventoryBatchDetailResponse mapBatchDetail(ResultSet rs, int rowNum) throws SQLException {
		Long batchId = rs.getLong("id");
		String sourceType = rs.getString("source_type");
		Long sourceId = nullableLong(rs, "source_id");
		Long sourceLineId = nullableLong(rs, "source_line_id");
		String sourceDocumentNo = resolveSourceDocumentNo(sourceType, sourceId, rs.getString("source_document_no"));
		return new InventoryBatchDetailResponse(batchId, rs.getString("batch_no"), rs.getLong("material_id"),
				rs.getString("material_code"), rs.getString("material_name"), sourceType, sourceId, sourceLineId,
				sourceDocumentNo,
				rs.getObject("business_date", LocalDate.class), rs.getBigDecimal("quantity_on_hand"),
				rs.getBigDecimal("available_quantity"), batchQualityStatusSummary(batchId), rs.getString("remark"),
				rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private InventorySerialSummaryResponse mapSerialSummary(ResultSet rs, int rowNum) throws SQLException {
		String qualityStatus = rs.getString("quality_status");
		String stockStatus = rs.getString("stock_status");
		BigDecimal availableQuantity = rs.getBigDecimal("available_quantity");
		boolean selectable = availableQuantity != null && availableQuantity.compareTo(ZERO) > 0;
		String sourceType = rs.getString("source_type");
		Long sourceId = nullableLong(rs, "source_id");
		Long sourceLineId = nullableLong(rs, "source_line_id");
		String sourceDocumentNo = resolveSourceDocumentNo(sourceType, sourceId, rs.getString("source_document_no"));
		return new InventorySerialSummaryResponse(rs.getLong("id"), rs.getString("serial_no"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				nullableLong(rs, "batch_id"), rs.getString("batch_no"), nullableLong(rs, "warehouse_id"),
				rs.getString("warehouse_name"), qualityStatus, qualityStatusName(qualityStatus), stockStatus,
				stockStatusName(stockStatus), availableQuantity, selectable,
				selectable ? null : serialDisabledReasonCode(stockStatus, qualityStatus),
				selectable ? null : serialDisabledReason(stockStatus, qualityStatus), sourceType, sourceId,
				sourceLineId, sourceDocumentNo,
				rs.getObject("updated_at", OffsetDateTime.class));
	}

	private InventorySerialDetailResponse mapSerialDetail(ResultSet rs, int rowNum) throws SQLException {
		String qualityStatus = rs.getString("quality_status");
		String stockStatus = rs.getString("stock_status");
		String sourceType = rs.getString("source_type");
		Long sourceId = nullableLong(rs, "source_id");
		Long sourceLineId = nullableLong(rs, "source_line_id");
		String sourceDocumentNo = resolveSourceDocumentNo(sourceType, sourceId, rs.getString("source_document_no"));
		return new InventorySerialDetailResponse(rs.getLong("id"), rs.getString("serial_no"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				nullableLong(rs, "batch_id"), rs.getString("batch_no"), nullableLong(rs, "warehouse_id"),
				rs.getString("warehouse_name"), qualityStatus, qualityStatusName(qualityStatus), stockStatus,
				stockStatusName(stockStatus), sourceType, sourceId, sourceLineId, sourceDocumentNo,
				rs.getObject("business_date", LocalDate.class), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getString("updated_by"),
				rs.getObject("updated_at", OffsetDateTime.class));
	}

	private List<InventoryTrackingQualityStatusSummaryResponse> batchQualityStatusSummary(Long batchId) {
		return this.jdbcTemplate.query("""
				select sb.quality_status, sum(sb.quantity_on_hand) as quantity_on_hand,
				       greatest(case when sb.quality_status = 'QUALIFIED' then sum(sb.quantity_on_hand) else 0 end
				                - coalesce(max(l.locked_quantity), 0), 0) as available_quantity
				from inv_stock_balance sb
				left join (
					select batch_id, quality_status,
					       sum(quantity - released_quantity - consumed_quantity) as locked_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					group by batch_id, quality_status
				) l on l.batch_id = sb.batch_id and l.quality_status = sb.quality_status
				where sb.batch_id = ?
				group by sb.quality_status
				order by sb.quality_status asc
				""", (rs, rowNum) -> new InventoryTrackingQualityStatusSummaryResponse(rs.getString("quality_status"),
				qualityStatusName(rs.getString("quality_status")), rs.getBigDecimal("quantity_on_hand"),
				rs.getBigDecimal("available_quantity")), batchId);
	}

	private InventoryTraceSubjectResponse batchTraceSubject(Long id) {
		return this.jdbcTemplate.query("""
				select b.id as batch_id, b.batch_no, null::bigint as serial_id, null::varchar as serial_no,
				       b.material_id, m.code as material_code, m.name as material_name, b.source_type,
				       b.source_id, b.source_line_id, d.document_no as source_document_no, b.business_date
				from inv_batch b
				join mst_material m on m.id = b.material_id
				left join inv_inventory_document d on d.id = b.source_id and b.source_type = 'INVENTORY_DOCUMENT'
				where b.id = ?
				""", (rs, rowNum) -> mapTraceSubject(rs, InventoryTrackingMethod.BATCH), id)
			.stream()
			.findFirst()
			.orElseThrow(this::trackingNotFound);
	}

	private InventoryTraceSubjectResponse serialTraceSubject(Long id) {
		return this.jdbcTemplate.query("""
				select s.batch_id, b.batch_no, s.id as serial_id, s.serial_no, s.material_id,
				       m.code as material_code, m.name as material_name, s.source_type, s.source_id,
				       s.source_line_id, d.document_no as source_document_no, s.business_date
				from inv_serial s
				join mst_material m on m.id = s.material_id
				left join inv_batch b on b.id = s.batch_id
				left join inv_inventory_document d on d.id = s.source_id and s.source_type = 'INVENTORY_DOCUMENT'
				where s.id = ?
				""", (rs, rowNum) -> mapTraceSubject(rs, InventoryTrackingMethod.SERIAL), id)
			.stream()
			.findFirst()
			.orElseThrow(this::trackingNotFound);
	}

	private InventoryTraceSubjectResponse mapTraceSubject(ResultSet rs, InventoryTrackingMethod trackingMethod)
			throws SQLException {
		String sourceType = rs.getString("source_type");
		Long sourceId = nullableLong(rs, "source_id");
		Long sourceLineId = nullableLong(rs, "source_line_id");
		String sourceDocumentNo = resolveSourceDocumentNo(sourceType, sourceId, rs.getString("source_document_no"));
		return new InventoryTraceSubjectResponse(trackingMethod.name(), trackingMethod.displayName(),
				nullableLong(rs, "batch_id"), rs.getString("batch_no"), nullableLong(rs, "serial_id"),
				rs.getString("serial_no"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), sourceType, sourceId, sourceLineId, sourceDocumentNo,
				rs.getObject("business_date", LocalDate.class));
	}

	private List<InventoryTraceBalanceResponse> traceBalances(String trackingColumn, Long trackingId) {
		return this.jdbcTemplate.query("""
				select sb.warehouse_id, w.name as warehouse_name, sb.quality_status,
				       sum(sb.quantity_on_hand) as quantity_on_hand,
				       greatest(case when sb.quality_status = 'QUALIFIED' then sum(sb.quantity_on_hand) else 0 end
				                - coalesce(max(r.reserved_quantity), 0)
				                - coalesce(max(r.occupied_quantity), 0), 0) as available_quantity,
				       coalesce(max(r.reserved_quantity), 0) as reserved_quantity,
				       coalesce(max(r.occupied_quantity), 0) as occupied_quantity
				from inv_stock_balance sb
				left join mst_warehouse w on w.id = sb.warehouse_id
				left join (
					select warehouse_id, quality_status, %s,
					       sum(case when reservation_type = 'RESERVATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as reserved_quantity,
					       sum(case when reservation_type = 'OCCUPATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as occupied_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					group by warehouse_id, quality_status, %s
				) r on r.warehouse_id = sb.warehouse_id and r.quality_status = sb.quality_status
					and r.%s = sb.%s
				where sb.%s = ?
				group by sb.warehouse_id, w.name, sb.quality_status
				order by w.name asc, sb.quality_status asc
				""".formatted(trackingColumn, trackingColumn, trackingColumn, trackingColumn, trackingColumn),
				(rs, rowNum) -> new InventoryTraceBalanceResponse(nullableLong(rs, "warehouse_id"),
						rs.getString("warehouse_name"), rs.getString("quality_status"),
						qualityStatusName(rs.getString("quality_status")), rs.getBigDecimal("quantity_on_hand"),
						rs.getBigDecimal("available_quantity"), rs.getBigDecimal("reserved_quantity"),
						rs.getBigDecimal("occupied_quantity")),
				trackingId);
	}

	private List<InventoryTraceNodeResponse> traceReservationNodes(String trackingColumn, Long trackingId) {
		return this.jdbcTemplate.query("""
				select r.reservation_type, r.source_type, r.source_id, r.source_line_id, r.source_document_no,
				       r.business_date, r.quantity - r.released_quantity - r.consumed_quantity as quantity,
				       r.quality_status, w.name as warehouse_name, r.created_by
				from inv_stock_reservation r
				left join mst_warehouse w on w.id = r.warehouse_id
				where r.status = 'ACTIVE'
				and r.%s = ?
				order by r.created_at asc, r.id asc
				""".formatted(trackingColumn), (rs, rowNum) -> {
			InventoryReservationType reservationType = InventoryReservationType.valueOf(rs.getString("reservation_type"));
			String qualityStatus = rs.getString("quality_status");
			String sourceType = rs.getString("source_type");
			Long sourceId = nullableLong(rs, "source_id");
			Long sourceLineId = nullableLong(rs, "source_line_id");
			String sourceDocumentNo = resolveSourceDocumentNo(sourceType, sourceId, rs.getString("source_document_no"));
			return new InventoryTraceNodeResponse("RESERVATION", reservationType.displayName(),
					sourceType, sourceId, sourceDocumentNo, sourceLineId, rs.getObject("business_date", LocalDate.class),
					null, rs.getBigDecimal("quantity"), qualityStatus, qualityStatusName(qualityStatus),
					rs.getString("warehouse_name"), rs.getString("created_by"), reservationType.name(), false);
		}, trackingId);
	}

	private List<InventoryTraceNodeResponse> traceMovementNodes(String trackingColumn, Long trackingId) {
		return this.jdbcTemplate.query("""
				select mv.movement_type, mv.direction, mv.source_type, mv.source_id, mv.source_line_id,
				       d.document_no as source_document_no, mv.business_date, mv.quantity, mv.quality_status,
				       w.name as warehouse_name, mv.operator_name, mv.reason
				from inv_stock_movement mv
				left join mst_warehouse w on w.id = mv.warehouse_id
				left join inv_inventory_document d on d.id = mv.source_id and mv.source_type = 'INVENTORY_DOCUMENT'
				where mv.%s = ?
				order by mv.occurred_at asc, mv.id asc
				""".formatted(trackingColumn), (rs, rowNum) -> {
			String movementType = rs.getString("movement_type");
			String qualityStatus = rs.getString("quality_status");
			String sourceType = rs.getString("source_type");
			Long sourceId = nullableLong(rs, "source_id");
			Long sourceLineId = nullableLong(rs, "source_line_id");
			String sourceDocumentNo = resolveSourceDocumentNo(sourceType, sourceId, rs.getString("source_document_no"));
			return new InventoryTraceNodeResponse("MOVEMENT", movementTypeName(movementType),
					sourceType, sourceId, sourceDocumentNo, sourceLineId,
					rs.getObject("business_date", LocalDate.class), rs.getString("direction"),
					rs.getBigDecimal("quantity"), qualityStatus,
					qualityStatusName(qualityStatus), rs.getString("warehouse_name"), rs.getString("operator_name"),
					rs.getString("reason"), false);
		}, trackingId);
	}

	private List<InventoryTraceNodeResponse> sourceRecord(InventoryTraceSubjectResponse subject) {
		return List.of(new InventoryTraceNodeResponse("SOURCE", "来源", subject.sourceType(), subject.sourceId(),
				subject.sourceDocumentNo(), subject.sourceLineId(), subject.businessDate(), null, null, null, null,
				null, null, "追踪主档", false));
	}

	private List<InventoryTraceNodeResponse> qualityEvents(List<InventoryTraceNodeResponse> movements) {
		return movements.stream().filter((movement) -> "QUALITY_STATUS_TRANSFER".equals(movement.nodeTypeName()))
			.toList();
	}

	private List<InventoryTraceNodeResponse> outboundRecords(List<InventoryTraceNodeResponse> movements) {
		return movements.stream()
			.filter((movement) -> "OUT".equals(movement.direction()) && !isReturnMovement(movement.nodeTypeName())
					&& !"QUALITY_STATUS_TRANSFER".equals(movement.nodeTypeName()))
			.toList();
	}

	private List<InventoryTraceNodeResponse> returnRecords(List<InventoryTraceNodeResponse> movements) {
		return movements.stream().filter((movement) -> isReturnMovement(movement.nodeTypeName())).toList();
	}

	private InventoryTraceSubjectResponse maskTraceSubject(InventoryTraceSubjectResponse subject,
			CurrentUser currentUser) {
		if (canViewTraceSource(subject.sourceType(), currentUser)) {
			return subject;
		}
		return new InventoryTraceSubjectResponse(subject.trackingMethod(), subject.trackingMethodName(),
				subject.batchId(), subject.batchNo(), subject.serialId(), subject.serialNo(), subject.materialId(),
				subject.materialCode(), subject.materialName(), subject.sourceType(), null, null, null,
				subject.businessDate());
	}

	private List<InventoryTraceNodeResponse> maskTraceNodes(List<InventoryTraceNodeResponse> nodes,
			CurrentUser currentUser) {
		return nodes.stream().map((node) -> maskTraceNode(node, currentUser)).toList();
	}

	private InventoryTraceNodeResponse maskTraceNode(InventoryTraceNodeResponse node, CurrentUser currentUser) {
		if (canViewTraceSource(node.documentType(), currentUser)) {
			return node;
		}
		return new InventoryTraceNodeResponse(node.nodeType(), node.nodeTypeName(), node.documentType(), null, null,
				null, null, node.direction(), node.quantity(), node.qualityStatus(), node.qualityStatusName(),
				node.warehouseName(), null, node.routeName(), true);
	}

	private List<InventoryTraceNodeResponse> restrictedSources(List<InventoryTraceNodeResponse> sourceRecords,
			List<InventoryTraceNodeResponse> activeReservations, List<InventoryTraceNodeResponse> movements) {
		List<InventoryTraceNodeResponse> restricted = new ArrayList<>();
		sourceRecords.stream().filter(InventoryTraceNodeResponse::permissionRestricted).forEach(restricted::add);
		activeReservations.stream().filter(InventoryTraceNodeResponse::permissionRestricted).forEach(restricted::add);
		movements.stream().filter(InventoryTraceNodeResponse::permissionRestricted).forEach(restricted::add);
		return List.copyOf(restricted);
	}

	private boolean canViewTraceSource(String sourceType, CurrentUser currentUser) {
		if (!hasText(sourceType) || currentUser == null) {
			return true;
		}
		String permission = switch (sourceType) {
			case "INVENTORY_DOCUMENT" -> "inventory:document:view";
			case "PURCHASE_RECEIPT" -> "procurement:receipt:view";
			case "PURCHASE_RETURN" -> "procurement:return:view";
			case "SALES_ORDER" -> "sales:order:view";
			case "SALES_SHIPMENT" -> "sales:shipment:view";
			case "SALES_RETURN" -> "sales:return:view";
			case "PRODUCTION_WORK_ORDER" -> "production:work-order:view";
			case "PRODUCTION_COMPLETION_RECEIPT", "PRODUCTION_COMPLETION" -> "production:receipt:view";
			case "PRODUCTION_MATERIAL_ISSUE" -> "production:issue:view";
			case "PRODUCTION_MATERIAL_RETURN" -> "production:material-return:view";
			case "PRODUCTION_MATERIAL_SUPPLEMENT" -> "production:material-supplement:view";
			case "QUALITY_INSPECTION", "QUALITY_STATUS_TRANSFER" -> "quality:inspection:view";
			default -> null;
		};
		return permission != null && currentUser.permissions().contains(permission);
	}

	private boolean isReturnMovement(String movementType) {
		return movementType != null && movementType.contains("RETURN");
	}

	private InventoryReservationResponse mapReservation(ResultSet rs, int rowNum, boolean includeDetail)
			throws SQLException {
		InventoryReservationType reservationType = InventoryReservationType.valueOf(rs.getString("reservation_type"));
		InventoryReservationStatus status = InventoryReservationStatus.valueOf(rs.getString("status"));
		InventoryQualityStatus qualityStatus = InventoryQualityStatus.valueOf(rs.getString("quality_status"));
		BigDecimal quantity = rs.getBigDecimal("quantity");
		BigDecimal releasedQuantity = rs.getBigDecimal("released_quantity");
		BigDecimal consumedQuantity = rs.getBigDecimal("consumed_quantity");
		BigDecimal remainingQuantity = quantity.subtract(releasedQuantity).subtract(consumedQuantity);
		String sourceType = rs.getString("source_type");
		Long sourceId = rs.getLong("source_id");
		Long sourceLineId = rs.getLong("source_line_id");
		String sourceDocumentNo = rs.getString("source_document_no");
		Map<String, Object> sourceSummary = includeDetail
				? sourceSummary(sourceType, sourceId, sourceLineId, sourceDocumentNo) : null;
		List<InventoryReservationAuditRecordResponse> auditRecords = includeDetail
				? reservationAuditRecords(rs.getLong("id")) : null;
		return new InventoryReservationResponse(rs.getLong("id"), rs.getString("reservation_no"),
				reservationType.name(), reservationType.displayName(), status.name(), status.displayName(),
				rs.getLong("warehouse_id"), rs.getString("warehouse_code"), rs.getString("warehouse_name"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				rs.getString("material_spec"), rs.getLong("unit_id"), rs.getString("unit_name"),
				qualityStatus.name(), qualityStatus.displayName(), quantity, releasedQuantity, consumedQuantity,
				remainingQuantity, sourceType, sourceTypeName(sourceType), sourceId, sourceLineId,
				sourceDocumentNo, rs.getString("ownership_type"), nullableLong(rs, "project_id"),
				nullableLong(rs, "cost_layer_id"), sourceSummary, rs.getObject("business_date", LocalDate.class),
				rs.getString("reason"), rs.getString("remark"), auditRecords, rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getString("updated_by"),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("released_by"),
				rs.getObject("released_at", OffsetDateTime.class));
	}

	private Map<String, Object> sourceSummary(String sourceType, Long sourceId, Long sourceLineId,
			String sourceDocumentNo) {
		return Map.of("sourceType", sourceType, "sourceTypeName", sourceTypeName(sourceType), "sourceId", sourceId,
				"sourceLineId", sourceLineId, "sourceDocumentNo", sourceDocumentNo);
	}

	private String sourceTypeName(String sourceType) {
		return switch (sourceType) {
			case "SALES_ORDER" -> "销售订单";
			case "PRODUCTION_WORK_ORDER" -> "生产工单";
			case "INVENTORY_DOCUMENT" -> "库存单据";
			default -> sourceType;
		};
	}

	private List<InventoryReservationAuditRecordResponse> reservationAuditRecords(Long reservationId) {
		return this.jdbcTemplate.query("""
				select id, operator_username, action, target_summary, request_method, request_path, result, error_code,
				       created_at
				from sys_audit_log
				where target_type = 'INVENTORY_RESERVATION'
				and target_id = ?
				order by created_at asc, id asc
				""", (rs, rowNum) -> new InventoryReservationAuditRecordResponse(rs.getLong("id"),
				rs.getString("operator_username"), rs.getString("action"), rs.getString("target_summary"),
				rs.getString("request_method"), rs.getString("request_path"), rs.getString("result"),
				rs.getString("error_code"), rs.getObject("created_at", OffsetDateTime.class)),
				reservationId == null ? null : reservationId.toString());
	}

	private InventoryDocumentSummaryResponse mapDocumentSummary(ResultSet rs, int rowNum) throws SQLException {
		return new InventoryDocumentSummaryResponse(rs.getLong("id"), rs.getString("document_no"),
				rs.getString("document_type"), rs.getString("status"), rs.getObject("business_date", LocalDate.class),
				rs.getString("reason"), rs.getString("remark"), rs.getInt("line_count"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("posted_by"), rs.getObject("posted_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private DocumentRow mapDocumentRow(ResultSet rs, int rowNum) throws SQLException {
		return new DocumentRow(rs.getLong("id"), rs.getString("document_no"),
				InventoryDocumentType.valueOf(rs.getString("document_type")),
				InventoryDocumentStatus.valueOf(rs.getString("status")),
				rs.getObject("business_date", LocalDate.class), rs.getString("reason"), rs.getString("remark"),
				rs.getLong("version"));
	}

	private DocumentLineRow mapDocumentLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new DocumentLineRow(rs.getLong("id"), rs.getInt("line_no"), rs.getLong("warehouse_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				parseNullableAdjustmentDirection(rs.getString("adjustment_direction")),
				rs.getBigDecimal("unit_price"), rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
				rs.getString("remark"));
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

	private InventoryQualityStatus parseNullableQualityStatus(String value) {
		if (!hasText(value)) {
			return null;
		}
		try {
			return InventoryQualityStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUALITY_STATUS_INVALID);
		}
	}

	private InventoryTrackingMethod parseNullableTrackingMethod(String value) {
		if (!hasText(value)) {
			return null;
		}
		try {
			return InventoryTrackingMethod.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private String parseOwnershipType(String value) {
		return switch (value) {
			case "PUBLIC", "PROJECT" -> value;
			default -> throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		};
	}

	private String parseValuationState(String value) {
		return switch (value) {
			case "VALUED", "LEGACY_UNVALUED", "NON_VALUED" -> value;
			default -> throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		};
	}

	private InventoryReservationType parseReservationType(String value) {
		try {
			return InventoryReservationType.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private InventoryReservationStatus parseReservationStatus(String value) {
		try {
			return InventoryReservationStatus.valueOf(value);
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

	private BusinessException trackingNotFound() {
		return new BusinessException(ApiErrorCode.INVENTORY_TRACKING_NOT_FOUND);
	}

	private BusinessException materialInvalid() {
		return new BusinessException(ApiErrorCode.INVENTORY_MATERIAL_INVALID);
	}

	private QueryParts where(List<String> conditions, List<Object> args) {
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args);
	}

	private BigDecimal selectedQuantity(InventoryQualityStatus qualityStatus, BigDecimal totalQuantity,
			BigDecimal pendingInspectionQuantity, BigDecimal qualifiedQuantity, BigDecimal rejectedQuantity,
			BigDecimal frozenQuantity) {
		if (qualityStatus == null) {
			return totalQuantity;
		}
		return switch (qualityStatus) {
			case PENDING_INSPECTION -> pendingInspectionQuantity;
			case QUALIFIED -> qualifiedQuantity;
			case REJECTED -> rejectedQuantity;
			case FROZEN -> frozenQuantity;
		};
	}

	private boolean trackingBreakdown(InventoryTrackingMethod trackingMethod, Long batchId, String batchNo,
			Long serialId, String serialNo) {
		return trackingMethod == InventoryTrackingMethod.BATCH || trackingMethod == InventoryTrackingMethod.SERIAL
				|| batchId != null || hasText(batchNo) || serialId != null || hasText(serialNo);
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private String resolveSourceDocumentNo(String sourceType, Long sourceId, String documentNo) {
		if (hasText(documentNo)) {
			return documentNo;
		}
		return this.sourceDocumentResolver.documentNo(sourceType, sourceId);
	}

	private String batchDisabledReasonCode(String qualityStatus) {
		if (hasText(qualityStatus) && !"QUALIFIED".equals(qualityStatus)) {
			return ApiErrorCode.INVENTORY_TRACKING_NOT_AVAILABLE.code();
		}
		return ApiErrorCode.INVENTORY_TRACKING_STOCK_NOT_ENOUGH.code();
	}

	private String batchDisabledReason(String qualityStatus) {
		if (hasText(qualityStatus) && !"QUALIFIED".equals(qualityStatus)) {
			return "非可用质量状态";
		}
		return ApiErrorCode.INVENTORY_TRACKING_STOCK_NOT_ENOUGH.message();
	}

	private String serialDisabledReasonCode(String stockStatus, String qualityStatus) {
		if (!"IN_STOCK".equals(stockStatus)) {
			return ApiErrorCode.INVENTORY_TRACKING_NOT_AVAILABLE.code();
		}
		if (!"QUALIFIED".equals(qualityStatus)) {
			return ApiErrorCode.INVENTORY_TRACKING_NOT_AVAILABLE.code();
		}
		return ApiErrorCode.INVENTORY_TRACKING_STOCK_NOT_ENOUGH.code();
	}

	private String serialDisabledReason(String stockStatus, String qualityStatus) {
		if (!"IN_STOCK".equals(stockStatus)) {
			return "序列号不在库";
		}
		if (!"QUALIFIED".equals(qualityStatus)) {
			return "非可用质量状态";
		}
		return ApiErrorCode.INVENTORY_TRACKING_STOCK_NOT_ENOUGH.message();
	}

	private String qualityStatusName(String qualityStatus) {
		if (!hasText(qualityStatus)) {
			return null;
		}
		return InventoryQualityStatus.valueOf(qualityStatus).displayName();
	}

	private String stockStatusName(String stockStatus) {
		return switch (stockStatus) {
			case "AVAILABLE" -> "可用";
			case "UNAVAILABLE" -> "不可用";
			case "IN_STOCK" -> "在库";
			case "RESERVED" -> "已预留";
			case "OCCUPIED" -> "已占用";
			case "OUTBOUND" -> "已出库";
			case "CANCELLED" -> "已作废";
			default -> stockStatus;
		};
	}

	private String movementTypeName(String movementType) {
		return movementType;
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

	private static boolean hasPermission(CurrentUser currentUser, String permissionCode) {
		return currentUser != null && currentUser.permissions().contains(permissionCode);
	}

	public record InventoryDocumentLineRequest(@NotNull Integer lineNo, @NotNull Long warehouseId,
			@NotNull Long materialId, Long unitId, @NotNull BigDecimal quantity, String adjustmentDirection,
			BigDecimal unitPrice, String remark) {
	}

	public record InventoryDocumentRequest(@NotBlank String documentType, @NotNull LocalDate businessDate,
			@NotBlank String reason, String remark, @Valid List<InventoryDocumentLineRequest> lines) {
	}

	public record InventoryDocumentPostRequest(Long version) {
	}

	public record InventoryBalanceResponse(Long id, Long warehouseId, String warehouseCode, String warehouseName,
			Long materialId, String materialCode, String materialName, String materialSpec, String materialType,
			String trackingMethod, String trackingMethodName, Long batchId, String batchNo, Long serialId,
			String serialNo, BigDecimal traceableQuantity, Long unitId, String unitName, String qualityStatus,
			String qualityStatusName, BigDecimal quantityOnHand, BigDecimal lockedQuantity,
			BigDecimal availableQuantity, BigDecimal bookQuantity, BigDecimal reservedQuantity,
			BigDecimal occupiedQuantity, BigDecimal inTransitQuantity, BigDecimal availableToPromiseQuantity,
			BigDecimal netRequirementShortageQuantity, BigDecimal totalQuantityOnHand,
			BigDecimal pendingInspectionQuantity, BigDecimal qualifiedQuantity, BigDecimal rejectedQuantity,
			BigDecimal frozenQuantity, String ownershipType, Long projectId, String valuationState,
			boolean costVisible, BigDecimal inventoryAmount, BigDecimal averageUnitCost, Long costLayerCount,
			Long costLayerId, OffsetDateTime updatedAt) {
	}

	public record InventoryMovementResponse(Long id, String movementNo, String movementType, String direction,
			Long warehouseId, String warehouseName, Long materialId, String materialCode, String materialName,
			Long unitId, String unitName, String quantity, String beforeQuantity, String afterQuantity,
			String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String remark,
			String operatorName, OffsetDateTime occurredAt, String qualityStatus, String qualityStatusName,
			String trackingMethod, String trackingMethodName, Long batchId, String batchNo, Long serialId,
			String serialNo, String sourceDocumentNo, String targetDocumentNo, String ownershipType, Long projectId,
			String valuationState, boolean costVisible, String unitCost, String inventoryAmount, String movementAmount,
			String valuationMethod, Long valueFlowId, Long costLayerId, Long originalValueMovementId,
			Long originalValueFlowId) {
	}

	public record InventoryTrackingQualityStatusSummaryResponse(String qualityStatus, String qualityStatusName,
			BigDecimal quantityOnHand, BigDecimal availableQuantity) {
	}

	public record InventoryBatchSummaryResponse(Long id, String batchNo, Long materialId, String materialCode,
			String materialName, String sourceType, Long sourceId, Long sourceLineId, String sourceDocumentNo,
			Long warehouseId, String warehouseName, String qualityStatus, String qualityStatusName,
			String stockStatus, String stockStatusName, LocalDate businessDate, BigDecimal quantityOnHand,
			BigDecimal availableQuantity, boolean selectable, String disabledReasonCode, String disabledReason,
			List<InventoryTrackingQualityStatusSummaryResponse> qualityStatusSummary, OffsetDateTime updatedAt) {
	}

	public record InventoryBatchDetailResponse(Long id, String batchNo, Long materialId, String materialCode,
			String materialName, String sourceType, Long sourceId, Long sourceLineId, String sourceDocumentNo,
			LocalDate businessDate, BigDecimal quantityOnHand, BigDecimal availableQuantity,
			List<InventoryTrackingQualityStatusSummaryResponse> qualityStatusSummary, String remark,
			String createdByName, OffsetDateTime createdAt, String updatedByName, OffsetDateTime updatedAt) {
	}

	public record InventorySerialSummaryResponse(Long id, String serialNo, Long materialId, String materialCode,
			String materialName, Long batchId, String batchNo, Long warehouseId, String warehouseName,
			String qualityStatus, String qualityStatusName, String stockStatus, String stockStatusName,
			BigDecimal availableQuantity, boolean selectable, String disabledReasonCode, String disabledReason,
			String sourceType, Long sourceId, Long sourceLineId, String sourceDocumentNo, OffsetDateTime updatedAt) {
	}

	public record InventorySerialDetailResponse(Long id, String serialNo, Long materialId, String materialCode,
			String materialName, Long batchId, String batchNo, Long warehouseId, String warehouseName,
			String qualityStatus, String qualityStatusName, String stockStatus, String stockStatusName,
			String sourceType, Long sourceId, Long sourceLineId, String sourceDocumentNo, LocalDate businessDate,
			String remark, String createdByName, OffsetDateTime createdAt, String updatedByName,
			OffsetDateTime updatedAt) {
	}

	public record InventoryTraceSubjectResponse(String trackingMethod, String trackingMethodName, Long batchId,
			String batchNo, Long serialId, String serialNo, Long materialId, String materialCode, String materialName,
			String sourceType, Long sourceId, Long sourceLineId, String sourceDocumentNo, LocalDate businessDate) {
	}

	public record InventoryTraceBalanceResponse(Long warehouseId, String warehouseName, String qualityStatus,
			String qualityStatusName, BigDecimal quantityOnHand, BigDecimal availableQuantity,
			BigDecimal reservedQuantity, BigDecimal occupiedQuantity) {
	}

	public record InventoryTraceNodeResponse(String nodeType, String nodeTypeName, String documentType,
			Long documentId, String documentNo, Long lineId, LocalDate businessDate, String direction,
			BigDecimal quantity, String qualityStatus, String qualityStatusName, String warehouseName,
			String operatorName, String routeName, boolean permissionRestricted) {
	}

	public record InventoryTraceDetailResponse(InventoryTraceSubjectResponse subject,
			List<InventoryTraceBalanceResponse> currentBalances, List<InventoryTraceNodeResponse> activeReservations,
			List<InventoryTraceNodeResponse> sourceRecords, List<InventoryTraceNodeResponse> qualityEvents,
			List<InventoryTraceNodeResponse> outboundRecords, List<InventoryTraceNodeResponse> returnRecords,
			List<InventoryTraceNodeResponse> movements, List<InventoryTraceNodeResponse> restrictedSources) {
	}

	public record InventoryReservationResponse(Long id, String reservationNo, String reservationType,
			String reservationTypeName, String status, String statusName, Long warehouseId, String warehouseCode,
			String warehouseName, Long materialId, String materialCode, String materialName, String materialSpec,
			Long unitId, String unitName, String qualityStatus, String qualityStatusName, BigDecimal quantity,
			BigDecimal releasedQuantity, BigDecimal consumedQuantity, BigDecimal remainingQuantity, String sourceType,
			String sourceTypeName, Long sourceId, Long sourceLineId, String sourceDocumentNo,
			String ownershipType, Long projectId, Long costLayerId, Map<String, Object> sourceSummary,
			LocalDate businessDate, String reason, String remark, List<InventoryReservationAuditRecordResponse> auditRecords,
			String createdByName,
			OffsetDateTime createdAt, String updatedByName, OffsetDateTime updatedAt, String releasedByName,
			OffsetDateTime releasedAt) {
	}

	public record InventoryReservationAuditRecordResponse(Long id, String operatorUsername, String action,
			String targetSummary, String requestMethod, String requestPath, String result, String errorCode,
			OffsetDateTime createdAt) {
	}

	public record InventoryDocumentSummaryResponse(Long id, String documentNo, String documentType, String status,
			LocalDate businessDate, String reason, String remark, int lineCount, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			Long version) {
	}

	public record InventoryDocumentLineResponse(Long id, Integer lineNo, Long warehouseId, String warehouseName,
			Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			BigDecimal quantity, String adjustmentDirection, BigDecimal unitPrice, BigDecimal beforeQuantity,
			BigDecimal afterQuantity, String remark) {
	}

	public record InventoryDocumentDetailResponse(Long id, String documentNo, String documentType, String status,
			LocalDate businessDate, String reason, String remark, int lineCount, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			Long version, List<InventoryDocumentLineResponse> lines) {
	}

	private record ValidatedDocument(InventoryDocumentType documentType, LocalDate businessDate, String reason,
			String remark, List<ValidatedLine> lines) {
	}

	private record ValidatedLine(Integer lineNo, Long warehouseId, Long materialId, Long unitId, BigDecimal quantity,
			InventoryAdjustmentDirection adjustmentDirection, String remark, BigDecimal unitPrice) {
	}

	private record DocumentRow(Long id, String documentNo, InventoryDocumentType documentType,
			InventoryDocumentStatus status, LocalDate businessDate, String reason, String remark, Long version) {
	}

	private record DocumentLineRow(Long id, Integer lineNo, Long warehouseId, Long materialId, Long unitId,
			BigDecimal quantity, InventoryAdjustmentDirection adjustmentDirection, BigDecimal unitPrice,
			BigDecimal beforeQuantity, BigDecimal afterQuantity, String remark) {
	}

	private record CreatedDocument(Long id, String documentNo) {
	}

	private record MaterialRef(Long id, String code, String name, Long unitId, String status) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

	private record HavingParts(String having, List<Object> args) {
	}

}
