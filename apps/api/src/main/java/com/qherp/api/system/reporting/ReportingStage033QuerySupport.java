package com.qherp.api.system.reporting;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

abstract class ReportingStage033QuerySupport {

	static final int DEFAULT_PAGE = 1;

	static final int DEFAULT_PAGE_SIZE = 20;

	static final int MAX_PAGE_SIZE = 100;

	static final String LIVE = "LIVE";

	static final String BUSINESS_SNAPSHOT = "BUSINESS_SNAPSHOT";

	static final String RESTRICTED = "RESTRICTED";

	static final String UNAVAILABLE = "UNAVAILABLE";

	static final String CURRENT = "CURRENT";

	static final String FROZEN = "FROZEN";

	static final String STALE = "STALE";

	static final String LEGACY_NOT_INCLUDED = "LEGACY_NOT_INCLUDED";

	static final String RESTRICTED_MESSAGE = "当前账号没有查看来源详情的权限";

	private static final ThreadLocal<Boolean> UNRESTRICTED_CAPTURE = ThreadLocal.withInitial(() -> false);

	protected final JdbcTemplate jdbcTemplate;

	protected final ObjectMapper objectMapper;

	ReportingStage033QuerySupport(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	static Object unrestrictedCapture(Supplier<Object> supplier) {
		boolean previous = Boolean.TRUE.equals(UNRESTRICTED_CAPTURE.get());
		UNRESTRICTED_CAPTURE.set(true);
		try {
			return supplier.get();
		}
		finally {
			UNRESTRICTED_CAPTURE.set(previous);
		}
	}

	protected OperatingFinanceQuery captureQuery(String periodCode, LocalDate dateFrom, LocalDate dateTo) {
		return new OperatingFinanceQuery(periodCode, dateFrom, dateTo, BUSINESS_SNAPSHOT, DEFAULT_PAGE,
				Integer.MAX_VALUE, null, null, null, null, null, null, null, null);
	}

	protected String freshnessStatus(OperatingFinanceQuery query) {
		if (BUSINESS_SNAPSHOT.equals(query.analysisMode())) {
			return FROZEN;
		}
		return businessPeriodReopened(query.periodCode()) || financialCloseReopened(query.periodCode()) ? STALE
				: CURRENT;
	}

	protected String freshnessStatus(OperatingFinanceQuery query, List<ProjectCostRow> rows) {
		String baseStatus = freshnessStatus(query);
		if (!CURRENT.equals(baseStatus)) {
			return baseStatus;
		}
		if (rows.stream().anyMatch((row) -> row.cutoffDate() != null && row.cutoffDate().isBefore(query.dateTo()))) {
			return STALE;
		}
		return projectCostSourceChanged(rows) ? STALE : CURRENT;
	}

	private boolean businessPeriodReopened(String periodCode) {
		return exists("""
				select 1
				from biz_period_close_run r
				join biz_business_period p on p.id = r.period_id
				where p.period_code = ?
				and r.status = 'REOPENED'
				""", periodCode);
	}

	private boolean financialCloseReopened(String periodCode) {
		return exists("""
				select 1
				from fin_close_run r
				join gl_accounting_period p on p.id = r.period_id
				where p.period_code = ?
				and r.status = 'REOPENED'
				""", periodCode);
	}

	private boolean projectCostSourceChanged(List<ProjectCostRow> rows) {
		for (ProjectCostRow row : rows) {
			if (exists("""
					select 1
					from prj_cost_source_line
					where calculation_id = ?
					and source_fingerprint is distinct from ?
					""", row.calculationId(), row.sourceFingerprint())) {
				return true;
			}
		}
		return false;
	}

	protected JsonNode snapshotResult(String periodCode, String reportCode) {
		List<String> rows = this.jdbcTemplate.query("""
				select rs.result_json::text
				from biz_period_report_snapshot rs
				join biz_period_snapshot s on s.id = rs.snapshot_id
				join biz_period_close_run r on r.snapshot_id = s.id
				join biz_business_period p on p.id = r.period_id
				where p.period_code = ?
				and r.status = 'CLOSED'
				and rs.report_code = ?
				order by r.revision_no desc, r.id desc
				limit 1
				""", (rs, rowNum) -> rs.getString(1), periodCode, reportCode);
		if (rows.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REPORT_SNAPSHOT_NOT_INCLUDED);
		}
		try {
			return this.objectMapper.readTree(rows.getFirst());
		}
		catch (Exception exception) {
			throw new BusinessException(ApiErrorCode.REPORT_SNAPSHOT_NOT_INCLUDED);
		}
	}

	protected void rejectUnsupportedSnapshot(OperatingFinanceQuery query) {
		if (BUSINESS_SNAPSHOT.equals(query.analysisMode())) {
			throw new BusinessException(ApiErrorCode.REPORT_BASIS_INVALID);
		}
	}

