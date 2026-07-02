package com.qherp.api.system.init;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.permission.SystemPermissionRepository;
import com.qherp.api.system.permission.SystemRolePermissionRepository;
import com.qherp.api.system.role.SystemRoleRepository;
import com.qherp.api.system.user.SystemUserRepository;
import com.qherp.api.system.user.SystemUserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "qherp.test.context=account-permission")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountPermissionInitializerTests extends PostgresIntegrationTest {

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

	@Test
	void initializesAdminRoleAndPermissionsIdempotently() {
		assertThat(userRepository.countByUsername("admin")).isOne();
		assertThat(roleRepository.countByCode("SYSTEM_ADMIN")).isOne();
		assertThat(permissionRepository.countByCode("system:user:view")).isOne();

		initializer.initialize();

		assertThat(userRepository.countByUsername("admin")).isOne();
		assertThat(roleRepository.countByCode("SYSTEM_ADMIN")).isOne();
		assertThat(permissionRepository.countByCode("system:user:view")).isOne();

		var admin = userRepository.findByUsername("admin").orElseThrow();
		var systemAdmin = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();
		var userViewPermission = permissionRepository.findByCode("system:user:view").orElseThrow();

		assertThat(userRoleRepository.existsByUserIdAndRoleId(admin.getId(), systemAdmin.getId())).isTrue();
		assertThat(rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), userViewPermission.getId()))
			.isTrue();
	}

}
