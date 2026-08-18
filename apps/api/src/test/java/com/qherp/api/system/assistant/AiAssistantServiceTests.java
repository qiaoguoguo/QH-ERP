package com.qherp.api.system.assistant;

import java.util.List;
import java.util.Optional;

import com.qherp.api.system.knowledge.logic.AiKnowledgeContextProvider;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.EvidenceType;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.LogicEvidence;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.ManualEvidence;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.RetrievalContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTests {

	@Mock
	private AiKnowledgeContextProvider contextProvider;
	@Mock
	private MiniMaxCompletionClient completionClient;
	private AiAssistantService service;

	@BeforeEach
	void setUp() {
		service = new AiAssistantService(
			contextProvider,
			completionClient,
			new AiAssistantPrivacyFilter(),
			new AiAssistantRateLimiter(12));
	}

	@Test
	void usesMiniMaxWithRetrievedKnowledge() {
		when(contextProvider.retrieve("采购订单怎么确认", "", 8)).thenReturn(context());
		when(contextProvider.retrieve("", "/procurement/orders/1", 3)).thenReturn(context());
		when(completionClient.configured()).thenReturn(true);
		when(completionClient.model()).thenReturn("MiniMax-M3");
		when(completionClient.complete(anyString(), anyList(), anyString())).thenReturn(Optional.of("进入采购订单详情后点击确认。"));

		var response = service.answer(9L, new AiAssistantModels.AskRequest(
			"采购订单怎么确认", "/procurement/orders/1", "采购订单", List.of()));

		assertThat(response.mode()).isEqualTo("MINIMAX");
		assertThat(response.answer()).contains("采购订单详情");
		assertThat(response.sources()).hasSize(2);
		verify(contextProvider).retrieve("采购订单怎么确认", "", 8);
		verify(contextProvider).retrieve("", "/procurement/orders/1", 3);
	}

	@Test
	void fallsBackToKnowledgeWhenMiniMaxIsNotConfigured() {
		when(contextProvider.retrieve("采购订单怎么确认", "", 8)).thenReturn(context());
		when(completionClient.configured()).thenReturn(false);
		when(completionClient.model()).thenReturn("MiniMax-M3");
		when(completionClient.complete(anyString(), anyList(), anyString())).thenReturn(Optional.empty());

		var response = service.answer(10L, new AiAssistantModels.AskRequest("采购订单怎么确认", "", "", List.of()));

		assertThat(response.mode()).isEqualTo("KNOWLEDGE_FALLBACK");
		assertThat(response.answer()).contains("当前未配置 MiniMax").contains("采购订单如何确认");
	}

	private RetrievalContext context() {
		return new RetrievalContext(null,
			List.of(new ManualEvidence(11L, "purchase-order-confirm", "采购订单如何确认", "在详情页确认采购订单。", "# 操作步骤\n进入采购订单详情并点击确认。", "采购管理", "/procurement/orders/:id")),
			List.of(new LogicEvidence(21L, EvidenceType.STATE_TRANSITION, "procurement", "采购订单确认规则", "草稿订单完成校验后可确认。", "/procurement/orders/:id", "POST", "procurement:order:confirm", "confirm", "hidden", 1, 1.0, "digest")));
	}
}
