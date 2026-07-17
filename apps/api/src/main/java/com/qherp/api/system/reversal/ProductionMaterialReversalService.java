package com.qherp.api.system.reversal;

import com.qherp.api.system.inventory.InventoryPostingService;
import com.qherp.api.system.inventory.InventoryQualityStatus;
import com.qherp.api.system.production.ProductionInventoryPostingCoordinator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class ProductionMaterialReversalService {

	private final ProductionInventoryPostingCoordinator postingCoordinator;

	public ProductionMaterialReversalService(ProductionInventoryPostingCoordinator postingCoordinator) {
		this.postingCoordinator = postingCoordinator;
	}

	public InventoryPostingService.PostingResult postProductionMaterialReturn(ProductionMaterialReturnPosting command) {
		return this.postingCoordinator.postProductionMaterialReturn(new ProductionInventoryPostingCoordinator
			.InboundPostingCommand(command.warehouseId(), command.materialId(), command.unitId(), command.quantity(),
					InventoryQualityStatus.PENDING_INSPECTION, command.sourceType(), command.sourceId(),
					command.sourceLineId(), command.businessDate(), command.reason(), command.remark(),
					command.operatorName(), command.batchId(), command.serialId(), command.ownershipType(),
					command.projectId(), command.unitCost(), command.costLayerId(), command.originalValueMovementId()));
	}

	public InventoryPostingService.PostingResult postProductionMaterialSupplement(
			ProductionMaterialSupplementPosting command) {
		return this.postingCoordinator.postProductionMaterialSupplement(new ProductionInventoryPostingCoordinator
			.OutboundPostingCommand(command.warehouseId(), command.materialId(), command.unitId(), command.quantity(),
					command.sourceType(), command.sourceId(), command.sourceLineId(), command.businessDate(),
					command.reason(), command.remark(), command.operatorName(), false, command.batchId(),
					command.serialId(), command.ownershipType(), command.projectId(), command.costLayerId(), null,
					null, null));
	}

	public record ProductionMaterialReturnPosting(Long warehouseId, Long materialId, Long unitId,
			BigDecimal quantity, String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate,
			String reason, String remark, String operatorName, Long batchId, Long serialId, String ownershipType,
			Long projectId, BigDecimal unitCost, Long costLayerId, Long originalValueMovementId) {
	}

	public record ProductionMaterialSupplementPosting(Long warehouseId, Long materialId, Long unitId,
			BigDecimal quantity, String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate,
			String reason, String remark, String operatorName, Long batchId, Long serialId, String ownershipType,
			Long projectId, Long costLayerId) {
	}

}
