package com.qherp.api.system.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemUserRoleRepository extends JpaRepository<SystemUserRole, Long> {

	boolean existsByUserIdAndRoleId(Long userId, Long roleId);

}
