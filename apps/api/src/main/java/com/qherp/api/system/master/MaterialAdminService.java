package com.qherp.api.system.master;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.inventory.InventoryTrackingMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MaterialAdminService {

	private static final String TARGET_TYPE = "MATERIAL";

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public MaterialAdminService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<MaterialResponse> list(String keyword, String status, Long categoryId, String materialType,
			String sourceType, String trackingMethod, int page, int pageSize) {
		QueryParts queryParts = queryParts(keyword, status, categoryId, materialType, sourceType, trackingMethod);
		long total = this.jdbcTemplate.queryForObject("select count(*) from mst_material m " + queryParts.where(),
				Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<MaterialResponse> items = this.jdbcTemplate.query("""
				select m.id, m.code, m.name, m.specification, m.material_type, m.source_type,
				       m.tracking_method, m.category_id, c.name as category_name, m.unit_id, u.name as unit_name,
				       m.status, m.remark, m.created_at, m.updated_at
				from mst_material m
				left join mst_material_category c on c.id = m.category_id
				left join mst_unit u on u.id = m.unit_id
				%s
				order by m.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapMaterial, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public MaterialResponse get(Long id) {
		return this.jdbcTemplate.query("""
				select m.id, m.code, m.name, m.specification, m.material_type, m.source_type,
				       m.tracking_method, m.category_id, c.name as category_name, m.unit_id, u.name as unit_name,
				       m.status, m.remark, m.created_at, m.updated_at
				from mst_material m
				left join mst_material_category c on c.id = m.category_id
				left join mst_unit u on u.id = m.unit_id
				where m.id = ?
				""", this::mapMaterial, id).stream().findFirst().orElseThrow(this::notFound);
	}

	@Transactional
	public MaterialResponse create(MaterialRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		MaterialType materialType = parseMaterialType(request.materialType());
		MaterialSourceType sourceType = parseSourceType(request.sourceType());
		InventoryTrackingMethod trackingMethod = trackingMethodOrNone(request.trackingMethod());
		validateEnabledReferences(request.categoryId(), request.unitId());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into mst_material (
						code, name, specification, material_type, source_type, tracking_method, category_id, unit_id, status,
						remark, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, request.code(), request.name(), blankToNull(request.specification()),
					materialType.name(), sourceType.name(), trackingMethod.name(), request.categoryId(),
					request.unitId(), statusOrEnabled(request.status()).name(), blankToNull(request.remark()),
					operator.username(), now, operator.username(), now);
			this.auditService.record(operator, "MATERIAL_CREATE", TARGET_TYPE, id, request.code(), servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public MaterialResponse update(Long id, MaterialRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		MaterialRow current = materialRow(id).orElseThrow(this::notFound);
		MaterialType materialType = parseMaterialType(request.materialType());
		MaterialSourceType sourceType = parseSourceType(request.sourceType());
		InventoryTrackingMethod trackingMethod = trackingMethodOrCurrent(request.trackingMethod(),
				current.trackingMethod());
		validateTrackingMethodChangeAllowed(id, current.trackingMethod(), trackingMethod);
		validateEnabledReferences(request.categoryId(), request.unitId());
		MasterDataStatus status = statusOrCurrent(request.status(), current.status());
		try {
			int updated = this.jdbcTemplate.update("""
					update mst_material
					set code = ?, name = ?, specification = ?, material_type = ?, source_type = ?, tracking_method = ?,
						category_id = ?, unit_id = ?, status = ?, remark = ?,
						updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", request.code(), request.name(), blankToNull(request.specification()), materialType.name(),
					sourceType.name(), trackingMethod.name(), request.categoryId(), request.unitId(), status.name(),
					blankToNull(request.remark()), operator.username(), OffsetDateTime.now(), id);
			if (updated == 0) {
				throw notFound();
			}
			this.auditService.record(operator, "MATERIAL_UPDATE", TARGET_TYPE, id, request.code(), servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public MaterialResponse enable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		MaterialRow current = materialRow(id).orElseThrow(this::notFound);
		validateEnabledReferences(current.categoryId(), current.unitId());
		changeStatus(id, MasterDataStatus.ENABLED, operator, servletRequest);
		return get(id);
	}

	@Transactional
	public MaterialResponse disable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		materialRow(id).orElseThrow(this::notFound);
		changeStatus(id, MasterDataStatus.DISABLED, operator, servletRequest);
		return get(id);
	}

	private QueryParts queryParts(String keyword, String status, Long categoryId, String materialType,
			String sourceType, String trackingMethod) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(m.code ilike ? or m.name ilike ? or m.specification ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (hasText(status)) {
			conditions.add("m.status = ?");
			args.add(parseStatus(status).name());
		}
		if (categoryId != null) {
			conditions.add("m.category_id = ?");
			args.add(categoryId);
		}
		if (hasText(materialType)) {
			conditions.add("m.material_type = ?");
			args.add(parseMaterialType(materialType).name());
		}
		if (hasText(sourceType)) {
			conditions.add("m.source_type = ?");
			args.add(parseSourceType(sourceType).name());
		}
		if (hasText(trackingMethod)) {
			conditions.add("m.tracking_method = ?");
			args.add(parseTrackingMethod(trackingMethod).name());
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args);
	}

	private MaterialResponse mapMaterial(ResultSet rs, int rowNum) throws SQLException {
		InventoryTrackingMethod trackingMethod = InventoryTrackingMethod.valueOf(rs.getString("tracking_method"));
		return new MaterialResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getString("specification"), rs.getString("material_type"), rs.getString("source_type"),
				trackingMethod.name(), trackingMethod.displayName(), rs.getLong("category_id"),
				rs.getString("category_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				rs.getString("status"), rs.getString("remark"), trackingMethodImmutableReason(rs.getLong("id")),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private Optional<MaterialRow> materialRow(Long id) {
		return this.jdbcTemplate
			.query("""
					select id, code, category_id, unit_id, status, tracking_method
					from mst_material
					where id = ?
					""", (rs, rowNum) -> new MaterialRow(rs.getLong("id"), rs.getString("code"),
					rs.getLong("category_id"), rs.getLong("unit_id"), MasterDataStatus.valueOf(rs.getString("status")),
					InventoryTrackingMethod.valueOf(rs.getString("tracking_method"))),
					id)
			.stream()
			.findFirst();
	}

	private void validateEnabledReferences(Long categoryId, Long unitId) {
		if (categoryId == null || unitId == null) {
			throw referenceInvalid();
		}
		MasterDataStatus categoryStatus = this.jdbcTemplate
			.query("select status from mst_material_category where id = ?",
					(rs, rowNum) -> MasterDataStatus.valueOf(rs.getString("status")), categoryId)
			.stream()
			.findFirst()
			.orElseThrow(this::referenceInvalid);
		MasterDataStatus unitStatus = this.jdbcTemplate
			.query("select status from mst_unit where id = ?",
					(rs, rowNum) -> MasterDataStatus.valueOf(rs.getString("status")), unitId)
			.stream()
			.findFirst()
			.orElseThrow(this::referenceInvalid);
		if (categoryStatus != MasterDataStatus.ENABLED || unitStatus != MasterDataStatus.ENABLED) {
			throw referenceInvalid();
		}
	}

	private void validateTrackingMethodChangeAllowed(Long materialId, InventoryTrackingMethod current,
			InventoryTrackingMethod requested) {
		if (current == requested) {
			return;
		}
		if (hasTrackingFacts(materialId)) {
			throw new BusinessException(ApiErrorCode.INVENTORY_TRACKING_METHOD_IMMUTABLE);
		}
	}

	private String trackingMethodImmutableReason(Long materialId) {
		return hasTrackingFacts(materialId) ? ApiErrorCode.INVENTORY_TRACKING_METHOD_IMMUTABLE.message() : null;
	}

	private boolean hasTrackingFacts(Long materialId) {
		Boolean hasTrackingFacts = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from inv_stock_balance
					where material_id = ?
					and quantity_on_hand > 0
				)
				or exists (
					select 1
					from inv_stock_movement
					where material_id = ?
				)
				or exists (
					select 1
					from inv_stock_reservation
					where material_id = ?
					and status = 'ACTIVE'
				)
				""", Boolean.class, materialId, materialId, materialId);
		return Boolean.TRUE.equals(hasTrackingFacts);
	}

	private void changeStatus(Long id, MasterDataStatus status, CurrentUser operator, HttpServletRequest servletRequest) {
		int updated = this.jdbcTemplate.update("""
				update mst_material
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", status.name(), operator.username(), OffsetDateTime.now(), id);
		if (updated == 0) {
			throw notFound();
		}
		this.auditService.record(operator, status == MasterDataStatus.ENABLED ? "MATERIAL_ENABLE" : "MATERIAL_DISABLE",
				TARGET_TYPE, id, code(id), servletRequest);
	}

	private String code(Long id) {
		return this.jdbcTemplate.queryForObject("select code from mst_material where id = ?", String.class, id);
	}

	private MaterialType parseMaterialType(String value) {
		try {
			return MaterialType.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private MaterialSourceType parseSourceType(String value) {
		try {
			return MaterialSourceType.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private InventoryTrackingMethod trackingMethodOrNone(String trackingMethod) {
		return hasText(trackingMethod) ? parseTrackingMethod(trackingMethod) : InventoryTrackingMethod.NONE;
	}

	private InventoryTrackingMethod trackingMethodOrCurrent(String trackingMethod, InventoryTrackingMethod current) {
		return hasText(trackingMethod) ? parseTrackingMethod(trackingMethod) : current;
	}

	private InventoryTrackingMethod parseTrackingMethod(String value) {
		try {
			return InventoryTrackingMethod.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private MasterDataStatus statusOrEnabled(String status) {
		return hasText(status) ? parseStatus(status) : MasterDataStatus.ENABLED;
	}

	private MasterDataStatus statusOrCurrent(String status, MasterDataStatus current) {
		return hasText(status) ? parseStatus(status) : current;
	}

	private MasterDataStatus parseStatus(String status) {
		try {
			return MasterDataStatus.valueOf(status);
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_INVALID_STATUS);
		}
	}

	private BusinessException codeExists() {
		return new BusinessException(ApiErrorCode.MASTER_DATA_CODE_EXISTS);
	}

	private BusinessException notFound() {
		return new BusinessException(ApiErrorCode.MASTER_DATA_NOT_FOUND);
	}

	private BusinessException referenceInvalid() {
		return new BusinessException(ApiErrorCode.MASTER_DATA_REFERENCE_INVALID);
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

	public record MaterialRequest(@NotBlank String code, @NotBlank String name, String specification,
			@NotBlank String materialType, @NotBlank String sourceType, String trackingMethod, Long categoryId,
			Long unitId, String status, String remark) {
	}

	public record MaterialResponse(Long id, String code, String name, String specification, String materialType,
			String sourceType, String trackingMethod, String trackingMethodName, Long categoryId, String categoryName,
			Long unitId, String unitName, String status, String remark, String trackingMethodImmutableReason,
			OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	private record MaterialRow(Long id, String code, Long categoryId, Long unitId, MasterDataStatus status,
			InventoryTrackingMethod trackingMethod) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
