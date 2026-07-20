package com.qherp.api.system.financialclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BankReconciliationService {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final JdbcTemplate jdbcTemplate;

	private final FinancialCloseAuditService auditService;

	public BankReconciliationService(JdbcTemplate jdbcTemplate, FinancialCloseAuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional
	public Map<String, Object> createAccount(FinancialCloseModels.BankAccountRequest request, CurrentUser operator) {
		AccountSnapshot glAccount = glAccount(request == null ? null : request.glAccountId());
		if (!"1002".equals(glAccount.code()) || !"ASSET".equals(glAccount.category()) || !glAccount.enabled()
				|| !glAccount.postable()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String normalizedAccountNo = normalizeAccountNo(request.accountNo());
		String fingerprint = FinancialCloseSupport.sha256("BANK_ACCOUNT|" + normalizedAccountNo);
		String last4 = normalizedAccountNo.length() <= 4 ? normalizedAccountNo
				: normalizedAccountNo.substring(normalizedAccountNo.length() - 4);
		String masked = "****" + last4;
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into fin_bank_account (
						account_name, account_type, bank_name, currency, gl_account_id, account_fingerprint,
						account_last4, account_masked, status, opened_on, created_by, updated_by
					)
					values (?, ?, ?, 'CNY', ?, ?, ?, ?, 'ENABLED', ?, ?, ?)
					returning id
					""", Long.class,
					FinancialCloseSupport.requiredText(request.accountName(), ApiErrorCode.VALIDATION_ERROR),
					FinancialCloseSupport.requiredText(request.accountType(), ApiErrorCode.VALIDATION_ERROR)
						.toUpperCase(),
					FinancialCloseSupport.requiredText(request.bankName(), ApiErrorCode.VALIDATION_ERROR),
					request.glAccountId(), fingerprint, last4, masked, request.openedOn(), operator.username(),
					operator.username());
			this.auditService.success(operator, "FIN_BANK_ACCOUNT_CREATE", "FIN_BANK_ACCOUNT", id);
			return account(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	@Transactional
	public Map<String, Object> updateAccount(Long id, FinancialCloseModels.BankAccountRequest request,
			CurrentUser operator) {
		AccountRow current = lockAccount(id);
		requireVersion(current.version(), request == null ? null : request.version());
		AccountSnapshot glAccount = glAccount(request.glAccountId());
		if (!"1002".equals(glAccount.code()) || !"ASSET".equals(glAccount.category()) || !glAccount.enabled()
				|| !glAccount.postable()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		AccountNumberParts accountNumber = accountNumberParts(request.accountNo());
		try {
			int updated = this.jdbcTemplate.update("""
					update fin_bank_account
					set account_name = ?, account_type = ?, bank_name = ?, currency = 'CNY', gl_account_id = ?,
					    account_fingerprint = ?, account_last4 = ?, account_masked = ?, opened_on = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					and version = ?
					""", FinancialCloseSupport.requiredText(request.accountName(), ApiErrorCode.VALIDATION_ERROR),
					FinancialCloseSupport.requiredText(request.accountType(), ApiErrorCode.VALIDATION_ERROR)
						.toUpperCase(),
					FinancialCloseSupport.requiredText(request.bankName(), ApiErrorCode.VALIDATION_ERROR),
					request.glAccountId(), accountNumber.fingerprint(), accountNumber.last4(), accountNumber.masked(),
					request.openedOn(), operator.username(), OffsetDateTime.now(), id, current.version());
			if (updated != 1) {
				throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
			}
			this.auditService.success(operator, "FIN_BANK_ACCOUNT_UPDATE", "FIN_BANK_ACCOUNT", id);
			return account(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	@Transactional
	public Map<String, Object> disableAccount(Long id, FinancialCloseModels.VersionedActionRequest request,
			CurrentUser operator) {
		AccountRow current = lockAccount(id);
		requireVersion(current.version(), request == null ? null : request.version());
		String reason = FinancialCloseSupport.requiredText(request.reason(), ApiErrorCode.VALIDATION_ERROR);
		if ("DISABLED".equals(current.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		int updated = this.jdbcTemplate.update("""
				update fin_bank_account
				set status = 'DISABLED', disabled_reason = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				and version = ?
				and status = 'ENABLED'
				""", reason, operator.username(), OffsetDateTime.now(), id, current.version());
		if (updated != 1) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		this.auditService.success(operator, "FIN_BANK_ACCOUNT_DISABLE", "FIN_BANK_ACCOUNT", id);
		return account(id, operator);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> accounts(int page, int pageSize, CurrentUser currentUser) {
		int safePageSize = FinancialCloseSupport.listLimit(pageSize);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from fin_bank_account", Long.class);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id
				from fin_bank_account
				order by created_at desc, id desc
				limit ? offset ?
				""", (rs, rowNum) -> account(rs.getLong("id"), currentUser), safePageSize,
				FinancialCloseSupport.offset(page, safePageSize));
		return PageResponse.of(items, page, safePageSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> account(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select a.id, a.account_name, a.account_type, a.bank_name, a.currency, a.gl_account_id,
				       g.code as gl_account_code, a.account_last4, a.account_masked, a.status, a.opened_on,
				       a.disabled_reason, a.created_at, a.updated_at, a.version
				from fin_bank_account a
				join gl_account g on g.id = a.gl_account_id
				where a.id = ?
				""", (rs, rowNum) -> {
			boolean sensitiveVisible = FinancialCloseSupport.bankSensitiveVisible(currentUser);
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("accountName", rs.getString("account_name"));
			map.put("accountType", rs.getString("account_type"));
			map.put("bankName", rs.getString("bank_name"));
			map.put("currency", rs.getString("currency"));
			map.put("glAccountId", rs.getLong("gl_account_id"));
			map.put("glAccountCode", rs.getString("gl_account_code"));
			map.put("accountLast4", sensitiveVisible ? rs.getString("account_last4") : null);
			map.put("accountMasked", rs.getString("account_masked"));
			map.put("status", rs.getString("status"));
			map.put("openedOn", rs.getObject("opened_on", LocalDate.class));
			map.put("disabledReason", rs.getString("disabled_reason"));
			FinancialCloseSupport.putVisibility(map, currentUser);
			map.put("allowedActions", List.of());
			map.put("actionDisabledReasons", Map.of());
			map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			map.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
			map.put("version", rs.getLong("version"));
			return map;
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	@Transactional
	public Map<String, Object> importStatementLine(FinancialCloseModels.BankStatementRequest request,
			CurrentUser operator) {
		String direction = FinancialCloseSupport.requiredText(request == null ? null : request.direction(),
				ApiErrorCode.VALIDATION_ERROR).toUpperCase();
		if (!List.of("CREDIT", "DEBIT").contains(direction)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		BigDecimal amount = FinancialCloseSupport.amount(request.amount());
		if (!FinancialCloseSupport.positive(amount)) {
			throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
		}
		account(request.bankAccountId(), operator);
		String dedupe = statementDedupe(request, direction, amount);
		Map<String, Object> existing = statementLineByDedupe(request.bankAccountId(), dedupe, operator);
		if (existing != null) {
			return existing;
		}
		Long statementId = this.jdbcTemplate.queryForObject("""
				insert into fin_bank_statement (
					bank_account_id, statement_no, source_method, period_code, import_fingerprint, status, created_by
				)
				values (?, ?, 'MANUAL', ?, ?, 'IMPORTED', ?)
				returning id
				""", Long.class, request.bankAccountId(), request.referenceNo(),
				FinancialCloseSupport.periodCode(request.postingDate()), dedupe, operator.username());
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into fin_bank_statement_line (
						statement_id, bank_account_id, transaction_date, posting_date, direction, amount,
						counterparty_name, summary, bank_transaction_id, reference_no, dedupe_fingerprint, status,
						source_method, created_by, updated_by
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNMATCHED', 'MANUAL', ?, ?)
					returning id
					""", Long.class, statementId, request.bankAccountId(), request.transactionDate(),
					request.postingDate(), direction, amount, FinancialCloseSupport.text(request.counterpartyName()),
					FinancialCloseSupport.text(request.summary()), FinancialCloseSupport.text(request.bankTransactionId()),
					FinancialCloseSupport.text(request.referenceNo()), dedupe, operator.username(), operator.username());
			this.auditService.success(operator, "FIN_BANK_STATEMENT_IMPORT", "FIN_BANK_STATEMENT_LINE", id);
			return statementLine(id, operator);
		}
		catch (DuplicateKeyException exception) {
			return statementLineByDedupe(request.bankAccountId(), dedupe, operator);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> statementLines(Long bankAccountId, int page, int pageSize,
			CurrentUser currentUser) {
		int safePageSize = FinancialCloseSupport.listLimit(pageSize);
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_bank_statement_line
				where (cast(? as bigint) is null or bank_account_id = cast(? as bigint))
				""", Long.class, bankAccountId, bankAccountId);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id
				from fin_bank_statement_line
				where (cast(? as bigint) is null or bank_account_id = cast(? as bigint))
				order by posting_date desc, id desc
				limit ? offset ?
				""", (rs, rowNum) -> statementLine(rs.getLong("id"), currentUser), bankAccountId, bankAccountId,
				safePageSize, FinancialCloseSupport.offset(page, safePageSize));
		return PageResponse.of(items, page, safePageSize, total == null ? 0 : total);
	}

	@Transactional
	public Map<String, Object> previewStatementImport(FinancialCloseModels.BankStatementImportRequest request,
			CurrentUser currentUser) {
		requireEnabledAccount(request == null ? null : request.bankAccountId());
		ParsedImport parsed = parseImport(request, currentUser);
		Map<String, Object> map = importResult(request.bankAccountId(), null, parsed, currentUser);
		this.auditService.success(currentUser, "FIN_BANK_STATEMENT_IMPORT_PREVIEW", "FIN_BANK_ACCOUNT",
				request.bankAccountId());
		return map;
	}

	@Transactional
	public Map<String, Object> confirmStatementImport(FinancialCloseModels.BankStatementImportRequest request,
			CurrentUser operator) {
		requireEnabledAccount(request == null ? null : request.bankAccountId());
		ParsedImport parsed = parseImport(request, operator);
		if (parsed.errorCount() > 0) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String key = FinancialCloseSupport.requiredText(request.idempotencyKey(), ApiErrorCode.VALIDATION_ERROR);
		String requestFingerprint = FinancialCloseSupport.sha256("BANK_IMPORT_CONFIRM|" + request.bankAccountId()
				+ "|" + parsed.importFingerprint());
		Long existingByKey = idempotentResult("BANK_STATEMENT_IMPORT_CONFIRM", "FIN_BANK_ACCOUNT",
				request.bankAccountId(), key, requestFingerprint, operator);
		if (existingByKey != null) {
			return statementImportResult(existingByKey, parsed, operator);
		}
		Long existingStatementId = existingStatementId(request.bankAccountId(), parsed.importFingerprint());
		if (existingStatementId != null) {
			recordIdempotency("BANK_STATEMENT_IMPORT_CONFIRM", "FIN_BANK_ACCOUNT", request.bankAccountId(), null,
					key, requestFingerprint, "FIN_BANK_STATEMENT", existingStatementId, null, operator);
			return statementImportResult(existingStatementId, parsed, operator);
		}
		String periodCode = parsed.rows()
			.stream()
			.filter((row) -> "VALID".equals(row.status()))
			.findFirst()
			.map((row) -> FinancialCloseSupport.periodCode(row.postingDate()))
			.orElse(null);
		Long statementId = this.jdbcTemplate.queryForObject("""
				insert into fin_bank_statement (
					bank_account_id, statement_no, source_method, period_code, import_fingerprint, status, created_by
				)
				values (?, ?, 'IMPORT', ?, ?, 'IMPORTED', ?)
				returning id
				""", Long.class, request.bankAccountId(), FinancialCloseSupport.text(request.fileName()), periodCode,
				parsed.importFingerprint(), operator.username());
		for (ImportRow row : parsed.rows()) {
			if (!"VALID".equals(row.status())) {
				continue;
			}
			try {
				this.jdbcTemplate.queryForObject("""
						insert into fin_bank_statement_line (
							statement_id, bank_account_id, transaction_date, posting_date, direction, amount,
							counterparty_name, summary, bank_transaction_id, reference_no, dedupe_fingerprint,
							status, source_method, created_by, updated_by
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNMATCHED', 'IMPORT', ?, ?)
						returning id
						""", Long.class, statementId, request.bankAccountId(), row.transactionDate(),
						row.postingDate(), row.direction(), row.amount(), row.counterpartyName(), row.summary(),
						row.bankTransactionId(), row.referenceNo(), row.dedupeFingerprint(), operator.username(),
						operator.username());
			}
			catch (DuplicateKeyException ignored) {
				// 并发或重复确认时保持幂等，最终响应按已落库事实重新查询。
			}
		}
		recordIdempotency("BANK_STATEMENT_IMPORT_CONFIRM", "FIN_BANK_ACCOUNT", request.bankAccountId(), null,
				key, requestFingerprint, "FIN_BANK_STATEMENT", statementId, null, operator);
		this.auditService.success(operator, "FIN_BANK_STATEMENT_IMPORT_CONFIRM", "FIN_BANK_STATEMENT", statementId);
		return statementImportResult(statementId, parsed, operator);
	}

	@Transactional
	public Map<String, Object> ignoreStatementLine(Long id, FinancialCloseModels.VersionedActionRequest request,
			CurrentUser operator) {
		String reason = FinancialCloseSupport.requiredText(request == null ? null : request.reason(),
				ApiErrorCode.VALIDATION_ERROR);
		String key = FinancialCloseSupport.requiredText(request.idempotencyKey(), ApiErrorCode.VALIDATION_ERROR);
		String requestFingerprint = FinancialCloseSupport.sha256("BANK_STATEMENT_IGNORE|" + id + "|"
				+ request.version() + "|" + reason);
		Long existing = idempotentResult("BANK_STATEMENT_IGNORE", "FIN_BANK_STATEMENT_LINE", id, key,
				requestFingerprint, operator);
		if (existing != null) {
			return statementLine(existing, operator);
		}
		StatementLineState current = lockStatementLine(id);
		requireVersion(current.version(), request.version());
		if (!List.of("UNMATCHED", "PARTIALLY_MATCHED").contains(current.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		if (matchedStatementAmountAcrossRuns(id).compareTo(ZERO) > 0) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		int updated = this.jdbcTemplate.update("""
				update fin_bank_statement_line
				set status = 'IGNORED', updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				and version = ?
				and status in ('UNMATCHED', 'PARTIALLY_MATCHED')
				""", operator.username(), OffsetDateTime.now(), id, current.version());
		if (updated != 1) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		recordIdempotency("BANK_STATEMENT_IGNORE", "FIN_BANK_STATEMENT_LINE", id, current.version(), key,
				requestFingerprint, "FIN_BANK_STATEMENT_LINE", id, current.version() + 1, operator);
		this.auditService.success(operator, "FIN_BANK_STATEMENT_IGNORE", "FIN_BANK_STATEMENT_LINE", id);
		return statementLine(id, operator);
	}

	@Transactional
	public Map<String, Object> createReconciliation(FinancialCloseModels.BankReconciliationRequest request,
			CurrentUser operator) {
		Period period = period(request.periodId());
		account(request.bankAccountId(), operator);
		FinancialCloseSupport.advisoryLock(this.jdbcTemplate, "BANK_RECON|" + period.id() + "|"
				+ request.bankAccountId());
		Long id = this.jdbcTemplate.queryForObject("""
				insert into fin_bank_reconciliation_run (
					period_id, bank_account_id, status, source_fingerprint, created_by, updated_by
				)
				values (?, ?, 'DRAFT', ?, ?, ?)
				returning id
				""", Long.class, period.id(), request.bankAccountId(),
				sourceFingerprint(period.id(), request.bankAccountId()), operator.username(), operator.username());
		this.auditService.success(operator, "FIN_BANK_RECONCILIATION_CREATE", "FIN_BANK_RECONCILIATION_RUN", id);
		return reconciliation(id, operator);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> reconciliations(int page, int pageSize, CurrentUser currentUser) {
		int safePageSize = FinancialCloseSupport.listLimit(pageSize);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from fin_bank_reconciliation_run", Long.class);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id
				from fin_bank_reconciliation_run
				order by created_at desc, id desc
				limit ? offset ?
				""", (rs, rowNum) -> reconciliation(rs.getLong("id"), currentUser), safePageSize,
				FinancialCloseSupport.offset(page, safePageSize));
		return PageResponse.of(items, page, safePageSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> reconciliation(Long id, CurrentUser currentUser) {
		RunRow row = run(id);
		return reconciliation(row, currentUser);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> candidates(Long id, int page, int pageSize, CurrentUser currentUser) {
		RunRow row = run(id);
		int safePageSize = FinancialCloseSupport.listLimit(pageSize);
		Map<String, Object> map = FinancialCloseSupport.map();
		map.put("id", row.id());
		map.put("statementLines", statementCandidates(row, safePageSize,
				FinancialCloseSupport.offset(page, safePageSize), currentUser));
		map.put("ledgerEntries", ledgerCandidates(row, safePageSize,
				FinancialCloseSupport.offset(page, safePageSize), currentUser));
		FinancialCloseSupport.putVisibility(map, currentUser);
		map.put("page", FinancialCloseSupport.page(page));
		map.put("pageSize", safePageSize);
		return map;
	}

	@Transactional
	public Map<String, Object> match(Long id, FinancialCloseModels.BankMatchRequest request, CurrentUser operator) {
		RunRow row = lockRun(id);
		requireMutable(row);
		requireVersion(row.version(), request == null ? null : request.version());
		String groupNo = FinancialCloseSupport.requiredText(request.matchGroupNo(), ApiErrorCode.VALIDATION_ERROR);
		if (request.matches() == null || request.matches().isEmpty()) {
			throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
		}
		List<ResolvedMatch> resolved = new ArrayList<>();
		Map<Long, BigDecimal> statementAdds = new HashMap<>();
		Map<Long, BigDecimal> ledgerAdds = new HashMap<>();
		for (FinancialCloseModels.BankMatchLineRequest line : request.matches()) {
			BigDecimal amount = FinancialCloseSupport.amount(line.amount());
			if (!FinancialCloseSupport.positive(amount)) {
				throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
			}
			StatementSide statement = statementSide(row, line.statementLineId());
			LedgerSide ledger = ledgerSide(row, line.ledgerEntryId());
			if (!directionMatches(statement, ledger)) {
				throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
			}
			resolved.add(new ResolvedMatch(statement.id(), ledger.id(), amount));
			statementAdds.merge(statement.id(), amount, BigDecimal::add);
			ledgerAdds.merge(ledger.id(), amount, BigDecimal::add);
		}
		for (Map.Entry<Long, BigDecimal> entry : statementAdds.entrySet()) {
			if (remainingStatementAmount(row.id(), entry.getKey()).compareTo(FinancialCloseSupport.amount(entry.getValue())) < 0) {
				throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
			}
		}
		for (Map.Entry<Long, BigDecimal> entry : ledgerAdds.entrySet()) {
			if (remainingLedgerAmount(row.id(), entry.getKey()).compareTo(FinancialCloseSupport.amount(entry.getValue())) < 0) {
				throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
			}
		}
		for (ResolvedMatch line : resolved) {
			this.jdbcTemplate.update("""
					insert into fin_bank_reconciliation_match (
						run_id, match_group_no, statement_line_id, ledger_entry_id, match_amount, created_by
					)
					values (?, ?, ?, ?, ?, ?)
					""", row.id(), groupNo, line.statementLineId(), line.ledgerEntryId(), line.amount(),
					operator.username());
		}
		updateStatementStatuses(row);
		touchRun(row, "RECONCILING", operator);
		this.auditService.success(operator, "FIN_BANK_RECONCILIATION_MATCH", "FIN_BANK_RECONCILIATION_RUN", row.id());
		return reconciliation(row.id(), operator);
	}

	@Transactional
	public Map<String, Object> cancelMatch(Long id, String matchGroupNo,
			FinancialCloseModels.VersionedActionRequest request, CurrentUser operator) {
		RunRow row = lockRun(id);
		requireMutable(row);
		requireVersion(row.version(), request == null ? null : request.version());
		String group = FinancialCloseSupport.requiredText(matchGroupNo, ApiErrorCode.VALIDATION_ERROR);
		this.jdbcTemplate.update("""
				delete from fin_bank_reconciliation_match
				where run_id = ?
				and match_group_no = ?
				""", row.id(), group);
		updateStatementStatuses(row);
		touchRun(row, "RECONCILING", operator);
		this.auditService.success(operator, "FIN_BANK_RECONCILIATION_CANCEL_MATCH",
				"FIN_BANK_RECONCILIATION_RUN", row.id());
		return reconciliation(row.id(), operator);
	}

	@Transactional
	public Map<String, Object> classifyException(Long id, FinancialCloseModels.BankExceptionRequest request,
			CurrentUser operator) {
		RunRow row = lockRun(id);
		requireMutable(row);
		requireVersion(row.version(), request == null ? null : request.version());
		String type = FinancialCloseSupport.requiredText(request.exceptionType(), ApiErrorCode.VALIDATION_ERROR)
			.toUpperCase();
		if (!List.of("BANK_ONLY", "LEDGER_ONLY", "AMOUNT_DIFFERENCE", "DATE_DIFFERENCE").contains(type)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String reason = FinancialCloseSupport.requiredText(request.reason(), ApiErrorCode.VALIDATION_ERROR);
		BigDecimal amount = FinancialCloseSupport.amount(request.amount());
		if ("BANK_ONLY".equals(type)) {
			if (request.statementLineId() == null || !FinancialCloseSupport.positive(amount)
					|| remainingStatementAmount(row.id(), request.statementLineId()).compareTo(amount) < 0) {
				throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
			}
			statementSide(row, request.statementLineId());
		}
		if ("LEDGER_ONLY".equals(type)) {
			if (request.ledgerEntryId() == null || !FinancialCloseSupport.positive(amount)
					|| remainingLedgerAmount(row.id(), request.ledgerEntryId()).compareTo(amount) < 0) {
				throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
			}
			ledgerSide(row, request.ledgerEntryId());
		}
		if (("AMOUNT_DIFFERENCE".equals(type) || "DATE_DIFFERENCE".equals(type)) && amount.compareTo(ZERO) < 0) {
			throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
		}
		this.jdbcTemplate.update("""
				insert into fin_bank_reconciliation_exception (
					run_id, statement_line_id, ledger_entry_id, exception_type, amount, reason, status, created_by
				)
				values (?, ?, ?, ?, ?, ?, 'OPEN', ?)
				""", row.id(), request.statementLineId(), request.ledgerEntryId(), type, amount, reason,
				operator.username());
		touchRun(row, "RECONCILING", operator);
		this.auditService.success(operator, "FIN_BANK_RECONCILIATION_EXCEPTION",
				"FIN_BANK_RECONCILIATION_RUN", row.id());
		return reconciliation(row.id(), operator);
	}

	@Transactional
	public Map<String, Object> calculate(Long id, FinancialCloseModels.VersionedActionRequest request,
			CurrentUser operator) {
		RunRow row = lockRun(id);
		requireMutable(row);
		requireVersion(row.version(), request == null ? null : request.version());
		ReconciliationMetrics metrics = metrics(row);
		String status = metrics.difference().compareTo(ZERO) == 0 && metrics.unclassifiedCount() == 0 ? "BALANCED"
				: "RECONCILING";
		int updated = this.jdbcTemplate.update("""
				update fin_bank_reconciliation_run
				set status = ?, statement_balance = ?, ledger_balance = ?, difference_amount = ?,
				    source_fingerprint = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				and version = ?
				""", status, metrics.bankEndingBalance(), metrics.glEndingBalance(), metrics.difference(),
				sourceFingerprint(row.periodId(), row.bankAccountId()), operator.username(), OffsetDateTime.now(),
				row.id(), row.version());
		if (updated != 1) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		this.auditService.success(operator, "FIN_BANK_RECONCILIATION_CALCULATE",
				"FIN_BANK_RECONCILIATION_RUN", row.id());
		return reconciliation(row.id(), operator);
	}

	@Transactional
	public Map<String, Object> confirmReconciliation(Long id, FinancialCloseModels.VersionedActionRequest request,
			CurrentUser operator) {
		RunRow row = lockRun(id);
		requireMutable(row);
		requireVersion(row.version(), request == null ? null : request.version());
		String fingerprint = sourceFingerprint(row.periodId(), row.bankAccountId());
		if (!fingerprint.equals(row.sourceFingerprint())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_STALE);
		}
		ReconciliationMetrics metrics = metrics(row);
		if (metrics.difference().compareTo(ZERO) != 0 || metrics.unclassifiedCount() != 0) {
			throw new BusinessException(ApiErrorCode.FIN_BANK_RECON_NOT_BALANCED);
		}
		int updated = this.jdbcTemplate.update("""
				update fin_bank_reconciliation_run
				set status = 'CONFIRMED', statement_balance = ?, ledger_balance = ?, difference_amount = ?,
				    confirmed_by = ?, confirmed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				and version = ?
				""", metrics.bankEndingBalance(), metrics.glEndingBalance(), metrics.difference(), operator.username(),
				OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), row.id(), row.version());
		if (updated != 1) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		this.auditService.success(operator, "FIN_BANK_RECONCILIATION_CONFIRM",
				"FIN_BANK_RECONCILIATION_RUN", row.id());
		return reconciliation(row.id(), operator);
	}

	@Transactional
	public Map<String, Object> reopenReconciliation(Long id, FinancialCloseModels.VersionedActionRequest request,
			CurrentUser operator) {
		RunRow row = lockRun(id);
		requireVersion(row.version(), request == null ? null : request.version());
		if (!"CONFIRMED".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		String reason = FinancialCloseSupport.requiredText(request.reason(), ApiErrorCode.VALIDATION_ERROR);
		Long newId = this.jdbcTemplate.queryForObject("""
				insert into fin_bank_reconciliation_run (
					period_id, bank_account_id, status, source_fingerprint, reopened_by, reopened_at,
					reopen_reason, created_by, updated_by
				)
				values (?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, row.periodId(), row.bankAccountId(), sourceFingerprint(row.periodId(),
				row.bankAccountId()), operator.username(), OffsetDateTime.now(), reason, operator.username(),
				operator.username());
		Map<String, Object> result = reconciliation(newId, operator);
		result.put("reopenedFromId", row.id());
		this.auditService.success(operator, "FIN_BANK_RECONCILIATION_REOPEN",
				"FIN_BANK_RECONCILIATION_RUN", newId);
		return result;
	}

	private Map<String, Object> reconciliation(RunRow row, CurrentUser currentUser) {
		ReconciliationMetrics metrics = metrics(row);
		String currentFingerprint = sourceFingerprint(row.periodId(), row.bankAccountId());
		boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
		boolean sourceVisible = FinancialCloseSupport.sourceVisible(currentUser);
		Map<String, Object> map = FinancialCloseSupport.map();
		map.put("id", row.id());
		map.put("periodId", row.periodId());
		map.put("bankAccountId", row.bankAccountId());
		map.put("status", row.status());
		map.put("statementBalance", FinancialCloseSupport.visibleDecimal(row.statementBalance(), amountVisible));
		map.put("ledgerBalance", FinancialCloseSupport.visibleDecimal(row.ledgerBalance(), amountVisible));
		map.put("differenceAmount", FinancialCloseSupport.visibleDecimal(row.differenceAmount(), amountVisible));
		map.put("bankEndingBalance", FinancialCloseSupport.visibleDecimal(metrics.bankEndingBalance(),
				amountVisible));
		map.put("glEndingBalance", FinancialCloseSupport.visibleDecimal(metrics.glEndingBalance(), amountVisible));
		map.put("adjustedBankBalance", FinancialCloseSupport.visibleDecimal(metrics.adjustedBankBalance(),
				amountVisible));
		map.put("adjustedBookBalance", FinancialCloseSupport.visibleDecimal(metrics.adjustedBookBalance(),
				amountVisible));
		map.put("difference", FinancialCloseSupport.visibleDecimal(metrics.difference(), amountVisible));
		map.put("matchedAmount", FinancialCloseSupport.visibleDecimal(metrics.matchedAmount(), amountVisible));
		map.put("unclassifiedCount", metrics.unclassifiedCount());
		map.put("sourceFingerprint", sourceVisible ? row.sourceFingerprint() : null);
		map.put("stale", "CONFIRMED".equals(row.status()) && !currentFingerprint.equals(row.sourceFingerprint()));
		map.put("confirmedBy", row.confirmedBy());
		map.put("confirmedAt", row.confirmedAt());
		FinancialCloseSupport.putVisibility(map, currentUser);
		map.put("allowedActions", List.of());
		map.put("actionDisabledReasons", Map.of());
		map.put("version", row.version());
		map.put("matches", matches(row.id(), currentUser));
		map.put("exceptions", exceptions(row.id(), currentUser));
		return map;
	}

	private List<Map<String, Object>> statementCandidates(RunRow row, int limit, int offset,
			CurrentUser currentUser) {
		Period period = period(row.periodId());
		boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
		boolean sourceVisible = FinancialCloseSupport.sourceVisible(currentUser);
		return this.jdbcTemplate.query("""
				select l.id, l.statement_id, l.bank_account_id, l.transaction_date, l.posting_date, l.direction,
				       l.amount, l.counterparty_name, l.summary, l.bank_transaction_id, l.reference_no, l.status,
				       l.source_method, l.version
				from fin_bank_statement_line l
				where l.bank_account_id = ?
				and l.posting_date between ? and ?
				and l.status <> 'IGNORED'
				order by l.posting_date, l.id
				limit ? offset ?
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			Long lineId = rs.getLong("id");
			map.put("id", lineId);
			map.put("statementId", FinancialCloseSupport.nullableLong(rs, "statement_id"));
			map.put("bankAccountId", rs.getLong("bank_account_id"));
			map.put("transactionDate", rs.getObject("transaction_date", LocalDate.class));
			map.put("postingDate", rs.getObject("posting_date", LocalDate.class));
			map.put("direction", rs.getString("direction"));
			map.put("amount", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("amount"), amountVisible));
			map.put("counterpartyName", rs.getString("counterparty_name"));
			map.put("summary", rs.getString("summary"));
			map.put("bankTransactionId", sourceVisible ? rs.getString("bank_transaction_id") : null);
			map.put("referenceNo", sourceVisible ? rs.getString("reference_no") : null);
			map.put("status", rs.getString("status"));
			map.put("sourceMethod", rs.getString("source_method"));
			map.put("matchedAmount", FinancialCloseSupport.visibleDecimal(matchedStatementAmount(row.id(), lineId),
					amountVisible));
			map.put("remainingAmount", FinancialCloseSupport.visibleDecimal(remainingStatementAmount(row.id(),
					lineId), amountVisible));
			FinancialCloseSupport.putVisibility(map, currentUser);
			map.put("version", rs.getLong("version"));
			return map;
		}, row.bankAccountId(), period.startDate(), period.endDate(), limit, offset);
	}

	private List<Map<String, Object>> ledgerCandidates(RunRow row, int limit, int offset, CurrentUser currentUser) {
		AccountSnapshot account = bankAccount(row.bankAccountId());
		boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
		boolean sourceVisible = FinancialCloseSupport.sourceVisible(currentUser);
		return this.jdbcTemplate.query("""
				select e.id, e.voucher_id, e.voucher_date, e.voucher_no, e.line_no, e.summary, e.account_id,
				       e.account_code, e.account_name, e.debit_amount, e.credit_amount
				from gl_ledger_entry e
				where e.period_id = ?
				and e.account_id = ?
				order by e.voucher_date, e.id
				limit ? offset ?
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			Long entryId = rs.getLong("id");
			BigDecimal debit = FinancialCloseSupport.amount(rs.getBigDecimal("debit_amount"));
			BigDecimal credit = FinancialCloseSupport.amount(rs.getBigDecimal("credit_amount"));
			map.put("id", entryId);
			map.put("voucherId", rs.getLong("voucher_id"));
			map.put("voucherDate", rs.getObject("voucher_date", LocalDate.class));
			map.put("voucherNo", sourceVisible ? rs.getString("voucher_no") : null);
			map.put("lineNo", rs.getInt("line_no"));
			map.put("summary", rs.getString("summary"));
			map.put("accountId", rs.getLong("account_id"));
			map.put("accountCode", rs.getString("account_code"));
			map.put("accountName", rs.getString("account_name"));
			map.put("direction", debit.compareTo(ZERO) > 0 ? "DEBIT" : "CREDIT");
			map.put("amount", FinancialCloseSupport.visibleDecimal(debit.compareTo(ZERO) > 0 ? debit : credit,
					amountVisible));
			map.put("matchedAmount", FinancialCloseSupport.visibleDecimal(matchedLedgerAmount(row.id(), entryId),
					amountVisible));
			map.put("remainingAmount", FinancialCloseSupport.visibleDecimal(remainingLedgerAmount(row.id(), entryId),
					amountVisible));
			FinancialCloseSupport.putVisibility(map, currentUser);
			return map;
		}, row.periodId(), account.glAccountId(), limit, offset);
	}

	private ReconciliationMetrics metrics(RunRow row) {
		BigDecimal bankEnding = queryAmount("""
				select coalesce(sum(case when direction = 'CREDIT' then amount else -amount end), 0)
				from fin_bank_statement_line l
				join gl_accounting_period p on p.id = ?
				where l.bank_account_id = ?
				and l.posting_date between p.start_date and p.end_date
				and l.status <> 'IGNORED'
				""", row.periodId(), row.bankAccountId());
		BigDecimal glEnding = queryAmount("""
				select coalesce(sum(e.debit_amount - e.credit_amount), 0)
				from gl_ledger_entry e
				join fin_bank_account a on a.id = ?
				where e.period_id = ?
				and e.account_id = a.gl_account_id
				""", row.bankAccountId(), row.periodId());
		BigDecimal ledgerOnly = queryAmount("""
				select coalesce(sum(case when e.debit_amount > 0 then x.amount else -x.amount end), 0)
				from fin_bank_reconciliation_exception x
				join gl_ledger_entry e on e.id = x.ledger_entry_id
				where x.run_id = ?
				and x.status = 'OPEN'
				and x.exception_type = 'LEDGER_ONLY'
				""", row.id());
		BigDecimal bankOnly = queryAmount("""
				select coalesce(sum(case when l.direction = 'CREDIT' then x.amount else -x.amount end), 0)
				from fin_bank_reconciliation_exception x
				join fin_bank_statement_line l on l.id = x.statement_line_id
				where x.run_id = ?
				and x.status = 'OPEN'
				and x.exception_type = 'BANK_ONLY'
				""", row.id());
		BigDecimal adjustedBank = FinancialCloseSupport.amount(bankEnding.add(ledgerOnly));
		BigDecimal adjustedBook = FinancialCloseSupport.amount(glEnding.add(bankOnly));
		BigDecimal matched = queryAmount("""
				select coalesce(sum(match_amount), 0)
				from fin_bank_reconciliation_match
				where run_id = ?
				""", row.id());
		return new ReconciliationMetrics(bankEnding, glEnding, adjustedBank, adjustedBook,
				FinancialCloseSupport.amount(adjustedBank.subtract(adjustedBook)), unclassifiedCount(row), matched);
	}

	private int unclassifiedCount(RunRow row) {
		Period period = period(row.periodId());
		Integer statementCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_bank_statement_line l
				where l.bank_account_id = ?
				and l.posting_date between ? and ?
				and l.status <> 'IGNORED'
				and l.amount - coalesce((
					select sum(m.match_amount) from fin_bank_reconciliation_match m
					where m.run_id = ? and m.statement_line_id = l.id
				), 0) > 0
				and not exists (
					select 1 from fin_bank_reconciliation_exception x
					where x.run_id = ? and x.statement_line_id = l.id and x.status = 'OPEN'
				)
				""", Integer.class, row.bankAccountId(), period.startDate(), period.endDate(), row.id(), row.id());
		AccountSnapshot account = bankAccount(row.bankAccountId());
		Integer ledgerCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_ledger_entry e
				where e.period_id = ?
				and e.account_id = ?
				and (case when e.debit_amount > 0 then e.debit_amount else e.credit_amount end) - coalesce((
					select sum(m.match_amount) from fin_bank_reconciliation_match m
					where m.run_id = ? and m.ledger_entry_id = e.id
				), 0) > 0
				and not exists (
					select 1 from fin_bank_reconciliation_exception x
					where x.run_id = ? and x.ledger_entry_id = e.id and x.status = 'OPEN'
				)
				""", Integer.class, row.periodId(), account.glAccountId(), row.id(), row.id());
		return (statementCount == null ? 0 : statementCount) + (ledgerCount == null ? 0 : ledgerCount);
	}

	private List<Map<String, Object>> matches(Long runId, CurrentUser currentUser) {
		boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
		return this.jdbcTemplate.query("""
				select id, match_group_no, statement_line_id, ledger_entry_id, match_amount, created_by, created_at
				from fin_bank_reconciliation_match
				where run_id = ?
				order by id
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("matchGroupNo", rs.getString("match_group_no"));
			map.put("statementLineId", FinancialCloseSupport.nullableLong(rs, "statement_line_id"));
			map.put("ledgerEntryId", FinancialCloseSupport.nullableLong(rs, "ledger_entry_id"));
			map.put("matchAmount", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("match_amount"),
					amountVisible));
			map.put("createdBy", rs.getString("created_by"));
			map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			return map;
		}, runId);
	}

	private List<Map<String, Object>> exceptions(Long runId, CurrentUser currentUser) {
		boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
		return this.jdbcTemplate.query("""
				select id, statement_line_id, ledger_entry_id, exception_type, amount, reason, status, created_by,
				       created_at, version
				from fin_bank_reconciliation_exception
				where run_id = ?
				order by id
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("statementLineId", FinancialCloseSupport.nullableLong(rs, "statement_line_id"));
			map.put("ledgerEntryId", FinancialCloseSupport.nullableLong(rs, "ledger_entry_id"));
			map.put("exceptionType", rs.getString("exception_type"));
			map.put("amount", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("amount"), amountVisible));
			map.put("reason", rs.getString("reason"));
			map.put("status", rs.getString("status"));
			map.put("createdBy", rs.getString("created_by"));
			map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			map.put("version", rs.getLong("version"));
			return map;
		}, runId);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> statementLine(Long id, CurrentUser currentUser) {
		boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
		boolean sourceVisible = FinancialCloseSupport.sourceVisible(currentUser);
		return this.jdbcTemplate.query("""
				select id, statement_id, bank_account_id, transaction_date, posting_date, direction, amount,
				       counterparty_name, summary, bank_transaction_id, reference_no, status, source_method,
				       created_at, updated_at, version
				from fin_bank_statement_line
				where id = ?
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("statementId", FinancialCloseSupport.nullableLong(rs, "statement_id"));
			map.put("bankAccountId", rs.getLong("bank_account_id"));
			map.put("transactionDate", rs.getObject("transaction_date", LocalDate.class));
			map.put("postingDate", rs.getObject("posting_date", LocalDate.class));
			map.put("direction", rs.getString("direction"));
			map.put("amount", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("amount"), amountVisible));
			map.put("counterpartyName", rs.getString("counterparty_name"));
			map.put("summary", rs.getString("summary"));
			map.put("bankTransactionId", sourceVisible ? rs.getString("bank_transaction_id") : null);
			map.put("referenceNo", sourceVisible ? rs.getString("reference_no") : null);
			map.put("status", rs.getString("status"));
			map.put("sourceMethod", rs.getString("source_method"));
			FinancialCloseSupport.putVisibility(map, currentUser);
			map.put("allowedActions", List.of());
			map.put("actionDisabledReasons", Map.of());
			map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			map.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
			map.put("version", rs.getLong("version"));
			return map;
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private Map<String, Object> statementLineByDedupe(Long bankAccountId, String dedupe, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select id
				from fin_bank_statement_line
				where bank_account_id = ?
				and dedupe_fingerprint = ?
				""", (rs, rowNum) -> statementLine(rs.getLong("id"), currentUser), bankAccountId, dedupe)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private ParsedImport parseImport(FinancialCloseModels.BankStatementImportRequest request, CurrentUser currentUser) {
		if (request == null || request.bankAccountId() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String content = FinancialCloseSupport.text(request.csvContent()) == null
				? FinancialCloseSupport.text(request.content()) : FinancialCloseSupport.text(request.csvContent());
		if (content == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		List<String> physicalLines = content.lines().map(String::trim).filter((line) -> !line.isBlank()).toList();
		if (physicalLines.size() < 2) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		Map<String, Integer> headers = importHeaders(parseCsvLine(physicalLines.get(0)));
		String calculatedFingerprint = FinancialCloseSupport.sha256("BANK_IMPORT|" + request.bankAccountId()
				+ "|" + String.join("\n", physicalLines));
		String requestedFingerprint = FinancialCloseSupport.text(request.importFingerprint());
		if (requestedFingerprint != null && !requestedFingerprint.equals(calculatedFingerprint)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		List<ImportRow> rows = new ArrayList<>();
		Map<String, Long> seen = new HashMap<>();
		int valid = 0;
		int duplicate = 0;
		int error = 0;
		for (int index = 1; index < physicalLines.size(); index++) {
			List<String> columns = parseCsvLine(physicalLines.get(index));
			List<String> errors = new ArrayList<>();
			LocalDate transactionDate = parseDate(value(columns, headers, "transactionDate"), "transactionDate",
					errors);
			LocalDate postingDate = parseDate(value(columns, headers, "postingDate"), "postingDate", errors);
			String direction = FinancialCloseSupport.text(value(columns, headers, "direction"));
			direction = direction == null ? null : direction.toUpperCase(Locale.ROOT);
			if (!List.of("CREDIT", "DEBIT").contains(direction)) {
				errors.add("direction");
			}
			BigDecimal amount = parseAmount(value(columns, headers, "amount"), errors);
			String counterpartyName = FinancialCloseSupport.text(value(columns, headers, "counterpartyName"));
			String summary = FinancialCloseSupport.text(value(columns, headers, "summary"));
			String bankTransactionId = FinancialCloseSupport.text(value(columns, headers, "bankTransactionId"));
			String referenceNo = FinancialCloseSupport.text(value(columns, headers, "referenceNo"));
			String dedupe = null;
			Long existingLineId = null;
			String status = "ERROR";
			if (errors.isEmpty()) {
				dedupe = statementDedupe(request.bankAccountId(), transactionDate, postingDate, direction, amount,
						bankTransactionId, referenceNo);
				existingLineId = existingStatementLineId(request.bankAccountId(), dedupe);
				if (existingLineId != null || seen.containsKey(dedupe)) {
					status = "DUPLICATE";
					duplicate++;
				}
				else {
					status = "VALID";
					seen.put(dedupe, (long) index);
					valid++;
				}
			}
			else {
				error++;
			}
			rows.add(new ImportRow(index, status, transactionDate, postingDate, direction, amount, counterpartyName,
					summary, bankTransactionId, referenceNo, dedupe, existingLineId, errors));
		}
		return new ParsedImport(calculatedFingerprint, rows, rows.size(), valid, duplicate, error);
	}

	private Map<String, Object> importResult(Long bankAccountId, Long statementId, ParsedImport parsed,
			CurrentUser currentUser) {
		Map<String, Object> map = FinancialCloseSupport.map();
		map.put("bankAccountId", bankAccountId);
		map.put("statementId", statementId);
		map.put("importFingerprint", FinancialCloseSupport.sourceVisible(currentUser)
				? parsed.importFingerprint() : null);
		map.put("rowCount", parsed.rowCount());
		map.put("validCount", parsed.validCount());
		map.put("duplicateCount", parsed.duplicateCount());
		map.put("errorCount", parsed.errorCount());
		map.put("lines", parsed.rows().stream().map((row) -> importRowMap(row, currentUser)).toList());
		FinancialCloseSupport.putVisibility(map, currentUser);
		map.put("allowedActions", List.of());
		map.put("actionDisabledReasons", Map.of());
		return map;
	}

	private Map<String, Object> statementImportResult(Long statementId, ParsedImport parsed, CurrentUser currentUser) {
		Long bankAccountId = this.jdbcTemplate.queryForObject("""
				select bank_account_id
				from fin_bank_statement
				where id = ?
				""", Long.class, statementId);
		List<Map<String, Object>> lines = this.jdbcTemplate.query("""
				select id
				from fin_bank_statement_line
				where statement_id = ?
				order by id
				""", (rs, rowNum) -> statementLine(rs.getLong("id"), currentUser), statementId);
		Map<String, Object> map = importResult(bankAccountId, statementId, parsed, currentUser);
		map.put("importedCount", lines.size());
		map.put("lines", lines);
		return map;
	}

	private Map<String, Object> importRowMap(ImportRow row, CurrentUser currentUser) {
		boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
		boolean sourceVisible = FinancialCloseSupport.sourceVisible(currentUser);
		Map<String, Object> map = FinancialCloseSupport.map();
		map.put("rowNo", row.rowNo());
		map.put("status", row.status());
		map.put("transactionDate", row.transactionDate());
		map.put("postingDate", row.postingDate());
		map.put("direction", row.direction());
		map.put("amount", FinancialCloseSupport.visibleDecimal(row.amount(), amountVisible));
		map.put("counterpartyName", row.counterpartyName());
		map.put("summary", row.summary());
		map.put("bankTransactionId", sourceVisible ? row.bankTransactionId() : null);
		map.put("referenceNo", sourceVisible ? row.referenceNo() : null);
		map.put("dedupeFingerprint", sourceVisible ? row.dedupeFingerprint() : null);
		map.put("existingLineId", sourceVisible ? row.existingLineId() : null);
		map.put("duplicate", "DUPLICATE".equals(row.status()));
		map.put("errors", row.errors());
		FinancialCloseSupport.putVisibility(map, currentUser);
		return map;
	}

	private Map<String, Integer> importHeaders(List<String> headers) {
		Map<String, Integer> result = new HashMap<>();
		for (int index = 0; index < headers.size(); index++) {
			result.put(normalizeHeader(headers.get(index)), index);
		}
		for (String required : List.of("transactionDate", "postingDate", "direction", "amount")) {
			if (!result.containsKey(normalizeHeader(required))) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
		}
		return result;
	}

	private String value(List<String> columns, Map<String, Integer> headers, String name) {
		Integer index = headers.get(normalizeHeader(name));
		if (index == null || index >= columns.size()) {
			return null;
		}
		return columns.get(index);
	}

	private String normalizeHeader(String header) {
		return header == null ? "" : header.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
	}

	private List<String> parseCsvLine(String line) {
		List<String> values = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char character = line.charAt(index);
			if (character == '"') {
				if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
					current.append('"');
					index++;
				}
				else {
					quoted = !quoted;
				}
			}
			else if (character == ',' && !quoted) {
				values.add(current.toString().trim());
				current.setLength(0);
			}
			else {
				current.append(character);
			}
		}
		values.add(current.toString().trim());
		return values;
	}

	private LocalDate parseDate(String value, String fieldName, List<String> errors) {
		String text = FinancialCloseSupport.text(value);
		if (text == null) {
			errors.add(fieldName);
			return null;
		}
		try {
			return LocalDate.parse(text);
		}
		catch (DateTimeParseException exception) {
			errors.add(fieldName);
			return null;
		}
	}

	private BigDecimal parseAmount(String value, List<String> errors) {
		String text = FinancialCloseSupport.text(value);
		if (text == null) {
			errors.add("amount");
			return ZERO;
		}
		try {
			BigDecimal amount = FinancialCloseSupport.amount(new BigDecimal(text));
			if (!FinancialCloseSupport.positive(amount)) {
				errors.add("amount");
			}
			return amount;
		}
		catch (NumberFormatException exception) {
			errors.add("amount");
			return ZERO;
		}
	}

	private Long existingStatementId(Long bankAccountId, String importFingerprint) {
		return this.jdbcTemplate.query("""
				select id
				from fin_bank_statement
				where bank_account_id = ?
				and import_fingerprint = ?
				""", (rs, rowNum) -> rs.getLong("id"), bankAccountId, importFingerprint).stream().findFirst()
			.orElse(null);
	}

	private Long existingStatementLineId(Long bankAccountId, String dedupe) {
		return this.jdbcTemplate.query("""
				select id
				from fin_bank_statement_line
				where bank_account_id = ?
				and dedupe_fingerprint = ?
				""", (rs, rowNum) -> rs.getLong("id"), bankAccountId, dedupe).stream().findFirst().orElse(null);
	}

	private Long idempotentResult(String action, String resourceType, Long resourceId, String key,
			String requestFingerprint, CurrentUser operator) {
		return this.jdbcTemplate.query("""
				select request_fingerprint, result_resource_id
				from fin_close_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and coalesce(resource_id, 0) = coalesce(?, 0)
				and idempotency_key = ?
				""", (rs, rowNum) -> {
			if (!requestFingerprint.equals(rs.getString("request_fingerprint"))) {
				throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
			}
			return rs.getLong("result_resource_id");
		}, operator.id(), action, resourceType, resourceId, key).stream().findFirst().orElse(null);
	}

	private void recordIdempotency(String action, String resourceType, Long resourceId, Long resourceVersion,
			String key, String requestFingerprint, String resultResourceType, Long resultResourceId,
			Long resultVersion, CurrentUser operator) {
		try {
			this.jdbcTemplate.update("""
					insert into fin_close_action_idempotency (
						operator_user_id, operator_username, action, resource_type, resource_id, resource_version,
						idempotency_key, request_fingerprint, result_resource_type, result_resource_id,
						result_version
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), operator.username(), action, resourceType, resourceId, resourceVersion,
					key, requestFingerprint, resultResourceType, resultResourceId, resultVersion);
		}
		catch (DuplicateKeyException exception) {
			idempotentResult(action, resourceType, resourceId, key, requestFingerprint, operator);
		}
	}

	private RunRow run(Long id) {
		return this.jdbcTemplate.query("""
				select id, period_id, bank_account_id, status, statement_balance, ledger_balance, difference_amount,
				       source_fingerprint, confirmed_by, confirmed_at, version
				from fin_bank_reconciliation_run
				where id = ?
				""", (rs, rowNum) -> new RunRow(rs.getLong("id"), rs.getLong("period_id"),
				rs.getLong("bank_account_id"), rs.getString("status"),
				FinancialCloseSupport.amount(rs.getBigDecimal("statement_balance")),
				FinancialCloseSupport.amount(rs.getBigDecimal("ledger_balance")),
				FinancialCloseSupport.amount(rs.getBigDecimal("difference_amount")), rs.getString("source_fingerprint"),
				rs.getString("confirmed_by"), rs.getObject("confirmed_at", OffsetDateTime.class),
				rs.getLong("version")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private RunRow lockRun(Long id) {
		return this.jdbcTemplate.query("""
				select id, period_id, bank_account_id, status, statement_balance, ledger_balance, difference_amount,
				       source_fingerprint, confirmed_by, confirmed_at, version
				from fin_bank_reconciliation_run
				where id = ?
				for update
				""", (rs, rowNum) -> new RunRow(rs.getLong("id"), rs.getLong("period_id"),
				rs.getLong("bank_account_id"), rs.getString("status"),
				FinancialCloseSupport.amount(rs.getBigDecimal("statement_balance")),
				FinancialCloseSupport.amount(rs.getBigDecimal("ledger_balance")),
				FinancialCloseSupport.amount(rs.getBigDecimal("difference_amount")), rs.getString("source_fingerprint"),
				rs.getString("confirmed_by"), rs.getObject("confirmed_at", OffsetDateTime.class),
				rs.getLong("version")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private StatementSide statementSide(RunRow row, Long statementLineId) {
		Period period = period(row.periodId());
		return this.jdbcTemplate.query("""
				select id, direction, amount
				from fin_bank_statement_line
				where id = ?
				and bank_account_id = ?
				and posting_date between ? and ?
				and status <> 'IGNORED'
				for update
				""", (rs, rowNum) -> new StatementSide(rs.getLong("id"), rs.getString("direction"),
				FinancialCloseSupport.amount(rs.getBigDecimal("amount"))), statementLineId, row.bankAccountId(),
				period.startDate(), period.endDate()).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID));
	}

	private LedgerSide ledgerSide(RunRow row, Long ledgerEntryId) {
		AccountSnapshot account = bankAccount(row.bankAccountId());
		return this.jdbcTemplate.query("""
				select id, debit_amount, credit_amount
				from gl_ledger_entry
				where id = ?
				and period_id = ?
				and account_id = ?
				for update
				""", (rs, rowNum) -> new LedgerSide(rs.getLong("id"),
				FinancialCloseSupport.amount(rs.getBigDecimal("debit_amount")),
				FinancialCloseSupport.amount(rs.getBigDecimal("credit_amount"))), ledgerEntryId, row.periodId(),
				account.glAccountId()).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID));
	}

	private boolean directionMatches(StatementSide statement, LedgerSide ledger) {
		return ("CREDIT".equals(statement.direction()) && ledger.debitAmount().compareTo(ZERO) > 0)
				|| ("DEBIT".equals(statement.direction()) && ledger.creditAmount().compareTo(ZERO) > 0);
	}

	private BigDecimal remainingStatementAmount(Long runId, Long statementLineId) {
		ensureStatementInRun(runId, statementLineId);
		BigDecimal original = this.jdbcTemplate.queryForObject(
				"select amount from fin_bank_statement_line where id = ?", BigDecimal.class, statementLineId);
		BigDecimal classified = queryAmount("""
				select coalesce(sum(amount), 0)
				from fin_bank_reconciliation_exception
				where run_id = ?
				and statement_line_id = ?
				and status = 'OPEN'
				""", runId, statementLineId);
		return FinancialCloseSupport.amount(FinancialCloseSupport.amount(original)
			.subtract(matchedStatementAmount(runId, statementLineId))
			.subtract(classified));
	}

	private BigDecimal remainingLedgerAmount(Long runId, Long ledgerEntryId) {
		ensureLedgerInRun(runId, ledgerEntryId);
		BigDecimal original = this.jdbcTemplate.queryForObject("""
				select case when debit_amount > 0 then debit_amount else credit_amount end
				from gl_ledger_entry
				where id = ?
				""", BigDecimal.class, ledgerEntryId);
		BigDecimal classified = queryAmount("""
				select coalesce(sum(amount), 0)
				from fin_bank_reconciliation_exception
				where run_id = ?
				and ledger_entry_id = ?
				and status = 'OPEN'
				""", runId, ledgerEntryId);
		return FinancialCloseSupport.amount(FinancialCloseSupport.amount(original)
			.subtract(matchedLedgerAmount(runId, ledgerEntryId))
			.subtract(classified));
	}

	private void ensureStatementInRun(Long runId, Long statementLineId) {
		RunRow row = run(runId);
		Period period = period(row.periodId());
		Boolean exists = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from fin_bank_statement_line
					where id = ?
					and bank_account_id = ?
					and posting_date between ? and ?
					and status <> 'IGNORED'
				)
				""", Boolean.class, statementLineId, row.bankAccountId(), period.startDate(), period.endDate());
		if (!Boolean.TRUE.equals(exists)) {
			throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
		}
	}

	private void ensureLedgerInRun(Long runId, Long ledgerEntryId) {
		RunRow row = run(runId);
		AccountSnapshot account = bankAccount(row.bankAccountId());
		Boolean exists = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from gl_ledger_entry
					where id = ?
					and period_id = ?
					and account_id = ?
				)
				""", Boolean.class, ledgerEntryId, row.periodId(), account.glAccountId());
		if (!Boolean.TRUE.equals(exists)) {
			throw new BusinessException(ApiErrorCode.FIN_BANK_MATCH_AMOUNT_INVALID);
		}
	}

	private BigDecimal matchedStatementAmount(Long runId, Long statementLineId) {
		return queryAmount("""
				select coalesce(sum(match_amount), 0)
				from fin_bank_reconciliation_match
				where run_id = ?
				and statement_line_id = ?
				""", runId, statementLineId);
	}

	private BigDecimal matchedLedgerAmount(Long runId, Long ledgerEntryId) {
		return queryAmount("""
				select coalesce(sum(match_amount), 0)
				from fin_bank_reconciliation_match
				where run_id = ?
				and ledger_entry_id = ?
				""", runId, ledgerEntryId);
	}

	private void updateStatementStatuses(RunRow row) {
		Period period = period(row.periodId());
		this.jdbcTemplate.update("""
				update fin_bank_statement_line l
				set status = case
						when coalesce(m.matched_amount, 0) <= 0 then 'UNMATCHED'
						when coalesce(m.matched_amount, 0) < l.amount then 'PARTIALLY_MATCHED'
						else 'MATCHED'
					end,
				    updated_at = now()
				from (
					select statement_line_id, sum(match_amount) as matched_amount
					from fin_bank_reconciliation_match
					where run_id = ?
					group by statement_line_id
				) m
				where l.id = m.statement_line_id
				and l.bank_account_id = ?
				and l.posting_date between ? and ?
				""", row.id(), row.bankAccountId(), period.startDate(), period.endDate());
		this.jdbcTemplate.update("""
				update fin_bank_statement_line l
				set status = 'UNMATCHED', updated_at = now()
				where l.bank_account_id = ?
				and l.posting_date between ? and ?
				and l.status <> 'IGNORED'
				and not exists (
					select 1 from fin_bank_reconciliation_match m
					where m.run_id = ? and m.statement_line_id = l.id
				)
				""", row.bankAccountId(), period.startDate(), period.endDate(), row.id());
	}

	private void touchRun(RunRow row, String status, CurrentUser operator) {
		int updated = this.jdbcTemplate.update("""
				update fin_bank_reconciliation_run
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				and version = ?
				and status <> 'CONFIRMED'
				""", status, operator.username(), OffsetDateTime.now(), row.id(), row.version());
		if (updated != 1) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	private AccountRow lockAccount(Long id) {
		if (id == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return this.jdbcTemplate.query("""
				select id, status, version
				from fin_bank_account
				where id = ?
				for update
				""", (rs, rowNum) -> new AccountRow(rs.getLong("id"), rs.getString("status"),
				rs.getLong("version")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private void requireEnabledAccount(Long id) {
		if (id == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String status = this.jdbcTemplate.query("""
				select status
				from fin_bank_account
				where id = ?
				""", (rs, rowNum) -> rs.getString("status"), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	private StatementLineState lockStatementLine(Long id) {
		if (id == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return this.jdbcTemplate.query("""
				select id, status, version
				from fin_bank_statement_line
				where id = ?
				for update
				""", (rs, rowNum) -> new StatementLineState(rs.getLong("id"), rs.getString("status"),
				rs.getLong("version")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private String sourceFingerprint(Long periodId, Long bankAccountId) {
		String source = this.jdbcTemplate.queryForObject("""
				select coalesce(string_agg(source_key, ',' order by source_key), '')
				from (
					select 'B|' || l.id || '|' || l.version || '|' || l.posting_date || '|' || l.direction || '|' || l.amount as source_key
					from fin_bank_statement_line l
					join gl_accounting_period p on p.id = ?
					where l.bank_account_id = ?
					and l.posting_date between p.start_date and p.end_date
					and l.status <> 'IGNORED'
					union all
					select 'G|' || e.id || '|' || e.voucher_date || '|' || e.debit_amount || '|' || e.credit_amount as source_key
					from gl_ledger_entry e
					join fin_bank_account a on a.id = ?
					where e.period_id = ?
					and e.account_id = a.gl_account_id
				) source
				""", String.class, periodId, bankAccountId, bankAccountId, periodId);
		return FinancialCloseSupport.sha256("BANK_RECON|" + periodId + "|" + bankAccountId + "|" + source);
	}

	private String statementDedupe(FinancialCloseModels.BankStatementRequest request, String direction,
			BigDecimal amount) {
		return statementDedupe(request.bankAccountId(), request.transactionDate(), request.postingDate(), direction,
				amount, request.bankTransactionId(), request.referenceNo());
	}

	private String statementDedupe(Long bankAccountId, LocalDate transactionDate, LocalDate postingDate,
			String direction, BigDecimal amount, String bankTransactionId, String referenceNo) {
		String explicit = FinancialCloseSupport.text(bankTransactionId);
		if (explicit != null) {
			return FinancialCloseSupport.sha256("BANK_LINE|" + bankAccountId + "|" + explicit);
		}
		return FinancialCloseSupport.sha256("BANK_LINE|" + bankAccountId + "|" + transactionDate
				+ "|" + postingDate + "|" + direction + "|" + FinancialCloseSupport.decimal(amount) + "|"
				+ FinancialCloseSupport.text(referenceNo));
	}

	private BigDecimal matchedStatementAmountAcrossRuns(Long statementLineId) {
		return queryAmount("""
				select coalesce(sum(match_amount), 0)
				from fin_bank_reconciliation_match
				where statement_line_id = ?
				""", statementLineId);
	}

	private AccountNumberParts accountNumberParts(String accountNo) {
		String normalizedAccountNo = normalizeAccountNo(accountNo);
		String fingerprint = FinancialCloseSupport.sha256("BANK_ACCOUNT|" + normalizedAccountNo);
		String last4 = normalizedAccountNo.length() <= 4 ? normalizedAccountNo
				: normalizedAccountNo.substring(normalizedAccountNo.length() - 4);
		return new AccountNumberParts(fingerprint, last4, "****" + last4);
	}

	private String normalizeAccountNo(String accountNo) {
		String normalized = FinancialCloseSupport.requiredText(accountNo, ApiErrorCode.VALIDATION_ERROR)
			.replaceAll("[^0-9A-Za-z]", "")
			.toUpperCase();
		if (normalized.length() < 4) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return normalized;
	}

	private Period period(Long id) {
		return this.jdbcTemplate.query("""
				select id, period_code, start_date, end_date
				from gl_accounting_period
				where id = ?
				""", (rs, rowNum) -> new Period(rs.getLong("id"), rs.getString("period_code"),
				rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)), id).stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_PERIOD_NOT_FOUND));
	}

	private AccountSnapshot glAccount(Long id) {
		return this.jdbcTemplate.query("""
				select id, code, category, postable, enabled
				from gl_account
				where id = ?
				""", (rs, rowNum) -> new AccountSnapshot(rs.getLong("id"), rs.getString("code"),
				rs.getString("category"), rs.getBoolean("postable"), rs.getBoolean("enabled"), null), id).stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_ACCOUNT_NOT_FOUND));
	}

	private AccountSnapshot bankAccount(Long id) {
		return this.jdbcTemplate.query("""
				select a.id, g.code, g.category, g.postable, g.enabled, a.gl_account_id
				from fin_bank_account a
				join gl_account g on g.id = a.gl_account_id
				where a.id = ?
				""", (rs, rowNum) -> new AccountSnapshot(rs.getLong("id"), rs.getString("code"),
				rs.getString("category"), rs.getBoolean("postable"), rs.getBoolean("enabled"),
				rs.getLong("gl_account_id")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private BigDecimal queryAmount(String sql, Object... args) {
		return FinancialCloseSupport.amount(this.jdbcTemplate.queryForObject(sql, BigDecimal.class, args));
	}

	private void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	private void requireMutable(RunRow row) {
		if ("CONFIRMED".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	private record AccountSnapshot(Long id, String code, String category, boolean postable, boolean enabled,
			Long glAccountId) {
	}

	private record AccountRow(Long id, String status, Long version) {
	}

	private record AccountNumberParts(String fingerprint, String last4, String masked) {
	}

	private record Period(Long id, String periodCode, LocalDate startDate, LocalDate endDate) {
	}

	private record RunRow(Long id, Long periodId, Long bankAccountId, String status, BigDecimal statementBalance,
			BigDecimal ledgerBalance, BigDecimal differenceAmount, String sourceFingerprint, String confirmedBy,
			OffsetDateTime confirmedAt, Long version) {
	}

	private record StatementSide(Long id, String direction, BigDecimal amount) {
	}

	private record LedgerSide(Long id, BigDecimal debitAmount, BigDecimal creditAmount) {
	}

	private record ResolvedMatch(Long statementLineId, Long ledgerEntryId, BigDecimal amount) {
	}

	private record StatementLineState(Long id, String status, Long version) {
	}

	private record ParsedImport(String importFingerprint, List<ImportRow> rows, int rowCount, int validCount,
			int duplicateCount, int errorCount) {
	}

	private record ImportRow(int rowNo, String status, LocalDate transactionDate, LocalDate postingDate,
			String direction, BigDecimal amount, String counterpartyName, String summary, String bankTransactionId,
			String referenceNo, String dedupeFingerprint, Long existingLineId, List<String> errors) {
	}

	private record ReconciliationMetrics(BigDecimal bankEndingBalance, BigDecimal glEndingBalance,
			BigDecimal adjustedBankBalance, BigDecimal adjustedBookBalance, BigDecimal difference,
			int unclassifiedCount, BigDecimal matchedAmount) {
	}

}
