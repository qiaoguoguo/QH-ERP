package com.qherp.api.system.platform;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@RestController
@RequestMapping("/api/admin")
public class PlatformDocumentTaskController {

	private final PlatformDocumentTaskService documentTaskService;

	public PlatformDocumentTaskController(PlatformDocumentTaskService documentTaskService) {
		this.documentTaskService = documentTaskService;
	}

	@PostMapping("/exports/materials")
	public ApiResponse<PlatformDocumentTaskService.DocumentTaskRecord> exportMaterials(
			@RequestBody(required = false) PlatformDocumentTaskService.MaterialExportRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(
				this.documentTaskService.exportMaterials(request, idempotencyKey, currentUser, servletRequest));
	}

	@GetMapping("/import-templates/materials")
	public ResponseEntity<byte[]> materialImportTemplate() {
		return xlsx("materials-import-template.xlsx",
				List.of(List.of("code", "name", "specification", "materialType", "sourceType", "trackingMethod",
						"categoryCode", "unitCode", "status", "costCategory", "inventoryValuationCategory",
						"inventoryValueEnabled", "projectCostEnabled", "costRemark", "remark")));
	}

	@GetMapping("/import-templates/bom-drafts")
	public ResponseEntity<byte[]> bomDraftImportTemplate() {
		return xlsx("bom-draft-import-template.xlsx",
				List.of(List.of("mode", "bomId", "version", "bomCode", "parentMaterialCode", "versionCode",
						"name", "baseQuantity", "baseUnit", "effectiveFrom", "effectiveTo", "remark"),
						List.of("items.lineNo", "items.childMaterialCode", "items.businessUnit",
								"items.businessQuantity", "items.lossRate", "items.warehouse", "items.remark")));
	}

	@PostMapping("/imports/materials")
	public ApiResponse<PlatformDocumentTaskService.DocumentTaskRecord> importMaterials(
			@RequestParam("file") MultipartFile file,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.documentTaskService.importMaterials(file, idempotencyKey, currentUser,
				servletRequest));
	}

	@PostMapping("/imports/bom-drafts")
	public ApiResponse<PlatformDocumentTaskService.DocumentTaskRecord> importBomDrafts(
			@RequestParam("file") MultipartFile file,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.documentTaskService.importBomDrafts(file, idempotencyKey, currentUser,
				servletRequest));
	}

	@PostMapping("/imports/{id}/confirm")
	public ApiResponse<PlatformDocumentTaskService.DocumentTaskRecord> confirmImport(@PathVariable Long id,
			@Valid @RequestBody PlatformDocumentTaskService.ConfirmImportRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.documentTaskService.confirmImport(id, request, idempotencyKey, currentUser,
				servletRequest));
	}

	@PostMapping("/exports/bom-drafts/{id}")
	public ApiResponse<PlatformDocumentTaskService.DocumentTaskRecord> exportBomDraft(@PathVariable Long id,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.documentTaskService.exportBomDraft(id, idempotencyKey, currentUser, servletRequest));
	}

	@PostMapping("/export-tasks")
	public ApiResponse<PlatformDocumentTaskService.DocumentTaskRecord> createExportTask(
			@Valid @RequestBody PlatformDocumentTaskService.ProcurementExportRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.documentTaskService.createExportTask(request, idempotencyKey, currentUser,
				servletRequest));
	}

	@PostMapping("/procurement/inquiries/{id}/quote-imports")
	public ApiResponse<PlatformDocumentTaskService.DocumentTaskRecord> importSupplierQuotes(@PathVariable Long id,
			@RequestParam("file") MultipartFile file,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.documentTaskService.importSupplierQuotes(id, file, idempotencyKey, currentUser,
				servletRequest));
	}

	@GetMapping("/print-templates")
	public ApiResponse<List<PlatformDocumentTaskService.PrintTemplateRecord>> printTemplates(
			@RequestParam(required = false) String sceneCode, @RequestParam(required = false) String objectType) {
		return ApiResponse.ok(this.documentTaskService.printTemplates(sceneCode, objectType));
	}

	@GetMapping("/print-previews/{id}")
	public ApiResponse<PlatformDocumentTaskService.PrintPreviewRecord> printPreview(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.documentTaskService.printPreview(id, currentUser));
	}

	@GetMapping("/print-previews")
	public ApiResponse<PlatformDocumentTaskService.PrintPreviewRecord> printObjectPreview(
			@RequestParam String objectType, @RequestParam Long objectId, @RequestParam String templateCode,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.documentTaskService.printObjectPreview(objectType, objectId, templateCode,
				currentUser));
	}

	@PostMapping("/print-tasks")
	public ApiResponse<PlatformDocumentTaskService.DocumentTaskRecord> createPrintTask(
			@Valid @RequestBody PlatformDocumentTaskService.PrintTaskRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.documentTaskService.createPrintTask(request, idempotencyKey, currentUser,
				servletRequest));
	}

	@GetMapping("/document-tasks")
	public ApiResponse<PageResponse<PlatformDocumentTaskService.DocumentTaskRecord>> list(
			@RequestParam(required = false) Long taskId, @RequestParam(required = false) Long batchOperationId,
			@RequestParam(required = false) String taskType, @RequestParam(required = false) String objectKeyword,
			@RequestParam(required = false) String createdByKeyword,
			@RequestParam(required = false) String createdAtFrom, @RequestParam(required = false) String createdAtTo,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.documentTaskService.list(taskId, batchOperationId, taskType, objectKeyword,
				createdByKeyword,
				createdAtFrom, createdAtTo, status, page, pageSize, currentUser));
	}

	@GetMapping("/document-tasks/{id}")
	public ApiResponse<PlatformDocumentTaskService.DocumentTaskRecord> get(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.documentTaskService.get(id, currentUser));
	}

	@GetMapping("/document-tasks/{id}/download")
	public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
		PlatformDocumentTaskService.DownloadedFile file = this.documentTaskService.download(id, currentUser);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
			.contentType(MediaType.parseMediaType(file.contentType()))
			.body(file.content());
	}

	@GetMapping("/document-tasks/{id}/errors")
	public ApiResponse<PageResponse<PlatformDocumentTaskService.TaskErrorRecord>> errors(@PathVariable Long id,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.documentTaskService.errors(id, page, pageSize, currentUser));
	}

	@PostMapping("/document-tasks/{id}/cancel")
	public ApiResponse<PlatformDocumentTaskService.DocumentTaskRecord> cancel(@PathVariable Long id,
			@Valid @RequestBody PlatformDocumentTaskService.CancelTaskRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.documentTaskService.cancel(id, request, currentUser, servletRequest));
	}

	private ResponseEntity<byte[]> xlsx(String filename, List<List<String>> rows) {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("template");
			for (int i = 0; i < rows.size(); i++) {
				Row row = sheet.createRow(i);
				List<String> values = rows.get(i);
				for (int j = 0; j < values.size(); j++) {
					row.createCell(j).setCellValue(values.get(j));
				}
			}
			workbook.write(output);
			return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(output.toByteArray());
		}
		catch (Exception exception) {
			throw new com.qherp.api.common.BusinessException(com.qherp.api.common.ApiErrorCode.SYSTEM_ERROR);
		}
	}

}
