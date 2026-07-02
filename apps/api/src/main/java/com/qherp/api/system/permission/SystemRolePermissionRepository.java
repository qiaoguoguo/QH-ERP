package com.qherp.api.system.permission;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemRolePermissionRepository extends JpaRepository<SystemRolePermission, Long> {

	boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

}
