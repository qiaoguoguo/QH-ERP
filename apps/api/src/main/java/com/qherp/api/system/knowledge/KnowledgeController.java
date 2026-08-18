package com.qherp.api.system.knowledge;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleDetail;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleSummary;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeCategoryResponse;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/help")
public class KnowledgeController {

	private final KnowledgeQueryService knowledgeQueryService;

	public KnowledgeController(KnowledgeQueryService knowledgeQueryService) {
		this.knowledgeQueryService = knowledgeQueryService;
	}

	@GetMapping("/categories")
	public ApiResponse<List<KnowledgeCategoryResponse>> categories() {
		return ApiResponse.ok(this.knowledgeQueryService.categories());
	}

	@GetMapping("/articles")
	public ApiResponse<PageResponse<KnowledgeArticleSummary>> articles(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) KnowledgeType knowledgeType, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.knowledgeQueryService.articles(keyword, categoryId, knowledgeType, page, pageSize));
	}

	@GetMapping("/articles/{id}")
	public ApiResponse<KnowledgeArticleDetail> article(@PathVariable Long id) {
		return ApiResponse.ok(this.knowledgeQueryService.article(id));
	}

	@GetMapping("/articles/by-route")
	public ApiResponse<PageResponse<KnowledgeArticleSummary>> byRoute(@RequestParam String routePath,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.knowledgeQueryService.byRoute(routePath, page, pageSize));
	}

	@GetMapping("/articles/{id}/related")
	public ApiResponse<List<KnowledgeArticleSummary>> related(@PathVariable Long id) {
		return ApiResponse.ok(this.knowledgeQueryService.related(id));
	}

}
