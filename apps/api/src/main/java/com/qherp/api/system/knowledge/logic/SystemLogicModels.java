package com.qherp.api.system.knowledge.logic;

import java.time.OffsetDateTime;
import java.util.List;

public final class SystemLogicModels {

	private SystemLogicModels() {
	}

	public enum EvidenceType {
		ROUTE,
		MENU,
		PAGE_ELEMENT,
		API,
		VALIDATION,
		PERMISSION,
		ENUM,
		STATE_TRANSITION,
		ERROR,
		DATABASE_CONSTRAINT,
		TEST_BEHAVIOR
	}

	public record SourceFile(String path, String sha256) {
	}

	public record IndexEvidence(String key, EvidenceType type, String domain, String title, String summary,
			String keywords, String routePath, String httpMethod, String permissionCode, String symbol,
			String sourcePath, Integer sourceLine, Double confidence, String digest) {
	}

	public record SystemLogicIndex(Integer schemaVersion, String generatorVersion, OffsetDateTime generatedAt,
			String sourceDigest, List<SourceFile> sourceFiles, List<IndexEvidence> evidence) {

		public SystemLogicIndex {
			sourceFiles = sourceFiles == null ? List.of() : List.copyOf(sourceFiles);
			evidence = evidence == null ? List.of() : List.copyOf(evidence);
		}
	}

	public record SnapshotManifest(Long id, String sourceDigest, Integer schemaVersion, String generatorVersion,
			OffsetDateTime generatedAt, Integer sourceFileCount, Integer evidenceCount, String status,
			OffsetDateTime importedAt) {
	}

	public record LogicEvidence(Long id, EvidenceType type, String domain, String title, String summary,
			String routePath, String httpMethod, String permissionCode, String symbol, String sourcePath,
			Integer sourceLine, Double confidence, String evidenceDigest) {
	}

	public record ManualEvidence(Long articleId, String slug, String title, String summary, String content,
			String categoryName, String routePaths) {
	}

	public record RetrievalContext(SnapshotManifest logicSnapshot, List<ManualEvidence> manualEvidence,
			List<LogicEvidence> logicEvidence) {

		public RetrievalContext {
			manualEvidence = manualEvidence == null ? List.of() : List.copyOf(manualEvidence);
			logicEvidence = logicEvidence == null ? List.of() : List.copyOf(logicEvidence);
		}
	}

}
