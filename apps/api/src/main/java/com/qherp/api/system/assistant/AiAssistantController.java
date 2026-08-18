package com.qherp.api.system.assistant;

import com.qherp.api.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-assistant")
public class AiAssistantController {

	private final CurrentUserService currentUserService;
	private final AiAssistantService assistantService;

	public AiAssistantController(CurrentUserService currentUserService, AiAssistantService assistantService) {
		this.currentUserService = currentUserService;
		this.assistantService = assistantService;
	}

	@GetMapping("/status")
	public AiAssistantModels.AssistantStatus status() {
		currentUserService.requireCurrentUser();
		return assistantService.status();
	}

	@PostMapping("/answers")
	public AiAssistantModels.AskResponse answer(@Valid @RequestBody AiAssistantModels.AskRequest request) {
		var currentUser = currentUserService.requireCurrentUser();
		return assistantService.answer(currentUser.id(), request);
	}
}
