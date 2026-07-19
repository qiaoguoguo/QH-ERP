package com.qherp.api.system.periodclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.period.BusinessPeriodLockService;
import com.qherp.api.system.period.BusinessPeriodStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static com.qherp.api.system.periodclose.PeriodCloseSupport.fingerprint;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.hasText;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.normalize;

@Service
class PeriodCloseActionService {

	private final JdbcTemplate jdbcTemplate;

	private final BusinessPeriodLockService lockService;

	private final PeriodCloseRepository repository;

	private final PeriodCloseCheckService checkService;

	private final PeriodCloseSnapshotService snapshotService;

	private final PeriodCloseQueryService queryService;

	private final AuditService auditService;

	private final PeriodCloseAuditLogService auditLogService;

	PeriodCloseActionService(JdbcTemplate jdbcTemplate, BusinessPeriodLockService lockService,
			PeriodCloseRepository repository, PeriodCloseCheckService checkService,
			PeriodCloseSnapshotService snapshotService, PeriodCloseQueryService queryService,
			AuditService auditService, PeriodCloseAuditLogService auditLogService) {
		this.jdbcTemplate = jdbcTemplate;
		this.lockService = lockService;
		this.repository = repository;
		this.checkService = checkService;
		this.snapshotService = snapshotService;
		this.queryService = queryService;
		this.auditService = auditService;
		this.auditLogService = auditLogService;
	}

