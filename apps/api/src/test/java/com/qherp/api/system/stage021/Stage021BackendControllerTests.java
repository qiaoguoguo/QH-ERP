package com.qherp.api.system.stage021;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=stage021-backend")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Stage021BackendControllerTests extends PostgresIntegrationTest {

	private static final String UNITS = "/api/admin/master/units";

	private static final String WAREHOUSES = "/api/admin/master/warehouses";

	private static final String CATEGORIES = "/api/admin/master/material-categories";

	private static final String MATERIALS = "/api/admin/master/materials";

	private static final String CUSTOMERS = "/api/admin/master/customers";

	private static final String SUPPLIERS = "/api/admin/master/suppliers";

	private static final String UNIT_CONVERSIONS = "/api/admin/master/unit-conversions";

	private static final String CODING_RULES = "/api/admin/coding-rules";

	private static final String BOMS = "/api/admin/boms";

	private static final String ECO = "/api/admin/bom-engineering-changes";

	private static final String SUBSTITUTES = "/api/admin/material-substitutes";

	private static final String PRODUCTION = "/api/admin/production";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void unitConversionsAndCodingRulesExposeFrozenContract() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long baseUnitId = createUnit(admin, "T21_UC_EA", "二十一换算基本单位");
		long boxUnitId = createUnit(admin, "T21_UC_BOX", "二十一换算箱");
		long categoryId = createCategory(admin, "T21_UC_CAT", "二十一换算分类");
		long materialId = createMaterial(admin, "T21_UC_MAT", "二十一换算物料", "RAW_MATERIAL", "PURCHASED",
				categoryId, baseUnitId);
		long disabledUnitId = createUnitWithStatus(admin, "T21_UC_DISABLED", "二十一禁用单位", "DISABLED");

		ResponseEntity<String> unitCandidates = get(UNIT_CONVERSIONS
				+ "/unit-candidates?keyword=T21_UC_DISABLED&page=1&pageSize=1&selectedIds=" + baseUnitId, admin);
		assertThat(unitCandidates.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode unitCandidateData = data(unitCandidates);
		assertThat(unitCandidateData.get("items").get(0).get("id").longValue()).isEqualTo(disabledUnitId);
		assertThat(unitCandidateData.get("items").get(0).get("disabled").booleanValue()).isTrue();
		assertThat(unitCandidateData.get("items").get(0).get("disabledReason").asText()).isEqualTo("状态不可用");
		assertThat(containsId(unitCandidateData.get("selectedItems"), baseUnitId)).isTrue();

		ResponseEntity<String> createConversion = exchange(HttpMethod.POST, UNIT_CONVERSIONS,
				unitConversionPayload(materialId, boxUnitId, "2.500000", 3, "HALF_UP", "2026-07-01", null),
				admin);
		assertThat(createConversion.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode conversion = data(createConversion);
		long conversionId = conversion.get("id").longValue();
		assertThat(conversion.get("baseUnitId").longValue()).isEqualTo(baseUnitId);
		assertThat(conversion.get("businessUnitId").longValue()).isEqualTo(boxUnitId);
		assertThat(conversion.get("conversionRate").asText()).isEqualTo("2.500000");
		assertThat(conversion.get("quantityScale").intValue()).isEqualTo(3);
		assertThat(conversion.get("roundingMode").asText()).isEqualTo("HALF_UP");
		assertThat(conversion.get("status").asText()).isEqualTo("ENABLED");
		assertThat(conversion.get("version").isIntegralNumber()).isTrue();
		assertAuditLog("UNIT_CONVERSION_CREATE", "UNIT_CONVERSION", conversionId, "POST", UNIT_CONVERSIONS);

		Map<String, Object> conversionUpdate = unitConversionPayload(materialId, boxUnitId, "2.500000", 3,
				"HALF_UP", "2026-07-01", null);
		conversionUpdate.put("version", conversion.get("version").longValue());
		ResponseEntity<String> updatedConversionResponse = exchange(HttpMethod.PUT,
				UNIT_CONVERSIONS + "/" + conversionId, conversionUpdate, admin);
		assertThat(updatedConversionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode updatedConversion = data(updatedConversionResponse);
		assertAuditLog("UNIT_CONVERSION_UPDATE", "UNIT_CONVERSION", conversionId, "PUT",
				UNIT_CONVERSIONS + "/" + conversionId);
		assertError(exchange(HttpMethod.PUT, UNIT_CONVERSIONS + "/" + conversionId, conversionUpdate, admin),
				HttpStatus.CONFLICT, "VERSION_CONFLICT");
		JsonNode disabledConversion = data(exchange(HttpMethod.PUT, UNIT_CONVERSIONS + "/" + conversionId
				+ "/disable", Map.of("version", updatedConversion.get("version").longValue()), admin));
		assertAuditLog("UNIT_CONVERSION_DISABLE", "UNIT_CONVERSION", conversionId, "PUT",
				UNIT_CONVERSIONS + "/" + conversionId + "/disable");
		JsonNode enabledConversion = data(exchange(HttpMethod.PUT, UNIT_CONVERSIONS + "/" + conversionId
				+ "/enable", Map.of("version", disabledConversion.get("version").longValue()), admin));
		assertThat(enabledConversion.get("status").asText()).isEqualTo("ENABLED");
		assertAuditLog("UNIT_CONVERSION_ENABLE", "UNIT_CONVERSION", conversionId, "PUT",
				UNIT_CONVERSIONS + "/" + conversionId + "/enable");

		assertError(exchange(HttpMethod.POST, UNIT_CONVERSIONS,
				unitConversionPayload(materialId, boxUnitId, "-1.000000", 3, "HALF_UP", "2026-09-01", null),
				admin), HttpStatus.BAD_REQUEST, "UNIT_CONVERSION_RATE_INVALID");
		assertError(exchange(HttpMethod.POST, UNIT_CONVERSIONS,
				unitConversionPayload(materialId, boxUnitId, "1.000000", 3, "HALF_UP", "2026-09-10",
						"2026-09-01"),
				admin), HttpStatus.BAD_REQUEST, "UNIT_CONVERSION_DATE_RANGE_INVALID");
		assertError(exchange(HttpMethod.POST, UNIT_CONVERSIONS,
				unitConversionPayload(materialId, boxUnitId, "3.000000", 3, "HALF_UP", "2026-07-15", null),
				admin), HttpStatus.CONFLICT, "UNIT_CONVERSION_EFFECTIVE_OVERLAP");
		assertError(exchange(HttpMethod.POST, UNIT_CONVERSIONS,
				unitConversionPayload(materialId, baseUnitId, "1.000000", 3, "HALF_UP", "2026-08-01", null),
				admin), HttpStatus.CONFLICT, "UNIT_CONVERSION_REQUIRED");

		ResponseEntity<String> convert = exchange(HttpMethod.POST, UNIT_CONVERSIONS + "/convert",
				Map.of("materialId", materialId, "businessUnitId", boxUnitId, "businessQuantity", "2.000000",
						"businessDate", "2026-07-20"),
				admin);
		assertThat(convert.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(convert).get("conversionId").longValue()).isEqualTo(conversionId);
		assertThat(data(convert).get("baseQuantity").asText()).isEqualTo("5.000");

		ResponseEntity<String> rule = exchange(HttpMethod.POST, CODING_RULES,
				codingRulePayload("T21_MAT_RULE", "二十一物料编码", "MATERIAL", "MAT-", "NONE", 4, "NEVER", 1,
						"ENABLED"),
				admin);
		assertThat(rule.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(rule).get("version").isIntegralNumber()).isTrue();
		long ruleId = data(rule).get("id").longValue();
		assertAuditLog("CODING_RULE_CREATE", "CODING_RULE", ruleId, "POST", CODING_RULES);

		Map<String, Object> ruleUpdate = codingRulePayload("T21_MAT_RULE", "二十一物料编码更新", "MATERIAL",
				"MAT-", "NONE", 4, "NEVER", 1, "ENABLED");
		ruleUpdate.put("version", data(rule).get("version").longValue());
		JsonNode updatedRule = data(exchange(HttpMethod.PUT, CODING_RULES + "/" + ruleId, ruleUpdate, admin));
		assertAuditLog("CODING_RULE_UPDATE", "CODING_RULE", ruleId, "PUT", CODING_RULES + "/" + ruleId);
		assertError(exchange(HttpMethod.PUT, CODING_RULES + "/" + ruleId, ruleUpdate, admin),
				HttpStatus.CONFLICT, "VERSION_CONFLICT");

		assertError(exchange(HttpMethod.POST, CODING_RULES,
				codingRulePayload("T21_BAD_RULE", "二十一非法对象", "UNKNOWN", "BAD-", "NONE", 4, "NEVER", 1,
						"ENABLED"),
				admin), HttpStatus.BAD_REQUEST, "CODING_RULE_OBJECT_TYPE_INVALID");
		assertError(exchange(HttpMethod.POST, CODING_RULES,
				codingRulePayload("T21_MAT_RULE_DUP", "二十一物料重复规则", "MATERIAL", "DUP-", "NONE", 4,
						"NEVER", 1, "ENABLED"),
				admin), HttpStatus.CONFLICT, "CODING_RULE_DUPLICATE_ENABLED");

		ResponseEntity<String> generated = exchange(HttpMethod.POST, CODING_RULES + "/generate",
				Map.of("objectType", "MATERIAL"), admin);
		assertThat(generated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(generated).get("generatedCode").asText()).isEqualTo("MAT-0001");
		assertThat(data(generated).get("generatedAt").isTextual()).isTrue();
		JsonNode generatedRule = data(get(CODING_RULES + "/" + ruleId, admin));
		assertThat(generatedRule.get("lastGeneratedAt").asText()).isEqualTo(data(generated).get("generatedAt").asText());
		assertAuditLog("CODING_RULE_GENERATE", "CODING_RULE", ruleId, "POST", CODING_RULES + "/generate");
		assertAuditSummaryContains("CODING_RULE_GENERATE", "CODING_RULE", ruleId, "objectType=MATERIAL",
				"generatedCode=MAT-0001");

		JsonNode currentRule = data(get(CODING_RULES + "/" + ruleId, admin));
		JsonNode disabledRule = data(exchange(HttpMethod.PUT, CODING_RULES + "/" + ruleId + "/disable",
				Map.of("version", currentRule.get("version").longValue()), admin));
		assertAuditLog("CODING_RULE_DISABLE", "CODING_RULE", ruleId, "PUT", CODING_RULES + "/" + ruleId
				+ "/disable");
		assertError(exchange(HttpMethod.PUT, CODING_RULES + "/" + ruleId + "/disable",
				Map.of("version", currentRule.get("version").longValue()), admin), HttpStatus.CONFLICT,
				"VERSION_CONFLICT");
		assertError(exchange(HttpMethod.POST, CODING_RULES + "/generate", Map.of("objectType", "MATERIAL"), admin),
				HttpStatus.CONFLICT, "CODING_RULE_DISABLED");

		long conflictMaterialId = createMaterial(admin, "T21_GEN_0001", "二十一编码冲突物料", "RAW_MATERIAL",
				"PURCHASED", categoryId, baseUnitId);
		assertThat(conflictMaterialId).isPositive();
		JsonNode conflictRule = data(exchange(HttpMethod.POST, CODING_RULES,
				codingRulePayload("T21_GEN_CONFLICT", "二十一生成冲突", "MATERIAL", "T21_GEN_", "NONE", 4,
						"NEVER", 1, "ENABLED"),
				admin));
		assertError(exchange(HttpMethod.POST, CODING_RULES + "/generate", Map.of("objectType", "MATERIAL"), admin),
				HttpStatus.CONFLICT, "CODING_RULE_GENERATE_CONFLICT");
		JsonNode disabledConflictRule = data(exchange(HttpMethod.PUT, CODING_RULES + "/"
				+ conflictRule.get("id").longValue() + "/disable",
				Map.of("version", conflictRule.get("version").longValue()), admin));
		assertThat(disabledConflictRule.get("status").asText()).isEqualTo("DISABLED");

		JsonNode enabledRuleAgain = data(exchange(HttpMethod.PUT, CODING_RULES + "/" + ruleId + "/enable",
				Map.of("version", disabledRule.get("version").longValue()), admin));
		assertThat(enabledRuleAgain.get("status").asText()).isEqualTo("ENABLED");
		assertAuditLog("CODING_RULE_ENABLE", "CODING_RULE", ruleId, "PUT", CODING_RULES + "/" + ruleId
				+ "/enable");
		assertError(exchange(HttpMethod.PUT, CODING_RULES + "/" + ruleId + "/enable",
				Map.of("version", disabledRule.get("version").longValue()), admin), HttpStatus.CONFLICT,
				"VERSION_CONFLICT");
		JsonNode finalDisabledRule = data(exchange(HttpMethod.PUT, CODING_RULES + "/" + ruleId + "/disable",
				Map.of("version", enabledRuleAgain.get("version").longValue()), admin));
		assertThat(finalDisabledRule.get("status").asText()).isEqualTo("DISABLED");
	}

	@Test
	void costAttributesAndSettlementTaxProtectSensitiveFieldsAndBaseUnit() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long firstUnitId = createUnit(admin, "T21_COST_EA", "二十一成本个");
		long secondUnitId = createUnit(admin, "T21_COST_KG", "二十一成本千克");
		long categoryId = createCategory(admin, "T21_COST_CAT", "二十一成本分类");
		long materialId = createMaterial(admin, "T21_COST_MAT", "二十一成本物料", "RAW_MATERIAL", "PURCHASED",
				categoryId, firstUnitId);

		JsonNode material = data(get(MATERIALS + "/" + materialId, admin));
		assertThat(material.get("costCategory").asText()).isEqualTo("DIRECT_MATERIAL");
		assertThat(material.get("inventoryValuationCategory").asText()).isEqualTo("VALUATED_MATERIAL");
		assertThat(material.get("inventoryValueEnabled").booleanValue()).isTrue();
		assertThat(material.get("projectCostEnabled").booleanValue()).isTrue();
		assertThat(material.get("costAttributeCompleted").booleanValue()).isTrue();
		assertThat(material.get("baseUnitImmutableReason").isNull()).isTrue();
		assertThat(material.get("version").isIntegralNumber()).isTrue();

		long materialUpdateOnlyRoleId = createRole("T21_MATERIAL_UPDATE_ONLY", "二十一物料普通编辑角色", admin);
		assignPermissions(materialUpdateOnlyRoleId, List.of(permissionId("master:material:update")), admin);
		createUser("stage021-material-updater", List.of(materialUpdateOnlyRoleId), admin);
		AuthenticatedSession materialUpdater = login("stage021-material-updater", "Qherp@2026!");
		Map<String, Object> ordinaryUpdate = materialRequest("T21_COST_MAT", "二十一成本物料普通更新",
				"RAW_MATERIAL", "PURCHASED", categoryId, firstUnitId);
		removeCostFields(ordinaryUpdate);
		ordinaryUpdate.put("version", material.get("version").longValue());
		ResponseEntity<String> ordinaryUpdateResponse = exchange(HttpMethod.PUT, MATERIALS + "/" + materialId,
				ordinaryUpdate, materialUpdater);
		assertThat(ordinaryUpdateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		material = data(ordinaryUpdateResponse);

		Map<String, Object> forbiddenCostUpdate = materialRequest("T21_COST_MAT", "二十一成本物料越权更新",
				"RAW_MATERIAL", "PURCHASED", categoryId, firstUnitId);
		forbiddenCostUpdate.put("costRemark", "无成本权限不得写入");
		forbiddenCostUpdate.put("version", material.get("version").longValue());
		assertError(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId, forbiddenCostUpdate, materialUpdater),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		Map<String, Object> adminCostUpdate = materialRequest("T21_COST_MAT", "二十一成本物料成本更新",
				"RAW_MATERIAL", "PURCHASED", categoryId, firstUnitId);
		adminCostUpdate.put("costCategory", "AUXILIARY_MATERIAL");
		adminCostUpdate.put("version", material.get("version").longValue());
		material = data(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId, adminCostUpdate, admin));
		assertThat(material.get("costCategory").asText()).isEqualTo("AUXILIARY_MATERIAL");
		assertAuditLog("MATERIAL_COST_UPDATE", "MATERIAL", materialId, "PUT", MATERIALS + "/" + materialId);

		Map<String, Object> missingVersionUpdate = materialRequest("T21_COST_MAT", "二十一成本物料无版本",
				"RAW_MATERIAL", "PURCHASED", categoryId, firstUnitId);
		assertError(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId, missingVersionUpdate, admin),
				HttpStatus.CONFLICT, "VERSION_CONFLICT");
		Map<String, Object> incompleteEnabled = materialRequest("T21_COST_BAD", "二十一成本未分类", "RAW_MATERIAL",
				"PURCHASED", categoryId, firstUnitId);
		incompleteEnabled.put("costCategory", "UNCLASSIFIED");
		assertError(exchange(HttpMethod.POST, MATERIALS, incompleteEnabled, admin), HttpStatus.BAD_REQUEST,
				"MATERIAL_COST_ATTRIBUTE_INCOMPLETE");
		Map<String, Object> legacyValuation = materialRequest("T21_COST_LEGACY", "二十一旧枚举", "RAW_MATERIAL",
				"PURCHASED", categoryId, firstUnitId);
		legacyValuation.put("inventoryValuationCategory", "NON_VALUED_CONSUMABLE");
		assertError(exchange(HttpMethod.POST, MATERIALS, legacyValuation, admin), HttpStatus.BAD_REQUEST,
				"MATERIAL_COST_ATTRIBUTE_INCOMPLETE");
		Map<String, Object> disabledIncomplete = materialRequest("T21_COST_DISABLED", "二十一禁用未分类",
				"RAW_MATERIAL", "PURCHASED", categoryId, firstUnitId);
		disabledIncomplete.put("status", "DISABLED");
		disabledIncomplete.put("costCategory", "UNCLASSIFIED");
		JsonNode incompleteMaterial = data(exchange(HttpMethod.POST, MATERIALS, disabledIncomplete, admin));
		assertError(exchange(HttpMethod.PUT, MATERIALS + "/" + incompleteMaterial.get("id").longValue() + "/enable",
				Map.of("version", incompleteMaterial.get("version").longValue()), admin), HttpStatus.BAD_REQUEST,
				"MATERIAL_COST_ATTRIBUTE_INCOMPLETE");

		long warehouseId = createWarehouse(admin, "T21_COST_WH", "二十一成本仓");
		insertStockMovement(warehouseId, materialId, firstUnitId);
		Map<String, Object> unitChange = materialRequest("T21_COST_MAT", "二十一成本物料", "RAW_MATERIAL",
				"PURCHASED", categoryId, secondUnitId);
		unitChange.put("version", material.get("version").longValue());
		assertError(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId, unitChange, admin), HttpStatus.CONFLICT,
				"MATERIAL_BASE_UNIT_IMMUTABLE");
		assertThat(data(get(MATERIALS + "/" + materialId, admin)).get("baseUnitImmutableReason").asText())
			.isEqualTo("已有业务事实的物料基本单位被普通编辑改变。");

		long customerId = createPartner(admin, CUSTOMERS, "T21_CUST", "二十一客户");
		ResponseEntity<String> settlement = exchange(HttpMethod.PUT, CUSTOMERS + "/" + customerId + "/settlement-tax",
				settlementPayload("二十一客户开票", "91310000123456789X", "上海", "021-10000000", "中国银行",
						"6222000000000000", "0.130000", "SPECIAL_VAT", "MONTHLY", 30, "月结三十天", 0L),
				admin);
		assertThat(settlement.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(settlement).get("taxNo").asText()).isEqualTo("91310000123456789X");
		assertThat(data(settlement).get("bankAccount").asText()).isEqualTo("6222000000000000");
		assertThat(data(settlement).get("sensitiveRestricted").booleanValue()).isFalse();
		assertAuditLog("CUSTOMER_SETTLEMENT_TAX_UPDATE", "CUSTOMER_SETTLEMENT_TAX", customerId, "PUT",
				CUSTOMERS + "/" + customerId + "/settlement-tax");
		assertAuditSummaryExcludes("CUSTOMER_SETTLEMENT_TAX_UPDATE", "CUSTOMER_SETTLEMENT_TAX", customerId,
				"91310000123456789X", "6222000000000000");
		assertError(exchange(HttpMethod.PUT, CUSTOMERS + "/" + customerId + "/settlement-tax",
				Map.of("defaultTaxRate", "1.500000", "version", data(settlement).get("version").longValue()),
				admin), HttpStatus.BAD_REQUEST, "SETTLEMENT_TAX_FIELD_INVALID");
		assertError(exchange(HttpMethod.PUT, CUSTOMERS + "/" + customerId + "/settlement-tax",
				Map.of("paymentTermDays", -1, "version", data(settlement).get("version").longValue()), admin),
				HttpStatus.BAD_REQUEST, "SETTLEMENT_TAX_FIELD_INVALID");
		assertError(exchange(HttpMethod.PUT, CUSTOMERS + "/" + customerId + "/settlement-tax",
				Map.of("invoiceType", "BAD_TYPE", "version", data(settlement).get("version").longValue()), admin),
				HttpStatus.BAD_REQUEST, "SETTLEMENT_TAX_FIELD_INVALID");

		long roleId = createRole("T21_SETTLEMENT_VIEW", "二十一结算只读角色", admin);
		assignPermissions(roleId,
				List.of(permissionId("master:customer-settlement:view"),
						permissionId("master:customer-settlement:update"), permissionId("master:customer:view")),
				admin);
		createUser("stage021-settlement-viewer", List.of(roleId), admin);
		AuthenticatedSession viewer = login("stage021-settlement-viewer", "Qherp@2026!");
		JsonNode masked = data(get(CUSTOMERS + "/" + customerId + "/settlement-tax", viewer));
		assertThat(masked.get("ownerType").asText()).isEqualTo("CUSTOMER");
		assertThat(masked.get("taxNo").isNull()).isTrue();
		assertThat(masked.get("bankAccount").isNull()).isTrue();
		assertThat(masked.get("taxNoMasked").asText()).contains("789X");
		assertThat(masked.get("bankAccountMasked").asText()).contains("0000");
		assertThat(masked.get("sensitiveRestricted").booleanValue()).isTrue();
		assertThat(masked.get("restrictedMessage").asText()).isEqualTo("敏感资料受限");

		JsonNode customerListSummary = data(get(CUSTOMERS + "?keyword=T21_CUST", viewer))
			.get("items")
			.get(0)
			.get("settlementTaxSummary");
		assertThat(customerListSummary.get("hasData").booleanValue()).isTrue();
		assertThat(customerListSummary.get("sensitiveRestricted").booleanValue()).isTrue();
		assertThat(customerListSummary.get("taxNoMasked").asText()).contains("789X");
		assertThat(customerListSummary.get("bankAccountMasked").asText()).contains("0000");
		assertThat(customerListSummary.has("taxNo")).isFalse();
		assertThat(customerListSummary.has("bankAccount")).isFalse();

		assertError(exchange(HttpMethod.PUT, CUSTOMERS + "/" + customerId + "/settlement-tax",
				Map.of("taxNo", "91310000999999999X", "version", data(settlement).get("version").longValue()),
				viewer), HttpStatus.FORBIDDEN, "SETTLEMENT_TAX_SENSITIVE_FORBIDDEN");
		ResponseEntity<String> updatedSettlement = exchange(HttpMethod.PUT,
				CUSTOMERS + "/" + customerId + "/settlement-tax",
				settlementPayload("二十一客户开票更新", "91310000123456789X", "上海", "021-10000000", "中国银行",
						"6222000000000000", "0.130000", "SPECIAL_VAT", "MONTHLY", 45, "月结四十五天",
						data(settlement).get("version").longValue()),
				admin);
		assertThat(updatedSettlement.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertError(exchange(HttpMethod.PUT, CUSTOMERS + "/" + customerId + "/settlement-tax",
				settlementPayload("二十一客户开票过期", "91310000123456789X", "上海", "021-10000000", "中国银行",
						"6222000000000000", "0.130000", "SPECIAL_VAT", "MONTHLY", 30, "月结三十天",
						data(settlement).get("version").longValue()),
				admin), HttpStatus.CONFLICT, "SETTLEMENT_TAX_CONCURRENT_MODIFICATION");

		long supplierId = createPartner(admin, SUPPLIERS, "T21_SUP", "二十一供应商");
		ResponseEntity<String> supplierSettlement = exchange(HttpMethod.PUT,
				SUPPLIERS + "/" + supplierId + "/settlement-tax",
				settlementPayload("二十一供应商开票", "91310000987654321X", "苏州", "0512-10000000", "建设银行",
						"6227000000000000", "0.130000", "SPECIAL_VAT", "MONTHLY", 45, "月结四十五天", 0L),
				admin);
		assertThat(supplierSettlement.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(supplierSettlement).get("ownerType").asText()).isEqualTo("SUPPLIER");
		assertAuditLog("SUPPLIER_SETTLEMENT_TAX_UPDATE", "SUPPLIER_SETTLEMENT_TAX", supplierId, "PUT",
				SUPPLIERS + "/" + supplierId + "/settlement-tax");
		assertAuditSummaryExcludes("SUPPLIER_SETTLEMENT_TAX_UPDATE", "SUPPLIER_SETTLEMENT_TAX", supplierId,
				"91310000987654321X", "6227000000000000");
		JsonNode supplierListSummary = data(get(SUPPLIERS + "?keyword=T21_SUP", admin))
			.get("items")
			.get(0)
			.get("settlementTaxSummary");
		assertThat(supplierListSummary.get("hasData").booleanValue()).isTrue();
		assertThat(supplierListSummary.get("sensitiveRestricted").booleanValue()).isFalse();
		assertThat(supplierListSummary.get("taxNoMasked").asText()).contains("321X");
		assertThat(supplierListSummary.get("bankAccountMasked").asText()).contains("0000");
	}

	@Test
	void bomEcoSubstituteAndWorkOrderSnapshotsUseBaseQuantities() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long eachUnitId = createUnit(admin, "T21_BOM_EA", "二十一 BOM 个");
		long boxUnitId = createUnit(admin, "T21_BOM_BOX", "二十一 BOM 箱");
		long categoryId = createCategory(admin, "T21_BOM_CAT", "二十一 BOM 分类");
		long finishedId = createMaterial(admin, "T21_BOM_FG", "二十一成品", "FINISHED_GOOD", "SELF_MADE", categoryId,
				eachUnitId);
		long rawId = createMaterial(admin, "T21_BOM_RAW", "二十一原料", "RAW_MATERIAL", "PURCHASED", categoryId,
				eachUnitId);
		long substituteId = createMaterial(admin, "T21_BOM_SUB", "二十一替代料", "RAW_MATERIAL", "PURCHASED",
				categoryId, eachUnitId);
		createWarehouse(admin, "T21_BOM_ISSUE_WH", "二十一领料仓");
		long receiptWarehouseId = createWarehouse(admin, "T21_BOM_RECEIPT_WH", "二十一入库仓");
		long issueWarehouseId = warehouseId("T21_BOM_ISSUE_WH");
		long conversionId = data(exchange(HttpMethod.POST, UNIT_CONVERSIONS,
				unitConversionPayload(rawId, boxUnitId, "10.000000", 6, "HALF_UP", "2026-07-01", null), admin))
			.get("id")
			.longValue();

		ResponseEntity<String> sourceBomResponse = exchange(HttpMethod.POST, BOMS,
				bomRequest("T21_BOM_SRC", finishedId, "V1.0", "二十一来源 BOM", eachUnitId, "2026-07-01", null,
						List.of(bomItem(10, rawId, boxUnitId, "2.000000", "0.000000"))),
				admin);
		assertThat(sourceBomResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode sourceBom = data(sourceBomResponse);
		assertThat(sourceBom.get("baseQuantity").asText()).isEqualTo("1.000000");
		JsonNode sourceItem = sourceBom.get("items").get(0);
		assertThat(sourceItem.get("businessQuantity").asText()).isEqualTo("2.000000");
		assertThat(sourceItem.get("baseQuantity").asText()).isEqualTo("20.000000");
		assertThat(sourceItem.get("conversionId").longValue()).isEqualTo(conversionId);
		assertThat(sourceItem.get("quantityBasis").asText()).isEqualTo("CONVERTED_BUSINESS_UNIT");

		ResponseEntity<String> published = exchange(HttpMethod.PUT, BOMS + "/" + sourceBom.get("id").longValue()
				+ "/enable", Map.of("version", sourceBom.get("version").longValue()), admin);
		assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);

		JsonNode overlapping = data(exchange(HttpMethod.POST, BOMS,
				bomRequest("T21_BOM_OVERLAP", finishedId, "V1.1", "二十一重叠 BOM", eachUnitId, "2026-07-10", null,
						List.of(bomItem(10, rawId, boxUnitId, "3.000000", "0.000000"))),
				admin));
		assertError(exchange(HttpMethod.PUT, BOMS + "/" + overlapping.get("id").longValue() + "/enable",
				Map.of("version", overlapping.get("version").longValue()), admin), HttpStatus.CONFLICT,
				"BOM_EFFECTIVE_DATE_OVERLAP");

		JsonNode sourceCandidates = data(get(ECO + "/source-bom-candidates?keyword=T21_BOM_SRC&page=1&pageSize=1"
				+ "&selectedIds=" + overlapping.get("id").longValue(), admin));
		assertThat(sourceCandidates.get("items").get(0).get("id").longValue())
			.isEqualTo(sourceBom.get("id").longValue());
		assertThat(sourceCandidates.get("items").get(0).get("disabled").booleanValue()).isFalse();
		assertThat(containsId(sourceCandidates.get("selectedItems"), overlapping.get("id").longValue())).isTrue();
		assertThat(sourceCandidates.get("selectedItems").get(0).get("disabled").booleanValue()).isTrue();
		assertThat(sourceCandidates.get("selectedItems").get(0).get("disabledReason").asText())
			.isEqualTo("来源BOM必须已发布");

		JsonNode targetCandidates = data(get(ECO + "/target-bom-candidates?sourceBomId="
				+ sourceBom.get("id").longValue() + "&keyword=T21_BOM_OVERLAP&page=1&pageSize=1&selectedIds="
				+ sourceBom.get("id").longValue(), admin));
		assertThat(targetCandidates.get("items").get(0).get("id").longValue())
			.isEqualTo(overlapping.get("id").longValue());
		assertThat(targetCandidates.get("items").get(0).get("disabled").booleanValue()).isFalse();
		assertThat(containsId(targetCandidates.get("selectedItems"), sourceBom.get("id").longValue())).isTrue();
		assertThat(targetCandidates.get("selectedItems").get(0).get("disabled").booleanValue()).isTrue();
		assertThat(targetCandidates.get("selectedItems").get(0).get("disabledReason").asText())
			.isEqualTo("目标BOM必须为草稿");

		long workOrderId = createWorkOrder(admin, finishedId, sourceBom.get("id").longValue(), issueWarehouseId,
				receiptWarehouseId);
		seedStock(issueWarehouseId, rawId, eachUnitId, "20.000000");
		ResponseEntity<String> release = exchange(HttpMethod.PUT, PRODUCTION + "/work-orders/" + workOrderId
				+ "/release", Map.of(), admin);
		assertThat(release.getStatusCode()).as(release.getBody()).isEqualTo(HttpStatus.OK);
		JsonNode releasedMaterial = data(release).get("materials").get(0);
		assertThat(releasedMaterial.get("businessUnitId").longValue()).isEqualTo(boxUnitId);
		assertThat(releasedMaterial.get("businessQuantity").asText()).isEqualTo("2.000000");
		assertThat(releasedMaterial.get("baseUnitId").longValue()).isEqualTo(eachUnitId);
		assertThat(releasedMaterial.get("baseRequiredQuantity").asText()).isEqualTo("20.000000");

		ResponseEntity<String> targetBomResponse = exchange(HttpMethod.POST, BOMS,
				bomRequest("T21_BOM_TARGET", finishedId, "V2.0", "二十一目标 BOM", eachUnitId, "2026-08-01", null,
						List.of(bomItem(10, rawId, boxUnitId, "4.000000", "0.000000"))),
				admin);
		JsonNode targetBom = data(targetBomResponse);
		assertError(exchange(HttpMethod.PUT, BOMS + "/" + targetBom.get("id").longValue(),
				bomRequest("T21_BOM_TARGET", finishedId, "V2.0", "二十一目标 BOM 无版本", eachUnitId,
						"2026-08-01", null, List.of(bomItem(10, rawId, boxUnitId, "4.000000", "0.000000"))),
				admin), HttpStatus.CONFLICT, "VERSION_CONFLICT");
		JsonNode ecoRule = createCodingRule(admin, "T21_ECO_RULE", "BOM_ECO", "ECO-", 1);
		JsonNode generatedEcoCode = data(exchange(HttpMethod.POST, CODING_RULES + "/generate",
				Map.of("objectType", "BOM_ECO"), admin));
		JsonNode eco = data(exchange(HttpMethod.POST, ECO,
				Map.of("ecoNo", generatedEcoCode.get("generatedCode").asText(), "sourceBomId",
						sourceBom.get("id").longValue(), "targetBomId", targetBom.get("id").longValue(),
						"effectiveFrom", "2026-08-01", "changeReason", "材料定额调整", "impactScope", "后续新工单",
						"changeSummary", "二十一 ECO 应用"),
				admin));
		assertThat(eco.get("ecoNo").asText()).isEqualTo(generatedEcoCode.get("generatedCode").asText());
		assertThat(data(get(CODING_RULES + "/" + ecoRule.get("id").longValue(), admin)).get("nextSerialNo").longValue())
			.isEqualTo(2);
		assertThat(eco.get("sourceVersionCode").asText()).isEqualTo("V1.0");
		assertThat(eco.get("targetVersionCode").asText()).isEqualTo("V2.0");
		assertAuditLog("BOM_ECO_CREATE", "BOM_ENGINEERING_CHANGE", eco.get("id").longValue(), "POST", ECO);
		assertError(exchange(HttpMethod.POST, ECO,
				Map.of("sourceBomId", sourceBom.get("id").longValue(), "targetBomId", targetBom.get("id").longValue(),
						"effectiveFrom", "2026-08-01", "changeReason", "缺少编号", "impactScope", "不应用",
						"changeSummary", "二十一 ECO 缺少编号"),
				admin), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		assertError(exchange(HttpMethod.POST, ECO,
				Map.of("ecoNo", generatedEcoCode.get("generatedCode").asText(), "sourceBomId",
						sourceBom.get("id").longValue(), "targetBomId", targetBom.get("id").longValue(),
						"effectiveFrom", "2026-08-01", "changeReason", "重复编号", "impactScope", "不应用",
						"changeSummary", "二十一 ECO 重复编号"),
				admin), HttpStatus.CONFLICT, "CODING_RULE_GENERATE_CONFLICT");
		JsonNode filteredEco = data(get(ECO + "?sourceBomId=" + sourceBom.get("id").longValue()
				+ "&targetBomId=" + targetBom.get("id").longValue() + "&page=1&pageSize=10", admin));
		assertThat(filteredEco.get("total").longValue()).isEqualTo(1);
		assertThat(filteredEco.get("items").get(0).get("id").longValue()).isEqualTo(eco.get("id").longValue());
		assertThat(filteredEco.get("items").get(0).get("sourceVersionCode").asText()).isEqualTo("V1.0");
		assertThat(filteredEco.get("items").get(0).get("targetVersionCode").asText()).isEqualTo("V2.0");

		JsonNode cancelTargetBom = data(exchange(HttpMethod.POST, BOMS,
				bomRequest("T21_BOM_CANCEL", finishedId, "V2.1", "二十一取消目标 BOM", eachUnitId, "2026-09-01",
						null, List.of(bomItem(10, rawId, boxUnitId, "5.000000", "0.000000"))),
				admin));
		JsonNode cancelEcoCode = data(exchange(HttpMethod.POST, CODING_RULES + "/generate",
				Map.of("objectType", "BOM_ECO"), admin));
		JsonNode cancelEco = data(exchange(HttpMethod.POST, ECO,
				Map.of("ecoNo", cancelEcoCode.get("generatedCode").asText(), "sourceBomId", sourceBom.get("id").longValue(),
						"targetBomId", cancelTargetBom.get("id").longValue(), "effectiveFrom", "2026-09-01",
						"changeReason", "取消验证", "impactScope", "不应用", "changeSummary", "二十一 ECO 取消"),
				admin));
		JsonNode cancelledEco = data(exchange(HttpMethod.PUT, ECO + "/" + cancelEco.get("id").longValue()
				+ "/cancel", Map.of("version", cancelEco.get("version").longValue(), "reason", "撤回验证"), admin));
		assertThat(cancelledEco.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(cancelledEco.get("cancelReason").asText()).isEqualTo("撤回验证");
		assertAuditLog("BOM_ECO_CANCEL", "BOM_ENGINEERING_CHANGE", cancelEco.get("id").longValue(), "PUT",
				ECO + "/" + cancelEco.get("id").longValue() + "/cancel");
		assertError(exchange(HttpMethod.PUT, ECO + "/" + cancelEco.get("id").longValue() + "/apply",
				Map.of("version", cancelledEco.get("version").longValue()), admin), HttpStatus.CONFLICT,
				"BOM_ENGINEERING_CHANGE_STATUS_INVALID");
		JsonNode invalidEcoCode = data(exchange(HttpMethod.POST, CODING_RULES + "/generate",
				Map.of("objectType", "BOM_ECO"), admin));
		assertError(exchange(HttpMethod.POST, ECO,
				Map.of("ecoNo", invalidEcoCode.get("generatedCode").asText(), "sourceBomId", sourceBom.get("id").longValue(),
						"targetBomId", sourceBom.get("id").longValue(), "effectiveFrom", "2026-08-01",
						"changeReason", "目标非法", "impactScope", "不应用", "changeSummary", "二十一 ECO 非法目标"),
				admin), HttpStatus.BAD_REQUEST, "BOM_ENGINEERING_CHANGE_TARGET_INVALID");
		assertError(exchange(HttpMethod.PUT, ECO + "/" + eco.get("id").longValue() + "/apply",
				Map.of("version", eco.get("version").longValue() + 99), admin), HttpStatus.CONFLICT,
				"BOM_ENGINEERING_CHANGE_CONCURRENT_MODIFICATION");
		ResponseEntity<String> applied = exchange(HttpMethod.PUT, ECO + "/" + eco.get("id").longValue() + "/apply",
				Map.of("version", eco.get("version").longValue()), admin);
		assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(applied).get("sourceBomAfter").get("effectiveTo").asText()).isEqualTo("2026-07-31");
		assertThat(data(applied).get("targetBomAfter").get("status").asText()).isEqualTo("ENABLED");
		assertAuditLog("BOM_ECO_APPLY", "BOM_ENGINEERING_CHANGE", eco.get("id").longValue(), "PUT",
				ECO + "/" + eco.get("id").longValue() + "/apply");
		JsonNode sourceHistory = data(get(BOMS + "/" + sourceBom.get("id").longValue(), admin)).get("historyRelations");
		assertThat(sourceHistory).hasSize(2);
		JsonNode appliedSourceRelation = findRelation(sourceHistory, eco.get("id").longValue(), "SOURCE");
		assertThat(appliedSourceRelation.get("ecoNo").asText())
			.isEqualTo(generatedEcoCode.get("generatedCode").asText());
		assertThat(appliedSourceRelation.get("sourceVersionCode").asText()).isEqualTo("V1.0");
		assertThat(appliedSourceRelation.get("targetVersionCode").asText()).isEqualTo("V2.0");
		assertThat(appliedSourceRelation.get("status").asText()).isEqualTo("APPLIED");
		JsonNode targetHistory = data(get(BOMS + "/" + targetBom.get("id").longValue(), admin)).get("historyRelations");
		assertThat(targetHistory).hasSize(1);
		assertThat(targetHistory.get(0).get("relationType").asText()).isEqualTo("TARGET");

		ResponseEntity<String> substitute = exchange(HttpMethod.POST, SUBSTITUTES,
				Map.of("mainMaterialId", rawId, "substituteMaterialId", substituteId, "scopeType", "BOM",
						"scopeId", sourceBom.get("id").longValue(), "priority", 1, "substituteRate", "1.000000",
						"effectiveFrom", "2026-07-01", "status", "ENABLED"),
				admin);
		assertThat(substitute.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode substituteData = data(substitute);
		assertThat(substituteData.get("substituteRate").isTextual()).isTrue();
		assertThat(substituteData.get("substituteRate").asText()).isEqualTo("1.000000");
		assertAuditLog("MATERIAL_SUBSTITUTE_CREATE", "MATERIAL_SUBSTITUTE", substituteData.get("id").longValue(),
				"POST", SUBSTITUTES);
		JsonNode substituteFilteredOut = data(get(SUBSTITUTES + "?substituteMaterialId=" + rawId
				+ "&scopeType=BOM&scopeId=" + sourceBom.get("id").longValue() + "&effectiveDate=2026-07-10", admin));
		assertThat(substituteFilteredOut.get("total").longValue()).isZero();
		JsonNode substituteFilteredIn = data(get(SUBSTITUTES + "?mainMaterialId=" + rawId
				+ "&substituteMaterialId=" + substituteId + "&scopeType=BOM&scopeId="
				+ sourceBom.get("id").longValue() + "&effectiveDate=2026-07-10", admin));
		assertThat(substituteFilteredIn.get("total").longValue()).isEqualTo(1);
		assertThat(substituteFilteredIn.get("items").get(0).get("id").longValue())
			.isEqualTo(substituteData.get("id").longValue());
		assertError(exchange(HttpMethod.POST, SUBSTITUTES,
				Map.of("mainMaterialId", rawId, "substituteMaterialId", rawId, "scopeType", "BOM",
						"scopeId", sourceBom.get("id").longValue(), "priority", 2, "substituteRate", "1.000000",
						"effectiveFrom", "2026-07-01", "status", "ENABLED"),
				admin), HttpStatus.BAD_REQUEST, "MATERIAL_SUBSTITUTE_SELF_REFERENCE");
		assertError(exchange(HttpMethod.POST, SUBSTITUTES,
				Map.of("mainMaterialId", rawId, "substituteMaterialId", substituteId, "scopeType", "BOM",
						"scopeId", sourceBom.get("id").longValue(), "priority", 2, "substituteRate", "0.000000",
						"effectiveFrom", "2026-07-01", "status", "ENABLED"),
				admin), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		assertError(exchange(HttpMethod.POST, SUBSTITUTES,
				Map.of("mainMaterialId", rawId, "substituteMaterialId", substituteId, "scopeType", "BOM",
						"scopeId", sourceBom.get("id").longValue(), "priority", 2, "substituteRate", "1.000000",
						"effectiveFrom", "2026-08-01", "effectiveTo", "2026-07-01", "status", "ENABLED"),
				admin), HttpStatus.CONFLICT, "MATERIAL_SUBSTITUTE_EFFECTIVE_OVERLAP");
		assertError(exchange(HttpMethod.PUT, SUBSTITUTES + "/" + substituteData.get("id").longValue(),
				Map.of("mainMaterialId", rawId, "substituteMaterialId", substituteId, "scopeType", "BOM",
						"scopeId", sourceBom.get("id").longValue(), "priority", 1, "substituteRate", "1.000000",
						"effectiveFrom", "2026-07-01", "status", "ENABLED"),
				admin), HttpStatus.CONFLICT, "MATERIAL_SUBSTITUTE_CONCURRENT_MODIFICATION");
		JsonNode updatedSubstitute = data(exchange(HttpMethod.PUT, SUBSTITUTES + "/"
				+ substituteData.get("id").longValue(),
				Map.of("mainMaterialId", rawId, "substituteMaterialId", substituteId, "scopeType", "BOM",
						"scopeId", sourceBom.get("id").longValue(), "priority", 1, "substituteRate", "1.000000",
						"effectiveFrom", "2026-07-01", "status", "ENABLED", "remark", "替代料更新",
						"version", substituteData.get("version").longValue()),
				admin));
		assertAuditLog("MATERIAL_SUBSTITUTE_UPDATE", "MATERIAL_SUBSTITUTE", substituteData.get("id").longValue(),
				"PUT", SUBSTITUTES + "/" + substituteData.get("id").longValue());
		JsonNode disabledSubstitute = data(exchange(HttpMethod.PUT, SUBSTITUTES + "/"
				+ substituteData.get("id").longValue() + "/disable",
				Map.of("version", updatedSubstitute.get("version").longValue()), admin));
		assertAuditLog("MATERIAL_SUBSTITUTE_DISABLE", "MATERIAL_SUBSTITUTE", substituteData.get("id").longValue(),
				"PUT", SUBSTITUTES + "/" + substituteData.get("id").longValue() + "/disable");
		JsonNode enabledSubstitute = data(exchange(HttpMethod.PUT, SUBSTITUTES + "/"
				+ substituteData.get("id").longValue() + "/enable",
				Map.of("version", disabledSubstitute.get("version").longValue()), admin));
		assertThat(enabledSubstitute.get("status").asText()).isEqualTo("ENABLED");
		assertAuditLog("MATERIAL_SUBSTITUTE_ENABLE", "MATERIAL_SUBSTITUTE", substituteData.get("id").longValue(),
				"PUT", SUBSTITUTES + "/" + substituteData.get("id").longValue() + "/enable");
		JsonNode substituteBomCandidates = data(get(SUBSTITUTES + "/bom-candidates?parentMaterialId=" + finishedId
				+ "&page=1&pageSize=1&selectedIds=" + overlapping.get("id").longValue(), admin));
		assertThat(substituteBomCandidates.get("items").get(0).has("disabledReason")).isTrue();
		assertThat(containsId(substituteBomCandidates.get("selectedItems"), overlapping.get("id").longValue()))
			.isTrue();
		assertError(exchange(HttpMethod.POST, SUBSTITUTES,
				Map.of("mainMaterialId", rawId, "substituteMaterialId", substituteId, "scopeType", "BOM",
						"scopeId", sourceBom.get("id").longValue(), "priority", 1, "substituteRate", "1.000000",
						"effectiveFrom", "2026-07-15", "status", "ENABLED"),
				admin), HttpStatus.CONFLICT, "MATERIAL_SUBSTITUTE_PRIORITY_CONFLICT");

		JsonNode unchangedWorkOrder = data(get(PRODUCTION + "/work-orders/" + workOrderId, admin));
		assertThat(unchangedWorkOrder.get("materials").get(0).get("baseRequiredQuantity").asText())
			.isEqualTo("20.000000");
	}

	private long createUnit(AuthenticatedSession admin, String code, String name) throws Exception {
		return createUnitWithStatus(admin, code, name, "ENABLED");
	}

	private long createUnitWithStatus(AuthenticatedSession admin, String code, String name, String status)
			throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, UNITS,
				Map.of("code", code, "name", name, "precisionScale", 6, "sortOrder", 10, "status", status), admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createWarehouse(AuthenticatedSession admin, String code, String name) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, WAREHOUSES,
				Map.of("code", code, "name", name, "warehouseType", "NORMAL", "status", "ENABLED"), admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createCategory(AuthenticatedSession admin, String code, String name) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, CATEGORIES,
				Map.of("code", code, "name", name, "status", "ENABLED", "sortOrder", 10), admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createMaterial(AuthenticatedSession admin, String code, String name, String materialType,
			String sourceType, long categoryId, long unitId) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, MATERIALS,
				materialRequest(code, name, materialType, sourceType, categoryId, unitId), admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createPartner(AuthenticatedSession admin, String path, String code, String name) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, path,
				Map.of("code", code, "name", name, "contactName", "联系人", "contactPhone", "13800000000", "status",
						"ENABLED"),
				admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createWorkOrder(AuthenticatedSession admin, long productMaterialId, long bomId, long issueWarehouseId,
			long receiptWarehouseId) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, PRODUCTION + "/work-orders",
				Map.of("productMaterialId", productMaterialId, "bomId", bomId, "plannedQuantity", "1.000000",
						"issueWarehouseId", issueWarehouseId, "receiptWarehouseId", receiptWarehouseId,
						"plannedStartDate", "2026-07-20", "plannedFinishDate", "2026-07-25"),
				admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private JsonNode createCodingRule(AuthenticatedSession admin, String code, String objectType, String prefix,
			int nextSerialNo) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, CODING_RULES,
				codingRulePayload(code, code, objectType, prefix, "NONE", 4, "NEVER", nextSerialNo, "ENABLED"),
				admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response);
	}

	private Map<String, Object> materialRequest(String code, String name, String materialType, String sourceType,
			long categoryId, long unitId) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("code", code);
		request.put("name", name);
		request.put("specification", "S");
		request.put("materialType", materialType);
		request.put("sourceType", sourceType);
		request.put("categoryId", categoryId);
		request.put("unitId", unitId);
		request.put("status", "ENABLED");
		request.put("costCategory", costCategoryFor(materialType));
		request.put("inventoryValuationCategory", "SERVICE".equals(materialType) ? "SERVICE_NON_STOCK"
				: "VALUATED_MATERIAL");
		request.put("inventoryValueEnabled", !"SERVICE".equals(materialType));
		request.put("projectCostEnabled", !"SERVICE".equals(materialType));
		request.put("costRemark", "二十一成本属性");
		return request;
	}

	private String costCategoryFor(String materialType) {
		return switch (materialType) {
			case "FINISHED_GOOD" -> "FINISHED_GOOD";
			case "SEMI_FINISHED" -> "SEMI_FINISHED";
			case "AUXILIARY" -> "AUXILIARY_MATERIAL";
			case "SERVICE" -> "SERVICE";
			default -> "DIRECT_MATERIAL";
		};
	}

	private Map<String, Object> unitConversionPayload(long materialId, long businessUnitId, String rate, int scale,
			String roundingMode, String effectiveFrom, String effectiveTo) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("materialId", materialId);
		request.put("businessUnitId", businessUnitId);
		request.put("conversionRate", rate);
		request.put("quantityScale", scale);
		request.put("roundingMode", roundingMode);
		request.put("effectiveFrom", effectiveFrom);
		request.put("effectiveTo", effectiveTo);
		request.put("remark", "二十一单位换算");
		return request;
	}

	private Map<String, Object> codingRulePayload(String ruleCode, String name, String objectType, String prefix,
			String datePattern, int serialLength, String resetCycle, int nextSerialNo, String status) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("ruleCode", ruleCode);
		request.put("name", name);
		request.put("objectType", objectType);
		request.put("prefix", prefix);
		request.put("datePattern", datePattern);
		request.put("serialLength", serialLength);
		request.put("resetCycle", resetCycle);
		request.put("nextSerialNo", nextSerialNo);
		request.put("status", status);
		return request;
	}

	private Map<String, Object> settlementPayload(String invoiceTitle, String taxNo, String registeredAddress,
			String registeredPhone, String bankName, String bankAccount, String defaultTaxRate, String invoiceType,
			String settlementMethod, int paymentTermDays, String paymentTerms, long version) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("invoiceTitle", invoiceTitle);
		request.put("taxNo", taxNo);
		request.put("registeredAddress", registeredAddress);
		request.put("registeredPhone", registeredPhone);
		request.put("bankName", bankName);
		request.put("bankAccount", bankAccount);
		request.put("defaultTaxRate", defaultTaxRate);
		request.put("invoiceType", invoiceType);
		request.put("settlementMethod", settlementMethod);
		request.put("paymentTermDays", paymentTermDays);
		request.put("paymentTerms", paymentTerms);
		request.put("remark", "二十一结算税务");
		request.put("version", version);
		return request;
	}

	private Map<String, Object> bomRequest(String bomCode, long parentMaterialId, String versionCode, String name,
			long baseUnitId, String effectiveFrom, String effectiveTo, List<Map<String, Object>> items) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("bomCode", bomCode);
		request.put("parentMaterialId", parentMaterialId);
		request.put("versionCode", versionCode);
		request.put("name", name);
		request.put("baseQuantity", "1.000000");
		request.put("baseUnitId", baseUnitId);
		request.put("effectiveFrom", effectiveFrom);
		request.put("effectiveTo", effectiveTo);
		request.put("items", items);
		return request;
	}

	private Map<String, Object> bomItem(int lineNo, long childMaterialId, long businessUnitId,
			String businessQuantity, String lossRate) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("lineNo", lineNo);
		item.put("childMaterialId", childMaterialId);
		item.put("businessUnitId", businessUnitId);
		item.put("businessQuantity", businessQuantity);
		item.put("lossRate", lossRate);
		return item;
	}

	private void insertStockMovement(long warehouseId, long materialId, long unitId) {
		this.jdbcTemplate.update("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason,
					remark, operator_name, occurred_at, quality_status
				)
				values (?, 'ADJUSTMENT_INCREASE', 'IN', ?, ?, ?, ?, 0, ?, 'STAGE021_BASE_UNIT_TEST', ?, ?,
					current_date, '基本单位保护', null, 'tester', now(), 'QUALIFIED')
				""", "MV-T21-" + materialId, warehouseId, materialId, unitId, new BigDecimal("1.000000"),
				new BigDecimal("1.000000"), materialId, materialId);
	}

	private void seedStock(long warehouseId, long materialId, long unitId, String quantity) {
		int updated = this.jdbcTemplate.update("""
				update inv_stock_balance
				set unit_id = ?, quantity_on_hand = ?, updated_at = now(), version = version + 1
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and batch_id is null
				and serial_id is null
				""", unitId, new BigDecimal(quantity), warehouseId, materialId);
		if (updated == 0) {
			this.jdbcTemplate.update("""
					insert into inv_stock_balance (
						warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at, updated_at,
						quality_status
					)
					values (?, ?, ?, ?, 0, now(), now(), 'QUALIFIED')
					""", warehouseId, materialId, unitId, new BigDecimal(quantity));
		}
	}

	private long warehouseId(String code) {
		return this.jdbcTemplate.queryForObject("select id from mst_warehouse where code = ?", Long.class, code);
	}

	private void createUser(String username, List<Long> roleIds, AuthenticatedSession session) {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", username, "displayName", username, "initialPassword", "Qherp@2026!", "status",
						"ENABLED", "roleIds", roleIds),
				session);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private long createRole(String code, String name, AuthenticatedSession session) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/roles",
				Map.of("code", code, "name", name, "description", "二十一测试角色", "status", "ENABLED"), session);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private void assignPermissions(long roleId, List<Long> permissionIds, AuthenticatedSession session) {
		ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/permissions",
				Map.of("permissionIds", permissionIds), session);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private long permissionId(String code) {
		return this.jdbcTemplate.queryForObject("select id from sys_permission where code = ?", Long.class, code);
	}

	private void removeCostFields(Map<String, Object> request) {
		request.remove("costCategory");
		request.remove("inventoryValuationCategory");
		request.remove("inventoryValueEnabled");
		request.remove("projectCostEnabled");
		request.remove("costRemark");
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(response.getBody()).contains("\"code\":\"" + code + "\"");
	}

	private void assertAuditLog(String action, String targetType, long targetId, String requestMethod,
			String requestPath) {
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				and target_type = ?
				and target_id = ?
				and request_method = ?
				and request_path = ?
				""", Long.class, action, targetType, Long.toString(targetId), requestMethod, requestPath)).as(action)
			.isOne();
	}

	private void assertAuditSummaryContains(String action, String targetType, long targetId, String... fragments) {
		String summary = auditSummary(action, targetType, targetId);
		for (String fragment : fragments) {
			assertThat(summary).contains(fragment);
		}
	}

	private void assertAuditSummaryExcludes(String action, String targetType, long targetId, String... fragments) {
		String summary = auditSummary(action, targetType, targetId);
		for (String fragment : fragments) {
			assertThat(summary).doesNotContain(fragment);
		}
	}

	private String auditSummary(String action, String targetType, long targetId) {
		return this.jdbcTemplate.queryForObject("""
				select target_summary
				from sys_audit_log
				where action = ?
				and target_type = ?
				and target_id = ?
				order by id desc
				limit 1
				""", String.class, action, targetType, Long.toString(targetId));
	}

	private AuthenticatedSession login(String username, String password) {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrf);
	}

	private CsrfSession csrfSession() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		try {
			JsonNode data = data(response);
			return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null), String.class);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()),
				String.class);
	}

	private HttpEntity<Object> entity(Object body, String cookie, CsrfSession csrf) {
		HttpHeaders headers = new HttpHeaders();
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		if (csrf != null) {
			headers.add(csrf.headerName(), csrf.token());
		}
		return new HttpEntity<>(body, headers);
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private boolean containsId(JsonNode items, long id) {
		for (JsonNode item : items) {
			if (item.get("id").longValue() == id) {
				return true;
			}
		}
		return false;
	}

	private JsonNode findRelation(JsonNode items, long ecoId, String relationType) {
		for (JsonNode item : items) {
			if (item.get("ecoId").longValue() == ecoId && relationType.equals(item.get("relationType").asText())) {
				return item;
			}
		}
		throw new AssertionError("未找到工程变更关系 " + ecoId + " / " + relationType);
	}

	private String sessionCookie(ResponseEntity<String> response) {
		return response.getHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE)
			.stream()
			.filter((cookie) -> cookie.startsWith("JSESSIONID="))
			.findFirst()
			.map((cookie) -> cookie.split(";", 2)[0])
			.orElseThrow();
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}

}
