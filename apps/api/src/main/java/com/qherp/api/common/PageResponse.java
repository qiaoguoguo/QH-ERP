package com.qherp.api.common;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int pageSize, long total, int totalPages) {

	public PageResponse {
		items = items == null ? List.of() : List.copyOf(items);
		page = Math.max(page, 1);
		pageSize = Math.max(pageSize, 1);
		totalPages = Math.max(totalPages, 0);
	}

	public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long total) {
		int safePageSize = Math.max(pageSize, 1);
		int totalPages = (int) Math.ceil((double) total / safePageSize);
		return new PageResponse<>(items, Math.max(page, 1), safePageSize, total, totalPages);
	}

}
