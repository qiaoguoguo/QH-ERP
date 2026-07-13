package com.qherp.api.system.bom;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.master.UnitConversionAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class MaterialSubstituteAdminService {

	private static final String TARGET_TYPE = "MATERIAL_SUBSTITUTE";

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final UnitConversionAdminService unitConversionAdminService;

	public MaterialSubstituteAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			UnitConversionAdminService unitConversionAdminService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.unitConversionAdminService = unitConversionAdminService;
	}

	@Transactional(readOnly = true)
	public PageResponse<MaterialSubstituteRecord> list(String keyword, String status, Long mainMaterialId,
			Long substituteMaterialId, String scopeType, Long scopeId, LocalDate effectiveDate, int page,
			int pageSize) {
		QueryParts queryParts = queryParts(keyword, status, mainMaterialId, substituteMaterialId, scopeType, scopeId,
				effectiveDate);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mst_material_substitute s
				join mst_material mainm on mainm.id = s.main_material_id
				join mst_material subm on subm.id = s.substitute_material_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<MaterialSubstituteRecord> items = this.jdbcTemplate.query("""
				select s.id, s.main_material_id, mainm.code as main_material_code, mainm.name as main_material_name,
				       s.substitute_material_id, subm.code as substitute_material_code,
				       subm.name as substitute_material_name, s.scope_type, s.scope_id, s.priority,
				       s.substitute_rate, s.effective_from, s.effective_to, s.status, s.remark,
				       s.created_at, s.updated_at, s.version
				from mst_material_substitute s
				join mst_material mainm on mainm.id = s.main_material_id
				join mst_material subm on subm.id = s.substitute_material_id
				%s
				order by s.updated_at desc, s.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapRecord, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public MaterialSubstituteRecord get(Long id) {
		return record(id).orElseThrow(this::notFound);
	}

	@Transactional
	public MaterialSubstituteRecord create(MaterialSubstituteRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedSubstitute validated = validateRequest(request);
		if ("ENABLED".equals(validated.status())) {
			validatePriorityConflict(null, validated);
		}
		OffsetDateTime now = OffsetDateTime.now();
		Long id = this.jdbcTemplate.queryForObject("""
				insert into mst_material_substitute (
					main_material_id, substitute_material_id, scope_type, scope_id, priority, substitute_rate,
					effective_from, effective_to, status, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, validated.mainMaterialId(), validated.substituteMaterialId(), validated.scopeType(),
				validated.scopeId(), validated.priority(), validated.substituteRate(), validated.effectiveFrom(),
				validated.effectiveTo(), validated.status(), blankToNull(request.remark()), operator.username(), now,
				operator.username(), now);
		this.auditService.record(operator, "MATERIAL_SUBSTITUTE_CREATE", TARGET_TYPE, id, Long.toString(id),
				servletRequest);
		return get(id);
	}

	@Transactional
	public MaterialSubstituteRecord update(Long id, MaterialSubstituteRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		MaterialSubstituteRecord current = get(id);
		requireVersion(current.version(), request.version());
		ValidatedSubstitute validated = validateRequest(request);
		if ("ENABLED".equals(validated.status())) {
			validatePriorityConflict(id, validated);
		}
		int updated = this.jdbcTemplate.update("""
				update mst_material_substitute
				set main_material_id = ?, substitute_material_id = ?, scope_type = ?, scope_id = ?, priority = ?,
				    substitute_rate = ?, effective_from = ?, effective_to = ?, status = ?, remark = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ?
				""", validated.mainMaterialId(), validated.substituteMaterialId(), validated.scopeType(),
				validated.scopeId(), validated.priority(), validated.substituteRate(), validated.effectiveFrom(),
				validated.effectiveTo(), validated.status(), blankToNull(request.remark()), operator.username(),
				OffsetDateTime.now(), id, current.version());
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.MATERIAL_SUBSTITUTE_CONCURRENT_MODIFICATION);
		}
		this.auditService.record(operator, "MATERIAL_SUBSTITUTE_UPDATE", TARGET_TYPE, id, Long.toString(id),
				servletRequest);
		return get(id);
	}

	@Transactional
	public MaterialSubstituteRecord enable(Long id, VersionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		MaterialSubstituteRecord current = get(id);
		requireVersion(current.version(), request == null ? null : request.version());
		ValidatedSubstitute validated = new ValidatedSubstitute(current.mainMaterialId(), current.substituteMaterialId(),
				current.scopeType(), current.scopeId(), current.priority(), new BigDecimal(current.substituteRate()),
				current.effectiveFrom(), current.effectiveTo(), "ENABLED");
		validatePriorityConflict(id, validated);
		int updated = this.jdbcTemplate.update("""
				update mst_material_substitute
				set status = 'ENABLED', updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ?
				""", operator.username(), OffsetDateTime.now(), id, current.version());
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.MATERIAL_SUBSTITUTE_CONCURRENT_MODIFICATION);
		}
		this.auditService.record(operator, "MATERIAL_SUBSTITUTE_ENABLE", TARGET_TYPE, id, Long.toString(id),
				servletRequest);
		return get(id);
	}

	@Transactional
	public MaterialSubstituteRecord disable(Long id, VersionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		MaterialSubstituteRecord current = get(id);
		requireVersion(current.version(), request == null ? null : request.version());
		int updated = this.jdbcTemplate.update("""
				update mst_material_substitute
				set status = 'DISABLED', updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ?
				""", operator.username(), OffsetDateTime.now(), id, current.version());
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.MATERIAL_SUBSTITUTE_CONCURRENT_MODIFICATION);
		}
		this.auditService.record(operator, "MATERIAL_SUBSTITUTE_DISABLE", TARGET_TYPE, id, Long.toString(id),
				servletRequest);
		return get(id);
	}

	@Transactional(readOnly = true)
	public UnitConversionAdminService.CandidatePage materialCandidates(String keyword, String status,
			String selectedIds, int page, int pageSize) {
		return this.unitConversionAdminService.materialCandidates(keyword, status, selectedIds, page, pageSize);
	}

	@Transactional(readOnly = true)
	public UnitConversionAdminService.CandidatePage bomCandidates(String keyword, Long parentMaterialId,
			String selectedIds, int page, int pageSize) {
		QueryParts queryParts = bomCandidateQueryParts(keyword, parentMaterialId);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_bom b
				join mst_material p on p.id = b.parent_material_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<UnitConversionAdminService.CandidateItem> items = this.jdbcTemplate.query("""
				select b.id, b.bom_code, b.name, b.status, b.version_code, p.code as parent_material_code
				from mfg_bom b
				join mst_material p on p.id = b.parent_material_id
				%s
				order by b.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapBomCandidate, args.toArray());
		List<Long> selected = parseIds(selectedIds);
		List<UnitConversionAdminService.CandidateItem> selectedItems = selected.isEmpty() ? List.of()
				: this.jdbcTemplate.query("""
						select b.id, b.bom_code, b.name, b.status, b.version_code, p.code as parent_material_code
						from mfg_bom b
						join mst_material p on p.id = b.parent_material_id
						where b.id in (%s)
						order by b.id desc
						""".formatted(placeholders(selected.size())), this::mapBomCandidate, selected.toArray());
		return new UnitConversionAdminService.CandidatePage(items, selectedItems, Math.max(page, 1), limit(pageSize),
				total, totalPages(total, limit(pageSize)));
	}

	private ValidatedSubstitute validateRequest(MaterialSubstituteRequest request) {
		if (request.mainMaterialId() == null || request.substituteMaterialId() == null
				|| request.mainMaterialId().equals(request.substituteMaterialId())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_SUBSTITUTE_SELF_REFERENCE);
		}
		validateEnabledMaterial(request.mainMaterialId());
		validateEnabledMaterial(request.substituteMaterialId());
		String scopeType = parseScopeType(request.scopeType());
		validateScope(scopeType, request.scopeId());
		if (request.priority() == null || request.priority() <= 0 || request.substituteRate() == null
				|| request.substituteRate().compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateEffectiveRange(request.effectiveFrom(), request.effectiveTo());
		return new ValidatedSubstitute(request.mainMaterialId(), request.substituteMaterialId(), scopeType,
				request.scopeId(), request.priority(), request.substituteRate().setScale(6, RoundingMode.HALF_UP),
				request.effectiveFrom(), request.effectiveTo(), parseStatus(request.status()));
	}

	private void validateEnabledMaterial(Long materialId) {
		String status = this.jdbcTemplate.query("select status from mst_material where id = ?",
				(rs, rowNum) -> rs.getString("status"), materialId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_REFERENCE_INVALID);
		}
	}

	private void validateScope(String scopeType, Long scopeId) {
		if ("GLOBAL".equals(scopeType)) {
			if (scopeId != null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			return;
		}
		if (scopeId == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String table = "BOM".equals(scopeType) ? "mfg_bom" : "mst_material";
		Long count = this.jdbcTemplate.queryForObject("select count(*) from " + table + " where id = ?", Long.class,
				scopeId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_REFERENCE_INVALID);
		}
	}

	private void validatePriorityConflict(Long currentId, ValidatedSubstitute validated) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mst_material_substitute
				where main_material_id = ?
				and scope_type = ?
				and coalesce(scope_id, -1) = coalesce(cast(? as bigint), -1)
				and priority = ?
				and status = 'ENABLED'
				and (cast(? as bigint) is null or id <> cast(? as bigint))
				and coalesce(effective_from, date '0001-01-01') <= coalesce(cast(? as date), date '9999-12-31')
				and coalesce(cast(? as date), date '0001-01-01') <= coalesce(effective_to, date '9999-12-31')
				""", Long.class, validated.mainMaterialId(), validated.scopeType(), validated.scopeId(),
				validated.priority(), currentId, currentId, validated.effectiveTo(), validated.effectiveFrom());
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.MATERIAL_SUBSTITUTE_PRIORITY_CONFLICT);
		}
	}

	private void validateEffectiveRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
		if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
			throw new BusinessException(ApiErrorCode.MATERIAL_SUBSTITUTE_EFFECTIVE_OVERLAP);
		}
	}

	private Optional<MaterialSubstituteRecord> record(Long id) {
		return this.jdbcTemplate.query("""
				select s.id, s.main_material_id, mainm.code as main_material_code, mainm.name as main_material_name,
				       s.substitute_material_id, subm.code as substitute_material_code,
				       subm.name as substitute_material_name, s.scope_type, s.scope_id, s.priority,
				       s.substitute_rate, s.effective_from, s.effective_to, s.status, s.remark,
				       s.created_at, s.updated_at, s.version
				from mst_material_substitute s
				join mst_material mainm on mainm.id = s.main_material_id
				join mst_material subm on subm.id = s.substitute_material_id
				where s.id = ?
				""", this::mapRecord, id).stream().findFirst();
	}

	private MaterialSubstituteRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
		return new MaterialSubstituteRecord(rs.getLong("id"), rs.getLong("main_material_id"),
				rs.getString("main_material_code"), rs.getString("main_material_name"),
				rs.getLong("substitute_material_id"), rs.getString("substitute_material_code"),
				rs.getString("substitute_material_name"), rs.getString("scope_type"),
				rs.getObject("scope_id", Long.class), rs.getInt("priority"),
				decimalString(rs.getBigDecimal("substitute_rate")),
				rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
				rs.getString("status"), rs.getString("remark"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private QueryParts bomCandidateQueryParts(String keyword, Long parentMaterialId) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(b.bom_code ilike ? or b.name ilike ? or b.version_code ilike ? or p.code ilike ? or p.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (parentMaterialId != null) {
			conditions.add("b.parent_material_id = ?");
			args.add(parentMaterialId);
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private UnitConversionAdminService.CandidateItem mapBomCandidate(ResultSet rs, int rowNum) throws SQLException {
		String status = rs.getString("status");
		String disabledReason = "ENABLED".equals(status) ? null : "BOM必须已发布";
		String summary = rs.getString("parent_material_code") + "/" + rs.getString("version_code");
		return new UnitConversionAdminService.CandidateItem(rs.getLong("id"), rs.getString("bom_code"),
				rs.getString("name"), status, disabledReason != null, disabledReason, summary);
	}

	private QueryParts queryParts(String keyword, String status, Long mainMaterialId, Long substituteMaterialId,
			String scopeType, Long scopeId, LocalDate effectiveDate) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(mainm.code ilike ? or mainm.name ilike ? or subm.code ilike ? or subm.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (hasText(status)) {
			conditions.add("s.status = ?");
			args.add(parseStatus(status));
		}
		if (mainMaterialId != null) {
			conditions.add("s.main_material_id = ?");
			args.add(mainMaterialId);
		}
		if (substituteMaterialId != null) {
			conditions.add("s.substitute_material_id = ?");
			args.add(substituteMaterialId);
		}
		if (hasText(scopeType)) {
			conditions.add("s.scope_type = ?");
			args.add(parseScopeType(scopeType));
		}
		if (scopeId != null) {
			conditions.add("s.scope_id = ?");
			args.add(scopeId);
		}
		if (effectiveDate != null) {
			conditions.add("coalesce(s.effective_from, date '0001-01-01') <= ?");
			conditions.add("? <= coalesce(s.effective_to, date '9999-12-31')");
			args.add(effectiveDate);
			args.add(effectiveDate);
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private String parseScopeType(String value) {
		return switch (value) {
			case "GLOBAL", "PARENT_MATERIAL", "BOM" -> value;
			default -> throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		};
	}

	private String parseStatus(String value) {
		return switch (hasText(value) ? value : "ENABLED") {
			case "ENABLED", "DISABLED" -> hasText(value) ? value : "ENABLED";
			default -> throw new BusinessException(ApiErrorCode.MASTER_DATA_INVALID_STATUS);
		};
	}

	private BusinessException notFound() {
		return new BusinessException(ApiErrorCode.MATERIAL_SUBSTITUTE_NOT_FOUND);
	}

	private void requireVersion(Long currentVersion, Long requestVersion) {
		if (requestVersion == null || !currentVersion.equals(requestVersion)) {
			throw new BusinessException(ApiErrorCode.MATERIAL_SUBSTITUTE_CONCURRENT_MODIFICATION);
		}
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static int totalPages(long total, int pageSize) {
		return (int) Math.ceil(total / (double) pageSize);
	}

	private static String decimalString(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	private static List<Long> parseIds(String selectedIds) {
		if (!hasText(selectedIds)) {
			return List.of();
		}
		return Arrays.stream(selectedIds.split(","))
			.map(String::trim)
			.filter(MaterialSubstituteAdminService::hasText)
			.map(Long::parseLong)
			.toList();
	}

	private static String placeholders(int size) {
		return String.join(",", java.util.Collections.nCopies(size, "?"));
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record MaterialSubstituteRequest(@NotNull Long mainMaterialId, @NotNull Long substituteMaterialId,
			@NotNull String scopeType, Long scopeId, @NotNull Integer priority, @NotNull BigDecimal substituteRate,
			LocalDate effectiveFrom, LocalDate effectiveTo, String status, String remark, Long version) {
	}

	public record VersionRequest(Long version) {
	}

	public record MaterialSubstituteRecord(Long id, Long mainMaterialId, String mainMaterialCode,
			String mainMaterialName, Long substituteMaterialId, String substituteMaterialCode,
			String substituteMaterialName, String scopeType, Long scopeId, Integer priority,
			String substituteRate, LocalDate effectiveFrom, LocalDate effectiveTo, String status, String remark,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

	private record ValidatedSubstitute(Long mainMaterialId, Long substituteMaterialId, String scopeType, Long scopeId,
			Integer priority, BigDecimal substituteRate, LocalDate effectiveFrom, LocalDate effectiveTo,
			String status) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
