package com.qherp.api.system.permission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PermissionAdminService {

	private final JdbcTemplate jdbcTemplate;

	public PermissionAdminService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public List<PermissionNode> tree() {
		List<PermissionRow> rows = this.jdbcTemplate.query("""
				select id, code, name, type, parent_id, route_path, sort_order
				from sys_permission
				order by sort_order, id
				""", this::mapPermission);
		Map<Long, List<PermissionRow>> childrenByParent = new LinkedHashMap<>();
		for (PermissionRow row : rows) {
			if (row.parentId() != null) {
				childrenByParent.computeIfAbsent(row.parentId(), (ignored) -> new ArrayList<>()).add(row);
			}
		}
		return rows.stream()
			.filter((row) -> row.parentId() == null)
			.sorted(Comparator.comparingInt(PermissionRow::sortOrder).thenComparingLong(PermissionRow::id))
			.map((row) -> toNode(row, childrenByParent))
			.toList();
	}

	private PermissionRow mapPermission(ResultSet rs, int rowNum) throws SQLException {
		return new PermissionRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				SystemPermissionType.valueOf(rs.getString("type")), nullableLong(rs, "parent_id"),
				rs.getString("route_path"), rs.getInt("sort_order"));
	}

	private PermissionNode toNode(PermissionRow row, Map<Long, List<PermissionRow>> childrenByParent) {
		List<PermissionNode> children = childrenByParent.getOrDefault(row.id(), List.of())
			.stream()
			.sorted(Comparator.comparingInt(PermissionRow::sortOrder).thenComparingLong(PermissionRow::id))
			.map((child) -> toNode(child, childrenByParent))
			.toList();
		return new PermissionNode(row.id(), row.code(), row.name(), row.type(), row.parentId(), row.routePath(),
				row.sortOrder(), children);
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record PermissionNode(Long id, String code, String name, SystemPermissionType type, Long parentId,
			String routePath, int sortOrder, List<PermissionNode> children) {

		public PermissionNode {
			children = children == null ? List.of() : List.copyOf(children);
		}

	}

	private record PermissionRow(Long id, String code, String name, SystemPermissionType type, Long parentId,
			String routePath, int sortOrder) {
	}

}
