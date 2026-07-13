package com.qherp.api.system.platform;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/messages")
public class PlatformMessageController {

	private final PlatformMessageService messageService;

	public PlatformMessageController(PlatformMessageService messageService) {
		this.messageService = messageService;
	}

	@GetMapping("/my")
	public ApiResponse<PageResponse<PlatformMessageService.MessageRecord>> myMessages(
			@RequestParam(required = false) Boolean unreadOnly, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.messageService.myMessages(unreadOnly, page, pageSize, currentUser));
	}

	@PutMapping("/{id}/read")
	public ApiResponse<PlatformMessageService.MessageRecord> read(@PathVariable Long id,
			@Valid @RequestBody PlatformMessageService.ReadMessageRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.messageService.read(id, request, currentUser));
	}

	@PutMapping("/read-all")
	public ApiResponse<Map<String, Integer>> readAll(@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(Map.of("updatedCount", this.messageService.readAll(currentUser)));
	}

}
