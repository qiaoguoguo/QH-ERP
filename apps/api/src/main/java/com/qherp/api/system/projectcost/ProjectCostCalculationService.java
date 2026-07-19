package com.qherp.api.system.projectcost;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ProjectCostCalculationService {

	private static final String TARGET_TYPE = "PROJECT_COST_CALCULATION";

	private static final String PROJECT_TARGET_TYPE = "PROJECT";

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger NUMBER_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final ProjectCostSourceCollector sourceCollector;

	private final ProjectCostEntryBuilder entryBuilder;

	private final ProjectCostQueryService queryService;

	private final AuditService auditService;

	public ProjectCostCalculationService(JdbcTemplate jdbcTemplate, ProjectCostSourceCollector sourceCollector,
			ProjectCostEntryBuilder entryBuilder, ProjectCostQueryService queryService, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.sourceCollector = sourceCollector;
		this.entryBuilder = entryBuilder;
		this.queryService = queryService;
		this.auditService = auditService;
	}

	@Transactional
	public ProjectCostQueryService.CalculationResponse calculate(Long projectId, CalculationRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		validateProject(projectId);
		String requestFingerprint = fingerprint("CALCULATE|" + projectId + "|" + request.cutoffDate());
		ProjectCostActionRecord existing =
				idempotentAction(operator, "CALCULATE", PROJECT_TARGET_TYPE, projectId, request.idempotencyKey());
		if (existing != null) {
			if (!requestFingerprint.equals(existing.requestFingerprint())) {
				throw new BusinessException(ApiErrorCode.PROJECT_COST_IDEMPOTENCY_CONFLICT);
			}
			return this.queryService.calculation(existing.resultId(), operator);
		}
		ProjectCostSourceCollector.CollectionResult collected = this.sourceCollector.collect(projectId,
				request.cutoffDate());
		ProjectCostEntryBuilder.EntryBuildResult entries = this.entryBuilder.build(collected.sources(),
				collected.variances());
		Metrics metrics = metrics(collected, entries);
		OffsetDateTime now = OffsetDateTime.now();
		Long id;
		try {
			id = this.jdbcTemplate.queryForObject("""
					insert into prj_cost_calculation (
						project_id, calculation_no, cutoff_date, status, is_current, source_fingerprint,
						project_cost_total, wip_cost, finished_cost, delivered_cost, direct_project_cost,
						shipment_revenue, invoice_revenue, target_revenue, shipment_gross_margin,
						invoice_gross_margin, target_gross_margin, shipment_gross_margin_rate,
						invoice_gross_margin_rate, target_gross_margin_rate, margin_completeness,
						completeness_reason, idempotency_key, request_fingerprint, created_by, created_at,
						updated_by, updated_at
					)
					values (?, ?, ?, 'CALCULATED', false, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
						?, ?, ?, ?)
					returning id
					""", Long.class, projectId, nextCalculationNo(), request.cutoffDate(),
					collected.sourceFingerprint(), metrics.projectCostTotal(), metrics.wipCost(),
					metrics.finishedCost(), metrics.deliveredCost(), metrics.directProjectCost(),
					collected.revenue().shipmentRevenue(), collected.revenue().invoiceRevenue(),
					collected.revenue().targetRevenue(), metrics.shipmentGrossMargin(), metrics.invoiceGrossMargin(),
					metrics.targetGrossMargin(), grossMarginRate(metrics.shipmentGrossMargin(),
							collected.revenue().shipmentRevenue()),
					grossMarginRate(metrics.invoiceGrossMargin(), collected.revenue().invoiceRevenue()),
					grossMarginRate(metrics.targetGrossMargin(), collected.revenue().targetRevenue()),
					metrics.marginCompleteness(), metrics.completenessReason(), request.idempotencyKey(),
					requestFingerprint,
					operator.username(), now, operator.username(), now);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_ACTION_NOT_ALLOWED);
		}
		writeSnapshot(id, collected, entries);
		this.auditService.record(operator, "PROJECT_COST_CALCULATE", TARGET_TYPE, id, projectId.toString(),
				servletRequest);
		recordAction(operator, "CALCULATE", PROJECT_TARGET_TYPE, projectId, request.idempotencyKey(),
				requestFingerprint, id);
		return this.queryService.calculation(id, operator);
	}

	@Transactional
	public ProjectCostQueryService.CalculationResponse recalculate(Long id, VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		String requestFingerprint = fingerprint("RECALCULATE|" + id + "|" + request.version());
		ProjectCostActionRecord existing = idempotentAction(operator, "RECALCULATE", TARGET_TYPE, id,
				request.idempotencyKey());
		if (existing != null) {
			if (!requestFingerprint.equals(existing.requestFingerprint())) {
				throw new BusinessException(ApiErrorCode.PROJECT_COST_IDEMPOTENCY_CONFLICT);
			}
			return this.queryService.calculation(existing.resultId(), operator);
		}
		CalculationState state = lockCalculation(id);
		requireVersion(state, request.version());
		if (!"CALCULATED".equals(state.status())) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_ACTION_NOT_ALLOWED);
		}
		ProjectCostSourceCollector.CollectionResult collected = this.sourceCollector.collect(state.projectId(),
				state.cutoffDate());
		ProjectCostEntryBuilder.EntryBuildResult entries = this.entryBuilder.build(collected.sources(),
				collected.variances());
		Metrics metrics = metrics(collected, entries);
		this.jdbcTemplate.update("""
				delete from prj_cost_variance where calculation_id = ?
				""", id);
		this.jdbcTemplate.update("""
				delete from prj_cost_entry where calculation_id = ?
				""", id);
		this.jdbcTemplate.update("""
				delete from prj_cost_source_line where calculation_id = ?
				""", id);
		this.jdbcTemplate.update("""
				update prj_cost_calculation
				set source_fingerprint = ?, project_cost_total = ?, wip_cost = ?, finished_cost = ?,
				    delivered_cost = ?, direct_project_cost = ?, shipment_revenue = ?, invoice_revenue = ?,
				    target_revenue = ?, shipment_gross_margin = ?, invoice_gross_margin = ?,
				    target_gross_margin = ?, shipment_gross_margin_rate = ?, invoice_gross_margin_rate = ?,
				    target_gross_margin_rate = ?, margin_completeness = ?, completeness_reason = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", collected.sourceFingerprint(), metrics.projectCostTotal(), metrics.wipCost(),
				metrics.finishedCost(), metrics.deliveredCost(), metrics.directProjectCost(),
				collected.revenue().shipmentRevenue(), collected.revenue().invoiceRevenue(),
				collected.revenue().targetRevenue(), metrics.shipmentGrossMargin(), metrics.invoiceGrossMargin(),
				metrics.targetGrossMargin(), grossMarginRate(metrics.shipmentGrossMargin(),
						collected.revenue().shipmentRevenue()),
				grossMarginRate(metrics.invoiceGrossMargin(), collected.revenue().invoiceRevenue()),
				grossMarginRate(metrics.targetGrossMargin(), collected.revenue().targetRevenue()),
				metrics.marginCompleteness(), metrics.completenessReason(), operator.username(), OffsetDateTime.now(),
				id);
		writeSnapshot(id, collected, entries);
		this.auditService.record(operator, "PROJECT_COST_RECALCULATE", TARGET_TYPE, id, state.calculationNo(),
				servletRequest);
		recordAction(operator, "RECALCULATE", TARGET_TYPE, id, request.idempotencyKey(), requestFingerprint, id);
		return this.queryService.calculation(id, operator);
	}

	@Transactional
	public ProjectCostQueryService.CalculationResponse confirm(Long id, ConfirmRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		String requestFingerprint =
				fingerprint("CONFIRM|" + id + "|" + request.version() + "|" + request.sourceFingerprint());
		ProjectCostActionRecord existing =
				idempotentAction(operator, "CONFIRM", TARGET_TYPE, id, request.idempotencyKey());
		if (existing != null) {
			if (!requestFingerprint.equals(existing.requestFingerprint())) {
				throw new BusinessException(ApiErrorCode.PROJECT_COST_IDEMPOTENCY_CONFLICT);
			}
			return this.queryService.calculation(existing.resultId(), operator);
		}
		CalculationState state = lockCalculation(id);
		requireVersion(state, request.version());
		if (!"CALCULATED".equals(state.status())) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_ACTION_NOT_ALLOWED);
		}
		ProjectCostSourceCollector.CollectionResult current = this.sourceCollector.collect(state.projectId(),
				state.cutoffDate());
		if (!state.sourceFingerprint().equals(request.sourceFingerprint())
				|| !state.sourceFingerprint().equals(current.sourceFingerprint())) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_SOURCE_CHANGED);
		}
		if (current.variances().stream()
			.anyMatch((variance) -> "SOURCE_UNPRICED".equals(variance.varianceType())
					&& "LABOR".equals(variance.costCategory()))) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_LABOR_UNPRICED);
		}
		if (current.variances().stream().anyMatch((variance) -> "BLOCKING".equals(variance.severity()))) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_SOURCE_UNVALUED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update prj_cost_calculation
				set is_current = false, updated_by = ?, updated_at = ?, version = version + 1
				where project_id = ?
				and is_current = true
				and id <> ?
				""", operator.username(), now, state.projectId(), id);
		int updated = this.jdbcTemplate.update("""
				update prj_cost_calculation
				set status = 'CONFIRMED', is_current = true, confirmed_by = ?, confirmed_at = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and status = 'CALCULATED'
				""", operator.username(), now, operator.username(), now, id);
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_VERSION_CONFLICT);
		}
		this.auditService.record(operator, "PROJECT_COST_CONFIRM", TARGET_TYPE, id, state.calculationNo(),
				servletRequest);
		recordAction(operator, "CONFIRM", TARGET_TYPE, id, request.idempotencyKey(), requestFingerprint, id);
		return this.queryService.calculation(id, operator);
	}

	@Transactional
	public ProjectCostQueryService.CalculationResponse cancel(Long id, VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		String requestFingerprint = fingerprint("CANCEL|" + id + "|" + request.version());
		ProjectCostActionRecord existing =
				idempotentAction(operator, "CANCEL", TARGET_TYPE, id, request.idempotencyKey());
		if (existing != null) {
			if (!requestFingerprint.equals(existing.requestFingerprint())) {
				throw new BusinessException(ApiErrorCode.PROJECT_COST_IDEMPOTENCY_CONFLICT);
			}
			return this.queryService.calculation(existing.resultId(), operator);
		}
		CalculationState state = lockCalculation(id);
		requireVersion(state, request.version());
		if (!"CALCULATED".equals(state.status()) && !"DRAFT".equals(state.status())) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_ACTION_NOT_ALLOWED);
		}
		this.jdbcTemplate.update("""
				update prj_cost_calculation
				set status = 'CANCELLED', is_current = false, cancelled_by = ?, cancelled_at = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
		this.auditService.record(operator, "PROJECT_COST_CANCEL", TARGET_TYPE, id, state.calculationNo(),
				servletRequest);
		recordAction(operator, "CANCEL", TARGET_TYPE, id, request.idempotencyKey(), requestFingerprint, id);
		return this.queryService.calculation(id, operator);
	}

	private Metrics metrics(ProjectCostSourceCollector.CollectionResult collected,
			ProjectCostEntryBuilder.EntryBuildResult entries) {
		BigDecimal wip = stageTotal(collected.sources(), "WIP");
		BigDecimal finished = stageTotal(collected.sources(), "FINISHED");
		BigDecimal delivered = stageTotal(collected.sources(), "DELIVERED");
		BigDecimal direct = entries.directProjectCost();
		BigDecimal total = wip.add(finished).add(delivered).add(direct).setScale(2, RoundingMode.HALF_UP);
		BigDecimal shipmentMargin = collected.revenue().shipmentRevenue().subtract(total).setScale(2, RoundingMode.HALF_UP);
		BigDecimal invoiceMargin = collected.revenue().invoiceRevenue().subtract(total).setScale(2, RoundingMode.HALF_UP);
		BigDecimal targetMargin = collected.revenue().targetRevenue().subtract(total).setScale(2, RoundingMode.HALF_UP);
		boolean incomplete = wip.compareTo(BigDecimal.ZERO) > 0 || collected.variances()
			.stream()
			.anyMatch((variance) -> "BLOCKING".equals(variance.severity())
					|| "OUTSOURCING_PROVISIONAL".equals(variance.varianceType()));
		return new Metrics(total, wip, finished, delivered, direct, shipmentMargin, invoiceMargin, targetMargin,
				incomplete ? "INCOMPLETE" : "COMPLETE", incomplete ? "存在未闭合来源或差异" : null);
	}

	private BigDecimal stageTotal(List<ProjectCostSourceCollector.SourceLineDraft> sources, String stage) {
		return sources.stream()
			.filter((source) -> stage.equals(source.costStage()))
			.map(ProjectCostSourceCollector.SourceLineDraft::calculatedAmount)
			.reduce(ZERO, BigDecimal::add)
			.setScale(2, RoundingMode.HALF_UP);
	}

	private void writeSnapshot(Long calculationId, ProjectCostSourceCollector.CollectionResult collected,
			ProjectCostEntryBuilder.EntryBuildResult entries) {
		for (ProjectCostSourceCollector.SourceLineDraft source : collected.sources()) {
			this.jdbcTemplate.update("""
					insert into prj_cost_source_line (
						calculation_id, project_id, cost_category, cost_stage, entry_type, source_type,
						source_id, source_line_id, source_no, source_status, business_date, quantity, unit_cost,
						source_amount, calculated_amount, source_fingerprint, source_restricted
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", calculationId, source.projectId(), source.costCategory(), source.costStage(),
					source.entryType(), source.sourceType(), source.sourceId(), source.sourceLineId(), source.sourceNo(),
					source.sourceStatus(), source.businessDate(), source.quantity(), source.unitCost(),
					source.sourceAmount(), source.calculatedAmount(), sourceFingerprint(source),
					source.sourceRestricted());
		}
		for (ProjectCostEntryBuilder.EntryDraft entry : entries.entries()) {
			Long entryId = this.jdbcTemplate.queryForObject("""
					insert into prj_cost_entry (
						calculation_id, entry_type, cost_category, cost_stage, direction, amount, description
					)
					values (?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, calculationId, entry.entryType(), entry.costCategory(), entry.costStage(),
					entry.direction(), entry.amount(), entry.description());
			this.jdbcTemplate.update("""
					insert into prj_cost_entry_line (entry_id, source_line_id, amount, description)
					values (?, null, ?, ?)
					""", entryId, entry.amount(), entry.description());
		}
		for (ProjectCostSourceCollector.VarianceDraft variance : collected.variances()) {
			this.jdbcTemplate.update("""
					insert into prj_cost_variance (
						calculation_id, project_id, variance_type, severity, status, source_restricted,
						variance_amount, description, source_type, source_id, source_line_id, cost_category,
						created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", calculationId, variance.projectId(), variance.varianceType(), variance.severity(),
					variance.status(), variance.sourceRestricted(), variance.varianceAmount(), variance.description(),
					variance.sourceType(), variance.sourceId(), variance.sourceLineId(), variance.costCategory(),
					OffsetDateTime.now(), OffsetDateTime.now());
		}
	}

	private void validateProject(Long projectId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_project
				where id = ?
				and status in ('ACTIVE', 'CLOSED')
				""", Long.class, projectId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_PROJECT_INVALID);
		}
	}

	private ProjectCostActionRecord idempotentAction(CurrentUser operator, String action, String resourceType,
			Long resourceId, String idempotencyKey) {
		if (operator == null || operator.id() == null || !hasText(idempotencyKey)) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select request_fingerprint, result_id
				from prj_cost_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ProjectCostActionRecord(rs.getString("request_fingerprint"),
				rs.getLong("result_id")), operator.id(), action, resourceType, resourceId, idempotencyKey)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private void recordAction(CurrentUser operator, String action, String resourceType, Long resourceId,
			String idempotencyKey, String requestFingerprint, Long resultId) {
		if (operator == null || operator.id() == null || !hasText(idempotencyKey)) {
			return;
		}
		try {
			this.jdbcTemplate.update("""
					insert into prj_cost_action_idempotency (
						operator_user_id, action, resource_type, resource_id, idempotency_key,
						request_fingerprint, result_id
					)
					values (?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), action, resourceType, resourceId, idempotencyKey, requestFingerprint,
					resultId);
		}
		catch (DuplicateKeyException exception) {
			ProjectCostActionRecord existing = idempotentAction(operator, action, resourceType, resourceId,
					idempotencyKey);
			if (existing == null || !requestFingerprint.equals(existing.requestFingerprint())) {
				throw new BusinessException(ApiErrorCode.PROJECT_COST_IDEMPOTENCY_CONFLICT);
			}
		}
	}

	private CalculationState lockCalculation(Long id) {
		return this.jdbcTemplate.query("""
				select id, project_id, calculation_no, cutoff_date, status, source_fingerprint, version
				from prj_cost_calculation
				where id = ?
				for update
				""", this::mapCalculationState, id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_COST_PROJECT_INVALID));
	}

	private CalculationState mapCalculationState(ResultSet rs, int rowNum) throws SQLException {
		return new CalculationState(rs.getLong("id"), rs.getLong("project_id"), rs.getString("calculation_no"),
				rs.getObject("cutoff_date", LocalDate.class), rs.getString("status"),
				rs.getString("source_fingerprint"), rs.getLong("version"));
	}

	private void requireVersion(CalculationState state, Long version) {
		if (!state.version().equals(version)) {
			throw new BusinessException(ApiErrorCode.PROJECT_COST_VERSION_CONFLICT);
		}
	}

	private BigDecimal grossMarginRate(BigDecimal margin, BigDecimal revenue) {
		if (revenue == null || revenue.compareTo(BigDecimal.ZERO) <= 0) {
			return null;
		}
		return margin.divide(revenue, 6, RoundingMode.HALF_UP);
	}

	private String nextCalculationNo() {
		return "PCC" + NUMBER_FORMATTER.format(OffsetDateTime.now()) + String.format("%03d",
				NUMBER_SEQUENCE.updateAndGet((value) -> value >= 999 ? 1 : value + 1));
	}

	private String sourceFingerprint(ProjectCostSourceCollector.SourceLineDraft source) {
		return fingerprint(source.sourceType() + "|" + source.sourceId() + "|" + source.sourceLineId() + "|"
				+ source.costCategory() + "|" + source.entryType() + "|" + source.calculatedAmount() + "|"
				+ source.sourceStatus());
	}

	private String fingerprint(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record CalculationRequest(LocalDate cutoffDate, String idempotencyKey) {
	}

	public record VersionedActionRequest(Long version, String idempotencyKey) {
	}

	public record ConfirmRequest(Long version, String sourceFingerprint, String idempotencyKey) {
	}

	private record CalculationState(Long id, Long projectId, String calculationNo, LocalDate cutoffDate,
			String status, String sourceFingerprint, Long version) {
	}

	private record ProjectCostActionRecord(String requestFingerprint, Long resultId) {
	}

	private record Metrics(BigDecimal projectCostTotal, BigDecimal wipCost, BigDecimal finishedCost,
			BigDecimal deliveredCost, BigDecimal directProjectCost, BigDecimal shipmentGrossMargin,
			BigDecimal invoiceGrossMargin, BigDecimal targetGrossMargin, String marginCompleteness,
			String completenessReason) {
	}

}
