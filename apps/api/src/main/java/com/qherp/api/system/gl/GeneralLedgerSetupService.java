package com.qherp.api.system.gl;

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
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
		int safeSize = GeneralLedgerSupport.listLimit(pageSize);
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
				       l.code as ledger_code, count(v.id) as voucher_count, max(v.posted_at) as last_posted_at
				from gl_accounting_period p
				join gl_ledger l on l.id = p.ledger_id
				left join gl_voucher v on v.accounting_period_id = p.id and v.status = 'POSTED'
				%s
				group by p.id, l.code
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
			item.put("voucherCount", rs.getLong("voucher_count"));
			item.put("lastPostedAt", rs.getObject("last_posted_at", OffsetDateTime.class));
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
	public PageResponse<Map<String, Object>> accounts(String keyword, String category, Boolean enabled,
			Boolean postable, int page, int pageSize) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.listLimit(pageSize);
		List<Object> args = new ArrayList<>();
		String where = "where l.code = 'MAIN'";
		if (keyword != null && !keyword.isBlank()) {
			where += " and (a.code ilike ? or a.name ilike ?)";
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
		}
		if (category != null && !category.isBlank()) {
			where += " and a.category = ?";
			args.add(normalizeCode(category));
		}
		if (enabled != null) {
			where += " and a.enabled = ?";
			args.add(enabled);
		}
		if (postable != null) {
			where += " and a.postable = ?";
			args.add(postable);
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

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> accountCandidates(String keyword, String selectedIds, int page,
			int pageSize) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.candidateLimit(pageSize);
		Set<Long> selected = parseIds(selectedIds);
		List<Object> args = new ArrayList<>();
		String where = "where l.code = 'MAIN' and a.enabled = true and a.is_leaf = true and a.postable = true";
		if (keyword != null && !keyword.isBlank()) {
			where += " and (a.code ilike ? or a.name ilike ?%s)".formatted(selectedPredicate(selected, "a.id"));
			String like = "%" + keyword.trim() + "%";
			args.add(like);
			args.add(like);
			if (!selected.isEmpty()) {
				args.add(selectedArray(selected));
			}
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
				select a.id, a.code, a.name, a.category, a.balance_direction, a.level_no, a.version
				from gl_account a
				join gl_ledger l on l.id = a.ledger_id
				%s
				order by case when a.id = any (?::bigint[]) then 0 else 1 end, a.code
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("accountId", rs.getLong("id"));
			item.put("id", rs.getLong("id"));
			item.put("accountCode", rs.getString("code"));
			item.put("code", rs.getString("code"));
			item.put("accountName", rs.getString("name"));
			item.put("name", rs.getString("name"));
			item.put("category", rs.getString("category"));
			item.put("balanceDirection", rs.getString("balance_direction"));
			item.put("level", rs.getInt("level_no"));
			item.put("version", rs.getLong("version"));
			item.put("auxiliaryRequirements", auxiliaryRequirements(rs.getLong("id")));
			return item;
		}, candidateArgs(args, selected).toArray());
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
		boolean referenced = accountReferenced(id);
		boolean activeRuleReferenced = accountReferencedByActiveRule(id);
		if (referenced && accountLockedFieldsChanged(account, request)) {
			throw new BusinessException(ApiErrorCode.GL_ACCOUNT_LOCKED);
		}
		if (activeRuleReferenced && request.enabled() != null && !request.enabled()) {
			throw new BusinessException(ApiErrorCode.GL_ACCOUNT_LOCKED);
		}
		this.jdbcTemplate.update("""
				update gl_account
				set name = ?, postable = ?, enabled = ?, description = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ? and version = ?
				""", GeneralLedgerSupport.requiredText(request.name(), ApiErrorCode.GL_RULE_INVALID),
				request.postable() == null ? account.postable() : request.postable(),
				request.enabled() == null ? account.enabled() : request.enabled(),
				request.description(), operator.username(), OffsetDateTime.now(), id, request.version());
		if (!referenced && request.auxiliaryRequirements() != null) {
			replaceAuxiliaryRequirements(id, request.auxiliaryRequirements(), operator);
		}
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
		if (accountReferencedByActiveRule(id)) {
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
	public PageResponse<Map<String, Object>> auxDimensions(String keyword, Boolean enabled, int page, int pageSize) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.listLimit(pageSize);
		List<Object> args = new ArrayList<>();
		String where = "where true";
		if (keyword != null && !keyword.isBlank()) {
			where += " and (d.code ilike ? or d.name ilike ?)";
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
		}
		if (enabled != null) {
			where += " and d.enabled = ?";
			args.add(enabled);
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_aux_dimension d
				%s
				""".formatted(where), Long.class, args.toArray());
		args.add(safeSize);
		args.add(GeneralLedgerSupport.offset(safePage, safeSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select d.id, d.code, d.name, d.dimension_type, d.object_source, d.system_defined, d.enabled,
				       d.version, count(i.id) as item_count
				from gl_aux_dimension d
				left join gl_aux_item i on i.dimension_id = d.id
				%s
				group by d.id
				order by d.sort_order, d.id
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("id", rs.getLong("id"));
			item.put("code", rs.getString("code"));
			item.put("name", rs.getString("name"));
			item.put("dimensionType", rs.getString("dimension_type"));
			item.put("objectSource", rs.getString("object_source"));
			item.put("systemDefined", rs.getBoolean("system_defined"));
			item.put("enabled", rs.getBoolean("enabled"));
			item.put("itemCount", rs.getLong("item_count"));
			item.put("version", rs.getLong("version"));
			item.put("allowedActions", rs.getBoolean("system_defined") ? List.of()
					: rs.getBoolean("enabled") ? List.of("UPDATE", "DISABLE") : List.of("UPDATE"));
			item.put("actionDisabledReasons", rs.getBoolean("system_defined")
					? Map.of("UPDATE", "系统辅助维度不允许维护", "DISABLE", "系统辅助维度不允许停用") : Map.of());
			return item;
		}, args.toArray());
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	@Transactional
	public Map<String, Object> createAuxDimension(AuxDimensionRequest request, CurrentUser operator) {
		String code = normalizeCode(request.code());
		String name = GeneralLedgerSupport.requiredText(request.name(), ApiErrorCode.GL_RULE_INVALID);
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into gl_aux_dimension (
						code, name, dimension_type, object_source, system_defined, enabled, sort_order, created_by,
						created_at, updated_by, updated_at
					)
					values (?, ?, 'CUSTOM', null, false, ?, coalesce((select max(sort_order) + 10 from gl_aux_dimension), 100),
					        ?, ?, ?, ?)
					returning id
					""", Long.class, code, name, request.enabled() == null || request.enabled(),
					operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now());
			return auxDimension(id);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
	}

	@Transactional
	public Map<String, Object> updateAuxDimension(Long id, AuxDimensionRequest request, CurrentUser operator) {
		AuxDimensionRow dimension = auxDimensionRow(id);
		if (dimension.systemDefined()) {
			throw new BusinessException(ApiErrorCode.GL_AUXILIARY_NOT_ALLOWED);
		}
		this.jdbcTemplate.update("""
				update gl_aux_dimension
				set name = ?, enabled = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ?
				""", GeneralLedgerSupport.requiredText(request.name(), ApiErrorCode.GL_RULE_INVALID),
				request.enabled() == null ? dimension.enabled() : request.enabled(), operator.username(),
				OffsetDateTime.now(), id, request.version());
		return auxDimension(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> auxiliaryCandidates(String dimensionCode, String keyword, int page,
			int pageSize) {
		return auxiliaryCandidates(dimensionCode, keyword, null, page, pageSize, null);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> auxiliaryCandidates(String dimensionCode, String keyword,
			String selectedIds, int page, int pageSize) {
		return auxiliaryCandidates(dimensionCode, keyword, selectedIds, page, pageSize, null);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> auxiliaryCandidates(String dimensionCode, String keyword,
			String selectedIds, int page, int pageSize, CurrentUser user) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.candidateLimit(pageSize);
		String code = GeneralLedgerSupport.requiredText(dimensionCode, ApiErrorCode.GL_RULE_INVALID);
		String table = switch (code) {
			case "CUSTOMER" -> "mst_customer";
			case "SUPPLIER" -> "mst_supplier";
			case "PROJECT" -> "sal_project";
			default -> null;
		};
		if (table == null) {
			return customAuxiliaryCandidates(code, keyword, selectedIds, safePage, safeSize);
		}
		Set<Long> selected = parseIds(selectedIds);
		String noColumn = "PROJECT".equals(code) ? "project_no" : "code";
		String nameColumn = "name";
		List<Object> args = new ArrayList<>();
		String where = "where status = 'ENABLED'";
		if (keyword != null && !keyword.isBlank()) {
			where += " and (" + noColumn + " ilike ? or " + nameColumn + " ilike ?"
					+ selectedPredicate(selected, "id") + ")";
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
			if (!selected.isEmpty()) {
				args.add(selectedArray(selected));
			}
		}
		Long total = this.jdbcTemplate.queryForObject("select count(*) from " + table + " " + where, Long.class,
				args.toArray());
		args.add(safeSize);
		args.add(GeneralLedgerSupport.offset(safePage, safeSize));
		boolean sourceVisible = GeneralLedgerSupport.hasPermission(user, "gl:source:view");
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id, %s as code, %s as name
				from %s
				%s
				order by case when id = any (?::bigint[]) then 0 else 1 end, %s
				limit ? offset ?
				""".formatted(noColumn, nameColumn, table, where, noColumn), (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("sourceVisible", sourceVisible);
			if (sourceVisible) {
				item.put("objectId", rs.getLong("id"));
				item.put("objectCode", rs.getString("code"));
				item.put("objectName", rs.getString("name"));
				item.put("sourceId", rs.getLong("id"));
				item.put("code", rs.getString("code"));
				item.put("name", rs.getString("name"));
			}
			else {
				item.put("dimensionCode", code);
				item.put("restricted", true);
				item.put("restrictedReason", "无权查看来源");
				item.put("displayName", "受限来源");
			}
			return item;
		}, candidateArgs(args, selected).toArray());
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> auxItems(Long dimensionId, String keyword, int page, int pageSize) {
		AuxDimensionRow dimension = auxDimensionRow(dimensionId);
		if (!"CUSTOM".equals(dimension.dimensionType())) {
			throw new BusinessException(ApiErrorCode.GL_AUXILIARY_NOT_ALLOWED);
		}
		return customAuxiliaryItems(dimension.id(), keyword, page, pageSize);
	}

	@Transactional
	public Map<String, Object> createAuxItem(Long dimensionId, AuxItemRequest request, CurrentUser operator) {
		AuxDimensionRow dimension = auxDimensionRow(dimensionId);
		if (!"CUSTOM".equals(dimension.dimensionType()) || !dimension.enabled()) {
			throw new BusinessException(ApiErrorCode.GL_AUXILIARY_NOT_ALLOWED);
		}
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into gl_aux_item (
						dimension_id, code, name, enabled, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, dimension.id(), normalizeCode(request.code()),
					GeneralLedgerSupport.requiredText(request.name(), ApiErrorCode.GL_RULE_INVALID),
					request.enabled() == null || request.enabled(), operator.username(), OffsetDateTime.now(),
					operator.username(), OffsetDateTime.now());
			return auxItem(id);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
	}

	@Transactional
	public Map<String, Object> updateAuxItem(Long dimensionId, Long itemId, AuxItemRequest request,
			CurrentUser operator) {
		AuxDimensionRow dimension = auxDimensionRow(dimensionId);
		if (!"CUSTOM".equals(dimension.dimensionType())) {
			throw new BusinessException(ApiErrorCode.GL_AUXILIARY_NOT_ALLOWED);
		}
		AuxItemRow item = auxItemRow(itemId);
		if (!item.dimensionId().equals(dimension.id())) {
			throw new BusinessException(ApiErrorCode.GL_AUXILIARY_NOT_ALLOWED);
		}
		this.jdbcTemplate.update("""
				update gl_aux_item
				set code = ?, name = ?, enabled = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ?
				""", normalizeCode(request.code()), GeneralLedgerSupport.requiredText(request.name(),
				ApiErrorCode.GL_RULE_INVALID), request.enabled() == null ? item.enabled() : request.enabled(),
				operator.username(), OffsetDateTime.now(), itemId, request.version());
		return auxItem(itemId);
	}

	private PageResponse<Map<String, Object>> customAuxiliaryCandidates(String dimensionCode, String keyword,
			String selectedIds, int page, int pageSize) {
		List<Object> args = new ArrayList<>();
		Set<Long> selected = parseIds(selectedIds);
		args.add(dimensionCode);
		String where = "where d.code = ? and i.enabled = true";
		if (keyword != null && !keyword.isBlank()) {
			where += " and (i.code ilike ? or i.name ilike ?" + selectedPredicate(selected, "i.id") + ")";
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
			if (!selected.isEmpty()) {
				args.add(selectedArray(selected));
			}
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
				order by case when i.id = any (?::bigint[]) then 0 else 1 end, i.code
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("objectId", rs.getLong("id"));
			item.put("objectCode", rs.getString("code"));
			item.put("objectName", rs.getString("name"));
			item.put("auxItemId", rs.getLong("id"));
			item.put("code", rs.getString("code"));
			item.put("name", rs.getString("name"));
			return item;
		}, candidateArgs(args, selected).toArray());
		return PageResponse.of(items, page, pageSize, total == null ? 0 : total);
	}

	private PageResponse<Map<String, Object>> customAuxiliaryItems(Long dimensionId, String keyword, int page,
			int pageSize) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.listLimit(pageSize);
		List<Object> args = new ArrayList<>();
		args.add(dimensionId);
		String where = "where dimension_id = ?";
		if (keyword != null && !keyword.isBlank()) {
			where += " and (code ilike ? or name ilike ?)";
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
		}
		Long total = this.jdbcTemplate.queryForObject("select count(*) from gl_aux_item " + where, Long.class,
				args.toArray());
		args.add(safeSize);
		args.add(GeneralLedgerSupport.offset(safePage, safeSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id
				from gl_aux_item
				%s
				order by code
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> auxItem(rs.getLong("id")), args.toArray());
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> postingRules(String sourceType, String status, int page, int pageSize) {
		int safePage = GeneralLedgerSupport.page(page);
		int safeSize = GeneralLedgerSupport.listLimit(pageSize);
		List<Object> args = new ArrayList<>();
		String where = "where true";
		if (sourceType != null && !sourceType.isBlank()) {
			where += " and r.source_type = ?";
			args.add(normalizeCode(sourceType));
		}
		if (status != null && !status.isBlank()) {
			where += " and r.status = ?";
			args.add(normalizeCode(status));
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_posting_rule r
				%s
				""".formatted(where), Long.class, args.toArray());
		args.add(safeSize);
		args.add(GeneralLedgerSupport.offset(safePage, safeSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select r.id, r.source_type, r.source_variant, r.rule_version, r.status, r.name, r.effective_from,
				       r.effective_to, r.version, count(l.id) as line_count
				from gl_posting_rule r
				left join gl_posting_rule_line l on l.rule_id = r.id
				%s
				group by r.id
				order by r.source_type, r.source_variant,
				         case r.status when 'ACTIVE' then 0 when 'DRAFT' then 1 when 'SUPERSEDED' then 2 else 3 end,
				         r.rule_version desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> postingRuleMap(new PostingRuleRow(rs.getLong("id"),
				rs.getString("source_type"), rs.getString("source_variant"), rs.getInt("rule_version"),
				rs.getString("status"), rs.getString("name"), rs.getObject("effective_from", LocalDate.class),
				rs.getObject("effective_to", LocalDate.class), rs.getLong("version")), false, rs.getLong("line_count")),
				args.toArray());
		return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> postingRule(Long id) {
		return postingRuleMap(postingRuleRow(id), true, null);
	}

	@Transactional
	public Map<String, Object> createPostingRule(PostingRuleRequest request, CurrentUser operator) {
		String sourceType = normalizeCode(request.sourceType());
		String sourceVariant = request.sourceVariant() == null || request.sourceVariant().isBlank()
				? "DEFAULT" : normalizeCode(request.sourceVariant());
		Integer nextVersion = this.jdbcTemplate.queryForObject("""
				select coalesce(max(rule_version), 0) + 1
				from gl_posting_rule
				where source_type = ?
				and source_variant = ?
				""", Integer.class, sourceType, sourceVariant);
		Long id = this.jdbcTemplate.queryForObject("""
				insert into gl_posting_rule (
					source_type, source_variant, rule_version, status, name, description, effective_from,
					effective_to, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, sourceType, sourceVariant, nextVersion,
				GeneralLedgerSupport.requiredText(request.name(), ApiErrorCode.GL_RULE_INVALID),
				request.description(), request.effectiveFrom(), request.effectiveTo(), operator.username(),
				OffsetDateTime.now(), operator.username(), OffsetDateTime.now());
		replaceRuleLines(id, request.lines());
		return postingRule(id);
	}

	@Transactional
	public Map<String, Object> newPostingRuleVersion(Long id, ActionRequest request, CurrentUser operator) {
		PostingRuleRow source = postingRuleRow(id);
		requireVersion(source.version(), request.version());
		Integer nextVersion = this.jdbcTemplate.queryForObject("""
				select coalesce(max(rule_version), 0) + 1
				from gl_posting_rule
				where source_type = ?
				and source_variant = ?
				""", Integer.class, source.sourceType(), source.sourceVariant());
		Long newId = this.jdbcTemplate.queryForObject("""
				insert into gl_posting_rule (
					source_type, source_variant, rule_version, status, name, description, effective_from,
					effective_to, created_by, created_at, updated_by, updated_at
				)
				select source_type, source_variant, ?, 'DRAFT', name, description, effective_from, effective_to,
				       ?, ?, ?, ?
				from gl_posting_rule
				where id = ?
				returning id
				""", Long.class, nextVersion, operator.username(), OffsetDateTime.now(), operator.username(),
				OffsetDateTime.now(), id);
		copyRuleLines(id, newId);
		return postingRule(newId);
	}

	@Transactional
	public Map<String, Object> updatePostingRule(Long id, PostingRuleRequest request, CurrentUser operator) {
		PostingRuleRow rule = postingRuleRow(id);
		requireVersion(rule.version(), request.version());
		if (!"DRAFT".equals(rule.status())) {
			throw new BusinessException(ApiErrorCode.GL_RULE_INVALID);
		}
		this.jdbcTemplate.update("""
				update gl_posting_rule
				set name = ?, description = ?, effective_from = ?, effective_to = ?, updated_by = ?,
				    updated_at = ?, version = version + 1
				where id = ?
				""", GeneralLedgerSupport.requiredText(request.name(), ApiErrorCode.GL_RULE_INVALID),
				request.description(), request.effectiveFrom(), request.effectiveTo(), operator.username(),
				OffsetDateTime.now(), id);
		if (request.lines() != null) {
			replaceRuleLines(id, request.lines());
		}
		return postingRule(id);
	}

	@Transactional
	public Map<String, Object> validatePostingRule(Long id, ActionRequest request, CurrentUser operator) {
		PostingRuleRow rule = postingRuleRow(id);
		if (request != null && request.version() != null) {
			requireVersion(rule.version(), request.version());
		}
		ruleValidationSummary(id, request);
		return postingRule(id);
	}

	@Transactional
	public Map<String, Object> activatePostingRule(Long id, ActionRequest request, CurrentUser operator) {
		PostingRuleRow rule = postingRuleRow(id);
		requireVersion(rule.version(), request.version());
		if (!"DRAFT".equals(rule.status()) && !"ACTIVE".equals(rule.status())) {
			throw new BusinessException(ApiErrorCode.GL_RULE_INVALID);
		}
		ruleValidationSummary(id, request);
		this.jdbcTemplate.update("""
				update gl_posting_rule
				set status = 'SUPERSEDED', updated_by = ?, updated_at = ?, version = version + 1
				where source_type = ?
				and source_variant = ?
				and status = 'ACTIVE'
				and id <> ?
				""", operator.username(), OffsetDateTime.now(), rule.sourceType(), rule.sourceVariant(), id);
		this.jdbcTemplate.update("""
				update gl_posting_rule
				set status = 'ACTIVE', activated_by = ?, activated_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
		return postingRule(id);
	}

	@Transactional
	public Map<String, Object> disablePostingRule(Long id, ActionRequest request, CurrentUser operator) {
		PostingRuleRow rule = postingRuleRow(id);
		requireVersion(rule.version(), request.version());
		if ("SUPERSEDED".equals(rule.status()) || "DISABLED".equals(rule.status())) {
			return postingRule(id);
		}
		this.jdbcTemplate.update("""
				update gl_posting_rule
				set status = 'DISABLED', updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), id);
		return postingRule(id);
	}

	private Map<String, Object> postingRuleMap(PostingRuleRow rule, boolean includeLines, Long lineCount) {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("id", rule.id());
			item.put("sourceType", rule.sourceType());
			item.put("sourceVariant", rule.sourceVariant());
			item.put("ruleVersion", rule.ruleVersion());
			item.put("versionNo", rule.ruleVersion());
			item.put("status", rule.status());
			item.put("name", rule.name());
			item.put("effectiveFrom", rule.effectiveFrom());
			item.put("effectiveTo", rule.effectiveTo());
			item.put("lineCount", lineCount == null ? ruleLineCount(rule.id()) : lineCount);
			Map<String, Object> validationSummary = "DISABLED".equals(rule.status()) ? null
					: ruleValidationSummaryOrNull(rule.id());
			item.put("validationStatus", "DISABLED".equals(rule.status()) ? "SKIPPED"
					: validationSummary == null ? "INVALID" : "VALID");
			item.put("validationSummary", validationSummary);
			item.put("allowedActions", ruleAllowedActions(rule.status()));
			item.put("actionDisabledReasons", ruleDisabledReasons(rule.status()));
			item.put("version", rule.version());
			if (includeLines) {
				item.put("lines", ruleLines(rule.id()));
			}
			return item;
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
		boolean referenced = accountReferenced(account.id());
		boolean activeRuleReferenced = accountReferencedByActiveRule(account.id());
		item.put("id", account.id());
		item.put("parentId", account.parentId());
		item.put("code", account.code());
		item.put("name", account.name());
		item.put("category", account.category());
		item.put("balanceDirection", account.balanceDirection());
		item.put("level", account.levelNo());
		item.put("levelNo", account.levelNo());
		item.put("isLeaf", account.isLeaf());
		item.put("postable", account.postable());
		item.put("enabled", account.enabled());
		item.put("referenced", referenced);
		item.put("version", account.version());
		item.put("allowedActions", accountAllowedActions(account, activeRuleReferenced));
		item.put("actionDisabledReasons", accountDisabledReasons(account, referenced, activeRuleReferenced));
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
			item.put("requirement", rs.getString("requirement"));
			item.put("requirementType", rs.getString("requirement"));
			return item;
		}, accountId);
	}

	private boolean accountReferenced(Long accountId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select (
					select count(*) from gl_posting_rule_line where account_id = ?
				) + (
					select count(*) from gl_voucher_line where account_id = ?
				) + (
					select count(*) from gl_ledger_entry where account_id = ?
				)
				""", Long.class, accountId, accountId, accountId);
		return count != null && count > 0;
	}

	private boolean accountReferencedByActiveRule(Long accountId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_posting_rule_line l
				join gl_posting_rule r on r.id = l.rule_id
				where l.account_id = ?
				and r.status = 'ACTIVE'
				""", Long.class, accountId);
		return count != null && count > 0;
	}

	private boolean accountLockedFieldsChanged(AccountRow account, AccountRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.GL_RULE_INVALID);
		}
		if (request.code() != null && !account.code().equals(GeneralLedgerSupport.text(request.code()))) {
			return true;
		}
		if (request.category() != null && !account.category().equals(request.category())) {
			return true;
		}
		if (request.balanceDirection() != null && !account.balanceDirection().equals(request.balanceDirection())) {
			return true;
		}
		if (request.postable() != null && !account.postable().equals(request.postable())) {
			return true;
		}
		return request.auxiliaryRequirements() != null
				&& !requirementSet(account.id()).equals(requirementSet(request.auxiliaryRequirements()));
	}

	private Set<String> requirementSet(Long accountId) {
		return new HashSet<>(this.jdbcTemplate.query("""
				select d.code || ':' || r.requirement
				from gl_account_aux_requirement r
				join gl_aux_dimension d on d.id = r.dimension_id
				where r.account_id = ?
				""", (rs, rowNum) -> rs.getString(1), accountId));
	}

	private Set<String> requirementSet(List<AuxiliaryRequirementRequest> requirements) {
		Set<String> result = new HashSet<>();
		for (AuxiliaryRequirementRequest requirement : requirements) {
			result.add(requirement.dimensionCode() + ":" + requirement.requirementType());
		}
		return result;
	}

	private List<String> accountAllowedActions(AccountRow account, boolean activeRuleReferenced) {
		List<String> actions = new ArrayList<>();
		if (account.enabled()) {
			actions.add("UPDATE");
			if (!activeRuleReferenced) {
				actions.add("DISABLE");
			}
		}
		return actions;
	}

	private Map<String, Object> accountDisabledReasons(AccountRow account, boolean referenced,
			boolean activeRuleReferenced) {
		Map<String, Object> reasons = GeneralLedgerSupport.map();
		if (referenced) {
			reasons.put("CHANGE_KEY_FIELDS", "会计科目已引用凭证、账簿或制证规则，关键属性不可改");
		}
		if (activeRuleReferenced) {
			reasons.put("DISABLE", "会计科目已引用活动制证规则，不能停用");
		}
		else if (!account.enabled()) {
			reasons.put("DISABLE", "会计科目已停用");
		}
		return reasons;
	}

	private Map<String, Object> auxDimension(Long id) {
		AuxDimensionRow dimension = auxDimensionRow(id);
		Map<String, Object> item = GeneralLedgerSupport.map();
		item.put("id", dimension.id());
		item.put("code", dimension.code());
		item.put("name", dimension.name());
		item.put("dimensionType", dimension.dimensionType());
		item.put("objectSource", dimension.objectSource());
		item.put("systemDefined", dimension.systemDefined());
		item.put("enabled", dimension.enabled());
		item.put("itemCount", auxItemCount(dimension.id()));
		item.put("version", dimension.version());
		item.put("allowedActions", dimension.systemDefined() ? List.of()
				: dimension.enabled() ? List.of("UPDATE", "DISABLE") : List.of("UPDATE"));
		item.put("actionDisabledReasons", dimension.systemDefined()
				? Map.of("UPDATE", "系统辅助维度不允许维护", "DISABLE", "系统辅助维度不允许停用") : Map.of());
		return item;
	}

	private AuxDimensionRow auxDimensionRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, code, name, dimension_type, object_source, system_defined, enabled, version
				from gl_aux_dimension
				where id = ?
				""", (rs, rowNum) -> new AuxDimensionRow(rs.getLong("id"), rs.getString("code"),
				rs.getString("name"), rs.getString("dimension_type"), rs.getString("object_source"),
				rs.getBoolean("system_defined"), rs.getBoolean("enabled"), rs.getLong("version")), id).stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_AUXILIARY_NOT_ALLOWED));
	}

	private long auxItemCount(Long dimensionId) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from gl_aux_item where dimension_id = ?",
				Long.class, dimensionId);
		return count == null ? 0 : count;
	}

	private Map<String, Object> auxItem(Long id) {
		AuxItemRow item = auxItemRow(id);
		Map<String, Object> response = GeneralLedgerSupport.map();
		response.put("id", item.id());
		response.put("objectId", item.id());
		response.put("auxItemId", item.id());
		response.put("dimensionId", item.dimensionId());
		response.put("objectCode", item.code());
		response.put("code", item.code());
		response.put("objectName", item.name());
		response.put("name", item.name());
		response.put("enabled", item.enabled());
		response.put("version", item.version());
		response.put("allowedActions", item.enabled() ? List.of("UPDATE", "DISABLE") : List.of("UPDATE"));
		response.put("actionDisabledReasons", Map.of());
		return response;
	}

	private AuxItemRow auxItemRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, dimension_id, code, name, enabled, version
				from gl_aux_item
				where id = ?
				""", (rs, rowNum) -> new AuxItemRow(rs.getLong("id"), rs.getLong("dimension_id"),
				rs.getString("code"), rs.getString("name"), rs.getBoolean("enabled"), rs.getLong("version")), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_AUXILIARY_NOT_ALLOWED));
	}

	private PostingRuleRow postingRuleRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, source_type, source_variant, rule_version, status, name, effective_from, effective_to,
				       version
				from gl_posting_rule
				where id = ?
				""", (rs, rowNum) -> new PostingRuleRow(rs.getLong("id"), rs.getString("source_type"),
				rs.getString("source_variant"), rs.getInt("rule_version"), rs.getString("status"),
				rs.getString("name"), rs.getObject("effective_from", LocalDate.class),
				rs.getObject("effective_to", LocalDate.class), rs.getLong("version")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_RULE_MISSING));
	}

	private long ruleLineCount(Long ruleId) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from gl_posting_rule_line where rule_id = ?",
				Long.class, ruleId);
		return count == null ? 0 : count;
	}

	private List<Map<String, Object>> ruleLines(Long ruleId) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.normalized_fact_code, l.direction, l.account_id, a.code as account_code,
				       a.name as account_name, l.summary_template
				from gl_posting_rule_line l
				join gl_account a on a.id = l.account_id
				where l.rule_id = ?
				order by l.line_no
				""", (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("id", rs.getLong("id"));
			item.put("lineNo", rs.getInt("line_no"));
			item.put("normalizedFactCode", rs.getString("normalized_fact_code"));
			item.put("direction", rs.getString("direction"));
			item.put("accountId", rs.getLong("account_id"));
			item.put("accountCode", rs.getString("account_code"));
			item.put("accountName", rs.getString("account_name"));
			item.put("summaryTemplate", rs.getString("summary_template"));
			item.put("auxiliaryMappings", ruleAuxMappings(rs.getLong("id")));
			return item;
		}, ruleId);
	}

	private List<Map<String, Object>> ruleAuxMappings(Long lineId) {
		return this.jdbcTemplate.query("""
				select d.code, d.name, m.mapping_type, m.fixed_aux_item_id
				from gl_posting_rule_line_aux_map m
				join gl_aux_dimension d on d.id = m.dimension_id
				where m.rule_line_id = ?
				order by d.sort_order, d.id
				""", (rs, rowNum) -> {
			Map<String, Object> item = GeneralLedgerSupport.map();
			item.put("dimensionCode", rs.getString("code"));
			item.put("dimensionName", rs.getString("name"));
			item.put("mappingType", rs.getString("mapping_type"));
			item.put("fixedAuxItemId", GeneralLedgerSupport.nullableLong(rs, "fixed_aux_item_id"));
			return item;
		}, lineId);
	}

	private Map<String, Object> ruleValidationSummary(Long ruleId, ActionRequest request) {
		Map<String, Object> summary = ruleValidationSummaryOrNull(ruleId, request);
		if (summary == null) {
			throw new BusinessException(ApiErrorCode.GL_RULE_INVALID);
		}
		return summary;
	}

	private Map<String, Object> ruleValidationSummaryOrNull(Long ruleId) {
		return ruleValidationSummaryOrNull(ruleId, null);
	}

	private Map<String, Object> ruleValidationSummaryOrNull(Long ruleId, ActionRequest request) {
		PostingRuleRow rule = postingRuleRow(ruleId);
		List<FactSpec> expectedFacts = expectedFacts(rule, request);
		List<RuleLineValidationRow> lines = ruleValidationLines(ruleId);
		if (expectedFacts.isEmpty() || lines.size() != expectedFacts.size()) {
			return null;
		}
		BigDecimal debit = BigDecimal.ZERO;
		BigDecimal credit = BigDecimal.ZERO;
		for (FactSpec fact : expectedFacts) {
			RuleLineValidationRow line = lines.stream()
				.filter((candidate) -> fact.factCode().equals(candidate.normalizedFactCode())
						&& fact.direction().equals(candidate.direction()))
				.findFirst()
				.orElse(null);
			if (line == null || !line.enabled() || !line.postable() || !line.leaf()
					|| !requiredAuxiliaryMappingsSatisfied(line, fact)) {
				return null;
			}
			if ("DEBIT".equals(line.direction())) {
				debit = debit.add(fact.amount());
			}
			else {
				credit = credit.add(fact.amount());
			}
		}
		if (debit.compareTo(credit) != 0) {
			return null;
		}
		Map<String, Object> summary = GeneralLedgerSupport.map();
		summary.put("balanced", true);
		summary.put("lineCount", lines.size());
		summary.put("factCount", expectedFacts.size());
		summary.put("debitTotal", GeneralLedgerSupport.decimal(debit));
		summary.put("creditTotal", GeneralLedgerSupport.decimal(credit));
		summary.put("previewOnly", true);
		summary.put("sourcePreview", request != null && request.sourceId() != null);
		return summary;
	}

	private List<RuleLineValidationRow> ruleValidationLines(Long ruleId) {
		return this.jdbcTemplate.query("""
				select l.id, l.normalized_fact_code, l.direction, l.account_id, a.enabled, a.postable, a.is_leaf
				from gl_posting_rule_line l
				join gl_account a on a.id = l.account_id
				where l.rule_id = ?
				order by l.line_no, l.id
				""", (rs, rowNum) -> new RuleLineValidationRow(rs.getLong("id"),
				rs.getString("normalized_fact_code"), rs.getString("direction"), rs.getLong("account_id"),
				rs.getBoolean("enabled"), rs.getBoolean("postable"), rs.getBoolean("is_leaf")), ruleId);
	}

	private boolean requiredAuxiliaryMappingsSatisfied(RuleLineValidationRow line, FactSpec fact) {
		List<String> requiredDimensions = this.jdbcTemplate.query("""
				select d.code
				from gl_account_aux_requirement r
				join gl_aux_dimension d on d.id = r.dimension_id
				where r.account_id = ?
				and r.requirement = 'REQUIRED'
				order by d.code
				""", (rs, rowNum) -> rs.getString("code"), line.accountId());
		if (requiredDimensions.isEmpty()) {
			return true;
		}
		Map<String, String> mappings = new LinkedHashMap<>();
		this.jdbcTemplate.query("""
				select d.code, m.mapping_type
				from gl_posting_rule_line_aux_map m
				join gl_aux_dimension d on d.id = m.dimension_id
				where m.rule_line_id = ?
				""", (rs) -> {
			mappings.put(rs.getString("code"), rs.getString("mapping_type"));
		}, line.id());
		for (String dimensionCode : requiredDimensions) {
			String mappingType = mappings.get(dimensionCode);
			if (mappingType == null || !factCanProvideDimension(fact, dimensionCode, mappingType)) {
				return false;
			}
		}
		return true;
	}

	private boolean factCanProvideDimension(FactSpec fact, String dimensionCode, String mappingType) {
		return switch (mappingType) {
			case "SOURCE_CUSTOMER" -> "CUSTOMER".equals(dimensionCode) && fact.customerAvailable();
			case "SOURCE_SUPPLIER" -> "SUPPLIER".equals(dimensionCode) && fact.supplierAvailable();
			case "SOURCE_PROJECT" -> "PROJECT".equals(dimensionCode) && fact.projectAvailable();
			case "FIXED_CUSTOM_ITEM" -> !List.of("CUSTOMER", "SUPPLIER", "PROJECT").contains(dimensionCode);
			default -> false;
		};
	}

	private List<FactSpec> expectedFacts(PostingRuleRow rule, ActionRequest request) {
		if (request != null && request.sourceType() != null
				&& !normalizeCode(request.sourceType()).equals(rule.sourceType())) {
			return List.of();
		}
		if (request != null && request.sourceId() != null) {
			return previewFacts(rule, request);
		}
		return templateFacts(rule.sourceType(), rule.sourceVariant());
	}

	private List<FactSpec> previewFacts(PostingRuleRow rule, ActionRequest request) {
		SourcePreview preview = sourcePreview(rule.sourceType(), request.sourceId());
		if (preview == null || !rule.sourceVariant().equals(preview.sourceVariant())) {
			return List.of();
		}
		if (request.sourceVersion() != null && !request.sourceVersion().equals(preview.sourceVersion())) {
			return List.of();
		}
		return preview.facts().stream()
			.filter((fact) -> fact.amount().compareTo(BigDecimal.ZERO) > 0)
			.toList();
	}

	private SourcePreview sourcePreview(String sourceType, Long sourceId) {
		if (sourceId == null) {
			return null;
		}
		return switch (sourceType) {
			case "SALES_INVOICE" -> salesInvoicePreview(sourceId);
			case "PURCHASE_INVOICE" -> purchaseInvoicePreview(sourceId);
			case "EXPENSE" -> expensePreview(sourceId);
			case "RECEIPT" -> receiptPreview(sourceId);
			case "PAYMENT" -> paymentPreview(sourceId);
			case "SETTLEMENT_ALLOCATION" -> settlementPreview(sourceId);
			default -> null;
		};
	}

	private SourcePreview salesInvoicePreview(Long id) {
		return this.jdbcTemplate.query("""
				select customer_id, project_id, tax_excluded_amount, tax_amount, tax_included_amount, status, version
				from fin_sales_invoice
				where id = ?
				""", (rs, rowNum) -> {
			if (!"CONFIRMED".equals(rs.getString("status"))) {
				return null;
			}
			Long customerId = GeneralLedgerSupport.nullableLong(rs, "customer_id");
			Long projectId = GeneralLedgerSupport.nullableLong(rs, "project_id");
			return new SourcePreview("DEFAULT", rs.getLong("version"), List.of(
					new FactSpec("SALES_RECEIVABLE", "DEBIT", rs.getBigDecimal("tax_included_amount"),
							customerId != null, false, projectId != null),
					new FactSpec("SALES_REVENUE", "CREDIT", rs.getBigDecimal("tax_excluded_amount"), false,
							false, projectId != null),
					new FactSpec("OUTPUT_VAT", "CREDIT", rs.getBigDecimal("tax_amount"), false, false, false)));
		}, id).stream().findFirst().orElse(null);
	}

	private SourcePreview purchaseInvoicePreview(Long id) {
		return this.jdbcTemplate.query("""
				select supplier_id, project_id, tax_excluded_amount, tax_amount, tax_included_amount, status, version
				from fin_purchase_invoice
				where id = ?
				""", (rs, rowNum) -> {
			if (!"CONFIRMED".equals(rs.getString("status"))) {
				return null;
			}
			Long supplierId = GeneralLedgerSupport.nullableLong(rs, "supplier_id");
			Long projectId = GeneralLedgerSupport.nullableLong(rs, "project_id");
			return new SourcePreview("DEFAULT", rs.getLong("version"), List.of(
					new FactSpec("PURCHASE_CLEARING", "DEBIT", rs.getBigDecimal("tax_excluded_amount"), false,
							false, projectId != null),
					new FactSpec("INPUT_VAT", "DEBIT", rs.getBigDecimal("tax_amount"), false, false, false),
					new FactSpec("PURCHASE_PAYABLE", "CREDIT", rs.getBigDecimal("tax_included_amount"), false,
							supplierId != null, projectId != null)));
		}, id).stream().findFirst().orElse(null);
	}

	private SourcePreview expensePreview(Long id) {
		return this.jdbcTemplate.query("""
				select supplier_id, project_id, tax_excluded_amount, tax_amount, tax_included_amount, status, version
				from fin_expense
				where id = ?
				""", (rs, rowNum) -> {
			if (!"CONFIRMED".equals(rs.getString("status"))) {
				return null;
			}
			Long supplierId = GeneralLedgerSupport.nullableLong(rs, "supplier_id");
			Long projectId = GeneralLedgerSupport.nullableLong(rs, "project_id");
			return new SourcePreview("DEFAULT", rs.getLong("version"), List.of(
					new FactSpec("EXPENSE", "DEBIT", rs.getBigDecimal("tax_excluded_amount"), false, false,
							projectId != null),
					new FactSpec("INPUT_VAT", "DEBIT", rs.getBigDecimal("tax_amount"), false, false, false),
					new FactSpec("EXPENSE_PAYABLE", "CREDIT", rs.getBigDecimal("tax_included_amount"), false,
							supplierId != null, projectId != null)));
		}, id).stream().findFirst().orElse(null);
	}

	private SourcePreview receiptPreview(Long id) {
		return this.jdbcTemplate.query("""
				select customer_id, amount, status, version
				from fin_receipt
				where id = ?
				""", (rs, rowNum) -> {
			if (!"POSTED".equals(rs.getString("status"))) {
				return null;
			}
			Long customerId = GeneralLedgerSupport.nullableLong(rs, "customer_id");
			BigDecimal amount = rs.getBigDecimal("amount");
			return new SourcePreview("DEFAULT", rs.getLong("version"), List.of(
					new FactSpec("BANK_RECEIPT", "DEBIT", amount, false, false, false),
					new FactSpec("ADVANCE_RECEIPT", "CREDIT", amount, customerId != null, false, false)));
		}, id).stream().findFirst().orElse(null);
	}

	private SourcePreview paymentPreview(Long id) {
		return this.jdbcTemplate.query("""
				select supplier_id, amount, status, version
				from fin_payment
				where id = ?
				""", (rs, rowNum) -> {
			if (!"POSTED".equals(rs.getString("status"))) {
				return null;
			}
			Long supplierId = GeneralLedgerSupport.nullableLong(rs, "supplier_id");
			BigDecimal amount = rs.getBigDecimal("amount");
			return new SourcePreview("DEFAULT", rs.getLong("version"), List.of(
					new FactSpec("PREPAYMENT", "DEBIT", amount, false, supplierId != null, false),
					new FactSpec("BANK_PAYMENT", "CREDIT", amount, false, false, false)));
		}, id).stream().findFirst().orElse(null);
	}

	private SourcePreview settlementPreview(Long id) {
		return this.jdbcTemplate.query("""
				select settlement_side, party_id, project_id, total_amount, status, version
				from fin_settlement_allocation
				where id = ?
				""", (rs, rowNum) -> {
			if (!"POSTED".equals(rs.getString("status"))) {
				return null;
			}
			String side = rs.getString("settlement_side");
			Long partyId = GeneralLedgerSupport.nullableLong(rs, "party_id");
			Long projectId = GeneralLedgerSupport.nullableLong(rs, "project_id");
			BigDecimal amount = rs.getBigDecimal("total_amount");
			List<FactSpec> facts = "RECEIVABLE".equals(side)
					? List.of(new FactSpec("ADVANCE_RECEIPT_CLEAR", "DEBIT", amount, partyId != null, false,
							projectId != null),
							new FactSpec("RECEIVABLE_CLEAR", "CREDIT", amount, partyId != null, false,
									projectId != null))
					: List.of(new FactSpec("PAYABLE_CLEAR", "DEBIT", amount, false, partyId != null,
							projectId != null),
							new FactSpec("PREPAYMENT_CLEAR", "CREDIT", amount, false, partyId != null,
									projectId != null));
			return new SourcePreview(side, rs.getLong("version"), facts);
		}, id).stream().findFirst().orElse(null);
	}

	private List<FactSpec> templateFacts(String sourceType, String sourceVariant) {
		BigDecimal net = new BigDecimal("100.00");
		BigDecimal tax = new BigDecimal("13.00");
		BigDecimal gross = new BigDecimal("113.00");
		BigDecimal amount = new BigDecimal("120.00");
		return switch (sourceType + "|" + sourceVariant) {
			case "SALES_INVOICE|DEFAULT" -> List.of(
					new FactSpec("SALES_RECEIVABLE", "DEBIT", gross, true, false, true),
					new FactSpec("SALES_REVENUE", "CREDIT", net, false, false, true),
					new FactSpec("OUTPUT_VAT", "CREDIT", tax, false, false, false));
			case "PURCHASE_INVOICE|DEFAULT" -> List.of(
					new FactSpec("PURCHASE_CLEARING", "DEBIT", net, false, false, true),
					new FactSpec("INPUT_VAT", "DEBIT", tax, false, false, false),
					new FactSpec("PURCHASE_PAYABLE", "CREDIT", gross, false, true, true));
			case "EXPENSE|DEFAULT" -> List.of(
					new FactSpec("EXPENSE", "DEBIT", net, false, false, true),
					new FactSpec("INPUT_VAT", "DEBIT", tax, false, false, false),
					new FactSpec("EXPENSE_PAYABLE", "CREDIT", gross, false, true, true));
			case "RECEIPT|DEFAULT" -> List.of(
					new FactSpec("BANK_RECEIPT", "DEBIT", amount, false, false, false),
					new FactSpec("ADVANCE_RECEIPT", "CREDIT", amount, true, false, false));
			case "PAYMENT|DEFAULT" -> List.of(
					new FactSpec("PREPAYMENT", "DEBIT", amount, false, true, false),
					new FactSpec("BANK_PAYMENT", "CREDIT", amount, false, false, false));
			case "SETTLEMENT_ALLOCATION|RECEIVABLE" -> List.of(
					new FactSpec("ADVANCE_RECEIPT_CLEAR", "DEBIT", amount, true, false, true),
					new FactSpec("RECEIVABLE_CLEAR", "CREDIT", amount, true, false, true));
			case "SETTLEMENT_ALLOCATION|PAYABLE" -> List.of(
					new FactSpec("PAYABLE_CLEAR", "DEBIT", amount, false, true, true),
					new FactSpec("PREPAYMENT_CLEAR", "CREDIT", amount, false, true, true));
			default -> List.of();
		};
	}

	private void copyRuleLines(Long sourceRuleId, Long targetRuleId) {
		this.jdbcTemplate.update("""
				insert into gl_posting_rule_line (
					rule_id, line_no, normalized_fact_code, direction, account_id, summary_template, created_at
				)
				select ?, line_no, normalized_fact_code, direction, account_id, summary_template, now()
				from gl_posting_rule_line
				where rule_id = ?
				order by line_no
				""", targetRuleId, sourceRuleId);
		this.jdbcTemplate.update("""
				insert into gl_posting_rule_line_aux_map (rule_line_id, dimension_id, mapping_type,
					fixed_aux_item_id, created_at)
				select target_line.id, source_map.dimension_id, source_map.mapping_type,
				       source_map.fixed_aux_item_id, now()
				from gl_posting_rule_line source_line
				join gl_posting_rule_line target_line
				  on target_line.rule_id = ?
				 and target_line.normalized_fact_code = source_line.normalized_fact_code
				 and target_line.direction = source_line.direction
				join gl_posting_rule_line_aux_map source_map on source_map.rule_line_id = source_line.id
				where source_line.rule_id = ?
				""", targetRuleId, sourceRuleId);
	}

	private void replaceRuleLines(Long ruleId, List<PostingRuleLineRequest> lines) {
		this.jdbcTemplate.update("delete from gl_posting_rule_line where rule_id = ?", ruleId);
		if (lines == null || lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.GL_RULE_INVALID);
		}
		for (PostingRuleLineRequest line : lines) {
			Long lineId = this.jdbcTemplate.queryForObject("""
					insert into gl_posting_rule_line (
						rule_id, line_no, normalized_fact_code, direction, account_id, summary_template, created_at
					)
					values (?, ?, ?, ?, ?, ?, now())
					returning id
					""", Long.class, ruleId, line.lineNo(), normalizeCode(line.normalizedFactCode()),
					normalizeCode(line.direction()), line.accountId(), GeneralLedgerSupport.requiredText(
							line.summaryTemplate(), ApiErrorCode.GL_RULE_INVALID));
			if (line.auxiliaryMappings() != null) {
				for (PostingRuleAuxMapRequest mapping : line.auxiliaryMappings()) {
					this.jdbcTemplate.update("""
							insert into gl_posting_rule_line_aux_map (
								rule_line_id, dimension_id, mapping_type, fixed_aux_item_id, created_at
							)
							values (?, ?, ?, ?, now())
							""", lineId, dimensionId(mapping.dimensionCode()), normalizeCode(mapping.mappingType()),
							mapping.fixedAuxItemId());
				}
			}
		}
	}

	private List<String> ruleAllowedActions(String status) {
		return switch (status) {
			case "DRAFT" -> List.of("UPDATE", "VALIDATE", "ACTIVATE", "DISABLE");
			case "ACTIVE" -> List.of("NEW_VERSION", "DISABLE");
			default -> List.of();
		};
	}

	private Map<String, Object> ruleDisabledReasons(String status) {
		if ("SUPERSEDED".equals(status)) {
			return Map.of("UPDATE", "已被新版本替代", "ACTIVATE", "已被新版本替代");
		}
		if ("DISABLED".equals(status)) {
			return Map.of("UPDATE", "规则已停用", "ACTIVATE", "规则已停用", "DISABLE", "规则已停用");
		}
		return Map.of();
	}

	private void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.GL_VERSION_CONFLICT);
		}
	}

	private String normalizeCode(String value) {
		return GeneralLedgerSupport.requiredText(value, ApiErrorCode.GL_RULE_INVALID).trim().toUpperCase();
	}

	private Set<Long> parseIds(String selectedIds) {
		if (selectedIds == null || selectedIds.isBlank()) {
			return Set.of();
		}
		Set<Long> result = new LinkedHashSet<>();
		for (String part : selectedIds.split(",")) {
			String text = part.trim();
			if (!text.isEmpty()) {
				result.add(Long.parseLong(text));
			}
		}
		return result;
	}

	private String selectedPredicate(Set<Long> selected, String column) {
		return selected.isEmpty() ? "" : " or " + column + " = any (?::bigint[])";
	}

	private Long[] selectedArray(Set<Long> selected) {
		return selected.toArray(Long[]::new);
	}

	private List<Object> candidateArgs(List<Object> args, Set<Long> selected) {
		List<Object> result = new ArrayList<>(args);
		int insertionPoint = Math.max(0, result.size() - 2);
		result.add(insertionPoint, selectedArray(selected));
		return result;
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

	public record AuxDimensionRequest(String code, String name, Boolean enabled, Long version, String idempotencyKey) {
	}

	public record AuxItemRequest(String code, String name, Boolean enabled, Long version, String idempotencyKey) {
	}

	public record ActionRequest(Long version, String reason, String idempotencyKey, String sourceType, Long sourceId,
			Long sourceVersion) {
	}

	public record PostingRuleRequest(String sourceType, String sourceVariant, String name, String description,
			LocalDate effectiveFrom, LocalDate effectiveTo, Long version, String idempotencyKey,
			List<PostingRuleLineRequest> lines) {
	}

	public record PostingRuleLineRequest(Integer lineNo, String normalizedFactCode, String direction, Long accountId,
			String summaryTemplate, List<PostingRuleAuxMapRequest> auxiliaryMappings) {
	}

	public record PostingRuleAuxMapRequest(String dimensionCode, String mappingType, Long fixedAuxItemId) {
	}

	record LedgerRow(Long id, String code, String name, String currency, boolean initialized, Long startPeriodId,
			String startYearMonth, Long version) {
	}

	record AccountRow(Long id, Long parentId, String code, String name, String category, String balanceDirection,
			int levelNo, boolean isLeaf, Boolean postable, boolean enabled, Long version) {
	}

	private record AuxDimensionRow(Long id, String code, String name, String dimensionType, String objectSource,
			boolean systemDefined, boolean enabled, Long version) {
	}

	private record AuxItemRow(Long id, Long dimensionId, String code, String name, boolean enabled, Long version) {
	}

	private record PostingRuleRow(Long id, String sourceType, String sourceVariant, Integer ruleVersion,
			String status, String name, LocalDate effectiveFrom, LocalDate effectiveTo, Long version) {
	}

	private record RuleLineValidationRow(Long id, String normalizedFactCode, String direction, Long accountId,
			boolean enabled, boolean postable, boolean leaf) {
	}

	private record FactSpec(String factCode, String direction, BigDecimal amount, boolean customerAvailable,
			boolean supplierAvailable, boolean projectAvailable) {

		FactSpec {
			amount = GeneralLedgerSupport.amount(amount);
		}

	}

	private record SourcePreview(String sourceVariant, Long sourceVersion, List<FactSpec> facts) {
	}

}
