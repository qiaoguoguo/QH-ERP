package com.qherp.api.system.stage022;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class Stage022MigrationRegressionTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Test
	void v18RepresentativeDataMigratesToV19WithoutBackfillingApprovalsOrChangingFacts() {
		migrate("18");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("18");
		assertThat(tableExists(jdbcTemplate, "platform_approval_instance")).isFalse();

		RepresentativeData data = insertRepresentativeV18Data(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("29");
		assertThat(tableExists(jdbcTemplate, "platform_approval_instance")).isTrue();
		assertThat(columnExists(jdbcTemplate, "sys_audit_log", "detail_json")).isTrue();
		assertThat(approvalDefinitionExists(jdbcTemplate, "SALES_PROJECT_CONTRACT_ACTIVATION")).isTrue();
		assertThat(approvalDefinitionExists(jdbcTemplate, "BOM_ECO_APPLICATION")).isTrue();
		assertThat(approvalStepExists(jdbcTemplate, "SALES_PROJECT_CONTRACT_ACTIVATION",
				"sales:contract:activate-approve")).isTrue();
		assertThat(approvalStepExists(jdbcTemplate, "BOM_ECO_APPLICATION",
				"material:bom-eco:apply-approve")).isTrue();
		assertThat(printTemplateExists(jdbcTemplate, "CONTRACT_ACTIVATION_APPROVAL_V1",
				"SALES_PROJECT_CONTRACT_ACTIVATION")).isTrue();
		assertThat(printTemplateExists(jdbcTemplate, "BOM_ECO_APPLICATION_APPROVAL_V1",
				"BOM_ECO_APPLICATION")).isTrue();
		assertThat(count(jdbcTemplate, "platform_approval_instance")).isZero();
		assertThat(count(jdbcTemplate, "platform_approval_task")).isZero();
		assertThat(count(jdbcTemplate, "platform_message")).isZero();
		assertThat(count(jdbcTemplate, "platform_document_task")).isZero();

		assertThat(status(jdbcTemplate, "sal_project_contract", data.contractId())).isEqualTo("EFFECTIVE");
		assertThat(status(jdbcTemplate, "mfg_bom_engineering_change", data.ecoId())).isEqualTo("APPLIED");
		assertThat(status(jdbcTemplate, "mfg_work_order", data.workOrderId())).isEqualTo("RELEASED");
		assertThat(status(jdbcTemplate, "mfg_bom", data.sourceBomId())).isEqualTo("ENABLED");
		assertThat(status(jdbcTemplate, "mfg_bom", data.targetBomId())).isEqualTo("ENABLED");

		assertThat(jdbcTemplate.queryForObject("""
				select cost_category || ':' || inventory_valuation_category || ':' || tracking_method
				from mst_material
				where id = ?
				""", String.class, data.rawMaterialId())).isEqualTo("UNCLASSIFIED:UNCLASSIFIED:NONE");
		assertThat(jdbcTemplate.queryForObject("""
				select business_quantity || ':' || base_required_quantity || ':' || quantity_basis
				from mfg_work_order_material
				where work_order_id = ?
				""", String.class, data.workOrderId())).isEqualTo("2.000000:2.000000:BASE_UNIT");
		assertThat(jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_bom b
				join mfg_bom_item i on i.bom_id = b.id
				where b.parent_material_id = ?
				  and b.status = 'ENABLED'
				  and i.child_material_id = ?
				""", Long.class, data.parentMaterialId(), data.rawMaterialId())).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_order so
				join sal_project_contract c on c.id = so.contract_id
				where so.project_id = ?
				  and c.status = 'EFFECTIVE'
				""", Long.class, data.projectId())).isOne();
		assertThat(jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_order po
				join proc_purchase_receipt pr on pr.order_id = po.id
				join fin_payable fp on fp.source_id = pr.id
				where po.supplier_id = ?
				  and fp.status = 'CONFIRMED'
				""", Long.class, data.supplierId())).isOne();
		assertThat(jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_shipment ss
				join fin_receivable fr on fr.source_id = ss.id
				where ss.customer_id = ?
				  and fr.status = 'CONFIRMED'
				""", Long.class, data.customerId())).isOne();
		assertThat(jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_balance
				where material_id = ?
				  and quality_status = 'QUALIFIED'
				""", Long.class, data.rawMaterialId())).isOne();
	}

	private RepresentativeData insertRepresentativeV18Data(JdbcTemplate jdbcTemplate) {
		long userId = id(jdbcTemplate, """
				insert into sys_user (
					username, password_hash, display_name, status, created_by, created_at, updated_by, updated_at
				)
				values ('migration-admin', '{noop}x', '迁移管理员', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""");
		long unitId = id(jdbcTemplate, """
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values ('MIG_UNIT', '迁移单位', 6, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""");
		long warehouseId = id(jdbcTemplate, """
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values ('MIG_WH', '迁移仓库', 'RAW', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""");
		long supplierId = id(jdbcTemplate, """
				insert into mst_supplier (
					code, name, status, created_by, created_at, updated_by, updated_at
				)
				values ('MIG_SUP', '迁移供应商', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""");
		long customerId = id(jdbcTemplate, """
				insert into mst_customer (
					code, name, status, created_by, created_at, updated_by, updated_at
				)
				values ('MIG_CUS', '迁移客户', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""");
		long categoryId = id(jdbcTemplate, """
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values ('MIG_CAT', '迁移分类', 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""");
		long parentMaterialId = id(jdbcTemplate, """
				insert into mst_material (
					code, name, material_type, source_type, category_id, unit_id, status,
					created_by, created_at, updated_by, updated_at
				)
				values ('MIG_PARENT', '迁移成品', 'FINISHED_GOOD', 'SELF_MADE', ?, ?, 'ENABLED',
					'test', now(), 'test', now())
				returning id
				""", categoryId, unitId);
		long rawMaterialId = id(jdbcTemplate, """
				insert into mst_material (
					code, name, material_type, source_type, category_id, unit_id, status,
					created_by, created_at, updated_by, updated_at
				)
				values ('MIG_RAW', '迁移原料', 'RAW_MATERIAL', 'PURCHASED', ?, ?, 'ENABLED',
					'test', now(), 'test', now())
				returning id
				""", categoryId, unitId);
		long sourceBomId = insertBom(jdbcTemplate, "MIG_BOM_V1", parentMaterialId, unitId, "V1",
				"2026-01-01", "2026-06-30");
		long targetBomId = insertBom(jdbcTemplate, "MIG_BOM_V2", parentMaterialId, unitId, "V2",
				"2026-07-01", null);
		insertBomItem(jdbcTemplate, sourceBomId, rawMaterialId, unitId, "2.000000");
		insertBomItem(jdbcTemplate, targetBomId, rawMaterialId, unitId, "3.000000");
		long ecoId = id(jdbcTemplate, """
				insert into mfg_bom_engineering_change (
					eco_no, source_bom_id, target_bom_id, parent_material_id, effective_from, effective_to,
					change_reason, impact_scope, change_summary, status, applied_by, applied_at,
					created_by, created_at, updated_by, updated_at
				)
				values ('MIG_ECO_001', ?, ?, ?, date '2026-07-01', null,
					'历史 ECO 原因', '历史 ECO 影响', '历史 ECO 摘要', 'APPLIED', 'test', now(),
					'test', now(), 'test', now())
				returning id
				""", sourceBomId, targetBomId, parentMaterialId);
		long workOrderId = id(jdbcTemplate, """
				insert into mfg_work_order (
					work_order_no, product_material_id, bom_id, planned_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_start_date, planned_finish_date, status,
					created_by, created_at, updated_by, updated_at, released_by, released_at
				)
				values ('MIG_WO_001', ?, ?, 1.000000, ?, ?, date '2026-02-01', date '2026-02-10',
					'RELEASED', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", parentMaterialId, sourceBomId, warehouseId, warehouseId);
		long sourceBomItemId = jdbcTemplate.queryForObject("""
				select id
				from mfg_bom_item
				where bom_id = ?
				  and line_no = 10
				""", Long.class, sourceBomId);
		jdbcTemplate.update("""
				insert into mfg_work_order_material (
					work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity,
					loss_rate, created_at, updated_at, business_unit_id, business_quantity,
					base_unit_id, base_required_quantity, quantity_basis
				)
				values (?, 10, ?, ?, ?, 2.000000, 0, now(), now(), ?, 2.000000, ?, 2.000000, 'BASE_UNIT')
				""", workOrderId, sourceBomItemId, rawMaterialId, unitId, unitId, unitId);
		jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, created_at, updated_at
				)
				values (?, ?, ?, 10.000000, 0.000000, 'QUALIFIED', now(), now())
				""", warehouseId, rawMaterialId, unitId);
		long projectId = id(jdbcTemplate, """
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at
				)
				values ('MIG_PRJ_001', '迁移项目', ?, ?, date '2026-01-01', date '2026-12-31',
					'ACTIVE', 100000.00, 60000.00, 'test', now(), 'test', now())
				returning id
				""", customerId, userId);
		long contractId = id(jdbcTemplate, """
				insert into sal_project_contract (
					contract_no, project_id, contract_type, name, signed_date, effective_start_date,
					effective_end_date, amount, status, created_by, created_at, updated_by, updated_at,
					activated_by, activated_at
				)
				values ('MIG_CON_001', ?, 'MAIN', '迁移历史生效合同', date '2026-01-05',
					date '2026-01-05', date '2026-12-31', 100000.00, 'EFFECTIVE',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", projectId);
		long purchaseOrderId = id(jdbcTemplate, """
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values ('MIG_PO_001', ?, date '2026-01-10', date '2026-01-20', 'CONFIRMED',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", supplierId);
		long purchaseOrderLineId = id(jdbcTemplate, """
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity,
					unit_price, expected_arrival_date, created_at, updated_at
				)
				values (?, 10, ?, ?, 5.000000, 5.000000, 10.000000, date '2026-01-20', now(), now())
				returning id
				""", purchaseOrderId, rawMaterialId, unitId);
		long purchaseReceiptId = id(jdbcTemplate, """
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values ('MIG_PR_001', ?, ?, ?, date '2026-01-21', 'POSTED',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", purchaseOrderId, supplierId, warehouseId);
		long purchaseReceiptLineId = id(jdbcTemplate, """
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity,
					before_quantity, after_quantity, created_at, updated_at
				)
				values (?, 10, ?, ?, ?, 5.000000, 0.000000, 5.000000, 5.000000,
					0.000000, 5.000000, now(), now())
				returning id
				""", purchaseReceiptId, purchaseOrderLineId, rawMaterialId, unitId);
		long salesOrderId = id(jdbcTemplate, """
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at,
					project_id, contract_id
				)
				values ('MIG_SO_001', ?, date '2026-02-01', date '2026-02-10', 'CONFIRMED',
					'test', now(), 'test', now(), 'test', now(), ?, ?)
				returning id
				""", customerId, projectId, contractId);
		long salesOrderLineId = id(jdbcTemplate, """
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity,
					unit_price, expected_ship_date, created_at, updated_at, reservation_warehouse_id
				)
				values (?, 10, ?, ?, 1.000000, 1.000000, 100000.000000, date '2026-02-10',
					now(), now(), ?)
				returning id
				""", salesOrderId, parentMaterialId, unitId, warehouseId);
		long shipmentId = id(jdbcTemplate, """
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values ('MIG_SHIP_001', ?, ?, ?, date '2026-02-11', 'POSTED',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", salesOrderId, customerId, warehouseId);
		long shipmentLineId = id(jdbcTemplate, """
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity,
					before_quantity, after_quantity, created_at, updated_at
				)
				values (?, 10, ?, ?, ?, 1.000000, 0.000000, 1.000000, 1.000000,
					1.000000, 0.000000, now(), now())
				returning id
				""", shipmentId, salesOrderLineId, parentMaterialId, unitId);
		long receivableId = id(jdbcTemplate, """
				insert into fin_receivable (
					receivable_no, customer_id, source_type, source_id, source_no, business_date,
					due_date, total_amount, received_amount, unreceived_amount, status,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values ('MIG_AR_001', ?, 'SALES_SHIPMENT', ?, 'MIG_SHIP_001', date '2026-02-11',
					date '2026-03-11', 100000.00, 0.00, 100000.00, 'CONFIRMED',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", customerId, shipmentId);
		jdbcTemplate.update("""
				insert into fin_receivable_source (
					receivable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'SALES_SHIPMENT', ?, 'MIG_SHIP_001', ?, 10, 100000.00)
				""", receivableId, shipmentId, shipmentLineId);
		long payableId = id(jdbcTemplate, """
				insert into fin_payable (
					payable_no, supplier_id, source_type, source_id, source_no, business_date,
					due_date, total_amount, paid_amount, unpaid_amount, status,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values ('MIG_AP_001', ?, 'PURCHASE_RECEIPT', ?, 'MIG_PR_001', date '2026-01-21',
					date '2026-02-21', 50.00, 0.00, 50.00, 'CONFIRMED',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", supplierId, purchaseReceiptId);
		jdbcTemplate.update("""
				insert into fin_payable_source (
					payable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'PURCHASE_RECEIPT', ?, 'MIG_PR_001', ?, 10, 50.00)
				""", payableId, purchaseReceiptId, purchaseReceiptLineId);
		return new RepresentativeData(customerId, supplierId, projectId, contractId, parentMaterialId, rawMaterialId,
				sourceBomId, targetBomId, ecoId, workOrderId);
	}

	private long insertBom(JdbcTemplate jdbcTemplate, String bomCode, long parentMaterialId, long unitId,
			String versionCode, String effectiveFrom, String effectiveTo) {
		return id(jdbcTemplate, """
				insert into mfg_bom (
					bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id,
					status, effective_from, effective_to, enabled_by, enabled_at,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 1.000000, ?, 'ENABLED', ?::date, ?::date, 'test', now(),
					'test', now(), 'test', now())
				returning id
				""", bomCode, parentMaterialId, versionCode, "迁移 BOM " + versionCode, unitId,
				effectiveFrom, effectiveTo);
	}

	private void insertBomItem(JdbcTemplate jdbcTemplate, long bomId, long materialId, long unitId, String quantity) {
		jdbcTemplate.update("""
				insert into mfg_bom_item (
					bom_id, line_no, child_material_id, unit_id, quantity, loss_rate, created_at, updated_at,
					business_unit_id, business_quantity, base_unit_id, base_quantity, quantity_basis
				)
				values (?, 10, ?, ?, ?::numeric, 0, now(), now(), ?, ?::numeric, ?, ?::numeric, 'BASE_UNIT')
				""", bomId, materialId, unitId, quantity, unitId, quantity, unitId, quantity);
	}

	private void migrate(String target) {
		var configuration = Flyway.configure()
			.dataSource(dataSource())
			.locations("classpath:db/migration");
		if (target != null) {
			configuration.target(target);
		}
		configuration.load().migrate();
	}

	private DataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		dataSource.setDriverClassName("org.postgresql.Driver");
		return dataSource;
	}

	private long id(JdbcTemplate jdbcTemplate, String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}

	private long count(JdbcTemplate jdbcTemplate, String tableName) {
		return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
	}

	private boolean approvalDefinitionExists(JdbcTemplate jdbcTemplate, String sceneCode) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from platform_approval_definition
				where scene_code = ?
				  and status = 'ENABLED'
				""", Boolean.class, sceneCode);
	}

	private boolean approvalStepExists(JdbcTemplate jdbcTemplate, String sceneCode, String permissionCode) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from platform_approval_definition_step s
				join platform_approval_definition d on d.id = s.definition_id
				where d.scene_code = ?
				  and s.step_no = 1
				  and s.candidate_permission_code = ?
				""", Boolean.class, sceneCode, permissionCode);
	}

	private boolean printTemplateExists(JdbcTemplate jdbcTemplate, String templateCode, String sceneCode) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from platform_print_template
				where template_code = ?
				  and scene_code = ?
				  and status = 'ENABLED'
				""", Boolean.class, templateCode, sceneCode);
	}

	private String status(JdbcTemplate jdbcTemplate, String tableName, long id) {
		return jdbcTemplate.queryForObject("select status from " + tableName + " where id = ?", String.class, id);
	}

	private String currentFlywayVersion(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForObject("""
				select version
				from flyway_schema_history
				where success = true
				order by installed_rank desc
				limit 1
				""", String.class);
	}

	private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from information_schema.tables
				where table_schema = 'public'
				  and table_name = ?
				""", Boolean.class, tableName);
	}

	private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from information_schema.columns
				where table_schema = 'public'
				  and table_name = ?
				  and column_name = ?
				""", Boolean.class, tableName, columnName);
	}

	private record RepresentativeData(
			long customerId,
			long supplierId,
			long projectId,
			long contractId,
			long parentMaterialId,
			long rawMaterialId,
			long sourceBomId,
			long targetBomId,
			long ecoId,
			long workOrderId) {
	}

}
