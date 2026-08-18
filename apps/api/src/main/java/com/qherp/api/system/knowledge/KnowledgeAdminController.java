package com.qherp.api.system.knowledge;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleDetail;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleRequest;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleSummary;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeCategoryRequest;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeCategoryResponse;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeStatus;
import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system/knowledge")
public class KnowledgeAdminController {

	private final KnowledgeAdminService knowledgeAdminService;

	public KnowledgeAdminController(KnowledgeAdminService knowledgeAdminService) {
		this.knowledgeAdminService = knowledgeAdminService;
	}

	@GetMapping("/categories")
	public ApiResponse<List<KnowledgeCategoryResponse>> categories() {
		return ApiResponse.ok(this.knowledgeAdminService.categories());
	}

	@PostMapping("/categories")
	public ApiResponse<KnowledgeCategoryResponse> createCategory(@RequestBody KnowledgeCategoryRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.knowledgeAdminService.createCategory(request, currentUser, servletRequest));
	}

	@PutMapping("/categories/{id}")
	public ApiResponse<KnowledgeCategoryResponse> updateCategory(@PathVariable Long id,
			@RequestBody KnowledgeCategoryRequest request, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.knowledgeAdminService.updateCategory(id, request, currentUser, servletRequest));
	}

	@PostMapping("/categories/{id}/enable")
	public ApiResponse<KnowledgeCategoryResponse> enableCategory(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.knowledgeAdminService.enableCategory(id, currentUser, servletRequest));
	}

	@PostMapping("/categories/{id}/disable")
	public ApiResponse<KnowledgeCategoryResponse> disableCategory(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.knowledgeAdminService.disableCategory(id, currentUser, servletRequest));
	}

	@DeleteMapping("/categories/{id}")
	public ApiResponse<Void> deleteCategory(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		this.knowledgeAdminService.deleteCategory(id, currentUser, servletRequest);
		return ApiResponse.ok(null);
	}

	@GetMapping("/articles")
	public ApiResponse<PageResponse<KnowledgeArticleSummary>> articles(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) KnowledgeType knowledgeType,
			@RequestParam(required = false) KnowledgeStatus status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(
				this.knowledgeAdminService.articles(keyword, categoryId, knowledgeType, status, page, pageSize));
	}

	@GetMapping("/articles/{id}")
	public ApiResponse<KnowledgeArticleDetail> article(@PathVariable Long id) {
		return ApiResponse.ok(this.knowledgeAdminService.article(id));
	}

	@PostMapping("/articles")
	public ApiResponse<KnowledgeArticleDetail> createArticle(@RequestBody KnowledgeArticleRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.knowledgeAdminService.createArticle(request, currentUser, servletRequest));
	}

	@PutMapping("/articles/{id}")
	public ApiResponse<KnowledgeArticleDetail> updateArticle(@PathVariable Long id,
			@RequestBody KnowledgeArticleRequest request, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.knowledgeAdminService.updateArticle(id, request, currentUser, servletRequest));
	}

	@PostMapping("/articles/{id}/enable")
	public ApiResponse<KnowledgeArticleDetail> enableArticle(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.knowledgeAdminService.enableArticle(id, currentUser, servletRequest));
	}

	@PostMapping("/articles/{id}/disable")
	public ApiResponse<KnowledgeArticleDetail> disableArticle(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.knowledgeAdminService.disableArticle(id, currentUser, servletRequest));
	}

	@DeleteMapping("/articles/{id}")
	public ApiResponse<Void> deleteArticle(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		this.knowledgeAdminService.deleteArticle(id, currentUser, servletRequest);
		return ApiResponse.ok(null);
	}

}