	protected List<ProjectCostRow> projectCostRows(OperatingFinanceQuery query) {
		StringBuilder sql = new StringBuilder("""
				select c.id as calculation_id, c.project_id, p.project_no, p.name as project_name,
				       customer.name as customer_name, c.cutoff_date, c.source_fingerprint,
				       c.project_cost_total, c.wip_cost, c.finished_cost, c.delivered_cost,
				       c.direct_project_cost, c.shipment_revenue, c.invoice_revenue, c.target_revenue,
				       c.margin_completeness, coalesce(source_counts.source_count, 0) as source_count
				from prj_cost_calculation c
				join sal_project p on p.id = c.project_id
				left join mst_customer customer on customer.id = p.customer_id
				left join (
					select calculation_id, count(*) as source_count
					from prj_cost_source_line
					group by calculation_id
				) source_counts on source_counts.calculation_id = c.id
				where c.is_current = true
				and c.status in ('CALCULATED', 'CONFIRMED')
				and c.cutoff_date between ? and ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(query.dateFrom());
		args.add(query.dateTo());
		appendProjectAndKeyword(sql, args, query);
		sql.append(" order by p.project_no, c.id");
		return this.jdbcTemplate.query(sql.toString(), projectCostMapper(), args.toArray());
	}

	protected List<ProjectCostRow> reconciliationProjectRows(OperatingFinanceQuery query) {
		StringBuilder sql = new StringBuilder("""
				select c.id as calculation_id, c.project_id, p.project_no, p.name as project_name,
				       customer.name as customer_name, c.cutoff_date, c.source_fingerprint,
				       c.project_cost_total, c.wip_cost, c.finished_cost, c.delivered_cost,
				       c.direct_project_cost, c.shipment_revenue, c.invoice_revenue, c.target_revenue,
				       c.margin_completeness, coalesce(source_counts.source_count, 0) as source_count
				from prj_cost_calculation c
				join sal_project p on p.id = c.project_id
				left join mst_customer customer on customer.id = p.customer_id
				left join (
					select calculation_id, count(*) as source_count
					from prj_cost_source_line
					group by calculation_id
				) source_counts on source_counts.calculation_id = c.id
				where c.is_current = true
				and c.status in ('CALCULATED', 'CONFIRMED')
				and c.cutoff_date between ? and ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(query.dateFrom());
		args.add(query.dateTo());
		appendProjectAndKeyword(sql, args, query);
		sql.append(" order by p.project_no, c.id");
		return this.jdbcTemplate.query(sql.toString(), projectCostMapper(), args.toArray());
	}

	private void appendProjectAndKeyword(StringBuilder sql, List<Object> args, OperatingFinanceQuery query) {
		if (query.projectId() != null) {
			sql.append(" and c.project_id = ?");
			args.add(query.projectId());
		}
		if (hasText(query.keyword())) {
			sql.append(" and (lower(p.project_no) like ? or lower(p.name) like ? or lower(customer.name) like ?)");
			String like = "%" + query.keyword().toLowerCase(Locale.ROOT) + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
	}

	private RowMapper<ProjectCostRow> projectCostMapper() {
		return (rs, rowNum) -> new ProjectCostRow(rs.getLong("calculation_id"), rs.getLong("project_id"),
				rs.getString("project_no"), rs.getString("project_name"), rs.getString("customer_name"),
				rs.getObject("cutoff_date", LocalDate.class), rs.getString("source_fingerprint"),
				rs.getBigDecimal("project_cost_total"), rs.getBigDecimal("wip_cost"),
				rs.getBigDecimal("finished_cost"), rs.getBigDecimal("delivered_cost"),
				rs.getBigDecimal("direct_project_cost"), rs.getBigDecimal("shipment_revenue"),
				rs.getBigDecimal("invoice_revenue"), rs.getBigDecimal("target_revenue"),
				rs.getString("margin_completeness"), rs.getInt("source_count"));
	}

	protected ProjectAccountingAmounts accountingAmounts(OperatingFinanceQuery query, long projectId) {
		List<ProjectAccountingAmounts> rows = this.jdbcTemplate.query("""
				with recursive account_scope(account_id, root_code) as (
					select id, code
					from gl_account
					where code in ('6001', '6051', '6401', '6601', '6602', '6603', '6301', '6711', '6801')
					union all
					select child.id, parent.root_code
					from gl_account child
					join account_scope parent on parent.account_id = child.parent_id
				),
				scoped as (
					select e.*, account_scope.root_code,
					       exists (
					           select 1
					           from jsonb_array_elements(e.auxiliary_snapshot) aux
					           where aux ->> 'dimensionCode' = 'PROJECT'
					           and aux ->> 'objectId' = ?
					       ) as has_project_auxiliary
					from gl_ledger_entry e
					join account_scope on account_scope.account_id = e.account_id
					join gl_voucher v on v.id = e.voucher_id
					join gl_accounting_period p on p.id = e.period_id
					where p.period_code = ?
					and v.status = 'POSTED'
				)
				select
					coalesce(sum(case when has_project_auxiliary and root_code in ('6001', '6051')
						then credit_amount - debit_amount else 0 end), 0) as revenue,
					coalesce(sum(case when has_project_auxiliary and root_code = '6401'
						then debit_amount - credit_amount else 0 end), 0) as main_cost,
					coalesce(sum(case when has_project_auxiliary and root_code in ('6601', '6602', '6603')
						then debit_amount - credit_amount else 0 end), 0) as period_expense,
					coalesce(sum(case when has_project_auxiliary and root_code = '6301'
						then credit_amount - debit_amount
						when has_project_auxiliary and root_code = '6711'
						then debit_amount - credit_amount else 0 end), 0) as other_profit_loss,
					coalesce(sum(case when has_project_auxiliary and root_code = '6801'
						then debit_amount - credit_amount else 0 end), 0) as income_tax_expense,
					count(*) filter (where has_project_auxiliary and root_code is not null) as source_count
				from scoped
				""", (rs, rowNum) -> mapProjectAccounting(rs), String.valueOf(projectId), query.periodCode());
		if (rows.isEmpty() || rows.getFirst().sourceCount() == 0) {
			return ProjectAccountingAmounts.unavailable();
		}
		return rows.getFirst();
	}

	private ProjectAccountingAmounts mapProjectAccounting(ResultSet rs) throws SQLException {
		BigDecimal revenue = rs.getBigDecimal("revenue");
		BigDecimal mainCost = rs.getBigDecimal("main_cost");
		BigDecimal periodExpense = rs.getBigDecimal("period_expense");
		BigDecimal otherProfitLoss = rs.getBigDecimal("other_profit_loss");
		BigDecimal incomeTaxExpense = rs.getBigDecimal("income_tax_expense");
		int sourceCount = rs.getInt("source_count");
		BigDecimal profit = revenue.subtract(mainCost).subtract(periodExpense).add(otherProfitLoss)
			.subtract(incomeTaxExpense);
		return new ProjectAccountingAmounts(revenue, mainCost, periodExpense, otherProfitLoss, incomeTaxExpense,
				profit, sourceCount, sourceCount > 0);
	}

	protected BigDecimal publicUnassignedAmount(OperatingFinanceQuery query) {
		BigDecimal value = this.jdbcTemplate.queryForObject("""
				with recursive account_scope(account_id) as (
					select id
					from gl_account
					where code in ('6001', '6051', '6401', '6601', '6602', '6603', '6301', '6711', '6801')
					union all
					select child.id
					from gl_account child
					join account_scope parent on parent.account_id = child.parent_id
				),
				scoped as (
					select e.*
					from gl_ledger_entry e
					join account_scope on account_scope.account_id = e.account_id
					join gl_voucher v on v.id = e.voucher_id
					join gl_accounting_period p on p.id = e.period_id
					where p.period_code = ?
					and v.status = 'POSTED'
					and not exists (
						select 1
						from jsonb_array_elements(e.auxiliary_snapshot) aux
						where aux ->> 'dimensionCode' = 'PROJECT'
					)
				)
				select coalesce(sum(abs(credit_amount - debit_amount)), 0)
				from scoped
				""", BigDecimal.class, query.periodCode());
		return value == null ? BigDecimal.ZERO : value;
	}

	protected PeriodState periodState(OperatingFinanceQuery query) {
		String businessStatus = exists("""
				select 1
				from biz_period_close_run r
				join biz_business_period p on p.id = r.period_id
				where p.period_code = ?
				and r.status = 'CLOSED'
				""", query.periodCode()) ? "CLOSED" : "OPEN";
		List<AccountingPeriodRow> periods = this.jdbcTemplate.query("""
				select id, status
				from gl_accounting_period
				where period_code = ?
				order by id
				limit 1
				""", (rs, rowNum) -> new AccountingPeriodRow(rs.getLong("id"), rs.getString("status")),
				query.periodCode());
		if (periods.isEmpty()) {
			return new PeriodState(businessStatus, UNAVAILABLE, "OPEN", UNAVAILABLE, null);
		}
		AccountingPeriodRow accounting = periods.getFirst();
		boolean financialClosed = exists("""
				select 1
				from fin_close_run
				where period_id = ?
				and status = 'CLOSED'
				""", accounting.id());
		return new PeriodState(businessStatus, accounting.status(), financialClosed ? "CLOSED" : "OPEN",
				financialClosed ? "FINAL" : "PREVIEW", accounting.id());
	}

	protected boolean exists(String sql, Object... args) {
		Boolean value = this.jdbcTemplate.queryForObject("select exists (" + sql + ")", Boolean.class, args);
		return Boolean.TRUE.equals(value);
	}

	protected ReportingAdminService.ReportPageResponse<Object> emptyPage(Object summary, OperatingFinanceQuery query) {
		return new ReportingAdminService.ReportPageResponse<>(summary, List.of(), query.page(), query.pageSize(), 0,
				0);
	}

	protected PageResponse<ReportingAdminService.TraceSourceResponse> emptyTracePage(OperatingFinanceQuery query) {
		return PageResponse.of(List.of(), query.page(), query.pageSize(), 0);
	}

	protected ReportingAdminService.ReportPageResponse<Object> legacySnapshotPage(Object summary,
			OperatingFinanceQuery query) {
		return new ReportingAdminService.ReportPageResponse<>(summary, List.of(), query.page(), query.pageSize(), 0,
				0);
	}

	protected PageResponse<ReportingAdminService.TraceSourceResponse> tracePage(
			List<ReportingAdminService.TraceSourceResponse> items, OperatingFinanceQuery query) {
		List<ReportingAdminService.TraceSourceResponse> visibleItems = items.stream()
			.filter((item) -> !item.restricted())
			.toList();
		int total = visibleItems.size();
		int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / query.pageSize());
		return new PageResponse<>(pageItems(visibleItems, query), query.page(), query.pageSize(), total, totalPages);
	}

	protected <T> ReportingAdminService.ReportPageResponse<Object> pageOf(Object summary, List<T> items,
			OperatingFinanceQuery query) {
		long total = items.size();
		int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / query.pageSize());
		List<Object> pageItems = pageItems(items, query).stream().map(Object.class::cast).toList();
		return new ReportingAdminService.ReportPageResponse<>(summary, pageItems, query.page(), query.pageSize(),
				total, totalPages);
	}

