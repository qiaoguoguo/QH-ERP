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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectCostQueryService {

	private final JdbcTemplate jdbcTemplate;

	public ProjectCostQueryService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public PageResponse<WorkbenchResponse> calculations(ProjectCostListFilter filter, int page, int pageSize,
			CurrentUser currentUser) {
		QueryParts query = projectWorkbenchQuery(filter);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_project p
				left join mst_customer cst on cst.id = p.customer_id
				left join sys_user owner_user on owner_user.id = p.owner_user_id
				left join lateral (
					select c.*
					from prj_cost_calculation c
					where c.project_id = p.id
					order by c.is_current desc, c.cutoff_date desc, c.id desc
					limit 1
				) latest on true
				%s
				""".formatted(query.where()), Long.class, query.args().toArray());
		List<Object> args = new ArrayList<>(query.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<WorkbenchResponse> items = this.jdbcTemplate.query("""
				select p.id as project_id, p.project_no, p.name as project_name, p.status as project_status,
				       cst.name as customer_name, owner_user.display_name as owner_display_name,
				       latest.id as calculation_id, latest.calculation_no, latest.cutoff_date, latest.status,
				       latest.is_current, latest.source_fingerprint, latest.project_cost_total, latest.wip_cost,
				       latest.finished_cost, latest.delivered_cost, latest.direct_project_cost,
				       latest.shipment_revenue, latest.invoice_revenue, latest.target_revenue,
				       latest.shipment_gross_margin, latest.invoice_gross_margin, latest.target_gross_margin,
				       latest.shipment_gross_margin_rate, latest.invoice_gross_margin_rate,
				       latest.target_gross_margin_rate, latest.margin_completeness, latest.completeness_reason,
				       latest.version, latest.created_by, latest.created_at, latest.confirmed_by, latest.confirmed_at
				from sal_project p
				left join mst_customer cst on cst.id = p.customer_id
				left join sys_user owner_user on owner_user.id = p.owner_user_id
				left join lateral (
					select c.*
					from prj_cost_calculation c
					where c.project_id = p.id
					order by c.is_current desc, c.cutoff_date desc, c.id desc
					limit 1
				) latest on true
				%s
				order by coalesce(latest.cutoff_date, p.planned_finish_date) desc, p.id desc
				limit ? offset ?
				""".formatted(query.where()), (rs, rowNum) -> mapWorkbench(rs, currentUser), args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public ProjectDetailResponse projectCalculation(Long projectId, CurrentUser currentUser) {
		ProjectRow project = this.jdbcTemplate.query("""
				select p.id, p.project_no, p.name as project_name, p.status as project_status,
				       c.name as customer_name, u.display_name as owner_display_name, p.target_revenue
				from sal_project p
				left join mst_customer c on c.id = p.customer_id
				left join sys_user u on u.id = p.owner_user_id
				where p.id = ?
				and p.status in ('ACTIVE', 'CLOSED')
				""", this::mapProject, projectId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_COST_PROJECT_INVALID));
		CalculationRow latest = latestCalculation(projectId);
		if (latest == null) {
			boolean amountVisible = amountVisible(currentUser);
			boolean sourceVisible = sourcePermissionVisible(currentUser);
			return ProjectDetailResponse.empty(project.id(), project.projectNo(), project.projectName(),
					project.customerName(), project.ownerDisplayName(), project.projectStatus(),
					amountVisible, sourceVisible, restrictedReason(amountVisible, sourceVisible),
					allowedProjectActions(project.projectStatus(), currentUser),
					projectActionDisabledReasons(project.projectStatus(), currentUser));
		}
		CalculationResponse calculation = calculation(latest.id(), currentUser);
		return ProjectDetailResponse.from(calculation, categorySummaries(latest.id(), currentUser),
				stageSummaries(latest.id(), currentUser), calculationSummaries(projectId), auditSummary(projectId));
	}

	@Transactional(readOnly = true)
	public CalculationResponse calculation(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select c.id as calculation_id, c.project_id, p.project_no, p.name as project_name,
				       p.status as project_status, cst.name as customer_name,
				       owner_user.display_name as owner_display_name, c.calculation_no, c.cutoff_date,
				       c.status, c.is_current, c.source_fingerprint, c.project_cost_total, c.wip_cost,
				       c.finished_cost, c.delivered_cost, c.direct_project_cost, c.shipment_revenue,
				       c.invoice_revenue, c.target_revenue, c.shipment_gross_margin, c.invoice_gross_margin,
				       c.target_gross_margin, c.shipment_gross_margin_rate, c.invoice_gross_margin_rate,
				       c.target_gross_margin_rate, c.margin_completeness, c.completeness_reason,
				       c.version, c.created_by, c.created_at, c.confirmed_by, c.confirmed_at
				from prj_cost_calculation c
				join sal_project p on p.id = c.project_id
				left join mst_customer cst on cst.id = p.customer_id
				left join sys_user owner_user on owner_user.id = p.owner_user_id
				where c.id = ?
				""", (rs, rowNum) -> mapCalculation(rs, currentUser), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_COST_PROJECT_INVALID));
	}

	@Transactional(readOnly = true)
	public PageResponse<SourceLineResponse> sources(Long calculationId, SourceListFilter filter, int page,
			int pageSize, CurrentUser currentUser) {
		QueryParts query = sourceQuery(calculationId, filter);
		long total = this.jdbcTemplate.queryForObject("select count(*) from prj_cost_source_line s " + query.where(),
				Long.class, query.args().toArray());
		List<Object> args = new ArrayList<>(query.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<SourceLineResponse> items = this.jdbcTemplate.query("""
				select s.*
				from prj_cost_source_line s
				%s
				order by s.id
				limit ? offset ?
				""".formatted(query.where()), (rs, rowNum) -> mapSourceLine(rs, currentUser), args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<EntryResponse> entries(Long calculationId, EntryListFilter filter, int page, int pageSize,
			CurrentUser currentUser) {
		QueryParts query = entryQuery(calculationId, filter);
		long total = this.jdbcTemplate.queryForObject("select count(*) from prj_cost_entry e " + query.where(),
				Long.class, query.args().toArray());
		List<Object> args = new ArrayList<>(query.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<EntryResponse> items = this.jdbcTemplate.query("""
				select e.*, (
					select count(*)
					from prj_cost_entry_line l
					where l.entry_id = e.id
				) as source_count
				from prj_cost_entry e
				%s
				order by e.id
				limit ? offset ?
				""".formatted(query.where()), (rs, rowNum) -> mapEntry(rs, currentUser), args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<VarianceResponse> variances(Long calculationId, VarianceListFilter filter, int page,
			int pageSize, CurrentUser currentUser) {
		QueryParts query = varianceQuery(calculationId, filter);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from prj_cost_variance v
				left join lateral (
					select s.source_no, s.business_date
					from prj_cost_source_line s
					where s.calculation_id = v.calculation_id
					and s.source_type = v.source_type
					and s.source_id = v.source_id
					and coalesce(s.source_line_id, 0) = coalesce(v.source_line_id, 0)
					order by s.id
					limit 1
				) s on true
				%s
				""".formatted(query.where()), Long.class, query.args().toArray());
		List<Object> args = new ArrayList<>(query.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<VarianceResponse> items = this.jdbcTemplate.query("""
				select v.*, p.project_no, p.name as project_name, s.source_no, s.business_date
				from prj_cost_variance v
				join sal_project p on p.id = v.project_id
				left join lateral (
					select s.source_no, s.business_date
					from prj_cost_source_line s
					where s.calculation_id = v.calculation_id
					and s.source_type = v.source_type
					and s.source_id = v.source_id
					and coalesce(s.source_line_id, 0) = coalesce(v.source_line_id, 0)
					order by s.id
					limit 1
				) s on true
				%s
				order by v.id desc
				limit ? offset ?
				""".formatted(query.where()), (rs, rowNum) -> mapVariance(rs, currentUser), args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	private QueryParts projectWorkbenchQuery(ProjectCostListFilter filter) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (filter == null) {
			filter = new ProjectCostListFilter(null, null, null, null, null, null, null, null, null, null);
		}
		conditions.add("p.status in ('ACTIVE', 'CLOSED')");
		if (filter.projectId() != null) {
			conditions.add("p.id = ?");
			args.add(filter.projectId());
		}
		if (hasText(filter.keyword())) {
			conditions.add("(p.project_no ilike ? or p.name ilike ? or cst.name ilike ? or latest.calculation_no ilike ?)");
			String keyword = "%" + filter.keyword().trim() + "%";
			args.add(keyword);
			args.add(keyword);
			args.add(keyword);
			args.add(keyword);
		}
		if (filter.ownerUserId() != null) {
			conditions.add("p.owner_user_id = ?");
			args.add(filter.ownerUserId());
		}
		if (hasText(filter.projectStatus())) {
			String projectStatus = filter.projectStatus().trim().toUpperCase();
			if (List.of("ACTIVE", "CLOSED").contains(projectStatus)) {
				conditions.add("p.status = ?");
				args.add(projectStatus);
			}
			else {
				conditions.add("1 = 0");
			}
		}
		if (hasText(filter.calculationStatus())) {
			conditions.add("latest.status = ?");
			args.add(filter.calculationStatus().trim().toUpperCase());
		}
		if (hasText(filter.freshnessStatus())) {
			if ("CURRENT".equals(filter.freshnessStatus().trim().toUpperCase())) {
				conditions.add("(latest.id is null or latest.is_current = true)");
			}
			else {
				conditions.add("latest.id is not null and latest.is_current = false");
			}
		}
		if (hasText(filter.varianceStatus())) {
			conditions.add("""
					exists (
						select 1
						from prj_cost_variance v
						where v.calculation_id = latest.id
						and v.status = ?
					)
					""");
			args.add(filter.varianceStatus().trim().toUpperCase());
		}
		if (hasText(filter.completenessStatus())) {
			conditions.add("latest.margin_completeness = ?");
			args.add(filter.completenessStatus().trim().toUpperCase());
		}
		if (filter.cutoffDateFrom() != null) {
			conditions.add("latest.cutoff_date >= ?");
			args.add(filter.cutoffDateFrom());
		}
		if (filter.cutoffDateTo() != null) {
			conditions.add("latest.cutoff_date <= ?");
			args.add(filter.cutoffDateTo());
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private QueryParts sourceQuery(Long calculationId, SourceListFilter filter) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("s.calculation_id = ?");
		args.add(calculationId);
		if (filter != null) {
			if (hasText(filter.category())) {
				conditions.add("s.cost_category = ?");
				args.add(filter.category().trim().toUpperCase());
			}
			if (hasText(filter.stage())) {
				conditions.add("s.cost_stage = ?");
				args.add(filter.stage().trim().toUpperCase());
			}
			if (hasText(filter.sourceStatus())) {
				conditions.add("s.source_status = ?");
				args.add(filter.sourceStatus().trim().toUpperCase());
			}
			if (hasText(filter.sourceType())) {
				conditions.add("s.source_type = ?");
				args.add(filter.sourceType().trim().toUpperCase());
			}
			if (filter.projectId() != null) {
				conditions.add("s.project_id = ?");
				args.add(filter.projectId());
			}
			if (filter.businessDateFrom() != null) {
				conditions.add("s.business_date >= ?");
				args.add(filter.businessDateFrom());
			}
			if (filter.businessDateTo() != null) {
				conditions.add("s.business_date <= ?");
				args.add(filter.businessDateTo());
			}
			if (filter.sourceRestricted() != null) {
				conditions.add("s.source_restricted = ?");
				args.add(filter.sourceRestricted());
			}
		}
		return new QueryParts("where " + String.join(" and ", conditions), args);
	}

	private QueryParts entryQuery(Long calculationId, EntryListFilter filter) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("e.calculation_id = ?");
		args.add(calculationId);
		if (filter != null) {
			if (hasText(filter.category())) {
				conditions.add("e.cost_category = ?");
				args.add(filter.category().trim().toUpperCase());
			}
			if (hasText(filter.stage())) {
				conditions.add("e.cost_stage = ?");
				args.add(filter.stage().trim().toUpperCase());
			}
		}
		return new QueryParts("where " + String.join(" and ", conditions), args);
	}

	private QueryParts varianceQuery(Long calculationId, VarianceListFilter filter) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (calculationId != null) {
			conditions.add("v.calculation_id = ?");
			args.add(calculationId);
		}
		if (filter != null) {
			if (filter.projectId() != null) {
				conditions.add("v.project_id = ?");
				args.add(filter.projectId());
			}
			if (hasText(filter.severity())) {
				conditions.add("v.severity = ?");
				args.add(filter.severity().trim().toUpperCase());
			}
			if (hasText(filter.varianceType())) {
				conditions.add("v.variance_type = ?");
				args.add(filter.varianceType().trim().toUpperCase());
			}
			if (hasText(filter.status())) {
				conditions.add("v.status = ?");
				args.add(filter.status().trim().toUpperCase());
			}
			if (hasText(filter.sourceType())) {
				conditions.add("v.source_type = ?");
				args.add(filter.sourceType().trim().toUpperCase());
			}
			if (filter.businessDateFrom() != null) {
				conditions.add("s.business_date >= ?");
				args.add(filter.businessDateFrom());
			}
			if (filter.businessDateTo() != null) {
				conditions.add("s.business_date <= ?");
				args.add(filter.businessDateTo());
			}
			if (filter.sourceRestricted() != null) {
				conditions.add("v.source_restricted = ?");
				args.add(filter.sourceRestricted());
			}
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private WorkbenchResponse mapWorkbench(ResultSet rs, CurrentUser currentUser) throws SQLException {
		CalculationRow row = mapOptionalCalculation(rs);
		boolean amountVisible = amountVisible(currentUser);
		boolean sourceVisible = sourcePermissionVisible(currentUser);
		Long projectId = rs.getLong("project_id");
		Long calculationId = row == null ? null : row.id();
		String status = row == null ? "DRAFT" : row.status();
		String projectStatus = rs.getString("project_status");
		Integer openVarianceCount = row == null ? 0 : varianceCount(calculationId, "OPEN", null);
		Integer blockingVarianceCount = row == null ? 0 : varianceCount(calculationId, "OPEN", "BLOCKING");
		Integer provisionalSourceCount = row == null ? 0 : sourceStatusCount(calculationId, "PROVISIONAL");
		Integer unpricedSourceCount = row == null ? 0 : sourceStatusCount(calculationId, "UNPRICED");
		CalculationActionContext actionContext = row == null ? null
				: new CalculationActionContext(status, row.marginCompleteness(), blockingVarianceCount,
						unpricedSourceCount, deliveryUnmatchedCount(calculationId));
		return new WorkbenchResponse(projectId, rs.getString("project_no"), rs.getString("project_name"),
				rs.getString("customer_name"), rs.getString("owner_display_name"), projectStatus,
				calculationId, row == null ? null : row.calculationNo(), status, status,
				row == null ? "CURRENT" : freshnessStatus(row.isCurrent()), row == null ? "COMPLETE" : row.marginCompleteness(),
				row == null ? "COMPLETE" : row.marginCompleteness(), row == null ? null : row.cutoffDate(),
				amount(amountVisible, row == null ? null : row.projectCostTotal()),
				amount(amountVisible, row == null ? null : row.projectCostTotal()),
				amount(amountVisible, row == null ? null : row.wipCost()),
				amount(amountVisible, row == null ? null : row.finishedCost()),
				amount(amountVisible, row == null ? null : row.deliveredCost()),
				amount(amountVisible, row == null ? null : row.directProjectCost()),
				amount(amountVisible, row == null ? null : row.shipmentRevenue()),
				amount(amountVisible, row == null ? null : row.shipmentRevenue()),
				amount(amountVisible, row == null ? null : row.invoiceRevenue()),
				amount(amountVisible, row == null ? null : row.invoiceRevenue()),
				amount(amountVisible, row == null ? null : row.targetRevenue()),
				amount(amountVisible, row == null ? null : row.shipmentGrossMargin()),
				amount(amountVisible, row == null ? null : row.invoiceGrossMargin()),
				amount(amountVisible, row == null ? null : row.targetGrossMargin()),
				amount(amountVisible, row == null ? null : row.shipmentGrossMarginRate()),
				amount(amountVisible, row == null ? null : row.invoiceGrossMarginRate()),
				amount(amountVisible, row == null ? null : row.targetGrossMarginRate()),
				row == null ? null : row.sourceFingerprint(), row == null ? 0L : row.version(), amountVisible,
				sourceVisible, restrictedReason(amountVisible, sourceVisible),
				openVarianceCount, blockingVarianceCount, provisionalSourceCount, unpricedSourceCount,
				row == null ? null : row.createdAt(),
				row == null ? allowedProjectActions(projectStatus, currentUser)
						: allowedCalculationActions(actionContext, currentUser),
				row == null ? projectActionDisabledReasons(projectStatus, currentUser)
						: actionDisabledReasons(actionContext, currentUser));
	}

	private CalculationResponse mapCalculation(ResultSet rs, CurrentUser currentUser) throws SQLException {
		CalculationRow row = mapRequiredCalculation(rs);
		boolean amountVisible = amountVisible(currentUser);
		boolean sourceVisible = sourcePermissionVisible(currentUser);
		Integer openVarianceCount = varianceCount(row.id(), "OPEN", null);
		Integer blockingVarianceCount = varianceCount(row.id(), "OPEN", "BLOCKING");
		Integer provisionalSourceCount = sourceStatusCount(row.id(), "PROVISIONAL");
		Integer unpricedSourceCount = sourceStatusCount(row.id(), "UNPRICED");
		CalculationActionContext actionContext = new CalculationActionContext(row.status(), row.marginCompleteness(),
				blockingVarianceCount, unpricedSourceCount, deliveryUnmatchedCount(row.id()));
		return new CalculationResponse(row.id(), row.id(), row.projectId(), rs.getString("project_no"),
				rs.getString("project_name"), rs.getString("customer_name"), rs.getString("owner_display_name"),
				rs.getString("project_status"), row.calculationNo(), row.cutoffDate(), row.status(), row.status(),
				freshnessStatus(row.isCurrent()), row.marginCompleteness(), row.marginCompleteness(),
				row.completenessReason(), row.isCurrent(), row.sourceFingerprint(),
				amount(amountVisible, row.projectCostTotal()), amount(amountVisible, row.projectCostTotal()),
				amount(amountVisible, row.wipCost()), amount(amountVisible, row.finishedCost()),
				amount(amountVisible, row.deliveredCost()), amount(amountVisible, row.directProjectCost()),
				amount(amountVisible, row.shipmentRevenue()), amount(amountVisible, row.shipmentRevenue()),
				amount(amountVisible, row.invoiceRevenue()), amount(amountVisible, row.invoiceRevenue()),
				amount(amountVisible, row.targetRevenue()), amount(amountVisible, row.shipmentGrossMargin()),
				amount(amountVisible, row.invoiceGrossMargin()), amount(amountVisible, row.targetGrossMargin()),
				amount(amountVisible, row.shipmentGrossMarginRate()), amount(amountVisible, row.invoiceGrossMarginRate()),
				amount(amountVisible, row.targetGrossMarginRate()), row.version(), amountVisible, sourceVisible,
				restrictedReason(amountVisible, sourceVisible), openVarianceCount, blockingVarianceCount,
				provisionalSourceCount, unpricedSourceCount, row.createdBy(), row.createdAt(), row.createdAt(),
				row.confirmedBy(), row.confirmedAt(), allowedCalculationActions(actionContext, currentUser),
				actionDisabledReasons(actionContext, currentUser));
	}

	private SourceLineResponse mapSourceLine(ResultSet rs, CurrentUser currentUser) throws SQLException {
		String sourceType = rs.getString("source_type");
		boolean amountVisible = amountVisible(currentUser, sourceType);
		boolean sourceVisible = sourceVisible(currentUser, sourceType, rs.getBoolean("source_restricted"));
		String restrictedReason = sourceVisible ? restrictedReason(amountVisible, true, sourceType)
				: "来源权限受限，仅显示脱敏摘要";
		String costCategory = rs.getString("cost_category");
		String costStage = rs.getString("cost_stage");
		Long visibleSourceId = sourceVisible ? rs.getLong("source_id") : null;
		if (sourceVisible) {
			long value = rs.getLong("source_id");
			visibleSourceId = rs.wasNull() ? null : value;
		}
		return new SourceLineResponse(rs.getLong("id"), rs.getLong("calculation_id"), rs.getLong("project_id"),
				costCategory, costCategory, costStage, costStage, rs.getString("entry_type"), sourceType,
				visibleSourceId, sourceVisible ? nullableLong(rs, "source_line_id") : null,
				sourceVisible ? rs.getString("source_no") : null, sourceVisible ? rs.getString("source_status") : "RESTRICTED",
				rs.getObject("business_date", LocalDate.class), sourceVisible ? decimal(rs.getBigDecimal("quantity")) : null,
				sourceVisible && amountVisible ? amount(true, rs.getBigDecimal("unit_cost")) : null,
				sourceVisible && amountVisible ? amount(true, rs.getBigDecimal("unit_cost")) : null,
				sourceVisible && amountVisible ? amount(true, rs.getBigDecimal("source_amount")) : null,
				sourceVisible && amountVisible ? amount(true, rs.getBigDecimal("calculated_amount")) : null,
				sourceVisible && amountVisible ? amount(true, rs.getBigDecimal("calculated_amount")) : null,
				rs.getBoolean("source_restricted"), amountVisible, sourceVisible, restrictedReason,
				sourceVisible ? sourceSummary(sourceType, rs.getString("source_no")) : "已纳入一条受限来源",
				sourceVisible ? sourceRoute(sourceType, visibleSourceId, nullableLong(rs, "source_line_id")) : null,
				null, null, null);
	}

	private EntryResponse mapEntry(ResultSet rs, CurrentUser currentUser) throws SQLException {
		boolean amountVisible = amountVisible(currentUser);
		String category = rs.getString("cost_category");
		String stage = rs.getString("cost_stage");
		return new EntryResponse(rs.getLong("id"), rs.getLong("calculation_id"), rs.getString("entry_type"),
				category, category, stage, stage, rs.getString("direction"), amount(amountVisible, rs.getBigDecimal("amount")),
				rs.getString("description"), rs.getInt("source_count"), amountVisible);
	}

	private VarianceResponse mapVariance(ResultSet rs, CurrentUser currentUser) throws SQLException {
		boolean amountVisible = amountVisible(currentUser);
		boolean sourceVisible = sourceVisible(currentUser, rs.getString("source_type"), rs.getBoolean("source_restricted"));
		String restrictedReason = sourceVisible ? restrictedReason(amountVisible, true) : "来源权限受限，仅显示脱敏摘要";
		String amount = amount(amountVisible, rs.getBigDecimal("variance_amount"));
		Long sourceId = sourceVisible ? nullableLong(rs, "source_id") : null;
		Long sourceLineId = sourceVisible ? nullableLong(rs, "source_line_id") : null;
		String sourceNo = sourceVisible ? rs.getString("source_no") : null;
		return new VarianceResponse(rs.getLong("id"), rs.getLong("calculation_id"), rs.getLong("project_id"),
				rs.getString("project_no"), rs.getString("project_name"), rs.getString("variance_type"),
				rs.getString("severity"), rs.getString("status"), rs.getBoolean("source_restricted"),
				amount, null, null, amount, rs.getString("description"), rs.getString("source_type"), sourceId,
				sourceLineId, sourceNo, sourceVisible ? sourceSummary(rs.getString("source_type"), sourceNo) : "已纳入一条受限来源",
				rs.getString("cost_category"), amountVisible, sourceVisible, restrictedReason, null);
	}

	private List<CategorySummaryResponse> categorySummaries(Long calculationId, CurrentUser currentUser) {
		boolean amountVisible = amountVisible(currentUser);
		return this.jdbcTemplate.query("""
				select cost_category, coalesce(sum(calculated_amount), 0) as amount, count(*) as source_count
				from prj_cost_source_line
				where calculation_id = ?
				group by cost_category
				order by cost_category
				""", (rs, rowNum) -> new CategorySummaryResponse(rs.getString("cost_category"),
				rs.getString("cost_category"), amount(amountVisible, rs.getBigDecimal("amount")),
				rs.getInt("source_count")), calculationId);
	}

	private List<StageSummaryResponse> stageSummaries(Long calculationId, CurrentUser currentUser) {
		boolean amountVisible = amountVisible(currentUser);
		return this.jdbcTemplate.query("""
				select cost_stage, coalesce(sum(calculated_amount), 0) as amount
				from prj_cost_source_line
				where calculation_id = ?
				group by cost_stage
				order by cost_stage
				""", (rs, rowNum) -> new StageSummaryResponse(rs.getString("cost_stage"), rs.getString("cost_stage"),
				amount(amountVisible, rs.getBigDecimal("amount"))), calculationId);
	}

	private List<CalculationSummaryResponse> calculationSummaries(Long projectId) {
		return this.jdbcTemplate.query("""
				select id, calculation_no, status, cutoff_date, created_at
				from prj_cost_calculation
				where project_id = ?
				order by cutoff_date desc, id desc
				limit 20
				""", (rs, rowNum) -> new CalculationSummaryResponse(rs.getLong("id"), rs.getString("calculation_no"),
				rs.getString("status"), rs.getString("status"), rs.getObject("cutoff_date", LocalDate.class),
				rs.getObject("created_at", OffsetDateTime.class)), projectId);
	}

	private List<AuditSummaryResponse> auditSummary(Long projectId) {
		return this.jdbcTemplate.query("""
				select l.action, l.operator_username, l.created_at, l.target_summary
				from sys_audit_log l
				join prj_cost_calculation c on c.id::text = l.target_id
				where c.project_id = ?
				and l.target_type = 'PROJECT_COST_CALCULATION'
				order by l.created_at desc, l.id desc
				limit 20
				""", (rs, rowNum) -> new AuditSummaryResponse(rs.getString("action"),
				rs.getString("operator_username"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getString("target_summary")), projectId);
	}

	private CalculationRow latestCalculation(Long projectId) {
		return this.jdbcTemplate.query("""
				select id as calculation_id, project_id, calculation_no, cutoff_date, status, is_current,
				       source_fingerprint, project_cost_total, wip_cost, finished_cost, delivered_cost,
				       direct_project_cost, shipment_revenue, invoice_revenue, target_revenue,
				       shipment_gross_margin, invoice_gross_margin, target_gross_margin,
				       shipment_gross_margin_rate, invoice_gross_margin_rate, target_gross_margin_rate,
				       margin_completeness, completeness_reason, version, created_by, created_at, confirmed_by,
				       confirmed_at
				from prj_cost_calculation
				where project_id = ?
				order by is_current desc, cutoff_date desc, id desc
				limit 1
				""", (rs, rowNum) -> mapRequiredCalculation(rs), projectId)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private ProjectRow mapProject(ResultSet rs, int rowNum) throws SQLException {
		return new ProjectRow(rs.getLong("id"), rs.getString("project_no"), rs.getString("project_name"),
				rs.getString("customer_name"), rs.getString("owner_display_name"), rs.getString("project_status"),
				rs.getBigDecimal("target_revenue"));
	}

	private CalculationRow mapOptionalCalculation(ResultSet rs) throws SQLException {
		Long id = nullableLong(rs, "calculation_id");
		return id == null ? null : mapCalculationRow(rs, id);
	}

	private CalculationRow mapRequiredCalculation(ResultSet rs) throws SQLException {
		return mapCalculationRow(rs, rs.getLong("calculation_id"));
	}

	private CalculationRow mapCalculationRow(ResultSet rs, Long id) throws SQLException {
		return new CalculationRow(id, rs.getLong("project_id"), rs.getString("calculation_no"),
				rs.getObject("cutoff_date", LocalDate.class), rs.getString("status"), rs.getBoolean("is_current"),
				rs.getString("source_fingerprint"), rs.getBigDecimal("project_cost_total"),
				rs.getBigDecimal("wip_cost"), rs.getBigDecimal("finished_cost"), rs.getBigDecimal("delivered_cost"),
				rs.getBigDecimal("direct_project_cost"), rs.getBigDecimal("shipment_revenue"),
				rs.getBigDecimal("invoice_revenue"), rs.getBigDecimal("target_revenue"),
				rs.getBigDecimal("shipment_gross_margin"), rs.getBigDecimal("invoice_gross_margin"),
				rs.getBigDecimal("target_gross_margin"), rs.getBigDecimal("shipment_gross_margin_rate"),
				rs.getBigDecimal("invoice_gross_margin_rate"), rs.getBigDecimal("target_gross_margin_rate"),
				rs.getString("margin_completeness"), rs.getString("completeness_reason"), rs.getLong("version"),
				rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getString("confirmed_by"), rs.getObject("confirmed_at", OffsetDateTime.class));
	}

	private int varianceCount(Long calculationId, String status, String severity) {
		if (calculationId == null) {
			return 0;
		}
		List<Object> args = new ArrayList<>();
		args.add(calculationId);
		String extra = "";
		if (hasText(status)) {
			extra += " and status = ?";
			args.add(status);
		}
		if (hasText(severity)) {
			extra += " and severity = ?";
			args.add(severity);
		}
		Integer count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from prj_cost_variance
				where calculation_id = ?
				%s
				""".formatted(extra), Integer.class, args.toArray());
		return count == null ? 0 : count;
	}

	private int sourceStatusCount(Long calculationId, String sourceStatus) {
		if (calculationId == null) {
			return 0;
		}
		Integer count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from prj_cost_source_line
				where calculation_id = ?
				and source_status = ?
				""", Integer.class, calculationId, sourceStatus);
		return count == null ? 0 : count;
	}

	private int deliveryUnmatchedCount(Long calculationId) {
		if (calculationId == null) {
			return 0;
		}
		Integer count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from prj_cost_variance
				where calculation_id = ?
				and status = 'OPEN'
				and severity = 'BLOCKING'
				and variance_type = 'DELIVERY_WITHOUT_FINISHED_COST'
				""", Integer.class, calculationId);
		return count == null ? 0 : count;
	}

	private List<String> allowedProjectActions(String projectStatus, CurrentUser currentUser) {
		return projectCostProjectAllowed(projectStatus) && hasPermission(currentUser, "cost:project-cost:calculate")
				? List.of("CALCULATE") : List.of();
	}

	private Map<String, String> projectActionDisabledReasons(String projectStatus, CurrentUser currentUser) {
		Map<String, String> reasons = new LinkedHashMap<>();
		if (!projectCostProjectAllowed(projectStatus)) {
			reasons.put("CALCULATE", "项目状态不允许计算项目成本");
		}
		else if (!hasPermission(currentUser, "cost:project-cost:calculate")) {
			reasons.put("CALCULATE", "无权计算项目成本");
		}
		return reasons;
	}

	private List<String> allowedCalculationActions(CalculationActionContext context, CurrentUser currentUser) {
		if (!"CALCULATED".equals(context.status())) {
			return List.of();
		}
		List<String> actions = new ArrayList<>();
		if (hasPermission(currentUser, "cost:project-cost:calculate")) {
			actions.add("RECALCULATE");
		}
		if (confirmable(context) && hasPermission(currentUser, "cost:project-cost:confirm")) {
			actions.add("CONFIRM");
		}
		if (hasPermission(currentUser, "cost:project-cost:cancel")) {
			actions.add("CANCEL");
		}
		return actions;
	}

	private Map<String, String> actionDisabledReasons(CalculationActionContext context, CurrentUser currentUser) {
		Map<String, String> reasons = new LinkedHashMap<>();
		if (!hasPermission(currentUser, "cost:project-cost:calculate")) {
			reasons.put("RECALCULATE", "无权计算项目成本");
		}
		if (!hasPermission(currentUser, "cost:project-cost:confirm")) {
			reasons.put("CONFIRM", "无权确认项目成本");
		}
		if (!hasPermission(currentUser, "cost:project-cost:cancel")) {
			reasons.put("CANCEL", "无权取消项目成本");
		}
		if (!"CALCULATED".equals(context.status())) {
			reasons.put("RECALCULATE", "当前状态不允许重算");
			reasons.put("CONFIRM", "当前状态不允许确认");
			reasons.put("CANCEL", "当前状态不允许取消");
		}
		else if (hasPermission(currentUser, "cost:project-cost:confirm") && !confirmable(context)) {
			reasons.put("CONFIRM", confirmDisabledReason(context));
		}
		return reasons;
	}

	private boolean projectCostProjectAllowed(String projectStatus) {
		return "ACTIVE".equals(projectStatus) || "CLOSED".equals(projectStatus);
	}

	private boolean confirmable(CalculationActionContext context) {
		return context.openBlockingVarianceCount() == 0 && context.unpricedSourceCount() == 0
				&& "COMPLETE".equals(context.completenessStatus());
	}

	private String confirmDisabledReason(CalculationActionContext context) {
		if (context.deliveryUnmatchedCount() > 0) {
			return "存在发货数量超过可匹配完工成本，不能确认";
		}
		if (context.unpricedSourceCount() > 0) {
			return "存在未定价来源，不能确认";
		}
		if (context.openBlockingVarianceCount() > 0) {
			return "存在阻断差异，不能确认";
		}
		if (!"COMPLETE".equals(context.completenessStatus())) {
			return "完整性未完成，不能确认";
		}
		return "当前核算不可确认";
	}

	private boolean amountVisible(CurrentUser currentUser) {
		return hasPermission(currentUser, "cost:project-cost:amount-view");
	}

	private boolean amountVisible(CurrentUser currentUser, String sourceType) {
		if (!amountVisible(currentUser)) {
			return false;
		}
		if (inventoryValuationSource(sourceType)) {
			return hasPermission(currentUser, "inventory:valuation:view");
		}
		return true;
	}

	private boolean sourcePermissionVisible(CurrentUser currentUser) {
		return hasPermission(currentUser, "cost:project-cost:source-view");
	}

	private boolean sourceVisible(CurrentUser currentUser, String sourceType, boolean sourceRestricted) {
		if (sourceRestricted || !sourcePermissionVisible(currentUser)) {
			return false;
		}
		String permission = sourcePermission(sourceType);
		return permission == null || hasPermission(currentUser, permission);
	}

	private String sourcePermission(String sourceType) {
		if (sourceType == null) {
			return null;
		}
		return switch (sourceType) {
			case "PRODUCTION_MATERIAL_ISSUE" -> "production:issue:view";
			case "PRODUCTION_MATERIAL_RETURN" -> "production:material-return:view";
			case "PRODUCTION_MATERIAL_SUPPLEMENT" -> "production:material-supplement:view";
			case "MFG_COST_RECORD" -> "cost:record:view";
			case "PRODUCTION_OUTSOURCING_ISSUE" -> "production:outsourcing-issue:view";
			case "OUTSOURCING_RECEIPT" -> "production:outsourcing-receipt:view";
			case "FIN_EXPENSE" -> "finance:expense:view";
			case "PROJECT_COST_ADJUSTMENT" -> "cost:project-cost-adjustment:view";
			default -> null;
		};
	}

	private boolean inventoryValuationSource(String sourceType) {
		return List.of("PRODUCTION_MATERIAL_ISSUE", "PRODUCTION_MATERIAL_RETURN",
				"PRODUCTION_MATERIAL_SUPPLEMENT", "PRODUCTION_OUTSOURCING_ISSUE").contains(sourceType);
	}

	private boolean hasPermission(CurrentUser currentUser, String permission) {
		return currentUser != null && currentUser.permissions().contains(permission);
	}

	private String restrictedReason(boolean amountVisible, boolean sourceVisible) {
		return restrictedReason(amountVisible, sourceVisible, null);
	}

	private String restrictedReason(boolean amountVisible, boolean sourceVisible, String sourceType) {
		if (!sourceVisible) {
			return "来源权限受限，仅显示脱敏摘要";
		}
		if (!amountVisible) {
			if (inventoryValuationSource(sourceType)) {
				return "无权查看库存估值金额";
			}
			return "无权查看项目成本金额";
		}
		return null;
	}

	private String freshnessStatus(boolean isCurrent) {
		return isCurrent ? "CURRENT" : "STALE";
	}

	private String sourceSummary(String sourceType, String sourceNo) {
		String no = hasText(sourceNo) ? sourceNo : "未编号";
		return switch (sourceType == null ? "" : sourceType) {
			case "PRODUCTION_MATERIAL_ISSUE" -> "生产领料 " + no;
			case "PRODUCTION_MATERIAL_RETURN" -> "生产退料 " + no;
			case "PRODUCTION_MATERIAL_SUPPLEMENT" -> "生产补料 " + no;
			case "PRODUCTION_OUTSOURCING_ISSUE" -> "外协发料 " + no;
			case "OUTSOURCING_RECEIPT" -> "外协收货 " + no;
			case "MFG_COST_RECORD" -> "成本记录 " + no;
			case "FIN_EXPENSE" -> "费用单 " + no;
			case "PROJECT_COST_ADJUSTMENT" -> "项目成本调整 " + no;
			default -> sourceType + " " + no;
		};
	}

	private Map<String, Object> sourceRoute(String sourceType, Long sourceId, Long sourceLineId) {
		if (sourceId == null) {
			return null;
		}
		Map<String, Object> route = new LinkedHashMap<>();
		switch (sourceType == null ? "" : sourceType) {
			case "FIN_EXPENSE" -> route.put("name", "finance-expense-detail");
			case "PROJECT_COST_ADJUSTMENT" -> route.put("name", "cost-project-cost-adjustment-detail");
			case "OUTSOURCING_RECEIPT" -> route.put("name", "production-outsourcing-receipt-detail");
			case "PRODUCTION_OUTSOURCING_ISSUE" -> route.put("name", "production-outsourcing-issue-detail");
			default -> route.put("name", "inventory-movements");
		}
		route.put("params", Map.of("id", sourceId));
		if (sourceLineId != null) {
			route.put("query", Map.of("sourceLineId", sourceLineId.toString()));
		}
		return route;
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

	public record ProjectCostListFilter(Long projectId, String keyword, Long ownerUserId, String projectStatus,
			String calculationStatus, String freshnessStatus, String varianceStatus, String completenessStatus,
			LocalDate cutoffDateFrom, LocalDate cutoffDateTo) {
	}

	public record SourceListFilter(String category, String stage, String sourceStatus, String sourceType,
			Long projectId, LocalDate businessDateFrom, LocalDate businessDateTo, Boolean sourceRestricted) {
	}

	public record EntryListFilter(String category, String stage) {
	}

	public record VarianceListFilter(Long projectId, String varianceType, String severity, String status,
			String sourceType, LocalDate businessDateFrom, LocalDate businessDateTo, Boolean sourceRestricted) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

	private record ProjectRow(Long id, String projectNo, String projectName, String customerName,
			String ownerDisplayName, String projectStatus, BigDecimal targetRevenue) {
	}

	private record CalculationRow(Long id, Long projectId, String calculationNo, LocalDate cutoffDate, String status,
			boolean isCurrent, String sourceFingerprint, BigDecimal projectCostTotal, BigDecimal wipCost,
			BigDecimal finishedCost, BigDecimal deliveredCost, BigDecimal directProjectCost,
			BigDecimal shipmentRevenue, BigDecimal invoiceRevenue, BigDecimal targetRevenue,
			BigDecimal shipmentGrossMargin, BigDecimal invoiceGrossMargin, BigDecimal targetGrossMargin,
			BigDecimal shipmentGrossMarginRate, BigDecimal invoiceGrossMarginRate, BigDecimal targetGrossMarginRate,
			String marginCompleteness, String completenessReason, Long version, String createdBy,
			OffsetDateTime createdAt, String confirmedBy, OffsetDateTime confirmedAt) {
	}

	private record CalculationActionContext(String status, String completenessStatus,
			Integer openBlockingVarianceCount, Integer unpricedSourceCount, Integer deliveryUnmatchedCount) {
	}

	public record WorkbenchResponse(Long projectId, String projectNo, String projectName, String customerName,
			String ownerDisplayName, String projectStatus, Long calculationId, String calculationNo,
			String status, String calculationStatus, String freshnessStatus, String marginCompleteness,
			String completenessStatus, LocalDate cutoffDate, String projectCostTotal, String totalCost,
			String wipCost, String finishedCost, String deliveredCost, String directProjectCost,
			String shipmentRevenue, String shipmentPretaxRevenue, String invoiceRevenue, String invoicePretaxRevenue,
			String targetRevenue, String shipmentGrossMargin, String invoiceGrossMargin, String targetGrossMargin,
			String shipmentGrossMarginRate, String invoiceGrossMarginRate, String targetGrossMarginRate,
			String sourceFingerprint, Long version, boolean amountVisible, boolean sourceVisible,
			String restrictedReason, Integer openVarianceCount, Integer blockingVarianceCount,
			Integer provisionalSourceCount, Integer unpricedSourceCount, OffsetDateTime calculatedAt,
			List<String> allowedActions, Map<String, String> actionDisabledReasons) {
	}

	public record CalculationResponse(Long id, Long calculationId, Long projectId, String projectNo,
			String projectName, String customerName, String ownerDisplayName, String projectStatus,
			String calculationNo, LocalDate cutoffDate, String status, String calculationStatus,
			String freshnessStatus, String marginCompleteness, String completenessStatus, String completenessReason,
			boolean isCurrent, String sourceFingerprint, String projectCostTotal, String totalCost,
			String wipCost, String finishedCost, String deliveredCost, String directProjectCost,
			String shipmentRevenue, String shipmentPretaxRevenue, String invoiceRevenue, String invoicePretaxRevenue,
			String targetRevenue, String shipmentGrossMargin, String invoiceGrossMargin, String targetGrossMargin,
			String shipmentGrossMarginRate, String invoiceGrossMarginRate, String targetGrossMarginRate,
			Long version, boolean amountVisible, boolean sourceVisible, String restrictedReason,
			Integer openVarianceCount, Integer blockingVarianceCount, Integer provisionalSourceCount,
			Integer unpricedSourceCount, String calculatedByName, OffsetDateTime createdAt,
			OffsetDateTime calculatedAt, String confirmedByName, OffsetDateTime confirmedAt,
			List<String> allowedActions, Map<String, String> actionDisabledReasons) {
	}

	public record ProjectDetailResponse(Long projectId, String projectNo, String projectName, String customerName,
			String ownerDisplayName, String projectStatus, Long calculationId, Long latestCalculationId,
			String calculationNo, String latestCalculationNo, String status, String calculationStatus,
			String freshnessStatus, String marginCompleteness, String completenessStatus, String completenessReason,
			LocalDate cutoffDate, String projectCostTotal, String totalCost, String wipCost, String finishedCost,
			String deliveredCost, String directProjectCost, String adjustmentAmount, String shipmentRevenue,
			String shipmentPretaxRevenue, String invoiceRevenue, String invoicePretaxRevenue,
			String targetRevenue, String shipmentGrossMargin, String invoiceGrossMargin, String targetGrossMargin,
			String shipmentGrossMarginRate, String invoiceGrossMarginRate, String targetGrossMarginRate,
			String sourceFingerprint, Long version, boolean amountVisible, boolean sourceVisible,
			String restrictedReason, Integer openVarianceCount, Integer blockingVarianceCount,
			Integer provisionalSourceCount, Integer unpricedSourceCount, List<String> allowedActions,
			Map<String, String> actionDisabledReasons, List<CategorySummaryResponse> categorySummaries,
			List<StageSummaryResponse> stageSummaries, List<CalculationSummaryResponse> calculations,
			List<AuditSummaryResponse> auditSummary) {

		static ProjectDetailResponse empty(Long projectId, String projectNo, String projectName, String customerName,
				String ownerDisplayName, String projectStatus, boolean amountVisible, boolean sourceVisible,
				String restrictedReason, List<String> allowedActions, Map<String, String> actionDisabledReasons) {
			return new ProjectDetailResponse(projectId, projectNo, projectName, customerName, ownerDisplayName,
					projectStatus, null, null, null, null, "DRAFT", "DRAFT", "CURRENT", "COMPLETE", "COMPLETE",
					null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
					null, null, null, null, null, 0L, amountVisible, sourceVisible, restrictedReason, 0, 0, 0, 0,
					allowedActions, actionDisabledReasons, List.of(), List.of(), List.of(), List.of());
		}

		static ProjectDetailResponse from(CalculationResponse c, List<CategorySummaryResponse> categories,
				List<StageSummaryResponse> stages, List<CalculationSummaryResponse> calculations,
				List<AuditSummaryResponse> audits) {
			return new ProjectDetailResponse(c.projectId(), c.projectNo(), c.projectName(), c.customerName(),
					c.ownerDisplayName(), c.projectStatus(), c.id(), c.id(), c.calculationNo(), c.calculationNo(),
					c.status(), c.calculationStatus(), c.freshnessStatus(), c.marginCompleteness(),
					c.completenessStatus(), c.completenessReason(), c.cutoffDate(), c.projectCostTotal(),
					c.totalCost(), c.wipCost(), c.finishedCost(), c.deliveredCost(), c.directProjectCost(),
					null, c.shipmentRevenue(), c.shipmentPretaxRevenue(), c.invoiceRevenue(),
					c.invoicePretaxRevenue(), c.targetRevenue(), c.shipmentGrossMargin(), c.invoiceGrossMargin(),
					c.targetGrossMargin(), c.shipmentGrossMarginRate(), c.invoiceGrossMarginRate(),
					c.targetGrossMarginRate(), c.sourceFingerprint(), c.version(), c.amountVisible(),
					c.sourceVisible(), c.restrictedReason(), c.openVarianceCount(), c.blockingVarianceCount(),
					c.provisionalSourceCount(), c.unpricedSourceCount(), c.allowedActions(),
					c.actionDisabledReasons(), categories, stages, calculations, audits);
		}
	}

	public record CategorySummaryResponse(String category, String costCategory, String amount, Integer sourceCount) {
	}

	public record StageSummaryResponse(String stage, String costStage, String amount) {
	}

	public record CalculationSummaryResponse(Long id, String calculationNo, String status, String calculationStatus,
			LocalDate cutoffDate, OffsetDateTime calculatedAt) {
	}

	public record AuditSummaryResponse(String action, String operatorUsername, OffsetDateTime createdAt,
			String amountSummary) {
	}

	public record SourceLineResponse(Long id, Long calculationId, Long projectId, String costCategory,
			String category, String costStage, String stage, String entryType, String sourceType, Long sourceId,
			Long sourceLineId, String sourceNo, String sourceStatus, LocalDate businessDate, String quantity,
			String unitCost, String unitPrice, String sourceAmount, String calculatedAmount, String amount,
			boolean sourceRestricted, boolean amountVisible, boolean sourceVisible, String restrictedReason,
			String sourceSummary, Map<String, Object> sourceRoute, String materialCode, String materialName,
			String unitName) {
	}

	public record EntryResponse(Long id, Long calculationId, String entryType, String costCategory,
			String category, String costStage, String stage, String direction, String amount, String description,
			Integer sourceCount, boolean amountVisible) {
	}

	public record VarianceResponse(Long id, Long calculationId, Long projectId, String projectNo,
			String projectName, String varianceType, String severity, String status, boolean sourceRestricted,
			String varianceAmount, String expectedAmount, String actualAmount, String differenceAmount,
			String description, String sourceType, Long sourceId, Long sourceLineId, String sourceNo,
			String sourceSummary, String costCategory, boolean amountVisible, boolean sourceVisible,
			String restrictedReason, String resolvedAdjustmentNo) {
	}

}
