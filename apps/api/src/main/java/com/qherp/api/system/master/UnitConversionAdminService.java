package com.qherp.api.system.master;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
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

@Service
public class UnitConversionAdminService {

	private static final String TARGET_TYPE = "UNIT_CONVERSION";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public UnitConversionAdminService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<UnitConversionRecord> list(String keyword, Long materialId, Long businessUnitId, String status,
			LocalDate effectiveDate, int page, int pageSize) {
		QueryParts queryParts = queryParts(keyword, materialId, businessUnitId, status, effectiveDate);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mst_material_unit_conversion c
				join mst_material m on m.id = c.material_id
				join mst_unit bu on bu.id = c.business_unit_id
				join mst_unit baseu on baseu.id = c.base_unit_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<UnitConversionRecord> items = this.jdbcTemplate.query("""
				select c.id, c.material_id, m.code as material_code, m.name as material_name,
				       c.base_unit_id, baseu.name as base_unit_name, c.business_unit_id,
				       bu.name as business_unit_name, c.conversion_rate, c.quantity_scale,
				       c.rounding_mode, c.effective_from, c.effective_to, c.status, c.remark,
				       c.created_at, c.updated_at, c.version
				from mst_material_unit_conversion c
				join mst_material m on m.id = c.material_id
				join mst_unit bu on bu.id = c.business_unit_id
				join mst_unit baseu on baseu.id = c.base_unit_id
				%s
				order by c.updated_at desc, c.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapRecord, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public UnitConversionRecord get(Long id) {
		return record(id);
	}

	@Transactional
	public UnitConversionRecord create(UnitConversionPayload request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedConversion validated = validatePayload(request, null, "ENABLED");
		OffsetDateTime now = OffsetDateTime.now();
		Long id = this.jdbcTemplate.queryForObject("""
				insert into mst_material_unit_conversion (
					material_id, base_unit_id, business_unit_id, conversion_rate, quantity_scale, rounding_mode,
					effective_from, effective_to, status, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, validated.material().id(), validated.material().baseUnitId(),
				validated.businessUnit().id(), validated.conversionRate(), validated.quantityScale(),
				validated.roundingMode().name(), validated.effectiveFrom(), validated.effectiveTo(), "ENABLED",
				blankToNull(request.remark()), operator.username(), now, operator.username(), now);
		this.auditService.record(operator, "UNIT_CONVERSION_CREATE", TARGET_TYPE, id,
				validated.material().code() + "/" + validated.businessUnit().code(), servletRequest);
		return get(id);
	}

	@Transactional
	public UnitConversionRecord update(Long id, UnitConversionPayload request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ConversionRow current = conversionRow(id);
		requireVersion(current.version(), request.version(), ApiErrorCode.VERSION_CONFLICT);
		ValidatedConversion validated = validatePayload(request, id, current.status());
		int updated = this.jdbcTemplate.update("""
				update mst_material_unit_conversion
				set material_id = ?, base_unit_id = ?, business_unit_id = ?, conversion_rate = ?, quantity_scale = ?,
				    rounding_mode = ?, effective_from = ?, effective_to = ?, remark = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				and version = ?
				""", validated.material().id(), validated.material().baseUnitId(), validated.businessUnit().id(),
				validated.conversionRate(), validated.quantityScale(), validated.roundingMode().name(),
				validated.effectiveFrom(), validated.effectiveTo(), blankToNull(request.remark()), operator.username(),
				OffsetDateTime.now(), id, request.version());
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
		this.auditService.record(operator, "UNIT_CONVERSION_UPDATE", TARGET_TYPE, id,
				validated.material().code() + "/" + validated.businessUnit().code(), servletRequest);
		return get(id);
	}

	@Transactional
	public UnitConversionRecord enable(Long id, VersionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ConversionRow current = conversionRow(id);
		requireVersion(current.version(), request == null ? null : request.version(), ApiErrorCode.VERSION_CONFLICT);
		validateNoOverlap(id, current.materialId(), current.businessUnitId(), current.effectiveFrom(),
				current.effectiveTo());
		changeStatus(id, current.version(), "ENABLED", "UNIT_CONVERSION_ENABLE", operator, servletRequest);
		return get(id);
	}

	@Transactional
	public UnitConversionRecord disable(Long id, VersionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ConversionRow current = conversionRow(id);
		requireVersion(current.version(), request == null ? null : request.version(), ApiErrorCode.VERSION_CONFLICT);
		changeStatus(id, current.version(), "DISABLED", "UNIT_CONVERSION_DISABLE", operator, servletRequest);
		return get(id);
	}

	@Transactional(readOnly = true)
	public ConversionResult convert(ConversionRequest request) {
		if (request == null || request.materialId() == null || request.businessUnitId() == null
				|| request.businessQuantity() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		MaterialRef material = materialRef(request.materialId()).orElseThrow(this::referenceInvalid);
		if (material.baseUnitId().equals(request.businessUnitId())) {
			BigDecimal quantity = request.businessQuantity().setScale(unitPrecision(material.baseUnitId()),
					RoundingMode.HALF_UP);
			return new ConversionResult(null, material.id(), request.businessUnitId(),
					decimal(request.businessQuantity(), request.businessQuantity().scale()), material.baseUnitId(),
					decimal(quantity, quantity.scale()), "1.000000", unitPrecision(material.baseUnitId()), "HALF_UP");
		}
		ConversionRow row = effectiveConversion(material.id(), request.businessUnitId(), request.businessDate())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.UNIT_CONVERSION_REQUIRED));
		BigDecimal baseQuantity = request.businessQuantity()
			.multiply(row.conversionRate())
			.setScale(row.quantityScale(), roundingMode(row.roundingMode()));
		return new ConversionResult(row.id(), material.id(), request.businessUnitId(),
				decimal(request.businessQuantity(), request.businessQuantity().scale()), row.baseUnitId(),
				decimal(baseQuantity, row.quantityScale()), decimal(row.conversionRate(), 6), row.quantityScale(),
				row.roundingMode());
	}

