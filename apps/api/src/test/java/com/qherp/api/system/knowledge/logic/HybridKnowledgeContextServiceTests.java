package com.qherp.api.system.knowledge.logic;

import java.util.List;

import com.qherp.api.common.PageResponse;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleDetail;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleSummary;
import com.qherp.api.system.knowledge.KnowledgeQueryService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridKnowledgeContextServiceTests {

	@Test
	void naturalLanguageQuestionFindsGlobalArticleAndLoadsFullContent() {
		KnowledgeQueryService knowledgeQueryService = mock(KnowledgeQueryService.class);
		SystemLogicEvidenceQueryService logicQueryService = mock(SystemLogicEvidenceQueryService.class);
		KnowledgeArticleSummary material = article(1L, "物料分类怎么维护", "物料分类", "/materials/categories");
		KnowledgeArticleSummary sales = article(2L, "销售订单怎么创建、确认和审批", "销售订单 确认", "/sales/orders");
		KnowledgeArticleDetail salesDetail = mock(KnowledgeArticleDetail.class);
		when(salesDetail.content()).thenReturn("# 操作步骤\n进入销售订单详情，检查库存和信用后点击确认。");
		when(knowledgeQueryService.articles("销售订单怎么确认", null, null, 1, 8))
				.thenReturn(PageResponse.of(List.of(), 1, 8, 0));
		when(knowledgeQueryService.articles(null, null, null, 1, 100))
				.thenReturn(PageResponse.of(List.of(material, sales), 1, 100, 2));
		when(knowledgeQueryService.article(2L)).thenReturn(salesDetail);
		when(logicQueryService.search(anyString(), anyString(), anyInt())).thenReturn(List.of());

		var service = new HybridKnowledgeContextService(knowledgeQueryService, logicQueryService);
		var context = service.retrieve("销售订单怎么确认", "", 8);

		assertThat(context.manualEvidence()).hasSize(1);
		assertThat(context.manualEvidence().get(0).title()).contains("销售订单");
		assertThat(context.manualEvidence().get(0).content()).contains("检查库存和信用");
	}

	private KnowledgeArticleSummary article(Long id, String title, String keywords, String routePaths) {
		KnowledgeArticleSummary article = mock(KnowledgeArticleSummary.class);
		when(article.id()).thenReturn(id);
		when(article.slug()).thenReturn("article-" + id);
		when(article.title()).thenReturn(title);
		when(article.summary()).thenReturn(title + "操作说明");
		when(article.keywords()).thenReturn(keywords);
		when(article.pageNames()).thenReturn(title);
		when(article.categoryName()).thenReturn("业务知识");
		when(article.routePaths()).thenReturn(routePaths);
		return article;
	}
}
