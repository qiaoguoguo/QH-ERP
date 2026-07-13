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
		VisibilityPredicate visibility = visibilityPredicate("m", currentUser);
		List<Object> args = new ArrayList<>();
		args.add(currentUser.id());
		String where = "where m.recipient_user_id = ?";
		if (Boolean.TRUE.equals(unreadOnly)) {
			where += " and m.status = 'UNREAD'";
		}
		where += " and " + visibility.sql();
		args.addAll(visibility.args());
		long total = this.jdbcTemplate.queryForObject("select count(*) from platform_message m " + where, Long.class,
				args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<MessageRecord> items = this.jdbcTemplate.query("""
				select m.id, m.title, m.content, m.message_type, m.status, m.related_object_type,
				       m.related_object_id, m.created_at, m.read_at, m.version
				from platform_message m
				%s
				order by m.created_at desc, m.id desc
				limit ? offset ?
				""".formatted(where), this::mapMessage, pageArgs.toArray());
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
		VisibilityPredicate visibility = visibilityPredicate("m", currentUser);
		List<Object> args = new ArrayList<>();
		args.add(currentUser.id());
		args.addAll(visibility.args());
		return this.jdbcTemplate.update("""
				update platform_message m
				set status = 'READ', read_at = coalesce(read_at, now()), version = version + 1
				where m.recipient_user_id = ?
				and m.status = 'UNREAD'
				and %s
				""".formatted(visibility.sql()), args.toArray());
	}

	private MessageRecord message(Long id, CurrentUser currentUser) {
		VisibilityPredicate visibility = visibilityPredicate("m", currentUser);
		List<Object> args = new ArrayList<>();
		args.add(id);
		args.add(currentUser.id());
		args.addAll(visibility.args());
		return this.jdbcTemplate.query("""
				select m.id, m.title, m.content, m.message_type, m.status, m.related_object_type,
				       m.related_object_id, m.created_at, m.read_at, m.version
				from platform_message m
				where m.id = ?
				and m.recipient_user_id = ?
				and %s
				""".formatted(visibility.sql()), this::mapMessage, args.toArray()).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.AUTH_FORBIDDEN));
	}

	private VisibilityPredicate visibilityPredicate(String alias, CurrentUser currentUser) {
		List<String> clauses = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasPermission(currentUser, "sales:contract:view")) {
			clauses.add(alias + ".related_object_type = 'SALES_PROJECT_CONTRACT'");
		}
		if (hasPermission(currentUser, "material:bom-eco:view")) {
			clauses.add(alias + ".related_object_type = 'BOM_ENGINEERING_CHANGE'");
		}
		List<String> documentTaskClauses = new ArrayList<>();
		if (hasPermission(currentUser, "platform:document-task:view")) {
			if (hasPermission(currentUser, "master:material:export")) {
				documentTaskClauses.add("dt.task_type = 'MATERIAL_EXPORT'");
			}
			if (hasPermission(currentUser, "master:material:import")) {
				documentTaskClauses.add("dt.task_type = 'MATERIAL_IMPORT'");
			}
			if (hasPermission(currentUser, "material:bom:export")) {
				documentTaskClauses.add("dt.task_type = 'BOM_DRAFT_EXPORT'");
			}
			if (hasPermission(currentUser, "material:bom:import")) {
				documentTaskClauses.add("dt.task_type = 'BOM_DRAFT_IMPORT'");
			}
			if (hasPermission(currentUser, "platform:print:generate")) {
				documentTaskClauses.add("dt.task_type = 'APPROVAL_PRINT'");
			}
		}
		if (!documentTaskClauses.isEmpty()) {
			clauses.add("""
					%s.related_object_type = 'DOCUMENT_TASK'
					and exists (
						select 1
						from platform_document_task dt
						where dt.id = %s.related_object_id
						and dt.created_by_user_id = ?
						and (%s)
					)
					""".formatted(alias, alias, String.join(" or ", documentTaskClauses)));
			args.add(currentUser.id());
		}
		if (clauses.isEmpty()) {
			return new VisibilityPredicate("1 = 0", List.of());
		}
		return new VisibilityPredicate("((" + String.join(") or (", clauses) + "))", args);
	}

	private static boolean hasPermission(CurrentUser currentUser, String permission) {
		return currentUser != null && currentUser.permissions().contains(permission);
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

	private record VisibilityPredicate(String sql, List<Object> args) {
	}

}
