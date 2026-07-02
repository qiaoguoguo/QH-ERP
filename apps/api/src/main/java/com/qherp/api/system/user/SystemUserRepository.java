package com.qherp.api.system.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemUserRepository extends JpaRepository<SystemUser, Long> {

	Optional<SystemUser> findByUsername(String username);

	long countByUsername(String username);

}
