package com.qherp.api.system.financialclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FinancialCloseSupport {

	static final String LEDGER_CODE = "MAIN";

	static final String CURRENCY = "CNY";

	static final String TAX_DISCLAIMER = "本结果为 ERP 基础汇总或估算，不是正式纳税申报结果，不代替税务专业判断。";

	private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

	private FinancialCloseSupport() {
	}

	static int page(int page) {
		return Math.max(page, 1);
	}

	static int listLimit(int pageSize) {
		return List.of(10, 20, 50, 100).contains(pageSize) ? pageSize : 10;
	}

	static int offset(int page, int pageSize) {
		return (page(page) - 1) * listLimit(pageSize);
	}

	static String text(String value) {
		return value == null ? null : value.trim();
	}

	static String requiredText(String value, ApiErrorCode errorCode) {
		String normalized = text(value);
		if (normalized == null || normalized.isBlank()) {
			throw new BusinessException(errorCode);
		}
		return normalized;
	}

	static BigDecimal amount(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
	}

	static String decimal(BigDecimal value) {
		return amount(value).toPlainString();
	}

	static boolean positive(BigDecimal value) {
		return value != null && value.compareTo(BigDecimal.ZERO) > 0;
	}

	static YearMonth yearMonth(String periodCode) {
		try {
			return YearMonth.parse(requiredText(periodCode, ApiErrorCode.GL_PERIOD_NOT_OPEN), PERIOD_FORMATTER);
		}
		catch (RuntimeException exception) {
			if (exception instanceof BusinessException businessException) {
				throw businessException;
			}
			throw new BusinessException(ApiErrorCode.GL_PERIOD_NOT_OPEN);
		}
	}

	static String periodCode(LocalDate date) {
		return YearMonth.from(date).format(PERIOD_FORMATTER);
	}

	static LocalDate periodStart(String periodCode) {
		return yearMonth(periodCode).atDay(1);
	}

	static LocalDate periodEnd(String periodCode) {
		return yearMonth(periodCode).atEndOfMonth();
	}

	static boolean hasPermission(CurrentUser user, String permission) {
		return user != null && user.permissions().contains(permission);
	}

	static boolean amountVisible(CurrentUser user) {
		return hasPermission(user, "financial-close:amount:view");
	}

	static boolean sourceVisible(CurrentUser user) {
		return hasPermission(user, "financial-close:source:view");
	}

	static boolean bankSensitiveVisible(CurrentUser user) {
		return hasPermission(user, "financial-close:bank-sensitive:view");
	}

	static void putVisibility(Map<String, Object> map, CurrentUser user) {
		map.put("amountVisible", amountVisible(user));
		map.put("sourceVisible", sourceVisible(user));
		map.put("bankSensitiveVisible", bankSensitiveVisible(user));
	}

	static String visibleDecimal(BigDecimal value, CurrentUser user) {
		return visibleDecimal(value, amountVisible(user));
	}

	static String visibleDecimal(BigDecimal value, boolean amountVisible) {
		return amountVisible ? decimal(value) : null;
	}

	static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	static BigDecimal nullableAmount(ResultSet rs, String column) throws SQLException {
		BigDecimal value = rs.getBigDecimal(column);
		return rs.wasNull() ? BigDecimal.ZERO : amount(value);
	}

	static Map<String, Object> map() {
		return new LinkedHashMap<>();
	}

	static void advisoryLock(JdbcTemplate jdbcTemplate, String key) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", (rs) -> {
		}, key);
	}

}
