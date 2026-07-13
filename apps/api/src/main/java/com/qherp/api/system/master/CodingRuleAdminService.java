package com.qherp.api.system.master;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class CodingRuleAdminService {

	private static final String TARGET_TYPE = "CODING_RULE";

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public CodingRuleAdminService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<CodingRuleRecord> list(String keyword, String objectType, String status, int page,
			int pageSize) {
		QueryParts queryParts = queryParts(keyword, objectType, status);
		long total = this.jdbcTemplate.queryForObject(
				"select count(*) from sys_coding_rule r " + queryParts.where(), Long.class,
				queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<CodingRuleRecord> items = this.jdbcTemplate.query("""
				select id, rule_code, name, object_type, prefix, date_pattern, serial_length, reset_cycle,
				       next_serial_no, status, last_generated_code, last_generated_at, last_reset_key,
				       remark, created_at, updated_at, version
				from sys_coding_rule r
				%s
				order by updated_at desc, id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapRecord, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public CodingRuleRecord get(Long id) {
		return record(id);
	}

	@Transactional
	public CodingRuleRecord create(CodingRulePayload request, CurrentUser operator, HttpServletRequest servletRequest) {
		ValidatedRule validated = validatePayload(request);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into sys_coding_rule (
						rule_code, name, object_type, prefix, date_pattern, serial_length, reset_cycle,
						next_serial_no, status, remark, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, request.ruleCode(), request.name(), validated.objectType(), request.prefix(),
					validated.datePattern(), validated.serialLength(), validated.resetCycle(),
					validated.nextSerialNo(), validated.status(), blankToNull(request.remark()), operator.username(),
					now, operator.username(), now);
			this.auditService.record(operator, "CODING_RULE_CREATE", TARGET_TYPE, id, request.ruleCode(),
					servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateRuleException(exception);
		}
	}

	@Transactional
	public CodingRuleRecord update(Long id, CodingRulePayload request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		CodingRuleRecord current = record(id);
		requireVersion(current.version(), request.version(), ApiErrorCode.VERSION_CONFLICT);
		ValidatedRule validated = validatePayload(request);
		try {
			int updated = this.jdbcTemplate.update("""
					update sys_coding_rule
					set rule_code = ?, name = ?, object_type = ?, prefix = ?, date_pattern = ?, serial_length = ?,
					    reset_cycle = ?, next_serial_no = ?, status = ?, remark = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where id = ? and version = ?
					""", request.ruleCode(), request.name(), validated.objectType(), request.prefix(),
					validated.datePattern(), validated.serialLength(), validated.resetCycle(),
					validated.nextSerialNo(), validated.status(), blankToNull(request.remark()), operator.username(),
					OffsetDateTime.now(), id, request.version());
			if (updated == 0) {
				throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
			}
			this.auditService.record(operator, "CODING_RULE_UPDATE", TARGET_TYPE, id, request.ruleCode(),
					servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateRuleException(exception);
		}
	}

	@Transactional
	public CodingRuleRecord enable(Long id, VersionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		CodingRuleRecord current = record(id);
		requireVersion(current.version(), request.version(), ApiErrorCode.VERSION_CONFLICT);
		try {
			int updated = this.jdbcTemplate.update("""
					update sys_coding_rule
					set status = 'ENABLED', updated_by = ?, updated_at = ?, version = version + 1
					where id = ? and version = ?
					""", operator.username(), OffsetDateTime.now(), id, request.version());
			if (updated == 0) {
				throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
			}
			this.auditService.record(operator, "CODING_RULE_ENABLE", TARGET_TYPE, id, current.ruleCode(),
					servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateRuleException(exception);
		}
	}

	@Transactional
	public CodingRuleRecord disable(Long id, VersionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		CodingRuleRecord current = record(id);
		requireVersion(current.version(), request.version(), ApiErrorCode.VERSION_CONFLICT);
		int updated = this.jdbcTemplate.update("""
				update sys_coding_rule
				set status = 'DISABLED', updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ?
				""", operator.username(), OffsetDateTime.now(), id, request.version());
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
		this.auditService.record(operator, "CODING_RULE_DISABLE", TARGET_TYPE, id, current.ruleCode(),
				servletRequest);
		return get(id);
	}

	@Transactional
	public GeneratedCode generate(GenerateRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		return generateForObject(request.objectType(), request.contextDate(), operator, servletRequest);
	}

	@Transactional
	public GeneratedCode generateForObject(String objectType, LocalDate contextDate, CurrentUser operator,
			HttpServletRequest servletRequest) {
		String parsedObjectType = parseObjectType(objectType);
		requireObjectCreatePermission(parsedObjectType, operator);
		CodingRuleRow row = enabledRuleForUpdate(parsedObjectType);
		LocalDate date = contextDate == null ? LocalDate.now() : contextDate;
		String resetKey = resetKey(row.resetCycle(), date);
		long serialNo = row.nextSerialNo();
		if (hasText(resetKey) && !resetKey.equals(row.lastResetKey())) {
			serialNo = 1L;
		}
		String generatedCode = row.prefix() + datePart(row.datePattern(), date) + serial(serialNo, row.serialLength());
		if (targetCodeExists(parsedObjectType, generatedCode)) {
			throw new BusinessException(ApiErrorCode.CODING_RULE_GENERATE_CONFLICT);
		}
		OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
		this.jdbcTemplate.update("""
				update sys_coding_rule
				set next_serial_no = ?, last_generated_code = ?, last_generated_at = ?, last_reset_key = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", serialNo + 1, generatedCode, generatedAt, resetKey, operator.username(), generatedAt, row.id());
		this.auditService.record(operator, "CODING_RULE_GENERATE", TARGET_TYPE, row.id(),
				"rule=%s;objectType=%s;generatedCode=%s".formatted(row.ruleCode(), parsedObjectType,
						generatedCode),
				servletRequest);
		return new GeneratedCode(row.id(), parsedObjectType, generatedCode, serialNo, row.prefix(),
				row.datePattern(), row.serialLength(), resetKey, generatedAt);
	}

	private QueryParts queryParts(String keyword, String objectType, String status) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(r.rule_code ilike ? or r.name ilike ? or r.prefix ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (hasText(objectType)) {
			conditions.add("r.object_type = ?");
			args.add(parseObjectType(objectType));
		}
		if (hasText(status)) {
			conditions.add("r.status = ?");
			args.add(parseStatus(status));
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private CodingRuleRecord record(Long id) {
		return this.jdbcTemplate.query("""
				select id, rule_code, name, object_type, prefix, date_pattern, serial_length, reset_cycle,
				       next_serial_no, status, last_generated_code, last_generated_at, last_reset_key,
				       remark, created_at, updated_at, version
				from sys_coding_rule
				where id = ?
				""", this::mapRecord, id).stream().findFirst().orElseThrow(this::notFound);
	}

	private CodingRuleRecord mapRecord(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new CodingRuleRecord(rs.getLong("id"), rs.getString("rule_code"), rs.getString("name"),
				rs.getString("object_type"), rs.getString("prefix"), rs.getString("date_pattern"),
				rs.getInt("serial_length"), rs.getString("reset_cycle"), rs.getLong("next_serial_no"),
				rs.getString("status"), rs.getString("last_generated_code"),
				rs.getObject("last_generated_at", OffsetDateTime.class), rs.getString("last_reset_key"),
				rs.getString("remark"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private CodingRuleRow enabledRuleForUpdate(String objectType) {
		List<CodingRuleRow> rows = this.jdbcTemplate.query("""
				select id, rule_code, object_type, prefix, date_pattern, serial_length, reset_cycle,
				       next_serial_no, last_reset_key
				from sys_coding_rule
				where object_type = ?
				and status = 'ENABLED'
				for update
				""", (rs, rowNum) -> new CodingRuleRow(rs.getLong("id"), rs.getString("rule_code"),
				rs.getString("object_type"), rs.getString("prefix"), rs.getString("date_pattern"),
				rs.getInt("serial_length"), rs.getString("reset_cycle"), rs.getLong("next_serial_no"),
				rs.getString("last_reset_key")), objectType);
		if (rows.isEmpty()) {
			throw new BusinessException(ApiErrorCode.CODING_RULE_DISABLED);
		}
		if (rows.size() > 1) {
			throw new BusinessException(ApiErrorCode.CODING_RULE_DUPLICATE_ENABLED);
		}
		return rows.get(0);
	}

	private boolean targetCodeExists(String objectType, String code) {
		String sql = switch (objectType) {
			case "MATERIAL" -> "select count(*) from mst_material where code = ?";
			case "CUSTOMER" -> "select count(*) from mst_customer where code = ?";
			case "SUPPLIER" -> "select count(*) from mst_supplier where code = ?";
			case "BOM" -> "select count(*) from mfg_bom where bom_code = ?";
			case "BOM_ECO" -> "select count(*) from mfg_bom_engineering_change where eco_no = ?";
			default -> throw new BusinessException(ApiErrorCode.CODING_RULE_OBJECT_TYPE_INVALID);
		};
		Long count = this.jdbcTemplate.queryForObject(sql, Long.class, code);
		return count != null && count > 0;
	}

	private void requireObjectCreatePermission(String objectType, CurrentUser operator) {
		String permissionCode = switch (objectType) {
			case "MATERIAL" -> "master:material:create";
			case "CUSTOMER" -> "master:customer:create";
			case "SUPPLIER" -> "master:supplier:create";
			case "BOM" -> "material:bom:create";
			case "BOM_ECO" -> "material:bom-eco:create";
			default -> throw new BusinessException(ApiErrorCode.CODING_RULE_OBJECT_TYPE_INVALID);
		};
		if (operator == null || !operator.permissions().contains(permissionCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private ValidatedRule validatePayload(CodingRulePayload request) {
		String objectType = parseObjectType(request.objectType());
		String datePattern = parseDatePattern(request.datePattern());
		String resetCycle = parseResetCycle(request.resetCycle());
		String status = parseStatus(request.status());
		if (request.serialLength() == null || request.serialLength() <= 0 || request.serialLength() > 12
				|| request.nextSerialNo() == null || request.nextSerialNo() <= 0 || !hasText(request.prefix())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return new ValidatedRule(objectType, datePattern, request.serialLength(), resetCycle, request.nextSerialNo(),
				status);
	}

	private String parseObjectType(String value) {
		return switch (value) {
			case "MATERIAL", "CUSTOMER", "SUPPLIER", "BOM", "BOM_ECO" -> value;
			default -> throw new BusinessException(ApiErrorCode.CODING_RULE_OBJECT_TYPE_INVALID);
		};
	}

	private String parseDatePattern(String value) {
		return switch (value) {
			case "NONE", "YYYY", "YYYYMM", "YYYYMMDD" -> value;
			default -> throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		};
	}

	private String parseResetCycle(String value) {
		return switch (value) {
			case "NEVER", "YEAR", "MONTH", "DAY" -> value;
			default -> throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		};
	}

	private String parseStatus(String value) {
		return switch (hasText(value) ? value : "ENABLED") {
			case "ENABLED", "DISABLED" -> hasText(value) ? value : "ENABLED";
			default -> throw new BusinessException(ApiErrorCode.MASTER_DATA_INVALID_STATUS);
		};
	}

	private BusinessException duplicateRuleException(DuplicateKeyException exception) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		if (message != null && message.contains("uk_sys_coding_rule_enabled_object")) {
			return new BusinessException(ApiErrorCode.CODING_RULE_DUPLICATE_ENABLED);
		}
		return new BusinessException(ApiErrorCode.MASTER_DATA_CODE_EXISTS);
	}

	private String datePart(String datePattern, LocalDate date) {
		return switch (datePattern) {
			case "YYYY" -> date.format(DateTimeFormatter.ofPattern("yyyy"));
			case "YYYYMM" -> date.format(DateTimeFormatter.ofPattern("yyyyMM"));
			case "YYYYMMDD" -> date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
			default -> "";
		};
	}

	private String resetKey(String resetCycle, LocalDate date) {
		return switch (resetCycle) {
			case "YEAR" -> date.format(DateTimeFormatter.ofPattern("yyyy"));
			case "MONTH" -> date.format(DateTimeFormatter.ofPattern("yyyyMM"));
			case "DAY" -> date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
			default -> null;
		};
	}

	private String serial(long serialNo, int serialLength) {
		String value = Long.toString(serialNo);
		if (value.length() >= serialLength) {
			return value;
		}
		return "0".repeat(serialLength - value.length()) + value;
	}

	private void requireVersion(Long currentVersion, Long requestVersion, ApiErrorCode errorCode) {
		if (requestVersion == null || !currentVersion.equals(requestVersion)) {
			throw new BusinessException(errorCode);
		}
	}

	private BusinessException notFound() {
		return new BusinessException(ApiErrorCode.CODING_RULE_NOT_FOUND);
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record CodingRulePayload(@NotBlank String ruleCode, @NotBlank String name, @NotBlank String objectType,
			@NotBlank String prefix, @NotBlank String datePattern, @NotNull Integer serialLength,
			@NotBlank String resetCycle, @NotNull Long nextSerialNo, String status, String remark, Long version) {
	}

	public record VersionRequest(@NotNull Long version) {
	}

	public record GenerateRequest(@NotBlank String objectType, LocalDate contextDate) {
	}

	public record CodingRuleRecord(Long id, String ruleCode, String name, String objectType, String prefix,
			String datePattern, Integer serialLength, String resetCycle, Long nextSerialNo, String status,
			String lastGeneratedCode, OffsetDateTime lastGeneratedAt, String lastResetKey, String remark,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

	public record GeneratedCode(Long ruleId, String objectType, String generatedCode, Long serialNo, String prefix,
			String datePattern, Integer serialLength, String resetKey, OffsetDateTime generatedAt) {
	}

	private record ValidatedRule(String objectType, String datePattern, Integer serialLength, String resetCycle,
			Long nextSerialNo, String status) {
	}

	private record CodingRuleRow(Long id, String ruleCode, String objectType, String prefix, String datePattern,
			Integer serialLength, String resetCycle, Long nextSerialNo, String lastResetKey) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
