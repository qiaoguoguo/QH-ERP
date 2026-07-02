package com.qherp.api.security;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.system.user.SystemUserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CurrentUserService {

	private final JdbcTemplate jdbcTemplate;

	public CurrentUserService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public Optional<CurrentUser> findCurrentUser(Long userId) {
		List<UserRow> users = this.jdbcTemplate.query("""
				select id, username, display_name, status
				from sys_user
				where id = ?
				and status = 'ENABLED'
				""", (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getString("username"),
				rs.getString("display_name"), SystemUserStatus.valueOf(rs.getString("status"))), userId);

		if (users.isEmpty()) {
			return Optional.empty();
		}

		UserRow user = users.getFirst();
		List<CurrentUser.Role> roles = findEnabledRoles(user.id());
		List<Long> roleIds = roles.stream().map(CurrentUser.Role::id).toList();
		List<PermissionRow> permissionRows = roleIds.isEmpty() ? List.of() : findPermissions(roleIds);
		List<String> permissionCodes = permissionRows.stream()
			.filter((permission) -> !"MENU".equals(permission.type()))
			.map(PermissionRow::code)
			.distinct()
			.toList();
		List<CurrentUser.Menu> menus = buildMenus(permissionRows);

		return Optional.of(new CurrentUser(user.id(), user.username(), user.displayName(), user.status(), roles, menus,
				permissionCodes));
	}

	public CurrentUser requireCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) {
			return currentUser;
		}
		throw new BusinessException(ApiErrorCode.AUTH_UNAUTHORIZED);
	}

	private List<CurrentUser.Role> findEnabledRoles(Long userId) {
		return this.jdbcTemplate.query("""
				select distinct r.id, r.code, r.name, r.sort_order
				from sys_role r
				join sys_user_role ur on ur.role_id = r.id
				where ur.user_id = ?
				and r.status = 'ENABLED'
				order by r.sort_order, r.id
				""", (rs, rowNum) -> new RoleRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getInt("sort_order")), userId)
			.stream()
			.map((role) -> new CurrentUser.Role(role.id(), role.code(), role.name()))
			.toList();
	}

	private List<PermissionRow> findPermissions(List<Long> roleIds) {
		String placeholders = String.join(",", roleIds.stream().map((ignored) -> "?").toList());
		Object[] args = roleIds.toArray();
		return this.jdbcTemplate.query("""
				select distinct p.id, p.code, p.name, p.type, p.parent_id, p.route_path, p.sort_order
				from sys_permission p
				join sys_role_permission rp on rp.permission_id = p.id
				where rp.role_id in (%s)
				order by p.sort_order, p.id
				""".formatted(placeholders), (rs, rowNum) -> new PermissionRow(rs.getLong("id"), rs.getString("code"),
				rs.getString("name"), rs.getString("type"), nullableLong(rs, "parent_id"), rs.getString("route_path"),
				rs.getInt("sort_order")), args);
	}

	private List<CurrentUser.Menu> buildMenus(List<PermissionRow> permissions) {
		Map<Long, PermissionRow> menusById = new LinkedHashMap<>();
		for (PermissionRow permission : permissions) {
			if ("MENU".equals(permission.type())) {
				menusById.put(permission.id(), permission);
			}
		}

		Map<Long, List<PermissionRow>> childrenByParent = new LinkedHashMap<>();
		for (PermissionRow menu : menusById.values()) {
			if (menu.parentId() != null && menusById.containsKey(menu.parentId())) {
				childrenByParent.computeIfAbsent(menu.parentId(), (ignored) -> new ArrayList<>()).add(menu);
			}
		}

		return menusById.values()
			.stream()
			.filter((menu) -> menu.parentId() == null || !menusById.containsKey(menu.parentId()))
			.sorted(Comparator.comparingInt(PermissionRow::sortOrder).thenComparingLong(PermissionRow::id))
			.map((menu) -> toMenu(menu, childrenByParent))
			.toList();
	}

	private CurrentUser.Menu toMenu(PermissionRow menu, Map<Long, List<PermissionRow>> childrenByParent) {
		List<CurrentUser.Menu> children = childrenByParent.getOrDefault(menu.id(), List.of())
			.stream()
			.sorted(Comparator.comparingInt(PermissionRow::sortOrder).thenComparingLong(PermissionRow::id))
			.map((child) -> toMenu(child, childrenByParent))
			.toList();
		return new CurrentUser.Menu(menu.id(), menu.code(), menu.name(), menu.routePath(), children);
	}

	private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private record UserRow(Long id, String username, String displayName, SystemUserStatus status) {
	}

	private record RoleRow(Long id, String code, String name, int sortOrder) {
	}

	private record PermissionRow(Long id, String code, String name, String type, Long parentId, String routePath,
			int sortOrder) {
	}

}
