package com.qherp.api.system.quality;

public enum QualityInspectionStatus {

	PENDING("待处理"),

	COMPLETED("已处理");

	private final String displayName;

	QualityInspectionStatus(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}

}
