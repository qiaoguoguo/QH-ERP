package com.qherp.api.system.gl;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GeneralLedgerQueryService {

	private final JdbcTemplate jdbcTemplate;

	public GeneralLedgerQueryService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> vouchers(String status, String keyword, int page, int pageSize,
			CurrentUser user) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.limit(pageSize);
		List<Object> args = new ArrayList<>();
		String where = "where true";
		if (status != null && !status.isBlank()) {
			where += " and v.status = ?";
			args.add(status.trim().toUpperCase());
		}
		if (keyword != null && !keyword.isBlank()) {
			where += " and (v.draft_no ilike ? or v.voucher_no ilike ? or v.summary ilike ? or v.source_no ilike ?)";
			String like = "%" + keyword.trim() + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_voucher v
				%s
				""".formatted(where), Long.class, args.toArray());
		args.add(safeSize);
		args.add(GeneralLedgerSupport.offset(safePage, safeSize));
		List<Long> ids = this.jdbcTemplate.query("""
				select v.id
				from gl_voucher v
				%s
				order by v.voucher_date desc, v.id desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> rs.getLong("id"), args.toArray());
		List<Map<String, Object>> items = ids.stream().map((id) -> voucher(id, user, false)).toList();
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> voucher(Long id, CurrentUser user) {
		return voucher(id, user, true);
	}

	Map<String, Object> voucher(Long id, CurrentUser user, boolean includeLines) {
		return this.jdbcTemplate.query("""
				select v.id, v.ledger_id, v.accounting_period_id, p.period_code, v.draft_no, v.voucher_type,
				       v.voucher_date, v.status, v.summary, v.source_type, v.source_id, v.source_no,
				       v.source_fingerprint, v.source_version, v.source_original_type, v.source_original_id,
				       v.source_original_no, v.source_original_version, v.source_original_fingerprint,
				       v.currency, v.debit_total, v.credit_total, v.voucher_word, v.voucher_number,
				       v.voucher_no, v.approval_instance_id,
				       v.reversal_original_voucher_id, v.reversal_reason, v.created_by, v.created_at,
				       v.updated_by, v.updated_at, v.submitted_by, v.submitted_at, v.posted_by, v.posted_at,
				       v.cancelled_by, v.cancelled_at, v.version
				from gl_voucher v
				join gl_accounting_period p on p.id = v.accounting_period_id
				where v.id = ?
				""", (rs, rowNum) -> {
			boolean amountVisible = GeneralLedgerSupport.hasPermission(user, "gl:amount:view");
			boolean sourceVisible = GeneralLedgerSupport.hasPermission(user, "gl:source:view");
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("id", rs.getLong("id"));
			item.put("draftNo", rs.getString("draft_no"));
			item.put("voucherType", rs.getString("voucher_type"));
			item.put("voucherNo", rs.getString("voucher_no"));
			item.put("voucherWord", rs.getString("voucher_word"));
			item.put("voucherNumber", GeneralLedgerSupport.nullableLong(rs, "voucher_number"));
			item.put("voucherDate", rs.getObject("voucher_date", LocalDate.class));
			item.put("accountingPeriodId", rs.getLong("accounting_period_id"));
			item.put("accountingPeriodCode", rs.getString("period_code"));
			item.put("status", rs.getString("status"));
			item.put("summary", rs.getString("summary"));
			item.put("sourceVisible", sourceVisible);
			item.put("sourceType", sourceVisible ? rs.getString("source_type") : null);
			item.put("sourceId", sourceVisible ? GeneralLedgerSupport.nullableLong(rs, "source_id") : null);
			item.put("sourceNo", sourceVisible ? rs.getString("source_no") : null);
			item.put("sourceOriginalType", sourceVisible ? rs.getString("source_original_type") : null);
			item.put("sourceOriginalId",
					sourceVisible ? GeneralLedgerSupport.nullableLong(rs, "source_original_id") : null);
			item.put("sourceOriginalNo", sourceVisible ? rs.getString("source_original_no") : null);
			item.put("sourceOriginalVersion",
					sourceVisible ? GeneralLedgerSupport.nullableLong(rs, "source_original_version") : null);
			item.put("sourceOriginalFingerprint", sourceVisible ? rs.getString("source_original_fingerprint") : null);
			item.put("currency", rs.getString("currency"));
			item.put("amountVisible", amountVisible);
			item.put("debitTotal", amountVisible ? GeneralLedgerSupport.decimal(rs.getBigDecimal("debit_total")) : null);
			item.put("creditTotal", amountVisible ? GeneralLedgerSupport.decimal(rs.getBigDecimal("credit_total")) : null);
			item.put("approvalSummary", approvalSummary(GeneralLedgerSupport.nullableLong(rs, "approval_instance_id")));
			item.put("reversalSummary", reversalSummary(id));
			item.put("auditSummary", auditSummary(id));
			item.put("createdBy", rs.getString("created_by"));
			item.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			item.put("updatedBy", rs.getString("updated_by"));
			item.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
			item.put("submittedBy", rs.getString("submitted_by"));
			item.put("submittedAt", rs.getObject("submitted_at", OffsetDateTime.class));
			item.put("postedBy", rs.getString("posted_by"));
			item.put("postedAt", rs.getObject("posted_at", OffsetDateTime.class));
			item.put("cancelledBy", rs.getString("cancelled_by"));
			item.put("cancelledAt", rs.getObject("cancelled_at", OffsetDateTime.class));
			item.put("version", rs.getLong("version"));
			item.put("allowedActions", allowedActions(rs.getString("status"), user));
			item.put("actionDisabledReasons", actionDisabledReasons(rs.getString("status")));
			if (includeLines) {
				item.put("lines", voucherLines(id, amountVisible, sourceVisible));
			}
			return item;
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.GL_VOUCHER_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> generalLedger(String periodCode, int page, int pageSize,
			CurrentUser user) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.limit(pageSize);
		PeriodRow period = period(periodCode);
		boolean amountVisible = GeneralLedgerSupport.hasPermission(user, "gl:amount:view");
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_account_period_total t
				where t.period_id = ?
				""", Long.class, period.id());
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select a.id as account_id, a.code, a.name, t.opening_debit, t.opening_credit, t.period_debit,
				       t.period_credit, t.ending_debit, t.ending_credit
				from gl_account_period_total t
				join gl_account a on a.id = t.account_id
				where t.period_id = ?
				order by a.code
				limit ? offset ?
				""", (rs, rowNum) -> amountMap(rs.getLong("account_id"), rs.getString("code"), rs.getString("name"),
				amountVisible, rs.getBigDecimal("opening_debit"), rs.getBigDecimal("opening_credit"),
				rs.getBigDecimal("period_debit"), rs.getBigDecimal("period_credit"),
				rs.getBigDecimal("ending_debit"), rs.getBigDecimal("ending_credit")), period.id(), safeSize,
				GeneralLedgerSupport.offset(safePage, safeSize));
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> detailLedger(String periodCode, String voucherNo, int page, int pageSize,
			CurrentUser user) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.limit(pageSize);
		PeriodRow period = period(periodCode);
		boolean amountVisible = GeneralLedgerSupport.hasPermission(user, "gl:amount:view");
		List<Object> args = new ArrayList<>();
		args.add(period.id());
		String where = "where e.period_id = ?";
		if (voucherNo != null && !voucherNo.isBlank()) {
			where += " and e.voucher_no = ?";
			args.add(voucherNo.trim());
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_ledger_entry e
				%s
				""".formatted(where), Long.class, args.toArray());
		args.add(safeSize);
		args.add(GeneralLedgerSupport.offset(safePage, safeSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select e.id, e.voucher_id, e.voucher_line_id, e.voucher_no, e.voucher_date, e.line_no, e.summary,
				       e.account_id, e.account_code, e.account_name, e.debit_amount, e.credit_amount,
				       e.source_type, e.source_id, e.source_no
				from gl_ledger_entry e
				%s
				order by e.voucher_date, e.voucher_number, e.line_no
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("id", rs.getLong("id"));
			item.put("voucherId", rs.getLong("voucher_id"));
			item.put("voucherLineId", rs.getLong("voucher_line_id"));
			item.put("voucherNo", rs.getString("voucher_no"));
			item.put("voucherDate", rs.getObject("voucher_date", LocalDate.class));
			item.put("lineNo", rs.getInt("line_no"));
			item.put("summary", rs.getString("summary"));
			item.put("accountId", rs.getLong("account_id"));
			item.put("accountCode", rs.getString("account_code"));
			item.put("accountName", rs.getString("account_name"));
			item.put("amountVisible", amountVisible);
			item.put("debitAmount", amountVisible ? GeneralLedgerSupport.decimal(rs.getBigDecimal("debit_amount")) : null);
			item.put("creditAmount", amountVisible ? GeneralLedgerSupport.decimal(rs.getBigDecimal("credit_amount")) : null);
			item.put("restricted", !amountVisible);
			return item;
		}, args.toArray());
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> accountBalances(String periodCode, int page, int pageSize,
			CurrentUser user) {
		return generalLedger(periodCode, page, pageSize, user);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> trialBalance(String periodCode, CurrentUser user) {
		PeriodRow period = period(periodCode);
		BalanceGroup opening = aggregateLedger(period.id(), true);
		BalanceGroup current = aggregateLedger(period.id(), false);
		BalanceGroup ending = new BalanceGroup(opening.debit().add(current.debit()),
				opening.credit().add(current.credit()));
		BalanceGroup openingCache = aggregateCache(period.id(), "opening");
		BalanceGroup periodCache = aggregateCache(period.id(), "period");
		BalanceGroup endingCache = aggregateCache(period.id(), "ending");
		if (!opening.equals(openingCache) || !current.equals(periodCache) || !ending.equals(endingCache)) {
			throw new BusinessException(ApiErrorCode.GL_TRIAL_BALANCE_MISMATCH);
		}
		boolean amountVisible = GeneralLedgerSupport.hasPermission(user, "gl:amount:view");
		Map<String, Object> result = GeneralLedgerSupport.map();
		result.put("periodCode", period.periodCode());
		result.put("amountVisible", amountVisible);
		result.put("opening", trialGroup(opening, amountVisible));
		result.put("period", trialGroup(current, amountVisible));
		result.put("ending", trialGroup(ending, amountVisible));
		result.put("balanced", opening.balanced() && current.balanced() && ending.balanced());
		result.put("periodDebit", amountVisible ? GeneralLedgerSupport.decimal(current.debit()) : null);
		result.put("periodCredit", amountVisible ? GeneralLedgerSupport.decimal(current.credit()) : null);
		return result;
	}

	private List<Map<String, Object>> voucherLines(Long voucherId, boolean amountVisible, boolean sourceVisible) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.summary, l.account_id, l.account_code, l.account_name,
				       l.account_category, l.account_balance_direction, l.debit_amount, l.credit_amount,
				       l.normalized_fact_code, l.source_type, l.source_id, l.source_no
				from gl_voucher_line l
				where l.voucher_id = ?
				order by l.line_no
				""", (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			Long lineId = rs.getLong("id");
			item.put("id", lineId);
			item.put("lineNo", rs.getInt("line_no"));
			item.put("summary", rs.getString("summary"));
			item.put("accountId", rs.getLong("account_id"));
			item.put("accountCode", rs.getString("account_code"));
			item.put("accountName", rs.getString("account_name"));
			item.put("accountCategory", rs.getString("account_category"));
			item.put("accountBalanceDirection", rs.getString("account_balance_direction"));
			item.put("amountVisible", amountVisible);
			item.put("debitAmount", amountVisible ? GeneralLedgerSupport.decimal(rs.getBigDecimal("debit_amount")) : null);
			item.put("creditAmount", amountVisible ? GeneralLedgerSupport.decimal(rs.getBigDecimal("credit_amount")) : null);
			item.put("normalizedFactCode", rs.getString("normalized_fact_code"));
			item.put("sourceVisible", sourceVisible);
			item.put("sourceType", sourceVisible ? rs.getString("source_type") : null);
			item.put("sourceId", sourceVisible ? GeneralLedgerSupport.nullableLong(rs, "source_id") : null);
			item.put("sourceNo", sourceVisible ? rs.getString("source_no") : null);
			item.put("sourceRoute", sourceVisible && rs.getString("source_type") != null
					? Map.of("sourceType", rs.getString("source_type"), "sourceId",
							GeneralLedgerSupport.nullableLong(rs, "source_id"))
					: null);
			item.put("auxiliaryItems", auxiliaries(lineId, sourceVisible));
			return item;
		}, voucherId);
	}

	private List<Map<String, Object>> auxiliaries(Long lineId, boolean sourceVisible) {
		return this.jdbcTemplate.query("""
				select dimension_code, dimension_name, object_type, object_id, object_code, object_name, aux_item_id
				from gl_voucher_line_auxiliary
				where voucher_line_id = ?
				order by dimension_code
				""", (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("dimensionCode", rs.getString("dimension_code"));
			item.put("dimensionName", rs.getString("dimension_name"));
			item.put("objectType", rs.getString("object_type"));
			item.put("objectId", sourceVisible ? GeneralLedgerSupport.nullableLong(rs, "object_id") : null);
			item.put("objectCode", sourceVisible ? rs.getString("object_code") : null);
			item.put("objectName", sourceVisible ? rs.getString("object_name") : null);
			item.put("auxItemId", sourceVisible ? GeneralLedgerSupport.nullableLong(rs, "aux_item_id") : null);
			return item;
		}, lineId);
	}

	private Map<String, Object> approvalSummary(Long instanceId) {
		if (instanceId == null) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select id, scene_code, status, version
				from platform_approval_instance
				where id = ?
				""", (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("id", rs.getLong("id"));
			item.put("sceneCode", rs.getString("scene_code"));
			item.put("status", rs.getString("status"));
			item.put("version", rs.getLong("version"));
			return item;
		}, instanceId).stream().findFirst().orElse(null);
	}

	private Map<String, Object> reversalSummary(Long voucherId) {
		return this.jdbcTemplate.query("""
				select original_voucher_id, reversal_voucher_id, status, reason
				from gl_voucher_reversal_link
				where original_voucher_id = ? or reversal_voucher_id = ?
				order by id desc
				limit 1
				""", (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("originalVoucherId", rs.getLong("original_voucher_id"));
			item.put("reversalVoucherId", rs.getLong("reversal_voucher_id"));
			item.put("status", rs.getString("status"));
			item.put("reason", rs.getString("reason"));
			return item;
		}, voucherId, voucherId).stream().findFirst().orElse(null);
	}

	private Map<String, Object> auditSummary(Long voucherId) {
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_audit_event
				where resource_type = 'GL_VOUCHER'
				and resource_id = ?
				""", Long.class, Long.toString(voucherId));
		Map<String, Object> item = GeneralLedgerSupport.map();
		item.put("eventCount", total == null ? 0 : total);
		return item;
	}

	private List<Map<String, Object>> allowedActions(String status, CurrentUser user) {
		List<Map<String, Object>> actions = new ArrayList<>();
		if ("DRAFT".equals(status)) {
			if (GeneralLedgerSupport.hasPermission(user, "gl:voucher:update")) {
				actions.add(action("UPDATE", "维护"));
			}
			if (GeneralLedgerSupport.hasPermission(user, "gl:voucher:submit")) {
				actions.add(action("SUBMIT", "提交"));
			}
			if (GeneralLedgerSupport.hasPermission(user, "gl:voucher:cancel")) {
				actions.add(action("CANCEL", "取消"));
			}
		}
		if ("POSTED".equals(status) && GeneralLedgerSupport.hasPermission(user, "gl:voucher:reverse")) {
			actions.add(action("REVERSE", "冲销"));
		}
		return actions;
	}

	private Map<String, Object> action(String code, String label) {
		Map<String, Object> item = GeneralLedgerSupport.map();
		item.put("code", code);
		item.put("label", label);
		item.put("enabled", true);
		return item;
	}

	private Map<String, Object> actionDisabledReasons(String status) {
		if ("POSTED".equals(status)) {
			return Map.of("UPDATE", "已记账凭证不可修改", "SUBMIT", "已记账凭证不可重复提交");
		}
		if ("SUBMITTED".equals(status)) {
			return Map.of("UPDATE", "审批中凭证不可修改", "CANCEL", "审批中凭证不可取消");
		}
		return Map.of();
	}

	private Map<String, Object> amountMap(Long accountId, String code, String name, boolean amountVisible,
			BigDecimal openingDebit, BigDecimal openingCredit, BigDecimal periodDebit, BigDecimal periodCredit,
			BigDecimal endingDebit, BigDecimal endingCredit) {
		Map<String, Object> item = GeneralLedgerSupport.map();
		item.put("accountId", accountId);
		item.put("accountCode", code);
		item.put("accountName", name);
		item.put("amountVisible", amountVisible);
		item.put("restricted", !amountVisible);
		item.put("openingDebit", amountVisible ? GeneralLedgerSupport.decimal(openingDebit) : null);
		item.put("openingCredit", amountVisible ? GeneralLedgerSupport.decimal(openingCredit) : null);
		item.put("periodDebit", amountVisible ? GeneralLedgerSupport.decimal(periodDebit) : null);
		item.put("periodCredit", amountVisible ? GeneralLedgerSupport.decimal(periodCredit) : null);
		item.put("endingDebit", amountVisible ? GeneralLedgerSupport.decimal(endingDebit) : null);
		item.put("endingCredit", amountVisible ? GeneralLedgerSupport.decimal(endingCredit) : null);
		return item;
	}

	private Map<String, Object> trialGroup(BalanceGroup group, boolean amountVisible) {
		Map<String, Object> item = GeneralLedgerSupport.map();
		item.put("balanced", group.balanced());
		item.put("debitTotal", amountVisible ? GeneralLedgerSupport.decimal(group.debit()) : null);
		item.put("creditTotal", amountVisible ? GeneralLedgerSupport.decimal(group.credit()) : null);
		item.put("differenceAmount", amountVisible ? GeneralLedgerSupport.decimal(group.debit().subtract(group.credit()).abs()) : null);
		item.put("restricted", !amountVisible);
		return item;
	}

	private BalanceGroup aggregateLedger(Long periodId, boolean opening) {
		return this.jdbcTemplate.query("""
				select coalesce(sum(debit_amount), 0) as debit, coalesce(sum(credit_amount), 0) as credit
				from gl_ledger_entry
				where period_id = ?
				and voucher_type %s
				""".formatted(opening ? "= 'OPENING'" : "<> 'OPENING'"), (rs) -> {
			if (rs.next()) {
				return new BalanceGroup(rs.getBigDecimal("debit"), rs.getBigDecimal("credit"));
			}
			return BalanceGroup.ZERO;
		}, periodId);
	}

	private BalanceGroup aggregateCache(Long periodId, String type) {
		String debitColumn = switch (type) {
			case "opening" -> "opening_debit";
			case "period" -> "period_debit";
			case "ending" -> "ending_debit";
			default -> throw new IllegalArgumentException(type);
		};
		String creditColumn = switch (type) {
			case "opening" -> "opening_credit";
			case "period" -> "period_credit";
			case "ending" -> "ending_credit";
			default -> throw new IllegalArgumentException(type);
		};
		return this.jdbcTemplate.query("""
				select coalesce(sum(%s), 0) as debit, coalesce(sum(%s), 0) as credit
				from gl_account_period_total
				where period_id = ?
				""".formatted(debitColumn, creditColumn), (rs) -> {
			if (rs.next()) {
				return new BalanceGroup(rs.getBigDecimal("debit"), rs.getBigDecimal("credit"));
			}
			return BalanceGroup.ZERO;
		}, periodId);
	}

	private PeriodRow period(String periodCode) {
		return this.jdbcTemplate.query("""
				select id, period_code
				from gl_accounting_period
				where period_code = ?
				""", (rs, rowNum) -> new PeriodRow(rs.getLong("id"), rs.getString("period_code")),
				GeneralLedgerSupport.requiredText(periodCode, ApiErrorCode.GL_PERIOD_NOT_FOUND)).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_PERIOD_NOT_FOUND));
	}

	record PeriodRow(Long id, String periodCode) {
	}

	record BalanceGroup(BigDecimal debit, BigDecimal credit) {

		static final BalanceGroup ZERO = new BalanceGroup(BigDecimal.ZERO, BigDecimal.ZERO);

		BalanceGroup {
			debit = GeneralLedgerSupport.amount(debit);
			credit = GeneralLedgerSupport.amount(credit);
		}

		boolean balanced() {
			return debit.compareTo(credit) == 0;
		}

	}

}