	protected ReportingAdminService.ReportPageResponse<Object> snapshotPage(String reportCode,
			OperatingFinanceQuery query, Predicate<JsonNode> filter, Function<List<JsonNode>, Object> summaryMapper,
			Function<JsonNode, Object> itemMapper) {
		JsonNode snapshot = snapshotResultOrLegacy(query.periodCode(), reportCode);
		if (snapshot == null) {
			return legacySnapshotPage(legacySummary(summaryMapper.apply(List.of())), query);
		}
		List<JsonNode> filtered = new ArrayList<>();
		JsonNode items = snapshot.get("items");
		if (items != null && items.isArray()) {
			for (JsonNode item : items) {
				if (filter.test(item)) {
					filtered.add(item);
				}
			}
		}
		return pageOf(summaryMapper.apply(filtered), filtered.stream().map(itemMapper).toList(), query);
	}

	private JsonNode snapshotResultOrLegacy(String periodCode, String reportCode) {
		try {
			return snapshotResult(periodCode, reportCode);
		}
		catch (BusinessException exception) {
			if (exception.errorCode() == ApiErrorCode.REPORT_SNAPSHOT_NOT_INCLUDED
					&& closedSnapshotExists(periodCode)) {
				return null;
			}
			throw exception;
		}
	}

	private boolean closedSnapshotExists(String periodCode) {
		return exists("""
				select 1
				from biz_period_snapshot s
				join biz_period_close_run r on r.snapshot_id = s.id
				join biz_business_period p on p.id = r.period_id
				where p.period_code = ?
				and r.status = 'CLOSED'
				""", periodCode);
	}

