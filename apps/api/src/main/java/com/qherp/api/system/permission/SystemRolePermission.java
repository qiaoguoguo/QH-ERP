package com.qherp.api.system.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sys_role_permission")
public class SystemRolePermission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "role_id", nullable = false)
	private Long roleId;

	@Column(name = "permission_id", nullable = false)
	private Long permissionId;

	@Column(name = "created_by", nullable = false, length = 64)
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected SystemRolePermission() {
	}

	public SystemRolePermission(Long roleId, Long permissionId, String operator) {
		this.roleId = roleId;
		this.permissionId = permissionId;
		this.createdBy = operator;
		this.createdAt = OffsetDateTime.now();
	}

}
