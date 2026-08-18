package com.qherp.api.system.knowledge.logic;

import com.qherp.api.system.knowledge.KnowledgeModels.KnowledgeArticleSummary;
import com.qherp.api.system.knowledge.KnowledgeQueryService;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.ManualEvidence;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.LogicEvidence;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.RetrievalContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HybridKnowledgeContextService implements AiKnowledgeContextProvider {
	private static final List<String> QUESTION_NOISE = List.of(
			"请问", "麻烦", "系统中", "系统里", "这个系统", "当前系统", "怎么样", "怎么", "如何", "为什么",
			"是什么", "什么意思", "能不能", "是否", "可以", "应该", "需要", "进行", "功能", "页面", "哪里");
	private static final Map<String, String> ACTION_ALIASES = Map.ofEntries(
			Map.entry("确认", "confirm"), Map.entry("提交", "submit"), Map.entry("审批", "approve"),
			Map.entry("驳回", "reject"), Map.entry("取消", "cancel"), Map.entry("关闭", "close"),
			Map.entry("创建", "create"), Map.entry("新增", "create"), Map.entry("编辑", "update"),
			Map.entry("修改", "update"), Map.entry("删除", "delete"), Map.entry("导入", "import"),
			Map.entry("导出", "export"), Map.entry("入库", "receipt"), Map.entry("发货", "shipment"),
			Map.entry("退货", "return"), Map.entry("过账", "post"), Map.entry("撤回", "withdraw"));

	private final KnowledgeQueryService knowledgeQueryService;

	private final SystemLogicEvidenceQueryService logicQueryService;

	public HybridKnowledgeContextService(KnowledgeQueryService knowledgeQueryService,
			SystemLogicEvidenceQueryService logicQueryService) {
		this.knowledgeQueryService = knowledgeQueryService;
		this.logicQueryService = logicQueryService;
	}

	@Override
	public RetrievalContext retrieve(String query, String routePath, int requestedLimit) {
		int limit = Math.max(1, Math.min(requestedLimit, 20));
		Map<Long, KnowledgeArticleSummary> manual = new LinkedHashMap<>();
		if (hasText(routePath)) {
			this.knowledgeQueryService.byRoute(routePath, 1, limit).items()
					.forEach(article -> manual.put(article.id(), article));
		}
		if (hasText(query) && manual.size() < limit) {
			this.knowledgeQueryService.articles(query, null, null, 1, limit).items()
					.forEach(article -> manual.putIfAbsent(article.id(), article));
		}
		if (hasText(query) && manual.size() < limit) {
			naturalLanguageMatches(query, limit).forEach(article -> manual.putIfAbsent(article.id(), article));
		}
		List<ManualEvidence> manualEvidence = manual.values().stream().limit(limit)
				.map(article -> {
					var detail = this.knowledgeQueryService.article(article.id());
					return new ManualEvidence(article.id(), article.slug(), article.title(), article.summary(),
							detail.content(), article.categoryName(), article.routePaths());
				})
				.toList();
		Map<Long, LogicEvidence> logicEvidence = new LinkedHashMap<>();
		this.logicQueryService.search(query, routePath, limit)
				.forEach(evidence -> logicEvidence.put(evidence.id(), evidence));
		if (hasText(query)) {
			for (String term : logicSearchTerms(query, manualEvidence)) {
				this.logicQueryService.search(term, "", Math.min(limit, 4))
						.forEach(evidence -> logicEvidence.putIfAbsent(evidence.id(), evidence));
			}
		}
		return new RetrievalContext(this.logicQueryService.activeManifest(), manualEvidence,
				logicEvidence.values().stream().limit(limit).toList());
	}

	private List<KnowledgeArticleSummary> naturalLanguageMatches(String query, int limit) {
		String normalizedQuery = normalizeQuestion(query);
		if (normalizedQuery.isBlank()) {
			return List.of();
		}
		return this.knowledgeQueryService.articles(null, null, null, 1, 100).items().stream()
				.map(article -> new ScoredArticle(article, relevanceScore(normalizedQuery, article)))
				.filter(candidate -> candidate.score() >= 3)
				.sorted((left, right) -> Integer.compare(right.score(), left.score()))
				.limit(limit)
				.map(ScoredArticle::article)
				.toList();
	}

	private int relevanceScore(String normalizedQuery, KnowledgeArticleSummary article) {
		String title = normalize(article.title());
		String searchable = normalize(String.join(" ", safe(article.title()), safe(article.summary()),
				safe(article.keywords()), safe(article.pageNames())));
		int score = searchable.contains(normalizedQuery) ? 100 : 0;
		for (int size : List.of(4, 3, 2)) {
			int weight = size == 4 ? 10 : size == 3 ? 5 : 2;
			for (String gram : ngrams(normalizedQuery, size)) {
				if (searchable.contains(gram)) {
					score += weight;
					if (title.contains(gram)) {
						score += weight;
					}
				}
			}
		}
		return score;
	}

	private Set<String> logicSearchTerms(String query, List<ManualEvidence> manualEvidence) {
		Set<String> routeBases = new LinkedHashSet<>();
		for (ManualEvidence evidence : manualEvidence.stream().limit(3).toList()) {
			if (!hasText(evidence.routePaths())) {
				continue;
			}
			for (String route : evidence.routePaths().split("\\R")) {
				List<String> segments = List.of(route.split("/")).stream()
						.filter(segment -> !segment.isBlank() && !segment.startsWith(":"))
						.toList();
				if (segments.size() >= 2) {
					routeBases.add(segments.get(0) + "/" + segments.get(1));
				}
			}
		}
		Set<String> actions = new LinkedHashSet<>();
		ACTION_ALIASES.forEach((chinese, english) -> {
			if (query.contains(chinese)) {
				actions.add(english);
			}
		});
		Set<String> terms = new LinkedHashSet<>();
		for (String routeBase : routeBases) {
			for (String action : actions) {
				terms.add(routeBase + "/" + action);
			}
		}
		terms.addAll(routeBases);
		terms.addAll(actions);
		return terms;
	}

	private static String normalizeQuestion(String value) {
		String normalized = normalize(value);
		for (String noise : QUESTION_NOISE) {
			normalized = normalized.replace(noise, "");
		}
		return normalized;
	}

	private static String normalize(String value) {
		return safe(value).toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
	}

	private static Set<String> ngrams(String value, int size) {
		Set<String> grams = new LinkedHashSet<>();
		if (value.length() < size) {
			return grams;
		}
		for (int index = 0; index <= value.length() - size; index++) {
			grams.add(value.substring(index, index + size));
		}
		return grams;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private record ScoredArticle(KnowledgeArticleSummary article, int score) {
	}

}
