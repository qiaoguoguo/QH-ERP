package com.qherp.api.system.assistant;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MiniMaxCompletionClient {

	private static final Logger log = LoggerFactory.getLogger(MiniMaxCompletionClient.class);
	private static final Pattern THINKING_BLOCK = Pattern.compile("(?is)<think>.*?</think>");

	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final String baseUrl;
	private final String apiKey;
	private final String model;
	private final boolean enabled;
	private final Duration timeout;

	public MiniMaxCompletionClient(
		@Value("${qherp.ai.minimax.base-url:https://api.minimaxi.com/v1}") String baseUrl,
		@Value("${qherp.ai.minimax.api-key:}") String apiKey,
		@Value("${qherp.ai.minimax.model:MiniMax-M3}") String model,
		@Value("${qherp.ai.minimax.enabled:true}") boolean enabled,
		@Value("${qherp.ai.minimax.timeout-seconds:45}") long timeoutSeconds) {
		this.objectMapper = new ObjectMapper();
		this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model == null || model.isBlank() ? "MiniMax-M3" : model.trim();
		this.enabled = enabled;
		this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
		this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
	}

	public boolean configured() {
		return enabled && !apiKey.isBlank() && !baseUrl.isBlank();
	}

	public String model() {
		return model;
	}

	public Optional<String> complete(
		String systemPrompt,
		List<AiAssistantModels.ConversationTurn> history,
		String question) {
		if (!configured()) {
			return Optional.empty();
		}
		try {
			List<Map<String, String>> messages = new ArrayList<>();
			messages.add(Map.of("role", "system", "content", systemPrompt));
			for (AiAssistantModels.ConversationTurn turn : history) {
				messages.add(Map.of("role", turn.role(), "content", turn.content()));
			}
			messages.add(Map.of("role", "user", "content", question));

			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("model", model);
			payload.put("messages", messages);
			payload.put("stream", false);
			payload.put("temperature", 0.2);
			payload.put("max_tokens", 1400);

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/chat/completions"))
				.timeout(timeout)
				.header("Authorization", "Bearer " + apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
				.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				log.warn("MiniMax request failed with HTTP status {}", response.statusCode());
				return Optional.empty();
			}
			JsonNode content = objectMapper.readTree(response.body())
				.path("choices").path(0).path("message").path("content");
			if (!content.isTextual() || content.asText().isBlank()) {
				log.warn("MiniMax response did not contain answer text");
				return Optional.empty();
			}
			String answer = THINKING_BLOCK.matcher(content.asText()).replaceAll("").trim();
			return answer.isBlank() ? Optional.empty() : Optional.of(answer.substring(0, Math.min(answer.length(), 6000)));
		} catch (Exception exception) {
			log.warn("MiniMax request failed: {}", exception.getClass().getSimpleName());
			return Optional.empty();
		}
	}
}
