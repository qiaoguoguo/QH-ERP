package com.qherp.api.system.platform;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.inventory.InventoryTrackingMethod;
import com.qherp.api.system.master.MasterDataStatus;
import com.qherp.api.system.master.MaterialSourceType;
import com.qherp.api.system.master.MaterialType;
import com.qherp.api.system.master.UnitConversionAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PlatformHistoryImportService {

	private static final AtomicInteger TASK_SEQUENCE = new AtomicInteger();

	private static final DateTimeFormatter TASK_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final Set<String> MATERIAL_TYPE_VALUES = enumNames(MaterialType.values());

	private static final Set<String> MATERIAL_SOURCE_TYPE_VALUES = enumNames(MaterialSourceType.values());

	private static final Set<String> MATERIAL_TRACKING_METHOD_VALUES = enumNames(InventoryTrackingMethod.values());

	private static final Set<String> MATERIAL_STATUS_VALUES = enumNames(MasterDataStatus.values());

	private static final Set<String> MATERIAL_COST_CATEGORY_VALUES = Set.of("DIRECT_MATERIAL", "AUXILIARY_MATERIAL",
			"SEMI_FINISHED", "FINISHED_GOOD", "OUTSOURCING", "SERVICE", "UNCLASSIFIED");

	private static final Set<String> MATERIAL_INVENTORY_VALUATION_CATEGORY_VALUES = Set.of("VALUATED_MATERIAL",
			"NON_VALUATED_CONSUMABLE", "SERVICE_NON_STOCK", "UNCLASSIFIED");

	private static final Set<String> BOM_QUANTITY_BASIS_VALUES = Set.of("BASE_UNIT", "CONVERTED_BUSINESS_UNIT",
			"LEGACY_BUSINESS_UNIT");

	static {
		ZipSecureFile.setMinInflateRatio(0.01d);
		ZipSecureFile.setMaxEntrySize(20L * 1024L * 1024L);
		ZipSecureFile.setMaxTextSize(10L * 1024L * 1024L);
	}

	private final JdbcTemplate jdbcTemplate;

	private final PlatformStorageService storageService;

	private final AuditService auditService;

	private final UnitConversionAdminService unitConversionAdminService;

	private final ObjectMapper objectMapper;

	public PlatformHistoryImportService(JdbcTemplate jdbcTemplate, PlatformStorageService storageService,
			AuditService auditService, UnitConversionAdminService unitConversionAdminService,
			ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.storageService = storageService;
		this.auditService = auditService;
		this.unitConversionAdminService = unitConversionAdminService;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public List<HistoryImportAdapterRecord> adapters(CurrentUser currentUser) {
		requirePermission(currentUser, "platform:history-import:view");
		return this.jdbcTemplate.query("""
				select adapter_code, name, target_object_type, template_code, template_version, max_rows,
				       required_permission_code, description, version
				from platform_import_adapter_definition
				where status = 'ENABLED'
				order by id
				""", this::mapAdapter)
			.stream()
			.filter((adapter) -> currentUser.permissions().contains(adapter.requiredPermissionCode()))
			.toList();
	}

	@Transactional(readOnly = true)
	public TemplateFile template(String adapterCode, CurrentUser currentUser) {
		requirePermission(currentUser, "platform:history-import:view");
		AdapterDefinition adapter = adapter(adapterCode);
		requirePermission(currentUser, adapter.requiredPermissionCode());
		return new TemplateFile(adapter.templateCode().toLowerCase().replace("_", "-") + "-template.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				templateWorkbook(adapter.adapterCode()));
	}

	@Transactional
	public PageResponse<HistoryImportRecord> list(String status, String adapterCode, String keyword, int page,
			int pageSize, CurrentUser currentUser) {
		requirePermission(currentUser, "platform:history-import:view");
		expireReadyImports();
		List<Object> args = new ArrayList<>();
		String where = "where b.import_type in (select adapter_code from platform_import_adapter_definition)";
		if (hasText(status)) {
			where += " and t.status = ?";
			args.add(status.trim().toUpperCase());
		}
		if (hasText(adapterCode)) {
			where += " and b.import_type = ?";
			args.add(adapterCode.trim().toUpperCase());
		}
		if (hasText(keyword)) {
			where += """
					 and (t.task_no ilike ? or b.import_type ilike ? or t.created_by_username ilike ?
					     or coalesce(t.error_summary, '') ilike ?)
					""";
			String pattern = "%" + keyword.trim() + "%";
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
			args.add(pattern);
		}
		List<HistoryImportRecord> visible = this.jdbcTemplate.query("""
				select t.id, t.task_no, b.import_type as adapter_code, a.target_object_type, a.template_version,
				       b.source_sha256, t.stage, t.status, t.total_count, t.success_count, t.error_count,
				       t.error_summary, t.created_by_user_id, t.created_by_username, t.created_at, t.finished_at,
				       t.version
				from platform_document_task t
				join platform_import_batch b on b.task_id = t.id
				join platform_import_adapter_definition a on a.adapter_code = b.import_type
				%s
				order by t.created_at desc, t.id desc
				""".formatted(where), (rs, rowNum) -> mapRecord(rs, currentUser), args.toArray())
			.stream()
			.filter((record) -> currentUser.permissions().contains(adapter(record.adapterCode()).requiredPermissionCode()))
			.toList();
		int from = Math.min(offset(page, pageSize), visible.size());
		int to = Math.min(from + limit(pageSize), visible.size());
		return PageResponse.of(visible.subList(from, to), page, limit(pageSize), visible.size());
	}

	@Transactional
	public HistoryImportRecord get(Long id, CurrentUser currentUser) {
		requirePermission(currentUser, "platform:history-import:view");
		expireReadyImports();
		HistoryImportRecord record = record(id, currentUser);
		AdapterDefinition adapter = adapter(record.adapterCode());
		requirePermission(currentUser, adapter.requiredPermissionCode());
		return record;
	}

	@Transactional
	public HistoryImportRecord upload(String adapterCode, MultipartFile file, String idempotencyKey,
			CurrentUser operator, HttpServletRequest servletRequest) {
		validateIdempotencyKey(idempotencyKey);
		requirePermission(operator, "platform:history-import:create");
		AdapterDefinition adapter = adapter(adapterCode);
		requirePermission(operator, adapter.requiredPermissionCode());
		byte[] content = readXlsx(file);
		String sourceSha256 = sha256(content);
		String filename = safeFilename(file.getOriginalFilename());
		String payloadJson = json(new UploadPayload(adapter.adapterCode(), adapter.templateVersion(), filename,
				sourceSha256));
		List<ExistingTask> existingTasks = existingTask(operator.id(), historyTaskType(adapter.adapterCode()),
				idempotencyKey);
		if (!existingTasks.isEmpty()) {
			ExistingTask existing = existingTasks.getFirst();
			if (!jsonEquivalent(payloadJson, existing.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(existing.id(), operator);
		}
		PlatformStorageService.StoredObject storedObject = this.storageService.put(
				"imports/history/" + adapter.adapterCode().toLowerCase() + "/" + UUID.randomUUID() + ".xlsx",
				content, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		Long fileId = insertFile(storedObject, filename,
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content, "IMPORT_SOURCE",
				operator);
		try {
			Long taskId = insertHistoryTask(adapter, payloadJson, idempotencyKey.trim(), fileId, operator);
			Long batchId = this.jdbcTemplate.queryForObject("""
					insert into platform_import_batch (
						task_id, import_type, source_file_id, source_sha256, status, created_at, updated_at
					)
					values (?, ?, ?, ?, 'QUEUED', now(), now())
					returning id
					""", Long.class, taskId, adapter.adapterCode(), fileId, sourceSha256);
			ValidationResult validation = validate(adapter, content);
			for (ImportRow row : validation.rows()) {
				insertImportRow(batchId, row.rowNo(), row.payload());
			}
			if (!validation.errors().isEmpty()) {
				recordImportErrors(taskId, batchId, validation.errors());
				markValidationFailed(taskId, batchId, validation.totalRows(), validation.errors().size());
			}
			else {
				markReadyToCommit(taskId, batchId, validation.totalRows());
			}
			this.auditService.recordDetail(operator, "HISTORY_IMPORT_UPLOAD", "DOCUMENT_TASK", taskId,
					adapter.adapterCode(), json(Map.of("adapterCode", adapter.adapterCode(), "sourceSha256",
							sourceSha256, "totalRows", validation.totalRows(), "failedRows",
							validation.errors().size())),
					servletRequest);
			return get(taskId, operator);
		}
		catch (DuplicateKeyException exception) {
			this.storageService.deleteQuietly(storedObject.objectKey());
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
		catch (RuntimeException exception) {
			this.storageService.deleteQuietly(storedObject.objectKey());
			throw exception;
		}
	}

	@Transactional
	public HistoryImportRecord confirm(Long id, ConfirmHistoryImportRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (request == null || request.version() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateIdempotencyKey(request.idempotencyKey());
		requirePermission(operator, "platform:history-import:confirm");
		expireReadyImports();
		String fingerprint = actionFingerprint("HISTORY_IMPORT_CONFIRM", id);
		HistoryImportRecord existing = existingActionResult(operator, "HISTORY_IMPORT_CONFIRM", "DOCUMENT_TASK",
				id, request.idempotencyKey(), fingerprint);
		if (existing != null) {
			return existing;
		}
		TaskRow task = lockTask(id);
		if (task == null || !task.taskType().endsWith("_HISTORY_IMPORT")) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_NOT_FOUND);
		}
		AdapterDefinition adapter = adapter(task.adapterCode());
		requirePermission(operator, adapter.requiredPermissionCode());
		if (!request.version().equals(task.version())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_CONCURRENT_MODIFICATION);
		}
		if (!"READY_TO_COMMIT".equals(task.status())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		validateUploadSnapshot(task, adapter);
		List<Map<String, Object>> rows = importRows(task.batchId());
		revalidateBeforeCommit(adapter, rows);
		commitRows(adapter, rows, operator);
		markImportSucceeded(task.id(), task.batchId(), rows.size());
		recordActionIdempotency(operator, "HISTORY_IMPORT_CONFIRM", "DOCUMENT_TASK", id, request.idempotencyKey(),
				fingerprint);
		this.auditService.recordDetail(operator, "HISTORY_IMPORT_CONFIRM", "DOCUMENT_TASK", id,
				adapter.adapterCode(), json(Map.of("adapterCode", adapter.adapterCode(), "totalRows", rows.size())),
				servletRequest);
		return get(id, operator);
	}

	@Transactional
	public HistoryImportRecord cancel(Long id, CancelHistoryImportRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (request == null || request.version() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateIdempotencyKey(request.idempotencyKey());
		requirePermission(operator, "platform:history-import:cancel");
		expireReadyImports();
		String fingerprint = actionFingerprint("HISTORY_IMPORT_CANCEL", id);
		HistoryImportRecord existing = existingActionResult(operator, "HISTORY_IMPORT_CANCEL", "DOCUMENT_TASK",
				id, request.idempotencyKey(), fingerprint);
		if (existing != null) {
			return existing;
		}
		TaskRow task = lockTask(id);
		if (task == null || !task.taskType().endsWith("_HISTORY_IMPORT")) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_NOT_FOUND);
		}
		AdapterDefinition adapter = adapter(task.adapterCode());
		requirePermission(operator, adapter.requiredPermissionCode());
		if (!request.version().equals(task.version())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_CONCURRENT_MODIFICATION);
		}
		if (!List.of("READY_TO_COMMIT", "VALIDATION_FAILED").contains(task.status())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID);
		}
		markImportCancelled(task.id(), task.batchId());
		recordActionIdempotency(operator, "HISTORY_IMPORT_CANCEL", "DOCUMENT_TASK", id, request.idempotencyKey(),
				fingerprint);
		this.auditService.recordDetail(operator, "HISTORY_IMPORT_CANCEL", "DOCUMENT_TASK", id,
				adapter.adapterCode(), json(Map.of("adapterCode", adapter.adapterCode(), "status", "CANCELLED")),
				servletRequest);
		return get(id, operator);
	}

	private ValidationResult validate(AdapterDefinition adapter, byte[] content) {
		return switch (adapter.adapterCode()) {
			case "CUSTOMER_MASTER_V1" -> validatePartner(content, "customers", false, adapter.maxRows());
			case "SUPPLIER_MASTER_V1" -> validatePartner(content, "suppliers", true, adapter.maxRows());
			case "MATERIAL_MASTER_V1" -> validateMaterials(content, adapter.maxRows());
			case "BOM_DRAFT_V1" -> validateBomDraft(content);
			case "SALES_PROJECT_DRAFT_V1" -> validateSalesProjects(content, adapter.maxRows());
			default -> throw new BusinessException(ApiErrorCode.HISTORY_IMPORT_ADAPTER_NOT_SUPPORTED);
		};
	}

	private ValidationResult validatePartner(byte[] content, String sheetName, boolean supplier, int maxRows) {
		List<ImportRow> rows = new ArrayList<>();
		List<ImportError> errors = new ArrayList<>();
		Set<String> codes = new HashSet<>();
		try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
			validateWorkbookSheets(workbook, List.of(sheetName));
			Sheet sheet = workbook.getSheet(sheetName);
			validateHeader(sheet, new String[] { "code", "name", "contactName", "contactPhone", "status", "remark" });
			validateVisibleColumns(sheet, 6);
			validateVisibleRows(sheet, 1);
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null || rowIsBlank(row)) {
					continue;
				}
				if (rows.size() + 1 > maxRows) {
					errors.add(new ImportError(i + 1, "file", ApiErrorCode.IMPORT_FILE_INVALID.name(), "导入行数超过上限"));
					continue;
				}
				Map<String, Object> payload = rowPayload("code", cellString(row, 0), "name", cellString(row, 1),
						"contactName", cellString(row, 2), "contactPhone", cellString(row, 3), "status",
						cellString(row, 4), "remark", cellString(row, 5));
				validatePartnerRow(i + 1, payload, supplier, codes, errors);
				rows.add(new ImportRow(i + 1, payload));
			}
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		if (rows.isEmpty()) {
			errors.add(new ImportError(null, "file", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "导入文件不能为空"));
		}
		return new ValidationResult(rows, errors, rows.size());
	}

	private void validatePartnerRow(int rowNo, Map<String, Object> row, boolean supplier, Set<String> codes,
			List<ImportError> errors) {
		String code = text(row, "code");
		String name = text(row, "name");
		String status = statusOrEnabled(text(row, "status"));
		if (!hasText(code) || !hasText(name)) {
			errors.add(new ImportError(rowNo, "code", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "编码和名称必填"));
		}
		if (hasText(code) && !codes.add(code)) {
			errors.add(new ImportError(rowNo, "code", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "文件内编码重复"));
		}
		if (hasText(code) && partnerCodeCount(code, supplier) > 0) {
			errors.add(new ImportError(rowNo, "code", ApiErrorCode.HISTORY_IMPORT_ALREADY_EXISTS.name(), "编码已存在"));
		}
		if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
			errors.add(new ImportError(rowNo, "status", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "状态不合法"));
		}
		row.put("status", status);
	}

	private ValidationResult validateMaterials(byte[] content, int maxRows) {
		List<ImportRow> rows = new ArrayList<>();
		List<ImportError> errors = new ArrayList<>();
		Set<String> codes = new HashSet<>();
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
				if (rows.size() + 1 > maxRows) {
					errors.add(new ImportError(i + 1, "file", ApiErrorCode.IMPORT_FILE_INVALID.name(), "导入行数超过上限"));
					continue;
				}
				Map<String, Object> payload = rowPayload("code", cellString(row, 0), "name", cellString(row, 1),
						"specification", cellString(row, 2), "materialType", cellString(row, 3), "sourceType",
						cellString(row, 4), "trackingMethod", cellString(row, 5), "categoryCode", cellString(row, 6),
						"unitCode", cellString(row, 7), "status", cellString(row, 8), "costCategory",
						cellString(row, 9), "inventoryValuationCategory", cellString(row, 10),
						"inventoryValueEnabled", cellString(row, 11), "projectCostEnabled", cellString(row, 12),
						"costRemark", cellString(row, 13), "remark", cellString(row, 14));
				validateMaterialRow(i + 1, payload, codes, errors);
				rows.add(new ImportRow(i + 1, payload));
			}
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		if (rows.isEmpty()) {
			errors.add(new ImportError(null, "file", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "导入文件不能为空"));
		}
		return new ValidationResult(rows, errors, rows.size());
	}

	private void validateMaterialRow(int rowNo, Map<String, Object> row, Set<String> codes, List<ImportError> errors) {
		String code = text(row, "code");
		if (!hasText(code) || !hasText(text(row, "name")) || !hasText(text(row, "materialType"))
				|| !hasText(text(row, "sourceType")) || !hasText(text(row, "categoryCode"))
				|| !hasText(text(row, "unitCode"))) {
			errors.add(new ImportError(rowNo, "code", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "物料必填字段缺失"));
		}
		if (hasText(code) && !codes.add(code)) {
			errors.add(new ImportError(rowNo, "code", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "文件内编码重复"));
		}
		if (hasText(code) && count("mst_material", "code", code) > 0) {
			errors.add(new ImportError(rowNo, "code", ApiErrorCode.HISTORY_IMPORT_ALREADY_EXISTS.name(), "物料编码已存在"));
		}
		if (hasText(text(row, "categoryCode")) && idByCode("mst_material_category", text(row, "categoryCode")) == null) {
			errors.add(new ImportError(rowNo, "categoryCode", ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(),
					"物料分类不存在或未启用"));
		}
		if (hasText(text(row, "unitCode")) && idByCode("mst_unit", text(row, "unitCode")) == null) {
			errors.add(new ImportError(rowNo, "unitCode", ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(),
					"单位不存在或未启用"));
		}
		normalizeMaterialValue(rowNo, row, "materialType", null, MATERIAL_TYPE_VALUES, errors);
		normalizeMaterialValue(rowNo, row, "sourceType", null, MATERIAL_SOURCE_TYPE_VALUES, errors);
		normalizeMaterialValue(rowNo, row, "trackingMethod", "NONE", MATERIAL_TRACKING_METHOD_VALUES, errors);
		normalizeMaterialValue(rowNo, row, "status", "ENABLED", MATERIAL_STATUS_VALUES, errors);
		normalizeMaterialValue(rowNo, row, "costCategory", "UNCLASSIFIED", MATERIAL_COST_CATEGORY_VALUES, errors);
		normalizeMaterialValue(rowNo, row, "inventoryValuationCategory", "UNCLASSIFIED",
				MATERIAL_INVENTORY_VALUATION_CATEGORY_VALUES, errors);
		normalizeMaterialBoolean(rowNo, row, "inventoryValueEnabled", errors);
		normalizeMaterialBoolean(rowNo, row, "projectCostEnabled", errors);
	}

	private void normalizeMaterialValue(int rowNo, Map<String, Object> row, String field, String defaultValue,
			Set<String> allowedValues, List<ImportError> errors) {
		String value = text(row, field);
		if (!hasText(value)) {
			if (defaultValue != null) {
				row.put(field, defaultValue);
			}
			return;
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		row.put(field, normalized);
		if (!allowedValues.contains(normalized)) {
			errors.add(new ImportError(rowNo, field, ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "字段取值不合法"));
		}
	}

	private void normalizeMaterialBoolean(int rowNo, Map<String, Object> row, String field,
			List<ImportError> errors) {
		String value = text(row, field);
		if (!hasText(value)) {
			row.put(field, "false");
			return;
		}
		if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
			errors.add(new ImportError(rowNo, field, ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "字段取值不合法"));
			return;
		}
		row.put(field, value.trim().toLowerCase(Locale.ROOT));
	}

	private ValidationResult validateBomDraft(byte[] content) {
		List<ImportError> errors = new ArrayList<>();
		List<ImportRow> rows = new ArrayList<>();
		try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
			validateWorkbookSheets(workbook, List.of("bom", "items"));
			Sheet bom = workbook.getSheet("bom");
			Sheet items = workbook.getSheet("items");
			validateHeader(bom, new String[] { "mode", "bomId", "version", "bomCode", "parentMaterialCode",
					"versionCode", "name", "baseQuantity", "baseUnit", "effectiveFrom", "effectiveTo", "remark" });
			validateHeader(items, new String[] { "lineNo", "childMaterialCode", "businessUnit",
					"businessQuantity", "lossRate", "warehouse", "remark" });
			validateVisibleColumns(bom, 12);
			validateVisibleColumns(items, 7);
			validateVisibleRows(bom, 1);
			validateVisibleRows(items, 1);
			Row header = bom.getRow(1);
			Map<String, Object> payload = rowPayload("mode", cellString(header, 0), "bomId", cellString(header, 1),
					"version", cellString(header, 2), "bomCode", cellString(header, 3), "parentMaterialCode",
					cellString(header, 4), "versionCode", cellString(header, 5), "name", cellString(header, 6),
					"baseQuantity", cellString(header, 7), "baseUnit", cellString(header, 8), "effectiveFrom",
					cellString(header, 9), "effectiveTo", cellString(header, 10), "remark", cellString(header, 11),
					"items", bomItems(items, errors));
			validateBomPayload(payload, errors);
			rows.add(new ImportRow(2, payload));
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		return new ValidationResult(rows, errors, rows.size());
	}

	private List<Map<String, Object>> bomItems(Sheet items, List<ImportError> errors) {
		List<Map<String, Object>> result = new ArrayList<>();
		for (int i = 1; i <= items.getLastRowNum(); i++) {
			Row row = items.getRow(i);
			if (row == null || rowIsBlank(row)) {
				continue;
			}
			Map<String, Object> payload = rowPayload("lineNo", cellString(row, 0), "childMaterialCode",
					cellString(row, 1), "businessUnit", cellString(row, 2), "businessQuantity", cellString(row, 3),
					"lossRate", cellString(row, 4), "warehouse", cellString(row, 5), "remark", cellString(row, 6));
			if (hasText(text(payload, "warehouse"))) {
				errors.add(new ImportError(i + 1, "warehouse", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(),
						"warehouse 必须留空"));
			}
			result.add(payload);
		}
		return result;
	}

	private void validateBomPayload(Map<String, Object> row, List<ImportError> errors) {
		if (!"CREATE".equals(text(row, "mode"))) {
			errors.add(new ImportError(2, "mode", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "034 历史导入仅允许创建草稿"));
		}
		if (!hasText(text(row, "bomCode")) || !hasText(text(row, "versionCode")) || !hasText(text(row, "name"))
				|| !hasText(text(row, "parentMaterialCode")) || !hasText(text(row, "baseUnit"))) {
			errors.add(new ImportError(2, "bomCode", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "BOM 必填字段缺失"));
		}
		if (hasText(text(row, "bomCode")) && count("mfg_bom", "bom_code", text(row, "bomCode")) > 0) {
			errors.add(new ImportError(2, "bomCode", ApiErrorCode.HISTORY_IMPORT_ALREADY_EXISTS.name(),
					"BOM 编码已存在"));
		}
		BomMaterialRef parent = materialByCode(text(row, "parentMaterialCode"));
		if (parent == null) {
			errors.add(new ImportError(2, "parentMaterialCode", ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(),
					"父项物料不存在或未启用"));
		}
		Long baseUnitId = unitIdByCode(text(row, "baseUnit"));
		if (baseUnitId == null) {
			errors.add(new ImportError(2, "baseUnit", ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(), "基准单位不存在"));
		}
		else {
			row.put("baseUnitId", baseUnitId);
		}
		if (parent != null && baseUnitId != null && !parent.unitId().equals(baseUnitId)) {
			errors.add(new ImportError(2, "baseUnit", ApiErrorCode.BOM_UNIT_INVALID.name(), "BOM 基准单位必须等于父项单位"));
		}
		BigDecimal baseQuantity = parsePositiveDecimal(2, "baseQuantity", text(row, "baseQuantity"), errors);
		if (baseQuantity != null) {
			row.put("baseQuantity", decimalText(baseQuantity));
		}
		LocalDate effectiveFrom = parseDate(2, "effectiveFrom", text(row, "effectiveFrom"), errors);
		LocalDate effectiveTo = parseDate(2, "effectiveTo", text(row, "effectiveTo"), errors);
		if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
			errors.add(new ImportError(2, "effectiveTo", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "BOM 生效日期范围不合法"));
		}
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) row.get("items");
		if (items == null || items.isEmpty()) {
			errors.add(new ImportError(2, "items", ApiErrorCode.BOM_EMPTY_ITEMS.name(), "BOM 明细不能为空"));
			return;
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<Long> childMaterialIds = new HashSet<>();
		for (Map<String, Object> item : items) {
			validateBomItem(parent, effectiveFrom, item, lineNos, childMaterialIds, errors);
		}
	}

	private void validateBomItem(BomMaterialRef parent, LocalDate effectiveFrom, Map<String, Object> item,
			Set<Integer> lineNos, Set<Long> childMaterialIds, List<ImportError> errors) {
		Integer rowNo = parsePositiveInteger(bomItemRowNo(item), "lineNo", text(item, "lineNo"), errors);
		if (rowNo != null) {
			item.put("lineNo", rowNo);
			if (!lineNos.add(rowNo)) {
				errors.add(new ImportError(rowNo, "lineNo", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "BOM 行号重复"));
			}
		}
		BomMaterialRef child = materialByCode(text(item, "childMaterialCode"));
		if (child == null) {
			errors.add(new ImportError(bomItemRowNo(item), "childMaterialCode",
					ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(), "BOM 明细物料不存在或未启用"));
		}
		else {
			item.put("childMaterialId", child.id());
			if (parent != null && child.id().equals(parent.id())) {
				errors.add(new ImportError(bomItemRowNo(item), "childMaterialCode",
						ApiErrorCode.BOM_SELF_REFERENCE.name(), "BOM 明细不能引用父项"));
			}
			if (!childMaterialIds.add(child.id())) {
				errors.add(new ImportError(bomItemRowNo(item), "childMaterialCode",
						ApiErrorCode.BOM_DUPLICATE_ITEM.name(), "BOM 明细物料重复"));
			}
		}
		Long businessUnitId = unitIdByCode(text(item, "businessUnit"));
		if (businessUnitId == null) {
			errors.add(new ImportError(bomItemRowNo(item), "businessUnit",
					ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(), "BOM 明细业务单位不存在或未启用"));
		}
		else {
			item.put("businessUnitId", businessUnitId);
		}
		BigDecimal businessQuantity = parsePositiveDecimal(bomItemRowNo(item), "businessQuantity",
				text(item, "businessQuantity"), errors);
		if (businessQuantity != null) {
			item.put("businessQuantity", decimalText(businessQuantity));
		}
		BigDecimal lossRate = parseLossRate(bomItemRowNo(item), text(item, "lossRate"), errors);
		if (lossRate != null) {
			item.put("lossRate", decimalText(lossRate));
		}
		if (child == null || businessUnitId == null || businessQuantity == null) {
			return;
		}
		try {
			UnitConversionAdminService.ConversionSnapshot snapshot = this.unitConversionAdminService
				.conversionSnapshot(child.id(), businessUnitId, businessQuantity,
						effectiveFrom == null ? LocalDate.now() : effectiveFrom);
			item.put("baseUnitId", snapshot.baseUnitId());
			item.put("baseQuantity", decimalText(snapshot.baseQuantity()));
			item.put("conversionId", snapshot.conversionId());
			item.put("conversionRateSnapshot", decimalText(snapshot.conversionRateSnapshot()));
			item.put("quantityScaleSnapshot", snapshot.quantityScaleSnapshot());
			item.put("roundingModeSnapshot", snapshot.roundingModeSnapshot());
			item.put("quantityBasis", snapshot.quantityBasis());
		}
		catch (BusinessException exception) {
			errors.add(new ImportError(bomItemRowNo(item), "businessUnit", exception.errorCode().name(),
					exception.getMessage()));
		}
	}

	private BomMaterialRef materialByCode(String code) {
		if (!hasText(code)) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select id, unit_id
				from mst_material
				where code = ?
				  and status = 'ENABLED'
				""", (rs, rowNum) -> new BomMaterialRef(rs.getLong("id"), rs.getLong("unit_id")), code)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private Long unitIdByCode(String code) {
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

	private Integer bomItemRowNo(Map<String, Object> item) {
		try {
			String lineNo = text(item, "lineNo");
			return hasText(lineNo) ? Integer.valueOf(lineNo) : null;
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	private Integer parsePositiveInteger(Integer rowNo, String field, String value, List<ImportError> errors) {
		try {
			Integer number = hasText(value) ? Integer.valueOf(value.trim()) : null;
			if (number == null || number <= 0) {
				errors.add(new ImportError(rowNo, field, ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "字段必须为正整数"));
				return null;
			}
			return number;
		}
		catch (RuntimeException exception) {
			errors.add(new ImportError(rowNo, field, ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "字段必须为正整数"));
			return null;
		}
	}

	private BigDecimal parsePositiveDecimal(Integer rowNo, String field, String value, List<ImportError> errors) {
		try {
			BigDecimal number = hasText(value) ? new BigDecimal(value.trim()) : null;
			if (number == null || number.compareTo(BigDecimal.ZERO) <= 0) {
				errors.add(new ImportError(rowNo, field, ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "字段必须大于 0"));
				return null;
			}
			return number.setScale(6, RoundingMode.HALF_UP);
		}
		catch (RuntimeException exception) {
			errors.add(new ImportError(rowNo, field, ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "字段必须为数字"));
			return null;
		}
	}

	private BigDecimal parseLossRate(Integer rowNo, String value, List<ImportError> errors) {
		if (!hasText(value)) {
			return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
		}
		try {
			BigDecimal number = new BigDecimal(value.trim());
			if (number.compareTo(BigDecimal.ZERO) < 0 || number.compareTo(BigDecimal.ONE) >= 0) {
				errors.add(new ImportError(rowNo, "lossRate", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(),
						"损耗率必须大于等于 0 且小于 1"));
				return null;
			}
			return number.setScale(6, RoundingMode.HALF_UP);
		}
		catch (RuntimeException exception) {
			errors.add(new ImportError(rowNo, "lossRate", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(),
					"损耗率必须为数字"));
			return null;
		}
	}

	private LocalDate parseDate(Integer rowNo, String field, String value, List<ImportError> errors) {
		if (!hasText(value)) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (RuntimeException exception) {
			errors.add(new ImportError(rowNo, field, ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "日期格式不合法"));
			return null;
		}
	}

	private String decimalText(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	private ValidationResult validateSalesProjects(byte[] content, int maxRows) {
		List<ImportRow> rows = new ArrayList<>();
		List<ImportError> errors = new ArrayList<>();
		Set<String> codes = new HashSet<>();
		try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
			validateWorkbookSheets(workbook, List.of("salesProjects"));
			Sheet sheet = workbook.getSheet("salesProjects");
			validateHeader(sheet, new String[] { "projectNo", "name", "customerCode", "ownerUsername",
					"plannedStartDate", "plannedFinishDate", "targetRevenue", "targetCost", "status", "remark" });
			validateVisibleColumns(sheet, 10);
			validateVisibleRows(sheet, 1);
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null || rowIsBlank(row)) {
					continue;
				}
				if (rows.size() + 1 > maxRows) {
					errors.add(new ImportError(i + 1, "file", ApiErrorCode.IMPORT_FILE_INVALID.name(), "导入行数超过上限"));
					continue;
				}
				Map<String, Object> payload = rowPayload("projectNo", cellString(row, 0), "name", cellString(row, 1),
						"customerCode", cellString(row, 2), "ownerUsername", cellString(row, 3),
						"plannedStartDate", cellString(row, 4), "plannedFinishDate", cellString(row, 5),
						"targetRevenue", cellString(row, 6), "targetCost", cellString(row, 7), "status",
						cellString(row, 8), "remark", cellString(row, 9));
				validateSalesProjectRow(i + 1, payload, codes, errors);
				rows.add(new ImportRow(i + 1, payload));
			}
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		if (rows.isEmpty()) {
			errors.add(new ImportError(null, "file", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "导入文件不能为空"));
		}
		return new ValidationResult(rows, errors, rows.size());
	}

	private void validateSalesProjectRow(int rowNo, Map<String, Object> row, Set<String> codes,
			List<ImportError> errors) {
		String projectNo = text(row, "projectNo");
		if (!hasText(projectNo) || !hasText(text(row, "name")) || !hasText(text(row, "customerCode"))) {
			errors.add(new ImportError(rowNo, "projectNo", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "项目必填字段缺失"));
		}
		if (hasText(projectNo) && !codes.add(projectNo)) {
			errors.add(new ImportError(rowNo, "projectNo", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(), "文件内项目号重复"));
		}
		if (hasText(projectNo) && count("sal_project", "project_no", projectNo) > 0) {
			errors.add(new ImportError(rowNo, "projectNo", ApiErrorCode.HISTORY_IMPORT_ALREADY_EXISTS.name(),
					"项目号已存在"));
		}
		if (idByCode("mst_customer", text(row, "customerCode")) == null) {
			errors.add(new ImportError(rowNo, "customerCode", ApiErrorCode.MASTER_DATA_REFERENCE_INVALID.name(),
					"客户不存在或未启用"));
		}
		String status = hasText(text(row, "status")) ? text(row, "status") : "DRAFT";
		if (!"DRAFT".equals(status)) {
			errors.add(new ImportError(rowNo, "status", ApiErrorCode.IMPORT_VALIDATION_FAILED.name(),
					"销售项目历史导入仅允许草稿"));
		}
		row.put("status", "DRAFT");
	}

	private void revalidateBeforeCommit(AdapterDefinition adapter, List<Map<String, Object>> rows) {
		for (Map<String, Object> row : rows) {
			switch (adapter.adapterCode()) {
				case "CUSTOMER_MASTER_V1" -> assertPartnerNotExists(row, false);
				case "SUPPLIER_MASTER_V1" -> assertPartnerNotExists(row, true);
				case "MATERIAL_MASTER_V1" -> {
					assertNotExists("mst_material", "code", text(row, "code"));
					assertMaterialPayloadAccepted(row);
				}
				case "BOM_DRAFT_V1" -> {
					assertNotExists("mfg_bom", "bom_code", text(row, "bomCode"));
					assertBomPayloadAccepted(row);
				}
				case "SALES_PROJECT_DRAFT_V1" -> assertNotExists("sal_project", "project_no", text(row, "projectNo"));
				default -> throw new BusinessException(ApiErrorCode.HISTORY_IMPORT_ADAPTER_NOT_SUPPORTED);
			}
		}
	}

	private void assertMaterialPayloadAccepted(Map<String, Object> row) {
		assertMaterialValueAccepted(row, "materialType", MATERIAL_TYPE_VALUES);
		assertMaterialValueAccepted(row, "sourceType", MATERIAL_SOURCE_TYPE_VALUES);
		assertMaterialValueAccepted(row, "trackingMethod", MATERIAL_TRACKING_METHOD_VALUES);
		assertMaterialValueAccepted(row, "status", MATERIAL_STATUS_VALUES);
		assertMaterialValueAccepted(row, "costCategory", MATERIAL_COST_CATEGORY_VALUES);
		assertMaterialValueAccepted(row, "inventoryValuationCategory", MATERIAL_INVENTORY_VALUATION_CATEGORY_VALUES);
		assertMaterialBooleanAccepted(row, "inventoryValueEnabled");
		assertMaterialBooleanAccepted(row, "projectCostEnabled");
	}

	private void assertMaterialValueAccepted(Map<String, Object> row, String field, Set<String> allowedValues) {
		if (!allowedValues.contains(text(row, field))) {
			throw new BusinessException(ApiErrorCode.IMPORT_VALIDATION_FAILED);
		}
	}

	private void assertMaterialBooleanAccepted(Map<String, Object> row, String field) {
		String value = text(row, field);
		if (!"true".equals(value) && !"false".equals(value)) {
			throw new BusinessException(ApiErrorCode.IMPORT_VALIDATION_FAILED);
		}
	}

	@SuppressWarnings("unchecked")
	private void assertBomPayloadAccepted(Map<String, Object> row) {
		if (longText(row, "baseUnitId") == null || decimalText(row, "baseQuantity") == null) {
			throw new BusinessException(ApiErrorCode.IMPORT_VALIDATION_FAILED);
		}
		List<Map<String, Object>> items = (List<Map<String, Object>>) row.get("items");
		if (items == null || items.isEmpty()) {
			throw new BusinessException(ApiErrorCode.BOM_EMPTY_ITEMS);
		}
		for (Map<String, Object> item : items) {
			if (integerText(item, "lineNo") == null || longText(item, "childMaterialId") == null
					|| longText(item, "businessUnitId") == null || decimalText(item, "businessQuantity") == null
					|| longText(item, "baseUnitId") == null || decimalText(item, "baseQuantity") == null
					|| decimalText(item, "conversionRateSnapshot") == null
					|| integerText(item, "quantityScaleSnapshot") == null
					|| !BOM_QUANTITY_BASIS_VALUES.contains(text(item, "quantityBasis"))) {
				throw new BusinessException(ApiErrorCode.IMPORT_VALIDATION_FAILED);
			}
		}
	}

	private void commitRows(AdapterDefinition adapter, List<Map<String, Object>> rows, CurrentUser operator) {
		for (Map<String, Object> row : rows) {
			switch (adapter.adapterCode()) {
				case "CUSTOMER_MASTER_V1" -> insertPartner(row, false, operator);
				case "SUPPLIER_MASTER_V1" -> insertPartner(row, true, operator);
				case "MATERIAL_MASTER_V1" -> insertMaterial(row, operator);
				case "BOM_DRAFT_V1" -> insertBomDraft(row, operator);
				case "SALES_PROJECT_DRAFT_V1" -> insertSalesProject(row, operator);
				default -> throw new BusinessException(ApiErrorCode.HISTORY_IMPORT_ADAPTER_NOT_SUPPORTED);
			}
		}
	}

	private void insertPartner(Map<String, Object> row, boolean supplier, CurrentUser operator) {
		String table = supplier ? "mst_supplier" : "mst_customer";
		this.jdbcTemplate.update("""
				insert into %s (
					code, name, contact_name, contact_phone, status, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, now(), ?, now())
				""".formatted(table), text(row, "code"), text(row, "name"), text(row, "contactName"),
				text(row, "contactPhone"), statusOrEnabled(text(row, "status")), text(row, "remark"),
				operator.username(), operator.username());
	}

	private void insertMaterial(Map<String, Object> row, CurrentUser operator) {
		this.jdbcTemplate.update("""
				insert into mst_material (
					code, name, specification, material_type, source_type, tracking_method, category_id, unit_id,
					status, remark, cost_category, inventory_valuation_category, inventory_value_enabled,
					project_cost_enabled, cost_remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, now())
				""", text(row, "code"), text(row, "name"), text(row, "specification"), text(row, "materialType"),
				text(row, "sourceType"), text(row, "trackingMethod"), idByCode("mst_material_category",
						text(row, "categoryCode")),
				idByCode("mst_unit", text(row, "unitCode")), statusOrEnabled(text(row, "status")),
				text(row, "remark"), text(row, "costCategory"), text(row, "inventoryValuationCategory"),
				booleanText(row, "inventoryValueEnabled"), booleanText(row, "projectCostEnabled"),
				text(row, "costRemark"), operator.username(), operator.username());
	}

	private void insertBomDraft(Map<String, Object> row, CurrentUser operator) {
		Long bomId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (
					bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id, status,
					effective_from, effective_to, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, now(), ?, now())
				returning id
				""", Long.class, text(row, "bomCode"), idByCode("mst_material", text(row, "parentMaterialCode")),
				text(row, "versionCode"), text(row, "name"), decimalText(row, "baseQuantity"),
				longText(row, "baseUnitId"), dateText(row, "effectiveFrom"),
				dateText(row, "effectiveTo"), text(row, "remark"), operator.username(), operator.username());
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) row.get("items");
		for (Map<String, Object> item : items) {
			this.jdbcTemplate.update("""
					insert into mfg_bom_item (
						bom_id, line_no, child_material_id, unit_id, quantity, loss_rate, remark, created_at, updated_at,
						business_unit_id, business_quantity, base_unit_id, base_quantity, conversion_id,
						conversion_rate_snapshot, quantity_scale_snapshot, rounding_mode_snapshot, quantity_basis
					)
					values (?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", bomId, integerText(item, "lineNo"), longText(item, "childMaterialId"),
					longText(item, "businessUnitId"), decimalText(item, "businessQuantity"),
					decimalText(item, "lossRate", BigDecimal.ZERO), text(item, "remark"),
					longText(item, "businessUnitId"), decimalText(item, "businessQuantity"),
					longText(item, "baseUnitId"), decimalText(item, "baseQuantity"), longText(item, "conversionId"),
					decimalText(item, "conversionRateSnapshot"), integerText(item, "quantityScaleSnapshot"),
					text(item, "roundingModeSnapshot"), text(item, "quantityBasis"));
		}
	}

	private void insertSalesProject(Map<String, Object> row, CurrentUser operator) {
		this.jdbcTemplate.update("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, now(), ?, now())
				""", text(row, "projectNo"), text(row, "name"), idByCode("mst_customer", text(row, "customerCode")),
				ownerUserId(row, operator), dateText(row, "plannedStartDate"), dateText(row, "plannedFinishDate"),
				decimalText(row, "targetRevenue", BigDecimal.ZERO), decimalText(row, "targetCost", BigDecimal.ZERO),
				text(row, "remark"), operator.username(), operator.username());
	}

	private Long ownerUserId(Map<String, Object> row, CurrentUser operator) {
		String username = text(row, "ownerUsername");
		if (!hasText(username)) {
			return operator.id();
		}
		return this.jdbcTemplate.query("""
				select id
				from sys_user
				where username = ?
				  and status = 'ENABLED'
				""", (rs, rowNum) -> rs.getLong("id"), username).stream().findFirst().orElse(operator.id());
	}

	private HistoryImportAdapterRecord mapAdapter(ResultSet rs, int rowNum) throws SQLException {
		return new HistoryImportAdapterRecord(rs.getString("adapter_code"), rs.getString("name"),
				rs.getString("target_object_type"), rs.getString("template_code"), rs.getInt("template_version"),
				rs.getInt("max_rows"), rs.getString("required_permission_code"), rs.getString("description"),
				rs.getLong("version"));
	}

	private HistoryImportRecord mapRecord(ResultSet rs, CurrentUser currentUser) throws SQLException {
		AdapterDefinition adapter = adapter(rs.getString("adapter_code"));
		return new HistoryImportRecord(rs.getLong("id"), rs.getString("task_no"), rs.getString("adapter_code"),
				rs.getString("target_object_type"), rs.getInt("template_version"), rs.getString("source_sha256"),
				rs.getString("stage"), rs.getString("status"), rs.getInt("total_count"),
				rs.getInt("success_count"), rs.getInt("error_count"), rs.getString("error_summary"),
				rs.getString("created_by_username"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("finished_at", OffsetDateTime.class), rs.getLong("version"),
				availableActions(rs.getString("status"), adapter, currentUser));
	}

	private HistoryImportRecord record(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select t.id, t.task_no, b.import_type as adapter_code, a.target_object_type, a.template_version,
				       b.source_sha256, t.stage, t.status, t.total_count, t.success_count, t.error_count,
				       t.error_summary, t.created_by_user_id, t.created_by_username, t.created_at, t.finished_at,
				       t.version
				from platform_document_task t
				join platform_import_batch b on b.task_id = t.id
				join platform_import_adapter_definition a on a.adapter_code = b.import_type
				where t.id = ?
				""", (rs, rowNum) -> mapRecord(rs, currentUser), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.DOCUMENT_TASK_NOT_FOUND));
	}

	private AdapterDefinition adapter(String adapterCode) {
		if (!hasText(adapterCode)) {
			throw new BusinessException(ApiErrorCode.HISTORY_IMPORT_ADAPTER_NOT_SUPPORTED);
		}
		return this.jdbcTemplate.query("""
				select adapter_code, target_object_type, template_code, template_version, max_rows,
				       required_permission_code
				from platform_import_adapter_definition
				where adapter_code = ?
				  and status = 'ENABLED'
				""", (rs, rowNum) -> new AdapterDefinition(rs.getString("adapter_code"),
				rs.getString("target_object_type"), rs.getString("template_code"), rs.getInt("template_version"),
				rs.getInt("max_rows"), rs.getString("required_permission_code")), adapterCode.trim().toUpperCase())
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.HISTORY_IMPORT_ADAPTER_NOT_SUPPORTED));
	}

	private TaskRow lockTask(Long taskId) {
		return this.jdbcTemplate.query("""
				select t.id, t.task_type, t.status, t.version, t.request_payload::text as request_payload,
				       t.source_file_id, b.id as batch_id, b.import_type as adapter_code,
				       b.source_sha256 as batch_source_sha256, f.sha256 as file_source_sha256
				from platform_document_task t
				join platform_import_batch b on b.task_id = t.id
				left join platform_file_object f on f.id = t.source_file_id and f.status = 'AVAILABLE'
				where t.id = ?
				for update of t, b
				""", (rs, rowNum) -> new TaskRow(rs.getLong("id"), rs.getString("task_type"),
				rs.getString("status"), rs.getLong("version"), rs.getString("request_payload"),
				nullableLong(rs, "source_file_id"), rs.getLong("batch_id"), rs.getString("adapter_code"),
				rs.getString("batch_source_sha256"), rs.getString("file_source_sha256")), taskId)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private Long insertHistoryTask(AdapterDefinition adapter, String payloadJson, String idempotencyKey, Long fileId,
			CurrentUser operator) {
		return this.jdbcTemplate.queryForObject("""
				insert into platform_document_task (
					task_no, task_type, stage, status, request_payload, idempotency_key, source_file_id,
					created_by_user_id, created_by_username, next_run_at, created_at
				)
				values (?, ?, 'VALIDATE', 'QUEUED', cast(? as jsonb), ?, ?, ?, ?, null, now())
				returning id
				""", Long.class, nextTaskNo("HIMP"), historyTaskType(adapter.adapterCode()), payloadJson,
				idempotencyKey, fileId, operator.id(), operator.username());
	}

	private Long insertFile(PlatformStorageService.StoredObject storedObject, String filename, String contentType,
			byte[] content, String usage, CurrentUser operator) {
		return this.jdbcTemplate.queryForObject("""
				insert into platform_file_object (
					bucket, object_key, original_filename, content_type, size_bytes, sha256, etag,
					file_usage, status, created_by_user_id, created_by_username, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', ?, ?, now())
				returning id
				""", Long.class, storedObject.bucket(), storedObject.objectKey(), filename, contentType,
				content.length, sha256(content), storedObject.eTag(), usage, operator.id(), operator.username());
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
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'VALIDATION_FAILED', total_count = ?, success_count = 0, error_count = ?,
				    error_summary = ?, finished_at = ?, expires_at = ?, updated_at = now(), version = version + 1
				where id = ?
				""", totalCount, errorCount, "历史导入预检失败：" + errorCount + " 条错误", now, now.plusDays(1),
				taskId);
		this.jdbcTemplate.update("""
				update platform_import_batch
				set status = 'VALIDATION_FAILED', updated_at = now(), version = version + 1
				where id = ?
				""", batchId);
	}

	private void markReadyToCommit(Long taskId, Long batchId, int totalCount) {
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'READY_TO_COMMIT', total_count = ?, success_count = ?, error_count = 0,
				    finished_at = ?, expires_at = ?, updated_at = now(), version = version + 1
				where id = ?
				""", totalCount, totalCount, now, now.plusDays(1), taskId);
		this.jdbcTemplate.update("""
				update platform_import_batch
				set status = 'VALIDATED', updated_at = now(), version = version + 1
				where id = ?
				""", batchId);
	}

	private void markImportSucceeded(Long taskId, Long batchId, int totalCount) {
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'SUCCEEDED', total_count = ?, success_count = ?, error_count = 0, committed_at = ?,
				    finished_at = ?, expires_at = ?, updated_at = now(), version = version + 1
				where id = ?
				""", totalCount, totalCount, now, now, now.plusDays(7), taskId);
		this.jdbcTemplate.update("""
				update platform_import_batch
				set status = 'COMMITTED', committed_at = ?, updated_at = now(), version = version + 1
				where id = ?
				""", now, batchId);
	}

	private void markImportCancelled(Long taskId, Long batchId) {
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'CANCELLED', error_summary = '历史导入已取消', finished_at = now(),
				    updated_at = now(), version = version + 1
				where id = ?
				""", taskId);
		this.jdbcTemplate.update("""
				update platform_import_batch
				set status = 'CANCELLED', updated_at = now(), version = version + 1
				where id = ?
				""", batchId);
	}

	private void expireReadyImports() {
		this.jdbcTemplate.update("""
				update platform_import_batch b
				set status = 'CANCELLED', updated_at = now(), version = b.version + 1
				from platform_document_task t
				where b.task_id = t.id
				  and t.status in ('READY_TO_COMMIT', 'VALIDATION_FAILED')
				  and t.expires_at is not null
				  and t.expires_at < now()
				  and b.status <> 'CANCELLED'
				""");
		this.jdbcTemplate.update("""
				update platform_document_task
				set status = 'EXPIRED', error_summary = '历史导入确认窗口已过期', finished_at = now(),
				    updated_at = now(), version = version + 1
				where status in ('READY_TO_COMMIT', 'VALIDATION_FAILED')
				  and expires_at is not null
				  and expires_at < now()
				""");
	}

	private List<Map<String, Object>> importRows(Long batchId) {
		return this.jdbcTemplate.query("""
				select payload::text
				from platform_import_row
				where batch_id = ?
				order by row_no
				""", (rs, rowNum) -> parseMap(rs.getString("payload")), batchId);
	}

	private void validateUploadSnapshot(TaskRow task, AdapterDefinition adapter) {
		UploadPayload upload = parse(task.requestPayload(), UploadPayload.class);
		if (!adapter.adapterCode().equals(upload.adapterCode()) || adapter.templateVersion() != upload.templateVersion()) {
			throw new BusinessException(ApiErrorCode.HISTORY_IMPORT_TEMPLATE_VERSION_MISMATCH);
		}
		if (task.sourceFileId() == null || !upload.sourceSha256().equals(task.batchSourceSha256())
				|| !upload.sourceSha256().equals(task.fileSourceSha256())) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_CONCURRENT_MODIFICATION);
		}
	}

	private HistoryImportRecord existingActionResult(CurrentUser operator, String action, String targetType,
			Long targetId, String idempotencyKey, String fingerprint) {
		List<ActionIdempotency> rows = this.jdbcTemplate.query("""
				select request_fingerprint, result_id
				from platform_action_idempotency
				where operator_user_id = ?
				  and action = ?
				  and target_type = ?
				  and target_id = ?
				  and idempotency_key = ?
				""", (rs, rowNum) -> new ActionIdempotency(rs.getString("request_fingerprint"),
				rs.getLong("result_id")), operator.id(), action, targetType, targetId, idempotencyKey.trim());
		if (rows.isEmpty()) {
			return null;
		}
		ActionIdempotency row = rows.getFirst();
		if (!row.requestFingerprint().equals(fingerprint)) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
		return get(row.resultId(), operator);
	}

	private void recordActionIdempotency(CurrentUser operator, String action, String targetType, Long targetId,
			String idempotencyKey, String fingerprint) {
		this.jdbcTemplate.update("""
				insert into platform_action_idempotency (
					operator_user_id, action, target_type, target_id, idempotency_key, request_fingerprint,
					result_type, result_id
				)
				values (?, ?, ?, ?, ?, ?, ?, ?)
				on conflict (operator_user_id, action, target_type, target_id, idempotency_key) do nothing
				""", operator.id(), action, targetType, targetId, idempotencyKey.trim(), fingerprint, targetType,
				targetId);
	}

	private List<ExistingTask> existingTask(Long userId, String taskType, String idempotencyKey) {
		return this.jdbcTemplate.query("""
				select id, request_payload::text as request_payload
				from platform_document_task
				where created_by_user_id = ?
				  and task_type = ?
				  and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingTask(rs.getLong("id"), rs.getString("request_payload")),
				userId, taskType, idempotencyKey.trim());
	}

	private byte[] templateWorkbook(String adapterCode) {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			switch (adapterCode) {
				case "CUSTOMER_MASTER_V1" -> addSheet(workbook, "customers", List.of("code", "name",
						"contactName", "contactPhone", "status", "remark"));
				case "SUPPLIER_MASTER_V1" -> addSheet(workbook, "suppliers", List.of("code", "name",
						"contactName", "contactPhone", "status", "remark"));
				case "MATERIAL_MASTER_V1" -> addSheet(workbook, "materials", List.of("code", "name",
						"specification", "materialType", "sourceType", "trackingMethod", "categoryCode",
						"unitCode", "status", "costCategory", "inventoryValuationCategory",
						"inventoryValueEnabled", "projectCostEnabled", "costRemark", "remark"));
				case "BOM_DRAFT_V1" -> {
					addSheet(workbook, "bom", List.of("mode", "bomId", "version", "bomCode",
							"parentMaterialCode", "versionCode", "name", "baseQuantity", "baseUnit",
							"effectiveFrom", "effectiveTo", "remark"));
					addSheet(workbook, "items", List.of("lineNo", "childMaterialCode", "businessUnit",
							"businessQuantity", "lossRate", "warehouse", "remark"));
				}
				case "SALES_PROJECT_DRAFT_V1" -> addSheet(workbook, "salesProjects", List.of("projectNo", "name",
						"customerCode", "ownerUsername", "plannedStartDate", "plannedFinishDate",
						"targetRevenue", "targetCost", "status", "remark"));
				default -> throw new BusinessException(ApiErrorCode.HISTORY_IMPORT_ADAPTER_NOT_SUPPORTED);
			}
			workbook.write(output);
			return output.toByteArray();
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.SYSTEM_ERROR);
		}
	}

	private void addSheet(Workbook workbook, String name, List<String> headers) {
		Sheet sheet = workbook.createSheet(name);
		Row row = sheet.createRow(0);
		for (int i = 0; i < headers.size(); i++) {
			row.createCell(i).setCellValue(headers.get(i));
		}
	}

	private void assertPartnerNotExists(Map<String, Object> row, boolean supplier) {
		if (partnerCodeCount(text(row, "code"), supplier) > 0) {
			throw new BusinessException(ApiErrorCode.HISTORY_IMPORT_ALREADY_EXISTS);
		}
	}

	private void assertNotExists(String tableName, String columnName, String value) {
		if (count(tableName, columnName, value) > 0) {
			throw new BusinessException(ApiErrorCode.HISTORY_IMPORT_ALREADY_EXISTS);
		}
	}

	private long partnerCodeCount(String code, boolean supplier) {
		return count(supplier ? "mst_supplier" : "mst_customer", "code", code);
	}

	private long count(String tableName, String columnName, String value) {
		if (!hasText(value)) {
			return 0;
		}
		return this.jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + columnName + " = ?",
				Long.class, value);
	}

	private Long idByCode(String tableName, String code) {
		if (!hasText(code)) {
			return null;
		}
		String statusPredicate = ("sys_user".equals(tableName) ? "status = 'ENABLED'" : "status = 'ENABLED'");
		return this.jdbcTemplate.query("select id from " + tableName + " where code = ? and " + statusPredicate,
				(rs, rowNum) -> rs.getLong("id"), code).stream().findFirst().orElse(null);
	}

	private byte[] readXlsx(MultipartFile file) {
		if (file == null || file.isEmpty() || file.getSize() > 10L * 1024L * 1024L
				|| !safeFilename(file.getOriginalFilename()).toLowerCase().endsWith(".xlsx")) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		try {
			byte[] content = file.getBytes();
			validateXlsxPackage(content);
			return content;
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
	}

	private void validateXlsxPackage(byte[] content) {
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

	private void validateWorkbookSheets(Workbook workbook, List<String> expectedSheetNames) {
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

	private void validateHeader(Sheet sheet, String[] expectedHeaders) {
		Row header = sheet.getRow(0);
		if (header == null) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
		for (int i = 0; i < expectedHeaders.length; i++) {
			if (!expectedHeaders[i].equals(cellString(header, i))) {
				throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
			}
		}
		if (header.getLastCellNum() > expectedHeaders.length) {
			throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
		}
	}

	private void validateVisibleColumns(Sheet sheet, int columnCount) {
		for (int i = 0; i < columnCount; i++) {
			if (sheet.isColumnHidden(i)) {
				throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
			}
		}
	}

	private void validateVisibleRows(Sheet sheet, int firstDataRow) {
		for (int i = firstDataRow; i <= sheet.getLastRowNum(); i++) {
			Row row = sheet.getRow(i);
			if (row != null && row.getZeroHeight() && !rowIsBlank(row)) {
				throw new BusinessException(ApiErrorCode.IMPORT_FILE_INVALID);
			}
		}
	}

	private boolean rowIsBlank(Row row) {
		for (int i = 0; i < row.getLastCellNum(); i++) {
			if (hasText(cellString(row, i))) {
				return false;
			}
		}
		return true;
	}

	private String cellString(Row row, int index) {
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

	private Map<String, Object> rowPayload(Object... pairs) {
		Map<String, Object> payload = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			payload.put((String) pairs[i], pairs[i + 1]);
		}
		return payload;
	}

	private List<String> availableActions(String status, AdapterDefinition adapter, CurrentUser currentUser) {
		if (!currentUser.permissions().contains(adapter.requiredPermissionCode())) {
			return List.of();
		}
		if ("READY_TO_COMMIT".equals(status)) {
			List<String> actions = new ArrayList<>();
			if (currentUser.permissions().contains("platform:history-import:confirm")) {
				actions.add("CONFIRM");
			}
			if (currentUser.permissions().contains("platform:history-import:cancel")) {
				actions.add("CANCEL");
			}
			return actions;
		}
		if ("VALIDATION_FAILED".equals(status)) {
			List<String> actions = new ArrayList<>();
			actions.add("ERRORS");
			if (currentUser.permissions().contains("platform:history-import:cancel")) {
				actions.add("CANCEL");
			}
			return actions;
		}
		return List.of();
	}

	private void requirePermission(CurrentUser operator, String permissionCode) {
		if (!operator.permissions().contains(permissionCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (!hasText(idempotencyKey) || idempotencyKey.length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private String actionFingerprint(String action, Long id) {
		return sha256((action + ":" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private String json(Object value) {
		try {
			return this.objectMapper.writeValueAsString(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseMap(String payload) {
		try {
			return this.objectMapper.readValue(payload, Map.class);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private <T> T parse(String payload, Class<T> type) {
		try {
			return this.objectMapper.readValue(payload, type);
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

	private static String text(Map<String, Object> row, String key) {
		Object value = row.get(key);
		return value == null ? null : trimToNull(String.valueOf(value));
	}

	private static Long nullableLong(ResultSet rs, String columnName) throws SQLException {
		long value = rs.getLong(columnName);
		return rs.wasNull() ? null : value;
	}

	private static Boolean booleanText(Map<String, Object> row, String key) {
		String value = text(row, key);
		return hasText(value) ? Boolean.valueOf(value) : Boolean.FALSE;
	}

	private static BigDecimal decimalText(Map<String, Object> row, String key) {
		return decimalText(row, key, null);
	}

	private static BigDecimal decimalText(Map<String, Object> row, String key, BigDecimal fallback) {
		String value = text(row, key);
		return hasText(value) ? new BigDecimal(value) : fallback;
	}

	private static Integer integerText(Map<String, Object> row, String key) {
		String value = text(row, key);
		return hasText(value) ? Integer.valueOf(value) : null;
	}

	private static Long longText(Map<String, Object> row, String key) {
		Object value = row.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		String text = trimToNull(String.valueOf(value));
		return hasText(text) ? Long.valueOf(text) : null;
	}

	private static LocalDate dateText(Map<String, Object> row, String key) {
		String value = text(row, key);
		return hasText(value) ? LocalDate.parse(value) : null;
	}

	private static Set<String> enumNames(Enum<?>[] values) {
		Set<String> names = new HashSet<>();
		for (Enum<?> value : values) {
			names.add(value.name());
		}
		return Set.copyOf(names);
	}

	private static String statusOrEnabled(String status) {
		return hasText(status) ? status.trim().toUpperCase() : "ENABLED";
	}

	private static String historyTaskType(String adapterCode) {
		return adapterCode + "_HISTORY_IMPORT";
	}

	private static String nextTaskNo(String prefix) {
		return prefix + OffsetDateTime.now().format(TASK_NO_FORMATTER)
				+ String.format("%03d", Math.floorMod(TASK_SEQUENCE.incrementAndGet(), 1000));
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

	private static String safeFilename(String filename) {
		return hasText(filename) ? filename.replace("\\", "_").replace("/", "_") : "history-import.xlsx";
	}

	private static String sha256(String content) {
		return sha256(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	public record HistoryImportAdapterRecord(String adapterCode, String name, String targetObjectType,
			String templateCode, int templateVersion, int maxRows, String requiredPermissionCode,
			String description, Long version) {
	}

	public record TemplateFile(String filename, String contentType, byte[] content) {
	}

	public record HistoryImportRecord(Long id, String taskNo, String adapterCode, String targetObjectType,
			int templateVersion, String sourceSha256, String stage, String status, int totalRows, int successRows,
			int failedRows, String errorMessage, String createdByName, OffsetDateTime createdAt,
			OffsetDateTime completedAt, Long version, List<String> availableActions) {
	}

	public record ConfirmHistoryImportRequest(@NotNull Long version, @NotBlank String idempotencyKey) {
	}

	public record CancelHistoryImportRequest(@NotNull Long version, @NotBlank String idempotencyKey) {
	}

	private record AdapterDefinition(String adapterCode, String targetObjectType, String templateCode,
			int templateVersion, int maxRows, String requiredPermissionCode) {
	}

	private record UploadPayload(String adapterCode, int templateVersion, String filename, String sourceSha256) {
	}

	private record ExistingTask(Long id, String requestPayload) {
	}

	private record ActionIdempotency(String requestFingerprint, Long resultId) {
	}

	private record TaskRow(Long id, String taskType, String status, Long version, String requestPayload,
			Long sourceFileId, Long batchId, String adapterCode, String batchSourceSha256,
			String fileSourceSha256) {
	}

	private record ImportRow(int rowNo, Map<String, Object> payload) {
	}

	private record ValidationResult(List<ImportRow> rows, List<ImportError> errors, int totalRows) {
	}

	private record BomMaterialRef(Long id, Long unitId) {
	}

	private record ImportError(Integer rowNo, String columnName, String errorCode, String message) {
	}

}
