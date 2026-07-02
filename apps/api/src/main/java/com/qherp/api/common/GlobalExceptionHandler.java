package com.qherp.api.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception,
			HttpServletRequest request) {
		return build(exception.httpStatus(), exception.errorCode(), exception.getMessage(), exception.details(),
				request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
			MethodArgumentNotValidException exception, HttpServletRequest request) {
		List<ApiErrorDetail> details = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(this::toErrorDetail)
			.toList();
		return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, ApiErrorCode.VALIDATION_ERROR.message(),
				details, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException exception,
			HttpServletRequest request) {
		List<ApiErrorDetail> details = exception.getConstraintViolations()
			.stream()
			.map((violation) -> new ApiErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()))
			.toList();
		return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, ApiErrorCode.VALIDATION_ERROR.message(),
				details, request);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException exception,
			HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTH_UNAUTHORIZED, ApiErrorCode.AUTH_UNAUTHORIZED.message(),
				List.of(), request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException exception,
			HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN, ApiErrorCode.AUTH_FORBIDDEN, ApiErrorCode.AUTH_FORBIDDEN.message(),
				List.of(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception, HttpServletRequest request) {
		return build(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.SYSTEM_ERROR, ApiErrorCode.SYSTEM_ERROR.message(),
				List.of(), request);
	}

	private ApiErrorDetail toErrorDetail(FieldError error) {
		return new ApiErrorDetail(error.getField(), error.getDefaultMessage());
	}

	private ResponseEntity<ApiResponse<Void>> build(HttpStatus httpStatus, ApiErrorCode errorCode, String message,
			List<ApiErrorDetail> details, HttpServletRequest request) {
		return ResponseEntity.status(httpStatus).body(ApiResponse.error(errorCode, message, details, traceId(request)));
	}

	private String traceId(HttpServletRequest request) {
		if (request == null) {
			return ApiResponse.newTraceId();
		}
		Object traceId = request.getAttribute("traceId");
		if (traceId instanceof String value && !value.isBlank()) {
			return value;
		}
		String headerTraceId = request.getHeader("X-Trace-Id");
		if (headerTraceId != null && !headerTraceId.isBlank()) {
			return headerTraceId;
		}
		return ApiResponse.newTraceId();
	}

}
