package com.qherp.api.system.periodclose;

import com.qherp.api.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PeriodCloseAuditLogService {

	private final JdbcTemplate jdbcTemplate;

	PeriodCloseAuditLogService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void write(Long runId, Long periodId, String action, String result, String reason, String sourceFingerprint,
			String errorCode, CurrentUser currentUser) {
		Long resolvedPeriodId = periodId == null && runId != null ? periodId(runId) : periodId;
		this.jdbcTemplate.update("""
				insert into biz_period_close_audit (
					run_id, period_id, action, result, reason, source_fingerprint, error_code,
					operator_user_id, operator_username
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", runId, resolvedPeriodId, action, result, reason, sourceFingerprint, errorCode,
				currentUser == null ? null : currentUser.id(), currentUser == null ? "system" : currentUser.username());
	}

	private Long periodId(Long runId) {
		return this.jdbcTemplate.query("""
				select period_id
				from biz_period_close_run
				where id = ?
				""", (rs) -> rs.next() ? rs.getLong("period_id") : null, runId);
	}

}