	@Transactional(readOnly = true)
	public CandidatePage materialCandidates(String keyword, String status, String selectedIds, int page, int pageSize) {
		return candidates("mst_material", keyword, status, selectedIds, page, pageSize, "unit_id");
	}

	@Transactional(readOnly = true)
	public CandidatePage unitCandidates(String keyword, String status, String selectedIds, int page, int pageSize) {
		return candidates("mst_unit", keyword, status, selectedIds, page, pageSize, "precision_scale");
	}

	public ConversionSnapshot conversionSnapshot(Long materialId, Long businessUnitId, BigDecimal businessQuantity,
			LocalDate businessDate) {
		MaterialRef material = materialRef(materialId).orElseThrow(this::referenceInvalid);
		if (businessUnitId == null) {
			businessUnitId = material.baseUnitId();
		}
		if (material.baseUnitId().equals(businessUnitId)) {
			BigDecimal baseQuantity = businessQuantity.setScale(6, RoundingMode.HALF_UP);
			return new ConversionSnapshot(null, businessUnitId, businessQuantity, material.baseUnitId(), baseQuantity,
					BigDecimal.ONE, 6, "HALF_UP", "BASE_UNIT");
		}
		ConversionRow row = effectiveConversion(materialId, businessUnitId, businessDate)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.UNIT_CONVERSION_REQUIRED));
		BigDecimal baseQuantity = businessQuantity.multiply(row.conversionRate())
			.setScale(row.quantityScale(), roundingMode(row.roundingMode()));
		return new ConversionSnapshot(row.id(), businessUnitId, businessQuantity, row.baseUnitId(), baseQuantity,
				row.conversionRate(), row.quantityScale(), row.roundingMode(), "CONVERTED_BUSINESS_UNIT");
	}

	private ValidatedConversion validatePayload(UnitConversionPayload request, Long currentId, String status) {
		if (request == null || request.materialId() == null || request.businessUnitId() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		MaterialRef material = materialRef(request.materialId()).orElseThrow(this::referenceInvalid);
		UnitRef businessUnit = unitRef(request.businessUnitId()).orElseThrow(this::referenceInvalid);
		if (!"ENABLED".equals(material.status()) || !"ENABLED".equals(businessUnit.status())) {
			throw referenceInvalid();
		}
		if (material.baseUnitId().equals(businessUnit.id())) {
			throw new BusinessException(ApiErrorCode.UNIT_CONVERSION_REQUIRED);
		}
		BigDecimal conversionRate = request.conversionRate();
		RoundingMode roundingMode = parseRoundingMode(request.roundingMode());
		if (conversionRate == null || conversionRate.compareTo(ZERO) <= 0 || request.quantityScale() == null
				|| request.quantityScale() < 0 || request.quantityScale() > 6) {
			throw new BusinessException(ApiErrorCode.UNIT_CONVERSION_RATE_INVALID);
		}
		if (request.effectiveFrom() != null && request.effectiveTo() != null
				&& request.effectiveFrom().isAfter(request.effectiveTo())) {
			throw new BusinessException(ApiErrorCode.UNIT_CONVERSION_DATE_RANGE_INVALID);
		}
		if ("ENABLED".equals(status)) {
			validateNoOverlap(currentId, material.id(), businessUnit.id(), request.effectiveFrom(),
					request.effectiveTo());
		}
		return new ValidatedConversion(material, businessUnit, conversionRate, request.quantityScale(), roundingMode,
				request.effectiveFrom(), request.effectiveTo());
	}

	private void validateNoOverlap(Long currentId, Long materialId, Long businessUnitId, LocalDate effectiveFrom,
			LocalDate effectiveTo) {
		long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mst_material_unit_conversion
				where material_id = ?
				and business_unit_id = ?
				and status = 'ENABLED'
				and (cast(? as bigint) is null or id <> cast(? as bigint))
				and coalesce(effective_from, date '0001-01-01') <= coalesce(cast(? as date), date '9999-12-31')
				and coalesce(effective_to, date '9999-12-31') >= coalesce(cast(? as date), date '0001-01-01')
				""", Long.class, materialId, businessUnitId, currentId, currentId, effectiveTo, effectiveFrom);
		if (count > 0) {
			throw new BusinessException(ApiErrorCode.UNIT_CONVERSION_EFFECTIVE_OVERLAP);
		}
	}

	private java.util.Optional<ConversionRow> effectiveConversion(Long materialId, Long businessUnitId,
			LocalDate businessDate) {
		LocalDate date = businessDate == null ? LocalDate.now() : businessDate;
		return this.jdbcTemplate.query("""
				select id, material_id, base_unit_id, business_unit_id, conversion_rate, quantity_scale,
				       rounding_mode, effective_from, effective_to, status, version
				from mst_material_unit_conversion
				where material_id = ?
				and business_unit_id = ?
				and status = 'ENABLED'
				and coalesce(effective_from, date '0001-01-01') <= ?
				and coalesce(effective_to, date '9999-12-31') >= ?
				order by effective_from desc nulls last, id desc
				limit 1
				""", this::mapRow, materialId, businessUnitId, date, date).stream().findFirst();
	}

	private void changeStatus(Long id, Long version, String status, String action, CurrentUser operator,
			HttpServletRequest servletRequest) {
		int updated = this.jdbcTemplate.update("""
				update mst_material_unit_conversion
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				and version = ?
				""", status, operator.username(), OffsetDateTime.now(), id, version);
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
		this.auditService.record(operator, action, TARGET_TYPE, id, codeSummary(id), servletRequest);
	}

	private UnitConversionRecord record(Long id) {
		return this.jdbcTemplate.query("""
				select c.id, c.material_id, m.code as material_code, m.name as material_name,
				       c.base_unit_id, baseu.name as base_unit_name, c.business_unit_id,
				       bu.name as business_unit_name, c.conversion_rate, c.quantity_scale,
				       c.rounding_mode, c.effective_from, c.effective_to, c.status, c.remark,
				       c.created_at, c.updated_at, c.version
				from mst_material_unit_conversion c
				join mst_material m on m.id = c.material_id
				join mst_unit bu on bu.id = c.business_unit_id
				join mst_unit baseu on baseu.id = c.base_unit_id
				where c.id = ?
				""", this::mapRecord, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.UNIT_CONVERSION_NOT_FOUND));
	}

	private ConversionRow conversionRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, material_id, base_unit_id, business_unit_id, conversion_rate, quantity_scale,
				       rounding_mode, effective_from, effective_to, status, version
				from mst_material_unit_conversion
				where id = ?
				""", this::mapRow, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.UNIT_CONVERSION_NOT_FOUND));
	}

	private QueryParts queryParts(String keyword, Long materialId, Long businessUnitId, String status,
			LocalDate effectiveDate) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(m.code ilike ? or m.name ilike ? or bu.code ilike ? or bu.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (materialId != null) {
			conditions.add("c.material_id = ?");
			args.add(materialId);
		}
		if (businessUnitId != null) {
			conditions.add("c.business_unit_id = ?");
			args.add(businessUnitId);
		}
		if (hasText(status)) {
			conditions.add("c.status = ?");
			args.add(parseStatus(status));
		}
		if (effectiveDate != null) {
			conditions.add("coalesce(c.effective_from, date '0001-01-01') <= ?");
			conditions.add("coalesce(c.effective_to, date '9999-12-31') >= ?");
			args.add(effectiveDate);
			args.add(effectiveDate);
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private CandidatePage candidates(String tableName, String keyword, String status, String selectedIds, int page,
			int pageSize, String summaryColumn) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(code ilike ? or name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
		}
		if (hasText(status)) {
			conditions.add("status = ?");
			args.add(parseStatus(status));
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		long total = this.jdbcTemplate.queryForObject("select count(*) from " + tableName + " " + where, Long.class,
				args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<CandidateItem> items = this.jdbcTemplate.query("""
				select id, code, name, status, %s as summary_value
				from %s
				%s
				order by id desc
				limit ? offset ?
				""".formatted(summaryColumn, tableName, where), this::mapCandidate, pageArgs.toArray());
		List<Long> selected = parseIds(selectedIds);
		List<CandidateItem> selectedItems = selected.isEmpty() ? List.of()
				: this.jdbcTemplate.query("""
						select id, code, name, status, %s as summary_value
						from %s
						where id in (%s)
						order by id desc
						""".formatted(summaryColumn, tableName, placeholders(selected.size())), this::mapCandidate,
						selected.toArray());
		return new CandidatePage(items, selectedItems, Math.max(page, 1), limit(pageSize), total,
				totalPages(total, limit(pageSize)));
	}

	private CandidateItem mapCandidate(ResultSet rs, int rowNum) throws SQLException {
		String status = rs.getString("status");
		boolean disabled = !"ENABLED".equals(status);
		return new CandidateItem(rs.getLong("id"), rs.getString("code"), rs.getString("name"), status, disabled,
				disabled ? "状态不可用" : null, rs.getString("summary_value"));
	}

	private UnitConversionRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
		return new UnitConversionRecord(rs.getLong("id"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("base_unit_id"), rs.getString("base_unit_name"),
				rs.getLong("business_unit_id"), rs.getString("business_unit_name"),
				decimal(rs.getBigDecimal("conversion_rate"), 6), rs.getInt("quantity_scale"),
				rs.getString("rounding_mode"), rs.getObject("effective_from", LocalDate.class),
				rs.getObject("effective_to", LocalDate.class), rs.getString("status"), null,
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getLong("version"));
	}

	private ConversionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new ConversionRow(rs.getLong("id"), rs.getLong("material_id"), rs.getLong("base_unit_id"),
				rs.getLong("business_unit_id"), rs.getBigDecimal("conversion_rate"), rs.getInt("quantity_scale"),
				rs.getString("rounding_mode"), rs.getObject("effective_from", LocalDate.class),
				rs.getObject("effective_to", LocalDate.class), rs.getString("status"), rs.getLong("version"));
	}

	private java.util.Optional<MaterialRef> materialRef(Long id) {
		return this.jdbcTemplate.query("""
				select m.id, m.code, m.name, m.unit_id as base_unit_id, u.precision_scale, m.status
				from mst_material m
				join mst_unit u on u.id = m.unit_id
				where m.id = ?
				""", (rs, rowNum) -> new MaterialRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getLong("base_unit_id"), rs.getInt("precision_scale"), rs.getString("status")), id)
			.stream()
			.findFirst();
	}

	private java.util.Optional<UnitRef> unitRef(Long id) {
		return this.jdbcTemplate.query("select id, code, name, precision_scale, status from mst_unit where id = ?",
				(rs, rowNum) -> new UnitRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
						rs.getInt("precision_scale"), rs.getString("status")),
				id).stream().findFirst();
	}

	private int unitPrecision(Long unitId) {
		Integer precision = this.jdbcTemplate.queryForObject("select precision_scale from mst_unit where id = ?",
				Integer.class, unitId);
		return precision == null ? 6 : precision;
	}

	private String codeSummary(Long id) {
		return this.jdbcTemplate.queryForObject("""
				select m.code || '/' || u.code
				from mst_material_unit_conversion c
				join mst_material m on m.id = c.material_id
				join mst_unit u on u.id = c.business_unit_id
				where c.id = ?
				""", String.class, id);
	}

	private RoundingMode parseRoundingMode(String value) {
		try {
			return roundingMode(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.UNIT_CONVERSION_RATE_INVALID);
		}
	}

	private RoundingMode roundingMode(String value) {
		return switch (value) {
			case "HALF_UP" -> RoundingMode.HALF_UP;
			case "UP" -> RoundingMode.UP;
			case "DOWN" -> RoundingMode.DOWN;
			default -> throw new IllegalArgumentException("roundingMode");
		};
	}

	private String parseStatus(String status) {
		if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return status;
	}

	private BusinessException referenceInvalid() {
		return new BusinessException(ApiErrorCode.MASTER_DATA_REFERENCE_INVALID);
	}

	private void requireVersion(Long currentVersion, Long requestVersion, ApiErrorCode errorCode) {
		if (requestVersion == null || !currentVersion.equals(requestVersion)) {
			throw new BusinessException(errorCode);
		}
	}

	private static String decimal(BigDecimal value, int scale) {
		if (value == null) {
			return null;
		}
		return value.setScale(Math.max(scale, 0), RoundingMode.HALF_UP).toPlainString();
	}

	private static List<Long> parseIds(String selectedIds) {
		if (!hasText(selectedIds)) {
			return List.of();
		}
		return Arrays.stream(selectedIds.split(","))
			.map(String::trim)
			.filter(UnitConversionAdminService::hasText)
			.map(Long::parseLong)
			.toList();
	}

	private static String placeholders(int size) {
		return String.join(",", java.util.Collections.nCopies(size, "?"));
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

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record UnitConversionPayload(@NotNull Long materialId, @NotNull Long businessUnitId,
			@NotNull BigDecimal conversionRate, @NotNull Integer quantityScale, @NotNull String roundingMode,
			LocalDate effectiveFrom, LocalDate effectiveTo, String remark, Long version) {
	}

	public record VersionRequest(@NotNull Long version) {
	}

	public record ConversionRequest(@NotNull Long materialId, @NotNull Long businessUnitId,
			@NotNull BigDecimal businessQuantity, LocalDate businessDate) {
	}

	public record UnitConversionRecord(Long id, Long materialId, String materialCode, String materialName,
			Long baseUnitId, String baseUnitName, Long businessUnitId, String businessUnitName, String conversionRate,
			Integer quantityScale, String roundingMode, LocalDate effectiveFrom, LocalDate effectiveTo, String status,
			String lockedReason, OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

	public record ConversionResult(Long conversionId, Long materialId, Long businessUnitId, String businessQuantity,
			Long baseUnitId, String baseQuantity, String conversionRateSnapshot, Integer quantityScaleSnapshot,
			String roundingModeSnapshot) {
	}

	public record CandidatePage(List<CandidateItem> items, List<CandidateItem> selectedItems, int page, int pageSize,
			long total, int totalPages) {
	}

	public record CandidateItem(Long id, String code, String name, String status, boolean disabled,
			String disabledReason, String summary) {
	}

	public record ConversionSnapshot(Long conversionId, Long businessUnitId, BigDecimal businessQuantity,
			Long baseUnitId, BigDecimal baseQuantity, BigDecimal conversionRateSnapshot, Integer quantityScaleSnapshot,
			String roundingModeSnapshot, String quantityBasis) {
	}

	private record ValidatedConversion(MaterialRef material, UnitRef businessUnit, BigDecimal conversionRate,
			Integer quantityScale, RoundingMode roundingMode, LocalDate effectiveFrom, LocalDate effectiveTo) {
	}

	private record ConversionRow(Long id, Long materialId, Long baseUnitId, Long businessUnitId,
			BigDecimal conversionRate, Integer quantityScale, String roundingMode, LocalDate effectiveFrom,
			LocalDate effectiveTo, String status, Long version) {
	}

	private record MaterialRef(Long id, String code, String name, Long baseUnitId, Integer precisionScale,
			String status) {
	}

	private record UnitRef(Long id, String code, String name, Integer precisionScale, String status) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
