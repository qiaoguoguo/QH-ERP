package com.qherp.api.system.periodclose;

import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.qherp.api.system.periodclose.PeriodCloseSupport.hasPermission;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.normalizeNullableStatus;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.nullableLong;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.pageSize;

@Service
class PeriodCloseQueryService {

	private final JdbcTemplate jdbcTemplate;

	private final PeriodCloseRepository repository;

	private final ObjectMapper objectMapper;

	PeriodCloseQueryService(JdbcTemplate jdbcTemplate, PeriodCloseRepository repository, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	PageResponse<PeriodCloseService.PeriodSummaryResponse> list(String periodCode, String status, String checkResult,
			Boolean hasBlocking, LocalDate startDate, LocalDate endDate, int page, int pageSize,
			CurrentUser currentUser) {
		int safePage = Math.max(page, 1);
		int safePageSize = pageSize(pageSize);
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		if (PeriodCloseSupport.hasText(periodCode)) {
			conditions.add("p.period_code ilike ?");
			args.add("%" + periodCode.trim() + "%");
		}
		if (startDate != null) {
			conditions.add("p.end_date >= ?");
			args.add(startDate);
		}
		if (endDate != null) {
			conditions.add("p.start_date <= ?");
			args.add(endDate);
		}
		String statusFilter = normalizeNullableStatus(status);
		String checkResultFilter = normalizeCheckResult(checkResult);
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		List<PeriodCloseService.PeriodSummaryResponse> summaries = this.repository.periods(where, args.toArray())
			.stream()
			.map((period) -> periodSummary(period, currentUser))
			.filter((summary) -> statusFilter == null || statusFilter.equals(summary.status()))
			.filter((summary) -> checkResultFilter == null || checkResultFilter.equals(checkResult(summary)))
			.filter((summary) -> hasBlocking == null || summary.blockingCount() > 0 == hasBlocking.booleanValue())
			.toList();
		int fromIndex = Math.min((safePage - 1) * safePageSize, summaries.size());
		int toIndex = Math.min(fromIndex + safePageSize, summaries.size());
		return PageResponse.of(summaries.subList(fromIndex, toIndex), safePage, safePageSize, summaries.size());
	}

	@Transactional(readOnly = true)
	PeriodCloseService.PeriodSummaryResponse period(Long periodId, CurrentUser currentUser) {
		return periodSummary(this.repository.findPeriod(periodId), currentUser);
	}

	@Transactional(readOnly = true)
	PeriodCloseService.RunResponse run(Long runId, CurrentUser currentUser) {
		PeriodCloseRunRow run = this.repository.findRun(runId);
		PeriodClosePeriodRow period = this.repository.findPeriod(run.periodId());
		return toRunResponse(run, period, currentUser);
	}

	@Transactional(readOnly = true)
	PageResponse<PeriodCloseService.CheckRunResponse> checks(Long runId, int page, int pageSize,
			CurrentUser currentUser) {
		this.repository.findRun(runId);
		int safePage = Math.max(page, 1);
		int safePageSize = pageSize(pageSize);
		long total = this.jdbcTemplate.queryForObject(
				"select count(*) from biz_period_close_check_run where run_id = ?", Long.class, runId);
		List<PeriodCloseService.CheckRunResponse> items = this.jdbcTemplate.query("""
				select id, run_id, status, source_fingerprint, inventory_fingerprint, wip_fingerprint,
				       project_cost_fingerprint, report_fingerprint, blocking_count, warning_count,
				       started_by, started_at, completed_at
				from biz_period_close_check_run
				where run_id = ?
				order by id desc
				limit ? offset ?
				""", (rs, rowNum) -> mapCheckRun(rs, currentUser), runId, safePageSize,
				(long) (safePage - 1) * safePageSize);
		return PageResponse.of(items, safePage, safePageSize, total);
	}

	@Transactional(readOnly = true)
	PageResponse<PeriodCloseService.CheckItemResponse> checkItems(Long runId, Long checkRunId, int page, int pageSize,
			CurrentUser currentUser) {
		this.repository.findRun(runId);
		Long matched = this.jdbcTemplate.queryForObject("""
				select count(*)
				from biz_period_close_check_run
				where id = ?
				and run_id = ?
				""", Long.class, checkRunId, runId);
		if (matched == null || matched == 0) {
			throw new com.qherp.api.common.BusinessException(com.qherp.api.common.ApiErrorCode.PERIOD_CLOSE_NOT_FOUND);
		}
		int safePage = Math.max(page, 1);
		int safePageSize = pageSize(pageSize);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from biz_period_close_check_item
				where check_run_id = ?
				""", Long.class, checkRunId);
		List<PeriodCloseService.CheckItemResponse> items = this.jdbcTemplate.query("""
				select id, domain, check_code, severity, source_restricted, object_type, object_id,
				       object_no, title, description, suggestion, source_route::text as source_route
				from biz_period_close_check_item
				where check_run_id = ?
				order by case severity when 'BLOCKING' then 1 when 'WARNING' then 2 else 3 end, id
				limit ? offset ?
				""", (rs, rowNum) -> mapCheckItem(rs, currentUser), checkRunId, safePageSize,
				(long) (safePage - 1) * safePageSize);
		return PageResponse.of(items, safePage, safePageSize, total);
	}

	PeriodCloseService.RunResponse toRunResponse(PeriodCloseRunRow run, PeriodClosePeriodRow period,
			CurrentUser currentUser) {
		return new PeriodCloseService.RunResponse(run.id(), run.periodId(), period.periodCode(), period.periodName(),
				period.startDate(), period.endDate(), period.status().name(), run.status().name(),
				statusName(run.status()), run.revisionNo(), run.latestCheckRunId(), run.snapshotId(),
				run.sourceFingerprint(), run.inventoryFingerprint(), run.wipFingerprint(), run.projectCostFingerprint(),
				run.reportFingerprint(), run.blockingCount(), run.warningCount(), run.warningAcknowledged(),
				run.warningReason(), run.closedBy(), run.closedAt(), run.closeReason(), run.reopenedBy(),
				run.reopenedAt(), run.reopenReason(), run.version(), allowedActions(run.status(), run.snapshotId(),
						currentUser), actionDisabledReasons(run, currentUser), this.repository.runHistory(period.id()),
				auditSummary(run.id()));
	}

	private PeriodCloseService.PeriodSummaryResponse periodSummary(PeriodClosePeriodRow period,
			CurrentUser currentUser) {
		List<PeriodCloseService.RunBriefResponse> history = this.repository.runHistory(period.id());
		PeriodCloseService.RunBriefResponse current = history.stream()
			.filter((run) -> "CLOSED".equals(run.status()))
			.findFirst()
			.orElseGet(() -> history.stream().findFirst().orElse(null));
		String status = current == null ? ("LOCKED".equals(period.status().name()) ? "MANUAL_LOCKED_WITHOUT_SNAPSHOT"
				: "NOT_CHECKED") : current.status();
		List<String> allowedActions = current == null ? initialAllowedActions(period, currentUser)
				: allowedActions(PeriodCloseStatus.valueOf(current.status()), current.snapshotId(), currentUser);
		Map<String, String> disabledReasons = current == null ? initialActionDisabledReasons(period, currentUser)
				: actionDisabledReasons(PeriodCloseStatus.valueOf(current.status()), current.snapshotId(), currentUser);
		return new PeriodCloseService.PeriodSummaryResponse(period.id(), period.periodCode(), period.periodName(),
				period.startDate(), period.endDate(), period.status().name(), status,
				current == null ? null : current.id(), current == null ? null : current.revisionNo(),
				current == null ? null : current.snapshotId(), current == null ? null : current.latestCheckRunId(),
				current == null ? 0 : current.blockingCount(), current == null ? 0 : current.warningCount(),
				allowedActions, disabledReasons, history);
	}

	private PeriodCloseService.CheckRunResponse mapCheckRun(ResultSet rs, CurrentUser currentUser) throws SQLException {
		Long checkRunId = rs.getLong("id");
		return new PeriodCloseService.CheckRunResponse(checkRunId, rs.getLong("run_id"), rs.getString("status"),
				rs.getString("source_fingerprint"), rs.getString("inventory_fingerprint"),
				rs.getString("wip_fingerprint"), rs.getString("project_cost_fingerprint"),
				rs.getString("report_fingerprint"), rs.getInt("blocking_count"), rs.getInt("warning_count"),
				rs.getString("started_by"), rs.getObject("started_at", OffsetDateTime.class),
				rs.getObject("completed_at", OffsetDateTime.class));
	}

	private PeriodCloseService.CheckItemResponse mapCheckItem(ResultSet rs, CurrentUser currentUser)
			throws SQLException {
		String objectType = rs.getString("object_type");
		boolean sourceVisible = sourceVisible(currentUser, objectType, rs.getBoolean("source_restricted"));
		return new PeriodCloseService.CheckItemResponse(rs.getLong("id"), rs.getString("domain"),
				rs.getString("check_code"), rs.getString("severity"), sourceVisible ? objectType : null,
				sourceVisible ? nullableLong(rs, "object_id") : null, sourceVisible ? rs.getString("object_no") : null,
				rs.getString("title"), rs.getString("description"), rs.getString("suggestion"), sourceVisible,
				sourceVisible ? null : "来源权限不足，已脱敏显示",
				sourceVisible ? readJson(rs.getString("source_route")) : null);
	}

	private boolean sourceVisible(CurrentUser currentUser, String objectType, boolean sourceRestricted) {
		if (sourceRestricted) {
			return false;
		}
		String permission = sourcePermission(objectType);
		return permission != null && hasPermission(currentUser, permission);
	}

	private String sourcePermission(String objectType) {
		return switch (objectType == null ? "" : objectType) {
			case "INVENTORY_STOCKTAKE" -> "inventory:stocktake:view";
			case "PROJECT", "PROJECT_COST_CALCULATION" -> "cost:project-cost:view";
			default -> null;
		};
	}

	private List<String> allowedActions(PeriodCloseStatus status, Long snapshotId, CurrentUser currentUser) {
		List<String> actions = new ArrayList<>();
		if ((status == PeriodCloseStatus.PENDING_CHECK || status == PeriodCloseStatus.BLOCKED
				|| status == PeriodCloseStatus.READY || status == PeriodCloseStatus.REOPENED)
				&& hasPermission(currentUser, "system:business-period-close:check")) {
			actions.add("CHECK");
		}
		if (status == PeriodCloseStatus.READY && hasPermission(currentUser, "system:business-period-close:close")) {
			actions.add("CLOSE");
		}
		if (status == PeriodCloseStatus.CLOSED && hasPermission(currentUser, "system:business-period-close:reopen")) {
			actions.add("REOPEN");
		}
		if (snapshotId != null && hasPermission(currentUser, "system:business-period-close:snapshot-view")) {
			actions.add("SNAPSHOT_VIEW");
		}
		return actions;
	}

	private Map<String, String> actionDisabledReasons(PeriodCloseRunRow run, CurrentUser currentUser) {
		return actionDisabledReasons(run.status(), run.snapshotId(), currentUser);
	}

	private Map<String, String> actionDisabledReasons(PeriodCloseStatus status, Long snapshotId,
			CurrentUser currentUser) {
		Map<String, String> reasons = new LinkedHashMap<>();
		if (status != PeriodCloseStatus.READY) {
			reasons.put("CLOSE", "仅检查通过且无阻断项的月结运行可以关闭");
		}
		else if (!hasPermission(currentUser, "system:business-period-close:close")) {
			reasons.put("CLOSE", "缺少业务月结关闭权限");
		}
		if (status != PeriodCloseStatus.CLOSED) {
			reasons.put("REOPEN", "仅已关闭的月结运行可以重开");
		}
		else if (!hasPermission(currentUser, "system:business-period-close:reopen")) {
			reasons.put("REOPEN", "缺少业务月结重开权限");
		}
		if (snapshotId == null) {
			reasons.put("SNAPSHOT_VIEW", "当前月结运行尚未生成快照");
		}
		return reasons;
	}

	private List<String> initialAllowedActions(PeriodClosePeriodRow period, CurrentUser currentUser) {
		if (period.status().name().equals("LOCKED")) {
			return List.of();
		}
		return hasPermission(currentUser, "system:business-period-close:check") ? List.of("CHECK") : List.of();
	}

	private Map<String, String> initialActionDisabledReasons(PeriodClosePeriodRow period, CurrentUser currentUser) {
		Map<String, String> reasons = new LinkedHashMap<>();
		if (period.status().name().equals("LOCKED")) {
			reasons.put("CHECK", "手工锁定期间没有 030 月结快照，请先按 016 解锁后再执行业务月结检查");
		}
		else if (!hasPermission(currentUser, "system:business-period-close:check")) {
			reasons.put("CHECK", "缺少业务月结检查权限");
		}
		reasons.put("CLOSE", "需要先完成业务月结检查");
		reasons.put("REOPEN", "当前期间尚未完成 030 业务月结关闭");
		reasons.put("SNAPSHOT_VIEW", "当前期间尚未生成 030 月结快照");
		return reasons;
	}

	private String normalizeCheckResult(String checkResult) {
		if (!PeriodCloseSupport.hasText(checkResult)) {
			return null;
		}
		String normalized = checkResult.trim().toUpperCase(java.util.Locale.ROOT);
		if (List.of("BLOCKING", "WARNING", "PASSED", "NOT_CHECKED").contains(normalized)) {
			return normalized;
		}
		throw new com.qherp.api.common.BusinessException(com.qherp.api.common.ApiErrorCode.PERIOD_CLOSE_ACTION_NOT_ALLOWED);
	}

	private String checkResult(PeriodCloseService.PeriodSummaryResponse summary) {
		if (summary.blockingCount() > 0) {
			return "BLOCKING";
		}
		if (summary.warningCount() > 0) {
			return "WARNING";
		}
		if (summary.latestCheckRunId() != null) {
			return "PASSED";
		}
		return "NOT_CHECKED";
	}

	private List<PeriodCloseService.AuditSummaryResponse> auditSummary(Long runId) {
		return this.jdbcTemplate.query("""
				select id, action, result, reason, error_code, source_fingerprint, operator_username, created_at
				from biz_period_close_audit
				where run_id = ?
				order by id desc
				limit 20
				""", (rs, rowNum) -> new PeriodCloseService.AuditSummaryResponse(rs.getLong("id"),
				rs.getString("action"), rs.getString("result"), rs.getString("reason"), rs.getString("error_code"),
				rs.getString("source_fingerprint"), rs.getString("operator_username"),
				rs.getObject("created_at", OffsetDateTime.class)), runId);
	}

	private JsonNode readJson(String json) {
		try {
			return this.objectMapper.readTree(PeriodCloseSupport.hasText(json) ? json : "{}");
		}
		catch (Exception exception) {
			throw new com.qherp.api.common.BusinessException(com.qherp.api.common.ApiErrorCode.PERIOD_CLOSE_SNAPSHOT_INCOMPLETE);
		}
	}

	private String statusName(PeriodCloseStatus status) {
		return switch (status) {
			case PENDING_CHECK -> "待检查";
			case BLOCKED -> "检查未通过";
			case READY -> "可月结";
			case CLOSED -> "已月结";
			case REOPENED -> "已重开";
		};
	}

}
