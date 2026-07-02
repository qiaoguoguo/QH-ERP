package com.qherp.api.system.master;

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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MaterialCategoryAdminService {

	private static final String TARGET_TYPE = "MATERIAL_CATEGORY";

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public MaterialCategoryAdminService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<CategoryResponse> list(String keyword, String status, int page, int pageSize) {
		QueryParts queryParts = queryParts(keyword, status);
		long total = this.jdbcTemplate.queryForObject("select count(*) from mst_material_category " + queryParts.where(),
				Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<CategoryResponse> items = this.jdbcTemplate.query("""
				select id, code, name, parent_id, status, sort_order, remark, created_at, updated_at
				from mst_material_category
				%s
				order by sort_order asc, id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapCategory, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public CategoryResponse get(Long id) {
		return this.jdbcTemplate.query("""
				select id, code, name, parent_id, status, sort_order, remark, created_at, updated_at
				from mst_material_category
				where id = ?
				""", this::mapCategory, id).stream().findFirst().orElseThrow(this::notFound);
	}

	@Transactional
	public CategoryResponse create(CategoryRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		validateCategoryRequest(request);
		validateParentChain(null, request.parentId());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into mst_material_category (
						code, name, parent_id, status, sort_order, remark,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, request.code(), request.name(), request.parentId(),
					statusOrEnabled(request.status()).name(), request.sortOrder(), blankToNull(request.remark()),
					operator.username(), now, operator.username(), now);
			this.auditService.record(operator, "CATEGORY_CREATE", TARGET_TYPE, id, request.code(), servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public CategoryResponse update(Long id, CategoryRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateCategoryRequest(request);
		CategoryNode current = categoryNode(id).orElseThrow(this::notFound);
		validateParentChain(current.id(), request.parentId());
		MasterDataStatus status = statusOrCurrent(request.status(), current.status());
		if (status == MasterDataStatus.DISABLED && current.status() != MasterDataStatus.DISABLED) {
			validateNotUsedByEnabledData(id);
		}
		try {
			int updated = this.jdbcTemplate.update("""
					update mst_material_category
					set code = ?, name = ?, parent_id = ?, status = ?, sort_order = ?, remark = ?,
						updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", request.code(), request.name(), request.parentId(), status.name(), request.sortOrder(),
					blankToNull(request.remark()), operator.username(), OffsetDateTime.now(), id);
			if (updated == 0) {
				throw notFound();
			}
			this.auditService.record(operator, "CATEGORY_UPDATE", TARGET_TYPE, id, request.code(), servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw codeExists();
		}
	}

	@Transactional
	public CategoryResponse enable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		CategoryNode current = categoryNode(id).orElseThrow(this::notFound);
		validateParentChain(current.id(), current.parentId());
		changeStatus(id, MasterDataStatus.ENABLED, operator, servletRequest);
		return get(id);
	}

	@Transactional
	public CategoryResponse disable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		categoryNode(id).orElseThrow(this::notFound);
		validateNotUsedByEnabledData(id);
		changeStatus(id, MasterDataStatus.DISABLED, operator, servletRequest);
		return get(id);
	}

	private QueryParts queryParts(String keyword, String status) {
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

	private CategoryResponse mapCategory(ResultSet rs, int rowNum) throws SQLException {
		return new CategoryResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				nullableLong(rs, "parent_id"), rs.getString("status"), rs.getObject("sort_order", Integer.class),
				rs.getString("remark"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class));
	}

	private java.util.Optional<CategoryNode> categoryNode(Long id) {
		return this.jdbcTemplate
			.query("""
					select id, parent_id, status, code
					from mst_material_category
					where id = ?
					""", (rs, rowNum) -> new CategoryNode(rs.getLong("id"), nullableLong(rs, "parent_id"),
					MasterDataStatus.valueOf(rs.getString("status")), rs.getString("code")), id)
			.stream()
			.findFirst();
	}

	private void validateCategoryRequest(CategoryRequest request) {
		if (request.sortOrder() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateParentChain(Long categoryId, Long parentId) {
		if (parentId == null) {
			return;
		}
		if (categoryId != null && parentId.equals(categoryId)) {
			throw parentInvalid();
		}
		Set<Long> visited = new HashSet<>();
		Long currentId = parentId;
		while (currentId != null) {
			if (categoryId != null && currentId.equals(categoryId)) {
				throw parentInvalid();
			}
			if (!visited.add(currentId)) {
				throw parentInvalid();
			}
			CategoryNode parent = categoryNode(currentId).orElseThrow(this::parentInvalid);
			if (parent.status() != MasterDataStatus.ENABLED) {
				throw parentInvalid();
			}
			currentId = parent.parentId();
		}
	}

	private boolean hasEnabledChild(Long id) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mst_material_category
				where parent_id = ?
				and status = 'ENABLED'
				""", Long.class, id);
		return count != null && count > 0;
	}

	private boolean hasEnabledMaterial(Long id) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mst_material
				where category_id = ?
				and status = 'ENABLED'
				""", Long.class, id);
		return count != null && count > 0;
	}

	private void validateNotUsedByEnabledData(Long id) {
		if (hasEnabledChild(id) || hasEnabledMaterial(id)) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_CATEGORY_IN_USE);
		}
	}

	private void changeStatus(Long id, MasterDataStatus status, CurrentUser operator, HttpServletRequest servletRequest) {
		int updated = this.jdbcTemplate.update("""
				update mst_material_category
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", status.name(), operator.username(), OffsetDateTime.now(), id);
		if (updated == 0) {
			throw notFound();
		}
		this.auditService.record(operator,
				status == MasterDataStatus.ENABLED ? "CATEGORY_ENABLE" : "CATEGORY_DISABLE", TARGET_TYPE, id, code(id),
				servletRequest);
	}

	private String code(Long id) {
		return this.jdbcTemplate.queryForObject("select code from mst_material_category where id = ?", String.class, id);
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

	private BusinessException parentInvalid() {
		return new BusinessException(ApiErrorCode.MASTER_DATA_CATEGORY_PARENT_INVALID);
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

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record CategoryRequest(@NotBlank String code, @NotBlank String name, Long parentId, String status,
			Integer sortOrder, String remark) {

		public CategoryRequest {
			if (sortOrder == null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
		}

	}

	public record CategoryResponse(Long id, String code, String name, Long parentId, String status, Integer sortOrder,
			String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	private record CategoryNode(Long id, Long parentId, MasterDataStatus status, String code) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
