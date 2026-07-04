package com.qherp.api.system.reversal;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReversalAdminService {

	public PageResponse<Object> emptyPage(int page, int pageSize) {
		return PageResponse.of(List.of(), page, pageSize, 0);
	}

	public List<ReversalTraceRecord> traces() {
		return List.of();
	}

	public Object sourceNotFound() {
		throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
	}

	public record ReversalTraceRecord(String traceKey, String direction, Object source, Object reverse,
			String businessDate, String quantity, String amount, String status, boolean canViewResource,
			boolean restricted, String restrictedMessage, String resourceRouteName, Object resourceRouteParams,
			Object resourceRouteQuery) {
	}

}
