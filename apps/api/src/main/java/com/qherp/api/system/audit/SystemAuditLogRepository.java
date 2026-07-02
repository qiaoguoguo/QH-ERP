package com.qherp.api.system.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, Long> {

}
