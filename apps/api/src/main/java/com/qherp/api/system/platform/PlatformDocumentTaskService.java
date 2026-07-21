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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PlatformDocumentTaskService {

	private static final AtomicInteger TASK_SEQUENCE = new AtomicInteger();

	private static final DateTimeFormatter TASK_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final Set<String> PROCUREMENT_EXPORT_TASK_TYPES = Set.of("PROCUREMENT_REQUISITION_EXPORT",
			"PROCUREMENT_INQUIRY_EXPORT", "PROCUREMENT_QUOTE_EXPORT", "PROCUREMENT_PRICE_AGREEMENT_EXPORT",
			"PROCUREMENT_ORDER_EXPORT", "PROCUREMENT_SCHEDULE_EXPORT", "PROCUREMENT_SUPPLY_EXPORT");

	private static final Set<String> SALES_EXPORT_TASK_TYPES = Set.of("SALES_QUOTE_EXPORT",
			"SALES_DELIVERY_PLAN_EXPORT", "SALES_EFFECTIVE_DEMAND_EXPORT");

	private static final Set<String> PLANNING_EXPORT_TASK_TYPES = Set.of("MATERIAL_REQUIREMENT_RUN_EXPORT");

	private static final Set<String> PROCUREMENT_ORDER_PRINT_STATUSES = Set.of("CONFIRMED", "PARTIALLY_RECEIVED",
			"RECEIVED", "CLOSED");

	private static final Map<String, FixedPrintDefinition> FIXED_PRINT_DEFINITIONS = Map.ofEntries(
			Map.entry("SALES_ORDER", new FixedPrintDefinition("SALES_ORDER", "SALES_ORDER_V1",
					"SALES_ORDER_PRINT", "销售订单固定打印", "sal_sales_order", "order_no", "sales:order:view")),
			Map.entry("SALES_SHIPMENT", new FixedPrintDefinition("SALES_SHIPMENT", "SALES_SHIPMENT_V1",
					"SALES_SHIPMENT_PRINT", "销售出库单固定打印", "sal_sales_shipment", "shipment_no",
					"sales:shipment:view")),
			Map.entry("PROCUREMENT_RECEIPT", new FixedPrintDefinition("PROCUREMENT_RECEIPT",
					"PROCUREMENT_RECEIPT_V1", "PROCUREMENT_RECEIPT_PRINT", "采购入库单固定打印",
					"proc_purchase_receipt", "receipt_no", "procurement:receipt:view")),
			Map.entry("INVENTORY_TRANSFER", new FixedPrintDefinition("INVENTORY_TRANSFER",
					"INVENTORY_TRANSFER_V1", "INVENTORY_TRANSFER_PRINT", "仓库调拨单固定打印",
					"inv_warehouse_transfer", "transfer_no", "inventory:warehouse-transfer:view")),
			Map.entry("PRODUCTION_WORK_ORDER", new FixedPrintDefinition("PRODUCTION_WORK_ORDER",
					"PRODUCTION_WORK_ORDER_V1", "PRODUCTION_WORK_ORDER_PRINT", "生产工单固定打印",
					"mfg_work_order", "work_order_no", "production:work-order:view")),
			Map.entry("PRODUCTION_MATERIAL_ISSUE", new FixedPrintDefinition("PRODUCTION_MATERIAL_ISSUE",
					"PRODUCTION_MATERIAL_ISSUE_V1", "PRODUCTION_MATERIAL_ISSUE_PRINT", "生产领料单固定打印",
					"mfg_material_issue", "issue_no", "production:issue:view")),
			Map.entry("PRODUCTION_COMPLETION_RECEIPT", new FixedPrintDefinition("PRODUCTION_COMPLETION_RECEIPT",
					"PRODUCTION_COMPLETION_RECEIPT_V1", "PRODUCTION_COMPLETION_RECEIPT_PRINT", "完工入库单固定打印",
					"mfg_completion_receipt", "receipt_no", "production:receipt:view")),
			Map.entry("SALES_INVOICE", new FixedPrintDefinition("SALES_INVOICE", "SALES_INVOICE_V1",
					"SALES_INVOICE_PRINT", "销售发票固定打印", "fin_sales_invoice", "invoice_no",
					"finance:sales-invoice:view")),
			Map.entry("PURCHASE_INVOICE", new FixedPrintDefinition("PURCHASE_INVOICE", "PURCHASE_INVOICE_V1",
					"PURCHASE_INVOICE_PRINT", "采购发票固定打印", "fin_purchase_invoice", "invoice_no",
					"finance:purchase-invoice:view")),
			Map.entry("ACCOUNTING_VOUCHER", new FixedPrintDefinition("ACCOUNTING_VOUCHER",
					"ACCOUNTING_VOUCHER_V1", "ACCOUNTING_VOUCHER_PRINT", "会计凭证固定打印", "gl_voucher",
					"coalesce(voucher_no, draft_no)", "gl:voucher:view")));

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
	public DocumentTaskRecord importSupplierQuotes(Long inquiryId, MultipartFile file, String idempotencyKey,
			CurrentUser operator, HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		requirePermission(operator, "platform:document-task:create");
		requirePermission(operator, "procurement:inquiry:view");
		requirePermission(operator, "procurement:quote:import");
		ProcurementInquiryTaskSnapshot inquiry = procurementInquiryTaskSnapshot(inquiryId);
		if (!"RELEASED".equals(inquiry.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_STATUS_INVALID);
		}
		byte[] content = readXlsx(file, "PROCUREMENT_QUOTE_IMPORT");
		String payloadJson = json(new SupplierQuoteImportPayload(null, file.getOriginalFilename(), sha256(content),
				inquiryId));
		List<ExistingTask> existing = existingTask(operator.id(), "PROCUREMENT_QUOTE_IMPORT", idempotencyKey);
		if (!existing.isEmpty()) {
			ExistingTask existingTask = existing.getFirst();
			if (!supplierQuoteImportPayloadEquivalent(payloadJson, existingTask.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(existingTask.id(), operator);
		}
		PlatformStorageService.StoredObject storedObject = this.storageService.put(
				"imports/procurement-quotes/" + UUID.randomUUID() + ".xlsx", content,
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		Long fileId = insertFile(storedObject, safeFilename(file.getOriginalFilename()),
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content, "IMPORT_SOURCE",
				operator);
		payloadJson = json(new SupplierQuoteImportPayload(fileId, file.getOriginalFilename(), sha256(content),
				inquiryId));
		try {
			Long taskId = insertQueuedTask("PROCUREMENT_QUOTE_IMPORT", "VALIDATE", payloadJson, idempotencyKey,
					fileId, operator);
			this.jdbcTemplate.update("""
					insert into platform_import_batch (
						task_id, import_type, source_file_id, source_sha256, status, target_object_id,
						created_at, updated_at
					)
					values (?, 'PROCUREMENT_QUOTE_IMPORT', ?, ?, 'QUEUED', ?, now(), now())
					""", taskId, fileId, sha256(content), inquiryId);
			this.auditService.record(operator, "IMPORT_UPLOAD", "DOCUMENT_TASK", taskId,
					"PROCUREMENT_QUOTE_IMPORT", servletRequest);
			return get(taskId, operator);
		}
		catch (DuplicateKeyException exception) {
			this.storageService.deleteQuietly(storedObject.objectKey());
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	@Transactional
	public DocumentTaskRecord confirmImport(Long taskId, ConfirmImportRequest request, String idempotencyKey,
			CurrentUser operator, HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		DocumentTaskState task = task(taskId);
		requireTaskAccess(task, operator);
		if (isHistoryImportTask(task.taskType())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
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

	@Transactional
	public DocumentTaskRecord createExportTask(ProcurementExportRequest request, String idempotencyKey,
			CurrentUser operator, HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		boolean procurementExport = request != null && PROCUREMENT_EXPORT_TASK_TYPES.contains(request.taskType());
		boolean salesExport = request != null && SALES_EXPORT_TASK_TYPES.contains(request.taskType());
		boolean planningExport = request != null && PLANNING_EXPORT_TASK_TYPES.contains(request.taskType());
		if (!procurementExport && !salesExport && !planningExport) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (!planningExport) {
			requirePermission(operator, "platform:document-task:create");
		}
		if (procurementExport) {
			requireProcurementExportPermissions(request.taskType(), operator);
		}
		else if (salesExport) {
			requireSalesExportPermissions(request.taskType(), operator);
		}
		else {
			requirePlanningExportPermissions(request.taskType(), operator);
		}
		ProcurementExportRequest payload = new ProcurementExportRequest(request.taskType(),
				trimToNull(request.objectType()), request.objectId(),
				request.filters() == null ? Map.of() : new LinkedHashMap<>(request.filters()));
		String payloadJson = json(payload);
		List<ExistingTask> existing = existingTask(operator.id(), request.taskType(), idempotencyKey);
		if (!existing.isEmpty()) {
			ExistingTask existingTask = existing.getFirst();
			if (!jsonEquivalent(payloadJson, existingTask.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(existingTask.id(), operator);
		}
		try {
			Long taskId = insertQueuedTask(request.taskType(), "EXPORT", payloadJson, idempotencyKey, null,
					operator);
			this.auditService.record(operator, "DOCUMENT_TASK_CREATE", "DOCUMENT_TASK", taskId, request.taskType(),
					servletRequest);
			return get(taskId, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	@Transactional(readOnly = true)
	public List<PrintTemplateRecord> printTemplates(String sceneCode, String objectType) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("status = 'ENABLED'");
		if (hasText(sceneCode)) {
			conditions.add("scene_code = ?");
			args.add(sceneCode.trim().toUpperCase());
		}
		if (hasText(objectType)) {
			conditions.add("object_type = ?");
			args.add(objectType.trim().toUpperCase());
		}
		String where = "where " + String.join(" and ", conditions);
		return this.jdbcTemplate.query("""
				select template_code, scene_code, name, object_type, template_version
				from platform_print_template
				%s
				order by id
				""".formatted(where), (rs, rowNum) -> new PrintTemplateRecord(rs.getString("template_code"),
				rs.getString("scene_code"), rs.getString("name"), rs.getString("object_type"),
				rs.getInt("template_version")), args.toArray());
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

	@Transactional(readOnly = true)
	public PrintPreviewRecord printObjectPreview(String objectType, Long objectId, String templateCode,
			CurrentUser operator) {
		FixedPrintDefinition definition = fixedPrintDefinition(objectType);
		if (!definition.templateCode().equals(templateCode)) {
			throw new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED);
		}
		requireFixedPrintAccess(definition, objectId, operator);
		FixedPrintSnapshot snapshot = fixedPrintSnapshot(definition, objectId);
		int templateVersion = printTemplateVersion(templateCode, definition.sceneCode(), definition.objectType());
		List<PrintPreviewSection> sections = fixedPrintPreviewSections(definition, snapshot,
				fixedPrintVisibility(definition, operator));
		return new PrintPreviewRecord(templateCode, templateVersion, definition.sceneCode(), definition.objectType(),
				snapshot.objectNo(), snapshot.status(), snapshot.status(), null, null, null, null, sections);
	}

	@Transactional
	public DocumentTaskRecord createPrintTask(PrintTaskRequest request, String idempotencyKey, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		if (request != null && "PROCUREMENT_ORDER".equals(request.objectType())) {
			return createProcurementOrderPrintTask(request, idempotencyKey, operator, servletRequest);
		}
		if (request != null && "SALES_QUOTE".equals(request.objectType())) {
			return createSalesQuotePrintTask(request, idempotencyKey, operator, servletRequest);
		}
		if (request != null && hasText(request.objectType())) {
			return createFixedPrintTask(request, idempotencyKey, operator, servletRequest);
		}
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

	private DocumentTaskRecord createFixedPrintTask(PrintTaskRequest request, String idempotencyKey,
			CurrentUser operator, HttpServletRequest servletRequest) {
		if (request.objectId() == null || !hasText(request.templateCode())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		FixedPrintDefinition definition = fixedPrintDefinition(request.objectType());
		if (!definition.templateCode().equals(request.templateCode())) {
			throw new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED);
		}
		requireFixedPrintAccess(definition, request.objectId(), operator);
		FixedPrintSnapshot snapshot = fixedPrintSnapshot(definition, request.objectId());
		FixedPrintVisibility visibility = fixedPrintVisibility(definition, operator);
		int templateVersion = printTemplateVersion(request.templateCode(), definition.sceneCode(),
				definition.objectType());
		String payloadJson = json(new FixedPrintPayload(definition.objectType(), request.objectId(),
				request.templateCode(), snapshot.version(), templateVersion, visibility.amountVisible(),
				visibility.sourceVisible()));
		List<ExistingTask> existing = existingTask(operator.id(), "FIXED_DOCUMENT_PRINT", idempotencyKey);
		if (!existing.isEmpty()) {
			ExistingTask existingTask = existing.getFirst();
			if (!jsonEquivalent(payloadJson, existingTask.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(existingTask.id(), operator);
		}
		try {
			Long taskId = insertQueuedTask("FIXED_DOCUMENT_PRINT", "PRINT", payloadJson, idempotencyKey, null,
					operator);
			this.auditService.record(operator, "PRINT_TASK_CREATE", "DOCUMENT_TASK", taskId,
					request.templateCode(), servletRequest);
			return get(taskId, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	private DocumentTaskRecord createSalesQuotePrintTask(PrintTaskRequest request, String idempotencyKey,
			CurrentUser operator, HttpServletRequest servletRequest) {
		if (request.objectId() == null || !"SALES_QUOTE_V1".equals(request.templateCode())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireSalesQuotePrintAccess(request.objectId(), operator);
		SalesQuotePrintSnapshot snapshot = salesQuotePrintSnapshot(request.objectId());
		if (!"APPROVED".equals(snapshot.status())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		int templateVersion = printTemplateVersion(request.templateCode(), "SALES_QUOTE_PRINT", "SALES_QUOTE");
		String payloadJson = json(new SalesQuotePrintPayload(request.objectId(), request.templateCode(),
				snapshot.version(), templateVersion));
		List<ExistingTask> existing = existingTask(operator.id(), "SALES_QUOTE_PRINT", idempotencyKey);
		if (!existing.isEmpty()) {
			ExistingTask existingTask = existing.getFirst();
			if (!jsonEquivalent(payloadJson, existingTask.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(existingTask.id(), operator);
		}
		try {
			Long taskId = insertQueuedTask("SALES_QUOTE_PRINT", "PRINT", payloadJson, idempotencyKey, null,
					operator);
			this.auditService.record(operator, "PRINT_TASK_CREATE", "DOCUMENT_TASK", taskId,
					request.templateCode(), servletRequest);
			return get(taskId, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	private DocumentTaskRecord createProcurementOrderPrintTask(PrintTaskRequest request, String idempotencyKey,
			CurrentUser operator, HttpServletRequest servletRequest) {
		if (request.objectId() == null || !"PROCUREMENT_ORDER_V1".equals(request.templateCode())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireProcurementOrderPrintAccess(request.objectId(), operator);
		ProcurementOrderPrintSnapshot snapshot = procurementOrderPrintSnapshot(request.objectId());
		if (!PROCUREMENT_ORDER_PRINT_STATUSES.contains(snapshot.status())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		int templateVersion = printTemplateVersion(request.templateCode(), "PROCUREMENT_ORDER_PRINT",
				"PROCUREMENT_ORDER");
		String payloadJson = json(new ProcurementOrderPrintPayload(request.objectId(), request.templateCode(),
				snapshot.version(), templateVersion));
		List<ExistingTask> existing = existingTask(operator.id(), "PROCUREMENT_ORDER_PRINT", idempotencyKey);
		if (!existing.isEmpty()) {
			ExistingTask existingTask = existing.getFirst();
			if (!jsonEquivalent(payloadJson, existingTask.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(existingTask.id(), operator);
		}
		try {
			Long taskId = insertQueuedTask("PROCUREMENT_ORDER_PRINT", "PRINT", payloadJson, idempotencyKey, null,
					operator);
			this.auditService.record(operator, "PRINT_TASK_CREATE", "DOCUMENT_TASK", taskId,
					request.templateCode(), servletRequest);
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
	public PageResponse<DocumentTaskRecord> list(Long taskId, Long batchOperationId, String taskType,
			String objectKeyword, String createdByKeyword, String createdAtFrom, String createdAtTo, String status,
			int page, int pageSize,
			CurrentUser currentUser) {
		boolean viewAll = currentUser.permissions().contains("platform:document-task:view-all");
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (!viewAll) {
			conditions.add("created_by_user_id = ?");
			args.add(currentUser.id());
		}
		if (taskId != null) {
			conditions.add("id = ?");
			args.add(taskId);
		}
		if (batchOperationId != null) {
			conditions.add("""
					id in (
						select document_task_id
						from platform_batch_operation
						where id = ?
						  and document_task_id is not null
					)
					""");
			args.add(batchOperationId);
		}
		if (hasText(taskType)) {
			conditions.add("task_type = ?");
			args.add(taskType.trim().toUpperCase());
		}
		if (hasText(status)) {
			conditions.add("status = ?");
			args.add(status.trim().toUpperCase());
		}
		if (hasText(createdByKeyword)) {
			conditions.add("created_by_username ilike ?");
			args.add("%" + createdByKeyword.trim() + "%");
		}
		OffsetDateTime fromTime = parseFilterTime(createdAtFrom, false);
		if (fromTime != null) {
			conditions.add("created_at >= ?");
			args.add(fromTime);
		}
		OffsetDateTime toTime = parseFilterTime(createdAtTo, true);
		if (toTime != null) {
			conditions.add("created_at <= ?");
			args.add(toTime);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		List<DocumentTaskState> visible = this.jdbcTemplate.query("""
				select id, task_no, task_type, stage, status, idempotency_key, created_by_user_id,
				       created_by_username, total_count, success_count, error_count, result_file_id,
				       error_file_id, error_summary, attempt_count, max_attempts, created_at, started_at,
				       finished_at, expires_at, version
				from platform_document_task
				%s
				order by created_at desc, id desc
				""".formatted(where), this::mapTask, args.toArray())
			.stream()
			.filter((task) -> canAccessTask(task, currentUser))
			.filter((task) -> matchesObjectKeyword(task, objectKeyword))
			.toList();
		int from = Math.min(offset(page, pageSize), visible.size());
		int to = Math.min(from + limit(pageSize), visible.size());
		return PageResponse.of(visible.subList(from, to)
			.stream()
			.map((task) -> toDocumentTaskRecord(task, currentUser))
			.toList(), page, limit(pageSize), visible.size());
	}

	@Transactional(readOnly = true)
	public DocumentTaskRecord get(Long id, CurrentUser currentUser) {
		DocumentTaskState task = task(id);
		requireTaskAccess(task, currentUser);
		return toDocumentTaskRecord(task, currentUser);
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
		if ("PROCUREMENT_QUOTE_IMPORT".equals(task.taskType())) {
			validateSupplierQuoteImport(task, operator);
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
		if ("PROCUREMENT_QUOTE_IMPORT".equals(task.taskType())) {
			commitSupplierQuoteImport(task, operator);
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
			case "PROCUREMENT_ORDER_V1" -> "采购订单";
			default -> this.jdbcTemplate.query("""
					select name
					from platform_print_template
					where template_code = ?
					  and status = 'ENABLED'
					""", (rs, rowNum) -> rs.getString("name"), templateCode)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED));
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
		if (isHistoryImportTask(task.taskType())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
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

	public boolean isProcurementExportTaskType(String taskType) {
		return PROCUREMENT_EXPORT_TASK_TYPES.contains(taskType);
	}

	public boolean isSalesExportTaskType(String taskType) {
		return SALES_EXPORT_TASK_TYPES.contains(taskType);
	}

	public boolean isPlanningExportTaskType(String taskType) {
		return PLANNING_EXPORT_TASK_TYPES.contains(taskType);
	}

	public ProcurementExportRequest parseProcurementExportRequest(String payload) {
		return parse(payload, ProcurementExportRequest.class);
	}

	public ExportedFile procurementExportFile(ProcurementExportRequest request, CurrentUser operator) {
		if (request == null || !PROCUREMENT_EXPORT_TASK_TYPES.contains(request.taskType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireProcurementExportPermissions(request.taskType(), operator);
		ProcurementExportDataset dataset = procurementExportDataset(request);
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet(dataset.sheetName());
			writeRow(sheet.createRow(0), dataset.headers().toArray(String[]::new));
			for (int i = 0; i < dataset.rows().size(); i++) {
				Row excelRow = sheet.createRow(i + 1);
				List<String> row = dataset.rows().get(i);
				for (int j = 0; j < row.size(); j++) {
					excelRow.createCell(j).setCellValue(nullToBlank(row.get(j)));
				}
			}
			workbook.write(output);
			return new ExportedFile(procurementExportFilename(request.taskType()),
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray(),
					dataset.rows().size());
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
		}
	}

	public ExportedFile salesExportFile(ProcurementExportRequest request, CurrentUser operator) {
		if (request == null || !SALES_EXPORT_TASK_TYPES.contains(request.taskType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireSalesExportPermissions(request.taskType(), operator);
		ProcurementExportDataset dataset = salesExportDataset(request);
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet(dataset.sheetName());
			writeRow(sheet.createRow(0), dataset.headers().toArray(String[]::new));
			for (int i = 0; i < dataset.rows().size(); i++) {
				Row excelRow = sheet.createRow(i + 1);
				List<String> row = dataset.rows().get(i);
				for (int j = 0; j < row.size(); j++) {
					excelRow.createCell(j).setCellValue(nullToBlank(row.get(j)));
				}
			}
			workbook.write(output);
			return new ExportedFile(salesExportFilename(request.taskType()),
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray(),
					dataset.rows().size());
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
		}
	}

	public ExportedFile planningExportFile(ProcurementExportRequest request, CurrentUser operator) {
		if (request == null || !PLANNING_EXPORT_TASK_TYPES.contains(request.taskType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requirePlanningExportPermissions(request.taskType(), operator);
		ProcurementExportDataset dataset = planningExportDataset(request, operator);
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet(dataset.sheetName());
			writeRow(sheet.createRow(0), dataset.headers().toArray(String[]::new));
			for (int i = 0; i < dataset.rows().size(); i++) {
				Row excelRow = sheet.createRow(i + 1);
				List<String> row = dataset.rows().get(i);
				for (int j = 0; j < row.size(); j++) {
					excelRow.createCell(j).setCellValue(nullToBlank(row.get(j)));
				}
			}
			workbook.write(output);
			return new ExportedFile(planningExportFilename(request.taskType()),
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray(),
					dataset.rows().size());
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
		}
	}

	public ProcurementOrderPrintPayload parseProcurementOrderPrintPayload(String payload) {
		return parse(payload, ProcurementOrderPrintPayload.class);
	}

	public ExportedFile printProcurementOrderFile(ProcurementOrderPrintPayload payload, CurrentUser operator) {
		requireProcurementOrderPrintAccess(payload.orderId(), operator);
		ProcurementOrderPrintSnapshot snapshot = procurementOrderPrintSnapshot(payload.orderId());
		if (!snapshot.version().equals(payload.orderVersion())
				|| printTemplateVersion(payload.templateCode(), "PROCUREMENT_ORDER_PRINT", "PROCUREMENT_ORDER")
						!= payload.templateVersion()) {
			throw new BusinessException(ApiErrorCode.APPROVAL_BUSINESS_OBJECT_CHANGED);
		}
		if (!PROCUREMENT_ORDER_PRINT_STATUSES.contains(snapshot.status())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		List<ProcurementOrderPrintLine> lines = procurementOrderPrintLines(payload.orderId());
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			document.getDocumentInformation().setTitle("采购订单");
			document.getDocumentInformation().setSubject(payload.templateCode());
			document.getDocumentInformation().setCustomMetadataValue("templateVersion",
					Integer.toString(payload.templateVersion()));
			document.getDocumentInformation().setCustomMetadataValue("orderId", Long.toString(payload.orderId()));
			document.getDocumentInformation().setCustomMetadataValue("orderVersion",
					Long.toString(payload.orderVersion()));
			PDType0Font font = PDType0Font.load(document,
					new ClassPathResource("fonts/NotoSansSC-wght.ttf").getInputStream());
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(font, 12);
				content.setLeading(18);
				content.newLineAtOffset(50, 760);
				for (String line : List.of("采购订单", "订单号：" + snapshot.orderNo(), "供应商："
						+ nullToBlank(snapshot.supplierName()), "状态：" + snapshot.status(), "采购模式："
						+ snapshot.purchaseMode(), "项目ID：" + (snapshot.projectId() == null ? "" : snapshot.projectId()),
						"订单日期：" + stringDate(snapshot.orderDate()), "模板版本：" + payload.templateVersion())) {
					content.showText(line);
					content.newLine();
				}
				content.newLine();
				content.showText("明细");
				content.newLine();
				for (ProcurementOrderPrintLine line : lines) {
					content.showText(line.lineNo() + ". " + line.materialCode() + " "
							+ nullToBlank(line.materialName()) + " 数量 " + line.quantity() + " 单价 "
							+ line.taxIncludedUnitPrice());
					content.newLine();
				}
				content.endText();
			}
			document.save(output);
			return new ExportedFile("procurement-order-" + snapshot.orderNo() + ".pdf", "application/pdf",
					output.toByteArray(), 1);
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
		}
	}

	public SalesQuotePrintPayload parseSalesQuotePrintPayload(String payload) {
		return parse(payload, SalesQuotePrintPayload.class);
	}

	public FixedPrintPayload parseFixedPrintPayload(String payload) {
		return parse(payload, FixedPrintPayload.class);
	}

	public ExportedFile printSalesQuoteFile(SalesQuotePrintPayload payload, CurrentUser operator) {
		requireSalesQuotePrintAccess(payload.quoteId(), operator);
		SalesQuotePrintSnapshot snapshot = salesQuotePrintSnapshot(payload.quoteId());
		if (!snapshot.version().equals(payload.quoteVersion())
				|| printTemplateVersion(payload.templateCode(), "SALES_QUOTE_PRINT", "SALES_QUOTE")
						!= payload.templateVersion()) {
			throw new BusinessException(ApiErrorCode.APPROVAL_BUSINESS_OBJECT_CHANGED);
		}
		if (!"APPROVED".equals(snapshot.status())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		List<SalesQuotePrintLine> lines = salesQuotePrintLines(payload.quoteId());
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			document.getDocumentInformation().setTitle("销售报价");
			document.getDocumentInformation().setSubject(payload.templateCode());
			document.getDocumentInformation().setCustomMetadataValue("templateVersion",
					Integer.toString(payload.templateVersion()));
			document.getDocumentInformation().setCustomMetadataValue("quoteId", Long.toString(payload.quoteId()));
			document.getDocumentInformation().setCustomMetadataValue("quoteVersion",
					Long.toString(payload.quoteVersion()));
			PDType0Font font = PDType0Font.load(document,
					new ClassPathResource("fonts/NotoSansSC-wght.ttf").getInputStream());
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(font, 12);
				content.setLeading(18);
				content.newLineAtOffset(50, 760);
				for (String line : List.of("销售报价", "报价号：" + snapshot.quoteNo(), "客户："
						+ nullToBlank(snapshot.customerName()), "项目："
						+ nullToBlank(snapshot.projectName()), "状态：" + snapshot.status(), "报价日期："
						+ stringDate(snapshot.quoteDate()), "有效期至：" + stringDate(snapshot.validUntil()),
						"模板版本：" + payload.templateVersion())) {
					content.showText(line);
					content.newLine();
				}
				content.newLine();
				content.showText("明细");
				content.newLine();
				for (SalesQuotePrintLine line : lines) {
					content.showText(line.lineNo() + ". " + line.materialCode() + " "
							+ nullToBlank(line.materialName()) + " 数量 " + line.quantity() + " 含税单价 "
							+ line.taxIncludedUnitPrice());
					content.newLine();
				}
				content.endText();
			}
			document.save(output);
			return new ExportedFile("sales-quote-" + snapshot.quoteNo() + ".pdf", "application/pdf",
					output.toByteArray(), 1);
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
		}
	}

	public ExportedFile printFixedDocumentFile(FixedPrintPayload payload, CurrentUser operator) {
		FixedPrintDefinition definition = fixedPrintDefinition(payload.objectType());
		if (!definition.templateCode().equals(payload.templateCode())) {
			throw new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED);
		}
		requireFixedPrintAccess(definition, payload.objectId(), operator);
		FixedPrintSnapshot snapshot = fixedPrintSnapshot(definition, payload.objectId());
		if (!snapshot.version().equals(payload.objectVersion())
				|| printTemplateVersion(payload.templateCode(), definition.sceneCode(), definition.objectType())
						!= payload.templateVersion()) {
			throw new BusinessException(ApiErrorCode.APPROVAL_BUSINESS_OBJECT_CHANGED);
		}
		List<PrintPreviewSection> sections = fixedPrintPreviewSections(definition, snapshot,
				effectiveFixedPrintVisibility(definition, payload, operator));
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			document.getDocumentInformation().setTitle(definition.templateName());
			document.getDocumentInformation().setSubject(payload.templateCode());
			document.getDocumentInformation().setCustomMetadataValue("templateVersion",
					Integer.toString(payload.templateVersion()));
			document.getDocumentInformation().setCustomMetadataValue("objectType", payload.objectType());
			document.getDocumentInformation().setCustomMetadataValue("objectId", Long.toString(payload.objectId()));
			document.getDocumentInformation().setCustomMetadataValue("objectVersion",
					Long.toString(payload.objectVersion()));
			PDType0Font font = PDType0Font.load(document,
					new ClassPathResource("fonts/NotoSansSC-wght.ttf").getInputStream());
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(font, 12);
				content.setLeading(20);
				content.newLineAtOffset(50, 760);
				List<String> lines = new ArrayList<>();
				lines.add(definition.templateName());
				lines.add("模板代码：" + payload.templateCode());
				lines.add("模板版本：" + payload.templateVersion());
				for (PrintPreviewSection section : sections) {
					lines.add(section.title());
					for (PrintPreviewField field : section.fields()) {
						lines.add(field.label() + "：" + nullToBlank(field.value()));
					}
				}
				for (String line : lines) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			document.save(output);
			return new ExportedFile(payload.objectType().toLowerCase().replace("_", "-") + "-"
					+ snapshot.objectNo() + ".pdf", "application/pdf", output.toByteArray(), 1);
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
		}
	}

	private List<PrintPreviewSection> fixedPrintPreviewSections(FixedPrintDefinition definition,
			FixedPrintSnapshot snapshot, FixedPrintVisibility visibility) {
		List<PrintPreviewField> header = new ArrayList<>();
		header.add(new PrintPreviewField("模板", definition.templateName()));
		header.add(new PrintPreviewField("对象类型", definition.objectType()));
		header.add(new PrintPreviewField("单据编号", snapshot.objectNo()));
		header.add(new PrintPreviewField("业务状态", snapshot.status()));
		header.add(new PrintPreviewField("对象版本", snapshot.version().toString()));
		header.addAll(fixedPrintSubjectFields(definition, snapshot.id(), visibility));
		List<PrintPreviewField> lines = fixedPrintLineFields(definition, snapshot.id(), visibility);
		List<PrintPreviewField> summary = List.of(new PrintPreviewField("明细行数",
				Integer.toString(fixedPrintLineCount(definition, snapshot.id()))));
		return List.of(new PrintPreviewSection("单据抬头", header), new PrintPreviewSection("业务明细", lines),
				new PrintPreviewSection("业务汇总", summary));
	}

	private List<PrintPreviewField> fixedPrintSubjectFields(FixedPrintDefinition definition, Long objectId,
			FixedPrintVisibility visibility) {
		return switch (definition.objectType()) {
			case "SALES_ORDER" -> this.jdbcTemplate.query("""
					select c.name as customer_name, o.order_date, o.expected_ship_date
					from sal_sales_order o
					join mst_customer c on c.id = o.customer_id
					where o.id = ?
					""", (rs, rowNum) -> List.of(new PrintPreviewField("客户", rs.getString("customer_name")),
					new PrintPreviewField("订单日期", stringDate(rs.getObject("order_date", LocalDate.class))),
					new PrintPreviewField("预计发货日", stringDate(rs.getObject("expected_ship_date",
							LocalDate.class)))), objectId).stream().findFirst().orElse(List.of());
			case "SALES_SHIPMENT" -> this.jdbcTemplate.query("""
					select c.name as customer_name, w.name as warehouse_name, o.order_no, s.business_date
					from sal_sales_shipment s
					join mst_customer c on c.id = s.customer_id
					join mst_warehouse w on w.id = s.warehouse_id
					join sal_sales_order o on o.id = s.order_id
					where s.id = ?
					""", (rs, rowNum) -> List.of(new PrintPreviewField("客户", rs.getString("customer_name")),
					new PrintPreviewField("出库仓库", rs.getString("warehouse_name")),
					new PrintPreviewField("销售订单", rs.getString("order_no")),
					new PrintPreviewField("业务日期", stringDate(rs.getObject("business_date",
							LocalDate.class)))), objectId).stream().findFirst().orElse(List.of());
			case "PROCUREMENT_RECEIPT" -> this.jdbcTemplate.query("""
					select s.name as supplier_name, w.name as warehouse_name, o.order_no, r.business_date
					from proc_purchase_receipt r
					join mst_supplier s on s.id = r.supplier_id
					join mst_warehouse w on w.id = r.warehouse_id
					join proc_purchase_order o on o.id = r.order_id
					where r.id = ?
					""", (rs, rowNum) -> List.of(new PrintPreviewField("供应商", rs.getString("supplier_name")),
					new PrintPreviewField("入库仓库", rs.getString("warehouse_name")),
					new PrintPreviewField("采购订单", rs.getString("order_no")),
					new PrintPreviewField("业务日期", stringDate(rs.getObject("business_date",
							LocalDate.class)))), objectId).stream().findFirst().orElse(List.of());
			case "INVENTORY_TRANSFER" -> this.jdbcTemplate.query("""
					select business_date, reason
					from inv_warehouse_transfer
					where id = ?
					""", (rs, rowNum) -> List.of(new PrintPreviewField("业务日期", stringDate(rs.getObject(
							"business_date", LocalDate.class))),
					new PrintPreviewField("调拨原因", rs.getString("reason"))), objectId).stream()
				.findFirst()
				.orElse(List.of());
			case "PRODUCTION_WORK_ORDER" -> this.jdbcTemplate.query("""
					select m.code as material_code, m.name as material_name, iw.name as issue_warehouse,
					       rw.name as receipt_warehouse, w.planned_quantity, w.planned_start_date,
					       w.planned_finish_date
					from mfg_work_order w
					join mst_material m on m.id = w.product_material_id
					left join mst_warehouse iw on iw.id = w.issue_warehouse_id
					left join mst_warehouse rw on rw.id = w.receipt_warehouse_id
					where w.id = ?
					""", (rs, rowNum) -> List.of(new PrintPreviewField("产品", rs.getString("material_code")
							+ " " + nullToBlank(rs.getString("material_name"))),
					new PrintPreviewField("计划数量", exportCell(rs.getBigDecimal("planned_quantity"))),
					new PrintPreviewField("领料仓库", nullToBlank(rs.getString("issue_warehouse"))),
					new PrintPreviewField("入库仓库", nullToBlank(rs.getString("receipt_warehouse"))),
					new PrintPreviewField("计划开始", stringDate(rs.getObject("planned_start_date",
							LocalDate.class))),
					new PrintPreviewField("计划完成", stringDate(rs.getObject("planned_finish_date",
							LocalDate.class)))), objectId).stream().findFirst().orElse(List.of());
			case "PRODUCTION_MATERIAL_ISSUE" -> this.jdbcTemplate.query("""
					select w.work_order_no, i.business_date, i.reason
					from mfg_material_issue i
					join mfg_work_order w on w.id = i.work_order_id
					where i.id = ?
					""", (rs, rowNum) -> List.of(new PrintPreviewField("生产工单", rs.getString("work_order_no")),
					new PrintPreviewField("业务日期", stringDate(rs.getObject("business_date",
							LocalDate.class))),
					new PrintPreviewField("领料原因", rs.getString("reason"))), objectId).stream()
				.findFirst()
				.orElse(List.of());
			case "PRODUCTION_COMPLETION_RECEIPT" -> this.jdbcTemplate.query("""
					select w.work_order_no, wh.name as warehouse_name, r.business_date, r.quantity
					from mfg_completion_receipt r
					join mfg_work_order w on w.id = r.work_order_id
					join mst_warehouse wh on wh.id = r.receipt_warehouse_id
					where r.id = ?
					""", (rs, rowNum) -> List.of(new PrintPreviewField("生产工单", rs.getString("work_order_no")),
					new PrintPreviewField("入库仓库", rs.getString("warehouse_name")),
					new PrintPreviewField("业务日期", stringDate(rs.getObject("business_date",
							LocalDate.class))),
					new PrintPreviewField("入库数量", exportCell(rs.getBigDecimal("quantity")))), objectId).stream()
				.findFirst()
				.orElse(List.of());
			case "SALES_INVOICE" -> this.jdbcTemplate.query("""
					select c.name as customer_name, i.source_no, i.invoice_date, i.due_date,
					       i.invoice_type, i.tax_included_amount
					from fin_sales_invoice i
					join mst_customer c on c.id = i.customer_id
					where i.id = ?
					""", (rs, rowNum) -> List.of(new PrintPreviewField("客户", rs.getString("customer_name")),
					new PrintPreviewField("来源单据", rs.getString("source_no")),
					new PrintPreviewField("开票日期", stringDate(rs.getObject("invoice_date",
							LocalDate.class))),
					new PrintPreviewField("到期日", stringDate(rs.getObject("due_date", LocalDate.class))),
					new PrintPreviewField("发票类型", rs.getString("invoice_type")),
					new PrintPreviewField("价税合计", exportCell(rs.getBigDecimal("tax_included_amount")))),
					objectId).stream().findFirst().orElse(List.of());
			case "PURCHASE_INVOICE" -> this.jdbcTemplate.query("""
					select s.name as supplier_name, i.source_no, i.invoice_date, i.due_date,
					       i.settlement_kind, i.match_status, i.tax_included_amount
					from fin_purchase_invoice i
					join mst_supplier s on s.id = i.supplier_id
					where i.id = ?
					""", (rs, rowNum) -> List.of(new PrintPreviewField("供应商", rs.getString("supplier_name")),
					new PrintPreviewField("来源单据", rs.getString("source_no")),
					new PrintPreviewField("开票日期", stringDate(rs.getObject("invoice_date",
							LocalDate.class))),
					new PrintPreviewField("到期日", stringDate(rs.getObject("due_date", LocalDate.class))),
					new PrintPreviewField("结算类型", rs.getString("settlement_kind")),
					new PrintPreviewField("匹配状态", rs.getString("match_status")),
					new PrintPreviewField("价税合计", exportCell(rs.getBigDecimal("tax_included_amount")))),
					objectId).stream().findFirst().orElse(List.of());
			case "ACCOUNTING_VOUCHER" -> this.jdbcTemplate.query("""
					select l.name as ledger_name, p.period_code, v.voucher_date, v.summary,
					       v.source_type, v.source_original_type, v.source_no, v.debit_total, v.credit_total
					from gl_voucher v
					join gl_ledger l on l.id = v.ledger_id
					join gl_accounting_period p on p.id = v.accounting_period_id
					where v.id = ?
					""", (rs, rowNum) -> List.of(new PrintPreviewField("账簿", rs.getString("ledger_name")),
					new PrintPreviewField("会计期间", rs.getString("period_code")),
					new PrintPreviewField("凭证日期", stringDate(rs.getObject("voucher_date",
							LocalDate.class))),
					new PrintPreviewField("摘要", visibleVoucherSummary(rs.getString("summary"),
							rs.getString("source_type"), rs.getString("source_original_type"),
							visibility.sourceVisible())),
					new PrintPreviewField("来源单据", visibility.sourceVisible() ? nullToBlank(rs.getString("source_no"))
							: null),
					new PrintPreviewField("借方合计", visibility.amountVisible()
							? exportCell(rs.getBigDecimal("debit_total")) : null),
					new PrintPreviewField("贷方合计", visibility.amountVisible()
							? exportCell(rs.getBigDecimal("credit_total")) : null)), objectId)
				.stream()
				.findFirst()
				.orElse(List.of());
			default -> List.of();
		};
	}

	private List<PrintPreviewField> fixedPrintLineFields(FixedPrintDefinition definition, Long objectId,
			FixedPrintVisibility visibility) {
		switch (definition.objectType()) {
			case "SALES_ORDER" -> {
				return this.jdbcTemplate.query("""
					select ol.line_no, m.code as material_code, m.name as material_name, ol.quantity
					from sal_sales_order_line ol
					join mst_material m on m.id = ol.material_id
					where ol.order_id = ?
					order by ol.line_no
					limit 20
					""", (rs, rowNum) -> new PrintPreviewField("第 " + rs.getInt("line_no") + " 行",
					rs.getString("material_code") + " " + nullToBlank(rs.getString("material_name"))
							+ " 数量 " + rs.getBigDecimal("quantity").stripTrailingZeros().toPlainString()),
					objectId);
			}
			case "SALES_SHIPMENT" -> {
				return this.jdbcTemplate.query("""
						select l.line_no, m.code as material_code, m.name as material_name, u.name as unit_name,
						       l.quantity, l.tax_included_amount
						from sal_sales_shipment_line l
						join mst_material m on m.id = l.material_id
						join mst_unit u on u.id = l.unit_id
						where l.shipment_id = ?
						order by l.line_no
						limit 20
						""", (rs, rowNum) -> new PrintPreviewField("第 " + rs.getInt("line_no") + " 行",
						rs.getString("material_code") + " " + nullToBlank(rs.getString("material_name"))
								+ " 数量 " + exportCell(rs.getBigDecimal("quantity"))
								+ " " + nullToBlank(rs.getString("unit_name"))
								+ " 价税合计 " + exportCell(rs.getBigDecimal("tax_included_amount"))),
						objectId);
			}
			case "PROCUREMENT_RECEIPT" -> {
				return this.jdbcTemplate.query("""
						select l.line_no, m.code as material_code, m.name as material_name, u.name as unit_name,
						       l.quantity
						from proc_purchase_receipt_line l
						join mst_material m on m.id = l.material_id
						join mst_unit u on u.id = l.unit_id
						where l.receipt_id = ?
						order by l.line_no
						limit 20
						""", (rs, rowNum) -> new PrintPreviewField("第 " + rs.getInt("line_no") + " 行",
						rs.getString("material_code") + " " + nullToBlank(rs.getString("material_name"))
								+ " 数量 " + exportCell(rs.getBigDecimal("quantity"))
								+ " " + nullToBlank(rs.getString("unit_name"))),
						objectId);
			}
			case "INVENTORY_TRANSFER" -> {
				return this.jdbcTemplate.query("""
						select l.line_no, sw.name as source_warehouse, tw.name as target_warehouse,
						       m.code as material_code, m.name as material_name, u.name as unit_name,
						       l.quality_status, l.quantity
						from inv_warehouse_transfer_line l
						join mst_warehouse sw on sw.id = l.source_warehouse_id
						join mst_warehouse tw on tw.id = l.target_warehouse_id
						join mst_material m on m.id = l.material_id
						join mst_unit u on u.id = l.unit_id
						where l.transfer_id = ?
						order by l.line_no
						limit 20
						""", (rs, rowNum) -> new PrintPreviewField("第 " + rs.getInt("line_no") + " 行",
						nullToBlank(rs.getString("source_warehouse")) + " -> "
								+ nullToBlank(rs.getString("target_warehouse"))
								+ " " + rs.getString("material_code") + " "
								+ nullToBlank(rs.getString("material_name"))
								+ " 数量 " + exportCell(rs.getBigDecimal("quantity"))
								+ " " + nullToBlank(rs.getString("unit_name"))
								+ " 质量状态 " + rs.getString("quality_status")),
						objectId);
			}
			case "PRODUCTION_WORK_ORDER" -> {
				return this.jdbcTemplate.query("""
						select l.line_no, m.code as material_code, m.name as material_name, u.name as unit_name,
						       l.required_quantity, l.issued_quantity
						from mfg_work_order_material l
						join mst_material m on m.id = l.material_id
						join mst_unit u on u.id = l.unit_id
						where l.work_order_id = ?
						order by l.line_no
						limit 20
						""", (rs, rowNum) -> new PrintPreviewField("第 " + rs.getInt("line_no") + " 行",
						rs.getString("material_code") + " " + nullToBlank(rs.getString("material_name"))
								+ " 需求 " + exportCell(rs.getBigDecimal("required_quantity"))
								+ " " + nullToBlank(rs.getString("unit_name"))
								+ " 已领 " + exportCell(rs.getBigDecimal("issued_quantity"))),
						objectId);
			}
			case "PRODUCTION_MATERIAL_ISSUE" -> {
				return this.jdbcTemplate.query("""
						select l.line_no, w.name as warehouse_name, m.code as material_code,
						       m.name as material_name, u.name as unit_name, l.quantity
						from mfg_material_issue_line l
						join mst_warehouse w on w.id = l.warehouse_id
						join mst_material m on m.id = l.material_id
						join mst_unit u on u.id = l.unit_id
						where l.issue_id = ?
						order by l.line_no
						limit 20
						""", (rs, rowNum) -> new PrintPreviewField("第 " + rs.getInt("line_no") + " 行",
						nullToBlank(rs.getString("warehouse_name")) + " "
								+ rs.getString("material_code") + " "
								+ nullToBlank(rs.getString("material_name"))
								+ " 数量 " + exportCell(rs.getBigDecimal("quantity"))
								+ " " + nullToBlank(rs.getString("unit_name"))),
						objectId);
			}
			case "PRODUCTION_COMPLETION_RECEIPT" -> {
				return this.jdbcTemplate.query("""
						select 1 as line_no, w.work_order_no, m.code as material_code, m.name as material_name,
						       u.name as unit_name, r.quantity
						from mfg_completion_receipt r
						join mfg_work_order w on w.id = r.work_order_id
						join mst_material m on m.id = w.product_material_id
						join mst_unit u on u.id = m.unit_id
						where r.id = ?
						""", (rs, rowNum) -> new PrintPreviewField("第 " + rs.getInt("line_no") + " 行",
						rs.getString("work_order_no") + " " + rs.getString("material_code") + " "
								+ nullToBlank(rs.getString("material_name"))
								+ " 数量 " + exportCell(rs.getBigDecimal("quantity"))
								+ " " + nullToBlank(rs.getString("unit_name"))),
						objectId);
			}
			case "SALES_INVOICE" -> {
				return this.jdbcTemplate.query("""
						select l.line_no, m.code as material_code, m.name as material_name, u.name as unit_name,
						       l.quantity, l.tax_included_amount
						from fin_sales_invoice_line l
						join mst_material m on m.id = l.material_id
						join mst_unit u on u.id = l.unit_id
						where l.sales_invoice_id = ?
						order by l.line_no
						limit 20
						""", (rs, rowNum) -> new PrintPreviewField("第 " + rs.getInt("line_no") + " 行",
						rs.getString("material_code") + " " + nullToBlank(rs.getString("material_name"))
								+ " 数量 " + exportCell(rs.getBigDecimal("quantity"))
								+ " " + nullToBlank(rs.getString("unit_name"))
								+ " 价税合计 " + exportCell(rs.getBigDecimal("tax_included_amount"))),
						objectId);
			}
			case "PURCHASE_INVOICE" -> {
				return this.jdbcTemplate.query("""
						select l.line_no, m.code as material_code, m.name as material_name, u.name as unit_name,
						       l.quantity, l.tax_included_amount, l.match_status
						from fin_purchase_invoice_line l
						join mst_material m on m.id = l.material_id
						join mst_unit u on u.id = l.unit_id
						where l.purchase_invoice_id = ?
						order by l.line_no
						limit 20
						""", (rs, rowNum) -> new PrintPreviewField("第 " + rs.getInt("line_no") + " 行",
						rs.getString("material_code") + " " + nullToBlank(rs.getString("material_name"))
								+ " 数量 " + exportCell(rs.getBigDecimal("quantity"))
								+ " " + nullToBlank(rs.getString("unit_name"))
								+ " 价税合计 " + exportCell(rs.getBigDecimal("tax_included_amount"))
								+ " 匹配状态 " + rs.getString("match_status")),
						objectId);
			}
			case "ACCOUNTING_VOUCHER" -> {
				return this.jdbcTemplate.query("""
						select line_no, account_code, account_name, summary, debit_amount, credit_amount
						from gl_voucher_line
						where voucher_id = ?
						order by line_no
						limit 20
						""", (rs, rowNum) -> new PrintPreviewField("第 " + rs.getInt("line_no") + " 行",
						rs.getString("account_code") + " " + nullToBlank(rs.getString("account_name"))
								+ " 摘要 " + nullToBlank(rs.getString("summary"))
								+ " 借方 " + maskedAmountCell(rs.getBigDecimal("debit_amount"), visibility)
								+ " 贷方 " + maskedAmountCell(rs.getBigDecimal("credit_amount"), visibility)),
						objectId);
			}
			default -> {
			}
		}
		int count = fixedPrintLineCount(definition, objectId);
		return count == 0 ? List.of(new PrintPreviewField("明细", "无明细行"))
				: List.of(new PrintPreviewField("明细", "共 " + count + " 行"));
	}

	private int fixedPrintLineCount(FixedPrintDefinition definition, Long objectId) {
		FixedPrintLineSpec spec = fixedPrintLineSpec(definition.objectType());
		if (spec == null) {
			return 0;
		}
		Integer count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from %s
				where %s = ?
				""".formatted(spec.tableName(), spec.parentColumn()), Integer.class, objectId);
		return count == null ? 0 : count;
	}

	private FixedPrintLineSpec fixedPrintLineSpec(String objectType) {
		return switch (objectType) {
			case "SALES_ORDER" -> new FixedPrintLineSpec("sal_sales_order_line", "order_id");
			case "SALES_SHIPMENT" -> new FixedPrintLineSpec("sal_sales_shipment_line", "shipment_id");
			case "PROCUREMENT_RECEIPT" -> new FixedPrintLineSpec("proc_purchase_receipt_line", "receipt_id");
			case "INVENTORY_TRANSFER" -> new FixedPrintLineSpec("inv_warehouse_transfer_line", "transfer_id");
			case "PRODUCTION_WORK_ORDER" -> new FixedPrintLineSpec("mfg_work_order_material", "work_order_id");
			case "PRODUCTION_MATERIAL_ISSUE" -> new FixedPrintLineSpec("mfg_material_issue_line", "issue_id");
			case "PRODUCTION_COMPLETION_RECEIPT" -> new FixedPrintLineSpec("mfg_completion_receipt", "id");
			case "SALES_INVOICE" -> new FixedPrintLineSpec("fin_sales_invoice_line", "sales_invoice_id");
			case "PURCHASE_INVOICE" -> new FixedPrintLineSpec("fin_purchase_invoice_line", "purchase_invoice_id");
			case "ACCOUNTING_VOUCHER" -> new FixedPrintLineSpec("gl_voucher_line", "voucher_id");
			default -> null;
		};
	}

	private ProcurementExportDataset procurementExportDataset(ProcurementExportRequest request) {
		String keyword = filterString(request.filters(), "keyword");
		return switch (request.taskType()) {
			case "PROCUREMENT_REQUISITION_EXPORT" -> new ProcurementExportDataset("采购请购",
					List.of("ID", "请购号", "采购模式", "项目ID", "状态", "需求日期", "用途"),
					procurementRows("""
							select id, requisition_no, purchase_mode, project_id, status, required_date, purpose
							from proc_purchase_requisition
							where (? is null or requisition_no ilike ? or coalesce(purpose, '') ilike ?)
							and (? is null or ? <> 'PROCUREMENT_REQUISITION' or id = ?)
							order by id desc
							""", keyword, request.objectType(), request.objectId()));
			case "PROCUREMENT_INQUIRY_EXPORT" -> new ProcurementExportDataset("采购询价",
					List.of("ID", "询价号", "标题", "采购模式", "项目ID", "状态"),
					procurementRows("""
							select id, inquiry_no, title, purchase_mode, project_id, status
							from proc_purchase_inquiry
							where (? is null or inquiry_no ilike ? or coalesce(title, '') ilike ?)
							and (? is null or ? <> 'PROCUREMENT_INQUIRY' or id = ?)
							order by id desc
							""", keyword, request.objectType(), request.objectId()));
			case "PROCUREMENT_QUOTE_EXPORT" -> new ProcurementExportDataset("供应商报价",
					List.of("ID", "报价号", "询价号", "供应商", "状态", "有效期起", "有效期止", "币种"),
					procurementRows("""
							select q.id, q.quote_no, i.inquiry_no, s.name as supplier_name, q.status,
							       q.valid_from, q.valid_to, q.currency
							from proc_supplier_quote q
							join proc_purchase_inquiry i on i.id = q.inquiry_id
							join mst_supplier s on s.id = q.supplier_id
							where (? is null or q.quote_no ilike ? or i.inquiry_no ilike ? or s.name ilike ?)
							and (? is null or ? <> 'PROCUREMENT_QUOTE' or q.id = ?)
							order by q.id desc
							""", keyword, request.objectType(), request.objectId()));
			case "PROCUREMENT_PRICE_AGREEMENT_EXPORT" -> new ProcurementExportDataset("采购价格协议",
					List.of("ID", "协议号", "供应商", "采购模式", "项目ID", "状态", "有效期起", "有效期止"),
					procurementRows("""
							select a.id, a.agreement_no, s.name as supplier_name, a.purchase_mode, a.project_id,
							       a.status, a.valid_from, a.valid_to
							from proc_price_agreement a
							join mst_supplier s on s.id = a.supplier_id
							where (? is null or a.agreement_no ilike ? or s.name ilike ?)
							and (? is null or ? <> 'PROCUREMENT_PRICE_AGREEMENT' or a.id = ?)
							order by a.id desc
							""", keyword, request.objectType(), request.objectId()));
			case "PROCUREMENT_ORDER_EXPORT" -> new ProcurementExportDataset("采购订单",
					List.of("ID", "订单号", "供应商", "采购模式", "项目ID", "状态", "订单日期", "预计到货日"),
					procurementRows("""
							select o.id, o.order_no, s.name as supplier_name, o.purchase_mode, o.project_id,
							       o.status, o.order_date, o.expected_arrival_date
							from proc_purchase_order o
							join mst_supplier s on s.id = o.supplier_id
							where (? is null or o.order_no ilike ? or s.name ilike ?)
							and (? is null or ? <> 'PROCUREMENT_ORDER' or o.id = ?)
							order by o.id desc
							""", keyword, request.objectType(), request.objectId()));
			case "PROCUREMENT_SCHEDULE_EXPORT" -> new ProcurementExportDataset("到货计划",
					List.of("ID", "订单号", "订单行", "计划序号", "计划日期", "计划数量", "已收数量", "状态"),
					procurementScheduleRows(request, keyword));
			case "PROCUREMENT_SUPPLY_EXPORT" -> new ProcurementExportDataset("有效供给",
					List.of("订单号", "订单行ID", "计划ID", "采购模式", "项目ID", "物料", "预计到货日", "剩余数量",
							"订单状态", "计划状态"),
					procurementRows("""
							select s.order_no, s.order_line_id, s.schedule_id, s.purchase_mode, s.project_id,
							       m.code as material_code, s.expected_arrival_date, s.remaining_quantity,
							       s.order_status, s.schedule_status
							from proc_effective_purchase_supply s
							join mst_material m on m.id = s.material_id
							where (? is null or s.order_no ilike ? or m.code ilike ? or m.name ilike ?)
							and (? is null or ? <> 'PROCUREMENT_ORDER' or s.order_id = ?)
							order by s.expected_arrival_date, s.order_no
							""", keyword, request.objectType(), request.objectId()));
			default -> throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		};
	}

	private ProcurementExportDataset salesExportDataset(ProcurementExportRequest request) {
		return switch (request.taskType()) {
			case "SALES_QUOTE_EXPORT" -> new ProcurementExportDataset("销售报价",
					List.of("ID", "报价号", "客户", "项目", "合同", "状态", "报价日期", "有效期至", "币种", "含税金额"),
					salesQuoteExportRows(request));
			case "SALES_DELIVERY_PLAN_EXPORT" -> new ProcurementExportDataset("销售交付计划",
					List.of("ID", "订单号", "订单行", "计划序号", "计划日期", "计划数量", "已发数量", "剩余数量", "状态",
							"物料"),
					salesDeliveryPlanExportRows(request));
			case "SALES_EFFECTIVE_DEMAND_EXPORT" -> new ProcurementExportDataset("有效销售需求",
					List.of("ID", "订单号", "订单行", "项目", "客户", "合同", "物料", "订单数量", "已发数量", "已退数量",
							"未履约数量", "预计日期", "状态", "计入有效需求", "排除原因"),
					salesEffectiveDemandExportRows(request));
			default -> throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		};
	}

	private ProcurementExportDataset planningExportDataset(ProcurementExportRequest request, CurrentUser operator) {
		return switch (request.taskType()) {
			case "MATERIAL_REQUIREMENT_RUN_EXPORT" -> new ProcurementExportDataset("缺料净算快照",
					List.of("运行ID", "运行编号", "范围", "项目ID", "状态", "来源指纹", "计算时间", "过期时间",
							"需求行数", "短缺行数", "建议数"),
					materialRequirementRunExportRows(request, operator));
			default -> throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		};
	}

	private List<List<String>> materialRequirementRunExportRows(ProcurementExportRequest request,
			CurrentUser operator) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (request.objectId() != null && "MATERIAL_REQUIREMENT_RUN".equals(request.objectType())) {
			conditions.add("r.id = ?");
			args.add(request.objectId());
		}
		Long runId = filterLong(request.filters(), "runId");
		if (runId != null) {
			conditions.add("r.id = ?");
			args.add(runId);
		}
		Long projectId = filterLong(request.filters(), "projectId");
		if (projectId != null) {
			conditions.add("r.project_id = ?");
			args.add(projectId);
		}
		Long customerId = filterLong(request.filters(), "customerId");
		if (customerId != null) {
			conditions.add("""
					(r.customer_id = ? or exists (
						select 1
						from mrp_requirement_line rl
						join sal_sales_order so on so.id = rl.demand_source_id
						where rl.run_id = r.id
						and rl.demand_source_type = 'SALES_ORDER'
						and so.customer_id = ?
					))
					""");
			args.add(customerId);
			args.add(customerId);
		}
		Long contractId = filterLong(request.filters(), "contractId");
		if (contractId != null) {
			conditions.add("""
					(r.contract_id = ? or exists (
						select 1
						from mrp_requirement_line rl
						join sal_sales_order so on so.id = rl.demand_source_id
						where rl.run_id = r.id
						and rl.demand_source_type = 'SALES_ORDER'
						and so.contract_id = ?
					))
					""");
			args.add(contractId);
			args.add(contractId);
		}
		Long orderId = filterLong(request.filters(), "orderId");
		if (orderId != null) {
			conditions.add("""
					(r.sales_order_id = ? or exists (
						select 1
						from mrp_requirement_line rl
						where rl.run_id = r.id
						and rl.demand_source_type = 'SALES_ORDER'
						and rl.demand_source_id = ?
					))
					""");
			args.add(orderId);
			args.add(orderId);
		}
		Long materialId = filterLong(request.filters(), "materialId");
		if (materialId != null) {
			conditions.add("""
					(r.material_id = ? or exists (
						select 1
						from mrp_requirement_line rl
						where rl.run_id = r.id
						and rl.material_id = ?
					))
					""");
			args.add(materialId);
			args.add(materialId);
		}
		LocalDate requiredDateTo = filterDate(request.filters(), "requiredDateTo");
		if (requiredDateTo != null) {
			conditions.add("r.demand_date_to = ?");
			args.add(requiredDateTo);
		}
		String status = filterString(request.filters(), "status");
		if (status != null) {
			conditions.add("r.status = ?");
			args.add(status);
		}
		Boolean expired = filterBoolean(request.filters(), "expired");
		if (Boolean.TRUE.equals(expired)) {
			conditions.add("(r.status = 'EXPIRED' or r.expires_at <= now())");
		}
		else if (Boolean.FALSE.equals(expired)) {
			conditions.add("not (r.status = 'EXPIRED' or r.expires_at <= now())");
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		boolean projectVisible = operator == null || operator.permissions().contains("sales:project:view")
				|| operator.permissions().contains("sales:order:view");
		List<Object> queryArgs = new ArrayList<>();
		queryArgs.add(projectVisible);
		queryArgs.addAll(args);
		return exportRows("""
				select r.id, r.run_no, r.scope_type, case when ? then r.project_id else null end as project_id,
				       r.status, r.source_fingerprint,
				       r.calculated_at, r.expires_at, count(distinct rl.id) as requirement_count,
				       count(distinct case when rl.shortage_quantity > 0 then rl.id end) as shortage_count,
				       count(distinct s.id) as suggestion_count
				from mrp_calculation_run r
				left join mrp_requirement_line rl on rl.run_id = r.id
				left join mrp_suggestion s on s.run_id = r.id
				%s
				group by r.id
				order by r.created_at desc, r.id desc
				""".formatted(where), queryArgs);
	}

	private List<List<String>> salesQuoteExportRows(ProcurementExportRequest request) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		String keyword = filterString(request.filters(), "keyword");
		if (keyword != null) {
			conditions.add("(q.quote_no ilike ? or c.name ilike ? or c.code ilike ?)");
			String pattern = "%" + keyword + "%";
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
		}
		Long customerId = filterLong(request.filters(), "customerId");
		if (customerId != null) {
			conditions.add("q.customer_id = ?");
			args.add(customerId);
		}
		Long projectId = filterLong(request.filters(), "projectId");
		if (projectId != null) {
			conditions.add("q.project_id = ?");
			args.add(projectId);
		}
		String status = filterString(request.filters(), "status");
		if (status != null) {
			conditions.add("q.status = ?");
			args.add(status);
		}
		String approvalStatus = filterString(request.filters(), "approvalStatus");
		if (approvalStatus != null) {
			switch (approvalStatus.toUpperCase()) {
				case "APPROVED" -> conditions.add("q.status in ('APPROVED', 'CONVERTED')");
				case "SUBMITTED", "PENDING", "IN_APPROVAL" -> conditions.add(
						"q.approval_instance_id is not null and q.status = 'DRAFT'");
				case "NONE", "NOT_SUBMITTED" -> conditions.add(
						"q.approval_instance_id is null and q.status = 'DRAFT' and not exists ("
								+ salesQuoteApprovalInstanceExistsSql() + ")");
				case "REJECTED", "WITHDRAWN", "CANCELLED" -> conditions.add(
						"q.approval_instance_id is null and q.status = 'DRAFT' and "
								+ latestSalesQuoteApprovalStatusExpression() + " = '" + approvalStatus.toUpperCase() + "'");
				default -> conditions.add("1 = 0");
			}
		}
		LocalDate validFrom = filterDate(request.filters(), "validFrom");
		if (validFrom != null) {
			conditions.add("q.valid_until >= ?");
			args.add(validFrom);
		}
		LocalDate validTo = filterDate(request.filters(), "validTo");
		if (validTo != null) {
			conditions.add("q.valid_until <= ?");
			args.add(validTo);
		}
		if (request.objectId() != null && "SALES_QUOTE".equals(request.objectType())) {
			conditions.add("q.id = ?");
			args.add(request.objectId());
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return exportRows("""
				select q.id, q.quote_no, c.name as customer_name, p.name as project_name, pc.contract_no,
				       q.status, q.quote_date, q.valid_until, q.currency, q.tax_included_amount
				from sal_sales_quote q
				join mst_customer c on c.id = q.customer_id
				left join sal_project p on p.id = q.project_id
				left join sal_project_contract pc on pc.id = q.contract_id
				%s
				order by q.updated_at desc, q.id desc
				""".formatted(where), args);
	}

	private String latestSalesQuoteApprovalStatusExpression() {
		return """
				(
					select ai.status
					from platform_approval_instance ai
					where ai.scene_code = 'SALES_QUOTE_APPROVAL'
					and ai.business_object_type = 'SALES_QUOTE'
					and ai.business_object_id = q.id
					order by ai.created_at desc, ai.id desc
					limit 1
				)
				""";
	}

	private String salesQuoteApprovalInstanceExistsSql() {
		return """
				select 1
				from platform_approval_instance ai
				where ai.scene_code = 'SALES_QUOTE_APPROVAL'
				and ai.business_object_type = 'SALES_QUOTE'
				and ai.business_object_id = q.id
				""";
	}

	private List<List<String>> salesDeliveryPlanExportRows(ProcurementExportRequest request) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		String keyword = filterString(request.filters(), "keyword");
		if (keyword != null) {
			conditions.add("(o.order_no ilike ? or c.code ilike ? or c.name ilike ? or m.code ilike ? "
					+ "or m.name ilike ? or p.close_reason ilike ?)");
			String pattern = "%" + keyword + "%";
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
		}
		Long customerId = filterLong(request.filters(), "customerId");
		if (customerId != null) {
			conditions.add("o.customer_id = ?");
			args.add(customerId);
		}
		Long orderId = filterLong(request.filters(), "orderId");
		if (orderId != null) {
			conditions.add("p.order_id = ?");
			args.add(orderId);
		}
		Long projectId = filterLong(request.filters(), "projectId");
		if (projectId != null) {
			conditions.add("o.project_id = ?");
			args.add(projectId);
		}
		Long contractId = filterLong(request.filters(), "contractId");
		if (contractId != null) {
			conditions.add("o.contract_id = ?");
			args.add(contractId);
		}
		Long materialId = filterLong(request.filters(), "materialId");
		if (materialId != null) {
			conditions.add("l.material_id = ?");
			args.add(materialId);
		}
		String status = filterString(request.filters(), "status");
		if (status != null) {
			conditions.add("p.status = ?");
			args.add(status);
		}
		if (Boolean.TRUE.equals(filterBoolean(request.filters(), "countedOnly"))) {
			conditions.add("p.status in ('PLANNED', 'PARTIALLY_SHIPPED')");
		}
		LocalDate expectedDateFrom = filterDate(request.filters(), "expectedDateFrom");
		if (expectedDateFrom != null) {
			conditions.add("p.planned_date >= ?");
			args.add(expectedDateFrom);
		}
		LocalDate expectedDateTo = filterDate(request.filters(), "expectedDateTo");
		if (expectedDateTo != null) {
			conditions.add("p.planned_date <= ?");
			args.add(expectedDateTo);
		}
		if (request.objectId() != null && "SALES_ORDER".equals(request.objectType())) {
			conditions.add("p.order_id = ?");
			args.add(request.objectId());
		}
		if (request.objectId() != null && "SALES_DELIVERY_PLAN".equals(request.objectType())) {
			conditions.add("p.id = ?");
			args.add(request.objectId());
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return exportRows("""
				select p.id, o.order_no, l.line_no as order_line_no, p.line_no as plan_line_no,
				       p.planned_date, p.planned_quantity, p.shipped_quantity,
				       (p.planned_quantity - p.shipped_quantity) as remaining_quantity,
				       p.status, m.code as material_code
				from sal_sales_delivery_plan p
				join sal_sales_order o on o.id = p.order_id
				join sal_sales_order_line l on l.id = p.order_line_id
				join mst_material m on m.id = l.material_id
				join mst_customer c on c.id = o.customer_id
				%s
				order by p.planned_date asc, p.line_no asc, p.id asc
				""".formatted(where), args);
	}

	private List<List<String>> salesEffectiveDemandExportRows(ProcurementExportRequest request) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		Long projectId = filterLong(request.filters(), "projectId");
		if (projectId != null) {
			conditions.add("d.project_id = ?");
			args.add(projectId);
		}
		Long customerId = filterLong(request.filters(), "customerId");
		if (customerId != null) {
			conditions.add("d.customer_id = ?");
			args.add(customerId);
		}
		Long contractId = filterLong(request.filters(), "contractId");
		if (contractId != null) {
			conditions.add("d.contract_id = ?");
			args.add(contractId);
		}
		Long orderId = filterLong(request.filters(), "orderId");
		if (orderId != null) {
			conditions.add("d.order_id = ?");
			args.add(orderId);
		}
		Long materialId = filterLong(request.filters(), "materialId");
		if (materialId != null) {
			conditions.add("d.material_id = ?");
			args.add(materialId);
		}
		if (Boolean.TRUE.equals(filterBoolean(request.filters(), "countedOnly"))) {
			conditions.add("d.counted_as_effective_demand = true");
		}
		LocalDate expectedDateFrom = filterDate(request.filters(), "expectedDateFrom");
		if (expectedDateFrom != null) {
			conditions.add("coalesce(p.planned_date, o.expected_ship_date, o.order_date) >= ?");
			args.add(expectedDateFrom);
		}
		LocalDate expectedDateTo = filterDate(request.filters(), "expectedDateTo");
		if (expectedDateTo != null) {
			conditions.add("coalesce(p.planned_date, o.expected_ship_date, o.order_date) <= ?");
			args.add(expectedDateTo);
		}
		String status = filterString(request.filters(), "status");
		if (status != null) {
			conditions.add("""
					case
					    when not d.counted_as_effective_demand then 'EXCLUDED'
					    when coalesce(p.planned_date, o.expected_ship_date, o.order_date) < current_date
					         and d.open_demand_quantity > 0 then 'OVERDUE'
					    when d.shipped_quantity > 0 then 'PARTIALLY_SHIPPED'
					    else 'OPEN'
					end = ?
					""");
			args.add(status);
		}
		if (request.objectId() != null && "SALES_ORDER".equals(request.objectType())) {
			conditions.add("d.order_id = ?");
			args.add(request.objectId());
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return exportRows("""
				select d.id, d.order_no, d.order_line_id, d.project_name, d.customer_name, d.contract_no,
				       d.material_code, d.ordered_quantity, d.shipped_quantity, d.returned_quantity,
				       d.open_demand_quantity, coalesce(p.planned_date, o.expected_ship_date, o.order_date) as expected_date,
				       case
				           when not d.counted_as_effective_demand then 'EXCLUDED'
				           when coalesce(p.planned_date, o.expected_ship_date, o.order_date) < current_date
				                and d.open_demand_quantity > 0 then 'OVERDUE'
				           when d.shipped_quantity > 0 then 'PARTIALLY_SHIPPED'
				           else 'OPEN'
				       end as status,
				       d.counted_as_effective_demand,
				       d.excluded_reason_code
				from sal_effective_sales_demand d
				join sal_sales_order o on o.id = d.order_id
				left join sal_sales_delivery_plan p on p.order_line_id = d.order_line_id
				%s
				order by expected_date asc, d.order_no asc, d.order_line_id asc
				""".formatted(where), args);
	}

	private List<List<String>> procurementRows(String sql, String keyword, String objectType, Long objectId) {
		String pattern = keyword == null ? null : "%" + keyword + "%";
		int keywordPlaceholders = placeholderCountBeforeObjectFilter(sql);
		List<Object> args = new ArrayList<>();
		args.add(keyword);
		for (int i = 1; i < keywordPlaceholders; i++) {
			args.add(pattern);
		}
		args.add(objectId);
		args.add(objectType);
		args.add(objectId);
		return this.jdbcTemplate.queryForList(sql, args.toArray())
			.stream()
			.map((row) -> row.values().stream().map(this::exportCell).toList())
			.toList();
	}

	private List<List<String>> exportRows(String sql, List<Object> args) {
		return this.jdbcTemplate.queryForList(sql, args.toArray())
			.stream()
			.map((row) -> row.values().stream().map(this::exportCell).toList())
			.toList();
	}

	private List<List<String>> procurementScheduleRows(ProcurementExportRequest request, String keyword) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (keyword != null) {
			conditions.add("o.order_no ilike ?");
			args.add("%" + keyword + "%");
		}
		if (request.objectId() != null && "PROCUREMENT_ORDER".equals(request.objectType())) {
			conditions.add("o.id = ?");
			args.add(request.objectId());
		}
		if (request.objectId() != null && "PROCUREMENT_SCHEDULE".equals(request.objectType())) {
			conditions.add("sch.id = ?");
			args.add(request.objectId());
		}
		String status = filterString(request.filters(), "status");
		if (status != null) {
			conditions.add("sch.status = ?");
			args.add(status);
		}
		LocalDate expectedDateFrom = filterDate(request.filters(), "expectedDateFrom");
		if (expectedDateFrom != null) {
			conditions.add("sch.planned_date >= ?");
			args.add(expectedDateFrom);
		}
		LocalDate expectedDateTo = filterDate(request.filters(), "expectedDateTo");
		if (expectedDateTo != null) {
			conditions.add("sch.planned_date <= ?");
			args.add(expectedDateTo);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return this.jdbcTemplate.queryForList("""
				select sch.id, o.order_no, ol.line_no as order_line_no, sch.line_no as schedule_no,
				       sch.planned_date, sch.planned_quantity, sch.received_quantity, sch.status
				from proc_purchase_order_schedule sch
				join proc_purchase_order_line ol on ol.id = sch.order_line_id
				join proc_purchase_order o on o.id = ol.order_id
				%s
				order by sch.planned_date, sch.line_no, sch.id
				""".formatted(where), args.toArray())
			.stream()
			.map((row) -> row.values().stream().map(this::exportCell).toList())
			.toList();
	}

	private int placeholderCountBeforeObjectFilter(String sql) {
		int objectFilter = sql.indexOf("and (? is null or ? <>");
		String keywordPart = objectFilter < 0 ? sql : sql.substring(0, objectFilter);
		int count = 0;
		for (int i = 0; i < keywordPart.length(); i++) {
			if (keywordPart.charAt(i) == '?') {
				count++;
			}
		}
		return count;
	}

	private String exportCell(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof BigDecimal decimal) {
			return decimal.stripTrailingZeros().toPlainString();
		}
		return value.toString();
	}

	private String procurementExportFilename(String taskType) {
		return taskType.toLowerCase().replace('_', '-') + "-" + OffsetDateTime.now().format(TASK_NO_FORMATTER)
				+ ".xlsx";
	}

	private String salesExportFilename(String taskType) {
		return taskType.toLowerCase().replace('_', '-') + "-" + OffsetDateTime.now().format(TASK_NO_FORMATTER)
				+ ".xlsx";
	}

	private String planningExportFilename(String taskType) {
		return taskType.toLowerCase().replace('_', '-') + "-" + OffsetDateTime.now().format(TASK_NO_FORMATTER)
				+ ".xlsx";
	}

	private String filterString(Map<String, Object> filters, String key) {
		if (filters == null || !filters.containsKey(key) || filters.get(key) == null) {
			return null;
		}
		return trimToNull(String.valueOf(filters.get(key)));
	}

	private Long filterLong(Map<String, Object> filters, String key) {
		if (filters == null || !filters.containsKey(key) || filters.get(key) == null) {
			return null;
		}
		Object value = filters.get(key);
		if (value instanceof Number number) {
			return number.longValue();
		}
		String text = trimToNull(String.valueOf(value));
		return text == null ? null : Long.valueOf(text);
	}

	private Boolean filterBoolean(Map<String, Object> filters, String key) {
		if (filters == null || !filters.containsKey(key) || filters.get(key) == null) {
			return null;
		}
		Object value = filters.get(key);
		if (value instanceof Boolean bool) {
			return bool;
		}
		String text = trimToNull(String.valueOf(value));
		return text == null ? null : Boolean.valueOf(text);
	}

	private LocalDate filterDate(Map<String, Object> filters, String key) {
		String value = filterString(filters, key);
		return value == null ? null : LocalDate.parse(value);
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

	private void validateSupplierQuoteImport(ClaimedTask task, CurrentUser operator) {
		SupplierQuoteImportPayload payload = parseSupplierQuoteImportPayload(task.requestPayload());
		requireSupplierQuoteImportAccess(payload.inquiryId(), operator);
		byte[] content = sourceFileContent(payload.sourceFileId());
		Long batchId = batchId(task.id());
		clearImportRowsAndErrors(task.id(), batchId);
		List<ImportError> errors = new ArrayList<>();
		int rowCount = 0;
		try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
			validateWorkbookSheets(workbook, List.of("quotes"));
			Sheet sheet = workbook.getSheet("quotes");
			validateHeader(sheet, new String[] { "supplierId", "validFrom", "validTo", "inquiryLineId",
					"quantity", "taxRate", "taxExcludedUnitPrice", "taxIncludedUnitPrice", "deliveryDate",
					"remark" });
			validateVisibleColumns(sheet, 10);
			validateVisibleRows(sheet, 1);
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null || rowIsBlank(row)) {
					continue;
				}
				rowCount++;
				SupplierQuoteImportRow importRow = new SupplierQuoteImportRow(longCell(row, 0), dateCell(row, 1),
						dateCell(row, 2), longCell(row, 3), decimalCell(row, 4),
						decimalCell(row, 5) == null ? BigDecimal.ZERO : decimalCell(row, 5), decimalCell(row, 6),
						decimalCell(row, 7), dateCell(row, 8), cellString(row, 9));
				validateSupplierQuoteImportRow(payload.inquiryId(), i + 1, importRow, errors);
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
		if (rowCount == 0) {
			errors.add(new ImportError(null, "file", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "供应商报价导入不能为空"));
		}
		if (!errors.isEmpty()) {
			recordImportErrors(task.id(), batchId, errors);
			markValidationFailed(task.id(), batchId, rowCount, errors.size());
			return;
		}
		markReadyToCommit(task.id(), batchId, rowCount);
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

	private void commitSupplierQuoteImport(ClaimedTask task, CurrentUser operator) {
		SupplierQuoteImportPayload payload = parseSupplierQuoteImportPayload(task.requestPayload());
		requireSupplierQuoteImportAccess(payload.inquiryId(), operator);
		Long batchId = batchId(task.id());
		List<SupplierQuoteImportRow> rows = this.jdbcTemplate.query("""
				select payload::text
				from platform_import_row
				where batch_id = ?
				order by row_no
				""", (rs, rowNum) -> parse(rs.getString("payload"), SupplierQuoteImportRow.class), batchId);
		try {
			for (SupplierQuoteImportRow row : rows) {
				Long quoteId = this.jdbcTemplate.queryForObject("""
						insert into proc_supplier_quote (
							quote_no, inquiry_id, supplier_id, status, valid_from, valid_to, currency, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, 'VALID', ?, ?, 'CNY', ?, ?, now(), ?, now())
						returning id
						""", Long.class, nextTaskNo("PQT"), payload.inquiryId(), row.supplierId(),
						row.validFrom(), row.validTo(), blankToNull(row.remark()), operator.username(),
						operator.username());
				InquiryLineImportSnapshot line = inquiryLineImportSnapshot(row.inquiryLineId());
				this.jdbcTemplate.update("""
						insert into proc_supplier_quote_line (
							quote_id, inquiry_line_id, line_no, material_id, unit_id, min_purchase_quantity,
							quantity, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
							tax_excluded_amount, tax_included_amount, delivery_date, created_at, updated_at
						)
						values (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, round(? * ?, 2), round(? * ?, 2), ?, now(), now())
						""", quoteId, row.inquiryLineId(), line.materialId(), line.unitId(), row.quantity(),
						row.quantity(), row.taxRate(), row.taxExcludedUnitPrice(), row.taxIncludedUnitPrice(),
						row.quantity(), row.taxExcludedUnitPrice(), row.quantity(), row.taxIncludedUnitPrice(),
						row.deliveryDate());
			}
			markImportSucceeded(task.id(), batchId, rows.size());
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

	private void validateSupplierQuoteImportRow(Long inquiryId, int rowNo, SupplierQuoteImportRow row,
			List<ImportError> errors) {
		if (row.supplierId() == null || supplierCount(row.supplierId()) == 0) {
			errors.add(new ImportError(rowNo, "supplierId", ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(),
					"供应商不存在或未启用"));
		}
		if (row.validFrom() == null || row.validTo() == null || row.validFrom().isAfter(row.validTo())) {
			errors.add(new ImportError(rowNo, "validFrom", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(),
					"有效期不合法"));
		}
		InquiryLineImportSnapshot line = row.inquiryLineId() == null ? null
				: inquiryLineImportSnapshotOrNull(row.inquiryLineId());
		if (line == null || !line.inquiryId().equals(inquiryId)) {
			errors.add(new ImportError(rowNo, "inquiryLineId", ApiErrorCode.PROCUREMENT_QUOTE_INVALID.name(),
					"报价行必须属于当前询价"));
		}
		if (row.quantity() == null || row.quantity().compareTo(BigDecimal.ZERO) <= 0
				|| row.taxExcludedUnitPrice() == null || row.taxExcludedUnitPrice().compareTo(BigDecimal.ZERO) < 0
				|| row.taxIncludedUnitPrice() == null || row.taxIncludedUnitPrice().compareTo(BigDecimal.ZERO) < 0
				|| row.taxRate() == null || row.taxRate().compareTo(BigDecimal.ZERO) < 0) {
			errors.add(new ImportError(rowNo, "quantity", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(),
					"数量、税率和价格不合法"));
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

	public SupplierQuoteImportPayload parseSupplierQuoteImportPayload(String payload) {
		return parse(payload, SupplierQuoteImportPayload.class);
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

	private long supplierCount(Long supplierId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from mst_supplier
				where id = ?
				and status = 'ENABLED'
				""", Long.class, supplierId);
	}

	private ProcurementInquiryTaskSnapshot procurementInquiryTaskSnapshot(Long inquiryId) {
		return this.jdbcTemplate.query("""
				select id, inquiry_no, status, version
				from proc_purchase_inquiry
				where id = ?
				""", (rs, rowNum) -> new ProcurementInquiryTaskSnapshot(rs.getLong("id"),
				rs.getString("inquiry_no"), rs.getString("status"), rs.getLong("version")), inquiryId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND));
	}

	private InquiryLineImportSnapshot inquiryLineImportSnapshot(Long inquiryLineId) {
		InquiryLineImportSnapshot line = inquiryLineImportSnapshotOrNull(inquiryLineId);
		if (line == null) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_INVALID);
		}
		return line;
	}

	private InquiryLineImportSnapshot inquiryLineImportSnapshotOrNull(Long inquiryLineId) {
		return this.jdbcTemplate.query("""
				select id, inquiry_id, material_id, unit_id
				from proc_purchase_inquiry_line
				where id = ?
				""", (rs, rowNum) -> new InquiryLineImportSnapshot(rs.getLong("id"), rs.getLong("inquiry_id"),
				rs.getLong("material_id"), rs.getLong("unit_id")), inquiryLineId).stream().findFirst().orElse(null);
	}

	private ProcurementOrderPrintSnapshot procurementOrderPrintSnapshot(Long orderId) {
		return this.jdbcTemplate.query("""
				select o.id, o.order_no, o.purchase_mode, o.project_id, o.status, o.order_date,
				       s.name as supplier_name, o.version
				from proc_purchase_order o
				join mst_supplier s on s.id = o.supplier_id
				where o.id = ?
				""", (rs, rowNum) -> new ProcurementOrderPrintSnapshot(rs.getLong("id"), rs.getString("order_no"),
				rs.getString("purchase_mode"), nullableLong(rs, "project_id"), rs.getString("status"),
				rs.getObject("order_date", LocalDate.class), rs.getString("supplier_name"), rs.getLong("version")),
				orderId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_NOT_FOUND));
	}

	private SalesQuotePrintSnapshot salesQuotePrintSnapshot(Long quoteId) {
		return this.jdbcTemplate.query("""
				select q.id, q.quote_no, q.status, q.quote_date, q.valid_until, q.version,
				       c.name as customer_name, p.name as project_name
				from sal_sales_quote q
				join mst_customer c on c.id = q.customer_id
				left join sal_project p on p.id = q.project_id
				where q.id = ?
				""", (rs, rowNum) -> new SalesQuotePrintSnapshot(rs.getLong("id"), rs.getString("quote_no"),
				rs.getString("status"), rs.getObject("quote_date", LocalDate.class),
				rs.getObject("valid_until", LocalDate.class), rs.getString("customer_name"),
				rs.getString("project_name"), rs.getLong("version")), quoteId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_QUOTE_NOT_FOUND));
	}

	private List<ProcurementOrderPrintLine> procurementOrderPrintLines(Long orderId) {
		return this.jdbcTemplate.query("""
				select ol.line_no, m.code as material_code, m.name as material_name, ol.quantity,
				       ol.tax_included_unit_price
				from proc_purchase_order_line ol
				join mst_material m on m.id = ol.material_id
				where ol.order_id = ?
				order by ol.line_no
				""", (rs, rowNum) -> new ProcurementOrderPrintLine(rs.getInt("line_no"),
				rs.getString("material_code"), rs.getString("material_name"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("tax_included_unit_price")), orderId);
	}

	private List<SalesQuotePrintLine> salesQuotePrintLines(Long quoteId) {
		return this.jdbcTemplate.query("""
				select ql.line_no, m.code as material_code, m.name as material_name, ql.quantity,
				       ql.tax_included_unit_price
				from sal_sales_quote_line ql
				join mst_material m on m.id = ql.material_id
				where ql.quote_id = ?
				order by ql.line_no
				""", (rs, rowNum) -> new SalesQuotePrintLine(rs.getInt("line_no"),
				rs.getString("material_code"), rs.getString("material_name"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("tax_included_unit_price")), quoteId);
	}

	private int printTemplateVersion(String templateCode, String sceneCode, String objectType) {
		return this.jdbcTemplate.query("""
				select template_version
				from platform_print_template
				where template_code = ?
				and scene_code = ?
				and object_type = ?
				and status = 'ENABLED'
				""", (rs, rowNum) -> rs.getInt("template_version"), templateCode, sceneCode, objectType)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED));
	}

	private FixedPrintDefinition fixedPrintDefinition(String objectType) {
		if (!hasText(objectType)) {
			throw new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED);
		}
		FixedPrintDefinition definition = FIXED_PRINT_DEFINITIONS.get(objectType.trim().toUpperCase());
		if (definition == null) {
			throw new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED);
		}
		return definition;
	}

	private void requireFixedPrintAccess(FixedPrintDefinition definition, Long objectId, CurrentUser operator) {
		requirePermission(operator, "platform:print:generate");
		requirePermission(operator, definition.viewPermission());
		fixedPrintSnapshot(definition, objectId);
	}

	private void requireFixedPrintPayloadAccess(FixedPrintDefinition definition, FixedPrintPayload payload,
			CurrentUser operator) {
		if (!"ACCOUNTING_VOUCHER".equals(definition.objectType())) {
			return;
		}
		if (!Boolean.FALSE.equals(payload.amountVisible())) {
			requirePermission(operator, "gl:amount:view");
		}
		if (!Boolean.FALSE.equals(payload.sourceVisible())) {
			requirePermission(operator, "gl:source:view");
		}
	}

	private FixedPrintVisibility fixedPrintVisibility(FixedPrintDefinition definition, CurrentUser operator) {
		if (!"ACCOUNTING_VOUCHER".equals(definition.objectType())) {
			return new FixedPrintVisibility(true, true);
		}
		return new FixedPrintVisibility(operator.permissions().contains("gl:amount:view"),
				operator.permissions().contains("gl:source:view"));
	}

	private FixedPrintVisibility effectiveFixedPrintVisibility(FixedPrintDefinition definition, FixedPrintPayload payload,
			CurrentUser operator) {
		FixedPrintVisibility current = fixedPrintVisibility(definition, operator);
		if (!"ACCOUNTING_VOUCHER".equals(definition.objectType())) {
			return current;
		}
		boolean amountVisible = current.amountVisible() && !Boolean.FALSE.equals(payload.amountVisible());
		boolean sourceVisible = current.sourceVisible() && !Boolean.FALSE.equals(payload.sourceVisible());
		return new FixedPrintVisibility(amountVisible, sourceVisible);
	}

	private FixedPrintSnapshot fixedPrintSnapshot(FixedPrintDefinition definition, Long objectId) {
		if (objectId == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		FixedPrintSnapshot snapshot = this.jdbcTemplate.query("""
				select id, %s as object_no, status, version
				from %s
				where id = ?
				""".formatted(definition.objectNoExpression(), definition.tableName()),
				(rs, rowNum) -> new FixedPrintSnapshot(rs.getLong("id"), rs.getString("object_no"),
						rs.getString("status"), rs.getLong("version")),
				objectId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID));
		if (!fixedPrintAllowedStatuses(definition.objectType()).contains(snapshot.status())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		return snapshot;
	}

	private Set<String> fixedPrintAllowedStatuses(String objectType) {
		return switch (objectType) {
			case "SALES_ORDER" -> Set.of("CONFIRMED", "PARTIALLY_SHIPPED", "SHIPPED", "CLOSED");
			case "SALES_SHIPMENT" -> Set.of("POSTED", "CANCELLED");
			case "PROCUREMENT_RECEIPT" -> Set.of("POSTED", "REVERSED");
			case "INVENTORY_TRANSFER" -> Set.of("POSTED", "CANCELLED", "REVERSED");
			case "PRODUCTION_MATERIAL_ISSUE", "PRODUCTION_COMPLETION_RECEIPT" -> Set.of("POSTED");
			case "PRODUCTION_WORK_ORDER" -> Set.of("RELEASED", "IN_PROGRESS", "COMPLETED", "CLOSED");
			case "SALES_INVOICE", "PURCHASE_INVOICE" -> Set.of("CONFIRMED", "POSTED", "CLOSED");
			case "ACCOUNTING_VOUCHER" -> Set.of("DRAFT", "POSTED");
			default -> Set.of();
		};
	}

	private String maskedAmountCell(BigDecimal amount, FixedPrintVisibility visibility) {
		return visibility.amountVisible() ? exportCell(amount) : "";
	}

	private String visibleVoucherSummary(String summary, String sourceType, String sourceOriginalType,
			boolean sourceVisible) {
		boolean sourceBacked = "FIN_VOUCHER_DRAFT".equals(sourceType) || sourceOriginalType != null;
		return !sourceVisible && sourceBacked ? "来源凭证" : summary;
	}

	private void requirePermission(CurrentUser operator, String permissionCode) {
		if (!operator.permissions().contains(permissionCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void requireProcurementOrderPrintAccess(Long orderId, CurrentUser operator) {
		requirePermission(operator, "platform:print:generate");
		requirePermission(operator, "procurement:order:view");
		requirePermission(operator, "procurement:order:print");
		procurementOrderPrintSnapshot(orderId);
	}

	private void requireSalesQuotePrintAccess(Long quoteId, CurrentUser operator) {
		requirePermission(operator, "platform:print:generate");
		requirePermission(operator, "sales:quote:view");
		requirePermission(operator, "sales:document:print");
		salesQuotePrintSnapshot(quoteId);
	}

	private void requireSupplierQuoteImportAccess(Long inquiryId, CurrentUser operator) {
		requirePermission(operator, "procurement:inquiry:view");
		requirePermission(operator, "procurement:quote:import");
		procurementInquiryTaskSnapshot(inquiryId);
	}

	private void requireProcurementExportPermissions(String taskType, CurrentUser operator) {
		switch (taskType) {
			case "PROCUREMENT_REQUISITION_EXPORT" -> {
				requirePermission(operator, "procurement:requisition:view");
				requirePermission(operator, "procurement:document:export");
			}
			case "PROCUREMENT_INQUIRY_EXPORT" -> {
				requirePermission(operator, "procurement:inquiry:view");
				requirePermission(operator, "procurement:document:export");
			}
			case "PROCUREMENT_QUOTE_EXPORT" -> {
				requirePermission(operator, "procurement:quote:view");
				requirePermission(operator, "procurement:quote:export");
			}
			case "PROCUREMENT_PRICE_AGREEMENT_EXPORT" -> {
				requirePermission(operator, "procurement:price-agreement:view");
				requirePermission(operator, "procurement:document:export");
			}
			case "PROCUREMENT_ORDER_EXPORT", "PROCUREMENT_SCHEDULE_EXPORT" -> {
				requirePermission(operator, "procurement:order:view");
				requirePermission(operator, "procurement:document:export");
			}
			case "PROCUREMENT_SUPPLY_EXPORT" -> {
				requirePermission(operator, "procurement:supply:view");
				requirePermission(operator, "procurement:supply:export");
			}
			default -> throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void requireSalesExportPermissions(String taskType, CurrentUser operator) {
		requirePermission(operator, "sales:document:export");
		switch (taskType) {
			case "SALES_QUOTE_EXPORT" -> requirePermission(operator, "sales:quote:view");
			case "SALES_DELIVERY_PLAN_EXPORT" -> requirePermission(operator, "sales:delivery-plan:view");
			case "SALES_EFFECTIVE_DEMAND_EXPORT" -> requirePermission(operator, "sales:effective-demand:view");
			default -> throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void requirePlanningExportPermissions(String taskType, CurrentUser operator) {
		if (!PLANNING_EXPORT_TASK_TYPES.contains(taskType)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
		requirePermission(operator, "planning:material-requirement:view");
		requirePermission(operator, "planning:material-requirement:export");
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
				documentTaskAvailableActions(rs.getString("task_type"), rs.getString("status"),
						nullableLong(rs, "result_file_id"),
						rs.getInt("error_count"), rs.getObject("expires_at", OffsetDateTime.class)));
	}

	private DocumentTaskRecord toDocumentTaskRecord(DocumentTaskState task, CurrentUser currentUser) {
		TaskObjectInfo objectInfo = taskObjectInfo(task);
		List<String> availableActions = isHistoryImportTask(task.taskType())
				? historyImportDocumentTaskActions(task, currentUser) : task.availableActions();
		return new DocumentTaskRecord(task.id(), task.taskNo(), task.taskType(), objectInfo.objectType(),
				objectInfo.objectId(), objectInfo.objectNo(), objectInfo.objectName(), taskDirection(task.taskType()),
				task.stage(), task.status(), progressPercent(task.status()), task.totalCount(), task.successCount(),
				task.errorCount(), task.errorSummary(), task.createdByUsername(), task.createdAt(), task.finishedAt(),
				task.expiresAt(), task.version(), availableActions);
	}

	private List<String> historyImportDocumentTaskActions(DocumentTaskState task, CurrentUser currentUser) {
		List<String> actions = new ArrayList<>();
		if ("READY_TO_COMMIT".equals(task.status())) {
			if (currentUser.permissions().contains("platform:history-import:confirm")) {
				actions.add("CONFIRM");
			}
			if (currentUser.permissions().contains("platform:history-import:cancel")) {
				actions.add("CANCEL");
			}
		}
		if ("VALIDATION_FAILED".equals(task.status())) {
			if (task.errorCount() > 0) {
				actions.add("ERRORS");
			}
			if (currentUser.permissions().contains("platform:history-import:cancel")) {
				actions.add("CANCEL");
			}
		}
		if ("SUCCEEDED".equals(task.status()) && task.resultFileId() != null
				&& (task.expiresAt() == null || task.expiresAt().isAfter(OffsetDateTime.now()))) {
			actions.add("DOWNLOAD");
		}
		return actions;
	}

	private boolean matchesObjectKeyword(DocumentTaskState task, String objectKeyword) {
		if (!hasText(objectKeyword)) {
			return true;
		}
		String keyword = objectKeyword.trim().toLowerCase();
		TaskObjectInfo objectInfo = taskObjectInfo(task);
		return containsIgnoreCase(objectInfo.objectNo(), keyword) || containsIgnoreCase(objectInfo.objectName(), keyword)
				|| containsIgnoreCase(objectInfo.objectType(), keyword) || containsIgnoreCase(task.taskNo(), keyword);
	}

	private boolean containsIgnoreCase(String value, String keyword) {
		return value != null && value.toLowerCase().contains(keyword);
	}

	private OffsetDateTime parseFilterTime(String value, boolean endOfDay) {
		if (!hasText(value)) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value.trim());
		}
		catch (RuntimeException ignored) {
			LocalDate date = LocalDate.parse(value.trim());
			return endOfDay ? date.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset())
					: date.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
		}
	}

	private TaskObjectInfo taskObjectInfo(DocumentTaskState task) {
		try {
			if ("APPROVAL_PRINT".equals(task.taskType())) {
				PrintTaskPayload payload = parsePrintTaskPayload(taskRequestPayload(task.id()));
				ApprovalPrintSnapshot snapshot = approvalPrintSnapshot(payload.approvalInstanceId());
				return new TaskObjectInfo(snapshot.businessObjectType(), snapshot.businessObjectId(),
						snapshot.businessObjectNo(), snapshot.businessObjectSummary());
			}
			if ("PROCUREMENT_ORDER_PRINT".equals(task.taskType())) {
				ProcurementOrderPrintPayload payload = parseProcurementOrderPrintPayload(taskRequestPayload(task.id()));
				ProcurementOrderPrintSnapshot snapshot = procurementOrderPrintSnapshot(payload.orderId());
				return new TaskObjectInfo("PROCUREMENT_ORDER", payload.orderId(), snapshot.orderNo(),
						snapshot.supplierName());
			}
			if ("SALES_QUOTE_PRINT".equals(task.taskType())) {
				SalesQuotePrintPayload payload = parseSalesQuotePrintPayload(taskRequestPayload(task.id()));
				SalesQuotePrintSnapshot snapshot = salesQuotePrintSnapshot(payload.quoteId());
				return new TaskObjectInfo("SALES_QUOTE", payload.quoteId(), snapshot.quoteNo(),
						snapshot.customerName());
			}
			if ("FIXED_DOCUMENT_PRINT".equals(task.taskType())) {
				FixedPrintPayload payload = parseFixedPrintPayload(taskRequestPayload(task.id()));
				FixedPrintDefinition definition = fixedPrintDefinition(payload.objectType());
				FixedPrintSnapshot snapshot = fixedPrintSnapshot(definition, payload.objectId());
				return new TaskObjectInfo(payload.objectType(), payload.objectId(), snapshot.objectNo(),
						snapshot.status());
			}
			if (PROCUREMENT_EXPORT_TASK_TYPES.contains(task.taskType())) {
				ProcurementExportRequest payload = parseProcurementExportRequest(taskRequestPayload(task.id()));
				return new TaskObjectInfo(payload.objectType(), payload.objectId(), null, null);
			}
			if (SALES_EXPORT_TASK_TYPES.contains(task.taskType())) {
				ProcurementExportRequest payload = parseProcurementExportRequest(taskRequestPayload(task.id()));
				return new TaskObjectInfo(payload.objectType(), payload.objectId(), null, null);
			}
			if (PLANNING_EXPORT_TASK_TYPES.contains(task.taskType())) {
				ProcurementExportRequest payload = parseProcurementExportRequest(taskRequestPayload(task.id()));
				return new TaskObjectInfo(payload.objectType(), payload.objectId(), null, null);
			}
			if ("PROCUREMENT_QUOTE_IMPORT".equals(task.taskType())) {
				SupplierQuoteImportPayload payload = parseSupplierQuoteImportPayload(taskRequestPayload(task.id()));
				ProcurementInquiryTaskSnapshot snapshot = procurementInquiryTaskSnapshot(payload.inquiryId());
				return new TaskObjectInfo("PROCUREMENT_INQUIRY", payload.inquiryId(), snapshot.inquiryNo(),
						snapshot.status());
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
		if (taskType.endsWith("_PRINT")) {
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

	private List<String> documentTaskAvailableActions(String taskType, String status, Long resultFileId,
			int errorCount, OffsetDateTime expiresAt) {
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

	private static boolean isHistoryImportTask(String taskType) {
		return taskType != null && taskType.endsWith("_HISTORY_IMPORT");
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
		if ("PROCUREMENT_ORDER_PRINT".equals(task.taskType())) {
			return canViewProcurementOrderPrintTask(task, currentUser);
		}
		if ("SALES_QUOTE_PRINT".equals(task.taskType())) {
			return canViewSalesQuotePrintTask(task, currentUser);
		}
		if ("FIXED_DOCUMENT_PRINT".equals(task.taskType())) {
			return canViewFixedPrintTask(task, currentUser);
		}
		if (PROCUREMENT_EXPORT_TASK_TYPES.contains(task.taskType())) {
			return canAccessProcurementExportTask(task, currentUser);
		}
		if (SALES_EXPORT_TASK_TYPES.contains(task.taskType())) {
			return canAccessSalesExportTask(task, currentUser);
		}
		if (PLANNING_EXPORT_TASK_TYPES.contains(task.taskType())) {
			return canAccessPlanningExportTask(task, currentUser);
		}
		if (task.taskType() != null && task.taskType().endsWith("_HISTORY_IMPORT")) {
			return canAccessHistoryImportTask(task, currentUser);
		}
		if ("PROCUREMENT_QUOTE_IMPORT".equals(task.taskType())) {
			return canViewSupplierQuoteImportTask(task, currentUser);
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

	private boolean canViewProcurementOrderPrintTask(DocumentTaskState task, CurrentUser currentUser) {
		try {
			ProcurementOrderPrintPayload payload = parseProcurementOrderPrintPayload(taskRequestPayload(task.id()));
			requireProcurementOrderPrintAccess(payload.orderId(), currentUser);
			return true;
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private boolean canViewSalesQuotePrintTask(DocumentTaskState task, CurrentUser currentUser) {
		try {
			SalesQuotePrintPayload payload = parseSalesQuotePrintPayload(taskRequestPayload(task.id()));
			requireSalesQuotePrintAccess(payload.quoteId(), currentUser);
			return true;
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private boolean canViewFixedPrintTask(DocumentTaskState task, CurrentUser currentUser) {
		try {
			FixedPrintPayload payload = parseFixedPrintPayload(taskRequestPayload(task.id()));
			FixedPrintDefinition definition = fixedPrintDefinition(payload.objectType());
			requireFixedPrintAccess(definition, payload.objectId(), currentUser);
			requireFixedPrintPayloadAccess(definition, payload, currentUser);
			return true;
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private boolean canAccessProcurementExportTask(DocumentTaskState task, CurrentUser currentUser) {
		try {
			ProcurementExportRequest payload = parseProcurementExportRequest(taskRequestPayload(task.id()));
			requireProcurementExportPermissions(payload.taskType(), currentUser);
			return true;
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private boolean canAccessSalesExportTask(DocumentTaskState task, CurrentUser currentUser) {
		try {
			ProcurementExportRequest payload = parseProcurementExportRequest(taskRequestPayload(task.id()));
			requireSalesExportPermissions(payload.taskType(), currentUser);
			return true;
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private boolean canAccessPlanningExportTask(DocumentTaskState task, CurrentUser currentUser) {
		try {
			ProcurementExportRequest payload = parseProcurementExportRequest(taskRequestPayload(task.id()));
			requirePlanningExportPermissions(payload.taskType(), currentUser);
			return true;
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private boolean canAccessHistoryImportTask(DocumentTaskState task, CurrentUser currentUser) {
		String adapterCode = task.taskType().substring(0, task.taskType().length() - "_HISTORY_IMPORT".length());
		String requiredPermission = this.jdbcTemplate.query("""
				select required_permission_code
				from platform_import_adapter_definition
				where adapter_code = ?
				  and status = 'ENABLED'
				""", (rs, rowNum) -> rs.getString("required_permission_code"), adapterCode)
			.stream()
			.findFirst()
			.orElse(null);
		return requiredPermission != null && currentUser.permissions().contains(requiredPermission);
	}

	private boolean canViewSupplierQuoteImportTask(DocumentTaskState task, CurrentUser currentUser) {
		try {
			SupplierQuoteImportPayload payload = parseSupplierQuoteImportPayload(taskRequestPayload(task.id()));
			requireSupplierQuoteImportAccess(payload.inquiryId(), currentUser);
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
		if (taskType != null && taskType.endsWith("_HISTORY_IMPORT")) {
			return "platform:history-import:view";
		}
		return switch (taskType) {
			case "MATERIAL_IMPORT" -> "master:material:import";
			case "MATERIAL_EXPORT" -> "master:material:export";
			case "BOM_DRAFT_IMPORT" -> "material:bom:import";
			case "BOM_DRAFT_EXPORT" -> "material:bom:export";
			case "APPROVAL_PRINT" -> "platform:print:generate";
			case "PROCUREMENT_ORDER_PRINT" -> "procurement:order:print";
			case "SALES_QUOTE_PRINT" -> "sales:document:print";
			case "FIXED_DOCUMENT_PRINT" -> "platform:print:generate";
			case "PROCUREMENT_QUOTE_IMPORT" -> "procurement:quote:import";
			case "PROCUREMENT_QUOTE_EXPORT" -> "procurement:quote:export";
			case "PROCUREMENT_SUPPLY_EXPORT" -> "procurement:supply:export";
			case "PROCUREMENT_REQUISITION_EXPORT", "PROCUREMENT_INQUIRY_EXPORT",
					"PROCUREMENT_PRICE_AGREEMENT_EXPORT", "PROCUREMENT_ORDER_EXPORT",
					"PROCUREMENT_SCHEDULE_EXPORT" -> "procurement:document:export";
			case "SALES_QUOTE_EXPORT", "SALES_DELIVERY_PLAN_EXPORT", "SALES_EFFECTIVE_DEMAND_EXPORT" ->
				"sales:document:export";
			case "MATERIAL_REQUIREMENT_RUN_EXPORT" -> "planning:material-requirement:export";
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

	private boolean supplierQuoteImportPayloadEquivalent(String left, String right) {
		try {
			SupplierQuoteImportPayload leftPayload = parseSupplierQuoteImportPayload(left);
			SupplierQuoteImportPayload rightPayload = parseSupplierQuoteImportPayload(right);
			return leftPayload.inquiryId().equals(rightPayload.inquiryId())
					&& nullToBlank(leftPayload.filename()).equals(nullToBlank(rightPayload.filename()))
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
			case "PROCUREMENT_QUOTE_IMPORT" -> "PQIM";
			case "PROCUREMENT_ORDER_PRINT" -> "POPR";
			case "PROCUREMENT_REQUISITION_EXPORT" -> "PREX";
			case "PROCUREMENT_INQUIRY_EXPORT" -> "PIEX";
			case "PROCUREMENT_QUOTE_EXPORT" -> "PQEX";
			case "PROCUREMENT_PRICE_AGREEMENT_EXPORT" -> "PAEX";
			case "PROCUREMENT_ORDER_EXPORT" -> "POEX";
			case "PROCUREMENT_SCHEDULE_EXPORT" -> "PSEX";
			case "PROCUREMENT_SUPPLY_EXPORT" -> "PUEX";
			case "SALES_QUOTE_PRINT" -> "SQPR";
			case "FIXED_DOCUMENT_PRINT" -> "FDPR";
			case "SALES_QUOTE_EXPORT" -> "SQEX";
			case "SALES_DELIVERY_PLAN_EXPORT" -> "SDPX";
			case "SALES_EFFECTIVE_DEMAND_EXPORT" -> "SDEX";
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

	private static String blankToNull(String value) {
		return trimToNull(value);
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

	public record PrintTaskRequest(Long approvalInstanceId, String objectType, Long objectId, String templateCode) {
	}

	public record ProcurementExportRequest(String taskType, String objectType, Long objectId,
			Map<String, Object> filters) {
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

	public record ProcurementOrderPrintPayload(Long orderId, String templateCode, Long orderVersion,
			int templateVersion) {
	}

	public record SalesQuotePrintPayload(Long quoteId, String templateCode, Long quoteVersion, int templateVersion) {
	}

	public record FixedPrintPayload(String objectType, Long objectId, String templateCode, Long objectVersion,
			int templateVersion, Boolean amountVisible, Boolean sourceVisible) {

		public FixedPrintPayload(String objectType, Long objectId, String templateCode, Long objectVersion,
				int templateVersion) {
			this(objectType, objectId, templateCode, objectVersion, templateVersion, null, null);
		}

	}

	private record ImportTaskPayload(Long sourceFileId, String filename, String sha256) {
	}

	public record SupplierQuoteImportPayload(Long sourceFileId, String filename, String sha256, Long inquiryId) {
	}

	private record ProcurementExportDataset(String sheetName, List<String> headers, List<List<String>> rows) {
	}

	private record ProcurementInquiryTaskSnapshot(Long id, String inquiryNo, String status, Long version) {
	}

	private record ProcurementOrderPrintSnapshot(Long id, String orderNo, String purchaseMode, Long projectId,
			String status, LocalDate orderDate, String supplierName, Long version) {
	}

	private record ProcurementOrderPrintLine(Integer lineNo, String materialCode, String materialName,
			BigDecimal quantity, BigDecimal taxIncludedUnitPrice) {
	}

	private record SalesQuotePrintSnapshot(Long id, String quoteNo, String status, LocalDate quoteDate,
			LocalDate validUntil, String customerName, String projectName, Long version) {
	}

	private record SalesQuotePrintLine(Integer lineNo, String materialCode, String materialName, BigDecimal quantity,
			BigDecimal taxIncludedUnitPrice) {
	}

	private record FixedPrintDefinition(String objectType, String templateCode, String sceneCode, String templateName,
			String tableName, String objectNoExpression, String viewPermission) {
	}

	private record FixedPrintSnapshot(Long id, String objectNo, String status, Long version) {
	}

	private record FixedPrintVisibility(boolean amountVisible, boolean sourceVisible) {
	}

	private record FixedPrintLineSpec(String tableName, String parentColumn) {
	}

	private record MaterialImportRow(String code, String name, String specification, String materialType,
			String sourceType, String trackingMethod, String categoryCode, String unitCode, String status,
			String costCategory, String inventoryValuationCategory, String inventoryValueEnabled,
			String projectCostEnabled, String costRemark, String remark) {
	}

	private record SupplierQuoteImportRow(Long supplierId, LocalDate validFrom, LocalDate validTo,
			Long inquiryLineId, BigDecimal quantity, BigDecimal taxRate, BigDecimal taxExcludedUnitPrice,
			BigDecimal taxIncludedUnitPrice, LocalDate deliveryDate, String remark) {
	}

	private record BomDraftImportPayload(String mode, Long bomId, Long version, String bomCode,
			String parentMaterialCode, String versionCode, String name, BigDecimal baseQuantity,
			String baseUnitCode, LocalDate effectiveFrom, LocalDate effectiveTo, String remark,
			List<BomDraftImportItem> items) {
	}

	private record BomDraftImportItem(Integer lineNo, String childMaterialCode, String businessUnitCode,
			BigDecimal businessQuantity, BigDecimal lossRate, String warehouse, String remark) {
	}

	private record InquiryLineImportSnapshot(Long id, Long inquiryId, Long materialId, Long unitId) {
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
