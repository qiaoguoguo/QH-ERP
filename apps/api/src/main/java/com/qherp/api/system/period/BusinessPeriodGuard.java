package com.qherp.api.system.period;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class BusinessPeriodGuard {

	private final JdbcTemplate jdbcTemplate;

	private final BusinessPeriodLockService lockService;

	public BusinessPeriodGuard(JdbcTemplate jdbcTemplate, BusinessPeriodLockService lockService) {
		this.jdbcTemplate = jdbcTemplate;
		this.lockService = lockService;
	}

	public void assertWritable(LocalDate businessDate, BusinessPeriodOperation operation, String sourceType, Long sourceId) {
		if (businessDate == null) {
			throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_DATE_RANGE_INVALID);
		}
		Long periodId = this.lockService.findPeriodId(businessDate).orElse(null);
		if (periodId == null) {
			return;
		}
		this.lockService.acquireForPeriodId(periodId);
		BusinessPeriodRow period = findPeriodById(periodId).orElse(null);
		if (period == null || period.status() == BusinessPeriodStatus.OPEN) {
			return;
		}
		this.jdbcTemplate.update("""
				insert into biz_business_period_audit (period_id, period_code, action, business_date, source_type, source_id, operator_username, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?)
				""", period.id(), period.periodCode(), operation.name(), businessDate, sourceType, sourceId, "system",
				OffsetDateTime.now());
		throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_LOCKED,
				"业务日期 " + businessDate + " 所属期间 " + period.periodCode() + " 已锁定");
	}

	private Optional<BusinessPeriodRow> findPeriodById(Long periodId) {
		return this.jdbcTemplate.query("""
				select id, period_code, status from biz_business_period
				where id = ?
				""", rs -> rs.next() ? Optional.of(new BusinessPeriodRow(rs.getLong("id"), rs.getString("period_code"),
				BusinessPeriodStatus.valueOf(rs.getString("status")))) : Optional.empty(), periodId);
	}

	private record BusinessPeriodRow(Long id, String periodCode, BusinessPeriodStatus status) {
	}
}
