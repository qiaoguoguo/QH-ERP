package com.qherp.api.system.master;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=task4-material-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MaterialAdminControllerTests extends PostgresIntegrationTest {

	private static final String UNITS = "/api/admin/master/units";

	private static final String CATEGORIES = "/api/admin/master/material-categories";

	private static final String MATERIALS = "/api/admin/master/materials";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void adminCanManageCategoriesAndMaterials() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T4_LIFE_UNIT", "任务四生命周期单位");
		long categoryId = createCategory(admin, "T4_LIFE_CAT", "任务四生命周期分类", null, "ENABLED", 10);
		assertAuditLog("CATEGORY_CREATE", "MATERIAL_CATEGORY", categoryId, "T4_LIFE_CAT", "POST", CATEGORIES);

		ResponseEntity<String> categoryList = get(CATEGORIES + "?keyword=T4_LIFE_CAT&page=1&pageSize=20", admin);
		assertThat(categoryList.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(categoryList).get("total").longValue()).isEqualTo(1);

		ResponseEntity<String> categoryUpdate = exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId,
				categoryRequest("T4_LIFE_CAT_UPD", "任务四生命周期分类改", null, "ENABLED", 20, "更新分类"), admin);
		assertThat(categoryUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(categoryUpdate).get("code").asText()).isEqualTo("T4_LIFE_CAT_UPD");
		assertAuditLog("CATEGORY_UPDATE", "MATERIAL_CATEGORY", categoryId, "T4_LIFE_CAT_UPD", "PUT",
				CATEGORIES + "/" + categoryId);

		assertThat(exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId + "/disable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(get(CATEGORIES + "/" + categoryId, admin)).get("status").asText()).isEqualTo("DISABLED");
		assertAuditLog("CATEGORY_DISABLE", "MATERIAL_CATEGORY", categoryId, "T4_LIFE_CAT_UPD", "PUT",
				CATEGORIES + "/" + categoryId + "/disable");

		assertThat(exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId + "/enable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(get(CATEGORIES + "/" + categoryId, admin)).get("status").asText()).isEqualTo("ENABLED");
		assertAuditLog("CATEGORY_ENABLE", "MATERIAL_CATEGORY", categoryId, "T4_LIFE_CAT_UPD", "PUT",
				CATEGORIES + "/" + categoryId + "/enable");

		ResponseEntity<String> materialCreate = exchange(HttpMethod.POST, MATERIALS,
				materialRequest("T4_LIFE_MAT", "任务四生命周期物料", "S-01", "RAW_MATERIAL", "PURCHASED", categoryId,
						unitId, "ENABLED", "创建物料"),
				admin);
		assertThat(materialCreate.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode material = data(materialCreate);
		long materialId = material.get("id").longValue();
		assertThat(material.get("categoryName").asText()).isEqualTo("任务四生命周期分类改");
		assertThat(material.get("unitName").asText()).isEqualTo("任务四生命周期单位");
		assertAuditLog("MATERIAL_CREATE", "MATERIAL", materialId, "T4_LIFE_MAT", "POST", MATERIALS);

		ResponseEntity<String> materialList = get(MATERIALS + "?keyword=T4_LIFE_MAT&page=1&pageSize=20", admin);
		assertThat(materialList.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode page = data(materialList);
		assertThat(page.get("items").isArray()).isTrue();
		assertThat(page.get("page").intValue()).isEqualTo(1);
		assertThat(page.get("pageSize").intValue()).isEqualTo(20);
		assertThat(page.get("total").longValue()).isEqualTo(1);
		assertThat(page.get("totalPages").intValue()).isEqualTo(1);
		assertThat(materialList.getBody()).contains("\"code\":\"T4_LIFE_MAT\"");

		ResponseEntity<String> materialDetail = get(MATERIALS + "/" + materialId, admin);
		assertThat(materialDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode detail = data(materialDetail);
		assertThat(detail.get("categoryName").asText()).isEqualTo("任务四生命周期分类改");
		assertThat(detail.get("unitName").asText()).isEqualTo("任务四生命周期单位");

		Map<String, Object> missingCodeUpdate = materialRequest("T4_LIFE_MAT", "任务四生命周期物料改", "S-02",
				"SEMI_FINISHED", "SELF_MADE", categoryId, unitId, "ENABLED", "缺少编码");
		missingCodeUpdate.remove("code");
		assertError(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId, missingCodeUpdate, admin),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

		ResponseEntity<String> materialUpdate = exchange(HttpMethod.PUT, MATERIALS + "/" + materialId,
				materialRequest("T4_LIFE_MAT_UPD", "任务四生命周期物料改", "S-02", "SEMI_FINISHED", "SELF_MADE",
						categoryId, unitId, "ENABLED", "更新物料"),
				admin);
		assertThat(materialUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(materialUpdate).get("code").asText()).isEqualTo("T4_LIFE_MAT_UPD");
		assertAuditLog("MATERIAL_UPDATE", "MATERIAL", materialId, "T4_LIFE_MAT_UPD", "PUT",
				MATERIALS + "/" + materialId);

		assertThat(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId + "/disable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(get(MATERIALS + "/" + materialId, admin)).get("status").asText()).isEqualTo("DISABLED");
		assertAuditLog("MATERIAL_DISABLE", "MATERIAL", materialId, "T4_LIFE_MAT_UPD", "PUT",
				MATERIALS + "/" + materialId + "/disable");

		assertThat(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId + "/enable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(get(MATERIALS + "/" + materialId, admin)).get("status").asText()).isEqualTo("ENABLED");
		assertAuditLog("MATERIAL_ENABLE", "MATERIAL", materialId, "T4_LIFE_MAT_UPD", "PUT",
				MATERIALS + "/" + materialId + "/enable");
	}

	@Test
	void materialReferencesMustPointToEnabledUnitAndCategoryOnCreateAndUpdate() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long activeUnitId = createUnit(admin, "T4_REF_UNIT_ACTIVE", "引用有效单位");
		long disabledUnitId = createUnit(admin, "T4_REF_UNIT_DISABLED", "引用停用单位");
		long activeCategoryId = createCategory(admin, "T4_REF_CAT_ACTIVE", "引用有效分类", null, "ENABLED", 10);
		long disabledCategoryId = createCategory(admin, "T4_REF_CAT_DISABLED", "引用停用分类", null, "DISABLED", 20);
		assertThat(exchange(HttpMethod.PUT, UNITS + "/" + disabledUnitId + "/disable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);

		assertError(exchange(HttpMethod.POST, MATERIALS,
				materialRequest("T4_REF_DISABLED_UNIT", "引用停用单位物料", "S", "RAW_MATERIAL", "PURCHASED",
						activeCategoryId, disabledUnitId, "ENABLED", null),
				admin), HttpStatus.BAD_REQUEST, "MASTER_DATA_REFERENCE_INVALID");
		assertError(exchange(HttpMethod.POST, MATERIALS,
				materialRequest("T4_REF_DISABLED_CAT", "引用停用分类物料", "S", "RAW_MATERIAL", "PURCHASED",
						disabledCategoryId, activeUnitId, "ENABLED", null),
				admin), HttpStatus.BAD_REQUEST, "MASTER_DATA_REFERENCE_INVALID");
		assertError(exchange(HttpMethod.POST, MATERIALS,
				materialRequest("T4_REF_MISSING_CAT", "引用不存在分类物料", "S", "RAW_MATERIAL", "PURCHASED",
						999_999_901L, activeUnitId, "ENABLED", null),
				admin), HttpStatus.BAD_REQUEST, "MASTER_DATA_REFERENCE_INVALID");
		assertError(exchange(HttpMethod.POST, MATERIALS,
				materialRequest("T4_REF_MISSING_UNIT", "引用不存在单位物料", "S", "RAW_MATERIAL", "PURCHASED",
						activeCategoryId, 999_999_902L, "ENABLED", null),
				admin), HttpStatus.BAD_REQUEST, "MASTER_DATA_REFERENCE_INVALID");

		long materialId = createMaterial(admin, "T4_REF_UPDATE_MAT", "引用更新物料", activeCategoryId, activeUnitId,
				"ENABLED");
		assertError(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId,
				materialRequest("T4_REF_UPDATE_MAT", "引用更新物料", "S", "RAW_MATERIAL", "PURCHASED",
						disabledCategoryId, activeUnitId, "ENABLED", null),
				admin), HttpStatus.BAD_REQUEST, "MASTER_DATA_REFERENCE_INVALID");
		assertError(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId,
				materialRequest("T4_REF_UPDATE_MAT", "引用更新物料", "S", "RAW_MATERIAL", "PURCHASED", 999_999_903L,
						activeUnitId, "ENABLED", null),
				admin), HttpStatus.BAD_REQUEST, "MASTER_DATA_REFERENCE_INVALID");
		assertError(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId,
				materialRequest("T4_REF_UPDATE_MAT", "引用更新物料", "S", "RAW_MATERIAL", "PURCHASED",
						activeCategoryId, 999_999_904L, "ENABLED", null),
				admin), HttpStatus.BAD_REQUEST, "MASTER_DATA_REFERENCE_INVALID");
	}

	@Test
	void enabledMaterialPreventsCategoryDisable() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T4_CAT_IN_USE_UNIT", "分类占用单位");
		long categoryId = createCategory(admin, "T4_CAT_IN_USE", "被物料引用分类", null, "ENABLED", 10);
		createMaterial(admin, "T4_CAT_IN_USE_MAT", "分类占用物料", categoryId, unitId, "ENABLED");

		assertError(exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId + "/disable", Map.of(), admin),
				HttpStatus.CONFLICT, "MASTER_DATA_CATEGORY_IN_USE");
	}

	@Test
	void enabledMaterialPreventsCategoryDisableThroughUpdateStatus() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T4_CAT_UPDATE_IN_USE_UNIT", "分类更新占用单位");
		long categoryId = createCategory(admin, "T4_CAT_UPDATE_IN_USE", "更新停用被引用分类", null, "ENABLED", 10);
		createMaterial(admin, "T4_CAT_UPDATE_IN_USE_MAT", "分类更新占用物料", categoryId, unitId, "ENABLED");

		assertError(exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId,
				categoryRequest("T4_CAT_UPDATE_IN_USE", "更新停用被引用分类", null, "DISABLED", 10, null), admin),
				HttpStatus.CONFLICT, "MASTER_DATA_CATEGORY_IN_USE");
	}

	@Test
	void enabledChildCategoryPreventsCategoryDisableThroughDisableAndUpdateStatus() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long parentId = createCategory(admin, "T4_CHILD_IN_USE_PARENT", "启用子分类父分类", null, "ENABLED", 10);
		createCategory(admin, "T4_CHILD_IN_USE_CHILD", "启用子分类", parentId, "ENABLED", 20);

		assertError(exchange(HttpMethod.PUT, CATEGORIES + "/" + parentId + "/disable", Map.of(), admin),
				HttpStatus.CONFLICT, "MASTER_DATA_CATEGORY_IN_USE");
		assertError(exchange(HttpMethod.PUT, CATEGORIES + "/" + parentId,
				categoryRequest("T4_CHILD_IN_USE_PARENT", "启用子分类父分类", null, "DISABLED", 10, null), admin),
				HttpStatus.CONFLICT, "MASTER_DATA_CATEGORY_IN_USE");
	}

	@Test
	void disabledCategoryUpdateWithDisabledStatusStillChecksEnabledChildren() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long parentId = createCategory(admin, "T4_DISABLED_PARENT_CHECK", "已停用仍校验父分类", null, "ENABLED", 10);
		createCategory(admin, "T4_DISABLED_PARENT_CHILD", "已停用仍校验子分类", parentId, "ENABLED", 20);
		this.jdbcTemplate.update("update mst_material_category set status = 'DISABLED' where id = ?", parentId);

		assertError(exchange(HttpMethod.PUT, CATEGORIES + "/" + parentId,
				categoryRequest("T4_DISABLED_PARENT_CHECK", "已停用仍校验父分类", null, "DISABLED", 10, null), admin),
				HttpStatus.CONFLICT, "MASTER_DATA_CATEGORY_IN_USE");
	}

	@Test
	void categoryParentMustNotBeSelfChildDisabledOrCyclic() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long rootId = createCategory(admin, "T4_PARENT_ROOT", "父级根分类", null, "ENABLED", 10);
		long childId = createCategory(admin, "T4_PARENT_CHILD", "父级子分类", rootId, "ENABLED", 20);
		long grandchildId = createCategory(admin, "T4_PARENT_GRANDCHILD", "父级孙分类", childId, "ENABLED", 30);

		assertError(exchange(HttpMethod.PUT, CATEGORIES + "/" + childId,
				categoryRequest("T4_PARENT_CHILD", "父级子分类", childId, "ENABLED", 20, null), admin),
				HttpStatus.BAD_REQUEST, "MASTER_DATA_CATEGORY_PARENT_INVALID");
		assertError(exchange(HttpMethod.PUT, CATEGORIES + "/" + rootId,
				categoryRequest("T4_PARENT_ROOT", "父级根分类", grandchildId, "ENABLED", 10, null), admin),
				HttpStatus.BAD_REQUEST, "MASTER_DATA_CATEGORY_PARENT_INVALID");

		long parentId = createCategory(admin, "T4_PARENT_DISABLED", "停用父分类", null, "ENABLED", 40);
		long disabledChildId = createCategory(admin, "T4_PARENT_DISABLED_CHILD", "父停用子分类", parentId, "DISABLED",
				50);
		assertThat(exchange(HttpMethod.PUT, CATEGORIES + "/" + parentId + "/disable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertError(exchange(HttpMethod.PUT, CATEGORIES + "/" + disabledChildId + "/enable", Map.of(), admin),
				HttpStatus.BAD_REQUEST, "MASTER_DATA_CATEGORY_PARENT_INVALID");

		long cyclicId = createCategory(admin, "T4_PARENT_CYCLIC", "环形父级分类", null, "DISABLED", 60);
		this.jdbcTemplate.update("update mst_material_category set parent_id = ? where id = ?", cyclicId, cyclicId);
		assertError(exchange(HttpMethod.PUT, CATEGORIES + "/" + cyclicId + "/enable", Map.of(), admin),
				HttpStatus.BAD_REQUEST, "MASTER_DATA_CATEGORY_PARENT_INVALID");
	}

	@Test
	void materialEnableRejectsDisabledReferences() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long categoryUnitId = createUnit(admin, "T4_ENABLE_CAT_UNIT", "启用分类校验单位");
		long categoryId = createCategory(admin, "T4_ENABLE_CAT", "启用校验分类", null, "ENABLED", 10);
		long materialWithDisabledCategoryId = createMaterial(admin, "T4_ENABLE_DISABLED_CAT_MAT", "启用校验物料一",
				categoryId, categoryUnitId, "DISABLED");
		assertThat(exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId + "/disable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertError(exchange(HttpMethod.PUT, MATERIALS + "/" + materialWithDisabledCategoryId + "/enable", Map.of(),
				admin), HttpStatus.BAD_REQUEST, "MASTER_DATA_REFERENCE_INVALID");

		long unitId = createUnit(admin, "T4_ENABLE_UNIT", "启用单位校验单位");
		long activeCategoryId = createCategory(admin, "T4_ENABLE_UNIT_CAT", "启用单位校验分类", null, "ENABLED", 20);
		long materialWithDisabledUnitId = createMaterial(admin, "T4_ENABLE_DISABLED_UNIT_MAT", "启用校验物料二",
				activeCategoryId, unitId, "DISABLED");
		assertThat(exchange(HttpMethod.PUT, UNITS + "/" + unitId + "/disable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertError(exchange(HttpMethod.PUT, MATERIALS + "/" + materialWithDisabledUnitId + "/enable", Map.of(),
				admin), HttpStatus.BAD_REQUEST, "MASTER_DATA_REFERENCE_INVALID");
	}

	@Test
	void enabledMaterialPreventsUnitDisable() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T4_UNIT_IN_USE", "被物料引用单位");
		long categoryId = createCategory(admin, "T4_UNIT_IN_USE_CAT", "单位占用分类", null, "ENABLED", 10);
		createMaterial(admin, "T4_UNIT_IN_USE_MAT", "单位占用物料", categoryId, unitId, "ENABLED");

		assertError(exchange(HttpMethod.PUT, UNITS + "/" + unitId + "/disable", Map.of(), admin),
				HttpStatus.CONFLICT, "MASTER_DATA_UNIT_IN_USE");
	}

	@Test
	void enabledMaterialPreventsUnitDisableThroughUpdateStatus() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T4_UNIT_UPDATE_IN_USE", "更新停用被引用单位");
		long categoryId = createCategory(admin, "T4_UNIT_UPDATE_IN_USE_CAT", "单位更新占用分类", null, "ENABLED", 10);
		createMaterial(admin, "T4_UNIT_UPDATE_IN_USE_MAT", "单位更新占用物料", categoryId, unitId, "ENABLED");

		assertError(exchange(HttpMethod.PUT, UNITS + "/" + unitId,
				Map.of("code", "T4_UNIT_UPDATE_IN_USE", "name", "更新停用被引用单位", "precisionScale", 2, "sortOrder",
						10, "status", "DISABLED"),
				admin), HttpStatus.CONFLICT, "MASTER_DATA_UNIT_IN_USE");
	}

	@Test
	void disabledUnitUpdateWithDisabledStatusStillChecksEnabledMaterials() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T4_DISABLED_UNIT_CHECK", "已停用仍校验单位");
		long categoryId = createCategory(admin, "T4_DISABLED_UNIT_CHECK_CAT", "已停用单位校验分类", null, "ENABLED",
				10);
		createMaterial(admin, "T4_DISABLED_UNIT_CHECK_MAT", "已停用单位校验物料", categoryId, unitId, "ENABLED");
		this.jdbcTemplate.update("update mst_unit set status = 'DISABLED' where id = ?", unitId);

		assertError(exchange(HttpMethod.PUT, UNITS + "/" + unitId,
				Map.of("code", "T4_DISABLED_UNIT_CHECK", "name", "已停用仍校验单位", "precisionScale", 2, "sortOrder",
						10, "status", "DISABLED"),
				admin), HttpStatus.CONFLICT, "MASTER_DATA_UNIT_IN_USE");
	}

	@Test
	void categoryStatusDefaultsToEnabledAndUpdateWithoutStatusRetainsCurrentStatus() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		ResponseEntity<String> createResponse = exchange(HttpMethod.POST, CATEGORIES,
				categoryRequest("T4_CAT_STATUS_DEFAULT", "分类状态默认", null, null, 10, null), admin);
		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode created = data(createResponse);
		long categoryId = created.get("id").longValue();
		assertThat(created.get("status").asText()).isEqualTo("ENABLED");

		assertThat(exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId + "/disable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		ResponseEntity<String> updateWithoutStatus = exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId,
				categoryRequest("T4_CAT_STATUS_DEFAULT_UPD", "分类状态默认改", null, null, 20, null), admin);

		assertThat(updateWithoutStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode updated = data(updateWithoutStatus);
		assertThat(updated.get("code").asText()).isEqualTo("T4_CAT_STATUS_DEFAULT_UPD");
		assertThat(updated.get("status").asText()).isEqualTo("DISABLED");
	}

	@Test
	void materialStatusDefaultsToEnabledAndUpdateWithoutStatusRetainsCurrentStatus() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T4_STATUS_DEFAULT_UNIT", "状态默认单位");
		long categoryId = createCategory(admin, "T4_STATUS_DEFAULT_CAT", "状态默认分类", null, "ENABLED", 10);

		ResponseEntity<String> createResponse = exchange(HttpMethod.POST, MATERIALS,
				materialRequest("T4_STATUS_DEFAULT_MAT", "状态默认物料", "S", "RAW_MATERIAL", "PURCHASED", categoryId,
						unitId, null, null),
				admin);
		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode created = data(createResponse);
		long materialId = created.get("id").longValue();
		assertThat(created.get("status").asText()).isEqualTo("ENABLED");

		assertThat(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId + "/disable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		ResponseEntity<String> updateWithoutStatus = exchange(HttpMethod.PUT, MATERIALS + "/" + materialId,
				materialRequest("T4_STATUS_DEFAULT_MAT_UPD", "状态默认物料改", "S-2", "RAW_MATERIAL", "PURCHASED",
						categoryId, unitId, null, null),
				admin);

		assertThat(updateWithoutStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(updateWithoutStatus).get("status").asText()).isEqualTo("DISABLED");
	}

	@Test
	void categorySortOrderIsRequiredForCreateAndUpdate() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		Map<String, Object> missingCreateSortOrder = categoryRequest("T4_MISSING_SORT_CREATE", "缺失排序分类", null,
				"ENABLED", 10, null);
		missingCreateSortOrder.remove("sortOrder");

		assertError(exchange(HttpMethod.POST, CATEGORIES, missingCreateSortOrder, admin), HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR");
		assertThat(countByCode("mst_material_category", "T4_MISSING_SORT_CREATE")).isZero();

		long categoryId = createCategory(admin, "T4_MISSING_SORT_UPDATE", "更新缺失排序分类", null, "ENABLED", 10);
		Map<String, Object> missingUpdateSortOrder = categoryRequest("T4_MISSING_SORT_UPDATE", "更新缺失排序分类", null,
				"ENABLED", 10, null);
		missingUpdateSortOrder.remove("sortOrder");

		assertError(exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId, missingUpdateSortOrder, admin),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
	}

	@Test
	void duplicateMaterialCodeReturnsMasterDataCodeExists() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T4_DUP_UNIT", "重复编码单位");
		long categoryId = createCategory(admin, "T4_DUP_CAT", "重复编码分类", null, "ENABLED", 10);
		createMaterial(admin, "T4_DUP_MAT", "重复编码物料", categoryId, unitId, "ENABLED");

		assertError(exchange(HttpMethod.POST, MATERIALS,
				materialRequest("T4_DUP_MAT", "重复编码物料二", "S", "RAW_MATERIAL", "PURCHASED", categoryId, unitId,
						"ENABLED", null),
				admin), HttpStatus.CONFLICT, "MASTER_DATA_CODE_EXISTS");
	}

	@Test
	void invalidMaterialTypeOrSourceTypeReturnsValidationError() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T4_ENUM_UNIT", "枚举校验单位");
		long categoryId = createCategory(admin, "T4_ENUM_CAT", "枚举校验分类", null, "ENABLED", 10);

		assertError(exchange(HttpMethod.POST, MATERIALS,
				materialRequest("T4_BAD_TYPE_MAT", "非法类型物料", "S", "BAD_TYPE", "PURCHASED", categoryId, unitId,
						"ENABLED", null),
				admin), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		assertError(exchange(HttpMethod.POST, MATERIALS,
				materialRequest("T4_BAD_SOURCE_MAT", "非法来源物料", "S", "RAW_MATERIAL", "BAD_SOURCE", categoryId,
						unitId, "ENABLED", null),
				admin), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
	}

	@Test
	void viewOnlyUserCanReadButCannotMutateCategoriesAndMaterials() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T4_PERMISSION_UNIT", "权限单位");
		long categoryId = createCategory(admin, "T4_PERMISSION_CAT", "权限分类", null, "ENABLED", 10);
		long materialId = createMaterial(admin, "T4_PERMISSION_MAT", "权限物料", categoryId, unitId, "ENABLED");
		long roleId = createRole("T4_MATERIAL_VIEW_ROLE", "任务四物料只读角色", admin);
		assignPermissions(roleId,
				List.of(permissionId("master:material-category:view"), permissionId("master:material:view")), admin);
		createUser("task4-material-view-user", List.of(roleId), admin);
		AuthenticatedSession readonly = login("task4-material-view-user", "Qherp@2026!");

		assertThat(get(CATEGORIES + "?keyword=T4_PERMISSION_CAT", readonly).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(get(CATEGORIES + "/" + categoryId, readonly).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(get(MATERIALS + "?keyword=T4_PERMISSION_MAT", readonly).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(get(MATERIALS + "/" + materialId, readonly).getStatusCode()).isEqualTo(HttpStatus.OK);

		assertForbidden(exchange(HttpMethod.POST, CATEGORIES,
				categoryRequest("T4_PERMISSION_CAT_CREATE", "权限分类新增", null, "ENABLED", 20, null), readonly));
		assertForbidden(exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId,
				categoryRequest("T4_PERMISSION_CAT", "权限分类改", null, "ENABLED", 10, null), readonly));
		assertForbidden(exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId + "/enable", Map.of(), readonly));
		assertForbidden(exchange(HttpMethod.PUT, CATEGORIES + "/" + categoryId + "/disable", Map.of(), readonly));

		assertForbidden(exchange(HttpMethod.POST, MATERIALS,
				materialRequest("T4_PERMISSION_MAT_CREATE", "权限物料新增", "S", "RAW_MATERIAL", "PURCHASED",
						categoryId, unitId, "ENABLED", null),
				readonly));
		assertForbidden(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId,
				materialRequest("T4_PERMISSION_MAT", "权限物料改", "S", "RAW_MATERIAL", "PURCHASED", categoryId,
						unitId, "ENABLED", null),
				readonly));
		assertForbidden(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId + "/enable", Map.of(), readonly));
		assertForbidden(exchange(HttpMethod.PUT, MATERIALS + "/" + materialId + "/disable", Map.of(), readonly));
	}

	private long createUnit(AuthenticatedSession admin, String code, String name) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, UNITS,
				Map.of("code", code, "name", name, "precisionScale", 2, "sortOrder", 10, "status", "ENABLED"),
				admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createCategory(AuthenticatedSession admin, String code, String name, Long parentId, String status,
			int sortOrder) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, CATEGORIES,
				categoryRequest(code, name, parentId, status, sortOrder, "分类备注"), admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode data = data(response);
		assertThat(data.get("code").asText()).isEqualTo(code);
		assertThat(data.get("status").asText()).isEqualTo(status);
		return data.get("id").longValue();
	}

	private long createMaterial(AuthenticatedSession admin, String code, String name, long categoryId, long unitId,
			String status) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, MATERIALS,
				materialRequest(code, name, "S", "RAW_MATERIAL", "PURCHASED", categoryId, unitId, status, "物料备注"),
				admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode data = data(response);
		assertThat(data.get("code").asText()).isEqualTo(code);
		assertThat(data.get("status").asText()).isEqualTo(status);
		return data.get("id").longValue();
	}

	private Map<String, Object> categoryRequest(String code, String name, Long parentId, String status, int sortOrder,
			String remark) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("code", code);
		request.put("name", name);
		if (parentId != null) {
			request.put("parentId", parentId);
		}
		if (status != null) {
			request.put("status", status);
		}
		request.put("sortOrder", sortOrder);
		if (remark != null) {
			request.put("remark", remark);
		}
		return request;
	}

	private Map<String, Object> materialRequest(String code, String name, String specification, String materialType,
			String sourceType, long categoryId, long unitId, String status, String remark) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("code", code);
		request.put("name", name);
		request.put("specification", specification);
		request.put("materialType", materialType);
		request.put("sourceType", sourceType);
		request.put("categoryId", categoryId);
		request.put("unitId", unitId);
		if (status != null) {
			request.put("status", status);
		}
		if (remark != null) {
			request.put("remark", remark);
		}
		return request;
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
				Map.of("code", code, "name", name, "description", "测试角色", "status", "ENABLED"), session);
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

	private long countByCode(String tableName, String code) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + tableName + " where code = ?", Long.class,
				code);
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(response.getBody()).contains("\"code\":\"" + code + "\"");
	}

	private void assertForbidden(ResponseEntity<String> response) {
		assertError(response, HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	private void assertAuditLog(String action, String targetType, long targetId, String targetSummary,
			String requestMethod, String requestPath) {
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				and target_type = ?
				and target_id = ?
				and target_summary = ?
				and request_method = ?
				and request_path = ?
				""", Long.class, action, targetType, Long.toString(targetId), targetSummary, requestMethod,
				requestPath)).as(action).isOne();
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
