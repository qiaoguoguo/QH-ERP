package com.qherp.api.system.assistant;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.qherp.api.system.knowledge.logic.AiKnowledgeContextProvider;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.EvidenceType;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.LogicEvidence;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.ManualEvidence;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.RetrievalContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiAssistantService {

	private static final String SYSTEM_INSTRUCTION = """
		你是 QH ERP 系统操作咨询助手。你只能解释系统功能、业务前置条件、页面操作步骤和页面导航。
		用户可以在任意页面咨询整个系统的任何模块。当前页面只是一项辅助上下文，绝不能作为知识检索或回答范围的限制，也不能要求用户先切换页面才回答。
		严禁声称已经替用户点击、提交、审批、修改或删除任何数据；严禁要求或推测密码、密钥和个人敏感信息。
		只能依据下方系统知识作答。证据不足时必须明确说明不知道，不得编造页面入口、字段、状态或业务规则。
		回答使用简洁中文，优先给出：结论、操作路径、前置条件、注意事项。不要输出代码路径、类名或内部实现细节。
		""";
	private static final Pattern TECHNICAL_ROUTE = Pattern.compile("(?<!\\w)/(?:[A-Za-z0-9_{}:-]+/?)+");
	private static final Pattern TECHNICAL_ROUTE_NAME = Pattern.compile("，?路由名称为\\s*[A-Za-z0-9_-]+[。.]?");

	private final AiKnowledgeContextProvider contextProvider;
	private final MiniMaxCompletionClient completionClient;
	private final AiAssistantPrivacyFilter privacyFilter;
	private final AiAssistantRateLimiter rateLimiter;

	public AiAssistantService(
		AiKnowledgeContextProvider contextProvider,
		MiniMaxCompletionClient completionClient,
		AiAssistantPrivacyFilter privacyFilter,
		AiAssistantRateLimiter rateLimiter) {
		this.contextProvider = contextProvider;
		this.completionClient = completionClient;
		this.privacyFilter = privacyFilter;
		this.rateLimiter = rateLimiter;
	}

	public AiAssistantModels.AssistantStatus status() {
		boolean configured = completionClient.configured();
		return new AiAssistantModels.AssistantStatus(
			configured,
			"MiniMax",
			completionClient.model(),
			configured ? "AI知识问答" : "知识库降级模式",
			"请勿输入密码、密钥、身份证号、银行卡号或其他敏感信息。系统会在发送模型前再次脱敏。"
		);
	}

	public AiAssistantModels.AskResponse answer(Long userId, AiAssistantModels.AskRequest request) {
		if (!rateLimiter.tryAcquire(userId)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "提问过于频繁，请稍后再试");
		}
		String question = request.question().trim();
		String routePath = request.routePath() == null ? "" : request.routePath().trim();
		RetrievalContext globalContext = contextProvider.retrieve(question, "", 8);
		RetrievalContext pageContext = routePath.isBlank()
			? new RetrievalContext(globalContext.logicSnapshot(), List.of(), List.of())
			: contextProvider.retrieve("", routePath, 3);
		RetrievalContext context = mergeContexts(globalContext, pageContext);
		List<AiAssistantModels.AnswerSource> sources = buildSources(context);
		List<AiAssistantModels.ConversationTurn> safeHistory = sanitizeHistory(request.history());
		String safeQuestion = privacyFilter.sanitize(question);
		String prompt = SYSTEM_INSTRUCTION + "\n\n当前页面上下文：" + currentPageContext(request.pageName(), pageContext)
			+ "。该页面信息仅用于理解用户所处位置，不得限制对其他模块问题的回答。"
			+ "\n\n系统知识：\n" + buildKnowledgeContext(context);

		var generated = completionClient.complete(prompt, safeHistory, safeQuestion);
		if (generated.isPresent()) {
			return new AiAssistantModels.AskResponse(
				generated.get(), "MINIMAX", completionClient.model(), sources, OffsetDateTime.now());
		}
		return new AiAssistantModels.AskResponse(
			fallbackAnswer(context, completionClient.configured()),
			"KNOWLEDGE_FALLBACK",
			completionClient.model(),
			sources,
			OffsetDateTime.now());
	}

	private List<AiAssistantModels.ConversationTurn> sanitizeHistory(
		List<AiAssistantModels.ConversationTurn> history) {
		if (history == null || history.isEmpty()) {
			return List.of();
		}
		return history.stream()
			.skip(Math.max(0, history.size() - 6L))
			.map(turn -> new AiAssistantModels.ConversationTurn(turn.role(), privacyFilter.sanitize(turn.content())))
			.toList();
	}

	private RetrievalContext mergeContexts(RetrievalContext globalContext, RetrievalContext pageContext) {
		List<ManualEvidence> manualEvidence = new ArrayList<>();
		Set<Long> manualIds = new HashSet<>();
		for (ManualEvidence evidence : globalContext.manualEvidence()) {
			if (manualIds.add(evidence.articleId())) {
				manualEvidence.add(evidence);
			}
		}
		for (ManualEvidence evidence : pageContext.manualEvidence()) {
			if (manualIds.add(evidence.articleId())) {
				manualEvidence.add(evidence);
			}
		}

		List<LogicEvidence> logicEvidence = new ArrayList<>();
		Set<String> logicKeys = new HashSet<>();
		for (LogicEvidence evidence : globalContext.logicEvidence()) {
			String key = logicEvidenceKey(evidence);
			if (logicKeys.add(key)) {
				logicEvidence.add(evidence);
			}
		}
		for (LogicEvidence evidence : pageContext.logicEvidence()) {
			String key = logicEvidenceKey(evidence);
			if (logicKeys.add(key)) {
				logicEvidence.add(evidence);
			}
		}
		return new RetrievalContext(
			globalContext.logicSnapshot() != null ? globalContext.logicSnapshot() : pageContext.logicSnapshot(),
			manualEvidence.stream().limit(10).toList(),
			logicEvidence.stream().limit(12).toList());
	}

	private String logicEvidenceKey(LogicEvidence evidence) {
		return evidence.id() + ":" + evidence.type() + ":" + evidence.evidenceDigest();
	}

	private String currentPageContext(String requestedPageName, RetrievalContext pageContext) {
		String pageName = privacyFilter.sanitize(requestedPageName);
		if (!pageName.isBlank()) {
			return pageName;
		}
		if (!pageContext.manualEvidence().isEmpty()) {
			return pageContext.manualEvidence().get(0).title();
		}
		return "未提供具体页面";
	}

	private String buildKnowledgeContext(RetrievalContext context) {
		StringBuilder result = new StringBuilder();
		for (ManualEvidence evidence : context.manualEvidence().stream().limit(5).toList()) {
			result.append("[操作手册] ").append(evidence.title())
				.append("；摘要：").append(limit(evidence.summary(), 500));
			if (evidence.content() != null && !evidence.content().isBlank()) {
				result.append("；详细操作说明：").append(limit(evidence.content(), 1800));
			}
			result.append('\n');
		}
		for (LogicEvidence evidence : context.logicEvidence()) {
			if (evidence.type() == EvidenceType.ROUTE) {
				continue;
			}
			result.append("[系统逻辑] ").append(userFacingLogicText(evidence.title()))
				.append("；规则：").append(limit(userFacingLogicText(evidence.summary()), 500));
			if (evidence.permissionCode() != null && !evidence.permissionCode().isBlank()) {
				result.append("；所需权限：").append(evidence.permissionCode());
			}
			result.append('\n');
		}
		return result.isEmpty() ? "未检索到足够依据。" : result.toString();
	}

	private List<AiAssistantModels.AnswerSource> buildSources(RetrievalContext context) {
		List<AiAssistantModels.AnswerSource> sources = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (ManualEvidence evidence : context.manualEvidence()) {
			String key = "M:" + evidence.articleId();
			if (seen.add(key)) {
				sources.add(new AiAssistantModels.AnswerSource(
					"MANUAL", evidence.title(), evidence.summary(), evidence.articleId(), evidence.slug(), firstRoute(evidence.routePaths())));
			}
		}
		for (LogicEvidence evidence : context.logicEvidence()) {
			if (evidence.type() == EvidenceType.ROUTE) {
				continue;
			}
			String key = "L:" + evidence.type() + ":" + evidence.title() + ":" + evidence.routePath();
			if (seen.add(key)) {
				sources.add(new AiAssistantModels.AnswerSource(
					"SYSTEM_LOGIC", userFacingLogicText(evidence.title()), userFacingLogicText(evidence.summary()), null, null, firstRoute(evidence.routePath())));
			}
		}
		return sources.stream().limit(8).toList();
	}

	private String fallbackAnswer(RetrievalContext context, boolean providerConfigured) {
		String prefix = providerConfigured
			? "MiniMax 服务暂时不可用，已切换为系统知识库回答。\n\n"
			: "当前未配置 MiniMax，以下内容来自系统知识库与代码逻辑索引。\n\n";
		if (context.manualEvidence().isEmpty() && context.logicEvidence().isEmpty()) {
			return prefix + "没有检索到足够依据。请补充具体模块、页面名称、单据状态或提示信息后再提问。";
		}
		StringBuilder answer = new StringBuilder(prefix);
		int index = 1;
		for (ManualEvidence evidence : context.manualEvidence().stream().limit(3).toList()) {
			answer.append(index++).append(". ").append(evidence.title()).append("：")
				.append(limit(evidence.summary(), 300));
			String route = firstRoute(evidence.routePaths());
			if (!route.isBlank()) {
				answer.append("\n   页面入口：").append(route);
			}
			answer.append('\n');
		}
		for (LogicEvidence evidence : context.logicEvidence().stream().limit(3).toList()) {
			if (evidence.type() != EvidenceType.ROUTE) {
				answer.append(index++).append(". ").append(userFacingLogicText(evidence.title())).append("：")
					.append(limit(userFacingLogicText(evidence.summary()), 300)).append('\n');
			}
		}
		return answer.toString().trim();
	}

	private String firstRoute(String routes) {
		if (routes == null || routes.isBlank()) {
			return "";
		}
		for (String candidate : routes.split("[,;\\s]+")) {
			if (candidate.startsWith("/")) {
				return candidate;
			}
		}
		return "";
	}

	private String safeRoute(String route) {
		if (route == null || route.isBlank()) {
			return "未提供";
		}
		String trimmed = route.trim();
		return trimmed.startsWith("/") && trimmed.length() <= 300 ? trimmed : "未提供";
	}

	private String userFacingLogicText(String value) {
		if (value == null || value.isBlank()) {
			return "系统业务规则";
		}
		String cleaned = TECHNICAL_ROUTE.matcher(value).replaceAll("相关页面");
		cleaned = TECHNICAL_ROUTE_NAME.matcher(cleaned).replaceAll("。");
		return cleaned.replace("页面路由：", "相关功能页面：").trim();
	}

	private String limit(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return "未提供摘要";
		}
		String trimmed = value.trim();
		return trimmed.substring(0, Math.min(trimmed.length(), maxLength));
	}
}
