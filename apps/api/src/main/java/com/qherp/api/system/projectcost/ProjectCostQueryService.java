package com.qherp.api.system.projectcost;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectCostQueryService {

	private final JdbcTemplate jdbcTemplate;

	public ProjectCostQueryService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public PageResponse<CalculationResponse> calculations(Long projectId, String status, int page, int pageSize,
			CurrentUser currentUser) {
		QueryParts query = calculationQuery(projectId, status);
		long total = this.jdbcTemplate.queryForObject("select count(*) from prj_cost_calculation c " + query.where(),
				Long.class, query.args().toArray());
		List<Object> args = new ArrayList<>(query.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<CalculationResponse> items = this.jdbcTemplate.query("""
				select c.*, p.project_no, p.name as project_name
				from prj_cost_calculation c
				join sal_project p on p.id = c.project_id
				%s
				order by c.cutoff_date desc, c.id desc
				limit ? offset ?
				""".formatted(query.where()), (rs, rowNum) -> mapCalculation(rs, currentUser), args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public CalculationResponse projectCalculation(Long projectId, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select c.*, p.project_no, p.name as project_name
				from prj_cost_calculation c
				join sal_project p on p.id = c.project_id
				where c.project_id = ?
				order by c.is_current desc, c.cutoff_date desc, c.id desc
				limit 1
				""", (rs, rowNum) -> mapCalculation(rs, currentUser), projectId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_COST_PROJECT_INVALID));
	}

	@Transactional(readOnly = true)
	public CalculationResponse calculation(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select c.*, p.project_no, p.name as project_name
				from prj_cost_calculation c
				join sal_project p on p.id = c.project_id
				where c.id = ?
				""", (rs, rowNum) -> mapCalculation(rs, currentUser), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_COST_PROJECT_INVALID));
	}

	@Transactional(readOnly = true)
	public PageResponse<SourceLineResponse> sources(Long calculationId, int page, int pageSize,
			CurrentUser currentUser) {
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from prj_cost_source_line
				where calculation_id = ?
				""", Long.class, calculationId);
		List<SourceLineResponse> items = this.jdbcTemplate.query("""
				select *
				from prj_cost_source_line
				where calculation_id = ?
				order by id
				limit ? offset ?
				""", (rs, rowNum) -> mapSourceLine(rs, currentUser), calculationId, limit(pageSize),
				offset(page, pageSize));
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<EntryResponse> entries(Long calculationId, int page, int pageSize) {
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from prj_cost_entry
				where calculation_id = ?
				""", Long.class, calculationId);
		List<EntryResponse> items = this.jdbcTemplate.query("""
				select *
				from prj_cost_entry
				where calculation_id = ?
				order by id
				limit ? offset ?
				""", this::mapEntry, calculationId, limit(pageSize), offset(page, pageSize));
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<VarianceResponse> variances(Long calculationId, int page, int pageSize,
			CurrentUser currentUser) {
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from prj_cost_variance
				where calculation_id = ?
				""", Long.class, calculationId);
		List<VarianceResponse> items = this.jdbcTemplate.query("""
				select *
				from prj_cost_variance
				where calculation_id = ?
				order by id
				limit ? offset ?
				""", (rs, rowNum) -> mapVariance(rs, currentUser), calculationId, limit(pageSize),
				offset(page, pageSize));
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<VarianceResponse> variances(Long projectId, String severity, String type, String status,
			Boolean sourceRestricted, int page, int pageSize, CurrentUser currentUser) {
		QueryParts query = varianceQuery(projectId, severity, type, status, sourceRestricted);
		long total = this.jdbcTemplate.queryForObject("select count(*) from prj_cost_variance v " + query.where(),
				Long.class, query.args().toArray());
		List<Object> args = new ArrayList<>(query.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<VarianceResponse> items = this.jdbcTemplate.query("""
				select v.*
				from prj_cost_variance v
				%s
				order by v.id desc
				limit ? offset ?
				""".formatted(query.where()), (rs, rowNum) -> mapVariance(rs, currentUser), args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	private QueryParts calculationQuery(Long projectId, String status) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (projectId != null) {
			conditions.add("c.project_id = ?");
			args.add(projectId);
		}
		if (hasText(status)) {
			conditions.add("c.status = ?");
			args.add(status.trim().toUpperCase());
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private QueryParts varianceQuery(Long projectId, String severity, String type, String status,
			Boolean sourceRestricted) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (projectId != null) {
			conditions.add("v.project_id = ?");
			args.add(projectId);
		}
		if (hasText(severity)) {
			conditions.add("v.severity = ?");
			args.add(severity.trim().toUpperCase());
		}
		if (hasText(type)) {
			conditions.add("v.variance_type = ?");
			args.add(type.trim().toUpperCase());
		}
		if (hasText(status)) {
			conditions.add("v.status = ?");
			args.add(status.trim().toUpperCase());
		}
		if (sourceRestricted != null) {
			conditions.add("v.source_restricted = ?");
			args.add(sourceRestricted);
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private CalculationResponse mapCalculation(ResultSet rs, CurrentUser currentUser) throws SQLException {
		boolean amountVisible = amountVisible(currentUser);
		BigDecimal projectCostTotal = rs.getBigDecimal("project_cost_total");
		BigDecimal shipmentRevenue = rs.getBigDecimal("shipment_revenue");
		BigDecimal invoiceRevenue = rs.getBigDecimal("invoice_revenue");
		BigDecimal targetRevenue = rs.getBigDecimal("target_revenue");
		return new CalculationResponse(rs.getLong("id"), rs.getLong("project_id"), rs.getString("project_no"),
				rs.getString("project_name"), rs.getString("calculation_no"),
				rs.getObject("cutoff_date", LocalDate.class), rs.getString("status"), rs.getBoolean("is_current"),
				rs.getString("source_fingerprint"), amount(amountVisible, projectCostTotal),
				amount(amountVisible, rs.getBigDecimal("wip_cost")), amount(amountVisible, rs.getBigDecimal("finished_cost")),
				amount(amountVisible, rs.getBigDecimal("delivered_cost")),
				amount(amountVisible, rs.getBigDecimal("direct_project_cost")),
				amount(amountVisible, shipmentRevenue), amount(amountVisible, invoiceRevenue),
				amount(amountVisible, targetRevenue), amount(amountVisible, rs.getBigDecimal("shipment_gross_margin")),
				amount(amountVisible, rs.getBigDecimal("invoice_gross_margin")),
				amount(amountVisible, rs.getBigDecimal("target_gross_margin")),
				rs.getString("margin_completeness"), rs.getString("completeness_reason"), amountVisible,
				rs.getLong("version"), rs.getObject("created_at", OffsetDateTime.class),
				allowedCalculationActions(rs.getString("status")));
	}

	private SourceLineResponse mapSourceLine(ResultSet rs, CurrentUser currentUser) throws SQLException {
		boolean amountVisible = amountVisible(currentUser);
		return new SourceLineResponse(rs.getLong("id"), rs.getLong("calculation_id"), rs.getLong("project_id"),
				rs.getString("cost_category"), rs.getString("cost_stage"), rs.getString("entry_type"),
				rs.getString("source_type"), rs.getLong("source_id"), nullableLong(rs, "source_line_id"),
				rs.getString("source_no"), rs.getString("source_status"),
				rs.getObject("business_date", LocalDate.class), decimal(rs.getBigDecimal("quantity")),
				amount(amountVisible, rs.getBigDecimal("unit_cost")), amount(amountVisible, rs.getBigDecimal("source_amount")),
				amount(amountVisible, rs.getBigDecimal("calculated_amount")), rs.getBoolean("source_restricted"),
				amountVisible);
	}

	private EntryResponse mapEntry(ResultSet rs, int rowNum) throws SQLException {
		return new EntryResponse(rs.getLong("id"), rs.getLong("calculation_id"), rs.getString("entry_type"),
				rs.getString("cost_category"), rs.getString("cost_stage"), rs.getString("direction"),
				decimal(rs.getBigDecimal("amount")), rs.getString("description"));
	}

	private VarianceResponse mapVariance(ResultSet rs, CurrentUser currentUser) throws SQLException {
		boolean amountVisible = amountVisible(currentUser);
		return new VarianceResponse(rs.getLong("id"), rs.getLong("calculation_id"), rs.getLong("project_id"),
				rs.getString("variance_type"), rs.getString("severity"), rs.getString("status"),
				rs.getBoolean("source_restricted"), amount(amountVisible, rs.getBigDecimal("variance_amount")),
				rs.getString("description"), rs.getString("source_type"), nullableLong(rs, "source_id"),
				nullableLong(rs, "source_line_id"), rs.getString("cost_category"), amountVisible);
	}

	private List<String> allowedCalculationActions(String status) {
		if ("CALCULATED".equals(status)) {
			return List.of("RECALCULATE", "CONFIRM", "CANCEL");
		}
		return List.of();
	}

	private boolean amountVisible(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("cost:project-cost:amount-view");
	}

	private String amount(boolean amountVisible, BigDecimal amount) {
		return amountVisible ? decimal(amount) : null;
	}

	private String decimal(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private record QueryParts(String where, List<Object> args) {
	}

	public record CalculationResponse(Long id, Long projectId, String projectNo, String projectName,
			String calculationNo, LocalDate cutoffDate, String status, boolean isCurrent, String sourceFingerprint,
			String projectCostTotal, String wipCost, String finishedCost, String deliveredCost,
			String directProjectCost, String shipmentRevenue, String invoiceRevenue, String targetRevenue,
			String shipmentGrossMargin, String invoiceGrossMargin, String targetGrossMargin,
			String marginCompleteness, String completenessReason, boolean amountVisible, Long version,
			OffsetDateTime createdAt, List<String> allowedActions) {
	}

	public record SourceLineResponse(Long id, Long calculationId, Long projectId, String costCategory,
			String costStage, String entryType, String sourceType, Long sourceId, Long sourceLineId, String sourceNo,
			String sourceStatus, LocalDate businessDate, String quantity, String unitCost, String sourceAmount,
			String calculatedAmount, boolean sourceRestricted, boolean amountVisible) {
	}

	public record EntryResponse(Long id, Long calculationId, String entryType, String costCategory, String costStage,
			String direction, String amount, String description) {
	}

	public record VarianceResponse(Long id, Long calculationId, Long projectId, String varianceType,
			String severity, String status, boolean sourceRestricted, String varianceAmount, String description,
			String sourceType, Long sourceId, Long sourceLineId, String costCategory, boolean amountVisible) {
	}

}
