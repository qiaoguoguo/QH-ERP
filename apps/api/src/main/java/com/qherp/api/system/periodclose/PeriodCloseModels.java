package com.qherp.api.system.periodclose;

import com.qherp.api.system.period.BusinessPeriodStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

record PeriodClosePeriodRow(Long id, String periodCode, String periodName, LocalDate startDate, LocalDate endDate,
		BusinessPeriodStatus status) {
}

record PeriodCloseRunRow(Long id, Long periodId, int revisionNo, PeriodCloseStatus status, Long latestCheckRunId,
		Long snapshotId, String sourceFingerprint, String inventoryFingerprint, String wipFingerprint,
		String projectCostFingerprint, String reportFingerprint, int blockingCount, int warningCount,
		boolean warningAcknowledged, String warningReason, String closedBy, OffsetDateTime closedAt,
		String closeReason, String reopenedBy, OffsetDateTime reopenedAt, String reopenReason, Long version) {
}

record PeriodCloseSnapshotRow(Long id, Long runId, Long sourceCheckRunId, String sourceFingerprint,
		String inventoryFingerprint, String wipFingerprint, String projectCostFingerprint, String reportFingerprint,
		String generatedBy, OffsetDateTime generatedAt) {
}

record PeriodCloseCheckResult(PeriodCloseStatus status, String sourceFingerprint, String inventoryFingerprint,
		String wipFingerprint, String projectCostFingerprint, String reportFingerprint, int blockingCount,
		int warningCount, List<PeriodCloseCheckItemDraft> items) {
}

record PeriodCloseCheckItemDraft(String domain, String checkCode, String severity, boolean sourceRestricted,
		String objectType, Long objectId, String objectNo, String title, String description, String suggestion,
		String sourceRouteJson) {
}

record PeriodCloseIdempotencyRow(String requestFingerprint, Long responseRunId) {
}

record PeriodCloseProjectActivity(Long projectId, String projectNo) {
}

record PeriodCloseProjectCostState(Long calculationId, boolean current, String completenessStatus,
		long blockingVarianceCount) {
}
