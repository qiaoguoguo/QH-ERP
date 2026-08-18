package com.qherp.api.system.knowledge;

import java.time.OffsetDateTime;
import java.util.List;

public final class KnowledgeModels {

	private KnowledgeModels() {
	}

	public enum KnowledgeStatus {

		ENABLED,

		DISABLED

	}

	public enum KnowledgeType {

		PAGE("页面操作"),

		PROCESS("业务流程"),

		FIELD("字段解释"),

		STATUS("状态解释"),

		ERROR("错误处理"),

		PERMISSION("权限说明"),

		IMPORT_EXPORT("导入导出"),

		CONCEPT("业务概念");

		private final String displayName;

		KnowledgeType(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return this.displayName;
		}

	}

	public record KnowledgeCategoryRequest(String code, String name, Long parentId, Integer sortOrder,
			KnowledgeStatus status) {
	}

	public record KnowledgeArticleRequest(String slug, String title, String summary, Long categoryId,
			KnowledgeType knowledgeType, String content, String keywords, String routePaths, String pageNames,
			String permissionNote, List<Long> relatedArticleIds, Integer sortOrder, KnowledgeStatus status) {

		public KnowledgeArticleRequest {
			relatedArticleIds = relatedArticleIds == null ? List.of() : List.copyOf(relatedArticleIds);
		}
	}

	public record KnowledgeCategoryResponse(Long id, String code, String name, Long parentId, String parentCode,
			String parentName, Integer sortOrder, KnowledgeStatus status, String statusName, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, Long version) {
	}

	public record KnowledgeArticleSummary(Long id, String slug, String title, String summary, Long categoryId,
			String categoryCode, String categoryName, KnowledgeType knowledgeType, String knowledgeTypeName,
			String keywords, String routePaths, String pageNames, String permissionNote, Integer sortOrder,
			KnowledgeStatus status, String statusName, OffsetDateTime updatedAt, Long version) {
	}

	public record KnowledgeArticleDetail(Long id, String slug, String title, String summary, Long categoryId,
			String categoryCode, String categoryName, KnowledgeType knowledgeType, String knowledgeTypeName,
			String content, String keywords, String routePaths, String pageNames, String permissionNote,
			List<Long> relatedArticleIds, Integer sortOrder, KnowledgeStatus status, String statusName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {

		public KnowledgeArticleDetail {
			relatedArticleIds = relatedArticleIds == null ? List.of() : List.copyOf(relatedArticleIds);
		}
	}

	public record KnowledgeSeedCategory(String code, String name, String parentCode, Integer sortOrder,
			KnowledgeStatus status) {
	}

	public record KnowledgeSeedArticle(String slug, String title, String summary, String categoryCode,
			KnowledgeType knowledgeType, String content, String keywords, String routePaths, String pageNames,
			String permissionNote, List<String> relatedSlugs, Integer sortOrder, KnowledgeStatus status) {

		public KnowledgeSeedArticle {
			relatedSlugs = relatedSlugs == null ? List.of() : List.copyOf(relatedSlugs);
		}
	}

	static String statusName(KnowledgeStatus status) {
		return status == KnowledgeStatus.DISABLED ? "停用" : "启用";
	}

	static KnowledgeStatus statusOrEnabled(KnowledgeStatus status) {
		return status == null ? KnowledgeStatus.ENABLED : status;
	}

}
