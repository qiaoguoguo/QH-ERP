package com.qherp.api.system.reporting;

import com.qherp.api.common.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReportingStage033Service {

	private final OperatingFinanceOverviewQueryService overviewQueryService;

	private final ProjectProfitReportQueryService projectProfitQueryService;

	private final ContractCollectionReportQueryService contractCollectionReportQueryService;

	private final ProcurementVarianceReportQueryService procurementVarianceReportQueryService;

	private final InventoryCapitalReportQueryService inventoryCapitalReportQueryService;

	private final ReceivablePayableReportQueryService receivablePayableReportQueryService;

	private final OperatingAccountingReportQueryService operatingAccountingReportQueryService;

	private final FinancialSummaryReportQueryService financialSummaryReportQueryService;

	ReportingStage033Service(OperatingFinanceOverviewQueryService overviewQueryService,
			ProjectProfitReportQueryService projectProfitQueryService,
			ContractCollectionReportQueryService contractCollectionReportQueryService,
			ProcurementVarianceReportQueryService procurementVarianceReportQueryService,
			InventoryCapitalReportQueryService inventoryCapitalReportQueryService,
			ReceivablePayableReportQueryService receivablePayableReportQueryService,
			OperatingAccountingReportQueryService operatingAccountingReportQueryService,
			FinancialSummaryReportQueryService financialSummaryReportQueryService) {
		this.overviewQueryService = overviewQueryService;
		this.projectProfitQueryService = projectProfitQueryService;
		this.contractCollectionReportQueryService = contractCollectionReportQueryService;
		this.procurementVarianceReportQueryService = procurementVarianceReportQueryService;
		this.inventoryCapitalReportQueryService = inventoryCapitalReportQueryService;
		this.receivablePayableReportQueryService = receivablePayableReportQueryService;
		this.operatingAccountingReportQueryService = operatingAccountingReportQueryService;
		this.financialSummaryReportQueryService = financialSummaryReportQueryService;
	}

	Object operatingFinanceOverview(MultiValueMap<String, String> parameters) {
		return this.overviewQueryService.operatingFinanceOverview(parameters);
	}

	public Object projectProfit(MultiValueMap<String, String> parameters) {
		return this.projectProfitQueryService.projectProfit(parameters);
	}

	Object projectProfitDetail(Long projectId, MultiValueMap<String, String> parameters) {
		return this.projectProfitQueryService.projectProfitDetail(projectId, parameters);
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> projectProfitTraces(Long projectId,
			MultiValueMap<String, String> parameters) {
		return this.projectProfitQueryService.projectProfitTraces(projectId, parameters);
	}

	public Object contractCollections(MultiValueMap<String, String> parameters) {
		return this.contractCollectionReportQueryService.contractCollections(parameters);
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> contractCollectionTraces(
			MultiValueMap<String, String> parameters) {
		return this.contractCollectionReportQueryService.contractCollectionTraces(parameters);
	}

	public Object procurementVariances(MultiValueMap<String, String> parameters) {
		return this.procurementVarianceReportQueryService.procurementVariances(parameters);
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> procurementVarianceTraces(
			MultiValueMap<String, String> parameters) {
		return this.procurementVarianceReportQueryService.procurementVarianceTraces(parameters);
	}

	public Object inventoryCapital(MultiValueMap<String, String> parameters) {
		return this.inventoryCapitalReportQueryService.inventoryCapital(parameters);
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> inventoryCapitalTraces(
			MultiValueMap<String, String> parameters) {
		return this.inventoryCapitalReportQueryService.inventoryCapitalTraces(parameters);
	}

	public Object receivablePayable(MultiValueMap<String, String> parameters) {
		return this.receivablePayableReportQueryService.receivablePayable(parameters);
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> receivablePayableTraces(
			MultiValueMap<String, String> parameters) {
		return this.receivablePayableReportQueryService.receivablePayableTraces(parameters);
	}

	Object operatingAccountingReconciliation(MultiValueMap<String, String> parameters) {
		return this.operatingAccountingReportQueryService.operatingAccountingReconciliation(parameters);
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> operatingAccountingTraces(
			MultiValueMap<String, String> parameters) {
		return this.operatingAccountingReportQueryService.operatingAccountingTraces(parameters);
	}

	Object financialSummary(MultiValueMap<String, String> parameters) {
		return this.financialSummaryReportQueryService.financialSummary(parameters);
	}

	public Object captureBusinessSnapshot(String reportCode, String periodCode, LocalDate dateFrom, LocalDate dateTo) {
		return ReportingStage033QuerySupport.unrestrictedCapture(() -> switch (reportCode) {
			case "PROJECT_PROFIT" -> this.projectProfitQueryService.captureSnapshot(periodCode, dateFrom, dateTo);
			case "CONTRACT_COLLECTION" ->
				this.contractCollectionReportQueryService.captureSnapshot(periodCode, dateFrom, dateTo);
			case "PROCUREMENT_VARIANCE" ->
				this.procurementVarianceReportQueryService.captureSnapshot(periodCode, dateFrom, dateTo);
			case "INVENTORY_CAPITAL" -> this.inventoryCapitalReportQueryService.captureSnapshot(periodCode, dateFrom,
					dateTo);
			case "RECEIVABLE_PAYABLE" -> this.receivablePayableReportQueryService.captureSnapshot(periodCode,
					dateFrom, dateTo);
			default -> throw new IllegalArgumentException("Unsupported 033 snapshot report: " + reportCode);
		});
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> financialSummaryTraces(
			MultiValueMap<String, String> parameters) {
		return this.financialSummaryReportQueryService.financialSummaryTraces(parameters);
	}

	public record OperatingFinanceOverviewResponse(String periodCode, String analysisMode,
			String businessPeriodStatus, String accountingPeriodStatus, String financialCloseStatus,
			String finalityStatus, String freshnessStatus, String projectProfitAmount,
			String contractUnreceivedAmount, String procurementVarianceAmount, String inventoryCapitalAmount,
			String receivablePayableBalanceAmount, String accountingDifferenceAmount, String restrictedReason,
			int sourceCount, List<ReportEntryResponse> reports) {
	}

	public record ReportEntryResponse(String reportCode, String routePath) {
	}

	public record ProjectProfitSummaryResponse(int projectCount, String shipmentRevenueAmount,
			String invoiceRevenueAmount, String targetRevenueAmount, String projectCostAmount,
			String operatingGrossProfitAmount, String accountingProfitAmount, String differenceAmount,
			String publicUnallocatedAmount, int sourceCount, boolean amountVisible, String completenessStatus,
			String freshnessStatus, String reconciliationStatus, String finalityStatus, String restrictedReason,
			String shipmentRevenue, String projectCostTotal, String shipmentGrossMargin, String accountingProfit,
			String difference, String invoiceRevenue, String targetRevenue, String unassignedAccountingAmount,
			String analysisMode, String accountingRevenue, String accountingCost) {
	}

	public record ProjectProfitItemResponse(Long projectId, String projectNo, String projectName,
			String customerName, String shipmentRevenueAmount, String invoiceRevenueAmount,
			String targetRevenueAmount, String projectCostAmount, String operatingGrossProfitAmount,
			String operatingGrossProfitRate, String accountingRevenueAmount, String accountingCostAmount,
			String accountingProfitAmount, String completenessStatus, String freshnessStatus,
			String reconciliationStatus, String finalityStatus, int sourceCount, String traceKey,
			boolean amountVisible, String restrictedReason, String differenceAmount, String shipmentRevenue,
			String invoiceRevenue, String targetRevenue, String projectCostTotal, String shipmentGrossMargin,
			String shipmentGrossMarginRate, String accountingRevenue, String accountingCost, String accountingProfit) {
	}

	public record ProjectProfitDetailResponse(Long projectId, String projectNo, String projectName,
			String customerName, String shipmentRevenueAmount, String invoiceRevenueAmount,
			String targetRevenueAmount, String projectCostAmount, String operatingGrossProfitAmount,
			String operatingGrossProfitRate, String accountingRevenueAmount, String accountingCostAmount,
			String accountingProfitAmount, String completenessStatus, String freshnessStatus,
			String reconciliationStatus, String finalityStatus, int sourceCount, String traceKey,
			boolean amountVisible, String restrictedReason, String differenceAmount, String shipmentRevenue,
			String invoiceRevenue, String targetRevenue, String projectCostTotal, String shipmentGrossMargin,
			String shipmentGrossMarginRate, String accountingRevenue, String accountingCost, String accountingProfit,
			ManagementBasisResponse managementBasis, AccountingBasisResponse accountingBasis,
			List<CostStageEntryResponse> costStageEntries, List<RevenueEntryResponse> revenueEntries,
			List<AccountingEntryResponse> accountingEntries, List<VarianceReasonResponse> varianceReasons) {
	}

	public record ManagementBasisResponse(String shipmentRevenue, String invoiceRevenue, String targetRevenue,
			String projectCostTotal, String shipmentGrossMargin, String shipmentGrossMarginRate,
			String completenessStatus) {
	}

	public record AccountingBasisResponse(String accountingRevenue, String accountingCost, String accountingProfit,
			String unassignedAccountingAmount, String finalityStatus) {
	}

	public record CostStageEntryResponse(String stage, String amount, String status) {
	}

	public record RevenueEntryResponse(String basis, String amount, String description) {
	}

	public record AccountingEntryResponse(String accountCode, String accountName, String amount, String description) {
	}

	public record VarianceReasonResponse(String reasonCode, String description, String amount) {
	}

	public record ContractCollectionSummaryResponse(String contractAmount, String invoiceAmount, String receivedAmount,
			String unreceivedAmount, String overdueAmount, String advanceReceiptAmount, int sourceCount,
			String analysisMode, String freshnessStatus) {
	}

	public record ContractCollectionItemResponse(Long projectId, String projectNo, Long contractId, String contractNo,
			String customerName, String contractAmount, String invoiceAmount, String receivedAmount,
			String allocatedAmount, String unreceivedAmount, String advanceReceiptAmount, String overdueAmount,
			String collectionRate, String status, int sourceCount, String traceKey, String invoiceNos,
			String receiptNos, String receivableNos) {
	}

	public record ProcurementVarianceSummaryResponse(String orderAmount, String receiptAmount, String invoiceAmount,
			String paidAmount, String matchVarianceAmount, int sourceCount, String analysisMode,
			String freshnessStatus) {
	}

	public record ProcurementVarianceItemResponse(Long projectId, String sourceNo, String supplierName, String projectNo,
			String basis, String orderAmount, String receiptAmount, String invoiceAmount, String paidAmount,
			String unreceivedOrderAmount, String receivedUninvoicedAmount, String invoiceReceiptDifferenceAmount,
			String unpaidAmount, String matchVarianceAmount, String outsourcingUnsettledAmount,
			String reconciliationStatus, int sourceCount, String traceKey, String purchaseInvoiceNos,
			String paymentNos) {
	}

	public record InventoryCapitalSummaryResponse(String quantity, String amount, String snapshotAmount,
			String differenceAmount, String riskQuantity, int sourceCount, String analysisMode,
			String freshnessStatus, String knownValuationAmount, String unknownValuationQuantity,
			String completenessStatus) {
	}

	public record InventoryCapitalItemResponse(Long projectId, String ownerType, String projectNo, String warehouseName,
			String materialName, String qualityStatus, String freezeStatus, String valuationStatus, String quantity,
			String amount, String snapshotAmount, String differenceAmount, String riskQuantity, String freshnessStatus,
			int sourceCount, String traceKey, String unknownValuationQuantity, String completenessStatus) {
	}

	public record ReceivablePayableSummaryResponse(String receivableAmount, String payableAmount,
			String advanceReceiptAmount, String prepaymentAmount, String balanceAmount, String overdueAmount,
			int sourceCount, String analysisMode, String freshnessStatus) {
	}

	public record ReceivablePayableItemResponse(Long projectId, String partyType, String partyName, String projectNo,
			String sourceType, String sourceNo, String receivableAmount, String payableAmount,
			String advanceReceiptAmount, String prepaymentAmount, String settledAmount, String balanceAmount,
			String notDueAmount, String aging1To30Amount, String aging31To60Amount, String aging61To90Amount,
			String agingOver90Amount, String overdueAmount, String agingBucket, int sourceCount, String traceKey) {
	}

	public record OperatingAccountingReconciliationSummaryResponse(String operatingProfitAmount,
			String accountingProfitAmount, String publicUnallocatedAmount, String differenceAmount, int sourceCount,
			String reconciliationStatus, String finalityStatus, String analysisMode, String freshnessStatus) {
	}

	public record OperatingAccountingReconciliationItemResponse(Long projectId, String projectNo, String projectName,
			String operatingRevenueAmount, String operatingCostAmount, String operatingProfitAmount,
			String accountingRevenueAmount, String accountingCostAmount, String accountingProfitAmount,
			String publicUnallocatedAmount, String differenceAmount, String reconciliationStatus,
			String finalityStatus, String varianceReason, int sourceCount, String traceKey) {
	}

	public record FinancialSummaryResponse(String periodCode, String analysisMode, String finalityStatus,
			String businessPeriodStatus, String accountingPeriodStatus, String financialCloseStatus,
			String revenueAmount, String mainCostAmount, String periodExpenseAmount, String otherProfitLossAmount,
			String incomeTaxExpenseAmount, String operatingResultAmount, String assetBalanceAmount,
			String liabilityBalanceAmount, String equityBalanceAmount, String trialBalanceStatus,
			String bankReconciliationStatus, String taxSummaryStatus, int sourceCount, String traceKey,
			boolean legalReport, String disclaimer) {
	}

}
