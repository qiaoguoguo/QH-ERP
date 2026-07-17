package com.qherp.api.system.production;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ProductionActionIdempotencyService {

	private final JdbcTemplate jdbcTemplate;

	public ProductionActionIdempotencyService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public String fingerprint(String action, String resourceType, Long resourceId, Long version, Object... parts) {
		StringBuilder builder = new StringBuilder(action).append('|')
			.append(resourceType)
			.append('|')
			.append(resourceId == null ? 0L : resourceId)
			.append('|')
			.append(version == null ? "" : version);
		for (Object part : parts) {
			builder.append('|').append(normalized(part));
		}
		return sha256(builder.toString());
	}

	public Optional<ResultRecord> existing(String action, String resourceType, Long resourceId, String idempotencyKey,
			String requestFingerprint, CurrentUser operator) {
		validateKey(idempotencyKey);
		return this.jdbcTemplate.query("""
				select result_resource_type, result_resource_id, result_version, request_fingerprint
				from mfg_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ResultRecord(rs.getString("result_resource_type"),
				rs.getLong("result_resource_id"), rs.getLong("result_version"), rs.getString("request_fingerprint")),
				operator.id(), action, resourceType, resourceId == null ? 0L : resourceId, idempotencyKey.trim())
			.stream()
			.findFirst()
			.map((record) -> {
				if (!record.requestFingerprint().equals(requestFingerprint)) {
					throw new BusinessException(ApiErrorCode.PRODUCTION_ACTION_IDEMPOTENCY_CONFLICT);
				}
				return record;
			});
	}

	public void record(String action, String resourceType, Long resourceId, Long resourceVersion,
			String idempotencyKey, String requestFingerprint, String resultResourceType, Long resultResourceId,
			Long resultVersion, CurrentUser operator) {
		validateKey(idempotencyKey);
		try {
			this.jdbcTemplate.update("""
					insert into mfg_action_idempotency (
						operator_user_id, operator_username, action, resource_type, resource_id, resource_version,
						idempotency_key, request_fingerprint, result_resource_type, result_resource_id,
						result_version
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), operator.username(), action, resourceType, resourceId == null ? 0L : resourceId,
					resourceVersion, idempotencyKey.trim(), requestFingerprint, resultResourceType, resultResourceId,
					resultVersion);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.PRODUCTION_ACTION_IDEMPOTENCY_CONFLICT);
		}
	}

	public void validateKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private static String normalized(Object part) {
		if (part == null) {
			return "";
		}
		return part.toString().trim();
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	public record ResultRecord(String resultResourceType, Long resultResourceId, Long resultVersion,
			String requestFingerprint) {
	}

}
