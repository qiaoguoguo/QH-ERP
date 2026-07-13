package com.qherp.api.system.platform;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PlatformMessageService {

	private final JdbcTemplate jdbcTemplate;

	public PlatformMessageService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public PageResponse<MessageRecord> myMessages(Boolean unreadOnly, int page, int pageSize, CurrentUser currentUser) {
		List<Object> args = new ArrayList<>();
		args.add(currentUser.id());
		String where = "where recipient_user_id = ?";
		if (Boolean.TRUE.equals(unreadOnly)) {
			where += " and status = 'UNREAD'";
		}
		long total = this.jdbcTemplate.queryForObject("select count(*) from platform_message " + where, Long.class,
				args.toArray());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<MessageRecord> items = this.jdbcTemplate.query("""
				select id, title, content, message_type, status, related_object_type, related_object_id,
				       created_at, read_at, version
				from platform_message
				%s
				order by created_at desc, id desc
				limit ? offset ?
				""".formatted(where), this::mapMessage, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional
	public MessageRecord read(Long id, ReadMessageRequest request, CurrentUser currentUser) {
		MessageRecord current = message(id, currentUser);
		if (request == null || request.version() == null || !request.version().equals(current.version())) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
		this.jdbcTemplate.update("""
				update platform_message
				set status = 'READ', read_at = coalesce(read_at, now()), version = version + 1
				where id = ?
				and recipient_user_id = ?
				and version = ?
				""", id, currentUser.id(), current.version());
		return message(id, currentUser);
	}

	@Transactional
	public int readAll(CurrentUser currentUser) {
		return this.jdbcTemplate.update("""
				update platform_message
				set status = 'READ', read_at = coalesce(read_at, now()), version = version + 1
				where recipient_user_id = ?
				and status = 'UNREAD'
				""", currentUser.id());
	}

	private MessageRecord message(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select id, title, content, message_type, status, related_object_type, related_object_id,
				       created_at, read_at, version
				from platform_message
				where id = ?
				and recipient_user_id = ?
				""", this::mapMessage, id, currentUser.id()).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.AUTH_FORBIDDEN));
	}

	private MessageRecord mapMessage(ResultSet rs, int rowNum) throws SQLException {
		return new MessageRecord(rs.getLong("id"), rs.getString("title"), rs.getString("content"),
				rs.getString("message_type"), rs.getString("status"), rs.getString("related_object_type"),
				nullableLong(rs, "related_object_id"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("read_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record ReadMessageRequest(@NotNull Long version) {
	}

	public record MessageRecord(Long id, String title, String content, String messageType, String status,
			String relatedObjectType, Long relatedObjectId, OffsetDateTime createdAt, OffsetDateTime readAt,
			Long version) {
	}

}
