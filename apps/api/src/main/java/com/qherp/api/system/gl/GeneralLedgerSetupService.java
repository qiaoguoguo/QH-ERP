package com.qherp.api.system.gl;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeneralLedgerSetupService {

	private final JdbcTemplate jdbcTemplate;

	public GeneralLedgerSetupService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> ledger() {
		return ledgerResponse(ledgerRow(false));
	}

	@Transactional
	public Map<String, Object> initialize(LedgerInitializeRequest request, CurrentUser operator) {
		GeneralLedgerSupport.advisoryLock(this.jdbcTemplate, "gl:ledger:initialize");
		LedgerRow ledger = ledgerRow(true);
		String startYearMonth = GeneralLedgerSupport.yearMonth(request.startYearMonth()).toString();
		if (ledger.initialized()) {
			if (startYearMonth.equals(ledger.startYearMonth())) {
				return ledgerResponse(ledger);
			}
			throw new BusinessException(ApiErrorCode.GL_LEDGER_ALREADY_INITIALIZED);
		}
		LocalDate start = GeneralLedgerSupport.periodStart(startYearMonth);
		LocalDate end = GeneralLedgerSupport.periodEnd(startYearMonth);
		Long periodId = this.jdbcTemplate.queryForObject("""
				insert into gl_accounting_period (
					ledger_id, period_code, start_date, end_date, status, created_by, created_at, updated_by,
					updated_at
				)
				values (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?)
				on conflict (ledger_id, period_code) do update set period_code = excluded.period_code
				returning id
				""", Long.class, ledger.id(), startYearMonth, start, end, operator.username(), OffsetDateTime.now(),
				operator.username(), OffsetDateTime.now());
		this.jdbcTemplate.update("""
				update gl_ledger
				set initialized = true, start_period_id = ?, start_year_month = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ? and initialized = false
				""", periodId, startYearMonth, operator.username(), OffsetDateTime.now(), ledger.id());
		return ledgerResponse(ledgerRow(false));
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> periods(String periodCode, int page, int pageSize) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.limit(pageSize);
		List<Object> args = new ArrayList<>();
		String where = "";
		if (periodCode != null && !periodCode.isBlank()) {
			where = "where p.period_code = ?";
			args.add(periodCode.trim());
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_accounting_period p
				%s
				""".formatted(where), Long.class, args.toArray());
		args.add(safeSize);
		args.add(GeneralLedgerSupport.offset(safePage, safeSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select p.id, p.period_code, p.start_date, p.end_date, p.status, p.version,
				       l.code as ledger_code
				from gl_accounting_period p
				join gl_ledger l on l.id = p.ledger_id
				%s
				order by p.period_code desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("id", rs.getLong("id"));
			item.put("ledgerCode", rs.getString("ledger_code"));
			item.put("periodCode", rs.getString("period_code"));
			item.put("startDate", rs.getObject("start_date", LocalDate.class));
			item.put("endDate", rs.getObject("end_date", LocalDate.class));
			item.put("status", rs.getString("status"));
			item.put("version", rs.getLong("version"));
			item.put("allowedActions", List.of());
			item.put("actionDisabledReasons", Map.of());
			return item;
		}, args.toArray());
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	@Transactional
	public Map<String, Object> createPeriod(PeriodCreateRequest request, CurrentUser operator) {
		GeneralLedgerSupport.advisoryLock(this.jdbcTemplate, "gl:period:create");
		LedgerRow ledger = ledgerRow(true);
		if (!ledger.initialized()) {
			throw new BusinessException(ApiErrorCode.GL_LEDGER_NOT_INITIALIZED);
		}
		String nextPeriod = nextPeriodCode();
		String requested = request.periodCode() == null || request.periodCode().isBlank()
				? nextPeriod : GeneralLedgerSupport.yearMonth(request.periodCode()).toString();
		if (!requested.equals(nextPeriod)) {
			throw new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN);
		}
		LocalDate start = GeneralLedgerSupport.periodStart(requested);
		LocalDate end = GeneralLedgerSupport.periodEnd(requested);
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into gl_accounting_period (
						ledger_id, period_code, start_date, end_date, status, created_by, created_at, updated_by,
						updated_at
					)
					values (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?)
					returning id
					""", Long.class, ledger.id(), requested, start, end, operator.username(), OffsetDateTime.now(),
					operator.username(), OffsetDateTime.now());
			return period(id);
		}
		catch (DuplicateKeyException exception) {
			return periodByCode(requested);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> accounts(String keyword, int page, int pageSize) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.limit(pageSize);
		List<Object> args = new ArrayList<>();
		String where = "where l.code = 'MAIN'";
		if (keyword != null && !keyword.isBlank()) {
			where += " and (a.code ilike ? or a.name ilike ?)";
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_account a
				join gl_ledger l on l.id = a.ledger_id
				%s
				""".formatted(where), Long.class, args.toArray());
		args.add(safeSize);
		args.add(GeneralLedgerSupport.offset(safePage, safeSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select a.*
				from gl_account a
				join gl_ledger l on l.id = a.ledger_id
				%s
				order by a.code
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> accountMap(new AccountRow(rs.getLong("id"),
				GeneralLedgerSupport.nullableLong(rs, "parent_id"), rs.getString("code"), rs.getString("name"),
				rs.getString("category"), rs.getString("balance_direction"), rs.getInt("level_no"),
				rs.getBoolean("is_leaf"), rs.getBoolean("postable"), rs.getBoolean("enabled"), rs.getLong("version")),
				true), args.toArray());
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	@Transactional
	public Map<String, Object> createAccount(AccountRequest request, CurrentUser operator) {
		LedgerRow ledger = ledgerRow(false);
		AccountRow parent = null;
		if (request.parentId() != null) {
			parent = accountRow(request.parentId());
			if (!parent.category().equals(request.category()) || !parent.balanceDirection().equals(request.balanceDirection())) {
				throw new BusinessException(ApiErrorCode.GL_RULE_INVALID);
			}
		}
		String code = GeneralLedgerSupport.requiredText(request.code(), ApiErrorCode.GL_RULE_INVALID);
		String name = GeneralLedgerSupport.requiredText(request.name(), ApiErrorCode.GL_RULE_INVALID);
		boolean postable = request.postable() == null || request.postable();
		boolean enabled = request.enabled() == null || request.enabled();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into gl_account (
						ledger_id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable,
						enabled, template_source, description, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, true, ?, ?, 'USER_DEFINED', ?, ?, ?, ?, ?)
					returning id
					""", Long.class, ledger.id(), request.parentId(), code, name, request.category(),
					request.balanceDirection(), parent == null ? 1 : parent.levelNo() + 1, postable, enabled,
					request.description(), operator.username(), OffsetDateTime.now(), operator.username(),
					OffsetDateTime.now());
			if (parent != null) {
				this.jdbcTemplate.update("""
						update gl_account
						set is_leaf = false, updated_by = ?, updated_at = ?, version = version + 1
						where id = ?
						""", operator.username(), OffsetDateTime.now(), parent.id());
			}
			replaceAuxiliaryRequirements(id, request.auxiliaryRequirements(), operator);
			return account(id, true);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
	}

	@Transactional(readOnly = true)
	public Map<String, Object> account(Long id, boolean includeRequirements) {
		return accountMap(accountRow(id), includeRequirements);
	}

	@Transactional
	public Map<String, Object> updateAccount(Long id, AccountRequest request, CurrentUser operator) {
		AccountRow account = accountRow(id);
		if (!account.enabled()) {
			throw new BusinessException(ApiErrorCode.GL_ACCOUNT_DISABLED);
		}
		this.jdbcTemplate.update("""
				update gl_account
				set name = ?, postable = ?, enabled = ?, description = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ? and version = ?
				""", GeneralLedgerSupport.requiredText(request.name(), ApiErrorCode.GL_RULE_INVALID),
				request.postable() == null || request.postable(), request.enabled() == null || request.enabled(),
				request.description(), operator.username(), OffsetDateTime.now(), id, request.version());
		replaceAuxiliaryRequirements(id, request.auxiliaryRequirements(), operator);
		return account(id, true);
	}

	@Transactional
	public Map<String, Object> disableAccount(Long id, CurrentUser operator) {
		AccountRow account = accountRow(id);
		if (!account.enabled()) {
			return account(id, true);
		}
		Long childCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_account
				where parent_id = ?
				and enabled = true
				""", Long.class, id);
		if (childCount != null && childCount > 0) {
			throw new BusinessException(ApiErrorCode.GL_ACCOUNT_LOCKED);
		}
		this.jdbcTemplate.update("""
				update gl_account
				set enabled = false, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), id);
		return account(id, true);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> auxDimensions(int page, int pageSize) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.limit(pageSize);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from gl_aux_dimension", Long.class);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id, code, name, dimension_type, object_source, system_defined, enabled, version
				from gl_aux_dimension
				order by sort_order, id
				limit ? offset ?
				""", (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("id", rs.getLong("id"));
			item.put("code", rs.getString("code"));
			item.put("name", rs.getString("name"));
			item.put("dimensionType", rs.getString("dimension_type"));
			item.put("objectSource", rs.getString("object_source"));
			item.put("systemDefined", rs.getBoolean("system_defined"));
			item.put("enabled", rs.getBoolean("enabled"));
			item.put("version", rs.getLong("version"));
			return item;
		}, safeSize, GeneralLedgerSupport.offset(safePage, safeSize));
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> auxiliaryCandidates(String dimensionCode, String keyword, int page,
			int pageSize) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.limit(pageSize);
		String code = GeneralLedgerSupport.requiredText(dimensionCode, ApiErrorCode.GL_RULE_INVALID);
		String table = switch (code) {
			case "CUSTOMER" -> "mst_customer";
			case "SUPPLIER" -> "mst_supplier";
			case "PROJECT" -> "sal_project";
			default -> null;
		};
		if (table == null) {
			return customAuxiliaryCandidates(code, keyword, safePage, safeSize);
		}
		String noColumn = "PROJECT".equals(code) ? "project_no" : "code";
		String nameColumn = "name";
		List<Object> args = new ArrayList<>();
		String where = "where status = 'ENABLED'";
		if (keyword != null && !keyword.isBlank()) {
			where += " and (" + noColumn + " ilike ? or " + nameColumn + " ilike ?)";
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
		}
		Long total = this.jdbcTemplate.queryForObject("select count(*) from " + table + " " + where, Long.class,
				args.toArray());
		args.add(safeSize);
		args.add(GeneralLedgerSupport.offset(safePage, safeSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id, %s as code, %s as name
				from %s
				%s
				order by %s
				limit ? offset ?
				""".formatted(noColumn, nameColumn, table, where, noColumn), (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("sourceId", rs.getLong("id"));
			item.put("code", rs.getString("code"));
			item.put("name", rs.getString("name"));
			return item;
		}, args.toArray());
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	private PageResponse<Map<String, Object>> customAuxiliaryCandidates(String dimensionCode, String keyword, int page,
			int pageSize) {
		List<Object> args = new ArrayList<>();
		args.add(dimensionCode);
		String where = "where d.code = ? and i.enabled = true";
		if (keyword != null && !keyword.isBlank()) {
			where += " and (i.code ilike ? or i.name ilike ?)";
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_aux_item i
				join gl_aux_dimension d on d.id = i.dimension_id
				%s
				""".formatted(where), Long.class, args.toArray());
		args.add(pageSize);
		args.add(GeneralLedgerSupport.offset(page, pageSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select i.id, i.code, i.name
				from gl_aux_item i
				join gl_aux_dimension d on d.id = i.dimension_id
				%s
				order by i.code
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("auxItemId", rs.getLong("id"));
			item.put("code", rs.getString("code"));
			item.put("name", rs.getString("name"));
			return item;
		}, args.toArray());
		return PageResponse.of(items, page, pageSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> postingRules(int page, int pageSize) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.limit(pageSize);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from gl_posting_rule", Long.class);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id, source_type, source_variant, rule_version, status, name, effective_from, effective_to, version
				from gl_posting_rule
				order by source_type, source_variant, rule_version desc
				limit ? offset ?
				""", (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("id", rs.getLong("id"));
			item.put("sourceType", rs.getString("source_type"));
			item.put("sourceVariant", rs.getString("source_variant"));
			item.put("ruleVersion", rs.getInt("rule_version"));
			item.put("status", rs.getString("status"));
			item.put("name", rs.getString("name"));
			item.put("effectiveFrom", rs.getObject("effective_from", LocalDate.class));
			item.put("effectiveTo", rs.getObject("effective_to", LocalDate.class));
			item.put("version", rs.getLong("version"));
			return item;
		}, safeSize, GeneralLedgerSupport.offset(safePage, safeSize));
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	private void replaceAuxiliaryRequirements(Long accountId, List<AuxiliaryRequirementRequest> requirements,
			CurrentUser operator) {
		this.jdbcTemplate.update("delete from gl_account_aux_requirement where account_id = ?", accountId);
		if (requirements == null) {
			return;
		}
		for (AuxiliaryRequirementRequest requirement : requirements) {
			Long dimensionId = dimensionId(requirement.dimensionCode());
			this.jdbcTemplate.update("""
					insert into gl_account_aux_requirement (account_id, dimension_id, requirement, created_by, created_at)
					values (?, ?, ?, ?, ?)
					""", accountId, dimensionId, requirement.requirementType(), operator.username(),
					OffsetDateTime.now());
		}
	}

	private Long dimensionId(String code) {
		return this.jdbcTemplate.query("""
				select id
				from gl_aux_dimension
				where code = ?
				and enabled = true
				""", (rs, rowNum) -> rs.getLong("id"), code).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_AUXILIARY_NOT_ALLOWED));
	}

	private String nextPeriodCode() {
		String max = this.jdbcTemplate.queryForObject("""
				select max(period_code)
				from gl_accounting_period
				""", String.class);
		if (max == null) {
			throw new BusinessException(ApiErrorCode.GL_LEDGER_NOT_INITIALIZED);
		}
		return YearMonth.parse(max).plusMonths(1).toString();
	}

	private Map<String, Object> period(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.period_code, p.start_date, p.end_date, p.status, p.version
				from gl_accounting_period p
				where p.id = ?
				""", (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("id", rs.getLong("id"));
			item.put("periodCode", rs.getString("period_code"));
			item.put("startDate", rs.getObject("start_date", LocalDate.class));
			item.put("endDate", rs.getObject("end_date", LocalDate.class));
			item.put("status", rs.getString("status"));
			item.put("version", rs.getLong("version"));
			return item;
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.GL_PERIOD_NOT_FOUND));
	}

	private Map<String, Object> periodByCode(String periodCode) {
		Long id = this.jdbcTemplate.queryForObject("""
				select id
				from gl_accounting_period
				where period_code = ?
				""", Long.class, periodCode);
		return period(id);
	}

	private LedgerRow ledgerRow(boolean forUpdate) {
		String lock = forUpdate ? " for update" : "";
		return this.jdbcTemplate.query("""
				select id, code, name, currency, initialized, start_period_id, start_year_month, version
				from gl_ledger
				where code = 'MAIN'
				%s
				""".formatted(lock), (rs, rowNum) -> new LedgerRow(rs.getLong("id"), rs.getString("code"),
				rs.getString("name"), rs.getString("currency"), rs.getBoolean("initialized"),
				GeneralLedgerSupport.nullableLong(rs, "start_period_id"), rs.getString("start_year_month"),
				rs.getLong("version"))).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_LEDGER_NOT_INITIALIZED));
	}

	private Map<String, Object> ledgerResponse(LedgerRow ledger) {
		Map<String, Object> response = GeneralLedgerSupport.map();
		response.put("id", ledger.id());
		response.put("code", ledger.code());
		response.put("name", ledger.name());
		response.put("currency", ledger.currency());
		response.put("initialized", ledger.initialized());
		response.put("startPeriodId", ledger.startPeriodId());
		response.put("startPeriodCode", ledger.startYearMonth());
		response.put("version", ledger.version());
		return response;
	}

	private AccountRow accountRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable, enabled,
				       version
				from gl_account
				where id = ?
				""", (rs, rowNum) -> new AccountRow(rs.getLong("id"),
				GeneralLedgerSupport.nullableLong(rs, "parent_id"), rs.getString("code"), rs.getString("name"),
				rs.getString("category"), rs.getString("balance_direction"), rs.getInt("level_no"),
				rs.getBoolean("is_leaf"), rs.getBoolean("postable"), rs.getBoolean("enabled"), rs.getLong("version")),
				id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.GL_ACCOUNT_NOT_FOUND));
	}

	private Map<String, Object> accountMap(AccountRow account, boolean includeRequirements) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("id", account.id());
		item.put("parentId", account.parentId());
		item.put("code", account.code());
		item.put("name", account.name());
		item.put("category", account.category());
		item.put("balanceDirection", account.balanceDirection());
		item.put("levelNo", account.levelNo());
		item.put("isLeaf", account.isLeaf());
		item.put("postable", account.postable());
		item.put("enabled", account.enabled());
		item.put("version", account.version());
		if (includeRequirements) {
			item.put("auxiliaryRequirements", auxiliaryRequirements(account.id()));
		}
		return item;
	}

	private List<Map<String, Object>> auxiliaryRequirements(Long accountId) {
		return this.jdbcTemplate.query("""
				select d.code, d.name, r.requirement
				from gl_account_aux_requirement r
				join gl_aux_dimension d on d.id = r.dimension_id
				where r.account_id = ?
				order by d.sort_order, d.id
				""", (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("dimensionCode", rs.getString("code"));
			item.put("dimensionName", rs.getString("name"));
			item.put("requirementType", rs.getString("requirement"));
			return item;
		}, accountId);
	}

	public record LedgerInitializeRequest(String startYearMonth, String idempotencyKey) {
	}

	public record PeriodCreateRequest(String periodCode, String idempotencyKey) {
	}

	public record AccountRequest(Long parentId, String code, String name, String category, String balanceDirection,
			Boolean postable, Boolean enabled, String description,
			List<AuxiliaryRequirementRequest> auxiliaryRequirements, Long version, String idempotencyKey) {
	}

	public record AuxiliaryRequirementRequest(String dimensionCode, String requirementType) {
	}

	record LedgerRow(Long id, String code, String name, String currency, boolean initialized, Long startPeriodId,
			String startYearMonth, Long version) {
	}

	record AccountRow(Long id, Long parentId, String code, String name, String category, String balanceDirection,
			int levelNo, boolean isLeaf, boolean postable, boolean enabled, Long version) {
	}

}
