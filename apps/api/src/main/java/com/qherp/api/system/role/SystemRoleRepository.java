package com.qherp.api.system.role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemRoleRepository extends JpaRepository<SystemRole, Long> {

	Optional<SystemRole> findByCode(String code);

	long countByCode(String code);

}
