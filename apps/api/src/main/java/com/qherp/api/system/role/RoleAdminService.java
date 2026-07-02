package com.qherp.api.system.role;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RoleAdminService {

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public RoleAdminService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<RoleResponse> list(String keyword, String status, int page, int pageSize) {
		QueryParts queryParts = queryParts(keyword, status);
		long total = this.jdbcTemplate.queryForObject("select count(*) from sys_role " + queryParts.where(),
				Long.class, queryParts.args().toArray());

		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<RoleResponse> items = this.jdbcTemplate.query("""
				select id, code, name, description, status, sort_order, created_at, updated_at
				from sys_role
				%s
				order by sort_order, id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapRole, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public RoleResponse get(Long id) {
		return this.jdbcTemplate.query("""
				select id, code, name, description, status, sort_order, created_at, updated_at
				from sys_role
				where id = ?
				""", this::mapRole, id)
			.stream()
			.findFirst()
			.orElseThrow(() -> notFound("角色不存在"));
	}

	@Transactional
	public RoleResponse create(CreateRoleRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		if (codeExists(request.code())) {
			throw new BusinessException(ApiErrorCode.AUTH_ROLE_CODE_EXISTS);
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into sys_role (
						code, name, description, status, sort_order,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, request.code(), request.name(), blankToNull(request.description()),
					statusOrEnabled(request.status()).name(), request.sortOrder() == null ? 0 : request.sortOrder(),
					operator.username(), now, operator.username(), now);
			this.auditService.record(operator, "ROLE_CREATE", "ROLE", id, request.code(), servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.AUTH_ROLE_CODE_EXISTS);
		}
	}

	@Transactional
	public RoleResponse update(Long id, UpdateRoleRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateRoleExists(id);
		int updated = this.jdbcTemplate.update("""
				update sys_role
				set name = ?, description = ?, status = ?, sort_order = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", request.name(), blankToNull(request.description()), statusOrEnabled(request.status()).name(),
				request.sortOrder() == null ? 0 : request.sortOrder(), operator.username(), OffsetDateTime.now(), id);
		if (updated == 0) {
			throw notFound("角色不存在");
		}
		this.auditService.record(operator, "ROLE_UPDATE", "ROLE", id, code(id), servletRequest);
		return get(id);
	}

	@Transactional
	public RoleResponse savePermissions(Long id, SaveRolePermissionsRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateRoleExists(id);
		validatePermissions(request.permissionIds());
		this.jdbcTemplate.update("delete from sys_role_permission where role_id = ?", id);
		for (Long permissionId : distinctIds(request.permissionIds())) {
			this.jdbcTemplate.update("""
					insert into sys_role_permission (role_id, permission_id, created_by, created_at)
					values (?, ?, ?, ?)
					on conflict (role_id, permission_id) do nothing
					""", id, permissionId, operator.username(), OffsetDateTime.now());
		}
		this.jdbcTemplate.update("update sys_role set updated_by = ?, updated_at = ?, version = version + 1 where id = ?",
				operator.username(), OffsetDateTime.now(), id);
		this.auditService.record(operator, "ROLE_PERMISSION_UPDATE", "ROLE", id, code(id), servletRequest);
		return get(id);
	}

	@Transactional
	public RoleResponse enable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		return changeStatus(id, SystemRoleStatus.ENABLED, operator, servletRequest);
	}

	@Transactional
	public RoleResponse disable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		return changeStatus(id, SystemRoleStatus.DISABLED, operator, servletRequest);
	}

	private RoleResponse changeStatus(Long id, SystemRoleStatus status, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateRoleExists(id);
		int updated = this.jdbcTemplate.update("""
				update sys_role
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", status.name(), operator.username(), OffsetDateTime.now(), id);
		if (updated == 0) {
			throw notFound("角色不存在");
		}
		this.auditService.record(operator, status == SystemRoleStatus.ENABLED ? "ROLE_ENABLE" : "ROLE_DISABLE", "ROLE",
				id, code(id), servletRequest);
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

	private RoleResponse mapRole(ResultSet rs, int rowNum) throws SQLException {
		Long id = rs.getLong("id");
		return new RoleResponse(id, rs.getString("code"), rs.getString("name"), rs.getString("description"),
				SystemRoleStatus.valueOf(rs.getString("status")), rs.getInt("sort_order"), findPermissionIds(id),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private List<Long> findPermissionIds(Long roleId) {
		return this.jdbcTemplate.query("""
				select permission_id
				from sys_role_permission
				where role_id = ?
				order by permission_id
				""", (rs, rowNum) -> rs.getLong("permission_id"), roleId);
	}

	private void validatePermissions(List<Long> permissionIds) {
		List<Long> ids = distinctIds(permissionIds);
		if (ids.isEmpty()) {
			return;
		}
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_permission
				where id in (%s)
				""".formatted(placeholders(ids.size())), Long.class, ids.toArray());
		if (count == null || count != ids.size()) {
			throw new BusinessException(ApiErrorCode.AUTH_PERMISSION_NOT_FOUND);
		}
	}

	private void validateRoleExists(Long id) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from sys_role where id = ?", Long.class, id);
		if (count == null || count == 0) {
			throw notFound("角色不存在");
		}
	}

	private String code(Long id) {
		return this.jdbcTemplate.queryForObject("select code from sys_role where id = ?", String.class, id);
	}

	private boolean codeExists(String code) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from sys_role where code = ?", Long.class,
				code);
		return count != null && count > 0;
	}

	private SystemRoleStatus statusOrEnabled(String status) {
		return hasText(status) ? parseStatus(status) : SystemRoleStatus.ENABLED;
	}

	private SystemRoleStatus parseStatus(String status) {
		try {
			return SystemRoleStatus.valueOf(status);
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "角色状态不合法");
		}
	}

	private static List<Long> distinctIds(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return new ArrayList<>(new LinkedHashSet<>(ids));
	}

	private static String placeholders(int count) {
		return String.join(",", java.util.Collections.nCopies(count, "?"));
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

	private static BusinessException notFound(String message) {
		return new BusinessException(ApiErrorCode.CONFLICT, message, HttpStatus.NOT_FOUND);
	}

	public record CreateRoleRequest(@NotBlank String code, @NotBlank String name, String description, String status,
			Integer sortOrder) {
	}

	public record UpdateRoleRequest(@NotBlank String name, String description, String status, Integer sortOrder) {
	}

	public record SaveRolePermissionsRequest(List<Long> permissionIds) {
	}

	public record RoleResponse(Long id, String code, String name, String description, SystemRoleStatus status,
			int sortOrder, List<Long> permissionIds, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
