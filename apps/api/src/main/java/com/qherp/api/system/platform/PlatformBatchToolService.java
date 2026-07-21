package com.qherp.api.system.platform;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.master.MasterDataAdminService;
import com.qherp.api.system.master.MaterialAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PlatformBatchToolService {

	private static final AtomicInteger OPERATION_SEQUENCE = new AtomicInteger();

	private static final DateTimeFormatter OPERATION_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

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

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final ObjectMapper objectMapper;

	private final PlatformDocumentTaskService documentTaskService;

	private final MasterDataAdminService masterDataAdminService;

	private final MaterialAdminService materialAdminService;

	public PlatformBatchToolService(JdbcTemplate jdbcTemplate, AuditService auditService, ObjectMapper objectMapper,
			PlatformDocumentTaskService documentTaskService, MasterDataAdminService masterDataAdminService,
			MaterialAdminService materialAdminService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
		this.documentTaskService = documentTaskService;
		this.masterDataAdminService = masterDataAdminService;
		this.materialAdminService = materialAdminService;
	}

	@Transactional(readOnly = true)
	public List<BatchToolRecord> tools(CurrentUser currentUser) {
		requirePermission(currentUser, "platform:batch-tool:view");
		return this.jdbcTemplate.query("""
				select tool_code, name, target_object_type, action_code, max_items, required_permission_code,
				       description, version
				from platform_batch_tool_definition
				where status = 'ENABLED'
				order by id
				""", this::mapTool)
			.stream()
			.filter((tool) -> currentUser.permissions().contains(tool.requiredPermissionCode()))
			.toList();
	}

	@Transactional(readOnly = true)
	public PageResponse<BatchOperationRecord> list(String status, int page, int pageSize, CurrentUser currentUser) {
		requirePermission(currentUser, "platform:batch-tool:view");
		List<Object> args = new ArrayList<>();
		String where = "";
		if (hasText(status)) {
			where = "where status = ?";
			args.add(status.trim().toUpperCase());
		}
		List<BatchOperationRecord> visible = this.jdbcTemplate.query("""
				select id, operation_no, tool_code, target_object_type, action_code, status, total_count,
				       blocked_count, success_count, error_count, error_summary, created_by_username,
				       executed_by_username, executed_at, created_at, version
				from platform_batch_operation
				%s
				order by created_at desc, id desc
				""".formatted(where), (rs, rowNum) -> mapOperation(rs, currentUser), args.toArray())
			.stream()
			.filter((operation) -> currentUser.permissions().contains(tool(operation.toolCode()).requiredPermissionCode()))
			.toList();
		int from = Math.min(offset(page, pageSize), visible.size());
		int to = Math.min(from + limit(pageSize), visible.size());
		return PageResponse.of(visible.subList(from, to), page, limit(pageSize), visible.size());
	}

	@Transactional(readOnly = true)
	public BatchOperationRecord get(Long id, CurrentUser currentUser) {
		requirePermission(currentUser, "platform:batch-tool:view");
		BatchOperationRecord record = operation(id, currentUser);
		ToolDefinition tool = tool(record.toolCode());
		requirePermission(currentUser, tool.requiredPermissionCode());
		return record;
	}

	@Transactional
	public BatchOperationRecord preview(String toolCode, BatchPreviewRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (request == null || request.targets() == null || request.targets().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateIdempotencyKey(request.idempotencyKey());
		requirePermission(operator, "platform:batch-tool:preview");
		ToolDefinition tool = tool(toolCode);
		requirePermission(operator, tool.requiredPermissionCode());
		if (!tool.actionCode().equals(request.actionCode().trim().toUpperCase())) {
			throw new BusinessException(ApiErrorCode.BATCH_TOOL_NOT_SUPPORTED);
		}
		if (request.targets().size() > tool.maxItems()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String targetStatus = null;
		String objectType = null;
		String templateCode = null;
		Integer templateVersion = null;
		if ("STATUS_CHANGE".equals(tool.actionCode())) {
			targetStatus = status(request.targetStatus());
		}
		else if ("BATCH_PRINT".equals(tool.actionCode())) {
			FixedPrintDefinition definition = fixedPrintDefinition(request.objectType());
			templateCode = requiredText(request.templateCode());
			if (!definition.templateCode().equals(templateCode)) {
				throw new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED);
			}
			objectType = definition.objectType();
			templateVersion = printTemplateVersion(templateCode, definition);
		}
		else {
			throw new BusinessException(ApiErrorCode.BATCH_TOOL_NOT_SUPPORTED);
		}
		BatchPayload payload = new BatchPayload(tool.toolCode(), tool.actionCode(), targetStatus, objectType,
				templateCode, templateVersion, request.reason(), request.targets());
		String payloadJson = json(payload);
		List<ExistingOperation> existing = existingOperation(operator.id(), tool.toolCode(), request.idempotencyKey());
		if (!existing.isEmpty()) {
			ExistingOperation row = existing.getFirst();
			if (!jsonEquivalent(payloadJson, row.requestPayload())) {
				throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
			}
			return get(row.id(), operator);
		}
		List<PrecheckItem> items = "BATCH_PRINT".equals(tool.actionCode())
				? precheckFixedPrint(payload, false, operator) : precheck(tool, request.targets(), targetStatus, false);
		int blockedCount = (int) items.stream().filter((item) -> !"READY".equals(item.status())).count();
		String status = blockedCount == 0 ? "PRECHECKED" : "PRECHECK_FAILED";
		try {
			Long operationId = this.jdbcTemplate.queryForObject("""
					insert into platform_batch_operation (
						operation_no, tool_code, target_object_type, action_code, status, request_payload,
						request_fingerprint, idempotency_key, total_count, blocked_count, error_count,
						created_by_user_id, created_by_username, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, now(), now())
					returning id
					""", Long.class, nextOperationNo(), tool.toolCode(), tool.targetObjectType(),
					tool.actionCode(), status, payloadJson, sha256(payloadJson), request.idempotencyKey().trim(),
					items.size(), blockedCount, blockedCount, operator.id(), operator.username());
			for (int i = 0; i < items.size(); i++) {
				PrecheckItem item = items.get(i);
				Long itemId = insertOperationItem(operationId, i + 1, tool.targetObjectType(), item);
				if (!"READY".equals(item.status())) {
					insertOperationError(operationId, itemId, i + 1, item.errorCode(), item.message());
				}
			}
			this.auditService.recordDetail(operator, "BATCH_OPERATION_PREVIEW", "BATCH_OPERATION", operationId,
					tool.toolCode(), json(Map.of("toolCode", tool.toolCode(), "totalRows", items.size(),
							"blockedRows", blockedCount)),
					servletRequest);
			return get(operationId, operator);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.DOCUMENT_TASK_IDEMPOTENCY_CONFLICT);
		}
	}

	@Transactional
	public BatchOperationRecord execute(Long id, BatchExecuteRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (request == null || request.version() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateIdempotencyKey(request.idempotencyKey());
		requirePermission(operator, "platform:batch-tool:execute");
		String fingerprint = actionFingerprint("BATCH_OPERATION_EXECUTE", id);
		BatchOperationRecord existing = existingActionResult(operator, "BATCH_OPERATION_EXECUTE", "BATCH_OPERATION",
				id, request.idempotencyKey(), fingerprint);
		if (existing != null) {
			return existing;
		}
		OperationRow operation = lockOperation(id);
		ToolDefinition tool = tool(operation.toolCode());
		requirePermission(operator, tool.requiredPermissionCode());
		if (!request.version().equals(operation.version())) {
			throw new BusinessException(ApiErrorCode.BATCH_OPERATION_OBJECT_CHANGED);
		}
		if (!"PRECHECKED".equals(operation.status())) {
			throw new BusinessException(ApiErrorCode.BATCH_OPERATION_STATUS_INVALID);
		}
		BatchPayload payload = parse(operation.requestPayload(), BatchPayload.class);
		List<PrecheckItem> items = "BATCH_PRINT".equals(tool.actionCode())
				? precheckFixedPrint(payload, true, operator) : precheck(tool, payload.targets(), payload.targetStatus(),
						true);
		List<PrecheckItem> blocked = items.stream().filter((item) -> !"READY".equals(item.status())).toList();
		if (!blocked.isEmpty()) {
			throw new BusinessException(ApiErrorCode.BATCH_OPERATION_OBJECT_CHANGED);
		}
		this.jdbcTemplate.update("""
				update platform_batch_operation
				set status = 'EXECUTING', updated_at = now(), version = version + 1
				where id = ?
				""", id);
		Long firstDocumentTaskId = null;
		if ("BATCH_PRINT".equals(tool.actionCode())) {
			firstDocumentTaskId = createBatchPrintTasks(id, payload, items, operator, servletRequest);
		}
		else {
			for (PrecheckItem item : items) {
				updateTargetStatus(tool, item.targetObjectId(), payload.targetStatus(), operator);
			}
		}
		this.jdbcTemplate.update("""
				update platform_batch_operation_item
				set status = 'SUCCEEDED', message = '执行成功', updated_at = now()
				where operation_id = ?
				""", id);
		this.jdbcTemplate.update("""
				update platform_batch_operation
				set status = 'SUCCEEDED', success_count = total_count, error_count = 0, blocked_count = 0,
				    document_task_id = coalesce(?, document_task_id),
				    executed_by_username = ?, executed_at = now(), updated_at = now(), version = version + 1
				where id = ?
				""", firstDocumentTaskId, operator.username(), id);
		recordActionIdempotency(operator, "BATCH_OPERATION_EXECUTE", "BATCH_OPERATION", id,
				request.idempotencyKey(), fingerprint);
		Map<String, Object> auditDetail = new LinkedHashMap<>();
		auditDetail.put("toolCode", tool.toolCode());
		auditDetail.put("totalRows", items.size());
		if ("BATCH_PRINT".equals(tool.actionCode())) {
			auditDetail.put("objectType", payload.objectType());
			auditDetail.put("templateCode", payload.templateCode());
			auditDetail.put("templateVersion", payload.templateVersion());
			auditDetail.put("documentTaskId", firstDocumentTaskId);
		}
		else {
			auditDetail.put("targetStatus", payload.targetStatus());
		}
		this.auditService.recordDetail(operator, "BATCH_OPERATION_EXECUTE", "BATCH_OPERATION", id,
				operation.operationNo(), json(auditDetail), servletRequest);
		return get(id, operator);
	}

	private Long createBatchPrintTasks(Long operationId, BatchPayload payload, List<PrecheckItem> items,
			CurrentUser operator, HttpServletRequest servletRequest) {
		Long firstDocumentTaskId = null;
		for (int i = 0; i < items.size(); i++) {
			PrecheckItem item = items.get(i);
			PlatformDocumentTaskService.DocumentTaskRecord task = this.documentTaskService.createPrintTask(
					new PlatformDocumentTaskService.PrintTaskRequest(null, payload.objectType(),
							item.targetObjectId(), payload.templateCode()),
					"batch-print-" + operationId + "-" + (i + 1), operator, servletRequest);
			if (firstDocumentTaskId == null) {
				firstDocumentTaskId = task.id();
			}
		}
		return firstDocumentTaskId;
	}

	private List<PrecheckItem> precheck(ToolDefinition tool, List<BatchTargetRequest> targets, String targetStatus,
			boolean lock) {
		List<PrecheckItem> items = new ArrayList<>();
		for (BatchTargetRequest target : targets) {
			if (target.targetObjectId() == null || target.version() == null) {
				items.add(new PrecheckItem(null, null, null, null, "BLOCKED",
						ApiErrorCode.VALIDATION_ERROR.name(), "目标和版本必填"));
				continue;
			}
			TargetSnapshot snapshot = targetSnapshot(tool, target.targetObjectId(), lock);
			if (snapshot == null) {
				items.add(new PrecheckItem(target.targetObjectId(), null, null, null, "BLOCKED",
						ApiErrorCode.MASTER_DATA_NOT_FOUND.name(), "目标不存在"));
				continue;
			}
			if (!target.version().equals(snapshot.version())) {
				items.add(new PrecheckItem(snapshot.id(), snapshot.code(), snapshot.name(), snapshot.version(),
						"BLOCKED", ApiErrorCode.BATCH_OPERATION_OBJECT_CHANGED.name(), "目标版本已变化"));
				continue;
			}
			if (targetStatus.equals(snapshot.status())) {
				items.add(new PrecheckItem(snapshot.id(), snapshot.code(), snapshot.name(), snapshot.version(),
						"BLOCKED", ApiErrorCode.BATCH_OPERATION_PRECHECK_FAILED.name(), "目标状态无需变更"));
				continue;
			}
			if ("DISABLED".equals(targetStatus) && hasOpenBusinessReferences(tool.toolCode(), snapshot.id())) {
				items.add(new PrecheckItem(snapshot.id(), snapshot.code(), snapshot.name(), snapshot.version(),
						"BLOCKED", ApiErrorCode.BATCH_OPERATION_PRECHECK_FAILED.name(), "目标存在开放业务引用"));
				continue;
			}
			items.add(new PrecheckItem(snapshot.id(), snapshot.code(), snapshot.name(), snapshot.version(), "READY",
					null, "预检通过"));
		}
		return items;
	}

	private List<PrecheckItem> precheckFixedPrint(BatchPayload payload, boolean lock, CurrentUser operator) {
		FixedPrintDefinition definition = fixedPrintDefinition(payload.objectType());
		if (!definition.templateCode().equals(payload.templateCode())) {
			throw new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED);
		}
		requirePermission(operator, "platform:print:generate");
		requirePermission(operator, definition.viewPermission());
		int currentTemplateVersion = printTemplateVersion(payload.templateCode(), definition);
		if (!Objects.equals(payload.templateVersion(), currentTemplateVersion)) {
			throw new BusinessException(ApiErrorCode.BATCH_OPERATION_OBJECT_CHANGED);
		}
		List<PrecheckItem> items = new ArrayList<>();
		for (BatchTargetRequest target : payload.targets()) {
			if (target.targetObjectId() == null || target.version() == null) {
				items.add(new PrecheckItem(null, null, null, null, "BLOCKED",
						ApiErrorCode.VALIDATION_ERROR.name(), "目标和版本必填"));
				continue;
			}
			FixedPrintSnapshot snapshot = fixedPrintSnapshot(definition, target.targetObjectId(), lock);
			if (snapshot == null) {
				items.add(new PrecheckItem(target.targetObjectId(), null, null, null, "BLOCKED",
						ApiErrorCode.DOCUMENT_TASK_STATUS_INVALID.name(), "目标不存在"));
				continue;
			}
			if (!target.version().equals(snapshot.version())) {
				items.add(new PrecheckItem(snapshot.id(), snapshot.objectNo(), definition.templateName(),
						snapshot.version(), "BLOCKED", ApiErrorCode.BATCH_OPERATION_OBJECT_CHANGED.name(),
						"目标版本已变化"));
				continue;
			}
			items.add(new PrecheckItem(snapshot.id(), snapshot.objectNo(), definition.templateName(),
					snapshot.version(), "READY", null, "预检通过"));
		}
		return items;
	}

	private TargetSnapshot targetSnapshot(ToolDefinition tool, Long targetId, boolean lock) {
		String table = statusTable(tool.toolCode());
		String sql = """
				select id, code, name, status, version
				from %s
				where id = ?
				%s
				""".formatted(table, lock ? "for update" : "");
		return this.jdbcTemplate.query(sql, (rs, rowNum) -> new TargetSnapshot(rs.getLong("id"),
				rs.getString("code"), rs.getString("name"), rs.getString("status"), rs.getLong("version")),
				targetId).stream().findFirst().orElse(null);
	}

	private void updateTargetStatus(ToolDefinition tool, Long targetId, String targetStatus, CurrentUser operator) {
		Long version = this.jdbcTemplate.queryForObject("select version from " + statusTable(tool.toolCode())
				+ " where id = ?", Long.class, targetId);
		if ("CUSTOMER_STATUS_CHANGE_V1".equals(tool.toolCode())) {
			MasterDataAdminService.Resource resource = MasterDataAdminService.Resource.CUSTOMER;
			if ("ENABLED".equals(targetStatus)) {
				this.masterDataAdminService.enablePartner(resource, targetId, operator, null);
			}
			else {
				this.masterDataAdminService.disablePartner(resource, targetId, operator, null);
			}
			return;
		}
		if ("SUPPLIER_STATUS_CHANGE_V1".equals(tool.toolCode())) {
			MasterDataAdminService.Resource resource = MasterDataAdminService.Resource.SUPPLIER;
			if ("ENABLED".equals(targetStatus)) {
				this.masterDataAdminService.enablePartner(resource, targetId, operator, null);
			}
			else {
				this.masterDataAdminService.disablePartner(resource, targetId, operator, null);
			}
			return;
		}
		if ("MATERIAL_STATUS_CHANGE_V1".equals(tool.toolCode())) {
			MaterialAdminService.VersionRequest request = new MaterialAdminService.VersionRequest(version);
			if ("ENABLED".equals(targetStatus)) {
				this.materialAdminService.enable(targetId, request, operator, null);
			}
			else {
				this.materialAdminService.disable(targetId, request, operator, null);
			}
			return;
		}
		throw new BusinessException(ApiErrorCode.BATCH_TOOL_NOT_SUPPORTED);
	}

	private boolean hasOpenBusinessReferences(String toolCode, Long targetId) {
		Long count = switch (toolCode) {
			case "CUSTOMER_STATUS_CHANGE_V1" -> countOpenReferences("""
					select count(*)
					from sal_sales_order
					where customer_id = ?
					  and status not in ('CANCELLED', 'CLOSED')
					""", targetId);
			case "SUPPLIER_STATUS_CHANGE_V1" -> countOpenReferences("""
					select count(*)
					from proc_purchase_order
					where supplier_id = ?
					  and status not in ('CANCELLED', 'CLOSED')
					""", targetId);
			case "MATERIAL_STATUS_CHANGE_V1" -> countOpenReferences("""
					select count(*)
					from sal_sales_order_line l
					join sal_sales_order o on o.id = l.order_id
					where l.material_id = ?
					  and o.status not in ('CANCELLED', 'CLOSED')
					""", targetId) + countOpenReferences("""
					select count(*)
					from proc_purchase_order_line l
					join proc_purchase_order o on o.id = l.order_id
					where l.material_id = ?
					  and o.status not in ('CANCELLED', 'CLOSED')
					""", targetId) + countOpenReferences("""
					select count(*)
					from mfg_bom_item i
					join mfg_bom b on b.id = i.bom_id
					where i.child_material_id = ?
					  and b.status in ('DRAFT', 'ENABLED')
					""", targetId);
			default -> 0L;
		};
		return count != null && count > 0;
	}

	private Long countOpenReferences(String sql, Long targetId) {
		return this.jdbcTemplate.queryForObject(sql, Long.class, targetId);
	}

	private FixedPrintSnapshot fixedPrintSnapshot(FixedPrintDefinition definition, Long objectId, boolean lock) {
		String sql = """
				select id, %s as object_no, status, version
				from %s
				where id = ?
				%s
				""".formatted(definition.objectNoExpression(), definition.tableName(), lock ? "for update" : "");
		return this.jdbcTemplate.query(sql, (rs, rowNum) -> new FixedPrintSnapshot(rs.getLong("id"),
				rs.getString("object_no"), rs.getString("status"), rs.getLong("version")), objectId)
			.stream()
			.findFirst()
			.filter((snapshot) -> fixedPrintAllowedStatuses(definition.objectType()).contains(snapshot.status()))
			.orElse(null);
	}

	private List<String> fixedPrintAllowedStatuses(String objectType) {
		return switch (objectType) {
			case "SALES_ORDER" -> List.of("CONFIRMED", "PARTIALLY_SHIPPED", "SHIPPED", "CLOSED");
			case "SALES_SHIPMENT" -> List.of("POSTED", "CANCELLED");
			case "PROCUREMENT_RECEIPT" -> List.of("POSTED", "REVERSED");
			case "INVENTORY_TRANSFER" -> List.of("POSTED", "CANCELLED", "REVERSED");
			case "PRODUCTION_MATERIAL_ISSUE", "PRODUCTION_COMPLETION_RECEIPT" -> List.of("POSTED");
			case "PRODUCTION_WORK_ORDER" -> List.of("RELEASED", "IN_PROGRESS", "COMPLETED", "CLOSED");
			case "SALES_INVOICE", "PURCHASE_INVOICE" -> List.of("CONFIRMED", "POSTED", "CLOSED");
			case "ACCOUNTING_VOUCHER" -> List.of("DRAFT", "POSTED");
			default -> List.of();
		};
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

	private int printTemplateVersion(String templateCode, FixedPrintDefinition definition) {
		return this.jdbcTemplate.query("""
				select template_version
				from platform_print_template
				where template_code = ?
				  and scene_code = ?
				  and object_type = ?
				  and status = 'ENABLED'
				""", (rs, rowNum) -> rs.getInt("template_version"), templateCode, definition.sceneCode(),
				definition.objectType()).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PRINT_TEMPLATE_NOT_SUPPORTED));
	}

	private String statusTable(String toolCode) {
		return switch (toolCode) {
			case "CUSTOMER_STATUS_CHANGE_V1" -> "mst_customer";
			case "SUPPLIER_STATUS_CHANGE_V1" -> "mst_supplier";
			case "MATERIAL_STATUS_CHANGE_V1" -> "mst_material";
			default -> throw new BusinessException(ApiErrorCode.BATCH_TOOL_NOT_SUPPORTED);
		};
	}

	private ToolDefinition tool(String toolCode) {
		if (!hasText(toolCode)) {
			throw new BusinessException(ApiErrorCode.BATCH_TOOL_NOT_SUPPORTED);
		}
		return this.jdbcTemplate.query("""
				select tool_code, target_object_type, action_code, max_items, required_permission_code
				from platform_batch_tool_definition
				where tool_code = ?
				  and status = 'ENABLED'
				""", (rs, rowNum) -> new ToolDefinition(rs.getString("tool_code"),
				rs.getString("target_object_type"), rs.getString("action_code"), rs.getInt("max_items"),
				rs.getString("required_permission_code")), toolCode.trim().toUpperCase()).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.BATCH_TOOL_NOT_SUPPORTED));
	}

	private BatchToolRecord mapTool(ResultSet rs, int rowNum) throws SQLException {
		return new BatchToolRecord(rs.getString("tool_code"), rs.getString("name"),
				rs.getString("target_object_type"), rs.getString("action_code"), rs.getInt("max_items"),
				rs.getString("required_permission_code"), rs.getString("description"), rs.getLong("version"));
	}

	private BatchOperationRecord mapOperation(ResultSet rs, CurrentUser currentUser) throws SQLException {
		Long id = rs.getLong("id");
		ToolDefinition tool = tool(rs.getString("tool_code"));
		return new BatchOperationRecord(id, rs.getString("operation_no"), rs.getString("tool_code"),
				rs.getString("target_object_type"), rs.getString("action_code"), rs.getString("status"),
				rs.getInt("total_count"), rs.getInt("success_count"), rs.getInt("error_count"),
				rs.getString("error_summary"), rs.getString("created_by_username"),
				rs.getString("executed_by_username"), rs.getObject("executed_at", OffsetDateTime.class),
				rs.getObject("created_at", OffsetDateTime.class), rs.getLong("version"),
				operationAvailableActions(rs.getString("status"), tool, currentUser), operationItems(id));
	}

	private BatchOperationRecord operation(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select id, operation_no, tool_code, target_object_type, action_code, status, total_count,
				       blocked_count, success_count, error_count, error_summary, created_by_username,
				       executed_by_username, executed_at, created_at, version
				from platform_batch_operation
				where id = ?
				""", (rs, rowNum) -> mapOperation(rs, currentUser), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.BATCH_OPERATION_STATUS_INVALID));
	}

	private OperationRow lockOperation(Long id) {
		return this.jdbcTemplate.query("""
				select id, operation_no, tool_code, status, request_payload::text as request_payload, version
				from platform_batch_operation
				where id = ?
				for update
				""", (rs, rowNum) -> new OperationRow(rs.getLong("id"), rs.getString("operation_no"),
				rs.getString("tool_code"), rs.getString("status"), rs.getString("request_payload"),
				rs.getLong("version")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.BATCH_OPERATION_STATUS_INVALID));
	}

	private List<BatchOperationItemRecord> operationItems(Long operationId) {
		return this.jdbcTemplate.query("""
				select i.line_no, i.target_object_type, i.target_object_id, i.target_object_no,
				       i.target_object_summary, i.target_object_version, i.status, e.error_code, i.message
				from platform_batch_operation_item i
				left join platform_batch_operation_error e on e.item_id = i.id
				where i.operation_id = ?
				order by i.line_no
				""", (rs, rowNum) -> new BatchOperationItemRecord(rs.getInt("line_no"),
				rs.getString("target_object_type"), rs.getLong("target_object_id"),
				rs.getString("target_object_no"), rs.getString("target_object_summary"),
				nullableLong(rs, "target_object_version"), rs.getString("status"), rs.getString("error_code"),
				rs.getString("message")),
				operationId);
	}

	private Long insertOperationItem(Long operationId, int lineNo, String targetObjectType, PrecheckItem item) {
		return this.jdbcTemplate.queryForObject("""
				insert into platform_batch_operation_item (
					operation_id, line_no, target_object_type, target_object_id, target_object_no,
					target_object_summary, target_object_version, status, message
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, operationId, lineNo, targetObjectType, item.targetObjectId(), item.targetObjectNo(),
				item.targetObjectSummary(), item.targetObjectVersion(), item.status(), item.message());
	}

	private void insertOperationError(Long operationId, Long itemId, int lineNo, String errorCode, String message) {
		this.jdbcTemplate.update("""
				insert into platform_batch_operation_error (operation_id, item_id, line_no, error_code, message)
				values (?, ?, ?, ?, ?)
				""", operationId, itemId, lineNo, errorCode, message);
	}

	private BatchOperationRecord existingActionResult(CurrentUser operator, String action, String targetType,
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

	private List<ExistingOperation> existingOperation(Long userId, String toolCode, String idempotencyKey) {
		return this.jdbcTemplate.query("""
				select id, request_payload::text as request_payload
				from platform_batch_operation
				where created_by_user_id = ?
				  and tool_code = ?
				  and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingOperation(rs.getLong("id"), rs.getString("request_payload")),
				userId, toolCode, idempotencyKey.trim());
	}

	private String status(String value) {
		if (!hasText(value)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String status = value.trim().toUpperCase();
		if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return status;
	}

	private String requiredText(String value) {
		if (!hasText(value)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value.trim().toUpperCase();
	}

	private List<String> operationAvailableActions(String status, ToolDefinition tool, CurrentUser currentUser) {
		if ("PRECHECKED".equals(status) && currentUser.permissions().contains("platform:batch-tool:execute")
				&& currentUser.permissions().contains(tool.requiredPermissionCode())) {
			return List.of("EXECUTE");
		}
		if ("PRECHECK_FAILED".equals(status) || "FAILED".equals(status)) {
			return List.of("ERRORS");
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
		return sha256(action + ":" + id);
	}

	private String nextOperationNo() {
		return "BOP" + OffsetDateTime.now().format(OPERATION_NO_FORMATTER)
				+ String.format("%03d", Math.floorMod(OPERATION_SEQUENCE.incrementAndGet(), 1000));
	}

	private String json(Object value) {
		try {
			return this.objectMapper.writeValueAsString(value);
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

	private static String sha256(String content) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
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

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record BatchToolRecord(String toolCode, String name, String targetObjectType, String actionCode,
			int maxItems, String requiredPermissionCode, String description, Long version) {
	}

	public record BatchPreviewRequest(@NotBlank String actionCode, String targetStatus, String objectType,
			String templateCode, String reason, @NotEmpty List<@Valid BatchTargetRequest> targets,
			@NotBlank String idempotencyKey) {
	}

	public record BatchTargetRequest(@NotNull Long targetObjectId, @NotNull Long version) {
	}

	public record BatchExecuteRequest(@NotNull Long version, @NotBlank String idempotencyKey) {
	}

	public record BatchOperationRecord(Long id, String operationNo, String toolCode, String targetObjectType,
			String actionCode, String status, int totalRows, int successRows, int failedRows, String errorMessage,
			String createdByName, String executedByName, OffsetDateTime executedAt, OffsetDateTime createdAt,
			Long version, List<String> availableActions, List<BatchOperationItemRecord> items) {
	}

	public record BatchOperationItemRecord(int lineNo, String targetObjectType, Long targetObjectId,
			String targetObjectNo, String targetObjectSummary, Long targetObjectVersion, String status,
			String errorCode, String message) {
	}

	private record ToolDefinition(String toolCode, String targetObjectType, String actionCode, int maxItems,
			String requiredPermissionCode) {
	}

	private record BatchPayload(String toolCode, String actionCode, String targetStatus, String reason,
			String objectType, String templateCode, Integer templateVersion, List<BatchTargetRequest> targets) {
		private BatchPayload(String toolCode, String actionCode, String targetStatus, String objectType,
				String templateCode, Integer templateVersion, String reason, List<BatchTargetRequest> targets) {
			this(toolCode, actionCode, targetStatus, reason, objectType, templateCode, templateVersion, targets);
		}
	}

	private record ExistingOperation(Long id, String requestPayload) {
	}

	private record ActionIdempotency(String requestFingerprint, Long resultId) {
	}

	private record OperationRow(Long id, String operationNo, String toolCode, String status, String requestPayload,
			Long version) {
	}

	private record TargetSnapshot(Long id, String code, String name, String status, Long version) {
	}

	private record FixedPrintDefinition(String objectType, String templateCode, String sceneCode, String templateName,
			String tableName, String objectNoExpression, String viewPermission) {
	}

	private record FixedPrintSnapshot(Long id, String objectNo, String status, Long version) {
	}

	private record PrecheckItem(Long targetObjectId, String targetObjectNo, String targetObjectSummary,
			Long targetObjectVersion, String status, String errorCode, String message) {
	}

}
