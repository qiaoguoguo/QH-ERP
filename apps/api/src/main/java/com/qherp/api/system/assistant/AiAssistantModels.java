package com.qherp.api.system.assistant;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AiAssistantModels {

	private AiAssistantModels() {
	}

	public record ConversationTurn(
		@Pattern(regexp = "user|assistant", message = "对话角色不合法") String role,
		@NotBlank(message = "对话内容不能为空") @Size(max = 1000, message = "单条对话不能超过1000字") String content) {
	}

	public record AskRequest(
		@NotBlank(message = "请输入需要咨询的问题") @Size(max = 500, message = "问题不能超过500字") String question,
		@Size(max = 300, message = "页面路径不能超过300字") String routePath,
		@Size(max = 120, message = "页面名称不能超过120字") String pageName,
		@Size(max = 6, message = "最多携带最近6条对话") List<@Valid ConversationTurn> history) {
	}

	public record AnswerSource(
		String type,
		String title,
		String summary,
		Long articleId,
		String slug,
		String routePath) {
	}

	public record AskResponse(
		String answer,
		String mode,
		String model,
		List<AnswerSource> sources,
		OffsetDateTime generatedAt) {
	}

	public record AssistantStatus(
		boolean modelConfigured,
		String provider,
		String model,
		String currentMode,
		String privacyNotice) {
	}
}
