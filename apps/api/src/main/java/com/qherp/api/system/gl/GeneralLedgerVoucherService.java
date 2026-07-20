package com.qherp.api.system.gl;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.platform.PlatformApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeneralLedgerVoucherService {

	private static final String TARGET = "GL_VOUCHER";

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final PlatformApprovalService approvalService;

	private final GeneralLedgerQueryService queryService;

	private final GeneralLedgerAuditService glAuditService;

	public GeneralLedgerVoucherService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
			@Lazy PlatformApprovalService approvalService, GeneralLedgerQueryService queryService,
			GeneralLedgerAuditService glAuditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.approvalService = approvalService;
		this.queryService = queryService;
		this.glAuditService = glAuditService;
	}

	@Transactional
	public Map<String, Object> create(VoucherRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		String fingerprint = fingerprint(request);
		Long existing = actionIdempotentResult("CREATE", TARGET, null, request.idempotencyKey(), fingerprint, operator);
		if (existing != null) {
			return this.queryService.voucher(existing, operator);
		}
		PersistedVoucher draft = createVoucher("MANUAL", null, null, null, null, null, null, null, null, null,
				request.voucherType(), request.voucherDate(), request.summary(), request.lines(), request.idempotencyKey(),
				fingerprint, null, null, operator);
		recordAction("CREATE", TARGET, null, null, request.idempotencyKey(), fingerprint, TARGET, draft.id(),
				draft.version(), operator);
		this.glAuditService.success(operator, "GL_VOUCHER_CREATE", TARGET, draft.id());
		return this.queryService.voucher(draft.id(), operator);
	}

	@Transactional
	public Map<String, Object> createSystemDraft(String sourceType, Long sourceId, String sourceNo,
			String sourceFingerprint, Long sourceVersion, LocalDate voucherDate, String summary,
			List<VoucherLineRequest> lines, String idempotencyKey, CurrentUser operator) {
		if (!List.of("PROFIT_LOSS_CARRYFORWARD", "TAX_SUMMARY").contains(sourceType)) {
			throw new BusinessException(ApiErrorCode.GL_RULE_INVALID);
		}
		String fingerprint = GeneralLedgerSupport.sha256("SYSTEM_CREATE|" + sourceType + "|" + sourceId + "|"
				+ sourceFingerprint + "|" + sourceVersion + "|" + voucherDate + "|" + summary + "|" + lines);
		Long existing = actionIdempotentResult("SYSTEM_CREATE", sourceType, sourceId, idempotencyKey, fingerprint,
				operator);
		if (existing != null) {
			return this.queryService.voucher(existing, operator);
		}
		PersistedVoucher draft = createVoucher(sourceType, sourceId, sourceNo, sourceFingerprint, sourceVersion, null,
				null, null, null, null, "GENERAL", voucherDate, summary, lines, idempotencyKey, fingerprint, null, null,
				operator);
		recordAction("SYSTEM_CREATE", sourceType, sourceId, sourceVersion, idempotencyKey, fingerprint, TARGET,
				draft.id(), draft.version(), operator);
		this.glAuditService.success(operator, "GL_VOUCHER_SYSTEM_CREATE", TARGET, draft.id());
		return this.queryService.voucher(draft.id(), operator);
	}

	@Transactional
	public Map<String, Object> update(Long id, VoucherRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		VoucherRow voucher = lockVoucher(id);
		ensurePeriodWritable(voucher.periodId());
		if (!"DRAFT".equals(voucher.status())) {
			throw new BusinessException(ApiErrorCode.GL_POSTED_IMMUTABLE);
		}
		requireVersion(voucher.version(), request.version());
		if (!"MANUAL".equals(voucher.sourceType()) && !"REVERSAL".equals(voucher.sourceType())) {
			throw new BusinessException(ApiErrorCode.GL_POSTED_IMMUTABLE);
		}
		ValidatedDraft validated = validateDraft(request.voucherType(), request.voucherDate(), request.summary(),
				request.lines(), voucher.id(), false);
		this.jdbcTemplate.update("""
				update gl_voucher
				set accounting_period_id = ?, voucher_type = ?, voucher_date = ?, summary = ?, debit_total = ?,
				    credit_total = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ? and status = 'DRAFT'
				""", validated.period().id(), validated.voucherType(), validated.voucherDate(), validated.summary(),
				validated.debitTotal(), validated.creditTotal(), operator.username(), OffsetDateTime.now(), id,
				request.version());
		this.jdbcTemplate.update("delete from gl_voucher_line where voucher_id = ?", id);
		insertLines(id, validated.lines());
		this.glAuditService.success(operator, "GL_VOUCHER_UPDATE", TARGET, id);
		return this.queryService.voucher(id, operator);
	}

	@Transactional
	public Map<String, Object> convertFinanceDraft(Long draftId, ActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		FinanceDraftRow draft = lockFinanceDraft(draftId);
		String fingerprint = GeneralLedgerSupport.sha256("CONVERT|" + draftId + "|" + request.version() + "|"
				+ request.idempotencyKey());
		Long existing = actionIdempotentResult("CONVERT", "FIN_VOUCHER_DRAFT", draftId, request.idempotencyKey(),
				fingerprint, operator);
		if (existing != null) {
			return this.queryService.voucher(existing, operator);
		}
		requireVersion(draft.version(), request.version());
		if (!"READY".equals(draft.status())) {
			throw new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY);
		}
		SourceFacts facts = sourceFacts(draft.sourceType(), draft.sourceId());
		if (!draft.generationVersion().equals(facts.version())) {
			throw new BusinessException(ApiErrorCode.GL_SOURCE_CHANGED);
		}
		ensureNoActiveClaim(facts.sourceType(), facts.sourceId());
		ValidatedDraft validated = validateSourceDraft(facts, draft);
		Long claimId = reserveSourceClaim(facts, operator);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("originalSourceType", facts.sourceType());
		payload.put("originalSourceId", facts.sourceId());
		payload.put("financeDraftId", draft.id());
		PersistedVoucher voucher = insertVoucher("FIN_VOUCHER_DRAFT", draft.id(), draft.draftNo(),
				GeneralLedgerSupport.sha256("DRAFT|" + draft.id() + "|" + draft.version()), draft.version(),
				facts.sourceType(), facts.sourceId(), facts.sourceNo(), facts.version(), facts.fingerprint(),
				"GENERAL", facts.businessDate(), draft.summary(), validated.period().id(), validated.debitTotal(),
				validated.creditTotal(), claimId, validated.rule().id(), validated.rule().ruleVersion(),
				request.idempotencyKey(), fingerprint, payload, null, null, operator);
		insertLines(voucher.id(), validated.lines());
		this.jdbcTemplate.update("update gl_voucher_source_claim set voucher_id = ? where id = ?", voucher.id(),
				claimId);
		recordAction("CONVERT", "FIN_VOUCHER_DRAFT", draftId, request.version(), request.idempotencyKey(),
				fingerprint, TARGET, voucher.id(), voucher.version(), operator);
		this.glAuditService.success(operator, "GL_VOUCHER_CONVERT", TARGET, voucher.id());
		return this.queryService.voucher(voucher.id(), operator);
	}

	@Transactional
	public Map<String, Object> submit(Long id, ActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		String fingerprint = GeneralLedgerSupport.sha256("SUBMIT|" + id + "|" + request.version() + "|"
				+ request.reason());
		Long existing = actionIdempotentResult("SUBMIT", TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing != null) {
			return this.queryService.voucher(existing, operator);
		}
		VoucherRow voucher = lockVoucher(id);
		requireVersion(voucher.version(), request.version());
		ensurePeriodWritable(voucher.periodId());
		if (!"DRAFT".equals(voucher.status())) {
			throw new BusinessException(ApiErrorCode.GL_POSTED_IMMUTABLE);
		}
		revalidatePersistedVoucher(id, true);
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService.submitGlVoucherPost(id,
				new PlatformApprovalService.ApprovalSubmitRequest(voucher.version(),
						request.reason() == null || request.reason().isBlank() ? "提交正式凭证审批" : request.reason(),
						request.idempotencyKey()),
				operator, servletRequest);
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update gl_voucher
				set status = 'SUBMITTED', approval_instance_id = ?, submitted_by = ?, submitted_at = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ? and status = 'DRAFT'
				returning version
				""", Long.class, approval.id(), operator.username(), OffsetDateTime.now(), operator.username(),
				OffsetDateTime.now(), id, voucher.version());
		this.approvalService.updateBusinessObjectVersion(approval.id(), newVersion);
		recordAction("SUBMIT", TARGET, id, request.version(), request.idempotencyKey(), fingerprint, TARGET, id,
				newVersion, operator);
		this.glAuditService.success(operator, "GL_VOUCHER_SUBMIT", TARGET, id);
		return this.queryService.voucher(id, operator);
	}

	@Transactional
	public void postFromApproval(Long id, Long version, CurrentUser operator, HttpServletRequest servletRequest) {
		try {
			VoucherRow voucher = lockVoucher(id);
			requireVersion(voucher.version(), version);
			ensurePeriodWritable(voucher.periodId());
			if (!"SUBMITTED".equals(voucher.status())) {
				throw new BusinessException(ApiErrorCode.GL_POSTED_IMMUTABLE);
			}
			revalidatePersistedVoucher(id, true);
			if (voucher.sourceClaimId() != null) {
				lockSourceClaim(voucher.sourceClaimId());
			}
			int number = nextVoucherNumber(voucher.ledgerId(), voucher.periodId());
			String no = "记-" + voucher.periodCode().replace("-", "") + "-" + "%04d".formatted(number);
			insertLedgerEntries(voucher, no, number, operator);
			updateTotals(voucher.periodId());
			if (voucher.sourceClaimId() != null) {
				this.jdbcTemplate.update("""
						update gl_voucher_source_claim
						set status = 'POSTED', updated_by = ?, updated_at = ?
						where id = ?
						""", operator.username(), OffsetDateTime.now(), voucher.sourceClaimId());
			}
			this.jdbcTemplate.update("""
					update gl_voucher
					set status = 'POSTED', voucher_number = ?, voucher_no = ?, posted_by = ?, posted_at = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where id = ? and status = 'SUBMITTED'
					""", number, no, operator.username(), OffsetDateTime.now(), operator.username(),
					OffsetDateTime.now(), id);
			this.jdbcTemplate.update("""
					update gl_voucher_reversal_link
					set status = 'POSTED', updated_at = ?
					where reversal_voucher_id = ?
					""", OffsetDateTime.now(), id);
			syncFinancialCloseVoucherStatus(voucher, operator);
			this.glAuditService.success(operator, "GL_VOUCHER_POST", TARGET, id);
		}
		catch (BusinessException exception) {
			this.glAuditService.failure(operator, "GL_VOUCHER_POST", TARGET, id, exception.errorCode());
			throw exception;
		}
	}

	@Transactional
	public void reopenAfterApprovalTerminal(Long id, CurrentUser operator) {
		VoucherRow voucher = lockVoucher(id);
		if (!"SUBMITTED".equals(voucher.status())) {
			return;
		}
		ensurePeriodWritable(voucher.periodId());
		this.jdbcTemplate.update("""
				update gl_voucher
				set status = 'DRAFT', updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and status = 'SUBMITTED'
				""", operator.username(), OffsetDateTime.now(), id);
		this.glAuditService.success(operator, "GL_VOUCHER_APPROVAL_REOPEN", TARGET, id);
	}

	@Transactional
	public Map<String, Object> withdraw(Long id, ActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		String fingerprint = GeneralLedgerSupport.sha256("WITHDRAW|" + id + "|" + request.version() + "|"
				+ request.reason());
		Long existing = actionIdempotentResult("WITHDRAW", TARGET, id, request.idempotencyKey(), fingerprint,
				operator);
		if (existing != null) {
			return this.queryService.voucher(existing, operator);
		}
		VoucherRow voucher = lockVoucher(id);
		requireVersion(voucher.version(), request.version());
		ensurePeriodWritable(voucher.periodId());
		if (!"SUBMITTED".equals(voucher.status())) {
			throw new BusinessException(ApiErrorCode.GL_POSTED_IMMUTABLE);
		}
		Long approvalInstanceId = approvalInstanceId(id);
		if (approvalInstanceId == null) {
			throw new BusinessException(ApiErrorCode.APPROVAL_STATUS_INVALID);
		}
		Long approvalVersion = approvalInstanceVersion(approvalInstanceId);
		this.approvalService.withdraw(approvalInstanceId,
				new PlatformApprovalService.ApprovalActionRequest(approvalVersion,
						request.reason() == null || request.reason().isBlank() ? "撤回凭证审批" : request.reason(),
						request.idempotencyKey()),
				operator, servletRequest);
		Long resultVersion = this.jdbcTemplate.queryForObject("select version from gl_voucher where id = ?",
				Long.class, id);
		recordAction("WITHDRAW", TARGET, id, request.version(), request.idempotencyKey(), fingerprint, TARGET, id,
				resultVersion, operator);
		this.glAuditService.success(operator, "GL_VOUCHER_WITHDRAW", TARGET, id);
		return this.queryService.voucher(id, operator);
	}

	@Transactional
	public Map<String, Object> cancel(Long id, ActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		String fingerprint = GeneralLedgerSupport.sha256("CANCEL|" + id + "|" + request.version() + "|"
				+ request.reason());
		Long existing = actionIdempotentResult("CANCEL", TARGET, id, request.idempotencyKey(), fingerprint, operator);
		if (existing != null) {
			return this.queryService.voucher(existing, operator);
		}
		VoucherRow voucher = lockVoucher(id);
		requireVersion(voucher.version(), request.version());
		ensurePeriodWritable(voucher.periodId());
		if (!"DRAFT".equals(voucher.status())) {
			throw new BusinessException(ApiErrorCode.GL_POSTED_IMMUTABLE);
		}
		this.jdbcTemplate.update("""
				update gl_voucher
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ? and status = 'DRAFT'
				""", operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
		if (voucher.sourceClaimId() != null) {
			this.jdbcTemplate.update("""
					update gl_voucher_source_claim
					set status = 'RELEASED', updated_by = ?, updated_at = ?
					where id = ? and status = 'RESERVED'
					""", operator.username(), OffsetDateTime.now(), voucher.sourceClaimId());
		}
		this.jdbcTemplate.update("""
				update gl_voucher_reversal_link
				set status = 'CANCELLED', updated_at = ?
				where reversal_voucher_id = ? and status = 'DRAFT'
				""", OffsetDateTime.now(), id);
		Long resultVersion = this.jdbcTemplate.queryForObject("select version from gl_voucher where id = ?",
				Long.class, id);
		recordAction("CANCEL", TARGET, id, request.version(), request.idempotencyKey(), fingerprint, TARGET, id,
				resultVersion, operator);
		this.glAuditService.success(operator, "GL_VOUCHER_CANCEL", TARGET, id);
		return this.queryService.voucher(id, operator);
	}

	@Transactional
	public Map<String, Object> refreshSource(Long id, ActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		String fingerprint = GeneralLedgerSupport.sha256("REFRESH_SOURCE|" + id + "|" + request.version() + "|"
				+ request.reason());
		Long existing = actionIdempotentResult("REFRESH_SOURCE", TARGET, id, request.idempotencyKey(), fingerprint,
				operator);
		if (existing != null) {
			return this.queryService.voucher(existing, operator);
		}
		VoucherRow voucher = lockVoucher(id);
		requireVersion(voucher.version(), request.version());
		ensurePeriodWritable(voucher.periodId());
		if (!"DRAFT".equals(voucher.status()) || !"FIN_VOUCHER_DRAFT".equals(voucher.sourceType())) {
			throw new BusinessException(ApiErrorCode.GL_POSTED_IMMUTABLE);
		}
		SourceFacts facts = sourceFacts(voucher.sourceOriginalType(), voucher.sourceOriginalId());
		FinanceDraftRow draft = financeDraft(voucher.sourceId());
		ValidatedDraft validated = validateSourceDraft(facts, draft);
		this.jdbcTemplate.update("delete from gl_voucher_line where voucher_id = ?", id);
		insertLines(id, validated.lines());
		this.jdbcTemplate.update("""
				update gl_voucher
				set accounting_period_id = ?, voucher_date = ?, summary = ?, source_original_no = ?,
				    source_original_version = ?, source_original_fingerprint = ?, debit_total = ?, credit_total = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", validated.period().id(), facts.businessDate(), draft.summary(), facts.sourceNo(), facts.version(),
				facts.fingerprint(), validated.debitTotal(), validated.creditTotal(), operator.username(),
				OffsetDateTime.now(), id);
		Long resultVersion = this.jdbcTemplate.queryForObject("select version from gl_voucher where id = ?",
				Long.class, id);
		recordAction("REFRESH_SOURCE", TARGET, id, request.version(), request.idempotencyKey(), fingerprint, TARGET,
				id, resultVersion, operator);
		this.glAuditService.success(operator, "GL_VOUCHER_REFRESH_SOURCE", TARGET, id);
		return this.queryService.voucher(id, operator);
	}

	@Transactional
	public Map<String, Object> reverse(Long originalId, ReversalRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		String fingerprint = GeneralLedgerSupport.sha256("REVERSAL|" + originalId + "|" + request.version() + "|"
				+ request.voucherDate() + "|" + request.reason());
		Long existing = actionIdempotentResult("REVERSAL", TARGET, originalId, request.idempotencyKey(), fingerprint,
				operator);
		if (existing != null) {
			return this.queryService.voucher(existing, operator);
		}
		VoucherRow original = lockVoucher(originalId);
		requireVersion(original.version(), request.version());
		ensurePeriodWritable(original.periodId());
		if (!"POSTED".equals(original.status())) {
			throw new BusinessException(ApiErrorCode.GL_POSTED_IMMUTABLE);
		}
		Long active = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_voucher_reversal_link
				where original_voucher_id = ?
				and status in ('DRAFT', 'POSTED')
				""", Long.class, originalId);
		if (active != null && active > 0) {
			throw new BusinessException(ApiErrorCode.GL_REVERSAL_ALREADY_EXISTS);
		}
		LocalDate reversalDate = request.voucherDate() == null ? original.voucherDate() : request.voucherDate();
		List<LineDraft> reversedLines = persistedLines(originalId).stream()
			.map(LineDraft::reversed)
			.sorted(Comparator.comparingInt(LineDraft::lineNo))
			.toList();
		PeriodRow period = openPeriod(reversalDate);
		PersistedVoucher reversal = insertVoucher("REVERSAL", originalId, original.voucherNo(), null, original.version(),
				original.sourceOriginalType(), original.sourceOriginalId(), original.sourceOriginalNo(),
				original.sourceOriginalVersion(), original.sourceOriginalFingerprint(), "GENERAL", reversalDate,
				"冲销：" + request.reason(), period.id(), original.debitTotal(), original.creditTotal(), null, null, null,
				request.idempotencyKey(), fingerprint, Map.of("originalVoucherId", originalId), originalId,
				request.reason(), operator);
		insertLines(reversal.id(), reversedLines);
		this.jdbcTemplate.update("""
				insert into gl_voucher_reversal_link (
					original_voucher_id, reversal_voucher_id, status, reason, created_by, created_at, updated_at
				)
				values (?, ?, 'DRAFT', ?, ?, ?, ?)
				""", originalId, reversal.id(), request.reason(), operator.username(), OffsetDateTime.now(),
				OffsetDateTime.now());
		recordAction("REVERSAL", TARGET, originalId, request.version(), request.idempotencyKey(), fingerprint, TARGET,
				reversal.id(), reversal.version(), operator);
		this.glAuditService.success(operator, "GL_VOUCHER_REVERSE", TARGET, reversal.id());
		return this.queryService.voucher(reversal.id(), operator);
	}

	@Transactional(readOnly = true)
	public ApprovalSnapshot approvalSnapshot(Long id) {
		return this.jdbcTemplate.query("""
				select id, draft_no, voucher_no, summary,
				       case when status in ('DRAFT', 'SUBMITTED') then 'DRAFT' else status end as approval_status,
				       version
				from gl_voucher
				where id = ?
				""", (rs, rowNum) -> new ApprovalSnapshot(rs.getLong("id"),
				rs.getString("voucher_no") == null ? rs.getString("draft_no") : rs.getString("voucher_no"),
				rs.getString("summary"), rs.getString("approval_status"), rs.getLong("version")), id).stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_VOUCHER_NOT_FOUND));
	}

	private PersistedVoucher createVoucher(String sourceType, Long sourceId, String sourceNo, String sourceFingerprint,
			Long sourceVersion, String sourceOriginalType, Long sourceOriginalId, String sourceOriginalNo,
			Long sourceOriginalVersion, String sourceOriginalFingerprint, String voucherType, LocalDate voucherDate,
			String summary, List<VoucherLineRequest> lineRequests, String idempotencyKey, String requestFingerprint,
			Long reversalOriginalVoucherId, String reversalReason, CurrentUser operator) {
		ValidatedDraft validated = validateDraft(voucherType, voucherDate, summary, lineRequests, null, true);
		PersistedVoucher voucher = insertVoucher(sourceType, sourceId, sourceNo, sourceFingerprint, sourceVersion,
				sourceOriginalType, sourceOriginalId, sourceOriginalNo, sourceOriginalVersion, sourceOriginalFingerprint,
				validated.voucherType(), validated.voucherDate(), validated.summary(), validated.period().id(),
				validated.debitTotal(), validated.creditTotal(), null, null, null, idempotencyKey, requestFingerprint,
				Map.of(), reversalOriginalVoucherId, reversalReason, operator);
		insertLines(voucher.id(), validated.lines());
		return voucher;
	}

	private PersistedVoucher insertVoucher(String sourceType, Long sourceId, String sourceNo, String sourceFingerprint,
			Long sourceVersion, String sourceOriginalType, Long sourceOriginalId, String sourceOriginalNo,
			Long sourceOriginalVersion, String sourceOriginalFingerprint, String voucherType, LocalDate voucherDate,
			String summary, Long periodId, BigDecimal debitTotal, BigDecimal creditTotal, Long claimId, Long ruleId,
			Integer ruleVersion, String idempotencyKey, String requestFingerprint, Map<String, Object> payload,
			Long reversalOriginalVoucherId, String reversalReason, CurrentUser operator) {
		Long id = this.jdbcTemplate.queryForObject("select nextval('gl_voucher_id_seq')", Long.class);
		this.jdbcTemplate.update("""
				insert into gl_voucher (
					id, ledger_id, accounting_period_id, draft_no, voucher_type, voucher_date, status, summary,
					source_type, source_id, source_no, source_fingerprint, source_version, source_original_type,
					source_original_id, source_original_no, source_original_version, source_original_fingerprint,
					source_payload, source_claim_id, rule_id, rule_version, currency, debit_total, credit_total,
					reversal_original_voucher_id, reversal_reason, idempotency_key, request_fingerprint, created_by,
					created_at, updated_by, updated_at
				)
				select ?, l.id, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, 'CNY',
				       ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
				from gl_ledger l
				where l.code = 'MAIN'
				""", id, periodId, "GLD-" + id, voucherType, voucherDate, summary, sourceType, sourceId, sourceNo,
				sourceFingerprint, sourceVersion, sourceOriginalType, sourceOriginalId, sourceOriginalNo,
				sourceOriginalVersion, sourceOriginalFingerprint, json(payload), claimId, ruleId, ruleVersion,
				debitTotal, creditTotal, reversalOriginalVoucherId, reversalReason, idempotencyKey, requestFingerprint,
				operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now());
		return new PersistedVoucher(id, 0L);
	}

	private ValidatedDraft validateDraft(String voucherType, LocalDate voucherDate, String summary,
			List<VoucherLineRequest> lineRequests, Long voucherId, boolean enforceOpeningLimit) {
		String type = voucherType == null || voucherType.isBlank() ? "GENERAL" : voucherType.trim().toUpperCase();
		if (!List.of("GENERAL", "OPENING").contains(type)) {
			throw new BusinessException(ApiErrorCode.GL_RULE_INVALID);
		}
		if (voucherDate == null) {
			throw new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN);
		}
		PeriodRow period = openPeriod(voucherDate);
		if ("OPENING".equals(type)) {
			LedgerInfo ledger = ledgerInfo();
			if (ledger.startPeriodId() == null || !ledger.startPeriodId().equals(period.id())
					|| !voucherDate.equals(period.startDate())) {
				throw new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN);
			}
			if (enforceOpeningLimit) {
				ensureNoPostedGeneralBeforeOpening();
				Long existing = existingOpeningVoucherCount(voucherId);
				if (existing != null && existing > 0) {
					throw new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN);
				}
			}
		}
		if (lineRequests == null || lineRequests.size() < 2) {
			throw new BusinessException(ApiErrorCode.GL_VOUCHER_UNBALANCED);
		}
		List<LineDraft> lines = new ArrayList<>();
		BigDecimal debit = BigDecimal.ZERO;
		BigDecimal credit = BigDecimal.ZERO;
		for (VoucherLineRequest line : lineRequests) {
			LineDraft draft = validateManualLine(line);
			lines.add(draft);
			debit = debit.add(draft.debitAmount());
			credit = credit.add(draft.creditAmount());
		}
		debit = GeneralLedgerSupport.amount(debit);
		credit = GeneralLedgerSupport.amount(credit);
		if (debit.compareTo(BigDecimal.ZERO) <= 0 || debit.compareTo(credit) != 0) {
			throw new BusinessException(ApiErrorCode.GL_VOUCHER_UNBALANCED);
		}
		return new ValidatedDraft(type, voucherDate, GeneralLedgerSupport.requiredText(summary,
				ApiErrorCode.GL_RULE_INVALID), period, null, debit, credit, lines);
	}

	private Long existingOpeningVoucherCount(Long voucherId) {
		if (voucherId == null) {
			return this.jdbcTemplate.queryForObject("""
					select count(*)
					from gl_voucher
					where voucher_type = 'OPENING'
					and status <> 'CANCELLED'
					""", Long.class);
		}
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_voucher
				where voucher_type = 'OPENING'
				and status <> 'CANCELLED'
				and id <> ?
				""", Long.class, voucherId);
	}

	private void ensureNoPostedGeneralBeforeOpening() {
		Long postedGeneral = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_voucher
				where voucher_type = 'GENERAL'
				and status = 'POSTED'
				""", Long.class);
		if (postedGeneral != null && postedGeneral > 0) {
			throw new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN);
		}
	}

	private LineDraft validateManualLine(VoucherLineRequest line) {
		if (line == null || line.accountId() == null) {
			throw new BusinessException(ApiErrorCode.GL_ACCOUNT_NOT_FOUND);
		}
		AccountSnapshot account = account(line.accountId());
		if (!account.enabled()) {
			throw new BusinessException(ApiErrorCode.GL_ACCOUNT_DISABLED);
		}
		if (!account.postable()) {
			throw new BusinessException(ApiErrorCode.GL_ACCOUNT_NOT_LEAF);
		}
		BigDecimal debit = GeneralLedgerSupport.amount(line.debitAmount());
		BigDecimal credit = GeneralLedgerSupport.amount(line.creditAmount());
		if (GeneralLedgerSupport.positive(debit) == GeneralLedgerSupport.positive(credit)) {
			throw new BusinessException(ApiErrorCode.GL_VOUCHER_UNBALANCED);
		}
		List<AuxiliarySnapshot> auxiliaries = validateAuxiliaries(account.id(), line.auxiliaryItems());
		return new LineDraft(line.lineNo() == null ? 0 : line.lineNo(), line.summary(), account, debit, credit,
				null, null, null, null, auxiliaries);
	}

	private ValidatedDraft validateSourceDraft(SourceFacts facts, FinanceDraftRow draft) {
		PostingRule rule = activeRule(facts.sourceType(), facts.sourceVariant());
		PeriodRow period = openPeriod(facts.businessDate());
		List<LineDraft> lines = new ArrayList<>();
		BigDecimal debit = BigDecimal.ZERO;
		BigDecimal credit = BigDecimal.ZERO;
		int lineNo = 1;
		for (SourceFactLine factLine : facts.lines()) {
			if (factLine.amount().compareTo(BigDecimal.ZERO) <= 0) {
				continue;
			}
			RuleLine ruleLine = ruleLine(rule.id(), factLine.factCode(), factLine.direction());
			AccountSnapshot account = account(ruleLine.accountId());
			List<AuxiliarySnapshot> auxiliaries = automaticAuxiliaries(account.id(), factLine);
			BigDecimal debitAmount = "DEBIT".equals(factLine.direction()) ? factLine.amount() : BigDecimal.ZERO;
			BigDecimal creditAmount = "CREDIT".equals(factLine.direction()) ? factLine.amount() : BigDecimal.ZERO;
			lines.add(new LineDraft(lineNo++, ruleLine.summaryTemplate(), account, debitAmount, creditAmount,
					factLine.factCode(), facts.sourceType(), facts.sourceId(), facts.sourceNo(), auxiliaries));
			debit = debit.add(debitAmount);
			credit = credit.add(creditAmount);
		}
		debit = GeneralLedgerSupport.amount(debit);
		credit = GeneralLedgerSupport.amount(credit);
		if (lines.size() < 2 || debit.compareTo(credit) != 0) {
			throw new BusinessException(ApiErrorCode.GL_RULE_INVALID);
		}
		return new ValidatedDraft("GENERAL", facts.businessDate(), draft.summary(), period, rule, debit, credit, lines);
	}

	private void revalidatePersistedVoucher(Long voucherId, boolean validateSource) {
		VoucherRow voucher = lockVoucher(voucherId);
		if ("OPENING".equals(voucher.voucherType())) {
			ensureNoPostedGeneralBeforeOpening();
			Long existing = existingOpeningVoucherCount(voucherId);
			if (existing != null && existing > 0) {
				throw new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN);
			}
		}
		for (LineDraft line : persistedLines(voucherId)) {
			AccountSnapshot account = account(line.account().id());
			if (!account.enabled()) {
				throw new BusinessException(ApiErrorCode.GL_ACCOUNT_DISABLED);
			}
			if (!account.postable()) {
				throw new BusinessException(ApiErrorCode.GL_ACCOUNT_NOT_LEAF);
			}
			validateAuxiliarySnapshots(account.id(), line.auxiliaries());
		}
		if (validateSource && "FIN_VOUCHER_DRAFT".equals(voucher.sourceType())) {
			SourceFacts facts = sourceFacts(voucher.sourceOriginalType(), voucher.sourceOriginalId());
			if (!facts.version().equals(voucher.sourceOriginalVersion())) {
				throw new BusinessException(ApiErrorCode.GL_SOURCE_CHANGED);
			}
			if (!facts.fingerprint().equals(voucher.sourceOriginalFingerprint())) {
				throw new BusinessException(ApiErrorCode.GL_SOURCE_CHANGED);
			}
		}
	}

	private AccountSnapshot account(Long id) {
		return this.jdbcTemplate.query("""
				select id, code, name, category, balance_direction, postable, enabled
				from gl_account
				where id = ?
				""", (rs, rowNum) -> new AccountSnapshot(rs.getLong("id"), rs.getString("code"),
				rs.getString("name"), rs.getString("category"), rs.getString("balance_direction"),
				rs.getBoolean("postable"), rs.getBoolean("enabled")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_ACCOUNT_NOT_FOUND));
	}

	private List<AuxiliarySnapshot> validateAuxiliaries(Long accountId, List<AuxiliaryItemRequest> requested) {
		Map<String, String> requirements = accountRequirements(accountId);
		List<AuxiliaryItemRequest> items = requested == null ? List.of() : requested;
		Map<String, AuxiliaryItemRequest> requestedByDimension = new LinkedHashMap<>();
		for (AuxiliaryItemRequest item : items) {
			requestedByDimension.put(item.dimensionCode(), item);
			if (!requirements.containsKey(item.dimensionCode())) {
				throw new BusinessException(ApiErrorCode.GL_AUXILIARY_NOT_ALLOWED);
			}
		}
		for (Map.Entry<String, String> entry : requirements.entrySet()) {
			if ("REQUIRED".equals(entry.getValue()) && !requestedByDimension.containsKey(entry.getKey())) {
				throw new BusinessException(ApiErrorCode.GL_AUXILIARY_REQUIRED);
			}
		}
		List<AuxiliarySnapshot> result = new ArrayList<>();
		for (AuxiliaryItemRequest item : items) {
			result.add(auxiliarySnapshot(item.dimensionCode(), item.sourceId(), item.auxItemId()));
		}
		return result;
	}

	private void validateAuxiliarySnapshots(Long accountId, List<AuxiliarySnapshot> snapshots) {
		List<AuxiliaryItemRequest> requests = snapshots.stream()
			.map((snapshot) -> new AuxiliaryItemRequest(snapshot.dimensionCode(), snapshot.objectId(),
					snapshot.auxItemId()))
			.toList();
		validateAuxiliaries(accountId, requests);
	}

	private List<AuxiliarySnapshot> automaticAuxiliaries(Long accountId, SourceFactLine factLine) {
		Map<String, String> requirements = accountRequirements(accountId);
		List<AuxiliarySnapshot> snapshots = new ArrayList<>();
		for (String dimensionCode : requirements.keySet()) {
			Long sourceId = switch (dimensionCode) {
				case "CUSTOMER" -> factLine.customerId();
				case "SUPPLIER" -> factLine.supplierId();
				case "PROJECT" -> factLine.projectId();
				default -> null;
			};
			if (sourceId != null) {
				snapshots.add(auxiliarySnapshot(dimensionCode, sourceId, null));
			}
			else if ("REQUIRED".equals(requirements.get(dimensionCode))) {
				throw new BusinessException(ApiErrorCode.GL_AUXILIARY_REQUIRED);
			}
		}
		return snapshots;
	}

	private Map<String, String> accountRequirements(Long accountId) {
		Map<String, String> result = new LinkedHashMap<>();
		this.jdbcTemplate.query("""
				select d.code, r.requirement
				from gl_account_aux_requirement r
				join gl_aux_dimension d on d.id = r.dimension_id
				where r.account_id = ?
				order by d.sort_order
				""", (rs) -> {
			result.put(rs.getString("code"), rs.getString("requirement"));
		}, accountId);
		return result;
	}

	private AuxiliarySnapshot auxiliarySnapshot(String dimensionCode, Long sourceId, Long auxItemId) {
		DimensionRow dimension = dimension(dimensionCode);
		if ("CUSTOMER".equals(dimensionCode)) {
			return this.jdbcTemplate.query("""
					select id, code, name
					from mst_customer
					where id = ?
					""", (rs, rowNum) -> new AuxiliarySnapshot(dimension.id(), dimension.code(), dimension.name(),
					"CUSTOMER", rs.getLong("id"), rs.getString("code"), rs.getString("name"), null), sourceId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_AUXILIARY_REQUIRED));
		}
		if ("SUPPLIER".equals(dimensionCode)) {
			return this.jdbcTemplate.query("""
					select id, code, name
					from mst_supplier
					where id = ?
					""", (rs, rowNum) -> new AuxiliarySnapshot(dimension.id(), dimension.code(), dimension.name(),
					"SUPPLIER", rs.getLong("id"), rs.getString("code"), rs.getString("name"), null), sourceId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_AUXILIARY_REQUIRED));
		}
		if ("PROJECT".equals(dimensionCode)) {
			return this.jdbcTemplate.query("""
					select id, project_no, name
					from sal_project
					where id = ?
					""", (rs, rowNum) -> new AuxiliarySnapshot(dimension.id(), dimension.code(), dimension.name(),
					"PROJECT", rs.getLong("id"), rs.getString("project_no"), rs.getString("name"), null), sourceId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_AUXILIARY_REQUIRED));
		}
		return this.jdbcTemplate.query("""
				select id, code, name
				from gl_aux_item
				where id = ?
				and dimension_id = ?
				""", (rs, rowNum) -> new AuxiliarySnapshot(dimension.id(), dimension.code(), dimension.name(),
				"CUSTOM", null, rs.getString("code"), rs.getString("name"), rs.getLong("id")), auxItemId,
				dimension.id()).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_AUXILIARY_REQUIRED));
	}

	private DimensionRow dimension(String code) {
		return this.jdbcTemplate.query("""
				select id, code, name
				from gl_aux_dimension
				where code = ?
				""", (rs, rowNum) -> new DimensionRow(rs.getLong("id"), rs.getString("code"), rs.getString("name")),
				code).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.GL_AUXILIARY_NOT_ALLOWED));
	}

	private void insertLines(Long voucherId, List<LineDraft> lines) {
		for (LineDraft line : lines) {
			Long lineId = this.jdbcTemplate.queryForObject("""
					insert into gl_voucher_line (
						voucher_id, line_no, summary, account_id, account_code, account_name, account_category,
						account_balance_direction, debit_amount, credit_amount, normalized_fact_code, source_type,
						source_id, source_no, source_route, created_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
					returning id
					""", Long.class, voucherId, line.lineNo(), line.summary(), line.account().id(),
					line.account().code(), line.account().name(), line.account().category(),
					line.account().balanceDirection(), line.debitAmount(), line.creditAmount(), line.normalizedFactCode(),
					line.sourceType(), line.sourceId(), line.sourceNo(), line.sourceType() == null ? null
							: json(Map.of("sourceType", line.sourceType(), "sourceId", line.sourceId())),
					OffsetDateTime.now());
			for (AuxiliarySnapshot auxiliary : line.auxiliaries()) {
				this.jdbcTemplate.update("""
						insert into gl_voucher_line_auxiliary (
							voucher_line_id, dimension_id, dimension_code, dimension_name, object_type, object_id,
							object_code, object_name, aux_item_id, created_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""", lineId, auxiliary.dimensionId(), auxiliary.dimensionCode(), auxiliary.dimensionName(),
						auxiliary.objectType(), auxiliary.objectId(), auxiliary.objectCode(), auxiliary.objectName(),
						auxiliary.auxItemId(), OffsetDateTime.now());
			}
		}
	}

	private List<LineDraft> persistedLines(Long voucherId) {
		return this.jdbcTemplate.query("""
				select id, line_no, summary, account_id, account_code, account_name, account_category,
				       account_balance_direction, debit_amount, credit_amount, normalized_fact_code, source_type,
				       source_id, source_no
				from gl_voucher_line
				where voucher_id = ?
				order by line_no
				""", (rs, rowNum) -> {
			Long lineId = rs.getLong("id");
			AccountSnapshot account = new AccountSnapshot(rs.getLong("account_id"), rs.getString("account_code"),
					rs.getString("account_name"), rs.getString("account_category"),
					rs.getString("account_balance_direction"), true, true);
			return new LineDraft(rs.getInt("line_no"), rs.getString("summary"), account,
					rs.getBigDecimal("debit_amount"), rs.getBigDecimal("credit_amount"),
					rs.getString("normalized_fact_code"), rs.getString("source_type"),
					GeneralLedgerSupport.nullableLong(rs, "source_id"), rs.getString("source_no"),
					persistedAuxiliaries(lineId));
		}, voucherId);
	}

	private List<AuxiliarySnapshot> persistedAuxiliaries(Long lineId) {
		return this.jdbcTemplate.query("""
				select dimension_id, dimension_code, dimension_name, object_type, object_id, object_code, object_name,
				       aux_item_id
				from gl_voucher_line_auxiliary
				where voucher_line_id = ?
				""", (rs, rowNum) -> new AuxiliarySnapshot(rs.getLong("dimension_id"), rs.getString("dimension_code"),
				rs.getString("dimension_name"), rs.getString("object_type"),
				GeneralLedgerSupport.nullableLong(rs, "object_id"), rs.getString("object_code"),
				rs.getString("object_name"), GeneralLedgerSupport.nullableLong(rs, "aux_item_id")), lineId);
	}

	private void insertLedgerEntries(VoucherRow voucher, String voucherNo, int voucherNumber, CurrentUser operator) {
		for (LineDraft line : persistedLines(voucher.id())) {
			Long lineId = this.jdbcTemplate.queryForObject("""
					select id
					from gl_voucher_line
					where voucher_id = ?
					and line_no = ?
					""", Long.class, voucher.id(), line.lineNo());
			this.jdbcTemplate.update("""
					insert into gl_ledger_entry (
						ledger_id, period_id, voucher_id, voucher_line_id, voucher_date, voucher_no, voucher_word,
						voucher_number, line_no, summary, account_id, account_code, account_name, balance_direction,
						voucher_type, debit_amount, credit_amount, auxiliary_snapshot, source_type, source_id,
						source_no, source_route, posted_by, posted_at, created_at
					)
					values (?, ?, ?, ?, ?, ?, '记', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?, ?, ?)
					""", voucher.ledgerId(), voucher.periodId(), voucher.id(), lineId, voucher.voucherDate(),
					voucherNo, voucherNumber, line.lineNo(), line.summary(), line.account().id(), line.account().code(),
					line.account().name(), line.account().balanceDirection(), voucher.voucherType(), line.debitAmount(),
					line.creditAmount(), json(line.auxiliaries()), line.sourceType(), line.sourceId(), line.sourceNo(),
					line.sourceType() == null ? null : json(Map.of("sourceType", line.sourceType(), "sourceId",
							line.sourceId())),
					operator.username(), OffsetDateTime.now(), OffsetDateTime.now());
		}
	}

	private void updateTotals(Long periodId) {
		this.jdbcTemplate.update("delete from gl_account_period_total where period_id = ?", periodId);
		this.jdbcTemplate.update("""
				insert into gl_account_period_total (
					ledger_id, period_id, account_id, opening_debit, opening_credit, period_debit, period_credit,
					ending_debit, ending_credit, updated_at
				)
				select e.ledger_id, e.period_id, e.account_id,
				       coalesce(sum(case when e.voucher_type = 'OPENING' then e.debit_amount else 0 end), 0),
				       coalesce(sum(case when e.voucher_type = 'OPENING' then e.credit_amount else 0 end), 0),
				       coalesce(sum(case when e.voucher_type <> 'OPENING' then e.debit_amount else 0 end), 0),
				       coalesce(sum(case when e.voucher_type <> 'OPENING' then e.credit_amount else 0 end), 0),
				       coalesce(sum(e.debit_amount), 0),
				       coalesce(sum(e.credit_amount), 0),
				       now()
				from gl_ledger_entry e
				where e.period_id = ?
				group by e.ledger_id, e.period_id, e.account_id
				""", periodId);
	}

	private int nextVoucherNumber(Long ledgerId, Long periodId) {
		this.jdbcTemplate.update("""
				insert into gl_voucher_number_sequence (ledger_id, period_id, voucher_word, last_number, updated_at)
				values (?, ?, '记', 0, now())
				on conflict (ledger_id, period_id, voucher_word) do nothing
				""", ledgerId, periodId);
		Integer current = this.jdbcTemplate.queryForObject("""
				select last_number
				from gl_voucher_number_sequence
				where ledger_id = ?
				and period_id = ?
				and voucher_word = '记'
				for update
				""", Integer.class, ledgerId, periodId);
		int next = (current == null ? 0 : current) + 1;
		this.jdbcTemplate.update("""
				update gl_voucher_number_sequence
				set last_number = ?, updated_at = now()
				where ledger_id = ?
				and period_id = ?
				and voucher_word = '记'
				""", next, ledgerId, periodId);
		return next;
	}

	private SourceFacts sourceFacts(String sourceType, Long sourceId) {
		return switch (sourceType) {
			case "SALES_INVOICE" -> salesInvoiceFacts(sourceId);
			case "PURCHASE_INVOICE" -> purchaseInvoiceFacts(sourceId);
			case "EXPENSE" -> expenseFacts(sourceId);
			case "RECEIPT" -> receiptFacts(sourceId);
			case "PAYMENT" -> paymentFacts(sourceId);
			case "SETTLEMENT_ALLOCATION" -> settlementFacts(sourceId);
			default -> throw new BusinessException(ApiErrorCode.GL_RULE_MISSING);
		};
	}

	private SourceFacts salesInvoiceFacts(Long id) {
		return this.jdbcTemplate.query("""
				select id, invoice_no, customer_id, project_id, invoice_date, tax_excluded_amount, tax_amount,
				       tax_included_amount, status, version
				from fin_sales_invoice
				where id = ?
				for update
				""", (rs, rowNum) -> {
			if (!"CONFIRMED".equals(rs.getString("status"))) {
				throw new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY);
			}
			Long customerId = rs.getLong("customer_id");
			Long projectId = GeneralLedgerSupport.nullableLong(rs, "project_id");
			String no = rs.getString("invoice_no");
			Long version = rs.getLong("version");
			List<SourceFactLine> lines = List.of(
					new SourceFactLine("SALES_RECEIVABLE", "DEBIT", rs.getBigDecimal("tax_included_amount"),
							customerId, null, projectId),
					new SourceFactLine("SALES_REVENUE", "CREDIT", rs.getBigDecimal("tax_excluded_amount"), null,
							null, projectId),
					new SourceFactLine("OUTPUT_VAT", "CREDIT", rs.getBigDecimal("tax_amount"), null, null, null));
			return new SourceFacts("SALES_INVOICE", "DEFAULT", id, no, rs.getObject("invoice_date", LocalDate.class),
					version, GeneralLedgerSupport.sha256("SALES_INVOICE|" + id + "|" + no + "|" + version + "|"
							+ rs.getBigDecimal("tax_excluded_amount") + "|" + rs.getBigDecimal("tax_amount") + "|"
							+ rs.getBigDecimal("tax_included_amount")),
					lines);
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY));
	}

	private SourceFacts purchaseInvoiceFacts(Long id) {
		return this.jdbcTemplate.query("""
				select id, invoice_no, supplier_id, project_id, invoice_date, tax_excluded_amount, tax_amount,
				       tax_included_amount, status, version
				from fin_purchase_invoice
				where id = ?
				for update
				""", (rs, rowNum) -> {
			if (!"CONFIRMED".equals(rs.getString("status"))) {
				throw new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY);
			}
			Long supplierId = rs.getLong("supplier_id");
			Long projectId = GeneralLedgerSupport.nullableLong(rs, "project_id");
			String no = rs.getString("invoice_no");
			Long version = rs.getLong("version");
			List<SourceFactLine> lines = List.of(
					new SourceFactLine("PURCHASE_CLEARING", "DEBIT", rs.getBigDecimal("tax_excluded_amount"),
							null, null, projectId),
					new SourceFactLine("INPUT_VAT", "DEBIT", rs.getBigDecimal("tax_amount"), null, null, null),
					new SourceFactLine("PURCHASE_PAYABLE", "CREDIT", rs.getBigDecimal("tax_included_amount"), null,
							supplierId, projectId));
			return new SourceFacts("PURCHASE_INVOICE", "DEFAULT", id, no,
					rs.getObject("invoice_date", LocalDate.class), version,
					GeneralLedgerSupport.sha256("PURCHASE_INVOICE|" + id + "|" + no + "|" + version + "|"
							+ rs.getBigDecimal("tax_included_amount")),
					lines);
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY));
	}

	private SourceFacts expenseFacts(Long id) {
		return this.jdbcTemplate.query("""
				select id, expense_no, supplier_id, project_id, expense_date, tax_excluded_amount, tax_amount,
				       tax_included_amount, status, version
				from fin_expense
				where id = ?
				for update
				""", (rs, rowNum) -> {
			if (!"CONFIRMED".equals(rs.getString("status"))) {
				throw new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY);
			}
			Long supplierId = rs.getLong("supplier_id");
			Long projectId = GeneralLedgerSupport.nullableLong(rs, "project_id");
			String no = rs.getString("expense_no");
			Long version = rs.getLong("version");
			List<SourceFactLine> lines = List.of(
					new SourceFactLine("EXPENSE", "DEBIT", rs.getBigDecimal("tax_excluded_amount"), null, null,
							projectId),
					new SourceFactLine("INPUT_VAT", "DEBIT", rs.getBigDecimal("tax_amount"), null, null, null),
					new SourceFactLine("EXPENSE_PAYABLE", "CREDIT", rs.getBigDecimal("tax_included_amount"), null,
							supplierId, projectId));
			return new SourceFacts("EXPENSE", "DEFAULT", id, no, rs.getObject("expense_date", LocalDate.class),
					version, GeneralLedgerSupport.sha256("EXPENSE|" + id + "|" + no + "|" + version + "|"
							+ rs.getBigDecimal("tax_included_amount")),
					lines);
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY));
	}

	private SourceFacts receiptFacts(Long id) {
		return this.jdbcTemplate.query("""
				select id, receipt_no, customer_id, receipt_date, amount, status, version
				from fin_receipt
				where id = ?
				for update
				""", (rs, rowNum) -> {
			if (!"POSTED".equals(rs.getString("status"))) {
				throw new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY);
			}
			Long customerId = rs.getLong("customer_id");
			String no = rs.getString("receipt_no");
			Long version = rs.getLong("version");
			List<SourceFactLine> lines = List.of(
					new SourceFactLine("BANK_RECEIPT", "DEBIT", rs.getBigDecimal("amount"), null, null, null),
					new SourceFactLine("ADVANCE_RECEIPT", "CREDIT", rs.getBigDecimal("amount"), customerId, null,
							null));
			return new SourceFacts("RECEIPT", "DEFAULT", id, no, rs.getObject("receipt_date", LocalDate.class),
					version, GeneralLedgerSupport.sha256("RECEIPT|" + id + "|" + no + "|" + version + "|"
							+ rs.getBigDecimal("amount")),
					lines);
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY));
	}

	private SourceFacts paymentFacts(Long id) {
		return this.jdbcTemplate.query("""
				select id, payment_no, supplier_id, payment_date, amount, status, version
				from fin_payment
				where id = ?
				for update
				""", (rs, rowNum) -> {
			if (!"POSTED".equals(rs.getString("status"))) {
				throw new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY);
			}
			Long supplierId = rs.getLong("supplier_id");
			String no = rs.getString("payment_no");
			Long version = rs.getLong("version");
			List<SourceFactLine> lines = List.of(
					new SourceFactLine("PREPAYMENT", "DEBIT", rs.getBigDecimal("amount"), null, supplierId, null),
					new SourceFactLine("BANK_PAYMENT", "CREDIT", rs.getBigDecimal("amount"), null, null, null));
			return new SourceFacts("PAYMENT", "DEFAULT", id, no, rs.getObject("payment_date", LocalDate.class),
					version, GeneralLedgerSupport.sha256("PAYMENT|" + id + "|" + no + "|" + version + "|"
							+ rs.getBigDecimal("amount")),
					lines);
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY));
	}

	private SourceFacts settlementFacts(Long id) {
		return this.jdbcTemplate.query("""
				select id, allocation_no, settlement_side, party_id, project_id, business_date, total_amount, status,
				       version
				from fin_settlement_allocation
				where id = ?
				for update
				""", (rs, rowNum) -> {
			if (!"POSTED".equals(rs.getString("status"))) {
				throw new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY);
			}
			String side = rs.getString("settlement_side");
			Long partyId = rs.getLong("party_id");
			Long projectId = GeneralLedgerSupport.nullableLong(rs, "project_id");
			String no = rs.getString("allocation_no");
			Long version = rs.getLong("version");
			BigDecimal amount = rs.getBigDecimal("total_amount");
			List<SourceFactLine> lines = "RECEIVABLE".equals(side)
					? List.of(new SourceFactLine("ADVANCE_RECEIPT_CLEAR", "DEBIT", amount, partyId, null, projectId),
							new SourceFactLine("RECEIVABLE_CLEAR", "CREDIT", amount, partyId, null, projectId))
					: List.of(new SourceFactLine("PAYABLE_CLEAR", "DEBIT", amount, null, partyId, projectId),
							new SourceFactLine("PREPAYMENT_CLEAR", "CREDIT", amount, null, partyId, projectId));
			return new SourceFacts("SETTLEMENT_ALLOCATION", side, id, no,
					rs.getObject("business_date", LocalDate.class), version,
					GeneralLedgerSupport.sha256("SETTLEMENT_ALLOCATION|" + id + "|" + no + "|" + version + "|"
							+ amount + "|" + side),
					lines);
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.GL_SOURCE_NOT_READY));
	}

	private PostingRule activeRule(String sourceType, String variant) {
		return this.jdbcTemplate.query("""
				select id, rule_version
				from gl_posting_rule
				where source_type = ?
				and source_variant = ?
				and status = 'ACTIVE'
				""", (rs, rowNum) -> new PostingRule(rs.getLong("id"), rs.getInt("rule_version")), sourceType,
				variant).stream().reduce((first, second) -> {
			throw new BusinessException(ApiErrorCode.GL_RULE_AMBIGUOUS);
		}).orElseThrow(() -> new BusinessException(ApiErrorCode.GL_RULE_MISSING));
	}

	private RuleLine ruleLine(Long ruleId, String factCode, String direction) {
		return this.jdbcTemplate.query("""
				select id, account_id, summary_template
				from gl_posting_rule_line
				where rule_id = ?
				and normalized_fact_code = ?
				and direction = ?
				""", (rs, rowNum) -> new RuleLine(rs.getLong("id"), rs.getLong("account_id"),
				rs.getString("summary_template")), ruleId, factCode, direction).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_RULE_MISSING));
	}

	private FinanceDraftRow lockFinanceDraft(Long id) {
		return this.jdbcTemplate.query("""
				select id, draft_no, source_type, source_id, status, business_date, summary, generation_version, version
				from fin_voucher_draft
				where id = ?
				for update
				""", (rs, rowNum) -> new FinanceDraftRow(rs.getLong("id"), rs.getString("draft_no"),
				rs.getString("source_type"), rs.getLong("source_id"), rs.getString("status"),
				rs.getObject("business_date", LocalDate.class), rs.getString("summary"),
				rs.getLong("generation_version"), rs.getLong("version")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FINANCE_VOUCHER_DRAFT_NOT_FOUND));
	}

	private FinanceDraftRow financeDraft(Long id) {
		return lockFinanceDraft(id);
	}

	private void ensureNoActiveClaim(String sourceType, Long sourceId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_voucher_source_claim
				where source_type = ?
				and source_id = ?
				and status in ('RESERVED', 'POSTED')
				""", Long.class, sourceType, sourceId);
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.GL_SOURCE_ALREADY_ACCOUNTED);
		}
	}

	private Long reserveSourceClaim(SourceFacts facts, CurrentUser operator) {
		try {
			return this.jdbcTemplate.queryForObject("""
					insert into gl_voucher_source_claim (
						source_type, source_id, source_no, status, source_version, source_fingerprint, created_by,
						created_at, updated_by, updated_at
					)
					values (?, ?, ?, 'RESERVED', ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, facts.sourceType(), facts.sourceId(), facts.sourceNo(), facts.version(),
					facts.fingerprint(), operator.username(), OffsetDateTime.now(), operator.username(),
					OffsetDateTime.now());
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.GL_SOURCE_ALREADY_ACCOUNTED);
		}
	}

	private void lockSourceClaim(Long claimId) {
		this.jdbcTemplate.query("""
				select id
				from gl_voucher_source_claim
				where id = ?
				for update
				""", (rs) -> {
		}, claimId);
	}

	private void syncFinancialCloseVoucherStatus(VoucherRow voucher, CurrentUser operator) {
		if ("PROFIT_LOSS_CARRYFORWARD".equals(voucher.sourceType()) && voucher.sourceId() != null) {
			this.jdbcTemplate.update("""
					update fin_close_profit_loss_transfer
					set status = 'POSTED', voucher_status = 'POSTED', updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					and voucher_id = ?
					and status <> 'POSTED'
					""", operator.username(), OffsetDateTime.now(), voucher.sourceId(), voucher.id());
		}
		if ("TAX_SUMMARY".equals(voucher.sourceType()) && voucher.sourceId() != null) {
			this.jdbcTemplate.update("""
					update fin_tax_period_summary
					set updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					and voucher_id = ?
					and status <> 'CONFIRMED'
					""", operator.username(), OffsetDateTime.now(), voucher.sourceId(), voucher.id());
		}
	}

	private VoucherRow lockVoucher(Long id) {
		return this.jdbcTemplate.query("""
				select v.id, v.ledger_id, v.accounting_period_id, p.period_code, p.start_date, v.draft_no,
				       v.voucher_type, v.voucher_date, v.status, v.summary, v.source_type, v.source_id,
				       v.source_no, v.source_claim_id, v.source_original_type, v.source_original_id,
				       v.source_original_no, v.source_original_version, v.source_original_fingerprint,
				       v.debit_total, v.credit_total, v.voucher_no, v.version
				from gl_voucher v
				join gl_accounting_period p on p.id = v.accounting_period_id
				where v.id = ?
				for update of v
				""", (rs, rowNum) -> new VoucherRow(rs.getLong("id"), rs.getLong("ledger_id"),
				rs.getLong("accounting_period_id"), rs.getString("period_code"),
				rs.getObject("start_date", LocalDate.class), rs.getString("draft_no"), rs.getString("voucher_type"),
				rs.getObject("voucher_date", LocalDate.class), rs.getString("status"), rs.getString("summary"),
				rs.getString("source_type"), GeneralLedgerSupport.nullableLong(rs, "source_id"),
				rs.getString("source_no"), GeneralLedgerSupport.nullableLong(rs, "source_claim_id"),
				rs.getString("source_original_type"), GeneralLedgerSupport.nullableLong(rs, "source_original_id"),
				rs.getString("source_original_no"), GeneralLedgerSupport.nullableLong(rs, "source_original_version"),
				rs.getString("source_original_fingerprint"), rs.getBigDecimal("debit_total"),
				rs.getBigDecimal("credit_total"), rs.getString("voucher_no"), rs.getLong("version")), id).stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_VOUCHER_NOT_FOUND));
	}

	private Long approvalInstanceId(Long voucherId) {
		return this.jdbcTemplate.query("""
				select approval_instance_id
				from gl_voucher
				where id = ?
				""", (rs, rowNum) -> GeneralLedgerSupport.nullableLong(rs, "approval_instance_id"), voucherId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_VOUCHER_NOT_FOUND));
	}

	private Long approvalInstanceVersion(Long approvalInstanceId) {
		return this.jdbcTemplate.query("""
				select version
				from platform_approval_instance
				where id = ?
				""", (rs, rowNum) -> rs.getLong("version"), approvalInstanceId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.APPROVAL_STATUS_INVALID));
	}

	private PeriodRow openPeriod(LocalDate date) {
		String periodCode = GeneralLedgerSupport.periodCode(date);
		PeriodRow row = this.jdbcTemplate.query("""
				select p.id, p.period_code, p.start_date, p.end_date, p.status
				from gl_accounting_period p
				join gl_ledger l on l.id = p.ledger_id
				where l.code = 'MAIN'
				and p.period_code = ?
				""", (rs, rowNum) -> new PeriodRow(rs.getLong("id"), rs.getString("period_code"),
				rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
				rs.getString("status")), periodCode).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN));
		if (!"OPEN".equals(row.status()) || date.isBefore(row.startDate()) || date.isAfter(row.endDate())) {
			if ("CLOSED".equals(row.status()) && !date.isBefore(row.startDate()) && !date.isAfter(row.endDate())) {
				throw new BusinessException(ApiErrorCode.FIN_CLOSE_PERIOD_CLOSED);
			}
			throw new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN);
		}
		LedgerInfo ledger = ledgerInfo();
		if (!ledger.initialized() || date.isBefore(GeneralLedgerSupport.periodStart(ledger.startYearMonth()))) {
			throw new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN);
		}
		return row;
	}

	private void ensurePeriodWritable(Long periodId) {
		String status = this.jdbcTemplate.queryForObject("""
				select status
				from gl_accounting_period
				where id = ?
				""", String.class, periodId);
		if ("CLOSED".equals(status)) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_PERIOD_CLOSED);
		}
		if (!"OPEN".equals(status)) {
			throw new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN);
		}
	}

	private LedgerInfo ledgerInfo() {
		return this.jdbcTemplate.query("""
				select id, initialized, start_period_id, start_year_month
				from gl_ledger
				where code = 'MAIN'
				""", (rs, rowNum) -> new LedgerInfo(rs.getLong("id"), rs.getBoolean("initialized"),
				GeneralLedgerSupport.nullableLong(rs, "start_period_id"), rs.getString("start_year_month"))).stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_LEDGER_NOT_INITIALIZED));
	}

	private Long actionIdempotentResult(String action, String resourceType, Long resourceId, String key,
			String fingerprint, CurrentUser operator) {
		if (key == null || key.isBlank()) {
			throw new BusinessException(ApiErrorCode.GL_IDEMPOTENCY_CONFLICT);
		}
		List<ExistingAction> existing = this.jdbcTemplate.query("""
				select request_fingerprint, result_resource_id
				from gl_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and coalesce(resource_id, 0) = coalesce(?, 0)
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingAction(rs.getString("request_fingerprint"),
				rs.getLong("result_resource_id")), operator.id(), action, resourceType, resourceId, key.trim());
		if (existing.isEmpty()) {
			return null;
		}
		if (!existing.getFirst().requestFingerprint().equals(fingerprint)) {
			throw new BusinessException(ApiErrorCode.GL_IDEMPOTENCY_CONFLICT);
		}
		return existing.getFirst().resultResourceId();
	}

	private void recordAction(String action, String resourceType, Long resourceId, Long resourceVersion, String key,
			String fingerprint, String resultType, Long resultId, Long resultVersion, CurrentUser operator) {
		this.jdbcTemplate.update("""
				insert into gl_action_idempotency (
					operator_user_id, operator_username, action, resource_type, resource_id, resource_version,
					idempotency_key, request_fingerprint, result_resource_type, result_resource_id, result_version,
					created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
				on conflict do nothing
				""", operator.id(), operator.username(), action, resourceType, resourceId, resourceVersion, key,
				fingerprint, resultType, resultId, resultVersion);
	}

	private String fingerprint(Object value) {
		return GeneralLedgerSupport.sha256(String.valueOf(value));
	}

	private void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.GL_VERSION_CONFLICT);
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

	public record VoucherRequest(String voucherType, LocalDate voucherDate, String summary, String currency,
			Long version, String idempotencyKey, List<VoucherLineRequest> lines) {
	}

	public record VoucherLineRequest(Integer lineNo, Long accountId, String summary, BigDecimal debitAmount,
			BigDecimal creditAmount, List<AuxiliaryItemRequest> auxiliaryItems) {
	}

	public record AuxiliaryItemRequest(String dimensionCode, Long sourceId, Long auxItemId) {
	}

	public record ActionRequest(Long version, String reason, String idempotencyKey) {
	}

	public record ReversalRequest(Long version, LocalDate voucherDate, String reason, String idempotencyKey) {
	}

	public record ApprovalSnapshot(Long id, String no, String summary, String approvalStatus, Long version) {
	}

	private record PersistedVoucher(Long id, Long version) {
	}

	private record LedgerInfo(Long id, boolean initialized, Long startPeriodId, String startYearMonth) {
	}

	private record PeriodRow(Long id, String periodCode, LocalDate startDate, LocalDate endDate, String status) {
	}

	private record VoucherRow(Long id, Long ledgerId, Long periodId, String periodCode, LocalDate periodStart,
			String draftNo, String voucherType, LocalDate voucherDate, String status, String summary,
			String sourceType, Long sourceId, String sourceNo, Long sourceClaimId, String sourceOriginalType,
			Long sourceOriginalId, String sourceOriginalNo, Long sourceOriginalVersion,
			String sourceOriginalFingerprint, BigDecimal debitTotal, BigDecimal creditTotal, String voucherNo,
			Long version) {
	}

	private record FinanceDraftRow(Long id, String draftNo, String sourceType, Long sourceId, String status,
			LocalDate businessDate, String summary, Long generationVersion, Long version) {
	}

	private record AccountSnapshot(Long id, String code, String name, String category, String balanceDirection,
			boolean postable, boolean enabled) {
	}

	private record DimensionRow(Long id, String code, String name) {
	}

	private record AuxiliarySnapshot(Long dimensionId, String dimensionCode, String dimensionName, String objectType,
			Long objectId, String objectCode, String objectName, Long auxItemId) {
	}

	private record LineDraft(int lineNo, String summary, AccountSnapshot account, BigDecimal debitAmount,
			BigDecimal creditAmount, String normalizedFactCode, String sourceType, Long sourceId, String sourceNo,
			List<AuxiliarySnapshot> auxiliaries) {

		LineDraft {
			summary = summary == null || summary.isBlank() ? account.name() : summary.trim();
			debitAmount = GeneralLedgerSupport.amount(debitAmount);
			creditAmount = GeneralLedgerSupport.amount(creditAmount);
			auxiliaries = auxiliaries == null ? List.of() : List.copyOf(auxiliaries);
		}

		LineDraft reversed() {
			return new LineDraft(this.lineNo, this.summary, this.account, this.creditAmount, this.debitAmount,
					this.normalizedFactCode, this.sourceType, this.sourceId, this.sourceNo, this.auxiliaries);
		}

	}

	private record ValidatedDraft(String voucherType, LocalDate voucherDate, String summary, PeriodRow period,
			PostingRule rule, BigDecimal debitTotal, BigDecimal creditTotal, List<LineDraft> lines) {
	}

	private record PostingRule(Long id, Integer ruleVersion) {
	}

	private record RuleLine(Long id, Long accountId, String summaryTemplate) {
	}

	private record SourceFacts(String sourceType, String sourceVariant, Long sourceId, String sourceNo,
			LocalDate businessDate, Long version, String fingerprint, List<SourceFactLine> lines) {
	}

	private record SourceFactLine(String factCode, String direction, BigDecimal amount, Long customerId,
			Long supplierId, Long projectId) {

		SourceFactLine {
			amount = GeneralLedgerSupport.amount(amount);
		}

	}

	private record ExistingAction(String requestFingerprint, Long resultResourceId) {
	}

}
