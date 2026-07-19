package com.qherp.api.system.periodclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.period.BusinessPeriodStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.qherp.api.system.periodclose.PeriodCloseSupport.nullableLong;

@Repository
class PeriodCloseRepository {

	private final JdbcTemplate jdbcTemplate;

	PeriodCloseRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<PeriodClosePeriodRow> periods(String where, Object[] args) {
		return this.jdbcTemplate.query("""
				select p.id, p.period_code, p.period_name, p.start_date, p.end_date, p.status
				from biz_business_period p
				%s
				order by p.start_date desc, p.id desc
				""".formatted(where), this::mapPeriod, args);
	}

	PeriodClosePeriodRow findPeriod(Long periodId) {
		List<PeriodClosePeriodRow> rows = this.jdbcTemplate.query("""
				select id, period_code, period_name, start_date, end_date, status
				from biz_business_period
				where id = ?
				""", this::mapPeriod, periodId);
		if (rows.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_PERIOD_INVALID);
		}
		return rows.getFirst();
	}

	PeriodClosePeriodRow findPeriodForUpdate(Long periodId) {
		List<PeriodClosePeriodRow> rows = this.jdbcTemplate.query("""
				select id, period_code, period_name, start_date, end_date, status
				from biz_business_period
				where id = ?
				for update
				""", this::mapPeriod, periodId);
		if (rows.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_PERIOD_INVALID);
		}
		return rows.getFirst();
	}

	PeriodCloseRunRow findRun(Long runId) {
		List<PeriodCloseRunRow> rows = this.jdbcTemplate.query("select * from biz_period_close_run where id = ?",
				this::mapRun, runId);
		if (rows.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_NOT_FOUND);
		}
		return rows.getFirst();
	}

	PeriodCloseRunRow findRunForUpdate(Long runId) {
		List<PeriodCloseRunRow> rows = this.jdbcTemplate.query(
				"select * from biz_period_close_run where id = ? for update", this::mapRun, runId);
		if (rows.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_NOT_FOUND);
		}
		return rows.getFirst();
	}

	Optional<PeriodCloseRunRow> currentMutableRun(PeriodClosePeriodRow period) {
		List<PeriodCloseRunRow> rows = this.jdbcTemplate.query("""
				select r.*
				from biz_period_close_run r
				where r.period_id = ?
				and r.status in ('PENDING_CHECK', 'BLOCKED', 'READY')
				order by r.revision_no desc, r.id desc
				limit 1
				""", this::mapRun, period.id());
		return rows.stream().findFirst();
	}

	PeriodCloseRunRow createRun(PeriodClosePeriodRow period, CurrentUser currentUser) {
		Integer revisionNo = this.jdbcTemplate.queryForObject("""
				select coalesce(max(revision_no), 0) + 1
				from biz_period_close_run
				where period_id = ?
				""", Integer.class, period.id());
		Long id = this.jdbcTemplate.queryForObject("""
				insert into biz_period_close_run (
					period_id, revision_no, status, created_by, updated_by
				)
				values (?, ?, 'PENDING_CHECK', ?, ?)
				returning id
				""", Long.class, period.id(), revisionNo, currentUser.username(), currentUser.username());
		return findRun(id);
	}

	void assertNoCurrentClosed(Long periodId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from biz_period_close_run
				where period_id = ?
				and status = 'CLOSED'
				""", Long.class, periodId);
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_ALREADY_CLOSED);
		}
	}

	void writeCheckRun(PeriodCloseRunRow run, PeriodClosePeriodRow period, PeriodCloseCheckResult result,
			CurrentUser currentUser) {
		Long checkRunId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_close_check_run (
					run_id, period_id, revision_no, status, schema_version, source_fingerprint,
					inventory_fingerprint, wip_fingerprint, project_cost_fingerprint, report_fingerprint,
					blocking_count, warning_count, started_by, started_at, completed_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, run.id(), period.id(), run.revisionNo(), result.status().name(),
				PeriodCloseSupport.SCHEMA_VERSION, result.sourceFingerprint(), result.inventoryFingerprint(),
				result.wipFingerprint(), result.projectCostFingerprint(), result.reportFingerprint(),
				result.blockingCount(), result.warningCount(), currentUser.username(), OffsetDateTime.now(),
				OffsetDateTime.now());
		for (PeriodCloseCheckItemDraft item : result.items()) {
			this.jdbcTemplate.update("""
					insert into biz_period_close_check_item (
						check_run_id, domain, check_code, severity, source_restricted, object_type,
						object_id, object_no, title, description, suggestion, source_route
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
					""", checkRunId, item.domain(), item.checkCode(), item.severity(), item.sourceRestricted(),
					item.objectType(), item.objectId(), item.objectNo(), item.title(), item.description(),
					item.suggestion(), item.sourceRouteJson());
		}
		this.jdbcTemplate.update("""
				update biz_period_close_run
				set status = ?,
				    latest_check_run_id = ?,
				    source_fingerprint = ?,
				    inventory_fingerprint = ?,
				    wip_fingerprint = ?,
				    project_cost_fingerprint = ?,
				    report_fingerprint = ?,
				    blocking_count = ?,
				    warning_count = ?,
				    updated_by = ?,
				    updated_at = ?,
				    version = version + 1
				where id = ?
				""", result.status().name(), checkRunId, result.sourceFingerprint(), result.inventoryFingerprint(),
				result.wipFingerprint(), result.projectCostFingerprint(), result.reportFingerprint(),
				result.blockingCount(), result.warningCount(), currentUser.username(), OffsetDateTime.now(), run.id());
	}

	List<PeriodCloseService.RunBriefResponse> runHistory(Long periodId) {
		return this.jdbcTemplate.query("""
				select id, revision_no, status, latest_check_run_id, snapshot_id, blocking_count, warning_count,
				       closed_at, reopened_at, version
				from biz_period_close_run
				where period_id = ?
				order by revision_no desc, id desc
				""", (rs, rowNum) -> new PeriodCloseService.RunBriefResponse(rs.getLong("id"),
				rs.getInt("revision_no"), rs.getString("status"), nullableLong(rs, "latest_check_run_id"),
				nullableLong(rs, "snapshot_id"), rs.getInt("blocking_count"), rs.getInt("warning_count"),
				rs.getObject("closed_at", OffsetDateTime.class), rs.getObject("reopened_at", OffsetDateTime.class),
				rs.getLong("version")), periodId);
	}

	PeriodCloseSnapshotRow findSnapshot(Long snapshotId) {
		List<PeriodCloseSnapshotRow> rows = this.jdbcTemplate.query("""
				select id, run_id, source_check_run_id, source_fingerprint, inventory_fingerprint,
				       wip_fingerprint, project_cost_fingerprint, report_fingerprint, generated_by, generated_at
				from biz_period_snapshot
				where id = ?
				""", (rs, rowNum) -> new PeriodCloseSnapshotRow(rs.getLong("id"), rs.getLong("run_id"),
				rs.getLong("source_check_run_id"), rs.getString("source_fingerprint"),
				rs.getString("inventory_fingerprint"), rs.getString("wip_fingerprint"),
				rs.getString("project_cost_fingerprint"), rs.getString("report_fingerprint"), rs.getString("generated_by"),
				rs.getObject("generated_at", OffsetDateTime.class)), snapshotId);
		if (rows.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_SNAPSHOT_INCOMPLETE);
		}
		return rows.getFirst();
	}

	void writeAudit(Long runId, Long periodId, String action, String result, String reason,
			String sourceFingerprint, String errorCode, CurrentUser currentUser) {
		this.jdbcTemplate.update("""
				insert into biz_period_close_audit (
					run_id, period_id, action, result, reason, source_fingerprint, error_code,
					operator_user_id, operator_username
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", runId, periodId, action, result, reason, sourceFingerprint, errorCode,
				currentUser == null ? null : currentUser.id(), currentUser == null ? "system" : currentUser.username());
	}

	List<PeriodCloseIdempotencyRow> idempotencyRows(CurrentUser currentUser, String action, String resourceType,
			Long resourceId, String idempotencyKey) {
		return this.jdbcTemplate.query("""
				select request_fingerprint, response_run_id
				from biz_period_close_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new PeriodCloseIdempotencyRow(rs.getString("request_fingerprint"),
				rs.getLong("response_run_id")), currentUser.id(), action, resourceType, resourceId, idempotencyKey);
	}

	int insertIdempotency(CurrentUser currentUser, String action, String resourceType, Long resourceId,
			String idempotencyKey, String requestFingerprint, Long responseRunId, String responseStatus) {
		return this.jdbcTemplate.update("""
				insert into biz_period_close_action_idempotency (
					operator_user_id, action, resource_type, resource_id, idempotency_key,
					request_fingerprint, response_run_id, response_status
				)
				values (?, ?, ?, ?, ?, ?, ?, ?)
				on conflict (operator_user_id, action, resource_type, resource_id, idempotency_key) do nothing
				""", currentUser.id(), action, resourceType, resourceId, idempotencyKey, requestFingerprint,
				responseRunId, responseStatus);
	}

	private PeriodClosePeriodRow mapPeriod(ResultSet rs, int rowNum) throws SQLException {
		return new PeriodClosePeriodRow(rs.getLong("id"), rs.getString("period_code"), rs.getString("period_name"),
				rs.getObject("start_date", java.time.LocalDate.class),
				rs.getObject("end_date", java.time.LocalDate.class),
				BusinessPeriodStatus.valueOf(rs.getString("status")));
	}

	private PeriodCloseRunRow mapRun(ResultSet rs, int rowNum) throws SQLException {
		return new PeriodCloseRunRow(rs.getLong("id"), rs.getLong("period_id"), rs.getInt("revision_no"),
				PeriodCloseStatus.valueOf(rs.getString("status")), nullableLong(rs, "latest_check_run_id"),
				nullableLong(rs, "snapshot_id"), rs.getString("source_fingerprint"),
				rs.getString("inventory_fingerprint"), rs.getString("wip_fingerprint"),
				rs.getString("project_cost_fingerprint"), rs.getString("report_fingerprint"),
				rs.getInt("blocking_count"), rs.getInt("warning_count"), rs.getBoolean("warning_acknowledged"),
				rs.getString("warning_reason"), rs.getString("closed_by"),
				rs.getObject("closed_at", OffsetDateTime.class), rs.getString("close_reason"),
				rs.getString("reopened_by"), rs.getObject("reopened_at", OffsetDateTime.class),
				rs.getString("reopen_reason"), rs.getLong("version"));
	}

}
