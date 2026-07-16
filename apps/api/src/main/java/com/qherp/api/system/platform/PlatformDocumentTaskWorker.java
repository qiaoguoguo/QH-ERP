package com.qherp.api.system.platform;

import com.qherp.api.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class PlatformDocumentTaskWorker {

	private final PlatformDocumentTaskService documentTaskService;

	private final boolean enabled;

	private final Duration leaseDuration;

	private final String workerId;

	public PlatformDocumentTaskWorker(PlatformDocumentTaskService documentTaskService,
			@Value("${qherp.platform.task.worker.enabled:true}") boolean enabled,
			@Value("${qherp.platform.task.worker.lease-seconds:30}") long leaseSeconds) {
		this.documentTaskService = documentTaskService;
		this.enabled = enabled;
		this.leaseDuration = Duration.ofSeconds(Math.max(5, leaseSeconds));
		this.workerId = "api-worker-" + UUID.randomUUID();
	}

	@Scheduled(fixedDelayString = "${qherp.platform.task.worker.delay-ms:2000}")
	public void scheduledPoll() {
		if (this.enabled) {
			processAvailableOnce();
		}
	}

	public boolean processAvailableOnce() {
		PlatformDocumentTaskService.ClaimedTask task = this.documentTaskService.claimNext(this.workerId,
				OffsetDateTime.now().plus(this.leaseDuration));
		if (task == null) {
			return false;
		}
		this.documentTaskService.heartbeat(task.id(), this.workerId, OffsetDateTime.now().plus(this.leaseDuration));
		try {
			CurrentUser operator = this.documentTaskService.taskOperator(task);
			if ("VALIDATE".equals(task.stage())) {
				this.documentTaskService.validateImport(task, operator);
				return true;
			}
			if ("COMMIT".equals(task.stage())) {
				this.documentTaskService.commitImport(task, operator);
				return true;
			}
			if ("MATERIAL_EXPORT".equals(task.taskType())) {
				PlatformDocumentTaskService.MaterialExportRequest request = this.documentTaskService
					.parseMaterialExportRequest(task.requestPayload());
				PlatformDocumentTaskService.ExportedFile exportedFile = this.documentTaskService
					.materialExportFile(request);
				this.documentTaskService.completeExport(task, exportedFile, operator);
				return true;
			}
			if ("BOM_DRAFT_EXPORT".equals(task.taskType())) {
				PlatformDocumentTaskService.BomDraftExportRequest request = this.documentTaskService
					.parseBomDraftExportRequest(task.requestPayload());
				PlatformDocumentTaskService.ExportedFile exportedFile = this.documentTaskService
					.bomDraftExportFile(request);
				this.documentTaskService.completeResult(task, exportedFile, operator,
						"exports/bom-drafts/" + UUID.randomUUID() + ".xlsx", "DOCUMENT_TASK_EXPORT_BOM_DRAFT");
				return true;
			}
			if (this.documentTaskService.isProcurementExportTaskType(task.taskType())) {
				PlatformDocumentTaskService.ProcurementExportRequest request = this.documentTaskService
					.parseProcurementExportRequest(task.requestPayload());
				PlatformDocumentTaskService.ExportedFile exportedFile = this.documentTaskService
					.procurementExportFile(request, operator);
				this.documentTaskService.completeResult(task, exportedFile, operator,
						"exports/procurement/" + UUID.randomUUID() + ".xlsx", "DOCUMENT_TASK_EXPORT_PROCUREMENT");
				return true;
			}
			if (this.documentTaskService.isSalesExportTaskType(task.taskType())) {
				PlatformDocumentTaskService.ProcurementExportRequest request = this.documentTaskService
					.parseProcurementExportRequest(task.requestPayload());
				PlatformDocumentTaskService.ExportedFile exportedFile = this.documentTaskService
					.salesExportFile(request, operator);
				this.documentTaskService.completeResult(task, exportedFile, operator,
						"exports/sales/" + UUID.randomUUID() + ".xlsx", "DOCUMENT_TASK_EXPORT_SALES");
				return true;
			}
			if ("APPROVAL_PRINT".equals(task.taskType())) {
				PlatformDocumentTaskService.PrintTaskPayload request = this.documentTaskService
					.parsePrintTaskPayload(task.requestPayload());
				PlatformDocumentTaskService.ExportedFile exportedFile = this.documentTaskService
					.printApprovalFile(request);
				this.documentTaskService.completeResult(task, exportedFile, operator,
						"prints/approvals/" + UUID.randomUUID() + ".pdf", "PRINT_GENERATE");
				return true;
			}
			if ("PROCUREMENT_ORDER_PRINT".equals(task.taskType())) {
				PlatformDocumentTaskService.ProcurementOrderPrintPayload request = this.documentTaskService
					.parseProcurementOrderPrintPayload(task.requestPayload());
				PlatformDocumentTaskService.ExportedFile exportedFile = this.documentTaskService
					.printProcurementOrderFile(request, operator);
				this.documentTaskService.completeResult(task, exportedFile, operator,
						"prints/procurement-orders/" + UUID.randomUUID() + ".pdf", "PROCUREMENT_ORDER_PRINT_GENERATE");
				return true;
			}
			if ("SALES_QUOTE_PRINT".equals(task.taskType())) {
				PlatformDocumentTaskService.SalesQuotePrintPayload request = this.documentTaskService
					.parseSalesQuotePrintPayload(task.requestPayload());
				PlatformDocumentTaskService.ExportedFile exportedFile = this.documentTaskService
					.printSalesQuoteFile(request, operator);
				this.documentTaskService.completeResult(task, exportedFile, operator,
						"prints/sales-quotes/" + UUID.randomUUID() + ".pdf", "SALES_QUOTE_PRINT_GENERATE");
				return true;
			}
			throw new IllegalStateException("不支持的任务类型：" + task.taskType());
		}
		catch (RuntimeException exception) {
			this.documentTaskService.failAttempt(task, exception);
			return true;
		}
	}

}
