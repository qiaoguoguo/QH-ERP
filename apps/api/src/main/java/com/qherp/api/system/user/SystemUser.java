package com.qherp.api.system.user;

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
@Table(name = "sys_user")
public class SystemUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String username;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(length = 32)
	private String phone;

	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SystemUserStatus status;

	@Column(name = "last_login_at")
	private OffsetDateTime lastLoginAt;

	@Column(name = "password_changed_at")
	private OffsetDateTime passwordChangedAt;

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

	protected SystemUser() {
	}

	public SystemUser(String username, String passwordHash, String displayName, SystemUserStatus status,
			String operator) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.status = status;
		this.passwordChangedAt = OffsetDateTime.now();
		this.createdBy = operator;
		this.createdAt = OffsetDateTime.now();
		this.updatedBy = operator;
		this.updatedAt = this.createdAt;
	}

	public Long getId() {
		return this.id;
	}

	public String getUsername() {
		return this.username;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public String getPhone() {
		return this.phone;
	}

	public String getEmail() {
		return this.email;
	}

	public SystemUserStatus getStatus() {
		return this.status;
	}

}
