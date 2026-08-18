package com.qherp.api.system.knowledge.logic;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.knowledge.logic.SystemLogicModels.RetrievalContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SystemLogicKnowledgeTests extends PostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SystemLogicIndexInitializer initializer;

	@Autowired
	private SystemLogicEvidenceQueryService queryService;

	@Autowired
	private HybridKnowledgeContextService contextService;

	@Test
	void v42CreatesVersionedLogicKnowledgeTables() {
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from sys_logic_snapshot", Long.class)).isPositive();
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from sys_logic_evidence", Long.class)).isPositive();
	}

	@Test
	void startupImportIsActiveCompleteAndIdempotent() {
		var before = this.queryService.activeManifest();
		var reloaded = this.initializer.reload();
		assertThat(before).isNotNull();
		assertThat(reloaded).isNotNull();
		assertThat(reloaded.id()).isEqualTo(before.id());
		assertThat(reloaded.status()).isEqualTo("ACTIVE");
		assertThat(reloaded.evidenceCount()).isPositive();
		assertThat(this.jdbcTemplate.queryForObject(
				"select count(*) from sys_logic_snapshot where status = 'ACTIVE'", Long.class)).isEqualTo(1L);
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*) from sys_logic_evidence e
				join sys_logic_snapshot s on s.id = e.snapshot_id
				where s.status = 'ACTIVE' and e.evidence_type = 'PERMISSION'
				""", Long.class)).isPositive();
	}

	@Test
	void activeEvidenceIsAllowlistedAndSearchable() {
		var evidence = this.queryService.search("采购", null, 20);
		assertThat(evidence).isNotEmpty();
		assertThat(evidence).allMatch(item -> !item.sourcePath().contains("target")
				&& !item.sourcePath().contains("node_modules") && !item.sourcePath().contains(".."));
		assertThat(this.queryService.search("SupplierQuoteStatus", null, 20)).isNotEmpty();
	}

	@Test
	void hybridContextCombinesManualArticlesAndCurrentCodeEvidence() {
		RetrievalContext context = this.contextService.retrieve("采购请购", "/procurement/requisitions", 10);
		assertThat(context.logicSnapshot()).isNotNull();
		assertThat(context.manualEvidence()).isNotEmpty();
		assertThat(context.logicEvidence()).isNotEmpty();
	}

}
