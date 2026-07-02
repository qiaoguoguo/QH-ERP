package com.qherp.api.system.master;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MasterDataAdminService {

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public MasterDataAdminService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<UnitResponse> listUnits(Resource resource, String keyword, String status, int page,
			int pageSize) {
		requireResource(resource, Resource.UNIT);
		QueryParts queryParts = queryParts(resource, keyword, status);
		long total = total(resource, queryParts);
		List<Object> args = paginationArgs(queryParts, page, pageSize);
		List<UnitResponse> items = this.jdbcTemplate.query("""
				select id, code, name, precision_scale, status, sort_order, remark, created_at, updated_at
				from %s
				%s
				order by sort_order, id desc
				limit ? offset ?
				""".formatted(resource.table(), queryParts.where()), this::mapUnit, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public UnitResponse getUnit(Resource resource, Long id) {
		requireResource(resource, Resource.UNIT);
		return this.jdbcTemplate.query("""
				select id, code, name, precision_scale, status, sort_order, remark, created_at, updated_at
				from %s
				where id = ?
				""".formatted(resource.table()), this::mapUnit, id).stream().findFirst().orElseThrow(this::notFound);
	}

	@Transactional
	public UnitResponse createUnit(Resource resource, UnitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requireResource(resource, Resource.UNIT);
		validateUnitRequest(request);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into %s (
						code, name, precision_scale, status, sort_order, remark,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""".formatted(resource.table()), Long.class, request.code(), request.name(),
					request.precisionScale(), statusOrEnabled(request.status()).name(), request.sortOrder(),
					blankToNull(request.remark()), operator.username(), now, operator.username(), now);
			this.auditService.record(operator, resource.createAction(), resource.targetType(), id, request.code(),
					servletRequest);
			return getUnit(resource, id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public UnitResponse updateUnit(Resource resource, Long id, UnitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requireResource(resource, Resource.UNIT);
		validateUnitRequest(request);
		MasterDataStatus currentStatus = currentStatus(resource, id);
		MasterDataStatus status = statusOrCurrent(request.status(), currentStatus);
		if (status == MasterDataStatus.DISABLED && currentStatus != MasterDataStatus.DISABLED) {
			validateUnitNotUsedByEnabledMaterial(id);
		}
		try {
			int updated = this.jdbcTemplate.update("""
					update %s
					set code = ?, name = ?, precision_scale = ?, status = ?, sort_order = ?, remark = ?,
						updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""".formatted(resource.table()), request.code(), request.name(), request.precisionScale(),
					status.name(), request.sortOrder(), blankToNull(request.remark()), operator.username(),
					OffsetDateTime.now(), id);
			if (updated == 0) {
				throw notFound();
			}
			this.auditService.record(operator, resource.updateAction(), resource.targetType(), id, request.code(),
					servletRequest);
			return getUnit(resource, id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public UnitResponse enableUnit(Resource resource, Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		requireResource(resource, Resource.UNIT);
		changeStatus(resource, id, MasterDataStatus.ENABLED, operator, servletRequest);
		return getUnit(resource, id);
	}

	@Transactional
	public UnitResponse disableUnit(Resource resource, Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requireResource(resource, Resource.UNIT);
		validateUnitNotUsedByEnabledMaterial(id);
		changeStatus(resource, id, MasterDataStatus.DISABLED, operator, servletRequest);
		return getUnit(resource, id);
	}

	@Transactional(readOnly = true)
	public PageResponse<WarehouseResponse> listWarehouses(Resource resource, String keyword, String status, int page,
			int pageSize) {
		requireResource(resource, Resource.WAREHOUSE);
		QueryParts queryParts = queryParts(resource, keyword, status);
		long total = total(resource, queryParts);
		List<Object> args = paginationArgs(queryParts, page, pageSize);
		List<WarehouseResponse> items = this.jdbcTemplate.query("""
				select id, code, name, warehouse_type, manager_name, address, status, remark, created_at, updated_at
				from %s
				%s
				order by id desc
				limit ? offset ?
				""".formatted(resource.table(), queryParts.where()), this::mapWarehouse, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public WarehouseResponse getWarehouse(Resource resource, Long id) {
		requireResource(resource, Resource.WAREHOUSE);
		return this.jdbcTemplate.query("""
				select id, code, name, warehouse_type, manager_name, address, status, remark, created_at, updated_at
				from %s
				where id = ?
				""".formatted(resource.table()), this::mapWarehouse, id)
			.stream()
			.findFirst()
			.orElseThrow(this::notFound);
	}

	@Transactional
	public WarehouseResponse createWarehouse(Resource resource, WarehouseRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requireResource(resource, Resource.WAREHOUSE);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into %s (
						code, name, warehouse_type, manager_name, address, status, remark,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""".formatted(resource.table()), Long.class, request.code(), request.name(),
					blankToNull(request.warehouseType()), blankToNull(request.managerName()),
					blankToNull(request.address()), statusOrEnabled(request.status()).name(),
					blankToNull(request.remark()), operator.username(), now, operator.username(), now);
			this.auditService.record(operator, resource.createAction(), resource.targetType(), id, request.code(),
					servletRequest);
			return getWarehouse(resource, id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public WarehouseResponse updateWarehouse(Resource resource, Long id, WarehouseRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requireResource(resource, Resource.WAREHOUSE);
		MasterDataStatus status = statusOrCurrent(request.status(), currentStatus(resource, id));
		try {
			int updated = this.jdbcTemplate.update("""
					update %s
					set code = ?, name = ?, warehouse_type = ?, manager_name = ?, address = ?, status = ?, remark = ?,
						updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""".formatted(resource.table()), request.code(), request.name(),
					blankToNull(request.warehouseType()), blankToNull(request.managerName()),
					blankToNull(request.address()), status.name(), blankToNull(request.remark()), operator.username(),
					OffsetDateTime.now(), id);
			if (updated == 0) {
				throw notFound();
			}
			this.auditService.record(operator, resource.updateAction(), resource.targetType(), id, request.code(),
					servletRequest);
			return getWarehouse(resource, id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public WarehouseResponse enableWarehouse(Resource resource, Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requireResource(resource, Resource.WAREHOUSE);
		changeStatus(resource, id, MasterDataStatus.ENABLED, operator, servletRequest);
		return getWarehouse(resource, id);
	}

	@Transactional
	public WarehouseResponse disableWarehouse(Resource resource, Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requireResource(resource, Resource.WAREHOUSE);
		changeStatus(resource, id, MasterDataStatus.DISABLED, operator, servletRequest);
		return getWarehouse(resource, id);
	}

	@Transactional(readOnly = true)
	public PageResponse<PartnerResponse> listPartners(Resource resource, String keyword, String status, int page,
			int pageSize) {
		requirePartnerResource(resource);
		QueryParts queryParts = queryParts(resource, keyword, status);
		long total = total(resource, queryParts);
		List<Object> args = paginationArgs(queryParts, page, pageSize);
		List<PartnerResponse> items = this.jdbcTemplate.query("""
				select id, code, name, contact_name, contact_phone, status, remark, created_at, updated_at
				from %s
				%s
				order by id desc
				limit ? offset ?
				""".formatted(resource.table(), queryParts.where()), this::mapPartner, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PartnerResponse getPartner(Resource resource, Long id) {
		requirePartnerResource(resource);
		return this.jdbcTemplate.query("""
				select id, code, name, contact_name, contact_phone, status, remark, created_at, updated_at
				from %s
				where id = ?
				""".formatted(resource.table()), this::mapPartner, id).stream().findFirst().orElseThrow(this::notFound);
	}

	@Transactional
	public PartnerResponse createPartner(Resource resource, PartnerRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requirePartnerResource(resource);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into %s (
						code, name, contact_name, contact_phone, status, remark,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""".formatted(resource.table()), Long.class, request.code(), request.name(),
					blankToNull(request.contactName()), blankToNull(request.contactPhone()),
					statusOrEnabled(request.status()).name(), blankToNull(request.remark()), operator.username(), now,
					operator.username(), now);
			this.auditService.record(operator, resource.createAction(), resource.targetType(), id, request.code(),
					servletRequest);
			return getPartner(resource, id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public PartnerResponse updatePartner(Resource resource, Long id, PartnerRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requirePartnerResource(resource);
		MasterDataStatus status = statusOrCurrent(request.status(), currentStatus(resource, id));
		try {
			int updated = this.jdbcTemplate.update("""
					update %s
					set code = ?, name = ?, contact_name = ?, contact_phone = ?, status = ?, remark = ?,
						updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""".formatted(resource.table()), request.code(), request.name(), blankToNull(request.contactName()),
					blankToNull(request.contactPhone()), status.name(), blankToNull(request.remark()),
					operator.username(), OffsetDateTime.now(), id);
			if (updated == 0) {
				throw notFound();
			}
			this.auditService.record(operator, resource.updateAction(), resource.targetType(), id, request.code(),
					servletRequest);
			return getPartner(resource, id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public PartnerResponse enablePartner(Resource resource, Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requirePartnerResource(resource);
		changeStatus(resource, id, MasterDataStatus.ENABLED, operator, servletRequest);
		return getPartner(resource, id);
	}

	@Transactional
	public PartnerResponse disablePartner(Resource resource, Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		requirePartnerResource(resource);
		changeStatus(resource, id, MasterDataStatus.DISABLED, operator, servletRequest);
		return getPartner(resource, id);
	}

	private QueryParts queryParts(Resource resource, String keyword, String status) {
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
			args.add(parseStatus(status).name());
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args);
	}

	private long total(Resource resource, QueryParts queryParts) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + resource.table() + " " + queryParts.where(),
				Long.class, queryParts.args().toArray());
	}

	private List<Object> paginationArgs(QueryParts queryParts, int page, int pageSize) {
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		return args;
	}

	private UnitResponse mapUnit(ResultSet rs, int rowNum) throws SQLException {
		return new UnitResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				MasterDataStatus.valueOf(rs.getString("status")), rs.getString("remark"),
				rs.getObject("precision_scale", Integer.class), rs.getObject("sort_order", Integer.class),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private WarehouseResponse mapWarehouse(ResultSet rs, int rowNum) throws SQLException {
		return new WarehouseResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				MasterDataStatus.valueOf(rs.getString("status")), rs.getString("remark"),
				rs.getString("warehouse_type"), rs.getString("manager_name"), rs.getString("address"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private PartnerResponse mapPartner(ResultSet rs, int rowNum) throws SQLException {
		return new PartnerResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				MasterDataStatus.valueOf(rs.getString("status")), rs.getString("remark"),
				rs.getString("contact_name"), rs.getString("contact_phone"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private void changeStatus(Resource resource, Long id, MasterDataStatus status, CurrentUser operator,
			HttpServletRequest servletRequest) {
		currentStatus(resource, id);
		int updated = this.jdbcTemplate.update("""
				update %s
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""".formatted(resource.table()), status.name(), operator.username(), OffsetDateTime.now(), id);
		if (updated == 0) {
			throw notFound();
		}
		this.auditService.record(operator, status == MasterDataStatus.ENABLED ? resource.enableAction()
				: resource.disableAction(), resource.targetType(), id, code(resource, id), servletRequest);
	}

	private MasterDataStatus currentStatus(Resource resource, Long id) {
		return this.jdbcTemplate
			.query("select status from " + resource.table() + " where id = ?",
					(rs, rowNum) -> MasterDataStatus.valueOf(rs.getString("status")), id)
			.stream()
			.findFirst()
			.orElseThrow(this::notFound);
	}

	private String code(Resource resource, Long id) {
		return this.jdbcTemplate.queryForObject("select code from " + resource.table() + " where id = ?", String.class,
				id);
	}

	private void validateUnitNotUsedByEnabledMaterial(Long unitId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mst_material
				where unit_id = ?
				and status = 'ENABLED'
				""", Long.class, unitId);
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_UNIT_IN_USE);
		}
	}

	private void validateUnitRequest(UnitRequest request) {
		if (request.precisionScale() == null || request.sortOrder() == null || request.precisionScale() < 0) {
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

	private void requireResource(Resource actual, Resource expected) {
		if (actual != expected) {
			throw new IllegalArgumentException("资源类型不匹配");
		}
	}

	private void requirePartnerResource(Resource resource) {
		if (resource != Resource.SUPPLIER && resource != Resource.CUSTOMER) {
			throw new IllegalArgumentException("资源类型不匹配");
		}
	}

	private BusinessException codeExists() {
		return new BusinessException(ApiErrorCode.MASTER_DATA_CODE_EXISTS);
	}

	private BusinessException notFound() {
		return new BusinessException(ApiErrorCode.MASTER_DATA_NOT_FOUND);
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

	public enum Resource {

		UNIT("mst_unit", "UNIT"),

		WAREHOUSE("mst_warehouse", "WAREHOUSE"),

		SUPPLIER("mst_supplier", "SUPPLIER"),

		CUSTOMER("mst_customer", "CUSTOMER");

		private final String table;

		private final String targetType;

		Resource(String table, String targetType) {
			this.table = table;
			this.targetType = targetType;
		}

		String table() {
			return this.table;
		}

		String targetType() {
			return this.targetType;
		}

		String createAction() {
			return this.targetType + "_CREATE";
		}

		String updateAction() {
			return this.targetType + "_UPDATE";
		}

		String enableAction() {
			return this.targetType + "_ENABLE";
		}

		String disableAction() {
			return this.targetType + "_DISABLE";
		}

	}

	public record UnitRequest(@NotBlank String code, @NotBlank String name, @NotNull @Min(0) Integer precisionScale,
			@NotNull Integer sortOrder, String status, String remark) {
	}

	public record WarehouseRequest(@NotBlank String code, @NotBlank String name, String warehouseType,
			String managerName, String address, String status, String remark) {
	}

	public record PartnerRequest(@NotBlank String code, @NotBlank String name, String contactName,
			String contactPhone, String status, String remark) {
	}

	public record UnitResponse(Long id, String code, String name, MasterDataStatus status, String remark,
			Integer precisionScale, Integer sortOrder, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record WarehouseResponse(Long id, String code, String name, MasterDataStatus status, String remark,
			String warehouseType, String managerName, String address, OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	public record PartnerResponse(Long id, String code, String name, MasterDataStatus status, String remark,
			String contactName, String contactPhone, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
