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
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin")
public class PlatformDocumentTaskController {

	private final PlatformDocumentTaskService documentTaskService;

	private static final int TEMPLATE_ENUM_DROPDOWN_DATA_ROWS = 10000;

	private static final Map<String, String[]> MATERIAL_IMPORT_TEMPLATE_ENUM_OPTIONS = Map.ofEntries(
			Map.entry("物料类型", new String[] { "原材料", "半成品", "成品", "辅料" }),
			Map.entry("来源类型", new String[] { "外购", "自制", "外协" }),
			Map.entry("跟踪方式", new String[] { "不追踪", "批次", "序列号" }),
			Map.entry("状态", new String[] { "启用", "停用" }),
			Map.entry("成本分类", new String[] { "直接材料", "辅助材料", "半成品", "产成品", "委外", "服务", "未分类" }),
			Map.entry("库存计价类别", new String[] { "计价物料", "非计价消耗品", "服务非库存", "未分类" }),
			Map.entry("是否启用库存计价", new String[] { "是", "否" }),
			Map.entry("是否启用项目成本", new String[] { "是", "否" }));

	private static final Map<String, String[]> BOM_DRAFT_TEMPLATE_ENUM_OPTIONS = Map.ofEntries(
			Map.entry("操作模式", new String[] { "创建草稿", "更新草稿" }));

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
				List.of(List.of("物料编码", "物料名称", "规格型号", "物料类型", "来源类型", "跟踪方式",
						"物料分类编码", "计量单位编码", "状态", "成本分类", "库存计价类别", "是否启用库存计价",
						"是否启用项目成本", "成本备注", "备注")), Map.ofEntries(
						Map.entry("物料编码", "code；可填中文或英文: code / 物料编码。建议使用系统规则码或人工编码。"),
						Map.entry("code", "可填中文或英文: code / 物料编码。建议使用系统规则码或人工编码。"),
						Map.entry("物料名称", "name；物料名称（必填），长度不超过 200"),
						Map.entry("name", "物料名称（必填），长度不超过 200"),
						Map.entry("规格型号", "specification；规格型号（可选）"),
						Map.entry("specification", "规格型号（可选）"),
						Map.entry("物料类型", "materialType；系统枚举：原材料 / 半成品 / 成品 / 辅料（中文）或英文枚举码"),
						Map.entry("materialType", "系统枚举：原材料 / 半成品 / 成品 / 辅料（中文）或英文枚举码"),
						Map.entry("来源类型", "sourceType；系统枚举：外购 / 自制 / 外协（中文）或英文枚举码"),
						Map.entry("sourceType", "系统枚举：外购 / 自制 / 外协（中文）或英文枚举码"),
						Map.entry("跟踪方式", "trackingMethod；系统枚举：不追踪 / 批次 / 序列号（中文）或英文枚举码"),
						Map.entry("trackingMethod", "系统枚举：不追踪 / 批次 / 序列号（中文）或英文枚举码"),
						Map.entry("物料分类编码", "categoryCode；使用已存在并启用的物料分类编码（必填）"),
						Map.entry("categoryCode", "使用已存在并启用的物料分类编码（必填）"),
						Map.entry("计量单位编码", "unitCode；使用已存在并启用的单位编码（必填）"),
						Map.entry("unitCode", "使用已存在并启用的单位编码（必填）"),
						Map.entry("状态", "status；状态枚举：启用 / 停用（中文）或英文枚举码"),
						Map.entry("status", "状态枚举：启用 / 停用（中文）或英文枚举码"),
						Map.entry("成本分类", "costCategory；系统枚举：直接材料 / 辅助材料 / 半成品 / 产成品 / 委外 / 服务 / 未分类（中文）或英文枚举码"),
						Map.entry("costCategory", "系统枚举：直接材料 / 辅助材料 / 半成品 / 产成品 / 委外 / 服务 / 未分类（中文）或英文枚举码"),
						Map.entry("库存计价类别", "inventoryValuationCategory；系统枚举：计价物料 / 非计价消耗品 / 服务非库存 / 未分类（中文）或英文枚举码"),
						Map.entry("inventoryValuationCategory", "系统枚举：计价物料 / 非计价消耗品 / 服务非库存 / 未分类（中文）或英文枚举码"),
						Map.entry("是否启用库存计价", "inventoryValueEnabled；中文可填 是/否；兼容英文 true/false"),
						Map.entry("inventoryValueEnabled", "布尔: true / false"),
						Map.entry("是否启用项目成本", "projectCostEnabled；中文可填 是/否；兼容英文 true/false"),
						Map.entry("projectCostEnabled", "布尔: true / false"),
						Map.entry("成本备注", "costRemark；成本备注（可选）"),
						Map.entry("costRemark", "成本备注（可选）"),
						Map.entry("备注", "remark；导入说明（可选）"),
						Map.entry("remark", "导入说明（可选）")));
	}

	@GetMapping("/import-templates/bom-drafts")
	public ResponseEntity<byte[]> bomDraftImportTemplate() {
		return bomDraftXlsx("bom-draft-import-template.xlsx");
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
		return xlsx(filename, rows, Map.of());
	}

	private ResponseEntity<byte[]> xlsx(String filename, List<List<String>> rows, Map<String, String> headerNotes) {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("template");
			CreationHelper creationHelper = workbook.getCreationHelper();
			Drawing<?> drawing = sheet.createDrawingPatriarch();
			int maxColumns = 0;
			for (int i = 0; i < rows.size(); i++) {
				Row row = sheet.createRow(i);
				List<String> values = rows.get(i);
				for (int j = 0; j < values.size(); j++) {
					Cell cell = row.createCell(j);
					cell.setCellValue(values.get(j));
				}
				maxColumns = Math.max(maxColumns, values.size());
			}

			Font headerFont = workbook.createFont();
			headerFont.setFontHeightInPoints((short) 11);
			headerFont.setBold(true);
			headerFont.setColor(IndexedColors.WHITE.getIndex());
			headerFont.setFontName("Microsoft YaHei");

			CellStyle headerStyle = workbook.createCellStyle();
			headerStyle.setFont(headerFont);
			headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setAlignment(HorizontalAlignment.CENTER);
			headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			headerStyle.setWrapText(true);
			headerStyle.setBorderTop(BorderStyle.THIN);
			headerStyle.setBorderBottom(BorderStyle.THIN);
			headerStyle.setBorderLeft(BorderStyle.THIN);
			headerStyle.setBorderRight(BorderStyle.THIN);

			CellStyle bodyStyle = workbook.createCellStyle();
			bodyStyle.setBorderTop(BorderStyle.THIN);
			bodyStyle.setBorderBottom(BorderStyle.THIN);
			bodyStyle.setBorderLeft(BorderStyle.THIN);
			bodyStyle.setBorderRight(BorderStyle.THIN);
			bodyStyle.setAlignment(HorizontalAlignment.LEFT);
			bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			if (!rows.isEmpty()) {
				Row header = sheet.getRow(0);
				header.setHeightInPoints(30f);
				for (int j = 0; j < header.getLastCellNum(); j++) {
					Cell headerCell = header.getCell(j);
					if (headerCell == null) {
						continue;
					}
					headerCell.setCellStyle(headerStyle);
					String note = headerNotes.get(headerCell.getStringCellValue());
					if (note != null) {
						ClientAnchor anchor = creationHelper.createClientAnchor();
						anchor.setCol1(j);
						anchor.setCol2(j + 3);
						anchor.setRow1(0);
						anchor.setRow2(4);
						Comment comment = drawing.createCellComment(anchor);
						RichTextString commentText = creationHelper.createRichTextString(note);
						comment.setString(commentText);
						headerCell.setCellComment(comment);
					}
				}
			}
			for (int i = 1; i < rows.size(); i++) {
				Row row = sheet.getRow(i);
				for (int j = 0; row != null && j < row.getLastCellNum(); j++) {
					Cell bodyCell = row.getCell(j);
					if (bodyCell != null) {
						bodyCell.setCellStyle(bodyStyle);
					}
				}
			}
			for (int j = 0; j < maxColumns; j++) {
				sheet.autoSizeColumn(j);
				sheet.setColumnWidth(j, Math.max(sheet.getColumnWidth(j), 2560));
			}
			applyTemplateValidation(sheet,
					"materials-import-template.xlsx".equals(filename) ? MATERIAL_IMPORT_TEMPLATE_ENUM_OPTIONS : Map.of());
			sheet.createFreezePane(0, 1);
			if (maxColumns > 0) {
				sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, maxColumns - 1));
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

	private ResponseEntity<byte[]> bomDraftXlsx(String filename) {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			CellStyle headerStyle = templateHeaderStyle(workbook);
			CellStyle bodyStyle = templateBodyStyle(workbook);
			Sheet bom = workbook.createSheet("bom");
			writeTemplateSheet(workbook, bom, List.of(List.of("操作模式", "BOM ID", "版本", "BOM 编码",
					"父项物料编码", "版本编码", "名称", "基准数量", "基准单位编码", "生效日期", "失效日期", "备注")),
					Map.ofEntries(
							Map.entry("操作模式", "mode；创建草稿=CREATE，更新草稿=UPDATE_DRAFT"),
							Map.entry("BOM ID", "bomId；更新草稿时填写既有 BOM ID"),
							Map.entry("版本", "version；更新草稿时填写既有版本号"),
							Map.entry("BOM 编码", "bomCode；BOM 编码"),
							Map.entry("父项物料编码", "parentMaterialCode；填写系统已有父项物料编码"),
							Map.entry("版本编码", "versionCode；BOM 版本编码"),
							Map.entry("名称", "name；BOM 名称"),
							Map.entry("基准数量", "baseQuantity；BOM 基准数量"),
							Map.entry("基准单位编码", "baseUnit；填写系统已有基准单位编码"),
							Map.entry("生效日期", "effectiveFrom；格式 yyyy-MM-dd"),
							Map.entry("失效日期", "effectiveTo；格式 yyyy-MM-dd，可选"),
							Map.entry("备注", "remark；备注，可选")),
					headerStyle, bodyStyle);
			applyTemplateValidation(bom, BOM_DRAFT_TEMPLATE_ENUM_OPTIONS);

			Sheet items = workbook.createSheet("items");
			writeTemplateSheet(workbook, items, List.of(List.of("行号", "子项物料编码", "业务单位编码", "业务用量",
					"损耗率", "仓库编码", "备注")), Map.ofEntries(
							Map.entry("行号", "lineNo；BOM 明细行号"),
							Map.entry("子项物料编码", "childMaterialCode；填写系统已有子项物料编码"),
							Map.entry("业务单位编码", "businessUnit；填写系统已有业务单位编码"),
							Map.entry("业务用量", "businessQuantity；业务单位下的用量"),
							Map.entry("损耗率", "lossRate；损耗率，可填 0"),
							Map.entry("仓库编码", "warehouse；当前导入流程必须留空"),
							Map.entry("备注", "remark；备注，可选")),
					headerStyle, bodyStyle);

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

	private CellStyle templateHeaderStyle(Workbook workbook) {
		Font headerFont = workbook.createFont();
		headerFont.setFontHeightInPoints((short) 11);
		headerFont.setBold(true);
		headerFont.setColor(IndexedColors.WHITE.getIndex());
		headerFont.setFontName("Microsoft YaHei");
		CellStyle headerStyle = workbook.createCellStyle();
		headerStyle.setFont(headerFont);
		headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
		headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		headerStyle.setAlignment(HorizontalAlignment.CENTER);
		headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
		headerStyle.setWrapText(true);
		headerStyle.setBorderTop(BorderStyle.THIN);
		headerStyle.setBorderBottom(BorderStyle.THIN);
		headerStyle.setBorderLeft(BorderStyle.THIN);
		headerStyle.setBorderRight(BorderStyle.THIN);
		return headerStyle;
	}

	private CellStyle templateBodyStyle(Workbook workbook) {
		CellStyle bodyStyle = workbook.createCellStyle();
		bodyStyle.setBorderTop(BorderStyle.THIN);
		bodyStyle.setBorderBottom(BorderStyle.THIN);
		bodyStyle.setBorderLeft(BorderStyle.THIN);
		bodyStyle.setBorderRight(BorderStyle.THIN);
		bodyStyle.setAlignment(HorizontalAlignment.LEFT);
		bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
		return bodyStyle;
	}

	private void writeTemplateSheet(Workbook workbook, Sheet sheet, List<List<String>> rows,
			Map<String, String> headerNotes, CellStyle headerStyle, CellStyle bodyStyle) {
		CreationHelper creationHelper = workbook.getCreationHelper();
		Drawing<?> drawing = sheet.createDrawingPatriarch();
		int maxColumns = 0;
		for (int i = 0; i < rows.size(); i++) {
			Row row = sheet.createRow(i);
			List<String> values = rows.get(i);
			for (int j = 0; j < values.size(); j++) {
				row.createCell(j).setCellValue(values.get(j));
			}
			maxColumns = Math.max(maxColumns, values.size());
		}
		if (!rows.isEmpty()) {
			Row header = sheet.getRow(0);
			header.setHeightInPoints(30f);
			for (int j = 0; j < header.getLastCellNum(); j++) {
				Cell headerCell = header.getCell(j);
				headerCell.setCellStyle(headerStyle);
				String note = headerNotes.get(headerCell.getStringCellValue());
				if (note != null) {
					ClientAnchor anchor = creationHelper.createClientAnchor();
					anchor.setCol1(j);
					anchor.setCol2(j + 3);
					anchor.setRow1(0);
					anchor.setRow2(4);
					Comment comment = drawing.createCellComment(anchor);
					RichTextString commentText = creationHelper.createRichTextString(note);
					comment.setString(commentText);
					headerCell.setCellComment(comment);
				}
			}
		}
		for (int i = 1; i < rows.size(); i++) {
			Row row = sheet.getRow(i);
			for (int j = 0; row != null && j < row.getLastCellNum(); j++) {
				Cell bodyCell = row.getCell(j);
				if (bodyCell != null) {
					bodyCell.setCellStyle(bodyStyle);
				}
			}
		}
		for (int j = 0; j < maxColumns; j++) {
			sheet.autoSizeColumn(j);
			sheet.setColumnWidth(j, Math.max(sheet.getColumnWidth(j), 2560));
		}
		sheet.createFreezePane(0, 1);
		if (maxColumns > 0) {
			sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, maxColumns - 1));
		}
	}

	private void applyTemplateValidation(Sheet sheet, Map<String, String[]> validationOptions) {
		if (validationOptions.isEmpty()) {
			return;
		}
		Row header = sheet.getRow(0);
		if (header == null) {
			return;
		}
		DataValidationHelper validationHelper = sheet.getDataValidationHelper();
		for (int i = 0; i < header.getLastCellNum(); i++) {
			Cell headerCell = header.getCell(i);
			if (headerCell == null) {
				continue;
			}
			String headerName = headerCell.getStringCellValue();
			String[] options = validationOptions.get(headerName);
			if (options == null) {
				continue;
			}
			CellRangeAddressList range = new CellRangeAddressList(1, TEMPLATE_ENUM_DROPDOWN_DATA_ROWS, i, i);
			DataValidationConstraint constraint = validationHelper.createExplicitListConstraint(options);
			DataValidation validation = validationHelper.createValidation(constraint, range);
			validation.setSuppressDropDownArrow(true);
			validation.setShowErrorBox(true);
			validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
			validation.createErrorBox("请输入合法的枚举值", "请选择下拉列表中的枚举值");
			sheet.addValidationData(validation);
		}
	}

}
