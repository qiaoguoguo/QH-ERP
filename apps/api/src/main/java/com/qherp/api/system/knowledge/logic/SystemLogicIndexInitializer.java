package com.qherp.api.system.knowledge.logic;

import com.qherp.api.system.knowledge.logic.SystemLogicModels.IndexEvidence;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.SnapshotManifest;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.SourceFile;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.SystemLogicIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SystemLogicIndexInitializer implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(SystemLogicIndexInitializer.class);

	private static final String INDEX_RESOURCE = "system-logic/system-logic-index.json";

	private static final int SUPPORTED_SCHEMA_VERSION = 1;

	private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

	private static final Pattern SENSITIVE_VALUE = Pattern.compile(
			"(?i)(password|secret|access[-_]?key)\\s*[:=]\\s*['\\\"][^'\\\"]+['\\\"]|BEGIN [A-Z ]*PRIVATE KEY");

	private static final List<String> ALLOWED_SOURCE_PREFIXES = List.of("apps/web/src/router/",
			"apps/web/src/navigation/", "apps/web/src/modules/", "apps/web/src/test/",
			"apps/api/src/main/java/com/qherp/api/system/", "apps/api/src/main/resources/db/migration/",
			"apps/api/src/test/java/com/qherp/api/system/");

	private static final Set<String> ALLOWED_SOURCE_FILES = Set.of(
			"apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java",
			"apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java");

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final TransactionTemplate transactionTemplate;

	public SystemLogicIndexInitializer(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
			TransactionTemplate transactionTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.transactionTemplate = transactionTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			SnapshotManifest manifest = reload();
			if (manifest != null) {
				LOGGER.info("系统逻辑知识索引已激活，摘要={}，证据数={}", manifest.sourceDigest(), manifest.evidenceCount());
			}
		}
		catch (RuntimeException ex) {
			LOGGER.error("系统逻辑知识索引导入失败，ERP 继续以人工知识库运行", ex);
		}
	}

	public SnapshotManifest reload() {
		ClassPathResource resource = new ClassPathResource(INDEX_RESOURCE);
		if (!resource.exists()) {
			LOGGER.warn("未找到系统逻辑知识索引，ERP 继续以人工知识库运行");
			return null;
		}
		SystemLogicIndex index;
		try (var input = resource.getInputStream()) {
			index = this.objectMapper.readValue(input, SystemLogicIndex.class);
		}
		catch (IOException ex) {
			throw new IllegalStateException("无法读取系统逻辑知识索引", ex);
		}
		validate(index);
		return this.transactionTemplate.execute(status -> importAndActivate(index));
	}

	private SnapshotManifest importAndActivate(SystemLogicIndex index) {
		Long existingId = this.jdbcTemplate.query("""
				select id from sys_logic_snapshot where source_digest = ? and generator_version = ?
				""", (rs, rowNum) -> rs.getLong("id"), index.sourceDigest(), index.generatorVersion())
				.stream().findFirst().orElse(null);
		Long snapshotId = existingId;
		if (snapshotId == null) {
			snapshotId = this.jdbcTemplate.queryForObject("""
					insert into sys_logic_snapshot (
						source_digest, schema_version, generator_version, generated_at,
						source_file_count, evidence_count, status
					) values (?, ?, ?, ?, ?, ?, 'INACTIVE') returning id
					""", Long.class, index.sourceDigest(), index.schemaVersion(), index.generatorVersion(),
					index.generatedAt(), index.sourceFiles().size(), index.evidence().size());
			batchInsertEvidence(snapshotId, index.evidence());
		}
		Long actualCount = this.jdbcTemplate.queryForObject(
				"select count(*) from sys_logic_evidence where snapshot_id = ?", Long.class, snapshotId);
		if (actualCount == null || actualCount != index.evidence().size()) {
			throw new IllegalStateException("系统逻辑知识索引证据数量不完整");
		}
		this.jdbcTemplate.update("update sys_logic_snapshot set status = 'INACTIVE' where status = 'ACTIVE'");
		this.jdbcTemplate.update("update sys_logic_snapshot set status = 'ACTIVE' where id = ?", snapshotId);
		return manifest(snapshotId);
	}

	private void batchInsertEvidence(Long snapshotId, List<IndexEvidence> evidence) {
		this.jdbcTemplate.batchUpdate("""
				insert into sys_logic_evidence (
					snapshot_id, evidence_key, evidence_type, domain, title, summary, keywords,
					route_path, http_method, permission_code, symbol, source_path, source_line,
					confidence, evidence_digest
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", evidence, 500, (PreparedStatement statement, IndexEvidence item) -> {
			statement.setLong(1, snapshotId);
			statement.setString(2, item.key());
			statement.setString(3, item.type().name());
			statement.setString(4, item.domain());
			statement.setString(5, item.title());
			statement.setString(6, item.summary());
			statement.setString(7, item.keywords());
			statement.setString(8, item.routePath());
			statement.setString(9, item.httpMethod());
			statement.setString(10, item.permissionCode());
			statement.setString(11, item.symbol());
			statement.setString(12, item.sourcePath());
			statement.setInt(13, item.sourceLine());
			statement.setDouble(14, item.confidence());
			statement.setString(15, item.digest());
		});
	}

	private SnapshotManifest manifest(Long id) {
		return this.jdbcTemplate.queryForObject("""
				select id, source_digest, schema_version, generator_version, generated_at,
				       source_file_count, evidence_count, status, imported_at
				from sys_logic_snapshot where id = ?
				""", (rs, rowNum) -> new SnapshotManifest(rs.getLong("id"), rs.getString("source_digest").trim(),
				rs.getInt("schema_version"), rs.getString("generator_version"),
				rs.getObject("generated_at", OffsetDateTime.class), rs.getInt("source_file_count"),
				rs.getInt("evidence_count"), rs.getString("status"),
				rs.getObject("imported_at", OffsetDateTime.class)), id);
	}

	private static void validate(SystemLogicIndex index) {
		if (index == null || index.schemaVersion() == null || index.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
			throw new IllegalArgumentException("不支持的系统逻辑知识索引版本");
		}
		if (!hasText(index.generatorVersion()) || index.generatedAt() == null || !validDigest(index.sourceDigest())) {
			throw new IllegalArgumentException("系统逻辑知识索引元数据无效");
		}
		String sourceMaterial = index.sourceFiles().stream().sorted(Comparator.comparing(SourceFile::path))
				.map(source -> {
					validateSourcePath(source.path());
					if (!validDigest(source.sha256())) throw new IllegalArgumentException("源文件摘要无效");
					return source.path() + "\0" + source.sha256() + "\n";
				}).reduce("", String::concat);
		if (!sha256(sourceMaterial).equals(index.sourceDigest())) {
			throw new IllegalArgumentException("系统逻辑知识索引源文件摘要不匹配");
		}
		for (IndexEvidence item : index.evidence()) validateEvidence(item);
	}

	private static void validateEvidence(IndexEvidence item) {
		if (item == null || !hasText(item.key()) || item.key().length() != 32 || item.type() == null
				|| !hasText(item.domain()) || !hasText(item.title()) || !hasText(item.summary())
				|| !hasText(item.keywords()) || item.sourceLine() == null || item.sourceLine() < 1
				|| item.confidence() == null || item.confidence() < 0 || item.confidence() > 1
				|| !validDigest(item.digest())) {
			throw new IllegalArgumentException("系统逻辑知识证据字段无效");
		}
		validateSourcePath(item.sourcePath());
		if (SENSITIVE_VALUE.matcher(item.summary()).find() || SENSITIVE_VALUE.matcher(item.keywords()).find()) {
			throw new IllegalArgumentException("系统逻辑知识证据包含敏感值");
		}
		String digestMaterial = String.join("\0", item.type().name(), item.domain(), item.title(), item.summary(),
				item.keywords(), nullToEmpty(item.routePath()), nullToEmpty(item.httpMethod()),
				nullToEmpty(item.permissionCode()), nullToEmpty(item.symbol()), item.sourcePath(),
				String.valueOf(item.sourceLine()), BigDecimal.valueOf(item.confidence()).stripTrailingZeros().toPlainString());
		if (!sha256(digestMaterial).equals(item.digest())) {
			throw new IllegalArgumentException("系统逻辑知识证据摘要不匹配");
		}
	}

	private static void validateSourcePath(String sourcePath) {
		if (!hasText(sourcePath) || sourcePath.contains("\\") || sourcePath.contains("..")
				|| sourcePath.startsWith("/") || sourcePath.contains("/target/") || sourcePath.contains("/node_modules/")
				|| sourcePath.contains("/dist/") || sourcePath.contains("/specs/")) {
			throw new IllegalArgumentException("系统逻辑知识来源路径越界");
		}
		boolean allowed = ALLOWED_SOURCE_FILES.contains(sourcePath)
				|| ALLOWED_SOURCE_PREFIXES.stream().anyMatch(sourcePath::startsWith);
		if (!allowed) throw new IllegalArgumentException("系统逻辑知识来源不在白名单");
	}

	private static boolean validDigest(String value) {
		return value != null && SHA_256.matcher(value).matches();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("运行环境不支持 SHA-256", ex);
		}
	}

}
