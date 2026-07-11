package com.qherp.api.system.inventory;

public enum InventoryTrackingAllocationType {

	INBOUND("入库分配"),

	OUTBOUND("出库分配"),

	QUALITY_TRANSFER("质量转移"),

	SOURCE_INHERIT("来源继承");

	private final String displayName;

	InventoryTrackingAllocationType(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}

}
