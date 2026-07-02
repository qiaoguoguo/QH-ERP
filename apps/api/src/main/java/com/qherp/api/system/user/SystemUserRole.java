package com.qherp.api.system.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sys_user_role")
public class SystemUserRole {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "role_id", nullable = false)
	private Long roleId;

	@Column(name = "created_by", nullable = false, length = 64)
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected SystemUserRole() {
	}

	public SystemUserRole(Long userId, Long roleId, String operator) {
		this.userId = userId;
		this.roleId = roleId;
		this.createdBy = operator;
		this.createdAt = OffsetDateTime.now();
	}

}
