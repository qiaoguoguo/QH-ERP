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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=task3-master-data-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MasterDataAdminControllerTests extends PostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void adminCanCreateQueryUpdateDisableAndEnableMasterDataResources() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");

		long unitId = createUnit(admin, "T3_UNIT", "任务三计量单位");
		assertResourceLifecycle(admin, "/api/admin/master/units", unitId, "T3_UNIT", "UNIT",
				Map.of("code", "T3_UNIT_UPD", "name", "任务三计量单位改", "precisionScale", 3, "sortOrder", 20,
						"remark", "更新计量单位"));

		long warehouseId = createResource(admin, "/api/admin/master/warehouses",
				Map.of("code", "T3_WH", "name", "任务三仓库", "warehouseType", "RAW", "managerName", "仓管一",
						"address", "一号园区", "status", "ENABLED", "remark", "仓库备注"));
		assertResourceLifecycle(admin, "/api/admin/master/warehouses", warehouseId, "T3_WH", "WAREHOUSE",
				Map.of("code", "T3_WH_UPD", "name", "任务三仓库改", "warehouseType", "FINISHED",
						"managerName", "仓管二", "address", "二号园区", "remark", "更新仓库"));

		long supplierId = createResource(admin, "/api/admin/master/suppliers",
				Map.of("code", "T3_SUP", "name", "任务三供应商", "contactName", "供应商联系人", "contactPhone",
						"13800000001", "status", "ENABLED", "remark", "供应商备注"));
		assertResourceLifecycle(admin, "/api/admin/master/suppliers", supplierId, "T3_SUP", "SUPPLIER",
				Map.of("code", "T3_SUP_UPD", "name", "任务三供应商改", "contactName", "供应商联系人二",
						"contactPhone", "13800000002", "remark", "更新供应商"));

		long customerId = createResource(admin, "/api/admin/master/customers",
				Map.of("code", "T3_CUS", "name", "任务三客户", "contactName", "客户联系人", "contactPhone",
						"13900000001", "status", "ENABLED", "remark", "客户备注"));
		assertResourceLifecycle(admin, "/api/admin/master/customers", customerId, "T3_CUS", "CUSTOMER",
				Map.of("code", "T3_CUS_UPD", "name", "任务三客户改", "contactName", "客户联系人二", "contactPhone",
						"13900000002", "remark", "更新客户"));
	}

	@Test
	void duplicateCodeReturnsMasterDataCodeExistsForAllResources() {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		List<ResourceRequest> requests = List.of(
				new ResourceRequest("/api/admin/master/units",
						Map.of("code", "T3_DUP_UNIT", "name", "重复计量单位", "precisionScale", 2, "sortOrder", 10,
								"status", "ENABLED")),
				new ResourceRequest("/api/admin/master/warehouses",
						Map.of("code", "T3_DUP_WH", "name", "重复仓库", "warehouseType", "RAW", "status", "ENABLED")),
				new ResourceRequest("/api/admin/master/suppliers",
						Map.of("code", "T3_DUP_SUP", "name", "重复供应商", "contactName", "供应商联系人", "status",
								"ENABLED")),
				new ResourceRequest("/api/admin/master/customers",
						Map.of("code", "T3_DUP_CUS", "name", "重复客户", "contactName", "客户联系人", "status",
								"ENABLED")));

		for (ResourceRequest request : requests) {
			assertThat(exchange(HttpMethod.POST, request.path(), request.body(), admin).getStatusCode())
				.as(request.path())
				.isEqualTo(HttpStatus.OK);
			ResponseEntity<String> duplicate = exchange(HttpMethod.POST, request.path(), request.body(), admin);

			assertThat(duplicate.getStatusCode()).as(request.path()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(duplicate.getBody()).contains("\"code\":\"MASTER_DATA_CODE_EXISTS\"");
		}
	}

	@Test
	void userWithoutPermissionCannotCreateUnit() {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		createUser("task3-readonly-user", admin);
		AuthenticatedSession readonly = login("task3-readonly-user", "Qherp@2026!");

		ResponseEntity<String> forbidden = exchange(HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", "T3_FORBIDDEN_UNIT", "name", "无权限计量单位", "precisionScale", 2, "sortOrder", 10),
				readonly);

		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(forbidden.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
	}

	@Test
	void readOnlyRoleCanViewUnitButCannotMutateAndCannotAccessOtherResources() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T3_READONLY_UNIT", "只读计量单位");
		long roleId = createRole("T3_UNIT_VIEW_ROLE", "只读计量单位角色", admin);
		assignPermissions(roleId, List.of(permissionId("master:unit:view")), admin);
		createUser("task3-unit-view-user", List.of(roleId), admin);
		AuthenticatedSession readonly = login("task3-unit-view-user", "Qherp@2026!");

		ResponseEntity<String> listResponse = get("/api/admin/master/units?keyword=T3_READONLY_UNIT", readonly);
		assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(listResponse.getBody()).contains("\"code\":\"T3_READONLY_UNIT\"");

		ResponseEntity<String> detailResponse = get("/api/admin/master/units/" + unitId, readonly);
		assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(detailResponse).get("id").longValue()).isEqualTo(unitId);

		assertForbidden(exchange(HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", "T3_READONLY_CREATE", "name", "只读创建", "precisionScale", 2, "sortOrder", 10),
				readonly));
		assertForbidden(exchange(HttpMethod.PUT, "/api/admin/master/units/" + unitId,
				Map.of("code", "T3_READONLY_UNIT_UPD", "name", "只读更新", "precisionScale", 2, "sortOrder", 10),
				readonly));
		assertForbidden(exchange(HttpMethod.PUT, "/api/admin/master/units/" + unitId + "/enable", Map.of(), readonly));
		assertForbidden(exchange(HttpMethod.PUT, "/api/admin/master/units/" + unitId + "/disable", Map.of(), readonly));
		assertForbidden(get("/api/admin/master/warehouses", readonly));
	}

	@Test
	void unitPrecisionScaleAndSortOrderAreRequiredAndNotDefaulted() {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");

		ResponseEntity<String> missingPrecisionScale = exchange(HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", "T3_MISSING_PRECISION", "name", "缺失精度", "sortOrder", 10), admin);
		assertThat(missingPrecisionScale.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(missingPrecisionScale.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
		assertThat(countByCode("mst_unit", "T3_MISSING_PRECISION")).isZero();

		ResponseEntity<String> missingSortOrder = exchange(HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", "T3_MISSING_SORT", "name", "缺失排序", "precisionScale", 2), admin);
		assertThat(missingSortOrder.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(missingSortOrder.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
		assertThat(countByCode("mst_unit", "T3_MISSING_SORT")).isZero();

		ResponseEntity<String> negativePrecisionScale = exchange(HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", "T3_NEGATIVE_PRECISION", "name", "负数精度", "precisionScale", -1, "sortOrder", 10),
				admin);
		assertThat(negativePrecisionScale.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(negativePrecisionScale.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
		assertThat(countByCode("mst_unit", "T3_NEGATIVE_PRECISION")).isZero();
	}

	@Test
	void statusDefaultsRetainsAndRejectsInvalidValues() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");

		ResponseEntity<String> createResponse = exchange(HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", "T3_STATUS_UNIT", "name", "状态计量单位", "precisionScale", 2, "sortOrder", 10),
				admin);
		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode created = data(createResponse);
		long unitId = created.get("id").longValue();
		assertThat(created.get("status").asText()).isEqualTo("ENABLED");

		assertThat(exchange(HttpMethod.PUT, "/api/admin/master/units/" + unitId + "/disable", Map.of(), admin)
			.getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<String> updateWithoutStatus = exchange(HttpMethod.PUT, "/api/admin/master/units/" + unitId,
				Map.of("code", "T3_STATUS_UNIT_UPD", "name", "状态计量单位改", "precisionScale", 4, "sortOrder", 20),
				admin);
		assertThat(updateWithoutStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(updateWithoutStatus).get("status").asText()).isEqualTo("DISABLED");

		ResponseEntity<String> invalidStatus = exchange(HttpMethod.PUT, "/api/admin/master/units/" + unitId,
				Map.of("code", "T3_STATUS_UNIT_UPD", "name", "状态计量单位改", "precisionScale", 4, "sortOrder", 20,
						"status", "LOCKED"),
				admin);
		assertThat(invalidStatus.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(invalidStatus.getBody()).contains("\"code\":\"MASTER_DATA_INVALID_STATUS\"");
	}

	@Test
	void enabledMaterialReferencePreventsUnitDisable() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long unitId = createUnit(admin, "T3_USED_UNIT", "被引用计量单位");
		insertEnabledMaterial(unitId);

		ResponseEntity<String> disableResponse = exchange(HttpMethod.PUT,
				"/api/admin/master/units/" + unitId + "/disable", Map.of(), admin);

		assertThat(disableResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(disableResponse.getBody()).contains("\"code\":\"MASTER_DATA_UNIT_IN_USE\"");
	}

	@Test
	void missingUnitReturnsMasterDataNotFoundForDetailUpdateEnableAndDisable() {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long missingId = 999_999_999L;

		assertMasterDataNotFound(get("/api/admin/master/units/" + missingId, admin));
		assertMasterDataNotFound(exchange(HttpMethod.PUT, "/api/admin/master/units/" + missingId,
				Map.of("code", "T3_MISSING_UNIT", "name", "不存在计量单位", "precisionScale", 2, "sortOrder", 10),
				admin));
		assertMasterDataNotFound(
				exchange(HttpMethod.PUT, "/api/admin/master/units/" + missingId + "/enable", Map.of(), admin));
		assertMasterDataNotFound(
				exchange(HttpMethod.PUT, "/api/admin/master/units/" + missingId + "/disable", Map.of(), admin));
	}

	private void assertResourceLifecycle(AuthenticatedSession admin, String path, long id, String originalCode,
			String targetType, Map<String, Object> update) throws Exception {
		ResponseEntity<String> listResponse = get(path + "?keyword=" + originalCode + "&page=1&pageSize=20", admin);
		assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode page = data(listResponse);
		assertThat(page.get("items").isArray()).isTrue();
		assertThat(page.get("page").intValue()).isEqualTo(1);
		assertThat(page.get("pageSize").intValue()).isEqualTo(20);
		assertThat(page.get("total").longValue()).isEqualTo(1);
		assertThat(page.get("totalPages").intValue()).isEqualTo(1);
		assertThat(listResponse.getBody()).contains("\"code\":\"" + originalCode + "\"");

		ResponseEntity<String> detailResponse = get(path + "/" + id, admin);
		assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(detailResponse).get("id").longValue()).isEqualTo(id);
		assertThat(detailResponse.getBody()).doesNotContain("extra1", "extra2", "extra3");
		assertAuditLog(targetType + "_CREATE", targetType, id, originalCode, "POST", path);

		ResponseEntity<String> updateResponse = exchange(HttpMethod.PUT, path + "/" + id, update, admin);
		assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String updatedCode = update.get("code").toString();
		assertThat(data(updateResponse).get("code").asText()).isEqualTo(updatedCode);
		assertAuditLog(targetType + "_UPDATE", targetType, id, updatedCode, "PUT", path + "/" + id);

		assertThat(exchange(HttpMethod.PUT, path + "/" + id + "/disable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertThat(data(get(path + "/" + id, admin)).get("status").asText()).isEqualTo("DISABLED");
		assertAuditLog(targetType + "_DISABLE", targetType, id, updatedCode, "PUT", path + "/" + id + "/disable");

		assertThat(exchange(HttpMethod.PUT, path + "/" + id + "/enable", Map.of(), admin).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertThat(data(get(path + "/" + id, admin)).get("status").asText()).isEqualTo("ENABLED");
		assertAuditLog(targetType + "_ENABLE", targetType, id, updatedCode, "PUT", path + "/" + id + "/enable");
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

	private void assertForbidden(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");
	}

	private void assertMasterDataNotFound(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"MASTER_DATA_NOT_FOUND\"");
	}

	private long createUnit(AuthenticatedSession admin, String code, String name) throws Exception {
		return createResource(admin, "/api/admin/master/units",
				Map.of("code", code, "name", name, "precisionScale", 2, "sortOrder", 10, "status", "ENABLED",
						"remark", "计量单位备注"));
	}

	private long createResource(AuthenticatedSession admin, String path, Map<String, Object> request) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, path, request, admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode data = data(response);
		assertThat(data.get("code").asText()).isEqualTo(request.get("code"));
		assertThat(data.get("status").asText()).isEqualTo(request.getOrDefault("status", "ENABLED"));
		return data.get("id").longValue();
	}

	private void createUser(String username, AuthenticatedSession session) {
		createUser(username, List.of(), session);
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

	private void insertEnabledMaterial(long unitId) {
		Long categoryId = this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (
					code, name, status, sort_order, remark, created_by, created_at, updated_by, updated_at
				)
				values ('T3_UNIT_IN_USE_CATEGORY', '引用计量单位分类', 'ENABLED', 10, null, 'test', now(), 'test', now())
				returning id
				""", Long.class);
		this.jdbcTemplate.update("""
				insert into mst_material (
					code, name, specification, material_type, source_type, category_id, unit_id, status,
					remark, created_by, created_at, updated_by, updated_at
				)
				values (
					'T3_UNIT_IN_USE_MATERIAL', '引用计量单位物料', '测试规格', 'RAW_MATERIAL', 'PURCHASED',
					?, ?, 'ENABLED', null, 'test', now(), 'test', now()
				)
				""", categoryId, unitId);
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

	private record ResourceRequest(String path, Map<String, Object> body) {
	}

}
