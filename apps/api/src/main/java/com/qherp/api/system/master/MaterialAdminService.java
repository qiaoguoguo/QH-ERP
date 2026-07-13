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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MaterialAdminService {

	private static final String TARGET_TYPE = "MATERIAL";

	private static final String MATERIAL_COST_UPDATE_PERMISSION = "master:material-cost:update";

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
				       m.status, m.remark, m.cost_category, m.inventory_valuation_category,
				       m.inventory_value_enabled, m.project_cost_enabled, m.cost_remark,
				       m.created_at, m.updated_at, m.version
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
				       m.status, m.remark, m.cost_category, m.inventory_valuation_category,
				       m.inventory_value_enabled, m.project_cost_enabled, m.cost_remark,
				       m.created_at, m.updated_at, m.version
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
		if (costFieldsTouched(request)) {
			requireCostUpdatePermission(operator);
		}
		CostAttributes costAttributes = costAttributes(request, materialType);
		MasterDataStatus status = statusOrEnabled(request.status());
		validateCostAttributeCompleted(status, costAttributes);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into mst_material (
						code, name, specification, material_type, source_type, tracking_method, category_id, unit_id, status,
						remark, cost_category, inventory_valuation_category, inventory_value_enabled,
						project_cost_enabled, cost_remark, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, request.code(), request.name(), blankToNull(request.specification()),
					materialType.name(), sourceType.name(), trackingMethod.name(), request.categoryId(),
					request.unitId(), statusOrEnabled(request.status()).name(), blankToNull(request.remark()),
					costAttributes.costCategory(), costAttributes.inventoryValuationCategory(),
					costAttributes.inventoryValueEnabled(), costAttributes.projectCostEnabled(),
					blankToNull(request.costRemark()), operator.username(), now, operator.username(), now);
			this.auditService.record(operator, "MATERIAL_CREATE", TARGET_TYPE, id, request.code(), servletRequest);
			if (costFieldsTouched(request)) {
				this.auditService.record(operator, "MATERIAL_COST_UPDATE", TARGET_TYPE, id, request.code(),
						servletRequest);
			}
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
		requireVersion(current.version(), request.version());
		boolean costFieldsTouched = costFieldsTouched(request);
		if (costFieldsTouched) {
			requireCostUpdatePermission(operator);
		}
		validateTrackingMethodChangeAllowed(id, current.trackingMethod(), trackingMethod);
		validateBaseUnitChangeAllowed(id, current.unitId(), request.unitId());
		validateEnabledReferences(request.categoryId(), request.unitId());
		MasterDataStatus status = statusOrCurrent(request.status(), current.status());
		CostAttributes costAttributes = costAttributes(request, materialType, current);
		validateCostAttributeCompleted(status, costAttributes);
		try {
			int updated = this.jdbcTemplate.update("""
					update mst_material
					set code = ?, name = ?, specification = ?, material_type = ?, source_type = ?, tracking_method = ?,
						category_id = ?, unit_id = ?, status = ?, remark = ?, cost_category = ?,
						inventory_valuation_category = ?, inventory_value_enabled = ?, project_cost_enabled = ?,
						cost_remark = ?,
						updated_by = ?, updated_at = ?, version = version + 1
					where id = ? and version = ?
					""", request.code(), request.name(), blankToNull(request.specification()), materialType.name(),
					sourceType.name(), trackingMethod.name(), request.categoryId(), request.unitId(), status.name(),
					blankToNull(request.remark()), costAttributes.costCategory(),
					costAttributes.inventoryValuationCategory(), costAttributes.inventoryValueEnabled(),
					costAttributes.projectCostEnabled(), costRemark(request, current), operator.username(),
					OffsetDateTime.now(), id, current.version());
			if (updated == 0) {
				throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
			}
			this.auditService.record(operator, "MATERIAL_UPDATE", TARGET_TYPE, id, request.code(), servletRequest);
			if (costFieldsTouched) {
				this.auditService.record(operator, "MATERIAL_COST_UPDATE", TARGET_TYPE, id, request.code(),
						servletRequest);
			}
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public MaterialResponse enable(Long id, VersionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		MaterialRow current = materialRow(id).orElseThrow(this::notFound);
		requireVersion(current.version(), request == null ? null : request.version());
		validateEnabledReferences(current.categoryId(), current.unitId());
		validateCostAttributeCompleted(id);
		changeStatus(id, current.version(), MasterDataStatus.ENABLED, operator, servletRequest);
		return get(id);
	}

	@Transactional
	public MaterialResponse disable(Long id, VersionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		MaterialRow current = materialRow(id).orElseThrow(this::notFound);
		requireVersion(current.version(), request == null ? null : request.version());
		changeStatus(id, current.version(), MasterDataStatus.DISABLED, operator, servletRequest);
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
				baseUnitImmutableReason(rs.getLong("id")), rs.getString("cost_category"),
				rs.getString("inventory_valuation_category"), rs.getBoolean("inventory_value_enabled"),
				rs.getBoolean("project_cost_enabled"), rs.getString("cost_remark"),
				costAttributeCompleted(rs.getString("cost_category"), rs.getString("inventory_valuation_category")),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getLong("version"));
	}

	private Optional<MaterialRow> materialRow(Long id) {
		return this.jdbcTemplate
			.query("""
					select id, code, category_id, unit_id, status, tracking_method, cost_category,
					       inventory_valuation_category, inventory_value_enabled, project_cost_enabled, cost_remark,
					       version
					from mst_material
					where id = ?
					""", (rs, rowNum) -> new MaterialRow(rs.getLong("id"), rs.getString("code"),
					rs.getLong("category_id"), rs.getLong("unit_id"), MasterDataStatus.valueOf(rs.getString("status")),
					InventoryTrackingMethod.valueOf(rs.getString("tracking_method")), rs.getString("cost_category"),
					rs.getString("inventory_valuation_category"),
					rs.getObject("inventory_value_enabled", Boolean.class),
					rs.getObject("project_cost_enabled", Boolean.class), rs.getString("cost_remark"),
					rs.getLong("version")),
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

	private void validateBaseUnitChangeAllowed(Long materialId, Long currentUnitId, Long requestedUnitId) {
		if (requestedUnitId == null || currentUnitId.equals(requestedUnitId)) {
			return;
		}
		if (hasMaterialBusinessFacts(materialId)) {
			throw new BusinessException(ApiErrorCode.MATERIAL_BASE_UNIT_IMMUTABLE);
		}
	}

	private String baseUnitImmutableReason(Long materialId) {
		return hasMaterialBusinessFacts(materialId) ? ApiErrorCode.MATERIAL_BASE_UNIT_IMMUTABLE.message() : null;
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

	private boolean hasMaterialBusinessFacts(Long materialId) {
		Boolean hasFacts = this.jdbcTemplate.queryForObject("""
				select exists (select 1 from inv_stock_balance where material_id = ?)
				or exists (select 1 from inv_stock_movement where material_id = ?)
				or exists (select 1 from inv_stock_reservation where material_id = ?)
				or exists (select 1 from inv_stock_tracking_allocation where material_id = ?)
				or exists (select 1 from mfg_bom where parent_material_id = ?)
				or exists (select 1 from mfg_bom_item where child_material_id = ?)
				or exists (select 1 from mfg_work_order where product_material_id = ?)
				or exists (select 1 from mfg_work_order_material where material_id = ?)
				or exists (select 1 from mfg_cost_record where product_material_id = ? or material_id = ?)
				or exists (select 1 from proc_purchase_order_line where material_id = ?)
				or exists (select 1 from proc_purchase_receipt_line where material_id = ?)
				or exists (select 1 from sal_sales_order_line where material_id = ?)
				or exists (select 1 from sal_sales_shipment_line where material_id = ?)
				""", Boolean.class, materialId, materialId, materialId, materialId, materialId, materialId,
				materialId, materialId, materialId, materialId, materialId, materialId, materialId, materialId);
		return Boolean.TRUE.equals(hasFacts);
	}

	private CostAttributes costAttributes(MaterialRequest request, MaterialType materialType) {
		String costCategory = hasText(request.costCategory()) ? request.costCategory() : defaultCostCategory(materialType);
		String inventoryValuationCategory = hasText(request.inventoryValuationCategory())
				? request.inventoryValuationCategory() : "VALUATED_MATERIAL";
		if (!validCostCategory(costCategory) || !validInventoryValuationCategory(inventoryValuationCategory)) {
			throw new BusinessException(ApiErrorCode.MATERIAL_COST_ATTRIBUTE_INCOMPLETE);
		}
		boolean inventoryValueEnabled = request.inventoryValueEnabled() == null ? true : request.inventoryValueEnabled();
		boolean projectCostEnabled = request.projectCostEnabled() == null ? true : request.projectCostEnabled();
		return new CostAttributes(costCategory, inventoryValuationCategory, inventoryValueEnabled, projectCostEnabled);
	}

	private CostAttributes costAttributes(MaterialRequest request, MaterialType materialType, MaterialRow current) {
		String costCategory = hasText(request.costCategory()) ? request.costCategory() : current.costCategory();
		String inventoryValuationCategory = hasText(request.inventoryValuationCategory())
				? request.inventoryValuationCategory() : current.inventoryValuationCategory();
		if (!validCostCategory(costCategory) || !validInventoryValuationCategory(inventoryValuationCategory)) {
			throw new BusinessException(ApiErrorCode.MATERIAL_COST_ATTRIBUTE_INCOMPLETE);
		}
		boolean inventoryValueEnabled = request.inventoryValueEnabled() == null ? current.inventoryValueEnabled()
				: request.inventoryValueEnabled();
		boolean projectCostEnabled = request.projectCostEnabled() == null ? current.projectCostEnabled()
				: request.projectCostEnabled();
		return new CostAttributes(costCategory, inventoryValuationCategory, inventoryValueEnabled, projectCostEnabled);
	}

	private String costRemark(MaterialRequest request, MaterialRow current) {
		return request.costRemark() == null ? current.costRemark() : blankToNull(request.costRemark());
	}

	private String defaultCostCategory(MaterialType materialType) {
		return switch (materialType) {
			case FINISHED_GOOD -> "FINISHED_GOOD";
			case SEMI_FINISHED -> "SEMI_FINISHED";
			case AUXILIARY -> "AUXILIARY_MATERIAL";
			default -> "DIRECT_MATERIAL";
		};
	}

	private boolean validCostCategory(String value) {
		return switch (value) {
			case "DIRECT_MATERIAL", "AUXILIARY_MATERIAL", "SEMI_FINISHED", "FINISHED_GOOD", "OUTSOURCING",
					"SERVICE", "UNCLASSIFIED" -> true;
			default -> false;
		};
	}

	private boolean validInventoryValuationCategory(String value) {
		return switch (value) {
			case "VALUATED_MATERIAL", "NON_VALUATED_CONSUMABLE", "SERVICE_NON_STOCK",
					"UNCLASSIFIED" -> true;
			default -> false;
		};
	}

	private boolean costAttributeCompleted(String costCategory, String inventoryValuationCategory) {
		return hasText(costCategory) && hasText(inventoryValuationCategory) && !"UNCLASSIFIED".equals(costCategory)
				&& !"UNCLASSIFIED".equals(inventoryValuationCategory);
	}

	private void validateCostAttributeCompleted(Long materialId) {
		Boolean completed = this.jdbcTemplate.query("""
				select cost_category, inventory_valuation_category
				from mst_material
				where id = ?
				""", (rs, rowNum) -> costAttributeCompleted(rs.getString("cost_category"),
				rs.getString("inventory_valuation_category")), materialId).stream().findFirst().orElse(false);
		if (!Boolean.TRUE.equals(completed)) {
			throw new BusinessException(ApiErrorCode.MATERIAL_COST_ATTRIBUTE_INCOMPLETE);
		}
	}

	private void validateCostAttributeCompleted(MasterDataStatus status, CostAttributes costAttributes) {
		if (status == MasterDataStatus.ENABLED && !costAttributeCompleted(costAttributes.costCategory(),
				costAttributes.inventoryValuationCategory())) {
			throw new BusinessException(ApiErrorCode.MATERIAL_COST_ATTRIBUTE_INCOMPLETE);
		}
	}

	private void changeStatus(Long id, Long version, MasterDataStatus status, CurrentUser operator,
			HttpServletRequest servletRequest) {
		int updated = this.jdbcTemplate.update("""
				update mst_material
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ?
				""", status.name(), operator.username(), OffsetDateTime.now(), id, version);
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
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

	private void requireVersion(Long currentVersion, Long requestVersion) {
		if (requestVersion == null || !currentVersion.equals(requestVersion)) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
	}

	private void requireCostUpdatePermission(CurrentUser operator) {
		if (operator == null || !operator.permissions().contains(MATERIAL_COST_UPDATE_PERMISSION)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private boolean costFieldsTouched(MaterialRequest request) {
		return request.costCategory() != null || request.inventoryValuationCategory() != null
				|| request.inventoryValueEnabled() != null || request.projectCostEnabled() != null
				|| request.costRemark() != null;
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
			Long unitId, String status, String remark, String costCategory, String inventoryValuationCategory,
			Boolean inventoryValueEnabled, Boolean projectCostEnabled, String costRemark, Long version) {
	}

	public record VersionRequest(Long version) {
	}

	public record MaterialResponse(Long id, String code, String name, String specification, String materialType,
			String sourceType, String trackingMethod, String trackingMethodName, Long categoryId, String categoryName,
			Long unitId, String unitName, String status, String remark, String trackingMethodImmutableReason,
			String baseUnitImmutableReason, String costCategory, String inventoryValuationCategory,
			Boolean inventoryValueEnabled, Boolean projectCostEnabled, String costRemark, Boolean costAttributeCompleted,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
	}

	private record MaterialRow(Long id, String code, Long categoryId, Long unitId, MasterDataStatus status,
			InventoryTrackingMethod trackingMethod, String costCategory, String inventoryValuationCategory,
			Boolean inventoryValueEnabled, Boolean projectCostEnabled, String costRemark, Long version) {
	}

	private record CostAttributes(String costCategory, String inventoryValuationCategory, Boolean inventoryValueEnabled,
			Boolean projectCostEnabled) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
