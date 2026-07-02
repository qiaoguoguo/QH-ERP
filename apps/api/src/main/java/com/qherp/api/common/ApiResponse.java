package com.qherp.api.common;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ApiResponse<T>(boolean success, String code, String message, T data, List<ApiErrorDetail> details,
		String traceId, OffsetDateTime timestamp) {

	public ApiResponse {
		details = details == null ? List.of() : List.copyOf(details);
		traceId = traceId == null || traceId.isBlank() ? newTraceId() : traceId;
		timestamp = timestamp == null ? OffsetDateTime.now() : timestamp;
	}

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, ApiErrorCode.OK.code(), ApiErrorCode.OK.message(), data, List.of(), newTraceId(),
				OffsetDateTime.now());
	}

	public static <T> ApiResponse<T> error(ApiErrorCode errorCode, String message, List<ApiErrorDetail> details,
			String traceId) {
		return new ApiResponse<>(false, errorCode.code(), message, null, details, traceId, OffsetDateTime.now());
	}

	static String newTraceId() {
		return UUID.randomUUID().toString();
	}

}
