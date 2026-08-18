package com.qherp.api.system.knowledge.logic;

import com.qherp.api.system.knowledge.logic.SystemLogicModels.EvidenceType;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.LogicEvidence;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.SnapshotManifest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SystemLogicEvidenceQueryService {

	private final JdbcTemplate jdbcTemplate;

	public SystemLogicEvidenceQueryService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public SnapshotManifest activeManifest() {
		return this.jdbcTemplate.query("""
				select id, source_digest, schema_version, generator_version, generated_at,
				       source_file_count, evidence_count, status, imported_at
				from sys_logic_snapshot where status = 'ACTIVE'
				""", (rs, rowNum) -> new SnapshotManifest(rs.getLong("id"), rs.getString("source_digest").trim(),
				rs.getInt("schema_version"), rs.getString("generator_version"),
				rs.getObject("generated_at", OffsetDateTime.class), rs.getInt("source_file_count"),
				rs.getInt("evidence_count"), rs.getString("status"),
				rs.getObject("imported_at", OffsetDateTime.class))).stream().findFirst().orElse(null);
	}

	@Transactional(readOnly = true)
	public List<LogicEvidence> search(String query, String routePath, int requestedLimit) {
		int limit = Math.max(1, Math.min(requestedLimit, 50));
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("s.status = 'ACTIVE'");
		if (hasText(routePath)) {
			conditions.add("(e.route_path = ? or e.route_path is null)");
			args.add(normalizeRoutePath(routePath));
		}
		String order = "e.confidence desc, e.source_path, e.source_line";
		if (hasText(query)) {
			String keyword = "%" + query.trim() + "%";
			conditions.add("(e.title ilike ? or e.summary ilike ? or e.keywords ilike ? or e.symbol ilike ?)");
			args.add(keyword);
			args.add(keyword);
			args.add(keyword);
			args.add(keyword);
			order = "case when e.title ilike ? then 1 when e.keywords ilike ? then 2 when e.summary ilike ? then 3 else 4 end, " + order;
			args.add(keyword);
			args.add(keyword);
			args.add(keyword);
		}
		args.add(limit);
		return this.jdbcTemplate.query("""
				select e.id, e.evidence_type, e.domain, e.title, e.summary, e.route_path,
				       e.http_method, e.permission_code, e.symbol, e.source_path, e.source_line,
				       e.confidence, e.evidence_digest
				from sys_logic_evidence e
				join sys_logic_snapshot s on s.id = e.snapshot_id
				where %s
				order by %s
				limit ?
				""".formatted(String.join(" and ", conditions), order), (rs, rowNum) -> new LogicEvidence(
				rs.getLong("id"), EvidenceType.valueOf(rs.getString("evidence_type")), rs.getString("domain"),
				rs.getString("title"), rs.getString("summary"), rs.getString("route_path"),
				rs.getString("http_method"), rs.getString("permission_code"), rs.getString("symbol"),
				rs.getString("source_path"), rs.getInt("source_line"), rs.getDouble("confidence"),
				rs.getString("evidence_digest").trim()), args.toArray());
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String normalizeRoutePath(String routePath) {
		String trimmed = routePath.trim();
		int queryIndex = trimmed.indexOf('?');
		return queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
	}

}
