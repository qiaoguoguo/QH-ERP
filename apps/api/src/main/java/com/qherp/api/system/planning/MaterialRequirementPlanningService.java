package com.qherp.api.system.planning;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.procurement.ProcurementRequisitionService;
import com.qherp.api.system.production.ProductionPlanningConversionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MaterialRequirementPlanningService {

	private static final String TARGET_RUN = "MATERIAL_REQUIREMENT_RUN";

	private static final String TARGET_SUGGESTION = "MATERIAL_REQUIREMENT_SUGGESTION";

	private static final DateTimeFormatter RUN_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger RUN_SEQUENCE = new AtomicInteger();

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);

	private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 20, 50, 100);

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final ProcurementRequisitionService requisitionService;

	private final ProductionPlanningConversionService productionPlanningConversionService;

	private final ObjectMapper objectMapper;

	private final TransactionTemplate transactionTemplate;

	public MaterialRequirementPlanningService(JdbcTemplate jdbcTemplate, AuditService auditService,
			ProcurementRequisitionService requisitionService, ObjectMapper objectMapper,
			TransactionTemplate transactionTemplate,
			ProductionPlanningConversionService productionPlanningConversionService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.requisitionService = requisitionService;
		this.productionPlanningConversionService = productionPlanningConversionService;
		this.objectMapper = objectMapper;
		this.transactionTemplate = transactionTemplate;
	}

	public RunResponse calculate(RunRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		CalculationOutcome outcome = Objects.requireNonNull(this.transactionTemplate
			.execute((status) -> calculateInTransaction(request, operator, servletRequest, false, null)));
		return completeCalculation(outcome);
	}

	private CalculationOutcome calculateInTransaction(RunRequest request, CurrentUser operator,
			HttpServletRequest servletRequest,
			boolean forceNewRun, Long previousRunId) {
		NormalizedScope scope = normalize(request);
		Optional<CalculationOutcome> existing = existingIdempotentOutcome(operator, scope);
		if (existing.isPresent()) {
			return existing.get();
		}

		this.jdbcTemplate.query("select pg_advisory_xact_lock(hashtext(?))", (rs) -> {
		}, scope.scopeHash());
		existing = existingIdempotentOutcome(operator, scope);
		if (existing.isPresent()) {
			return existing.get();
		}

		CalculationResult calculation;
		try {
			calculation = buildCalculation(scope);
		}
		catch (PlanningFailureException exception) {
			OffsetDateTime now = OffsetDateTime.now();
			Long runId = insertRun(scope, operator, now, previousRunId);
			markRunFailed(runId, exception.failure(), operator, now, servletRequest, scope.scopeHash());
			return CalculationOutcome.failed(runId, exception.failure());
		}

		Optional<Long> reusableRunId = forceNewRun ? Optional.empty()
				: reusableRun(scope.scopeHash(), calculation.sourceFingerprint());
		if (!forceNewRun && reusableRunId.isPresent()) {
			return CalculationOutcome.success(reusableRunId.get());
		}

		OffsetDateTime now = OffsetDateTime.now();
		Long runId = insertRun(scope, operator, now, previousRunId);
		try {
			persistCalculation(runId, calculation, operator.username());
			this.jdbcTemplate.update("""
					update mrp_calculation_run
					set status = 'COMPLETED', source_fingerprint = ?, source_snapshot = cast(? as jsonb),
					    calculated_at = ?, expires_at = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", calculation.sourceFingerprint(), json(calculation.snapshot()), now, now.plusMinutes(30),
					operator.username(), now, runId);
			this.auditService.record(operator, "MATERIAL_REQUIREMENT_RUN_CALCULATE", TARGET_RUN, runId,
					scope.scopeHash(), servletRequest);
			return CalculationOutcome.success(runId);
		}
		catch (RuntimeException exception) {
			this.jdbcTemplate.update("""
					update mrp_calculation_run
					set status = 'FAILED', status_reason = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", safeError(exception), operator.username(), OffsetDateTime.now(), runId);
			this.auditService.recordFailure(operator, "MATERIAL_REQUIREMENT_RUN_CALCULATE", TARGET_RUN, runId,
					scope.scopeHash(), ApiErrorCode.SYSTEM_ERROR.code(), servletRequest);
			throw exception;
		}
	}

	private RunResponse completeCalculation(CalculationOutcome outcome) {
		if (outcome.failure() != null) {
			throw planningBusinessException(outcome.failure());
		}
		return detail(outcome.runId());
	}

	private Optional<CalculationOutcome> existingIdempotentOutcome(CurrentUser operator, NormalizedScope scope) {
		Optional<ExistingRun> existing = existingIdempotentRun(operator.id(), scope.idempotencyKey());
		if (existing.isEmpty()) {
			return Optional.empty();
		}
		if (!existing.get().requestFingerprint().equals(scope.requestFingerprint())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_RUN_CONFLICT);
		}
		RunRow row = run(existing.get().id()).orElseThrow(this::notFound);
		if ("FAILED".equals(row.status())) {
			return Optional.of(CalculationOutcome.failed(row.id(), failureFromRun(row)));
		}
		return Optional.of(CalculationOutcome.success(row.id()));
	}

	private BusinessException planningBusinessException(PlanningFailure failure) {
		return new BusinessException(failure.errorCode(), failure.summary(), HttpStatus.BAD_REQUEST);
	}

	private PlanningFailure failureFromRun(RunRow row) {
		ApiErrorCode errorCode = apiErrorCode(row.failureCode());
		String summary = hasText(row.failureSummary()) ? row.failureSummary()
				: hasText(row.statusReason()) ? row.statusReason() : errorCode.message();
		return new PlanningFailure(errorCode, summary);
	}

	private ApiErrorCode apiErrorCode(String code) {
		if (hasText(code)) {
			try {
				return ApiErrorCode.valueOf(code);
			}
			catch (IllegalArgumentException ignored) {
				return ApiErrorCode.VALIDATION_ERROR;
			}
		}
		return ApiErrorCode.VALIDATION_ERROR;
	}

	@Transactional(readOnly = true)
	public PageResponse<RunResponse> list(RunListFilter filter, int page, int pageSize) {
		validatePagination(page, pageSize);
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		if (filter != null) {
			if (filter.projectId() != null) {
				conditions.add("r.project_id = ?");
				args.add(filter.projectId());
			}
			if (filter.customerId() != null) {
				conditions.add("""
						(r.customer_id = ? or exists (
							select 1
							from mrp_requirement_line rl
							join sal_sales_order so on so.id = rl.demand_source_id
							where rl.run_id = r.id
							and rl.demand_source_type = 'SALES_ORDER'
							and so.customer_id = ?
						))
						""");
				args.add(filter.customerId());
				args.add(filter.customerId());
			}
			if (filter.contractId() != null) {
				conditions.add("""
						(r.contract_id = ? or exists (
							select 1
							from mrp_requirement_line rl
							join sal_sales_order so on so.id = rl.demand_source_id
							where rl.run_id = r.id
							and rl.demand_source_type = 'SALES_ORDER'
							and so.contract_id = ?
						))
						""");
				args.add(filter.contractId());
				args.add(filter.contractId());
			}
			if (filter.orderId() != null) {
				conditions.add("""
						(r.sales_order_id = ? or exists (
							select 1
							from mrp_requirement_line rl
							where rl.run_id = r.id
							and rl.demand_source_type = 'SALES_ORDER'
							and rl.demand_source_id = ?
						))
						""");
				args.add(filter.orderId());
				args.add(filter.orderId());
			}
			if (filter.materialId() != null) {
				conditions.add("""
						(r.material_id = ? or exists (
							select 1
							from mrp_requirement_line rl
							where rl.run_id = r.id
							and rl.material_id = ?
						))
						""");
				args.add(filter.materialId());
				args.add(filter.materialId());
			}
			if (filter.requiredDateTo() != null) {
				conditions.add("r.demand_date_to = ?");
				args.add(filter.requiredDateTo());
			}
			if (hasText(filter.status())) {
				conditions.add("r.status = ?");
				args.add(filter.status().trim().toUpperCase());
			}
			if (Boolean.TRUE.equals(filter.expired())) {
				conditions.add("(r.status = 'EXPIRED' or r.expires_at <= now())");
			}
			else if (Boolean.FALSE.equals(filter.expired())) {
				conditions.add("not (r.status = 'EXPIRED' or r.expires_at <= now())");
			}
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from mrp_calculation_run r " + where,
				Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<RunResponse> items = this.jdbcTemplate.query("""
				select r.*
				from mrp_calculation_run r
				%s
				order by r.created_at desc, r.id desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> runResponse(mapRunRow(rs, rowNum)), pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional
	public RunResponse detail(Long id) {
		return detail(id, null);
	}

	@Transactional
	public RunResponse detail(Long id, CurrentUser currentUser) {
		RunRow row = run(id).orElseThrow(this::notFound);
		refreshRunState(row);
		return run(id).map(this::runResponse).orElseThrow(this::notFound);
	}

	public RunResponse recalculate(Long id, RunRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		RunRow row = Objects.requireNonNull(this.transactionTemplate.execute((status) -> run(id).orElseThrow(this::notFound)));
		RunRequest effectiveRequest = recalculateRequest(row, request);
		CalculationOutcome outcome = Objects.requireNonNull(this.transactionTemplate
			.execute((status) -> calculateInTransaction(effectiveRequest, operator, servletRequest, true, id)));
		return completeCalculation(outcome);
	}

	@Transactional(readOnly = true)
	public PageResponse<RequirementLineResponse> requirements(Long runId, RequirementLineFilter filter, int page,
			int pageSize, CurrentUser currentUser) {
		validatePagination(page, pageSize);
		ensureRunExists(runId);
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		conditions.add("rl.run_id = ?");
		args.add(runId);
		if (filter != null) {
			if (filter.materialId() != null) {
				conditions.add("rl.material_id = ?");
				args.add(filter.materialId());
			}
			if (Boolean.TRUE.equals(filter.shortageOnly())) {
				conditions.add("rl.shortage_quantity > 0");
			}
			if (hasText(filter.coverageStatus())) {
				conditions.add(coverageStatusExpression("rl") + " = ?");
				args.add(filter.coverageStatus().trim().toUpperCase());
			}
		}
		String where = "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from mrp_requirement_line rl " + where,
				Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		String sql = """
				select rl.*, m.code as material_code, m.name as material_name, u.name as unit_name,
				       so.order_no, p.project_no, p.name as project_name,
				       fm.code as finished_material_code, fm.name as finished_material_name,
				       coalesce(nullif(rl.source_snapshot ->> 'bomVersionNo', ''),
				                case when rl.bom_path like '%BOM:%' then substring(rl.bom_path from 'BOM:([^:>]+):') end) as bom_version_no,
				       (select max(a.supply_date) from mrp_supply_allocation a
				        where a.requirement_line_id = rl.id and a.allocated_quantity > 0 and a.on_time = true) as estimated_available_date,
				       {coverage_status} as coverage_status,
				       (select s.suggestion_type from mrp_suggestion s
				        where s.requirement_line_id = rl.id order by s.id asc limit 1) as suggestion_type,
				       (select a.reason from mrp_supply_allocation a
				        where a.requirement_line_id = rl.id and coalesce(a.reason, '') not in ('', 'ALLOCATED', 'SUPPLY_LATE')
				        order by a.allocation_rank asc, a.id asc limit 1) as exception_reason_code
				from mrp_requirement_line rl
				join mst_material m on m.id = rl.material_id
				join mst_unit u on u.id = rl.unit_id
				left join sal_sales_order so on so.id = rl.demand_source_id
				left join sal_sales_delivery_plan dp on dp.id = rl.delivery_plan_id
				left join sal_sales_order_line sol on sol.id = coalesce(dp.order_line_id, rl.demand_source_line_id)
				left join mst_material fm on fm.id = sol.material_id
				left join sal_project p on p.id = rl.project_id
				{where_clause}
				order by rl.line_no asc, rl.id asc
				limit ? offset ?
				""".replace("{coverage_status}", coverageStatusExpression("rl")).replace("{where_clause}", where);
		List<RequirementLineResponse> items = this.jdbcTemplate.query(sql,
				(rs, rowNum) -> mapRequirement(rs, rowNum, currentUser), pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public PageResponse<SupplyAllocationResponse> allocations(Long runId, AllocationFilter filter, int page,
			int pageSize, CurrentUser currentUser) {
		validatePagination(page, pageSize);
		ensureRunExists(runId);
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		conditions.add("a.run_id = ?");
		args.add(runId);
		if (filter != null && filter.requirementLineId() != null) {
			conditions.add("a.requirement_line_id = ?");
			args.add(filter.requirementLineId());
		}
		String where = "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from mrp_supply_allocation a " + where,
				Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<SupplyAllocationResponse> items = this.jdbcTemplate.query("""
				select a.*,
				       p.project_no,
				       coalesce(stock_wh.name, wo_wh.name) as warehouse_name,
				       case a.supply_type
				       when 'PROJECT_STOCK' then '项目库存'
				       when 'PUBLIC_STOCK' then '公共库存'
				       when 'PROJECT_PURCHASE' then '项目采购供给'
				       when 'PUBLIC_PURCHASE' then '公共采购供给'
				       when 'WORK_ORDER' then '工单供给'
				       else a.supply_type
				       end as supply_type_name,
				       case
				       when a.source_table = 'inv_stock_balance' then stock_wh.code
				       when a.source_table = 'proc_purchase_order' then po.order_no
				       when a.source_table = 'mfg_work_order' then wo.work_order_no
				       end as source_no
				from mrp_supply_allocation a
				left join sal_project p on p.id = a.project_id
				left join inv_stock_balance sb on sb.id = a.source_id and a.source_table = 'inv_stock_balance'
				left join mst_warehouse stock_wh on stock_wh.id = sb.warehouse_id
				left join proc_purchase_order po on po.id = a.source_id and a.source_table = 'proc_purchase_order'
				left join mfg_work_order wo on wo.id = a.source_id and a.source_table = 'mfg_work_order'
				left join mst_warehouse wo_wh on wo_wh.id = wo.receipt_warehouse_id
				%s
				order by a.requirement_line_id asc, a.allocation_rank asc, a.id asc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> mapAllocation(rs, rowNum, currentUser), pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public PageResponse<SuggestionResponse> suggestions(Long runId, SuggestionFilter filter, int page, int pageSize,
			CurrentUser currentUser) {
		validatePagination(page, pageSize);
		ensureRunExists(runId);
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		conditions.add("s.run_id = ?");
		args.add(runId);
		if (filter != null) {
			if (hasText(filter.status())) {
				conditions.add("s.status = ?");
				args.add(filter.status().trim().toUpperCase());
			}
			if (hasText(filter.suggestionType())) {
				conditions.add("s.suggestion_type = ?");
				args.add(filter.suggestionType().trim().toUpperCase());
			}
		}
		String where = "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from mrp_suggestion s " + where,
				Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<SuggestionResponse> items = this.jdbcTemplate.query("""
				select s.*, m.code as material_code, m.name as material_name, u.name as unit_name,
				       p.project_no, p.name as project_name
				from mrp_suggestion s
				join mst_material m on m.id = s.material_id
				join mst_unit u on u.id = s.unit_id
				left join sal_project p on p.id = s.project_id
				%s
				order by s.id asc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> mapSuggestion(rs, rowNum, currentUser), pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public PageResponse<SubstituteHintResponse> substituteHints(Long runId, SubstituteHintFilter filter, int page,
			int pageSize, CurrentUser currentUser) {
		validatePagination(page, pageSize);
		ensureRunExists(runId);
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		conditions.add("h.run_id = ?");
		args.add(runId);
		if (filter != null && filter.requirementLineId() != null) {
			conditions.add("h.requirement_line_id = ?");
			args.add(filter.requirementLineId());
		}
		String where = "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from mrp_substitute_hint h " + where,
				Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<SubstituteHintResponse> items = this.jdbcTemplate.query("""
				select h.*, sm.code as substitute_material_code, sm.name as substitute_material_name
				from mrp_substitute_hint h
				join mst_material sm on sm.id = h.substitute_material_id
				%s
				order by h.requirement_line_id asc, h.priority asc, h.id asc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> mapSubstituteHint(rs, rowNum, currentUser), pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional
	public SuggestionResponse confirmSuggestion(Long id, SuggestionActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		SuggestionActionRequest actionRequest = requireActionRequest(request);
		SuggestionRow row = lockSuggestion(id).orElseThrow(this::suggestionNotFound);
		requireRunWritable(row.runId());
		String fingerprint = actionFingerprint("CONFIRM", id, actionRequest);
		Optional<ActionRecord> existing = existingAction("CONFIRM", id, actionRequest.idempotencyKey(), operator);
		if (existing.isPresent()) {
			requireSameFingerprint(existing.get(), fingerprint);
			return suggestion(existing.get().resultResourceId());
		}
		requireVersion(row.version(), actionRequest.version());
		if (isReadOnlySuggestion(row.suggestionType())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_SUGGESTION_STATE_CONFLICT);
		}
		if (!"OPEN".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_SUGGESTION_STATE_CONFLICT);
		}
		this.jdbcTemplate.update("""
				update mrp_suggestion
				set status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
		SuggestionResponse result = suggestion(id);
		recordAction("CONFIRM", id, actionRequest.idempotencyKey(), fingerprint, TARGET_SUGGESTION, id,
				result.version(), operator);
		this.auditService.record(operator, "MATERIAL_REQUIREMENT_SUGGESTION_CONFIRM", TARGET_SUGGESTION, id,
				Long.toString(row.runId()), servletRequest);
		return result;
	}

	@Transactional
	public SuggestionResponse dismissSuggestion(Long id, SuggestionActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		SuggestionActionRequest actionRequest = requireActionRequest(request);
		SuggestionRow row = lockSuggestion(id).orElseThrow(this::suggestionNotFound);
		requireRunWritable(row.runId());
		String fingerprint = actionFingerprint("DISMISS", id, actionRequest);
		Optional<ActionRecord> existing = existingAction("DISMISS", id, actionRequest.idempotencyKey(), operator);
		if (existing.isPresent()) {
			requireSameFingerprint(existing.get(), fingerprint);
			return suggestion(existing.get().resultResourceId());
		}
		requireVersion(row.version(), actionRequest.version());
		if (isReadOnlySuggestion(row.suggestionType())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_SUGGESTION_STATE_CONFLICT);
		}
		if (!"OPEN".equals(row.status()) && !"CONFIRMED".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_SUGGESTION_STATE_CONFLICT);
		}
		this.jdbcTemplate.update("""
				update mrp_suggestion
				set status = 'DISMISSED', dismissed_by = ?, dismissed_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
		SuggestionResponse result = suggestion(id);
		recordAction("DISMISS", id, actionRequest.idempotencyKey(), fingerprint, TARGET_SUGGESTION, id,
				result.version(), operator);
		this.auditService.record(operator, "MATERIAL_REQUIREMENT_SUGGESTION_DISMISS", TARGET_SUGGESTION, id,
				Long.toString(row.runId()), servletRequest);
		return result;
	}

	@Transactional
	public SuggestionResponse convertToRequisition(Long id, SuggestionActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		requirePermission(operator, "procurement:requisition:create");
		SuggestionActionRequest actionRequest = requireActionRequest(request);
		SuggestionRow row = lockSuggestion(id).orElseThrow(this::suggestionNotFound);
		requireRunWritable(row.runId());
		String fingerprint = actionFingerprint("CONVERT_REQUISITION", id, actionRequest);
		Optional<ActionRecord> existing = existingAction("CONVERT_REQUISITION", id, actionRequest.idempotencyKey(),
				operator);
		if (existing.isPresent()) {
			requireSameFingerprint(existing.get(), fingerprint);
			return suggestion(id);
		}
		requireVersion(row.version(), actionRequest.version());
		if (!"CONFIRMED".equals(row.status()) || !"PURCHASE_REQUISITION".equals(row.suggestionType())
				|| !row.conversionAllowed()) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_SUGGESTION_STATE_CONFLICT);
		}
		if (row.targetObjectId() != null) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_REQUISITION_ALREADY_CONVERTED);
		}

		String purchaseMode = "PROJECT".equals(row.ownershipType()) ? "PROJECT" : "PUBLIC";
		ProcurementRequisitionService.PurchaseRequisitionLineRequest lineRequest =
				new ProcurementRequisitionService.PurchaseRequisitionLineRequest(1, row.materialId(), row.unitId(),
						row.suggestedQuantity(), row.suggestedQuantity(), purchaseMode, purchaseMode, row.projectId(),
						row.requiredDate(), "订单缺料建议", null, null);
		ProcurementRequisitionService.PurchaseRequisitionRequest requisitionRequest =
				new ProcurementRequisitionService.PurchaseRequisitionRequest(purchaseMode, purchaseMode, purchaseMode,
						row.projectId(), "订单缺料建议", row.requiredDate(), "订单缺料建议", null, null, null,
						"MRP-SUG-" + id, null, List.of(lineRequest));
		ProcurementRequisitionService.PurchaseRequisitionDetailResponse requisition =
				this.requisitionService.create(requisitionRequest, operator, servletRequest);
		Long requisitionLineId = this.jdbcTemplate.queryForObject("""
				select id
				from proc_purchase_requisition_line
				where requisition_id = ?
				order by line_no asc, id asc
				limit 1
				""", Long.class, requisition.id());
		this.jdbcTemplate.update("""
				update proc_purchase_requisition
				set source_mrp_run_id = ?, source_mrp_suggestion_id = ?
				where id = ?
				""", row.runId(), id, requisition.id());
		this.jdbcTemplate.update("""
				update proc_purchase_requisition_line
				set source_mrp_run_id = ?, source_mrp_requirement_line_id = ?, source_mrp_suggestion_id = ?
				where id = ?
				""", row.runId(), row.requirementLineId(), id, requisitionLineId);
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update mrp_suggestion
				set status = 'CONVERTED', target_object_type = 'PROCUREMENT_REQUISITION',
				    target_object_id = ?, target_object_line_id = ?, target_object_no = ?, converted_by = ?,
				    converted_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				returning version
				""", Long.class, requisition.id(), requisitionLineId, requisition.requisitionNo(), operator.username(),
				OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
		recordAction("CONVERT_REQUISITION", id, actionRequest.idempotencyKey(), fingerprint,
				"PROCUREMENT_REQUISITION", requisition.id(), newVersion, operator);
		this.auditService.record(operator, "MATERIAL_REQUIREMENT_SUGGESTION_CONVERT_REQUISITION", TARGET_SUGGESTION,
				id, requisition.requisitionNo(), servletRequest);
		return suggestion(id);
	}

	@Transactional
	public SuggestionConversionResponse convertToWorkOrder(Long id, SuggestionActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requirePermission(operator, "planning:material-requirement:convert-production");
		requirePermission(operator, "production:work-order:create");
		SuggestionActionRequest actionRequest = requireActionRequest(request);
		SuggestionRow row = lockSuggestion(id).orElseThrow(this::suggestionNotFound);
		String fingerprint = actionFingerprint("CONVERT_WORK_ORDER", id, actionRequest);
		Optional<ActionRecord> existing = existingAction("CONVERT_WORK_ORDER", id, actionRequest.idempotencyKey(),
				operator);
		if (existing.isPresent()) {
			requireSameFingerprint(existing.get(), fingerprint);
			return suggestionConversion(id);
		}
		requireRunWritable(row.runId());
		requireVersion(row.version(), actionRequest.version());
		requireProductionConversionTarget(row, "SELF_MADE");
		ProductionPlanningConversionService.ConvertedProductionTarget target =
				this.productionPlanningConversionService.createDraftWorkOrder(planningSuggestion(row),
						operator.username(), OffsetDateTime.now());
		Long newVersion = markSuggestionConverted(id, row, target, operator);
		recordAction("CONVERT_WORK_ORDER", id, actionRequest.idempotencyKey(), fingerprint, target.targetObjectType(),
				target.targetObjectId(), newVersion, operator);
		this.auditService.record(operator, "MATERIAL_REQUIREMENT_SUGGESTION_CONVERT_WORK_ORDER", TARGET_SUGGESTION,
				id, target.targetObjectNo(), servletRequest);
		return suggestionConversion(id);
	}

	@Transactional
	public SuggestionConversionResponse convertToOutsourcingOrder(Long id, SuggestionActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		requirePermission(operator, "planning:material-requirement:convert-outsourcing");
		requirePermission(operator, "production:outsourcing:create");
		SuggestionActionRequest actionRequest = requireActionRequest(request);
		SuggestionRow row = lockSuggestion(id).orElseThrow(this::suggestionNotFound);
		String fingerprint = actionFingerprint("CONVERT_OUTSOURCING_ORDER", id, actionRequest);
		Optional<ActionRecord> existing = existingAction("CONVERT_OUTSOURCING_ORDER", id,
				actionRequest.idempotencyKey(), operator);
		if (existing.isPresent()) {
			requireSameFingerprint(existing.get(), fingerprint);
			return suggestionConversion(id);
		}
		requireRunWritable(row.runId());
		requireVersion(row.version(), actionRequest.version());
		requireProductionConversionTarget(row, "OUTSOURCED");
		ProductionPlanningConversionService.ConvertedProductionTarget target =
				this.productionPlanningConversionService.createDraftOutsourcingOrder(planningSuggestion(row),
						operator.username(), OffsetDateTime.now());
		Long newVersion = markSuggestionConverted(id, row, target, operator);
		recordAction("CONVERT_OUTSOURCING_ORDER", id, actionRequest.idempotencyKey(), fingerprint,
				target.targetObjectType(), target.targetObjectId(), newVersion, operator);
		this.auditService.record(operator, "MATERIAL_REQUIREMENT_SUGGESTION_CONVERT_OUTSOURCING", TARGET_SUGGESTION,
				id, target.targetObjectNo(), servletRequest);
		return suggestionConversion(id);
	}

	private SuggestionConversionResponse suggestionConversion(Long suggestionId) {
		return this.jdbcTemplate.query("""
				select id, status, target_object_type, target_object_id, target_object_no, version
				from mrp_suggestion
				where id = ?
				""", (rs, rowNum) -> {
			String storedType = rs.getString("target_object_type");
			Long targetObjectId = nullableLong(rs, "target_object_id");
			String targetObjectType = "PRODUCTION_WORK_ORDER".equals(storedType) ? "WORK_ORDER" : storedType;
			String targetRoute = switch (targetObjectType) {
				case "WORK_ORDER" -> "/production/work-orders/" + targetObjectId;
				case "OUTSOURCING_ORDER" -> "/production/outsourcing-orders/" + targetObjectId;
				default -> null;
			};
			return new SuggestionConversionResponse(rs.getLong("id"), rs.getString("status"), targetObjectType,
					targetObjectId, rs.getString("target_object_no"), targetRoute, rs.getLong("version"));
		}, suggestionId).stream().findFirst().orElseThrow(this::suggestionNotFound);
	}

	private void requireProductionConversionTarget(SuggestionRow row, String requiredSourceType) {
		if (!"CONFIRMED".equals(row.status()) || !"PRODUCTION_ORDER".equals(row.suggestionType())
				|| !requiredSourceType.equals(row.materialSourceType())) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_PLANNING_SUGGESTION_INVALID);
		}
		if (row.targetObjectId() != null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_PLANNING_SUGGESTION_ALREADY_CONVERTED);
		}
		if ("PROJECT".equals(row.ownershipType()) && row.projectId() == null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_PROJECT_REQUIRED);
		}
	}

	private ProductionPlanningConversionService.PlanningProductionSuggestion planningSuggestion(SuggestionRow row) {
		return new ProductionPlanningConversionService.PlanningProductionSuggestion(row.id(), row.runId(),
				row.requirementLineId(), row.materialId(), row.unitId(), row.suggestedQuantity(), row.ownershipType(),
				row.projectId(), row.requiredDate(), "MRP-SUG-" + row.id());
	}

	private Long markSuggestionConverted(Long id, SuggestionRow row,
			ProductionPlanningConversionService.ConvertedProductionTarget target, CurrentUser operator) {
		return this.jdbcTemplate.queryForObject("""
				update mrp_suggestion
				set status = 'CONVERTED', target_object_type = ?, target_object_id = ?,
				    target_object_line_id = null, target_object_no = ?, converted_by = ?, converted_at = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				returning version
				""", Long.class, target.targetObjectType(), target.targetObjectId(), target.targetObjectNo(),
				operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
	}

	private CalculationResult buildCalculation(NormalizedScope scope) {
		CalculationContext context = new CalculationContext(scope);
		List<DemandRow> demands = demandRows(scope);
		for (DemandRow demand : demands) {
			context.fingerprintParts.add(demand.fingerprintPart());
			MaterialSnapshot material = context.material(demand.materialId());
			WorkRequirement productRequirement = context.requirement(demand.materialId(), material.unitId(),
					demand.projectId(), demand.demandDate(), demand.openDemandQuantity(), "SALES_DEMAND",
					"SALES_ORDER", demand.orderId(), demand.demandSourceLineId(), 0, demand.bomPath(),
					demand.deliveryPlanId(), demand.deliveryPlanNo());
			context.allocate(productRequirement);
			if (positive(productRequirement.shortageQuantity)) {
				if (material.isPurchased()) {
					context.purchaseSuggestion(productRequirement);
				}
				else {
					context.productionSuggestion(productRequirement, material);
					if (material.isManufacturingSupply()) {
						context.expandBom(material, productRequirement, productRequirement.shortageQuantity, demand, 1,
								productRequirement.bomPath, List.of(material.id()));
					}
				}
			}
		}
		context.materializePurchasedRequirements();
		context.addSubstituteHints();
		List<String> parts = new ArrayList<>(context.fingerprintParts);
		parts.sort(String::compareTo);
		String fingerprint = sha256(String.join("\n", parts));
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("demandCount", demands.size());
		snapshot.put("requirementCount", context.requirements.size());
		snapshot.put("allocationCount", context.allocations.size());
		snapshot.put("suggestionCount", context.suggestions.size());
		snapshot.put("scopeHash", scope.scopeHash());
		return new CalculationResult(context.requirements, context.allocations, context.suggestions,
				context.substituteHints, fingerprint, snapshot);
	}

	private List<DemandRow> demandRows(NormalizedScope scope) {
		List<Object> args = new ArrayList<>();
		List<String> planConditions = demandScopeConditions(scope, "dp.planned_date", args);
		planConditions.add("dp.status in ('PLANNED', 'PARTIALLY_SHIPPED')");
		planConditions.add("greatest(dp.planned_quantity - dp.shipped_quantity, 0.000000) > 0");
		List<String> lineConditions = demandScopeConditions(scope,
				"coalesce(ol.expected_ship_date, o.expected_ship_date, current_date)", args);
		lineConditions.add("d.open_demand_quantity > 0");
		lineConditions.add("""
				not exists (
					select 1
					from sal_sales_delivery_plan dp2
					where dp2.order_line_id = d.order_line_id
					and dp2.status in ('PLANNED', 'PARTIALLY_SHIPPED')
					and greatest(dp2.planned_quantity - dp2.shipped_quantity, 0.000000) > 0
				)
				""");
		return this.jdbcTemplate.query("""
				select *
				from (
					select d.order_id, d.order_no, d.order_line_id, dp.id as delivery_plan_id,
					       ('DP-' || dp.line_no) as delivery_plan_no, d.customer_id, d.project_id, d.contract_id,
					       d.material_id, d.unit_id,
					       greatest(dp.planned_quantity - dp.shipped_quantity, 0.000000) as open_demand_quantity,
					       dp.planned_date as demand_date, ol.quantity, ol.shipped_quantity,
					       ol.version as line_version, ol.updated_at as line_updated_at, o.status as order_status,
					       o.version as order_version, o.updated_at as order_updated_at,
					       dp.status as delivery_plan_status, dp.version as delivery_plan_version,
					       dp.updated_at as delivery_plan_updated_at
					from sal_effective_sales_demand d
					join sal_sales_order_line ol on ol.id = d.order_line_id
					join sal_sales_order o on o.id = d.order_id
					join sal_sales_delivery_plan dp on dp.order_line_id = d.order_line_id
					where %s
					union all
					select d.order_id, d.order_no, d.order_line_id, null::bigint as delivery_plan_id,
					       null::varchar as delivery_plan_no, d.customer_id, d.project_id, d.contract_id,
					       d.material_id, d.unit_id, d.open_demand_quantity,
					       coalesce(ol.expected_ship_date, o.expected_ship_date, current_date) as demand_date,
					       ol.quantity, ol.shipped_quantity, ol.version as line_version, ol.updated_at as line_updated_at,
					       o.status as order_status, o.version as order_version, o.updated_at as order_updated_at,
					       null::varchar as delivery_plan_status, null::bigint as delivery_plan_version,
					       null::timestamptz as delivery_plan_updated_at
					from sal_effective_sales_demand d
					join sal_sales_order_line ol on ol.id = d.order_line_id
					join sal_sales_order o on o.id = d.order_id
					where %s
				) demand
				order by demand_date asc, order_id asc, coalesce(delivery_plan_id, order_line_id) asc
				""".formatted(String.join(" and ", planConditions), String.join(" and ", lineConditions)),
				this::mapDemand, args.toArray());
	}

	private List<String> demandScopeConditions(NormalizedScope scope, String dateExpression, List<Object> args) {
		List<String> conditions = new ArrayList<>();
		conditions.add("d.counted_as_effective_demand = true");
		conditions.add(dateExpression + " <= ?");
		args.add(scope.demandDateTo());
		if ("PROJECT".equals(scope.scopeType())) {
			conditions.add("d.project_id = ?");
			args.add(scope.projectId());
		}
		if (scope.customerId() != null) {
			conditions.add("d.customer_id = ?");
			args.add(scope.customerId());
		}
		if (scope.contractId() != null) {
			conditions.add("d.contract_id = ?");
			args.add(scope.contractId());
		}
		if (scope.salesOrderId() != null) {
			conditions.add("d.order_id = ?");
			args.add(scope.salesOrderId());
		}
		if (scope.materialId() != null) {
			conditions.add("d.material_id = ?");
			args.add(scope.materialId());
		}
		return conditions;
	}

	private void persistCalculation(Long runId, CalculationResult calculation, String username) {
		int lineNo = 1;
		for (WorkRequirement requirement : calculation.requirements()) {
			Long requirementId = this.jdbcTemplate.queryForObject("""
					insert into mrp_requirement_line (
						run_id, line_no, demand_source_type, demand_source_id, demand_source_line_id, delivery_plan_id,
						delivery_plan_no, demand_type, project_id, material_id, unit_id, demand_date, required_quantity,
						covered_quantity, shortage_quantity, bom_level, bom_path, source_snapshot
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
					returning id
					""", Long.class, runId, lineNo++, requirement.demandSourceType, requirement.demandSourceId,
					requirement.demandSourceLineId, requirement.deliveryPlanId, requirement.deliveryPlanNo,
					requirement.demandType, requirement.projectId, requirement.materialId, requirement.unitId,
					requirement.demandDate,
					scale(requirement.requiredQuantity), scale(requirement.coveredQuantity),
					scale(requirement.shortageQuantity), requirement.bomLevel, requirement.bomPath,
					json(requirement.snapshot));
			requirement.id = requirementId;
			for (WorkAllocation allocation : requirement.allocations) {
				allocation.requirementLineId = requirementId;
			}
			for (WorkSuggestion suggestion : calculation.suggestions()) {
				if (suggestion.requirement == requirement) {
					suggestion.requirementLineId = requirementId;
				}
			}
			for (WorkSubstituteHint hint : calculation.substituteHints()) {
				if (hint.requirement == requirement) {
					hint.requirementLineId = requirementId;
				}
			}
		}
		for (WorkAllocation allocation : calculation.allocations()) {
			this.jdbcTemplate.update("""
					insert into mrp_supply_allocation (
						run_id, requirement_line_id, supply_type, source_table, source_id, source_line_id, material_id,
						unit_id, ownership_type, project_id, supply_date, available_quantity, allocated_quantity,
						on_time, allocation_rank, reason, source_snapshot
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
					""", runId, allocation.requirementLineId, allocation.supplyType, allocation.sourceTable,
					allocation.sourceId, allocation.sourceLineId, allocation.materialId, allocation.unitId,
					allocation.ownershipType, allocation.projectId, allocation.supplyDate,
					scale(allocation.availableQuantity), scale(allocation.allocatedQuantity), allocation.onTime,
					allocation.allocationRank, allocation.reason, json(allocation.snapshot));
		}
		for (WorkSuggestion suggestion : calculation.suggestions()) {
			this.jdbcTemplate.update("""
					insert into mrp_suggestion (
						run_id, requirement_line_id, suggestion_type, status, material_id, unit_id, project_id,
						ownership_type, required_date, suggested_quantity, material_source_type, conversion_allowed,
						action_disabled_reason, reason,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", runId, suggestion.requirementLineId, suggestion.suggestionType, suggestion.materialId,
					suggestion.unitId, suggestion.projectId, suggestion.ownershipType, suggestion.requiredDate,
					scale(suggestion.suggestedQuantity), suggestion.materialSourceType, suggestion.conversionAllowed,
					suggestion.actionDisabledReason, suggestion.reason, username, OffsetDateTime.now(), username,
					OffsetDateTime.now());
		}
		for (WorkSubstituteHint hint : calculation.substituteHints()) {
			this.jdbcTemplate.update("""
					insert into mrp_substitute_hint (
						run_id, requirement_line_id, main_material_id, substitute_material_id, priority,
						substitute_rate, scope_type, effective_from, effective_to
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", runId, hint.requirementLineId, hint.mainMaterialId, hint.substituteMaterialId,
					hint.priority, scale(hint.substituteRate), hint.scopeType, hint.effectiveFrom, hint.effectiveTo);
		}
	}

	private Long insertRun(NormalizedScope scope, CurrentUser operator, OffsetDateTime now, Long previousRunId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mrp_calculation_run (
					run_no, scope_type, project_id, customer_id, contract_id, sales_order_id, material_id,
					demand_date_to, include_public_demand, scope_hash, request_fingerprint, status,
					idempotency_key, previous_run_id, created_by_user_id, created_by_username, created_at,
					updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, nextRunNo(), scope.scopeType(), scope.projectId(), scope.customerId(),
				scope.contractId(), scope.salesOrderId(), scope.materialId(), scope.demandDateTo(),
				scope.includePublicDemand(), scope.scopeHash(), scope.requestFingerprint(), scope.idempotencyKey(),
				previousRunId, operator.id(), operator.username(), now, operator.username(), now);
	}

	private void markRunFailed(Long runId, PlanningFailure failure, CurrentUser operator, OffsetDateTime now,
			HttpServletRequest servletRequest, String scopeHash) {
		this.jdbcTemplate.update("""
				update mrp_calculation_run
				set status = 'FAILED', status_reason = ?, failure_code = ?, failure_summary = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", failure.errorCode().code(), failure.errorCode().code(), failure.summary(), operator.username(),
				now, runId);
		this.auditService.recordFailure(operator, "MATERIAL_REQUIREMENT_RUN_CALCULATE", TARGET_RUN, runId,
				scopeHash, failure.errorCode().code(), servletRequest);
	}

	private Optional<Long> reusableRun(String scopeHash, String sourceFingerprint) {
		return this.jdbcTemplate.query("""
				select id
				from mrp_calculation_run
				where scope_hash = ?
				and source_fingerprint = ?
				and status = 'COMPLETED'
				and expires_at > now()
				order by calculated_at desc, id desc
				limit 1
				""", (rs, rowNum) -> rs.getLong("id"), scopeHash, sourceFingerprint).stream().findFirst();
	}

	private Optional<ExistingRun> existingIdempotentRun(Long operatorUserId, String idempotencyKey) {
		return this.jdbcTemplate.query("""
				select id, request_fingerprint
				from mrp_calculation_run
				where created_by_user_id = ?
				and idempotency_key = ?
				order by id desc
				limit 1
				""", (rs, rowNum) -> new ExistingRun(rs.getLong("id"), rs.getString("request_fingerprint")),
				operatorUserId, idempotencyKey).stream().findFirst();
	}

	private void refreshRunState(RunRow row) {
		if (!"COMPLETED".equals(row.status())) {
			return;
		}
		String currentFingerprint;
		try {
			currentFingerprint = buildCalculation(row.scope()).sourceFingerprint();
		}
		catch (PlanningFailureException | BusinessException exception) {
			markRunStale(row.id());
			return;
		}
		if (!Objects.equals(row.sourceFingerprint(), currentFingerprint)) {
			markRunStale(row.id());
			return;
		}
		if (row.expiresAt() != null && row.expiresAt().isBefore(OffsetDateTime.now())) {
			this.jdbcTemplate.update("""
					update mrp_calculation_run
					set status = 'EXPIRED', status_reason = 'SNAPSHOT_EXPIRED', updated_at = ?, version = version + 1
					where id = ?
					and status = 'COMPLETED'
					""", OffsetDateTime.now(), row.id());
		}
	}

	private void markRunStale(Long runId) {
		this.jdbcTemplate.update("""
				update mrp_calculation_run
				set status = 'STALE', status_reason = 'SOURCE_CHANGED', updated_at = ?, version = version + 1
				where id = ?
				and status = 'COMPLETED'
				""", OffsetDateTime.now(), runId);
	}

	private void requireRunWritable(Long runId) {
		RunRow row = run(runId).orElseThrow(this::notFound);
		refreshRunState(row);
		RunRow refreshed = run(runId).orElseThrow(this::notFound);
		if ("STALE".equals(refreshed.status())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_RUN_STALE);
		}
		if ("EXPIRED".equals(refreshed.status())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_RUN_EXPIRED);
		}
		if (!"COMPLETED".equals(refreshed.status())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_SUGGESTION_STATE_CONFLICT);
		}
	}

	private Optional<RunRow> run(Long id) {
		return this.jdbcTemplate.query("""
				select r.*
				from mrp_calculation_run r
				where r.id = ?
				""", this::mapRunRow, id).stream().findFirst();
	}

	private void ensureRunExists(Long runId) {
		if (run(runId).isEmpty()) {
			throw notFound();
		}
	}

	private RunResponse runResponse(RunRow row) {
		RunCounts counts = this.jdbcTemplate.queryForObject("""
				select count(distinct rl.id) as requirement_line_count,
				       count(distinct rl.project_id) as project_count,
				       count(distinct case when rl.shortage_quantity > 0 then rl.material_id end) as shortage_material_count,
				       count(distinct case when s.suggestion_type = 'PURCHASE_REQUISITION' then s.id end) as purchase_suggestion_count,
				       count(distinct case when s.suggestion_type = 'PRODUCTION_ORDER' then s.id end) as production_suggestion_count,
				       count(distinct case when coalesce(a.reason, '') not in ('', 'ALLOCATED', 'SUPPLY_LATE') then a.id end) as exception_count,
				       count(distinct s.id) as suggestion_count
				from mrp_calculation_run r
				left join mrp_requirement_line rl on rl.run_id = r.id
				left join mrp_suggestion s on s.run_id = r.id
				left join mrp_supply_allocation a on a.run_id = r.id
				where r.id = ?
				""", (rs, rowNum) -> new RunCounts(rs.getInt("requirement_line_count"), rs.getInt("project_count"),
				rs.getInt("shortage_material_count"), rs.getInt("purchase_suggestion_count"),
				rs.getInt("production_suggestion_count"), rs.getInt("exception_count"), rs.getInt("suggestion_count")),
				row.id());
		boolean expired = "EXPIRED".equals(row.status())
				|| (row.expiresAt() != null && row.expiresAt().isBefore(OffsetDateTime.now()));
		return new RunResponse(row.id(), row.runNo(), row.scopeType(), row.projectId(), row.customerId(),
				row.contractId(), row.salesOrderId(), row.materialId(), row.demandDateTo(),
				row.includePublicDemand(), row.status(), row.statusReason(), row.failureCode(), row.failureSummary(),
				row.scopeHash(), row.sourceFingerprint(), row.calculatedAt(), row.expiresAt(),
				counts.requirementLineCount(), counts.suggestionCount(), runAllowedActions(row.status()), row.version(),
				scopeSummary(row), counts.projectCount(), counts.requirementLineCount(), counts.shortageMaterialCount(),
				counts.purchaseSuggestionCount(), counts.productionSuggestionCount(), counts.exceptionCount(),
				row.demandDateTo(), row.calculatedAt(), row.calculatedAt(), row.createdByUsername(),
				"STALE".equals(row.status()), expired, row.previousRunId(), sourceCounts(row.id()));
	}

	private RunResponse mapRun(ResultSet rs, int rowNum) throws SQLException {
		return runResponse(mapRunRow(rs, rowNum));
	}

	private RunRow mapRunRow(ResultSet rs, int rowNum) throws SQLException {
		return new RunRow(rs.getLong("id"), rs.getString("run_no"), rs.getString("scope_type"),
				nullableLong(rs, "project_id"), nullableLong(rs, "customer_id"), nullableLong(rs, "contract_id"),
				nullableLong(rs, "sales_order_id"), nullableLong(rs, "material_id"),
				rs.getObject("demand_date_to", LocalDate.class), rs.getBoolean("include_public_demand"),
				rs.getString("status"), rs.getString("scope_hash"), rs.getString("request_fingerprint"),
				rs.getString("source_fingerprint"), rs.getObject("calculated_at", OffsetDateTime.class),
				rs.getObject("expires_at", OffsetDateTime.class), rs.getString("idempotency_key"),
				rs.getString("status_reason"), rs.getString("failure_code"), rs.getString("failure_summary"),
				nullableLong(rs, "previous_run_id"), rs.getString("created_by_username"), rs.getLong("version"));
	}

	private RequirementLineResponse mapRequirement(ResultSet rs, int rowNum) throws SQLException {
		return mapRequirement(rs, rowNum, null);
	}

	private RequirementLineResponse mapRequirement(ResultSet rs, int rowNum, CurrentUser currentUser) throws SQLException {
		boolean salesVisible = hasPermission(currentUser, "sales:order:view");
		boolean bomVisible = hasPermission(currentUser, "material:bom:view");
		boolean projectVisible = salesVisible || hasPermission(currentUser, "sales:project:view");
		return new RequirementLineResponse(rs.getLong("id"), rs.getLong("run_id"), rs.getInt("line_no"),
				rs.getString("demand_source_type"), salesVisible ? nullableLong(rs, "demand_source_id") : null,
				salesVisible ? nullableLong(rs, "demand_source_line_id") : null,
				salesVisible ? nullableLong(rs, "delivery_plan_id") : null,
				salesVisible ? rs.getString("delivery_plan_no") : null, rs.getString("demand_type"),
				projectVisible ? nullableLong(rs, "project_id") : null,
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				rs.getLong("unit_id"), rs.getString("unit_name"), rs.getObject("demand_date", LocalDate.class),
				rs.getObject("demand_date", LocalDate.class),
				decimalString(rs.getBigDecimal("required_quantity")), decimalString(rs.getBigDecimal("covered_quantity")),
				decimalString(rs.getBigDecimal("shortage_quantity")), rs.getInt("bom_level"),
				bomVisible ? rs.getString("bom_path") : null, salesVisible ? rs.getString("order_no") : null,
				projectVisible ? rs.getString("project_no") : null, projectVisible ? rs.getString("project_name") : null,
				salesVisible ? rs.getString("finished_material_code") : null,
				salesVisible ? rs.getString("finished_material_name") : null,
				bomVisible ? rs.getString("bom_version_no") : null,
				rs.getObject("estimated_available_date", LocalDate.class), rs.getString("coverage_status"),
				rs.getString("suggestion_type"), rs.getString("exception_reason_code"));
	}

	private SupplyAllocationResponse mapAllocation(ResultSet rs, int rowNum) throws SQLException {
		return mapAllocation(rs, rowNum, null);
	}

	private SupplyAllocationResponse mapAllocation(ResultSet rs, int rowNum, CurrentUser currentUser)
			throws SQLException {
		boolean sourceVisible = allocationSourceVisible(currentUser, rs.getString("source_table"));
		return new SupplyAllocationResponse(rs.getLong("id"), rs.getLong("run_id"),
				rs.getLong("requirement_line_id"), rs.getString("supply_type"),
				sourceVisible ? rs.getString("source_table") : null, sourceVisible ? rs.getLong("source_id") : null,
				sourceVisible ? nullableLong(rs, "source_line_id") : null, rs.getLong("material_id"),
				rs.getLong("unit_id"), rs.getString("ownership_type"),
				sourceVisible ? nullableLong(rs, "project_id") : null,
				rs.getObject("supply_date", LocalDate.class), decimalString(rs.getBigDecimal("available_quantity")),
				decimalString(rs.getBigDecimal("allocated_quantity")), rs.getBoolean("on_time"),
				rs.getInt("allocation_rank"), excludedReasonCode(rs.getString("reason")),
				sourceVisible ? rs.getString("supply_type_name") : null, sourceVisible ? rs.getString("project_no") : null,
				sourceVisible ? rs.getString("warehouse_name") : null, sourceVisible ? rs.getString("source_no") : null);
	}

	private static String excludedReasonCode(String reason) {
		if (!hasText(reason) || "ALLOCATED".equals(reason)) {
			return null;
		}
		return reason;
	}

	private SuggestionResponse mapSuggestion(ResultSet rs, int rowNum) throws SQLException {
		return mapSuggestion(rs, rowNum, null);
	}

	private SuggestionResponse mapSuggestion(ResultSet rs, int rowNum, CurrentUser currentUser) throws SQLException {
		boolean projectVisible = hasPermission(currentUser, "sales:project:view")
				|| hasPermission(currentUser, "sales:order:view");
		String status = rs.getString("status");
		String suggestionType = rs.getString("suggestion_type");
		String materialSourceType = rs.getString("material_source_type");
		boolean storedConversionAllowed = rs.getBoolean("conversion_allowed");
		boolean effectiveConversionAllowed = suggestionConversionAllowed(status, suggestionType, materialSourceType,
				storedConversionAllowed, currentUser);
		String actionDisabledReason = suggestionActionDisabledReason(status, suggestionType, materialSourceType,
				storedConversionAllowed, effectiveConversionAllowed, rs.getString("action_disabled_reason"));
		return new SuggestionResponse(rs.getLong("id"), rs.getLong("run_id"), rs.getLong("requirement_line_id"),
				suggestionType, status, rs.getLong("material_id"),
				rs.getString("material_code"), rs.getString("material_name"), rs.getLong("unit_id"),
				rs.getString("unit_name"), projectVisible ? nullableLong(rs, "project_id") : null,
				rs.getString("ownership_type"),
				rs.getObject("required_date", LocalDate.class), decimalString(rs.getBigDecimal("suggested_quantity")),
				materialSourceType, effectiveConversionAllowed,
				actionDisabledReason, rs.getString("reason"),
				nullableLong(rs, "target_object_id"), rs.getString("target_object_no"),
				rs.getString("target_object_type"), rs.getLong("version"),
				suggestionAllowedActions(status, suggestionType, effectiveConversionAllowed, materialSourceType),
				"MRP-SUG-" + rs.getLong("id"), suggestionStatusName(rs.getString("status")),
				projectVisible ? rs.getString("project_no") : null, projectVisible ? rs.getString("project_name") : null,
				rs.getObject("required_date", LocalDate.class),
				rs.getString("reason"), suggestionReasonMessage(rs.getString("reason")),
				nullableLong(rs, "target_object_id"), rs.getString("target_object_no"));
	}

	private SubstituteHintResponse mapSubstituteHint(ResultSet rs, int rowNum) throws SQLException {
		return mapSubstituteHint(rs, rowNum, null);
	}

	private SubstituteHintResponse mapSubstituteHint(ResultSet rs, int rowNum, CurrentUser currentUser)
			throws SQLException {
		boolean substituteVisible = hasPermission(currentUser, "material:substitute:view")
				&& hasPermission(currentUser, "master:material:view");
		return new SubstituteHintResponse(rs.getLong("id"), rs.getLong("run_id"), rs.getLong("requirement_line_id"),
				rs.getLong("main_material_id"), substituteVisible ? rs.getLong("substitute_material_id") : null,
				substituteVisible ? rs.getString("substitute_material_code") : null,
				substituteVisible ? rs.getString("substitute_material_name") : null,
				rs.getInt("priority"), decimalString(rs.getBigDecimal("substitute_rate")), rs.getString("scope_type"),
				rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class));
	}

	private DemandRow mapDemand(ResultSet rs, int rowNum) throws SQLException {
		return new DemandRow(rs.getLong("order_id"), rs.getString("order_no"), rs.getLong("order_line_id"),
				nullableLong(rs, "delivery_plan_id"), rs.getString("delivery_plan_no"), rs.getLong("customer_id"),
				nullableLong(rs, "project_id"), nullableLong(rs, "contract_id"), rs.getLong("material_id"),
				rs.getLong("unit_id"), scale(rs.getBigDecimal("open_demand_quantity")),
				rs.getObject("demand_date", LocalDate.class), scale(rs.getBigDecimal("quantity")),
				scale(rs.getBigDecimal("shipped_quantity")), rs.getLong("line_version"),
				rs.getObject("line_updated_at", OffsetDateTime.class), rs.getString("order_status"),
				rs.getLong("order_version"), rs.getObject("order_updated_at", OffsetDateTime.class),
				rs.getString("delivery_plan_status"), nullableLong(rs, "delivery_plan_version"),
				rs.getObject("delivery_plan_updated_at", OffsetDateTime.class));
	}

	private SuggestionResponse suggestion(Long id) {
		return this.jdbcTemplate.query("""
				select s.*, m.code as material_code, m.name as material_name, u.name as unit_name,
				       p.project_no, p.name as project_name
				from mrp_suggestion s
				join mst_material m on m.id = s.material_id
				join mst_unit u on u.id = s.unit_id
				left join sal_project p on p.id = s.project_id
				where s.id = ?
				""", this::mapSuggestion, id).stream().findFirst().orElseThrow(this::suggestionNotFound);
	}

	private Optional<SuggestionRow> lockSuggestion(Long id) {
		return this.jdbcTemplate.query("""
				select id, run_id, requirement_line_id, suggestion_type, status, material_id, unit_id, project_id,
				       ownership_type, required_date, suggested_quantity, material_source_type, conversion_allowed,
				       target_object_id, version
				from mrp_suggestion
				where id = ?
				for update
				""", this::mapSuggestionRow, id).stream().findFirst();
	}

	private SuggestionRow mapSuggestionRow(ResultSet rs, int rowNum) throws SQLException {
		return new SuggestionRow(rs.getLong("id"), rs.getLong("run_id"), rs.getLong("requirement_line_id"),
				rs.getString("suggestion_type"), rs.getString("status"), rs.getLong("material_id"),
				rs.getLong("unit_id"), nullableLong(rs, "project_id"), rs.getString("ownership_type"),
				rs.getObject("required_date", LocalDate.class), scale(rs.getBigDecimal("suggested_quantity")),
				rs.getString("material_source_type"), rs.getBoolean("conversion_allowed"),
				nullableLong(rs, "target_object_id"), rs.getLong("version"));
	}

	private NormalizedScope normalize(RunRequest request) {
		if (request == null || !hasText(request.idempotencyKey()) || request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String scopeType = hasText(request.scopeType()) ? request.scopeType().trim().toUpperCase() : "GLOBAL";
		if (request.projectId() != null) {
			scopeType = "PROJECT";
		}
		else if (request.customerId() != null) {
			scopeType = "CUSTOMER";
		}
		else if (request.contractId() != null) {
			scopeType = "CONTRACT";
		}
		else if (request.salesOrderId() != null) {
			scopeType = "SALES_ORDER";
		}
		else if (request.materialId() != null) {
			scopeType = "MATERIAL";
		}
		if ("PROJECT".equals(scopeType) && request.projectId() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		LocalDate demandDateTo = request.demandDateTo() == null ? LocalDate.now().plusDays(180)
				: request.demandDateTo();
		if (demandDateTo.isAfter(LocalDate.now().plusDays(365))) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		boolean includePublicDemand = request.includePublicDemand() == null || request.includePublicDemand();
		String canonical = "scopeType=" + scopeType + "|projectId=" + nullToBlank(request.projectId())
				+ "|customerId=" + nullToBlank(request.customerId()) + "|contractId=" + nullToBlank(request.contractId())
				+ "|salesOrderId=" + nullToBlank(request.salesOrderId()) + "|materialId="
				+ nullToBlank(request.materialId()) + "|demandDateTo=" + demandDateTo + "|includePublicDemand="
				+ includePublicDemand;
		String scopeHash = sha256(canonical);
		return new NormalizedScope(scopeType, request.projectId(), request.customerId(), request.contractId(),
				request.salesOrderId(), request.materialId(), demandDateTo, includePublicDemand,
				request.idempotencyKey().trim(), scopeHash, scopeHash);
	}

	private RunRequest recalculateRequest(RunRow row, RunRequest request) {
		if (request == null || !hasText(request.idempotencyKey()) || request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.version() != null) {
			requireVersion(row.version(), request.version());
		}
		requireSameScopeIfPresent(row.scopeType(), request.scopeType());
		requireSameScopeIfPresent(row.projectId(), request.projectId());
		requireSameScopeIfPresent(row.customerId(), request.customerId());
		requireSameScopeIfPresent(row.contractId(), request.contractId());
		requireSameScopeIfPresent(row.salesOrderId(), request.salesOrderId());
		requireSameScopeIfPresent(row.salesOrderId(), request.orderId());
		requireSameScopeIfPresent(row.materialId(), request.materialId());
		requireSameScopeIfPresent(row.demandDateTo(), request.demandDateTo());
		requireSameScopeIfPresent(row.demandDateTo(), request.requiredDateTo());
		if (request.includePublicDemand() != null && request.includePublicDemand() != row.includePublicDemand()) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_RUN_CONFLICT);
		}
		return new RunRequest(row.scopeType(), row.projectId(), row.customerId(), row.contractId(), row.salesOrderId(),
				row.materialId(), row.demandDateTo(), row.includePublicDemand(), row.version(),
				request.idempotencyKey());
	}

	private void requireSameScopeIfPresent(String actual, String requested) {
		if (hasText(requested) && !actual.equalsIgnoreCase(requested.trim())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_RUN_CONFLICT);
		}
	}

	private void requireSameScopeIfPresent(Object actual, Object requested) {
		if (requested != null && !Objects.equals(actual, requested)) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_RUN_CONFLICT);
		}
	}

	private Optional<ActionRecord> existingAction(String action, Long resourceId, String idempotencyKey,
			CurrentUser operator) {
		validateIdempotencyKey(idempotencyKey);
		return this.jdbcTemplate.query("""
				select request_fingerprint, result_resource_id, result_version
				from mrp_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ActionRecord(rs.getString("request_fingerprint"),
				rs.getLong("result_resource_id"), nullableLong(rs, "result_version")), operator.id(), action,
				TARGET_SUGGESTION, resourceId, idempotencyKey).stream().findFirst();
	}

	private void recordAction(String action, Long resourceId, String idempotencyKey, String requestFingerprint,
			String resultResourceType, Long resultResourceId, Long resultVersion, CurrentUser operator) {
		try {
			this.jdbcTemplate.update("""
					insert into mrp_action_idempotency (
						operator_user_id, operator_username, action, resource_type, resource_id, idempotency_key,
						request_fingerprint, result_resource_type, result_resource_id, result_version
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), operator.username(), action, TARGET_SUGGESTION, resourceId, idempotencyKey,
					requestFingerprint, resultResourceType, resultResourceId, resultVersion);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_RUN_CONFLICT);
		}
	}

	private SuggestionActionRequest requireActionRequest(SuggestionActionRequest request) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return request;
	}

	private void requireSameFingerprint(ActionRecord record, String fingerprint) {
		if (!record.requestFingerprint().equals(fingerprint)) {
			throw new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_RUN_CONFLICT);
		}
	}

	private void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
	}

	private void requirePermission(CurrentUser operator, String permissionCode) {
		if (operator == null || !operator.permissions().contains(permissionCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private BusinessException notFound() {
		return new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_RUN_NOT_FOUND);
	}

	private BusinessException suggestionNotFound() {
		return new BusinessException(ApiErrorCode.MATERIAL_REQUIREMENT_SUGGESTION_NOT_FOUND);
	}

	private String actionFingerprint(String action, Long id, SuggestionActionRequest request) {
		return sha256(action + "|" + id + "|" + request.version() + "|" + nullToBlank(request.reason()));
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (!hasText(idempotencyKey) || idempotencyKey.length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private List<String> runAllowedActions(String status) {
		if ("COMPLETED".equals(status)) {
			return List.of("RECALCULATE", "EXPORT");
		}
		if ("STALE".equals(status) || "EXPIRED".equals(status) || "FAILED".equals(status)) {
			return List.of("RECALCULATE");
		}
		return List.of();
	}

	private List<String> suggestionAllowedActions(String status, String suggestionType, boolean conversionAllowed,
			String materialSourceType) {
		if ("USE_PUBLIC_STOCK".equals(suggestionType) || "USE_EXISTING_SUPPLY".equals(suggestionType)) {
			return List.of();
		}
		if ("OPEN".equals(status)) {
			return List.of("CONFIRM", "DISMISS");
		}
		if ("CONFIRMED".equals(status) && "PURCHASE_REQUISITION".equals(suggestionType) && conversionAllowed) {
			return List.of("CONVERT_REQUISITION", "DISMISS");
		}
		if ("CONFIRMED".equals(status) && "PRODUCTION_ORDER".equals(suggestionType)) {
			if ("SELF_MADE".equals(materialSourceType) && conversionAllowed) {
				return List.of("CONVERT_WORK_ORDER", "DISMISS");
			}
			if ("OUTSOURCED".equals(materialSourceType) && conversionAllowed) {
				return List.of("CONVERT_OUTSOURCING_ORDER", "DISMISS");
			}
		}
		return List.of();
	}

	private boolean suggestionConversionAllowed(String status, String suggestionType, String materialSourceType,
			boolean storedConversionAllowed, CurrentUser currentUser) {
		if (!storedConversionAllowed || !"CONFIRMED".equals(status)) {
			return storedConversionAllowed;
		}
		if ("PRODUCTION_ORDER".equals(suggestionType) && "SELF_MADE".equals(materialSourceType)) {
			return hasPermission(currentUser, "planning:material-requirement:convert-production")
					&& hasPermission(currentUser, "production:work-order:create");
		}
		if ("PRODUCTION_ORDER".equals(suggestionType) && "OUTSOURCED".equals(materialSourceType)) {
			return hasPermission(currentUser, "planning:material-requirement:convert-outsourcing")
					&& hasPermission(currentUser, "production:outsourcing:create");
		}
		return storedConversionAllowed;
	}

	private String suggestionActionDisabledReason(String status, String suggestionType, String materialSourceType,
			boolean storedConversionAllowed, boolean effectiveConversionAllowed, String storedReason) {
		if (hasText(storedReason)) {
			return storedReason;
		}
		if (storedConversionAllowed && !effectiveConversionAllowed && "CONFIRMED".equals(status)
				&& "PRODUCTION_ORDER".equals(suggestionType)
				&& ("SELF_MADE".equals(materialSourceType) || "OUTSOURCED".equals(materialSourceType))) {
			return "当前用户权限不足";
		}
		return null;
	}

	private boolean isReadOnlySuggestion(String suggestionType) {
		return "USE_PUBLIC_STOCK".equals(suggestionType) || "USE_EXISTING_SUPPLY".equals(suggestionType);
	}

	private String nextRunNo() {
		return "MRP" + OffsetDateTime.now().format(RUN_NO_FORMATTER)
				+ String.format("%03d", Math.floorMod(RUN_SEQUENCE.incrementAndGet(), 1000));
	}

	private String json(Object value) {
		try {
			return this.objectMapper.writeValueAsString(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private static boolean positive(BigDecimal value) {
		return value != null && value.compareTo(BigDecimal.ZERO) > 0;
	}

	private static BigDecimal scale(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(6, RoundingMode.HALF_UP);
	}

	private static String decimalString(BigDecimal value) {
		return scale(value).toPlainString();
	}

	private static int limit(int pageSize) {
		return pageSize;
	}

	private static int offset(int page, int pageSize) {
		return (page - 1) * pageSize;
	}

	private static void validatePagination(int page, int pageSize) {
		if (page < 1 || !ALLOWED_PAGE_SIZES.contains(pageSize)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private static String coverageStatusExpression(String alias) {
		return "case when " + alias + ".shortage_quantity > 0 then 'SHORTAGE' "
				+ "when " + alias + ".covered_quantity >= " + alias + ".required_quantity then 'COVERED' "
				+ "else 'PARTIAL' end";
	}

	private static boolean hasPermission(CurrentUser currentUser, String permissionCode) {
		return currentUser == null || currentUser.permissions().contains(permissionCode);
	}

	private static boolean allocationSourceVisible(CurrentUser currentUser, String sourceTable) {
		if (currentUser == null) {
			return true;
		}
		return switch (sourceTable) {
			case "inv_stock_balance" -> currentUser.permissions().contains("inventory:balance:view");
			case "proc_purchase_order" -> currentUser.permissions().contains("procurement:supply:view");
			case "mfg_work_order" -> currentUser.permissions().contains("production:work-order:view");
			default -> false;
		};
	}

	private String scopeSummary(RunRow row) {
		return switch (row.scopeType()) {
			case "PROJECT" -> "项目 " + row.projectId();
			case "CUSTOMER" -> "客户 " + row.customerId();
			case "CONTRACT" -> "合同 " + row.contractId();
			case "SALES_ORDER" -> "销售订单 " + row.salesOrderId();
			case "MATERIAL" -> "物料 " + row.materialId();
			default -> "全部需求";
		};
	}

	private Map<String, Integer> sourceCounts(Long runId) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, Integer> requirementCounts = this.jdbcTemplate.query("""
				select demand_type, count(*) as count
				from mrp_requirement_line
				where run_id = ?
				group by demand_type
				""", (rs) -> {
			Map<String, Integer> result = new HashMap<>();
			while (rs.next()) {
				result.put(rs.getString("demand_type"), rs.getInt("count"));
			}
			return result;
		}, runId);
		Map<String, Integer> allocationCounts = this.jdbcTemplate.query("""
				select supply_type, count(*) as count
				from mrp_supply_allocation
				where run_id = ?
				group by supply_type
				""", (rs) -> {
			Map<String, Integer> result = new HashMap<>();
			while (rs.next()) {
				result.put(rs.getString("supply_type"), rs.getInt("count"));
			}
			return result;
		}, runId);
		counts.put("salesDemand", requirementCounts.getOrDefault("SALES_DEMAND", 0));
		counts.put("bomComponent", requirementCounts.getOrDefault("BOM_COMPONENT", 0));
		counts.put("projectStock", allocationCounts.getOrDefault("PROJECT_STOCK", 0));
		counts.put("publicStock", allocationCounts.getOrDefault("PUBLIC_STOCK", 0));
		counts.put("projectPurchase", allocationCounts.getOrDefault("PROJECT_PURCHASE", 0));
		counts.put("publicPurchase", allocationCounts.getOrDefault("PUBLIC_PURCHASE", 0));
		counts.put("workOrder", allocationCounts.getOrDefault("WORK_ORDER", 0));
		return counts;
	}

	private static String suggestionStatusName(String status) {
		return switch (status) {
			case "OPEN" -> "待处理";
			case "CONFIRMED" -> "已确认";
			case "CONVERTED" -> "已转换";
			case "DISMISSED" -> "已驳回";
			default -> status;
		};
	}

	private static String suggestionReasonMessage(String reason) {
		if (!hasText(reason)) {
			return null;
		}
		return switch (reason) {
			case "SHORTAGE" -> "净需求存在缺口";
			case "PUBLIC_STOCK" -> "可使用公共库存";
			case "PUBLIC_PURCHASE" -> "可使用公共采购供给";
			case "WORK_ORDER" -> "可使用既有工单供给";
			default -> reason;
		};
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String nullToBlank(Object value) {
		return value == null ? "" : value.toString();
	}

	private static String safeError(RuntimeException exception) {
		String message = exception.getMessage();
		if (!hasText(message)) {
			return exception.getClass().getSimpleName();
		}
		return message.length() > 500 ? message.substring(0, 500) : message;
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes()));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record RunListFilter(Long projectId, Long customerId, Long contractId, Long orderId, Long materialId,
			LocalDate requiredDateTo, String status, Boolean expired) {
	}

	public record RequirementLineFilter(Long materialId, Boolean shortageOnly, String coverageStatus) {
	}

	public record AllocationFilter(Long requirementLineId) {
	}

	public record SuggestionFilter(String status, String suggestionType) {
	}

	public record SubstituteHintFilter(Long requirementLineId) {
	}

	public record RunRequest(String scopeType, Long projectId, Long customerId, Long contractId, Long salesOrderId,
			Long materialId, LocalDate demandDateTo, Boolean includePublicDemand, Long version,
			@NotBlank String idempotencyKey, Long orderId, LocalDate requiredDateTo) {

		public RunRequest {
			if (salesOrderId == null && orderId != null) {
				salesOrderId = orderId;
			}
			if (demandDateTo == null && requiredDateTo != null) {
				demandDateTo = requiredDateTo;
			}
		}

		public RunRequest(String scopeType, Long projectId, Long customerId, Long contractId, Long salesOrderId,
				Long materialId, LocalDate demandDateTo, Boolean includePublicDemand, Long version,
				String idempotencyKey) {
			this(scopeType, projectId, customerId, contractId, salesOrderId, materialId, demandDateTo,
					includePublicDemand, version, idempotencyKey, null, null);
		}
	}

	public record SuggestionActionRequest(@NotNull Long version, String reason, @NotBlank String idempotencyKey) {
	}

	public record RunResponse(Long id, String runNo, String scopeType, Long projectId, Long customerId,
			Long contractId, Long salesOrderId, Long materialId, LocalDate demandDateTo, boolean includePublicDemand,
			String status, String statusReason, String failureCode, String failureSummary, String scopeHash,
			String sourceFingerprint, OffsetDateTime calculatedAt, OffsetDateTime expiresAt, int requirementCount,
			int suggestionCount, List<String> allowedActions, Long version, String scopeSummary, Integer projectCount,
			Integer requirementLineCount, Integer shortageMaterialCount, Integer purchaseSuggestionCount,
			Integer productionSuggestionCount, Integer exceptionCount, LocalDate asOfBusinessDate,
			OffsetDateTime asOfTime, OffsetDateTime completedAt, String createdByName, Boolean stale, Boolean expired,
			Long previousRunId, Map<String, Integer> sourceCounts) {
	}

	public record RequirementLineResponse(Long id, Long runId, Integer lineNo, String demandSourceType,
			Long demandSourceId, Long demandSourceLineId, Long deliveryPlanId, String deliveryPlanNo, String demandType,
			Long projectId, Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			LocalDate demandDate, LocalDate requiredDate, String requiredQuantity, String coveredQuantity,
			String shortageQuantity, Integer bomLevel, String bomPath, String orderNo, String projectNo,
			String projectName, String finishedMaterialCode, String finishedMaterialName, String bomVersionNo,
			LocalDate estimatedAvailableDate, String coverageStatus, String suggestionType, String exceptionReasonCode) {
	}

	public record SupplyAllocationResponse(Long id, Long runId, Long requirementLineId, String supplyType,
			String sourceTable, Long sourceId, Long sourceLineId, Long materialId, Long unitId, String ownershipType,
			Long projectId, LocalDate availableDate, String availableQuantity, String allocatedQuantity, boolean onTime,
			Integer allocationRank, String excludedReasonCode, String supplyTypeName, String projectNo, String warehouseName,
			String sourceNo) {
	}

	public record SuggestionResponse(Long id, Long runId, Long requirementLineId, String suggestionType,
			String status, Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			Long projectId, String ownershipType, LocalDate requiredDate, String suggestedQuantity,
			String materialSourceType, boolean conversionAllowed, String actionDisabledReason, String reason,
			Long targetObjectId, String targetObjectNo, String targetObjectType, Long version,
			List<String> allowedActions, String suggestionNo, String statusName, String projectNo, String projectName,
			LocalDate suggestedDate, String reasonCode, String reasonMessage, Long convertedRequisitionId,
			String convertedRequisitionNo) {
	}

	public record SuggestionConversionResponse(Long suggestionId, String status, String targetObjectType,
			Long targetObjectId, String targetObjectNo, String targetRoute, Long version) {
	}

	public record SubstituteHintResponse(Long id, Long runId, Long requirementLineId, Long mainMaterialId,
			Long substituteMaterialId, String substituteMaterialCode, String substituteMaterialName, Integer priority,
			String substituteRate, String scopeType, LocalDate effectiveFrom, LocalDate effectiveTo) {
	}

	private record NormalizedScope(String scopeType, Long projectId, Long customerId, Long contractId, Long salesOrderId,
			Long materialId, LocalDate demandDateTo, boolean includePublicDemand, String idempotencyKey,
			String scopeHash, String requestFingerprint) {
	}

	private record ExistingRun(Long id, String requestFingerprint) {
	}

	private record ActionRecord(String requestFingerprint, Long resultResourceId, Long resultVersion) {
	}

	private record RunCounts(int requirementLineCount, int projectCount, int shortageMaterialCount,
			int purchaseSuggestionCount, int productionSuggestionCount, int exceptionCount, int suggestionCount) {
	}

	private record CalculationOutcome(Long runId, PlanningFailure failure) {

		static CalculationOutcome success(Long runId) {
			return new CalculationOutcome(runId, null);
		}

		static CalculationOutcome failed(Long runId, PlanningFailure failure) {
			return new CalculationOutcome(runId, failure);
		}

	}

	private record PlanningFailure(ApiErrorCode errorCode, String summary) {
	}

	private static final class PlanningFailureException extends RuntimeException {

		private final PlanningFailure failure;

		private PlanningFailureException(ApiErrorCode errorCode, String summary) {
			super(summary);
			this.failure = new PlanningFailure(errorCode, summary);
		}

		private PlanningFailure failure() {
			return this.failure;
		}

	}

	private record RunRow(Long id, String runNo, String scopeType, Long projectId, Long customerId, Long contractId,
			Long salesOrderId, Long materialId, LocalDate demandDateTo, boolean includePublicDemand, String status,
			String scopeHash, String requestFingerprint, String sourceFingerprint, OffsetDateTime calculatedAt,
			OffsetDateTime expiresAt, String idempotencyKey, String statusReason, String failureCode,
			String failureSummary, Long previousRunId, String createdByUsername, Long version) {

		NormalizedScope scope() {
			return new NormalizedScope(this.scopeType, this.projectId, this.customerId, this.contractId,
					this.salesOrderId, this.materialId, this.demandDateTo, this.includePublicDemand,
					this.idempotencyKey, this.scopeHash, this.requestFingerprint);
		}

		RunRequest toRequest() {
			return new RunRequest(this.scopeType, this.projectId, this.customerId, this.contractId,
					this.salesOrderId, this.materialId, this.demandDateTo, this.includePublicDemand,
					this.version, this.idempotencyKey);
		}
	}

	private record DemandRow(Long orderId, String orderNo, Long orderLineId, Long deliveryPlanId,
			String deliveryPlanNo, Long customerId, Long projectId, Long contractId, Long materialId, Long unitId,
			BigDecimal openDemandQuantity, LocalDate demandDate, BigDecimal quantity, BigDecimal shippedQuantity,
			Long lineVersion, OffsetDateTime lineUpdatedAt, String orderStatus, Long orderVersion,
			OffsetDateTime orderUpdatedAt, String deliveryPlanStatus, Long deliveryPlanVersion,
			OffsetDateTime deliveryPlanUpdatedAt) {

		Long demandSourceLineId() {
			return this.deliveryPlanId == null ? this.orderLineId : this.deliveryPlanId;
		}

		String bomPath() {
			if (this.deliveryPlanId == null) {
				return "SO:" + this.orderNo + ":" + this.orderLineId;
			}
			return "SO:" + this.orderNo + ":" + this.orderLineId + ":DP:" + this.deliveryPlanNo;
		}

		String fingerprintPart() {
			return "D|" + this.orderId + "|" + this.orderLineId + "|" + this.demandSourceLineId() + "|"
					+ this.materialId + "|"
					+ decimalString(this.openDemandQuantity) + "|" + decimalString(this.quantity) + "|"
					+ decimalString(this.shippedQuantity) + "|" + this.lineVersion + "|" + this.lineUpdatedAt + "|"
					+ this.orderStatus + "|" + this.orderVersion + "|" + this.orderUpdatedAt + "|"
					+ this.deliveryPlanNo + "|" + this.deliveryPlanStatus + "|" + this.deliveryPlanVersion + "|"
					+ this.deliveryPlanUpdatedAt;
		}
	}

	private record MaterialSnapshot(Long id, String code, String name, Long unitId, String sourceType, String status) {

		boolean isPurchased() {
			return "PURCHASED".equals(this.sourceType);
		}

		boolean isSelfMade() {
			return "SELF_MADE".equals(this.sourceType);
		}

		boolean isOutsourced() {
			return "OUTSOURCED".equals(this.sourceType);
		}

		boolean isManufacturingSupply() {
			return isSelfMade() || isOutsourced();
		}
	}

	private record BomHeader(Long id, String bomCode, BigDecimal baseQuantity, Long baseUnitId, Long version,
			OffsetDateTime updatedAt) {
	}

	private record BomLine(Long id, Integer lineNo, Long childMaterialId, Long unitId, Long baseUnitId,
			Long conversionId, BigDecimal baseQuantity, BigDecimal lossRate, OffsetDateTime updatedAt) {
	}

	private record SupplyKey(Long materialId, String sourceTable, Long sourceId, Long sourceLineId) {
	}

	private record PurchaseKey(Long materialId, Long unitId, Long projectId, LocalDate demandDate, String demandSourceType,
			Long demandSourceId, Long demandSourceLineId) implements Comparable<PurchaseKey> {

		@Override
		public int compareTo(PurchaseKey other) {
			return Comparator.comparing(PurchaseKey::demandDate)
				.thenComparing(PurchaseKey::materialId)
				.thenComparing((PurchaseKey key) -> key.projectId == null ? 0L : key.projectId)
				.thenComparing(PurchaseKey::demandSourceId, Comparator.nullsFirst(Long::compareTo))
				.thenComparing(PurchaseKey::demandSourceLineId, Comparator.nullsFirst(Long::compareTo))
				.compare(this, other);
		}
	}

	private record CalculationResult(List<WorkRequirement> requirements, List<WorkAllocation> allocations,
			List<WorkSuggestion> suggestions, List<WorkSubstituteHint> substituteHints, String sourceFingerprint,
			Map<String, Object> snapshot) {
	}

	private record SuggestionRow(Long id, Long runId, Long requirementLineId, String suggestionType, String status,
			Long materialId, Long unitId, Long projectId, String ownershipType, LocalDate requiredDate,
			BigDecimal suggestedQuantity, String materialSourceType, boolean conversionAllowed, Long targetObjectId,
			Long version) {
	}

	private final class CalculationContext {

		private final NormalizedScope scope;

		private final Map<Long, MaterialSnapshot> materials = new HashMap<>();

		private final Map<Long, List<SupplySource>> supplies = new HashMap<>();

		private final TreeMap<PurchaseKey, PurchaseAccumulator> purchaseAccumulators = new TreeMap<>();

		private final List<WorkRequirement> requirements = new ArrayList<>();

		private final List<WorkAllocation> allocations = new ArrayList<>();

		private final List<WorkSuggestion> suggestions = new ArrayList<>();

		private final List<WorkSubstituteHint> substituteHints = new ArrayList<>();

		private final List<String> fingerprintParts = new ArrayList<>();

		private CalculationContext(NormalizedScope scope) {
			this.scope = scope;
		}

		private MaterialSnapshot material(Long materialId) {
			return this.materials.computeIfAbsent(materialId, (id) -> {
				MaterialSnapshot material = jdbcTemplate.query("""
						select id, code, name, unit_id, source_type, status, version, updated_at
						from mst_material
						where id = ?
						""", (rs, rowNum) -> {
					fingerprintParts.add("M|" + rs.getLong("id") + "|" + rs.getString("source_type") + "|"
							+ rs.getString("status") + "|" + rs.getLong("version") + "|"
							+ rs.getObject("updated_at", OffsetDateTime.class));
					return new MaterialSnapshot(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
							rs.getLong("unit_id"), rs.getString("source_type"), rs.getString("status"));
				}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.MASTER_DATA_NOT_FOUND));
				if (!"ENABLED".equals(material.status())) {
					throw planningFailure(ApiErrorCode.MATERIAL_DISABLED, "物料 " + material.code() + " 已停用");
				}
				return material;
			});
		}

		private WorkRequirement requirement(Long materialId, Long unitId, Long projectId, LocalDate demandDate,
				BigDecimal quantity, String demandType, String demandSourceType, Long demandSourceId,
				Long demandSourceLineId, int bomLevel, String bomPath) {
			return requirement(materialId, unitId, projectId, demandDate, quantity, demandType, demandSourceType,
					demandSourceId, demandSourceLineId, bomLevel, bomPath, null, null);
		}

		private WorkRequirement requirement(Long materialId, Long unitId, Long projectId, LocalDate demandDate,
				BigDecimal quantity, String demandType, String demandSourceType, Long demandSourceId,
				Long demandSourceLineId, int bomLevel, String bomPath, Long deliveryPlanId, String deliveryPlanNo) {
			WorkRequirement requirement = new WorkRequirement();
			requirement.materialId = materialId;
			requirement.unitId = unitId;
			requirement.projectId = projectId;
			requirement.demandDate = demandDate;
			requirement.requiredQuantity = scale(quantity);
			requirement.coveredQuantity = ZERO;
			requirement.shortageQuantity = ZERO;
			requirement.demandType = demandType;
			requirement.demandSourceType = demandSourceType;
			requirement.demandSourceId = demandSourceId;
			requirement.demandSourceLineId = demandSourceLineId;
			requirement.deliveryPlanId = deliveryPlanId;
			requirement.deliveryPlanNo = deliveryPlanNo;
			requirement.bomLevel = bomLevel;
			requirement.bomPath = bomPath;
			requirement.snapshot.put("scopeHash", this.scope.scopeHash());
			if (deliveryPlanId != null) {
				requirement.snapshot.put("deliveryPlanId", deliveryPlanId);
				requirement.snapshot.put("deliveryPlanNo", deliveryPlanNo);
			}
			this.requirements.add(requirement);
			return requirement;
		}

		private void allocate(WorkRequirement requirement) {
			BigDecimal remaining = requirement.requiredQuantity;
			List<SupplySource> materialSupplies = this.supplies.computeIfAbsent(requirement.materialId,
					this::loadSupplies);
			recordExcludedSupply(requirement, materialSupplies);
			for (SupplySource source : orderedSupply(materialSupplies, requirement.projectId, requirement.demandDate)) {
				if (!positive(remaining)) {
					break;
				}
				if (!positive(source.remainingQuantity)) {
					continue;
				}
				BigDecimal allocated = remaining.min(source.remainingQuantity);
				source.remainingQuantity = scale(source.remainingQuantity.subtract(allocated));
				remaining = scale(remaining.subtract(allocated));
				WorkAllocation allocation = new WorkAllocation(source, allocated);
				requirement.allocations.add(allocation);
				this.allocations.add(allocation);
				existingSupplySuggestion(requirement, allocation);
			}
			requirement.coveredQuantity = scale(requirement.requiredQuantity.subtract(remaining));
			requirement.shortageQuantity = scale(remaining);
			recordDelayedSupply(requirement, materialSupplies, remaining);
		}

		private void recordExcludedSupply(WorkRequirement requirement, List<SupplySource> materialSupplies) {
			for (SupplySource source : materialSupplies) {
				String reason = exclusionReason(source, requirement.projectId);
				if (reason == null || !positive(source.originalQuantity)) {
					continue;
				}
				WorkAllocation allocation = new WorkAllocation(source, ZERO, source.onTimeFor(requirement.demandDate),
						reason, exclusionRank(reason));
				requirement.allocations.add(allocation);
				this.allocations.add(allocation);
			}
		}

		private List<SupplySource> orderedSupply(List<SupplySource> materialSupplies, Long projectId,
				LocalDate demandDate) {
			List<SupplySource> ordered = new ArrayList<>();
			for (SupplySource source : materialSupplies) {
				if (!source.onTimeFor(demandDate)) {
					continue;
				}
				if (exclusionReason(source, projectId) != null) {
					continue;
				}
				Integer rank = allocationRank(source, projectId);
				if (rank != null) {
					ordered.add(source.withRank(rank));
				}
			}
			sortSupplyForAllocation(ordered);
			return ordered;
		}

		private void recordDelayedSupply(WorkRequirement requirement, List<SupplySource> materialSupplies,
				BigDecimal shortageQuantity) {
			if (!positive(shortageQuantity)) {
				return;
			}
			BigDecimal delayedRemaining = shortageQuantity;
			for (SupplySource source : orderedDelayedSupply(materialSupplies, requirement.projectId,
					requirement.demandDate)) {
				if (!positive(delayedRemaining)) {
					break;
				}
				if (!positive(source.lateExplanationRemainingQuantity)) {
					continue;
				}
				BigDecimal delayedQuantity = delayedRemaining.min(source.lateExplanationRemainingQuantity);
				source.lateExplanationRemainingQuantity =
						scale(source.lateExplanationRemainingQuantity.subtract(delayedQuantity));
				delayedRemaining = scale(delayedRemaining.subtract(delayedQuantity));
				WorkAllocation allocation = new WorkAllocation(source, delayedQuantity, false, "SUPPLY_LATE");
				requirement.allocations.add(allocation);
				this.allocations.add(allocation);
			}
		}

		private List<SupplySource> orderedDelayedSupply(List<SupplySource> materialSupplies, Long projectId,
				LocalDate demandDate) {
			List<SupplySource> ordered = new ArrayList<>();
			for (SupplySource source : materialSupplies) {
				if (source.onTimeFor(demandDate)) {
					continue;
				}
				if (exclusionReason(source, projectId) != null) {
					continue;
				}
				Integer rank = allocationRank(source, projectId);
				if (rank != null) {
					ordered.add(source.withRank(rank));
				}
			}
			sortSupplyForAllocation(ordered);
			return ordered;
		}

		private Integer allocationRank(SupplySource source, Long projectId) {
			if (source.exclusionReason != null) {
				return null;
			}
			if (projectId != null) {
				if ("PROJECT_STOCK".equals(source.supplyType) && projectId.equals(source.projectId)) {
					return 10;
				}
				if ("PROJECT_PURCHASE".equals(source.supplyType) && projectId.equals(source.projectId)) {
					return 20;
				}
				if ("PUBLIC_STOCK".equals(source.supplyType)) {
					return 30;
				}
				if ("PUBLIC_PURCHASE".equals(source.supplyType)) {
					return 40;
				}
				if ("WORK_ORDER".equals(source.supplyType)) {
					return source.projectId != null && projectId.equals(source.projectId) ? 50 : null;
				}
				return null;
			}
			if ("PUBLIC_STOCK".equals(source.supplyType) || "PUBLIC_PURCHASE".equals(source.supplyType)
					|| "WORK_ORDER".equals(source.supplyType)) {
				return source.defaultRank;
			}
			return null;
		}

		private String exclusionReason(SupplySource source, Long projectId) {
			if (source.exclusionReason != null) {
				return source.exclusionReason;
			}
			if (projectId != null) {
				if (("PROJECT_STOCK".equals(source.supplyType) || "PROJECT_PURCHASE".equals(source.supplyType))
						&& !projectId.equals(source.projectId)) {
					return "CROSS_PROJECT_NOT_ALLOWED";
				}
				if ("WORK_ORDER".equals(source.supplyType) && source.projectId == null) {
					return "WORK_ORDER_NOT_PROJECT_BOUND";
				}
				if ("WORK_ORDER".equals(source.supplyType) && !projectId.equals(source.projectId)) {
					return "CROSS_PROJECT_NOT_ALLOWED";
				}
			}
			return null;
		}

		private int exclusionRank(String reason) {
			return switch (reason) {
				case "STOCK_NOT_QUALIFIED" -> 900;
				case "STOCK_RESERVED_OR_OCCUPIED" -> 901;
				case "SUPPLY_STATUS_NOT_COUNTED" -> 902;
				case "WORK_ORDER_NOT_PROJECT_BOUND" -> 903;
				case "CROSS_PROJECT_NOT_ALLOWED" -> 904;
				default -> 999;
			};
		}

		private void sortSupplyForAllocation(List<SupplySource> ordered) {
			ordered.sort(Comparator.comparingInt((SupplySource source) -> source.allocationRank)
				.thenComparing((SupplySource source) -> source.supplyDate, Comparator.nullsFirst(LocalDate::compareTo))
				.thenComparing((SupplySource source) -> source.sourceId)
				.thenComparing((SupplySource source) -> source.sourceLineId, Comparator.nullsFirst(Long::compareTo)));
		}

		private List<SupplySource> loadSupplies(Long materialId) {
			List<SupplySource> result = new ArrayList<>();
			result.addAll(loadStockSupplies(materialId));
			result.addAll(loadPurchaseSupplies(materialId));
			result.addAll(loadWorkOrderSupplies(materialId));
			result.sort(Comparator.comparing((SupplySource source) -> source.supplyType)
				.thenComparing((SupplySource source) -> source.supplyDate, Comparator.nullsFirst(LocalDate::compareTo))
				.thenComparing((SupplySource source) -> source.sourceId)
				.thenComparing((SupplySource source) -> source.sourceLineId, Comparator.nullsFirst(Long::compareTo)));
			return result;
		}

		private List<SupplySource> loadStockSupplies(Long materialId) {
			return jdbcTemplate.query("""
					select b.id, b.material_id, b.unit_id, coalesce(b.ownership_type, 'PUBLIC') as ownership_type,
					       b.project_id, b.quantity_on_hand, b.locked_quantity, b.quality_status,
					       greatest(
					       b.quantity_on_hand - b.locked_quantity,
					       0.000000
					       ) as available_quantity,
					       b.version, b.updated_at
					from inv_stock_balance b
					where b.material_id = ?
					and b.quantity_on_hand > 0
					order by coalesce(b.project_id, 0), b.id
					""", (rs, rowNum) -> {
				String ownership = rs.getString("ownership_type");
				String type = "PROJECT".equals(ownership) ? "PROJECT_STOCK" : "PUBLIC_STOCK";
				BigDecimal availableQuantity = scale(rs.getBigDecimal("available_quantity"));
				BigDecimal sourceQuantity = availableQuantity;
				String exclusionReason = null;
				if (!"QUALIFIED".equals(rs.getString("quality_status"))) {
					exclusionReason = "STOCK_NOT_QUALIFIED";
					sourceQuantity = scale(rs.getBigDecimal("quantity_on_hand"));
				}
				else if (!positive(availableQuantity)) {
					exclusionReason = "STOCK_RESERVED_OR_OCCUPIED";
					sourceQuantity = scale(rs.getBigDecimal("quantity_on_hand"));
				}
				fingerprintParts.add("ST|" + rs.getLong("id") + "|" + decimalString(rs.getBigDecimal("quantity_on_hand"))
						+ "|" + decimalString(rs.getBigDecimal("locked_quantity")) + "|"
						+ decimalString(availableQuantity) + "|" + rs.getString("quality_status") + "|" + ownership
						+ "|" + nullableLong(rs, "project_id") + "|" + rs.getLong("version") + "|"
						+ rs.getObject("updated_at", OffsetDateTime.class));
				return new SupplySource(type, "inv_stock_balance", rs.getLong("id"), null, rs.getLong("material_id"),
						rs.getLong("unit_id"), ownership, nullableLong(rs, "project_id"), null,
						sourceQuantity, 30, exclusionReason);
			}, materialId);
		}

		private List<SupplySource> loadPurchaseSupplies(Long materialId) {
			return jdbcTemplate.query("""
					select o.id as order_id, o.order_no, ol.id as order_line_id, s.id as schedule_id,
					       o.purchase_mode, o.project_id, ol.material_id, ol.unit_id,
					       coalesce(s.planned_date, ol.expected_arrival_date, o.expected_arrival_date) as expected_arrival_date,
					       greatest(coalesce(s.planned_quantity - s.received_quantity,
					       ol.quantity - ol.received_quantity), 0.000000) as remaining_quantity,
					       o.status as order_status, coalesce(s.status, 'PLANNED') as schedule_status
					from proc_purchase_order o
					join proc_purchase_order_line ol on ol.order_id = o.id
					left join proc_purchase_order_schedule s on s.order_line_id = ol.id
					where ol.material_id = ?
					and greatest(coalesce(s.planned_quantity - s.received_quantity,
						ol.quantity - ol.received_quantity), 0.000000) > 0
					order by expected_arrival_date asc, order_id asc, order_line_id asc, schedule_id asc
					""", (rs, rowNum) -> {
				String purchaseMode = rs.getString("purchase_mode");
				String ownership = "PROJECT".equals(purchaseMode) ? "PROJECT" : "PUBLIC";
				String type = "PROJECT".equals(ownership) ? "PROJECT_PURCHASE" : "PUBLIC_PURCHASE";
				String orderStatus = rs.getString("order_status");
				String scheduleStatus = rs.getString("schedule_status");
				String exclusionReason = ("CONFIRMED".equals(orderStatus) || "PARTIALLY_RECEIVED".equals(orderStatus))
						&& ("PLANNED".equals(scheduleStatus) || "PARTIALLY_RECEIVED".equals(scheduleStatus))
								? null : "SUPPLY_STATUS_NOT_COUNTED";
				fingerprintParts.add("PU|" + rs.getLong("order_id") + "|" + rs.getLong("order_line_id") + "|"
						+ nullableLong(rs, "schedule_id") + "|" + decimalString(rs.getBigDecimal("remaining_quantity"))
						+ "|" + purchaseMode + "|" + nullableLong(rs, "project_id") + "|"
						+ rs.getObject("expected_arrival_date", LocalDate.class) + "|" + orderStatus + "|"
						+ scheduleStatus);
				return new SupplySource(type, "proc_purchase_order", rs.getLong("order_id"),
						nullableLong(rs, "schedule_id"), rs.getLong("material_id"), rs.getLong("unit_id"), ownership,
						nullableLong(rs, "project_id"), rs.getObject("expected_arrival_date", LocalDate.class),
						scale(rs.getBigDecimal("remaining_quantity")), "PROJECT".equals(ownership) ? 20 : 40,
						exclusionReason);
			}, materialId);
		}

		private List<SupplySource> loadWorkOrderSupplies(Long materialId) {
			return jdbcTemplate.query("""
					select wo.id, wo.product_material_id as material_id, m.unit_id, wo.planned_finish_date,
					       greatest(wo.planned_quantity - wo.received_quantity, 0.000000) as remaining_quantity,
					       wo.status, wo.version, wo.updated_at
					from mfg_work_order wo
					join mst_material m on m.id = wo.product_material_id
					where wo.product_material_id = ?
					and greatest(wo.planned_quantity - wo.received_quantity, 0.000000) > 0
					order by wo.planned_finish_date asc, wo.id asc
					""", (rs, rowNum) -> {
				String status = rs.getString("status");
				String exclusionReason = ("RELEASED".equals(status) || "IN_PROGRESS".equals(status))
						? null : "SUPPLY_STATUS_NOT_COUNTED";
				fingerprintParts.add("WO|" + rs.getLong("id") + "|"
						+ decimalString(rs.getBigDecimal("remaining_quantity")) + "|"
						+ rs.getObject("planned_finish_date", LocalDate.class) + "|" + status + "|"
						+ rs.getLong("version") + "|" + rs.getObject("updated_at", OffsetDateTime.class));
				return new SupplySource("WORK_ORDER", "mfg_work_order", rs.getLong("id"), null,
						rs.getLong("material_id"), rs.getLong("unit_id"), "PUBLIC", null,
						rs.getObject("planned_finish_date", LocalDate.class), scale(rs.getBigDecimal("remaining_quantity")),
						50, exclusionReason);
			}, materialId);
		}

		private void expandBom(MaterialSnapshot parent, WorkRequirement parentRequirement,
				BigDecimal parentShortage, DemandRow demand, int level, String parentPath, List<Long> materialPath) {
			BomHeader bom = requiredEffectiveBom(parent, demand.demandDate());
			recordRootBomSnapshot(parentRequirement, bom);
			for (BomLine line : bomLines(bom.id())) {
				MaterialSnapshot child = material(line.childMaterialId());
				requireNoBomCycle(child, materialPath, bom, line);
				requireBomLineUnitSnapshot(line, child, bom);
				BigDecimal childQuantity = parentShortage.multiply(line.baseQuantity())
					.divide(bom.baseQuantity(), 6, RoundingMode.HALF_UP)
					.multiply(BigDecimal.ONE.add(scale(line.lossRate())))
					.setScale(6, RoundingMode.HALF_UP);
				String path = parentPath + ">BOM:" + bom.bomCode() + ":" + line.lineNo();
				if (child.isPurchased()) {
					PurchaseKey key = new PurchaseKey(child.id(), child.unitId(), demand.projectId(), demand.demandDate(),
							"SALES_ORDER", demand.orderId(), demand.demandSourceLineId());
					PurchaseAccumulator accumulator = this.purchaseAccumulators.computeIfAbsent(key,
							(ignored) -> new PurchaseAccumulator(child, demand, level, path));
					accumulator.quantity = scale(accumulator.quantity.add(childQuantity));
				}
				else {
					WorkRequirement childRequirement = requirement(child.id(), child.unitId(), demand.projectId(),
							demand.demandDate(), childQuantity, "BOM_COMPONENT", "SALES_ORDER", demand.orderId(),
							demand.demandSourceLineId(), level, path, demand.deliveryPlanId(), demand.deliveryPlanNo());
					allocate(childRequirement);
					if (positive(childRequirement.shortageQuantity)) {
						productionSuggestion(childRequirement, child);
						if (child.isManufacturingSupply()) {
							List<Long> childPath = new ArrayList<>(materialPath);
							childPath.add(child.id());
							expandBom(child, childRequirement, childRequirement.shortageQuantity, demand, level + 1,
									path, childPath);
						}
					}
				}
			}
		}

		private void recordRootBomSnapshot(WorkRequirement parentRequirement, BomHeader bom) {
			if (parentRequirement == null || hasText(parentRequirement.bomPath)
					&& parentRequirement.bomPath.contains("BOM:")) {
				return;
			}
			parentRequirement.snapshot.put("bomVersionNo", bom.bomCode());
		}

		private BomHeader requiredEffectiveBom(MaterialSnapshot parent, LocalDate demandDate) {
			Optional<BomHeader> bom = effectiveBom(parent.id(), demandDate);
			if (bom.isPresent()) {
				return bom.get();
			}
			if (enabledBomExists(parent.id())) {
				throw planningFailure(ApiErrorCode.BOM_NOT_EFFECTIVE,
						"物料 " + parent.code() + " 的 BOM 在需求日期 " + demandDate + " 未生效");
			}
			throw planningFailure(ApiErrorCode.BOM_NOT_FOUND, "物料 " + parent.code() + " 缺少有效 BOM");
		}

		private boolean enabledBomExists(Long parentMaterialId) {
			Integer count = jdbcTemplate.queryForObject("""
					select count(*)
					from mfg_bom
					where parent_material_id = ?
					and status = 'ENABLED'
					""", Integer.class, parentMaterialId);
			return count != null && count > 0;
		}

		private void requireNoBomCycle(MaterialSnapshot child, List<Long> materialPath, BomHeader bom, BomLine line) {
			if (materialPath.contains(child.id())) {
				throw planningFailure(ApiErrorCode.BOM_CYCLE_DETECTED,
						"BOM " + bom.bomCode() + " 第 " + line.lineNo() + " 行形成循环引用");
			}
		}

		private void requireBomLineUnitSnapshot(BomLine line, MaterialSnapshot child, BomHeader bom) {
			if (Objects.equals(line.unitId(), child.unitId())) {
				return;
			}
			if (line.baseUnitId() == null || !Objects.equals(line.baseUnitId(), child.unitId())
					|| line.conversionId() == null) {
				throw planningFailure(ApiErrorCode.UNIT_CONVERSION_REQUIRED,
						"BOM " + bom.bomCode() + " 第 " + line.lineNo() + " 行缺少物料 " + child.code()
								+ " 的单位换算快照");
			}
		}

		private PlanningFailureException planningFailure(ApiErrorCode errorCode, String summary) {
			return new PlanningFailureException(errorCode, summary);
		}

		private Optional<BomHeader> effectiveBom(Long parentMaterialId, LocalDate demandDate) {
			return jdbcTemplate.query("""
					select id, bom_code, base_quantity, base_unit_id, version, updated_at
					from mfg_bom
					where parent_material_id = ?
					and status = 'ENABLED'
					and effective_from <= ?
					and (effective_to is null or effective_to >= ?)
					order by effective_from desc, id desc
					limit 1
					""", (rs, rowNum) -> {
				fingerprintParts.add("BH|" + rs.getLong("id") + "|" + rs.getString("bom_code") + "|"
						+ decimalString(rs.getBigDecimal("base_quantity")) + "|" + rs.getLong("version") + "|"
						+ rs.getObject("updated_at", OffsetDateTime.class));
				return new BomHeader(rs.getLong("id"), rs.getString("bom_code"), scale(rs.getBigDecimal("base_quantity")),
						rs.getLong("base_unit_id"), rs.getLong("version"),
						rs.getObject("updated_at", OffsetDateTime.class));
			}, parentMaterialId, demandDate, demandDate).stream().findFirst();
		}

		private List<BomLine> bomLines(Long bomId) {
			return jdbcTemplate.query("""
					select id, line_no, child_material_id, unit_id, base_unit_id, conversion_id,
					       coalesce(base_quantity, quantity) as base_quantity,
					       coalesce(loss_rate, 0.000000) as loss_rate, updated_at
					from mfg_bom_item
					where bom_id = ?
					order by line_no asc, id asc
					""", (rs, rowNum) -> {
				fingerprintParts.add("BI|" + rs.getLong("id") + "|" + rs.getLong("child_material_id") + "|"
						+ decimalString(rs.getBigDecimal("base_quantity")) + "|"
						+ decimalString(rs.getBigDecimal("loss_rate")) + "|"
						+ rs.getObject("updated_at", OffsetDateTime.class));
				return new BomLine(rs.getLong("id"), rs.getInt("line_no"), rs.getLong("child_material_id"),
						rs.getLong("unit_id"), nullableLong(rs, "base_unit_id"), nullableLong(rs, "conversion_id"),
						scale(rs.getBigDecimal("base_quantity")), scale(rs.getBigDecimal("loss_rate")),
						rs.getObject("updated_at", OffsetDateTime.class));
			}, bomId);
		}

		private void materializePurchasedRequirements() {
			for (PurchaseAccumulator accumulator : this.purchaseAccumulators.values()) {
				if (!positive(accumulator.quantity)) {
					continue;
				}
				WorkRequirement requirement = requirement(accumulator.material.id(), accumulator.material.unitId(),
						accumulator.demand.projectId(), accumulator.demand.demandDate(), accumulator.quantity,
						"BOM_COMPONENT", "SALES_ORDER", accumulator.demand.orderId(),
						accumulator.demand.demandSourceLineId(), accumulator.level, accumulator.bomPath,
						accumulator.demand.deliveryPlanId(), accumulator.demand.deliveryPlanNo());
				allocate(requirement);
				if (positive(requirement.shortageQuantity)) {
					purchaseSuggestion(requirement);
				}
			}
		}

		private void purchaseSuggestion(WorkRequirement requirement) {
			WorkSuggestion suggestion = new WorkSuggestion();
			suggestion.requirement = requirement;
			suggestion.suggestionType = "PURCHASE_REQUISITION";
			suggestion.materialId = requirement.materialId;
			suggestion.unitId = requirement.unitId;
			suggestion.projectId = requirement.projectId;
			suggestion.ownershipType = requirement.projectId == null ? "PUBLIC" : "PROJECT";
			suggestion.requiredDate = requirement.demandDate;
			suggestion.suggestedQuantity = requirement.shortageQuantity;
			suggestion.materialSourceType = material(requirement.materialId).sourceType();
			suggestion.conversionAllowed = true;
			suggestion.reason = "SHORTAGE";
			this.suggestions.add(suggestion);
		}

		private void productionSuggestion(WorkRequirement requirement, MaterialSnapshot material) {
			WorkSuggestion suggestion = new WorkSuggestion();
			suggestion.requirement = requirement;
			suggestion.suggestionType = "PRODUCTION_ORDER";
			suggestion.materialId = requirement.materialId;
			suggestion.unitId = requirement.unitId;
			suggestion.projectId = requirement.projectId;
			suggestion.ownershipType = requirement.projectId == null ? "PUBLIC" : "PROJECT";
			suggestion.requiredDate = requirement.demandDate;
			suggestion.suggestedQuantity = requirement.shortageQuantity;
			suggestion.materialSourceType = material.sourceType();
			suggestion.conversionAllowed = true;
			suggestion.reason = "SHORTAGE";
			this.suggestions.add(suggestion);
		}

		private void existingSupplySuggestion(WorkRequirement requirement, WorkAllocation allocation) {
			if (requirement.projectId == null || !positive(allocation.allocatedQuantity)) {
				return;
			}
			String suggestionType = null;
			if ("PUBLIC_STOCK".equals(allocation.supplyType)) {
				suggestionType = "USE_PUBLIC_STOCK";
			}
			else if ("PUBLIC_PURCHASE".equals(allocation.supplyType) || "WORK_ORDER".equals(allocation.supplyType)) {
				suggestionType = "USE_EXISTING_SUPPLY";
			}
			if (suggestionType == null) {
				return;
			}
			WorkSuggestion suggestion = new WorkSuggestion();
			suggestion.requirement = requirement;
			suggestion.suggestionType = suggestionType;
			suggestion.materialId = requirement.materialId;
			suggestion.unitId = requirement.unitId;
			suggestion.projectId = requirement.projectId;
			suggestion.ownershipType = "PROJECT";
			suggestion.requiredDate = requirement.demandDate;
			suggestion.suggestedQuantity = allocation.allocatedQuantity;
			suggestion.materialSourceType = material(requirement.materialId).sourceType();
			suggestion.conversionAllowed = false;
			suggestion.actionDisabledReason = "只读供给建议，需通过对应业务受控处理";
			suggestion.reason = allocation.supplyType;
			this.suggestions.add(suggestion);
		}

		private void addSubstituteHints() {
			for (WorkRequirement requirement : this.requirements) {
				if (!positive(requirement.shortageQuantity)) {
					continue;
				}
				jdbcTemplate.query("""
						select ms.id, ms.main_material_id, ms.substitute_material_id, ms.priority, ms.substitute_rate,
						       ms.scope_type, ms.effective_from, ms.effective_to, ms.version, ms.updated_at
						from mst_material_substitute ms
						where ms.main_material_id = ?
						and ms.status = 'ENABLED'
						and ms.effective_from <= ?
						and (ms.effective_to is null or ms.effective_to >= ?)
						order by ms.priority asc, ms.id asc
						""", (rs) -> {
					fingerprintParts.add("SUB|" + rs.getLong("id") + "|" + rs.getLong("substitute_material_id")
							+ "|" + rs.getInt("priority") + "|" + decimalString(rs.getBigDecimal("substitute_rate"))
							+ "|" + rs.getLong("version") + "|" + rs.getObject("updated_at", OffsetDateTime.class));
					WorkSubstituteHint hint = new WorkSubstituteHint();
					hint.requirement = requirement;
					hint.mainMaterialId = rs.getLong("main_material_id");
					hint.substituteMaterialId = rs.getLong("substitute_material_id");
					hint.priority = rs.getInt("priority");
					hint.substituteRate = scale(rs.getBigDecimal("substitute_rate"));
					hint.scopeType = rs.getString("scope_type");
					hint.effectiveFrom = rs.getObject("effective_from", LocalDate.class);
					hint.effectiveTo = rs.getObject("effective_to", LocalDate.class);
					this.substituteHints.add(hint);
				}, requirement.materialId, requirement.demandDate, requirement.demandDate);
			}
		}
	}

	private static final class WorkRequirement {

		private Long id;

		private Long materialId;

		private Long unitId;

		private Long projectId;

		private LocalDate demandDate;

		private BigDecimal requiredQuantity;

		private BigDecimal coveredQuantity;

		private BigDecimal shortageQuantity;

		private String demandType;

		private String demandSourceType;

		private Long demandSourceId;

		private Long demandSourceLineId;

		private Long deliveryPlanId;

		private String deliveryPlanNo;

		private int bomLevel;

		private String bomPath;

		private final Map<String, Object> snapshot = new LinkedHashMap<>();

		private final List<WorkAllocation> allocations = new ArrayList<>();

	}

	private static final class WorkAllocation {

		private Long requirementLineId;

		private final String supplyType;

		private final String sourceTable;

		private final Long sourceId;

		private final Long sourceLineId;

		private final Long materialId;

		private final Long unitId;

		private final String ownershipType;

		private final Long projectId;

		private final LocalDate supplyDate;

		private final BigDecimal availableQuantity;

		private final BigDecimal allocatedQuantity;

		private final boolean onTime;

		private final int allocationRank;

		private final String reason;

		private final Map<String, Object> snapshot = new LinkedHashMap<>();

		private WorkAllocation(SupplySource source, BigDecimal allocatedQuantity) {
			this(source, allocatedQuantity, true, "ALLOCATED");
		}

		private WorkAllocation(SupplySource source, BigDecimal allocatedQuantity, boolean onTime, String reason) {
			this(source, allocatedQuantity, onTime, reason, source.allocationRank);
		}

		private WorkAllocation(SupplySource source, BigDecimal allocatedQuantity, boolean onTime, String reason,
				int allocationRank) {
			this.supplyType = source.supplyType;
			this.sourceTable = source.sourceTable;
			this.sourceId = source.sourceId;
			this.sourceLineId = source.sourceLineId;
			this.materialId = source.materialId;
			this.unitId = source.unitId;
			this.ownershipType = source.ownershipType;
			this.projectId = "CROSS_PROJECT_NOT_ALLOWED".equals(reason) ? null : source.projectId;
			this.supplyDate = source.supplyDate;
			this.availableQuantity = source.originalQuantity;
			this.allocatedQuantity = scale(allocatedQuantity);
			this.onTime = onTime;
			this.allocationRank = allocationRank;
			this.reason = reason;
			this.snapshot.put("sourceKey", source.key().toString());
		}
	}

	private static final class WorkSuggestion {

		private WorkRequirement requirement;

		private Long requirementLineId;

		private String suggestionType;

		private Long materialId;

		private Long unitId;

		private Long projectId;

		private String ownershipType;

		private LocalDate requiredDate;

		private BigDecimal suggestedQuantity;

		private String materialSourceType;

		private boolean conversionAllowed;

		private String actionDisabledReason;

		private String reason;

	}

	private static final class WorkSubstituteHint {

		private WorkRequirement requirement;

		private Long requirementLineId;

		private Long mainMaterialId;

		private Long substituteMaterialId;

		private Integer priority;

		private BigDecimal substituteRate;

		private String scopeType;

		private LocalDate effectiveFrom;

		private LocalDate effectiveTo;

	}

	private static final class PurchaseAccumulator {

		private final MaterialSnapshot material;

		private final DemandRow demand;

		private final int level;

		private final String bomPath;

		private BigDecimal quantity = ZERO;

		private PurchaseAccumulator(MaterialSnapshot material, DemandRow demand, int level, String bomPath) {
			this.material = material;
			this.demand = demand;
			this.level = level;
			this.bomPath = bomPath;
		}
	}

	private static final class SupplySource {

		private final String supplyType;

		private final String sourceTable;

		private final Long sourceId;

		private final Long sourceLineId;

		private final Long materialId;

		private final Long unitId;

		private final String ownershipType;

		private final Long projectId;

		private final LocalDate supplyDate;

		private final BigDecimal originalQuantity;

		private BigDecimal remainingQuantity;

		private BigDecimal lateExplanationRemainingQuantity;

		private final int defaultRank;

		private final String exclusionReason;

		private int allocationRank;

		private SupplySource(String supplyType, String sourceTable, Long sourceId, Long sourceLineId, Long materialId,
				Long unitId, String ownershipType, Long projectId, LocalDate supplyDate, BigDecimal quantity,
				int defaultRank) {
			this(supplyType, sourceTable, sourceId, sourceLineId, materialId, unitId, ownershipType, projectId,
					supplyDate, quantity, defaultRank, null);
		}

		private SupplySource(String supplyType, String sourceTable, Long sourceId, Long sourceLineId, Long materialId,
				Long unitId, String ownershipType, Long projectId, LocalDate supplyDate, BigDecimal quantity,
				int defaultRank, String exclusionReason) {
			this.supplyType = supplyType;
			this.sourceTable = sourceTable;
			this.sourceId = sourceId;
			this.sourceLineId = sourceLineId;
			this.materialId = materialId;
			this.unitId = unitId;
			this.ownershipType = ownershipType;
			this.projectId = projectId;
			this.supplyDate = supplyDate;
			this.originalQuantity = scale(quantity);
			this.remainingQuantity = scale(quantity);
			this.lateExplanationRemainingQuantity = scale(quantity);
			this.defaultRank = defaultRank;
			this.exclusionReason = exclusionReason;
			this.allocationRank = defaultRank;
		}

		private SupplySource withRank(int rank) {
			this.allocationRank = rank;
			return this;
		}

		private boolean onTimeFor(LocalDate demandDate) {
			return this.supplyDate == null || !this.supplyDate.isAfter(demandDate);
		}

		private SupplyKey key() {
			return new SupplyKey(this.materialId, this.sourceTable, this.sourceId, this.sourceLineId);
		}
	}

}
