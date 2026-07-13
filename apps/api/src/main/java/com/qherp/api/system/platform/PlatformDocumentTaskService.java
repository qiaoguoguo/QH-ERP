package com.qherp.api.system.platform;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.bom.BomAdminService;
import com.qherp.api.system.master.CodingRuleAdminService;
import com.qherp.api.system.master.MaterialAdminService;
import com.qherp.api.system.user.SystemUserStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PlatformDocumentTaskService {

	private static final AtomicInteger TASK_SEQUENCE = new AtomicInteger();

	private static final DateTimeFormatter TASK_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	static {
		ZipSecureFile.setMinInflateRatio(0.01d);
		ZipSecureFile.setMaxEntrySize(20L * 1024L * 1024L);
		ZipSecureFile.setMaxTextSize(10L * 1024L * 1024L);
	}

	private final JdbcTemplate jdbcTemplate;

	private final PlatformStorageService storageService;

	private final AuditService auditService;

	private final MaterialAdminService materialAdminService;

	private final BomAdminService bomAdminService;

	private final CodingRuleAdminService codingRuleAdminService;

	private final ObjectMapper objectMapper;

	public PlatformDocumentTaskService(JdbcTemplate jdbcTemplate, PlatformStorageService storageService,
			AuditService auditService, MaterialAdminService materialAdminService, BomAdminService bomAdminService,
			CodingRuleAdminService codingRuleAdminService, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.storageService = storageService;
		this.auditService = auditService;
		this.materialAdminService = materialAdminService;
		this.bomAdminService = bomAdminService;
		this.codingRuleAdminService = codingRuleAdminService;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public DocumentTaskRecord exportMaterials(MaterialExportRequest request, String idempotencyKey,
			CurrentUser operator, HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		String payloadJson = json(request == null ? new MaterialExportRequest(null, null, null, null, null, null) : request);
		List<ExistingTask> existing = this.jdbcTemplate.query("""
				select id, request_payload::text as request_payload
				from platform_document_task
				where created_by_user_id = ?
				and task_type = 'MATERIAL_EXPORT'
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingTask(rs.getLong("id"), rs.getString("request_payload")),
				operator.id(), idempotencyKey);
		if (!existing.isEmpty()) {
			ExistingTask existingTask = existing.getFirst();
			if (!jsonEquivalent(payloadJson, existingTask.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(existingTask.id(), operator);
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long taskId = this.jdbcTemplate.queryForObject("""
					insert into platform_document_task (
						task_no, task_type, stage, status, request_payload, idempotency_key, created_by_user_id,
						created_by_username, next_run_at, created_at
					)
					values (?, 'MATERIAL_EXPORT', 'EXPORT', 'QUEUED', cast(? as jsonb), ?, ?, ?, ?, ?)
					returning id
					""", Long.class, nextTaskNo("MEXP"), payloadJson, idempotencyKey, operator.id(),
					operator.username(), now, now);
			this.auditService.record(operator, "DOCUMENT_TASK_CREATE", "DOCUMENT_TASK", taskId, "MATERIAL_EXPORT",
					servletRequest);
			return get(taskId, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	@Transactional
	public DocumentTaskRecord importMaterials(MultipartFile file, String idempotencyKey, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return createImportTask("MATERIAL_IMPORT", "materials", file, idempotencyKey, operator, servletRequest);
	}

	@Transactional
	public DocumentTaskRecord importBomDrafts(MultipartFile file, String idempotencyKey, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return createImportTask("BOM_DRAFT_IMPORT", "bom-drafts", file, idempotencyKey, operator, servletRequest);
	}

	@Transactional
	public DocumentTaskRecord confirmImport(Long taskId, ConfirmImportRequest request, String idempotencyKey,
			CurrentUser operator, HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		DocumentTaskState task = task(taskId);
		requireTaskAccess(task, operator);
		if (!task.taskType().endsWith("_IMPORT")) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		String existingKey = this.jdbcTemplate.queryForObject("""
				select commit_idempotency_key
				from platform_document_task
				where id = ?
				""", String.class, taskId);
		if (hasText(existingKey)) {
			if (!existingKey.equals(idempotencyKey)) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(taskId, operator);
		}
		if (request == null || request.version() == null || !request.version().equals(task.version())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_CONCURRENT_MODIFICATION);
		}
		if (!"READY_TO_COMMIT".equals(task.status())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		int updated = this.jdbcTemplate.update("""
				update platform_document_task
				set stage = 'COMMIT', status = 'QUEUED', commit_idempotency_key = ?, commit_requested_at = now(),
				    next_run_at = now(), updated_at = now(), version = version + 1
				where id = ?
				and version = ?
				and status = 'READY_TO_COMMIT'
				and commit_idempotency_key is null
				""", idempotencyKey, taskId, task.version());
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_CONCURRENT_MODIFICATION);
		}
		this.jdbcTemplate.update("""
				update platform_import_batch
				set status = 'COMMITTING', commit_idempotency_key = ?, updated_at = now(), version = version + 1
				where task_id = ?
				""", idempotencyKey, taskId);
		this.auditService.record(operator, "IMPORT_CONFIRM", "DOCUMENT_TASK", taskId, task.taskType(), servletRequest);
		return get(taskId, operator);
	}

	@Transactional
	public DocumentTaskRecord exportBomDraft(Long bomId, String idempotencyKey, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		BomAdminService.BomDetailResponse bom = this.bomAdminService.get(bomId);
		if (!"DRAFT".equals(bom.status())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		String payloadJson = json(new BomDraftExportRequest(bomId, bom.version()));
		List<ExistingTask> existing = existingTask(operator.id(), "BOM_DRAFT_EXPORT", idempotencyKey);
		if (!existing.isEmpty()) {
			ExistingTask existingTask = existing.getFirst();
			if (!jsonEquivalent(payloadJson, existingTask.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(existingTask.id(), operator);
		}
		try {
			Long taskId = insertQueuedTask("BOM_DRAFT_EXPORT", "EXPORT", payloadJson, idempotencyKey, null, operator);
			this.auditService.record(operator, "DOCUMENT_TASK_CREATE", "DOCUMENT_TASK", taskId, "BOM_DRAFT_EXPORT",
					servletRequest);
			return get(taskId, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	@Transactional(readOnly = true)
	public List<PrintTemplateRecord> printTemplates(String sceneCode) {
		String where = hasText(sceneCode) ? "where scene_code = ? and status = 'ENABLED'" : "where status = 'ENABLED'";
		Object[] args = hasText(sceneCode) ? new Object[] { sceneCode } : new Object[] {};
		return this.jdbcTemplate.query("""
				select template_code, scene_code, name, object_type, template_version
				from platform_print_template
				%s
				order by id
				""".formatted(where), (rs, rowNum) -> new PrintTemplateRecord(rs.getString("template_code"),
				rs.getString("scene_code"), rs.getString("name"), rs.getString("object_type"),
				rs.getInt("template_version")), args);
	}

	@Transactional(readOnly = true)
	public PrintPreviewRecord printPreview(Long approvalInstanceId, CurrentUser operator) {
		ApprovalPrintSnapshot snapshot = approvalPrintSnapshot(approvalInstanceId);
		requireApprovalBusinessView(snapshot.sceneCode(), operator);
		return new PrintPreviewRecord(printTemplateCode(snapshot.sceneCode()), 1, snapshot.sceneCode(),
				snapshot.businessObjectType(), snapshot.businessObjectNo(), snapshot.businessObjectSummary(),
				snapshot.status(), snapshot.submittedByUsername(), snapshot.submittedAt(),
				snapshot.completedByUsername(), snapshot.completedAt(), List.of(
						new PrintPreviewSection("审批信息", List.of(
								new PrintPreviewField("审批场景", snapshot.sceneCode()),
								new PrintPreviewField("审批状态", snapshot.status()),
								new PrintPreviewField("提交人", snapshot.submittedByUsername()),
								new PrintPreviewField("提交时间", snapshot.submittedAt().toString()))),
						new PrintPreviewSection("业务对象", List.of(
								new PrintPreviewField("对象类型", snapshot.businessObjectType()),
								new PrintPreviewField("对象编号", snapshot.businessObjectNo()),
								new PrintPreviewField("对象摘要", snapshot.businessObjectSummary())))));
	}

	@Transactional
	public DocumentTaskRecord createPrintTask(PrintTaskRequest request, String idempotencyKey, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		if (request == null || request.approvalInstanceId() == null || !hasText(request.templateCode())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ApprovalPrintSnapshot snapshot = approvalPrintSnapshot(request.approvalInstanceId());
		requireApprovalBusinessView(snapshot.sceneCode(), operator);
		String expectedTemplate = printTemplateCode(snapshot.sceneCode());
		if (!expectedTemplate.equals(request.templateCode())) {
			throw new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED);
		}
		String payloadJson = json(new PrintTaskPayload(request.approvalInstanceId(), request.templateCode(),
				snapshot.version(), snapshot.businessObjectVersion()));
		List<ExistingTask> existing = existingTask(operator.id(), "APPROVAL_PRINT", idempotencyKey);
		if (!existing.isEmpty()) {
			ExistingTask existingTask = existing.getFirst();
			if (!jsonEquivalent(payloadJson, existingTask.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(existingTask.id(), operator);
		}
		try {
			Long taskId = insertQueuedTask("APPROVAL_PRINT", "PRINT", payloadJson, idempotencyKey, null, operator);
			this.auditService.record(operator, "PRINT_TASK_CREATE", "DOCUMENT_TASK", taskId, request.templateCode(),
					servletRequest);
			return get(taskId, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<TaskErrorRecord> errors(Long taskId, int page, int pageSize, CurrentUser currentUser) {
		DocumentTaskState task = task(taskId);
		requireTaskAccess(task, currentUser);
		long total = this.jdbcTemplate.queryForObject("select count(*) from platform_document_task_error where task_id = ?",
				Long.class, task.id());
		List<TaskErrorRecord> items = this.jdbcTemplate.query("""
				select row_no, column_name, error_code, message
				from platform_document_task_error
				where task_id = ?
				order by row_no nulls first, id
				limit ? offset ?
				""", (rs, rowNum) -> new TaskErrorRecord(nullableInteger(rs, "row_no"), rs.getString("column_name"),
				rs.getString("error_code"), rs.getString("message")), task.id(), limit(pageSize), offset(page, pageSize));
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	private DocumentTaskRecord createImportTask(String taskType, String folder, MultipartFile file, String idempotencyKey,
			CurrentUser operator, HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		byte[] content = readXlsx(file, taskType);
		String payloadJson = json(new ImportTaskPayload(null, file.getOriginalFilename(), sha256(content)));
		List<ExistingTask> existing = existingTask(operator.id(), taskType, idempotencyKey);
		if (!existing.isEmpty()) {
			ExistingTask existingTask = existing.getFirst();
			if (!importPayloadEquivalent(payloadJson, existingTask.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(existingTask.id(), operator);
		}
		PlatformStorageService.StoredObject storedObject = this.storageService.put(
				"imports/" + folder + "/" + UUID.randomUUID() + ".xlsx", content,
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		Long fileId = insertFile(storedObject, safeFilename(file.getOriginalFilename()),
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content, "IMPORT_SOURCE",
				operator);
		payloadJson = json(new ImportTaskPayload(fileId, file.getOriginalFilename(), sha256(content)));
		try {
			Long taskId = insertQueuedTask(taskType, "VALIDATE", payloadJson, idempotencyKey, fileId, operator);
			this.jdbcTemplate.update("""
					insert into platform_import_batch (
						task_id, import_type, source_file_id, source_sha256, status, created_at, updated_at
					)
					values (?, ?, ?, ?, 'QUEUED', now(), now())
					""", taskId, taskType, fileId, sha256(content));
			this.auditService.record(operator, "IMPORT_UPLOAD", "DOCUMENT_TASK", taskId, taskType, servletRequest);
			return get(taskId, operator);
		}
		catch (DuplicateKeyException exception) {
			this.storageService.deleteQuietly(storedObject.objectKey());
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<DocumentTaskRecord> list(int page, int pageSize, CurrentUser currentUser) {
		boolean viewAll = currentUser.permissions().contains("platform:document-task:view-all");
		String where = viewAll ? "" : "where created_by_user_id = ?";
		Object[] args = viewAll ? new Object[] {} : new Object[] { currentUser.id() };
		List<DocumentTaskState> visible = this.jdbcTemplate.query("""
				select id, task_no, task_type, stage, status, idempotency_key, created_by_user_id,
				       created_by_username, total_count, success_count, error_count, result_file_id,
				       error_file_id, error_summary, attempt_count, max_attempts, created_at, started_at,
				       finished_at, expires_at, version
				from platform_document_task
				%s
				order by created_at desc, id desc
				""".formatted(where), this::mapTask, args)
			.stream()
			.filter((task) -> canAccessTask(task, currentUser))
			.toList();
		int from = Math.min(offset(page, pageSize), visible.size());
		int to = Math.min(from + limit(pageSize), visible.size());
		return PageResponse.of(visible.subList(from, to).stream().map(this::toDocumentTaskRecord).toList(), page,
				limit(pageSize), visible.size());
	}

	@Transactional(readOnly = true)
	public DocumentTaskRecord get(Long id, CurrentUser currentUser) {
		DocumentTaskState task = task(id);
		requireTaskAccess(task, currentUser);
		return toDocumentTaskRecord(task);
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public DownloadedFile download(Long id, CurrentUser currentUser) {
		DocumentTaskState task = task(id);
		requireTaskAccess(task, currentUser);
		if (!"SUCCEEDED".equals(task.status()) || task.resultFileId() == null) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		if (task.expiresAt() != null && task.expiresAt().isBefore(OffsetDateTime.now())) {
			markTaskExpired(task);
			throw new BusinessException(ApiErrorCode.DOCUMENT_RESULT_EXPIRED);
		}
		DocumentTaskFile file = this.jdbcTemplate.query("""
				select object_key, original_filename, content_type
				from platform_file_object
				where id = ?
				and status = 'AVAILABLE'
				""", (rs, rowNum) -> new DocumentTaskFile(rs.getString("object_key"),
				rs.getString("original_filename"), rs.getString("content_type")), task.resultFileId())
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID));
		return new DownloadedFile(file.originalFilename(), file.contentType(), this.storageService.get(file.objectKey()));
	}

	private void markTaskExpired(DocumentTaskState task) {
		int updated = this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'EXPIRED', updated_at = now(), version = version + 1
				where id = ?
				and status = 'SUCCEEDED'
				and expires_at < now()
				""", task.id());
		if (updated > 0 && task.resultFileId() != null) {
			this.jdbcTemplate.update("""
					update platform_file_object
					set status = 'EXPIRED', deleted_at = now(), version = version + 1
					where id = ?
					and status = 'AVAILABLE'
					""", task.resultFileId());
			createMessage(task.createdByUserId(), "文档结果已过期", task.taskNo(), "DOCUMENT_TASK_EXPIRED", task.id());
		}
	}

	@Transactional
	public ClaimedTask claimNext(String workerId, OffsetDateTime leaseUntil) {
		List<ClaimedTask> candidates = this.jdbcTemplate.query("""
				select id, task_no, task_type, stage, request_payload::text as request_payload, created_by_user_id,
				       created_by_username, attempt_count, max_attempts, version
				from platform_document_task
				where (
					status = 'QUEUED'
					and (next_run_at is null or next_run_at <= now())
				) or (
					status = 'RUNNING'
					and lease_until < now()
				)
				order by created_at, id
				limit 1
				for update skip locked
				""", this::mapClaimedTask);
		if (candidates.isEmpty()) {
			return null;
		}
		ClaimedTask task = candidates.getFirst();
		if (task.attemptCount() >= task.maxAttempts()) {
			this.jdbcTemplate.update("""
					update platform_document_task
					set status = 'FAILED', error_summary = '超过最大重试次数', finished_at = now(),
					    lease_owner = null, lease_until = null, updated_at = now(), version = version + 1
					where id = ?
					""", task.id());
			return null;
		}
		int updated = this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'RUNNING', lease_owner = ?, lease_until = ?, heartbeat_at = now(),
				    started_at = coalesce(started_at, now()), attempt_count = attempt_count + 1,
				    updated_at = now(), version = version + 1
				where id = ? and version = ?
				""", workerId, leaseUntil, task.id(), task.version());
		if (updated == 0) {
			return null;
		}
		ClaimedTask claimed = task.withAttempt(task.attemptCount() + 1);
		recordWorkerAudit(claimed, "DOCUMENT_TASK_CLAIM", workerId);
		return claimed;
	}

	@Transactional
	public void heartbeat(Long taskId, String workerId, OffsetDateTime leaseUntil) {
		this.jdbcTemplate.update("""
				update platform_document_task
				set heartbeat_at = now(), lease_until = ?, updated_at = now(), version = version + 1
				where id = ?
				and status = 'RUNNING'
				and lease_owner = ?
				""", leaseUntil, taskId, workerId);
	}

	@Transactional
	public void completeExport(ClaimedTask task, ExportedFile exportedFile, CurrentUser operator) {
		completeResult(task, exportedFile, operator, "exports/materials/" + UUID.randomUUID() + ".xlsx",
				"DOCUMENT_TASK_EXPORT_MATERIALS");
	}

	@Transactional
	public void completeResult(ClaimedTask task, ExportedFile exportedFile, CurrentUser operator, String objectKey,
			String auditAction) {
		PlatformStorageService.StoredObject storedObject = this.storageService.put(
				objectKey, exportedFile.content(), exportedFile.contentType());
		Long fileId = insertFile(storedObject, exportedFile.filename(), exportedFile.contentType(),
				exportedFile.content(), "EXPORT", operator);
		OffsetDateTime finishedAt = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'SUCCEEDED', result_file_id = ?, total_count = ?, success_count = ?,
				    error_count = 0, finished_at = ?, expires_at = ?, lease_owner = null, lease_until = null,
				    updated_at = now(), version = version + 1
				where id = ? and status = 'RUNNING'
				""", fileId, exportedFile.totalCount(), exportedFile.totalCount(), finishedAt, finishedAt.plusDays(7),
				task.id());
		this.auditService.record(operator, auditAction, "DOCUMENT_TASK", task.id(), exportedFile.filename(), null);
		createMessage(task.createdByUserId(), "导出任务已完成", exportedFile.filename(), "DOCUMENT_TASK_SUCCEEDED",
				task.id());
	}

	@Transactional
	public void validateImport(ClaimedTask task, CurrentUser operator) {
		if ("MATERIAL_IMPORT".equals(task.taskType())) {
			validateMaterialImport(task);
			return;
		}
		if ("BOM_DRAFT_IMPORT".equals(task.taskType())) {
			validateBomDraftImport(task);
			return;
		}
		throw new IllegalStateException("不支持的导入任务：" + task.taskType());
	}

	@Transactional
	public void commitImport(ClaimedTask task, CurrentUser operator) {
		if ("MATERIAL_IMPORT".equals(task.taskType())) {
			commitMaterialImport(task, operator);
			return;
		}
		if ("BOM_DRAFT_IMPORT".equals(task.taskType())) {
			commitBomDraftImport(task, operator);
			return;
		}
		throw new IllegalStateException("不支持的导入提交任务：" + task.taskType());
	}

	public ExportedFile bomDraftExportFile(BomDraftExportRequest request) {
		BomAdminService.BomDetailResponse bom = this.bomAdminService.get(request.bomId());
		if (!"DRAFT".equals(bom.status()) || !bom.version().equals(request.version())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet headerSheet = workbook.createSheet("bom");
			String[] headerNames = { "mode", "bomId", "version", "bomCode", "parentMaterialCode", "versionCode",
					"name", "baseQuantity", "baseUnit", "effectiveFrom", "effectiveTo", "remark" };
			writeRow(headerSheet.createRow(0), headerNames);
			writeRow(headerSheet.createRow(1), new String[] { "UPDATE_DRAFT", bom.id().toString(),
					bom.version().toString(), bom.bomCode(), bom.parentMaterialCode(), bom.versionCode(), bom.name(),
					bom.baseQuantity(), unitCode(bom.baseUnitId()), stringDate(bom.effectiveFrom()), stringDate(bom.effectiveTo()),
					nullToBlank(bom.remark()) });
			Sheet itemsSheet = workbook.createSheet("items");
			writeRow(itemsSheet.createRow(0), new String[] { "lineNo", "childMaterialCode", "businessUnit",
					"businessQuantity", "lossRate", "warehouse", "remark" });
			for (int i = 0; i < bom.items().size(); i++) {
				BomAdminService.BomItemResponse item = bom.items().get(i);
				writeRow(itemsSheet.createRow(i + 1), new String[] { item.lineNo().toString(),
						item.childMaterialCode(), unitCode(item.businessUnitId()), item.businessQuantity(), item.lossRate(), "",
						nullToBlank(item.remark()) });
			}
			workbook.write(output);
			return new ExportedFile("bom-draft-" + bom.bomCode() + ".xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray(),
					bom.items().size());
		}
			catch (IOException exception) {
				throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
			}
		}

	public ExportedFile printApprovalFile(PrintTaskPayload payload) {
		ApprovalPrintSnapshot snapshot = approvalPrintSnapshot(payload.approvalInstanceId());
		if (!snapshot.version().equals(payload.approvalInstanceVersion())
				|| !snapshot.businessObjectVersion().equals(payload.businessObjectVersion())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_BUSINESS_OBJECT_CHANGED);
		}
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			String templateName = printTemplateName(payload.templateCode());
			document.getDocumentInformation().setTitle(templateName);
			document.getDocumentInformation().setSubject(payload.templateCode());
			document.getDocumentInformation().setCustomMetadataValue("templateVersion", "1");
			document.getDocumentInformation().setCustomMetadataValue("approvalInstanceId",
					Long.toString(payload.approvalInstanceId()));
			document.getDocumentInformation().setCustomMetadataValue("approvalInstanceVersion",
					Long.toString(payload.approvalInstanceVersion()));
			document.getDocumentInformation().setCustomMetadataValue("businessObjectVersion",
					Long.toString(payload.businessObjectVersion()));
			PDType0Font font = PDType0Font.load(document,
					new ClassPathResource("fonts/NotoSansSC-wght.ttf").getInputStream());
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(font, 14);
				content.setLeading(22);
				content.newLineAtOffset(50, 740);
				for (String line : List.of(templateName, "模板代码：" + payload.templateCode(), "模板版本：1",
						"审批场景：" + snapshot.sceneCode(), "业务对象：" + snapshot.businessObjectNo(),
						"对象摘要：" + snapshot.businessObjectSummary(), "审批状态：" + snapshot.status(),
						"提交人：" + snapshot.submittedByUsername(), "提交时间：" + snapshot.submittedAt(),
						"完成人：" + nullToBlank(snapshot.completedByUsername()),
						"完成时间：" + (snapshot.completedAt() == null ? "" : snapshot.completedAt().toString()))) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			document.save(output);
			return new ExportedFile("approval-" + payload.approvalInstanceId() + ".pdf", "application/pdf",
					output.toByteArray(), 1);
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
		}
	}

	private String printTemplateName(String templateCode) {
		return switch (templateCode) {
			case "CONTRACT_ACTIVATION_APPROVAL_V1" -> "合同生效审批单";
			case "BOM_ECO_APPLICATION_APPROVAL_V1" -> "BOM ECO 应用审批单";
			default -> throw new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED);
		};
	}

	@Transactional
	public void failAttempt(ClaimedTask task, RuntimeException exception) {
		boolean exhausted = task.attemptCount() >= task.maxAttempts();
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = ?, error_summary = ?, lease_owner = null, lease_until = null, heartbeat_at = now(),
				    next_run_at = ?, finished_at = case when ? then now() else finished_at end,
				    updated_at = now(), version = version + 1
				where id = ?
				""", exhausted ? "FAILED" : "QUEUED", safeError(exception), exhausted ? null : OffsetDateTime.now().plusSeconds(1),
				exhausted, task.id());
		recordWorkerAudit(task, exhausted ? "DOCUMENT_TASK_FAILED" : "DOCUMENT_TASK_RETRY", "worker");
		if (exhausted) {
			createMessage(task.createdByUserId(), "文档任务失败", safeError(exception), "DOCUMENT_TASK_FAILED",
					task.id());
		}
	}

	@Transactional
	public DocumentTaskRecord cancel(Long id, CancelTaskRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		DocumentTaskState task = task(id);
		requireTaskAccess(task, operator);
		if (request == null || request.version() == null || !request.version().equals(task.version())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_CONCURRENT_MODIFICATION);
		}
		if (!"QUEUED".equals(task.status()) && !"RUNNING".equals(task.status())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		int updated = this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'CANCELLED', error_summary = ?, finished_at = now(), updated_at = now(),
				    version = version + 1
				where id = ? and version = ?
				""", trimToNull(request.reason()), id, task.version());
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_CONCURRENT_MODIFICATION);
		}
		this.auditService.record(operator, "DOCUMENT_TASK_CANCEL", "DOCUMENT_TASK", id, request.reason(),
				servletRequest);
		return get(id, operator);
	}

	public ExportedFile materialExportFile(MaterialExportRequest request) {
		String keyword = request == null ? null : trimToNull(request.keyword());
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		if (keyword != null) {
			conditions.add("(m.code ilike ? or m.name ilike ?)");
			args.add("%" + keyword + "%");
			args.add("%" + keyword + "%");
		}
		if (request != null && hasText(request.status())) {
			conditions.add("m.status = ?");
			args.add(request.status());
		}
		if (request != null && request.categoryId() != null) {
			conditions.add("m.category_id = ?");
			args.add(request.categoryId());
		}
		if (request != null && hasText(request.materialType())) {
			conditions.add("m.material_type = ?");
			args.add(request.materialType());
		}
		if (request != null && hasText(request.sourceType())) {
			conditions.add("m.source_type = ?");
			args.add(request.sourceType());
		}
		if (request != null && hasText(request.trackingMethod())) {
			conditions.add("m.tracking_method = ?");
			args.add(request.trackingMethod());
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		List<MaterialExportRow> rows = this.jdbcTemplate.query("""
				select m.code, m.name, m.specification, m.material_type, m.source_type, m.tracking_method,
				       c.name as category_name, u.name as unit_name, m.status, m.cost_category,
				       m.inventory_valuation_category, m.inventory_value_enabled, m.project_cost_enabled
				from mst_material m
				left join mst_material_category c on c.id = m.category_id
				left join mst_unit u on u.id = m.unit_id
				%s
				order by m.id
				""".formatted(where), (rs, rowNum) -> new MaterialExportRow(rs.getString("code"),
				rs.getString("name"), rs.getString("specification"), rs.getString("material_type"),
				rs.getString("source_type"), rs.getString("tracking_method"), rs.getString("category_name"),
				rs.getString("unit_name"), rs.getString("status"), rs.getString("cost_category"),
				rs.getString("inventory_valuation_category"), rs.getBoolean("inventory_value_enabled"),
				rs.getBoolean("project_cost_enabled")), args.toArray());
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("物料");
			String[] headers = { "编码", "名称", "规格", "物料类型", "来源类型", "追踪方式", "分类", "单位", "状态",
					"成本分类", "库存计价分类", "库存计价", "项目成本" };
			Row header = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				header.createCell(i).setCellValue(headers[i]);
			}
			for (int i = 0; i < rows.size(); i++) {
				MaterialExportRow row = rows.get(i);
				Row excelRow = sheet.createRow(i + 1);
				excelRow.createCell(0).setCellValue(row.code());
				excelRow.createCell(1).setCellValue(row.name());
				excelRow.createCell(2).setCellValue(nullToBlank(row.specification()));
				excelRow.createCell(3).setCellValue(row.materialType());
				excelRow.createCell(4).setCellValue(row.sourceType());
				excelRow.createCell(5).setCellValue(row.trackingMethod());
				excelRow.createCell(6).setCellValue(nullToBlank(row.categoryName()));
				excelRow.createCell(7).setCellValue(nullToBlank(row.unitName()));
				excelRow.createCell(8).setCellValue(row.status());
				excelRow.createCell(9).setCellValue(row.costCategory());
				excelRow.createCell(10).setCellValue(row.inventoryValuationCategory());
				excelRow.createCell(11).setCellValue(row.inventoryValueEnabled());
				excelRow.createCell(12).setCellValue(row.projectCostEnabled());
			}
			workbook.write(output);
			return new ExportedFile("materials-" + OffsetDateTime.now().format(TASK_NO_FORMATTER) + ".xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray(),
					rows.size());
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
		}
	}

	private void validateMaterialImport(ClaimedTask task) {
		ImportTaskPayload payload = parseImportTaskPayload(task.requestPayload());
		byte[] content = sourceFileContent(payload.sourceFileId());
		Long batchId = batchId(task.id());
		clearImportRowsAndErrors(task.id(), batchId);
		List<ImportError> errors = new ArrayList<>();
		int rowCount = 0;
		try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
			validateWorkbookSheets(workbook, List.of("materials"));
			Sheet sheet = workbook.getSheet("materials");
			validateHeader(sheet, new String[] { "code", "name", "specification", "materialType", "sourceType",
					"trackingMethod", "categoryCode", "unitCode", "status", "costCategory",
					"inventoryValuationCategory", "inventoryValueEnabled", "projectCostEnabled", "costRemark",
					"remark" });
			validateVisibleColumns(sheet, 15);
			validateVisibleRows(sheet, 1);
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null || rowIsBlank(row)) {
					continue;
				}
				rowCount++;
				if (rowCount > 10000) {
					throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
				}
				MaterialImportRow importRow = new MaterialImportRow(cellString(row, 0), cellString(row, 1),
						cellString(row, 2), cellString(row, 3), cellString(row, 4), cellString(row, 5),
						cellString(row, 6), cellString(row, 7), cellString(row, 8), cellString(row, 9),
						cellString(row, 10), cellString(row, 11), cellString(row, 12), cellString(row, 13),
						cellString(row, 14));
				validateMaterialImportRow(i + 1, importRow, errors);
				insertImportRow(batchId, i + 1, importRow);
			}
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		catch (BusinessException exception) {
			recordImportErrors(task.id(), batchId, List.of(new ImportError(null, "file",
					exception.errorCode().name(), exception.getMessage())));
			markValidationFailed(task.id(), batchId, rowCount, 1);
			return;
		}
		if (!errors.isEmpty()) {
			recordImportErrors(task.id(), batchId, errors);
			markValidationFailed(task.id(), batchId, rowCount, errors.size());
			return;
		}
		markReadyToCommit(task.id(), batchId, rowCount);
	}

	private void validateBomDraftImport(ClaimedTask task) {
		ImportTaskPayload payload = parseImportTaskPayload(task.requestPayload());
		byte[] content = sourceFileContent(payload.sourceFileId());
		Long batchId = batchId(task.id());
		clearImportRowsAndErrors(task.id(), batchId);
		List<ImportError> errors = new ArrayList<>();
		try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
			validateWorkbookSheets(workbook, List.of("bom", "items"));
			Sheet bomSheet = workbook.getSheet("bom");
			Sheet itemsSheet = workbook.getSheet("items");
			if (bomSheet == null || itemsSheet == null) {
				errors.add(new ImportError(1, "sheet", ApiErrorCode.IMPORT_FILE_INVALID.name(), "缺少 bom 或 items 工作表"));
			}
			else {
				validateHeader(bomSheet, new String[] { "mode", "bomId", "version", "bomCode",
						"parentMaterialCode", "versionCode", "name", "baseQuantity", "baseUnit", "effectiveFrom",
						"effectiveTo", "remark" });
				validateHeader(itemsSheet, new String[] { "lineNo", "childMaterialCode", "businessUnit",
						"businessQuantity", "lossRate", "warehouse", "remark" });
				validateVisibleColumns(bomSheet, 12);
				validateVisibleColumns(itemsSheet, 7);
				validateVisibleRows(bomSheet, 1);
				validateVisibleRows(itemsSheet, 1);
				if (itemsSheet.getLastRowNum() > 5000) {
					throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
				}
				Row header = bomSheet.getRow(1);
				BomDraftImportPayload bomPayload = new BomDraftImportPayload(cellString(header, 0),
						longCell(header, 1), longCell(header, 2), cellString(header, 3), cellString(header, 4),
						cellString(header, 5), cellString(header, 6), decimalCell(header, 7), cellString(header, 8),
						dateCell(header, 9), dateCell(header, 10), cellString(header, 11), bomItems(itemsSheet, errors));
				validateBomDraftPayload(bomPayload, errors);
				insertImportRow(batchId, 2, bomPayload);
				this.jdbcTemplate.update("""
						update platform_import_batch
						set mode = ?, target_object_id = ?, target_version = ?, updated_at = now(), version = version + 1
						where id = ?
						""", bomPayload.mode(), bomPayload.bomId(), bomPayload.version(), batchId);
			}
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		catch (BusinessException exception) {
			recordImportErrors(task.id(), batchId, List.of(new ImportError(null, "file",
					exception.errorCode().name(), exception.getMessage())));
			markValidationFailed(task.id(), batchId, 0, 1);
			return;
		}
		if (!errors.isEmpty()) {
			recordImportErrors(task.id(), batchId, errors);
			markValidationFailed(task.id(), batchId, 1, errors.size());
			return;
		}
		markReadyToCommit(task.id(), batchId, 1);
	}

	private void commitMaterialImport(ClaimedTask task, CurrentUser operator) {
		Long batchId = batchId(task.id());
		List<MaterialImportRow> rows = this.jdbcTemplate.query("""
				select payload::text
				from platform_import_row
				where batch_id = ?
				order by row_no
				""", (rs, rowNum) -> parse(rs.getString("payload"), MaterialImportRow.class), batchId);
		try {
			for (MaterialImportRow row : rows) {
				Long categoryId = categoryId(row.categoryCode());
				Long unitId = unitId(row.unitCode());
				String materialCode = hasText(row.code()) ? row.code()
						: this.codingRuleAdminService.generateForObject("MATERIAL", null, operator, null)
							.generatedCode();
				MaterialAdminService.MaterialRequest request = new MaterialAdminService.MaterialRequest(materialCode,
						row.name(), row.specification(), row.materialType(), row.sourceType(), row.trackingMethod(),
						categoryId, unitId, row.status(), row.remark(), row.costCategory(),
						row.inventoryValuationCategory(), booleanValue(row.inventoryValueEnabled()),
						booleanValue(row.projectCostEnabled()), row.costRemark(), null);
				this.materialAdminService.create(request, operator, null);
			}
			markImportSucceeded(task.id(), batchId, rows.size());
		}
		catch (RuntimeException exception) {
			recordImportErrors(task.id(), batchId, List.of(new ImportError(null, null,
					ApiErrorCode.IMPORT_APPLY_FAILED.name(), safeError(exception))));
			throw exception;
		}
	}

	private void commitBomDraftImport(ClaimedTask task, CurrentUser operator) {
		Long batchId = batchId(task.id());
		BomDraftImportPayload payload = parseBomDraftImportFromSource(task);
		try {
			Long parentId = materialId(payload.parentMaterialCode());
			List<BomAdminService.BomItemRequest> items = payload.items()
				.stream()
				.map((item) -> new BomAdminService.BomItemRequest(item.lineNo(), materialId(item.childMaterialCode()),
						null, null, unitId(item.businessUnitCode()), nonNullPositive(item.businessQuantity()),
						item.lossRate() == null ? BigDecimal.ZERO : item.lossRate(), item.remark()))
				.toList();
			BomAdminService.BomRequest request = new BomAdminService.BomRequest(payload.bomCode(), parentId,
					payload.versionCode(), payload.name(), nonNullPositive(payload.baseQuantity()), unitId(payload.baseUnitCode()), "DRAFT",
					payload.effectiveFrom(), payload.effectiveTo(), payload.remark(), items, payload.version());
			BomAdminService.BomDetailResponse result;
			if ("UPDATE_DRAFT".equals(payload.mode())) {
				result = this.bomAdminService.update(payload.bomId(), request, operator, null);
			}
			else {
				result = this.bomAdminService.create(request, operator, null);
			}
			this.jdbcTemplate.update("update platform_import_batch set target_object_id = ? where id = ?", result.id(),
					batchId);
			markImportSucceeded(task.id(), batchId, 1);
		}
		catch (RuntimeException exception) {
			recordImportErrors(task.id(), batchId, List.of(new ImportError(null, null,
					ApiErrorCode.IMPORT_APPLY_FAILED.name(), safeError(exception))));
			throw exception;
		}
	}

	private BomDraftImportPayload parseBomDraftImportFromSource(ClaimedTask task) {
		ImportTaskPayload payload = parseImportTaskPayload(task.requestPayload());
		List<ImportError> errors = new ArrayList<>();
		try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(sourceFileContent(payload.sourceFileId())))) {
			Sheet bomSheet = workbook.getSheet("bom");
			Sheet itemsSheet = workbook.getSheet("items");
			if (bomSheet == null || itemsSheet == null) {
				throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
			}
			Row header = bomSheet.getRow(1);
			return new BomDraftImportPayload(cellString(header, 0), longCell(header, 1), longCell(header, 2),
					cellString(header, 3), cellString(header, 4), cellString(header, 5), cellString(header, 6),
					decimalCell(header, 7), cellString(header, 8), dateCell(header, 9), dateCell(header, 10), cellString(header, 11),
					bomItems(itemsSheet, errors));
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
	}

	private void validateMaterialImportRow(int rowNo, MaterialImportRow row, List<ImportError> errors) {
		if (!hasText(row.name()) || !hasText(row.materialType()) || !hasText(row.sourceType())) {
			errors.add(new ImportError(rowNo, "name", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "必填字段缺失"));
		}
		if (hasText(row.code()) && countMaterial(row.code()) > 0) {
			errors.add(new ImportError(rowNo, "code", ApiErrorCode.MASTER_DATA_CODE_EXISTS.name(), "物料编码已存在"));
		}
		if (!hasText(row.code()) && enabledCodingRuleCount("MATERIAL") == 0) {
			errors.add(new ImportError(rowNo, "code", ApiErrorCode.CODING_RULE_DISABLED.name(), "物料编码规则未启用"));
		}
		if (categoryId(row.categoryCode()) == null) {
			errors.add(new ImportError(rowNo, "categoryCode", ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(),
					"物料分类不存在或未启用"));
		}
		if (unitId(row.unitCode()) == null) {
			errors.add(new ImportError(rowNo, "unitCode", ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(),
					"单位不存在或未启用"));
		}
	}

	private void validateBomDraftPayload(BomDraftImportPayload payload, List<ImportError> errors) {
		if (!"CREATE".equals(payload.mode()) && !"UPDATE_DRAFT".equals(payload.mode())) {
			errors.add(new ImportError(2, "mode", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "模式必须为 CREATE 或 UPDATE_DRAFT"));
		}
		if ("UPDATE_DRAFT".equals(payload.mode()) && (payload.bomId() == null || payload.version() == null)) {
			errors.add(new ImportError(2, "bomId", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "更新草稿必须提供 BOM ID 和版本"));
		}
		if (materialId(payload.parentMaterialCode()) == null) {
			errors.add(new ImportError(2, "parentMaterialCode", ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(),
					"父项物料不存在或未启用"));
		}
		if (payload.baseQuantity() == null || payload.baseQuantity().compareTo(BigDecimal.ZERO) <= 0) {
			errors.add(new ImportError(2, "baseQuantity", ApiErrorCode.BOM_QUANTITY_INVALID.name(), "BOM 基准数量必须大于 0"));
		}
		if (unitId(payload.baseUnitCode()) == null) {
			errors.add(new ImportError(2, "baseUnit", ApiErrorCode.BOM_UNIT_INVALID.name(), "BOM 基准单位不存在或未启用"));
		}
		if (payload.items() == null || payload.items().isEmpty()) {
			errors.add(new ImportError(2, "items", ApiErrorCode.BOM_EMPTY_ITEMS.name(), "BOM 明细不能为空"));
		}
		else {
			for (BomDraftImportItem item : payload.items()) {
				if (unitId(item.businessUnitCode()) == null) {
					errors.add(new ImportError(item.lineNo(), "businessUnit", ApiErrorCode.BOM_UNIT_INVALID.name(),
							"BOM 明细业务单位不存在或未启用"));
				}
				if (item.businessQuantity() == null || item.businessQuantity().compareTo(BigDecimal.ZERO) <= 0) {
					errors.add(new ImportError(item.lineNo(), "businessQuantity", ApiErrorCode.BOM_QUANTITY_INVALID.name(),
							"BOM 明细数量必须大于 0"));
				}
				if (materialId(item.childMaterialCode()) == null) {
					errors.add(new ImportError(item.lineNo(), "childMaterialCode",
							ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(), "子项物料不存在或未启用"));
				}
				if (hasText(item.warehouse())) {
					errors.add(new ImportError(item.lineNo(), "warehouse",
							ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "warehouse 必须留空，022 不支持 BOM 仓库导入"));
				}
			}
		}
	}

	private Long insertFile(PlatformStorageService.StoredObject storedObject, String filename, String contentType,
			byte[] content, String usage, CurrentUser operator) {
		return this.jdbcTemplate.queryForObject("""
				insert into platform_file_object (
					bucket, object_key, original_filename, content_type, size_bytes, sha256, etag,
					file_usage, status, created_by_user_id, created_by_username, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', ?, ?, ?)
				returning id
				""", Long.class, storedObject.bucket(), storedObject.objectKey(), filename, contentType,
				content.length, sha256(content), storedObject.eTag(), usage, operator.id(),
				operator.username(), OffsetDateTime.now());
	}

	private Long insertQueuedTask(String taskType, String stage, String payloadJson, String idempotencyKey,
			Long sourceFileId, CurrentUser operator) {
		OffsetDateTime now = OffsetDateTime.now();
		return this.jdbcTemplate.queryForObject("""
				insert into platform_document_task (
					task_no, task_type, stage, status, request_payload, idempotency_key, source_file_id,
					created_by_user_id, created_by_username, next_run_at, created_at
				)
				values (?, ?, ?, 'QUEUED', cast(? as jsonb), ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, nextTaskNo(prefix(taskType)), taskType, stage, payloadJson, idempotencyKey,
				sourceFileId, operator.id(), operator.username(), now, now);
	}

	private List<ExistingTask> existingTask(Long userId, String taskType, String idempotencyKey) {
		return this.jdbcTemplate.query("""
				select id, request_payload::text as request_payload
				from platform_document_task
				where created_by_user_id = ?
				and task_type = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingTask(rs.getLong("id"), rs.getString("request_payload")),
				userId, taskType, idempotencyKey);
	}

	private byte[] readXlsx(MultipartFile file, String taskType) {
		if (file == null || file.isEmpty() || file.getSize() > 10L * 1024L * 1024L
				|| !safeFilename(file.getOriginalFilename()).toLowerCase().endsWith(".xlsx")) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		try {
			byte[] content = file.getBytes();
			validateXlsxPackage(content, taskType);
			return content;
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
	}

	private void validateXlsxPackage(byte[] content, String taskType) {
		boolean hasContentTypes = false;
		boolean hasWorkbook = false;
		try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(content))) {
			ZipEntry entry;
			int entries = 0;
			while ((entry = zip.getNextEntry()) != null) {
				entries++;
				if (entries > 200) {
					throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
				}
				String name = entry.getName().toLowerCase();
				if ("[content_types].xml".equals(name)) {
					hasContentTypes = true;
				}
				if ("xl/workbook.xml".equals(name)) {
					hasWorkbook = true;
				}
				if (name.endsWith("vbaproject.bin") || name.contains("externallinks/")
						|| name.endsWith(".bin") && name.contains("vba")) {
					throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
				}
			}
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		if (!hasContentTypes || !hasWorkbook) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
	}

	private byte[] sourceFileContent(Long fileId) {
		String objectKey = this.jdbcTemplate.queryForObject("""
				select object_key
				from platform_file_object
				where id = ?
				and status = 'AVAILABLE'
				""", String.class, fileId);
		return this.storageService.get(objectKey);
	}

	private Long batchId(Long taskId) {
		return this.jdbcTemplate.queryForObject("select id from platform_import_batch where task_id = ?", Long.class,
				taskId);
	}

	private void clearImportRowsAndErrors(Long taskId, Long batchId) {
		this.jdbcTemplate.update("delete from platform_document_task_error where task_id = ?", taskId);
		this.jdbcTemplate.update("delete from platform_import_error where batch_id = ?", batchId);
		this.jdbcTemplate.update("delete from platform_import_row where batch_id = ?", batchId);
	}

	private void insertImportRow(Long batchId, int rowNo, Object payload) {
		this.jdbcTemplate.update("""
				insert into platform_import_row (batch_id, row_no, payload)
				values (?, ?, cast(? as jsonb))
				""", batchId, rowNo, json(payload));
	}

	private void recordImportErrors(Long taskId, Long batchId, List<ImportError> errors) {
		for (ImportError error : errors) {
			this.jdbcTemplate.update("""
					insert into platform_document_task_error (task_id, row_no, column_name, error_code, message)
					values (?, ?, ?, ?, ?)
					""", taskId, error.rowNo(), error.columnName(), error.errorCode(), error.message());
			this.jdbcTemplate.update("""
					insert into platform_import_error (batch_id, row_no, column_name, error_code, message)
					values (?, ?, ?, ?, ?)
					""", batchId, error.rowNo(), error.columnName(), error.errorCode(), error.message());
		}
	}

	private void markValidationFailed(Long taskId, Long batchId, int totalCount, int errorCount) {
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'VALIDATION_FAILED', total_count = ?, error_count = ?, error_summary = ?,
				    finished_at = now(), lease_owner = null, lease_until = null, updated_at = now(),
				    version = version + 1
				where id = ?
				""", totalCount, errorCount, "导入校验失败：" + errorCount + " 条错误", taskId);
		this.jdbcTemplate.update("""
				update platform_import_batch
				set status = 'VALIDATION_FAILED', updated_at = now(), version = version + 1
				where id = ?
				""", batchId);
	}

	private void markReadyToCommit(Long taskId, Long batchId, int totalCount) {
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'READY_TO_COMMIT', total_count = ?, success_count = ?, error_count = 0,
				    finished_at = now(), lease_owner = null, lease_until = null, updated_at = now(), version = version + 1
				where id = ?
				""", totalCount, totalCount, taskId);
		this.jdbcTemplate.update("""
				update platform_import_batch
				set status = 'VALIDATED', updated_at = now(), version = version + 1
				where id = ?
				""", batchId);
	}

	private void markImportSucceeded(Long taskId, Long batchId, int totalCount) {
		OffsetDateTime finishedAt = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'SUCCEEDED', total_count = ?, success_count = ?, error_count = 0, finished_at = ?,
				    committed_at = ?, expires_at = ?, lease_owner = null, lease_until = null, updated_at = now(),
				    version = version + 1
				where id = ?
				""", totalCount, totalCount, finishedAt, finishedAt, finishedAt.plusDays(7), taskId);
		this.jdbcTemplate.update("""
				update platform_import_batch
				set status = 'COMMITTED', committed_at = ?, updated_at = now(), version = version + 1
				where id = ?
				""", finishedAt, batchId);
		createMessage(task(taskId).createdByUserId(), "导入任务已完成", "导入提交成功", "DOCUMENT_TASK_SUCCEEDED", taskId);
	}

	public CurrentUser taskOperator(ClaimedTask task) {
		List<String> permissions = this.jdbcTemplate.query("""
				select distinct p.code
				from sys_user u
				join sys_user_role ur on ur.user_id = u.id
				join sys_role r on r.id = ur.role_id and r.status = 'ENABLED'
				join sys_role_permission rp on rp.role_id = r.id
				join sys_permission p on p.id = rp.permission_id
				where u.id = ?
				and u.status = 'ENABLED'
				""", (rs, rowNum) -> rs.getString("code"), task.createdByUserId());
		return new CurrentUser(task.createdByUserId(), task.createdByUsername(), task.createdByUsername(),
				SystemUserStatus.ENABLED, List.of(), List.of(), permissions);
	}

	private ImportTaskPayload parseImportTaskPayload(String payload) {
		return parse(payload, ImportTaskPayload.class);
	}

	public BomDraftExportRequest parseBomDraftExportRequest(String payload) {
		return parse(payload, BomDraftExportRequest.class);
	}

	public PrintTaskPayload parsePrintTaskPayload(String payload) {
		return parse(payload, PrintTaskPayload.class);
	}

	private <T> T parse(String payload, Class<T> type) {
		try {
			return this.objectMapper.readValue(payload, type);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private Long categoryId(String code) {
		if (!hasText(code)) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select id
				from mst_material_category
				where code = ?
				and status = 'ENABLED'
				""", (rs, rowNum) -> rs.getLong("id"), code).stream().findFirst().orElse(null);
	}

	private Long unitId(String code) {
		if (!hasText(code)) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select id
				from mst_unit
				where code = ?
				and status = 'ENABLED'
				""", (rs, rowNum) -> rs.getLong("id"), code).stream().findFirst().orElse(null);
	}

	private String unitCode(Long id) {
		if (id == null) {
			return "";
		}
		return this.jdbcTemplate.query("""
				select code
				from mst_unit
				where id = ?
				""", (rs, rowNum) -> rs.getString("code"), id).stream().findFirst().orElse("");
	}

	private Long materialId(String code) {
		if (!hasText(code)) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select id
				from mst_material
				where code = ?
				and status = 'ENABLED'
				""", (rs, rowNum) -> rs.getLong("id"), code).stream().findFirst().orElse(null);
	}

	private long countMaterial(String code) {
		return this.jdbcTemplate.queryForObject("select count(*) from mst_material where code = ?", Long.class, code);
	}

	private long enabledCodingRuleCount(String objectType) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_coding_rule
				where object_type = ?
				and status = 'ENABLED'
				""", Long.class, objectType);
	}

	private ApprovalPrintSnapshot approvalPrintSnapshot(Long approvalInstanceId) {
		return this.jdbcTemplate.query("""
				select id, scene_code, business_object_type, business_object_id, business_object_no,
				       business_object_summary, business_object_version, status, submitted_by_username,
				       submitted_at, completed_by_username, completed_at, version
				from platform_approval_instance
				where id = ?
				""", (rs, rowNum) -> new ApprovalPrintSnapshot(rs.getLong("id"), rs.getString("scene_code"),
				rs.getString("business_object_type"), rs.getLong("business_object_id"),
				rs.getString("business_object_no"), rs.getString("business_object_summary"),
				rs.getLong("business_object_version"), rs.getString("status"), rs.getString("submitted_by_username"),
				rs.getObject("submitted_at", OffsetDateTime.class), rs.getString("completed_by_username"),
				rs.getObject("completed_at", OffsetDateTime.class), rs.getLong("version")), approvalInstanceId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED));
	}

	private void requireApprovalBusinessView(String sceneCode, CurrentUser operator) {
		if ("SALES_PROJECT_CONTRACT_ACTIVATION".equals(sceneCode)
				&& !operator.permissions().contains("sales:contract:view")) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
		if ("BOM_ECO_APPLICATION".equals(sceneCode) && !operator.permissions().contains("material:bom-eco:view")) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private String printTemplateCode(String sceneCode) {
		return switch (sceneCode) {
			case "SALES_PROJECT_CONTRACT_ACTIVATION" -> "CONTRACT_ACTIVATION_APPROVAL_V1";
			case "BOM_ECO_APPLICATION" -> "BOM_ECO_APPLICATION_APPROVAL_V1";
			default -> throw new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED);
		};
	}

	private DocumentTaskState task(Long id) {
		return this.jdbcTemplate.query("""
				select id, task_no, task_type, stage, status, idempotency_key, created_by_user_id,
				       created_by_username, total_count, success_count, error_count, result_file_id,
				       error_file_id, error_summary, attempt_count, max_attempts, created_at, started_at,
				       finished_at, expires_at, version
				from platform_document_task
				where id = ?
				""", this::mapTask, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.DOCUMENT_TASK_NOT_FOUND));
	}

	private DocumentTaskState mapTask(ResultSet rs, int rowNum) throws SQLException {
		return new DocumentTaskState(rs.getLong("id"), rs.getString("task_no"), rs.getString("task_type"),
				rs.getString("stage"), rs.getString("status"), rs.getString("idempotency_key"),
				rs.getLong("created_by_user_id"), rs.getString("created_by_username"), rs.getInt("total_count"),
				rs.getInt("success_count"), rs.getInt("error_count"), nullableLong(rs, "result_file_id"),
				nullableLong(rs, "error_file_id"), rs.getString("error_summary"), rs.getInt("attempt_count"),
				rs.getInt("max_attempts"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("started_at", OffsetDateTime.class), rs.getObject("finished_at", OffsetDateTime.class),
				rs.getObject("expires_at", OffsetDateTime.class), rs.getLong("version"),
				documentTaskAvailableActions(rs.getString("status"), nullableLong(rs, "result_file_id"),
						rs.getInt("error_count"), rs.getObject("expires_at", OffsetDateTime.class)));
	}

	private DocumentTaskRecord toDocumentTaskRecord(DocumentTaskState task) {
		TaskObjectInfo objectInfo = taskObjectInfo(task);
		return new DocumentTaskRecord(task.id(), task.taskNo(), task.taskType(), objectInfo.objectType(),
				objectInfo.objectId(), objectInfo.objectNo(), objectInfo.objectName(), taskDirection(task.taskType()),
				task.stage(), task.status(), progressPercent(task.status()), task.totalCount(), task.successCount(),
				task.errorCount(), task.errorSummary(), task.createdByUsername(), task.createdAt(), task.finishedAt(),
				task.expiresAt(), task.version(), task.availableActions());
	}

	private TaskObjectInfo taskObjectInfo(DocumentTaskState task) {
		try {
			if ("APPROVAL_PRINT".equals(task.taskType())) {
				PrintTaskPayload payload = parsePrintTaskPayload(taskRequestPayload(task.id()));
				ApprovalPrintSnapshot snapshot = approvalPrintSnapshot(payload.approvalInstanceId());
				return new TaskObjectInfo(snapshot.businessObjectType(), snapshot.businessObjectId(),
						snapshot.businessObjectNo(), snapshot.businessObjectSummary());
			}
			if ("BOM_DRAFT_EXPORT".equals(task.taskType())) {
				BomDraftExportRequest payload = parseBomDraftExportRequest(taskRequestPayload(task.id()));
				return new TaskObjectInfo("BOM", payload.bomId(), null, null);
			}
			if ("MATERIAL_EXPORT".equals(task.taskType()) || "MATERIAL_IMPORT".equals(task.taskType())) {
				return new TaskObjectInfo("MATERIAL", null, null, null);
			}
			if ("BOM_DRAFT_IMPORT".equals(task.taskType())) {
				return new TaskObjectInfo("BOM", null, null, null);
			}
		}
		catch (RuntimeException exception) {
			return new TaskObjectInfo(null, null, null, null);
		}
		return new TaskObjectInfo(null, null, null, null);
	}

	private static String taskDirection(String taskType) {
		if (taskType.endsWith("_IMPORT")) {
			return "IMPORT";
		}
		if (taskType.endsWith("_EXPORT")) {
			return "EXPORT";
		}
		if ("APPROVAL_PRINT".equals(taskType)) {
			return "PRINT";
		}
		return null;
	}

	private static Integer progressPercent(String status) {
		return switch (status) {
			case "QUEUED" -> 0;
			case "RUNNING" -> 50;
			default -> 100;
		};
	}

	private List<String> documentTaskAvailableActions(String status, Long resultFileId, int errorCount,
			OffsetDateTime expiresAt) {
		List<String> actions = new ArrayList<>();
		if ("READY_TO_COMMIT".equals(status)) {
			actions.add("CONFIRM");
			actions.add("CANCEL");
		}
		if ("SUCCEEDED".equals(status) && resultFileId != null
				&& (expiresAt == null || expiresAt.isAfter(OffsetDateTime.now()))) {
			actions.add("DOWNLOAD");
		}
		if ("VALIDATION_FAILED".equals(status) && errorCount > 0) {
			actions.add("ERRORS");
		}
		if ("QUEUED".equals(status) || "RUNNING".equals(status)) {
			actions.add("CANCEL");
		}
		return actions;
	}

	private ClaimedTask mapClaimedTask(ResultSet rs, int rowNum) throws SQLException {
		return new ClaimedTask(rs.getLong("id"), rs.getString("task_no"), rs.getString("task_type"),
				rs.getString("stage"), rs.getString("request_payload"), rs.getLong("created_by_user_id"),
				rs.getString("created_by_username"), rs.getInt("attempt_count"), rs.getInt("max_attempts"),
				rs.getLong("version"));
	}

	private void requireTaskAccess(DocumentTaskState task, CurrentUser currentUser) {
		if (!canAccessTask(task, currentUser)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private boolean canAccessTask(DocumentTaskState task, CurrentUser currentUser) {
		if (!task.createdByUserId().equals(currentUser.id())
				&& !currentUser.permissions().contains("platform:document-task:view-all")) {
			return false;
		}
		String permission;
		try {
			permission = taskDomainPermission(task.taskType());
		}
		catch (BusinessException exception) {
			return false;
		}
		if (!currentUser.permissions().contains(permission)) {
			return false;
		}
		if ("APPROVAL_PRINT".equals(task.taskType())) {
			return canViewApprovalPrintTask(task, currentUser);
		}
		return true;
	}

	private boolean canViewApprovalPrintTask(DocumentTaskState task, CurrentUser currentUser) {
		try {
			PrintTaskPayload payload = parsePrintTaskPayload(taskRequestPayload(task.id()));
			ApprovalPrintSnapshot snapshot = approvalPrintSnapshot(payload.approvalInstanceId());
			requireApprovalBusinessView(snapshot.sceneCode(), currentUser);
			return true;
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private String taskRequestPayload(Long taskId) {
		return this.jdbcTemplate.queryForObject("""
				select request_payload::text
				from platform_document_task
				where id = ?
				""", String.class, taskId);
	}

	private String taskDomainPermission(String taskType) {
		return switch (taskType) {
			case "MATERIAL_IMPORT" -> "master:material:import";
			case "MATERIAL_EXPORT" -> "master:material:export";
			case "BOM_DRAFT_IMPORT" -> "material:bom:import";
			case "BOM_DRAFT_EXPORT" -> "material:bom:export";
			case "APPROVAL_PRINT" -> "platform:print:generate";
			default -> throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		};
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (!hasText(idempotencyKey) || idempotencyKey.length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private String json(Object value) {
		try {
			return this.objectMapper.writeValueAsString(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	public MaterialExportRequest parseMaterialExportRequest(String payload) {
		try {
			if (!hasText(payload)) {
				return new MaterialExportRequest(null, null, null, null, null, null);
			}
			return this.objectMapper.readValue(payload, MaterialExportRequest.class);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private boolean jsonEquivalent(String left, String right) {
		try {
			return this.objectMapper.readTree(left).equals(this.objectMapper.readTree(right));
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private boolean importPayloadEquivalent(String left, String right) {
		try {
			ImportTaskPayload leftPayload = parseImportTaskPayload(left);
			ImportTaskPayload rightPayload = parseImportTaskPayload(right);
			return nullToBlank(leftPayload.filename()).equals(nullToBlank(rightPayload.filename()))
					&& nullToBlank(leftPayload.sha256()).equals(nullToBlank(rightPayload.sha256()));
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private void recordWorkerAudit(ClaimedTask task, String action, String workerId) {
		this.jdbcTemplate.update("""
				insert into sys_audit_log (
					operator_user_id, operator_username, action, target_type, target_id, target_summary,
					request_method, request_path, ip_address, result, error_code, created_at
				)
				values (?, ?, ?, 'DOCUMENT_TASK', ?, ?, null, null, null, 'SUCCESS', null, ?)
				""", task.createdByUserId(), workerId, action, Long.toString(task.id()), task.taskType(),
				OffsetDateTime.now());
	}

	private void createMessage(Long recipientUserId, String title, String content, String messageType, Long taskId) {
		this.jdbcTemplate.update("""
				insert into platform_message (
					recipient_user_id, title, content, message_type, status, related_object_type,
					related_object_id, created_at
				)
				values (?, ?, ?, ?, 'UNREAD', 'DOCUMENT_TASK', ?, ?)
				""", recipientUserId, title, content, messageType, taskId, OffsetDateTime.now());
	}

	private static String safeError(RuntimeException exception) {
		String message = exception.getMessage();
		if (!hasText(message)) {
			return exception.getClass().getSimpleName();
		}
		return message.length() > 500 ? message.substring(0, 500) : message;
	}

	private static String nextTaskNo(String prefix) {
		return prefix + OffsetDateTime.now().format(TASK_NO_FORMATTER)
				+ String.format("%03d", Math.floorMod(TASK_SEQUENCE.incrementAndGet(), 1000));
	}

	private static String prefix(String taskType) {
		return switch (taskType) {
			case "MATERIAL_IMPORT" -> "MIMP";
			case "MATERIAL_EXPORT" -> "MEXP";
			case "BOM_DRAFT_IMPORT" -> "BIMP";
			case "BOM_DRAFT_EXPORT" -> "BEXP";
			case "APPROVAL_PRINT" -> "PRNT";
			default -> "TASK";
		};
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String trimToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private static String nullToBlank(String value) {
		return value == null ? "" : value;
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private static String safeFilename(String filename) {
		return hasText(filename) ? filename.replace("\\", "_").replace("/", "_") : "import.xlsx";
	}

	private static boolean rowIsBlank(Row row) {
		for (int i = 0; i < row.getLastCellNum(); i++) {
			if (hasText(cellString(row, i))) {
				return false;
			}
		}
		return true;
	}

	private static void validateWorkbookSheets(Workbook workbook, List<String> expectedSheetNames) {
		if (workbook.getNumberOfSheets() != expectedSheetNames.size()) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		for (int i = 0; i < expectedSheetNames.size(); i++) {
			if (workbook.isSheetHidden(i) || workbook.isSheetVeryHidden(i)
					|| workbook.getSheet(expectedSheetNames.get(i)) == null) {
				throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
			}
		}
	}

	private static void validateHeader(Sheet sheet, String[] expectedHeaders) {
		Row header = sheet.getRow(0);
		if (header == null) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		for (int i = 0; i < expectedHeaders.length; i++) {
			String actual = cellString(header, i);
			if (!expectedHeaders[i].equals(actual)) {
				throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
			}
		}
		if (header.getLastCellNum() > expectedHeaders.length) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
	}

	private static void validateVisibleColumns(Sheet sheet, int columnCount) {
		for (int i = 0; i < columnCount; i++) {
			if (sheet.isColumnHidden(i)) {
				throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
			}
		}
	}

	private static void validateVisibleRows(Sheet sheet, int firstDataRow) {
		for (int i = firstDataRow; i <= sheet.getLastRowNum(); i++) {
			Row row = sheet.getRow(i);
			if (row != null && row.getZeroHeight() && !rowIsBlank(row)) {
				throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
			}
		}
	}

	private static String cellString(Row row, int index) {
		if (row == null) {
			return null;
		}
		Cell cell = row.getCell(index);
		if (cell == null) {
			return null;
		}
		if (cell.getCellType() == CellType.FORMULA) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		return switch (cell.getCellType()) {
			case STRING -> trimToNull(cell.getStringCellValue());
			case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
			case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
			case BLANK -> null;
			default -> trimToNull(cell.toString());
		};
	}

	private static Long longCell(Row row, int index) {
		String value = cellString(row, index);
		return hasText(value) ? Long.valueOf(value) : null;
	}

	private static BigDecimal decimalCell(Row row, int index) {
		String value = cellString(row, index);
		return hasText(value) ? new BigDecimal(value) : null;
	}

	private static LocalDate dateCell(Row row, int index) {
		String value = cellString(row, index);
		return hasText(value) ? LocalDate.parse(value) : null;
	}

	private static Boolean booleanValue(String value) {
		return hasText(value) ? Boolean.valueOf(value) : null;
	}

	private static BigDecimal nonNullPositive(BigDecimal value) {
		if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.BOM_QUANTITY_INVALID);
		}
		return value;
	}

	private static List<BomDraftImportItem> bomItems(Sheet itemsSheet, List<ImportError> errors) {
		List<BomDraftImportItem> items = new ArrayList<>();
		for (int i = 1; i <= itemsSheet.getLastRowNum(); i++) {
			Row row = itemsSheet.getRow(i);
			if (row == null || rowIsBlank(row)) {
				continue;
			}
			try {
				items.add(new BomDraftImportItem(Integer.valueOf(cellString(row, 0)), cellString(row, 1),
						cellString(row, 2), decimalCell(row, 3),
						decimalCell(row, 4) == null ? BigDecimal.ZERO : decimalCell(row, 4), cellString(row, 5),
						cellString(row, 6)));
			}
			catch (RuntimeException exception) {
				errors.add(new ImportError(i + 1, "items", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "BOM 明细格式错误"));
			}
		}
		return items;
	}

	private static void writeRow(Row row, String[] values) {
		for (int i = 0; i < values.length; i++) {
			row.createCell(i).setCellValue(nullToBlank(values[i]));
		}
	}

	private static String stringDate(LocalDate value) {
		return value == null ? "" : value.toString();
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	public record MaterialExportRequest(String keyword, String status, Long categoryId, String materialType,
			String sourceType, String trackingMethod) {
	}

	public record ConfirmImportRequest(Long version) {
	}

	public record BomDraftExportRequest(Long bomId, Long version) {
	}

	public record PrintTaskRequest(Long approvalInstanceId, String templateCode) {
	}

	public record ImportTemplateRecord(String filename, String contentType, byte[] content) {
	}

	public record CancelTaskRequest(Long version, String reason) {
	}

	public record DocumentTaskRecord(Long id, String taskNo, String taskType, String objectType, Long objectId,
			String objectNo, String objectName, String direction, String stage, String status, Integer progressPercent,
			int totalRows, int successRows, int failedRows, String errorMessage, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime completedAt, OffsetDateTime expiresAt, Long version,
			List<String> availableActions) {
	}

	private record DocumentTaskState(Long id, String taskNo, String taskType, String stage, String status,
			String idempotencyKey, Long createdByUserId, String createdByUsername, int totalCount, int successCount,
			int errorCount, Long resultFileId, Long errorFileId, String errorSummary, int attemptCount,
			int maxAttempts, OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime finishedAt,
			OffsetDateTime expiresAt, Long version, List<String> availableActions) {
	}

	public record DownloadedFile(String filename, String contentType, byte[] content) {
	}

	public record ClaimedTask(Long id, String taskNo, String taskType, String stage, String requestPayload,
			Long createdByUserId, String createdByUsername, int attemptCount, int maxAttempts, Long version) {

		ClaimedTask withAttempt(int newAttemptCount) {
			return new ClaimedTask(this.id, this.taskNo, this.taskType, this.stage, this.requestPayload,
					this.createdByUserId, this.createdByUsername, newAttemptCount, this.maxAttempts, this.version);
		}

	}

	private record ExistingTask(Long id, String requestPayload) {
	}

	private record TaskObjectInfo(String objectType, Long objectId, String objectNo, String objectName) {
	}

	private record DocumentTaskFile(String objectKey, String originalFilename, String contentType) {
	}

	public record ExportedFile(String filename, String contentType, byte[] content, int totalCount) {
	}

	public record PrintTemplateRecord(String templateCode, String sceneCode, String name, String objectType,
			int templateVersion) {
	}

	public record PrintPreviewRecord(String templateCode, int templateVersion, String sceneCode,
			String businessObjectType, String businessObjectNo, String businessObjectSummary, String approvalStatus,
			String submittedByUsername, OffsetDateTime submittedAt, String completedByUsername,
			OffsetDateTime completedAt, List<PrintPreviewSection> sections) {
	}

	public record PrintPreviewSection(String title, List<PrintPreviewField> fields) {
	}

	public record PrintPreviewField(String label, String value) {
	}

	public record TaskErrorRecord(Integer rowNo, String columnName, String errorCode, String message) {
	}

	public record PrintTaskPayload(Long approvalInstanceId, String templateCode, Long approvalInstanceVersion,
			Long businessObjectVersion) {
	}

	private record ImportTaskPayload(Long sourceFileId, String filename, String sha256) {
	}

	private record MaterialImportRow(String code, String name, String specification, String materialType,
			String sourceType, String trackingMethod, String categoryCode, String unitCode, String status,
			String costCategory, String inventoryValuationCategory, String inventoryValueEnabled,
			String projectCostEnabled, String costRemark, String remark) {
	}

	private record BomDraftImportPayload(String mode, Long bomId, Long version, String bomCode,
			String parentMaterialCode, String versionCode, String name, BigDecimal baseQuantity,
			String baseUnitCode, LocalDate effectiveFrom, LocalDate effectiveTo, String remark,
			List<BomDraftImportItem> items) {
	}

	private record BomDraftImportItem(Integer lineNo, String childMaterialCode, String businessUnitCode,
			BigDecimal businessQuantity, BigDecimal lossRate, String warehouse, String remark) {
	}

	private record ImportError(Integer rowNo, String columnName, String errorCode, String message) {
	}

	private record ApprovalPrintSnapshot(Long id, String sceneCode, String businessObjectType, Long businessObjectId,
			String businessObjectNo, String businessObjectSummary, Long businessObjectVersion, String status,
			String submittedByUsername, OffsetDateTime submittedAt, String completedByUsername,
			OffsetDateTime completedAt, Long version) {
	}

	private record MaterialExportRow(String code, String name, String specification, String materialType,
			String sourceType, String trackingMethod, String categoryName, String unitName, String status,
			String costCategory, String inventoryValuationCategory, boolean inventoryValueEnabled,
			boolean projectCostEnabled) {
	}

}
