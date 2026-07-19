package com.qherp.api.system.period;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class BusinessPeriodLockService {

	private static final long LOCK_NAMESPACE = 0x5148303000000000L;

	private final JdbcTemplate jdbcTemplate;

	public BusinessPeriodLockService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<Long> findPeriodId(LocalDate businessDate) {
		if (businessDate == null) {
			return Optional.empty();
		}
		return this.jdbcTemplate.query("""
				select id
				from biz_business_period
				where start_date <= ? and end_date >= ?
				order by id
				limit 1
				""", rs -> rs.next() ? Optional.of(rs.getLong("id")) : Optional.empty(), businessDate, businessDate);
	}

	public void acquireForBusinessDate(LocalDate businessDate) {
		findPeriodId(businessDate).ifPresent(this::acquireForPeriodId);
	}

	public void acquireForPeriodId(Long periodId) {
		if (periodId == null) {
			return;
		}
		this.jdbcTemplate.query("select pg_advisory_xact_lock(?)", rs -> null, advisoryKey(periodId));
	}

	private long advisoryKey(Long periodId) {
		return LOCK_NAMESPACE ^ (periodId & 0xffffffffL);
	}

}
