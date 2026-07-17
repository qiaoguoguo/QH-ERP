package com.qherp.api.system.production;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=project-production-stage027")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectProductionStage027Tests extends PostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v29AddsProjectProductionOutsourcingColumnsAndPermissionSeeds() {
		assertThat(columnExists("mfg_work_order", "ownership_type")).isTrue();
		assertThat(columnExists("mfg_work_order", "project_id")).isTrue();
		assertThat(columnExists("mfg_work_order", "source_mrp_run_id")).isTrue();
		assertThat(columnExists("mfg_work_order", "source_mrp_suggestion_id")).isTrue();
		assertThat(columnExists("mfg_material_issue_line", "ownership_type")).isTrue();
		assertThat(columnExists("mfg_material_issue_line", "project_id")).isTrue();
		assertThat(columnExists("mfg_material_issue_line", "cost_layer_id")).isTrue();
		assertThat(columnExists("mfg_completion_receipt", "ownership_type")).isTrue();
		assertThat(columnExists("mfg_completion_receipt", "project_id")).isTrue();
		assertThat(tableExists("mfg_outsourcing_order")).isTrue();
		assertThat(tableExists("mfg_outsourcing_issue")).isTrue();
		assertThat(tableExists("mfg_outsourcing_receipt")).isTrue();
		assertThat(tableExists("mfg_outsourcing_receipt_line")).isTrue();
		assertThat(tableExists("mfg_action_idempotency")).isTrue();
		assertThat(columnExists("mfg_outsourcing_receipt_line", "accepted_quantity")).isTrue();
		assertThat(columnExists("mfg_outsourcing_receipt_line", "rejected_quantity")).isTrue();
		assertThat(columnExists("mfg_outsourcing_receipt_line", "stock_movement_id")).isTrue();
		assertThat(permissionExists("planning:material-requirement:convert-production")).isTrue();
		assertThat(permissionExists("planning:material-requirement:convert-outsourcing")).isTrue();
		assertThat(permissionExists("production:outsourcing:view")).isTrue();
		assertThat(permissionExists("production:outsourcing-issue:post")).isTrue();
		assertThat(permissionExists("production:outsourcing-receipt:post")).isTrue();
	}

	@Test
	void stage027ProductionOwnershipPostingAndReversalPathsAreSplitIntoDedicatedServices() throws IOException {
		Path sourceRoot = Path.of("src/main/java/com/qherp/api/system");
		Path productionRoot = sourceRoot.resolve("production");
		Path reversalRoot = sourceRoot.resolve("reversal");
		assertThat(productionRoot.resolve("ProductionOwnershipPolicy.java")).exists();
		assertThat(productionRoot.resolve("ProductionInventoryPostingCoordinator.java")).exists();
		assertThat(reversalRoot.resolve("ProductionMaterialReversalService.java")).exists();

		String productionAdminService = Files.readString(productionRoot.resolve("ProductionAdminService.java"));
		String outsourcingService = Files
			.readString(productionRoot.resolve("outsourcing/ProductionOutsourcingService.java"));
		String reversalAdminService = Files.readString(reversalRoot.resolve("ReversalAdminService.java"));

		assertThat(productionAdminService).contains("ProductionOwnershipPolicy")
			.contains("ProductionInventoryPostingCoordinator")
			.doesNotContain("new InventoryPostingService.ValuationContext");
		assertThat(outsourcingService).contains("ProductionOwnershipPolicy")
			.contains("ProductionInventoryPostingCoordinator")
			.doesNotContain("new InventoryPostingService.ValuationContext");
		assertThat(reversalAdminService).contains("ProductionMaterialReversalService")
			.contains("postProductionMaterialReturn")
			.contains("postProductionMaterialSupplement");
	}

	private boolean tableExists(String tableName) {
		Integer count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from information_schema.tables
				where table_schema = 'public'
				  and table_name = ?
				""", Integer.class, tableName);
		return count != null && count > 0;
	}

	private boolean columnExists(String tableName, String columnName) {
		Integer count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from information_schema.columns
				where table_schema = 'public'
				  and table_name = ?
				  and column_name = ?
				""", Integer.class, tableName, columnName);
		return count != null && count > 0;
	}

	private boolean permissionExists(String permissionCode) {
		Integer count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_permission
				where code = ?
				""", Integer.class, permissionCode);
		return count != null && count > 0;
	}

}
