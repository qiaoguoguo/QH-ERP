package com.qherp.api.system.inventory;

public enum InventoryTrackingMethod {

	NONE("不追踪"),

	BATCH("批次管理"),

	SERIAL("序列号管理");

	private final String displayName;

	InventoryTrackingMethod(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}

}