	@Transactional
	PeriodCloseService.RunResponse check(PeriodCloseService.CheckRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		if (request == null || request.periodId() == null || !hasText(request.idempotencyKey())) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_PERIOD_INVALID);
		}
		String requestFingerprint = fingerprint("CHECK", request.periodId().toString());
		try {
			PeriodCloseService.RunResponse replay = replayIfSame(currentUser, "CHECK", "PERIOD", request.periodId(),
					request.idempotencyKey(), requestFingerprint);
			if (replay != null) {
				return replay("CHECK", replay.id(), request.periodId(), null, null, currentUser, replay);
			}
			this.lockService.acquireForPeriodId(request.periodId());
			replay = replayIfSame(currentUser, "CHECK", "PERIOD", request.periodId(), request.idempotencyKey(),
					requestFingerprint);
			if (replay != null) {
				return replay("CHECK", replay.id(), request.periodId(), null, null, currentUser, replay);
			}
			PeriodClosePeriodRow period = this.repository.findPeriodForUpdate(request.periodId());
			if (period.status() != BusinessPeriodStatus.OPEN) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_PERIOD_INVALID, "业务期间不是开放状态，不能执行月结检查");
			}
			this.repository.assertNoCurrentClosed(period.id());
			PeriodCloseRunRow run = this.repository.currentMutableRun(period)
				.orElseGet(() -> this.repository.createRun(period, currentUser));
			PeriodCloseCheckResult result = this.checkService.evaluate(period);
			this.repository.writeCheckRun(run, period, result, currentUser);
			PeriodCloseService.RunResponse response = this.queryService.run(run.id(), currentUser);
			recordIdempotency(currentUser, "CHECK", "PERIOD", period.id(), request.idempotencyKey(), requestFingerprint,
					run.id(), response.status());
			this.repository.writeAudit(run.id(), period.id(), "CHECK", "SUCCESS", null, response.sourceFingerprint(),
					null, currentUser);
			this.auditService.record(currentUser, "PERIOD_CLOSE_CHECK", "PERIOD_CLOSE", run.id(), period.periodCode(),
					servletRequest);
			return response;
		}
		catch (BusinessException exception) {
			auditFailure(null, request.periodId(), "CHECK", null, exception, currentUser);
			throw exception;
		}
	}

	@Transactional
	PeriodCloseService.RunResponse close(Long runId, PeriodCloseService.CloseRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		if (request == null || !hasText(request.idempotencyKey())) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_IDEMPOTENCY_CONFLICT);
		}
		String requestFingerprint = fingerprint("CLOSE", runId.toString(), String.valueOf(request.version()),
				request.sourceFingerprint(), String.valueOf(request.warningAcknowledged()), normalize(request.reason()));
		Long periodId = null;
		try {
			PeriodCloseService.RunResponse replay = replayIfSame(currentUser, "CLOSE", "RUN", runId,
					request.idempotencyKey(), requestFingerprint);
			if (replay != null) {
				return replay("CLOSE", runId, replay.periodId(), null, replay.sourceFingerprint(), currentUser, replay);
			}
			PeriodCloseRunRow current = this.repository.findRun(runId);
			periodId = current.periodId();
			this.lockService.acquireForPeriodId(current.periodId());
			replay = replayIfSame(currentUser, "CLOSE", "RUN", runId, request.idempotencyKey(), requestFingerprint);
			if (replay != null) {
				return replay("CLOSE", runId, replay.periodId(), null, replay.sourceFingerprint(), currentUser, replay);
			}
			PeriodClosePeriodRow period = this.repository.findPeriodForUpdate(current.periodId());
			PeriodCloseRunRow run = this.repository.findRunForUpdate(runId);
			if (run.status() != PeriodCloseStatus.READY) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_ACTION_NOT_ALLOWED, "当前月结运行不能关闭");
			}
			if (period.status() != BusinessPeriodStatus.OPEN) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_PERIOD_INVALID, "业务期间不是开放状态，不能关闭");
			}
			assertVersion(request.version(), run.version());
			if (!hasText(request.sourceFingerprint()) || !request.sourceFingerprint().equals(run.sourceFingerprint())) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_SOURCE_CHANGED);
			}
			if (run.warningCount() > 0 && (!Boolean.TRUE.equals(request.warningAcknowledged())
					|| !hasText(request.reason()))) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_WARNING_ACK_REQUIRED);
			}
			if (!hasText(request.reason())) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_REASON_REQUIRED);
			}
			PeriodCloseCheckResult result = this.checkService.evaluate(period);
			this.repository.writeCheckRun(run, period, result, currentUser);
			if (result.blockingCount() > 0) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_BLOCKED);
			}
			if (!result.sourceFingerprint().equals(request.sourceFingerprint())) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_SOURCE_CHANGED);
			}
			long snapshotId = this.snapshotService.createSnapshot(run, period, result, currentUser);
			OffsetDateTime now = OffsetDateTime.now();
			int updated = this.jdbcTemplate.update("""
					update biz_period_close_run
					set status = 'CLOSED',
					    snapshot_id = ?,
					    warning_acknowledged = ?,
					    warning_reason = ?,
					    closed_by = ?,
					    closed_at = ?,
					    close_reason = ?,
					    updated_by = ?,
					    updated_at = ?,
					    version = version + 1
					where id = ?
					and status = 'READY'
					""", snapshotId, Boolean.TRUE.equals(request.warningAcknowledged()), request.reason().trim(),
					currentUser.username(), now, request.reason().trim(), currentUser.username(), now, run.id());
			if (updated != 1) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_VERSION_CONFLICT);
			}
			this.jdbcTemplate.update("""
					update biz_business_period
					set status = 'LOCKED',
					    locked_by = ?,
					    locked_at = ?,
					    lock_reason = ?,
					    updated_at = ?
					where id = ?
					and status = 'OPEN'
					""", currentUser.username(), now, "030 业务月结关闭：" + request.reason().trim(), now, period.id());
			this.jdbcTemplate.update("""
					insert into biz_business_period_audit (
						period_id, period_code, action, reason, operator_username, created_at
					)
					values (?, ?, 'PERIOD_CLOSE', ?, ?, ?)
					""", period.id(), period.periodCode(), request.reason().trim(), currentUser.username(), now);
			PeriodCloseService.RunResponse response = this.queryService.run(run.id(), currentUser);
			recordIdempotency(currentUser, "CLOSE", "RUN", run.id(), request.idempotencyKey(), requestFingerprint,
					run.id(), response.status());
			this.repository.writeAudit(run.id(), period.id(), "CLOSE", "SUCCESS", request.reason().trim(),
					response.sourceFingerprint(), null, currentUser);
			this.auditService.record(currentUser, "PERIOD_CLOSE_CLOSE", "PERIOD_CLOSE", run.id(), period.periodCode(),
					servletRequest);
			return response;
		}
		catch (BusinessException exception) {
			auditFailure(runId, periodId, "CLOSE", request.sourceFingerprint(), exception, currentUser);
			throw exception;
		}
	}

	@Transactional
	PeriodCloseService.RunResponse reopen(Long runId, PeriodCloseService.ReopenRequest request,
			CurrentUser currentUser, HttpServletRequest servletRequest) {
		if (request == null || !hasText(request.idempotencyKey())) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_IDEMPOTENCY_CONFLICT);
		}
		if (!hasText(request.reason())) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_REASON_REQUIRED);
		}
		String requestFingerprint = fingerprint("REOPEN", runId.toString(), String.valueOf(request.version()),
				normalize(request.reason()));
		Long periodId = null;
		try {
			PeriodCloseService.RunResponse replay = replayIfSame(currentUser, "REOPEN", "RUN", runId,
					request.idempotencyKey(), requestFingerprint);
			if (replay != null) {
				return replay("REOPEN", runId, replay.periodId(), request.reason(), replay.sourceFingerprint(),
						currentUser, replay);
			}
			PeriodCloseRunRow current = this.repository.findRun(runId);
			periodId = current.periodId();
			this.lockService.acquireForPeriodId(current.periodId());
			replay = replayIfSame(currentUser, "REOPEN", "RUN", runId, request.idempotencyKey(), requestFingerprint);
			if (replay != null) {
				return replay("REOPEN", runId, replay.periodId(), request.reason(), replay.sourceFingerprint(),
						currentUser, replay);
			}
			PeriodClosePeriodRow period = this.repository.findPeriodForUpdate(current.periodId());
			PeriodCloseRunRow run = this.repository.findRunForUpdate(runId);
			if (run.status() != PeriodCloseStatus.CLOSED) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_ACTION_NOT_ALLOWED, "当前月结运行不能重开");
			}
			assertVersion(request.version(), run.version());
			OffsetDateTime now = OffsetDateTime.now();
			this.jdbcTemplate.update("""
					update biz_period_close_run
					set status = 'REOPENED',
					    reopened_by = ?,
					    reopened_at = ?,
					    reopen_reason = ?,
					    updated_by = ?,
					    updated_at = ?,
					    version = version + 1
					where id = ?
					and status = 'CLOSED'
					""", currentUser.username(), now, request.reason().trim(), currentUser.username(), now, run.id());
			this.jdbcTemplate.update("""
					update biz_business_period
					set status = 'OPEN',
					    unlocked_by = ?,
					    unlocked_at = ?,
					    unlock_reason = ?,
					    updated_at = ?
					where id = ?
					and status = 'LOCKED'
					""", currentUser.username(), now, "030 业务月结重开：" + request.reason().trim(), now, period.id());
			this.jdbcTemplate.update("""
					insert into biz_business_period_audit (
						period_id, period_code, action, reason, operator_username, created_at
					)
					values (?, ?, 'PERIOD_REOPEN', ?, ?, ?)
					""", period.id(), period.periodCode(), request.reason().trim(), currentUser.username(), now);
			PeriodCloseService.RunResponse response = this.queryService.run(run.id(), currentUser);
			recordIdempotency(currentUser, "REOPEN", "RUN", run.id(), request.idempotencyKey(), requestFingerprint,
					run.id(), response.status());
			this.repository.writeAudit(run.id(), period.id(), "REOPEN", "SUCCESS", request.reason().trim(),
					response.sourceFingerprint(), null, currentUser);
			this.auditService.record(currentUser, "PERIOD_CLOSE_REOPEN", "PERIOD_CLOSE", run.id(), period.periodCode(),
					servletRequest);
			return response;
		}
		catch (BusinessException exception) {
			auditFailure(runId, periodId, "REOPEN", null, exception, currentUser);
			throw exception;
		}
	}

	private PeriodCloseService.RunResponse replayIfSame(CurrentUser currentUser, String action, String resourceType,
			Long resourceId, String idempotencyKey, String requestFingerprint) {
		if (!hasText(idempotencyKey)) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_IDEMPOTENCY_CONFLICT);
		}
		List<PeriodCloseIdempotencyRow> rows = this.repository.idempotencyRows(currentUser, action, resourceType,
				resourceId, idempotencyKey);
		if (rows.isEmpty()) {
			return null;
		}
		PeriodCloseIdempotencyRow row = rows.getFirst();
		if (!row.requestFingerprint().equals(requestFingerprint)) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_IDEMPOTENCY_CONFLICT);
		}
		return this.queryService.run(row.responseRunId(), currentUser);
	}

	private void recordIdempotency(CurrentUser currentUser, String action, String resourceType, Long resourceId,
			String idempotencyKey, String requestFingerprint, Long responseRunId, String responseStatus) {
		int inserted = this.repository.insertIdempotency(currentUser, action, resourceType, resourceId, idempotencyKey,
				requestFingerprint, responseRunId, responseStatus);
		if (inserted == 0) {
			PeriodCloseService.RunResponse replay = replayIfSame(currentUser, action, resourceType, resourceId,
					idempotencyKey, requestFingerprint);
			if (replay == null) {
				throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_IDEMPOTENCY_CONFLICT);
			}
		}
	}

	private PeriodCloseService.RunResponse replay(String action, Long runId, Long periodId, String reason,
			String sourceFingerprint, CurrentUser currentUser, PeriodCloseService.RunResponse replay) {
		this.auditLogService.write(runId, periodId, action, "REPLAY", reason, sourceFingerprint, null, currentUser);
		return replay;
	}

	private void auditFailure(Long runId, Long periodId, String action, String sourceFingerprint,
			BusinessException exception, CurrentUser currentUser) {
		String result = failureResult(exception);
		String errorCode = exception.errorCode().name();
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			this.auditLogService.write(runId, periodId, action, result, null, sourceFingerprint, errorCode,
					currentUser);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCompletion(int status) {
				auditLogService.write(runId, periodId, action, result, null, sourceFingerprint, errorCode, currentUser);
			}

		});
	}

	private String failureResult(BusinessException exception) {
		return switch (exception.errorCode()) {
			case PERIOD_CLOSE_IDEMPOTENCY_CONFLICT, PERIOD_CLOSE_VERSION_CONFLICT, PERIOD_CLOSE_SOURCE_CHANGED,
					PERIOD_CLOSE_ACTION_NOT_ALLOWED, PERIOD_CLOSE_ALREADY_CLOSED -> "CONFLICT";
			default -> "FAILURE";
		};
	}

	private void assertVersion(Long requestVersion, Long currentVersion) {
		if (requestVersion == null || !Objects.equals(requestVersion, currentVersion)) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_VERSION_CONFLICT);
		}
	}

}
