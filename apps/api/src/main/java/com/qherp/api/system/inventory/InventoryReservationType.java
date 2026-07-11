package com.qherp.api.system.inventory;

public enum InventoryReservationType {

	RESERVATION("预留"),

	OCCUPATION("占用");

	private final String displayName;

	InventoryReservationType(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}

}
