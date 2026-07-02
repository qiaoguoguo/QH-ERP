package com.qherp.api.system.init;

import com.qherp.api.system.permission.SystemPermission;
import com.qherp.api.system.permission.SystemPermissionRepository;
import com.qherp.api.system.permission.SystemPermissionType;
import com.qherp.api.system.permission.SystemRolePermission;
import com.qherp.api.system.permission.SystemRolePermissionRepository;
import com.qherp.api.system.role.SystemRole;
import com.qherp.api.system.role.SystemRoleRepository;
import com.qherp.api.system.role.SystemRoleStatus;
import com.qherp.api.system.user.SystemUser;
import com.qherp.api.system.user.SystemUserRepository;
import com.qherp.api.system.user.SystemUserRole;
import com.qherp.api.system.user.SystemUserRoleRepository;
import com.qherp.api.system.user.SystemUserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
public class AccountPermissionInitializer implements ApplicationRunner {

	private static final String SYSTEM_OPERATOR = "system";

	private static final String ADMIN_USERNAME = "admin";

	private static final String SYSTEM_ADMIN_ROLE_CODE = "SYSTEM_ADMIN";

	private static final List<PermissionSeed> PERMISSION_SEEDS = List.of(
			new PermissionSeed("system", "系统管理", SystemPermissionType.MENU, null, "/system", null, null, 0),
			new PermissionSeed("system:user", "用户管理", SystemPermissionType.MENU, "system", "/system/users", null,
					null, 10),
			new PermissionSeed("system:user:view", "查看用户", SystemPermissionType.ACTION, "system:user",
					"/system/users", "GET", "/api/admin/users/**", 11),
			new PermissionSeed("system:user:create", "创建用户", SystemPermissionType.ACTION, "system:user",
					"/system/users", "POST", "/api/admin/users", 12),
			new PermissionSeed("system:user:update", "更新、启用、停用用户", SystemPermissionType.ACTION, "system:user",
					"/system/users", "PUT", "/api/admin/users/**", 13),
			new PermissionSeed("system:user:reset-password", "重置用户密码", SystemPermissionType.ACTION, "system:user",
					"/system/users", "PUT", "/api/admin/users/{id}/password", 14),
			new PermissionSeed("system:role", "角色管理", SystemPermissionType.MENU, "system", "/system/roles", null,
					null, 20),
			new PermissionSeed("system:role:view", "查看角色", SystemPermissionType.ACTION, "system:role",
					"/system/roles", "GET", "/api/admin/roles/**", 21),
			new PermissionSeed("system:role:create", "创建角色", SystemPermissionType.ACTION, "system:role",
					"/system/roles", "POST", "/api/admin/roles", 22),
			new PermissionSeed("system:role:update", "更新、启用、停用角色", SystemPermissionType.ACTION, "system:role",
					"/system/roles", "PUT", "/api/admin/roles/**", 23),
			new PermissionSeed("system:role:assign-permission", "分配角色权限", SystemPermissionType.ACTION,
					"system:role", "/system/roles", "PUT", "/api/admin/roles/{id}/permissions", 24),
			new PermissionSeed("system:permission", "权限管理", SystemPermissionType.MENU, "system",
					"/system/permissions", null, null, 30),
			new PermissionSeed("system:permission:view", "查看权限树", SystemPermissionType.ACTION, "system:permission",
					"/system/permissions", "GET", "/api/admin/permissions/tree", 31),
			new PermissionSeed("system:audit", "审计日志", SystemPermissionType.MENU, "system", "/system/audit-logs",
					null, null, 40),
			new PermissionSeed("system:audit:view", "查看账号权限审计", SystemPermissionType.ACTION, "system:audit",
					"/system/audit-logs", "GET", "/api/admin/audit-logs", 41));

	private final SystemUserRepository userRepository;

	private final SystemRoleRepository roleRepository;

	private final SystemPermissionRepository permissionRepository;

	private final SystemUserRoleRepository userRoleRepository;

	private final SystemRolePermissionRepository rolePermissionRepository;

	private final PasswordEncoder passwordEncoder;

	private final String initialAdminPassword;

	public AccountPermissionInitializer(SystemUserRepository userRepository, SystemRoleRepository roleRepository,
			SystemPermissionRepository permissionRepository, SystemUserRoleRepository userRoleRepository,
			SystemRolePermissionRepository rolePermissionRepository, PasswordEncoder passwordEncoder,
			@Value("${qherp.account-permission.initial-admin-password:Qherp@2026!}") String initialAdminPassword) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.permissionRepository = permissionRepository;
		this.userRoleRepository = userRoleRepository;
		this.rolePermissionRepository = rolePermissionRepository;
		this.passwordEncoder = passwordEncoder;
		this.initialAdminPassword = initialAdminPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		initialize();
	}

	@Transactional
	public void initialize() {
		SystemUser admin = this.userRepository.findByUsername(ADMIN_USERNAME)
			.orElseGet(() -> this.userRepository.save(new SystemUser(ADMIN_USERNAME,
					this.passwordEncoder.encode(this.initialAdminPassword), "超级管理员", SystemUserStatus.ENABLED,
					SYSTEM_OPERATOR)));

		SystemRole systemAdmin = this.roleRepository.findByCode(SYSTEM_ADMIN_ROLE_CODE)
			.orElseGet(() -> this.roleRepository
				.save(new SystemRole(SYSTEM_ADMIN_ROLE_CODE, "系统管理员", SystemRoleStatus.ENABLED, 0, SYSTEM_OPERATOR)));

		Map<String, SystemPermission> permissionsByCode = new LinkedHashMap<>();
		for (PermissionSeed permission : PERMISSION_SEEDS) {
			Long parentId = permission.parentCode() == null ? null : permissionsByCode.get(permission.parentCode()).getId();
			SystemPermission savedPermission = this.permissionRepository.findByCode(permission.code())
				.orElseGet(() -> this.permissionRepository.save(new SystemPermission(permission.code(), permission.name(),
						permission.type(), parentId, permission.routePath(), permission.apiMethod(), permission.apiPath(),
						permission.sortOrder(), SYSTEM_OPERATOR)));
			permissionsByCode.put(permission.code(), savedPermission);
		}

		if (!this.userRoleRepository.existsByUserIdAndRoleId(admin.getId(), systemAdmin.getId())) {
			this.userRoleRepository.save(new SystemUserRole(admin.getId(), systemAdmin.getId(), SYSTEM_OPERATOR));
		}

		for (SystemPermission permission : permissionsByCode.values()) {
			if (!this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), permission.getId())) {
				this.rolePermissionRepository
					.save(new SystemRolePermission(systemAdmin.getId(), permission.getId(), SYSTEM_OPERATOR));
			}
		}
	}

	private record PermissionSeed(String code, String name, SystemPermissionType type, String parentCode,
			String routePath, String apiMethod, String apiPath, int sortOrder) {
	}

}
