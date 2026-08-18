package com.qherp.api.system.assistant;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class AiAssistantPrivacyFilter {

	private static final Pattern SECRET = Pattern.compile(
		"(?i)(密码|口令|token|api[ _-]?key|secret)\\s*[:：=]\\s*\\S+");
	private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
	private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
	private static final Pattern BANK_ACCOUNT = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");
	private static final Pattern EMAIL = Pattern.compile(
		"(?i)(?<![\\w.-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![\\w.-])");

	public String sanitize(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String sanitized = SECRET.matcher(value).replaceAll("$1：[已脱敏]");
		sanitized = ID_CARD.matcher(sanitized).replaceAll("[证件号码已脱敏]");
		sanitized = PHONE.matcher(sanitized).replaceAll("[手机号码已脱敏]");
		sanitized = BANK_ACCOUNT.matcher(sanitized).replaceAll("[账号已脱敏]");
		return EMAIL.matcher(sanitized).replaceAll("[邮箱已脱敏]").trim();
	}
}
