package com.qherp.api.system.audit;

import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService {

	private final JdbcTemplate jdbcTemplate;

	public AuditService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void record(CurrentUser operator, String action, String targetType, Long targetId, String targetSummary,
			HttpServletRequest request) {
		this.jdbcTemplate.update("""
				insert into sys_audit_log (
					operator_user_id, operator_username, action, target_type, target_id, target_summary,
					request_method, request_path, ip_address, result, error_code, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", operator.id(), operator.username(), action, targetType, targetId == null ? null : targetId.toString(),
				targetSummary, request == null ? null : request.getMethod(), request == null ? null : request.getRequestURI(),
				clientIp(request), "SUCCESS", null, OffsetDateTime.now());
	}

	@Transactional
	public void recordDetail(CurrentUser operator, String action, String targetType, Long targetId,
			String targetSummary, String detailJson, HttpServletRequest request) {
		this.jdbcTemplate.update("""
				insert into sys_audit_log (
					operator_user_id, operator_username, action, target_type, target_id, target_summary,
					request_method, request_path, ip_address, result, error_code, detail_json, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
				""", operator.id(), operator.username(), action, targetType,
				targetId == null ? null : targetId.toString(), targetSummary,
				request == null ? null : request.getMethod(), request == null ? null : request.getRequestURI(),
				clientIp(request), "SUCCESS", null, detailJson, OffsetDateTime.now());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(CurrentUser operator, String action, String targetType, Long targetId,
			String targetSummary, String errorCode, HttpServletRequest request) {
		this.jdbcTemplate.update("""
				insert into sys_audit_log (
					operator_user_id, operator_username, action, target_type, target_id, target_summary,
					request_method, request_path, ip_address, result, error_code, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", operator.id(), operator.username(), action, targetType,
				targetId == null ? null : targetId.toString(), targetSummary,
				request == null ? null : request.getMethod(), requestPath(request), clientIp(request), "FAILURE",
				errorCode, OffsetDateTime.now());
	}

	@Transactional(readOnly = true)
	public PageResponse<AuditLogResponse> list(String operatorKeyword, String targetType, String action,
			OffsetDateTime startAt, OffsetDateTime endAt, int page, int pageSize) {
		QueryParts queryParts = queryParts(operatorKeyword, targetType, action, startAt, endAt);
		long total = this.jdbcTemplate.queryForObject("select count(*) from sys_audit_log " + queryParts.where(),
				Long.class, queryParts.args().toArray());

		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<AuditLogResponse> items = this.jdbcTemplate.query("""
				select id, operator_user_id, operator_username, action, target_type, target_id, target_summary,
				       request_method, request_path, ip_address, result, error_code, created_at
				from sys_audit_log
				%s
				order by created_at desc, id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapAuditLog, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	private QueryParts queryParts(String operatorKeyword, String targetType, String action, OffsetDateTime startAt,
			OffsetDateTime endAt) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(operatorKeyword)) {
			conditions.add("operator_username ilike ?");
			args.add("%" + operatorKeyword + "%");
		}
		if (hasText(targetType)) {
			conditions.add("target_type = ?");
			args.add(targetType);
		}
		if (hasText(action)) {
			conditions.add("action = ?");
			args.add(action);
		}
		if (startAt != null) {
			conditions.add("created_at >= ?");
			args.add(startAt);
		}
		if (endAt != null) {
			conditions.add("created_at <= ?");
			args.add(endAt);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args);
	}

	private AuditLogResponse mapAuditLog(ResultSet rs, int rowNum) throws SQLException {
		return new AuditLogResponse(rs.getLong("id"), nullableLong(rs, "operator_user_id"),
				rs.getString("operator_username"), rs.getString("action"), rs.getString("target_type"),
				rs.getString("target_id"), rs.getString("target_summary"), rs.getString("request_method"),
				rs.getString("request_path"), rs.getString("ip_address"), rs.getString("result"),
				rs.getString("error_code"), rs.getObject("created_at", OffsetDateTime.class));
	}

	private String clientIp(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (hasText(forwardedFor)) {
			return forwardedFor.split(",", 2)[0].trim();
		}
		return request.getRemoteAddr();
	}

	private String requestPath(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (hasText(contextPath) && uri.startsWith(contextPath)) {
			return uri.substring(contextPath.length());
		}
		return uri;
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record AuditLogResponse(Long id, Long operatorUserId, String operatorUsername, String action,
			String targetType, String targetId, String targetSummary, String requestMethod, String requestPath,
			String ipAddress, String result, String errorCode, OffsetDateTime createdAt) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
