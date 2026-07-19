package com.qherp.api.system.periodclose;

import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PeriodCloseService {

	private final PeriodCloseQueryService queryService;

	private final PeriodCloseActionService actionService;

	private final PeriodCloseSnapshotService snapshotService;

	public PeriodCloseService(PeriodCloseQueryService queryService, PeriodCloseActionService actionService,
			PeriodCloseSnapshotService snapshotService) {
		this.queryService = queryService;
		this.actionService = actionService;
		this.snapshotService = snapshotService;
	}

	public PageResponse<PeriodSummaryResponse> list(String periodCode, String status, String checkResult,
			Boolean hasBlocking, LocalDate startDate, LocalDate endDate, int page, int pageSize,
			CurrentUser currentUser) {
		return this.queryService.list(periodCode, status, checkResult, hasBlocking, startDate, endDate, page,
				pageSize, currentUser);
	}

	public PeriodSummaryResponse period(Long periodId, CurrentUser currentUser) {
		return this.queryService.period(periodId, currentUser);
	}

	public RunResponse check(CheckRequest request, CurrentUser currentUser, HttpServletRequest servletRequest) {
		return this.actionService.check(request, currentUser, servletRequest);
	}

	public RunResponse run(Long runId, CurrentUser currentUser) {
		return this.queryService.run(runId, currentUser);
	}

	public PageResponse<CheckRunResponse> checks(Long runId, int page, int pageSize, CurrentUser currentUser) {
		return this.queryService.checks(runId, page, pageSize, currentUser);
	}

	public PageResponse<CheckItemResponse> checkItems(Long runId, Long checkRunId, int page, int pageSize,
			CurrentUser currentUser) {
		return this.queryService.checkItems(runId, checkRunId, page, pageSize, currentUser);
	}

	public RunResponse close(Long runId, CloseRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return this.actionService.close(runId, request, currentUser, servletRequest);
	}

	public RunResponse reopen(Long runId, ReopenRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return this.actionService.reopen(runId, request, currentUser, servletRequest);
	}

	public SnapshotOverviewResponse snapshot(Long runId, CurrentUser currentUser) {
		return this.snapshotService.snapshot(runId, currentUser);
	}

	public PageResponse<InventorySnapshotResponse> inventorySnapshot(Long runId, int page, int pageSize,
			CurrentUser currentUser) {
		return this.snapshotService.inventorySnapshot(runId, page, pageSize, currentUser);
	}

	public PageResponse<WipSnapshotResponse> wipSnapshot(Long runId, int page, int pageSize, CurrentUser currentUser) {
		return this.snapshotService.wipSnapshot(runId, page, pageSize, currentUser);
	}

	public PageResponse<ProjectCostSnapshotResponse> projectCostSnapshot(Long runId, int page, int pageSize,
			CurrentUser currentUser) {
		return this.snapshotService.projectCostSnapshot(runId, page, pageSize, currentUser);
	}

	public ReportSnapshotResponse reportSnapshot(Long runId, String reportCode, CurrentUser currentUser) {
		return this.snapshotService.reportSnapshot(runId, reportCode, currentUser);
	}

	public record CheckRequest(Long periodId, String idempotencyKey) {
	}

	public record CloseRequest(Long version, String sourceFingerprint, Boolean warningAcknowledged, String reason,
			String idempotencyKey) {
	}

	public record ReopenRequest(Long version, String reason, String idempotencyKey) {
	}

	public record PeriodSummaryResponse(Long periodId, String periodCode, String periodName, LocalDate startDate,
			LocalDate endDate, String periodStatus, String status, Long currentRunId, Integer currentRevisionNo,
			Long currentSnapshotId, Long latestCheckRunId, int blockingCount, int warningCount,
			List<String> allowedActions, Map<String, String> actionDisabledReasons, List<RunBriefResponse> history) {
	}

	public record RunBriefResponse(Long id, int revisionNo, String status, Long latestCheckRunId, Long snapshotId,
			int blockingCount, int warningCount, OffsetDateTime closedAt, OffsetDateTime reopenedAt, Long version) {
	}

	public record RunResponse(Long id, Long periodId, String periodCode, String periodName, LocalDate startDate,
			LocalDate endDate, String periodStatus, String status, String statusName, int revisionNo,
			Long latestCheckRunId, Long snapshotId, String sourceFingerprint, String inventoryFingerprint,
			String wipFingerprint, String projectCostFingerprint, String reportFingerprint, int blockingCount,
			int warningCount, boolean warningAcknowledged, String warningReason, String closedBy, OffsetDateTime closedAt,
			String closeReason, String reopenedBy, OffsetDateTime reopenedAt, String reopenReason, Long version,
			List<String> allowedActions, Map<String, String> actionDisabledReasons,
			List<RunBriefResponse> historyVersions, List<AuditSummaryResponse> auditSummary) {
	}

	public record CheckRunResponse(Long id, Long runId, String status, String sourceFingerprint,
			String inventoryFingerprint, String wipFingerprint, String projectCostFingerprint, String reportFingerprint,
			int blockingCount, int warningCount, String startedBy, OffsetDateTime startedAt,
			OffsetDateTime completedAt) {
	}

	public record CheckItemResponse(Long id, String domain, String checkCode, String severity, String objectType,
			Long objectId, String objectNo, String title, String description, String suggestion, boolean sourceVisible,
			String restrictedReason, JsonNode sourceRoute) {
	}

	public record SnapshotOverviewResponse(Long runId, Long periodId, int revisionNo, String status, Long snapshotId,
			String periodCode, String periodName, LocalDate startDate, LocalDate endDate, String sourceFingerprint,
			String generatedBy, OffsetDateTime generatedAt, Long sourceCheckRunId, List<String> reportCodes,
			List<SnapshotPartitionResponse> partitions, Long inventoryItemCount, Long wipItemCount,
			Long projectCostItemCount, boolean inventoryAmountVisible, boolean projectCostAmountVisible,
			boolean sourceVisible, String readonlyReason) {
	}

	public record SnapshotPartitionResponse(String code, String name, Long itemCount, String sourceFingerprint,
			boolean amountVisible, boolean sourceVisible, String restrictedReason) {
	}

	public record InventorySnapshotResponse(Long id, Long warehouseId, String warehouseName, Long materialId,
			String materialCode, String materialName, String qualityStatus, String ownershipType, Long projectId,
			String projectNo, Long batchId, Long serialId, Long costLayerId, String endingQuantity,
			String lockedQuantity, String availableQuantity, String valuationState, String unitCost,
			String endingAmount, String inQuantity, String outQuantity, String adjustmentQuantity,
			boolean amountVisible, boolean sourceVisible, String restrictedReason, String fingerprint) {
	}

	public record WipSnapshotResponse(Long id, Long projectId, String projectNo, Long workOrderId, String workOrderNo,
			Long productMaterialId, String productMaterialCode, String productMaterialName, String status,
			String plannedQuantity, String issuedQuantity, String reportedQuantity, String qualifiedQuantity,
			String completedQuantity, String wipQuantity, String wipCost, boolean amountVisible, boolean sourceVisible,
			String restrictedReason, String fingerprint) {
	}

	public record ProjectCostSnapshotResponse(Long id, Long projectId, String projectNo, String projectName,
			Long calculationId, String calculationNo, String sourceFingerprint, String freshnessStatus,
			String completenessStatus, String projectCostTotal, String wipCost, String finishedCost,
			String deliveredCost, String directProjectCost, String shipmentRevenue, String shipmentGrossMargin,
			int blockingVarianceCount, int warningVarianceCount, boolean amountVisible, boolean sourceVisible,
			String restrictedReason, String fingerprint) {
	}

	public record AuditSummaryResponse(Long id, String action, String result, String reason, String errorCode,
			String sourceFingerprint, String operatorUsername, OffsetDateTime createdAt) {
	}

	public record ReportSnapshotResponse(String reportCode, String reportName, int schemaVersion, JsonNode result,
			int sourceCount, String sourceFingerprint, OffsetDateTime generatedAt, boolean amountVisible, boolean sourceVisible,
			String restrictedReason) {
	}

}
