package com.qherp.api.system.knowledge;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleDetail;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleSummary;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeCategoryResponse;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeStatus;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeQueryService {

	private final JdbcTemplate jdbcTemplate;

	public KnowledgeQueryService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public List<KnowledgeCategoryResponse> categories() {
		return this.jdbcTemplate.query("""
				select c.id, c.code, c.name, c.parent_id, p.code as parent_code, p.name as parent_name,
				       c.sort_order, c.status, c.created_at, c.updated_at, c.version
				from sys_knowledge_category c
				left join sys_knowledge_category p on p.id = c.parent_id
				where c.status = 'ENABLED'
				and (p.id is null or p.status = 'ENABLED')
				order by coalesce(p.sort_order, c.sort_order), c.parent_id nulls first, c.sort_order, c.id
				""", this::mapCategory);
	}

	@Transactional(readOnly = true)
	public PageResponse<KnowledgeArticleSummary> articles(String keyword, Long categoryId, KnowledgeType knowledgeType,
			int page, int pageSize) {
		QueryParts queryParts = articleQueryParts(keyword, categoryId, knowledgeType, true);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_knowledge_article a
				join sys_knowledge_category c on c.id = a.category_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.addAll(queryParts.orderArgs());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<KnowledgeArticleSummary> items = this.jdbcTemplate.query("""
				select a.id, a.slug, a.title, a.summary, a.category_id, c.code as category_code,
				       c.name as category_name, a.knowledge_type, a.keywords, a.route_paths,
				       a.page_names, a.permission_note, a.sort_order, a.status, a.updated_at, a.version
				from sys_knowledge_article a
				join sys_knowledge_category c on c.id = a.category_id
				%s
				order by %s
				limit ? offset ?
				""".formatted(queryParts.where(), queryParts.orderRank()), this::mapArticleSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public KnowledgeArticleDetail article(Long id) {
		return article(id, true);
	}

	@Transactional(readOnly = true)
	public PageResponse<KnowledgeArticleSummary> byRoute(String routePath, int page, int pageSize) {
		if (!hasText(routePath)) {
			throw new BusinessException(ApiErrorCode.KNOWLEDGE_REQUEST_INVALID);
		}
		String normalizedRoute = normalizeRoutePath(routePath);
		List<Object> args = new ArrayList<>();
		args.add(normalizedRoute);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_knowledge_article a
				join sys_knowledge_category c on c.id = a.category_id
				where a.status = 'ENABLED'
				and c.status = 'ENABLED'
				and exists (
					select 1
					from regexp_split_to_table(coalesce(a.route_paths, ''), E'\\r?\\n') as routes(route_path)
					where trim(route_path) = ?
				)
				""", Long.class, args.toArray());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<KnowledgeArticleSummary> items = this.jdbcTemplate.query("""
				select a.id, a.slug, a.title, a.summary, a.category_id, c.code as category_code,
				       c.name as category_name, a.knowledge_type, a.keywords, a.route_paths,
				       a.page_names, a.permission_note, a.sort_order, a.status, a.updated_at, a.version
				from sys_knowledge_article a
				join sys_knowledge_category c on c.id = a.category_id
				where a.status = 'ENABLED'
				and c.status = 'ENABLED'
				and exists (
					select 1
					from regexp_split_to_table(coalesce(a.route_paths, ''), E'\\r?\\n') as routes(route_path)
					where trim(route_path) = ?
				)
				order by a.sort_order, a.updated_at desc, a.id desc
				limit ? offset ?
				""", this::mapArticleSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public List<KnowledgeArticleSummary> related(Long id) {
		article(id, true);
		return this.jdbcTemplate.query("""
				select related.id, related.slug, related.title, related.summary, related.category_id,
				       c.code as category_code, c.name as category_name, related.knowledge_type,
				       related.keywords, related.route_paths, related.page_names, related.permission_note,
				       related.sort_order, related.status, related.updated_at, related.version
				from sys_knowledge_article_relation r
				join sys_knowledge_article related on related.id = r.related_article_id
				join sys_knowledge_category c on c.id = related.category_id
				where r.article_id = ?
				and related.status = 'ENABLED'
				and c.status = 'ENABLED'
				order by related.sort_order, related.updated_at desc, related.id desc
				""", this::mapArticleSummary, id);
	}

	KnowledgeArticleDetail article(Long id, boolean onlyEnabled) {
		String statusFilter = onlyEnabled ? "and a.status = 'ENABLED' and c.status = 'ENABLED'" : "";
		return this.jdbcTemplate.query("""
				select a.id, a.slug, a.title, a.summary, a.category_id, c.code as category_code,
				       c.name as category_name, a.knowledge_type, a.content, a.keywords, a.route_paths,
				       a.page_names, a.permission_note, a.sort_order, a.status, a.created_at, a.updated_at,
				       a.version
				from sys_knowledge_article a
				join sys_knowledge_category c on c.id = a.category_id
				where a.id = ?
				%s
				""".formatted(statusFilter), this::mapArticleDetail, id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.KNOWLEDGE_ARTICLE_NOT_FOUND));
	}

	KnowledgeArticleSummary articleSummary(Long id, boolean onlyEnabled) {
		String statusFilter = onlyEnabled ? "and a.status = 'ENABLED' and c.status = 'ENABLED'" : "";
		return this.jdbcTemplate.query("""
				select a.id, a.slug, a.title, a.summary, a.category_id, c.code as category_code,
				       c.name as category_name, a.knowledge_type, a.keywords, a.route_paths,
				       a.page_names, a.permission_note, a.sort_order, a.status, a.updated_at, a.version
				from sys_knowledge_article a
				join sys_knowledge_category c on c.id = a.category_id
				where a.id = ?
				%s
				""".formatted(statusFilter), this::mapArticleSummary, id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.KNOWLEDGE_ARTICLE_NOT_FOUND));
	}

	KnowledgeCategoryResponse category(Long id, boolean onlyEnabled) {
		String statusFilter = onlyEnabled ? "where c.id = ? and c.status = 'ENABLED'" : "where c.id = ?";
		return this.jdbcTemplate.query("""
				select c.id, c.code, c.name, c.parent_id, p.code as parent_code, p.name as parent_name,
				       c.sort_order, c.status, c.created_at, c.updated_at, c.version
				from sys_knowledge_category c
				left join sys_knowledge_category p on p.id = c.parent_id
				%s
				""".formatted(statusFilter), this::mapCategory, id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.KNOWLEDGE_CATEGORY_NOT_FOUND));
	}

	private QueryParts articleQueryParts(String keyword, Long categoryId, KnowledgeType knowledgeType,
			boolean onlyEnabled) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		List<Object> orderArgs = new ArrayList<>();
		if (onlyEnabled) {
			conditions.add("a.status = 'ENABLED'");
			conditions.add("c.status = 'ENABLED'");
		}
		if (categoryId != null) {
			conditions.add("a.category_id = ?");
			args.add(categoryId);
		}
		if (knowledgeType != null) {
			conditions.add("a.knowledge_type = ?");
			args.add(knowledgeType.name());
		}
		String orderRank = "a.sort_order, a.updated_at desc, a.id desc";
		if (hasText(keyword)) {
			String likeKeyword = "%" + keyword.trim() + "%";
			conditions.add("""
					(a.title ilike ? or a.keywords ilike ? or a.page_names ilike ?
					 or a.summary ilike ? or a.content ilike ?)
					""");
			args.add(likeKeyword);
			args.add(likeKeyword);
			args.add(likeKeyword);
			args.add(likeKeyword);
			args.add(likeKeyword);
			orderRank = """
					case
						when a.title ilike ? then 1
						when a.keywords ilike ? then 2
						when a.page_names ilike ? then 3
						when a.summary ilike ? then 4
						when a.content ilike ? then 5
						else 9
					end
					, a.sort_order, a.updated_at desc, a.id desc
					""";
			orderArgs.add(likeKeyword);
			orderArgs.add(likeKeyword);
			orderArgs.add(likeKeyword);
			orderArgs.add(likeKeyword);
			orderArgs.add(likeKeyword);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args, orderArgs, orderRank);
	}

	private KnowledgeCategoryResponse mapCategory(ResultSet rs, int rowNum) throws SQLException {
		KnowledgeStatus status = KnowledgeStatus.valueOf(rs.getString("status"));
		return new KnowledgeCategoryResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				nullableLong(rs, "parent_id"), rs.getString("parent_code"), rs.getString("parent_name"),
				rs.getInt("sort_order"), status, KnowledgeModels.statusName(status),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private KnowledgeArticleSummary mapArticleSummary(ResultSet rs, int rowNum) throws SQLException {
		KnowledgeType type = KnowledgeType.valueOf(rs.getString("knowledge_type"));
		KnowledgeStatus status = KnowledgeStatus.valueOf(rs.getString("status"));
		return new KnowledgeArticleSummary(rs.getLong("id"), rs.getString("slug"), rs.getString("title"),
				rs.getString("summary"), rs.getLong("category_id"), rs.getString("category_code"),
				rs.getString("category_name"), type, type.displayName(), rs.getString("keywords"),
				rs.getString("route_paths"), rs.getString("page_names"), rs.getString("permission_note"),
				rs.getInt("sort_order"), status, KnowledgeModels.statusName(status),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private KnowledgeArticleDetail mapArticleDetail(ResultSet rs, int rowNum) throws SQLException {
		KnowledgeType type = KnowledgeType.valueOf(rs.getString("knowledge_type"));
		KnowledgeStatus status = KnowledgeStatus.valueOf(rs.getString("status"));
		Long id = rs.getLong("id");
		return new KnowledgeArticleDetail(id, rs.getString("slug"), rs.getString("title"), rs.getString("summary"),
				rs.getLong("category_id"), rs.getString("category_code"), rs.getString("category_name"), type,
				type.displayName(), rs.getString("content"), rs.getString("keywords"), rs.getString("route_paths"),
				rs.getString("page_names"), rs.getString("permission_note"), relatedIds(id), rs.getInt("sort_order"),
				status, KnowledgeModels.statusName(status), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private List<Long> relatedIds(Long id) {
		return this.jdbcTemplate.query("""
				select related_article_id
				from sys_knowledge_article_relation
				where article_id = ?
				order by related_article_id
				""", (rs, rowNum) -> rs.getLong("related_article_id"), id);
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

	private static String normalizeRoutePath(String routePath) {
		String trimmed = routePath.trim();
		int queryIndex = trimmed.indexOf('?');
		return queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private record QueryParts(String where, List<Object> args, List<Object> orderArgs, String orderRank) {
	}

}
