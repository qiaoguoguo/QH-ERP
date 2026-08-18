package com.qherp.api.system.knowledge;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleDetail;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleRequest;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleSummary;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeCategoryRequest;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeCategoryResponse;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeStatus;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeAdminService {

	private static final String CATEGORY_TARGET = "KNOWLEDGE_CATEGORY";

	private static final String ARTICLE_TARGET = "KNOWLEDGE_ARTICLE";

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final KnowledgeQueryService queryService;

	public KnowledgeAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			KnowledgeQueryService queryService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.queryService = queryService;
	}

	@Transactional(readOnly = true)
	public List<KnowledgeCategoryResponse> categories() {
		return this.jdbcTemplate.query("""
				select c.id, c.code, c.name, c.parent_id, p.code as parent_code, p.name as parent_name,
				       c.sort_order, c.status, c.created_at, c.updated_at, c.version
				from sys_knowledge_category c
				left join sys_knowledge_category p on p.id = c.parent_id
				order by coalesce(p.sort_order, c.sort_order), c.parent_id nulls first, c.sort_order, c.id
				""", this::mapCategory);
	}

	@Transactional
	public KnowledgeCategoryResponse createCategory(KnowledgeCategoryRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedCategory category = validateCategoryRequest(request, null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into sys_knowledge_category (
						code, name, parent_id, sort_order, status, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, category.code(), category.name(), category.parentId(), category.sortOrder(),
					category.status().name(), operator.username(), now, operator.username(), now);
			this.auditService.record(operator, "KNOWLEDGE_CATEGORY_CREATE", CATEGORY_TARGET, id, category.code(),
					servletRequest);
			return this.queryService.category(id, false);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.KNOWLEDGE_CATEGORY_CODE_EXISTS);
		}
	}

	@Transactional
	public KnowledgeCategoryResponse updateCategory(Long id, KnowledgeCategoryRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		this.queryService.category(id, false);
		ValidatedCategory category = validateCategoryRequest(request, id);
		try {
			int updated = this.jdbcTemplate.update("""
					update sys_knowledge_category
					set code = ?, name = ?, parent_id = ?, sort_order = ?, status = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", category.code(), category.name(), category.parentId(), category.sortOrder(),
					category.status().name(), operator.username(), OffsetDateTime.now(), id);
			if (updated == 0) {
				throw new BusinessException(ApiErrorCode.KNOWLEDGE_CATEGORY_NOT_FOUND);
			}
			this.auditService.record(operator, "KNOWLEDGE_CATEGORY_UPDATE", CATEGORY_TARGET, id, category.code(),
					servletRequest);
			return this.queryService.category(id, false);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.KNOWLEDGE_CATEGORY_CODE_EXISTS);
		}
	}

	@Transactional
	public KnowledgeCategoryResponse enableCategory(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		return changeCategoryStatus(id, KnowledgeStatus.ENABLED, operator, servletRequest);
	}

	@Transactional
	public KnowledgeCategoryResponse disableCategory(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		return changeCategoryStatus(id, KnowledgeStatus.DISABLED, operator, servletRequest);
	}

	@Transactional
	public void deleteCategory(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		KnowledgeCategoryResponse category = this.queryService.category(id, false);
		Long childCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_knowledge_category
				where parent_id = ?
				""", Long.class, id);
		Long articleCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_knowledge_article
				where category_id = ?
				""", Long.class, id);
		if ((childCount != null && childCount > 0) || (articleCount != null && articleCount > 0)) {
			throw new BusinessException(ApiErrorCode.KNOWLEDGE_CATEGORY_IN_USE);
		}
		this.jdbcTemplate.update("delete from sys_knowledge_category where id = ?", id);
		this.auditService.record(operator, "KNOWLEDGE_CATEGORY_DELETE", CATEGORY_TARGET, id, category.code(),
				servletRequest);
	}

	@Transactional(readOnly = true)
	public PageResponse<KnowledgeArticleSummary> articles(String keyword, Long categoryId, KnowledgeType knowledgeType,
			KnowledgeStatus status, int page, int pageSize) {
		QueryParts queryParts = adminArticleQueryParts(keyword, categoryId, knowledgeType, status);
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
		return this.queryService.article(id, false);
	}

	@Transactional
	public KnowledgeArticleDetail createArticle(KnowledgeArticleRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedArticle article = validateArticleRequest(request, null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into sys_knowledge_article (
						slug, title, summary, category_id, knowledge_type, content, keywords, route_paths,
						page_names, permission_note, sort_order, status, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, article.slug(), article.title(), article.summary(), article.categoryId(),
					article.knowledgeType().name(), article.content(), blankToNull(article.keywords()),
					blankToNull(article.routePaths()), blankToNull(article.pageNames()),
					blankToNull(article.permissionNote()), article.sortOrder(), article.status().name(),
					operator.username(), now, operator.username(), now);
			replaceRelations(id, article.relatedArticleIds());
			this.auditService.record(operator, "KNOWLEDGE_ARTICLE_CREATE", ARTICLE_TARGET, id, article.slug(),
					servletRequest);
			return this.queryService.article(id, false);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.KNOWLEDGE_ARTICLE_SLUG_EXISTS);
		}
	}

	@Transactional
	public KnowledgeArticleDetail updateArticle(Long id, KnowledgeArticleRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		this.queryService.article(id, false);
		ValidatedArticle article = validateArticleRequest(request, id);
		try {
			int updated = this.jdbcTemplate.update("""
					update sys_knowledge_article
					set slug = ?, title = ?, summary = ?, category_id = ?, knowledge_type = ?, content = ?,
					    keywords = ?, route_paths = ?, page_names = ?, permission_note = ?, sort_order = ?,
					    status = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", article.slug(), article.title(), article.summary(), article.categoryId(),
					article.knowledgeType().name(), article.content(), blankToNull(article.keywords()),
					blankToNull(article.routePaths()), blankToNull(article.pageNames()),
					blankToNull(article.permissionNote()), article.sortOrder(), article.status().name(),
					operator.username(), OffsetDateTime.now(), id);
			if (updated == 0) {
				throw new BusinessException(ApiErrorCode.KNOWLEDGE_ARTICLE_NOT_FOUND);
			}
			replaceRelations(id, article.relatedArticleIds());
			this.auditService.record(operator, "KNOWLEDGE_ARTICLE_UPDATE", ARTICLE_TARGET, id, article.slug(),
					servletRequest);
			return this.queryService.article(id, false);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.KNOWLEDGE_ARTICLE_SLUG_EXISTS);
		}
	}

	@Transactional
	public KnowledgeArticleDetail enableArticle(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		return changeArticleStatus(id, KnowledgeStatus.ENABLED, operator, servletRequest);
	}

	@Transactional
	public KnowledgeArticleDetail disableArticle(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		return changeArticleStatus(id, KnowledgeStatus.DISABLED, operator, servletRequest);
	}

	@Transactional
	public void deleteArticle(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		KnowledgeArticleDetail article = this.queryService.article(id, false);
		this.jdbcTemplate.update("delete from sys_knowledge_article where id = ?", id);
		this.auditService.record(operator, "KNOWLEDGE_ARTICLE_DELETE", ARTICLE_TARGET, id, article.slug(),
				servletRequest);
	}

	private KnowledgeCategoryResponse changeCategoryStatus(Long id, KnowledgeStatus status, CurrentUser operator,
			HttpServletRequest servletRequest) {
		KnowledgeCategoryResponse category = this.queryService.category(id, false);
		this.jdbcTemplate.update("""
				update sys_knowledge_category
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", status.name(), operator.username(), OffsetDateTime.now(), id);
		this.auditService.record(operator,
				status == KnowledgeStatus.ENABLED ? "KNOWLEDGE_CATEGORY_ENABLE" : "KNOWLEDGE_CATEGORY_DISABLE",
				CATEGORY_TARGET, id, category.code(), servletRequest);
		return this.queryService.category(id, false);
	}

	private KnowledgeArticleDetail changeArticleStatus(Long id, KnowledgeStatus status, CurrentUser operator,
			HttpServletRequest servletRequest) {
		KnowledgeArticleDetail article = this.queryService.article(id, false);
		this.jdbcTemplate.update("""
				update sys_knowledge_article
				set status = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", status.name(), operator.username(), OffsetDateTime.now(), id);
		this.auditService.record(operator,
				status == KnowledgeStatus.ENABLED ? "KNOWLEDGE_ARTICLE_ENABLE" : "KNOWLEDGE_ARTICLE_DISABLE",
				ARTICLE_TARGET, id, article.slug(), servletRequest);
		return this.queryService.article(id, false);
	}

	private ValidatedCategory validateCategoryRequest(KnowledgeCategoryRequest request, Long currentId) {
		if (request == null || !hasText(request.code()) || !hasText(request.name())) {
			throw new BusinessException(ApiErrorCode.KNOWLEDGE_REQUEST_INVALID);
		}
		String code = request.code().trim();
		String name = request.name().trim();
		Long parentId = request.parentId();
		if (currentId != null && currentId.equals(parentId)) {
			throw new BusinessException(ApiErrorCode.KNOWLEDGE_REQUEST_INVALID);
		}
		if (parentId != null) {
			CategoryRow parent = categoryRow(parentId);
			if (parent.parentId() != null) {
				throw new BusinessException(ApiErrorCode.KNOWLEDGE_REQUEST_INVALID);
			}
			if (currentId != null && hasChildCategory(currentId)) {
				throw new BusinessException(ApiErrorCode.KNOWLEDGE_REQUEST_INVALID);
			}
		}
		return new ValidatedCategory(code, name, parentId, request.sortOrder() == null ? 0 : request.sortOrder(),
				KnowledgeModels.statusOrEnabled(request.status()));
	}

	private ValidatedArticle validateArticleRequest(KnowledgeArticleRequest request, Long currentId) {
		if (request == null || !hasText(request.slug()) || !hasText(request.title()) || !hasText(request.summary())
				|| request.categoryId() == null || request.knowledgeType() == null || !hasText(request.content())) {
			throw new BusinessException(ApiErrorCode.KNOWLEDGE_REQUEST_INVALID);
		}
		this.queryService.category(request.categoryId(), false);
		List<Long> relatedIds = request.relatedArticleIds() == null ? List.of() : request.relatedArticleIds();
		validateRelatedArticles(currentId, relatedIds);
		return new ValidatedArticle(request.slug().trim(), request.title().trim(), request.summary().trim(),
				request.categoryId(), request.knowledgeType(), request.content().trim(), trimToNull(request.keywords()),
				trimToNull(request.routePaths()), trimToNull(request.pageNames()), trimToNull(request.permissionNote()),
				relatedIds, request.sortOrder() == null ? 0 : request.sortOrder(),
				KnowledgeModels.statusOrEnabled(request.status()));
	}

	private void validateRelatedArticles(Long currentId, List<Long> relatedIds) {
		Set<Long> seen = new HashSet<>();
		for (Long relatedId : relatedIds) {
			if (relatedId == null || !seen.add(relatedId) || (currentId != null && currentId.equals(relatedId))) {
				throw new BusinessException(ApiErrorCode.KNOWLEDGE_RELATION_INVALID);
			}
			this.queryService.articleSummary(relatedId, false);
		}
	}

	private void replaceRelations(Long id, List<Long> relatedIds) {
		this.jdbcTemplate.update("delete from sys_knowledge_article_relation where article_id = ?", id);
		for (Long relatedId : relatedIds) {
			this.jdbcTemplate.update("""
					insert into sys_knowledge_article_relation (article_id, related_article_id, created_at)
					values (?, ?, ?)
					""", id, relatedId, OffsetDateTime.now());
		}
	}

	private CategoryRow categoryRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, parent_id
				from sys_knowledge_category
				where id = ?
				""", (rs, rowNum) -> new CategoryRow(rs.getLong("id"), nullableLong(rs, "parent_id")), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.KNOWLEDGE_CATEGORY_NOT_FOUND));
	}

	private boolean hasChildCategory(Long id) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_knowledge_category
				where parent_id = ?
				""", Long.class, id);
		return count != null && count > 0;
	}

	private QueryParts adminArticleQueryParts(String keyword, Long categoryId, KnowledgeType knowledgeType,
			KnowledgeStatus status) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		List<Object> orderArgs = new ArrayList<>();
		if (categoryId != null) {
			conditions.add("a.category_id = ?");
			args.add(categoryId);
		}
		if (knowledgeType != null) {
			conditions.add("a.knowledge_type = ?");
			args.add(knowledgeType.name());
		}
		if (status != null) {
			conditions.add("a.status = ?");
			args.add(status.name());
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

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static String trimToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private record ValidatedCategory(String code, String name, Long parentId, Integer sortOrder,
			KnowledgeStatus status) {
	}

	private record ValidatedArticle(String slug, String title, String summary, Long categoryId,
			KnowledgeType knowledgeType, String content, String keywords, String routePaths, String pageNames,
			String permissionNote, List<Long> relatedArticleIds, Integer sortOrder, KnowledgeStatus status) {
	}

	private record CategoryRow(Long id, Long parentId) {
	}

	private record QueryParts(String where, List<Object> args, List<Object> orderArgs, String orderRank) {
	}

}
