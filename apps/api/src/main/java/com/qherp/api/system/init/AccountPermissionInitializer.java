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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class AccountPermissionInitializer implements ApplicationRunner {

	private static final String SYSTEM_OPERATOR = "system";

	private static final String ADMIN_USERNAME = "admin";

	private static final String ADMIN_INITIAL_PASSWORD = "Admin@123456";

	private static final String SYSTEM_ADMIN_ROLE_CODE = "SYSTEM_ADMIN";

	private static final List<PermissionSeed> DOCUMENTED_PERMISSIONS = List.of(
			new PermissionSeed("system:user:view", "查看用户", SystemPermissionType.ACTION, 10),
			new PermissionSeed("system:user:create", "创建用户", SystemPermissionType.ACTION, 20),
			new PermissionSeed("system:user:update", "更新、启用、停用用户", SystemPermissionType.ACTION, 30),
			new PermissionSeed("system:user:reset-password", "重置用户密码", SystemPermissionType.ACTION, 40),
			new PermissionSeed("system:role:view", "查看角色", SystemPermissionType.ACTION, 50),
			new PermissionSeed("system:role:create", "创建角色", SystemPermissionType.ACTION, 60),
			new PermissionSeed("system:role:update", "更新、启用、停用角色", SystemPermissionType.ACTION, 70),
			new PermissionSeed("system:role:assign-permission", "分配角色权限", SystemPermissionType.ACTION, 80),
			new PermissionSeed("system:permission:view", "查看权限树", SystemPermissionType.ACTION, 90),
			new PermissionSeed("system:audit:view", "查看账号权限审计", SystemPermissionType.ACTION, 100));

	private final SystemUserRepository userRepository;

	private final SystemRoleRepository roleRepository;

	private final SystemPermissionRepository permissionRepository;

	private final SystemUserRoleRepository userRoleRepository;

	private final SystemRolePermissionRepository rolePermissionRepository;

	private final PasswordEncoder passwordEncoder;

	public AccountPermissionInitializer(SystemUserRepository userRepository, SystemRoleRepository roleRepository,
			SystemPermissionRepository permissionRepository, SystemUserRoleRepository userRoleRepository,
			SystemRolePermissionRepository rolePermissionRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.permissionRepository = permissionRepository;
		this.userRoleRepository = userRoleRepository;
		this.rolePermissionRepository = rolePermissionRepository;
		this.passwordEncoder = passwordEncoder;
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
					this.passwordEncoder.encode(ADMIN_INITIAL_PASSWORD), "超级管理员", SystemUserStatus.ENABLED,
					SYSTEM_OPERATOR)));

		SystemRole systemAdmin = this.roleRepository.findByCode(SYSTEM_ADMIN_ROLE_CODE)
			.orElseGet(() -> this.roleRepository
				.save(new SystemRole(SYSTEM_ADMIN_ROLE_CODE, "系统管理员", SystemRoleStatus.ENABLED, 0, SYSTEM_OPERATOR)));

		List<SystemPermission> permissions = DOCUMENTED_PERMISSIONS.stream()
			.map((permission) -> this.permissionRepository.findByCode(permission.code())
				.orElseGet(() -> this.permissionRepository.save(new SystemPermission(permission.code(), permission.name(),
						permission.type(), permission.sortOrder(), SYSTEM_OPERATOR))))
			.toList();

		if (!this.userRoleRepository.existsByUserIdAndRoleId(admin.getId(), systemAdmin.getId())) {
			this.userRoleRepository.save(new SystemUserRole(admin.getId(), systemAdmin.getId(), SYSTEM_OPERATOR));
		}

		for (SystemPermission permission : permissions) {
			if (!this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), permission.getId())) {
				this.rolePermissionRepository
					.save(new SystemRolePermission(systemAdmin.getId(), permission.getId(), SYSTEM_OPERATOR));
			}
		}
	}

	private record PermissionSeed(String code, String name, SystemPermissionType type, int sortOrder) {
	}

}
