package com.qherp.api.system.financialclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FinancialCloseAuditService {

	private final JdbcTemplate jdbcTemplate;

	public FinancialCloseAuditService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void success(CurrentUser operator, String action, String resourceType, Long resourceId) {
		record(operator, action, "SUCCESS", resourceType, resourceId, null);
	}

	public void failure(CurrentUser operator, String action, String resourceType, Long resourceId,
			ApiErrorCode errorCode) {
		record(operator, action, "FAILURE", resourceType, resourceId, errorCode);
	}

	private void record(CurrentUser operator, String action, String result, String resourceType, Long resourceId,
			ApiErrorCode errorCode) {
		this.jdbcTemplate.update("""
				insert into fin_close_audit_event (
					operator_user_id, operator_username, action, result, resource_type, resource_id, error_code,
					created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, now())
				""", operator == null ? null : operator.id(), operator == null ? "system" : operator.username(),
				action, result, resourceType, resourceId, errorCode == null ? null : errorCode.code());
	}

}
