package com.qherp.api.system.production;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductionOwnershipPolicy {

	private final JdbcTemplate jdbcTemplate;

	public ProductionOwnershipPolicy(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Ownership normalizeTarget(String ownershipType, Long projectId) {
		String normalized = normalizeOwnershipType(ownershipType);
		return new Ownership(normalized, normalizeProjectId(normalized, projectId));
	}

	public String normalizeOwnershipType(String ownershipType) {
		if (ownershipType == null || ownershipType.isBlank()) {
			return "PUBLIC";
		}
		String normalized = ownershipType.trim().toUpperCase();
		if (!"PUBLIC".equals(normalized) && !"PROJECT".equals(normalized)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_OWNERSHIP_SOURCE_INVALID);
		}
		return normalized;
	}

	public Long normalizeProjectId(String ownershipType, Long projectId) {
		if ("PUBLIC".equals(ownershipType)) {
			return null;
		}
		if (projectId == null) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_PROJECT_REQUIRED);
		}
		Boolean exists = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from sal_project
					where id = ?
					and status = 'ACTIVE'
				)
				""", Boolean.class, projectId);
		if (!Boolean.TRUE.equals(exists)) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_PROJECT_INVALID);
		}
		return projectId;
	}

	public void requireLineSourceAllowed(Ownership target, Ownership source, Long costLayerId) {
		if ("PROJECT".equals(source.ownershipType())) {
			if (!"PROJECT".equals(target.ownershipType()) || !source.projectId().equals(target.projectId())) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_PROJECT_MISMATCH);
			}
			if (costLayerId == null) {
				throw new BusinessException(ApiErrorCode.PRODUCTION_OWNERSHIP_SOURCE_INVALID);
			}
		}
	}

	public void requireCompletionOwnership(Ownership target) {
		normalizeProjectId(target.ownershipType(), target.projectId());
	}

	public record Ownership(String ownershipType, Long projectId) {
	}

}
