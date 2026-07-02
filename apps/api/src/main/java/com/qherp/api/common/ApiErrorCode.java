package com.qherp.api.common;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {

	OK("OK", "成功", HttpStatus.OK),

	VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败", HttpStatus.BAD_REQUEST),

	AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "未认证或登录已过期", HttpStatus.UNAUTHORIZED),

	AUTH_FORBIDDEN("AUTH_FORBIDDEN", "无访问权限", HttpStatus.FORBIDDEN),

	AUTH_ACCOUNT_DISABLED("AUTH_ACCOUNT_DISABLED", "账号已停用", HttpStatus.LOCKED),

	AUTH_USERNAME_EXISTS("AUTH_USERNAME_EXISTS", "用户名已存在", HttpStatus.CONFLICT),

	AUTH_ROLE_CODE_EXISTS("AUTH_ROLE_CODE_EXISTS", "角色编码已存在", HttpStatus.CONFLICT),

	CONFLICT("CONFLICT", "数据冲突", HttpStatus.CONFLICT),

	SYSTEM_ERROR("SYSTEM_ERROR", "系统异常", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String code;

	private final String message;

	private final HttpStatus httpStatus;

	ApiErrorCode(String code, String message, HttpStatus httpStatus) {
		this.code = code;
		this.message = message;
		this.httpStatus = httpStatus;
	}

	public String code() {
		return this.code;
	}

	public String message() {
		return this.message;
	}

	public HttpStatus httpStatus() {
		return this.httpStatus;
	}

}
