package com.qherp.api.system.period;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class BusinessPeriodAdminService {

	private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

	private final JdbcTemplate jdbcTemplate;
	private final AuditService auditService;

	public BusinessPeriodAdminService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<BusinessPeriodRecord> list() {
		return this.jdbcTemplate.query("""
				select id, period_code, period_name, start_date, end_date, status, locked_by, locked_at, lock_reason,
				       unlocked_by, unlocked_at, unlock_reason, created_at, updated_at
				from biz_business_period order by start_date, id
				""", (rs, rowNum) -> map(rs));
	}

	@Transactional
	public BusinessPeriodRecord create(BusinessPeriodRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		ValidatedPeriod period = validate(request);
		assertNoOverlap(null, period.startDate(), period.endDate());
		long id = insert(period, operator.username());
		audit(operator, "BUSINESS_PERIOD_CREATE", id, period.periodCode(), servletRequest);
		return get(id);
	}

	@Transactional
	public BusinessPeriodRecord update(Long id, BusinessPeriodRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		BusinessPeriodRecord current = get(id);
		if (current.status() != BusinessPeriodStatus.OPEN) {
			throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_STATUS_INVALID);
		}
		ValidatedPeriod period = validate(request);
		assertNoOverlap(id, period.startDate(), period.endDate());
		this.jdbcTemplate.update("""
				update biz_business_period set period_code = ?, period_name = ?, start_date = ?, end_date = ?, updated_at = ? where id = ?
				""", period.periodCode(), period.periodName(), period.startDate(), period.endDate(), OffsetDateTime.now(), id);
		audit(operator, "BUSINESS_PERIOD_UPDATE", id, period.periodCode(), servletRequest);
		return get(id);
	}

	@Transactional
	public List<BusinessPeriodRecord> generateMonthly(GenerateMonthlyRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		YearMonth start = parseMonth(request == null ? null : request.startMonth());
		YearMonth end = parseMonth(request == null ? null : request.endMonth());
		if (start.isAfter(end)) {
			throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_DATE_RANGE_INVALID);
		}
		List<ValidatedPeriod> periods = new ArrayList<>();
		for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
			periods.add(new ValidatedPeriod(month.format(MONTH_FORMATTER), month.getYear() + "年" + String.format("%02d", month.getMonthValue()) + "月",
					month.atDay(1), month.atEndOfMonth()));
		}
		for (ValidatedPeriod period : periods) {
			assertNoOverlap(null, period.startDate(), period.endDate());
		}
		List<BusinessPeriodRecord> created = new ArrayList<>();
		for (ValidatedPeriod period : periods) {
			long id = insert(period, operator.username());
			audit(operator, "BUSINESS_PERIOD_CREATE", id, period.periodCode(), servletRequest);
			created.add(get(id));
		}
		return created;
	}

	@Transactional
	public BusinessPeriodRecord lock(Long id, ReasonRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		BusinessPeriodRecord current = get(id);
		if (current.status() != BusinessPeriodStatus.OPEN) throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_STATUS_INVALID);
		String reason = requiredReason(request);
		this.jdbcTemplate.update("update biz_business_period set status = ?, locked_by = ?, locked_at = ?, lock_reason = ?, updated_at = ? where id = ?",
				BusinessPeriodStatus.LOCKED.name(), operator.username(), OffsetDateTime.now(), reason, OffsetDateTime.now(), id);
		writePeriodAudit(current, "LOCK", reason, operator.username());
		audit(operator, "BUSINESS_PERIOD_LOCK", id, current.periodCode(), servletRequest);
		return get(id);
	}

	@Transactional
	public BusinessPeriodRecord unlock(Long id, ReasonRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		BusinessPeriodRecord current = get(id);
		if (current.status() != BusinessPeriodStatus.LOCKED) throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_STATUS_INVALID);
		String reason = requiredReason(request);
		this.jdbcTemplate.update("update biz_business_period set status = ?, unlocked_by = ?, unlocked_at = ?, unlock_reason = ?, updated_at = ? where id = ?",
				BusinessPeriodStatus.OPEN.name(), operator.username(), OffsetDateTime.now(), reason, OffsetDateTime.now(), id);
		writePeriodAudit(current, "UNLOCK", reason, operator.username());
		audit(operator, "BUSINESS_PERIOD_UNLOCK", id, current.periodCode(), servletRequest);
		return get(id);
	}

	private long insert(ValidatedPeriod period, String username) {
		return this.jdbcTemplate.queryForObject("""
				insert into biz_business_period (period_code, period_name, start_date, end_date, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?) returning id
				""", Long.class, period.periodCode(), period.periodName(), period.startDate(), period.endDate(),
				BusinessPeriodStatus.OPEN.name(), OffsetDateTime.now(), OffsetDateTime.now());
	}

	private BusinessPeriodRecord get(Long id) {
		List<BusinessPeriodRecord> records = this.jdbcTemplate.query("""
				select id, period_code, period_name, start_date, end_date, status, locked_by, locked_at, lock_reason,
				       unlocked_by, unlocked_at, unlock_reason, created_at, updated_at from biz_business_period where id = ?
				""", (rs, rowNum) -> map(rs), id);
		if (records.isEmpty()) throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_NOT_FOUND);
		return records.getFirst();
	}

	private void assertNoOverlap(Long id, LocalDate startDate, LocalDate endDate) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*) from biz_business_period where id <> coalesce(?, -1) and start_date <= ? and end_date >= ?
				""", Long.class, id, endDate, startDate);
		if (count != null && count > 0) throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_OVERLAPPED);
	}

	private ValidatedPeriod validate(BusinessPeriodRequest request) {
		if (request == null || blank(request.periodCode()) || blank(request.periodName()) || request.startDate() == null || request.endDate() == null || request.startDate().isAfter(request.endDate()))
			throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_DATE_RANGE_INVALID);
		return new ValidatedPeriod(request.periodCode().trim(), request.periodName().trim(), request.startDate(), request.endDate());
	}

	private YearMonth parseMonth(String value) {
		try { return value == null || !value.matches("\\d{4}-\\d{2}") ? invalidMonth() : YearMonth.parse(value, MONTH_FORMATTER); }
		catch (DateTimeParseException exception) { throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_DATE_RANGE_INVALID); }
	}

	private YearMonth invalidMonth() { throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_DATE_RANGE_INVALID); }

	private String requiredReason(ReasonRequest request) {
		if (request == null || blank(request.reason())) throw new BusinessException(ApiErrorCode.BUSINESS_PERIOD_REASON_REQUIRED);
		return request.reason().trim();
	}

	private void writePeriodAudit(BusinessPeriodRecord period, String action, String reason, String username) {
		this.jdbcTemplate.update("insert into biz_business_period_audit (period_id, period_code, action, reason, operator_username, created_at) values (?, ?, ?, ?, ?, ?)",
				period.id(), period.periodCode(), action, reason, username, OffsetDateTime.now());
	}

	private void audit(CurrentUser operator, String action, Long id, String summary, HttpServletRequest request) {
		this.auditService.record(operator, action, "BUSINESS_PERIOD", id, summary, request);
	}

	private BusinessPeriodRecord map(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new BusinessPeriodRecord(rs.getLong("id"), rs.getString("period_code"), rs.getString("period_name"),
				rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class), BusinessPeriodStatus.valueOf(rs.getString("status")),
				rs.getString("locked_by"), rs.getObject("locked_at", OffsetDateTime.class), rs.getString("lock_reason"), rs.getString("unlocked_by"),
				rs.getObject("unlocked_at", OffsetDateTime.class), rs.getString("unlock_reason"), rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private boolean blank(String value) { return value == null || value.isBlank(); }

	public record BusinessPeriodRequest(String periodCode, String periodName, LocalDate startDate, LocalDate endDate) {}
	public record GenerateMonthlyRequest(String startMonth, String endMonth) {}
	public record ReasonRequest(String reason) {}
	public record BusinessPeriodRecord(Long id, String periodCode, String periodName, LocalDate startDate, LocalDate endDate, BusinessPeriodStatus status, String lockedBy, OffsetDateTime lockedAt, String lockReason, String unlockedBy, OffsetDateTime unlockedAt, String unlockReason, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
	private record ValidatedPeriod(String periodCode, String periodName, LocalDate startDate, LocalDate endDate) {}
}
