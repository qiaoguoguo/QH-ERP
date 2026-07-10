package com.qherp.api.system.inventory;

public enum InventoryQualityStatus {

	PENDING_INSPECTION("待检"),

	QUALIFIED("合格"),

	REJECTED("不合格"),

	FROZEN("冻结");

	private final String displayName;

	InventoryQualityStatus(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}

}
