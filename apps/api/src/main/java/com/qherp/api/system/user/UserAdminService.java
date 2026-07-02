package com.qherp.api.system.user;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class UserAdminService {

	private final JdbcTemplate jdbcTemplate;

	private final PasswordEncoder passwordEncoder;

	private final AuditService auditService;

	public UserAdminService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.passwordEncoder = passwordEncoder;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<UserResponse> list(String keyword, String status, int page, int pageSize) {
		QueryParts queryParts = queryParts(keyword, status);
		long total = this.jdbcTemplate.queryForObject("select count(*) from sys_user " + queryParts.where(),
				Long.class, queryParts.args().toArray());

		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<UserResponse> items = this.jdbcTemplate.query("""
				select id, username, display_name, phone, email, status, last_login_at, created_at, updated_at
				from sys_user
				%s
				order by id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapUser, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public UserResponse get(Long id) {
		return this.jdbcTemplate.query("""
				select id, username, display_name, phone, email, status, last_login_at, created_at, updated_at
				from sys_user
				where id = ?
				""", this::mapUser, id)
			.stream()
			.findFirst()
			.orElseThrow(() -> notFound("用户不存在"));
	}

	@Transactional
	public UserResponse create(CreateUserRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		if (usernameExists(request.username())) {
			throw new BusinessException(ApiErrorCode.AUTH_USERNAME_EXISTS);
		}
		validateEnabledRoles(request.roleIds());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into sys_user (
						username, password_hash, display_name, phone, email, status, password_changed_at,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, request.username(), this.passwordEncoder.encode(request.initialPassword()),
					request.displayName(), blankToNull(request.phone()), blankToNull(request.email()),
					statusOrEnabled(request.status()).name(), now, operator.username(), now, operator.username(), now);
			replaceRoles(id, request.roleIds(), operator.username());
			this.auditService.record(operator, "USER_CREATE", "USER", id, request.username(), servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.AUTH_USERNAME_EXISTS);
		}
	}

	@Transactional
	public UserResponse update(Long id, UpdateUserRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateUserExists(id);
		validateEnabledRoles(request.roleIds());
		int updated = this.jdbcTemplate.update("""
				update sys_user
				set display_name = ?, phone = ?, email = ?, status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", request.displayName(), blankToNull(request.phone()), blankToNull(request.email()),
				statusOrEnabled(request.status()).name(), operator.username(), OffsetDateTime.now(), id);
		if (updated == 0) {
			throw notFound("用户不存在");
		}
		replaceRoles(id, request.roleIds(), operator.username());
		this.auditService.record(operator, "USER_UPDATE", "USER", id, username(id), servletRequest);
		return get(id);
	}

	@Transactional
	public UserResponse resetPassword(Long id, ResetPasswordRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateUserExists(id);
		int updated = this.jdbcTemplate.update("""
				update sys_user
				set password_hash = ?, password_changed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", this.passwordEncoder.encode(request.newPassword()), OffsetDateTime.now(), operator.username(),
				OffsetDateTime.now(), id);
		if (updated == 0) {
			throw notFound("用户不存在");
		}
		this.auditService.record(operator, "USER_PASSWORD_RESET", "USER", id, username(id), servletRequest);
		return get(id);
	}

	@Transactional
	public UserResponse enable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		return changeStatus(id, SystemUserStatus.ENABLED, operator, servletRequest);
	}

	@Transactional
	public UserResponse disable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		return changeStatus(id, SystemUserStatus.DISABLED, operator, servletRequest);
	}

	private UserResponse changeStatus(Long id, SystemUserStatus status, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateUserExists(id);
		int updated = this.jdbcTemplate.update("""
				update sys_user
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", status.name(), operator.username(), OffsetDateTime.now(), id);
		if (updated == 0) {
			throw notFound("用户不存在");
		}
		this.auditService.record(operator, status == SystemUserStatus.ENABLED ? "USER_ENABLE" : "USER_DISABLE", "USER",
				id, username(id), servletRequest);
		return get(id);
	}

	private QueryParts queryParts(String keyword, String status) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(username ilike ? or display_name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
		}
		if (hasText(status)) {
			conditions.add("status = ?");
			args.add(status);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args);
	}

	private UserResponse mapUser(ResultSet rs, int rowNum) throws SQLException {
		Long id = rs.getLong("id");
		return new UserResponse(id, rs.getString("username"), rs.getString("display_name"), rs.getString("phone"),
				rs.getString("email"), SystemUserStatus.valueOf(rs.getString("status")),
				findRoles(id), rs.getObject("last_login_at", OffsetDateTime.class),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private List<RoleSummary> findRoles(Long userId) {
		return this.jdbcTemplate.query("""
				select r.id, r.code, r.name
				from sys_role r
				join sys_user_role ur on ur.role_id = r.id
				where ur.user_id = ?
				order by r.sort_order, r.id
				""", (rs, rowNum) -> new RoleSummary(rs.getLong("id"), rs.getString("code"), rs.getString("name")),
				userId);
	}

	private void replaceRoles(Long userId, List<Long> roleIds, String operator) {
		this.jdbcTemplate.update("delete from sys_user_role where user_id = ?", userId);
		for (Long roleId : distinctIds(roleIds)) {
			this.jdbcTemplate.update("""
					insert into sys_user_role (user_id, role_id, created_by, created_at)
					values (?, ?, ?, ?)
					on conflict (user_id, role_id) do nothing
					""", userId, roleId, operator, OffsetDateTime.now());
		}
	}

	private void validateEnabledRoles(List<Long> roleIds) {
		List<Long> ids = distinctIds(roleIds);
		if (ids.isEmpty()) {
			return;
		}
		String placeholders = placeholders(ids.size());
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_role
				where status = 'ENABLED'
				and id in (%s)
				""".formatted(placeholders), Long.class, ids.toArray());
		if (count == null || count != ids.size()) {
			throw new BusinessException(ApiErrorCode.AUTH_ROLE_DISABLED);
		}
	}

	private void validateUserExists(Long id) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from sys_user where id = ?", Long.class, id);
		if (count == null || count == 0) {
			throw notFound("用户不存在");
		}
	}

	private String username(Long id) {
		return this.jdbcTemplate.queryForObject("select username from sys_user where id = ?", String.class, id);
	}

	private boolean usernameExists(String username) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from sys_user where username = ?", Long.class,
				username);
		return count != null && count > 0;
	}

	private SystemUserStatus statusOrEnabled(String status) {
		return hasText(status) ? SystemUserStatus.valueOf(status) : SystemUserStatus.ENABLED;
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

	public record CreateUserRequest(@NotBlank String username, @NotBlank String displayName, String phone,
			String email, @NotBlank String initialPassword, String status, List<Long> roleIds) {
	}

	public record UpdateUserRequest(@NotBlank String displayName, String phone, String email, String status,
			List<Long> roleIds) {
	}

	public record ResetPasswordRequest(@NotBlank String newPassword) {
	}

	public record UserResponse(Long id, String username, String displayName, String phone, String email,
			SystemUserStatus status, List<RoleSummary> roles, OffsetDateTime lastLoginAt, OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	public record RoleSummary(Long id, String code, String name) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
