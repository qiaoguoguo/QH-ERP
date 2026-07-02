package com.qherp.api.system.bom;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class BomAdminService {

	private static final String TARGET_TYPE = "BOM";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final BigDecimal ONE = BigDecimal.ONE;

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public BomAdminService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<BomSummaryResponse> list(String keyword, String status, Long parentMaterialId, int page,
			int pageSize) {
		QueryParts queryParts = queryParts(keyword, status, parentMaterialId);
		long total = this.jdbcTemplate.queryForObject("select count(*) from mfg_bom b join mst_material p on p.id = b.parent_material_id "
				+ queryParts.where(), Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<BomSummaryResponse> items = this.jdbcTemplate.query("""
				select b.id, b.bom_code, b.parent_material_id, p.code as parent_material_code,
				       p.name as parent_material_name, b.version_code, b.name, b.base_quantity,
				       b.base_unit_id, u.name as base_unit_name, b.status,
				       (select count(*) from mfg_bom_item bi where bi.bom_id = b.id) as item_count,
				       b.effective_from, b.effective_to, b.remark, b.created_at, b.updated_at, b.enabled_at
				from mfg_bom b
				join mst_material p on p.id = b.parent_material_id
				join mst_unit u on u.id = b.base_unit_id
				%s
				order by b.updated_at desc, b.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public BomDetailResponse get(Long id) {
		BomSummaryResponse summary = summary(id).orElseThrow(this::notFound);
		return detail(summary, items(id));
	}

	@Transactional
	public BomDetailResponse create(BomRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		return insertBom(request, operator, servletRequest, "BOM_CREATE");
	}

	@Transactional
	public BomDetailResponse update(Long id, BomRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		BomRow current = bomRow(id).orElseThrow(this::notFound);
		statusOrCurrentDraftForUpdate(request.status(), current.status());
		MaterialRef parent = validateParentMaterial(request.parentMaterialId());
		BigDecimal baseQuantity = validatePositive(request.baseQuantity());
		Long baseUnitId = request.baseUnitId() == null ? parent.unitId() : request.baseUnitId();
		List<ValidatedItem> items = validateItems(parent.id(), baseUnitId, request.items());
		validateNoCycle(id, parent.id(), items);
		try {
			int updated = this.jdbcTemplate.update("""
					update mfg_bom
					set bom_code = ?, parent_material_id = ?, version_code = ?, name = ?, base_quantity = ?,
					    base_unit_id = ?, status = ?, effective_from = ?, effective_to = ?, remark = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", request.bomCode(), parent.id(), request.versionCode(), request.name(), baseQuantity,
					baseUnitId, BomStatus.DRAFT.name(), request.effectiveFrom(), request.effectiveTo(),
					blankToNull(request.remark()), operator.username(), OffsetDateTime.now(), id);
			if (updated == 0) {
				throw notFound();
			}
			this.jdbcTemplate.update("delete from mfg_bom_item where bom_id = ?", id);
			insertItems(id, items, OffsetDateTime.now());
			this.auditService.record(operator, "BOM_UPDATE", TARGET_TYPE, id, request.bomCode(), servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateBomException(exception);
		}
	}

	@Transactional
	public BomDetailResponse copy(Long id, BomCopyRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		BomRow current = bomRow(id).orElseThrow(this::notFound);
		List<BomItemRequest> copiedItems = itemRequests(id);
		BomRequest copiedRequest = new BomRequest(request.bomCode(), current.parentMaterialId(), request.versionCode(),
				hasText(request.name()) ? request.name() : current.name(), current.baseQuantity(), current.baseUnitId(),
				BomStatus.DRAFT.name(), request.effectiveFrom(), request.effectiveTo(), request.remark(), copiedItems);
		return insertBom(copiedRequest, operator, servletRequest, "BOM_COPY");
	}

	@Transactional
	public BomDetailResponse enable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		BomRow current = bomRow(id).orElseThrow(this::notFound);
		MaterialRef parent = validateParentMaterial(current.parentMaterialId());
		List<ValidatedItem> items = validateItems(parent.id(), current.baseUnitId(), itemRequests(id));
		validateNoCycle(id, parent.id(), items);
		validateEnabledVersionUnique(id, parent.id());
		try {
			int updated = this.jdbcTemplate.update("""
					update mfg_bom
					set status = ?, enabled_by = ?, enabled_at = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", BomStatus.ENABLED.name(), operator.username(), OffsetDateTime.now(), operator.username(),
					OffsetDateTime.now(), id);
			if (updated == 0) {
				throw notFound();
			}
			this.auditService.record(operator, "BOM_ENABLE", TARGET_TYPE, id, current.bomCode(), servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateBomException(exception);
		}
	}

	@Transactional
	public BomDetailResponse disable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		BomRow current = bomRow(id).orElseThrow(this::notFound);
		int updated = this.jdbcTemplate.update("""
				update mfg_bom
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", BomStatus.DISABLED.name(), operator.username(), OffsetDateTime.now(), id);
		if (updated == 0) {
			throw notFound();
		}
		this.auditService.record(operator, "BOM_DISABLE", TARGET_TYPE, id, current.bomCode(), servletRequest);
		return get(id);
	}

	private BomDetailResponse insertBom(BomRequest request, CurrentUser operator, HttpServletRequest servletRequest,
			String auditAction) {
		BomStatus status = statusOrDraftForCreate(request.status());
		MaterialRef parent = validateParentMaterial(request.parentMaterialId());
		BigDecimal baseQuantity = validatePositive(request.baseQuantity());
		Long baseUnitId = request.baseUnitId() == null ? parent.unitId() : request.baseUnitId();
		List<ValidatedItem> items = validateItems(parent.id(), baseUnitId, request.items());
		validateNoCycle(null, parent.id(), items);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into mfg_bom (
						bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id, status,
						effective_from, effective_to, remark, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, request.bomCode(), parent.id(), request.versionCode(), request.name(),
					baseQuantity, baseUnitId, status.name(), request.effectiveFrom(), request.effectiveTo(),
					blankToNull(request.remark()), operator.username(), now, operator.username(), now);
			insertItems(id, items, now);
			this.auditService.record(operator, auditAction, TARGET_TYPE, id, request.bomCode(), servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateBomException(exception);
		}
	}

	private void insertItems(Long bomId, List<ValidatedItem> items, OffsetDateTime now) {
		for (ValidatedItem item : items) {
			this.jdbcTemplate.update("""
					insert into mfg_bom_item (
						bom_id, line_no, child_material_id, unit_id, quantity, loss_rate, remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", bomId, item.lineNo(), item.childMaterial().id(), item.unitId(), item.quantity(),
					item.lossRate(), blankToNull(item.remark()), now, now);
		}
	}

	private List<ValidatedItem> validateItems(Long parentMaterialId, Long baseUnitId, List<BomItemRequest> items) {
		validateEnabledUnit(baseUnitId);
		if (items == null || items.isEmpty()) {
			throw new BusinessException(ApiErrorCode.BOM_EMPTY_ITEMS);
		}
		Set<Long> childIds = new HashSet<>();
		Set<Integer> lineNos = new HashSet<>();
		List<ValidatedItem> result = new ArrayList<>();
		for (BomItemRequest item : items) {
			if (item == null || item.lineNo() == null || item.lineNo() <= 0 || !lineNos.add(item.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			MaterialRef childMaterial = validateChildMaterial(item.childMaterialId());
			if (childMaterial.id().equals(parentMaterialId)) {
				throw new BusinessException(ApiErrorCode.BOM_SELF_REFERENCE);
			}
			if (!childIds.add(childMaterial.id())) {
				throw new BusinessException(ApiErrorCode.BOM_DUPLICATE_ITEM);
			}
			BigDecimal quantity = validatePositive(item.quantity());
			BigDecimal lossRate = item.lossRate() == null ? ZERO : item.lossRate();
			if (lossRate.compareTo(ZERO) < 0 || lossRate.compareTo(ONE) >= 0) {
				throw new BusinessException(ApiErrorCode.BOM_QUANTITY_INVALID);
			}
			Long unitId = item.unitId() == null ? childMaterial.unitId() : item.unitId();
			validateEnabledUnit(unitId);
			result.add(new ValidatedItem(item.lineNo(), childMaterial, unitId, quantity, lossRate, item.remark()));
		}
		return result;
	}

	private void validateNoCycle(Long currentBomId, Long parentMaterialId, List<ValidatedItem> items) {
		ArrayDeque<Long> queue = new ArrayDeque<>();
		for (ValidatedItem item : items) {
			queue.add(item.childMaterial().id());
		}
		Set<Long> visited = new HashSet<>();
		while (!queue.isEmpty()) {
			Long currentMaterialId = queue.removeFirst();
			if (!visited.add(currentMaterialId)) {
				continue;
			}
			if (currentMaterialId.equals(parentMaterialId)) {
				throw new BusinessException(ApiErrorCode.BOM_CYCLE_DETECTED);
			}
			List<Long> childMaterialIds = currentBomId == null
					? this.jdbcTemplate.query("""
							select i.child_material_id
							from mfg_bom b
							join mfg_bom_item i on i.bom_id = b.id
							where b.parent_material_id = ?
							and b.status in ('DRAFT', 'ENABLED')
							""", (rs, rowNum) -> rs.getLong("child_material_id"), currentMaterialId)
					: this.jdbcTemplate.query("""
							select i.child_material_id
							from mfg_bom b
							join mfg_bom_item i on i.bom_id = b.id
							where b.parent_material_id = ?
							and b.status in ('DRAFT', 'ENABLED')
							and b.id <> ?
							""", (rs, rowNum) -> rs.getLong("child_material_id"), currentMaterialId, currentBomId);
			queue.addAll(childMaterialIds);
		}
	}

	private void validateEnabledVersionUnique(Long bomId, Long parentMaterialId) {
		long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_bom
				where parent_material_id = ?
				and status = 'ENABLED'
				and id <> ?
				""", Long.class, parentMaterialId, bomId);
		if (count > 0) {
			throw new BusinessException(ApiErrorCode.BOM_ENABLED_VERSION_EXISTS);
		}
	}

	private MaterialRef validateParentMaterial(Long parentMaterialId) {
		if (parentMaterialId == null) {
			throw new BusinessException(ApiErrorCode.BOM_PARENT_MATERIAL_INVALID);
		}
		MaterialRef material = materialRef(parentMaterialId).orElseThrow(this::parentInvalid);
		if (!"ENABLED".equals(material.status()) || !("FINISHED_GOOD".equals(material.materialType())
				|| "SEMI_FINISHED".equals(material.materialType())) || !("SELF_MADE".equals(material.sourceType())
				|| "OUTSOURCED".equals(material.sourceType()))) {
			throw parentInvalid();
		}
		return material;
	}

	private MaterialRef validateChildMaterial(Long childMaterialId) {
		if (childMaterialId == null) {
			throw new BusinessException(ApiErrorCode.BOM_CHILD_MATERIAL_INVALID);
		}
		MaterialRef material = materialRef(childMaterialId).orElseThrow(this::childInvalid);
		if (!"ENABLED".equals(material.status())) {
			throw childInvalid();
		}
		return material;
	}

	private Optional<MaterialRef> materialRef(Long materialId) {
		return this.jdbcTemplate
			.query("""
					select m.id, m.code, m.name, m.material_type, m.source_type, m.unit_id, u.name as unit_name,
					       m.status
					from mst_material m
					left join mst_unit u on u.id = m.unit_id
					where m.id = ?
					""",
					(rs, rowNum) -> new MaterialRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
							rs.getString("material_type"), rs.getString("source_type"), rs.getLong("unit_id"),
							rs.getString("unit_name"), rs.getString("status")),
					materialId)
			.stream()
			.findFirst();
	}

	private void validateEnabledUnit(Long unitId) {
		if (unitId == null) {
			throw new BusinessException(ApiErrorCode.BOM_UNIT_INVALID);
		}
		String status = this.jdbcTemplate.query("select status from mst_unit where id = ?",
				(rs, rowNum) -> rs.getString("status"), unitId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.BOM_UNIT_INVALID);
		}
	}

	private BigDecimal validatePositive(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.BOM_QUANTITY_INVALID);
		}
		return value;
	}

	private BomStatus statusOrDraftForCreate(String status) {
		BomStatus parsed = hasText(status) ? parseStatus(status) : BomStatus.DRAFT;
		if (parsed != BomStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.BOM_STATUS_NOT_EDITABLE);
		}
		return parsed;
	}

	private BomStatus statusOrCurrentDraftForUpdate(String status, BomStatus currentStatus) {
		if (currentStatus != BomStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.BOM_STATUS_NOT_EDITABLE);
		}
		BomStatus parsed = hasText(status) ? parseStatus(status) : currentStatus;
		if (parsed != BomStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.BOM_STATUS_NOT_EDITABLE);
		}
		return parsed;
	}

	private BomStatus parseStatus(String status) {
		try {
			return BomStatus.valueOf(status);
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private QueryParts queryParts(String keyword, String status, Long parentMaterialId) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(b.bom_code ilike ? or b.version_code ilike ? or b.name ilike ? or p.code ilike ? or p.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (hasText(status)) {
			conditions.add("b.status = ?");
			args.add(parseStatus(status).name());
		}
		if (parentMaterialId != null) {
			conditions.add("b.parent_material_id = ?");
			args.add(parentMaterialId);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args);
	}

	private Optional<BomSummaryResponse> summary(Long id) {
		return this.jdbcTemplate
			.query("""
					select b.id, b.bom_code, b.parent_material_id, p.code as parent_material_code,
					       p.name as parent_material_name, b.version_code, b.name, b.base_quantity,
					       b.base_unit_id, u.name as base_unit_name, b.status,
					       (select count(*) from mfg_bom_item bi where bi.bom_id = b.id) as item_count,
					       b.effective_from, b.effective_to, b.remark, b.created_at, b.updated_at, b.enabled_at
					from mfg_bom b
					join mst_material p on p.id = b.parent_material_id
					join mst_unit u on u.id = b.base_unit_id
					where b.id = ?
					""", this::mapSummary, id)
			.stream()
			.findFirst();
	}

	private BomSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
		return new BomSummaryResponse(rs.getLong("id"), rs.getString("bom_code"), rs.getLong("parent_material_id"),
				rs.getString("parent_material_code"), rs.getString("parent_material_name"),
				rs.getString("version_code"), rs.getString("name"), rs.getBigDecimal("base_quantity"),
				rs.getLong("base_unit_id"), rs.getString("base_unit_name"), rs.getString("status"),
				rs.getInt("item_count"), rs.getObject("effective_from", LocalDate.class),
				rs.getObject("effective_to", LocalDate.class), rs.getString("remark"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getObject("enabled_at", OffsetDateTime.class));
	}

	private List<BomItemResponse> items(Long bomId) {
		return this.jdbcTemplate.query("""
				select i.id, i.line_no, i.child_material_id, m.code as child_material_code,
				       m.name as child_material_name, m.material_type as child_material_type,
				       i.unit_id, u.name as unit_name, i.quantity, i.loss_rate, i.remark
				from mfg_bom_item i
				join mst_material m on m.id = i.child_material_id
				join mst_unit u on u.id = i.unit_id
				where i.bom_id = ?
				order by i.line_no asc, i.id asc
				""", (rs, rowNum) -> new BomItemResponse(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("child_material_id"), rs.getString("child_material_code"),
				rs.getString("child_material_name"), rs.getString("child_material_type"), rs.getLong("unit_id"),
				rs.getString("unit_name"), rs.getBigDecimal("quantity"), rs.getBigDecimal("loss_rate"),
				rs.getString("remark")), bomId);
	}

	private List<BomItemRequest> itemRequests(Long bomId) {
		return this.jdbcTemplate.query("""
				select line_no, child_material_id, unit_id, quantity, loss_rate, remark
				from mfg_bom_item
				where bom_id = ?
				order by line_no asc, id asc
				""", (rs, rowNum) -> new BomItemRequest(rs.getInt("line_no"), rs.getLong("child_material_id"),
				rs.getLong("unit_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("loss_rate"),
				rs.getString("remark")), bomId);
	}

	private Optional<BomRow> bomRow(Long id) {
		return this.jdbcTemplate
			.query("""
					select id, bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id,
					       status, effective_from, effective_to, remark
					from mfg_bom
					where id = ?
					""", (rs, rowNum) -> new BomRow(rs.getLong("id"), rs.getString("bom_code"),
					rs.getLong("parent_material_id"), rs.getString("version_code"), rs.getString("name"),
					rs.getBigDecimal("base_quantity"), rs.getLong("base_unit_id"),
					BomStatus.valueOf(rs.getString("status")), rs.getObject("effective_from", LocalDate.class),
					rs.getObject("effective_to", LocalDate.class), rs.getString("remark")), id)
			.stream()
			.findFirst();
	}

	private BomDetailResponse detail(BomSummaryResponse summary, List<BomItemResponse> items) {
		return new BomDetailResponse(summary.id(), summary.bomCode(), summary.parentMaterialId(),
				summary.parentMaterialCode(), summary.parentMaterialName(), summary.versionCode(), summary.name(),
				summary.baseQuantity(), summary.baseUnitId(), summary.baseUnitName(), summary.status(),
				summary.effectiveFrom(), summary.effectiveTo(), summary.remark(), summary.createdAt(),
				summary.updatedAt(), summary.enabledAt(), items);
	}

	private BusinessException duplicateBomException(DuplicateKeyException exception) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		if (message != null && message.contains("uk_mfg_bom_code")) {
			return new BusinessException(ApiErrorCode.BOM_CODE_EXISTS);
		}
		if (message != null && message.contains("uk_mfg_bom_parent_version")) {
			return new BusinessException(ApiErrorCode.BOM_VERSION_EXISTS);
		}
		if (message != null && message.contains("uk_mfg_bom_enabled_parent")) {
			return new BusinessException(ApiErrorCode.BOM_ENABLED_VERSION_EXISTS);
		}
		if (message != null && message.contains("uk_mfg_bom_item_material")) {
			return new BusinessException(ApiErrorCode.BOM_DUPLICATE_ITEM);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private BusinessException notFound() {
		return new BusinessException(ApiErrorCode.BOM_NOT_FOUND);
	}

	private BusinessException parentInvalid() {
		return new BusinessException(ApiErrorCode.BOM_PARENT_MATERIAL_INVALID);
	}

	private BusinessException childInvalid() {
		return new BusinessException(ApiErrorCode.BOM_CHILD_MATERIAL_INVALID);
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

	public record BomItemRequest(Integer lineNo, Long childMaterialId, Long unitId, BigDecimal quantity,
			BigDecimal lossRate, String remark) {
	}

	public record BomRequest(@NotBlank String bomCode, Long parentMaterialId, @NotBlank String versionCode,
			@NotBlank String name, BigDecimal baseQuantity, Long baseUnitId, String status, LocalDate effectiveFrom,
			LocalDate effectiveTo, String remark, List<BomItemRequest> items) {
	}

	public record BomCopyRequest(@NotBlank String bomCode, @NotBlank String versionCode, String name,
			LocalDate effectiveFrom, LocalDate effectiveTo, String remark) {
	}

	public record BomSummaryResponse(Long id, String bomCode, Long parentMaterialId, String parentMaterialCode,
			String parentMaterialName, String versionCode, String name, BigDecimal baseQuantity, Long baseUnitId,
			String baseUnitName, String status, int itemCount, LocalDate effectiveFrom, LocalDate effectiveTo,
			String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime enabledAt) {
	}

	public record BomItemResponse(Long id, Integer lineNo, Long childMaterialId, String childMaterialCode,
			String childMaterialName, String childMaterialType, Long unitId, String unitName, BigDecimal quantity,
			BigDecimal lossRate, String remark) {
	}

	public record BomDetailResponse(Long id, String bomCode, Long parentMaterialId, String parentMaterialCode,
			String parentMaterialName, String versionCode, String name, BigDecimal baseQuantity, Long baseUnitId,
			String baseUnitName, String status, LocalDate effectiveFrom, LocalDate effectiveTo, String remark,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime enabledAt, List<BomItemResponse> items) {
	}

	private record BomRow(Long id, String bomCode, Long parentMaterialId, String versionCode, String name,
			BigDecimal baseQuantity, Long baseUnitId, BomStatus status, LocalDate effectiveFrom, LocalDate effectiveTo,
			String remark) {
	}

	private record MaterialRef(Long id, String code, String name, String materialType, String sourceType, Long unitId,
			String unitName, String status) {
	}

	private record ValidatedItem(Integer lineNo, MaterialRef childMaterial, Long unitId, BigDecimal quantity,
			BigDecimal lossRate, String remark) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
