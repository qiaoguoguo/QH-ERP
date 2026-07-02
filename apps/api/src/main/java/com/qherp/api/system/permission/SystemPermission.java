package com.qherp.api.system.permission;

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
@Table(name = "sys_permission")
public class SystemPermission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 128)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SystemPermissionType type;

	@Column(name = "parent_id")
	private Long parentId;

	@Column(name = "route_path")
	private String routePath;

	@Column(name = "api_method", length = 16)
	private String apiMethod;

	@Column(name = "api_path")
	private String apiPath;

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

	protected SystemPermission() {
	}

	public SystemPermission(String code, String name, SystemPermissionType type, int sortOrder, String operator) {
		this(code, name, type, null, null, null, null, sortOrder, operator);
	}

	public SystemPermission(String code, String name, SystemPermissionType type, Long parentId, String routePath,
			String apiMethod, String apiPath, int sortOrder, String operator) {
		this.code = code;
		this.name = name;
		this.type = type;
		this.parentId = parentId;
		this.routePath = routePath;
		this.apiMethod = apiMethod;
		this.apiPath = apiPath;
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

	public SystemPermissionType getType() {
		return this.type;
	}

	public Long getParentId() {
		return this.parentId;
	}

	public String getRoutePath() {
		return this.routePath;
	}

	public String getApiMethod() {
		return this.apiMethod;
	}

	public String getApiPath() {
		return this.apiPath;
	}

}
