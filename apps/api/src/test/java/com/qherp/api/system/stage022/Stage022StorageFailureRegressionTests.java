package com.qherp.api.system.stage022;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=stage022-storage-failure-regression")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Stage022StorageFailureRegressionTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Container
	static final GenericContainer<?> minio = new GenericContainer<>(
			DockerImageName.parse("minio/minio:RELEASE.2024-01-16T16-07-38Z"))
		.withEnv("MINIO_ROOT_USER", "qherpminio")
		.withEnv("MINIO_ROOT_PASSWORD", "qherpminio123")
		.withCommand("server /data --console-address :9001")
		.withExposedPorts(9000)
		.waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).withStartupTimeout(Duration.ofSeconds(90)));

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("qherp.storage.s3.endpoint", () -> "http://" + minio.getHost() + ":"
				+ minio.getMappedPort(9000));
		registry.add("qherp.storage.s3.region", () -> "us-east-1");
		registry.add("qherp.storage.s3.bucket", () -> "qherp-test-private-failure");
		registry.add("qherp.storage.s3.access-key", () -> "qherpminio");
		registry.add("qherp.storage.s3.secret-key", () -> "qherpminio123");
		registry.add("qherp.storage.s3.path-style", () -> "true");
	}

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void minioOutageDoesNotMarkAttachmentMetadataDeletedOnDownloadOrDeleteFailure() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long customerId = insertCustomer("S22_STORE_CUS_" + SEQUENCE.incrementAndGet(), "二十二存储故障客户");
		long projectId = data(createProject(admin, projectPayload(customerId, userId("admin")))).get("id").longValue();
		long contractId = data(createContract(admin, projectId, contractPayload())).get("id").longValue();
		JsonNode uploaded = data(uploadAttachment(admin, contractId, "storage-failure-" + contractId));
		long attachmentId = uploaded.get("id").longValue();
		assertThat(attachmentStatus(attachmentId)).isEqualTo("AVAILABLE");
		assertThat(fileStatus(attachmentId)).isEqualTo("AVAILABLE");
		long fileCountBeforeOutageUpload = fileObjectCount();
		long attachmentCountBeforeOutageUpload = attachmentCount(contractId);

		minio.stop();

		assertError(uploadAttachment(admin, contractId, "storage-failure-upload-" + contractId,
				"MinIO 停服上传新内容".getBytes(), "storage-failure-upload.txt"),
				HttpStatus.SERVICE_UNAVAILABLE, "FILE_STORAGE_UNAVAILABLE");
		assertThat(fileObjectCount()).isEqualTo(fileCountBeforeOutageUpload);
		assertThat(attachmentCount(contractId)).isEqualTo(attachmentCountBeforeOutageUpload);

		assertError(getString(admin, "/api/admin/attachments/" + attachmentId + "/download"),
				HttpStatus.SERVICE_UNAVAILABLE, "FILE_STORAGE_UNAVAILABLE");
		assertThat(attachmentStatus(attachmentId)).isEqualTo("AVAILABLE");
		assertThat(fileStatus(attachmentId)).isEqualTo("AVAILABLE");

		assertError(put(admin, "/api/admin/attachments/" + attachmentId + "/delete",
				Map.of("version", uploaded.get("version").longValue(), "reason", "MinIO 故障删除")),
				HttpStatus.SERVICE_UNAVAILABLE, "FILE_STORAGE_UNAVAILABLE");
		assertThat(attachmentStatus(attachmentId)).isEqualTo("AVAILABLE");
		assertThat(fileStatus(attachmentId)).isEqualTo("AVAILABLE");
	}

	private ResponseEntity<String> uploadAttachment(AuthenticatedSession session, long contractId,
			String idempotencyKey) {
		return uploadAttachment(session, contractId, idempotencyKey, "MinIO 故障一致性".getBytes(),
				"storage-failure.txt");
	}

	private ResponseEntity<String> uploadAttachment(AuthenticatedSession session, long contractId,
			String idempotencyKey, byte[] content, String filename) {
		org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
		body.add("objectType", "SALES_PROJECT_CONTRACT");
		body.add("objectId", Long.toString(contractId));
		body.add("description", "存储故障附件");
		body.add("file", new ByteArrayResource(content) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		HttpHeaders headers = headers(session);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.add("Idempotency-Key", idempotencyKey);
		return this.restTemplate.exchange("/api/admin/attachments", HttpMethod.POST, new HttpEntity<>(body, headers),
				String.class);
	}

	private ResponseEntity<String> createProject(AuthenticatedSession session, Map<String, Object> body) {
		return exchange(session, HttpMethod.POST, "/api/admin/sales-projects", body);
	}

	private ResponseEntity<String> createContract(AuthenticatedSession session, long projectId, Map<String, Object> body) {
		return exchange(session, HttpMethod.POST, "/api/admin/sales-projects/" + projectId + "/contracts", body);
	}

	private ResponseEntity<String> put(AuthenticatedSession session, String path, Map<String, Object> body) {
		return exchange(session, HttpMethod.PUT, path, body);
	}

	private ResponseEntity<String> getString(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers(session)), String.class);
	}

	private ResponseEntity<String> exchange(AuthenticatedSession session, HttpMethod method, String path, Object body) {
		return this.restTemplate.exchange(path, method, new HttpEntity<>(body, headers(session)), String.class);
	}

	private Map<String, Object> projectPayload(long customerId, long ownerUserId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("name", "022 存储故障项目 " + SEQUENCE.incrementAndGet());
		payload.put("customerId", customerId);
		payload.put("ownerUserId", ownerUserId);
		payload.put("plannedStartDate", LocalDate.now().toString());
		payload.put("plannedFinishDate", LocalDate.now().plusDays(30).toString());
		payload.put("targetRevenue", "100000.00");
		payload.put("targetCost", "60000.00");
		payload.put("remark", "022 存储故障项目");
		return payload;
	}

	private Map<String, Object> contractPayload() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("contractType", "MAIN");
		payload.put("name", "存储故障合同");
		payload.put("signedDate", LocalDate.now().toString());
		payload.put("effectiveStartDate", LocalDate.now().toString());
		payload.put("effectiveEndDate", LocalDate.now().plusDays(60).toString());
		payload.put("amount", "1000.00");
		payload.put("remark", "022 存储故障合同");
		return payload;
	}

	private long insertCustomer(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long userId(String username) {
		return this.jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
	}

	private String attachmentStatus(long attachmentId) {
		return this.jdbcTemplate.queryForObject("select status from platform_business_attachment where id = ?",
				String.class, attachmentId);
	}

	private String fileStatus(long attachmentId) {
		return this.jdbcTemplate.queryForObject("""
				select f.status
				from platform_business_attachment a
				join platform_file_object f on f.id = a.file_id
				where a.id = ?
				""", String.class, attachmentId);
	}

	private long fileObjectCount() {
		return this.jdbcTemplate.queryForObject("select count(*) from platform_file_object", Long.class);
	}

	private long attachmentCount(long contractId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_business_attachment
				where object_type = 'SALES_PROJECT_CONTRACT'
				  and object_id = ?
				""", Long.class, contractId);
	}

	private AuthenticatedSession login(String username, String password) {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				new HttpEntity<>(Map.of("username", username, "password", password), headers(csrf.sessionCookie(),
						csrf.headerName(), csrf.token())),
				String.class);
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

	private HttpHeaders headers(AuthenticatedSession session) {
		return headers(session.sessionCookie(), session.csrf().headerName(), session.csrf().token());
	}

	private HttpHeaders headers(String cookie, String csrfHeaderName, String csrfToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, cookie);
		headers.add(csrfHeaderName, csrfToken);
		return headers;
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(code);
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

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrf) {
	}

}
