package com.qherp.api.system.quality;

import java.util.Optional;

public enum QualityInspectionSourceType {

	PURCHASE_RECEIPT("采购入库"),

	PRODUCTION_COMPLETION("完工入库"),

	SALES_RETURN("销售退货"),

	PRODUCTION_RETURN("生产退料");

	private final String displayName;

	QualityInspectionSourceType(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}

	public static Optional<QualityInspectionSourceType> from(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(QualityInspectionSourceType.valueOf(value));
		}
		catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

}
