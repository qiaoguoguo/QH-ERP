package com.qherp.api.system.financialclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class FinancialCloseQueryService {

	private final JdbcTemplate jdbcTemplate;

	public FinancialCloseQueryService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> periods(String periodCode, int page, int pageSize,
			CurrentUser currentUser) {
		int safePageSize = FinancialCloseSupport.listLimit(pageSize);
		int safePage = FinancialCloseSupport.page(page);
		String normalized = FinancialCloseSupport.text(periodCode);
		List<Object> args = new ArrayList<>();
		String where = "where l.code = 'MAIN'";
		if (normalized != null && !normalized.isBlank()) {
			where += " and p.period_code = ?";
			args.add(normalized);
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_accounting_period p
				join gl_ledger l on l.id = p.ledger_id
				%s
				""".formatted(where), Long.class, args.toArray());
		args.add(safePageSize);
		args.add(FinancialCloseSupport.offset(safePage, safePageSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select p.id, p.period_code, p.start_date, p.end_date, p.status, p.version,
				       c.id as latest_check_id, c.status as latest_check_status,
				       c.source_fingerprint as latest_check_fingerprint, c.version as latest_check_version,
				       r.id as close_run_id, r.status as close_status, r.close_version, r.version as close_run_version
				from gl_accounting_period p
				join gl_ledger l on l.id = p.ledger_id
				left join lateral (
					select id, status, source_fingerprint, version
					from fin_close_check_run
					where period_id = p.id
					order by created_at desc, id desc
					limit 1
				) c on true
				left join lateral (
					select id, status, close_version, version
					from fin_close_run
					where period_id = p.id
					and status = 'CLOSED'
					order by close_version desc, id desc
					limit 1
				) r on true
				%s
				order by p.start_date desc, p.id desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> periodMap(rs, currentUser), args.toArray());
		return PageResponse.of(items, safePage, safePageSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> period(Long periodId, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select p.id, p.period_code, p.start_date, p.end_date, p.status, p.version,
				       c.id as latest_check_id, c.status as latest_check_status,
				       c.source_fingerprint as latest_check_fingerprint, c.version as latest_check_version,
				       r.id as close_run_id, r.status as close_status, r.close_version, r.version as close_run_version
				from gl_accounting_period p
				join gl_ledger l on l.id = p.ledger_id
				left join lateral (
					select id, status, source_fingerprint, version
					from fin_close_check_run
					where period_id = p.id
					order by created_at desc, id desc
					limit 1
				) c on true
				left join lateral (
					select id, status, close_version, version
					from fin_close_run
					where period_id = p.id
					and status = 'CLOSED'
					order by close_version desc, id desc
					limit 1
				) r on true
				where l.code = 'MAIN'
				and p.id = ?
				""", (rs, rowNum) -> periodMap(rs, currentUser), periodId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_PERIOD_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> checkRun(Long id, CurrentUser currentUser) {
		Map<String, Object> result = this.jdbcTemplate.query("""
				select c.id, c.period_id, p.period_code, c.status, c.close_version, c.source_fingerprint,
				       c.blocking_count, c.warning_count, c.created_by, c.created_at, c.completed_at, c.version
				from fin_close_check_run c
				join gl_accounting_period p on p.id = c.period_id
				where c.id = ?
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("periodId", rs.getLong("period_id"));
			map.put("periodCode", rs.getString("period_code"));
			map.put("status", rs.getString("status"));
			map.put("closeVersion", rs.getLong("close_version"));
			map.put("sourceFingerprint", FinancialCloseSupport.sourceVisible(currentUser)
					? rs.getString("source_fingerprint") : null);
			map.put("blockingCount", rs.getInt("blocking_count"));
			map.put("warningCount", rs.getInt("warning_count"));
			map.put("createdBy", rs.getString("created_by"));
			map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			map.put("completedAt", rs.getObject("completed_at", OffsetDateTime.class));
			map.put("version", rs.getLong("version"));
			FinancialCloseSupport.putVisibility(map, currentUser);
			return map;
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_NOT_READY));
		result.put("items", checkItems(id, currentUser));
		result.put("allowedActions", "READY".equals(result.get("status")) ? List.of("CLOSE") : List.of());
		result.put("actionDisabledReasons", Map.of());
		return result;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> closeRun(Long id, CurrentUser currentUser) {
		Map<String, Object> result = this.jdbcTemplate.query("""
				select r.id, r.period_id, p.period_code, r.check_run_id, r.close_version, r.status,
				       r.source_fingerprint, r.snapshot_id, r.closed_by, r.closed_at, r.close_reason,
				       r.reopened_by, r.reopened_at, r.reopen_reason, r.version
				from fin_close_run r
				join gl_accounting_period p on p.id = r.period_id
				where r.id = ?
				""", (rs, rowNum) -> closeRunMap(rs, currentUser), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
		result.put("allowedActions", "CLOSED".equals(result.get("status")) ? List.of("REOPEN") : List.of());
		return result;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> reopenRequest(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select req.id, req.close_run_id, req.period_id, p.period_code, req.request_no, req.status,
				       req.reason, req.approval_instance_id, req.requested_by_user_id, req.requested_by_username,
				       req.applied_by, req.applied_at, req.created_at, req.updated_at, req.version
				from fin_close_reopen_request req
				join gl_accounting_period p on p.id = req.period_id
				where req.id = ?
				""", (rs, rowNum) -> reopenRequestMap(rs, currentUser), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> profitLossTransfers(Long periodId, int page, int pageSize,
			CurrentUser currentUser) {
		int safePageSize = FinancialCloseSupport.listLimit(pageSize);
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_close_profit_loss_transfer
				where period_id = ?
				""", Long.class, periodId);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id, period_id, status, source_fingerprint, voucher_id, voucher_status, debit_total,
				       credit_total, line_json::text as line_json, reason, created_by, created_at, updated_at, version
				from fin_close_profit_loss_transfer
				where period_id = ?
				order by created_at desc, id desc
				limit ? offset ?
				""", (rs, rowNum) -> {
			boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
			boolean sourceVisible = FinancialCloseSupport.sourceVisible(currentUser);
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("periodId", rs.getLong("period_id"));
			map.put("status", rs.getString("status"));
			map.put("sourceFingerprint", sourceVisible ? rs.getString("source_fingerprint") : null);
			map.put("voucherId", FinancialCloseSupport.nullableLong(rs, "voucher_id"));
			map.put("voucherStatus", rs.getString("voucher_status"));
			map.put("debitTotal", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("debit_total"),
					amountVisible));
			map.put("creditTotal", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("credit_total"),
					amountVisible));
			map.put("lineJson", amountVisible && sourceVisible ? rs.getString("line_json") : null);
			map.put("reason", rs.getString("reason"));
			map.put("createdBy", rs.getString("created_by"));
			map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			map.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
			map.put("version", rs.getLong("version"));
			FinancialCloseSupport.putVisibility(map, currentUser);
			map.put("allowedActions", List.of());
			map.put("actionDisabledReasons", Map.of());
			return map;
		}, periodId, safePageSize, FinancialCloseSupport.offset(page, safePageSize));
		return PageResponse.of(items, page, safePageSize, total == null ? 0 : total);
	}

	private List<Map<String, Object>> checkItems(Long checkRunId, CurrentUser currentUser) {
		boolean sourceVisible = FinancialCloseSupport.sourceVisible(currentUser);
		return this.jdbcTemplate.query("""
				select id, check_code, severity, passed, actual_value, expected_value, conclusion, source_type,
				       source_id, source_no, source_restricted
				from fin_close_check_item
				where check_run_id = ?
				order by id
				""", (rs, rowNum) -> {
			Map<String, Object> item = FinancialCloseSupport.map();
			item.put("id", rs.getLong("id"));
			item.put("checkCode", rs.getString("check_code"));
			item.put("severity", rs.getString("severity"));
			item.put("passed", rs.getBoolean("passed"));
			item.put("actualValue", rs.getString("actual_value"));
			item.put("expectedValue", rs.getString("expected_value"));
			item.put("conclusion", rs.getString("conclusion"));
			boolean reveal = sourceVisible && !rs.getBoolean("source_restricted");
			item.put("sourceType", reveal ? rs.getString("source_type") : null);
			item.put("sourceId", reveal ? FinancialCloseSupport.nullableLong(rs, "source_id") : null);
			item.put("sourceNo", reveal ? rs.getString("source_no") : null);
			return item;
		}, checkRunId);
	}

	private Map<String, Object> periodMap(ResultSet rs, CurrentUser currentUser) throws SQLException {
		Map<String, Object> map = FinancialCloseSupport.map();
		map.put("id", rs.getLong("id"));
		map.put("periodCode", rs.getString("period_code"));
		map.put("startDate", rs.getObject("start_date", LocalDate.class));
		map.put("endDate", rs.getObject("end_date", LocalDate.class));
		map.put("status", rs.getString("status"));
		map.put("latestCheckId", FinancialCloseSupport.nullableLong(rs, "latest_check_id"));
		map.put("latestCheckStatus", rs.getString("latest_check_status"));
		map.put("latestCheckFingerprint", FinancialCloseSupport.sourceVisible(currentUser)
				? rs.getString("latest_check_fingerprint") : null);
		map.put("closeRunId", FinancialCloseSupport.nullableLong(rs, "close_run_id"));
		map.put("closeStatus", rs.getString("close_status"));
		map.put("closeVersion", FinancialCloseSupport.nullableLong(rs, "close_version"));
		map.put("version", rs.getLong("version"));
		FinancialCloseSupport.putVisibility(map, currentUser);
		map.put("allowedActions", periodActions(rs.getString("status"), rs.getString("latest_check_status"),
				FinancialCloseSupport.nullableLong(rs, "close_run_id")));
		map.put("actionDisabledReasons", Map.of());
		return map;
	}

	private Map<String, Object> closeRunMap(ResultSet rs, CurrentUser currentUser) throws SQLException {
		Map<String, Object> map = FinancialCloseSupport.map();
		map.put("id", rs.getLong("id"));
		map.put("periodId", rs.getLong("period_id"));
		map.put("periodCode", rs.getString("period_code"));
		map.put("checkRunId", rs.getLong("check_run_id"));
		map.put("closeVersion", rs.getLong("close_version"));
		map.put("status", rs.getString("status"));
		map.put("sourceFingerprint", FinancialCloseSupport.sourceVisible(currentUser)
				? rs.getString("source_fingerprint") : null);
		map.put("snapshotId", FinancialCloseSupport.nullableLong(rs, "snapshot_id"));
		map.put("closedBy", rs.getString("closed_by"));
		map.put("closedAt", rs.getObject("closed_at", OffsetDateTime.class));
		map.put("closeReason", rs.getString("close_reason"));
		map.put("reopenedBy", rs.getString("reopened_by"));
		map.put("reopenedAt", rs.getObject("reopened_at", OffsetDateTime.class));
		map.put("reopenReason", rs.getString("reopen_reason"));
		map.put("version", rs.getLong("version"));
		FinancialCloseSupport.putVisibility(map, currentUser);
		return map;
	}

	private Map<String, Object> reopenRequestMap(ResultSet rs, CurrentUser currentUser) throws SQLException {
		Map<String, Object> map = FinancialCloseSupport.map();
		map.put("id", rs.getLong("id"));
		map.put("closeRunId", rs.getLong("close_run_id"));
		map.put("periodId", rs.getLong("period_id"));
		map.put("periodCode", rs.getString("period_code"));
		map.put("requestNo", rs.getString("request_no"));
		map.put("status", rs.getString("status"));
		map.put("reason", rs.getString("reason"));
		map.put("approvalInstanceId", FinancialCloseSupport.nullableLong(rs, "approval_instance_id"));
		map.put("requestedByUserId", rs.getLong("requested_by_user_id"));
		map.put("requestedByUsername", rs.getString("requested_by_username"));
		map.put("appliedBy", rs.getString("applied_by"));
		map.put("appliedAt", rs.getObject("applied_at", OffsetDateTime.class));
		map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
		map.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
		map.put("version", rs.getLong("version"));
		FinancialCloseSupport.putVisibility(map, currentUser);
		return map;
	}

	private List<String> periodActions(String periodStatus, String latestCheckStatus, Long closeRunId) {
		List<String> actions = new ArrayList<>();
		if ("OPEN".equals(periodStatus)) {
			actions.add("CHECK");
			if ("READY".equals(latestCheckStatus)) {
				actions.add("CLOSE");
			}
		}
		if ("CLOSED".equals(periodStatus) && closeRunId != null) {
			actions.add("REOPEN");
		}
		return actions;
	}

}
