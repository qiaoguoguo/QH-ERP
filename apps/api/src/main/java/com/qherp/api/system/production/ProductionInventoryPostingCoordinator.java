package com.qherp.api.system.production;

import com.qherp.api.system.inventory.InventoryDirection;
import com.qherp.api.system.inventory.InventoryMovementType;
import com.qherp.api.system.inventory.InventoryPostingService;
import com.qherp.api.system.inventory.InventoryQualityStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;

@Service
public class ProductionInventoryPostingCoordinator {

	private final InventoryPostingService inventoryPostingService;

	public ProductionInventoryPostingCoordinator(InventoryPostingService inventoryPostingService) {
		this.inventoryPostingService = inventoryPostingService;
	}

	public void lockPostingScopes(Collection<InventoryPostingService.PostingScope> scopes) {
		this.inventoryPostingService.lockPostingScopes(scopes);
	}

	public InventoryPostingService.PostingScope postingScope(Long warehouseId, Long materialId) {
		return new InventoryPostingService.PostingScope(warehouseId, materialId);
	}

	public InventoryPostingService.PostingResult postProductionIssue(OutboundPostingCommand command) {
		return postOutbound(InventoryMovementType.PRODUCTION_ISSUE, command);
	}

	public InventoryPostingService.PostingResult postOutsourcingIssue(OutboundPostingCommand command) {
		return postOutbound(InventoryMovementType.OUTSOURCING_ISSUE, command);
	}

	public InventoryPostingService.PostingResult postProductionMaterialSupplement(OutboundPostingCommand command) {
		return postOutbound(InventoryMovementType.PRODUCTION_MATERIAL_SUPPLEMENT_OUT, command);
	}

	public InventoryPostingService.PostingResult postProductionCompletionReceipt(InboundPostingCommand command) {
		return postInbound(InventoryMovementType.PRODUCTION_RECEIPT, command);
	}

	public InventoryPostingService.PostingResult postOutsourcingReceipt(InboundPostingCommand command) {
		return postInbound(InventoryMovementType.OUTSOURCING_RECEIPT, command);
	}

	public InventoryPostingService.PostingResult postProductionMaterialReturn(InboundPostingCommand command) {
		return postInbound(InventoryMovementType.PRODUCTION_MATERIAL_RETURN_IN, command);
	}

	private InventoryPostingService.PostingResult postOutbound(InventoryMovementType movementType,
			OutboundPostingCommand command) {
		InventoryPostingService.ValuationContext valuationContext = new InventoryPostingService.ValuationContext(
				command.ownershipType(), command.projectId(), null, command.costLayerId(), command.originalValueMovementId());
		return this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(movementType,
				InventoryDirection.OUT, command.warehouseId(), command.materialId(), command.unitId(),
				command.quantity(), InventoryQualityStatus.QUALIFIED, command.sourceType(), command.sourceId(),
				command.sourceLineId(), command.businessDate(), command.reason(), command.remark(),
				command.operatorName(), command.consumedReservation(), command.batchId(), command.serialId(),
				valuationContext, command.consumedReservationSourceType(), command.consumedReservationSourceLineId()));
	}

	private InventoryPostingService.PostingResult postInbound(InventoryMovementType movementType,
			InboundPostingCommand command) {
		InventoryPostingService.ValuationContext valuationContext = new InventoryPostingService.ValuationContext(
				command.ownershipType(), command.projectId(), command.unitCost(), command.costLayerId(),
				command.originalValueMovementId());
		return this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(movementType,
				InventoryDirection.IN, command.warehouseId(), command.materialId(), command.unitId(),
				command.quantity(), command.qualityStatus(), command.sourceType(), command.sourceId(),
				command.sourceLineId(), command.businessDate(), command.reason(), command.remark(),
				command.operatorName(), false, command.batchId(), command.serialId(), valuationContext));
	}

	public record OutboundPostingCommand(Long warehouseId, Long materialId, Long unitId, BigDecimal quantity,
			String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String remark,
			String operatorName, boolean consumedReservation, Long batchId, Long serialId, String ownershipType,
			Long projectId, Long costLayerId, Long originalValueMovementId, String consumedReservationSourceType,
			Long consumedReservationSourceLineId) {
	}

	public record InboundPostingCommand(Long warehouseId, Long materialId, Long unitId, BigDecimal quantity,
			InventoryQualityStatus qualityStatus, String sourceType, Long sourceId, Long sourceLineId,
			LocalDate businessDate, String reason, String remark, String operatorName, Long batchId, Long serialId,
			String ownershipType, Long projectId, BigDecimal unitCost, Long costLayerId, Long originalValueMovementId) {
	}

}
