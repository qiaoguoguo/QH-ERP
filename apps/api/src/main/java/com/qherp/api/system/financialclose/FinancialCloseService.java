package com.qherp.api.system.financialclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.platform.PlatformApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class FinancialCloseService {

	private static final String TARGET_CLOSE_RUN = "FIN_CLOSE_RUN";

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final FinancialCloseCheckService checkService;

	private final FinancialCloseQueryService queryService;

	private final FinancialCloseAuditService auditService;

	private final PlatformApprovalService approvalService;

	public FinancialCloseService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
			FinancialCloseCheckService checkService, FinancialCloseQueryService queryService,
			FinancialCloseAuditService auditService, @Lazy PlatformApprovalService approvalService) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.checkService = checkService;
		this.queryService = queryService;
		this.auditService = auditService;
		this.approvalService = approvalService;
	}

	@Transactional
	public Map<String, Object> close(Long checkRunId, FinancialCloseModels.CloseRequest request,
			CurrentUser operator) {
		CheckRunRow checkRun = lockCheckRun(checkRunId);
		requireVersion(checkRun.version(), request == null ? null : request.version());
		String reason = FinancialCloseSupport.requiredText(request == null ? null : request.reason(),
				ApiErrorCode.PERIOD_CLOSE_REASON_REQUIRED);
		if (!"READY".equals(checkRun.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_NOT_READY);
		}
		FinancialCloseCheckService.PeriodRow period = lockPeriod(checkRun.periodId());
		if (!"OPEN".equals(period.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_PERIOD_CLOSED);
		}
		FinancialCloseCheckService.BusinessCloseRun businessCloseRun = this.checkService
			.businessCloseRun(period.periodCode());
		if (businessCloseRun == null) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_NOT_READY);
		}
		String currentFingerprint = this.checkService.currentSourceFingerprint(period.id());
		if (!currentFingerprint.equals(checkRun.sourceFingerprint())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_STALE);
		}
		Long alreadyClosed = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_close_run
				where period_id = ?
				and status = 'CLOSED'
				""", Long.class, period.id());
		if (alreadyClosed != null && alreadyClosed > 0) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_PERIOD_CLOSED);
		}
		Long closeVersion = this.jdbcTemplate.queryForObject("""
				select coalesce(max(close_version), 0) + 1
				from fin_close_run
				where period_id = ?
				""", Long.class, period.id());
		Long closeRunId = this.jdbcTemplate.queryForObject("""
				insert into fin_close_run (
					ledger_id, period_id, check_run_id, close_version, status, source_fingerprint, closed_by,
					closed_at, close_reason
				)
				values (?, ?, ?, ?, 'CLOSED', ?, ?, ?, ?)
				returning id
				""", Long.class, period.ledgerId(), period.id(), checkRun.id(), closeVersion, checkRun.sourceFingerprint(),
				operator.username(), OffsetDateTime.now(), reason);
		Long snapshotId = this.jdbcTemplate.queryForObject("""
				insert into fin_close_snapshot (
					close_run_id, period_id, close_version, source_fingerprint, trial_balance_json,
					bank_reconciliation_json, tax_summary_json, business_period_close_run_id, created_by
				)
				values (?, ?, ?, ?, ?::jsonb, '{}'::jsonb, '{}'::jsonb, ?, ?)
				returning id
				""", Long.class, closeRunId, period.id(), closeVersion, checkRun.sourceFingerprint(),
				json(Map.of("periodCode", period.periodCode(), "closedAt", OffsetDateTime.now().toString())),
				businessCloseRun.id(), operator.username());
		this.jdbcTemplate.update("""
				update fin_close_run
				set snapshot_id = ?, updated_at = now()
				where id = ?
				""", snapshotId, closeRunId);
		this.jdbcTemplate.update("""
				update fin_close_check_run
				set status = 'CONSUMED', version = version + 1
				where id = ?
				""", checkRun.id());
		int periodUpdated = this.jdbcTemplate.update("""
				update gl_accounting_period
				set status = 'CLOSED', updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				and status = 'OPEN'
				""", operator.username(), OffsetDateTime.now(), period.id());
		if (periodUpdated == 0) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		this.auditService.success(operator, "FIN_CLOSE_PERIOD_CLOSE", TARGET_CLOSE_RUN, closeRunId);
		return this.queryService.closeRun(closeRunId, operator);
	}

	@Transactional
	public Map<String, Object> submitReopenRequest(Long closeRunId, FinancialCloseModels.ReopenRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		CloseRunRow closeRun = lockCloseRun(closeRunId);
		requireVersion(closeRun.version(), request == null ? null : request.version());
		String reason = FinancialCloseSupport.requiredText(request == null ? null : request.reason(),
				ApiErrorCode.PERIOD_CLOSE_REASON_REQUIRED);
		if (!"CLOSED".equals(closeRun.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		Long existing = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_close_reopen_request
				where close_run_id = ?
				and status = 'SUBMITTED'
				""", Long.class, closeRun.id());
		if (existing != null && existing > 0) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		Long requestId = this.jdbcTemplate.queryForObject("select nextval('fin_close_reopen_request_id_seq')",
				Long.class);
		String requestNo = "FCR-" + closeRun.periodCode().replace("-", "") + "-" + "%04d".formatted(requestId);
		this.jdbcTemplate.update("""
				insert into fin_close_reopen_request (
					id, close_run_id, period_id, request_no, status, reason, requested_by_user_id,
					requested_by_username
				)
				values (?, ?, ?, ?, 'SUBMITTED', ?, ?, ?)
				""", requestId, closeRun.id(), closeRun.periodId(), requestNo, reason, operator.id(),
				operator.username());
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService
			.submitFinancialPeriodReopen(requestId,
					new PlatformApprovalService.ApprovalSubmitRequest(0L, reason,
							request == null ? null : request.idempotencyKey()),
					operator, servletRequest);
		this.jdbcTemplate.update("""
				update fin_close_reopen_request
				set approval_instance_id = ?, updated_at = now()
				where id = ?
				""", approval.id(), requestId);
		this.auditService.success(operator, "FIN_CLOSE_REOPEN_REQUEST", "FIN_CLOSE_REOPEN_REQUEST", requestId);
		return this.queryService.reopenRequest(requestId, operator);
	}

	@Transactional(readOnly = true)
	public ApprovalSnapshot approvalSnapshot(Long requestId) {
		return this.jdbcTemplate.query("""
				select id, request_no, reason,
				       case when status = 'SUBMITTED' then 'DRAFT' else status end as approval_status,
				       version
				from fin_close_reopen_request
				where id = ?
				""", (rs, rowNum) -> new ApprovalSnapshot(rs.getLong("id"), rs.getString("request_no"),
				rs.getString("reason"), rs.getString("approval_status"), rs.getLong("version")), requestId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	@Transactional
	public void applyReopenFromApproval(Long requestId, Long version, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ReopenRequestRow request = lockReopenRequest(requestId);
		requireVersion(request.version(), version);
		if (!"SUBMITTED".equals(request.status())) {
			throw new BusinessException(ApiErrorCode.FIN_REOPEN_APPROVAL_REQUIRED);
		}
		CloseRunRow closeRun = lockCloseRun(request.closeRunId());
		if (!"CLOSED".equals(closeRun.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		this.jdbcTemplate.update("""
				update gl_accounting_period
				set status = 'OPEN', updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				and status = 'CLOSED'
				""", operator.username(), OffsetDateTime.now(), closeRun.periodId());
		this.jdbcTemplate.update("""
				update fin_close_run
				set status = 'REOPENED', reopened_by = ?, reopened_at = ?, reopen_reason = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				and status = 'CLOSED'
				""", operator.username(), OffsetDateTime.now(), request.reason(), OffsetDateTime.now(), closeRun.id());
		this.jdbcTemplate.update("""
				update fin_close_reopen_request
				set status = 'APPLIED', applied_by = ?, applied_at = ?, updated_at = ?, version = version + 1
				where id = ?
				and status = 'SUBMITTED'
				""", operator.username(), OffsetDateTime.now(), OffsetDateTime.now(), request.id());
		this.auditService.success(operator, "FIN_CLOSE_REOPEN_APPLY", "FIN_CLOSE_REOPEN_REQUEST", request.id());
	}

	@Transactional
	public void reopenAfterApprovalTerminal(Long requestId, CurrentUser operator) {
		ReopenRequestRow request = lockReopenRequest(requestId);
		if (!"SUBMITTED".equals(request.status())) {
			return;
		}
		this.jdbcTemplate.update("""
				update fin_close_reopen_request
				set status = 'REJECTED', updated_at = ?, version = version + 1
				where id = ?
				and status = 'SUBMITTED'
				""", OffsetDateTime.now(), request.id());
	}

	private CheckRunRow lockCheckRun(Long id) {
		return this.jdbcTemplate.query("""
				select id, ledger_id, period_id, status, source_fingerprint, version
				from fin_close_check_run
				where id = ?
				for update
				""", (rs, rowNum) -> new CheckRunRow(rs.getLong("id"), rs.getLong("ledger_id"),
				rs.getLong("period_id"), rs.getString("status"), rs.getString("source_fingerprint"),
				rs.getLong("version")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_NOT_READY));
	}

	private FinancialCloseCheckService.PeriodRow lockPeriod(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.ledger_id, p.period_code, p.start_date, p.end_date, p.status, p.version
				from gl_accounting_period p
				join gl_ledger l on l.id = p.ledger_id
				where l.code = 'MAIN'
				and p.id = ?
				for update of p
				""", (rs, rowNum) -> new FinancialCloseCheckService.PeriodRow(rs.getLong("id"),
				rs.getLong("ledger_id"), rs.getString("period_code"), rs.getObject("start_date", LocalDate.class),
				rs.getObject("end_date", LocalDate.class), rs.getString("status"), rs.getLong("version")), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_PERIOD_NOT_FOUND));
	}

	private CloseRunRow lockCloseRun(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.period_id, p.period_code, r.status, r.close_version, r.version
				from fin_close_run r
				join gl_accounting_period p on p.id = r.period_id
				where r.id = ?
				for update of r
				""", (rs, rowNum) -> new CloseRunRow(rs.getLong("id"), rs.getLong("period_id"),
				rs.getString("period_code"), rs.getString("status"), rs.getLong("close_version"),
				rs.getLong("version")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private ReopenRequestRow lockReopenRequest(Long id) {
		return this.jdbcTemplate.query("""
				select id, close_run_id, period_id, status, reason, version
				from fin_close_reopen_request
				where id = ?
				for update
				""", (rs, rowNum) -> new ReopenRequestRow(rs.getLong("id"), rs.getLong("close_run_id"),
				rs.getLong("period_id"), rs.getString("status"), rs.getString("reason"), rs.getLong("version")),
				id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	private String json(Object value) {
		try {
			return this.objectMapper.writeValueAsString(value == null ? Map.of() : value);
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	public record ApprovalSnapshot(Long id, String no, String summary, String approvalStatus, Long version) {
	}

	private record CheckRunRow(Long id, Long ledgerId, Long periodId, String status, String sourceFingerprint,
			Long version) {
	}

	private record CloseRunRow(Long id, Long periodId, String periodCode, String status, Long closeVersion,
			Long version) {
	}

	private record ReopenRequestRow(Long id, Long closeRunId, Long periodId, String status, String reason,
			Long version) {
	}

}
