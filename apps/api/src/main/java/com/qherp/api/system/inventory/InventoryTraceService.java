package com.qherp.api.system.inventory;

import com.qherp.api.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryTraceService {

	private final InventoryAdminService inventoryAdminService;

	public InventoryTraceService(InventoryAdminService inventoryAdminService) {
		this.inventoryAdminService = inventoryAdminService;
	}

	@Transactional(readOnly = true)
	public InventoryAdminService.InventoryTraceDetailResponse batchTrace(Long id, CurrentUser currentUser) {
		return this.inventoryAdminService.batchTrace(id, currentUser);
	}

	@Transactional(readOnly = true)
	public InventoryAdminService.InventoryTraceDetailResponse serialTrace(Long id, CurrentUser currentUser) {
		return this.inventoryAdminService.serialTrace(id, currentUser);
	}

}
