package com.qherp.api.system.gl;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeneralLedgerAuditService {

	private final JdbcTemplate jdbcTemplate;

	public GeneralLedgerAuditService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failure(CurrentUser operator, String action, String resourceType, Long resourceId,
			ApiErrorCode errorCode) {
		record(operator, action, resourceType, resourceId, "FAILURE", errorCode, null, null);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void success(CurrentUser operator, String action, String resourceType, Long resourceId) {
		record(operator, action, resourceType, resourceId, "SUCCESS", null, null, null);
	}

	void record(CurrentUser operator, String action, String resourceType, Long resourceId, String result,
			ApiErrorCode errorCode, String sourceType, Long sourceId) {
		this.jdbcTemplate.update("""
				insert into gl_audit_event (
					operator_user_id, operator_username, action, result, resource_type, resource_id, error_code,
					source_type, source_id, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
				""", operator == null ? null : operator.id(), operator == null ? "system" : operator.username(),
				action, result, resourceType, resourceId == null ? null : Long.toString(resourceId),
				errorCode == null ? null : errorCode.code(), sourceType, sourceId);
	}

}
