package com.qherp.api.system.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sys_role")
public class SystemRole {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SystemRoleStatus status;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_by", nullable = false, length = 64)
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_by", nullable = false, length = 64)
	private String updatedBy;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@Version
	private Long version;

	protected SystemRole() {
	}

	public SystemRole(String code, String name, SystemRoleStatus status, int sortOrder, String operator) {
		this(code, name, null, status, sortOrder, operator);
	}

	public SystemRole(String code, String name, String description, SystemRoleStatus status, int sortOrder,
			String operator) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.status = status;
		this.sortOrder = sortOrder;
		this.createdBy = operator;
		this.createdAt = OffsetDateTime.now();
		this.updatedBy = operator;
		this.updatedAt = this.createdAt;
	}

	public Long getId() {
		return this.id;
	}

	public String getCode() {
		return this.code;
	}

	public String getDescription() {
		return this.description;
	}

	public SystemRoleStatus getStatus() {
		return this.status;
	}

}