	@SuppressWarnings("unchecked")
	private Object legacySummary(Object summary) {
		if (summary instanceof Map<?, ?> map) {
			Map<String, Object> mutable = new LinkedHashMap<>((Map<String, Object>) map);
			mutable.put("analysisMode", BUSINESS_SNAPSHOT);
			mutable.put("freshnessStatus", LEGACY_NOT_INCLUDED);
			return mutable;
		}
		return summary;
	}

	private <T> List<T> pageItems(List<T> items, OperatingFinanceQuery query) {
		long offset = ((long) query.page() - 1) * query.pageSize();
		if (offset >= items.size()) {
			return List.of();
		}
		int start = (int) offset;
		int end = (int) Math.min(offset + query.pageSize(), (long) items.size());
		return items.subList(start, end);
	}

	protected OperatingFinanceQuery parseTraceQuery(MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseQuery(parameters, true);
		String traceKey = first(parameters, "traceKey");
		if (!hasText(traceKey)) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return query.withTraceKey(URLDecoder.decode(traceKey, StandardCharsets.UTF_8));
	}

	protected OperatingFinanceQuery parseQuery(MultiValueMap<String, String> parameters, boolean paged) {
		String periodCode = first(parameters, "periodCode");
		LocalDate dateFrom = parseOptionalDate(parameters, "dateFrom");
		LocalDate dateTo = parseOptionalDate(parameters, "dateTo");
		if (!hasText(periodCode)) {
			if (dateFrom != null) {
				periodCode = YearMonth.from(dateFrom).toString();
			}
			else {
				periodCode = YearMonth.now().toString();
			}
		}
		YearMonth yearMonth = parsePeriodCode(periodCode);
		LocalDate from = dateFrom == null ? yearMonth.atDay(1) : dateFrom;
		LocalDate to = dateTo == null ? yearMonth.atEndOfMonth() : dateTo;
		if (from.isAfter(to) || from.plusMonths(12).isBefore(to)) {
			throw new BusinessException(ApiErrorCode.REPORT_DATE_RANGE_INVALID);
		}
		String analysisMode = first(parameters, "analysisMode");
		if (!hasText(analysisMode)) {
			analysisMode = LIVE;
		}
		analysisMode = analysisMode.trim().toUpperCase(Locale.ROOT);
		if (!LIVE.equals(analysisMode) && !BUSINESS_SNAPSHOT.equals(analysisMode)) {
			throw new BusinessException(ApiErrorCode.REPORT_BASIS_INVALID);
		}
		int page = paged ? parsePage(parameters, "page", DEFAULT_PAGE) : DEFAULT_PAGE;
		int pageSize = paged ? parsePage(parameters, "pageSize", DEFAULT_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
		if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
		}
		return new OperatingFinanceQuery(yearMonth.toString(), from, to, analysisMode, page, pageSize,
				parseOptionalPositiveLong(parameters, "projectId"), parseOptionalPositiveLong(parameters, "contractId"),
				first(parameters, "basis"), first(parameters, "completenessStatus"),
				first(parameters, "reconciliationStatus"), first(parameters, "finalityStatus"), first(parameters,
						"keyword"),
				null);
	}

