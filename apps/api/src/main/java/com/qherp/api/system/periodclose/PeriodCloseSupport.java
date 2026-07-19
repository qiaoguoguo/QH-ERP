package com.qherp.api.system.periodclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PeriodCloseSupport {

	static final int SCHEMA_VERSION = 1;

	static final List<String> REPORT_CODES = List.of("OVERVIEW", "SALES_SUMMARY", "PROCUREMENT_SUMMARY",
			"INVENTORY_STOCK_FLOW", "PRODUCTION_EXECUTION", "COST_COLLECTION", "SETTLEMENT_SUMMARY", "EXCEPTIONS");

	static final Map<String, String> REPORT_PERMISSIONS = Map.of(
			"OVERVIEW", "report:overview:view",
			"SALES_SUMMARY", "report:sales:view",
			"PROCUREMENT_SUMMARY", "report:procurement:view",
			"INVENTORY_STOCK_FLOW", "report:inventory:view",
			"PRODUCTION_EXECUTION", "report:production:view",
			"COST_COLLECTION", "report:cost:view",
			"SETTLEMENT_SUMMARY", "report:settlement:view",
			"EXCEPTIONS", "report:exception:view");

	private PeriodCloseSupport() {
	}

	static String amount(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	static String quantity(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	static String unitCost(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	static boolean hasPermission(CurrentUser currentUser, String permission) {
		return currentUser != null && currentUser.permissions().contains(permission);
	}

	static int pageSize(int pageSize) {
		if (pageSize == 10 || pageSize == 20 || pageSize == 50 || pageSize == 100) {
			return pageSize;
		}
		return 10;
	}

	static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	static String normalizeReportCode(String reportCode) {
		return reportCode == null ? "" : reportCode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
	}

	static String normalizeNullableStatus(String status) {
		if (!hasText(status)) {
			return null;
		}
		String normalized = status.trim().toUpperCase(Locale.ROOT);
		if ("MANUAL_LOCKED_WITHOUT_SNAPSHOT".equals(normalized) || "NOT_CHECKED".equals(normalized)) {
			return normalized;
		}
		try {
			PeriodCloseStatus.valueOf(normalized);
			return normalized;
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_ACTION_NOT_ALLOWED);
		}
	}

	static String fingerprint(String... parts) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(String.join("\n", parts).getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

}
