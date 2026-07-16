package com.qherp.api.system.platform;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PlatformAttachmentService {

	private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;

	private static final int MAX_ACTIVE_ATTACHMENTS_PER_OBJECT = 20;

	private static final List<String> ALLOWED_EXTENSIONS = List.of("txt", "csv", "pdf", "png", "jpg", "jpeg",
			"docx", "xlsx");

	private final JdbcTemplate jdbcTemplate;

	private final PlatformStorageService storageService;

	private final AuditService auditService;

	public PlatformAttachmentService(JdbcTemplate jdbcTemplate, PlatformStorageService storageService,
			AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.storageService = storageService;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public PageResponse<AttachmentRecord> list(String objectType, Long objectId, int page, int pageSize,
			CurrentUser currentUser) {
		requireBusinessPermission(objectType, objectId, currentUser, AttachmentAccessMode.VIEW);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_business_attachment a
				where a.object_type = ?
				and a.object_id = ?
				and a.status = 'AVAILABLE'
				""", Long.class, objectType, objectId);
		List<AttachmentRecord> items = this.jdbcTemplate.query("""
				select a.id, a.object_type, a.object_id, f.original_filename, f.content_type, f.size_bytes,
				       f.sha256, a.description, a.status, a.created_by_username, a.created_at, a.version
				from platform_business_attachment a
				join platform_file_object f on f.id = a.file_id
				where a.object_type = ?
				and a.object_id = ?
				and a.status = 'AVAILABLE'
				order by a.created_at desc, a.id desc
				limit ? offset ?
				""", this::mapRecord, objectType, objectId, limit(pageSize), offset(page, pageSize));
		return PageResponse.of(items.stream()
			.map((item) -> item.withAvailableActions(attachmentAvailableActions(item, currentUser)))
			.toList(), page, limit(pageSize), total);
	}

	@Transactional
	public AttachmentRecord upload(AttachmentUpload upload, CurrentUser operator, HttpServletRequest servletRequest) {
		validateUpload(upload);
		requireBusinessPermission(upload.objectType(), upload.objectId(), operator, AttachmentAccessMode.MANAGE);
		requireApprovalUnlocked(upload.objectType(), upload.objectId());
		String actualContentType = actualContentType(upload.originalFilename(), upload.content());
		String sha256 = sha256(upload.content());
		List<AttachmentRecord> duplicates = this.jdbcTemplate.query("""
				select a.id, a.object_type, a.object_id, f.original_filename, f.content_type, f.size_bytes,
				       f.sha256, a.description, a.status, a.created_by_username, a.created_at, a.version
				from platform_business_attachment a
				join platform_file_object f on f.id = a.file_id
				where a.object_type = ?
				and a.object_id = ?
				and a.status = 'AVAILABLE'
				and f.status = 'AVAILABLE'
				and f.sha256 = ?
				order by a.id
				limit 1
				""", this::mapRecord, upload.objectType(), upload.objectId(), sha256);
		if (!duplicates.isEmpty()) {
			AttachmentRecord duplicate = duplicates.getFirst();
			return duplicate.withAvailableActions(attachmentAvailableActions(duplicate, operator));
		}
		Long activeCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_business_attachment
				where object_type = ?
				and object_id = ?
				and status = 'AVAILABLE'
				""", Long.class, upload.objectType(), upload.objectId());
		if (activeCount != null && activeCount >= MAX_ACTIVE_ATTACHMENTS_PER_OBJECT) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String objectKey = "attachments/" + UUID.randomUUID();
		PlatformStorageService.StoredObject storedObject = this.storageService.put(objectKey, upload.content(),
				actualContentType);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long fileId = this.jdbcTemplate.queryForObject("""
					insert into platform_file_object (
						bucket, object_key, original_filename, content_type, size_bytes, sha256, etag,
						file_usage, status, created_by_user_id, created_by_username, created_at
					)
					values (?, ?, ?, ?, ?, ?, ?, 'ATTACHMENT', 'AVAILABLE', ?, ?, ?)
					returning id
					""", Long.class, storedObject.bucket(), storedObject.objectKey(), upload.originalFilename(),
					actualContentType, upload.content().length, sha256, storedObject.eTag(), operator.id(),
					operator.username(), now);
			Long attachmentId = this.jdbcTemplate.queryForObject("""
					insert into platform_business_attachment (
						object_type, object_id, file_id, description, status, created_by_user_id,
						created_by_username, created_at
					)
					values (?, ?, ?, ?, 'AVAILABLE', ?, ?, ?)
					returning id
					""", Long.class, upload.objectType(), upload.objectId(), fileId, trimToNull(upload.description()),
					operator.id(), operator.username(), now);
			this.auditService.record(operator, "ATTACHMENT_UPLOAD", upload.objectType(), upload.objectId(),
					upload.originalFilename(), servletRequest);
			return getRecord(attachmentId, operator);
		}
		catch (RuntimeException exception) {
			this.storageService.deleteQuietly(objectKey);
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public DownloadedFile download(Long attachmentId, CurrentUser operator) {
		AttachmentFile file = attachmentFile(attachmentId);
		requireBusinessPermission(file.objectType(), file.objectId(), operator, AttachmentAccessMode.VIEW);
		byte[] content = this.storageService.get(file.objectKey());
		return new DownloadedFile(file.originalFilename(), file.contentType(), content);
	}

	@Transactional
	public AttachmentRecord delete(Long attachmentId, DeleteAttachmentRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		AttachmentFile file = attachmentFile(attachmentId);
		if (request == null || request.version() == null || !file.version().equals(request.version())) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
		requireBusinessPermission(file.objectType(), file.objectId(), operator, AttachmentAccessMode.MANAGE);
		requireApprovalUnlocked(file.objectType(), file.objectId());
		this.storageService.delete(file.objectKey());
		OffsetDateTime now = OffsetDateTime.now();
		int updated = this.jdbcTemplate.update("""
				update platform_business_attachment
				set status = 'DELETED', deleted_by_username = ?, deleted_at = ?, version = version + 1
				where id = ?
				and version = ?
				and status = 'AVAILABLE'
				""", operator.username(), now, attachmentId, request.version());
		if (updated == 0) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
		this.jdbcTemplate.update("""
				update platform_file_object
				set status = 'DELETED', deleted_by_username = ?, deleted_at = ?, version = version + 1
				where id = ?
				and status = 'AVAILABLE'
				""", operator.username(), now, file.fileId());
		this.auditService.record(operator, "ATTACHMENT_DELETE", file.objectType(), file.objectId(),
				trimToNull(request.reason()), servletRequest);
		return getRecord(attachmentId, operator);
	}

	private AttachmentRecord getRecord(Long id, CurrentUser currentUser) {
		AttachmentRecord record = this.jdbcTemplate.query("""
				select a.id, a.object_type, a.object_id, f.original_filename, f.content_type, f.size_bytes,
				       f.sha256, a.description, a.status, a.created_by_username, a.created_at, a.version
				from platform_business_attachment a
				join platform_file_object f on f.id = a.file_id
				where a.id = ?
				""", this::mapRecord, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.ATTACHMENT_NOT_FOUND));
		return record.withAvailableActions(attachmentAvailableActions(record, currentUser));
	}

	private AttachmentFile attachmentFile(Long id) {
		return this.jdbcTemplate.query("""
				select a.id, a.object_type, a.object_id, a.file_id, f.object_key, f.original_filename,
				       f.content_type, a.version
				from platform_business_attachment a
				join platform_file_object f on f.id = a.file_id
				where a.id = ?
				and a.status = 'AVAILABLE'
				and f.status = 'AVAILABLE'
				""", (rs, rowNum) -> new AttachmentFile(rs.getLong("id"), rs.getString("object_type"),
				rs.getLong("object_id"), rs.getLong("file_id"), rs.getString("object_key"),
				rs.getString("original_filename"), rs.getString("content_type"), rs.getLong("version")), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.ATTACHMENT_NOT_FOUND));
	}

	private AttachmentRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
		return new AttachmentRecord(rs.getLong("id"), rs.getString("object_type"), rs.getLong("object_id"),
				rs.getString("original_filename"), rs.getString("content_type"), rs.getLong("size_bytes"),
				rs.getString("sha256"), rs.getString("description"), rs.getString("status"),
				rs.getString("created_by_username"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getLong("version"), List.of());
	}

	private void validateUpload(AttachmentUpload upload) {
		if (upload == null || !hasText(upload.objectType()) || upload.objectId() == null
				|| !hasText(upload.originalFilename()) || upload.content() == null || upload.content().length == 0) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (upload.content().length > MAX_FILE_SIZE) {
			throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_SIZE_EXCEEDED);
		}
		if (!"SALES_PROJECT_CONTRACT".equals(upload.objectType())
				&& !"BOM_ENGINEERING_CHANGE".equals(upload.objectType())
				&& !"INVENTORY_OWNERSHIP_CONVERSION".equals(upload.objectType())
				&& !"INVENTORY_STOCKTAKE".equals(upload.objectType())
				&& !"INVENTORY_VALUATION_ADJUSTMENT".equals(upload.objectType())
				&& !"SALES_QUOTE".equals(upload.objectType())
				&& !"SALES_ORDER_CHANGE".equals(upload.objectType())
				&& !"SALES_PROJECT".equals(upload.objectType())) {
			throw new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED);
		}
		String extension = extension(upload.originalFilename());
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
		}
		if (hasDangerousDoubleExtension(upload.originalFilename())) {
			throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
		}
	}

	private String actualContentType(String filename, byte[] content) {
		String extension = extension(filename);
		return switch (extension) {
			case "pdf" -> {
				requirePrefix(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
				yield "application/pdf";
			}
			case "png" -> {
				requirePrefix(content, new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a });
				yield "image/png";
			}
			case "jpg", "jpeg" -> {
				if (content.length < 3 || content[0] != (byte) 0xff || content[1] != (byte) 0xd8
						|| content[2] != (byte) 0xff) {
					throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
				}
				yield "image/jpeg";
			}
			case "txt" -> {
				requireUtf8Text(content);
				yield "text/plain; charset=utf-8";
			}
			case "csv" -> {
				requireUtf8Text(content);
				yield "text/csv; charset=utf-8";
			}
			case "docx" -> {
				requireOfficeDocument(content, "word/");
				yield "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			}
			case "xlsx" -> {
				requireOfficeDocument(content, "xl/");
				yield "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
			}
			default -> throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
		};
	}

	private void requirePrefix(byte[] content, byte[] prefix) {
		if (content.length < prefix.length) {
			throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
		}
		for (int i = 0; i < prefix.length; i++) {
			if (content[i] != prefix[i]) {
				throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
			}
		}
	}

	private void requireUtf8Text(byte[] content) {
		for (byte value : content) {
			if (value == 0) {
				throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
			}
		}
		try {
			StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(content));
		}
		catch (CharacterCodingException exception) {
			throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
		}
	}

	private void requireOfficeDocument(byte[] content, String requiredFolder) {
		boolean hasContentTypes = false;
		boolean hasRequiredFolder = false;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				String lower = name.toLowerCase(Locale.ROOT);
				if ("[content_types].xml".equals(lower)) {
					hasContentTypes = true;
				}
				if (lower.startsWith(requiredFolder)) {
					hasRequiredFolder = true;
				}
				if (lower.endsWith("vbaProject.bin".toLowerCase(Locale.ROOT)) || lower.endsWith(".bin")
						&& lower.contains("vba") || lower.contains("externalLinks/".toLowerCase(Locale.ROOT))) {
					throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
				}
			}
		}
		catch (IOException exception) {
			throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
		}
		if (!hasContentTypes || !hasRequiredFolder) {
			throw new BusinessException(ApiErrorCode.ATTACHMENT_FILE_TYPE_INVALID);
		}
	}

	private void requireApprovalUnlocked(String objectType, Long objectId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_instance
				where business_object_type = ?
				and business_object_id = ?
				and status = 'SUBMITTED'
				""", Long.class, objectType, objectId);
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.ATTACHMENT_ACCESS_FORBIDDEN);
		}
	}

	private boolean approvalOpen(String objectType, Long objectId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_instance
				where business_object_type = ?
				and business_object_id = ?
				and status = 'SUBMITTED'
				""", Long.class, objectType, objectId);
		return count != null && count > 0;
	}

	private List<String> attachmentAvailableActions(AttachmentRecord record, CurrentUser currentUser) {
		if (!"AVAILABLE".equals(record.status()) || !hasBusinessPermission(record.objectType(), record.objectId(),
				currentUser, AttachmentAccessMode.VIEW)) {
			return List.of();
		}
		List<String> actions = new ArrayList<>();
		if (currentUser.permissions().contains("platform:attachment:download")) {
			actions.add("DOWNLOAD");
		}
		if (currentUser.permissions().contains("platform:attachment:delete")
				&& hasBusinessPermission(record.objectType(), record.objectId(), currentUser,
						AttachmentAccessMode.MANAGE)
				&& !approvalOpen(record.objectType(), record.objectId())) {
			actions.add("DELETE");
		}
		return actions;
	}

	private void requireBusinessPermission(String objectType, Long objectId, CurrentUser currentUser,
			AttachmentAccessMode mode) {
		if ("SALES_PROJECT_CONTRACT".equals(objectType)) {
			requirePermission(currentUser, "sales:contract:view");
			requireExists("select count(*) from sal_project_contract where id = ?", objectId);
			return;
		}
		if ("SALES_QUOTE".equals(objectType)) {
			requirePermission(currentUser, mode == AttachmentAccessMode.MANAGE ? "sales:quote:update"
					: "sales:quote:view");
			requireExists("select count(*) from sal_sales_quote where id = ?", objectId);
			return;
		}
		if ("SALES_ORDER_CHANGE".equals(objectType)) {
			requirePermission(currentUser, mode == AttachmentAccessMode.MANAGE ? "sales:order-change:update"
					: "sales:order-change:view");
			requireExists("select count(*) from sal_sales_order_change where id = ?", objectId);
			return;
		}
		if ("SALES_PROJECT".equals(objectType)) {
			requirePermission(currentUser, mode == AttachmentAccessMode.MANAGE ? "sales:project:update"
					: "sales:project:view");
			requireExists("select count(*) from sal_project where id = ?", objectId);
			return;
		}
		if ("BOM_ENGINEERING_CHANGE".equals(objectType)) {
			requirePermission(currentUser, "material:bom-eco:view");
			requireExists("select count(*) from mfg_bom_engineering_change where id = ?", objectId);
			return;
		}
		if ("INVENTORY_OWNERSHIP_CONVERSION".equals(objectType)) {
			requirePermission(currentUser, "inventory:balance:view");
			requireExists("select count(*) from inv_ownership_conversion where id = ?", objectId);
			return;
		}
		if ("INVENTORY_STOCKTAKE".equals(objectType)) {
			requirePermission(currentUser, "inventory:balance:view");
			requireExists("select count(*) from inv_stocktake where id = ?", objectId);
			return;
		}
		if ("INVENTORY_VALUATION_ADJUSTMENT".equals(objectType)) {
			requirePermission(currentUser, "inventory:valuation:view");
			requireExists("select count(*) from inv_valuation_adjustment where id = ?", objectId);
			return;
		}
		throw new BusinessException(ApiErrorCode.APPROVAL_OBJECT_NOT_SUPPORTED);
	}

	private boolean hasBusinessPermission(String objectType, Long objectId, CurrentUser currentUser,
			AttachmentAccessMode mode) {
		if ("SALES_PROJECT_CONTRACT".equals(objectType)) {
			return currentUser.permissions().contains("sales:contract:view")
					&& exists("select count(*) from sal_project_contract where id = ?", objectId);
		}
		if ("SALES_QUOTE".equals(objectType)) {
			String permission = mode == AttachmentAccessMode.MANAGE ? "sales:quote:update" : "sales:quote:view";
			return currentUser.permissions().contains(permission)
					&& exists("select count(*) from sal_sales_quote where id = ?", objectId);
		}
		if ("SALES_ORDER_CHANGE".equals(objectType)) {
			String permission = mode == AttachmentAccessMode.MANAGE ? "sales:order-change:update"
					: "sales:order-change:view";
			return currentUser.permissions().contains(permission)
					&& exists("select count(*) from sal_sales_order_change where id = ?", objectId);
		}
		if ("SALES_PROJECT".equals(objectType)) {
			String permission = mode == AttachmentAccessMode.MANAGE ? "sales:project:update" : "sales:project:view";
			return currentUser.permissions().contains(permission)
					&& exists("select count(*) from sal_project where id = ?", objectId);
		}
		if ("BOM_ENGINEERING_CHANGE".equals(objectType)) {
			return currentUser.permissions().contains("material:bom-eco:view")
					&& exists("select count(*) from mfg_bom_engineering_change where id = ?", objectId);
		}
		if ("INVENTORY_OWNERSHIP_CONVERSION".equals(objectType)) {
			return currentUser.permissions().contains("inventory:balance:view")
					&& exists("select count(*) from inv_ownership_conversion where id = ?", objectId);
		}
		if ("INVENTORY_STOCKTAKE".equals(objectType)) {
			return currentUser.permissions().contains("inventory:balance:view")
					&& exists("select count(*) from inv_stocktake where id = ?", objectId);
		}
		if ("INVENTORY_VALUATION_ADJUSTMENT".equals(objectType)) {
			return currentUser.permissions().contains("inventory:valuation:view")
					&& exists("select count(*) from inv_valuation_adjustment where id = ?", objectId);
		}
		return false;
	}

	private void requirePermission(CurrentUser currentUser, String permissionCode) {
		if (!currentUser.permissions().contains(permissionCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void requireExists(String sql, Long objectId) {
		if (!exists(sql, objectId)) {
			throw new BusinessException(ApiErrorCode.ATTACHMENT_NOT_FOUND);
		}
	}

	private boolean exists(String sql, Long objectId) {
		Long count = this.jdbcTemplate.queryForObject(sql, Long.class, objectId);
		return count != null && count > 0;
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static String extension(String filename) {
		int index = filename.lastIndexOf('.');
		if (index < 0 || index == filename.length() - 1) {
			return "";
		}
		return filename.substring(index + 1).toLowerCase(Locale.ROOT);
	}

	private static boolean hasDangerousDoubleExtension(String filename) {
		String lower = filename.toLowerCase(Locale.ROOT);
		int lastDot = lower.lastIndexOf('.');
		if (lastDot <= 0) {
			return false;
		}
		String prefix = lower.substring(0, lastDot);
		for (String extension : List.of("pdf", "png", "jpg", "jpeg", "txt", "csv", "docx", "xlsx", "exe", "bat",
				"cmd", "js", "vbs", "ps1", "sh", "scr", "com", "zip", "rar", "7z", "docm", "xlsm")) {
			if (prefix.endsWith("." + extension)) {
				return true;
			}
		}
		return false;
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String trimToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record AttachmentUpload(String objectType, Long objectId, String originalFilename, String contentType,
			byte[] content, String description, String idempotencyKey) {
	}

	public record AttachmentRecord(Long id, String objectType, Long objectId, String fileName,
			String contentType, Long fileSize, String sha256, String description, String status, String uploadedByName,
			OffsetDateTime uploadedAt, Long version, List<String> availableActions) {

		AttachmentRecord withAvailableActions(List<String> nextAvailableActions) {
			return new AttachmentRecord(this.id, this.objectType, this.objectId, this.fileName, this.contentType,
					this.fileSize, this.sha256, this.description, this.status, this.uploadedByName, this.uploadedAt,
					this.version, nextAvailableActions);
		}

	}

	public record DownloadedFile(String originalFilename, String contentType, byte[] content) {
	}

	public record DeleteAttachmentRequest(Long version, String reason) {
	}

	private record AttachmentFile(Long id, String objectType, Long objectId, Long fileId, String objectKey,
			String originalFilename, String contentType, Long version) {
	}

	private enum AttachmentAccessMode {

		VIEW, MANAGE

	}

}