	private YearMonth parsePeriodCode(String periodCode) {
		try {
			return YearMonth.parse(periodCode);
		}
		catch (DateTimeParseException exception) {
			throw new BusinessException(ApiErrorCode.REPORT_PERIOD_UNAVAILABLE);
		}
	}

	private LocalDate parseOptionalDate(MultiValueMap<String, String> parameters, String name) {
		String value = first(parameters, name);
		if (!hasText(value)) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException exception) {
			throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
		}
	}

	private int parsePage(MultiValueMap<String, String> parameters, String name, int defaultValue) {
		String value = first(parameters, name);
		if (!hasText(value)) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException exception) {
			throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
		}
	}

	private Long parseOptionalPositiveLong(MultiValueMap<String, String> parameters, String name) {
		String value = first(parameters, name);
		if (!hasText(value)) {
			return null;
		}
		try {
			long parsed = Long.parseLong(value);
			if (parsed < 1) {
				throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
			}
			return parsed;
		}
		catch (NumberFormatException exception) {
			throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
		}
	}

	protected void validateTraceKey(String traceKey, String prefix) {
		if (!hasText(traceKey) || !traceKey.startsWith(prefix + ":")) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
	}

	protected TraceKeyParts traceKeyParts(String traceKey, String prefix) {
		validateTraceKey(traceKey, prefix);
		String[] parts = traceKey.split(":");
		if (parts.length != 3 || !hasText(parts[1]) || !hasText(parts[2])) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		try {
			return new TraceKeyParts(parts[1], Long.parseLong(parts[2]));
		}
		catch (NumberFormatException exception) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
	}

	protected ReportingAdminService.TraceSourceResponse trace(String sourceType, Long sourceId, String sourceNo,
			Long sourceLineId, LocalDate businessDate, String status, BigDecimal quantity, BigDecimal amount,
			String permissionCode, String resourceRouteName, Map<String, Object> resourceRouteParams) {
		boolean canView = permissionCode == null || hasPermission(permissionCode);
		if (!canView) {
			return new ReportingAdminService.TraceSourceResponse(sourceType, null, null, null, null, null, null, null,
					null, null, null, false, true, RESTRICTED_MESSAGE);
		}
		return new ReportingAdminService.TraceSourceResponse(sourceType, sourceId, sourceNo, sourceLineId,
				businessDate, status, quantity == null ? null : quantity(quantity), amount == null ? null : amount(amount),
				resourceRouteName, resourceRouteParams, null, true, false, null);
	}

	protected Map<String, Object> routeParams(Object... keyValues) {
		if (keyValues == null || keyValues.length == 0) {
			return null;
		}
		Map<String, Object> values = new LinkedHashMap<>();
		for (int index = 0; index + 1 < keyValues.length; index += 2) {
			values.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
		}
		return values;
	}

	protected boolean amountVisible() {
		return projectProfitAmountVisible();
	}

	protected boolean projectProfitAmountVisible() {
		return hasPermission("cost:project-cost:amount-view");
	}

	protected boolean projectProfitAccountingAmountVisible() {
		return hasPermission("gl:amount:view");
	}

	protected boolean projectProfitSourceVisible() {
		return hasPermission("cost:project-cost:view") && hasPermission("gl:ledger:view");
	}

	protected boolean contractCollectionAmountVisible() {
		return hasPermission("sales:contract:view") && hasPermission("finance:sales-invoice:view")
				&& hasPermission("finance:receivable:view") && hasPermission("finance:receipt:view");
	}

	protected boolean contractCollectionSourceVisible() {
		return hasPermission("sales:contract:view") && hasPermission("finance:sales-invoice:view")
				&& hasPermission("finance:receivable:view") && hasPermission("finance:receipt:view")
				&& hasPermission("finance:settlement-allocation:view");
	}

	protected boolean procurementVarianceAmountVisible() {
		return hasPermission("procurement:order:view") && hasPermission("procurement:receipt:view")
				&& hasPermission("finance:purchase-invoice:view") && hasPermission("finance:payable:view")
				&& hasPermission("finance:payment:view");
	}

	protected boolean procurementVarianceSourceVisible() {
		return hasPermission("procurement:order:view") && hasPermission("procurement:receipt:view")
				&& hasPermission("procurement:return:view") && hasPermission("finance:purchase-invoice:view")
				&& hasPermission("finance:payable:view") && hasPermission("finance:payment:view")
				&& hasPermission("production:outsourcing:view")
				&& hasPermission("production:outsourcing-receipt:view");
	}

	protected boolean inventoryCapitalAmountVisible() {
		return hasPermission("inventory:valuation:view");
	}

	protected boolean inventoryCapitalSourceVisible() {
		return hasPermission("inventory:balance:view") && hasPermission("inventory:movement:view");
	}

	protected boolean receivablePayableAmountVisible() {
		return hasPermission("finance:receivable:view") && hasPermission("finance:receipt:view")
				&& hasPermission("finance:payable:view") && hasPermission("finance:payment:view");
	}

	protected boolean receivablePayableSourceVisible() {
		return hasPermission("finance:receivable:view") && hasPermission("finance:receipt:view")
				&& hasPermission("finance:payable:view") && hasPermission("finance:payment:view")
				&& hasPermission("finance:settlement-allocation:view")
				&& hasPermission("procurement:receipt:view");
	}

	protected boolean operatingAccountingAmountVisible() {
		return projectProfitAmountVisible() && projectProfitAccountingAmountVisible();
	}

	protected boolean operatingAccountingSourceVisible() {
		return projectProfitSourceVisible();
	}

	protected boolean financialSummaryAmountVisible() {
		return hasPermission("gl:amount:view") && hasPermission("financial-close:amount:view");
	}

	protected boolean financialSummarySourceVisible() {
		return hasPermission("gl:ledger:view") && hasPermission("financial-close:period:view")
				&& hasPermission("financial-close:bank-reconciliation:view")
				&& hasPermission("financial-close:tax-summary:view");
	}

	protected boolean hasPermission(String permissionCode) {
		return Boolean.TRUE.equals(UNRESTRICTED_CAPTURE.get()) || currentUserPermissions().contains(permissionCode);
	}

	protected List<String> currentUserPermissions() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) {
			return currentUser.permissions();
		}
		return List.of();
	}

	protected List<ReportingStage033Service.ReportEntryResponse> reportEntries() {
		return List.of(new ReportingStage033Service.ReportEntryResponse("PROJECT_PROFIT", "/reports/project-profit"),
				new ReportingStage033Service.ReportEntryResponse("CONTRACT_COLLECTION",
						"/reports/contract-collection"),
				new ReportingStage033Service.ReportEntryResponse("PROCUREMENT_VARIANCE",
						"/reports/procurement-variance"),
				new ReportingStage033Service.ReportEntryResponse("INVENTORY_CAPITAL", "/reports/inventory-capital"),
				new ReportingStage033Service.ReportEntryResponse("RECEIVABLE_PAYABLE", "/reports/receivable-payable"),
				new ReportingStage033Service.ReportEntryResponse("OPERATING_ACCOUNTING_RECONCILIATION",
						"/reports/operating-accounting-reconciliation"),
				new ReportingStage033Service.ReportEntryResponse("FINANCIAL_SUMMARY", "/reports/financial-summary"));
	}

	protected String first(MultiValueMap<String, String> parameters, String name) {
		return parameters == null ? null : parameters.getFirst(name);
	}

	protected boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	protected BigDecimal grossProfit(ProjectCostRow row) {
		return row.shipmentRevenue().subtract(row.projectCostTotal());
	}

	protected BigDecimal sum(List<ProjectCostRow> rows, java.util.function.Function<ProjectCostRow, BigDecimal> mapper) {
		return rows.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	protected BigDecimal decimal(String value) {
		return hasText(value) ? new BigDecimal(value) : BigDecimal.ZERO;
	}

	protected String amount(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	protected String nullableAmount(BigDecimal value) {
		return value == null ? null : amount(value);
	}

	protected String visibleAmount(BigDecimal value, boolean visible) {
		return visible ? amount(value) : null;
	}

	protected String visibleNullableAmount(BigDecimal value, boolean visible) {
		return visible && value != null ? amount(value) : null;
	}

	protected String snapshotText(JsonNode node, String fieldName) {
		JsonNode value = node == null ? null : node.get(fieldName);
		if (value == null || value.isNull()) {
			return null;
		}
		return value.asText();
	}

	protected Long snapshotLong(JsonNode node, String fieldName) {
		JsonNode value = node == null ? null : node.get(fieldName);
		if (value == null || value.isNull()) {
			return null;
		}
		return value.longValue();
	}

	protected int snapshotInt(JsonNode node, String fieldName) {
		JsonNode value = node == null ? null : node.get(fieldName);
		return value == null || value.isNull() ? 0 : value.intValue();
	}

	protected BigDecimal snapshotDecimal(JsonNode node, String fieldName) {
		String value = snapshotText(node, fieldName);
		return hasText(value) ? new BigDecimal(value) : null;
	}

	protected boolean containsIgnoreCase(String value, String keyword) {
		return !hasText(keyword) || (value != null && value.toLowerCase(Locale.ROOT)
			.contains(keyword.toLowerCase(Locale.ROOT)));
	}

	protected boolean anyContainsIgnoreCase(String keyword, String... values) {
		if (!hasText(keyword)) {
			return true;
		}
		for (String value : values) {
			if (containsIgnoreCase(value, keyword)) {
				return true;
			}
		}
		return false;
	}

	protected BigDecimal sumDecimal(List<JsonNode> rows, String fieldName) {
		return rows.stream()
			.map((row) -> snapshotDecimal(row, fieldName))
			.filter((value) -> value != null)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	protected boolean matchesCommonSnapshotFilters(JsonNode item, OperatingFinanceQuery query) {
		if (query.projectId() != null) {
			JsonNode projectId = item.get("projectId");
			if (projectId == null || projectId.isNull() || projectId.longValue() != query.projectId()) {
				return false;
			}
		}
		if (query.contractId() != null) {
			JsonNode contractId = item.get("contractId");
			if (contractId == null || contractId.isNull() || contractId.longValue() != query.contractId()) {
				return false;
			}
		}
		if (hasText(query.basis()) && !matchesBasis(item, query.basis())) {
			return false;
		}
		if (hasText(query.keyword()) && !snapshotContainsKeyword(item, query.keyword())) {
			return false;
		}
		if (hasText(query.completenessStatus())
				&& !query.completenessStatus().equalsIgnoreCase(snapshotText(item, "completenessStatus"))) {
			return false;
		}
		if (hasText(query.reconciliationStatus())
				&& !query.reconciliationStatus().equalsIgnoreCase(snapshotText(item, "reconciliationStatus"))) {
			return false;
		}
		return !hasText(query.finalityStatus())
				|| query.finalityStatus().equalsIgnoreCase(snapshotText(item, "finalityStatus"));
	}

	private boolean matchesBasis(JsonNode item, String basis) {
		String expected = basis.trim();
		for (String field : List.of("basis", "ownerType", "partyType", "sourceType")) {
			String value = snapshotText(item, field);
			if (value != null && expected.equalsIgnoreCase(value)) {
				return true;
			}
		}
		return false;
	}

	private boolean snapshotContainsKeyword(JsonNode item, String keyword) {
		if (item == null || !item.isObject()) {
			return false;
		}
		for (Map.Entry<String, JsonNode> entry : item.properties()) {
			JsonNode value = entry.getValue();
			if (value != null && !value.isNull() && containsIgnoreCase(value.asText(), keyword)) {
				return true;
			}
		}
		return false;
	}

	protected Map<String, Object> snapshotItemMap(JsonNode item, boolean amountVisible, Set<String> amountFields) {
		return snapshotItemMap(item, amountVisible, amountVisible, amountFields);
	}

	protected Map<String, Object> snapshotItemMap(JsonNode item, boolean amountVisible, boolean sourceVisible,
			Set<String> amountFields) {
		Map<String, Object> result = new LinkedHashMap<>();
		Set<String> amounts = amountFields == null ? Set.of() : amountFields;
		if (item != null && item.isObject()) {
			for (Map.Entry<String, JsonNode> entry : item.properties()) {
				String field = entry.getKey();
				if (amounts.contains(field)) {
					result.put(field, amountVisible ? snapshotText(item, field) : null);
				}
				else if ("sourceCount".equals(field)) {
					result.put(field, sourceVisible ? snapshotInt(item, field) : 0);
				}
				else if ("freshnessStatus".equals(field)) {
					result.put(field, FROZEN);
				}
				else if ("analysisMode".equals(field)) {
					result.put(field, BUSINESS_SNAPSHOT);
				}
				else if (!sourceVisible && snapshotSourceField(field)) {
					result.put(field, null);
				}
				else {
					result.put(field, snapshotScalar(field, entry.getValue()));
				}
			}
		}
		return result;
	}

	private boolean snapshotSourceField(String field) {
		String normalized = field.toLowerCase(Locale.ROOT);
		return "tracekey".equals(normalized) || "sourceno".equals(normalized) || "contractno".equals(normalized)
				|| normalized.contains("sourcesecret") || normalized.endsWith("id") && !"projectid".equals(normalized)
				|| normalized.endsWith("nos");
	}

	protected Map<String, Object> snapshotSummaryMap(List<JsonNode> items, OperatingFinanceQuery query,
			boolean amountVisible, Map<String, String> summaryFields, Set<String> quantityFields) {
		return snapshotSummaryMap(items, query, amountVisible, amountVisible, summaryFields, quantityFields);
	}

	protected Map<String, Object> snapshotSummaryMap(List<JsonNode> items, OperatingFinanceQuery query,
			boolean amountVisible, boolean sourceVisible, Map<String, String> summaryFields,
			Set<String> quantityFields) {
		Map<String, Object> summary = new LinkedHashMap<>();
		Set<String> quantityFieldSet = quantityFields == null ? Set.of() : new HashSet<>(quantityFields);
		for (Map.Entry<String, String> entry : summaryFields.entrySet()) {
			BigDecimal value = sumDecimal(items, entry.getValue());
			if (quantityFieldSet.contains(entry.getKey())) {
				summary.put(entry.getKey(), quantity(value));
			}
			else {
				summary.put(entry.getKey(), amountVisible ? amount(value) : null);
			}
		}
		summary.put("sourceCount", sourceVisible ? items.stream().mapToInt((item) -> snapshotInt(item, "sourceCount")).sum()
				: 0);
		summary.put("analysisMode", query.analysisMode());
		summary.put("freshnessStatus", FROZEN);
		return summary;
	}

	protected Object snapshotScalar(String fieldName, JsonNode value) {
		if (value == null || value.isNull()) {
			return null;
		}
		if ("amountVisible".equals(fieldName)) {
			return value.booleanValue();
		}
		if ("sourceCount".equals(fieldName) || "projectCount".equals(fieldName)) {
			return value.intValue();
		}
		if (fieldName.endsWith("Id")) {
			return value.longValue();
		}
		return value.asText();
	}

	protected String quantity(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	protected BigDecimal zero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	protected String percentageIfVisible(BigDecimal numerator, BigDecimal denominator, boolean visible) {
		return visible ? percentage(numerator, denominator) : null;
	}

	protected String percentage(BigDecimal numerator, BigDecimal denominator) {
		if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
			return null;
		}
		return (numerator == null ? BigDecimal.ZERO : numerator).multiply(BigDecimal.valueOf(100))
			.divide(denominator, 2, RoundingMode.HALF_UP)
			.toPlainString();
	}

	protected String reconciliationStatus(BigDecimal difference, BigDecimal publicUnassigned,
			boolean accountingAvailable, boolean amountVisible) {
		if (!amountVisible) {
			return RESTRICTED;
		}
		if (!accountingAvailable) {
			return UNAVAILABLE;
		}
		BigDecimal safeDifference = difference == null ? BigDecimal.ZERO : difference;
		return safeDifference.compareTo(BigDecimal.ZERO) == 0 && publicUnassigned.compareTo(BigDecimal.ZERO) == 0
				? "MATCHED" : "DIFFERENT";
	}

	protected record OperatingFinanceQuery(String periodCode, LocalDate dateFrom, LocalDate dateTo,
			String analysisMode, int page, int pageSize, Long projectId, Long contractId, String basis,
			String completenessStatus, String reconciliationStatus, String finalityStatus, String keyword,
			String traceKey) {

		OperatingFinanceQuery withProjectId(Long value) {
			return new OperatingFinanceQuery(this.periodCode, this.dateFrom, this.dateTo, this.analysisMode,
					this.page, this.pageSize, value, this.contractId, this.basis, this.completenessStatus,
					this.reconciliationStatus, this.finalityStatus, this.keyword, this.traceKey);
		}

		OperatingFinanceQuery withTraceKey(String value) {
			return new OperatingFinanceQuery(this.periodCode, this.dateFrom, this.dateTo, this.analysisMode,
					this.page, this.pageSize, this.projectId, this.contractId, this.basis, this.completenessStatus,
					this.reconciliationStatus, this.finalityStatus, this.keyword, value);
		}

	}

	protected record PeriodState(String businessPeriodStatus, String accountingPeriodStatus,
			String financialCloseStatus, String finalityStatus, Long accountingPeriodId) {
	}

	protected record TraceKeyParts(String type, long sourceId) {
	}

	private record AccountingPeriodRow(Long id, String status) {
	}

	protected record ProjectCostRow(Long calculationId, Long projectId, String projectNo, String projectName,
			String customerName, LocalDate cutoffDate, String sourceFingerprint, BigDecimal projectCostTotal,
			BigDecimal wipCost, BigDecimal finishedCost, BigDecimal deliveredCost, BigDecimal directProjectCost,
			BigDecimal shipmentRevenue, BigDecimal invoiceRevenue, BigDecimal targetRevenue,
			String completenessStatus, int sourceCount) {
	}

	protected record ProjectAccountingAmounts(BigDecimal revenue, BigDecimal cost, BigDecimal periodExpense,
			BigDecimal otherProfitLoss, BigDecimal incomeTaxExpense, BigDecimal profit, int sourceCount,
			boolean available) {

		static ProjectAccountingAmounts unavailable() {
			return new ProjectAccountingAmounts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
					BigDecimal.ZERO, BigDecimal.ZERO, 0, false);
		}

	}

}
