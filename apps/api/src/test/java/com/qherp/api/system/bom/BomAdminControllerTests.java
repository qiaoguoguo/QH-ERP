package com.qherp.api.system.bom;

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
		properties = "qherp.test.context=task6-bom-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BomAdminControllerTests extends PostgresIntegrationTest {

	private static final String UNITS = "/api/admin/master/units";

	private static final String CATEGORIES = "/api/admin/master/material-categories";

	private static final String MATERIALS = "/api/admin/master/materials";

	private static final String BOMS = "/api/admin/boms";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void adminCanManageBomLifecycle() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		TestMaterials materials = createTestMaterials(admin, "T6_LIFE");

		ResponseEntity<String> createResponse = exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_LIFE_BOM", materials.finishedGoodId(), "V1.0", "任务六生命周期 BOM",
						materials.unitEachId(),
						List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 2.5, 0.02),
								bomItem(20, materials.semiFinishedId(), materials.unitEachId(), 1, 0),
								bomItem(30, materials.auxiliaryId(), materials.unitEachId(), 3, 0))),
				admin);
		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode created = data(createResponse);
		long bomId = created.get("id").longValue();
		assertThat(created.get("status").asText()).isEqualTo("DRAFT");
		assertThat(created.get("items").size()).isEqualTo(3);
		assertAuditLog("BOM_CREATE", bomId, "T6_LIFE_BOM", "POST", BOMS);

		ResponseEntity<String> listResponse = get(BOMS + "?keyword=T6_LIFE_BOM&page=1&pageSize=20", admin);
		assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(listResponse).get("total").longValue()).isEqualTo(1);

		ResponseEntity<String> detailResponse = get(BOMS + "/" + bomId, admin);
		assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(detailResponse).get("items").size()).isEqualTo(3);

		ResponseEntity<String> updateResponse = exchange(HttpMethod.PUT, BOMS + "/" + bomId,
				bomRequest("T6_LIFE_BOM", materials.finishedGoodId(), "V1.0", "任务六生命周期 BOM 改",
						materials.unitEachId(),
						List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 2.75, 0.01),
								bomItem(20, materials.auxiliaryId(), materials.unitEachId(), 4, 0))),
				admin);
		assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(updateResponse).get("items").size()).isEqualTo(2);
		assertAuditLog("BOM_UPDATE", bomId, "T6_LIFE_BOM", "PUT", BOMS + "/" + bomId);

		ResponseEntity<String> enableResponse = exchange(HttpMethod.PUT, BOMS + "/" + bomId + "/enable", Map.of(),
				admin);
		assertThat(enableResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(enableResponse).get("status").asText()).isEqualTo("ENABLED");
		assertAuditLog("BOM_ENABLE", bomId, "T6_LIFE_BOM", "PUT", BOMS + "/" + bomId + "/enable");

		assertError(exchange(HttpMethod.PUT, BOMS + "/" + bomId,
				bomRequest("T6_LIFE_BOM", materials.finishedGoodId(), "V1.0", "启用后不可编辑",
						materials.unitEachId(),
						List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 2.8, 0))),
				admin), HttpStatus.CONFLICT, "BOM_STATUS_NOT_EDITABLE");

		ResponseEntity<String> copyResponse = exchange(HttpMethod.POST, BOMS + "/" + bomId + "/copy",
				Map.of("bomCode", "T6_LIFE_BOM_V11", "versionCode", "V1.1", "name", "任务六生命周期 BOM V1.1"),
				admin);
		assertThat(copyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode copied = data(copyResponse);
		long copiedBomId = copied.get("id").longValue();
		assertThat(copied.get("status").asText()).isEqualTo("DRAFT");
		assertThat(copied.get("items").size()).isEqualTo(2);
		assertAuditLog("BOM_COPY", copiedBomId, "T6_LIFE_BOM_V11", "POST", BOMS + "/" + bomId + "/copy");

		ResponseEntity<String> disableResponse = exchange(HttpMethod.PUT, BOMS + "/" + bomId + "/disable", Map.of(),
				admin);
		assertThat(disableResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(disableResponse).get("status").asText()).isEqualTo("DISABLED");
		assertAuditLog("BOM_DISABLE", bomId, "T6_LIFE_BOM", "PUT", BOMS + "/" + bomId + "/disable");
	}

	@Test
	void bomCodeVersionAndEnabledVersionMustBeUnique() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		TestMaterials materials = createTestMaterials(admin, "T6_UNIQUE");
		Map<String, Object> request = bomRequest("T6_UNIQUE_BOM", materials.finishedGoodId(), "V1.0",
				"任务六唯一 BOM", materials.unitEachId(),
				List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 1, 0)));
		long firstBomId = createBom(admin, request);

		assertError(exchange(HttpMethod.POST, BOMS, request, admin), HttpStatus.CONFLICT, "BOM_CODE_EXISTS");
		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_UNIQUE_BOM_OTHER", materials.finishedGoodId(), "V1.0", "任务六重复版本",
						materials.unitEachId(),
						List.of(bomItem(10, materials.auxiliaryId(), materials.unitEachId(), 1, 0))),
				admin), HttpStatus.CONFLICT, "BOM_VERSION_EXISTS");

		assertThat(exchange(HttpMethod.PUT, BOMS + "/" + firstBomId + "/enable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		long secondBomId = createBom(admin,
				bomRequest("T6_UNIQUE_BOM_V2", materials.finishedGoodId(), "V2.0", "任务六第二版本",
						materials.unitEachId(),
						List.of(bomItem(10, materials.auxiliaryId(), materials.unitEachId(), 1, 0))));

		assertError(exchange(HttpMethod.PUT, BOMS + "/" + secondBomId + "/enable", Map.of(), admin),
				HttpStatus.CONFLICT, "BOM_ENABLED_VERSION_EXISTS");
		assertThat(exchange(HttpMethod.PUT, BOMS + "/" + firstBomId + "/disable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertThat(exchange(HttpMethod.PUT, BOMS + "/" + secondBomId + "/enable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
	}

	@Test
	void bomReferencesAndItemsMustBeValid() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		TestMaterials materials = createTestMaterials(admin, "T6_VALID");
		long disabledUnitId = createUnit(admin, "T6_VALID_DISABLED_UNIT", "任务六停用单位");
		assertThat(exchange(HttpMethod.PUT, UNITS + "/" + disabledUnitId + "/disable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);

		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_VALID_RAW_PARENT", materials.rawMaterialId(), "V1.0", "原材料父项",
						materials.unitEachId(),
						List.of(bomItem(10, materials.auxiliaryId(), materials.unitEachId(), 1, 0))),
				admin), HttpStatus.BAD_REQUEST, "BOM_PARENT_MATERIAL_INVALID");
		assertThat(exchange(HttpMethod.PUT, MATERIALS + "/" + materials.finishedGoodId() + "/disable", Map.of(),
				admin).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_VALID_DISABLED_PARENT", materials.finishedGoodId(), "V1.1", "停用父项",
						materials.unitEachId(),
						List.of(bomItem(10, materials.auxiliaryId(), materials.unitEachId(), 1, 0))),
				admin), HttpStatus.BAD_REQUEST, "BOM_PARENT_MATERIAL_INVALID");
		assertThat(exchange(HttpMethod.PUT, MATERIALS + "/" + materials.finishedGoodId() + "/enable", Map.of(),
				admin).getStatusCode()).isEqualTo(HttpStatus.OK);

		assertThat(exchange(HttpMethod.PUT, MATERIALS + "/" + materials.auxiliaryId() + "/disable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_VALID_DISABLED_CHILD", materials.finishedGoodId(), "V1.2", "停用子项",
						materials.unitEachId(),
						List.of(bomItem(10, materials.auxiliaryId(), materials.unitEachId(), 1, 0))),
				admin), HttpStatus.BAD_REQUEST, "BOM_CHILD_MATERIAL_INVALID");
		assertThat(exchange(HttpMethod.PUT, MATERIALS + "/" + materials.auxiliaryId() + "/enable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);

		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_VALID_BASE_UNIT", materials.finishedGoodId(), "V1.3", "停用基准单位", disabledUnitId,
						List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 1, 0))),
				admin), HttpStatus.BAD_REQUEST, "BOM_UNIT_INVALID");
		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_VALID_ITEM_UNIT", materials.finishedGoodId(), "V1.4", "停用明细单位",
						materials.unitEachId(),
						List.of(bomItem(10, materials.rawMaterialId(), disabledUnitId, 1, 0))),
				admin), HttpStatus.BAD_REQUEST, "BOM_UNIT_INVALID");
		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_VALID_EMPTY_ITEMS", materials.finishedGoodId(), "V1.5", "空明细",
						materials.unitEachId(), List.of()),
				admin), HttpStatus.BAD_REQUEST, "BOM_EMPTY_ITEMS");
		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_VALID_ZERO_QTY", materials.finishedGoodId(), "V1.6", "零用量", materials.unitEachId(),
						List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 0, 0))),
				admin), HttpStatus.BAD_REQUEST, "BOM_QUANTITY_INVALID");
		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_VALID_BAD_LOSS", materials.finishedGoodId(), "V1.7", "损耗率错误",
						materials.unitEachId(),
						List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 1, 1))),
				admin), HttpStatus.BAD_REQUEST, "BOM_QUANTITY_INVALID");
		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_VALID_SELF", materials.finishedGoodId(), "V1.8", "父项等于子项",
						materials.unitEachId(),
						List.of(bomItem(10, materials.finishedGoodId(), materials.unitEachId(), 1, 0))),
				admin), HttpStatus.BAD_REQUEST, "BOM_SELF_REFERENCE");
		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_VALID_DUP_ITEM", materials.finishedGoodId(), "V1.9", "重复子项",
						materials.unitEachId(),
						List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 1, 0),
								bomItem(20, materials.rawMaterialId(), materials.unitKgId(), 2, 0))),
				admin), HttpStatus.CONFLICT, "BOM_DUPLICATE_ITEM");
	}

	@Test
	void detectableBomCycleMustBeRejected() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		TestMaterials materials = createTestMaterials(admin, "T6_CYCLE");
		long semiBomId = createBom(admin,
				bomRequest("T6_CYCLE_SEMI", materials.semiFinishedId(), "V1.0", "半成品 BOM",
						materials.unitEachId(),
						List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 1, 0))));
		createBom(admin,
				bomRequest("T6_CYCLE_FINISHED", materials.finishedGoodId(), "V1.0", "成品 BOM",
						materials.unitEachId(),
						List.of(bomItem(10, materials.semiFinishedId(), materials.unitEachId(), 1, 0))));

		assertError(exchange(HttpMethod.PUT, BOMS + "/" + semiBomId,
				bomRequest("T6_CYCLE_SEMI", materials.semiFinishedId(), "V1.0", "半成品 BOM 改",
						materials.unitEachId(),
						List.of(bomItem(10, materials.finishedGoodId(), materials.unitEachId(), 1, 0))),
				admin), HttpStatus.CONFLICT, "BOM_CYCLE_DETECTED");
		assertError(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_CYCLE_FINISHED_BAD", materials.finishedGoodId(), "V1.1", "成品循环 BOM",
						materials.unitEachId(),
						List.of(bomItem(10, materials.finishedGoodId(), materials.unitEachId(), 1, 0))),
				admin), HttpStatus.BAD_REQUEST, "BOM_SELF_REFERENCE");
	}

	@Test
	void bomAuthorizationMustBeEnforced() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		TestMaterials materials = createTestMaterials(admin, "T6_AUTH");
		long bomId = createBom(admin,
				bomRequest("T6_AUTH_BOM", materials.finishedGoodId(), "V1.0", "权限 BOM",
						materials.unitEachId(),
						List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 1, 0))));

		assertError(get(BOMS, null), HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED");
		assertError(get(BOMS + "/" + bomId, null), HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED");

		createUser("task6-no-bom-user", List.of(), admin);
		AuthenticatedSession noBomPermission = login("task6-no-bom-user", "Qherp@2026!");
		assertError(get(BOMS, noBomPermission), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertError(get(BOMS + "/" + bomId, noBomPermission), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		long roleId = createRole("T6_BOM_VIEW_ROLE", "任务六 BOM 只读角色", admin);
		assignPermissions(roleId, List.of(permissionId("material:bom:view")), admin);
		createUser("task6-bom-view-user", List.of(roleId), admin);
		AuthenticatedSession readonly = login("task6-bom-view-user", "Qherp@2026!");
		assertThat(get(BOMS, readonly).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertForbidden(exchange(HttpMethod.POST, BOMS,
				bomRequest("T6_AUTH_FORBIDDEN_CREATE", materials.finishedGoodId(), "V9.0", "无权限创建",
						materials.unitEachId(),
						List.of(bomItem(10, materials.rawMaterialId(), materials.unitKgId(), 1, 0))),
				readonly));
		assertForbidden(exchange(HttpMethod.PUT, BOMS + "/" + bomId + "/enable", Map.of(), readonly));
		assertThat(exchangeWithoutCsrf(HttpMethod.PUT, BOMS + "/" + bomId + "/enable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.FORBIDDEN);
	}

	private TestMaterials createTestMaterials(AuthenticatedSession admin, String prefix) throws Exception {
		long unitEachId = createUnit(admin, prefix + "_UNIT_EACH", prefix + " 个");
		long unitKgId = createUnit(admin, prefix + "_UNIT_KG", prefix + " 千克");
		long categoryId = createCategory(admin, prefix + "_CAT", prefix + " 分类");
		long finishedGoodId = createMaterial(admin, prefix + "_FG", prefix + " 成品", "FINISHED_GOOD", "SELF_MADE",
				categoryId, unitEachId, "ENABLED");
		long semiFinishedId = createMaterial(admin, prefix + "_SEMI", prefix + " 半成品", "SEMI_FINISHED",
				"SELF_MADE", categoryId, unitEachId, "ENABLED");
		long rawMaterialId = createMaterial(admin, prefix + "_RAW", prefix + " 原材料", "RAW_MATERIAL", "PURCHASED",
				categoryId, unitKgId, "ENABLED");
		long auxiliaryId = createMaterial(admin, prefix + "_AUX", prefix + " 辅料", "AUXILIARY", "PURCHASED",
				categoryId, unitEachId, "ENABLED");
		return new TestMaterials(unitEachId, unitKgId, categoryId, finishedGoodId, semiFinishedId, rawMaterialId,
				auxiliaryId);
	}

	private long createUnit(AuthenticatedSession admin, String code, String name) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, UNITS,
				Map.of("code", code, "name", name, "precisionScale", 2, "sortOrder", 10, "status", "ENABLED"),
				admin);
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
			String sourceType, long categoryId, long unitId, String status) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, MATERIALS,
				materialRequest(code, name, materialType, sourceType, categoryId, unitId, status), admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createBom(AuthenticatedSession admin, Map<String, Object> request) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, BOMS, request, admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private Map<String, Object> materialRequest(String code, String name, String materialType, String sourceType,
			long categoryId, long unitId, String status) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("code", code);
		request.put("name", name);
		request.put("specification", "S");
		request.put("materialType", materialType);
		request.put("sourceType", sourceType);
		request.put("categoryId", categoryId);
		request.put("unitId", unitId);
		request.put("status", status);
		return request;
	}

	private Map<String, Object> bomRequest(String bomCode, long parentMaterialId, String versionCode, String name,
			long baseUnitId, List<Map<String, Object>> items) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("bomCode", bomCode);
		request.put("parentMaterialId", parentMaterialId);
		request.put("versionCode", versionCode);
		request.put("name", name);
		request.put("baseQuantity", 1);
		request.put("baseUnitId", baseUnitId);
		request.put("effectiveFrom", "2026-07-03");
		request.put("items", items);
		return request;
	}

	private Map<String, Object> bomItem(int lineNo, long childMaterialId, long unitId, double quantity, double lossRate) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("lineNo", lineNo);
		item.put("childMaterialId", childMaterialId);
		item.put("unitId", unitId);
		item.put("quantity", quantity);
		item.put("lossRate", lossRate);
		return item;
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

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(response.getBody()).contains("\"code\":\"" + code + "\"");
	}

	private void assertForbidden(ResponseEntity<String> response) {
		assertError(response, HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	private void assertAuditLog(String action, long targetId, String targetSummary, String requestMethod,
			String requestPath) {
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				and target_type = 'BOM'
				and target_id = ?
				and target_summary = ?
				and request_method = ?
				and request_path = ?
				""", Long.class, action, Long.toString(targetId), targetSummary, requestMethod, requestPath)).as(action)
			.isOne();
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
		return this.restTemplate.exchange(path, HttpMethod.GET,
				entity(null, session == null ? null : session.sessionCookie(), null), String.class);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()),
				String.class);
	}

	private ResponseEntity<String> exchangeWithoutCsrf(HttpMethod method, String path, Object body,
			AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), null), String.class);
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

	private record TestMaterials(long unitEachId, long unitKgId, long categoryId, long finishedGoodId,
			long semiFinishedId, long rawMaterialId, long auxiliaryId) {
	}

}
