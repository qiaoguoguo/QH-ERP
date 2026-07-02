package com.qherp.api.system.init;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.audit.SystemAuditLogRepository;
import com.qherp.api.system.permission.SystemPermissionType;
import com.qherp.api.system.permission.SystemPermissionRepository;
import com.qherp.api.system.permission.SystemRolePermissionRepository;
import com.qherp.api.system.role.SystemRoleStatus;
import com.qherp.api.system.role.SystemRoleRepository;
import com.qherp.api.system.user.SystemUserStatus;
import com.qherp.api.system.user.SystemUserRepository;
import com.qherp.api.system.user.SystemUserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "qherp.test.context=account-permission")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountPermissionInitializerTests extends PostgresIntegrationTest {

	private static final Map<String, String> DOCUMENTED_PERMISSIONS = Map.ofEntries(
			Map.entry("system:user:view", "查看用户"),
			Map.entry("system:user:create", "创建用户"),
			Map.entry("system:user:update", "更新、启用、停用用户"),
			Map.entry("system:user:reset-password", "重置用户密码"),
			Map.entry("system:role:view", "查看角色"),
			Map.entry("system:role:create", "创建角色"),
			Map.entry("system:role:update", "更新、启用、停用角色"),
			Map.entry("system:role:assign-permission", "分配角色权限"),
			Map.entry("system:permission:view", "查看权限树"),
			Map.entry("system:audit:view", "查看账号权限审计"));

	@Autowired
	private AccountPermissionInitializer initializer;

	@Autowired
	private SystemUserRepository userRepository;

	@Autowired
	private SystemRoleRepository roleRepository;

	@Autowired
	private SystemPermissionRepository permissionRepository;

	@Autowired
	private SystemUserRoleRepository userRoleRepository;

	@Autowired
	private SystemRolePermissionRepository rolePermissionRepository;

	@Autowired
	private SystemAuditLogRepository auditLogRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void initializesAdminRoleAndPermissionsIdempotently() {
		assertThat(userRepository.countByUsername("admin")).isOne();
		assertThat(roleRepository.countByCode("SYSTEM_ADMIN")).isOne();
		assertDocumentedPermissionsInitializedAndAssigned();

		initializer.initialize();

		assertThat(userRepository.countByUsername("admin")).isOne();
		assertThat(roleRepository.countByCode("SYSTEM_ADMIN")).isOne();
		assertDocumentedPermissionsInitializedAndAssigned();

		var admin = userRepository.findByUsername("admin").orElseThrow();
		var systemAdmin = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();

		assertThat(admin.getStatus()).isEqualTo(SystemUserStatus.ENABLED);
		assertThat(admin.getPhone()).isNull();
		assertThat(admin.getEmail()).isNull();
		assertThat(passwordEncoder.matches("Qherp@2026!", admin.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches("Admin@123456", admin.getPasswordHash())).isFalse();
		assertThat(systemAdmin.getStatus()).isEqualTo(SystemRoleStatus.ENABLED);
		assertThat(systemAdmin.getDescription()).isNull();
		assertThat(userRoleRepository.existsByUserIdAndRoleId(admin.getId(), systemAdmin.getId())).isTrue();
	}

	@Test
	void accountPermissionSchemaContainsContractFieldsAndAuditLogTable() {
		assertThat(auditLogRepository.count()).isZero();

		var userColumns = this.jdbcTemplate.queryForList("""
				select column_name
				from information_schema.columns
				where table_schema = 'public'
				and table_name = 'sys_user'
				""", String.class);
		var roleColumns = this.jdbcTemplate.queryForList("""
				select column_name
				from information_schema.columns
				where table_schema = 'public'
				and table_name = 'sys_role'
				""", String.class);
		var auditColumns = this.jdbcTemplate.queryForList("""
				select column_name
				from information_schema.columns
				where table_schema = 'public'
				and table_name = 'sys_audit_log'
				""", String.class);

		assertThat(userColumns).contains("phone", "email");
		assertThat(roleColumns).contains("description");
		assertThat(auditColumns).contains("id", "operator_user_id", "operator_username", "action", "target_type",
				"target_id", "target_summary", "request_method", "request_path", "ip_address", "result",
				"error_code", "created_at");
	}

	@Test
	void initializesPermissionTreeAndApiMetadata() {
		var systemMenu = permissionRepository.findByCode("system").orElseThrow();
		var userMenu = permissionRepository.findByCode("system:user").orElseThrow();
		var userCreate = permissionRepository.findByCode("system:user:create").orElseThrow();
		var permissionView = permissionRepository.findByCode("system:permission:view").orElseThrow();
		var auditView = permissionRepository.findByCode("system:audit:view").orElseThrow();

		assertThat(systemMenu.getType()).isEqualTo(SystemPermissionType.MENU);
		assertThat(systemMenu.getParentId()).isNull();
		assertThat(systemMenu.getRoutePath()).isEqualTo("/system");
		assertThat(systemMenu.getApiMethod()).isNull();
		assertThat(systemMenu.getApiPath()).isNull();

		assertThat(userMenu.getType()).isEqualTo(SystemPermissionType.MENU);
		assertThat(userMenu.getParentId()).isEqualTo(systemMenu.getId());
		assertThat(userMenu.getRoutePath()).isEqualTo("/system/users");

		assertThat(userCreate.getType()).isEqualTo(SystemPermissionType.ACTION);
		assertThat(userCreate.getParentId()).isEqualTo(userMenu.getId());
		assertThat(userCreate.getRoutePath()).isEqualTo("/system/users");
		assertThat(userCreate.getApiMethod()).isEqualTo("POST");
		assertThat(userCreate.getApiPath()).isEqualTo("/api/admin/users");

		assertThat(permissionView.getType()).isEqualTo(SystemPermissionType.ACTION);
		assertThat(permissionView.getApiMethod()).isEqualTo("GET");
		assertThat(permissionView.getApiPath()).isEqualTo("/api/admin/permissions/tree");

		assertThat(auditView.getType()).isEqualTo(SystemPermissionType.ACTION);
		assertThat(auditView.getApiMethod()).isEqualTo("GET");
		assertThat(auditView.getApiPath()).isEqualTo("/api/admin/audit-logs");
	}

	private void assertDocumentedPermissionsInitializedAndAssigned() {
		var systemAdmin = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();

		DOCUMENTED_PERMISSIONS.forEach((code, name) -> {
			assertThat(permissionRepository.countByCode(code)).as(code).isOne();

			var permission = permissionRepository.findByCode(code).orElseThrow();
			assertThat(rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), permission.getId()))
				.as(code)
				.isTrue();
		});
	}

}
