package com.qherp.api.system.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sys_audit_log")
public class SystemAuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "operator_user_id")
	private Long operatorUserId;

	@Column(name = "operator_username", nullable = false, length = 64)
	private String operatorUsername;

	@Column(nullable = false, length = 64)
	private String action;

	@Column(name = "target_type", nullable = false, length = 64)
	private String targetType;

	@Column(name = "target_id", length = 64)
	private String targetId;

	@Column(name = "target_summary")
	private String targetSummary;

	@Column(name = "request_method", length = 16)
	private String requestMethod;

	@Column(name = "request_path")
	private String requestPath;

	@Column(name = "ip_address", length = 64)
	private String ipAddress;

	@Column(nullable = false, length = 32)
	private String result;

	@Column(name = "error_code", length = 64)
	private String errorCode;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected SystemAuditLog() {
	}

}
