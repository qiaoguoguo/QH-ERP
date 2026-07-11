package com.qherp.api.system.inventory;

public enum InventoryReservationStatus {

	ACTIVE("生效中"),

	RELEASED("已释放"),

	CONSUMED("已消耗"),

	CANCELLED("已取消");

	private final String displayName;

	InventoryReservationStatus(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}

}
