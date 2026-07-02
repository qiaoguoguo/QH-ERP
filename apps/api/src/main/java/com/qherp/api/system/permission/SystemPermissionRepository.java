package com.qherp.api.system.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemPermissionRepository extends JpaRepository<SystemPermission, Long> {

	Optional<SystemPermission> findByCode(String code);

	long countByCode(String code);

}
