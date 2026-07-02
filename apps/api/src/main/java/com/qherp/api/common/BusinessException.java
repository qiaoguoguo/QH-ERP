package com.qherp.api.common;

import org.springframework.http.HttpStatus;

import java.util.List;

public class BusinessException extends RuntimeException {

	private final ApiErrorCode errorCode;

	private final HttpStatus httpStatus;

	private final List<ApiErrorDetail> details;

	public BusinessException(ApiErrorCode errorCode) {
		this(errorCode, errorCode.message(), errorCode.httpStatus(), List.of());
	}

	public BusinessException(ApiErrorCode errorCode, String message) {
		this(errorCode, message, errorCode.httpStatus(), List.of());
	}

	public BusinessException(ApiErrorCode errorCode, String message, HttpStatus httpStatus) {
		this(errorCode, message, httpStatus, List.of());
	}

	public BusinessException(ApiErrorCode errorCode, String message, HttpStatus httpStatus,
			List<ApiErrorDetail> details) {
		super(message);
		this.errorCode = errorCode;
		this.httpStatus = httpStatus;
		this.details = details == null ? List.of() : List.copyOf(details);
	}

	public ApiErrorCode errorCode() {
		return this.errorCode;
	}

	public HttpStatus httpStatus() {
		return this.httpStatus;
	}

	public List<ApiErrorDetail> details() {
		return this.details;
	}

}
