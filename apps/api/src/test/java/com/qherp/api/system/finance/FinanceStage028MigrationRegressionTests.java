package com.qherp.api.system.finance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FinanceStage028MigrationRegressionTests {

	private static final String LATEST_MIGRATION_VERSION = "36";

	private static final int EXPECTED_V35_CHECKSUM = -82801719;

	private static final int EXPECTED_V36_CHECKSUM = 1030907058;

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@BeforeEach
	void 清理迁移数据库() {
		Flyway.configure()
			.dataSource(dataSource())
			.locations("classpath:db/migration")
			.cleanDisabled(false)
			.load()
			.clean();
	}

	@Test
	void v29财务存量前迁v30必须保持历史台账并允许资金多目标核销() {
		migrate("29");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("29");
		Map<String, Integer> v29Checksums = migrationChecksums(jdbcTemplate);
		FinanceLedgerSeed seed = insertV29FinanceLedger(jdbcTemplate);
		Map<String, Object> receivableBefore = financeRow(jdbcTemplate, "fin_receivable", seed.receivableId());
		Map<String, Object> payableBefore = financeRow(jdbcTemplate, "fin_payable", seed.payableId());
		Map<String, Object> receiptBefore = cashRow(jdbcTemplate, "fin_receipt", seed.receiptId());
		Map<String, Object> paymentBefore = cashRow(jdbcTemplate, "fin_payment", seed.paymentId());

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo(LATEST_MIGRATION_VERSION);
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertThat(migrationChecksums(jdbcTemplate).entrySet()
			.stream()
			.filter((entry) -> Integer.parseInt(entry.getKey()) <= 29)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))).isEqualTo(v29Checksums);
		assertThat(financeRow(jdbcTemplate, "fin_receivable", seed.receivableId())).isEqualTo(receivableBefore);
		assertThat(financeRow(jdbcTemplate, "fin_payable", seed.payableId())).isEqualTo(payableBefore);
		assertThat(cashRow(jdbcTemplate, "fin_receipt", seed.receiptId())).isEqualTo(receiptBefore);
		assertThat(cashRow(jdbcTemplate, "fin_payment", seed.paymentId())).isEqualTo(paymentBefore);
		assertThat(tableCount(jdbcTemplate, "fin_sales_invoice")).isZero();
		assertThat(tableCount(jdbcTemplate, "fin_purchase_invoice")).isZero();
		assertThat(tableCount(jdbcTemplate, "fin_expense")).isZero();
		assertThat(tableCount(jdbcTemplate, "fin_voucher_draft")).isZero();
		assertThat(columnExists(jdbcTemplate, "fin_voucher_draft", "generation_version")).isTrue();
		assertThat(tableCount(jdbcTemplate, "fin_receipt_balance")).isZero();
		assertThat(tableCount(jdbcTemplate, "fin_payment_balance")).isZero();
		assertThat(constraintExists(jdbcTemplate, "fin_receipt_allocation", "uk_fin_receipt_allocation_receipt"))
			.isFalse();
		assertThat(constraintExists(jdbcTemplate, "fin_payment_allocation", "uk_fin_payment_allocation_payment"))
			.isFalse();
		assertThat(indexExists(jdbcTemplate, "uk_fin_receipt_allocation_receipt_target")).isTrue();
		assertThat(indexExists(jdbcTemplate, "uk_fin_payment_allocation_payment_target")).isTrue();

		jdbcTemplate.update("""
				insert into fin_receipt_allocation (receipt_id, receivable_id, allocated_amount)
				values (?, ?, 5.00)
				""", seed.receiptId(), seed.secondReceivableId());
		jdbcTemplate.update("""
				insert into fin_payment_allocation (payment_id, payable_id, allocated_amount)
				values (?, ?, 6.00)
				""", seed.paymentId(), seed.secondPayableId());
		assertThat(tableCount(jdbcTemplate, "fin_receipt_allocation", "receipt_id", seed.receiptId())).isEqualTo(2);
		assertThat(tableCount(jdbcTemplate, "fin_payment_allocation", "payment_id", seed.paymentId())).isEqualTo(2);
	}

	private FinanceLedgerSeed insertV29FinanceLedger(JdbcTemplate jdbcTemplate) {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit(jdbcTemplate, "028_MIG_UNIT_" + suffix);
		long warehouseId = insertWarehouse(jdbcTemplate, "028_MIG_WH_" + suffix);
		long customerId = insertCustomer(jdbcTemplate, "028_MIG_CUS_" + suffix);
		long supplierId = insertSupplier(jdbcTemplate, "028_MIG_SUP_" + suffix);
		long categoryId = insertMaterialCategory(jdbcTemplate, "028_MIG_CAT_" + suffix);
		long materialId = insertMaterial(jdbcTemplate, "028_MIG_MAT_" + suffix, categoryId, unitId);
		long firstShipmentId = createPostedShipment(jdbcTemplate, customerId, warehouseId, materialId, unitId, suffix,
				"1.000000", "30.000000");
		long secondShipmentId = createPostedShipment(jdbcTemplate, customerId, warehouseId, materialId, unitId,
				suffix + 1000, "1.000000", "15.000000");
		long firstPurchaseReceiptId = createPostedPurchaseReceipt(jdbcTemplate, supplierId, warehouseId, materialId,
				unitId, suffix, "1.000000", "22.000000");
		long secondPurchaseReceiptId = createPostedPurchaseReceipt(jdbcTemplate, supplierId, warehouseId, materialId,
				unitId, suffix + 1000, "1.000000", "18.000000");
		long receivableId = insertReceivable(jdbcTemplate, "028-MIG-AR-" + suffix, customerId, firstShipmentId,
				"028-MIG-SH-" + suffix, "30.00", "20.00", "10.00");
		long secondReceivableId = insertReceivable(jdbcTemplate, "028-MIG-AR2-" + suffix, customerId,
				secondShipmentId, "028-MIG-SH-" + (suffix + 1000), "15.00", "0.00", "15.00");
		long receiptId = insertReceipt(jdbcTemplate, "028-MIG-RC-" + suffix, customerId, "30.00");
		jdbcTemplate.update("""
				insert into fin_receipt_allocation (receipt_id, receivable_id, allocated_amount)
				values (?, ?, 20.00)
				""", receiptId, receivableId);
		long payableId = insertPayable(jdbcTemplate, "028-MIG-AP-" + suffix, supplierId, firstPurchaseReceiptId,
				"028-MIG-PR-" + suffix, "22.00", "12.00", "10.00");
		long secondPayableId = insertPayable(jdbcTemplate, "028-MIG-AP2-" + suffix, supplierId,
				secondPurchaseReceiptId, "028-MIG-PR-" + (suffix + 1000), "18.00", "0.00", "18.00");
		long paymentId = insertPayment(jdbcTemplate, "028-MIG-PM-" + suffix, supplierId, "25.00");
		jdbcTemplate.update("""
				insert into fin_payment_allocation (payment_id, payable_id, allocated_amount)
				values (?, ?, 12.00)
				""", paymentId, payableId);
		return new FinanceLedgerSeed(receivableId, secondReceivableId, receiptId, payableId, secondPayableId,
				paymentId);
	}

	private long insertUnit(JdbcTemplate jdbcTemplate, String code) {
		return jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, 6, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "单位");
	}

	private long insertWarehouse(JdbcTemplate jdbcTemplate, String code) {
		return jdbcTemplate.queryForObject("""
				insert into mst_warehouse (code, name, warehouse_type, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "仓库");
	}

	private long insertCustomer(JdbcTemplate jdbcTemplate, String code) {
		return jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "客户");
	}

	private long insertSupplier(JdbcTemplate jdbcTemplate, String code) {
		return jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "供应商");
	}

	private long insertMaterialCategory(JdbcTemplate jdbcTemplate, String code) {
		return jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "分类");
	}

	private long insertMaterial(JdbcTemplate jdbcTemplate, String code, long categoryId, long unitId) {
		return jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id, unit_id,
					status, cost_category, inventory_valuation_category, inventory_value_enabled, project_cost_enabled,
					created_by, created_at, updated_by, updated_at)
				values (?, ?, '028 迁移规格', 'RAW_MATERIAL', 'PURCHASED', ?, ?, 'ENABLED', 'DIRECT_MATERIAL',
					'VALUATED_MATERIAL', true, true, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "物料", categoryId, unitId);
	}

	private long createPostedShipment(JdbcTemplate jdbcTemplate, long customerId, long warehouseId, long materialId,
			long unitId, int suffix, String quantity, String unitPrice) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue).setScale(2);
		long orderId = jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark, created_by, created_at,
					updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'CONFIRMED', '028 迁移销售', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-MIG-SO-" + suffix, customerId, LocalDate.now(), LocalDate.now().plusDays(3));
		long orderLineId = jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, remark, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
					tax_excluded_amount, tax_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, '028 迁移销售行', 0, ?, ?, ?, 0, ?, now(), now())
				returning id
				""", Long.class, orderId, materialId, unitId, quantityValue, quantityValue, unitPriceValue,
				LocalDate.now().plusDays(3), unitPriceValue, unitPriceValue, amount, amount);
		long shipmentId = jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', '028 迁移出库', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-MIG-SH-" + suffix, orderId, customerId, warehouseId, LocalDate.now());
		jdbcTemplate.update("""
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity,
					remark, tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, null, null, '028 迁移出库行', 0, ?, ?, ?, 0, ?, now(), now())
				""", shipmentId, orderLineId, materialId, unitId, quantityValue, quantityValue, quantityValue,
				unitPriceValue, unitPriceValue, amount, amount);
		return shipmentId;
	}

	private long createPostedPurchaseReceipt(JdbcTemplate jdbcTemplate, long supplierId, long warehouseId,
			long materialId, long unitId, int suffix, String quantity, String unitPrice) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue).setScale(2);
		long orderId = jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, remark, purchase_mode,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'RECEIVED', '028 迁移采购', 'PUBLIC', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-MIG-PO-" + suffix, supplierId, LocalDate.now(), LocalDate.now().plusDays(3));
		long orderLineId = jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price, tax_rate,
					tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_included_amount,
					expected_arrival_date, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, '028 迁移采购行', now(), now())
				returning id
				""", Long.class, orderId, materialId, unitId, quantityValue, quantityValue, unitPriceValue,
				unitPriceValue, unitPriceValue, amount, amount, LocalDate.now().plusDays(3));
		long receiptId = jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', '028 迁移入库', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-MIG-PR-" + suffix, orderId, supplierId, warehouseId, LocalDate.now());
		jdbcTemplate.update("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity,
					remark, purchase_mode, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, null, null, '028 迁移入库行', 'PUBLIC', now(), now())
				""", receiptId, orderLineId, materialId, unitId, quantityValue, quantityValue, quantityValue);
		return receiptId;
	}

	private long insertReceivable(JdbcTemplate jdbcTemplate, String receivableNo, long customerId, long sourceId,
			String sourceNo, String totalAmount, String receivedAmount, String unreceivedAmount) {
		return jdbcTemplate.queryForObject("""
				insert into fin_receivable (
					receivable_no, customer_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, received_amount, unreceived_amount, status, remark, created_by, created_at,
					updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'SALES_SHIPMENT', ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', '028 迁移历史应收',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, receivableNo, customerId, sourceId, sourceNo, LocalDate.now(),
				LocalDate.now().plusDays(30), new BigDecimal(totalAmount), new BigDecimal(receivedAmount),
				new BigDecimal(unreceivedAmount));
	}

	private long insertReceipt(JdbcTemplate jdbcTemplate, String receiptNo, long customerId, String amount) {
		return jdbcTemplate.queryForObject("""
				insert into fin_receipt (
					receipt_no, customer_id, receipt_date, amount, method, status, remark, created_by, created_at,
					updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, 'BANK_TRANSFER', 'POSTED', '028 迁移历史收款', 'test', now(), 'test', now(),
					'test', now())
				returning id
				""", Long.class, receiptNo, customerId, LocalDate.now(), new BigDecimal(amount));
	}

	private long insertPayable(JdbcTemplate jdbcTemplate, String payableNo, long supplierId, long sourceId,
			String sourceNo, String totalAmount, String paidAmount, String unpaidAmount) {
		return jdbcTemplate.queryForObject("""
				insert into fin_payable (
					payable_no, supplier_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, paid_amount, unpaid_amount, status, remark, created_by, created_at, updated_by,
					updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', '028 迁移历史应付',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, payableNo, supplierId, sourceId, sourceNo, LocalDate.now(),
				LocalDate.now().plusDays(30), new BigDecimal(totalAmount), new BigDecimal(paidAmount),
				new BigDecimal(unpaidAmount));
	}

	private long insertPayment(JdbcTemplate jdbcTemplate, String paymentNo, long supplierId, String amount) {
		return jdbcTemplate.queryForObject("""
				insert into fin_payment (
					payment_no, supplier_id, payment_date, amount, method, status, remark, created_by, created_at,
					updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, 'BANK_TRANSFER', 'POSTED', '028 迁移历史付款', 'test', now(), 'test', now(),
					'test', now())
				returning id
				""", Long.class, paymentNo, supplierId, LocalDate.now(), new BigDecimal(amount));
	}

	private Map<String, Object> financeRow(JdbcTemplate jdbcTemplate, String tableName, long id) {
		String partyColumn = "fin_receivable".equals(tableName) ? "customer_id" : "supplier_id";
		String settledColumn = "fin_receivable".equals(tableName) ? "received_amount" : "paid_amount";
		String openColumn = "fin_receivable".equals(tableName) ? "unreceived_amount" : "unpaid_amount";
		return jdbcTemplate.queryForMap("select " + partyColumn + ", source_type, source_id, source_no, business_date,"
				+ " due_date, total_amount, " + settledColumn + ", adjusted_amount, " + openColumn
				+ ", status, version from " + tableName + " where id = ?", id);
	}

	private Map<String, Object> cashRow(JdbcTemplate jdbcTemplate, String tableName, long id) {
		String partyColumn = "fin_receipt".equals(tableName) ? "customer_id" : "supplier_id";
		String dateColumn = "fin_receipt".equals(tableName) ? "receipt_date" : "payment_date";
		return jdbcTemplate.queryForMap("select " + partyColumn + ", " + dateColumn
				+ ", amount, method, status, version from " + tableName + " where id = ?", id);
	}

	private long tableCount(JdbcTemplate jdbcTemplate, String tableName) {
		Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
		return count == null ? 0 : count;
	}

	private long tableCount(JdbcTemplate jdbcTemplate, String tableName, String columnName, long value) {
		Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + columnName
				+ " = ?", Long.class, value);
		return count == null ? 0 : count;
	}

	private boolean constraintExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
		Boolean exists = jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from pg_constraint c
					join pg_class t on t.oid = c.conrelid
					where t.relname = ?
					and c.conname = ?
				)
				""", Boolean.class, tableName, constraintName);
		return Boolean.TRUE.equals(exists);
	}

	private boolean indexExists(JdbcTemplate jdbcTemplate, String indexName) {
		Boolean exists = jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
				"public." + indexName);
		return Boolean.TRUE.equals(exists);
	}

	private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
		Boolean exists = jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from information_schema.columns
					where table_schema = 'public'
					and table_name = ?
					and column_name = ?
				)
				""", Boolean.class, tableName, columnName);
		return Boolean.TRUE.equals(exists);
	}

	private void migrate(String target) {
		var configuration = Flyway.configure().dataSource(dataSource()).locations("classpath:db/migration");
		if (target != null) {
			configuration.target(target);
		}
		configuration.load().migrate();
	}

	private DataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		dataSource.setDriverClassName("org.postgresql.Driver");
		return dataSource;
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

	private Map<String, Integer> migrationChecksums(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.query("""
				select version, checksum
				from flyway_schema_history
				where success = true
				  and version is not null
				order by installed_rank
				""", (rs, rowNum) -> Map.entry(rs.getString("version"), rs.getInt("checksum")))
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private void assertCurrentMigrationChecksums(JdbcTemplate jdbcTemplate) {
		assertThat(migrationChecksum(jdbcTemplate, "29")).isEqualTo(774334682);
		assertThat(migrationChecksum(jdbcTemplate, "30")).isEqualTo(2130342893);
		assertThat(migrationChecksum(jdbcTemplate, "31")).isEqualTo(-2074547591);
		assertThat(migrationChecksum(jdbcTemplate, "32")).isEqualTo(249406902);
		assertThat(migrationChecksum(jdbcTemplate, "33")).isEqualTo(612501943);
		assertThat(migrationChecksum(jdbcTemplate, "34")).isEqualTo(-629066235);
		assertThat(migrationChecksum(jdbcTemplate, "35")).isEqualTo(EXPECTED_V35_CHECKSUM);
		assertThat(migrationChecksum(jdbcTemplate, "36")).isEqualTo(EXPECTED_V36_CHECKSUM);
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
	}

	private Integer migrationChecksum(JdbcTemplate jdbcTemplate, String version) {
		return jdbcTemplate.queryForObject("""
				select checksum
				from flyway_schema_history
				where success = true
				  and version = ?
				""", Integer.class, version);
	}

	private long failedMigrationCount(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForObject("""
				select count(*)
				from flyway_schema_history
				where success = false
				""", Long.class);
	}

	private record FinanceLedgerSeed(long receivableId, long secondReceivableId, long receiptId, long payableId,
			long secondPayableId, long paymentId) {
	}

}
