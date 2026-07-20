package com.qherp.api.system.financialclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.gl.GeneralLedgerVoucherService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class ProfitLossTransferService {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final FinancialCloseCheckService checkService;

	private final GeneralLedgerVoucherService voucherService;

	private final FinancialCloseAuditService auditService;

	public ProfitLossTransferService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
			FinancialCloseCheckService checkService, GeneralLedgerVoucherService voucherService,
			FinancialCloseAuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.checkService = checkService;
		this.voucherService = voucherService;
		this.auditService = auditService;
	}

	@Transactional
	public Map<String, Object> preview(Long periodId, FinancialCloseModels.CheckRequest request, CurrentUser operator) {
		Map<String, Object> result = previewMap(buildPreview(periodId), null, 0L, operator);
		this.auditService.success(operator, "FIN_PROFIT_LOSS_PREVIEW", "FIN_CLOSE_PROFIT_LOSS_TRANSFER", periodId);
		return result;
	}

	@Transactional
	public Map<String, Object> generate(Long periodId, FinancialCloseModels.ProfitLossGenerateRequest request,
			CurrentUser operator) {
		TransferPreview preview = buildPreview(periodId);
		String requestedFingerprint = FinancialCloseSupport.text(request == null ? null : request.sourceFingerprint());
		if (requestedFingerprint != null && !preview.sourceFingerprint().equals(requestedFingerprint)) {
			throw new BusinessException(ApiErrorCode.FIN_PROFIT_LOSS_STALE);
		}
		String key = FinancialCloseSupport.requiredText(request == null ? null : request.idempotencyKey(),
				ApiErrorCode.FIN_CLOSE_CONFLICT);
		String reason = FinancialCloseSupport.text(request == null ? null : request.reason());
		if (reason == null || reason.isBlank()) {
			reason = "生成期末损益结转草稿";
		}
		String requestFingerprint = FinancialCloseSupport.sha256("PL_GENERATE|" + periodId + "|"
				+ preview.sourceFingerprint() + "|" + reason);
		Map<String, Object> existing = existingTransfer(periodId, key, requestFingerprint, operator);
		if (existing != null) {
			this.auditService.success(operator, "FIN_PROFIT_LOSS_GENERATE_REPLAY",
					"FIN_CLOSE_PROFIT_LOSS_TRANSFER", ((Number) existing.get("id")).longValue());
			return existing;
		}
		if (preview.lines().isEmpty()) {
			Long transferId = this.jdbcTemplate.queryForObject("""
					insert into fin_close_profit_loss_transfer (
						ledger_id, period_id, status, source_fingerprint, debit_total, credit_total, line_json,
						reason, idempotency_key, request_fingerprint, created_by, updated_by
					)
					values (?, ?, 'ZERO_BALANCE', ?, 0, 0, '[]'::jsonb, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, preview.period().ledgerId(), periodId, preview.sourceFingerprint(), reason, key,
					requestFingerprint, operator.username(), operator.username());
			this.auditService.success(operator, "FIN_PROFIT_LOSS_GENERATE_ZERO",
					"FIN_CLOSE_PROFIT_LOSS_TRANSFER", transferId);
			return transfer(transferId, List.of(), operator);
		}
		Long transferId = this.jdbcTemplate.queryForObject("""
				insert into fin_close_profit_loss_transfer (
					ledger_id, period_id, status, source_fingerprint, debit_total, credit_total, line_json, reason,
					idempotency_key, request_fingerprint, created_by, updated_by
				)
				values (?, ?, 'DRAFT', ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, preview.period().ledgerId(), periodId, preview.sourceFingerprint(),
				preview.debitTotal(), preview.creditTotal(), json(previewLineMaps(preview.lines())), reason, key,
				requestFingerprint, operator.username(), operator.username());
		String sourceNo = "PL-" + preview.period().periodCode() + "-" + transferId;
		Map<String, Object> voucher = this.voucherService.createSystemDraft("PROFIT_LOSS_CARRYFORWARD", transferId,
				sourceNo, preview.sourceFingerprint(), 0L, preview.period().endDate(),
				preview.period().periodCode() + " 期末损益结转", toVoucherLines(preview.lines()), key, operator);
		Long voucherId = ((Number) voucher.get("id")).longValue();
		this.jdbcTemplate.update("""
				update fin_close_profit_loss_transfer
				set voucher_id = ?, voucher_status = ?, updated_at = now(), version = version + 1
				where id = ?
				""", voucherId, String.valueOf(voucher.get("status")), transferId);
		this.auditService.success(operator, "FIN_PROFIT_LOSS_GENERATE", "FIN_CLOSE_PROFIT_LOSS_TRANSFER",
				transferId);
		return transfer(transferId, preview.lines(), operator);
	}

	private Map<String, Object> existingTransfer(Long periodId, String key, String requestFingerprint,
			CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select id, request_fingerprint
				from fin_close_profit_loss_transfer
				where period_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> {
			if (!requestFingerprint.equals(rs.getString("request_fingerprint"))) {
				throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
			}
			return transfer(rs.getLong("id"), null, currentUser);
		}, periodId, key).stream().findFirst().orElse(null);
	}

	private TransferPreview buildPreview(Long periodId) {
		FinancialCloseCheckService.PeriodRow period = this.checkService.period(periodId);
		Long retainedEarningsAccountId = accountId("4103");
		List<TransferLine> lines = new ArrayList<>(this.jdbcTemplate.query("""
				select a.id, a.code, a.name, coalesce(sum(e.debit_amount), 0) as debit_amount,
				       coalesce(sum(e.credit_amount), 0) as credit_amount
				from gl_ledger_entry e
				join gl_account a on a.id = e.account_id
				where e.period_id = ?
				and a.category = 'PROFIT_LOSS'
				group by a.id, a.code, a.name
				having coalesce(sum(e.debit_amount), 0) <> coalesce(sum(e.credit_amount), 0)
				order by a.code
				""", (rs, rowNum) -> {
			BigDecimal debit = FinancialCloseSupport.amount(rs.getBigDecimal("debit_amount"));
			BigDecimal credit = FinancialCloseSupport.amount(rs.getBigDecimal("credit_amount"));
			BigDecimal net = debit.subtract(credit);
			BigDecimal lineDebit = net.compareTo(BigDecimal.ZERO) < 0 ? net.abs() : BigDecimal.ZERO;
			BigDecimal lineCredit = net.compareTo(BigDecimal.ZERO) > 0 ? net : BigDecimal.ZERO;
			return new TransferLine(rs.getLong("id"), rs.getString("code"), rs.getString("name"), lineDebit,
					lineCredit);
		}, periodId));
		BigDecimal debitTotal = lines.stream().map(TransferLine::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal creditTotal = lines.stream()
			.map(TransferLine::creditAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal difference = debitTotal.subtract(creditTotal);
		if (difference.compareTo(BigDecimal.ZERO) > 0) {
			lines.add(new TransferLine(retainedEarningsAccountId, "4103", "本年利润", BigDecimal.ZERO, difference));
			creditTotal = creditTotal.add(difference);
		}
		else if (difference.compareTo(BigDecimal.ZERO) < 0) {
			lines.add(new TransferLine(retainedEarningsAccountId, "4103", "本年利润", difference.abs(), BigDecimal.ZERO));
			debitTotal = debitTotal.add(difference.abs());
		}
		lines = lines.stream().sorted(Comparator.comparing(TransferLine::accountCode)).toList();
		String sourceFingerprint = FinancialCloseSupport.sha256("PL|" + period.id() + "|" + period.periodCode() + "|"
				+ previewLineMaps(lines));
		return new TransferPreview(period, lines, FinancialCloseSupport.amount(debitTotal),
				FinancialCloseSupport.amount(creditTotal), sourceFingerprint);
	}

	private Map<String, Object> transfer(Long id, List<TransferLine> lines, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select id, period_id, status, source_fingerprint, voucher_id, voucher_status, debit_total,
				       credit_total, line_json::text as line_json, version
				from fin_close_profit_loss_transfer
				where id = ?
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
			map.put("lines", lines == null ? List.of() : previewLineMaps(lines, amountVisible));
			map.put("lineJson", amountVisible && sourceVisible ? rs.getString("line_json") : null);
			FinancialCloseSupport.putVisibility(map, currentUser);
			map.put("allowedActions", List.of());
			map.put("actionDisabledReasons", Map.of());
			map.put("version", rs.getLong("version"));
			return map;
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private Map<String, Object> previewMap(TransferPreview preview, Long id, Long version, CurrentUser currentUser) {
		boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
		boolean sourceVisible = FinancialCloseSupport.sourceVisible(currentUser);
		Map<String, Object> map = FinancialCloseSupport.map();
		map.put("id", id);
		map.put("periodId", preview.period().id());
		map.put("periodCode", preview.period().periodCode());
		map.put("status", "PREVIEW");
		map.put("sourceFingerprint", sourceVisible ? preview.sourceFingerprint() : null);
		map.put("debitTotal", FinancialCloseSupport.visibleDecimal(preview.debitTotal(), amountVisible));
		map.put("creditTotal", FinancialCloseSupport.visibleDecimal(preview.creditTotal(), amountVisible));
		map.put("lines", previewLineMaps(preview.lines(), amountVisible));
		FinancialCloseSupport.putVisibility(map, currentUser);
		map.put("allowedActions", List.of());
		map.put("actionDisabledReasons", Map.of());
		map.put("version", version);
		return map;
	}

	private List<Map<String, Object>> previewLineMaps(List<TransferLine> lines) {
		return previewLineMaps(lines, true);
	}

	private List<Map<String, Object>> previewLineMaps(List<TransferLine> lines, boolean amountVisible) {
		List<Map<String, Object>> result = new ArrayList<>();
		int lineNo = 1;
		for (TransferLine line : lines) {
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("lineNo", lineNo++);
			map.put("accountId", line.accountId());
			map.put("accountCode", line.accountCode());
			map.put("accountName", line.accountName());
			map.put("summary", "期末损益结转");
			map.put("debitAmount", FinancialCloseSupport.visibleDecimal(line.debitAmount(), amountVisible));
			map.put("creditAmount", FinancialCloseSupport.visibleDecimal(line.creditAmount(), amountVisible));
			result.add(map);
		}
		return result;
	}

	private List<GeneralLedgerVoucherService.VoucherLineRequest> toVoucherLines(List<TransferLine> lines) {
		List<GeneralLedgerVoucherService.VoucherLineRequest> result = new ArrayList<>();
		int lineNo = 1;
		for (TransferLine line : lines) {
			result.add(new GeneralLedgerVoucherService.VoucherLineRequest(lineNo++, line.accountId(), "期末损益结转",
					line.debitAmount(), line.creditAmount(), List.of()));
		}
		return result;
	}

	private Long accountId(String code) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from gl_account
				where code = ?
				""", Long.class, code);
	}

	private String json(Object value) {
		try {
			return this.objectMapper.writeValueAsString(value == null ? Map.of() : value);
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private record TransferPreview(FinancialCloseCheckService.PeriodRow period, List<TransferLine> lines,
			BigDecimal debitTotal, BigDecimal creditTotal, String sourceFingerprint) {
	}

	private record TransferLine(Long accountId, String accountCode, String accountName, BigDecimal debitAmount,
			BigDecimal creditAmount) {
	}

}
